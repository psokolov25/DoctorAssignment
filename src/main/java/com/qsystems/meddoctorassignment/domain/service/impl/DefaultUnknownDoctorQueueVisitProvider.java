package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.domain.service.UnknownDoctorQueueVisitProvider;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class DefaultUnknownDoctorQueueVisitProvider implements UnknownDoctorQueueVisitProvider {

    private final VisitWorkflowGateway visitWorkflowGateway;

    public DefaultUnknownDoctorQueueVisitProvider(VisitWorkflowGateway visitWorkflowGateway) {
        this.visitWorkflowGateway = visitWorkflowGateway;
    }

    @Override
    public List<VisitSummary> getWaitingVisits(DoctorContext doctorContext, BranchAssignmentCache branchCache) {
        if (branchCache.getUnknownDoctorQueueId() == null) {
            return Collections.emptyList();
        }
        return visitWorkflowGateway.getWaitingVisits(doctorContext.getBranchId(), branchCache.getUnknownDoctorQueueId().intValue());
    }
}
