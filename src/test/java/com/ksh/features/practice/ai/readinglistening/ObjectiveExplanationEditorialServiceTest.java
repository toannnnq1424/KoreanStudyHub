package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeExplanationEditorialRevisionRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectiveExplanationEditorialServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicationFailsBeforeGraphMutationWhenApprovedRevisionIsMissing()
            throws Exception {
        PracticeAuthorizationService authorization =
                mock(PracticeAuthorizationService.class);
        PracticeExplanationEditorialRevisionRepository revisions =
                mock(PracticeExplanationEditorialRevisionRepository.class);
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        QuestionTypeResolver typeResolver = mock(QuestionTypeResolver.class);
        ReadingListeningExplanationClient client =
                mock(ReadingListeningExplanationClient.class);
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("option_1", "A"),
                        new QuestionContent.Option("option_2", "B")),
                List.of());
        AnswerSpec answerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("option_1"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        when(typeResolver.resolve("SINGLE_CHOICE"))
                .thenReturn(CanonicalQuestionType.SINGLE_CHOICE);
        when(codec.readQuestionContent(
                any(), any(CanonicalQuestionType.class)))
                .thenReturn(content);
        when(codec.readAnswerSpec(any(), any()))
                .thenReturn(answerSpec);
        when(client.promptVersion()).thenReturn("prompt-v9");
        when(client.schemaVersion()).thenReturn("v4");
        when(revisions
                .findFirstByDraftIdAndQuestionClientIdAndEditorialStateOrderByRevisionNoDesc(
                        7L, "question-1", "APPROVED"))
                .thenReturn(Optional.empty());

        ObjectiveExplanationEditorialService service =
                new ObjectiveExplanationEditorialService(
                        authorization,
                        mock(PracticeDraftRepository.class),
                        revisions,
                        mock(PracticeDraftContractService.class),
                        codec,
                        typeResolver,
                        client,
                        objectMapper,
                        mock(PracticeQuestionVersionRepository.class),
                        mock(PracticeSectionVersionRepository.class),
                        mock(PracticeQuestionGroupVersionRepository.class),
                        mock(ExplanationInputFactory.class),
                        mock(QuestionVersionExplanationBindingRepository.class),
                        mock(QuestionExplanationArtifactRepository.class));
        JsonNode root = objectMapper.readTree("""
                {
                  "sections":[{
                    "skill":"READING",
                    "groups":[{
                      "instruction":"Đọc rồi chọn.",
                      "stimulus":{
                        "passageText":"본문 근거",
                        "provenance":{"approved":true}
                      },
                      "questions":[{
                        "clientId":"question-1",
                        "questionNo":1,
                        "questionType":"SINGLE_CHOICE",
                        "prompt":"질문",
                        "questionContent":{},
                        "answerSpec":{},
                        "explanationStrategy":{
                          "registryVersion":"rl-explanation-strategy-registry-v1",
                          "strategyCode":"EVIDENCE_ONLY",
                          "strategyVersion":"v1"
                        }
                      }]
                    }]
                  }]
                }
                """);

        List<ObjectiveExplanationEditorialService.PublishBlocker> blockers =
                service.publishBlockers(7L, 9L, root);

        assertThat(blockers).singleElement().satisfies(blocker -> {
            assertThat(blocker.type()).isEqualTo("BLOCKING");
            assertThat(blocker.code())
                    .isEqualTo("OBJECTIVE_EXPLANATION_APPROVAL_MISSING");
            assertThat(blocker.content())
                    .isEqualTo("Câu 1 (Đọc) chưa có lời giải typed đã duyệt.");
            assertThat(blocker.sIdx()).isZero();
            assertThat(blocker.gIdx()).isZero();
            assertThat(blocker.qIdx()).isZero();
            assertThat(blocker.content()).doesNotContain("question-1");
        });
        assertThatThrownBy(() ->
                service.requireApprovedForPublish(7L, 9L, root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Câu 1 (Đọc) chưa có lời giải typed đã duyệt.");
        verify(authorization, times(2)).requireDraft(
                7L,
                9L,
                com.ksh.features.practice.governance.PracticeAction.PUBLISH);
    }
}
