package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.OrchestraMetadataGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.ServicePointContextGateway;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * Fallback-реализация поиска контекста точки обслуживания через справочники Orchestra.
 *
 * <p>Используется только тогда, когда event payload недостаточен или локальный runtime cache
 * еще не успел обновиться.</p>
 */
@Singleton
public class ServicePointContextGatewayImpl implements ServicePointContextGateway {

    private final OrchestraMetadataGateway metadataGateway;

    public ServicePointContextGatewayImpl(OrchestraMetadataGateway metadataGateway) {
        this.metadataGateway = metadataGateway;
    }

    @Override
    public Optional<ServicePointRuntimeState> getServicePointContext(int branchId, long servicePointId) {
        List<ServicePointData> servicePoints = metadataGateway.getServicePointsFromBranch(branchId);
        for (ServicePointData data : servicePoints) {
            if (data.getId() == servicePointId) {
                return Optional.of(toRuntimeState(data));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ServicePointRuntimeState> findByStaff(int branchId, int staffId) {
        List<ServicePointData> servicePoints = metadataGateway.getServicePointsFromBranch(branchId);
        for (ServicePointData data : servicePoints) {
            if (data.getStaffId() == staffId) {
                return Optional.of(toRuntimeState(data));
            }
        }
        return Optional.empty();
    }

    private ServicePointRuntimeState toRuntimeState(ServicePointData data) {
        return new ServicePointRuntimeState(data.getId(), data.getBranchId(), data.getStaffId(), data.getWorkProfileId(), data.getStatus());
    }
}
