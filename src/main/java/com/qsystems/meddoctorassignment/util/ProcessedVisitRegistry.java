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
        return alreadyProcessed(branchId, visitId, staffId, null, ttl);
    }

    /**
     * Возвращает {@code true}, если этот же врач уже обрабатывал этот же маршрутный шаг визита
     * внутри заданного TTL.
     */
    public boolean alreadyProcessed(int branchId, long visitId, int staffId, String routeFingerprint, Duration ttl) {
        cleanup(ttl);
        String key = key(branchId, visitId, staffId, routeFingerprint);
        Instant instant = processed.get(key);
        return instant != null && instant.plus(ttl).isAfter(Instant.now());
    }

    /**
     * Помечает визит как успешно обработанный конкретным врачом.
     */
    public void markProcessed(int branchId, long visitId, int staffId) {
        markProcessed(branchId, visitId, staffId, null);
    }

    /**
     * Помечает успешно обработанным конкретный маршрутный шаг визита.
     */
    public void markProcessed(int branchId, long visitId, int staffId, String routeFingerprint) {
        processed.put(key(branchId, visitId, staffId, routeFingerprint), Instant.now());
    }

    private String key(int branchId, long visitId, int staffId, String routeFingerprint) {
        String fingerprint = routeFingerprint == null || routeFingerprint.trim().isEmpty()
                ? "default"
                : routeFingerprint;
        return branchId + "|" + visitId + "|" + staffId + "|" + fingerprint;
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
