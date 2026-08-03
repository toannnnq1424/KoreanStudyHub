package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Owner-scoped reusable lesson blueprint stored in {@code lesson_templates}.
 *
 * <p>File bodies always reference {@link LibraryAsset} rows — one-off lesson
 * uploads are promoted into the library before the template is saved.
 */
@Entity
@Table(name = "lesson_templates")
@SQLRestriction("is_deleted = 0")
public class LessonTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "chapter_title", nullable = false, length = 200)
    private String chapterTitle = "Chương 1";

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "content_richtext", columnDefinition = "LONGTEXT")
    private String contentRichtext;

    @Column(name = "pdf_library_asset_id")
    private Long pdfLibraryAssetId;

    @Column(name = "video_provider", length = 20)
    private String videoProvider;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "video_library_asset_id")
    private Long videoLibraryAssetId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA-only constructor. */
    protected LessonTemplate() {
    }

    /**
     * Creates a new template shell; callers fill type-specific body fields
     * before persist so the DB CHECK stays satisfied.
     */
    public LessonTemplate(Long ownerId, String title, String contentType) {
        this(ownerId, null, "Chương 1", title, contentType);
    }

    public LessonTemplate(Long ownerId, Long subjectId, String chapterTitle,
                          String title, String contentType) {
        Lesson.validateContentType(contentType);
        this.ownerId = ownerId;
        this.subjectId = subjectId;
        this.chapterTitle = chapterTitle;
        this.title = title;
        this.contentType = contentType;
        this.deleted = false;
        // RICHTEXT CHECK requires non-null body; PDF/VIDEO fill their FKs before flush.
        if (Lesson.CONTENT_TYPE_RICHTEXT.equals(contentType)) {
            this.contentRichtext = "";
        }
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Renames the display title only. */
    public void rename(String newTitle) {
        this.title = newTitle;
    }

    /** Updates hierarchy and clears type-specific body fields before remapping. */
    public void updateAuthoring(String chapterTitle, String title, String contentType) {
        Lesson.validateContentType(contentType);
        this.chapterTitle = chapterTitle;
        this.title = title;
        this.contentType = contentType;
        this.contentRichtext = Lesson.CONTENT_TYPE_RICHTEXT.equals(contentType) ? "" : null;
        this.pdfLibraryAssetId = null;
        this.videoProvider = null;
        this.videoUrl = null;
        this.videoLibraryAssetId = null;
    }

    /** Soft-deletes so default queries exclude the row. */
    public void markDeleted() {
        this.deleted = true;
    }

    public void setContentRichtext(String contentRichtext) {
        this.contentRichtext = contentRichtext;
    }

    public void setPdfLibraryAssetId(Long pdfLibraryAssetId) {
        this.pdfLibraryAssetId = pdfLibraryAssetId;
    }

    public void setVideoProvider(String videoProvider) {
        this.videoProvider = videoProvider;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public void setVideoLibraryAssetId(Long videoLibraryAssetId) {
        this.videoLibraryAssetId = videoLibraryAssetId;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getSubjectId() { return subjectId; }

    public String getTitle() {
        return title;
    }

    public String getChapterTitle() { return chapterTitle; }

    public int getDisplayOrder() { return displayOrder; }

    public String getContentType() {
        return contentType;
    }

    public String getContentRichtext() {
        return contentRichtext;
    }

    public Long getPdfLibraryAssetId() {
        return pdfLibraryAssetId;
    }

    public String getVideoProvider() {
        return videoProvider;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public Long getVideoLibraryAssetId() {
        return videoLibraryAssetId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
