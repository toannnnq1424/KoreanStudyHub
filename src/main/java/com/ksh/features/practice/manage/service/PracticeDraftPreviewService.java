package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.PlayerQuestionPayload;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class PracticeDraftPreviewService {

    public static final String SCHEMA_VERSION = "practice-draft-preview-v1";

    private final PracticeDraftContractService draftContractService;
    private final AssessmentContractCodec contractCodec;
    private final QuestionTypeResolver questionTypeResolver;
    private final ObjectMapper objectMapper;

    public PracticeDraftPreviewService(PracticeDraftContractService draftContractService,
                                       AssessmentContractCodec contractCodec,
                                       QuestionTypeResolver questionTypeResolver,
                                       ObjectMapper objectMapper) {
        this.draftContractService = draftContractService;
        this.contractCodec = contractCodec;
        this.questionTypeResolver = questionTypeResolver;
        this.objectMapper = objectMapper;
    }

    public DraftDeliveryPreview preview(String draftJson) {
        PracticeDraftContractService.NormalizedDraft normalized =
                draftContractService.normalize(draftJson, "MANUAL");
        try {
            JsonNode root = objectMapper.readTree(normalized.json());
            List<PreviewSection> sections = new ArrayList<>();
            for (JsonNode section : root.path("sections")) {
                String skill = section.path("skill").asText("READING");
                List<PreviewGroup> groups = new ArrayList<>();
                for (JsonNode group : section.path("groups")) {
                    JsonNode stimulus = group.path("stimulus");
                    List<PreviewQuestion> questions = new ArrayList<>();
                    for (JsonNode question : group.path("questions")) {
                        CanonicalQuestionType type = questionTypeResolver.resolve(
                                question.path("questionType").asText());
                        QuestionContent content = deliveryContent(question, type);
                        questions.add(new PreviewQuestion(
                                PlayerQuestionPayload.SCHEMA_VERSION,
                                question.path("questionNo").asInt(questions.size() + 1),
                                type,
                                question.path("prompt").asText(""),
                                content,
                                positivePoints(question.path("points")),
                                nonNegativeInt(question.path("prepTimeSeconds")),
                                nonNegativeInt(question.path("respTimeSeconds"))
                        ));
                    }
                    groups.add(new PreviewGroup(
                            group.path("label").asText(""),
                            group.path("instruction").asText(""),
                            stimulus.path("type").asText("NONE"),
                            "READING".equals(skill) ? nullableText(stimulus.path("passageText")) : null,
                            safeMediaReference(firstNonBlank(
                                    stimulus.path("mediaReference").asText(null),
                                    group.path("audioUrl").asText(null))),
                            safeMediaReference(firstNonBlank(
                                    stimulus.path("imageReference").asText(null),
                                    group.path("imageUrl").asText(null))),
                            List.copyOf(questions)
                    ));
                }
                sections.add(new PreviewSection(
                        section.path("title").asText(""),
                        skill,
                        safeMediaReference(section.path("sectionDelivery")
                                .path("listeningDelivery")
                                .path("checkAudioReference").asText(null)),
                        List.copyOf(groups)));
            }
            return new DraftDeliveryPreview(
                    SCHEMA_VERSION,
                    root.path("document").path("title").asText("Đề luyện tập"),
                    List.copyOf(sections)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không thể tạo bản xem trước an toàn.", exception);
        }
    }

    private QuestionContent deliveryContent(JsonNode question, CanonicalQuestionType type) {
        QuestionContent content;
        try {
            JsonNode typedContent = question.path("questionContent");
            if (typedContent.isObject()) {
                content = contractCodec.readQuestionContent(typedContent.toString(), type);
            } else {
                content = contractCodec.adaptLegacyContent(
                        question.path("options").toString(), type.name());
            }
        } catch (IllegalArgumentException exception) {
            content = QuestionContent.empty();
        }
        return safeDeliveryContent(withQuestionMediaFallbacks(content, question));
    }

    private static BigDecimal positivePoints(JsonNode node) {
        BigDecimal points = node.isNumber() ? node.decimalValue() : BigDecimal.ONE;
        return points.signum() > 0 ? points : BigDecimal.ONE;
    }

    private static int nonNegativeInt(JsonNode node) {
        return Math.max(0, node.asInt(0));
    }

    private static QuestionContent withQuestionMediaFallbacks(QuestionContent content, JsonNode question) {
        String imageReference = firstNonBlank(
                content.imageReference(), question.path("imageUrl").asText(null));
        String audioReference = firstNonBlank(
                content.audioReference(), question.path("audioUrl").asText(null));
        return new QuestionContent(
                content.schemaVersion(),
                content.options(),
                content.blanks(),
                imageReference,
                audioReference,
                content.speakingDelivery());
    }

    private static QuestionContent safeDeliveryContent(QuestionContent content) {
        List<QuestionContent.Option> options = content.options().stream()
                .map(option -> new QuestionContent.Option(
                        option.id(), option.text(), safeMediaReference(option.imageReference())))
                .toList();
        QuestionContent.SpeakingDelivery speakingDelivery = content.speakingDelivery() == null
                ? null
                : new QuestionContent.SpeakingDelivery(
                        safeMediaReference(content.speakingDelivery().promptAudioReference()),
                        content.speakingDelivery().promptPlayLimit(),
                        content.speakingDelivery().preparationSeconds(),
                        content.speakingDelivery().responseSeconds());
        return new QuestionContent(
                content.schemaVersion(),
                options,
                content.blanks(),
                safeMediaReference(content.imageReference()),
                safeMediaReference(content.audioReference()),
                speakingDelivery
        );
    }

    private static String nullableText(JsonNode node) {
        String value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String safeMediaReference(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.startsWith("/") && !value.startsWith("//")) return value;
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    ? value
                    : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record DraftDeliveryPreview(String schemaVersion, String title, List<PreviewSection> sections) {
    }

    public record PreviewSection(String title,
                                 String skill,
                                 String listeningCheckAudioReference,
                                 List<PreviewGroup> groups) {
    }

    public record PreviewGroup(String label,
                               String instruction,
                               String stimulusType,
                               String passageText,
                               String mediaReference,
                               String imageReference,
                               List<PreviewQuestion> questions) {
    }

    public record PreviewQuestion(String schemaVersion,
                                  int questionNo,
                                  CanonicalQuestionType questionType,
                                  String prompt,
                                  QuestionContent content,
                                  BigDecimal points,
                                  int prepTimeSeconds,
                                  int respTimeSeconds) {
    }
}
