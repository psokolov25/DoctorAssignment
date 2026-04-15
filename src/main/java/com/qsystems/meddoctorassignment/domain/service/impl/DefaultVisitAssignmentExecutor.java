package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.domain.service.VisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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

    @Override
    public boolean assign(DoctorContext doctorContext,
                          VisitSummary visitSummary,
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

        if (assignmentProperties.isRecheckVisitBeforeTransfer()) {
            Optional<VisitSummary> actualVisit = visitWorkflowGateway.findVisit(doctorContext.getBranchId(), visitSummary.getId());
            if (actualVisit.isPresent() && actualVisit.get().getQueueId() != null && actualVisit.get().getQueueId().intValue() != unknownDoctorQueueId) {
                log.warn("Skip visit {} because queue already changed from unknown-doctor queue {}", visitSummary.getId(), unknownDoctorQueueId);
                return false;
            }
        }

        visitWorkflowGateway.assignServiceToVisit(
                doctorContext.getBranchId(),
                visitSummary.getId(),
                selectedDoctorService.getServiceId(),
                doctorContext.getStaffId(),
                doctorContext.getServicePointId());

        visitWorkflowGateway.transferVisitToQueue(
                doctorContext.getBranchId(),
                visitSummary.getId(),
                unknownDoctorQueueId,
                selectedDoctorService.getTargetQueueId());

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
