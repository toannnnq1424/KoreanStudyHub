package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_exam_templates")
public class AssessmentExamTemplate {

    @Id
    @Column(length = 80)
    private String code;

    @Column(name = "program_version_id", nullable = false)
    private Long programVersionId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    private String configJson;

    protected AssessmentExamTemplate() {
    }

    public String getCode() { return code; }
    public Long getProgramVersionId() { return programVersionId; }
    public String getDisplayName() { return displayName; }
    public String getCategoryCode() { return categoryCode; }
    public boolean isEnabled() { return enabled; }
    public String getConfigJson() { return configJson; }
}
