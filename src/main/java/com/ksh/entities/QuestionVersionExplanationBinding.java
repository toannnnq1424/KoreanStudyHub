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

    @Column(name = "bound_at", insertable = false, updatable = false)
    private LocalDateTime boundAt;

    protected QuestionVersionExplanationBinding() {
    }

    public Long getId() { return id; }
    public Long getQuestionVersionId() { return questionVersionId; }
    public Long getArtifactId() { return artifactId; }
    public String getExplanationLanguage() { return explanationLanguage; }
    public String getFingerprint() { return fingerprint; }
    public LocalDateTime getBoundAt() { return boundAt; }
}
