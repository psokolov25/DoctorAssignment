package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.MissingRobotServiceAddFailureMode;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.VisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Исполнитель доменного решения: назначает услугу визиту и переводит его в целевую очередь.
 *
 * <p>Ключевая особенность текущей реализации — поддержка ветки {@code transfer-only}.
 * Если визит уже стоит на той услуге, которую алгоритм выбрал для врача, сервис пропускает
 * redundant assign и сразу делает перевод в очередь врача. Это резко уменьшает число лишних
 * PUT-запросов и позволяет не ломать цикл на no-op assign.</p>
 */
@Singleton
public class DefaultVisitAssignmentExecutor implements VisitAssignmentExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultVisitAssignmentExecutor.class);

    private final VisitWorkflowGateway visitWorkflowGateway;
    private final AssignmentProperties assignmentProperties;

    public DefaultVisitAssignmentExecutor(VisitWorkflowGateway visitWorkflowGateway,
                                          AssignmentProperties assignmentProperties) {
        this.visitWorkflowGateway = visitWorkflowGateway;
        this.assignmentProperties = assignmentProperties;
    }

    /**
     * Выполняет одно доменное решение по конкретному визиту.
     *
     * <p>Метод сначала делает defensive recheck текущей очереди, затем выбирает между
     * режимами {@code assign+transfer} и {@code transfer-only}, а после перевода может
     * дополнительно перечитать визит и убедиться, что он действительно оказался в ожидаемой
     * очереди.</p>
     */
    @Override
    public boolean assign(DoctorContext doctorContext,
                          VisitSummary visitSummary,
                          VisitDetails visitDetails,
                          SelectedDoctorService selectedDoctorService,
                          int unknownDoctorQueueId) {
        if (assignmentProperties.isDryRun()) {
            log.info("DRY-RUN visit={} service={} targetQueue={} doctor={} servicePoint={}",
                    visitSummary.getId(),
                    selectedDoctorService.getServiceId(),
                    selectedDoctorService.getTargetQueueId(),
                    doctorContext.getStaffId(),
                    doctorContext.getServicePointId());
            return true;
        }

        // Перед изменением визита еще раз убеждаемся, что он не ушел в другую очередь.
        if (assignmentProperties.isRecheckVisitBeforeTransfer()) {
            Optional<VisitSummary> actualVisit = visitWorkflowGateway.findVisit(doctorContext.getBranchId(), visitSummary.getId());
            if (actualVisit.isPresent() && actualVisit.get().getQueueId() != null && actualVisit.get().getQueueId().intValue() != unknownDoctorQueueId) {
                log.warn("Skip visit {} because queue already changed from unknown-doctor queue {}", visitSummary.getId(), unknownDoctorQueueId);
                return false;
            }
        }

        log.info("Assign workflow start visit={} currentQueue={} currentService={} selectedService={} targetQueue={} doctor={} servicePoint={} workProfile={} trigger={}",
                visitSummary.getId(),
                visitSummary.getQueueId(),
                visitDetails != null ? visitDetails.getCurrentServiceId() : null,
                selectedDoctorService.getServiceId(),
                selectedDoctorService.getTargetQueueId(),
                doctorContext.getStaffId(),
                doctorContext.getServicePointId(),
                doctorContext.getWorkProfileId(),
                doctorContext.getTriggerSource());

        if (shouldAddMissingRobotServiceToVisit(visitDetails, selectedDoctorService)) {
            log.info("Selected med-robot service {} is absent in visit {} unserved route and is not current service. Add service to visit before assign/transfer.",
                    selectedDoctorService.getServiceId(),
                    visitSummary.getId());
            try {
                visitWorkflowGateway.addServiceToVisit(
                        doctorContext.getBranchId(),
                        visitSummary.getId(),
                        selectedDoctorService.getServiceId());
                rememberAddedService(visitDetails, selectedDoctorService.getServiceId());
            } catch (RuntimeException exception) {
                MissingRobotServiceAddFailureMode failureMode = assignmentProperties.getAddMissingRobotServiceFailureMode();
                if (failureMode == MissingRobotServiceAddFailureMode.SKIP_VISIT) {
                    log.warn("Skip visit {} because adding med-robot selected service {} failed and addMissingRobotServiceFailureMode=SKIP_VISIT: {}",
                            visitSummary.getId(),
                            selectedDoctorService.getServiceId(),
                            exception.getMessage());
                    return false;
                }
                if (failureMode == MissingRobotServiceAddFailureMode.PROPAGATE_ERROR) {
                    throw exception;
                }
                log.warn("Continue visit {} with assign/transfer after failed add-service for med-robot selected service {} because addMissingRobotServiceFailureMode=CONTINUE_WITH_ASSIGN: {}",
                        visitSummary.getId(),
                        selectedDoctorService.getServiceId(),
                        exception.getMessage());
            }
        }

        boolean assignRequired = visitDetails == null
                || visitDetails.getCurrentServiceId() == null
                || visitDetails.getCurrentServiceId().intValue() != selectedDoctorService.getServiceId();

        if (assignRequired) {
            visitWorkflowGateway.assignServiceToVisit(
                    doctorContext.getBranchId(),
                    visitSummary.getId(),
                    selectedDoctorService.getServiceId(),
                    doctorContext.getStaffId(),
                    doctorContext.getServicePointId());
        } else {
            log.info("Skip redundant assign for visit={} because currentService={} already matches selectedService={} and workflow can continue with transfer-only",
                    visitSummary.getId(),
                    visitDetails.getCurrentServiceId(),
                    selectedDoctorService.getServiceId());
        }

        log.info("Assign workflow transfer step visit={} sourceQueue={} targetQueue={} doctor={} servicePoint={} trigger={} transferOnly={}",
                visitSummary.getId(),
                unknownDoctorQueueId,
                selectedDoctorService.getTargetQueueId(),
                doctorContext.getStaffId(),
                doctorContext.getServicePointId(),
                doctorContext.getTriggerSource(),
                !assignRequired);

        visitWorkflowGateway.transferVisitToQueue(
                doctorContext.getBranchId(),
                visitSummary.getId(),
                unknownDoctorQueueId,
                selectedDoctorService.getTargetQueueId());

        // Post-check полезен для интеграционных сценариев, где Orchestra может принять запрос,
        // но фактический переход визита не произойдет из-за внутренних ограничений платформы.
        Optional<VisitSummary> actualVisitAfterTransfer = visitWorkflowGateway.findVisit(doctorContext.getBranchId(), visitSummary.getId());
        if (actualVisitAfterTransfer.isPresent()
                && actualVisitAfterTransfer.get().getQueueId() != null
                && actualVisitAfterTransfer.get().getQueueId().intValue() != selectedDoctorService.getTargetQueueId()) {
            log.warn("Visit {} was not moved to queue {}. Actual queue after transfer is {}",
                    visitSummary.getId(),
                    selectedDoctorService.getTargetQueueId(),
                    actualVisitAfterTransfer.get().getQueueId());
            return false;
        }

        return true;
    }

    private boolean shouldAddMissingRobotServiceToVisit(VisitDetails visitDetails,
                                                        SelectedDoctorService selectedDoctorService) {
        if (!assignmentProperties.isAddMissingRobotServiceToVisit()) {
            return false;
        }
        if (visitDetails == null || selectedDoctorService == null || !isMedRobotSelection(selectedDoctorService)) {
            return false;
        }
        if (visitDetails.getCurrentServiceId() != null
                && visitDetails.getCurrentServiceId().intValue() == selectedDoctorService.getServiceId()) {
            return false;
        }
        if (selectedDoctorService.getRouteOrder() != null) {
            return false;
        }
        return !containsUnservedService(visitDetails, selectedDoctorService.getServiceId());
    }

    private boolean isMedRobotSelection(SelectedDoctorService selectedDoctorService) {
        String reason = selectedDoctorService.getSelectionReason();
        return reason != null && reason.startsWith("med-robot-");
    }

    private boolean containsUnservedService(VisitDetails visitDetails, int serviceId) {
        if (visitDetails == null || visitDetails.getUnservedServices() == null) {
            return false;
        }
        for (VisitUnservedService unservedService : visitDetails.getUnservedServices()) {
            if (unservedService != null
                    && unservedService.getServiceId() != null
                    && unservedService.getServiceId().intValue() == serviceId) {
                return true;
            }
        }
        return false;
    }

    private void rememberAddedService(VisitDetails visitDetails, int serviceId) {
        if (visitDetails == null || containsUnservedService(visitDetails, serviceId)) {
            return;
        }
        List<VisitUnservedService> existing = visitDetails.getUnservedServices();
        List<VisitUnservedService> updated = existing != null
                ? new ArrayList<VisitUnservedService>(existing)
                : new ArrayList<VisitUnservedService>();
        updated.add(new VisitUnservedService(Integer.valueOf(serviceId), null, null));
        visitDetails.setUnservedServices(updated);
    }
}
