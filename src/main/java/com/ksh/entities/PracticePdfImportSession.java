package com.ksh.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Column(name = "target_test_no")
    private Integer targetTestNo;

    @Column(name = "target_skill", length = 20)
    private String targetSkill;

    @Column(name = "target_lesson_code", length = 20)
    private String targetLessonCode;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "stored_pdf_path")
    private String storedPdfPath;

    @Column(name = "storage_profile_code", length = 40)
    private String storageProfileCode;

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

    @Column(name = "generation_claim_token", length = 64)
    private String generationClaimToken;

    @Column(name = "generation_lease_expires_at")
    private LocalDateTime generationLeaseExpiresAt;

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

    public Integer getTargetTestNo() {
        return targetTestNo;
    }

    public void setTargetTestNo(Integer targetTestNo) {
        this.targetTestNo = targetTestNo;
    }

    public String getTargetSkill() {
        return targetSkill;
    }

    public void setTargetSkill(String targetSkill) {
        this.targetSkill = targetSkill;
    }

    public String getTargetLessonCode() {
        return targetLessonCode;
    }

    public void setTargetLessonCode(String targetLessonCode) {
        this.targetLessonCode = targetLessonCode;
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

    public String getStorageProfileCode() {
        return storageProfileCode;
    }

    public void setStorageProfileCode(String storageProfileCode) {
        this.storageProfileCode = storageProfileCode;
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

    public boolean hasLiveGenerationClaim(LocalDateTime now) {
        return "PROCESSING".equals(status)
                && generationClaimToken != null
                && generationLeaseExpiresAt != null
                && now != null
                && generationLeaseExpiresAt.isAfter(now);
    }

    public boolean hasCompletedGeneration() {
        return "AI_COMPLETED".equals(status) && linkedDraftId != null;
    }

    public void claimGeneration(
            String token,
            LocalDateTime leaseExpiresAt,
            LocalDateTime now) {
        if (token == null || token.isBlank() || token.length() > 64) {
            throw new IllegalArgumentException("generation token is invalid.");
        }
        if (leaseExpiresAt == null || now == null || !leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("generation lease is invalid.");
        }
        status = "PROCESSING";
        generationClaimToken = token;
        generationLeaseExpiresAt = leaseExpiresAt;
        updatedAt = now;
    }

    public void completeGeneration(
            String expectedToken,
            Long draftId,
            LocalDateTime now) {
        requireGenerationClaim(expectedToken);
        linkedDraftId = java.util.Objects.requireNonNull(draftId, "draftId");
        status = "AI_COMPLETED";
        clearGenerationClaim();
        updatedAt = java.util.Objects.requireNonNull(now, "now");
    }

    public void releaseGeneration(
            String expectedToken,
            String nextStatus,
            LocalDateTime now) {
        requireGenerationClaim(expectedToken);
        status = nextStatus;
        clearGenerationClaim();
        updatedAt = java.util.Objects.requireNonNull(now, "now");
    }

    public void markContentChanged(LocalDateTime now) {
        status = "ANNOTATING";
        clearGenerationClaim();
        updatedAt = java.util.Objects.requireNonNull(now, "now");
    }

    private void requireGenerationClaim(String expectedToken) {
        if (!"PROCESSING".equals(status)
                || generationClaimToken == null
                || expectedToken == null
                || !generationClaimToken.equals(expectedToken)) {
            throw new IllegalStateException("PDF AI generation claim mismatch.");
        }
    }

    private void clearGenerationClaim() {
        generationClaimToken = null;
        generationLeaseExpiresAt = null;
    }

    @JsonIgnore
    public String getGenerationClaimToken() {
        return generationClaimToken;
    }

    public LocalDateTime getGenerationLeaseExpiresAt() {
        return generationLeaseExpiresAt;
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
