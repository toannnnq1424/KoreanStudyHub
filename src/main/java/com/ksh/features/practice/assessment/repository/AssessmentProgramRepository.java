package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentProgramRepository extends JpaRepository<AssessmentProgram, String> {
}
