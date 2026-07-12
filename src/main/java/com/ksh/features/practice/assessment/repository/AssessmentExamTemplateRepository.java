package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentExamTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentExamTemplateRepository extends JpaRepository<AssessmentExamTemplate, String> {
    List<AssessmentExamTemplate> findByEnabledTrueOrderByDisplayNameAsc();
    List<AssessmentExamTemplate> findByProgramCodeOrderByDisplayNameAsc(String programCode);
    List<AssessmentExamTemplate> findByProgramCodeAndEnabledTrueOrderByDisplayNameAsc(
            String programCode);
    Optional<AssessmentExamTemplate> findByCodeAndEnabledTrue(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AssessmentExamTemplate t where t.code = :code")
    Optional<AssessmentExamTemplate> findByCodeForUpdate(@Param("code") String code);
}
