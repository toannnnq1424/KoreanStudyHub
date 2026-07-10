package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeQuestionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeQuestionVersionRepository extends JpaRepository<PracticeQuestionVersion, Long> {
    List<PracticeQuestionVersion> findBySectionVersionIdOrderByDisplayOrderAscQuestionNoAscIdAsc(Long sectionVersionId);
}
