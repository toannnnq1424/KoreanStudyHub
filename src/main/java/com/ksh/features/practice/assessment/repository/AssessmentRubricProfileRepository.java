package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentRubricProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentRubricProfileRepository extends JpaRepository<AssessmentRubricProfile, Long> {
    @Query("select coalesce(max(p.versionNumber), 0) from AssessmentRubricProfile p " +
            "where p.code = :code")
    Integer maxVersionNumber(@Param("code") String code);
}
