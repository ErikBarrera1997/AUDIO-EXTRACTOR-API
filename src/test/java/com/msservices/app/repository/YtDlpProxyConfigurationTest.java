package com.msservices.app.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.config.YtdlpProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YtDlpProxyConfigurationTest {

    private static final String PROXY_WITH_CREDENTIALS = "http://provider-user:sup3rs3cret@us-west.proxy.test:8000";

    @Nested
    @DisplayName("The proxy only reaches yt-dlp when one is configured")
    class CommandArguments {

        @Test
        @DisplayName("No --proxy flag is passed when YTDLP_PROXY is empty")
        void omitsProxyWhenUnset() {
            List<String> args = repositoryWith("").buildCommonArgs("tv_embedded");

            assertFalse(args.contains("--proxy"), "argv must not mention the proxy at all");
        }

        @Test
        @DisplayName("No --proxy flag is passed when YTDLP_PROXY is only whitespace")
        void omitsProxyWhenBlank() {
            List<String> args = repositoryWith("   ").buildCommonArgs("tv_embedded");

            assertFalse(args.contains("--proxy"), "a blank value is not a proxy");
        }

        @Test
        @DisplayName("A configured proxy is passed verbatim as --proxy")
        void passesProxyVerbatim() {
            List<String> args = repositoryWith(PROXY_WITH_CREDENTIALS).buildCommonArgs("tv_embedded");

            assertEquals("--proxy", args.get(args.size() - 2));
            assertEquals(PROXY_WITH_CREDENTIALS, args.get(args.size() - 1),
                    "the value must reach yt-dlp exactly as configured");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://proxy.test:8000",
                "https://proxy.test:8443",
                "socks5://proxy.test:1080",
                "socks5h://proxy.test:1080",
                "socks4://proxy.test:1080"
        })
        @DisplayName("Every supported scheme is forwarded untouched")
        void passesEverySupportedScheme(String proxy) {
            List<String> args = repositoryWith(proxy).buildCommonArgs("tv_embedded");

            assertEquals(proxy, args.get(args.size() - 1));
        }

        @Test
        @DisplayName("The extraction command carries the proxy alongside the watch URL")
        void extractionCommandCarriesProxy() {
            List<String> command = repositoryWith(PROXY_WITH_CREDENTIALS)
                    .buildExtractionCommand("ignored", "dQw4w9WgXcQ", Path.of("/tmp/work"), "ios");

            assertEquals("yt-dlp", command.get(0));
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", command.get(1));
            assertEquals(PROXY_WITH_CREDENTIALS, command.get(command.size() - 1));
        }

        @Test
        @DisplayName("The search argument set also carries the proxy")
        void searchArgumentsCarryProxy() {
            List<String> args = repositoryWith(PROXY_WITH_CREDENTIALS).buildCommonArgs("web_safari");

            assertTrue(args.contains("--proxy"));
            assertEquals(PROXY_WITH_CREDENTIALS, args.get(args.size() - 1));
        }

        @Test
        @DisplayName("Surrounding whitespace in the variable does not reach yt-dlp")
        void trimsConfiguredProxy() {
            YtdlpProperties properties = properties();
            properties.setProxy("  " + PROXY_WITH_CREDENTIALS + "  ");

            assertEquals(PROXY_WITH_CREDENTIALS, properties.getProxy());
        }
    }

    @Nested
    @DisplayName("A malformed proxy fails the deploy instead of every request")
    class Validation {

        @ParameterizedTest
        @ValueSource(strings = {
                "ftp://proxy.test:8000",
                "host:8000",
                "proxy.test:8000",
                "just-a-hostname",
                "http://",
                "https://",
                "socks9://proxy.test:1080"
        })
        @DisplayName("An unusable scheme or a missing host is rejected")
        void rejectsUnusableValues(String proxy) {
            YtdlpProperties properties = properties();

            assertThrows(IllegalArgumentException.class, () -> properties.setProxy(proxy));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ftp://user:sup3rs3cret@proxy.test:8000", "not a url at all"})
        @DisplayName("The rejection never echoes the submitted value")
        void neverEchoesTheSubmittedValue(String proxy) {
            YtdlpProperties properties = properties();
            String passwordFragment = "sup3rs3cret";

            try {
                properties.setProxy(proxy);
            } catch (IllegalArgumentException exception) {
                assertFalse(exception.getMessage().contains(passwordFragment),
                        "credentials must not reach the exception message");
                assertFalse(exception.getMessage().contains("user:"),
                        "the userinfo must not reach the exception message");
                assertTrue(exception.getMessage().contains("YTDLP_PROXY"),
                        "the message must name the variable to fix");
                assertTrue(exception.getMessage().contains("socks5"),
                        "the message must list the accepted schemes");
            }
        }

        @Test
        @DisplayName("An empty value stays valid so the no-proxy default survives")
        void acceptsEmptyValue() {
            YtdlpProperties properties = properties();

            properties.setProxy("");
            assertEquals("", properties.getProxy());

            properties.setProxy(null);
            assertEquals("", properties.getProxy());
        }

        @Test
        @DisplayName("The startup check leaves an already valid proxy alone")
        void startupCheckAcceptsValidValue() {
            YtdlpProperties properties = properties();
            properties.setProxy(PROXY_WITH_CREDENTIALS);

            assertDoesNotThrow(properties::afterPropertiesSet);
            assertEquals(PROXY_WITH_CREDENTIALS, properties.getProxy());
        }

        @Test
        @DisplayName("The startup check accepts the no-proxy default")
        void startupCheckAcceptsEmptyDefault() {
            YtdlpProperties properties = properties();

            assertDoesNotThrow(properties::afterPropertiesSet);
            assertEquals("", properties.getProxy());
        }
    }

    @Nested
    @DisplayName("Credentials are masked in anything meant for a human")
    class Masking {

        @Test
        @DisplayName("The userinfo is replaced by a marker, keeping scheme and port")
        void masksUserAndPassword() {
            YtdlpProperties properties = properties();
            properties.setProxy(PROXY_WITH_CREDENTIALS);

            String masked = properties.getMaskedProxy();

            assertEquals("http://***@us-west.proxy.test:8000", masked);
            assertFalse(masked.contains("sup3rs3cret"), "the password must not survive masking");
            assertFalse(masked.contains("provider-user"), "the username must not survive masking");
        }

        @Test
        @DisplayName("A credential-less proxy is left readable")
        void leavesCredentialFreeProxyIntact() {
            YtdlpProperties properties = properties();
            properties.setProxy("socks5://proxy.test:1080");

            assertEquals("socks5://proxy.test:1080", properties.getMaskedProxy());
        }

        @Test
        @DisplayName("An absent proxy masks to an empty string")
        void masksNothingWhenUnset() {
            assertEquals("", properties().getMaskedProxy());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://user:sup3rs3cret@proxy.test:8000",
                "socks5://user:sup3rs3cret@proxy.test:1080",
                "http://sup3rs3cret@proxy.test:8000"
        })
        @DisplayName("No password survives masking in any supported shape")
        void neverLeaksPassword(String proxy) {
            YtdlpProperties properties = properties();
            properties.setProxy(proxy);

            assertFalse(properties.getMaskedProxy().contains("sup3rs3cret"));
        }
    }

    private static YtDlpYoutubeAudioRepository repositoryWith(String proxy) {
        YtdlpProperties properties = properties();
        properties.setProxy(proxy);
        return new YtDlpYoutubeAudioRepository(properties);
    }

    private static YtdlpProperties properties() {
        YtdlpProperties properties = new YtdlpProperties();
        properties.setPlayerClients(List.of("tv_embedded"));
        properties.setPotEnabled(false);
        // These tests only care about the proxy argument. Cookies are made optional so
        // buildCommonArgs does not fail closed on the /app/cookies.txt path that does not exist
        // outside the container.
        properties.setCookiesRequired(false);
        properties.setCookiesPath("");
        return properties;
    }
}