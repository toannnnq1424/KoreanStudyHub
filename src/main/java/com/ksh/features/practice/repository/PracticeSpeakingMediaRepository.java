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

    List<PracticeSpeakingMedia> findByAttemptIdAndQuestionIdAndStatus(
            Long attemptId, Long questionId, PracticeSpeakingMediaStatus status);

    List<PracticeSpeakingMedia> findByAttemptIdAndStatus(Long attemptId, PracticeSpeakingMediaStatus status);

    Optional<PracticeSpeakingMedia> findByIdAndAttemptIdAndQuestionId(
            Long id, Long attemptId, Long questionId);

    boolean existsByStorageProviderAndStorageKey(PracticeSpeakingStorageProvider storageProvider, String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PracticeSpeakingMedia m where m.attemptId = :attemptId order by m.id asc")
    List<PracticeSpeakingMedia> findByAttemptIdForUpdateOrderByIdAsc(@Param("attemptId") Long attemptId);

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
}
