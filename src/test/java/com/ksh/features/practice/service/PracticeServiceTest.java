package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSubmission;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.features.practice.ai.AnswerExplanationClient;
import com.ksh.features.practice.ai.WritingEvaluationClient;
import com.ksh.features.practice.dto.PracticeDtos.*;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {

    private PracticeSetRepository setRepository;
    private PracticeQuestionRepository questionRepository;
    private PracticeSubmissionRepository submissionRepository;
    private PracticeQuestionGroupRepository groupRepository;
    private com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;
    private com.ksh.features.practice.repository.PracticeAttemptRepository attemptRepository;
    private com.ksh.features.practice.repository.PracticeTestRepository testRepository;
    private WritingEvaluationClient evaluationClient;
    private AnswerExplanationClient answerExplanationClient;
    private com.ksh.features.practice.service.ReadingListeningExplanationService readingListeningExplanationService;
    private com.ksh.common.storage.AudioStorageService audioStorageService;
    private ObjectMapper objectMapper;

    private PracticeService practiceService;

    @BeforeEach
    void setUp() {
        setRepository = mock(PracticeSetRepository.class);
        questionRepository = mock(PracticeQuestionRepository.class);
        submissionRepository = mock(PracticeSubmissionRepository.class);
        groupRepository = mock(PracticeQuestionGroupRepository.class);
        sectionRepository = mock(com.ksh.features.practice.repository.PracticeSectionRepository.class);
        attemptRepository = mock(com.ksh.features.practice.repository.PracticeAttemptRepository.class);
        testRepository = mock(com.ksh.features.practice.repository.PracticeTestRepository.class);
        evaluationClient = mock(WritingEvaluationClient.class);
        answerExplanationClient = mock(AnswerExplanationClient.class);
        readingListeningExplanationService = mock(com.ksh.features.practice.service.ReadingListeningExplanationService.class);
        audioStorageService = mock(com.ksh.common.storage.AudioStorageService.class);
        objectMapper = new ObjectMapper();

        practiceService = new PracticeService(
                setRepository,
                questionRepository,
                submissionRepository,
                groupRepository,
                sectionRepository,
                attemptRepository,
                testRepository,
                evaluationClient,
                answerExplanationClient,
                readingListeningExplanationService,
                audioStorageService,
                objectMapper
        );
    }

    @Test
    void testListPublished() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING", "TOPIK_I", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findByStatusOrderByCreatedAtDesc("PUBLISHED")).thenReturn(List.of(set));

        List<PracticeSetRow> result = practiceService.listPublished();
        assertEquals(1, result.size());
        assertEquals("Title", result.get(0).title());
    }

    @Test
    void testGetPracticeNotFound() {
        when(setRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> practiceService.getPractice(1L));
    }

    @Test
    void testGetPracticeNotPublished() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING", "TOPIK_I", "GLOBAL", null, null, null, "DRAFT", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        assertThrows(EntityNotFoundException.class, () -> practiceService.getPractice(1L));
    }

    @Test
    void testGetPracticeWithGroups() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING", "TOPIK_I", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(100L);
        when(group.getGroupLabel()).thenReturn("1-2");
        when(group.getQuestionFrom()).thenReturn(1);
        when(group.getQuestionTo()).thenReturn(2);
        when(group.getInstruction()).thenReturn("Instruction");
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(group));

        PracticeQuestion q1 = mock(PracticeQuestion.class);
        when(q1.getId()).thenReturn(10L);
        when(q1.getQuestionNo()).thenReturn(1);
        when(q1.getQuestionType()).thenReturn("MCQ");
        when(q1.getPrompt()).thenReturn("Q1");
        when(q1.getGroupId()).thenReturn(100L);
        when(q1.getPoints()).thenReturn(BigDecimal.valueOf(5));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q1));

        PracticeSetView view = practiceService.getPractice(1L);
        assertNotNull(view);
        assertEquals(1, view.groups().size());
        assertEquals("1-2", view.groups().get(0).groupLabel());
        assertEquals(1, view.groups().get(0).questions().size());
    }

    @Test
    void testGetPracticeFallbackGrouping() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING", "TOPIK_I", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        PracticeQuestion q1 = mock(PracticeQuestion.class);
        when(q1.getId()).thenReturn(10L);
        when(q1.getQuestionNo()).thenReturn(1);
        when(q1.getQuestionType()).thenReturn("MCQ");
        when(q1.getPrompt()).thenReturn("Q1");
        when(q1.getPoints()).thenReturn(BigDecimal.valueOf(5));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q1));

        PracticeSetView view = practiceService.getPractice(1L);
        assertNotNull(view);
        assertEquals(1, view.groups().size());
        assertEquals("1-2", view.groups().get(0).groupLabel());
    }



    @Test
    void testReEvaluateNotFound() {
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> practiceService.reEvaluate(1L, 2L));
    }

    @Test
    void testReEvaluateSuccess() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"10\":\"Tôi học tiếng Hàn.\"}");
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(
                1L, 54, "ESSAY", "Q",
                "[]", "", "Giải thích đáp án đúng",
                BigDecimal.valueOf(50.0), 0
        );
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        when(evaluationClient.evaluate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"score\":6.0,\"overall_score\":6.0,\"raw_score\":30.0,\"raw_score_max\":50.0}");

        practiceService.reEvaluate(1L, 2L);
        verify(evaluationClient, times(1)).evaluate(anyString(), anyString(), anyBoolean());
    }

    @Test
    void testReEvaluateEmptyScoreSavedAsZero() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"10\":\"\"}"); // Empty answer
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(
                1L, 54, "ESSAY", "Q",
                "[]", "", "Giải thích đáp án đúng",
                BigDecimal.valueOf(50.0), 0
        );
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        // Stub evaluate to return a contract-valid JSON with raw_score = 0.0 (spam/empty)
        when(evaluationClient.evaluate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"score\":0.0,\"overall_score\":0.0,\"raw_score\":0.0,\"raw_score_max\":50.0}");

        practiceService.reEvaluate(1L, 2L);

        // Verify that score is saved as exactly ZERO (0.0) in attempt
        assertEquals(BigDecimal.ZERO, attempt.getScore(), "Empty score must be persisted as exactly 0");
    }

    @Test
    void testGetResult() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"10\":\"3\"}");
        attempt.setScore(BigDecimal.TEN);
        attempt.setTotalPoints(BigDecimal.TEN);
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Section 1", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        PracticeResultView view = practiceService.getResult(1L, 2L);
        assertNotNull(view);
        assertEquals(BigDecimal.TEN, view.score());
    }



    @Test
    void testGetLearningProgressOverview() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.markGraded(BigDecimal.valueOf(8), BigDecimal.valueOf(10), "{}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(attempt));
        when(attemptRepository.findTop100ByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(attempt));
        when(setRepository.findAllById(any())).thenReturn(List.of(
                new PracticeSet("Reading Test", "Desc", "MIXED", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L)));
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test 1", "Desc", 1, 40);
        setEntityId(test, 10L);
        when(testRepository.findAllById(any())).thenReturn(List.of(test));
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findAllById(any())).thenReturn(List.of(section));

        LearningProgressOverview overview = practiceService.getLearningProgressOverview(2L, "Toan", "avatar.jpg");
        assertNotNull(overview);
        assertEquals("Toan", overview.studentName());
        assertEquals("avatar.jpg", overview.avatarUrl());
        assertEquals(1, overview.totalAttempts());
        assertEquals(1, overview.totalCompletedTests());
        // Verify skill metric for READING is calculated
        Optional<SkillMetric> readingMetric = overview.skillMetrics().stream()
                .filter(m -> "READING".equals(m.skill())).findFirst();
        assertTrue(readingMetric.isPresent());
        assertEquals(80.0, readingMetric.get().normalizedScore());
        assertEquals("READING", overview.recentHistory().get(0).skill());
        verify(submissionRepository, never()).findByUserIdAndStatusNotOrderByCreatedAtDesc(any(), anyString());
    }

    @Test
    void testGetPracticeAnalytics() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.markGraded(BigDecimal.valueOf(8), BigDecimal.valueOf(10), "{\"100\":\"1\"}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(attempt));
        when(attemptRepository.findTop100ByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(attempt));
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "MIXED", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        when(setRepository.findAllById(any())).thenReturn(List.of(set));
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test 1", "Desc", 1, 40);
        setEntityId(test, 10L);
        when(testRepository.findAllById(any())).thenReturn(List.of(test));
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findAllById(any())).thenReturn(List.of(section));

        PracticeQuestion q1 = mock(PracticeQuestion.class);
        when(q1.getId()).thenReturn(100L);
        when(q1.getQuestionType()).thenReturn("MCQ");
        when(q1.getSetId()).thenReturn(1L);
        when(questionRepository.findBySetIdIn(anyList())).thenReturn(List.of(q1));

        PracticeAnalytics analytics = practiceService.getPracticeAnalytics(2L);
        assertNotNull(analytics);
        assertFalse(analytics.weeklySkillMetrics().isEmpty());
        assertFalse(analytics.scoreTrend().isEmpty());
        assertFalse(analytics.history().isEmpty());
        // Verify Reading weekly score is mapped
        Optional<SkillMetric> readingMetric = analytics.weeklySkillMetrics().stream()
                .filter(m -> "READING".equals(m.skill())).findFirst();
        assertTrue(readingMetric.isPresent());
        assertEquals(80.0, readingMetric.get().normalizedScore());
        assertEquals("READING", analytics.history().get(0).skill());
        verify(submissionRepository, never()).findByUserIdAndStatusNotOrderByCreatedAtDesc(any(), anyString());
    }

    @Test
    void testProgressAnalyticsUsesAllAttemptsNotTop100ForAverage() {
        List<PracticeAttempt> allAttempts = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
            BigDecimal score = i == 101 ? BigDecimal.valueOf(100) : BigDecimal.valueOf(50);
            attempt.markGraded(score, BigDecimal.valueOf(100), "{}", "{}");
            setEntityId(attempt, i);
            allAttempts.add(attempt);
        }
        List<PracticeAttempt> recent100 = allAttempts.subList(0, 100);
        when(attemptRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(allAttempts);
        when(attemptRepository.findTop100ByUserIdOrderByCreatedAtDesc(2L)).thenReturn(recent100);

        LearningProgressOverview overview = practiceService.getLearningProgressOverview(2L, "Toan", "");

        assertEquals(101, overview.totalAttempts());
        assertEquals(101, overview.totalCompletedTests());
        assertEquals(50.5, overview.recentAverageScore());
        assertEquals(8, overview.recentHistory().size());
    }
    private void setEntityId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testStartAttemptValidationAndSuccess() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));

        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        when(attemptRepository.save(any(PracticeAttempt.class))).thenAnswer(invocation -> {
            PracticeAttempt att = invocation.getArgument(0);
            setEntityId(att, 99L);
            return att;
        });

        Long attemptId = practiceService.startAttempt(1L, 10L, 20L, 2L);
        assertEquals(99L, attemptId);
    }

    @Test
    void testStartAttemptReuseExisting() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt existingAttempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(existingAttempt, 88L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));

        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(2L, 10L, 20L, "IN_PROGRESS"))
                .thenReturn(Optional.of(existingAttempt));

        Long attemptId = practiceService.startAttempt(1L, 10L, 20L, 2L);
        assertEquals(88L, attemptId);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void testStartAttemptInvalidSectionIdThrows() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        // Section not found
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            practiceService.startAttempt(1L, 10L, 999L, 2L);
        });
    }

    @Test
    void testStartAttemptSectionNotBelongingToTestThrows() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(9999L); // mismatch testId
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            practiceService.startAttempt(1L, 10L, 20L, 2L);
        });
    }

    @Test
    void testSaveInProgressAnswersSuccess() throws Exception {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        Map<String, String> form = Map.of("answer_101", "3", "other_field", "value");
        practiceService.saveInProgressAnswers(99L, 2L, form);

        assertEquals("{\"101\":\"3\"}", attempt.getAnswersJson());
        verify(attemptRepository).save(attempt);
    }

    @Test
    void testSaveInProgressAnswersNotInProgressThrows() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        assertThrows(IllegalStateException.class, () -> {
            practiceService.saveInProgressAnswers(99L, 2L, Map.of());
        });
    }

    @Test
    void testDiscardAttemptSuccess() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        practiceService.discardAttempt(99L, 2L);
        verify(attemptRepository).delete(attempt);
    }

    @Test
    void testDiscardAttemptNotInProgressThrows() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        assertThrows(IllegalStateException.class, () -> {
            practiceService.discardAttempt(99L, 2L);
        });
    }

    @Test
    void testSubmitReadingAttemptSuccessful() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "3");
        Long attemptId = practiceService.submitAttempt(99L, 2L, form);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
        assertEquals("{\"101\":\"3\"}", attempt.getAnswersJson());
        verify(attemptRepository).save(attempt);
    }

    @Test
    void testSubmitAttemptToleratesExplanationAiQuotaExceeded() {
        String metaJson = "{\"skills\":[\"READING\"]}";
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, metaJson, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        // Mock explanation client throwing exception
        when(answerExplanationClient.explain(any(), any(), any()))
                .thenThrow(new RuntimeException("Gemini quota exceeded 429"));

        Map<String, String> form = Map.of("answer_101", "3");
        Long attemptId = practiceService.submitAttempt(99L, 2L, form);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
    }

    @Test
    void testSubmitAttemptIgnoresOtherSectionsQuestions() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // Question 1 belongs to section 20
        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        // Question 2 belongs to another section (not in section 20)
        // Since getQuestionGroupsForSection(1L, 20L) only returns question 1, question 2 is not graded
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "3", "answer_102", "4");
        practiceService.submitAttempt(99L, 2L, form);

        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
    }

    @Test
    void testSubmitAttemptAlreadySubmittedThrows() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        setEntityId(attempt, 99L);

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        assertThrows(IllegalStateException.class, () -> {
            practiceService.submitAttempt(99L, 2L, Map.of());
        });
    }

    @Test
    void testSubmitAttemptOtherUserThrows() {
        // Attempt owned by user 2, requested by user 3
        when(attemptRepository.findByIdAndUserId(99L, 3L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            practiceService.submitAttempt(99L, 3L, Map.of());
        });
    }



    @Test
    void testGetReadingListeningResultLegacyFallback() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"101\":\"3\"}");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // Question group with sectionId = null (legacy)
        PracticeQuestionGroup group = new PracticeQuestionGroup(1L, "Group 1", 1, 1, "Instruction", null, null, 1);
        group.setSectionId(null);
        setEntityId(group, 5L);

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        q1.setGroupId(5L);
        setEntityId(q1, 101L);

        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));
        when(questionRepository.findById(101L)).thenReturn(Optional.of(q1));

        ReadingListeningResultView result = practiceService.getReadingListeningResult(99L, 2L);
        assertNotNull(result);
        assertEquals(1, result.groups().size());
        assertEquals("Group 1", result.groups().get(0).groupLabel());
        assertEquals(1, result.groups().get(0).questions().size());
        assertEquals("Q", result.groups().get(0).questions().get(0).prompt());
    }

    @Test
    void testGetAttemptResultLegacyFallback() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"101\":\"3\"}");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // Question group with sectionId = null (legacy)
        PracticeQuestionGroup group = new PracticeQuestionGroup(1L, "Group 1", 1, 1, "Instruction", null, null, 1);
        group.setSectionId(null);
        setEntityId(group, 5L);

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        q1.setGroupId(5L);
        setEntityId(q1, 101L);

        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));
        when(questionRepository.findById(101L)).thenReturn(Optional.of(q1));

        PracticeAttemptResultView result = practiceService.getAttemptResult(99L, 2L);
        assertNotNull(result);
        assertEquals(1, result.sections().size());
        assertEquals(1, result.sections().get(0).groups().size());
        assertEquals("Group 1", result.sections().get(0).groups().get(0).groupLabel());
    }
    @Test
    void testQuestionComparatorOrdering() {
        PracticeQuestion q1 = new PracticeQuestion(
                1L, 52, "ESSAY", "Prompt 1",
                "[]", "", "Explain",
                BigDecimal.TEN, 5
        );
        setEntityId(q1, 1L);

        PracticeQuestion q2 = new PracticeQuestion(
                1L, 51, "ESSAY", "Prompt 2",
                "[]", "", "Explain",
                BigDecimal.TEN, 10
        );
        setEntityId(q2, 2L);

        PracticeQuestion q3 = new PracticeQuestion(
                1L, null, "ESSAY", "Prompt 3",
                "[]", "", "Explain",
                BigDecimal.TEN, 1
        );
        setEntityId(q3, 3L);

        PracticeQuestion q4 = new PracticeQuestion(
                1L, null, "ESSAY", "Prompt 4",
                "[]", "", "Explain",
                BigDecimal.TEN, null
        );
        setEntityId(q4, 4L);

        List<PracticeQuestion> list = new ArrayList<>(List.of(q4, q1, q3, q2));
        list.sort(practiceService.questionComparator);

        assertEquals(3L, list.get(0).getId()); // displayOrder 1
        assertEquals(1L, list.get(1).getId()); // displayOrder 5
        assertEquals(2L, list.get(2).getId()); // displayOrder 10
        assertEquals(4L, list.get(3).getId()); // questionNo null, displayOrder null
    }

    @Test
    void testSubmitAttemptThrowsOnInvalidPointsConfig() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // Question 1 has points <= 0
        PracticeQuestion q1 = new PracticeQuestion(
                1L, 51, "ESSAY", "Q",
                "[]", "", "Explain",
                BigDecimal.ZERO, 0
        );
        setEntityId(q1, 101L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "My answer");
        assertThrows(IllegalStateException.class, () -> {
            practiceService.submitAttempt(99L, 2L, form);
        });
    }

    @Test
    void testLegacyFeedbackMappingRules() throws Exception {
        // ESSAY Questions
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q2, 102L);

        List<PracticeQuestion> questions = List.of(q1, q2);
        String answersJson = "{\"101\":\"Answer one\",\"102\":\"Answer two\"}";

        // Rule 1: Match student_text exactly
        String aiFeedbackJson1 = "{\"rubric_scores\":[],\"student_text\":\"Answer one\",\"raw_score\":8.0,\"raw_score_max\":10.0}";
        List<PracticeQuestionFeedbackRow> feedbacks1 = practiceService.buildQuestionFeedbackRows(questions, answersJson, aiFeedbackJson1);
        assertNotNull(feedbacks1.get(0).feedbackNode());
        assertNull(feedbacks1.get(1).feedbackNode());
        assertEquals("Answer one", feedbacks1.get(0).learnerAnswer());

        // Rule 2: Match multiple student_text (same answer) -> match last ordered matching
        String answersJsonDup = "{\"101\":\"Same answer\",\"102\":\"Same answer\"}";
        String aiFeedbackJsonDup = "{\"rubric_scores\":[],\"student_text\":\"Same answer\",\"raw_score\":8.0,\"raw_score_max\":10.0}";
        List<PracticeQuestionFeedbackRow> feedbacksDup = practiceService.buildQuestionFeedbackRows(questions, answersJsonDup, aiFeedbackJsonDup);
        assertNull(feedbacksDup.get(0).feedbackNode());
        assertNotNull(feedbacksDup.get(1).feedbackNode()); // matched last ordered (q2)

        // Rule 3: No match -> map to last ordered essay
        String aiFeedbackJsonNoMatch = "{\"rubric_scores\":[],\"student_text\":\"Different answer\",\"raw_score\":7.0,\"raw_score_max\":10.0}";
        List<PracticeQuestionFeedbackRow> feedbacksNoMatch = practiceService.buildQuestionFeedbackRows(questions, answersJson, aiFeedbackJsonNoMatch);
        assertNull(feedbacksNoMatch.get(0).feedbackNode());
        assertNotNull(feedbacksNoMatch.get(1).feedbackNode()); // fallback to last ordered essay (q2)

        // Rule 4: Only one essay -> map to single essay question
        List<PracticeQuestion> singleList = List.of(q1);
        List<PracticeQuestionFeedbackRow> feedbacksSingle = practiceService.buildQuestionFeedbackRows(singleList, answersJson, aiFeedbackJsonNoMatch);
        assertNotNull(feedbacksSingle.get(0).feedbackNode()); // mapped to single essay
    }

    @Test
    void testWritingAggregationWithMcqAndEssay() throws Exception {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // MCQ Question (Points 5.0)
        PracticeQuestion qMcq = new PracticeQuestion(
                1L, 10, "MCQ", "Q1",
                "[\"1\",\"2\",\"3\"]", "3", "Explain",
                BigDecimal.valueOf(5.0), 0
        );
        setEntityId(qMcq, 101L);

        // Essay Question (Points 15.0)
        PracticeQuestion qEssay = new PracticeQuestion(
                1L, 51, "ESSAY", "Q2",
                "[]", "", "Explain",
                BigDecimal.valueOf(15.0), 0
        );
        setEntityId(qEssay, 102L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(qMcq, qEssay));

        // Stub AI Client to return raw_score = 6.0, raw_score_max = 10.0 (meaning 60% earned points)
        // 60% of 15.0 = 9.0 earned points.
        // Total earned points = MCQ (5.0) + ESSAY (9.0) = 14.0 points.
        // Total possible points = 5.0 + 15.0 = 20.0 points.
        // Final percentage score = (14.0 / 20.0) * 100 = 70.00%
        when(evaluationClient.evaluate(anyString(), anyString(), anyBoolean())).thenReturn("{\"raw_score\":6.0,\"raw_score_max\":10.0}");

        Map<String, String> form = Map.of("answer_101", "3", "answer_102", "My essay");
        practiceService.submitAttempt(99L, 2L, form);

        assertEquals("GRADED", attempt.getStatus());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(70.00)));
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(20.0)));
    }

    @Test
    void testWritingFeedbackMapWritesObjectValuesNotTextualJson() throws Exception {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(eq("Q1"), anyString(), anyBoolean()))
                .thenReturn("{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}");
        when(evaluationClient.evaluate(eq("Q2"), anyString(), anyBoolean()))
                .thenReturn("{\"raw_score\":21.0,\"raw_score_max\":30.0,\"rubric_scores\":[]}");

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1", "answer_102", "A2"));

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertTrue(root.get("101").isObject());
        assertFalse(root.get("101").isTextual());
        assertTrue(root.get("102").isObject());
        assertFalse(root.get("102").isTextual());
    }

    @Test
    void testWritingAggregationClampsRawScoresBeforeWeighting() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(eq("Q1"), anyString(), anyBoolean()))
                .thenReturn("{\"raw_score\":-5.0,\"raw_score_max\":10.0}");
        when(evaluationClient.evaluate(eq("Q2"), anyString(), anyBoolean()))
                .thenReturn("{\"raw_score\":40.0,\"raw_score_max\":30.0}");

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1", "answer_102", "A2"));

        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40.0)));
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(75.00)));
    }
}
