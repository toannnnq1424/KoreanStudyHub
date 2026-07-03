package com.ksh.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cloud storage configuration properties.
 *
 * <p>All values loaded from environment variables or {@code application-local.properties}.
 * Never hardcode secrets here.
 *
 * <p>Example {@code application-local.properties}:
 * <pre>
 * storage.provider=r2
 * storage.r2.account-id=abc123xyz
 * storage.r2.access-key=your-r2-access-key-id
 * storage.r2.secret-key=your-r2-secret-access-key
 * storage.r2.bucket=ksh-audio
 * storage.r2.public-domain=https://audio.ksh.edu.vn
 * </pre>
 *
 * <p>Or via environment variables (recommended for production):
 * <pre>
 * STORAGE_R2_ACCOUNT_ID=abc123xyz
 * STORAGE_R2_ACCESS_KEY=...
 * STORAGE_R2_SECRET_KEY=...
 * STORAGE_R2_BUCKET=ksh-audio
 * STORAGE_R2_PUBLIC_DOMAIN=https://audio.ksh.edu.vn
 * </pre>
 */
@Component
public class CloudStorageProperties {

    private final String provider;
    private final String r2AccountId;
    private final String r2AccessKey;
    private final String r2SecretKey;
    private final String r2Bucket;
    private final String r2PublicDomain;

    public CloudStorageProperties(
            @Value("${storage.provider:local}") String provider,
            @Value("${storage.r2.account-id:}") String r2AccountId,
            @Value("${storage.r2.access-key:}") String r2AccessKey,
            @Value("${storage.r2.secret-key:}") String r2SecretKey,
            @Value("${storage.r2.bucket:ksh-audio}") String r2Bucket,
            @Value("${storage.r2.public-domain:}") String r2PublicDomain) {
        this.provider = provider;
        this.r2AccountId = r2AccountId;
        this.r2AccessKey = r2AccessKey;
        this.r2SecretKey = r2SecretKey;
        this.r2Bucket = r2Bucket;
        this.r2PublicDomain = r2PublicDomain;
    }

    public String provider() { return provider; }
    public String r2AccountId() { return r2AccountId; }
    public String r2AccessKey() { return r2AccessKey; }
    public String r2SecretKey() { return r2SecretKey; }
    public String r2Bucket() { return r2Bucket; }
    public String r2PublicDomain() { return r2PublicDomain; }

    /** Returns true if cloud storage is configured and ready. */
    public boolean isCloudConfigured() {
        return !r2AccountId.isBlank() && !r2AccessKey.isBlank() && !r2SecretKey.isBlank();
    }
}
