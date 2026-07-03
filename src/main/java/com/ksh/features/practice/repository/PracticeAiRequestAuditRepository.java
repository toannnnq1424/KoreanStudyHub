package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeAiRequestAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeAiRequestAuditRepository extends JpaRepository<PracticeAiRequestAudit, Long> {
    List<PracticeAiRequestAudit> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
