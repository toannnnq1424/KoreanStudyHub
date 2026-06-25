package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeQuestionRepository extends JpaRepository<PracticeQuestion, Long> {
    List<PracticeQuestion> findBySetIdOrderByDisplayOrderAsc(Long setId);

    List<PracticeQuestion> findBySetIdIn(List<Long> setIds);

    void deleteBySetId(Long setId);
}
