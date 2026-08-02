package com.ksh.features.practice.manage.service;

import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
public class PracticePdfAiGenerationService {

    private static final Set<String> RELEASE_STATUSES =
            Set.of("READY_FOR_AI", "AI_FAILED_RETRYABLE", "REVIEWING");

    private final PracticePdfImportSessionRepository repository;
    private final PracticePdfAiLimits limits;
    private final Clock clock;

    @Autowired
    public PracticePdfAiGenerationService(
            PracticePdfImportSessionRepository repository,
            PracticePdfAiLimits limits,
            ObjectProvider<Clock> clockProvider) {
        this(
                repository,
                limits,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    PracticePdfAiGenerationService(
            PracticePdfImportSessionRepository repository,
            PracticePdfAiLimits limits,
            Clock clock) {
        this.repository = repository;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claim(Long sessionId, Long userId) {
        PracticePdfImportSession session = loadOwnedForUpdate(sessionId, userId);
        LocalDateTime now = now();
        if (session.hasCompletedGeneration()) {
            return ClaimResult.completed(session.getLinkedDraftId());
        }
        if (session.hasLiveGenerationClaim(now)) {
            return ClaimResult.inProgress(
                    session.getGenerationLeaseExpiresAt());
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime leaseExpiresAt =
                now.plus(limits.generationLeaseDuration());
        session.claimGeneration(token, leaseExpiresAt, now);
        repository.saveAndFlush(session);
        return ClaimResult.claimed(token, leaseExpiresAt, session);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(
            Long sessionId,
            Long userId,
            String claimToken,
            Long draftId) {
        PracticePdfImportSession session =
                loadOwnedForUpdate(sessionId, userId);
        session.completeGeneration(claimToken, draftId, now());
        repository.save(session);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(
            Long sessionId,
            Long userId,
            String claimToken,
            String nextStatus) {
        if (!RELEASE_STATUSES.contains(nextStatus)) {
            throw new IllegalArgumentException(
                    "PDF AI generation release status is invalid.");
        }
        PracticePdfImportSession session =
                loadOwnedForUpdate(sessionId, userId);
        session.releaseGeneration(claimToken, nextStatus, now());
        repository.save(session);
    }

    private PracticePdfImportSession loadOwnedForUpdate(
            Long sessionId,
            Long userId) {
        PracticePdfImportSession session = repository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Session không tồn tại."));
        if (userId == null || !userId.equals(session.getUploaderId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền quản lý session này.");
        }
        return session;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public record ClaimResult(
            Outcome outcome,
            String claimToken,
            Long completedDraftId,
            LocalDateTime leaseExpiresAt,
            PracticePdfImportSession claimedSession) {

        static ClaimResult claimed(
                String claimToken,
                LocalDateTime leaseExpiresAt,
                PracticePdfImportSession claimedSession) {
            return new ClaimResult(
                    Outcome.CLAIMED,
                    claimToken,
                    null,
                    leaseExpiresAt,
                    claimedSession);
        }

        static ClaimResult inProgress(LocalDateTime leaseExpiresAt) {
            return new ClaimResult(
                    Outcome.IN_PROGRESS, null, null, leaseExpiresAt, null);
        }

        static ClaimResult completed(Long draftId) {
            return new ClaimResult(
                    Outcome.COMPLETED, null, draftId, null, null);
        }
    }

    public enum Outcome {
        CLAIMED,
        IN_PROGRESS,
        COMPLETED
    }
}
