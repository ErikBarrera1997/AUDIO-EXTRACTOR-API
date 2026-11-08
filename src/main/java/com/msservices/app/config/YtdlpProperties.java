package com.msservices.app.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ytdlp")
public class YtdlpProperties implements InitializingBean {

    private String cookiesPath = "/app/cookies.txt";
    private boolean cookiesRequired = true;
    private boolean userCookiesEnabled = true;
    private boolean potEnabled = false;
    private String potProviderUrl = "http://127.0.0.1:4416";
    private String proxy = "";
    private static final Set<String> SUPPORTED_PROXY_SCHEMES =
            Set.of("http", "https", "socks4", "socks5", "socks5h");
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

    public boolean isUserCookiesEnabled() {
        return userCookiesEnabled;
    }

    public void setUserCookiesEnabled(boolean userCookiesEnabled) {
        this.userCookiesEnabled = userCookiesEnabled;
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
        this.proxy = normalizeProxy(proxy);
    }

    // Blank means "no proxy", which is the default and stays valid. Anything else must be a real
    // proxy URL: a typo here otherwise surfaces only as an opaque yt-dlp failure on every request,
    // long after the deploy that introduced it. The parsed value is never echoed back in the
    // message because it carries provider credentials.
    private String normalizeProxy(String rawProxy) {
        String trimmed = rawProxy == null ? "" : rawProxy.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        URI parsed;
        try {
            parsed = new URI(trimmed);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(invalidProxyMessage(), exception);
        }

        String scheme = parsed.getScheme();
        if (scheme == null || !SUPPORTED_PROXY_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(invalidProxyMessage());
        }

        if (parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new IllegalArgumentException(invalidProxyMessage());
        }

        return trimmed;
    }

    private String invalidProxyMessage() {
        return "YTDLP_PROXY is not a valid proxy URL. Supported schemes: "
                + String.join(", ", SUPPORTED_PROXY_SCHEMES.stream().sorted().toList())
                + ". The value itself is omitted from this message because it holds credentials.";
    }

    // Runs at bean construction so a bad proxy fails the deploy in the Render logs, instead of
    // failing one request at a time. The blank default stays valid.
    @Override
    public void afterPropertiesSet() {
        this.proxy = normalizeProxy(this.proxy);
    }

    // Replaces the userinfo with a fixed marker while keeping the scheme and port, which are the
    // parts worth reading in a log line. Everything before the '@' in the authority is dropped, so
    // no username or password can survive this.
    public String getMaskedProxy() {
        if (proxy.isEmpty()) {
            return "";
        }

        int schemeEnd = proxy.indexOf("://");
        if (schemeEnd < 0) {
            return "***";
        }

        int authorityStart = schemeEnd + 3;
        int atIndex = proxy.indexOf('@', authorityStart);
        if (atIndex < 0) {
            return proxy;
        }

        int pathStart = proxy.indexOf('/', authorityStart);
        if (pathStart < 0 || pathStart < atIndex) {
            return proxy.substring(0, authorityStart) + "***" + proxy.substring(atIndex);
        }
        return proxy.substring(0, authorityStart) + "***" + proxy.substring(atIndex, pathStart);
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
