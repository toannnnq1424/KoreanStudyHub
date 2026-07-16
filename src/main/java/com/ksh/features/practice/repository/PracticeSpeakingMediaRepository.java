package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PracticeSpeakingMediaRepository extends JpaRepository<PracticeSpeakingMedia, Long> {

    interface PlaybackAuthorizationProjection {
        PracticeSpeakingStorageProvider getStorageProvider();
        String getStorageKey();
        String getMimeType();
        Long getByteSize();
    }

    interface TranscriptionAuthorizationProjection {
        Long getMediaId();
        Long getAttemptId();
        Long getQuestionId();
        Long getLockVersion();
        PracticeSpeakingStorageProvider getStorageProvider();
        String getStorageKey();
        String getMimeType();
        Long getByteSize();
        Long getDurationMs();
    }

    List<PracticeSpeakingMedia> findByAttemptIdAndQuestionIdAndStatus(
            Long attemptId, Long questionId, PracticeSpeakingMediaStatus status);

    List<PracticeSpeakingMedia> findByAttemptIdAndStatus(Long attemptId, PracticeSpeakingMediaStatus status);

    Optional<PracticeSpeakingMedia> findByIdAndAttemptIdAndQuestionId(
            Long id, Long attemptId, Long questionId);

    boolean existsByStorageProviderAndStorageKey(PracticeSpeakingStorageProvider storageProvider, String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PracticeSpeakingMedia m where m.attemptId = :attemptId order by m.id asc")
    List<PracticeSpeakingMedia> findByAttemptIdForUpdateOrderByIdAsc(@Param("attemptId") Long attemptId);

    @Query(value = """
            SELECT m.id
            FROM practice_speaking_media m
            JOIN practice_questions q ON q.id = m.question_id
            WHERE q.set_id = :setId
            ORDER BY m.id
            LIMIT 1
            FOR SHARE
            """, nativeQuery = true)
    Optional<Long> findFirstIdByQuestionSetIdForShare(@Param("setId") Long setId);

    @Query("""
            select m.storageProvider as storageProvider,
                   m.storageKey as storageKey,
                   m.mimeType as mimeType,
                   m.byteSize as byteSize
            from PracticeSpeakingMedia m, PracticeAttempt a
            where m.attemptId = a.id
              and m.id = :mediaId
              and m.attemptId = :attemptId
              and m.questionId = :questionId
              and a.userId = :userId
              and m.status = :mediaStatus
              and a.status in :attemptStatuses
            """)
    Optional<PlaybackAuthorizationProjection> findAuthorizedPlayback(
            @Param("userId") Long userId,
            @Param("attemptId") Long attemptId,
            @Param("questionId") Long questionId,
            @Param("mediaId") Long mediaId,
            @Param("mediaStatus") PracticeSpeakingMediaStatus mediaStatus,
            @Param("attemptStatuses") Collection<String> attemptStatuses);

    @Query("""
            select m.id as mediaId,
                   m.attemptId as attemptId,
                   m.questionId as questionId,
                   m.lockVersion as lockVersion,
                   m.storageProvider as storageProvider,
                   m.storageKey as storageKey,
                   m.mimeType as mimeType,
                   m.byteSize as byteSize,
                   m.durationMs as durationMs
            from PracticeSpeakingMedia m, PracticeAttempt a, PracticeSection s, PracticeQuestion q, PracticeQuestionGroup g
            where m.attemptId = a.id
              and s.id = a.sectionId
              and q.id = m.questionId
              and g.id = q.groupId
              and m.attemptId = :attemptId
              and m.questionId = :questionId
              and a.userId = :userId
              and a.skill = 'SPEAKING'
              and s.setId = a.setId
              and s.testId = a.testId
              and q.setId = a.setId
              and q.questionType = 'SPEAKING'
              and g.sectionId = a.sectionId
              and m.status = :mediaStatus
            order by m.id asc
            """)
    List<TranscriptionAuthorizationProjection> findAuthorizedTranscriptionCandidates(
            @Param("userId") Long userId,
            @Param("attemptId") Long attemptId,
            @Param("questionId") Long questionId,
            @Param("mediaStatus") PracticeSpeakingMediaStatus mediaStatus);
}
