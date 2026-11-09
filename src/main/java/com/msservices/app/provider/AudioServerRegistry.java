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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Keeps the list of available backends and decides which one handles a request. The ids are stable
// API values: clients may send "serverId" and the default stays YouTube when the field is absent.
@Component
public class AudioServerRegistry {

    public static final String DEFAULT_SERVER_ID = YouTubeAudioServerProvider.ID;

    private final List<AudioServerProvider> providerList;
    private final Map<String, AudioServerProvider> providers;
    private final String defaultServerId;

    @Autowired
    public AudioServerRegistry(List<AudioServerProvider> providers,
                               @Value("${audio.server.default:youtube}") String defaultServerId) {
        this.providerList = List.copyOf(providers);
        this.defaultServerId = (defaultServerId == null || defaultServerId.isBlank())
                ? DEFAULT_SERVER_ID
                : defaultServerId.trim().toLowerCase(Locale.ROOT);

        Map<String, AudioServerProvider> indexed = new LinkedHashMap<>();
        for (AudioServerProvider provider : providers) {
            indexed.put(provider.id().toLowerCase(Locale.ROOT), provider);
            for (String alias : provider.aliases()) {
                indexed.put(alias.toLowerCase(Locale.ROOT), provider);
            }
        }
        this.providers = Collections.unmodifiableMap(indexed);
    }

    public AudioServerRegistry(List<AudioServerProvider> providers) {
        this(providers, DEFAULT_SERVER_ID);
    }

    public AudioServerProvider resolve(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return defaultProvider();
        }

        AudioServerProvider provider = providers.get(serverId.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            List<String> availableIds = providerList.stream()
                    .map(AudioServerProvider::id)
                    .toList();
            throw new UnsupportedAudioServerException(
                    "The selected audio server is not available. Available servers: "
                            + String.join(", ", availableIds) + ".");
        }
        return provider;
    }

    public AudioServerProvider defaultProvider() {
        AudioServerProvider configured = providers.get(defaultServerId);
        if (configured != null) {
            return configured;
        }

        AudioServerProvider youtube = providers.get(DEFAULT_SERVER_ID);
        if (youtube != null) {
            return youtube;
        }

        return providerList.stream()
                .findFirst()
                .orElseThrow(() -> new AudioServerUnavailableException(
                        "No audio server is configured on the server."));
    }

    public List<AudioServerInfo> availableServers() {
        AudioServerProvider defaultProvider = defaultProvider();
        List<AudioServerInfo> servers = new ArrayList<>();
        for (AudioServerProvider provider : providerList) {
            servers.add(new AudioServerInfo(
                    provider.id(),
                    provider.displayName(),
                    provider.supportsUserCookies(),
                    provider.id().equals(defaultProvider.id())));
        }
        return servers;
    }
}
