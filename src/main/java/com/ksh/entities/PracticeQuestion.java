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
    public static final String TYPE_SINGLE_CHOICE = "SINGLE_CHOICE";
    public static final String TYPE_MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String TYPE_TRUE_FALSE_NOT_GIVEN = "TRUE_FALSE_NOT_GIVEN";
    public static final String TYPE_MATCHING_INFORMATION = "MATCHING_INFORMATION";
    public static final String TYPE_MATCHING = "MATCHING";
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

    @Column(name = "question_type", nullable = false, length = 40)
    private String questionType;

    @Column(name = "canonical_question_type", length = 40)
    private String canonicalQuestionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "options_json", columnDefinition = "JSON")
    private String optionsJson;

    @Column(name = "question_content_json", columnDefinition = "JSON")
    private String questionContentJson;

    @Column(name = "answer_key", length = 500)
    private String answerKey;

    @Column(name = "answer_spec_json", columnDefinition = "JSON")
    private String answerSpecJson;

    @Column(name = "scoring_policy_code", length = 80)
    private String scoringPolicyCode;

    @Column(name = "scoring_profile_code", length = 100)
    private String scoringProfileCode;

    @Column(name = "scoring_profile_version")
    private Integer scoringProfileVersion;

    @Column(name = "prompt_profile_code", length = 100)
    private String promptProfileCode;

    @Column(name = "prompt_profile_version")
    private Integer promptProfileVersion;

    @Column(name = "rubric_profile_code", length = 100)
    private String rubricProfileCode;

    @Column(name = "rubric_profile_version")
    private Integer rubricProfileVersion;

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

    public String getCanonicalQuestionType() {
        return canonicalQuestionType;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public String getQuestionContentJson() {
        return questionContentJson;
    }

    public String getAnswerKey() {
        return answerKey;
    }

    public String getAnswerSpecJson() {
        return answerSpecJson;
    }

    public String getScoringPolicyCode() {
        return scoringPolicyCode;
    }

    public String getScoringProfileCode() {
        return scoringProfileCode;
    }

    public Integer getScoringProfileVersion() {
        return scoringProfileVersion;
    }

    public String getPromptProfileCode() {
        return promptProfileCode;
    }

    public Integer getPromptProfileVersion() {
        return promptProfileVersion;
    }

    public String getRubricProfileCode() {
        return rubricProfileCode;
    }

    public Integer getRubricProfileVersion() {
        return rubricProfileVersion;
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

    public void setCanonicalQuestionType(String canonicalQuestionType) {
        this.canonicalQuestionType = canonicalQuestionType;
    }

    public void setQuestionContentJson(String questionContentJson) {
        this.questionContentJson = questionContentJson;
    }

    public void setAnswerSpecJson(String answerSpecJson) {
        this.answerSpecJson = answerSpecJson;
    }

    public void setScoringPolicyCode(String scoringPolicyCode) {
        this.scoringPolicyCode = scoringPolicyCode;
    }

    public void setScoringProfileCode(String scoringProfileCode) {
        this.scoringProfileCode = scoringProfileCode;
    }

    public void setScoringProfileVersion(Integer scoringProfileVersion) {
        this.scoringProfileVersion = scoringProfileVersion;
    }

    public void setPromptProfileCode(String promptProfileCode) {
        this.promptProfileCode = promptProfileCode;
    }

    public void setPromptProfileVersion(Integer promptProfileVersion) {
        this.promptProfileVersion = promptProfileVersion;
    }

    public void setRubricProfileCode(String rubricProfileCode) {
        this.rubricProfileCode = rubricProfileCode;
    }

    public void setRubricProfileVersion(Integer rubricProfileVersion) {
        this.rubricProfileVersion = rubricProfileVersion;
    }

    public WritingTaskType getWritingTaskType() {
        return writingTaskType;
    }

    public void setWritingTaskType(WritingTaskType writingTaskType) {
        this.writingTaskType = writingTaskType;
    }
}
