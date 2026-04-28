package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter;
import com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter;
import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OrchestraDataCacheUpdateServiceTest {

  @Test
  void startupRefreshDoesNotPropagateExceptionWhenBranchListResolutionFails() {
    OrchestraDataCacheUpdateService service =
        new OrchestraDataCacheUpdateService(
            new BranchCacheUpdater(null, new OrchestraDataCacheContainer(), new AssignmentProperties()) {
              @Override
              public void updateBranchCache(int branchId) {
                throw new UnsupportedOperationException("not expected");
              }
            },
            new OrchestraDataCacheContainer(),
            orchestraProperties(),
            new AssignmentProperties(),
            new AllBranchesGetter(null) {
              @Override
              public boolean supports(String branchesForCache) {
                return true;
              }

              @Override
              public Collection<Integer> getBranchIds() {
                throw new IllegalStateException("orchestra down");
              }
            },
            new DefinedBranchesGetter(orchestraProperties()));

    Assertions.assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        service.refreshConfiguredBranches();
      }
    });
  }

  @Test
  void startupRefreshContinuesWhenOneBranchFails() {
    final java.util.List<Integer> updated = new java.util.ArrayList<Integer>();
    OrchestraDataCacheUpdateService service =
        new OrchestraDataCacheUpdateService(
            new BranchCacheUpdater(null, new OrchestraDataCacheContainer(), new AssignmentProperties()) {
              @Override
              public void updateBranchCache(int branchId) {
                updated.add(Integer.valueOf(branchId));
                if (branchId == 2) {
                  throw new IllegalStateException("temporary outage");
                }
              }
            },
            new OrchestraDataCacheContainer(),
            orchestraProperties(),
            new AssignmentProperties(),
            new AllBranchesGetter(null) {
              @Override
              public boolean supports(String branchesForCache) {
                return true;
              }

              @Override
              public Collection<Integer> getBranchIds() {
                return Arrays.asList(Integer.valueOf(1), Integer.valueOf(2));
              }
            },
            new DefinedBranchesGetter(orchestraProperties()));

    Assertions.assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        service.refreshConfiguredBranches();
      }
    });
    Assertions.assertEquals(Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)), updated);
  }

  private OrchestraProperties orchestraProperties() {
    OrchestraProperties properties = new OrchestraProperties();
    properties.setBranchesForCache("*");
    properties.setReconnectDelayMs(1000L);
    return properties;
  }
}
