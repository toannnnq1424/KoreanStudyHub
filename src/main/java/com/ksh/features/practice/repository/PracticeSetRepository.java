package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeSetRepository extends JpaRepository<PracticeSet, Long> {
    List<PracticeSet> findByStatusOrderByCreatedAtDesc(String status);
}
