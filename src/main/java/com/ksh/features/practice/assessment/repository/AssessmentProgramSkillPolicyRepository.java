package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentProgramSkillPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentProgramSkillPolicyRepository extends JpaRepository<AssessmentProgramSkillPolicy, Long> {
    Optional<AssessmentProgramSkillPolicy> findByProgramVersionIdAndSkillCode(
            Long programVersionId,
            String skillCode);
    List<AssessmentProgramSkillPolicy> findByProgramVersionIdOrderBySkillCodeAsc(
            Long programVersionId);
}
