package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioReviewerAccessAuditPolicyArtifactTest {
    private static final String ID =
            "KSH-SPEAKING-DIRECT-AUDIO-REVIEWER-ACCESS-AUDIT-RETENTION-V1";

    @Test
    void approvedArtifactPinsP90dMetadataOnlyAndNoScoreOrNetworkIdentifiers()
            throws Exception {
        JsonNode policy = new ObjectMapper().readTree(Files.readString(Path.of(
                "docs/policies/"
                        + "ksh-speaking-direct-audio-reviewer-access-audit-retention-v1.json")));
        String properties = Files.readString(Path.of(
                "src/main/resources/application.properties"));

        assertThat(policy.path("artifact_id").asText()).isEqualTo(ID);
        assertThat(policy.path("status").asText())
                .isEqualTo("APPROVED_PREPRODUCTION_PRODUCT_PRIVACY_BASELINE");
        assertThat(policy.path("purpose").asText())
                .isEqualTo(DirectAudioSpeakingEvaluationService.PURPOSE);
        assertThat(policy.path("retention").asText()).isEqualTo("P90D");
        assertThat(policy.path("denied_probe_collection").asText())
                .isEqualTo("NOT_COLLECTED");
        assertThat(policy.at("/purge/maximum_batch_size").asInt()).isEqualTo(1_000);
        assertThat(policy.path("forbidden_fields").toString())
                .contains("audio_bytes", "storage_key", "provider_payload",
                        "score_value", "ip_address", "user_agent");
        assertThat(properties).contains(
                "RETENTION_POLICY_ID:" + ID,
                "REVIEWER_ACCESS_AUDIT_RETENTION:P90D",
                "RETENTION_WORKER_ENABLED:false");
    }
}
