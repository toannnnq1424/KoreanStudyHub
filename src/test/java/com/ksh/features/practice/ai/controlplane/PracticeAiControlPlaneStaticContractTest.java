package com.ksh.features.practice.ai.controlplane;

import com.ksh.features.admin.settings.controller.PracticeAiControlPlaneController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiControlPlaneStaticContractTest {

    @Test
    void migrationUsesOnePrimaryKeyRowPerExactPurpose() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V84__practice_ai_control_plane.sql"));
        assertThat(sql)
                .contains("purpose_code VARCHAR(64) PRIMARY KEY")
                .contains("practice_ai_provider_profiles")
                .contains("practice_ai_purpose_bindings")
                .contains("practice_ai_capability_test_runs")
                .contains("practice_ai_execution_audits");
        for (PracticeAiPurpose purpose : PracticeAiPurpose.values()) {
            assertThat(sql).contains("'" + purpose.name() + "'");
        }
        assertThat(sql).doesNotContain(
                "GENERAL_UPLOADS",
                "storage_profiles",
                "R2",
                "DROP TABLE",
                "DELETE FROM");
    }

    @Test
    void adminControllerRetainsSystemAiPermissionGate() {
        PreAuthorize authorization = PracticeAiControlPlaneController.class
                .getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
                .isEqualTo("hasAuthority('PERM_system.ai')");
    }

    @Test
    void practiceSourceDoesNotImportSharedAiClientOrGlobalProviderRepository()
            throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java/com/ksh/features/practice"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertThat(source)
                        .as(path.toString())
                        .doesNotContain(
                                "com.ksh.features.ai.client.AiClient",
                                "com.ksh.features.admin.settings.repository.AiProviderRepository",
                                "findEnabledOrdered(");
            }
        }
        String learnerStt = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/transcription/"
                        + "OpenAiSpeakingTranscriptionClient.java"));
        String promptStt = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "OpenAiSpeakingPromptSttAdapter.java"));
        String promptTts = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "OpenAiSpeakingPromptTtsAdapter.java"));
        assertThat(learnerStt + promptStt + promptTts)
                .doesNotContain("OpenAiAudioHttpTransport", "RestClient.builder");
    }

    @Test
    void providerTransportAllowsOnlyPracticeOwnedPurposeEndpoints()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/controlplane/"
                        + "RestClientPracticeAiProviderTransport.java"));
        assertThat(source)
                .contains("/chat/completions")
                .contains("/audio/transcriptions")
                .contains("/audio/speech")
                .doesNotContain("fallback", "AiClient", "ai_providers");
    }

    @Test
    void legacyEnvironmentCapabilityAuthorityIsNotConfigured() throws Exception {
        String properties = Files.readString(Path.of(
                "src/main/resources/application.properties"));
        assertThat(properties)
                .doesNotContain("app.practice.ai.openai-primary")
                .contains("authority comes only from PRACTICE_SPEAKING_EVALUATION")
                .contains("PRACTICE_SPEAKING_STT");
        assertThat(Files.exists(Path.of(
                "src/main/java/com/ksh/features/practice/ai/transport/"
                        + "OpenAiPrimaryStructuredGenerationAdapter.java")))
                .isFalse();
    }
}
