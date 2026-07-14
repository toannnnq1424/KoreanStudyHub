package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeAssessmentExcelServiceTest {

    @Test
    void templateContainsOnlyTheFiveSupportedQuestionSheets() throws Exception {
        ExcelFixture fixture = fixture();
        byte[] bytes = fixture.service.buildTemplate();

        assertThat(bytes).startsWith((byte) 'P', (byte) 'K');
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("00_HUONG_DAN")).isNotNull();
            assertThat(workbook.getSheet("01_THONG_TIN_SET")).isNotNull();
            assertThat(workbook.getSheet("02_TAI_NGUYEN")).isNotNull();
            assertThat(workbook.getSheet("03_SINGLE_CHOICE")).isNotNull();
            assertThat(workbook.getSheet("04_TRUE_FALSE_NG")).isNotNull();
            assertThat(workbook.getSheet("05_FILL_BLANK")).isNotNull();
            assertThat(workbook.getSheet("06_ESSAY")).isNotNull();
            assertThat(workbook.getSheet("07_SPEAKING")).isNotNull();
            assertThat(workbook.getSheet("10_DANH_MUC")).isNotNull();
            assertThat(workbook.getSheet("04_MULTIPLE_CHOICE")).isNull();
            assertThat(workbook.getSheet("07_MATCHING")).isNull();
            assertThat(workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(13).getStringCellValue())
                    .isEqualTo("correct_answer");
            assertThat(workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(14).getStringCellValue())
                    .isEqualTo("teacher_explanation_vi");
            assertThat(workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(17).getStringCellValue())
                    .isEqualTo("option_A_text");
        }
    }

    @Test
    void generatedWorkbookPreviewsAllFiveTypesAndWritingTasksQ51ToQ54() throws Exception {
        ExcelFixture fixture = fixture();

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(
                workbookFile(fixture.service.buildTemplate()));

        assertThat(preview.canImport()).as(preview.issues().toString()).isTrue();
        assertThat(preview.rows()).extracting(PracticeAssessmentExcelService.ImportRowPreview::questionType)
                .contains("SINGLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "ESSAY", "SPEAKING")
                .doesNotContain("MULTIPLE_CHOICE", "MATCHING");
        assertThat(preview.rows().stream()
                .filter(row -> "ESSAY".equals(row.questionType()))
                .map(PracticeAssessmentExcelService.ImportRowPreview::questionNoInSection))
                .containsExactly("51", "52", "53", "54");

        JsonNode root = new ObjectMapper().readTree(preview.draftJson());
        assertThat(root.path("schemaVersion").asText()).isEqualTo("practice-draft-v3");
        assertThat(root.path("document").has("examTemplateCode")).isFalse();
        assertThat(root.path("document").has("assessmentProgramCode")).isFalse();
        JsonNode speaking = findQuestion(root, "SPEAKING");
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("promptAudioReference").asText())
                .startsWith("material:AUD_T01_S_Q");
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("promptPlayLimit").asInt())
                .isEqualTo(1);
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("preparationSeconds").asInt())
                .isEqualTo(30);
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("responseSeconds").asInt())
                .isEqualTo(60);
    }

    @Test
    void previewKeepsInvalidRowsVisibleAndImportDropsThem() throws Exception {
        ExcelFixture fixture = fixture();
        byte[] invalidWorkbook;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fixture.service.buildTemplate()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int rowIndex : List.of(3, 4, 5)) {
                org.apache.poi.ss.usermodel.Row invalid = workbook.getSheet("03_SINGLE_CHOICE").getRow(rowIndex);
                for (int column = 17; column <= 32; column++) {
                    if (invalid.getCell(column) != null) invalid.getCell(column).setBlank();
                }
            }
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }
        MockMultipartFile file = workbookFile(invalidWorkbook);
        when(fixture.repository.save(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file);

        assertThat(preview.canImport()).as(preview.issues().toString()).isTrue();
        assertThat(preview.errorRowCount()).isEqualTo(3);
        assertThat(preview.rows()).anyMatch(row -> !row.importable() && "ERROR".equals(row.status()));
        assertThat(preview.rows().stream().filter(PracticeAssessmentExcelService.ImportRowPreview::importable)
                .map(PracticeAssessmentExcelService.ImportRowPreview::importedQuestionNo))
                .doesNotContainNull();

        PracticeDraft imported = fixture.service.importDraft(file, null, 77L);
        JsonNode importedRoot = new ObjectMapper().readTree(imported.getDraftJson());
        assertThat(countQuestions(importedRoot)).isEqualTo(preview.importableQuestionCount());
        assertThat(imported.getCreationMethod()).isEqualTo("EXCEL");
    }

    @Test
    void previewShowsOptionsAndMediaReferencesForTeacherReview() throws Exception {
        ExcelFixture fixture = fixture();

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(
                workbookFile(fixture.service.buildTemplate()));
        PracticeAssessmentExcelService.ImportRowPreview row = preview.rows().stream()
                .filter(item -> "SINGLE_CHOICE".equals(item.questionType()))
                .findFirst().orElseThrow();

        assertThat(row.detail().options()).isNotEmpty();
        assertThat(row.detail().options().get(0).label()).isEqualTo("A");
        assertThat(row.mediaSummary()).isNotNull();
    }

    private static int countQuestions(JsonNode root) {
        int count = 0;
        for (JsonNode section : root.path("sections")) {
            for (JsonNode group : section.path("groups")) count += group.path("questions").size();
        }
        return count;
    }

    private static JsonNode findQuestion(JsonNode root, String questionType) {
        for (JsonNode section : root.path("sections")) {
            for (JsonNode group : section.path("groups")) {
                for (JsonNode question : group.path("questions")) {
                    if (questionType.equals(question.path("questionType").asText())) return question;
                }
            }
        }
        throw new AssertionError("Không tìm thấy câu " + questionType);
    }

    private static ExcelFixture fixture() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(new PracticeContentRules());
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
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

    private record ExcelFixture(PracticeAssessmentExcelService service,
                                PracticeDraftRepository repository) {
    }
}
