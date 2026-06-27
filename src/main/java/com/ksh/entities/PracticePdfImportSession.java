package com.ksh.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_pdf_import_sessions")
public class PracticePdfImportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "title")
    private String title;

    @Column(name = "exam_category")
    private String examCategory;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "stored_pdf_path")
    private String storedPdfPath;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "selected_start_page")
    private Integer selectedStartPage;

    @Column(name = "selected_end_page")
    private Integer selectedEndPage;

    @Column(name = "current_page", nullable = false)
    private Integer currentPage = 1;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "extraction_strategy")
    private String extractionStrategy;

    @Column(name = "linked_draft_id")
    private Long linkedDraftId;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public PracticePdfImportSession() {
    }

    public PracticePdfImportSession(Long uploaderId, String originalFilename, String storedPdfPath,
                                    Integer totalPages, String status, LocalDateTime createdAt,
                                    LocalDateTime updatedAt, LocalDateTime expiresAt) {
        this.uploaderId = uploaderId;
        this.createdBy = uploaderId;
        this.originalFilename = originalFilename;
        this.storedPdfPath = storedPdfPath;
        this.totalPages = totalPages;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
        this.selectedStartPage = 1;
        this.selectedEndPage = totalPages != null && totalPages > 0 ? totalPages : 1;
        this.currentPage = 1;
        this.extractionStrategy = "HYBRID";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExamCategory() {
        return examCategory;
    }

    public void setExamCategory(String examCategory) {
        this.examCategory = examCategory;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredPdfPath() {
        return storedPdfPath;
    }

    public void setStoredPdfPath(String storedPdfPath) {
        this.storedPdfPath = storedPdfPath;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getSelectedStartPage() {
        return selectedStartPage;
    }

    public void setSelectedStartPage(Integer selectedStartPage) {
        this.selectedStartPage = selectedStartPage;
    }

    public Integer getSelectedEndPage() {
        return selectedEndPage;
    }

    public void setSelectedEndPage(Integer selectedEndPage) {
        this.selectedEndPage = selectedEndPage;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExtractionStrategy() {
        return extractionStrategy;
    }

    public void setExtractionStrategy(String extractionStrategy) {
        this.extractionStrategy = extractionStrategy;
    }

    public Long getLinkedDraftId() {
        return linkedDraftId;
    }

    public void setLinkedDraftId(Long linkedDraftId) {
        this.linkedDraftId = linkedDraftId;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public LocalDateTime getLastSavedAt() {
        return lastSavedAt;
    }

    public void setLastSavedAt(LocalDateTime lastSavedAt) {
        this.lastSavedAt = lastSavedAt;
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

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
