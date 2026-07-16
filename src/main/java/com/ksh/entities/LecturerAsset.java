package com.ksh.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lecturer_assets")
public class LecturerAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_lecturer_id", nullable = false)
    private Long ownerLecturerId;

    @Column(name = "source_import_session_id")
    private Long sourceImportSessionId;

    @Column(name = "source_region_id")
    private Long sourceRegionId;

    @Column(name = "storage_provider", nullable = false)
    private String storageProvider = "LOCAL";

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "content_verified", nullable = false)
    private boolean contentVerified;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "size_bytes", nullable = false)
    private Long fileSize = 0L; // Map to size_bytes column

    @Column(name = "asset_type", nullable = false)
    private String assetType;

    @Column(name = "title")
    private String title; // acts as displayName

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "sha256")
    private String sha256;

    @Column(name = "source_type", nullable = false)
    private String sourceType = "PDF_REGION";

    @Column(name = "source_page_number")
    private Integer sourcePageNumber;

    @Column(name = "crop_x")
    private Double cropX;

    @Column(name = "crop_y")
    private Double cropY;

    @Column(name = "crop_width")
    private Double cropWidth;

    @Column(name = "crop_height")
    private Double cropHeight;

    @Column(name = "lecturer_note")
    private String lecturerNote;

    @Column(name = "tags_json")
    private String tagsJson;

    @Column(name = "status", nullable = false)
    private String status = "TEMPORARY";

    @Column(name = "visibility", nullable = false)
    private String visibility = "PRIVATE";

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public LecturerAsset() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerLecturerId() {
        return ownerLecturerId;
    }

    public void setOwnerLecturerId(Long ownerLecturerId) {
        this.ownerLecturerId = ownerLecturerId;
    }

    public Long getSourceImportSessionId() {
        return sourceImportSessionId;
    }

    public void setSourceImportSessionId(Long sourceImportSessionId) {
        this.sourceImportSessionId = sourceImportSessionId;
    }

    public Long getSourceRegionId() {
        return sourceRegionId;
    }

    public void setSourceRegionId(Long sourceRegionId) {
        this.sourceRegionId = sourceRegionId;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isContentVerified() {
        return contentVerified;
    }

    public void setContentVerified(boolean contentVerified) {
        this.contentVerified = contentVerified;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getSourcePageNumber() {
        return sourcePageNumber;
    }

    public void setSourcePageNumber(Integer sourcePageNumber) {
        this.sourcePageNumber = sourcePageNumber;
    }

    public Double getCropX() {
        return cropX;
    }

    public void setCropX(Double cropX) {
        this.cropX = cropX;
    }

    public Double getCropY() {
        return cropY;
    }

    public void setCropY(Double cropY) {
        this.cropY = cropY;
    }

    public Double getCropWidth() {
        return cropWidth;
    }

    public void setCropWidth(Double cropWidth) {
        this.cropWidth = cropWidth;
    }

    public Double getCropHeight() {
        return cropHeight;
    }

    public void setCropHeight(Double cropHeight) {
        this.cropHeight = cropHeight;
    }

    public String getLecturerNote() {
        return lecturerNote;
    }

    public void setLecturerNote(String lecturerNote) {
        this.lecturerNote = lecturerNote;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public LocalDateTime getRetentionUntil() {
        return retentionUntil;
    }

    public void setRetentionUntil(LocalDateTime retentionUntil) {
        this.retentionUntil = retentionUntil;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
