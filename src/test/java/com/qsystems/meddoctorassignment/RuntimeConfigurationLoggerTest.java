package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.VisitProcessingSortOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RuntimeConfigurationLoggerTest {

  @Test
  void assignmentPropertiesDefaultsKeepStrictContextChecksEnabled() {
    AssignmentProperties properties = new AssignmentProperties();

    Assertions.assertFalse(properties.isSetWorkProfileTriggerEnabled());
    Assertions.assertTrue(properties.isWorkProfileExpandedTriggerEnabled());
    Assertions.assertTrue(properties.isUserServicePointSessionStartTriggerEnabled());
    Assertions.assertEquals(2000L, properties.getUserSessionSettleWindowMs());
    Assertions.assertFalse(properties.isServicePointOpenTriggerEnabled());
    Assertions.assertTrue(properties.isAbortCycleOnForbiddenMutation());
    Assertions.assertEquals(3, properties.getMaxVisitsPerCycle());
    Assertions.assertEquals(VisitProcessingSortOrder.OLDEST_FIRST, properties.getVisitProcessingSortOrder());
    Assertions.assertTrue(properties.isTreatInactiveUserStateAsFailure());
    Assertions.assertTrue(properties.isTreatNoStartedServicePointSessionAsFailure());
    Assertions.assertTrue(properties.isPollingEnabled());
    Assertions.assertFalse(properties.getActivation().isEnabled());
    Assertions.assertTrue(properties.getActivation().isFailCycleOnError());
  }

  @Test
  void orchestraPropertiesDefaultsIncludeReconnectDelay() {
    com.qsystems.meddoctorassignment.config.OrchestraProperties properties =
        new com.qsystems.meddoctorassignment.config.OrchestraProperties();

    Assertions.assertEquals(30000L, properties.getReconnectDelayMs());
  }
}
