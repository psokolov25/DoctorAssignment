package com.qsystems.meddoctorassignment.schedule;

import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
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
    private final OrchestraDataCacheUpdateService cacheUpdateService;
    private final AutonomousMedicalExamAssignmentService assignmentService;
    private final AssignmentProperties assignmentProperties;

    public PollingReconciliationJob(OrchestraDataCacheContainer cacheContainer,
                                    OrchestraDataCacheUpdateService cacheUpdateService,
                                    AutonomousMedicalExamAssignmentService assignmentService,
                                    AssignmentProperties assignmentProperties) {
        this.cacheContainer = cacheContainer;
        this.cacheUpdateService = cacheUpdateService;
        this.assignmentService = assignmentService;
        this.assignmentProperties = assignmentProperties;
    }

    @Scheduled(cron = "${application.assignment.polling-cron}")
    public void reconcile() {
        if (!assignmentProperties.isEnabled()) {
            return;
        }
        for (BranchAssignmentCache branchCache : cacheContainer.getBranchCacheMap().values()) {
            if (!assignmentProperties.isAllowedBranch(branchCache.getBranchId())) {
                continue;
            }
            cacheUpdateService.ensureFresh(branchCache.getBranchId());
            for (ServicePointRuntimeState runtimeState : branchCache.getServicePointRuntimeStateMap().values()) {
                if (runtimeState.getStaffId() <= 0 || runtimeState.getWorkProfileId() <= 0) {
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
                log.info("Polling reconciliation for branch={} servicePoint={} staff={} workProfile={}",
                        context.getBranchId(), context.getServicePointId(), context.getStaffId(), context.getWorkProfileId());
                assignmentService.process(context);
            }
        }
    }
}
