package com.ksh.features.practice.service;

import com.ksh.features.practice.web.PracticeFormFields;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Small boundary helper for the player form contract.
 *
 * <p>PracticeService still owns grading and persistence, but this class keeps
 * form-field parsing out of the grading paths so route/form changes have one
 * focused place to test.</p>
 */
final class PracticeAnswerFormMapper {

    private PracticeAnswerFormMapper() {
    }

    static void mergeAllowedQuestionAnswers(
            Map<String, String> answers,
            Map<String, String> form,
            Collection<Long> allowedQuestionIds
    ) {
        Objects.requireNonNull(answers, "answers");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(allowedQuestionIds, "allowedQuestionIds");

        for (Long questionId : allowedQuestionIds) {
            String key = PracticeFormFields.answerKey(questionId);
            if (form.containsKey(key)) {
                answers.put(String.valueOf(questionId), clean(form.get(key)));
            }
        }
    }

    static void mergeAllAnswerFields(Map<String, String> answers, Map<String, String> form) {
        Objects.requireNonNull(answers, "answers");
        Objects.requireNonNull(form, "form");

        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (PracticeFormFields.isAnswerField(entry.getKey())) {
                answers.put(
                        PracticeFormFields.questionIdFromAnswerField(entry.getKey()),
                        clean(entry.getValue()));
            }
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
