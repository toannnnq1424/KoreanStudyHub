package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "practice_speaking_prompt_ai_artifacts")
public class SpeakingPromptAiArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_lecturer_id", nullable = false)
    private Long ownerLecturerId;

    @Column(nullable = false, length = 16)
    private String operation;

    @Column(name = "operation_fingerprint", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String operationFingerprint;

    @Column(name = "input_source_revision", nullable = false)
    private Long inputSourceRevision;

    @Column(name = "input_sha256", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String inputSha256;

    @Column(name = "input_audio_asset_id")
    private Long inputAudioAssetId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "model_code", nullable = false, length = 128)
    private String modelCode;

    @Column(name = "language_tag", nullable = false, length = 32)
    private String languageTag;

    @Column(name = "voice_code", length = 128)
    private String voiceCode;

    @Column(precision = 5, scale = 2)
    private BigDecimal speed;

    @Column(name = "output_format", length = 32)
    private String outputFormat;

    @Column(name = "contract_version", nullable = false, length = 64)
    private String contractVersion;

    @Column(name = "purpose_code", nullable = false, length = 64)
    private String purposeCode;

    @Column(name = "retention_code", nullable = false, length = 64)
    private String retentionCode;

    @Column(name = "provider_request_reference", length = 255)
    private String providerRequestReference;

    @Column(name = "provider_transcript_text", columnDefinition = "MEDIUMTEXT")
    private String providerTranscriptText;

    @Column(name = "current_context_text", columnDefinition = "MEDIUMTEXT")
    private String currentContextText;

    @Column(name = "current_context_sha256", length = 64,
            columnDefinition = "CHAR(64)")
    private String currentContextSha256;

    @Column(name = "generated_audio_asset_id")
    private Long generatedAudioAssetId;

    @Column(precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "artifact_status", nullable = false, length = 32)
    private String artifactStatus;

    @Column(name = "public_error_category", length = 64)
    private String publicErrorCategory;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected SpeakingPromptAiArtifact() {
    }

    public void markSttReady(
            SpeakingPromptAiContract.SttResult result,
            String contextSha256,
            boolean needsReview,
            LocalDateTime now) {
        requireOperation(SpeakingPromptAiContract.Operation.STT);
        requireUnresolved();
        providerRequestReference = result.providerRequestReference();
        providerTranscriptText = result.providerTranscript();
        currentContextText = result.providerTranscript();
        currentContextSha256 = requiredHash(contextSha256);
        confidence = result.confidence();
        artifactStatus = needsReview
                ? SpeakingPromptSource.STATUS_NEEDS_REVIEW
                : SpeakingPromptSource.STATUS_READY;
        publicErrorCategory = null;
        readyAt = now;
        failedAt = null;
        supersededAt = null;
    }

    public void markTtsReady(
            SpeakingPromptAiContract.TtsResult result,
            Long assetId,
            LocalDateTime now) {
        requireOperation(SpeakingPromptAiContract.Operation.TTS);
        requireUnresolved();
        providerRequestReference = result.providerRequestReference();
        generatedAudioAssetId = Objects.requireNonNull(assetId, "assetId");
        artifactStatus = SpeakingPromptSource.STATUS_READY;
        publicErrorCategory = null;
        readyAt = now;
        failedAt = null;
        supersededAt = null;
    }

    public boolean isReady() {
        return SpeakingPromptSource.STATUS_READY.equals(artifactStatus)
                || SpeakingPromptSource.STATUS_NEEDS_REVIEW.equals(artifactStatus);
    }

    private void requireOperation(SpeakingPromptAiContract.Operation expected) {
        if (!expected.code().equals(operation)) {
            throw new IllegalStateException(
                    "Speaking prompt artifact operation does not match completion.");
        }
    }

    private void requireUnresolved() {
        if (isReady()) {
            throw new IllegalStateException(
                    "A verified Speaking prompt artifact outcome is immutable.");
        }
    }

    private static String requiredHash(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 identity is invalid.");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getOwnerLecturerId() { return ownerLecturerId; }
    public String getOperation() { return operation; }
    String getOperationFingerprint() { return operationFingerprint; }
    public Long getInputSourceRevision() { return inputSourceRevision; }
    String getInputSha256() { return inputSha256; }
    Long getInputAudioAssetId() { return inputAudioAssetId; }
    public String getProviderCode() { return providerCode; }
    public String getModelCode() { return modelCode; }
    public String getLanguageTag() { return languageTag; }
    public String getVoiceCode() { return voiceCode; }
    public BigDecimal getSpeed() { return speed; }
    public String getOutputFormat() { return outputFormat; }
    public String getContractVersion() { return contractVersion; }
    public String getPurposeCode() { return purposeCode; }
    public String getRetentionCode() { return retentionCode; }
    Long getGeneratedAudioAssetId() { return generatedAudioAssetId; }
    public BigDecimal getConfidence() { return confidence; }
    public String getArtifactStatus() { return artifactStatus; }
    public String getPublicErrorCategory() { return publicErrorCategory; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public LocalDateTime getFailedAt() { return failedAt; }
    public LocalDateTime getSupersededAt() { return supersededAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "SpeakingPromptAiArtifact{id=" + id
                + ", operation='" + operation + '\''
                + ", artifactStatus='" + artifactStatus + '\''
                + '}';
    }
}
