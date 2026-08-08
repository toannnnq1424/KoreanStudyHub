package com.ksh.features.practice.ai.transport;

import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionSnapshot;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PracticeControlPlaneStructuredGenerationAdapter
        implements PracticeStructuredGenerationPort {

    private static final Set<String> FULL_REPLACEMENT_CATEGORIES = Set.of(
            "PROVIDER_REFUSAL",
            "PROVIDER_TRUNCATED_RESPONSE",
            "PROVIDER_MALFORMED_STRUCTURED_OUTPUT",
            "PROVIDER_NON_OBJECT_STRUCTURED_OUTPUT",
            "PROVIDER_EMPTY_RESPONSE");

    private final PracticeAiBindingResolver resolver;
    private final PracticeAiExecutionAuditService auditService;
    private final PracticeAiProviderTransport transport;
    private final CanonicalPracticeJson canonicalJson;
    private final StrictOpenAiStructuredResponseDecoder decoder;

    public PracticeControlPlaneStructuredGenerationAdapter(
            PracticeAiBindingResolver resolver,
            PracticeAiExecutionAuditService auditService,
            PracticeAiProviderTransport transport,
            CanonicalPracticeJson canonicalJson,
            StrictOpenAiStructuredResponseDecoder decoder) {
        this.resolver = resolver;
        this.auditService = auditService;
        this.transport = transport;
        this.canonicalJson = canonicalJson;
        this.decoder = decoder;
    }

    @Override
    public ProviderIdentity identity(PracticeAiPurpose purpose) {
        return resolver.availableSnapshot(purpose)
                .map(snapshot -> new ProviderIdentity(
                        snapshot.providerFamily(),
                        snapshot.model(),
                        capabilityProfile(snapshot),
                        true,
                        snapshot.bindingRevision(),
                        snapshot.providerProfileRevision(),
                        snapshot.providerProfileCode()))
                .orElseGet(() -> new ProviderIdentity(
                        "UNBOUND",
                        "",
                        PracticeModelCapabilityProfile.openAiAssessmentV1(),
                        false,
                        -1L,
                        -1L,
                        "UNBOUND"));
    }

    @Override
    public PracticeStructuredGenerationResponse generate(
            PracticeStructuredGenerationRequest request) {
        PracticeAiResolvedBinding binding;
        try {
            binding = resolver.resolve(request.purpose());
            requireSupported(request, binding.snapshot());
        } catch (PracticeAiControlPlaneException exception) {
            throw contract(exception);
        }

        CanonicalPracticeJson.CanonicalPayload inputPayload =
                canonicalJson.serialize(structuredInput(request));
        String idempotencyKey = idempotencyKey(
                request.idempotencyKey(), inputPayload.sha256());
        CanonicalPracticeJson.CanonicalPayload requestBody = canonicalJson.serialize(
                wireBody(request, binding.snapshot().model(), inputPayload.json(), null));
        if (requestBody.json().getBytes(StandardCharsets.UTF_8).length
                > binding.snapshot().limits().maxRequestBytes()) {
            throw new PracticeAiContractException("PROVIDER_REQUEST_TOO_LARGE", false);
        }

        String contractIdentity = String.join("|",
                request.authority().schemaVersion(),
                request.authority().promptVersion(),
                request.authority().strategyCode(),
                request.authority().strategyVersion(),
                request.authority().authorityIdentity(),
                request.responseSchemaName());
        Long auditId = auditService.start(
                binding.snapshot(),
                request.operation(),
                contractIdentity,
                binding.snapshot().purpose().dataClass());
        try {
            resolver.assertCurrent(binding.snapshot());
            StrictOpenAiStructuredResponseDecoder.DecodedResponse decoded =
                    callAndDecodeWithBoundedReplacement(
                            binding, request, inputPayload.json(), requestBody.json(),
                            idempotencyKey, contractIdentity);
            auditService.success(auditId);
            return new PracticeStructuredGenerationResponse(
                    decoded.output(),
                    binding.snapshot().providerProfileCode(),
                    binding.snapshot().model(),
                    decoded.finishReason(),
                    decoded.providerRequestId());
        } catch (PracticeAiControlPlaneException exception) {
            auditService.failure(auditId, exception.errorCode());
            throw contract(exception);
        } catch (PracticeAiContractException exception) {
            auditService.failure(auditId, exception.category());
            throw exception;
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                auditService.cancelled(auditId, "EVALUATION_INTERRUPTED");
                throw new PracticeAiContractException(
                        "EVALUATION_INTERRUPTED", false, exception);
            }
            auditService.failure(auditId, "PROVIDER_TRANSPORT_ERROR");
            throw new PracticeAiContractException(
                    "PROVIDER_TRANSPORT_ERROR", true, exception);
        }
    }

    private StrictOpenAiStructuredResponseDecoder.DecodedResponse
    callAndDecodeWithBoundedReplacement(
            PracticeAiResolvedBinding binding,
            PracticeStructuredGenerationRequest request,
            String structuredInputJson,
            String initialRequestJson,
            String initialIdempotencyKey,
            String contractIdentity) {
        int totalBudget = binding.snapshot().limits().maxRetries() + 1;
        TransportCall initial = callWithRetry(
                binding, initialRequestJson, initialIdempotencyKey, totalBudget);
        try {
            return decode(binding, initial.body());
        } catch (PracticeAiContractException firstFailure) {
            int remainingBudget = totalBudget - initial.calls();
            if (remainingBudget < 1
                    || !FULL_REPLACEMENT_CATEGORIES.contains(
                            firstFailure.category())) {
                throw firstFailure;
            }

            resolver.assertCurrent(binding.snapshot());
            String replacementOperation = request.operation()
                    + "_FULL_REPLACEMENT";
            Long replacementAuditId = auditService.start(
                    binding.snapshot(),
                    replacementOperation,
                    contractIdentity + "|replacement=" + firstFailure.category(),
                    binding.snapshot().purpose().dataClass());
            try {
                CanonicalPracticeJson.CanonicalPayload replacementBody =
                        canonicalJson.serialize(wireBody(
                                request,
                                binding.snapshot().model(),
                                structuredInputJson,
                                firstFailure.category()));
                if (replacementBody.json().getBytes(StandardCharsets.UTF_8).length
                        > binding.snapshot().limits().maxRequestBytes()) {
                    throw new PracticeAiContractException(
                            "PROVIDER_REQUEST_TOO_LARGE", false);
                }
                String replacementIdempotencyKey = replacementIdempotencyKey(
                        initialIdempotencyKey, firstFailure.category());
                TransportCall replacement = callWithRetry(
                        binding,
                        replacementBody.json(),
                        replacementIdempotencyKey,
                        remainingBudget);
                StrictOpenAiStructuredResponseDecoder.DecodedResponse decoded =
                        decode(binding, replacement.body());
                auditService.success(replacementAuditId);
                return decoded;
            } catch (PracticeAiControlPlaneException exception) {
                auditService.failure(replacementAuditId, exception.errorCode());
                throw exception;
            } catch (PracticeAiContractException exception) {
                auditService.failure(replacementAuditId, exception.category());
                throw exception;
            } catch (RuntimeException exception) {
                auditService.failure(
                        replacementAuditId, "PROVIDER_TRANSPORT_ERROR");
                throw exception;
            }
        }
    }

    private StrictOpenAiStructuredResponseDecoder.DecodedResponse decode(
            PracticeAiResolvedBinding binding, byte[] rawResponse) {
        return decoder.decode(
                rawResponse,
                binding.snapshot().limits().maxResponseBytes());
    }

    private static void requireSupported(
            PracticeStructuredGenerationRequest request,
            PracticeAiExecutionSnapshot snapshot) {
        if (!request.purpose().structuredJson()
                || request.capability()
                    != PracticeAiCapability.STRICT_STRUCTURED_TEXT_VISION
                || !request.modelCapabilityProfile().nativeJsonSchema()
                || request.modelCapabilityProfile().toolCalls()
                || request.modelCapabilityProfile().plainJsonFallback()
                || request.modelCapabilityProfile().streaming()
                || !snapshot.capabilities().strictJsonSchema()) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_CAPABILITY_INCOMPATIBLE", false);
        }
        if (!request.images().isEmpty()
                && !snapshot.capabilities().imageInput()) {
            throw new PracticeAiControlPlaneException(
                    "IMAGE_INPUT_UNSUPPORTED", false);
        }
    }

    private TransportCall callWithRetry(
            PracticeAiResolvedBinding binding,
            String requestJson,
            String idempotencyKey,
            int maximumCalls) {
        PracticeAiProviderTransport.ProviderResponse last = null;
        int calls = 0;
        while (calls < maximumCalls) {
            if (Thread.currentThread().isInterrupted()) {
                throw new PracticeAiControlPlaneException(
                        "EVALUATION_INTERRUPTED", false);
            }
            PracticeAiProviderTransport.ProviderResponse response = transport.exchange(
                    binding,
                    "/chat/completions",
                    MediaType.APPLICATION_JSON,
                    MediaType.APPLICATION_JSON,
                    requestJson,
                    Map.of("Idempotency-Key", idempotencyKey));
            calls++;
            if (response.successful()) {
                return new TransportCall(response.body(), calls);
            }
            last = response;
            if (!retryable(response.status())
                    || calls >= maximumCalls) {
                break;
            }
        }
        int status = last == null ? 0 : last.status();
        throw new PracticeAiControlPlaneException(
                "PROVIDER_HTTP_ERROR", retryable(status));
    }

    private Map<String, Object> structuredInput(
            PracticeStructuredGenerationRequest request) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("profileVersion", request.modelCapabilityProfile().profileVersion());
        profile.put("nativeJsonSchema", request.modelCapabilityProfile().nativeJsonSchema());
        profile.put("toolCalls", request.modelCapabilityProfile().toolCalls());
        profile.put("plainJsonFallback", request.modelCapabilityProfile().plainJsonFallback());
        profile.put("imageInput", request.modelCapabilityProfile().imageInput());
        profile.put("streaming", request.modelCapabilityProfile().streaming());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("purpose", request.purpose().name());
        envelope.put("schemaVersion", request.authority().schemaVersion());
        envelope.put("promptVersion", request.authority().promptVersion());
        envelope.put("strategyCode", request.authority().strategyCode());
        envelope.put("strategyVersion", request.authority().strategyVersion());
        envelope.put("authorityIdentity", request.authority().authorityIdentity());
        envelope.put("modelCapabilityProfile", profile);
        envelope.put("operation", request.operation());
        envelope.put("input", request.input());
        return envelope;
    }

    private static Map<String, Object> wireBody(
            PracticeStructuredGenerationRequest request,
            String model,
            String structuredInputJson,
            String replacementReason) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", request.systemInstruction()));
        if (!request.developerInstruction().isBlank()) {
            messages.add(message("developer", request.developerInstruction()));
        }
        if (replacementReason != null) {
            messages.add(message("developer", String.join(" ",
                    "The previous response was unusable due to",
                    replacementReason + ".",
                    "Return one complete replacement that satisfies the supplied JSON schema.",
                    "Do not mention, quote, patch, or depend on the previous response.")));
        }
        messages.add(message("user", userContent(structuredInputJson, request.images())));

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", request.responseSchemaName());
        jsonSchema.put("strict", Boolean.TRUE);
        jsonSchema.put("schema", request.responseSchema());
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.0);
        body.put("top_p", 1.0);
        body.put("max_tokens", request.maxOutputTokens());
        body.put("messages", messages);
        body.put("response_format", responseFormat);
        return body;
    }

    private static List<Map<String, Object>> userContent(
            String structuredInputJson,
            List<PracticeStructuredGenerationRequest.ImageEvidence> images) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", structuredInputJson));
        for (PracticeStructuredGenerationRequest.ImageEvidence image : images) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", image.dataUrl(),
                            "detail", image.detail())));
        }
        return List.copyOf(content);
    }

    private static Map<String, Object> message(String role, Object content) {
        return Map.of("role", role, "content", content);
    }

    private static String idempotencyKey(String requested, String payloadSha256) {
        if (requested == null || requested.isBlank()) {
            return "ksh-practice-" + payloadSha256;
        }
        if (requested.length() > 128
                || !requested.matches("[A-Za-z0-9._:-]+")) {
            throw new PracticeAiContractException("INVALID_IDEMPOTENCY_KEY", false);
        }
        return requested;
    }

    private String replacementIdempotencyKey(String original, String reason) {
        String digest = canonicalJson.serialize(Map.of(
                "originalIdempotencyKey", original,
                "replacementReason", reason,
                "replacementAttempt", 1)).sha256();
        return "ksh-practice-full-replacement-" + digest;
    }

    private static PracticeModelCapabilityProfile capabilityProfile(
            PracticeAiExecutionSnapshot snapshot) {
        return new PracticeModelCapabilityProfile(
                "practice-purpose-binding-v1",
                snapshot.capabilities().strictJsonSchema(),
                false,
                false,
                snapshot.capabilities().imageInput(),
                false);
    }

    private static PracticeAiContractException contract(
            PracticeAiControlPlaneException exception) {
        return new PracticeAiContractException(
                exception.errorCode(), exception.retryable(), exception);
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 500 || status == 502
                || status == 503 || status == 504;
    }

    private record TransportCall(byte[] body, int calls) {
        private TransportCall {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
