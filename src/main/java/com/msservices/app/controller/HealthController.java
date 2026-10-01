package com.msservices.app.controller;

import com.msservices.app.config.YtdlpProperties;
import com.msservices.app.config.YoutubeDataApiProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final YtdlpProperties ytdlpProperties;
    private final YoutubeDataApiProperties dataApiProperties;

    public HealthController(YtdlpProperties ytdlpProperties, YoutubeDataApiProperties dataApiProperties) {
        this.ytdlpProperties = ytdlpProperties;
        this.dataApiProperties = dataApiProperties;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean cookiesAvailable = cookiesFileAvailable();
        boolean extractionReady = cookiesAvailable || !ytdlpProperties.isCookiesRequired();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", extractionReady ? "UP" : "DEGRADED");
        body.put("cookies", cookiesAvailable ? "available" : "missing");
        body.put("poToken", ytdlpProperties.isPotEnabled() ? "enabled" : "disabled");
        body.put("search", dataApiProperties.isEnabled() && !dataApiProperties.getApiKey().isBlank()
                ? "data-api"
                : "yt-dlp");
        body.put("playerClients", ytdlpProperties.getPlayerClients());

        return ResponseEntity
                .status(extractionReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private boolean cookiesFileAvailable() {
        String configuredPath = ytdlpProperties.getCookiesPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return false;
        }
        Path cookies = Path.of(configuredPath);
        return Files.isRegularFile(cookies) && Files.isReadable(cookies);
    }
}
