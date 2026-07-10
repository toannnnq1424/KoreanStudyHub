package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_published_versions")
public class PracticePublishedVersion {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "content_hash", columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PracticePublishedVersion() {
    }

    public PracticePublishedVersion(Long setId, Integer versionNumber, String status,
                                    String contentHash, Long publishedBy) {
        this.setId = setId;
        this.versionNumber = versionNumber;
        this.status = status;
        this.contentHash = contentHash;
        this.publishedBy = publishedBy;
        this.publishedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSetId() { return setId; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getStatus() { return status; }
    public String getContentHash() { return contentHash; }
    public Long getPublishedBy() { return publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
