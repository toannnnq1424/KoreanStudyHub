package com.ksh.features.storage.profile;

import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Wider-product ObjectStorage bridge. New writes use GENERAL_UPLOADS exactly;
 * the legacy store is read only after an exact-profile miss for pre-AIM-6
 * general-upload keys. Practice never injects this adapter.
 */
public final class GeneralUploadsObjectStorage implements ObjectStorage {
    private final StorageProfileObjectStore profiles;
    private final ObjectStorage legacy;

    public GeneralUploadsObjectStorage(StorageProfileObjectStore profiles,
                                       ObjectStorage legacy) {
        this.profiles = profiles;
        this.legacy = legacy;
    }

    @Override
    public void put(String key, InputStream data, String contentType, long contentLength)
            throws IOException {
        profiles.put(StorageProfileCode.GENERAL_UPLOADS, key, data, contentType, contentLength);
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            if (profiles.exists(StorageProfileCode.GENERAL_UPLOADS, key)) {
                profiles.delete(StorageProfileCode.GENERAL_UPLOADS, key);
                return;
            }
        } catch (RuntimeException unavailableProfile) {
            // A pre-AIM-6 general object remains deletable through the bounded
            // wider-product legacy store while the new profile is unavailable.
        }
        legacy.delete(key);
    }

    @Override
    public boolean exists(String key) {
        try {
            if (profiles.exists(StorageProfileCode.GENERAL_UPLOADS, key)) return true;
        } catch (RuntimeException ignored) {
            // Missing/invalid active profile is not replaced for writes. This
            // bounded branch only preserves reads of pre-AIM-6 general bytes.
        }
        return legacy.exists(key);
    }

    @Override
    public StoredObject open(String key) throws IOException {
        try {
            return profiles.open(StorageProfileCode.GENERAL_UPLOADS, key);
        } catch (IOException | RuntimeException exactMiss) {
            return legacy.open(key);
        }
    }

    @Override
    public StoredObject openRange(String key, long start, long end) throws IOException {
        try {
            return profiles.openRange(StorageProfileCode.GENERAL_UPLOADS, key, start, end);
        } catch (IOException | RuntimeException exactMiss) {
            return legacy.openRange(key, start, end);
        }
    }

    @Override
    public void copy(String sourceKey, String destKey) throws IOException {
        try (StoredObject source = open(sourceKey)) {
            put(destKey, source.inputStream(), source.contentType(), source.contentLength());
        }
    }

    @Override
    public List<String> listKeys(String prefix) throws IOException {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        try {
            keys.addAll(profiles.listKeys(StorageProfileCode.GENERAL_UPLOADS, prefix));
        } catch (RuntimeException ignored) {
            // Bounded legacy general-upload inventory remains available.
        }
        keys.addAll(legacy.listKeys(prefix));
        return List.copyOf(keys);
    }
}
