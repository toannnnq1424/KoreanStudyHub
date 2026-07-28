package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable evaluator/audit identity. Creation is intentionally left to
 * 13C3-03; 13C3-01 maps the accepted persistence contract for that handoff.
 */
@Entity
@Table(name = "practice_speaking_prompt_version_contexts")
public class SpeakingPromptVersionContext {

    @Id
    @Column(name = "question_version_id")
    private Long questionVersionId;

    @Column(name = "owner_lecturer_id", nullable = false)
    private Long ownerLecturerId;

    @Column(name = "input_type", nullable = false, length = 32)
    private String inputType;

    @Column(name = "delivery_mode", nullable = false, length = 32)
    private String deliveryMode;

    @Column(name = "audio_origin", nullable = false, length = 32)
    private String audioOrigin;

    @Column(name = "prompt_context_source", nullable = false, length = 32)
    private String promptContextSource;

    @Column(name = "prompt_context_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String promptContextText;

    @Column(name = "prompt_context_sha256", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String promptContextSha256;

    @Column(name = "prompt_context_fingerprint", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String promptContextFingerprint;

    @Column(name = "original_audio_asset_id")
    private Long originalAudioAssetId;

    @Column(name = "active_audio_asset_id")
    private Long activeAudioAssetId;

    @Column(name = "stt_artifact_id")
    private Long sttArtifactId;

    @Column(name = "tts_artifact_id")
    private Long ttsArtifactId;

    @Column(name = "stt_operation", nullable = false, length = 16)
    private String sttOperation = SpeakingPromptAiContract.Operation.STT.code();

    @Column(name = "tts_operation", nullable = false, length = 16)
    private String ttsOperation = SpeakingPromptAiContract.Operation.TTS.code();

    @Column(name = "stt_provider_code", length = 64)
    private String sttProviderCode;

    @Column(name = "stt_model_code", length = 128)
    private String sttModelCode;

    @Column(name = "stt_contract_version", length = 64)
    private String sttContractVersion;

    @Column(name = "stt_purpose_code", length = 64)
    private String sttPurposeCode;

    @Column(name = "stt_retention_code", length = 64)
    private String sttRetentionCode;

    @Column(name = "tts_provider_code", length = 64)
    private String ttsProviderCode;

    @Column(name = "tts_model_code", length = 128)
    private String ttsModelCode;

    @Column(name = "tts_contract_version", length = 64)
    private String ttsContractVersion;

    @Column(name = "tts_purpose_code", length = 64)
    private String ttsPurposeCode;

    @Column(name = "tts_retention_code", length = 64)
    private String ttsRetentionCode;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SpeakingPromptVersionContext() {
    }

    static SpeakingPromptVersionContext create(
            Long questionVersionId,
            ImmutableData data,
            Long actorId) {
        Objects.requireNonNull(questionVersionId, "questionVersionId");
        Objects.requireNonNull(data, "data");
        String exactSha = SpeakingPromptContextIdentity.contextSha256(
                data.promptContextText());
        if (!Objects.equals(exactSha, data.promptContextSha256())) {
            throw new IllegalArgumentException(
                    "Mã băm ngữ cảnh đề Speaking không khớp nội dung bất biến.");
        }
        String exactFingerprint = SpeakingPromptContextIdentity.fingerprint(data);
        if (!Objects.equals(exactFingerprint, data.promptContextFingerprint())) {
            throw new IllegalArgumentException(
                    "Dấu vân tay ngữ cảnh đề Speaking không khớp nguồn xuất bản.");
        }
        SpeakingPromptVersionContext context = new SpeakingPromptVersionContext();
        context.questionVersionId = questionVersionId;
        context.ownerLecturerId = data.ownerLecturerId();
        context.inputType = data.inputType();
        context.deliveryMode = data.deliveryMode();
        context.audioOrigin = data.audioOrigin();
        context.promptContextSource = data.promptContextSource();
        context.promptContextText = data.promptContextText();
        context.promptContextSha256 = exactSha;
        context.promptContextFingerprint = exactFingerprint;
        context.originalAudioAssetId = data.originalAudioAssetId();
        context.activeAudioAssetId = data.activeAudioAssetId();
        context.sttArtifactId = data.sttArtifactId();
        context.ttsArtifactId = data.ttsArtifactId();
        context.sttProviderCode = data.sttProviderCode();
        context.sttModelCode = data.sttModelCode();
        context.sttContractVersion = data.sttContractVersion();
        context.sttPurposeCode = data.sttPurposeCode();
        context.sttRetentionCode = data.sttRetentionCode();
        context.ttsProviderCode = data.ttsProviderCode();
        context.ttsModelCode = data.ttsModelCode();
        context.ttsContractVersion = data.ttsContractVersion();
        context.ttsPurposeCode = data.ttsPurposeCode();
        context.ttsRetentionCode = data.ttsRetentionCode();
        context.createdBy = Objects.requireNonNull(actorId, "actorId");
        context.verifyIntegrity();
        return context;
    }

    void verifyIntegrity() {
        ImmutableData data = immutableData();
        if (!Objects.equals(
                SpeakingPromptContextIdentity.contextSha256(promptContextText),
                promptContextSha256)
                || !Objects.equals(
                SpeakingPromptContextIdentity.fingerprint(data),
                promptContextFingerprint)) {
            throw new IllegalStateException(
                    "Ngữ cảnh đề Speaking bất biến không còn khớp dấu vân tay.");
        }
    }

    ImmutableData immutableData() {
        return new ImmutableData(
                ownerLecturerId, inputType, deliveryMode, audioOrigin,
                promptContextSource, promptContextText, promptContextSha256,
                promptContextFingerprint, originalAudioAssetId, activeAudioAssetId,
                sttArtifactId, ttsArtifactId, sttProviderCode, sttModelCode,
                sttContractVersion, sttPurposeCode, sttRetentionCode,
                ttsProviderCode, ttsModelCode, ttsContractVersion,
                ttsPurposeCode, ttsRetentionCode);
    }

    public Long getQuestionVersionId() { return questionVersionId; }
    public Long getOwnerLecturerId() { return ownerLecturerId; }
    public String getInputType() { return inputType; }
    public String getDeliveryMode() { return deliveryMode; }
    public String getAudioOrigin() { return audioOrigin; }
    public String getPromptContextSource() { return promptContextSource; }
    String getPromptContextText() { return promptContextText; }
    String getPromptContextSha256() { return promptContextSha256; }
    String getPromptContextFingerprint() { return promptContextFingerprint; }
    Long getOriginalAudioAssetId() { return originalAudioAssetId; }
    Long getActiveAudioAssetId() { return activeAudioAssetId; }
    public Long getSttArtifactId() { return sttArtifactId; }
    public Long getTtsArtifactId() { return ttsArtifactId; }
    public String getSttProviderCode() { return sttProviderCode; }
    public String getSttModelCode() { return sttModelCode; }
    public String getSttContractVersion() { return sttContractVersion; }
    public String getSttPurposeCode() { return sttPurposeCode; }
    public String getSttRetentionCode() { return sttRetentionCode; }
    public String getTtsProviderCode() { return ttsProviderCode; }
    public String getTtsModelCode() { return ttsModelCode; }
    public String getTtsContractVersion() { return ttsContractVersion; }
    public String getTtsPurposeCode() { return ttsPurposeCode; }
    public String getTtsRetentionCode() { return ttsRetentionCode; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public record ImmutableData(
            Long ownerLecturerId,
            String inputType,
            String deliveryMode,
            String audioOrigin,
            String promptContextSource,
            String promptContextText,
            String promptContextSha256,
            String promptContextFingerprint,
            Long originalAudioAssetId,
            Long activeAudioAssetId,
            Long sttArtifactId,
            Long ttsArtifactId,
            String sttProviderCode,
            String sttModelCode,
            String sttContractVersion,
            String sttPurposeCode,
            String sttRetentionCode,
            String ttsProviderCode,
            String ttsModelCode,
            String ttsContractVersion,
            String ttsPurposeCode,
            String ttsRetentionCode
    ) {
        ImmutableData withFingerprint() {
            String sha = SpeakingPromptContextIdentity.contextSha256(
                    promptContextText);
            ImmutableData unhashed = new ImmutableData(
                    ownerLecturerId, inputType, deliveryMode, audioOrigin,
                    promptContextSource, promptContextText, sha, "",
                    originalAudioAssetId, activeAudioAssetId,
                    sttArtifactId, ttsArtifactId,
                    sttProviderCode, sttModelCode, sttContractVersion,
                    sttPurposeCode, sttRetentionCode,
                    ttsProviderCode, ttsModelCode, ttsContractVersion,
                    ttsPurposeCode, ttsRetentionCode);
            return new ImmutableData(
                    ownerLecturerId, inputType, deliveryMode, audioOrigin,
                    promptContextSource, promptContextText, sha,
                    SpeakingPromptContextIdentity.fingerprint(unhashed),
                    originalAudioAssetId, activeAudioAssetId,
                    sttArtifactId, ttsArtifactId,
                    sttProviderCode, sttModelCode, sttContractVersion,
                    sttPurposeCode, sttRetentionCode,
                    ttsProviderCode, ttsModelCode, ttsContractVersion,
                    ttsPurposeCode, ttsRetentionCode);
        }
    }

    @Override
    public String toString() {
        return "SpeakingPromptVersionContext{questionVersionId="
                + questionVersionId + ", inputType='" + inputType + "'}";
    }
}
