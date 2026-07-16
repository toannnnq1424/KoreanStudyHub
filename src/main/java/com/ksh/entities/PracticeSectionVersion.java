package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "practice_section_versions")
public class PracticeSectionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "published_version_id", nullable = false)
    private Long publishedVersionId;

    @Column(name = "test_version_id", nullable = false)
    private Long testVersionId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 20)
    private String skill;

    @Column(name = "section_type", length = 50)
    private String sectionType;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "delivery_json", columnDefinition = "JSON")
    private String deliveryJson;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_points", precision = 6, scale = 2)
    private BigDecimal totalPoints;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected PracticeSectionVersion() {
    }

    public PracticeSectionVersion(Long publishedVersionId, Long testVersionId, PracticeSection section) {
        this.publishedVersionId = publishedVersionId;
        this.testVersionId = testVersionId;
        this.sectionId = section.getId();
        this.title = section.getTitle();
        this.skill = section.getSkill();
        this.sectionType = section.getSectionType();
        this.instructions = section.getInstructions();
        this.deliveryJson = section.getDeliveryJson();
        this.durationMinutes = section.getDurationMinutes();
        this.totalPoints = section.getTotalPoints();
        this.displayOrder = section.getDisplayOrder();
    }

    public Long getId() { return id; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public Long getTestVersionId() { return testVersionId; }
    public Long getSectionId() { return sectionId; }
    public String getTitle() { return title; }
    public String getSkill() { return skill; }
    public String getSectionType() { return sectionType; }
    public String getInstructions() { return instructions; }
    public String getDeliveryJson() { return deliveryJson; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getTotalPoints() { return totalPoints; }
    public Integer getDisplayOrder() { return displayOrder; }
}
