package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.adapter.gateway.OperatorContextActivationGateway;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.exception.MutationContextException;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Центральный доменный оркестратор цикла автоматического назначения врача.
 *
 * <p>Класс ничего не знает о формате websocket, конкретных REST endpoint-ах или способе хранения
 * кэша. Он опирается только на абстракции домена и координирует весь workflow обработки.</p>
 */
@Singleton
public class AutonomousMedicalExamAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousMedicalExamAssignmentService.class);

    private final OrchestraDataCacheUpdateService cacheUpdateService;
    private final OrchestraDataCacheContainer cacheContainer;
    private final DoctorAvailableServicesResolver doctorAvailableServicesResolver;
    private final UnknownDoctorQueueVisitProvider unknownDoctorQueueVisitProvider;
    private final VisitRouteAnalyzer visitRouteAnalyzer;
    private final DoctorServiceSelectionService doctorServiceSelectionService;
    private final VisitAssignmentExecutor visitAssignmentExecutor;
    private final OperatorContextActivationGateway operatorContextActivationGateway;
    private final BranchLockManager branchLockManager;
    private final ProcessedVisitRegistry processedVisitRegistry;
    private final AssignmentProperties assignmentProperties;

    public AutonomousMedicalExamAssignmentService(OrchestraDataCacheUpdateService cacheUpdateService,
                                                  OrchestraDataCacheContainer cacheContainer,
                                                  DoctorAvailableServicesResolver doctorAvailableServicesResolver,
                                                  UnknownDoctorQueueVisitProvider unknownDoctorQueueVisitProvider,
                                                  VisitRouteAnalyzer visitRouteAnalyzer,
                                                  DoctorServiceSelectionService doctorServiceSelectionService,
                                                  VisitAssignmentExecutor visitAssignmentExecutor,
                                                  OperatorContextActivationGateway operatorContextActivationGateway,
                                                  BranchLockManager branchLockManager,
                                                  ProcessedVisitRegistry processedVisitRegistry,
                                                  AssignmentProperties assignmentProperties) {
        this.cacheUpdateService = cacheUpdateService;
        this.cacheContainer = cacheContainer;
        this.doctorAvailableServicesResolver = doctorAvailableServicesResolver;
        this.unknownDoctorQueueVisitProvider = unknownDoctorQueueVisitProvider;
        this.visitRouteAnalyzer = visitRouteAnalyzer;
        this.doctorServiceSelectionService = doctorServiceSelectionService;
        this.visitAssignmentExecutor = visitAssignmentExecutor;
        this.operatorContextActivationGateway = operatorContextActivationGateway;
        this.branchLockManager = branchLockManager;
        this.processedVisitRegistry = processedVisitRegistry;
        this.assignmentProperties = assignmentProperties;
    }

    /**
     * Запускает полный цикл назначения врача для одного события или polling-триггера.
     *
     * <p>Последовательность шагов:</p>
     * <ol>
     *     <li>проверка глобальных флагов и белого списка отделений;</li>
     *     <li>обновление branch cache при необходимости;</li>
     *     <li>получение branch-level lock;</li>
     *     <li>вычисление доступных врачу услуг;</li>
     *     <li>чтение визитов из очереди "врач не назначен";</li>
     *     <li>поочередный анализ маршрута каждого визита;</li>
     *     <li>назначение услуги и перевод визита.</li>
     * </ol>
     *
     * @param doctorContext нормализованный контекст врача и service point
     */
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
            // Перед принятием решения убеждаемся, что branch cache не устарел.
            cacheUpdateService.ensureFresh(doctorContext.getBranchId());

            // Один branch обрабатываем только одним потоком, чтобы websocket и polling не конфликтовали.
            lockAcquired = branchLockManager.tryLock(doctorContext.getBranchId(), assignmentProperties.getBranchLockTimeoutMs());
            if (!lockAcquired) {
                log.warn("Could not acquire lock for branch {}", doctorContext.getBranchId());
                return;
            }

            BranchAssignmentCache branchCache = cacheContainer.getOrCreateBranchCache(doctorContext.getBranchId());

            // Обновляем runtime-срез service point локально, даже если событие пришло раньше следующего cache refresh.
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

            try {
                operatorContextActivationGateway.activate(doctorContext);
            } catch (Exception activationException) {
                log.error("Activation step failed source={} branchId={} servicePointId={} staffId={} workProfileId={}: {}",
                        doctorContext.getTriggerSource(),
                        doctorContext.getBranchId(),
                        doctorContext.getServicePointId(),
                        doctorContext.getStaffId(),
                        doctorContext.getWorkProfileId(),
                        activationException.getMessage(),
                        activationException);
                if (shouldAbortCycle(activationException)) {
                    log.warn("Abort assignment cycle source={} branchId={} before first visit because activation step did not produce a valid mutating context: {}",
                            doctorContext.getTriggerSource(),
                            doctorContext.getBranchId(),
                            activationException.getMessage());
                    return;
                }
                throw activationException;
            }

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

                // Повторная обработка одного визита тем же врачом в коротком окне времени обычно означает
                // дубль события или повторный запуск fallback-задачи. Пропускаем такой визит без ошибки.
                if (processedVisitRegistry.alreadyProcessed(doctorContext.getBranchId(), visit.getId(), doctorContext.getStaffId(), processedTtl)) {
                    log.info("Visit {} already processed recently for doctor {}", visit.getId(), doctorContext.getStaffId());
                    continue;
                }

                try {
                    VisitDetails visitDetails = visitRouteAnalyzer.analyze(doctorContext.getBranchId(), visit.getId());
                    Optional<SelectedDoctorService> selected = doctorServiceSelectionService.select(visitDetails, doctorAvailableServices, branchCache);

                    if (!selected.isPresent()) {
                        log.info("Visit {} has no matching unserved service for doctor {}", visit.getId(), doctorContext.getStaffId());
                        continue;
                    }

                    boolean success = visitAssignmentExecutor.assign(
                            doctorContext,
                            visit,
                            visitDetails,
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
                    if (shouldAbortCycle(exception)) {
                        log.warn("Abort assignment cycle source={} branchId={} after visit {} because mutating context is invalid: {}",
                                doctorContext.getTriggerSource(),
                                doctorContext.getBranchId(),
                                visit.getId(),
                                exception.getMessage());
                        break;
                    }
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

    private boolean shouldAbortCycle(Exception exception) {
        if (exception instanceof MutationContextException) {
            return true;
        }
        if (!assignmentProperties.isAbortCycleOnForbiddenMutation()) {
            return false;
        }

        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof HttpClientResponseException) {
                HttpClientResponseException httpException = (HttpClientResponseException) cursor;
                if (httpException.getStatus() != null && httpException.getStatus().getCode() == 403) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
