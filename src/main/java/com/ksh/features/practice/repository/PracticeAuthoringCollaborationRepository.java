package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAuthoringCollaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PracticeAuthoringCollaborationRepository
        extends JpaRepository<PracticeAuthoringCollaboration, Long> {

    Optional<PracticeAuthoringCollaboration>
    findBySetIdAndCollaboratorIdAndRevokedAtIsNull(Long setId, Long collaboratorId);

    Optional<PracticeAuthoringCollaboration>
    findBySetIdAndCollaboratorId(Long setId, Long collaboratorId);

    List<PracticeAuthoringCollaboration>
    findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(Long collaboratorId);

    List<PracticeAuthoringCollaboration>
    findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(
            Long collaboratorId, Pageable pageable);

    List<PracticeAuthoringCollaboration>
    findBySetIdAndRevokedAtIsNull(Long setId);
}
