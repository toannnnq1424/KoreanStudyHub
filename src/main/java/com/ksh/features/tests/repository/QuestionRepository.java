package com.ksh.features.tests.repository;

import com.ksh.features.tests.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Repository for {@link Question} rows belonging to a {@link com.ksh.features.tests.entity.Test}. */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Questions of a test in authoring order. */
    List<Question> findByTestIdOrderBySortOrderAscIdAsc(Long testId);

    /** Questions across several tests (practice sampling pool). */
    List<Question> findByTestIdIn(Collection<Long> testIds);

    /** Deletes all questions of a test (edit = full replacement). */
    void deleteByTestId(Long testId);
}
