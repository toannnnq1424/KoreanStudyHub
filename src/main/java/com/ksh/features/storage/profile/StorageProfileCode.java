package com.ksh.features.storage.profile;

import java.util.Locale;

public enum StorageProfileCode {
    GENERAL_UPLOADS("general-uploads"),
    PRACTICE_AUTHORING("practice-authoring"),
    PRACTICE_SPEAKING("practice-speaking");

    private final String fixedKeyPrefix;

    StorageProfileCode(String fixedKeyPrefix) {
        this.fixedKeyPrefix = fixedKeyPrefix;
    }

    public String fixedKeyPrefix() {
        return fixedKeyPrefix;
    }

    public static StorageProfileCode require(String value) {
        if (value == null || value.isBlank()) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
    }
}
