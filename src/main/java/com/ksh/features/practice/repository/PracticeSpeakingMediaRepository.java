package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PracticeSpeakingMediaRepository extends JpaRepository<PracticeSpeakingMedia, Long> {

    List<PracticeSpeakingMedia> findByAttemptIdAndQuestionIdAndStatus(
            Long attemptId, Long questionId, PracticeSpeakingMediaStatus status);

    List<PracticeSpeakingMedia> findByAttemptIdAndStatus(Long attemptId, PracticeSpeakingMediaStatus status);

    Optional<PracticeSpeakingMedia> findByIdAndAttemptIdAndQuestionId(
            Long id, Long attemptId, Long questionId);

    boolean existsByStorageProviderAndStorageKey(PracticeSpeakingStorageProvider storageProvider, String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PracticeSpeakingMedia m where m.attemptId = :attemptId order by m.id asc")
    List<PracticeSpeakingMedia> findByAttemptIdForUpdateOrderByIdAsc(@Param("attemptId") Long attemptId);
}
