package com.ksh.features.questionbank.repository;

import com.ksh.features.questionbank.entity.QuestionBankItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for subject-scoped shared question contributions.
 */
public interface QuestionBankItemRepository extends JpaRepository<QuestionBankItem, Long> {

    List<QuestionBankItem> findBySubjectIdOrderByUpdatedAtDescIdDesc(Long subjectId);

    List<QuestionBankItem> findBySubjectIdAndWorkflowStatusInOrderByUpdatedAtDescIdDesc(
            Long subjectId, Collection<String> workflowStatuses);

    Optional<QuestionBankItem> findByIdAndSubjectId(Long id, Long subjectId);
}
