package com.ksh.features.practice.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WritingEvaluationNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WritingEvaluationNormalizer normalizer = new WritingEvaluationNormalizer(objectMapper);

    @Test
    void evidenceScopesEnforceTaskContractWithoutFakeHighlights() throws Exception {
        String input = """
                {
                  "summary":"ok",
                  "rubric_scores":[],
                  "strengths":[
                    {"criterionId":"W_LOGICAL_ORGANIZATION","evidenceScope":"WHOLE_ANSWER","evidence":"fabricated span","explanationVi":"Mạch lạc","correction":""},
                    {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN","evidence":"không có","explanationVi":"Sai","correction":"sửa"},
                    {"criterionId":"W_WON_GO_JI","evidenceScope":"TASK_METADATA","evidence":"","explanationVi":"Format","correction":""}
                  ],
                  "needs_improvement":[],
                  "upgraded_answer":"","sample_answer":"","sentence_rewrites":[]
                }
                """;

        JsonNode root = objectMapper.readTree(normalizer.normalize(input, "Q54", "Bài làm có mạch lạc.", null));

        assertEquals(1, root.path("strengths").size());
        assertEquals("WHOLE_ANSWER", root.path("strengths").get(0).path("evidenceScope").asText());
        assertEquals("", root.path("strengths").get(0).path("evidence").asText());
        assertTrue(root.path("annotations").isEmpty());
    }

    @Test
    void normalizerDropsCriterionThatIsNotActiveForExactTask() throws Exception {
        String input = """
                {
                  "summary":"ok",
                  "rubric_scores":[],
                  "strengths":[
                    {"criterionId":"W_CLEAR_THESIS_OR_MAIN_IDEA","evidenceScope":"WHOLE_ANSWER","evidence":"","explanationVi":"Rõ","correction":""},
                    {"criterionId":"W_CLOZE_CONTEXT_FIT","evidenceScope":"TEXT_SPAN","evidence":"있다","explanationVi":"Phù hợp","correction":""}
                  ],
                  "needs_improvement":[],
                  "upgraded_answer":"","sample_answer":"","sentence_rewrites":[]
                }
                """;

        JsonNode root = objectMapper.readTree(normalizer.normalize(input, "Q51", "있다", null));
        assertEquals(1, root.path("strengths").size());
        assertEquals("W_CLOZE_CONTEXT_FIT", root.path("strengths").get(0).path("criterionId").asText());
    }

    @Test
    void textSpanMustMatchExactlyWithoutNormalizerTrimming() throws Exception {
        String input = """
                {
                  "summary":"ok","rubric_scores":[],
                  "strengths":[
                    {"criterionId":"W_NATURAL_KOREAN_EXPRESSIONS","evidenceScope":"TEXT_SPAN","evidence":" 답안 ","explanationVi":"exact","correction":""}
                  ],
                  "needs_improvement":[],"upgraded_answer":"","sample_answer":"","sentence_rewrites":[]
                }
                """;

        JsonNode rejected = objectMapper.readTree(normalizer.normalize(input, "GENERAL", "답안", null));
        JsonNode accepted = objectMapper.readTree(normalizer.normalize(input, "GENERAL", "문장 답안 문장", null));

        assertTrue(rejected.path("strengths").isEmpty());
        assertEquals(" 답안 ", accepted.path("strengths").get(0).path("evidence").asText());
    }

    @Test
    void newProviderFindingsEnforceCanonicalTaxonomyMatrix() throws Exception {
        String input = """
                {
                  "summary":"ok","rubric_scores":[],
                  "strengths":[
                    {"criterionId":"UNKNOWN_ID","evidenceScope":"TEXT_SPAN","evidence":"답안","explanationVi":"unknown","correction":""},
                    {"criterionId":"W_SENTENCE_VARIETY","evidenceScope":"TEXT_SPAN","evidence":"답안","explanationVi":"legacy","correction":""},
                    {"criterionId":"W_GRAMMAR_ERRORS","evidenceScope":"TEXT_SPAN","evidence":"답안","explanationVi":"wrong polarity","correction":""},
                    {"criterionId":"W_NATURAL_KOREAN_EXPRESSIONS","evidenceScope":"WHOLE_ANSWER","evidence":"","explanationVi":"unsupported scope","correction":""},
                    {"criterionId":"W_NATURAL_KOREAN_EXPRESSIONS","evidenceScope":"TEXT_SPAN","evidence":"답안","explanationVi":"valid","correction":"","category":"PROVIDER_OVERRIDE"}
                  ],
                  "needs_improvement":[],"upgraded_answer":"","sample_answer":"","sentence_rewrites":[]
                }
                """;

        JsonNode root = objectMapper.readTree(normalizer.normalize(input, "GENERAL", "답안", null));

        assertEquals(1, root.path("strengths").size());
        assertEquals("W_NATURAL_KOREAN_EXPRESSIONS", root.path("strengths").get(0).path("criterionId").asText());
        assertEquals("GENERAL_LANGUAGE", root.path("strengths").get(0).path("category").asText());
    }

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

    @Test
    void testTaskAwareNormalizeFallbackOnError() throws Exception {
        String invalidJson = "{ malformed json }";
        String normalizedJson = normalizer.normalize(invalidJson, "Q54", "한국어", null);

        JsonNode root = objectMapper.readTree(normalizedJson);

        assertEquals("Q54", root.path("task_type").asText());
        assertEquals(50.0, root.path("raw_score_max").asDouble());
        assertEquals(3, root.path("rubric_scores").size());
    }

    @Test
    void testTaskAwareFallbackKeepsProfileRawMaxAndRubrics() throws Exception {
        assertFallbackProfile("Q51_52", 10.0);
        assertFallbackProfile("Q53", 30.0);
        assertFallbackProfile("Q54", 50.0);
        assertFallbackProfile("GENERAL", 100.0);
    }

    private void assertFallbackProfile(String taskType, double rawScoreMax) throws Exception {
        JsonNode root = objectMapper.readTree(normalizer.fallback("retry later", taskType));

        assertEquals(taskType, root.path("task_type").asText());
        assertEquals(rawScoreMax, root.path("raw_score_max").asDouble());
        assertEquals(3, root.path("rubric_scores").size());
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

    @Test
    void testSanitizeForCacheRemovesTopLevelStudentText() throws Exception {
        String normalized = """
        {
          "score": 7.0,
          "raw_score": 20.0,
          "raw_score_max": 30.0,
          "student_text": "한국어를 공부합니다",
          "engine": "KSH_WRITING_EVALUATOR_V2"
        }
        """;

        JsonNode root = objectMapper.readTree(normalizer.sanitizeForCache(normalized));

        assertFalse(root.has("student_text"));
        assertEquals(7.0, root.path("score").asDouble());
    }

    @Test
    void testRehydrateCachedResultSetsStudentTextFiltersEvidenceAndPreservesScores() throws Exception {
        String cached = """
        {
          "score": 7.0,
          "overall_score": 7.0,
          "raw_score": 22.0,
          "raw_score_max": 30.0,
          "task_type": "Q53",
          "summary": "Good",
          "rubric_scores": [
            {"name": "A", "score": 7.0, "feedback": "A"},
            {"name": "B", "score": 7.0, "feedback": "B"},
            {"name": "C", "score": 7.0, "feedback": "C"}
          ],
          "strengths": [
            {"criterionId": "W_ADVANCED_GRAMMAR_STRUCTURES", "evidence": "공부합니다", "explanationVi": "Good", "correction": ""},
            {"criterionId": "W_NATURAL_KOREAN_EXPRESSIONS", "evidence": "없는증거", "explanationVi": "Bad", "correction": ""}
          ],
          "needs_improvement": [
            {"criterionId": "W_GRAMMAR_ERRORS", "evidence": "한국어를", "explanationVi": "Need", "correction": "한국어를 더"},
            {"criterionId": "W_GRAMMAR_ERRORS", "evidence": "없는오류", "explanationVi": "Bad", "correction": "수정"}
          ],
          "annotations": [
            {"id": "stale", "evidence": "없는증거"}
          ],
          "sentence_rewrites": [
            {"original": "한국어를", "upgraded": "한국어를 더", "reason": "Better"},
            {"original": "없는문장", "upgraded": "수정", "reason": "Bad"}
          ],
          "engine": "KSH_WRITING_EVALUATOR_V2"
        }
        """;

        JsonNode root = objectMapper.readTree(normalizer.rehydrateCachedResult(cached, "한국어를 공부합니다"));

        assertEquals("한국어를 공부합니다", root.path("student_text").asText());
        assertEquals(7.0, root.path("score").asDouble());
        assertEquals(22.0, root.path("raw_score").asDouble());
        assertEquals(30.0, root.path("raw_score_max").asDouble());
        assertEquals(3, root.path("rubric_scores").size());
        assertEquals(1, root.path("strengths").size());
        assertEquals("공부합니다", root.path("strengths").get(0).path("evidence").asText());
        assertEquals(1, root.path("needs_improvement").size());
        assertEquals("한국어를", root.path("needs_improvement").get(0).path("evidence").asText());
        assertEquals(1, root.path("sentence_rewrites").size());
        assertEquals("한국어를", root.path("sentence_rewrites").get(0).path("original").asText());
        assertEquals(2, root.path("annotations").size());
        assertNotEquals("stale", root.path("annotations").get(0).path("id").asText());
    }

    @Test
    void testCacheabilityRejectsFallback() {
        assertFalse(normalizer.isCacheableAiResult("{\"engine\":\"KSH_WRITING_EVALUATOR_FALLBACK\",\"raw_score\":1.0,\"raw_score_max\":100.0}"));
        assertTrue(normalizer.isCacheableAiResult("{\"engine\":\"KSH_WRITING_EVALUATOR_V2\",\"raw_score\":1.0,\"raw_score_max\":100.0}"));
    }

    // ---- Task-specific raw max and score validation ----

    @Test
    void testNormalizerScoringTaskSpecificMax() throws Exception {
        String baseJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "%s", "score": 9.0, "feedback": "Best"},
            {"name": "%s", "score": 9.0, "feedback": "Best"},
            {"name": "%s", "score": 9.0, "feedback": "Best"}
          ],
          "strengths": [], "needs_improvement": []
        }
        """;

        // Q51_52: max 10.0
        List<String> r51 = WritingPromptRules.rubricNamesForTask("Q51_52");
        String json51 = String.format(baseJson, r51.get(0), r51.get(1), r51.get(2));
        JsonNode root51 = objectMapper.readTree(normalizer.normalize(json51, "Q51_52", "답안", null));
        assertEquals(9.0, root51.path("score").asDouble());
        assertEquals(10.0, root51.path("raw_score_max").asDouble());
        assertEquals(10.0, root51.path("raw_score").asDouble());

        // Q53: max 30.0
        List<String> r53 = WritingPromptRules.rubricNamesForTask("Q53");
        String json53 = String.format(baseJson, r53.get(0), r53.get(1), r53.get(2));
        JsonNode root53 = objectMapper.readTree(normalizer.normalize(json53, "Q53", "답안", null));
        assertEquals(9.0, root53.path("score").asDouble());
        assertEquals(30.0, root53.path("raw_score_max").asDouble());
        assertEquals(30.0, root53.path("raw_score").asDouble());

        // Q54: max 50.0
        List<String> r54 = WritingPromptRules.rubricNamesForTask("Q54");
        String json54 = String.format(baseJson, r54.get(0), r54.get(1), r54.get(2));
        JsonNode root54 = objectMapper.readTree(normalizer.normalize(json54, "Q54", "답안", null));
        assertEquals(9.0, root54.path("score").asDouble());
        assertEquals(50.0, root54.path("raw_score_max").asDouble());
        assertEquals(50.0, root54.path("raw_score").asDouble());

        // GENERAL: max 100.0
        List<String> rGen = WritingPromptRules.rubricNamesForTask("GENERAL");
        String jsonGen = String.format(baseJson, rGen.get(0), rGen.get(1), rGen.get(2));
        JsonNode rootGen = objectMapper.readTree(normalizer.normalize(jsonGen, "GENERAL", "답안", null));
        assertEquals(9.0, rootGen.path("score").asDouble());
        assertEquals(100.0, rootGen.path("raw_score_max").asDouble());
        assertEquals(100.0, rootGen.path("raw_score").asDouble());
    }

    // ---- Duplicate, missing, and wrong-task rubrics validation ----

    @Test
    void testNormalizerSafeHandlingOfMissingDuplicateAndWrongTaskRubrics() throws Exception {
        // 1. Missing rubric scores entirely or partially
        String missingJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 8.0, "feedback": "Good"}
          ],
          "strengths": [], "needs_improvement": []
        }
        """;
        JsonNode rootMissing = objectMapper.readTree(normalizer.normalize(missingJson, "Q53", "답안", null));
        JsonNode rubricsMissing = rootMissing.path("rubric_scores");
        assertEquals(3, rubricsMissing.size()); // Normalizer must enforce exactly 3 rubrics for Q53
        assertEquals(8.0, rubricsMissing.get(0).path("score").asDouble());
        assertEquals(1.0, rubricsMissing.get(1).path("score").asDouble()); // Fallback missing to 1.0
        assertEquals(1.0, rubricsMissing.get(2).path("score").asDouble()); // Fallback missing to 1.0
        // final average: clampAndRound((8 + 1 + 1)/3) = clampAndRound(3.33) = 3.5
        assertEquals(3.5, rootMissing.path("score").asDouble());

        // 2. Duplicate rubrics in output (should select the first matching and discard duplicates)
        String duplicateJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 8.0, "feedback": "First"},
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 5.0, "feedback": "Second"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 7.0, "feedback": "OK"},
            {"name": "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)", "score": 6.0, "feedback": "OK"}
          ],
          "strengths": [], "needs_improvement": []
        }
        """;
        JsonNode rootDuplicate = objectMapper.readTree(normalizer.normalize(duplicateJson, "Q53", "답안", null));
        JsonNode rubricsDuplicate = rootDuplicate.path("rubric_scores");
        assertEquals(3, rubricsDuplicate.size());
        assertEquals(8.0, rubricsDuplicate.get(0).path("score").asDouble()); // Must pick the first matching score 8.0, not 5.0
        assertEquals(7.0, rubricsDuplicate.get(1).path("score").asDouble());
        assertEquals(6.0, rubricsDuplicate.get(2).path("score").asDouble());

        // 3. Wrong-task rubrics (e.g., input Q51_52 task but AI returns Q53 essay rubrics)
        String wrongTaskJson = """
        {
          "summary": "OK",
          "rubric_scores": [
            {"name": "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)", "score": 8.0, "feedback": "Essay content"},
            {"name": "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)", "score": 7.0, "feedback": "Essay structure"}
          ],
          "strengths": [], "needs_improvement": []
        }
        """;
        JsonNode rootWrong = objectMapper.readTree(normalizer.normalize(wrongTaskJson, "Q51_52", "답안", null));
        JsonNode rubricsWrong = rootWrong.path("rubric_scores");
        assertEquals(3, rubricsWrong.size()); // Enforces Q51_52 rubrics
        assertEquals(1.0, rubricsWrong.get(0).path("score").asDouble()); // Fallback expected Q51_52 rubric 1 to 1.0
        assertEquals(1.0, rubricsWrong.get(1).path("score").asDouble()); // Fallback expected Q51_52 rubric 2 to 1.0
        assertEquals(1.0, rubricsWrong.get(2).path("score").asDouble()); // Fallback expected Q51_52 rubric 3 to 1.0
        assertEquals(1.0, rootWrong.path("score").asDouble());
    }
}
