package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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

    @Column(name = "governance_status", nullable = false, length = 20)
    private String governanceStatus = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    protected AssessmentRubricProfile() {
    }

    public AssessmentRubricProfile(String code, Integer versionNumber, String skillCode,
                                   String taskType, String configJson, Long createdBy) {
        this.code = code;
        this.versionNumber = versionNumber;
        this.skillCode = skillCode;
        this.taskType = taskType;
        this.configJson = configJson;
        this.enabled = false;
        this.governanceStatus = "DRAFT";
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getSkillCode() { return skillCode; }
    public String getTaskType() { return taskType; }
    public String getConfigJson() { return configJson; }
    public boolean isEnabled() { return enabled; }
    public String getGovernanceStatus() { return governanceStatus; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void activate() {
        this.enabled = true;
        this.governanceStatus = "ACTIVE";
        this.activatedAt = LocalDateTime.now();
    }
}
