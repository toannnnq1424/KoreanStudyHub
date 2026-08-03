package com.ksh.features.practice.ai.speaking.acoustic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectAudioAcousticContractStaticTest {

    @Test
    void versionedSchemaIsClosedAndContainsNoInventedReleaseWeights()
            throws Exception {
        String schemaText = Files.readString(Path.of(
                "docs/contracts/ksh-speaking-direct-audio-acoustic-v1.schema.json"));
        JsonNode schema = new ObjectMapper().readTree(schemaText);

        assertThat(schema.path("properties").path("contract_version")
                .path("const").asText()).isEqualTo(
                DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION);
        assertThat(schemaText)
                .contains("\"additionalProperties\": false")
                .contains("PRONUNCIATION", "FLUENCY")
                .contains("provider_request_id", "provenance_digest")
                .contains("corpus_evidence_id", "fairness_evidence_id")
                .contains("\"eligible\": { \"const\": false }")
                .doesNotContain("scoring_weight", "fairness_threshold",
                        "holistic_score", "attempt_points");
    }

    @Test
    void acousticDarkResultHasNoCurrentPresenterProgressOrScoreConsumer()
            throws Exception {
        String identity = "DirectAudioAcousticObservationResult";
        String presenter = source(
                "src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java");
        String service = source(
                "src/main/java/com/ksh/features/practice/service/PracticeService.java");
        String progress = source(
                "src/main/java/com/ksh/features/practice/service/PracticeProgressService.java");
        String dto = source(
                "src/main/java/com/ksh/features/practice/dto/PracticeDtos.java");
        String scorePolicy = source(
                "src/main/java/com/ksh/features/practice/ai/speaking/SpeakingScorePolicy.java");

        assertThat(presenter + service + progress + dto + scorePolicy)
                .doesNotContain(identity,
                        DirectAudioAcousticResponseNormalizer.CONTRACT_VERSION,
                        "DirectAudioAcousticResponseNormalizer");
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
