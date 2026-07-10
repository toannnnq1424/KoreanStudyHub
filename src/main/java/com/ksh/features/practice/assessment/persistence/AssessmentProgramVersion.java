package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_program_versions")
public class AssessmentProgramVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_code", nullable = false, length = 40)
    private String programCode;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "default_language", nullable = false, length = 16)
    private String defaultLanguage;

    protected AssessmentProgramVersion() {
    }

    public AssessmentProgramVersion(String programCode, Integer versionNumber, String displayName,
                                    String status, String defaultLanguage) {
        this.programCode = programCode;
        this.versionNumber = versionNumber;
        this.displayName = displayName;
        this.status = status;
        this.defaultLanguage = defaultLanguage;
    }

    public Long getId() { return id; }
    public String getProgramCode() { return programCode; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public String getDefaultLanguage() { return defaultLanguage; }
}
