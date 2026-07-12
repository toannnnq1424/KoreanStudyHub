package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeGovernanceAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeGovernanceAuditEventRepository
        extends JpaRepository<PracticeGovernanceAuditEvent, Long> {

    List<PracticeGovernanceAuditEvent> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId);
}
