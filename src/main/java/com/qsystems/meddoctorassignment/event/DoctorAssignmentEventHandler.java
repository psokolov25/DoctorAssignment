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

    public DoctorAssignmentEventHandler(EventDeduplicator eventDeduplicator,
                                        LoggedDoctorContextResolver loggedDoctorContextResolver,
                                        AutonomousMedicalExamAssignmentService assignmentService,
                                        AssignmentProperties assignmentProperties) {
        this.eventDeduplicator = eventDeduplicator;
        this.loggedDoctorContextResolver = loggedDoctorContextResolver;
        this.assignmentService = assignmentService;
        this.assignmentProperties = assignmentProperties;
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
        return null;
    }
}
