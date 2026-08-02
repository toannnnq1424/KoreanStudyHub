package com.ksh.features.practice.service.storage;

import com.ksh.features.storage.StoredObject;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ProfiledPracticeStorageMigrationObjectPort
        implements PracticeStorageMigrationObjectPort {
    private final StorageProfileObjectStore profiles;

    public ProfiledPracticeStorageMigrationObjectPort(StorageProfileObjectStore profiles) {
        this.profiles = profiles;
    }

    @Override
    public InputStream openSource(PracticeStorageMigrationClaim claim) throws IOException {
        requireActiveSource(claim);
        StoredObject object = profiles.open(
                claim.sourceProfileCode(), claim.sourceStorageKey());
        return object.inputStream();
    }

    @Override
    public StorageBackend writeTarget(PracticeStorageMigrationClaim claim, InputStream bytes)
            throws IOException {
        return profiles.put(claim.targetProfileCode(), claim.targetStorageKey(), bytes,
                "application/octet-stream", claim.expectedSize());
    }

    @Override
    public InputStream openTarget(PracticeStorageMigrationClaim claim) throws IOException {
        return profiles.open(claim.targetProfileCode(), claim.targetStorageKey()).inputStream();
    }

    @Override
    public void deleteTarget(PracticeStorageMigrationClaim claim) throws IOException {
        profiles.delete(claim.targetProfileCode(), claim.targetStorageKey());
    }

    @Override
    public void deleteSource(PracticeStorageMigrationClaim claim) throws IOException {
        requireActiveSource(claim);
        profiles.delete(claim.sourceProfileCode(), claim.sourceStorageKey());
    }

    @Override
    public boolean sourceExists(PracticeStorageMigrationClaim claim) {
        requireActiveSource(claim);
        return profiles.exists(claim.sourceProfileCode(), claim.sourceStorageKey());
    }

    private static void requireActiveSource(PracticeStorageMigrationClaim claim) {
        if (claim.logicalType()
                == com.ksh.entities.PracticeStorageMigrationLogicalType.PDF_IMPORT_SESSION) {
            throw new IllegalStateException("PDF_IMPORT_SESSION_MIGRATION_RETIRED");
        }
        if (claim.sourceProfileCode() == null
                || claim.sourceProfileCode() != claim.logicalType().requiredProfile()) {
            throw new IllegalStateException("STORAGE_MIGRATION_SOURCE_PROFILE_REQUIRED");
        }
    }
}
