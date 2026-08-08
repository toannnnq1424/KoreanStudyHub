package com.ksh.features.practice.ai.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.contract.PracticeAiResultCompleteness;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WritingFeedbackContractParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingFeedbackContractParser parser =
            new WritingFeedbackContractParser(objectMapper);

    @Test
    void parsesCurrentScoredEntryWithoutPrecisionLoss() throws Exception {
        JsonNode node = currentScored("0.12345678901234567890", "10");

        WritingFeedbackContractParser.EntryResult result =
                parser.parseStoredEntry(node);

        assertEquals(WritingFeedbackContractParser.Status.VALID_CURRENT,
                result.status());
        assertEquals(new BigDecimal("0.12345678901234567890"),
                result.value().rawScore());
    }

    @Test
    void parsesExplicitCurrentNonScoreBearingEntry() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"evaluation_status":"EVALUATION_UNAVAILABLE",
                 "evaluation_source":"PROVIDER","evaluation_reason":"HTTP_ERROR",
                 "evaluation_retryable":true,"score_available":false,
                 "result_completeness":{"version":"practice-ai-result-completeness-v1",
                   "status":"UNAVAILABLE","reason_code":"HTTP_ERROR",
                   "rejected_item_count":0},
                 "task_type":"Q51","engine":"KSH_WRITING_EVALUATOR_V2",
                 "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                 "policy_bundle_id":"KSH_WRITING_POLICY_BUNDLE_V3"}
                """);

        assertEquals(WritingFeedbackContractParser.Status.VALID_CURRENT,
                parser.parseStoredEntry(node).status());
    }

    @Test
    void legacyFlatAndImplicitScorePayloadsFailClosed() throws Exception {
        JsonNode flat = objectMapper.readTree(
                "{\"raw_score\":8,\"raw_score_max\":10,\"student_text\":\"old\"}");

        assertEquals(WritingFeedbackContractParser.Status.MALFORMED,
                parser.parseStoredEntry(flat).status());
        assertEquals(WritingFeedbackContractParser.Status.MALFORMED,
                parser.parseRoot(flat, List.of(101L)).status());
    }

    @Test
    void partialDiagnosticsRemainExplicitlyNonScoreBearing() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"evaluation_status":"EVALUATION_PARTIAL_NON_SCORE",
                 "evaluation_source":"PROVIDER",
                 "evaluation_reason":"DIAGNOSTIC_ITEMS_REJECTED",
                 "evaluation_retryable":false,"score_available":false,
                 "result_completeness":{
                   "version":"practice-ai-result-completeness-v1",
                   "status":"PARTIAL_NON_SCORE",
                   "reason_code":"DIAGNOSTIC_ITEMS_REJECTED",
                   "rejected_item_count":2},
                 "task_type":"Q53","engine":"KSH_WRITING_EVALUATOR_V3",
                 "scoring_contract":"TASK_NATIVE_RUBRIC_V1",
                 "policy_bundle_id":"KSH_WRITING_POLICY_BUNDLE_V3"}
                """);

        var result = parser.parseStoredEntry(node);

        assertEquals(WritingFeedbackContractParser.Status.VALID_CURRENT,
                result.status());
        assertEquals(false, result.value().scoreAvailableFlag());
        assertEquals(2,
                result.value().completeness().rejectedItemCount());
    }

    @Test
    void malformedRangeAndMissingTypedMetadataFailClosed() throws Exception {
        JsonNode outOfRange = currentScored("11", "10");
        JsonNode missingMetadata = objectMapper.readTree(
                "{\"raw_score\":8,\"raw_score_max\":10}");

        assertEquals(WritingFeedbackContractParser.Status.MALFORMED,
                parser.parseGeneratedEntry(outOfRange).status());
        assertEquals(WritingFeedbackContractParser.Status.MALFORMED,
                parser.parseStoredEntry(missingMetadata).status());
    }

    @Test
    void currentPerQuestionMapRequiresEveryRequestedEntry() throws Exception {
        JsonNode first = currentScored("8", "10");
        JsonNode second = currentScored("7", "10");
        var root = objectMapper.createObjectNode();
        root.set("101", first);
        root.set("102", second);

        WritingFeedbackContractParser.FeedbackResult valid =
                parser.parseRoot(root, List.of(101L, 102L));
        assertEquals(WritingFeedbackContractParser.Status.VALID_CURRENT,
                valid.status());
        root.remove("102");
        assertEquals(WritingFeedbackContractParser.Status.MALFORMED,
                parser.parseRoot(root, List.of(101L, 102L)).status());
    }

    @Test
    void missingAndInvalidJsonAreExplicit() {
        assertEquals(WritingFeedbackContractParser.Status.MISSING,
                parser.parsePayload(null, List.of(101L)).status());
        assertEquals(WritingFeedbackContractParser.Status.MALFORMED,
                parser.parsePayload("{not-json", List.of(101L)).status());
    }

    private JsonNode currentScored(String raw, String maximum)
            throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("raw_score", new BigDecimal(raw));
        node.put("raw_score_max", new BigDecimal(maximum));
        node.put("evaluation_status", "EVALUATED");
        node.put("evaluation_source", "PROVIDER");
        node.put("evaluation_reason", "NONE");
        node.put("evaluation_retryable", false);
        node.put("score_available", true);
        node.set(PracticeAiResultCompleteness.FIELD,
                objectMapper.valueToTree(
                        PracticeAiResultCompleteness.complete().toMap()));
        node.put("task_type", "Q51");
        node.put("engine", "KSH_WRITING_EVALUATOR_V2");
        node.put("scoring_contract", "TASK_NATIVE_RUBRIC_V1");
        node.put("policy_bundle_id", "KSH_WRITING_POLICY_BUNDLE_V3");
        return node;
    }
}
