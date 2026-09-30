package com.msservices.app.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.config.YtdlpProperties;
import com.msservices.app.exception.YoutubeToolUnavailableException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YtDlpCookiesSecurityTest {

    @Test
    @DisplayName("Fails closed when the cookies file is missing and cookies are required")
    void failsClosedWhenCookiesMissing(@TempDir Path tempDir) {
        YtdlpProperties properties = baseProperties();
        properties.setCookiesPath(tempDir.resolve("absent.txt").toString());
        properties.setCookiesRequired(true);

        YtDlpYoutubeAudioRepository repository = new YtDlpYoutubeAudioRepository(properties);

        YoutubeToolUnavailableException exception = assertThrows(YoutubeToolUnavailableException.class,
                () -> repository.searchVideos("valid query"));
        assertFalse(exception.getMessage().contains(tempDir.toString()));
    }

    @Test
    @DisplayName("Fails closed when the cookies path is not configured at all")
    void failsClosedWhenCookiesPathUnset(@TempDir Path tempDir) {
        YtdlpProperties properties = baseProperties();
        properties.setCookiesPath("");
        properties.setCookiesRequired(true);

        YtDlpYoutubeAudioRepository repository = new YtDlpYoutubeAudioRepository(properties);

        YoutubeToolUnavailableException exception = assertThrows(YoutubeToolUnavailableException.class,
                () -> repository.searchVideos("valid query"));
        assertTrue(exception.getMessage().contains("YTDLP_COOKIES_PATH"));
    }

    @Test
    @DisplayName("Does not leak the cookies path in the thrown message")
    void doesNotLeakCookiesPath(@TempDir Path tempDir) {
        Path secretPath = tempDir.resolve("cookies.txt");
        YtdlpProperties properties = baseProperties();
        properties.setCookiesPath(secretPath.toString());
        properties.setCookiesRequired(true);

        YtDlpYoutubeAudioRepository repository = new YtDlpYoutubeAudioRepository(properties);

        YoutubeToolUnavailableException exception = assertThrows(YoutubeToolUnavailableException.class,
                () -> repository.extractAudioByVideoName("valid name", null));
        assertFalse(exception.getMessage().contains("cookies.txt"));
        assertFalse(exception.getMessage().contains(tempDir.toString()));
    }

    @Test
    @DisplayName("A directory in place of the cookies file is rejected as unreadable")
    void rejectsDirectoryAsCookiesFile(@TempDir Path tempDir) {
        YtdlpProperties properties = baseProperties();
        properties.setCookiesPath(tempDir.toString());
        properties.setCookiesRequired(true);

        YtDlpYoutubeAudioRepository repository = new YtDlpYoutubeAudioRepository(properties);

        assertThrows(YoutubeToolUnavailableException.class, () -> repository.searchVideos("valid query"));
    }

    @Test
    @DisplayName("A readable cookies file never leaks its contents in the raised error")
    void readableCookiesFileIsUsedWithoutLeaking(@TempDir Path tempDir) throws Exception {
        Path cookies = tempDir.resolve("cookies.txt");
        Files.writeString(cookies, "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsupersecretvalue\n");

        YtdlpProperties properties = baseProperties();
        properties.setCookiesPath(cookies.toString());
        properties.setCookiesRequired(true);

        YtDlpYoutubeAudioRepository repository = new YtDlpYoutubeAudioRepository(properties);

        try {
            repository.searchVideos("valid query");
        } catch (RuntimeException exception) {
            assertFalse(exception.getMessage().contains("supersecretvalue"),
                    "error message must not contain the cookie value");
            assertFalse(exception.getMessage().contains("SID"),
                    "error message must not contain the cookie name");
            assertFalse(exception.getMessage().contains(cookies.toString()),
                    "error message must not contain the cookies path");
        }
    }

    private YtdlpProperties baseProperties() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setSearchLimit(15);
        properties.setSearchTimeoutSeconds(30);
        properties.setExtractionTimeoutSeconds(30);
        properties.setAttemptTimeoutSeconds(5);
        properties.setMaxDurationSeconds(900L);
        properties.setPlayerClients(java.util.List.of("tv_embedded"));
        properties.setPotEnabled(false);
        return properties;
    }
}
