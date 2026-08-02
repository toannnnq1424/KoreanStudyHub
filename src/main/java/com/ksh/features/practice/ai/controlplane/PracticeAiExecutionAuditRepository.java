package com.ksh.features.practice.ai.controlplane;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeAiExecutionAuditRepository
        extends JpaRepository<PracticeAiExecutionAudit, Long> {
}
