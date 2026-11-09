package com.msservices.app.provider;

import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.repository.YoutubeAudioRepository;
import com.msservices.app.repository.YoutubeDataApiSearchRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Server provider for running on a local network.
// In a local network environment, YouTube does not block requests by data-centre IP reputation,
// so cookies are not required. It uses the same yt-dlp service and repository as this project.
@Component
public class LocalAudioServerProvider implements AudioServerProvider {

    public static final String ID = "local";

    private static final Logger log = LoggerFactory.getLogger(LocalAudioServerProvider.class);

    private final YoutubeAudioRepository youtubeAudioRepository;
    private final YoutubeDataApiSearchRepository dataApiSearchRepository;

    public LocalAudioServerProvider(YoutubeAudioRepository youtubeAudioRepository,
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
        return "Red Local";
    }

    @Override
    public boolean supportsUserCookies() {
        return false;
    }

    @Override
    public List<String> aliases() {
        return List.of("local_network", "local-network", "local_api", "local-api", "red_local", "red-local");
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
        return youtubeAudioRepository.searchVideos(videoName);
    }

    @Override
    public ExtractedAudioDto extract(String videoName, String videoId, String rawCookies) {
        return youtubeAudioRepository.extractAudioByVideoName(videoName, videoId);
    }
}
