package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.util.EventDeduplicator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class EventDeduplicatorTest {

    @Test
    void marksSecondIdenticalEventAsDuplicate() {
        EventDeduplicator deduplicator = new EventDeduplicator();
        OrchestraEvent event = new OrchestraEvent();
        event.setEventName("SERVICE_POINT_OPEN");
        event.setUnitId(10L);
        event.setEventTime("2026-04-15T10:00:00");
        event.getParameters().put("staffTransactionId", 1L);
        event.getParameters().put("servicePointTransactionId", 2L);

        Assertions.assertFalse(deduplicator.isDuplicate(event, Duration.ofSeconds(60)));
        Assertions.assertTrue(deduplicator.isDuplicate(event, Duration.ofSeconds(60)));
    }
}
