package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_programs")
public class AssessmentProgram {

    @Id
    @Column(length = 40)
    private String code;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AssessmentProgram() {
    }

    public AssessmentProgram(String code, Long activeVersionId) {
        this.code = code;
        this.activeVersionId = activeVersionId;
    }

    public String getCode() {
        return code;
    }

    public Long getActiveVersionId() {
        return activeVersionId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void activateVersion(Long activeVersionId) {
        this.activeVersionId = activeVersionId;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
