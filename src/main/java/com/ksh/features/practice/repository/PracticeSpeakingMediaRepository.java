package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeSpeakingMediaRepository extends JpaRepository<PracticeSpeakingMedia, Long> {

    List<PracticeSpeakingMedia> findByAttemptIdAndQuestionIdAndStatus(
            Long attemptId, Long questionId, PracticeSpeakingMediaStatus status);

    List<PracticeSpeakingMedia> findByAttemptIdAndStatus(Long attemptId, PracticeSpeakingMediaStatus status);

    boolean existsByStorageProviderAndStorageKey(PracticeSpeakingStorageProvider storageProvider, String storageKey);
}
