package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.adapter.gateway.OperatorContextActivationGateway;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.VisitProcessingSortOrder;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

            List<VisitSummary> visits = orderVisitsForProcessing(unknownDoctorQueueVisitProvider.getWaitingVisits(doctorContext, branchCache));
            int configuredLimit = Math.max(0, assignmentProperties.getMaxVisitsPerCycle());
            int limit = Math.min(visits.size(), configuredLimit);
            log.info("Visits in unknown-doctor queue {} count={} sortOrder={} maxVisitsPerCycle={} processingLimit={}",
                    branchCache.getUnknownDoctorQueueId(),
                    Integer.valueOf(visits.size()),
                    resolveVisitProcessingSortOrder(),
                    Integer.valueOf(configuredLimit),
                    Integer.valueOf(limit));

            int processed = 0;
            Duration processedTtl = Duration.ofSeconds(assignmentProperties.getProcessedVisitTtlSeconds());

            for (int i = 0; i < limit; i++) {
                VisitSummary visit = visits.get(i);

                try {
                    VisitDetails visitDetails = visitRouteAnalyzer.analyze(doctorContext.getBranchId(), visit.getId());
                    String processingFingerprint = buildProcessingFingerprint(visitDetails);

                    // Повторная обработка одного и того же маршрутного шага тем же врачом в коротком
                    // окне времени обычно означает дубль события или повторный запуск fallback-задачи.
                    // Но один и тот же visitId может несколько раз возвращаться в «Врач не в системе»:
                    // тогда currentVisitService.id меняется, и визит должен снова обратиться к med-robot.
                    if (processedVisitRegistry.alreadyProcessed(
                            doctorContext.getBranchId(),
                            visit.getId(),
                            doctorContext.getStaffId(),
                            processingFingerprint,
                            processedTtl)) {
                        log.info("Visit {} already processed recently for doctor {} processingFingerprint={}",
                                visit.getId(),
                                doctorContext.getStaffId(),
                                processingFingerprint);
                        continue;
                    }

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
                            processedVisitRegistry.markProcessed(
                                    doctorContext.getBranchId(),
                                    visit.getId(),
                                    doctorContext.getStaffId(),
                                    processingFingerprint);
                            if (i + 1 < limit) {
                                refreshBranchSnapshotAfterSuccessfulAssignment(doctorContext.getBranchId(), "event");
                            }
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


    /**
     * Запускает polling-цикл на уровне отделения, а не отдельного врача.
     *
     * <p>В отличие от {@link #process(DoctorContext)}, этот метод читает очередь "Врач не назначен"
     * один раз на branch, применяет {@code maxVisitsPerCycle} к общему списку визитов и затем
     * распределяет выбранные визиты между доступными врачами. Это предотвращает ситуацию, когда
     * polling последовательно запускает по одному циклу на каждого врача и фактически умножает
     * лимит на количество открытых service point-ов.</p>
     */
    public void processPollingBranch(int branchId, List<DoctorContext> doctorContexts) {
        if (!assignmentProperties.isEnabled()) {
            return;
        }
        if (!assignmentProperties.isAllowedBranch(branchId)) {
            log.info("Skip polling branch {} because it is not allowed by configuration", Integer.valueOf(branchId));
            return;
        }
        if (doctorContexts == null || doctorContexts.isEmpty()) {
            log.info("Skip polling branch {} because there are no open doctor contexts", Integer.valueOf(branchId));
            return;
        }

        boolean lockAcquired = false;
        try {
            cacheUpdateService.ensureFresh(branchId);

            lockAcquired = branchLockManager.tryLock(branchId, assignmentProperties.getBranchLockTimeoutMs());
            if (!lockAcquired) {
                log.warn("Could not acquire polling lock for branch {}", Integer.valueOf(branchId));
                return;
            }

            BranchAssignmentCache branchCache = cacheContainer.getOrCreateBranchCache(branchId);
            if (branchCache.getUnknownDoctorQueueId() == null) {
                log.warn("Unknown-doctor queue is not resolved for branch {}. Check application.assignment.unknown-doctor-queue-id", Integer.valueOf(branchId));
                return;
            }

            List<DoctorSelectionContext> doctorSelectionContexts = resolvePollingDoctorSelectionContexts(branchId, doctorContexts, branchCache);
            if (doctorSelectionContexts.isEmpty()) {
                log.info("Polling branch {} has no doctors with available services", Integer.valueOf(branchId));
                return;
            }

            DoctorContext queueReadContext = doctorSelectionContexts.get(0).doctorContext;
            List<VisitSummary> visits = orderVisitsForProcessing(unknownDoctorQueueVisitProvider.getWaitingVisits(queueReadContext, branchCache));
            int configuredLimit = Math.max(0, assignmentProperties.getMaxVisitsPerCycle());
            int limit = Math.min(visits.size(), configuredLimit);
            log.info("Polling branch assignment source=POLLING branchId={} doctors={} unknownDoctorQueue={} count={} sortOrder={} maxVisitsPerCycle={} processingLimit={}",
                    Integer.valueOf(branchId),
                    Integer.valueOf(doctorSelectionContexts.size()),
                    branchCache.getUnknownDoctorQueueId(),
                    Integer.valueOf(visits.size()),
                    resolveVisitProcessingSortOrder(),
                    Integer.valueOf(configuredLimit),
                    Integer.valueOf(limit));

            int processed = 0;
            int roundRobinCursor = 0;
            Duration processedTtl = Duration.ofSeconds(assignmentProperties.getProcessedVisitTtlSeconds());
            Map<Integer, Integer> targetQueueLoad = new HashMap<Integer, Integer>();
            Map<Integer, Integer> staffLoad = new HashMap<Integer, Integer>();

            for (int i = 0; i < limit; i++) {
                VisitSummary visit = visits.get(i);
                try {
                    VisitDetails visitDetails = visitRouteAnalyzer.analyze(branchId, visit.getId());
                    String processingFingerprint = buildProcessingFingerprint(visitDetails);

                    Optional<BalancedSelection> selected = selectBalancedDoctorForVisit(
                            visit,
                            visitDetails,
                            doctorSelectionContexts,
                            branchCache,
                            targetQueueLoad,
                            staffLoad,
                            roundRobinCursor,
                            processedTtl,
                            processingFingerprint);

                    if (!selected.isPresent()) {
                        log.info("Polling visit {} has no matching doctor among {} candidate doctors",
                                Long.valueOf(visit.getId()),
                                Integer.valueOf(doctorSelectionContexts.size()));
                        continue;
                    }

                    BalancedSelection decision = selected.get();
                    try {
                        operatorContextActivationGateway.activate(decision.doctorSelectionContext.doctorContext);
                    } catch (Exception activationException) {
                        log.error("Activation step failed before polling assignment branchId={} visit={} servicePointId={} staffId={} workProfileId={}: {}",
                                Integer.valueOf(branchId),
                                Long.valueOf(visit.getId()),
                                Long.valueOf(decision.doctorSelectionContext.doctorContext.getServicePointId()),
                                Integer.valueOf(decision.doctorSelectionContext.doctorContext.getStaffId()),
                                Integer.valueOf(decision.doctorSelectionContext.doctorContext.getWorkProfileId()),
                                activationException.getMessage(),
                                activationException);
                        if (shouldAbortCycle(activationException)) {
                            log.warn("Abort polling assignment branchId={} before visit {} because activation step did not produce a valid mutating context: {}",
                                    Integer.valueOf(branchId),
                                    Long.valueOf(visit.getId()),
                                    activationException.getMessage());
                            break;
                        }
                        throw activationException;
                    }

                    boolean success = visitAssignmentExecutor.assign(
                            decision.doctorSelectionContext.doctorContext,
                            visit,
                            visitDetails,
                            decision.selectedDoctorService,
                            branchCache.getUnknownDoctorQueueId().intValue());

                    log.info("Polling visit {} matchFound=true selectedService={} targetQueue={} staffId={} servicePointId={} success={} reason={}",
                            Long.valueOf(visit.getId()),
                            Integer.valueOf(decision.selectedDoctorService.getServiceId()),
                            Integer.valueOf(decision.selectedDoctorService.getTargetQueueId()),
                            Integer.valueOf(decision.doctorSelectionContext.doctorContext.getStaffId()),
                            Long.valueOf(decision.doctorSelectionContext.doctorContext.getServicePointId()),
                            Boolean.valueOf(success),
                            decision.selectedDoctorService.getSelectionReason());

                    if (success) {
                        if (!assignmentProperties.isDryRun()) {
                            processedVisitRegistry.markProcessed(
                                    branchId,
                                    visit.getId(),
                                    decision.doctorSelectionContext.doctorContext.getStaffId(),
                                    processingFingerprint);
                            if (i + 1 < limit) {
                                refreshBranchSnapshotAfterSuccessfulAssignment(branchId, "polling");
                            }
                        }
                        increment(targetQueueLoad, Integer.valueOf(decision.selectedDoctorService.getTargetQueueId()));
                        increment(staffLoad, Integer.valueOf(decision.doctorSelectionContext.doctorContext.getStaffId()));
                        processed++;
                        roundRobinCursor = (decision.candidateIndex + 1) % doctorSelectionContexts.size();
                    }
                } catch (Exception exception) {
                    log.error("Failed to process polling visit {} in branch {}: {}", Long.valueOf(visit.getId()), Integer.valueOf(branchId), exception.getMessage(), exception);
                    if (shouldAbortCycle(exception)) {
                        log.warn("Abort polling assignment branchId={} after visit {} because mutating context is invalid: {}",
                                Integer.valueOf(branchId),
                                Long.valueOf(visit.getId()),
                                exception.getMessage());
                        break;
                    }
                }
            }

            log.info("Finish polling assignment branchId={} processed={} doctors={}",
                    Integer.valueOf(branchId),
                    Integer.valueOf(processed),
                    Integer.valueOf(doctorSelectionContexts.size()));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for polling lock on branch {}", Integer.valueOf(branchId), interruptedException);
        } finally {
            if (lockAcquired) {
                branchLockManager.unlock(branchId);
            }
        }
    }

    private List<DoctorSelectionContext> resolvePollingDoctorSelectionContexts(
            int branchId,
            List<DoctorContext> doctorContexts,
            BranchAssignmentCache branchCache) {
        List<DoctorSelectionContext> result = new ArrayList<DoctorSelectionContext>();
        for (DoctorContext doctorContext : doctorContexts) {
            if (doctorContext == null || doctorContext.getBranchId() != branchId) {
                continue;
            }
            if (doctorContext.getStaffId() <= 0 || doctorContext.getWorkProfileId() <= 0 || doctorContext.getServicePointId() <= 0) {
                continue;
            }
            branchCache.getServicePointRuntimeStateMap().put(
                    Long.valueOf(doctorContext.getServicePointId()),
                    new ServicePointRuntimeState(
                            doctorContext.getServicePointId(),
                            branchId,
                            doctorContext.getStaffId(),
                            doctorContext.getWorkProfileId(),
                            "OPEN"));
            Set<Integer> availableServices = doctorAvailableServicesResolver.resolve(doctorContext, branchCache);
            log.info("Polling doctor {} available services={} servicePoint={} workProfile={}",
                    Integer.valueOf(doctorContext.getStaffId()),
                    availableServices,
                    Long.valueOf(doctorContext.getServicePointId()),
                    Integer.valueOf(doctorContext.getWorkProfileId()));
            if (!availableServices.isEmpty()) {
                result.add(new DoctorSelectionContext(doctorContext, availableServices));
            }
        }
        Collections.sort(result, new Comparator<DoctorSelectionContext>() {
            @Override
            public int compare(DoctorSelectionContext left, DoctorSelectionContext right) {
                int servicePointCompare = Long.compare(left.doctorContext.getServicePointId(), right.doctorContext.getServicePointId());
                if (servicePointCompare != 0) {
                    return servicePointCompare;
                }
                return Integer.compare(left.doctorContext.getStaffId(), right.doctorContext.getStaffId());
            }
        });
        return result;
    }

    private Optional<BalancedSelection> selectBalancedDoctorForVisit(
            VisitSummary visit,
            VisitDetails visitDetails,
            List<DoctorSelectionContext> doctorSelectionContexts,
            BranchAssignmentCache branchCache,
            Map<Integer, Integer> targetQueueLoad,
            Map<Integer, Integer> staffLoad,
            int roundRobinCursor,
            Duration processedTtl,
            String processingFingerprint) {
        List<BalancedSelection> selections = new ArrayList<BalancedSelection>();
        for (int candidateIndex = 0; candidateIndex < doctorSelectionContexts.size(); candidateIndex++) {
            DoctorSelectionContext candidate = doctorSelectionContexts.get(candidateIndex);
            if (processedVisitRegistry.alreadyProcessed(
                    candidate.doctorContext.getBranchId(),
                    visit.getId(),
                    candidate.doctorContext.getStaffId(),
                    processingFingerprint,
                    processedTtl)) {
                log.info("Polling visit {} already processed recently for doctor {} processingFingerprint={}",
                        Long.valueOf(visit.getId()),
                        Integer.valueOf(candidate.doctorContext.getStaffId()),
                        processingFingerprint);
                continue;
            }

            Optional<SelectedDoctorService> selected = doctorServiceSelectionService.select(
                    visitDetails,
                    candidate.availableServices,
                    branchCache);
            if (selected.isPresent()) {
                selections.add(new BalancedSelection(candidateIndex, candidate, selected.get()));
            }
        }
        if (selections.isEmpty()) {
            return Optional.empty();
        }

        final int doctorCount = doctorSelectionContexts.size();
        final int cursor = normalizeRoundRobinCursor(roundRobinCursor, doctorCount);
        Collections.sort(selections, new Comparator<BalancedSelection>() {
            @Override
            public int compare(BalancedSelection left, BalancedSelection right) {
                int queueLoadCompare = Integer.compare(
                        getCount(targetQueueLoad, Integer.valueOf(left.selectedDoctorService.getTargetQueueId())),
                        getCount(targetQueueLoad, Integer.valueOf(right.selectedDoctorService.getTargetQueueId())));
                if (queueLoadCompare != 0) {
                    return queueLoadCompare;
                }
                int staffLoadCompare = Integer.compare(
                        getCount(staffLoad, Integer.valueOf(left.doctorSelectionContext.doctorContext.getStaffId())),
                        getCount(staffLoad, Integer.valueOf(right.doctorSelectionContext.doctorContext.getStaffId())));
                if (staffLoadCompare != 0) {
                    return staffLoadCompare;
                }
                int cursorCompare = Integer.compare(
                        roundRobinDistance(left.candidateIndex, cursor, doctorCount),
                        roundRobinDistance(right.candidateIndex, cursor, doctorCount));
                if (cursorCompare != 0) {
                    return cursorCompare;
                }
                int routeCompare = compareNullableRouteOrder(left.selectedDoctorService.getRouteOrder(), right.selectedDoctorService.getRouteOrder());
                if (routeCompare != 0) {
                    return routeCompare;
                }
                int serviceCompare = Integer.compare(left.selectedDoctorService.getServiceId(), right.selectedDoctorService.getServiceId());
                if (serviceCompare != 0) {
                    return serviceCompare;
                }
                return Integer.compare(
                        left.doctorSelectionContext.doctorContext.getStaffId(),
                        right.doctorSelectionContext.doctorContext.getStaffId());
            }
        });
        return Optional.of(selections.get(0));
    }

    private void increment(Map<Integer, Integer> load, Integer key) {
        Integer current = load.get(key);
        load.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    private static int getCount(Map<Integer, Integer> load, Integer key) {
        Integer current = load.get(key);
        return current != null ? current.intValue() : 0;
    }

    private static int normalizeRoundRobinCursor(int cursor, int size) {
        if (size <= 0) {
            return 0;
        }
        int normalized = cursor % size;
        return normalized >= 0 ? normalized : normalized + size;
    }

    private static int roundRobinDistance(int index, int cursor, int size) {
        if (size <= 0) {
            return 0;
        }
        return (index - cursor + size) % size;
    }

    private static int compareNullableRouteOrder(Integer left, Integer right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static final class DoctorSelectionContext {
        private final DoctorContext doctorContext;
        private final Set<Integer> availableServices;

        private DoctorSelectionContext(DoctorContext doctorContext, Set<Integer> availableServices) {
            this.doctorContext = doctorContext;
            this.availableServices = availableServices;
        }
    }

    private static final class BalancedSelection {
        private final int candidateIndex;
        private final DoctorSelectionContext doctorSelectionContext;
        private final SelectedDoctorService selectedDoctorService;

        private BalancedSelection(int candidateIndex,
                                  DoctorSelectionContext doctorSelectionContext,
                                  SelectedDoctorService selectedDoctorService) {
            this.candidateIndex = candidateIndex;
            this.doctorSelectionContext = doctorSelectionContext;
            this.selectedDoctorService = selectedDoctorService;
        }
    }


    private List<VisitSummary> orderVisitsForProcessing(List<VisitSummary> visits) {
        if (visits == null || visits.isEmpty()) {
            return Collections.emptyList();
        }

        List<VisitSummary> orderedVisits = new ArrayList<VisitSummary>(visits);
        VisitProcessingSortOrder sortOrder = resolveVisitProcessingSortOrder();
        switch (sortOrder) {
            case OLDEST_FIRST:
                Collections.sort(orderedVisits, oldestFirstComparator());
                break;
            case NEWEST_FIRST:
                Collections.sort(orderedVisits, newestFirstComparator());
                break;
            case ID_ASC:
                Collections.sort(orderedVisits, idAscComparator());
                break;
            case ID_DESC:
                Collections.sort(orderedVisits, idDescComparator());
                break;
            case AS_RETURNED:
            default:
                break;
        }
        return orderedVisits;
    }

    private VisitProcessingSortOrder resolveVisitProcessingSortOrder() {
        VisitProcessingSortOrder sortOrder = assignmentProperties.getVisitProcessingSortOrder();
        return sortOrder != null ? sortOrder : VisitProcessingSortOrder.AS_RETURNED;
    }

    private Comparator<VisitSummary> oldestFirstComparator() {
        return new Comparator<VisitSummary>() {
            
            public int compare(VisitSummary left, VisitSummary right) {
                int waitingTimeCompare = compareWaitingTimeDescNullsLast(left, right);
                if (waitingTimeCompare != 0) {
                    return waitingTimeCompare;
                }
                return Long.compare(left.getId(), right.getId());
            }
        };
    }

    private Comparator<VisitSummary> newestFirstComparator() {
        return new Comparator<VisitSummary>() {
            
            public int compare(VisitSummary left, VisitSummary right) {
                int waitingTimeCompare = compareWaitingTimeAscNullsLast(left, right);
                if (waitingTimeCompare != 0) {
                    return waitingTimeCompare;
                }
                return Long.compare(right.getId(), left.getId());
            }
        };
    }

    private Comparator<VisitSummary> idAscComparator() {
        return new Comparator<VisitSummary>() {
            
            public int compare(VisitSummary left, VisitSummary right) {
                return Long.compare(left.getId(), right.getId());
            }
        };
    }

    private Comparator<VisitSummary> idDescComparator() {
        return new Comparator<VisitSummary>() {
            
            public int compare(VisitSummary left, VisitSummary right) {
                return Long.compare(right.getId(), left.getId());
            }
        };
    }

    private int compareWaitingTimeDescNullsLast(VisitSummary left, VisitSummary right) {
        Integer leftWaitingTime = left.getWaitingTime();
        Integer rightWaitingTime = right.getWaitingTime();
        if (leftWaitingTime == null && rightWaitingTime == null) {
            return 0;
        }
        if (leftWaitingTime == null) {
            return 1;
        }
        if (rightWaitingTime == null) {
            return -1;
        }
        return rightWaitingTime.compareTo(leftWaitingTime);
    }

    private int compareWaitingTimeAscNullsLast(VisitSummary left, VisitSummary right) {
        Integer leftWaitingTime = left.getWaitingTime();
        Integer rightWaitingTime = right.getWaitingTime();
        if (leftWaitingTime == null && rightWaitingTime == null) {
            return 0;
        }
        if (leftWaitingTime == null) {
            return 1;
        }
        if (rightWaitingTime == null) {
            return -1;
        }
        return leftWaitingTime.compareTo(rightWaitingTime);
    }

    private String buildProcessingFingerprint(VisitDetails visitDetails) {
        if (visitDetails == null) {
            return "details-absent";
        }
        if (visitDetails.getCurrentVisitServiceRecordId() != null) {
            return "currentVisitServiceRecordId=" + visitDetails.getCurrentVisitServiceRecordId();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("currentServiceId=").append(visitDetails.getCurrentServiceId());
        builder.append("|queueId=").append(visitDetails.getQueueId());
        builder.append("|unserved=");
        if (visitDetails.getUnservedServices() == null || visitDetails.getUnservedServices().isEmpty()) {
            builder.append("empty");
            return builder.toString();
        }
        for (int i = 0; i < visitDetails.getUnservedServices().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            if (visitDetails.getUnservedServices().get(i) == null) {
                builder.append("null");
            } else {
                builder.append(visitDetails.getUnservedServices().get(i).getServiceId())
                        .append(':')
                        .append(visitDetails.getUnservedServices().get(i).getExternalKey())
                        .append(':')
                        .append(visitDetails.getUnservedServices().get(i).getRouteOrder());
            }
        }
        return builder.toString();
    }

    private void refreshBranchSnapshotAfterSuccessfulAssignment(int branchId, String source) {
        try {
            cacheUpdateService.ensureFresh(branchId);
            log.debug("Refreshed branch snapshot after successful {} assignment branchId={}", source, Integer.valueOf(branchId));
        } catch (Exception refreshException) {
            log.warn("Could not refresh branch snapshot after successful {} assignment branchId={}: {}",
                    source,
                    Integer.valueOf(branchId),
                    refreshException.getMessage());
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
