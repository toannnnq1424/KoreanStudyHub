package com.ksh.features.practice;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.dto.PracticeDtos.ObjectiveDetailPayload;
import com.ksh.features.practice.dto.PracticeDtos.ResultDetailScreenKind;
import com.ksh.features.practice.ai.readinglistening
        .ObjectiveExplanationEditorialService;
import com.ksh.features.practice.ai.readinglistening
        .ReadingListeningExplanationClient;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.manage.service.PracticePublisherService;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import com.ksh.features.practice.service.PracticeService;
import com.ksh.features.practice.service.PracticeVersionSnapshot;
import com.ksh.features.practice.result.PracticeResultDetailAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "openai.api-key=")
@Transactional
class Phase10AssessmentFlowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PracticeDraftRepository draftRepository;
    @Autowired private PracticeSetRepository setRepository;
    @Autowired private PracticeTestRepository testRepository;
    @Autowired private PracticeSectionRepository sectionRepository;
    @Autowired private PracticeQuestionRepository questionRepository;
    @Autowired private PracticeAttemptRepository attemptRepository;
    @Autowired private PracticePublisherService publisherService;
    @Autowired private PracticePublishedVersionService publishedVersionService;
    @Autowired private PracticeService practiceService;
    @Autowired private PracticeResultDetailAssembler resultDetailAssembler;
    @Autowired private ObjectiveExplanationEditorialService
            objectiveExplanationEditorialService;
    @MockitoBean private ReadingListeningExplanationClient
            readingListeningExplanationClient;

    @BeforeEach
    void acceptEditorialFixtureAfterStrategyAuthorityIsValidated() {
        when(readingListeningExplanationClient.cleanAndValidateJson(
                anyString(), any(ExplanationContext.class), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void singleScopePublishSnapshotSubmitScoreAndReadOnlyResultFlowIsVersionLocked() {
        User lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        User student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
        PracticeDraft draft = draftRepository.saveAndFlush(new PracticeDraft(
                "Phase 10 TOPIK Reading",
                "Assessment flow",
                "GLOBAL",
                null,
                "DRAFT",
                lecturer.getId(),
                """
                        {
                          "document":{"title":"Phase 10 TOPIK Reading","detectedCategory":"TOPIK_II"},
                          "sections":[{
                            "title":"Reading","skill":"READING","sectionType":"DEFAULT",
                            "instructions":"본문을 읽고 답하세요.","durationMinutes":20,"totalPoints":1,
                            "groups":[{
                              "label":"1","instruction":"오늘은 날씨가 좋습니다.",
                              "questions":[{
                                "clientId":"question-1",
                                "questionType":"SINGLE_CHOICE","prompt":"맞는 것을 고르세요.",
                                "options":["좋습니다","나쁩니다"],
                                "answer":{"value":"1"},
                                "explanationStrategy":{
                                  "registryVersion":"rl-explanation-strategy-registry-v1",
                                  "strategyCode":"EVIDENCE_ONLY",
                                  "strategyVersion":"v1"
                                },
                                "explanationVi":"첫 번째 선택지가 본문과 일치합니다.","points":1
                              }]
                            }]
                          }]
                        }
                        """
        ));
        var editorial = objectiveExplanationEditorialService.saveEditedDraft(
                draft.getId(),
                "question-1",
                "{\"fixture\":\"phase10-strategy-authority\"}",
                lecturer.getId());
        objectiveExplanationEditorialService.approve(
                draft.getId(),
                "question-1",
                editorial.revisionId(),
                lecturer.getId());

        Long setId = publisherService.publish(draft.getId(), lecturer.getId());
        PracticeSet set = setRepository.findById(setId).orElseThrow();
        PracticeTest test = testRepository.findBySetIdOrderByDisplayOrderAsc(setId).get(0);
        PracticeSection section = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId).get(0);
        PracticeQuestion question = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId).get(0);

        assertThat(section.getTestId()).isEqualTo(test.getId());
        assertThat(question.getQuestionType()).isEqualTo("SINGLE_CHOICE");
        assertThat(question.getQuestionContentJson())
                .contains("question-content-v3", "\"languageTag\":\"ko\"", "opt_1");
        assertThat(question.getAnswerSpecJson()).contains("answer-spec-v1", "opt_1");

        Long attemptId = practiceService.startAttempt(
                setId, test.getId(), section.getId(), student.getId());
        practiceService.submitAttempt(attemptId, student.getId(), Map.of(
                "answer_" + question.getId(), "1"));

        PracticeAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(attempt.getPublishedVersionId()).isNotNull();
        assertThat(attempt.getScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(attempt.getTotalPoints()).isEqualByComparingTo(BigDecimal.ONE);

        PracticeVersionSnapshot snapshot = publishedVersionService.snapshot(
                attempt.getPublishedVersionId(),
                attempt.getSetVersionId(),
                attempt.getTestVersionId(),
                attempt.getSectionVersionId()).orElseThrow();
        assertThat(snapshot.questions()).singleElement().satisfies(version -> {
            assertThat(version.getQuestionType()).isEqualTo("SINGLE_CHOICE");
            assertThat(version.getAnswerSpecJson()).isEqualTo(question.getAnswerSpecJson());
        });

        var result = resultDetailAssembler.assemble(
                attemptId, student.getId(), null);
        assertThat(result.screenKind())
                .isEqualTo(ResultDetailScreenKind.OBJECTIVE_DETAIL);
        ObjectiveDetailPayload payload =
                (ObjectiveDetailPayload) result.payload();
        assertThat(payload.questions()).singleElement().satisfies(row -> {
            assertThat(row.core().earnedPoints())
                    .isEqualByComparingTo(BigDecimal.ONE);
            assertThat(row.explanation().state()).isEqualTo("UNAVAILABLE");
        });
    }
}
