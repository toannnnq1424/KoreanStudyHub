package com.ksh.features.practice.ai.controlplane;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeAiCapabilityTestRunRepository
        extends JpaRepository<PracticeAiCapabilityTestRun, Long> {

    List<PracticeAiCapabilityTestRun> findByPurposeCodeOrderByStartedAtDesc(
            String purposeCode,
            Pageable pageable);
}
