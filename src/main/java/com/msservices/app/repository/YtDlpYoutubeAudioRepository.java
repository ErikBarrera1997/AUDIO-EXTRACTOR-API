package com.msservices.app.repository;

import com.msservices.app.dto.ExtractedAudioDto;
import com.msservices.app.exception.AudioExtractionException;
import com.msservices.app.exception.YoutubeToolUnavailableException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Repository;

@Repository
public class YtDlpYoutubeAudioRepository implements YoutubeAudioRepository {

    private static final Duration EXTRACTION_TIMEOUT = Duration.ofMinutes(3);

    @Override
    public ExtractedAudioDto extractAudioByVideoName(String videoName) {
        Path workDirectory = createWorkDirectory();

        try {
            Process process = startExtractionProcess(videoName, workDirectory);
            CompletableFuture<String> commandOutputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));
            boolean finished = process.waitFor(EXTRACTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new AudioExtractionException("La extraccion esta tardando demasiado. Intenta con otro video o vuelve a intentarlo mas tarde.");
            }

            String commandOutput = commandOutputFuture.join();

            if (process.exitValue() != 0) {
                throw new AudioExtractionException(resolveFriendlyError(commandOutput));
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
            throw new AudioExtractionException("La extraccion fue interrumpida. Intenta nuevamente.", exception);
        } catch (IOException exception) {
            throw new YoutubeToolUnavailableException("El servicio de extraccion no esta disponible. Verifica que yt-dlp y ffmpeg esten instalados.", exception);
        } finally {
            deleteDirectory(workDirectory);
        }
    }

    private Process startExtractionProcess(String videoName, Path workDirectory) throws IOException {
        List<String> command = List.of(
                "yt-dlp",
                "ytsearch1:" + videoName,
                "--extract-audio",
                "--audio-format",
                "mp3",
                "--audio-quality",
                "0",
                "--print",
                "title",
                "--no-playlist",
                "--output",
                workDirectory.resolve("%(id)s.%(ext)s").toString()
        );

        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
    }

    private String readProcessOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException exception) {
            return "";
        }
    }

    private Path createWorkDirectory() {
        try {
            return Files.createTempDirectory("youtube-audio-");
        } catch (IOException exception) {
            throw new AudioExtractionException("No pudimos preparar la extraccion del audio. Intenta nuevamente.", exception);
        }
    }

    private Path findAudioFile(Path workDirectory) throws IOException {
        try (var files = Files.list(workDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .findFirst()
                    .orElseThrow(() -> new AudioExtractionException("No encontramos un audio disponible para ese video."));
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

    private String resolveFriendlyError(String commandError) {
        String normalizedError = commandError == null ? "" : commandError.toLowerCase();

        if (normalizedError.contains("ffmpeg")) {
            return "No pudimos convertir el audio. Verifica que ffmpeg este instalado en el servidor.";
        }

        if (normalizedError.contains("unsupported url") || normalizedError.contains("unable to download webpage")) {
            return "No pudimos acceder a YouTube en este momento. Intenta nuevamente mas tarde.";
        }

        if (normalizedError.contains("private video") || normalizedError.contains("sign in")) {
            return "El video no esta disponible publicamente. Intenta con otro resultado.";
        }

        if (normalizedError.contains("no video results")) {
            return "No encontramos videos con ese nombre. Intenta con una busqueda mas especifica.";
        }

        return "No pudimos extraer el audio del video seleccionado. Intenta con otro video.";
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
