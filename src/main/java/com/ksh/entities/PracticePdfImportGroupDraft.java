package com.ksh.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "practice_pdf_import_group_drafts")
public class PracticePdfImportGroupDraft {

    @Id
    @Column(name = "temp_id", length = 128)
    private String tempId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "section_temp_id", length = 128)
    private String sectionTempId;

    @Column(name = "title")
    private String title;

    @Column(name = "expected_from")
    private Integer expectedFrom;

    @Column(name = "expected_to")
    private Integer expectedTo;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    public PracticePdfImportGroupDraft() {
    }

    public String getTempId() {
        return tempId;
    }

    public void setTempId(String tempId) {
        this.tempId = tempId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSectionTempId() {
        return sectionTempId;
    }

    public void setSectionTempId(String sectionTempId) {
        this.sectionTempId = sectionTempId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getExpectedFrom() {
        return expectedFrom;
    }

    public void setExpectedFrom(Integer expectedFrom) {
        this.expectedFrom = expectedFrom;
    }

    public Integer getExpectedTo() {
        return expectedTo;
    }

    public void setExpectedTo(Integer expectedTo) {
        this.expectedTo = expectedTo;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
