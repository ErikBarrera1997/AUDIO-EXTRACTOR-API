package com.msservices.app.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.config.YoutubeDataApiProperties;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.repository.YoutubeAudioRepository;
import com.msservices.app.repository.YoutubeDataApiSearchRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalAudioServerProviderTest {

    @Test
    @DisplayName("Metadata reflects a local server that does not require user cookies")
    void metadata() {
        LocalAudioServerProvider provider = new LocalAudioServerProvider(
                new FakeYoutubeAudioRepository(), new FakeDataApiSearchRepository(false));

        assertEquals("local", provider.id());
        assertEquals("Red Local", provider.displayName());
        assertFalse(provider.supportsUserCookies(), "local execution does not require cookies");
        assertTrue(provider.aliases().contains("local_network"));
        assertTrue(provider.aliases().contains("local-network"));
        assertTrue(provider.aliases().contains("red_local"));
    }

    @Test
    @DisplayName("Search prefers Data API when available and does not pass cookies")
    void searchPrefersDataApi() {
        FakeYoutubeAudioRepository ytRepo = new FakeYoutubeAudioRepository();
        FakeDataApiSearchRepository dataApiRepo = new FakeDataApiSearchRepository(true);
        LocalAudioServerProvider provider = new LocalAudioServerProvider(ytRepo, dataApiRepo);

        List<AudioSearchResultDto> results = provider.search("query", "dummy-cookies");

        assertEquals(1, results.size());
        assertTrue(dataApiRepo.searchCalled);
        assertFalse(ytRepo.searchCalled);
    }

    @Test
    @DisplayName("Search falls back to yt-dlp without cookies when Data API is not available")
    void searchFallsBackWithoutCookies() {
        FakeYoutubeAudioRepository ytRepo = new FakeYoutubeAudioRepository();
        FakeDataApiSearchRepository dataApiRepo = new FakeDataApiSearchRepository(false);
        LocalAudioServerProvider provider = new LocalAudioServerProvider(ytRepo, dataApiRepo);

        List<AudioSearchResultDto> results = provider.search("query", "dummy-cookies");

        assertEquals(1, results.size());
        assertTrue(ytRepo.searchCalled);
        assertFalse(dataApiRepo.searchCalled);
    }

    @Test
    @DisplayName("Extraction calls the repository without cookies, ignoring any rawCookies")
    void extractionDoesNotUseCookies() {
        FakeYoutubeAudioRepository ytRepo = new FakeYoutubeAudioRepository();
        FakeDataApiSearchRepository dataApiRepo = new FakeDataApiSearchRepository(false);
        LocalAudioServerProvider provider = new LocalAudioServerProvider(ytRepo, dataApiRepo);

        ExtractedAudioDto result = provider.extract("video", "videoId", "dummy-cookies");

        assertEquals("title", result.getVideoTitle());
        assertTrue(ytRepo.extractCalled);
    }

    static class FakeDataApiSearchRepository extends YoutubeDataApiSearchRepository {
        final boolean available;
        boolean searchCalled = false;
        final List<AudioSearchResultDto> results = List.of(
                new AudioSearchResultDto("id1", "title", "author", 100L, 10L));

        FakeDataApiSearchRepository(boolean available) {
            super(new YoutubeDataApiProperties());
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public List<AudioSearchResultDto> searchVideos(String videoName) {
            this.searchCalled = true;
            return results;
        }
    }

    static class FakeYoutubeAudioRepository implements YoutubeAudioRepository {
        boolean searchCalled = false;
        boolean extractCalled = false;
        final List<AudioSearchResultDto> results = List.of(
                new AudioSearchResultDto("id1", "title", "author", 100L, 10L));
        final ExtractedAudioDto extractResult = new ExtractedAudioDto(
                "title", "video.mp3", "audio/mpeg", "AAA=");

        @Override
        public List<AudioSearchResultDto> searchVideos(String videoName) {
            this.searchCalled = true;
            return results;
        }

        @Override
        public List<AudioSearchResultDto> searchVideos(String videoName, String cookies) {
            throw new AssertionError("Should not be called with cookies");
        }

        @Override
        public ExtractedAudioDto extractAudioByVideoName(String videoName, String videoId) {
            this.extractCalled = true;
            return extractResult;
        }

        @Override
        public ExtractedAudioDto extractAudioByVideoName(String videoName, String videoId, String cookies) {
            throw new AssertionError("Should not be called with cookies");
        }
    }
}
