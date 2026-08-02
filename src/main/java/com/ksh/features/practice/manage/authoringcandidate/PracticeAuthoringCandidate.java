package com.ksh.features.practice.manage.authoringcandidate;

import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "practice_authoring_candidates")
public class PracticeAuthoringCandidate {

    public static final String NORMALIZER_VERSION =
            "practice-authoring-normalizer-v1";
    public static final String VALIDATOR_VERSION =
            "practice-authoring-validator-v1";

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 32)
    private SourceKind sourceKind;

    @Column(name = "source_contract_version", nullable = false, length = 64)
    private String sourceContractVersion;

    @Column(name = "source_digest", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String sourceDigest;

    @Column(name = "source_revision", nullable = false, length = 100)
    private String sourceRevision;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_operation", nullable = false, length = 12)
    private SourceOperation sourceOperation;

    @Column(name = "target_draft_id", nullable = false)
    private Long targetDraftId;

    @Column(name = "target_test_no", nullable = false)
    private Integer targetTestNo;

    @Column(name = "target_skill", nullable = false, length = 16)
    private String targetSkill;

    @Column(name = "target_lesson_code", nullable = false, length = 32)
    private String targetLessonCode;

    @Column(name = "base_draft_version", nullable = false)
    private Integer baseDraftVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CandidateState state;

    @Column(name = "normalizer_version", nullable = false, length = 64)
    private String normalizerVersion;

    @Column(name = "validator_version", nullable = false, length = 64)
    private String validatorVersion;

    @Column(name = "candidate_json", nullable = false, columnDefinition = "LONGTEXT")
    private String candidateJson;

    @Column(name = "content_digest", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String contentDigest;

    @Column(name = "warning_acknowledged_at")
    private LocalDateTime warningAcknowledgedAt;

    @Column(name = "warning_acknowledged_by")
    private Long warningAcknowledgedBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "applied_draft_version")
    private Integer appliedDraftVersion;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PracticeAuthoringCandidate() {
    }

    public PracticeAuthoringCandidate(
            String id,
            Long ownerId,
            SourceKind sourceKind,
            String sourceContractVersion,
            String sourceDigest,
            String sourceRevision,
            String sourceName,
            SourceOperation sourceOperation,
            Long targetDraftId,
            int targetTestNo,
            String targetSkill,
            String targetLessonCode,
            int baseDraftVersion,
            String candidateJson,
            String contentDigest,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        this.id = requireUuid(id);
        this.ownerId = Objects.requireNonNull(ownerId, "owner id");
        this.sourceKind = Objects.requireNonNull(sourceKind, "source kind");
        this.sourceContractVersion = require(
                sourceContractVersion, "source contract version");
        this.sourceDigest = requireDigest(sourceDigest);
        this.sourceRevision = require(sourceRevision, "source revision");
        this.sourceName = blankToNull(sourceName);
        this.sourceOperation = Objects.requireNonNull(
                sourceOperation, "source operation");
        this.targetDraftId = Objects.requireNonNull(targetDraftId, "target draft");
        this.targetTestNo = targetTestNo;
        this.targetSkill = require(targetSkill, "target skill");
        this.targetLessonCode = require(targetLessonCode, "target lesson");
        this.baseDraftVersion = baseDraftVersion;
        this.state = CandidateState.PARSED;
        this.normalizerVersion = NORMALIZER_VERSION;
        this.validatorVersion = VALIDATOR_VERSION;
        this.candidateJson = require(candidateJson, "candidate JSON");
        this.contentDigest = requireDigest(contentDigest);
        this.createdAt = Objects.requireNonNull(createdAt, "created at");
        this.updatedAt = createdAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expires at");
        if (ownerId < 1
                || !sourceKind.contractVersion().equals(sourceContractVersion)
                || (sourceKind == SourceKind.PDF_AI
                    ? sourceOperation == SourceOperation.NONE
                    : sourceOperation != SourceOperation.NONE)
                || sourceRevision.length() > 100
                || (sourceName != null && sourceName.length() > 255)
                || targetTestNo < 1 || baseDraftVersion < 0
                || !validTarget(targetSkill, targetLessonCode, targetTestNo)
                || expiresAt.isBefore(createdAt.plusDays(7))) {
            throw new IllegalArgumentException(
                    "Candidate target/version/expiry is invalid");
        }
    }

    public void markNormalized(String json, String digest, LocalDateTime now) {
        requireState(CandidateState.PARSED);
        replaceEnvelope(json, digest, now);
        state = CandidateState.NORMALIZED;
    }

    public void markValidated(String json, String digest, LocalDateTime now) {
        requireState(CandidateState.NORMALIZED);
        replaceEnvelope(json, digest, now);
        state = CandidateState.VALIDATED;
    }

    public void beginReview(String json, LocalDateTime now) {
        requireState(CandidateState.VALIDATED);
        candidateJson = require(json, "candidate JSON");
        state = CandidateState.REVIEWING;
        updatedAt = Objects.requireNonNull(now, "updated at");
    }

    public void replaceReview(
            String json,
            String digest,
            Long actorId,
            boolean warningsAcknowledged,
            LocalDateTime now) {
        requireMutable(now);
        if (state != CandidateState.REVIEWING
                && state != CandidateState.VALIDATED
                && state != CandidateState.READY_TO_APPLY) {
            throw new IllegalStateException(
                    "Candidate content is not reviewable in state " + state);
        }
        replaceEnvelope(json, digest, now);
        state = CandidateState.REVIEWING;
        if (warningsAcknowledged) {
            warningAcknowledgedAt = now;
            warningAcknowledgedBy = Objects.requireNonNull(
                    actorId, "warning actor");
        } else {
            warningAcknowledgedAt = null;
            warningAcknowledgedBy = null;
        }
    }

    public void markReady(String json, Long actorId,
                          boolean hasWarnings, LocalDateTime now) {
        requireMutable(now);
        if (state != CandidateState.REVIEWING) {
            throw new IllegalStateException(
                    "Only a reviewing candidate can become ready");
        }
        if (hasWarnings && warningAcknowledgedAt == null) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_WARNING_ACKNOWLEDGEMENT_REQUIRED",
                    "Phải xác nhận cảnh báo trước khi áp dụng candidate.");
        }
        if (hasWarnings && !Objects.equals(actorId, warningAcknowledgedBy)) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_WARNING_ACKNOWLEDGEMENT_REQUIRED",
                    "Người áp dụng phải xác nhận cảnh báo của candidate.");
        }
        candidateJson = require(json, "candidate JSON");
        state = CandidateState.READY_TO_APPLY;
        updatedAt = now;
    }

    public void markApplied(String json, int draftVersion, LocalDateTime now) {
        requireState(CandidateState.READY_TO_APPLY);
        candidateJson = require(json, "candidate JSON");
        state = CandidateState.APPLIED;
        appliedAt = Objects.requireNonNull(now, "applied at");
        appliedDraftVersion = draftVersion;
        updatedAt = now;
    }

    public void reject(String json, LocalDateTime now) {
        requireMutable(now);
        candidateJson = require(json, "candidate JSON");
        state = CandidateState.REJECTED;
        updatedAt = now;
    }

    public void fail(String json, LocalDateTime now) {
        requireMutable(now);
        candidateJson = require(json, "candidate JSON");
        state = CandidateState.FAILED;
        updatedAt = now;
    }

    public boolean expireIfDue(String json, LocalDateTime now) {
        if (isTerminal() || expiresAt.isAfter(now)) {
            return false;
        }
        candidateJson = require(json, "candidate JSON");
        state = CandidateState.EXPIRED;
        updatedAt = now;
        return true;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isTerminal() {
        return state == CandidateState.APPLIED
                || state == CandidateState.REJECTED
                || state == CandidateState.EXPIRED;
    }

    private void requireMutable(LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) {
            throw new IllegalStateException(
                    "Candidate content is immutable in state " + state);
        }
        if (isExpiredAt(now)) {
            throw new PracticeAuthoringCandidateException(
                    "CANDIDATE_EXPIRED", "Candidate đã hết hạn.");
        }
    }

    private void replaceEnvelope(String json, String digest, LocalDateTime now) {
        candidateJson = require(json, "candidate JSON");
        contentDigest = requireDigest(digest);
        updatedAt = Objects.requireNonNull(now, "updated at");
    }

    private void requireState(CandidateState expected) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Expected candidate state " + expected + " but was " + state);
        }
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static String requireUuid(String value) {
        String candidateId = require(value, "candidate id");
        try {
            UUID.fromString(candidateId);
            return candidateId;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("candidate id must be a UUID", exception);
        }
    }

    private static boolean validTarget(
            String skill, String lessonCode, int testNo) {
        String prefix = switch (skill) {
            case "READING" -> "R";
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "";
        };
        return lessonCode.equals(prefix + testNo);
    }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 digest is invalid");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public SourceKind getSourceKind() { return sourceKind; }
    public String getSourceContractVersion() { return sourceContractVersion; }
    public String getSourceDigest() { return sourceDigest; }
    public String getSourceRevision() { return sourceRevision; }
    public String getSourceName() { return sourceName; }
    public SourceOperation getSourceOperation() { return sourceOperation; }
    public Long getTargetDraftId() { return targetDraftId; }
    public Integer getTargetTestNo() { return targetTestNo; }
    public String getTargetSkill() { return targetSkill; }
    public String getTargetLessonCode() { return targetLessonCode; }
    public Integer getBaseDraftVersion() { return baseDraftVersion; }
    public CandidateState getState() { return state; }
    public String getNormalizerVersion() { return normalizerVersion; }
    public String getValidatorVersion() { return validatorVersion; }
    public String getCandidateJson() { return candidateJson; }
    public String getContentDigest() { return contentDigest; }
    public LocalDateTime getWarningAcknowledgedAt() {
        return warningAcknowledgedAt;
    }
    public Long getWarningAcknowledgedBy() { return warningAcknowledgedBy; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public Integer getAppliedDraftVersion() { return appliedDraftVersion; }
    public Long getLockVersion() { return lockVersion == null ? 0L : lockVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
