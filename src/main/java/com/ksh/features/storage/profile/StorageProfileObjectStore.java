package com.ksh.features.storage.profile;

import com.ksh.features.storage.LocalObjectStorage;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.R2ObjectStorage;
import com.ksh.features.storage.StoredObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class StorageProfileObjectStore {
    private final StorageProfileResolver resolver;
    private final LocalObjectStorage local;
    private final StorageProfileR2Clients r2Clients;

    public StorageProfileObjectStore(StorageProfileResolver resolver,
                                     LocalObjectStorage local,
                                     StorageProfileR2Clients r2Clients) {
        this.resolver = resolver;
        this.local = local;
        this.r2Clients = r2Clients;
    }

    public StorageBackend put(StorageProfileCode code,
                              String key,
                              InputStream data,
                              String contentType,
                              long contentLength) throws IOException {
        ResolvedStorageProfile profile = resolver.resolveForWrite(code);
        backend(profile).put(physicalKey(profile, key), data, contentType, contentLength);
        return profile.backend();
    }

    public StoredObject open(StorageProfileCode code, String key) throws IOException {
        ResolvedStorageProfile profile = resolver.resolveForRead(code);
        return backend(profile).open(physicalKey(profile, key));
    }

    public StoredObject openRange(StorageProfileCode code, String key, long start, long end)
            throws IOException {
        ResolvedStorageProfile profile = resolver.resolveForRead(code);
        return backend(profile).openRange(physicalKey(profile, key), start, end);
    }

    public boolean exists(StorageProfileCode code, String key) {
        ResolvedStorageProfile profile = resolver.resolveForRead(code);
        return backend(profile).exists(physicalKey(profile, key));
    }

    public void delete(StorageProfileCode code, String key) throws IOException {
        ResolvedStorageProfile profile = resolver.resolveForRead(code);
        ObjectStorage backend = backend(profile);
        String physicalKey = physicalKey(profile, key);
        backend.delete(physicalKey);
        if (backend.exists(physicalKey)) {
            throw new StorageProfileException("STORAGE_DELETE_UNCONFIRMED");
        }
    }

    public void copy(StorageProfileCode code, String sourceKey, String targetKey) throws IOException {
        ResolvedStorageProfile profile = resolver.resolveForWrite(code);
        ObjectStorage backend = backend(profile);
        backend.copy(physicalKey(profile, sourceKey), physicalKey(profile, targetKey));
    }

    public List<String> listKeys(StorageProfileCode code, String prefix) throws IOException {
        ResolvedStorageProfile profile = resolver.resolveForRead(code);
        String physicalPrefix = physicalKey(profile, prefix);
        String strip = profile.keyPrefix() + "/";
        return backend(profile).listKeys(physicalPrefix).stream()
                .filter(key -> key.startsWith(strip))
                .map(key -> key.substring(strip.length()))
                .toList();
    }

    public StorageBackend backendCodeForWrite(StorageProfileCode code) {
        return resolver.resolveForWrite(code).backend();
    }

    private ObjectStorage backend(ResolvedStorageProfile profile) {
        if (profile.backend() == StorageBackend.LOCAL) {
            return local;
        }
        return new R2ObjectStorage(
                () -> r2Clients.client(profile),
                profile::bucket);
    }

    private static String physicalKey(ResolvedStorageProfile profile, String logicalKey) {
        String safe = StorageProfileResolver.requireSafeObjectKey(logicalKey);
        String physical = profile.keyPrefix() + "/" + safe;
        String prefixFence = profile.keyPrefix() + "/";
        if (!physical.startsWith(prefixFence)) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
        return physical;
    }
}
