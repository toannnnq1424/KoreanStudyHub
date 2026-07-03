package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {

    Optional<PracticeAttempt> findByIdAndUserId(Long id, Long userId);

    List<PracticeAttempt> findByTestIdAndUserIdAndSkillOrderByCreatedAtDesc(
            Long testId, Long userId, String skill);

    List<PracticeAttempt> findByTestIdAndUserIdOrderByCreatedAtDesc(
            Long testId, Long userId);

    List<PracticeAttempt> findBySetIdAndUserIdOrderByCreatedAtDesc(
            Long setId, Long userId);

    List<PracticeAttempt> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PracticeAttempt> findByUserIdAndStatusNotOrderByCreatedAtDesc(
            Long userId, String status);

    Optional<PracticeAttempt> findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(
            Long userId, Long testId, Long sectionId, String status);
}
