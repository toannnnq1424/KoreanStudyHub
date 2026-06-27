package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_edit_logs")
public class PracticeEditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "edited_by", nullable = false)
    private Long editedBy;

    @Column(name = "change_summary", nullable = false, length = 500)
    private String changeSummary;

    @Column(name = "change_details_json", columnDefinition = "LONGTEXT")
    private String changeDetailsJson;

    @Column(name = "before_snapshot_json", columnDefinition = "LONGTEXT")
    private String beforeSnapshotJson;

    @Column(name = "after_snapshot_json", columnDefinition = "LONGTEXT")
    private String afterSnapshotJson;

    @Column(name = "edit_type", length = 50)
    private String editType;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    protected PracticeEditLog() {
    }

    public PracticeEditLog(Long setId, Long editedBy, String changeSummary, String changeDetailsJson) {
        this.setId = setId;
        this.editedBy = editedBy;
        this.changeSummary = changeSummary;
        this.changeDetailsJson = changeDetailsJson;
        this.editedAt = LocalDateTime.now();
    }

    public PracticeEditLog(Long setId, Long editedBy, String changeSummary, String changeDetailsJson, 
                           String beforeSnapshotJson, String afterSnapshotJson, String editType) {
        this.setId = setId;
        this.editedBy = editedBy;
        this.changeSummary = changeSummary;
        this.changeDetailsJson = changeDetailsJson;
        this.beforeSnapshotJson = beforeSnapshotJson;
        this.afterSnapshotJson = afterSnapshotJson;
        this.editType = editType;
        this.editedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (editedAt == null) {
            editedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSetId() {
        return setId;
    }

    public void setSetId(Long setId) {
        this.setId = setId;
    }

    public Long getEditedBy() {
        return editedBy;
    }

    public void setEditedBy(Long editedBy) {
        this.editedBy = editedBy;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getChangeDetailsJson() {
        return changeDetailsJson;
    }

    public void setChangeDetailsJson(String changeDetailsJson) {
        this.changeDetailsJson = changeDetailsJson;
    }

    public String getBeforeSnapshotJson() {
        return beforeSnapshotJson;
    }

    public void setBeforeSnapshotJson(String beforeSnapshotJson) {
        this.beforeSnapshotJson = beforeSnapshotJson;
    }

    public String getAfterSnapshotJson() {
        return afterSnapshotJson;
    }

    public void setAfterSnapshotJson(String afterSnapshotJson) {
        this.afterSnapshotJson = afterSnapshotJson;
    }

    public String getEditType() {
        return editType;
    }

    public void setEditType(String editType) {
        this.editType = editType;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }
}
