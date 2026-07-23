package com.ksh.features.practice.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAttempt;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.ResultAttemptIdentity;
import com.ksh.features.practice.dto.PracticeDtos.ResultScoreSummary;
import com.ksh.features.practice.dto.PracticeDtos.ResultState;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.service.PracticePublishedVersionService;
import com.ksh.features.practice.service.PracticeVersionSnapshot;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PracticeResultAssembler {

    private final PracticeAttemptRepository attemptRepository;
    private final PracticePublishedVersionService publishedVersionService;
    private final ObjectMapper objectMapper;
    private final List<PracticeResultPresenter> presenters;

    public PracticeResultAssembler(
            PracticeAttemptRepository attemptRepository,
            PracticePublishedVersionService publishedVersionService,
            ObjectMapper objectMapper,
            List<PracticeResultPresenter> presenters) {
        this.attemptRepository = attemptRepository;
        this.publishedVersionService = publishedVersionService;
        this.objectMapper = objectMapper;
        this.presenters = List.copyOf(presenters);
    }

    @Transactional(readOnly = true)
    public PracticeAttemptResultView assemble(Long attemptId, Long userId) {
        return assemble(loadContext(attemptId, userId));
    }

    PracticeResultContext loadContext(Long attemptId, Long userId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Kết quả không tồn tại"));
        requireResultState(attempt);
        PracticeVersionSnapshot snapshot = publishedVersionService.snapshot(
                        attempt.getPublishedVersionId(),
                        attempt.getSetVersionId(),
                        attempt.getTestVersionId(),
                        attempt.getSectionVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Bài làm không có immutable snapshot hợp lệ để hiển thị kết quả."));
        if (!attempt.getSkill().equals(snapshot.sectionVersion().getSkill())) {
            throw new IllegalStateException("Kỹ năng của bài làm không khớp immutable snapshot.");
        }

        ResultScoreSummary score = scoreSummary(attempt);
        return new PracticeResultContext(
                attempt, snapshot, readAnswers(attempt.getAnswersJson()), score);
    }

    PracticeAttemptResultView assemble(PracticeResultContext context) {
        PracticeAttempt attempt = context.attempt();
        PracticeVersionSnapshot snapshot = context.snapshot();
        List<PracticeResultPresenter> matches = presenters.stream()
                .filter(presenter -> presenter.supports(attempt.getSkill()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Kỹ năng phải được xử lý bởi đúng một result presenter.");
        }
        PracticeResultPresenter.Presentation presentation = matches.get(0).present(context);

        return new PracticeAttemptResultView(
                identity(attempt, snapshot),
                resultState(attempt),
                presentation.score(),
                presentation.answers(),
                presentation.feedback(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                elapsedSeconds(attempt),
                presentation.payload());
    }

    private static ResultAttemptIdentity identity(PracticeAttempt attempt, PracticeVersionSnapshot snapshot) {
        return new ResultAttemptIdentity(
                attempt.getId(),
                snapshot.publishedVersion().getId(),
                snapshot.setVersion().getId(),
                snapshot.testVersion().getId(),
                snapshot.sectionVersion().getId(),
                snapshot.setVersion().getSetId(),
                snapshot.setVersion().getTitle(),
                snapshot.testVersion().getTestId(),
                snapshot.testVersion().getTitle(),
                snapshot.sectionVersion().getSectionId(),
                snapshot.sectionVersion().getTitle(),
                snapshot.sectionVersion().getSkill(),
                skillLabel(snapshot.sectionVersion().getSkill()));
    }

    private static ResultScoreSummary scoreSummary(PracticeAttempt attempt) {
        String unit = attempt.getScoreUnit();
        if (unit == null || unit.isBlank()) {
            unit = attempt.isSubjectiveSkill() ? "PERCENTAGE" : "EARNED_POINTS";
        }
        BigDecimal earned = attempt.getEarnedPoints();
        if (earned == null && "EARNED_POINTS".equals(unit)) {
            earned = attempt.getScore();
        }
        BigDecimal percentage = attempt.getScorePercentage();
        if (percentage == null && attempt.getScore() != null) {
            if ("PERCENTAGE".equals(unit)) {
                percentage = clampPercentage(attempt.getScore());
            } else if (attempt.getTotalPoints() != null && attempt.getTotalPoints().signum() > 0) {
                percentage = attempt.getScore().multiply(BigDecimal.valueOf(100))
                        .divide(attempt.getTotalPoints(), 2, RoundingMode.HALF_UP);
            }
        }
        return new ResultScoreSummary(
                attempt.getScore(),
                earned,
                attempt.getTotalPoints(),
                percentage,
                unit,
                "PERCENTAGE".equals(unit) ? "Thang 100" : "Điểm đạt được",
                null);
    }

    private Map<String, String> readAnswers(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> values = objectMapper.readValue(json, new TypeReference<>() { });
            Map<String, String> sanitized = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key != null) {
                    sanitized.put(key, value == null ? "" : value);
                }
            });
            return Map.copyOf(sanitized);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc đáp án đã khóa của bài làm.", exception);
        }
    }

    static ResultState resultState(PracticeAttempt attempt) {
        if (!PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus())) {
            return new ResultState("SUBMITTED", "Đã nộp");
        }
        return "SPEAKING".equals(attempt.getSkill())
                ? new ResultState("GRADED", "Đã xử lý phản hồi")
                : new ResultState("GRADED", "Đã chấm");
    }

    private static void requireResultState(PracticeAttempt attempt) {
        if (PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            throw new EntityNotFoundException("Kết quả không tồn tại");
        }
        if (!PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                && !PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus())) {
            throw new IllegalStateException("Kết quả chỉ khả dụng sau khi bài làm đã được nộp.");
        }
    }

    private static Long elapsedSeconds(PracticeAttempt attempt) {
        if (attempt.getStartedAt() == null || attempt.getSubmittedAt() == null
                || attempt.getSubmittedAt().isBefore(attempt.getStartedAt())) {
            return null;
        }
        return Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).getSeconds();
    }

    private static String skillLabel(String skill) {
        return switch (skill) {
            case "READING" -> "Đọc";
            case "LISTENING" -> "Nghe";
            case "WRITING" -> "Viết";
            case "SPEAKING" -> "Nói";
            default -> skill;
        };
    }

    private static BigDecimal clampPercentage(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }
}
