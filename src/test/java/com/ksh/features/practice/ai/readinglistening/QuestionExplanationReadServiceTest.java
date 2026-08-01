package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.dto.PracticeDtos.ResultFeedbackAvailability;
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
    private QuestionTypeResolver typeResolver;
    private QuestionExplanationReadService service;

    @BeforeEach
    void setUp() {
        bindingRepository = mock(QuestionVersionExplanationBindingRepository.class);
        artifactRepository = mock(QuestionExplanationArtifactRepository.class);
        typeResolver = mock(QuestionTypeResolver.class);
        service = new QuestionExplanationReadService(
                bindingRepository,
                artifactRepository,
                typeResolver,
                objectMapper);
        when(typeResolver.resolve("SINGLE_CHOICE"))
                .thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
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
        ReflectionTestUtils.setField(
                exact, "inputContractJson", singleChoiceV3Input());
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
    void objectiveReadAcceptsOnlyStrategyMatchedEvidenceLinkedV4Claims() {
        QuestionExplanationArtifact exact = artifact(
                218L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v1",
                  "strategyCode":"EVIDENCE_ONLY",
                  "strategyVersion":"v1",
                  "questionType":"SINGLE_CHOICE",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"e1","kind":"TEXT_SPAN",
                      "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                      "exactQuoteKo":"본문","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[{
                      "evidenceId":"e1","translationVi":"Đoạn chính"
                    }],
                    "strategyBlock":{"evidenceClaims":[{
                      "claimId":"claim-1",
                      "textVi":"Nguồn xác nhận đáp án.",
                      "evidenceIds":["e1"]
                    }]}
                  }
                }
                """);
        ReflectionTestUtils.setField(exact, "responseSchemaVersion", "v4");
        ReflectionTestUtils.setField(
                exact, "inputContractJson", singleChoiceV3Input());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(
                118L, "vi"))
                .thenReturn(Optional.of(binding(118L, 218L)));
        when(artifactRepository.findById(218L))
                .thenReturn(Optional.of(exact));

        QuestionExplanationReadService.ObjectiveExplanationArtifact result =
                service.readObjective(
                        118L, CanonicalQuestionType.SINGLE_CHOICE)
                        .orElseThrow();

        assertThat(result.schemaVersion()).isEqualTo("v4");
        assertThat(result.strategyCode()).isEqualTo("EVIDENCE_ONLY");
        assertThat(result.claims()).singleElement().satisfies(claim -> {
            assertThat(claim.claimId()).isEqualTo("claim-1");
            assertThat(claim.evidenceIds()).containsExactly("e1");
        });
    }

    @Test
    void objectiveReadKeepsCurrentGenericStrategyWithTypeNativeV4Shape() {
        QuestionExplanationArtifact fill = artifact(
                221L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v2",
                  "strategyCode":"KEYWORD_PARAPHRASE_BRIDGE",
                  "strategyVersion":"v1",
                  "questionType":"FILL_BLANK",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"fill-evidence","kind":"TEXT_SPAN",
                      "purpose":"BLANK_CONSTRAINT","sourceRole":"PASSAGE",
                      "exactQuoteKo":"본문","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[],
                    "strategyBlock":{"blankExplanations":[{
                      "claimId":"fill-claim","blankId":"blank_1",
                      "contextExplanationVi":"Nguồn khóa vị trí cần điền.",
                      "semanticConstraintVi":"Danh từ",
                      "grammarConstraintVi":"Vị trí danh từ",
                      "registerConstraintVi":"Trung tính",
                      "evidenceIds":["fill-evidence"]
                    }]}
                  }
                }
                """);
        ReflectionTestUtils.setField(
                fill, "questionType", "FILL_BLANK");
        ReflectionTestUtils.setField(fill, "responseSchemaVersion", "v4");
        ReflectionTestUtils.setField(
                fill, "inputContractJson", currentFillBlankV3Input());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(
                121L, "vi"))
                .thenReturn(Optional.of(binding(121L, 221L)));
        when(artifactRepository.findById(221L))
                .thenReturn(Optional.of(fill));

        QuestionExplanationReadService.ObjectiveExplanationArtifact
                fillResult = service.readObjective(
                        121L, CanonicalQuestionType.FILL_BLANK)
                .orElseThrow();

        assertThat(fillResult.strategyCode())
                .isEqualTo("KEYWORD_PARAPHRASE_BRIDGE");
        assertThat(fillResult.typeExplanation())
                .isInstanceOf(
                        QuestionExplanationReadService
                                .FillBlankExplanation.class);

        QuestionExplanationArtifact tfng = artifact(
                222L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v2",
                  "strategyCode":"FULL_SOURCE_INLINE_HIGHLIGHT",
                  "strategyVersion":"v1",
                  "questionType":"TRUE_FALSE_NOT_GIVEN",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"tfng-evidence","kind":"TEXT_SPAN",
                      "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                      "exactQuoteKo":"본문","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[],
                    "strategyBlock":{
                      "claim":{"claimId":"claim","textVi":"Đối chiếu mệnh đề.","evidenceIds":["tfng-evidence"]},
                      "whyTrue":{"claimId":"true","textVi":"Nguồn xác nhận.","evidenceIds":["tfng-evidence"]},
                      "whyFalse":{"claimId":"false","textVi":"Nguồn không phủ định.","evidenceIds":["tfng-evidence"]},
                      "whyNotGiven":{"claimId":"ng","textVi":"Nguồn có thông tin.","evidenceIds":["tfng-evidence"]},
                      "missingInformation":{"claimId":"missing","textVi":"Không thiếu dữ kiện.","evidenceIds":["tfng-evidence"]}
                    }
                  }
                }
                """);
        ReflectionTestUtils.setField(
                tfng, "questionType", "TRUE_FALSE_NOT_GIVEN");
        ReflectionTestUtils.setField(tfng, "responseSchemaVersion", "v4");
        ReflectionTestUtils.setField(
                tfng, "inputContractJson", currentTfngV3Input());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(
                122L, "vi"))
                .thenReturn(Optional.of(binding(122L, 222L)));
        when(artifactRepository.findById(222L))
                .thenReturn(Optional.of(tfng));

        QuestionExplanationReadService.ObjectiveExplanationArtifact
                tfngResult = service.readObjective(
                        122L, CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN)
                .orElseThrow();

        assertThat(tfngResult.strategyCode())
                .isEqualTo("FULL_SOURCE_INLINE_HIGHLIGHT");
        assertThat(tfngResult.typeExplanation())
                .isInstanceOf(
                        QuestionExplanationReadService.TfngExplanation.class);
    }

    @Test
    void objectiveReadAcceptsGroundedMultipleAnswerAndMatchingV4Artifacts() {
        QuestionExplanationArtifact multiple = artifact(
                223L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v2",
                  "strategyCode":"EVIDENCE_AND_ELIMINATION",
                  "strategyVersion":"v1",
                  "questionType":"MULTIPLE_ANSWER",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"multi-evidence","kind":"TEXT_SPAN",
                      "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                      "exactQuoteKo":"본문","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[{
                      "evidenceId":"multi-evidence","translationVi":"Đoạn chính"
                    }],
                    "strategyBlock":{
                      "contextClaims":[{
                        "claimId":"multi-context","textVi":"Đối chiếu nguồn.",
                        "evidenceIds":["multi-evidence"]
                      }],
                      "answerClaim":{
                        "claimId":"multi-answer","textVi":"A và C đúng.",
                        "evidenceIds":["multi-evidence"]
                      },
                      "optionRationales":[
                        {"claimId":"multi-a","optionId":"option_1","reasonVi":"A được nguồn xác nhận.","evidenceIds":["multi-evidence"]},
                        {"claimId":"multi-b","optionId":"option_2","reasonVi":"B trái nguồn.","evidenceIds":["multi-evidence"]},
                        {"claimId":"multi-c","optionId":"option_3","reasonVi":"C được nguồn xác nhận.","evidenceIds":["multi-evidence"]}
                      ]
                    }
                  }
                }
                """);
        ReflectionTestUtils.setField(multiple, "questionType", "MULTIPLE_ANSWER");
        ReflectionTestUtils.setField(multiple, "responseSchemaVersion", "v4");
        ReflectionTestUtils.setField(multiple, "inputContractJson", multipleAnswerV3Input());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(123L, "vi"))
                .thenReturn(Optional.of(binding(123L, 223L)));
        when(artifactRepository.findById(223L)).thenReturn(Optional.of(multiple));

        QuestionExplanationReadService.ObjectiveExplanationArtifact multipleResult =
                service.readObjective(123L, CanonicalQuestionType.MULTIPLE_ANSWER)
                        .orElseThrow();
        assertThat(multipleResult.typeExplanation())
                .isInstanceOf(QuestionExplanationReadService.MultipleAnswerExplanation.class);
        QuestionExplanationReadService.MultipleAnswerExplanation multipleTyped =
                (QuestionExplanationReadService.MultipleAnswerExplanation)
                        multipleResult.typeExplanation();
        assertThat(multipleTyped.correctOptionIds())
                .containsExactly("option_1", "option_3");
        assertThat(multipleTyped.optionRationales()).hasSize(3);

        QuestionExplanationArtifact matching = artifact(
                224L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v2",
                  "strategyCode":"MATCHING_MATRIX",
                  "strategyVersion":"v1",
                  "questionType":"MATCHING",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"match-evidence","kind":"TEXT_SPAN",
                      "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                      "exactQuoteKo":"본문","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[],
                    "strategyBlock":{"targetExplanations":[
                      {"claimId":"match-1","targetId":"blank_1","candidateOptionId":"option_1","reasonVi":"Thủ đô là Seoul.","evidenceIds":["match-evidence"]},
                      {"claimId":"match-2","targetId":"blank_2","candidateOptionId":"option_3","reasonVi":"Hòn đảo là Jeju.","evidenceIds":["match-evidence"]}
                    ]}
                  }
                }
                """);
        ReflectionTestUtils.setField(matching, "questionType", "MATCHING");
        ReflectionTestUtils.setField(matching, "responseSchemaVersion", "v4");
        ReflectionTestUtils.setField(matching, "inputContractJson", matchingV3Input());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(124L, "vi"))
                .thenReturn(Optional.of(binding(124L, 224L)));
        when(artifactRepository.findById(224L)).thenReturn(Optional.of(matching));

        QuestionExplanationReadService.ObjectiveExplanationArtifact matchingResult =
                service.readObjective(124L, CanonicalQuestionType.MATCHING)
                        .orElseThrow();
        assertThat(matchingResult.typeExplanation())
                .isInstanceOf(QuestionExplanationReadService.MatchingExplanation.class);
        QuestionExplanationReadService.MatchingExplanation matchingTyped =
                (QuestionExplanationReadService.MatchingExplanation)
                        matchingResult.typeExplanation();
        assertThat(matchingTyped.targets())
                .extracting(QuestionExplanationReadService.MatchingRationale::candidateOptionId)
                .containsExactly("option_1", "option_3");
    }

    @Test
    void objectiveReadAcceptsExactStandalonePromptEvidenceAndRejectsRoleDrift() {
        String explanation = """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v1",
                  "strategyCode":"EVIDENCE_ONLY",
                  "strategyVersion":"v1",
                  "questionType":"SINGLE_CHOICE",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"e1","kind":"TEXT_SPAN",
                      "purpose":"ANSWER_RATIONALE",
                      "sourceRole":"QUESTION_PROMPT",
                      "exactQuoteKo":"정답","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[],
                    "strategyBlock":{"evidenceClaims":[{
                      "claimId":"claim-standalone",
                      "textVi":"Đề bài xác định đáp án.",
                      "evidenceIds":["e1"]
                    }]}
                  }
                }
                """;
        QuestionExplanationArtifact exact = artifact(
                220L,
                QuestionExplanationArtifact.STATUS_READY,
                explanation);
        ReflectionTestUtils.setField(exact, "responseSchemaVersion", "v4");
        ReflectionTestUtils.setField(
                exact, "inputContractJson", standaloneSingleChoiceV3Input());
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(
                120L, "vi"))
                .thenReturn(Optional.of(binding(120L, 220L)));
        when(artifactRepository.findById(220L))
                .thenReturn(Optional.of(exact));

        QuestionExplanationReadService.ObjectiveExplanationArtifact result =
                service.readObjective(
                        120L, CanonicalQuestionType.SINGLE_CHOICE)
                        .orElseThrow();

        assertThat(result.evidence()).singleElement().satisfies(value -> {
            QuestionExplanationReadService.TextEvidence text =
                    (QuestionExplanationReadService.TextEvidence) value;
            assertThat(text.sourceRole()).isEqualTo("QUESTION_PROMPT");
            assertThat(text.exactQuoteKo()).isEqualTo("정답");
        });

        ReflectionTestUtils.setField(
                exact,
                "explanationJson",
                explanation.replace(
                        "\"sourceRole\":\"QUESTION_PROMPT\"",
                        "\"sourceRole\":\"PASSAGE\""));
        assertThat(service.readObjective(
                120L, CanonicalQuestionType.SINGLE_CHOICE)).isEmpty();
    }

    @Test
    void v2ReaderRejectsRepeatedQuoteInsteadOfGuessingOccurrence() {
        QuestionExplanationArtifact repeated = artifact(
                219L,
                QuestionExplanationArtifact.STATUS_READY,
                """
                {"meaningVi":"Nghĩa","evidenceQuote":"본문",
                 "evidenceKind":"TEXT","correctReasonVi":"Lý do",
                 "relatedTranslationVi":"",
                 "eliminatedOptions":[
                   {"optionKey":"option_2","reasonVi":"Sai"}]}
                """);
        ReflectionTestUtils.setField(
                repeated,
                "inputContractJson",
                singleChoiceInput().replace(
                        "\"passageText\":\"본문 근거\"",
                        "\"passageText\":\"본문 그리고 본문\""));
        when(bindingRepository.findByQuestionVersionIdAndExplanationLanguage(
                119L, "vi"))
                .thenReturn(Optional.of(binding(119L, 219L)));
        when(artifactRepository.findById(219L))
                .thenReturn(Optional.of(repeated));

        assertThat(service.readObjective(
                119L, CanonicalQuestionType.SINGLE_CHOICE)).isEmpty();
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

    private static String singleChoiceV3Input() {
        return """
                {
                  "schemaVersion":"rl-explanation-input-v3",
                  "questionType":"SINGLE_CHOICE",
                  "explanationStrategy":{
                    "registryVersion":"rl-explanation-strategy-registry-v1",
                    "strategyCode":"EVIDENCE_ONLY",
                    "strategyVersion":"v1"
                  },
                  "questionContent":{
                    "options":[{"id":"option_1"},{"id":"option_2"}]
                  },
                  "answerSpec":{"correctOptionIds":["option_1"]},
                  "stimulus":{
                    "type":"READING_PASSAGE",
                    "passageText":"본문 근거",
                    "transcriptText":null,
                    "approved":true
                  },
                  "media":[]
                }
                """;
    }

    private static String standaloneSingleChoiceV3Input() {
        return """
                {
                  "schemaVersion":"rl-explanation-input-v3",
                  "questionType":"SINGLE_CHOICE",
                  "prompt":"정답을 고르세요.",
                  "explanationStrategy":{
                    "registryVersion":"rl-explanation-strategy-registry-v1",
                    "strategyCode":"EVIDENCE_ONLY",
                    "strategyVersion":"v1"
                  },
                  "questionContent":{
                    "options":[{"id":"option_1"},{"id":"option_2"}]
                  },
                  "answerSpec":{"correctOptionIds":["option_1"]},
                  "stimulus":{
                    "type":"STANDALONE_PROMPT",
                    "passageText":"정답을 고르세요.",
                    "transcriptText":null,
                    "approved":true
                  },
                  "media":[]
                }
                """;
    }

    private static String currentFillBlankV3Input() {
        return """
                {
                  "schemaVersion":"rl-explanation-input-v3",
                  "questionType":"FILL_BLANK",
                  "explanationStrategy":{
                    "registryVersion":"rl-explanation-strategy-registry-v2",
                    "strategyCode":"KEYWORD_PARAPHRASE_BRIDGE",
                    "strategyVersion":"v1"
                  },
                  "questionContent":{
                    "blanks":[{"id":"blank_1"}]
                  },
                  "answerSpec":{
                    "blanks":[{
                      "blankId":"blank_1",
                      "acceptedValues":["값"]
                    }]
                  },
                  "stimulus":{
                    "type":"READING_PASSAGE",
                    "passageText":"본문 근거",
                    "transcriptText":null,
                    "approved":true
                  },
                  "media":[]
                }
                """;
    }

    private static String currentTfngV3Input() {
        return """
                {
                  "schemaVersion":"rl-explanation-input-v3",
                  "questionType":"TRUE_FALSE_NOT_GIVEN",
                  "explanationStrategy":{
                    "registryVersion":"rl-explanation-strategy-registry-v2",
                    "strategyCode":"FULL_SOURCE_INLINE_HIGHLIGHT",
                    "strategyVersion":"v1"
                  },
                  "questionContent":{"options":[],"blanks":[]},
                  "answerSpec":{"correctValue":"TRUE"},
                  "stimulus":{
                    "type":"READING_PASSAGE",
                    "passageText":"본문 근거",
                    "transcriptText":null,
                    "approved":true
                  },
                  "media":[]
                }
                """;
    }

    private static String multipleAnswerV3Input() {
        return """
                {
                  "schemaVersion":"rl-explanation-input-v3",
                  "questionType":"MULTIPLE_ANSWER",
                  "explanationStrategy":{
                    "registryVersion":"rl-explanation-strategy-registry-v2",
                    "strategyCode":"EVIDENCE_AND_ELIMINATION",
                    "strategyVersion":"v1"
                  },
                  "questionContent":{"options":[
                    {"id":"option_1"},{"id":"option_2"},{"id":"option_3"}
                  ],"blanks":[]},
                  "answerSpec":{"correctOptionIds":["option_1","option_3"]},
                  "stimulus":{"type":"READING_PASSAGE","passageText":"본문 근거","transcriptText":null,"approved":true},
                  "media":[]
                }
                """;
    }

    private static String matchingV3Input() {
        return """
                {
                  "schemaVersion":"rl-explanation-input-v3",
                  "questionType":"MATCHING",
                  "explanationStrategy":{
                    "registryVersion":"rl-explanation-strategy-registry-v2",
                    "strategyCode":"MATCHING_MATRIX",
                    "strategyVersion":"v1"
                  },
                  "questionContent":{
                    "options":[{"id":"option_1"},{"id":"option_2"},{"id":"option_3"}],
                    "blanks":[{"id":"blank_1"},{"id":"blank_2"}]
                  },
                  "answerSpec":{"blanks":[
                    {"blankId":"blank_1","acceptedValues":["option_1"]},
                    {"blankId":"blank_2","acceptedValues":["option_3"]}
                  ]},
                  "stimulus":{"type":"READING_PASSAGE","passageText":"본문 근거","transcriptText":null,"approved":true},
                  "media":[]
                }
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
