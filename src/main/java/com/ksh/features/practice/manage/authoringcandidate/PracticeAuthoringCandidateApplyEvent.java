package com.ksh.features.practice.manage.authoringcandidate;

import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ApplyResultCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "practice_authoring_candidate_apply_events")
public class PracticeAuthoringCandidateApplyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false, length = 36,
            columnDefinition = "CHAR(36)")
    private String candidateId;

    @Column(name = "apply_request_id", nullable = false, length = 36,
            columnDefinition = "CHAR(36)")
    private String applyRequestId;

    @Column(name = "candidate_version", nullable = false)
    private Long candidateVersion;

    @Column(name = "candidate_digest", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String candidateDigest;

    @Column(name = "base_draft_version", nullable = false)
    private Integer baseDraftVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ApplyResultCode result;

    @Column(name = "result_code", nullable = false, length = 100)
    private String resultCode;

    @Column(name = "result_draft_version")
    private Integer resultDraftVersion;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PracticeAuthoringCandidateApplyEvent() {
    }

    public PracticeAuthoringCandidateApplyEvent(
            String candidateId,
            UUID applyRequestId,
            long candidateVersion,
            String candidateDigest,
            int baseDraftVersion,
            ApplyResultCode result,
            String resultCode,
            Integer resultDraftVersion,
            Long actorId,
            LocalDateTime createdAt) {
        this.candidateId = require(candidateId, "candidate id");
        this.applyRequestId = Objects.requireNonNull(
                applyRequestId, "apply request id").toString();
        this.candidateVersion = candidateVersion;
        this.candidateDigest = requireDigest(candidateDigest);
        this.baseDraftVersion = baseDraftVersion;
        this.result = Objects.requireNonNull(result, "apply result");
        this.resultCode = require(resultCode, "result code");
        this.resultDraftVersion = resultDraftVersion;
        this.actorId = Objects.requireNonNull(actorId, "actor id");
        this.createdAt = Objects.requireNonNull(createdAt, "created at");
        if (candidateVersion < 0 || baseDraftVersion < 0
                || (result == ApplyResultCode.DRAFT_APPLIED)
                != (resultDraftVersion != null)) {
            throw new IllegalArgumentException("Apply event result is invalid");
        }
    }

    public boolean matches(long version, String digest, int draftVersion,
                           Long actor) {
        return candidateVersion == version
                && candidateDigest.equals(stripPrefix(digest))
                && baseDraftVersion == draftVersion
                && Objects.equals(actorId, actor);
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static String requireDigest(String value) {
        String digest = stripPrefix(value);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Candidate digest is invalid");
        }
        return digest;
    }

    static String stripPrefix(String value) {
        if (value == null) return "";
        return value.startsWith("sha256:") ? value.substring(7) : value;
    }

    public Long getId() { return id; }
    public String getCandidateId() { return candidateId; }
    public String getApplyRequestId() { return applyRequestId; }
    public Long getCandidateVersion() { return candidateVersion; }
    public String getCandidateDigest() { return candidateDigest; }
    public Integer getBaseDraftVersion() { return baseDraftVersion; }
    public ApplyResultCode getResult() { return result; }
    public String getResultCode() { return resultCode; }
    public Integer getResultDraftVersion() { return resultDraftVersion; }
    public Long getActorId() { return actorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
