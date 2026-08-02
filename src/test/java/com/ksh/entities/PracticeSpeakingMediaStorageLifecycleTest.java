package com.ksh.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeSpeakingMediaStorageLifecycleTest {

    @Test
    void exactLifecycleRequiresReadyBeforeSupersedeAndPhysicalConfirmationBeforeDeleted() {
        PracticeSpeakingMedia media = PracticeSpeakingMedia.temporary(
                1L, 2L, PracticeSpeakingStorageProvider.LOCAL,
                "PRACTICE_SPEAKING", "learner-speaking/temporary/a",
                "audio/webm", "webm", "opus", 3L, 1000L, "a".repeat(64));

        assertThatThrownBy(media::markSuperseded).isInstanceOf(IllegalStateException.class);
        media.promoteToReady(PracticeSpeakingStorageProvider.LOCAL,
                "PRACTICE_SPEAKING", "learner-speaking/ready/a");
        assertThat(media.getStatus()).isEqualTo(PracticeSpeakingMediaStatus.READY);
        media.markSuperseded();
        media.markDeletionPending();
        assertThat(media.getStatus()).isEqualTo(PracticeSpeakingMediaStatus.DELETION_PENDING);

        LocalDateTime confirmation = LocalDateTime.of(2026, 8, 2, 12, 0);
        media.markDeleted(confirmation);
        assertThat(media.getStatus()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(media.getDeletedAt()).isEqualTo(confirmation);
    }
}
