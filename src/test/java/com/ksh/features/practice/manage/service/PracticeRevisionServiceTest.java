package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeEditLog;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.repository.PracticeEditLogRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeRevisionServiceTest {

    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeSectionRepository sectionRepository = mock(PracticeSectionRepository.class);
    private final PracticeQuestionGroupRepository groupRepository = mock(PracticeQuestionGroupRepository.class);
    private final PracticeQuestionRepository questionRepository = mock(PracticeQuestionRepository.class);
    private final PracticeEditLogRepository editLogRepository = mock(PracticeEditLogRepository.class);
    private final PracticePublishedGraphMutationGuard mutationGuard = mock(PracticePublishedGraphMutationGuard.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong idSequence = new AtomicLong(100L);
    private final List<PracticeQuestion> savedQuestions = new ArrayList<>();

    private PracticeRevisionService service;

    @BeforeEach
    void setUp() {
        service = new PracticeRevisionService(
                setRepository,
                sectionRepository,
                groupRepository,
                questionRepository,
                editLogRepository,
                mutationGuard,
                objectMapper
        );
        when(setRepository.save(any(PracticeSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sectionRepository.save(any(PracticeSection.class))).thenAnswer(invocation -> {
            PracticeSection section = invocation.getArgument(0);
            assignIdIfMissing(section);
            return section;
        });
        when(groupRepository.save(any(PracticeQuestionGroup.class))).thenAnswer(invocation -> {
            PracticeQuestionGroup group = invocation.getArgument(0);
            assignIdIfMissing(group);
            return group;
        });
        when(questionRepository.save(any(PracticeQuestion.class))).thenAnswer(invocation -> {
            PracticeQuestion question = invocation.getArgument(0);
            savedQuestions.add(question);
            return question;
        });
        when(editLogRepository.save(any(PracticeEditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void restoreRevisionPreservesExplicitWritingTaskMetadata() throws Exception {
        arrangeRestoreQuestion(snapshotQuestion("\"Q53\""));

        service.restoreRevision(7L, 99L);

        assertEquals(1, savedQuestions.size());
        assertEquals(WritingTaskType.Q53, savedQuestions.get(0).getWritingTaskType());
    }

    @Test
    void restoreRevisionPreservesGeneralWritingTaskMetadata() throws Exception {
        arrangeRestoreQuestion(snapshotQuestion("\"GENERAL\""));

        service.restoreRevision(7L, 99L);

        assertEquals(WritingTaskType.GENERAL, savedQuestions.get(0).getWritingTaskType());
    }

    @Test
    void restoreRevisionMissingWritingTaskMetadataRestoresNull() throws Exception {
        arrangeRestoreQuestion(snapshotQuestionWithoutTask());

        service.restoreRevision(7L, 99L);

        assertNull(savedQuestions.get(0).getWritingTaskType());
    }

    @Test
    void restoreRevisionIgnoresStaleNonWritingTaskMetadata() throws Exception {
        arrangeRestoreSnapshot(snapshot("READING", snapshotQuestion("\"NOT_A_TASK\"")));

        service.restoreRevision(7L, 99L);

        assertNull(savedQuestions.get(0).getWritingTaskType());
    }

    @Test
    void restoreRevisionWithLearnerHistoryBlocksBeforeGraphMutation() throws Exception {
        arrangeRestoreQuestion(snapshotQuestion("\"Q53\""));
        when(mutationGuard.lockAndAssertRestoreAllowed(10L))
                .thenThrow(PublishedPracticeGraphMutationBlockedException.forRestore());

        PublishedPracticeGraphMutationBlockedException exception = assertThrows(
                PublishedPracticeGraphMutationBlockedException.class,
                () -> service.restoreRevision(7L, 99L));

        assertEquals(PublishedPracticeGraphMutationBlockedException.RESTORE_MESSAGE, exception.getMessage());
        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(questionRepository, never()).save(any());
        verify(editLogRepository, never()).save(any());
    }

    @Test
    void restoreRevisionAcquiresGuardBeforeSnapshotValidation() throws Exception {
        arrangeRestoreQuestion(snapshotQuestion("\"Q51_52\""));

        assertThrows(IllegalArgumentException.class, () -> service.restoreRevision(7L, 99L));

        verify(mutationGuard).lockAndAssertRestoreAllowed(10L);
        verify(questionRepository, never()).deleteBySetId(any());
    }

    @Test
    void restoreRevisionInvalidWritingTaskMetadataFailsBeforeDelete() throws Exception {
        arrangeRestoreQuestion(snapshotQuestion("\"Q51_52\""));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.restoreRevision(7L, 99L));

        assertEquals("Loại bài Writing không hợp lệ.", exception.getMessage());
        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(questionRepository, never()).save(any());
        verify(editLogRepository, never()).save(any());
    }

    @Test
    void restoreRevisionLateBlankWritingTaskMetadataFailsBeforeDelete() throws Exception {
        arrangeRestoreSnapshot(snapshot("WRITING",
                snapshotQuestion("\"Q51\"") + "," + snapshotQuestion("\"\"")));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.restoreRevision(7L, 99L));

        assertTrue(exception.getMessage().contains("Writing"));
        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void restoreRevisionSpeakingEssayFailsBeforeDelete() throws Exception {
        arrangeRestoreSnapshot(snapshot("SPEAKING", snapshotQuestionWithoutTask()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.restoreRevision(7L, 99L));

        assertTrue(exception.getMessage().contains("question type SPEAKING"));
        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(questionRepository, never()).save(any());
    }

    private void arrangeRestoreQuestion(String questionJson) throws Exception {
        arrangeRestoreSnapshot(snapshot("WRITING", questionJson));
    }

    private void arrangeRestoreSnapshot(String snapshotJson) throws Exception {
        PracticeEditLog log = new PracticeEditLog(10L, 99L, "Change", "{}", snapshotJson, null, "QUESTIONS");
        setEntityId(log, 7L);
        PracticeSet set = new PracticeSet("Set", "Description", "WRITING", "TOPIK_II",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        setEntityId(set, 10L);
        when(editLogRepository.findById(7L)).thenReturn(Optional.of(log));
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(mutationGuard.lockAndAssertRestoreAllowed(10L)).thenReturn(set);
    }

    private String snapshotQuestion(String rawTaskValue) {
        return """
                {
                  "questionNo": 1,
                  "questionType": "ESSAY",
                  "prompt": "Prompt",
                  "answerKey": "",
                  "explanationVi": "Explanation",
                  "points": 10,
                  "essayTaskType": %s
                }
                """.formatted(rawTaskValue);
    }

    private String snapshotQuestionWithoutTask() {
        return """
                {
                  "questionNo": 1,
                  "questionType": "ESSAY",
                  "prompt": "Prompt",
                  "answerKey": "",
                  "explanationVi": "Explanation",
                  "points": 10
                }
                """;
    }

    private String snapshot(String skill, String questionJson) {
        return """
                {
                  "document": {"title": "Set", "description": "Description", "detectedCategory": "TOPIK_II"},
                  "sections": [
                    {
                      "title": "Section",
                      "skill": "%s",
                      "durationMinutes": 50,
                      "totalPoints": 10,
                      "groups": [
                        {
                          "label": "Group",
                          "instruction": "",
                          "questions": [%s]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(skill, questionJson);
    }

    private void assignIdIfMissing(Object entity) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, idSequence.incrementAndGet());
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setEntityId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
