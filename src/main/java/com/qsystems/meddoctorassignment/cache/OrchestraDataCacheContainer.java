package com.qsystems.meddoctorassignment.cache;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class OrchestraDataCacheContainer {

    private final Map<Integer, BranchAssignmentCache> branchCacheMap = new ConcurrentHashMap<Integer, BranchAssignmentCache>();

    public Map<Integer, BranchAssignmentCache> getBranchCacheMap() {
        return branchCacheMap;
    }

    public BranchAssignmentCache getOrCreateBranchCache(int branchId) {
        BranchAssignmentCache existing = branchCacheMap.get(branchId);
        if (existing != null) {
            return existing;
        }
        BranchAssignmentCache created = new BranchAssignmentCache(branchId);
        BranchAssignmentCache previous = branchCacheMap.putIfAbsent(branchId, created);
        return previous != null ? previous : created;
    }

    public void replaceBranchCache(int branchId, BranchAssignmentCache cache) {
        branchCacheMap.put(branchId, cache);
    }
}
