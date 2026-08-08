package com.ksh.features.practice.ai.speaking.readiness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the explicit separation between a portfolio demo and production release. */
class DirectAudioReleaseEvidenceManifestTest {
    private static final Path MANIFEST = Path.of("docs/operations/"
            + "practice-speaking-direct-audio-release-evidence-manifest.json");
    private static final Path SCHEMA = Path.of("docs/operations/"
            + "practice-speaking-direct-audio-release-evidence-manifest.schema.json");
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
        root.path("providerEvidence").path("evidence").forEach(node -> {
            states.add(node.path("state").asText());
            assertThat(node.path("artifactId").isNull()).isTrue();
            assertThat(node.path("reviewDecisionId").isNull()).isTrue();
        });
        assertThat(states).contains("DEFERRED_FOR_PRODUCTION",
                "NOT_REQUIRED_FOR_EXPERIMENTAL_DEMO");
        root.path("calibration").path("evidence").forEach(node ->
                assertThat(node.path("state").asText())
                        .isEqualTo("AVAILABLE_EXPERIMENTAL_EVIDENCE"));
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
}
