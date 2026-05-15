package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.MedRobotPlainTextIdentificatorMode;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
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
    Assertions.assertTrue(properties.isFallbackToLocalOnError());
    Assertions.assertTrue(properties.isFallbackToLocalOnEmptyResponse());
    Assertions.assertFalse(properties.isRequireDoctorAvailableService());
    Assertions.assertTrue(properties.isRequireKnownQueue());
    Assertions.assertEquals(MedRobotPlainTextIdentificatorMode.TICKET_NUMBER, properties.getPlainTextIdentificatorMode());
  }
}
