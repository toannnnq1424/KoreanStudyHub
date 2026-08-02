package com.ksh.features.practice.manage.service;

import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;

public interface AssetStorageService {

    String providerCode();

    default String profileCode() {
        return null;
    }
    
    StoredAsset store(InputStream content, String filename, String relativePath) throws IOException;
    
    Resource load(String storageKey) throws IOException;

    default Resource load(String storageProfileCode, String storageKey) throws IOException {
        if (storageProfileCode != null) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
        return load(storageKey);
    }
    
    boolean exists(String storageKey);

    default boolean exists(String storageProfileCode, String storageKey) {
        return storageProfileCode == null && exists(storageKey);
    }
    
    void delete(String storageKey) throws IOException;

    default void delete(String storageProfileCode, String storageKey) throws IOException {
        if (storageProfileCode != null) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
        delete(storageKey);
    }
    
    AssetMetadata inspect(String storageKey) throws IOException;

    default AssetMetadata inspect(String storageProfileCode, String storageKey) throws IOException {
        if (storageProfileCode != null) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
        return inspect(storageKey);
    }

    record StoredAsset(String storageKey, long sizeBytes, String sha256,
                       boolean newlyCreated, String storageProfileCode,
                       String storageProvider) {
        public StoredAsset(String storageKey, long sizeBytes, String sha256) {
            this(storageKey, sizeBytes, sha256, true, null, "LOCAL");
        }

        public StoredAsset(String storageKey, long sizeBytes, String sha256,
                           boolean newlyCreated) {
            this(storageKey, sizeBytes, sha256, newlyCreated, null, "LOCAL");
        }
    }
    record AssetMetadata(int width, int height) {}
}
