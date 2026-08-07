package com.ksh.features.practice.ai.speaking.readiness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.ai.controlplane.PracticeDirectAudioCapabilityRegistry;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;
import com.ksh.features.practice.ai.speaking.acoustic.DirectAudioAcousticResponseNormalizer;
import com.ksh.features.practice.ai.speaking.alignment.KoreanDirectAudioAlignmentNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectAudioReleaseEvidenceManifestTest {

    private static final Path MANIFEST = Path.of(
            "docs/operations/"
                    + "practice-speaking-direct-audio-release-evidence-manifest.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/"
                    + "practice-speaking-direct-audio-release-evidence-manifest.schema.json");
    private static final Path REVIEW_DECISIONS = Path.of(
            "docs/evidence/practice-speaking-direct-audio/"
                    + "speaking-scoring-review-decisions-2026-08-07.json");
    private static final String EVIDENCE_PREFIX =
            "docs/evidence/practice-speaking-direct-audio/";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "manifestId", "status", "approvalScope",
            "contracts", "forcedAlignment", "providerEvidence", "calibration",
            "releaseGate", "blockers");
    private static final Set<String> REQUIRED_EVIDENCE = Set.of(
            "ALIGNER_CAPABILITY_CAPTURE",
            "KOREAN_TIMESTAMP_SAMPLE_REPORT",
            "REGION_POLICY",
            "NON_TRAINING_POLICY",
            "RETENTION_POLICY",
            "DELETION_SLA_POLICY",
            "REDACTED_CAPTURED_REQUEST",
            "REDACTED_CAPTURED_RESPONSE_RECEIPT",
            "CORPUS_MANIFEST_REPORT",
            "ACOUSTIC_CALIBRATION_REPORT",
            "FAIRNESS_REVIEW_REPORT",
            "REPEATABILITY_REPORT");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void repositoryIntakeHasSixPendingSixMissingAndRemainsNonScoreBearing() throws Exception {
        JsonNode root = objectMapper.readTree(Files.readString(MANIFEST));

        Validation validation = validate(root, Path.of("."));

        assertThat(validation.missingKinds()).containsExactlyInAnyOrder(
                "REGION_POLICY", "NON_TRAINING_POLICY", "RETENTION_POLICY",
                "DELETION_SLA_POLICY", "REDACTED_CAPTURED_REQUEST",
                "REDACTED_CAPTURED_RESPONSE_RECEIPT");
        assertThat(validation.pendingKinds()).containsExactlyInAnyOrder(
                "ALIGNER_CAPABILITY_CAPTURE", "KOREAN_TIMESTAMP_SAMPLE_REPORT",
                "CORPUS_MANIFEST_REPORT", "ACOUSTIC_CALIBRATION_REPORT",
                "FAIRNESS_REVIEW_REPORT", "REPEATABILITY_REPORT");
        assertThat(root.path("status").asText())
                .isEqualTo("BLOCKED_EXTERNAL_EVIDENCE");
        assertThat(root.path("approvalScope").asText())
                .isEqualTo("AUTHORIZED_TO_COLLECT_AND_REVIEW_NOT_RESULT_ACCEPTANCE");
        assertThat(root.path("releaseGate").path("darkObservationOnly").asBoolean())
                .isTrue();
        assertThat(root.path("releaseGate").path("learnerVisible").asBoolean())
                .isFalse();
        assertThat(root.path("releaseGate").path("scoreReleaseEligible").asBoolean())
                .isFalse();
        assertThat(root.toString()).doesNotContain(
                "TEST-", "FAKE-", "PLACEHOLDER", "SCORE_RELEASE_READY");
    }

    @Test
    void contractIdentitiesMatchCurrentProductionSource() throws Exception {
        JsonNode contracts = objectMapper.readTree(Files.readString(MANIFEST))
                .path("contracts");

        assertThat(contracts.path("purpose").asText())
                .isEqualTo(DirectAudioSpeakingEvaluationService.PURPOSE);
        assertThat(contracts.path("policyBundleId").asText())
                .isEqualTo(DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID);
        assertThat(contracts.path("acousticContractVersion").asText())
                .isEqualTo(DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION);
        assertThat(contracts.path("alignmentContractVersion").asText())
                .isEqualTo(KoreanDirectAudioAlignmentNormalizer.CONTRACT_VERSION);
        assertThat(contracts.path("capabilityRegistryArtifactId").asText())
                .isEqualTo(PracticeDirectAudioCapabilityRegistry.REGISTRY_ARTIFACT_ID);
    }

    @Test
    void suppliedLocalEvidenceHasIndependentDigestBoundReviewFeedback()
            throws Exception {
        JsonNode manifest = objectMapper.readTree(Files.readString(MANIFEST));
        JsonNode decisions = objectMapper.readTree(
                Files.readString(REVIEW_DECISIONS)).path("decisions");

        assertThat(decisions).hasSize(6);
        Set<String> reviewedKinds = new HashSet<>();
        decisions.forEach(decision -> {
            String kind = decision.path("artifact").asText();
            assertThat(decision.path("decision").asText()).isEqualTo(
                    "ALIGNER_CAPABILITY_CAPTURE".equals(kind)
                            ? "PENDING" : "CHANGES_REQUIRED");
            assertThat(decision.has("reviewDecisionId")).isFalse();
            assertThat(decision.path("reviewer").isNull()).isTrue();
            reviewedKinds.add(kind);

            ObjectNode reference = references(manifest).stream()
                    .filter(candidate -> candidate.path("kind").asText()
                            .equals(decision.path("artifact").asText()))
                    .findFirst()
                    .orElseThrow();
            assertThat(reference.path("state").asText())
                    .isEqualTo("SUPPLIED_REVIEW_PENDING");
            assertThat(decision.path("artifactId").asText())
                    .isEqualTo(reference.path("artifactId").asText());
            assertThat(decision.path("artifactSha256").asText())
                    .isEqualTo(reference.path("sha256").asText());
        });
        assertThat(reviewedKinds).containsExactlyInAnyOrder(
                "ALIGNER_CAPABILITY_CAPTURE", "KOREAN_TIMESTAMP_SAMPLE_REPORT",
                "CORPUS_MANIFEST_REPORT", "ACOUSTIC_CALIBRATION_REPORT",
                "FAIRNESS_REVIEW_REPORT", "REPEATABILITY_REPORT");
    }

    @Test
    void schemaIsClosedAndCannotRepresentLearnerScoreRelease() throws Exception {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA));
        String source = Files.readString(SCHEMA);

        assertAllObjectsClosed(schema, "$root");
        assertThat(schema.path("properties").path("status").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("BLOCKED_EXTERNAL_EVIDENCE", "DARK_VALIDATION_READY");
        assertThat(source)
                .contains("EOJJEOL_OR_WORD_TIMESTAMPS")
                .contains("SYLLABLE_JAMO_PHONEME_REQUIRE_SEPARATE_CAPTURED_EVIDENCE")
                .contains("EVIDENCE_INTAKE_CANNOT_AUTHORIZE_SCORE_RELEASE")
                .doesNotContain("SCORE_RELEASE_READY", "holisticScore", "attemptPoints");
    }

    @Test
    void darkReadyRequiresAcceptedHashBoundFilesAndExactSelections(
            @TempDir Path repositoryRoot) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(
                Files.readString(MANIFEST));
        makeAcceptedDarkManifest(root, repositoryRoot);

        Validation validation = validate(root, repositoryRoot);

        assertThat(validation.missingKinds()).isEmpty();
        assertThat(validation.acceptedKinds())
                .containsExactlyInAnyOrderElementsOf(REQUIRED_EVIDENCE);
    }

    @Test
    void readyStateRejectsMissingEvidenceFakeIdentityAndDigestTampering(
            @TempDir Path repositoryRoot) throws Exception {
        ObjectNode missing = (ObjectNode) objectMapper.readTree(
                Files.readString(MANIFEST));
        missing.put("status", "DARK_VALIDATION_READY");
        missing.withArray("blockers").removeAll();
        for (ObjectNode evidence : references(missing)) {
            evidence.put("state", "MISSING");
            evidence.putNull("artifactId");
            evidence.putNull("relativePath");
            evidence.putNull("sha256");
            evidence.putNull("reviewDecisionId");
        }
        assertThatThrownBy(() -> validate(missing, repositoryRoot))
                .hasMessageContaining("accepted evidence");

        ObjectNode accepted = (ObjectNode) objectMapper.readTree(
                Files.readString(MANIFEST));
        makeAcceptedDarkManifest(accepted, repositoryRoot);
        ObjectNode first = references(accepted).get(0);
        first.put("artifactId", "FAKE-ALIGNER-EVIDENCE");
        assertThatThrownBy(() -> validate(accepted, repositoryRoot))
                .hasMessageContaining("artifact ID");

        makeAcceptedDarkManifest(accepted, repositoryRoot);
        references(accepted).get(0).put("sha256", "0".repeat(64));
        assertThatThrownBy(() -> validate(accepted, repositoryRoot))
                .hasMessageContaining("digest");
    }

    private void makeAcceptedDarkManifest(
            ObjectNode root, Path repositoryRoot) throws IOException {
        root.put("status", "DARK_VALIDATION_READY");
        root.withArray("blockers").removeAll();

        ObjectNode aligner = (ObjectNode) root.path("forcedAlignment");
        aligner.put("selectionStatus", "ACCEPTED");
        aligner.put("componentType", "ASR_WORD_TIMESTAMPS");
        aligner.put("provider", "CAPTURED_COMPONENT_PROVIDER");
        aligner.put("model", "CAPTURED_COMPONENT_MODEL");
        aligner.put("version", "2026.08");

        ObjectNode provider = (ObjectNode) root.path("providerEvidence");
        provider.put("selectionStatus", "ACCEPTED");
        provider.put("profileCode", PracticeDirectAudioCapabilityRegistry
                .GEMINI_DEVELOPER_CODE);
        provider.put("baseUrl", PracticeDirectAudioCapabilityRegistry
                .GEMINI_DEVELOPER_BASE_URL);
        provider.put("model", PracticeDirectAudioCapabilityRegistry
                .GEMINI_DEVELOPER_MODEL);
        provider.put("credentialMode", "STATIC_BEARER");

        ObjectNode calibration = (ObjectNode) root.path("calibration");
        calibration.put("profileId", "KSH-KO-ACOUSTIC-PROFILE-DIGEST");
        calibration.put("version", "2026.08");

        for (ObjectNode evidence : references(root)) {
            String kind = evidence.path("kind").asText();
            String relativePath = EVIDENCE_PREFIX
                    + kind.toLowerCase(Locale.ROOT) + ".json";
            Path file = repositoryRoot.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\"kind\":\"" + kind + "\"}\n",
                    StandardCharsets.UTF_8);
            String digest = sha256(Files.readAllBytes(file));
            evidence.put("state", "ACCEPTED");
            evidence.put("relativePath", relativePath);
            evidence.put("sha256", digest);
            evidence.put("artifactId", "KSH-DA-EVIDENCE-"
                    + kind.replace('_', '-') + "-20260803-"
                    + digest.substring(0, 12));
            evidence.put("reviewDecisionId", "KSH-DA-REVIEW-20260803-"
                    + digest.substring(0, 12));
        }
    }

    private static Validation validate(JsonNode root, Path repositoryRoot) {
        require(root.isObject(), "root object");
        require(fieldNames(root).equals(ROOT_FIELDS), "exact root fields");
        require("practice-speaking-direct-audio-release-evidence-manifest-v1"
                .equals(root.path("schemaVersion").asText()), "schema version");
        require("KSH-SPEAKING-DIRECT-AUDIO-RELEASE-EVIDENCE-INTAKE-V1"
                .equals(root.path("manifestId").asText()), "manifest ID");
        require("AUTHORIZED_TO_COLLECT_AND_REVIEW_NOT_RESULT_ACCEPTANCE"
                .equals(root.path("approvalScope").asText()), "approval scope");
        require(root.path("releaseGate").path("darkObservationOnly").asBoolean(),
                "dark-only release gate");
        require(!root.path("releaseGate").path("learnerVisible").asBoolean(),
                "learner visibility forbidden");
        require(!root.path("releaseGate").path("scoreReleaseEligible").asBoolean(),
                "score release forbidden");
        require(root.path("calibration").path("numericThresholds").isNull(),
                "numeric thresholds require separate evidence");

        List<ObjectNode> references = references(root);
        Set<String> kinds = new HashSet<>();
        List<String> missing = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        for (ObjectNode evidence : references) {
            String kind = text(evidence, "kind");
            require(kinds.add(kind), "duplicate evidence kind " + kind);
            String state = text(evidence, "state");
            if ("MISSING".equals(state)) {
                require(evidence.path("artifactId").isNull()
                                && evidence.path("relativePath").isNull()
                                && evidence.path("sha256").isNull()
                                && evidence.path("reviewDecisionId").isNull(),
                        "missing evidence must not invent identity " + kind);
                missing.add(kind);
                continue;
            }
            require("SUPPLIED_REVIEW_PENDING".equals(state)
                            || "ACCEPTED".equals(state),
                    "evidence state " + kind);
            String artifactId = text(evidence, "artifactId");
            String relativePath = text(evidence, "relativePath");
            String expectedDigest = text(evidence, "sha256");
            require(!fake(artifactId)
                            && artifactId.matches(
                            "^KSH-DA-EVIDENCE-[A-Z0-9-]+-[0-9]{8}-[0-9a-f]{12}$"),
                    "artifact ID " + kind);
            require(relativePath.startsWith(EVIDENCE_PREFIX)
                            && !Path.of(relativePath).isAbsolute()
                            && !relativePath.contains(".."),
                    "evidence path " + kind);
            Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
            Path file = normalizedRoot.resolve(relativePath).normalize();
            require(file.startsWith(normalizedRoot) && Files.isRegularFile(file),
                    "evidence file " + kind);
            String actualDigest;
            try {
                actualDigest = sha256(Files.readAllBytes(file));
            } catch (IOException exception) {
                throw new IllegalArgumentException("evidence file " + kind, exception);
            }
            require(expectedDigest.equals(actualDigest), "evidence digest " + kind);
            require(artifactId.endsWith(actualDigest.substring(0, 12)),
                    "artifact digest identity " + kind);
            if ("ACCEPTED".equals(state)) {
                String reviewDecisionId = text(evidence, "reviewDecisionId");
                require(!fake(reviewDecisionId), "review decision " + kind);
                accepted.add(kind);
            } else {
                require(evidence.path("reviewDecisionId").isNull(),
                        "pending evidence cannot be accepted " + kind);
                pending.add(kind);
            }
        }
        require(kinds.equals(REQUIRED_EVIDENCE), "exact required evidence set");

        String status = text(root, "status");
        if ("DARK_VALIDATION_READY".equals(status)) {
            require(missing.isEmpty() && accepted.size() == REQUIRED_EVIDENCE.size(),
                    "dark ready requires all accepted evidence");
            require(root.path("blockers").isArray()
                            && root.path("blockers").isEmpty(),
                    "dark ready blockers");
            assertSelection(root.path("forcedAlignment"),
                    "componentType", "provider", "model", "version");
            assertSelection(root.path("providerEvidence"),
                    "profileCode", "baseUrl", "model", "credentialMode");
            require(!blank(root.path("calibration").path("profileId").asText())
                            && !blank(root.path("calibration").path("version").asText()),
                    "calibration identity");
        } else {
            require("BLOCKED_EXTERNAL_EVIDENCE".equals(status),
                    "fail-closed status");
            require(!missing.isEmpty(), "blocked manifest requires missing evidence");
            require(root.path("blockers").isArray()
                            && !root.path("blockers").isEmpty(),
                    "blocked manifest requires blockers");
        }
        return new Validation(
                List.copyOf(missing), List.copyOf(pending), List.copyOf(accepted));
    }

    private static void assertSelection(JsonNode node, String... fields) {
        require("ACCEPTED".equals(node.path("selectionStatus").asText()),
                "accepted selection");
        for (String field : fields) {
            String value = node.path(field).asText();
            require(!blank(value) && !fake(value), "selection " + field);
        }
    }

    private static List<ObjectNode> references(JsonNode root) {
        List<ObjectNode> references = new ArrayList<>();
        for (String section : List.of(
                "forcedAlignment", "providerEvidence", "calibration")) {
            JsonNode evidence = root.path(section).path("evidence");
            require(evidence.isArray(), section + " evidence array");
            evidence.forEach(item -> {
                require(item.isObject(), section + " evidence object");
                references.add((ObjectNode) item);
            });
        }
        return references;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        require(value != null && value.isTextual() && !value.textValue().isBlank(),
                "text " + field);
        return value.textValue();
    }

    private static boolean fake(String value) {
        if (blank(value)) return true;
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("TEST")
                || normalized.contains("FAKE")
                || normalized.contains("PLACEHOLDER")
                || normalized.contains("TBD");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertAllObjectsClosed(JsonNode node, String path) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) {
                assertThat(node.path("additionalProperties").asBoolean())
                        .as(path)
                        .isFalse();
            }
            node.fields().forEachRemaining(entry -> assertAllObjectsClosed(
                    entry.getValue(), path + "/" + entry.getKey()));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertAllObjectsClosed(node.get(index), path + "/" + index);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Validation(
            List<String> missingKinds,
            List<String> pendingKinds,
            List<String> acceptedKinds) {
    }
}
