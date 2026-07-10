package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentRubricProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRubricProfileRepository extends JpaRepository<AssessmentRubricProfile, Long> {
}
