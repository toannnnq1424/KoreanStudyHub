package com.ksh.features.practice.governance;

import com.ksh.entities.PracticeGovernanceAuditEvent;
import com.ksh.features.practice.repository.PracticeGovernanceAuditEventRepository;
import org.springframework.stereotype.Service;

@Service
public class PracticeGovernanceAuditService {

    private final PracticeGovernanceAuditEventRepository repository;

    public PracticeGovernanceAuditService(PracticeGovernanceAuditEventRepository repository) {
        this.repository = repository;
    }

    public PracticeGovernanceAuditEvent record(String actionCode, String targetType,
                                               Long targetId, Long ownerId, Long actorId,
                                               Long sourceVersionId, boolean overrideUsed,
                                               String reason, String beforeJson,
                                               String afterJson) {
        return repository.save(new PracticeGovernanceAuditEvent(
                actionCode, targetType, targetId, ownerId, actorId, sourceVersionId,
                overrideUsed, normalizeReason(reason), beforeJson, afterJson));
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String trimmed = reason.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
