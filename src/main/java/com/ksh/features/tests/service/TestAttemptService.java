package com.ksh.features.tests.service;

import com.ksh.features.tests.dto.TestDtos.ResultView;
import com.ksh.features.tests.dto.TestDtos.ReviewView;
import com.ksh.features.tests.dto.TestDtos.SubmitRequest;
import com.ksh.features.tests.dto.TestDtos.SubmitResult;
import com.ksh.features.tests.dto.TestDtos.TakeView;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.QuestionOption;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.entity.TestAttempt;
import com.ksh.features.tests.entity.TestResponse;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.repository.TestResponseRepository;
import com.ksh.features.tests.service.GradingService.GradeOutcome;
import com.ksh.features.tests.support.ExamDeadline;
import com.ksh.features.tests.support.OptionIdsCodec;
import com.ksh.features.tests.support.TestAccessResolver;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Attempt lifecycle: start/resume, taking view, heartbeat, and graded submit. */
@Service
public class TestAttemptService {

    private final TestRepository testRepository;
    private final TestAttemptRepository attemptRepository;
    private final TestResponseRepository responseRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final TestAccessResolver accessResolver;
    private final GradingService gradingService;
    private final TakeViewBuilder takeViewBuilder;
    private final AttemptResultBuilder resultBuilder;

    public TestAttemptService(TestRepository testRepository,
                              TestAttemptRepository attemptRepository,
                              TestResponseRepository responseRepository,
                              QuestionRepository questionRepository,
                              QuestionOptionRepository optionRepository,
                              TestAccessResolver accessResolver,
                              GradingService gradingService,
                              TakeViewBuilder takeViewBuilder,
                              AttemptResultBuilder resultBuilder) {
        this.testRepository = testRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.accessResolver = accessResolver;
        this.gradingService = gradingService;
        this.takeViewBuilder = takeViewBuilder;
        this.resultBuilder = resultBuilder;
    }

    /** Owner-only result summary for a submitted attempt. */
    @Transactional(readOnly = true)
    public ResultView result(Long testId, Long attemptId, Long userId) {
        TestAttempt attempt = requireAttemptOfTest(testId, attemptId, userId);
        Test test = loadTest(attempt.getTestId());
        return resultBuilder.buildResult(test, attempt);
    }

    /** Owner-only per-question review for a submitted attempt. */
    @Transactional(readOnly = true)
    public ReviewView review(Long testId, Long attemptId, Long userId) {
        TestAttempt attempt = requireAttemptOfTest(testId, attemptId, userId);
        Test test = loadTest(attempt.getTestId());
        return resultBuilder.buildReview(test, attempt, false, null);
    }

    private TestAttempt requireAttemptOfTest(Long testId, Long attemptId, Long userId) {
        TestAttempt attempt = accessResolver.requireOwnAttempt(attemptId, userId);
        // Guard against a mismatched {testId}/{attemptId} path pairing.
        if (!attempt.getTestId().equals(testId)) {
            throw new EntityNotFoundException(TestAccessResolver.ATTEMPT_NF_MSG);
        }
        return attempt;
    }

    private Test loadTest(Long testId) {
        return testRepository.findById(testId)
                .orElseThrow(() -> new EntityNotFoundException(TestAccessResolver.NF_MSG));
    }

    /**
     * Starts a class exam after an explicit student action, or resumes its open
     * attempt. Non-practice exams allow exactly one attempt per student.
     */
    @Transactional
    public TakeView startOrResume(Long testId, Long userId) {
        Test test = accessResolver.requireAttemptableForUpdate(testId, userId);
        List<TestAttempt> attempts =
                attemptRepository.findByTestIdAndUserIdOrderByStartedAtDesc(testId, userId);
        if (!test.isPractice() && attempts.stream().anyMatch(a -> !a.isInProgress())) {
            throw completedAttemptException();
        }
        TestAttempt open = attempts.stream().filter(TestAttempt::isInProgress)
                .findFirst().orElse(null);
        if (open != null) {
            return takeViewBuilder.build(test, open);
        }
        ensureStartable(test, LocalDateTime.now());
        TestAttempt attempt = attemptRepository.save(new TestAttempt(testId, userId));
        return takeViewBuilder.build(test, attempt);
    }

    /**
     * Renders an existing attempt. Direct GET requests never start a class
     * exam; the legacy practice flow keeps its repeatable start behaviour.
     */
    @Transactional
    public TakeView resumeForTake(Long testId, Long userId) {
        Test test = accessResolver.requireAttemptableForUpdate(testId, userId);
        List<TestAttempt> attempts =
                attemptRepository.findByTestIdAndUserIdOrderByStartedAtDesc(testId, userId);
        if (!test.isPractice() && attempts.stream().anyMatch(a -> !a.isInProgress())) {
            throw completedAttemptException();
        }
        TestAttempt open = attempts.stream().filter(TestAttempt::isInProgress)
                .findFirst().orElse(null);
        if (open != null) {
            return takeViewBuilder.build(test, open);
        }
        if (test.isPractice()) {
            TestAttempt practiceAttempt = attemptRepository.save(new TestAttempt(testId, userId));
            return takeViewBuilder.build(test, practiceAttempt);
        }
        throw new TestAttemptUnavailableException(
                "Hãy xem thông tin bài test và bấm Bắt đầu làm bài.");
    }

