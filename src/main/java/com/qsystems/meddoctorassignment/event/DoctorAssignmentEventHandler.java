package com.qsystems.meddoctorassignment.event;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.domain.service.LoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import com.qsystems.meddoctorassignment.util.EventDeduplicator;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный обработчик входящих событий Orchestra для сценария автоматического назначения визитов.
 *
 * <p>Класс не запускает mutating workflow "по любому событию". Вместо этого он строит
 * небольшой конечный автомат:
 * USER_SERVICE_POINT_SESSION_START открывает pending-session, следующий SET_WORK_PROFILE
 * завершает стабилизацию посадки и превращается во внутренний trigger USER_SESSION_READY,
 * а отдельный SET_WORK_PROFILE внутри уже активной сессии может породить trigger
 * WORK_PROFILE_EXPANDED, если новый профиль действительно расширил набор доступных услуг.</p>
 */
@Singleton
public class DoctorAssignmentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DoctorAssignmentEventHandler.class);

    private final EventDeduplicator eventDeduplicator;
    private final LoggedDoctorContextResolver loggedDoctorContextResolver;
    private final AutonomousMedicalExamAssignmentService assignmentService;
    private final AssignmentProperties assignmentProperties;
    private final UserSessionReadinessCoordinator userSessionReadinessCoordinator;
    private final WorkProfileExpansionTriggerEvaluator workProfileExpansionTriggerEvaluator;

    public DoctorAssignmentEventHandler(EventDeduplicator eventDeduplicator,
                                        LoggedDoctorContextResolver loggedDoctorContextResolver,
                                        AutonomousMedicalExamAssignmentService assignmentService,
                                        AssignmentProperties assignmentProperties,
                                        UserSessionReadinessCoordinator userSessionReadinessCoordinator,
                                        WorkProfileExpansionTriggerEvaluator workProfileExpansionTriggerEvaluator) {
        this.eventDeduplicator = eventDeduplicator;
        this.loggedDoctorContextResolver = loggedDoctorContextResolver;
        this.assignmentService = assignmentService;
        this.assignmentProperties = assignmentProperties;
        this.userSessionReadinessCoordinator = userSessionReadinessCoordinator;
        this.workProfileExpansionTriggerEvaluator = workProfileExpansionTriggerEvaluator;
    }

    /**
     * Обрабатывает одно нормализованное событие Orchestra.
     *
     * <p>На этом уровне происходит только маршрутизация и корреляция событий. Восстановление
     * полного {@link com.qsystems.meddoctorassignment.model.event.DoctorContext} и запуск
     * доменного цикла выполняются только для событий, которые уже признаны безопасными
     * trigger-ами.</p>
     */
    public void handle(OrchestraEvent event) {
        TriggerSource triggerSource = map(event.getEventName());
        if (triggerSource == null) {
            return;
        }

        boolean duplicate = eventDeduplicator.isDuplicate(event, Duration.ofSeconds(assignmentProperties.getEventDeduplicationTtlSeconds()));
        if (duplicate) {
            log.info("Duplicate event skipped {}", event.getEventName());
            return;
        }

        if (triggerSource == TriggerSource.USER_SERVICE_POINT_SESSION_START) {
            userSessionReadinessCoordinator.registerSessionStart(event);
            return;
        }

        if (triggerSource == TriggerSource.SET_WORK_PROFILE) {
            Optional<UserSessionReadinessCoordinator.CompletedUserSession> readySession = userSessionReadinessCoordinator.completeIfReady(event);
            if (readySession.isPresent()) {
                OrchestraEvent readyEvent = readySession.get().toReadyEvent();
                DoctorContext doctorContext = loggedDoctorContextResolver.resolve(readyEvent, TriggerSource.USER_SESSION_READY);
                assignmentService.process(doctorContext);
                return;
            }

            WorkProfileExpansionTriggerEvaluator.EvaluationResult expansionResult = workProfileExpansionTriggerEvaluator.evaluate(event);
            if (expansionResult.isTriggered()) {
                if (!assignmentProperties.isTriggerEnabled(TriggerSource.WORK_PROFILE_EXPANDED)) {
                    log.info("Skip expanded SET_WORK_PROFILE because trigger {} is disabled by configuration", TriggerSource.WORK_PROFILE_EXPANDED);
                    return;
                }
                assignmentService.process(expansionResult.getDoctorContext());
                return;
            }

            if (!assignmentProperties.isTriggerEnabled(TriggerSource.SET_WORK_PROFILE)) {
                log.info("Skip raw SET_WORK_PROFILE because no matching USER_SERVICE_POINT_SESSION_START was found and raw trigger is disabled (reason={})", expansionResult.getReason());
                return;
            }
        }

        if (!assignmentProperties.isTriggerEnabled(triggerSource)) {
            log.info("Skip event {} because trigger {} is disabled by configuration", event.getEventName(), triggerSource);
            return;
        }

        DoctorContext doctorContext = loggedDoctorContextResolver.resolve(event, triggerSource);
        assignmentService.process(doctorContext);
    }

    private TriggerSource map(String eventName) {
        if ("SERVICE_POINT_OPEN".equals(eventName)) {
            return TriggerSource.SERVICE_POINT_OPEN;
        }
        if ("SET_WORK_PROFILE".equals(eventName)) {
            return TriggerSource.SET_WORK_PROFILE;
        }
        if ("USER_SERVICE_POINT_SESSION_START".equals(eventName)) {
            return TriggerSource.USER_SERVICE_POINT_SESSION_START;
        }
        return null;
    }
}
