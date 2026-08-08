package com.ksh.features.practice.ai.speaking.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanDirectAudioAlignmentContractStaticTest {

    private static final Path SCHEMA = Path.of(
            "docs/contracts/ksh-speaking-korean-alignment-v1.schema.json");

    @Test
    void schemaIsClosedKoreanHierarchyAndForbidsScoreOrRawPlaybackIdentity()
            throws Exception {
        String source = Files.readString(SCHEMA);
        JsonNode schema = new ObjectMapper().readTree(source);

        assertAllObjectsClosed(schema, "$root");
        assertThat(source)
                .contains("EOJJEOL", "SYLLABLE", "JAMO", "PHONEME")
                .contains("FORCED_ALIGNMENT", "ASR_WORD_TIMESTAMP")
                .contains("PARTIAL_NON_SCORE", "ALIGNMENT_DARK_ONLY")
                .contains("UTF16_CODE_UNIT")
                .doesNotContain("IPA", "GRAMMAR_ERROR", "LEXICAL_ERROR",
                        "raw_url", "audio_bytes", "access_token",
                        "holistic_score", "attempt_points");
    }

    @Test
    void currentLearnerSurfacesDoNotConsumeAlignmentOrCreatePerWordAudio()
            throws Exception {
        String[] paths = {
                "src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java",
                "src/main/java/com/ksh/features/practice/service/PracticeService.java",
                "src/main/java/com/ksh/features/practice/service/PracticeProgressService.java",
                "src/main/java/com/ksh/features/practice/dto/PracticeDtos.java",
                "src/main/java/com/ksh/features/practice/service/audio/"
                        + "ProfiledPracticeSpeakingAudioStorage.java"
        };
        for (String path : paths) {
            assertThat(Files.readString(Path.of(path))).as(path)
                    .doesNotContain("KoreanDirectAudioAlignment",
                            "ksh-speaking-korean-alignment-v1",
                            "wordAudio", "phonemeAudio");
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
}
