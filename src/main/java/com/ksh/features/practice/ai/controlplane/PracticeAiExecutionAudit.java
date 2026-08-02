package com.ksh.features.practice.ai.controlplane;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_ai_execution_audits")
public class PracticeAiExecutionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purpose_code", nullable = false, length = 64)
    private String purposeCode;
    @Column(name = "binding_revision", nullable = false)
    private long bindingRevision;
    @Column(name = "provider_profile_revision", nullable = false)
    private long providerProfileRevision;
    @Column(name = "provider_family", nullable = false, length = 40)
    private String providerFamily;
    @Column(name = "provider_profile_code", nullable = false, length = 64)
    private String providerProfileCode;
    @Column(name = "model", nullable = false, length = 150)
    private String model;
    @Column(name = "transport_dialect", nullable = false, length = 64)
    private String transportDialect;
    @Column(name = "capability_digest", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String capabilityDigest;
    @Column(name = "limits_digest", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String limitsDigest;
    @Column(name = "retention_code", nullable = false, length = 64)
    private String retentionCode;
    @Column(name = "operation_code", nullable = false, length = 80)
    private String operationCode;
    @Column(name = "contract_identity_digest", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String contractIdentityDigest;
    @Column(name = "data_class", nullable = false, length = 64)
    private String dataClass;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "bounded_error_code", length = 64)
    private String boundedErrorCode;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PracticeAiExecutionAudit() {
    }

    public PracticeAiExecutionAudit(
            PracticeAiExecutionSnapshot snapshot,
            String operationCode,
            String contractIdentityDigest,
            String dataClass,
            LocalDateTime startedAt) {
        purposeCode = snapshot.purpose().name();
        bindingRevision = snapshot.bindingRevision();
        providerProfileRevision = snapshot.providerProfileRevision();
        providerFamily = snapshot.providerFamily();
        providerProfileCode = snapshot.providerProfileCode();
        model = snapshot.model();
        transportDialect = snapshot.transportDialect();
        capabilityDigest = snapshot.capabilityDigest();
        limitsDigest = snapshot.limitsDigest();
        retentionCode = snapshot.retentionCode();
        this.operationCode = operationCode;
        this.contractIdentityDigest = contractIdentityDigest;
        this.dataClass = dataClass;
        status = "RESOLVED";
        this.startedAt = startedAt;
    }

    public void complete(String status, String errorCode, LocalDateTime completedAt) {
        this.status = status;
        this.boundedErrorCode = errorCode;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
}
