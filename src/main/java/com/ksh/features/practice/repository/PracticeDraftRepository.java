package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeDraftRepository extends JpaRepository<PracticeDraft, Long> {
    List<PracticeDraft> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    java.util.Optional<PracticeDraft> findByPublishedSetId(Long publishedSetId);
}
