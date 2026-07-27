package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface SpeakingPromptAiArtifactRepository
        extends JpaRepository<SpeakingPromptAiArtifact, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SpeakingPromptAiArtifact a where a.id = :id")
    Optional<SpeakingPromptAiArtifact> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from SpeakingPromptAiArtifact a
            where a.ownerLecturerId = :ownerId
              and a.operation = :operation
              and a.operationFingerprint = :fingerprint
            """)
    Optional<SpeakingPromptAiArtifact> findByFingerprintForUpdate(
            @Param("ownerId") Long ownerId,
            @Param("operation") String operation,
            @Param("fingerprint") String fingerprint);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO practice_speaking_prompt_ai_artifacts (
                owner_lecturer_id, operation, operation_fingerprint,
                input_source_revision, input_sha256, input_audio_asset_id,
                provider_code, model_code, language_tag, contract_version,
                purpose_code, retention_code, artifact_status
            ) VALUES (
                :ownerId, 'stt', :fingerprint,
                :sourceRevision, :inputSha256, :inputAssetId,
                :provider, :model, :language, :contractVersion,
                :purposeCode, :retentionCode, 'queued'
            )
            """, nativeQuery = true)
    int insertSttIfAbsent(
            @Param("ownerId") Long ownerId,
            @Param("fingerprint") String fingerprint,
            @Param("sourceRevision") long sourceRevision,
            @Param("inputSha256") String inputSha256,
            @Param("inputAssetId") Long inputAssetId,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("language") String language,
            @Param("contractVersion") String contractVersion,
            @Param("purposeCode") String purposeCode,
            @Param("retentionCode") String retentionCode);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO practice_speaking_prompt_ai_artifacts (
                owner_lecturer_id, operation, operation_fingerprint,
                input_source_revision, input_sha256,
                provider_code, model_code, language_tag,
                voice_code, speed, output_format, contract_version,
                purpose_code, retention_code, artifact_status
            ) VALUES (
                :ownerId, 'tts', :fingerprint,
                :sourceRevision, :inputSha256,
                :provider, :model, :language,
                :voice, :speed, :outputFormat, :contractVersion,
                :purposeCode, :retentionCode, 'queued'
            )
            """, nativeQuery = true)
    int insertTtsIfAbsent(
            @Param("ownerId") Long ownerId,
            @Param("fingerprint") String fingerprint,
            @Param("sourceRevision") long sourceRevision,
            @Param("inputSha256") String inputSha256,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("language") String language,
            @Param("voice") String voice,
            @Param("speed") BigDecimal speed,
            @Param("outputFormat") String outputFormat,
            @Param("contractVersion") String contractVersion,
            @Param("purposeCode") String purposeCode,
            @Param("retentionCode") String retentionCode);

    boolean existsByInputAudioAssetIdOrGeneratedAudioAssetId(
            Long inputAudioAssetId, Long generatedAudioAssetId);

    @Query("""
            select a.id from SpeakingPromptAiArtifact a
            where a.updatedAt <= :cutoff
              and not exists (
                select s.id from SpeakingPromptSource s
                where s.currentSttArtifactId = a.id
                   or s.currentTtsArtifactId = a.id
              )
              and not exists (
                select v.questionVersionId from SpeakingPromptVersionContext v
                where v.sttArtifactId = a.id
                   or v.ttsArtifactId = a.id
              )
              and not exists (
                select t.id from SpeakingPromptAiTask t
                where t.artifactId = a.id
                  and t.taskStatus in ('queued', 'processing', 'retry_wait')
              )
            order by a.id asc
            """)
    List<Long> findExpiredUnretainedIds(
            @Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
