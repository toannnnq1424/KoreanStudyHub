package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "practice_question_group_versions")
public class PracticeQuestionGroupVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "published_version_id", nullable = false)
    private Long publishedVersionId;

    @Column(name = "section_version_id", nullable = false)
    private Long sectionVersionId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "group_label", nullable = false, length = 50)
    private String groupLabel;

    @Column(name = "question_from", nullable = false)
    private Integer questionFrom;

    @Column(name = "question_to", nullable = false)
    private Integer questionTo;

    @Column(columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "example_json", columnDefinition = "JSON")
    private String exampleJson;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected PracticeQuestionGroupVersion() {
    }

    public PracticeQuestionGroupVersion(Long publishedVersionId, Long sectionVersionId, PracticeQuestionGroup group) {
        this.publishedVersionId = publishedVersionId;
        this.sectionVersionId = sectionVersionId;
        this.groupId = group.getId();
        this.groupLabel = group.getGroupLabel();
        this.questionFrom = group.getQuestionFrom();
        this.questionTo = group.getQuestionTo();
        this.instruction = group.getInstruction();
        this.audioUrl = group.getAudioUrl();
        this.exampleJson = group.getExampleJson();
        this.displayOrder = group.getDisplayOrder();
    }

    public Long getId() { return id; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public Long getSectionVersionId() { return sectionVersionId; }
    public Long getGroupId() { return groupId; }
    public String getGroupLabel() { return groupLabel; }
    public Integer getQuestionFrom() { return questionFrom; }
    public Integer getQuestionTo() { return questionTo; }
    public String getInstruction() { return instruction; }
    public String getAudioUrl() { return audioUrl; }
    public String getExampleJson() { return exampleJson; }
    public Integer getDisplayOrder() { return displayOrder; }
}
