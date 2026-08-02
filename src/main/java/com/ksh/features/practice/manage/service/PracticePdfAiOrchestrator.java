package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAiRequestAudit;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.transport.PracticeAiAuthoritySnapshot;
import com.ksh.features.practice.ai.transport.PracticeAiCapability;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.PracticeModelCapabilityProfile;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationPort;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationResponse;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.*;

@Service
public class PracticePdfAiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfAiOrchestrator.class);

    private final ObjectMapper objectMapper;
    private final PracticeAiRequestAuditRepository auditRepository;
    private final PracticeStructuredGenerationPort structuredGeneration;

    public PracticePdfAiOrchestrator(
            ObjectMapper objectMapper,
            PracticeAiRequestAuditRepository auditRepository,
            PracticeStructuredGenerationPort structuredGeneration) {
        this.objectMapper = objectMapper;
        this.auditRepository = auditRepository;
        this.structuredGeneration = structuredGeneration;
    }

    public String callAi(PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo, Long sessionId, String strategy) {
        log.info("[PdfAiOrchestrator] Preparing purpose-bound request for sessionId={}", sessionId);
        PracticeStructuredGenerationPort.ProviderIdentity identity =
                structuredGeneration.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING);

        PracticeAiRequestAudit audit = new PracticeAiRequestAudit();
        audit.setSessionId(sessionId);
        audit.setPromptVersion("practice-import-v3");
        audit.setModel(identity.model());
        audit.setStrategy(strategy);

        int sentText = payloadInfo.statsSummary().containsKey("finalSentTextCharacters")
                ? Integer.parseInt(payloadInfo.statsSummary().get("finalSentTextCharacters").toString()) : 0;
        long sentImageBytes = payloadInfo.statsSummary().containsKey("estimatedImageBytes")
                ? Long.parseLong(payloadInfo.statsSummary().get("estimatedImageBytes").toString()) : 0L;

        audit.setSentTextChars(sentText);
        audit.setSentImageCount(payloadInfo.crops().size());
        audit.setSentImageBytes(sentImageBytes);
        audit.setCreatedAt(LocalDateTime.now());

        try {
            // Serialize summary config
            Map<String, Object> summaryConfig = new LinkedHashMap<>();
            summaryConfig.put("strategy", strategy);
            summaryConfig.put("cropsCount", payloadInfo.crops().size());
            summaryConfig.put("purpose", PracticeAiPurpose.PRACTICE_PDF_AUTHORING.name());
            summaryConfig.put("providerProfile", identity.providerProfileCode());
            summaryConfig.put("bindingRevision", identity.bindingRevision());
            if (payloadInfo.requestDto().getRequestMeta() != null) {
                summaryConfig.put("requestId", payloadInfo.requestDto().getRequestMeta().getRequestId());
                summaryConfig.put("schemaVersion", payloadInfo.requestDto().getRequestMeta().getSchemaVersion());
            }
            audit.setPayloadSummaryJson(objectMapper.writeValueAsString(summaryConfig));
        } catch (Exception e) {
            log.warn("[PdfAiOrchestrator] Failed to serialize summary info", e);
        }

        try {
            PracticeStructuredGenerationResponse response = structuredGeneration.generate(
                    request(payloadInfo, sessionId, strategy));
            audit.setStatus("SUCCESS");
            auditRepository.save(audit);
            return objectMapper.writeValueAsString(response.output());
        } catch (Exception e) {
            audit.setStatus("FAILED");
            audit.setErrorCode(e instanceof PracticeAiContractException contract
                    ? contract.category()
                    : "AI_PROVIDER_CALL_FAILED");
            auditRepository.save(audit);
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Không thể giải mã phản hồi AI.", e);
        }
    }

    private PracticeStructuredGenerationRequest request(
            PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo,
            Long sessionId,
            String strategy) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request", payloadInfo.requestDto());
        input.put("basePageRangeText",
                payloadInfo.basePageRangeText() == null ? "" : payloadInfo.basePageRangeText());
        input.put("regions", payloadInfo.crops().stream().map(crop -> Map.of(
                "regionId", crop.regionId(),
                "pageNumber", crop.pageNumber(),
                "regionType", crop.regionType(),
                "assetRef", crop.assetRef(),
                "placement", crop.placement())).toList());
        List<PracticeStructuredGenerationRequest.ImageEvidence> images =
                payloadInfo.crops().stream()
                        .map(crop -> new PracticeStructuredGenerationRequest.ImageEvidence(
                                "PDF_REGION_" + crop.regionId(),
                                sha256(crop.base64DataUrl()),
                                crop.base64DataUrl(),
                                "high"))
                        .toList();
        return new PracticeStructuredGenerationRequest(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING,
                "pdf-authoring-legacy-workspace",
                PracticeAiCapability.STRICT_STRUCTURED_TEXT_VISION,
                new PracticeAiAuthoritySnapshot(
                        "pdf_import_response",
                        "practice-import-v3",
                        strategy,
                        "legacy-workspace-v1",
                        "session=" + sessionId),
                PracticeModelCapabilityProfile.openAiAssessmentV1(),
                systemPrompt(),
                "Return only the strict authoring JSON object.",
                input,
                "pdf_import_response",
                schema(),
                images,
                4096,
                "pdf-session-" + sessionId);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private String systemPrompt() {
        return PracticePdfAiPromptRules.systemPrompt();
    }

    private Map<String, Object> schema() {
        Map<String, Object> asset = objectSchema(
                List.of("assetRef", "sourceRegionId", "placement", "caption"),
                prop("assetRef", typed("string"),
                        "sourceRegionId", typed("string"),
                        "placement", typed("string"),
                        "caption", typed("string"))
        );

        Map<String, Object> question = objectSchema(
                List.of("questionNo", "questionType", "prompt", "options", "answerKey", "explanationVi", "points", "confidence", "reviewRequired", "sourceRegionIds", "assets"),
                prop("questionNo", typed("integer"),
                        "questionType", Map.of(
                                "type", "string",
                                "enum", List.of("SINGLE_CHOICE", "TRUE_FALSE_NOT_GIVEN", "FILL_BLANK", "ESSAY", "SPEAKING")),
                        "prompt", typed("string"),
                        "options", arrayOf(typed("string")),
                        "answerKey", typed("string"),
                        "explanationVi", typed("string"),
                        "points", typed("number"),
                        "confidence", typed("number"),
                        "reviewRequired", typed("boolean"),
                        "sourceRegionIds", arrayOf(typed("string")),
                        "assets", arrayOf(asset))
        );

        Map<String, Object> group = objectSchema(
                List.of("clientId", "label", "displayOrder", "questionFrom", "questionTo", "instruction", "passage", "transcript", "audioRef", "sourceRegionIds", "assets", "questions"),
                prop("clientId", typed("string"),
                        "label", typed("string"),
                        "displayOrder", typed("integer"),
                        "questionFrom", nullableTyped("integer"),
                        "questionTo", nullableTyped("integer"),
                        "instruction", typed("string"),
                        "passage", typed("string"),
                        "transcript", typed("string"),
                        "audioRef", nullableTyped("string"),
                        "sourceRegionIds", arrayOf(typed("string")),
                        "assets", arrayOf(asset),
                        "questions", arrayOf(question))
        );

        Map<String, Object> section = objectSchema(
                List.of("clientId", "label", "skill", "displayOrder", "durationMinutes", "sourceRegionIds", "groups"),
                prop("clientId", typed("string"),
                        "label", typed("string"),
                        "skill", typed("string"),
                        "displayOrder", typed("integer"),
                        "durationMinutes", nullableTyped("integer"),
                        "sourceRegionIds", arrayOf(typed("string")),
                        "groups", arrayOf(group))
        );

        Map<String, Object> warning = objectSchema(
                List.of("code", "severity", "regionIds", "messageVi"),
                prop("code", typed("string"),
                        "severity", typed("string"),
                        "regionIds", arrayOf(typed("string")),
                        "messageVi", typed("string"))
        );

        return objectSchema(
                List.of("documentTitle", "sections", "assets", "warnings"),
                prop("documentTitle", typed("string"),
                        "sections", arrayOf(section),
                        "assets", arrayOf(asset),
                        "warnings", arrayOf(warning))
        );
    }

    private static Map<String, Object> typed(String type) {
        return Map.of("type", type);
    }

    private static Map<String, Object> nullableTyped(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static Map<String, Object> arrayOf(Map<String, Object> itemSchema) {
        return Map.of("type", "array", "items", itemSchema);
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object> propertiesMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "object");
        node.put("additionalProperties", Boolean.FALSE);
        node.put("required", required);
        node.put("properties", propertiesMap);
        return node;
    }

    private static Map<String, Object> prop(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
