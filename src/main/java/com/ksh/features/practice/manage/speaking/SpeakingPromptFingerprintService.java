package com.ksh.features.practice.manage.speaking;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

@Component
public class SpeakingPromptFingerprintService {

    String exactTextSha256(String originalText) {
        if (originalText == null) {
            throw new IllegalArgumentException("Prompt text must not be null.");
        }
        return SpeakingPromptAiContract.exactBytesSha256(
                originalText.getBytes(StandardCharsets.UTF_8));
    }

    String sttFingerprint(
            Long ownerLecturerId,
            Long inputAudioAssetId,
            String verifiedOriginalAudioSha256,
            SpeakingPromptAuthoringAiProperties.SttConfig config) {
        return digest(
                field("owner", String.valueOf(ownerLecturerId)),
                field("operation", SpeakingPromptAiContract.Operation.STT.code()),
                field("input_audio_asset_id", String.valueOf(inputAudioAssetId)),
                field("audio_sha256", verifiedOriginalAudioSha256),
                field("language", config.language()),
                field("provider", config.provider()),
                field("model", config.model()),
                field("purpose_code", config.purposeCode()),
                field("retention_code", config.retentionCode()),
                field("contract", SpeakingPromptAiContract.CONTRACT_VERSION));
    }

    String ttsFingerprint(
            Long ownerLecturerId,
            String originalPromptText,
            SpeakingPromptAuthoringAiProperties.TtsConfig config) {
        String nfc = Normalizer.normalize(
                requiredPrompt(originalPromptText), Normalizer.Form.NFC);
        return digest(
                field("owner", String.valueOf(ownerLecturerId)),
                field("operation", SpeakingPromptAiContract.Operation.TTS.code()),
                field("prompt_nfc", nfc),
                field("language", config.language()),
                field("provider", config.provider()),
                field("model", config.model()),
                field("voice", config.voice()),
                field("speed", canonicalSpeed(config.speed())),
                field("format", config.outputFormat()),
                field("purpose_code", config.purposeCode()),
                field("retention_code", config.retentionCode()),
                field("contract", SpeakingPromptAiContract.CONTRACT_VERSION));
    }

    private static String digest(String... fields) {
        String joined = String.join("", fields);
        return SpeakingPromptAiContract.exactBytesSha256(
                joined.getBytes(StandardCharsets.UTF_8));
    }

    private static String field(String name, String value) {
        String safe = value == null ? "" : value;
        return name.length() + ":" + name + "="
                + safe.length() + ":" + safe + ";";
    }

    private static String canonicalSpeed(BigDecimal speed) {
        if (speed == null) {
            throw new IllegalArgumentException("TTS speed must not be null.");
        }
        return speed.stripTrailingZeros().toPlainString();
    }

    private static String requiredPrompt(String value) {
        if (value == null
                || value.isBlank()
                || value.length() > SpeakingPromptAiContract.MAX_PROMPT_TEXT_CHARS) {
            throw new IllegalArgumentException("TTS prompt text is invalid.");
        }
        return value;
    }
}
