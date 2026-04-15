package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.domain.model.VisitDetails;

/**
 * Возвращает маршрут визита в форме, пригодной для алгоритма сопоставления услуг.
 */
public interface VisitRouteAnalyzer {

    /**
     * Возвращает детальное состояние маршрута визита.
     */
    VisitDetails analyze(int branchId, long visitId);
}
