package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.service.VisitRouteAnalyzer;
import jakarta.inject.Singleton;

/**
 * Минимальная реализация анализатора маршрута визита, делегирующая чтение детального состояния gateway-слою.
 */
@Singleton
public class DefaultVisitRouteAnalyzer implements VisitRouteAnalyzer {

    private final VisitWorkflowGateway visitWorkflowGateway;

    public DefaultVisitRouteAnalyzer(VisitWorkflowGateway visitWorkflowGateway) {
        this.visitWorkflowGateway = visitWorkflowGateway;
    }

    @Override
    public VisitDetails analyze(int branchId, long visitId) {
        return visitWorkflowGateway.getVisitDetails(branchId, visitId);
    }
}
