package com.ksh.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_pdf_region_annotations")
public class PracticePdfRegionAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "region_type", nullable = false)
    private String regionType;

    @Column(name = "x_ratio", nullable = false)
    private Double xRatio;

    @Column(name = "y_ratio", nullable = false)
    private Double yRatio;

    @Column(name = "width_ratio", nullable = false)
    private Double widthRatio;

    @Column(name = "height_ratio", nullable = false)
    private Double heightRatio;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "section_temp_id")
    private String sectionTempId;

    @Column(name = "group_temp_id")
    private String groupTempId;

    @Column(name = "expected_question_type")
    private String expectedQuestionType;

    @Column(name = "expected_question_from")
    private Integer expectedQuestionFrom;

    @Column(name = "expected_question_to")
    private Integer expectedQuestionTo;

    @Column(name = "target_question_no")
    private Integer targetQuestionNo;

    @Column(name = "option_index")
    private Integer optionIndex;

    @Column(name = "asset_placement")
    private String assetPlacement;

    @Column(name = "include_in_ai", nullable = false)
    private Boolean includeInAi = true;

    @Column(name = "include_text_in_ai", nullable = false)
    private Boolean includeTextInAi = true;

    @Column(name = "include_image_in_ai", nullable = false)
    private Boolean includeImageInAi = true;

    @Column(name = "save_to_asset_library", nullable = false)
    private Boolean saveToAssetLibrary = false;

    @Column(name = "lecturer_note")
    private String lecturerNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PracticePdfRegionAnnotation() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getRegionType() {
        return regionType;
    }

    public void setRegionType(String regionType) {
        this.regionType = regionType;
    }

    public Double getxRatio() {
        return xRatio;
    }

    public void setxRatio(Double xRatio) {
        this.xRatio = xRatio;
    }

    public Double getyRatio() {
        return yRatio;
    }

    public void setyRatio(Double yRatio) {
        this.yRatio = yRatio;
    }

    public Double getWidthRatio() {
        return widthRatio;
    }

    public void setWidthRatio(Double widthRatio) {
        this.widthRatio = widthRatio;
    }

    public Double getHeightRatio() {
        return heightRatio;
    }

    public void setHeightRatio(Double heightRatio) {
        this.heightRatio = heightRatio;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
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

    public String getExpectedQuestionType() {
        return expectedQuestionType;
    }

    public void setExpectedQuestionType(String expectedQuestionType) {
        this.expectedQuestionType = expectedQuestionType;
    }

    public Integer getExpectedQuestionFrom() {
        return expectedQuestionFrom;
    }

    public void setExpectedQuestionFrom(Integer expectedQuestionFrom) {
        this.expectedQuestionFrom = expectedQuestionFrom;
    }

    public Integer getExpectedQuestionTo() {
        return expectedQuestionTo;
    }

    public void setExpectedQuestionTo(Integer expectedQuestionTo) {
        this.expectedQuestionTo = expectedQuestionTo;
    }

    public Integer getTargetQuestionNo() {
        return targetQuestionNo;
    }

    public void setTargetQuestionNo(Integer targetQuestionNo) {
        this.targetQuestionNo = targetQuestionNo;
    }

    public Integer getOptionIndex() {
        return optionIndex;
    }

    public void setOptionIndex(Integer optionIndex) {
        this.optionIndex = optionIndex;
    }

    public String getAssetPlacement() {
        return assetPlacement;
    }

    public void setAssetPlacement(String assetPlacement) {
        this.assetPlacement = assetPlacement;
    }

    public Boolean getIncludeInAi() {
        return includeInAi;
    }

    public void setIncludeInAi(Boolean includeInAi) {
        this.includeInAi = includeInAi;
    }

    public Boolean getIncludeTextInAi() {
        return includeTextInAi;
    }

    public void setIncludeTextInAi(Boolean includeTextInAi) {
        this.includeTextInAi = includeTextInAi;
    }

    public Boolean getIncludeImageInAi() {
        return includeImageInAi;
    }

    public void setIncludeImageInAi(Boolean includeImageInAi) {
        this.includeImageInAi = includeImageInAi;
    }

    public Boolean getSaveToAssetLibrary() {
        return saveToAssetLibrary;
    }

    public void setSaveToAssetLibrary(Boolean saveToAssetLibrary) {
        this.saveToAssetLibrary = saveToAssetLibrary;
    }

    public String getLecturerNote() {
        return lecturerNote;
    }

    public void setLecturerNote(String lecturerNote) {
        this.lecturerNote = lecturerNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
