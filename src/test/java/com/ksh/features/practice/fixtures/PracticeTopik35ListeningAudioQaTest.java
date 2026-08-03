package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeTopik35ListeningAudioQaTest {
    private static final Path PACKAGE = Path.of(
            "docs/operations/practice-topik35-listening-audio-qa.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-listening-audio-qa.schema.json");
    private static final Path IMPORT_PACKAGE = Path.of(
            "docs/operations/practice-topik35-listening-import-package.json");
    private static final Path TRANSCRIPT_PACKAGE = Path.of(
            "docs/operations/practice-topik35-listening-transcript-payload.json");
    private static final int[][] BOUNDARIES = {
            {1, 3}, {4, 8}, {9, 12}, {13, 16}, {17, 20},
            {21, 22}, {23, 24}, {25, 26}, {27, 28}, {29, 30},
            {31, 32}, {33, 34}, {35, 36}, {37, 38}, {39, 40},
            {41, 42}, {43, 44}, {45, 46}, {47, 48}, {49, 50}
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinsExactSourceIdentityAndByteVerifiedSingleMp3Program() throws Exception {
        JsonNode root = read(PACKAGE);

        validate(root);
        assertThat(root.at("/sourceIdentity/sourceUrl").asText())
                .isEqualTo("https://www.youtube.com/watch?v=zqd7AQTKk6k");
        assertThat(root.at("/sourceIdentity/title").asText())
                .contains("LISTENING TOPIK 35", "TOPIK 듣기 35회");
        assertThat(root.at("/sourceIdentity/identityDecision").asText())
                .isEqualTo("MATCHED_TOPIK_35_LISTENING");
        assertThat(root.at("/sourceAudio/sha256").asText())
                .isEqualTo("beb8c362a7ebe9905467fdfd637aa9db15ebfdd8fb28e22f08ed574f3ef0fcaf");
        assertThat(root.at("/sourceAudio/logicalKey").asText())
                .contains("/" + root.at("/sourceAudio/sha256").asText() + ".m4a");
        assertThat(root.at("/derivedProgram/sha256").asText())
                .isEqualTo("0f8f7504849689b15c5dcb5f0892580c81c5285b37ead05058e47a9645a91ee1");
        assertThat(root.at("/derivedProgram/logicalKey").asText())
                .contains("/" + root.at("/derivedProgram/sha256").asText() + ".mp3");
        assertThat(root.at("/sourceAudio/durationMs").asLong()).isEqualTo(3797136);
        assertThat(root.at("/derivedProgram/durationMs").asLong()).isEqualTo(3797136);
        assertThat(root.at("/deliveryPolicy/singleContinuousProgram").asBoolean())
                .isTrue();
    }

    @Test
    void boundaryQaRemainsHumanAuditoryCaptionFreeAndFailClosed() throws Exception {
        JsonNode root = read(PACKAGE);
        JsonNode qa = root.path("manualBoundaryQa");

        assertThat(qa.path("captionsAllowedForTimestampInference").asBoolean()).isFalse();
        assertThat(qa.path("captionsUsedForTimestampInference").asBoolean()).isFalse();
        assertThat(qa.path("automatedSpeechRecognitionUsed").asBoolean()).isFalse();
        assertThat(qa.path("signalScanAcceptedAsBoundaryEvidence").asBoolean()).isFalse();
        assertThat(qa.path("auditoryReviewerAvailable").asBoolean()).isFalse();
        assertThat(qa.path("groups")).hasSize(20);
        qa.path("groups").forEach(group -> {
            assertThat(group.path("status").asText())
                    .isEqualTo("PENDING_HUMAN_AUDITORY_QA");
            assertThat(group.path("startMs").isNull()).isTrue();
            assertThat(group.path("endMs").isNull()).isTrue();
            assertThat(group.path("reviewerEvidenceId").isNull()).isTrue();
        });
        assertThat(root.at("/validationSummary/boundaryReadyGroupCount").asInt())
                .isZero();
        assertThat(root.at("/validationSummary/loadReady").asBoolean()).isFalse();
    }

    @Test
    void exactGroupOrderMatchesAuthoritativeTranscriptAndImportPackages()
            throws Exception {
        JsonNode root = read(PACKAGE);
        JsonNode imported = read(IMPORT_PACKAGE);
        JsonNode transcript = read(TRANSCRIPT_PACKAGE);
        Set<Integer> covered = new HashSet<>();

        for (int index = 0; index < BOUNDARIES.length; index++) {
            JsonNode audioGroup = root.at("/manualBoundaryQa/groups").get(index);
            JsonNode importGroup = imported.path("groups").get(index);
            JsonNode transcriptGroup = transcript.path("groups").get(index);
            int from = BOUNDARIES[index][0];
            int to = BOUNDARIES[index][1];

            assertThat(audioGroup.path("questionFrom").asInt()).isEqualTo(from);
            assertThat(audioGroup.path("questionTo").asInt()).isEqualTo(to);
            assertThat(audioGroup.path("groupId").asText())
                    .isEqualTo(importGroup.path("groupId").asText())
                    .isEqualTo(transcriptGroup.path("groupId").asText());
            for (int question = from; question <= to; question++) {
                assertThat(covered.add(question)).isTrue();
            }
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList());
        assertThat(imported.at("/audioQaBinding/packageId").asText())
                .isEqualTo(root.path("packageId").asText());
        assertThat(imported.at("/sourceBindings/audioProgram/logicalKey").asText())
                .isEqualTo(root.at("/derivedProgram/logicalKey").asText());
    }

    @Test
    void validatorRejectsIdentityDigestCaptionBoundaryAndReadinessTampering()
            throws Exception {
        ObjectNode wrongVideo = copy();
        ((ObjectNode) wrongVideo.path("sourceIdentity")).put("videoId", "different");
        assertThatThrownBy(() -> validate(wrongVideo)).hasMessageContaining("identity");

        ObjectNode wrongDigest = copy();
        ((ObjectNode) wrongDigest.path("derivedProgram")).put("sha256", "0".repeat(64));
        assertThatThrownBy(() -> validate(wrongDigest)).hasMessageContaining("derived");

        ObjectNode captionUsed = copy();
        ((ObjectNode) captionUsed.path("manualBoundaryQa"))
                .put("captionsUsedForTimestampInference", true);
        assertThatThrownBy(() -> validate(captionUsed)).hasMessageContaining("caption");

        ObjectNode inventedBoundary = copy();
        ((ObjectNode) inventedBoundary.at("/manualBoundaryQa/groups/0"))
                .put("startMs", 1000);
        assertThatThrownBy(() -> validate(inventedBoundary)).hasMessageContaining("boundary");

        ObjectNode falseReady = copy();
        ((ObjectNode) falseReady.path("validationSummary")).put("loadReady", true);
        assertThatThrownBy(() -> validate(falseReady)).hasMessageContaining("load gate");
    }

    @Test
    void packageContainsNoBinaryPathDeliveryUrlOrSecretAndSchemaIsClosed()
            throws Exception {
        String raw = Files.readString(PACKAGE);
        JsonNode schema = read(SCHEMA);

        assertThat(raw).doesNotContain(
                "/Users/", "/tmp/", "uploads/", "file://", "googlevideo.com",
                "audio_url", "bucketName", "r2://", "Authorization", "Bearer ");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/sourceIdentity/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/sourceAudio/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/derivedProgram/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/group/additionalProperties").asBoolean())
                .isFalse();
        assertThat(read(IMPORT_PACKAGE).at(
                "/sourceBindings/captionReference/timestampUse").asText())
                .isEqualTo("PROHIBITED_FOR_TIMESTAMP_INFERENCE");
    }

    private JsonNode read(Path path) throws Exception {
        return objectMapper.readTree(Files.readString(path));
    }

    private ObjectNode copy() throws Exception {
        return (ObjectNode) read(PACKAGE);
    }

    private static void validate(JsonNode root) {
        require("practice-topik35-listening-audio-qa-v1".equals(
                root.path("schemaVersion").asText()), "schema identity");
        require("zqd7AQTKk6k".equals(root.at("/sourceIdentity/videoId").asText())
                && root.at("/sourceIdentity/title").asText().contains("TOPIK 35")
                && "MATCHED_TOPIK_35_LISTENING".equals(
                root.at("/sourceIdentity/identityDecision").asText()), "source identity");
        require("beb8c362a7ebe9905467fdfd637aa9db15ebfdd8fb28e22f08ed574f3ef0fcaf"
                .equals(root.at("/sourceAudio/sha256").asText()), "source digest");
        require("0f8f7504849689b15c5dcb5f0892580c81c5285b37ead05058e47a9645a91ee1"
                .equals(root.at("/derivedProgram/sha256").asText()), "derived digest");
        require(root.at("/sourceAudio/logicalKey").asText().contains(
                "/" + root.at("/sourceAudio/sha256").asText() + ".m4a"),
                "source logical key digest");
        require(root.at("/derivedProgram/logicalKey").asText().contains(
                "/" + root.at("/derivedProgram/sha256").asText() + ".mp3"),
                "derived logical key digest");
        require(root.at("/sourceAudio/durationMs").asLong() == 3797136
                && root.at("/derivedProgram/durationMs").asLong() == 3797136,
                "duration identity");

        JsonNode qa = root.path("manualBoundaryQa");
        require(!qa.path("captionsAllowedForTimestampInference").asBoolean()
                && !qa.path("captionsUsedForTimestampInference").asBoolean()
                && !qa.path("automatedSpeechRecognitionUsed").asBoolean()
                && !qa.path("signalScanAcceptedAsBoundaryEvidence").asBoolean(),
                "caption and signal policy");
        require(qa.path("groups").size() == BOUNDARIES.length, "group count");
        for (int index = 0; index < BOUNDARIES.length; index++) {
            JsonNode group = qa.path("groups").get(index);
            require(group.path("questionFrom").asInt() == BOUNDARIES[index][0]
                    && group.path("questionTo").asInt() == BOUNDARIES[index][1],
                    "group boundary");
            require("PENDING_HUMAN_AUDITORY_QA".equals(group.path("status").asText())
                    && group.path("startMs").isNull()
                    && group.path("endMs").isNull()
                    && group.path("reviewerEvidenceId").isNull(),
                    "boundary must remain pending");
        }
        require(!root.at("/validationSummary/loadReady").asBoolean()
                && root.at("/validationSummary/boundaryReadyGroupCount").asInt() == 0,
                "load gate");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
