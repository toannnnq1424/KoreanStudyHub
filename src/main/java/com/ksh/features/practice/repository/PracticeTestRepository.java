package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;

public interface PracticeTestRepository extends JpaRepository<PracticeTest, Long> {

    List<PracticeTest> findBySetIdOrderByDisplayOrderAsc(Long setId);
    @Query(value = "SELECT * FROM practice_tests WHERE id = :id FOR SHARE", nativeQuery = true)
    Optional<PracticeTest> findByIdForShare(@Param("id") Long id);
}
