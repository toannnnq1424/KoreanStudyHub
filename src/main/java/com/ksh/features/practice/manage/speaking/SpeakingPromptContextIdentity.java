package com.ksh.features.practice.manage.speaking;

import java.nio.charset.StandardCharsets;

final class SpeakingPromptContextIdentity {
    static final String CONTRACT_IDENTITY = "speaking-prompt-version-context-v1";

    private SpeakingPromptContextIdentity() {
    }

    static String contextSha256(String exactText) {
        if (exactText == null || exactText.isBlank()) {
            throw new IllegalArgumentException("Ngữ cảnh đề Speaking không được để trống.");
        }
        return SpeakingPromptAiContract.exactBytesSha256(
                exactText.getBytes(StandardCharsets.UTF_8));
    }

    static String fingerprint(SpeakingPromptVersionContext.ImmutableData data) {
        String canonical = String.join("\n",
                field("contract", CONTRACT_IDENTITY),
                field("owner", data.ownerLecturerId()),
                field("input_type", data.inputType()),
                field("delivery_mode", data.deliveryMode()),
                field("audio_origin", data.audioOrigin()),
                field("context_source", data.promptContextSource()),
                field("context_sha256", data.promptContextSha256()),
                field("original_asset", data.originalAudioAssetId()),
                field("active_asset", data.activeAudioAssetId()),
                field("stt_artifact", data.sttArtifactId()),
                field("tts_artifact", data.ttsArtifactId()),
                field("stt_provider", data.sttProviderCode()),
                field("stt_model", data.sttModelCode()),
                field("stt_contract", data.sttContractVersion()),
                field("stt_purpose", data.sttPurposeCode()),
                field("stt_retention", data.sttRetentionCode()),
                field("tts_provider", data.ttsProviderCode()),
                field("tts_model", data.ttsModelCode()),
                field("tts_contract", data.ttsContractVersion()),
                field("tts_purpose", data.ttsPurposeCode()),
                field("tts_retention", data.ttsRetentionCode()));
        return SpeakingPromptAiContract.exactBytesSha256(
                canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String field(String name, Object value) {
        String safe = value == null ? "" : String.valueOf(value);
        return name.length() + ":" + name + "=" + safe.length() + ":" + safe;
    }
}
