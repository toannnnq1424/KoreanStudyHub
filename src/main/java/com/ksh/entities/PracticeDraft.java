package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_drafts")
public class PracticeDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "class_id")
    private Long classId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "draft_json", columnDefinition = "LONGTEXT")
    private String draftJson;

    @Column(name = "published_set_id")
    private Long publishedSetId;

    @Column(name = "creation_method", length = 30)
    private String creationMethod;

    @Column(name = "draft_schema_version", length = 40)
    private String draftSchemaVersion;

    @Column(name = "assessment_program_code", length = 40)
    private String assessmentProgramCode;

    @Column(name = "assessment_program_version_id")
    private Long assessmentProgramVersionId;

    @Column(name = "exam_template_code", length = 80)
    private String examTemplateCode;

    @Version
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PracticeDraft() {
    }

    public Long getPublishedSetId() {
        return publishedSetId;
    }

    public void setPublishedSetId(Long publishedSetId) {
        this.publishedSetId = publishedSetId;
    }

    public PracticeDraft(String title, String description, String category, String scope,
                         Long classId, String status, Long ownerId, String draftJson) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.scope = scope;
        this.classId = classId;
        this.status = status;
        this.ownerId = ownerId;
        this.draftJson = draftJson;
        this.creationMethod = "MANUAL"; // default
        this.draftSchemaVersion = "practice-draft-v3";
        this.assessmentProgramCode = category != null && category.toUpperCase().startsWith("TOPIK")
                ? "TOPIK"
                : "CUSTOM";
        this.examTemplateCode = "TOPIK_I".equalsIgnoreCase(category)
                ? "TOPIK_I"
                : (assessmentProgramCode.equals("TOPIK") ? "TOPIK_II" : "CUSTOM_FLEXIBLE");
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getDraftJson() {
        return draftJson;
    }

    public void setDraftJson(String draftJson) {
        this.draftJson = draftJson;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getCreationMethod() {
        return creationMethod;
    }

    public void setCreationMethod(String creationMethod) {
        this.creationMethod = creationMethod;
    }

    public String getDraftSchemaVersion() { return draftSchemaVersion; }
    public void setDraftSchemaVersion(String draftSchemaVersion) { this.draftSchemaVersion = draftSchemaVersion; }
    public String getAssessmentProgramCode() { return assessmentProgramCode; }
    public void setAssessmentProgramCode(String assessmentProgramCode) { this.assessmentProgramCode = assessmentProgramCode; }
    public Long getAssessmentProgramVersionId() { return assessmentProgramVersionId; }
    public void setAssessmentProgramVersionId(Long assessmentProgramVersionId) { this.assessmentProgramVersionId = assessmentProgramVersionId; }
    public String getExamTemplateCode() { return examTemplateCode; }
    public void setExamTemplateCode(String examTemplateCode) { this.examTemplateCode = examTemplateCode; }
}
