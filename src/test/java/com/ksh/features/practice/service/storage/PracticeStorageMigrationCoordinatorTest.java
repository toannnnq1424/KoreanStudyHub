package com.ksh.features.practice.service.storage;

import com.ksh.entities.PracticeStorageMigrationLogicalType;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeStorageMigrationCoordinatorTest {
    private static final byte[] BYTES = "exact-private-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void copyVerifySwitchLeavesSourceForDelayedDurableCleanup() {
        PracticeStorageMigrationJobService jobs = mock(PracticeStorageMigrationJobService.class);
        PracticeStorageMigrationIdentityService identities =
                mock(PracticeStorageMigrationIdentityService.class);
        PracticeStorageMigrationClaim claim = claim();
        FakeObjects objects = new FakeObjects(BYTES);
        when(jobs.claimCopy(7L)).thenReturn(Optional.of(claim));
        when(identities.switchVerifiedTarget(7L)).thenReturn(true);

        boolean processed = new PracticeStorageMigrationCoordinator(
                jobs, identities, objects).processCopy(7L);

        assertThat(processed).isTrue();
        assertThat(objects.target).isEqualTo(BYTES);
        assertThat(objects.source).isEqualTo(BYTES);
        assertThat(objects.sourceDeleteCalls).isZero();
        verify(jobs).markCopiedVerified(claim, StorageBackend.LOCAL);
        verify(identities).switchVerifiedTarget(7L);
        verify(jobs, never()).retryCopy(claim, "STORAGE_MIGRATION_VERIFY_FAILED");
    }

    @Test
    void hashMismatchNeverUpdatesLogicalIdentityAndDeletesUnverifiedTarget() {
        PracticeStorageMigrationJobService jobs = mock(PracticeStorageMigrationJobService.class);
        PracticeStorageMigrationIdentityService identities =
                mock(PracticeStorageMigrationIdentityService.class);
        PracticeStorageMigrationClaim claim = new PracticeStorageMigrationClaim(
                7L, "claimtoken1234567", PracticeStorageMigrationLogicalType.LECTURER_ASSET,
                11L, null, "/legacy/asset.bin", StorageProfileCode.PRACTICE_AUTHORING,
                "lecturer-assets/migrated.bin", BYTES.length, "0".repeat(64));
        FakeObjects objects = new FakeObjects(BYTES);
        when(jobs.claimCopy(7L)).thenReturn(Optional.of(claim));

        boolean processed = new PracticeStorageMigrationCoordinator(
                jobs, identities, objects).processCopy(7L);

        assertThat(processed).isFalse();
        assertThat(objects.targetDeleteCalls).isEqualTo(1);
        verify(jobs).retryCopy(claim, "STORAGE_MIGRATION_VERIFY_FAILED");
        verify(identities, never()).switchVerifiedTarget(7L);
    }

    @Test
    void physicalConfirmationPrecedesCompletionAndFailureRetriesDurably() {
        PracticeStorageMigrationJobService jobs = mock(PracticeStorageMigrationJobService.class);
        PracticeStorageMigrationClaim claim = claim();
        FakeObjects objects = new FakeObjects(BYTES);
        objects.refuseSourceDelete = true;
        when(jobs.claimCleanup(7L)).thenReturn(Optional.of(claim));

        boolean processed = new PracticeStorageMigrationCoordinator(
                jobs, mock(PracticeStorageMigrationIdentityService.class), objects)
                .processDelayedSourceDelete(7L);

        assertThat(processed).isFalse();
        verify(jobs).retryCleanup(claim, "STORAGE_MIGRATION_IO_FAILED");
        verify(jobs, never()).completeCleanup(claim);
    }

    @Test
    void losingClaimIsIdempotentAndPerformsNoByteIo() {
        PracticeStorageMigrationJobService jobs = mock(PracticeStorageMigrationJobService.class);
        PracticeStorageMigrationIdentityService identities =
                mock(PracticeStorageMigrationIdentityService.class);
        FakeObjects objects = new FakeObjects(BYTES);
        when(jobs.claimCopy(7L)).thenReturn(Optional.empty());
        when(identities.switchVerifiedTarget(7L)).thenReturn(false);

        assertThat(new PracticeStorageMigrationCoordinator(jobs, identities, objects)
                .processCopy(7L)).isFalse();
        assertThat(objects.openSourceCalls).isZero();
        assertThat(objects.target).isNull();
    }

    private static PracticeStorageMigrationClaim claim() {
        return new PracticeStorageMigrationClaim(
                7L, "claimtoken1234567", PracticeStorageMigrationLogicalType.LECTURER_ASSET,
                11L, null, "/legacy/asset.bin", StorageProfileCode.PRACTICE_AUTHORING,
                "lecturer-assets/migrated.bin", BYTES.length, sha256(BYTES));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeObjects implements PracticeStorageMigrationObjectPort {
        private byte[] source;
        private byte[] target;
        private int openSourceCalls;
        private int targetDeleteCalls;
        private int sourceDeleteCalls;
        private boolean refuseSourceDelete;

        private FakeObjects(byte[] source) { this.source = source; }

        @Override public InputStream openSource(PracticeStorageMigrationClaim claim) {
            openSourceCalls++;
            return new ByteArrayInputStream(source);
        }

        @Override public StorageBackend writeTarget(
                PracticeStorageMigrationClaim claim, InputStream bytes) throws IOException {
            target = bytes.readAllBytes();
            return StorageBackend.LOCAL;
        }

        @Override public InputStream openTarget(PracticeStorageMigrationClaim claim) {
            return new ByteArrayInputStream(target);
        }

        @Override public void deleteTarget(PracticeStorageMigrationClaim claim) {
            targetDeleteCalls++;
            target = null;
        }

        @Override public void deleteSource(PracticeStorageMigrationClaim claim) {
            sourceDeleteCalls++;
            if (!refuseSourceDelete) source = null;
        }

        @Override public boolean sourceExists(PracticeStorageMigrationClaim claim) {
            return source != null;
        }
    }
}
