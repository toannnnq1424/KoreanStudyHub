package com.ksh.features.tests.support;

import com.ksh.features.lessons.support.YouTubeEmbedUrl;
import com.ksh.features.tests.dto.LecturerTestDtos.ExamForm;
import com.ksh.features.tests.dto.LecturerTestDtos.OptionForm;
import com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.ksh.common.IConstant.MSG_EXAM_CONTENT_TOO_LARGE;
import static com.ksh.common.IConstant.MSG_EXAM_DURATION_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_DURATION_REQUIRED;
import static com.ksh.common.IConstant.MSG_EXAM_FIXED_WINDOW_REQUIRED;
import static com.ksh.common.IConstant.MSG_EXAM_MEDIA_TYPE_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_MEDIA_TYPE_REQUIRED;
import static com.ksh.common.IConstant.MSG_EXAM_MEDIA_URL_REQUIRED;
import static com.ksh.common.IConstant.MSG_EXAM_MEDIA_URL_SCHEME;
import static com.ksh.common.IConstant.MSG_EXAM_MEDIA_YOUTUBE_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_NEEDS_CLASS;
import static com.ksh.common.IConstant.MSG_EXAM_NEEDS_QUESTIONS;
import static com.ksh.common.IConstant.MSG_EXAM_PASSING_SCORE_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_STATUS_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_TIME_MODE_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_TIME_RANGE_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_TITLE_BLANK;
import static com.ksh.common.IConstant.MSG_EXAM_TITLE_TOO_LONG;
import static com.ksh.common.IConstant.MSG_EXAM_TOTAL_POINTS_INVALID;
import static com.ksh.common.IConstant.MSG_EXAM_TYPE_INVALID;
import static com.ksh.common.IConstant.MSG_MCQ_ONE_CORRECT;
import static com.ksh.common.IConstant.MSG_OPTION_CONTENT_BLANK;
import static com.ksh.common.IConstant.MSG_QUESTION_CONTENT_BLANK;
import static com.ksh.common.IConstant.MSG_QUESTION_NEEDS_CORRECT;
import static com.ksh.common.IConstant.MSG_QUESTION_NEEDS_OPTIONS;
import static com.ksh.common.IConstant.MSG_QUESTION_POINTS_INVALID;
import static com.ksh.common.IConstant.MSG_QUESTION_TYPE_INVALID;

/**
 * Validates a lecturer exam form before any persistence. Rules: title + class
 * required, and a publishable exam needs at least one question. An empty DRAFT
 * is allowed so the lecturer can persist the shell before generating questions
 * with AI. Every present question needs ≥2 options and ≥1 correct, and an MCQ
 * needs exactly one correct option. Optional media fields must be both empty or
 * both set with a type-consistent URL. Throws
 * {@link IllegalArgumentException} (→ 400 / field toast) on the first violation.
 */
