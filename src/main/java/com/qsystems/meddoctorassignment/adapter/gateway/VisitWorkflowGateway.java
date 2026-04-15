package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;

import java.util.List;
import java.util.Optional;

public interface VisitWorkflowGateway {

    List<VisitSummary> getWaitingVisits(int branchId, int queueId);

    VisitDetails getVisitDetails(int branchId, long visitId);

    void assignServiceToVisit(int branchId, long visitId, int serviceId, int staffId, long servicePointId);

    void transferVisitToQueue(int branchId, long visitId, int sourceQueueId, int targetQueueId);

    Optional<VisitSummary> findVisit(int branchId, long visitId);
}
