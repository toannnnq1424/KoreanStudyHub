package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_version_explanation_bindings")
public class QuestionVersionExplanationBinding {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_version_id", nullable = false)
    private Long questionVersionId;

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(name = "explanation_language", nullable = false, length = 16)
    private String explanationLanguage;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String fingerprint;

    @Column(name = "binding_status", nullable = false, length = 20)
    private String bindingStatus;

    @Column(name = "bound_at", insertable = false, updatable = false)
    private LocalDateTime boundAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    protected QuestionVersionExplanationBinding() {
    }

    public Long getId() { return id; }
    public Long getQuestionVersionId() { return questionVersionId; }
    public Long getArtifactId() { return artifactId; }
    public String getExplanationLanguage() { return explanationLanguage; }
    public String getFingerprint() { return fingerprint; }
    public String getBindingStatus() { return bindingStatus; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public LocalDateTime getSupersededAt() { return supersededAt; }
}
