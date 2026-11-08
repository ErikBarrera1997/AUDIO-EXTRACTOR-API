package com.msservices.app.controller;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.dto.AudioSearchResponse;
import com.msservices.app.service.AudioExtractionService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audios")
public class AudioExtractionController {

	@Qualifier("Youtube")
    private final AudioExtractionService audioExtractionService;

    public AudioExtractionController(AudioExtractionService audioExtractionService) {
        this.audioExtractionService = audioExtractionService;
    }

    @PostMapping("/search")
    public ResponseEntity<AudioSearchResponse> searchVideos(@RequestBody AudioExtractionRequest request) {
        String videoName = request == null ? null : request.getVideoName();
        String cookies = request == null ? null : request.getCookies();
        Boolean dataUsageConsent = request == null ? null : request.getDataUsageConsent();
        String serverId = request == null ? null : request.getServerId();
        return ResponseEntity.ok(
                audioExtractionService.searchVideos(videoName, cookies, dataUsageConsent, serverId));
    }

    @PostMapping("/extract")
    public ResponseEntity<AudioExtractionResponse> extractAudio(@RequestBody AudioExtractionRequest request) {
        return ResponseEntity.ok(audioExtractionService.extractAudio(request));
    }
}
