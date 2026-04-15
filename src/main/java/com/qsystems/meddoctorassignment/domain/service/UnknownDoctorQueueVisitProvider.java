package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;

import java.util.List;

public interface UnknownDoctorQueueVisitProvider {

    List<VisitSummary> getWaitingVisits(DoctorContext doctorContext, BranchAssignmentCache branchCache);
}
