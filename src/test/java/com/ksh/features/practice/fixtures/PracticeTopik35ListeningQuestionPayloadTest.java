package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeTopik35ListeningQuestionPayloadTest {
    private static final Path PAYLOAD = Path.of(
            "docs/operations/practice-topik35-listening-question-payload.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-listening-question-payload.schema.json");
    private static final Path SOURCE_PACKAGE = Path.of(
            "docs/operations/practice-topik35-listening-import-package.json");
    private static final String PROJECTION_SHA256 =
            "52249b9398f8950e948b7d449d0d1391f493e9113aaffa78e9ab8818e604df1f";
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
    private final AssessmentContractCodec codec = new AssessmentContractCodec(
            objectMapper, new QuestionTypeResolver());

    @Test
    void exactPayloadProjectionAndAnswerVectorArePinned() throws Exception {
        JsonNode root = payload();

        validate(root);
        assertThat(projectionSha256(root)).isEqualTo(PROJECTION_SHA256);
        assertThat(root.at("/validationSummary/contentProjectionSha256").asText())
                .isEqualTo(PROJECTION_SHA256);
        assertThat(root.path("questions")).hasSize(50);
        assertThat(root.path("visualAssets")).hasSize(12);
        assertThat(root.at("/validationSummary/optionCount").asInt()).isEqualTo(200);
        assertThat(root.at("/validationSummary/textOptionCount").asInt()).isEqualTo(188);
        assertThat(root.at("/validationSummary/imageOptionCount").asInt()).isEqualTo(12);
    }

    @Test
    void everyQuestionUsesTheCurrentTypedObjectiveContract() throws Exception {
        for (JsonNode question : payload().path("questions")) {
            QuestionContent content = objectMapper.treeToValue(
                    question.path("questionContent"), QuestionContent.class);
            AnswerSpec answer = objectMapper.treeToValue(
                    question.path("answerSpec"), AnswerSpec.class);

            assertThat(content.schemaVersion()).isEqualTo(QuestionContent.SCHEMA_VERSION_V3);
            assertThat(content.languageTag()).isEqualTo("ko");
            assertThat(content.options()).hasSize(4);
            assertThat(answer.schemaVersion()).isEqualTo(AnswerSpec.SCHEMA_VERSION);
            assertThat(answer.questionType()).isEqualTo(CanonicalQuestionType.SINGLE_CHOICE);
            assertThat(codec.writeQuestionContent(
                    content, CanonicalQuestionType.SINGLE_CHOICE)).isNotBlank();
            assertThat(codec.writeAnswerSpec(answer, content)).isNotBlank();
        }
    }

    @Test
    void imageOptionsBindExactDigestKeysAndSourceCrops() throws Exception {
        JsonNode root = payload();
        Map<String, JsonNode> assets = new HashMap<>();
        for (JsonNode asset : root.path("visualAssets")) {
            String id = asset.path("assetId").asText();
            assertThat(assets.put(id, asset)).isNull();
            assertThat(asset.path("logicalKey").asText())
                    .isEqualTo("practice-seed/topik35-v1/derived/page-image/"
                            + asset.path("sha256").asText() + ".png");
            assertThat(asset.path("width").asInt()).isEqualTo(480);
            assertThat(asset.at("/source/renderDpi").asInt()).isEqualTo(200);
            assertThat(asset.at("/source/cropPixels/width").asInt()).isEqualTo(480);
            assertThat(asset.path("qaStatus").asText()).isEqualTo("VISUALLY_VERIFIED");
        }

        for (int index = 0; index < root.path("questions").size(); index++) {
            int number = index + 1;
            JsonNode options = root.path("questions").get(index)
                    .path("questionContent").path("options");
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                JsonNode option = options.get(optionIndex);
                if (number <= 3) {
                    String assetId = "topik35-listening-q%02d-opt-%d"
                            .formatted(number, optionIndex + 1);
                    assertThat(option.path("imageReference").asText())
                            .isEqualTo(assets.get(assetId).path("logicalKey").asText());
                    assertThat(option.path("text").asText())
                            .isEqualTo("①②③④".substring(optionIndex, optionIndex + 1));
                } else {
                    assertThat(option.path("imageReference").isNull()).isTrue();
                    assertThat(option.path("text").asText()).isNotBlank();
                }
            }
        }
    }

    @Test
    void localIgnoredCropsMatchMetadataWhenPresent() throws Exception {
        Path localAssets = Path.of(
                "uploads/practice-seed/topik35/derived/page-image");
        Assumptions.assumeTrue(Files.isDirectory(localAssets));

        for (JsonNode asset : payload().path("visualAssets")) {
            Path file = localAssets.resolve(asset.path("sha256").asText() + ".png");
            assertThat(file).exists();
            byte[] bytes = Files.readAllBytes(file);
            assertThat(bytes).hasSize(asset.path("sizeBytes").asInt());
            assertThat(sha256(bytes)).isEqualTo(asset.path("sha256").asText());
            BufferedImage image = ImageIO.read(file.toFile());
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isEqualTo(asset.path("width").asInt());
            assertThat(image.getHeight()).isEqualTo(asset.path("height").asInt());
        }
    }

    @Test
    void sourcePackageBindsPayloadButKeepsExamAndLoadGatesClosed() throws Exception {
        JsonNode source = objectMapper.readTree(Files.readString(SOURCE_PACKAGE));

        assertThat(source.at("/questionPayloadBinding/packageId").asText())
                .isEqualTo("topik35-v1-listening-question-payload-v1");
        assertThat(source.at("/validationSummary/payloadQuestionCount").asInt())
                .isEqualTo(50);
        assertThat(source.at("/validationSummary/payloadOptionCount").asInt())
                .isEqualTo(200);
        assertThat(source.at("/validationSummary/payloadVisualAssetCount").asInt())
                .isEqualTo(12);
        assertThat(source.at("/targetContract/candidateMaterialized").asBoolean()).isFalse();
        assertThat(source.at("/validationSummary/loadReady").asBoolean()).isFalse();
        assertThat(source.path("materializationBlockers").toString())
                .contains("GROUP_TRANSCRIPT_TEXT_NOT_MATERIALIZED_BY_THIS_SLICE")
                .contains("GROUP_TIMING_RANGES_NOT_YET_MANUALLY_VERIFIED")
                .doesNotContain("QUESTION_PROMPT_AND_OPTION_PAYLOAD_NOT_YET_TRANSCRIBED",
                        "IMAGE_OPTION_PAGE_ASSETS_Q01_Q03_NOT_YET_DERIVED");
        assertThat(source.at("/playbackPolicy/singleOrderedAudioProgram").asBoolean())
                .isTrue();
        assertThat(source.at("/playbackPolicy/seekAllowed").asBoolean()).isFalse();
        assertThat(source.at("/playbackPolicy/replayAllowed").asBoolean()).isFalse();
        assertThat(source.at("/playbackPolicy/timestampAutoNavigation").asBoolean())
                .isFalse();
    }

    @Test
    void adversarialPromptOptionAnswerAssetAndReadinessMutationsFailClosed()
            throws Exception {
        ObjectNode blankPrompt = copy();
        ((ObjectNode) blankPrompt.path("questions").get(20)).put("prompt", " ");
        assertThatThrownBy(() -> validate(blankPrompt)).hasMessageContaining("prompt");

        ObjectNode missingOption = copy();
        ((ArrayNode) missingOption.path("questions").get(3)
                .path("questionContent").path("options")).remove(3);
        assertThatThrownBy(() -> validate(missingOption)).hasMessageContaining("options");

        ObjectNode wrongAnswer = copy();
        ((ArrayNode) wrongAnswer.path("questions").get(0)
                .path("answerSpec").path("correctOptionIds"))
                .removeAll().add("opt_1");
        assertThatThrownBy(() -> validate(wrongAnswer)).hasMessageContaining("answer vector");

        ObjectNode localPath = copy();
        ((ObjectNode) localPath.path("questions").get(0)
                .path("questionContent").path("options").get(0))
                .put("imageReference", "/tmp/q01.png");
        assertThatThrownBy(() -> validate(localPath)).hasMessageContaining("image key");

        ObjectNode digestMismatch = copy();
        ((ObjectNode) digestMismatch.path("visualAssets").get(0))
                .put("sha256", "0".repeat(64));
        assertThatThrownBy(() -> validate(digestMismatch)).hasMessageContaining("asset key");

        ObjectNode falseReady = copy();
        ((ObjectNode) falseReady.path("validationSummary")).put("loadReady", true);
        assertThatThrownBy(() -> validate(falseReady)).hasMessageContaining("load gate");
    }

    @Test
    void schemaIsClosedAndPayloadContainsNoLocalOrDeliveryIdentity() throws Exception {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA));
        String raw = Files.readString(PAYLOAD);

        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/visualAsset/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/question/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/questionContent/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/$defs/answerSpec/additionalProperties").asBoolean())
                .isFalse();
        assertThat(raw).doesNotContain(
                "/Users/", "/tmp/", "uploads/", "file://", "s3://", "r2://",
                ".r2.cloudflarestorage.com", "bucketName", "audio_url");
    }

    private JsonNode payload() throws Exception {
        return objectMapper.readTree(Files.readString(PAYLOAD));
    }

    private ObjectNode copy() throws Exception {
        return (ObjectNode) payload();
    }

    private static void validate(JsonNode root) {
        require("practice-topik35-listening-question-payload-v1".equals(
                root.path("schemaVersion").asText()), "schema identity");
        require("QUESTION_PAYLOAD_AND_VISUAL_QA_COMPLETE_LOAD_BLOCKED".equals(
                root.path("status").asText()), "status");
        require(root.path("questions").size() == 50, "question count");
        require(root.path("visualAssets").size() == 12, "visual asset count");
        require(root.path("qaBlockers").isEmpty(), "unexpected QA blockers");

        Map<String, JsonNode> assets = new HashMap<>();
        Set<String> assetQuestionOptions = new HashSet<>();
        for (JsonNode asset : root.path("visualAssets")) {
            String sha = asset.path("sha256").asText();
            String key = asset.path("logicalKey").asText();
            require(sha.matches("[0-9a-f]{64}")
                    && key.equals("practice-seed/topik35-v1/derived/page-image/"
                    + sha + ".png"), "asset key digest");
            require(asset.path("sizeBytes").asLong() > 0
                    && asset.path("width").asInt() == 480
                    && asset.path("height").asInt() > 0, "asset metadata");
            String relation = asset.path("questionNumber").asInt()
                    + ":" + asset.path("optionId").asText();
            require(assetQuestionOptions.add(relation), "duplicate asset relation");
            require(assets.put(key, asset) == null, "duplicate asset key");
        }

        for (int index = 0; index < root.path("questions").size(); index++) {
            int number = index + 1;
            JsonNode question = root.path("questions").get(index);
            require(question.path("questionNumber").asInt() == number,
                    "question order");
            require(groupId(number).equals(question.path("groupId").asText()),
                    "question group");
            require(!question.path("prompt").asText().isBlank(), "prompt");
            require(question.path("points").asInt() == 2
                    && "SINGLE_CHOICE".equals(question.path("questionType").asText()),
                    "question identity");
            JsonNode content = question.path("questionContent");
            require("question-content-v3".equals(content.path("schemaVersion").asText())
                    && "ko".equals(content.path("languageTag").asText()),
                    "content identity");
            JsonNode options = content.path("options");
            require(options.size() == 4, "exact four options");
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                JsonNode option = options.get(optionIndex);
                require(("opt_" + (optionIndex + 1)).equals(option.path("id").asText())
                        && !option.path("text").asText().isBlank(), "option identity");
                if (number <= 3) {
                    String key = option.path("imageReference").asText();
                    require(assets.containsKey(key), "image key");
                    require((number + ":opt_" + (optionIndex + 1)).equals(
                            assets.get(key).path("questionNumber").asInt()
                                    + ":" + assets.get(key).path("optionId").asText()),
                            "image relation");
                } else {
                    require(option.path("imageReference").isNull(), "text option image key");
                }
            }
            String answer = question.at("/answerSpec/correctOptionIds/0").asText();
            require(answer.equals("opt_" + ANSWERS[index]), "answer vector");
            require(question.at("/provenance/questionPdfPage").asInt()
                    == questionPdfPage(number), "question page provenance");
            require(question.at("/provenance/answerRowQuestion").asInt() == number,
                    "answer row provenance");
            require(question.path("ambiguityRecordId").isNull()
                    && "VISUALLY_VERIFIED".equals(question.path("qaStatus").asText()),
                    "question QA state");
        }
        require(assetQuestionOptions.size() == 12, "image option coverage");
        require(root.at("/validationSummary/payloadQaComplete").asBoolean()
                && !root.at("/validationSummary/loadReady").asBoolean(), "load gate");
        require(root.path("remainingLoadBlockers").size() == 3, "remaining blockers");
    }

    private static String projectionSha256(JsonNode root) throws Exception {
        StringBuilder projection = new StringBuilder();
        for (JsonNode question : root.path("questions")) {
            appendTsv(projection,
                    question.path("questionNumber"), question.path("seedKey"),
                    question.path("groupId"), question.path("prompt"),
                    question.at("/questionContent/options/0/text"),
                    question.at("/questionContent/options/1/text"),
                    question.at("/questionContent/options/2/text"),
                    question.at("/questionContent/options/3/text"),
                    question.at("/answerSpec/correctOptionIds/0"),
                    question.at("/provenance/questionPdfPage"),
                    question.at("/provenance/questionPrintedPage"));
        }
        for (JsonNode asset : root.path("visualAssets")) {
            appendTsv(projection,
                    asset.path("assetId"), asset.path("logicalKey"),
                    asset.path("sha256"), asset.path("sizeBytes"),
                    asset.path("width"), asset.path("height"),
                    asset.at("/source/pdfPage"), asset.at("/source/printedPage"),
                    asset.at("/source/renderDpi"), asset.at("/source/cropPixels/x"),
                    asset.at("/source/cropPixels/y"),
                    asset.at("/source/cropPixels/width"),
                    asset.at("/source/cropPixels/height"));
        }
        return sha256(projection.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void appendTsv(StringBuilder target, JsonNode... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) target.append('\t');
            target.append(values[index].asText());
        }
        target.append('\n');
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bytes));
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
