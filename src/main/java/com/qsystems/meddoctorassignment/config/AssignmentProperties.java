package com.qsystems.meddoctorassignment.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Конфигурационные свойства алгоритма назначения врача.
 *
 * <p>Здесь собраны как бизнес-параметры алгоритма, так и технические настройки устойчивости:
 * ограничения на цикл обработки, дедупликация событий, recheck перед переводом и endpoint-ы,
 * используемые для работы с визитами.</p>
 */
@ConfigurationProperties("application.assignment")
public class AssignmentProperties {

    /**
     * Полностью включает или отключает доменный алгоритм.
     */
    private boolean enabled = true;

    /**
     * Queue id очереди "врач не назначен".
     */
    private Integer unknownDoctorQueueId;

    /**
     * Максимум визитов, обрабатываемых за один доменный цикл.
     */
    private int maxVisitsPerCycle = 50;

    /**
     * Расписание polling fallback.
     */
    private String pollingCron = "0 */5 * * * ?";

    /**
     * Максимальное время ожидания branch lock.
     */
    private long branchLockTimeoutMs = 5000L;

    /**
     * Режим без реальных изменений в Orchestra.
     */
    private boolean dryRun = true;

    /**
     * Нужно ли перечитывать визит перед переводом между очередями.
     */
    private boolean recheckVisitBeforeTransfer = true;

    /**
     * Белый список branch id. Пустой список означает "все отделения".
     */
    private List<Integer> allowedBranches = new ArrayList<Integer>();

    /**
     * TTL branch cache в секундах.
     */
    private int staleCacheDurationSeconds = 300;

    /**
     * Окно дедупликации входящих событий в секундах.
     */
    private int eventDeduplicationTtlSeconds = 120;

    /**
     * TTL защиты от повторной обработки одного и того же визита одним и тем же врачом.
     */
    private int processedVisitTtlSeconds = 900;

    /**
     * Конфигурируемые приоритеты услуг.
     *
     * <p>Ключ может быть serviceId, internalName или externalName.</p>
     */
    private Map<String, Integer> servicePriorityByKey = new HashMap<String, Integer>();

    /**
     * Резервный идентификатор source entry point, если для отделения не задано отдельное значение.
     *
     * <p>Нужен для тех инсталляций Orchestra, где endpoint перевода визита ожидает в поле
     * {@code fromId} не queue id, а идентификатор entry point.</p>
     */
    private Integer defaultSourceEntryPointId;

    /**
     * Переопределения source entry point по branch id.
     *
     * <p>Позволяет задавать собственный entry point для каждого отделения.</p>
     */
    private Map<Integer, Integer> sourceEntryPointIdByBranch = new HashMap<Integer, Integer>();

    /**
     * Конфигурируемые пути для visit workflow endpoint-ов.
     */
    private ExperimentalEndpoints experimentalEndpoints = new ExperimentalEndpoints();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getUnknownDoctorQueueId() {
        return unknownDoctorQueueId;
    }

    public void setUnknownDoctorQueueId(Integer unknownDoctorQueueId) {
        this.unknownDoctorQueueId = unknownDoctorQueueId;
    }

    public int getMaxVisitsPerCycle() {
        return maxVisitsPerCycle;
    }

    public void setMaxVisitsPerCycle(int maxVisitsPerCycle) {
        this.maxVisitsPerCycle = maxVisitsPerCycle;
    }

    public String getPollingCron() {
        return pollingCron;
    }

    public void setPollingCron(String pollingCron) {
        this.pollingCron = pollingCron;
    }

    public long getBranchLockTimeoutMs() {
        return branchLockTimeoutMs;
    }

