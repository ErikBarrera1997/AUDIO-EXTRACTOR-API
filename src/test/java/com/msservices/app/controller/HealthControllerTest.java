package com.msservices.app.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msservices.app.config.YtdlpProperties;
import com.msservices.app.config.YoutubeDataApiProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    @Test
    @DisplayName("Health check answers GET without a body, so Render can reach it")
    void healthIsReachableWithGet() throws Exception {
        HealthController controller = controllerWith(new YtdlpProperties(), new YoutubeDataApiProperties());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Health check never leaks the cookies path or the API key")
    void healthDoesNotLeakSecrets() throws Exception {
        YtdlpProperties ytdlp = new YtdlpProperties();
        ytdlp.setCookiesPath("/nonexistent/cookies.txt");
        YoutubeDataApiProperties dataApi = new YoutubeDataApiProperties();
        dataApi.setApiKey("AIza-not-a-real-key");

        HealthController controller = controllerWith(ytdlp, dataApi);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String body = mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertFalse(body.contains("cookies.txt"), "must not expose the cookies path");
        Assertions.assertFalse(body.contains("/nonexistent/"), "must not expose filesystem paths");
        Assertions.assertFalse(body.contains("AIza"), "must not expose the API key");
    }

    @Test
    @DisplayName("A readable cookies file reports UP with 200")
    void healthyWhenCookiesAvailable(@TempDir Path tempDir) throws Exception {
        Path cookies = tempDir.resolve("cookies.txt");
        Files.writeString(cookies, "# Netscape HTTP Cookie File\n");

        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath(cookies.toString());
        properties.setCookiesRequired(true);

        HealthController controller = controllerWith(properties, new YoutubeDataApiProperties());
        ResponseEntity<Map<String, Object>> response = controller.health();

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("UP", response.getBody().get("status"));
        Assertions.assertEquals("available", response.getBody().get("cookies"));
    }

    @Test
    @DisplayName("A missing cookies file with cookies required reports DEGRADED")
    void degradedWhenCookiesMissing() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath("/nonexistent/cookies.txt");
        properties.setCookiesRequired(true);
        properties.setUserCookiesEnabled(false);

        HealthController controller = controllerWith(properties, new YoutubeDataApiProperties());
        ResponseEntity<Map<String, Object>> response = controller.health();

        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Assertions.assertEquals("DEGRADED", response.getBody().get("status"));
        Assertions.assertEquals("missing", response.getBody().get("cookies"));
        Assertions.assertEquals("disabled", response.getBody().get("userCookies"));
    }

    @Test
    @DisplayName("User cookies enabled keeps the service UP without a static cookies file")
    void healthyWhenUserCookiesEnabled() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath("/nonexistent/cookies.txt");
        properties.setCookiesRequired(true);
        properties.setUserCookiesEnabled(true);

        ResponseEntity<Map<String, Object>> response = controllerWith(properties, new YoutubeDataApiProperties())
                .health();

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("UP", response.getBody().get("status"));
        Assertions.assertEquals("missing", response.getBody().get("cookies"));
        Assertions.assertEquals("enabled", response.getBody().get("userCookies"));
    }

    @Test
    @DisplayName("Cookies optional and absent still reports UP so the deploy is not killed")
    void healthyWhenCookiesOptional() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath("/nonexistent/cookies.txt");
        properties.setCookiesRequired(false);

        HealthController controller = controllerWith(properties, new YoutubeDataApiProperties());
        ResponseEntity<Map<String, Object>> response = controller.health();

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    @DisplayName("A directory is not accepted as a valid cookies file")
    void directoryIsNotValidCookies(@TempDir Path tempDir) {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath(tempDir.toString());
        properties.setCookiesRequired(true);

        HealthController controller = controllerWith(properties, new YoutubeDataApiProperties());

        Assertions.assertEquals("missing", controller.health().getBody().get("cookies"));
    }

    @Test
    @DisplayName("Health reports which search backend is active")
    void reportsSearchBackend() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath("/nonexistent/cookies.txt");
        properties.setCookiesRequired(false);

        YoutubeDataApiProperties dataApi = new YoutubeDataApiProperties();
        dataApi.setEnabled(false);
        Assertions.assertEquals("yt-dlp",
                controllerWith(properties, dataApi).health().getBody().get("search"));

        dataApi.setEnabled(true);
        dataApi.setApiKey("");
        Assertions.assertEquals("yt-dlp",
                controllerWith(properties, dataApi).health().getBody().get("search"));

        dataApi.setApiKey("AIza-not-a-real-key");
        Assertions.assertEquals("data-api",
                controllerWith(properties, dataApi).health().getBody().get("search"));
    }

    @Test
    @DisplayName("Health reports the proxy as not configured when none is set")
    void reportsProxyNotConfigured() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesRequired(false);

        Assertions.assertEquals("not-configured", controllerWith(properties, new YoutubeDataApiProperties())
                .health().getBody().get("proxy"));
    }

    @Test
    @DisplayName("Health reports the proxy as configured without exposing it")
    void reportsProxyConfiguredWithoutLeakingIt() throws Exception {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesRequired(false);
        properties.setProxy("http://provider-user:sup3rs3cret@us-west.proxy.test:8000");

        HealthController controller = controllerWith(properties, new YoutubeDataApiProperties());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String body = mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertEquals("configured", controller.health().getBody().get("proxy"));
        Assertions.assertFalse(body.contains("sup3rs3cret"), "must not expose the proxy password");
        Assertions.assertFalse(body.contains("provider-user"), "must not expose the proxy username");
        Assertions.assertFalse(body.contains("proxy.test"), "must not expose the proxy host");
        Assertions.assertFalse(body.contains("http://"), "must not expose the proxy scheme");
    }

    @Test
    @DisplayName("Health reports the PO Token state and the client chain")
    void reportsPotAndClients() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath("/nonexistent/cookies.txt");
        properties.setCookiesRequired(false);
        properties.setPotEnabled(false);

        Map<String, Object> body = controllerWith(properties, new YoutubeDataApiProperties())
                .health()
                .getBody();

        Assertions.assertEquals("disabled", body.get("poToken"));
        Assertions.assertEquals(java.util.List.of("tv_embedded", "web_safari", "ios", "android_vr"),
                body.get("playerClients"));
    }

    private HealthController controllerWith(YtdlpProperties ytdlp, YoutubeDataApiProperties dataApi) {
        return new HealthController(ytdlp, dataApi);
    }
}
