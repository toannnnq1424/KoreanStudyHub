package com.ksh.features.practice.ai.speaking.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanDirectAudioAlignmentNormalizerTest {

    private static final Path FIXTURES = Path.of(
            "src/test/resources/practice/direct-audio");
    private final ObjectMapper mapper = new ObjectMapper();
    private final KoreanDirectAudioAlignmentNormalizer normalizer =
            new KoreanDirectAudioAlignmentNormalizer();

    @Test
    void validDedicatedAlignmentIsCompleteButNeverLearnerVisibleOrScoreBearing()
            throws Exception {
        var result = normalizer.normalize(valid(), context());

        assertThat(result.status()).isEqualTo(
                KoreanDirectAudioAlignmentResult.Status.COMPLETE);
        assertThat(result.spans()).extracting(
                KoreanDirectAudioAlignmentResult.Span::level)
                .containsExactly(
                        KoreanDirectAudioAlignmentResult.Level.EOJJEOL,
                        KoreanDirectAudioAlignmentResult.Level.SYLLABLE);
        assertThat(result.rejectedItems()).isEmpty();
        assertThat(result.scoreReleaseEligible()).isFalse();
        assertThat(result.learnerVisible()).isFalse();
        assertThat(result.playbackUrl()).isNull();
        assertThat(result.holisticScore()).isNull();
        assertThat(result.attemptPoints()).isNull();
    }

    @Test
    void adversarialFixturesSeparateAtomicFailureFromItemLevelPartial()
            throws Exception {
        JsonNode cases = mapper.readTree(Files.readString(FIXTURES.resolve(
                "korean-alignment-adversarial-patches-v1.json")));

        for (JsonNode testCase : cases) {
            ObjectNode input = valid();
            apply(input, testCase);
            var result = normalizer.normalize(input, context());
            assertThat(result.status().name())
                    .as(testCase.path("case").asText())
                    .isEqualTo(testCase.path("expected_status").asText());
            assertThat(result.reason())
                    .as(testCase.path("case").asText())
                    .isEqualTo(testCase.path("expected_reason").asText());
            assertThat(result.scoreReleaseEligible()).isFalse();
            assertThat(result.learnerVisible()).isFalse();
        }
    }

    @Test
    void invalidChildDropsOnlyChildAndPreservesSafeEojjeolRange() throws Exception {
        ObjectNode input = valid();
        ((ObjectNode) input.withArray("spans").get(1))
                .put("confidence", 1.1);

        var result = normalizer.normalize(input, context());

        assertThat(result.status()).isEqualTo(
                KoreanDirectAudioAlignmentResult.Status.PARTIAL_NON_SCORE);
        assertThat(result.spans()).extracting(
                KoreanDirectAudioAlignmentResult.Span::spanId)
                .containsExactly("eojjeol-1");
        assertThat(result.rejectedItems())
                .extracting(KoreanDirectAudioAlignmentResult.RejectedItem::reason)
                .containsExactly("ALIGNMENT_CONFIDENCE_INVALID");
    }

    @Test
    void explicitUnavailableHasNoSpansAndRemainsNonScoreBearing() throws Exception {
        ObjectNode input = valid();
        input.put("alignment_status", "UNAVAILABLE");
        input.put("alignment_reason", "ALIGNER_TIMEOUT");
        input.withArray("spans").removeAll();

        var result = normalizer.normalize(input, context());

        assertThat(result.status()).isEqualTo(
                KoreanDirectAudioAlignmentResult.Status.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo("ALIGNER_TIMEOUT");
        assertThat(result.spans()).isEmpty();
    }

    private ObjectNode valid() throws Exception {
        return (ObjectNode) mapper.readTree(Files.readString(FIXTURES.resolve(
                "korean-alignment-valid-v1.json")));
    }

    private static KoreanDirectAudioAlignmentNormalizer.ExpectedContext context() {
        return new KoreanDirectAudioAlignmentNormalizer.ExpectedContext(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "TEST-TRANSCRIPT-V1",
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "TEST-ALIGNER", "TEST-KOREAN-ALIGNER", "TEST-V1",
                "TEST-KOREAN-TIMESTAMP-EVIDENCE",
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "TEST-CALIBRATION-PROFILE");
    }

    private static void apply(ObjectNode root, JsonNode testCase) {
        String pointer = testCase.path("pointer").asText();
        int slash = pointer.lastIndexOf('/');
        String parentPointer = slash == 0 ? "" : pointer.substring(0, slash);
        String field = pointer.substring(slash + 1);
        JsonNode parent = parentPointer.isEmpty() ? root : root.at(parentPointer);
        if (!(parent instanceof ObjectNode object)) {
            throw new IllegalArgumentException("Patch parent is not an object: " + pointer);
        }
        if ("remove".equals(testCase.path("op").asText())) {
            object.remove(field);
        } else {
            object.set(field, testCase.get("value"));
        }
    }
}
