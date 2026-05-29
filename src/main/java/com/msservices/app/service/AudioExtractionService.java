package com.msservices.app.service;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.repository.YoutubeAudioRepository;
import org.springframework.stereotype.Service;

@Service
public class AudioExtractionService {

    private final YoutubeAudioRepository youtubeAudioRepository;

    public AudioExtractionService(YoutubeAudioRepository youtubeAudioRepository) {
        this.youtubeAudioRepository = youtubeAudioRepository;
    }

    public AudioExtractionResponse extractAudio(AudioExtractionRequest request) {
        String videoName = validateAndNormalizeVideoName(request);
        ExtractedAudioDto extractedAudio = youtubeAudioRepository.extractAudioByVideoName(videoName);
        return AudioExtractionResponse.success("Audio extraido correctamente.", extractedAudio);
    }

    private String validateAndNormalizeVideoName(AudioExtractionRequest request) {
        if (request == null || request.getVideoName() == null || request.getVideoName().trim().isEmpty()) {
            throw new InvalidVideoSearchException("Ingresa el nombre de un video para buscar en YouTube.");
        }

        String videoName = request.getVideoName().trim();

        if (videoName.length() < 3) {
            throw new InvalidVideoSearchException("La busqueda es demasiado corta. Escribe al menos 3 caracteres.");
        }

        if (videoName.length() > 150) {
            throw new InvalidVideoSearchException("La busqueda es demasiado larga. Intenta con un nombre mas corto.");
        }

        return videoName;
    }
}
