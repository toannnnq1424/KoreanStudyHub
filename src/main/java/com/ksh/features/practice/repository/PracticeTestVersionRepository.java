package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeTestVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeTestVersionRepository extends JpaRepository<PracticeTestVersion, Long> {
    Optional<PracticeTestVersion> findByPublishedVersionIdAndTestId(Long publishedVersionId, Long testId);
    List<PracticeTestVersion> findByPublishedVersionIdOrderByDisplayOrderAscIdAsc(Long publishedVersionId);
}
