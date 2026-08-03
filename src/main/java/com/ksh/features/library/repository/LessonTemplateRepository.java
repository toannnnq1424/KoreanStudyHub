package com.ksh.features.library.repository;

import com.ksh.entities.LessonTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

/**
 * Owner-scoped repository for {@link LessonTemplate}. Soft-deleted rows are
 * excluded by the entity {@code @SQLRestriction}.
 */
public interface LessonTemplateRepository extends JpaRepository<LessonTemplate, Long> {

    Optional<LessonTemplate> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("""
            SELECT t FROM LessonTemplate t
            WHERE t.ownerId = :ownerId
              AND t.subjectId = :subjectId
              AND (:q IS NULL OR :q = ''
                   OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.chapterTitle) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.chapterOrder ASC, t.displayOrder ASC, t.updatedAt DESC
            """)
    Page<LessonTemplate> searchOwnedSubject(@Param("ownerId") Long ownerId,
                                            @Param("subjectId") Long subjectId,
                                            @Param("q") String q,
                                            Pageable pageable);

    @Query("""
            SELECT t FROM LessonTemplate t
            WHERE t.subjectId = :subjectId
              AND (:q IS NULL OR :q = ''
                   OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.chapterTitle) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.chapterOrder ASC, t.displayOrder ASC, t.updatedAt DESC
            """)
    Page<LessonTemplate> searchSubject(@Param("subjectId") Long subjectId,
                                       @Param("q") String q,
                                       Pageable pageable);

    long countByOwnerIdAndSubjectId(Long ownerId, Long subjectId);

    long countBySubjectId(Long subjectId);

    List<LessonTemplate> findBySubjectIdOrderByChapterOrderAscDisplayOrderAscTitleAsc(Long subjectId);

    List<LessonTemplate> findByOwnerIdAndSubjectIdOrderByChapterOrderAscDisplayOrderAscTitleAsc(
            Long ownerId, Long subjectId);

    Optional<LessonTemplate> findByIdAndSubjectId(Long id, Long subjectId);
}
