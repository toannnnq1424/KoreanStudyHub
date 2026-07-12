package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_prompt_profiles")
public class AssessmentPromptProfile {

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

    @Column(name = "compatibility_adapter", length = 100)
    private String compatibilityAdapter;

    @Column(name = "system_rules", columnDefinition = "TEXT")
    private String systemRules;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "governance_status", nullable = false, length = 20)
    private String governanceStatus = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    protected AssessmentPromptProfile() {
    }

    public AssessmentPromptProfile(String code, Integer versionNumber, String skillCode,
                                   String taskType, String compatibilityAdapter,
                                   String systemRules, Long createdBy) {
        this.code = code;
        this.versionNumber = versionNumber;
        this.skillCode = skillCode;
        this.taskType = taskType;
        this.compatibilityAdapter = compatibilityAdapter;
        this.systemRules = systemRules;
        this.enabled = false;
        this.governanceStatus = "DRAFT";
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getSkillCode() { return skillCode; }
    public String getTaskType() { return taskType; }
    public String getCompatibilityAdapter() { return compatibilityAdapter; }
    public String getSystemRules() { return systemRules; }
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
