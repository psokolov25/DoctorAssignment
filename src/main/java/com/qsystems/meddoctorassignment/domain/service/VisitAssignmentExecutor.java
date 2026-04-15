package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;

public interface VisitAssignmentExecutor {

    boolean assign(DoctorContext doctorContext, VisitSummary visitSummary, SelectedDoctorService selectedDoctorService, int unknownDoctorQueueId);
}
