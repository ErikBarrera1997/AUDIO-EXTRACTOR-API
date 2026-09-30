package com.msservices.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.msservices.app.dto.AudioExtractionRequest;
import com.msservices.app.dto.AudioExtractionResponse;
import com.msservices.app.dto.AudioSearchResponse;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.repository.YoutubeAudioRepository;
import com.msservices.app.repository.YoutubeDataApiSearchRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AudioExtractionServiceSecurityTest {

    private YoutubeAudioRepository youtubeAudioRepository;
    private YoutubeDataApiSearchRepository dataApiSearchRepository;
    private AudioExtractionService service;

    @BeforeEach
    void setUp() {
        youtubeAudioRepository = Mockito.mock(YoutubeAudioRepository.class);
        dataApiSearchRepository = Mockito.mock(YoutubeDataApiSearchRepository.class);
        service = new AudioExtractionService(youtubeAudioRepository, dataApiSearchRepository);
    }

    @Test
    @DisplayName("Rejects a null request without touching the repository")
    void rejectsNullRequest() {
        assertThrows(InvalidVideoSearchException.class, () -> service.extractAudio(null));
        verifyNoInteractions(youtubeAudioRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Rejects blank video names")
    void rejectsBlankVideoNames(String videoName) {
        AudioExtractionRequest request = new AudioExtractionRequest();
        request.setVideoName(videoName);

        assertThrows(InvalidVideoSearchException.class, () -> service.extractAudio(request));
        verifyNoInteractions(youtubeAudioRepository);
    }

    @Test
    @DisplayName("Rejects a video name below the minimum length")
    void rejectsTooShortVideoName() {
        AudioExtractionRequest request = new AudioExtractionRequest();
        request.setVideoName("ab");

        assertThrows(InvalidVideoSearchException.class, () -> service.extractAudio(request));
        verifyNoInteractions(youtubeAudioRepository);
    }

    @Test
    @DisplayName("Rejects a video name above the maximum length")
    void rejectsTooLongVideoName() {
        AudioExtractionRequest request = new AudioExtractionRequest();
        request.setVideoName("a".repeat(151));

        assertThrows(InvalidVideoSearchException.class, () -> service.extractAudio(request));
        verifyNoInteractions(youtubeAudioRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/video",
            "video/../../etc/passwd",
            "abc;rm -rf /",
            "abc|cat /etc/passwd",
            "abc&&curl attacker.example",
            "abc$(whoami)",
            "abc`id`",
            "abc>out.txt",
            "../../../etc/shadow"
    })
    @DisplayName("Rejects video ids with path traversal, shell or URL injection payloads")
    void rejectsMaliciousVideoIds(String videoId) {
        AudioExtractionRequest request = new AudioExtractionRequest();
        request.setVideoName("valid video name");
        request.setVideoId(videoId);

        assertThrows(InvalidVideoSearchException.class, () -> service.extractAudio(request));
        verifyNoInteractions(youtubeAudioRepository);
    }

    @Test
    @DisplayName("Rejects an oversized video id before any extraction")
    void rejectsOversizedVideoId() {
        AudioExtractionRequest request = new AudioExtractionRequest();
        request.setVideoName("valid video name");
        request.setVideoId("a".repeat(64));

        assertThrows(InvalidVideoSearchException.class, () -> service.extractAudio(request));
        verifyNoInteractions(youtubeAudioRepository);
    }

    @Test
    @DisplayName("Accepts a well formed video id and forwards it untouched")
    void acceptsValidVideoId() {
        when(youtubeAudioRepository.extractAudioByVideoName(anyString(), any()))
                .thenReturn(new ExtractedAudioDto("title", "audio.mp3", "audio/mpeg", "AAA="));

        AudioExtractionResponse response = service.extractAudio(requestWithId("  dQw4w9WgXcQ  "));

        assertTrue(response.isSuccess());
        verify(youtubeAudioRepository).extractAudioByVideoName("valid video name", "dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("Passes a null video id so the repository falls back to search")
    void passesNullVideoIdWhenAbsent() {
        when(youtubeAudioRepository.extractAudioByVideoName(anyString(), any()))
                .thenReturn(new ExtractedAudioDto("title", "audio.mp3", "audio/mpeg", "AAA="));

        service.extractAudio(requestWithId(null));

        verify(youtubeAudioRepository).extractAudioByVideoName("valid video name", null);
    }

    @Test
    @DisplayName("Search uses the Data API when available and never calls yt-dlp")
    void searchPrefersDataApi() {
        when(dataApiSearchRepository.isAvailable()).thenReturn(true);
        when(dataApiSearchRepository.searchVideos(anyString()))
                .thenReturn(List.of(new AudioSearchResultDto("id1", "title", "author", 100L, 10L)));

        AudioSearchResponse response = service.searchVideos("  never gonna give you up  ");

        assertTrue(response.isSuccess());
        assertEquals(1, response.getResults().size());
        verify(dataApiSearchRepository).searchVideos("never gonna give you up");
        verify(youtubeAudioRepository, never()).searchVideos(anyString());
    }

    @Test
    @DisplayName("Search falls back to yt-dlp when the Data API key is absent")
    void searchFallsBackWhenDataApiUnavailable() {
        when(dataApiSearchRepository.isAvailable()).thenReturn(false);
        when(youtubeAudioRepository.searchVideos(anyString()))
                .thenReturn(List.of(new AudioSearchResultDto("id1", "title", "author", 100L, 10L)));

        AudioSearchResponse response = service.searchVideos("never gonna give you up");

        assertTrue(response.isSuccess());
        assertFalse(response.getResults().isEmpty());
        verify(youtubeAudioRepository).searchVideos("never gonna give you up");
    }

    @Test
    @DisplayName("Search falls back to yt-dlp when the Data API fails at runtime")
    void searchFallsBackOnDataApiError() {
        when(dataApiSearchRepository.isAvailable()).thenReturn(true);
        when(dataApiSearchRepository.searchVideos(anyString()))
                .thenThrow(new RuntimeException("quota exhausted"));
        when(youtubeAudioRepository.searchVideos(anyString()))
                .thenReturn(List.of(new AudioSearchResultDto("id1", "title", "author", 100L, 10L)));

        AudioSearchResponse response = service.searchVideos("never gonna give you up");

        assertTrue(response.isSuccess());
        verify(youtubeAudioRepository).searchVideos("never gonna give you up");
    }

    @Test
    @DisplayName("A no-results response from the Data API is not retried against yt-dlp")
    void doesNotFallbackOnEmptySearch() {
        when(dataApiSearchRepository.isAvailable()).thenReturn(true);
        when(dataApiSearchRepository.searchVideos(anyString()))
                .thenThrow(new InvalidVideoSearchException("We could not find videos with that name."));

        assertThrows(InvalidVideoSearchException.class, () -> service.searchVideos("obscure query"));
        verify(youtubeAudioRepository, never()).searchVideos(anyString());
    }

    @Test
    @DisplayName("Search query is trimmed before it reaches any backend")
    void trimsSearchQuery() {
        when(dataApiSearchRepository.isAvailable()).thenReturn(true);
        when(dataApiSearchRepository.searchVideos(anyString()))
                .thenReturn(List.of(new AudioSearchResultDto("id1", "title", "author", 100L, 10L)));

        service.searchVideos("   spaced query   ");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(dataApiSearchRepository).searchVideos(captor.capture());
        assertEquals("spaced query", captor.getValue());
    }

    @Test
    @DisplayName("Rejects an invalid search query before calling any backend")
    void rejectsInvalidSearchQuery() {
        assertThrows(InvalidVideoSearchException.class, () -> service.searchVideos("a"));
        assertThrows(InvalidVideoSearchException.class, () -> service.searchVideos("x".repeat(200)));
        verifyNoInteractions(dataApiSearchRepository);
        verifyNoInteractions(youtubeAudioRepository);
    }

    @Test
    @DisplayName("Does not leak internal error details in the success response")
    void doesNotLeakInternalsInResponse() {
        when(youtubeAudioRepository.extractAudioByVideoName(anyString(), any()))
                .thenReturn(new ExtractedAudioDto("Safe Title", "audio.mp3", "audio/mpeg", "AAA="));

        AudioExtractionResponse response = service.extractAudio(requestWithId("dQw4w9WgXcQ"));

        assertEquals("Audio extraido correctamente.", response.getMessage());
        assertFalse(response.getMessage().contains("Exception"));
        assertFalse(response.getMessage().contains("/app/"));
    }

    private AudioExtractionRequest requestWithId(String videoId) {
        AudioExtractionRequest request = new AudioExtractionRequest();
        request.setVideoName("  valid video name  ");
        request.setVideoId(videoId);
        return request;
    }
}
