package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;

import java.util.Optional;

public interface ServicePointContextGateway {

    Optional<ServicePointRuntimeState> getServicePointContext(int branchId, long servicePointId);

    Optional<ServicePointRuntimeState> findByStaff(int branchId, int staffId);
}
