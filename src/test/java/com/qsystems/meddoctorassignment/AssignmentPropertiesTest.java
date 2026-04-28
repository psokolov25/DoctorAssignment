package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AssignmentPropertiesTest {

    @Test
    void resolvesBranchSpecificSourceEntryPointIdFirst() {
        AssignmentProperties properties = new AssignmentProperties();
        properties.setDefaultSourceEntryPointId(11);

        Map<Integer, Integer> mapping = new HashMap<Integer, Integer>();
        mapping.put(Integer.valueOf(6), Integer.valueOf(21));
        properties.setSourceEntryPointIdByBranch(mapping);

        Assertions.assertEquals(Integer.valueOf(21), properties.resolveSourceEntryPointId(6));
    }

    @Test
    void fallsBackToDefaultSourceEntryPointId() {
        AssignmentProperties properties = new AssignmentProperties();
        properties.setDefaultSourceEntryPointId(11);

        Assertions.assertEquals(Integer.valueOf(11), properties.resolveSourceEntryPointId(99));
    }

    @Test
    void returnsNullWhenNoSourceEntryPointIdConfigured() {
        AssignmentProperties properties = new AssignmentProperties();

        Assertions.assertNull(properties.resolveSourceEntryPointId(6));
    }

    @Test
    void triggerFlagsDefaultToSafeValues() {
        AssignmentProperties properties = new AssignmentProperties();

        Assertions.assertFalse(properties.isServicePointOpenTriggerEnabled());
        Assertions.assertFalse(properties.isSetWorkProfileTriggerEnabled());
        Assertions.assertTrue(properties.isUserServicePointSessionStartTriggerEnabled());
        Assertions.assertTrue(properties.isPollingEnabled());
        Assertions.assertEquals(2000L, properties.getUserSessionSettleWindowMs());
        Assertions.assertTrue(properties.isAbortCycleOnForbiddenMutation());
        Assertions.assertTrue(properties.isTreatInactiveUserStateAsFailure());
        Assertions.assertTrue(properties.isTreatNoStartedServicePointSessionAsFailure());
        Assertions.assertFalse(properties.getActivation().isEnabled());
        Assertions.assertTrue(properties.getActivation().isFailCycleOnError());
        Assertions.assertEquals("POST", properties.getActivation().getMethod());
        Assertions.assertFalse(properties.isTriggerEnabled(TriggerSource.SERVICE_POINT_OPEN));
        Assertions.assertFalse(properties.isTriggerEnabled(TriggerSource.SET_WORK_PROFILE));
        Assertions.assertTrue(properties.isTriggerEnabled(TriggerSource.USER_SERVICE_POINT_SESSION_START));
        Assertions.assertTrue(properties.isTriggerEnabled(TriggerSource.USER_SESSION_READY));
        Assertions.assertTrue(properties.isTriggerEnabled(TriggerSource.POLLING));
    }
}
