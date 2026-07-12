package com.ksh.features.practice.manage.service;

import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;

public interface AssetStorageService {

    String providerCode();
    
    StoredAsset store(InputStream content, String filename, String relativePath) throws IOException;
    
    Resource load(String storageKey) throws IOException;
    
    boolean exists(String storageKey);
    
    void delete(String storageKey) throws IOException;
    
    AssetMetadata inspect(String storageKey) throws IOException;

    record StoredAsset(String storageKey, long sizeBytes, String sha256,
                       boolean newlyCreated) {
        public StoredAsset(String storageKey, long sizeBytes, String sha256) {
            this(storageKey, sizeBytes, sha256, true);
        }
    }
    record AssetMetadata(int width, int height) {}
}
