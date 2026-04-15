package com.qsystems.meddoctorassignment.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("application.assignment")
public class AssignmentProperties {

    private boolean enabled = true;
    private Integer unknownDoctorQueueId;
    private int maxVisitsPerCycle = 50;
    private String pollingCron = "0 */5 * * * ?";
    private long branchLockTimeoutMs = 5000L;
    private boolean dryRun = true;
    private boolean recheckVisitBeforeTransfer = true;
    private List<Integer> allowedBranches = new ArrayList<Integer>();
    private int staleCacheDurationSeconds = 300;
    private int eventDeduplicationTtlSeconds = 120;
    private int processedVisitTtlSeconds = 900;
    private Map<String, Integer> servicePriorityByKey = new HashMap<String, Integer>();
    private Integer defaultSourceEntryPointId;
    private Map<Integer, Integer> sourceEntryPointIdByBranch = new HashMap<Integer, Integer>();
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

    public boolean isAllowedBranch(int branchId) {
        return allowedBranches == null || allowedBranches.isEmpty() || allowedBranches.contains(branchId);
    }

    public int resolvePriority(String key, int fallback) {
        if (key == null) {
            return fallback;
        }
        Integer priority = servicePriorityByKey.get(key);
        return priority != null ? priority.intValue() : fallback;
    }

    public Integer resolveSourceEntryPointId(int branchId) {
        if (sourceEntryPointIdByBranch != null) {
            Integer branchSpecific = sourceEntryPointIdByBranch.get(Integer.valueOf(branchId));
            if (branchSpecific != null) {
                return branchSpecific;
            }
        }
        return defaultSourceEntryPointId;
    }

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
