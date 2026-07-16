package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSectionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeSectionVersionRepository extends JpaRepository<PracticeSectionVersion, Long> {
    Optional<PracticeSectionVersion> findByPublishedVersionIdAndSectionId(Long publishedVersionId, Long sectionId);
    List<PracticeSectionVersion> findByTestVersionIdOrderByDisplayOrderAscIdAsc(Long testVersionId);
    List<PracticeSectionVersion> findByPublishedVersionIdOrderByTestVersionIdAscDisplayOrderAscIdAsc(
            Long publishedVersionId);
}
