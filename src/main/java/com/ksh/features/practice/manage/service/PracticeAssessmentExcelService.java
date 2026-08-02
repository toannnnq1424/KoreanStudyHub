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
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateJson;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceSnapshot;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.material.PracticeMaterialPlacements;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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

@Service
public class PracticeAssessmentExcelService {

    public static final String SCHEMA_VERSION = "practice-excel-v2";
    private static final String LEGACY_SCHEMA_VERSION = "practice-excel-v1";
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final int MAX_MEDIA_OVERRIDES = 200;
    private static final Pattern MANAGED_MEDIA_URL = Pattern.compile(
            "^/practice/materials/(\\d+)/content$");
    /**
     * @deprecated use {@link PracticeMaterialPlacements#SPEAKING_PROMPT_EXCEL_STAGING}.
     */
    @Deprecated(forRemoval = false)
    public static final String EXCEL_SPEAKING_STAGING =
            PracticeMaterialPlacements.SPEAKING_PROMPT_EXCEL_STAGING;
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
    private final PracticeAuthoringCandidateService candidateService;
    private final PracticeAssessmentQuickExcelCodec quickCodec;

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
            LecturerAssetService assetService,
            PracticeAuthoringCandidateService candidateService) {
        this.catalogService = catalogService;
        this.draftContractService = draftContractService;
        this.draftValidator = draftValidator;
        this.draftRepository = draftRepository;
        this.contractCodec = contractCodec;
        this.questionTypeResolver = questionTypeResolver;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.assetService = assetService;
        this.candidateService = candidateService;
        this.v2Codec = new PracticeAssessmentExcelV2Codec(
                draftContractService, draftValidator, contractCodec, objectMapper);
        this.quickCodec = new PracticeAssessmentQuickExcelCodec(
                objectMapper, catalogService);
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
                contractCodec, questionTypeResolver, objectMapper,
                null, null, null);
    }

    public byte[] buildTemplate() {
        return v2Codec.buildTemplate(catalogService.defaultTemplate());
    }

    public byte[] buildQuickTemplate() {
        return quickCodec.buildTemplate();
    }

    public ExcelPreview preview(MultipartFile file) {
        byte[] bytes = validateAndRead(file);
        return preview(bytes, null);
    }

    public ExcelPreview preview(
            MultipartFile file, ExcelImportContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Route Excel target là bắt buộc.");
        }
        byte[] bytes = validateAndRead(file);
        return preview(bytes, context);
    }

    private ExcelPreview preview(byte[] bytes, ExcelImportContext context) {
        List<ImportIssue> issues = new ArrayList<>();
        PracticeAssessmentQuickExcelCodec.PackageInspection packageInspection =
                inspectPackage(bytes);
        failUnsafeQuickPackage(packageInspection);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            WorkbookFormat format = detect(workbook);
            if (format == WorkbookFormat.QUICK_V1) {
                if (context == null) {
                    throw new PracticeAssessmentExcelException(
                            "CANDIDATE_TARGET_INVALID",
                            "Quick Excel cần route draftId + testNo + skill + lessonCode.");
                }
                PracticeAssessmentQuickExcelCodec.QuickParseResult parsed =
                        quickCodec.parse(
                                workbook, context.skill(), packageInspection);
                validateQuickTargetAuthority(context, parsed.groups());
                return quickPreview(parsed);
            }
            if (format == WorkbookFormat.ADVANCED_V2) {
                return v2Codec.preview(workbook, catalogService.defaultTemplate(), issues);
            }

            List<QuestionRowSeed> questionRows = readQuestionRows(workbook.getSheet("Questions"));

            Map<String, String> manifest = readManifest(workbook.getSheet("Manifest"), issues);
            String schemaVersion = manifest.getOrDefault("schemaVersion", "");
            if (!LEGACY_SCHEMA_VERSION.equals(schemaVersion)) {
                issues.add(blocking("SCHEMA_VERSION_UNSUPPORTED", "Manifest", 2, "schemaVersion",
                        "Phiên bản file Excel không được hỗ trợ."));
            }
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template = catalogService.defaultTemplate();

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
            if (exception instanceof PracticeAssessmentExcelException excelException) {
                throw excelException;
            }
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Không đọc được nội dung file Excel.", exception);
        }
    }

    @Transactional
    public CandidateView createCandidate(
            MultipartFile file,
            ExcelImportContext context,
            Long actorId,
            String mediaOverridesJson) {
        if (context == null || actorId == null || actorId < 1) {
            throw new IllegalArgumentException(
                    "Route và actor của Excel candidate là bắt buộc.");
        }
        if (candidateService == null) {
            throw new IllegalStateException(
                    "Authoring candidate service chưa được cấu hình.");
        }
        byte[] bytes = validateAndRead(file);
        AdaptedWorkbook adapted = adapt(
                bytes, context, mediaOverridesJson);
        SourceSnapshot source = new SourceSnapshot(
                adapted.sourceKind(),
                adapted.sourceKind().contractVersion(),
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
                actorId, source, target, adapted.groups()));
    }

    private AdaptedWorkbook adapt(
            byte[] bytes,
            ExcelImportContext context,
            String mediaOverridesJson) {
        ExcelPreview preview = preview(bytes, context);
        Map<String, String> mediaOverrides =
                parseMediaOverrides(mediaOverridesJson);
        PracticeAssessmentQuickExcelCodec.PackageInspection packageInspection =
                inspectPackage(bytes);
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes))) {
            WorkbookFormat format = detect(workbook);
            if (format == WorkbookFormat.QUICK_V1) {
                if (!mediaOverrides.isEmpty()) {
                    throw new PracticeAssessmentExcelException(
                            "ADVANCED_AUTHORING_REQUIRED",
                            "Quick Excel không hỗ trợ media upload/reference.");
                }
                PracticeAssessmentQuickExcelCodec.QuickParseResult parsed =
                        quickCodec.parse(
                                workbook, context.skill(), packageInspection);
                validateQuickTargetAuthority(context, parsed.groups());
                return new AdaptedWorkbook(
                        SourceKind.QUICK_EXCEL, parsed.groups());
            }
            if (!preview.canImport()) {
                throw new PracticeAssessmentExcelException(
                        "WORKBOOK_SCHEMA_UNSUPPORTED",
                        "Workbook không còn nội dung hợp lệ để tạo candidate.");
            }
            ObjectNode root = readObject(preview.draftJson());
            applyMediaOverrides(root, mediaOverrides);
            SourceKind sourceKind = format == WorkbookFormat.ADVANCED_V2
                    ? SourceKind.ADVANCED_EXCEL_V2
                    : SourceKind.LEGACY_EXCEL_V1;
            if ("SPEAKING".equals(context.skill())) {
                requireVerifiedSpeakingUploadAssets(
                        exactTargetSection(root, context),
                        context.draft().getId(),
                        context.draft().getOwnerId());
            }
            return new AdaptedWorkbook(
                    sourceKind,
                    adaptExactTargetGroups(root, context, sourceKind));
        } catch (PracticeAssessmentExcelException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PracticeAssessmentExcelException(
                    "WORKBOOK_SCHEMA_UNSUPPORTED",
                    "Không thể chuyển workbook sang authoring candidate.",
                    exception);
        }
    }

    private void requireVerifiedSpeakingUploadAssets(
            JsonNode targetSection,
            Long linkedDraftId,
            Long assetOwnerId) {
        for (JsonNode group : targetSection.path("groups")) {
            for (JsonNode question : group.path("questions")) {
                if (!CanonicalQuestionType.SPEAKING.name().equals(
                        question.path("questionType").asText())) {
                    continue;
                }
                JsonNode content = question.path("questionContent");
                JsonNode delivery = content.path("speakingDelivery");
                String reference = delivery.path(
                        "promptAudioReference").asText("").trim();
                java.util.regex.Matcher matcher =
                        MANAGED_MEDIA_URL.matcher(reference);
                if (!QuestionContent.supportsTypedSpeakingDelivery(
                            content.path("schemaVersion").asText())
                        || !"audio_upload".equals(
                            delivery.path("inputType").asText())
                        || !"audio_only".equals(
                            delivery.path("deliveryMode").asText())
                        || !"teacher_upload".equals(
                            delivery.path("audioOrigin").asText())
                        || !matcher.matches()) {
                    throw new IllegalArgumentException(
                            "Speaking qua Excel chỉ nhận audio riêng tư đã tải lên. "
                                    + "Hãy tải tệp ở màn hình nhập, rồi mở từng câu trong Editor "
                                    + "để xác minh và tạo bản chép lời; Excel không gọi TTS.");
                }
                Long assetId = Long.valueOf(matcher.group(1));
                if (linkedDraftId == null || assetService == null) {
                    throw new IllegalStateException(
                            "Không thể xác minh audio Speaking theo đúng bản nháp. "
                                    + "Hãy mở Nhập Excel từ Editor và tải audio vào bản nháp đó.");
                }
                String clientId = question.path("clientId").asText("").trim();
                if (clientId.isBlank() || clientId.length() > 100) {
                    throw new IllegalArgumentException(
                            "Câu Speaking trong Excel thiếu clientId hợp lệ.");
                }
                assetService.requireVerifiedPrivateManualAudioForExcel(
                        assetId,
                        assetOwnerId,
                        linkedDraftId,
                        clientId);
            }
        }
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
            throw new IllegalStateException(
                    "Không thể tạo JSON typed từ Excel.", exception);
        }
    }

    private ObjectNode readObject(String value) {
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed instanceof ObjectNode object) return object;
        } catch (Exception exception) {
            throw new PracticeAssessmentExcelException(
                    "WORKBOOK_SCHEMA_UNSUPPORTED",
                    "Workbook không tạo được JSON typed hợp lệ.", exception);
        }
        throw new PracticeAssessmentExcelException(
                "WORKBOOK_SCHEMA_UNSUPPORTED",
                "Workbook không tạo được JSON typed hợp lệ.");
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
                "Workbook không thuộc Quick v1, Advanced v2 hoặc Legacy v1.");
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
                    throw unsafePackage("Workbook có quá nhiều ZIP entries.");
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
                ByteArrayOutputStream relationship = inspectXml
                        ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw unsafePackage(
                                "Workbook vượt giới hạn dữ liệu giải nén.");
                    }
                    if (relationship != null
                            && relationship.size() < 1_000_000) {
                        relationship.write(buffer, 0, read);
                    }
                }
                if (relationship != null) {
                    String xml = relationship.toString(StandardCharsets.UTF_8);
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

    private ExcelPreview quickPreview(
            PracticeAssessmentQuickExcelCodec.QuickParseResult parsed) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", PracticeAssessmentQuickExcelCodec.CONTRACT_VERSION);
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
                    points.getOrDefault(entry.getKey(), BigDecimal.ZERO)) == 0;
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

    private ArrayNode adaptExactTargetGroups(
            ObjectNode root,
            ExcelImportContext context,
            SourceKind sourceKind) {
        JsonNode section = exactTargetSection(root, context);
        ArrayNode result = objectMapper.createArrayNode();
        int groupOrder = 0;
        for (JsonNode sourceGroup : section.path("groups")) {
            groupOrder++;
            ObjectNode group = result.addObject();
            group.put("candidateGroupId", stableAdapterId(
                    sourceGroup.path("clientId").asText(""),
                    "excel_group_" + groupOrder));
            group.put("groupOrder", groupOrder);
            group.put("label", sourceGroup.path("label").asText(
                    "Nhóm " + groupOrder));
            group.put("instruction",
                    sourceGroup.path("instruction").asText(""));
            String sharedImage = firstNonBlank(
                    sourceGroup.path("stimulus").path("imageReference").asText(""),
                    sourceGroup.path("imageUrl").asText(""));
            group.set("stimulus", adaptStimulus(sourceGroup, sourceKind));
            group.putArray("sourceRefs");
            ArrayNode questions = group.putArray("questions");
            int questionOrder = 0;
            for (JsonNode sourceQuestion : sourceGroup.path("questions")) {
                questionOrder++;
                questions.add(adaptQuestion(
                        sourceQuestion, groupOrder,
                        questionOrder, sharedImage));
            }
        }
        if (result.isEmpty()) {
            throw new PracticeAssessmentExcelException(
                    "CANDIDATE_TARGET_SECTION_NOT_FOUND",
                    "Workbook không có group hợp lệ cho exact route target.");
        }
        return result;
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

    private ObjectNode adaptStimulus(
            JsonNode sourceGroup, SourceKind sourceKind) {
        JsonNode raw = sourceGroup.path("stimulus");
        ObjectNode stimulus = objectMapper.createObjectNode();
        stimulus.put("schemaVersion", "practice-stimulus-v2");
        stimulus.put("type", raw.path("type").asText("NONE"));
        stimulus.put("instruction", raw.path("instruction").asText(""));
        stimulus.put("passageText", raw.path("passageText").asText(""));
        stimulus.put("transcriptText", raw.path("transcriptText").asText(""));
        String media = raw.path("mediaReference").asText("");
        if (media.isBlank()) stimulus.putNull("mediaReference");
        else stimulus.put("mediaReference", media);
        ObjectNode provenance = stimulus.putObject("provenance");
        provenance.put("source", sourceKind.name());
        provenance.put("approved", raw.path("provenance")
                .path("approved").asBoolean(true));
        provenance.putArray("sourceRefs");
        return stimulus;
    }

    private ObjectNode adaptQuestion(
            JsonNode source,
            int groupOrder,
            int order,
            String sharedImage) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("candidateQuestionId", stableAdapterId(
                source.path("clientId").asText(""),
                "excel_question_" + groupOrder + "_" + order));
        question.put("questionOrder", order);
        copy(source, question, "questionType");
        copy(source, question, "essayTaskType");
        copy(source, question, "prompt");
        copy(source, question, "points");
        copy(source, question, "explanationVi");
        copy(source, question, "explanationStrategy");
        ObjectNode content = source.path("questionContent") instanceof ObjectNode object
                ? object.deepCopy() : objectMapper.createObjectNode();
        String questionImage = firstNonBlank(
                content.path("imageReference").asText(""),
                source.path("imageUrl").asText(""),
                sharedImage);
        String questionAudio = firstNonBlank(
                content.path("audioReference").asText(""),
                source.path("audioUrl").asText(""));
        if (!questionImage.isBlank()) content.put("imageReference", questionImage);
        if (!questionAudio.isBlank()) content.put("audioReference", questionAudio);
        question.set("questionContent", content);
        question.set("answerSpec", source.path("answerSpec").deepCopy());
        question.put("reviewState",
                source.path("reviewRequired").asBoolean(false)
                        ? "REVIEW_REQUIRED" : "ACCEPTED");
        question.putArray("sourceRefs");
        return question;
    }

    private static void copy(
            JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) target.set(field, source.get(field).deepCopy());
    }

    private static String stableAdapterId(
            String raw, String fallback) {
        String normalized = PracticeAuthoringCandidateJson.normalizedText(raw);
        if (normalized.matches("[A-Za-z0-9._-]{1,80}")) return normalized;
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = PracticeAuthoringCandidateJson.normalizedText(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
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
            byte[] value = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte current : value) {
                result.append(Character.forDigit((current >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(current & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng.", exception);
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
        authorizationService.requireDraft(
                draftId, ownerId, PracticeAction.EDIT);
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản nháp liên kết không tồn tại."));
    }

    @Transactional(readOnly = true)
    public ExcelImportContext requireExcelImportContext(Long draftId,
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
            throw new IllegalArgumentException("Hãy mở Nhập Excel từ một phần kỹ năng trong editor.");
        }
        ObjectNode root = normalizedRoot(draft.getDraftJson(), draft.getCreationMethod());
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode section : root.path("sections")) {
            if (testNo == section.path("testNo").asInt()
                    && routeSkill.equalsIgnoreCase(
                    section.path("skill").asText())
                    && lessonCode.equalsIgnoreCase(section.path("lessonCode").asText())) {
                matches.add(section);
            }
        }
        if (matches.size() != 1) {
            throw new PracticeAssessmentExcelException(
                    "CANDIDATE_TARGET_IDENTITY_MISMATCH",
                    "Không tìm thấy đúng một section khớp route draftId/Test/skill/lesson.");
        }
        JsonNode selected = matches.get(0);
        if (!Set.of("READING", "LISTENING", "WRITING", "SPEAKING")
                .contains(routeSkill)) {
            throw new IllegalArgumentException("Kỹ năng của phần thi không hỗ trợ nhập Excel.");
        }
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = catalogService.defaultTemplate();
        if (!template.requireSkill(routeSkill).excelImportEnabled()) {
            throw new IllegalArgumentException("Kỹ năng này không hỗ trợ nhập Excel.");
        }
        return new ExcelImportContext(draft, testNo,
                selected.path("lessonCode").asText(), routeSkill);
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
            boolean strategyPending =
                    "EXPLANATION_STRATEGY_REQUIRED".equals(
                            message.code());
            String issueType = strategyPending
                    ? "WARNING"
                    : message.type();
            List<String> questionKeys = affectedQuestionKeys(normalizedRoot, message);
            if (questionKeys.isEmpty()) {
                addUnique(issues, new ImportIssue(
                        issueType, message.code(), "Draft", 0, null,
                        message.content(), null));
                continue;
            }
            for (String questionKey : questionKeys) {
                int sourceRow = sourceRows.getOrDefault(questionKey, 0);
                addUnique(issues, new ImportIssue(
                        issueType, message.code(), "Questions", sourceRow, null,
                        message.content(), questionKey));
                if (!strategyPending
                        && "BLOCKING".equals(message.type())) {
                    rejected.add(questionKey);
                }
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
                q.blanks.stream().map(blank -> new QuestionContent.Blank(blank.id, blank.prompt)).toList()
        );
        AnswerSpec spec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                q.type,
                q.options.stream().filter(option -> option.correct).map(option -> option.id).toList(),
                blankToNull(q.correctValue),
                q.blanks.stream().map(blank -> new AnswerSpec.BlankAnswer(blank.id, blank.acceptedValues)).toList(),
                q.scoringPolicy
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
        answer.put("type", "SINGLE");
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
            AssessmentAuthoringCatalogService.SkillAuthoringPolicy skillPolicy =
                    template.requireSkill(skill);
            String writingTask = rows.value(row, "essayTaskType").trim().toUpperCase(Locale.ROOT);
            if ("WRITING".equals(skill)) {
                AssessmentAuthoringCatalogService.WritingTaskAuthoringPolicy taskPolicy =
                        skillPolicy.writingTask(writingTask);
                if (taskPolicy == null) {
                    issues.add(blocking("WRITING_TASK_INVALID", sheet.getSheetName(), rowIndex + 1,
                            "essayTaskType", "Writing phải chọn Q51, Q52, Q53 hoặc Q54.", rowKey));
                } else {
                    int expectedQuestionNo = Integer.parseInt(writingTask.substring(1));
                    if (questionNo != expectedQuestionNo) {
                        issues.add(blocking("WRITING_TASK_QUESTION_NUMBER_MISMATCH",
                                sheet.getSheetName(), rowIndex + 1, "questionNo",
                                writingTask + " phải dùng số câu " + expectedQuestionNo + ".", rowKey));
                    }
                    if (!type.name().equals(taskPolicy.questionType())) {
                        issues.add(blocking("WRITING_TASK_TYPE_MISMATCH",
                                sheet.getSheetName(), rowIndex + 1, "questionType",
                                writingTask + " phải dùng dạng " + taskPolicy.questionType() + ".", rowKey));
                    }
                    if (points.compareTo(taskPolicy.points()) != 0) {
                        issues.add(blocking("WRITING_TASK_POINTS_MISMATCH",
                                sheet.getSheetName(), rowIndex + 1, "points",
                                writingTask + " có điểm tối đa cố định là "
                                        + taskPolicy.points().stripTrailingZeros().toPlainString() + ".", rowKey));
                    }
                }
            }
            AssessmentAuthoringCatalogService.QuestionAuthoringPolicy authoringPolicy =
                    skillPolicy.questionPolicy(type.name());
            ScoringPolicyCode scoringPolicy = authoringPolicy == null
                    ? scoringPolicy(type)
                    : ScoringPolicyCode.valueOf(authoringPolicy.defaultScoringPolicyCode());
            QuestionBuilder question = new QuestionBuilder(
                    id, questionNo, type, rows.value(row, "prompt"), points, scoringPolicy,
                    rows.value(row, "explanationVi"), writingTask,
                    nullableInt(rows.value(row, "prepTimeSeconds")),
                    nullableInt(rows.value(row, "responseTimeSeconds")), rowIndex + 1);
            if (type == CanonicalQuestionType.SPEAKING) {
                issues.add(blocking(
                        "SPEAKING_PROMPT_AUDIO_REQUIRED",
                        sheet.getSheetName(),
                        rowIndex + 1,
                        "questionType",
                        "Speaking trong workbook v1 không có audio đề bài riêng "
                                + "nên chỉ được đọc để sửa, không được nâng ngầm "
                                + "thành câu text-only.",
                        rowKey));
            }
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

    private static ScoringPolicyCode scoringPolicy(CanonicalQuestionType type) {
        return switch (type) {
            case FILL_BLANK -> ScoringPolicyCode.NORMALIZED_EXACT;
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
                                  List<ImportOptionPreview> options) {
        public ImportRowDetail {
            options = options == null ? List.of() : List.copyOf(options);
        }

        static ImportRowDetail empty() {
            return new ImportRowDetail(null, null, null, null, null, null,
                    null, null, null, List.of());
        }
    }

    public record ImportOptionPreview(String label, String text, String imageReference) {
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
                                     Integer testNo,
                                     String lessonCode,
                                     String skill) {
    }

    private enum WorkbookFormat {
        QUICK_V1,
        ADVANCED_V2,
        LEGACY_V1
    }

    private record AdaptedWorkbook(
            SourceKind sourceKind,
            ArrayNode groups) {
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
