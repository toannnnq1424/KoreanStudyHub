package com.ksh.features.practice.ai.speaking.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic critical-envelope validation plus item-by-item diagnostic span
 * validation. Invalid independent spans do not erase safe sibling spans.
 */
@Component
public final class KoreanDirectAudioAlignmentNormalizer {

    public static final String CONTRACT_VERSION =
            "ksh-speaking-korean-alignment-v1";
    public static final String LANGUAGE = "ko-KR";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "contract_version", "language", "evidence_mode", "alignment_status",
            "alignment_reason", "audio", "transcript", "engine", "provenance",
            "spans", "score_release");
    private static final Set<String> AUDIO_FIELDS = Set.of(
            "duration_ms", "audio_digest", "authorized_handle_fingerprint");
    private static final Set<String> TRANSCRIPT_FIELDS = Set.of(
            "identity", "text_sha256", "offset_unit");
    private static final Set<String> ENGINE_FIELDS = Set.of(
            "component_type", "provider", "model", "version",
            "capability_evidence_id");
    private static final Set<String> PROVENANCE_FIELDS = Set.of(
            "provider_request_fingerprint", "policy_bundle_id",
            "calibration_profile_id");
    private static final Set<String> SPAN_FIELDS = Set.of(
            "span_id", "level", "parent_span_id", "token_id", "surface_ko",
            "utf16_start", "utf16_end", "start_ms", "end_ms",
            "expected_pronunciation", "observed_pronunciation", "issue_code",
            "confidence", "evidence");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "source", "evidence_id");
    private static final Set<String> RELEASE_FIELDS = Set.of("eligible", "reason");
    private static final Set<String> ENGINE_TYPES = Set.of(
            "DEDICATED_FORCED_ALIGNER", "ASR_WORD_TIMESTAMPS");

    public KoreanDirectAudioAlignmentResult normalize(
            JsonNode input, ExpectedContext expected) {
        try {
            Objects.requireNonNull(expected);
            exactObject(input, ROOT_FIELDS, "ALIGNMENT_CRITICAL_ENVELOPE_INVALID");
            exactText(input, "contract_version", CONTRACT_VERSION,
                    "ALIGNMENT_CONTRACT_VERSION_MISMATCH");
            exactText(input, "language", LANGUAGE, "ALIGNMENT_LANGUAGE_UNSUPPORTED");
            exactText(input, "evidence_mode", "DIRECT_AUDIO_ALIGNMENT",
                    "ALIGNMENT_EVIDENCE_MODE_INVALID");

            KoreanDirectAudioAlignmentResult.Status declaredStatus = enumValue(
                    KoreanDirectAudioAlignmentResult.Status.class,
                    requiredText(input, "alignment_status"),
                    "ALIGNMENT_STATUS_INVALID");
            String declaredReason = requiredText(input, "alignment_reason");

            JsonNode audio = input.get("audio");
            exactObject(audio, AUDIO_FIELDS, "ALIGNMENT_AUDIO_IDENTITY_INVALID");
            long durationMs = requiredLong(audio, "duration_ms");
            if (durationMs <= 0 || durationMs > 600_000) {
                throw critical("ALIGNMENT_AUDIO_DURATION_INVALID");
            }
            exactText(audio, "audio_digest", expected.audioDigest(),
                    "ALIGNMENT_AUDIO_IDENTITY_MISMATCH");
            exactText(audio, "authorized_handle_fingerprint",
                    expected.authorizedHandleFingerprint(),
                    "ALIGNMENT_AUDIO_IDENTITY_MISMATCH");

            JsonNode transcript = input.get("transcript");
            exactObject(transcript, TRANSCRIPT_FIELDS,
                    "ALIGNMENT_TRANSCRIPT_IDENTITY_INVALID");
            exactText(transcript, "identity", expected.transcriptIdentity(),
                    "ALIGNMENT_TRANSCRIPT_IDENTITY_MISMATCH");
            exactText(transcript, "text_sha256", expected.transcriptTextSha256(),
                    "ALIGNMENT_TRANSCRIPT_IDENTITY_MISMATCH");
            exactText(transcript, "offset_unit", "UTF16_CODE_UNIT",
                    "ALIGNMENT_OFFSET_UNIT_INVALID");

            JsonNode engine = input.get("engine");
            exactObject(engine, ENGINE_FIELDS, "ALIGNMENT_ENGINE_INVALID");
            String engineType = requiredText(engine, "component_type");
            if (!ENGINE_TYPES.contains(engineType)) {
                throw critical("ALIGNMENT_DEDICATED_COMPONENT_REQUIRED");
            }
            exactText(engine, "provider", expected.engineProvider(),
                    "ALIGNMENT_ENGINE_MISMATCH");
            exactText(engine, "model", expected.engineModel(),
                    "ALIGNMENT_ENGINE_MISMATCH");
            exactText(engine, "version", expected.engineVersion(),
                    "ALIGNMENT_ENGINE_MISMATCH");
            exactText(engine, "capability_evidence_id",
                    expected.capabilityEvidenceId(),
                    "ALIGNMENT_CAPABILITY_EVIDENCE_MISMATCH");

            JsonNode provenance = input.get("provenance");
            exactObject(provenance, PROVENANCE_FIELDS,
                    "ALIGNMENT_PROVENANCE_INVALID");
            exactText(provenance, "provider_request_fingerprint",
                    expected.providerRequestFingerprint(),
                    "ALIGNMENT_PROVENANCE_MISMATCH");
            exactText(provenance, "policy_bundle_id",
                    DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID,
                    "ALIGNMENT_POLICY_MISMATCH");
            exactText(provenance, "calibration_profile_id",
                    expected.calibrationProfileId(),
                    "ALIGNMENT_CALIBRATION_MISMATCH");

            JsonNode release = input.get("score_release");
            exactObject(release, RELEASE_FIELDS, "ALIGNMENT_RELEASE_GATE_INVALID");
            if (!release.path("eligible").isBoolean()
                    || release.path("eligible").booleanValue()) {
                throw critical("ALIGNMENT_SCORE_RELEASE_FORBIDDEN");
            }
            exactText(release, "reason", "ALIGNMENT_DARK_ONLY",
                    "ALIGNMENT_RELEASE_GATE_INVALID");

            JsonNode spanNodes = input.get("spans");
            if (spanNodes == null || !spanNodes.isArray()) {
                throw critical("ALIGNMENT_SPANS_INVALID");
            }
            if (declaredStatus == KoreanDirectAudioAlignmentResult.Status.UNAVAILABLE) {
                if (!spanNodes.isEmpty() || "NONE".equals(declaredReason)) {
                    throw critical("ALIGNMENT_UNAVAILABLE_STATE_INCONSISTENT");
                }
                return KoreanDirectAudioAlignmentResult.unavailable(declaredReason);
            }
            if (declaredStatus == KoreanDirectAudioAlignmentResult.Status.COMPLETE
                    && !"NONE".equals(declaredReason)) {
                throw critical("ALIGNMENT_COMPLETE_STATE_INCONSISTENT");
            }

            List<KoreanDirectAudioAlignmentResult.Span> candidates = new ArrayList<>();
            List<KoreanDirectAudioAlignmentResult.RejectedItem> rejected =
                    new ArrayList<>();
            Set<String> spanIds = new HashSet<>();
            for (int index = 0; index < spanNodes.size(); index++) {
                JsonNode spanNode = spanNodes.get(index);
                String marker = marker(spanNode, index);
                try {
                    KoreanDirectAudioAlignmentResult.Span span = span(
                            spanNode, durationMs);
                    if (!spanIds.add(span.spanId())) {
                        throw item("ALIGNMENT_SPAN_ID_DUPLICATE");
                    }
                    candidates.add(span);
                } catch (ItemInvalid invalid) {
                    rejected.add(new KoreanDirectAudioAlignmentResult.RejectedItem(
                            marker, invalid.code));
                }
            }

            Map<String, KoreanDirectAudioAlignmentResult.Span> byId = new HashMap<>();
            candidates.forEach(span -> byId.put(span.spanId(), span));
            List<KoreanDirectAudioAlignmentResult.Span> accepted = new ArrayList<>();
            for (KoreanDirectAudioAlignmentResult.Span span : candidates) {
                if (span.level() == KoreanDirectAudioAlignmentResult.Level.EOJJEOL) {
                    if (span.parentSpanId() != null) {
                        rejected.add(rejected(span, "ALIGNMENT_EOJJEOL_PARENT_FORBIDDEN"));
                    } else {
                        accepted.add(span);
                    }
                    continue;
                }
                KoreanDirectAudioAlignmentResult.Span parent = byId.get(
                        span.parentSpanId());
                if (parent == null
                        || !hierarchyAllowed(parent.level(), span.level())
                        || parent.startMs() > span.startMs()
                        || parent.endMs() < span.endMs()
                        || parent.utf16Start() > span.utf16Start()
                        || parent.utf16End() < span.utf16End()
                        || !parent.tokenId().equals(span.tokenId())) {
                    rejected.add(rejected(span, "ALIGNMENT_PARENT_INCONSISTENT"));
                } else {
                    accepted.add(span);
                }
            }

            if (accepted.isEmpty()) {
                return new KoreanDirectAudioAlignmentResult(
                        KoreanDirectAudioAlignmentResult.Status.UNAVAILABLE,
                        "NO_VALID_ALIGNMENT_SPANS", List.of(), rejected,
                        false, false, null, null, null);
            }
            boolean partial = declaredStatus
                    == KoreanDirectAudioAlignmentResult.Status.PARTIAL_NON_SCORE
                    || !rejected.isEmpty();
            return new KoreanDirectAudioAlignmentResult(
                    partial
                            ? KoreanDirectAudioAlignmentResult.Status.PARTIAL_NON_SCORE
                            : KoreanDirectAudioAlignmentResult.Status.COMPLETE,
                    partial ? "INVALID_ITEMS_DROPPED" : "NONE",
                    accepted, rejected, false, false, null, null, null);
        } catch (CriticalInvalid invalid) {
            return KoreanDirectAudioAlignmentResult.unavailable(invalid.code);
        } catch (RuntimeException exception) {
            return KoreanDirectAudioAlignmentResult.unavailable(
                    "ALIGNMENT_CRITICAL_ENVELOPE_INVALID");
        }
    }

    private static boolean hierarchyAllowed(
            KoreanDirectAudioAlignmentResult.Level parent,
            KoreanDirectAudioAlignmentResult.Level child) {
        return child == KoreanDirectAudioAlignmentResult.Level.SYLLABLE
                ? parent == KoreanDirectAudioAlignmentResult.Level.EOJJEOL
                : (child == KoreanDirectAudioAlignmentResult.Level.JAMO
                        || child == KoreanDirectAudioAlignmentResult.Level.PHONEME)
                        && parent == KoreanDirectAudioAlignmentResult.Level.SYLLABLE;
    }

    private static KoreanDirectAudioAlignmentResult.Span span(
            JsonNode node, long durationMs) {
        exactItemObject(node, SPAN_FIELDS);
        String spanId = itemText(node, "span_id");
        var level = enumItem(KoreanDirectAudioAlignmentResult.Level.class,
                itemText(node, "level"));
        JsonNode parent = node.get("parent_span_id");
        if (parent == null || (!parent.isNull() && !parent.isTextual())) {
            throw item("ALIGNMENT_PARENT_INVALID");
        }
        String parentId = parent.isNull() ? null : parent.textValue();
        if (parentId != null && parentId.isBlank()) {
            throw item("ALIGNMENT_PARENT_INVALID");
        }
        String surface = itemText(node, "surface_ko");
        if (!Normalizer.isNormalized(surface, Normalizer.Form.NFC)) {
            throw item("ALIGNMENT_SURFACE_NOT_NFC");
        }
        int utf16Start = itemInt(node, "utf16_start");
        int utf16End = itemInt(node, "utf16_end");
        long startMs = itemLong(node, "start_ms");
        long endMs = itemLong(node, "end_ms");
        if (utf16Start < 0 || utf16End <= utf16Start
                || startMs < 0 || endMs <= startMs || endMs > durationMs) {
            throw item("ALIGNMENT_SPAN_RANGE_INVALID");
        }
        BigDecimal confidence = itemDecimal(node, "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw item("ALIGNMENT_CONFIDENCE_INVALID");
        }
        JsonNode evidence = node.get("evidence");
        exactItemObject(evidence, EVIDENCE_FIELDS);
        return new KoreanDirectAudioAlignmentResult.Span(
                spanId, level, parentId, itemText(node, "token_id"), surface,
                utf16Start, utf16End, startMs, endMs,
                nullableNfcText(node.get("expected_pronunciation")),
                nullableNfcText(node.get("observed_pronunciation")),
                enumItem(KoreanDirectAudioAlignmentResult.IssueCode.class,
                        itemText(node, "issue_code")),
                confidence,
                enumItem(KoreanDirectAudioAlignmentResult.EvidenceSource.class,
                        itemText(evidence, "source")),
                itemText(evidence, "evidence_id"));
    }

    private static String nullableNfcText(JsonNode node) {
        if (node == null || (!node.isNull() && !node.isTextual())) {
            throw item("ALIGNMENT_PRONUNCIATION_INVALID");
        }
        if (node.isNull()) return null;
        if (node.textValue().isBlank()
                || !Normalizer.isNormalized(node.textValue(), Normalizer.Form.NFC)) {
            throw item("ALIGNMENT_PRONUNCIATION_INVALID");
        }
        return node.textValue();
    }

    private static String marker(JsonNode node, int index) {
        return node != null && node.path("span_id").isTextual()
                ? node.path("span_id").textValue()
                : "index:" + index;
    }

    private static KoreanDirectAudioAlignmentResult.RejectedItem rejected(
            KoreanDirectAudioAlignmentResult.Span span, String reason) {
        return new KoreanDirectAudioAlignmentResult.RejectedItem(span.spanId(), reason);
    }

    private static void exactObject(JsonNode node, Set<String> fields, String code) {
        if (node == null || !node.isObject() || !fieldNames(node).equals(fields)) {
            throw critical(code);
        }
    }

    private static void exactItemObject(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || !fieldNames(node).equals(fields)) {
            throw item("ALIGNMENT_SPAN_SCHEMA_INVALID");
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw critical("ALIGNMENT_REQUIRED_TEXT_INVALID");
        }
        return value.textValue();
    }

    private static void exactText(
            JsonNode node, String field, String expected, String code) {
        if (!Objects.equals(requiredText(node, field), expected)) {
            throw critical(code);
        }
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw critical("ALIGNMENT_REQUIRED_INTEGER_INVALID");
        }
        return value.longValue();
    }

    private static String itemText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw item("ALIGNMENT_SPAN_REQUIRED_TEXT_INVALID");
        }
        return value.textValue();
    }

    private static int itemInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw item("ALIGNMENT_SPAN_INTEGER_INVALID");
        }
        return value.intValue();
    }

    private static long itemLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw item("ALIGNMENT_SPAN_INTEGER_INVALID");
        }
        return value.longValue();
    }

    private static BigDecimal itemDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw item("ALIGNMENT_SPAN_DECIMAL_INVALID");
        }
        return value.decimalValue();
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String code) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw critical(code);
        }
    }

    private static <E extends Enum<E>> E enumItem(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw item("ALIGNMENT_SPAN_ENUM_INVALID");
        }
    }

    private static CriticalInvalid critical(String code) {
        return new CriticalInvalid(code);
    }

    private static ItemInvalid item(String code) {
        return new ItemInvalid(code);
    }

    private static final class CriticalInvalid extends RuntimeException {
        private final String code;

        private CriticalInvalid(String code) {
            this.code = code;
        }
    }

    private static final class ItemInvalid extends RuntimeException {
        private final String code;

        private ItemInvalid(String code) {
            this.code = code;
        }
    }

    public record ExpectedContext(
            String audioDigest,
            String authorizedHandleFingerprint,
            String transcriptIdentity,
            String transcriptTextSha256,
            String engineProvider,
            String engineModel,
            String engineVersion,
            String capabilityEvidenceId,
            String providerRequestFingerprint,
            String calibrationProfileId) {
    }
}
