package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfDraftAssemblerContractTest {

    @Test
    void aiDraftUsesSingleScopeAndCanonicalTypedContracts() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(new PracticeContentRules());
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        when(drafts.save(any(PracticeDraft.class))).thenAnswer(invocation -> {
            PracticeDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 88L);
            return draft;
        });
        PracticePdfImportSessionService sessions = mock(PracticePdfImportSessionService.class);
        PracticePdfDraftAssembler assembler = new PracticePdfDraftAssembler(
                drafts, sessions, objectMapper, contract);

        PracticeDraft saved = assembler.assembleAndSaveDraft(session(), aiResponse(), 7L);
        JsonNode root = objectMapper.readTree(saved.getDraftJson());
        JsonNode group = root.path("sections").path(0).path("groups").path(0);
        JsonNode question = group.path("questions").path(0);

        assertEquals("practice-draft-v3", root.path("schemaVersion").asText());
        assertFalse(root.path("document").has("examTemplateCode"));
        assertFalse(root.path("document").has("assessmentProgramCode"));
        assertEquals("SINGLE_CHOICE", question.path("questionType").asText());
        assertEquals("opt_2", question.path("answerSpec").path("correctOptionIds").path(0).asText());
        assertEquals("opt_1", question.path("questionContent").path("options").path(0).path("id").asText());
        assertEquals("READING_PASSAGE", group.path("stimulus").path("type").asText());
        assertEquals("PDF_AI", group.path("stimulus").path("provenance").path("source").asText());
        assertFalse(group.path("stimulus").path("provenance").path("approved").asBoolean());
        assertEquals(9, question.path("sourceQuestionNo").asInt());
        assertEquals(1, question.path("questionNo").asInt());
        verify(sessions).updateDraftId(9L, 88L);
    }

    @Test
    void pdfImportKeepsSingleChoiceDefaultAndSourceReviewGate() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(new PracticeContentRules());
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        when(drafts.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PracticePdfDraftAssembler assembler = new PracticePdfDraftAssembler(
                drafts, mock(PracticePdfImportSessionService.class), objectMapper, contract);

        PracticeDraft saved = assembler.assembleAndSaveDraft(session(), aiResponse(), 7L);
        JsonNode question = objectMapper.readTree(saved.getDraftJson())
                .path("sections").path(0).path("groups").path(0).path("questions").path(0);

        assertEquals("SINGLE_CHOICE", question.path("questionType").asText());
        assertEquals("opt_2", question.path("answerSpec").path("correctOptionIds").path(0).asText());
        assertEquals("PDF_AI", question.path("importSource").asText());
        assertTrue(question.path("reviewRequired").asBoolean());
    }

    @Test
    void linkedPdfImportAppendsOnlyToSelectedSectionAndKeepsSourceNumber() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(new PracticeContentRules());
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        PracticeDraft existing = new PracticeDraft("Bộ đề đang soạn", "Giữ mô tả",
                "GLOBAL", null, "DRAFT", 7L, existingDraftJson());
        ReflectionTestUtils.setField(existing, "id", 55L);
        when(drafts.findByIdAndOwnerId(55L, 7L)).thenReturn(Optional.of(existing));
        when(drafts.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PracticePdfDraftAssembler assembler = new PracticePdfDraftAssembler(
                drafts, mock(PracticePdfImportSessionService.class), objectMapper, contract);
        PracticePdfImportSession session = session();
        session.setLinkedDraftId(55L);

        PracticeDraft saved = assembler.assembleAndSaveDraft(session, aiResponse(), 7L);
        JsonNode root = objectMapper.readTree(saved.getDraftJson());
        JsonNode r1 = root.path("sections").path(0);
        JsonNode importedQuestion = r1.path("groups").path(1).path("questions").path(0);

        assertEquals("Bộ đề đang soạn", saved.getTitle());
        assertEquals(2, root.path("sections").size());
        assertEquals("R2", root.path("sections").path(1).path("lessonCode").asText());
        assertEquals(2, importedQuestion.path("questionNo").asInt());
        assertEquals(9, importedQuestion.path("sourceQuestionNo").asInt());
        assertEquals("R1.2", r1.path("groups").path(1).path("groupCode").asText());
    }

    private static PracticePdfImportSession session() {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "custom.pdf", "/tmp/custom.pdf", 1, "READY_FOR_AI",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(9L);
        session.setTargetTestNo(1);
        session.setTargetSkill("READING");
        session.setTargetLessonCode("R1");
        return session;
    }

    private static String aiResponse() {
        return """
                {
                  "sections": [{
                    "clientId": "section-reading",
                    "label": "Doc hieu",
                    "skill": "READING",
                    "sourceRegionIds": ["page-1"],
                    "groups": [{
                      "clientId": "group-1",
                      "label": "Doan van",
                      "instruction": "Chon dap an dung",
                      "passage": "Noi dung bai doc",
                      "sourceRegionIds": ["page-1"],
                      "questions": [{
                        "questionNo": 9,
                        "questionType": "MCQ_SINGLE",
                        "prompt": "Cau nao dung?",
                        "options": ["Phuong an A", "Phuong an B"],
                        "answerKey": "2",
                        "points": 1,
                        "sourceRegionIds": ["page-1"]
                      }]
                    }]
                  }]
                }
                """;
    }

    private static String existingDraftJson() {
        return """
                {
                  "document": {"title": "Bộ đề đang soạn", "examTemplateCode": "CUSTOM_FLEXIBLE"},
                  "sections": [
                    {
                      "testNo": 1, "lessonCode": "R1", "skill": "READING", "title": "Phần Đọc",
                      "groups": [{
                        "groupCode": "R1.1", "label": "R1.1",
                        "questions": [{"questionNo": 1, "questionType": "SINGLE_CHOICE", "prompt": "Câu cũ", "options": ["A", "B"], "answerKey": "1"}]
                      }]
                    },
                    {
                      "testNo": 2, "lessonCode": "R2", "skill": "READING", "title": "Phần Đọc 2",
                      "groups": [{
                        "groupCode": "R2.1", "label": "R2.1",
                        "questions": [{"questionNo": 1, "questionType": "SINGLE_CHOICE", "prompt": "Giữ lại", "options": ["A", "B"], "answerKey": "1"}]
                      }]
                    }
                  ]
                }
                """;
    }
}
