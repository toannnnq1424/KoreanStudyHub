package com.ksh.features.practice.repository;

import com.ksh.entities.QuestionExplanationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface QuestionExplanationCacheRepository extends JpaRepository<QuestionExplanationCache, Long> {
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<QuestionExplanationCache> findByCacheKey(String cacheKey);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void deleteByCacheKey(String cacheKey);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO question_explanation_cache
                (cache_key, question_id, test_id, skill_type, question_type, question_hash,
                 correct_answer, explanation_json, ai_model, prompt_version, schema_version,
                 explanation_language, created_at, updated_at)
            VALUES
                (:cacheKey, :questionId, :testId, :skillType, :questionType, :questionHash,
                 :correctAnswer, :explanationJson, :aiModel, :promptVersion, :schemaVersion,
                 :explanationLanguage, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                question_id = VALUES(question_id),
                test_id = VALUES(test_id),
                skill_type = VALUES(skill_type),
                question_type = VALUES(question_type),
                question_hash = VALUES(question_hash),
                correct_answer = VALUES(correct_answer),
                explanation_json = VALUES(explanation_json),
                ai_model = VALUES(ai_model),
                prompt_version = VALUES(prompt_version),
                schema_version = VALUES(schema_version),
                explanation_language = VALUES(explanation_language),
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsert(@Param("cacheKey") String cacheKey,
                @Param("questionId") Long questionId,
                @Param("testId") Long testId,
                @Param("skillType") String skillType,
                @Param("questionType") String questionType,
                @Param("questionHash") String questionHash,
                @Param("correctAnswer") String correctAnswer,
                @Param("explanationJson") String explanationJson,
                @Param("aiModel") String aiModel,
                @Param("promptVersion") String promptVersion,
                @Param("schemaVersion") String schemaVersion,
                @Param("explanationLanguage") String explanationLanguage);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO question_explanation_cache
                (cache_key, question_id, question_version_id, test_id,
                 skill_type, question_type, question_hash, stimulus_hash, answer_spec_hash,
                 correct_answer, explanation_json, ai_model, prompt_version,
                 schema_version, explanation_language, created_at, updated_at)
            VALUES
                (:cacheKey, :questionId, :questionVersionId, :testId,
                 :skillType, :questionType, :questionHash, :stimulusHash, :answerSpecHash,
                 :correctAnswer, :explanationJson, :aiModel, :promptVersion,
                 :schemaVersion, :explanationLanguage, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                question_id = VALUES(question_id),
                question_version_id = VALUES(question_version_id),
                test_id = VALUES(test_id),
                skill_type = VALUES(skill_type),
                question_type = VALUES(question_type),
                question_hash = VALUES(question_hash),
                stimulus_hash = VALUES(stimulus_hash),
                answer_spec_hash = VALUES(answer_spec_hash),
                correct_answer = VALUES(correct_answer),
                explanation_json = VALUES(explanation_json),
                ai_model = VALUES(ai_model),
                prompt_version = VALUES(prompt_version),
                schema_version = VALUES(schema_version),
                explanation_language = VALUES(explanation_language),
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertVersioned(@Param("cacheKey") String cacheKey,
                         @Param("questionId") Long questionId,
                         @Param("questionVersionId") Long questionVersionId,
                         @Param("testId") Long testId,
                         @Param("skillType") String skillType,
                         @Param("questionType") String questionType,
                         @Param("questionHash") String questionHash,
                         @Param("stimulusHash") String stimulusHash,
                         @Param("answerSpecHash") String answerSpecHash,
                         @Param("correctAnswer") String correctAnswer,
                         @Param("explanationJson") String explanationJson,
                         @Param("aiModel") String aiModel,
                         @Param("promptVersion") String promptVersion,
                         @Param("schemaVersion") String schemaVersion,
                         @Param("explanationLanguage") String explanationLanguage);
}
