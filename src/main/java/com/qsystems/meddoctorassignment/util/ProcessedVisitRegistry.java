package com.qsystems.meddoctorassignment.util;

import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Краткоживущий реестр недавно обработанных визитов.
 */
@Singleton
public class ProcessedVisitRegistry {

    private final Map<String, Instant> processed = new ConcurrentHashMap<String, Instant>();

    /**
     * Возвращает {@code true}, если этот же врач уже обрабатывал этот же визит внутри заданного TTL.
     */
    public boolean alreadyProcessed(int branchId, long visitId, int staffId, Duration ttl) {
        cleanup(ttl);
        String key = key(branchId, visitId, staffId);
        Instant instant = processed.get(key);
        return instant != null && instant.plus(ttl).isAfter(Instant.now());
    }

    /**
     * Помечает визит как успешно обработанный конкретным врачом.
     */
    public void markProcessed(int branchId, long visitId, int staffId) {
        processed.put(key(branchId, visitId, staffId), Instant.now());
    }

    private String key(int branchId, long visitId, int staffId) {
        return branchId + "|" + visitId + "|" + staffId;
    }

    private void cleanup(Duration ttl) {
        Instant now = Instant.now();
        for (Map.Entry<String, Instant> entry : processed.entrySet()) {
            if (entry.getValue().plus(ttl).isBefore(now)) {
                processed.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
