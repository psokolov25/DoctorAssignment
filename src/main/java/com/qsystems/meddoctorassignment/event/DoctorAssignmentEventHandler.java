package com.qsystems.meddoctorassignment.event;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.domain.service.LoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import com.qsystems.meddoctorassignment.util.EventDeduplicator;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Обработчик входящих событий Orchestra, относящихся к началу работы врача.
 */
@Singleton
public class DoctorAssignmentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DoctorAssignmentEventHandler.class);

    private final EventDeduplicator eventDeduplicator;
    private final LoggedDoctorContextResolver loggedDoctorContextResolver;
    private final AutonomousMedicalExamAssignmentService assignmentService;
    private final AssignmentProperties assignmentProperties;
    private final UserSessionReadinessCoordinator userSessionReadinessCoordinator;

    public DoctorAssignmentEventHandler(EventDeduplicator eventDeduplicator,
                                        LoggedDoctorContextResolver loggedDoctorContextResolver,
                                        AutonomousMedicalExamAssignmentService assignmentService,
                                        AssignmentProperties assignmentProperties,
                                        UserSessionReadinessCoordinator userSessionReadinessCoordinator) {
        this.eventDeduplicator = eventDeduplicator;
        this.loggedDoctorContextResolver = loggedDoctorContextResolver;
        this.assignmentService = assignmentService;
        this.assignmentProperties = assignmentProperties;
        this.userSessionReadinessCoordinator = userSessionReadinessCoordinator;
    }

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
            if (!assignmentProperties.isTriggerEnabled(TriggerSource.SET_WORK_PROFILE)) {
                log.info("Skip raw SET_WORK_PROFILE because no matching USER_SERVICE_POINT_SESSION_START was found and raw trigger is disabled");
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
