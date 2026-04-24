package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.MedRobotOptimalServiceGateway;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.DoctorServiceMatcher;
import com.qsystems.meddoctorassignment.domain.service.DoctorServiceSelectionService;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервис выбора услуги, который сохраняет старую локальную схему и при необходимости уточняет
 * итоговую пару serviceId/queueId через med-robot.
 */
@Singleton
public class MedRobotAwareDoctorServiceSelectionService implements DoctorServiceSelectionService {

  private static final Logger log =
      LoggerFactory.getLogger(MedRobotAwareDoctorServiceSelectionService.class);

  private final DoctorServiceMatcher localMatcher;
  private final MedRobotOptimalServiceGateway medRobotGateway;
  private final MedRobotProperties medRobotProperties;

  public MedRobotAwareDoctorServiceSelectionService(
      DoctorServiceMatcher localMatcher,
      MedRobotOptimalServiceGateway medRobotGateway,
      MedRobotProperties medRobotProperties) {
    this.localMatcher = localMatcher;
    this.medRobotGateway = medRobotGateway;
    this.medRobotProperties = medRobotProperties;
  }

  @Override
  public Optional<SelectedDoctorService> select(
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache) {
    Optional<SelectedDoctorService> localSelection =
        localMatcher.match(visitDetails, doctorAvailableServices, branchCache);
    if (!medRobotProperties.isEnabled()) {
      return localSelection;
    }
    if (!localSelection.isPresent()) {
      log.info("Med-robot selection is skipped because local pre-selection did not find a service");
      return Optional.empty();
    }

    Set<Integer> unservedServiceIds = resolveUnservedServiceIds(visitDetails, branchCache);
    if (unservedServiceIds.isEmpty()) {
      log.warn("Med-robot selection is skipped because visit {} has no resolvable unserved service ids", visitDetails.getId());
      return localSelection;
    }

    SelectedDoctorService local = localSelection.get();
    try {
      log.info(
          "Request med-robot optimal service branch={} currentService={} unservedServices={}",
          branchCache.getBranchId(),
          Integer.valueOf(local.getServiceId()),
          unservedServiceIds);
      MedRobotOptimalServiceResponse response =
          medRobotGateway.selectOptimalService(
              branchCache.getBranchId(), local.getServiceId(), unservedServiceIds);
      return toSelection(response, visitDetails, doctorAvailableServices, branchCache, localSelection);
    } catch (Exception exception) {
      log.error(
          "Med-robot optimal service request failed for branch={} currentService={} visit={}: {}",
          Integer.valueOf(branchCache.getBranchId()),
          Integer.valueOf(local.getServiceId()),
          Long.valueOf(visitDetails.getId()),
          exception.getMessage(),
          exception);
      if (medRobotProperties.isFallbackToLocalOnError()) {
        log.warn("Fallback to local service selection for visit {} after med-robot error", visitDetails.getId());
        return localSelection;
      }
      return Optional.empty();
    }
  }

  private Optional<SelectedDoctorService> toSelection(
      MedRobotOptimalServiceResponse response,
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache,
      Optional<SelectedDoctorService> localSelection) {
    if (response == null || !response.hasSelectedServiceAndQueue()) {
      log.warn(
          "Med-robot did not select service/queue for visit {}. serviceId={} queueId={}",
          Long.valueOf(visitDetails.getId()),
          response != null ? response.getServiceId() : null,
          response != null ? response.getQueueId() : null);
      return fallbackOnEmptyResponse(localSelection);
    }

    int serviceId = response.getServiceId().intValue();
    int queueId = response.getQueueId().intValue();
    if (!resolveUnservedServiceIds(visitDetails, branchCache).contains(Integer.valueOf(serviceId))) {
      log.warn(
          "Med-robot returned service {} that is absent in visit {} unserved route",
          Integer.valueOf(serviceId),
          Long.valueOf(visitDetails.getId()));
      return fallbackOnEmptyResponse(localSelection);
    }
    if (medRobotProperties.isRequireDoctorAvailableService()
        && !doctorAvailableServices.contains(Integer.valueOf(serviceId))) {
      log.warn(
          "Med-robot returned service {} that is not available for current doctor. doctorAvailableServices={}",
          Integer.valueOf(serviceId),
          doctorAvailableServices);
      return fallbackOnEmptyResponse(localSelection);
    }
    if (medRobotProperties.isRequireKnownQueue()
        && !branchCache.getQueueMap().containsKey(Integer.valueOf(queueId))) {
      log.warn(
          "Med-robot returned queue {} that is absent in branch {} cache",
          Integer.valueOf(queueId),
          Integer.valueOf(branchCache.getBranchId()));
      return fallbackOnEmptyResponse(localSelection);
    }

    Integer routeOrder = findRouteOrder(visitDetails, branchCache, serviceId);
    log.info(
        "Med-robot selected service={} queue={} visit={} routeOrder={} localService={} localQueue={}",
        Integer.valueOf(serviceId),
        Integer.valueOf(queueId),
        Long.valueOf(visitDetails.getId()),
        routeOrder,
        Integer.valueOf(localSelection.get().getServiceId()),
        Integer.valueOf(localSelection.get().getTargetQueueId()));
    return Optional.of(
        new SelectedDoctorService(
            serviceId,
            queueId,
            routeOrder,
            "med-robot-current-service-" + localSelection.get().getServiceId()));
  }

  private Optional<SelectedDoctorService> fallbackOnEmptyResponse(
      Optional<SelectedDoctorService> localSelection) {
    if (medRobotProperties.isFallbackToLocalOnEmptyResponse()) {
      return localSelection;
    }
    return Optional.empty();
  }

  private Set<Integer> resolveUnservedServiceIds(
      VisitDetails visitDetails, BranchAssignmentCache branchCache) {
    Set<Integer> result = new HashSet<Integer>();
    if (visitDetails == null || visitDetails.getUnservedServices() == null) {
      return result;
    }
    for (VisitUnservedService unservedService : visitDetails.getUnservedServices()) {
      Integer serviceId = resolveServiceId(unservedService, branchCache);
      if (serviceId != null && serviceId.intValue() > 0) {
        result.add(serviceId);
      }
    }
    return result;
  }

  private Integer resolveServiceId(
      VisitUnservedService unservedService, BranchAssignmentCache branchCache) {
    if (unservedService == null) {
      return null;
    }
    if (unservedService.getServiceId() != null) {
      return unservedService.getServiceId();
    }
    if (unservedService.getExternalKey() == null) {
      return null;
    }
    return branchCache.getServiceExternalKeyToId().get(unservedService.getExternalKey());
  }

  private Integer findRouteOrder(
      VisitDetails visitDetails, BranchAssignmentCache branchCache, int selectedServiceId) {
    if (visitDetails == null || visitDetails.getUnservedServices() == null) {
      return null;
    }
    for (VisitUnservedService unservedService : visitDetails.getUnservedServices()) {
      Integer serviceId = resolveServiceId(unservedService, branchCache);
      if (serviceId != null && serviceId.intValue() == selectedServiceId) {
        return unservedService.getRouteOrder();
      }
    }
    return null;
  }
}
