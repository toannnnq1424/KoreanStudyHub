package com.ksh.entities;

import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeStorageMigrationJobTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void profileAndLogicalTypeCannotCrossOrReuseSamePhysicalIdentity() {
        assertThatThrownBy(() -> PracticeStorageMigrationJob.planned(
                PracticeStorageMigrationLogicalType.SPEAKING_MEDIA, 1L, null,
                "legacy-key", StorageProfileCode.PRACTICE_AUTHORING,
                "learner-speaking/ready/a", 1L, HASH, LocalDateTime.now()))
                .hasMessage("STORAGE_MIGRATION_PROFILE_INVALID");
        assertThatThrownBy(() -> PracticeStorageMigrationJob.planned(
                PracticeStorageMigrationLogicalType.LECTURER_ASSET, 1L,
                StorageProfileCode.PRACTICE_AUTHORING, "lecturer-assets/a",
                StorageProfileCode.PRACTICE_AUTHORING, "lecturer-assets/a",
                1L, HASH, LocalDateTime.now()))
                .hasMessage("STORAGE_MIGRATION_KEYS_COLLIDE");
    }

    @Test
    void cleanupIsDelayedAndCopyRetriesAreBoundedAtEight() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        PracticeStorageMigrationJob job = planned(now);
        assertThat(job.claimCopy(now, now.plusMinutes(5), "claimtoken1234567")).isTrue();
        job.markCopiedVerified("claimtoken1234567", StorageBackend.LOCAL, now);
        job.markLogicalUpdatedAndCleanupPending(now, now.plusHours(24));

        assertThat(job.claimCleanup(now.plusHours(23), now.plusHours(23).plusMinutes(5),
                "cleanupclaim12345")).isFalse();
        assertThat(job.claimCleanup(now.plusHours(24), now.plusHours(24).plusMinutes(5),
                "cleanupclaim12345")).isTrue();
    }

    @Test
    void copyRetryBecomesTerminalOnEighthAttempt() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 0, 0);
        PracticeStorageMigrationJob job = planned(now);
        for (int attempt = 1; attempt <= PracticeStorageMigrationJob.MAX_ATTEMPTS; attempt++) {
            String token = "claimtoken" + String.format("%07d", attempt);
            assertThat(job.claimCopy(now, now.plusMinutes(5), token)).isTrue();
            job.retryCopy(token, "STORAGE_MIGRATION_IO_FAILED", now.plusMinutes(1));
            now = now.plusMinutes(1);
        }
        assertThat(job.getCopyAttemptCount()).isEqualTo(8);
        assertThat(job.getStatus()).isEqualTo(PracticeStorageMigrationStatus.FAILED);
        assertThat(job.claimCopy(now, now.plusMinutes(5), "claimtoken9999999")).isFalse();
    }

    private static PracticeStorageMigrationJob planned(LocalDateTime now) {
        return PracticeStorageMigrationJob.planned(
                PracticeStorageMigrationLogicalType.LECTURER_ASSET, 1L, null,
                "/legacy/asset.bin", StorageProfileCode.PRACTICE_AUTHORING,
                "lecturer-assets/migrated.bin", 1L, HASH, now);
    }
}
