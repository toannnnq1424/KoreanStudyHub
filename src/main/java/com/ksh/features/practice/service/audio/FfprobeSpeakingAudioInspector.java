package com.ksh.features.practice.service.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Set;

@Service
public class FfprobeSpeakingAudioInspector implements SpeakingAudioInspector {
    private static final Set<String> WEBM_FORMATS = Set.of("webm");
    private static final Set<String> MP4_FORMATS = Set.of("mp4", "m4a");
    private static final Set<String> KNOWN_FORMATS = Set.of("webm", "matroska", "mov", "mp4", "m4a", "3gp", "3g2", "mj2");

    private final FfprobeAudioProbe audioProbe;
    private final SpeakingAudioProperties properties;

    public FfprobeSpeakingAudioInspector(ObjectMapper objectMapper,
                                         FfprobeProcessRunner processRunner,
                                         SpeakingAudioProperties properties) {
        this.audioProbe = new FfprobeAudioProbe(objectMapper, processRunner);
        this.properties = properties;
    }

    @Override
    public SpeakingAudioInspection inspect(Path privateMediaPath) {
        FfprobeAudioProbe.Result result;
        try {
            result = audioProbe.inspect(privateMediaPath);
        } catch (FfprobeAudioProbe.ProbeException exception) {
            throw validation(
                    validationCategory(exception.failure()),
                    exception.getMessage(),
                    exception);
        }
        BigDecimal maximumSeconds = BigDecimal
                .valueOf(properties.getMaxDuration().toMillis())
                .divide(
                        BigDecimal.valueOf(1000L),
                        9,
                        RoundingMode.UNNECESSARY);
        if (result.durationSeconds().compareTo(maximumSeconds) > 0) {
            throw validation(
                    SpeakingAudioValidationCategory.TOO_LONG,
                    "Audio duration is too long");
        }
        if ("opus".equals(result.codec())
                && hasAny(result.formatTokens(), WEBM_FORMATS)) {
            return new SpeakingAudioInspection(
                    "webm",
                    "opus",
                    "audio/webm",
                    result.durationMillis());
        }
        if ("aac".equals(result.codec())
                && hasAny(result.formatTokens(), MP4_FORMATS)) {
            return new SpeakingAudioInspection(
                    "mp4",
                    "aac",
                    "audio/mp4",
                    result.durationMillis());
        }
        if (hasAny(result.formatTokens(), KNOWN_FORMATS)) {
            throw validation(SpeakingAudioValidationCategory.UNSUPPORTED_CODEC, "Audio codec is unsupported");
        }
        throw validation(SpeakingAudioValidationCategory.INVALID_CONTAINER, "Audio container is unsupported");
    }

    private static boolean hasAny(Set<String> actual, Set<String> allowed) {
        return actual.stream().anyMatch(allowed::contains);
    }

    private static SpeakingAudioValidationCategory validationCategory(
            FfprobeAudioProbe.Failure failure) {
        return switch (failure) {
            case NON_AUDIO_STREAM_PRESENT ->
                    SpeakingAudioValidationCategory.NON_AUDIO_STREAM_PRESENT;
            case MULTIPLE_AUDIO_STREAMS ->
                    SpeakingAudioValidationCategory.MULTIPLE_AUDIO_STREAMS;
            case AUDIO_STREAM_MISSING ->
                    SpeakingAudioValidationCategory.INVALID_CONTAINER;
            case CODEC_MISSING ->
                    SpeakingAudioValidationCategory.UNSUPPORTED_CODEC;
            case CORRUPT_OUTPUT, INVALID_DURATION ->
                    SpeakingAudioValidationCategory.CORRUPT_MEDIA;
        };
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category, String message) {
        return new SpeakingAudioValidationException(category, message);
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category, String message, Throwable cause) {
        return new SpeakingAudioValidationException(category, message, cause);
    }
}
