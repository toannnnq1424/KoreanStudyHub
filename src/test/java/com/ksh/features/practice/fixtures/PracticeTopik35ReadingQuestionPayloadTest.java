package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeTopik35ReadingQuestionPayloadTest {
    private static final Path PAYLOAD = Path.of(
            "docs/operations/practice-topik35-reading-question-payload.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-reading-question-payload.schema.json");
    private static final Path SOURCE_AUDIT = Path.of(
            "docs/operations/practice-topik35-reading-source-audit.json");
    private static final Path BUNDLE = Path.of(
            "docs/operations/practice-topik35-canonical-seed-bundle.json");
    private static final String READING_SHA =
            "d3618891f8afdb4739754067ab8268632998fc522ee0a5519b1286984454a4cd";
    private static final String ANSWER_SHA =
            "60fb5fa5e5a211609d0d6e36bc1cacc490c34ff5a0009e6952e931f9a850d17b";
    private static final String CONTENT_SHA =
            "ff98f5c7c112f46d42e19682041dbc40c80aa8ab2abd2d19587560c53f9dc5e6";
    private static final int[] ANSWERS = {
            3, 4, 1, 1, 1, 2, 1, 3, 4, 1,
            2, 3, 1, 4, 4, 2, 3, 3, 2, 4,
            1, 2, 1, 3, 3, 1, 4, 3, 4, 4,
            1, 2, 4, 4, 2, 2, 4, 4, 2, 3,
            3, 1, 2, 2, 3, 2, 3, 2, 1, 4
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allFiftyQuestionsOptionsAnswersGroupsAndPagesValidateAtomically() throws Exception {
        JsonNode root = read(PAYLOAD);

        validate(root);
        assertThat(root.path("questions")).hasSize(50);
        assertThat(root.path("passageGroups")).hasSize(42);
        assertThat(root.path("pageQa")).hasSize(21);
        assertThat(root.at("/validationSummary/answerVector"))
                .extracting(JsonNode::asInt)
                .containsExactly(java.util.Arrays.stream(ANSWERS).boxed().toArray(Integer[]::new));
    }

    @Test
    void typedQuestionAndAnswerContractsRemainCanonical() throws Exception {
        JsonNode root = read(PAYLOAD);

        for (JsonNode question : root.path("questions")) {
            assertThat(question.path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
            assertThat(question.at("/questionContent/schemaVersion").asText())
                    .isEqualTo("question-content-v3");
            assertThat(question.at("/questionContent/languageTag").asText())
                    .isEqualTo("ko");
            assertThat(question.at("/answerSpec/schemaVersion").asText())
                    .isEqualTo("answer-spec-v1");
            assertThat(question.at("/answerSpec/scoringPolicyCode").asText())
                    .isEqualTo("ALL_OR_NOTHING");
            assertThat(question.path("points").asInt()).isEqualTo(2);
        }
    }

    @Test
    void sourceAuditAndCanonicalBundlePinTheSameReadingAndAnswerArtifacts()
            throws Exception {
        JsonNode payload = read(PAYLOAD);
        JsonNode audit = read(SOURCE_AUDIT);
        JsonNode bundle = read(BUNDLE);
        Map<String, JsonNode> artifacts = new HashMap<>();
        bundle.path("artifacts").forEach(node -> artifacts.put(
                node.path("artifactId").asText(), node));

        assertThat(payload.at("/sourceAuthority/questionArtifactSha256").asText())
                .isEqualTo(READING_SHA);
        assertThat(payload.at("/sourceAuthority/answerArtifactSha256").asText())
                .isEqualTo(ANSWER_SHA);
        assertThat(audit.at("/candidates/0/sha256").asText()).isEqualTo(READING_SHA);
        assertThat(audit.at("/candidates/1/sha256").asText()).isEqualTo(ANSWER_SHA);
        assertThat(artifacts.get("topik35-reading-pdf").path("sha256").asText())
                .isEqualTo(READING_SHA);
        assertThat(artifacts.get("topik35-answer-key-pdf").path("sha256").asText())
                .isEqualTo(ANSWER_SHA);
    }

    @Test
    void visualAssetsHaveExactCropDigestAndLogicalKeyProvenance() throws Exception {
        JsonNode assets = read(PAYLOAD).path("visualAssets");

        assertAsset(assets.get(0), "topik35-reading-q09-table", 9,
                "2bb6a1b88df53a1aafd6e1f70906f0aa1068f46079b060123df0c2c215d81e85",
                121704, 1120, 580, 290, 335);
        assertAsset(assets.get(1), "topik35-reading-q10-chart", 10,
                "734ae3aa61930d951e8a2a800409712d6d73fe8bfef6bf13fe311538a6b671ca",
                52556, 940, 500, 360, 1300);
    }

    @Test
    void validatorRejectsAnyPartialQuestionAnswerGroupPageAssetOrLoadMutation()
            throws Exception {
        ObjectNode missingQuestion = copy();
        ((ArrayNode) missingQuestion.path("questions")).remove(49);
        assertThatThrownBy(() -> validate(missingQuestion))
                .hasMessageContaining("question count");

        ObjectNode missingOption = copy();
        ((ArrayNode) missingOption.at("/questions/0/questionContent/options")).remove(3);
        assertThatThrownBy(() -> validate(missingOption))
                .hasMessageContaining("option set");

        ObjectNode wrongAnswer = copy();
        ((ArrayNode) wrongAnswer.at("/questions/0/answerSpec/correctOptionIds"))
                .set(0, objectMapper.getNodeFactory().textNode("opt_1"));
        assertThatThrownBy(() -> validate(wrongAnswer))
                .hasMessageContaining("answer vector");

        ObjectNode missingGroupQuestion = copy();
        ((ArrayNode) missingGroupQuestion.at("/passageGroups/18/questionNumbers")).remove(1);
        assertThatThrownBy(() -> validate(missingGroupQuestion))
                .hasMessageContaining("group coverage");

        ObjectNode wrongGroupPage = copy();
        ((ObjectNode) wrongGroupPage.path("passageGroups").get(0))
                .put("sourcePrintedPage", 2);
        assertThatThrownBy(() -> validate(wrongGroupPage))
                .hasMessageContaining("group page");

        ObjectNode wrongCrop = copy();
        ((ObjectNode) wrongCrop.at("/visualAssets/0/source/cropPixels")).put("x", 291);
        assertThatThrownBy(() -> validate(wrongCrop))
                .hasMessageContaining("visual asset");

        ObjectNode falseReady = copy();
        ((ObjectNode) falseReady.path("loadPolicy")).put("loadReady", true);
        assertThatThrownBy(() -> validate(falseReady))
                .hasMessageContaining("load gate");
    }

    @Test
    void payloadUsesNoLocalPathDeliveryUrlOrInventedReadingTranscript() throws Exception {
        String raw = Files.readString(PAYLOAD);
        JsonNode schema = read(SCHEMA);

        assertThat(raw).doesNotContain(
                "/Users/", "/tmp/", "uploads/", "file://", "s3://", "r2://",
                ".r2.cloudflarestorage.com", "bucketName", "deliveryUrl");
        assertThat(raw).contains("\"readingTranscriptProvided\": false");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/passageGroup/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/question/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/visualAsset/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/loadPolicy/additionalProperties").asBoolean())
                .isFalse();
    }

    private JsonNode read(Path path) throws Exception {
        return objectMapper.readTree(Files.readString(path));
    }

    private ObjectNode copy() throws Exception {
        return (ObjectNode) read(PAYLOAD);
    }

    private void validate(JsonNode root) throws Exception {
        require("practice-topik35-reading-question-payload-v1".equals(
                root.path("schemaVersion").asText()), "schema identity");
        require(root.path("pageQa").size() == 21, "page count");
        require(root.path("passageGroups").size() == 42, "group count");
        require(root.path("questions").size() == 50, "question count");
        require(root.path("visualAssets").size() == 2, "visual asset count");
        require(root.path("qaBlockers").isEmpty(), "QA blockers");

        Map<Integer, Integer> pageByQuestion = validatePages(root.path("pageQa"));
        Map<String, JsonNode> groups = validateGroups(root.path("passageGroups"));
        validateAssets(root.path("visualAssets"), groups);
        validateQuestions(root.path("questions"), groups, pageByQuestion);

        JsonNode authority = root.path("sourceAuthority");
        require(READING_SHA.equals(authority.path("questionArtifactSha256").asText())
                && ANSWER_SHA.equals(authority.path("answerArtifactSha256").asText())
                && !authority.path("sourceAnnotationsAreAnswerAuthority").asBoolean()
                && !authority.path("readingTranscriptProvided").asBoolean()
                && !authority.path("separateReadingAssetRouteProvided").asBoolean(),
                "source authority");

        JsonNode load = root.path("loadPolicy");
        require(load.path("contentQaComplete").asBoolean()
                && load.path("assetQaComplete").asBoolean()
                && load.path("allOrNothingValidationRequired").asBoolean()
                && !load.path("databaseLoadPerformed").asBoolean()
                && !load.path("migrationPerformed").asBoolean()
                && !load.path("objectStoreWritePerformed").asBoolean()
                && !load.path("providerCallPerformed").asBoolean()
                && !load.path("aiCallPerformed").asBoolean()
                && !load.path("loadReady").asBoolean()
                && !root.at("/validationSummary/loadReady").asBoolean(), "load gate");

        String actualContentSha = contentProjectionSha256(root);
        require(CONTENT_SHA.equals(actualContentSha),
                "content projection digest: " + actualContentSha);
    }

    private static Map<Integer, Integer> validatePages(JsonNode pages) {
        Map<Integer, Integer> pageByQuestion = new HashMap<>();
        int expectedQuestion = 1;
        for (int index = 0; index < pages.size(); index++) {
            JsonNode page = pages.get(index);
            int pdfPage = 3 + index;
            int printedPage = 1 + index;
            int from = page.path("questionFrom").asInt();
            int to = page.path("questionTo").asInt();
            require(page.path("pdfPage").asInt() == pdfPage
                    && page.path("printedPage").asInt() == printedPage
                    && from == expectedQuestion && to >= from
                    && "VISUALLY_VERIFIED".equals(page.path("qaStatus").asText()),
                    "page sequence");
            for (int question = from; question <= to; question++) {
                require(pageByQuestion.put(question, pdfPage) == null, "page coverage");
            }
            expectedQuestion = to + 1;
        }
        require(expectedQuestion == 51 && pageByQuestion.size() == 50, "page coverage");
        return pageByQuestion;
    }

    private static Map<String, JsonNode> validateGroups(JsonNode groupNodes) {
        Map<String, JsonNode> groups = new HashMap<>();
        Set<Integer> coveredQuestions = new TreeSet<>();
        int sharedGroupCount = 0;
        for (JsonNode group : groupNodes) {
            String groupId = group.path("groupId").asText();
            require(groups.put(groupId, group) == null, "group identity");
            require(group.path("sourcePrintedPage").asInt()
                    == group.path("sourcePdfPage").asInt() - 2, "group page");
            require(!group.path("instruction").asText().isBlank()
                    && !group.path("passageText").asText().isBlank()
                    && "VISUALLY_VERIFIED".equals(group.path("qaStatus").asText()),
                    "group content");
            String passage = group.path("passageText").asText();
            require(occurrences(passage, "[BLANK]") == group.path("blankCount").asInt(),
                    "blank marker count");
            int slotCount = 0;
            for (int slot = 1; slot <= 4; slot++) {
                int count = occurrences(passage, "[SLOT_" + slot + "]");
                require(count <= 1, "insertion marker count");
                slotCount += count;
            }
            require(slotCount == group.path("insertionSlotCount").asInt(),
                    "insertion marker count");
            if (group.path("questionNumbers").size() > 1) sharedGroupCount++;
            for (JsonNode question : group.path("questionNumbers")) {
                require(coveredQuestions.add(question.asInt()), "group coverage");
            }
        }
        require(coveredQuestions.equals(new TreeSet<>(
                java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList())),
                "group coverage");
        require(sharedGroupCount == 7, "shared group count");
        return groups;
    }

    private static void validateAssets(JsonNode assets, Map<String, JsonNode> groups) {
        JsonNode q9 = assets.get(0);
        JsonNode q10 = assets.get(1);
        require(assetMatches(q9, "topik35-reading-q09-table", 9,
                "2bb6a1b88df53a1aafd6e1f70906f0aa1068f46079b060123df0c2c215d81e85",
                121704, 1120, 580, 290, 335), "visual asset q9");
        require(assetMatches(q10, "topik35-reading-q10-chart", 10,
                "734ae3aa61930d951e8a2a800409712d6d73fe8bfef6bf13fe311538a6b671ca",
                52556, 940, 500, 360, 1300), "visual asset q10");
        require(groups.get("R009").path("visualAssetIds").get(0).asText()
                .equals(q9.path("assetId").asText())
                && groups.get("R010").path("visualAssetIds").get(0).asText()
                .equals(q10.path("assetId").asText()), "visual asset group binding");
    }

    private static void validateQuestions(JsonNode questions, Map<String, JsonNode> groups,
                                          Map<Integer, Integer> pageByQuestion) {
        List<Integer> actualAnswers = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            int number = index + 1;
            JsonNode question = questions.get(index);
            require(question.path("questionNumber").asInt() == number
                    && ("topik35-reading-q" + String.format("%02d", number))
                    .equals(question.path("seedKey").asText()), "question sequence");
            JsonNode options = question.at("/questionContent/options");
            require(options.size() == 4, "option set");
            Set<String> optionIds = new HashSet<>();
            for (int optionIndex = 0; optionIndex < 4; optionIndex++) {
                JsonNode option = options.get(optionIndex);
                require(("opt_" + (optionIndex + 1)).equals(option.path("id").asText())
                        && !option.path("text").asText().isBlank()
                        && option.path("imageReference").isNull()
                        && optionIds.add(option.path("id").asText()), "option set");
            }
            JsonNode answerIds = question.at("/answerSpec/correctOptionIds");
            require(answerIds.size() == 1, "answer cardinality");
            String answerId = answerIds.get(0).asText();
            int answer = Integer.parseInt(answerId.substring(answerId.length() - 1));
            actualAnswers.add(answer);
            require(answer == ANSWERS[index] && optionIds.contains(answerId), "answer vector");

            String groupId = question.path("groupId").asText();
            JsonNode group = groups.get(groupId);
            require(group != null && contains(group.path("questionNumbers"), number),
                    "question group binding");
            JsonNode provenance = question.path("provenance");
            require(provenance.path("questionPdfPage").asInt()
                    == group.path("sourcePdfPage").asInt()
                    && provenance.path("questionPrintedPage").asInt()
                    == group.path("sourcePrintedPage").asInt()
                    && provenance.path("questionPdfPage").asInt()
                    == pageByQuestion.get(number)
                    && provenance.path("answerPdfPage").asInt() == 3
                    && provenance.path("answerRowQuestion").asInt() == number,
                    "question provenance");
            require("question-content-v3".equals(
                    question.at("/questionContent/schemaVersion").asText())
                    && "answer-spec-v1".equals(
                    question.at("/answerSpec/schemaVersion").asText())
                    && "SINGLE_CHOICE".equals(question.path("questionType").asText())
                    && "ALL_OR_NOTHING".equals(
                    question.at("/answerSpec/scoringPolicyCode").asText()),
                    "typed contract");
        }
        require(actualAnswers.equals(java.util.Arrays.stream(ANSWERS).boxed().toList()),
                "answer vector");
    }

    private static boolean assetMatches(JsonNode asset, String id, int question,
                                        String sha, int size, int width, int height,
                                        int cropX, int cropY) {
        JsonNode crop = asset.at("/source/cropPixels");
        return id.equals(asset.path("assetId").asText())
                && asset.path("questionNumbers").size() == 1
                && asset.path("questionNumbers").get(0).asInt() == question
                && sha.equals(asset.path("sha256").asText())
                && asset.path("logicalKey").asText().equals(
                "practice-seed/topik35-v1/derived/page-image/" + sha + ".png")
                && asset.path("sizeBytes").asInt() == size
                && asset.path("width").asInt() == width
                && asset.path("height").asInt() == height
                && asset.at("/source/pdfPage").asInt() == 5
                && asset.at("/source/printedPage").asInt() == 3
                && asset.at("/source/renderDpi").asInt() == 200
                && crop.path("x").asInt() == cropX
                && crop.path("y").asInt() == cropY
                && crop.path("width").asInt() == width
                && crop.path("height").asInt() == height
                && "VISUALLY_VERIFIED".equals(asset.path("qaStatus").asText());
    }

    private static void assertAsset(JsonNode asset, String id, int question,
                                    String sha, int size, int width, int height,
                                    int cropX, int cropY) {
        assertThat(assetMatches(asset, id, question, sha, size, width, height, cropX, cropY))
                .isTrue();
    }

    private String contentProjectionSha256(JsonNode root) throws Exception {
        ObjectNode projection = objectMapper.createObjectNode();
        projection.set("passageGroups", root.path("passageGroups"));
        projection.set("questions", root.path("questions"));
        projection.set("visualAssets", root.path("visualAssets"));
        byte[] canonical = objectMapper.writeValueAsString(sortObjectFields(projection))
                .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical));
    }

    private JsonNode sortObjectFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> sorted.set(name, sortObjectFields(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sortObjectFields(value)));
            return sorted;
        }
        return node.deepCopy();
    }

    private static boolean contains(JsonNode array, int value) {
        for (JsonNode node : array) if (node.asInt() == value) return true;
        return false;
    }

    private static int occurrences(String text, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
