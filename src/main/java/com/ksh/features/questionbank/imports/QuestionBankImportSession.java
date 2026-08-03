package com.ksh.features.questionbank.imports;

import com.ksh.features.questionbank.dto.QuestionBankImportDtos.Preview;
import com.ksh.features.questionbank.dto.QuestionBankImportDtos.PreviewRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** In-memory preview session for a single subject-scoped Excel upload. */
public final class QuestionBankImportSession {

    public static final long TTL_MINUTES = 10L;

    private final UUID id;
    private final Long actorId;
    private final Long subjectId;
    private final Long lessonTemplateId;
    private final Instant uploadedAt;
    private final String fileName;
    private final String workflowStatus;
    private final List<ImportedItem> items;
    private final List<PreviewRow> rows;

    public QuestionBankImportSession(UUID id,
                                     Long actorId,
                                     Long subjectId,
                                     Long lessonTemplateId,
                                     Instant uploadedAt,
                                     String fileName,
                                     String workflowStatus,
                                     List<ImportedItem> items,
                                     List<PreviewRow> rows) {
        this.id = id;
        this.actorId = actorId;
        this.subjectId = subjectId;
        this.lessonTemplateId = lessonTemplateId;
        this.uploadedAt = uploadedAt;
        this.fileName = fileName;
        this.workflowStatus = workflowStatus;
        this.items = List.copyOf(items);
        this.rows = List.copyOf(rows);
    }

    /** Compatibility constructor for session-store tests; real imports always
     * use the constructor carrying a canonical Library lesson id. */
    public QuestionBankImportSession(UUID id,
                                     Long actorId,
                                     Long subjectId,
                                     Instant uploadedAt,
                                     String fileName,
                                     String workflowStatus,
                                     List<ImportedItem> items,
                                     List<PreviewRow> rows) {
        this(id, actorId, subjectId, null, uploadedAt, fileName,
                workflowStatus, items, rows);
    }

    public UUID getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getLessonTemplateId() {
        return lessonTemplateId;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public List<ImportedItem> getItems() {
        return items;
    }

    public Preview toPreview() {
        int acceptedRows = items.size();
        int errorRows = (int) rows.stream().filter(PreviewRow::blocking).count();
        return new Preview(id, fileName, rows.size(), acceptedRows, errorRows, errorRows == 0 && acceptedRows > 0, rows);
    }

    public boolean isExpired(Instant now) {
        return uploadedAt.plusSeconds(TTL_MINUTES * 60).isBefore(now);
    }

    public record ImportedItem(String questionType,
                               String contentHtml,
                               String explanationHtml,
                               List<ImportedOption> options) {
    }

    public record ImportedOption(String contentHtml, boolean correct, int sortOrder) {
    }
}
