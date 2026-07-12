package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_exam_template_versions")
public class AssessmentExamTemplateVersion {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 80)
    private String templateCode;

    @Column(name = "program_version_id", nullable = false)
    private Long programVersionId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    private String configJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    protected AssessmentExamTemplateVersion() {
    }

    public AssessmentExamTemplateVersion(String templateCode, Integer versionNumber,
                                         String configJson, Long createdBy) {
        this(templateCode, null, versionNumber, configJson, createdBy);
    }

    public AssessmentExamTemplateVersion(String templateCode, Long programVersionId,
                                         Integer versionNumber, String configJson,
                                         Long createdBy) {
        this.templateCode = templateCode;
        this.programVersionId = programVersionId;
        this.versionNumber = versionNumber;
        this.configJson = configJson;
        this.status = STATUS_DRAFT;
        this.createdBy = createdBy;
    }

    public void activate(Long actorId) {
        this.status = STATUS_ACTIVE;
        this.activatedBy = actorId;
        this.activatedAt = LocalDateTime.now();
    }

    public void archive() {
        this.status = STATUS_ARCHIVED;
    }

    public Long getId() { return id; }
    public String getTemplateCode() { return templateCode; }
    public Long getProgramVersionId() { return programVersionId; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getConfigJson() { return configJson; }
    public String getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public Long getActivatedBy() { return activatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
}
