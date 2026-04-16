package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.domain.service.VisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
}
