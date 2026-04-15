package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultLoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import com.qsystems.meddoctorassignment.support.InMemoryTestGateways;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ServicePointOpenContextResolverTest {

    @Test
    void resolvesContextDirectlyFromServicePointOpenPayload() {
        OrchestraDataCacheContainer cacheContainer = new OrchestraDataCacheContainer();
        DefaultLoggedDoctorContextResolver resolver = new DefaultLoggedDoctorContextResolver(cacheContainer, new InMemoryTestGateways());

        OrchestraEvent event = new OrchestraEvent();
        event.setEventName("SERVICE_POINT_OPEN");
        event.setUnitId(10220000000007L);
        event.getParameters().put("branchId", 7);
        event.getParameters().put("userId", 1000002);
        event.getParameters().put("workProfileOrigId", 31);
        event.getParameters().put("workProfileName", "Маммография");
        event.getParameters().put("servicePointName", "102 Маммография");
        event.getParameters().put("userName", "p.sokolov");

        DoctorContext context = resolver.resolve(event, TriggerSource.SERVICE_POINT_OPEN);

        Assertions.assertEquals(7, context.getBranchId());
        Assertions.assertEquals(10220000000007L, context.getServicePointId());
        Assertions.assertEquals(1000002, context.getStaffId());
        Assertions.assertEquals(31, context.getWorkProfileId());
        Assertions.assertEquals("SERVICE_POINT_OPEN", context.getTriggerSource().name());
    }

    @Test
    void resolvesMissingFieldsFromFallbackRuntimeState() {
        OrchestraDataCacheContainer cacheContainer = new OrchestraDataCacheContainer();
        cacheContainer.getOrCreateBranchCache(10)
                .getServicePointRuntimeStateMap()
                .put(820000000010L, new ServicePointRuntimeState(820000000010L, 10, 29, 28, "OPEN"));

        InMemoryTestGateways gateways = new InMemoryTestGateways();
        DefaultLoggedDoctorContextResolver resolver = new DefaultLoggedDoctorContextResolver(cacheContainer, gateways);

        OrchestraEvent event = new OrchestraEvent();
        event.setEventName("SET_WORK_PROFILE");
        event.getParameters().put("branchId", 10);
        event.getParameters().put("userId", 29);

        DoctorContext context = resolver.resolve(event, TriggerSource.SET_WORK_PROFILE);

        Assertions.assertEquals(820000000010L, context.getServicePointId());
        Assertions.assertEquals(28, context.getWorkProfileId());
        Assertions.assertEquals("fallback.servicePointRuntimeState", context.getFieldSources().get("servicePointId"));
    }
}
