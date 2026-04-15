package com.qsystems.meddoctorassignment.cache;

import com.qsystems.meddoctorassignment.adapter.gateway.OrchestraMetadataGateway;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class BranchCacheUpdater {

    private static final Logger log = LoggerFactory.getLogger(BranchCacheUpdater.class);

    private final OrchestraMetadataGateway metadataGateway;
    private final OrchestraDataCacheContainer cacheContainer;
    private final AssignmentProperties assignmentProperties;

    public BranchCacheUpdater(OrchestraMetadataGateway metadataGateway,
                              OrchestraDataCacheContainer cacheContainer,
                              AssignmentProperties assignmentProperties) {
        this.metadataGateway = metadataGateway;
        this.cacheContainer = cacheContainer;
        this.assignmentProperties = assignmentProperties;
    }

    public void updateBranchCache(int branchId) {
        long startedAt = System.currentTimeMillis();
        log.info("Start cache refresh for branch {}", branchId);

        try {
            BranchAssignmentCache refreshedCache = new BranchAssignmentCache(branchId);

            List<ServiceData> services = metadataGateway.getServicesFromBranch(branchId);
            for (ServiceData service : services) {
                refreshedCache.getServiceMap().put(service.getId(), service);
                refreshedCache.getServiceExternalKeyToId().put(String.valueOf(service.getId()), service.getId());
                if (service.getInternalName() != null) {
                    refreshedCache.getServiceExternalKeyToId().put(service.getInternalName(), service.getId());
                }
                if (service.getExternalName() != null) {
                    refreshedCache.getServiceExternalKeyToId().put(service.getExternalName(), service.getId());
                }
                TinyQueue queue = metadataGateway.getQueueForServiceInBranch(branchId, service.getId());
                if (queue != null) {
                    refreshedCache.getServiceIdToQueueId().put(service.getId(), queue.getId());
                    refreshedCache.getQueueMap().put(queue.getId(), queue);
                    Set<Integer> serviceIds = refreshedCache.getQueueIdToServiceIds().get(queue.getId());
                    if (serviceIds == null) {
                        serviceIds = Collections.synchronizedSet(new HashSet<Integer>());
                        refreshedCache.getQueueIdToServiceIds().put(queue.getId(), serviceIds);
                    }
                    serviceIds.add(service.getId());
                }
            }

            List<WorkProfileData> workProfiles = metadataGateway.getWorkProfilesFromBranch(branchId);
            for (WorkProfileData workProfile : workProfiles) {
                List<TinyQueue> queues = metadataGateway.getQueuesForWorkProfileInBranch(branchId, workProfile.getId());
                Set<Integer> queueIds = Collections.synchronizedSet(new HashSet<Integer>());
                for (TinyQueue queue : queues) {
                    queueIds.add(queue.getId());
                    refreshedCache.getQueueMap().put(queue.getId(), queue);
                }
                refreshedCache.getWorkProfileToQueueIds().put(workProfile.getId(), queueIds);
            }

            List<TinyQueue> allQueues = metadataGateway.getAllQueuesInBranch(branchId);
            for (TinyQueue queue : allQueues) {
                refreshedCache.getQueueMap().put(queue.getId(), queue);
                if (assignmentProperties.getUnknownDoctorQueueId() != null
                        && assignmentProperties.getUnknownDoctorQueueId().intValue() == queue.getId()) {
                    refreshedCache.setUnknownDoctorQueueId(queue.getId());
                }
            }

            if (assignmentProperties.getUnknownDoctorQueueId() == null) {
                log.warn("Property application.assignment.unknown-doctor-queue-id is not configured for branch {}", branchId);
            } else if (refreshedCache.getUnknownDoctorQueueId() == null) {
                log.warn("Configured unknown-doctor queue id {} was not found in branch {}",
                        assignmentProperties.getUnknownDoctorQueueId(),
                        branchId);
            }

            List<ServicePointData> servicePoints = metadataGateway.getServicePointsFromBranch(branchId);
            for (ServicePointData servicePoint : servicePoints) {
                ServicePointRuntimeState state = new ServicePointRuntimeState(
                        servicePoint.getId(),
                        branchId,
                        servicePoint.getStaffId(),
                        servicePoint.getWorkProfileId(),
                        servicePoint.getStatus()
                );
                refreshedCache.getServicePointRuntimeStateMap().put(servicePoint.getId(), state);
            }

            refreshedCache.markUpdated();
            cacheContainer.replaceBranchCache(branchId, refreshedCache);

            log.info(
                    "Finish cache refresh for branch {}: services={}, queues={}, workProfiles={}, servicePoints={}, unknownDoctorQueueId={}, tookMs={}",
                    branchId,
                    Integer.valueOf(refreshedCache.getServiceMap().size()),
                    Integer.valueOf(refreshedCache.getQueueMap().size()),
                    Integer.valueOf(refreshedCache.getWorkProfileToQueueIds().size()),
                    Integer.valueOf(refreshedCache.getServicePointRuntimeStateMap().size()),
                    refreshedCache.getUnknownDoctorQueueId(),
                    Long.valueOf(System.currentTimeMillis() - startedAt)
            );
        } catch (RuntimeException exception) {
            log.error("Failed to refresh cache for branch {} after {} ms: {}",
                    Integer.valueOf(branchId),
                    Long.valueOf(System.currentTimeMillis() - startedAt),
                    exception.getMessage(),
                    exception);
            throw exception;
        }
    }
}
