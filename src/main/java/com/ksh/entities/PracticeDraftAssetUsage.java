package com.ksh.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_draft_asset_usages")
public class PracticeDraftAssetUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "section_temp_id")
    private String sectionTempId;

    @Column(name = "group_temp_id")
    private String groupTempId;

    @Column(name = "question_temp_id")
    private String questionTempId;

    @Column(name = "placement", nullable = false)
    private String placement;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "caption")
    private String caption;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PracticeDraftAssetUsage() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getSectionTempId() {
        return sectionTempId;
    }

    public void setSectionTempId(String sectionTempId) {
        this.sectionTempId = sectionTempId;
    }

    public String getGroupTempId() {
        return groupTempId;
    }

    public void setGroupTempId(String groupTempId) {
        this.groupTempId = groupTempId;
    }

    public String getQuestionTempId() {
        return questionTempId;
    }

    public void setQuestionTempId(String questionTempId) {
        this.questionTempId = questionTempId;
    }

    public String getPlacement() {
        return placement;
    }

    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
