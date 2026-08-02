package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeExplanationEditorialRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeExplanationEditorialRevisionRepository
        extends JpaRepository<PracticeExplanationEditorialRevision, Long> {

    List<PracticeExplanationEditorialRevision>
            findByDraftIdAndQuestionClientIdOrderByRevisionNoDesc(
                    Long draftId,
                    String questionClientId);

    Optional<PracticeExplanationEditorialRevision>
            findFirstByDraftIdAndQuestionClientIdAndEditorialStateOrderByRevisionNoDesc(
                    Long draftId,
                    String questionClientId,
                    String editorialState);
}
