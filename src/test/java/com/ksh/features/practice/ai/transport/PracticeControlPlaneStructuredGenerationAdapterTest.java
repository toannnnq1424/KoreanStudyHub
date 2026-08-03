package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilitySet;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionAuditService;
import com.ksh.features.practice.ai.controlplane.PracticeAiExecutionSnapshot;
import com.ksh.features.practice.ai.controlplane.PracticeAiLimits;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderTransport;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiResolvedBinding;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PracticeControlPlaneStructuredGenerationAdapterTest {

    @Test
    void identityPreservesFirstZeroBasedBindingRevision() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING, true, 0, 0L);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        when(resolver.availableSnapshot(PracticeAiPurpose.PRACTICE_PDF_AUTHORING))
                .thenReturn(Optional.of(binding.snapshot()));
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver,
                mock(PracticeAiExecutionAuditService.class),
                mock(PracticeAiProviderTransport.class));

        PracticeStructuredGenerationPort.ProviderIdentity identity =
                adapter.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING);

        assertThat(identity.available()).isTrue();
        assertThat(identity.bindingRevision()).isZero();
        assertThat(identity.providerProfileRevision()).isEqualTo(3L);
        assertThat(identity.providerProfileCode()).isEqualTo("PRACTICE_PRIMARY");
    }

    @Test
    void usesOneExactBindingSnapshotAndAuditsBeforeFakeTransport() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, true);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport transport = (resolved, path, contentType, accept, body, headers) -> {
            calls.incrementAndGet();
            assertThat(resolved).isSameAs(binding);
            assertThat(path).isEqualTo("/chat/completions");
            assertThat(body.toString()).contains("PRACTICE_WRITING_EVALUATION");
            return new PracticeAiProviderTransport.ProviderResponse(
                    200,
                    envelope("{\"ok\":true}"),
                    "application/json",
                    "fake-request");
        };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(binding);
        when(resolver.availableSnapshot(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(Optional.of(binding.snapshot()));
        doNothing().when(resolver).assertCurrent(binding.snapshot());
        when(audits.start(
                binding.snapshot(),
                "writing-fixture",
                "schema-v1|prompt-v1|TASK|rubric-v1|question=1|fixture_response",
                "LEARNER_WRITING_RESPONSE")).thenReturn(41L);
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver, audits, transport);

        PracticeStructuredGenerationResponse response = adapter.generate(request(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, List.of()));

        assertThat(response.output().path("ok").asBoolean()).isTrue();
        assertThat(response.provider()).isEqualTo("PRACTICE_PRIMARY");
        assertThat(calls).hasValue(1);
        verify(resolver).assertCurrent(binding.snapshot());
        verify(audits).success(41L);
    }

    @Test
    void changedBindingFailsClosedBeforeTransportAndMarksAudit() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_RL_EXPLANATION, true);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        PracticeAiProviderTransport transport = mock(PracticeAiProviderTransport.class);
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_RL_EXPLANATION))
                .thenReturn(binding);
        when(audits.start(
                binding.snapshot(),
                "writing-fixture",
                "schema-v1|prompt-v1|TASK|rubric-v1|question=1|fixture_response",
                "PUBLISHED_QUESTION_EVIDENCE")).thenReturn(42L);
        doThrow(new PracticeAiControlPlaneException(
                "PROVIDER_BINDING_CHANGED", false))
                .when(resolver).assertCurrent(binding.snapshot());
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver, audits, transport);

        assertThatThrownBy(() -> adapter.generate(request(
                PracticeAiPurpose.PRACTICE_RL_EXPLANATION, List.of())))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(error -> ((PracticeAiContractException) error).category())
                .isEqualTo("PROVIDER_BINDING_CHANGED");
        verify(transport, never()).exchange(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
        verify(audits).failure(42L, "PROVIDER_BINDING_CHANGED");
    }

    @Test
    void imageRequestFailsBeforeTransportWhenBindingDidNotDeclareImageInput() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING, false);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        PracticeAiProviderTransport transport = mock(PracticeAiProviderTransport.class);
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_PDF_AUTHORING))
                .thenReturn(binding);
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver, audits, transport);
        var image = new PracticeStructuredGenerationRequest.ImageEvidence(
                "PDF_PAGE", "a".repeat(64), "data:image/png;base64,AA==", "low");

        assertThatThrownBy(() -> adapter.generate(request(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING, List.of(image))))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(error -> ((PracticeAiContractException) error).category())
                .isEqualTo("IMAGE_INPUT_UNSUPPORTED");
        verify(transport, never()).exchange(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void retryRepeatsOnlyTheResolvedBindingAndNeverResolvesFallback() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, true, 2);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport transport =
                (resolved, path, contentType, accept, body, headers) -> {
                    assertThat(resolved).isSameAs(binding);
                    int call = calls.incrementAndGet();
                    return call < 3
                            ? new PracticeAiProviderTransport.ProviderResponse(
                                    503, new byte[0], "application/json", "fake-retry")
                            : new PracticeAiProviderTransport.ProviderResponse(
                                    200, envelope("{\"ok\":true}"),
                                    "application/json", "fake-success");
                };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(binding);
        when(audits.start(
                binding.snapshot(),
                "writing-fixture",
                "schema-v1|prompt-v1|TASK|rubric-v1|question=1|fixture_response",
                "LEARNER_WRITING_RESPONSE")).thenReturn(43L);
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver, audits, transport);

        assertThat(adapter.generate(request(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, List.of()))
                .output().path("ok").asBoolean()).isTrue();
        assertThat(calls).hasValue(3);
        verify(resolver, times(1)).resolve(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION);
        verify(audits).success(43L);
    }

    @Test
    void refusalGetsOneFullReplacementWithinSharedBudgetAndSeparateAudit() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, true, 1);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
        PracticeAiProviderTransport transport =
                (resolved, path, contentType, accept, body, headers) -> {
                    bodies.add(body.toString());
                    idempotencyKeys.add(headers.get("Idempotency-Key"));
                    return calls.incrementAndGet() == 1
                            ? new PracticeAiProviderTransport.ProviderResponse(
                                    200, refusalEnvelope(),
                                    "application/json", "fake-refusal")
                            : new PracticeAiProviderTransport.ProviderResponse(
                                    200, envelope("{\"ok\":true}"),
                                    "application/json", "fake-replacement");
                };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(binding);
        when(audits.start(
                binding.snapshot(),
                "writing-fixture",
                "schema-v1|prompt-v1|TASK|rubric-v1|question=1|fixture_response",
                "LEARNER_WRITING_RESPONSE")).thenReturn(44L);
        when(audits.start(
                binding.snapshot(),
                "writing-fixture_FULL_REPLACEMENT",
                "schema-v1|prompt-v1|TASK|rubric-v1|question=1|fixture_response"
                        + "|replacement=PROVIDER_REFUSAL",
                "LEARNER_WRITING_RESPONSE")).thenReturn(45L);
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver, audits, transport);

        assertThat(adapter.generate(request(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, List.of()))
                .output().path("ok").asBoolean()).isTrue();

        assertThat(calls).hasValue(2);
        assertThat(idempotencyKeys).hasSize(2);
        assertThat(idempotencyKeys.get(0)).isEqualTo("fixture-idempotency");
        assertThat(idempotencyKeys.get(1))
                .startsWith("ksh-practice-full-replacement-")
                .isNotEqualTo(idempotencyKeys.get(0));
        assertThat(bodies.get(1))
                .contains("PROVIDER_REFUSAL", "complete replacement")
                .doesNotContain("policy refusal fixture");
        verify(resolver, times(2)).assertCurrent(binding.snapshot());
        verify(audits).success(45L);
        verify(audits).success(44L);
    }

    @Test
    void replacementNeverExceedsBudgetAndNeverRepairsSecondMalformedOutput() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, true, 1);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport transport =
                (resolved, path, contentType, accept, body, headers) -> {
                    calls.incrementAndGet();
                    return new PracticeAiProviderTransport.ProviderResponse(
                            200,
                            envelope("{malformed"),
                            "application/json",
                            "fake-malformed");
                };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(binding);
        when(audits.start(any(), any(), any(), any())).thenReturn(46L, 47L);
        PracticeControlPlaneStructuredGenerationAdapter adapter = adapter(
                resolver, audits, transport);

        assertThatThrownBy(() -> adapter.generate(request(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, List.of())))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(error -> ((PracticeAiContractException) error).category())
                .isEqualTo("PROVIDER_MALFORMED_STRUCTURED_OUTPUT");

        assertThat(calls).hasValue(2);
        verify(audits).failure(47L, "PROVIDER_MALFORMED_STRUCTURED_OUTPUT");
        verify(audits).failure(46L, "PROVIDER_MALFORMED_STRUCTURED_OUTPUT");
    }

    @Test
    void zeroRetryBudgetDoesNotCreateReplacementCall() {
        PracticeAiResolvedBinding zeroBudget = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, true, 0);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport transport =
                (resolved, path, contentType, accept, body, headers) -> {
                    calls.incrementAndGet();
                    return new PracticeAiProviderTransport.ProviderResponse(
                            200, refusalEnvelope(),
                            "application/json", "fake-refusal");
                };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(zeroBudget);
        when(audits.start(any(), any(), any(), any())).thenReturn(48L);

        assertThatThrownBy(() -> adapter(resolver, audits, transport).generate(
                request(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, List.of())))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(error -> ((PracticeAiContractException) error).category())
                .isEqualTo("PROVIDER_REFUSAL");
        assertThat(calls).hasValue(1);
        verify(audits, times(1)).start(any(), any(), any(), any());
    }

    @Test
    void consumedHttpRetryBudgetDoesNotCreateReplacementCall() {
        PracticeAiResolvedBinding binding = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, true, 1);
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiExecutionAuditService audits = mock(PracticeAiExecutionAuditService.class);
        AtomicInteger calls = new AtomicInteger();
        PracticeAiProviderTransport transport =
                (resolved, path, contentType, accept, body, headers) -> {
                    if (calls.incrementAndGet() == 1) {
                        return new PracticeAiProviderTransport.ProviderResponse(
                                503, new byte[0], "application/json", "fake-503");
                    }
                    return new PracticeAiProviderTransport.ProviderResponse(
                            200,
                            envelope("{malformed"),
                            "application/json",
                            "fake-malformed");
                };
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .thenReturn(binding);
        when(audits.start(any(), any(), any(), any())).thenReturn(49L);

        assertThatThrownBy(() -> adapter(resolver, audits, transport).generate(
                request(PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, List.of())))
                .isInstanceOf(PracticeAiContractException.class)
                .extracting(error -> ((PracticeAiContractException) error).category())
                .isEqualTo("PROVIDER_MALFORMED_STRUCTURED_OUTPUT");
        assertThat(calls).hasValue(2);
        verify(audits, times(1)).start(any(), any(), any(), any());
        verify(audits).failure(49L, "PROVIDER_MALFORMED_STRUCTURED_OUTPUT");
    }

    private static PracticeControlPlaneStructuredGenerationAdapter adapter(
            PracticeAiBindingResolver resolver,
            PracticeAiExecutionAuditService audits,
            PracticeAiProviderTransport transport) {
        ObjectMapper mapper = new ObjectMapper();
        return new PracticeControlPlaneStructuredGenerationAdapter(
                resolver,
                audits,
                transport,
                new CanonicalPracticeJson(mapper),
                new StrictOpenAiStructuredResponseDecoder());
    }

    private static PracticeStructuredGenerationRequest request(
            PracticeAiPurpose purpose,
            List<PracticeStructuredGenerationRequest.ImageEvidence> images) {
        return new PracticeStructuredGenerationRequest(
                purpose,
                "writing-fixture",
                PracticeAiCapability.STRICT_STRUCTURED_TEXT_VISION,
                new PracticeAiAuthoritySnapshot(
                        "schema-v1",
                        "prompt-v1",
                        "TASK",
                        "rubric-v1",
                        "question=1"),
                PracticeModelCapabilityProfile.openAiAssessmentV1(),
                "Return JSON.",
                "",
                Map.of("text", "fixture"),
                "fixture_response",
                Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("ok"),
                        "properties", Map.of("ok", Map.of("type", "boolean"))),
                images,
                64,
                "fixture-idempotency");
    }

    private static PracticeAiResolvedBinding binding(
            PracticeAiPurpose purpose,
            boolean imageInput) {
        return binding(purpose, imageInput, 0);
    }

    private static PracticeAiResolvedBinding binding(
            PracticeAiPurpose purpose,
            boolean imageInput,
            int maxRetries) {
        return binding(purpose, imageInput, maxRetries, 5L);
    }

    private static PracticeAiResolvedBinding binding(
            PracticeAiPurpose purpose,
            boolean imageInput,
            int maxRetries,
            long bindingRevision) {
        return new PracticeAiResolvedBinding(
                new PracticeAiExecutionSnapshot(
                        purpose,
                        bindingRevision,
                        3,
                        "OPENAI_COMPATIBLE",
                        "PRACTICE_PRIMARY",
                        "purpose-model",
                        "OPENAI_COMPATIBLE_V1",
                        new PracticeAiCapabilitySet(
                                true,
                                imageInput,
                                purpose == PracticeAiPurpose.PRACTICE_SPEAKING_EVALUATION,
                                false,
                                false),
                        new PracticeAiLimits(
                                5_000, 60_000, maxRetries,
                                1_048_576, 1_048_576),
                        "a".repeat(64),
                        "b".repeat(64),
                        "PURPOSE_RETENTION_V1"),
                URI.create("https://provider.invalid/v1"),
                "TOP_SECRET");
    }

    private static byte[] envelope(String outputJson) {
        return ("{\"id\":\"fake-request\",\"choices\":[{\"finish_reason\":\"stop\","
                + "\"message\":{\"content\":"
                + quote(outputJson)
                + "}}]}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] refusalEnvelope() {
        return ("{\"id\":\"fake-refusal\",\"choices\":[{"
                + "\"finish_reason\":\"stop\",\"message\":{"
                + "\"refusal\":\"policy refusal fixture\","
                + "\"content\":\"{}\"}}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
