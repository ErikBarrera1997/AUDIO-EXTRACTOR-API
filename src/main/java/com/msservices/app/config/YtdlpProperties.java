package com.msservices.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ytdlp")
public class YtdlpProperties {

    private String cookiesPath = "/app/cookies.txt";
    private boolean cookiesRequired = true;
    private boolean potEnabled = true;
    private String potProviderUrl = "http://127.0.0.1:4416";
    private String proxy = "";
    private int attemptTimeoutSeconds = 90;
    private int extractionTimeoutSeconds = 180;
    private int searchTimeoutSeconds = 60;
    private long maxDurationSeconds = 900L;
    private int searchLimit = 15;
    private List<String> playerClients = new ArrayList<>(List.of(
            "tv_embedded",
            "web_safari",
            "ios",
            "android_vr"
    ));

    public String getCookiesPath() {
        return cookiesPath;
    }

    public void setCookiesPath(String cookiesPath) {
        this.cookiesPath = cookiesPath;
    }

    public boolean isCookiesRequired() {
        return cookiesRequired;
    }

    public void setCookiesRequired(boolean cookiesRequired) {
        this.cookiesRequired = cookiesRequired;
    }

    public boolean isPotEnabled() {
        return potEnabled;
    }

    public void setPotEnabled(boolean potEnabled) {
        this.potEnabled = potEnabled;
    }

    public String getPotProviderUrl() {
        return potProviderUrl;
    }

    public void setPotProviderUrl(String potProviderUrl) {
        this.potProviderUrl = potProviderUrl;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public int getAttemptTimeoutSeconds() {
        return attemptTimeoutSeconds;
    }

    public void setAttemptTimeoutSeconds(int attemptTimeoutSeconds) {
        this.attemptTimeoutSeconds = attemptTimeoutSeconds;
    }

    public int getExtractionTimeoutSeconds() {
        return extractionTimeoutSeconds;
    }

    public void setExtractionTimeoutSeconds(int extractionTimeoutSeconds) {
        this.extractionTimeoutSeconds = extractionTimeoutSeconds;
    }

    public int getSearchTimeoutSeconds() {
        return searchTimeoutSeconds;
    }

    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) {
        this.searchTimeoutSeconds = searchTimeoutSeconds;
    }

    public long getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(long maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public List<String> getPlayerClients() {
        return playerClients;
    }

    public void setPlayerClients(List<String> playerClients) {
        this.playerClients = playerClients;
    }
}
