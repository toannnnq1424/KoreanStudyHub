package com.ksh.features.practice.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogBatch;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogQuery;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeCatalogServiceTest {

    private static final long USER_ID = 7L;
    private static final long SET_ID = 11L;
    private static final long TEST_ID = 21L;

    @Mock private PracticeSetRepository setRepository;
    @Mock private PracticeTestRepository testRepository;
    @Mock private PracticeSectionRepository sectionRepository;
    @Mock private PracticeAttemptRepository attemptRepository;
    @Mock private ClassRepository classRepository;
    @Mock private PracticeLearnerAccessService learnerAccessService;

    @InjectMocks
    private PracticeCatalogService service;

    @Test
    void loadsOneBoundedBatchWithRealGraphCountsAndProgress() {
        PracticeSet set = set(SET_ID, "Buổi sáng tiếng Hàn", PracticeSet.SKILL_READING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection listening = section(31L, SET_ID, TEST_ID, "LISTENING", 1);
        PracticeSection reading = section(32L, SET_ID, TEST_ID, "READING", 2);
        PracticeAttempt listeningAttempt = completedAttempt(41L, listening, false);
        PracticeAttempt readingAttempt = completedAttempt(42L, reading, true);
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED, PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS, USER_ID, List.of(-1L), 0L,
                "buổi sáng", "READING", request))
                .thenReturn(new PageImpl<>(List.of(set), request, 25));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(reading, listening));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(readingAttempt, listeningAttempt));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery("  buổi sáng  ", " reading ", null, 0));

        assertThat(batch.items()).hasSize(1);
        assertThat(batch.totalElements()).isEqualTo(25);
        assertThat(batch.hasMore()).isTrue();
        assertThat(batch.batchSize()).isEqualTo(PracticeCatalogService.BATCH_SIZE);
        assertThat(batch.items().get(0).skills())
                .extracting(skill -> skill.code())
                .containsExactly("LISTENING", "READING");
        assertThat(batch.items().get(0).multiSkill()).isTrue();
        assertThat(batch.items().get(0).coverSkill()).isEqualTo("MIXED");
        assertThat(batch.items().get(0).coverLabel()).isEqualTo("2 KỸ NĂNG");
        assertThat(batch.items().get(0).hasSkill("LISTENING")).isTrue();
        assertThat(batch.items().get(0).hasSkill("READING")).isTrue();
        assertThat(batch.items().get(0).hasSkill("WRITING")).isFalse();
        assertThat(batch.items().get(0).skillSummary()).isEqualTo("Nghe, Đọc");
        assertThat(batch.items().get(0).skillCodes()).isEqualTo("LISTENING,READING");
        assertThat(batch.items().get(0).testCount()).isEqualTo(1);
        assertThat(batch.items().get(0).completedTests()).isEqualTo(1);
        assertThat(batch.items().get(0).progressPercent()).isEqualTo(100);
        assertThat(batch.items().get(0).state()).isEqualTo("SCORED");

        verify(testRepository).findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID));
        verify(sectionRepository).findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID));
        verify(attemptRepository)
                .findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                        USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED);
    }

    @Test
    void invalidClassFilterFailsClosedWithoutLoadingCatalogRows() {
        ClassEntity learnerClass = new ClassEntity(
                "Lớp A", 2L, 2L, null,
                LocalDate.now(), LocalDate.now().plusMonths(1), 30);
        ReflectionTestUtils.setField(learnerClass, "id", 15L);
        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of(15L));
        when(classRepository.findAllById(List.of(15L))).thenReturn(List.of(learnerClass));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery("", "ALL", 99L, 0));

        assertThat(batch.items()).isEmpty();
        assertThat(batch.totalElements()).isZero();
        assertThat(batch.hasMore()).isFalse();
        assertThat(batch.classId()).isEqualTo(99L);
        assertThat(batch.classes()).extracting(option -> option.id()).containsExactly(15L);
        verify(setRepository, never()).findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), anyLong(), anyList(),
                anyLong(), anyString(), anyString(), any());
        verify(testRepository, never()).findBySetIdInOrderBySetIdAscDisplayOrderAsc(anyList());
    }

    @Test
    void legacySubmittedAttemptWithoutAnalysisStatusDoesNotBreakTheCatalog() {
        PracticeSet set = set(SET_ID, "Bộ đề cũ", PracticeSet.SKILL_READING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection section = section(31L, SET_ID, TEST_ID, "READING", 1);
        PracticeAttempt attempt = completedAttempt(41L, section, false);
        attempt.setAnalysisStatus(null);
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), eq(USER_ID), eq(List.of(-1L)),
                eq(0L), eq(""), eq(""), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(section));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(attempt));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery(null, "invalid", null, -4));

        assertThat(batch.batch()).isZero();
        assertThat(batch.skill()).isEqualTo("ALL");
        assertThat(batch.items().get(0).state()).isEqualTo("SUBMITTED");
    }

    private PracticeSet set(long id, String title, String skill) {
        PracticeSet set = new PracticeSet(
                title, "Mô tả", skill, PracticeSet.SCOPE_GLOBAL, null,
                null, "{}", PracticeSet.STATUS_PUBLISHED, 2L);
        ReflectionTestUtils.setField(set, "id", id);
        return set;
    }

    private PracticeTest test(long id, long setId) {
        PracticeTest test = new PracticeTest(setId, "Test 1", null, 1, 30);
        ReflectionTestUtils.setField(test, "id", id);
        return test;
    }

    private PracticeSection section(long id, long setId, long testId,
                                    String skill, int displayOrder) {
        PracticeSection section = new PracticeSection(
                setId, skill, skill, "SINGLE_CHOICE", null,
                30, BigDecimal.TEN, displayOrder);
        section.setTestId(testId);
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private PracticeAttempt completedAttempt(long id, PracticeSection section, boolean graded) {
        PracticeAttempt attempt = new PracticeAttempt(
                USER_ID, SET_ID, TEST_ID, section.getSkill(), section.getId());
        if (graded) {
            attempt.markGraded(BigDecimal.TEN, BigDecimal.TEN, "{}", "{}");
        } else {
            attempt.markSubmitted(BigDecimal.TEN, BigDecimal.TEN, "{}");
        }
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }
}
