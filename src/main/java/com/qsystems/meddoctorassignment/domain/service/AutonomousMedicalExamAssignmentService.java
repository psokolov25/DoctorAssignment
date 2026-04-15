package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
public class AutonomousMedicalExamAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousMedicalExamAssignmentService.class);

    private final OrchestraDataCacheUpdateService cacheUpdateService;
    private final OrchestraDataCacheContainer cacheContainer;
    private final DoctorAvailableServicesResolver doctorAvailableServicesResolver;
    private final UnknownDoctorQueueVisitProvider unknownDoctorQueueVisitProvider;
    private final VisitRouteAnalyzer visitRouteAnalyzer;
    private final DoctorServiceMatcher doctorServiceMatcher;
    private final VisitAssignmentExecutor visitAssignmentExecutor;
    private final BranchLockManager branchLockManager;
    private final ProcessedVisitRegistry processedVisitRegistry;
    private final AssignmentProperties assignmentProperties;

    public AutonomousMedicalExamAssignmentService(OrchestraDataCacheUpdateService cacheUpdateService,
                                                  OrchestraDataCacheContainer cacheContainer,
                                                  DoctorAvailableServicesResolver doctorAvailableServicesResolver,
                                                  UnknownDoctorQueueVisitProvider unknownDoctorQueueVisitProvider,
                                                  VisitRouteAnalyzer visitRouteAnalyzer,
                                                  DoctorServiceMatcher doctorServiceMatcher,
                                                  VisitAssignmentExecutor visitAssignmentExecutor,
                                                  BranchLockManager branchLockManager,
                                                  ProcessedVisitRegistry processedVisitRegistry,
                                                  AssignmentProperties assignmentProperties) {
        this.cacheUpdateService = cacheUpdateService;
        this.cacheContainer = cacheContainer;
        this.doctorAvailableServicesResolver = doctorAvailableServicesResolver;
        this.unknownDoctorQueueVisitProvider = unknownDoctorQueueVisitProvider;
        this.visitRouteAnalyzer = visitRouteAnalyzer;
        this.doctorServiceMatcher = doctorServiceMatcher;
        this.visitAssignmentExecutor = visitAssignmentExecutor;
        this.branchLockManager = branchLockManager;
        this.processedVisitRegistry = processedVisitRegistry;
        this.assignmentProperties = assignmentProperties;
    }

    public void process(DoctorContext doctorContext) {
        if (!assignmentProperties.isEnabled()) {
            return;
        }
        if (!assignmentProperties.isAllowedBranch(doctorContext.getBranchId())) {
            log.info("Skip branch {} because it is not allowed by configuration", doctorContext.getBranchId());
            return;
        }

        boolean lockAcquired = false;
        try {
            cacheUpdateService.ensureFresh(doctorContext.getBranchId());
            lockAcquired = branchLockManager.tryLock(doctorContext.getBranchId(), assignmentProperties.getBranchLockTimeoutMs());
            if (!lockAcquired) {
                log.warn("Could not acquire lock for branch {}", doctorContext.getBranchId());
                return;
            }

            BranchAssignmentCache branchCache = cacheContainer.getOrCreateBranchCache(doctorContext.getBranchId());
            branchCache.getServicePointRuntimeStateMap().put(
                    doctorContext.getServicePointId(),
                    new ServicePointRuntimeState(
                            doctorContext.getServicePointId(),
                            doctorContext.getBranchId(),
                            doctorContext.getStaffId(),
                            doctorContext.getWorkProfileId(),
                            "OPEN"));

            log.info("Start assignment cycle source={} branchId={} servicePointId={} staffId={} workProfileId={} sources={}",
                    doctorContext.getTriggerSource(),
                    doctorContext.getBranchId(),
                    doctorContext.getServicePointId(),
                    doctorContext.getStaffId(),
                    doctorContext.getWorkProfileId(),
                    doctorContext.describeSources());

            Set<Integer> doctorAvailableServices = doctorAvailableServicesResolver.resolve(doctorContext, branchCache);
            log.info("Doctor {} available services={}", doctorContext.getStaffId(), doctorAvailableServices);

            if (doctorAvailableServices.isEmpty()) {
                log.info("No available services for doctor {} and work profile {}", doctorContext.getStaffId(), doctorContext.getWorkProfileId());
                return;
            }

            if (branchCache.getUnknownDoctorQueueId() == null) {
                log.warn("Unknown-doctor queue is not resolved for branch {}. Check application.assignment.unknown-doctor-queue-id", doctorContext.getBranchId());
                return;
            }

            List<VisitSummary> visits = unknownDoctorQueueVisitProvider.getWaitingVisits(doctorContext, branchCache);
            log.info("Visits in unknown-doctor queue {} count={}", branchCache.getUnknownDoctorQueueId(), visits.size());

            int processed = 0;
            int limit = Math.min(visits.size(), assignmentProperties.getMaxVisitsPerCycle());
            Duration processedTtl = Duration.ofSeconds(assignmentProperties.getProcessedVisitTtlSeconds());

            for (int i = 0; i < limit; i++) {
                VisitSummary visit = visits.get(i);
                if (processedVisitRegistry.alreadyProcessed(doctorContext.getBranchId(), visit.getId(), doctorContext.getStaffId(), processedTtl)) {
                    log.info("Visit {} already processed recently for doctor {}", visit.getId(), doctorContext.getStaffId());
                    continue;
                }

                try {
                    VisitDetails visitDetails = visitRouteAnalyzer.analyze(doctorContext.getBranchId(), visit.getId());
                    Optional<SelectedDoctorService> selected = doctorServiceMatcher.match(visitDetails, doctorAvailableServices, branchCache);

                    if (!selected.isPresent()) {
                        log.info("Visit {} has no matching unserved service for doctor {}", visit.getId(), doctorContext.getStaffId());
                        continue;
                    }

                    boolean success = visitAssignmentExecutor.assign(
                            doctorContext,
                            visit,
                            selected.get(),
                            branchCache.getUnknownDoctorQueueId().intValue());

                    log.info("Visit {} matchFound=true selectedService={} targetQueue={} success={} reason={}",
                            visit.getId(),
                            selected.get().getServiceId(),
                            selected.get().getTargetQueueId(),
                            success,
                            selected.get().getSelectionReason());

                    if (success) {
                        if (!assignmentProperties.isDryRun()) {
                            processedVisitRegistry.markProcessed(doctorContext.getBranchId(), visit.getId(), doctorContext.getStaffId());
                        }
                        processed++;
                    }
                } catch (Exception exception) {
                    log.error("Failed to process visit {} in branch {}: {}", visit.getId(), doctorContext.getBranchId(), exception.getMessage(), exception);
                }
            }

            log.info("Finish assignment cycle source={} branchId={} processed={}",
                    doctorContext.getTriggerSource(),
                    doctorContext.getBranchId(),
                    processed);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for lock on branch {}", doctorContext.getBranchId(), interruptedException);
        } finally {
            if (lockAcquired) {
                branchLockManager.unlock(doctorContext.getBranchId());
            }
        }
    }
}
