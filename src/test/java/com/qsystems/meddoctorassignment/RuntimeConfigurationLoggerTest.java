package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
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
    Assertions.assertTrue(properties.isTreatInactiveUserStateAsFailure());
    Assertions.assertTrue(properties.isTreatNoStartedServicePointSessionAsFailure());
    Assertions.assertFalse(properties.getActivation().isEnabled());
    Assertions.assertTrue(properties.getActivation().isFailCycleOnError());
  }
}
