package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.SpeakingAudioUploadService.SpeakingAudioDeletionResult;
import com.ksh.features.practice.service.SpeakingAudioUploadService.SpeakingAudioUploadResult;
import com.ksh.features.practice.service.PracticeSpeakingMediaCleanupProcessor.CleanupTaskProcessingResult;
import com.ksh.features.practice.service.PracticeSpeakingMediaCleanupProcessor.CleanupTaskProcessingResult.Outcome;
import com.ksh.features.practice.service.audio.PreparedSpeakingAudio;
import com.ksh.features.practice.service.audio.SpeakingAudioPreparationService;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeakingAudioUploadServiceTest {

    private static final Long USER_ID = 11L;
    private static final Long ATTEMPT_ID = 22L;
    private static final Long QUESTION_ID = 33L;
    private static final String SECRET_KEY = "learner-speaking/ready/LEARNER_AUDIO_KEY_SECRET_B2A";
    private static final String CLEANUP_SECRET_KEY = "LEARNER_AUDIO_CLEANUP_KEY_SECRET_B2A";
    private static final String SECRET_HASH = "a".repeat(64);

    private SpeakingAudioPreparationService preparationService;
    private PracticeSpeakingMediaService mediaService;
    private SpeakingAudioStorage storage;
    private PracticeSpeakingMediaCleanupTaskService cleanupTaskService;
    private PracticeSpeakingMediaCleanupProcessor cleanupProcessor;
    private SpeakingAudioUploadService service;

    @BeforeEach
    void setUp() {
        preparationService = mock(SpeakingAudioPreparationService.class);
        mediaService = mock(PracticeSpeakingMediaService.class);
        storage = mock(SpeakingAudioStorage.class);
        cleanupTaskService = mock(PracticeSpeakingMediaCleanupTaskService.class);
        cleanupProcessor = mock(PracticeSpeakingMediaCleanupProcessor.class);
        service = new SpeakingAudioUploadService(
                preparationService, mediaService, storage, cleanupTaskService, cleanupProcessor);
    }

    @Test
    void preflightFailureAvoidsPreparationActivationAndStorage() {
        RuntimeException primary = new jakarta.persistence.EntityNotFoundException("target unavailable");
        doThrow(primary).when(mediaService).validateUploadTargetForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID);

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isSameAs(primary);

        verifyNoInteractions(preparationService, storage);
        verify(mediaService, never()).activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void preparationFailureDoesNotActivateOrAddExtraCompensation() {
        RuntimeException primary = new IllegalStateException("preparation failed");
        when(preparationService.prepare(any(InputStream.class), eq(1L), eq("audio/webm")))
                .thenThrow(primary);

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isSameAs(primary);

        verify(mediaService, never()).activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(storage);
    }

    @Test
    void successfulActivationUsesPreparedDescriptorAndReturnsOnlySafeFields() {
        PreparedSpeakingAudio prepared = prepared(SECRET_KEY, SECRET_HASH);
        SpeakingMediaActivationResult activated = activated(101L, Optional.empty());
        when(preparationService.prepare(any(InputStream.class), eq(3L), eq("audio/webm")))
                .thenReturn(prepared);
        when(mediaService.activateValidatedMediaForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.toDescriptor()))
                .thenReturn(activated);

        SpeakingAudioUploadResult result = upload(new byte[]{1, 2, 3});

        assertThat(result.mediaId()).isEqualTo(101L);
        assertThat(result.questionId()).isEqualTo(QUESTION_ID);
        assertThat(result.status()).isEqualTo(PracticeSpeakingMediaStatus.READY);
        assertThat(result.byteSize()).isEqualTo(3L);
        assertThat(result.durationMs()).isEqualTo(1200L);
        assertThat(result.mimeType()).isEqualTo("audio/webm");
        assertThat(result.lockVersion()).isEqualTo(0L);
        assertThat(result.toString())
                .doesNotContain(SECRET_KEY)
                .doesNotContain(SECRET_HASH)
                .doesNotContain("LEARNER_AUDIO_PATH_SECRET_B2A");
        verify(storage, never()).delete(anyString());

        var ordered = inOrder(mediaService, preparationService);
        ordered.verify(mediaService).validateUploadTargetForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID);
        ordered.verify(preparationService).prepare(any(InputStream.class), eq(3L), eq("audio/webm"));
        ordered.verify(mediaService).activateValidatedMediaForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.toDescriptor());
    }

    @Test
    void activationFailureDeletesNewFinalObjectAndRethrowsPrimary() {
        PreparedSpeakingAudio prepared = prepared(SECRET_KEY, SECRET_HASH);
        RuntimeException primary = new IllegalStateException("activation failed");
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString())).thenReturn(prepared);
        when(mediaService.activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(primary);

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isSameAs(primary);

        verify(storage).delete(SECRET_KEY);
    }

    @Test
    void databaseFailureAndAttemptStatusRaceBothCompensatePreparedObject() {
        PreparedSpeakingAudio first = prepared("learner-speaking/ready/db-failure", "b".repeat(64));
        PreparedSpeakingAudio second = prepared("learner-speaking/ready/status-race", "c".repeat(64));
        RuntimeException dbFailure = new org.springframework.dao.DataIntegrityViolationException("flush failed");
        RuntimeException statusRace = new IllegalStateException("Speaking media can only be changed before submit.");
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString()))
                .thenReturn(first, second);
        when(mediaService.activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(dbFailure, statusRace);

        assertThatThrownBy(() -> upload(new byte[]{1})).isSameAs(dbFailure);
        assertThatThrownBy(() -> upload(new byte[]{2})).isSameAs(statusRace);

        verify(storage).delete(first.storageKey());
        verify(storage).delete(second.storageKey());
    }

    @Test
    void compensationFailurePreservesPrimaryWithoutSensitiveSuppressedException() {
        PreparedSpeakingAudio prepared = prepared(SECRET_KEY, SECRET_HASH);
        RuntimeException primary = new IllegalStateException("activation failed safely");
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString())).thenReturn(prepared);
        when(mediaService.activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(primary);
        doThrow(new IllegalStateException("LEARNER_AUDIO_PATH_SECRET_B2A/" + SECRET_KEY))
                .when(storage).delete(SECRET_KEY);
        when(cleanupTaskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY))
                .thenReturn(900L);

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isSameAs(primary)
                .hasMessage("activation failed safely")
                .satisfies(ex -> {
                    assertThat(ex.getSuppressed()).isEmpty();
                    assertThat(ex.getMessage())
                            .doesNotContain(SECRET_KEY)
                            .doesNotContain(SECRET_HASH)
                            .doesNotContain("LEARNER_AUDIO_PATH_SECRET_B2A");
                });
        verify(cleanupTaskService).enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY);
    }

    @Test
    void unsupportedPreparedProviderDoesNotUseLocalStorageAndPreservesPrimary() {
        PreparedSpeakingAudio prepared = new PreparedSpeakingAudio(
                PracticeSpeakingStorageProvider.OBJECT_STORAGE,
                CLEANUP_SECRET_KEY,
                "audio/webm",
                "webm",
                "opus",
                3L,
                1200L,
                SECRET_HASH);
        RuntimeException primary = new IllegalStateException("activation failed safely");
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString())).thenReturn(prepared);
        when(mediaService.activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(primary);

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isSameAs(primary)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(CLEANUP_SECRET_KEY));

        verifyNoInteractions(storage);
        verify(cleanupTaskService)
                .enqueueCompensationOrphan(PracticeSpeakingStorageProvider.OBJECT_STORAGE, CLEANUP_SECRET_KEY);
    }

    @Test
    void orphanTaskPersistenceFailureStillPreservesPrimaryActivationException() {
        PreparedSpeakingAudio prepared = prepared(SECRET_KEY, SECRET_HASH);
        RuntimeException primary = new IllegalStateException("activation failed safely");
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString())).thenReturn(prepared);
        when(mediaService.activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(primary);
        doThrow(new IllegalStateException("LEARNER_AUDIO_PATH_SECRET_B3A1/" + SECRET_KEY))
                .when(storage).delete(SECRET_KEY);
        when(cleanupTaskService.enqueueCompensationOrphan(PracticeSpeakingStorageProvider.LOCAL, SECRET_KEY))
                .thenThrow(new IllegalStateException("LEARNER_AUDIO_ERROR_SECRET_B3A1"));

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isSameAs(primary)
                .satisfies(ex -> assertThat(ex.getSuppressed()).isEmpty());
    }

    @Test
    void failureWhileMappingSafeResultAfterSuccessfulActivationDoesNotCompensate() {
        PreparedSpeakingAudio prepared = prepared(SECRET_KEY, SECRET_HASH);
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString())).thenReturn(prepared);
        when(mediaService.activateValidatedMediaForOwner(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(null);

        assertThatThrownBy(() -> upload(new byte[]{1}))
                .isInstanceOf(NullPointerException.class);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void retryCreatesTwoIndependentActivationsWithoutDedupOrCompensation() {
        PreparedSpeakingAudio first = prepared("OLD_READY_KEY_SECRET_B2A", SECRET_HASH);
        PreparedSpeakingAudio second = prepared("NEW_PREPARED_KEY_SECRET_B2A", SECRET_HASH);
        when(preparationService.prepare(any(InputStream.class), anyLong(), anyString()))
                .thenReturn(first, second);
        when(mediaService.activateValidatedMediaForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, first.toDescriptor()))
                .thenReturn(activated(201L, Optional.empty()));
        when(mediaService.activateValidatedMediaForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, second.toDescriptor()))
                .thenReturn(activated(202L, Optional.of(701L)));

        assertThat(upload(new byte[]{1}).mediaId()).isEqualTo(201L);
        assertThat(upload(new byte[]{1}).mediaId()).isEqualTo(202L);

        verify(mediaService, times(2)).activateValidatedMediaForOwner(
                eq(USER_ID), eq(ATTEMPT_ID), eq(QUESTION_ID), any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void unsupportedDeleteProviderKeepsLogicalSuccessWithoutUsingLocalStorage() {
        when(mediaService.markDeletedForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 251L))
                .thenReturn(new SpeakingMediaDeletionResult(
                        251L, PracticeSpeakingMediaStatus.DELETED, 801L));

        SpeakingAudioDeletionResult result = service.deleteForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 251L);

        assertThat(result.status()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(result.toString()).doesNotContain(CLEANUP_SECRET_KEY);
        verifyNoInteractions(storage);
        verify(cleanupProcessor).processTaskNow(801L);
    }

    @Test
    void deleteCommitsMetadataContractBeforePhysicalDeleteAndReturnsSafeResult() {
        when(mediaService.markDeletedForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 301L))
                .thenReturn(new SpeakingMediaDeletionResult(
                        301L, PracticeSpeakingMediaStatus.DELETED, 901L));

        SpeakingAudioDeletionResult result = service.deleteForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 301L);

        assertThat(result.status()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(result.toString()).doesNotContain(SECRET_KEY).doesNotContain(SECRET_HASH);
        var ordered = inOrder(mediaService, cleanupProcessor);
        ordered.verify(mediaService).markDeletedForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 301L);
        ordered.verify(cleanupProcessor).processTaskNow(901L);
    }

    @Test
    void metadataDeleteFailureOrEmptyCleanupDoesNotTouchStorage() {
        RuntimeException primary = new jakarta.persistence.EntityNotFoundException("not found");
        when(mediaService.markDeletedForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 401L))
                .thenThrow(primary);

        assertThatThrownBy(() -> service.deleteForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 401L))
                .isSameAs(primary);
        verifyNoInteractions(storage);

        when(mediaService.markDeletedForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 402L))
                .thenReturn(new SpeakingMediaDeletionResult(
                        402L, PracticeSpeakingMediaStatus.DELETED, null));
        assertThat(service.deleteForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 402L).status())
                .isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        verifyNoInteractions(storage);
        verifyNoInteractions(cleanupProcessor);
    }

    @Test
    void physicalDeleteFailureKeepsLogicalDeleteSuccessfulAndAlreadyDeletedCanRetry() {
        SpeakingMediaDeletionResult deleted = new SpeakingMediaDeletionResult(
                501L, PracticeSpeakingMediaStatus.DELETED, 1001L);
        when(mediaService.markDeletedForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 501L))
                .thenReturn(deleted, deleted);
        when(cleanupProcessor.processTaskNow(1001L))
                .thenThrow(new IllegalStateException("LEARNER_AUDIO_PATH_SECRET_B2A"))
                .thenReturn(new CleanupTaskProcessingResult(Outcome.COMPLETED));

        assertThat(service.deleteForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 501L).status())
                .isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(service.deleteForOwner(USER_ID, ATTEMPT_ID, QUESTION_ID, 501L).status())
                .isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        verify(cleanupProcessor, times(2)).processTaskNow(1001L);
    }

    @Test
    void uploadServiceHasNoTransactionalBoundary() throws Exception {
        assertThat(SpeakingAudioUploadService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(SpeakingAudioUploadService.class
                .getMethod("uploadOrReplaceForOwner", Long.class, Long.class, Long.class,
                        InputStream.class, Long.class, String.class)
                .getAnnotation(Transactional.class)).isNull();
    }

    private SpeakingAudioUploadResult upload(byte[] bytes) {
        return service.uploadOrReplaceForOwner(
                USER_ID,
                ATTEMPT_ID,
                QUESTION_ID,
                new ByteArrayInputStream(bytes),
                (long) bytes.length,
                "audio/webm");
    }

    private PreparedSpeakingAudio prepared(String key, String hash) {
        return new PreparedSpeakingAudio(
                PracticeSpeakingStorageProvider.LOCAL,
                key,
                "audio/webm",
                "webm",
                "opus",
                3L,
                1200L,
                hash);
    }

    private SpeakingMediaActivationResult activated(
            Long mediaId,
            Optional<Long> supersededCleanupTaskId) {
        return new SpeakingMediaActivationResult(
                mediaId,
                QUESTION_ID,
                PracticeSpeakingMediaStatus.READY,
                3L,
                1200L,
                "audio/webm",
                0L,
                supersededCleanupTaskId);
    }
}
