package com.ksh.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "practice_pdf_page_extractions")
public class PracticePdfPageExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "normalized_text", columnDefinition = "LONGTEXT")
    private String normalizedText;

    @Column(name = "raw_char_count", nullable = false)
    private Integer rawCharCount = 0;

    @Column(name = "extraction_status", nullable = false)
    private String extractionStatus = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PracticePdfPageExtraction() {
    }

    public PracticePdfPageExtraction(Long sessionId, Integer pageNumber, String rawText,
                                     String normalizedText, Integer rawCharCount, String extractionStatus,
                                     LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.pageNumber = pageNumber;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.rawCharCount = rawCharCount != null ? rawCharCount : 0;
        this.extractionStatus = extractionStatus;
        this.createdAt = createdAt;
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

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public void setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public Integer getRawCharCount() {
        return rawCharCount;
    }

    public void setRawCharCount(Integer rawCharCount) {
        this.rawCharCount = rawCharCount;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
