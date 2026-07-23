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
        when(typeResolver.resolve("SINGLE_CHOICE"))
                .thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
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

    @Test
    void objectiveReadAdaptsOnlyValidV2SingleChoiceWithExactTextEvidence() {
        String explanation = """
                {"meaningVi":"Nghĩa","evidenceQuote":"본문","evidenceKind":"TEXT",
                 "correctReasonVi":"Đúng vì nguồn nêu rõ","relatedTranslationVi":"Đoạn chính",
                 "eliminatedOptions":[{"optionKey":"option_2","reasonVi":"Sai với nguồn"}]}
                """;
        QuestionExplanationArtifact ready = artifact(
                211L, QuestionExplanationArtifact.STATUS_READY, explanation);
        ReflectionTestUtils.setField(ready, "inputContractJson", singleChoiceInput());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(111L, "vi"))
                .thenReturn(Optional.of(binding(111L, 211L)));
        when(artifactRepository.findById(211L)).thenReturn(Optional.of(ready));

        QuestionExplanationReadService.ObjectiveExplanationArtifact result =
                service.readObjective(111L, CanonicalQuestionType.SINGLE_CHOICE)
                        .orElseThrow();

        assertThat(result.schemaVersion()).isEqualTo("v2");
        assertThat(result.questionType()).isEqualTo(CanonicalQuestionType.SINGLE_CHOICE);
        assertThat(result.evidence()).singleElement().isInstanceOf(
                QuestionExplanationReadService.TextEvidence.class);
        assertThat(result.relevantTranslations()).isEmpty();
        QuestionExplanationReadService.SingleChoiceExplanation typed =
                (QuestionExplanationReadService.SingleChoiceExplanation)
                        result.typeExplanation();
        assertThat(typed.optionRationales()).extracting(
                QuestionExplanationReadService.OptionRationale::optionId)
                .containsExactly("option_1", "option_2");
    }

    @Test
    void objectiveReadRejectsCrossTypeV3AndV2FillBlankInsteadOfCoercingShape() {
        QuestionExplanationArtifact crossType = artifact(
                212L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "textEvidenceRefs":[],"imageEvidenceRefs":[],"relevantTranslations":[],
                  "blankExplanations":[]}}
                """);
        ReflectionTestUtils.setField(crossType, "responseSchemaVersion", "v3");
        ReflectionTestUtils.setField(crossType, "inputContractJson", singleChoiceInput());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(112L, "vi"))
                .thenReturn(Optional.of(binding(112L, 212L)));
        when(artifactRepository.findById(212L)).thenReturn(Optional.of(crossType));

        assertThat(service.readObjective(
                112L, CanonicalQuestionType.SINGLE_CHOICE)).isEmpty();

        QuestionExplanationArtifact v2Fill = artifact(
                213L,
                QuestionExplanationArtifact.STATUS_READY,
                validExplanation("fill"));
        ReflectionTestUtils.setField(v2Fill, "questionType", "FILL_BLANK");
        ReflectionTestUtils.setField(v2Fill, "inputContractJson", """
                {"schemaVersion":"rl-explanation-input-v2","questionType":"FILL_BLANK",
                 "questionContent":{"blanks":[{"id":"blank_1"}]},
                 "answerSpec":{"blanks":[{"blankId":"blank_1","acceptedValues":["값"]}]},
                 "stimulus":{"passageText":"본문","transcriptText":null,"approved":true},
                 "media":[]}
                """);
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(113L, "vi"))
                .thenReturn(Optional.of(binding(113L, 213L)));
        when(artifactRepository.findById(213L)).thenReturn(Optional.of(v2Fill));

        assertThat(service.readObjective(
                113L, CanonicalQuestionType.FILL_BLANK)).isEmpty();
    }

    @Test
    void objectiveReadRejectsNonExactTextAndImageWithoutAuthoritativeRegion() {
        QuestionExplanationArtifact nonExact = artifact(
                214L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "optionRationales":[
                    {"optionId":"option_1","reasonVi":"Đúng","evidenceIds":["e1"]},
                    {"optionId":"option_2","reasonVi":"Sai","evidenceIds":["e1"]}],
                  "textEvidenceRefs":[{"evidenceId":"e1","kind":"TEXT_SPAN",
                    "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                    "exactQuoteKo":"본문","startOffset":1,"endOffset":3}],
                  "imageEvidenceRefs":[],"relevantTranslations":[]}}
                """);
        ReflectionTestUtils.setField(nonExact, "responseSchemaVersion", "v3");
        ReflectionTestUtils.setField(nonExact, "inputContractJson", singleChoiceInput());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(114L, "vi"))
                .thenReturn(Optional.of(binding(114L, 214L)));
        when(artifactRepository.findById(214L)).thenReturn(Optional.of(nonExact));
        assertThat(service.readObjective(
                114L, CanonicalQuestionType.SINGLE_CHOICE)).isEmpty();

        QuestionExplanationArtifact imageWithoutRegion = artifact(
                215L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "optionRationales":[
                    {"optionId":"option_1","reasonVi":"Đúng","evidenceIds":["img"]},
                    {"optionId":"option_2","reasonVi":"Sai","evidenceIds":["img"]}],
                  "textEvidenceRefs":[],
                  "imageEvidenceRefs":[{"evidenceId":"img","kind":"IMAGE_REGION",
                    "purpose":"ANSWER_RATIONALE","sourceRole":"question.image",
                    "assetDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "imageIndex":0,"regionMode":"RECTANGLE",
                    "x":null,"y":null,"width":null,"height":null}],
                  "relevantTranslations":[]}}
                """);
        ReflectionTestUtils.setField(imageWithoutRegion, "responseSchemaVersion", "v3");
        ReflectionTestUtils.setField(
                imageWithoutRegion, "inputContractJson", singleChoiceImageInput());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(115L, "vi"))
                .thenReturn(Optional.of(binding(115L, 215L)));
        when(artifactRepository.findById(215L)).thenReturn(Optional.of(imageWithoutRegion));
        assertThat(service.readObjective(
                115L, CanonicalQuestionType.SINGLE_CHOICE)).isEmpty();
    }

    @Test
    void objectiveReadAcceptsExactV3TextSpanAndEvidenceBoundTranslation() {
        QuestionExplanationArtifact exact = artifact(
                216L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {"schemaVersion":"v3","questionType":"SINGLE_CHOICE","explanation":{
                  "meaningVi":"Nghĩa","correctReasonVi":"Lý do",
                  "optionRationales":[
                    {"optionId":"option_1","reasonVi":"Đúng","evidenceIds":["e1"]},
                    {"optionId":"option_2","reasonVi":"Sai","evidenceIds":["e1"]}],
                  "textEvidenceRefs":[{"evidenceId":"e1","kind":"TEXT_SPAN",
                    "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                    "exactQuoteKo":"본문","startOffset":0,"endOffset":2}],
                  "imageEvidenceRefs":[],
                  "relevantTranslations":[
                    {"evidenceId":"e1","translationVi":"Đoạn văn chính"}]}}
                """);
        ReflectionTestUtils.setField(exact, "responseSchemaVersion", "v3");
        ReflectionTestUtils.setField(exact, "inputContractJson", singleChoiceInput());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(116L, "vi"))
                .thenReturn(Optional.of(binding(116L, 216L)));
        when(artifactRepository.findById(216L)).thenReturn(Optional.of(exact));

        QuestionExplanationReadService.ObjectiveExplanationArtifact result =
                service.readObjective(116L, CanonicalQuestionType.SINGLE_CHOICE)
                        .orElseThrow();

        assertThat(result.evidence()).singleElement().satisfies(value -> {
            QuestionExplanationReadService.TextEvidence text =
                    (QuestionExplanationReadService.TextEvidence) value;
            assertThat(text.exactQuoteKo()).isEqualTo("본문");
            assertThat(text.startOffset()).isZero();
            assertThat(text.endOffset()).isEqualTo(2);
        });
        assertThat(result.relevantTranslations()).singleElement().satisfies(translation -> {
            assertThat(translation.evidenceId()).isEqualTo("e1");
            assertThat(translation.translationVi()).isEqualTo("Đoạn văn chính");
        });
    }

    @Test
    void objectiveReadRejectsArtifactWhenImmutableBindingFingerprintDoesNotMatch() {
        QuestionExplanationArtifact ready = artifact(
                217L,
                QuestionExplanationArtifact.STATUS_READY,
                validExplanation("wrong binding"));
        ReflectionTestUtils.setField(
                ready,
                "fingerprint",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(117L, "vi"))
                .thenReturn(Optional.of(binding(117L, 217L)));
        when(artifactRepository.findById(217L)).thenReturn(Optional.of(ready));

        assertThat(service.readObjective(
                117L, CanonicalQuestionType.SINGLE_CHOICE)).isEmpty();
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
        ReflectionTestUtils.setField(
                binding,
                "fingerprint",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        return binding;
    }

    private static QuestionExplanationArtifact artifact(
            Long id,
            String status,
            String explanationJson) {
        QuestionExplanationArtifact artifact = instantiate(QuestionExplanationArtifact.class);
        ReflectionTestUtils.setField(artifact, "id", id);
        ReflectionTestUtils.setField(
                artifact,
                "fingerprint",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        ReflectionTestUtils.setField(artifact, "questionType", "SINGLE_CHOICE");
        ReflectionTestUtils.setField(artifact, "responseSchemaVersion", "v2");
        ReflectionTestUtils.setField(artifact, "inputContractJson", minimalSingleChoiceInput());
        ReflectionTestUtils.setField(artifact, "status", status);
        ReflectionTestUtils.setField(artifact, "explanationJson", explanationJson);
        return artifact;
    }

    private static String minimalSingleChoiceInput() {
        return """
                {"schemaVersion":"rl-explanation-input-v2","questionType":"SINGLE_CHOICE",
                 "questionContent":{"options":[{"id":"option_1"}]},
                 "answerSpec":{"correctOptionIds":["option_1"]},
                 "stimulus":{"passageText":"본문","transcriptText":null,"approved":true},
                 "media":[]}
                """;
    }

    private static String singleChoiceInput() {
        return """
                {"schemaVersion":"rl-explanation-input-v2","questionType":"SINGLE_CHOICE",
                 "questionContent":{"options":[{"id":"option_1"},{"id":"option_2"}]},
                 "answerSpec":{"correctOptionIds":["option_1"]},
                 "stimulus":{"passageText":"본문 근거","transcriptText":null,"approved":true},
                 "media":[]}
                """;
    }

    private static String singleChoiceImageInput() {
        return """
                {"schemaVersion":"rl-explanation-input-v2","questionType":"SINGLE_CHOICE",
                 "questionContent":{"options":[{"id":"option_1"},{"id":"option_2"}]},
                 "answerSpec":{"correctOptionIds":["option_1"]},
                 "stimulus":{"passageText":"","transcriptText":null,"approved":true},
                 "media":[{"role":"question.image","kind":"IMAGE",
                   "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}
                """;
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
