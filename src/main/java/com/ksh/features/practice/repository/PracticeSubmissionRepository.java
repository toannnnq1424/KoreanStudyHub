package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeSubmissionRepository extends JpaRepository<PracticeSubmission, Long> {
    Optional<PracticeSubmission> findByIdAndUserId(Long id, Long userId);

    List<PracticeSubmission> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeSubmission> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeSubmission> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, String status);

    List<PracticeSubmission> findBySetIdAndUserIdOrderByCreatedAtDesc(Long setId, Long userId);
}
