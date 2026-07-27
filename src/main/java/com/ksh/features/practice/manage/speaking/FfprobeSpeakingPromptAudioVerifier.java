package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.manage.service.PracticeUploadContentVerifier;
import com.ksh.features.practice.service.audio.FfprobeAudioProbe;
import com.ksh.features.practice.service.audio.FfprobeProcessRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class FfprobeSpeakingPromptAudioVerifier
        implements SpeakingPromptAudioVerifier {

    private final FfprobeAudioProbe audioProbe;
    private final PracticeUploadContentVerifier signatureVerifier;
    private final SpeakingPromptAuthoringAiProperties properties;

    public FfprobeSpeakingPromptAudioVerifier(
            ObjectMapper objectMapper,
            FfprobeProcessRunner processRunner,
            PracticeUploadContentVerifier signatureVerifier,
            SpeakingPromptAuthoringAiProperties properties) {
        this.audioProbe = new FfprobeAudioProbe(objectMapper, processRunner);
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
    }

    @Override
    public SpeakingPromptAiContract.VerifiedAudio verifySttInput(
            byte[] bytes,
            String filename,
            String declaredMimeType,
            String expectedSha256) {
        SpeakingPromptAuthoringAiProperties.SttConfig config =
                properties.sttConfig();
        return verify(
                bytes,
                filename,
                declaredMimeType,
                expectedSha256,
                config.maxInputBytes(),
                config.maxInputDuration().toMillis(),
                config.allowedMimeTypes(),
                null);
    }

    @Override
    public SpeakingPromptAiContract.VerifiedAudio verifyTtsOutput(
            byte[] bytes,
            String filename,
            String declaredMimeType) {
        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                properties.ttsConfig();
        return verify(
                bytes,
                filename,
                declaredMimeType,
                null,
                config.maxOutputBytes(),
                config.maxOutputDuration().toMillis(),
                config.allowedOutputMimeTypes(),
                config.allowedOutputFormats());
    }

    private SpeakingPromptAiContract.VerifiedAudio verify(
            byte[] bytes,
            String filename,
            String declaredMimeType,
            String expectedSha256,
            long maximumBytes,
            long maximumDurationMillis,
            Set<String> allowedMimeTypes,
            Set<String> allowedFormats) {
        if (bytes == null || bytes.length == 0 || bytes.length > maximumBytes) {
            throw new IllegalArgumentException(
                    "Authoring audio byte length is outside the configured bounds.");
        }
        String declared = normalized(declaredMimeType);
        if (!allowedMimeTypes.contains(declared)) {
            throw new IllegalArgumentException(
                    "Authoring audio MIME type is not allowed.");
        }
        PracticeUploadContentVerifier.VerifiedContent signature =
                signatureVerifier.verify(bytes, filename, "AUDIO");
        if (!mimeCompatible(declared, signature.mimeType())) {
            throw new IllegalArgumentException(
                    "Authoring audio MIME type does not match verified content.");
        }
        String exactHash = SpeakingPromptAiContract.exactBytesSha256(bytes);
        if (expectedSha256 != null
                && !exactHash.equalsIgnoreCase(expectedSha256)) {
            throw new IllegalArgumentException(
                    "Authoring audio hash does not match the stored asset.");
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(
                    "ksh-speaking-prompt-verify-", signature.extension());
            Files.write(temporary, bytes);
            FfprobeAudioProbe.Result probe;
            try {
                probe = audioProbe.inspect(temporary);
            } catch (FfprobeAudioProbe.ProbeException exception) {
                throw new IllegalArgumentException(
                        "Authoring audio is corrupt or unsupported.",
                        exception);
            }
            ProbeFormat selected = resolveFormat(
                    probe.formatTokens(),
                    probe.codec());
            BigDecimal maximumSeconds =
                    BigDecimal.valueOf(maximumDurationMillis).movePointLeft(3);
            if (probe.durationSeconds().compareTo(maximumSeconds) > 0) {
                throw new IllegalArgumentException(
                        "Authoring audio duration is outside the configured bounds.");
            }
            if (!selected.mimeType().equals(signature.mimeType())) {
                throw new IllegalArgumentException(
                        "Authoring audio container does not match its signature.");
            }
            if (allowedFormats != null
                    && !allowedFormats.contains(selected.outputFormat())) {
                throw new IllegalArgumentException(
                        "Generated authoring audio format is not allowed.");
            }
            return new SpeakingPromptAiContract.VerifiedAudio(
                    bytes,
                    filename,
                    selected.mimeType(),
                    exactHash,
                    probe.durationMillis());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Authoring audio could not be verified safely.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The OS temporary directory remains outside product storage.
                }
            }
        }
    }

    private static ProbeFormat resolveFormat(Set<String> formats, String codec) {
        if (formats.contains("mp3") && "mp3".equals(codec)) {
            return new ProbeFormat("audio/mpeg", "mp3");
        }
        if (formats.contains("wav")
                && (codec.startsWith("pcm_") || Set.of("flac", "alac").contains(codec))) {
            return new ProbeFormat("audio/wav", "wav");
        }
        if ((formats.contains("mov")
                || formats.contains("mp4")
                || formats.contains("m4a"))
                && Set.of("aac", "alac").contains(codec)) {
            return new ProbeFormat("audio/mp4", "m4a");
        }
        if (formats.contains("ogg")
                && Set.of("vorbis", "opus", "flac").contains(codec)) {
            return new ProbeFormat("audio/ogg", "ogg");
        }
        if ((formats.contains("webm") || formats.contains("matroska"))
                && Set.of("opus", "vorbis").contains(codec)) {
            return new ProbeFormat("audio/webm", "webm");
        }
        throw new IllegalArgumentException(
                "Authoring audio container or codec is unsupported.");
    }

    private static boolean mimeCompatible(String declared, String verified) {
        return declared.equals(verified)
                || Set.of("audio/wav", "audio/x-wav").contains(declared)
                    && "audio/wav".equals(verified)
                || Set.of("audio/mp4", "audio/x-m4a").contains(declared)
                    && "audio/mp4".equals(verified);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ProbeFormat(String mimeType, String outputFormat) {
    }
}
