package com.ksh.features.practice.manage.speaking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpeakingPromptSourceRepository
        extends JpaRepository<SpeakingPromptSource, Long> {

    Optional<SpeakingPromptSource> findByDraftIdAndQuestionClientId(
            Long draftId, String questionClientId);

    List<SpeakingPromptSource> findByDraftId(Long draftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SpeakingPromptSource s
            where s.draftId = :draftId
            order by s.questionClientId asc, s.id asc
            """)
    List<SpeakingPromptSource> findByDraftIdForUpdate(@Param("draftId") Long draftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SpeakingPromptSource s
            where s.draftId = :draftId
              and s.questionClientId = :questionClientId
            """)
    Optional<SpeakingPromptSource> findByDraftAndClientForUpdate(
            @Param("draftId") Long draftId,
            @Param("questionClientId") String questionClientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SpeakingPromptSource s
            where s.id in :ids
            order by s.draftId asc, s.id asc
            """)
    List<SpeakingPromptSource> findByIdsForUpdate(@Param("ids") List<Long> ids);

    List<SpeakingPromptSource>
    findByCurrentSttArtifactIdOrderByDraftIdAscIdAsc(Long artifactId);

    List<SpeakingPromptSource>
    findByCurrentTtsArtifactIdOrderByDraftIdAscIdAsc(Long artifactId);

    boolean existsByOriginalAudioAssetIdOrGeneratedAudioAssetIdOrActiveAudioAssetId(
            Long originalAudioAssetId,
            Long generatedAudioAssetId,
            Long activeAudioAssetId);
}
