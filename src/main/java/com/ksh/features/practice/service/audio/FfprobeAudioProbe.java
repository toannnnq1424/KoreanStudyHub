package com.ksh.features.practice.service.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain-neutral parser for the project's existing ffprobe runner.
 * Callers retain their own accepted-container, codec and duration policy.
 */
public final class FfprobeAudioProbe {

    private final ObjectMapper objectMapper;
    private final FfprobeProcessRunner processRunner;

    public FfprobeAudioProbe(
            ObjectMapper objectMapper,
            FfprobeProcessRunner processRunner) {
        this.objectMapper = objectMapper;
        this.processRunner = processRunner;
    }

    public Result inspect(Path mediaPath) {
        FfprobeProcessResult process = processRunner.run(mediaPath);
        if (process.getExitCode() != 0) {
            throw new ProbeException(
                    Failure.CORRUPT_OUTPUT,
                    "Audio media could not be inspected");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(process.getStdout());
        } catch (Exception exception) {
            throw new ProbeException(
                    Failure.CORRUPT_OUTPUT,
                    "Audio probe output is invalid",
                    exception);
        }

        JsonNode streams = root.get("streams");
        if (streams == null || !streams.isArray() || streams.isEmpty()) {
            throw new ProbeException(
                    Failure.AUDIO_STREAM_MISSING,
                    "Audio stream is missing");
        }
        JsonNode audioStream = null;
        int audioCount = 0;
        for (JsonNode stream : streams) {
            if ("audio".equals(normalizedText(stream.get("codec_type")))) {
                audioCount++;
                audioStream = stream;
            } else {
                throw new ProbeException(
                        Failure.NON_AUDIO_STREAM_PRESENT,
                        "Only one audio stream is allowed");
            }
        }
        if (audioCount == 0) {
            throw new ProbeException(
                    Failure.AUDIO_STREAM_MISSING,
                    "Audio stream is missing");
        }
        if (audioCount > 1) {
            throw new ProbeException(
                    Failure.MULTIPLE_AUDIO_STREAMS,
                    "Only one audio stream is allowed");
        }

        String codec = normalizedText(audioStream.get("codec_name"));
        if (codec == null || codec.isBlank()) {
            throw new ProbeException(
                    Failure.CODEC_MISSING,
                    "Audio codec is missing");
        }
        JsonNode format = root.get("format");
        Set<String> formats = formatTokens(
                format == null ? null : format.get("format_name"));
        DurationValue duration = duration(
                audioStream.get("duration"),
                format == null ? null : format.get("duration"));
        return new Result(
                formats,
                codec,
                duration.seconds(),
                duration.millis());
    }

    private static DurationValue duration(
            JsonNode streamDuration,
            JsonNode formatDuration) {
        BigDecimal seconds = parseDurationSeconds(streamDuration);
        if (seconds == null) {
            seconds = parseDurationSeconds(formatDuration);
        }
        if (seconds == null) {
            throw new ProbeException(
                    Failure.INVALID_DURATION,
                    "Audio duration is invalid");
        }
        try {
            long millis = seconds.multiply(BigDecimal.valueOf(1000L))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (millis <= 0L) {
                throw new ProbeException(
                        Failure.INVALID_DURATION,
                        "Audio duration is invalid");
            }
            return new DurationValue(seconds, millis);
        } catch (ArithmeticException exception) {
            throw new ProbeException(
                    Failure.INVALID_DURATION,
                    "Audio duration is invalid",
                    exception);
        }
    }

    private static BigDecimal parseDurationSeconds(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if ("n/a".equalsIgnoreCase(normalized)
                || "nan".equalsIgnoreCase(normalized)
                || normalized.toLowerCase(Locale.ROOT).contains("infinity")) {
            return null;
        }
        try {
            BigDecimal seconds = new BigDecimal(normalized);
            return seconds.signum() > 0 ? seconds : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Set<String> formatTokens(JsonNode node) {
        String value = normalizedText(node);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizedText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(
            Set<String> formatTokens,
            String codec,
            BigDecimal durationSeconds,
            long durationMillis) {
        public Result {
            formatTokens = Set.copyOf(formatTokens);
        }
    }

    private record DurationValue(
            BigDecimal seconds,
            long millis) {
    }

    public enum Failure {
        CORRUPT_OUTPUT,
        AUDIO_STREAM_MISSING,
        NON_AUDIO_STREAM_PRESENT,
        MULTIPLE_AUDIO_STREAMS,
        CODEC_MISSING,
        INVALID_DURATION
    }

    public static final class ProbeException extends RuntimeException {
        private final Failure failure;

        private ProbeException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        private ProbeException(
                Failure failure,
                String message,
                Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
