package com.ksh.features.practice.service.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FfprobeSpeakingAudioInspector implements SpeakingAudioInspector {
    private static final Set<String> WEBM_FORMATS = Set.of("webm");
    private static final Set<String> MP4_FORMATS = Set.of("mp4", "m4a");
    private static final Set<String> KNOWN_FORMATS = Set.of("webm", "matroska", "mov", "mp4", "m4a", "3gp", "3g2", "mj2");

    private final ObjectMapper objectMapper;
    private final FfprobeProcessRunner processRunner;
    private final SpeakingAudioProperties properties;

    public FfprobeSpeakingAudioInspector(ObjectMapper objectMapper,
                                         FfprobeProcessRunner processRunner,
                                         SpeakingAudioProperties properties) {
        this.objectMapper = objectMapper;
        this.processRunner = processRunner;
        this.properties = properties;
    }

    @Override
    public SpeakingAudioInspection inspect(Path privateMediaPath) {
        FfprobeProcessResult result = processRunner.run(privateMediaPath);
        if (result.getExitCode() != 0) {
            throw validation(SpeakingAudioValidationCategory.CORRUPT_MEDIA, "Audio media could not be inspected");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(result.getStdout());
        } catch (Exception ex) {
            throw validation(SpeakingAudioValidationCategory.CORRUPT_MEDIA, "Audio probe output is invalid", ex);
        }

        JsonNode streams = root.get("streams");
        if (streams == null || !streams.isArray() || streams.isEmpty()) {
            throw validation(SpeakingAudioValidationCategory.INVALID_CONTAINER, "Audio stream is missing");
        }

        JsonNode audioStream = null;
        int audioCount = 0;
        for (JsonNode stream : streams) {
            String codecType = normalizedText(stream.get("codec_type"));
            if ("audio".equals(codecType)) {
                audioCount++;
                audioStream = stream;
            } else {
                throw validation(SpeakingAudioValidationCategory.NON_AUDIO_STREAM_PRESENT, "Only one audio stream is allowed");
            }
        }
        if (audioCount == 0) {
            throw validation(SpeakingAudioValidationCategory.INVALID_CONTAINER, "Audio stream is missing");
        }
        if (audioCount > 1) {
            throw validation(SpeakingAudioValidationCategory.MULTIPLE_AUDIO_STREAMS, "Only one audio stream is allowed");
        }

        String codec = normalizedText(audioStream.get("codec_name"));
        if (codec == null || codec.isBlank()) {
            throw validation(SpeakingAudioValidationCategory.UNSUPPORTED_CODEC, "Audio codec is unsupported");
        }

        JsonNode format = root.get("format");
        Set<String> formatTokens = formatTokens(format == null ? null : format.get("format_name"));
        long durationMs = durationMillis(audioStream.get("duration"), format == null ? null : format.get("duration"));

        if ("opus".equals(codec) && hasAny(formatTokens, WEBM_FORMATS)) {
            return new SpeakingAudioInspection("webm", "opus", "audio/webm", durationMs);
        }
        if ("aac".equals(codec) && hasAny(formatTokens, MP4_FORMATS)) {
            return new SpeakingAudioInspection("mp4", "aac", "audio/mp4", durationMs);
        }
        if (hasAny(formatTokens, KNOWN_FORMATS)) {
            throw validation(SpeakingAudioValidationCategory.UNSUPPORTED_CODEC, "Audio codec is unsupported");
        }
        throw validation(SpeakingAudioValidationCategory.INVALID_CONTAINER, "Audio container is unsupported");
    }

    private long durationMillis(JsonNode streamDuration, JsonNode formatDuration) {
        BigDecimal selectedSeconds = parseDurationSeconds(streamDuration);
        if (selectedSeconds == null) {
            selectedSeconds = parseDurationSeconds(formatDuration);
        }
        if (selectedSeconds == null) {
            throw validation(SpeakingAudioValidationCategory.CORRUPT_MEDIA, "Audio duration is invalid");
        }
        BigDecimal maxSeconds = BigDecimal.valueOf(properties.getMaxDuration().toMillis())
                .divide(BigDecimal.valueOf(1000L), 9, RoundingMode.UNNECESSARY);
        if (selectedSeconds.compareTo(maxSeconds) > 0) {
            throw validation(SpeakingAudioValidationCategory.TOO_LONG, "Audio duration is too long");
        }
        Long millis = toMillis(selectedSeconds);
        if (millis == null) {
            throw validation(SpeakingAudioValidationCategory.CORRUPT_MEDIA, "Audio duration is invalid");
        }
        return millis;
    }

    private BigDecimal parseDurationSeconds(JsonNode durationNode) {
        if (durationNode == null || durationNode.isNull()) {
            return null;
        }
        String value = durationNode.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if ("n/a".equalsIgnoreCase(normalized)
                || "nan".equalsIgnoreCase(normalized)
                || "infinity".equalsIgnoreCase(normalized)
                || "+infinity".equalsIgnoreCase(normalized)
                || "-infinity".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            BigDecimal seconds = new BigDecimal(normalized);
            if (seconds.signum() <= 0) {
                return null;
            }
            return seconds;
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    private Long toMillis(BigDecimal seconds) {
        BigDecimal millis = seconds.multiply(BigDecimal.valueOf(1000L)).setScale(0, RoundingMode.HALF_UP);
        if (millis.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return null;
        }
        long valueMs = millis.longValueExact();
        return valueMs > 0 ? valueMs : null;
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

    private static boolean hasAny(Set<String> actual, Set<String> allowed) {
        return actual.stream().anyMatch(allowed::contains);
    }

    private static String normalizedText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category, String message) {
        return new SpeakingAudioValidationException(category, message);
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category, String message, Throwable cause) {
        return new SpeakingAudioValidationException(category, message, cause);
    }
}
