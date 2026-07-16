package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class PracticeDraftServiceTest {

    private final PracticeDraftRepository draftRepository = Mockito.mock(PracticeDraftRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PracticeDraftService service = new PracticeDraftService(draftRepository, objectMapper);

    @Test
    public void testCreateEmptyDraft() {
        when(draftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeDraft draft = service.getOrCreateEmptyDraft(99L);
        assertNotNull(draft);
        assertEquals("DRAFT", draft.getStatus());
        assertEquals(99L, draft.getOwnerId());
        assertTrue(draft.getDraftJson().contains("sections"));
    }

    @Test
    public void testSaveDraftState() {
        PracticeDraft draft = new PracticeDraft("Tiêu đề", "Mô tả",  "GLOBAL", null, "DRAFT", 99L, "{}");
        when(draftRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.of(draft));
        when(draftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.saveAndFlush(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeDraft saved = service.saveDraftState(1L, 99L, "{\"sections\": []}", "Tiêu đề mới", "Mô tả mới", null);
        assertEquals("Tiêu đề mới", saved.getTitle());
        assertEquals("Mô tả mới", saved.getDescription());
        assertEquals("{\"sections\": []}", saved.getDraftJson());
    }

    @Test
    public void createDraftFromPublishedSetPreservesWritingTaskMetadata() throws Exception {
        PracticeSetRepository setRepository = Mockito.mock(PracticeSetRepository.class);
        PracticeSectionRepository sectionRepository = Mockito.mock(PracticeSectionRepository.class);
        PracticeQuestionGroupRepository groupRepository = Mockito.mock(PracticeQuestionGroupRepository.class);
        PracticeQuestionRepository questionRepository = Mockito.mock(PracticeQuestionRepository.class);
        PracticeDraftRepository localDraftRepository = Mockito.mock(PracticeDraftRepository.class);
        PracticeDraftService fullService = new PracticeDraftService(
                localDraftRepository,
                setRepository,
                sectionRepository,
                groupRepository,
                questionRepository,
                objectMapper
        );

        PracticeSet set = new PracticeSet("Set", "Description", "WRITING",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        setEntityId(set, 10L);
        PracticeSection section = new PracticeSection(10L, "Writing", "WRITING", "DEFAULT",
                "", 50, BigDecimal.TEN, 0);
        setEntityId(section, 20L);
        PracticeQuestionGroup group = new PracticeQuestionGroup(10L, "Group", 1, 1, "", null, null, 0);
        setEntityId(group, 30L);
        group.setSectionId(20L);
        PracticeQuestion question = new PracticeQuestion(10L, 1, "ESSAY", "Prompt",
                null, "", "Explanation", BigDecimal.TEN, 0);
        question.setGroupId(30L);
        question.setWritingTaskType(WritingTaskType.Q54);

        when(localDraftRepository.findByPublishedSetIdAndOwnerId(10L, 99L)).thenReturn(Optional.empty());
        when(localDraftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(setRepository.findByIdAndCreatedBy(10L, 99L)).thenReturn(Optional.of(set));
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(section));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(question));

        PracticeDraft draft = fullService.createDraftFromPublishedSet(10L, 99L);

        JsonNode root = objectMapper.readTree(draft.getDraftJson());
        JsonNode qNode = root.path("sections").get(0).path("groups").get(0).path("questions").get(0);
        assertEquals("Q54", qNode.path("essayTaskType").asText());
    }

    @Test
    public void createDraftFromPublishedSetOmitsStaleNonWritingTaskMetadata() throws Exception {
        PracticeSetRepository setRepository = Mockito.mock(PracticeSetRepository.class);
        PracticeSectionRepository sectionRepository = Mockito.mock(PracticeSectionRepository.class);
        PracticeQuestionGroupRepository groupRepository = Mockito.mock(PracticeQuestionGroupRepository.class);
        PracticeQuestionRepository questionRepository = Mockito.mock(PracticeQuestionRepository.class);
        PracticeDraftRepository localDraftRepository = Mockito.mock(PracticeDraftRepository.class);
        PracticeDraftService fullService = new PracticeDraftService(
                localDraftRepository,
                setRepository,
                sectionRepository,
                groupRepository,
                questionRepository,
                objectMapper
        );

        PracticeSet set = new PracticeSet("Set", "Description", "READING",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        setEntityId(set, 10L);
        PracticeSection section = new PracticeSection(10L, "Reading", "READING", "DEFAULT",
                "", 50, BigDecimal.TEN, 0);
        setEntityId(section, 20L);
        PracticeQuestionGroup group = new PracticeQuestionGroup(10L, "Group", 1, 1, "", null, null, 0);
        setEntityId(group, 30L);
        group.setSectionId(20L);
        PracticeQuestion question = new PracticeQuestion(10L, 1, "ESSAY", "Prompt",
                null, "", "Explanation", BigDecimal.TEN, 0);
        question.setGroupId(30L);
        question.setWritingTaskType(WritingTaskType.Q54);

        when(localDraftRepository.findByPublishedSetIdAndOwnerId(10L, 99L)).thenReturn(Optional.empty());
        when(localDraftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(setRepository.findByIdAndCreatedBy(10L, 99L)).thenReturn(Optional.of(set));
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(section));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(question));

        PracticeDraft draft = fullService.createDraftFromPublishedSet(10L, 99L);

        JsonNode root = objectMapper.readTree(draft.getDraftJson());
        JsonNode qNode = root.path("sections").get(0).path("groups").get(0).path("questions").get(0);
        assertFalse(qNode.has("essayTaskType"));
    }

    @Test
    public void createDraftFromPublishedSetPreservesLegacyUngroupedQuestions() throws Exception {
        PracticeSetRepository setRepository = Mockito.mock(PracticeSetRepository.class);
        PracticeSectionRepository sectionRepository = Mockito.mock(PracticeSectionRepository.class);
        PracticeQuestionGroupRepository groupRepository = Mockito.mock(PracticeQuestionGroupRepository.class);
        PracticeQuestionRepository questionRepository = Mockito.mock(PracticeQuestionRepository.class);
        PracticeDraftRepository localDraftRepository = Mockito.mock(PracticeDraftRepository.class);
        PracticeDraftService fullService = new PracticeDraftService(
                localDraftRepository,
                setRepository,
                sectionRepository,
                groupRepository,
                questionRepository,
                objectMapper
        );

        PracticeSet set = new PracticeSet("Legacy set", "Description", "READING",
                "GLOBAL", null, null, "{}", "PUBLISHED", 99L);
        setEntityId(set, 10L);
        PracticeSection section = new PracticeSection(10L, "Reading", "READING", "DEFAULT",
                "Đọc và chọn đáp án.", 40, BigDecimal.TEN, 0);
        setEntityId(section, 20L);
        PracticeQuestion question = new PracticeQuestion(10L, 1, "SINGLE_CHOICE", "레거시 질문",
                "[\"A\",\"B\"]", "A", "Giải thích", BigDecimal.TEN, 0);

        when(localDraftRepository.findByPublishedSetIdAndOwnerId(10L, 99L)).thenReturn(Optional.empty());
        when(localDraftRepository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(setRepository.findById(10L)).thenReturn(Optional.of(set));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(section));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of());
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(question));

        PracticeDraft draft = fullService.createDraftFromPublishedSet(10L, 99L);

        JsonNode root = objectMapper.readTree(draft.getDraftJson());
        JsonNode groupNode = root.path("sections").get(0).path("groups").get(0);
        assertEquals("R1.1", groupNode.path("groupCode").asText());
        assertEquals("레거시 질문", groupNode.path("questions").get(0).path("prompt").asText());
        assertEquals(2, groupNode.path("questions").get(0).path("options").size());
    }

    @Test
    void crossOwnerCannotReadSaveOrDeleteDraft() {
        when(draftRepository.findByIdAndOwnerId(1L, 100L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.getDraft(1L, 100L));
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.saveDraftState(1L, 100L, "{}", "Title", "", null));
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.deleteDraft(1L, 100L));
    }

    private void setEntityId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
