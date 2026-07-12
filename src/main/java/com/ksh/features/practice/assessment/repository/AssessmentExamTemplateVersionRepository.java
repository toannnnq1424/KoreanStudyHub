package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssessmentExamTemplateVersionRepository
        extends JpaRepository<AssessmentExamTemplateVersion, Long> {

    @Query("select coalesce(max(v.versionNumber), 0) from AssessmentExamTemplateVersion v " +
            "where v.templateCode = :templateCode")
    Integer maxVersionNumber(@Param("templateCode") String templateCode);

    List<AssessmentExamTemplateVersion> findByTemplateCodeOrderByVersionNumberDesc(
            String templateCode);

    List<AssessmentExamTemplateVersion>
    findByTemplateCodeAndProgramVersionIdOrderByVersionNumberDesc(
            String templateCode, Long programVersionId);
}
