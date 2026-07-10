package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentQuestionTypePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentQuestionTypePolicyRepository extends JpaRepository<AssessmentQuestionTypePolicy, Long> {
    Optional<AssessmentQuestionTypePolicy> findByProgramVersionIdAndSkillCodeAndCanonicalQuestionType(
            Long programVersionId,
            String skillCode,
            String canonicalQuestionType);
}
