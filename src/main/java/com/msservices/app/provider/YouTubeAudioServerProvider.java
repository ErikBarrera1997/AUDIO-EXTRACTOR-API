package com.msservices.app.provider;

import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.repository.YoutubeAudioRepository;
import com.msservices.app.repository.YoutubeDataApiSearchRepository;
import com.msservices.app.service.CookieContentValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class YouTubeAudioServerProvider implements AudioServerProvider {

    public static final String ID = "youtube";

    private static final Logger log = LoggerFactory.getLogger(YouTubeAudioServerProvider.class);

    private final YoutubeAudioRepository youtubeAudioRepository;
    private final YoutubeDataApiSearchRepository dataApiSearchRepository;

    public YouTubeAudioServerProvider(YoutubeAudioRepository youtubeAudioRepository,
                                      YoutubeDataApiSearchRepository dataApiSearchRepository) {
        this.youtubeAudioRepository = youtubeAudioRepository;
        this.dataApiSearchRepository = dataApiSearchRepository;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public boolean supportsUserCookies() {
        return true;
    }

    @Override
    public List<AudioSearchResultDto> search(String videoName, String rawCookies) {
        if (dataApiSearchRepository.isAvailable()) {
            try {
                return dataApiSearchRepository.searchVideos(videoName);
            } catch (InvalidVideoSearchException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("YouTube Data API search failed, falling back to yt-dlp: {}",
                        exception.getMessage());
            }
        }
        return searchWithYtDlp(videoName, rawCookies);
    }

    @Override
    public ExtractedAudioDto extract(String videoName, String videoId, String rawCookies) {
        String cookies = CookieContentValidator.normalizeAndValidate(rawCookies);
        return cookies == null
                ? youtubeAudioRepository.extractAudioByVideoName(videoName, videoId)
                : youtubeAudioRepository.extractAudioByVideoName(videoName, videoId, cookies);
    }

    // Cookies are only validated on the path that actually uses them. If the Data API resolves the
    // search, a malformed cookie payload is irrelevant and must not break the request.
    private List<AudioSearchResultDto> searchWithYtDlp(String videoName, String rawCookies) {
        String cookies = CookieContentValidator.normalizeAndValidate(rawCookies);
        return cookies == null
                ? youtubeAudioRepository.searchVideos(videoName)
                : youtubeAudioRepository.searchVideos(videoName, cookies);
    }
}
