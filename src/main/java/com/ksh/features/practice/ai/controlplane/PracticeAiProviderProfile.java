package com.ksh.features.practice.ai.controlplane;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_ai_provider_profiles")
public class PracticeAiProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_code", nullable = false, length = 64, unique = true)
    private String profileCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "provider_family", nullable = false, length = 40)
    private String providerFamily;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "credential_secret", nullable = false, length = 4096)
    private String credentialSecret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Version
    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PracticeAiProviderProfile() {
    }

    public PracticeAiProviderProfile(
            String profileCode,
            String displayName,
            String providerFamily,
            String baseUrl,
            String credentialSecret,
            boolean enabled,
            Long updatedBy) {
        update(displayName, providerFamily, baseUrl, credentialSecret, enabled, updatedBy);
        this.profileCode = profileCode;
    }

    public void update(
            String displayName,
            String providerFamily,
            String baseUrl,
            String replacementSecret,
            boolean enabled,
            Long updatedBy) {
        this.displayName = displayName;
        this.providerFamily = providerFamily;
        this.baseUrl = baseUrl;
        if (replacementSecret != null) {
            this.credentialSecret = replacementSecret;
        }
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }

    public void toggle(Long actorId) {
        enabled = !enabled;
        updatedBy = actorId;
    }

    public Long getId() { return id; }
    public String getProfileCode() { return profileCode; }
    public String getDisplayName() { return displayName; }
    public String getProviderFamily() { return providerFamily; }
    public String getBaseUrl() { return baseUrl; }
    public String getCredentialSecret() { return credentialSecret; }
    public boolean isEnabled() { return enabled; }
    public long getRevision() { return revision; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
