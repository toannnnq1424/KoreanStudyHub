package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioAcousticResponseNormalizerTest {

    private static final Path FIXTURES = Path.of(
            "src/test/resources/practice/direct-audio");
    private final ObjectMapper mapper = new ObjectMapper();
    private final DirectAudioAcousticResponseNormalizer normalizer =
            new DirectAudioAcousticResponseNormalizer(fixtureCalibration());

    @Test
    void validCapturedResponseProducesDarkObservationsButNoReleaseValues()
            throws Exception {
        var result = normalizer.normalize(valid(), context());

        assertThat(result.state()).isEqualTo(
                DirectAudioAcousticObservationResult.State.VALID_DARK_OBSERVATION);
        assertThat(result.observations())
                .extracting(DirectAudioAcousticObservationResult
                        .DimensionObservation::dimension)
                .containsExactly(
                        DirectAudioAcousticObservationResult.Dimension.PRONUNCIATION,
                        DirectAudioAcousticObservationResult.Dimension.FLUENCY);
        assertThat(result.providerObservationTotal())
                .isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(result.providerConfidence())
                .isEqualByComparingTo(new BigDecimal("0.85"));
        assertThat(result.scoreReleaseEligible()).isFalse();
        assertThat(result.presenterEligible()).isFalse();
        assertThat(result.holisticScore()).isNull();
        assertThat(result.attemptPoints()).isNull();
        assertThat(result.completeness().status()).isEqualTo(
                PracticeAiResultCompleteness.Status.COMPLETE);
    }

    @Test
    void invalidIndependentEvidenceItemProducesReviewerOnlyPartialResult()
            throws Exception {
        ObjectNode input = valid();
        ArrayNode evidence = (ArrayNode) input.withArray("observations")
                .get(0).path("evidence");
        ObjectNode invalidSibling = evidence.get(0).deepCopy();
        invalidSibling.put("evidence_id", "evidence-invalid-sibling");
        invalidSibling.put("start_ms", 5000);
        invalidSibling.put("end_ms", 4000);
        evidence.add(invalidSibling);

        var result = normalizer.normalize(input, context());

        assertThat(result.state()).isEqualTo(
                DirectAudioAcousticObservationResult.State.VALID_DARK_OBSERVATION);
        assertThat(result.completeness().status()).isEqualTo(
                PracticeAiResultCompleteness.Status.PARTIAL_NON_SCORE);
        assertThat(result.completeness().rejectedItemCount()).isEqualTo(1);
        assertThat(result.observations().get(0).evidence()).hasSize(1);
        assertThat(result.scoreReleaseEligible()).isFalse();
        assertThat(result.presenterEligible()).isFalse();
        assertThat(result.holisticScore()).isNull();
        assertThat(result.attemptPoints()).isNull();
    }

    @Test
    void capturedAdversarialFixturesFailWithExactBoundedReasons() throws Exception {
        JsonNode cases = mapper.readTree(Files.readString(FIXTURES.resolve(
                "acoustic-adversarial-patches-v1.json")));

        for (JsonNode testCase : cases) {
            ObjectNode input = valid();
            apply(input, testCase);
            var result = normalizer.normalize(input, context());
            assertThat(result.state())
                    .as(testCase.path("case").asText())
                    .isEqualTo(DirectAudioAcousticObservationResult.State
                            .REJECTED_NON_SCORE_BEARING);
            assertThat(result.rejectionCode())
                    .as(testCase.path("case").asText())
                    .isEqualTo(testCase.path("expected_code").asText());
            assertThat(result.observations()).isEmpty();
            assertThat(result.scoreReleaseEligible()).isFalse();
            assertThat(result.presenterEligible()).isFalse();
            assertThat(result.holisticScore()).isNull();
            assertThat(result.attemptPoints()).isNull();
        }
    }

    @Test
    void duplicateDimensionMissingFieldAndDuplicateEvidenceFailClosed()
            throws Exception {
        ObjectNode duplicateDimension = valid();
        ((ObjectNode) duplicateDimension.withArray("observations").get(1))
                .put("dimension", "PRONUNCIATION");
        assertRejected(duplicateDimension, "DIRECT_AUDIO_DIMENSION_DUPLICATE");

        ObjectNode missingField = valid();
        ((ObjectNode) missingField.get("evaluator")).remove("model");
        assertRejected(missingField, "DIRECT_AUDIO_EVALUATOR_INVALID");

        ObjectNode duplicateEvidence = valid();
        String firstId = duplicateEvidence.at(
                "/observations/0/evidence/0/evidence_id").asText();
        ((ObjectNode) duplicateEvidence.at("/observations/1/evidence/0"))
                .put("evidence_id", firstId);
        assertRejected(duplicateEvidence, "DIRECT_AUDIO_EVIDENCE_DUPLICATE");
    }

    @Test
    void productionCalibrationAuthorityKeepsReadinessRed() throws Exception {
        var production = new DirectAudioAcousticResponseNormalizer(
                new DisabledDirectAudioCalibrationProfileAuthority());

        var result = production.normalize(valid(), context());

        assertThat(result.rejectionCode()).isEqualTo(
                "DIRECT_AUDIO_CALIBRATION_NOT_READY");
        assertThat(result.scoreReleaseEligible()).isFalse();
    }

    @Test
    void strictProviderEnvelopeRejectsMalformedTruncatedAndWrongReceiptId()
            throws Exception {
        var parser = new DirectAudioAcousticProviderResponseParser(
                new com.ksh.features.practice.ai.transport
                        .StrictOpenAiStructuredResponseDecoder(),
                normalizer);

        assertThat(parser.parse("not-json".getBytes(StandardCharsets.UTF_8),
                32_768, context()).rejectionCode())
                .isEqualTo("PROVIDER_MALFORMED_ENVELOPE");
        assertThat(parser.parse(envelope("provider-request-test-1", "length"),
                32_768, context()).rejectionCode())
                .isEqualTo("PROVIDER_TRUNCATED_RESPONSE");
        assertThat(parser.parse(envelope("wrong-request", "stop"),
                32_768, context()).rejectionCode())
                .isEqualTo("DIRECT_AUDIO_CONSUMPTION_RECEIPT_MISMATCH");
        assertThat(parser.parse(envelope("provider-request-test-1", "stop"),
                32_768, context()).state())
                .isEqualTo(DirectAudioAcousticObservationResult.State
                        .VALID_DARK_OBSERVATION);
    }

    private void assertRejected(ObjectNode input, String code) {
        assertThat(normalizer.normalize(input, context()).rejectionCode())
                .isEqualTo(code);
    }

    private ObjectNode valid() throws Exception {
        return (ObjectNode) mapper.readTree(Files.readString(FIXTURES.resolve(
                "acoustic-valid-v1.json")));
    }

    private byte[] envelope(String requestId, String finishReason) throws Exception {
        ObjectNode message = mapper.createObjectNode();
        message.put("content", mapper.writeValueAsString(valid()));
        message.putNull("refusal");
        ObjectNode choice = mapper.createObjectNode();
        choice.put("finish_reason", finishReason);
        choice.set("message", message);
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("id", requestId);
        envelope.putArray("choices").add(choice);
        return mapper.writeValueAsBytes(envelope);
    }

    private void apply(ObjectNode root, JsonNode patch) {
        String pointer = patch.path("pointer").asText();
        int separator = pointer.lastIndexOf('/');
        String parentPointer = separator == 0 ? "" : pointer.substring(0, separator);
        String field = pointer.substring(separator + 1);
        ObjectNode parent = (ObjectNode) (parentPointer.isEmpty()
                ? root
                : root.at(parentPointer));
        if ("remove".equals(patch.path("op").asText())) {
            parent.remove(field);
        } else {
            parent.set(field, patch.get("value").deepCopy());
        }
    }

    private static DirectAudioAcousticResponseNormalizer.ExpectedContext context() {
        return new DirectAudioAcousticResponseNormalizer.ExpectedContext(
                "gemini-enterprise-direct-audio-v1",
                "gemini-3.5-flash",
                "provider-request-test-1",
                "a".repeat(64),
                "test-provider-cache-identity",
                "b".repeat(64));
    }

    private static DirectAudioCalibrationProfileAuthority fixtureCalibration() {
        var profile = new DirectAudioCalibrationProfileAuthority.CalibrationProfile(
                "TEST_KOREAN_ACOUSTIC_CALIBRATION",
                "TEST_V1_NON_PRODUCTION",
                "ko-KR",
                "TEST_CORPUS_EVIDENCE",
                "TEST_ACOUSTIC_EVIDENCE",
                "TEST_FAIRNESS_EVIDENCE",
                "TEST_REPEATABILITY_EVIDENCE",
                false);
        return (profileId, version) ->
                profile.profileId().equals(profileId)
                        && profile.version().equals(version)
                        ? Optional.of(profile)
                        : Optional.empty();
    }
}
