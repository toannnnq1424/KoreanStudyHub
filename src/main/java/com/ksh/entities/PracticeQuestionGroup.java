package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_question_groups")
public class PracticeQuestionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

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

    @Column(name = "section_id")
    private Long sectionId;

    protected PracticeQuestionGroup() {
    }

    public PracticeQuestionGroup(Long setId, String groupLabel, Integer questionFrom, Integer questionTo,
                                 String instruction, String audioUrl, String exampleJson, Integer displayOrder) {
        this.setId = setId;
        this.groupLabel = groupLabel;
        this.questionFrom = questionFrom;
        this.questionTo = questionTo;
        this.instruction = instruction;
        this.audioUrl = audioUrl;
        this.exampleJson = exampleJson;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getSetId() {
        return setId;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public Integer getQuestionFrom() {
        return questionFrom;
    }

    public Integer getQuestionTo() {
        return questionTo;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public String getExampleJson() {
        return exampleJson;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
