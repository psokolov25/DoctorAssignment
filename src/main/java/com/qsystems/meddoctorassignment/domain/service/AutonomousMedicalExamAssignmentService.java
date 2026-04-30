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

            List<VisitSummary> visits = orderVisitsForProcessing(unknownDoctorQueueVisitProvider.getWaitingVisits(doctorContext, branchCache));
            int configuredLimit = Math.max(0, assignmentProperties.getMaxVisitsPerCycle());
            int limit = Math.min(visits.size(), configuredLimit);
            log.info("Visits in unknown-doctor queue {} count={} sortOrder={} maxVisitsPerCycle={} processingLimit={}",
                    branchCache.getUnknownDoctorQueueId(),
                    visits.size(),
                    resolveVisitProcessingSortOrder(),
                    configuredLimit,
                    limit);

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
            @Override
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
            @Override
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
            @Override
            public int compare(VisitSummary left, VisitSummary right) {
                return Long.compare(left.getId(), right.getId());
            }
        };
    }

    private Comparator<VisitSummary> idDescComparator() {
        return new Comparator<VisitSummary>() {
            @Override
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
