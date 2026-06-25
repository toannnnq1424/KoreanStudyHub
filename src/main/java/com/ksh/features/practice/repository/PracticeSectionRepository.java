package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeSectionRepository extends JpaRepository<PracticeSection, Long> {
    List<PracticeSection> findBySetIdOrderByDisplayOrderAsc(Long setId);
    void deleteBySetId(Long setId);
}