    private TestAttemptUnavailableException completedAttemptException() {
        return new TestAttemptUnavailableException(
                "Bạn đã hoàn thành bài test này. Mỗi học sinh chỉ được làm một lần.");
    }

    /** Updates {@code last_activity_at} for a live attempt; owner-only. No-op when closed. */
    @Transactional
    public void heartbeat(Long attemptId, Long userId) {
        TestAttempt attempt = accessResolver.requireOwnAttemptForUpdate(attemptId, userId);
        if (attempt.isInProgress()) {
            attempt.touchActivity();
            attemptRepository.save(attempt);
        }
    }

    /**
     * Grades and closes an attempt. The server recomputes the deadline: past it →
     * {@code TIMED_OUT}, otherwise {@code SUBMITTED}; either way only the submitted
     * responses are graded. Already-closed attempts are returned unchanged.
     */
    @Transactional
    public SubmitResult submit(Long attemptId, Long userId, SubmitRequest request) {
        TestAttempt attempt = accessResolver.requireOwnAttemptForUpdate(attemptId, userId);
        if (!attempt.isInProgress()) {
            return new SubmitResult(attempt.getTestId(), attempt.getId(), attempt.getStatus());
        }
        Test test = testRepository.findById(attempt.getTestId())
                .orElseThrow(() -> new EntityNotFoundException(TestAccessResolver.NF_MSG));

        List<Question> questions = questionRepository
                .findByTestIdOrderBySortOrderAscIdAsc(test.getId());
        Map<Long, List<QuestionOption>> optionsByQuestion = loadOptions(questions);
        Map<Long, List<Long>> answers = indexAnswers(request);

        BigDecimal earnedSum = BigDecimal.ZERO;
        BigDecimal totalPointsSum = BigDecimal.ZERO;
        int correctCount = 0;
        for (Question q : questions) {
            totalPointsSum = totalPointsSum.add(nonNull(q.getPoints()));
            Set<Long> selected = OptionIdsCodec.fromList(answers.get(q.getId()));
            GradeOutcome outcome = gradingService.gradeQuestion(
                    q, optionsByQuestion.getOrDefault(q.getId(), List.of()), selected);
            if (outcome.correct()) correctCount++;
            earnedSum = earnedSum.add(outcome.pointsEarned());
            persistResponse(attempt.getId(), q.getId(), selected, outcome);
        }

        String finalStatus = ExamDeadline.isPastDeadline(test, attempt, LocalDateTime.now())
                ? TestAttempt.STATUS_TIMED_OUT : TestAttempt.STATUS_SUBMITTED;
        int timeSpent = ExamDeadline.elapsedSeconds(test, attempt, LocalDateTime.now());
        attempt.finalizeGrade(earnedSum, totalPointsSum, correctCount, questions.size(),
                timeSpent, finalStatus);
        attemptRepository.save(attempt);
        return new SubmitResult(test.getId(), attempt.getId(), finalStatus);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void persistResponse(Long attemptId, Long questionId, Set<Long> selected,
                                 GradeOutcome outcome) {
        Set<Long> canonical = OptionIdsCodec.sorted(selected);
        TestResponse response = new TestResponse(attemptId, questionId,
                OptionIdsCodec.toJson(canonical));
        response.grade(outcome.correct(), outcome.pointsEarned());
        responseRepository.save(response);
    }

    private Map<Long, List<Long>> indexAnswers(SubmitRequest request) {
        if (request == null || request.answers() == null) return Map.of();
        return request.answers().stream()
                .filter(a -> a.questionId() != null)
                .collect(Collectors.toMap(
                        a -> a.questionId(),
                        a -> a.selectedOptionIds() == null ? List.of() : a.selectedOptionIds(),
                        (a, b) -> b));
    }

    private Map<Long, List<QuestionOption>> loadOptions(List<Question> questions) {
        if (questions.isEmpty()) return Map.of();
        List<Long> ids = questions.stream().map(Question::getId).toList();
        return optionRepository.findByQuestionIdInOrderBySortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void ensureStartable(Test test, LocalDateTime now) {
        if (test.isPractice()) return;
        if (test.getStartAt() != null && now.isBefore(test.getStartAt())) {
            throw new TestAttemptUnavailableException("Bài test chưa đến giờ mở.");
        }
        if (test.getEndAt() != null && !now.isBefore(test.getEndAt())) {
            throw new TestAttemptUnavailableException("Bài test đã kết thúc.");
        }
        if (Test.TIME_MODE_FIXED_WINDOW.equals(test.getTimeMode())
                && test.getEndAt() == null) {
            throw new TestAttemptUnavailableException(
                    "Bài test chưa được cấu hình thời gian kết thúc.");
        }
        if (test.isIndividualTimer()
                && (test.getDurationMinutes() == null || test.getDurationMinutes() <= 0)) {
            throw new TestAttemptUnavailableException(
                    "Bài test chưa được cấu hình thời lượng làm bài.");
        }
    }
}
