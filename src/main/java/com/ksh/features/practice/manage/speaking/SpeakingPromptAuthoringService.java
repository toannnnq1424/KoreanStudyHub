package com.ksh.features.practice.manage.speaking;

import com.ksh.entities.LecturerAsset;
import com.ksh.features.practice.governance.PracticeAction;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class SpeakingPromptAuthoringService {

    private final SpeakingPromptDraftAuthority draftAuthority;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptAiTaskRepository taskRepository;
    private final SpeakingPromptTranscriptRevisionRepository revisionRepository;
    private final SpeakingPromptFingerprintService fingerprintService;
    private final SpeakingPromptAssetService assetService;
    private final SpeakingPromptAuthoringAiProperties properties;

    public SpeakingPromptAuthoringService(
            SpeakingPromptDraftAuthority draftAuthority,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptAiTaskRepository taskRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository,
            SpeakingPromptFingerprintService fingerprintService,
            SpeakingPromptAssetService assetService,
            SpeakingPromptAuthoringAiProperties properties) {
        this.draftAuthority = draftAuthority;
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.fingerprintService = fingerprintService;
        this.assetService = assetService;
        this.properties = properties;
    }

    /**
     * Updates the sole mutable manual prompt authority and its source hash in
     * one transaction. It never creates a provider task.
     */
    @Transactional
    public SourceResult saveManualPrompt(SaveManualPrompt command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = sourceRepository
                .findByDraftAndClientForUpdate(
                        command.draftId(), command.questionClientId())
                .orElse(null);
        Long previousSttArtifactId =
                source == null ? null : source.getCurrentSttArtifactId();
        Long previousTtsArtifactId =
                source == null ? null : source.getCurrentTtsArtifactId();
        SpeakingPromptAuthoringAiProperties.TtsConfig selection =
                selectedTtsConfig(
                        command.voiceCode(),
                        command.speed(),
                        command.outputFormat());
        SpeakingPromptDraftAuthority.DraftAuthoringOptions previousOptions =
                draftAuthority.authoringOptions(authorized);
        String exactHash = fingerprintService.exactTextSha256(command.promptText());
        if (source == null) {
            requireInitialRevision(command.expectedSourceRevision());
            source = SpeakingPromptSource.manualText(
                    command.draftId(),
                    command.questionClientId(),
                    authorized.ownerId(),
                    exactHash,
                    command.ttsEnabled(),
                    command.actorId());
        } else {
            requireSourceAuthority(source, authorized.ownerId());
            source.requireExpectedRevision(command.expectedSourceRevision());
            boolean sameManualSource = SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                    source.getInputType());
            boolean unchanged = sameManualSource
                    && source.isTtsEnabled() == command.ttsEnabled()
                    && Objects.equals(source.getManualTextSha256(), exactHash);
            boolean optionsChanged = !sameTtsSelection(
                    previousOptions, selection)
                    || !currentTtsArtifactMatches(source, selection);
            if (!unchanged && sameManualSource
                    && Objects.equals(source.getManualTextSha256(), exactHash)) {
                source.markManualConfigurationChanged(
                        exactHash, command.ttsEnabled(), command.actorId());
            } else if (!unchanged) {
                source.switchToManualText(
                        exactHash, command.ttsEnabled(), command.actorId());
            } else if (optionsChanged && source.isTtsEnabled()) {
                source.markManualConfigurationChanged(
                        exactHash, true, command.actorId());
            }
        }
        draftAuthority.replacePromptAndAuthoringOptions(
                authorized,
                command.promptText(),
                command.ttsEnabled(),
                selection.voice(),
                selection.speed(),
                selection.outputFormat());
        SpeakingPromptSource saved = sourceRepository.saveAndFlush(source);
        cancelDetachedTask(
                saved.getOwnerLecturerId(),
                previousSttArtifactId,
                saved.getCurrentSttArtifactId(),
                SpeakingPromptAiContract.Operation.STT);
        cancelDetachedTask(
                saved.getOwnerLecturerId(),
                previousTtsArtifactId,
                saved.getCurrentTtsArtifactId(),
                SpeakingPromptAiContract.Operation.TTS);
        return SourceResult.from(saved);
    }

    /**
     * Selects a previously retained lecturer upload without creating a task.
     * A fresh/changed upload remains the only path that enqueues STT.
     */
    @Transactional
    public SourceResult selectAudioMode(SourceCommand command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = sourceRepository
                .findByDraftAndClientForUpdate(
                        command.draftId(), command.questionClientId())
                .orElse(null);
        Long previousSttArtifactId =
                source == null ? null : source.getCurrentSttArtifactId();
        Long previousTtsArtifactId =
                source == null ? null : source.getCurrentTtsArtifactId();
        if (source == null) {
            requireInitialRevision(command.expectedSourceRevision());
            source = SpeakingPromptSource.emptyAudioMode(
                    command.draftId(),
                    command.questionClientId(),
                    authorized.ownerId(),
                    command.actorId());
        } else {
            requireSourceAuthority(source, authorized.ownerId());
            source.requireExpectedRevision(command.expectedSourceRevision());
            source.selectRetainedOriginalAudio(command.actorId());
        }
        draftAuthority.selectAudioAuthoringMode(authorized);
        SpeakingPromptSource saved = sourceRepository.saveAndFlush(source);
        cancelDetachedTask(
                saved.getOwnerLecturerId(),
                previousSttArtifactId,
                saved.getCurrentSttArtifactId(),
                SpeakingPromptAiContract.Operation.STT);
        cancelDetachedTask(
                saved.getOwnerLecturerId(),
                previousTtsArtifactId,
                saved.getCurrentTtsArtifactId(),
                SpeakingPromptAiContract.Operation.TTS);
        return SourceResult.from(saved);
    }

    @Transactional(readOnly = true)
    public void requireUploadAllowed(UploadOriginalAudio command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorize(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.MATERIAL_MANAGE);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource current = sourceRepository
                .findByDraftIdAndQuestionClientId(
                        command.draftId(), command.questionClientId())
                .orElse(null);
        if (current == null) {
            requireInitialRevision(command.expectedSourceRevision());
        } else {
            requireSourceAuthority(current, authorized.ownerId());
            current.requireExpectedRevision(command.expectedSourceRevision());
        }
        properties.requireOperational(SpeakingPromptAiContract.Operation.STT);
    }

    @Transactional
    public EnqueueResult bindVerifiedOriginalUpload(
            UploadOriginalAudio command,
            SpeakingPromptAssetService.VerifiedOriginalUpload upload) {
        VerifiedOriginalAudioProof proof = new VerifiedOriginalAudioProof(
                upload.draftId(),
                upload.questionClientId(),
                upload.ownerId(),
                upload.assetId(),
                upload.verifiedAudio());
        return bindOriginalAudioAndEnqueueStt(
                new BindOriginalAudio(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        command.expectedSourceRevision(),
                        command.expectedDraftVersion(),
                        proof));
    }

    /**
     * Marks selected voice/speed/format changes stale before an explicit
     * Generate action. The options themselves remain draft-question data owned
     * by 13C3-02.
     */
    @Transactional
    public SourceResult markTtsConfigurationChanged(SourceCommand command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = requireSourceForUpdate(command);
        requireSourceAuthority(source, authorized.ownerId());
        SpeakingPromptDraftAuthority.DraftPrompt prompt =
                new SpeakingPromptDraftAuthority.DraftPrompt(
                        authorized.draft().getId(),
                        authorized.ownerId(),
                        command.questionClientId(),
                        authorized.question().path("prompt").asText(""));
        String exactHash = fingerprintService.exactTextSha256(prompt.promptText());
        if (!Objects.equals(exactHash, source.getManualTextSha256())
                || !SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                        source.getInputType())) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Manual prompt text and source identity are not synchronized.");
        }
        source.markManualConfigurationChanged(
                exactHash, source.isTtsEnabled(), command.actorId());
        return SourceResult.from(sourceRepository.save(source));
    }

    /**
     * Binds a verified original lecturer asset and idempotently prepares one
     * owner-scoped STT task. It never creates or replaces prompt audio.
     */
    public VerifiedOriginalAudioProof verifyOriginalAudio(
            VerifyOriginalAudio command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorize(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.MATERIAL_MANAGE);
        SpeakingPromptAiContract.VerifiedAudio verified =
                assetService.loadVerifiedOriginal(
                        command.draftId(),
                        authorized.ownerId(),
                        command.assetId(),
                        command.questionClientId());
        return new VerifiedOriginalAudioProof(
                command.draftId(),
                command.questionClientId(),
                authorized.ownerId(),
                command.assetId(),
                verified);
    }

    @Transactional
    public EnqueueResult bindOriginalAudioAndEnqueueStt(BindOriginalAudio command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.MATERIAL_MANAGE);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = sourceRepository
                .findByDraftAndClientForUpdate(
                        command.draftId(), command.questionClientId())
                .orElse(null);
        if (source == null) {
            requireInitialRevision(command.expectedSourceRevision());
        } else {
            requireSourceAuthority(source, authorized.ownerId());
            source.requireExpectedRevision(command.expectedSourceRevision());
        }
        VerifiedOriginalAudioProof proof =
                Objects.requireNonNull(command.proof(), "proof");
        proof.requireMatches(
                command.draftId(),
                command.questionClientId(),
                authorized.ownerId());
        SpeakingPromptAiContract.VerifiedAudio verifiedAudio =
                proof.verifiedAudio();
        LecturerAsset asset = assetService.bindVerifiedOriginalAsset(
                command.draftId(),
                authorized.ownerId(),
                proof.assetId(),
                command.questionClientId(),
                verifiedAudio);
        properties.requireOperational(SpeakingPromptAiContract.Operation.STT);
        if (source == null) {
            source = SpeakingPromptSource.audioUpload(
                    command.draftId(),
                    command.questionClientId(),
                    authorized.ownerId(),
                    asset.getId(),
                    command.actorId());
            source = sourceRepository.saveAndFlush(source);
        } else {
            boolean sameInput = SpeakingPromptSource.INPUT_AUDIO_UPLOAD.equals(
                    source.getInputType())
                    && Objects.equals(source.getOriginalAudioAssetId(), asset.getId());
            if (!sameInput) {
                source.switchToAudioUpload(asset.getId(), command.actorId());
            }
        }
        draftAuthority.selectAudioAuthoringMode(authorized);

        SpeakingPromptAuthoringAiProperties.SttConfig config =
                properties.sttConfig();
        String fingerprint = fingerprintService.sttFingerprint(
                authorized.ownerId(),
                asset.getId(),
                verifiedAudio.sha256(),
                config);
        artifactRepository.insertSttIfAbsent(
                authorized.ownerId(),
                fingerprint,
                source.getSourceRevision(),
                verifiedAudio.sha256(),
                asset.getId(),
                config.provider(),
                config.model(),
                config.language(),
                SpeakingPromptAiContract.CONTRACT_VERSION,
                config.purposeCode(),
                config.retentionCode());
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByFingerprintForUpdate(
                        authorized.ownerId(),
                        SpeakingPromptAiContract.Operation.STT.code(),
                        fingerprint)
                .orElseThrow(() -> new IllegalStateException(
                        "STT artifact was not available after idempotent insertion."));
        validateSttArtifact(artifact, verifiedAudio.sha256(), asset.getId());
        if (artifact.isReady()) {
            if (!source.currentForArtifact(artifact)
                    || source.getCurrentTranscriptRevisionId() == null) {
                source.markSttQueued(artifact.getId(), command.actorId());
                SpeakingPromptTranscriptRevision providerRevision =
                        requireProviderRevision(artifact);
                source.attachSttArtifact(
                        artifact.getId(),
                        providerRevision.getId(),
                        SpeakingPromptSource.STATUS_NEEDS_REVIEW.equals(
                                artifact.getArtifactStatus()));
            }
            sourceRepository.save(source);
            return EnqueueResult.ready(source, artifact);
        }
        if (!source.currentForArtifact(artifact)) {
            source.markSttQueued(artifact.getId(), command.actorId());
        }
        sourceRepository.save(source);
        return enqueueTask(source, artifact, command.actorId());
    }

    /**
     * Explicit Generate/Regenerate command. A save/toggle/GET path cannot call
     * this method accidentally because it requires current mode and revision.
     */
    @Transactional
    public EnqueueResult requestTts(GenerateTts command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = requireSourceForUpdate(
                new SourceCommand(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        command.expectedSourceRevision(),
                        command.expectedDraftVersion()));
        requireSourceAuthority(source, authorized.ownerId());
        if (!SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(source.getInputType())
                || !source.isTtsEnabled()) {
            throw new SpeakingPromptAuthoringConflictException(
                    "TTS is not enabled for the current manual prompt.");
        }
        String promptText = authorized.question().path("prompt").asText("");
        String exactHash = fingerprintService.exactTextSha256(promptText);
        if (!Objects.equals(source.getManualTextSha256(), exactHash)) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Manual prompt text and source identity are not synchronized.");
        }

        SpeakingPromptAuthoringAiProperties.TtsConfig config =
                selectedTtsConfig(command);
        if (!sameTtsSelection(
                draftAuthority.authoringOptions(authorized), config)) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Tùy chọn audio AI không khớp bản nháp đã lưu.");
        }
        String fingerprint = fingerprintService.ttsFingerprint(
                authorized.ownerId(), promptText, config);
        String nfcHash = SpeakingPromptAiContract.unicodeNfcUtf8Sha256(promptText);
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByFingerprintForUpdate(
                        authorized.ownerId(),
                        SpeakingPromptAiContract.Operation.TTS.code(),
                        fingerprint)
                .orElse(null);
        if (artifact != null) {
            validateTtsArtifact(artifact, nfcHash, config);
            EnqueueResult reusable = reuseExistingTtsOutcome(
                    source, artifact, command.actorId());
            if (reusable != null) {
                return reusable;
            }
        }
        properties.requireOperational(SpeakingPromptAiContract.Operation.TTS);
        if (artifact == null) {
            artifactRepository.insertTtsIfAbsent(
                    authorized.ownerId(),
                    fingerprint,
                    source.getSourceRevision(),
                    nfcHash,
                    config.provider(),
                    config.model(),
                    config.language(),
                    config.voice(),
                    config.speed(),
                    config.outputFormat(),
                    SpeakingPromptAiContract.CONTRACT_VERSION,
                    config.purposeCode(),
                    config.retentionCode());
            artifact = artifactRepository
                    .findByFingerprintForUpdate(
                            authorized.ownerId(),
                            SpeakingPromptAiContract.Operation.TTS.code(),
                            fingerprint)
                    .orElseThrow(() -> new IllegalStateException(
                            "TTS artifact was not available after idempotent insertion."));
            validateTtsArtifact(artifact, nfcHash, config);
        }
        preparePendingTtsSource(source, artifact, command.actorId());
        sourceRepository.save(source);
        return enqueueTask(source, artifact, command.actorId());
    }

    @Transactional
    public RetryResult retryCurrentOperation(RetryCommand command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = requireSourceForUpdate(
                new SourceCommand(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        command.expectedSourceRevision(),
                        command.expectedDraftVersion()));
        requireSourceAuthority(source, authorized.ownerId());
        Long artifactId = command.operation() == SpeakingPromptAiContract.Operation.STT
                ? source.getCurrentSttArtifactId()
                : source.getCurrentTtsArtifactId();
        if (artifactId == null) {
            return new RetryResult(false, 0L, "not_retryable");
        }
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByIdForUpdate(artifactId)
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking prompt artifact is missing."));
        if (!source.currentForArtifact(artifact)) {
            return new RetryResult(false, 0L, "not_retryable");
        }
        String sourceStatus =
                command.operation() == SpeakingPromptAiContract.Operation.STT
                        ? source.getTranscriptStatus()
                        : source.getAudioSyncStatus();
        if (command.operation() == SpeakingPromptAiContract.Operation.STT
                && SpeakingPromptSource.STATUS_NEEDS_REVIEW.equals(sourceStatus)
                && artifact.isReady()) {
            return new RetryResult(false, 0L, "needs_review");
        }
        if (!SpeakingPromptSource.STATUS_FAILED_RETRYABLE.equals(sourceStatus)) {
            return new RetryResult(false, 0L, "not_retryable");
        }
        requireRetryProviderSnapshot(command.operation(), artifact);
        if (command.operation() == SpeakingPromptAiContract.Operation.TTS) {
            String promptText = authorized.question().path("prompt").asText("");
            if (!Objects.equals(
                        source.getManualTextSha256(),
                        fingerprintService.exactTextSha256(promptText))
                    || !Objects.equals(
                        artifact.getInputSha256(),
                        SpeakingPromptAiContract.unicodeNfcUtf8Sha256(
                                promptText))) {
                throw new SpeakingPromptAuthoringConflictException(
                        "Manual prompt text and retry identity are not synchronized.");
            }
        }
        properties.requireOperational(command.operation());
        if (taskRepository.findActiveByFingerprint(
                source.getOwnerLecturerId(),
                artifact.getOperation(),
                artifact.getOperationFingerprint()).isPresent()) {
            return new RetryResult(false, 0L, "already_active");
        }
        List<SpeakingPromptAiTask> candidates =
                taskRepository.findLatestByFingerprint(
                        source.getOwnerLecturerId(),
                        artifact.getOperation(),
                        artifact.getOperationFingerprint(),
                        PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return new RetryResult(false, 0L, "not_retryable");
        }
        SpeakingPromptAiTask task = taskRepository.findByIdForUpdate(
                candidates.get(0).getId()).orElseThrow();
        if (!SpeakingPromptAiTask.STATUS_FAILED.equals(task.getTaskStatus())
                || task.getUpdatedAt() == null) {
            return new RetryResult(false, 0L, "not_retryable");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime allowedAt = task.getUpdatedAt().plus(
                properties.taskBounds().manualRetryCooldown());
        if (allowedAt.isAfter(now)) {
            long seconds = Math.max(
                    1L, java.time.Duration.between(now, allowedAt).getSeconds());
            return new RetryResult(false, seconds, "cooldown");
        }
        if (!retryableCategory(task.getPublicErrorCategory())
                || task.attemptsExhausted()) {
            return new RetryResult(false, 0L, "not_retryable");
        }
        if (taskRepository.countProviderAttemptsSince(
                source.getOwnerLecturerId(), now.minusHours(1))
                >= properties.taskBounds().maxRequestsPerLecturerPerHour()) {
            return new RetryResult(false, 0L, "quota");
        }
        int inserted = taskRepository.insertRetrySuccessor(
                artifact.getId(),
                source.getId(),
                source.getOwnerLecturerId(),
                artifact.getOperation(),
                source.getInputType(),
                artifact.getOperationFingerprint(),
                source.getSourceRevision(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                now,
                command.actorId());
        if (inserted != 1) {
            return new RetryResult(false, 0L, "already_active");
        }
        if (command.operation() == SpeakingPromptAiContract.Operation.STT) {
            source.markSttQueued(artifact.getId(), command.actorId());
        } else {
            source.markTtsQueued(artifact.getId(), command.actorId());
        }
        sourceRepository.save(source);
        return new RetryResult(true, 0L, "queued");
    }

    @Transactional
    public SourceResult unlinkCurrentOriginalAudio(SourceCommand command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.MATERIAL_MANAGE);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = requireSourceForUpdate(command);
        requireSourceAuthority(source, authorized.ownerId());
        Long assetId = source.getOriginalAudioAssetId();
        Long currentArtifactId = source.getCurrentSttArtifactId();
        if (assetId == null) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Câu hỏi hiện không có audio gốc để gỡ.");
        }
        assetService.unlinkOriginalBinding(
                source.getDraftId(),
                source.getOwnerLecturerId(),
                assetId,
                source.getQuestionClientId());
        source.unlinkOriginalAudio(command.actorId());
        draftAuthority.selectAudioAuthoringMode(authorized);
        SpeakingPromptSource saved = sourceRepository.saveAndFlush(source);
        cancelDetachedTask(
                saved.getOwnerLecturerId(),
                currentArtifactId,
                saved.getCurrentSttArtifactId(),
                SpeakingPromptAiContract.Operation.STT);
        assetService.queueIfUnreferenced(assetId);
        return SourceResult.from(saved);
    }

    @Transactional
    public boolean cancelCurrentOperation(CancelCommand command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = requireSourceForUpdate(
                new SourceCommand(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        command.expectedSourceRevision(),
                        command.expectedDraftVersion()));
        requireSourceAuthority(source, authorized.ownerId());
        Long currentArtifactId =
                command.operation() == SpeakingPromptAiContract.Operation.STT
                        ? source.getCurrentSttArtifactId()
                        : source.getCurrentTtsArtifactId();
        if (currentArtifactId == null) {
            return false;
        }
        /*
         * Detach this source first. The reusable artifact/task remains intact
         * when another exact source attachment exists; otherwise only the
         * now-orphaned active task is terminalized.
         */
        source.markOperationCancelled(command.operation(), command.actorId());
        sourceRepository.saveAndFlush(source);
        cancelDetachedTask(
                source.getOwnerLecturerId(),
                currentArtifactId,
                null,
                command.operation());
        return true;
    }

    private void cancelDetachedTask(
            Long ownerId,
            Long previousArtifactId,
            Long currentArtifactId,
            SpeakingPromptAiContract.Operation operation) {
        if (previousArtifactId == null
                || Objects.equals(previousArtifactId, currentArtifactId)) {
            return;
        }
        List<SpeakingPromptSource> attached =
                operation == SpeakingPromptAiContract.Operation.STT
                        ? sourceRepository
                            .findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(
                                    previousArtifactId)
                        : sourceRepository
                            .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(
                                    previousArtifactId);
        if (!attached.isEmpty()) {
            return;
        }
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByIdForUpdate(previousArtifactId)
                .orElse(null);
        if (artifact == null
                || !Objects.equals(ownerId, artifact.getOwnerLecturerId())
                || !operation.code().equals(artifact.getOperation())) {
            return;
        }
        List<SpeakingPromptSource> afterArtifactLock =
                operation == SpeakingPromptAiContract.Operation.STT
                        ? sourceRepository
                            .findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(
                                    previousArtifactId)
                        : sourceRepository
                            .findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(
                                    previousArtifactId);
        if (!afterArtifactLock.isEmpty()) {
            return;
        }
        SpeakingPromptAiTask active = taskRepository.findActiveByFingerprint(
                        ownerId,
                        operation.code(),
                        artifact.getOperationFingerprint())
                .orElse(null);
        if (active != null
                && Objects.equals(active.getArtifactId(), previousArtifactId)) {
            active.markCancelled(LocalDateTime.now());
            taskRepository.saveAndFlush(active);
        }
    }

    private EnqueueResult enqueueTask(
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact,
            Long actorId) {
        SpeakingPromptAiTask active = taskRepository.findActiveByFingerprint(
                        source.getOwnerLecturerId(),
                        artifact.getOperation(),
                        artifact.getOperationFingerprint())
                .orElse(null);
        if (active != null) {
            return EnqueueResult.pending(source, artifact, active);
        }
        List<SpeakingPromptAiTask> latest = taskRepository.findLatestByFingerprint(
                source.getOwnerLecturerId(),
                artifact.getOperation(),
                artifact.getOperationFingerprint(),
                PageRequest.of(0, 1));
        if (!latest.isEmpty()
                && SpeakingPromptAiTask.STATUS_FAILED.equals(
                        latest.get(0).getTaskStatus())) {
            SpeakingPromptAiTask failed = latest.get(0);
            source.markOperationFailure(
                    SpeakingPromptAiContract.Operation.STT.code().equals(
                            artifact.getOperation())
                            ? SpeakingPromptAiContract.Operation.STT
                            : SpeakingPromptAiContract.Operation.TTS,
                    failed.isRetryable());
            sourceRepository.save(source);
            return EnqueueResult.notQueued(source, artifact, failed);
        }
        taskRepository.insertQueuedIfNoActive(
                artifact.getId(),
                source.getId(),
                source.getOwnerLecturerId(),
                artifact.getOperation(),
                source.getInputType(),
                artifact.getOperationFingerprint(),
                source.getSourceRevision(),
                properties.taskBounds().maxAttempts(),
                actorId);
        SpeakingPromptAiTask task = taskRepository.findActiveByFingerprint(
                        source.getOwnerLecturerId(),
                        artifact.getOperation(),
                        artifact.getOperationFingerprint())
                .orElseThrow(() -> new IllegalStateException(
                        "Speaking prompt task was not available after idempotent insertion."));
        return EnqueueResult.pending(source, artifact, task);
    }

    private EnqueueResult reuseExistingTtsOutcome(
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact,
            Long actorId) {
        if (artifact.isReady() && artifact.getGeneratedAudioAssetId() != null) {
            if (!source.currentForArtifact(artifact)) {
                source.markTtsQueued(artifact.getId(), actorId);
            }
            assetService.linkExistingGeneratedAsset(
                    source.getDraftId(),
                    source.getOwnerLecturerId(),
                    source.getQuestionClientId(),
                    artifact.getGeneratedAudioAssetId());
            source.attachTtsArtifact(
                    artifact.getId(), artifact.getGeneratedAudioAssetId());
            sourceRepository.save(source);
            return EnqueueResult.ready(source, artifact);
        }
        SpeakingPromptAiTask active = taskRepository.findActiveByFingerprint(
                        source.getOwnerLecturerId(),
                        artifact.getOperation(),
                        artifact.getOperationFingerprint())
                .orElse(null);
        if (active != null) {
            preparePendingTtsSource(source, artifact, actorId);
            sourceRepository.save(source);
            return EnqueueResult.pending(source, artifact, active);
        }
        List<SpeakingPromptAiTask> latest = taskRepository
                .findLatestByFingerprint(
                        source.getOwnerLecturerId(),
                        artifact.getOperation(),
                        artifact.getOperationFingerprint(),
                        PageRequest.of(0, 1));
        if (!latest.isEmpty()
                && SpeakingPromptAiTask.STATUS_FAILED.equals(
                        latest.get(0).getTaskStatus())) {
            SpeakingPromptAiTask failed = latest.get(0);
            source.markOperationFailure(
                    SpeakingPromptAiContract.Operation.TTS,
                    failed.isRetryable());
            sourceRepository.save(source);
            return EnqueueResult.notQueued(source, artifact, failed);
        }
        return null;
    }

    private void preparePendingTtsSource(
            SpeakingPromptSource source,
            SpeakingPromptAiArtifact artifact,
            Long actorId) {
        assetService.retireGeneratedAssetBinding(
                source.getDraftId(), source.getQuestionClientId());
        source.markTtsQueued(artifact.getId(), actorId);
    }

    private SpeakingPromptSource requireSourceForUpdate(SourceCommand command) {
        SpeakingPromptSource source = sourceRepository
                .findByDraftAndClientForUpdate(
                        command.draftId(), command.questionClientId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Nguồn đề Nói chưa tồn tại."));
        source.requireExpectedRevision(command.expectedSourceRevision());
        return source;
    }

    private SpeakingPromptAuthoringAiProperties.TtsConfig selectedTtsConfig(
            GenerateTts command) {
        return selectedTtsConfig(
                command.voiceCode(), command.speed(), command.outputFormat());
    }

    private SpeakingPromptAuthoringAiProperties.TtsConfig selectedTtsConfig(
            String requestedVoice,
            BigDecimal requestedSpeed,
            String requestedFormat) {
        SpeakingPromptAuthoringAiProperties.TtsConfig base =
                properties.ttsConfig();
        String voice = requestedVoice == null ? base.voice() : requestedVoice;
        if (!base.voice().equals(voice)) {
            throw new IllegalArgumentException(
                    "Giọng đọc đã chọn không thuộc cấu hình được duyệt.");
        }
        BigDecimal speed = requestedSpeed == null
                ? base.speed()
                : requestedSpeed;
        boolean speedApproved = List.of(
                        new BigDecimal("0.75"),
                        BigDecimal.ONE,
                        new BigDecimal("1.25"),
                        base.speed())
                .stream()
                .anyMatch(candidate -> candidate.compareTo(speed) == 0);
        if (!speedApproved) {
            throw new IllegalArgumentException(
                    "Tốc độ đọc đã chọn không thuộc cấu hình được duyệt.");
        }
        return new SpeakingPromptAuthoringAiProperties.TtsConfig(
                base.enabled(),
                base.provider(),
                base.baseUrl(),
                base.apiKey(),
                base.model(),
                base.language(),
                voice,
                speed,
                requestedFormat == null
                        ? base.outputFormat()
                        : requestedFormat,
                base.maxInputCharacters(),
                base.purposeCode(),
                base.retentionCode(),
                base.bindingRevision(),
                base.providerProfileRevision(),
                base.maxOutputBytes(),
                base.maxOutputDuration(),
                base.connectTimeout(),
                base.readTimeout(),
                base.allowedOutputFormats(),
                base.allowedOutputMimeTypes());
    }

    private boolean currentTtsArtifactMatches(
            SpeakingPromptSource source,
            SpeakingPromptAuthoringAiProperties.TtsConfig selected) {
        if (source.getCurrentTtsArtifactId() == null) {
            return true;
        }
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findById(source.getCurrentTtsArtifactId())
                .orElse(null);
        return artifact != null
                && source.currentForArtifact(artifact)
                && ttsArtifactMatches(artifact, selected);
    }

    private static boolean sameTtsSelection(
            SpeakingPromptDraftAuthority.DraftAuthoringOptions previous,
            SpeakingPromptAuthoringAiProperties.TtsConfig selected) {
        return previous != null
                && Objects.equals(previous.voiceCode(), selected.voice())
                && previous.speed() != null
                && previous.speed().compareTo(selected.speed()) == 0
                && Objects.equals(previous.outputFormat(), selected.outputFormat());
    }

    private SpeakingPromptTranscriptRevision requireProviderRevision(
            SpeakingPromptAiArtifact artifact) {
        return revisionRepository
                .findFirstByArtifactIdAndRevisionSourceOrderByRevisionNumberDesc(
                        artifact.getId(),
                        SpeakingPromptTranscriptRevision.SOURCE_PROVIDER)
                .orElseThrow(() -> new IllegalStateException(
                        "Ready STT artifact has no immutable provider revision."));
    }

    private void requireRetryProviderSnapshot(
            SpeakingPromptAiContract.Operation operation,
            SpeakingPromptAiArtifact artifact) {
        boolean matches;
        if (operation == SpeakingPromptAiContract.Operation.STT) {
            SpeakingPromptAuthoringAiProperties.SttConfig config =
                    properties.sttConfig();
            matches = Objects.equals(config.provider(), artifact.getProviderCode())
                    && Objects.equals(config.model(), artifact.getModelCode())
                    && Objects.equals(config.language(), artifact.getLanguageTag())
                    && Objects.equals(
                            config.purposeCode(), artifact.getPurposeCode())
                    && Objects.equals(
                            config.retentionCode(), artifact.getRetentionCode());
        } else {
            SpeakingPromptAuthoringAiProperties.TtsConfig config =
                    properties.ttsConfig();
            matches = Objects.equals(config.provider(), artifact.getProviderCode())
                    && Objects.equals(config.model(), artifact.getModelCode())
                    && Objects.equals(config.language(), artifact.getLanguageTag())
                    && Objects.equals(
                            config.purposeCode(), artifact.getPurposeCode())
                    && Objects.equals(
                            config.retentionCode(), artifact.getRetentionCode())
                    && config.allowedOutputFormats().contains(
                            artifact.getOutputFormat());
        }
        if (!matches
                || !SpeakingPromptAiContract.CONTRACT_VERSION.equals(
                        artifact.getContractVersion())) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                    false,
                    null,
                    null);
        }
    }

    private static void validateSttArtifact(
            SpeakingPromptAiArtifact artifact,
            String inputSha256,
            Long inputAudioAssetId) {
        if (!SpeakingPromptAiContract.Operation.STT.code().equals(
                    artifact.getOperation())
                || !inputSha256.equals(artifact.getInputSha256())
                || !Objects.equals(
                        inputAudioAssetId, artifact.getInputAudioAssetId())) {
            throw new IllegalStateException(
                    "Existing STT artifact identity does not match the verified input.");
        }
    }

    private static void validateTtsArtifact(
            SpeakingPromptAiArtifact artifact,
            String inputSha256,
            SpeakingPromptAuthoringAiProperties.TtsConfig config) {
        if (!inputSha256.equals(artifact.getInputSha256())
                || !ttsArtifactMatches(artifact, config)) {
            throw new IllegalStateException(
                    "Existing TTS artifact identity does not match the current options.");
        }
    }

    static boolean ttsArtifactMatches(
            SpeakingPromptAiArtifact artifact,
            SpeakingPromptAuthoringAiProperties.TtsConfig config) {
        return artifact != null
                && config != null
                && SpeakingPromptAiContract.Operation.TTS.code().equals(
                        artifact.getOperation())
                && Objects.equals(config.provider(), artifact.getProviderCode())
                && Objects.equals(config.model(), artifact.getModelCode())
                && Objects.equals(config.language(), artifact.getLanguageTag())
                && Objects.equals(config.voice(), artifact.getVoiceCode())
                && artifact.getSpeed() != null
                && config.speed().compareTo(artifact.getSpeed()) == 0
                && Objects.equals(
                        config.outputFormat(), artifact.getOutputFormat())
                && Objects.equals(
                        SpeakingPromptAiContract.CONTRACT_VERSION,
                        artifact.getContractVersion())
                && Objects.equals(
                        config.purposeCode(), artifact.getPurposeCode())
                && Objects.equals(
                        config.retentionCode(), artifact.getRetentionCode());
    }

    private static void requireSourceAuthority(
            SpeakingPromptSource source,
            Long expectedOwnerId) {
        if (!Objects.equals(source.getOwnerLecturerId(), expectedOwnerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Nguồn đề Nói không thuộc chủ sở hữu bản nháp.");
        }
    }

    private static void requireInitialRevision(long expectedRevision) {
        if (expectedRevision != 0L) {
            throw new SpeakingPromptAuthoringConflictException(
                    "Speaking prompt source revision is stale.");
        }
    }

    private static boolean retryableCategory(String category) {
        if (category == null) {
            return false;
        }
        try {
            return switch (SpeakingPromptAiContract.PublicErrorCategory.valueOf(category)) {
                case RATE_LIMIT, TIMEOUT, TRANSPORT -> true;
                default -> false;
            };
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record SaveManualPrompt(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            String promptText,
            boolean ttsEnabled,
            String voiceCode,
            BigDecimal speed,
            String outputFormat) {
        public SaveManualPrompt(
                Long draftId,
                String questionClientId,
                Long actorId,
                long expectedSourceRevision,
                long expectedDraftVersion,
                String promptText,
                boolean ttsEnabled) {
            this(
                    draftId,
                    questionClientId,
                    actorId,
                    expectedSourceRevision,
                    expectedDraftVersion,
                    promptText,
                    ttsEnabled,
                    null,
                    null,
                    null);
        }

        @Override
        public String toString() {
            return "SaveManualPrompt{draftId=" + draftId
                    + ", questionClientId='" + questionClientId + '\''
                    + ", expectedSourceRevision=" + expectedSourceRevision
                    + ", promptTextLength="
                    + (promptText == null ? 0 : promptText.length())
                    + ", ttsEnabled=" + ttsEnabled
                    + '}';
        }
    }

    public record UploadOriginalAudio(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            org.springframework.web.multipart.MultipartFile file) {
        @Override
        public String toString() {
            return "UploadOriginalAudio{draftId=" + draftId
                    + ", questionClientId='" + questionClientId + '\''
                    + ", expectedSourceRevision=" + expectedSourceRevision
                    + ", filePresent=" + (file != null && !file.isEmpty())
                    + '}';
        }
    }

    public record SourceCommand(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion) {
    }

    public record VerifyOriginalAudio(
            Long draftId,
            String questionClientId,
            Long actorId,
            Long assetId) {
        @Override
        public String toString() {
            return "VerifyOriginalAudio{privateAssetSelected="
                    + (assetId != null) + '}';
        }
    }

    public record BindOriginalAudio(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            VerifiedOriginalAudioProof proof) {
    }

    public record GenerateTts(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            String voiceCode,
            BigDecimal speed,
            String outputFormat) {
    }

    public record RetryCommand(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            SpeakingPromptAiContract.Operation operation) {
    }

    public record CancelCommand(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            SpeakingPromptAiContract.Operation operation) {
    }

    public record SourceResult(
            Long sourceId,
            long sourceRevision,
            String inputType,
            boolean ttsEnabled,
            String transcriptStatus,
            String audioStatus) {
        static SourceResult from(SpeakingPromptSource source) {
            return new SourceResult(
                    source.getId(),
                    source.getSourceRevision(),
                    source.getInputType(),
                    source.isTtsEnabled(),
                    source.getTranscriptStatus(),
                    source.getAudioSyncStatus());
        }
    }

    public record EnqueueResult(
            Long sourceId,
            long sourceRevision,
            Long artifactId,
            Long taskId,
            String status,
            boolean reusedReady) {
        static EnqueueResult ready(
                SpeakingPromptSource source,
                SpeakingPromptAiArtifact artifact) {
            return new EnqueueResult(
                    source.getId(),
                    source.getSourceRevision(),
                    artifact.getId(),
                    null,
                    artifact.getArtifactStatus(),
                    true);
        }

        static EnqueueResult pending(
                SpeakingPromptSource source,
                SpeakingPromptAiArtifact artifact,
                SpeakingPromptAiTask task) {
            return new EnqueueResult(
                    source.getId(),
                    source.getSourceRevision(),
                    artifact.getId(),
                    task.getId(),
                    task.getTaskStatus(),
                    false);
        }

        static EnqueueResult notQueued(
                SpeakingPromptSource source,
                SpeakingPromptAiArtifact artifact,
                SpeakingPromptAiTask task) {
            return new EnqueueResult(
                    source.getId(),
                    source.getSourceRevision(),
                    artifact.getId(),
                    null,
                    task.isRetryable()
                            ? SpeakingPromptSource.STATUS_FAILED_RETRYABLE
                            : SpeakingPromptSource.STATUS_FAILED_FINAL,
                    false);
        }
    }

    public record RetryResult(
            boolean queued,
            long retryAfterSeconds,
            String status) {
    }

    /**
     * Opaque, short-lived proof that private audio passed the authoring
     * verifier outside the enqueue transaction. No byte/hash accessor is
     * exposed to controller/API packages.
     */
    public static final class VerifiedOriginalAudioProof {
        private final Long draftId;
        private final String questionClientId;
        private final Long ownerId;
        private final Long assetId;
        private final SpeakingPromptAiContract.VerifiedAudio verifiedAudio;

        private VerifiedOriginalAudioProof(
                Long draftId,
                String questionClientId,
                Long ownerId,
                Long assetId,
                SpeakingPromptAiContract.VerifiedAudio verifiedAudio) {
            this.draftId = Objects.requireNonNull(draftId, "draftId");
            this.questionClientId = Objects.requireNonNull(
                    questionClientId, "questionClientId");
            this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
            this.assetId = Objects.requireNonNull(assetId, "assetId");
            this.verifiedAudio = Objects.requireNonNull(
                    verifiedAudio, "verifiedAudio");
        }

        Long assetId() {
            return assetId;
        }

        SpeakingPromptAiContract.VerifiedAudio verifiedAudio() {
            return verifiedAudio;
        }

        private void requireMatches(
                Long expectedDraftId,
                String expectedQuestionClientId,
                Long expectedOwnerId) {
            if (!Objects.equals(draftId, expectedDraftId)
                    || !Objects.equals(
                            questionClientId, expectedQuestionClientId)
                    || !Objects.equals(ownerId, expectedOwnerId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Audio verification proof does not belong to this prompt source.");
            }
        }

        @Override
        public String toString() {
            return "VerifiedOriginalAudioProof{verified=true}";
        }
    }
}
