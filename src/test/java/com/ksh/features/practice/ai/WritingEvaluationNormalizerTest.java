package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WritingEvaluationNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    // ---- Happy path ----

    @Test
    void testNormalizeHappyPath() throws Exception {
        String aiJson = """
        {
          "summary": "Tốt",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 7.0, "feedback": "Đủ ý"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 6.5, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "Cần cải thiện"}
          ],
          "strengths": [
            {
              "criterionId": "W_REGISTER_HONORIFIC_ACCURACY",
              "evidence": "합니다",
              "explanationVi": "Đúng đuôi văn viết",
              "correction": ""
            }
          ],
          "needs_improvement": [],
          "upgraded_answer": "한국어를 공부합니다.",
          "upgraded_answer_annotated": "",
          "sample_answer": "한국어를 열심히 공부합니다.",
          "sentence_rewrites": []
        }
        """;
        String studentText = "한국어를 공부합니다";
        String normalized = normalizer.normalize(aiJson, "Q53", studentText, null);
        JsonNode root = objectMapper.readTree(normalized);

        // Score derived from rubric average: (7.0 + 6.5 + 6.0) / 3 = 6.5
        assertEquals(6.5, root.path("score").asDouble());
        assertEquals("Q53", root.path("task_type").asText());
        assertEquals("KSH_WRITING_EVALUATOR_V2", root.path("engine").asText());
        assertTrue(root.path("raw_score").asDouble() <= 30.0, "Q53 raw_score should be <= 30");
        assertEquals(30.0, root.path("raw_score_max").asDouble());
    }

    // ---- Q51/Q52 rubrics ----

    @Test
    void testQ51_52NoEssayRubric() throws Exception {
        String aiJson = """
        {
          "summary": "Điền đúng ngữ cảnh",
          "rubric_scores": [
            {"name": "Hoàn thành đúng nội dung & ngữ cảnh (내용의 적절성)", "score": 8.0, "feedback": "OK"},
            {"name": "Ngữ pháp & cấu trúc câu (문법 및 문장 구성)", "score": 7.0, "feedback": "OK"},
            {"name": "Từ vựng, register & tính tự nhiên (어휘 및 자연스러움)", "score": 7.5, "feedback": "OK"}
          ],
          "strengths": [],
          "needs_improvement": [],
          "upgraded_answer": "",
          "upgraded_answer_annotated": "",
          "sample_answer": "",
          "sentence_rewrites": []
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q51_52", "할 수 있다", null);
        JsonNode root = objectMapper.readTree(normalized);

        // Score = avg(8.0, 7.0, 7.5) = 7.5
        assertEquals(7.5, root.path("score").asDouble());
        assertEquals(10.0, root.path("raw_score_max").asDouble());
        assertTrue(root.path("raw_score").asDouble() <= 10.0);

        // Verify rubric names are Q51_52-specific, NOT essay rubrics
        JsonNode rubrics = root.path("rubric_scores");
        assertEquals(3, rubrics.size());
        String rubricName0 = rubrics.get(0).path("name").asText();
        assertFalse(rubricName0.contains("Bố cục"), "Q51/Q52 should not have essay structure rubric");
        assertTrue(rubricName0.contains("nội dung") || rubricName0.contains("적절성"));
    }

    @Test
    void testQ53RubricAndRawMax() throws Exception {
        String aiJson = """
        {
          "summary": "Q53 OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 6.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 6.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "OK"}
          ],
          "strengths": [], "needs_improvement": [],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "", "sentence_rewrites": []
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q53", "테스트", null);
        JsonNode root = objectMapper.readTree(normalized);
        assertEquals(6.0, root.path("score").asDouble());
        assertEquals(30.0, root.path("raw_score_max").asDouble());
        assertTrue(root.path("raw_score").asDouble() <= 30.0);
    }

    @Test
    void testQ54RubricAndRawMax() throws Exception {
        String aiJson = """
        {
          "summary": "Q54 OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 7.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 7.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 7.0, "feedback": "OK"}
          ],
          "strengths": [], "needs_improvement": [],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "", "sentence_rewrites": []
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q54", "테스트", null);
        JsonNode root = objectMapper.readTree(normalized);
        assertEquals(7.0, root.path("score").asDouble());
        assertEquals(50.0, root.path("raw_score_max").asDouble());
        assertTrue(root.path("raw_score").asDouble() <= 50.0);
    }

    // ---- Evidence validation ----

    @Test
    void testFakeEvidenceRemoved() throws Exception {
        String studentText = "한국어를 공부합니다";
        String aiJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 6.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 6.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "OK"}
          ],
          "strengths": [
            {"criterionId": "W_ADVANCED_GRAMMAR_STRUCTURES", "evidence": "이것은 가짜 증거입니다", "explanationVi": "Fake", "correction": ""}
          ],
          "needs_improvement": [
            {"criterionId": "W_GRAMMAR_ERRORS", "evidence": "가짜 증거", "explanationVi": "Fake", "correction": "수정"}
          ],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "",
          "sentence_rewrites": [
            {"original": "없는 문장", "upgraded": "수정된 문장", "reason": "Fake rewrite"}
          ]
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q53", studentText, null);
        JsonNode root = objectMapper.readTree(normalized);

        assertTrue(root.path("strengths").isEmpty(), "Fake evidence should be removed");
        assertTrue(root.path("needs_improvement").isEmpty(), "Fake evidence should be removed");
        assertTrue(root.path("sentence_rewrites").isEmpty(), "Fake original should be removed");
    }

    @Test
    void testStrengthCorrectionAlwaysEmpty() throws Exception {
        String studentText = "한국어를 공부합니다";
        String aiJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 6.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 6.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "OK"}
          ],
          "strengths": [
            {"criterionId": "W_ADVANCED_GRAMMAR_STRUCTURES", "evidence": "공부합니다", "explanationVi": "Good grammar", "correction": "should be empty"}
          ],
          "needs_improvement": [],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "", "sentence_rewrites": []
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q53", studentText, null);
        JsonNode root = objectMapper.readTree(normalized);
        assertEquals("", root.path("strengths").get(0).path("correction").asText());
    }

    @Test
    void testNeedsCorrectionRequired() throws Exception {
        String studentText = "한국어를 공부합니다";
        String aiJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 6.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 6.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "OK"}
          ],
          "strengths": [],
          "needs_improvement": [
            {"criterionId": "W_GRAMMAR_ERRORS", "evidence": "공부합니다", "explanationVi": "Error", "correction": ""},
            {"criterionId": "W_GRAMMAR_ERRORS", "evidence": "한국어를", "explanationVi": "Error", "correction": "한국어를 더"}
          ],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "", "sentence_rewrites": []
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q53", studentText, null);
        JsonNode root = objectMapper.readTree(normalized);
        // First need has empty correction → removed; second has correction → kept
        assertEquals(1, root.path("needs_improvement").size());
        assertEquals("한국어를", root.path("needs_improvement").get(0).path("evidence").asText());
    }

    // ---- Score stability ----

    @Test
    void testScoreStableWhenFindingsCountChanges() throws Exception {
        String studentText = "한국어를 공부합니다. 재미있다. 열심히 하겠다.";
        // Same rubric scores, different findings count
        String aiJsonFew = """
        {
          "summary": "Few findings",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 7.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 7.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 7.0, "feedback": "OK"}
          ],
          "strengths": [
            {"criterionId": "W_ADVANCED_GRAMMAR_STRUCTURES", "evidence": "공부합니다", "explanationVi": "Good", "correction": ""}
          ],
          "needs_improvement": [],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "", "sentence_rewrites": []
        }
        """;
        String aiJsonMany = """
        {
          "summary": "Many findings",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 7.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 7.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 7.0, "feedback": "OK"}
          ],
          "strengths": [
            {"criterionId": "W_ADVANCED_GRAMMAR_STRUCTURES", "evidence": "공부합니다", "explanationVi": "Good", "correction": ""},
            {"criterionId": "W_NATURAL_KOREAN_EXPRESSIONS", "evidence": "재미있다", "explanationVi": "Natural", "correction": ""},
            {"criterionId": "W_APPROPRIATE_VOCABULARY_USAGE", "evidence": "열심히", "explanationVi": "Good vocab", "correction": ""}
          ],
          "needs_improvement": [
            {"criterionId": "W_GRAMMAR_ERRORS", "evidence": "하겠다", "explanationVi": "Error", "correction": "하겠습니다"}
          ],
          "upgraded_answer": "", "upgraded_answer_annotated": "",
          "sample_answer": "", "sentence_rewrites": []
        }
        """;

        JsonNode rootFew = objectMapper.readTree(normalizer.normalize(aiJsonFew, "Q53", studentText, null));
        JsonNode rootMany = objectMapper.readTree(normalizer.normalize(aiJsonMany, "Q53", studentText, null));

        assertEquals(rootFew.path("score").asDouble(), rootMany.path("score").asDouble(),
                "Score should be determined by rubric scores, not findings count");
    }

    // ---- Missing upgrade fields ----

    @Test
    void testMissingUpgradeFieldsSafeDefaults() throws Exception {
        String aiJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 6.0, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 6.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "OK"}
          ],
          "strengths": [],
          "needs_improvement": []
        }
        """;
        String normalized = normalizer.normalize(aiJson, "Q53", "테스트", null);
        JsonNode root = objectMapper.readTree(normalized);
        assertEquals("", root.path("upgraded_answer").asText());
        assertEquals("", root.path("sample_answer").asText());
        assertTrue(root.path("sentence_rewrites").isEmpty());
    }

    // ---- Spam response ----

    @Test
    void testSpamResponse() throws Exception {
        String spamJson = normalizer.spamResponse("Q53", "asdfasdf");
        JsonNode root = objectMapper.readTree(spamJson);
        assertEquals(0.0, root.path("score").asDouble());
        assertTrue(root.path("summary").asText().startsWith("[SPAM_DETECTED]"));
        assertEquals(30.0, root.path("raw_score_max").asDouble());
        assertEquals(3, root.path("rubric_scores").size());
        assertTrue(root.path("strengths").isEmpty());
        assertTrue(root.path("needs_improvement").isEmpty());
    }

    @Test
    void testSpamResponseQ51_52UsesCorrectRubrics() throws Exception {
        String spamJson = normalizer.spamResponse("Q51_52", "");
        JsonNode root = objectMapper.readTree(spamJson);
        assertEquals(10.0, root.path("raw_score_max").asDouble());
        String rubric0 = root.path("rubric_scores").get(0).path("name").asText();
        assertFalse(rubric0.contains("Bố cục"), "Spam response for Q51/52 should not have essay rubric");
    }

    // ---- Fallback on error ----

    @Test
    void testNormalizeFallbackOnError() throws Exception {
        String invalidJson = "{ malformed json }";
        String normalizedJson = normalizer.normalize(invalidJson);

        JsonNode root = objectMapper.readTree(normalizedJson);
        assertEquals(1.0, root.path("score").asDouble());
        assertEquals("KSH_WRITING_EVALUATOR_FALLBACK", root.path("engine").asText());
    }

    // ---- Regression: mock outputs normalize successfully ----

    @Test
    void testMockEvaluatorServiceOutputNormalizes() throws Exception {
        WritingMockEvaluatorService mockService = new WritingMockEvaluatorService(objectMapper);
        WritingRuleEngine.RuleAnalysis analysis = new WritingRuleEngine.RuleAnalysis(
                "Q53", 250, "OK: length fits", List.of()
        );
        String mockOutput = mockService.evaluate("Prompt Q53", "한국어를 공부합니다", analysis, "Test");

        // normalize(String) backward-compatible overload
        String normalized = normalizer.normalize(mockOutput);
        JsonNode root = objectMapper.readTree(normalized);

        assertTrue(root.path("score").asDouble() >= 1.0);
        assertTrue(root.path("score").asDouble() <= 9.0);
        assertFalse(root.path("rubric_scores").isEmpty());
        assertNotNull(root.path("engine").asText());
    }

    @Test
    void testPracticeServiceMockWritingFeedbackFormatNormalizes() throws Exception {
        // Simulate the JSON structure produced by PracticeService.mockWritingFeedback
        String mockJson = """
        {
          "score": 5.5,
          "overall_score": 5.5,
          "raw_score": 55.0,
          "raw_score_max": 100.0,
          "task_type": "GENERAL",
          "student_text": "한국어를 배우는 이유",
          "summary_vi": "Bài viết đã đáp ứng yêu cầu cơ bản.",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 5.5, "feedback": "OK"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 5.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 4.5, "feedback": "OK"}
          ],
          "strengths": [
            {"criterionId": "W_NATURAL_KOREAN_EXPRESSIONS", "evidence": "한국어를 배우는", "explanationVi": "Good", "correction": ""}
          ],
          "needs_improvement": [
            {"criterionId": "W_SPELLING_SPACING_ERRORS", "evidence": "이유", "explanationVi": "Spelling", "correction": "이유를"}
          ],
          "upgraded_answer": "",
          "sample_answer": "",
          "sentence_rewrites": []
        }
        """;
        // normalize(String) backward-compatible overload should not crash
        String normalized = normalizer.normalize(mockJson);
        JsonNode root = objectMapper.readTree(normalized);

        assertTrue(root.path("score").asDouble() >= 1.0);
        assertTrue(root.path("score").asDouble() <= 9.0);
        assertEquals(3, root.path("rubric_scores").size());
    }

    // ---- deriveScoreFromRubrics unit test ----

    @Test
    void testDeriveScoreFromRubricsEqualAverage() {
        var rubrics = List.of(
                java.util.Map.<String, Object>of("name", "A", "score", 7.0, "feedback", ""),
                java.util.Map.<String, Object>of("name", "B", "score", 8.0, "feedback", ""),
                java.util.Map.<String, Object>of("name", "C", "score", 6.0, "feedback", "")
        );
        // avg = (7+8+6)/3 = 7.0
        assertEquals(7.0, WritingEvaluationNormalizer.deriveScoreFromRubrics(rubrics));
    }

    @Test
    void testDeriveScoreFromRubricsEmpty() {
        assertEquals(1.0, WritingEvaluationNormalizer.deriveScoreFromRubrics(List.of()));
    }
}
