package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeQuestionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeQuestionGroupRepository extends JpaRepository<PracticeQuestionGroup, Long> {
    List<PracticeQuestionGroup> findBySetIdOrderByDisplayOrderAsc(Long setId);
    List<PracticeQuestionGroup> findBySectionIdOrderByDisplayOrderAsc(Long sectionId);
    void deleteBySetId(Long setId);
}

