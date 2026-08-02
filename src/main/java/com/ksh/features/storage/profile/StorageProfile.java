package com.ksh.features.storage.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "storage_profiles")
public class StorageProfile {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_code", length = 40)
    private StorageProfileCode profileCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "backend", nullable = false, length = 16)
    private StorageBackend backend;

    @Column(name = "account_id", length = 128)
    private String accountId;

    @Column(name = "access_key_id", length = 255)
    private String accessKeyId;

    @Column(name = "secret_access_key", length = 4096)
    private String secretAccessKey;

    @Column(name = "bucket", length = 255)
    private String bucket;

    @Column(name = "endpoint", length = 512)
    private String endpoint;

    @Column(name = "region", length = 64)
    private String region;

    @Column(name = "key_prefix", nullable = false, length = 255)
    private String keyPrefix;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Version
    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected StorageProfile() {
    }

    public StorageProfile(StorageProfileCode profileCode,
                          StorageBackend backend,
                          String accountId,
                          String accessKeyId,
                          String secretAccessKey,
                          String bucket,
                          String endpoint,
                          String region,
                          boolean enabled,
                          Long updatedBy) {
        this.profileCode = java.util.Objects.requireNonNull(profileCode, "profileCode");
        this.keyPrefix = profileCode.fixedKeyPrefix();
        update(backend, accountId, accessKeyId, secretAccessKey, bucket,
                endpoint, region, enabled, updatedBy);
    }

    public void update(StorageBackend backend,
                       String accountId,
                       String accessKeyId,
                       String replacementSecret,
                       String bucket,
                       String endpoint,
                       String region,
                       boolean enabled,
                       Long updatedBy) {
        this.backend = java.util.Objects.requireNonNull(backend, "backend");
        this.accountId = accountId;
        this.accessKeyId = accessKeyId;
        if (replacementSecret != null) {
            this.secretAccessKey = replacementSecret;
        }
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.region = region;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }

    public void toggle(Long actorId) {
        enabled = !enabled;
        updatedBy = actorId;
    }

    public StorageProfileCode getProfileCode() { return profileCode; }
    public StorageBackend getBackend() { return backend; }
    public String getAccountId() { return accountId; }
    public String getAccessKeyId() { return accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public String getBucket() { return bucket; }
    public String getEndpoint() { return endpoint; }
    public String getRegion() { return region; }
    public String getKeyPrefix() { return keyPrefix; }
    public boolean isEnabled() { return enabled; }
    public long getRevision() { return revision; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
