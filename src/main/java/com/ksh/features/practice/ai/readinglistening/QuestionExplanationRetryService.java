package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionExplanationRetryService {

    private static final Duration MANUAL_RETRY_COOLDOWN = Duration.ofMinutes(1);

    private final QuestionExplanationArtifactRepository artifactRepository;
    private final QuestionExplanationGenerationTaskRepository taskRepository;
    private final PracticeQuestionVersionRepository questionRepository;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final PracticeAuthorizationService authorizationService;

    public QuestionExplanationRetryService(
            QuestionExplanationArtifactRepository artifactRepository,
            QuestionExplanationGenerationTaskRepository taskRepository,
            PracticeQuestionVersionRepository questionRepository,
            PracticePublishedVersionRepository publishedVersionRepository,
            QuestionVersionExplanationBindingRepository bindingRepository,
            PracticeAuthorizationService authorizationService) {
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.questionRepository = questionRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.bindingRepository = bindingRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public RetryResult retry(Long artifactId, Long requestedBy) {
        requireAuthorizedBinding(artifactId, requestedBy);
        QuestionExplanationGenerationTask task = taskRepository
                .findByArtifactIdForUpdate(artifactId)
                .orElse(null);
        QuestionExplanationArtifact artifact = artifactRepository.findByIdForUpdate(artifactId)
                .orElseThrow(() -> new EntityNotFoundException("Explanation artifact not found"));
        if (QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())) {
            return new RetryResult("READY", false, 0,
                    "Explanation artifact is already ready");
        }
        if (task == null) {
            return new RetryResult("NOT_RETRYABLE", false, 0,
                    "Published evidence must be corrected and republished before retrying");
        }
        if (QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())
                && isActive(task.getStatus())) {
            return new RetryResult("PENDING", false, 0,
                    "Explanation generation is already pending");
        }
        if (!QuestionExplanationArtifact.STATUS_FAILED.equals(artifact.getStatus())
                || !QuestionExplanationGenerationTask.STATUS_FAILED.equals(task.getStatus())) {
            return new RetryResult("NOT_RETRYABLE", false, 0,
                    "Only a terminal failed generation task can be retried");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime allowedAt = task.getLastRetryRequestedAt() == null
                ? null
                : task.getLastRetryRequestedAt().plus(MANUAL_RETRY_COOLDOWN);
        if (allowedAt != null && allowedAt.isAfter(now)) {
            long retryAfter = Math.max(1, Duration.between(now, allowedAt).getSeconds());
            return new RetryResult("RATE_LIMITED", false, retryAfter,
                    "Manual retry is temporarily rate limited");
        }
        artifact.markPending();
        task.requestManualRetry(requestedBy, now);
        artifactRepository.save(artifact);
        taskRepository.save(task);
        return new RetryResult("PENDING", true, 0,
                "Explanation generation was queued for retry");
    }

    private void requireAuthorizedBinding(Long artifactId, Long requestedBy) {
        List<QuestionVersionExplanationBinding> bindings =
                bindingRepository.findByArtifactIdOrderByIdAsc(artifactId);
        Set<Long> questionVersionIds = bindings.stream()
                .map(QuestionVersionExplanationBinding::getQuestionVersionId)
                .collect(Collectors.toSet());
        Set<Long> publishedVersionIds = questionRepository.findAllById(questionVersionIds).stream()
                .map(PracticeQuestionVersion::getPublishedVersionId)
                .collect(Collectors.toSet());
        Set<Long> setIds = publishedVersionRepository.findAllById(publishedVersionIds).stream()
                .map(PracticePublishedVersion::getSetId)
                .collect(Collectors.toSet());
        AccessDeniedException denied = null;
        for (Long setId : setIds) {
            try {
                authorizationService.requireSet(setId, requestedBy, PracticeAction.PUBLISH);
                return;
            } catch (AccessDeniedException exception) {
                denied = exception;
            }
        }
        if (denied != null) {
            throw denied;
        }
        throw new AccessDeniedException("No authorized published binding was found for this artifact");
    }

    private static boolean isActive(String status) {
        return QuestionExplanationGenerationTask.STATUS_PENDING.equals(status)
                || QuestionExplanationGenerationTask.STATUS_PROCESSING.equals(status)
                || QuestionExplanationGenerationTask.STATUS_RETRY_WAIT.equals(status);
    }

    public record RetryResult(
            String status,
            boolean queued,
            long retryAfterSeconds,
            String message
    ) {
    }
}
