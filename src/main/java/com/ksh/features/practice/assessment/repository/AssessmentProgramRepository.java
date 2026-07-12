package com.ksh.features.practice.assessment.repository;

import com.ksh.features.practice.assessment.persistence.AssessmentProgram;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface AssessmentProgramRepository extends JpaRepository<AssessmentProgram, String> {
    List<AssessmentProgram> findAllByOrderByCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AssessmentProgram p where p.code = :code")
    Optional<AssessmentProgram> findByCodeForUpdate(@Param("code") String code);
}
