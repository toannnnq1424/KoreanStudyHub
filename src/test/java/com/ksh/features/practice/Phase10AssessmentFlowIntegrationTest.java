package com.ksh.features.practice;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.dto.PracticeDtos.ReadingListeningResultView;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void topikPolicyPublishSnapshotSubmitScoreAndExplainFlowIsVersionLocked() {
        User lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        User student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
        PracticeDraft draft = draftRepository.saveAndFlush(new PracticeDraft(
                "Phase 10 TOPIK Reading",
                "Assessment flow",
                "TOPIK_II",
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
                                "questionType":"SINGLE_CHOICE","prompt":"맞는 것을 고르세요.",
                                "options":["좋습니다","나쁩니다"],
                                "answer":{"value":"1"},
                                "explanationVi":"첫 번째 선택지가 본문과 일치합니다.","points":1
                              }]
                            }]
                          }]
                        }
                        """
        ));

        Long setId = publisherService.publish(draft.getId(), lecturer.getId());
        PracticeSet set = setRepository.findById(setId).orElseThrow();
        PracticeTest test = testRepository.findBySetIdOrderByDisplayOrderAsc(setId).get(0);
        PracticeSection section = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId).get(0);
        PracticeQuestion question = questionRepository.findBySetIdOrderByDisplayOrderAsc(setId).get(0);

        assertThat(set.getAssessmentProgramCode()).isEqualTo("TOPIK");
        assertThat(section.getTestId()).isEqualTo(test.getId());
        assertThat(question.getQuestionType()).isEqualTo("SINGLE_CHOICE");
        assertThat(question.getCanonicalQuestionType()).isEqualTo("SINGLE_CHOICE");
        assertThat(question.getQuestionContentJson()).contains("question-content-v1", "opt_1");
        assertThat(question.getAnswerSpecJson()).contains("answer-spec-v1", "opt_1");
        assertThat(question.getScoringPolicyCode()).isEqualTo("ALL_OR_NOTHING");
        assertThat(question.getScoringProfileCode()).isEqualTo("TOPIK_SINGLE_CHOICE");
        assertThat(question.getScoringProfileVersion()).isEqualTo(1);

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
        assertThat(snapshot.setVersion().getAssessmentProgramCode()).isEqualTo("TOPIK");
        assertThat(snapshot.questions()).singleElement().satisfies(version -> {
            assertThat(version.getCanonicalQuestionType()).isEqualTo("SINGLE_CHOICE");
            assertThat(version.getAnswerSpecJson()).isEqualTo(question.getAnswerSpecJson());
            assertThat(version.getScoringProfileVersion()).isEqualTo(1);
        });

        ReadingListeningResultView result = practiceService.getReadingListeningResult(
                attemptId, student.getId());
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.groups()).singleElement().satisfies(group ->
                assertThat(group.questions()).singleElement().satisfies(row ->
                        assertThat(row.explanationJson()).contains("correctReasonVi")));
    }
}
