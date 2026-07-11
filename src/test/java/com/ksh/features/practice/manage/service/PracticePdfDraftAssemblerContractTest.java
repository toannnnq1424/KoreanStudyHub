package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfDraftAssemblerContractTest {

    @Test
    void aiDraftUsesSelectedTemplateAndCanonicalTypedContracts() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog = mock(AssessmentAuthoringCatalogService.class);
        when(catalog.requireTemplate("CUSTOM_FLEXIBLE")).thenReturn(customTemplate());
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
        assertEquals("CUSTOM_FLEXIBLE", root.path("document").path("examTemplateCode").asText());
        assertEquals("SINGLE_CHOICE", question.path("questionType").asText());
        assertEquals("opt_2", question.path("answerSpec").path("correctOptionIds").path(0).asText());
        assertEquals("opt_1", question.path("questionContent").path("options").path(0).path("id").asText());
        assertEquals("READING_PASSAGE", group.path("stimulus").path("type").asText());
        assertEquals("PDF_AI", group.path("stimulus").path("provenance").path("source").asText());
        assertFalse(group.path("stimulus").path("provenance").path("approved").asBoolean());
        assertEquals("CUSTOM", saved.getCategory());
        assertEquals("CUSTOM", saved.getAssessmentProgramCode());
        assertNotNull(saved.getAssessmentProgramVersionId());
        verify(sessions).updateDraftId(9L, 88L);
    }

    @Test
    void topikPdfImportKeepsSingleChoiceDefaultAndSourceReviewGate() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        AssessmentAuthoringCatalogService catalog = mock(AssessmentAuthoringCatalogService.class);
        when(catalog.requireTemplate("TOPIK_II")).thenReturn(new AssessmentAuthoringCatalogService.ExamTemplatePolicy(
                "TOPIK_II", "TOPIK II", "TOPIK_II", "TOPIK", 10L, 1,
                Map.of("READING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                        70, BigDecimal.valueOf(2), List.of("SINGLE_CHOICE")))));
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
        PracticeDraftRepository drafts = mock(PracticeDraftRepository.class);
        when(drafts.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PracticePdfDraftAssembler assembler = new PracticePdfDraftAssembler(
                drafts, mock(PracticePdfImportSessionService.class), objectMapper, contract);

        PracticeDraft saved = assembler.assembleAndSaveDraft(session("TOPIK_II"), aiResponse(), 7L);
        JsonNode question = objectMapper.readTree(saved.getDraftJson())
                .path("sections").path(0).path("groups").path(0).path("questions").path(0);

        assertEquals("TOPIK_II", saved.getCategory());
        assertEquals("SINGLE_CHOICE", question.path("questionType").asText());
        assertEquals("opt_2", question.path("answerSpec").path("correctOptionIds").path(0).asText());
        assertEquals("PDF_AI", question.path("importSource").asText());
        assertTrue(question.path("reviewRequired").asBoolean());
    }

    private static AssessmentAuthoringCatalogService.ExamTemplatePolicy customTemplate() {
        return new AssessmentAuthoringCatalogService.ExamTemplatePolicy(
                "CUSTOM_FLEXIBLE", "Bai luyen tuy chinh", "CUSTOM", "CUSTOM", 12L, 1,
                Map.of("READING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                        40, BigDecimal.ONE, List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE"))));
    }

    private static PracticePdfImportSession session() {
        return session("CUSTOM_FLEXIBLE");
    }

    private static PracticePdfImportSession session(String category) {
        PracticePdfImportSession session = new PracticePdfImportSession(
                7L, "custom.pdf", "/tmp/custom.pdf", 1, "READY_FOR_AI",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        session.setId(9L);
        session.setExamCategory(category);
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
                        "questionNo": 1,
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
}
