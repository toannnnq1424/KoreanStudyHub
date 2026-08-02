package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAssessmentQuickExcelCodecTest {

    @Test
    void quickTemplateIsExactOneSheetIdentityAndTwentyFourHeaders()
            throws Exception {
        Fixture fixture = fixture(null);

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(fixture.service.buildQuickTemplate()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo("QUICK_QUESTIONS");
            assertThat(workbook.isSheetHidden(0)).isFalse();
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0)
                    .getStringCellValue())
                    .isEqualTo("KSH_PRACTICE_QUICK_EXCEL");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1)
                    .getStringCellValue())
                    .isEqualTo("practice-quick-excel-v1");
            assertThat(headerValues(workbook.getSheetAt(0)))
                    .containsExactlyElementsOf(
                            PracticeAssessmentQuickExcelCodec.headers());
        }

        try (Workbook advanced = WorkbookFactory.create(
                new ByteArrayInputStream(fixture.service.buildTemplate()))) {
            assertThat(advanced.getSheet("01_THONG_TIN_SET")).isNotNull();
            assertThat(advanced.getSheet("QUICK_QUESTIONS")).isNull();
        }
    }

    @Test
    void malformedQuickIdentityFailsQuickWithoutLegacyFallthrough()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] malformed = mutateQuick(fixture, workbook -> workbook
                .getSheet("QUICK_QUESTIONS").getRow(0).getCell(1)
                .setCellValue("practice-quick-excel-v0"));

        assertCode(
                () -> fixture.service.preview(
                        workbookFile(malformed),
                        targetContext(fixture, "READING", false)),
                "QUICK_SENTINEL_INVALID");
    }

    @Test
    void unsupportedWorkbookUsesStableSchemaErrorInsteadOfLegacyGuess()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Questions");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        assertCode(
                () -> fixture.service.preview(workbookFile(bytes)),
                "WORKBOOK_SCHEMA_UNSUPPORTED");
    }

    @Test
    void quickBlocksExtraOrHiddenSheetsMergedCellsFormulasAndExtraColumns()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] extraSheet = mutateQuick(fixture,
                workbook -> workbook.createSheet("HIDDEN_DATA"));
        assertCode(() -> previewReading(fixture, extraSheet),
                "QUICK_SHEET_COUNT_INVALID");

        byte[] hiddenSheet = mutateQuick(fixture, workbook -> {
            workbook.createSheet("HIDDEN_DATA");
            workbook.setSheetHidden(1, true);
        });
        assertCode(() -> previewReading(fixture, hiddenSheet),
                "QUICK_SHEET_COUNT_INVALID");

        byte[] merged = mutateQuick(fixture, workbook -> workbook
                .getSheet("QUICK_QUESTIONS").addMergedRegion(
                        new org.apache.poi.ss.util.CellRangeAddress(
                                3, 3, 0, 1)));
        assertCode(() -> previewReading(fixture, merged),
                "QUICK_MERGED_CELLS_NOT_ALLOWED");

        byte[] formula = readingWorkbook(fixture);
        formula = mutate(formula, workbook -> workbook
                .getSheet("QUICK_QUESTIONS").getRow(3).getCell(8)
                .setCellFormula("1+1"));
        byte[] finalFormula = formula;
        assertCode(() -> previewReading(fixture, finalFormula),
                "QUICK_FORMULA_NOT_ALLOWED");

        byte[] extraColumn = mutateQuick(fixture, workbook -> workbook
                .getSheet("QUICK_QUESTIONS").getRow(2)
                .createCell(24).setCellValue("unexpected_column"));
        assertCode(() -> previewReading(fixture, extraColumn),
                "QUICK_COLUMN_UNSUPPORTED");
    }

    @Test
    void quickBlocksMacrosAndExternalLinksAtPackageBoundary()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] source = readingWorkbook(fixture);

        byte[] macro = addZipEntry(
                source, "xl/vbaProject.bin", new byte[]{1, 2, 3});
        assertCode(() -> previewReading(fixture, macro),
                "QUICK_MACRO_NOT_ALLOWED");

        byte[] external = addZipEntry(
                source,
                "xl/externalLinks/externalLink1.xml",
                "<externalLink/>".getBytes(StandardCharsets.UTF_8));
        assertCode(() -> previewReading(fixture, external),
                "QUICK_EXTERNAL_LINK_NOT_ALLOWED");
    }

    @Test
    void readingMapsAllFourSimpleObjectiveTypesToCanonicalCandidate()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] bytes = quickWorkbook(fixture, sheet -> {
            quickRow(sheet, 3, "reading_group", "Đọc hiểu", "Đọc và trả lời.",
                    "민수는 토요일에 도서관에 갑니다.", "reading_q1", 1,
                    "SINGLE_CHOICE", "", "민수는 어디에 갑니까?",
                    List.of("도서관", "학교", "회사", "공원"),
                    "A", "", "", "1", "Bằng chứng trực tiếp.", "", "");
            quickRow(sheet, 4, "reading_group", "", "", "", "reading_q2", 2,
                    "MULTIPLE_ANSWER", "", "맞는 것을 두 개 고르십시오.",
                    List.of("토요일", "도서관", "월요일", "회사"),
                    "A|B", "", "", "", "", "", "");
            quickRow(sheet, 5, "reading_group", "", "", "", "reading_q3", 3,
                    "TRUE_FALSE_NOT_GIVEN", "", "민수는 토요일에 갑니다.",
                    List.of(), "TRUE", "", "", "", "", "", "");
            quickRow(sheet, 6, "reading_group", "", "", "", "reading_q4", 4,
                    "FILL_BLANK", "", "민수는 {{blank:blank_1}}에 갑니다.",
                    List.of(), "", "도서관|도서실", "", "", "", "", "");
        });

        PracticeAssessmentQuickExcelCodec.QuickParseResult parsed =
                parseQuick(fixture, bytes, "READING");
        JsonNode questions = parsed.groups().get(0).path("questions");

        assertThat(questionTypes(questions)).containsExactly(
                "SINGLE_CHOICE", "MULTIPLE_ANSWER",
                "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK");
        assertThat(questions.get(0).path("answerSpec")
                .path("correctOptionIds").get(0).asText()).isEqualTo("opt_A");
        assertThat(questions.get(1).path("answerSpec")
                .path("correctOptionIds")).hasSize(2);
        assertThat(questions.get(2).path("answerSpec")
                .path("correctValue").asText()).isEqualTo("TRUE");
        assertThat(questions.get(3).path("questionContent")
                .path("blanks").get(0).path("id").asText())
                .isEqualTo("blank_1");
        assertThat(questions.get(3).path("answerSpec")
                .path("blanks").get(0).path("acceptedValues"))
                .extracting(JsonNode::asText)
                .containsExactly("도서관", "도서실");
        assertThat(questions)
                .allMatch(question -> "REVIEW_REQUIRED".equals(
                        question.path("reviewState").asText()));

        JsonNode listeningQuestions =
                parseQuick(fixture, bytes, "LISTENING")
                        .groups().get(0).path("questions");
        assertThat(questionTypes(listeningQuestions)).containsExactly(
                "SINGLE_CHOICE", "MULTIPLE_ANSWER",
                "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK");
    }

    @Test
    void listeningRequiresExistingCheckAudioAuthority()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] listening = quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "listening_group", "Nghe", "Nghe và trả lời.",
                "회의는 세 시에 시작합니다.", "listening_q1", 1,
                "TRUE_FALSE_NOT_GIVEN", "", "회의는 세 시에 시작합니다.",
                List.of(), "TRUE", "", "", "", "", "", ""));

        assertCode(() -> fixture.service.preview(
                        workbookFile(listening),
                        targetContext(fixture, "LISTENING", false)),
                "LISTENING_CHECK_AUDIO_REQUIRED");

        PracticeAssessmentExcelService.ExcelPreview preview =
                fixture.service.preview(
                        workbookFile(listening),
                        targetContext(fixture, "LISTENING", true));
        assertThat(preview.canImport()).isTrue();
        assertThat(preview.importableQuestionCount()).isEqualTo(1);
    }

    @Test
    void writingMapsExactQ51ToQ54TypedAuthorityAndFixedPoints()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] writing = writingWorkbook(fixture);

        PracticeAssessmentQuickExcelCodec.QuickParseResult parsed =
                parseQuick(fixture, writing, "WRITING");
        JsonNode questions = parsed.groups().get(0).path("questions");

        assertThat(essayTasks(questions))
                .containsExactly("Q51", "Q52", "Q53", "Q54");
        assertThat(questionPoints(questions))
                .containsExactly("10", "10", "30", "50");
        assertThat(questions.get(0).path("questionContent")
                .path("writingResponse").path("blanks")).hasSize(2);
        assertThat(questions.get(0).path("questionContent")
                .path("writingResponse").path("blanks").get(0)
                .path("blankId").asText()).isEqualTo("q51-b1");
        assertThat(questions.get(1).path("answerSpec")
                .path("writingBlankAuthority").path("blanks")).hasSize(2);
        assertThat(questions.get(1).path("answerSpec")
                .path("writingBlankAuthority").path("blanks").get(1)
                .path("blankId").asText()).isEqualTo("q52-b2");
        assertThat(questions.get(2).path("answerSpec")
                .has("writingBlankAuthority")).isFalse();
        assertThat(questions.get(3).path("answerSpec")
                .has("writingBlankAuthority")).isFalse();
        assertThat(fixture.service.preview(
                workbookFile(writing),
                targetContext(fixture, "WRITING", false)).canImport())
                .isTrue();
    }

    @Test
    void writingSimulationRejectsMissingOrDuplicateTaskAgainstTarget()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] missing = quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "writing_group", "Viết", "Hoàn thành.", "",
                "writing_q53", 1, "ESSAY", "Q53",
                "자료를 설명하십시오.", List.of(), "", "", "", "30",
                "", "", ""));

        assertCode(() -> fixture.service.preview(
                        workbookFile(missing),
                        targetContext(fixture, "WRITING", false)),
                "WRITING_TASK_CARDINALITY_INVALID");
    }

    @Test
    void speakingMapsOnlyManualTextTextOnlyNoneWithBoundedTiming()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] speaking = quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "speaking_group", "Nói", "Trả lời bằng tiếng Hàn.",
                "", "speaking_q1", 1, "SPEAKING", "",
                "주말에 보통 무엇을 합니까?", List.of(), "", "", "", "",
                "", "45", "90"));

        JsonNode question = parseQuick(fixture, speaking, "SPEAKING")
                .groups().get(0).path("questions").get(0);
        JsonNode delivery = question.path("questionContent")
                .path("speakingDelivery");

        assertThat(question.path("points").decimalValue())
                .isEqualByComparingTo("100");
        assertThat(delivery.path("inputType").asText())
                .isEqualTo("manual_text");
        assertThat(delivery.path("deliveryMode").asText())
                .isEqualTo("text_only");
        assertThat(delivery.path("audioOrigin").asText()).isEqualTo("none");
        assertThat(delivery.path("promptAudioReference").isNull()).isTrue();
        assertThat(delivery.path("promptPlayLimit").isNull()).isTrue();
        assertThat(delivery.path("preparationSeconds").asInt()).isEqualTo(45);
        assertThat(delivery.path("responseSeconds").asInt()).isEqualTo(90);
    }

    @Test
    void matchingMediaAndComplexBlankRouteToAdvancedWithoutLossyConversion()
            throws Exception {
        Fixture fixture = fixture(null);
        byte[] matching = quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "group", "Nhóm", "", "글", "q1", 1,
                "MATCHING", "", "연결하십시오.", List.of("A", "B"),
                "A", "", "", "", "", "", ""));
        assertCode(() -> previewReading(fixture, matching),
                "ADVANCED_AUTHORING_REQUIRED");

        byte[] complexBlank = quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "group", "Nhóm", "", "글", "q1", 1,
                "FILL_BLANK", "",
                "{{blank:blank_1}} {{blank:blank_2}}",
                List.of(), "", "하나", "둘", "", "", "", ""));
        assertCode(() -> previewReading(fixture, complexBlank),
                "ADVANCED_AUTHORING_REQUIRED");

        byte[] mediaHeader = mutateQuick(fixture, workbook -> workbook
                .getSheet("QUICK_QUESTIONS").getRow(2)
                .createCell(24).setCellValue("audio_reference"));
        assertCode(() -> previewReading(fixture, mediaHeader),
                "ADVANCED_AUTHORING_REQUIRED");

        byte[] mediaValue = mutate(readingWorkbook(fixture), workbook ->
                workbook.getSheet("QUICK_QUESTIONS").getRow(3).getCell(3)
                        .setCellValue("https://cdn.example.test/listening.mp3"));
        assertCode(() -> previewReading(fixture, mediaValue),
                "ADVANCED_AUTHORING_REQUIRED");
    }

    @Test
    void routeSkillCannotBeOverriddenAndCandidateCommandUsesExactRoute()
            throws Exception {
        PracticeAuthoringCandidateService candidates =
                mock(PracticeAuthoringCandidateService.class);
        Fixture fixture = fixture(candidates);
        CandidateView returned = candidateView();
        when(candidates.createOrReuse(any(CreateCommand.class)))
                .thenReturn(returned);
        PracticeAssessmentExcelService.ExcelImportContext context =
                targetContext(fixture, "READING", false);

        CandidateView actual = fixture.service.createCandidate(
                workbookFile(readingWorkbook(fixture)),
                context,
                77L,
                null);

        assertThat(actual).isSameAs(returned);
        ArgumentCaptor<CreateCommand> command =
                ArgumentCaptor.forClass(CreateCommand.class);
        verify(candidates).createOrReuse(command.capture());
        assertThat(command.getValue().source().kind())
                .isEqualTo(SourceKind.QUICK_EXCEL);
        assertThat(command.getValue().source().contractVersion())
                .isEqualTo("practice-quick-excel-v1");
        assertThat(command.getValue().target().draftId()).isEqualTo(5001L);
        assertThat(command.getValue().target().testNo()).isEqualTo(1);
        assertThat(command.getValue().target().skill()).isEqualTo("READING");
        assertThat(command.getValue().target().lessonCode()).isEqualTo("R1");
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void requireContextRejectsRouteSkillMismatchEvenWhenTestAndLessonExist()
            throws Exception {
        Fixture fixture = fixture(null);
        PracticeAssessmentExcelService.ExcelImportContext valid =
                targetContext(fixture, "READING", false);
        when(fixture.repository.findByIdAndOwnerId(5001L, 77L))
                .thenReturn(java.util.Optional.of(valid.draft()));

        assertCode(() -> fixture.service.requireExcelImportContext(
                        5001L, 77L, 1, "LISTENING", "R1"),
                "CANDIDATE_TARGET_IDENTITY_MISMATCH");
    }

    @Test
    void everyQuickSkillShapeReachesTheCanonicalCandidateContract()
            throws Exception {
        Fixture fixture = fixture(null);
        assertCanonicalQuickShape(
                fixture,
                parseQuick(fixture, readingWorkbook(fixture), "READING").groups(),
                "READING",
                "R1");
        assertCanonicalQuickShape(
                fixture,
                parseQuick(fixture, readingWorkbook(fixture), "LISTENING").groups(),
                "LISTENING",
                "L1");
        assertCanonicalQuickShape(
                fixture,
                parseQuick(fixture, writingWorkbook(fixture), "WRITING").groups(),
                "WRITING",
                "W1");
        byte[] speaking = quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "speaking_group", "Nói", "Trả lời.", "",
                "speaking_q1", 1, "SPEAKING", "",
                "한국에서 무엇을 공부합니까?", List.of(), "", "", "", "",
                "", "", ""));
        assertCanonicalQuickShape(
                fixture,
                parseQuick(fixture, speaking, "SPEAKING").groups(),
                "SPEAKING",
                "S1");
    }

    private static PracticeAssessmentExcelService.ExcelPreview previewReading(
            Fixture fixture, byte[] bytes) {
        return fixture.service.preview(
                workbookFile(bytes),
                targetContext(fixture, "READING", false));
    }

    private static void assertCanonicalQuickShape(
            Fixture fixture,
            JsonNode groups,
            String skill,
            String lessonCode) {
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(
                fixture.objectMapper, resolver);
        PracticeAuthoringCandidateJson candidateJson =
                new PracticeAuthoringCandidateJson(fixture.objectMapper);
        PracticeAuthoringCandidateNormalizer normalizer =
                new PracticeAuthoringCandidateNormalizer(
                        fixture.objectMapper, codec, resolver, candidateJson);
        PracticeAuthoringCandidateValidator validator =
                new PracticeAuthoringCandidateValidator(
                        codec, resolver, candidateJson);
        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        "11111111-1111-4111-8111-111111111111",
                        SourceKind.QUICK_EXCEL,
                        groups);
        PracticeAuthoringCandidateValidator.ValidationResult validated =
                validator.validate(
                        SourceKind.QUICK_EXCEL,
                        new TargetRoute(5001L, 1, skill, lessonCode),
                        normalized.groups(),
                        normalized.issues());

        assertThat(normalized.issues()).isEmpty();
        assertThat(validated.issues())
                .extracting(issue -> issue.code())
                .doesNotContain(
                        "CANDIDATE_TYPED_CONTRACT_INVALID",
                        "CANDIDATE_SCHEMA_FIELD_UNKNOWN",
                        "CANDIDATE_TARGET_IDENTITY_MISMATCH",
                        "QUESTION_TYPE_NOT_SUPPORTED_BY_QUICK",
                        "ADVANCED_AUTHORING_REQUIRED");
    }

    private static PracticeAssessmentQuickExcelCodec.QuickParseResult parseQuick(
            Fixture fixture, byte[] bytes, String skill) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes))) {
            return fixture.quickCodec.parse(
                    workbook,
                    skill,
                    new PracticeAssessmentQuickExcelCodec.PackageInspection(
                            false, false));
        }
    }

    private static List<String> headerValues(Sheet sheet) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            values.add(sheet.getRow(2).getCell(index).getStringCellValue());
        }
        return values;
    }

    private static List<String> questionTypes(JsonNode questions) {
        List<String> values = new ArrayList<>();
        questions.forEach(value -> values.add(
                value.path("questionType").asText()));
        return values;
    }

    private static List<String> essayTasks(JsonNode questions) {
        List<String> values = new ArrayList<>();
        questions.forEach(value -> values.add(
                value.path("essayTaskType").asText()));
        return values;
    }

    private static List<String> questionPoints(JsonNode questions) {
        List<String> values = new ArrayList<>();
        questions.forEach(value -> values.add(
                value.path("points").decimalValue().stripTrailingZeros()
                        .toPlainString()));
        return values;
    }

    private static byte[] readingWorkbook(Fixture fixture) throws Exception {
        return quickWorkbook(fixture, sheet -> quickRow(
                sheet, 3, "reading_group", "Đọc", "Đọc và trả lời.",
                "민수는 학교에 갑니다.", "reading_q1", 1,
                "SINGLE_CHOICE", "", "민수는 어디에 갑니까?",
                List.of("학교", "회사"), "A", "", "", "", "", "", ""));
    }

    private static byte[] writingWorkbook(Fixture fixture) throws Exception {
        return quickWorkbook(fixture, sheet -> {
            quickRow(sheet, 3, "writing_group", "Viết Q51-Q54", "Hoàn thành.",
                    "", "writing_q51", 1, "ESSAY", "Q51",
                    "안내문을 읽고 두 표현을 완성하십시오.", List.of(), "",
                    "안녕하세요|안녕하십니까", "연락해 주세요", "", "", "", "");
            quickRow(sheet, 4, "writing_group", "", "", "",
                    "writing_q52", 2, "ESSAY", "Q52",
                    "대화의 흐름에 맞게 두 표현을 완성하십시오.", List.of(), "",
                    "왜 늦었어요", "같이 가요", "10", "", "", "");
            quickRow(sheet, 5, "writing_group", "", "", "",
                    "writing_q53", 3, "ESSAY", "Q53",
                    "자료를 설명하는 글을 쓰십시오.", List.of(), "",
                    "", "", "30", "", "", "");
            quickRow(sheet, 6, "writing_group", "", "", "",
                    "writing_q54", 4, "ESSAY", "Q54",
                    "환경 보호에 대한 글을 쓰십시오.", List.of(), "",
                    "", "", "50", "", "", "");
        });
    }

    private static byte[] quickWorkbook(
            Fixture fixture, WorkbookMutation mutation) throws Exception {
        return mutate(fixture.service.buildQuickTemplate(), workbook ->
                mutation.accept(workbook.getSheet("QUICK_QUESTIONS")));
    }

    private static byte[] mutateQuick(
            Fixture fixture, WorkbookConsumer mutation) throws Exception {
        return mutate(fixture.service.buildQuickTemplate(), mutation);
    }

    private static byte[] mutate(
            byte[] source, WorkbookConsumer mutation) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            mutation.accept(workbook);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void quickRow(
            Sheet sheet,
            int rowIndex,
            String groupKey,
            String groupLabel,
            String groupInstruction,
            String stimulus,
            String questionKey,
            int questionOrder,
            String questionType,
            String writingTask,
            String prompt,
            List<String> options,
            String correctAnswer,
            String blank1,
            String blank2,
            String points,
            String explanation,
            String preparation,
            String response) {
        Row row = sheet.createRow(rowIndex);
        Object[] values = new Object[24];
        values[0] = groupKey;
        values[1] = groupLabel;
        values[2] = groupInstruction;
        values[3] = stimulus;
        values[4] = questionKey;
        values[5] = questionOrder;
        values[6] = questionType;
        values[7] = writingTask;
        values[8] = prompt;
        for (int index = 0; index < options.size(); index++) {
            values[9 + index] = options.get(index);
        }
        values[17] = correctAnswer;
        values[18] = blank1;
        values[19] = blank2;
        values[20] = points;
        values[21] = explanation;
        values[22] = preparation;
        values[23] = response;
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) {
                row.createCell(index).setCellValue(number.doubleValue());
            } else if (value != null) {
                row.createCell(index).setCellValue(value.toString());
            }
        }
    }

    private static PracticeAssessmentExcelService.ExcelImportContext
    targetContext(Fixture fixture, String skill, boolean listeningAuthority) {
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
        ArrayNode tests = root.putArray("tests");
        ObjectNode test = tests.addObject();
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
        if ("LISTENING".equals(skill) && listeningAuthority) {
            ObjectNode delivery = section.putObject("sectionDelivery");
            delivery.put("schemaVersion", "practice-section-delivery-v1");
            delivery.putObject("listeningDelivery")
                    .put("checkAudioReference", "/practice/materials/12/content");
        }
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
        ObjectMapper mapper = new ObjectMapper();
        return new CandidateView(
                "11111111-1111-4111-8111-111111111111",
                CandidateState.REVIEWING,
                0,
                "sha256:" + "a".repeat(64),
                mapper.createObjectNode(),
                List.of());
    }

    private static Fixture fixture(
            PracticeAuthoringCandidateService candidateService) {
        ObjectMapper objectMapper = new ObjectMapper();
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(new PracticeContentRules());
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService contract = new PracticeDraftContractService(
                objectMapper, catalog, resolver, codec);
        PracticeDraftRepository repository = mock(PracticeDraftRepository.class);
        PracticeAssessmentExcelService service =
                new PracticeAssessmentExcelService(
                        catalog,
                        contract,
                        new PracticeDraftValidator(objectMapper),
                        repository,
                        codec,
                        resolver,
                        objectMapper,
                        null,
                        null,
                        candidateService);
        return new Fixture(
                service,
                new PracticeAssessmentQuickExcelCodec(objectMapper, catalog),
                repository,
                contract,
                objectMapper);
    }

    private static MockMultipartFile workbookFile(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "quick.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
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

    private static byte[] addZipEntry(
            byte[] source, String name, byte[] content) throws Exception {
        try (ZipInputStream input = new ZipInputStream(
                    new ByteArrayInputStream(source));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            byte[] buffer = new byte[8_192];
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) output.write(buffer, 0, read);
                }
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry(name));
            output.write(content);
            output.closeEntry();
            output.finish();
            return bytes.toByteArray();
        }
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String code) {
        assertThatThrownBy(callable)
                .isInstanceOf(PracticeAssessmentExcelException.class)
                .extracting(exception ->
                        ((PracticeAssessmentExcelException) exception).code())
                .isEqualTo(code);
    }

    private record Fixture(
            PracticeAssessmentExcelService service,
            PracticeAssessmentQuickExcelCodec quickCodec,
            PracticeDraftRepository repository,
            PracticeDraftContractService contract,
            ObjectMapper objectMapper) {
    }

    @FunctionalInterface
    private interface WorkbookConsumer {
        void accept(Workbook workbook) throws Exception;
    }

    @FunctionalInterface
    private interface WorkbookMutation {
        void accept(Sheet sheet) throws Exception;
    }
}
