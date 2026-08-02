package com.ksh.features.practice.manage.service;

import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;

public interface AssetStorageService {

    String providerCode();

    String profileCode();
    
    StoredAsset store(InputStream content, String filename, String relativePath) throws IOException;
    
    Resource load(String storageProfileCode, String storageKey) throws IOException;
    
    boolean exists(String storageProfileCode, String storageKey);
    
    void delete(String storageProfileCode, String storageKey) throws IOException;
    
    AssetMetadata inspect(String storageProfileCode, String storageKey) throws IOException;

    record StoredAsset(String storageKey, long sizeBytes, String sha256,
                       boolean newlyCreated, String storageProfileCode,
                       String storageProvider) {
        public StoredAsset {
            if (!"PRACTICE_AUTHORING".equals(storageProfileCode)
                    || storageProvider == null || storageProvider.isBlank()) {
                throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
            }
        }
    }
    record AssetMetadata(int width, int height) {}
}
