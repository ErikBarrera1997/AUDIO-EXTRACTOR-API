package com.msservices.app.service;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.dto.AudioSearchResponse;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.repository.YoutubeAudioRepository;
import com.msservices.app.repository.YoutubeDataApiSearchRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("Youtube")
public class AudioExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AudioExtractionService.class);
    private static final int MAX_VIDEO_ID_LENGTH = 32;

    private final YoutubeAudioRepository youtubeAudioRepository;
    private final YoutubeDataApiSearchRepository dataApiSearchRepository;

    public AudioExtractionService(YoutubeAudioRepository youtubeAudioRepository,
                                  YoutubeDataApiSearchRepository dataApiSearchRepository) {
        this.youtubeAudioRepository = youtubeAudioRepository;
        this.dataApiSearchRepository = dataApiSearchRepository;
    }

    public AudioSearchResponse searchVideos(String videoName) {
        String normalizedName = validateAndNormalizeVideoName(videoName);
        List<AudioSearchResultDto> results = dataApiSearchRepository.isAvailable()
                ? searchWithDataApi(normalizedName)
                : youtubeAudioRepository.searchVideos(normalizedName);
        return AudioSearchResponse.success("Resultados obtenidos.", results);
    }

    private List<AudioSearchResultDto> searchWithDataApi(String normalizedName) {
        try {
            return dataApiSearchRepository.searchVideos(normalizedName);
        } catch (InvalidVideoSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("YouTube Data API search failed, falling back to yt-dlp: {}", exception.getMessage());
            return youtubeAudioRepository.searchVideos(normalizedName);
        }
    }

    public AudioExtractionResponse extractAudio(AudioExtractionRequest request) {
        String videoName = validateAndNormalizeVideoName(request);
        String videoId = validateAndNormalizeVideoId(request == null ? null : request.getVideoId());
        ExtractedAudioDto extractedAudio = youtubeAudioRepository.extractAudioByVideoName(videoName, videoId);
        return AudioExtractionResponse.success("Audio extraido correctamente.", extractedAudio);
    }

    private String validateAndNormalizeVideoName(AudioExtractionRequest request) {
        return validateAndNormalizeVideoName(request == null ? null : request.getVideoName());
    }

    private String validateAndNormalizeVideoId(String rawVideoId) {
        if (rawVideoId == null || rawVideoId.isBlank()) {
            return null;
        }

        String videoId = rawVideoId.trim();
        if (videoId.length() > MAX_VIDEO_ID_LENGTH) {
            throw new InvalidVideoSearchException("The video identifier is not valid. Try another result.");
        }

        for (int index = 0; index < videoId.length(); index++) {
            char character = videoId.charAt(index);
            boolean isAllowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '_';
            if (!isAllowed) {
                throw new InvalidVideoSearchException("The video identifier is not valid. Try another result.");
            }
        }

        return videoId;
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
