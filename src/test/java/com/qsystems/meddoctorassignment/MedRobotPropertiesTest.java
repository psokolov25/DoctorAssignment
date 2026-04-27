package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.MedRobotErrorHandlingMode;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.MedRobotRequestBodyMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Проверяет безопасные значения по умолчанию для интеграции с med-robot. */
public class MedRobotPropertiesTest {

  @Test
  void defaultsKeepLegacyModeAndSafeFallbacks() {
    MedRobotProperties properties = new MedRobotProperties();

    Assertions.assertFalse(properties.isEnabled());
    Assertions.assertEquals("http://localhost:8082", properties.getUrl());
    Assertions.assertEquals(
        "/prorobot/optimalqueue/{branchId}/service/{serviceId}",
        properties.getOptimalServicePath());
    Assertions.assertEquals(
        MedRobotRequestBodyMode.UNSERVED_SERVICE_IDS_JSON_ARRAY, properties.getRequestBodyMode());
    Assertions.assertEquals("default", properties.getPlainTextPolicy());
    Assertions.assertEquals(
        MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL, properties.getErrorHandlingMode());
    Assertions.assertTrue(properties.isFallbackToLocalOnError());
    Assertions.assertTrue(properties.isFallbackToLocalOnEmptyResponse());
    Assertions.assertFalse(properties.isRequireDoctorAvailableService());
    Assertions.assertTrue(properties.isRequireKnownQueue());
  }

  @Test
  void fallbackToLocalOnErrorBooleanAliasMapsToErrorHandlingMode() {
    MedRobotProperties properties = new MedRobotProperties();

    properties.setFallbackToLocalOnError(false);

    Assertions.assertEquals(MedRobotErrorHandlingMode.SKIP_VISIT, properties.getErrorHandlingMode());
    Assertions.assertFalse(properties.isFallbackToLocalOnError());

    properties.setFallbackToLocalOnError(true);

    Assertions.assertEquals(
        MedRobotErrorHandlingMode.FALLBACK_TO_LOCAL, properties.getErrorHandlingMode());
    Assertions.assertTrue(properties.isFallbackToLocalOnError());
  }
}
