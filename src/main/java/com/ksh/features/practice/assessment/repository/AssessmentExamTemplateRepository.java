package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentExamTemplateRepository extends JpaRepository<AssessmentExamTemplate, String> {
    List<AssessmentExamTemplate> findByEnabledTrueOrderByDisplayNameAsc();
    Optional<AssessmentExamTemplate> findByCodeAndEnabledTrue(String code);
}
