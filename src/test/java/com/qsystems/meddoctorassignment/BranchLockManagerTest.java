package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.util.BranchLockManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BranchLockManagerTest {

  @Test
  void acquiresAndReleasesBranchLock() throws Exception {
    BranchLockManager lockManager = new BranchLockManager();
    Assertions.assertTrue(lockManager.tryLock(7, 10));
    lockManager.unlock(7);
    Assertions.assertTrue(lockManager.tryLock(7, 10));
    lockManager.unlock(7);
  }
}
