package com.ksh.features.storage.profile;

public record ResolvedStorageProfile(
        StorageProfileCode profileCode,
        StorageBackend backend,
        String accountId,
        String accessKeyId,
        String secretAccessKey,
        String bucket,
        String endpoint,
        String region,
        String keyPrefix,
        long revision
) {
}
