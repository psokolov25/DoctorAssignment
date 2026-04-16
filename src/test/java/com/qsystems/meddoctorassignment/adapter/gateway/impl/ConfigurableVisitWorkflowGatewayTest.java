package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigurableVisitWorkflowGatewayTest {

    @Test
    void extractsInactiveUserStateFromJsonResponse() {
        String body = "{\"visitId\":172154,\"userState\":\"INACTIVE\"}";

        Assertions.assertEquals("INACTIVE", ConfigurableVisitWorkflowGateway.extractUserState(body));
    }

    @Test
    void extractsNoStartedServicePointSessionStateFromJsonResponse() {
        String body = "{\n  \"userState\" : \"NO_STARTED_SERVICE_POINT_SESSION\",\n  \"servicePointId\" : null\n}";

        Assertions.assertEquals("NO_STARTED_SERVICE_POINT_SESSION", ConfigurableVisitWorkflowGateway.extractUserState(body));
    }

    @Test
    void returnsNullWhenUserStateIsAbsent() {
        String body = "{\"visitId\":172154,\"status\":\"WAITING\"}";

        Assertions.assertNull(ConfigurableVisitWorkflowGateway.extractUserState(body));
    }

    @Test
    void extractsCurrentVisitServiceIdFromJsonResponse() {
        String body = "{\"visitId\":172162,\"userState\":\"INACTIVE\",\"currentVisitService\":{\"serviceId\":4}}";

        Assertions.assertEquals(Integer.valueOf(4), ConfigurableVisitWorkflowGateway.extractCurrentVisitServiceId(body));
    }

    @Test
    void treatsAssignAsEffectivelyAppliedWhenCurrentVisitServiceMatchesRequestedService() {
        Assertions.assertTrue(ConfigurableVisitWorkflowGateway.isAssignEffectivelyApplied(Integer.valueOf(4), 4));
        Assertions.assertFalse(ConfigurableVisitWorkflowGateway.isAssignEffectivelyApplied(Integer.valueOf(72), 4));
        Assertions.assertFalse(ConfigurableVisitWorkflowGateway.isAssignEffectivelyApplied(null, 4));
    }

}
