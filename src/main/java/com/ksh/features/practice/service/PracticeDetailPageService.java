package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogSkill;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetTestCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSkillAttemptCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeTestRow;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ksh.features.practice.dto.PracticeDtos.getSkillLabel;

@Service
@Transactional(readOnly = true)
public class PracticeDetailPageService {

    private static final int INITIAL_ATTEMPT_LIMIT = 2;
    private static final String SPEAKING_SCORE_UNAVAILABLE = "Không có điểm Nói tổng hợp";

    private final PracticeSectionRepository sectionRepository;
    private final PracticeAttemptRepository attemptRepository;

    public PracticeDetailPageService(PracticeSectionRepository sectionRepository,
                                     PracticeAttemptRepository attemptRepository) {
        this.sectionRepository = sectionRepository;
        this.attemptRepository = attemptRepository;
    }

    public List<PracticeSetTestCard> buildTestCards(Long setId,
                                                    List<PracticeTestRow> tests,
                                                    Long userId) {
        if (tests == null || tests.isEmpty()) return List.of();

        Set<Long> testIds = tests.stream()
                .map(PracticeTestRow::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<PracticeSection>> sectionsByTest = sectionRepository
                .findBySetIdOrderByDisplayOrderAsc(setId).stream()
                .filter(section -> section.getTestId() != null && testIds.contains(section.getTestId()))
                .collect(Collectors.groupingBy(
                        PracticeSection::getTestId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, List<PracticeAttempt>> attemptsBySection = attemptRepository
                .findBySetIdAndUserIdOrderByCreatedAtDescIdDesc(setId, userId).stream()
                .filter(this::isActiveAttempt)
                .filter(attempt -> attempt.getTestId() != null && testIds.contains(attempt.getTestId()))
                .filter(attempt -> attempt.getSectionId() != null)
                .collect(Collectors.groupingBy(
                        PracticeAttempt::getSectionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return tests.stream()
                .map(test -> toTestCard(test,
                        sectionsByTest.getOrDefault(test.id(), List.of()),
                        attemptsBySection))
                .toList();
    }

    public List<PracticeSkillAttemptCard> buildSkillCards(Long testId,
                                                          List<PracticeSection> sections,
                                                          Long userId) {
        if (sections == null || sections.isEmpty()) return List.of();

        Map<Long, List<PracticeAttempt>> attemptsBySection = attemptRepository
                .findByTestIdAndUserIdOrderByCreatedAtDesc(testId, userId).stream()
                .filter(this::isActiveAttempt)
                .filter(attempt -> attempt.getSectionId() != null)
                .sorted(attemptComparator())
                .collect(Collectors.groupingBy(
                        PracticeAttempt::getSectionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return sections.stream()
                .map(section -> toSkillCard(
                        section,
                        attemptsBySection.getOrDefault(section.getId(), List.of())))
                .toList();
    }

    public List<PracticeCatalogSkill> collectSetSkills(List<PracticeSetTestCard> testCards) {
        if (testCards == null || testCards.isEmpty()) return List.of();
        Map<String, PracticeCatalogSkill> unique = new LinkedHashMap<>();
        for (PracticeSetTestCard testCard : testCards) {
            if (testCard.skills() == null) continue;
            for (PracticeCatalogSkill skill : testCard.skills()) {
                unique.putIfAbsent(skill.code(), skill);
            }
        }
        return List.copyOf(unique.values());
    }

    private PracticeSetTestCard toTestCard(PracticeTestRow test,
                                           List<PracticeSection> sections,
                                           Map<Long, List<PracticeAttempt>> attemptsBySection) {
        List<PracticeCatalogSkill> skills = sections.stream()
                .map(section -> new PracticeCatalogSkill(
                        normalizeSkill(section.getSkill()),
                        getSkillLabel(section.getSkill())))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                PracticeCatalogSkill::code,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new),
                        values -> List.copyOf(values.values())));

        int completedSkillCount = 0;
        PracticeAttempt newestInProgress = null;
        for (PracticeSection section : sections) {
            List<PracticeAttempt> sectionAttempts = attemptsBySection
                    .getOrDefault(section.getId(), List.of());
            if (sectionAttempts.stream().anyMatch(this::isCompletedAttempt)) {
                completedSkillCount++;
            }
            PracticeAttempt inProgress = sectionAttempts.stream()
                    .filter(this::isInProgressAttempt)
                    .min(attemptComparator())
                    .orElse(null);
            if (inProgress != null && (newestInProgress == null
                    || attemptComparator().compare(inProgress, newestInProgress) < 0)) {
                newestInProgress = inProgress;
            }
        }

        int totalSkillCount = sections.size();
        String state;
        String stateLabel;
        if (newestInProgress != null) {
            state = "IN_PROGRESS";
            stateLabel = "Đang làm";
        } else if (totalSkillCount > 0 && completedSkillCount == totalSkillCount) {
            state = "COMPLETED";
            stateLabel = "Đã hoàn thành";
        } else if (completedSkillCount > 0) {
            state = "PARTIAL";
            stateLabel = "Đã làm một phần";
        } else {
            state = "NOT_STARTED";
            stateLabel = "Chưa bắt đầu";
        }

        Integer estimatedMinutes = test.estimatedMinutes();
        if (estimatedMinutes == null) {
            int duration = sections.stream()
                    .map(PracticeSection::getDurationMinutes)
                    .filter(value -> value != null && value > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
            estimatedMinutes = duration > 0 ? duration : null;
        }

        return new PracticeSetTestCard(
                test.id(),
                test.title(),
                test.description(),
                test.displayOrder(),
                estimatedMinutes,
                skills,
                completedSkillCount,
                totalSkillCount,
                state,
                stateLabel,
                newestInProgress == null ? null : newestInProgress.getId());
    }

    private PracticeSkillAttemptCard toSkillCard(PracticeSection section,
                                                 List<PracticeAttempt> sectionAttempts) {
        boolean speaking = "SPEAKING".equals(normalizeSkill(section.getSkill()));
        PracticeAttempt inProgress = sectionAttempts.stream()
                .filter(this::isInProgressAttempt)
                .findFirst()
                .orElse(null);
        List<PracticeAttempt> completed = sectionAttempts.stream()
                .filter(this::isCompletedAttempt)
                .sorted(attemptComparator())
                .toList();

        List<PracticeAttemptCard> attemptCards = new ArrayList<>();
        for (int index = 0; index < completed.size(); index++) {
            PracticeAttempt attempt = completed.get(index);
            attemptCards.add(new PracticeAttemptCard(
                    attempt.getId(),
                    completed.size() - index,
                    formatScore(attempt, speaking),
                    attempt.getStatus(),
                    attemptStatusLabel(attempt),
                    activityAt(attempt),
                    index < INITIAL_ATTEMPT_LIMIT));
        }

        String state;
        String stateLabel;
        if (inProgress != null) {
            state = "IN_PROGRESS";
            stateLabel = "Đang làm";
        } else if (!completed.isEmpty()) {
            state = "COMPLETED";
            stateLabel = "Đã có kết quả";
        } else {
            state = "NOT_STARTED";
            stateLabel = "Chưa bắt đầu";
        }

        PracticeAttempt latest = completed.isEmpty() ? null : completed.get(0);
        PracticeAttempt best = speaking ? null : completed.stream()
                .filter(attempt -> normalizedScore(attempt) != null)
                .max(Comparator.comparing(this::normalizedScore))
                .orElse(null);

        return new PracticeSkillAttemptCard(
                section.getId(),
                section.getTitle(),
                normalizeSkill(section.getSkill()),
                getSkillLabel(section.getSkill()),
                section.getDurationMinutes(),
                section.getTotalPoints(),
                inProgress == null ? null : inProgress.getId(),
                List.copyOf(attemptCards),
                state,
                stateLabel,
                speaking ? SPEAKING_SCORE_UNAVAILABLE
                        : latest == null ? "Chưa có" : formatScore(latest, false),
                speaking ? SPEAKING_SCORE_UNAVAILABLE
                        : best == null ? "Chưa có" : formatScore(best, false));
    }

    private boolean isActiveAttempt(PracticeAttempt attempt) {
        return attempt != null && !PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus());
    }

    private boolean isInProgressAttempt(PracticeAttempt attempt) {
        return PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus());
    }

    private boolean isCompletedAttempt(PracticeAttempt attempt) {
        return PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                || PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus());
    }

    private Comparator<PracticeAttempt> attemptComparator() {
        return Comparator
                .comparing(this::activityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PracticeAttempt::getId,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private LocalDateTime activityAt(PracticeAttempt attempt) {
        if (attempt.getSubmittedAt() != null) return attempt.getSubmittedAt();
        if (attempt.getUpdatedAt() != null) return attempt.getUpdatedAt();
        return attempt.getCreatedAt();
    }

    private String attemptStatusLabel(PracticeAttempt attempt) {
        if (PracticeAttempt.ANALYSIS_QUEUED.equals(attempt.getAnalysisStatus())
                || PracticeAttempt.ANALYSIS_PROCESSING.equals(attempt.getAnalysisStatus())) {
            return isSpeakingAttempt(attempt) ? "Đang xử lý phản hồi" : "Đang chấm AI";
        }
        if (PracticeAttempt.ANALYSIS_FAILED.equals(attempt.getAnalysisStatus())) {
            return isSpeakingAttempt(attempt)
                    ? "Chưa thể xử lý phản hồi"
                    : "Chấm AI chưa hoàn tất";
        }
        if (PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus())) {
            return isSpeakingAttempt(attempt) ? "Đã xử lý phản hồi" : "Đã chấm";
        }
        return "Đã nộp";
    }

    private String formatScore(PracticeAttempt attempt, boolean speakingSection) {
        if (speakingSection || isSpeakingAttempt(attempt)) {
            return SPEAKING_SCORE_UNAVAILABLE;
        }
        if (attempt.getScorePercentage() != null
                && "PERCENTAGE".equals(attempt.getScoreUnit())) {
            return decimal(attempt.getScorePercentage()) + "%";
        }
        BigDecimal earned = attempt.getEarnedPoints() != null
                ? attempt.getEarnedPoints()
                : attempt.getScore();
        BigDecimal total = attempt.getTotalPoints();
        if (earned != null && total != null && total.signum() > 0) {
            return decimal(earned) + "/" + decimal(total);
        }
        if (earned != null) return decimal(earned);
        return "Đang chấm";
    }

    private BigDecimal normalizedScore(PracticeAttempt attempt) {
        if (isSpeakingAttempt(attempt)) return null;
        if (attempt.getScorePercentage() != null) {
            return attempt.getScorePercentage();
        }
        BigDecimal earned = attempt.getEarnedPoints() != null
                ? attempt.getEarnedPoints()
                : attempt.getScore();
        if (earned == null) return null;
        if (attempt.getTotalPoints() == null || attempt.getTotalPoints().signum() <= 0) {
            return earned;
        }
        return earned.multiply(BigDecimal.valueOf(100))
                .divide(attempt.getTotalPoints(), 4, RoundingMode.HALF_UP);
    }

    private boolean isSpeakingAttempt(PracticeAttempt attempt) {
        return attempt != null && "SPEAKING".equals(normalizeSkill(attempt.getSkill()));
    }

    private String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String normalizeSkill(String skill) {
        if (skill == null || skill.isBlank()) return "UNKNOWN";
        return skill.trim().toUpperCase();
    }
}
