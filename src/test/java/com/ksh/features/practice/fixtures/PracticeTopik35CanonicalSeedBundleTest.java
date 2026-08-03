package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeTopik35CanonicalSeedBundleTest {
    private static final Path MANIFEST = Path.of(
            "docs/operations/practice-topik35-canonical-seed-bundle.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-canonical-seed-bundle.schema.json");
    private static final Pattern LOGICAL_KEY = Pattern.compile(
            "^practice-seed/topik35-v1/(source/(document|audio|image)|"
                    + "derived/(audio-mp3|page-image|transcript)|review/artifact)/"
                    + "([0-9a-f]{64})\\.[a-z0-9]{1,10}$");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void provenanceAndContentDigestsArePinned() throws Exception {
        JsonNode root = manifest();
        assertThat(root.path("status").asText())
                .isEqualTo("PROVENANCE_CAPTURED_CONTENT_QA_PENDING");
        assertThat(root.path("rights").path("metadataVisibility").asText())
                .isEqualTo("OPERATIONS_ONLY");
        assertThat(root.path("rights").path("learnerAttributionRequired").asBoolean())
                .isFalse();

        Map<String, ExpectedArtifact> expected = Map.of(
                "topik35-source-index", artifact("3feaac13d1080d46bc990656d0275624853aa4ad22772a7077074287e64c5818", 78248),
                "topik35-listening-writing-pdf", artifact("cc21da6b877b27f0d7d3550c732282d610dadcea03a80842881f17eda3d51323", 949386),
                "topik35-reading-pdf", artifact("d3618891f8afdb4739754067ab8268632998fc522ee0a5519b1286984454a4cd", 13902947),
                "topik35-answer-key-pdf", artifact("60fb5fa5e5a211609d0d6e36bc1cacc490c34ff5a0009e6952e931f9a850d17b", 261669),
                "topik35-listening-transcript-pdf", artifact("25caa51ee044b020f84b75fae2f64b5d95fbb35ebef07f58d5d8fc9186df3922", 951996),
                "topik35-listening-youtube-original-audio", artifact("beb8c362a7ebe9905467fdfd637aa9db15ebfdd8fb28e22f08ed574f3ef0fcaf", 61453300),
                "topik35-listening-program-mp3", artifact("0f8f7504849689b15c5dcb5f0892580c81c5285b37ead05058e47a9645a91ee1", 60755426),
                "topik35-youtube-auto-caption", artifact("50bfb72cddb954cc2fe3c235591f128917b33c9a91832bb13ebc88209b672bb7", 233498),
                "topik35-youtube-capture-metadata", artifact("a75a506b38d61fc6661e99652c44b4e68968c125aff91e9424ea874ca2ce53a6", 554700),
                "topik35-writing-q53-chart", artifact("47977060c3255f13f67d3f041bfe2d998dc3e0f13e23830e3527363ae8b4bee1", 143548));
        Map<String, JsonNode> actual = new HashMap<>();
        root.path("artifacts").forEach(node -> actual.put(
                node.path("artifactId").asText(), node));
        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((id, value) -> {
            JsonNode node = actual.get(id);
            assertThat(node.path("sha256").asText()).isEqualTo(value.sha256());
            assertThat(node.path("sizeBytes").asLong()).isEqualTo(value.sizeBytes());
            assertThat(node.path("retrievedAt").asText()).isEqualTo("2026-08-03");
            assertThat(node.path("learnerVisible").asBoolean()).isFalse();
            var matcher = LOGICAL_KEY.matcher(node.path("logicalKey").asText());
            assertThat(matcher.matches()).as(id + " logical key").isTrue();
            assertThat(node.path("logicalKey").asText())
                    .contains("/" + value.sha256() + ".");
        });
        assertThat(actual.get("topik35-listening-writing-pdf")
                .path("sourceUrls")).hasSize(2);
        assertThat(actual.get("topik35-youtube-auto-caption")
                .path("provenanceStatus").asText())
                .isEqualTo("NON_AUTHORITATIVE_AUTO_CAPTION_QA_ONLY");
    }

    @Test
    void listeningIsOneContinuousProgramWithoutTimestampAssistance() throws Exception {
        JsonNode listening = manifest().path("listening");
        JsonNode policy = listening.path("examPlaybackPolicy");

        assertThat(listening.path("singleOrderedAudioProgram").asBoolean()).isTrue();
        assertThat(listening.path("audioLogicalKey").asText())
                .matches("^practice-seed/topik35-v1/derived/audio-mp3/.+\\.mp3$");
        assertThat(policy.path("startOnce").asBoolean()).isTrue();
        assertThat(policy.path("continuousPlayback").asBoolean()).isTrue();
        assertThat(policy.path("seekAllowed").asBoolean()).isFalse();
        assertThat(policy.path("replayAllowed").asBoolean()).isFalse();
        assertThat(policy.path("learnerControlsQuestionNavigation").asBoolean()).isTrue();
        assertThat(policy.path("timestampAutoNavigation").asBoolean()).isFalse();
        assertThat(policy.path("timestampAutoHighlight").asBoolean()).isFalse();
        assertThat(policy.path("timestampDrivenAssistance").asBoolean()).isFalse();
        assertThat(listening.path("timingAuthority").path("examModeUse").asText())
                .isEqualTo("FORBIDDEN");
        assertThat(listening.path("timingAuthority").path("captionTimestampUse")
                .asText()).isEqualTo("PROHIBITED_FOR_TIMESTAMP_INFERENCE");

        Set<Integer> questions = new HashSet<>();
        int expectedFrom = 1;
        assertThat(listening.path("groups")).hasSize(20);
        for (JsonNode group : listening.path("groups")) {
            int from = group.path("questionFrom").asInt();
            int to = group.path("questionTo").asInt();
            assertThat(from).isEqualTo(expectedFrom);
            assertThat(to).isGreaterThanOrEqualTo(from);
            for (int question = from; question <= to; question++) {
                assertThat(questions.add(question)).isTrue();
            }
            expectedFrom = to + 1;
            assertThat(group.path("startMs").isNull()).isTrue();
            assertThat(group.path("endMs").isNull()).isTrue();
            assertThat(group.path("timingStatus").asText())
                    .isEqualTo("PENDING_MANUAL_QA");
        }
        assertThat(questions).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList());
        assertThat(expectedFrom).isEqualTo(51);
    }

    @Test
    void manifestContainsNoLocalPathBucketOrDeliveryUrl() throws Exception {
        JsonNode root = manifest();
        String raw = Files.readString(MANIFEST);

        assertThat(root.path("storageContract").path("port").asText())
                .isEqualTo("AssetStorageService");
        assertThat(root.path("storageContract").path("currentBackend").asText())
                .isEqualTo("LOCAL");
        assertThat(root.path("storageContract").path("deliveryResolvedServerSide")
                .asBoolean()).isTrue();
        assertThat(root.path("storageContract").path("legacyAudioUrlColumnsLoaded")
                .asBoolean()).isFalse();
        assertThat(raw).doesNotContain("/Users/", "/tmp/", "file://", "s3://", "r2://",
                ".r2.cloudflarestorage.com", "audio_url", "audioPath");
        root.path("artifacts").forEach(node ->
                assertThat(node.path("logicalKey").asText()).matches(LOGICAL_KEY));
    }

    @Test
    void postTestReviewIsSeparateAndContentLoadRemainsBlocked() throws Exception {
        JsonNode root = manifest();
        JsonNode review = root.path("postTestReview");

        assertThat(review.path("enabledByThisBundle").asBoolean()).isFalse();
        assertThat(review.path("targetLayout").path("leftPane").asText())
                .isEqualTo("TRANSCRIPT");
        assertThat(review.path("targetLayout").path("rightPane").asText())
                .isEqualTo("EXPLANATION");
        assertThat(review.path("testModePlaybackPolicyUnchanged").asBoolean()).isTrue();
        assertThat(root.path("loadPolicy").path("sharedDatabaseAllowed").asBoolean())
                .isFalse();
        assertThat(root.path("loadPolicy").path("productionDatabaseAllowed").asBoolean())
                .isFalse();
        assertThat(root.path("loadPolicy").path("flywayMigration").asBoolean())
                .isFalse();
        assertThat(root.path("contentScope").path("questionBodiesSeeded").asBoolean())
                .isFalse();
    }

    @Test
    void schemaIsVersionPinnedAndClosed() throws Exception {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA));

        assertThat(schema.path("properties").path("schemaVersion")
                .path("const").asText())
                .isEqualTo("practice-topik35-canonical-seed-bundle-v1");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("artifact")
                .path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("listeningGroup")
                .path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("examPlaybackPolicy")
                .path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("timingAuthority")
                .path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("postTestReview")
                .path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("contentScope")
                .path("additionalProperties").asBoolean()).isFalse();
    }

    private JsonNode manifest() throws Exception {
        return objectMapper.readTree(Files.readString(MANIFEST));
    }

    private static ExpectedArtifact artifact(String sha256, long sizeBytes) {
        return new ExpectedArtifact(sha256, sizeBytes);
    }

    private record ExpectedArtifact(String sha256, long sizeBytes) {
    }
}
