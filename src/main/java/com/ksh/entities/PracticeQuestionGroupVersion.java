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

    @Column(name = "stimulus_type", length = 40)
    private String stimulusType;

    @Column(name = "passage_text", columnDefinition = "LONGTEXT")
    private String passageText;

    @Column(name = "transcript_text", columnDefinition = "LONGTEXT")
    private String transcriptText;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "stimulus_provenance_json", columnDefinition = "JSON")
    private String stimulusProvenanceJson;

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
        this.stimulusType = group.getStimulusType();
        this.passageText = group.getPassageText();
        this.transcriptText = group.getTranscriptText();
        this.imageUrl = group.getImageUrl();
        this.stimulusProvenanceJson = group.getStimulusProvenanceJson();
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
    public String getStimulusType() { return stimulusType; }
    public String getPassageText() { return passageText; }
    public String getTranscriptText() { return transcriptText; }
    public String getImageUrl() { return imageUrl; }
    public String getStimulusProvenanceJson() { return stimulusProvenanceJson; }
    public String getAudioUrl() { return audioUrl; }
    public String getExampleJson() { return exampleJson; }
    public Integer getDisplayOrder() { return displayOrder; }
}
