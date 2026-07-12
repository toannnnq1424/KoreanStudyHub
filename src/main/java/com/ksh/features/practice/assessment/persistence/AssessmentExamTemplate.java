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

    @Column(name = "program_code", nullable = false, length = 40)
    private String programCode;

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

    @Column(name = "active_version_id")
    private Long activeVersionId;

    protected AssessmentExamTemplate() {
    }

    public AssessmentExamTemplate(String code, String programCode, Long programVersionId,
                                  String displayName, String categoryCode, boolean enabled,
                                  String configJson) {
        this.code = code;
        this.programCode = programCode;
        this.programVersionId = programVersionId;
        this.displayName = displayName;
        this.categoryCode = categoryCode;
        this.enabled = enabled;
        this.configJson = configJson;
    }

    public String getCode() { return code; }
    public String getProgramCode() { return programCode; }
    public Long getProgramVersionId() { return programVersionId; }
    public String getDisplayName() { return displayName; }
    public String getCategoryCode() { return categoryCode; }
    public boolean isEnabled() { return enabled; }
    public String getConfigJson() { return configJson; }
    public Long getActiveVersionId() { return activeVersionId; }

    public void activateVersion(Long versionId, String compatibilityConfigJson) {
        this.activeVersionId = versionId;
        this.configJson = compatibilityConfigJson;
    }

    public void activateVersion(Long versionId, Long compatibleProgramVersionId,
                                String compatibilityConfigJson) {
        this.activeVersionId = versionId;
        this.programVersionId = compatibleProgramVersionId;
        this.configJson = compatibilityConfigJson;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
