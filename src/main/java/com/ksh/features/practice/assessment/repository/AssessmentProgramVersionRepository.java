package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentProgramVersionRepository extends JpaRepository<AssessmentProgramVersion, Long> {
}
