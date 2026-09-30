package com.msservices.app.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msservices.app.config.YtdlpProperties;
import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.AudioExtractionException;
import com.msservices.app.exception.InvalidVideoSearchException;
import com.msservices.app.exception.YoutubeAccessBlockedException;
import com.msservices.app.exception.YoutubeToolUnavailableException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class YtDlpYoutubeAudioRepository implements YoutubeAudioRepository {

    private static final Logger log = LoggerFactory.getLogger(YtDlpYoutubeAudioRepository.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final YtdlpProperties properties;

    public YtDlpYoutubeAudioRepository(YtdlpProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<AudioSearchResultDto> searchVideos(String videoName) {
        YoutubeAccessBlockedException lastBlock = null;
        AudioExtractionException lastFailure = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getSearchTimeoutSeconds());

        for (String playerClient : properties.getPlayerClients()) {
            int remainingSeconds = remainingSeconds(deadline);
            if (remainingSeconds <= 0) {
                lastFailure = new AudioExtractionException("The search is taking too long. Try again.");
                break;
            }

            List<String> command = buildSearchCommand(videoName, playerClient);
            try {
                ProcessOutcome outcome = runCommand(command, remainingSeconds);
                if (outcome.timedOut()) {
                    lastFailure = new AudioExtractionException("The search is taking too long. Try again.");
                    continue;
                }
                if (outcome.exitCode() != 0) {
                    log.warn("yt-dlp search failed for client '{}' (exit {})", playerClient, outcome.exitCode());
                    if (isAccessBlocked(outcome.errorOutput())) {
                        lastBlock = new YoutubeAccessBlockedException(
                                "YouTube is blocking this request from our server. Try again later.");
                        continue;
                    }
                    lastFailure = new AudioExtractionException(
                            resolveFriendlyError(outcome.errorOutput(), "We could not perform the search. Try again."));
                    continue;
                }
                return rankBestResults(parseSearchResults(outcome.output()));
            } catch (IOException exception) {
                throw new YoutubeToolUnavailableException(
                        "The extraction service is unavailable. Check that yt-dlp and ffmpeg are installed.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AudioExtractionException("The search was interrupted. Try again.", exception);
            }
        }

        if (lastBlock != null) {
            throw lastBlock;
        }
        throw lastFailure != null
                ? lastFailure
                : new YoutubeAccessBlockedException("We could not perform the search right now. Try again later.");
    }

    @Override
    public ExtractedAudioDto extractAudioByVideoName(String videoName, String videoId) {
        Path rootDirectory = createWorkDirectory();
        YoutubeAccessBlockedException lastBlock = null;
        AudioExtractionException lastFailure = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getExtractionTimeoutSeconds());

        try {
            for (String playerClient : properties.getPlayerClients()) {
                int remainingSeconds = remainingSeconds(deadline);
                if (remainingSeconds <= 0) {
                    lastFailure = new AudioExtractionException(
                            "The extraction is taking too long. Try another video or try again later.");
                    break;
                }

                Path workDirectory = Files.createDirectory(rootDirectory.resolve(playerClient));
                Process process = startExtractionProcess(videoName, videoId, workDirectory, playerClient);
                ProcessOutcome outcome = awaitProcess(process, remainingSeconds);

                if (outcome.timedOut()) {
                    lastFailure = new AudioExtractionException(
                            "The extraction is taking too long. Try another video or try again later.");
                    continue;
                }

                if (outcome.exitCode() != 0) {
                    String errorOutput = outcome.errorOutput();
                    if (isAccessBlocked(errorOutput)) {
                        log.warn("yt-dlp extraction blocked for client '{}'", playerClient);
                        lastBlock = new YoutubeAccessBlockedException(
                                "YouTube is blocking this request from our server. Try again later.");
                        continue;
                    }
                    lastFailure = new AudioExtractionException(resolveFriendlyError(errorOutput,
                            "We could not extract the audio from the selected video. Try another video."));
                    continue;
                }

                Path audioFile = findAudioFile(workDirectory);
                byte[] audioBytes = Files.readAllBytes(audioFile);
                String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

                return new ExtractedAudioDto(
                        resolveVideoTitle(outcome.output(), videoName),
                        audioFile.getFileName().toString(),
                        resolveContentType(audioFile),
                        audioBase64
                );
            }

            if (lastBlock != null) {
                throw lastBlock;
            }
            throw lastFailure != null
                    ? lastFailure
                    : new YoutubeAccessBlockedException("We could not extract the audio. Try again later.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AudioExtractionException("The extraction was interrupted. Try again.", exception);
        } catch (IOException exception) {
            throw new YoutubeToolUnavailableException(
                    "The extraction service is unavailable. Check that yt-dlp and ffmpeg are installed.", exception);
        } finally {
            deleteDirectory(rootDirectory);
        }
    }

    private int remainingSeconds(long deadlineNanos) {
        long remaining = TimeUnit.NANOSECONDS.toSeconds(deadlineNanos - System.nanoTime());
        return (int) Math.max(0, Math.min(remaining, Integer.MAX_VALUE));
    }

    private List<String> buildSearchCommand(String videoName, String playerClient) {
        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add("--flat-playlist");
        command.add("--match-filter");
        command.add("duration<" + properties.getMaxDurationSeconds());
        command.add("-J");
        command.addAll(buildCommonArgs(playerClient));
        command.add("ytsearch" + properties.getSearchLimit() + ":" + videoName);
        return command;
    }

    private Process startExtractionProcess(String videoName, String videoId, Path workDirectory, String playerClient)
            throws IOException {
        String target = (videoId != null && !videoId.isBlank())
                ? "https://www.youtube.com/watch?v=" + videoId
                : "ytsearch1:" + videoName;

        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add(target);
        command.add("--extract-audio");
        command.add("--audio-format");
        command.add("mp3");
        command.add("--audio-quality");
        command.add("0");
        command.add("--print");
        command.add("title");
        command.add("--no-simulate");
        command.add("--no-playlist");
        command.add("--output");
        command.add(workDirectory.resolve("%(id)s.%(ext)s").toString());
        command.addAll(buildCommonArgs(playerClient));

        return new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();
    }

    private List<String> buildCommonArgs(String playerClient) {
        List<String> args = new ArrayList<>();
        args.add("--extractor-args");
        args.add("youtube:player_client=" + playerClient + ";player_skip=webpage");
        if (properties.isPotEnabled()) {
            args.add("--extractor-args");
            args.add("youtubepot-bgutilhttp:base_url=" + properties.getPotProviderUrl());
        }
        Path cookies = resolveCookiesFile();
        if (cookies != null) {
            args.add("--cookies");
            args.add(cookies.toString());
        }
        String proxy = properties.getProxy();
        if (proxy != null && !proxy.isBlank()) {
            args.add("--proxy");
            args.add(proxy);
        }
        return args;
    }

    private Path resolveCookiesFile() {
        String configuredPath = properties.getCookiesPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            if (properties.isCookiesRequired()) {
                throw new YoutubeToolUnavailableException(
                        "The YouTube cookies file is not configured. Set YTDLP_COOKIES_PATH.");
            }
            return null;
        }

        Path cookies = Path.of(configuredPath);
        if (!Files.isRegularFile(cookies) || !Files.isReadable(cookies)) {
            if (properties.isCookiesRequired()) {
                log.error("Cookies file is not readable at the configured path");
                throw new YoutubeToolUnavailableException(
                        "The YouTube session cookies are not available on the server.");
            }
            log.warn("Cookies file is not readable, continuing without cookies");
            return null;
        }
        return cookies;
    }

    private ProcessOutcome runCommand(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        return awaitProcess(process, timeoutSeconds);
    }

    private ProcessOutcome awaitProcess(Process process, int timeoutSeconds) throws InterruptedException {
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
        CompletableFuture<String> errorFuture = CompletableFuture.supplyAsync(() -> readProcessError(process));
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return new ProcessOutcome(true, -1, outputFuture.getNow(""), errorFuture.getNow(""));
        }

        return new ProcessOutcome(false, process.exitValue(), outputFuture.join(), errorFuture.join());
    }

    private boolean isAccessBlocked(String errorOutput) {
        String normalizedError = errorOutput == null ? "" : errorOutput.toLowerCase();
        return normalizedError.contains("sign in to confirm")
                || normalizedError.contains("confirm you're not a bot")
                || normalizedError.contains("confirm you are not a bot")
                || normalizedError.contains("bot check")
                || normalizedError.contains("http error 403")
                || normalizedError.contains("po token")
                || normalizedError.contains("failed to extract any player response");
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

                if (duration != null && duration > properties.getMaxDurationSeconds()) {
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

    private record ProcessOutcome(boolean timedOut, int exitCode, String output, String errorOutput) {
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
