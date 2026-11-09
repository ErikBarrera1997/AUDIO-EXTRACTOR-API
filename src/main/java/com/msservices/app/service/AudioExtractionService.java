package com.msservices.app.service;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.dto.AudioSearchResponse;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.CookieConsentRequiredException;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.provider.AudioServerProvider;
import com.msservices.app.provider.AudioServerRegistry;
import com.msservices.app.provider.LocalAudioServerProvider;
import com.msservices.app.provider.YouTubeAudioServerProvider;
import com.msservices.app.repository.YoutubeAudioRepository;
import com.msservices.app.repository.YoutubeDataApiSearchRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("Youtube")
public class AudioExtractionService {

    private static final String COOKIE_CONSENT_MESSAGE =
            "Your cookies were not used because the data usage consent is missing. "
                    + "Resend the request with dataUsageConsent=true to allow cookies, "
                    + "or send dataUsageConsent=false to continue without cookies.";
    private static final int MAX_VIDEO_ID_LENGTH = 32;

    private final AudioServerRegistry serverRegistry;

    @Autowired
    public AudioExtractionService(AudioServerRegistry serverRegistry) {
        this.serverRegistry = serverRegistry;
    }

    // Kept for callers and tests that build the service by hand. It wires the providers that exist
    // today; new providers are picked up by Spring through AudioServerRegistry.
    public AudioExtractionService(YoutubeAudioRepository youtubeAudioRepository,
                                  YoutubeDataApiSearchRepository dataApiSearchRepository) {
        this(new AudioServerRegistry(List.of(
                new YouTubeAudioServerProvider(youtubeAudioRepository, dataApiSearchRepository),
                new LocalAudioServerProvider(youtubeAudioRepository, dataApiSearchRepository))));
    }

    public AudioSearchResponse searchVideos(String videoName) {
        return searchVideos(videoName, null, Boolean.TRUE, null);
    }

    public AudioSearchResponse searchVideos(String videoName, String rawCookies) {
        // Legacy overload: the caller already has the cookies, so consent is implied.
        return searchVideos(videoName, rawCookies, Boolean.TRUE, null);
    }

    public AudioSearchResponse searchVideos(String videoName, String rawCookies,
                                            Boolean dataUsageConsent, String serverId) {
        String normalizedName = validateAndNormalizeVideoName(videoName);
        AudioServerProvider provider = serverRegistry.resolve(serverId);
        String cookies = provider.supportsUserCookies()
                ? resolveCookies(rawCookies, dataUsageConsent)
                : null;
        List<AudioSearchResultDto> results = provider.search(normalizedName, cookies);
        return AudioSearchResponse.success("Resultados obtenidos.", results);
    }

    public AudioExtractionResponse extractAudio(AudioExtractionRequest request) {
        String videoId = validateAndNormalizeVideoId(request == null ? null : request.getVideoId());
        String videoName = validateAndNormalizeExtractionName(
                request == null ? null : request.getVideoName(), videoId);
        AudioServerProvider provider = serverRegistry.resolve(
                request == null ? null : request.getServerId());
        String cookies = provider.supportsUserCookies()
                ? resolveCookies(
                        request == null ? null : request.getCookies(),
                        request == null ? null : request.getDataUsageConsent())
                : null;
        ExtractedAudioDto extractedAudio = provider.extract(videoName, videoId, cookies);
        return AudioExtractionResponse.success("Audio extraido correctamente.", extractedAudio);
    }

    // A null consent means the client has not shown the notice yet. A false consent means the user
    // declined: the request continues without cookies through the default server. Neither case
    // logs or echoes the cookie content.
    private String resolveCookies(String rawCookies, Boolean dataUsageConsent) {
        if (rawCookies == null || rawCookies.isBlank()) {
            return null;
        }
        if (dataUsageConsent == null) {
            throw new CookieConsentRequiredException(COOKIE_CONSENT_MESSAGE);
        }
        return dataUsageConsent ? rawCookies : null;
    }

    // A valid video id fully identifies the target, so the name is optional there. The length rules
    // only apply when the name is what yt-dlp has to search with.
    private String validateAndNormalizeExtractionName(String rawVideoName, String videoId) {
        if (videoId != null) {
            if (rawVideoName == null || rawVideoName.trim().isEmpty()) {
                return null;
            }
            String trimmed = rawVideoName.trim();
            return trimmed.length() > 150 ? trimmed.substring(0, 150) : trimmed;
        }

        return validateAndNormalizeVideoName(rawVideoName);
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
