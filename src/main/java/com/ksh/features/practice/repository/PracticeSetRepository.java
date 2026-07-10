package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeSetRepository extends JpaRepository<PracticeSet, Long> {
    List<PracticeSet> findByStatusOrderByCreatedAtDesc(String status);
    List<PracticeSet> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    List<PracticeSet> findByCreatedByAndStatusOrderByCreatedAtDesc(Long createdBy, String status);
    Optional<PracticeSet> findByIdAndCreatedBy(Long id, Long createdBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticeSet s where s.id = :id")
    Optional<PracticeSet> findByIdForUpdate(@Param("id") Long id);
}
