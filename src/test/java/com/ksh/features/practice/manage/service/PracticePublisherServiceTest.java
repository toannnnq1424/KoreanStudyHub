package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeEditLog;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeEditLogRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePublisherServiceTest {

    private final PracticeDraftRepository draftRepository = mock(PracticeDraftRepository.class);
    private final PracticeSetRepository setRepository = mock(PracticeSetRepository.class);
    private final PracticeTestRepository testRepository = mock(PracticeTestRepository.class);
    private final PracticeSectionRepository sectionRepository = mock(PracticeSectionRepository.class);
    private final PracticeQuestionGroupRepository groupRepository = mock(PracticeQuestionGroupRepository.class);
    private final PracticeQuestionRepository questionRepository = mock(PracticeQuestionRepository.class);
    private final PracticeEditLogRepository editLogRepository = mock(PracticeEditLogRepository.class);
    private final PracticePublishedGraphMutationGuard mutationGuard = mock(PracticePublishedGraphMutationGuard.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<PracticeSection> savedSections = new ArrayList<>();
    private final List<PracticeTest> savedTests = new ArrayList<>();
    private final List<PracticeQuestionGroup> savedGroups = new ArrayList<>();
    private final List<PracticeQuestion> savedQuestions = new ArrayList<>();
    private final List<PracticeEditLog> savedEditLogs = new ArrayList<>();
    private final AtomicReference<PracticeSet> savedSet = new AtomicReference<>();
    private final AtomicLong idSequence = new AtomicLong(1000L);

    private PracticePublisherService service;

    @BeforeEach
    void setUp() {
        service = new PracticePublisherService(
                draftRepository,
                setRepository,
                testRepository,
                sectionRepository,
                groupRepository,
                questionRepository,
                editLogRepository,
                mutationGuard,
                new PracticeDraftValidator(objectMapper),
                objectMapper
        );

        when(setRepository.save(any(PracticeSet.class))).thenAnswer(invocation -> {
            PracticeSet set = invocation.getArgument(0);
            assignIdIfMissing(set);
            savedSet.set(set);
            return set;
        });
        when(setRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedSet.get()));
        when(testRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(savedTests);
        when(testRepository.save(any(PracticeTest.class))).thenAnswer(invocation -> {
            PracticeTest test = invocation.getArgument(0);
            assignIdIfMissing(test);
            savedTests.add(test);
            return test;
        });
        when(mutationGuard.lockAndAssertRepublishAllowed(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedSet.get()).orElseThrow());
        when(sectionRepository.save(any(PracticeSection.class))).thenAnswer(invocation -> {
            PracticeSection section = invocation.getArgument(0);
            assignIdIfMissing(section);
            savedSections.add(section);
            return section;
        });
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(savedSections);
        when(groupRepository.save(any(PracticeQuestionGroup.class))).thenAnswer(invocation -> {
            PracticeQuestionGroup group = invocation.getArgument(0);
            assignIdIfMissing(group);
            savedGroups.add(group);
            return group;
        });
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(savedGroups);
        when(questionRepository.save(any(PracticeQuestion.class))).thenAnswer(invocation -> {
            PracticeQuestion question = invocation.getArgument(0);
            assignIdIfMissing(question);
            savedQuestions.add(question);
            return question;
        });
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(savedQuestions);
        when(editLogRepository.save(any(PracticeEditLog.class))).thenAnswer(invocation -> {
            PracticeEditLog log = invocation.getArgument(0);
            savedEditLogs.add(log);
            return log;
        });
        when(draftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void writingTaskTypeValuesRemainExact() {
        assertArrayEquals(
                new WritingTaskType[]{
                        WritingTaskType.Q51,
                        WritingTaskType.Q52,
                        WritingTaskType.Q53,
                        WritingTaskType.Q54,
                        WritingTaskType.GENERAL
                },
                WritingTaskType.values()
        );
    }

    @Test
    void migrationOnlyAddsNullableWritingTaskTypeColumn() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V20__practice_question_writing_task_type.sql"
        ));

        assertEquals("""
                ALTER TABLE practice_questions
                    ADD COLUMN writing_task_type VARCHAR(20) NULL;
                """, sql);
    }

    @ParameterizedTest
    @EnumSource(WritingTaskType.class)
    void publishPersistsWritingEssayTaskMetadata(WritingTaskType taskType) {
        publish(newDraft(draftJson("WRITING", "ESSAY", taskType.name())));

        assertEquals(1, savedQuestions.size());
        assertEquals(taskType, savedQuestions.get(0).getWritingTaskType());
        assertEquals(1, savedQuestions.get(0).getQuestionNo());
        assertEquals(0, savedQuestions.get(0).getDisplayOrder());
        assertEquals(1, savedGroups.get(0).getQuestionFrom());
        assertEquals(1, savedGroups.get(0).getQuestionTo());
        assertEquals(1, savedTests.size());
        assertEquals(savedTests.get(0).getId(), savedSections.get(0).getTestId());
    }

    @Test
    void publishAllowsMissingWritingEssayTaskMetadataAsNull() {
        publish(newDraft(draftJsonWithoutTask("WRITING", "ESSAY")));

        assertNull(savedQuestions.get(0).getWritingTaskType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void publishBlocksBlankWritingEssayTaskMetadataBeforeGraphMutation(String taskValue) {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", taskValue));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> publish(draft));

        assertEquals("Vui lòng chọn loại bài Writing cho câu tự luận.", exception.getMessage());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q51_52", "q53", "CUSTOM"})
    void invalidWritingEssayTaskMetadataFailsBeforeGraphMutation(String taskValue) {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", taskValue));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> publish(draft));

        assertEquals("Loại bài Writing không hợp lệ.", exception.getMessage());
        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void surroundingWhitespaceWritingEssayTaskMetadataIsInvalid() {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", " Q51 "));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> publish(draft));

        assertEquals("Loại bài Writing không hợp lệ.", exception.getMessage());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void nonTextualWritingEssayTaskMetadataFailsBeforeGraphMutation() {
        PracticeDraft draft = newDraft(draftJsonWithRawTask("WRITING", "ESSAY", "53"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> publish(draft));

        assertEquals("Loại bài Writing không hợp lệ.", exception.getMessage());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void lateInvalidWritingEssayTaskMetadataFailsBeforeAnyDraftMutation() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("WRITING", "ESSAY", "Q51"),
                questionJson("READING", "ESSAY", "Q54"),
                questionJson("WRITING", "ESSAY", "Q52"),
                questionJson("WRITING", "ESSAY", "Q51_52")
        ));

        assertThrows(IllegalArgumentException.class, () -> publish(draft));

        verify(setRepository, never()).save(any());
        verify(sectionRepository, never()).save(any());
        verify(groupRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
        verify(draftRepository, never()).save(any());
        verify(editLogRepository, never()).save(any());
    }

    @Test
    void lateBlankWritingEssayTaskMetadataFailsBeforeAnyDraftMutation() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("WRITING", "ESSAY", "Q51"),
                questionJson("WRITING", "ESSAY", "Q52"),
                questionJson("WRITING", "ESSAY", "")
        ));

        assertThrows(IllegalArgumentException.class, () -> publish(draft));

        verify(setRepository, never()).save(any());
        verify(sectionRepository, never()).save(any());
        verify(groupRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
        verify(draftRepository, never()).save(any());
        verify(editLogRepository, never()).save(any());
    }

    @Test
    void invalidRepublishMetadataDoesNotDeleteExistingGraph() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("WRITING", "ESSAY", "Q51"),
                questionJson("WRITING", "ESSAY", "NOT_A_TASK")
        ));
        draft.setPublishedSetId(77L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING", "TOPIK_II",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        assignId(existingSet, 77L);
        savedSet.set(existingSet);

        assertThrows(IllegalArgumentException.class, () -> publish(draft));

        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void blankRepublishMetadataDoesNotDeleteExistingGraph() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("WRITING", "ESSAY", "Q51"),
                questionJson("WRITING", "ESSAY", "")
        ));
        draft.setPublishedSetId(77L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING", "TOPIK_II",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        assignId(existingSet, 77L);
        savedSet.set(existingSet);

        assertThrows(IllegalArgumentException.class, () -> publish(draft));

        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void staleEssayTaskMetadataIsIgnoredForMcqAndNonWritingQuestions() {
        publish(newDraft(draftJsonWithQuestions(
                questionJson("WRITING", "SINGLE_CHOICE", "Q53"),
                questionJson("READING", "ESSAY", "Q54"),
                questionJson("LISTENING", "ESSAY", "GENERAL")
        )));

        assertEquals(3, savedQuestions.size());
        assertNull(savedQuestions.get(0).getWritingTaskType());
        assertNull(savedQuestions.get(1).getWritingTaskType());
        assertNull(savedQuestions.get(2).getWritingTaskType());
    }

    @Test
    void invalidStaleEssayTaskMetadataIsIgnoredForNonTargetQuestions() {
        publish(newDraft(draftJsonWithQuestions(
                questionJson("WRITING", "SINGLE_CHOICE", "Q51_52"),
                questionJsonWithRawTask("WRITING", "SINGLE_CHOICE", "53"),
                questionJson("READING", "ESSAY", "NOT_A_TASK"),
                questionJsonWithRawTask("LISTENING", "ESSAY", "{\"task\":\"Q54\"}"),
                questionJsonWithRawTask("LISTENING", "ESSAY", "[\"Q54\"]")
        )));

        assertEquals(5, savedQuestions.size());
        savedQuestions.forEach(question -> assertNull(question.getWritingTaskType()));
    }

    @Test
    void speakingEssayDraftIsBlockedBeforePublishingMutation() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("SPEAKING", "ESSAY", "GENERAL")
        ));

        assertThrows(IllegalStateException.class, () -> publish(draft));

        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void republishUsesCurrentDraftMetadataAndKeepsSectionLocalRenumbering() {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", "Q52"));
        draft.setPublishedSetId(77L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING", "TOPIK_II",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        assignId(existingSet, 77L);
        savedSet.set(existingSet);

        Long setId = publish(draft);

        assertEquals(77L, setId);
        verify(questionRepository).deleteBySetId(77L);
        assertEquals(WritingTaskType.Q52, savedQuestions.get(0).getWritingTaskType());
        assertEquals(1, savedQuestions.get(0).getQuestionNo());
    }

    @Test
    void republishWithLearnerHistoryBlocksBeforeGraphOrMetadataMutation() {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", "Q52"));
        draft.setPublishedSetId(77L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING", "TOPIK_II",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        assignId(existingSet, 77L);
        savedSet.set(existingSet);
        when(mutationGuard.lockAndAssertRepublishAllowed(77L))
                .thenThrow(PublishedPracticeGraphMutationBlockedException.forRepublish());

        PublishedPracticeGraphMutationBlockedException exception = assertThrows(
                PublishedPracticeGraphMutationBlockedException.class,
                () -> publish(draft));

        assertEquals(PublishedPracticeGraphMutationBlockedException.REPUBLISH_MESSAGE, exception.getMessage());
        assertEquals("Old", existingSet.getTitle());
        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(editLogRepository, never()).save(any());
    }

    @Test
    void firstPublishDoesNotInvokeHistoryGuardForNewSet() {
        publish(newDraft(draftJson("WRITING", "ESSAY", "Q52")));

        verify(mutationGuard, never()).lockAndAssertRepublishAllowed(any());
        assertEquals(1, savedEditLogs.size());
    }

    @Test
    void crossOwnerCannotPublishDraft() {
        when(draftRepository.findByIdAndOwnerId(1L, 100L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.publish(1L, 100L));

        verify(setRepository, never()).save(any());
        verify(testRepository, never()).save(any());
    }

    @Test
    void republishClearsMetadataWhenDraftRemovesTaskField() {
        PracticeDraft draft = newDraft(draftJsonWithoutTask("WRITING", "ESSAY"));
        draft.setPublishedSetId(88L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING", "TOPIK_II",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        assignId(existingSet, 88L);
        savedSet.set(existingSet);

        publish(draft);

        assertNull(savedQuestions.get(0).getWritingTaskType());
    }

    @Test
    void publishSnapshotPreservesExplicitWritingTaskMetadata() throws Exception {
        publish(newDraft(draftJson("WRITING", "ESSAY", "GENERAL")));

        assertEquals(1, savedEditLogs.size());
        JsonNode after = objectMapper.readTree(savedEditLogs.get(0).getAfterSnapshotJson());
        JsonNode question = after.path("sections").get(0).path("groups").get(0).path("questions").get(0);
        assertEquals("GENERAL", question.path("essayTaskType").asText());
    }

    @Test
    void publishSnapshotOmitsNullWritingTaskMetadata() throws Exception {
        publish(newDraft(draftJsonWithoutTask("WRITING", "ESSAY")));

        JsonNode after = objectMapper.readTree(savedEditLogs.get(0).getAfterSnapshotJson());
        JsonNode question = after.path("sections").get(0).path("groups").get(0).path("questions").get(0);
        assertFalse(question.has("essayTaskType"));
    }

    @Test
    void publishSnapshotOmitsStaleNonWritingTaskMetadata() throws Exception {
        publish(newDraft(draftJsonWithQuestions(
                questionJson("READING", "ESSAY", "Q54")
        )));

        JsonNode after = objectMapper.readTree(savedEditLogs.get(0).getAfterSnapshotJson());
        JsonNode question = after.path("sections").get(0).path("groups").get(0).path("questions").get(0);
        assertFalse(question.has("essayTaskType"));
    }

    private Long publish(PracticeDraft draft) {
        when(draftRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.of(draft));
        return service.publish(1L, 99L);
    }

    private PracticeDraft newDraft(String draftJson) {
        return new PracticeDraft(
                "Draft",
                "Description",
                "TOPIK_II",
                "GLOBAL",
                null,
                "DRAFT",
                99L,
                draftJson
        );
    }

    private String draftJson(String skill, String questionType, String taskValue) {
        return draftJsonWithQuestions(questionJson(skill, questionType, taskValue));
    }

    private String draftJsonWithoutTask(String skill, String questionType) {
        return draftJsonWithQuestions("""
                {
                  "skill": "%s",
                  "questionType": "%s",
                  "prompt": "Prompt",
                  "options": ["A", "B"],
                  "answer": {"value": "1"},
                  "explanationVi": "",
                  "points": 10
                }
                """.formatted(skill, questionType));
    }

    private String draftJsonWithRawTask(String skill, String questionType, String rawTaskValue) {
        return draftJsonWithQuestions(questionJsonWithRawTask(skill, questionType, rawTaskValue));
    }

    private String questionJsonWithRawTask(String skill, String questionType, String rawTaskValue) {
        return """
                {
                  "skill": "%s",
                  "questionType": "%s",
                  "prompt": "Prompt",
                  "options": ["A", "B"],
                  "answer": {"value": "1"},
                  "explanationVi": "",
                  "points": 10,
                  "essayTaskType": %s
                }
                """.formatted(skill, questionType, rawTaskValue);
    }

    private String questionJson(String skill, String questionType, String taskValue) {
        return """
                {
                  "skill": "%s",
                  "questionType": "%s",
                  "prompt": "Prompt",
                  "options": ["A", "B"],
                  "answer": {"value": "1"},
                  "explanationVi": "",
                  "points": 10,
                  "essayTaskType": "%s"
                }
                """.formatted(skill, questionType, taskValue);
    }

    private String draftJsonWithQuestions(String... questionJsons) {
        StringBuilder sections = new StringBuilder();
        for (int i = 0; i < questionJsons.length; i++) {
            if (i > 0) {
                sections.append(",");
            }
            String question = questionJsons[i];
            String skill = question.contains("\"skill\": \"READING\"")
                    ? "READING"
                    : question.contains("\"skill\": \"LISTENING\"")
                            ? "LISTENING"
                            : question.contains("\"skill\": \"SPEAKING\"") ? "SPEAKING" : "WRITING";
            sections.append("""
                    {
                      "title": "Section %d",
                      "skill": "%s",
                      "sectionType": "DEFAULT",
                      "durationMinutes": 40,
                      "totalPoints": 100,
                      "groups": [{
                        "label": "Group",
                        "instruction": "",
                        "questions": [%s]
                      }]
                    }
                    """.formatted(i + 1, skill, question));
        }
        return """
                {
                  "document": {"detectedCategory": "TOPIK_II"},
                  "sections": [%s]
                }
                """.formatted(sections);
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

    private void assignId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
