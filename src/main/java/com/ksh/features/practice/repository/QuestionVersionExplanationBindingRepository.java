package com.ksh.features.practice.repository;

import com.ksh.entities.QuestionVersionExplanationBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionVersionExplanationBindingRepository
        extends JpaRepository<QuestionVersionExplanationBinding, Long> {

    Optional<QuestionVersionExplanationBinding> findByQuestionVersionIdAndExplanationLanguage(
            Long questionVersionId, String explanationLanguage);

    List<QuestionVersionExplanationBinding> findByQuestionVersionIdInAndExplanationLanguage(
            Collection<Long> questionVersionIds, String explanationLanguage);

    List<QuestionVersionExplanationBinding> findByArtifactIdOrderByIdAsc(Long artifactId);

    @Query(value = """
            SELECT DISTINCT q.published_version_id
            FROM practice_question_versions q
            JOIN practice_section_versions s
              ON s.id = q.section_version_id
             AND s.published_version_id = q.published_version_id
            JOIN practice_published_versions published
              ON published.id = q.published_version_id
            LEFT JOIN question_version_explanation_bindings binding
              ON binding.question_version_id = q.id
             AND binding.explanation_language = :explanationLanguage
            LEFT JOIN question_explanation_artifacts artifact
              ON artifact.id = binding.artifact_id
            LEFT JOIN question_explanation_generation_tasks task
              ON task.artifact_id = artifact.id
            WHERE UPPER(s.skill) IN ('READING', 'LISTENING')
              AND (
                    binding.id IS NULL
                    OR (
                        artifact.status = 'PENDING'
                        AND task.id IS NULL
                    )
              )
              AND (
                    published.version_number = (
                        SELECT MAX(latest.version_number)
                        FROM practice_published_versions latest
                        WHERE latest.set_id = published.set_id
                          AND latest.status = 'PUBLISHED'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM practice_attempts attempt
                        WHERE attempt.published_version_id = published.id
                    )
              )
            ORDER BY q.published_version_id ASC
            """, nativeQuery = true)
    List<Long> findPublishedVersionIdsWithPreparationGaps(
            @Param("explanationLanguage") String explanationLanguage,
            Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO question_version_explanation_bindings (
                question_version_id, artifact_id, explanation_language, fingerprint
            ) VALUES (:questionVersionId, :artifactId, :explanationLanguage, :fingerprint)
            """, nativeQuery = true)
    int bindIfAbsent(@Param("questionVersionId") Long questionVersionId,
                     @Param("artifactId") Long artifactId,
                     @Param("explanationLanguage") String explanationLanguage,
                     @Param("fingerprint") String fingerprint);
}
