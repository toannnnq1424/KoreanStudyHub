package com.ksh.features.practice.repository;

import com.ksh.entities.QuestionExplanationArtifact;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionExplanationArtifactRepository
        extends JpaRepository<QuestionExplanationArtifact, Long> {

    Optional<QuestionExplanationArtifact> findByFingerprint(String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuestionExplanationArtifact a where a.id = :id")
    Optional<QuestionExplanationArtifact> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO question_explanation_artifacts (
                fingerprint, skill, question_type, assessment_schema_version,
                provider_model, prompt_version, response_schema_version,
                explanation_language, question_hash, stimulus_hash,
                answer_spec_hash, media_bundle_hash, input_contract_json, status
            ) VALUES (
                :fingerprint, :skill, :questionType, :assessmentSchemaVersion,
                :providerModel, :promptVersion, :responseSchemaVersion,
                :explanationLanguage, :questionHash, :stimulusHash,
                :answerSpecHash, :mediaBundleHash, :inputContractJson, 'PENDING'
            )
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("fingerprint") String fingerprint,
                              @Param("skill") String skill,
                              @Param("questionType") String questionType,
                              @Param("assessmentSchemaVersion") String assessmentSchemaVersion,
                              @Param("providerModel") String providerModel,
                              @Param("promptVersion") String promptVersion,
                              @Param("responseSchemaVersion") String responseSchemaVersion,
                              @Param("explanationLanguage") String explanationLanguage,
                              @Param("questionHash") String questionHash,
                              @Param("stimulusHash") String stimulusHash,
                              @Param("answerSpecHash") String answerSpecHash,
                              @Param("mediaBundleHash") String mediaBundleHash,
                              @Param("inputContractJson") String inputContractJson);
}
