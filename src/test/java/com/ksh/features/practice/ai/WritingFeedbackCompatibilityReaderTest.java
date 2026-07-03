package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WritingFeedbackCompatibilityReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingFeedbackCompatibilityReader reader = new WritingFeedbackCompatibilityReader(objectMapper);

    @Test
    void parsesValidCurrentEntryScoreFields() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"raw_score":8.0,"raw_score_max":10.0,"score":7.5,"overall_score":7.5,"task_type":"Q51","engine":"KSH_WRITING_EVALUATOR_V2"}
                """);

        WritingFeedbackCompatibilityReader.EntryResult result = reader.parseStoredEntry(node);

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, result.status());
        assertEquals(0, result.value().rawScore().compareTo(BigDecimal.valueOf(8.0)));
        assertEquals(0, result.value().rawScoreMax().compareTo(BigDecimal.valueOf(10.0)));
        assertEquals("Q51", result.value().taskType());
        assertEquals("KSH_WRITING_EVALUATOR_V2", result.value().engine());
    }

    @Test
    void parsesIntegerAndDecimalNumericNodes() throws Exception {
        JsonNode integerNode = objectMapper.readTree("{\"raw_score\":8,\"raw_score_max\":10}");
        JsonNode decimalNode = objectMapper.readTree("{\"raw_score\":8.25,\"raw_score_max\":10.5}");

        assertEquals(0, reader.parseStoredEntry(integerNode).value().rawScore().compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, reader.parseStoredEntry(decimalNode).value().rawScore().compareTo(BigDecimal.valueOf(8.25)));
        assertEquals(0, reader.parseStoredEntry(decimalNode).value().rawScoreMax().compareTo(BigDecimal.valueOf(10.5)));
    }

    @Test
    void preservesBigDecimalPrecisionWithoutDoubleConversion() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("raw_score", new BigDecimal("0.12345678901234567890"));
        node.put("raw_score_max", new BigDecimal("10.00000000000000000001"));

        WritingFeedbackCompatibilityReader.EntryResult result = reader.parseStoredEntry(node);

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, result.status());
        assertEquals(new BigDecimal("0.12345678901234567890"), result.value().rawScore());
        assertEquals(new BigDecimal("10.00000000000000000001"), result.value().rawScoreMax());
    }

    @Test
    void rejectsMissingTextualAndInvalidStoredScoreFields() throws Exception {
        assertEquals(WritingFeedbackCompatibilityReader.Status.MISSING, reader.parseStoredEntry(null).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score_max\":10}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":8}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":\"8\",\"raw_score_max\":10}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":8,\"raw_score_max\":\"10\"}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":8,\"raw_score_max\":0}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":8,\"raw_score_max\":-1}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":-1,\"raw_score_max\":10}")).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseStoredEntry(objectMapper.readTree("{\"raw_score\":11,\"raw_score_max\":10}")).status());
    }

    @Test
    void generatedEntryKeepsCurrentClampBehaviorForOutOfRangeRawScore() throws Exception {
        JsonNode negative = objectMapper.readTree("{\"raw_score\":-1,\"raw_score_max\":10}");
        JsonNode aboveMax = objectMapper.readTree("{\"raw_score\":11,\"raw_score_max\":10}");

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, reader.parseGeneratedEntry(negative).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, reader.parseGeneratedEntry(aboveMax).status());
    }

    @Test
    void optionalAndUnknownFieldsDoNotBreakStoredEntryParsing() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"raw_score":8,"raw_score_max":10,"unknown_field":{"nested":true}}
                """);

        WritingFeedbackCompatibilityReader.EntryResult result = reader.parseStoredEntry(node);

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, result.status());
        assertNotNull(result.value());
    }

    @Test
    void parsesValidCurrentQuestionMapInRequestedOrder() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"102":{"raw_score":7,"raw_score_max":10},"101":{"raw_score":8,"raw_score_max":10},"extra":{"ignored":true}}
                """);

        WritingFeedbackCompatibilityReader.FeedbackResult result = reader.parseRoot(root, List.of(101L, 102L));

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, result.status());
        assertEquals(List.of(101L, 102L), result.entries().keySet().stream().toList());
        assertEquals(0, result.entries().get(101L).rawScore().compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, result.entries().get(102L).rawScore().compareTo(BigDecimal.valueOf(7)));
    }

    @Test
    void malformedEntryInsideCurrentMapIsMalformed() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"101":{"raw_score":8,"raw_score_max":10},"102":{"raw_score":"bad","raw_score_max":10}}
                """);

        WritingFeedbackCompatibilityReader.FeedbackResult result = reader.parseRoot(root, List.of(101L, 102L));

        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED, result.status());
    }

    @Test
    void classifiesLegacyFlatSingleEssayAndMultiEssay() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"raw_score":8,"raw_score_max":10,"student_text":"answer"}
                """);

        WritingFeedbackCompatibilityReader.FeedbackResult single = reader.parseRoot(root, List.of(101L));
        WritingFeedbackCompatibilityReader.FeedbackResult multi = reader.parseRoot(root, List.of(101L, 102L));

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_LEGACY_SINGLE, single.status());
        assertEquals(0, single.entries().get(101L).rawScore().compareTo(BigDecimal.valueOf(8)));
        assertEquals(WritingFeedbackCompatibilityReader.Status.UNSUPPORTED_LEGACY_MULTI, multi.status());
    }

    @Test
    void missingNullBlankAndMalformedPayloadAreExplicit() {
        assertEquals(WritingFeedbackCompatibilityReader.Status.MISSING,
                reader.parsePayload(null, List.of(101L)).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MISSING,
                reader.parsePayload(" ", List.of(101L)).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parsePayload("{not-json", List.of(101L)).status());
        assertEquals(WritingFeedbackCompatibilityReader.Status.MALFORMED,
                reader.parseRoot(objectMapper.createArrayNode(), List.of(101L)).status());
    }

    @Test
    void readerDoesNotMutateSourceJson() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"101":{"raw_score":8,"raw_score_max":10,"unknown":{"kept":true}}}
                """);
        String before = root.toString();

        WritingFeedbackCompatibilityReader.FeedbackResult result = reader.parseRoot(root, List.of(101L));

        assertEquals(WritingFeedbackCompatibilityReader.Status.VALID_CURRENT, result.status());
        assertEquals(before, root.toString());
        assertFalse(result.entries().isEmpty());
    }
}
