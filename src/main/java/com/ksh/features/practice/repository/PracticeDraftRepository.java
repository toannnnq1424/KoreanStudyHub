package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeDraftRepository extends JpaRepository<PracticeDraft, Long> {
    List<PracticeDraft> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Optional<PracticeDraft> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<PracticeDraft> findByPublishedSetId(Long publishedSetId);
    Optional<PracticeDraft> findByPublishedSetIdAndOwnerId(Long publishedSetId, Long ownerId);
}
