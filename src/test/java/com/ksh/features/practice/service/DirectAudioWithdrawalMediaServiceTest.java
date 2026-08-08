package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioWithdrawalMediaServiceTest {
    private final PracticeAttemptRepository attempts = mock(PracticeAttemptRepository.class);
    private final PracticeSpeakingMediaRepository media = mock(PracticeSpeakingMediaRepository.class);
    private final PracticeSpeakingMediaCleanupTaskService cleanup =
            mock(PracticeSpeakingMediaCleanupTaskService.class);
    private final DirectAudioWithdrawalMediaService service =
            new DirectAudioWithdrawalMediaService(attempts, media, cleanup);

    @Test
    void ownedSpeakingWithdrawalMarksEveryLiveMediaPendingAndEnqueuesExactEvidence() {
        PracticeAttempt attempt = mock(PracticeAttempt.class);
        PracticeSpeakingMedia ready = row(31L, PracticeSpeakingMediaStatus.READY,
                "learner-speaking/ready/secret-one");
        PracticeSpeakingMedia deleted = row(32L, PracticeSpeakingMediaStatus.DELETED,
                "learner-speaking/ready/secret-two");
        when(attempt.getSkill()).thenReturn("SPEAKING");
        when(attempts.findByIdAndUserIdForUpdate(22L, 11L)).thenReturn(Optional.of(attempt));
        when(media.findByAttemptIdForUpdateOrderByIdAsc(22L)).thenReturn(List.of(ready, deleted));

        int enqueued = service.enqueueForWithdrawal(11L, 22L, "WITHDRAWAL-EVIDENCE-1");

        assertThat(enqueued).isEqualTo(1);
        verify(ready).markDeletionPending();
        verify(cleanup).enqueueConsentWithdrawal(31L, PracticeSpeakingStorageProvider.LOCAL,
                "PRACTICE_SPEAKING", "learner-speaking/ready/secret-one",
                "WITHDRAWAL-EVIDENCE-1");
        verify(deleted, never()).markDeletionPending();
        verify(media).flush();
    }

    @Test
    void nonSpeakingOrUnownedAttemptCannotEnqueueStorageDeletion() {
        PracticeAttempt writing = mock(PracticeAttempt.class);
        when(writing.getSkill()).thenReturn("WRITING");
        when(attempts.findByIdAndUserIdForUpdate(22L, 11L)).thenReturn(Optional.of(writing));

        assertThatThrownBy(() -> service.enqueueForWithdrawal(
                11L, 22L, "WITHDRAWAL-EVIDENCE-1"))
                .isInstanceOf(SecurityException.class);
        verify(media, never()).findByAttemptIdForUpdateOrderByIdAsc(22L);
        verify(cleanup, never()).enqueueConsentWithdrawal(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static PracticeSpeakingMedia row(
            Long id, PracticeSpeakingMediaStatus status, String storageKey) {
        PracticeSpeakingMedia value = mock(PracticeSpeakingMedia.class);
        when(value.getId()).thenReturn(id);
        when(value.getStatus()).thenReturn(status);
        when(value.getStorageProvider()).thenReturn(PracticeSpeakingStorageProvider.LOCAL);
        when(value.getStorageProfileCode()).thenReturn("PRACTICE_SPEAKING");
        when(value.getStorageKey()).thenReturn(storageKey);
        return value;
    }
}
