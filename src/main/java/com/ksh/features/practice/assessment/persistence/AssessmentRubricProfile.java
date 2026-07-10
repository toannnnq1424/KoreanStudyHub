package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_rubric_profiles")
public class AssessmentRubricProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "skill_code", nullable = false, length = 30)
    private String skillCode;

    @Column(name = "task_type", length = 40)
    private String taskType;

    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    private String configJson;

    @Column(nullable = false)
    private boolean enabled;

    protected AssessmentRubricProfile() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getSkillCode() { return skillCode; }
    public String getTaskType() { return taskType; }
    public String getConfigJson() { return configJson; }
    public boolean isEnabled() { return enabled; }
}
