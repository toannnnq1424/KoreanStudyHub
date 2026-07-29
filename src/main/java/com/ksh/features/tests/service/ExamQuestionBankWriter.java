package com.ksh.features.tests.service;

import com.ksh.common.HtmlSanitizer;
import com.ksh.features.tests.dto.LecturerTestDtos.OptionForm;
import com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.QuestionOption;
import com.ksh.features.tests.repository.QuestionOptionRepository;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestAttemptRepository;
import com.ksh.features.tests.repository.TestResponseRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ksh.common.IConstant.MSG_EXAM_QUESTION_BANK_LOCKED;

/**
 * Persists exam question banks for lecturer authoring. Owns full replacement
 * when the bank is unlocked, and in-place content updates when student
 * responses already exist (shape locked to keep FK rows valid).
 */
@Service
public class ExamQuestionBankWriter {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final TestResponseRepository responseRepository;
    private final TestAttemptRepository attemptRepository;

    public ExamQuestionBankWriter(QuestionRepository questionRepository,
                                  QuestionOptionRepository optionRepository,
                                  TestResponseRepository responseRepository,
                                  TestAttemptRepository attemptRepository) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.responseRepository = responseRepository;
        this.attemptRepository = attemptRepository;
    }

    /** True when any current question already has a student response. */
    public boolean hasStudentResponses(Long testId) {
        List<Question> existing = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId);
        if (existing.isEmpty()) return false;
        return responseRepository.existsByQuestionIdIn(
                existing.stream().map(Question::getId).toList());
    }

    /**
     * True once a student has started the exam. Question-bank shape must stay stable from
     * that moment, even before the first answer is submitted.
     */
    public boolean hasStudentActivity(Long testId) {
        return attemptRepository.existsByTestId(testId) || hasStudentResponses(testId);
    }

    /**
     * Loads options for the given questions grouped by question id, ordered
     * by sort order. Shared by edit form hydration and locked-bank updates.
     */
    public Map<Long, List<QuestionOption>> loadOptions(List<Question> questions) {
        if (questions.isEmpty()) return Map.of();
        List<Long> ids = questions.stream().map(Question::getId).toList();
        Map<Long, List<QuestionOption>> map = new HashMap<>();
        for (QuestionOption o : optionRepository.findByQuestionIdInOrderBySortOrderAscIdAsc(ids)) {
            map.computeIfAbsent(o.getQuestionId(), k -> new ArrayList<>()).add(o);
        }
        return map;
    }

    /**
     * Appends the submitted questions as exam-owned snapshot rows after the
     * existing set, without touching current questions/options. Used by
     * insert-from-bank so approved shared questions are copied (not live-linked)
     * into the test. Returns the number of questions appended.
     */
    public int appendQuestions(Long testId, List<QuestionForm> questions) {
        List<Question> existing = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId);
        int order = existing.size() + 1;
        int appended = 0;
        for (QuestionForm qf : questions) {
            String contentHtml = HtmlSanitizer.sanitize(qf.content());
            Question q = new Question(testId, defaultQuestionType(qf.type()), contentHtml,
                    trimToNull(qf.explanation()), qf.points(), order++);
            Long qId = questionRepository.save(q).getId();
            int optOrder = 1;
            for (OptionForm of : qf.options()) {
                String optionHtml = HtmlSanitizer.sanitize(of.content());
                optionRepository.save(new QuestionOption(qId, optionHtml, of.correct(), optOrder++));
            }
            appended++;
        }
        return appended;
    }

    /** Deletes existing questions/options and inserts the submitted set in order. */
    public void replaceQuestions(Long testId, List<QuestionForm> questions) {
        List<Question> existing = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId);
        if (!existing.isEmpty()) {
            optionRepository.deleteByQuestionIdIn(existing.stream().map(Question::getId).toList());
            questionRepository.deleteByTestId(testId);
        }
        int order = 1;
        for (QuestionForm qf : questions) {
            String contentHtml = HtmlSanitizer.sanitize(qf.content());
            Question q = new Question(testId, defaultQuestionType(qf.type()), contentHtml,
                    trimToNull(qf.explanation()), qf.points(), order++);
            Long qId = questionRepository.save(q).getId();
            int optOrder = 1;
            for (OptionForm of : qf.options()) {
                String optionHtml = HtmlSanitizer.sanitize(of.content());
                optionRepository.save(new QuestionOption(qId, optionHtml, of.correct(), optOrder++));
            }
        }
    }

    /**
     * Updates content of an existing question bank without deleting rows.
     * Shape (counts + ids) must match exactly; otherwise reject so graded
     * responses keep pointing at stable option ids.
     */
    public void updateQuestionContentsInPlace(Long testId, List<QuestionForm> questions) {
        List<Question> existing = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId);
        if (existing.size() != questions.size()) {
            throw new IllegalArgumentException(MSG_EXAM_QUESTION_BANK_LOCKED);
        }
        Map<Long, List<QuestionOption>> optionsByQuestion = loadOptions(existing);
        validateLockedGradingContract(existing, optionsByQuestion, questions);
        for (int i = 0; i < existing.size(); i++) {
            Question q = existing.get(i);
            QuestionForm qf = questions.get(i);
            List<QuestionOption> opts = optionsByQuestion.getOrDefault(q.getId(), List.of());
            List<OptionForm> optForms = qf.options();
            // Only display text may change after an attempt starts. Type, points,
            // answer key, ids and shape are grading history and stay immutable.
            q.updateContent(q.getQuestionType(),
                    HtmlSanitizer.sanitize(qf.content()),
                    trimToNull(qf.explanation()),
                    q.getPoints(),
                    i + 1);
            questionRepository.save(q);
            for (int j = 0; j < opts.size(); j++) {
                QuestionOption o = opts.get(j);
                OptionForm of = optForms.get(j);
                o.updateContent(
                        HtmlSanitizer.sanitize(of.content()),
                        o.isCorrect(),
                        j + 1);
                optionRepository.save(o);
            }
        }
    }

    /**
     * Validates the complete locked bank before mutating any managed entity.
     * This prevents a late option mismatch from leaving partially changed state
     * when the writer is reused outside the normal transactional service.
     */
    private static void validateLockedGradingContract(
            List<Question> existing,
            Map<Long, List<QuestionOption>> optionsByQuestion,
            List<QuestionForm> questions) {
        for (int i = 0; i < existing.size(); i++) {
            Question question = existing.get(i);
            QuestionForm form = questions.get(i);
            if ((form.id() != null && !form.id().equals(question.getId()))
                    || !defaultQuestionType(form.type()).equals(question.getQuestionType())
                    || !sameNumber(form.points(), question.getPoints())) {
                throw new IllegalArgumentException(MSG_EXAM_QUESTION_BANK_LOCKED);
            }

            List<QuestionOption> options =
                    optionsByQuestion.getOrDefault(question.getId(), List.of());
            List<OptionForm> optionForms =
                    form.options() == null ? List.of() : form.options();
            if (options.size() != optionForms.size()) {
                throw new IllegalArgumentException(MSG_EXAM_QUESTION_BANK_LOCKED);
            }
            for (int j = 0; j < options.size(); j++) {
                QuestionOption option = options.get(j);
                OptionForm optionForm = optionForms.get(j);
                if ((optionForm.id() != null && !optionForm.id().equals(option.getId()))
                        || optionForm.correct() != option.isCorrect()) {
                    throw new IllegalArgumentException(MSG_EXAM_QUESTION_BANK_LOCKED);
                }
            }
        }
    }

    private static boolean sameNumber(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static String defaultQuestionType(String type) {
        return Question.TYPE_MR.equals(type) ? Question.TYPE_MR : Question.TYPE_MCQ;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
