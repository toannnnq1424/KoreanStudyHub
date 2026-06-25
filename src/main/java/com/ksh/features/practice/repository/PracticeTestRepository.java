package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeTestRepository extends JpaRepository<PracticeTest, Long> {

    List<PracticeTest> findBySetIdOrderByDisplayOrderAsc(Long setId);
}