    public void setBranchLockTimeoutMs(long branchLockTimeoutMs) {
        this.branchLockTimeoutMs = branchLockTimeoutMs;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isRecheckVisitBeforeTransfer() {
        return recheckVisitBeforeTransfer;
    }

    public void setRecheckVisitBeforeTransfer(boolean recheckVisitBeforeTransfer) {
        this.recheckVisitBeforeTransfer = recheckVisitBeforeTransfer;
    }

    public List<Integer> getAllowedBranches() {
        return allowedBranches;
    }

    public void setAllowedBranches(List<Integer> allowedBranches) {
        this.allowedBranches = allowedBranches;
    }

    public int getStaleCacheDurationSeconds() {
        return staleCacheDurationSeconds;
    }

    public void setStaleCacheDurationSeconds(int staleCacheDurationSeconds) {
        this.staleCacheDurationSeconds = staleCacheDurationSeconds;
    }

    public int getEventDeduplicationTtlSeconds() {
        return eventDeduplicationTtlSeconds;
    }

    public void setEventDeduplicationTtlSeconds(int eventDeduplicationTtlSeconds) {
        this.eventDeduplicationTtlSeconds = eventDeduplicationTtlSeconds;
    }

    public int getProcessedVisitTtlSeconds() {
        return processedVisitTtlSeconds;
    }

    public void setProcessedVisitTtlSeconds(int processedVisitTtlSeconds) {
        this.processedVisitTtlSeconds = processedVisitTtlSeconds;
    }

    public Map<String, Integer> getServicePriorityByKey() {
        return servicePriorityByKey;
    }

    public void setServicePriorityByKey(Map<String, Integer> servicePriorityByKey) {
        this.servicePriorityByKey = servicePriorityByKey;
    }

    public Integer getDefaultSourceEntryPointId() {
        return defaultSourceEntryPointId;
    }

    public void setDefaultSourceEntryPointId(Integer defaultSourceEntryPointId) {
        this.defaultSourceEntryPointId = defaultSourceEntryPointId;
    }

    public Map<Integer, Integer> getSourceEntryPointIdByBranch() {
        return sourceEntryPointIdByBranch;
    }

    public void setSourceEntryPointIdByBranch(Map<Integer, Integer> sourceEntryPointIdByBranch) {
        this.sourceEntryPointIdByBranch = sourceEntryPointIdByBranch;
    }

    public ExperimentalEndpoints getExperimentalEndpoints() {
        return experimentalEndpoints;
    }

    public void setExperimentalEndpoints(ExperimentalEndpoints experimentalEndpoints) {
        this.experimentalEndpoints = experimentalEndpoints;
    }

    /**
     * Проверяет, разрешено ли сервису работать с указанным отделением.
     *
     * @param branchId идентификатор отделения Orchestra
     * @return {@code true}, если branch явно разрешен или белый список пуст
     */
    public boolean isAllowedBranch(int branchId) {
        return allowedBranches == null || allowedBranches.isEmpty() || allowedBranches.contains(branchId);
    }

    /**
     * Возвращает конфигурируемый приоритет услуги.
     *
     * @param key      serviceId, internalName или externalName услуги
     * @param fallback значение по умолчанию, если приоритет явно не задан
     * @return найденный приоритет или переданный fallback
     */
    public int resolvePriority(String key, int fallback) {
        if (key == null) {
            return fallback;
        }
        Integer priority = servicePriorityByKey.get(key);
        return priority != null ? priority.intValue() : fallback;
    }

    /**
     * Определяет source entry point, который должен использоваться для конкретного отделения.
     *
     * <p>Сначала ищется branch-specific настройка, затем применяется общий fallback.</p>
     *
     * @param branchId идентификатор отделения Orchestra
     * @return entry point id или {@code null}, если ни branch-specific, ни default значение не заданы
     */
    public Integer resolveSourceEntryPointId(int branchId) {
        if (sourceEntryPointIdByBranch != null) {
            Integer branchSpecific = sourceEntryPointIdByBranch.get(Integer.valueOf(branchId));
            if (branchSpecific != null) {
                return branchSpecific;
            }
        }
        return defaultSourceEntryPointId;
    }

    /**
     * Конфигурируемые endpoint-ы для чтения и изменения состояния визита.
     *
     * <p>Эти пути вынесены в конфигурацию, потому что в разных инсталляциях Orchestra они часто
     * различаются или требуют отдельной верификации перед production rollout.</p>
     */
    @ConfigurationProperties("experimental-endpoints")
    public static class ExperimentalEndpoints {

        private boolean enabled;
        private String queueVisitsPath;
        private String visitDetailsPath;
        private String visitByIdPath;
        private String assignServicePath;
        private String transferVisitPath;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getQueueVisitsPath() {
            return queueVisitsPath;
        }

        public void setQueueVisitsPath(String queueVisitsPath) {
            this.queueVisitsPath = queueVisitsPath;
        }

        public String getVisitDetailsPath() {
            return visitDetailsPath;
        }

        public void setVisitDetailsPath(String visitDetailsPath) {
            this.visitDetailsPath = visitDetailsPath;
        }

        public String getVisitByIdPath() {
            return visitByIdPath;
        }

        public void setVisitByIdPath(String visitByIdPath) {
            this.visitByIdPath = visitByIdPath;
        }

        public String getAssignServicePath() {
            return assignServicePath;
        }

        public void setAssignServicePath(String assignServicePath) {
            this.assignServicePath = assignServicePath;
        }

        public String getTransferVisitPath() {
            return transferVisitPath;
        }

        public void setTransferVisitPath(String transferVisitPath) {
            this.transferVisitPath = transferVisitPath;
        }
    }
}
