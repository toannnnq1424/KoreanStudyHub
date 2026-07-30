package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSet;
import com.ksh.entities.WritingTaskType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeSetRepository extends JpaRepository<PracticeSet, Long> {
    List<PracticeSet> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    List<PracticeSet> findByCreatedByAndStatusOrderByCreatedAtDesc(Long createdBy, String status);
    List<PracticeSet> findByCreatedByNotOrderByCreatedAtDesc(Long createdBy, Pageable pageable);
    Optional<PracticeSet> findByIdAndCreatedBy(Long id, Long createdBy);

    /**
     * Public learner catalogue. Practice is intentionally independent from the
     * classroom domain, so only published GLOBAL sets participate here.
     */
    @Query(value = """
            select s from PracticeSet s
            where s.status = :status
              and s.scope = :globalScope
              and (
                    :search = ''
                    or lower(s.title) like lower(concat('%', :search, '%'))
                    or lower(coalesce(s.description, '')) like lower(concat('%', :search, '%'))
                  )
              and (
                    :skill = ''
                    or s.skill = :skill
                    or exists (
                        select section.id from PracticeSection section
                        where section.setId = s.id and section.skill = :skill
                    )
                  )
              and (
                    :writingTask is null
                    or exists (
                        select question.id from PracticeQuestion question
                        where question.setId = s.id
                          and question.writingTaskType = :writingTask
                    )
                  )
            order by s.createdAt desc, s.id desc
            """,
            countQuery = """
            select count(s) from PracticeSet s
            where s.status = :status
              and s.scope = :globalScope
              and (
                    :search = ''
                    or lower(s.title) like lower(concat('%', :search, '%'))
                    or lower(coalesce(s.description, '')) like lower(concat('%', :search, '%'))
                  )
              and (
                    :skill = ''
                    or s.skill = :skill
                    or exists (
                        select section.id from PracticeSection section
                        where section.setId = s.id and section.skill = :skill
                    )
                  )
              and (
                    :writingTask is null
                    or exists (
                        select question.id from PracticeQuestion question
                        where question.setId = s.id
                          and question.writingTaskType = :writingTask
                    )
                  )
            """)
    Page<PracticeSet> findPublishedGlobalCatalog(
            @Param("status") String status,
            @Param("globalScope") String globalScope,
            @Param("search") String search,
            @Param("skill") String skill,
            @Param("writingTask") WritingTaskType writingTask,
            Pageable pageable);

    /**
     * Legacy class-aware lookup retained for non-catalog compatibility tests.
     * New learner catalogue code must use {@link #findPublishedGlobalCatalog}.
     */
    @Query(value = """
            select s from PracticeSet s
            where s.status = :status
              and (
                    s.scope = :globalScope
                    or s.createdBy = :userId
                    or (s.scope = :classScope and s.classId in :activeClassIds)
                  )
              and (:selectedClassId = 0 or s.classId = :selectedClassId)
              and (
                    :search = ''
                    or lower(s.title) like lower(concat('%', :search, '%'))
                    or lower(coalesce(s.description, '')) like lower(concat('%', :search, '%'))
                  )
              and (
                    :skill = ''
                    or s.skill = :skill
                    or exists (
                        select section.id from PracticeSection section
                        where section.setId = s.id and section.skill = :skill
                    )
                  )
              and (
                    :writingTask is null
                    or exists (
                        select question.id from PracticeQuestion question
                        where question.setId = s.id
                          and question.writingTaskType = :writingTask
                    )
                  )
            order by s.createdAt desc, s.id desc
            """,
            countQuery = """
            select count(s) from PracticeSet s
            where s.status = :status
              and (
                    s.scope = :globalScope
                    or s.createdBy = :userId
                    or (s.scope = :classScope and s.classId in :activeClassIds)
                  )
              and (:selectedClassId = 0 or s.classId = :selectedClassId)
              and (
                    :search = ''
                    or lower(s.title) like lower(concat('%', :search, '%'))
                    or lower(coalesce(s.description, '')) like lower(concat('%', :search, '%'))
                  )
              and (
                    :skill = ''
                    or s.skill = :skill
                    or exists (
                        select section.id from PracticeSection section
                        where section.setId = s.id and section.skill = :skill
                    )
                  )
              and (
                    :writingTask is null
                    or exists (
                        select question.id from PracticeQuestion question
                        where question.setId = s.id
                          and question.writingTaskType = :writingTask
                    )
                  )
            """)
    Page<PracticeSet> findLearnerVisiblePublished(
            @Param("status") String status,
            @Param("globalScope") String globalScope,
            @Param("classScope") String classScope,
            @Param("userId") Long userId,
            @Param("activeClassIds") List<Long> activeClassIds,
            @Param("selectedClassId") Long selectedClassId,
            @Param("search") String search,
            @Param("skill") String skill,
            @Param("writingTask") WritingTaskType writingTask,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticeSet s where s.id = :id")
    Optional<PracticeSet> findByIdForUpdate(@Param("id") Long id);
}
