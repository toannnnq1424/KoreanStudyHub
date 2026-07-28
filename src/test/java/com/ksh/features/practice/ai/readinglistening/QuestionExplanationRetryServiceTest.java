package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionExplanationRetryServiceTest {

    private QuestionExplanationArtifactRepository artifactRepository;
    private QuestionExplanationGenerationTaskRepository taskRepository;
    private PracticeQuestionVersionRepository questionRepository;
    private PracticePublishedVersionRepository publishedVersionRepository;
    private PracticeSectionVersionRepository sectionRepository;
    private QuestionVersionExplanationBindingRepository bindingRepository;
    private PracticeAuthorizationService authorizationService;
    private EntityManager entityManager;
    private QuestionExplanationRetryService service;

    @BeforeEach
    void setUp() {
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        taskRepository = mock(QuestionExplanationGenerationTaskRepository.class);
        questionRepository = mock(PracticeQuestionVersionRepository.class);
        publishedVersionRepository = mock(PracticePublishedVersionRepository.class);
        sectionRepository = mock(PracticeSectionVersionRepository.class);
        bindingRepository = mock(QuestionVersionExplanationBindingRepository.class);
        authorizationService = mock(PracticeAuthorizationService.class);
        entityManager = mock(EntityManager.class);
        service = new QuestionExplanationRetryService(
                artifactRepository,
                taskRepository,
                questionRepository,
                publishedVersionRepository,
                sectionRepository,
                bindingRepository,
                authorizationService,
                entityManager);
    }

    @Test
    void readyArtifactReturnsIdempotentNoOpWithoutTakingLocks() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_READY);
        authorizeArtifact(artifact);

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.queued()).isFalse();
        assertThat(result.message()).contains("đã sẵn sàng").doesNotContain("provider");
        verify(authorizationService).requireSet(90L, 7L, PracticeAction.PUBLISH);
        verifyNoInteractions(taskRepository, entityManager);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
        verify(artifactRepository, never()).save(artifact);
    }

    @Test
    void pendingAndFailedArtifactsStillRequireCoherentTaskSources() {
        QuestionExplanationGenerationTask activeTask = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING);
        QuestionExplanationGenerationTask failedTask = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);

        assertThat(QuestionExplanationRetryService.recoveryState(
                artifact(QuestionExplanationArtifact.STATUS_PENDING),
                activeTask,
                false,
                LocalDateTime.now()))
                .isEqualTo(
                        QuestionExplanationRetryService.RecoveryState
                                .FAILED_NON_RETRYABLE);
        assertThat(QuestionExplanationRetryService.recoveryState(
                artifact(QuestionExplanationArtifact.STATUS_FAILED),
                failedTask,
                false,
                LocalDateTime.now()))
                .isEqualTo(
                        QuestionExplanationRetryService.RecoveryState
                                .FAILED_NON_RETRYABLE);
    }

    @Test
    void artifactCommandGlobalDenialPerformsZeroRepositoryReadsOrLocks() {
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requireGlobal(7L, PracticeAction.PUBLISH);

        assertThatThrownBy(() -> service.retry(50L, 7L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(
                bindingRepository,
                questionRepository,
                publishedVersionRepository,
                sectionRepository,
                artifactRepository,
                taskRepository,
                entityManager);
    }

    @Test
    void activeTaskReturnsIdempotentPendingNoOpWithoutTakingLocks() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_PENDING);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_PROCESSING);
        authorizeArtifact(artifact);
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.queued()).isFalse();
        assertThat(task.getManualRetryCount()).isZero();
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
    }

    @Test
    void failedArtifactAndFailedRetryableTaskAreQueuedOnceAfterOrderedLocks() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(task, "attemptCount", 4);
        authorizeArtifact(artifact);
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));
        when(taskRepository.findByArtifactIdForUpdate(50L))
                .thenReturn(Optional.of(task));
        when(artifactRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(artifact));

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.queued()).isTrue();
        assertThat(artifact.getStatus())
                .isEqualTo(QuestionExplanationArtifact.STATUS_PENDING);
        assertThat(task.getStatus())
                .isEqualTo(QuestionExplanationGenerationTask.STATUS_PENDING);
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getManualRetryCount()).isEqualTo(1);
        assertThat(task.getLastRetryRequestedBy()).isEqualTo(7L);
        InOrder lockOrder =
                inOrder(entityManager, taskRepository, artifactRepository);
        lockOrder.verify(entityManager).clear();
        lockOrder.verify(taskRepository).findByArtifactIdForUpdate(50L);
        lockOrder.verify(artifactRepository).findByIdForUpdate(50L);
    }

    @Test
    void recentlyRetriedFailureIsRateLimitedByServerWithoutTakingLocks() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(
                task, "lastRetryRequestedAt", LocalDateTime.now().minusSeconds(10));
        authorizeArtifact(artifact);
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("RATE_LIMITED");
        assertThat(result.queued()).isFalse();
        assertThat(result.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(artifact.getStatus())
                .isEqualTo(QuestionExplanationArtifact.STATUS_FAILED);
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
    }

    @Test
    void missingTaskAndNonRetryableFailureNeverQueue() {
        QuestionExplanationArtifact missingTaskArtifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        authorizeArtifact(missingTaskArtifact);
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.empty());

        QuestionExplanationRetryService.RetryResult missingTask =
                service.retry(50L, 7L);

        assertThat(missingTask.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(missingTask.queued()).isFalse();

        QuestionExplanationArtifact permanentArtifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        ReflectionTestUtils.setField(
                permanentArtifact, "errorCategory", "PROVIDER_HTTP_400");
        QuestionExplanationGenerationTask permanentTask = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(
                permanentTask, "errorCategory", "PROVIDER_HTTP_400");
        authorizeArtifact(permanentArtifact);
        when(taskRepository.findByArtifactId(50L))
                .thenReturn(Optional.of(permanentTask));

        QuestionExplanationRetryService.RetryResult permanent =
                service.retry(50L, 7L);

        assertThat(permanent.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(permanent.queued()).isFalse();
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
    }

    @Test
    void invalidFingerprintAndUnsupportedSkillFailClosedBeforeTaskLookup() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        ReflectionTestUtils.setField(artifact, "fingerprint", "b".repeat(64));
        authorizeArtifact(artifact);

        QuestionExplanationRetryService.RetryResult invalidFingerprint =
                service.retry(50L, 7L);

        assertThat(invalidFingerprint.status()).isEqualTo("NOT_RETRYABLE");
        verifyNoInteractions(taskRepository);

        ReflectionTestUtils.setField(artifact, "fingerprint", "a".repeat(64));
        PracticeSectionVersion speaking = section("SPEAKING");
        when(sectionRepository.findById(81L)).thenReturn(Optional.of(speaking));

        QuestionExplanationRetryService.RetryResult unsupportedSkill =
                service.retry(50L, 7L);

        assertThat(unsupportedSkill.status()).isEqualTo("NOT_RETRYABLE");
        verifyNoInteractions(taskRepository);
    }

    @Test
    void stableQuestionVersionCommandAuthorizesSetBeforeResolvingBinding() {
        when(authorizationService.requireSet(90L, 7L, PracticeAction.PUBLISH))
                .thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(
                () -> service.retryQuestionVersion(90L, 70L, 7L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(
                questionRepository,
                publishedVersionRepository,
                sectionRepository,
                bindingRepository,
                taskRepository,
                artifactRepository);
    }

    @Test
    void stableQuestionVersionCommandQueuesOnlyItsCoherentReadingBinding() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        PracticeQuestionVersion question = question();
        when(questionRepository.findById(70L)).thenReturn(Optional.of(question));
        when(publishedVersionRepository.findById(80L))
                .thenReturn(Optional.of(publishedVersion()));
        when(sectionRepository.findById(81L))
                .thenReturn(Optional.of(section("READING")));
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(70L, "vi"))
                .thenReturn(Optional.of(binding()));
        when(artifactRepository.findById(50L)).thenReturn(Optional.of(artifact));
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));
        when(taskRepository.findByArtifactIdForUpdate(50L))
                .thenReturn(Optional.of(task));
        when(artifactRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(artifact));

        QuestionExplanationRetryService.RetryResult result =
                service.retryQuestionVersion(90L, 70L, 7L);

        assertThat(result.queued()).isTrue();
        assertThat(result.status()).isEqualTo("PENDING");
        InOrder authorizationOrder =
                inOrder(authorizationService, questionRepository);
        authorizationOrder.verify(authorizationService)
                .requireSet(90L, 7L, PracticeAction.PUBLISH);
        authorizationOrder.verify(questionRepository, atLeastOnce())
                .findById(70L);
    }

    @Test
    void artifactCommandAuthorizesBoundSetBeforeTaskArtifactLocksOrProviderWork() {
        QuestionVersionExplanationBinding binding = binding();
        PracticeQuestionVersion question = question();
        PracticePublishedVersion published = publishedVersion();
        when(bindingRepository.findByArtifactIdOrderByIdAsc(50L))
                .thenReturn(List.of(binding));
        when(questionRepository.findAllById(Set.of(70L)))
                .thenReturn(List.of(question));
        when(publishedVersionRepository.findAllById(Set.of(80L)))
                .thenReturn(List.of(published));
        when(authorizationService.requireSet(90L, 7L, PracticeAction.PUBLISH))
                .thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.retry(50L, 7L))
                .isInstanceOf(AccessDeniedException.class);

        InOrder authorizationOrder =
                inOrder(authorizationService, bindingRepository);
        authorizationOrder.verify(authorizationService)
                .requireGlobal(7L, PracticeAction.PUBLISH);
        authorizationOrder.verify(bindingRepository)
                .findByArtifactIdOrderByIdAsc(50L);
        authorizationOrder.verify(authorizationService)
                .requireSet(90L, 7L, PracticeAction.PUBLISH);
        verifyNoInteractions(sectionRepository, taskRepository, artifactRepository);
    }

    @Test
    void missingOrMismatchedTaskSourceFailsClosedWithoutLocksOrMutation() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 999L);
        authorizeArtifact(artifact);
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        QuestionExplanationRetryService.RetryResult result =
                service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(result.queued()).isFalse();

        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 71L);
        PracticeQuestionVersion mismatchedSource = question();
        ReflectionTestUtils.setField(mismatchedSource, "id", 72L);
        QuestionVersionExplanationBinding mismatchedBinding = binding();
        ReflectionTestUtils.setField(
                mismatchedBinding, "questionVersionId", 72L);
        when(questionRepository.findById(71L))
                .thenReturn(Optional.of(mismatchedSource));
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(72L, "vi"))
                .thenReturn(Optional.of(mismatchedBinding));

        QuestionExplanationRetryService.RetryResult mismatched =
                service.retry(50L, 7L);

        assertThat(mismatched.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(mismatched.queued()).isFalse();
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
        verify(taskRepository, never()).save(task);
        verify(artifactRepository, never()).save(artifact);
    }

    @Test
    void sourceBindingToAnotherArtifactOrFingerprintFailsClosedWithoutLocks() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        authorizeArtifact(artifact);
        QuestionVersionExplanationBinding wrongSourceBinding = binding();
        ReflectionTestUtils.setField(wrongSourceBinding, "artifactId", 51L);
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(70L, "vi"))
                .thenReturn(Optional.of(wrongSourceBinding));
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));

        QuestionExplanationRetryService.RetryResult wrongArtifact =
                service.retry(50L, 7L);

        assertThat(wrongArtifact.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(wrongArtifact.queued()).isFalse();

        QuestionVersionExplanationBinding wrongFingerprint = binding();
        ReflectionTestUtils.setField(
                wrongFingerprint, "fingerprint", "b".repeat(64));
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(70L, "vi"))
                .thenReturn(Optional.of(wrongFingerprint));

        QuestionExplanationRetryService.RetryResult fingerprintMismatch =
                service.retry(50L, 7L);

        assertThat(fingerprintMismatch.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(fingerprintMismatch.queued()).isFalse();
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
    }

    @Test
    void nonReadingListeningOrNonObjectiveTaskSourceNeverQueues() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 71L);
        authorizeArtifact(artifact);
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));
        PracticeQuestionVersion source = question();
        ReflectionTestUtils.setField(source, "id", 71L);
        ReflectionTestUtils.setField(source, "sectionVersionId", 82L);
        QuestionVersionExplanationBinding sourceBinding = binding();
        ReflectionTestUtils.setField(sourceBinding, "questionVersionId", 71L);
        when(questionRepository.findById(71L)).thenReturn(Optional.of(source));
        when(sectionRepository.findById(82L))
                .thenReturn(Optional.of(section(82L, "SPEAKING")));
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(71L, "vi"))
                .thenReturn(Optional.of(sourceBinding));

        QuestionExplanationRetryService.RetryResult unsupportedSkill =
                service.retry(50L, 7L);

        assertThat(unsupportedSkill.status()).isEqualTo("NOT_RETRYABLE");
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);

        PracticeQuestionVersion essay = question();
        ReflectionTestUtils.setField(essay, "id", 71L);
        ReflectionTestUtils.setField(essay, "sectionVersionId", 82L);
        ReflectionTestUtils.setField(essay, "questionType", "ESSAY");
        when(questionRepository.findById(71L)).thenReturn(Optional.of(essay));
        when(sectionRepository.findById(82L))
                .thenReturn(Optional.of(section(82L, "READING")));

        QuestionExplanationRetryService.RetryResult nonObjective =
                service.retry(50L, 7L);

        assertThat(nonObjective.status()).isEqualTo("NOT_RETRYABLE");
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
    }

    @Test
    void validSharedArtifactSourceMayDifferFromSelectedQuestionAndQueue() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 71L);
        authorizeArtifact(artifact);

        PracticeQuestionVersion sharedSource = question();
        ReflectionTestUtils.setField(sharedSource, "id", 71L);
        ReflectionTestUtils.setField(sharedSource, "sectionVersionId", 82L);
        PracticeSectionVersion sharedSection = section("READING");
        ReflectionTestUtils.setField(sharedSection, "id", 82L);
        QuestionVersionExplanationBinding sharedBinding = binding();
        ReflectionTestUtils.setField(sharedBinding, "id", 41L);
        ReflectionTestUtils.setField(sharedBinding, "questionVersionId", 71L);
        when(questionRepository.findById(71L))
                .thenReturn(Optional.of(sharedSource));
        when(sectionRepository.findById(82L))
                .thenReturn(Optional.of(sharedSection));
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(71L, "vi"))
                .thenReturn(Optional.of(sharedBinding));
        when(taskRepository.findByArtifactId(50L)).thenReturn(Optional.of(task));
        when(taskRepository.findByArtifactIdForUpdate(50L))
                .thenReturn(Optional.of(task));
        when(artifactRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(artifact));

        QuestionExplanationRetryService.RetryResult result =
                service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.queued()).isTrue();
    }

    @Test
    void lockedTaskSourceChangeFailsClosedBeforeArtifactLockOrMutation() {
        QuestionExplanationArtifact artifact = artifact(
                QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask currentTask = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        QuestionExplanationGenerationTask lockedTask = task(
                QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(lockedTask, "sourceQuestionVersionId", 999L);
        authorizeArtifact(artifact);
        when(taskRepository.findByArtifactId(50L))
                .thenReturn(Optional.of(currentTask));
        when(taskRepository.findByArtifactIdForUpdate(50L))
                .thenReturn(Optional.of(lockedTask));
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        QuestionExplanationRetryService.RetryResult result =
                service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(result.queued()).isFalse();
        verify(artifactRepository, never()).findByIdForUpdate(50L);
        verify(taskRepository, never()).save(lockedTask);
        verify(artifactRepository, never()).save(artifact);
    }

    @Test
    void providerHttpCategoryAcceptsOnlyExactRetryableThreeDigitCodes() {
        for (String category : List.of(
                "PROVIDER_HTTP_408",
                "PROVIDER_HTTP_425",
                "PROVIDER_HTTP_429",
                "PROVIDER_HTTP_500",
                "PROVIDER_HTTP_599")) {
            QuestionExplanationArtifact artifact =
                    artifact(QuestionExplanationArtifact.STATUS_FAILED);
            QuestionExplanationGenerationTask task =
                    task(QuestionExplanationGenerationTask.STATUS_FAILED);
            ReflectionTestUtils.setField(artifact, "errorCategory", category);
            ReflectionTestUtils.setField(task, "errorCategory", category);

            assertThat(QuestionExplanationRetryService.recoveryState(
                    artifact, task, true, LocalDateTime.now()))
                    .as(category)
                    .isEqualTo(
                            QuestionExplanationRetryService.RecoveryState
                                    .FAILED_RETRYABLE);
        }

        for (String category : List.of(
                "PROVIDER_HTTP_600",
                "PROVIDER_HTTP_999",
                "PROVIDER_HTTP_0500",
                "PROVIDER_HTTP_50",
                "PROVIDER_HTTP_5A0",
                "PROVIDER_HTTP_５００",
                "PROVIDER_HTTP_-1")) {
            QuestionExplanationArtifact artifact =
                    artifact(QuestionExplanationArtifact.STATUS_FAILED);
            QuestionExplanationGenerationTask task =
                    task(QuestionExplanationGenerationTask.STATUS_FAILED);
            ReflectionTestUtils.setField(artifact, "errorCategory", category);
            ReflectionTestUtils.setField(task, "errorCategory", category);

            assertThat(QuestionExplanationRetryService.recoveryState(
                    artifact, task, true, LocalDateTime.now()))
                    .as(category)
                    .isEqualTo(
                            QuestionExplanationRetryService.RecoveryState
                                    .FAILED_NON_RETRYABLE);
        }
    }

    @Test
    void invalidProviderHttpCategoriesNeverTakeRetryLocksOrQueue() {
        for (String category : List.of(
                "PROVIDER_HTTP_600",
                "PROVIDER_HTTP_999",
                "PROVIDER_HTTP_0500",
                "PROVIDER_HTTP_50",
                "PROVIDER_HTTP_5A0",
                "PROVIDER_HTTP_５００")) {
            QuestionExplanationArtifact artifact =
                    artifact(QuestionExplanationArtifact.STATUS_FAILED);
            QuestionExplanationGenerationTask task =
                    task(QuestionExplanationGenerationTask.STATUS_FAILED);
            ReflectionTestUtils.setField(artifact, "errorCategory", category);
            ReflectionTestUtils.setField(task, "errorCategory", category);
            authorizeArtifact(artifact);
            when(taskRepository.findByArtifactId(50L))
                    .thenReturn(Optional.of(task));

            QuestionExplanationRetryService.RetryResult result =
                    service.retry(50L, 7L);

            assertThat(result.status()).as(category).isEqualTo("NOT_RETRYABLE");
            assertThat(result.queued()).as(category).isFalse();
        }
        verify(taskRepository, never()).findByArtifactIdForUpdate(50L);
        verify(artifactRepository, never()).findByIdForUpdate(50L);
    }

    private void authorizeArtifact(QuestionExplanationArtifact artifact) {
        QuestionVersionExplanationBinding binding = binding();
        PracticeQuestionVersion question = question();
        PracticePublishedVersion published = publishedVersion();
        when(bindingRepository.findByArtifactIdOrderByIdAsc(50L))
                .thenReturn(List.of(binding));
        when(questionRepository.findAllById(Set.of(70L)))
                .thenReturn(List.of(question));
        when(publishedVersionRepository.findAllById(Set.of(80L)))
                .thenReturn(List.of(published));
        when(sectionRepository.findById(81L))
                .thenReturn(Optional.of(section("READING")));
        when(questionRepository.findById(70L))
                .thenReturn(Optional.of(question));
        when(bindingRepository
                .findByQuestionVersionIdAndExplanationLanguage(70L, "vi"))
                .thenReturn(Optional.of(binding));
        when(artifactRepository.findById(50L)).thenReturn(Optional.of(artifact));
    }

    private static PracticeQuestionVersion question() {
        PracticeQuestionVersion question = instantiate(PracticeQuestionVersion.class);
        ReflectionTestUtils.setField(question, "id", 70L);
        ReflectionTestUtils.setField(question, "publishedVersionId", 80L);
        ReflectionTestUtils.setField(question, "sectionVersionId", 81L);
        ReflectionTestUtils.setField(question, "questionType", "SINGLE_CHOICE");
        return question;
    }

    private static PracticePublishedVersion publishedVersion() {
        PracticePublishedVersion published = instantiate(PracticePublishedVersion.class);
        ReflectionTestUtils.setField(published, "id", 80L);
        ReflectionTestUtils.setField(published, "setId", 90L);
        ReflectionTestUtils.setField(published, "versionNumber", 1);
        return published;
    }

    private static PracticeSectionVersion section(String skill) {
        return section(81L, skill);
    }

    private static PracticeSectionVersion section(Long id, String skill) {
        PracticeSectionVersion section = instantiate(PracticeSectionVersion.class);
        ReflectionTestUtils.setField(section, "id", id);
        ReflectionTestUtils.setField(section, "publishedVersionId", 80L);
        ReflectionTestUtils.setField(section, "skill", skill);
        return section;
    }

    private static QuestionVersionExplanationBinding binding() {
        QuestionVersionExplanationBinding binding =
                instantiate(QuestionVersionExplanationBinding.class);
        ReflectionTestUtils.setField(binding, "id", 40L);
        ReflectionTestUtils.setField(binding, "questionVersionId", 70L);
        ReflectionTestUtils.setField(binding, "artifactId", 50L);
        ReflectionTestUtils.setField(binding, "explanationLanguage", "vi");
        ReflectionTestUtils.setField(binding, "fingerprint", "a".repeat(64));
        return binding;
    }

    private static QuestionExplanationArtifact artifact(String status) {
        QuestionExplanationArtifact artifact =
                instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", 50L);
        ReflectionTestUtils.setField(artifact, "status", status);
        ReflectionTestUtils.setField(artifact, "fingerprint", "a".repeat(64));
        ReflectionTestUtils.setField(artifact, "skill", "READING");
        ReflectionTestUtils.setField(
                artifact, "questionType", "SINGLE_CHOICE");
        ReflectionTestUtils.setField(artifact, "explanationLanguage", "vi");
        ReflectionTestUtils.setField(
                artifact, "errorCategory", "PROVIDER_TRANSPORT_ERROR");
        return artifact;
    }

    private static QuestionExplanationGenerationTask task(String status) {
        QuestionExplanationGenerationTask task =
                instantiate(QuestionExplanationGenerationTask.class);
        ReflectionTestUtils.setField(task, "id", 60L);
        ReflectionTestUtils.setField(task, "artifactId", 50L);
        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 70L);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "attemptCount", 0);
        ReflectionTestUtils.setField(task, "maxAttempts", 4);
        ReflectionTestUtils.setField(task, "manualRetryCount", 0);
        ReflectionTestUtils.setField(
                task, "errorCategory", "PROVIDER_TRANSPORT_ERROR");
        return task;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Could not create test fixture " + type.getSimpleName(),
                    exception);
        }
    }
}
