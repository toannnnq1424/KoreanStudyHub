package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PracticeAssessmentExcelService {

    public static final String SCHEMA_VERSION = "practice-excel-v2";
    private static final String LEGACY_SCHEMA_VERSION = "practice-excel-v1";
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_MEDIA_OVERRIDES = 200;
    private static final Pattern MANAGED_MEDIA_URL = Pattern.compile(
            "^/practice/materials/(\\d+)/content$");
    private static final Set<String> LEGACY_REQUIRED_SHEETS = Set.of(
            "Manifest", "Sections", "Groups", "Questions", "OptionsAnswers");
    private static final Set<String> FATAL_ISSUE_CODES = Set.of(
            "SHEET_MISSING",
            "SCHEMA_VERSION_UNSUPPORTED",
            "TEMPLATE_REQUIRED",
            "TEMPLATE_UNSUPPORTED",
            "TEMPLATE_MISMATCH",
            "MANIFEST_KEY_DUPLICATE",
            "SECTIONS_EMPTY",
            "NO_IMPORTABLE_QUESTIONS"
    );

    private final AssessmentAuthoringCatalogService catalogService;
    private final PracticeDraftContractService draftContractService;
    private final PracticeDraftValidator draftValidator;
    private final PracticeDraftRepository draftRepository;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver questionTypeResolver;
    private final ObjectMapper objectMapper;
    private final PracticeAssessmentExcelV2Codec v2Codec;
    private final PracticeAuthorizationService authorizationService;
    private final LecturerAssetService assetService;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeAssessmentExcelService(
            AssessmentAuthoringCatalogService catalogService,
            PracticeDraftContractService draftContractService,
            PracticeDraftValidator draftValidator,
            PracticeDraftRepository draftRepository,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver questionTypeResolver,
            ObjectMapper objectMapper,
            PracticeAuthorizationService authorizationService,
            LecturerAssetService assetService) {
        this.catalogService = catalogService;
        this.draftContractService = draftContractService;
        this.draftValidator = draftValidator;
        this.draftRepository = draftRepository;
        this.contractCodec = contractCodec;
        this.questionTypeResolver = questionTypeResolver;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.assetService = assetService;
        this.v2Codec = new PracticeAssessmentExcelV2Codec(
                draftContractService, draftValidator, contractCodec, objectMapper);
    }

    public PracticeAssessmentExcelService(
            AssessmentAuthoringCatalogService catalogService,
            PracticeDraftContractService draftContractService,
            PracticeDraftValidator draftValidator,
            PracticeDraftRepository draftRepository,
            AssessmentContractCodec contractCodec,
            QuestionTypeResolver questionTypeResolver,
            ObjectMapper objectMapper) {
        this(catalogService, draftContractService, draftValidator, draftRepository,
                contractCodec, questionTypeResolver, objectMapper, null, null);
    }

    public byte[] buildTemplate(String templateCode) {
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = catalogService.requireTemplate(templateCode);
        return v2Codec.buildTemplate(template);
    }

    public ExcelPreview preview(MultipartFile file, String requestedTemplateCode) {
        byte[] bytes = validateAndRead(file);
        List<ImportIssue> issues = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getSheet("01_THONG_TIN_SET") != null) {
                AssessmentAuthoringCatalogService.ExamTemplatePolicy requestedTemplate = null;
                if (requestedTemplateCode != null && !requestedTemplateCode.isBlank()) {
                    try {
                        requestedTemplate = catalogService.requireTemplate(requestedTemplateCode);
                    } catch (IllegalArgumentException exception) {
                        issues.add(blocking("TEMPLATE_UNSUPPORTED", "01_THONG_TIN_SET", 0,
                                "program_code", "Mẫu đề chưa được bật."));
                    }
                }
                return v2Codec.preview(workbook, requestedTemplate, issues);
            }
            for (String required : LEGACY_REQUIRED_SHEETS) {
                if (workbook.getSheet(required) == null) {
                    issues.add(blocking("SHEET_MISSING", required, 0, null,
                            "Thiếu sheet bắt buộc: " + required));
                }
            }
            if (hasBlocking(issues)) {
                return previewResult(null, issues, List.of(), 0, 0, BigDecimal.ZERO);
            }

            List<QuestionRowSeed> questionRows = readQuestionRows(workbook.getSheet("Questions"));

            Map<String, String> manifest = readManifest(workbook.getSheet("Manifest"), issues);
            String schemaVersion = manifest.getOrDefault("schemaVersion", "");
            if (!LEGACY_SCHEMA_VERSION.equals(schemaVersion)) {
                issues.add(blocking("SCHEMA_VERSION_UNSUPPORTED", "Manifest", 2, "schemaVersion",
                        "Phiên bản file Excel không được hỗ trợ."));
            }
            String templateCode = manifest.getOrDefault("examTemplateCode", requestedTemplateCode);
            if (templateCode == null || templateCode.isBlank()) {
                issues.add(blocking("TEMPLATE_REQUIRED", "Manifest", 3, "examTemplateCode",
                        "Thiếu mã mẫu đề."));
                return previewResult(null, issues, questionRows, 0, 0, BigDecimal.ZERO);
            }
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template;
            try {
                template = catalogService.requireTemplate(templateCode);
            } catch (IllegalArgumentException exception) {
                issues.add(blocking("TEMPLATE_UNSUPPORTED", "Manifest", 3, "examTemplateCode",
                        "Mẫu đề chưa được bật."));
                return previewResult(null, issues, questionRows, 0, 0, BigDecimal.ZERO);
            }
            if (requestedTemplateCode != null && !requestedTemplateCode.isBlank()
                    && !template.code().equalsIgnoreCase(requestedTemplateCode)) {
                issues.add(blocking("TEMPLATE_MISMATCH", "Manifest", 3, "examTemplateCode",
                        "File Excel không thuộc mẫu đề đã chọn."));
            }

            Map<String, SectionBuilder> sections = readSections(workbook.getSheet("Sections"), template, issues);
            Map<String, GroupBuilder> groups = readGroups(workbook.getSheet("Groups"), sections, issues);
            pruneUnusedTemplateSections(sections, issues);
            Map<String, QuestionBuilder> questions = readQuestions(
                    workbook.getSheet("Questions"), groups, template, issues);
            readAnswers(workbook.getSheet("OptionsAnswers"), questions, issues);

            Map<String, Integer> sourceRows = new LinkedHashMap<>();
            questionRows.forEach(row -> sourceRows.putIfAbsent(row.rowKey, row.row));
            Set<String> rejectedQuestionKeys = blockingQuestionKeys(issues);
            DraftBuild finalBuild = null;
            String importableDraftJson = null;
            int maxPasses = Math.max(questionRows.size() + 2, 3);

            for (int pass = 0; pass < maxPasses; pass++) {
                DraftBuild build = buildDraft(manifest, template, sections, rejectedQuestionKeys, issues);
                if (rejectedQuestionKeys.addAll(blockingQuestionKeys(issues))) {
                    continue;
                }
                finalBuild = build;
                if (build.questionCount == 0) {
                    addUnique(issues, blocking("NO_IMPORTABLE_QUESTIONS", "Questions", 0, null,
                            "File không còn câu hỏi hợp lệ để nhập."));
                    break;
                }

                PracticeDraftContractService.NormalizedDraft normalized =
                        draftContractService.normalize(build.root, "EXCEL");
                ObjectNode normalizedRoot = (ObjectNode) objectMapper.readTree(normalized.json());
                PracticeDraftValidator.ValidationResult validation = draftValidator.validate(normalized.json());
                Set<String> newlyRejected = collectValidationIssues(
                        validation, normalizedRoot, sourceRows, issues);
                if (rejectedQuestionKeys.addAll(newlyRejected)) {
                    continue;
                }

                finalBuild = summarizeDraft(normalizedRoot);
                if (!hasFatalBlocking(issues)) {
                    importableDraftJson = normalized.json();
                }
                break;
            }

            if (finalBuild == null) {
                finalBuild = new DraftBuild(objectMapper.createObjectNode(), 0, 0, 0, BigDecimal.ZERO);
            }
            if (hasFatalBlocking(issues)) {
                importableDraftJson = null;
            }
            return previewResult(importableDraftJson, issues, questionRows,
                    finalBuild.sectionCount, finalBuild.groupCount, finalBuild.totalPoints);
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Không đọc được nội dung file Excel.", exception);
        }
    }

    @Transactional
    public PracticeDraft importDraft(MultipartFile file,
                                     String requestedTemplateCode,
                                     Long linkedDraftId,
                                     Long ownerId) {
        return importDraft(file, requestedTemplateCode, linkedDraftId, ownerId, null);
    }

    @Transactional
    public PracticeDraft importDraft(MultipartFile file,
                                     String requestedTemplateCode,
                                     Long linkedDraftId,
                                     Long ownerId,
                                     String mediaOverridesJson) {
        ExcelPreview preview = preview(file, requestedTemplateCode);
        if (!preview.canImport()) {
            throw new IllegalArgumentException("File Excel không còn dòng hợp lệ để nhập hoặc có lỗi cấp file.");
        }
        ObjectNode root;
        try {
            root = (ObjectNode) objectMapper.readTree(preview.draftJson());
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo draft từ Excel.", exception);
        }
        applyMediaOverrides(root, parseMediaOverrides(mediaOverridesJson));
        PracticeDraft draft;
        String finalJson = writeJson(root);
        if (linkedDraftId == null) {
            if (authorizationService != null) {
                authorizationService.requireGlobal(ownerId, PracticeAction.CREATE);
            }
            String title = root.path("document").path("title").asText("Bộ đề nhập từ Excel");
            String description = root.path("document").path("description").asText("");
            String category = root.path("document").path("detectedCategory").asText("CUSTOM");
            draft = new PracticeDraft(title, description, category, "GLOBAL", null, "DRAFT", ownerId,
                    finalJson);
            draft.setCreationMethod("EXCEL");
        } else {
            draft = requireLinkedDraft(linkedDraftId, ownerId);
            String importedTemplate = root.path("document").path("examTemplateCode").asText("");
            if (draft.getExamTemplateCode() != null
                    && !draft.getExamTemplateCode().equalsIgnoreCase(importedTemplate)) {
                throw new IllegalArgumentException("File Excel không thuộc mẫu đề của bản nháp hiện tại.");
            }
            ObjectNode existing = normalizedRoot(draft.getDraftJson(), draft.getCreationMethod());
            root = mergeImportedLessons(existing, root);
            PracticeDraftContractService.NormalizedDraft normalized =
                    draftContractService.normalize(root, "EXCEL");
            finalJson = normalized.json();
            try {
                root = (ObjectNode) objectMapper.readTree(finalJson);
            } catch (Exception exception) {
                throw new IllegalStateException("Không thể đọc draft sau khi gộp Excel.", exception);
            }
        }
        draft.setCategory(root.path("document").path("detectedCategory").asText(draft.getCategory()));
        draft.setDraftJson(finalJson);
        draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
        draft.setAssessmentProgramCode(root.path("document").path("assessmentProgramCode").asText());
        draft.setAssessmentProgramVersionId(root.path("document").path("assessmentProgramVersionId").longValue());
        draft.setExamTemplateCode(root.path("document").path("examTemplateCode").asText());
        PracticeDraft saved = draftRepository.save(draft);
        linkManagedMedia(saved.getId(), ownerId, parseMediaOverrides(mediaOverridesJson));
        return saved;
    }

    private Map<String, String> parseMediaOverrides(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return Map.of();
        try {
            JsonNode value = objectMapper.readTree(rawJson);
            if (!value.isObject() || value.size() > MAX_MEDIA_OVERRIDES) {
                throw new IllegalArgumentException("Danh sách tài nguyên Excel không hợp lệ.");
            }
            Map<String, String> result = new LinkedHashMap<>();
            value.fields().forEachRemaining(entry -> {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                String url = entry.getValue().isTextual() ? entry.getValue().asText().trim() : "";
                if (key.startsWith("material:")) key = key.substring("material:".length()).trim();
                if (key.isBlank() || key.length() > 200 || !MANAGED_MEDIA_URL.matcher(url).matches()) {
                    throw new IllegalArgumentException("Liên kết tài nguyên Excel không hợp lệ.");
                }
                result.put(key, url);
            });
            return Map.copyOf(result);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Danh sách tài nguyên Excel không hợp lệ.", exception);
        }
    }

    private void applyMediaOverrides(ObjectNode root, Map<String, String> overrides) {
        if (overrides.isEmpty()) return;
        Set<String> referenced = new LinkedHashSet<>();
        collectPendingMediaReferences(root, referenced);
        Map<String, String> applicable = new LinkedHashMap<>();
        overrides.forEach((ref, url) -> {
            if (referenced.contains(ref)) applicable.put(ref, url);
        });
        if (applicable.isEmpty()) return;

        replacePendingMediaReferences(root, applicable);
        for (JsonNode value : root.path("materials")) {
            if (!(value instanceof ObjectNode material)) continue;
            String ref = material.path("materialRef").asText("");
            String url = applicable.get(ref);
            if (url == null) continue;
            String type = material.path("type").asText("").toUpperCase(Locale.ROOT);
            if (!url.matches("/practice/materials/\\d+/content")) {
                throw new IllegalArgumentException("Tệp media không đúng loại tài nguyên trong Excel.");
            }
            material.put("managedReference", url);
            material.put("pendingUpload", false);
        }
    }

    private void collectPendingMediaReferences(JsonNode node, Set<String> references) {
        if (node == null) return;
        if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith("material:") && value.length() > "material:".length()) {
                references.add(value.substring("material:".length()));
            }
            return;
        }
        if (node.isContainerNode()) node.forEach(child -> collectPendingMediaReferences(child, references));
    }

    private void replacePendingMediaReferences(JsonNode node, Map<String, String> overrides) {
        if (node instanceof ObjectNode object) {
            List<String> fieldNames = new ArrayList<>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode child = object.get(fieldName);
                if (child != null && child.isTextual() && child.asText().startsWith("material:")) {
                    String replacement = overrides.get(child.asText().substring("material:".length()));
                    if (replacement != null) object.put(fieldName, replacement);
                } else {
                    replacePendingMediaReferences(child, overrides);
                }
            }
            return;
        }
        if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                JsonNode child = array.get(index);
                if (child.isTextual() && child.asText().startsWith("material:")) {
                    String replacement = overrides.get(child.asText().substring("material:".length()));
                    if (replacement != null) array.set(index, objectMapper.getNodeFactory().textNode(replacement));
                } else {
                    replacePendingMediaReferences(child, overrides);
                }
            }
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể lưu tài nguyên vào draft Excel.", exception);
        }
    }

    @Transactional(readOnly = true)
    public PracticeDraft requireLinkedDraft(Long draftId, Long ownerId) {
        if (draftId == null) {
            throw new IllegalArgumentException("Nhập Excel phải được mở từ một bản nháp thủ công.");
        }
        if (authorizationService == null) {
            return draftRepository.findByIdAndOwnerId(draftId, ownerId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Bản nháp liên kết không tồn tại."));
        }
        authorizationService.requireDraft(draftId, ownerId, PracticeAction.EDIT, null);
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản nháp liên kết không tồn tại."));
    }

    @Transactional(readOnly = true)
    public ExcelImportContext requireExcelImportContext(Long draftId,
                                                        Long ownerId,
                                                        Integer testNo,
                                                        String lessonCode) {
        PracticeDraft draft = requireLinkedDraft(draftId, ownerId);
        if (testNo == null || testNo <= 0 || lessonCode == null || lessonCode.isBlank()) {
            throw new IllegalArgumentException("Hãy mở Nhập Excel từ một phần kỹ năng trong editor.");
        }
        ObjectNode root = normalizedRoot(draft.getDraftJson(), draft.getCreationMethod());
        JsonNode selected = null;
        for (JsonNode section : root.path("sections")) {
            if (testNo == section.path("testNo").asInt()
                    && lessonCode.equalsIgnoreCase(section.path("lessonCode").asText())) {
                selected = section;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Không tìm thấy phần thi được chọn trong bản nháp.");
        }
        String skill = selected.path("skill").asText("").toUpperCase(Locale.ROOT);
        if (!Set.of("READING", "LISTENING", "WRITING", "SPEAKING").contains(skill)) {
            throw new IllegalArgumentException("Kỹ năng của phần thi không hỗ trợ nhập Excel.");
        }
        String templateCode = draft.getExamTemplateCode();
        if (templateCode == null || templateCode.isBlank()) {
            templateCode = root.path("document").path("examTemplateCode").asText("");
        }
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = catalogService.requireTemplate(templateCode);
        if (!template.requireSkill(skill).excelImportEnabled()) {
            throw new IllegalArgumentException("Mẫu đề đã khóa nhập Excel cho kỹ năng này.");
        }
        return new ExcelImportContext(draft, template.code(), testNo,
                selected.path("lessonCode").asText(), skill);
    }

    private ObjectNode normalizedRoot(String draftJson, String source) {
        try {
            PracticeDraftContractService.NormalizedDraft normalized =
                    draftContractService.normalize(draftJson, source);
            return (ObjectNode) objectMapper.readTree(normalized.json());
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể chuẩn hóa bản nháp hiện tại.", exception);
        }
    }

    private void linkManagedMedia(Long draftId, Long actorId,
                                  Map<String, String> overrides) {
        if (assetService == null || overrides.isEmpty()) return;
        Set<Long> linked = new LinkedHashSet<>();
        for (String url : overrides.values()) {
            java.util.regex.Matcher matcher = MANAGED_MEDIA_URL.matcher(url);
            if (!matcher.matches()) continue;
            Long assetId = Long.valueOf(matcher.group(1));
            if (!linked.add(assetId)) continue;
            assetService.linkAssetToDraft(draftId, assetId, actorId,
                    null, null, null, "EXCEL_MEDIA", null);
        }
    }

    private ObjectNode mergeImportedLessons(ObjectNode existing, ObjectNode imported) {
        ObjectNode existingDocument = existing.with("document");
        JsonNode importedDocument = imported.path("document");
        for (String field : List.of("detectedCategory", "assessmentProgramCode",
                "assessmentProgramVersionId", "assessmentProgramVersion", "examTemplateCode")) {
            if (importedDocument.has(field)) existingDocument.set(field, importedDocument.get(field).deepCopy());
        }

        ArrayNode existingTests = existing.withArray("tests");
        Map<Integer, ObjectNode> testsByNumber = new LinkedHashMap<>();
        for (JsonNode value : existingTests) {
            if (value instanceof ObjectNode test) testsByNumber.put(test.path("testNo").asInt(), test);
        }
        for (JsonNode value : imported.path("tests")) {
            if (!(value instanceof ObjectNode importedTest)) continue;
            int testNo = importedTest.path("testNo").asInt();
            testsByNumber.computeIfAbsent(testNo, ignored -> {
                ObjectNode copy = importedTest.deepCopy();
                existingTests.add(copy);
                return copy;
            });
        }

        ArrayNode existingSections = existing.withArray("sections");
        for (JsonNode value : imported.path("sections")) {
            if (!(value instanceof ObjectNode importedSection)) continue;
            ObjectNode copy = importedSection.deepCopy();
            int testNo = copy.path("testNo").asInt(1);
            ObjectNode targetTest = testsByNumber.get(testNo);
            if (targetTest == null) continue;
            copy.put("testClientId", targetTest.path("clientId").asText());
            String lessonCode = copy.path("lessonCode").asText("");
            int existingIndex = -1;
            for (int index = 0; index < existingSections.size(); index++) {
                if (lessonCode.equalsIgnoreCase(existingSections.get(index).path("lessonCode").asText())) {
                    existingIndex = index;
                    break;
                }
            }
            if (existingIndex >= 0) existingSections.set(existingIndex, copy);
            else existingSections.add(copy);
        }

        ArrayNode existingMaterials = existing.withArray("materials");
        Map<String, Integer> materialIndexes = new LinkedHashMap<>();
        for (int index = 0; index < existingMaterials.size(); index++) {
            materialIndexes.put(existingMaterials.get(index).path("materialRef").asText(), index);
        }
        for (JsonNode material : imported.path("materials")) {
            String ref = material.path("materialRef").asText("");
            Integer index = materialIndexes.get(ref);
            if (index == null || ref.isBlank()) {
                existingMaterials.add(material.deepCopy());
                if (!ref.isBlank()) materialIndexes.put(ref, existingMaterials.size() - 1);
            } else {
                existingMaterials.set(index, material.deepCopy());
            }
        }
        existing.put("schemaVersion", PracticeDraftContractService.SCHEMA_VERSION);
        return existing;
    }

    private DraftBuild buildDraft(Map<String, String> manifest,
                                  AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                  Map<String, SectionBuilder> sections,
                                  Set<String> rejectedQuestionKeys,
                                  List<ImportIssue> issues) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", PracticeDraftContractService.SCHEMA_VERSION);
        ObjectNode document = root.putObject("document");
        document.put("title", manifest.getOrDefault("title", "Bộ đề nhập từ Excel"));
        document.put("description", manifest.getOrDefault("description", ""));
        document.put("detectedCategory", template.categoryCode());
        document.put("assessmentProgramCode", template.programCode());
        document.put("assessmentProgramVersionId", template.programVersionId());
        document.put("assessmentProgramVersion", template.programVersion());
        document.put("examTemplateCode", template.code());
        ArrayNode sectionsNode = root.putArray("sections");
        root.putArray("warnings");

        int sectionCount = 0;
        int groupCount = 0;
        int questionCount = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (SectionBuilder section : sections.values()) {
            List<GroupBuilder> importableGroups = section.groups.stream()
                    .filter(group -> group.questions.stream()
                            .anyMatch(question -> !rejectedQuestionKeys.contains(question.id)))
                    .toList();
            if (importableGroups.isEmpty()) continue;

            sectionCount++;
            ObjectNode sectionNode = sectionsNode.addObject();
            sectionNode.put("clientId", section.id);
            sectionNode.put("title", section.title);
            sectionNode.put("skill", section.skill);
            sectionNode.put("durationMinutes", section.durationMinutes);
            ArrayNode groupNodes = sectionNode.putArray("groups");
            for (GroupBuilder group : importableGroups) {
                groupCount++;
                ObjectNode groupNode = groupNodes.addObject();
                groupNode.put("clientId", group.id);
                groupNode.put("label", group.label);
                groupNode.put("instruction", group.instruction);
                groupNode.put("passageText", "LISTENING_AUDIO".equals(group.stimulusType)
                        ? group.transcriptText : group.passageText);
                groupNode.put("transcriptText", group.transcriptText);
                groupNode.put("stimulusKind", "READING_PASSAGE".equals(group.stimulusType)
                        ? "PASSAGE" : ("LISTENING_AUDIO".equals(group.stimulusType) ? "TRANSCRIPT" : "NONE"));
                groupNode.put("audioUrl", group.audioUrl);
                groupNode.put("imageUrl", group.imageUrl);
                ObjectNode stimulus = groupNode.putObject("stimulus");
                stimulus.put("schemaVersion", PracticeDraftContractService.STIMULUS_SCHEMA_VERSION);
                stimulus.put("type", group.stimulusType);
                stimulus.put("instruction", group.instruction);
                nullable(stimulus, "passageText", group.passageText);
                nullable(stimulus, "transcriptText", group.transcriptText);
                nullable(stimulus, "mediaReference", group.audioUrl);
                nullable(stimulus, "imageReference", group.imageUrl);
                ObjectNode provenance = stimulus.putObject("provenance");
                provenance.put("source", "EXCEL");
                provenance.put("approved", true);
                provenance.putArray("sourceRegionIds");
                groupNode.putArray("sourceRegionIds");

                ArrayNode questionNodes = groupNode.putArray("questions");
                for (QuestionBuilder question : group.questions) {
                    if (rejectedQuestionKeys.contains(question.id)) continue;
                    questionCount++;
                    totalPoints = totalPoints.add(question.points);
                    questionNodes.add(buildQuestion(question, issues));
                }
            }
        }
        return new DraftBuild(root, sectionCount, groupCount, questionCount, totalPoints);
    }

    private DraftBuild summarizeDraft(ObjectNode root) {
        int sectionCount = 0;
        int groupCount = 0;
        int questionCount = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;
        JsonNode sections = root.path("sections");
        if (sections.isArray()) {
            sectionCount = sections.size();
            for (JsonNode section : sections) {
                JsonNode groups = section.path("groups");
                if (!groups.isArray()) continue;
                groupCount += groups.size();
                for (JsonNode group : groups) {
                    JsonNode questions = group.path("questions");
                    if (!questions.isArray()) continue;
                    questionCount += questions.size();
                    for (JsonNode question : questions) {
                        totalPoints = totalPoints.add(question.path("points").decimalValue());
                    }
                }
            }
        }
        return new DraftBuild(root, sectionCount, groupCount, questionCount, totalPoints);
    }

    private Set<String> collectValidationIssues(PracticeDraftValidator.ValidationResult validation,
                                                ObjectNode normalizedRoot,
                                                Map<String, Integer> sourceRows,
                                                List<ImportIssue> issues) {
        Set<String> rejected = new LinkedHashSet<>();
        for (PracticeDraftValidator.ValidationMsg message : validation.messages()) {
            List<String> questionKeys = affectedQuestionKeys(normalizedRoot, message);
            if (questionKeys.isEmpty()) {
                addUnique(issues, new ImportIssue(
                        message.type(), message.code(), "Draft", 0, null, message.content(), null));
                continue;
            }
            for (String questionKey : questionKeys) {
                int sourceRow = sourceRows.getOrDefault(questionKey, 0);
                addUnique(issues, new ImportIssue(
                        message.type(), message.code(), "Questions", sourceRow, null,
                        message.content(), questionKey));
                if ("BLOCKING".equals(message.type())) rejected.add(questionKey);
            }
        }
        return rejected;
    }

    private static List<String> affectedQuestionKeys(ObjectNode root,
                                                     PracticeDraftValidator.ValidationMsg message) {
        if (message.sIdx() == null) return List.of();
        JsonNode sections = root.path("sections");
        if (!sections.isArray() || message.sIdx() < 0 || message.sIdx() >= sections.size()) return List.of();
        JsonNode section = sections.get(message.sIdx());
        if (message.gIdx() == null) return questionKeys(section.path("groups"));
        JsonNode groups = section.path("groups");
        if (!groups.isArray() || message.gIdx() < 0 || message.gIdx() >= groups.size()) return List.of();
        JsonNode group = groups.get(message.gIdx());
        if (message.qIdx() == null) return questionKeys(group.path("questions"));
        JsonNode questions = group.path("questions");
        if (!questions.isArray() || message.qIdx() < 0 || message.qIdx() >= questions.size()) return List.of();
        String questionKey = questions.get(message.qIdx()).path("clientId").asText("");
        return questionKey.isBlank() ? List.of() : List.of(questionKey);
    }

    private static List<String> questionKeys(JsonNode containersOrQuestions) {
        List<String> result = new ArrayList<>();
        if (!containersOrQuestions.isArray()) return result;
        for (JsonNode item : containersOrQuestions) {
            JsonNode questions = item.has("questions") ? item.path("questions") : containersOrQuestions;
            if (questions.isArray()) {
                for (JsonNode question : questions) {
                    String key = question.path("clientId").asText("");
                    if (!key.isBlank()) result.add(key);
                }
            }
            if (!item.has("questions")) break;
        }
        return result;
    }

    private ExcelPreview previewResult(String draftJson,
                                       List<ImportIssue> issues,
                                       List<QuestionRowSeed> sourceRows,
                                       int sectionCount,
                                       int groupCount,
                                       BigDecimal totalPoints) {
        boolean fatal = hasFatalBlocking(issues);
        Set<String> importedQuestionKeys = fatal || draftJson == null
                ? Set.of() : importedQuestionKeys(draftJson);
        List<ImportRowPreview> rows = new ArrayList<>();
        int importableCount = 0;
        int warningCount = 0;
        int errorCount = 0;

        for (QuestionRowSeed source : sourceRows) {
            List<ImportIssue> rowIssues = issues.stream()
                    .filter(issue -> source.rowKey.equals(issue.rowKey)
                            || (issue.rowKey == null && "Questions".equals(issue.sheet) && issue.row == source.row))
                    .toList();
            boolean hasError = rowIssues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity));
            boolean hasWarning = rowIssues.stream().anyMatch(issue -> "WARNING".equals(issue.severity));
            String status = hasError ? "ERROR" : (hasWarning ? "WARNING" : "VALID");
            boolean importable = !fatal && !hasError && importedQuestionKeys.contains(source.rowKey);
            if (importable) importableCount++;
            if (hasError) errorCount++;
            else if (hasWarning) warningCount++;
            rows.add(new ImportRowPreview(
                    source.row,
                    source.questionId,
                    source.questionNo,
                    source.groupId,
                    source.questionType,
                    source.prompt,
                    status,
                    importable,
                    rowIssues.stream().map(ImportIssue::message).distinct().toList()
            ));
        }
        return new ExcelPreview(
                draftJson,
                List.copyOf(issues),
                List.copyOf(rows),
                sectionCount,
                groupCount,
                sourceRows.size(),
                importableCount,
                warningCount,
                errorCount,
                totalPoints
        );
    }

    private Set<String> importedQuestionKeys(String draftJson) {
        Set<String> result = new LinkedHashSet<>();
        try {
            JsonNode sections = objectMapper.readTree(draftJson).path("sections");
            for (JsonNode section : sections) {
                for (JsonNode group : section.path("groups")) {
                    for (JsonNode question : group.path("questions")) {
                        String key = question.path("clientId").asText("");
                        if (!key.isBlank()) result.add(key);
                    }
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc bản nháp Excel đã chuẩn hóa.", exception);
        }
        return result;
    }

    private static Set<String> blockingQuestionKeys(List<ImportIssue> issues) {
        Set<String> result = new LinkedHashSet<>();
        issues.stream()
                .filter(issue -> "BLOCKING".equals(issue.severity))
                .map(ImportIssue::rowKey)
                .filter(key -> key != null && !key.isBlank())
                .forEach(result::add);
        return result;
    }

    private static void addUnique(List<ImportIssue> issues, ImportIssue issue) {
        if (!issues.contains(issue)) issues.add(issue);
    }

    private ObjectNode buildQuestion(QuestionBuilder q, List<ImportIssue> issues) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("clientId", q.id);
        node.put("questionNo", q.questionNo);
        node.put("questionType", q.type.name());
        node.put("canonicalQuestionType", q.type.name());
        node.put("prompt", q.prompt);
        node.put("points", q.points);
        node.put("explanationVi", q.explanationVi);
        if (!q.essayTaskType.isBlank()) node.put("essayTaskType", q.essayTaskType);
        if (q.prepTimeSeconds != null) node.put("prepTimeSeconds", q.prepTimeSeconds);
        if (q.responseTimeSeconds != null) node.put("respTimeSeconds", q.responseTimeSeconds);
        node.putArray("sourceRegionIds");

        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                q.options.stream().map(option -> new QuestionContent.Option(option.id, option.text)).toList(),
                q.matching.stream().map(pair -> new QuestionContent.Item(pair.leftId, pair.leftText)).toList(),
                uniqueRightItems(q.matching),
                q.blanks.stream().map(blank -> new QuestionContent.Blank(blank.id, blank.prompt)).toList()
        );
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                q.type,
                q.options.stream().filter(option -> option.correct).map(option -> option.id).toList(),
                blankToNull(q.correctValue),
                q.blanks.stream().map(blank -> new AnswerSpec.BlankAnswer(blank.id, blank.acceptedValues)).toList(),
                matchingMap(q.matching),
                q.scoringPolicy,
                null, null, null
        );
        try {
            node.set("questionContent", objectMapper.readTree(contractCodec.writeQuestionContent(content, q.type)));
            node.set("answerSpec", objectMapper.readTree(contractCodec.writeAnswerSpec(spec, content)));
        } catch (Exception exception) {
            issues.add(blocking("ANSWER_SPEC_INVALID", "OptionsAnswers", q.sourceRow, null,
                    "Cấu hình đáp án của câu " + q.questionNo + " không hợp lệ.", q.id));
        }

        ArrayNode options = node.putArray("options");
        q.options.forEach(option -> {
            ObjectNode optionNode = options.addObject();
            optionNode.put("id", option.id);
            optionNode.put("text", option.text);
        });
        String legacyAnswer = legacyAnswer(q);
        node.put("answerKey", legacyAnswer);
        ObjectNode answer = node.putObject("answer");
        answer.put("type", q.type == CanonicalQuestionType.MULTIPLE_CHOICE ? "MULTIPLE" : "SINGLE");
        answer.put("value", legacyAnswer);
        return node;
    }

    private List<QuestionRowSeed> readQuestionRows(Sheet sheet) {
        SheetReader rows = new SheetReader(sheet);
        List<QuestionRowSeed> result = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (rows.blank(row)) continue;
            int sourceRow = rowIndex + 1;
            String questionId = rows.value(row, "questionId");
            result.add(new QuestionRowSeed(
                    sourceRow,
                    questionRowKey(questionId, sourceRow),
                    questionId,
                    rows.value(row, "questionNo"),
                    rows.value(row, "groupId"),
                    rows.value(row, "questionType"),
                    rows.value(row, "prompt")
            ));
        }
        return result;
    }

    private Map<String, SectionBuilder> readSections(
            Sheet sheet,
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            List<ImportIssue> issues) {
        SheetReader rows = new SheetReader(sheet);
        Map<String, SectionBuilder> result = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (rows.blank(row)) continue;
            String id = rows.value(row, "sectionId");
            String skill = rows.value(row, "skill").toUpperCase(Locale.ROOT);
            if (id.isBlank() || result.containsKey(id)) {
                issues.add(blocking("SECTION_ID_INVALID", sheet.getSheetName(), rowIndex + 1, "sectionId",
                        "sectionId trống hoặc bị trùng."));
                continue;
            }
            AssessmentAuthoringCatalogService.SkillAuthoringPolicy policy;
            try {
                policy = template.requireSkill(skill);
            } catch (IllegalArgumentException exception) {
                issues.add(blocking("SKILL_NOT_ALLOWED_BY_TEMPLATE", sheet.getSheetName(), rowIndex + 1, "skill",
                        "Kỹ năng không thuộc mẫu đề."));
                continue;
            }
            int duration = positiveInt(rows.value(row, "durationMinutes"), policy.durationMinutes(),
                    issues, sheet.getSheetName(), rowIndex + 1, "durationMinutes");
            result.put(id, new SectionBuilder(id, defaultText(rows.value(row, "title"), skill), skill, duration));
        }
        if (result.isEmpty()) {
            issues.add(blocking("SECTIONS_EMPTY", sheet.getSheetName(), 0, null, "File chưa có phần thi hợp lệ."));
        }
        return result;
    }

    private Map<String, GroupBuilder> readGroups(
            Sheet sheet, Map<String, SectionBuilder> sections, List<ImportIssue> issues) {
        SheetReader rows = new SheetReader(sheet);
        Map<String, GroupBuilder> result = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (rows.blank(row)) continue;
            String id = rows.value(row, "groupId");
            String sectionId = rows.value(row, "sectionId");
            SectionBuilder section = sections.get(sectionId);
            if (id.isBlank() || result.containsKey(id)) {
                issues.add(blocking("GROUP_ID_INVALID", sheet.getSheetName(), rowIndex + 1, "groupId",
                        "groupId trống hoặc bị trùng."));
                continue;
            }
            if (section == null) {
                issues.add(blocking("GROUP_SECTION_UNKNOWN", sheet.getSheetName(), rowIndex + 1, "sectionId",
                        "sectionId không tồn tại."));
                continue;
            }
            String stimulusType = rows.value(row, "stimulusType").toUpperCase(Locale.ROOT);
            if (stimulusType.isBlank()) stimulusType = "NONE";
            if (!Set.of("NONE", "READING_PASSAGE", "LISTENING_AUDIO").contains(stimulusType)) {
                issues.add(blocking("STIMULUS_TYPE_INVALID", sheet.getSheetName(), rowIndex + 1, "stimulusType",
                        "stimulusType không hợp lệ."));
                continue;
            }
            GroupBuilder group = new GroupBuilder(
                    id,
                    defaultText(rows.value(row, "label"), id),
                    rows.value(row, "instruction"),
                    stimulusType,
                    rows.value(row, "passageText"),
                    rows.value(row, "transcriptText"),
                    rows.value(row, "audioUrl"),
                    rows.value(row, "imageUrl")
            );
            group.skill = section.skill;
            result.put(id, group);
            section.groups.add(group);
        }
        return result;
    }

    private Map<String, QuestionBuilder> readQuestions(
            Sheet sheet,
            Map<String, GroupBuilder> groups,
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            List<ImportIssue> issues) {
        SheetReader rows = new SheetReader(sheet);
        Map<String, QuestionBuilder> result = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (rows.blank(row)) continue;
            String id = rows.value(row, "questionId");
            String rowKey = questionRowKey(id, rowIndex + 1);
            GroupBuilder group = groups.get(rows.value(row, "groupId"));
            if (id.isBlank() || result.containsKey(id)) {
                issues.add(blocking("QUESTION_ID_INVALID", sheet.getSheetName(), rowIndex + 1, "questionId",
                        "questionId trống hoặc bị trùng.", rowKey));
                continue;
            }
            if (group == null) {
                issues.add(blocking("QUESTION_GROUP_UNKNOWN", sheet.getSheetName(), rowIndex + 1, "groupId",
                        "groupId không tồn tại.", rowKey));
                continue;
            }
            CanonicalQuestionType type;
            try {
                type = questionTypeResolver.resolve(rows.value(row, "questionType"));
            } catch (IllegalArgumentException exception) {
                issues.add(blocking("QUESTION_TYPE_UNSUPPORTED", sheet.getSheetName(), rowIndex + 1, "questionType",
                        "Dạng câu hỏi không được hỗ trợ.", rowKey));
                continue;
            }
            String skill = skillForGroup(template, group, groups);
            if (skill != null && !template.requireSkill(skill).questionTypes().contains(type.name())) {
                issues.add(blocking("QUESTION_TYPE_NOT_ALLOWED_BY_TEMPLATE", sheet.getSheetName(), rowIndex + 1,
                        "questionType", "Dạng câu hỏi không thuộc policy của mẫu đề.", rowKey));
                continue;
            }
            int questionNo = positiveInt(rows.value(row, "questionNo"), result.size() + 1,
                    issues, sheet.getSheetName(), rowIndex + 1, "questionNo", rowKey);
            BigDecimal points = positiveDecimal(rows.value(row, "points"), BigDecimal.ONE,
                    issues, sheet.getSheetName(), rowIndex + 1, "points", rowKey);
            AssessmentAuthoringCatalogService.QuestionAuthoringPolicy authoringPolicy =
                    template.requireSkill(skill).questionPolicy(type.name());
            String rawScoringPolicy = rows.value(row, "scoringPolicyCode");
            String scoringPolicyCode = rawScoringPolicy.isBlank()
                    ? (authoringPolicy == null ? scoringPolicy(type).name() : authoringPolicy.defaultScoringPolicyCode())
                    : rawScoringPolicy.toUpperCase(Locale.ROOT);
            ScoringPolicyCode scoringPolicy;
            try {
                scoringPolicy = ScoringPolicyCode.valueOf(scoringPolicyCode);
                if (authoringPolicy != null
                        && !authoringPolicy.allowedScoringPolicyCodes().contains(scoringPolicy.name())) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                issues.add(blocking("SCORING_POLICY_NOT_ALLOWED_BY_TEMPLATE", sheet.getSheetName(),
                        rowIndex + 1, "scoringPolicyCode",
                        "Chính sách chấm điểm không được phép cho dạng câu hỏi này.", rowKey));
                continue;
            }
            QuestionBuilder question = new QuestionBuilder(
                    id, questionNo, type, rows.value(row, "prompt"), points, scoringPolicy,
                    rows.value(row, "explanationVi"), rows.value(row, "essayTaskType"),
                    nullableInt(rows.value(row, "prepTimeSeconds")),
                    nullableInt(rows.value(row, "responseTimeSeconds")), rowIndex + 1);
            result.put(id, question);
            group.questions.add(question);
        }
        return result;
    }

    private void pruneUnusedTemplateSections(Map<String, SectionBuilder> sections,
                                             List<ImportIssue> issues) {
        List<String> unusedIds = sections.values().stream()
                .filter(section -> section.groups.isEmpty())
                .map(section -> section.id)
                .toList();
        for (String id : unusedIds) {
            sections.remove(id);
            issues.add(warning("UNUSED_TEMPLATE_SECTION_IGNORED", "Sections", 0, "sectionId",
                    "Section mẫu " + id + " không có group nên được bỏ qua."));
        }
        if (sections.isEmpty()) {
            issues.add(blocking("SECTIONS_EMPTY", "Sections", 0, null,
                    "File chưa có phần thi nào chứa nội dung."));
        }
    }

    private void readAnswers(Sheet sheet, Map<String, QuestionBuilder> questions, List<ImportIssue> issues) {
        SheetReader rows = new SheetReader(sheet);
        Set<String> optionIds = new LinkedHashSet<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (rows.blank(row)) continue;
            String questionId = rows.value(row, "questionId");
            String rowKey = questionId.isBlank()
                    ? "OptionsAnswers:" + (rowIndex + 1)
                    : questionId;
            QuestionBuilder question = questions.get(questionId);
            if (question == null) {
                issues.add(blocking("ANSWER_QUESTION_UNKNOWN", sheet.getSheetName(), rowIndex + 1, "questionId",
                        "questionId không tồn tại.", rowKey));
                continue;
            }
            String optionId = rows.value(row, "optionId");
            String optionText = rows.value(row, "optionText");
            if (!optionId.isBlank() || !optionText.isBlank()) {
                String scopedId = question.id + ":" + optionId;
                if (optionId.isBlank() || !optionIds.add(scopedId)) {
                    issues.add(blocking("OPTION_ID_INVALID", sheet.getSheetName(), rowIndex + 1, "optionId",
                            "optionId trống hoặc bị trùng trong câu hỏi.", question.id));
                } else {
                    question.options.add(new OptionBuilder(optionId, optionText,
                            booleanValue(rows.value(row, "isCorrect"))));
                }
            }
            String correctValue = rows.value(row, "correctValue");
            if (!correctValue.isBlank()) question.correctValue = correctValue;

            String blankId = rows.value(row, "blankId");
            if (!blankId.isBlank()) {
                List<String> accepted = List.of(rows.value(row, "acceptedValues").split("\\|"))
                        .stream().map(String::trim).filter(value -> !value.isBlank()).toList();
                question.blanks.add(new BlankBuilder(blankId, rows.value(row, "blankPrompt"), accepted));
            }
            String leftId = rows.value(row, "leftId");
            String rightId = rows.value(row, "rightId");
            if (!leftId.isBlank() || !rightId.isBlank()) {
                if (leftId.isBlank() || rightId.isBlank()) {
                    issues.add(blocking("MATCHING_PAIR_INCOMPLETE", sheet.getSheetName(), rowIndex + 1, null,
                            "Cặp nối phải có cả leftId và rightId.", question.id));
                } else {
                    question.matching.add(new MatchingBuilder(leftId, rows.value(row, "leftText"),
                            rightId, rows.value(row, "rightText")));
                }
            }
        }
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file Excel.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File Excel vượt quá 10MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                throw new IllegalArgumentException("File không phải định dạng XLSX hợp lệ.");
            }
            return bytes;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Không đọc được file Excel.", exception);
        }
    }

    private Map<String, String> readManifest(Sheet sheet, List<ImportIssue> issues) {
        Map<String, String> values = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String key = formatter.formatCellValue(row.getCell(0)).trim();
            if (key.isBlank()) continue;
            if (values.put(key, formatter.formatCellValue(row.getCell(1)).trim()) != null) {
                issues.add(blocking("MANIFEST_KEY_DUPLICATE", sheet.getSheetName(), rowIndex + 1, key,
                        "Manifest có key bị trùng."));
            }
        }
        return values;
    }

    private void manifestSheet(Workbook workbook, AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        Sheet sheet = workbook.createSheet("Manifest");
        row(sheet, 0, "key", "value");
        row(sheet, 1, "schemaVersion", SCHEMA_VERSION);
        row(sheet, 2, "examTemplateCode", template.code());
        row(sheet, 3, "title", template.displayName() + " - Bộ đề mẫu");
        row(sheet, 4, "description", "");
        autosize(sheet, 2);
    }

    private void instructionsSheet(Workbook workbook,
                                   AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        Sheet sheet = workbook.createSheet("Instructions");
        row(sheet, 0, "Mục", "Hướng dẫn");
        row(sheet, 1, "Template", template.displayName() + " (" + template.code() + ")");
        row(sheet, 2, "Quy trình", "Điền Sections -> Groups -> Questions -> OptionsAnswers, sau đó tải file lên để xem lỗi từng dòng trước khi tạo bản nháp.");
        row(sheet, 3, "ID", "sectionId, groupId, questionId và các item ID phải ổn định, không trùng trong cùng phạm vi.");
        row(sheet, 4, "SINGLE_CHOICE", "Mỗi phương án là một dòng OptionsAnswers; chỉ một dòng có isCorrect=TRUE.");
        row(sheet, 5, "MULTIPLE_CHOICE", "Mỗi phương án là một dòng; đánh dấu tất cả đáp án đúng bằng isCorrect=TRUE.");
        row(sheet, 6, "TRUE_FALSE_NOT_GIVEN", "Điền correctValue bằng TRUE, FALSE hoặc NOT_GIVEN.");
        row(sheet, 7, "FILL_BLANK", "Mỗi ô trống dùng blankId; các đáp án chấp nhận ngăn cách bằng dấu | trong acceptedValues.");
        row(sheet, 8, "MATCHING", "Mỗi cặp dùng leftId/leftText và rightId/rightText trên một dòng.");
        row(sheet, 9, "ESSAY/SPEAKING", "Không nhập đáp án khách quan; hệ thống gắn profile chấm và prompt đã được phê duyệt theo template.");
        int rowIndex = 11;
        row(sheet, rowIndex++, "Kỹ năng", "Dạng câu được phép");
        for (Map.Entry<String, AssessmentAuthoringCatalogService.SkillAuthoringPolicy> entry
                : template.skills().entrySet()) {
            row(sheet, rowIndex++, entry.getKey(), String.join(", ", entry.getValue().questionTypes()));
        }
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 110 * 256);
        sheet.createFreezePane(0, 1);
    }

    private void sectionsSheet(Workbook workbook, AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        Sheet sheet = workbook.createSheet("Sections");
        row(sheet, 0, "sectionId", "title", "skill", "durationMinutes");
        int index = 1;
        for (Map.Entry<String, AssessmentAuthoringCatalogService.SkillAuthoringPolicy> entry : template.skills().entrySet()) {
            row(sheet, index++, "section-" + entry.getKey().toLowerCase(Locale.ROOT),
                    SKILL_TITLES.getOrDefault(entry.getKey(), entry.getKey()), entry.getKey(),
                    entry.getValue().durationMinutes());
        }
        addListValidation(sheet, 2, template.skills().keySet().toArray(String[]::new));
        autosize(sheet, 4);
    }

    private void groupsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Groups");
        row(sheet, 0, "groupId", "sectionId", "label", "instruction", "stimulusType",
                "passageText", "transcriptText", "audioUrl", "imageUrl");
        autosize(sheet, 9);
    }

    private void questionsSheet(Workbook workbook, AssessmentAuthoringCatalogService.ExamTemplatePolicy template) {
        Sheet sheet = workbook.createSheet("Questions");
        row(sheet, 0, "questionId", "groupId", "questionNo", "questionType", "prompt", "points", "scoringPolicyCode",
                "explanationVi", "essayTaskType", "prepTimeSeconds", "responseTimeSeconds");
        String[] types = template.skills().values().stream()
                .flatMap(policy -> policy.questionTypes().stream())
                .distinct()
                .toArray(String[]::new);
        addListValidation(sheet, 3, types);
        String[] scoringPolicies = template.skills().values().stream()
                .flatMap(policy -> policy.questionPolicies().values().stream())
                .flatMap(policy -> policy.allowedScoringPolicyCodes().stream())
                .distinct()
                .toArray(String[]::new);
        addListValidation(sheet, 6, scoringPolicies);
        autosize(sheet, 11);
    }

    private void optionsAnswersSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("OptionsAnswers");
        row(sheet, 0, "questionId", "optionId", "optionText", "isCorrect", "correctValue",
                "blankId", "blankPrompt", "acceptedValues", "leftId", "leftText", "rightId", "rightText");
        addListValidation(sheet, 3, new String[]{"TRUE", "FALSE"});
        autosize(sheet, 12);
    }

    private static void addListValidation(Sheet sheet, int column, String[] values) {
        if (values == null || values.length == 0) return;
        org.apache.poi.ss.usermodel.DataValidationHelper helper = sheet.getDataValidationHelper();
        org.apache.poi.ss.usermodel.DataValidationConstraint constraint =
                helper.createExplicitListConstraint(values);
        org.apache.poi.ss.util.CellRangeAddressList cells =
                new org.apache.poi.ss.util.CellRangeAddressList(1, 1000, column, column);
        org.apache.poi.ss.usermodel.DataValidation validation = helper.createValidation(constraint, cells);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Giá trị không hợp lệ", "Hãy chọn một giá trị trong danh sách của template.");
        sheet.addValidationData(validation);
    }

    private static void row(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) {
            Cell cell = row.createCell(column);
            Object value = values[column];
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(value == null ? "" : value.toString());
        }
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int column = 0; column < columns; column++) sheet.autoSizeColumn(column);
    }

    private static final Map<String, String> SKILL_TITLES = Map.of(
            "READING", "Phần Đọc", "LISTENING", "Phần Nghe",
            "WRITING", "Phần Viết", "SPEAKING", "Phần Nói");

    private static List<QuestionContent.Item> uniqueRightItems(List<MatchingBuilder> pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        pairs.forEach(pair -> values.putIfAbsent(pair.rightId, pair.rightText));
        return values.entrySet().stream().map(entry -> new QuestionContent.Item(entry.getKey(), entry.getValue())).toList();
    }

    private static Map<String, String> matchingMap(List<MatchingBuilder> pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        pairs.forEach(pair -> result.put(pair.leftId, pair.rightId));
        return result;
    }

    private static ScoringPolicyCode scoringPolicy(CanonicalQuestionType type) {
        return switch (type) {
            case FILL_BLANK -> ScoringPolicyCode.NORMALIZED_EXACT;
            case MATCHING -> ScoringPolicyCode.PER_PAIR;
            case ESSAY, SPEAKING -> ScoringPolicyCode.PROFILE_BASED;
            default -> ScoringPolicyCode.ALL_OR_NOTHING;
        };
    }

    private static String legacyAnswer(QuestionBuilder question) {
        if (question.type == CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN) return question.correctValue;
        if (question.type == CanonicalQuestionType.FILL_BLANK) {
            return question.blanks.isEmpty() || question.blanks.get(0).acceptedValues.isEmpty()
                    ? "" : question.blanks.get(0).acceptedValues.get(0);
        }
        List<String> indexes = new ArrayList<>();
        for (int index = 0; index < question.options.size(); index++) {
            if (question.options.get(index).correct) indexes.add(String.valueOf(index + 1));
        }
        return String.join(",", indexes);
    }

    private String skillForGroup(AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
                                 GroupBuilder group,
                                 Map<String, GroupBuilder> groups) {
        return group.skill;
    }

    private static int positiveInt(String raw, int fallback, List<ImportIssue> issues,
                                   String sheet, int row, String field) {
        return positiveInt(raw, fallback, issues, sheet, row, field, null);
    }

    private static int positiveInt(String raw, int fallback, List<ImportIssue> issues,
                                   String sheet, int row, String field, String rowKey) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = new BigDecimal(raw).intValueExact();
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            issues.add(blocking("POSITIVE_INTEGER_REQUIRED", sheet, row, field,
                    "Giá trị phải là số nguyên dương.", rowKey));
            return fallback;
        }
    }

    private static BigDecimal positiveDecimal(String raw, BigDecimal fallback, List<ImportIssue> issues,
                                              String sheet, int row, String field) {
        return positiveDecimal(raw, fallback, issues, sheet, row, field, null);
    }

    private static BigDecimal positiveDecimal(String raw, BigDecimal fallback, List<ImportIssue> issues,
                                              String sheet, int row, String field, String rowKey) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            issues.add(blocking("POSITIVE_DECIMAL_REQUIRED", sheet, row, field,
                    "Giá trị phải lớn hơn 0.", rowKey));
            return fallback;
        }
    }

    private static Integer nullableInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return new BigDecimal(raw).intValueExact(); }
        catch (RuntimeException exception) { return null; }
    }

    private static boolean booleanValue(String value) {
        return Set.of("TRUE", "1", "YES", "Y", "X").contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void nullable(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) node.putNull(field);
        else node.put(field, value);
    }

    private static String questionRowKey(String questionId, int row) {
        return questionId == null || questionId.isBlank() ? "Questions:" + row : questionId;
    }

    private static boolean hasBlocking(List<ImportIssue> issues) {
        return issues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity));
    }

    private static boolean hasFatalBlocking(List<ImportIssue> issues) {
        return issues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity)
                && (FATAL_ISSUE_CODES.contains(issue.code)
                || ("Draft".equals(issue.sheet) && (issue.rowKey == null || issue.rowKey.isBlank()))));
    }

    private static ImportIssue blocking(String code, String sheet, int row, String field, String message) {
        return blocking(code, sheet, row, field, message, null);
    }

    private static ImportIssue blocking(String code, String sheet, int row, String field,
                                        String message, String rowKey) {
        return new ImportIssue("BLOCKING", code, sheet, row, field, message, rowKey);
    }

    private static ImportIssue warning(String code, String sheet, int row, String field, String message) {
        return new ImportIssue("WARNING", code, sheet, row, field, message, null);
    }

    public record ImportIssue(String severity, String code, String sheet, int row,
                              String field, String message, String rowKey) {
    }

    public record ImportRowPreview(int row,
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
        public ImportRowPreview(int row,
                                String questionId,
                                String questionNo,
                                String groupId,
                                String questionType,
                                String prompt,
                                String status,
                                boolean importable,
                                List<String> messages) {
            this(row, "Questions", null, null, groupId, questionNo, null, ImportRowDetail.empty(),
                    questionId, groupId, questionType, null, prompt,
                    null, null, null, status, importable, messages);
        }
    }

    public record ImportRowDetail(String skill,
                                  String groupInstruction,
                                  String groupPassage,
                                  String groupTranscript,
                                  String groupImageReference,
                                  String groupAudioReference,
                                  String questionImageReference,
                                  String questionAudioReference,
                                  String teacherNote,
                                  List<ImportOptionPreview> options,
                                  List<ImportMatchingPairPreview> matchingPairs) {
        public ImportRowDetail {
            options = options == null ? List.of() : List.copyOf(options);
            matchingPairs = matchingPairs == null ? List.of() : List.copyOf(matchingPairs);
        }

        static ImportRowDetail empty() {
            return new ImportRowDetail(null, null, null, null, null, null,
                    null, null, null, List.of(), List.of());
        }
    }

    public record ImportOptionPreview(String label, String text, String imageReference) {
    }

    public record ImportMatchingPairPreview(String leftId,
                                            String leftText,
                                            String leftImageReference,
                                            String rightId,
                                            String rightText,
                                            String rightImageReference) {
    }

    public record ExcelPreview(String draftJson, List<ImportIssue> issues, List<ImportRowPreview> rows,
                               int sectionCount, int groupCount, int questionCount,
                               int importableQuestionCount, int warningRowCount, int errorRowCount,
                               BigDecimal totalPoints) {
        @com.fasterxml.jackson.annotation.JsonProperty("hasBlocking")
        public boolean hasBlocking() { return PracticeAssessmentExcelService.hasBlocking(issues); }

        @com.fasterxml.jackson.annotation.JsonProperty("hasFatalBlocking")
        public boolean hasFatalBlocking() { return PracticeAssessmentExcelService.hasFatalBlocking(issues); }

        @com.fasterxml.jackson.annotation.JsonProperty("canImport")
        public boolean canImport() { return !hasFatalBlocking() && importableQuestionCount > 0 && draftJson != null; }
    }

    public record ExcelImportContext(PracticeDraft draft,
                                     String templateCode,
                                     Integer testNo,
                                     String lessonCode,
                                     String skill) {
    }

    private record QuestionRowSeed(int row, String rowKey, String questionId, String questionNo,
                                   String groupId, String questionType, String prompt) {
    }

    private record DraftBuild(ObjectNode root, int sectionCount, int groupCount,
                              int questionCount, BigDecimal totalPoints) {
    }

    private static final class SectionBuilder {
        private final String id;
        private final String title;
        private final String skill;
        private final int durationMinutes;
        private final List<GroupBuilder> groups = new ArrayList<>();

        private SectionBuilder(String id, String title, String skill, int durationMinutes) {
            this.id = id; this.title = title; this.skill = skill; this.durationMinutes = durationMinutes;
        }
    }

    private static final class GroupBuilder {
        private final String id;
        private final String label;
        private final String instruction;
        private final String stimulusType;
        private final String passageText;
        private final String transcriptText;
        private final String audioUrl;
        private final String imageUrl;
        private String skill;
        private final List<QuestionBuilder> questions = new ArrayList<>();

        private GroupBuilder(String id, String label, String instruction, String stimulusType,
                             String passageText, String transcriptText, String audioUrl, String imageUrl) {
            this.id = id; this.label = label; this.instruction = instruction; this.stimulusType = stimulusType;
            this.passageText = passageText; this.transcriptText = transcriptText;
            this.audioUrl = audioUrl; this.imageUrl = imageUrl;
        }
    }

    private static final class QuestionBuilder {
        private final String id;
        private final int questionNo;
        private final CanonicalQuestionType type;
        private final String prompt;
        private final BigDecimal points;
        private final ScoringPolicyCode scoringPolicy;
        private final String explanationVi;
        private final String essayTaskType;
        private final Integer prepTimeSeconds;
        private final Integer responseTimeSeconds;
        private final int sourceRow;
        private String correctValue = "";
        private final List<OptionBuilder> options = new ArrayList<>();
        private final List<BlankBuilder> blanks = new ArrayList<>();
        private final List<MatchingBuilder> matching = new ArrayList<>();

        private QuestionBuilder(String id, int questionNo, CanonicalQuestionType type, String prompt,
                                BigDecimal points, ScoringPolicyCode scoringPolicy,
                                String explanationVi, String essayTaskType,
                                Integer prepTimeSeconds, Integer responseTimeSeconds, int sourceRow) {
            this.id = id; this.questionNo = questionNo; this.type = type; this.prompt = prompt;
            this.points = points; this.scoringPolicy = scoringPolicy;
            this.explanationVi = explanationVi; this.essayTaskType = essayTaskType;
            this.prepTimeSeconds = prepTimeSeconds; this.responseTimeSeconds = responseTimeSeconds;
            this.sourceRow = sourceRow;
        }
    }

    private record OptionBuilder(String id, String text, boolean correct) {}
    private record BlankBuilder(String id, String prompt, List<String> acceptedValues) {}
    private record MatchingBuilder(String leftId, String leftText, String rightId, String rightText) {}

    private static final class SheetReader {
        private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
        private final Map<String, Integer> columns = new LinkedHashMap<>();

        private SheetReader(Sheet sheet) {
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Sheet " + sheet.getSheetName() + " thiếu header.");
            for (Cell cell : header) columns.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
        }

        private String value(Row row, String column) {
            Integer index = columns.get(column);
            return index == null || row == null ? "" : formatter.formatCellValue(row.getCell(index)).trim();
        }

        private boolean blank(Row row) {
            if (row == null) return true;
            for (Cell cell : row) if (!formatter.formatCellValue(cell).trim().isBlank()) return false;
            return true;
        }
    }
}
