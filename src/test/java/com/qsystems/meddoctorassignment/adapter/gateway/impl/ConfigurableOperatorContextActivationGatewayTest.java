package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import io.micronaut.http.HttpMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class ConfigurableOperatorContextActivationGatewayTest {

    @Test
    void expandsActivationTemplatePlaceholders() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("branchId", Integer.valueOf(6));
        variables.put("servicePointId", Long.valueOf(120000000006L));
        variables.put("staffId", Integer.valueOf(1));
        variables.put("workProfileId", Integer.valueOf(14));

        String expanded = ConfigurableOperatorContextActivationGateway.expand(
                "{\"branchId\":{branchId},\"servicePointId\":{servicePointId},\"staffId\":{staffId},\"workProfileId\":{workProfileId}}",
                variables);

        Assertions.assertEquals("{\"branchId\":6,\"servicePointId\":120000000006,\"staffId\":1,\"workProfileId\":14}", expanded);
    }

    @Test
    void resolvesActivationMethodWithSafeFallbackToPost() {
        Assertions.assertEquals(HttpMethod.POST, ConfigurableOperatorContextActivationGateway.resolveMethod(null));
        Assertions.assertEquals(HttpMethod.POST, ConfigurableOperatorContextActivationGateway.resolveMethod("UNKNOWN"));
        Assertions.assertEquals(HttpMethod.GET, ConfigurableOperatorContextActivationGateway.resolveMethod("GET"));
        Assertions.assertEquals(HttpMethod.PUT, ConfigurableOperatorContextActivationGateway.resolveMethod("PUT"));
        Assertions.assertEquals(HttpMethod.PATCH, ConfigurableOperatorContextActivationGateway.resolveMethod("PATCH"));
        Assertions.assertEquals(HttpMethod.DELETE, ConfigurableOperatorContextActivationGateway.resolveMethod("DELETE"));
    }
}
