package com.ksh.features.practice.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogClassOption;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogBatch;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogQuery;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogSkill;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
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

    private final PracticeSetRepository setRepository;
    private final PracticeTestRepository testRepository;
    private final PracticeSectionRepository sectionRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final ClassRepository classRepository;
    private final PracticeLearnerAccessService learnerAccessService;

    public PracticeCatalogService(PracticeSetRepository setRepository,
                                  PracticeTestRepository testRepository,
                                  PracticeSectionRepository sectionRepository,
                                  PracticeAttemptRepository attemptRepository,
                                  ClassRepository classRepository,
                                  PracticeLearnerAccessService learnerAccessService) {
        this.setRepository = setRepository;
        this.testRepository = testRepository;
        this.sectionRepository = sectionRepository;
        this.attemptRepository = attemptRepository;
        this.classRepository = classRepository;
        this.learnerAccessService = learnerAccessService;
    }

    public PracticeCatalogBatch loadBatch(Long userId, PracticeCatalogQuery rawQuery) {
        PracticeCatalogQuery query = normalize(rawQuery);
        List<Long> activeClassIds = learnerAccessService.activeClassIds(userId);
        List<Long> queryClassIds = activeClassIds.isEmpty() ? List.of(-1L) : activeClassIds;
        List<PracticeCatalogClassOption> classOptions = loadClassOptions(activeClassIds);
        if (query.classId() != null && !activeClassIds.contains(query.classId())) {
            return new PracticeCatalogBatch(
                    List.of(), classOptions, query.search(), query.skill(), query.classId(),
                    query.batch(), BATCH_SIZE, 0, false);
        }
        long selectedClassId = query.classId() == null ? 0L : query.classId();

        Page<PracticeSet> setPage = setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS,
                userId,
                queryClassIds,
                selectedClassId,
                query.search(),
                "ALL".equals(query.skill()) ? "" : query.skill(),
                PageRequest.of(query.batch(), BATCH_SIZE));

        if (setPage.isEmpty()) {
            return new PracticeCatalogBatch(
                    List.of(), classOptions, query.search(), query.skill(),
                    selectedClassId == 0 ? null : selectedClassId,
                    query.batch(), BATCH_SIZE, setPage.getTotalElements(), setPage.hasNext());
        }

        List<PracticeSet> sets = setPage.getContent();
        List<Long> setIds = sets.stream().map(PracticeSet::getId).toList();
        List<PracticeTest> tests = testRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(setIds);
        List<PracticeSection> sections = sectionRepository
                .findBySetIdInOrderBySetIdAscDisplayOrderAsc(setIds);
        List<PracticeAttempt> attempts = attemptRepository
                .findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc(
                        userId, setIds, PracticeAttempt.STATUS_DISCARDED);

        Map<Long, List<PracticeTest>> testsBySet = groupBy(tests, PracticeTest::getSetId);
        Map<Long, List<PracticeSection>> sectionsBySet = groupBy(sections, PracticeSection::getSetId);
        Map<Long, List<PracticeAttempt>> attemptsBySet = groupBy(attempts, PracticeAttempt::getSetId);
        Map<Long, String> classNames = classOptions.stream()
                .collect(Collectors.toMap(PracticeCatalogClassOption::id,
                        PracticeCatalogClassOption::name));

        List<PracticeCatalogCard> cards = sets.stream()
                .map(set -> toCard(
                        set,
                        testsBySet.getOrDefault(set.getId(), List.of()),
                        sectionsBySet.getOrDefault(set.getId(), List.of()),
                        attemptsBySet.getOrDefault(set.getId(), List.of()),
                        classNames))
                .toList();

        return new PracticeCatalogBatch(
                cards, classOptions, query.search(), query.skill(),
                selectedClassId == 0 ? null : selectedClassId,
                query.batch(), BATCH_SIZE, setPage.getTotalElements(), setPage.hasNext());
    }

    private PracticeCatalogCard toCard(PracticeSet set,
                                       List<PracticeTest> tests,
                                       List<PracticeSection> sections,
                                       List<PracticeAttempt> attempts,
                                       Map<Long, String> classNames) {
        List<PracticeCatalogSkill> skills = deriveSkills(set, sections);
        String primarySkill = skills.isEmpty() ? "READING" : skills.get(0).code();
        int completedTests = completedTestCount(tests, sections, attempts);
        AttemptState state = resolveState(attempts);
        String visibility = PracticeSet.SCOPE_CLASS.equals(set.getScope())
                ? classNames.getOrDefault(set.getClassId(), "Lớp học")
                : "Công khai trong KSH";

        return new PracticeCatalogCard(
                set.getId(), set.getTitle(), set.getDescription(), primarySkill,
                skills, tests.size(), completedTests, visibility,
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
                                   List<PracticeAttempt> attempts) {
        Set<Long> completedSectionIds = attempts.stream()
                .filter(attempt -> PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                        || PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus()))
                .map(PracticeAttempt::getSectionId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
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

    private AttemptState resolveState(List<PracticeAttempt> attempts) {
        PracticeAttempt resumable = attempts.stream()
                .filter(attempt -> PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus()))
                .max(Comparator.comparing(this::attemptTimestamp)
                        .thenComparing(attempt -> attempt.getId() == null ? 0L : attempt.getId()))
                .orElse(null);
        if (resumable != null) {
            String compatibility = resumable.getVersionCompatibilityStatus();
            if (compatibility != null && !compatibility.isBlank()
                    && !"COMPATIBLE".equalsIgnoreCase(compatibility)) {
                return new AttemptState("STALE", "Cần bắt đầu lại", null);
            }
            return new AttemptState("IN_PROGRESS", "Đang làm", resumable.getId());
        }

        PracticeAttempt latest = attempts.stream()
                .max(Comparator.comparing(this::attemptTimestamp)
                        .thenComparing(attempt -> attempt.getId() == null ? 0L : attempt.getId()))
                .orElse(null);
        if (latest == null) return new AttemptState("NOT_STARTED", "Chưa bắt đầu", null);
        if (PracticeAttempt.STATUS_GRADED.equals(latest.getStatus())) {
            return new AttemptState("SCORED", "Đã có kết quả", null);
        }
        if (!PracticeAttempt.STATUS_SUBMITTED.equals(latest.getStatus())) {
            return new AttemptState("NOT_STARTED", "Chưa bắt đầu", null);
        }

        String analysisStatus = latest.getAnalysisStatus();
        if (analysisStatus == null || analysisStatus.isBlank()) {
            return new AttemptState("SUBMITTED", "Đã nộp", null);
        }
        return switch (analysisStatus) {
            case PracticeAttempt.ANALYSIS_QUEUED, PracticeAttempt.ANALYSIS_PROCESSING ->
                    new AttemptState("SCORING", "Đang chấm", null);
            case PracticeAttempt.ANALYSIS_SUCCEEDED ->
                    new AttemptState("SCORED", "Đã có kết quả", null);
            case PracticeAttempt.ANALYSIS_FAILED -> latest.isObjectiveSkill()
                    ? new AttemptState("PARTIAL", "Có điểm, thiếu phản hồi", null)
                    : new AttemptState("FAILED", "Chấm điểm thất bại", null);
            default -> new AttemptState("SUBMITTED", "Đã nộp", null);
        };
    }

    private LocalDateTime attemptTimestamp(PracticeAttempt attempt) {
        if (attempt.getUpdatedAt() != null) return attempt.getUpdatedAt();
        if (attempt.getCreatedAt() != null) return attempt.getCreatedAt();
        if (attempt.getStartedAt() != null) return attempt.getStartedAt();
        return LocalDateTime.MIN;
    }

    private List<PracticeCatalogClassOption> loadClassOptions(List<Long> classIds) {
        if (classIds.isEmpty()) return List.of();
        return classRepository.findAllById(classIds).stream()
                .sorted(Comparator.comparing(ClassEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(clazz -> new PracticeCatalogClassOption(clazz.getId(), clazz.getName()))
                .toList();
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
        int batch = raw == null ? 0 : Math.max(0, raw.batch());
        Long classId = raw == null || raw.classId() == null || raw.classId() <= 0
                ? null
                : raw.classId();
        return new PracticeCatalogQuery(search, skill, classId, batch);
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
}
