package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAuthoringCollaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PracticeAuthoringCollaborationRepository
        extends JpaRepository<PracticeAuthoringCollaboration, Long> {

    Optional<PracticeAuthoringCollaboration>
    findByTargetTypeAndTargetIdAndCollaboratorIdAndRevokedAtIsNull(
            String targetType, Long targetId, Long collaboratorId);

    Optional<PracticeAuthoringCollaboration>
    findByTargetTypeAndTargetIdAndCollaboratorId(
            String targetType, Long targetId, Long collaboratorId);

    List<PracticeAuthoringCollaboration>
    findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(Long collaboratorId);

    List<PracticeAuthoringCollaboration>
    findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(
            Long collaboratorId, Pageable pageable);

    List<PracticeAuthoringCollaboration>
    findByTargetTypeAndTargetIdAndRevokedAtIsNull(String targetType, Long targetId);
}
