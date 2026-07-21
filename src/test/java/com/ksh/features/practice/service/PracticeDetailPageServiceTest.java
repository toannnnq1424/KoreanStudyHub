package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetTestCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSkillAttemptCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeTestRow;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeDetailPageServiceTest {

    @Mock
    private PracticeSectionRepository sectionRepository;

    @Mock
    private PracticeAttemptRepository attemptRepository;

    private PracticeDetailPageService service;

    @BeforeEach
    void setUp() {
        service = new PracticeDetailPageService(sectionRepository, attemptRepository);
    }

    @Test
    void setCardsKeepProgressAndResumeStateInsideEachTest() {
        PracticeSection reading = section(101L, 10L, "READING", 1);
        PracticeSection listening = section(102L, 10L, "LISTENING", 2);
        PracticeSection speaking = section(201L, 20L, "SPEAKING", 1);
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(reading, listening, speaking));

        PracticeAttempt completedReading = completedAttempt(
                1001L, 10L, 101L, "READING", 8, "2026-07-14T08:00:00");
        PracticeAttempt activeListening = activeAttempt(
                1002L, 10L, 102L, "LISTENING", "2026-07-14T09:00:00");
        PracticeAttempt completedSpeaking = completedAttempt(
                2001L, 20L, 201L, "SPEAKING", 90, "2026-07-14T10:00:00");
        when(attemptRepository.findBySetIdAndUserIdOrderByCreatedAtDescIdDesc(1L, 7L))
                .thenReturn(List.of(completedSpeaking, activeListening, completedReading));

        List<PracticeSetTestCard> cards = service.buildTestCards(
                1L,
                List.of(
                        new PracticeTestRow(10L, 1L, "Test 1", null, 1, 45),
                        new PracticeTestRow(20L, 1L, "Test 2", null, 2, 15)),
                7L);

        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).completedSkillCount()).isEqualTo(1);
        assertThat(cards.get(0).totalSkillCount()).isEqualTo(2);
        assertThat(cards.get(0).state()).isEqualTo("IN_PROGRESS");
        assertThat(cards.get(0).resumeAttemptId()).isEqualTo(1002L);
        assertThat(cards.get(0).skills()).extracting(skill -> skill.code())
                .containsExactly("READING", "LISTENING");

        assertThat(cards.get(1).completedSkillCount()).isEqualTo(1);
        assertThat(cards.get(1).totalSkillCount()).isEqualTo(1);
        assertThat(cards.get(1).state()).isEqualTo("COMPLETED");
        assertThat(cards.get(1).resumeAttemptId()).isNull();
        assertThat(cards.get(1).skills()).extracting(skill -> skill.code())
                .containsExactly("SPEAKING");

        assertThat(service.collectSetSkills(cards)).extracting(skill -> skill.code())
                .containsExactly("READING", "LISTENING", "SPEAKING");
        verify(sectionRepository).findBySetIdOrderByDisplayOrderAsc(1L);
        verify(attemptRepository).findBySetIdAndUserIdOrderByCreatedAtDescIdDesc(1L, 7L);
    }

    @Test
    void skillCardShowsTwoNewestAttemptsAndKeepsOlderHistoryExpandable() {
        PracticeSection reading = section(101L, 10L, "READING", 1);
        PracticeAttempt oldest = completedAttempt(
                1001L, 10L, 101L, "READING", 6, "2026-07-14T08:00:00");
        PracticeAttempt middle = completedAttempt(
                1002L, 10L, 101L, "READING", 7, "2026-07-14T09:00:00");
        PracticeAttempt newest = completedAttempt(
                1003L, 10L, 101L, "READING", 9, "2026-07-14T10:00:00");
        PracticeAttempt active = activeAttempt(
                1004L, 10L, 101L, "READING", "2026-07-14T11:00:00");
        PracticeAttempt discarded = activeAttempt(
                1005L, 10L, 101L, "READING", "2026-07-14T12:00:00");
        discarded.discard(LocalDateTime.parse("2026-07-14T12:05:00"));

        when(attemptRepository.findByTestIdAndUserIdOrderByCreatedAtDesc(10L, 7L))
                .thenReturn(List.of(oldest, discarded, active, middle, newest));

        List<PracticeSkillAttemptCard> cards = service.buildSkillCards(
                10L, List.of(reading), 7L);

        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.inProgressAttemptId()).isEqualTo(1004L);
            assertThat(card.completedAttempts()).extracting(attempt -> attempt.id())
                    .containsExactly(1003L, 1002L, 1001L);
            assertThat(card.completedAttempts()).extracting(attempt -> attempt.initiallyVisible())
                    .containsExactly(true, true, false);
            assertThat(card.hiddenAttemptCount()).isEqualTo(1);
            assertThat(card.latestScoreLabel()).isEqualTo("9/10");
            assertThat(card.bestScoreLabel()).isEqualTo("9/10");
            assertThat(card.state()).isEqualTo("IN_PROGRESS");
            assertThat(card.completedAttempts()).extracting(attempt -> attempt.statusLabel())
                    .containsOnly("Đã nộp");
        });
        verify(attemptRepository).findByTestIdAndUserIdOrderByCreatedAtDesc(10L, 7L);
    }

    @Test
    void speakingSkillCardNeverLeaksLegacyOrMissingHolisticScores() {
        PracticeSection speaking = section(201L, 20L, "SPEAKING", 1);
        PracticeAttempt legacyNumeric = gradedAttempt(
                2001L, 20L, 201L, "SPEAKING", 90, "2026-07-14T09:00:00");
        PracticeAttempt currentWithoutScore = gradedAttempt(
                2002L, 20L, 201L, "SPEAKING", null, "2026-07-14T10:00:00");
        when(attemptRepository.findByTestIdAndUserIdOrderByCreatedAtDesc(20L, 7L))
                .thenReturn(List.of(legacyNumeric, currentWithoutScore));

        List<PracticeSkillAttemptCard> cards = service.buildSkillCards(
                20L, List.of(speaking), 7L);

        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.completedAttempts()).extracting(attempt -> attempt.scoreLabel())
                    .containsExactly(
                            "Không có điểm Nói tổng hợp",
                            "Không có điểm Nói tổng hợp");
            assertThat(card.latestScoreLabel()).isEqualTo("Không có điểm Nói tổng hợp");
            assertThat(card.bestScoreLabel()).isEqualTo("Không có điểm Nói tổng hợp");
            assertThat(card.completedAttempts()).extracting(attempt -> attempt.statusLabel())
                    .containsOnly("Đã xử lý phản hồi");
            assertThat(card.completedAttempts()).extracting(attempt -> attempt.scoreLabel())
                    .noneMatch(label -> label.matches(".*\\d.*"));
        });
    }

    @Test
    void speakingSkillCardUsesFeedbackCopyForQueuedAndFailedAnalysis() {
        PracticeSection speaking = section(201L, 20L, "SPEAKING", 1);
        PracticeAttempt queued = completedAttempt(
                2001L, 20L, 201L, "SPEAKING", 0, "2026-07-14T09:00:00");
        queued.setAnalysisStatus(PracticeAttempt.ANALYSIS_QUEUED);
        PracticeAttempt failed = completedAttempt(
                2002L, 20L, 201L, "SPEAKING", 0, "2026-07-14T10:00:00");
        failed.markAnalysisFailed("PROVIDER_UNAVAILABLE");
        when(attemptRepository.findByTestIdAndUserIdOrderByCreatedAtDesc(20L, 7L))
                .thenReturn(List.of(queued, failed));

        List<PracticeSkillAttemptCard> cards = service.buildSkillCards(
                20L, List.of(speaking), 7L);

        assertThat(cards).singleElement().satisfies(card ->
                assertThat(card.completedAttempts()).extracting(attempt -> attempt.statusLabel())
                        .containsExactly("Chưa thể xử lý phản hồi", "Đang xử lý phản hồi"));
    }

    private PracticeSection section(Long id, Long testId, String skill, int displayOrder) {
        PracticeSection section = new PracticeSection(
                1L,
                "Phần " + skill,
                skill,
                "SPEAKING".equals(skill) ? "SPEAKING" : "SINGLE_CHOICE",
                "Hướng dẫn",
                40,
                BigDecimal.TEN,
                displayOrder);
        section.setTestId(testId);
        setField(section, "id", id);
        return section;
    }

    private PracticeAttempt completedAttempt(Long id,
                                               Long testId,
                                               Long sectionId,
                                               String skill,
                                               int score,
                                               String submittedAt) {
        PracticeAttempt attempt = new PracticeAttempt(7L, 1L, testId, skill, sectionId);
        attempt.markSubmitted(BigDecimal.valueOf(score), BigDecimal.TEN, "{}");
        setField(attempt, "id", id);
        setField(attempt, "submittedAt", LocalDateTime.parse(submittedAt));
        return attempt;
    }

    private PracticeAttempt activeAttempt(Long id,
                                          Long testId,
                                          Long sectionId,
                                          String skill,
                                          String updatedAt) {
        PracticeAttempt attempt = new PracticeAttempt(7L, 1L, testId, skill, sectionId);
        setField(attempt, "id", id);
        setField(attempt, "updatedAt", LocalDateTime.parse(updatedAt));
        return attempt;
    }

    private PracticeAttempt gradedAttempt(Long id,
                                          Long testId,
                                          Long sectionId,
                                          String skill,
                                          Integer score,
                                          String submittedAt) {
        PracticeAttempt attempt = new PracticeAttempt(7L, 1L, testId, skill, sectionId);
        attempt.markGraded(score == null ? null : BigDecimal.valueOf(score),
                BigDecimal.TEN, "{}", "{}");
        setField(attempt, "id", id);
        setField(attempt, "submittedAt", LocalDateTime.parse(submittedAt));
        return attempt;
    }

    private void setField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
        throw new IllegalArgumentException("Missing field " + fieldName);
    }
}
