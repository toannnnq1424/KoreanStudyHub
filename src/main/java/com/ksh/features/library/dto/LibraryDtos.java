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
            String code,
            boolean alreadyDistributed
    ) {
    }

    /** One lesson-template row on the library "Bài giảng" tab. */
    public record LessonTemplateRow(
            Long id,
            String subjectCode,
            int chapterNumber,
            String chapterTitle,
            int lessonNumber,
            String title,
            String contentType,
            LocalDateTime updatedAt,
            int attachmentCount,
            boolean canManage,
            List<LessonResourceRow> resources
    ) {
    }

    /** One persisted resource currently attached to a Library lesson. */
    public record LessonResourceRow(Long assetId, String kind, String label, String name) {
    }

    /** One chapter in the subject tree, containing its ordered lessons. */
    public record ChapterView(int number, String title, List<LessonTemplateRow> lessons,
                              boolean canManage) {
    }

    /** Paginated SSR view for the templates rail. */
    public record LessonTemplatePageView(
            Page<LessonTemplateRow> page,
            String q,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String subjectDescription,
            List<SubjectContext> subjectOptions,
            List<AttachTargetClassRow> classOptions,
            List<ChapterView> chapters,
            long templateCount
    ) {
    }

    /** Owned material option for Library lesson authoring. */
    public record MaterialOption(Long id, String title, String kind, String mimeType) {
    }

    /** Subject identity shown in the Library selector and authoring form. */
    public record SubjectContext(Long id, String code, String name, String description) {
    }

    /** Result of cloning a template or lesson into a destination section. */
    public record LessonCloneResult(
            Long lessonId,
            Long classId,
            Long sectionId,
            String title
    ) {
    }

    /** Subject with library statistics for the list view. */
    public record SubjectLibraryStats(
            Long id,
            String code,
            String name,
            String description,
            int chapterCount,
            int lessonCount
    ) {
    }
}
