package com.ksh.features.tests.service;

import com.ksh.common.HtmlSanitizer;
import com.ksh.features.tests.dto.TestDtos.ResultView;
import com.ksh.features.tests.dto.TestDtos.ReviewOptionView;
import com.ksh.features.tests.dto.TestDtos.ReviewQuestionView;
import com.ksh.features.tests.dto.TestDtos.ReviewView;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.QuestionOption;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.entity.TestResponse;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestResponseRepository;
import com.ksh.features.tests.support.OptionIdsCodec;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the post-submit result summary and the per-question review from a
 * graded attempt. Access is the caller's responsibility — this builder only
 * shapes data (used by both the student review and the lecturer submissions
 * review).
 */
@Service
public class AttemptResultBuilder {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final TestResponseRepository responseRepository;

    public AttemptResultBuilder(QuestionRepository questionRepository,
                                QuestionOptionRepository optionRepository,
                                TestResponseRepository responseRepository) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.responseRepository = responseRepository;
    }

    /** The numeric result summary + pass/fail decision. */
    public ResultView buildResult(Test test, TestAttempt attempt) {
        BigDecimal score = nz(attempt.getScore());
        BigDecimal passing = test.getPassingScore();
        boolean hasThreshold = passing != null;
        boolean passed = hasThreshold && score.compareTo(passing) >= 0;
        BigDecimal totalPoints = nz(attempt.getTotalPoints());
        int correctCount = nzInt(attempt.getCorrectCount());
        int totalQuestions = nzInt(attempt.getTotalQuestions());
        int timeSpentSeconds = nzInt(attempt.getTimeSpentSeconds());
        return new ResultView(test.getId(), test.getClassId(), attempt.getId(), test.getTitle(),
                score, totalPoints, hasThreshold, passed, passing,
                correctCount, totalQuestions,
                percentage(score, totalPoints), percentage(correctCount, totalQuestions),
                timeSpentSeconds, formatDuration(timeSpentSeconds), attempt.getStatus());
    }

    /** The per-question review: student answer, correct answer, correctness, explanation. */
    public ReviewView buildReview(Test test, TestAttempt attempt,
                                  boolean lecturerView, String studentName) {
        List<Question> questions = questionRepository
                .findByTestIdOrderBySortOrderAscIdAsc(test.getId());
        Map<Long, List<QuestionOption>> optionsByQuestion = loadOptions(questions);
        Map<Long, TestResponse> responsesByQuestion = responseRepository
                .findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(TestResponse::getQuestionId, r -> r, (a, b) -> b));

        List<ReviewQuestionView> views = new ArrayList<>();
        for (Question q : questions) {
            TestResponse resp = responsesByQuestion.get(q.getId());
            Set<Long> selected = resp == null ? Set.of()
                    : OptionIdsCodec.parse(resp.getSelectedOptionIds());
            boolean answered = !selected.isEmpty();
            boolean correct = resp != null && Boolean.TRUE.equals(resp.getCorrect());
            List<ReviewOptionView> optViews = new ArrayList<>();
            for (QuestionOption o : optionsByQuestion.getOrDefault(q.getId(), List.of())) {
                optViews.add(new ReviewOptionView(o.getId(), sanitizeRichText(o.getContent()),
                        o.isCorrect(), selected.contains(o.getId())));
            }
            views.add(new ReviewQuestionView(q.getId(), q.getQuestionType(),
                    sanitizeRichText(q.getContent()),
                    sanitizeOptional(q.getExplanation()), correct, answered, optViews));
        }
        int correctCount = nzInt(attempt.getCorrectCount());
        int totalQuestions = nzInt(attempt.getTotalQuestions());
        int unansweredCount = (int) views.stream().filter(q -> !q.answered()).count();
        int incorrectCount = Math.max(0, totalQuestions - correctCount - unansweredCount);
        BigDecimal score = nz(attempt.getScore());
        BigDecimal totalPoints = nz(attempt.getTotalPoints());
        int timeSpentSeconds = nzInt(attempt.getTimeSpentSeconds());
        return new ReviewView(test.getId(), test.getClassId(), attempt.getId(), test.getTitle(),
                correctCount, totalQuestions, score, totalPoints,
                percentage(score, totalPoints), percentage(correctCount, totalQuestions),
                incorrectCount, unansweredCount,
                timeSpentSeconds, formatDuration(timeSpentSeconds), attempt.getStatus(),
                lecturerView, studentName, views);
    }

    private Map<Long, List<QuestionOption>> loadOptions(List<Question> questions) {
        if (questions.isEmpty()) return Map.of();
        List<Long> ids = questions.stream().map(Question::getId).toList();
        return optionRepository.findByQuestionIdInOrderBySortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static String sanitizeOptional(String value) {
        if (value == null) return null;
        String sanitized = sanitizeRichText(value).trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * Repairs one legacy layer of entity-encoded rich text, then applies the
     * normal safelist. Decoding is gated on an encoded allowed tag so ordinary
     * prose containing entities is left untouched.
     */
    private static String sanitizeRichText(String value) {
        if (value == null || value.isBlank()) return "";
        String source = containsEncodedRichTextTag(value)
                ? Parser.unescapeEntities(value, false)
                : value;
        return HtmlSanitizer.sanitize(source);
    }

    private static boolean containsEncodedRichTextTag(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String tag : List.of("p", "br", "strong", "b", "em", "i", "u", "s",
                "blockquote", "pre", "code", "ol", "ul", "li", "a", "img",
                "h1", "h2", "h3", "h4", "h5", "h6")) {
            if (lower.contains("&lt;" + tag) || lower.contains("&lt;/" + tag)) {
                return true;
            }
        }
        return false;
    }

    private static int percentage(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.signum() <= 0) return 0;
        int percent = value.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(0, Math.min(100, percent));
    }

    private static int percentage(int value, int total) {
        if (total <= 0) return 0;
        return Math.max(0, Math.min(100,
                BigDecimal.valueOf(value * 100L)
                        .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                        .intValue()));
    }

    private static String formatDuration(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;
        if (hours > 0) {
            return remainder > 0
                    ? hours + " giờ " + minutes + " phút " + remainder + " giây"
                    : hours + " giờ " + minutes + " phút";
        }
        if (minutes > 0) {
            return remainder > 0
                    ? minutes + " phút " + remainder + " giây"
                    : minutes + " phút";
        }
        return remainder + " giây";
    }
}
