package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.service.audio.PreparedSpeakingAudio;
import com.ksh.features.practice.service.audio.SpeakingAudioPreparationService;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeakingAudioUploadServiceTest {
    private static final Long USER_ID = 11L;
    private static final Long ATTEMPT_ID = 22L;
    private static final Long QUESTION_ID = 33L;
    private static final String PROFILE = "PRACTICE_SPEAKING";
    private static final String TEMP_KEY = "learner-speaking/temporary/secret-b2a";
    private static final String READY_KEY = "learner-speaking/ready/secret-b2a";
    private static final String HASH = "a".repeat(64);

    private SpeakingAudioPreparationService preparation;
    private PracticeSpeakingMediaService media;
    private SpeakingAudioStorage storage;
    private PracticeSpeakingMediaCleanupTaskService cleanupTasks;
    private PracticeSpeakingMediaCleanupProcessor cleanupProcessor;
    private SpeakingAudioUploadService service;

    @BeforeEach
    void setUp() {
        preparation = mock(SpeakingAudioPreparationService.class);
        media = mock(PracticeSpeakingMediaService.class);
        storage = mock(SpeakingAudioStorage.class);
        cleanupTasks = mock(PracticeSpeakingMediaCleanupTaskService.class);
        cleanupProcessor = mock(PracticeSpeakingMediaCleanupProcessor.class);
        service = new SpeakingAudioUploadService(
                preparation, media, storage, cleanupTasks, cleanupProcessor);
    }

    @Test
    void preflightFailureAvoidsPreparationAndStorage() {
        RuntimeException failure = new jakarta.persistence.EntityNotFoundException("target unavailable");
        doThrow(failure).when(media).validateUploadTargetForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID);

        assertThatThrownBy(() -> upload()).isSameAs(failure);
        verifyNoInteractions(preparation, storage);
    }

    @Test
    void exactProfileTemporaryIsRegisteredPromotedAndExplicitlyActivated() {
        PreparedSpeakingAudio prepared = prepared();
        SpeakingMediaActivationResult activated = new SpeakingMediaActivationResult(
                101L, QUESTION_ID, PracticeSpeakingMediaStatus.READY,
                3L, 1200L, "audio/webm", 0L, Optional.empty());
        when(preparation.prepare(any(InputStream.class), eq(3L), eq("audio/webm")))
                .thenReturn(prepared);
        when(media.registerUnreferencedTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.temporaryDescriptor()))
                .thenReturn(91L);
        when(storage.promoteTemporary(PROFILE, TEMP_KEY)).thenReturn(READY_KEY);
        when(media.promoteTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, 91L, TEMP_KEY,
                prepared.readyDescriptor(READY_KEY))).thenReturn(activated);

        var result = upload();

        assertThat(result.mediaId()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo(PracticeSpeakingMediaStatus.READY);
        var ordered = inOrder(media, storage);
        ordered.verify(media).registerUnreferencedTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.temporaryDescriptor());
        ordered.verify(storage).promoteTemporary(PROFILE, TEMP_KEY);
        ordered.verify(media).promoteTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, 91L, TEMP_KEY,
                prepared.readyDescriptor(READY_KEY));
        verify(media, never()).activateValidatedMediaForOwner(
                anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void registrationFailureCompensatesOnlyTheExactTemporaryIdentity() {
        PreparedSpeakingAudio prepared = prepared();
        RuntimeException failure = new IllegalStateException("registration failed");
        when(preparation.prepare(any(InputStream.class), anyLong(), anyString()))
                .thenReturn(prepared);
        when(media.registerUnreferencedTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.temporaryDescriptor()))
                .thenThrow(failure);

        assertThatThrownBy(() -> upload()).isSameAs(failure);
        verify(storage).delete(PROFILE, TEMP_KEY);
        verify(storage, never()).delete(eq("GENERAL_UPLOADS"), anyString());
    }

    @Test
    void failedExactCompensationPersistsExactCleanupIntent() {
        PreparedSpeakingAudio prepared = prepared();
        RuntimeException primary = new IllegalStateException("registration failed");
        when(preparation.prepare(any(InputStream.class), anyLong(), anyString()))
                .thenReturn(prepared);
        when(media.registerUnreferencedTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.temporaryDescriptor()))
                .thenThrow(primary);
        doThrow(new IllegalStateException("delete failed"))
                .when(storage).delete(PROFILE, TEMP_KEY);

        assertThatThrownBy(() -> upload()).isSameAs(primary);
        verify(cleanupTasks).enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, PROFILE, TEMP_KEY);
    }

    @Test
    void activationFailureQueuesReadyObjectWithoutDeletingTemporaryOwnershipRow() {
        PreparedSpeakingAudio prepared = prepared();
        RuntimeException failure = new IllegalStateException("activation failed");
        when(preparation.prepare(any(InputStream.class), anyLong(), anyString()))
                .thenReturn(prepared);
        when(media.registerUnreferencedTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, prepared.temporaryDescriptor()))
                .thenReturn(91L);
        when(storage.promoteTemporary(PROFILE, TEMP_KEY)).thenReturn(READY_KEY);
        when(media.promoteTemporaryForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID, 91L, TEMP_KEY,
                prepared.readyDescriptor(READY_KEY))).thenThrow(failure);

        assertThatThrownBy(() -> upload()).isSameAs(failure);
        verify(cleanupTasks).enqueueCompensationOrphan(
                PracticeSpeakingStorageProvider.LOCAL, PROFILE, READY_KEY);
        verify(storage, never()).delete(PROFILE, TEMP_KEY);
    }

    private SpeakingAudioUploadService.SpeakingAudioUploadResult upload() {
        return service.uploadOrReplaceForOwner(
                USER_ID, ATTEMPT_ID, QUESTION_ID,
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3L, "audio/webm");
    }

    private static PreparedSpeakingAudio prepared() {
        return new PreparedSpeakingAudio(
                PracticeSpeakingStorageProvider.LOCAL, PROFILE,
                TEMP_KEY, TEMP_KEY, "audio/webm", "webm", "opus",
                3L, 1200L, HASH);
    }
}
