package com.msservices.app.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.AudioServerInfo;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.AudioServerUnavailableException;
import com.msservices.app.exception.UnsupportedAudioServerException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AudioServerRegistryTest {

    @Test
    @DisplayName("An absent server id resolves to YouTube")
    void absentServerIdUsesYouTube() {
        AudioServerProvider youtube = provider("youtube", "YouTube", true);
        AudioServerRegistry registry = new AudioServerRegistry(List.of(youtube));

        assertSame(youtube, registry.resolve(null));
        assertSame(youtube, registry.resolve("   "));
        assertSame(youtube, registry.defaultProvider());
    }

    @Test
    @DisplayName("Server ids are matched case-insensitively and trimmed")
    void serverIdIsNormalized() {
        AudioServerProvider youtube = provider("youtube", "YouTube", true);
        AudioServerRegistry registry = new AudioServerRegistry(List.of(youtube));

        assertSame(youtube, registry.resolve("  YOUtube  "));
    }

    @Test
    @DisplayName("An unknown server id fails with a client error")
    void unknownServerIsRejected() {
        AudioServerRegistry registry = new AudioServerRegistry(
                List.of(provider("youtube", "YouTube", true)));

        assertThrows(UnsupportedAudioServerException.class, () -> registry.resolve("other"));
    }

    @Test
    @DisplayName("The available servers list marks the default and its capabilities")
    void listsServers() {
        AudioServerRegistry registry = new AudioServerRegistry(List.of(
                provider("youtube", "YouTube", true),
                provider("archive", "Archive", false)));

        List<AudioServerInfo> servers = registry.availableServers();

        assertEquals(2, servers.size());
        assertEquals("youtube", servers.get(0).getId());
        assertTrue(servers.get(0).isDefaultServer());
        assertTrue(servers.get(0).isSupportsUserCookies());
        assertEquals("archive", servers.get(1).getId());
        assertFalse(servers.get(1).isDefaultServer());
    }

    @Test
    @DisplayName("An empty registry fails closed")
    void emptyRegistryFailsClosed() {
        AudioServerRegistry registry = new AudioServerRegistry(List.of());

        assertThrows(AudioServerUnavailableException.class, registry::defaultProvider);
    }

    private AudioServerProvider provider(String id, String displayName, boolean supportsUserCookies) {
        return new AudioServerProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public boolean supportsUserCookies() {
                return supportsUserCookies;
            }

            @Override
            public List<AudioSearchResultDto> search(String videoName, String rawCookies) {
                return List.of();
            }

            @Override
            public ExtractedAudioDto extract(String videoName, String videoId, String rawCookies) {
                return null;
            }
        };
    }
}
