package com.ksh.features.practice.ai.controlplane;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PracticeAiExecutionAuditService {

    private final PracticeAiExecutionAuditRepository repository;
    private final PracticeAiControlPlaneCodec codec;

    public PracticeAiExecutionAuditService(
            PracticeAiExecutionAuditRepository repository,
            PracticeAiControlPlaneCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(
            PracticeAiExecutionSnapshot snapshot,
            String operation,
            String contractIdentity,
            String dataClass) {
        PracticeAiExecutionAudit audit = new PracticeAiExecutionAudit(
                snapshot,
                boundedCode(operation, "UNKNOWN_OPERATION"),
                codec.digest(contractIdentity == null ? "" : contractIdentity),
                boundedCode(dataClass, snapshot.purpose().dataClass()),
                LocalDateTime.now());
        return repository.saveAndFlush(audit).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(Long auditId) {
        complete(auditId, "SUCCESS", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(Long auditId, String errorCode) {
        complete(auditId, "FAILED", safeError(errorCode));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelled(Long auditId, String errorCode) {
        complete(auditId, "CANCELLED", safeError(errorCode));
    }

    private void complete(Long auditId, String status, String errorCode) {
        repository.findById(auditId).ifPresent(audit -> {
            audit.complete(status, errorCode, LocalDateTime.now());
            repository.save(audit);
        });
    }

    private static String safeError(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{1,63}")
                ? value
                : "PROVIDER_FAILURE";
    }

    private static String boundedCode(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase()
                .replaceAll("[^A-Z0-9_]+", "_");
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.length() <= 80
                ? normalized
                : normalized.substring(0, 80);
    }
}
