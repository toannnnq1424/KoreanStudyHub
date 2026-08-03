package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persists and reads dark observations without creating a learner result. */
public final class DirectAudioDarkObservationService {

    public static final String RETENTION_POLICY_ID =
            "KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1";
    public static final Duration RETENTION_CEILING = Duration.ofDays(30);

    private final Store store;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DirectAudioDarkObservationService(
            Store store, ObjectMapper mapper, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public StoredObservation capture(
            Long attemptId,
            String observationKey,
            DirectAudioAcousticObservationResult result,
            Instant deleteAfter) {
        Instant capturedAt = clock.instant();
        requireIdentity(attemptId);
        requireToken(observationKey, "observationKey");
        if (deleteAfter == null || !deleteAfter.isAfter(capturedAt)
                || deleteAfter.isAfter(capturedAt.plus(RETENTION_CEILING))) {
            throw rejected("DIRECT_AUDIO_DARK_RETENTION_INVALID");
        }
        requireDarkResult(result);

        SafePayload payload = new SafePayload(
                result.completeness().toMap(),
                result.observations().stream()
                .map(observation -> new SafeDimension(
                        observation.dimension().name(),
                        observation.providerSignalValue(),
                        observation.confidence(),
                        observation.evidence().stream()
                                .map(span -> new SafeSpan(
                                        span.evidenceId(), span.startMs(), span.endMs(),
                                        span.confidence()))
                                .toList()))
                .toList());
        String payloadJson;
        try {
            payloadJson = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw rejected("DIRECT_AUDIO_DARK_PAYLOAD_SERIALIZATION_FAILED");
        }
        StoredObservation stored = new StoredObservation(
                observationKey, attemptId, result.contractVersion(), result.language(),
                result.evaluatorId(), result.model(), result.calibrationProfileId(),
                result.calibrationVersion(), sha256(result.providerRequestId()),
                sha256(result.providerCacheIdentity()),
                result.providerObservationTotal(), result.providerConfidence(),
                payloadJson, RETENTION_POLICY_ID, capturedAt, deleteAfter);
        return store.insert(stored);
    }

    public Optional<ReviewerView> inspect(
            Long reviewerId, Long attemptId) {
        if (reviewerId == null || reviewerId <= 0
                || attemptId == null || attemptId <= 0) {
            return Optional.empty();
        }
        return store.findInspectable(reviewerId, attemptId, clock.instant())
                .flatMap(stored -> validatedCompleteness(stored.payloadJson())
                .map(completeness -> new ReviewerView(
                        stored.observationKey(), stored.attemptId(),
                        stored.contractVersion(), stored.language(),
                        stored.evaluatorId(), stored.model(),
                        stored.calibrationProfileId(), stored.calibrationVersion(),
                        stored.providerObservationTotal(), stored.providerConfidence(),
                        stored.payloadJson(), stored.capturedAt(), stored.deleteAfter(),
                        completeness.status(), completeness.reasonCode(),
                        completeness.rejectedItemCount(),
                        false, null, null)));
    }

    private static void requireDarkResult(
            DirectAudioAcousticObservationResult result) {
        if (result == null
                || result.state() != DirectAudioAcousticObservationResult.State
                        .VALID_DARK_OBSERVATION
                || result.scoreReleaseEligible()
                || result.presenterEligible()
                || result.holisticScore() != null
                || result.attemptPoints() != null
                || result.completeness().status()
                    == PracticeAiResultCompleteness.Status.UNAVAILABLE
                || !DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION.equals(
                        result.contractVersion())
                || !DirectAudioAcousticResponseNormalizer.LANGUAGE.equals(result.language())
                || result.observations().size() != 2
                || blank(result.evaluatorId())
                || blank(result.model())
                || blank(result.calibrationProfileId())
                || blank(result.calibrationVersion())
                || blank(result.providerRequestId())
                || blank(result.providerCacheIdentity())
                || result.providerObservationTotal() == null
                || result.providerConfidence() == null) {
            throw rejected("DIRECT_AUDIO_DARK_OBSERVATION_INVALID");
        }
    }

    private Optional<PracticeAiResultCompleteness> validatedCompleteness(
            String payloadJson) {
        try {
            JsonNode root = mapper.readTree(payloadJson);
            if (root == null || !root.isObject() || root.size() != 2
                    || !root.path("observations").isArray()) {
                return Optional.empty();
            }
            PracticeAiResultCompleteness completeness =
                    PracticeAiResultCompleteness.require(root);
            if (completeness.status()
                    == PracticeAiResultCompleteness.Status.UNAVAILABLE) {
                return Optional.empty();
            }
            return Optional.of(completeness);
        } catch (RuntimeException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static void requireIdentity(Long value) {
        if (value == null || value <= 0) {
            throw rejected("DIRECT_AUDIO_DARK_ATTEMPT_INVALID");
        }
    }

    private static void requireToken(String value, String name) {
        if (blank(value) || value.length() > 80) {
            throw rejected("DIRECT_AUDIO_DARK_" + name.toUpperCase() + "_INVALID");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static IllegalStateException rejected(String code) {
        return new IllegalStateException(code);
    }

    public interface Store {
        StoredObservation insert(StoredObservation observation);

        Optional<StoredObservation> findInspectable(
                Long reviewerId, Long attemptId, Instant now);
    }

    public record StoredObservation(
            String observationKey,
            Long attemptId,
            String contractVersion,
            String language,
            String evaluatorId,
            String model,
            String calibrationProfileId,
            String calibrationVersion,
            String receiptFingerprint,
            String providerCacheFingerprint,
            BigDecimal providerObservationTotal,
            BigDecimal providerConfidence,
            String payloadJson,
            String retentionPolicyId,
            Instant capturedAt,
            Instant deleteAfter) {
    }

    public record ReviewerView(
            String observationKey,
            Long attemptId,
            String contractVersion,
            String language,
            String evaluatorId,
            String model,
            String calibrationProfileId,
            String calibrationVersion,
            BigDecimal providerObservationTotal,
            BigDecimal providerConfidence,
            String payloadJson,
            Instant capturedAt,
            Instant deleteAfter,
            PracticeAiResultCompleteness.Status completenessStatus,
            String completenessReasonCode,
            int rejectedItemCount,
            boolean scoreReleaseEligible,
            BigDecimal holisticScore,
            BigDecimal attemptPoints) {
    }

    private record SafePayload(
            @JsonProperty(PracticeAiResultCompleteness.FIELD)
            java.util.Map<String, Object> resultCompleteness,
            List<SafeDimension> observations) {
    }

    private record SafeDimension(
            String dimension,
            BigDecimal signalValue,
            BigDecimal confidence,
            List<SafeSpan> evidence) {
    }

    private record SafeSpan(
            String evidenceId,
            long startMs,
            long endMs,
            BigDecimal confidence) {
    }
}
