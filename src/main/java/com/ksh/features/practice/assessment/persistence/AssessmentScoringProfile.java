package com.ksh.features.practice.assessment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_scoring_profiles")
public class AssessmentScoringProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    private String configJson;

    @Column(nullable = false)
    private boolean enabled;

    protected AssessmentScoringProfile() {
    }

    public AssessmentScoringProfile(String code, Integer versionNumber, String configJson, boolean enabled) {
        this.code = code;
        this.versionNumber = versionNumber;
        this.configJson = configJson;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getConfigJson() { return configJson; }
    public boolean isEnabled() { return enabled; }
}
