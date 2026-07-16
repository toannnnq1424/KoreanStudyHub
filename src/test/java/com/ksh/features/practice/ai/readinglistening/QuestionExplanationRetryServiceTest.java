package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionExplanationRetryServiceTest {

    private QuestionExplanationArtifactRepository artifactRepository;
    private QuestionExplanationGenerationTaskRepository taskRepository;
    private PracticeQuestionVersionRepository questionRepository;
    private PracticePublishedVersionRepository publishedVersionRepository;
    private QuestionVersionExplanationBindingRepository bindingRepository;
    private PracticeAuthorizationService authorizationService;
    private QuestionExplanationRetryService service;

    @BeforeEach
    void setUp() {
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        taskRepository = mock(QuestionExplanationGenerationTaskRepository.class);
        questionRepository = mock(PracticeQuestionVersionRepository.class);
        publishedVersionRepository = mock(PracticePublishedVersionRepository.class);
        bindingRepository = mock(QuestionVersionExplanationBindingRepository.class);
        authorizationService = mock(PracticeAuthorizationService.class);
        service = new QuestionExplanationRetryService(
                artifactRepository,
                taskRepository,
                questionRepository,
                publishedVersionRepository,
                bindingRepository,
                authorizationService);
        authorizeArtifact();
    }

    @Test
    void readyArtifactReturnsIdempotentNoOp() {
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_READY);
        when(artifactRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(artifact));
        when(taskRepository.findByArtifactIdForUpdate(50L)).thenReturn(Optional.empty());

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.queued()).isFalse();
        verify(authorizationService).requireSet(90L, 7L, PracticeAction.PUBLISH);
    }

    @Test
    void activeTaskReturnsIdempotentPendingNoOp() {
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_PENDING);
        QuestionExplanationGenerationTask task = task(QuestionExplanationGenerationTask.STATUS_PROCESSING);
        when(artifactRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(artifact));
        when(taskRepository.findByArtifactIdForUpdate(50L)).thenReturn(Optional.of(task));

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.queued()).isFalse();
        assertThat(task.getManualRetryCount()).isZero();
    }

    @Test
    void terminalFailureIsQueuedOnceAndResetsAttemptState() {
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(task, "attemptCount", 4);
        when(artifactRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(artifact));
        when(taskRepository.findByArtifactIdForUpdate(50L)).thenReturn(Optional.of(task));

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.queued()).isTrue();
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_PENDING);
        assertThat(task.getStatus()).isEqualTo(QuestionExplanationGenerationTask.STATUS_PENDING);
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getManualRetryCount()).isEqualTo(1);
        assertThat(task.getLastRetryRequestedBy()).isEqualTo(7L);
        InOrder lockOrder = inOrder(taskRepository, artifactRepository);
        lockOrder.verify(taskRepository).findByArtifactIdForUpdate(50L);
        lockOrder.verify(artifactRepository).findByIdForUpdate(50L);
    }

    @Test
    void recentlyRetriedTerminalFailureIsRateLimited() {
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_FAILED);
        QuestionExplanationGenerationTask task = task(QuestionExplanationGenerationTask.STATUS_FAILED);
        ReflectionTestUtils.setField(task, "lastRetryRequestedAt", LocalDateTime.now().minusSeconds(10));
        when(artifactRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(artifact));
        when(taskRepository.findByArtifactIdForUpdate(50L)).thenReturn(Optional.of(task));

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("RATE_LIMITED");
        assertThat(result.queued()).isFalse();
        assertThat(result.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_FAILED);
    }

    @Test
    void permanentPublishInputFailureWithoutTaskRequiresRepublish() {
        QuestionExplanationArtifact artifact = artifact(QuestionExplanationArtifact.STATUS_FAILED);
        when(artifactRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(artifact));
        when(taskRepository.findByArtifactIdForUpdate(50L)).thenReturn(Optional.empty());

        QuestionExplanationRetryService.RetryResult result = service.retry(50L, 7L);

        assertThat(result.status()).isEqualTo("NOT_RETRYABLE");
        assertThat(result.queued()).isFalse();
    }

    @Test
    void unauthorizedRetryFailsBeforeTakingTaskOrArtifactLocks() {
        when(authorizationService.requireSet(90L, 7L, PracticeAction.PUBLISH))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.retry(50L, 7L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(taskRepository, artifactRepository);
    }

    private void authorizeArtifact() {
        QuestionVersionExplanationBinding binding = mock(QuestionVersionExplanationBinding.class);
        when(binding.getQuestionVersionId()).thenReturn(70L);
        when(bindingRepository.findByArtifactIdOrderByIdAsc(50L)).thenReturn(List.of(binding));
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getPublishedVersionId()).thenReturn(80L);
        when(questionRepository.findAllById(Set.of(70L))).thenReturn(List.of(question));
        PracticePublishedVersion published = mock(PracticePublishedVersion.class);
        when(published.getSetId()).thenReturn(90L);
        when(publishedVersionRepository.findAllById(Set.of(80L))).thenReturn(List.of(published));
    }

    private static QuestionExplanationArtifact artifact(String status) {
        QuestionExplanationArtifact artifact = instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", 50L);
        ReflectionTestUtils.setField(artifact, "status", status);
        return artifact;
    }

    private static QuestionExplanationGenerationTask task(String status) {
        QuestionExplanationGenerationTask task = instantiate(QuestionExplanationGenerationTask.class);
        ReflectionTestUtils.setField(task, "id", 60L);
        ReflectionTestUtils.setField(task, "artifactId", 50L);
        ReflectionTestUtils.setField(task, "sourceQuestionVersionId", 70L);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "attemptCount", 0);
        ReflectionTestUtils.setField(task, "maxAttempts", 4);
        ReflectionTestUtils.setField(task, "manualRetryCount", 0);
        return task;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not create test fixture " + type.getSimpleName(), exception);
        }
    }
}
