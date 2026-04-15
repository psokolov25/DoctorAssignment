package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.ServicePointContextGateway;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.domain.service.LoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import jakarta.inject.Singleton;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

@Singleton
public class DefaultLoggedDoctorContextResolver implements LoggedDoctorContextResolver {

    private final OrchestraDataCacheContainer cacheContainer;
    private final ServicePointContextGateway servicePointContextGateway;

    public DefaultLoggedDoctorContextResolver(OrchestraDataCacheContainer cacheContainer,
                                              ServicePointContextGateway servicePointContextGateway) {
        this.cacheContainer = cacheContainer;
        this.servicePointContextGateway = servicePointContextGateway;
    }

    @Override
    public DoctorContext resolve(OrchestraEvent event, TriggerSource triggerSource) {
        DoctorContext context = new DoctorContext();
        context.setTriggerSource(triggerSource);

        Map<String, Serializable> parameters = event.getParameters();

        Integer branchId = asInteger(parameters.get("branchId"));
        if (branchId != null) {
            context.setBranchId(branchId.intValue());
            context.recordSource("branchId", "event.parameters.branchId");
        }

        Long servicePointId = asLong(parameters.get("servicePointId"));
        if (servicePointId == null && event.getUnitId() != null) {
            servicePointId = event.getUnitId();
            context.recordSource("servicePointId", "event.unitId");
        } else if (servicePointId != null) {
            context.recordSource("servicePointId", "event.parameters.servicePointId");
        }
        if (servicePointId != null) {
            context.setServicePointId(servicePointId.longValue());
        }

        Integer staffId = asInteger(parameters.get("staffId"));
        if (staffId == null) {
            staffId = asInteger(parameters.get("userId"));
            if (staffId != null) {
                context.recordSource("staffId", "event.parameters.userId");
            }
        } else {
            context.recordSource("staffId", "event.parameters.staffId");
        }
        if (staffId != null) {
            context.setStaffId(staffId.intValue());
        }

        Integer workProfileId = asInteger(parameters.get("workProfileOrigId"));
        if (workProfileId == null) {
            workProfileId = asInteger(parameters.get("workProfile"));
            if (workProfileId != null) {
                context.recordSource("workProfileId", "event.parameters.workProfile");
            }
        } else {
            context.recordSource("workProfileId", "event.parameters.workProfileOrigId");
        }
        if (workProfileId != null) {
            context.setWorkProfileId(workProfileId.intValue());
        }

        String workProfileName = asString(parameters.get("workProfileName"));
        if (workProfileName != null) {
            context.setWorkProfileName(workProfileName);
            context.recordSource("workProfileName", "event.parameters.workProfileName");
        }

        String servicePointName = asString(parameters.get("servicePointName"));
        if (servicePointName != null) {
            context.setServicePointName(servicePointName);
            context.recordSource("servicePointName", "event.parameters.servicePointName");
        }

        String userName = asString(parameters.get("userName"));
        if (userName == null) {
            userName = asString(parameters.get("user"));
        }
        if (userName != null) {
            context.setUserName(userName);
            context.recordSource("userName", "event.parameters.userName/user");
        }

        enrichMissingFields(context);
        return context;
    }

    private void enrichMissingFields(DoctorContext context) {
        if (context.getBranchId() <= 0) {
            return;
        }

        BranchAssignmentCache branchCache = cacheContainer.getOrCreateBranchCache(context.getBranchId());
        ServicePointRuntimeState runtimeState = null;

        if (context.getServicePointId() > 0) {
            runtimeState = branchCache.getServicePointRuntimeStateMap().get(context.getServicePointId());
            if (runtimeState == null) {
                Optional<ServicePointRuntimeState> lookup = servicePointContextGateway.getServicePointContext(context.getBranchId(), context.getServicePointId());
                if (lookup.isPresent()) {
                    runtimeState = lookup.get();
                }
            }
        }

        if (runtimeState == null && context.getStaffId() > 0) {
            runtimeState = findByStaff(branchCache, context.getStaffId());
            if (runtimeState == null) {
                Optional<ServicePointRuntimeState> lookup = servicePointContextGateway.findByStaff(context.getBranchId(), context.getStaffId());
                if (lookup.isPresent()) {
                    runtimeState = lookup.get();
                }
            }
        }

        if (runtimeState != null) {
            if (context.getServicePointId() <= 0) {
                context.setServicePointId(runtimeState.getServicePointId());
                context.recordSource("servicePointId", "fallback.servicePointRuntimeState");
            }
            if (context.getStaffId() <= 0) {
                context.setStaffId(runtimeState.getStaffId());
                context.recordSource("staffId", "fallback.servicePointRuntimeState");
            }
            if (context.getWorkProfileId() <= 0) {
                context.setWorkProfileId(runtimeState.getWorkProfileId());
                context.recordSource("workProfileId", "fallback.servicePointRuntimeState");
            }
        }

        if (context.getBranchId() <= 0 || context.getServicePointId() <= 0 || context.getStaffId() <= 0 || context.getWorkProfileId() <= 0) {
            throw new IllegalStateException("Cannot resolve doctor context completely. Resolved fields: " + context.describeSources());
        }
    }

    private ServicePointRuntimeState findByStaff(BranchAssignmentCache branchCache, int staffId) {
        for (ServicePointRuntimeState state : branchCache.getServicePointRuntimeStateMap().values()) {
            if (state.getStaffId() == staffId) {
                return state;
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
