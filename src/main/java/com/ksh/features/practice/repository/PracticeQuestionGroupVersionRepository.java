package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeQuestionGroupVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeQuestionGroupVersionRepository extends JpaRepository<PracticeQuestionGroupVersion, Long> {
    List<PracticeQuestionGroupVersion> findBySectionVersionIdOrderByDisplayOrderAscIdAsc(Long sectionVersionId);
    List<PracticeQuestionGroupVersion> findByPublishedVersionIdOrderBySectionVersionIdAscDisplayOrderAscIdAsc(
            Long publishedVersionId);
}
