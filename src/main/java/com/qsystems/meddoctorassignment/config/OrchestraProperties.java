package com.qsystems.meddoctorassignment.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Базовые параметры подключения к Orchestra.
 */
@ConfigurationProperties("application.orchestra")
public class OrchestraProperties {

    private String url;
    private String username;
    private String password;
    private String commonRestPath = "/rest";
    private String configurationRestPath = "/qsystem/rest/config";
    private String branchesForCache = "*";
    private boolean replayMutationCookies = false;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCommonRestPath() {
        return commonRestPath;
    }

    public void setCommonRestPath(String commonRestPath) {
        this.commonRestPath = commonRestPath;
    }

    public String getConfigurationRestPath() {
        return configurationRestPath;
    }

    public void setConfigurationRestPath(String configurationRestPath) {
        this.configurationRestPath = configurationRestPath;
    }

    public String getBranchesForCache() {
        return branchesForCache;
    }

    public void setBranchesForCache(String branchesForCache) {
        this.branchesForCache = branchesForCache;
    }

    public boolean isReplayMutationCookies() {
        return replayMutationCookies;
    }

    public void setReplayMutationCookies(boolean replayMutationCookies) {
        this.replayMutationCookies = replayMutationCookies;
    }
}
