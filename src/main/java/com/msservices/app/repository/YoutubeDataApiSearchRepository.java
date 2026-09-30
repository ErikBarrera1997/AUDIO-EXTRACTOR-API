package com.msservices.app.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msservices.app.config.YoutubeDataApiProperties;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.exception.AudioExtractionException;
import com.msservices.app.exception.InvalidVideoSearchException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class YoutubeDataApiSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(YoutubeDataApiSearchRepository.class);
    private static final String SEARCH_ENDPOINT = "https://www.googleapis.com/youtube/v3/search";
    private static final String VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final YoutubeDataApiProperties properties;
    private final HttpClient httpClient;
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();

    public YoutubeDataApiSearchRepository(YoutubeDataApiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    public List<AudioSearchResultDto> searchVideos(String videoName) {
        String cacheKey = videoName.toLowerCase(Locale.ROOT);
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.results();
        }

        JsonNode searchResponse = call(SEARCH_ENDPOINT, Map.of(
                "part", "snippet",
                "type", "video",
                "q", videoName,
                "maxResults", String.valueOf(Math.min(properties.getMaxResults(), 50)),
                "key", properties.getApiKey()
        ));

        List<AudioSearchResultDto> results = collectResults(videoName, searchResponse);
        searchCache.put(cacheKey, new CacheEntry(results, Instant.now().plus(properties.getCacheTtl())));
        return results;
    }

    private List<AudioSearchResultDto> collectResults(String videoName, JsonNode searchResponse) {
        List<String> videoIds = new ArrayList<>();
        for (JsonNode item : searchResponse.path("items")) {
            String videoId = item.path("id").path("videoId").asText(null);
            if (videoId != null && !videoId.isBlank()) {
                videoIds.add(videoId);
            }
        }

        if (videoIds.isEmpty()) {
            throw new InvalidVideoSearchException("We could not find videos with that name. Try a more specific search.");
        }

        Map<String, JsonNode> details = fetchVideoDetails(videoIds);

        List<AudioSearchResultDto> results = new ArrayList<>();
        for (JsonNode item : searchResponse.path("items")) {
            String videoId = item.path("id").path("videoId").asText(null);
            if (videoId == null || videoId.isBlank()) {
                continue;
            }

            JsonNode snippet = item.path("snippet");
            String title = snippet.path("title").asText(null);
            if (title == null || title.isBlank()) {
                continue;
            }

            String author = snippet.path("channelTitle").asText("");
            if (author.isBlank()) {
                author = "Desconocido";
            }

            JsonNode detailsNode = details.get(videoId);
            Long durationSeconds = null;
            Long viewCount = null;

            if (detailsNode != null) {
                JsonNode lengthSeconds = detailsNode.path("contentDetails").path("durationSeconds");
                if (lengthSeconds.isNumber()) {
                    durationSeconds = lengthSeconds.asLong();
                }
                if (detailsNode.path("statistics").path("viewCount").isNumber()) {
                    viewCount = detailsNode.path("statistics").path("viewCount").asLong();
                }
            }

            if (durationSeconds != null && durationSeconds > properties.getMaxDurationSeconds()) {
                continue;
            }

            results.add(new AudioSearchResultDto(videoId, title, author, durationSeconds, viewCount));
        }

        if (results.isEmpty()) {
            throw new InvalidVideoSearchException("We could not find videos with that name. Try a more specific search.");
        }

        return results;
    }

    private Map<String, JsonNode> fetchVideoDetails(List<String> videoIds) {
        String joinedIds = String.join(",", videoIds);
        JsonNode response = call(VIDEOS_ENDPOINT, Map.of(
                "part", "contentDetails,statistics",
                "id", joinedIds,
                "key", properties.getApiKey()
        ));

        Map<String, JsonNode> details = new ConcurrentHashMap<>();
        for (JsonNode item : response.path("items")) {
            String videoId = item.path("id").asText(null);
            if (videoId != null) {
                details.put(videoId, item);
            }
        }
        return details;
    }

    private JsonNode call(String endpoint, Map<String, String> queryParams) {
        String query = queryParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "?" + query))
                .timeout(properties.getTimeout())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                log.warn("YouTube Data API rejected the search request with status {}", response.statusCode());
                throw new AudioExtractionException("The YouTube search quota is exhausted. Try again later.");
            }
            if (response.statusCode() != 200) {
                log.warn("YouTube Data API returned status {} for search", response.statusCode());
                throw new AudioExtractionException("We could not perform the search right now. Try again.");
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (IOException exception) {
            throw new AudioExtractionException("We could not reach the YouTube search service. Try again.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AudioExtractionException("The search was interrupted. Try again.", exception);
        }
    }

    private record CacheEntry(List<AudioSearchResultDto> results, Instant expiresAt) {
    }
}