public final class ExamFormValidator {

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            Test.MEDIA_TYPE_YOUTUBE, Test.MEDIA_TYPE_VIDEO, Test.MEDIA_TYPE_AUDIO);
    private static final Set<String> ALLOWED_TYPES = Set.of(Test.TYPE_MOCK, Test.TYPE_MODULE);
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            Test.STATUS_DRAFT, Test.STATUS_PUBLISHED, Test.STATUS_ARCHIVED);
    private static final Set<String> ALLOWED_TIME_MODES = Set.of(
            Test.TIME_MODE_FIXED_WINDOW, Test.TIME_MODE_INDIVIDUAL);
    private static final Set<String> ALLOWED_QUESTION_TYPES = Set.of(
            Question.TYPE_MCQ, Question.TYPE_MR);
    private static final BigDecimal MAX_DECIMAL_5_2 = new BigDecimal("999.99");
    private static final BigDecimal MAX_ATTEMPT_TOTAL = new BigDecimal("9999.99");

    private ExamFormValidator() {
        // utility holder
    }

    /** Validates the form; throws {@link IllegalArgumentException} on the first error. */
    public static void validate(ExamForm form) {
        if (isBlank(form.title())) {
            throw new IllegalArgumentException(MSG_EXAM_TITLE_BLANK);
        }
        if (form.title().trim().length() > 300) {
            throw new IllegalArgumentException(MSG_EXAM_TITLE_TOO_LONG);
        }
        if (form.classId() == null) {
            throw new IllegalArgumentException(MSG_EXAM_NEEDS_CLASS);
        }
        if (!ALLOWED_TYPES.contains(form.type())) {
            throw new IllegalArgumentException(MSG_EXAM_TYPE_INVALID);
        }
        if (!ALLOWED_STATUSES.contains(form.status())) {
            throw new IllegalArgumentException(MSG_EXAM_STATUS_INVALID);
        }
        if (!ALLOWED_TIME_MODES.contains(form.timeMode())) {
            throw new IllegalArgumentException(MSG_EXAM_TIME_MODE_INVALID);
        }
        validateTiming(form);
        validateMedia(form.mediaType(), form.mediaUrl());
        List<QuestionForm> questions = form.questions() == null ? List.of() : form.questions();
        if (questions.isEmpty() && !Test.STATUS_DRAFT.equals(form.status())) {
            throw new IllegalArgumentException(MSG_EXAM_NEEDS_QUESTIONS);
        }
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (QuestionForm q : questions) {
            validateQuestion(q);
            totalPoints = totalPoints.add(q.points());
            if (totalPoints.compareTo(MAX_ATTEMPT_TOTAL) > 0) {
                throw new IllegalArgumentException(MSG_EXAM_TOTAL_POINTS_INVALID);
            }
        }
        validatePassingScore(form.passingScore(), questions, totalPoints);
    }

    private static void validateTiming(ExamForm form) {
        Integer duration = form.durationMinutes();
        if (duration != null && (duration < 1 || duration > 600)) {
            throw new IllegalArgumentException(MSG_EXAM_DURATION_INVALID);
        }
        if (form.startAt() != null && form.endAt() != null
                && !form.endAt().isAfter(form.startAt())) {
            throw new IllegalArgumentException(MSG_EXAM_TIME_RANGE_INVALID);
        }
        if (!Test.STATUS_PUBLISHED.equals(form.status())) {
            return;
        }
        if (Test.TIME_MODE_FIXED_WINDOW.equals(form.timeMode())
                && (form.startAt() == null || form.endAt() == null)) {
            throw new IllegalArgumentException(MSG_EXAM_FIXED_WINDOW_REQUIRED);
        }
        if (Test.TIME_MODE_INDIVIDUAL.equals(form.timeMode()) && duration == null) {
            throw new IllegalArgumentException(MSG_EXAM_DURATION_REQUIRED);
        }
    }

    private static void validateMedia(String mediaType, String mediaUrl) {
        boolean typeBlank = isBlank(mediaType);
        boolean urlBlank = isBlank(mediaUrl);
        if (typeBlank && urlBlank) {
            return;
        }
        if (!typeBlank && urlBlank) {
            throw new IllegalArgumentException(MSG_EXAM_MEDIA_URL_REQUIRED);
        }
        if (typeBlank) {
            throw new IllegalArgumentException(MSG_EXAM_MEDIA_TYPE_REQUIRED);
        }
        String type = mediaType.trim();
        if (!ALLOWED_MEDIA_TYPES.contains(type)) {
            throw new IllegalArgumentException(MSG_EXAM_MEDIA_TYPE_INVALID);
        }
        String url = mediaUrl.trim();
        if (Test.MEDIA_TYPE_YOUTUBE.equals(type)) {
            if (!YouTubeEmbedUrl.matches(url)) {
                throw new IllegalArgumentException(MSG_EXAM_MEDIA_YOUTUBE_INVALID);
            }
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException(MSG_EXAM_MEDIA_URL_SCHEME);
        }
    }

    /** Soft cap so oversized paste (base64 data URIs) fail with a clear 400. */
    private static final int MAX_HTML_CHARS = 200_000;

    private static void validateQuestion(QuestionForm q) {
        if (q == null || !ALLOWED_QUESTION_TYPES.contains(q.type())) {
            throw new IllegalArgumentException(MSG_QUESTION_TYPE_INVALID);
        }
        if (!validQuestionPoints(q.points())) {
            throw new IllegalArgumentException(MSG_QUESTION_POINTS_INVALID);
        }
        if (isBlank(plainText(q.content()))) {
            throw new IllegalArgumentException(MSG_QUESTION_CONTENT_BLANK);
        }
        if (tooLarge(q.content())) {
            throw new IllegalArgumentException(MSG_EXAM_CONTENT_TOO_LARGE);
        }
        List<OptionForm> options = q.options();
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException(MSG_QUESTION_NEEDS_OPTIONS);
        }
        for (OptionForm o : options) {
            if (isBlank(plainText(o.content()))) {
                throw new IllegalArgumentException(MSG_OPTION_CONTENT_BLANK);
            }
            if (tooLarge(o.content())) {
                throw new IllegalArgumentException(MSG_EXAM_CONTENT_TOO_LARGE);
            }
        }
        long correct = options.stream().filter(OptionForm::correct).count();
        if (correct < 1) {
            throw new IllegalArgumentException(MSG_QUESTION_NEEDS_CORRECT);
        }
        // MCQ (single-response) must have exactly one correct option.
        if (Question.TYPE_MCQ.equals(q.type()) && correct != 1) {
            throw new IllegalArgumentException(MSG_MCQ_ONE_CORRECT);
        }
    }

    private static void validatePassingScore(BigDecimal passingScore,
                                             List<QuestionForm> questions,
                                             BigDecimal totalPoints) {
        if (passingScore == null) return;
        boolean invalid = passingScore.signum() < 0
                || passingScore.compareTo(MAX_DECIMAL_5_2) > 0
                || decimalPlaces(passingScore) > 2
                || (!questions.isEmpty() && passingScore.compareTo(totalPoints) > 0);
        if (invalid) {
            throw new IllegalArgumentException(MSG_EXAM_PASSING_SCORE_INVALID);
        }
    }

    private static boolean validQuestionPoints(BigDecimal points) {
        return points != null
                && points.signum() > 0
                && points.compareTo(MAX_DECIMAL_5_2) <= 0
                && decimalPlaces(points) <= 2;
    }

    private static int decimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean tooLarge(String html) {
        return html != null && html.length() > MAX_HTML_CHARS;
    }

    /**
     * Content is non-empty when it has visible text OR an embedded image
     * (image-only Quill payloads like {@code <p><img src="..."></p>}).
     */
    private static String plainText(String htmlOrText) {
        if (htmlOrText == null) return "";
        // Image-only answers/questions are valid rich content.
        if (htmlOrText.matches("(?is).*<img\\b[^>]*\\bsrc\\s*=.*")) {
            return "img";
        }
        return htmlOrText.replaceAll("(?is)<[^>]*>", " ").replace('\u00a0', ' ').trim();
    }
}
