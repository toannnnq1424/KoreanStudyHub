package com.ksh.features.questionbank.repository;

import com.ksh.features.questionbank.entity.QuestionBankOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for subject-scoped question bank answer options.
 */
public interface QuestionBankOptionRepository extends JpaRepository<QuestionBankOption, Long> {

    List<QuestionBankOption> findByItemIdOrderBySortOrderAscIdAsc(Long itemId);

    List<QuestionBankOption> findByItemIdInOrderBySortOrderAscIdAsc(Collection<Long> itemIds);

    void deleteByItemIdIn(Collection<Long> itemIds);
}
