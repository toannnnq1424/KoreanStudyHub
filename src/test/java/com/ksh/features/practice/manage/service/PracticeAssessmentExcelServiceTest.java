package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.manage.speaking.SpeakingPromptLifecycleService;
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
import static org.mockito.Mockito.verify;
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
            assertThat(workbook.getSheet("00_HUONG_DAN").getRow(10)
                    .getCell(1).getStringCellValue())
                    .contains("Excel không bật hoặc gọi TTS")
                    .contains("mở từng câu Speaking trong Editor");
            assertThat(workbook.getSheet("04_MULTIPLE_CHOICE")).isNull();
            assertThat(workbook.getSheet("07_MATCHING")).isNull();
            assertThat(workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(13).getStringCellValue())
                    .isEqualTo("correct_answer");
            assertThat(workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(14).getStringCellValue())
                    .isEqualTo("teacher_explanation_vi");
            assertThat(workbook.getSheet("03_SINGLE_CHOICE").getRow(0).getCell(17).getStringCellValue())
                    .isEqualTo("option_A_text");
            assertThat(List.of(2, 3, 4, 5).stream()
                    .map(row -> workbook.getSheet("06_ESSAY").getRow(row).getCell(15).getNumericCellValue())
                    .toList()).containsExactly(10.0, 10.0, 30.0, 50.0);
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
        assertThat(speaking.path("questionContent").path("schemaVersion").asText())
                .isEqualTo("question-content-v2");
        assertThat(speaking.path("questionContent").path("speakingDelivery")
                .path("inputType").asText()).isEqualTo("audio_upload");
        assertThat(speaking.path("questionContent").path("speakingDelivery")
                .path("deliveryMode").asText()).isEqualTo("audio_only");
        assertThat(speaking.path("questionContent").path("speakingDelivery")
                .path("audioOrigin").asText()).isEqualTo("teacher_upload");
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("promptAudioReference").asText())
                .startsWith("material:AUD_T01_S_Q");
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("promptPlayLimit").asInt())
                .isEqualTo(1);
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("preparationSeconds").asInt())
                .isEqualTo(30);
        assertThat(speaking.path("questionContent").path("speakingDelivery").path("responseSeconds").asInt())
                .isEqualTo(60);
        JsonNode fillBlank = findQuestion(root, "FILL_BLANK");
        assertThat(fillBlank.path("prompt").asText()).contains("{{blank:B1}}");
        assertThat(writingPoints(root)).containsExactly("10", "10", "30", "50");
    }

    @Test
    void previewRejectsWritingRowWhosePointsDoNotMatchItsTask() throws Exception {
        ExcelFixture fixture = fixture();
        byte[] invalidWorkbook;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fixture.service.buildTemplate()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheet("06_ESSAY").getRow(5).getCell(15).setCellValue(10);
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(
                workbookFile(invalidWorkbook));

        assertThat(preview.issues()).anyMatch(issue ->
                "WRITING_TASK_POINTS_MISMATCH".equals(issue.code()));
        assertThat(preview.rows()).anyMatch(row ->
                "54".equals(row.questionNoInSection()) && !row.importable());
    }

    @Test
    void modernWorkbookRequiresExplicitV2SchemaDeclaration() throws Exception {
        ExcelFixture fixture = fixture();
        byte[] invalidWorkbook;
        try (Workbook workbook = WorkbookFactory.create(
                    new ByteArrayInputStream(fixture.service.buildTemplate()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheet("01_THONG_TIN_SET").getRow(2).getCell(1).setBlank();
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }

        PracticeAssessmentExcelService.ExcelPreview preview =
                fixture.service.preview(workbookFile(invalidWorkbook));

        assertThat(preview.issues()).anyMatch(issue ->
                "SCHEMA_VERSION_UNSUPPORTED".equals(issue.code()));
        assertThat(preview.canImport()).isFalse();
    }

    @Test
    void modernWorkbookRejectsCaseMutatedExplicitV2Declaration()
            throws Exception {
        ExcelFixture fixture = fixture();
        byte[] invalidWorkbook;
        try (Workbook workbook = WorkbookFactory.create(
                    new ByteArrayInputStream(fixture.service.buildTemplate()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheet("01_THONG_TIN_SET")
                    .getRow(2)
                    .getCell(1)
                    .setCellValue("PRACTICE-EXCEL-V2");
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }

        PracticeAssessmentExcelService.ExcelPreview preview =
                fixture.service.preview(workbookFile(invalidWorkbook));

        assertThat(preview.issues()).anyMatch(issue ->
                "SCHEMA_VERSION_UNSUPPORTED".equals(issue.code()));
        assertThat(preview.canImport()).isFalse();
    }

    @Test
    void historicalV1WorkbookReaderRemainsExactForItsLegacySheetContract()
            throws Exception {
        ExcelFixture fixture = fixture();
        MockMultipartFile file = workbookFile(
                legacyWorkbook(false));
        when(fixture.repository.saveAndFlush(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAssessmentExcelService.ExcelPreview preview =
                fixture.service.preview(file);
        PracticeDraft imported =
                fixture.service.importDraft(file, null, 77L);

        assertThat(preview.canImport())
                .as(preview.issues().toString())
                .isTrue();
        JsonNode previewRoot =
                new ObjectMapper().readTree(preview.draftJson());
        assertThat(findQuestion(previewRoot, "SINGLE_CHOICE")
                .path("questionContent")
                .path("schemaVersion")
                .asText())
                .isEqualTo("question-content-v1");
        assertThat(imported.getDraftJson())
                .contains("\"schemaVersion\":\"question-content-v1\"")
                .doesNotContain(
                        "\"inputType\"",
                        "\"deliveryMode\"",
                        "\"audioOrigin\"");
    }

    @Test
    void historicalV1SpeakingWorkbookWithoutQuestionAudioFailsClosed()
            throws Exception {
        ExcelFixture fixture = fixture();

        PracticeAssessmentExcelService.ExcelPreview preview =
                fixture.service.preview(
                        workbookFile(legacyWorkbook(true)));

        assertThat(preview.canImport()).isFalse();
        assertThat(preview.issues()).anyMatch(issue ->
                "SPEAKING_PROMPT_AUDIO_REQUIRED".equals(issue.code())
                        || "NO_IMPORTABLE_QUESTIONS".equals(issue.code()));
        assertThat(preview.rows()).allMatch(row ->
                !"SPEAKING".equals(row.questionType())
                        || !row.importable());
    }

    @Test
    void previewKeepsInvalidRowsVisibleAndImportDropsThem() throws Exception {
        ExcelFixture fixture = fixture();
        byte[] invalidWorkbook;
        int expectedErrorRows = 3;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fixture.service.buildTemplate()));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int rowIndex : List.of(3, 4, 5)) {
                org.apache.poi.ss.usermodel.Row invalid = workbook.getSheet("03_SINGLE_CHOICE").getRow(rowIndex);
                for (int column = 17; column <= 32; column++) {
                    if (invalid.getCell(column) != null) invalid.getCell(column).setBlank();
                }
            }
            for (int rowIndex = 2;
                 rowIndex <= workbook.getSheet("07_SPEAKING").getLastRowNum();
                 rowIndex++) {
                org.apache.poi.ss.usermodel.Row speaking =
                        workbook.getSheet("07_SPEAKING").getRow(rowIndex);
                if (speaking != null && speaking.getCell(11) != null) {
                    speaking.getCell(11).setBlank();
                    expectedErrorRows++;
                }
            }
            workbook.write(output);
            invalidWorkbook = output.toByteArray();
        }
        MockMultipartFile file = workbookFile(invalidWorkbook);
        when(fixture.repository.saveAndFlush(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file);

        assertThat(preview.canImport()).as(preview.issues().toString()).isTrue();
        assertThat(preview.errorRowCount()).isEqualTo(expectedErrorRows);
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

    @Test
    void linkedImportLocksDraftVerifiesExactDraftAudioAndReconcilesReplacement()
            throws Exception {
        ExcelFixture fixture = fixture();
        LecturerAssetService assets = mock(LecturerAssetService.class);
        SpeakingPromptLifecycleService lifecycle =
                mock(SpeakingPromptLifecycleService.class);
        PracticeAssessmentExcelService service =
                new PracticeAssessmentExcelService(
                        fixture.catalog,
                        fixture.contract,
                        fixture.validator,
                        fixture.repository,
                        fixture.codec,
                        fixture.resolver,
                        fixture.objectMapper,
                        null,
                        assets);
        service.setSpeakingPromptLifecycleService(lifecycle);
        byte[] workbook = fixture.service.buildTemplate();
        String existingJson = fixture.service.preview(
                workbookFile(workbook)).draftJson();
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 77L, existingJson);
        setDraftId(draft, 10L);
        when(fixture.repository.findByIdForUpdate(10L))
                .thenReturn(java.util.Optional.of(draft));
        when(fixture.repository.saveAndFlush(any(PracticeDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assets.requireVerifiedPrivateManualAudioForExcel(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new com.ksh.entities.LecturerAsset());

        PracticeDraft imported = service.importDraft(
                workbookFile(workbook),
                10L,
                77L,
                speakingOverrides());

        assertThat(imported).isSameAs(draft);
        verify(lifecycle).reconcileDraftQuestions(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.contains(
                        "\"questionType\":\"SPEAKING\""));
        verify(assets, org.mockito.Mockito.atLeastOnce())
                .requireVerifiedPrivateManualAudioForExcel(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.eq(77L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.anyString());
        verify(assets, org.mockito.Mockito.atLeastOnce())
                .consumeExcelSpeakingUploadReference(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.eq(77L),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void speakingImportFailsClosedWhenAssetBoundaryIsUnavailable()
            throws Exception {
        ExcelFixture fixture = fixture();
        byte[] workbook = fixture.service.buildTemplate();
        String existingJson = fixture.service.preview(
                workbookFile(workbook)).draftJson();
        PracticeDraft draft = new PracticeDraft(
                "Draft", "", "GLOBAL", null, "DRAFT", 77L, existingJson);
        setDraftId(draft, 10L);
        when(fixture.repository.findByIdForUpdate(10L))
                .thenReturn(java.util.Optional.of(draft));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> fixture.service.importDraft(
                        workbookFile(workbook),
                        10L,
                        77L,
                        speakingOverrides()));
    }

    private static String speakingOverrides() {
        return """
                {
                  "AUD_T01_S_Q01":"/practice/materials/101/content",
                  "AUD_T01_S_Q02":"/practice/materials/102/content",
                  "AUD_T01_S_Q03":"/practice/materials/103/content",
                  "AUD_T01_S_Q04":"/practice/materials/104/content"
                }
                """;
    }

    private static void setDraftId(PracticeDraft draft, Long id) {
        try {
            java.lang.reflect.Field field =
                    PracticeDraft.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(draft, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
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

    private static List<String> writingPoints(JsonNode root) {
        java.util.ArrayList<String> points = new java.util.ArrayList<>();
        for (JsonNode section : root.path("sections")) {
            if (!"WRITING".equals(section.path("skill").asText())) continue;
            for (JsonNode group : section.path("groups")) {
                for (JsonNode question : group.path("questions")) {
                    points.add(question.path("points").decimalValue()
                            .stripTrailingZeros().toPlainString());
                }
            }
        }
        return points;
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
        return new ExcelFixture(
                service,
                repository,
                catalog,
                contract,
                new PracticeDraftValidator(objectMapper),
                codec,
                resolver,
                objectMapper);
    }

    private static MockMultipartFile workbookFile(byte[] bytes) {
        return new MockMultipartFile("file", "assessment-v2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static byte[] legacyWorkbook(boolean speaking)
            throws Exception {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet manifest =
                    workbook.createSheet("Manifest");
            excelRow(manifest, 0, "key", "value");
            excelRow(manifest, 1, "schemaVersion", "practice-excel-v1");
            excelRow(manifest, 2, "title", "Legacy fixture");
            excelRow(manifest, 3, "description", "");

            org.apache.poi.ss.usermodel.Sheet sections =
                    workbook.createSheet("Sections");
            excelRow(sections, 0,
                    "sectionId", "title", "skill", "durationMinutes");
            excelRow(sections, 1,
                    "legacy-section",
                    speaking ? "Phần Nói" : "Phần Đọc",
                    speaking ? "SPEAKING" : "READING",
                    10);

            org.apache.poi.ss.usermodel.Sheet groups =
                    workbook.createSheet("Groups");
            excelRow(groups, 0,
                    "groupId", "sectionId", "label", "instruction",
                    "stimulusType", "passageText", "transcriptText",
                    "audioUrl", "imageUrl");
            excelRow(groups, 1,
                    "legacy-group",
                    "legacy-section",
                    "Nhóm 1",
                    "Đọc và trả lời.",
                    speaking ? "NONE" : "READING_PASSAGE",
                    speaking ? "" : "서울은 한국의 수도입니다.",
                    "",
                    "",
                    "");

            org.apache.poi.ss.usermodel.Sheet questions =
                    workbook.createSheet("Questions");
            excelRow(questions, 0,
                    "questionId", "groupId", "questionNo", "questionType",
                    "prompt", "points", "explanationVi", "essayTaskType",
                    "prepTimeSeconds", "responseTimeSeconds");
            excelRow(questions, 1,
                    "legacy-question",
                    "legacy-group",
                    1,
                    speaking ? "SPEAKING" : "SINGLE_CHOICE",
                    speaking
                            ? "주말에 무엇을 합니까?"
                            : "한국의 수도는 어디입니까?",
                    1,
                    "",
                    "",
                    speaking ? 30 : "",
                    speaking ? 60 : "");

            org.apache.poi.ss.usermodel.Sheet answers =
                    workbook.createSheet("OptionsAnswers");
            excelRow(answers, 0,
                    "questionId", "optionId", "optionText", "isCorrect",
                    "correctValue", "blankId", "blankPrompt",
                    "acceptedValues");
            if (!speaking) {
                excelRow(answers, 1,
                        "legacy-question", "opt_1", "서울", "TRUE",
                        "", "", "", "");
                excelRow(answers, 2,
                        "legacy-question", "opt_2", "부산", "FALSE",
                        "", "", "", "");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void excelRow(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowIndex,
            Object... values) {
        org.apache.poi.ss.usermodel.Row row =
                sheet.createRow(rowIndex);
        for (int column = 0; column < values.length; column++) {
            Object value = values[column];
            org.apache.poi.ss.usermodel.Cell cell =
                    row.createCell(column);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(
                        value == null ? "" : value.toString());
            }
        }
    }

    private record ExcelFixture(PracticeAssessmentExcelService service,
                                PracticeDraftRepository repository,
                                AssessmentAuthoringCatalogService catalog,
                                PracticeDraftContractService contract,
                                PracticeDraftValidator validator,
                                AssessmentContractCodec codec,
                                QuestionTypeResolver resolver,
                                ObjectMapper objectMapper) {
    }
}
