package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class PracticeAssessmentExcelV2Codec {

    private static final Set<String> CORE_SHEETS = Set.of("01_THONG_TIN_SET", "02_TAI_NGUYEN");
    private static final Map<String, CanonicalQuestionType> QUESTION_SHEETS = questionSheets();
    private static final List<String> SKILL_ORDER = List.of("LISTENING", "READING", "WRITING", "SPEAKING");
    private static final Pattern LESSON_CODE = Pattern.compile("^[LRWS]\\d+$");
    private static final String[] HEADERS = {
            "test_no", "lesson_code", "group_code", "question_no_in_section", "question_no_in_group",
            "group_instruction_ko", "group_text_or_transcript_ko", "group_image_ref", "group_audio_ref",
            "question_prompt_ko", "question_image_ref", "question_audio_ref", "question_type",
            "correct_answer", "teacher_explanation_vi", "points", "scoring_policy",
            "option_A_text", "option_A_image_ref", "option_B_text", "option_B_image_ref",
            "option_C_text", "option_C_image_ref", "option_D_text", "option_D_image_ref",
            "option_E_text", "option_E_image_ref", "option_F_text", "option_F_image_ref",
            "option_G_text", "option_G_image_ref", "option_H_text", "option_H_image_ref",
            "rubric_profile_ref", "prompt_profile_ref", "extra_answer_schema", "teacher_note"
    };
    private static final String[] HEADER_NOTES = {
            "Số test: 1, 2...", "L1/R1/W1/S1, L2/R2...", "R1.1, L1.17...",
            "Số câu trong phần; không đếm xuyên kỹ năng", "Số câu trong nhóm",
            "Hướng dẫn chung tiếng Hàn", "Bài đọc hoặc transcript dùng chung", "Mã ảnh nhóm trong 02_TAI_NGUYEN",
            "Mã audio nhóm trong 02_TAI_NGUYEN", "Đề bài/câu hỏi tiếng Hàn", "Mã ảnh riêng của câu",
            "Mã audio riêng của câu", "Loại câu hỏi", "Đáp án đúng; multiple dùng A,C; blank dùng B1=từ/từ",
            "Giải thích tiếng Việt của giáo viên", "Điểm tối đa", "Chính sách chấm",
            "Nội dung A", "Ảnh A", "Nội dung B", "Ảnh B", "Nội dung C", "Ảnh C", "Nội dung D", "Ảnh D",
            "Nội dung E", "Ảnh E", "Nội dung F", "Ảnh F", "Nội dung G", "Ảnh G", "Nội dung H", "Ảnh H",
            "Rubric profile", "Prompt profile", "Schema bổ sung", "Ghi chú giáo viên"
    };
    private static final String[] MATCHING_HEADERS = matchingColumns("matching_L", "_text", "_image_ref");
    private static final String[] MATCHING_HEADER_NOTES = matchingNotes();

    private final PracticeDraftContractService draftContractService;
    private final PracticeDraftValidator draftValidator;
    private final AssessmentContractCodec contractCodec;
    private final ObjectMapper objectMapper;

    PracticeAssessmentExcelV2Codec(PracticeDraftContractService draftContractService,
                                   PracticeDraftValidator draftValidator,
                                   AssessmentContractCodec contractCodec,
                                   ObjectMapper objectMapper) {
        this.draftContractService = draftContractService;
        this.draftValidator = draftValidator;
        this.contractCodec = contractCodec;
        this.objectMapper = objectMapper;
    }

    byte[] buildTemplate(AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            TemplateStyles styles = styles(workbook);
            instructionsSheet(workbook, template, styles);
            setInfoSheet(workbook, template, styles);
            materialsSheet(workbook, styles);
            Map<String, Integer> nextQuestionBySkill = new LinkedHashMap<>();
            Map<String, Integer> nextGroupBySkill = new LinkedHashMap<>();
            for (Map.Entry<String, CanonicalQuestionType> entry : QUESTION_SHEETS.entrySet()) {
                if (supportsType(template, entry.getValue())) {
                    String skill = firstSkillForType(template, entry.getValue());
                    int firstQuestion = nextQuestionBySkill.getOrDefault(skill, 1);
                    int groupNumber = nextGroupBySkill.getOrDefault(skill, 1);
                    questionSheet(workbook, entry.getKey(), entry.getValue(), template, styles,
                            skill, firstQuestion, groupNumber);
                    nextQuestionBySkill.put(skill, firstQuestion + 4);
                    nextGroupBySkill.put(skill, groupNumber + 1);
                }
            }
            catalogSheet(workbook, template, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo file mẫu Excel v2.", exception);
        }
    }

    private void instructionsSheet(Workbook workbook,
                                   AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                   TemplateStyles styles) {
        Sheet sheet = workbook.createSheet("00_HUONG_DAN");
        write(sheet, 0, styles.title(), "KSH TEACHER IMPORT TEMPLATE v2", "");
        write(sheet, 1, styles.header(), "Mục", "Hướng dẫn");
        write(sheet, 2, null, "Mẫu đề", template.displayName() + " (" + template.code() + ")");
        write(sheet, 3, null, "Cấu trúc", "Practice Set > Test > Kỹ năng L/R/W/S > Group > Question.");
        write(sheet, 4, null, "Đánh số", "Mỗi L1/R1/W1/S1 tự đánh số câu từ 1; không nối số giữa các kỹ năng.");
        write(sheet, 5, null, "Đáp án", "correct_answer đứng trước option; multiple dùng A,C; fill blank dùng B1=빵/토스트.");
        write(sheet, 6, null, "Tài nguyên", "Ảnh/audio có thể gắn ở SECTION, GROUP hoặc QUESTION trong sheet 02_TAI_NGUYEN.");
        write(sheet, 7, null, "Dòng lỗi", "Màn xem trước luôn hiển thị lỗi; khi xác nhận, hệ thống tự bỏ dòng lỗi và nhập dòng hợp lệ.");
        write(sheet, 8, null, "Tệp cục bộ", "Đường dẫn trên máy giáo viên cần được tải lên lại; hệ thống không đọc trực tiếp ổ đĩa cá nhân.");
        write(sheet, 9, null, "Quyền cấu hình", "Kỹ năng, dạng câu, số option và giới hạn câu do admin/head cấu hình theo chứng chỉ.");
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 105 * 256);
        sheet.createFreezePane(0, 2);
    }

    private void setInfoSheet(Workbook workbook,
                              AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                              TemplateStyles styles) {
        Sheet sheet = workbook.createSheet("01_THONG_TIN_SET");
        write(sheet, 0, styles.header(), "field", "value", "teacher_note", "import_note");
        write(sheet, 1, styles.note(), "Tên trường", "Giá trị giáo viên nhập", "Ghi chú dễ hiểu", "Ghi chú importer");
        write(sheet, 2, null, "schema_version", PracticeAssessmentExcelService.SCHEMA_VERSION,
                "Không chỉnh sửa", "Contract version");
        write(sheet, 3, null, "set_title", template.displayName() + " - Practice Set 01",
                "Tên bộ đề hiển thị", "Practice Set title");
        write(sheet, 4, null, "program_code", template.code(), "Chứng chỉ/mẫu đề", "Phải khớp cấu hình");
        write(sheet, 5, null, "language", "ko", "Ngôn ngữ học", "Korean");
        write(sheet, 6, null, "version_note", "Bản nhập Excel giáo viên", "Ghi chú phiên bản", "Optional");
        write(sheet, 7, null, "tests_in_set", "1", "Các Test có trong Set", "Ví dụ: 1,2");
        write(sheet, 8, null, "allowed_lessons_example", allowedLessonExample(template),
                "Ví dụ mã phần", "Sinh từ cấu hình");
        autosize(sheet, 4, 58);
        sheet.createFreezePane(0, 2);
    }

    private void materialsSheet(Workbook workbook, TemplateStyles styles) {
        Sheet sheet = workbook.createSheet("02_TAI_NGUYEN");
        write(sheet, 0, styles.header(), "material_ref", "material_level", "lesson_code", "group_code",
                "question_no_in_section", "material_type", "source_file_or_link", "normalized_target_hint", "teacher_note");
        write(sheet, 1, styles.note(), "Mã tài nguyên", "SECTION/GROUP/QUESTION", "L1/R1...", "L1.1/R1.1...",
                "Số câu nếu cấp QUESTION", "IMAGE/AUDIO/PASSAGE_TEXT/AUDIO_TRANSCRIPT",
                "Tệp hoặc link giáo viên cung cấp", "Gợi ý tên chuẩn sau import", "Ghi chú");
        write(sheet, 2, null, "IMG_L1_G01", "GROUP", "L1", "L1.1", "", "IMAGE",
                "images/listening_scene_01.png", "images/t01_l_g01.png", "Ảnh chung câu 1-4");
        write(sheet, 3, null, "AUD_T01_L_Q01", "QUESTION", "L1", "L1.1", 1, "AUDIO",
                "audio01.mp3", "audio/t01_l_q01.mp3", "Audio riêng câu 1");
        write(sheet, 4, null, "AUD_T01_L_G17", "GROUP", "L1", "L1.17", "", "AUDIO",
                "listening_17_21.mp3", "audio/t01_l_g17.mp3", "Audio chung câu 17-21");
        write(sheet, 5, null, "PASS_R1_G01", "GROUP", "R1", "R1.1", "", "PASSAGE_TEXT",
                "이 글은 한국어 학습에 관한 내용입니다.", "", "Bài đọc chung");
        autosize(sheet, 9, 46);
        sheet.createFreezePane(0, 2);
    }

    private void questionSheet(Workbook workbook,
                               String sheetName,
                               CanonicalQuestionType type,
                               AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                               TemplateStyles styles,
                               String skill,
                               int firstQuestion,
                               int groupNumber) {
        Sheet sheet = workbook.createSheet(sheetName);
        String[] headers = type == CanonicalQuestionType.MATCHING
                ? concat(HEADERS, MATCHING_HEADERS) : HEADERS;
        String[] notes = type == CanonicalQuestionType.MATCHING
                ? concat(HEADER_NOTES, MATCHING_HEADER_NOTES) : HEADER_NOTES;
        write(sheet, 0, styles.header(), (Object[]) headers);
        write(sheet, 1, styles.note(), (Object[]) notes);
        AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy = template.requireSkill(skill);
        AssessmentAuthoringCatalogService.QuestionAuthoringPolicy questionPolicy = skillPolicy.questionPolicy(type.name());
        for (int index = 0; index < 4; index++) {
            Object[] sample = sampleRow(type, skill, questionPolicy, skillPolicy,
                    firstQuestion + index, index + 1, groupNumber);
            if (type == CanonicalQuestionType.MATCHING) sample = concat(sample, matchingSample());
            write(sheet, index + 2, null, sample);
        }
        for (int column = 0; column < headers.length; column++) {
            int width = column == 14 ? 44 : (column >= 17 && column <= 32 ? 24 : 20);
            sheet.setColumnWidth(column, Math.min(width, 60) * 256);
        }
        sheet.createFreezePane(0, 2);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 5, 0, headers.length - 1));
    }

    private void catalogSheet(Workbook workbook,
                              AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                              TemplateStyles styles) {
        Sheet sheet = workbook.createSheet("10_DANH_MUC");
        write(sheet, 0, styles.header(), "skill", "enabled_question_types", "max_questions", "option_rules", "excel_import");
        int row = 1;
        for (String skill : SKILL_ORDER) {
            AssessmentAuthoringCatalogService.SkillAuthoringPolicy policy = template.skills().get(skill);
            if (policy == null) continue;
            String optionRules = policy.questionPolicies().values().stream()
                    .filter(candidate -> candidate.maxOptions() > 0)
                    .map(candidate -> candidate.questionType() + ":" + candidate.minOptions() + "-" + candidate.maxOptions())
                    .reduce((left, right) -> left + "; " + right).orElse("-");
            write(sheet, row++, null, skill, String.join(",", policy.questionTypes()),
                    policy.maxQuestions(), optionRules, policy.excelImportEnabled() ? "YES" : "NO");
        }
        write(sheet, row + 1, styles.note(), "max_tests", template.maxTests(), "", "", "");
        autosize(sheet, 5, 54);
        sheet.createFreezePane(0, 1);
    }

    private Object[] sampleRow(CanonicalQuestionType type,
                               String skill,
                               AssessmentAuthoringCatalogService.QuestionAuthoringPolicy questionPolicy,
                               AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy,
                               int questionNo,
                               int questionNoInGroup,
                               int groupNumber) {
        Object[] values = new Object[HEADERS.length];
        String lesson = skillPrefix(skill) + "1";
        values[0] = 1;
        values[1] = lesson;
        values[2] = lesson + "." + groupNumber;
        values[3] = questionNo;
        values[4] = questionNoInGroup;
        values[5] = sampleInstruction(type);
        values[6] = sampleSharedText(type, questionNo);
        values[9] = samplePrompt(type, questionNo);
        values[12] = type.name();
        values[13] = sampleAnswer(type);
        values[14] = "Giải thích mẫu bằng tiếng Việt cho câu " + questionNo + ".";
        values[15] = skillPolicy.defaultPoints();
        values[16] = sampleScoring(type);
        if (type == CanonicalQuestionType.SINGLE_CHOICE || type == CanonicalQuestionType.MULTIPLE_CHOICE) {
            int min = questionPolicy == null ? 2 : questionPolicy.minOptions();
            int max = questionPolicy == null ? 8 : questionPolicy.maxOptions();
            int optionCount = Math.min(max, Math.max(min, 4));
            for (int index = 0; index < optionCount; index++) {
                values[17 + index * 2] = "선택지 " + (char) ('A' + index);
            }
        }
        if (type == CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN) {
            values[17] = "TRUE"; values[19] = "FALSE"; values[21] = "NOT_GIVEN";
        }
        if (type == CanonicalQuestionType.MATCHING) {
            values[17] = "매일 한국어를 공부합니다.";
            values[19] = "주말에 운동합니다.";
        }
        if (type == CanonicalQuestionType.ESSAY || type == CanonicalQuestionType.SPEAKING) {
            values[33] = questionPolicy != null && questionPolicy.rubricProfile() != null
                    ? questionPolicy.rubricProfile().code() : "";
            values[34] = questionPolicy != null && questionPolicy.promptProfile() != null
                    ? questionPolicy.promptProfile().code() : "";
        }
        values[35] = sampleExtraSchema(type);
        values[36] = "Câu mẫu tiếng Hàn " + questionNo;
        return values;
    }

    private static Object[] matchingSample() {
        Object[] values = new Object[MATCHING_HEADERS.length];
        values[0] = "민수";
        values[2] = "지영";
        return values;
    }

    private static String[] matchingColumns(String prefix, String textSuffix, String imageSuffix) {
        String[] values = new String[16];
        for (int index = 0; index < 8; index++) {
            int number = index + 1;
            values[index * 2] = prefix + number + textSuffix;
            values[index * 2 + 1] = prefix + number + imageSuffix;
        }
        return values;
    }

    private static String[] matchingNotes() {
        String[] values = new String[16];
        for (int index = 0; index < 8; index++) {
            int number = index + 1;
            values[index * 2] = "Nội dung bên trái L" + number;
            values[index * 2 + 1] = "Ảnh bên trái L" + number;
        }
        return values;
    }

    private static String[] concat(String[] left, String[] right) {
        String[] values = new String[left.length + right.length];
        System.arraycopy(left, 0, values, 0, left.length);
        System.arraycopy(right, 0, values, left.length, right.length);
        return values;
    }

    private static Object[] concat(Object[] left, Object[] right) {
        Object[] values = new Object[left.length + right.length];
        System.arraycopy(left, 0, values, 0, left.length);
        System.arraycopy(right, 0, values, left.length, right.length);
        return values;
    }

    private static String sampleInstruction(CanonicalQuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> "다음을 읽거나 듣고 알맞은 것을 고르십시오.";
            case MULTIPLE_CHOICE -> "맞는 것을 모두 고르십시오.";
            case TRUE_FALSE_NOT_GIVEN -> "내용과 같으면 TRUE, 다르면 FALSE, 알 수 없으면 NOT_GIVEN을 고르십시오.";
            case FILL_BLANK -> "빈칸에 들어갈 알맞은 말을 쓰십시오.";
            case MATCHING -> "각 항목을 알맞게 연결하십시오.";
            case ESSAY -> "다음 주제에 대해 쓰십시오.";
            case SPEAKING -> "다음 질문에 대답하십시오.";
        };
    }

    private static String sampleSharedText(CanonicalQuestionType type, int number) {
        return switch (type) {
            case ESSAY, SPEAKING -> "";
            default -> "민수는 매일 아침 한국어를 공부하고 학교에 갑니다. (예시 " + number + ")";
        };
    }

    private static String samplePrompt(CanonicalQuestionType type, int number) {
        return switch (type) {
            case SINGLE_CHOICE -> "민수는 아침에 무엇을 합니까?";
            case MULTIPLE_CHOICE -> "민수에 대한 설명으로 맞는 것을 모두 고르십시오.";
            case TRUE_FALSE_NOT_GIVEN -> "민수는 아침에 한국어를 공부합니다.";
            case FILL_BLANK -> "빈칸 B1에 들어갈 말을 쓰십시오.";
            case MATCHING -> "사람과 알맞은 행동을 연결하십시오.";
            case ESSAY -> "한국어를 배우는 이유에 대해 쓰십시오. (" + number + ")";
            case SPEAKING -> "자기소개를 해 보십시오. (" + number + ")";
        };
    }

    private static String sampleAnswer(CanonicalQuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> "A";
            case MULTIPLE_CHOICE -> "A,C";
            case TRUE_FALSE_NOT_GIVEN -> "TRUE";
            case FILL_BLANK -> "B1=한국어/한국말";
            case MATCHING -> "L1=A;L2=B";
            case ESSAY, SPEAKING -> "NO_OBJECTIVE_KEY";
        };
    }

    private static String sampleScoring(CanonicalQuestionType type) {
        return switch (type) {
            case MULTIPLE_CHOICE -> "ALL_OR_NOTHING";
            case FILL_BLANK -> "NORMALIZED_TEXT";
            case MATCHING -> "PAIR_MATCH";
            case ESSAY, SPEAKING -> "AI_EVALUATED";
            default -> "EXACT";
        };
    }

    private static String sampleExtraSchema(CanonicalQuestionType type) {
        return switch (type) {
            case FILL_BLANK -> "BLANKS:B1 aliases with slash";
            case MATCHING -> "MATCHING_PAIR_MAP";
            case ESSAY -> "WRITING_PROFILE_REF";
            case SPEAKING -> "SPEAKING_PROFILE_REF";
            default -> type.name() + "_V1";
        };
    }

    PracticeAssessmentExcelService.ExcelPreview preview(
            Workbook workbook,
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            List<PracticeAssessmentExcelService.ImportIssue> issues) {
        for (String required : CORE_SHEETS) {
            if (workbook.getSheet(required) == null) {
                issues.add(issue("BLOCKING", "SHEET_MISSING", required, 0, null,
                        "Thiếu sheet bắt buộc: " + required, null));
            }
        }
        if (template == null) {
            issues.add(issue("BLOCKING", "TEMPLATE_REQUIRED", "01_THONG_TIN_SET", 0,
                    "program_code", "Phải chọn một mẫu đề đang được bật.", null));
        }
        if (hasFatal(issues)) return emptyPreview(issues);

        Map<String, String> setInfo = readSetInfo(workbook.getSheet("01_THONG_TIN_SET"), issues);
        String schemaVersion = setInfo.getOrDefault("schema_version", PracticeAssessmentExcelService.SCHEMA_VERSION);
        if (!PracticeAssessmentExcelService.SCHEMA_VERSION.equalsIgnoreCase(schemaVersion)) {
            issues.add(issue("BLOCKING", "SCHEMA_VERSION_UNSUPPORTED", "01_THONG_TIN_SET", 3,
                    "schema_version", "Phiên bản file Excel không được hỗ trợ.", null));
        }
        String workbookProgram = setInfo.getOrDefault("program_code", "").trim();
        if (!workbookProgram.isBlank() && !matchesTemplate(workbookProgram, template)) {
            issues.add(issue("BLOCKING", "TEMPLATE_MISMATCH", "01_THONG_TIN_SET", 5,
                    "program_code", "File Excel không thuộc mẫu đề đang chỉnh sửa.", null));
        }

        Map<String, V2Material> materials = readMaterials(workbook.getSheet("02_TAI_NGUYEN"), issues);
        List<V2QuestionRow> rows = new ArrayList<>();
        Map<String, Integer> questionCounts = new LinkedHashMap<>();
        Set<String> questionNumbers = new HashSet<>();
        Map<String, String> groupSignatures = new LinkedHashMap<>();
        boolean foundQuestionSheet = false;

        for (Map.Entry<String, CanonicalQuestionType> entry : QUESTION_SHEETS.entrySet()) {
            Sheet sheet = workbook.getSheet(entry.getKey());
            if (sheet == null) continue;
            foundQuestionSheet = true;
            SheetReader reader;
            try {
                reader = new SheetReader(sheet);
            } catch (IllegalArgumentException exception) {
                issues.add(issue("BLOCKING", "HEADER_INVALID", sheet.getSheetName(), 1,
                        null, exception.getMessage(), null));
                continue;
            }
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row source = sheet.getRow(rowIndex);
                if (reader.blank(source)) continue;
                V2QuestionRow parsed = parseQuestionRow(reader, source, rowIndex + 1, entry.getValue(),
                        template, materials, issues, questionCounts, questionNumbers, groupSignatures);
                rows.add(parsed);
            }
        }
        if (!foundQuestionSheet) {
            issues.add(issue("BLOCKING", "SHEET_MISSING", "Workbook", 0, null,
                    "Không có sheet dạng câu hỏi nào trong file.", null));
        }

        List<V2QuestionRow> importableRows = rows.stream()
                .filter(row -> !hasBlockingForRow(issues, row.rowKey()))
                .sorted(Comparator.comparingInt(V2QuestionRow::testNo)
                        .thenComparingInt(row -> skillRank(row.skill()))
                        .thenComparing(V2QuestionRow::lessonCode)
                        .thenComparingInt(V2QuestionRow::questionNo))
                .toList();
        if (importableRows.isEmpty()) {
            issues.add(issue("BLOCKING", "NO_IMPORTABLE_QUESTIONS", "Workbook", 0, null,
                    "File không còn câu hỏi hợp lệ để nhập.", null));
            return previewResult(null, rows, issues, 0, 0, BigDecimal.ZERO);
        }

        ObjectNode root;
        String normalizedJson;
        try {
            root = buildDraft(setInfo, template, materials, importableRows);
            PracticeDraftContractService.NormalizedDraft normalized = draftContractService.normalize(root, "EXCEL");
            normalizedJson = normalized.json();
            PracticeDraftValidator.ValidationResult validation = draftValidator == null
                    ? null : draftValidator.validate(normalizedJson);
            if (validation != null && validation.hasBlocking()) {
                String detail = validation.messages().stream()
                        .filter(message -> "BLOCKING".equals(message.type()))
                        .map(PracticeDraftValidator.ValidationMsg::content)
                        .distinct().limit(3).reduce((left, right) -> left + " " + right).orElse("");
                issues.add(issue("BLOCKING", "DRAFT_CONTRACT_INVALID", "Draft", 0, null,
                        "Dữ liệu hợp lệ chưa thể tạo draft. " + detail, null));
                normalizedJson = null;
            }
        } catch (RuntimeException exception) {
            issues.add(issue("BLOCKING", "DRAFT_CONTRACT_INVALID", "Draft", 0, null,
                    "Không thể chuẩn hóa dữ liệu Excel: " + exception.getMessage(), null));
            normalizedJson = null;
        }

        int sectionCount = (int) importableRows.stream().map(V2QuestionRow::lessonCode).distinct().count();
        int groupCount = (int) importableRows.stream().map(row -> row.lessonCode() + ":" + row.groupCode()).distinct().count();
        BigDecimal totalPoints = importableRows.stream().map(V2QuestionRow::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return previewResult(normalizedJson, rows, issues, sectionCount, groupCount, totalPoints);
    }

    private Map<String, String> readSetInfo(Sheet sheet,
                                            List<PracticeAssessmentExcelService.ImportIssue> issues) {
        Map<String, String> values = new LinkedHashMap<>();
        SheetReader reader = new SheetReader(sheet);
        for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String field = reader.value(row, "field").trim();
            if (field.isBlank()) continue;
            if (values.putIfAbsent(field, reader.value(row, "value").trim()) != null) {
                issues.add(issue("BLOCKING", "MANIFEST_KEY_DUPLICATE", sheet.getSheetName(), rowIndex + 1,
                        field, "Thông tin Set có field bị trùng: " + field, null));
            }
        }
        return values;
    }

    private Map<String, V2Material> readMaterials(
            Sheet sheet,
            List<PracticeAssessmentExcelService.ImportIssue> issues) {
        Map<String, V2Material> result = new LinkedHashMap<>();
        SheetReader reader = new SheetReader(sheet);
        for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (reader.blank(row)) continue;
            int excelRow = rowIndex + 1;
            String ref = reader.value(row, "material_ref").trim();
            if (ref.isBlank()) {
                issues.add(issue("WARNING", "MATERIAL_REF_REQUIRED", sheet.getSheetName(), excelRow,
                        "material_ref", "Bỏ qua tài nguyên không có material_ref.", null));
                continue;
            }
            String level = reader.value(row, "material_level").toUpperCase(Locale.ROOT);
            String type = reader.value(row, "material_type").toUpperCase(Locale.ROOT);
            if (!Set.of("SECTION", "GROUP", "QUESTION").contains(level)) {
                issues.add(issue("WARNING", "MATERIAL_LEVEL_INVALID", sheet.getSheetName(), excelRow,
                        "material_level", "Cấp tài nguyên phải là SECTION, GROUP hoặc QUESTION.", null));
                continue;
            }
            if (!Set.of("IMAGE", "AUDIO", "PASSAGE_TEXT", "AUDIO_TRANSCRIPT").contains(type)) {
                issues.add(issue("WARNING", "MATERIAL_TYPE_INVALID", sheet.getSheetName(), excelRow,
                        "material_type", "Loại tài nguyên không được hỗ trợ.", null));
                continue;
            }
            String source = reader.value(row, "source_file_or_link");
            String target = reader.value(row, "normalized_target_hint");
            boolean inlineText = Set.of("PASSAGE_TEXT", "AUDIO_TRANSCRIPT").contains(type);
            boolean pendingUpload = !inlineText && !source.isBlank() && !isManagedReference(source);
            V2Material material = new V2Material(
                    ref, level, reader.value(row, "lesson_code").toUpperCase(Locale.ROOT),
                    reader.value(row, "group_code").toUpperCase(Locale.ROOT),
                    nullablePositiveInt(reader.value(row, "question_no_in_section")), type,
                    inlineText ? source : (isManagedReference(source) ? source : null),
                    inlineText ? null : fileName(source), target, pendingUpload,
                    reader.value(row, "teacher_note"));
            if (result.putIfAbsent(ref, material) != null) {
                issues.add(issue("WARNING", "MATERIAL_REF_DUPLICATED", sheet.getSheetName(), excelRow,
                        "material_ref", "material_ref bị trùng; hệ thống giữ dòng đầu tiên.", null));
            }
        }
        return result;
    }

    private V2QuestionRow parseQuestionRow(
            SheetReader reader,
            Row row,
            int excelRow,
            CanonicalQuestionType sheetType,
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            Map<String, V2Material> materials,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            Map<String, Integer> questionCounts,
            Set<String> questionNumbers,
            Map<String, String> groupSignatures) {
        String sheet = reader.sheetName();
        String rowKey = sheet + ":" + excelRow;
        int testNo = positiveInt(reader.value(row, "test_no"));
        String lessonCode = reader.value(row, "lesson_code").toUpperCase(Locale.ROOT);
        String groupCode = reader.value(row, "group_code").toUpperCase(Locale.ROOT);
        int questionNo = positiveInt(reader.value(row, "question_no_in_section"));
        int questionNoInGroup = positiveInt(reader.value(row, "question_no_in_group"));
        String skill = skillFromLesson(lessonCode);
        String rawType = reader.value(row, "question_type").toUpperCase(Locale.ROOT);
        String prompt = reader.value(row, "question_prompt_ko");
        String correctAnswer = reader.value(row, "correct_answer");
        String explanation = reader.value(row, "teacher_explanation_vi");
        BigDecimal points = positiveDecimal(reader.value(row, "points"));

        if (testNo <= 0 || testNo > template.maxTests()) {
            rowBlocking(issues, "TEST_NUMBER_INVALID", sheet, excelRow, "test_no",
                    "test_no phải từ 1 đến " + template.maxTests() + ".", rowKey);
        }
        if (!LESSON_CODE.matcher(lessonCode).matches() || skill == null) {
            rowBlocking(issues, "LESSON_CODE_INVALID", sheet, excelRow, "lesson_code",
                    "lesson_code phải theo dạng L1/R1/W1/S1...", rowKey);
        } else if (testNo > 0 && testNumber(lessonCode) != testNo) {
            rowBlocking(issues, "LESSON_TEST_MISMATCH", sheet, excelRow, "lesson_code",
                    "lesson_code phải dùng cùng số với test_no.", rowKey);
        }
        if (!lessonCode.isBlank() && !groupCode.matches(Pattern.quote(lessonCode) + "\\.\\d+")) {
            rowBlocking(issues, "GROUP_CODE_INVALID", sheet, excelRow, "group_code",
                    "group_code phải theo dạng " + lessonCode + ".1.", rowKey);
        }
        if (questionNo <= 0) {
            rowBlocking(issues, "QUESTION_NUMBER_INVALID", sheet, excelRow, "question_no_in_section",
                    "Số câu trong phần phải lớn hơn 0.", rowKey);
        } else if (!questionNumbers.add(lessonCode + ":" + questionNo)) {
            rowBlocking(issues, "QUESTION_NUMBER_DUPLICATED", sheet, excelRow, "question_no_in_section",
                    "Số câu " + questionNo + " bị trùng trong " + lessonCode + ".", rowKey);
        }
        if (questionNoInGroup <= 0) {
            rowBlocking(issues, "QUESTION_NUMBER_IN_GROUP_INVALID", sheet, excelRow,
                    "question_no_in_group", "Số câu trong nhóm phải lớn hơn 0.", rowKey);
        }
        if (!rawType.equals(sheetType.name())) {
            rowBlocking(issues, "QUESTION_TYPE_SHEET_MISMATCH", sheet, excelRow, "question_type",
                    "question_type phải là " + sheetType + " trong sheet này.", rowKey);
        }
        if (prompt.isBlank()) {
            rowBlocking(issues, "QUESTION_PROMPT_REQUIRED", sheet, excelRow, "question_prompt_ko",
                    "Câu hỏi phải có nội dung tiếng Hàn.", rowKey);
        }
        if (points == null) {
            rowBlocking(issues, "POSITIVE_DECIMAL_REQUIRED", sheet, excelRow, "points",
                    "Điểm phải là số lớn hơn 0.", rowKey);
            points = BigDecimal.ONE;
        }

        AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy = null;
        AssessmentAuthoringCatalogService.QuestionAuthoringPolicy questionPolicy = null;
        if (skill != null) {
            try {
                skillPolicy = template.requireSkill(skill);
                if (!skillPolicy.questionTypes().contains(sheetType.name())) {
                    rowBlocking(issues, "QUESTION_TYPE_NOT_ALLOWED_BY_TEMPLATE", sheet, excelRow,
                            "question_type", "Mẫu đề không cho phép " + sheetType + " ở phần " + skill + ".", rowKey);
                } else if (!skillPolicy.excelImportEnabled()) {
                    rowBlocking(issues, "EXCEL_IMPORT_DISABLED_FOR_SKILL", sheet, excelRow,
                            "lesson_code", "Mẫu đề đã khóa nhập Excel cho kỹ năng " + skill + ".", rowKey);
                } else {
                    questionPolicy = skillPolicy.questionPolicy(sheetType.name());
                }
                int count = questionCounts.merge(lessonCode, 1, Integer::sum);
                if (count > skillPolicy.maxQuestions()) {
                    rowBlocking(issues, "SECTION_QUESTION_LIMIT_EXCEEDED", sheet, excelRow,
                            "question_no_in_section", "Phần " + lessonCode + " vượt giới hạn "
                                    + skillPolicy.maxQuestions() + " câu.", rowKey);
                }
            } catch (IllegalArgumentException exception) {
                rowBlocking(issues, "SKILL_NOT_ALLOWED_BY_TEMPLATE", sheet, excelRow, "lesson_code",
                        "Kỹ năng " + skill + " không được bật trong mẫu đề.", rowKey);
            }
        }

        List<V2Option> options = readOptions(reader, row, materials, issues, rowKey, excelRow);
        List<V2MatchingItem> matchingLeftItems = readMatchingLeftItems(
                reader, row, materials, issues, rowKey, excelRow, sheetType);
        validateOptionCount(sheetType, options, questionPolicy, issues, sheet, excelRow, rowKey);
        AnswerData answer = parseAnswer(
                sheetType, correctAnswer, options, matchingLeftItems, issues, sheet, excelRow, rowKey);
        if (explanation.isBlank() && ("READING".equals(skill) || "LISTENING".equals(skill))) {
            rowWarning(issues, "TEACHER_EXPLANATION_MISSING", sheet, excelRow, "teacher_explanation_vi",
                    "Nên bổ sung giải thích tiếng Việt để AI có ngữ cảnh tốt hơn.", rowKey);
        }

        String groupInstruction = reader.value(row, "group_instruction_ko");
        String groupText = reader.value(row, "group_text_or_transcript_ko");
        String groupImage = resolveMaterial(reader.value(row, "group_image_ref"), materials,
                issues, sheet, excelRow, "group_image_ref", rowKey);
        String groupAudio = resolveMaterial(reader.value(row, "group_audio_ref"), materials,
                issues, sheet, excelRow, "group_audio_ref", rowKey);
        String questionImage = resolveMaterial(reader.value(row, "question_image_ref"), materials,
                issues, sheet, excelRow, "question_image_ref", rowKey);
        String questionAudio = resolveMaterial(reader.value(row, "question_audio_ref"), materials,
                issues, sheet, excelRow, "question_audio_ref", rowKey);

        String signature = String.join("|", groupInstruction, groupText, nullToEmpty(groupImage), nullToEmpty(groupAudio));
        String previousSignature = groupSignatures.putIfAbsent(lessonCode + ":" + groupCode, signature);
        if (previousSignature != null && !previousSignature.equals(signature)) {
            rowWarning(issues, "GROUP_SHARED_CONTENT_CONFLICT", sheet, excelRow, "group_code",
                    "Các dòng cùng group_code có nội dung dùng chung khác nhau; hệ thống giữ dòng đầu.", rowKey);
        }

        String requestedRubric = normalizeProfileRef(reader.value(row, "rubric_profile_ref"));
        String requestedPrompt = normalizeProfileRef(reader.value(row, "prompt_profile_ref"));
        String approvedRubric = questionPolicy != null && questionPolicy.rubricProfile() != null
                ? questionPolicy.rubricProfile().code() : null;
        String approvedPrompt = questionPolicy != null && questionPolicy.promptProfile() != null
                ? questionPolicy.promptProfile().code() : null;
        if (!requestedRubric.isBlank() && approvedRubric != null && !requestedRubric.equals(approvedRubric)) {
            rowWarning(issues, "RUBRIC_PROFILE_OVERRIDDEN", sheet, excelRow, "rubric_profile_ref",
                    "Hệ thống sẽ dùng rubric được admin/head phê duyệt: " + approvedRubric + ".", rowKey);
        }
        if (!requestedPrompt.isBlank() && approvedPrompt != null && !requestedPrompt.equals(approvedPrompt)) {
            rowWarning(issues, "PROMPT_PROFILE_OVERRIDDEN", sheet, excelRow, "prompt_profile_ref",
                    "Hệ thống sẽ dùng prompt được admin/head phê duyệt: " + approvedPrompt + ".", rowKey);
        }

        return new V2QuestionRow(
                excelRow, sheet, rowKey, testNo, lessonCode, groupCode, questionNo, questionNoInGroup,
                skill, sheetType, groupInstruction, groupText, groupImage, groupAudio, prompt,
                questionImage, questionAudio, correctAnswer, explanation, points,
                scoringPolicy(sheetType, reader.value(row, "scoring_policy")), options,
                matchingLeftItems, answer.blanks(), answer.matchingPairs(),
                answer.correctOptionLetters(), answer.correctValue(),
                questionPolicy, approvedRubric, approvedPrompt,
                reader.value(row, "extra_answer_schema"), reader.value(row, "teacher_note"));
    }

    private List<V2Option> readOptions(
            SheetReader reader,
            Row row,
            Map<String, V2Material> materials,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String rowKey,
            int excelRow) {
        List<V2Option> options = new ArrayList<>();
        for (char letter = 'A'; letter <= 'H'; letter++) {
            String text = reader.value(row, "option_" + letter + "_text");
            String rawImage = reader.value(row, "option_" + letter + "_image_ref");
            String image = resolveMaterial(rawImage, materials, issues, reader.sheetName(), excelRow,
                    "option_" + letter + "_image_ref", rowKey);
            if (!text.isBlank() || image != null) options.add(new V2Option(String.valueOf(letter), text, image));
        }
        return options;
    }

    private List<V2MatchingItem> readMatchingLeftItems(
            SheetReader reader,
            Row row,
            Map<String, V2Material> materials,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String rowKey,
            int excelRow,
            CanonicalQuestionType type) {
        if (type != CanonicalQuestionType.MATCHING) return List.of();
        List<V2MatchingItem> items = new ArrayList<>();
        for (int number = 1; number <= 8; number++) {
            String id = "L" + number;
            String text = reader.value(row, "matching_" + id + "_text");
            String image = resolveMaterial(reader.value(row, "matching_" + id + "_image_ref"),
                    materials, issues, reader.sheetName(), excelRow,
                    "matching_" + id + "_image_ref", rowKey);
            if (!text.isBlank() || image != null) items.add(new V2MatchingItem(id, text, image));
        }
        if (items.isEmpty()) {
            rowBlocking(issues, "MATCHING_LEFT_ITEMS_REQUIRED", reader.sheetName(), excelRow,
                    "matching_L1_text", "Câu nối phải có ít nhất một mục bên trái.", rowKey);
        }
        return List.copyOf(items);
    }

    private void validateOptionCount(
            CanonicalQuestionType type,
            List<V2Option> options,
            AssessmentAuthoringCatalogService.QuestionAuthoringPolicy policy,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String sheet,
            int row,
            String rowKey) {
        if (type == CanonicalQuestionType.MATCHING) {
            if (options.isEmpty() || options.size() > 8) {
                rowBlocking(issues, "MATCHING_RIGHT_ITEMS_REQUIRED", sheet, row, "option_A_text",
                        "Câu nối phải có từ 1 đến 8 mục bên phải ở Option A-H.", rowKey);
            }
            return;
        }
        if (type != CanonicalQuestionType.SINGLE_CHOICE && type != CanonicalQuestionType.MULTIPLE_CHOICE) return;
        int min = policy == null ? 2 : policy.minOptions();
        int max = policy == null ? 8 : policy.maxOptions();
        if (options.size() < min || options.size() > max) {
            String expected = min == max ? "đúng " + min : "từ " + min + " đến " + max;
            rowBlocking(issues, "OPTION_COUNT_OUTSIDE_TEMPLATE", sheet, row, "option_A_text",
                    "Dạng câu này phải có " + expected + " phương án chữ hoặc ảnh.", rowKey);
        }
    }

    private AnswerData parseAnswer(
            CanonicalQuestionType type,
            String raw,
            List<V2Option> options,
            List<V2MatchingItem> matchingLeftItems,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String sheet,
            int row,
            String rowKey) {
        Set<String> availableOptions = options.stream().map(V2Option::letter)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return switch (type) {
            case SINGLE_CHOICE -> {
                String selected = raw.trim().toUpperCase(Locale.ROOT);
                if (!availableOptions.contains(selected)) {
                    rowBlocking(issues, "SINGLE_CHOICE_ANSWER_INVALID", sheet, row, "correct_answer",
                            "Đáp án đúng phải là đúng một chữ cái của phương án hiện có.", rowKey);
                    yield AnswerData.empty();
                }
                yield new AnswerData(List.of(selected), null, List.of(), Map.of());
            }
            case MULTIPLE_CHOICE -> {
                List<String> values = split(raw, ",").stream()
                        .map(value -> value.toUpperCase(Locale.ROOT)).toList();
                Set<String> unique = new LinkedHashSet<>(values);
                if (values.isEmpty() || unique.size() != values.size() || !availableOptions.containsAll(unique)) {
                    rowBlocking(issues, "MULTIPLE_CHOICE_ANSWER_INVALID", sheet, row, "correct_answer",
                            "Đáp án chọn nhiều phải không rỗng, không trùng và theo dạng A,C.", rowKey);
                    yield AnswerData.empty();
                }
                yield new AnswerData(List.copyOf(unique), null, List.of(), Map.of());
            }
            case TRUE_FALSE_NOT_GIVEN -> {
                String value = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                if (!Set.of("TRUE", "FALSE", "NOT_GIVEN").contains(value)) {
                    rowBlocking(issues, "TFNG_ANSWER_INVALID", sheet, row, "correct_answer",
                            "Đáp án phải là TRUE, FALSE hoặc NOT_GIVEN.", rowKey);
                    yield AnswerData.empty();
                }
                yield new AnswerData(List.of(), value, List.of(), Map.of());
            }
            case FILL_BLANK -> {
                List<V2Blank> blanks = new ArrayList<>();
                Set<String> ids = new HashSet<>();
                for (String token : split(raw, ";")) {
                    String id;
                    String acceptedRaw;
                    int separator = token.indexOf('=');
                    if (separator < 0) {
                        id = "B1";
                        acceptedRaw = token;
                    } else {
                        id = token.substring(0, separator).trim();
                        acceptedRaw = token.substring(separator + 1).trim();
                    }
                    List<String> accepted = split(acceptedRaw, "/");
                    if (!id.matches("[A-Za-z][A-Za-z0-9_]*") || accepted.isEmpty() || !ids.add(id)) {
                        rowBlocking(issues, "FILL_BLANK_ANSWER_INVALID", sheet, row, "correct_answer",
                                "Điền từ phải theo dạng B1=con bò/con nghé; các blank ID không được trùng.", rowKey);
                        blanks.clear();
                        break;
                    }
                    blanks.add(new V2Blank(id, accepted));
                }
                if (blanks.isEmpty()) {
                    rowBlockingOnce(issues, "FILL_BLANK_ANSWER_INVALID", sheet, row, "correct_answer",
                            "Câu điền từ phải có ít nhất một đáp án được chấp nhận.", rowKey);
                }
                yield new AnswerData(List.of(), null, List.copyOf(blanks), Map.of());
            }
            case MATCHING -> {
                Map<String, String> pairs = new LinkedHashMap<>();
                for (String token : split(raw, ";")) {
                    int separator = token.indexOf('=');
                    if (separator <= 0 || separator == token.length() - 1) {
                        pairs.clear();
                        break;
                    }
                    String left = token.substring(0, separator).trim();
                    String right = token.substring(separator + 1).trim().toUpperCase(Locale.ROOT);
                    if (!left.matches("[A-Za-z][A-Za-z0-9_]*")
                            || !right.matches("[A-H][A-Za-z0-9_]*") || pairs.putIfAbsent(left, right) != null) {
                        pairs.clear();
                        break;
                    }
                }
                if (pairs.isEmpty()) {
                    rowBlocking(issues, "MATCHING_ANSWER_INVALID", sheet, row, "correct_answer",
                            "Câu nối phải có pair map theo dạng L1=A;L2=B.", rowKey);
                } else {
                    Set<String> leftIds = matchingLeftItems.stream().map(V2MatchingItem::id)
                            .collect(java.util.stream.Collectors.toSet());
                    Set<String> rightIds = options.stream().map(V2Option::letter)
                            .collect(java.util.stream.Collectors.toSet());
                    if (!leftIds.equals(pairs.keySet()) || !rightIds.containsAll(pairs.values())) {
                        rowBlocking(issues, "MATCHING_PAIR_REFERENCE_INVALID", sheet, row, "correct_answer",
                                "Pair map phải ánh xạ mọi mục trái L1-L8 tới Option A-H đang có.", rowKey);
                    }
                }
                yield new AnswerData(List.of(), null, List.of(),
                        java.util.Collections.unmodifiableMap(new LinkedHashMap<>(pairs)));
            }
            case ESSAY, SPEAKING -> new AnswerData(List.of(), null, List.of(), Map.of());
        };
    }

    private ObjectNode buildDraft(
            Map<String, String> setInfo,
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            Map<String, V2Material> materials,
            List<V2QuestionRow> rows) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", PracticeDraftContractService.SCHEMA_VERSION);
        ObjectNode document = root.putObject("document");
        document.put("title", defaultText(setInfo.get("set_title"), template.displayName() + " - Bộ đề Excel"));
        document.put("description", defaultText(setInfo.get("version_note"), "Nhập từ Excel v2"));
        document.put("detectedCategory", template.categoryCode());
        document.put("assessmentProgramCode", template.programCode());
        document.put("assessmentProgramVersionId", template.programVersionId());
        document.put("assessmentProgramVersion", template.programVersion());
        document.put("examTemplateCode", template.code());
        document.put("creationMethod", "EXCEL");

        ArrayNode testsNode = root.putArray("tests");
        Map<Integer, String> testClientIds = new LinkedHashMap<>();
        rows.stream().map(V2QuestionRow::testNo).distinct().sorted().forEach(testNo -> {
            String clientId = "excel-test-" + testNo;
            testClientIds.put(testNo, clientId);
            ObjectNode test = testsNode.addObject();
            test.put("clientId", clientId);
            test.put("testNo", testNo);
            test.put("title", "Test " + testNo);
            test.put("description", "");
            test.putNull("estimatedMinutes");
        });

        ArrayNode materialNodes = root.putArray("materials");
        for (V2Material material : materials.values()) {
            ObjectNode node = materialNodes.addObject();
            node.put("materialRef", material.ref());
            node.put("level", material.level());
            nullable(node, "lessonCode", material.lessonCode());
            nullable(node, "groupCode", material.groupCode());
            if (material.questionNo() == null) node.putNull("questionNoInSection");
            else node.put("questionNoInSection", material.questionNo());
            node.put("type", material.type());
            nullable(node, "managedReference", material.managedReference());
            nullable(node, "sourceFileName", material.sourceFileName());
            nullable(node, "normalizedTargetHint", material.normalizedTargetHint());
            node.put("pendingUpload", material.pendingUpload());
            nullable(node, "teacherNote", material.teacherNote());
        }

        ArrayNode sectionsNode = root.putArray("sections");
        root.putArray("warnings");
        Map<String, ObjectNode> sections = new LinkedHashMap<>();
        Map<String, Map<String, ObjectNode>> groups = new LinkedHashMap<>();

        for (V2QuestionRow row : rows) {
            ObjectNode section = sections.computeIfAbsent(row.lessonCode(), lesson -> {
                ObjectNode created = sectionsNode.addObject();
                created.put("clientId", "excel-section-" + lesson.toLowerCase(Locale.ROOT));
                created.put("testNo", row.testNo());
                created.put("testClientId", testClientIds.get(row.testNo()));
                created.put("lessonCode", lesson);
                created.put("title", skillTitle(row.skill()) + " " + lesson);
                created.put("skill", row.skill());
                created.put("durationMinutes", template.requireSkill(row.skill()).durationMinutes());
                created.putArray("groups");
                groups.put(lesson, new LinkedHashMap<>());
                return created;
            });
            Map<String, ObjectNode> sectionGroups = groups.get(row.lessonCode());
            ObjectNode group = sectionGroups.computeIfAbsent(row.groupCode(), groupCode -> {
                ObjectNode created = ((ArrayNode) section.path("groups")).addObject();
                created.put("clientId", "excel-group-" + slug(groupCode));
                created.put("groupCode", groupCode);
                created.put("label", groupCode);
                created.put("instruction", row.groupInstruction());
                created.put("passageText", "READING".equals(row.skill()) ? row.groupText() : "");
                created.put("transcriptText", "LISTENING".equals(row.skill()) ? row.groupText() : "");
                nullable(created, "imageUrl", row.groupImage());
                nullable(created, "audioUrl", row.groupAudio());
                ObjectNode stimulus = created.putObject("stimulus");
                stimulus.put("schemaVersion", PracticeDraftContractService.STIMULUS_SCHEMA_VERSION);
                stimulus.put("type", stimulusType(row));
                nullable(stimulus, "instruction", row.groupInstruction());
                nullable(stimulus, "passageText", "READING".equals(row.skill()) ? row.groupText() : null);
                nullable(stimulus, "transcriptText", "LISTENING".equals(row.skill()) ? row.groupText() : null);
                nullable(stimulus, "mediaReference", row.groupAudio());
                nullable(stimulus, "imageReference", row.groupImage());
                ObjectNode provenance = stimulus.putObject("provenance");
                provenance.put("source", "EXCEL");
                provenance.put("approved", true);
                provenance.putArray("sourceRegionIds");
                created.putArray("questions");
                return created;
            });
            ((ArrayNode) group.path("questions")).add(buildQuestion(row));
        }
        return root;
    }

    private ObjectNode buildQuestion(V2QuestionRow row) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("clientId", "excel-" + row.lessonCode().toLowerCase(Locale.ROOT)
                + "-q" + String.format(Locale.ROOT, "%03d", row.questionNo()));
        question.put("questionNo", row.questionNo());
        question.put("questionType", row.type().name());
        question.put("canonicalQuestionType", row.type().name());
        question.put("prompt", row.prompt());
        question.put("points", row.points());
        question.put("explanationVi", row.explanation());
        question.put("importSource", "EXCEL");
        question.put("reviewRequired", false);
        nullable(question, "imageUrl", row.questionImage());
        nullable(question, "audioUrl", row.questionAudio());
        nullable(question, "teacherNote", row.teacherNote());
        nullable(question, "extraAnswerSchema", row.extraAnswerSchema());

        List<QuestionContent.Option> contentOptions = row.options().stream()
                .map(option -> new QuestionContent.Option("opt_" + option.letter(), option.text(), option.imageReference()))
                .toList();
        List<QuestionContent.Blank> contentBlanks = row.blanks().stream()
                .map(blank -> new QuestionContent.Blank(blank.id(), blank.id()))
                .toList();
        List<QuestionContent.Item> leftItems = row.matchingLeftItems().stream()
                .map(item -> new QuestionContent.Item(item.id(), item.text(), item.imageReference()))
                .toList();
        List<QuestionContent.Item> rightItems = row.options().stream()
                .map(option -> new QuestionContent.Item(
                        "right_" + option.letter(), option.text(), option.imageReference()))
                .toList();
        Map<String, String> matchingPairs = new LinkedHashMap<>();
        row.matchingPairs().forEach((left, right) -> matchingPairs.put(left, "right_" + right));

        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                contentOptions,
                leftItems,
                rightItems,
                contentBlanks,
                row.questionImage(),
                row.questionAudio());
        AnswerSpec answerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                row.type(),
                row.correctOptionLetters().stream().map(letter -> "opt_" + letter).toList(),
                row.correctValue(),
                row.blanks().stream().map(blank -> new AnswerSpec.BlankAnswer(blank.id(), blank.acceptedValues())).toList(),
                matchingPairs,
                row.scoringPolicy(),
                profileCode(row.questionPolicy() == null ? null : row.questionPolicy().scoringProfile()),
                row.approvedPrompt(),
                row.approvedRubric(),
                profileVersion(row.questionPolicy() == null ? null : row.questionPolicy().scoringProfile()),
                profileVersion(row.questionPolicy() == null ? null : row.questionPolicy().promptProfile()),
                profileVersion(row.questionPolicy() == null ? null : row.questionPolicy().rubricProfile()));
        try {
            question.set("questionContent", objectMapper.readTree(contractCodec.writeQuestionContent(content, row.type())));
            question.set("answerSpec", objectMapper.readTree(contractCodec.writeAnswerSpec(answerSpec, content)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không tạo được typed contract cho " + row.rowKey(), exception);
        }

        ArrayNode legacyOptions = question.putArray("options");
        for (V2Option option : row.options()) {
            ObjectNode legacy = legacyOptions.addObject();
            legacy.put("id", "opt_" + option.letter());
            legacy.put("text", option.text());
            nullable(legacy, "imageReference", option.imageReference());
        }
        ObjectNode answer = question.putObject("answer");
        answer.put("type", legacyAnswerType(row.type()));
        String legacyAnswer = legacyAnswer(row);
        answer.put("value", legacyAnswer);
        question.put("answerKey", legacyAnswer);
        question.put("scoringPolicyCode", row.scoringPolicy().name());

        if (!row.blanks().isEmpty()) {
            ArrayNode blanks = question.putArray("fillBlanks");
            for (V2Blank blank : row.blanks()) {
                ObjectNode value = blanks.addObject();
                value.put("id", blank.id());
                value.put("prompt", blank.id());
                ArrayNode accepted = value.putArray("acceptedValues");
                blank.acceptedValues().forEach(accepted::add);
            }
        }
        if (!row.matchingPairs().isEmpty()) {
            ArrayNode pairs = question.putArray("matchingPairs");
            Map<String, V2MatchingItem> leftById = row.matchingLeftItems().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            V2MatchingItem::id, item -> item, (left, right) -> left, LinkedHashMap::new));
            Map<String, V2Option> rightById = row.options().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            V2Option::letter, option -> option, (left, right) -> left, LinkedHashMap::new));
            row.matchingPairs().forEach((left, right) -> {
                ObjectNode value = pairs.addObject();
                value.put("leftId", left);
                V2MatchingItem leftItem = leftById.get(left);
                V2Option rightItem = rightById.get(right);
                value.put("leftText", leftItem == null ? left : leftItem.text());
                nullable(value, "leftImageReference", leftItem == null ? null : leftItem.imageReference());
                value.put("rightId", "right_" + right);
                value.put("rightText", rightItem == null ? right : rightItem.text());
                nullable(value, "rightImageReference", rightItem == null ? null : rightItem.imageReference());
            });
        }
        if (row.type() == CanonicalQuestionType.ESSAY) {
            question.put("essayTaskType", essayTaskType(row));
        }
        nullable(question, "promptProfileCode", row.approvedPrompt());
        nullable(question, "rubricProfileCode", row.approvedRubric());
        nullable(question, "scoringProfileCode",
                profileCode(row.questionPolicy() == null ? null : row.questionPolicy().scoringProfile()));
        return question;
    }

    private PracticeAssessmentExcelService.ExcelPreview previewResult(
            String draftJson,
            List<V2QuestionRow> sourceRows,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            int sectionCount,
            int groupCount,
            BigDecimal totalPoints) {
        boolean fatal = hasFatal(issues);
        Map<String, Integer> importedNumbers = new LinkedHashMap<>();
        Map<String, Integer> nextNumberByLesson = new LinkedHashMap<>();
        if (!fatal && draftJson != null) {
            sourceRows.stream()
                    .filter(row -> !hasBlockingForRow(issues, row.rowKey()))
                    .sorted(Comparator.comparingInt(V2QuestionRow::testNo)
                            .thenComparingInt(row -> skillRank(row.skill()))
                            .thenComparing(V2QuestionRow::lessonCode)
                            .thenComparingInt(V2QuestionRow::questionNo))
                    .forEach(row -> importedNumbers.put(row.rowKey(),
                            nextNumberByLesson.merge(row.lessonCode(), 1, Integer::sum)));
        }
        List<PracticeAssessmentExcelService.ImportRowPreview> rows = new ArrayList<>();
        int importable = 0;
        int warningRows = 0;
        int errorRows = 0;
        for (V2QuestionRow row : sourceRows) {
            List<PracticeAssessmentExcelService.ImportIssue> rowIssues = issues.stream()
                    .filter(issue -> row.rowKey().equals(issue.rowKey())).toList();
            boolean error = rowIssues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity()));
            boolean warning = rowIssues.stream().anyMatch(issue -> "WARNING".equals(issue.severity()));
            String status = error ? "ERROR" : (warning ? "WARNING" : "VALID");
            boolean rowImportable = !fatal && !error && draftJson != null;
            if (rowImportable) importable++;
            if (error) errorRows++;
            else if (warning) warningRows++;
            rows.add(new PracticeAssessmentExcelService.ImportRowPreview(
                    row.excelRow(), row.sheet(), row.testNo(), row.lessonCode(), row.groupCode(),
                    String.valueOf(row.questionNo()), rowImportable ? importedNumbers.get(row.rowKey()) : null,
                    previewDetail(row),
                    row.rowKey(), row.groupCode(), row.type().name(),
                    row.correctAnswer(), row.prompt(), row.explanation(), mediaSummary(row), optionSummary(row.options()),
                    status, rowImportable,
                    rowIssues.stream().map(PracticeAssessmentExcelService.ImportIssue::message).distinct().toList()));
        }
        return new PracticeAssessmentExcelService.ExcelPreview(
                draftJson, List.copyOf(issues), List.copyOf(rows), sectionCount, groupCount,
                sourceRows.size(), importable, warningRows, errorRows, totalPoints);
    }

    private static PracticeAssessmentExcelService.ImportRowDetail previewDetail(V2QuestionRow row) {
        List<PracticeAssessmentExcelService.ImportOptionPreview> options = row.options().stream()
                .map(option -> new PracticeAssessmentExcelService.ImportOptionPreview(
                        option.letter(), option.text(), option.imageReference()))
                .toList();
        Map<String, V2MatchingItem> leftById = row.matchingLeftItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        V2MatchingItem::id, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, V2Option> rightById = row.options().stream()
                .collect(java.util.stream.Collectors.toMap(
                        V2Option::letter, option -> option, (left, right) -> left, LinkedHashMap::new));
        List<PracticeAssessmentExcelService.ImportMatchingPairPreview> pairs = row.matchingPairs().entrySet().stream()
                .map(pair -> {
                    V2MatchingItem left = leftById.get(pair.getKey());
                    V2Option right = rightById.get(pair.getValue());
                    return new PracticeAssessmentExcelService.ImportMatchingPairPreview(
                            pair.getKey(), left == null ? null : left.text(),
                            left == null ? null : left.imageReference(),
                            pair.getValue(), right == null ? null : right.text(),
                            right == null ? null : right.imageReference());
                })
                .toList();
        return new PracticeAssessmentExcelService.ImportRowDetail(
                row.skill(),
                row.groupInstruction(),
                "READING".equals(row.skill()) ? row.groupText() : null,
                "LISTENING".equals(row.skill()) ? row.groupText() : null,
                row.groupImage(),
                row.groupAudio(),
                row.questionImage(),
                row.questionAudio(),
                row.teacherNote(),
                options,
                pairs
        );
    }

    private PracticeAssessmentExcelService.ExcelPreview emptyPreview(
            List<PracticeAssessmentExcelService.ImportIssue> issues) {
        return new PracticeAssessmentExcelService.ExcelPreview(
                null, List.copyOf(issues), List.of(), 0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }

    private static String resolveMaterial(
            String raw,
            Map<String, V2Material> materials,
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String sheet,
            int row,
            String field,
            String rowKey) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return null;
        if (isManagedReference(value)) return value;
        V2Material material = materials.get(value);
        if (material == null) {
            rowWarning(issues, "MATERIAL_REFERENCE_UNRESOLVED", sheet, row, field,
                    "Không tìm thấy " + value + " trong 02_TAI_NGUYEN; cần gắn lại tài nguyên.", rowKey);
            return "material:" + value;
        }
        if (material.pendingUpload()) {
            rowWarning(issues, "ATTACHMENT_REQUIRED", sheet, row, field,
                    "Tài nguyên " + value + " cần được tải lên từ máy giáo viên sau khi nhập.", rowKey);
        }
        return material.managedReference() == null || material.managedReference().isBlank()
                ? "material:" + value : material.managedReference();
    }

    private static ScoringPolicyCode scoringPolicy(CanonicalQuestionType type, String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case MULTIPLE_CHOICE -> value.contains("PARTIAL")
                    ? ScoringPolicyCode.PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO
                    : ScoringPolicyCode.ALL_OR_NOTHING;
            case FILL_BLANK -> ScoringPolicyCode.NORMALIZED_EXACT;
            case MATCHING -> value.contains("ALL_OR_NOTHING")
                    ? ScoringPolicyCode.ALL_OR_NOTHING : ScoringPolicyCode.PER_PAIR;
            case ESSAY, SPEAKING -> ScoringPolicyCode.PROFILE_BASED;
            default -> ScoringPolicyCode.ALL_OR_NOTHING;
        };
    }

    private static String legacyAnswer(V2QuestionRow row) {
        if (row.type() == CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN) return nullToEmpty(row.correctValue());
        if (row.type() == CanonicalQuestionType.FILL_BLANK) {
            return row.blanks().isEmpty() ? "" : row.blanks().get(0).acceptedValues().get(0);
        }
        if (row.type() == CanonicalQuestionType.SINGLE_CHOICE
                || row.type() == CanonicalQuestionType.MULTIPLE_CHOICE) {
            List<String> indexes = new ArrayList<>();
            for (String letter : row.correctOptionLetters()) {
                for (int index = 0; index < row.options().size(); index++) {
                    if (row.options().get(index).letter().equals(letter)) indexes.add(String.valueOf(index + 1));
                }
            }
            return String.join(",", indexes);
        }
        return "";
    }

    private static String legacyAnswerType(CanonicalQuestionType type) {
        return switch (type) {
            case MULTIPLE_CHOICE -> "MULTIPLE";
            case TRUE_FALSE_NOT_GIVEN -> "TFNG";
            case FILL_BLANK -> "FILL_BLANK";
            case MATCHING -> "MATCHING";
            case ESSAY, SPEAKING -> "PROFILE";
            default -> "SINGLE";
        };
    }

    private static String essayTaskType(V2QuestionRow row) {
        String source = (nullToEmpty(row.extraAnswerSchema()) + " " + nullToEmpty(row.approvedRubric()))
                .toUpperCase(Locale.ROOT);
        for (String task : List.of("Q51", "Q52", "Q53", "Q54")) {
            if (source.contains(task)) return task;
        }
        return "GENERAL";
    }

    private static String stimulusType(V2QuestionRow row) {
        if ("READING".equals(row.skill()) && !row.groupText().isBlank()) return "READING_PASSAGE";
        if ("LISTENING".equals(row.skill()) && (!row.groupText().isBlank() || row.groupAudio() != null)) {
            return "LISTENING_AUDIO";
        }
        return "NONE";
    }

    private static boolean matchesTemplate(String workbookProgram,
                                           AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        String value = workbookProgram.trim().toUpperCase(Locale.ROOT);
        return value.equals(template.code().toUpperCase(Locale.ROOT))
                || value.equals(template.categoryCode().toUpperCase(Locale.ROOT))
                || value.equals(template.programCode().toUpperCase(Locale.ROOT));
    }

    private static String skillFromLesson(String lessonCode) {
        if (lessonCode == null || !LESSON_CODE.matcher(lessonCode).matches()) return null;
        return switch (lessonCode.charAt(0)) {
            case 'L' -> "LISTENING";
            case 'R' -> "READING";
            case 'W' -> "WRITING";
            case 'S' -> "SPEAKING";
            default -> null;
        };
    }

    private static int testNumber(String lessonCode) {
        try {
            return Integer.parseInt(lessonCode.substring(1));
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static int skillRank(String skill) {
        int index = SKILL_ORDER.indexOf(skill);
        return index < 0 ? SKILL_ORDER.size() : index;
    }

    private static String skillTitle(String skill) {
        return switch (skill) {
            case "LISTENING" -> "Phần Nghe";
            case "WRITING" -> "Phần Viết";
            case "SPEAKING" -> "Phần Nói";
            default -> "Phần Đọc";
        };
    }

    private static String profileCode(com.ksh.features.practice.assessment.ProfileReference profile) {
        return profile == null ? null : profile.code();
    }

    private static Integer profileVersion(com.ksh.features.practice.assessment.ProfileReference profile) {
        return profile == null ? null : profile.version();
    }

    private static String normalizeProfileRef(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith("_V1") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }

    private static String mediaSummary(V2QuestionRow row) {
        List<String> values = new ArrayList<>();
        if (row.groupImage() != null) values.add("Ảnh nhóm: " + row.groupImage());
        if (row.groupAudio() != null) values.add("Audio nhóm: " + row.groupAudio());
        if (row.questionImage() != null) values.add("Ảnh câu: " + row.questionImage());
        if (row.questionAudio() != null) values.add("Audio câu: " + row.questionAudio());
        long imageOptions = row.options().stream().filter(option -> option.imageReference() != null).count();
        if (imageOptions > 0) values.add(imageOptions + " ảnh option");
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static String optionSummary(List<V2Option> options) {
        if (options.isEmpty()) return "-";
        return options.stream().map(option -> option.letter() + (option.imageReference() == null ? "" : "[ảnh]"))
                .reduce((left, right) -> left + ", " + right).orElse("-");
    }

    private static int positiveInt(String raw) {
        try {
            int value = new BigDecimal(raw.trim()).intValueExact();
            return value > 0 ? value : 0;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static Integer nullablePositiveInt(String raw) {
        int value = positiveInt(raw);
        return value > 0 ? value : null;
    }

    private static BigDecimal positiveDecimal(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            return value.signum() > 0 ? value : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<String> split(String raw, String delimiter) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(Pattern.quote(delimiter)))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private static boolean isManagedReference(String source) {
        if (source == null) return false;
        String value = source.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("https://") || value.startsWith("http://")
                || value.startsWith("/practice/") || value.startsWith("material:");
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) return null;
        String normalized = path.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void nullable(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) node.putNull(field);
        else node.put(field, value);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static boolean hasBlockingForRow(
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String rowKey) {
        return issues.stream().anyMatch(issue -> rowKey.equals(issue.rowKey())
                && "BLOCKING".equals(issue.severity()));
    }

    private static boolean hasFatal(List<PracticeAssessmentExcelService.ImportIssue> issues) {
        Set<String> fatalCodes = Set.of(
                "SHEET_MISSING", "HEADER_INVALID", "SCHEMA_VERSION_UNSUPPORTED",
                "TEMPLATE_REQUIRED", "TEMPLATE_UNSUPPORTED", "TEMPLATE_MISMATCH",
                "MANIFEST_KEY_DUPLICATE", "NO_IMPORTABLE_QUESTIONS", "DRAFT_CONTRACT_INVALID");
        return issues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity())
                && issue.rowKey() == null && fatalCodes.contains(issue.code()));
    }

    private static void rowBlocking(
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String code,
            String sheet,
            int row,
            String field,
            String message,
            String rowKey) {
        issues.add(issue("BLOCKING", code, sheet, row, field, message, rowKey));
    }

    private static void rowBlockingOnce(
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String code,
            String sheet,
            int row,
            String field,
            String message,
            String rowKey) {
        if (issues.stream().noneMatch(issue -> code.equals(issue.code()) && rowKey.equals(issue.rowKey()))) {
            rowBlocking(issues, code, sheet, row, field, message, rowKey);
        }
    }

    private static void rowWarning(
            List<PracticeAssessmentExcelService.ImportIssue> issues,
            String code,
            String sheet,
            int row,
            String field,
            String message,
            String rowKey) {
        issues.add(issue("WARNING", code, sheet, row, field, message, rowKey));
    }

    private static PracticeAssessmentExcelService.ImportIssue issue(
            String severity,
            String code,
            String sheet,
            int row,
            String field,
            String message,
            String rowKey) {
        return new PracticeAssessmentExcelService.ImportIssue(
                severity, code, sheet, row, field, message, rowKey);
    }

    private static TemplateStyles styles(Workbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        bold.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = workbook.createCellStyle();
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setWrapText(true);
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        CellStyle note = workbook.createCellStyle();
        note.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        note.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        note.setWrapText(true);
        return new TemplateStyles(header, title, note);
    }

    private static void write(Sheet sheet, int rowIndex, CellStyle style, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            Object value = values[index];
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(value == null ? "" : value.toString());
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static void autosize(Sheet sheet, int columns, int maxCharacters) {
        for (int column = 0; column < columns; column++) {
            sheet.autoSizeColumn(column);
            sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column), maxCharacters * 256));
        }
    }

    private static boolean supportsType(AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                        CanonicalQuestionType type) {
        return template.skills().values().stream()
                .anyMatch(policy -> policy.excelImportEnabled() && policy.questionTypes().contains(type.name()));
    }

    private static String firstSkillForType(AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                            CanonicalQuestionType type) {
        return SKILL_ORDER.stream()
                .filter(skill -> template.skills().containsKey(skill))
                .filter(skill -> template.skills().get(skill).excelImportEnabled())
                .filter(skill -> template.skills().get(skill).questionTypes().contains(type.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question type is not enabled: " + type));
    }

    private static String allowedLessonExample(AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        List<String> codes = new ArrayList<>();
        for (int testNo = 1; testNo <= Math.min(template.maxTests(), 2); testNo++) {
            for (String skill : SKILL_ORDER) {
                AssessmentAuthoringCatalogService.SkillAuthoringPolicy policy = template.skills().get(skill);
                if (policy != null && policy.excelImportEnabled()) codes.add(skillPrefix(skill) + testNo);
            }
        }
        return String.join(",", codes);
    }

    private static String skillPrefix(String skill) {
        return switch (skill) {
            case "LISTENING" -> "L";
            case "WRITING" -> "W";
            case "SPEAKING" -> "S";
            default -> "R";
        };
    }

    private static Map<String, CanonicalQuestionType> questionSheets() {
        Map<String, CanonicalQuestionType> sheets = new LinkedHashMap<>();
        sheets.put("03_SINGLE_CHOICE", CanonicalQuestionType.SINGLE_CHOICE);
        sheets.put("04_MULTIPLE_CHOICE", CanonicalQuestionType.MULTIPLE_CHOICE);
        sheets.put("05_TRUE_FALSE_NG", CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN);
        sheets.put("06_FILL_BLANK", CanonicalQuestionType.FILL_BLANK);
        sheets.put("07_MATCHING", CanonicalQuestionType.MATCHING);
        sheets.put("08_ESSAY", CanonicalQuestionType.ESSAY);
        sheets.put("09_SPEAKING", CanonicalQuestionType.SPEAKING);
        return java.util.Collections.unmodifiableMap(sheets);
    }

    private record TemplateStyles(CellStyle header, CellStyle title, CellStyle note) {
    }

    private record V2Material(
            String ref,
            String level,
            String lessonCode,
            String groupCode,
            Integer questionNo,
            String type,
            String managedReference,
            String sourceFileName,
            String normalizedTargetHint,
            boolean pendingUpload,
            String teacherNote) {
    }

    private record V2Option(String letter, String text, String imageReference) {
    }

    private record V2MatchingItem(String id, String text, String imageReference) {
    }

    private record V2Blank(String id, List<String> acceptedValues) {
    }

    private record AnswerData(
            List<String> correctOptionLetters,
            String correctValue,
            List<V2Blank> blanks,
            Map<String, String> matchingPairs) {
        private static AnswerData empty() {
            return new AnswerData(List.of(), null, List.of(), Map.of());
        }
    }

    private record V2QuestionRow(
            int excelRow,
            String sheet,
            String rowKey,
            int testNo,
            String lessonCode,
            String groupCode,
            int questionNo,
            int questionNoInGroup,
            String skill,
            CanonicalQuestionType type,
            String groupInstruction,
            String groupText,
            String groupImage,
            String groupAudio,
            String prompt,
            String questionImage,
            String questionAudio,
            String correctAnswer,
            String explanation,
            BigDecimal points,
            ScoringPolicyCode scoringPolicy,
            List<V2Option> options,
            List<V2MatchingItem> matchingLeftItems,
            List<V2Blank> blanks,
            Map<String, String> matchingPairs,
            List<String> correctOptionLetters,
            String correctValue,
            AssessmentAuthoringCatalogService.QuestionAuthoringPolicy questionPolicy,
            String approvedRubric,
            String approvedPrompt,
            String extraAnswerSchema,
            String teacherNote) {
    }

    private static final class SheetReader {
        private final Sheet sheet;
        private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
        private final Map<String, Integer> columns = new LinkedHashMap<>();

        private SheetReader(Sheet sheet) {
            if (sheet == null) throw new IllegalArgumentException("Thiếu sheet dữ liệu.");
            this.sheet = sheet;
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Sheet " + sheet.getSheetName() + " thiếu header.");
            for (Cell cell : header) {
                String name = formatter.formatCellValue(cell).trim();
                if (!name.isBlank()) columns.put(name, cell.getColumnIndex());
            }
        }

        private String sheetName() {
            return sheet.getSheetName();
        }

        private String value(Row row, String column) {
            Integer index = columns.get(column);
            return index == null || row == null ? "" : formatter.formatCellValue(row.getCell(index)).trim();
        }

        private boolean blank(Row row) {
            if (row == null) return true;
            for (Cell cell : row) {
                if (!formatter.formatCellValue(cell).trim().isBlank()) return false;
            }
            return true;
        }
    }
}
