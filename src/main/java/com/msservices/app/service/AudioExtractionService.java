package com.msservices.app.service;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.dto.AudioSearchResponse;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.repository.YoutubeAudioRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AudioExtractionService {

    private final YoutubeAudioRepository youtubeAudioRepository;

    public AudioExtractionService(YoutubeAudioRepository youtubeAudioRepository) {
        this.youtubeAudioRepository = youtubeAudioRepository;
    }

    public AudioSearchResponse searchVideos(String videoName) {
        String normalizedName = validateAndNormalizeVideoName(videoName);
        List<AudioSearchResultDto> results = youtubeAudioRepository.searchVideos(normalizedName);
        return AudioSearchResponse.success("Resultados obtenidos.", results);
    }

    public AudioExtractionResponse extractAudio(AudioExtractionRequest request) {
        String videoName = validateAndNormalizeVideoName(request);
        ExtractedAudioDto extractedAudio = youtubeAudioRepository.extractAudioByVideoName(videoName, request.getVideoId());
        return AudioExtractionResponse.success("Audio extraido correctamente.", extractedAudio);
    }

    private String validateAndNormalizeVideoName(AudioExtractionRequest request) {
        return validateAndNormalizeVideoName(request == null ? null : request.getVideoName());
    }

    private String validateAndNormalizeVideoName(String rawVideoName) {
        if (rawVideoName == null || rawVideoName.trim().isEmpty()) {
            throw new InvalidVideoSearchException("Enter the name of a video to search on YouTube.");
        }

        String videoName = rawVideoName.trim();

        if (videoName.length() < 3) {
            throw new InvalidVideoSearchException("The search is too short. Enter at least 3 characters.");
        }

        if (videoName.length() > 150) {
            throw new InvalidVideoSearchException("The search is too long. Try a shorter name.");
        }

        return videoName;
    }
}
