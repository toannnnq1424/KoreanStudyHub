package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
            "v10-evidence-specific-lecturer-strategy";
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

    /**
     * Converts a strict-JSON provider response into a conservative draft when
     * its strategy payload is incomplete. It deliberately reuses only source
     * evidence supplied by the provider and canonical answer identifiers from
     * the immutable question; otherwise it returns {@code null}.
     */
    private String recoverTypedStrategyDraft(
            String aiJson,
            ExplanationContext context,
            List<ExplanationImageEvidence> images) {
        try {
            JsonNode parsed = objectMapper.readTree(aiJson);
            if (!(parsed instanceof ObjectNode parsedRoot)
                    || !(parsedRoot.path("explanation") instanceof ObjectNode original)) {
                return null;
            }
            ObjectNode recovered = objectMapper.createObjectNode();
            recovered.put("schemaVersion", EXPLANATION_SCHEMA_VERSION);
            recovered.put("strategyRegistryVersion",
                    context.explanationStrategy().registryVersion());
            recovered.put("strategyCode", context.explanationStrategy().strategyCode());
            recovered.put("strategyVersion", context.explanationStrategy().strategyVersion());
            recovered.put("questionType", context.questionType().name());

            ObjectNode explanation = objectMapper.createObjectNode();
            explanation.set("textEvidenceRefs", original.path("textEvidenceRefs").isArray()
                    ? original.path("textEvidenceRefs").deepCopy()
                    : objectMapper.createArrayNode());
            explanation.set("imageEvidenceRefs", original.path("imageEvidenceRefs").isArray()
                    ? original.path("imageEvidenceRefs").deepCopy()
                    : objectMapper.createArrayNode());
            // Keep provider translations while repairing only an incomplete
            // strategy block. The table renderer needs the translation tied
            // to its primary evidence; discarding an otherwise valid one made
            // a recoverable provider response fail after transport succeeded.
            explanation.set("relevantTranslations",
                    original.path("relevantTranslations").isArray()
                            ? original.path("relevantTranslations").deepCopy()
                            : objectMapper.createArrayNode());
            repairTextEvidenceOffsets(explanation, context);
            RecoveryEvidence evidence = firstRecoveryEvidence(explanation);
            if (evidence == null) {
                return null;
            }
            explanation.set("strategyBlock", recoveredStrategyBlock(
                    context, evidence));
            recovered.set("explanation", explanation);
            String value = objectMapper.writeValueAsString(recovered);
            String validated = cleanAndValidateJson(value, context, images);
            if (validated != null) {
                log.info("[ReadingListeningAI] recovered incomplete provider strategy type={} strategy={}",
                        context.questionType(),
                        context.explanationStrategy().strategyCode());
            }
            return validated;
        } catch (Exception exception) {
            log.warn("[ReadingListeningAI] provider recovery failed type={} exception={}",
                    context.questionType(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private ObjectNode recoveredStrategyBlock(
            ExplanationContext context,
            RecoveryEvidence evidence) {
        ObjectNode block = objectMapper.createObjectNode();
        ArrayNode evidenceIds = objectMapper.createArrayNode().add(evidence.id());
        String question = concise(context.prompt());
        String quote = concise(evidence.quoteKo());
        String sourceAnchor = "Đoạn nguồn nêu “" + quote + "”";
        switch (context.questionType()) {
            case SINGLE_CHOICE, MULTIPLE_ANSWER -> {
                switch (context.explanationStrategy().generationFamily()) {
                    case EVIDENCE -> block.set("evidenceClaims", objectMapper.createArrayNode()
                            .add(recoveredClaim("claim-1",
                                    sourceAnchor + "; đây là chi tiết dùng để trả lời câu hỏi “"
                                            + question + "”.",
                                    evidenceIds)));
                    case FULL_CONTEXT -> {
                        block.set("contextClaims", objectMapper.createArrayNode()
                                .add(recoveredClaim("context-1",
                                        sourceAnchor + "; nó tạo ngữ cảnh trực tiếp cho câu hỏi “"
                                                + question + "”.",
                                        evidenceIds)));
                        block.set("answerClaim", recoveredClaim("answer-1",
                                "Khi đặt câu hỏi “" + question + "” cạnh chi tiết “"
                                        + quote + "”, chỉ phương án được chọn đáp ứng thông tin nguồn.",
                                evidenceIds));
                    }
                    case OPTION_ELIMINATION -> block.set("optionRationales",
                            recoveredOptionRationales(context, evidenceIds, quote));
                    case EVIDENCE_AND_ELIMINATION -> {
                        block.set("contextClaims", objectMapper.createArrayNode()
                                .add(recoveredClaim("context-1",
                                        sourceAnchor + "; nó tạo ngữ cảnh trực tiếp cho câu hỏi “"
                                                + question + "”.",
                                        evidenceIds)));
                        block.set("answerClaim", recoveredClaim("answer-1",
                                "Chi tiết “" + quote + "” là căn cứ để giữ phương án phù hợp với câu hỏi “"
                                        + question + "”.",
                                evidenceIds));
                        block.set("optionRationales",
                                recoveredOptionRationales(context, evidenceIds, quote));
                    }
                    default -> throw new IllegalArgumentException("Unsupported option strategy");
                }
            }
            case MATCHING -> {
                ArrayNode rows = objectMapper.createArrayNode();
                for (QuestionContent.Blank blank : context.questionContent().blanks()) {
                    String accepted = context.answerSpec().blanks().stream()
                            .filter(answer -> blank.id().equals(answer.blankId()))
                            .flatMap(answer -> answer.acceptedValues().stream())
                            .findFirst().orElse(null);
                    if (accepted == null) throw new IllegalArgumentException("Missing canonical matching answer");
                    ObjectNode row = objectMapper.createObjectNode();
                    row.put("claimId", "target-" + blank.id());
                    row.put("targetId", blank.id());
                    row.put("candidateOptionId", accepted);
                    row.put("reasonVi", "Mục “" + concise(blank.prompt()) + "” ghép với “"
                            + concise(optionText(context, accepted)) + "” vì đoạn nguồn nêu “"
                            + quote + "”.");
                    row.set("evidenceIds", evidenceIds.deepCopy());
                    rows.add(row);
                }
                block.set("targetExplanations", rows);
            }
            case FILL_BLANK -> {
                ArrayNode rows = objectMapper.createArrayNode();
                for (QuestionContent.Blank blank : context.questionContent().blanks()) {
                    ObjectNode row = objectMapper.createObjectNode();
                    row.put("claimId", "blank-" + blank.id());
                    row.put("blankId", blank.id());
                    row.put("contextExplanationVi", "Ô “" + concise(blank.prompt())
                            + "” được xác định từ đoạn nguồn “" + quote + "”.");
                    row.put("semanticConstraintVi", "");
                    row.put("grammarConstraintVi", "");
                    row.put("registerConstraintVi", "");
                    row.set("evidenceIds", evidenceIds.deepCopy());
                    rows.add(row);
                }
                block.set("blankExplanations", rows);
            }
            case TRUE_FALSE_NOT_GIVEN -> {
                block.set("claim", recoveredClaim("claim-1",
                        "Mệnh đề “" + question + "” được xét dựa trên đoạn “" + quote + "”.", evidenceIds));
                block.set("whyTrue", recoveredClaim("true-1",
                        "TRUE chỉ phù hợp khi đoạn “" + quote
                                + "” xác nhận đầy đủ các chi tiết của mệnh đề.", evidenceIds));
                block.set("whyFalse", recoveredClaim("false-1",
                        "FALSE chỉ phù hợp khi đoạn “" + quote
                                + "” nêu chi tiết trái với mệnh đề.", evidenceIds));
                block.set("whyNotGiven", recoveredClaim("not-given-1",
                        "NOT GIVEN áp dụng khi đoạn “" + quote
                                + "” chưa cho chi tiết cần để kết luận mệnh đề.", evidenceIds));
                block.set("missingInformation", recoveredClaim("missing-1",
                        "Không thể thêm điều kiện ngoài đoạn “" + quote
                                + "” để suy ra phần còn thiếu của mệnh đề.", evidenceIds));
            }
            case ESSAY, SPEAKING -> throw new IllegalArgumentException("Unsupported subjective type");
        }
        return block;
    }

    private ObjectNode recoveredClaim(
            String claimId,
            String textVi,
            ArrayNode evidenceIds) {
        ObjectNode claim = objectMapper.createObjectNode();
        claim.put("claimId", claimId);
        claim.put("textVi", textVi);
        claim.set("evidenceIds", evidenceIds.deepCopy());
        return claim;
    }

    private ArrayNode recoveredOptionRationales(
            ExplanationContext context,
            ArrayNode evidenceIds,
            String evidenceQuote) {
        Set<String> correct = new LinkedHashSet<>(context.answerSpec().correctOptionIds());
        ArrayNode rows = objectMapper.createArrayNode();
        for (QuestionContent.Option option : context.questionContent().options()) {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("claimId", "option-" + option.id());
            row.put("optionId", option.id());
            row.put("reasonVi", correct.contains(option.id())
                    ? "Phương án “" + concise(option.text())
                            + "” phù hợp với chi tiết “" + evidenceQuote
                            + "” trong nguồn."
                    : "Phương án “" + concise(option.text())
                            + "” không có căn cứ trực tiếp trong chi tiết “"
                            + evidenceQuote + "” của nguồn.");
            row.set("evidenceIds", evidenceIds.deepCopy());
            rows.add(row);
        }
        return rows;
    }

    private static void repairTextEvidenceOffsets(
            ObjectNode explanation,
            ExplanationContext context) {
        JsonNode references = explanation.path("textEvidenceRefs");
        if (!(references instanceof ArrayNode rows)
                || !context.stimulus().hasUsableEvidence()) {
            return;
        }
        String source = context.stimulus().evidenceText();
        for (JsonNode item : rows) {
            if (!(item instanceof ObjectNode row)) continue;
            String quote = row.path("exactQuoteKo").asText("");
            int start = row.path("startOffset").canConvertToInt()
                    ? row.path("startOffset").intValue() : -1;
            int end = row.path("endOffset").canConvertToInt()
                    ? row.path("endOffset").intValue() : -1;
            if (quote.isBlank() || (start >= 0 && end > start
                    && end <= source.length()
                    && source.substring(start, end).equals(quote))) {
                continue;
            }
            List<Integer> occurrences = new ArrayList<>();
            for (int index = source.indexOf(quote); index >= 0;
                 index = source.indexOf(quote, index + Math.max(1, quote.length()))) {
                occurrences.add(index);
            }
            // A repeated quote has no trustworthy offset without the model's
            // original location. Let strict validation reject it rather than
            // silently attaching the explanation to a different occurrence.
            if (occurrences.size() != 1) continue;
            row.put("startOffset", occurrences.get(0));
            row.put("endOffset", occurrences.get(0) + quote.length());
        }
    }

    private static RecoveryEvidence firstRecoveryEvidence(ObjectNode explanation) {
        JsonNode values = explanation.path("textEvidenceRefs");
        if (!values.isArray()) return null;
        for (JsonNode value : values) {
            String id = value.path("evidenceId").asText("").trim();
            String quote = value.path("exactQuoteKo").asText("").trim();
            if (!id.isEmpty() && !quote.isEmpty()) {
                return new RecoveryEvidence(id, quote);
            }
        }
        return null;
    }

    private record RecoveryEvidence(String id, String quoteKo) {
    }

    private static String optionText(ExplanationContext context, String optionId) {
        return context.questionContent().options().stream()
                .filter(option -> option.id().equals(optionId))
                .map(QuestionContent.Option::text)
                .findFirst()
                .orElse(optionId);
    }

    private static String concise(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return "nội dung nguồn";
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 177) + "…";
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
        String raw = objectMapper.writeValueAsString(output);
        String cleaned = cleanAndValidateJson(raw, context, images);
        if (cleaned == null || cleaned.isBlank()) {
            // A provider's small presentation drift must not erase an
            // evidence-grounded lecturer draft. The recovery path only uses
            // immutable answer/evidence authority and never invents a span.
            cleaned = recoverTypedStrategyDraft(raw, context, images);
        }
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
        String strategyRule = strategySpecificRule(strategyCode);
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
                Không bỏ field khi không chắc cách diễn đạt: dùng evidenceId đã tạo, dùng optionId/blankId/targetId đúng nguyên văn từ input,
                và viết nhận định ngắn. Không dùng nhãn A/B/C tự suy ra thay cho stable ID; không tự đổi offset hoặc cắt exactQuoteKo.
                Chất lượng lời giải là bắt buộc: mỗi nhận định phải nêu chi tiết thực tế của câu hỏi/phương án hoặc mệnh đề VÀ chi tiết
                trong exactQuoteKo mà nó đang dùng. Với phương án đúng, giải thích điểm phù hợp; với phương án không chọn, chỉ ra chi tiết
                không được span nguồn hỗ trợ hoặc mâu thuẫn với span. Với MATCHING/FILL_BLANK, nêu nội dung target/ô trống và lựa chọn/ràng buộc
                tương ứng. Không viết các câu khuôn mẫu như “đáp án chính thức”, “đối chiếu với vùng nguồn”, “có bằng chứng”, “xem bằng chứng”,
                hoặc chỉ nói phương án “không khớp” mà không nhắc nội dung cụ thể. Mỗi reasonVi/textVi nên là 1–2 câu ngắn, hữu ích cho người học.
                Trả JSON schema v4 đúng strategy discriminator do giảng viên đã chọn; không tự đổi strategy.
                Quy tắc theo loại câu hỏi: %s
                Yêu cầu riêng của chiến lược đã chọn: %s
                """.formatted(typeRule, strategyRule);
    }

    private static String strategySpecificRule(
            ObjectiveExplanationStrategyRegistry.Code strategyCode) {
        return switch (strategyCode) {
            case FULL_SOURCE_INLINE_HIGHLIGHT ->
                    "FULL_SOURCE_INLINE_HIGHLIGHT: contextClaims phải lần lượt giải thích các chi tiết nguồn được tô sáng; "
                            + "relevantTranslations phải dịch evidence đầu tiên. Không dùng một claim chung chung thay cho nội dung span.";
            case QUESTION_EVIDENCE_TRANSLATION_TABLE ->
                    "QUESTION_EVIDENCE_TRANSLATION_TABLE: relevantTranslations phải có ít nhất một mục cho evidenceId đầu tiên "
                            + "trong textEvidenceRefs; translationVi là bản dịch tiếng Việt của chính exactQuoteKo đó, không viết placeholder.";
            case EVIDENCE_AND_ELIMINATION ->
                    "EVIDENCE_AND_ELIMINATION: nêu chi tiết span xác nhận đáp án và, với từng phương án không chọn, "
                            + "nêu đúng chi tiết khiến phương án đó sai; dịch evidence đầu tiên trong relevantTranslations.";
            case KEYWORD_PARAPHRASE_BRIDGE ->
                    "KEYWORD_PARAPHRASE_BRIDGE: contextClaims phải chỉ ra cụm từ khóa trong câu hỏi/phương án và cụm diễn đạt tương đương "
                            + "hoặc chi tiết đối chiếu trong nguồn; answerClaim kết luận từ cầu nối cụ thể đó, không dùng câu mẫu; "
                            + "dịch evidence đầu tiên trong relevantTranslations.";
            case BILINGUAL_STEP_BY_STEP ->
                    "BILINGUAL_STEP_BY_STEP: viết contextClaims theo tiến trình ngắn 'Bước 1', 'Bước 2' (và 'Bước 3' nếu cần): "
                            + "đọc chi tiết nguồn, đối chiếu với câu hỏi, rồi kết luận; dịch evidence đầu tiên trong relevantTranslations.";
            default -> "Bám sát mô tả chiến lược và evidence authority trong request.";
        };
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
                            exactCoverageArraySchema(
                                    optionIds.size(), optionRationale)));
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
                            exactCoverageArraySchema(
                                    optionIds.size(), optionRationale)));
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
                                exactCoverageArraySchema(blankIds.size(), blank))));
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
                                exactCoverageArraySchema(targetIds.size(), target))));
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
        Map<String, Object> translations = new LinkedHashMap<>();
        translations.put("type", "array");
        translations.put("items", objectSchema(
                List.of("evidenceId", "translationVi"),
                Map.of(
                        "evidenceId", Map.of("type", "string"),
                        "translationVi", Map.of("type", "string"))));
        // This renderer has a dedicated translation row. Letting the model
        // omit it made a technically valid envelope look empty to lecturers.
        if (requiresPrimaryTranslation(context)) {
            translations.put("minItems", 1);
        }
        properties.put("relevantTranslations", translations);
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

    /**
     * The semantic validator requires one rationale for every stable option,
     * blank, or matching target.  Mirror that cardinality in the provider
     * schema so a strict-schema provider cannot return a superficially valid
     * but incomplete strategy block.
     */
    private static Map<String, Object> exactCoverageArraySchema(
            int expectedSize,
            Map<String, Object> itemSchema) {
        return Map.of(
                "type", "array",
                "minItems", expectedSize,
                "maxItems", expectedSize,
                "items", itemSchema);
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
        requirePrimaryTranslationWhenRendered(
                context,
                array(explanation, "textEvidenceRefs"),
                array(explanation, "relevantTranslations"));

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

    private static boolean requiresPrimaryTranslation(ExplanationContext context) {
        // Only the table renderer has a mandatory translation row. The other
        // layouts can use a translation when the provider supplies one, but
        // must never discard an otherwise evidence-valid explanation merely
        // because that optional teaching aid is absent.
        return context.explanationStrategy().code()
                == ObjectiveExplanationStrategyRegistry.Code
                        .QUESTION_EVIDENCE_TRANSLATION_TABLE;
    }

    private static void requirePrimaryTranslationWhenRendered(
            ExplanationContext context,
            JsonNode textEvidence,
            JsonNode translations) {
        if (!requiresPrimaryTranslation(context)) {
            return;
        }
        if (textEvidence == null || !textEvidence.isArray()
                || textEvidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "translation-table explanation requires text evidence");
        }
        String primaryEvidenceId = text(textEvidence.get(0), "evidenceId");
        for (JsonNode translation : translations) {
            if (primaryEvidenceId.equals(text(translation, "evidenceId"))
                    && !text(translation, "translationVi").isBlank()) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "translation-table explanation requires the primary evidence translation");
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
            requireUsefulExplanationText(node, "reasonVi");
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
            requireUsefulExplanationText(node, "contextExplanationVi");
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
            requireUsefulExplanationText(node, "reasonVi");
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
        requireUsefulExplanationText(claim, "textVi");
        requireEvidenceReferences(
                stringList(claim, "evidenceIds"), evidenceIds);
    }

    /**
     * A typed JSON object can still be pedagogically empty. Reject the
     * boilerplate previously emitted by the recovery path so it is replaced
     * with a source- and option-specific draft instead of being published.
     */
    private static void requireUsefulExplanationText(JsonNode node, String field) {
        String value = text(node, field);
        String normalized = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ");
        List<String> boilerplate = List.of(
                "đáp án chính thức và được đối chiếu",
                "không khớp đáp án chính thức khi đối chiếu",
                "được đối chiếu trực tiếp với vùng nguồn",
                "vùng nguồn này là ngữ cảnh trực tiếp",
                "đáp án chính thức được kiểm tra theo bằng chứng nguồn",
                "ghép nối được đối chiếu với bằng chứng nguồn",
                "đáp án ô trống được đối chiếu với vùng nguồn",
                "mệnh đề được đối chiếu với vùng nguồn");
        if (boilerplate.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException("explanation text is generic boilerplate");
        }
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
