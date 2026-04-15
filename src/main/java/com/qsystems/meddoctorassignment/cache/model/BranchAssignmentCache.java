package com.qsystems.meddoctorassignment.cache.model;

import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BranchAssignmentCache {

    private final int branchId;
    private final Map<Integer, ServiceData> serviceMap = new ConcurrentHashMap<Integer, ServiceData>();
    private final Map<Integer, TinyQueue> queueMap = new ConcurrentHashMap<Integer, TinyQueue>();
    private final Map<Integer, Integer> serviceIdToQueueId = new ConcurrentHashMap<Integer, Integer>();
    private final Map<Integer, Set<Integer>> queueIdToServiceIds = new ConcurrentHashMap<Integer, Set<Integer>>();
    private final Map<Integer, Set<Integer>> workProfileToQueueIds = new ConcurrentHashMap<Integer, Set<Integer>>();
    private final Map<Long, ServicePointRuntimeState> servicePointRuntimeStateMap = new ConcurrentHashMap<Long, ServicePointRuntimeState>();
    private final Map<String, Integer> serviceExternalKeyToId = new ConcurrentHashMap<String, Integer>();
    private volatile Integer unknownDoctorQueueId;
    private volatile Instant lastUpdated = Instant.EPOCH;

    public BranchAssignmentCache(int branchId) {
        this.branchId = branchId;
    }

    public int getBranchId() {
        return branchId;
    }

    public Map<Integer, ServiceData> getServiceMap() {
        return serviceMap;
    }

    public Map<Integer, TinyQueue> getQueueMap() {
        return queueMap;
    }

    public Map<Integer, Integer> getServiceIdToQueueId() {
        return serviceIdToQueueId;
    }

    public Map<Integer, Set<Integer>> getQueueIdToServiceIds() {
        return queueIdToServiceIds;
    }

    public Map<Integer, Set<Integer>> getWorkProfileToQueueIds() {
        return workProfileToQueueIds;
    }

    public Map<Long, ServicePointRuntimeState> getServicePointRuntimeStateMap() {
        return servicePointRuntimeStateMap;
    }

    public Map<String, Integer> getServiceExternalKeyToId() {
        return serviceExternalKeyToId;
    }

    public Integer getUnknownDoctorQueueId() {
        return unknownDoctorQueueId;
    }

    public void setUnknownDoctorQueueId(Integer unknownDoctorQueueId) {
        this.unknownDoctorQueueId = unknownDoctorQueueId;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void markUpdated() {
        this.lastUpdated = Instant.now();
    }

    public boolean isStale(Duration ttl) {
        return lastUpdated.plus(ttl).isBefore(Instant.now());
    }

    public void clear() {
        serviceMap.clear();
        queueMap.clear();
        serviceIdToQueueId.clear();
        queueIdToServiceIds.clear();
        workProfileToQueueIds.clear();
        servicePointRuntimeStateMap.clear();
        serviceExternalKeyToId.clear();
        unknownDoctorQueueId = null;
        lastUpdated = Instant.EPOCH;
    }
}
