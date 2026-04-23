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
     * Разрешает ли SERVICE_POINT_OPEN запускать mutating workflow.
     *
     * <p>По логам Orchestra этот сигнал часто приходит раньше окончательной фиксации work profile,
     * поэтому по умолчанию отключен и используется только как диагностический.</p>
     */
    private boolean servicePointOpenTriggerEnabled = false;

    /**
     * Разрешает ли SET_WORK_PROFILE запускать mutating workflow.
     *
     * <p>После обнаружения USER_SERVICE_POINT_SESSION_START этот сигнал по умолчанию оставлен
     * только как вспомогательный и диагностический.</p>
     */
    private boolean setWorkProfileTriggerEnabled = false;

    /**
     * Разрешает ли USER_SERVICE_POINT_SESSION_START запускать mutating workflow.
     *
     * <p>Это основной рекомендуемый trigger: он лучше всего соответствует реальному началу
     * пользовательской сессии на точке обслуживания.</p>
     */
    private boolean userServicePointSessionStartTriggerEnabled = true;


    /**
     * Разрешает ли изменение рабочего профиля внутри уже активной пользовательской сессии
     * запускать повторный assignment cycle, если новый профиль расширяет доступный набор услуг.
     */
    private boolean workProfileExpandedTriggerEnabled = true;

    /**
     * Окно ожидания финального SET_WORK_PROFILE после USER_SERVICE_POINT_SESSION_START.
     *
     * <p>По наблюдаемым логам итоговый профиль приходит через десятки миллисекунд,
     * поэтому по умолчанию даем короткое окно на стабилизацию посадки.</p>
     */
    private long userSessionSettleWindowMs = 2000L;

    /**
     * Нужно ли перечитывать визит перед переводом между очередями.
     */
    private boolean recheckVisitBeforeTransfer = true;

    /**
     * Прерывать ли текущий цикл после первого 403/контекстного отказа mutation-запроса.
     *
     * <p>По логам Orchestra повторные PUT в том же невалидном контексте почти всегда
     * приводят к одинаковым 403, поэтому безопаснее остановить цикл и дождаться
     * следующего события или polling fallback.</p>
     */
    private boolean abortCycleOnForbiddenMutation = true;

    /**
     * Считать ли ответ assign с userState=INACTIVE контекстной ошибкой.
     *
     * <p>Такой ответ означает, что Orchestra приняла запрос формально, но операторский
     * контекст EntryPoint ещё не активирован для безопасного последующего transfer.</p>
     */
    private boolean treatInactiveUserStateAsFailure = true;

    /**
     * Считать ли ответ assign с userState=NO_STARTED_SERVICE_POINT_SESSION контекстной ошибкой.
     *
     * <p>По свежим логам Orchestra такое состояние означает, что текущая service point session
     * ещё не стартовала в серверном EntryPoint-контуре, даже если assign формально вернул 200.</p>
     */
    private boolean treatNoStartedServicePointSessionAsFailure = true;

    /**
     * Конфигурация предварительной активации operator/service-point context.
     */
    private Activation activation = new Activation();

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

    public boolean isServicePointOpenTriggerEnabled() {
        return servicePointOpenTriggerEnabled;
    }

    public void setServicePointOpenTriggerEnabled(boolean servicePointOpenTriggerEnabled) {
        this.servicePointOpenTriggerEnabled = servicePointOpenTriggerEnabled;
    }

    public boolean isSetWorkProfileTriggerEnabled() {
        return setWorkProfileTriggerEnabled;
    }

    public void setSetWorkProfileTriggerEnabled(boolean setWorkProfileTriggerEnabled) {
        this.setWorkProfileTriggerEnabled = setWorkProfileTriggerEnabled;
    }

    public boolean isUserServicePointSessionStartTriggerEnabled() {
        return userServicePointSessionStartTriggerEnabled;
    }

    public void setUserServicePointSessionStartTriggerEnabled(boolean userServicePointSessionStartTriggerEnabled) {
        this.userServicePointSessionStartTriggerEnabled = userServicePointSessionStartTriggerEnabled;
    }

    public boolean isWorkProfileExpandedTriggerEnabled() {
        return workProfileExpandedTriggerEnabled;
    }

    public void setWorkProfileExpandedTriggerEnabled(boolean workProfileExpandedTriggerEnabled) {
        this.workProfileExpandedTriggerEnabled = workProfileExpandedTriggerEnabled;
    }

    public long getUserSessionSettleWindowMs() {
        return userSessionSettleWindowMs;
    }

    public void setUserSessionSettleWindowMs(long userSessionSettleWindowMs) {
        this.userSessionSettleWindowMs = userSessionSettleWindowMs;
    }

    public boolean isRecheckVisitBeforeTransfer() {
        return recheckVisitBeforeTransfer;
    }

    public void setRecheckVisitBeforeTransfer(boolean recheckVisitBeforeTransfer) {
        this.recheckVisitBeforeTransfer = recheckVisitBeforeTransfer;
    }

    public boolean isAbortCycleOnForbiddenMutation() {
        return abortCycleOnForbiddenMutation;
    }

    public void setAbortCycleOnForbiddenMutation(boolean abortCycleOnForbiddenMutation) {
        this.abortCycleOnForbiddenMutation = abortCycleOnForbiddenMutation;
    }

    public boolean isTreatInactiveUserStateAsFailure() {
        return treatInactiveUserStateAsFailure;
    }

    public void setTreatInactiveUserStateAsFailure(boolean treatInactiveUserStateAsFailure) {
        this.treatInactiveUserStateAsFailure = treatInactiveUserStateAsFailure;
    }

    public boolean isTreatNoStartedServicePointSessionAsFailure() {
        return treatNoStartedServicePointSessionAsFailure;
    }

    public void setTreatNoStartedServicePointSessionAsFailure(boolean treatNoStartedServicePointSessionAsFailure) {
        this.treatNoStartedServicePointSessionAsFailure = treatNoStartedServicePointSessionAsFailure;
    }

    public Activation getActivation() {
        return activation;
    }

    public void setActivation(Activation activation) {
        this.activation = activation;
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
     * Проверяет, разрешено ли источнику события запускать mutating workflow.
     */
    public boolean isTriggerEnabled(com.qsystems.meddoctorassignment.model.event.TriggerSource triggerSource) {
        if (triggerSource == null) {
            return false;
        }
        switch (triggerSource) {
            case SERVICE_POINT_OPEN:
                return servicePointOpenTriggerEnabled;
            case SET_WORK_PROFILE:
                return setWorkProfileTriggerEnabled;
            case USER_SERVICE_POINT_SESSION_START:
            case USER_SESSION_READY:
                return userServicePointSessionStartTriggerEnabled;
            case WORK_PROFILE_EXPANDED:
                return workProfileExpandedTriggerEnabled;
            case POLLING:
                return true;
            default:
                return false;
        }
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
     * Конфигурация предварительного activation-step перед mutating REST.
     */
    @ConfigurationProperties("activation")
    public static class Activation {

        /**
         * Включает отдельный activation-step перед assign/transfer.
         */
        private boolean enabled = false;

        /**
         * Прерывать ли текущий цикл, если activation-step завершился ошибкой.
         */
        private boolean failCycleOnError = true;

        /**
         * HTTP-метод вызова activation-step. Поддерживаются GET/POST/PUT/PATCH/DELETE.
         */
        private String method = "POST";

        /**
         * Путь activation endpoint-а. Может содержать placeholders:
         * {branchId}, {servicePointId}, {staffId}, {workProfileId}, {servicePointName},
         * {workProfileName}, {userName}.
         */
        private String path;

        /**
         * Тело запроса activation-step как шаблон строки с теми же placeholders.
         *
         * <p>Шаблон подставляется как есть, поэтому кавычки и формат JSON нужно задавать
         * непосредственно в конфигурации.</p>
         */
        private String payloadTemplate = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailCycleOnError() {
            return failCycleOnError;
        }

        public void setFailCycleOnError(boolean failCycleOnError) {
            this.failCycleOnError = failCycleOnError;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPayloadTemplate() {
            return payloadTemplate;
        }

        public void setPayloadTemplate(String payloadTemplate) {
            this.payloadTemplate = payloadTemplate;
        }
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
