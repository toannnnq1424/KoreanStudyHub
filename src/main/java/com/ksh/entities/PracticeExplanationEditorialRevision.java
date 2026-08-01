package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_explanation_editorial_revisions")
public class PracticeExplanationEditorialRevision {

    public static final String STATE_GENERATED_DRAFT = "GENERATED_DRAFT";
    public static final String STATE_APPROVED = "APPROVED";
    public static final String STATE_INVALIDATED = "INVALIDATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "question_client_id", nullable = false, length = 80)
    private String questionClientId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @Column(name = "strategy_registry_version", nullable = false, length = 64)
    private String strategyRegistryVersion;

    @Column(name = "strategy_code", nullable = false, length = 64)
    private String strategyCode;

    @Column(name = "strategy_version", nullable = false, length = 32)
    private String strategyVersion;

    @Column(name = "authority_fingerprint", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String authorityFingerprint;

    @Column(name = "editorial_state", nullable = false, length = 24)
    private String editorialState;

    @Column(name = "explanation_json", nullable = false, columnDefinition = "JSON")
    private String explanationJson;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PracticeExplanationEditorialRevision() {
    }

    public PracticeExplanationEditorialRevision(
            Long draftId,
            String questionClientId,
            Integer revisionNo,
            String strategyRegistryVersion,
            String strategyCode,
            String strategyVersion,
            String authorityFingerprint,
            String explanationJson,
            Long createdBy) {
        if (draftId == null
                || questionClientId == null
                || questionClientId.isBlank()
                || revisionNo == null
                || revisionNo < 1
                || strategyRegistryVersion == null
                || strategyCode == null
                || strategyVersion == null
                || authorityFingerprint == null
                || !authorityFingerprint.matches("(?i)[0-9a-f]{64}")
                || explanationJson == null
                || explanationJson.isBlank()
                || createdBy == null) {
            throw new IllegalArgumentException(
                    "Explanation editorial revision is incomplete");
        }
        this.draftId = draftId;
        this.questionClientId = questionClientId;
        this.revisionNo = revisionNo;
        this.strategyRegistryVersion = strategyRegistryVersion;
        this.strategyCode = strategyCode;
        this.strategyVersion = strategyVersion;
        this.authorityFingerprint = authorityFingerprint.toLowerCase(
                java.util.Locale.ROOT);
        this.editorialState = STATE_GENERATED_DRAFT;
        this.explanationJson = explanationJson;
        this.createdBy = createdBy;
    }

    public void approve(Long actorId, LocalDateTime now) {
        if (!STATE_GENERATED_DRAFT.equals(editorialState)
                || actorId == null
                || now == null) {
            throw new IllegalStateException(
                    "Only a generated explanation draft can be approved");
        }
        editorialState = STATE_APPROVED;
        approvedBy = actorId;
        approvedAt = now;
        invalidatedAt = null;
    }

    public void invalidate(LocalDateTime now) {
        if (STATE_INVALIDATED.equals(editorialState)) {
            return;
        }
        editorialState = STATE_INVALIDATED;
        invalidatedAt = now;
    }

    public Long getId() { return id; }
    public Long getDraftId() { return draftId; }
    public String getQuestionClientId() { return questionClientId; }
    public Integer getRevisionNo() { return revisionNo; }
    public String getStrategyRegistryVersion() {
        return strategyRegistryVersion;
    }
    public String getStrategyCode() { return strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public String getAuthorityFingerprint() { return authorityFingerprint; }
    public String getEditorialState() { return editorialState; }
    public String getExplanationJson() { return explanationJson; }
    public Long getCreatedBy() { return createdBy; }
    public Long getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getInvalidatedAt() { return invalidatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
