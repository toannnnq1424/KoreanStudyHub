package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "practice_speaking_prompt_transcript_revisions")
public class SpeakingPromptTranscriptRevision {

    public static final String SOURCE_PROVIDER = "provider";
    public static final String SOURCE_LECTURER_EDIT = "lecturer_edit";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(name = "owner_lecturer_id", nullable = false)
    private Long ownerLecturerId;

    @Column(name = "artifact_operation", nullable = false, length = 16)
    private String artifactOperation = SpeakingPromptAiContract.Operation.STT.code();

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "revision_source", nullable = false, length = 32)
    private String revisionSource;

    @Column(name = "context_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contextText;

    @Column(name = "context_sha256", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String contextSha256;

    @Column(name = "edited_by")
    private Long editedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SpeakingPromptTranscriptRevision() {
    }

    public static SpeakingPromptTranscriptRevision provider(
            SpeakingPromptAiArtifact artifact,
            int revisionNumber,
            String contextText,
            String contextSha256,
            LocalDateTime confirmedAt) {
        return create(
                artifact,
                revisionNumber,
                SOURCE_PROVIDER,
                contextText,
                contextSha256,
                null,
                confirmedAt);
    }

    public static SpeakingPromptTranscriptRevision lecturerEdit(
            SpeakingPromptAiArtifact artifact,
            int revisionNumber,
            String contextText,
            String contextSha256,
            Long editedBy,
            LocalDateTime confirmedAt) {
        return create(
                artifact,
                revisionNumber,
                SOURCE_LECTURER_EDIT,
                contextText,
                contextSha256,
                Objects.requireNonNull(editedBy, "editedBy"),
                confirmedAt);
    }

    private static SpeakingPromptTranscriptRevision create(
            SpeakingPromptAiArtifact artifact,
            int revisionNumber,
            String source,
            String contextText,
            String contextSha256,
            Long editedBy,
            LocalDateTime confirmedAt) {
        if (artifact == null
                || !SpeakingPromptAiContract.Operation.STT.code().equals(
                        artifact.getOperation())) {
            throw new IllegalArgumentException("Transcript revision requires an STT artifact.");
        }
        if (revisionNumber < 1) {
            throw new IllegalArgumentException(
                    "Transcript revision number must be positive.");
        }
        SpeakingPromptTranscriptRevision revision =
                new SpeakingPromptTranscriptRevision();
        revision.artifactId = artifact.getId();
        revision.ownerLecturerId = artifact.getOwnerLecturerId();
        revision.revisionNumber = revisionNumber;
        revision.revisionSource = source;
        String exactContext = Objects.requireNonNull(contextText, "contextText");
        if (exactContext.isBlank()
                || exactContext.length()
                    > SpeakingPromptAiContract.MAX_PROMPT_TRANSCRIPT_CHARS) {
            throw new IllegalArgumentException(
                    "Transcript revision context is outside the authoring contract.");
        }
        String exactHash = requiredHash(contextSha256);
        if (!Objects.equals(
                exactHash,
                SpeakingPromptAiContract.exactBytesSha256(
                        exactContext.getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
            throw new IllegalArgumentException(
                    "Transcript revision hash does not match its exact text.");
        }
        revision.contextText = exactContext;
        revision.contextSha256 = exactHash;
        revision.editedBy = editedBy;
        revision.confirmedAt = confirmedAt;
        return revision;
    }

    private static String requiredHash(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 identity is invalid.");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getArtifactId() { return artifactId; }
    public Long getOwnerLecturerId() { return ownerLecturerId; }
    public String getArtifactOperation() { return artifactOperation; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public String getRevisionSource() { return revisionSource; }
    String getContextText() { return contextText; }
    String getContextSha256() { return contextSha256; }
    public Long getEditedBy() { return editedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "SpeakingPromptTranscriptRevision{id=" + id
                + ", artifactId=" + artifactId
                + ", revisionNumber=" + revisionNumber
                + ", revisionSource='" + revisionSource + '\''
                + '}';
    }
}
