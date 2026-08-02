package com.ksh.entities;

import com.ksh.features.storage.profile.StorageProfileCode;

public enum PracticeStorageMigrationLogicalType {
    LECTURER_ASSET(StorageProfileCode.PRACTICE_AUTHORING),
    PDF_IMPORT_SESSION(StorageProfileCode.PRACTICE_AUTHORING),
    SPEAKING_MEDIA(StorageProfileCode.PRACTICE_SPEAKING);

    private final StorageProfileCode requiredProfile;

    PracticeStorageMigrationLogicalType(StorageProfileCode requiredProfile) {
        this.requiredProfile = requiredProfile;
    }

    public StorageProfileCode requiredProfile() {
        return requiredProfile;
    }
}
