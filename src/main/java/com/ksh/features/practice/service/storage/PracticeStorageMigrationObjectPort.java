package com.ksh.features.practice.service.storage;

import com.ksh.features.storage.profile.StorageBackend;

import java.io.IOException;
import java.io.InputStream;

/** Provider-neutral byte port; test fakes exercise migration without R2 calls. */
public interface PracticeStorageMigrationObjectPort {
    InputStream openSource(PracticeStorageMigrationClaim claim) throws IOException;

    StorageBackend writeTarget(PracticeStorageMigrationClaim claim, InputStream bytes)
            throws IOException;

    InputStream openTarget(PracticeStorageMigrationClaim claim) throws IOException;

    void deleteTarget(PracticeStorageMigrationClaim claim) throws IOException;

    void deleteSource(PracticeStorageMigrationClaim claim) throws IOException;

    boolean sourceExists(PracticeStorageMigrationClaim claim);
}
