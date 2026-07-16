package com.ksh.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "practice_pdf_import_section_drafts")
public class PracticePdfImportSectionDraft {

    @Id
    @Column(name = "temp_id", length = 128)
    private String tempId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "title")
    private String title;

    @Column(name = "skill")
    private String skill;

    @Column(name = "section_type")
    private String sectionType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    public PracticePdfImportSectionDraft() {
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSkill() {
        return skill;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }

    public String getSectionType() {
        return sectionType;
    }

    public void setSectionType(String sectionType) {
        this.sectionType = sectionType;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
