package com.ksh.features.practice.service.storage;

import com.ksh.entities.PracticeStorageMigrationLogicalType;
import com.ksh.features.storage.profile.StorageProfileCode;

public record PracticeStorageMigrationClaim(
        Long jobId,
        String claimToken,
        PracticeStorageMigrationLogicalType logicalType,
        Long logicalId,
        StorageProfileCode sourceProfileCode,
        String sourceStorageKey,
        StorageProfileCode targetProfileCode,
        String targetStorageKey,
        long expectedSize,
        String expectedSha256) {
}
