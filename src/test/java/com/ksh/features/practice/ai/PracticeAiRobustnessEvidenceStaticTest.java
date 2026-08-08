package com.ksh.features.practice.ai;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiRobustnessEvidenceStaticTest {

    @Test
    void datedMatrixPinsActualSchemaTransportParsersAndOpenRetryGap()
            throws Exception {
        String evidence = Files.readString(Path.of(
                "docs/evidence/"
                        + "practice-ai-contract-robustness-and-korean-alignment-2026-08-03.md"));
        String transport = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/transport/"
                        + "PracticeControlPlaneStructuredGenerationAdapter.java"));
        String decoder = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/transport/"
                        + "StrictOpenAiStructuredResponseDecoder.java"));

        assertThat(evidence)
                .contains("PRACTICE_WRITING_EVALUATION")
                .contains("PRACTICE_RL_EXPLANATION")
                .contains("PRACTICE_SPEAKING_EVALUATION")
                .contains("PARTIAL_NON_SCORE")
                .contains("full replacement")
                .contains("purpose-binding authority identity")
                .contains("dedicated Korean forced-aligner");
        assertThat(transport)
                .contains("response_format", "json_schema", "strict")
                .contains("snapshot.capabilities().strictJsonSchema()")
                .contains("status == 429", "status == 504");
        assertThat(decoder)
                .contains("PROVIDER_TRUNCATED_RESPONSE", "PROVIDER_REFUSAL")
                .doesNotContain("full replacement", "repairMalformed");
    }
}
