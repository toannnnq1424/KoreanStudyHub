package com.ksh.features.practice.ai.controlplane;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_ai_purpose_bindings")
public class PracticeAiPurposeBinding {

    @Id
    @Column(name = "purpose_code", nullable = false, length = 64)
    private String purposeCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_profile_id", nullable = false)
    private PracticeAiProviderProfile providerProfile;

    @Column(name = "model", nullable = false, length = 150)
    private String model;

    @Column(name = "transport_dialect", nullable = false, length = 64)
    private String transportDialect;

    @Column(name = "capability_json", nullable = false, columnDefinition = "JSON")
    private String capabilityJson;

    @Column(name = "limits_json", nullable = false, columnDefinition = "JSON")
    private String limitsJson;

    @Column(name = "retention_code", nullable = false, length = 64)
    private String retentionCode;

    /** Optional/deprecated provider information; not a readiness gate. */
    @Column(name = "region_evidence_id", length = 160)
    private String regionEvidenceId;

    @Column(name = "non_training_evidence_id", length = 160)
    private String nonTrainingEvidenceId;

    @Column(name = "retention_evidence_id", length = 160)
    private String retentionEvidenceId;

    /** Optional/deprecated metadata; never proof that provider data was deleted. */
    @Column(name = "deletion_sla_evidence_id", length = 160)
    private String deletionSlaEvidenceId;

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

    protected PracticeAiPurposeBinding() {
    }

    public PracticeAiPurposeBinding(
            PracticeAiPurpose purpose,
            PracticeAiProviderProfile providerProfile,
            String model,
            String transportDialect,
            String capabilityJson,
            String limitsJson,
            String retentionCode,
            boolean enabled,
            Long updatedBy) {
        this.purposeCode = purpose.name();
        update(providerProfile, model, transportDialect, capabilityJson,
                limitsJson, retentionCode, enabled, updatedBy);
    }

    public void update(
            PracticeAiProviderProfile providerProfile,
            String model,
            String transportDialect,
            String capabilityJson,
            String limitsJson,
            String retentionCode,
            boolean enabled,
            Long updatedBy) {
        this.providerProfile = providerProfile;
        this.model = model;
        this.transportDialect = transportDialect;
        this.capabilityJson = capabilityJson;
        this.limitsJson = limitsJson;
        this.retentionCode = retentionCode;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }

    public void updatePolicyEvidence(
            String regionEvidenceId,
            String nonTrainingEvidenceId,
            String retentionEvidenceId,
            String deletionSlaEvidenceId) {
        this.regionEvidenceId = blankToNull(regionEvidenceId);
        this.nonTrainingEvidenceId = blankToNull(nonTrainingEvidenceId);
        this.retentionEvidenceId = blankToNull(retentionEvidenceId);
        this.deletionSlaEvidenceId = blankToNull(deletionSlaEvidenceId);
    }

    public void toggle(Long actorId) {
        enabled = !enabled;
        updatedBy = actorId;
    }

    public String getPurposeCode() { return purposeCode; }
    public PracticeAiProviderProfile getProviderProfile() { return providerProfile; }
    public String getModel() { return model; }
    public String getTransportDialect() { return transportDialect; }
    public String getCapabilityJson() { return capabilityJson; }
    public String getLimitsJson() { return limitsJson; }
    public String getRetentionCode() { return retentionCode; }
    public String getRegionEvidenceId() { return regionEvidenceId; }
    public String getNonTrainingEvidenceId() { return nonTrainingEvidenceId; }
    public String getRetentionEvidenceId() { return retentionEvidenceId; }
    public String getDeletionSlaEvidenceId() { return deletionSlaEvidenceId; }
    public boolean isEnabled() { return enabled; }
    public long getRevision() { return revision; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
