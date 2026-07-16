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
import com.ksh.features.practice.assessment.PracticeContentRules;
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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void authoringRulesExposeOnlyFourWritingTasks() {
        assertEquals(Set.of("Q51", "Q52", "Q53", "Q54"),
                new PracticeContentRules().requiredWritingTasks().stream()
                        .map(Enum::name).collect(java.util.stream.Collectors.toSet()));
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

    @Test
    void publishPersistsExactlyQ51ToQ54ForWriting() {
        publish(newDraft(completeWritingDraftJson()));

        assertEquals(4, savedQuestions.size());
        assertEquals(List.of(WritingTaskType.Q51, WritingTaskType.Q52,
                        WritingTaskType.Q53, WritingTaskType.Q54),
                savedQuestions.stream().map(PracticeQuestion::getWritingTaskType).toList());
        assertEquals(List.of(51, 52, 53, 54),
                savedQuestions.stream().map(PracticeQuestion::getQuestionNo).toList());
        assertEquals(List.of(BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.valueOf(30), BigDecimal.valueOf(50)),
                savedQuestions.stream().map(PracticeQuestion::getPoints).toList());
        assertEquals(0, savedSections.get(0).getTotalPoints()
                .compareTo(BigDecimal.valueOf(100)));
        assertEquals(1, savedTests.size());
        assertEquals(savedTests.get(0).getId(), savedSections.get(0).getTestId());
    }

    @Test
    void incompleteWritingSectionIsBlockedBeforeGraphMutation() {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", "Q51"));

        assertThrows(IllegalStateException.class, () -> publish(draft));
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void invalidWritingTaskFailsBeforeGraphMutation() {
        PracticeDraft draft = newDraft(draftJson("WRITING", "ESSAY", "GENERAL"));

        assertThrows(IllegalStateException.class, () -> publish(draft));
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void crossSkillQuestionTypeIsBlocked() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("READING", "ESSAY", "Q54")));

        assertThrows(IllegalStateException.class, () -> publish(draft));
        verify(setRepository, never()).save(any());
    }

    @Test
    void speakingEssayDraftIsBlockedBeforePublishingMutation() {
        PracticeDraft draft = newDraft(draftJsonWithQuestions(
                questionJson("SPEAKING", "ESSAY", "Q54")));

        assertThrows(IllegalStateException.class, () -> publish(draft));

        verify(questionRepository, never()).deleteBySetId(any());
        verify(groupRepository, never()).deleteBySetId(any());
        verify(sectionRepository, never()).deleteBySetId(any());
        verify(setRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void republishUsesCurrentDraftMetadataAndKeepsSectionLocalRenumbering() {
        PracticeDraft draft = newDraft(validReadingDraftJson());
        draft.setPublishedSetId(77L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        assignId(existingSet, 77L);
        savedSet.set(existingSet);

        Long setId = publish(draft);

        assertEquals(77L, setId);
        verify(questionRepository).deleteBySetId(77L);
        assertNull(savedQuestions.get(0).getWritingTaskType());
        assertEquals(1, savedQuestions.get(0).getQuestionNo());
    }

    @Test
    void republishWithLearnerHistoryBlocksBeforeGraphOrMetadataMutation() {
        PracticeDraft draft = newDraft(validReadingDraftJson());
        draft.setPublishedSetId(77L);
        PracticeSet existingSet = new PracticeSet("Old", "Old", "WRITING",
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
        publish(newDraft(validReadingDraftJson()));

        verify(mutationGuard, never()).lockAndAssertRepublishAllowed(any());
        assertEquals(1, savedEditLogs.size());
    }

    @Test
    void firstPublishStoresPublishedSetIdOnDraftForFutureRepublish() {
        PracticeDraft draft = newDraft(validReadingDraftJson());

        Long setId = publish(draft);

        assertEquals(setId, draft.getPublishedSetId());
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
    void publishSnapshotPreservesQ51ToQ54Identity() throws Exception {
        publish(newDraft(completeWritingDraftJson()));

        assertEquals(1, savedEditLogs.size());
        JsonNode after = objectMapper.readTree(savedEditLogs.get(0).getAfterSnapshotJson());
        JsonNode questions = after.path("sections").get(0).path("groups").get(0)
                .path("questions");
        assertEquals(List.of("Q51", "Q52", "Q53", "Q54"),
                java.util.stream.StreamSupport.stream(questions.spliterator(), false)
                        .map(question -> question.path("essayTaskType").asText())
                        .toList());
    }

    @Test
    void publishPersistsTypedListeningStimulusWithoutFoldingTranscriptIntoInstruction() throws Exception {
        publish(newDraft("""
                {
                  "document": {"detectedCategory": "TOPIK_II"},
                  "sections": [{
                    "title": "Listening",
                    "skill": "LISTENING",
                    "durationMinutes": 40,
                    "sectionDelivery": {
                      "schemaVersion": "practice-section-delivery-v1",
                      "listeningDelivery": {
                        "checkAudioReference": "/practice/materials/11/content"
                      }
                    },
                    "groups": [{
                      "label": "Dialogue",
                      "instruction": "Nghe và chọn đáp án.",
                      "stimulus": {
                        "schemaVersion": "practice-stimulus-v1",
                        "type": "LISTENING_AUDIO",
                        "transcriptText": "대화 원문",
                        "mediaReference": "/practice/materials/12/content",
                        "imageReference": "/practice/materials/13/content",
                        "provenance": {"source": "MANUAL", "approved": true, "sourceRegionIds": []}
                      },
                      "questions": [{
                        "questionNo": 1,
                        "questionType": "SINGLE_CHOICE",
                        "prompt": "무엇을 말하고 있습니까?",
                        "options": ["A", "B"],
                        "answer": {"value": "1"},
                        "points": 2
                      }]
                    }]
                  }]
                }
                """));

        PracticeQuestionGroup group = savedGroups.get(0);
        assertEquals("Nghe và chọn đáp án.", group.getInstruction());
        assertEquals("LISTENING_AUDIO", group.getStimulusType());
        assertNull(group.getPassageText());
        assertEquals("대화 원문", group.getTranscriptText());
        assertEquals("/practice/materials/12/content", group.getAudioUrl());
        assertEquals("/practice/materials/13/content", group.getImageUrl());
        JsonNode delivery = objectMapper.readTree(savedSections.get(0).getDeliveryJson());
        assertEquals("/practice/materials/11/content",
                delivery.path("listeningDelivery").path("checkAudioReference").asText());
        assertTrue(objectMapper.readTree(group.getStimulusProvenanceJson()).path("approved").asBoolean());
    }

    private Long publish(PracticeDraft draft) {
        when(draftRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.of(draft));
        return service.publish(1L, 99L);
    }

    private PracticeDraft newDraft(String draftJson) {
        String normalizedFixture = draftJson;
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root =
                    (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(draftJson);
            com.fasterxml.jackson.databind.node.ArrayNode tests = root.withArray("tests");
            tests.removeAll();
            for (int sectionIndex = 0; sectionIndex < root.path("sections").size(); sectionIndex++) {
                int testNo = sectionIndex + 1;
                String testClientId = "test-" + testNo;
                com.fasterxml.jackson.databind.node.ObjectNode test = tests.addObject();
                test.put("clientId", testClientId);
                test.put("testNo", testNo);
                test.put("title", "Test " + testNo);

                com.fasterxml.jackson.databind.node.ObjectNode section =
                        (com.fasterxml.jackson.databind.node.ObjectNode) root.path("sections").get(sectionIndex);
                String skill = section.path("skill").asText("READING");
                String prefix = switch (skill) {
                    case "LISTENING" -> "L";
                    case "WRITING" -> "W";
                    case "SPEAKING" -> "S";
                    default -> "R";
                };
                section.put("clientId", "section-" + testNo);
                section.put("testNo", testNo);
                section.put("testClientId", testClientId);
                section.put("lessonCode", prefix + testNo);
                int questionNo = "WRITING".equals(skill) ? 51 : 1;
                for (int groupIndex = 0; groupIndex < section.path("groups").size(); groupIndex++) {
                    com.fasterxml.jackson.databind.node.ObjectNode group =
                            (com.fasterxml.jackson.databind.node.ObjectNode) section.path("groups").get(groupIndex);
                    group.put("clientId", "group-" + testNo + "-" + (groupIndex + 1));
                    group.put("groupCode", prefix + testNo + "." + (groupIndex + 1));
                    for (JsonNode value : group.path("questions")) {
                        com.fasterxml.jackson.databind.node.ObjectNode question =
                                (com.fasterxml.jackson.databind.node.ObjectNode) value;
                        String task = question.path("essayTaskType").asText("");
                        if ("WRITING".equals(skill) && task.matches("Q5[1-4]")) {
                            question.put("questionNo", Integer.parseInt(task.substring(1)));
                        } else {
                            question.put("questionNo", questionNo);
                        }
                        questionNo = question.path("questionNo").asInt() + 1;
                    }
                }
            }
            normalizedFixture = root.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể chuẩn hóa fixture publish.", exception);
        }
        return new PracticeDraft(
                "Draft",
                "Description",
                "GLOBAL",
                null,
                "DRAFT",
                99L,
                normalizedFixture
        );
    }

    private String draftJson(String skill, String questionType, String taskValue) {
        return draftJsonWithQuestions(questionJson(skill, questionType, taskValue));
    }

    private String validReadingDraftJson() {
        return draftJsonWithQuestions(
                questionJson("READING", "SINGLE_CHOICE", "Q54"));
    }

    private String completeWritingDraftJson() {
        return """
                {
                  "document": {},
                  "sections": [{
                    "title": "Writing",
                    "skill": "WRITING",
                    "sectionType": "DEFAULT",
                    "durationMinutes": 50,
                    "totalPoints": 999,
                    "groups": [{
                      "label": "Writing Q51-Q54",
                      "instruction": "Viết theo yêu cầu.",
                      "questions": [%s]
                    }]
                  }]
                }
                """.formatted(String.join(",",
                questionJson("WRITING", "ESSAY", "Q51"),
                questionJson("WRITING", "ESSAY", "Q52"),
                questionJson("WRITING", "ESSAY", "Q53"),
                questionJson("WRITING", "ESSAY", "Q54")));
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
        int points = "WRITING".equals(skill)
                ? switch (taskValue) {
                    case "Q53" -> 30;
                    case "Q54" -> 50;
                    default -> 10;
                }
                : 10;
        return """
                {
                  "skill": "%s",
                  "questionType": "%s",
                  "prompt": "Prompt",
                  "options": ["A", "B"],
                  "answer": {"value": "1"},
                  "explanationVi": "",
                  "points": %d,
                  "essayTaskType": "%s"
                }
                """.formatted(skill, questionType, points, taskValue);
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
