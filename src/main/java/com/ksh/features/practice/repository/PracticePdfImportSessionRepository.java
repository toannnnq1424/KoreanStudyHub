package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePdfImportSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PracticePdfImportSessionRepository extends JpaRepository<PracticePdfImportSession, Long> {
    List<PracticePdfImportSession> findByExpiresAtBefore(LocalDateTime time);
    List<PracticePdfImportSession> findByUploaderIdOrderByCreatedAtDesc(Long uploaderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticePdfImportSession s where s.id = :sessionId")
    Optional<PracticePdfImportSession> findByIdForUpdate(
            @Param("sessionId") Long sessionId);
}
