package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "practice_question_versions")
public class PracticeQuestionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "published_version_id", nullable = false)
    private Long publishedVersionId;

    @Column(name = "section_version_id", nullable = false)
    private Long sectionVersionId;

    @Column(name = "group_version_id")
    private Long groupVersionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "question_no", nullable = false)
    private Integer questionNo;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "options_json", columnDefinition = "JSON")
    private String optionsJson;

    @Column(name = "answer_key", length = 500)
    private String answerKey;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal points;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "writing_task_type", length = 20)
    private WritingTaskType writingTaskType;

    protected PracticeQuestionVersion() {
    }

    public PracticeQuestionVersion(Long publishedVersionId, Long sectionVersionId,
                                   Long groupVersionId, PracticeQuestion question) {
        this.publishedVersionId = publishedVersionId;
        this.sectionVersionId = sectionVersionId;
        this.groupVersionId = groupVersionId;
        this.questionId = question.getId();
        this.questionNo = question.getQuestionNo();
        this.questionType = question.getQuestionType();
        this.prompt = question.getPrompt();
        this.optionsJson = question.getOptionsJson();
        this.answerKey = question.getAnswerKey();
        this.explanation = question.getExplanation();
        this.points = question.getPoints();
        this.displayOrder = question.getDisplayOrder();
        this.writingTaskType = question.getWritingTaskType();
    }

    public Long getId() { return id; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public Long getSectionVersionId() { return sectionVersionId; }
    public Long getGroupVersionId() { return groupVersionId; }
    public Long getQuestionId() { return questionId; }
    public Integer getQuestionNo() { return questionNo; }
    public String getQuestionType() { return questionType; }
    public String getPrompt() { return prompt; }
    public String getOptionsJson() { return optionsJson; }
    public String getAnswerKey() { return answerKey; }
    public String getExplanation() { return explanation; }
    public BigDecimal getPoints() { return points; }
    public Integer getDisplayOrder() { return displayOrder; }
    public WritingTaskType getWritingTaskType() { return writingTaskType; }
}
