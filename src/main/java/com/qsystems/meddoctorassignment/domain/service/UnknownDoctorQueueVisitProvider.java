package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import java.util.List;

/**
 * Получает список визитов из очереди "врач не назначен".
 */
public interface UnknownDoctorQueueVisitProvider {

    /**
     * Возвращает визиты из очереди "врач не назначен" для указанного отделения.
     */
    List<VisitSummary> getWaitingVisits(DoctorContext doctorContext, BranchAssignmentCache branchCache);
}
