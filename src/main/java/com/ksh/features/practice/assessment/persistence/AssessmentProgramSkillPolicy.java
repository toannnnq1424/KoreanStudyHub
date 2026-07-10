package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_program_skill_policies")
public class AssessmentProgramSkillPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_version_id", nullable = false)
    private Long programVersionId;

    @Column(name = "skill_code", nullable = false, length = 30)
    private String skillCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "delivery_mode", nullable = false, length = 40)
    private String deliveryMode;

    protected AssessmentProgramSkillPolicy() {
    }

    public AssessmentProgramSkillPolicy(Long programVersionId, String skillCode,
                                        boolean enabled, String deliveryMode) {
        this.programVersionId = programVersionId;
        this.skillCode = skillCode;
        this.enabled = enabled;
        this.deliveryMode = deliveryMode;
    }

    public Long getId() { return id; }
    public Long getProgramVersionId() { return programVersionId; }
    public String getSkillCode() { return skillCode; }
    public boolean isEnabled() { return enabled; }
    public String getDeliveryMode() { return deliveryMode; }
}
