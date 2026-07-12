package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentPromptProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentPromptProfileRepository extends JpaRepository<AssessmentPromptProfile, Long> {
    @Query("select coalesce(max(p.versionNumber), 0) from AssessmentPromptProfile p " +
            "where p.code = :code")
    Integer maxVersionNumber(@Param("code") String code);
}
