package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.MedRobotOptimalServiceGateway;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.config.MedRobotErrorHandlingMode;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.MedRobotRequestBodyMode;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.DoctorServiceMatcher;
import com.qsystems.meddoctorassignment.domain.service.DoctorServiceSelectionService;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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

    boolean plainTextMode = isPlainTextMode();
    if (!localSelection.isPresent() && !plainTextMode) {
      log.info("Med-robot selection is skipped because local pre-selection did not find a service");
      return Optional.empty();
    }

    Set<Integer> unservedServiceIds = resolveUnservedServiceIds(visitDetails, branchCache);
    if (unservedServiceIds.isEmpty() && !plainTextMode) {
      log.warn(
          "Med-robot selection is skipped because visit {} has no resolvable unserved service ids",
          Long.valueOf(visitDetails.getId()));
      return localSelection;
    }

    Integer currentServiceId =
        resolveCurrentServiceIdForRobot(
            visitDetails, doctorAvailableServices, branchCache, localSelection);
    if (currentServiceId == null) {
      log.warn(
          "Med-robot selection is skipped because current service for robot is not resolved: visit={} localSelectionPresent={} doctorAvailableServices={} currentVisitService={}",
          Long.valueOf(visitDetails.getId()),
          Boolean.valueOf(localSelection.isPresent()),
          doctorAvailableServices,
          visitDetails.getCurrentServiceId());
      return localSelection;
    }

    try {
      MedRobotOptimalServiceResponse response =
          requestMedRobot(visitDetails, branchCache, currentServiceId.intValue(), unservedServiceIds);
      return toSelection(
          response,
          visitDetails,
          doctorAvailableServices,
          branchCache,
          localSelection,
          currentServiceId.intValue(),
          unservedServiceIds,
          plainTextMode);
    } catch (Exception exception) {
      return fallbackAfterRobotError(
          visitDetails, branchCache, localSelection, currentServiceId.intValue(), exception);
    }
  }

  private boolean isPlainTextMode() {
    return MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT.equals(
        medRobotProperties.getRequestBodyMode());
  }

  private Integer resolveCurrentServiceIdForRobot(
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache,
      Optional<SelectedDoctorService> localSelection) {
    if (localSelection.isPresent()) {
      return Integer.valueOf(localSelection.get().getServiceId());
    }

    if (doctorAvailableServices == null || doctorAvailableServices.isEmpty()) {
      return null;
    }

    Integer visitCurrentServiceId = visitDetails.getCurrentServiceId();
    if (isUsableCurrentServiceId(visitCurrentServiceId, branchCache)
        && doctorAvailableServices.contains(visitCurrentServiceId)) {
      log.info(
          "Med-robot plain-text mode uses visit currentService={} as request path service because local pre-selection is absent for visit {}",
          visitCurrentServiceId,
          Long.valueOf(visitDetails.getId()));
      return visitCurrentServiceId;
    }

    TreeSet<Integer> sortedDoctorServices = new TreeSet<Integer>(doctorAvailableServices);
    Integer selectedCurrentServiceId = sortedDoctorServices.first();
    if (sortedDoctorServices.size() > 1) {
      log.warn(
          "Med-robot plain-text mode has no local pre-selection and multiple doctor services for visit {}. Use deterministic first service={} from availableServices={}",
          Long.valueOf(visitDetails.getId()),
          selectedCurrentServiceId,
          sortedDoctorServices);
    } else {
      log.info(
          "Med-robot plain-text mode uses the only doctor available service={} as request path service for visit {}",
          selectedCurrentServiceId,
          Long.valueOf(visitDetails.getId()));
    }
    return selectedCurrentServiceId;
  }

  private boolean isUsableCurrentServiceId(
      Integer currentServiceId, BranchAssignmentCache branchCache) {
    if (currentServiceId == null || currentServiceId.intValue() <= 0) {
      return false;
    }
    return branchCache == null
        || branchCache.getServiceMap().isEmpty()
        || branchCache.getServiceMap().containsKey(currentServiceId);
  }

  private MedRobotOptimalServiceResponse requestMedRobot(
      VisitDetails visitDetails,
      BranchAssignmentCache branchCache,
      int currentServiceId,
      Set<Integer> unservedServiceIds) {
    if (isPlainTextMode()) {
      String ticketNumber = normalize(visitDetails.getTicketNumber());
      if (ticketNumber == null) {
        throw new IllegalStateException(
            "Med-robot text/plain mode requires ticketNumber, but visit "
                + visitDetails.getId()
                + " has no ticket number");
      }
      log.info(
          "Request med-robot optimal service branch={} currentService={} bodyMode={} ticketNumber={} policy={}",
          Integer.valueOf(branchCache.getBranchId()),
          Integer.valueOf(currentServiceId),
          medRobotProperties.getRequestBodyMode(),
          ticketNumber,
          medRobotProperties.getPlainTextPolicy());
      return medRobotGateway.selectOptimalServicePlainText(
          branchCache.getBranchId(),
          currentServiceId,
          ticketNumber,
          medRobotProperties.getPlainTextPolicy());
    }

    log.info(
        "Request med-robot optimal service branch={} currentService={} bodyMode={} unservedServices={}",
        Integer.valueOf(branchCache.getBranchId()),
        Integer.valueOf(currentServiceId),
        medRobotProperties.getRequestBodyMode(),
        unservedServiceIds);
    return medRobotGateway.selectOptimalService(
        branchCache.getBranchId(), currentServiceId, unservedServiceIds);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private Optional<SelectedDoctorService> toSelection(
      MedRobotOptimalServiceResponse response,
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache,
      Optional<SelectedDoctorService> localSelection,
      int currentServiceId,
      Set<Integer> unservedServiceIds,
      boolean plainTextMode) {
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
    if (!plainTextMode && !unservedServiceIds.contains(Integer.valueOf(serviceId))) {
      log.warn(
          "Med-robot returned service {} that is absent in visit {} unserved route",
          Integer.valueOf(serviceId),
          Long.valueOf(visitDetails.getId()));
      return fallbackOnEmptyResponse(localSelection);
    }
    if (plainTextMode && !unservedServiceIds.isEmpty()
        && !unservedServiceIds.contains(Integer.valueOf(serviceId))) {
      log.info(
          "Med-robot plain-text response service {} is absent in locally read visit {} unserved route {}. Accept response because text/plain mode uses ticket-number source of truth.",
          Integer.valueOf(serviceId),
          Long.valueOf(visitDetails.getId()),
          unservedServiceIds);
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
        "Med-robot selected service={} queue={} visit={} routeOrder={} localService={} localQueue={} currentService={}",
        Integer.valueOf(serviceId),
        Integer.valueOf(queueId),
        Long.valueOf(visitDetails.getId()),
        routeOrder,
        localSelection.isPresent() ? Integer.valueOf(localSelection.get().getServiceId()) : null,
        localSelection.isPresent() ? Integer.valueOf(localSelection.get().getTargetQueueId()) : null,
        Integer.valueOf(currentServiceId));
    return Optional.of(
        new SelectedDoctorService(
            serviceId,
            queueId,
            routeOrder,
            "med-robot-current-service-" + currentServiceId));
  }

  private Optional<SelectedDoctorService> fallbackAfterRobotError(
      VisitDetails visitDetails,
      BranchAssignmentCache branchCache,
      Optional<SelectedDoctorService> localSelection,
      int currentServiceId,
      Exception exception) {
    log.error(
        "Med-robot optimal service request failed for branch={} currentService={} visit={}: {}",
        Integer.valueOf(branchCache.getBranchId()),
        Integer.valueOf(currentServiceId),
        Long.valueOf(visitDetails.getId()),
        exception.getMessage(),
        exception);
    if (MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL.equals(
        medRobotProperties.getErrorHandlingMode())) {
      if (localSelection.isPresent()) {
        log.warn(
            "Fallback to local service selection for visit {} after med-robot error. errorHandlingMode={}",
            Long.valueOf(visitDetails.getId()),
            medRobotProperties.getErrorHandlingMode());
      } else {
        log.warn(
            "No local service selection for visit {} after med-robot error; return empty selection. errorHandlingMode={}",
            Long.valueOf(visitDetails.getId()),
            medRobotProperties.getErrorHandlingMode());
      }
      return localSelection;
    }
    log.warn(
        "Skip visit {} after med-robot error. errorHandlingMode={}",
        Long.valueOf(visitDetails.getId()),
        medRobotProperties.getErrorHandlingMode());
    return Optional.empty();
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
    // TreeSet нужен не для бизнес-логики, а для детерминированности:
    // одинаковый визит должен формировать одинаковый JSON-массив и одинаковую строку в логах/тестах.
    Set<Integer> result = new TreeSet<Integer>();
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
