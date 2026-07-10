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
}
