package com.ksh.features.practice.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioDisclosureArtifactTest {
    private static final Path JSON = Path.of(
            "docs/policies/ksh-speaking-direct-audio-disclosure-v1.json");
    private static final Path DISCLOSURE = Path.of(
            "docs/policies/KSH_SPEAKING_DIRECT_AUDIO_DISCLOSURE_V1.md");

    @Test
    void artifactPinsPurposePolicyLimitsAndFailClosedScoreRelease() throws Exception {
        JsonNode artifact = new ObjectMapper().readTree(Files.readString(JSON));

        assertThat(artifact.path("artifact_id").asText())
                .isEqualTo("KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1");
        assertThat(artifact.path("purpose").asText())
                .isEqualTo(DirectAudioSpeakingEvaluationService.PURPOSE);
        assertThat(artifact.path("policy_bundle_id").asText())
                .isEqualTo(DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID);
        assertThat(artifact.at("/consent/default_checked").asBoolean()).isFalse();
        assertThat(artifact.at("/consent/withdrawal_supported").asBoolean()).isTrue();
        assertThat(artifact.at("/limits/audio_retention_ceiling").asText())
                .isEqualTo("P30D");
        assertThat(artifact.at("/limits/provider_deletion_sla_ceiling").asText())
                .isEqualTo("P7D");
        assertThat(artifact.path("score_release").asText())
                .isEqualTo("BLOCKED_UNTIL_AUTHORIZED_CONSUMPTION_AND_READINESS_GREEN");
        assertThat(artifact.path("grant_manager_authorities").toString())
                .isEqualTo("[\"ACADEMIC_LEADER\",\"PRIVACY_RELEASE_OWNER\"]");
    }

    @Test
    void learnerCopyContainsRequiredDisclosureAndNoInventedProvider() throws Exception {
        String text = Files.readString(DISCLOSURE);
        String normalized = text.replaceAll("\\s+", " ");

        assertThat(normalized)
                .contains("Việc đồng ý là tự nguyện")
                .contains("không sử dụng bản ghi âm để huấn luyện mô hình")
                .contains("Bạn có thể rút lại sự đồng ý")
                .contains("đang xóa")
                .contains("mặc định bỏ chọn")
                .contains("30 days", "7 calendar days")
                .contains("PRIVACY_RELEASE_OWNER")
                .doesNotContain("OpenAI", "Cloudflare", "AWS", "Seoul");
    }
}
