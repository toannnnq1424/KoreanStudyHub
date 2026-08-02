package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateJson;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateNormalizer;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateValidator;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAssessmentExcelServiceTest {

    @Test
    void templateRetainsFiveLegacySheetsAndAddsTwoTypedObjectiveSheets() throws Exception {
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
            assertThat(workbook.getSheet("08_MULTIPLE_ANSWER")).isNotNull();
            assertThat(workbook.getSheet("09_MATCHING")).isNotNull();
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
    void generatedWorkbookPreviewsAllSevenTypesAndWritingTasksQ51ToQ54() throws Exception {
        ExcelFixture fixture = fixture();

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(
                workbookFile(fixture.service.buildTemplate()));

        assertThat(preview.canImport()).as(preview.issues().toString()).isTrue();
        assertThat(preview.rows()).extracting(PracticeAssessmentExcelService.ImportRowPreview::questionType)
                .contains("SINGLE_CHOICE", "MULTIPLE_ANSWER", "MATCHING",
                        "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "ESSAY", "SPEAKING")
                .doesNotContain("MULTIPLE_CHOICE");
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
                .isEqualTo("question-content-v3");
        assertThat(speaking.path("questionContent").path("languageTag").asText())
                .isEqualTo("ko");
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
        JsonNode q51 = findWritingQuestion(root, "Q51");
        JsonNode q52 = findWritingQuestion(root, "Q52");
        assertThat(q51.path("questionContent").path("writingResponse")
                .path("blanks").size()).isEqualTo(2);
        assertThat(q51.path("answerSpec").path("writingBlankAuthority")
                .path("blanks").size()).isEqualTo(2);
        assertThat(q52.path("questionContent").path("writingResponse")
                .path("blanks").size()).isEqualTo(2);
        assertThat(q52.path("answerSpec").path("writingBlankAuthority")
                .path("blanks").size()).isEqualTo(2);
        assertThat(q51.path("answer").path("value").asText())
                .isEqualTo("STRUCTURED_BLANKS");
        assertThat(q52.path("answer").path("value").asText())
                .isEqualTo("STRUCTURED_BLANKS");
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
    void historicalV1WorkbookReaderCanonicalizesToCurrentLanguageContract()
            throws Exception {
        ExcelFixture fixture = fixture();
        MockMultipartFile file = workbookFile(
                legacyWorkbook(false));

        PracticeAssessmentExcelService.ExcelPreview preview =
                fixture.service.preview(file);

        assertThat(preview.canImport())
                .as(preview.issues().toString())
                .isTrue();
        JsonNode previewRoot =
                new ObjectMapper().readTree(preview.draftJson());
        assertThat(findQuestion(previewRoot, "SINGLE_CHOICE")
                .path("questionContent")
                .path("schemaVersion")
                .asText())
                .isEqualTo("question-content-v3");
        assertThat(preview.draftJson())
                .contains(
                        "\"schemaVersion\":\"question-content-v3\"",
                        "\"languageTag\":\"ko\"")
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

        PracticeAssessmentExcelService.ExcelPreview preview = fixture.service.preview(file);

        assertThat(preview.canImport()).as(preview.issues().toString()).isTrue();
        assertThat(preview.errorRowCount()).isEqualTo(expectedErrorRows);
        assertThat(preview.rows()).anyMatch(row -> !row.importable() && "ERROR".equals(row.status()));
        assertThat(preview.rows().stream().filter(PracticeAssessmentExcelService.ImportRowPreview::importable)
                .map(PracticeAssessmentExcelService.ImportRowPreview::importedQuestionNo))
                .doesNotContainNull();

        JsonNode importableRoot = new ObjectMapper().readTree(preview.draftJson());
        assertThat(countQuestions(importableRoot))
                .isEqualTo(preview.importableQuestionCount());
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
    void excelImporterHasNoAlternateDraftMutationSeam() throws Exception {
        String source = String.join("\n",
                java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/ksh/features/practice/manage/"
                                + "controller/PracticeAssessmentExcelController.java")),
                java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/ksh/features/practice/manage/service/"
                                + "PracticeAssessmentExcelService.java")),
                java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/ksh/features/practice/manage/service/"
                                + "PracticeAssessmentExcelV2Codec.java")),
                java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/ksh/features/practice/manage/service/"
                                + "PracticeAssessmentQuickExcelCodec.java")));

        assertThat(source)
                .contains("candidateService.createOrReuse")
                .doesNotContain(
                        "importDraft(",
                        "setDraftJson(",
                        "saveAndFlush(",
                        "mergeImportedLessons(",
                        "reconcileDraftQuestions(",
                        "consumeExcelSpeakingUploadReference(",
                        "linkExcelManagedUploadToDraft(",
                        "com.ksh.features.ai.client",
                        "AiClient");
    }

    @Test
    void advancedV2CandidateAdapterPreservesExactTargetTypedMeaning()
            throws Exception {
        PracticeAuthoringCandidateService candidates =
                mock(PracticeAuthoringCandidateService.class);
        when(candidates.createOrReuse(any(CreateCommand.class)))
                .thenReturn(candidateView());
        ExcelFixture fixture = fixture(candidates);
        byte[] workbook = fixture.service.buildTemplate();
        PracticeAssessmentExcelService.ExcelPreview golden =
                fixture.service.preview(workbookFile(workbook));
        JsonNode expectedSection = exactSection(
                fixture.objectMapper.readTree(golden.draftJson()),
                1, "LISTENING", "L1");

        fixture.service.createCandidate(
                workbookFile(workbook),
                targetContext(fixture, "LISTENING"),
                77L,
                null);

        ArgumentCaptor<CreateCommand> command =
                ArgumentCaptor.forClass(CreateCommand.class);
        verify(candidates).createOrReuse(command.capture());
        assertThat(command.getValue().source().kind())
                .isEqualTo(SourceKind.ADVANCED_EXCEL_V2);
        assertThat(command.getValue().source().contractVersion())
                .isEqualTo("practice-excel-v2");
        assertExactTargetParity(expectedSection, command.getValue().groups());
        assertThat(questionTypesIn(command.getValue().groups()))
                .contains("MATCHING");
        assertCanonicalCandidateGroups(fixture, command.getValue());
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void legacyV1CandidateAdapterPreservesGoldenTypedMeaning()
            throws Exception {
        PracticeAuthoringCandidateService candidates =
                mock(PracticeAuthoringCandidateService.class);
        when(candidates.createOrReuse(any(CreateCommand.class)))
                .thenReturn(candidateView());
        ExcelFixture fixture = fixture(candidates);
        byte[] workbook = legacyWorkbook(false);
        PracticeAssessmentExcelService.ExcelPreview golden =
                fixture.service.preview(workbookFile(workbook));
        JsonNode expectedSection = exactSection(
                fixture.objectMapper.readTree(golden.draftJson()),
                1, "READING", "R1");

        fixture.service.createCandidate(
                workbookFile(workbook),
                targetContext(fixture, "READING"),
                77L,
                null);

        ArgumentCaptor<CreateCommand> command =
                ArgumentCaptor.forClass(CreateCommand.class);
        verify(candidates).createOrReuse(command.capture());
        assertThat(command.getValue().source().kind())
                .isEqualTo(SourceKind.LEGACY_EXCEL_V1);
        assertThat(command.getValue().source().contractVersion())
                .isEqualTo("practice-excel-v1");
        assertExactTargetParity(expectedSection, command.getValue().groups());
        assertThat(questionTypesIn(command.getValue().groups()))
                .containsExactly("SINGLE_CHOICE");
        assertCanonicalCandidateGroups(fixture, command.getValue());
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void advancedSpeakingVerifiesExistingUploadsWithoutBindingOrConsuming()
            throws Exception {
        PracticeAuthoringCandidateService candidates =
                mock(PracticeAuthoringCandidateService.class);
        LecturerAssetService assets = mock(LecturerAssetService.class);
        when(candidates.createOrReuse(any(CreateCommand.class)))
                .thenReturn(candidateView());
        ExcelFixture fixture = fixture(candidates, assets);
        String overrides = """
                {
                  "AUD_T01_S_Q01":"/practice/materials/101/content",
                  "AUD_T01_S_Q02":"/practice/materials/102/content",
                  "AUD_T01_S_Q03":"/practice/materials/103/content",
                  "AUD_T01_S_Q04":"/practice/materials/104/content"
                }
                """;

        fixture.service.createCandidate(
                workbookFile(fixture.service.buildTemplate()),
                targetContext(fixture, "SPEAKING"),
                77L,
                overrides);

        verify(assets, times(4))
                .requireVerifiedPrivateManualAudioForExcel(
                        anyLong(), eq(77L), eq(5001L), anyString());
        verify(assets, never()).consumeExcelSpeakingUploadReference(
                anyLong(), anyLong(), anyLong(), anyString());
        verify(assets, never()).linkExcelManagedUploadToDraft(
                anyLong(), anyLong(), anyLong());
        verify(candidates).createOrReuse(any(CreateCommand.class));
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    private static void assertCanonicalCandidateGroups(
            ExcelFixture fixture, CreateCommand command) {
        PracticeAuthoringCandidateJson candidateJson =
                new PracticeAuthoringCandidateJson(fixture.objectMapper);
        PracticeAuthoringCandidateNormalizer normalizer =
                new PracticeAuthoringCandidateNormalizer(
                        fixture.objectMapper,
                        fixture.codec,
                        fixture.resolver,
                        candidateJson);
        PracticeAuthoringCandidateValidator validator =
                new PracticeAuthoringCandidateValidator(
                        fixture.codec,
                        fixture.resolver,
                        candidateJson);
        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        "11111111-1111-4111-8111-111111111111",
                        command.source().kind(),
                        command.groups());
        PracticeAuthoringCandidateValidator.ValidationResult validated =
                validator.validate(
                        command.source().kind(),
                        new TargetRoute(
                                command.target().draftId(),
                                command.target().testNo(),
                                command.target().skill(),
                                command.target().lessonCode()),
                        normalized.groups(),
                        normalized.issues());

        assertThat(normalized.issues()).isEmpty();
        assertThat(validated.issues())
                .extracting(issue -> issue.code())
                .doesNotContain(
                        "CANDIDATE_RAW_TYPED_CONTRACT_INVALID",
                        "CANDIDATE_TYPED_CONTRACT_INVALID",
                        "CANDIDATE_SCHEMA_FIELD_UNKNOWN",
                        "CANDIDATE_TARGET_IDENTITY_MISMATCH",
                        "QUESTION_TYPE_NOT_ALLOWED_FOR_SKILL");
    }

    private static void assertExactTargetParity(
            JsonNode expectedSection, JsonNode candidateGroups) {
        assertThat(candidateGroups).hasSize(
                expectedSection.path("groups").size());
        for (int groupIndex = 0;
             groupIndex < expectedSection.path("groups").size();
             groupIndex++) {
            JsonNode expectedGroup =
                    expectedSection.path("groups").get(groupIndex);
            JsonNode actualGroup = candidateGroups.get(groupIndex);
            assertThat(actualGroup.path("label").asText())
                    .isEqualTo(expectedGroup.path("label").asText());
            assertThat(actualGroup.path("instruction").asText())
                    .isEqualTo(expectedGroup.path("instruction").asText());
            assertThat(actualGroup.path("stimulus").path("type").asText())
                    .isEqualTo(expectedGroup.path("stimulus")
                            .path("type").asText());
            assertThat(actualGroup.path("stimulus")
                    .path("passageText").asText(""))
                    .isEqualTo(expectedGroup.path("stimulus")
                            .path("passageText").asText(""));
            assertThat(actualGroup.path("stimulus")
                    .path("transcriptText").asText(""))
                    .isEqualTo(expectedGroup.path("stimulus")
                            .path("transcriptText").asText(""));
            assertThat(actualGroup.path("stimulus")
                    .path("mediaReference"))
                    .isEqualTo(expectedGroup.path("stimulus")
                            .path("mediaReference"));
            assertThat(actualGroup.path("questions")).hasSize(
                    expectedGroup.path("questions").size());
            for (int questionIndex = 0;
                 questionIndex < expectedGroup.path("questions").size();
                 questionIndex++) {
                JsonNode expected = expectedGroup.path("questions")
                        .get(questionIndex);
                JsonNode actual = actualGroup.path("questions")
                        .get(questionIndex);
                assertThat(actual.path("questionType"))
                        .isEqualTo(expected.path("questionType"));
                assertThat(actual.path("essayTaskType"))
                        .isEqualTo(expected.path("essayTaskType"));
                assertThat(actual.path("prompt"))
                        .isEqualTo(expected.path("prompt"));
                assertThat(actual.path("points").decimalValue())
                        .isEqualByComparingTo(
                                expected.path("points").decimalValue());
                assertThat(actual.path("questionContent"))
                        .isEqualTo(expected.path("questionContent"));
                assertThat(actual.path("answerSpec"))
                        .isEqualTo(expected.path("answerSpec"));
                assertThat(actual.path("reviewState").asText())
                        .isEqualTo(expected.path("reviewRequired")
                                .asBoolean(false)
                                ? "REVIEW_REQUIRED" : "ACCEPTED");
            }
        }
    }

    private static List<String> questionTypesIn(JsonNode groups) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
        groups.forEach(group -> group.path("questions").forEach(question ->
                types.add(question.path("questionType").asText())));
        return types;
    }

    private static JsonNode exactSection(
            JsonNode root,
            int testNo,
            String skill,
            String lessonCode) {
        for (JsonNode section : root.path("sections")) {
            if (section.path("testNo").asInt() == testNo
                    && skill.equals(section.path("skill").asText())
                    && lessonCode.equals(
                    section.path("lessonCode").asText())) {
                return section;
            }
        }
        throw new AssertionError("Không tìm thấy exact target section");
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

    private static JsonNode findWritingQuestion(
            JsonNode root,
            String writingTask) {
        for (JsonNode section : root.path("sections")) {
            for (JsonNode group : section.path("groups")) {
                for (JsonNode question : group.path("questions")) {
                    if (writingTask.equals(
                            question.path("essayTaskType").asText())) {
                        return question;
                    }
                }
            }
        }
        throw new AssertionError(
                "Không tìm thấy Writing " + writingTask);
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
        return fixture(null);
    }

    private static ExcelFixture fixture(
            PracticeAuthoringCandidateService candidateService) {
        return fixture(candidateService, null);
    }

    private static ExcelFixture fixture(
            PracticeAuthoringCandidateService candidateService,
            LecturerAssetService assetService) {
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
                codec, resolver, objectMapper, null, assetService, candidateService);
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

    private static PracticeAssessmentExcelService.ExcelImportContext
    targetContext(ExcelFixture fixture, String skill) {
        String prefix = switch (skill) {
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "R";
        };
        ObjectNode root = fixture.objectMapper.createObjectNode();
        root.put("schemaVersion", "practice-draft-v3");
        ObjectNode document = root.putObject("document");
        document.put("title", "Target");
        document.put("description", "");
        ObjectNode test = root.putArray("tests").addObject();
        test.put("clientId", "test-1");
        test.put("testNo", 1);
        test.put("title", "Test 1");
        test.put("description", "");
        test.putNull("estimatedMinutes");
        ObjectNode section = root.putArray("sections").addObject();
        section.put("clientId", "section-" + prefix.toLowerCase());
        section.put("testNo", 1);
        section.put("testClientId", "test-1");
        section.put("lessonCode", prefix + "1");
        section.put("title", skill);
        section.put("skill", skill);
        section.put("durationMinutes", 40);
        section.putArray("groups");
        root.putArray("materials");
        root.putArray("warnings");
        String normalized = fixture.contract.normalize(root, "TEST").json();
        PracticeDraft draft = new PracticeDraft(
                "Target", "", "GLOBAL", null, "DRAFT", 77L, normalized);
        setId(draft, 5001L);
        return new PracticeAssessmentExcelService.ExcelImportContext(
                draft, 1, prefix + "1", skill);
    }

    private static CandidateView candidateView() {
        return new CandidateView(
                "11111111-1111-4111-8111-111111111111",
                CandidateState.REVIEWING,
                0,
                "sha256:" + "a".repeat(64),
                new ObjectMapper().createObjectNode(),
                List.of());
    }

    private static void setId(PracticeDraft draft, Long id) {
        try {
            java.lang.reflect.Field field =
                    PracticeDraft.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(draft, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
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
