package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "practice_test_versions")
public class PracticeTestVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "published_version_id", nullable = false)
    private Long publishedVersionId;

    @Column(name = "set_version_id", nullable = false)
    private Long setVersionId;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    protected PracticeTestVersion() {
    }

    public PracticeTestVersion(Long publishedVersionId, Long setVersionId, PracticeTest test) {
        this.publishedVersionId = publishedVersionId;
        this.setVersionId = setVersionId;
        this.testId = test.getId();
        this.title = test.getTitle();
        this.description = test.getDescription();
        this.displayOrder = test.getDisplayOrder();
        this.estimatedMinutes = test.getEstimatedMinutes();
    }

    public Long getId() { return id; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public Long getSetVersionId() { return setVersionId; }
    public Long getTestId() { return testId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
}
