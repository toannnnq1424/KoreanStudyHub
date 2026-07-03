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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                1,
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
    void writingTaskTypeDefaultsToNullWhenUnset() {
        PracticeSet set = saveSet("Writing metadata null");
        PracticeQuestion question = new PracticeQuestion(
                set.getId(),
                1,
                PracticeQuestion.TYPE_ESSAY,
                "Prompt",
                null,
                "",
                "",
                BigDecimal.TEN,
                0
        );

        PracticeQuestion saved = questionRepository.saveAndFlush(question);
        PracticeQuestion reloaded = questionRepository.findById(saved.getId()).orElseThrow();

        assertNull(reloaded.getWritingTaskType());
    }

    private PracticeSet saveSet(String title) {
        PracticeSet set = new PracticeSet(
                title,
                "Description",
                PracticeSet.SKILL_WRITING,
                "TOPIK_II",
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
