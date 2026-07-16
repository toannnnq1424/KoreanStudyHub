package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSetVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeSetVersionRepository extends JpaRepository<PracticeSetVersion, Long> {
    Optional<PracticeSetVersion> findByPublishedVersionId(Long publishedVersionId);
}
