package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.ai.readinglistening.ExplanationArtifactInput;
import com.ksh.features.practice.ai.readinglistening.ReadingListeningExplanationClient;
import com.ksh.features.practice.ai.speaking.SpeakingAssessmentPolicyBundle;
import com.ksh.features.practice.ai.writing.WritingAssessmentPolicyBundle;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticePre15CanonicalUatSeedManifestTest {

    private static final Path MANIFEST = Path.of(
            "docs/operations/practice-pre15-canonical-uat-seed-manifest.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-pre15-canonical-uat-seed-manifest.schema.json");
    private static final Set<String> LOCKS = Set.of(
            "SET_VERSION", "TEST_VERSION", "SECTION_VERSION",
            "GROUP_VERSION", "QUESTION_VERSION");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void skeletonIsDeterministicCanonicalAndFailClosed() throws Exception {
        JsonNode root = objectMapper.readTree(Files.readString(MANIFEST));

        validate(root);

        assertThat(root.path("status").asText())
                .isEqualTo("BLOCKED_SME_REQUIRED");
        assertThat(root.path("sourceAuthority").asText())
                .isEqualTo("REPO_MANIFEST_ONLY_DO_NOT_LOAD");
        assertThat(root.path("blockers")).hasSize(3);
        assertThat(root.path("blockers").toString())
                .doesNotContain("APPROVED", "CALIBRATED", "PASSED");
    }

    @Test
    void policyAndExplanationIdentitiesMatchCurrentSource() throws Exception {
        JsonNode contracts = objectMapper.readTree(
                Files.readString(MANIFEST)).path("policyContracts");

        assertThat(contracts.path("readingListening")
                .path("inputSchema").asText())
                .isEqualTo(ExplanationArtifactInput.SCHEMA_VERSION);
        assertThat(contracts.path("readingListening")
                .path("explanationSchema").asText())
                .isEqualTo(ReadingListeningExplanationClient
                        .EXPLANATION_SCHEMA_VERSION);
        assertThat(contracts.path("writing").path("policyBundleId").asText())
                .isEqualTo(WritingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(contracts.path("speaking").path("policyBundleId").asText())
                .isEqualTo(SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID);
        assertThat(contracts.path("speaking").path("capability").asText())
                .isEqualTo("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION");
        assertThat(contracts.path("speaking").path("evidenceMode").asText())
                .isEqualTo("TRANSCRIPT_ONLY");
        assertThat(contracts.path("speaking").path("holisticScore").asBoolean())
                .isFalse();
    }

    @Test
    void validatorRejectsUngroupedUnlockedOrFalselyReadySkeleton()
            throws Exception {
        ObjectNode ungrouped = (ObjectNode) objectMapper.readTree(
                Files.readString(MANIFEST));
        ((ObjectNode) ungrouped.path("skills").get(0)
                .path("questions").get(0))
                .put("grouped", false);
        assertThatThrownBy(() -> validate(ungrouped))
                .hasMessageContaining("grouped");

        ObjectNode unlocked = (ObjectNode) objectMapper.readTree(
                Files.readString(MANIFEST));
        ((ObjectNode) unlocked.path("skills").get(0))
                .withArray("versionLocks").removeAll().add("QUESTION_VERSION");
        assertThatThrownBy(() -> validate(unlocked))
                .hasMessageContaining("version locks");

        ObjectNode falselyReady = (ObjectNode) objectMapper.readTree(
                Files.readString(MANIFEST));
        falselyReady.put("status", "READY_FOR_LOAD");
        assertThatThrownBy(() -> validate(falselyReady))
                .hasMessageContaining("SME blockers");
    }

    @Test
    void schemaDocumentIsPinnedAndClosed() throws Exception {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA));

        assertThat(schema.path("$schema").asText())
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.path("properties").path("schemaVersion")
                .path("const").asText())
                .isEqualTo("practice-pre15-canonical-uat-seed-manifest-v1");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").path("question")
                .path("additionalProperties").asBoolean()).isFalse();
    }

    private static void validate(JsonNode root) {
        require("practice-pre15-canonical-uat-seed-manifest-v1".equals(
                root.path("schemaVersion").asText()), "schema version");
        require("REPO_MANIFEST_ONLY_DO_NOT_LOAD".equals(
                root.path("sourceAuthority").asText()), "source authority");
        require(root.path("skills").isArray()
                && root.path("skills").size() == 4, "exact four skills");

        Set<String> skills = new HashSet<>();
        Set<String> seedKeys = new HashSet<>();
        for (JsonNode skill : root.path("skills")) {
            skills.add(skill.path("skill").asText());
            Set<String> locks = new HashSet<>();
            skill.path("versionLocks").forEach(lock -> locks.add(lock.asText()));
            require(locks.equals(LOCKS), "complete version locks");
            require(!skill.path("groupSeedKey").asText().isBlank(),
                    "stable group seed key");
            for (JsonNode question : skill.path("questions")) {
                require(question.path("grouped").asBoolean(),
                        "every question must be grouped");
                require(question.path("immutableVersionLockRequired")
                        .asBoolean(), "question version lock");
                require(seedKeys.add(question.path("seedKey").asText()),
                        "unique seed key");
                require(List.of("SINGLE_CHOICE", "FILL_BLANK",
                                "TRUE_FALSE_NOT_GIVEN", "ESSAY", "SPEAKING")
                        .contains(question.path("questionType").asText()),
                        "canonical question type");
            }
        }
        require(skills.equals(Set.of(
                "READING", "LISTENING", "WRITING", "SPEAKING")),
                "R/L/W/S coverage");

        JsonNode writing = findSkill(root, "WRITING");
        assertWriting(writing, "Q51", 10);
        assertWriting(writing, "Q52", 10);
        assertWriting(writing, "Q53", 30);
        assertWriting(writing, "Q54", 50);

        boolean smeBlockers = root.path("blockers").isArray()
                && !root.path("blockers").isEmpty();
        if ("READY_FOR_LOAD".equals(root.path("status").asText())) {
            require(!smeBlockers, "READY_FOR_LOAD cannot retain SME blockers");
        } else {
            require("BLOCKED_SME_REQUIRED".equals(
                    root.path("status").asText()), "fail-closed status");
            require(smeBlockers, "blocked skeleton requires SME blockers");
        }
    }

    private static JsonNode findSkill(JsonNode root, String expected) {
        for (JsonNode skill : root.path("skills")) {
            if (expected.equals(skill.path("skill").asText())) return skill;
        }
        throw new IllegalArgumentException("Missing skill " + expected);
    }

    private static void assertWriting(
            JsonNode writing, String task, int points) {
        for (JsonNode question : writing.path("questions")) {
            if (task.equals(question.path("writingTask").asText())) {
                require("ESSAY".equals(question.path("questionType").asText()),
                        task + " must be ESSAY");
                require(question.path("points").asInt() == points,
                        task + " points");
                return;
            }
        }
        throw new IllegalArgumentException("Missing Writing task " + task);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
