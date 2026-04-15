package com.qsystems.meddoctorassignment.cache.service;

import com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter;
import com.qsystems.meddoctorassignment.branchgetter.BranchGettingStrategy;
import com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter;
import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;

@Singleton
public class OrchestraDataCacheUpdateService implements ApplicationEventListener<StartupEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrchestraDataCacheUpdateService.class);

    private final BranchCacheUpdater branchCacheUpdater;
    private final OrchestraDataCacheContainer cacheContainer;
    private final OrchestraProperties orchestraProperties;
    private final AssignmentProperties assignmentProperties;
    private final AllBranchesGetter allBranchesGetter;
    private final DefinedBranchesGetter definedBranchesGetter;

    public OrchestraDataCacheUpdateService(BranchCacheUpdater branchCacheUpdater,
                                           OrchestraDataCacheContainer cacheContainer,
                                           OrchestraProperties orchestraProperties,
                                           AssignmentProperties assignmentProperties,
                                           AllBranchesGetter allBranchesGetter,
                                           DefinedBranchesGetter definedBranchesGetter) {
        this.branchCacheUpdater = branchCacheUpdater;
        this.cacheContainer = cacheContainer;
        this.orchestraProperties = orchestraProperties;
        this.assignmentProperties = assignmentProperties;
        this.allBranchesGetter = allBranchesGetter;
        this.definedBranchesGetter = definedBranchesGetter;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        refreshConfiguredBranches();
    }

    public void refreshConfiguredBranches() {
        Collection<Integer> branchIds = selectStrategy().getBranchIds();
        log.info("Refresh caches for configured branches {}", branchIds);
        for (Integer branchId : branchIds) {
            branchCacheUpdater.updateBranchCache(branchId);
        }
    }

    public void ensureFresh(int branchId) {
        BranchAssignmentCache cache = cacheContainer.getOrCreateBranchCache(branchId);
        Duration ttl = Duration.ofSeconds(assignmentProperties.getStaleCacheDurationSeconds());
        if (cache.isStale(ttl)) {
            branchCacheUpdater.updateBranchCache(branchId);
        }
    }

    private BranchGettingStrategy selectStrategy() {
        String raw = orchestraProperties.getBranchesForCache();
        return allBranchesGetter.supports(raw) ? allBranchesGetter : definedBranchesGetter;
    }
}
