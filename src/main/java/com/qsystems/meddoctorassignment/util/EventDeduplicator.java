package com.qsystems.meddoctorassignment.util;

import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Дедупликатор входящих событий Orchestra по окну времени.
 */
@Singleton
public class EventDeduplicator {

    private final Map<String, Instant> seenEvents = new ConcurrentHashMap<String, Instant>();

    /**
     * Проверяет, было ли событие уже обработано в заданном окне времени.
     *
     * <p>Ключ строится из набора полей, которые обычно позволяют отличить реальный новый event
     * от повтора доставки одного и того же сообщения.</p>
     */
    public boolean isDuplicate(OrchestraEvent event, Duration ttl) {
        cleanup(ttl);
        String key = buildKey(event);
        Instant now = Instant.now();
        Instant previous = seenEvents.putIfAbsent(key, now);
        return previous != null && previous.plus(ttl).isAfter(now);
    }

    private void cleanup(Duration ttl) {
        Instant now = Instant.now();
        for (Map.Entry<String, Instant> entry : seenEvents.entrySet()) {
            if (entry.getValue().plus(ttl).isBefore(now)) {
                seenEvents.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private String buildKey(OrchestraEvent event) {
        Object staffTransactionId = event.getParameters().get("staffTransactionId");
        Object servicePointTransactionId = event.getParameters().get("servicePointTransactionId");
        return String.valueOf(event.getEventName()) + "|"
                + String.valueOf(event.getUnitId()) + "|"
                + String.valueOf(event.getEventTime()) + "|"
                + String.valueOf(staffTransactionId) + "|"
                + String.valueOf(servicePointTransactionId);
    }
}
