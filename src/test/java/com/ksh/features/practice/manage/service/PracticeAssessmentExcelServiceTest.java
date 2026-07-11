package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAssessmentExcelServiceTest {

    @Test
    void templateUsesTeacherFriendlyV2HierarchyAndDynamicQuestionSheets() throws Exception {
        ExcelFixture fixture = fixture(template());

        byte[] bytes = fixture.service.buildTemplate(template().code());

        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("00_HUONG_DAN"));
            assertNotNull(workbook.getSheet("01_THONG_TIN_SET"));
            assertNotNull(workbook.getSheet("02_TAI_NGUYEN"));
            assertNotNull(workbook.getSheet("03_SINGLE_CHOICE"));
            assertNotNull(workbook.getSheet("04_MULTIPLE_CHOICE"));
            assertNotNull(workbook.getSheet("05_TRUE_FALSE_NG"));
            assertNotNull(workbook.getSheet("06_FILL_BLANK"));
            assertNotNull(workbook.getSheet("07_MATCHING"));
            assertNotNull(workbook.getSheet("08_ESSAY"));
            assertNotNull(workbook.getSheet("09_SPEAKING"));
            assertNotNull(workbook.getSheet("10_DANH_MUC"));
            assertEquals("correct_answer", workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(13).getStringCellValue());
            assertEquals("teacher_explanation_vi", workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(14).getStringCellValue());
            assertEquals("option_A_text", workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(17).getStringCellValue());
            assertEquals("matching_L1_text", workbook.getSheet("07_MATCHING").getRow(0).getCell(37).getStringCellValue());
            assertEquals("matching_L1_image_ref", workbook.getSheet("07_MATCHING").getRow(0).getCell(38).getStringCellValue());
            assertEquals(5, workbook.getSheet("03_SINGLE_CHOICE").getLastRowNum());
        }
    }

    @Test
    void generatedV2WorkbookParsesAllSevenQuestionTypesToTypedDraft() throws Exception {
        ExcelFixture fixture = fixture(template());
        MockMultipartFile file = workbookFile(fixture.service.buildTemplate(template().code()));

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file, template().code());

        assertFalse(preview.hasBlocking(), preview.issues().toString());
        assertTrue(preview.canImport());
        assertEquals(28, preview.questionCount());
        assertEquals(28, preview.importableQuestionCount());
        JsonNode root = new ObjectMapper().readTree(preview.draftJson());
        assertEquals("practice-draft-v3", root.path("schemaVersion").asText());
        assertEquals(1, root.path("tests").size());
        assertTrue(root.path("sections").size() >= 3);
        JsonNode firstQuestion = root.path("sections").path(0).path("groups").path(0).path("questions").path(0);
        assertEquals("SINGLE_CHOICE", firstQuestion.path("questionType").asText());
        assertEquals("opt_A", firstQuestion.path("answerSpec").path("correctOptionIds").path(0).asText());
        PracticeAssessmentExcelService.ImportRowPreview matching = preview.rows().stream()
                .filter(row -> "MATCHING".equals(row.questionType())).findFirst().orElseThrow();
        assertEquals("민수", matching.detail().matchingPairs().get(0).leftText());
        assertEquals("매일 한국어를 공부합니다.", matching.detail().matchingPairs().get(0).rightText());
    }

    @Test
    void previewShowsInvalidRowButImportSilentlyDropsIt() throws Exception {
        ExcelFixture fixture = fixture(template());
        byte[] invalidWorkbook;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(
                fixture.service.buildTemplate(template().code())));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Row invalid = workbook.getSheet("03_SINGLE_CHOICE").getRow(3);
            for (int column : List.of(19, 21, 23)) {
                invalid.getCell(column).setBlank();
            }
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }
        MockMultipartFile file = workbookFile(invalidWorkbook);
        when(fixture.repository.save(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file, template().code());

        assertTrue(preview.hasBlocking());
        assertFalse(preview.hasFatalBlocking(), preview.issues().toString());
        assertTrue(preview.canImport());
        assertEquals(28, preview.questionCount());
        assertEquals(27, preview.importableQuestionCount());
        assertEquals(1, preview.errorRowCount());
        assertTrue(preview.rows().stream().anyMatch(row -> row.status().equals("ERROR")
                && row.lessonCode().equals("L1") && row.questionNoInSection().equals("2")));

        PracticeDraft imported = fixture.service.importDraft(file, template().code(), null, 77L);
        JsonNode root = new ObjectMapper().readTree(imported.getDraftJson());
        int importedQuestions = 0;
        for (JsonNode section : root.path("sections")) {
            for (JsonNode group : section.path("groups")) importedQuestions += group.path("questions").size();
        }
        assertEquals(27, importedQuestions);
        assertEquals("EXCEL", imported.getCreationMethod());
    }

    @Test
    void invalidRowsStayVisibleWhileLaterGroupsReceiveContiguousImportedNumbers() throws Exception {
        ExcelFixture fixture = fixture(template());
        byte[] invalidWorkbook;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(
                fixture.service.buildTemplate(template().code())));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int rowIndex : List.of(3, 4, 5)) {
                org.apache.poi.ss.usermodel.Row invalid = workbook.getSheet("03_SINGLE_CHOICE").getRow(rowIndex);
                for (int column : List.of(19, 21, 23)) invalid.getCell(column).setBlank();
            }
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }
        MockMultipartFile file = workbookFile(invalidWorkbook);
        when(fixture.repository.save(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file, template().code());

        assertTrue(preview.canImport(), preview.issues().toString());
        assertEquals(3, preview.errorRowCount());
        assertEquals(25, preview.importableQuestionCount());
        PracticeAssessmentExcelService.ImportRowPreview sourceQuestionFive = preview.rows().stream()
                .filter(row -> "L1".equals(row.lessonCode()) && "5".equals(row.questionNoInSection()))
                .findFirst().orElseThrow();
        assertEquals("L1.2", sourceQuestionFive.groupCode());
        assertEquals(2, sourceQuestionFive.importedQuestionNo());

        PracticeDraft imported = fixture.service.importDraft(file, template().code(), null, 77L);
        JsonNode importedRoot = new ObjectMapper().readTree(imported.getDraftJson());
        JsonNode listening = findLesson(importedRoot, "L1");
        assertEquals(1, listening.path("groups").path(0).path("questions").path(0).path("questionNo").asInt());
        assertEquals(2, listening.path("groups").path(1).path("questions").path(0).path("questionNo").asInt());
        assertTrue(listening.path("groups").path(1).path("questions").path(0)
                .path("clientId").asText().endsWith("q005"));
    }

    @Test
    void importReplacesSelectedLocalMaterialWithManagedUploadReference() throws Exception {
        ExcelFixture fixture = fixture(template());
        byte[] workbookWithImage;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(
                fixture.service.buildTemplate(template().code())));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheet("03_SINGLE_CHOICE").getRow(2).getCell(7).setCellValue("IMG_L1_G01");
            workbook.write(output);
            workbookWithImage = output.toByteArray();
        }
        MockMultipartFile file = workbookFile(workbookWithImage);
        when(fixture.repository.save(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String managedUrl = "/uploads/practice-images/123e4567-e89b-12d3-a456-426614174000.png";

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file, template().code());

        assertTrue(preview.canImport(), preview.issues().toString());
        assertEquals("material:IMG_L1_G01", preview.rows().get(0).detail().groupImageReference());

        PracticeDraft imported = fixture.service.importDraft(
                file, template().code(), null, 77L, "{\"IMG_L1_G01\":\"" + managedUrl + "\"}");
        JsonNode root = new ObjectMapper().readTree(imported.getDraftJson());
        assertEquals(managedUrl, findLesson(root, "L1").path("groups").path(0).path("imageUrl").asText());
        assertEquals(managedUrl, root.path("materials").path(0).path("managedReference").asText());
        assertFalse(root.path("materials").path(0).path("pendingUpload").asBoolean(true));
    }

    @Test
    void topikTemplateOmitsDisallowedQuestionTypeSheetsAndPinsFourOptions() throws Exception {
        ExcelFixture fixture = fixture(topikTemplate());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(
                fixture.service.buildTemplate(topikTemplate().code())))) {
            assertNotNull(workbook.getSheet("03_SINGLE_CHOICE"));
            assertNull(workbook.getSheet("04_MULTIPLE_CHOICE"));
            assertNull(workbook.getSheet("06_FILL_BLANK"));
            org.apache.poi.ss.usermodel.Row sample = workbook.getSheet("03_SINGLE_CHOICE").getRow(2);
            assertFalse(sample.getCell(17).getStringCellValue().isBlank());
            assertFalse(sample.getCell(23).getStringCellValue().isBlank());
            assertTrue(sample.getCell(25) == null || sample.getCell(25).getStringCellValue().isBlank());
        }
    }

    @Test
    void linkedImportReplacesImportedLessonsAndPreservesOtherEditorLessons() throws Exception {
        ExcelFixture fixture = fixture(template());
        MockMultipartFile file = workbookFile(fixture.service.buildTemplate(template().code()));
        PracticeAssessmentExcelService.ExcelPreview generated = fixture.service.preview(file, template().code());
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode existingRoot =
                (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(generated.draftJson());
        com.fasterxml.jackson.databind.node.ArrayNode sections = existingRoot.withArray("sections");
        com.fasterxml.jackson.databind.node.ObjectNode reading =
                (com.fasterxml.jackson.databind.node.ObjectNode) sections.get(0).deepCopy();
        reading.put("skill", "READING");
        reading.put("lessonCode", "R1");
        reading.put("title", "Phần Đọc giữ lại");
        for (int index = 0; index < reading.path("groups").size(); index++) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) reading.path("groups").get(index))
                    .put("groupCode", "R1." + (index + 1));
        }
        sections.removeAll();
        sections.add(reading);
        PracticeDraft linked = new PracticeDraft(
                "Bộ đề đang chỉnh", "Mô tả cũ", "CUSTOM", "GLOBAL", null,
                "DRAFT", 77L, existingRoot.toString());
        linked.setExamTemplateCode(template().code());
        linked.setAssessmentProgramCode("CUSTOM");
        linked.setAssessmentProgramVersionId(12L);
        when(fixture.repository.findByIdAndOwnerId(44L, 77L)).thenReturn(Optional.of(linked));
        when(fixture.repository.save(any(PracticeDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeDraft imported = fixture.service.importDraft(file, template().code(), 44L, 77L);

        JsonNode merged = mapper.readTree(imported.getDraftJson());
        assertEquals("Bộ đề đang chỉnh", imported.getTitle());
        assertEquals("MANUAL", imported.getCreationMethod());
        assertTrue(containsLesson(merged, "R1"));
        assertTrue(containsLesson(merged, "L1"));
        assertTrue(containsLesson(merged, "W1"));
        assertTrue(containsLesson(merged, "S1"));
    }

    @Test
    void linkedExcelContextAllowsWritingAndSpeakingWhenTemplateEnablesThem() throws Exception {
        ExcelFixture fixture = fixture(template());
        PracticeAssessmentExcelService.ExcelPreview generated = fixture.service.preview(
                workbookFile(fixture.service.buildTemplate(template().code())), template().code());
        PracticeDraft linked = new PracticeDraft(
                "Bộ đề đủ kỹ năng", "", "CUSTOM", "GLOBAL", null,
                "DRAFT", 77L, generated.draftJson());
        linked.setExamTemplateCode(template().code());
        linked.setAssessmentProgramCode("CUSTOM");
        linked.setAssessmentProgramVersionId(12L);
        when(fixture.repository.findByIdAndOwnerId(44L, 77L)).thenReturn(Optional.of(linked));

        PracticeAssessmentExcelService.ExcelImportContext writing =
                fixture.service.requireExcelImportContext(44L, 77L, 1, "W1");
        PracticeAssessmentExcelService.ExcelImportContext speaking =
                fixture.service.requireExcelImportContext(44L, 77L, 1, "S1");

        assertEquals("WRITING", writing.skill());
        assertEquals("SPEAKING", speaking.skill());
    }

    private static ExcelFixture fixture(AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        AssessmentAuthoringCatalogService catalog = mock(AssessmentAuthoringCatalogService.class);
        when(catalog.requireTemplate(template.code())).thenReturn(template);
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService contract = new PracticeDraftContractService(objectMapper, catalog, resolver, codec);
        PracticeDraftRepository repository = mock(PracticeDraftRepository.class);
        PracticeAssessmentExcelService service = new PracticeAssessmentExcelService(
                catalog, contract, new PracticeDraftValidator(objectMapper), repository,
                codec, resolver, objectMapper);
        return new ExcelFixture(service, repository);
    }

    private static MockMultipartFile workbookFile(byte[] bytes) {
        return new MockMultipartFile("file", "assessment-v2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static boolean containsLesson(JsonNode root, String lessonCode) {
        return !findLesson(root, lessonCode).isMissingNode();
    }

    private static JsonNode findLesson(JsonNode root, String lessonCode) {
        for (JsonNode section : root.path("sections")) {
            if (lessonCode.equals(section.path("lessonCode").asText())) return section;
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static AssessmentAuthoringCatalogService.ExamTemplatePolicy template() {
        return new AssessmentAuthoringCatalogService.ExamTemplatePolicy(
                "CUSTOM_FLEXIBLE", "Bai luyen tuy chinh", "CUSTOM", "CUSTOM", 12L, 1,
                Map.of(
                        "LISTENING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                                40, BigDecimal.ONE,
                                List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "MATCHING")),
                        "READING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                                40, BigDecimal.ONE,
                                List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "MATCHING")),
                        "WRITING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                                50, BigDecimal.valueOf(50), List.of("ESSAY")),
                        "SPEAKING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                                30, BigDecimal.valueOf(50), List.of("SPEAKING"))),
                10);
    }

    private static AssessmentAuthoringCatalogService.ExamTemplatePolicy topikTemplate() {
        AssessmentAuthoringCatalogService.QuestionAuthoringPolicy single =
                new AssessmentAuthoringCatalogService.QuestionAuthoringPolicy(
                        "SINGLE_CHOICE", "ALL_OR_NOTHING", List.of("ALL_OR_NOTHING"),
                        null, null, null, 4, 4);
        return new AssessmentAuthoringCatalogService.ExamTemplatePolicy(
                "TOPIK_I", "TOPIK I", "TOPIK_I", "TOPIK", 10L, 1,
                Map.of(
                        "LISTENING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                                40, BigDecimal.valueOf(2), List.of("SINGLE_CHOICE"), false,
                                Map.of("SINGLE_CHOICE", single), 50, true),
                        "READING", new AssessmentAuthoringCatalogService.SkillAuthoringPolicy(
                                60, BigDecimal.valueOf(2), List.of("SINGLE_CHOICE"), false,
                                Map.of("SINGLE_CHOICE", single), 50, true)),
                10);
    }

    private record ExcelFixture(PracticeAssessmentExcelService service,
                                PracticeDraftRepository repository) {
    }
}
