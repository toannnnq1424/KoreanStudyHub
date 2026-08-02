package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeDraftRepository extends JpaRepository<PracticeDraft, Long> {
    List<PracticeDraft> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    List<PracticeDraft> findByOwnerIdNotOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);
    Optional<PracticeDraft> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<PracticeDraft> findByPublishedSetId(Long publishedSetId);
    Optional<PracticeDraft> findByPublishedSetIdAndOwnerId(Long publishedSetId, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PracticeDraft d where d.id = :id")
    Optional<PracticeDraft> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from PracticeDraft d where d.id = :id")
    Optional<PracticeDraft> findByIdForRead(@Param("id") Long id);
}
