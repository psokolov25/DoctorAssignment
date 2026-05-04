package com.qsystems.meddoctorassignment.schedule;

import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Периодическая подстраховочная задача, запускающая повторную обработку по runtime cache.
 *
 * <p>Нужна на случай, если websocket-событие было потеряно, пришло до прогрева кэша или было
 * отклонено из-за временной сетевой проблемы.</p>
 */
@Singleton
public class PollingReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(PollingReconciliationJob.class);

  private final OrchestraDataCacheContainer cacheContainer;
  private final AutonomousMedicalExamAssignmentService assignmentService;
  private final AssignmentProperties assignmentProperties;

  public PollingReconciliationJob(
      OrchestraDataCacheContainer cacheContainer,
      com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService cacheUpdateService,
      AutonomousMedicalExamAssignmentService assignmentService,
      AssignmentProperties assignmentProperties) {
    this.cacheContainer = cacheContainer;
    this.assignmentService = assignmentService;
    this.assignmentProperties = assignmentProperties;
  }

  @Scheduled(cron = "${application.assignment.polling-cron}")
  public void reconcile() {
    if (!assignmentProperties.isEnabled() || !assignmentProperties.isPollingEnabled()) {
      return;
    }
    for (BranchAssignmentCache branchCache : cacheContainer.getBranchCacheMap().values()) {
      if (!assignmentProperties.isAllowedBranch(branchCache.getBranchId())) {
        continue;
      }

      List<DoctorContext> doctorContexts = buildPollingDoctorContexts(branchCache);
      if (doctorContexts.isEmpty()) {
        continue;
      }

      log.info(
          "Polling reconciliation for branch={} candidateDoctors={}",
          Integer.valueOf(branchCache.getBranchId()),
          Integer.valueOf(doctorContexts.size()));
      assignmentService.processPollingBranch(branchCache.getBranchId(), doctorContexts);
    }
  }

  private List<DoctorContext> buildPollingDoctorContexts(BranchAssignmentCache branchCache) {
    List<ServicePointRuntimeState> runtimeStates =
        new ArrayList<ServicePointRuntimeState>(branchCache.getServicePointRuntimeStateMap().values());
    Collections.sort(
        runtimeStates,
        new Comparator<ServicePointRuntimeState>() {
          @Override
          public int compare(ServicePointRuntimeState left, ServicePointRuntimeState right) {
            int servicePointCompare = Long.compare(left.getServicePointId(), right.getServicePointId());
            if (servicePointCompare != 0) {
              return servicePointCompare;
            }
            return Integer.compare(left.getStaffId(), right.getStaffId());
          }
        });

    List<DoctorContext> result = new ArrayList<DoctorContext>();
    for (ServicePointRuntimeState runtimeState : runtimeStates) {
      if (runtimeState.getStaffId() <= 0 || runtimeState.getWorkProfileId() <= 0) {
        continue;
      }
      if (runtimeState.getStatus() != null && !"OPEN".equalsIgnoreCase(runtimeState.getStatus())) {
        continue;
      }
      DoctorContext context = new DoctorContext();
      context.setTriggerSource(TriggerSource.POLLING);
      context.setBranchId(runtimeState.getBranchId());
      context.setServicePointId(runtimeState.getServicePointId());
      context.setStaffId(runtimeState.getStaffId());
      context.setWorkProfileId(runtimeState.getWorkProfileId());
      context.recordSource("branchId", "polling.runtime-cache");
      context.recordSource("servicePointId", "polling.runtime-cache");
      context.recordSource("staffId", "polling.runtime-cache");
      context.recordSource("workProfileId", "polling.runtime-cache");
      result.add(context);
    }
    return result;
  }
}
