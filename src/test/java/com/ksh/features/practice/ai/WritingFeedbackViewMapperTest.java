package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionFeedbackRow;
import com.ksh.features.practice.dto.PracticeDtos.WritingFeedbackView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingFeedbackViewMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingFeedbackViewMapper mapper = new WritingFeedbackViewMapper();

    @Test
    void mapsAllConfirmedFieldsAndPreservesOrder() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "raw_score":8.5,
                  "raw_score_max":10,
                  "score":7.25,
                  "summary":"summary",
                  "summary_vi":"summary vi",
                  "rubric_scores":[
                    {"name":"content","score":8,"feedback":"good"},
                    {"name":"language","score":7.5,"feedback":"ok"}
                  ],
                  "strengths":[
                    {"category":"cat1","vietnameseLabel":"vi1","uiLabel":"ui1","criterionId":"c1","evidence":"ev1","explanationVi":"ex1","correction":"","severity":"LOW","errorType":"","whyItIsGood":"why1","topikTip":"tip1"},
                    {"category":"cat2","criterionId":"c2","evidence":"ev2","explanationVi":"ex2"}
                  ],
                  "needs_improvement":[
                    {"category":"need1","vietnameseLabel":"nvi1","uiLabel":"nui1","criterionId":"n1","evidence":"nev1","explanationVi":"nex1","correction":"fix","severity":"HIGH","errorType":"grammar","topikTip":"ntip"}
                  ],
                  "annotations":[
                    {"id":"ann-1","kind":"strength","criterionId":"c1","category":"cat1","start":1,"end":5,"severity":"LOW","displayType":"WORD","index":1,"explanationVi":"ann ex","correction":"","evidence":"ev1"},
                    {"id":"ann-2","kind":"need","start":6,"end":9,"index":2}
                  ],
                  "upgraded_answer":"better answer",
                  "sentence_rewrites":[
                    {"original":"old1","upgraded":"new1","reason":"because1"},
                    {"original":"old2","upgraded":"new2","reason":"because2"}
                  ],
                  "sample_answer":"sample"
                }
                """);

        WritingFeedbackView view = mapper.map(node);

        assertNotNull(view);
        assertEquals(0, view.rawScore().compareTo(BigDecimal.valueOf(8.5)));
        assertEquals(0, view.rawScoreMax().compareTo(BigDecimal.TEN));
        assertEquals(0, view.score().compareTo(BigDecimal.valueOf(7.25)));
        assertEquals("summary", view.summary());
        assertEquals("summary vi", view.summaryVi());
        assertEquals("content", view.rubricScores().get(0).name());
        assertEquals("language", view.rubricScores().get(1).name());
        assertEquals("cat1", view.strengths().get(0).category());
        assertEquals("cat2", view.strengths().get(1).category());
        assertEquals("need1", view.needsImprovement().get(0).category());
        assertEquals("ann-1", view.annotations().get(0).id());
        assertEquals(Integer.valueOf(1), view.annotations().get(0).start());
        assertEquals(Integer.valueOf(5), view.annotations().get(0).end());
        assertEquals(Integer.valueOf(1), view.annotations().get(0).index());
        assertEquals("ann-2", view.annotations().get(1).id());
        assertEquals("better answer", view.upgradedAnswer());
        assertEquals("old1", view.sentenceRewrites().get(0).original());
        assertEquals("old2", view.sentenceRewrites().get(1).original());
        assertEquals("sample", view.sampleAnswer());
    }

    @Test
    void mapsIntegerDecimalAndBigDecimalScoresWithoutDoubleConversion() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("raw_score", new BigDecimal("0.12345678901234567890"));
        node.put("raw_score_max", 10);
        node.put("score", new BigDecimal("7.00000000000000000001"));

        WritingFeedbackView view = mapper.map(node);

        assertEquals(new BigDecimal("0.12345678901234567890"), view.rawScore());
        assertEquals(0, view.rawScoreMax().compareTo(BigDecimal.TEN));
        assertEquals(new BigDecimal("7.00000000000000000001"), view.score());
    }

    @Test
    void textualNumbersAndNonTextScalarsBecomeNull() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"raw_score":"8","raw_score_max":true,"score":{},"summary":7,"summary_vi":null,"upgraded_answer":["bad"],"sample_answer":false}
                """);

        WritingFeedbackView view = mapper.map(node);

        assertNull(view.rawScore());
        assertNull(view.rawScoreMax());
        assertNull(view.score());
        assertNull(view.summary());
        assertNull(view.summaryVi());
        assertNull(view.upgradedAnswer());
        assertNull(view.sampleAnswer());
    }

    @Test
    void optionalArraysDefaultEmptyAndIgnoreNonObjectElements() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "rubric_scores":"bad",
                  "strengths":null,
                  "needs_improvement":{},
                  "annotations":[1, {"id":"ok","start":1.5,"end":2,"index":"bad"}],
                  "sentence_rewrites":[true, {"original":"o"}]
                }
                """);

        WritingFeedbackView view = mapper.map(node);

        assertTrue(view.rubricScores().isEmpty());
        assertTrue(view.strengths().isEmpty());
        assertTrue(view.needsImprovement().isEmpty());
        assertEquals(1, view.annotations().size());
        assertEquals("ok", view.annotations().get(0).id());
        assertNull(view.annotations().get(0).start());
        assertEquals(Integer.valueOf(2), view.annotations().get(0).end());
        assertNull(view.annotations().get(0).index());
        assertEquals(1, view.sentenceRewrites().size());
        assertEquals("o", view.sentenceRewrites().get(0).original());
        assertNull(view.sentenceRewrites().get(0).upgraded());
    }

    @Test
    void unknownFieldsAreIgnoredAndInputIsNotMutated() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"raw_score":8,"raw_score_max":10,"unknown":{"nested":true},"rubric_scores":[{"name":"n","score":8,"feedback":"f","extra":"x"}]}
                """);
        String before = node.toString();

        WritingFeedbackView view = mapper.map(node);

        assertNotNull(view);
        assertEquals(before, node.toString());
        assertEquals(1, view.rubricScores().size());
    }

    @Test
    void nullMissingAndNonObjectEntryReturnNull() {
        assertNull(mapper.map(null));
        assertNull(mapper.map(NullNode.getInstance()));
        assertNull(mapper.map(objectMapper.createArrayNode()));
    }

    @Test
    void serializesWritingFeedbackUsingCurrentFrontendPropertyNames() throws Exception {
        WritingFeedbackView view = mapper.map(objectMapper.readTree("""
                {"raw_score":8,"raw_score_max":10,"score":7,"summary":"s","summary_vi":"sv","rubric_scores":[{"name":"n","score":8,"feedback":"f"}],"needs_improvement":[{"criterionId":"c"}],"upgraded_answer":"u","sentence_rewrites":[{"original":"o","upgraded":"n","reason":"r"}],"sample_answer":"sample"}
                """));

        String json = objectMapper.writeValueAsString(view);

        assertTrue(json.contains("\"raw_score\""));
        assertTrue(json.contains("\"raw_score_max\""));
        assertTrue(json.contains("\"rubric_scores\""));
        assertTrue(json.contains("\"needs_improvement\""));
        assertTrue(json.contains("\"upgraded_answer\""));
        assertTrue(json.contains("\"sentence_rewrites\""));
        assertTrue(json.contains("\"sample_answer\""));
        assertTrue(json.contains("\"criterionId\""));
        assertTrue(json.contains("\"explanationVi\""));
        assertFalse(json.contains("rawScore"));
        assertFalse(json.contains("needsImprovement"));
    }

    @Test
    void serializesFinalPracticeQuestionFeedbackRowContract() throws Exception {
        JsonNode sourceEntry = objectMapper.readTree("{\"raw_score\":8,\"raw_score_max\":10}");
        WritingFeedbackView view = mapper.map(sourceEntry);
        PracticeQuestionFeedbackRow row = new PracticeQuestionFeedbackRow(
                101L,
                51,
                "ESSAY",
                "Prompt",
                "Answer",
                view,
                true);

        String json = objectMapper.writeValueAsString(row);

        assertTrue(json.contains("\"questionId\""));
        assertTrue(json.contains("\"questionNo\""));
        assertTrue(json.contains("\"questionType\""));
        assertTrue(json.contains("\"prompt\""));
        assertTrue(json.contains("\"learnerAnswer\""));
        assertTrue(json.contains("\"writingFeedback\""));
        assertTrue(json.contains("\"reEvaluatable\""));
        assertTrue(json.contains("\"raw_score\""));
        assertTrue(json.contains("\"raw_score_max\""));
        assertFalse(json.contains("\"feedbackNode\""));
        assertFalse(json.contains("\"rawFeedback\""));
        assertFalse(json.contains("\"aiFeedbackJson\""));
    }

    @Test
    void typedViewContractDoesNotExposeJsonNodeOrObjectNodeFields() {
        assertFalse(Arrays.stream(WritingFeedbackView.class.getRecordComponents())
                .anyMatch(component -> JsonNode.class.isAssignableFrom(component.getType())));
    }
}
