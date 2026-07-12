package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "practice_sets")
@SQLRestriction("is_deleted = 0")
public class PracticeSet {

    public static final String SKILL_READING = "READING";
    public static final String SKILL_LISTENING = "LISTENING";
    public static final String SKILL_WRITING = "WRITING";
    public static final String SKILL_SPEAKING = "SPEAKING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_CLASS = "CLASS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String skill;

    @Column(name = "topik_level", length = 20)
    private String topikLevel;

    @Column(name = "assessment_program_code", nullable = false, length = 40)
    private String assessmentProgramCode;

    @Column(name = "assessment_program_version_id")
    private Long assessmentProgramVersionId;

    @Column(name = "exam_template_code", length = 80)
    private String examTemplateCode;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "source_pdf_path", length = 500)
    private String sourcePdfPath;

    @Column(name = "audio_path", length = 500)
    private String audioPath;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @Column(name = "creation_method", length = 30)
    private String creationMethod;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "owner_locked", nullable = false)
    private boolean ownerLocked;

    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    protected PracticeSet() {
    }

    public PracticeSet(String title, String description, String skill, String topikLevel,
                       String scope, Long classId, String sourcePdfPath, String metadataJson,
                       String status, Long createdBy) {
        this.title = title;
        this.description = description;
        this.skill = skill;
        this.topikLevel = topikLevel;
        this.assessmentProgramCode = topikLevel != null && topikLevel.toUpperCase().startsWith("TOPIK")
                ? "TOPIK"
                : "CUSTOM";
        this.scope = scope;
        this.classId = classId;
        this.sourcePdfPath = sourcePdfPath;
        this.metadataJson = metadataJson;
        this.status = status;
        this.createdBy = createdBy;
        this.creationMethod = "MANUAL"; // default legacy compat
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSkill() {
        return skill;
    }

    public String getTopikLevel() {
        return topikLevel;
    }

    public String getAssessmentProgramCode() {
        return assessmentProgramCode;
    }

    public Long getAssessmentProgramVersionId() { return assessmentProgramVersionId; }

    public String getExamTemplateCode() { return examTemplateCode; }

    public String getScope() {
        return scope;
    }

    public Long getClassId() {
        return classId;
    }

    public String getSourcePdfPath() {
        return sourcePdfPath;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public String getStatus() {
        return status;
    }

    public boolean isOwnerLocked() {
        return ownerLocked;
    }

    public Long getLockedBy() {
        return lockedBy;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }

    public void setTopikLevel(String topikLevel) {
        this.topikLevel = topikLevel;
    }

    public void setAssessmentProgramCode(String assessmentProgramCode) {
        this.assessmentProgramCode = assessmentProgramCode;
    }

    public void setAssessmentProgramVersionId(Long assessmentProgramVersionId) {
        this.assessmentProgramVersionId = assessmentProgramVersionId;
    }

    public void setExamTemplateCode(String examTemplateCode) {
        this.examTemplateCode = examTemplateCode;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void lock(Long actorId) {
        this.ownerLocked = true;
        this.lockedBy = actorId;
        this.lockedAt = LocalDateTime.now();
    }

    public void unlock() {
        this.ownerLocked = false;
        this.lockedBy = null;
        this.lockedAt = null;
    }

    public void archive() {
        this.status = STATUS_ARCHIVED;
        this.archivedAt = LocalDateTime.now();
    }

    public void restoreFromArchive() {
        this.status = STATUS_PUBLISHED;
        this.archivedAt = null;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public List<String> getSkillsList() {
        if ("MIXED".equals(this.skill)) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(this.metadataJson);
                if (root != null && root.has("skills") && root.get("skills").isArray()) {
                    List<String> list = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode node : root.get("skills")) {
                        list.add(node.asText());
                    }
                    if (!list.isEmpty()) {
                        return list;
                    }
                }
            } catch (Exception e) {
                // ignore and fallback
            }
        }
        return List.of(this.skill != null ? this.skill : "READING");
    }

    public String getCreationMethod() {
        return creationMethod;
    }

    public void setCreationMethod(String creationMethod) {
        this.creationMethod = creationMethod;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }
}
