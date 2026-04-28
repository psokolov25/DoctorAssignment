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
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервис управления жизненным циклом branch cache.
 *
 * <p>Отвечает за инициализацию кэшей на старте и за ленивое обновление при обработке событий,
 * когда локальный снимок отделения считается устаревшим.</p>
 *
 * <p>Ключевое требование: потеря связи с Orchestra не должна аварийно останавливать процесс.
 * Поэтому ошибки bootstrap/refresh логируются, а повторная попытка планируется в фоне.</p>
 */
@Singleton
public class OrchestraDataCacheUpdateService implements ApplicationEventListener<StartupEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrchestraDataCacheUpdateService.class);

    private final BranchCacheUpdater branchCacheUpdater;
    private final OrchestraDataCacheContainer cacheContainer;
    private final OrchestraProperties orchestraProperties;
    private final AssignmentProperties assignmentProperties;
    private final AllBranchesGetter allBranchesGetter;
    private final DefinedBranchesGetter definedBranchesGetter;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "orchestra-rest-retry");
            thread.setDaemon(true);
            return thread;
        }
    });
    private final AtomicBoolean retryScheduled = new AtomicBoolean(false);

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

    /**
     * Перестраивает кэши для всех branch id, выбранных стратегией из конфигурации.
     *
     * <p>Ошибки не пробрасываются наружу, чтобы отсутствие Orchestra не валило весь сервис на
     * старте. Вместо этого ставится отложенная попытка переподключения.</p>
     */
    public void refreshConfiguredBranches() {
        Collection<Integer> branchIds;
        try {
            branchIds = selectStrategy().getBranchIds();
        } catch (RuntimeException exception) {
            log.error("Cannot resolve configured branches from Orchestra. Service will stay alive and retry later: {}",
                    exception.getMessage(), exception);
            scheduleRetry("branch list resolution failed");
            return;
        }

        log.info("Refresh caches for configured branches {}", branchIds);
        boolean hasFailures = false;
        for (Integer branchId : branchIds) {
            try {
                branchCacheUpdater.updateBranchCache(branchId.intValue());
            } catch (RuntimeException exception) {
                hasFailures = true;
                log.error("Branch cache refresh failed for branch {}. Service will continue and retry later: {}",
                        branchId,
                        exception.getMessage(),
                        exception);
            }
        }

        if (hasFailures) {
            scheduleRetry("one or more branch cache refresh attempts failed");
        } else {
            retryScheduled.set(false);
        }
    }

    /**
     * Обновляет branch cache только если он старше заданного TTL.
     *
     * <p>Если Orchestra временно недоступна, прежний кэш сохраняется, а процесс продолжает жить.
     * Следующая попытка refresh будет запланирована автоматически.</p>
     */
    public void ensureFresh(int branchId) {
        BranchAssignmentCache cache = cacheContainer.getOrCreateBranchCache(branchId);
        Duration ttl = Duration.ofSeconds(assignmentProperties.getStaleCacheDurationSeconds());
        if (cache.isStale(ttl)) {
            try {
                branchCacheUpdater.updateBranchCache(branchId);
                retryScheduled.set(false);
            } catch (RuntimeException exception) {
                log.warn("Could not refresh stale cache for branch {}. Existing cache will be kept and retry will be scheduled: {}",
                        Integer.valueOf(branchId),
                        exception.getMessage(),
                        exception);
                scheduleRetry("stale cache refresh failed for branch " + branchId);
            }
        }
    }

    private BranchGettingStrategy selectStrategy() {
        String raw = orchestraProperties.getBranchesForCache();
        return allBranchesGetter.supports(raw) ? allBranchesGetter : definedBranchesGetter;
    }

    private void scheduleRetry(String reason) {
        if (!retryScheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = Math.max(1000L, orchestraProperties.getReconnectDelayMs());
        log.warn("Schedule Orchestra REST reconnect/cache bootstrap retry in {} ms: {}",
                Long.valueOf(delay),
                reason);
        retryScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                retryScheduled.set(false);
                refreshConfiguredBranches();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
