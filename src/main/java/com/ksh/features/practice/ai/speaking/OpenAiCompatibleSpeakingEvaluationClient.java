package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.features.practice.ai.transport.PracticeAiAuthoritySnapshot;
import com.ksh.features.practice.ai.transport.PracticeAiCapability;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.PracticeModelCapabilityProfile;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationPort;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiCompatibleSpeakingEvaluationClient implements SpeakingEvaluationClient {
    private final SpeakingEvaluatorProperties properties;
    private final SpeakingEvaluationPromptBuilder promptBuilder;
    private final PracticeStructuredGenerationPort structuredGeneration;

    @Autowired
    public OpenAiCompatibleSpeakingEvaluationClient(
            SpeakingEvaluatorProperties properties,
            SpeakingEvaluationPromptBuilder promptBuilder,
            PracticeStructuredGenerationPort structuredGeneration
    ) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.structuredGeneration = structuredGeneration;
    }

    @Override
    public SpeakingEvaluationProviderResult evaluate(SpeakingEvaluationRequest request) {
        long startNanos = System.nanoTime();
        if (request == null || !request.transcriptLanguageEvaluatorContract()) {
            return failure(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                    "UNSUPPORTED_EVALUATOR_CAPABILITY", false, startNanos);
        }
        if (!currentPolicyConfiguration()) {
            return failure(SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                    "STALE_EVALUATOR_POLICY_CONFIGURATION", false, startNanos);
        }
        if (!providerAvailable()) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE, "MISSING_API_KEY", false, startNanos);
        }
        try {
            return parseStructured(request, startNanos);
        } catch (PracticeAiContractException ex) {
            return failure(
                    SpeakingEvaluationStatus.EVALUATION_CONTRACT_FAILED,
                    ex.category(),
                    ex.retryable(),
                    startNanos);
        } catch (HttpStatusCodeException ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                    "PROVIDER_HTTP_ERROR", isRetryable(ex.getStatusCode()), startNanos);
        } catch (ResourceAccessException ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                    "PROVIDER_TRANSPORT_ERROR", true, startNanos);
        } catch (RuntimeException ex) {
            return failure(SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                    "PROVIDER_TRANSPORT_ERROR", true, startNanos);
        }
    }

    private boolean currentPolicyConfiguration() {
        return SpeakingPromptRules.PROMPT_VERSION.equals(
                properties.promptVersion())
                && SpeakingPromptRules.RUBRIC_VERSION.equals(
                properties.rubricVersion())
                && SpeakingPromptRules.SCHEMA_VERSION.equals(
                properties.schemaVersion());
    }

    private SpeakingEvaluationProviderResult failure(
            SpeakingEvaluationStatus status,
            String errorCategory,
            boolean retryable,
            long startNanos
    ) {
        return SpeakingEvaluationProviderResult.failure(
                status,
                providerName(),
                evaluatorModel(),
                errorCategory,
                retryable,
                elapsedMillis(startNanos));
    }

    private SpeakingEvaluationProviderResult parseStructured(
            SpeakingEvaluationRequest request,
            long startNanos) {
        Map<String, Object> responseFormat =
                promptBuilder.responseFormat(request);
        Map<String, Object> jsonSchema = nestedMap(
                responseFormat,
                "json_schema");
        Map<String, Object> schema = nestedMap(jsonSchema, "schema");
        List<PracticeStructuredGenerationRequest.ImageEvidence> images =
                request.imageEvidence() == null
                        ? List.of()
                        : List.of(new PracticeStructuredGenerationRequest.ImageEvidence(
                                "QUESTION_IMAGE",
                                request.imageEvidence().sha256(),
                                request.imageEvidence().dataUrl(),
                                "high"));
        String authorityIdentity = String.join(
                "|",
                "question=" + request.questionId(),
                "questionVersion=" + request.questionVersionId(),
                "context=" + request.promptContextContractIdentity(),
                "policy=" + request.policyBundleId());
        PracticeStructuredGenerationRequest structuredRequest =
                new PracticeStructuredGenerationRequest(
                        "speaking-transcript-evaluation",
                        PracticeAiCapability.ASSESSMENT_TEXT_VISION,
                        new PracticeAiAuthoritySnapshot(
                                request.schemaVersion(),
                                request.promptVersion(),
                                "TRANSCRIPT_ONLY",
                                request.evidenceContractVersion(),
                                authorityIdentity),
                        PracticeModelCapabilityProfile.openAiAssessmentV1(),
                        promptBuilder.systemPrompt(request),
                        "",
                        promptBuilder.userPayloadObject(request),
                        String.valueOf(jsonSchema.get("name")),
                        schema,
                        images,
                        4096,
                        "");
        JsonNode output = structuredGeneration.generate(
                structuredRequest).output();
        return SpeakingEvaluationProviderResult.success(
                output,
                providerName(),
                evaluatorModel(),
                elapsedMillis(startNanos));
    }

    private boolean providerAvailable() {
        return structuredGeneration.identity(
                PracticeAiCapability.ASSESSMENT_TEXT_VISION).available();
    }

    private String evaluatorModel() {
        return structuredGeneration.identity(
                PracticeAiCapability.ASSESSMENT_TEXT_VISION).model();
    }

    private String providerName() {
        return structuredGeneration.identity(
                PracticeAiCapability.ASSESSMENT_TEXT_VISION).provider();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(
            Map<String, Object> source,
            String key) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new PracticeAiContractException(
                    "INVALID_INTERNAL_RESPONSE_SCHEMA",
                    false);
        }
        return (Map<String, Object>) map;
    }

    private static boolean isRetryable(HttpStatusCode status) {
        int value = status.value();
        return value == 429 || value == 500 || value == 502 || value == 503 || value == 504;
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

}
