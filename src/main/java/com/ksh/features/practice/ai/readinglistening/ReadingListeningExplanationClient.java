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

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReadingListeningExplanationClient {

    private static final Logger log = LoggerFactory.getLogger(ReadingListeningExplanationClient.class);
    public static final String EXPLANATION_PROMPT_VERSION = "v7";
    public static final String EXPLANATION_SCHEMA_VERSION = "v2";
    public static final String EXPLANATION_LANGUAGE = "vi";
    private static final String IMAGE_EVIDENCE_PREFIX = "[IMAGE] ";

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
        request.put("response_format", responseFormat());
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
            String cleaned = cleanAndValidateJson(content, context, !safeImages.isEmpty());
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
        try {
            JsonNode root = objectMapper.readTree(aiJson);
            Map<String, QuestionContent.Option> optionsById = new LinkedHashMap<>();
            for (QuestionContent.Option option : context.questionContent().options()) {
                optionsById.put(option.id(), option);
            }
            Set<String> correctOptionIds = new LinkedHashSet<>(context.answerSpec().correctOptionIds());
            List<Map<String, String>> eliminated = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            if (root.path("eliminatedOptions").isArray()) {
                for (JsonNode item : root.path("eliminatedOptions")) {
                    String optionId = item.path("optionKey").asText("").trim();
                    String reason = item.path("reasonVi").asText("").trim();
                    if (optionId.isBlank() || reason.isBlank() || seen.contains(optionId)
                            || correctOptionIds.contains(optionId) || !optionsById.containsKey(optionId)) {
                        continue;
                    }
                    eliminated.add(Map.of("optionKey", optionId, "reasonVi", reason));
                    seen.add(optionId);
                }
            }

            String meaningVi = root.path("meaningVi").asText("").trim();
            String evidenceQuote = root.path("evidenceQuote").asText("").trim();
            String correctReasonVi = root.path("correctReasonVi").asText("").trim();
            String relatedTranslationVi = root.path("relatedTranslationVi").asText("").trim();
            String evidenceText = context.stimulus().evidenceText();
            boolean textualEvidence = context.stimulus().hasUsableEvidence();
            boolean visualEvidence = hasImageEvidence
                    && evidenceQuote.startsWith(IMAGE_EVIDENCE_PREFIX)
                    && evidenceQuote.length() > IMAGE_EVIDENCE_PREFIX.length();
            boolean quotedTextEvidence = textualEvidence
                    && !evidenceQuote.startsWith(IMAGE_EVIDENCE_PREFIX)
                    && normalizeEvidence(evidenceText).contains(normalizeEvidence(evidenceQuote));
            if (meaningVi.isBlank() || correctReasonVi.isBlank()
                    || (!visualEvidence && !quotedTextEvidence)) {
                return null;
            }

            Map<String, Object> cleaned = new LinkedHashMap<>();
            cleaned.put("meaningVi", meaningVi);
            cleaned.put("evidenceQuote", visualEvidence
                    ? evidenceQuote.substring(IMAGE_EVIDENCE_PREFIX.length()).trim()
                    : evidenceQuote);
            cleaned.put("evidenceKind", visualEvidence ? "IMAGE" : "TEXT");
            cleaned.put("correctReasonVi", correctReasonVi);
            cleaned.put("relatedTranslationVi", relatedTranslationVi);
            cleaned.put("eliminatedOptions", eliminated);
            return objectMapper.writeValueAsString(cleaned);
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
        payload.put("questionImages", images.stream()
                .map(image -> Map.of(
                        "role", image.role(),
                        "sha256", image.evidence().sha256()))
                .toList());
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
            case SINGLE_CHOICE -> "Giải thích đúng một correctOptionId và loại từng option ID còn lại.";
            case TRUE_FALSE_NOT_GIVEN ->
                    "Phân biệt nghiêm ngặt TRUE, FALSE và NOT_GIVEN dựa trên bằng chứng.";
            case FILL_BLANK ->
                    "Giải thích từng blank ID và acceptedValues; không tự thêm đáp án mới.";
            case ESSAY, SPEAKING -> throw new IllegalArgumentException(
                    "Reading/Listening explanation does not support subjective type " + questionType);
        };
        return """
                Bạn là giáo viên giải thích đáp án Reading/Listening cho học viên Việt Nam học tiếng Hàn.
                Explanation này thuộc nội dung câu hỏi đã xuất bản và dùng chung cho mọi học viên.
                Chỉ dùng evidenceText và ảnh nội bộ được cung cấp trong request.
                Không suy diễn audio hay bằng chứng không tồn tại; không chấm hoặc nhắc learnerAnswer.
                Nếu trích evidenceText, evidenceQuote phải là chuỗi con chính xác.
                Nếu dùng ảnh, evidenceQuote phải bắt đầu chính xác bằng "[IMAGE] ".
                eliminatedOptions phải dùng stable option ID trong questionContent.
                Không thay đổi answerSpec. Trả JSON đúng schema bằng tiếng Việt.
                Quy tắc theo loại câu hỏi: %s
                """.formatted(typeRule);
    }

    private Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "rl_answer_explanation",
                        "strict", Boolean.TRUE,
                        "schema", schema()));
    }

    private static Map<String, Object> schema() {
        Map<String, Object> eliminated = objectSchema(
                List.of("optionKey", "reasonVi"),
                Map.of(
                        "optionKey", Map.of("type", "string"),
                        "reasonVi", Map.of("type", "string")));
        return objectSchema(
                List.of("meaningVi", "evidenceQuote", "correctReasonVi",
                        "relatedTranslationVi", "eliminatedOptions"),
                Map.of(
                        "meaningVi", Map.of("type", "string"),
                        "evidenceQuote", Map.of("type", "string"),
                        "correctReasonVi", Map.of("type", "string"),
                        "relatedTranslationVi", Map.of("type", "string"),
                        "eliminatedOptions", Map.of("type", "array", "items", eliminated)));
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

    private static String normalizeEvidence(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim();
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
