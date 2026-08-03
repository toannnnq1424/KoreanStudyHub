package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.transport.PracticeAiAuthoritySnapshot;
import com.ksh.features.practice.ai.transport.PracticeAiCapability;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.PracticeModelCapabilityProfile;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationPort;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReadingListeningExplanationClient {

    private static final Logger log = LoggerFactory.getLogger(ReadingListeningExplanationClient.class);
    public static final String EXPLANATION_PROMPT_VERSION =
            "v9-objective-lecturer-strategy";
    public static final String EXPLANATION_SCHEMA_VERSION = "v4";
    public static final String PREVIOUS_EXPLANATION_SCHEMA_VERSION = "v3";
    public static final String LEGACY_EXPLANATION_SCHEMA_VERSION = "v2";
    public static final String EXPLANATION_LANGUAGE = "vi";

    private final ObjectMapper objectMapper;
    private final PracticeStructuredGenerationPort structuredGeneration;

    @Autowired
    public ReadingListeningExplanationClient(
            PracticeStructuredGenerationPort structuredGeneration,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.structuredGeneration = structuredGeneration;
    }

    public String generate(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        List<ExplanationImageEvidence> safeImages = images == null ? List.of() : List.copyOf(images);
        if (!providerAvailable()) {
            throw new ExplanationProviderException(
                    "PROVIDER_NOT_CONFIGURED", "AI provider key is not configured.", false);
        }
        if (!context.stimulus().hasUsableEvidence() && safeImages.isEmpty()) {
            throw new ExplanationProviderException(
                    "EVIDENCE_UNAVAILABLE", "No approved text or image evidence is available.", false);
        }

        log.info("[ReadingListeningAI] generate model={} skill={} type={}",
                model(), context.skill(), context.questionType());
        try {
            return generateThroughStructuredPort(context, safeImages);
        } catch (PracticeAiContractException exception) {
            log.warn(
                    "[ReadingListeningAI] structured provider failure category={} model={} skill={}",
                    exception.category(),
                    model(),
                    context.skill());
            throw new ExplanationProviderException(
                    exception.category(),
                    "Provider response failed the strict Practice transport contract.",
                    exception.retryable(),
                    exception);
        } catch (ExplanationProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExplanationProviderException(
                    "INVALID_PROVIDER_RESPONSE",
                    "Provider returned unreadable explanation JSON.",
                    true,
                    exception);
        }
    }

    public String cleanAndValidateJson(
            String aiJson,
            ExplanationContext context,
            boolean hasImageEvidence) {
        return cleanAndValidateJson(aiJson, context, List.of());
    }

    public String cleanAndValidateJson(
            String aiJson,
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        try {
            JsonNode root = objectMapper.readTree(aiJson);
            if (!(root instanceof ObjectNode objectRoot)) {
                return null;
            }
            if (objectRoot.has(PracticeAiResultCompleteness.FIELD)) {
                PracticeAiResultCompleteness existing =
                        PracticeAiResultCompleteness.require(objectRoot);
                if (existing.status()
                        != PracticeAiResultCompleteness.Status.COMPLETE) {
                    return null;
                }
                objectRoot.remove(PracticeAiResultCompleteness.FIELD);
            }
            requireFields(root, Set.of(
                    "schemaVersion",
                    "strategyRegistryVersion",
                    "strategyCode",
                    "strategyVersion",
                    "questionType",
                    "explanation"));
            if (!EXPLANATION_SCHEMA_VERSION.equals(text(root, "schemaVersion"))
                    || !context.questionType().name().equals(text(root, "questionType"))
                    || !context.explanationStrategy().registryVersion().equals(
                            text(root, "strategyRegistryVersion"))
                    || !context.explanationStrategy().strategyCode().equals(
                            text(root, "strategyCode"))
                    || !context.explanationStrategy().strategyVersion().equals(
                            text(root, "strategyVersion"))) {
                return null;
            }
            JsonNode explanation = object(root, "explanation");
            validateTypeExplanation(explanation, context, images == null ? List.of() : images);
            objectRoot.set(
                    PracticeAiResultCompleteness.FIELD,
                    objectMapper.valueToTree(
                            PracticeAiResultCompleteness.complete().toMap()));
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            log.warn("[ReadingListeningAI] explanation cleaning failed type={} exception={}",
                    context.questionType(), exception.getClass().getSimpleName());
            return null;
        }
    }

    public String model() {
        return structuredGeneration.identity(
                PracticeAiPurpose.PRACTICE_RL_EXPLANATION).model();
    }

    public String bindingIdentity() {
        PracticeStructuredGenerationPort.ProviderIdentity identity =
                structuredGeneration.identity(
                        PracticeAiPurpose.PRACTICE_RL_EXPLANATION);
        return identity.providerProfileCode()
                + ":binding-revision=" + identity.bindingRevision()
                + ":profile-revision=" + identity.providerProfileRevision();
    }

    public String promptVersion() {
        return EXPLANATION_PROMPT_VERSION;
    }

    public String schemaVersion() {
        return EXPLANATION_SCHEMA_VERSION;
    }

    public String explanationLanguage() {
        return EXPLANATION_LANGUAGE;
    }

    private Map<String, Object> userPayloadObject(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contextSchemaVersion", context.schemaVersion());
        payload.put("skill", context.skill().name());
        payload.put("questionType", context.questionType().name());
        payload.put(
                "strategyRegistryVersion",
                context.explanationStrategy().registryVersion());
        payload.put(
                "strategyCode",
                context.explanationStrategy().strategyCode());
        payload.put(
                "strategyVersion",
                context.explanationStrategy().strategyVersion());
        payload.put("prompt", context.prompt());
        payload.put("instruction", context.instruction());
        payload.put("questionContent", context.questionContent());
        payload.put("answerSpec", context.answerSpec());
        payload.put("evidenceText", context.stimulus().evidenceText());
        payload.put("evidenceSourceRole", evidenceSourceRole(context));
        payload.put("transcriptEvidenceScope",
                context.stimulus().type()
                        == com.ksh.features.practice.assessment.AssessmentStimulus.StimulusType.LISTENING_AUDIO
                        ? "LINGUISTIC_CONTENT_ONLY"
                        : "NOT_APPLICABLE");
        List<Map<String, Object>> imageDescriptors = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            ExplanationImageEvidence image = images.get(index);
            imageDescriptors.add(Map.of(
                    "imageIndex", index,
                    "role", image.role(),
                    "sha256", image.evidence().sha256()));
        }
        payload.put("questionImages", imageDescriptors);
        payload.put("teacherExplanation", context.teacherExplanation());
        payload.put("optionLabelMode", context.optionLabelMode());
        payload.put("explanationLanguage", context.explanationLanguage());
        return payload;
    }

    private String userPayload(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        try {
            return objectMapper.writeValueAsString(
                    userPayloadObject(context, images));
        } catch (Exception exception) {
            throw new ExplanationProviderException(
                    "INPUT_SERIALIZATION_FAILED",
                    "Could not serialize immutable explanation input.",
                    false,
                    exception);
        }
    }

    private boolean providerAvailable() {
        return structuredGeneration.identity(
                PracticeAiPurpose.PRACTICE_RL_EXPLANATION).available();
    }

    private String generateThroughStructuredPort(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) throws Exception {
        List<PracticeStructuredGenerationRequest.ImageEvidence> imageInputs =
                images.stream()
                        .map(image -> new PracticeStructuredGenerationRequest.ImageEvidence(
                                image.role(),
                                image.evidence().sha256(),
                                image.evidence().dataUrl(),
                                "high"))
                        .toList();
        String authorityIdentity = String.join(
                "|",
                context.schemaVersion(),
                "question=" + context.questionId(),
                "questionVersion=" + context.questionVersionId(),
                "skill=" + context.skill().name(),
                "type=" + context.questionType().name());
        PracticeStructuredGenerationRequest request =
                new PracticeStructuredGenerationRequest(
                        PracticeAiPurpose.PRACTICE_RL_EXPLANATION,
                        "reading-listening-explanation",
                        PracticeAiCapability.STRICT_STRUCTURED_TEXT_VISION,
                        new PracticeAiAuthoritySnapshot(
                                EXPLANATION_SCHEMA_VERSION,
                                EXPLANATION_PROMPT_VERSION,
                                context.explanationStrategy().strategyCode(),
                                context.explanationStrategy().strategyVersion(),
                                authorityIdentity),
                        PracticeModelCapabilityProfile.openAiAssessmentV1(),
                        systemPrompt(
                                context.questionType(),
                                context.explanationStrategy().code()),
                        "",
                        userPayloadObject(context, images),
                        "rl_answer_explanation_"
                                + context.questionType().name()
                                        .toLowerCase(java.util.Locale.ROOT),
                        schema(context, images),
                        imageInputs,
                        4096,
                        "");
        JsonNode output = structuredGeneration.generate(request).output();
        String cleaned = cleanAndValidateJson(
                objectMapper.writeValueAsString(output),
                context,
                images);
        if (cleaned == null || cleaned.isBlank()) {
            throw new ExplanationProviderException(
                    "INVALID_PROVIDER_RESPONSE",
                    "Provider response did not satisfy the explanation evidence contract.",
                    false);
        }
        return cleaned;
    }

    private static String systemPrompt(
            CanonicalQuestionType questionType,
            ObjectiveExplanationStrategyRegistry.Code strategyCode) {
        String typeRule = switch (questionType) {
            case SINGLE_CHOICE, MULTIPLE_ANSWER -> switch (strategyCode.generationFamily()) {
                case EVIDENCE ->
                        "strategyBlock chỉ có evidenceClaims, mỗi claim phải dẫn evidenceIds.";
                case OPTION_ELIMINATION ->
                        "strategyBlock chỉ có optionRationales và phải phủ đúng mọi stable option ID.";
                case FULL_CONTEXT ->
                        "strategyBlock chỉ có contextClaims và answerClaim, tất cả phải dẫn evidenceIds.";
                case EVIDENCE_AND_ELIMINATION ->
                        "strategyBlock có contextClaims, answerClaim và optionRationales, tất cả phải dẫn evidenceIds.";
                case TFNG_RELATION, FILL_CONSTRAINTS ->
                        throw new IllegalArgumentException(
                        "Option strategy is incompatible: " + strategyCode);
            };
            case MATCHING ->
                    "strategyBlock chỉ có targetExplanations, phủ đúng mọi stable target ID và dùng đúng candidateOptionId chính thức; mỗi dòng phải dẫn evidenceIds.";
            case TRUE_FALSE_NOT_GIVEN ->
                    "strategyBlock phải là CLAIM_EVIDENCE_RELATION với claim, relationClaims và missingInformationVi; không thay đổi official key.";
            case FILL_BLANK ->
                    "strategyBlock phải là CONSTRAINTS_AND_EVIDENCE với blankExplanations phủ đúng mọi stable blank ID.";
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException(
                            "Reading/Listening provider generation is not available for type "
                                    + questionType);
        };
        return """
                Bạn là giáo viên giải thích đáp án Reading/Listening cho học viên Việt Nam học tiếng Hàn.
                Explanation này thuộc nội dung câu hỏi đã xuất bản và dùng chung cho mọi học viên.
                Chỉ dùng evidenceText tiếng Hàn và ảnh nội bộ có digest được cung cấp trong request.
                Không suy diễn audio hay bằng chứng không tồn tại; không chấm hoặc nhắc learnerAnswer.
                Bản chép lời chỉ chứng minh nội dung ngôn ngữ, không chứng minh phát âm, ngữ điệu hay đặc tính âm học.
                TEXT_SPAN/TRANSCRIPT_SPAN phải có exactQuoteKo đúng tuyệt đối với startOffset/endOffset.
                IMAGE_REGION phải chép đúng role, sha256, imageIndex và chỉ rõ RECTANGLE hoặc WHOLE_IMAGE.
                Chuỗi "[IMAGE]" không phải bằng chứng hình ảnh hợp lệ.
                relevantTranslations là danh sách theo từng evidenceId; mỗi mục chỉ dịch evidence đã liên kết và ngữ cảnh tối thiểu.
                Không thay đổi, nhắc lại hay đề xuất answerSpec. Không tạo construct/taxonomy/chip.
                Mọi nhận định tiếng Việt phải nằm trong typed claim và dẫn ít nhất một evidenceId.
                Trả JSON schema v4 đúng strategy discriminator do giảng viên đã chọn; không tự đổi strategy.
                Quy tắc theo loại câu hỏi: %s
                """.formatted(typeRule);
    }

    private Map<String, Object> responseFormat(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "rl_answer_explanation_"
                                + context.questionType().name()
                                        .toLowerCase(java.util.Locale.ROOT),
                        "strict", Boolean.TRUE,
                        "schema", schema(context, images)));
    }

    private static Map<String, Object> schema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> explanationSchema = switch (context.questionType()) {
            case SINGLE_CHOICE, MULTIPLE_ANSWER ->
                    singleChoiceExplanationSchema(context, images);
            case MATCHING -> matchingExplanationSchema(context, images);
            case FILL_BLANK -> fillBlankExplanationSchema(context, images);
            case TRUE_FALSE_NOT_GIVEN -> tfngExplanationSchema(context, images);
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException(
                            "provider generation is not supported for this type");
        };
        return responseVariant(context, explanationSchema);
    }

    private static Map<String, Object> responseVariant(
            ExplanationContext context,
            Map<String, Object> explanationSchema) {
        return objectSchema(
                List.of(
                        "schemaVersion",
                        "strategyRegistryVersion",
                        "strategyCode",
                        "strategyVersion",
                        "questionType",
                        "explanation"),
                Map.of(
                        "schemaVersion", Map.of(
                                "type", "string", "const", EXPLANATION_SCHEMA_VERSION),
                        "strategyRegistryVersion", Map.of(
                                "type", "string",
                                "const",
                                context.explanationStrategy().registryVersion()),
                        "strategyCode", Map.of(
                                "type", "string",
                                "const",
                                context.explanationStrategy().strategyCode()),
                        "strategyVersion", Map.of(
                                "type", "string",
                                "const",
                                context.explanationStrategy().strategyVersion()),
                        "questionType", Map.of(
                                "type", "string",
                                "const",
                                context.questionType().name()),
                        "explanation", explanationSchema));
    }

    private static Map<String, Object> singleChoiceExplanationSchema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        List<String> optionIds = context.questionContent().options().stream()
                .map(QuestionContent.Option::id)
                .toList();
        Map<String, Object> optionRationale = objectSchema(
                List.of("claimId", "optionId", "reasonVi", "evidenceIds"),
                Map.of(
                        "claimId", Map.of("type", "string"),
                        "optionId", Map.of("type", "string", "enum", optionIds),
                        "reasonVi", Map.of("type", "string"),
                        "evidenceIds", stringArraySchema()));
        Map<String, Object> properties = commonExplanationProperties(context, images);
        Map<String, Object> strategyBlock = switch (
                context.explanationStrategy().generationFamily()) {
            case EVIDENCE -> objectSchema(
                    List.of("evidenceClaims"),
                    Map.of("evidenceClaims", claimArraySchema()));
            case OPTION_ELIMINATION -> objectSchema(
                    List.of("optionRationales"),
                    Map.of(
                            "optionRationales",
                            Map.of(
                                    "type", "array",
                                    "items", optionRationale)));
            case FULL_CONTEXT -> objectSchema(
                    List.of("contextClaims", "answerClaim"),
                    Map.of(
                            "contextClaims", claimArraySchema(),
                            "answerClaim", claimSchema()));
            case EVIDENCE_AND_ELIMINATION -> objectSchema(
                    List.of(
                            "contextClaims",
                            "answerClaim",
                            "optionRationales"),
                    Map.of(
                            "contextClaims", claimArraySchema(),
                            "answerClaim", claimSchema(),
                            "optionRationales",
                            Map.of(
                                    "type", "array",
                                    "items", optionRationale)));
            case TFNG_RELATION, FILL_CONSTRAINTS ->
                    throw new IllegalArgumentException(
                    "Invalid single-choice explanation strategy");
        };
        properties.put("strategyBlock", strategyBlock);
        return objectSchema(new ArrayList<>(properties.keySet()), properties);
    }

    private static Map<String, Object> fillBlankExplanationSchema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        List<String> blankIds = context.questionContent().blanks().stream()
                .map(QuestionContent.Blank::id)
                .toList();
        Map<String, Object> blank = objectSchema(
                List.of(
                        "claimId", "blankId", "contextExplanationVi",
                        "semanticConstraintVi",
                        "grammarConstraintVi", "registerConstraintVi", "evidenceIds"),
                Map.of(
                        "claimId", Map.of("type", "string"),
                        "blankId", Map.of("type", "string", "enum", blankIds),
                        "contextExplanationVi", Map.of("type", "string"),
                        "semanticConstraintVi", Map.of("type", "string"),
                        "grammarConstraintVi", Map.of("type", "string"),
                        "registerConstraintVi", Map.of("type", "string"),
                        "evidenceIds", stringArraySchema()));
        Map<String, Object> properties = commonExplanationProperties(context, images);
        properties.put(
                "strategyBlock",
                objectSchema(
                        List.of("blankExplanations"),
                        Map.of(
                                "blankExplanations",
                                Map.of("type", "array", "items", blank))));
        return objectSchema(new ArrayList<>(properties.keySet()), properties);
    }

    private static Map<String, Object> matchingExplanationSchema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        List<String> targetIds = context.questionContent().blanks().stream()
                .map(QuestionContent.Blank::id)
                .toList();
        List<String> candidateIds = context.questionContent().options().stream()
                .map(QuestionContent.Option::id)
                .toList();
        Map<String, Object> target = objectSchema(
                List.of(
                        "claimId", "targetId", "candidateOptionId",
                        "reasonVi", "evidenceIds"),
                Map.of(
                        "claimId", Map.of("type", "string"),
                        "targetId", Map.of("type", "string", "enum", targetIds),
                        "candidateOptionId", Map.of(
                                "type", "string", "enum", candidateIds),
                        "reasonVi", Map.of("type", "string"),
                        "evidenceIds", stringArraySchema()));
        Map<String, Object> properties = commonExplanationProperties(context, images);
        properties.put(
                "strategyBlock",
                objectSchema(
                        List.of("targetExplanations"),
                        Map.of(
                                "targetExplanations",
                                Map.of("type", "array", "items", target))));
        return objectSchema(new ArrayList<>(properties.keySet()), properties);
    }

    private static Map<String, Object> tfngExplanationSchema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> properties = commonExplanationProperties(context, images);
        properties.put(
                "strategyBlock",
                objectSchema(
                        List.of(
                                "claim",
                                "whyTrue",
                                "whyFalse",
                                "whyNotGiven",
                                "missingInformation"),
                        Map.of(
                                "claim", claimSchema(),
                                "whyTrue", claimSchema(),
                                "whyFalse", claimSchema(),
                                "whyNotGiven", claimSchema(),
                                "missingInformation", claimSchema())));
        return objectSchema(new ArrayList<>(properties.keySet()), properties);
    }

    private static Map<String, Object> commonExplanationProperties(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> textArray = new LinkedHashMap<>();
        textArray.put("type", "array");
        textArray.put("items", textEvidenceSchema(context));
        if (!context.stimulus().hasUsableEvidence()) {
            textArray.put("maxItems", 0);
        }
        properties.put("textEvidenceRefs", textArray);
        Map<String, Object> imageArray = new LinkedHashMap<>();
        imageArray.put("type", "array");
        imageArray.put("items", imageEvidenceSchema(images));
        if (images.isEmpty()) {
            imageArray.put("maxItems", 0);
        }
        properties.put("imageEvidenceRefs", imageArray);
        properties.put("relevantTranslations", Map.of(
                "type", "array",
                "items", objectSchema(
                        List.of("evidenceId", "translationVi"),
                        Map.of(
                                "evidenceId", Map.of("type", "string"),
                                "translationVi", Map.of("type", "string")))));
        return properties;
    }

    private static Map<String, Object> claimSchema() {
        return objectSchema(
                List.of("claimId", "textVi", "evidenceIds"),
                Map.of(
                        "claimId", Map.of("type", "string"),
                        "textVi", Map.of("type", "string"),
                        "evidenceIds", stringArraySchema()));
    }

    private static Map<String, Object> claimArraySchema() {
        return Map.of(
                "type", "array",
                "minItems", 1,
                "items", claimSchema());
    }

    private static Map<String, Object> textEvidenceSchema(
            ExplanationContext context) {
        String evidenceKind = evidenceKind(context);
        String evidenceRole = evidenceSourceRole(context);
        return objectSchema(
                List.of(
                        "evidenceId", "kind", "purpose", "sourceRole",
                        "exactQuoteKo", "startOffset", "endOffset"),
                Map.of(
                        "evidenceId", Map.of("type", "string"),
                        "kind", Map.of(
                                "type", "string",
                                "const", evidenceKind),
                        "purpose", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "ANSWER_RATIONALE",
                                        "OPTION_ELIMINATION",
                                        "BLANK_CONSTRAINT",
                                        "SUPPORTING",
                                        "CONTRASTING",
                                        "MISSING_INFORMATION")),
                        "sourceRole", Map.of(
                                "type", "string",
                                "const", evidenceRole),
                        "exactQuoteKo", Map.of("type", "string"),
                        "startOffset", Map.of("type", "integer", "minimum", 0),
                        "endOffset", Map.of("type", "integer", "minimum", 1)));
    }

    private static Map<String, Object> imageEvidenceSchema(
            List<ExplanationImageEvidence> images) {
        Map<String, Object> nullableNumber = Map.of("type", List.of("number", "null"));
        Map<String, Object> imageIndex = new LinkedHashMap<>();
        imageIndex.put("type", "integer");
        imageIndex.put("minimum", 0);
        if (!images.isEmpty()) {
            imageIndex.put("maximum", images.size() - 1);
        }
        return objectSchema(
                List.of(
                        "evidenceId", "kind", "purpose", "sourceRole", "assetDigest",
                        "imageIndex", "regionMode", "x", "y", "width", "height"),
                Map.ofEntries(
                        Map.entry("evidenceId", Map.of("type", "string")),
                        Map.entry("kind", Map.of("type", "string", "const", "IMAGE_REGION")),
                        Map.entry("purpose", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "ANSWER_RATIONALE",
                                        "OPTION_ELIMINATION",
                                        "BLANK_CONSTRAINT",
                                        "SUPPORTING",
                                        "CONTRASTING",
                                        "MISSING_INFORMATION"))),
                        Map.entry("sourceRole", Map.of("type", "string")),
                        Map.entry("assetDigest", Map.of("type", "string")),
                        Map.entry("imageIndex", imageIndex),
                        Map.entry("regionMode", Map.of(
                                "type", "string",
                                "enum", List.of("WHOLE_IMAGE", "RECTANGLE"))),
                        Map.entry("x", nullableNumber),
                        Map.entry("y", nullableNumber),
                        Map.entry("width", nullableNumber),
                        Map.entry("height", nullableNumber)));
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of(
                "type", "array",
                "minItems", 1,
                "items", Map.of("type", "string"));
    }

    private static Map<String, Object> objectSchema(
            List<String> required,
            Map<String, Object> properties) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "object");
        node.put("additionalProperties", Boolean.FALSE);
        node.put("required", required);
        node.put("properties", properties);
        return node;
    }

    private static void validateTypeExplanation(
            JsonNode explanation,
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Set<String> common = Set.of(
                "strategyBlock",
                "textEvidenceRefs",
                "imageEvidenceRefs",
                "relevantTranslations");
        Set<String> expected = new LinkedHashSet<>(common);
        requireFields(explanation, expected);
        Set<String> evidenceIds = validateEvidence(
                array(explanation, "textEvidenceRefs"),
                array(explanation, "imageEvidenceRefs"),
                context,
                images);
        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "objective explanation requires approved evidence");
        }
        validateRelevantTranslations(
                array(explanation, "relevantTranslations"), evidenceIds);

        JsonNode strategyBlock = object(explanation, "strategyBlock");
        switch (context.questionType()) {
            case SINGLE_CHOICE, MULTIPLE_ANSWER -> validateSingleChoiceStrategy(
                    strategyBlock, context, evidenceIds);
            case MATCHING -> validateMatchingExplanations(
                    strategyBlock, context, evidenceIds);
            case FILL_BLANK -> validateBlankExplanations(
                    strategyBlock, context, evidenceIds);
            case TRUE_FALSE_NOT_GIVEN ->
                    validateTfngExplanation(
                            strategyBlock, context, evidenceIds);
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "subjective type is not supported");
        }
    }

    private static void validateSingleChoiceStrategy(
            JsonNode strategyBlock,
            ExplanationContext context,
            Set<String> evidenceIds) {
        Set<String> claimIds = new LinkedHashSet<>();
        switch (context.explanationStrategy().generationFamily()) {
            case EVIDENCE -> {
                requireFields(strategyBlock, Set.of("evidenceClaims"));
                validateClaims(
                        array(strategyBlock, "evidenceClaims"),
                        evidenceIds,
                        claimIds);
            }
            case OPTION_ELIMINATION -> {
                requireFields(strategyBlock, Set.of("optionRationales"));
                validateOptionRationales(
                        strategyBlock,
                        context,
                        evidenceIds,
                        claimIds);
            }
            case FULL_CONTEXT -> {
                requireFields(
                        strategyBlock,
                        Set.of("contextClaims", "answerClaim"));
                validateClaims(
                        array(strategyBlock, "contextClaims"),
                        evidenceIds,
                        claimIds);
                validateClaim(
                        object(strategyBlock, "answerClaim"),
                        evidenceIds,
                        claimIds);
            }
            case EVIDENCE_AND_ELIMINATION -> {
                requireFields(
                        strategyBlock,
                        Set.of(
                                "contextClaims",
                                "answerClaim",
                                "optionRationales"));
                validateClaims(
                        array(strategyBlock, "contextClaims"),
                        evidenceIds,
                        claimIds);
                validateClaim(
                        object(strategyBlock, "answerClaim"),
                        evidenceIds,
                        claimIds);
                validateOptionRationales(
                        strategyBlock,
                        context,
                        evidenceIds,
                        claimIds);
            }
            case TFNG_RELATION, FILL_CONSTRAINTS ->
                    throw new IllegalArgumentException(
                            "Single-choice strategy is incompatible");
        }
    }

    private static void validateOptionRationales(
            JsonNode strategyBlock,
            ExplanationContext context,
            Set<String> evidenceIds,
            Set<String> claimIds) {
        Set<String> expected = context.questionContent().options().stream()
                .map(QuestionContent.Option::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : array(strategyBlock, "optionRationales")) {
            requireFields(node, Set.of(
                    "claimId", "optionId", "reasonVi", "evidenceIds"));
            requireUniqueClaimId(node, claimIds);
            String optionId = text(node, "optionId");
            if (!expected.contains(optionId) || !seen.add(optionId)) {
                throw new IllegalArgumentException(
                        "option rationale references a foreign option");
            }
            text(node, "reasonVi");
            requireEvidenceReferences(stringList(node, "evidenceIds"), evidenceIds);
        }
        if (!seen.equals(expected)) {
            throw new IllegalArgumentException(
                    "option rationale coverage is incomplete");
        }
    }

    private static void validateBlankExplanations(
            JsonNode strategyBlock,
            ExplanationContext context,
            Set<String> evidenceIds) {
        requireFields(strategyBlock, Set.of("blankExplanations"));
        Set<String> expected = context.questionContent().blanks().stream()
                .map(QuestionContent.Blank::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> seen = new LinkedHashSet<>();
        Set<String> claimIds = new LinkedHashSet<>();
        for (JsonNode node : array(strategyBlock, "blankExplanations")) {
            requireFields(node, Set.of(
                    "claimId", "blankId", "contextExplanationVi",
                    "semanticConstraintVi",
                    "grammarConstraintVi", "registerConstraintVi", "evidenceIds"));
            requireUniqueClaimId(node, claimIds);
            String blankId = text(node, "blankId");
            if (!expected.contains(blankId) || !seen.add(blankId)) {
                throw new IllegalArgumentException(
                        "blank explanation references a foreign blank");
            }
            text(node, "contextExplanationVi");
            textAllowBlank(node, "semanticConstraintVi");
            textAllowBlank(node, "grammarConstraintVi");
            textAllowBlank(node, "registerConstraintVi");
            requireEvidenceReferences(stringList(node, "evidenceIds"), evidenceIds);
        }
        if (!seen.equals(expected)) {
            throw new IllegalArgumentException(
                    "blank explanation coverage is incomplete");
        }
    }

    private static void validateMatchingExplanations(
            JsonNode strategyBlock,
            ExplanationContext context,
            Set<String> evidenceIds) {
        requireFields(strategyBlock, Set.of("targetExplanations"));
        Map<String, String> officialByTarget = context.answerSpec().blanks().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.ksh.features.practice.assessment.AnswerSpec.BlankAnswer::blankId,
                        answer -> answer.acceptedValues().size() == 1
                                ? answer.acceptedValues().get(0)
                                : ""));
        Set<String> expectedTargets = context.questionContent().blanks().stream()
                .map(QuestionContent.Blank::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> candidates = context.questionContent().options().stream()
                .map(QuestionContent.Option::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> seen = new LinkedHashSet<>();
        Set<String> claimIds = new LinkedHashSet<>();
        for (JsonNode node : array(strategyBlock, "targetExplanations")) {
            requireFields(node, Set.of(
                    "claimId", "targetId", "candidateOptionId",
                    "reasonVi", "evidenceIds"));
            requireUniqueClaimId(node, claimIds);
            String targetId = text(node, "targetId");
            String candidateId = text(node, "candidateOptionId");
            if (!expectedTargets.contains(targetId)
                    || !seen.add(targetId)
                    || !candidates.contains(candidateId)
                    || !candidateId.equals(officialByTarget.get(targetId))) {
                throw new IllegalArgumentException(
                        "matching explanation contradicts canonical target authority");
            }
            text(node, "reasonVi");
            requireEvidenceReferences(
                    stringList(node, "evidenceIds"), evidenceIds);
        }
        if (!seen.equals(expectedTargets)) {
            throw new IllegalArgumentException(
                    "matching explanation coverage is incomplete");
        }
    }

    private static void validateTfngExplanation(
            JsonNode strategyBlock,
            ExplanationContext context,
            Set<String> evidenceIds) {
        requireFields(
                strategyBlock,
                Set.of(
                        "claim",
                        "whyTrue",
                        "whyFalse",
                        "whyNotGiven",
                        "missingInformation"));
        Set<String> claimIds = new LinkedHashSet<>();
        validateClaim(
                object(strategyBlock, "claim"),
                evidenceIds,
                claimIds);
        validateClaim(object(strategyBlock, "whyTrue"), evidenceIds, claimIds);
        validateClaim(object(strategyBlock, "whyFalse"), evidenceIds, claimIds);
        validateClaim(
                object(strategyBlock, "whyNotGiven"), evidenceIds, claimIds);
        JsonNode missingInformation = object(
                strategyBlock, "missingInformation");
        validateClaim(missingInformation, evidenceIds, claimIds);
        String missing = text(missingInformation, "textVi");
        String official = context.answerSpec().correctValue() == null
                ? ""
                : context.answerSpec().correctValue().trim()
                        .replace('-', '_')
                        .replace(' ', '_')
                        .toUpperCase(java.util.Locale.ROOT);
        if ("NOT_GIVEN".equals(official) && missing.isBlank()) {
            throw new IllegalArgumentException(
                    "NOT_GIVEN requires a missing-information statement");
        }
        if (!"NOT_GIVEN".equals(official) && evidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "TRUE/FALSE requires supporting or contrasting evidence");
        }
    }

    private static void validateClaims(
            JsonNode claims,
            Set<String> evidenceIds,
            Set<String> claimIds) {
        if (claims.isEmpty()) {
            throw new IllegalArgumentException(
                    "strategy claim list must not be empty");
        }
        for (JsonNode claim : claims) {
            validateClaim(claim, evidenceIds, claimIds);
        }
    }

    private static void validateClaim(
            JsonNode claim,
            Set<String> evidenceIds,
            Set<String> claimIds) {
        requireFields(claim, Set.of("claimId", "textVi", "evidenceIds"));
        requireUniqueClaimId(claim, claimIds);
        text(claim, "textVi");
        requireEvidenceReferences(
                stringList(claim, "evidenceIds"), evidenceIds);
    }

    private static void requireUniqueClaimId(
            JsonNode claim,
            Set<String> claimIds) {
        if (!claimIds.add(text(claim, "claimId"))) {
            throw new IllegalArgumentException(
                    "strategy claim IDs must be unique");
        }
    }

    private static Set<String> validateEvidence(
            JsonNode textEvidenceNodes,
            JsonNode imageEvidenceNodes,
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode node : textEvidenceNodes) {
            String evidenceId = text(node, "evidenceId");
            if (!ids.add(evidenceId)) {
                throw new IllegalArgumentException("duplicate evidence ID");
            }
            String kind = text(node, "kind");
            if (!"TEXT_SPAN".equals(kind) && !"TRANSCRIPT_SPAN".equals(kind)) {
                throw new IllegalArgumentException("unsupported text evidence kind");
            }
            validateTextEvidence(node, context);
        }
        for (JsonNode node : imageEvidenceNodes) {
            String evidenceId = text(node, "evidenceId");
            if (!ids.add(evidenceId)) {
                throw new IllegalArgumentException("duplicate evidence ID");
            }
            if (!"IMAGE_REGION".equals(text(node, "kind"))) {
                throw new IllegalArgumentException("unsupported image evidence kind");
            }
            validateImageEvidence(node, images);
        }
        return ids;
    }

    private static void validateRelevantTranslations(
            JsonNode translationNodes,
            Set<String> evidenceIds) {
        Set<String> translatedEvidenceIds = new LinkedHashSet<>();
        for (JsonNode node : translationNodes) {
            requireFields(node, Set.of("evidenceId", "translationVi"));
            String evidenceId = text(node, "evidenceId");
            if (!evidenceIds.contains(evidenceId)
                    || !translatedEvidenceIds.add(evidenceId)) {
                throw new IllegalArgumentException(
                        "translation references foreign or duplicate evidence");
            }
            text(node, "translationVi");
        }
    }

    private static void validateTextEvidence(
            JsonNode node,
            ExplanationContext context) {
        requireFields(node, Set.of(
                "evidenceId", "kind", "purpose", "sourceRole",
                "exactQuoteKo", "startOffset", "endOffset"));
        String kind = text(node, "kind");
        String role = text(node, "sourceRole");
        String expectedKind = evidenceKind(context);
        String expectedRole = evidenceSourceRole(context);
        if (!expectedKind.equals(kind) || !expectedRole.equals(role)
                || !context.stimulus().hasUsableEvidence()) {
            throw new IllegalArgumentException(
                    "text evidence source is not authorized");
        }
        String source = context.stimulus().evidenceText();
        String quote = text(node, "exactQuoteKo");
        int start = integer(node, "startOffset");
        int end = integer(node, "endOffset");
        if (quote.contains("[IMAGE]") || start < 0 || end <= start
                || end > source.length()
                || !source.substring(start, end).equals(quote)) {
            throw new IllegalArgumentException(
                    "text evidence is not an exact approved source span");
        }
        requireEvidencePurpose(text(node, "purpose"));
    }

    private static String evidenceKind(ExplanationContext context) {
        return context.stimulus().type()
                == com.ksh.features.practice.assessment.AssessmentStimulus
                        .StimulusType.LISTENING_AUDIO
                ? "TRANSCRIPT_SPAN"
                : "TEXT_SPAN";
    }

    private static String evidenceSourceRole(
            ExplanationContext context) {
        return switch (context.stimulus().type()) {
            case READING_PASSAGE -> "PASSAGE";
            case LISTENING_AUDIO -> "TRANSCRIPT";
            case STANDALONE_PROMPT -> "QUESTION_PROMPT";
        };
    }

    private static void validateImageEvidence(
            JsonNode node,
            List<ExplanationImageEvidence> images) {
        requireFields(node, Set.of(
                "evidenceId", "kind", "purpose", "sourceRole", "assetDigest",
                "imageIndex", "regionMode", "x", "y", "width", "height"));
        int imageIndex = integer(node, "imageIndex");
        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new IllegalArgumentException(
                    "image evidence index is outside authorized images");
        }
        ExplanationImageEvidence image = images.get(imageIndex);
        if (!image.role().equals(text(node, "sourceRole"))
                || !image.evidence().sha256().equalsIgnoreCase(text(node, "assetDigest"))) {
            throw new IllegalArgumentException(
                    "image evidence digest or index is not authoritative");
        }
        String regionMode = text(node, "regionMode");
        BigDecimal x = decimalOrNull(node, "x");
        BigDecimal y = decimalOrNull(node, "y");
        BigDecimal width = decimalOrNull(node, "width");
        BigDecimal height = decimalOrNull(node, "height");
        if ("WHOLE_IMAGE".equals(regionMode)) {
            if (x != null || y != null || width != null || height != null) {
                throw new IllegalArgumentException(
                        "WHOLE_IMAGE must not include a rectangle");
            }
        } else if (!"RECTANGLE".equals(regionMode)
                || x == null || y == null || width == null || height == null
                || x.signum() < 0 || y.signum() < 0
                || width.signum() <= 0 || height.signum() <= 0) {
            throw new IllegalArgumentException(
                    "image evidence rectangle is incomplete");
        }
        requireEvidencePurpose(text(node, "purpose"));
    }

    private static void requireEvidenceReferences(
            List<String> references,
            Set<String> evidenceIds) {
        if (references.isEmpty()
                || !evidenceIds.containsAll(references)
                || new LinkedHashSet<>(references).size() != references.size()) {
            throw new IllegalArgumentException(
                    "explanation references foreign evidence");
        }
    }

    private static void requireEvidencePurpose(String purpose) {
        if (!Set.of(
                "ANSWER_RATIONALE",
                "OPTION_ELIMINATION",
                "BLANK_CONSTRAINT",
                "SUPPORTING",
                "CONTRASTING",
                "MISSING_INFORMATION").contains(purpose)) {
            throw new IllegalArgumentException(
                    "evidence purpose is outside the objective registry");
        }
    }

    private static void requireFields(JsonNode node, Set<String> expected) {
        if (!node.isObject()) {
            throw new IllegalArgumentException(
                    "typed explanation node must be an object");
        }
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "typed explanation has missing, unknown, or cross-type fields");
        }
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return node;
    }

    private static JsonNode array(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return node;
    }

    private static String text(JsonNode parent, String field) {
        String value = textAllowBlank(parent, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static String textAllowBlank(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return node.asText().trim();
    }

    private static int integer(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return node.intValue();
    }

    private static BigDecimal decimalOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric or null");
        }
        return node.decimalValue();
    }

    private static List<String> stringList(JsonNode parent, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array(parent, field)) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new IllegalArgumentException(field + " contains invalid text");
            }
            values.add(node.asText().trim());
        }
        return List.copyOf(values);
    }
}
