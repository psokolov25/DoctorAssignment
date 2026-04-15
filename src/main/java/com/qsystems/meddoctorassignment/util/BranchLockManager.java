package com.qsystems.meddoctorassignment.util;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class BranchLockManager {

    private final Map<Integer, ReentrantLock> branchLocks = new ConcurrentHashMap<Integer, ReentrantLock>();

    public boolean tryLock(int branchId, long timeoutMs) throws InterruptedException {
        ReentrantLock lock = branchLocks.computeIfAbsent(branchId, key -> new ReentrantLock());
        return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void unlock(int branchId) {
        ReentrantLock lock = branchLocks.get(branchId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
