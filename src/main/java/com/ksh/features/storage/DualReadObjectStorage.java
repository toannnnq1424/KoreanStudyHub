package com.ksh.features.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import static com.ksh.common.IConstant.MSG_STORAGE_R2_NOT_CONFIGURED;
import static com.ksh.common.IConstant.STORAGE_PROVIDER_LOCAL;
import static com.ksh.common.IConstant.STORAGE_PROVIDER_R2;

/**
 * Composite storage: writes go to the active provider only; reads try
 * local first then R2; deletes attempt every available backend and fail
 * observably if any backend cannot confirm the deletion.
 *
 * <p>When {@code provider=r2} but R2 is not ready, writes fail closed
 * with {@link StorageNotConfiguredException} — never silent local fallback.
 */
public class DualReadObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(DualReadObjectStorage.class);

    private final LocalObjectStorage local;
    private final R2ObjectStorage r2;
    private final Supplier<String> providerSupplier;
    private final Supplier<Boolean> r2ReadySupplier;

    public DualReadObjectStorage(LocalObjectStorage local,
                                 R2ObjectStorage r2,
                                 Supplier<String> providerSupplier,
                                 Supplier<Boolean> r2ReadySupplier) {
        this.local = local;
        this.r2 = r2;
        this.providerSupplier = providerSupplier;
        this.r2ReadySupplier = r2ReadySupplier;
    }

    @Override
    public void put(String key, InputStream data, String contentType, long contentLength)
            throws IOException {
        String provider = normalizeProvider(providerSupplier.get());
        if (STORAGE_PROVIDER_R2.equals(provider)) {
            if (!Boolean.TRUE.equals(r2ReadySupplier.get())) {
                throw new StorageNotConfiguredException(MSG_STORAGE_R2_NOT_CONFIGURED);
            }
            r2.put(key, data, contentType, contentLength);
            return;
        }
        local.put(key, data, contentType, contentLength);
    }

    @Override
    public void delete(String key) throws IOException {
        IOException failure = null;
        try {
            local.delete(key);
        } catch (Exception ex) {
            failure = deletionFailure("Local", key, ex);
        }

        boolean r2Ready = Boolean.TRUE.equals(r2ReadySupplier.get());
        if (r2Ready) {
            try {
                r2.delete(key);
            } catch (Exception ex) {
                IOException r2Failure = deletionFailure("R2", key, ex);
                if (failure == null) {
                    failure = r2Failure;
                } else {
                    failure.addSuppressed(r2Failure);
                }
            }
        } else if (STORAGE_PROVIDER_R2.equals(
                normalizeProvider(providerSupplier.get()))) {
            throw new StorageNotConfiguredException(MSG_STORAGE_R2_NOT_CONFIGURED);
        }

        if (failure != null) {
            log.warn("Object delete failed for {}: {}", key, failure.getMessage());
            throw failure;
        }

        if (local.exists(key) || (r2Ready && r2.exists(key))) {
            throw new IOException("Object still exists after delete: " + key);
        }
    }

    @Override
    public boolean exists(String key) {
        if (local.exists(key)) {
            return true;
        }
        return Boolean.TRUE.equals(r2ReadySupplier.get()) && r2.exists(key);
    }

    @Override
    public StoredObject open(String key) throws IOException {
        if (local.exists(key)) {
            return local.open(key);
        }
        if (Boolean.TRUE.equals(r2ReadySupplier.get())) {
            return r2.open(key);
        }
        throw new IOException("Object not found: " + key);
    }

    @Override
    public StoredObject openRange(String key, long start, long end) throws IOException {
        if (local.exists(key)) {
            return local.openRange(key, start, end);
        }
        if (Boolean.TRUE.equals(r2ReadySupplier.get())) {
            return r2.openRange(key, start, end);
        }
        throw new IOException("Object not found: " + key);
    }

    @Override
    public void copy(String sourceKey, String destKey) throws IOException {
        // Prefer same-backend copy when source is local; otherwise stream via open/put.
        if (local.exists(sourceKey)) {
            String provider = normalizeProvider(providerSupplier.get());
            if (STORAGE_PROVIDER_LOCAL.equals(provider)) {
                local.copy(sourceKey, destKey);
                return;
            }
            // Active provider is R2: read local source and put to R2.
            try (StoredObject src = local.open(sourceKey)) {
                put(destKey, src.inputStream(), src.contentType(), src.contentLength());
            }
            return;
        }
        if (Boolean.TRUE.equals(r2ReadySupplier.get()) && r2.exists(sourceKey)) {
            String provider = normalizeProvider(providerSupplier.get());
            if (STORAGE_PROVIDER_R2.equals(provider)) {
                r2.copy(sourceKey, destKey);
                return;
            }
            try (StoredObject src = r2.open(sourceKey)) {
                put(destKey, src.inputStream(), src.contentType(), src.contentLength());
            }
            return;
        }
        throw new IOException("Source object not found: " + sourceKey);
    }

    @Override
    public List<String> listKeys(String prefix) throws IOException {
        LinkedHashSet<String> keys = new LinkedHashSet<>(local.listKeys(prefix));
        if (Boolean.TRUE.equals(r2ReadySupplier.get())) {
            keys.addAll(r2.listKeys(prefix));
        }
        return List.copyOf(keys);
    }

    private static String normalizeProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return STORAGE_PROVIDER_LOCAL;
        }
        return raw.trim().toLowerCase();
    }

    private static IOException deletionFailure(
            String backend, String key, Exception cause) {
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(backend + " delete failed for " + key, cause);
    }
}
