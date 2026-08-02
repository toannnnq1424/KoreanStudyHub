package com.ksh.features.practice.service.storage;

import com.ksh.features.practice.manage.service.AssetStorageService;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
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
    private final AssetStorageService authoring;
    private final SpeakingAudioStorage speaking;

    public ProfiledPracticeStorageMigrationObjectPort(
            StorageProfileObjectStore profiles,
            AssetStorageService authoring,
            SpeakingAudioStorage speaking) {
        this.profiles = profiles;
        this.authoring = authoring;
        this.speaking = speaking;
    }

    @Override
    public InputStream openSource(PracticeStorageMigrationClaim claim) throws IOException {
        if (claim.sourceProfileCode() != null) {
            StoredObject object = profiles.open(
                    claim.sourceProfileCode(), claim.sourceStorageKey());
            return object.inputStream();
        }
        return switch (claim.logicalType()) {
            case LECTURER_ASSET -> authoring.load(null, claim.sourceStorageKey()).getInputStream();
            case PDF_IMPORT_SESSION -> throw retiredPdfMigration();
            case SPEAKING_MEDIA -> speaking.open(null, claim.sourceStorageKey());
        };
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
        if (claim.sourceProfileCode() != null) {
            profiles.delete(claim.sourceProfileCode(), claim.sourceStorageKey());
            return;
        }
        switch (claim.logicalType()) {
            case LECTURER_ASSET -> authoring.delete(null, claim.sourceStorageKey());
            case PDF_IMPORT_SESSION -> throw retiredPdfMigration();
            case SPEAKING_MEDIA -> speaking.delete(null, claim.sourceStorageKey());
        }
    }

    @Override
    public boolean sourceExists(PracticeStorageMigrationClaim claim) {
        if (claim.sourceProfileCode() != null) {
            return profiles.exists(claim.sourceProfileCode(), claim.sourceStorageKey());
        }
        return switch (claim.logicalType()) {
            case LECTURER_ASSET -> authoring.exists(null, claim.sourceStorageKey());
            case PDF_IMPORT_SESSION -> throw retiredPdfMigration();
            case SPEAKING_MEDIA -> speaking.exists(null, claim.sourceStorageKey());
        };
    }

    private static IllegalStateException retiredPdfMigration() {
        return new IllegalStateException("PDF_IMPORT_SESSION_MIGRATION_RETIRED");
    }
}
