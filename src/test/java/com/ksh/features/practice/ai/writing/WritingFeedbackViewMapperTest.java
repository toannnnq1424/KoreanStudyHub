package com.ksh.features.practice.ai.writing;

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
    void legacyCriterionWithoutLabelsOrEvidenceScopeRemainsReadable() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"strengths":[{"criterionId":"W_SENTENCE_VARIETY","evidence":"답안","explanationVi":"ok","correction":""}]}
                """);

        WritingFeedbackView view = mapper.map(payload);

        assertEquals("W_LENGTH_REQUIREMENT_MET", view.strengths().get(0).criterionId());
        assertEquals("LENGTH", view.strengths().get(0).category());
        assertEquals("TEXT_SPAN", view.strengths().get(0).evidenceScope());
        assertNotNull(view.strengths().get(0).vietnameseLabel());
    }

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
                    {"id":"ann-1","kind":"need","criterionId":"W_GRAMMAR_ERRORS","category":"Provider content","start":1,"end":5,"severity":"LOW","displayType":"WORD","index":1,"explanationVi":"ann ex","correction":"fix","evidence":"ev1"},
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
        assertNull(view.strengths().get(0).category());
        assertNull(view.strengths().get(1).category());
        assertNull(view.needsImprovement().get(0).category());
        assertEquals("ann-1", view.annotations().get(0).id());
        assertEquals(Integer.valueOf(1), view.annotations().get(0).start());
        assertEquals(Integer.valueOf(5), view.annotations().get(0).end());
        assertEquals(Integer.valueOf(1), view.annotations().get(0).index());
        assertEquals(1, view.annotations().size());
        assertEquals("better answer", view.upgradedAnswer());
        assertEquals("old1", view.sentenceRewrites().get(0).original());
        assertEquals("old2", view.sentenceRewrites().get(1).original());
        assertEquals("sample", view.sampleAnswer());
    }

    @Test
    void providerLabelsCannotOverrideKshDescriptorLabels() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"needs_improvement":[{
                  "criterionId":"W_GRAMMAR_ERRORS",
                  "category":"Content",
                  "vietnameseLabel":"Provider label",
                  "uiLabel":"Coherence",
                  "evidenceScope":"TEXT_SPAN",
                  "evidence":"문법 오류"
                }]}
                """);

        WritingFeedbackView view = mapper.map(payload);

        assertEquals("GRAMMAR", view.needsImprovement().get(0).category());
        assertEquals("Lỗi ngữ pháp", view.needsImprovement().get(0).vietnameseLabel());
        assertEquals("Lỗi ngữ pháp", view.needsImprovement().get(0).uiLabel());
    }

    @Test
    void providerCategoryCannotReclassifyAnnotationAndUnknownOrInactiveIdsAreRejected()
            throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"annotations":[
                  {"id":"valid","criterionId":"W_GRAMMAR_ERRORS","category":"CONTENT",
                   "start":0,"end":4,"evidence":"문법","explanationVi":"Lỗi ngữ pháp"},
                  {"id":"unknown","criterionId":"PROVIDER_CONTENT","category":"CONTENT",
                   "start":5,"end":9,"evidence":"내용"},
                  {"id":"inactive","criterionId":"W_SENTENCE_VARIETY","category":"GRAMMAR",
                   "start":10,"end":14,"evidence":"분량"}
                ]}
                """);

        WritingFeedbackView view = mapper.map(payload);

        assertEquals(1, view.annotations().size());
        assertEquals("valid", view.annotations().get(0).id());
        assertEquals("W_GRAMMAR_ERRORS", view.annotations().get(0).criterionId());
        assertEquals("GRAMMAR", view.annotations().get(0).category());
        assertEquals(Integer.valueOf(0), view.annotations().get(0).start());
        assertEquals(Integer.valueOf(4), view.annotations().get(0).end());
        assertEquals("문법", view.annotations().get(0).evidence());
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
                  "annotations":[1, {"id":"ok","criterionId":"W_GRAMMAR_ERRORS","start":1.5,"end":2,"index":"bad"}],
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
