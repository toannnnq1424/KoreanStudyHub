package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PracticeDraftRepository extends JpaRepository<PracticeDraft, Long> {
    List<PracticeDraft> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    List<PracticeDraft> findByOwnerIdNotOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);
    Optional<PracticeDraft> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<PracticeDraft> findByPublishedSetId(Long publishedSetId);
    Optional<PracticeDraft> findByPublishedSetIdAndOwnerId(Long publishedSetId, Long ownerId);
}
