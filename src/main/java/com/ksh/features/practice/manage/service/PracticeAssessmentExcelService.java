package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateJson;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Quick Excel is the only writable spreadsheet contract. Advanced v2 and
 * Legacy v1 remain recognizable solely so callers receive deterministic
 * retirement errors instead of an accidental fallback.
 */
@Service
public class PracticeAssessmentExcelService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final Pattern MANAGED_MEDIA_URL = Pattern.compile(
            "^/practice/materials/(\\d+)/content$");
    private static final Set<String> LEGACY_REQUIRED_SHEETS = Set.of(
            "Manifest", "Sections", "Groups", "Questions", "OptionsAnswers");

    private final AssessmentAuthoringCatalogService catalogService;
    private final PracticeDraftContractService draftContractService;
    private final PracticeDraftRepository draftRepository;
    private final ObjectMapper objectMapper;
    private final PracticeAuthorizationService authorizationService;
    private final PracticeAuthoringCandidateService candidateService;
    private final PracticeAssessmentQuickExcelCodec quickCodec;

    public PracticeAssessmentExcelService(
            AssessmentAuthoringCatalogService catalogService,
            PracticeDraftContractService draftContractService,
            PracticeDraftRepository draftRepository,
            ObjectMapper objectMapper,
            PracticeAuthorizationService authorizationService,
            PracticeAuthoringCandidateService candidateService) {
        this.catalogService = catalogService;
        this.draftContractService = draftContractService;
        this.draftRepository = draftRepository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.candidateService = candidateService;
        this.quickCodec = new PracticeAssessmentQuickExcelCodec(
                objectMapper, catalogService);
    }

    public byte[] buildQuickTemplate() {
        return quickCodec.buildTemplate();
    }

    public ExcelPreview preview(
            MultipartFile file, ExcelImportContext context) {
        requireTarget(context);
        ParsedQuick parsed = parseQuick(validateAndRead(file), context);
        return quickPreview(parsed.result());
    }

    @Transactional
    public CandidateView createCandidate(
            MultipartFile file,
            ExcelImportContext context,
            Long actorId) {
        requireTarget(context);
        if (actorId == null || actorId < 1) {
            throw new IllegalArgumentException(
                    "Actor của Excel candidate là bắt buộc.");
        }
        if (candidateService == null) {
            throw new IllegalStateException(
                    "Authoring candidate service chưa được cấu hình.");
        }
        byte[] bytes = validateAndRead(file);
        ParsedQuick parsed = parseQuick(bytes, context);
        SourceSnapshot source = new SourceSnapshot(
                SourceKind.QUICK_EXCEL,
                SourceKind.QUICK_EXCEL.contractVersion(),
                "sha256:" + digest(bytes),
                "upload-1",
                normalizedFileName(file),
                SourceOperation.NONE,
                null);
        TargetRoute target = new TargetRoute(
                context.draft().getId(),
                context.testNo(),
                context.skill(),
                context.lessonCode());
        return candidateService.createOrReuse(new CreateCommand(
                actorId, source, target, parsed.result().groups()));
    }

    private ParsedQuick parseQuick(
            byte[] bytes, ExcelImportContext context) {
        PracticeAssessmentQuickExcelCodec.PackageInspection inspection =
                inspectPackage(bytes);
        failUnsafeQuickPackage(inspection);
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes))) {
            WorkbookFormat format = detect(workbook);
            if (format == WorkbookFormat.ADVANCED_V2) {
                throw new PracticeAssessmentExcelException(
                        "ADVANCED_EXCEL_V2_RETIRED",
                        "Advanced Excel v2 đã ngừng hỗ trợ. Hãy dùng Quick Excel cho nội dung văn bản và Editor cho media/MATCHING/complex blanks.");
            }
            if (format == WorkbookFormat.LEGACY_V1) {
                throw new PracticeAssessmentExcelException(
                        "LEGACY_EXCEL_V1_RETIRED",
                        "Legacy Excel v1 đã ngừng hỗ trợ. Hãy dùng mẫu Quick Excel hiện hành.");
            }
            PracticeAssessmentQuickExcelCodec.QuickParseResult result =
                    quickCodec.parse(workbook, context.skill(), inspection);
            validateQuickTargetAuthority(context, result.groups());
            return new ParsedQuick(result);
        } catch (PracticeAssessmentExcelException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAssessmentExcelException(
                    "WORKBOOK_SCHEMA_UNSUPPORTED",
                    "Không đọc được workbook Quick Excel v1.", exception);
        }
    }

    private WorkbookFormat detect(Workbook workbook) {
        if (quickCodec.hasIdentityMarker(workbook)) {
            return WorkbookFormat.QUICK_V1;
        }
        if (workbook.getSheet("01_THONG_TIN_SET") != null) {
            return WorkbookFormat.ADVANCED_V2;
        }
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            names.add(workbook.getSheetName(index));
        }
        if (names.equals(LEGACY_REQUIRED_SHEETS)) {
            return WorkbookFormat.LEGACY_V1;
        }
        throw new PracticeAssessmentExcelException(
                "WORKBOOK_SCHEMA_UNSUPPORTED",
                "Chỉ hỗ trợ workbook Quick Excel v1.");
    }

    private ExcelPreview quickPreview(
            PracticeAssessmentQuickExcelCodec.QuickParseResult parsed) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion",
                PracticeAssessmentQuickExcelCodec.CONTRACT_VERSION);
        root.set("groups", parsed.groups().deepCopy());
        return new ExcelPreview(
                writeJson(root),
                List.of(),
                parsed.rows(),
                1,
                parsed.groupCount(),
                parsed.questionCount(),
                parsed.questionCount(),
                0,
                0,
                parsed.totalPoints());
    }

    private void validateQuickTargetAuthority(
            ExcelImportContext context, JsonNode candidateGroups) {
        ObjectNode targetRoot = normalizedRoot(
                context.draft().getDraftJson(),
                context.draft().getCreationMethod());
        JsonNode targetSection = exactTargetSection(targetRoot, context);
        if ("LISTENING".equals(context.skill())) {
            String schema = targetSection.path("sectionDelivery")
                    .path("schemaVersion").asText("");
            String reference = targetSection.path("sectionDelivery")
                    .path("listeningDelivery")
                    .path("checkAudioReference").asText("");
            if (!"practice-section-delivery-v1".equals(schema)
                    || !MANAGED_MEDIA_URL.matcher(reference).matches()) {
                throw new PracticeAssessmentExcelException(
                        "LISTENING_CHECK_AUDIO_REQUIRED",
                        "Target Listening phải có check-audio authority trước khi dùng Quick Excel.");
            }
        }
        if ("WRITING".equals(context.skill())) {
            validateQuickWritingSimulation(targetSection, candidateGroups);
        }
    }

    private void validateQuickWritingSimulation(
            JsonNode targetSection, JsonNode candidateGroups) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> points = new LinkedHashMap<>();
        for (JsonNode group : targetSection.path("groups")) {
            collectWritingTasks(group.path("questions"), counts, points);
        }
        for (JsonNode group : candidateGroups) {
            collectWritingTasks(group.path("questions"), counts, points);
        }
        Map<String, BigDecimal> expected = Map.of(
                "Q51", BigDecimal.TEN,
                "Q52", BigDecimal.TEN,
                "Q53", BigDecimal.valueOf(30),
                "Q54", BigDecimal.valueOf(50));
        boolean valid = counts.keySet().equals(expected.keySet());
        for (Map.Entry<String, BigDecimal> entry : expected.entrySet()) {
            valid &= counts.getOrDefault(entry.getKey(), 0) == 1;
            valid &= entry.getValue().compareTo(
                    points.getOrDefault(
                            entry.getKey(), BigDecimal.ZERO)) == 0;
        }
        if (!valid) {
            throw new PracticeAssessmentExcelException(
                    "WRITING_TASK_CARDINALITY_INVALID",
                    "Section Writing mô phỏng sau apply phải có đúng Q51–Q54 với điểm 10/10/30/50.");
        }
    }

    private static void collectWritingTasks(
            JsonNode questions,
            Map<String, Integer> counts,
            Map<String, BigDecimal> points) {
        for (JsonNode question : questions) {
            String task = question.path("essayTaskType").asText("");
            if (task.isBlank()) continue;
            counts.merge(task, 1, Integer::sum);
            points.put(task, question.path("points").decimalValue());
        }
    }

    private JsonNode exactTargetSection(
            JsonNode root, ExcelImportContext context) {
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode section : root.path("sections")) {
            if (section.path("testNo").asInt() == context.testNo()
                    && context.skill().equalsIgnoreCase(
                    section.path("skill").asText())
                    && context.lessonCode().equalsIgnoreCase(
                    section.path("lessonCode").asText())) {
                matches.add(section);
            }
        }
        if (matches.size() != 1) {
            throw new PracticeAssessmentExcelException(
                    "CANDIDATE_TARGET_SECTION_NOT_FOUND",
                    "Workbook phải có đúng một section khớp route Test/skill/lesson.");
        }
        return matches.get(0);
    }

    @Transactional(readOnly = true)
    public PracticeDraft requireLinkedDraft(Long draftId, Long ownerId) {
        if (draftId == null) {
            throw new IllegalArgumentException(
                    "Nhập Excel phải được mở từ một bản nháp thủ công.");
        }
        if (authorizationService == null) {
            return draftRepository.findByIdAndOwnerId(draftId, ownerId)
                    .orElseThrow(() ->
                            new jakarta.persistence.EntityNotFoundException(
                                    "Bản nháp liên kết không tồn tại."));
        }
        authorizationService.requireDraft(
                draftId, ownerId, PracticeAction.EDIT);
        return draftRepository.findById(draftId)
                .orElseThrow(() ->
                        new jakarta.persistence.EntityNotFoundException(
                                "Bản nháp liên kết không tồn tại."));
    }

    @Transactional(readOnly = true)
    public ExcelImportContext requireExcelImportContext(
            Long draftId,
            Long ownerId,
            Integer testNo,
            String skill,
            String lessonCode) {
        PracticeDraft draft = requireLinkedDraft(draftId, ownerId);
        String routeSkill = skill == null
                ? "" : skill.trim().toUpperCase(Locale.ROOT);
        if (testNo == null || testNo <= 0
                || routeSkill.isBlank()
                || lessonCode == null || lessonCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Hãy mở Nhập Excel từ một phần kỹ năng trong editor.");
        }
        ObjectNode root = normalizedRoot(
                draft.getDraftJson(), draft.getCreationMethod());
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode section : root.path("sections")) {
            if (testNo == section.path("testNo").asInt()
                    && routeSkill.equalsIgnoreCase(
                    section.path("skill").asText())
                    && lessonCode.equalsIgnoreCase(
                    section.path("lessonCode").asText())) {
                matches.add(section);
            }
        }
        if (matches.size() != 1) {
            throw new PracticeAssessmentExcelException(
                    "CANDIDATE_TARGET_IDENTITY_MISMATCH",
                    "Không tìm thấy đúng một section khớp route draftId/Test/skill/lesson.");
        }
        if (!Set.of("READING", "LISTENING", "WRITING", "SPEAKING")
                .contains(routeSkill)) {
            throw new IllegalArgumentException(
                    "Kỹ năng của phần thi không hỗ trợ nhập Excel.");
        }
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template =
                catalogService.defaultTemplate();
        if (!template.requireSkill(routeSkill).excelImportEnabled()) {
            throw new IllegalArgumentException(
                    "Kỹ năng này không hỗ trợ nhập Excel.");
        }
        return new ExcelImportContext(
                draft,
                testNo,
                matches.get(0).path("lessonCode").asText(),
                routeSkill);
    }

    private ObjectNode normalizedRoot(String draftJson, String source) {
        try {
            PracticeDraftContractService.NormalizedDraft normalized =
                    draftContractService.normalize(draftJson, source);
            return (ObjectNode) objectMapper.readTree(normalized.json());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể chuẩn hóa bản nháp hiện tại.", exception);
        }
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file Excel.");
        }
        String name = file.getOriginalFilename();
        if (name == null
                || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new PracticeAssessmentExcelException(
                    "WORKBOOK_FILE_TYPE_INVALID",
                    "Chỉ chấp nhận file có đuôi .xlsx.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File Excel vượt quá 10MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                throw new IllegalArgumentException(
                        "File không phải định dạng XLSX hợp lệ.");
            }
            return bytes;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException(
                    "Không đọc được file Excel.", exception);
        }
    }

    private PracticeAssessmentQuickExcelCodec.PackageInspection inspectPackage(
            byte[] bytes) {
        boolean macro = false;
        boolean external = false;
        boolean quickMarker = false;
        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[8_192];
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw unsafePackage(
                            "Workbook có quá nhiều ZIP entries.");
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                macro |= name.endsWith("vbaproject.bin")
                        || name.contains("/activex/");
                external |= name.startsWith("xl/externallinks/");
                boolean quickIdentityXml = "xl/workbook.xml".equals(name)
                        || "xl/sharedstrings.xml".equals(name)
                        || (name.startsWith("xl/worksheets/")
                        && name.endsWith(".xml"));
                boolean inspectXml = name.endsWith(".rels")
                        || quickIdentityXml;
                ByteArrayOutputStream captured = inspectXml
                        ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw unsafePackage(
                                "Workbook vượt giới hạn dữ liệu giải nén.");
                    }
                    if (captured != null && captured.size() < 1_000_000) {
                        captured.write(buffer, 0, read);
                    }
                }
                if (captured != null) {
                    String xml = captured.toString(StandardCharsets.UTF_8);
                    external |= Pattern.compile(
                                    "TargetMode\\s*=\\s*['\"]External['\"]",
                                    Pattern.CASE_INSENSITIVE)
                            .matcher(xml).find();
                    quickMarker |= quickIdentityXml
                            && (xml.contains("QUICK_QUESTIONS")
                            || xml.contains("KSH_PRACTICE_QUICK_EXCEL")
                            || xml.contains("practice-quick-excel-v1"));
                }
                zip.closeEntry();
            }
        } catch (PracticeAssessmentExcelException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAssessmentExcelException(
                    "WORKBOOK_PACKAGE_UNSAFE",
                    "Không kiểm tra được package XLSX an toàn.", exception);
        }
        return new PracticeAssessmentQuickExcelCodec.PackageInspection(
                macro, external, quickMarker);
    }

    private static void failUnsafeQuickPackage(
            PracticeAssessmentQuickExcelCodec.PackageInspection inspection) {
        if (!inspection.quickMarker()) return;
        if (inspection.macro()) {
            throw new PracticeAssessmentExcelException(
                    "QUICK_MACRO_NOT_ALLOWED",
                    "Quick Excel không cho phép macro.");
        }
        if (inspection.externalLink()) {
            throw new PracticeAssessmentExcelException(
                    "QUICK_EXTERNAL_LINK_NOT_ALLOWED",
                    "Quick Excel không cho phép external link.");
        }
    }

    private static PracticeAssessmentExcelException unsafePackage(
            String message) {
        return new PracticeAssessmentExcelException(
                "WORKBOOK_PACKAGE_UNSAFE", message);
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể ghi JSON Quick Excel.", exception);
        }
    }

    private static void requireTarget(ExcelImportContext context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Route Excel target là bắt buộc.");
        }
    }

    private static String normalizedFileName(MultipartFile file) {
        String name = file == null ? "" : PracticeAuthoringCandidateJson
                .normalizedText(file.getOriginalFilename());
        if (name.isBlank() || name.length() > 255) {
            throw new PracticeAssessmentExcelException(
                    "WORKBOOK_FILE_NAME_INVALID",
                    "Tên file XLSX phải có từ 1 đến 255 ký tự.");
        }
        return name;
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte current : value) {
                result.append(Character.forDigit(
                        (current >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(current & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 không khả dụng.", exception);
        }
    }

    private static boolean hasBlocking(List<ImportIssue> issues) {
        return issues.stream().anyMatch(
                issue -> "BLOCKING".equals(issue.severity()));
    }

    private static boolean hasFatalBlocking(List<ImportIssue> issues) {
        return hasBlocking(issues);
    }

    public record ImportIssue(
            String severity,
            String code,
            String sheet,
            int row,
            String field,
            String message,
            String rowKey) {
    }

    public record ImportRowPreview(
            int row,
            String sheet,
            Integer testNo,
            String lessonCode,
            String groupCode,
            String questionNoInSection,
            Integer importedQuestionNo,
            ImportRowDetail detail,
            String questionId,
            String groupId,
            String questionType,
            String correctAnswer,
            String prompt,
            String teacherExplanation,
            String mediaSummary,
            String optionSummary,
            String status,
            boolean importable,
            List<String> messages) {
    }

    public record ImportRowDetail(
            String skill,
            String groupInstruction,
            String groupPassage,
            String groupTranscript,
            String groupImageReference,
            String groupAudioReference,
            String questionImageReference,
            String questionAudioReference,
            String teacherNote,
            List<ImportOptionPreview> options) {
        public ImportRowDetail {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record ImportOptionPreview(
            String label, String text, String imageReference) {
    }

    public record ExcelPreview(
            String draftJson,
            List<ImportIssue> issues,
            List<ImportRowPreview> rows,
            int sectionCount,
            int groupCount,
            int questionCount,
            int importableQuestionCount,
            int warningRowCount,
            int errorRowCount,
            BigDecimal totalPoints) {
        @com.fasterxml.jackson.annotation.JsonProperty("hasBlocking")
        public boolean hasBlocking() {
            return PracticeAssessmentExcelService.hasBlocking(issues);
        }

        @com.fasterxml.jackson.annotation.JsonProperty("hasFatalBlocking")
        public boolean hasFatalBlocking() {
            return PracticeAssessmentExcelService.hasFatalBlocking(issues);
        }

        @com.fasterxml.jackson.annotation.JsonProperty("canImport")
        public boolean canImport() {
            return !hasFatalBlocking()
                    && importableQuestionCount > 0
                    && draftJson != null;
        }
    }

    public record ExcelImportContext(
            PracticeDraft draft,
            Integer testNo,
            String lessonCode,
            String skill) {
    }

    private enum WorkbookFormat {
        QUICK_V1,
        ADVANCED_V2,
        LEGACY_V1
    }

    private record ParsedQuick(
            PracticeAssessmentQuickExcelCodec.QuickParseResult result) {
    }
}
