package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.QuestionContent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeTopik35ListeningImportPackageTest {
    private static final Path PACKAGE = Path.of(
            "docs/operations/practice-topik35-listening-import-package.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-listening-import-package.schema.json");
    private static final int[] ANSWERS = {
            2, 4, 1, 2, 4, 3, 2, 4, 1, 3,
            1, 3, 4, 4, 3, 3, 1, 1, 2, 1,
            2, 2, 4, 3, 4, 2, 2, 4, 1, 3,
            4, 2, 1, 2, 4, 4, 2, 4, 2, 1,
            3, 3, 1, 3, 3, 1, 1, 2, 4, 3
    };
    private static final int[][] BOUNDARIES = {
            {1, 3}, {4, 8}, {9, 12}, {13, 16}, {17, 20},
            {21, 22}, {23, 24}, {25, 26}, {27, 28}, {29, 30},
            {31, 32}, {33, 34}, {35, 36}, {37, 38}, {39, 40},
            {41, 42}, {43, 44}, {45, 46}, {47, 48}, {49, 50}
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void packagePinsCurrentTypedContractAndExactSourceIdentity() throws Exception {
        JsonNode root = readPackage();

        validate(root);
        assertThat(root.path("targetContract").path("questionContentSchema").asText())
                .isEqualTo(QuestionContent.SCHEMA_VERSION_V3);
        assertThat(root.path("targetContract").path("answerSchema").asText())
                .isEqualTo(AnswerSpec.SCHEMA_VERSION);
        assertThat(root.at("/sourceBindings/questionDocument/sha256").asText())
                .isEqualTo("cc21da6b877b27f0d7d3550c732282d610dadcea03a80842881f17eda3d51323");
        assertThat(root.at("/sourceBindings/transcriptDocument/sha256").asText())
                .isEqualTo("25caa51ee044b020f84b75fae2f64b5d95fbb35ebef07f58d5d8fc9186df3922");
        assertThat(root.at("/sourceBindings/answerDocument/sha256").asText())
                .isEqualTo("60fb5fa5e5a211609d0d6e36bc1cacc490c34ff5a0009e6952e931f9a850d17b");
        assertThat(root.path("sourceAnomalies").get(0).path("embeddedTitleMentions")
                .asText()).isEqualTo("32회");
        assertThat(root.path("sourceAnomalies").get(0)
                .path("visibleHeaderAndAnswerAuthority").asText())
                .isEqualTo("제35회");
    }

    @Test
    void exactGroupsAnswersAndPageLocatorsCoverOneThroughFifty() throws Exception {
        JsonNode root = readPackage();
        JsonNode groups = root.path("groups");
        JsonNode questions = root.path("questions");
        Set<Integer> covered = new HashSet<>();

        assertThat(groups).hasSize(20);
        for (int index = 0; index < groups.size(); index++) {
            JsonNode group = groups.get(index);
            assertThat(group.path("questionFrom").asInt()).isEqualTo(BOUNDARIES[index][0]);
            assertThat(group.path("questionTo").asInt()).isEqualTo(BOUNDARIES[index][1]);
            assertThat(group.path("displayOrder").asInt()).isEqualTo(index + 1);
            for (int number = BOUNDARIES[index][0];
                 number <= BOUNDARIES[index][1]; number++) {
                assertThat(covered.add(number)).isTrue();
            }
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList());

        assertThat(questions).hasSize(50);
        for (int index = 0; index < questions.size(); index++) {
            int number = index + 1;
            JsonNode question = questions.get(index);
            assertThat(question.path("questionNumber").asInt()).isEqualTo(number);
            assertThat(question.path("groupId").asText()).isEqualTo(groupId(number));
            assertThat(question.path("correctOptionNumber").asInt())
                    .isEqualTo(ANSWERS[index]);
            assertThat(question.path("correctOptionId").asText())
                    .isEqualTo("opt_" + ANSWERS[index]);
            assertThat(question.at("/answerSource/pdfPage").asInt()).isEqualTo(1);
            assertThat(question.at("/answerSource/rowQuestion").asInt())
                    .isEqualTo(number);
            assertThat(question.at("/questionSource/pdfPage").asInt())
                    .isEqualTo(questionPdfPage(number));
            assertThat(question.at("/questionSource/printedPage").asInt())
                    .isEqualTo(questionPdfPage(number) - 2);
            assertThat(question.at("/transcriptSource/pdfPage").asInt())
                    .isEqualTo(transcriptPdfPage(number));
            assertThat(question.path("optionPresentation").asText())
                    .isEqualTo(number <= 3 ? "IMAGE_OPTIONS" : "TEXT_OPTIONS");
        }
    }

    @Test
    void transcriptIsAuthoritativeButTimingAndImportStayFailClosed() throws Exception {
        JsonNode root = readPackage();

        assertThat(root.path("transcriptGaps")).isEmpty();
        root.path("questions").forEach(question ->
                assertThat(question.at("/transcriptSource/status").asText())
                        .isEqualTo("AUTHORITATIVE_PDF_PRESENT"));
        root.path("groups").forEach(group -> {
            JsonNode timing = group.path("timingQa");
            assertThat(timing.path("status").asText())
                    .isEqualTo("PENDING_MANUAL_AUDIO_QA");
            assertThat(timing.path("startMs").isNull()).isTrue();
            assertThat(timing.path("endMs").isNull()).isTrue();
            assertThat(timing.path("firstAudibleCueMatched").asBoolean()).isFalse();
            assertThat(timing.path("finalAudibleCueMatched").asBoolean()).isFalse();
            assertThat(timing.path("repeatPlaybackAccountedFor").asBoolean()).isFalse();
            assertThat(timing.path("neighborBoundaryChecked").asBoolean()).isFalse();
            assertThat(timing.path("transcriptBoundaryChecked").asBoolean()).isFalse();
            assertThat(timing.path("examModeConsumption").asText()).isEqualTo("FORBIDDEN");
        });
        assertThat(root.path("materializationBlockers")).hasSize(2);
        assertThat(root.at("/questionPayloadBinding/packageId").asText())
                .isEqualTo("topik35-v1-listening-question-payload-v1");
        assertThat(root.at("/validationSummary/payloadQuestionCount").asInt())
                .isEqualTo(50);
        assertThat(root.at("/validationSummary/payloadOptionCount").asInt())
                .isEqualTo(200);
        assertThat(root.at("/validationSummary/payloadVisualAssetCount").asInt())
                .isEqualTo(12);
        assertThat(root.at("/transcriptPayloadBinding/packageId").asText())
                .isEqualTo("topik35-v1-listening-transcript-payload-v1");
        assertThat(root.at("/validationSummary/transcriptMaterializedGroupCount")
                .asInt()).isEqualTo(20);
        assertThat(root.at("/validationSummary/transcriptQuestionBindingCount")
                .asInt()).isEqualTo(50);
        assertThat(root.at("/targetContract/candidateMaterialized").asBoolean()).isFalse();
        assertThat(root.at("/validationSummary/loadReady").asBoolean()).isFalse();
        assertThat(root.at("/loadPolicy/bulkSetIngestionAllowed").asBoolean()).isFalse();
    }

    @Test
    void singleProgramExamPolicyNeverUsesTimestampsForLearnerNavigation()
            throws Exception {
        JsonNode policy = readPackage().path("playbackPolicy");

        assertThat(policy.path("singleOrderedAudioProgram").asBoolean()).isTrue();
        assertThat(policy.path("startOnce").asBoolean()).isTrue();
        assertThat(policy.path("continuousPlayback").asBoolean()).isTrue();
        assertThat(policy.path("seekAllowed").asBoolean()).isFalse();
        assertThat(policy.path("replayAllowed").asBoolean()).isFalse();
        assertThat(policy.path("learnerControlsQuestionNavigation").asBoolean()).isTrue();
        assertThat(policy.path("timestampAutoNavigation").asBoolean()).isFalse();
        assertThat(policy.path("timestampAutoHighlight").asBoolean()).isFalse();
        assertThat(policy.path("timestampDrivenAssistance").asBoolean()).isFalse();
    }

    @Test
    void validatorRejectsAnswerBoundaryTranscriptTimingAndPlaybackTampering()
            throws Exception {
        ObjectNode wrongAnswer = copy();
        ((ObjectNode) wrongAnswer.path("questions").get(0)).put("correctOptionNumber", 1);
        assertThatThrownBy(() -> validate(wrongAnswer)).hasMessageContaining("answer map");

        ObjectNode wrongBoundary = copy();
        ((ObjectNode) wrongBoundary.path("groups").get(0)).put("questionTo", 4);
        assertThatThrownBy(() -> validate(wrongBoundary)).hasMessageContaining("boundary");

        ObjectNode missingTranscript = copy();
        ((ObjectNode) missingTranscript.path("questions").get(20)
                .path("transcriptSource")).put("status", "UNAVAILABLE");
        assertThatThrownBy(() -> validate(missingTranscript))
                .hasMessageContaining("transcript");

        ObjectNode falseTiming = copy();
        ((ObjectNode) falseTiming.path("groups").get(0).path("timingQa"))
                .put("status", "READY");
        assertThatThrownBy(() -> validate(falseTiming)).hasMessageContaining("timing");

        ObjectNode assistedPlayback = copy();
        ((ObjectNode) assistedPlayback.path("playbackPolicy"))
                .put("timestampAutoNavigation", true);
        assertThatThrownBy(() -> validate(assistedPlayback))
                .hasMessageContaining("playback");
    }

    @Test
    void packageHasLogicalKeysOnlyAndSchemaIsClosed() throws Exception {
        String raw = Files.readString(PACKAGE);
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA));

        assertThat(raw).doesNotContain(
                "/Users/", "/tmp/", "uploads/", "file://", "s3://", "r2://",
                ".r2.cloudflarestorage.com", "bucketName", "audio_url");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/group/additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/question/additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/timingQa/additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/playbackPolicy/additionalProperties").asBoolean())
                .isFalse();
    }

    private JsonNode readPackage() throws Exception {
        return objectMapper.readTree(Files.readString(PACKAGE));
    }

    private ObjectNode copy() throws Exception {
        return (ObjectNode) readPackage();
    }

    private static void validate(JsonNode root) {
        require("practice-topik35-listening-import-package-v1".equals(
                root.path("schemaVersion").asText()), "schema identity");
        require("TRANSCRIPT_AND_QUESTION_PAYLOAD_QA_COMPLETE_IMPORT_LOAD_BLOCKED".equals(
                root.path("status").asText()), "fail-closed status");
        require(root.path("groups").size() == 20, "group count");
        require(root.path("questions").size() == 50, "question count");
        require(root.path("transcriptGaps").isArray()
                && root.path("transcriptGaps").isEmpty(), "transcript gaps");

        Set<Integer> covered = new HashSet<>();
        for (int index = 0; index < root.path("groups").size(); index++) {
            JsonNode group = root.path("groups").get(index);
            int from = group.path("questionFrom").asInt();
            int to = group.path("questionTo").asInt();
            require(from == BOUNDARIES[index][0] && to == BOUNDARIES[index][1],
                    "group boundary");
            for (int number = from; number <= to; number++) {
                require(covered.add(number), "group boundary overlap");
            }
            JsonNode timing = group.path("timingQa");
            require("PENDING_MANUAL_AUDIO_QA".equals(timing.path("status").asText())
                    && timing.path("startMs").isNull()
                    && timing.path("endMs").isNull(), "timing must remain pending");
        }
        require(covered.size() == 50, "group boundary coverage");

        for (int index = 0; index < root.path("questions").size(); index++) {
            int number = index + 1;
            JsonNode question = root.path("questions").get(index);
            require(question.path("questionNumber").asInt() == number,
                    "question order");
            require(groupId(number).equals(question.path("groupId").asText()),
                    "question group membership");
            int answer = question.path("correctOptionNumber").asInt();
            require(answer == ANSWERS[index]
                    && ("opt_" + answer).equals(question.path("correctOptionId").asText()),
                    "answer map");
            require(question.at("/answerSource/rowQuestion").asInt() == number,
                    "answer source row");
            require(question.at("/questionSource/pdfPage").asInt()
                    == questionPdfPage(number), "question page map");
            require("AUTHORITATIVE_PDF_PRESENT".equals(
                    question.at("/transcriptSource/status").asText())
                    && question.at("/transcriptSource/pdfPage").asInt()
                    == transcriptPdfPage(number), "transcript coverage");
        }

        JsonNode playback = root.path("playbackPolicy");
        require(playback.path("singleOrderedAudioProgram").asBoolean()
                && playback.path("startOnce").asBoolean()
                && playback.path("continuousPlayback").asBoolean()
                && playback.path("learnerControlsQuestionNavigation").asBoolean()
                && !playback.path("seekAllowed").asBoolean()
                && !playback.path("replayAllowed").asBoolean()
                && !playback.path("timestampAutoNavigation").asBoolean()
                && !playback.path("timestampAutoHighlight").asBoolean()
                && !playback.path("timestampDrivenAssistance").asBoolean(),
                "playback policy");
        require(!root.at("/validationSummary/loadReady").asBoolean()
                && !root.at("/targetContract/candidateMaterialized").asBoolean()
                && !root.at("/loadPolicy/bulkSetIngestionAllowed").asBoolean(),
                "load gate");
    }

    private static int questionPdfPage(int number) {
        if (number <= 2) return 3;
        if (number <= 6) return 4;
        if (number <= 12) return 5;
        if (number <= 16) return 6;
        if (number <= 20) return 7;
        if (number <= 24) return 8;
        if (number <= 28) return 9;
        if (number <= 32) return 10;
        if (number <= 36) return 11;
        if (number <= 40) return 12;
        if (number <= 44) return 13;
        if (number <= 48) return 14;
        return 15;
    }

    private static int transcriptPdfPage(int number) {
        if (number <= 3) return number;
        if (number <= 6) return 4;
        if (number <= 8) return 5;
        if (number <= 10) return 6;
        if (number <= 12) return 7;
        if (number <= 14) return 8;
        if (number <= 16) return 9;
        if (number <= 18) return 10;
        if (number <= 20) return 11;
        return 12 + ((number - 21) / 2);
    }

    private static String groupId(int number) {
        for (int[] boundary : BOUNDARIES) {
            if (number >= boundary[0] && number <= boundary[1]) {
                return "L%02d_%02d".formatted(boundary[0], boundary[1]);
            }
        }
        throw new IllegalArgumentException("question outside canonical boundaries");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
