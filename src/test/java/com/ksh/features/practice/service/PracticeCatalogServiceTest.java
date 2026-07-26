package com.ksh.features.practice.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogBatch;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogQuery;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository.GlobalResumeProjection;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

    @BeforeEach
    void setUpGlobalResumeDefault() {
        lenient().when(attemptRepository.findGlobalResumeCandidates(
                anyLong(), anyList(), any()))
                .thenReturn(List.of());
        lenient().when(
                        attemptRepository.findCoherentAttemptIdentityIds(
                                anyLong(), anyList()))
                .thenReturn(List.of());
    }

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
                "buổi sáng", "READING", null, request))
                .thenReturn(new PageImpl<>(List.of(set), request, 25));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(reading, listening));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(readingAttempt, listeningAttempt));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery(
                        "  buổi sáng  ", " reading ", "ALL", null, 0));

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
        assertThat(batch.items().get(0).stateLabel()).isEqualTo("Đã có kết quả");

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
                USER_ID, new PracticeCatalogQuery("", "ALL", "Q51", 99L, 0));

        assertThat(batch.items()).isEmpty();
        assertThat(batch.totalElements()).isZero();
        assertThat(batch.hasMore()).isFalse();
        assertThat(batch.classId()).isEqualTo(99L);
        assertThat(batch.classes()).extracting(option -> option.id()).containsExactly(15L);
        verify(setRepository, never()).findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), anyLong(), anyList(),
                anyLong(), anyString(), anyString(), any(), any());
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
                eq(0L), eq(""), eq(""), eq(null), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(section));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(attempt));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery(null, "invalid", "Q51", null, -4));

        assertThat(batch.batch()).isZero();
        assertThat(batch.skill()).isEqualTo("ALL");
        assertThat(batch.writingTask()).isEqualTo("ALL");
        assertThat(batch.items().get(0).state()).isEqualTo("SUBMITTED");
    }

    @Test
    void duplicateRepositoryRowsRenderAsOneCatalogCard() {
        PracticeSet set = set(SET_ID, "Bộ đề nhiều kỹ năng", PracticeSet.SKILL_READING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection reading = section(31L, SET_ID, TEST_ID, "READING", 1);
        PracticeSection listening = section(32L, SET_ID, TEST_ID, "LISTENING", 2);
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), eq(USER_ID), eq(List.of(-1L)),
                eq(0L), eq(""), eq(""), eq(null), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set, set), request, 2));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(reading, listening));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of());

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery(null, "ALL", "ALL", null, 0));

        assertThat(batch.items()).hasSize(1);
        assertThat(batch.items().get(0).id()).isEqualTo(SET_ID);
        assertThat(batch.items().get(0).skills()).extracting(skill -> skill.code())
                .containsExactly("LISTENING", "READING");
        verify(testRepository).findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID));
    }

    @Test
    void speakingCatalogStateDescribesFeedbackInsteadOfFullSkillScoring() {
        PracticeSet set = set(SET_ID, "Luyện nói", PracticeSet.SKILL_SPEAKING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection speaking = section(31L, SET_ID, TEST_ID, "SPEAKING", 1);
        PracticeAttempt graded = completedAttempt(41L, speaking, true);
        PracticeAttempt queued = completedAttempt(42L, speaking, false);
        queued.setAnalysisStatus(PracticeAttempt.ANALYSIS_PROCESSING);
        PracticeAttempt failed = completedAttempt(43L, speaking, false);
        failed.markAnalysisFailed("PROVIDER_UNAVAILABLE");
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), eq(USER_ID), eq(List.of(-1L)),
                eq(0L), eq(""), eq("SPEAKING"), eq(null), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(speaking));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(graded), List.of(queued), List.of(failed));

        PracticeCatalogQuery query =
                new PracticeCatalogQuery("", "SPEAKING", "Q54", null, 0);
        PracticeCatalogBatch gradedBatch = service.loadBatch(USER_ID, query);
        PracticeCatalogBatch queuedBatch = service.loadBatch(USER_ID, query);
        PracticeCatalogBatch failedBatch = service.loadBatch(USER_ID, query);

        assertThat(gradedBatch.items().get(0).stateLabel()).isEqualTo("Đã xử lý phản hồi");
        assertThat(gradedBatch.writingTask()).isEqualTo("ALL");
        assertThat(queuedBatch.items().get(0).stateLabel()).isEqualTo("Đang xử lý phản hồi");
        assertThat(failedBatch.items().get(0).stateLabel()).isEqualTo("Chưa thể xử lý phản hồi");
    }

    @Test
    void writingTaskFilterIsNormalizedAndAppliedToAuthorizedSearchAndPaging() {
        PracticeSet set = set(SET_ID, "Luyện viết 53", PracticeSet.SKILL_WRITING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection writing = section(31L, SET_ID, TEST_ID, "WRITING", 1);
        PageRequest request = PageRequest.of(2, PracticeCatalogService.BATCH_SIZE);

        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS,
                USER_ID,
                List.of(-1L),
                0L,
                "biểu đồ",
                "WRITING",
                WritingTaskType.Q53,
                request))
                .thenReturn(new PageImpl<>(List.of(set), request, 40));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(writing));
        when(attemptRepository.findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of());

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID,
                new PracticeCatalogQuery(
                        " biểu đồ ", "writing", "q53", null, 2));

        assertThat(batch.skill()).isEqualTo("WRITING");
        assertThat(batch.writingTask()).isEqualTo("Q53");
        assertThat(batch.search()).isEqualTo("biểu đồ");
        assertThat(batch.batch()).isEqualTo(2);
        assertThat(batch.hasMore()).isTrue();
        assertThat(batch.items()).extracting(item -> item.id()).containsExactly(SET_ID);
    }

    @Test
    void unknownWritingTaskFailsSafelyToAllWithoutDroppingWritingSkill() {
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);
        when(learnerAccessService.activeClassIds(USER_ID)).thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS,
                USER_ID,
                List.of(-1L),
                0L,
                "",
                "WRITING",
                null,
                request))
                .thenReturn(new PageImpl<>(List.of(), request, 0));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID,
                new PracticeCatalogQuery("", "WRITING", "Q99<script>", null, 0));

        assertThat(batch.skill()).isEqualTo("WRITING");
        assertThat(batch.writingTask()).isEqualTo("ALL");
        assertThat(batch.items()).isEmpty();
    }

    @Test
    void globalResumeIsIndependentOfCurrentPageSearchAndSkillFilter() {
        PracticeSet visiblePageSet =
                set(SET_ID, "Kết quả tìm kiếm", PracticeSet.SKILL_READING);
        PageRequest request =
                PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);
        GlobalResumeProjection resume = mock(GlobalResumeProjection.class);
        when(resume.getAttemptId()).thenReturn(901L);
        when(resume.getSetId()).thenReturn(99L);
        when(resume.getTestId()).thenReturn(199L);
        when(resume.getSectionId()).thenReturn(299L);
        when(resume.getSetTitle()).thenReturn("Bộ đề ngoài trang hiện tại");
        when(resume.getTestTitle()).thenReturn("Bài Writing đang làm");
        when(resume.getSkill()).thenReturn("WRITING");
        when(resume.getActivityAt())
                .thenReturn(LocalDateTime.parse("2026-07-25T12:00:00"));

        when(learnerAccessService.activeClassIds(USER_ID))
                .thenReturn(List.of(15L));
        when(classRepository.findAllById(List.of(15L)))
                .thenReturn(List.of());
        when(attemptRepository.findGlobalResumeCandidates(
                USER_ID, List.of(15L), PageRequest.of(0, 1)))
                .thenReturn(List.of(resume));
        when(setRepository.findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), eq(USER_ID),
                eq(List.of(15L)), eq(0L), eq("đọc"), eq("READING"),
                eq(null), eq(request)))
                .thenReturn(new PageImpl<>(
                        List.of(visiblePageSet), request, 30));
        when(testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of());
        when(sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of());
        when(attemptRepository
                .findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                        USER_ID,
                        List.of(SET_ID),
                        PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of());

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID,
                new PracticeCatalogQuery(
                        "đọc", "READING", "ALL", null, 0));

        assertThat(batch.globalResume()).isNotNull();
        assertThat(batch.globalResume().attemptId()).isEqualTo(901L);
        assertThat(batch.globalResume().setId()).isEqualTo(99L);
        assertThat(batch.globalResume().setTitle())
                .isEqualTo("Bộ đề ngoài trang hiện tại");
        assertThat(batch.items()).extracting(item -> item.id())
                .containsExactly(SET_ID);
    }

    @Test
    void completeLookingIncoherentAttemptIsStaleAndNeverProducesCardResume() {
        PracticeSet set =
                set(SET_ID, "Bộ đề cần bắt đầu lại", PracticeSet.SKILL_READING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection section =
                section(31L, SET_ID, TEST_ID, "READING", 1);
        PracticeAttempt incoherent =
                new PracticeAttempt(USER_ID, SET_ID, TEST_ID, "READING", 31L);
        incoherent.lockPublishedVersion(101L, 102L, 103L, 104L);
        ReflectionTestUtils.setField(incoherent, "id", 401L);
        PageRequest request =
                PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(learnerAccessService.activeClassIds(USER_ID))
                .thenReturn(List.of());
        when(setRepository.findLearnerVisiblePublished(
                anyString(), anyString(), anyString(), eq(USER_ID),
                eq(List.of(-1L)), eq(0L), eq(""), eq(""), eq(null),
                eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(section));
        when(attemptRepository
                .findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                        USER_ID,
                        List.of(SET_ID),
                        PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(incoherent));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID,
                new PracticeCatalogQuery("", "ALL", "ALL", null, 0));

        assertThat(batch.items()).singleElement().satisfies(card -> {
            assertThat(card.state()).isEqualTo("STALE");
            assertThat(card.resumeAttemptId()).isNull();
        });
        assertThat(batch.globalResume()).isNull();
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
