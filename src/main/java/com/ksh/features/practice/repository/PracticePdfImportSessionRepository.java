package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePdfImportSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PracticePdfImportSessionRepository extends JpaRepository<PracticePdfImportSession, Long> {
    List<PracticePdfImportSession> findByExpiresAtBefore(LocalDateTime time);
    List<PracticePdfImportSession> findByUploaderIdOrderByCreatedAtDesc(Long uploaderId);
}
