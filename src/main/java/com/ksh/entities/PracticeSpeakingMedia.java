package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "practice_speaking_media",
        uniqueConstraints = @UniqueConstraint(name = "uk_psm_storage", columnNames = {"storage_provider", "storage_key"})
)
public class PracticeSpeakingMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 32)
    private PracticeSpeakingStorageProvider storageProvider;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(nullable = false, length = 32)
    private String container;

    @Column(nullable = false, length = 64)
    private String codec;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PracticeSpeakingMediaStatus status;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected PracticeSpeakingMedia() {
    }

    private PracticeSpeakingMedia(Long attemptId,
                                  Long questionId,
                                  PracticeSpeakingStorageProvider storageProvider,
                                  String storageKey,
                                  String mimeType,
                                  String container,
                                  String codec,
                                  Long byteSize,
                                  Long durationMs,
                                  String contentHash) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.container = container;
        this.codec = codec;
        this.byteSize = byteSize;
        this.durationMs = durationMs;
        this.contentHash = contentHash;
        this.status = PracticeSpeakingMediaStatus.READY;
    }

    public static PracticeSpeakingMedia ready(Long attemptId,
                                              Long questionId,
                                              PracticeSpeakingStorageProvider storageProvider,
                                              String storageKey,
                                              String mimeType,
                                              String container,
                                              String codec,
                                              Long byteSize,
                                              Long durationMs,
                                              String contentHash) {
        return new PracticeSpeakingMedia(
                attemptId, questionId, storageProvider, storageKey, mimeType, container, codec, byteSize, durationMs,
                contentHash);
    }

    public void markSuperseded() {
        if (status != PracticeSpeakingMediaStatus.READY) {
            throw new IllegalStateException("Only READY speaking media can be superseded.");
        }
        status = PracticeSpeakingMediaStatus.SUPERSEDED;
    }

    public void markDeleted() {
        if (status == PracticeSpeakingMediaStatus.DELETED) {
            return;
        }
        if (status != PracticeSpeakingMediaStatus.READY && status != PracticeSpeakingMediaStatus.SUPERSEDED) {
            throw new IllegalStateException("Speaking media cannot be deleted from current state.");
        }
        status = PracticeSpeakingMediaStatus.DELETED;
        deletedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getQuestionId() { return questionId; }
    public PracticeSpeakingStorageProvider getStorageProvider() { return storageProvider; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public String getContainer() { return container; }
    public String getCodec() { return codec; }
    public Long getByteSize() { return byteSize; }
    public Long getDurationMs() { return durationMs; }
    public String getContentHash() { return contentHash; }
    public PracticeSpeakingMediaStatus getStatus() { return status; }
    public Long getLockVersion() { return lockVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
