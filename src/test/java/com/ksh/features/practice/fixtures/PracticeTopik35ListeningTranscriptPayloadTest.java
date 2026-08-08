package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeTopik35ListeningTranscriptPayloadTest {
    private static final Path PAYLOAD = Path.of(
            "docs/operations/practice-topik35-listening-transcript-payload.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-listening-transcript-payload.schema.json");
    private static final Path QUESTION_PAYLOAD = Path.of(
            "docs/operations/practice-topik35-listening-question-payload.json");
    private static final Path IMPORT_PACKAGE = Path.of(
            "docs/operations/practice-topik35-listening-import-package.json");
    private static final String PROJECTION_SHA256 =
            "490f58b95a23ceb3f66745d1cebea79b6aa46715fed72118be08da70c2cb7daf";
    private static final int[][] BOUNDARIES = {
            {1, 3}, {4, 8}, {9, 12}, {13, 16}, {17, 20},
            {21, 22}, {23, 24}, {25, 26}, {27, 28}, {29, 30},
            {31, 32}, {33, 34}, {35, 36}, {37, 38}, {39, 40},
            {41, 42}, {43, 44}, {45, 46}, {47, 48}, {49, 50}
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exactAuthoritativeTranscriptProjectionIsPinned() throws Exception {
        JsonNode root = payload();

        validate(root);
        assertThat(projectionSha256(root)).isEqualTo(PROJECTION_SHA256);
        assertThat(root.at("/validationSummary/contentProjectionSha256").asText())
                .isEqualTo(PROJECTION_SHA256);
        assertThat(root.path("groups")).hasSize(20);
        assertThat(root.path("questionBindings")).hasSize(50);
        assertThat(root.at("/validationSummary/sourcePageCount").asInt())
                .isEqualTo(26);
        assertThat(root.at("/sourceBinding/sha256").asText())
                .isEqualTo("25caa51ee044b020f84b75fae2f64b5d95fbb35ebef07f58d5d8fc9186df3922");
    }

    @Test
    void everyGroupUsesTheCurrentTypedListeningStimulusAndExactPageProvenance()
            throws Exception {
        JsonNode root = payload();
        JsonNode sourcePackage = read(IMPORT_PACKAGE);
        Set<Integer> pages = new HashSet<>();

        for (int index = 0; index < root.path("groups").size(); index++) {
            JsonNode group = root.path("groups").get(index);
            JsonNode sourceGroup = sourcePackage.path("groups").get(index);
            JsonNode stimulus = group.path("stimulus");

            assertThat(group.path("groupId").asText())
                    .isEqualTo(sourceGroup.path("groupId").asText());
            assertThat(group.path("questionFrom").asInt())
                    .isEqualTo(sourceGroup.path("questionFrom").asInt());
            assertThat(group.path("questionTo").asInt())
                    .isEqualTo(sourceGroup.path("questionTo").asInt());
            assertThat(group.path("sourcePdfPages"))
                    .isEqualTo(sourceGroup.path("transcriptSourcePdfPages"));
            assertThat(stimulus.path("schemaVersion").asText())
                    .isEqualTo(PracticeDraftContractService.STIMULUS_SCHEMA_VERSION);
            assertThat(stimulus.path("type").asText()).isEqualTo("LISTENING_AUDIO");
            assertThat(stimulus.path("instruction").asText())
                    .isEqualTo(sourceGroup.path("instruction").asText());
            assertThat(stimulus.path("passageText").asText()).isEmpty();
            assertThat(stimulus.path("transcriptText").asText()).isNotBlank();
            assertThat(stimulus.path("mediaReference").isNull()).isTrue();
            assertThat(stimulus.path("sourceRefs")).hasSameSizeAs(
                    group.path("sourcePdfPages"));
            group.path("sourcePdfPages").forEach(page -> pages.add(page.asInt()));
            for (int pageIndex = 0;
                 pageIndex < group.path("sourcePdfPages").size(); pageIndex++) {
                assertThat(stimulus.path("sourceRefs").get(pageIndex)
                        .path("pageNumber").asInt())
                        .isEqualTo(group.path("sourcePdfPages").get(pageIndex).asInt());
            }
        }

        assertThat(pages).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 26).boxed().toList());
    }

    @Test
    void fiftyBindingsStrictlyMatchQuestionAndSourceImportPayloads()
            throws Exception {
        JsonNode transcript = payload();
        JsonNode questions = read(QUESTION_PAYLOAD);
        JsonNode sourcePackage = read(IMPORT_PACKAGE);

        assertThat(transcript.at("/crossCheck/questionPayloadProjectionSha256")
                .asText()).isEqualTo(questions.at(
                "/validationSummary/contentProjectionSha256").asText());
        assertThat(transcript.path("questionBindings")).hasSameSizeAs(
                questions.path("questions"));
        assertThat(transcript.path("questionBindings")).hasSameSizeAs(
                sourcePackage.path("questions"));

        for (int index = 0; index < 50; index++) {
            JsonNode binding = transcript.path("questionBindings").get(index);
            JsonNode question = questions.path("questions").get(index);
            JsonNode sourceQuestion = sourcePackage.path("questions").get(index);

            assertThat(binding.path("questionNumber").asInt())
                    .isEqualTo(question.path("questionNumber").asInt())
                    .isEqualTo(sourceQuestion.path("questionNumber").asInt());
            assertThat(binding.path("seedKey").asText())
                    .isEqualTo(question.path("seedKey").asText())
                    .isEqualTo(sourceQuestion.path("seedKey").asText());
            assertThat(binding.path("groupId").asText())
                    .isEqualTo(question.path("groupId").asText())
                    .isEqualTo(sourceQuestion.path("groupId").asText());
            assertThat(binding.path("sourcePdfPage").asInt())
                    .isEqualTo(sourceQuestion.at("/transcriptSource/pdfPage").asInt());
            assertThat(binding.path("sourcePrintedPage").asInt())
                    .isEqualTo(sourceQuestion.at("/transcriptSource/printedPage").asInt());
        }
    }

    @Test
    void transcriptCheckpointsPreserveOfficialKoreanText() throws Exception {
        JsonNode groups = payload().path("groups");

        assertThat(groups.get(0).at("/stimulus/transcriptText").asText())
                .startsWith("1. 여자: 이거 좀 봐 주실래요? 옷에 커피를 쏟았어요.")
                .contains("3. 남자: 스마트폰 사용자가 많아지면서 모바일 쇼핑 이용객이 급격히 증가한");
        assertThat(groups.get(5).at("/stimulus/transcriptText").asText())
                .contains("공동생활도 생각보다 괜찮아.")
                .contains("서로 의지가 되니까 외로움도 덜하고.");
        assertThat(groups.get(13).at("/stimulus/transcriptText").asText())
                .contains("『1만 시간의 법칙』")
                .contains("자신의 한계를 넘어서려는 노력과 연습");
        assertThat(groups.get(19).at("/stimulus/transcriptText").asText())
                .contains("물과 관련된 기업, 대학, 연구소의 핵심 주체들이 모여")
                .endsWith("물 산업 발전에 시너지 효과를 낳을 수 있을 것입니다.");
    }

    @Test
    void timingAndLoadRemainFailClosedWithoutCaptionOrAudioInference()
            throws Exception {
        JsonNode root = payload();
        JsonNode sourcePackage = read(IMPORT_PACKAGE);

        assertThat(root.at("/materializationPolicy/captionUsed").asBoolean()).isFalse();
        assertThat(root.at("/materializationPolicy/audioUsedForText").asBoolean())
                .isFalse();
        assertThat(root.at("/materializationPolicy/timestampsDerived").asBoolean())
                .isFalse();
        root.path("groups").forEach(group -> {
            assertThat(group.path("timingStatus").asText())
                    .isEqualTo("PENDING_MANUAL_AUDIO_QA");
            assertThat(group.path("startMs").isNull()).isTrue();
            assertThat(group.path("endMs").isNull()).isTrue();
        });
        sourcePackage.path("groups").forEach(group -> {
            assertThat(group.at("/timingQa/status").asText())
                    .isEqualTo("PENDING_MANUAL_AUDIO_QA");
            assertThat(group.at("/timingQa/startMs").isNull()).isTrue();
            assertThat(group.at("/timingQa/endMs").isNull()).isTrue();
        });
        assertThat(root.path("remainingLoadBlockers").toString())
                .contains("GROUP_TIMING_RANGES_NOT_YET_MANUALLY_VERIFIED")
                .contains("CANONICAL_DRAFT_VERSION_IDS_NOT_YET_ALLOCATED");
        assertThat(root.at("/validationSummary/loadReady").asBoolean()).isFalse();
        assertThat(sourcePackage.at("/validationSummary/loadReady").asBoolean())
                .isFalse();
        assertThat(sourcePackage.path("materializationBlockers").toString())
                .doesNotContain("GROUP_TRANSCRIPT_TEXT_NOT_MATERIALIZED_BY_THIS_SLICE");
    }

    @Test
    void missingMisorderedAndMismatchedTranscriptEntriesFailClosed()
            throws Exception {
        ObjectNode missing = copy();
        ((ArrayNode) missing.path("questionBindings")).remove(19);
        assertThatThrownBy(() -> validate(missing))
                .hasMessageContaining("binding count");

        ObjectNode misordered = copy();
        ArrayNode bindings = (ArrayNode) misordered.path("questionBindings");
        JsonNode first = bindings.get(0).deepCopy();
        JsonNode second = bindings.get(1).deepCopy();
        bindings.set(0, second);
        bindings.set(1, first);
        assertThatThrownBy(() -> validate(misordered))
                .hasMessageContaining("binding order");

        ObjectNode wrongGroup = copy();
        ((ObjectNode) wrongGroup.path("questionBindings").get(20))
                .put("groupId", "L23_24");
        assertThatThrownBy(() -> validate(wrongGroup))
                .hasMessageContaining("binding group");

        ObjectNode wrongPage = copy();
        ((ObjectNode) wrongPage.path("questionBindings").get(48))
                .put("sourcePdfPage", 25);
        assertThatThrownBy(() -> validate(wrongPage))
                .hasMessageContaining("binding page");

        ObjectNode blankTranscript = copy();
        ((ObjectNode) blankTranscript.path("groups").get(8)
                .path("stimulus")).put("transcriptText", " ");
        assertThatThrownBy(() -> validate(blankTranscript))
                .hasMessageContaining("transcript text");
    }

    @Test
    void schemaAndPayloadRejectLocalIdentityAndFalseReadiness() throws Exception {
        JsonNode schema = read(SCHEMA);
        String raw = Files.readString(PAYLOAD);

        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/group/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/stimulus/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/questionBinding/additionalProperties")
                .asBoolean()).isFalse();
        assertThat(raw).doesNotContain(
                "/Users/", "/tmp/", "uploads/", "file://", "s3://", "r2://",
                ".r2.cloudflarestorage.com", "bucketName", "audio_url");

        ObjectNode falseTiming = copy();
        ((ObjectNode) falseTiming.path("groups").get(0))
                .put("timingStatus", "READY").put("startMs", 0).put("endMs", 1);
        assertThatThrownBy(() -> validate(falseTiming))
                .hasMessageContaining("timing");

        ObjectNode falseReady = copy();
        ((ObjectNode) falseReady.path("validationSummary")).put("loadReady", true);
        assertThatThrownBy(() -> validate(falseReady))
                .hasMessageContaining("load gate");
    }

    private JsonNode payload() throws Exception {
        return read(PAYLOAD);
    }

    private JsonNode read(Path path) throws Exception {
        return objectMapper.readTree(Files.readString(path));
    }

    private ObjectNode copy() throws Exception {
        return (ObjectNode) payload();
    }

    private void validate(JsonNode root) throws Exception {
        require("practice-topik35-listening-transcript-payload-v1".equals(
                root.path("schemaVersion").asText()), "schema identity");
        require("TRANSCRIPT_QA_COMPLETE_LOAD_BLOCKED".equals(
                root.path("status").asText()), "status");
        require(root.path("groups").size() == 20, "group count");
        require(root.path("questionBindings").size() == 50, "binding count");

        Map<String, JsonNode> groups = new HashMap<>();
        Set<Integer> covered = new HashSet<>();
        for (int index = 0; index < root.path("groups").size(); index++) {
            JsonNode group = root.path("groups").get(index);
            int from = BOUNDARIES[index][0];
            int to = BOUNDARIES[index][1];
            String id = groupId(from);
            require(id.equals(group.path("groupId").asText())
                    && group.path("displayOrder").asInt() == index + 1
                    && group.path("questionFrom").asInt() == from
                    && group.path("questionTo").asInt() == to, "group order");
            require(groups.put(id, group) == null, "duplicate group");
            for (int number = from; number <= to; number++) {
                require(covered.add(number), "group coverage overlap");
            }
            JsonNode stimulus = group.path("stimulus");
            require(PracticeDraftContractService.STIMULUS_SCHEMA_VERSION.equals(
                    stimulus.path("schemaVersion").asText())
                    && "LISTENING_AUDIO".equals(stimulus.path("type").asText())
                    && !stimulus.path("transcriptText").asText().isBlank(),
                    "transcript text");
            require("VISUALLY_VERIFIED".equals(
                    group.path("transcriptQaStatus").asText()), "transcript QA");
            require("PENDING_MANUAL_AUDIO_QA".equals(
                    group.path("timingStatus").asText())
                    && group.path("startMs").isNull()
                    && group.path("endMs").isNull(), "timing pending");
        }
        require(covered.size() == 50, "group coverage");

        for (int index = 0; index < root.path("questionBindings").size(); index++) {
            int number = index + 1;
            JsonNode binding = root.path("questionBindings").get(index);
            require(binding.path("questionNumber").asInt() == number
                    && ("topik35-listening-q%02d".formatted(number))
                    .equals(binding.path("seedKey").asText()), "binding order");
            String id = groupId(number);
            require(id.equals(binding.path("groupId").asText()), "binding group");
            JsonNode group = groups.get(id);
            int page = transcriptPdfPage(number);
            require(binding.path("sourcePdfPage").asInt() == page
                    && binding.path("sourcePrintedPage").asInt() == page
                    && contains(group.path("sourcePdfPages"), page), "binding page");
        }

        require(!root.at("/materializationPolicy/captionUsed").asBoolean()
                && !root.at("/materializationPolicy/audioUsedForText").asBoolean()
                && !root.at("/materializationPolicy/timestampsDerived").asBoolean(),
                "source policy");
        require(!root.at("/validationSummary/loadReady").asBoolean()
                && root.at("/validationSummary/timingReadyGroupCount").asInt() == 0,
                "load gate");
        require(PROJECTION_SHA256.equals(projectionSha256(root))
                && PROJECTION_SHA256.equals(root.at(
                "/validationSummary/contentProjectionSha256").asText()),
                "content projection");
    }

    private String projectionSha256(JsonNode root) throws Exception {
        ObjectNode projection = objectMapper.createObjectNode();
        projection.set("groups", root.path("groups"));
        projection.set("questionBindings", root.path("questionBindings"));
        return sha256(objectMapper.writeValueAsBytes(canonical(projection)));
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name ->
                    sorted.set(name, canonical(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(value -> array.add(canonical(value)));
            return array;
        }
        return node.deepCopy();
    }

    private static boolean contains(JsonNode array, int expected) {
        for (JsonNode value : array) {
            if (value.asInt() == expected) return true;
        }
        return false;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bytes));
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
