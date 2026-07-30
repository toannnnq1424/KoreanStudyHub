package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogBatch;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogQuery;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository.CatalogAttemptStateProjection;
import com.ksh.features.practice.repository.PracticeAttemptRepository.CatalogCompletedSectionProjection;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @InjectMocks
    private PracticeCatalogService service;

    @BeforeEach
    void setUpGlobalResumeDefault() {
        lenient().when(attemptRepository.findGlobalCatalogResumeCandidates(
                anyLong(), any(LocalDateTime.class), any()))
                .thenReturn(List.of());
        lenient().when(attemptRepository.findCatalogCompletedSections(
                        anyLong(), anyList()))
                .thenReturn(List.of());
        lenient().when(attemptRepository.findCatalogAttemptStateCandidates(
                        anyLong(), anyList(), anyString()))
                .thenReturn(List.of());
        lenient().when(attemptRepository.findAllById(anyList()))
                .thenReturn(List.of());
    }

    @Test
    void loadsOneBoundedBatchWithRealGraphCountsAndProgress() {
        PracticeSet set = set(SET_ID, "Buổi sáng tiếng Hàn", PracticeSet.SKILL_READING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection listening = section(31L, SET_ID, TEST_ID, "LISTENING", 1);
        PracticeSection reading = section(32L, SET_ID, TEST_ID, "READING", 2);
        PracticeAttempt readingAttempt = completedAttempt(42L, reading, true);
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED, PracticeSet.SCOPE_GLOBAL,
                "buổi sáng", "READING", null, request))
                .thenReturn(new PageImpl<>(List.of(set), request, 25));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(reading, listening));
        when(attemptRepository.findCatalogCompletedSections(
                USER_ID, List.of(32L, 31L)))
                .thenReturn(List.of(
                        completedSection(SET_ID, 31L),
                        completedSection(SET_ID, 32L)));
        when(attemptRepository.findCatalogAttemptStateCandidates(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(stateCandidate(42L, SET_ID, 2)));
        when(attemptRepository.findAllById(List.of(42L)))
                .thenReturn(List.of(readingAttempt));

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
        verify(attemptRepository).findCatalogCompletedSections(
                USER_ID, List.of(32L, 31L));
        verify(attemptRepository).findCatalogAttemptStateCandidates(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED);
        verify(attemptRepository).findAllById(List.of(42L));
    }

    @Test
    void legacyClassFilterIsIgnoredByTheStandaloneCatalog() {
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);
        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED, PracticeSet.SCOPE_GLOBAL,
                "", "", null, request))
                .thenReturn(new PageImpl<>(List.of(), request, 0));

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID, new PracticeCatalogQuery("", "ALL", "Q51", 99L, 0));

        assertThat(batch.items()).isEmpty();
        assertThat(batch.totalElements()).isZero();
        assertThat(batch.hasMore()).isFalse();
        assertThat(batch.classId()).isNull();
        assertThat(batch.classes()).isEmpty();
        verify(setRepository).findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED, PracticeSet.SCOPE_GLOBAL,
                "", "", null, request);
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

        when(setRepository.findPublishedGlobalCatalog(
                anyString(), anyString(), eq(""), eq(""), eq(null), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(section));
        when(attemptRepository.findCatalogAttemptStateCandidates(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(stateCandidate(41L, SET_ID, 2)));
        when(attemptRepository.findAllById(List.of(41L)))
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

        when(setRepository.findPublishedGlobalCatalog(
                anyString(), anyString(), eq(""), eq(""), eq(null), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set, set), request, 2));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(reading, listening));
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
        PracticeAttempt unavailable =
                completedAttempt(44L, speaking, false);
        unavailable.markAnalysisUnavailable(
                BigDecimal.TEN,
                "{}",
                null,
                "SPEAKING_AI_DISABLED",
                false,
                LocalDateTime.of(2026, 7, 29, 10, 0));
        PageRequest request = PageRequest.of(0, PracticeCatalogService.BATCH_SIZE);

        when(setRepository.findPublishedGlobalCatalog(
                anyString(), anyString(), eq(""), eq("SPEAKING"), eq(null), eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(speaking));
        when(attemptRepository.findCatalogAttemptStateCandidates(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(
                        List.of(stateCandidate(41L, SET_ID, 2)),
                        List.of(stateCandidate(42L, SET_ID, 2)),
                        List.of(stateCandidate(43L, SET_ID, 2)),
                        List.of(stateCandidate(44L, SET_ID, 2)));
        when(attemptRepository.findAllById(anyList()))
                .thenReturn(
                        List.of(graded),
                        List.of(queued),
                        List.of(failed),
                        List.of(unavailable));

        PracticeCatalogQuery query =
                new PracticeCatalogQuery("", "SPEAKING", "Q54", null, 0);
        PracticeCatalogBatch gradedBatch = service.loadBatch(USER_ID, query);
        PracticeCatalogBatch queuedBatch = service.loadBatch(USER_ID, query);
        PracticeCatalogBatch failedBatch = service.loadBatch(USER_ID, query);
        PracticeCatalogBatch unavailableBatch =
                service.loadBatch(USER_ID, query);

        assertThat(gradedBatch.items().get(0).stateLabel()).isEqualTo("Đã xử lý phản hồi");
        assertThat(gradedBatch.writingTask()).isEqualTo("ALL");
        assertThat(queuedBatch.items().get(0).stateLabel()).isEqualTo("Đang xử lý phản hồi");
        assertThat(failedBatch.items().get(0).stateLabel()).isEqualTo("Chưa thể xử lý phản hồi");
        assertThat(unavailableBatch.items().get(0).state())
                .isEqualTo("UNAVAILABLE");
        assertThat(unavailableBatch.items().get(0).stateLabel())
                .isEqualTo("Không khả dụng");
    }

    @Test
    void writingTaskFilterIsNormalizedAndAppliedToAuthorizedSearchAndPaging() {
        PracticeSet set = set(SET_ID, "Luyện viết 53", PracticeSet.SKILL_WRITING);
        PracticeTest test = test(TEST_ID, SET_ID);
        PracticeSection writing = section(31L, SET_ID, TEST_ID, "WRITING", 1);
        PageRequest request = PageRequest.of(2, PracticeCatalogService.BATCH_SIZE);

        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                "biểu đồ",
                "WRITING",
                WritingTaskType.Q53,
                request))
                .thenReturn(new PageImpl<>(List.of(set), request, 40));
        when(testRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository.findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(writing));
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
        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
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

        when(attemptRepository.findGlobalCatalogResumeCandidates(
                eq(USER_ID),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(resume));
        when(setRepository.findPublishedGlobalCatalog(
                anyString(), anyString(), eq("đọc"), eq("READING"),
                eq(null), eq(request)))
                .thenReturn(new PageImpl<>(
                        List.of(visiblePageSet), request, 30));
        when(testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of());
        when(sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
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

        when(setRepository.findPublishedGlobalCatalog(
                anyString(), anyString(), eq(""), eq(""), eq(null),
                eq(request)))
                .thenReturn(new PageImpl<>(List.of(set), request, 1));
        when(testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(test));
        when(sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of(section));
        when(attemptRepository.findCatalogAttemptStateCandidates(
                USER_ID, List.of(SET_ID), PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(List.of(stateCandidate(401L, SET_ID, 1)));
        when(attemptRepository.findAllById(List.of(401L)))
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

    @Test
    void tenThousandSetCatalogKeepsRepositoryCallsConstantAndHydratesTwelveStateCandidates() {
        PageRequest request =
                PageRequest.of(417, PracticeCatalogService.BATCH_SIZE);
        List<PracticeSet> pageSets = LongStream.rangeClosed(1, 12)
                .mapToObj(offset -> set(
                        5_000L + offset,
                        "Bộ đề trang lớn " + offset,
                        PracticeSet.SKILL_READING))
                .toList();
        List<Long> pageSetIds =
                pageSets.stream().map(PracticeSet::getId).toList();
        List<PracticeAttempt> stateAttempts = LongStream.rangeClosed(1, 12)
                .mapToObj(offset -> {
                    PracticeAttempt attempt = new PracticeAttempt(
                            USER_ID,
                            5_000L + offset,
                            6_000L + offset,
                            "READING",
                            7_000L + offset);
                    ReflectionTestUtils.setField(
                            attempt, "id", 8_000L + offset);
                    return attempt;
                })
                .toList();
        List<Long> stateAttemptIds =
                stateAttempts.stream().map(PracticeAttempt::getId).toList();
        List<CatalogAttemptStateProjection> stateRows =
                LongStream.rangeClosed(1, 12)
                        .mapToObj(offset -> stateCandidate(
                                8_000L + offset,
                                5_000L + offset,
                                1))
                        .toList();

        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                "",
                "",
                null,
                request))
                .thenReturn(new PageImpl<>(pageSets, request, 10_000));
        when(testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(pageSetIds))
                .thenReturn(List.of());
        when(sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(pageSetIds))
                .thenReturn(List.of());
        when(attemptRepository.findCatalogAttemptStateCandidates(
                USER_ID, pageSetIds, PracticeAttempt.STATUS_DISCARDED))
                .thenReturn(stateRows);
        when(attemptRepository.findAllById(stateAttemptIds))
                .thenReturn(stateAttempts);

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID,
                new PracticeCatalogQuery("", "ALL", "ALL", null, 417));

        assertThat(batch.items()).hasSize(PracticeCatalogService.BATCH_SIZE);
        assertThat(batch.totalElements()).isEqualTo(10_000);
        assertThat(batch.batch()).isEqualTo(417);
        assertThat(batch.hasMore()).isTrue();
        verify(setRepository).findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                "",
                "",
                null,
                request);
        verify(testRepository)
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(pageSetIds);
        verify(sectionRepository)
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(pageSetIds);
        verify(attemptRepository).findGlobalCatalogResumeCandidates(
                eq(USER_ID),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 1)));
        verify(attemptRepository).findCatalogAttemptStateCandidates(
                USER_ID, pageSetIds, PracticeAttempt.STATUS_DISCARDED);
        verify(attemptRepository).findAllById(stateAttemptIds);
        verify(attemptRepository, never())
                .findCatalogCompletedSections(anyLong(), anyList());
        verifyNoMoreInteractions(
                setRepository,
                testRepository,
                sectionRepository,
                attemptRepository);
    }

    @Test
    void batchBeyondLastPageClampsToTheLastRealServerPage() {
        PageRequest requested =
                PageRequest.of(999, PracticeCatalogService.BATCH_SIZE);
        PageRequest last =
                PageRequest.of(1, PracticeCatalogService.BATCH_SIZE);
        PracticeSet lastSet =
                set(SET_ID, "Bộ đề cuối", PracticeSet.SKILL_READING);

        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                "",
                "",
                null,
                requested))
                .thenReturn(new PageImpl<>(List.of(), requested, 13));
        when(setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                "",
                "",
                null,
                last))
                .thenReturn(new PageImpl<>(List.of(lastSet), last, 13));
        when(testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of());
        when(sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(List.of(SET_ID)))
                .thenReturn(List.of());

        PracticeCatalogBatch batch = service.loadBatch(
                USER_ID,
                new PracticeCatalogQuery("", "ALL", "ALL", null, 999));

        assertThat(batch.batch()).isEqualTo(1);
        assertThat(batch.items()).extracting(item -> item.id())
                .containsExactly(SET_ID);
        assertThat(batch.firstItemNumber()).isEqualTo(13);
        assertThat(batch.lastItemNumber()).isEqualTo(13);
        assertThat(batch.totalElements()).isEqualTo(13);
        assertThat(batch.hasPrevious()).isTrue();
        assertThat(batch.hasMore()).isFalse();
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

    private CatalogCompletedSectionProjection completedSection(
            long setId,
            long sectionId
    ) {
        return new CatalogCompletedSectionProjection() {
            @Override
            public Long getSetId() {
                return setId;
            }

            @Override
            public Long getSectionId() {
                return sectionId;
            }
        };
    }

    private CatalogAttemptStateProjection stateCandidate(
            long attemptId,
            long setId,
            int priority
    ) {
        return new CatalogAttemptStateProjection() {
            @Override
            public Long getAttemptId() {
                return attemptId;
            }

            @Override
            public Long getSetId() {
                return setId;
            }

            @Override
            public Integer getStatePriority() {
                return priority;
            }
        };
    }
}
