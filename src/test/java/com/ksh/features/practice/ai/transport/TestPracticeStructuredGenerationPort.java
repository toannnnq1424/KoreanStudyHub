package com.ksh.features.practice.ai.transport;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Test-only Practice port fixture.
 *
 * <p>This fixture deliberately starts at the domain port boundary. It must
 * never reconstruct the retired client-local OpenAI-compatible transports.
 * Wire-envelope behavior belongs to the OpenAI-primary adapter and strict
 * decoder tests.</p>
 */
public final class TestPracticeStructuredGenerationPort
        implements PracticeStructuredGenerationPort {

    private final ProviderIdentity identity;
    private final Function<
            PracticeStructuredGenerationRequest,
            PracticeStructuredGenerationResponse> responder;
    private final AtomicInteger calls = new AtomicInteger();
    private PracticeStructuredGenerationRequest lastRequest;

    private TestPracticeStructuredGenerationPort(
            ProviderIdentity identity,
            Function<
                    PracticeStructuredGenerationRequest,
                    PracticeStructuredGenerationResponse> responder) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.responder = Objects.requireNonNull(responder, "responder");
    }

    public static TestPracticeStructuredGenerationPort available(
            String provider,
            String model,
            JsonNode output) {
        JsonNode immutableOutput =
                Objects.requireNonNull(output, "output").deepCopy();
        return available(
                provider,
                model,
                request -> new PracticeStructuredGenerationResponse(
                        immutableOutput,
                        provider,
                        model,
                        "stop",
                        "test-request"));
    }

    public static TestPracticeStructuredGenerationPort available(
            String provider,
            String model,
            Function<
                    PracticeStructuredGenerationRequest,
                    PracticeStructuredGenerationResponse> responder) {
        return new TestPracticeStructuredGenerationPort(
                identity(provider, model, true),
                responder);
    }

    public static TestPracticeStructuredGenerationPort throwing(
            String provider,
            String model,
            RuntimeException failure) {
        return available(
                provider,
                model,
                request -> {
                    throw failure;
                });
    }

    public static TestPracticeStructuredGenerationPort unavailable(
            String provider,
            String model) {
        return new TestPracticeStructuredGenerationPort(
                identity(provider, model, false),
                request -> {
                    throw new AssertionError(
                            "Unavailable provider must not be called.");
                });
    }

    @Override
    public ProviderIdentity identity(PracticeAiCapability capability) {
        if (capability != PracticeAiCapability.ASSESSMENT_TEXT_VISION) {
            return new ProviderIdentity(
                    identity.provider(),
                    "",
                    DisabledPracticeStructuredGenerationAdapter.profileFor(
                            capability),
                    false);
        }
        return identity;
    }

    @Override
    public PracticeStructuredGenerationResponse generate(
            PracticeStructuredGenerationRequest request) {
        calls.incrementAndGet();
        lastRequest = Objects.requireNonNull(request, "request");
        return responder.apply(request);
    }

    public int calls() {
        return calls.get();
    }

    public PracticeStructuredGenerationRequest lastRequest() {
        return lastRequest;
    }

    private static ProviderIdentity identity(
            String provider,
            String model,
            boolean available) {
        return new ProviderIdentity(
                provider,
                model,
                PracticeModelCapabilityProfile.openAiAssessmentV1(),
                available);
    }
}
