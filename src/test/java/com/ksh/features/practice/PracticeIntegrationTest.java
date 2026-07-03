package com.ksh.features.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSubmission;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSubmissionRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.service.PracticeAttemptConflictException;
import com.ksh.features.practice.service.PracticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PracticeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticeSetRepository setRepository;

    @Autowired
    private PracticeQuestionRepository questionRepository;

    @Autowired
    private PracticeSubmissionRepository submissionRepository;

    @Autowired
    private PracticeAttemptRepository attemptRepository;

    @Autowired
    private PracticeTestRepository testRepository;

    @Autowired
    private PracticeQuestionGroupRepository groupRepository;

    @Autowired
    private PracticeSectionRepository sectionRepository;

    @Autowired
    private QuestionExplanationCacheRepository questionExplanationCacheRepository;

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private com.ksh.features.practice.repository.PracticeDraftRepository draftRepository;

    @Autowired
    private com.ksh.features.practice.repository.PracticeEditLogRepository editLogRepository;

    @Autowired
    private com.ksh.features.practice.manage.service.PracticeRevisionService revisionService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.ksh.features.practice.ai.WritingEvaluationClient writingEvaluationClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.ksh.features.practice.ai.ReadingListeningExplanationClient readingListeningExplanationClient;

    private User student;
    private User lecturer;
    private PracticeSet practiceSet;
    private PracticeQuestion question;
    private PracticeTest defaultTest;
    private PracticeSection defaultSection;

    @BeforeEach
    void setUp() {
        student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();

        attemptRepository.deleteAll();
        submissionRepository.deleteAll();

        when(writingEvaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"score\":8.0,\"overall_score\":8.0,\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}");
        when(readingListeningExplanationClient.model()).thenReturn("test-rl-model");
        when(readingListeningExplanationClient.promptVersion()).thenReturn("prompt-v1");
        when(readingListeningExplanationClient.schemaVersion()).thenReturn("schema-v1");
        when(readingListeningExplanationClient.explanationLanguage()).thenReturn("vi");
        when(readingListeningExplanationClient.explain(any(PracticeQuestion.class), nullable(String.class), anyString(), nullable(String.class)))
                .thenReturn(null);

        // Seed a published practice set
        practiceSet = new PracticeSet(
                "TOPIK II - Đọc hiểu 35",
                "Mô tả đề thi đọc hiểu TOPIK II kì 35",
                "READING",
                "TOPIK_II",
                "GLOBAL",
                null,
                "practice-pdfs/test.pdf",
                "{}",
                "PUBLISHED",
                lecturer.getId()
        );
        practiceSet = setRepository.saveAndFlush(practiceSet);

        // Seed a default test
        defaultTest = new PracticeTest(practiceSet.getId(), "Test 1", "Desc", 1, 40);
        defaultTest = testRepository.saveAndFlush(defaultTest);

        // Seed a default section
        defaultSection = new PracticeSection(practiceSet.getId(), "Phần Đọc", "READING", "MCQ", "Đọc kỹ", 40, BigDecimal.TEN, 1);
        defaultSection.setTestId(defaultTest.getId());
        defaultSection = sectionRepository.saveAndFlush(defaultSection);

        // Seed a question for the set
        question = new PracticeQuestion(
                practiceSet.getId(),
                1,
                "MCQ",
                "Câu hỏi 1",
                "[\"Đáp án A\", \"Đáp án B\"]",
                "1",
                "Giải thích đáp án đúng",
                BigDecimal.valueOf(2.5),
                0
        );
        question.setGroupId(null);
        question = questionRepository.saveAndFlush(question);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testIndexAuthenticated() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(model().attributeExists("sets"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSetDetailView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/set-detail"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attributeExists("submissions"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testTestDetailView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/test-detail"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attributeExists("sections"))
                .andExpect(model().attributeExists("inProgressAttempts"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testModeView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId() + "/mode"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/mode"))
                .andExpect(model().attributeExists("view"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testPlayerView() throws Exception {
        // Start attempt
        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(defaultSection.getId()))
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/practice/attempts/*"));

        List<PracticeAttempt> attempts = attemptRepository.findAll();
        assertThat(attempts).isNotEmpty();
        PracticeAttempt attempt = attempts.get(0);

        mockMvc.perform(get("/practice/attempts/" + attempt.getId()).param("mode", "exam"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/player"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attribute("mode", "exam"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSubmitAttemptAndGetResult() throws Exception {
        // Start attempt
        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(defaultSection.getId()))
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection());

        List<PracticeAttempt> attempts = attemptRepository.findAll();
        assertThat(attempts).isNotEmpty();
        PracticeAttempt attempt = attempts.get(0);

        // Perform Submit
        String paramName = "answer_" + question.getId();
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param(paramName, "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));

        // Perform GET result view -> should redirect to rl-result template for READING/LISTENING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result"))
                .andExpect(model().attributeExists("result"));

        // Perform GET detailed result view -> should redirect to rl-result-detail template
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"))
                .andExpect(model().attributeExists("result"));

        // Perform POST Re-evaluation
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/re-evaluate")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testReadingResultOverviewCreatesCacheAndDetailDoesNotRecallProvider() throws Exception {
        String providerJson = """
                {
                  "meaningVi": "meaning",
                  "evidenceQuote": "quote",
                  "correctReasonVi": "reason",
                  "relatedTranslationVi": "translation",
                  "eliminatedOptions": [
                    {"optionKey": "2", "reasonVi": "wrong"}
                  ]
                }
                """;
        when(readingListeningExplanationClient.explain(any(PracticeQuestion.class), nullable(String.class), eq("READING"), nullable(String.class)))
                .thenReturn(providerJson);

        PracticeAttempt attempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.setStatus("SUBMITTED");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result"));

        long cacheRows = countExplanationCacheRowsForQuestion(question.getId());
        assertThat(cacheRows).isEqualTo(1);

        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"));

        verify(readingListeningExplanationClient, times(1))
                .explain(any(PracticeQuestion.class), nullable(String.class), eq("READING"), nullable(String.class));
    }

    private long countExplanationCacheRowsForQuestion(Long questionId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status ->
                questionExplanationCacheRepository.findAll().stream()
                        .filter(row -> questionId.equals(row.getQuestionId()))
                        .count()
        );
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSubmitWritingAttemptAndGetResult() throws Exception {
        // Seed a published WRITING set
        PracticeSet writingSetSeed = new PracticeSet(
                "TOPIK II - Viết 35",
                "Mô tả đề thi viết TOPIK II kì 35",
                "WRITING",
                "TOPIK_II",
                "GLOBAL",
                null,
                "practice-pdfs/test.pdf",
                "{}",
                "PUBLISHED",
                lecturer.getId()
        );
        final PracticeSet writingSet = setRepository.saveAndFlush(writingSetSeed);

        PracticeTest writingTest = new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40);
        writingTest = testRepository.saveAndFlush(writingTest);

        PracticeSection writingSec = new PracticeSection(writingSet.getId(), "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 1);
        writingSec.setTestId(writingTest.getId());
        writingSec = sectionRepository.saveAndFlush(writingSec);

        PracticeQuestion writingQuestion = new PracticeQuestion(
                writingSet.getId(),
                51,
                "ESSAY",
                "Câu hỏi viết 51",
                "[]",
                "",
                "Giải thích đáp án đúng",
                BigDecimal.valueOf(10.0),
                0
        );
        writingQuestion.setGroupId(null);
        questionRepository.saveAndFlush(writingQuestion);

        // Start attempt
        mockMvc.perform(post("/practice/sets/" + writingSet.getId() + "/tests/" + writingTest.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(writingSec.getId()))
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection());

        List<PracticeAttempt> attempts = attemptRepository.findAll();
        assertThat(attempts).isNotEmpty();
        PracticeAttempt attempt = attempts.stream()
                .filter(a -> a.getSetId().equals(writingSet.getId()))
                .findFirst().orElseThrow();

        // Perform Submit
        String paramName = "answer_" + writingQuestion.getId();
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param(paramName, "Tôi học tiếng Hàn."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));

        // Perform GET result view -> should redirect to result template for WRITING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result"))
                .andExpect(model().attributeExists("result"));

        // Perform GET detailed result view -> should redirect to result-detail template for WRITING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result-detail"))
                .andExpect(model().attributeExists("result"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testWritingEvaluationRunsOutsideActiveTransaction() throws Exception {
        PracticeSet writingSet = setRepository.saveAndFlush(new PracticeSet(
                "Writing Transaction Boundary",
                "Desc",
                "WRITING",
                "TOPIK_II",
                "GLOBAL",
                null,
                null,
                "{}",
                "PUBLISHED",
                lecturer.getId()
        ));
        PracticeTest writingTest = testRepository.saveAndFlush(new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection writingSection = new PracticeSection(
                writingSet.getId(), "Writing Section", "WRITING", "ESSAY", "Instruction", 50, BigDecimal.TEN, 1);
        writingSection.setTestId(writingTest.getId());
        writingSection = sectionRepository.saveAndFlush(writingSection);

        PracticeQuestion writingQuestion = new PracticeQuestion(
                writingSet.getId(), 51, "ESSAY", "Prompt", "[]", "", "Explain", BigDecimal.TEN, 0);
        writingQuestion = questionRepository.saveAndFlush(writingQuestion);

        mockMvc.perform(post("/practice/sets/" + writingSet.getId() + "/tests/" + writingTest.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(writingSection.getId()))
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection());

        PracticeAttempt attempt = attemptRepository.findAll().stream()
                .filter(a -> a.getSetId().equals(writingSet.getId()))
                .findFirst()
                .orElseThrow();

        final boolean[] evaluatorSawTransaction = {true};
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq("Prompt"), anyString(), eq(false)))
                .thenAnswer(invocation -> {
                    evaluatorSawTransaction[0] = TransactionSynchronizationManager.isActualTransactionActive();
                    return "{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param("answer_" + writingQuestion.getId(), "My writing answer"))
                .andExpect(status().is3xxRedirection());

        assertThat(evaluatorSawTransaction[0]).isFalse();
    }

    @Test
    void testPracticeAttemptLockVersionIncrementsOnUpdate() {
        PracticeAttempt attempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);
        Long initialVersion = attempt.getLockVersion();

        attempt.setAnswersJson("{\"1\":\"2\"}");
        attempt = attemptRepository.saveAndFlush(attempt);

        assertThat(initialVersion).isNotNull();
        assertThat(attempt.getLockVersion()).isGreaterThan(initialVersion);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProfileRedirectsToProgress() throws Exception {
        mockMvc.perform(get("/practice/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/progress"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProgressAuthenticated() throws Exception {
        mockMvc.perform(get("/practice/progress"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/progress"))
                .andExpect(model().attributeExists("overview"))
                .andExpect(model().attributeExists("analytics"))
                .andExpect(model().attributeExists("overviewJson"))
                .andExpect(model().attributeExists("analyticsJson"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProgressUsesPracticeAttemptSkillAndLinks() throws Exception {
        practiceSet.setSkill("MIXED");
        setRepository.saveAndFlush(practiceSet);

        PracticeAttempt readingAttempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        readingAttempt.markGraded(BigDecimal.valueOf(8), BigDecimal.TEN, "{}", "{}");
        readingAttempt = attemptRepository.saveAndFlush(readingAttempt);

        PracticeSection writingSection = new PracticeSection(
                practiceSet.getId(), "Pháº§n Viáº¿t", "WRITING", "ESSAY", "Viáº¿t luáº­n", 50, BigDecimal.TEN, 2);
        writingSection.setTestId(defaultTest.getId());
        writingSection = sectionRepository.saveAndFlush(writingSection);

        PracticeAttempt writingAttempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "WRITING", writingSection.getId());
        writingAttempt.markGraded(BigDecimal.valueOf(7), BigDecimal.TEN, "{}", "{}");
        writingAttempt = attemptRepository.saveAndFlush(writingAttempt);

        mockMvc.perform(get("/practice/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("skill\\\":\\\"READING\\\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("skill\\\":\\\"WRITING\\\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("skill\\\":\\\"MIXED\\\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/practice/attempts/" + readingAttempt.getId() + "/result")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId() + "/attempts")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"" + writingSection.getId() + "\"")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProgressInProgressAttemptShowsContinueOnly() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(get("/practice/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/practice/attempts/" + attempt.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/practice/attempts/" + attempt.getId() + "/result"))));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProgressDoesNotShowOtherUsersAttemptsOrCreateSubmission() throws Exception {
        PracticeAttempt otherUserAttempt = new PracticeAttempt(
                lecturer.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        otherUserAttempt.markGraded(BigDecimal.valueOf(8), BigDecimal.TEN, "{}", "{}");
        otherUserAttempt = attemptRepository.saveAndFlush(otherUserAttempt);
        long submissionsBefore = submissionRepository.count();

        mockMvc.perform(get("/practice/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/practice/attempts/" + otherUserAttempt.getId()))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pp-empty-state")));

        assertThat(submissionRepository.count()).isEqualTo(submissionsBefore);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testUploadDeniedForStudent() throws Exception {
        mockMvc.perform(get("/practice/manage/import"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testUploadAllowedForLecturer() throws Exception {
        mockMvc.perform(get("/practice/manage/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/manage/import-wizard"));

        // Check legacy upload redirect
        mockMvc.perform(get("/practice/manage/upload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/manage/import"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testManualDraftForLecturer() throws Exception {
        // GET /practice/manage/create redirects to /practice/manage/drafts/{draftId}
        mockMvc.perform(get("/practice/manage/create"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/practice/manage/drafts/*"));

        // Check legacy manual redirect
        mockMvc.perform(get("/practice/manage/manual"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/manage/create"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testPublishDraft() throws Exception {
        String draftJson = """
        {
          "document": {
            "detectedCategory": "TOPIK_II",
            "title": "Đề mới xuất bản",
            "confidence": 1.0
          },
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "durationMinutes": 40,
              "groups": [
                {
                  "label": "1",
                  "questionFrom": 1,
                  "questionTo": 1,
                  "instruction": "Chỉ dẫn",
                  "questions": [
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Câu 1",
                      "options": ["A", "B"],
                      "answer": { "value": "1" },
                      "explanationVi": "Vì đúng",
                      "points": 5.0
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                "Draft test", "Desc", "TOPIK_II", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson
        );
        draft = draftRepository.saveAndFlush(draft);

        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/publish")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/practice/sets/*"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testOptimisticLockingConflict() throws Exception {
        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                "Lock Test", "Desc", "TOPIK_II", "GLOBAL", null, "DRAFT", lecturer.getId(), "{}"
        );
        draft = draftRepository.saveAndFlush(draft);
        int originalVersion = draft.getVersion();

        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/autosave")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"draftJson\":\"{}\",\"title\":\"Lock Test Sửa\",\"version\":" + originalVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(originalVersion + 1));

        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/autosave")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"draftJson\":\"{}\",\"title\":\"Ghi đè lỗi\",\"version\":" + originalVersion + "}"))
                .andExpect(status().is4xxClientError()) // HTTP 409 Conflict
                .andExpect(jsonPath("$.status").value("conflict"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testPublishEditAndRestoreRevision() throws Exception {
        String draftJson = """
        {
          "document": {
            "detectedCategory": "TOPIK_II",
            "title": "Học liệu gốc",
            "confidence": 1.0
          },
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "durationMinutes": 40,
              "groups": [
                {
                  "label": "1",
                  "questionFrom": 1,
                  "questionTo": 1,
                  "instruction": "Chỉ dẫn",
                  "questions": [
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Câu 1 ban đầu",
                      "options": ["A", "B"],
                      "answer": { "value": "1" },
                      "explanationVi": "Vì đúng",
                      "points": 5.0
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                "Học liệu gốc", "Desc", "TOPIK_II", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson
        );
        draft = draftRepository.saveAndFlush(draft);

        // 1. Publish first time
        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/publish").with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<PracticeSet> sets = setRepository.findAll();
        PracticeSet publishedSet = sets.stream()
                .filter(s -> "Học liệu gốc".equals(s.getTitle()))
                .findFirst().orElseThrow();

        // 2. Edit existing set -> redirects to /practice/manage/drafts/{id}
        mockMvc.perform(get("/practice/manage/sets/" + publishedSet.getId() + "/edit"))
                .andExpect(status().is3xxRedirection());

        List<com.ksh.entities.PracticeDraft> drafts = draftRepository.findByOwnerIdOrderByUpdatedAtDesc(lecturer.getId());
        com.ksh.entities.PracticeDraft editDraft = drafts.stream()
                .filter(d -> d.getPublishedSetId() != null && d.getPublishedSetId().equals(publishedSet.getId()))
                .findFirst().orElseThrow();

        // Modify a question prompt in the edit draft JSON
        String updatedJson = draftJson.replace("Câu 1 ban đầu", "Câu 1 đã sửa");
        
        mockMvc.perform(post("/practice/manage/drafts/" + editDraft.getId() + "/autosave")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"draftJson\":" + objectMapper.writeValueAsString(updatedJson) + ",\"title\":\"Học liệu đã sửa\",\"version\":" + editDraft.getVersion() + "}"))
                .andExpect(status().isOk());

        // 3. Publish modified draft to update original set
        mockMvc.perform(post("/practice/manage/drafts/" + editDraft.getId() + "/publish").with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 4. Assert a revision log entry was recorded
        List<com.ksh.entities.PracticeEditLog> logs = editLogRepository.findBySetIdOrderByEditedAtDesc(publishedSet.getId());
        assertThat(logs).isNotEmpty();
        com.ksh.entities.PracticeEditLog lastLog = logs.stream()
                .filter(l -> "QUESTIONS,METADATA".equals(l.getEditType()) || "QUESTIONS".equals(l.getEditType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No edit log found with QUESTIONS or QUESTIONS,METADATA edit type"));
        assertThat(lastLog.getBeforeSnapshotJson()).contains("Câu 1 ban đầu");
        assertThat(lastLog.getAfterSnapshotJson()).contains("Câu 1 đã sửa");

        // 5. Restore the revision
        revisionService.restoreRevision(lastLog.getId(), lecturer.getId());

        // Assert the questions in the active published set have reverted to "Câu 1 ban đầu"
        List<PracticeQuestion> revertedQs = questionRepository.findBySetIdOrderByDisplayOrderAsc(publishedSet.getId());
        assertThat(revertedQs).isNotEmpty();
        assertThat(revertedQs.get(0).getPrompt()).isEqualTo("Câu 1 ban đầu");
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSectionAttemptsFlow() throws Exception {
        // 1. Create and save a PracticeTest
        PracticeTest test = new PracticeTest(practiceSet.getId(), "Test 1", "Desc", 1, 40);
        test = testRepository.saveAndFlush(test);

        // 2. Create Reading and Writing sections
        PracticeSection readingSec = new PracticeSection(practiceSet.getId(), "Phần Đọc", "READING", "MCQ", "Đọc kỹ", 40, BigDecimal.TEN, 1);
        readingSec.setTestId(test.getId());
        readingSec = sectionRepository.saveAndFlush(readingSec);

        PracticeSection writingSec = new PracticeSection(practiceSet.getId(), "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 2);
        writingSec.setTestId(test.getId());
        writingSec = sectionRepository.saveAndFlush(writingSec);

        // Seed question groups for the sections to satisfy multi-section requirements
        PracticeQuestionGroup readingGroup = new PracticeQuestionGroup(practiceSet.getId(), "Phần Đọc", 1, 1, "Đọc văn bản", null, null, 1);
        readingGroup.setSectionId(readingSec.getId());
        groupRepository.saveAndFlush(readingGroup);

        PracticeQuestionGroup writingGroup = new PracticeQuestionGroup(practiceSet.getId(), "Phần Viết", 2, 2, "Viết luận", null, null, 2);
        writingGroup.setSectionId(writingSec.getId());
        groupRepository.saveAndFlush(writingGroup);

        // --- Test 1: Start Reading ---
        // Post request to create attempt for Reading section
        String redirectUrl = mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + test.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(readingSec.getId()))
                        .param("mode", "practice"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(redirectUrl).contains("/practice/attempts/");
        String attemptIdStr = redirectUrl.substring(redirectUrl.indexOf("/attempts/") + 10, redirectUrl.indexOf("?"));
        Long readingAttemptId = Long.parseLong(attemptIdStr);

        // Verify PracticeAttempt was created correctly, and NO PracticeSubmission was created
        PracticeAttempt readingAttempt = attemptRepository.findById(readingAttemptId).orElseThrow();
        assertThat(readingAttempt.getSectionId()).isEqualTo(readingSec.getId());
        assertThat(readingAttempt.getSkill()).isEqualTo("READING");
        assertThat(readingAttempt.getTestId()).isEqualTo(test.getId());
        assertThat(readingAttempt.getStatus()).isEqualTo("IN_PROGRESS");

        List<PracticeSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).isEmpty();

        // --- Test 2: Start Writing ---
        String redirectUrl2 = mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + test.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(writingSec.getId()))
                        .param("mode", "practice"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        String attemptIdStr2 = redirectUrl2.substring(redirectUrl2.indexOf("/attempts/") + 10, redirectUrl2.indexOf("?"));
        Long writingAttemptId = Long.parseLong(attemptIdStr2);
        PracticeAttempt writingAttempt = attemptRepository.findById(writingAttemptId).orElseThrow();
        assertThat(writingAttempt.getSectionId()).isEqualTo(writingSec.getId());
        assertThat(writingAttempt.getSkill()).isEqualTo("WRITING");
        assertThat(writingAttempt.getId()).isNotEqualTo(readingAttemptId);

        // --- Test 3: Restart Reading (reuses existing IN_PROGRESS attempt) ---
        String redirectUrl3 = mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + test.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(readingSec.getId()))
                        .param("mode", "practice"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String attemptIdStr3 = redirectUrl3.substring(redirectUrl3.indexOf("/attempts/") + 10, redirectUrl3.indexOf("?"));
        Long readingAttemptId3 = Long.parseLong(attemptIdStr3);
        assertThat(readingAttemptId3).isEqualTo(readingAttemptId);

        // --- Test 4: SectionId mismatch testId ---
        PracticeTest test2 = new PracticeTest(practiceSet.getId(), "Test 2", "Desc", 2, 40);
        test2 = testRepository.saveAndFlush(test2);
        PracticeSection mismatchedSec = new PracticeSection(practiceSet.getId(), "Mismatched", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 3);
        mismatchedSec.setTestId(test2.getId());
        mismatchedSec = sectionRepository.saveAndFlush(mismatchedSec);

        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + test.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(mismatchedSec.getId())))
                .andExpect(status().is4xxClientError());

        // --- Test 5: SectionId mismatch setId ---
        PracticeSet anotherSet = new PracticeSet("Another", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId());
        anotherSet = setRepository.saveAndFlush(anotherSet);
        PracticeSection anotherSetSec = new PracticeSection(anotherSet.getId(), "Phần Khác", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        anotherSetSec.setTestId(test.getId());
        anotherSetSec = sectionRepository.saveAndFlush(anotherSetSec);

        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + test.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(anotherSetSec.getId())))
                .andExpect(status().is4xxClientError());

        // --- Test 6: Set skill=MIXED, section skill=READING -> attempt skill is READING ---
        practiceSet.setSkill("MIXED");
        setRepository.saveAndFlush(practiceSet);

        String redirectUrlMixed = mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + test.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(readingSec.getId()))
                        .param("mode", "practice"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String attemptIdStrMixed = redirectUrlMixed.substring(redirectUrlMixed.indexOf("/attempts/") + 10, redirectUrlMixed.indexOf("?"));
        Long mixedAttemptId = Long.parseLong(attemptIdStrMixed);
        PracticeAttempt mixedAttempt = attemptRepository.findById(mixedAttemptId).orElseThrow();
        assertThat(mixedAttempt.getSkill()).isEqualTo("READING");

        // --- Test 7: Player access (only loads current section) ---
        mockMvc.perform(get("/practice/attempts/" + readingAttemptId))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/player"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attribute("activeSectionTitle", "Phần Đọc"))
                .andExpect(model().attribute("activeSectionSkill", "READING"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testPlayerAccessDeniedForOtherUser() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), practiceSet.getId(), 1L, "READING", 1L);
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(get("/practice/attempts/" + attempt.getId()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testDiscardAttempt() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), practiceSet.getId(), 1L, "READING", 1L);
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/discard")
                        .with(csrf())
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", "1"))
                .andExpect(status().is3xxRedirection());

        assertThat(attemptRepository.findById(attempt.getId())).isEmpty();
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn") // different user
    void testDiscardAttemptDeniedForOtherUser() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), practiceSet.getId(), 1L, "READING", 1L);
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/discard")
                        .with(csrf())
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", "1"))
                .andExpect(status().is4xxClientError());

        assertThat(attemptRepository.findById(attempt.getId())).isPresent();
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testDiscardAttemptDeniedForSubmittedAndGraded() throws Exception {
        PracticeAttempt attemptSubmitted = new PracticeAttempt(student.getId(), practiceSet.getId(), 1L, "READING", 1L);
        attemptSubmitted.setStatus("SUBMITTED");
        attemptSubmitted = attemptRepository.saveAndFlush(attemptSubmitted);

        mockMvc.perform(post("/practice/attempts/" + attemptSubmitted.getId() + "/discard")
                        .with(csrf())
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", "1"))
                .andExpect(status().is4xxClientError());

        assertThat(attemptRepository.findById(attemptSubmitted.getId())).isPresent();

        PracticeAttempt attemptGraded = new PracticeAttempt(student.getId(), practiceSet.getId(), 1L, "READING", 1L);
        attemptGraded.setStatus("GRADED");
        attemptGraded = attemptRepository.saveAndFlush(attemptGraded);

        mockMvc.perform(post("/practice/attempts/" + attemptGraded.getId() + "/discard")
                        .with(csrf())
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", "1"))
                .andExpect(status().is4xxClientError());

        assertThat(attemptRepository.findById(attemptGraded.getId())).isPresent();
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn") // different user
    void testSubmitAttemptDeniedForOtherUser() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param("answer_" + question.getId(), "1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn") // different user
    void testResultAccessDeniedForOtherUser() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.setStatus("SUBMITTED");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testMixedSetSkillBasedRouting() throws Exception {
        // Set skill=MIXED
        practiceSet.setSkill("MIXED");
        setRepository.saveAndFlush(practiceSet);

        // Seed group for defaultSection to avoid IllegalStateException on multi-section set
        PracticeQuestionGroup group1 = new PracticeQuestionGroup(practiceSet.getId(), "Phần 1", 1, 1, "Đọc văn bản", null, null, 1);
        group1.setSectionId(defaultSection.getId());
        group1 = groupRepository.saveAndFlush(group1);

        question.setGroupId(group1.getId());
        questionRepository.saveAndFlush(question);

        // 1. Reading attempt -> rl-result & rl-result-detail
        PracticeAttempt readingAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        readingAttempt.setStatus("SUBMITTED");
        readingAttempt = attemptRepository.saveAndFlush(readingAttempt);

        mockMvc.perform(get("/practice/attempts/" + readingAttempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result"));

        mockMvc.perform(get("/practice/attempts/" + readingAttempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"));

        // 2. Writing attempt -> result & result-detail
        PracticeSection writingSection = new PracticeSection(practiceSet.getId(), "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 2);
        writingSection.setTestId(defaultTest.getId());
        writingSection = sectionRepository.saveAndFlush(writingSection);

        PracticeQuestionGroup group2 = new PracticeQuestionGroup(practiceSet.getId(), "Phần 2", 2, 2, "Viết đoạn văn", null, null, 2);
        group2.setSectionId(writingSection.getId());
        groupRepository.saveAndFlush(group2);

        PracticeAttempt writingAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "WRITING", writingSection.getId());
        writingAttempt.setStatus("GRADED");
        writingAttempt = attemptRepository.saveAndFlush(writingAttempt);

        mockMvc.perform(get("/practice/attempts/" + writingAttempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result"));

        mockMvc.perform(get("/practice/attempts/" + writingAttempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result-detail"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testReadingResultDetailLegacyFallback() throws Exception {
        // Create attempt for Reading section
        PracticeAttempt readingAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        readingAttempt.setStatus("SUBMITTED");
        readingAttempt = attemptRepository.saveAndFlush(readingAttempt);

        mockMvc.perform(get("/practice/attempts/" + readingAttempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Câu hỏi 1")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testReadingResultDetailEmptyState() throws Exception {
        // Clear all groups and questions from the set
        questionRepository.deleteBySetId(practiceSet.getId());
        groupRepository.deleteBySetId(practiceSet.getId());

        PracticeAttempt readingAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        readingAttempt.setStatus("SUBMITTED");
        readingAttempt = attemptRepository.saveAndFlush(readingAttempt);

        mockMvc.perform(get("/practice/attempts/" + readingAttempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không tìm thấy dữ liệu câu hỏi cho lượt làm này.")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testMultipleSectionsIsolatesScoring() throws Exception {
        // Seed a published WRITING set
        PracticeSet writingSet = new PracticeSet(
                "Multi-Section Writing Set", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId()
        );
        writingSet = setRepository.saveAndFlush(writingSet);

        PracticeTest test = new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40);
        test = testRepository.saveAndFlush(test);

        // Section A (Points = 10)
        PracticeSection sectionA = new PracticeSection(writingSet.getId(), "Section A", "WRITING", "ESSAY", "Desc", 50, BigDecimal.TEN, 1);
        sectionA.setTestId(test.getId());
        sectionA = sectionRepository.saveAndFlush(sectionA);

        // Section B (Points = 20)
        PracticeSection sectionB = new PracticeSection(writingSet.getId(), "Section B", "WRITING", "ESSAY", "Desc", 50, BigDecimal.valueOf(20.0), 2);
        sectionB.setTestId(test.getId());
        sectionB = sectionRepository.saveAndFlush(sectionB);

        // Group A and Question A
        PracticeQuestionGroup groupA = new PracticeQuestionGroup(writingSet.getId(), "Group A", 1, 1, "Desc", null, null, 1);
        groupA.setSectionId(sectionA.getId());
        groupA = groupRepository.saveAndFlush(groupA);

        PracticeQuestion qA = new PracticeQuestion(writingSet.getId(), 51, "ESSAY", "Prompt A", "[]", "", "Explain", BigDecimal.valueOf(10.0), 0);
        qA.setGroupId(groupA.getId());
        qA = questionRepository.saveAndFlush(qA);

        // Group B and Question B
        PracticeQuestionGroup groupB = new PracticeQuestionGroup(writingSet.getId(), "Group B", 2, 2, "Desc", null, null, 2);
        groupB.setSectionId(sectionB.getId());
        groupB = groupRepository.saveAndFlush(groupB);

        PracticeQuestion qB = new PracticeQuestion(writingSet.getId(), 52, "ESSAY", "Prompt B", "[]", "", "Explain", BigDecimal.valueOf(20.0), 0);
        qB.setGroupId(groupB.getId());
        qB = questionRepository.saveAndFlush(qB);

        // Start attempt on Section A
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), writingSet.getId(), test.getId(), "WRITING", sectionA.getId());
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        // Mock evaluation client for Question A
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq("Prompt A"), anyString(), anyBoolean()))
                .thenReturn("{\"raw_score\":8.0,\"raw_score_max\":10.0}"); // 80% earned points

        // Submit for Section A attempt
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param("answer_" + qA.getId(), "Student Answer A"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));

        // Verify attempt points are isolated:
        // Total points should be 10.0 (Question A only), score = 8.0 (80% of 10.0) -> attempt score = 80.00%
        PracticeAttempt gradedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(0, gradedAttempt.getTotalPoints().compareTo(BigDecimal.valueOf(10.0)));
        assertEquals(0, gradedAttempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testResultRenderSecurityEscaping() throws Exception {
        // Seed a published WRITING set
        PracticeSet writingSet = new PracticeSet(
                "Security Writing Set", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId()
        );
        writingSet = setRepository.saveAndFlush(writingSet);

        PracticeTest test = new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40);
        test = testRepository.saveAndFlush(test);

        PracticeSection section = new PracticeSection(writingSet.getId(), "Section 1", "WRITING", "ESSAY", "Desc", 50, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);

        PracticeQuestionGroup group = new PracticeQuestionGroup(writingSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);

        PracticeQuestion q = new PracticeQuestion(writingSet.getId(), 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.valueOf(10.0), 0);
        q.setGroupId(group.getId());
        q = questionRepository.saveAndFlush(q);

        // Malicious input payload containing characters to escape
        String maliciousAnswer = "Tôi học tiếng Hàn 한국어 </script> <script>alert('hack')</script> \"quotes\" \\ backslash \n newline";

        // Create Graded Attempt with malicious answer and JSON feedback mapping
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), writingSet.getId(), test.getId(), "WRITING", section.getId());
        attempt.setStatus("GRADED");
        
        // Write the structures
        Map<String, String> answersMap = Map.of(String.valueOf(q.getId()), maliciousAnswer);
        String answersJson = objectMapper.writeValueAsString(answersMap);

        java.util.Map<String, Object> qFeedback = new java.util.LinkedHashMap<>();
        qFeedback.put("raw_score", 8.0);
        qFeedback.put("raw_score_max", 10.0);
        qFeedback.put("summary_vi", "Bài tốt </script> <script>alert(1)</script> \"nháy\" \\ gạch");
        qFeedback.put("upgraded_answer", "Nâng cấp </script> <script>alert(2)</script>");

        java.util.Map<String, Object> feedbackMap = new java.util.LinkedHashMap<>();
        feedbackMap.put(String.valueOf(q.getId()), qFeedback);
        String aiFeedbackJson = objectMapper.writeValueAsString(feedbackMap);

        attempt.markGraded(BigDecimal.valueOf(80.00), BigDecimal.valueOf(10.0), answersJson, aiFeedbackJson);
        attempt = attemptRepository.saveAndFlush(attempt);

        // Load result detail page
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                // Assert no plain executable <script>alert tag is rendered in response (since HTML tags inside inline JSON string variables are escaped)
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("</script> <script>alert"))))
                // Thymeleaf JS Inlining escapes solidus and closing tag as <\/script>
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<\\/script> <script>alert")))
                // HTML structure is still HTTP 200 OK and render successful
                .andExpect(view().name("practice/result-detail"));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testConcurrentWritingSubmitOnlyOneCommit() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Concurrent Submit Writing", false);
        CyclicBarrier evaluatorBarrier = new CyclicBarrier(2);
        AtomicInteger evaluatorCalls = new AtomicInteger();
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(false)))
                .thenAnswer(invocation -> {
                    evaluatorCalls.incrementAndGet();
                    evaluatorBarrier.await(5, TimeUnit.SECONDS);
                    return "{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> submit = () -> {
                try {
                    return practiceService.submitAttempt(
                            fixture.attemptId(),
                            student.getId(),
                            Map.of("answer_" + fixture.questionId(), "Concurrent answer")
                    );
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Object> first = executor.submit(submit);
            Future<Object> second = executor.submit(submit);
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            long successes = List.of(firstResult, secondResult).stream()
                    .filter(result -> result instanceof Long)
                    .count();
            long conflicts = List.of(firstResult, secondResult).stream()
                    .filter(result -> result instanceof PracticeAttemptConflictException)
                    .count();

            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(2, evaluatorCalls.get());

            PracticeAttempt finalAttempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals("GRADED", finalAttempt.getStatus());
            assertEquals(0, finalAttempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
            assertTrue(objectMapper.readTree(finalAttempt.getAiFeedbackJson()).has(String.valueOf(fixture.questionId())));
        } finally {
            executor.shutdownNow();
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingSubmitAutosaveConflictReturnsHttp409AndKeepsAutosavedAnswers() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Autosave Conflict Writing", false);
        try {
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(false)))
                .thenAnswer(invocation -> {
                    practiceService.saveInProgressAnswers(
                            fixture.attemptId(),
                            student.getId(),
                            Map.of("answer_" + fixture.questionId(), "Autosaved answer")
                    );
                    return "{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        mockMvc.perform(post("/practice/attempts/" + fixture.attemptId() + "/submit")
                        .with(csrf())
                        .param("answer_" + fixture.questionId(), "Submitted answer"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bài làm đã thay đổi")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Submitted answer"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(fixture.prompt()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("lockVersion"))));

        PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
        assertEquals("IN_PROGRESS", attempt.getStatus());
        assertTrue(attempt.getAnswersJson().contains("Autosaved answer"));
        assertFalse(attempt.getAnswersJson().contains("Submitted answer"));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingSubmitAfterDiscardDoesNotRecreateAttempt() {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Discard Conflict Writing", false);
        try {
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(false)))
                .thenAnswer(invocation -> {
                    practiceService.discardAttempt(fixture.attemptId(), student.getId());
                    return "{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> practiceService.submitAttempt(
                        fixture.attemptId(),
                        student.getId(),
                        Map.of("answer_" + fixture.questionId(), "Submitted answer")
                ));

        assertTrue(attemptRepository.findById(fixture.attemptId()).isEmpty());
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingReEvaluateStaleAnswersConflictPreservesOldResultAndRunsOutsideTransaction() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Reevaluate Conflict Writing", true);
        final boolean[] evaluatorSawTransaction = {true};
        try {
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true)))
                .thenAnswer(invocation -> {
                    evaluatorSawTransaction[0] = TransactionSynchronizationManager.isActualTransactionActive();
                    TransactionTemplate template = new TransactionTemplate(transactionManager);
                    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    template.execute(status -> {
                        PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
                        attempt.setAnswersJson("{\"" + fixture.questionId() + "\":\"Changed after snapshot\"}");
                        attemptRepository.saveAndFlush(attempt);
                        return null;
                    });
                    return "{\"raw_score\":9.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        PracticeAttemptConflictException ex = assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluate(fixture.attemptId(), student.getId()));

        assertTrue(ex.getMessage().contains("Bài làm đã thay đổi"));
        assertFalse(evaluatorSawTransaction[0]);
        PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
        assertEquals("GRADED", attempt.getStatus());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
        assertEquals(objectMapper.readTree(fixture.oldFeedbackJson()), objectMapper.readTree(attempt.getAiFeedbackJson()));
        assertTrue(attempt.getAnswersJson().contains("Changed after snapshot"));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingQuestionReEvaluateEndpointUsesQuestionIdParameter() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate Writing", true);
        try {
            String beforeAnswersJson = attemptRepository.findById(fixture.attemptId()).orElseThrow().getAnswersJson();
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), eq("Existing answer"), eq(true)))
                    .thenReturn("{\"raw_score\":9.0,\"raw_score_max\":10.0,\"summary\":\"target only\",\"rubric_scores\":[]}");

            mockMvc.perform(post("/practice/attempts/" + fixture.attemptId() + "/re-evaluate")
                            .with(csrf())
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/practice/attempts/" + fixture.attemptId() + "/result"));

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals("GRADED", attempt.getStatus());
            assertEquals(beforeAnswersJson, attempt.getAnswersJson());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(90.00)));
            JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
            assertEquals("target only", feedback.get(String.valueOf(fixture.questionId())).path("summary").asText());
            verify(writingEvaluationClient, times(1))
                    .evaluate(eq(student.getId()), eq(fixture.prompt()), eq("Existing answer"), eq(true));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingQuestionReEvaluateStaleAnswersConflictPreservesOldResultAndRunsOutsideTransaction() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate Stale Answers", true);
        final boolean[] evaluatorSawTransaction = {true};
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true)))
                    .thenAnswer(invocation -> {
                        evaluatorSawTransaction[0] = TransactionSynchronizationManager.isActualTransactionActive();
                        TransactionTemplate template = new TransactionTemplate(transactionManager);
                        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        template.execute(status -> {
                            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
                            attempt.setAnswersJson("{\"" + fixture.questionId() + "\":\"Changed after snapshot\"}");
                            attemptRepository.saveAndFlush(attempt);
                            return null;
                        });
                        return "{\"raw_score\":9.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                    });

            assertThrows(PracticeAttemptConflictException.class,
                    () -> practiceService.reEvaluateQuestion(fixture.attemptId(), fixture.questionId(), student.getId()));

            assertFalse(evaluatorSawTransaction[0]);
            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals("GRADED", attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
            assertEquals(objectMapper.readTree(fixture.oldFeedbackJson()), objectMapper.readTree(attempt.getAiFeedbackJson()));
            assertTrue(attempt.getAnswersJson().contains("Changed after snapshot"));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingQuestionReEvaluateAfterDiscardDoesNotRecreateAttempt() {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate Discard", true);
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true)))
                    .thenAnswer(invocation -> {
                        TransactionTemplate template = new TransactionTemplate(transactionManager);
                        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        template.execute(status -> {
                            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
                            attemptRepository.delete(attempt);
                            attemptRepository.flush();
                            return null;
                        });
                        return "{\"raw_score\":9.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                    });

            assertThrows(jakarta.persistence.EntityNotFoundException.class,
                    () -> practiceService.reEvaluateQuestion(fixture.attemptId(), fixture.questionId(), student.getId()));

            assertTrue(attemptRepository.findById(fixture.attemptId()).isEmpty());
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testConcurrentWritingQuestionReEvaluateSameTargetOnlyOneCommit() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Concurrent Question Reevaluate Writing", true);
        CyclicBarrier evaluatorBarrier = new CyclicBarrier(2);
        AtomicInteger evaluatorCalls = new AtomicInteger();
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true)))
                .thenAnswer(invocation -> {
                    evaluatorCalls.incrementAndGet();
                    evaluatorBarrier.await(5, TimeUnit.SECONDS);
                    return "{\"raw_score\":9.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> reEvaluateQuestion = () -> {
                try {
                    return practiceService.reEvaluateQuestion(fixture.attemptId(), fixture.questionId(), student.getId());
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Object> first = executor.submit(reEvaluateQuestion);
            Future<Object> second = executor.submit(reEvaluateQuestion);
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            long successes = List.of(firstResult, secondResult).stream()
                    .filter(result -> result instanceof Long)
                    .count();
            long conflicts = List.of(firstResult, secondResult).stream()
                    .filter(result -> result instanceof PracticeAttemptConflictException)
                    .count();

            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(2, evaluatorCalls.get());

            PracticeAttempt finalAttempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals("GRADED", finalAttempt.getStatus());
            assertEquals(0, finalAttempt.getScore().compareTo(BigDecimal.valueOf(90.00)));
            JsonNode feedback = objectMapper.readTree(finalAttempt.getAiFeedbackJson());
            assertTrue(feedback.has(String.valueOf(fixture.questionId())));
            assertEquals(9.0, feedback.get(String.valueOf(fixture.questionId())).path("raw_score").asDouble());
        } finally {
            executor.shutdownNow();
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testRestRouteReturns404() throws Exception {
        mockMvc.perform(get("/practice/attempts/1/rest").param("nextSectionIndex", "1"))
                .andExpect(status().isNotFound());
    }

    private WritingAttemptFixture createWritingAttemptFixture(String title, boolean graded) {
        PracticeSet writingSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(writingSet.getId(), "Writing Section", "WRITING", "ESSAY", "Desc", 50, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(writingSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion question = new PracticeQuestion(writingSet.getId(), 51, "ESSAY", "Prompt " + title, "[]", "", "Explain", BigDecimal.TEN, 0);
        question.setGroupId(group.getId());
        question = questionRepository.saveAndFlush(question);

        PracticeAttempt attempt = new PracticeAttempt(student.getId(), writingSet.getId(), test.getId(), "WRITING", section.getId());
        String answersJson = "{\"" + question.getId() + "\":\"Existing answer\"}";
        String oldFeedbackJson = "{\"" + question.getId() + "\":{\"raw_score\":8.0,\"raw_score_max\":10.0}}";
        if (graded) {
            attempt.markGraded(BigDecimal.valueOf(80.00), BigDecimal.TEN, answersJson, oldFeedbackJson);
        } else {
            attempt.setStatus("IN_PROGRESS");
        }
        attempt = attemptRepository.saveAndFlush(attempt);
        return new WritingAttemptFixture(
                writingSet.getId(),
                test.getId(),
                section.getId(),
                group.getId(),
                question.getId(),
                attempt.getId(),
                question.getPrompt(),
                oldFeedbackJson
        );
    }

    private void deleteWritingAttemptFixture(WritingAttemptFixture fixture) {
        attemptRepository.findById(fixture.attemptId()).ifPresent(attemptRepository::delete);
        questionRepository.findById(fixture.questionId()).ifPresent(questionRepository::delete);
        groupRepository.findById(fixture.groupId()).ifPresent(groupRepository::delete);
        sectionRepository.findById(fixture.sectionId()).ifPresent(sectionRepository::delete);
        testRepository.findById(fixture.testId()).ifPresent(testRepository::delete);
        setRepository.findById(fixture.setId()).ifPresent(setRepository::delete);
    }

    private record WritingAttemptFixture(
            Long setId,
            Long testId,
            Long sectionId,
            Long groupId,
            Long questionId,
            Long attemptId,
            String prompt,
            String oldFeedbackJson
    ) {
    }
}
