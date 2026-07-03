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
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_questions")
public class PracticeQuestion {

    public static final String TYPE_MCQ = "MCQ";
    public static final String TYPE_TRUE_FALSE_NOT_GIVEN = "TRUE_FALSE_NOT_GIVEN";
    public static final String TYPE_MATCHING_INFORMATION = "MATCHING_INFORMATION";
    public static final String TYPE_FILL_BLANK = "FILL_BLANK";
    public static final String TYPE_ORDERING = "ORDERING";
    public static final String TYPE_TEXT_COMPLETION = "TEXT_COMPLETION";
    public static final String TYPE_SHORT_TEXT = "SHORT_TEXT";
    public static final String TYPE_ESSAY = "ESSAY";
    public static final String TYPE_SPEAKING = "SPEAKING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

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

    @Column(name = "group_id")
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "writing_task_type", length = 20)
    private WritingTaskType writingTaskType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PracticeQuestion() {
    }

    public PracticeQuestion(Long setId, Integer questionNo, String questionType, String prompt,
                            String optionsJson, String answerKey, String explanation,
                            BigDecimal points, Integer displayOrder) {
        this.setId = setId;
        this.questionNo = questionNo;
        this.questionType = questionType;
        this.prompt = prompt;
        this.optionsJson = optionsJson;
        this.answerKey = answerKey;
        this.explanation = explanation;
        this.points = points;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getSetId() {
        return setId;
    }

    public Integer getQuestionNo() {
        return questionNo;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public String getAnswerKey() {
        return answerKey;
    }

    public String getExplanation() {
        return explanation;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public WritingTaskType getWritingTaskType() {
        return writingTaskType;
    }

    public void setWritingTaskType(WritingTaskType writingTaskType) {
        this.writingTaskType = writingTaskType;
    }
}
