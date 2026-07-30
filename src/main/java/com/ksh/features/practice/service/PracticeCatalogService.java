package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.dto.PracticeDtos;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogBatch;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogQuery;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogSkill;
import com.ksh.features.practice.dto.PracticeDtos.PracticeGlobalResume;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository.CatalogAttemptStateProjection;
import com.ksh.features.practice.repository.PracticeAttemptRepository.CatalogCompletedSectionProjection;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PracticeCatalogService {

    static final int BATCH_SIZE = 12;
    private static final int MAX_SEARCH_LENGTH = 120;
    private static final List<String> SKILL_ORDER =
            List.of("LISTENING", "READING", "WRITING", "SPEAKING");
    private static final Set<String> ALLOWED_SKILLS = Set.copyOf(SKILL_ORDER);
    private static final Set<String> ALLOWED_WRITING_TASKS =
            Set.of("Q51", "Q52", "Q53", "Q54");
    private static final PracticeAttemptStatePolicy ATTEMPT_STATE =
            PracticeAttemptStatePolicy.INSTANCE;

    private final PracticeSetRepository setRepository;
    private final PracticeTestRepository testRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeAttemptRepository attemptRepository;

    public PracticeCatalogService(PracticeSetRepository setRepository,
                                  PracticeTestRepository testRepository,
                                  PracticeSectionRepository sectionRepository,
                                  PracticeAttemptRepository attemptRepository) {
        this.setRepository = setRepository;
        this.testRepository = testRepository;
        this.sectionRepository = sectionRepository;
        this.attemptRepository = attemptRepository;
    }

    public PracticeCatalogBatch loadBatch(Long userId, PracticeCatalogQuery rawQuery) {
        PracticeCatalogQuery query = normalize(rawQuery);
        PracticeGlobalResume globalResume = loadGlobalResume(userId);

        int effectiveBatch = query.batch();
        Page<PracticeSet> setPage = findVisibleSetPage(
                query,
                effectiveBatch);
        if (setPage.isEmpty() && setPage.getTotalElements() > 0 && effectiveBatch > 0) {
            effectiveBatch = Math.max(0, setPage.getTotalPages() - 1);
            setPage = findVisibleSetPage(
                    query,
                    effectiveBatch);
        } else if (setPage.isEmpty() && setPage.getTotalElements() == 0) {
            effectiveBatch = 0;
        }

        if (setPage.isEmpty()) {
            return new PracticeCatalogBatch(
                    List.of(), globalResume, List.of(), query.search(), query.skill(),
                    query.writingTask(), null,
                    effectiveBatch, BATCH_SIZE, setPage.getTotalElements(), false);
        }

        List<PracticeSet> sets = setPage.getContent().stream()
                .collect(Collectors.toMap(
                        PracticeSet::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values().stream()
                .toList();
        List<Long> setIds = sets.stream().map(PracticeSet::getId).toList();
        List<PracticeTest> tests = testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(setIds);
        List<PracticeSection> sections = sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(setIds);
        List<Long> sectionIds = sections.stream()
                .map(PracticeSection::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<CatalogCompletedSectionProjection> completedSectionRows =
                sectionIds.isEmpty()
                        ? List.of()
                        : attemptRepository.findCatalogCompletedSections(
                                userId, sectionIds);
        Map<Long, Set<Long>> completedSectionIdsBySet =
                completedSectionRows.stream()
                        .filter(row -> row.getSetId() != null
                                && row.getSectionId() != null)
                        .collect(Collectors.groupingBy(
                                CatalogCompletedSectionProjection::getSetId,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        CatalogCompletedSectionProjection::getSectionId,
                                        Collectors.toCollection(LinkedHashSet::new))));

        List<CatalogAttemptStateProjection> stateRows =
                attemptRepository.findCatalogAttemptStateCandidates(
                        userId, setIds, PracticeAttempt.STATUS_DISCARDED);
        List<Long> stateAttemptIds = stateRows.stream()
                .map(CatalogAttemptStateProjection::getAttemptId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, PracticeAttempt> stateAttemptsById =
                (stateAttemptIds.isEmpty()
                        ? List.<PracticeAttempt>of()
                        : attemptRepository.findAllById(stateAttemptIds))
                        .stream()
                        .filter(attempt -> attempt.getId() != null)
                        .collect(Collectors.toMap(
                                PracticeAttempt::getId,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new));
        Map<Long, CatalogStateCandidate> stateBySet = new LinkedHashMap<>();
        for (CatalogAttemptStateProjection row : stateRows) {
            if (row.getSetId() == null || row.getAttemptId() == null) continue;
            PracticeAttempt attempt = stateAttemptsById.get(row.getAttemptId());
            if (attempt == null) continue;
            int priority = row.getStatePriority() == null
                    ? 2
                    : row.getStatePriority();
            stateBySet.putIfAbsent(
                    row.getSetId(),
                    new CatalogStateCandidate(attempt, priority));
        }

        Map<Long, List<PracticeTest>> testsBySet = groupBy(tests, PracticeTest::getSetId);
        Map<Long, List<PracticeSection>> sectionsBySet = groupBy(sections, PracticeSection::getSetId);
        List<PracticeCatalogCard> cards = sets.stream()
                .map(set -> toCard(
                        set,
                        testsBySet.getOrDefault(set.getId(), List.of()),
                        sectionsBySet.getOrDefault(set.getId(), List.of()),
                        completedSectionIdsBySet.getOrDefault(
                                set.getId(), Set.of()),
                        stateBySet.get(set.getId())))
                .toList();

        return new PracticeCatalogBatch(
                cards, globalResume, List.of(), query.search(), query.skill(), query.writingTask(),
                null,
                effectiveBatch, BATCH_SIZE, setPage.getTotalElements(), setPage.hasNext());
    }

    private Page<PracticeSet> findVisibleSetPage(
            PracticeCatalogQuery query,
            int batch
    ) {
        return setRepository.findPublishedGlobalCatalog(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                query.search(),
                "ALL".equals(query.skill()) ? "" : query.skill(),
                "ALL".equals(query.writingTask())
                        ? null
                        : WritingTaskType.valueOf(query.writingTask()),
                PageRequest.of(batch, BATCH_SIZE));
    }

    private PracticeCatalogCard toCard(PracticeSet set,
                                       List<PracticeTest> tests,
                                       List<PracticeSection> sections,
                                       Set<Long> completedSectionIds,
                                       CatalogStateCandidate stateCandidate) {
        List<PracticeCatalogSkill> skills = deriveSkills(set, sections);
        String primarySkill = skills.isEmpty() ? "READING" : skills.get(0).code();
        int completedTests = completedTestCount(
                tests, sections, completedSectionIds);
        AttemptState state = resolveState(stateCandidate);
        return new PracticeCatalogCard(
                set.getId(), set.getTitle(), set.getDescription(), primarySkill,
                skills, tests.size(), completedTests, "Công khai trong KSH",
                state.code(), state.label(), state.resumeAttemptId());
    }

    private List<PracticeCatalogSkill> deriveSkills(PracticeSet set,
                                                    List<PracticeSection> sections) {
        Set<String> found = sections.stream()
                .map(PracticeSection::getSkill)
                .filter(skill -> skill != null && ALLOWED_SKILLS.contains(skill.toUpperCase(Locale.ROOT)))
                .map(skill -> skill.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (found.isEmpty() && set.getSkill() != null
                && ALLOWED_SKILLS.contains(set.getSkill().toUpperCase(Locale.ROOT))) {
            found.add(set.getSkill().toUpperCase(Locale.ROOT));
        }
        return found.stream()
                .sorted(Comparator.comparingInt(SKILL_ORDER::indexOf))
                .map(skill -> new PracticeCatalogSkill(skill, skillLabel(skill)))
                .toList();
    }

    private int completedTestCount(List<PracticeTest> tests,
                                   List<PracticeSection> sections,
                                   Set<Long> completedSectionIds) {
        Map<Long, List<PracticeSection>> sectionsByTest = groupBy(
                sections.stream().filter(section -> section.getTestId() != null).toList(),
                PracticeSection::getTestId);

        int completed = 0;
        for (PracticeTest test : tests) {
            List<PracticeSection> testSections = sectionsByTest.getOrDefault(test.getId(), List.of());
            if (!testSections.isEmpty()
                    && testSections.stream().allMatch(section -> completedSectionIds.contains(section.getId()))) {
                completed++;
            }
        }
        return completed;
    }

    private AttemptState resolveState(CatalogStateCandidate candidate) {
        if (candidate == null || candidate.attempt() == null) {
            return new AttemptState("NOT_STARTED", "Chưa bắt đầu", null);
        }
        PracticeAttemptStatePolicy.Presentation presentation =
                ATTEMPT_STATE.presentation(
                        candidate.attempt(),
                        candidate.statePriority() == 0
                                || candidate.statePriority() >= 2);
        return new AttemptState(
                presentation.code(),
                presentation.label(),
                presentation.resumeAttemptId());
    }

    private PracticeGlobalResume loadGlobalResume(Long userId) {
        return attemptRepository.findGlobalCatalogResumeCandidates(
                        userId,
                        LocalDateTime.now(),
                        PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(candidate -> new PracticeGlobalResume(
                        candidate.getAttemptId(),
                        candidate.getSetId(),
                        candidate.getTestId(),
                        candidate.getSectionId(),
                        candidate.getSetTitle(),
                        candidate.getTestTitle(),
                        candidate.getSkill(),
                        PracticeDtos.getSkillLabel(candidate.getSkill()),
                        candidate.getActivityAt()))
                .orElse(null);
    }

    private PracticeCatalogQuery normalize(PracticeCatalogQuery raw) {
        String search = raw == null || raw.search() == null ? "" : raw.search().strip();
        if (search.length() > MAX_SEARCH_LENGTH) {
            search = search.substring(0, MAX_SEARCH_LENGTH);
        }
        String skill = raw == null || raw.skill() == null
                ? "ALL"
                : raw.skill().strip().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SKILLS.contains(skill)) skill = "ALL";
        String writingTask = raw == null || raw.writingTask() == null
                ? "ALL"
                : raw.writingTask().strip().toUpperCase(Locale.ROOT);
        if (!"WRITING".equals(skill) || !ALLOWED_WRITING_TASKS.contains(writingTask)) {
            writingTask = "ALL";
        }
        int batch = raw == null ? 0 : Math.max(0, raw.batch());
        return new PracticeCatalogQuery(search, skill, writingTask, null, batch);
    }

    private String skillLabel(String skill) {
        return switch (skill) {
            case "LISTENING" -> "Nghe";
            case "READING" -> "Đọc";
            case "WRITING" -> "Viết";
            case "SPEAKING" -> "Nói";
            default -> skill;
        };
    }

    private static <T, K> Map<K, List<T>> groupBy(List<T> values, Function<T, K> key) {
        return values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
    }

    private record AttemptState(String code, String label, Long resumeAttemptId) {
    }

    private record CatalogStateCandidate(
            PracticeAttempt attempt,
            int statePriority
    ) {
    }
}
