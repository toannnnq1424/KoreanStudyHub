package com.ksh.features.practice.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAnswerFormMapperTest {

    @Test
    void mergeAllowedQuestionAnswersOnlyKeepsCurrentSectionQuestions() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("1", "old");

        PracticeAnswerFormMapper.mergeAllowedQuestionAnswers(
                answers,
                Map.of(
                        "answer_1", "  새 답  ",
                        "answer_2", "ignored",
                        "not_answer_3", "ignored"),
                List.of(1L));

        assertThat(answers).containsEntry("1", "새 답");
        assertThat(answers).doesNotContainKeys("2", "3");
    }

    @Test
    void mergeAllAnswerFieldsKeepsDraftSaveContract() {
        Map<String, String> answers = new LinkedHashMap<>();

        PracticeAnswerFormMapper.mergeAllAnswerFields(
                answers,
                Map.of(
                        "answer_10", "  A  ",
                        "answer_11", "",
                        "mode", "exam"));

        assertThat(answers)
                .containsEntry("10", "A")
                .containsEntry("11", "")
                .doesNotContainKey("mode");
    }
}
