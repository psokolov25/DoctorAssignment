package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.domain.model.VisitDetails;

public interface VisitRouteAnalyzer {

    VisitDetails analyze(int branchId, long visitId);
}
