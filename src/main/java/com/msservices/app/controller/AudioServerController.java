package com.msservices.app.controller;

import com.msservices.app.dto.AudioServerInfo;
import com.msservices.app.provider.AudioServerRegistry;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/servers")
public class AudioServerController {

    private final AudioServerRegistry serverRegistry;

    public AudioServerController(AudioServerRegistry serverRegistry) {
        this.serverRegistry = serverRegistry;
    }

    @GetMapping
    public ResponseEntity<List<AudioServerInfo>> servers() {
        return ResponseEntity.ok(serverRegistry.availableServers());
    }
}
