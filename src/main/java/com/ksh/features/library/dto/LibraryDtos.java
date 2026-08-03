package com.ksh.features.library.dto;

import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for the lecturer personal file library (SSR page + JSON picker).
 */
public final class LibraryDtos {

    private LibraryDtos() {
    }

    /** One asset row on the SSR library page. */
    public record LibraryAssetRow(
            Long id,
            String title,
            String originalFilename,
            String kind,
            String mimeType,
            long sizeBytes,
            LocalDateTime updatedAt
    ) {
    }

    /** One editable class row for the library attach wizard. */
    public record AttachTargetClassRow(
            Long id,
            String name,
            String code
    ) {
    }

    /** One lesson-template row on the library "Bài giảng" tab. */
    public record LessonTemplateRow(
            Long id,
            String subjectCode,
            String chapterTitle,
            String title,
            String contentType,
            LocalDateTime updatedAt,
            int attachmentCount
    ) {
    }

    /** Paginated SSR view for the templates rail. */
    public record LessonTemplatePageView(
            Page<LessonTemplateRow> page,
            String q,
            String subjectCode,
            String subjectName,
            List<AttachTargetClassRow> classOptions,
            long templateCount
    ) {
    }

    /** Owned material option for Library lesson authoring. */
    public record MaterialOption(Long id, String title, String kind, String mimeType) {
    }

    /** Immutable subject identity shown on the Library authoring form. */
    public record SubjectContext(Long id, String code, String name) {
    }

    /** Result of cloning a template or lesson into a destination section. */
    public record LessonCloneResult(
            Long lessonId,
            Long classId,
            Long sectionId,
            String title
    ) {
    }
}
