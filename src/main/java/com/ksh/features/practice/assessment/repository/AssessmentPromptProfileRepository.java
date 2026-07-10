package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentPromptProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentPromptProfileRepository extends JpaRepository<AssessmentPromptProfile, Long> {
}
