package com.msservices.app.controller;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.service.AudioExtractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audios")
public class AudioExtractionController {

    private final AudioExtractionService audioExtractionService;

    public AudioExtractionController(AudioExtractionService audioExtractionService) {
        this.audioExtractionService = audioExtractionService;
    }

    @PostMapping("/extract")
    public ResponseEntity<AudioExtractionResponse> extractAudio(@RequestBody AudioExtractionRequest request) {
        return ResponseEntity.ok(audioExtractionService.extractAudio(request));
    }
}
