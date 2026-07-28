package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService.RecoveryState;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionExplanationRecoveryQueryServiceTest {

    private PracticeAuthorizationService authorizationService;
    private PracticePublishedVersionRepository publishedVersionRepository;
    private PracticeQuestionVersionRepository questionRepository;
    private PracticeSectionVersionRepository sectionRepository;
    private QuestionVersionExplanationBindingRepository bindingRepository;
    private QuestionExplanationArtifactRepository artifactRepository;
    private QuestionExplanationGenerationTaskRepository taskRepository;
    private QuestionExplanationRecoveryQueryService service;

    @BeforeEach
    void setUp() {
        authorizationService = mock(PracticeAuthorizationService.class);
        publishedVersionRepository = mock(PracticePublishedVersionRepository.class);
        questionRepository = mock(PracticeQuestionVersionRepository.class);
        sectionRepository = mock(PracticeSectionVersionRepository.class);
        bindingRepository = mock(QuestionVersionExplanationBindingRepository.class);
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        taskRepository = mock(QuestionExplanationGenerationTaskRepository.class);
        service = new QuestionExplanationRecoveryQueryService(
                authorizationService,
                publishedVersionRepository,
                questionRepository,
                sectionRepository,
                bindingRepository,
                artifactRepository,
                taskRepository);
    }

    @Test
    void authorizationRunsBeforeEveryRecoveryRead() {
        when(authorizationService.requireSet(90L, 7L, PracticeAction.PUBLISH))
                .thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.load(90L, List.of(80L), 7L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(
                publishedVersionRepository,
                questionRepository,
                sectionRepository,
                bindingRepository,
                artifactRepository,
                taskRepository);
    }

    @Test
    void boundedBatchLoadProducesTheExactFiveStateAndActionMatrix() {
        List<PracticeQuestionVersion> questions = List.of(
                question(101L, 201L, 1),
                question(102L, 202L, 2),
                question(103L, 203L, 3),
                question(104L, 204L, 4),
                question(105L, 205L, 5));
        List<PracticeSectionVersion> sections = List.of(
                section(201L, "READING"),
                section(202L, "LISTENING"),
                section(203L, "READING"),
                section(204L, "LISTENING"),
                section(205L, "READING"));
        List<QuestionVersionExplanationBinding> bindings = List.of(
                binding(301L, 101L, 401L, "a"),
                binding(302L, 102L, 402L, "b"),
                binding(303L, 103L, 403L, "c"),
                binding(304L, 104L, 404L, "d"),
                binding(305L, 105L, 405L, "e"));
        List<QuestionExplanationArtifact> artifacts = List.of(
                artifact(401L, "a", QuestionExplanationArtifact.STATUS_READY,
                        "PROVIDER_TRANSPORT_ERROR"),
                artifact(402L, "b", QuestionExplanationArtifact.STATUS_PENDING,
                        null),
                artifact(403L, "c", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR"),
                artifact(404L, "d", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR"),
                artifact(405L, "e", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_HTTP_400"));
        List<QuestionExplanationGenerationTask> tasks = List.of(
                task(501L, 401L, 101L,
                        QuestionExplanationGenerationTask.STATUS_SUCCEEDED,
                        null, null),
                task(502L, 402L, 102L,
                        QuestionExplanationGenerationTask.STATUS_PROCESSING,
                        null, null),
                task(503L, 403L, 103L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR", null),
                task(504L, 404L, 104L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR",
                        LocalDateTime.now().minusSeconds(10)),
                task(505L, 405L, 105L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_HTTP_400", null));
        stubBatch(questions, sections, bindings, artifacts, tasks);

        List<QuestionExplanationRecoveryQueryService.RecoveryRow> rows =
                service.load(90L, List.of(80L), 7L);

        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::state)
                .containsExactly(
                        RecoveryState.READY,
                        RecoveryState.PENDING,
                        RecoveryState.FAILED_RETRYABLE,
                        RecoveryState.RATE_LIMITED,
                        RecoveryState.FAILED_NON_RETRYABLE);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::retryableAction)
                .containsExactly(false, false, true, false, false);
        assertThat(rows.get(3).retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::guidance)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("PROVIDER_", "raw", "exception"));

        var order = inOrder(authorizationService, publishedVersionRepository);
        order.verify(authorizationService)
                .requireSet(90L, 7L, PracticeAction.PUBLISH);
        order.verify(publishedVersionRepository).findAllById(List.of(80L));
        verify(questionRepository)
                .findByPublishedVersionIdInOrderByPublishedVersionIdAscSectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                        List.of(80L));
        verify(questionRepository).findAllById(any());
        verify(sectionRepository, times(2)).findAllById(any());
        verify(bindingRepository, times(2))
                .findByQuestionVersionIdInAndExplanationLanguage(any(), any());
        verify(artifactRepository).findAllById(any());
        verify(taskRepository).findByArtifactIdIn(any());
        verify(questionRepository, never()).findById(anyLong());
        verify(sectionRepository, never()).findById(anyLong());
        verify(artifactRepository, never()).findById(anyLong());
        verify(taskRepository, never()).findByArtifactId(anyLong());
        verify(taskRepository, never()).findByArtifactIdForUpdate(anyLong());
        verify(artifactRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void readyArtifactWithoutRetainedTaskProjectsReadyWithoutRetryAction() {
        PracticeQuestionVersion question = question(101L, 201L, 1);
        PracticeSectionVersion section = section(201L, "READING");
        QuestionVersionExplanationBinding binding =
                binding(301L, 101L, 401L, "ready");
        QuestionExplanationArtifact artifact = artifact(
                401L,
                "ready",
                QuestionExplanationArtifact.STATUS_READY,
                null);
        stubBatch(
                List.of(question),
                List.of(section),
                List.of(binding),
                List.of(artifact),
                List.of());

        List<QuestionExplanationRecoveryQueryService.RecoveryRow> rows =
                service.load(90L, List.of(80L), 7L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo(RecoveryState.READY);
            assertThat(row.retryableAction()).isFalse();
            assertThat(row.guidance()).isEqualTo(
                    "Không cần tạo thêm yêu cầu.");
        });
        verify(taskRepository).findByArtifactIdIn(any());
        verify(taskRepository, never()).findByArtifactIdForUpdate(anyLong());
        verify(artifactRepository, never()).findByIdForUpdate(anyLong());
        verify(taskRepository, never()).save(any());
        verify(artifactRepository, never()).save(any());
    }

    @Test
    void invalidFingerprintAndMissingTaskFailClosedWhileUnsupportedSkillIsExcluded() {
        PracticeQuestionVersion invalid = question(101L, 201L, 1);
        PracticeQuestionVersion missingTask = question(102L, 202L, 2);
        PracticeQuestionVersion speaking = question(103L, 203L, 3);
        stubBatch(
                List.of(invalid, missingTask, speaking),
                List.of(
                        section(201L, "READING"),
                        section(202L, "LISTENING"),
                        section(203L, "SPEAKING")),
                List.of(
                        binding(301L, 101L, 401L, "expected"),
                        binding(302L, 102L, 402L, "missing-task"),
                        binding(303L, 103L, 403L, "speaking")),
                List.of(
                        artifact(
                                401L,
                                "different",
                                QuestionExplanationArtifact.STATUS_FAILED,
                                "PROVIDER_TRANSPORT_ERROR"),
                        artifact(
                                402L,
                                "missing-task",
                                QuestionExplanationArtifact.STATUS_FAILED,
                                "PROVIDER_TRANSPORT_ERROR"),
                        artifact(
                                403L,
                                "speaking",
                                QuestionExplanationArtifact.STATUS_FAILED,
                                "PROVIDER_TRANSPORT_ERROR")),
                List.of(task(
                        501L,
                        401L,
                        101L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR",
                        null)));

        List<QuestionExplanationRecoveryQueryService.RecoveryRow> rows =
                service.load(90L, List.of(80L), 7L);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::state)
                .containsOnly(RecoveryState.FAILED_NON_RETRYABLE);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::retryableAction)
                .containsOnly(false);
    }

    @Test
    void validSharedArtifactSourceOutsideSelectedVersionsRemainsRetryable() {
        PracticeQuestionVersion selected = question(101L, 201L, 1);
        PracticeQuestionVersion sharedSource =
                question(201L, 81L, 301L, 1, "SINGLE_CHOICE");
        PracticeSectionVersion selectedSection =
                section(201L, 80L, "READING");
        PracticeSectionVersion sourceSection =
                section(301L, 81L, "READING");
        QuestionVersionExplanationBinding selectedBinding =
                binding(301L, 101L, 401L, "shared");
        QuestionVersionExplanationBinding sourceBinding =
                binding(302L, 201L, 401L, "shared");
        QuestionExplanationArtifact artifact = artifact(
                401L,
                "shared",
                QuestionExplanationArtifact.STATUS_FAILED,
                "PROVIDER_TRANSPORT_ERROR");
        QuestionExplanationGenerationTask task = task(
                501L,
                401L,
                201L,
                QuestionExplanationGenerationTask.STATUS_FAILED,
                "PROVIDER_TRANSPORT_ERROR",
                null);
        stubBatch(
                List.of(selected),
                List.of(sharedSource),
                List.of(selectedSection, sourceSection),
                List.of(selectedBinding, sourceBinding),
                List.of(artifact),
                List.of(task));

        List<QuestionExplanationRecoveryQueryService.RecoveryRow> rows =
                service.load(90L, List.of(80L), 7L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo(RecoveryState.FAILED_RETRYABLE);
            assertThat(row.retryableAction()).isTrue();
        });
    }

    @Test
    void incoherentTaskSourcesFailClosedWithoutRetryActions() {
        List<PracticeQuestionVersion> selected = List.of(
                question(101L, 201L, 1),
                question(102L, 202L, 2),
                question(103L, 203L, 3),
                question(104L, 204L, 4));
        PracticeQuestionVersion wrongBindingSource =
                question(201L, 80L, 301L, 1, "SINGLE_CHOICE");
        PracticeQuestionVersion speakingSource =
                question(202L, 80L, 302L, 2, "SINGLE_CHOICE");
        PracticeQuestionVersion essaySource =
                question(203L, 80L, 303L, 3, "ESSAY");
        List<PracticeSectionVersion> sections = List.of(
                section(201L, "READING"),
                section(202L, "LISTENING"),
                section(203L, "READING"),
                section(204L, "LISTENING"),
                section(301L, "LISTENING"),
                section(302L, "SPEAKING"),
                section(303L, "LISTENING"));
        List<QuestionVersionExplanationBinding> bindings = List.of(
                binding(301L, 101L, 401L, "a"),
                binding(302L, 102L, 402L, "b"),
                binding(303L, 103L, 403L, "c"),
                binding(304L, 104L, 404L, "d"),
                binding(305L, 201L, 499L, "wrong"),
                binding(306L, 202L, 403L, "c"),
                binding(307L, 203L, 404L, "d"));
        List<QuestionExplanationArtifact> artifacts = List.of(
                artifact(401L, "a", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR"),
                artifact(402L, "b", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR"),
                artifact(403L, "c", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR"),
                artifact(404L, "d", QuestionExplanationArtifact.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR"));
        List<QuestionExplanationGenerationTask> tasks = List.of(
                task(501L, 401L, 999L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR", null),
                task(502L, 402L, 201L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR", null),
                task(503L, 403L, 202L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR", null),
                task(504L, 404L, 203L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        "PROVIDER_TRANSPORT_ERROR", null));
        stubBatch(
                selected,
                List.of(wrongBindingSource, speakingSource, essaySource),
                sections,
                bindings,
                artifacts,
                tasks);

        List<QuestionExplanationRecoveryQueryService.RecoveryRow> rows =
                service.load(90L, List.of(80L), 7L);

        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::state)
                .containsOnly(RecoveryState.FAILED_NON_RETRYABLE);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow
                                ::retryableAction)
                .containsOnly(false);
        verify(taskRepository, never()).findByArtifactIdForUpdate(anyLong());
        verify(artifactRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void strictProviderHttpCategoriesDriveTheProjectedActionMatrix() {
        List<PracticeQuestionVersion> questions = List.of(
                question(101L, 201L, 1),
                question(102L, 202L, 2),
                question(103L, 203L, 3),
                question(104L, 204L, 4),
                question(105L, 205L, 5));
        List<PracticeSectionVersion> sections = List.of(
                section(201L, "READING"),
                section(202L, "LISTENING"),
                section(203L, "READING"),
                section(204L, "LISTENING"),
                section(205L, "READING"));
        List<QuestionVersionExplanationBinding> bindings = List.of(
                binding(301L, 101L, 401L, "a"),
                binding(302L, 102L, 402L, "b"),
                binding(303L, 103L, 403L, "c"),
                binding(304L, 104L, 404L, "d"),
                binding(305L, 105L, 405L, "e"));
        List<String> categories = List.of(
                "PROVIDER_HTTP_600",
                "PROVIDER_HTTP_999",
                "PROVIDER_HTTP_0500",
                "PROVIDER_HTTP_5A0",
                "PROVIDER_HTTP_500");
        List<QuestionExplanationArtifact> artifacts = List.of(
                artifact(401L, "a", QuestionExplanationArtifact.STATUS_FAILED,
                        categories.get(0)),
                artifact(402L, "b", QuestionExplanationArtifact.STATUS_FAILED,
                        categories.get(1)),
                artifact(403L, "c", QuestionExplanationArtifact.STATUS_FAILED,
                        categories.get(2)),
                artifact(404L, "d", QuestionExplanationArtifact.STATUS_FAILED,
                        categories.get(3)),
                artifact(405L, "e", QuestionExplanationArtifact.STATUS_FAILED,
                        categories.get(4)));
        List<QuestionExplanationGenerationTask> tasks = List.of(
                task(501L, 401L, 101L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        categories.get(0), null),
                task(502L, 402L, 102L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        categories.get(1), null),
                task(503L, 403L, 103L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        categories.get(2), null),
                task(504L, 404L, 104L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        categories.get(3), null),
                task(505L, 405L, 105L,
                        QuestionExplanationGenerationTask.STATUS_FAILED,
                        categories.get(4), null));
        stubBatch(questions, sections, bindings, artifacts, tasks);

        List<QuestionExplanationRecoveryQueryService.RecoveryRow> rows =
                service.load(90L, List.of(80L), 7L);

        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow::state)
                .containsExactly(
                        RecoveryState.FAILED_NON_RETRYABLE,
                        RecoveryState.FAILED_NON_RETRYABLE,
                        RecoveryState.FAILED_NON_RETRYABLE,
                        RecoveryState.FAILED_NON_RETRYABLE,
                        RecoveryState.FAILED_RETRYABLE);
        assertThat(rows).extracting(
                        QuestionExplanationRecoveryQueryService.RecoveryRow
                                ::retryableAction)
                .containsExactly(false, false, false, false, true);
    }

    private void stubBatch(
            List<PracticeQuestionVersion> questions,
            List<PracticeSectionVersion> sections,
            List<QuestionVersionExplanationBinding> bindings,
            List<QuestionExplanationArtifact> artifacts,
            List<QuestionExplanationGenerationTask> tasks) {
        stubBatch(
                questions,
                questions,
                sections,
                bindings,
                artifacts,
                tasks);
    }

    private void stubBatch(
            List<PracticeQuestionVersion> selectedQuestions,
            List<PracticeQuestionVersion> sourceQuestions,
            List<PracticeSectionVersion> sections,
            List<QuestionVersionExplanationBinding> bindings,
            List<QuestionExplanationArtifact> artifacts,
            List<QuestionExplanationGenerationTask> tasks) {
        when(publishedVersionRepository.findAllById(List.of(80L)))
                .thenReturn(List.of(publishedVersion()));
        when(questionRepository
                .findByPublishedVersionIdInOrderByPublishedVersionIdAscSectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                        List.of(80L)))
                .thenReturn(selectedQuestions);
        when(questionRepository.findAllById(any())).thenReturn(sourceQuestions);
        when(sectionRepository.findAllById(any())).thenReturn(sections);
        when(bindingRepository
                .findByQuestionVersionIdInAndExplanationLanguage(any(), any()))
                .thenReturn(bindings);
        when(artifactRepository.findAllById(any())).thenReturn(artifacts);
        when(taskRepository.findByArtifactIdIn(any())).thenReturn(tasks);
    }

    private static PracticePublishedVersion publishedVersion() {
        PracticePublishedVersion version = instantiate(PracticePublishedVersion.class);
        ReflectionTestUtils.setField(version, "id", 80L);
        ReflectionTestUtils.setField(version, "setId", 90L);
        ReflectionTestUtils.setField(version, "versionNumber", 3);
        return version;
    }

    private static PracticeQuestionVersion question(
            Long id,
            Long sectionVersionId,
            int questionNo) {
        return question(
                id,
                80L,
                sectionVersionId,
                questionNo,
                "SINGLE_CHOICE");
    }

    private static PracticeQuestionVersion question(
            Long id,
            Long publishedVersionId,
            Long sectionVersionId,
            int questionNo,
            String questionType) {
        PracticeQuestionVersion question = instantiate(PracticeQuestionVersion.class);
        ReflectionTestUtils.setField(question, "id", id);
        ReflectionTestUtils.setField(
                question, "publishedVersionId", publishedVersionId);
        ReflectionTestUtils.setField(
                question, "sectionVersionId", sectionVersionId);
        ReflectionTestUtils.setField(question, "questionNo", questionNo);
        ReflectionTestUtils.setField(question, "displayOrder", questionNo);
        ReflectionTestUtils.setField(
                question, "questionType", questionType);
        ReflectionTestUtils.setField(
                question, "prompt", "Câu hỏi " + questionNo);
        return question;
    }

    private static PracticeSectionVersion section(Long id, String skill) {
        return section(id, 80L, skill);
    }

    private static PracticeSectionVersion section(
            Long id,
            Long publishedVersionId,
            String skill) {
        PracticeSectionVersion section = instantiate(PracticeSectionVersion.class);
        ReflectionTestUtils.setField(section, "id", id);
        ReflectionTestUtils.setField(
                section, "publishedVersionId", publishedVersionId);
        ReflectionTestUtils.setField(section, "skill", skill);
        ReflectionTestUtils.setField(section, "title", "Phần " + id);
        return section;
    }

    private static QuestionVersionExplanationBinding binding(
            Long id,
            Long questionVersionId,
            Long artifactId,
            String fingerprint) {
        QuestionVersionExplanationBinding binding =
                instantiate(QuestionVersionExplanationBinding.class);
        ReflectionTestUtils.setField(binding, "id", id);
        ReflectionTestUtils.setField(
                binding, "questionVersionId", questionVersionId);
        ReflectionTestUtils.setField(binding, "artifactId", artifactId);
        ReflectionTestUtils.setField(binding, "explanationLanguage", "vi");
        ReflectionTestUtils.setField(binding, "fingerprint", fingerprint);
        return binding;
    }

    private static QuestionExplanationArtifact artifact(
            Long id,
            String fingerprint,
            String status,
            String errorCategory) {
        QuestionExplanationArtifact artifact =
                instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", id);
        ReflectionTestUtils.setField(artifact, "fingerprint", fingerprint);
        ReflectionTestUtils.setField(artifact, "skill",
                id == 402L || id == 404L ? "LISTENING" : "READING");
        ReflectionTestUtils.setField(
                artifact, "questionType", "SINGLE_CHOICE");
        ReflectionTestUtils.setField(artifact, "explanationLanguage", "vi");
        ReflectionTestUtils.setField(artifact, "status", status);
        ReflectionTestUtils.setField(
                artifact, "errorCategory", errorCategory);
        return artifact;
    }

    private static QuestionExplanationGenerationTask task(
            Long id,
            Long artifactId,
            Long sourceQuestionVersionId,
            String status,
            String errorCategory,
            LocalDateTime lastRetryRequestedAt) {
        QuestionExplanationGenerationTask task =
                instantiate(QuestionExplanationGenerationTask.class);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "artifactId", artifactId);
        ReflectionTestUtils.setField(
                task, "sourceQuestionVersionId", sourceQuestionVersionId);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "errorCategory", errorCategory);
        ReflectionTestUtils.setField(
                task, "lastRetryRequestedAt", lastRetryRequestedAt);
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
