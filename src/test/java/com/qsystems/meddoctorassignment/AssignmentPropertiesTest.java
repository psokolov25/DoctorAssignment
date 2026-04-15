package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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
}
