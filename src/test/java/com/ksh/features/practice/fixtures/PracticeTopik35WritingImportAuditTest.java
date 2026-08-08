package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.writing.WritingTaskRequirementPolicy;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.WritingBlankContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeTopik35WritingImportAuditTest {
    private static final Path PACKAGE = Path.of(
            "docs/operations/practice-topik35-writing-import-audit.json");
    private static final Path SCHEMA = Path.of(
            "docs/operations/practice-topik35-writing-import-audit.schema.json");
    private static final int[] POINTS = {10, 10, 30, 50};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PracticeContentRules contentRules = new PracticeContentRules();

    @Test
    void sourceIdentityAndCurrentTypedWritingContractArePinned() throws Exception {
        JsonNode root = read(PACKAGE);

        validate(root);
        assertThat(root.at("/sourceBindings/questionDocument/sha256").asText())
                .isEqualTo("cc21da6b877b27f0d7d3550c732282d610dadcea03a80842881f17eda3d51323");
        assertThat(root.at("/sourceBindings/answerDocument/sha256").asText())
                .isEqualTo("60fb5fa5e5a211609d0d6e36bc1cacc490c34ff5a0009e6952e931f9a850d17b");
        assertThat(root.at("/targetContract/questionContentSchema").asText())
                .isEqualTo(QuestionContent.SCHEMA_VERSION_V3);
        assertThat(root.at("/targetContract/structuredBlankSchema").asText())
                .isEqualTo(WritingBlankContract.RESPONSE_SCHEMA_VERSION);
        assertThat(root.at("/targetContract/structuredBlankAuthoritySchema").asText())
                .isEqualTo(WritingBlankContract.AUTHORITY_SCHEMA_VERSION);

        for (int index = 0; index < 4; index++) {
            WritingTaskType task = WritingTaskType.values()[index];
            JsonNode question = root.path("questions").get(index);
            assertThat(question.path("questionNumber").asInt())
                    .isEqualTo(contentRules.writingQuestionNumber(task));
            assertThat(new BigDecimal(question.path("points").asText()))
                    .isEqualByComparingTo(contentRules.writingTaskPolicy(task).points());
            assertThat(question.path("taskType").asText()).isEqualTo(task.name());
        }
    }

    @Test
    void promptsAnswersAndQuestionShapeMatchTheVisuallyReviewedPages() throws Exception {
        JsonNode questions = read(PACKAGE).path("questions");

        assertThat(questions.get(0).path("promptText").asText())
                .contains("무료로 드립니다", "010-1234-5678", "[BLANK_1]", "[BLANK_2]");
        assertThat(questions.get(1).path("promptText").asText())
                .contains("퍼즐은 여러 개의 조각", "사회와 개인의 관계");
        for (int index = 0; index < 2; index++) {
            JsonNode question = questions.get(index);
            assertThat(question.path("responseMode").asText())
                    .isEqualTo("STRUCTURED_BLANKS");
            assertThat(question.path("blankDefinitions")).hasSize(2);
            assertThat(question.at("/answerExpectation/modelAnswers")).hasSize(2);
            assertThat(question.at("/answerExpectation/exactOnly").asBoolean()).isFalse();
            assertThat(question.at("/answerExpectation/semanticEquivalentAlternativesApproved")
                    .asBoolean()).isFalse();
        }

        assertThat(questions.get(2).path("promptInstruction").asText())
                .contains("200~300자", "연령대", "공공시설");
        assertThat(questions.get(2).at("/answerExpectation/modelAnswerPresent")
                .asBoolean()).isTrue();
        assertThat(questions.get(3).path("promptInstruction").asText())
                .contains("600~700자");
        assertThat(questions.get(3).at("/answerExpectation/requiredPromptCoverage"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "DEFINE_HAPPY_LIFE",
                        "EXPLAIN_ECONOMIC_CONDITIONS_AND_HAPPINESS_RELATIONSHIP",
                        "PROPOSE_EFFORTS_TO_INCREASE_HAPPINESS",
                        "LENGTH_600_700_KOREAN_CHARACTERS");
    }

    @Test
    void q53ChartAssetHasExactCropDigestAndDataAuthority() throws Exception {
        JsonNode root = read(PACKAGE);
        JsonNode asset = root.path("assetBindings").get(0);
        JsonNode chart = root.at("/questions/2/answerExpectation/chartData");

        assertThat(asset.path("assetId").asText()).isEqualTo("topik35-writing-q53-chart");
        assertThat(asset.path("logicalKey").asText())
                .isEqualTo("practice-seed/topik35-v1/derived/page-image/"
                        + "47977060c3255f13f67d3f041bfe2d998dc3e0f13e23830e3527363ae8b4bee1.png");
        assertThat(asset.path("sizeBytes").asLong()).isEqualTo(143548);
        assertThat(asset.at("/cropRectPx/x").asInt()).isEqualTo(295);
        assertThat(asset.at("/cropRectPx/y").asInt()).isEqualTo(395);
        assertThat(asset.at("/cropRectPx/width").asInt()).isEqualTo(1120);
        assertThat(asset.at("/cropRectPx/height").asInt()).isEqualTo(620);
        assertThat(root.at("/questions/2/assetIds/0").asText())
                .isEqualTo(asset.path("assetId").asText());

        assertThat(sum(chart.path("age30"))).isEqualTo(100);
        assertThat(sum(chart.path("age60"))).isEqualTo(100);
        assertThat(chart.at("/age30/performanceCultureCenter").asInt()).isEqualTo(40);
        assertThat(chart.at("/age60/hospitalPharmacy").asInt()).isEqualTo(50);
        assertThat(chart.at("/age30/park").asInt()).isEqualTo(22);
        assertThat(chart.at("/age60/park").asInt()).isEqualTo(22);
    }

    @Test
    void rubricAuditDoesNotTreatInternalSubweightsOrWrongQ53RequirementsAsSource()
            throws Exception {
        JsonNode audit = read(PACKAGE).path("rubricAudit");
        Set<String> currentQ53Requirements = new HashSet<>();
        WritingTaskRequirementPolicy.requirementsFor("Q53")
                .forEach(requirement -> currentQ53Requirements.add(requirement.requirementId()));

        assertThat(audit.at("/internalProfile/profileId").asText())
                .isEqualTo(WritingScoringPolicy.PROFILE_ID);
        assertThat(audit.at("/internalProfile/contract").asText())
                .isEqualTo(WritingScoringPolicy.SCORING_CONTRACT);
        assertThat(audit.path("sourcePublishedDetailedSubweights").asBoolean()).isFalse();
        assertThat(audit.at("/internalProfile/bindingStatus").asText())
                .isEqualTo("NOT_BOUND_SOURCE_DOES_NOT_AUTHORIZE_INTERNAL_SUBWEIGHTS");
        assertThat(audit.at("/taskRequirementProfile/version").asText())
                .isEqualTo(WritingTaskRequirementPolicy.VERSION);
        assertThat(audit.at("/taskRequirementProfile/q53BindingStatus").asText())
                .isEqualTo("REJECTED_SOURCE_INCOMPATIBLE");
        assertThat(currentQ53Requirements)
                .contains("Q53_FOUR_TRANSPORT_MODES", "Q53_DATA_2024", "Q53_DATA_2026");
        assertThat(audit.at("/taskRequirementProfile/q53MismatchEvidence"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(currentQ53Requirements.stream()
                        .filter(id -> id.equals("Q53_FOUR_TRANSPORT_MODES")
                                || id.equals("Q53_DATA_2024")
                                || id.equals("Q53_DATA_2026"))
                        .toList());
    }

    @Test
    void validatorRejectsPromptAnswerAssetRubricAndReadinessTampering() throws Exception {
        ObjectNode blankPrompt = copy();
        ((ObjectNode) blankPrompt.path("questions").get(0)).put("promptText", "");
        assertThatThrownBy(() -> validate(blankPrompt)).hasMessageContaining("prompt");

        ObjectNode exactOnly = copy();
        ((ObjectNode) exactOnly.at("/questions/0/answerExpectation")).put("exactOnly", true);
        assertThatThrownBy(() -> validate(exactOnly)).hasMessageContaining("answer authority");

        ObjectNode wrongChart = copy();
        ((ObjectNode) wrongChart.at("/questions/2/answerExpectation/chartData/age60"))
                .put("hospitalPharmacy", 49);
        assertThatThrownBy(() -> validate(wrongChart)).hasMessageContaining("chart data");

        ObjectNode wrongCrop = copy();
        ((ObjectNode) wrongCrop.at("/assetBindings/0/cropRectPx")).put("x", 296);
        assertThatThrownBy(() -> validate(wrongCrop)).hasMessageContaining("asset crop");

        ObjectNode boundRubric = copy();
        ((ObjectNode) boundRubric.at("/rubricAudit/internalProfile"))
                .put("bindingStatus", "BOUND");
        assertThatThrownBy(() -> validate(boundRubric)).hasMessageContaining("rubric gate");

        ObjectNode falseReady = copy();
        ((ObjectNode) falseReady.path("loadPolicy")).put("loadReady", true);
        assertThatThrownBy(() -> validate(falseReady)).hasMessageContaining("load gate");
    }

    @Test
    void packageUsesLogicalKeysOnlyAndSchemaIsClosed() throws Exception {
        String raw = Files.readString(PACKAGE);
        JsonNode schema = read(SCHEMA);

        assertThat(raw).doesNotContain(
                "/Users/", "/tmp/", "uploads/", "file://", "s3://", "r2://",
                ".r2.cloudflarestorage.com", "bucketName", "rawScore", "scoreValue");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/question/additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/asset/additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/rubricAudit/additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/$defs/loadPolicy/additionalProperties").asBoolean()).isFalse();
    }

    private JsonNode read(Path path) throws Exception {
        return objectMapper.readTree(Files.readString(path));
    }

    private ObjectNode copy() throws Exception {
        return (ObjectNode) read(PACKAGE);
    }

    private static int sum(JsonNode node) {
        int total = 0;
        for (JsonNode value : node) total += value.asInt();
        return total;
    }

    private static void validate(JsonNode root) {
        require("practice-topik35-writing-import-audit-v1".equals(
                root.path("schemaVersion").asText()), "schema identity");
        require(root.path("questions").size() == 4, "question count");
        require(root.path("assetBindings").size() == 1, "asset count");

        for (int index = 0; index < 4; index++) {
            JsonNode question = root.path("questions").get(index);
            int number = 51 + index;
            require(question.path("questionNumber").asInt() == number
                    && ("Q" + number).equals(question.path("taskType").asText()),
                    "question identity");
            require(question.path("points").asInt() == POINTS[index], "point map");
            require(!question.path("promptInstruction").asText().isBlank()
                    && !question.path("promptText").asText().isBlank(), "prompt content");
            require("VISUALLY_VERIFIED".equals(question.path("sourceQaStatus").asText()),
                    "prompt QA");
            if (number <= 52) {
                require("STRUCTURED_BLANKS".equals(question.path("responseMode").asText())
                        && question.path("blankDefinitions").size() == 2,
                        "structured blanks");
                JsonNode authority = question.path("answerExpectation");
                require(authority.path("modelAnswers").size() == 2
                        && !authority.path("exactOnly").asBoolean()
                        && !authority.path("semanticEquivalentAlternativesApproved").asBoolean(),
                        "answer authority gate");
            } else {
                require("ESSAY_TEXT".equals(question.path("responseMode").asText())
                        && question.path("blankDefinitions").isEmpty(), "essay response");
            }
        }

        JsonNode asset = root.path("assetBindings").get(0);
        require(asset.path("logicalKey").asText().contains(
                        "/" + asset.path("sha256").asText() + ".png")
                && asset.path("sha256").asText().equals(
                        "47977060c3255f13f67d3f041bfe2d998dc3e0f13e23830e3527363ae8b4bee1")
                && asset.at("/cropRectPx/x").asInt() == 295
                && asset.at("/cropRectPx/y").asInt() == 395
                && asset.at("/cropRectPx/width").asInt() == 1120
                && asset.at("/cropRectPx/height").asInt() == 620,
                "asset crop and digest");

        JsonNode chart = root.at("/questions/2/answerExpectation/chartData");
        require(sum(chart.path("age30")) == 100 && sum(chart.path("age60")) == 100
                && chart.at("/age30/hospitalPharmacy").asInt() == 28
                && chart.at("/age30/performanceCultureCenter").asInt() == 40
                && chart.at("/age30/park").asInt() == 22
                && chart.at("/age30/other").asInt() == 10
                && chart.at("/age60/hospitalPharmacy").asInt() == 50
                && chart.at("/age60/performanceCultureCenter").asInt() == 23
                && chart.at("/age60/park").asInt() == 22
                && chart.at("/age60/other").asInt() == 5,
                "chart data authority");

        require(!root.at("/rubricAudit/sourcePublishedDetailedSubweights").asBoolean()
                && "NOT_BOUND_SOURCE_DOES_NOT_AUTHORIZE_INTERNAL_SUBWEIGHTS".equals(
                root.at("/rubricAudit/internalProfile/bindingStatus").asText())
                && "REJECTED_SOURCE_INCOMPATIBLE".equals(
                root.at("/rubricAudit/taskRequirementProfile/q53BindingStatus").asText()),
                "rubric gate");
        require(!root.at("/loadPolicy/loadReady").asBoolean()
                && !root.at("/loadPolicy/databaseLoadPerformed").asBoolean()
                && !root.at("/loadPolicy/objectStoreWritePerformed").asBoolean()
                && !root.at("/loadPolicy/providerCallPerformed").asBoolean()
                && !root.at("/loadPolicy/aiScoringInvoked").asBoolean()
                && !root.at("/loadPolicy/learnerScoresCreated").asBoolean()
                && !root.at("/validationSummary/loadReady").asBoolean(),
                "load gate");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
