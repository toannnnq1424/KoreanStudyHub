package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.audio.PreparedSpeakingAudio;
import com.ksh.features.practice.service.audio.SpeakingAudioPreparationService;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class SpeakingAudioUploadService {

    private static final Logger log = LoggerFactory.getLogger(SpeakingAudioUploadService.class);
    private static final String COMPENSATION_FAILURE_EVENT = "Speaking audio activation compensation failed";
    private static final String PHYSICAL_DELETE_FAILURE_EVENT = "Speaking audio physical delete failed";

    private final SpeakingAudioPreparationService preparationService;
    private final PracticeSpeakingMediaService mediaService;
    private final SpeakingAudioStorage storage;

    public SpeakingAudioUploadService(SpeakingAudioPreparationService preparationService,
                                      PracticeSpeakingMediaService mediaService,
                                      SpeakingAudioStorage storage) {
        this.preparationService = preparationService;
        this.mediaService = mediaService;
        this.storage = storage;
    }

    public SpeakingAudioUploadResult uploadOrReplaceForOwner(
            Long userId,
            Long attemptId,
            Long questionId,
            InputStream input,
            Long declaredSize,
            String clientMime) {
        mediaService.validateUploadTargetForOwner(userId, attemptId, questionId);
        PreparedSpeakingAudio prepared = preparationService.prepare(input, declaredSize, clientMime);

        SpeakingMediaActivationResult activated;
        try {
            activated = mediaService.activateValidatedMediaForOwner(
                    userId, attemptId, questionId, prepared.toDescriptor());
        } catch (RuntimeException activationFailure) {
            compensatePreparedObject(prepared);
            throw activationFailure;
        }

        return safeUploadResult(activated);
    }

    public SpeakingAudioDeletionResult deleteForOwner(
            Long userId,
            Long attemptId,
            Long questionId,
            Long mediaId) {
        SpeakingMediaDeletionResult deleted = mediaService.markDeletedForOwner(
                userId, attemptId, questionId, mediaId);
        deleted.cleanup().ifPresent(this::deletePhysicalObjectBestEffort);
        return new SpeakingAudioDeletionResult(deleted.mediaId(), deleted.status());
    }

    private SpeakingAudioUploadResult safeUploadResult(SpeakingMediaActivationResult activated) {
        return new SpeakingAudioUploadResult(
                activated.mediaId(),
                activated.questionId(),
                activated.status(),
                activated.byteSize(),
                activated.durationMs(),
                activated.mimeType(),
                activated.lockVersion());
    }

    private void compensatePreparedObject(PreparedSpeakingAudio prepared) {
        if (prepared.storageProvider() != PracticeSpeakingStorageProvider.LOCAL) {
            log.warn(COMPENSATION_FAILURE_EVENT);
            return;
        }
        try {
            storage.delete(prepared.storageKey());
        } catch (RuntimeException ignored) {
            log.warn(COMPENSATION_FAILURE_EVENT);
        }
    }

    private void deletePhysicalObjectBestEffort(SpeakingMediaCleanupHandle cleanup) {
        if (cleanup.storageProvider() != PracticeSpeakingStorageProvider.LOCAL) {
            log.warn(PHYSICAL_DELETE_FAILURE_EVENT);
            return;
        }
        try {
            storage.delete(cleanup.storageKey());
        } catch (RuntimeException ignored) {
            log.warn(PHYSICAL_DELETE_FAILURE_EVENT);
        }
    }

    public record SpeakingAudioUploadResult(
            Long mediaId,
            Long questionId,
            PracticeSpeakingMediaStatus status,
            Long byteSize,
            Long durationMs,
            String mimeType,
            Long lockVersion
    ) {}

    public record SpeakingAudioDeletionResult(
            Long mediaId,
            PracticeSpeakingMediaStatus status
    ) {}
}
