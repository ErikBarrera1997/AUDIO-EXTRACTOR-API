package com.msservices.app.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.AudioExtractionException;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.exception.YoutubeToolUnavailableException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class YtDlpYoutubeAudioRepository implements YoutubeAudioRepository {

    private static final int SEARCH_LIMIT = 15;
    private static final long MAX_DURATION_SECONDS = 15 * 60;
    private static final Duration EXTRACTION_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration SEARCH_TIMEOUT = Duration.ofMinutes(1);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<AudioSearchResultDto> searchVideos(String videoName) {
        List<String> command = List.of(
                "yt-dlp",
                "--flat-playlist",
                "--match-filter",
                "duration<" + MAX_DURATION_SECONDS,
                "-J",
                "ytsearch" + SEARCH_LIMIT + ":" + videoName
        );

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start();

            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            CompletableFuture<String> errorFuture = CompletableFuture.supplyAsync(() -> readProcessError(process));
            boolean finished = process.waitFor(SEARCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new AudioExtractionException("The search is taking too long. Try again.");
            }

            String commandOutput = outputFuture.join();
            String commandError = errorFuture.join();

            if (process.exitValue() != 0) {
                throw new AudioExtractionException(resolveFriendlyError(commandError, "We could not perform the search. Try again."));
            }

            return rankBestResults(parseSearchResults(commandOutput));
        } catch (IOException exception) {
            throw new YoutubeToolUnavailableException("The extraction service is unavailable. Check that yt-dlp and ffmpeg are installed.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AudioExtractionException("The search was interrupted. Try again.", exception);
        }
    }

    @Override
    public ExtractedAudioDto extractAudioByVideoName(String videoName, String videoId) {
        Path workDirectory = createWorkDirectory();

        try {
            Process process = startExtractionProcess(videoName, videoId, workDirectory);
            CompletableFuture<String> commandOutputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            CompletableFuture<String> commandErrorFuture = CompletableFuture.supplyAsync(() -> readProcessError(process));
            boolean finished = process.waitFor(EXTRACTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new AudioExtractionException("The extraction is taking too long. Try another video or try again later.");
            }

            String commandOutput = commandOutputFuture.join();
            String commandError = commandErrorFuture.join();

            if (process.exitValue() != 0) {
                throw new AudioExtractionException(resolveFriendlyError(commandError, "We could not extract the audio from the selected video. Try another video."));
            }

            Path audioFile = findAudioFile(workDirectory);
            byte[] audioBytes = Files.readAllBytes(audioFile);
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

            return new ExtractedAudioDto(
                    resolveVideoTitle(commandOutput, videoName),
                    audioFile.getFileName().toString(),
                    resolveContentType(audioFile),
                    audioBase64
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AudioExtractionException("The extraction was interrupted. Try again.", exception);
        } catch (IOException exception) {
            throw new YoutubeToolUnavailableException("The extraction service is unavailable. Check that yt-dlp and ffmpeg are installed.", exception);
        } finally {
            deleteDirectory(workDirectory);
        }
    }

    private List<AudioSearchResultDto> parseSearchResults(String commandOutput) {
        try {
            List<AudioSearchResultDto> results = new ArrayList<>();
            JsonNode root = OBJECT_MAPPER.readTree(commandOutput);
            JsonNode entries = root.path("entries");

            for (JsonNode entry : entries) {
                String videoId = entry.path("id").asText(null);
                String title = entry.path("title").asText(null);
                if (videoId == null || videoId.isBlank() || title == null || title.isBlank()) {
                    continue;
                }

                String author = readAuthor(entry);
                Long duration = entry.path("duration").isNumber() ? entry.path("duration").asLong() : null;

                if (duration != null && duration > MAX_DURATION_SECONDS) {
                    continue;
                }

                Long viewCount = entry.path("view_count").isNumber() ? entry.path("view_count").asLong() : null;

                results.add(new AudioSearchResultDto(videoId, title, author, duration, viewCount));
            }

            if (results.isEmpty()) {
                throw new InvalidVideoSearchException("We could not find videos with that name. Try a more specific search.");
            }

            return results;
        } catch (IOException exception) {
            throw new AudioExtractionException("We could not interpret the search results. Try again.", exception);
        }
    }

    private String readAuthor(JsonNode entry) {
        String channel = entry.path("channel").asText("");
        if (!channel.isBlank()) {
            return channel;
        }
        String uploader = entry.path("uploader").asText("");
        if (!uploader.isBlank()) {
            return uploader;
        }
        return "Desconocido";
    }

    private List<AudioSearchResultDto> rankBestResults(List<AudioSearchResultDto> results) {
        Map<String, AudioSearchResultDto> bestByAuthor = new LinkedHashMap<>();

        for (AudioSearchResultDto result : results) {
            String authorKey = result.getAuthor().toLowerCase();
            AudioSearchResultDto existing = bestByAuthor.get(authorKey);
            if (existing == null || qualityScore(result) > qualityScore(existing)) {
                bestByAuthor.put(authorKey, result);
            }
        }

        return bestByAuthor.values().stream()
                .sorted(Comparator.comparingLong(this::qualityScore).reversed())
                .collect(Collectors.toList());
    }

    private long qualityScore(AudioSearchResultDto result) {
        long views = result.getViewCount() != null ? result.getViewCount() : 0L;
        long duration = result.getDurationSeconds() != null ? result.getDurationSeconds() : 0L;
        return views + (duration * 1000L);
    }

    private Process startExtractionProcess(String videoName, String videoId, Path workDirectory) throws IOException {
        String target = (videoId != null && !videoId.isBlank())
                ? "https://www.youtube.com/watch?v=" + videoId
                : "ytsearch1:" + videoName;

        List<String> command = List.of(
                "yt-dlp",
                target,
                "--extract-audio",
                "--audio-format",
                "mp3",
                "--audio-quality",
                "0",
                "--print",
                "title",
                "--no-simulate",
                "--no-playlist",
                "--extractor-args",
                "youtube:player_client=android",
                "--output",
                workDirectory.resolve("%(id)s.%(ext)s").toString()
        );

        return new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();
    }

    private String readProcessOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException exception) {
            return "";
        }
    }

    private String readProcessError(Process process) {
        try {
            return new String(process.getErrorStream().readAllBytes());
        } catch (IOException exception) {
            return "";
        }
    }

    private Path createWorkDirectory() {
        try {
            return Files.createTempDirectory("youtube-audio-");
        } catch (IOException exception) {
            throw new AudioExtractionException("We could not prepare the audio extraction. Try again.", exception);
        }
    }

    private Path findAudioFile(Path workDirectory) throws IOException {
        try (var files = Files.list(workDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .findFirst()
                    .orElseThrow(() -> new AudioExtractionException("We could not find an audio available for that video."));
        }
    }

    private String resolveVideoTitle(String commandOutput, String fallbackTitle) {
        return commandOutput.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallbackTitle);
    }

    private String resolveContentType(Path audioFile) {
        try {
            String detectedType = Files.probeContentType(audioFile);
            return detectedType == null ? "audio/mpeg" : detectedType;
        } catch (IOException exception) {
            return "audio/mpeg";
        }
    }

    private String resolveFriendlyError(String commandError, String fallbackMessage) {
        String normalizedError = commandError == null ? "" : commandError.toLowerCase();

        if (normalizedError.contains("ffmpeg")) {
            return "We could not convert the audio. Check that ffmpeg is installed on the server.";
        }

        if (normalizedError.contains("403") || normalizedError.contains("forbidden")
                || normalizedError.contains("unsupported url") || normalizedError.contains("unable to download webpage")) {
            return "We could not access YouTube right now. Try again later.";
        }

        if (normalizedError.contains("private video") || normalizedError.contains("sign in")) {
            return "The video is not publicly available. Try another result.";
        }

        if (normalizedError.contains("no video results")) {
            return "We could not find videos with that name. Try a more specific search.";
        }

        return fallbackMessage;
    }

    private void deleteDirectory(Path directory) {
        try (var files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
