package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateJson;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact one-sheet codec for practice-quick-excel-v1. */
final class PracticeAssessmentQuickExcelCodec {

    static final String SHEET_NAME = "QUICK_QUESTIONS";
    static final String SENTINEL = "KSH_PRACTICE_QUICK_EXCEL";
    static final String CONTRACT_VERSION = "practice-quick-excel-v1";
    private static final Pattern STABLE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,80}");
    private static final Pattern KOREAN =
            Pattern.compile(".*[가-힣].*", Pattern.DOTALL);
    private static final Pattern MEDIA_REFERENCE = Pattern.compile(
            "(?i)(https?://|material:|/practice/materials/|"
                    + "\\.(mp3|wav|m4a|ogg|aac|png|jpe?g|gif|webp)(?:$|[?#\\s]))");
    private static final List<String> HEADERS = List.of(
            "group_key",
            "group_label",
            "group_instruction",
            "stimulus_text",
            "question_key",
            "question_order",
            "question_type",
            "writing_task",
            "prompt",
            "option_a",
            "option_b",
            "option_c",
            "option_d",
            "option_e",
            "option_f",
            "option_g",
            "option_h",
            "correct_answer",
            "blank_1_answers",
            "blank_2_answers",
            "points",
            "teacher_explanation_vi",
            "preparation_seconds",
            "response_seconds");
    private static final Set<String> QUICK_RL_TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_ANSWER",
            "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK");
    private static final Map<String, Integer> WRITING_POINTS = Map.of(
            "Q51", 10,
            "Q52", 10,
            "Q53", 30,
            "Q54", 50);

    private final ObjectMapper objectMapper;
    private final AssessmentAuthoringCatalogService catalogService;

    PracticeAssessmentQuickExcelCodec(
            ObjectMapper objectMapper,
            AssessmentAuthoringCatalogService catalogService) {
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
    }

    static List<String> headers() {
        return HEADERS;
    }

    boolean hasIdentityMarker(Workbook workbook) {
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            if (SHEET_NAME.equals(sheet.getSheetName())
                    || SENTINEL.equals(raw(sheet, 0, 0))
                    || CONTRACT_VERSION.equals(raw(sheet, 0, 1))) {
                return true;
            }
        }
        return false;
    }

    byte[] buildTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Font bold = workbook.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);

            Row identity = sheet.createRow(0);
            identity.createCell(0).setCellValue(SENTINEL);
            identity.createCell(1).setCellValue(CONTRACT_VERSION);
            Row note = sheet.createRow(1);
            note.createCell(0).setCellValue(
                    "Nhập câu hỏi từ dòng 4. Target Test/kỹ năng/lesson do route KSH quyết định.");
            Row headerRow = sheet.createRow(2);
            for (int index = 0; index < HEADERS.size(); index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(HEADERS.get(index));
                cell.setCellStyle(header);
                sheet.setColumnWidth(index, width(HEADERS.get(index)) * 256);
            }
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    2, 2, 0, HEADERS.size() - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể tạo file mẫu Quick Excel v1.", exception);
        }
    }

    QuickParseResult parse(
            Workbook workbook,
            String routeSkill,
            PackageInspection packageInspection) {
        validateIdentity(workbook, packageInspection);
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        validateCells(sheet);
        validateHeaders(sheet);

        String skill = normalized(routeSkill).toUpperCase(Locale.ROOT);
        if (!Set.of("READING", "LISTENING", "WRITING", "SPEAKING")
                .contains(skill)) {
            throw error("CANDIDATE_TARGET_INVALID",
                    "Kỹ năng target của Quick Excel không hợp lệ.");
        }

        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        Set<String> questionIds = new HashSet<>();
        List<PracticeAssessmentExcelService.ImportRowPreview> previews =
                new ArrayList<>();
        BigDecimal totalPoints = BigDecimal.ZERO;

        for (int rowIndex = 3; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (blank(row, formatter)) continue;
            int excelRow = rowIndex + 1;
            QuickRow values = readRow(row, formatter, excelRow);
            if (values.questionKey().isBlank()) {
                throw error("QUICK_QUESTION_KEY_REQUIRED",
                        "Dòng " + excelRow
                                + " có dữ liệu nhưng thiếu question_key.");
            }
            requireId(values.groupKey(), "group_key", excelRow);
            requireId(values.questionKey(), "question_key", excelRow);
            if (!questionIds.add(values.questionKey())) {
                throw error("QUICK_QUESTION_KEY_DUPLICATED",
                        "question_key bị trùng tại dòng " + excelRow + ".");
            }
            int questionOrder = positiveInteger(
                    values.questionOrder(), "question_order", excelRow);
            validateExactUppercase(values.questionType(), "question_type", excelRow);

            GroupAccumulator group = groups.computeIfAbsent(
                    values.groupKey(), ignored -> new GroupAccumulator(
                            values.groupKey(), groups.size() + 1, excelRow));
            group.acceptShared(values, excelRow);
            if (!group.questionOrders.add(questionOrder)) {
                throw error("QUICK_QUESTION_ORDER_INVALID",
                        "question_order bị trùng trong group tại dòng "
                                + excelRow + ".");
            }

            ObjectNode question = question(
                    values, skill, questionOrder, excelRow);
            group.questions.add(question);
            BigDecimal points = question.path("points").decimalValue();
            totalPoints = totalPoints.add(points);
            previews.add(preview(values, skill, excelRow, points));
        }
        if (questionIds.isEmpty()) {
            throw error("NO_IMPORTABLE_QUESTIONS",
                    "Quick Excel chưa có dòng câu hỏi nào từ dòng 4.");
        }

        ArrayNode candidateGroups = objectMapper.createArrayNode();
        for (GroupAccumulator group : groups.values()) {
            candidateGroups.add(group.toCandidate(skill));
        }
        return new QuickParseResult(
                candidateGroups,
                List.copyOf(previews),
                groups.size(),
                questionIds.size(),
                totalPoints);
    }

    private void validateIdentity(
            Workbook workbook, PackageInspection packageInspection) {
        if (workbook.getNumberOfSheets() != 1) {
            throw error("QUICK_SHEET_COUNT_INVALID",
                    "Quick Excel phải có đúng một sheet không ẩn QUICK_QUESTIONS.");
        }
        Sheet sheet = workbook.getSheetAt(0);
        if (!SHEET_NAME.equals(sheet.getSheetName())
                || workbook.isSheetHidden(0)
                || workbook.isSheetVeryHidden(0)) {
            throw error("QUICK_SHEET_COUNT_INVALID",
                    "Quick Excel phải có đúng một sheet không ẩn QUICK_QUESTIONS.");
        }
        if (!SENTINEL.equals(raw(sheet, 0, 0))
                || !CONTRACT_VERSION.equals(raw(sheet, 0, 1))) {
            throw error("QUICK_SENTINEL_INVALID",
                    "A1/B1 của Quick Excel không đúng contract v1.");
        }
        if (packageInspection.macro()) {
            throw error("QUICK_MACRO_NOT_ALLOWED",
                    "Quick Excel không cho phép macro.");
        }
        if (packageInspection.externalLink()) {
            throw error("QUICK_EXTERNAL_LINK_NOT_ALLOWED",
                    "Quick Excel không cho phép external link.");
        }
    }

    private void validateCells(Sheet sheet) {
        if (sheet.getNumMergedRegions() > 0) {
            throw error("QUICK_MERGED_CELLS_NOT_ALLOWED",
                    "Quick Excel không cho phép merged cells.");
        }
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA) {
                    throw error("QUICK_FORMULA_NOT_ALLOWED",
                            "Quick Excel không cho phép formula tại "
                                    + cell.getAddress().formatAsString() + ".");
                }
                if (cell.getColumnIndex() >= HEADERS.size()
                        && !formatter.formatCellValue(cell).trim().isBlank()) {
                    if (row.getRowNum() == 2
                            && looksAdvancedHeader(
                            formatter.formatCellValue(cell))) {
                        throw advanced(
                                "Quick Excel có cột media/layout phải dùng Advanced.");
                    }
                    throw error("QUICK_COLUMN_UNSUPPORTED",
                            "Quick Excel có dữ liệu ngoài 24 cột contract tại "
                                    + cell.getAddress().formatAsString() + ".");
                }
            }
        }
    }

    private void validateHeaders(Sheet sheet) {
        Row row = sheet.getRow(2);
        if (row == null) {
            throw error("QUICK_HEADER_INVALID",
                    "Quick Excel thiếu header ở dòng 3.");
        }
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int index = 0; index < HEADERS.size(); index++) {
            String actual = formatter.formatCellValue(
                    row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            if (!HEADERS.get(index).equals(actual)) {
                if (looksAdvancedHeader(actual)) {
                    throw advanced("Quick Excel có cột media/layout không được hỗ trợ.");
                }
                throw error("QUICK_HEADER_INVALID",
                        "Header dòng 3 phải đúng 24 cột và đúng thứ tự contract.");
            }
        }
    }

    private QuickRow readRow(Row row, DataFormatter formatter, int excelRow) {
        List<String> values = new ArrayList<>(HEADERS.size());
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = row.getCell(
                    index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : formatter.formatCellValue(cell);
            values.add(normalized(value));
        }
        return new QuickRow(excelRow, values);
    }

    private ObjectNode question(
            QuickRow row, String skill, int order, int excelRow) {
        if (looksLikeMediaReference(row.stimulusText())
                || looksLikeMediaReference(row.prompt())
                || row.options().stream().anyMatch(
                PracticeAssessmentQuickExcelCodec::looksLikeMediaReference)) {
            throw advanced(
                    "Quick Excel không hỗ trợ image/audio/media reference.");
        }
        String type = row.questionType();
        if ("MATCHING".equals(type)) {
            throw advanced("MATCHING phải dùng Advanced Excel.");
        }
        Set<String> supported = switch (skill) {
            case "READING", "LISTENING" -> QUICK_RL_TYPES;
            case "WRITING" -> Set.of("ESSAY");
            case "SPEAKING" -> Set.of("SPEAKING");
            default -> Set.of();
        };
        if (!supported.contains(type)) {
            throw advanced("Dạng " + type
                    + " không thuộc support matrix Quick cho " + skill + ".");
        }
        if (row.prompt().isBlank()) {
            throw error("QUESTION_PROMPT_REQUIRED",
                    "prompt không được để trống tại dòng " + excelRow + ".");
        }

        List<String> optionTexts = contiguousOptions(row, excelRow);
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", "question-content-v3");
        ArrayNode options = content.putArray("options");
        ArrayNode contentBlanks = content.putArray("blanks");
        content.put("languageTag", "ko");
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("schemaVersion", "answer-spec-v1");
        answer.put("questionType", type);
        ArrayNode correctIds = answer.putArray("correctOptionIds");
        answer.putNull("correctValue");
        ArrayNode answerBlanks = answer.putArray("blanks");

        BigDecimal points;
        if ("READING".equals(skill) || "LISTENING".equals(skill)) {
            requireBlank(row.writingTask(), "writing_task", excelRow);
            requireBlank(row.preparationSeconds(), "preparation_seconds", excelRow);
            requireBlank(row.responseSeconds(), "response_seconds", excelRow);
            points = points(row.points(), BigDecimal.ONE, excelRow);
            buildObjective(
                    row, type, optionTexts, options, contentBlanks,
                    correctIds, answer, answerBlanks, excelRow);
        } else if ("WRITING".equals(skill)) {
            requireNoOptions(optionTexts, excelRow);
            requireBlank(row.correctAnswer(), "correct_answer", excelRow);
            requireBlank(row.preparationSeconds(), "preparation_seconds", excelRow);
            requireBlank(row.responseSeconds(), "response_seconds", excelRow);
            points = buildWriting(row, content, answer, excelRow);
        } else {
            requireNoOptions(optionTexts, excelRow);
            requireBlank(row.writingTask(), "writing_task", excelRow);
            requireBlank(row.correctAnswer(), "correct_answer", excelRow);
            requireBlank(row.blank1Answers(), "blank_1_answers", excelRow);
            requireBlank(row.blank2Answers(), "blank_2_answers", excelRow);
            points = points(
                    row.points(), speakingDefaultPoints(), excelRow);
            if (points.compareTo(speakingDefaultPoints()) != 0) {
                throw error("QUESTION_POINTS_INVALID",
                        "Điểm Quick Speaking phải để trống hoặc bằng default profile.");
            }
            if (!KOREAN.matcher(row.prompt()).matches()) {
                throw error("SPEAKING_MANUAL_PROMPT_KOREAN_REQUIRED",
                        "Prompt Quick Speaking phải chứa tiếng Hàn.");
            }
            ObjectNode delivery = content.putObject("speakingDelivery");
            delivery.put("inputType", "manual_text");
            delivery.put("deliveryMode", "text_only");
            delivery.putNull("promptAudioReference");
            delivery.put("audioOrigin", "none");
            delivery.putNull("promptPlayLimit");
            delivery.put("preparationSeconds", boundedInteger(
                    row.preparationSeconds(), 30, 0, 600,
                    "preparation_seconds", excelRow));
            delivery.put("responseSeconds", boundedInteger(
                    row.responseSeconds(), 60, 1, 1800,
                    "response_seconds", excelRow));
            answer.put("scoringPolicyCode", "PROFILE_BASED");
        }

        ObjectNode question = objectMapper.createObjectNode();
        question.put("candidateQuestionId", row.questionKey());
        question.put("questionOrder", order);
        question.put("questionType", type);
        if (!row.writingTask().isBlank()) {
            question.put("essayTaskType", row.writingTask());
        }
        question.put("prompt", row.prompt());
        question.put("points", points);
        if (!row.teacherExplanation().isBlank()) {
            question.put("explanationVi", row.teacherExplanation());
        }
        question.set("questionContent", content);
        question.set("answerSpec", answer);
        question.put("reviewState", "REVIEW_REQUIRED");
        question.set("sourceRefs", sourceRefs(excelRow, null));
        return question;
    }

    private void buildObjective(
            QuickRow row,
            String type,
            List<String> optionTexts,
            ArrayNode options,
            ArrayNode contentBlanks,
            ArrayNode correctIds,
            ObjectNode answer,
            ArrayNode answerBlanks,
            int excelRow) {
        if (Set.of("SINGLE_CHOICE", "MULTIPLE_ANSWER").contains(type)) {
            if (optionTexts.size() < 2 || optionTexts.size() > 8) {
                throw error("OPTION_AUTHORITY_INVALID",
                        "Câu lựa chọn phải có 2–8 option liên tục từ A.");
            }
            for (int index = 0; index < optionTexts.size(); index++) {
                ObjectNode option = options.addObject();
                option.put("id", "opt_" + (char) ('A' + index));
                option.put("text", optionTexts.get(index));
            }
            List<String> selected = splitAnswers(row.correctAnswer(), excelRow);
            if (("SINGLE_CHOICE".equals(type) && selected.size() != 1)
                    || ("MULTIPLE_ANSWER".equals(type)
                    && selected.size() < 2)) {
                throw error("OPTION_AUTHORITY_INVALID",
                        "correct_answer không đúng cardinality của dạng lựa chọn.");
            }
            Set<String> unique = new LinkedHashSet<>();
            for (String value : selected) {
                if (!value.matches("[A-H]")
                        || value.charAt(0) - 'A' >= optionTexts.size()
                        || !unique.add(value)) {
                    throw error("OPTION_AUTHORITY_INVALID",
                            "correct_answer phải tham chiếu option A–H hiện có và không trùng.");
                }
                correctIds.add("opt_" + value);
            }
            requireBlank(row.blank1Answers(), "blank_1_answers", excelRow);
            requireBlank(row.blank2Answers(), "blank_2_answers", excelRow);
            answer.put("scoringPolicyCode", "ALL_OR_NOTHING");
            return;
        }
        requireNoOptions(optionTexts, excelRow);
        if ("TRUE_FALSE_NOT_GIVEN".equals(type)) {
            if (!Set.of("TRUE", "FALSE", "NOT_GIVEN")
                    .contains(row.correctAnswer())) {
                throw error("OPTION_AUTHORITY_INVALID",
                        "TFNG phải dùng TRUE, FALSE hoặc NOT_GIVEN.");
            }
            answer.put("correctValue", row.correctAnswer());
            requireBlank(row.blank1Answers(), "blank_1_answers", excelRow);
            requireBlank(row.blank2Answers(), "blank_2_answers", excelRow);
            answer.put("scoringPolicyCode", "ALL_OR_NOTHING");
            return;
        }
        requireBlank(row.correctAnswer(), "correct_answer", excelRow);
        if (!row.blank2Answers().isBlank()
                || count(row.prompt(), "{{blank:") != 1
                || count(row.prompt(), "{{blank:blank_1}}") != 1) {
            throw advanced(
                    "Quick FILL_BLANK chỉ hỗ trợ đúng một {{blank:blank_1}}.");
        }
        List<String> accepted = splitAnswers(row.blank1Answers(), excelRow);
        if (accepted.isEmpty()) {
            throw error("SIMPLE_BLANK_REQUIRED",
                    "blank_1_answers cần ít nhất một đáp án.");
        }
        ObjectNode blank = contentBlanks.addObject();
        blank.put("id", "blank_1");
        blank.put("prompt", "blank_1");
        ObjectNode blankAnswer = answerBlanks.addObject();
        blankAnswer.put("blankId", "blank_1");
        ArrayNode acceptedValues = blankAnswer.putArray("acceptedValues");
        accepted.forEach(acceptedValues::add);
        answer.put("scoringPolicyCode", "NORMALIZED_EXACT");
    }

    private BigDecimal buildWriting(
            QuickRow row,
            ObjectNode content,
            ObjectNode answer,
            int excelRow) {
        String task = row.writingTask();
        validateExactUppercase(task, "writing_task", excelRow);
        Integer fixedPoints = WRITING_POINTS.get(task);
        if (fixedPoints == null) {
            throw error("WRITING_TASK_IDENTITY_INVALID",
                    "Quick Writing chỉ hỗ trợ Q51–Q54.");
        }
        BigDecimal points = points(
                row.points(), BigDecimal.valueOf(fixedPoints), excelRow);
        if (points.compareTo(BigDecimal.valueOf(fixedPoints)) != 0) {
            throw error("WRITING_TASK_IDENTITY_INVALID",
                    task + " phải có đúng " + fixedPoints + " điểm.");
        }
        answer.put("scoringPolicyCode", "PROFILE_BASED");
        if (Set.of("Q51", "Q52").contains(task)) {
            if (row.prompt().contains("{{blank:")
                    || row.blank1Answers().isBlank()
                    || row.blank2Answers().isBlank()) {
                throw error("WRITING_BLANK_AUTHORITY_INVALID",
                        task + " cần hai answer authority typed; không dùng objective token.");
            }
            List<String> first = splitAnswers(row.blank1Answers(), excelRow);
            List<String> second = splitAnswers(row.blank2Answers(), excelRow);
            ObjectNode response = content.putObject("writingResponse");
            response.put("responseSchemaVersion", "writing-blanks.v1");
            response.put("responseMode", "STRUCTURED_BLANKS");
            response.put("taskType", task);
            ArrayNode definitions = response.putArray("blanks");
            String firstBlankId = task.toLowerCase(Locale.ROOT) + "-b1";
            String secondBlankId = task.toLowerCase(Locale.ROOT) + "-b2";
            writingDefinition(definitions, firstBlankId, 1, task + " 빈칸 1");
            writingDefinition(definitions, secondBlankId, 2, task + " 빈칸 2");

            ObjectNode authority = answer.putObject("writingBlankAuthority");
            authority.put("contractVersion", "writing-blank-authority.v1");
            authority.put("taskType", task);
            authority.put("normalization", "NFC");
            authority.put("whitespacePolicy", "TRIM_COLLAPSE");
            ArrayNode blanks = authority.putArray("blanks");
            writingAuthority(blanks, firstBlankId, 1, first);
            writingAuthority(blanks, secondBlankId, 2, second);
        } else if (!row.blank1Answers().isBlank()
                || !row.blank2Answers().isBlank()
                || row.prompt().contains("{{blank:")) {
            throw error("WRITING_BLANK_AUTHORITY_INVALID",
                    "Q53/Q54 không được có structured blank authority.");
        }
        return points;
    }

    private static void writingDefinition(
            ArrayNode definitions, String id, int ordinal, String context) {
        ObjectNode blank = definitions.addObject();
        blank.put("blankId", id);
        blank.put("ordinal", ordinal);
        blank.put("context", context);
    }

    private static void writingAuthority(
            ArrayNode blanks,
            String id,
            int ordinal,
            List<String> accepted) {
        ObjectNode blank = blanks.addObject();
        blank.put("blankId", id);
        blank.put("ordinal", ordinal);
        ArrayNode answers = blank.putArray("acceptedAnswers");
        for (String text : accepted) {
            ObjectNode value = answers.addObject();
            value.put("text", text);
            value.put("equivalence", "EXACT");
            value.putNull("reason");
            value.putArray("evidenceIds");
        }
    }

    private List<String> contiguousOptions(QuickRow row, int excelRow) {
        List<String> raw = row.options();
        List<String> result = new ArrayList<>();
        boolean gap = false;
        for (String value : raw) {
            if (value.isBlank()) {
                gap = true;
            } else if (gap) {
                throw error("OPTION_AUTHORITY_INVALID",
                        "Option phải liên tục từ A tại dòng " + excelRow + ".");
            } else {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private void requireNoOptions(List<String> options, int excelRow) {
        if (!options.isEmpty()) {
            throw error("OPTION_AUTHORITY_INVALID",
                    "Dạng câu tại dòng " + excelRow
                            + " không được khai báo option.");
        }
    }

    private List<String> splitAnswers(String raw, int excelRow) {
        if (raw.isBlank()) return List.of();
        String[] tokens = raw.split("\\|", -1);
        List<String> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            String value = normalized(token);
            if (value.isBlank() || !unique.add(value)) {
                throw error("OPTION_AUTHORITY_INVALID",
                        "Danh sách đáp án tại dòng " + excelRow
                                + " có giá trị trống hoặc trùng.");
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private BigDecimal points(
            String raw, BigDecimal fallback, int excelRow) {
        if (raw.isBlank()) return fallback;
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() <= 0) throw new NumberFormatException();
            return value.stripTrailingZeros();
        } catch (RuntimeException exception) {
            throw error("QUESTION_POINTS_INVALID",
                    "points phải là số dương tại dòng " + excelRow + ".");
        }
    }

    private BigDecimal speakingDefaultPoints() {
        return catalogService.defaultTemplate()
                .requireSkill("SPEAKING").defaultPoints();
    }

    private PracticeAssessmentExcelService.ImportRowPreview preview(
            QuickRow row,
            String skill,
            int excelRow,
            BigDecimal points) {
        List<PracticeAssessmentExcelService.ImportOptionPreview> options =
                new ArrayList<>();
        List<String> optionValues = contiguousOptions(row, excelRow);
        for (int index = 0; index < optionValues.size(); index++) {
            options.add(new PracticeAssessmentExcelService.ImportOptionPreview(
                    String.valueOf((char) ('A' + index)),
                    optionValues.get(index), null));
        }
        PracticeAssessmentExcelService.ImportRowDetail detail =
                new PracticeAssessmentExcelService.ImportRowDetail(
                        skill,
                        row.groupInstruction(),
                        "READING".equals(skill) ? row.stimulusText() : null,
                        "LISTENING".equals(skill) ? row.stimulusText() : null,
                        null, null, null, null, null, options);
        return new PracticeAssessmentExcelService.ImportRowPreview(
                excelRow,
                SHEET_NAME,
                null,
                null,
                row.groupKey(),
                row.questionOrder(),
                null,
                detail,
                row.questionKey(),
                row.groupKey(),
                row.questionType(),
                row.correctAnswer(),
                row.prompt(),
                row.teacherExplanation(),
                "-",
                optionValues.isEmpty()
                        ? "-" : String.join(", ", optionValues),
                "VALID",
                true,
                List.of());
    }

    private ArrayNode sourceRefs(int excelRow, String column) {
        ArrayNode refs = objectMapper.createArrayNode();
        ObjectNode ref = refs.addObject();
        ref.put("kind", "SHEET_ROW");
        ref.put("sourceId", "quick-row-" + excelRow);
        ref.put("sheet", SHEET_NAME);
        ref.put("row", excelRow);
        if (column != null) ref.put("column", column);
        return refs;
    }

    private static int positiveInteger(
            String raw, String field, int excelRow) {
        try {
            int value = new BigDecimal(raw).intValueExact();
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw error("QUICK_POSITIVE_INTEGER_REQUIRED",
                    field + " phải là số nguyên dương tại dòng " + excelRow + ".");
        }
    }

    private static int boundedInteger(
            String raw,
            int fallback,
            int minimum,
            int maximum,
            String field,
            int excelRow) {
        if (raw.isBlank()) return fallback;
        try {
            int value = new BigDecimal(raw).intValueExact();
            if (value < minimum || value > maximum) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException exception) {
            throw error("QUICK_INTEGER_RANGE_INVALID",
                    field + " ngoài khoảng cho phép tại dòng " + excelRow + ".");
        }
    }

    private static void requireId(
            String value, String field, int excelRow) {
        if (!STABLE_ID.matcher(value).matches()) {
            throw error("QUICK_STABLE_ID_INVALID",
                    field + " không đúng [A-Za-z0-9._-]{1,80} tại dòng "
                            + excelRow + ".");
        }
    }

    private static void validateExactUppercase(
            String value, String field, int excelRow) {
        if (value.isBlank()
                || !value.equals(value.toUpperCase(Locale.ROOT))) {
            throw error("QUICK_ENUM_CASE_INVALID",
                    field + " phải là enum uppercase exact tại dòng "
                            + excelRow + ".");
        }
    }

    private static void requireBlank(
            String value, String field, int excelRow) {
        if (!value.isBlank()) {
            throw error("QUICK_COLUMN_VALUE_NOT_ALLOWED",
                    field + " phải để trống tại dòng " + excelRow + ".");
        }
    }

    private static int count(String value, String token) {
        int result = 0;
        int cursor = 0;
        while ((cursor = value.indexOf(token, cursor)) >= 0) {
            result++;
            cursor += token.length();
        }
        return result;
    }

    private static boolean looksAdvancedHeader(String value) {
        String normalized = value == null
                ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("media")
                || normalized.contains("audio")
                || normalized.contains("image")
                || normalized.contains("layout")
                || normalized.contains("tts")
                || normalized.contains("upload")
                || normalized.contains("matching");
    }

    private static boolean looksLikeMediaReference(String value) {
        return value != null && MEDIA_REFERENCE.matcher(value).find();
    }

    private static boolean blank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).trim().isBlank()) return false;
        }
        return true;
    }

    private static String raw(Sheet sheet, int rowIndex, int columnIndex) {
        if (sheet == null) return "";
        Row row = sheet.getRow(rowIndex);
        if (row == null) return "";
        Cell cell = row.getCell(
                columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return new DataFormatter(Locale.ROOT).formatCellValue(cell);
    }

    private static int width(String header) {
        if (Set.of("prompt", "stimulus_text", "teacher_explanation_vi")
                .contains(header)) return 42;
        if (header.startsWith("option_") || header.contains("answers")) return 25;
        return Math.max(16, Math.min(28, header.length() + 3));
    }

    private static String normalized(String value) {
        return PracticeAuthoringCandidateJson.normalizedText(value);
    }

    private static PracticeAssessmentExcelException error(
            String code, String message) {
        return new PracticeAssessmentExcelException(code, message);
    }

    private static PracticeAssessmentExcelException advanced(String message) {
        return error("ADVANCED_AUTHORING_REQUIRED", message);
    }

    record PackageInspection(
            boolean macro, boolean externalLink, boolean quickMarker) {
        PackageInspection(boolean macro, boolean externalLink) {
            this(macro, externalLink, false);
        }
    }

    record QuickParseResult(
            ArrayNode groups,
            List<PracticeAssessmentExcelService.ImportRowPreview> rows,
            int groupCount,
            int questionCount,
            BigDecimal totalPoints) {
    }

    private final class GroupAccumulator {
        private final String id;
        private final int order;
        private final int firstExcelRow;
        private final Set<Integer> questionOrders = new HashSet<>();
        private final ArrayNode questions = objectMapper.createArrayNode();
        private String label;
        private String instruction;
        private String stimulus;

        private GroupAccumulator(String id, int order, int firstExcelRow) {
            this.id = id;
            this.order = order;
            this.firstExcelRow = firstExcelRow;
        }

        private void acceptShared(QuickRow row, int excelRow) {
            if (label == null) {
                if (row.groupLabel().isBlank()) {
                    throw error("QUICK_GROUP_LABEL_REQUIRED",
                            "group_label bắt buộc ở dòng đầu của group " + id + ".");
                }
                label = row.groupLabel();
                instruction = row.groupInstruction();
                stimulus = row.stimulusText();
                return;
            }
            label = consistent(label, row.groupLabel(), "group_label", excelRow);
            instruction = consistent(
                    instruction, row.groupInstruction(), "group_instruction", excelRow);
            stimulus = consistent(
                    stimulus, row.stimulusText(), "stimulus_text", excelRow);
        }

        private ObjectNode toCandidate(String skill) {
            if (("WRITING".equals(skill) || "SPEAKING".equals(skill))
                    && stimulus != null && !stimulus.isBlank()) {
                throw advanced("Quick Writing/Speaking không hỗ trợ stimulus dùng chung.");
            }
            ObjectNode group = objectMapper.createObjectNode();
            group.put("candidateGroupId", id);
            group.put("groupOrder", order);
            group.put("label", label == null ? "" : label);
            group.put("instruction", instruction == null ? "" : instruction);
            ObjectNode stimulusNode = group.putObject("stimulus");
            stimulusNode.put("schemaVersion", "practice-stimulus-v2");
            stimulusNode.put("type", switch (skill) {
                case "READING" -> "READING_PASSAGE";
                case "LISTENING" -> "LISTENING_AUDIO";
                default -> "NONE";
            });
            stimulusNode.put("instruction", instruction == null ? "" : instruction);
            stimulusNode.put("passageText",
                    "READING".equals(skill) && stimulus != null ? stimulus : "");
            stimulusNode.put("transcriptText",
                    "LISTENING".equals(skill) && stimulus != null ? stimulus : "");
            stimulusNode.putNull("mediaReference");
            ObjectNode provenance = stimulusNode.putObject("provenance");
            provenance.put("source", "QUICK_EXCEL");
            provenance.put("approved", false);
            provenance.set("sourceRefs", sourceRefs(
                    firstExcelRow,
                    stimulus == null || stimulus.isBlank()
                            ? null : "stimulus_text"));
            group.set("sourceRefs", sourceRefs(firstExcelRow, null));
            group.set("questions", questions);
            return group;
        }

        private String consistent(
                String previous, String current, String field, int excelRow) {
            if (current.isBlank()) return previous;
            if (previous == null || previous.isBlank()) return current;
            if (!previous.equals(current)) {
                throw error("QUICK_GROUP_SHARED_CONTENT_CONFLICT",
                        field + " không nhất quán trong group " + id
                                + " tại dòng " + excelRow + ".");
            }
            return previous;
        }
    }

    private record QuickRow(int excelRow, List<String> values) {
        private String value(int index) { return values.get(index); }
        String groupKey() { return value(0); }
        String groupLabel() { return value(1); }
        String groupInstruction() { return value(2); }
        String stimulusText() { return value(3); }
        String questionKey() { return value(4); }
        String questionOrder() { return value(5); }
        String questionType() { return value(6); }
        String writingTask() { return value(7); }
        String prompt() { return value(8); }
        List<String> options() { return values.subList(9, 17); }
        String correctAnswer() { return value(17); }
        String blank1Answers() { return value(18); }
        String blank2Answers() { return value(19); }
        String points() { return value(20); }
        String teacherExplanation() { return value(21); }
        String preparationSeconds() { return value(22); }
        String responseSeconds() { return value(23); }
    }
}
