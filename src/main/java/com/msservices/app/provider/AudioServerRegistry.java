package com.msservices.app.provider;

import com.msservices.app.dto.AudioServerInfo;
import com.msservices.app.exception.AudioServerUnavailableException;
import com.msservices.app.exception.UnsupportedAudioServerException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

// Keeps the list of available backends and decides which one handles a request. The ids are stable
// API values: clients may send "serverId" and the default stays YouTube when the field is absent.
@Component
public class AudioServerRegistry {

    public static final String DEFAULT_SERVER_ID = YouTubeAudioServerProvider.ID;

    private final Map<String, AudioServerProvider> providers;

    public AudioServerRegistry(List<AudioServerProvider> providers) {
        Map<String, AudioServerProvider> indexed = new LinkedHashMap<>();
        for (AudioServerProvider provider : providers) {
            indexed.put(provider.id().toLowerCase(Locale.ROOT), provider);
        }
        this.providers = Collections.unmodifiableMap(indexed);
    }

    public AudioServerProvider resolve(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return defaultProvider();
        }

        AudioServerProvider provider = providers.get(serverId.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new UnsupportedAudioServerException(
                    "The selected audio server is not available. Available servers: "
                            + String.join(", ", providers.keySet()) + ".");
        }
        return provider;
    }

    public AudioServerProvider defaultProvider() {
        AudioServerProvider youtube = providers.get(DEFAULT_SERVER_ID);
        if (youtube != null) {
            return youtube;
        }
        return providers.values().stream()
                .findFirst()
                .orElseThrow(() -> new AudioServerUnavailableException(
                        "No audio server is configured on the server."));
    }

    public List<AudioServerInfo> availableServers() {
        AudioServerProvider defaultProvider = defaultProvider();
        List<AudioServerInfo> servers = new ArrayList<>();
        for (AudioServerProvider provider : providers.values()) {
            servers.add(new AudioServerInfo(
                    provider.id(),
                    provider.displayName(),
                    provider.supportsUserCookies(),
                    provider.id().equals(defaultProvider.id())));
        }
        return servers;
    }
}
