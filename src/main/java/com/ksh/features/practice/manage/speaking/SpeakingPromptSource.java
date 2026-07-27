package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "practice_speaking_prompt_sources")
public class SpeakingPromptSource {

    public static final String INPUT_AUDIO_UPLOAD = "audio_upload";
    public static final String INPUT_MANUAL_TEXT = "manual_text";

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_NEEDS_REVIEW = "needs_review";
    public static final String STATUS_STALE = "stale";
    public static final String STATUS_FAILED_RETRYABLE = "failed_retryable";
    public static final String STATUS_FAILED_FINAL = "failed_final";
    public static final String STATUS_SUPERSEDED = "superseded";
    public static final String STATUS_CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "question_client_id", nullable = false, length = 100)
    private String questionClientId;

    @Column(name = "owner_lecturer_id", nullable = false)
    private Long ownerLecturerId;

    @Column(name = "input_type", nullable = false, length = 32)
    private String inputType;

    @Column(name = "tts_enabled", nullable = false)
    private boolean ttsEnabled;

    @Column(name = "manual_text_sha256", length = 64, columnDefinition = "CHAR(64)")
    private String manualTextSha256;

    @Column(name = "original_audio_asset_id")
    private Long originalAudioAssetId;

    @Column(name = "generated_audio_asset_id")
    private Long generatedAudioAssetId;

    @Column(name = "active_audio_asset_id")
    private Long activeAudioAssetId;

    @Column(name = "current_stt_artifact_id")
    private Long currentSttArtifactId;

    @Column(name = "current_transcript_revision_id")
    private Long currentTranscriptRevisionId;

    @Column(name = "current_tts_artifact_id")
    private Long currentTtsArtifactId;

    @Column(name = "current_stt_operation", nullable = false, length = 16)
    private String currentSttOperation = SpeakingPromptAiContract.Operation.STT.code();

    @Column(name = "current_tts_operation", nullable = false, length = 16)
    private String currentTtsOperation = SpeakingPromptAiContract.Operation.TTS.code();

    @Column(name = "transcript_status", nullable = false, length = 32)
    private String transcriptStatus = STATUS_IDLE;

    @Column(name = "audio_sync_status", nullable = false, length = 32)
    private String audioSyncStatus = STATUS_IDLE;

    @Column(name = "lecturer_transcript_confirmed_at")
    private LocalDateTime lecturerTranscriptConfirmedAt;

    @Column(name = "source_revision", nullable = false)
    private Long sourceRevision = 0L;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion = 0L;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected SpeakingPromptSource() {
    }

    public static SpeakingPromptSource manualText(
            Long draftId,
            String questionClientId,
            Long ownerLecturerId,
            String manualTextSha256,
            boolean ttsEnabled,
            Long actorId) {
        SpeakingPromptSource source = base(
                draftId, questionClientId, ownerLecturerId, actorId);
        source.inputType = INPUT_MANUAL_TEXT;
        source.ttsEnabled = ttsEnabled;
        source.manualTextSha256 = requiredHash(manualTextSha256);
        source.sourceRevision = 1L;
        source.audioSyncStatus = ttsEnabled ? STATUS_STALE : STATUS_IDLE;
        return source;
    }

    public static SpeakingPromptSource audioUpload(
            Long draftId,
            String questionClientId,
            Long ownerLecturerId,
            Long originalAudioAssetId,
            Long actorId) {
        SpeakingPromptSource source = base(
                draftId, questionClientId, ownerLecturerId, actorId);
        source.inputType = INPUT_AUDIO_UPLOAD;
        source.originalAudioAssetId = Objects.requireNonNull(
                originalAudioAssetId, "originalAudioAssetId");
        source.activeAudioAssetId = originalAudioAssetId;
        source.sourceRevision = 1L;
        source.transcriptStatus = STATUS_QUEUED;
        return source;
    }

    public static SpeakingPromptSource emptyAudioMode(
            Long draftId,
            String questionClientId,
            Long ownerLecturerId,
            Long actorId) {
        SpeakingPromptSource source = base(
                draftId, questionClientId, ownerLecturerId, actorId);
        source.inputType = INPUT_AUDIO_UPLOAD;
        source.ttsEnabled = false;
        source.sourceRevision = 1L;
        source.transcriptStatus = STATUS_IDLE;
        source.audioSyncStatus = STATUS_IDLE;
        return source;
    }

    private static SpeakingPromptSource base(
            Long draftId,
            String questionClientId,
            Long ownerLecturerId,
            Long actorId) {
        SpeakingPromptSource source = new SpeakingPromptSource();
        source.draftId = Objects.requireNonNull(draftId, "draftId");
        source.questionClientId = requiredClientId(questionClientId);
        source.ownerLecturerId = Objects.requireNonNull(
                ownerLecturerId, "ownerLecturerId");
        source.createdBy = Objects.requireNonNull(actorId, "actorId");
        source.updatedBy = actorId;
        return source;
    }

    public void requireExpectedRevision(long expectedRevision) {
        if (sourceRevision == null || sourceRevision != expectedRevision) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Speaking prompt source revision is stale.");
        }
    }

    public long switchToManualText(
            String exactManualTextSha256,
            boolean enabled,
            Long actorId) {
        inputType = INPUT_MANUAL_TEXT;
        ttsEnabled = enabled;
        manualTextSha256 = requiredHash(exactManualTextSha256);
        /*
         * The lecturer upload remains the retained inactive source. Switching
         * modes changes current attachment state only; TTS must never replace
         * or erase the original-audio identity.
         */
        currentSttArtifactId = null;
        currentTranscriptRevisionId = null;
        currentTtsArtifactId = null;
        activeAudioAssetId = null;
        lecturerTranscriptConfirmedAt = null;
        transcriptStatus = STATUS_IDLE;
        audioSyncStatus = enabled ? STATUS_STALE : STATUS_IDLE;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public long switchToAudioUpload(Long assetId, Long actorId) {
        inputType = INPUT_AUDIO_UPLOAD;
        ttsEnabled = false;
        originalAudioAssetId = Objects.requireNonNull(assetId, "assetId");
        activeAudioAssetId = assetId;
        currentSttArtifactId = null;
        currentTranscriptRevisionId = null;
        currentTtsArtifactId = null;
        lecturerTranscriptConfirmedAt = null;
        transcriptStatus = STATUS_QUEUED;
        audioSyncStatus = STATUS_IDLE;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public long selectRetainedOriginalAudio(Long actorId) {
        if (INPUT_AUDIO_UPLOAD.equals(inputType)) {
            return sourceRevision;
        }
        inputType = INPUT_AUDIO_UPLOAD;
        ttsEnabled = false;
        activeAudioAssetId = originalAudioAssetId;
        currentSttArtifactId = null;
        currentTranscriptRevisionId = null;
        currentTtsArtifactId = null;
        lecturerTranscriptConfirmedAt = null;
        transcriptStatus = originalAudioAssetId == null
                ? STATUS_IDLE
                : STATUS_STALE;
        audioSyncStatus = STATUS_IDLE;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public long markManualConfigurationChanged(
            String exactManualTextSha256,
            boolean enabled,
            Long actorId) {
        if (!INPUT_MANUAL_TEXT.equals(inputType)) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Speaking prompt is not in manual-text mode.");
        }
        ttsEnabled = enabled;
        manualTextSha256 = requiredHash(exactManualTextSha256);
        currentTtsArtifactId = null;
        activeAudioAssetId = null;
        audioSyncStatus = enabled ? STATUS_STALE : STATUS_IDLE;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public void markSttQueued(Long artifactId, Long actorId) {
        if (!INPUT_AUDIO_UPLOAD.equals(inputType) || originalAudioAssetId == null) {
            throw new SpeakingPromptAuthoringConflictException(
                    "STT is only valid for a bound original audio upload.");
        }
        currentSttArtifactId = Objects.requireNonNull(artifactId, "artifactId");
        currentTranscriptRevisionId = null;
        transcriptStatus = STATUS_QUEUED;
        lecturerTranscriptConfirmedAt = null;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
    }

    public void markSttProcessing() {
        transcriptStatus = STATUS_PROCESSING;
    }

    public void attachSttArtifact(
            Long artifactId,
            Long transcriptRevisionId,
            boolean needsReview) {
        if (!INPUT_AUDIO_UPLOAD.equals(inputType) || originalAudioAssetId == null) {
            throw new SpeakingPromptAuthoringConflictException(
                    "STT result no longer matches the current source mode.");
        }
        currentSttArtifactId = Objects.requireNonNull(artifactId, "artifactId");
        currentTranscriptRevisionId = Objects.requireNonNull(
                transcriptRevisionId, "transcriptRevisionId");
        activeAudioAssetId = originalAudioAssetId;
        transcriptStatus = needsReview ? STATUS_NEEDS_REVIEW : STATUS_READY;
        lecturerTranscriptConfirmedAt = needsReview
                ? null
                : lecturerTranscriptConfirmedAt;
    }

    public void markTtsQueued(Long artifactId, Long actorId) {
        if (!INPUT_MANUAL_TEXT.equals(inputType) || !ttsEnabled) {
            throw new SpeakingPromptAuthoringConflictException(
                    "TTS is only valid for enabled manual-text mode.");
        }
        generatedAudioAssetId = null;
        currentTtsArtifactId = Objects.requireNonNull(artifactId, "artifactId");
        activeAudioAssetId = null;
        audioSyncStatus = STATUS_QUEUED;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
    }

    public void markTtsProcessing() {
        audioSyncStatus = STATUS_PROCESSING;
    }

    public void attachTtsArtifact(Long artifactId, Long generatedAssetId) {
        if (!INPUT_MANUAL_TEXT.equals(inputType) || !ttsEnabled) {
            throw new SpeakingPromptAuthoringConflictException(
                    "TTS result no longer matches the current source mode.");
        }
        currentTtsArtifactId = Objects.requireNonNull(artifactId, "artifactId");
        generatedAudioAssetId = Objects.requireNonNull(
                generatedAssetId, "generatedAssetId");
        activeAudioAssetId = generatedAssetId;
        audioSyncStatus = STATUS_READY;
    }

    public void markOperationFailure(
            SpeakingPromptAiContract.Operation operation,
            boolean retryable) {
        String status = retryable ? STATUS_FAILED_RETRYABLE : STATUS_FAILED_FINAL;
        if (operation == SpeakingPromptAiContract.Operation.STT) {
            transcriptStatus = status;
        } else {
            audioSyncStatus = status;
            activeAudioAssetId = null;
        }
    }

    public long markOperationCancelled(
            SpeakingPromptAiContract.Operation operation,
            Long actorId) {
        if (operation == SpeakingPromptAiContract.Operation.STT) {
            currentSttArtifactId = null;
            currentTranscriptRevisionId = null;
            transcriptStatus = STATUS_CANCELLED;
            lecturerTranscriptConfirmedAt = null;
        } else {
            currentTtsArtifactId = null;
            audioSyncStatus = STATUS_CANCELLED;
            activeAudioAssetId = null;
        }
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public long unlinkOriginalAudio(Long actorId) {
        if (!INPUT_AUDIO_UPLOAD.equals(inputType)) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Speaking prompt is not in original-audio mode.");
        }
        originalAudioAssetId = null;
        activeAudioAssetId = null;
        currentSttArtifactId = null;
        currentTranscriptRevisionId = null;
        lecturerTranscriptConfirmedAt = null;
        transcriptStatus = STATUS_IDLE;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public long recordTranscriptEdit(
            Long transcriptRevisionId,
            Long actorId,
            boolean confirmed,
            LocalDateTime now) {
        if (!INPUT_AUDIO_UPLOAD.equals(inputType) || currentSttArtifactId == null) {
            throw new SpeakingPromptAuthoringConflictException(
                    "No current prompt transcript can be edited.");
        }
        currentTranscriptRevisionId = Objects.requireNonNull(
                transcriptRevisionId, "transcriptRevisionId");
        lecturerTranscriptConfirmedAt = confirmed
                ? Objects.requireNonNull(now, "now")
                : null;
        transcriptStatus = STATUS_READY;
        updatedBy = Objects.requireNonNull(actorId, "actorId");
        return incrementRevision();
    }

    public boolean currentFor(
            SpeakingPromptAiTask task,
            SpeakingPromptAiArtifact artifact) {
        if (task == null
                || artifact == null
                || !Objects.equals(id, task.getSourceId())
                || !Objects.equals(ownerLecturerId, task.getOwnerLecturerId())
                || !Objects.equals(ownerLecturerId, artifact.getOwnerLecturerId())
                || !Objects.equals(task.getArtifactId(), artifact.getId())
                || !Objects.equals(
                        task.getOperationFingerprint(),
                        artifact.getOperationFingerprint())
                || !Objects.equals(sourceRevision, task.getExpectedSourceRevision())) {
            return false;
        }
        if (SpeakingPromptAiContract.Operation.STT.code().equals(task.getOperation())) {
            return currentForArtifact(artifact)
                    && SpeakingPromptAiContract.Operation.STT.code().equals(
                            task.getOperation());
        }
        return currentForArtifact(artifact)
                && SpeakingPromptAiContract.Operation.TTS.code().equals(
                        task.getOperation());
    }

    /**
     * Source-local attachment currentness. The artifact is owner-scoped and
     * reusable, so task creator/source revision are intentionally not part of
     * this predicate.
     */
    public boolean currentForArtifact(SpeakingPromptAiArtifact artifact) {
        if (artifact == null
                || !Objects.equals(ownerLecturerId, artifact.getOwnerLecturerId())) {
            return false;
        }
        if (SpeakingPromptAiContract.Operation.STT.code().equals(
                artifact.getOperation())) {
            return INPUT_AUDIO_UPLOAD.equals(inputType)
                    && Objects.equals(currentSttArtifactId, artifact.getId())
                    && Objects.equals(
                            originalAudioAssetId, artifact.getInputAudioAssetId());
        }
        return SpeakingPromptAiContract.Operation.TTS.code().equals(
                    artifact.getOperation())
                && INPUT_MANUAL_TEXT.equals(inputType)
                && ttsEnabled
                && Objects.equals(currentTtsArtifactId, artifact.getId());
    }

    private long incrementRevision() {
        sourceRevision = sourceRevision == null ? 1L : sourceRevision + 1L;
        return sourceRevision;
    }

    private static String requiredClientId(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException(
                    "Question client ID must be present and at most 100 characters.");
        }
        return value;
    }

    private static String requiredHash(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(
                java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 identity is invalid.");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getDraftId() { return draftId; }
    public String getQuestionClientId() { return questionClientId; }
    public Long getOwnerLecturerId() { return ownerLecturerId; }
    public String getInputType() { return inputType; }
    public boolean isTtsEnabled() { return ttsEnabled; }
    String getManualTextSha256() { return manualTextSha256; }
    Long getOriginalAudioAssetId() { return originalAudioAssetId; }
    Long getGeneratedAudioAssetId() { return generatedAudioAssetId; }
    Long getActiveAudioAssetId() { return activeAudioAssetId; }
    public Long getCurrentSttArtifactId() { return currentSttArtifactId; }
    public Long getCurrentTranscriptRevisionId() {
        return currentTranscriptRevisionId;
    }
    public Long getCurrentTtsArtifactId() { return currentTtsArtifactId; }
    public String getTranscriptStatus() { return transcriptStatus; }
    public String getAudioSyncStatus() { return audioSyncStatus; }
    public LocalDateTime getLecturerTranscriptConfirmedAt() {
        return lecturerTranscriptConfirmedAt;
    }
    public Long getSourceRevision() { return sourceRevision; }
    public Long getLockVersion() { return lockVersion; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "SpeakingPromptSource{id=" + id
                + ", draftId=" + draftId
                + ", inputType='" + inputType + '\''
                + ", sourceRevision=" + sourceRevision
                + '}';
    }
}
