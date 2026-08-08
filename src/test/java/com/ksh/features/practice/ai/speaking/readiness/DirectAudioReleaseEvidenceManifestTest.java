package com.ksh.features.practice.ai.speaking.readiness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the explicit separation between a portfolio demo and production release. */
class DirectAudioReleaseEvidenceManifestTest {
    private static final Path MANIFEST = Path.of("docs/operations/"
            + "practice-speaking-direct-audio-release-evidence-manifest.json");
    private static final Path SCHEMA = Path.of("docs/operations/"
            + "practice-speaking-direct-audio-release-evidence-manifest.schema.json");
    private static final Path REVIEW_DECISIONS = Path.of("docs/evidence/"
            + "practice-speaking-direct-audio/"
            + "speaking-scoring-review-decisions-2026-08-07.json");
    private static final Path REPEATABILITY_REPORT = Path.of("docs/evidence/"
            + "practice-speaking-direct-audio/repeatability-report.json");
    private static final Set<String> PROVIDER_EVIDENCE_KINDS = Set.of(
            "NON_TRAINING_POLICY", "RETENTION_POLICY",
            "REDACTED_CAPTURED_REQUEST", "REDACTED_CAPTURED_RESPONSE_RECEIPT");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void experimentalDemoDoesNotPretendToBeProductionReady() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(MANIFEST));
        assertThat(root.path("releaseScope").asText()).isEqualTo("EXPERIMENTAL_DEMO");
        assertThat(root.path("status").asText())
                .isEqualTo("EXPERIMENTAL_DEMO_CONFIGURATION_REQUIRED");
        assertThat(root.path("productionReadiness").asText())
                .isEqualTo("PRODUCTION_VALIDATION_REQUIRED");
        assertThat(root.path("releaseGate").path("learnerVisible").asBoolean()).isTrue();
        assertThat(root.path("releaseGate").path("scoreReleaseEligible").asBoolean()).isFalse();
        assertThat(root.path("releaseGate").path("reason").asText())
                .contains("EXPERIMENTAL_FEEDBACK_ONLY");
    }

    @Test
    void policyAndCalibrationEvidenceAreDeferredNotFabricated() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(MANIFEST));
        Set<String> states = new HashSet<>();
        Set<String> kinds = new HashSet<>();
        root.path("providerEvidence").path("evidence").forEach(node -> {
            states.add(node.path("state").asText());
            kinds.add(node.path("kind").asText());
            assertThat(node.path("artifactId").isNull()).isTrue();
            assertThat(node.path("reviewDecisionId").isNull()).isTrue();
        });
        assertThat(kinds).containsExactlyInAnyOrderElementsOf(PROVIDER_EVIDENCE_KINDS);
        assertThat(states).contains("DEFERRED_FOR_PRODUCTION",
                "NOT_REQUIRED_FOR_EXPERIMENTAL_DEMO");
        root.path("calibration").path("evidence").forEach(node ->
                assertThat(node.path("state").asText())
                        .isEqualTo("AVAILABLE_EXPERIMENTAL_EVIDENCE"));
    }

    @Test
    void experimentalEvidenceStaysDigestBoundAndRepeatabilityChangesRequired()
            throws Exception {
        JsonNode manifest = mapper.readTree(Files.readString(MANIFEST));
        List<JsonNode> localEvidence = new ArrayList<>();
        manifest.path("forcedAlignment").path("evidence")
                .forEach(localEvidence::add);
        manifest.path("calibration").path("evidence")
                .forEach(localEvidence::add);

        assertThat(localEvidence).hasSize(6);
        for (JsonNode evidence : localEvidence) {
            assertThat(evidence.path("state").asText())
                    .isEqualTo("AVAILABLE_EXPERIMENTAL_EVIDENCE");
            assertThat(evidence.path("reviewDecisionId").isNull()).isTrue();
            Path artifact = Path.of(evidence.path("relativePath").asText());
            assertThat(artifact).exists();
            assertThat(sha256(artifact)).isEqualTo(evidence.path("sha256").asText());
        }

        JsonNode repeatability = mapper.readTree(Files.readString(REPEATABILITY_REPORT));
        assertThat(repeatability.path("status").asText())
                .isEqualTo("TECHNICAL_CAPTURE_COMPLETE_CHANGES_REQUIRED");
        assertThat(repeatability.path("acceptance").path("decision").asText())
                .isEqualTo("CHANGES_REQUIRED");
        assertThat(repeatability.path("acceptance")
                .path("automaticAcceptanceAllowed").asBoolean()).isFalse();
        assertThat(repeatability.path("acceptance").path("reviewDecisionId").isNull())
                .isTrue();
        assertThat(repeatability.path("acceptance").path("gates")
                .path("wordLabelSequenceIdentity100Percent").asText()).isEqualTo("FAIL");
        assertThat(repeatability.path("acceptance").path("gates")
                .path("maxWordBoundaryDeltaMillisecondsMaximum100").asText())
                .isEqualTo("FAIL");

        JsonNode review = mapper.readTree(Files.readString(REVIEW_DECISIONS))
                .path("decisions");
        JsonNode repeatabilityDecision = null;
        for (JsonNode decision : review) {
            if ("REPEATABILITY_REPORT".equals(decision.path("artifact").asText())) {
                repeatabilityDecision = decision;
                break;
            }
        }
        assertThat(repeatabilityDecision).isNotNull();
        assertThat(repeatabilityDecision.path("decision").asText())
                .isEqualTo("CHANGES_REQUIRED");
        assertThat(repeatabilityDecision.has("reviewDecisionId")).isFalse();
        assertThat(repeatabilityDecision.path("reviewer").isNull()).isTrue();
    }

    @Test
    void schemaRepresentsDemoAndProductionAsDistinctStates() throws Exception {
        JsonNode schema = mapper.readTree(Files.readString(SCHEMA));
        assertThat(schema.path("properties").path("status").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("EXPERIMENTAL_DEMO_CONFIGURATION_REQUIRED",
                        "EXPERIMENTAL_DEMO_READY", "PRODUCTION_VALIDATION_REQUIRED");
        assertThat(schema.path("properties").path("releaseScope").path("const").asText())
                .isEqualTo("EXPERIMENTAL_DEMO");
        assertThat(schema.path("properties").path("productionReadiness").path("const").asText())
                .isEqualTo("PRODUCTION_VALIDATION_REQUIRED");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
