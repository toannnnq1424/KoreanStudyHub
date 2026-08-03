package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict current-contract parser producing dark, non-score-bearing observations. */
@Component
public final class DirectAudioAcousticResponseNormalizer {

    public static final String CONTRACT_VERSION =
            "ksh-speaking-direct-audio-acoustic-v1";
    public static final String LANGUAGE = "ko-KR";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "contract_version", "language", "evidence_mode", "evaluation_status",
            "evaluator", "policy", "calibration", "audio_consumption",
            "audio_duration_ms", "observations", "provider_observation_total",
            "provider_confidence", "score_release");
    private static final Set<String> EVALUATOR_FIELDS = Set.of(
            "evaluator_id", "model", "capability_id");
    private static final Set<String> POLICY_FIELDS = Set.of(
            "policy_bundle_id", "policy_bundle_fingerprint");
    private static final Set<String> CALIBRATION_FIELDS = Set.of(
            "profile_id", "version", "corpus_evidence_id",
            "acoustic_calibration_evidence_id", "fairness_evidence_id",
            "repeatability_evidence_id");
    private static final Set<String> CONSUMPTION_FIELDS = Set.of(
            "provider_request_id", "audio_consumed", "audio_digest",
            "provider_cache_identity", "provenance_digest");
    private static final Set<String> OBSERVATION_FIELDS = Set.of(
            "dimension", "signal_value", "confidence", "evidence");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "evidence_id", "start_ms", "end_ms", "confidence", "observation");
    private static final Set<String> RELEASE_FIELDS = Set.of("eligible", "reason");

    private final DirectAudioCalibrationProfileAuthority calibrationAuthority;

    public DirectAudioAcousticResponseNormalizer(
            DirectAudioCalibrationProfileAuthority calibrationAuthority) {
        this.calibrationAuthority = Objects.requireNonNull(calibrationAuthority);
    }

    public DirectAudioAcousticObservationResult normalize(
            JsonNode input,
            ExpectedContext expected) {
        try {
            exactObject(input, ROOT_FIELDS, "DIRECT_AUDIO_SCHEMA_ROOT_INVALID");
            exactText(input, "contract_version", CONTRACT_VERSION,
                    "DIRECT_AUDIO_CONTRACT_VERSION_MISMATCH");
            exactText(input, "language", LANGUAGE,
                    "DIRECT_AUDIO_LANGUAGE_UNSUPPORTED");
            exactText(input, "evidence_mode", "DIRECT_AUDIO",
                    "DIRECT_AUDIO_EVIDENCE_MODE_INVALID");
            exactText(input, "evaluation_status", "OBSERVED",
                    "DIRECT_AUDIO_STATUS_INVALID");

            JsonNode evaluator = input.get("evaluator");
            exactObject(evaluator, EVALUATOR_FIELDS, "DIRECT_AUDIO_EVALUATOR_INVALID");
            exactText(evaluator, "evaluator_id", expected.evaluatorId(),
                    "DIRECT_AUDIO_EVALUATOR_MISMATCH");
            exactText(evaluator, "model", expected.model(),
                    "DIRECT_AUDIO_MODEL_MISMATCH");
            exactText(evaluator, "capability_id",
                    DirectAudioSpeakingEvaluationService.CAPABILITY_ID,
                    "DIRECT_AUDIO_CAPABILITY_MISMATCH");

            JsonNode policy = input.get("policy");
            exactObject(policy, POLICY_FIELDS, "DIRECT_AUDIO_POLICY_INVALID");
            exactText(policy, "policy_bundle_id",
                    DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID,
                    "DIRECT_AUDIO_POLICY_MISMATCH");
            exactText(policy, "policy_bundle_fingerprint",
                    DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_FINGERPRINT,
                    "DIRECT_AUDIO_POLICY_MISMATCH");

            JsonNode calibration = input.get("calibration");
            exactObject(calibration, CALIBRATION_FIELDS,
                    "DIRECT_AUDIO_CALIBRATION_INVALID");
            String profileId = requiredText(calibration, "profile_id");
            String calibrationVersion = requiredText(calibration, "version");
            var profile = calibrationAuthority.resolve(profileId, calibrationVersion)
                    .orElseThrow(() -> rejected("DIRECT_AUDIO_CALIBRATION_NOT_READY"));
            if (profile.scoreReleaseApproved()) {
                throw rejected("DIRECT_AUDIO_SCORE_RELEASE_FORBIDDEN");
            }
            exactText(calibration, "corpus_evidence_id", profile.corpusEvidenceId(),
                    "DIRECT_AUDIO_CALIBRATION_MISMATCH");
            exactText(calibration, "acoustic_calibration_evidence_id",
                    profile.acousticCalibrationEvidenceId(),
                    "DIRECT_AUDIO_CALIBRATION_MISMATCH");
            exactText(calibration, "fairness_evidence_id", profile.fairnessEvidenceId(),
                    "DIRECT_AUDIO_CALIBRATION_MISMATCH");
            exactText(calibration, "repeatability_evidence_id",
                    profile.repeatabilityEvidenceId(),
                    "DIRECT_AUDIO_CALIBRATION_MISMATCH");
            if (!LANGUAGE.equals(profile.language())) {
                throw rejected("DIRECT_AUDIO_CALIBRATION_LANGUAGE_MISMATCH");
            }

            JsonNode receipt = input.get("audio_consumption");
            exactObject(receipt, CONSUMPTION_FIELDS,
                    "DIRECT_AUDIO_CONSUMPTION_RECEIPT_INVALID");
            exactText(receipt, "provider_request_id", expected.providerRequestId(),
                    "DIRECT_AUDIO_CONSUMPTION_RECEIPT_MISMATCH");
            if (!receipt.path("audio_consumed").isBoolean()
                    || !receipt.path("audio_consumed").booleanValue()) {
                throw rejected("DIRECT_AUDIO_CONSUMPTION_UNPROVEN");
            }
            exactText(receipt, "audio_digest", expected.audioDigest(),
                    "DIRECT_AUDIO_CONSUMPTION_RECEIPT_MISMATCH");
            exactText(receipt, "provider_cache_identity",
                    expected.providerCacheIdentity(),
                    "DIRECT_AUDIO_CONSUMPTION_RECEIPT_MISMATCH");
            exactText(receipt, "provenance_digest", expected.provenanceDigest(),
                    "DIRECT_AUDIO_CONSUMPTION_RECEIPT_MISMATCH");

            long durationMs = requiredLong(input, "audio_duration_ms");
            if (durationMs <= 0 || durationMs > 600_000) {
                throw rejected("DIRECT_AUDIO_DURATION_INVALID");
            }
            List<DirectAudioAcousticObservationResult.DimensionObservation> observations =
                    observations(input.get("observations"), durationMs);
            BigDecimal calculatedTotal = observations.stream()
                    .map(DirectAudioAcousticObservationResult.DimensionObservation
                            ::providerSignalValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal declaredTotal = boundedDecimal(
                    input, "provider_observation_total", BigDecimal.ZERO,
                    BigDecimal.valueOf(2), "DIRECT_AUDIO_TOTAL_INVALID");
            if (declaredTotal.compareTo(calculatedTotal) != 0) {
                throw rejected("DIRECT_AUDIO_TOTAL_INCONSISTENT");
            }
            BigDecimal calculatedConfidence = observations.stream()
                    .map(DirectAudioAcousticObservationResult.DimensionObservation::confidence)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(observations.size()));
            BigDecimal providerConfidence = boundedDecimal(
                    input, "provider_confidence", BigDecimal.ZERO, BigDecimal.ONE,
                    "DIRECT_AUDIO_CONFIDENCE_INVALID");
            if (providerConfidence.compareTo(calculatedConfidence) != 0) {
                throw rejected("DIRECT_AUDIO_CONFIDENCE_INCONSISTENT");
            }

            JsonNode release = input.get("score_release");
            exactObject(release, RELEASE_FIELDS, "DIRECT_AUDIO_RELEASE_GATE_INVALID");
            if (!release.path("eligible").isBoolean()
                    || release.path("eligible").booleanValue()) {
                throw rejected("DIRECT_AUDIO_SCORE_RELEASE_FORBIDDEN");
            }
            exactText(release, "reason", "DARK_ROLLOUT_ONLY",
                    "DIRECT_AUDIO_RELEASE_GATE_INVALID");

            return new DirectAudioAcousticObservationResult(
                    DirectAudioAcousticObservationResult.State.VALID_DARK_OBSERVATION,
                    CONTRACT_VERSION,
                    LANGUAGE,
                    expected.evaluatorId(),
                    expected.model(),
                    profileId,
                    calibrationVersion,
                    observations,
                    declaredTotal,
                    providerConfidence,
                    expected.providerRequestId(),
                    expected.providerCacheIdentity(),
                    null,
                    false, false, null, null);
        } catch (Rejected exception) {
            return DirectAudioAcousticObservationResult.rejected(exception.code);
        } catch (RuntimeException exception) {
            return DirectAudioAcousticObservationResult.rejected(
                    "DIRECT_AUDIO_CONTRACT_INVALID");
        }
    }

    private static List<DirectAudioAcousticObservationResult.DimensionObservation>
            observations(JsonNode array, long durationMs) {
        if (array == null || !array.isArray() || array.size() != 2) {
            throw rejected("DIRECT_AUDIO_OBSERVATIONS_INVALID");
        }
        EnumSet<DirectAudioAcousticObservationResult.Dimension> seen =
                EnumSet.noneOf(DirectAudioAcousticObservationResult.Dimension.class);
        Set<String> evidenceIds = new HashSet<>();
        List<DirectAudioAcousticObservationResult.DimensionObservation> result =
                new ArrayList<>();
        for (JsonNode node : array) {
            exactObject(node, OBSERVATION_FIELDS, "DIRECT_AUDIO_OBSERVATION_INVALID");
            DirectAudioAcousticObservationResult.Dimension dimension;
            try {
                dimension = DirectAudioAcousticObservationResult.Dimension.valueOf(
                        requiredText(node, "dimension"));
            } catch (RuntimeException exception) {
                throw rejected("DIRECT_AUDIO_DIMENSION_INVALID");
            }
            if (!seen.add(dimension)) {
                throw rejected("DIRECT_AUDIO_DIMENSION_DUPLICATE");
            }
            BigDecimal signal = boundedDecimal(node, "signal_value",
                    BigDecimal.ZERO, BigDecimal.ONE, "DIRECT_AUDIO_SIGNAL_INVALID");
            BigDecimal confidence = boundedDecimal(node, "confidence",
                    BigDecimal.ZERO, BigDecimal.ONE, "DIRECT_AUDIO_CONFIDENCE_INVALID");
            JsonNode evidence = node.get("evidence");
            if (evidence == null || !evidence.isArray() || evidence.isEmpty()) {
                throw rejected("DIRECT_AUDIO_EVIDENCE_MISSING");
            }
            List<DirectAudioAcousticObservationResult.EvidenceSpan> spans =
                    new ArrayList<>();
            for (JsonNode span : evidence) {
                exactObject(span, EVIDENCE_FIELDS, "DIRECT_AUDIO_EVIDENCE_INVALID");
                String evidenceId = requiredText(span, "evidence_id");
                if (!evidenceIds.add(evidenceId)) {
                    throw rejected("DIRECT_AUDIO_EVIDENCE_DUPLICATE");
                }
                long start = requiredLong(span, "start_ms");
                long end = requiredLong(span, "end_ms");
                if (start < 0 || end <= start || end > durationMs) {
                    throw rejected("DIRECT_AUDIO_EVIDENCE_TIMESTAMP_INVALID");
                }
                spans.add(new DirectAudioAcousticObservationResult.EvidenceSpan(
                        evidenceId, start, end,
                        boundedDecimal(span, "confidence", BigDecimal.ZERO,
                                BigDecimal.ONE, "DIRECT_AUDIO_CONFIDENCE_INVALID"),
                        requiredText(span, "observation")));
            }
            result.add(new DirectAudioAcousticObservationResult.DimensionObservation(
                    dimension, signal, confidence, spans));
        }
        if (!seen.equals(EnumSet.allOf(
                DirectAudioAcousticObservationResult.Dimension.class))) {
            throw rejected("DIRECT_AUDIO_DIMENSIONS_INCOMPLETE");
        }
        return List.copyOf(result);
    }

    private static void exactObject(JsonNode node, Set<String> fields, String code) {
        if (node == null || !node.isObject()) {
            throw rejected(code);
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(fields)) {
            throw rejected(code);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw rejected("DIRECT_AUDIO_REQUIRED_TEXT_INVALID");
        }
        return value.textValue();
    }

    private static void exactText(
            JsonNode node, String field, String expected, String code) {
        if (!Objects.equals(requiredText(node, field), expected)) {
            throw rejected(code);
        }
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw rejected("DIRECT_AUDIO_INTEGER_INVALID");
        }
        return value.longValue();
    }

    private static BigDecimal boundedDecimal(
            JsonNode node, String field, BigDecimal minimum,
            BigDecimal maximum, String code) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw rejected(code);
        }
        BigDecimal number = value.decimalValue();
        if (number.compareTo(minimum) < 0 || number.compareTo(maximum) > 0) {
            throw rejected(code);
        }
        return number.stripTrailingZeros();
    }

    private static Rejected rejected(String code) {
        return new Rejected(code);
    }

    public record ExpectedContext(
            String evaluatorId,
            String model,
            String providerRequestId,
            String audioDigest,
            String providerCacheIdentity,
            String provenanceDigest) {
        public ExpectedContext {
            evaluatorId = required(evaluatorId);
            model = required(model);
            providerRequestId = required(providerRequestId);
            audioDigest = digest(audioDigest);
            providerCacheIdentity = required(providerCacheIdentity);
            provenanceDigest = digest(provenanceDigest);
        }

        private static String required(String value) {
            String normalized = Objects.requireNonNull(value).trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Expected context field is required");
            }
            return normalized;
        }

        private static String digest(String value) {
            String normalized = required(value);
            if (!normalized.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Expected SHA-256 digest");
            }
            return normalized;
        }
    }

    private static final class Rejected extends RuntimeException {
        private final String code;

        private Rejected(String code) {
            super(code, null, false, false);
            this.code = code;
        }
    }
}
