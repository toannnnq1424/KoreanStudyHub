package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.WritingTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class PracticeQuestionRepositoryTest {

    @Autowired
    private PracticeSetRepository setRepository;

    @Autowired
    private PracticeQuestionRepository questionRepository;

    @ParameterizedTest
    @EnumSource(WritingTaskType.class)
    void writingTaskTypeRoundTripsAsStringEnum(WritingTaskType taskType) {
        PracticeSet set = saveSet("Writing metadata " + taskType.name());
        PracticeQuestion question = new PracticeQuestion(
                set.getId(),
                Integer.parseInt(taskType.name().substring(1)),
                PracticeQuestion.TYPE_ESSAY,
                "Prompt",
                null,
                "",
                "",
                BigDecimal.TEN,
                0
        );
        question.setWritingTaskType(taskType);

        PracticeQuestion saved = questionRepository.saveAndFlush(question);
        questionRepository.flush();

        PracticeQuestion reloaded = questionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(taskType, reloaded.getWritingTaskType());
    }

    @Test
    void essayWithoutOneOfTheFourWritingTasksIsRejectedByTheDatabase() {
        PracticeSet set = saveSet("Writing metadata null");
        PracticeQuestion question = new PracticeQuestion(
                set.getId(),
                51,
                PracticeQuestion.TYPE_ESSAY,
                "Prompt",
                null,
                "",
                "",
                BigDecimal.TEN,
                0
        );

        assertThrows(JpaSystemException.class,
                () -> questionRepository.saveAndFlush(question));
    }

    @Test
    void writingTaskMustMatchThePersistedQuestionNumber() {
        PracticeSet set = saveSet("Writing metadata mismatch");
        PracticeQuestion question = new PracticeQuestion(
                set.getId(),
                51,
                PracticeQuestion.TYPE_ESSAY,
                "Prompt",
                null,
                "",
                "",
                BigDecimal.TEN,
                0
        );
        question.setWritingTaskType(WritingTaskType.Q52);

        assertThrows(JpaSystemException.class,
                () -> questionRepository.saveAndFlush(question));
    }

    private PracticeSet saveSet(String title) {
        PracticeSet set = new PracticeSet(
                title,
                "Description",
                PracticeSet.SKILL_WRITING,
                PracticeSet.SCOPE_GLOBAL,
                null,
                null,
                "{}",
                PracticeSet.STATUS_PUBLISHED,
                1L
        );
        return setRepository.saveAndFlush(set);
    }
}
