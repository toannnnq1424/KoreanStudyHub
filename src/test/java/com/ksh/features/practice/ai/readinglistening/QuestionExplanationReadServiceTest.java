package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionExplanationReadServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuestionVersionExplanationBindingRepository bindingRepository;
    private QuestionExplanationArtifactRepository artifactRepository;
    private PracticeQuestionVersionRepository questionRepository;
    private AssessmentContractCodec contractCodec;
    private QuestionTypeResolver typeResolver;
    private QuestionExplanationReadService service;

    @BeforeEach
    void setUp() {
        bindingRepository = mock(QuestionVersionExplanationBindingRepository.class);
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        questionRepository = mock(PracticeQuestionVersionRepository.class);
        contractCodec = mock(AssessmentContractCodec.class);
        typeResolver = mock(QuestionTypeResolver.class);
        service = new QuestionExplanationReadService(
                bindingRepository,
                artifactRepository,
                questionRepository,
                contractCodec,
                typeResolver,
                objectMapper);
    }

    @Test
    void oldAndNewQuestionVersionsReadTheirOwnImmutableExplanationBindings() {
        bindReady(101L, 201L, validExplanation("old snapshot"));
        bindReady(102L, 202L, validExplanation("new snapshot"));

        assertThat(service.readReadyJson(101L)).hasValueSatisfying(
                value -> assertThat(value).contains("old snapshot"));
        assertThat(service.readReadyJson(102L)).hasValueSatisfying(
                value -> assertThat(value).contains("new snapshot"));
    }

    @Test
    void availabilityDistinguishesMissingPendingPartialAndFailedArtifacts() {
        assertThat(service.availability(List.of(100L)).state()).isEqualTo("UNAVAILABLE");

        QuestionVersionExplanationBinding pendingBinding = binding(101L, 201L);
        QuestionExplanationArtifact pending = artifact(
                201L, QuestionExplanationArtifact.STATUS_PENDING, null);
        when(bindingRepository.findByQuestionVersionIdInAndExplanationLanguage(List.of(101L), "vi"))
                .thenReturn(List.of(pendingBinding));
        when(artifactRepository.findAllById(List.of(201L))).thenReturn(List.of(pending));
        assertThat(service.availability(List.of(101L)).state()).isEqualTo("PENDING");

        QuestionVersionExplanationBinding readyBinding = binding(102L, 202L);
        QuestionExplanationArtifact ready = artifact(
                202L, QuestionExplanationArtifact.STATUS_READY, validExplanation("ready"));
        when(bindingRepository.findByQuestionVersionIdInAndExplanationLanguage(
                List.of(102L, 103L), "vi"))
                .thenReturn(List.of(readyBinding));
        when(artifactRepository.findAllById(List.of(202L))).thenReturn(List.of(ready));
        ResultFeedbackAvailability partial = service.availability(List.of(102L, 103L));
        assertThat(partial.state()).isEqualTo("PARTIAL");
        assertThat(partial.readyCount()).isEqualTo(1);

        QuestionVersionExplanationBinding failedBinding = binding(104L, 204L);
        QuestionExplanationArtifact failed = artifact(
                204L, QuestionExplanationArtifact.STATUS_FAILED, null);
        when(bindingRepository.findByQuestionVersionIdInAndExplanationLanguage(List.of(104L), "vi"))
                .thenReturn(List.of(failedBinding));
        when(artifactRepository.findAllById(List.of(204L))).thenReturn(List.of(failed));
        assertThat(service.availability(List.of(104L)).state()).isEqualTo("FAILED");

        QuestionVersionExplanationBinding malformedBinding = binding(105L, 205L);
        QuestionExplanationArtifact malformed = artifact(
                205L, QuestionExplanationArtifact.STATUS_READY, "not-json");
        when(bindingRepository.findByQuestionVersionIdInAndExplanationLanguage(List.of(105L), "vi"))
                .thenReturn(List.of(malformedBinding));
        when(artifactRepository.findAllById(List.of(205L))).thenReturn(List.of(malformed));
        assertThat(service.availability(List.of(105L)).state()).isEqualTo("UNAVAILABLE");

        QuestionVersionExplanationBinding wrongShapeBinding = binding(106L, 206L);
        QuestionExplanationArtifact wrongShape = artifact(
                206L, QuestionExplanationArtifact.STATUS_READY, "{\"foo\":\"bar\"}");
        when(bindingRepository.findByQuestionVersionIdInAndExplanationLanguage(List.of(106L), "vi"))
                .thenReturn(List.of(wrongShapeBinding));
        when(artifactRepository.findAllById(List.of(206L))).thenReturn(List.of(wrongShape));
        assertThat(service.availability(List.of(106L)).state()).isEqualTo("UNAVAILABLE");

        QuestionVersionExplanationBinding malformedOptionBinding = binding(107L, 207L);
        QuestionExplanationArtifact malformedOption = artifact(
                207L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {"meaningVi":"Meaning","evidenceQuote":"본문","correctReasonVi":"Reason",
                 "relatedTranslationVi":"Translation","eliminatedOptions":[{"optionKey":7}]}
                """);
        when(bindingRepository.findByQuestionVersionIdInAndExplanationLanguage(List.of(107L), "vi"))
                .thenReturn(List.of(malformedOptionBinding));
        when(artifactRepository.findAllById(List.of(207L))).thenReturn(List.of(malformedOption));
        assertThat(service.availability(List.of(107L)).state()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void displayMappingUsesStableOptionIdsButReturnsAttemptSpecificLabels() throws Exception {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        when(question.getId()).thenReturn(101L);
        when(question.getQuestionType()).thenReturn("SINGLE_CHOICE");
        when(question.getQuestionContentJson()).thenReturn("typed-content");
        when(question.getAnswerSpecJson()).thenReturn("typed-answer");
        when(questionRepository.findById(101L)).thenReturn(Optional.of(question));
        when(typeResolver.resolve("SINGLE_CHOICE")).thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("current_random_a", "First"),
                        new QuestionContent.Option("current_random_b", "Second")),
                List.of());
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("current_random_a"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        when(contractCodec.readQuestionContent("typed-content", CanonicalQuestionType.SINGLE_CHOICE))
                .thenReturn(content);
        when(contractCodec.readAnswerSpec("typed-answer", content)).thenReturn(spec);
        bindReady(101L, 201L, """
                {"meaningVi":"Meaning","evidenceQuote":"본문","correctReasonVi":"Correct",
                 "relatedTranslationVi":"Bản dịch","eliminatedOptions":[
                  {"optionKey":"option_2","reasonVi":"Wrong"},
                  {"optionKey":"unknown","reasonVi":"Ignore"}
                ]}
                """);

        String displayJson = service.readDisplayJson(101L, "ALPHA").orElseThrow();
        JsonNode display = objectMapper.readTree(displayJson);

        assertThat(display.path("correctAnswer").asText()).isEqualTo("A");
        assertThat(display.path("eliminatedOptions").size()).isEqualTo(1);
        assertThat(display.path("eliminatedOptions").path(0).path("optionKey").asText())
                .isEqualTo("B");
    }

    private void bindReady(Long questionVersionId, Long artifactId, String explanationJson) {
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(questionVersionId, "vi"))
                .thenReturn(Optional.of(binding(questionVersionId, artifactId)));
        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact(
                artifactId, QuestionExplanationArtifact.STATUS_READY, explanationJson)));
    }

    private static String validExplanation(String meaning) {
        return """
                {"meaningVi":"%s","evidenceQuote":"본문","correctReasonVi":"Lý do đúng",
                 "relatedTranslationVi":"","eliminatedOptions":[]}
                """.formatted(meaning);
    }

    private static QuestionVersionExplanationBinding binding(Long questionVersionId, Long artifactId) {
        QuestionVersionExplanationBinding binding = instantiate(QuestionVersionExplanationBinding.class);
        ReflectionTestUtils.setField(binding, "questionVersionId", questionVersionId);
        ReflectionTestUtils.setField(binding, "artifactId", artifactId);
        ReflectionTestUtils.setField(binding, "explanationLanguage", "vi");
        return binding;
    }

    private static QuestionExplanationArtifact artifact(
            Long id,
            String status,
            String explanationJson) {
        QuestionExplanationArtifact artifact = instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", id);
        ReflectionTestUtils.setField(artifact, "status", status);
        ReflectionTestUtils.setField(artifact, "explanationJson", explanationJson);
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
}
