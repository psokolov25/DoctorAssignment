package com.qsystems.meddoctorassignment.util;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Менеджер branch-level блокировок.
 *
 * <p>Не допускает конкурентную обработку одного и того же отделения несколькими потоками,
 * что особенно важно при одновременном срабатывании websocket и polling fallback.</p>
 */
@Singleton
public class BranchLockManager {

    private final Map<Integer, ReentrantLock> branchLocks = new ConcurrentHashMap<Integer, ReentrantLock>();

    /**
     * Пытается захватить блокировку на конкретный branch за ограниченное время.
     */
    public boolean tryLock(int branchId, long timeoutMs) throws InterruptedException {
        ReentrantLock lock = branchLocks.computeIfAbsent(branchId, key -> new ReentrantLock());
        return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Освобождает блокировку branch, только если текущий поток действительно ей владеет.
     */
    public void unlock(int branchId) {
        ReentrantLock lock = branchLocks.get(branchId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
