package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionExplanationRetryService {

    private static final Duration MANUAL_RETRY_COOLDOWN = Duration.ofMinutes(1);

    private final QuestionExplanationArtifactRepository artifactRepository;
    private final QuestionExplanationGenerationTaskRepository taskRepository;
    private final PracticeQuestionVersionRepository questionRepository;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final PracticeSectionVersionRepository sectionRepository;
    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final PracticeAuthorizationService authorizationService;
    private final EntityManager entityManager;

    public QuestionExplanationRetryService(
            QuestionExplanationArtifactRepository artifactRepository,
            QuestionExplanationGenerationTaskRepository taskRepository,
            PracticeQuestionVersionRepository questionRepository,
            PracticePublishedVersionRepository publishedVersionRepository,
            PracticeSectionVersionRepository sectionRepository,
            QuestionVersionExplanationBindingRepository bindingRepository,
            PracticeAuthorizationService authorizationService,
            EntityManager entityManager) {
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.questionRepository = questionRepository;
        this.publishedVersionRepository = publishedVersionRepository;
        this.sectionRepository = sectionRepository;
        this.bindingRepository = bindingRepository;
        this.authorizationService = authorizationService;
        this.entityManager = entityManager;
    }

    @Transactional
    public RetryResult retry(Long artifactId, Long requestedBy) {
        authorizationService.requireGlobal(requestedBy, PracticeAction.PUBLISH);
        RetryTarget target = requireAuthorizedArtifactTarget(artifactId, requestedBy);
        if (!target.valid()) {
            return notRetryable();
        }
        return retryAuthorized(target.artifact(), requestedBy);
    }

    @Transactional
    public RetryResult retryQuestionVersion(
            Long setId,
            Long questionVersionId,
            Long requestedBy) {
        authorizationService.requireSet(setId, requestedBy, PracticeAction.PUBLISH);
        RetryTarget target = resolveQuestionVersionTarget(setId, questionVersionId);
        if (!target.valid()) {
            return notRetryable();
        }
        return retryAuthorized(target.artifact(), requestedBy);
    }

    private RetryResult retryAuthorized(
            QuestionExplanationArtifact currentArtifact,
            Long requestedBy) {
        LocalDateTime now = LocalDateTime.now();
        RecoveryState artifactOnlyState = recoveryState(
                currentArtifact, null, false, now);
        if (artifactOnlyState == RecoveryState.READY) {
            return resultFor(artifactOnlyState, null, now);
        }

        QuestionExplanationGenerationTask currentTask = taskRepository
                .findByArtifactId(currentArtifact.getId())
                .orElse(null);
        RecoveryState initialState = recoveryState(
                currentArtifact,
                currentTask,
                hasValidTaskSource(currentArtifact, currentTask),
                now);
        if (initialState != RecoveryState.FAILED_RETRYABLE) {
            return resultFor(initialState, currentTask, now);
        }

        Long canonicalArtifactId = currentArtifact.getId();
        entityManager.clear();
        QuestionExplanationGenerationTask task = taskRepository
                .findByArtifactIdForUpdate(canonicalArtifactId)
                .orElse(null);
        if (!hasValidTaskSource(currentArtifact, task)) {
            return notRetryable();
        }
        QuestionExplanationArtifact artifact = artifactRepository
                .findByIdForUpdate(canonicalArtifactId)
                .orElse(null);
        RecoveryState lockedState = recoveryState(
                artifact,
                task,
                hasValidTaskSource(artifact, task),
                LocalDateTime.now());
        if (lockedState != RecoveryState.FAILED_RETRYABLE) {
            return resultFor(lockedState, task, LocalDateTime.now());
        }

        LocalDateTime queuedAt = LocalDateTime.now();
        artifact.markPending();
        task.requestManualRetry(requestedBy, queuedAt);
        artifactRepository.save(artifact);
        taskRepository.save(task);
        return new RetryResult("PENDING", true, 0,
                "Đã xếp lịch tạo lại giải thích.");
    }

    private RetryTarget requireAuthorizedArtifactTarget(
            Long artifactId,
            Long requestedBy) {
        List<QuestionVersionExplanationBinding> bindings =
                bindingRepository.findByArtifactIdOrderByIdAsc(artifactId);
        Set<Long> questionVersionIds = bindings.stream()
                .map(QuestionVersionExplanationBinding::getQuestionVersionId)
                .collect(Collectors.toSet());
        Map<Long, PracticeQuestionVersion> questions = indexById(
                questionRepository.findAllById(questionVersionIds));
        Set<Long> publishedVersionIds = questions.values().stream()
                .map(PracticeQuestionVersion::getPublishedVersionId)
                .collect(Collectors.toSet());
        Map<Long, PracticePublishedVersion> publishedVersions = publishedVersionRepository
                .findAllById(publishedVersionIds).stream()
                .collect(Collectors.toMap(
                        PracticePublishedVersion::getId,
                        version -> version,
                        (left, right) -> left,
                        LinkedHashMap::new));

        AccessDeniedException denied = null;
        boolean authorizedBindingFound = false;
        for (QuestionVersionExplanationBinding binding : bindings) {
            PracticeQuestionVersion question = questions.get(binding.getQuestionVersionId());
            PracticePublishedVersion published = question == null
                    ? null
                    : publishedVersions.get(question.getPublishedVersionId());
            if (published == null) {
                continue;
            }
            try {
                authorizationService.requireSet(
                        published.getSetId(), requestedBy, PracticeAction.PUBLISH);
                authorizedBindingFound = true;
            } catch (AccessDeniedException exception) {
                denied = exception;
                continue;
            }
            PracticeSectionVersion section = sectionRepository
                    .findById(question.getSectionVersionId())
                    .orElse(null);
            QuestionExplanationArtifact artifact = artifactRepository
                    .findById(artifactId)
                    .orElse(null);
            if (validBinding(question, section, binding, artifact)) {
                return new RetryTarget(artifact, true);
            }
        }
        if (authorizedBindingFound) {
            return RetryTarget.invalid();
        }
        if (denied != null) {
            throw denied;
        }
        throw new AccessDeniedException(
                "Không tìm thấy liên kết phiên bản đã xuất bản mà bạn được phép quản lý.");
    }

    private RetryTarget resolveQuestionVersionTarget(
            Long setId,
            Long questionVersionId) {
        PracticeQuestionVersion question = questionRepository.findById(questionVersionId)
                .orElse(null);
        if (question == null) {
            return RetryTarget.invalid();
        }
        PracticePublishedVersion published = publishedVersionRepository
                .findById(question.getPublishedVersionId())
                .orElse(null);
        if (published == null || !Objects.equals(setId, published.getSetId())) {
            return RetryTarget.invalid();
        }
        PracticeSectionVersion section = sectionRepository
                .findById(question.getSectionVersionId())
                .orElse(null);
        QuestionVersionExplanationBinding binding = bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(
                        questionVersionId,
                        ReadingListeningExplanationClient.EXPLANATION_LANGUAGE)
                .orElse(null);
        QuestionExplanationArtifact artifact = binding == null
                ? null
                : artifactRepository.findById(binding.getArtifactId()).orElse(null);
        return validBinding(question, section, binding, artifact)
                ? new RetryTarget(artifact, true)
                : RetryTarget.invalid();
    }

    private boolean hasValidTaskSource(
            QuestionExplanationArtifact artifact,
            QuestionExplanationGenerationTask task) {
        if (artifact == null || task == null
                || task.getSourceQuestionVersionId() == null) {
            return false;
        }
        PracticeQuestionVersion sourceQuestion = questionRepository
                .findById(task.getSourceQuestionVersionId())
                .orElse(null);
        PracticeSectionVersion sourceSection = sourceQuestion == null
                ? null
                : sectionRepository.findById(sourceQuestion.getSectionVersionId())
                        .orElse(null);
        QuestionVersionExplanationBinding sourceBinding = sourceQuestion == null
                ? null
                : bindingRepository
                        .findByQuestionVersionIdAndExplanationLanguage(
                                sourceQuestion.getId(),
                                ReadingListeningExplanationClient.EXPLANATION_LANGUAGE)
                        .orElse(null);
        return validTaskSource(
                artifact, task, sourceQuestion, sourceSection, sourceBinding);
    }

    static RecoveryState recoveryState(
            QuestionExplanationArtifact artifact,
            QuestionExplanationGenerationTask task,
            boolean validTaskSource,
            LocalDateTime now) {
        if (artifact == null) {
            return RecoveryState.FAILED_NON_RETRYABLE;
        }
        if (QuestionExplanationArtifact.STATUS_READY.equals(artifact.getStatus())) {
            return RecoveryState.READY;
        }
        if (task == null || !validTaskSource) {
            return RecoveryState.FAILED_NON_RETRYABLE;
        }
        if (QuestionExplanationArtifact.STATUS_PENDING.equals(artifact.getStatus())
                && isActive(task.getStatus())) {
            return RecoveryState.PENDING;
        }
        if (!QuestionExplanationArtifact.STATUS_FAILED.equals(artifact.getStatus())
                || !QuestionExplanationGenerationTask.STATUS_FAILED.equals(task.getStatus())
                || !retryableFailure(artifact, task)) {
            return RecoveryState.FAILED_NON_RETRYABLE;
        }
        return retryAfterSeconds(task, now) > 0
                ? RecoveryState.RATE_LIMITED
                : RecoveryState.FAILED_RETRYABLE;
    }

    static long retryAfterSeconds(
            QuestionExplanationGenerationTask task,
            LocalDateTime now) {
        if (task == null || task.getLastRetryRequestedAt() == null || now == null) {
            return 0;
        }
        LocalDateTime allowedAt = task.getLastRetryRequestedAt()
                .plus(MANUAL_RETRY_COOLDOWN);
        return allowedAt.isAfter(now)
                ? Math.max(1, Duration.between(now, allowedAt).getSeconds())
                : 0;
    }

    static boolean validBinding(
            PracticeQuestionVersion question,
            PracticeSectionVersion section,
            QuestionVersionExplanationBinding binding,
            QuestionExplanationArtifact artifact) {
        if (question == null || section == null || binding == null || artifact == null
                || !Objects.equals(question.getPublishedVersionId(), section.getPublishedVersionId())
                || !Objects.equals(question.getSectionVersionId(), section.getId())
                || !Objects.equals(binding.getQuestionVersionId(), question.getId())
                || !Objects.equals(binding.getArtifactId(), artifact.getId())
                || !Objects.equals(binding.getFingerprint(), artifact.getFingerprint())
                || !ReadingListeningExplanationClient.EXPLANATION_LANGUAGE.equals(
                        binding.getExplanationLanguage())
                || !ReadingListeningExplanationClient.EXPLANATION_LANGUAGE.equals(
                        artifact.getExplanationLanguage())) {
            return false;
        }
        String skill = normalized(section.getSkill());
        if (!Set.of("READING", "LISTENING").contains(skill)
                || !skill.equals(normalized(artifact.getSkill()))
                || !Objects.equals(
                        normalized(question.getQuestionType()),
                        normalized(artifact.getQuestionType()))) {
            return false;
        }
        try {
            return CanonicalQuestionType.valueOf(
                    normalized(question.getQuestionType())).isObjective();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean validTaskSource(
            QuestionExplanationArtifact artifact,
            QuestionExplanationGenerationTask task,
            PracticeQuestionVersion sourceQuestion,
            PracticeSectionVersion sourceSection,
            QuestionVersionExplanationBinding sourceBinding) {
        return artifact != null
                && task != null
                && sourceQuestion != null
                && Objects.equals(task.getArtifactId(), artifact.getId())
                && Objects.equals(
                        task.getSourceQuestionVersionId(), sourceQuestion.getId())
                && validBinding(
                        sourceQuestion,
                        sourceSection,
                        sourceBinding,
                        artifact);
    }

    private static boolean retryableFailure(
            QuestionExplanationArtifact artifact,
            QuestionExplanationGenerationTask task) {
        String artifactCategory = normalized(artifact.getErrorCategory());
        String taskCategory = normalized(task.getErrorCategory());
        if (artifactCategory.isBlank() || !artifactCategory.equals(taskCategory)) {
            return false;
        }
        if (Set.of(
                "INVALID_PROVIDER_RESPONSE",
                "PROVIDER_TRANSPORT_ERROR",
                "GENERATION_INTERNAL_ERROR",
                "MEDIA_RESOLUTION_FAILED",
                "LEASE_EXPIRED_AFTER_MAX_ATTEMPTS").contains(artifactCategory)) {
            return true;
        }
        if (!artifactCategory.startsWith("PROVIDER_HTTP_")) {
            return false;
        }
        String statusCode =
                artifactCategory.substring("PROVIDER_HTTP_".length());
        if (statusCode.length() != 3
                || !statusCode.chars().allMatch(
                        character -> character >= '0' && character <= '9')) {
            return false;
        }
        int status = Integer.parseInt(statusCode);
        return status == 408
                || status == 425
                || status == 429
                || (status >= 500 && status <= 599);
    }

    private static boolean isActive(String status) {
        return QuestionExplanationGenerationTask.STATUS_PENDING.equals(status)
                || QuestionExplanationGenerationTask.STATUS_PROCESSING.equals(status)
                || QuestionExplanationGenerationTask.STATUS_RETRY_WAIT.equals(status);
    }

    private static RetryResult resultFor(
            RecoveryState state,
            QuestionExplanationGenerationTask task,
            LocalDateTime now) {
        return switch (state) {
            case READY -> new RetryResult(
                    "READY", false, 0,
                    "Giải thích đã sẵn sàng; không cần tạo lại.");
            case PENDING -> new RetryResult(
                    "PENDING", false, 0,
                    "Giải thích đang được xử lý; hệ thống không tạo yêu cầu trùng lặp.");
            case RATE_LIMITED -> new RetryResult(
                    "RATE_LIMITED", false, retryAfterSeconds(task, now),
                    "Yêu cầu thử lại đang trong thời gian chờ.");
            case FAILED_RETRYABLE -> throw new IllegalStateException(
                    "Trạng thái có thể thử lại phải được khóa trước khi xếp lịch.");
            case FAILED_NON_RETRYABLE -> notRetryable();
        };
    }

    private static RetryResult notRetryable() {
        return new RetryResult(
                "NOT_RETRYABLE",
                false,
                0,
                "Không thể thử lại. Hãy sửa nội dung hoặc bằng chứng rồi xuất bản phiên bản mới.");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<Long, PracticeQuestionVersion> indexById(
            Collection<PracticeQuestionVersion> questions) {
        return questions.stream().collect(Collectors.toMap(
                PracticeQuestionVersion::getId,
                question -> question,
                (left, right) -> left,
                LinkedHashMap::new));
    }

    public enum RecoveryState {
        READY,
        PENDING,
        FAILED_RETRYABLE,
        RATE_LIMITED,
        FAILED_NON_RETRYABLE
    }

    private record RetryTarget(
            QuestionExplanationArtifact artifact,
            boolean valid) {
        private static RetryTarget invalid() {
            return new RetryTarget(null, false);
        }
    }

    public record RetryResult(
            String status,
            boolean queued,
            long retryAfterSeconds,
            String message
    ) {
    }
}
