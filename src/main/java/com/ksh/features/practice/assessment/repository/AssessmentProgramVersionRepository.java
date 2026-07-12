package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentProgramVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssessmentProgramVersionRepository extends JpaRepository<AssessmentProgramVersion, Long> {
    @Query("select coalesce(max(v.versionNumber), 0) from AssessmentProgramVersion v " +
            "where v.programCode = :programCode")
    Integer maxVersionNumber(@Param("programCode") String programCode);

    List<AssessmentProgramVersion> findByProgramCodeOrderByVersionNumberDesc(String programCode);
}
