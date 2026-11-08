package com.msservices.app.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.config.YtdlpProperties;
import com.msservices.app.exception.InvalidCookieException;
import com.msservices.app.exception.YoutubeToolUnavailableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YtDlpUserCookiesTest {

    private static final String USER_COOKIES =
            "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tuser-secret\n";
    private static final String STATIC_COOKIES =
            "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tstatic-secret\n";

    @Test
    @DisplayName("The user cookie file reaches yt-dlp as --cookies")
    void userCookiesReachTheCommand(@TempDir Path tempDir) {
        Path userCookies = tempDir.resolve("user-cookies.txt");
        List<String> args = repository(properties(tempDir.resolve("absent.txt")))
                .buildCommonArgs("tv_embedded", userCookies);

        int index = args.indexOf("--cookies");
        assertTrue(index >= 0, "argv must carry --cookies");
        assertEquals(userCookies.toString(), args.get(index + 1));
    }

    @Test
    @DisplayName("The extraction command also carries the per-request cookies")
    void extractionCommandCarriesUserCookies(@TempDir Path tempDir) {
        Path userCookies = tempDir.resolve("user-cookies.txt");
        List<String> command = repository(properties(tempDir.resolve("absent.txt")))
                .buildExtractionCommand("ignored", "dQw4w9WgXcQ", tempDir, "ios", userCookies);

        int index = command.indexOf("--cookies");
        assertTrue(index >= 0, "argv must carry --cookies");
        assertEquals(userCookies.toString(), command.get(index + 1));
    }

    @Test
    @DisplayName("User cookies are used first and the static cookie remains the fallback")
    void userCookiesTakePrecedenceAndStaticRemainsFallback(@TempDir Path tempDir) throws Exception {
        Path staticCookies = tempDir.resolve("static.txt");
        Files.writeString(staticCookies, STATIC_COOKIES);

        List<Path> candidates = repository(properties(staticCookies))
                .resolveCookieCandidates(USER_COOKIES, tempDir);

        assertEquals(2, candidates.size());
        assertEquals(USER_COOKIES, Files.readString(candidates.get(0)));
        assertEquals(STATIC_COOKIES, Files.readString(candidates.get(1)));
    }

    @Test
    @DisplayName("User cookies work when the static file is absent and cookies are required")
    void userCookiesDoNotRequireTheStaticFile(@TempDir Path tempDir) throws Exception {
        List<Path> candidates = repository(properties(tempDir.resolve("absent.txt")))
                .resolveCookieCandidates(USER_COOKIES, tempDir);

        assertEquals(1, candidates.size());
        assertEquals(USER_COOKIES, Files.readString(candidates.get(0)));
    }

    @Test
    @DisplayName("Without user cookies the static file is the only candidate")
    void staticCookiesRemainTheDefault(@TempDir Path tempDir) throws Exception {
        Path staticCookies = tempDir.resolve("static.txt");
        Files.writeString(staticCookies, STATIC_COOKIES);

        List<Path> candidates = repository(properties(staticCookies))
                .resolveCookieCandidates(null, tempDir);

        assertEquals(1, candidates.size());
        assertEquals(STATIC_COOKIES, Files.readString(candidates.get(0)));
    }

    @Test
    @DisplayName("Without any cookies the request can still run when cookies are optional")
    void noCookiesOptional(@TempDir Path tempDir) throws Exception {
        YtdlpProperties properties = properties(tempDir.resolve("absent.txt"));
        properties.setCookiesRequired(false);

        assertTrue(repository(properties).resolveCookieCandidates(null, tempDir).isEmpty());
    }

    @Test
    @DisplayName("Without any cookies the request fails closed when cookies are required")
    void noCookiesRequiredFailsClosed(@TempDir Path tempDir) {
        YtdlpProperties properties = properties(tempDir.resolve("absent.txt"));

        assertThrows(YoutubeToolUnavailableException.class,
                () -> repository(properties).resolveCookieCandidates(null, tempDir));
    }

    @Test
    @DisplayName("Per-request cookies are rejected when the feature flag is off")
    void userCookiesCanBeDisabled(@TempDir Path tempDir) {
        YtdlpProperties properties = properties(tempDir.resolve("absent.txt"));
        properties.setUserCookiesEnabled(false);

        assertThrows(InvalidCookieException.class,
                () -> repository(properties).resolveCookieCandidates(USER_COOKIES, tempDir));
    }

    private YtDlpYoutubeAudioRepository repository(YtdlpProperties properties) {
        return new YtDlpYoutubeAudioRepository(properties);
    }

    private YtdlpProperties properties(Path staticCookies) {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setCookiesPath(staticCookies.toString());
        properties.setCookiesRequired(true);
        properties.setUserCookiesEnabled(true);
        properties.setPlayerClients(List.of("tv_embedded"));
        return properties;
    }
}
