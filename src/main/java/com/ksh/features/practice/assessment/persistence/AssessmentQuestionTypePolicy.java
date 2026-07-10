package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_question_type_policies")
public class AssessmentQuestionTypePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_version_id", nullable = false)
    private Long programVersionId;

    @Column(name = "skill_code", nullable = false, length = 30)
    private String skillCode;

    @Column(name = "canonical_question_type", nullable = false, length = 40)
    private String canonicalQuestionType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "default_scoring_policy_code", nullable = false, length = 80)
    private String defaultScoringPolicyCode;

    @Column(name = "scoring_profile_id")
    private Long scoringProfileId;

    @Column(name = "prompt_profile_id")
    private Long promptProfileId;

    @Column(name = "rubric_profile_id")
    private Long rubricProfileId;

    protected AssessmentQuestionTypePolicy() {
    }

    public AssessmentQuestionTypePolicy(Long programVersionId, String skillCode,
                                        String canonicalQuestionType, boolean enabled,
                                        String defaultScoringPolicyCode,
                                        Long scoringProfileId, Long promptProfileId, Long rubricProfileId) {
        this.programVersionId = programVersionId;
        this.skillCode = skillCode;
        this.canonicalQuestionType = canonicalQuestionType;
        this.enabled = enabled;
        this.defaultScoringPolicyCode = defaultScoringPolicyCode;
        this.scoringProfileId = scoringProfileId;
        this.promptProfileId = promptProfileId;
        this.rubricProfileId = rubricProfileId;
    }

    public Long getId() { return id; }
    public Long getProgramVersionId() { return programVersionId; }
    public String getSkillCode() { return skillCode; }
    public String getCanonicalQuestionType() { return canonicalQuestionType; }
    public boolean isEnabled() { return enabled; }
    public String getDefaultScoringPolicyCode() { return defaultScoringPolicyCode; }
    public Long getScoringProfileId() { return scoringProfileId; }
    public Long getPromptProfileId() { return promptProfileId; }
    public Long getRubricProfileId() { return rubricProfileId; }
}
