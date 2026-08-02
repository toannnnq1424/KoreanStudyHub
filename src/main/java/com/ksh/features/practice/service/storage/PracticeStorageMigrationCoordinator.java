package com.ksh.features.practice.service.storage;

import com.ksh.features.storage.profile.StorageBackend;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Explicit-only migration seam: copy, verify, transactional identity switch,
 * then separately claimed delayed deletion. It has no scheduler and creates no
 * jobs by itself, so AIM-6 never initiates a provider migration.
 */
@Service
public class PracticeStorageMigrationCoordinator {
    private final PracticeStorageMigrationJobService jobs;
    private final PracticeStorageMigrationIdentityService identities;
    private final PracticeStorageMigrationObjectPort objects;

    public PracticeStorageMigrationCoordinator(PracticeStorageMigrationJobService jobs,
                                               PracticeStorageMigrationIdentityService identities,
                                               PracticeStorageMigrationObjectPort objects) {
        this.jobs = jobs;
        this.identities = identities;
        this.objects = objects;
    }

    public boolean processCopy(Long jobId) {
        var claimed = jobs.claimCopy(jobId);
        if (claimed.isEmpty()) {
            return identities.switchVerifiedTarget(jobId);
        }
        PracticeStorageMigrationClaim claim = claimed.get();
        Path spool = null;
        boolean verificationPersisted = false;
        try {
            spool = Files.createTempFile("ksh-practice-storage-migration-", ".bin");
            Verification source;
            try (InputStream input = objects.openSource(claim);
                 OutputStream output = Files.newOutputStream(spool)) {
                source = copyAndHash(input, output);
            }
            requireExpected(claim, source);
            StorageBackend provider;
            try (InputStream input = Files.newInputStream(spool)) {
                provider = objects.writeTarget(claim, input);
            }
            Verification target;
            try (InputStream input = objects.openTarget(claim)) {
                target = hash(input);
            }
            requireExpected(claim, target);
            jobs.markCopiedVerified(claim, provider);
            verificationPersisted = true;
            identities.switchVerifiedTarget(jobId);
            return true;
        } catch (Exception failure) {
            if (!verificationPersisted) {
                safelyDeleteUnverifiedTarget(claim);
                safelyRetryCopy(claim, errorCode(failure));
            }
            return false;
        } finally {
            if (spool != null) {
                try { Files.deleteIfExists(spool); } catch (IOException ignored) { }
            }
        }
    }

    public boolean processDelayedSourceDelete(Long jobId) {
        var claimed = jobs.claimCleanup(jobId);
        if (claimed.isEmpty()) return false;
        PracticeStorageMigrationClaim claim = claimed.get();
        try {
            objects.deleteSource(claim);
            if (objects.sourceExists(claim)) {
                throw new IOException("Source deletion was not physically confirmed");
            }
            jobs.completeCleanup(claim);
            return true;
        } catch (Exception failure) {
            try {
                jobs.retryCleanup(claim, errorCode(failure));
            } catch (RuntimeException ignored) {
                // A newer lease owns the durable job; the stale worker cannot mutate it.
            }
            return false;
        }
    }

    private void safelyRetryCopy(PracticeStorageMigrationClaim claim, String errorCode) {
        try {
            jobs.retryCopy(claim, errorCode);
        } catch (RuntimeException ignored) {
            // A newer lease owns the durable job; the stale worker cannot mutate it.
        }
    }

    private void safelyDeleteUnverifiedTarget(PracticeStorageMigrationClaim claim) {
        try {
            objects.deleteTarget(claim);
        } catch (Exception ignored) {
            // The deterministic target key is overwritten and re-verified on retry.
        }
    }

    private static void requireExpected(PracticeStorageMigrationClaim claim,
                                        Verification actual) throws IOException {
        if (actual.size() != claim.expectedSize()
                || !actual.sha256().equals(claim.expectedSha256())) {
            throw new IOException("Migrated bytes failed size/hash verification");
        }
    }

    private static Verification copyAndHash(InputStream input, OutputStream output)
            throws IOException {
        MessageDigest digest = sha256();
        long size;
        try (DigestInputStream hashing = new DigestInputStream(input, digest)) {
            size = hashing.transferTo(output);
        }
        return new Verification(size, HexFormat.of().formatHex(digest.digest()));
    }

    private static Verification hash(InputStream input) throws IOException {
        return copyAndHash(input, OutputStream.nullOutputStream());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String errorCode(Exception failure) {
        String message = failure.getMessage();
        if (message != null && message.contains("size/hash")) {
            return "STORAGE_MIGRATION_VERIFY_FAILED";
        }
        return "STORAGE_MIGRATION_IO_FAILED";
    }

    private record Verification(long size, String sha256) { }
}
