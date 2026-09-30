package com.msservices.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureConfigurationDefaultsTest {

    @Test
    @DisplayName("Cookies are required by default so the app fails closed")
    void cookiesRequiredByDefault() {
        assertTrue(new YtdlpProperties().isCookiesRequired());
    }

    @Test
    @DisplayName("The cookies path is not baked into the source tree")
    void cookiesPathIsOutsideTheProject() {
        String cookiesPath = new YtdlpProperties().getCookiesPath();
        assertTrue(cookiesPath.startsWith("/app/"), "cookies must live outside the repository");
    }

    @Test
    @DisplayName("The PO Token provider is enabled and bound to loopback")
    void potProviderIsLoopbackOnly() {
        YtdlpProperties properties = new YtdlpProperties();

        assertTrue(properties.isPotEnabled());
        String url = properties.getPotProviderUrl();
        assertTrue(url.startsWith("http://127.0.0.1:"), "provider must not be exposed publicly, got: " + url);
    }

    @Test
    @DisplayName("No proxy is configured by default")
    void noProxyByDefault() {
        assertTrue(new YtdlpProperties().getProxy().isBlank());
    }

    @Test
    @DisplayName("The client chain is the agreed fallback order")
    void clientChainOrder() {
        List<String> clients = new YtdlpProperties().getPlayerClients();

        assertEquals(List.of("tv_embedded", "web_safari", "ios", "android_vr"), clients);
    }

    @Test
    @DisplayName("No YouTube API key is hardcoded as a default")
    void dataApiHasNoDefaultKey() {
        assertTrue(new YoutubeDataApiProperties().getApiKey().isBlank(),
                "the API key must come from the environment only");
    }

    @Test
    @DisplayName("Extraction and search have bounded timeouts")
    void timeoutsAreBounded() {
        YtdlpProperties properties = new YtdlpProperties();

        assertTrue(properties.getExtractionTimeoutSeconds() > 0);
        assertTrue(properties.getSearchTimeoutSeconds() > 0);
        assertTrue(properties.getAttemptTimeoutSeconds() <= properties.getExtractionTimeoutSeconds(),
                "a single attempt must not outlive the overall extraction budget");
    }
}
