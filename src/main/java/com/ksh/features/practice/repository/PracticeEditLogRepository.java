package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeEditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeEditLogRepository extends JpaRepository<PracticeEditLog, Long> {
    List<PracticeEditLog> findBySetIdOrderByEditedAtDesc(Long setId);

    @org.springframework.data.jpa.repository.Query("SELECT log FROM PracticeEditLog log, PracticeSet s WHERE log.setId = s.id AND s.createdBy = :ownerId ORDER BY log.editedAt DESC")
    List<PracticeEditLog> findBySetOwnerOrderByEditedAtDesc(@org.springframework.data.repository.query.Param("ownerId") Long ownerId);
}
