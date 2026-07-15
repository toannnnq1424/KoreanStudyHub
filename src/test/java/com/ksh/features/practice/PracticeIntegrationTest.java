package com.ksh.features.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.entities.ClassEntity;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningExplanationClient;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.ai.writing.WritingEvaluationClient;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.QuestionExplanationCacheRepository;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaCleanupTaskRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.service.PracticeAttemptConflictException;
import com.ksh.features.practice.service.PracticeAttemptDiscardService;
import com.ksh.features.practice.service.PracticeDetailPageService;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import com.ksh.features.practice.service.PracticeService;
import com.ksh.features.practice.manage.service.PracticePublisherService;
import com.ksh.features.practice.manage.service.PublishedPracticeGraphMutationBlockedException;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetTestCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSkillAttemptCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogBatch;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionRow;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
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
    private ClassRepository classRepository;

    @Autowired
    private PracticeQuestionRepository questionRepository;

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
    private PracticeDetailPageService detailPageService;

    @Autowired
    private PracticePublishedVersionService publishedVersionService;

    @Autowired
    private com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository;

    @Autowired
    private PracticeAttemptDiscardService attemptDiscardService;

    @Autowired
    private PracticePublisherService publisherService;

    @Autowired
    private PracticeSpeakingMediaRepository speakingMediaRepository;

    @Autowired
    private PracticeSpeakingMediaCleanupTaskRepository cleanupTaskRepository;

    @Autowired
    private DataSource dataSource;

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
    private WritingEvaluationClient writingEvaluationClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ReadingListeningExplanationClient readingListeningExplanationClient;

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
        when(writingEvaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
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
        defaultSection = new PracticeSection(practiceSet.getId(), "Phần Đọc", "READING", "SINGLE_CHOICE", "Đọc kỹ", 40, BigDecimal.TEN, 1);
        defaultSection.setTestId(defaultTest.getId());
        defaultSection = sectionRepository.saveAndFlush(defaultSection);

        // Seed a question for the set
        question = new PracticeQuestion(
                practiceSet.getId(),
                1,
                "SINGLE_CHOICE",
                "Câu hỏi 1",
                "[\"Đáp án A\", \"Đáp án B\"]",
                "1",
                "Giải thích đáp án đúng",
                BigDecimal.valueOf(2.5),
                0
        );
        question.setGroupId(null);
        question = questionRepository.saveAndFlush(question);

        publishVersion(practiceSet.getId());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testIndexAuthenticated() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(model().attributeExists("catalog"))
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    PracticeCatalogCard card = catalog.items().stream()
                            .filter(item -> practiceSet.getId().equals(item.id()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(card.testCount()).isEqualTo(1);
                    assertThat(card.completedTests()).isZero();
                    assertThat(card.state()).isEqualTo("NOT_STARTED");
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0/1")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void indexCountsARealTestOnlyAfterEverySectionIsCompleted() throws Exception {
        PracticeSection listeningSection = new PracticeSection(
                practiceSet.getId(), "Phần Nghe", "LISTENING", "SINGLE_CHOICE",
                "Nghe kỹ", 40, BigDecimal.TEN, 2);
        listeningSection.setTestId(defaultTest.getId());
        listeningSection = sectionRepository.saveAndFlush(listeningSection);

        PracticeAttempt readingAttempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(),
                "READING", defaultSection.getId());
        readingAttempt.markSubmitted(BigDecimal.TEN, BigDecimal.TEN, "{}");
        attemptRepository.saveAndFlush(readingAttempt);

        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    PracticeCatalogCard card = catalog.items().stream()
                            .filter(item -> practiceSet.getId().equals(item.id()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(card.completedTests()).isZero();
                });

        PracticeAttempt listeningAttempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(),
                "LISTENING", listeningSection.getId());
        listeningAttempt.markGraded(BigDecimal.TEN, BigDecimal.TEN, "{}", "{}");
        attemptRepository.saveAndFlush(listeningAttempt);

        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    PracticeCatalogCard card = catalog.items().stream()
                            .filter(item -> practiceSet.getId().equals(item.id()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(card.completedTests()).isEqualTo(1);
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1/1")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void catalogFiltersOnServerAndLoadsTheNextBoundedBatchAsAFragment() throws Exception {
        String marker = "Lazy catalog " + System.nanoTime();
        for (int index = 1; index <= 13; index++) {
            setRepository.saveAndFlush(new PracticeSet(
                    marker + " " + index,
                    "Bộ đề kiểm tra lazy loading",
                    PracticeSet.SKILL_READING,
                    PracticeSet.SCOPE_GLOBAL,
                    null,
                    null,
                    "{}",
                    PracticeSet.STATUS_PUBLISHED,
                    lecturer.getId()));
        }

        mockMvc.perform(get("/practice")
                        .param("q", marker)
                        .param("skill", PracticeSet.SKILL_READING))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    assertThat(catalog.search()).isEqualTo(marker);
                    assertThat(catalog.skill()).isEqualTo(PracticeSet.SKILL_READING);
                    assertThat(catalog.items()).hasSize(12);
                    assertThat(catalog.totalElements()).isEqualTo(13);
                    assertThat(catalog.hasMore()).isTrue();
                    assertThat(catalog.nextBatch()).isEqualTo(1);
                });

        mockMvc.perform(get("/practice/catalog")
                        .param("q", marker)
                        .param("skill", PracticeSet.SKILL_READING)
                        .param("batch", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/fragments/catalog-cards :: cards"))
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    assertThat(catalog.items()).hasSize(1);
                    assertThat(catalog.hasMore()).isFalse();
                });

        mockMvc.perform(get("/practice")
                        .param("q", marker)
                        .param("skill", PracticeSet.SKILL_SPEAKING))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    assertThat(catalog.items()).isEmpty();
                    assertThat(catalog.totalElements()).isZero();
                });
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void unrelatedClassSetIsHiddenFromCatalogAndDirectLearnerRoutes() throws Exception {
        ClassEntity unrelatedClass = classRepository.saveAndFlush(new ClassEntity(
                "Lớp riêng " + System.nanoTime(),
                lecturer.getId(),
                lecturer.getId(),
                null,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                30));
        String title = "Bộ đề lớp riêng " + System.nanoTime();
        PracticeSet classSet = setRepository.saveAndFlush(new PracticeSet(
                title,
                "Chỉ thành viên lớp được xem",
                PracticeSet.SKILL_READING,
                PracticeSet.SCOPE_CLASS,
                unrelatedClass.getId(),
                null,
                "{}",
                PracticeSet.STATUS_PUBLISHED,
                lecturer.getId()));

        mockMvc.perform(get("/practice").param("q", title))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    PracticeCatalogBatch catalog = (PracticeCatalogBatch) result.getModelAndView()
                            .getModel().get("catalog");
                    assertThat(catalog.items()).isEmpty();
                })
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-set-id=\"" + classSet.getId() + "\""))));

        mockMvc.perform(get("/practice/sets/" + classSet.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSetDetailView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/set-detail"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attributeExists("testCards", "setSkills"))
                .andExpect(model().attributeDoesNotExist("submissions"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void setDetailLinksUseActualPracticeTestIds() throws Exception {
        PracticeTest secondTest = testRepository.saveAndFlush(
                new PracticeTest(practiceSet.getId(), "Test riêng", "Bài luyện thứ hai", 2, 30));

        mockMvc.perform(get("/practice/sets/" + practiceSet.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/practice/sets/" + practiceSet.getId() + "/tests/" + secondTest.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "testId=" + practiceSet.getId() + ")"))));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSetDetailUsesPerTestProgressAndIgnoresOtherUsersAndSets() throws Exception {
        PracticeAttempt currentUserAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        currentUserAttempt.markSubmitted(BigDecimal.valueOf(8.5), BigDecimal.TEN, "{\"" + question.getId() + "\":\"1\"}");
        attemptRepository.saveAndFlush(currentUserAttempt);

        PracticeAttempt otherUserAttempt = new PracticeAttempt(lecturer.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        otherUserAttempt.markSubmitted(BigDecimal.valueOf(9.5), BigDecimal.TEN, "{}");
        attemptRepository.saveAndFlush(otherUserAttempt);

        PracticeSet otherSet = setRepository.saveAndFlush(new PracticeSet(
                "Other Set", "Desc", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()));
        PracticeAttempt otherSetAttempt = new PracticeAttempt(student.getId(), otherSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        otherSetAttempt.markSubmitted(BigDecimal.valueOf(7.5), BigDecimal.TEN, "{}");
        attemptRepository.saveAndFlush(otherSetAttempt);

        PracticeAttempt activeAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attemptRepository.saveAndFlush(activeAttempt);

        PracticeAttempt newestCurrentUserAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        newestCurrentUserAttempt.markGraded(BigDecimal.valueOf(9.0), BigDecimal.TEN, "{\"" + question.getId() + "\":\"1\"}", "{}");
        attemptRepository.saveAndFlush(newestCurrentUserAttempt);

        mockMvc.perform(get("/practice/sets/" + practiceSet.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/set-detail"))
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<PracticeSetTestCard> cards = (List<PracticeSetTestCard>)
                            result.getModelAndView().getModel().get("testCards");
                    assertThat(cards).singleElement().satisfies(card -> {
                        assertThat(card.id()).isEqualTo(defaultTest.getId());
                        assertThat(card.completedSkillCount()).isEqualTo(1);
                        assertThat(card.totalSkillCount()).isEqualTo(1);
                        assertThat(card.state()).isEqualTo("IN_PROGRESS");
                        assertThat(card.resumeAttemptId()).isEqualTo(activeAttempt.getId());
                    });
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1/1 kỹ năng")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testLegacySubmissionResultRedirectStillTargetsAttemptRoute() throws Exception {
        mockMvc.perform(get("/practice/submissions/123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/123/result"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testTestDetailView() throws Exception {
        PracticeAttempt oldest = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(),
                "READING", defaultSection.getId());
        oldest.markSubmitted(BigDecimal.valueOf(6), BigDecimal.TEN, "{}");
        attemptRepository.saveAndFlush(oldest);

        PracticeAttempt middle = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(),
                "READING", defaultSection.getId());
        middle.markSubmitted(BigDecimal.valueOf(7), BigDecimal.TEN, "{}");
        attemptRepository.saveAndFlush(middle);

        PracticeAttempt newest = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(),
                "READING", defaultSection.getId());
        newest.markGraded(BigDecimal.valueOf(9), BigDecimal.TEN, "{}", "{}");
        attemptRepository.saveAndFlush(newest);

        PracticeAttempt inProgress = attemptRepository.saveAndFlush(new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(),
                "READING", defaultSection.getId()));

        mockMvc.perform(get("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/test-detail"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attribute("selectedTest", new com.ksh.features.practice.dto.PracticeDtos.PracticeTestRow(
                        defaultTest.getId(), practiceSet.getId(), defaultTest.getTitle(), defaultTest.getDescription(),
                        defaultTest.getDisplayOrder(), defaultTest.getEstimatedMinutes())))
                .andExpect(model().attributeExists("skillCards", "speakingMediaUploadEnabled"))
                .andExpect(model().attributeDoesNotExist("sections", "submissions", "inProgressAttempts"))
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<PracticeSkillAttemptCard> cards = (List<PracticeSkillAttemptCard>)
                            result.getModelAndView().getModel().get("skillCards");
                    assertThat(cards).singleElement().satisfies(card -> {
                        assertThat(card.sectionId()).isEqualTo(defaultSection.getId());
                        assertThat(card.inProgressAttemptId()).isEqualTo(inProgress.getId());
                        assertThat(card.completedAttempts()).hasSize(3);
                        assertThat(card.completedAttempts())
                                .extracting(attempt -> attempt.initiallyVisible())
                                .containsExactly(true, true, false);
                        assertThat(card.completedAttempts().get(0).id()).isEqualTo(newest.getId());
                        assertThat(card.completedAttempts().get(2).id()).isEqualTo(oldest.getId());
                        assertThat(card.latestScoreLabel()).isEqualTo("9/10");
                        assertThat(card.hiddenAttemptCount()).isEqualTo(1);
                    });
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-attempt-toggle")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/practice/attempts/" + newest.getId() + "/result")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void legacyModeRedirectsToSetDetail() throws Exception {
        mockMvc.perform(get("/practice/" + practiceSet.getId() + "/mode"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/sets/" + practiceSet.getId()));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void legacyRoomRedirectsToSetDetail() throws Exception {
        mockMvc.perform(get("/practice/" + practiceSet.getId() + "/room"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/sets/" + practiceSet.getId()));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void resultBackLinkUsesAttemptTestId() throws Exception {
        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId() + "/attempts")
                        .with(csrf())
                        .param("sectionId", String.valueOf(defaultSection.getId()))
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection());

        PracticeAttempt attempt = attemptRepository.findAll().get(0);
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param("answer_" + question.getId(), "1"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/practice/sets/" + practiceSet.getId() + "/tests/" + defaultTest.getId())));
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

        Long lockedQuestionId = question.getId();
        questionRepository.delete(question);
        questionRepository.flush();
        PracticeQuestion replacement = new PracticeQuestion(
                practiceSet.getId(), 1, "SINGLE_CHOICE", "Live prompt must not replace locked content",
                "[\"Live A\",\"Live B\"]", "2", "Live explanation",
                BigDecimal.valueOf(2.5), 0);
        questionRepository.saveAndFlush(replacement);

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                        get("/practice/attempts/" + attempt.getId()).param("mode", "exam"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/player"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attribute("mode", "exam"))
                .andReturn();

        PracticeSetView playerView = (PracticeSetView) result.getModelAndView().getModel().get("view");
        PracticeQuestionRow deliveredQuestion = playerView.groups().get(0).questions().get(0);
        assertThat(deliveredQuestion.id()).isEqualTo(lockedQuestionId);
        assertThat(deliveredQuestion.prompt()).isEqualTo("Câu hỏi 1");
        assertThat(deliveredQuestion.answerKey()).isNull();
        assertThat(deliveredQuestion.explanation()).isNull();
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
                  "evidenceQuote": "văn bản nguồn",
                  "correctReasonVi": "reason",
                  "relatedTranslationVi": "translation",
                  "eliminatedOptions": [
                    {"optionKey": "2", "reasonVi": "wrong"}
                  ]
                }
                """;
        PracticeQuestionGroup evidenceGroup = new PracticeQuestionGroup(
                practiceSet.getId(), "Đoạn văn", 1, 1,
                "Đây là văn bản nguồn đã được giáo viên duyệt.", null, null, 1);
        evidenceGroup.setSectionId(defaultSection.getId());
        evidenceGroup = groupRepository.saveAndFlush(evidenceGroup);
        question.setGroupId(evidenceGroup.getId());
        question = questionRepository.saveAndFlush(question);

        when(readingListeningExplanationClient.explain(any(ExplanationContext.class)))
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
                .explain(any(ExplanationContext.class));
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

    private void publishVersion(Long setId) {
        publishedVersionService.createPublishedVersion(setId, lecturer.getId());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSubmitWritingAttemptAndGetResult() throws Exception {
        // Seed a published WRITING set
        PracticeSet writingSetSeed = new PracticeSet(
                "TOPIK II - Viết 35",
                "Mô tả đề thi viết TOPIK II kì 35",
                "WRITING",
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
        writingQuestion.setWritingTaskType(WritingTaskType.Q51);
        writingQuestion.setGroupId(null);
        questionRepository.saveAndFlush(writingQuestion);
        publishVersion(writingSet.getId());

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
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("activePartObj.question.writingFeedback || {}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("const rawAiFeedbackJson = \"{}\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"writingFeedback\\\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"raw_score\\\":8.0")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\\\"feedbackNode\\\""))));

        // Perform GET detailed result view -> should redirect to result-detail template for WRITING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result-detail"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("currentQ.writingFeedback || {}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("const rawAiFeedbackJson = \"{}\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"writingFeedback\\\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"raw_score\\\":8.0")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\\\"feedbackNode\\\""))));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testWritingEvaluationRunsOutsideActiveTransaction() throws Exception {
        PracticeSet writingSet = setRepository.saveAndFlush(new PracticeSet(
                "Writing Transaction Boundary",
                "Desc",
                "WRITING",
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
        writingQuestion.setWritingTaskType(WritingTaskType.Q51);
        writingQuestion = questionRepository.saveAndFlush(writingQuestion);
        publishVersion(writingSet.getId());

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
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq("Prompt"), anyString(), eq(false), any()))
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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testNonWritingEssaySubmitRunsEvaluatorOutsideTransactionAndKeepsLegacyShape() throws Exception {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Reading Essay Submit Boundary", false, true);
        final boolean[] evaluatorSawTransaction = {true};
        try {
            String feedback = "{\"score\":7.0,\"overall_score\":7.0,\"raw_score\":7.0,\"raw_score_max\":10.0,\"task_type\":\"GENERAL\"}";
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(false), any()))
                    .thenAnswer(invocation -> {
                        evaluatorSawTransaction[0] = TransactionSynchronizationManager.isActualTransactionActive();
                        return feedback;
                    });

            Long result = practiceService.submitAttempt(
                    fixture.attemptId(),
                    student.getId(),
                    Map.of(
                            "answer_" + fixture.mcqQuestionId(), "1",
                            "answer_" + fixture.essayQuestionId(), "Essay answer"
                    ));

            assertEquals(fixture.attemptId(), result);
            assertFalse(evaluatorSawTransaction[0]);
            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_GRADED, attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(77.78)));
            assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(15)));
            assertEquals(objectMapper.readTree(feedback), objectMapper.readTree(attempt.getAiFeedbackJson()));
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testNonWritingEssayReEvaluateRunsEvaluatorOutsideTransaction() throws Exception {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Reading Essay Reevaluate Boundary", true, true);
        final boolean[] evaluatorSawTransaction = {true};
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(true), any()))
                    .thenAnswer(invocation -> {
                        evaluatorSawTransaction[0] = TransactionSynchronizationManager.isActualTransactionActive();
                        return "{\"score\":8.0,\"overall_score\":8.0,\"raw_score\":8.0,\"raw_score_max\":10.0}";
                    });

            practiceService.reEvaluate(fixture.attemptId(), student.getId());

            assertFalse(evaluatorSawTransaction[0]);
            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_GRADED, attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(88.89)));
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates() throws Exception {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Legacy Speaking Essay", false, true, "SPEAKING");
        String submittedFeedback = "{\"score\":7.0,\"percentage\":77.78,\"summary_vi\":\"Legacy speaking essay\"}";
        try {
            when(writingEvaluationClient.evaluate(
                    eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(false), any()))
                    .thenReturn(submittedFeedback);

            practiceService.submitAttempt(fixture.attemptId(), student.getId(), Map.of(
                    "answer_" + fixture.mcqQuestionId(), "1",
                    "answer_" + fixture.essayQuestionId(), "Legacy essay answer"));

            PracticeAttempt submitted = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(objectMapper.readTree(submittedFeedback), objectMapper.readTree(submitted.getAiFeedbackJson()));
            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Legacy speaking essay")));

            when(writingEvaluationClient.evaluate(
                    eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(true), any()))
                    .thenReturn("{\"score\":8.0,\"percentage\":88.89,\"summary_vi\":\"Re-evaluated legacy essay\"}");
            practiceService.reEvaluate(fixture.attemptId(), student.getId());

            PracticeAttempt reEvaluated = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertTrue(reEvaluated.getAiFeedbackJson().contains("Re-evaluated legacy essay"));
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testNonWritingEssaySubmitEvaluatorFailureDoesNotMutateAttempt() {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Reading Essay Submit Failure Boundary", false, true);
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(false), any()))
                    .thenThrow(new RuntimeException("provider unavailable"));

            assertThrows(RuntimeException.class, () -> practiceService.submitAttempt(
                    fixture.attemptId(),
                    student.getId(),
                    Map.of(
                            "answer_" + fixture.mcqQuestionId(), "1",
                            "answer_" + fixture.essayQuestionId(), "Essay answer"
                    )));

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_IN_PROGRESS, attempt.getStatus());
            assertEquals("{}", attempt.getAnswersJson());
            assertNull(attempt.getAiFeedbackJson());
            assertNull(attempt.getScore());
            assertNull(attempt.getTotalPoints());
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testNonWritingMcqOnlySubmitDoesNotCallWritingEvaluator() {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Reading MCQ Only", false, false);
        try {
            practiceService.submitAttempt(
                    fixture.attemptId(),
                    student.getId(),
                    Map.of("answer_" + fixture.mcqQuestionId(), "1"));

            verify(writingEvaluationClient, never()).evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any());
            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_SUBMITTED, attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(5)));
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
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
                practiceSet.getId(), "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 2);
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
        mockMvc.perform(get("/practice/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/practice/attempts/" + otherUserAttempt.getId()))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pp-empty-state")));

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
                "Draft test", "Desc", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson
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
                "Lock Test", "Desc", "GLOBAL", null, "DRAFT", lecturer.getId(), "{}"
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
                "Học liệu gốc", "Desc", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson
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
    void restoreRevisionWithLearnerAttemptBlocksBeforeGraphMutation() {
        PracticeAttempt attempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attemptRepository.saveAndFlush(attempt);
        com.ksh.entities.PracticeEditLog log = createRestoreLog(practiceSet.getId(), "unsafe restore");
        long logCountBefore = editLogRepository.findBySetIdOrderByEditedAtDesc(practiceSet.getId()).size();
        List<PracticeQuestion> questionsBefore = questionRepository.findBySetIdOrderByDisplayOrderAsc(practiceSet.getId());
        String titleBefore = setRepository.findById(practiceSet.getId()).orElseThrow().getTitle();

        assertThrows(
                com.ksh.features.practice.manage.service.PublishedPracticeGraphMutationBlockedException.class,
                () -> revisionService.restoreRevision(log.getId(), lecturer.getId())
        );

        assertThat(questionRepository.findBySetIdOrderByDisplayOrderAsc(practiceSet.getId()))
                .extracting(PracticeQuestion::getId)
                .containsExactlyElementsOf(questionsBefore.stream().map(PracticeQuestion::getId).toList());
        assertThat(setRepository.findById(practiceSet.getId()).orElseThrow().getTitle()).isEqualTo(titleBefore);
        assertThat(editLogRepository.findBySetIdOrderByEditedAtDesc(practiceSet.getId())).hasSize((int) logCountBefore);
    }

    @Test
    void attemptHistoryExistenceIncludesEveryAttemptStatus() {
        for (String status : List.of(
                PracticeAttempt.STATUS_IN_PROGRESS,
                PracticeAttempt.STATUS_SUBMITTED,
                PracticeAttempt.STATUS_GRADED,
                PracticeAttempt.STATUS_DISCARDED
        )) {
            attemptRepository.deleteAll();
            PracticeAttempt attempt = new PracticeAttempt(
                    student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
            if (PracticeAttempt.STATUS_DISCARDED.equals(status)) {
                attempt.discard(java.time.LocalDateTime.now());
            } else {
                attempt.setStatus(status);
            }
            attemptRepository.saveAndFlush(attempt);

            assertThat(attemptRepository.existsBySetId(practiceSet.getId())).isTrue();
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void setPessimisticLockBlocksSecondDatabaseTransactionUntilCommit() throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        AtomicReference<Connection> firstConnection = new AtomicReference<>();
        AtomicReference<Connection> secondConnection = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> requiresNewTransaction().executeWithoutResult(status -> {
                firstConnection.set(DataSourceUtils.getConnection(dataSource));
                setRepository.findByIdForUpdate(practiceSet.getId()).orElseThrow();
                firstLockAcquired.countDown();
                awaitLatch(releaseFirstLock);
            }));
            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> requiresNewTransaction().executeWithoutResult(status -> {
                secondConnection.set(DataSourceUtils.getConnection(dataSource));
                setRepository.findByIdForUpdate(practiceSet.getId()).orElseThrow();
            }));

            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            releaseFirstLock.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertNotSame(firstConnection.get(), secondConnection.get());
        } finally {
            releaseFirstLock.countDown();
            shutdownExecutor(executor);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void startWinsAgainstRepublishAndVersionLockedAttemptAllowsNewVersion() throws Exception {
        com.ksh.entities.PracticeDraft draft = createRepublishDraft(practiceSet.getId(), "Versioned republish");
        List<Long> questionIdsBefore = questionIds(practiceSet.getId());
        int versionCountBefore = publishedVersionRepository
                .findBySetIdOrderByVersionNumberDesc(practiceSet.getId()).size();
        Long attemptVersionBeforeRepublish = publishedVersionRepository
                .findFirstBySetIdOrderByVersionNumberDesc(practiceSet.getId()).orElseThrow().getId();
        CountDownLatch attemptCreated = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> start = executor.submit(() -> requiresNewTransaction().execute(status -> {
                Long attemptId = practiceService.startAttempt(
                        practiceSet.getId(), defaultTest.getId(), defaultSection.getId(), student.getId());
                attemptRepository.flush();
                attemptCreated.countDown();
                awaitLatch(releaseStart);
                return attemptId;
            }));
            assertTrue(attemptCreated.await(5, TimeUnit.SECONDS));

            Future<Long> republish = executor.submit(() -> publisherService.publish(draft.getId(), lecturer.getId()));
            assertThrows(TimeoutException.class, () -> republish.get(250, TimeUnit.MILLISECONDS));
            releaseStart.countDown();

            Long attemptId = start.get(5, TimeUnit.SECONDS);
            assertEquals(practiceSet.getId(), republish.get(15, TimeUnit.SECONDS));

            PracticeAttempt lockedAttempt = attemptRepository.findById(attemptId).orElseThrow();
            assertThat(lockedAttempt.getPublishedVersionId()).isEqualTo(attemptVersionBeforeRepublish);
            assertThat(lockedAttempt.getSetVersionId()).isNotNull();
            assertThat(lockedAttempt.getTestVersionId()).isNotNull();
            assertThat(lockedAttempt.getSectionVersionId()).isNotNull();
            assertThat(publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(practiceSet.getId()))
                    .hasSize(versionCountBefore + 1);
            assertThat(publishedVersionRepository.findFirstBySetIdOrderByVersionNumberDesc(practiceSet.getId()))
                    .get()
                    .extracting(com.ksh.entities.PracticePublishedVersion::getId)
                    .isNotEqualTo(attemptVersionBeforeRepublish);
            assertThat(questionIds(practiceSet.getId())).isNotEqualTo(questionIdsBefore);
        } finally {
            releaseStart.countDown();
            shutdownExecutor(executor);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void restoreWithoutAttemptsReplacesLiveGraph() {
        Long oldSectionId = defaultSection.getId();
        Long restoreLogId = createRestoreLog(practiceSet.getId(), "Restored graph").getId();
        revisionService.restoreRevision(restoreLogId, lecturer.getId());

        assertFalse(sectionRepository.existsById(oldSectionId));
        assertThat(sectionRepository.findBySetIdOrderByDisplayOrderAsc(practiceSet.getId()))
                .singleElement()
                .extracting(PracticeSection::getTitle)
                .isEqualTo("Restored section");
        assertThat(questionRepository.findBySetIdOrderByDisplayOrderAsc(practiceSet.getId()))
                .singleElement()
                .extracting(PracticeQuestion::getPrompt)
                .isEqualTo("Restored prompt");
        assertFalse(attemptRepository.existsBySetId(practiceSet.getId()));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void resumeWinsAgainstRepublishAndPinsExistingAttemptBeforeNewVersion() throws Exception {
        Long existingAttemptId = practiceService.startAttempt(
                practiceSet.getId(), defaultTest.getId(), defaultSection.getId(), student.getId());
        PracticeAttempt existing = attemptRepository.findById(existingAttemptId).orElseThrow();
        com.ksh.entities.PracticeDraft draft = createRepublishDraft(practiceSet.getId(), "Versioned resume republish");
        int versionCountBefore = publishedVersionRepository
                .findBySetIdOrderByVersionNumberDesc(practiceSet.getId()).size();
        CountDownLatch resumed = new CountDownLatch(1);
        CountDownLatch releaseResume = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> resume = executor.submit(() -> requiresNewTransaction().execute(status -> {
                Long attemptId = practiceService.startAttempt(
                        practiceSet.getId(), defaultTest.getId(), defaultSection.getId(), student.getId());
                resumed.countDown();
                awaitLatch(releaseResume);
                return attemptId;
            }));
            assertTrue(resumed.await(5, TimeUnit.SECONDS));

            Future<Long> republish = executor.submit(() -> publisherService.publish(draft.getId(), lecturer.getId()));
            assertThrows(TimeoutException.class, () -> republish.get(250, TimeUnit.MILLISECONDS));
            releaseResume.countDown();

            assertEquals(existing.getId(), resume.get(5, TimeUnit.SECONDS));
            assertEquals(practiceSet.getId(), republish.get(15, TimeUnit.SECONDS));
            assertEquals(1, attemptRepository.findAll().stream()
                    .filter(a -> practiceSet.getId().equals(a.getSetId()))
                    .count());
            PracticeAttempt lockedAttempt = attemptRepository.findById(existing.getId()).orElseThrow();
            assertThat(lockedAttempt.getPublishedVersionId()).isNotNull();
            assertThat(lockedAttempt.getSetVersionId()).isNotNull();
            assertThat(lockedAttempt.getTestVersionId()).isNotNull();
            assertThat(lockedAttempt.getSectionVersionId()).isNotNull();
            assertThat(publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(practiceSet.getId()))
                    .hasSize(versionCountBefore + 1);
            assertThat(publishedVersionRepository.findFirstBySetIdOrderByVersionNumberDesc(practiceSet.getId()))
                    .get()
                    .extracting(com.ksh.entities.PracticePublishedVersion::getId)
                    .isNotEqualTo(lockedAttempt.getPublishedVersionId());
        } finally {
            releaseResume.countDown();
            shutdownExecutor(executor);
        }
    }

    @Test
    void readingResultRemainsIdenticalWhenRestoreIsBlocked() {
        PracticeAttempt attempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.markSubmitted(BigDecimal.valueOf(2.5), BigDecimal.valueOf(2.5),
                "{\"" + question.getId() + "\":\"1\"}");
        attempt = attemptRepository.saveAndFlush(attempt);
        var before = practiceService.getReadingListeningResult(attempt.getId(), student.getId());
        List<Long> idsBefore = questionIds(practiceSet.getId());
        var log = createRestoreLog(practiceSet.getId(), "Unsafe reading restore");

        assertThrows(PublishedPracticeGraphMutationBlockedException.class,
                () -> revisionService.restoreRevision(log.getId(), lecturer.getId()));

        var after = practiceService.getReadingListeningResult(attempt.getId(), student.getId());
        assertEquals(before, after);
        assertEquals(question.getId(), after.groups().get(0).questions().get(0).questionId());
        assertEquals("1", after.groups().get(0).questions().get(0).userAnswer());
        assertThat(questionIds(practiceSet.getId())).containsExactlyElementsOf(idsBefore);
    }

    @Test
    void listeningResultRemainsIdenticalWhenRepublishIsBlocked() {
        ListeningAttemptFixture fixture = createListeningAttemptFixture("Listening history guard");
        var before = practiceService.getReadingListeningResult(fixture.attemptId(), student.getId());
        com.ksh.entities.PracticeDraft draft = createRepublishDraft(fixture.setId(), "Unsafe listening republish");

        assertThrows(PublishedPracticeGraphMutationBlockedException.class,
                () -> publisherService.publish(draft.getId(), lecturer.getId()));

        var after = practiceService.getReadingListeningResult(fixture.attemptId(), student.getId());
        assertEquals(before, after);
        assertEquals(fixture.questionId(), after.groups().get(0).questions().get(0).questionId());
        assertEquals("1", after.groups().get(0).questions().get(0).userAnswer());
        assertTrue(questionRepository.existsById(fixture.questionId()));
    }

    @Test
    void writingResultRemainsIdenticalWhenRestoreIsBlocked() {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Writing history guard", true);
        var before = practiceService.getResult(fixture.attemptId(), student.getId());
        var log = createRestoreLog(fixture.setId(), "Unsafe writing restore");

        assertThrows(PublishedPracticeGraphMutationBlockedException.class,
                () -> revisionService.restoreRevision(log.getId(), lecturer.getId()));

        var after = practiceService.getResult(fixture.attemptId(), student.getId());
        assertEquals(before, after);
        assertEquals(fixture.questionId(), after.questionFeedbacks().get(0).questionId());
        assertEquals(fixture.prompt(), after.questionFeedbacks().get(0).prompt());
        assertEquals("Existing answer", after.questionFeedbacks().get(0).learnerAnswer());
        assertEquals(fixture.oldFeedbackJson(), after.aiFeedbackJson());
        assertTrue(questionRepository.existsById(fixture.questionId()));
    }

    @Test
    void speakingMediaAndResultRemainIntactWhenRepublishIsBlockedBeforeForeignKeyDelete() {
        SpeakingAttemptFixture fixture = createSpeakingAttemptFixture("Speaking history guard");
        PracticeSpeakingMedia media = speakingMediaRepository.saveAndFlush(PracticeSpeakingMedia.ready(
                fixture.attemptId(), fixture.questionId(), PracticeSpeakingStorageProvider.LOCAL,
                "test/guard-" + java.util.UUID.randomUUID() + ".webm", "audio/webm", "webm", "opus",
                100L, 1000L, "a".repeat(64)));
        var before = practiceService.getResult(fixture.attemptId(), student.getId());
        com.ksh.entities.PracticeDraft draft = createRepublishDraft(fixture.setId(), "Unsafe speaking republish");
        long cleanupCountBefore = cleanupTaskRepository.count();
        long logCountBefore = editLogRepository.findBySetIdOrderByEditedAtDesc(fixture.setId()).size();
        String titleBefore = setRepository.findById(fixture.setId()).orElseThrow().getTitle();

        assertThrows(PublishedPracticeGraphMutationBlockedException.class,
                () -> publisherService.publish(draft.getId(), lecturer.getId()));

        var after = practiceService.getResult(fixture.attemptId(), student.getId());
        assertEquals(before, after);
        assertEquals(fixture.questionId(), after.speakingQuestionFeedbacks().get(0).questionId());
        assertEquals("Existing spoken answer", after.speakingQuestionFeedbacks().get(0).learnerAnswer());
        assertTrue(questionRepository.existsById(fixture.questionId()));
        PracticeSpeakingMedia unchanged = speakingMediaRepository.findById(media.getId()).orElseThrow();
        assertEquals(PracticeSpeakingMediaStatus.READY, unchanged.getStatus());
        assertEquals(fixture.questionId(), unchanged.getQuestionId());
        assertEquals(cleanupCountBefore, cleanupTaskRepository.count());
        assertEquals(logCountBefore, editLogRepository.findBySetIdOrderByEditedAtDesc(fixture.setId()).size());
        assertEquals(titleBefore, setRepository.findById(fixture.setId()).orElseThrow().getTitle());
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
        readingGroup = groupRepository.saveAndFlush(readingGroup);

        PracticeQuestionGroup writingGroup = new PracticeQuestionGroup(practiceSet.getId(), "Phần Viết", 2, 2, "Viết luận", null, null, 2);
        writingGroup.setSectionId(writingSec.getId());
        groupRepository.saveAndFlush(writingGroup);
        question.setGroupId(readingGroup.getId());
        questionRepository.saveAndFlush(question);
        publishVersion(practiceSet.getId());

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

        // Verify the canonical PracticeAttempt was created correctly.
        PracticeAttempt readingAttempt = attemptRepository.findById(readingAttemptId).orElseThrow();
        assertThat(readingAttempt.getSectionId()).isEqualTo(readingSec.getId());
        assertThat(readingAttempt.getSkill()).isEqualTo("READING");
        assertThat(readingAttempt.getTestId()).isEqualTo(test.getId());
        assertThat(readingAttempt.getStatus()).isEqualTo("IN_PROGRESS");

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
        PracticeSet anotherSet = new PracticeSet("Another", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId());
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
        PracticeAttempt attempt = new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/discard")
                        .with(csrf())
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", String.valueOf(defaultTest.getId())))
                .andExpect(status().is3xxRedirection());

        PracticeAttempt discarded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(discarded.getStatus()).isEqualTo(PracticeAttempt.STATUS_DISCARDED);
        assertThat(discarded.getDiscardedAt()).isNotNull();

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/discard")
                        .with(csrf())
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", String.valueOf(defaultTest.getId())))
                .andExpect(status().is3xxRedirection());
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getDiscardedAt())
                .isEqualTo(discarded.getDiscardedAt());

        mockMvc.perform(get("/practice/attempts/" + attempt.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isNotFound());
        assertThat(detailPageService.buildSkillCards(
                defaultTest.getId(), List.of(defaultSection), student.getId()).get(0).completedAttempts())
                .noneMatch(row -> row.id().equals(discarded.getId()));
        assertThat(practiceService.getProgressPageData(student.getId(), "Student", "")
                .overview().totalAttempts())
                .isZero();

        Long restartedId = practiceService.startAttempt(
                practiceSet.getId(), defaultTest.getId(), defaultSection.getId(), student.getId());
        assertThat(restartedId).isNotEqualTo(attempt.getId());
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testDiscardAttemptRequiresCsrf() throws Exception {
        PracticeAttempt attempt = attemptRepository.saveAndFlush(new PracticeAttempt(
                student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId()));

        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/discard")
                        .param("setId", String.valueOf(practiceSet.getId()))
                        .param("testId", String.valueOf(defaultTest.getId())))
                .andExpect(status().isForbidden());

        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(PracticeAttempt.STATUS_IN_PROGRESS);
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
    void speakingAttemptPreflightDiscardsLegacySnapshotMissingDeliveryJson() throws Exception {
        SpeakingAttemptFixture fixture = createLegacySpeakingInProgressAttempt("Legacy speaking missing delivery");

        IllegalStateException invalidDelivery = assertThrows(
                IllegalStateException.class,
                () -> practiceService.getSpeakingPlayerDelivery(fixture.attemptId(), student.getId()));
        assertThat(invalidDelivery)
                .hasMessageContaining("Speaking question is missing immutable prompt audio");

        mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/speaking-check"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/sets/" + fixture.setId()
                        + "/tests/" + fixture.testId()))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString(
                        "Nội dung Speaking này chưa có audio hoặc thời lượng hợp lệ")));

        PracticeAttempt discarded = attemptRepository.findById(fixture.attemptId()).orElseThrow();
        assertThat(discarded.getStatus()).isEqualTo(PracticeAttempt.STATUS_DISCARDED);
        assertThat(discarded.getDiscardedAt()).isNotNull();
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void listeningPreflightUnlocksImmutableAttemptOnlyForCompletedSession() throws Exception {
        PracticeSet listeningSet = setRepository.saveAndFlush(new PracticeSet(
                "Listening preflight integration", "Desc", "LISTENING", "GLOBAL",
                null, null, "{}", "PUBLISHED", lecturer.getId()));
        PracticeTest listeningTest = testRepository.saveAndFlush(new PracticeTest(
                listeningSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection listeningSection = new PracticeSection(
                listeningSet.getId(), "Phần Nghe", "LISTENING", "SINGLE_CHOICE",
                "Nghe và chọn đáp án", 40, BigDecimal.TEN, 1);
        listeningSection.setTestId(listeningTest.getId());
        listeningSection.setDeliveryJson("""
                {"schemaVersion":"practice-section-delivery-v1",
                 "listeningDelivery":{"checkAudioReference":"/practice/materials/12/content"}}
                """);
        listeningSection = sectionRepository.saveAndFlush(listeningSection);

        PracticeQuestionGroup group = new PracticeQuestionGroup(
                listeningSet.getId(), "L1.1", 1, 1, "Nghe đoạn hội thoại.",
                "/practice/materials/13/content", null, 1);
        group.setSectionId(listeningSection.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion listeningQuestion = new PracticeQuestion(
                listeningSet.getId(), 1, PracticeQuestion.TYPE_SINGLE_CHOICE,
                "Đáp án nào đúng?", "[\"A\",\"B\"]", "1", "Giải thích",
                BigDecimal.TEN, 0);
        listeningQuestion.setGroupId(group.getId());
        questionRepository.saveAndFlush(listeningQuestion);
        publishVersion(listeningSet.getId());

        String livePreflightPath = "/practice/sets/" + listeningSet.getId()
                + "/tests/" + listeningTest.getId()
                + "/sections/" + listeningSection.getId() + "/listening-check";
        MockHttpSession completedSession = new MockHttpSession();
        mockMvc.perform(get(livePreflightPath).session(completedSession))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/listening-preflight"))
                .andExpect(model().attribute(
                        "listeningCheckAudioReference", "/practice/materials/12/content"));

        String completedRedirect = mockMvc.perform(
                        post(livePreflightPath).session(completedSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        PracticeAttempt attempt = attemptRepository.findAll().stream()
                .filter(candidate -> listeningSet.getId().equals(candidate.getSetId()))
                .findFirst()
                .orElseThrow();
        String attemptPath = "/practice/attempts/" + attempt.getId();
        assertThat(completedRedirect).isEqualTo(attemptPath + "?mode=practice");
        mockMvc.perform(get(attemptPath).session(completedSession))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/player"));

        mockMvc.perform(get(attemptPath).session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(attemptPath + "/listening-check"));
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
        PracticeSet emptySet = new PracticeSet(
                "Empty Reading Detail Set", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId()
        );
        emptySet = setRepository.saveAndFlush(emptySet);

        PracticeTest emptyTest = new PracticeTest(emptySet.getId(), "Empty Test", "Desc", 1, 40);
        emptyTest = testRepository.saveAndFlush(emptyTest);

        PracticeSection emptySection = new PracticeSection(
                emptySet.getId(), "Empty Section", "READING", "DEFAULT", "Desc", 50, BigDecimal.TEN, 1
        );
        emptySection.setTestId(emptyTest.getId());
        emptySection = sectionRepository.saveAndFlush(emptySection);

        PracticeAttempt readingAttempt = new PracticeAttempt(
                student.getId(), emptySet.getId(), emptyTest.getId(), "READING", emptySection.getId());
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
                "Multi-Section Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId()
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
        qA.setWritingTaskType(WritingTaskType.Q51);
        qA.setGroupId(groupA.getId());
        qA = questionRepository.saveAndFlush(qA);

        // Group B and Question B
        PracticeQuestionGroup groupB = new PracticeQuestionGroup(writingSet.getId(), "Group B", 2, 2, "Desc", null, null, 2);
        groupB.setSectionId(sectionB.getId());
        groupB = groupRepository.saveAndFlush(groupB);

        PracticeQuestion qB = new PracticeQuestion(writingSet.getId(), 52, "ESSAY", "Prompt B", "[]", "", "Explain", BigDecimal.valueOf(20.0), 0);
        qB.setWritingTaskType(WritingTaskType.Q52);
        qB.setGroupId(groupB.getId());
        qB = questionRepository.saveAndFlush(qB);

        // Start attempt on Section A
        PracticeAttempt attempt = new PracticeAttempt(student.getId(), writingSet.getId(), test.getId(), "WRITING", sectionA.getId());
        attempt.setStatus("IN_PROGRESS");
        attempt = attemptRepository.saveAndFlush(attempt);

        // Mock evaluation client for Question A
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq("Prompt A"), anyString(), anyBoolean(), any()))
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
                "Security Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", lecturer.getId()
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
        q.setWritingTaskType(WritingTaskType.Q51);
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
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(false), any()))
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
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(false), any()))
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
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(false), any()))
                .thenAnswer(invocation -> {
                    attemptDiscardService.discardForOwner(fixture.attemptId(), student.getId());
                    return "{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}";
                });

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.submitAttempt(
                        fixture.attemptId(),
                        student.getId(),
                        Map.of("answer_" + fixture.questionId(), "Submitted answer")
                ));

        assertEquals(PracticeAttempt.STATUS_DISCARDED,
                attemptRepository.findById(fixture.attemptId()).orElseThrow().getStatus());
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testNonWritingEssaySubmitStaleAnswersConflictPreservesWinner() {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Reading Essay Submit Stale Answers", false, true);
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(false), any()))
                    .thenAnswer(invocation -> {
                        practiceService.saveInProgressAnswers(
                                fixture.attemptId(),
                                student.getId(),
                                Map.of("answer_" + fixture.essayQuestionId(), "Autosaved essay"));
                        return "{\"score\":8.0,\"overall_score\":8.0,\"raw_score\":8.0,\"raw_score_max\":10.0}";
                    });

            assertThrows(PracticeAttemptConflictException.class,
                    () -> practiceService.submitAttempt(
                            fixture.attemptId(),
                            student.getId(),
                            Map.of(
                                    "answer_" + fixture.mcqQuestionId(), "1",
                                    "answer_" + fixture.essayQuestionId(), "Submitted essay"
                            )));

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_IN_PROGRESS, attempt.getStatus());
            assertTrue(attempt.getAnswersJson().contains("Autosaved essay"));
            assertFalse(attempt.getAnswersJson().contains("Submitted essay"));
            assertNull(attempt.getAiFeedbackJson());
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingReEvaluateStaleAnswersConflictPreservesOldResultAndRunsOutsideTransaction() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Reevaluate Conflict Writing", true);
        final boolean[] evaluatorSawTransaction = {true};
        try {
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true), any()))
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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testNonWritingEssayReEvaluateStaleResultConflictPreservesWinner() throws Exception {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Reading Essay Reevaluate Stale Result", true, true);
        String winnerFeedback = "{\"score\":6.0,\"overall_score\":6.0,\"raw_score\":6.0,\"raw_score_max\":10.0}";
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(true), any()))
                    .thenAnswer(invocation -> {
                        TransactionTemplate template = new TransactionTemplate(transactionManager);
                        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        template.execute(status -> {
                            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
                            attempt.markGraded(BigDecimal.valueOf(66.67), BigDecimal.valueOf(15),
                                    attempt.getAnswersJson(), winnerFeedback);
                            attemptRepository.saveAndFlush(attempt);
                            return null;
                        });
                        return "{\"score\":9.0,\"overall_score\":9.0,\"raw_score\":9.0,\"raw_score_max\":10.0}";
                    });

            assertThrows(PracticeAttemptConflictException.class,
                    () -> practiceService.reEvaluate(fixture.attemptId(), student.getId()));

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_GRADED, attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(66.67)));
            assertEquals(objectMapper.readTree(winnerFeedback), objectMapper.readTree(attempt.getAiFeedbackJson()));
        } finally {
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingQuestionReEvaluateEndpointUsesQuestionIdParameter() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate Writing", true);
        try {
            String beforeAnswersJson = attemptRepository.findById(fixture.attemptId()).orElseThrow().getAnswersJson();
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), eq("Existing answer"), eq(true), any()))
                    .thenReturn("{\"raw_score\":9.0,\"raw_score_max\":10.0,\"summary\":\"target only\",\"rubric_scores\":[]}");

            mockMvc.perform(post("/practice/attempts/" + fixture.attemptId() + "/re-evaluate")
                            .with(csrf())
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/practice/attempts/" + fixture.attemptId() + "/result/detail?questionId=" + fixture.questionId()))
                    .andExpect(flash().attribute("success", "Đã chấm lại câu đã chọn."));

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals("GRADED", attempt.getStatus());
            assertEquals(beforeAnswersJson, attempt.getAnswersJson());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(90.00)));
            JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
            assertEquals("target only", feedback.get(String.valueOf(fixture.questionId())).path("summary").asText());
            verify(writingEvaluationClient, times(1))
                    .evaluate(eq(student.getId()), eq(fixture.prompt()), eq("Existing answer"), eq(true), any());

            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail")
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("currentQ.writingFeedback || {}")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"writingFeedback\\\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\\\"feedbackNode\\\""))))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("target only")));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingResultDetailRendersPerQuestionReEvaluateForm() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate UI", true);
        try {
            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail")
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const activeQuestionId = " + fixture.questionId())))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"questionReEvaluateForm\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("method=\"post\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/practice/attempts/" + fixture.attemptId() + "/re-evaluate")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"questionId\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Chấm lại câu này")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"writingFeedback\\\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\\\"feedbackNode\\\""))))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"reEvaluatable\\\":true")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"raw_score\\\":8.0")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const activeQuestionIndex = selectorQuestions.findIndex")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("String(question.questionId) === String(activeQuestionId)")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("setQuestion(initialQuestionIndex);")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("let reEvaluateSubmitting = false;")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("event.preventDefault();")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Đang chấm lại...")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-busy")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("reEvaluateQuestionIdInput.value = '';")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("reEvaluateButton.disabled = true;")));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Invalid Active Question UI", true);
        try {
            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail")
                            .param("questionId", "999999999"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const activeQuestionId = null")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const initialQuestionIndex = activeQuestionIndex >= 0 ? activeQuestionIndex : 0;")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("setQuestion(initialQuestionIndex);")));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingResultDetailDoesNotRestoreMcqQuestionId() throws Exception {
        WritingMixedAttemptFixture fixture = createWritingMixedAttemptFixture("MCQ Active Question UI");
        try {
            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail")
                            .param("questionId", String.valueOf(fixture.mcqQuestionId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const activeQuestionId = null")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"questionId\\\":" + fixture.mcqQuestionId())))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"questionId\\\":" + fixture.essayQuestionId())));
        } finally {
            deleteWritingMixedAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak() throws Exception {
        WritingAttemptFixture target = createWritingAttemptFixture("Target Active Question UI", true);
        WritingAttemptFixture foreign = createWritingAttemptFixture("Foreign Active Question UI", true);
        try {
            mockMvc.perform(get("/practice/attempts/" + target.attemptId() + "/result/detail")
                            .param("questionId", String.valueOf(foreign.questionId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const activeQuestionId = null")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Prompt Foreign Active Question UI"))));
        } finally {
            deleteWritingAttemptFixture(target);
            deleteWritingAttemptFixture(foreign);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingFullReEvaluateEndpointWithoutQuestionIdStillRedirectsOverview() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Full Reevaluate Regression UI", true);
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), eq("Existing answer"), eq(true), any()))
                    .thenReturn("{\"raw_score\":9.0,\"raw_score_max\":10.0,\"summary\":\"full\",\"rubric_scores\":[]}");

            mockMvc.perform(post("/practice/attempts/" + fixture.attemptId() + "/re-evaluate")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/practice/attempts/" + fixture.attemptId() + "/result"));

            verify(writingEvaluationClient, times(1))
                    .evaluate(eq(student.getId()), eq(fixture.prompt()), eq("Existing answer"), eq(true), any());
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testReadingResultDetailDoesNotRenderPerQuestionReEvaluateForm() throws Exception {
        PracticeAttempt readingAttempt = new PracticeAttempt(student.getId(), practiceSet.getId(), defaultTest.getId(), "READING", defaultSection.getId());
        readingAttempt.setStatus("SUBMITTED");
        readingAttempt = attemptRepository.saveAndFlush(readingAttempt);

        mockMvc.perform(get("/practice/attempts/" + readingAttempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form id=\"questionReEvaluateForm\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Chấm lại câu này"))));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testListeningResultDetailDoesNotRenderPerQuestionReEvaluateForm() throws Exception {
        ListeningAttemptFixture fixture = createListeningAttemptFixture("Listening Reevaluate UI");
        try {
            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/rl-result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form id=\"questionReEvaluateForm\""))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Chấm lại câu này"))));
        } finally {
            deleteListeningAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm() throws Exception {
        SpeakingAttemptFixture fixture = createSpeakingAttemptFixture("Speaking Reevaluate UI");
        try {
            assertEquals("80%", practiceService.getResult(fixture.attemptId(), student.getId()).scoreLabel());
            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Điểm luyện tập tham khảo")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form id=\"questionReEvaluateForm\""))));
        } finally {
            deleteSpeakingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testWritingQuestionReEvaluateConflictRedirectsDetailWithFlashError() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate Conflict UI", true);
        try {
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true), any()))
                    .thenAnswer(invocation -> {
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

            mockMvc.perform(post("/practice/attempts/" + fixture.attemptId() + "/re-evaluate")
                            .with(csrf())
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/practice/attempts/" + fixture.attemptId() + "/result/detail?questionId=" + fixture.questionId()))
                    .andExpect(flash().attribute("error", "Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại."));

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals("GRADED", attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
            assertEquals(objectMapper.readTree(fixture.oldFeedbackJson()), objectMapper.readTree(attempt.getAiFeedbackJson()));

            mockMvc.perform(get("/practice/attempts/" + fixture.attemptId() + "/result/detail")
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("practice/result-detail"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("const activeQuestionId = " + fixture.questionId())))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\\\"raw_score\\\":8.0")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\\\"raw_score\\\":9.0"))));
        } finally {
            deleteWritingAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testWritingQuestionReEvaluateRequiresCsrf() throws Exception {
        WritingAttemptFixture fixture = createWritingAttemptFixture("Question Reevaluate CSRF", true);
        try {
            mockMvc.perform(post("/practice/attempts/" + fixture.attemptId() + "/re-evaluate")
                            .param("questionId", String.valueOf(fixture.questionId())))
                    .andExpect(status().isForbidden());
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
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true), any()))
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
            when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true), any()))
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
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.prompt()), anyString(), eq(true), any()))
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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void testConcurrentNonWritingEssaySubmitOnlyOneCommit() throws Exception {
        NonWritingEssayAttemptFixture fixture = createNonWritingEssayAttemptFixture(
                "Concurrent Reading Essay Submit", false, true);
        CyclicBarrier evaluatorBarrier = new CyclicBarrier(2);
        AtomicInteger evaluatorCalls = new AtomicInteger();
        when(writingEvaluationClient.evaluate(eq(student.getId()), eq(fixture.essayPrompt()), anyString(), eq(false), any()))
                .thenAnswer(invocation -> {
                    evaluatorCalls.incrementAndGet();
                    evaluatorBarrier.await(5, TimeUnit.SECONDS);
                    return "{\"score\":8.0,\"overall_score\":8.0,\"raw_score\":8.0,\"raw_score_max\":10.0}";
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> submit = () -> {
                try {
                    return practiceService.submitAttempt(
                            fixture.attemptId(),
                            student.getId(),
                            Map.of(
                                    "answer_" + fixture.mcqQuestionId(), "1",
                                    "answer_" + fixture.essayQuestionId(), "Concurrent essay"
                            ));
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

            PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
            assertEquals(PracticeAttempt.STATUS_GRADED, attempt.getStatus());
            assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(88.89)));
        } finally {
            executor.shutdownNow();
            deleteNonWritingEssayAttemptFixture(fixture);
        }
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testRestRouteReturns404() throws Exception {
        mockMvc.perform(get("/practice/attempts/1/rest").param("nextSectionIndex", "1"))
                .andExpect(status().isNotFound());
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(10);
        return template;
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test coordination latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test coordination latch", e);
        }
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private <T extends Throwable> T assertFutureCause(Future<?> future, Class<T> expectedType)
            throws InterruptedException, TimeoutException {
        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS)
        );
        assertTrue(expectedType.isInstance(exception.getCause()),
                () -> "Expected " + expectedType.getSimpleName() + " but got " + exception.getCause());
        return expectedType.cast(exception.getCause());
    }

    private List<Long> questionIds(Long setId) {
        return questionRepository.findBySetIdOrderByDisplayOrderAsc(setId).stream()
                .map(PracticeQuestion::getId)
                .toList();
    }

    private com.ksh.entities.PracticeDraft createRepublishDraft(Long setId, String title) {
        String draftJson = """
                {
                  "document": {"title":"Replacement","confidence":1.0},
                  "sections": [{
                    "title":"Replacement section","skill":"READING","durationMinutes":40,
                    "groups":[{"label":"1","questionFrom":1,"questionTo":1,"instruction":"Instruction",
                      "questions":[{"questionNo":1,"questionType":"SINGLE_CHOICE","prompt":"Replacement prompt",
                        "options":["A","B"],"answer":{"value":"1"},"explanationVi":"Because","points":5.0}]
                    }]
                  }]
                }
                """;
        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                title, "Desc", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson);
        draft.setPublishedSetId(setId);
        return draftRepository.saveAndFlush(draft);
    }

    private com.ksh.entities.PracticeEditLog createRestoreLog(Long setId, String title) {
        String snapshot = """
                {
                  "document":{"title":"Restored","description":"Restored description"},
                  "sections":[{
                    "title":"Restored section","skill":"READING","durationMinutes":40,"totalPoints":5.0,
                    "groups":[{"label":"1","instruction":"Instruction",
                      "questions":[{"questionNo":1,"questionType":"SINGLE_CHOICE","prompt":"Restored prompt",
                        "options":["A","B"],"answerKey":"1","explanationVi":"Because","points":5.0}]
                    }]
                  }]
                }
                """;
        return editLogRepository.saveAndFlush(new com.ksh.entities.PracticeEditLog(
                setId, lecturer.getId(), title, "{}", snapshot, "{}", "QUESTIONS"));
    }

    private WritingAttemptFixture createWritingAttemptFixture(String title, boolean graded) {
        PracticeSet writingSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", "WRITING", "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(writingSet.getId(), "Writing Section", "WRITING", "ESSAY", "Desc", 50, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(writingSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion question = new PracticeQuestion(writingSet.getId(), 51, "ESSAY", "Prompt " + title, "[]", "", "Explain", BigDecimal.TEN, 0);
        question.setWritingTaskType(WritingTaskType.Q51);
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

    private NonWritingEssayAttemptFixture createNonWritingEssayAttemptFixture(
            String title,
            boolean graded,
            boolean includeEssay
    ) {
        return createNonWritingEssayAttemptFixture(title, graded, includeEssay, "READING");
    }

    private NonWritingEssayAttemptFixture createNonWritingEssayAttemptFixture(
            String title,
            boolean graded,
            boolean includeEssay,
            String skill
    ) {
        PracticeSet readingSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", skill, "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(readingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(readingSet.getId(), skill + " Section", skill, "MIXED", "Desc", 40, BigDecimal.valueOf(15), 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(readingSet.getId(), "Group 1", 1, 2, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);

        PracticeQuestion mcq = new PracticeQuestion(readingSet.getId(), 1, "SINGLE_CHOICE", "Prompt MCQ " + title, "[\"A\",\"B\"]", "1", "Explain", BigDecimal.valueOf(5), 0);
        mcq.setGroupId(group.getId());
        mcq = questionRepository.saveAndFlush(mcq);

        PracticeQuestion essay = null;
        if (includeEssay) {
            essay = new PracticeQuestion(readingSet.getId(), 51, "ESSAY", "Prompt Essay " + title, "[]", "", "Explain", BigDecimal.TEN, 1);
            essay.setWritingTaskType(WritingTaskType.Q51);
            essay.setGroupId(group.getId());
            essay = questionRepository.saveAndFlush(essay);
        }

        PracticeAttempt attempt;
        if ("SPEAKING".equals(skill)) {
            publishVersion(readingSet.getId());
            Long attemptId = practiceService.startAttempt(
                    readingSet.getId(), test.getId(), section.getId(), student.getId());
            attempt = attemptRepository.findById(attemptId).orElseThrow();
        } else {
            attempt = new PracticeAttempt(
                    student.getId(), readingSet.getId(), test.getId(), skill, section.getId());
        }
        String answersJson = includeEssay
                ? "{\"" + mcq.getId() + "\":\"1\",\"" + essay.getId() + "\":\"Existing essay\"}"
                : "{\"" + mcq.getId() + "\":\"1\"}";
        String oldFeedbackJson = "{\"score\":7.0,\"overall_score\":7.0,\"raw_score\":7.0,\"raw_score_max\":10.0}";
        if (graded) {
            attempt.markGraded(BigDecimal.valueOf(77.78), BigDecimal.valueOf(includeEssay ? 15 : 5),
                    answersJson, includeEssay ? oldFeedbackJson : null);
        } else {
            attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
            attempt.setAnswersJson("{}");
        }
        attempt = attemptRepository.saveAndFlush(attempt);

        return new NonWritingEssayAttemptFixture(
                readingSet.getId(),
                test.getId(),
                section.getId(),
                group.getId(),
                mcq.getId(),
                essay == null ? null : essay.getId(),
                attempt.getId(),
                essay == null ? null : essay.getPrompt()
        );
    }

    private void deleteNonWritingEssayAttemptFixture(NonWritingEssayAttemptFixture fixture) {
        attemptRepository.findById(fixture.attemptId()).ifPresent(attemptRepository::delete);
        deletePublishedVersionFixture(fixture.setId());
        if (fixture.essayQuestionId() != null) {
            questionRepository.findById(fixture.essayQuestionId()).ifPresent(questionRepository::delete);
        }
        questionRepository.findById(fixture.mcqQuestionId()).ifPresent(questionRepository::delete);
        groupRepository.findById(fixture.groupId()).ifPresent(groupRepository::delete);
        sectionRepository.findById(fixture.sectionId()).ifPresent(sectionRepository::delete);
        testRepository.findById(fixture.testId()).ifPresent(testRepository::delete);
        setRepository.findById(fixture.setId()).ifPresent(setRepository::delete);
    }

    private record NonWritingEssayAttemptFixture(
            Long setId,
            Long testId,
            Long sectionId,
            Long groupId,
            Long mcqQuestionId,
            Long essayQuestionId,
            Long attemptId,
            String essayPrompt
    ) {
    }

    private WritingMixedAttemptFixture createWritingMixedAttemptFixture(String title) {
        PracticeSet writingSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", "WRITING", "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(writingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(writingSet.getId(), "Writing Section", "WRITING", "MIXED", "Desc", 50, BigDecimal.valueOf(20), 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(writingSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);

        PracticeQuestion mcq = new PracticeQuestion(writingSet.getId(), 50, "SINGLE_CHOICE", "Prompt MCQ " + title, "[\"A\",\"B\"]", "1", "Explain", BigDecimal.TEN, 0);
        mcq.setGroupId(group.getId());
        mcq = questionRepository.saveAndFlush(mcq);

        PracticeQuestion essay = new PracticeQuestion(writingSet.getId(), 51, "ESSAY", "Prompt Essay " + title, "[]", "", "Explain", BigDecimal.TEN, 1);
        essay.setWritingTaskType(WritingTaskType.Q51);
        essay.setGroupId(group.getId());
        essay = questionRepository.saveAndFlush(essay);

        PracticeAttempt attempt = new PracticeAttempt(student.getId(), writingSet.getId(), test.getId(), "WRITING", section.getId());
        String answersJson = "{\"" + mcq.getId() + "\":\"1\",\"" + essay.getId() + "\":\"Existing essay\"}";
        String feedbackJson = "{\"" + essay.getId() + "\":{\"raw_score\":8.0,\"raw_score_max\":10.0}}";
        attempt.markGraded(BigDecimal.valueOf(90.00), BigDecimal.valueOf(20), answersJson, feedbackJson);
        attempt = attemptRepository.saveAndFlush(attempt);

        return new WritingMixedAttemptFixture(
                writingSet.getId(),
                test.getId(),
                section.getId(),
                group.getId(),
                mcq.getId(),
                essay.getId(),
                attempt.getId()
        );
    }

    private void deleteWritingMixedAttemptFixture(WritingMixedAttemptFixture fixture) {
        attemptRepository.findById(fixture.attemptId()).ifPresent(attemptRepository::delete);
        questionRepository.findById(fixture.mcqQuestionId()).ifPresent(questionRepository::delete);
        questionRepository.findById(fixture.essayQuestionId()).ifPresent(questionRepository::delete);
        groupRepository.findById(fixture.groupId()).ifPresent(groupRepository::delete);
        sectionRepository.findById(fixture.sectionId()).ifPresent(sectionRepository::delete);
        testRepository.findById(fixture.testId()).ifPresent(testRepository::delete);
        setRepository.findById(fixture.setId()).ifPresent(setRepository::delete);
    }

    private record WritingMixedAttemptFixture(
            Long setId,
            Long testId,
            Long sectionId,
            Long groupId,
            Long mcqQuestionId,
            Long essayQuestionId,
            Long attemptId
    ) {
    }

    private ListeningAttemptFixture createListeningAttemptFixture(String title) {
        PracticeSet listeningSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", "LISTENING", "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(listeningSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(listeningSet.getId(), "Listening Section", "LISTENING", "MCQ", "Desc", 50, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(listeningSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion question = new PracticeQuestion(listeningSet.getId(), 1, "SINGLE_CHOICE", "Prompt " + title, "[\"A\",\"B\"]", "1", "Explain", BigDecimal.TEN, 0);
        question.setGroupId(group.getId());
        question = questionRepository.saveAndFlush(question);

        PracticeAttempt attempt = new PracticeAttempt(student.getId(), listeningSet.getId(), test.getId(), "LISTENING", section.getId());
        attempt.markSubmitted(BigDecimal.TEN, BigDecimal.TEN, "{\"" + question.getId() + "\":\"1\"}");
        attempt = attemptRepository.saveAndFlush(attempt);

        return new ListeningAttemptFixture(
                listeningSet.getId(),
                test.getId(),
                section.getId(),
                group.getId(),
                question.getId(),
                attempt.getId()
        );
    }

    private void deleteListeningAttemptFixture(ListeningAttemptFixture fixture) {
        attemptRepository.findById(fixture.attemptId()).ifPresent(attemptRepository::delete);
        questionRepository.findById(fixture.questionId()).ifPresent(questionRepository::delete);
        groupRepository.findById(fixture.groupId()).ifPresent(groupRepository::delete);
        sectionRepository.findById(fixture.sectionId()).ifPresent(sectionRepository::delete);
        testRepository.findById(fixture.testId()).ifPresent(testRepository::delete);
        setRepository.findById(fixture.setId()).ifPresent(setRepository::delete);
    }

    private record ListeningAttemptFixture(
            Long setId,
            Long testId,
            Long sectionId,
            Long groupId,
            Long questionId,
            Long attemptId
    ) {
    }

    private SpeakingAttemptFixture createSpeakingAttemptFixture(String title) {
        PracticeSet speakingSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", "SPEAKING", "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(speakingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(speakingSet.getId(), "Speaking Section", "SPEAKING", "ORAL", "Desc", 50, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(speakingSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion question = new PracticeQuestion(speakingSet.getId(), 1, "SPEAKING", "Prompt " + title, "[]", "", "Explain", BigDecimal.TEN, 0);
        question.setGroupId(group.getId());
        question = questionRepository.saveAndFlush(question);

        publishVersion(speakingSet.getId());
        Long attemptId = practiceService.startAttempt(
                speakingSet.getId(), test.getId(), section.getId(), student.getId());
        PracticeAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        attempt.markGraded(
                BigDecimal.valueOf(80.00),
                BigDecimal.TEN,
                "{\"" + question.getId() + "\":\"Existing spoken answer\"}",
                "{\"raw_score\":8.0,\"raw_score_max\":10.0}");
        attempt = attemptRepository.saveAndFlush(attempt);

        return new SpeakingAttemptFixture(
                speakingSet.getId(),
                test.getId(),
                section.getId(),
                group.getId(),
                question.getId(),
                attempt.getId()
        );
    }

    private SpeakingAttemptFixture createLegacySpeakingInProgressAttempt(String title) {
        PracticeSet speakingSet = setRepository.saveAndFlush(new PracticeSet(
                title, "Desc", "SPEAKING", "GLOBAL", null, null, "{}", "PUBLISHED", lecturer.getId()
        ));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(speakingSet.getId(), "Test 1", "Desc", 1, 40));
        PracticeSection section = new PracticeSection(
                speakingSet.getId(), "Speaking Section", "SPEAKING", "ORAL", "Desc", 50, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(speakingSet.getId(), "Group 1", 1, 1, "Desc", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion question = new PracticeQuestion(
                speakingSet.getId(),
                1,
                PracticeQuestion.TYPE_SPEAKING,
                "Prompt " + title,
                "[]",
                "",
                "Explain",
                BigDecimal.TEN,
                0);
        question.setGroupId(group.getId());
        question = questionRepository.saveAndFlush(question);
        publishVersion(speakingSet.getId());

        Long attemptId = practiceService.startAttempt(
                speakingSet.getId(), test.getId(), section.getId(), student.getId());

        return new SpeakingAttemptFixture(
                speakingSet.getId(),
                test.getId(),
                section.getId(),
                group.getId(),
                question.getId(),
                attemptId
        );
    }

    private void deleteSpeakingAttemptFixture(SpeakingAttemptFixture fixture) {
        attemptRepository.findById(fixture.attemptId()).ifPresent(attemptRepository::delete);
        deletePublishedVersionFixture(fixture.setId());
        questionRepository.findById(fixture.questionId()).ifPresent(questionRepository::delete);
        groupRepository.findById(fixture.groupId()).ifPresent(groupRepository::delete);
        sectionRepository.findById(fixture.sectionId()).ifPresent(sectionRepository::delete);
        testRepository.findById(fixture.testId()).ifPresent(testRepository::delete);
        setRepository.findById(fixture.setId()).ifPresent(setRepository::delete);
    }

    private void deletePublishedVersionFixture(Long setId) {
        List<Long> versionIds = publishedVersionRepository.findBySetIdOrderByVersionNumberDesc(setId).stream()
                .map(com.ksh.entities.PracticePublishedVersion::getId)
                .toList();
        if (versionIds.isEmpty()) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (Long versionId : versionIds) {
            jdbc.update("DELETE FROM practice_material_references WHERE published_version_id = ?", versionId);
            jdbc.update("DELETE FROM practice_question_versions WHERE published_version_id = ?", versionId);
            jdbc.update("DELETE FROM practice_question_group_versions WHERE published_version_id = ?", versionId);
            jdbc.update("DELETE FROM practice_section_versions WHERE published_version_id = ?", versionId);
            jdbc.update("DELETE FROM practice_test_versions WHERE published_version_id = ?", versionId);
            jdbc.update("DELETE FROM practice_set_versions WHERE published_version_id = ?", versionId);
            jdbc.update("DELETE FROM practice_published_versions WHERE id = ?", versionId);
        }
    }

    private record SpeakingAttemptFixture(
            Long setId,
            Long testId,
            Long sectionId,
            Long groupId,
            Long questionId,
            Long attemptId
    ) {
    }
}
