package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.QuestionContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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
    public static final String EXPLANATION_PROMPT_VERSION = "v8-objective-type-native";
    public static final String EXPLANATION_SCHEMA_VERSION = "v3";
    public static final String LEGACY_EXPLANATION_SCHEMA_VERSION = "v2";
    public static final String EXPLANATION_LANGUAGE = "vi";

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public ReadingListeningExplanationClient(
            OpenAiProperties properties,
            ObjectMapper objectMapper,
            @Value("${app.practice.explanation-generation.provider-timeout:60s}") Duration providerTimeout) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .requestFactory(requestFactory(providerTimeout))
                .build();
    }

    ReadingListeningExplanationClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Duration.ofSeconds(60));
    }

    public String generate(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        List<ExplanationImageEvidence> safeImages = images == null ? List.of() : List.copyOf(images);
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ExplanationProviderException(
                    "PROVIDER_NOT_CONFIGURED", "AI provider key is not configured.", false);
        }
        if (!context.stimulus().hasUsableEvidence() && safeImages.isEmpty()) {
            throw new ExplanationProviderException(
                    "EVIDENCE_UNAVAILABLE", "No approved text or image evidence is available.", false);
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.evaluatorModel());
        request.put("temperature", 0.0);
        request.put("response_format", responseFormat(context, safeImages));
        request.put("messages", List.of(
                message("system", systemPrompt(context.questionType())),
                message("user", multimodalContent(userPayload(context, safeImages), safeImages))
        ));

        log.info("[ReadingListeningAI] generate model={} skill={} type={}",
                properties.evaluatorModel(), context.skill(), context.questionType());
        String raw = callOnce(request, context.skill().name());
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = extractOutputText(root, raw);
            String cleaned = cleanAndValidateJson(content, context, safeImages);
            if (cleaned == null || cleaned.isBlank()) {
                throw new ExplanationProviderException(
                        "INVALID_PROVIDER_RESPONSE",
                        "Provider response did not satisfy the explanation evidence contract.",
                        true);
            }
            return cleaned;
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
            requireFields(root, Set.of("schemaVersion", "questionType", "explanation"));
            if (!EXPLANATION_SCHEMA_VERSION.equals(text(root, "schemaVersion"))
                    || !context.questionType().name().equals(text(root, "questionType"))) {
                return null;
            }
            JsonNode explanation = object(root, "explanation");
            validateTypeExplanation(explanation, context, images == null ? List.of() : images);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            log.warn("[ReadingListeningAI] explanation cleaning failed type={} exception={}",
                    context.questionType(), exception.getClass().getSimpleName());
            return null;
        }
    }

    public String model() {
        return properties.evaluatorModel();
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

    private String userPayload(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contextSchemaVersion", context.schemaVersion());
        payload.put("skill", context.skill().name());
        payload.put("questionType", context.questionType().name());
        payload.put("prompt", context.prompt());
        payload.put("instruction", context.instruction());
        payload.put("questionContent", context.questionContent());
        payload.put("answerSpec", context.answerSpec());
        payload.put("evidenceText", context.stimulus().evidenceText());
        payload.put("evidenceSourceRole",
                context.stimulus().type()
                        == com.ksh.features.practice.assessment.AssessmentStimulus.StimulusType.READING_PASSAGE
                        ? "PASSAGE"
                        : "TRANSCRIPT");
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
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new ExplanationProviderException(
                    "INPUT_SERIALIZATION_FAILED",
                    "Could not serialize immutable explanation input.",
                    false,
                    exception);
        }
    }

    private static String systemPrompt(CanonicalQuestionType questionType) {
        String typeRule = switch (questionType) {
            case SINGLE_CHOICE ->
                    "optionRationales phải có đúng một dòng cho mọi stable option ID, gồm cả đáp án đúng và từng phương án bị loại.";
            case TRUE_FALSE_NOT_GIVEN ->
                    "Không trả lại relation/official key. Giải thích bằng whyTrueVi/whyFalseVi/whyNotGivenVi; NOT_GIVEN phải nêu thông tin còn thiếu.";
            case FILL_BLANK ->
                    "blankExplanations phải có đúng một dòng cho mọi stable blank ID; không trả lại hoặc đề xuất acceptedValues/alias mới.";
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "Reading/Listening explanation does not support subjective type " + questionType);
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
                Trả JSON schema v3 đúng discriminator, lời giải hướng đến học viên bằng tiếng Việt.
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
            case SINGLE_CHOICE -> singleChoiceExplanationSchema(context, images);
            case FILL_BLANK -> fillBlankExplanationSchema(context, images);
            case TRUE_FALSE_NOT_GIVEN -> tfngExplanationSchema(context, images);
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "subjective type is not supported");
        };
        return responseVariant(context.questionType().name(), explanationSchema);
    }

    private static Map<String, Object> responseVariant(
            String questionType,
            Map<String, Object> explanationSchema) {
        return objectSchema(
                List.of("schemaVersion", "questionType", "explanation"),
                Map.of(
                        "schemaVersion", Map.of(
                                "type", "string", "const", EXPLANATION_SCHEMA_VERSION),
                        "questionType", Map.of("type", "string", "const", questionType),
                        "explanation", explanationSchema));
    }

    private static Map<String, Object> singleChoiceExplanationSchema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        List<String> optionIds = context.questionContent().options().stream()
                .map(QuestionContent.Option::id)
                .toList();
        Map<String, Object> rationale = objectSchema(
                List.of("optionId", "reasonVi", "evidenceIds"),
                Map.of(
                        "optionId", Map.of("type", "string", "enum", optionIds),
                        "reasonVi", Map.of("type", "string"),
                        "evidenceIds", stringArraySchema()));
        Map<String, Object> properties = commonExplanationProperties(context, images);
        properties.put("optionRationales", Map.of("type", "array", "items", rationale));
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
                        "blankId", "contextExplanationVi", "semanticConstraintVi",
                        "grammarConstraintVi", "registerConstraintVi", "evidenceIds"),
                Map.of(
                        "blankId", Map.of("type", "string", "enum", blankIds),
                        "contextExplanationVi", Map.of("type", "string"),
                        "semanticConstraintVi", Map.of("type", "string"),
                        "grammarConstraintVi", Map.of("type", "string"),
                        "registerConstraintVi", Map.of("type", "string"),
                        "evidenceIds", stringArraySchema()));
        Map<String, Object> properties = commonExplanationProperties(context, images);
        properties.put("blankExplanations", Map.of("type", "array", "items", blank));
        return objectSchema(new ArrayList<>(properties.keySet()), properties);
    }

    private static Map<String, Object> tfngExplanationSchema(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> properties = commonExplanationProperties(context, images);
        properties.put("relationExplanationVi", Map.of("type", "string"));
        properties.put("whyTrueVi", Map.of("type", "string"));
        properties.put("whyFalseVi", Map.of("type", "string"));
        properties.put("whyNotGivenVi", Map.of("type", "string"));
        properties.put("missingInformationVi", Map.of("type", "string"));
        return objectSchema(new ArrayList<>(properties.keySet()), properties);
    }

    private static Map<String, Object> commonExplanationProperties(
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("meaningVi", Map.of("type", "string"));
        properties.put("correctReasonVi", Map.of("type", "string"));
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

    private static Map<String, Object> textEvidenceSchema(
            ExplanationContext context) {
        boolean reading = context.stimulus().type()
                == com.ksh.features.practice.assessment.AssessmentStimulus.StimulusType.READING_PASSAGE;
        return objectSchema(
                List.of(
                        "evidenceId", "kind", "purpose", "sourceRole",
                        "exactQuoteKo", "startOffset", "endOffset"),
                Map.of(
                        "evidenceId", Map.of("type", "string"),
                        "kind", Map.of(
                                "type", "string",
                                "const", reading ? "TEXT_SPAN" : "TRANSCRIPT_SPAN"),
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
                                "const", reading ? "PASSAGE" : "TRANSCRIPT"),
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
                "meaningVi", "correctReasonVi", "textEvidenceRefs",
                "imageEvidenceRefs", "relevantTranslations");
        Set<String> expected = new LinkedHashSet<>(common);
        switch (context.questionType()) {
            case SINGLE_CHOICE -> expected.add("optionRationales");
            case FILL_BLANK -> expected.add("blankExplanations");
            case TRUE_FALSE_NOT_GIVEN -> expected.addAll(List.of(
                    "relationExplanationVi", "whyTrueVi", "whyFalseVi",
                    "whyNotGivenVi", "missingInformationVi"));
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "subjective type is not supported");
        }
        requireFields(explanation, expected);
        text(explanation, "meaningVi");
        text(explanation, "correctReasonVi");
        Set<String> evidenceIds = validateEvidence(
                array(explanation, "textEvidenceRefs"),
                array(explanation, "imageEvidenceRefs"),
                context,
                images);
        if ((context.questionType() == CanonicalQuestionType.SINGLE_CHOICE
                || context.questionType() == CanonicalQuestionType.FILL_BLANK)
                && evidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "objective explanation requires approved evidence");
        }
        validateRelevantTranslations(
                array(explanation, "relevantTranslations"), evidenceIds);

        switch (context.questionType()) {
            case SINGLE_CHOICE ->
                    validateOptionRationales(explanation, context, evidenceIds);
            case FILL_BLANK ->
                    validateBlankExplanations(explanation, context, evidenceIds);
            case TRUE_FALSE_NOT_GIVEN ->
                    validateTfngExplanation(explanation, context, evidenceIds);
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "subjective type is not supported");
        }
    }

    private static void validateOptionRationales(
            JsonNode explanation,
            ExplanationContext context,
            Set<String> evidenceIds) {
        Set<String> expected = context.questionContent().options().stream()
                .map(QuestionContent.Option::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : array(explanation, "optionRationales")) {
            requireFields(node, Set.of("optionId", "reasonVi", "evidenceIds"));
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
            JsonNode explanation,
            ExplanationContext context,
            Set<String> evidenceIds) {
        Set<String> expected = context.questionContent().blanks().stream()
                .map(QuestionContent.Blank::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : array(explanation, "blankExplanations")) {
            requireFields(node, Set.of(
                    "blankId", "contextExplanationVi", "semanticConstraintVi",
                    "grammarConstraintVi", "registerConstraintVi", "evidenceIds"));
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

    private static void validateTfngExplanation(
            JsonNode explanation,
            ExplanationContext context,
            Set<String> evidenceIds) {
        text(explanation, "relationExplanationVi");
        text(explanation, "whyTrueVi");
        text(explanation, "whyFalseVi");
        text(explanation, "whyNotGivenVi");
        String missing = textAllowBlank(explanation, "missingInformationVi");
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
        String expectedKind = context.stimulus().type()
                == com.ksh.features.practice.assessment.AssessmentStimulus.StimulusType.READING_PASSAGE
                ? "TEXT_SPAN"
                : "TRANSCRIPT_SPAN";
        String expectedRole = "TEXT_SPAN".equals(expectedKind)
                ? "PASSAGE"
                : "TRANSCRIPT";
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
        if (!evidenceIds.containsAll(references)
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

    private String callOnce(Map<String, Object> request, String skill) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            log.warn("[ReadingListeningAI] provider HTTP failure status={} model={} skill={}",
                    status, properties.evaluatorModel(), skill);
            throw new ExplanationProviderException(
                    "PROVIDER_HTTP_" + status,
                    "Provider request failed with HTTP " + status + ".",
                    retryableStatus(status),
                    exception);
        } catch (Exception exception) {
            log.warn("[ReadingListeningAI] provider call failed skill={} exception={}",
                    skill, exception.getClass().getSimpleName());
            throw new ExplanationProviderException(
                    "PROVIDER_TRANSPORT_ERROR",
                    "Provider request could not be completed.",
                    true,
                    exception);
        }
    }

    private static List<Map<String, Object>> multimodalContent(
            String payload,
            List<ExplanationImageEvidence> images) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", payload));
        for (ExplanationImageEvidence image : images) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", image.evidence().dataUrl(),
                            "detail", "high")));
        }
        return content;
    }

    private static Map<String, Object> message(String role, Object content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String extractOutputText(JsonNode root, String raw) {
        JsonNode choice = root.path("choices").path(0);
        return choice.path("message").hasNonNull("content")
                ? choice.path("message").path("content").asText()
                : raw;
    }

    private static boolean retryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(QuestionExplanationTaskTransactions.LEASE_DURATION) >= 0) {
            throw new IllegalArgumentException(
                    "Explanation provider timeout must be positive and shorter than the task lease");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.toIntExact(Math.min(timeout.toMillis(), Integer.MAX_VALUE));
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }
}
