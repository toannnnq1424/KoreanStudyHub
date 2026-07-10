package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "practice_set_versions")
public class PracticeSetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "published_version_id", nullable = false)
    private Long publishedVersionId;

    @Column(name = "set_id", nullable = false)
    private Long setId;

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

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @Column(name = "creation_method", length = 30)
    private String creationMethod;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    protected PracticeSetVersion() {
    }

    public PracticeSetVersion(Long publishedVersionId, PracticeSet set) {
        this.publishedVersionId = publishedVersionId;
        this.setId = set.getId();
        this.title = set.getTitle();
        this.description = set.getDescription();
        this.skill = set.getSkill();
        this.topikLevel = set.getTopikLevel();
        this.assessmentProgramCode = set.getAssessmentProgramCode();
        this.scope = set.getScope();
        this.classId = set.getClassId();
        this.metadataJson = set.getMetadataJson();
        this.creationMethod = set.getCreationMethod();
        this.coverImageUrl = set.getCoverImageUrl();
    }

    public Long getId() { return id; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public Long getSetId() { return setId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSkill() { return skill; }
    public String getTopikLevel() { return topikLevel; }
    public String getAssessmentProgramCode() { return assessmentProgramCode; }
    public String getScope() { return scope; }
    public Long getClassId() { return classId; }
    public String getMetadataJson() { return metadataJson; }
    public String getCreationMethod() { return creationMethod; }
    public String getCoverImageUrl() { return coverImageUrl; }
}
