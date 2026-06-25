package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSubmission;
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
        when(submissionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> practiceService.reEvaluate(1L, 2L));
    }

    @Test
    void testReEvaluateSuccess() {
        PracticeSubmission sub = new PracticeSubmission(1L, 2L, BigDecimal.TEN, BigDecimal.TEN, "{}", "{}");
        when(submissionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(sub));

        PracticeSet set = new PracticeSet("Title", "Desc", "WRITING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeQuestion q = mock(PracticeQuestion.class);
        when(q.getId()).thenReturn(10L);
        when(q.getQuestionNo()).thenReturn(54);
        when(q.getQuestionType()).thenReturn("ESSAY");
        when(q.getPrompt()).thenReturn("Q");
        when(q.getPoints()).thenReturn(BigDecimal.valueOf(50));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        when(evaluationClient.evaluate(anyString(), anyString(), anyBoolean())).thenReturn("{\"score\":6.0,\"overall_score\":6.0}");

        practiceService.reEvaluate(1L, 2L);
        verify(evaluationClient, times(1)).evaluate(anyString(), anyString(), anyBoolean());
    }

    @Test
    void testGetResult() {
        PracticeSubmission sub = new PracticeSubmission(1L, 2L, BigDecimal.TEN, BigDecimal.TEN, "{}", "{}");
        when(submissionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(sub));

        PracticeSet set = new PracticeSet("Title", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeQuestion q = mock(PracticeQuestion.class);
        when(q.getId()).thenReturn(10L);
        when(q.getQuestionNo()).thenReturn(1);
        when(q.getQuestionType()).thenReturn("MCQ");
        when(q.getPrompt()).thenReturn("Q");
        when(q.getPoints()).thenReturn(BigDecimal.valueOf(5));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        PracticeResultView view = practiceService.getResult(1L, 2L);
        assertNotNull(view);
        assertEquals(BigDecimal.TEN, view.score());
    }



    @Test
    void testGetLearningProgressOverview() {
        PracticeSubmission sub1 = new PracticeSubmission(1L, 2L, BigDecimal.valueOf(8), BigDecimal.valueOf(10), "{}", "{}");
        sub1.setSubmittedAt(java.time.LocalDateTime.now());
        when(submissionRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(any(), anyString()))
                .thenReturn(List.of(sub1));

        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

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
    }

    @Test
    void testGetPracticeAnalytics() {
        PracticeSubmission sub1 = new PracticeSubmission(1L, 2L, BigDecimal.valueOf(8), BigDecimal.valueOf(10), "{}", "{}");
        sub1.setSubmittedAt(java.time.LocalDateTime.now());
        when(submissionRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(any(), anyString()))
                .thenReturn(List.of(sub1));

        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING", "TOPIK_II", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

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
    }
}

