package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentScoringProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentScoringProfileRepository extends JpaRepository<AssessmentScoringProfile, Long> {
}
