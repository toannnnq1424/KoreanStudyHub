package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.ai.readinglistening.ExplanationInputFactory.PreparedExplanation;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionExplanationPreparationServiceTest {

    private PracticeSectionVersionRepository sectionRepository;
    private PracticeQuestionGroupVersionRepository groupRepository;
    private PracticeQuestionVersionRepository questionRepository;
    private ExplanationInputFactory inputFactory;
    private QuestionExplanationArtifactRepository artifactRepository;
    private QuestionVersionExplanationBindingRepository bindingRepository;
    private QuestionExplanationGenerationTaskRepository taskRepository;
    private QuestionExplanationPreparationService service;

    @BeforeEach
    void setUp() {
        sectionRepository = mock(PracticeSectionVersionRepository.class);
        groupRepository = mock(PracticeQuestionGroupVersionRepository.class);
        questionRepository = mock(PracticeQuestionVersionRepository.class);
        inputFactory = mock(ExplanationInputFactory.class);
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        bindingRepository = mock(QuestionVersionExplanationBindingRepository.class);
        taskRepository = mock(QuestionExplanationGenerationTaskRepository.class);
        service = new QuestionExplanationPreparationService(
                sectionRepository,
                groupRepository,
                questionRepository,
                inputFactory,
                artifactRepository,
                bindingRepository,
                taskRepository);
    }

    @Test
    void newFingerprintCreatesOneImmutableBindingAndOneGenerationTask() {
        Fixture fixture = fixture(101L);
        QuestionExplanationArtifact artifact = artifact(501L, QuestionExplanationArtifact.STATUS_PENDING);
        QuestionVersionExplanationBinding binding = binding(101L, 501L);
        when(inputFactory.prepare(fixture.question(), null, fixture.section()))
                .thenReturn(prepared("a".repeat(64), null));
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(101L, "vi"))
                .thenReturn(Optional.empty(), Optional.of(binding));
        when(artifactRepository.findByFingerprint("a".repeat(64)))
                .thenReturn(Optional.of(artifact));
        when(taskRepository.insertPendingIfAbsent(501L, 101L, 4)).thenReturn(1);

        QuestionExplanationPreparationService.PreparationSummary summary =
                service.preparePublishedVersion(77L);

        assertThat(summary).isEqualTo(
                new QuestionExplanationPreparationService.PreparationSummary(1, 0, 1, 0));
        verify(artifactRepository).insertPendingIfAbsent(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(bindingRepository).bindIfAbsent(101L, 501L, "vi", "a".repeat(64));
        verify(taskRepository).insertPendingIfAbsent(501L, 101L, 4);
    }

    @Test
    void unchangedQuestionVersionReusesReadyArtifactWithoutQueueingProviderWork() {
        Fixture fixture = fixture(102L);
        QuestionExplanationArtifact artifact = artifact(502L, QuestionExplanationArtifact.STATUS_READY);
        QuestionVersionExplanationBinding binding = binding(102L, 502L);
        when(inputFactory.prepare(fixture.question(), null, fixture.section()))
                .thenReturn(prepared("b".repeat(64), null));
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(102L, "vi"))
                .thenReturn(Optional.empty(), Optional.of(binding));
        when(artifactRepository.findByFingerprint("b".repeat(64)))
                .thenReturn(Optional.of(artifact));

        QuestionExplanationPreparationService.PreparationSummary summary =
                service.preparePublishedVersion(77L);

        assertThat(summary).isEqualTo(
                new QuestionExplanationPreparationService.PreparationSummary(1, 1, 0, 0));
        verify(bindingRepository).bindIfAbsent(102L, 502L, "vi", "b".repeat(64));
        verify(taskRepository, never()).insertPendingIfAbsent(anyLong(), anyLong(), anyInt());
    }

    @Test
    void existingQuestionVersionBindingIsNeverRecomputedOrRebound() {
        fixture(103L);
        QuestionVersionExplanationBinding binding = binding(103L, 503L);
        QuestionExplanationArtifact artifact = artifact(503L, QuestionExplanationArtifact.STATUS_READY);
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(103L, "vi"))
                .thenReturn(Optional.of(binding));
        when(artifactRepository.findById(503L)).thenReturn(Optional.of(artifact));

        QuestionExplanationPreparationService.PreparationSummary summary =
                service.preparePublishedVersion(77L);

        assertThat(summary).isEqualTo(
                new QuestionExplanationPreparationService.PreparationSummary(1, 1, 0, 0));
        verifyNoInteractions(inputFactory);
        verify(bindingRepository, never()).bindIfAbsent(anyLong(), anyLong(), anyString(), anyString());
        verify(artifactRepository, never()).insertPendingIfAbsent(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(taskRepository, never()).insertPendingIfAbsent(anyLong(), anyLong(), anyInt());
    }

    @Test
    void existingPendingBindingRecreatesMissingGenerationTaskAfterCrashGap() {
        Fixture fixture = fixture(106L);
        String fingerprint = "e".repeat(64);
        QuestionVersionExplanationBinding binding = binding(106L, 507L);
        when(binding.getFingerprint()).thenReturn(fingerprint);
        QuestionExplanationArtifact artifact = artifact(
                507L, QuestionExplanationArtifact.STATUS_PENDING);
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(106L, "vi"))
                .thenReturn(Optional.of(binding));
        when(artifactRepository.findById(507L)).thenReturn(Optional.of(artifact));
        when(inputFactory.prepare(fixture.question(), null, fixture.section()))
                .thenReturn(prepared(fingerprint, null));
        when(taskRepository.insertPendingIfAbsent(507L, 106L, 4)).thenReturn(1);

        QuestionExplanationPreparationService.PreparationSummary summary =
                service.preparePublishedVersion(77L);

        assertThat(summary).isEqualTo(
                new QuestionExplanationPreparationService.PreparationSummary(1, 0, 1, 0));
        verify(taskRepository).insertPendingIfAbsent(507L, 106L, 4);
        verify(bindingRepository, never()).bindIfAbsent(anyLong(), anyLong(), anyString(), anyString());
        verify(artifactRepository, never()).insertPendingIfAbsent(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void concurrentBindingWinnerIsPreservedAndItsReadyArtifactIsReused() {
        Fixture fixture = fixture(104L);
        QuestionExplanationArtifact computed = artifact(504L, QuestionExplanationArtifact.STATUS_PENDING);
        QuestionExplanationArtifact winner = artifact(505L, QuestionExplanationArtifact.STATUS_READY);
        QuestionVersionExplanationBinding binding = binding(104L, 505L);
        when(inputFactory.prepare(fixture.question(), null, fixture.section()))
                .thenReturn(prepared("c".repeat(64), null));
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(104L, "vi"))
                .thenReturn(Optional.empty(), Optional.of(binding));
        when(artifactRepository.findByFingerprint("c".repeat(64)))
                .thenReturn(Optional.of(computed));
        when(artifactRepository.findById(505L)).thenReturn(Optional.of(winner));

        QuestionExplanationPreparationService.PreparationSummary summary =
                service.preparePublishedVersion(77L);

        assertThat(summary).isEqualTo(
                new QuestionExplanationPreparationService.PreparationSummary(1, 1, 0, 0));
        verify(taskRepository, never()).insertPendingIfAbsent(anyLong(), anyLong(), anyInt());
    }

    @Test
    void unavailablePublishedEvidenceFailsArtifactWithoutQueueingProviderWork() {
        Fixture fixture = fixture(105L);
        QuestionExplanationArtifact artifact = pendingArtifact(506L);
        QuestionVersionExplanationBinding binding = binding(105L, 506L);
        when(inputFactory.prepare(fixture.question(), null, fixture.section()))
                .thenReturn(prepared(
                        "d".repeat(64), ExplanationInputFactory.ISSUE_EVIDENCE_UNAVAILABLE));
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(105L, "vi"))
                .thenReturn(Optional.empty(), Optional.of(binding));
        when(artifactRepository.findByFingerprint("d".repeat(64)))
                .thenReturn(Optional.of(artifact));
        when(artifactRepository.findByIdForUpdate(506L)).thenReturn(Optional.of(artifact));

        QuestionExplanationPreparationService.PreparationSummary summary =
                service.preparePublishedVersion(77L);

        assertThat(summary).isEqualTo(
                new QuestionExplanationPreparationService.PreparationSummary(1, 0, 0, 1));
        assertThat(artifact.getStatus()).isEqualTo(QuestionExplanationArtifact.STATUS_FAILED);
        assertThat(artifact.getErrorCategory())
                .isEqualTo(ExplanationInputFactory.ISSUE_EVIDENCE_UNAVAILABLE);
        verify(artifactRepository).save(artifact);
        verify(taskRepository, never()).insertPendingIfAbsent(anyLong(), anyLong(), anyInt());
    }

    private Fixture fixture(Long questionVersionId) {
        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getId()).thenReturn(11L);
        when(section.getSkill()).thenReturn("READING");
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(questionVersionId);
        when(question.getSectionVersionId()).thenReturn(11L);
        when(question.getGroupVersionId()).thenReturn(null);
        when(sectionRepository.findByPublishedVersionIdOrderByTestVersionIdAscDisplayOrderAscIdAsc(77L))
                .thenReturn(List.of(section));
        when(groupRepository.findByPublishedVersionIdOrderBySectionVersionIdAscDisplayOrderAscIdAsc(77L))
                .thenReturn(List.of());
        when(questionRepository.findByPublishedVersionIdOrderBySectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(77L))
                .thenReturn(List.of(question));
        return new Fixture(question, section);
    }

    private static PreparedExplanation prepared(String fingerprint, String readinessIssue) {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(new QuestionContent.Option("opt_1", "Answer")),
                List.of());
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("opt_1"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        ExplanationArtifactInput input = new ExplanationArtifactInput(
                ExplanationArtifactInput.SCHEMA_VERSION,
                AssessmentSkill.READING,
                CanonicalQuestionType.SINGLE_CHOICE,
                "Prompt",
                "Instruction",
                content,
                spec,
                AssessmentStimulus.readingPassage("Passage", "PUBLISHED_SNAPSHOT"),
                "Teacher explanation",
                "NUMERIC",
                "vi",
                List.of(),
                readinessIssue);
        ExplanationFingerprint hashes = new ExplanationFingerprint(
                fingerprint,
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                "assessment-v1",
                "model",
                "prompt-v1",
                "response-v1",
                "vi",
                "{}");
        return new PreparedExplanation(input, hashes, null, List.of());
    }

    private static QuestionExplanationArtifact artifact(Long id, String status) {
        QuestionExplanationArtifact artifact = mock(QuestionExplanationArtifact.class);
        when(artifact.getId()).thenReturn(id);
        when(artifact.getStatus()).thenReturn(status);
        return artifact;
    }

    private static QuestionExplanationArtifact pendingArtifact(Long id) {
        QuestionExplanationArtifact artifact = instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", id);
        ReflectionTestUtils.setField(artifact, "status", QuestionExplanationArtifact.STATUS_PENDING);
        return artifact;
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

    private static QuestionVersionExplanationBinding binding(Long questionVersionId, Long artifactId) {
        QuestionVersionExplanationBinding binding = mock(QuestionVersionExplanationBinding.class);
        when(binding.getQuestionVersionId()).thenReturn(questionVersionId);
        when(binding.getArtifactId()).thenReturn(artifactId);
        return binding;
    }

    private record Fixture(PracticeQuestionVersion question, PracticeSectionVersion section) {
    }
}
