# Practice Module — Test Plan (100% Coverage)

> **Mục tiêu**: Unit test + Integration test cho 100% class coverage trong `com.ksh.features.practice.*` và `com.ksh.common.storage.*`
>
> **Framework**: JUnit 5 · Mockito · Spring Boot Test · MockMvc · DataJpaTest

---

## 1. Tổng quan Coverage Target

| Layer | Class | Hiện tại | Target |
|---|---|---|---|
| **Controller** | `PracticeController` | ❌ Chưa có WebMvc test | ✅ 100% |
| **Service** | `PracticeService` | 🟡 Partial (unit test có sẵn) | ✅ 100% |
| **Service** | `PracticePdfImportService` | 🟡 Partial | ✅ 100% |
| **Service** | `ReadingListeningExplanationService` | ❌ Chưa có | ✅ 100% |
| **AI** | `WritingEvaluationClient` | ❌ Chưa có | ✅ 100% |
| **AI** | `WritingEvaluationNormalizer` | 🟡 Partial | ✅ 100% |
| **AI** | `WritingRuleEngine` | ✅ Đã có | ✅ Bổ sung edge cases |
| **AI** | `WritingScoreMatrix` | 🟡 Partial (in normalizer test) | ✅ 100% dedicated |
| **AI** | `WritingRubricCriterion` | ❌ Chưa có | ✅ 100% |
| **AI** | `WritingMockEvaluatorService` | ✅ Đã có | ✅ Bổ sung edge cases |
| **AI** | `WritingEvaluationCacheService` | ❌ Chưa có | ✅ 100% |
| **AI** | `AnswerExplanationClient` | ❌ Chưa có | ✅ 100% (mock HTTP) |
| **AI** | `ReadingListeningExplanationClient` | ❌ Chưa có | ✅ 100% (mock HTTP) |
| **Storage** | `WritingEvaluationCacheService` | ❌ Chưa có | ✅ 100% |
| **Storage** | `LocalAudioStorageService` | ❌ Chưa có | ✅ 100% |
| **Repository** | 5 repositories | ❌ Chưa có DataJpaTest | ✅ Key queries |
| **Integration** | `PracticeIntegrationTest` | ✅ Đã có (10 tests) | ✅ Bổ sung R/L detail, Writing detail |

---

## 2. Unit Tests — chi tiết từng class

### 2.1 `WritingRuleEngineTest` _(bổ sung vào test đã có)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingRuleEngineTest.java`

| Test Method | Scenario |
|---|---|
| `detectTaskType_Q51_52_fromNumber` | Prompt chứa "51" |
| `detectTaskType_Q51_52_fromBracket` | Prompt chứa "(ㄱ)" |
| `detectTaskType_Q53_fromNumber` | Prompt chứa "53" |
| `detectTaskType_Q53_fromCharRange` | Prompt chứa "200-300자" |
| `detectTaskType_Q54_fromNumber` | Prompt chứa "54" |
| `detectTaskType_Q54_fromCharRange` | Prompt chứa "600~700자" |
| `detectTaskType_GENERAL_noKeyword` | Prompt không khớp → GENERAL |
| `detectTaskType_nullPrompt` | Null prompt → GENERAL |
| `analyze_detectsSpokenLanguage_근데` | "근데" trong answer → violations size = 1 |
| `analyze_detectsSpokenLanguage_해요` | "해요" → violation |
| `analyze_noViolations_formalText` | Text chính thức → violations empty |
| `analyze_charCount_Q53_ok` | 250 chars + Q53 → "OK:" warning |
| `analyze_charCount_Q53_critical` | 100 chars + Q53 → "CRITICAL:" warning |
| `analyze_charCount_Q53_warning` | 180 chars + Q53 → "WARNING:" warning |
| `analyze_charCount_Q54_ok` | 650 chars + Q54 → "OK:" warning |
| `analyze_charCount_Q54_critical` | 300 chars + Q54 → "CRITICAL:" warning |
| `analyze_charCount_GENERAL` | Any chars + GENERAL → "글자 수:" prefix |
| `analyze_ignoresNewlinesInCharCount` | "\n\n" → không đếm vào chars |
| `analyze_multipleViolations` | 3 khẩu ngữ → violations size = 3 |

---

### 2.2 `WritingScoreMatrixTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingScoreMatrixTest.java`

| Test Method | Scenario |
|---|---|
| `clampAndRound_below1_clampedTo1` | score 0.0 → 1.0 |
| `clampAndRound_above9_clampedTo9` | score 10.0 → 9.0 |
| `clampAndRound_5point3_roundToHalf` | 5.3 → 5.5 |
| `clampAndRound_5point7_roundToHalf` | 5.7 → 5.5 |
| `backendScore_fewStrengths_penalized` | 1 strength → -1.0 |
| `backendScore_moderateStrengths_halfPenalty` | 2 strengths → -0.5 |
| `backendScore_manyNeeds_penalized` | 7 needs → -1.0 |
| `backendScore_someNeeds_halfPenalty` | 4 needs → -0.5 |
| `rawScoreMax_Q51_52` | Q51_52 → 10.0 |
| `rawScoreMax_Q53` | Q53 → 30.0 |
| `rawScoreMax_Q54` | Q54 → 50.0 |
| `rawScoreMax_GENERAL` | GENERAL → 100.0 |
| `rawScoreMax_null` | null → 100.0 (GENERAL default) |
| `rawScoreFromNormalized_Q54_score9` | 9.0, Q54 → 50.0 |
| `rawScoreFromNormalized_Q53_score4_5` | 4.5, Q53 → calculated correctly |
| `toHundredPointScale_9` | 9.0 → 100.00 |
| `toHundredPointScale_1` | 1.0 → 11.11 |
| `bandLabel_9` | 9.0 → "Xuất sắc" |
| `bandLabel_8` | 8.0 → "Rất tốt" |
| `bandLabel_1` | 1.0 → "Không phản hồi" |
| `bands_returns9Entries` | bands() → 9 entries |

---

### 2.3 `WritingRubricCriterionTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingRubricCriterionTest.java`

| Test Method | Scenario |
|---|---|
| `strengths_returns7criteria` | strengths() size = 7 |
| `needsImprovement_returns8criteria` | needsImprovement() size = 8 |
| `parse_validId_returnsEnum` | "W_GRAMMAR_ERRORS" → enum value |
| `parse_nullId_returnsNull` | null → null |
| `parse_blankId_returnsNull` | "  " → null |
| `parse_unknownId_returnsNull` | "NONEXISTENT" → null |
| `allCriteria_haveNonNullFields` | Tất cả criteria có vietnameseLabel, koreanLabel, rule |
| `allCriteria_haveWeight1` | tất cả weight = 1.0 |
| `id_equalsName` | criterion.id() == criterion.name() |
| `strengthPolarity` | W_NATURAL_KOREAN_EXPRESSIONS.polarity() == STRENGTH |
| `needsImprovementPolarity` | W_GRAMMAR_ERRORS.polarity() == NEEDS_IMPROVEMENT |

---

### 2.4 `WritingEvaluationCacheServiceTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingEvaluationCacheServiceTest.java`

| Test Method | Scenario |
|---|---|
| `get_empty_returnsEmpty` | Cache trống → Optional.empty() |
| `putAndGet_sameKey_returnsValue` | put("p","a","val") → get("p","a") = "val" |
| `get_differentPrompt_returnsEmpty` | Prompt khác → miss |
| `get_differentAnswer_returnsEmpty` | Answer khác → miss |
| `get_afterTTLExpired_returnsEmpty` | TTL 30 min giả lập hết hạn → empty (dùng reflection hoặc subclass override) |
| `put_overwritesExistingEntry` | put 2 lần cùng key → get trả về value mới nhất |
| `key_trimmedBeforeHashing` | "  prompt  " và "prompt" → same cache entry |
| `key_nullPrompt_handledGracefully` | null prompt → no NPE |
| `key_nullAnswer_handledGracefully` | null answer → no NPE |
| `concurrentAccess_noRaceCondition` | 10 threads put/get → không deadlock (smoke test) |

---

### 2.5 `WritingEvaluationNormalizerTest` _(bổ sung)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingEvaluationNormalizerTest.java`

| Test Method | Scenario |
|---|---|
| `normalize_validJson_extractsScore` | Đã có — giữ nguyên |
| `normalize_missingScore_fallbackToOverallScore` | `overall_score` thay vì `score` |
| `normalize_buildsBandLabel` | `band_label` đúng theo score |
| `normalize_buildsAnnotations_fromStrengths` | `strengths[].evidence` tìm thấy trong `student_text` → `annotations[]` |
| `normalize_buildsAnnotations_fromNeeds` | `needs_improvement[].evidence` → annotations |
| `normalize_evidenceNotFoundInText_skipAnnotation` | evidence không khớp → không add annotation |
| `normalize_emptyStrengthsAndNeeds_emptyAnnotations` | Mảng rỗng → annotations rỗng |
| `normalize_scoreAdjusted_byLowStrengthCount` | 1 strength → score giảm |
| `normalize_scoreAdjusted_byHighNeedsCount` | 7 needs → score giảm |
| `normalize_invalidJson_returnsEmptyJsonOrDefault` | JSON không hợp lệ → không crash |
| `normalize_copysSummaryViFromSummary` | `summary_vi` lấy từ `summary` nếu không có |
| `normalize_rubricScoresNormalized` | 3 rubric entries giữ nguyên |
| `normalize_upgradedAnswerPresent` | `upgraded_answer` được copy qua |
| `normalize_sampleAnswerPresent` | `sample_answer` được copy qua |
| `normalize_sentenceRewritesPresent` | `sentence_rewrites[]` được copy qua |

---

### 2.6 `WritingMockEvaluatorServiceTest` _(bổ sung)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingMockEvaluatorServiceTest.java`

| Test Method | Scenario |
|---|---|
| `evaluate_shortAnswer_lowScore` | Đã có |
| `evaluate_longAnswer_higherScore` | Đã có |
| `evaluate_nullAnswer_noNpe` | null answer → không crash |
| `evaluate_emptyAnswer_treated_asSpam` | Blank → spam detected score 1.0 |
| `evaluate_spamAnswer_score1` | Gõ bừa → score 1.0, summary chứa SPAM_DETECTED |
| `evaluate_withRuleViolations_penalized` | violations > 0 → score thấp hơn |
| `evaluate_Q54_taskType_correctRawMax` | taskType=Q54 → raw_score_max = 50 |
| `evaluate_Q53_taskType_correctRawMax` | taskType=Q53 → raw_score_max = 30 |
| `evaluate_generatesUpgradedAnswer` | `upgraded_answer` không blank |
| `evaluate_generatesSampleAnswer` | `sample_answer` không blank |
| `evaluate_generatesStrengths` | `strengths` không empty (nếu không spam) |
| `evaluate_generatesNeedsImprovement` | `needs_improvement` không empty |
| `evaluate_validJsonOutput` | Output parseable bởi ObjectMapper |

---

### 2.7 `WritingEvaluationClientTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/ai/WritingEvaluationClientTest.java`

> **Strategy**: Mock `RestClient` hoặc dùng `MockRestServiceServer` nếu RestClient có `@Bean` config.
> Thực tế đơn giản hơn: mock tại `callWithRetry()` bằng spy hoặc wrap interface.

| Test Method | Scenario |
|---|---|
| `evaluate_noApiKey_returnsMockResult` | apiKey null/blank → MockEvaluatorService |
| `evaluate_cacheHit_returnsCache` | Lần 2 cùng prompt/answer → không gọi AI |
| `evaluate_cacheHit_reEvaluation_bypassCache` | isReEvaluation=true → bypass cache |
| `evaluate_aiException_returnsMockFallback` | AI throw exception → mock fallback |
| `evaluate_spamDetected_skip_pass2_pass3` | SPAM_DETECTED → không gọi evaluateDetails |
| `evaluate_successfulPasses_mergesCorrectly` | AI thành công 3 pass → merged JSON |
| `evaluate_callWithRetry_429_retries` | HTTP 429 → retry lên đến 5 lần |
| `evaluate_callWithRetry_400_noRetry` | HTTP 400 → không retry, throw |
| `emptyDetails_returnsEmptyArrays` | emptyDetails() → strengths=[], needs=[] |
| `emptyUpgrade_returnsEmptyFields` | emptyUpgrade() → empty strings |
| `allowedRubric_containsAllCriteria` | allowedRubric() size = 15 (tất cả criteria) |

---

### 2.8 `AnswerExplanationClientTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/ai/AnswerExplanationClientTest.java`

| Test Method | Scenario |
|---|---|
| `explain_noApiKey_returnsFallback` | apiKey blank → fallback JSON |
| `explain_apiSuccess_returnsContent` | AI trả content → parse và return |
| `explain_aiException_returnsFallback` | Exception → fallback |
| `explain_retry429_retriesUpTo3Times` | HTTP 429 → retry 3 lần |
| `explain_retry400_noRetry` | HTTP 400 → throw ngay |
| `fallback_containsQuestionExplanation` | Fallback dùng question.explanation khi có |
| `userPayload_containsAllFields` | payload chứa skill, questions, answers |
| `responseFormat_strictJsonSchema` | responseFormat có strict=true |

---

### 2.9 `ReadingListeningExplanationClientTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/ai/ReadingListeningExplanationClientTest.java`

| Test Method | Scenario |
|---|---|
| `explain_noApiKey_returnsFallbackJson` | apiKey null → fallback |
| `explain_success_returnsAiContent` | AI thành công → return content |
| `explain_exception_returnsFallback` | Exception → fallback không crash |
| `explain_retry429` | 429 → retry 3 lần |
| `explain_retry400_noRetry` | 400 → throw ngay |
| `fallbackJson_containsRequiredFields` | fallback có questionId, correctAnswer, reasonVi |
| `fallbackJson_usesExistingExplanation` | question.explanation không null → dùng trong reasonVi |
| `userPayload_includesPassageText` | passageText không null được include |
| `userPayload_nullPassage_handledGracefully` | null passageText → không crash |

---

### 2.10 `ReadingListeningExplanationServiceTest` _(mới)_

**File**: `src/test/java/com/ksh/features/practice/service/ReadingListeningExplanationServiceTest.java`

| Test Method | Scenario |
|---|---|
| `getOrCreate_cacheHit_returnsCached` | Cache đã có → không gọi AI |
| `getOrCreate_cacheMiss_callsAI` | Cache miss → gọi AI → lưu vào repo |
| `getOrCreate_cacheMiss_failToSave_returnAiResult` | Lưu cache thất bại → vẫn trả về AI result |
| `getOrCreate_doubleCheck_cacheHitInLock` | Race: miss → lock → check lại → hit |
| `buildQuestionHash_sameQuestion_sameHash` | Cùng question → cùng hash |
| `buildQuestionHash_differentPrompt_differentHash` | Prompt khác → hash khác |
| `buildQuestionHash_nullFields_noNpe` | Null prompt/options/answerKey → không crash |

---

### 2.11 `PracticeServiceTest` _(bổ sung vào test đã có)_

**File**: `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java`

| Test Method | Scenario | Thêm/Sửa |
|---|---|---|
| `testListPublished` | Đã có | Giữ |
| `testGetPracticeNotFound` | Đã có | Giữ |
| `testGetPracticeNotPublished` | Đã có | Giữ |
| `testGetPracticeWithGroups` | Đã có | Giữ |
| `testGetPracticeFallbackGrouping` | Đã có | Giữ |
| `testSubmitMcq` | **Sửa**: fix assertion `assertNull` → `assertNotNull` | Sửa |
| `testSubmitEssay` | Đã có | Giữ |
| `testSubmitSpeaking` | Đã có | Bổ sung verify score |
| `testReEvaluateNotFound` | Đã có | Giữ |
| `testReEvaluateSuccess` | Đã có | Giữ |
| `testGetResult` | Đã có | Giữ |
| `testGetLearningProfile` | Đã có | Giữ |
| **`testStartAttempt_createsInProgress`** | Tạo submission với STATUS_IN_PROGRESS | **Mới** |
| **`testSubmitAttempt_updatesStatusToGraded`** | Sau submit → status = GRADED | **Mới** |
| **`testSubmitAttempt_mcqCorrect_scoreUpdated`** | MCQ đúng → score tăng | **Mới** |
| **`testSubmitAttempt_mcqWrong_scoreZero`** | MCQ sai → score = 0 | **Mới** |
| **`testSubmitAttempt_attemptNotFound`** | attemptId không tồn tại → EntityNotFoundException | **Mới** |
| **`testSubmitAttempt_emptyQuestions`** | Set không có câu hỏi → EntityNotFoundException | **Mới** |
| **`testGetReadingListeningResult_correctCount`** | R/L: correctCount đúng | **Mới** |
| **`testGetReadingListeningResult_performanceByType`** | R/L: performanceByType có đúng accuracy | **Mới** |
| **`testGetReadingListeningResult_groupsBuilt`** | R/L: groups built từ dbGroups | **Mới** |
| **`testGetReadingListeningResult_fallbackGrouping`** | R/L: fallback khi không có groups | **Mới** |
| **`testGetPracticeSubmission_notFound`** | submissionId không tồn tại → exception | **Mới** |
| **`testGetAttempts_returnsBySetAndUser`** | Trả đúng submissions theo setId+userId | **Mới** |
| **`testAnswersMatch_normalizeUppercase`** | "a" vs "A" → match | **Mới** |
| **`testAnswersMatch_normalizeSlash`** | "／" vs "/" → match | **Mới** |
| **`testAnswersMatch_blankAnswer_noMatch`** | Blank answer → false | **Mới** |
| **`testNormalizeKey_trimAndUppercase`** | "  abc  " → "ABC" | **Mới** |
| **`testScoreLabel_zeroTotal_returns0`** | total=0 → "0%" | **Mới** |
| **`testScoreLabel_half_returns50`** | score=5, total=10 → "50%" | **Mới** |
| **`testGroupLabel_question1`** | questionNo=1 → "1-2" | **Mới** |
| **`testGroupLabel_question53`** | questionNo=53 → "53" | **Mới** |
| **`testGroupLabel_question54`** | questionNo=54 → "54" | **Mới** |
| **`testGroupLabel_null`** | null → "Câu" | **Mới** |
| **`testBadgeText_readingTopik`** | READING + TOPIK_I → "Đọc · TOPIK_I" | **Mới** |
| **`testBadgeText_writing_noLevel`** | WRITING + null → "Viết" | **Mới** |
| **`testLearningProfile_excludesInProgress`** | IN_PROGRESS submissions → không tính | **Mới** |
| **`testLearningProfile_averageScore`** | Tính average đúng | **Mới** |

---

### 2.12 `PracticePdfImportServiceTest` _(bổ sung)_

**File**: `src/test/java/com/ksh/features/practice/service/PracticePdfImportServiceTest.java`

| Test Method | Scenario | Thêm/Sửa |
|---|---|---|
| `testPreviewDraftParsesPdf` | Đã có (stub) | Bổ sung field assertions |
| `testSaveDraftPersistsSet` | Đã có | Bổ sung verify group/question count |
| **`testSaveDraft_emptyGroups_throwsOrSavesEmpty`** | draft.groups() = [] → behavior | **Mới** |
| **`testSaveDraft_publishesWithCorrectStatus`** | Saved set status = PUBLISHED | **Mới** |
| **`testPreviewDraft_invalidFile_throwsIllegalArgument`** | File không phải PDF → exception | **Mới** |
| **`testSaveDraft_groupWithNullAudioUrl_ok`** | audioUrl = null → không crash | **Mới** |

---

### 2.13 `LocalAudioStorageServiceTest` _(mới)_

**File**: `src/test/java/com/ksh/common/storage/LocalAudioStorageServiceTest.java`

| Test Method | Scenario |
|---|---|
| `resolveUrl_validKey_returnsStaticPath` | "set-1/audio.mp3" → "/static/audio/set-1/audio.mp3" |
| `resolveUrl_nullKey_returnsEmpty` | null → "" (resolveUrlSafe) |
| `resolveUrlSafe_nullKey_returnsEmpty` | null → "" |
| `resolveUrlSafe_validKey_returnsPath` | valid → path |

---

### 2.14 `PracticeControllerTest` _(mới — WebMvcTest)_

**File**: `src/test/java/com/ksh/features/practice/controller/PracticeControllerTest.java`

> Dùng `@WebMvcTest(PracticeController.class)` + mock services.

| Test Method | Scenario |
|---|---|
| `getIndex_authenticated_returns200` | GET /practice → 200, model.sets |
| `getSetDetail_authenticated_returns200` | GET /practice/sets/{id} → 200 |
| `getTestDetail_authenticated_returns200` | GET /practice/sets/{id}/tests/{id} → 200 |
| `getMode_authenticated_returns200` | GET /practice/sets/{id}/tests/{id}/mode → 200 |
| `getAttempt_authenticated_returns200` | GET /practice/attempts/{id} → 200 |
| `postCreateAttempt_redirectsToPlayer` | POST /practice/sets/{id}/tests/{id}/attempts → 302 |
| `postSubmitAttempt_redirectsToResult` | POST /practice/attempts/{id}/submit → 302 |
| `getResult_writingSkill_returnsResultTemplate` | GET /practice/attempts/{id}/result (WRITING) → "practice/result" |
| `getResult_readingSkill_returnsRlResultTemplate` | GET /practice/attempts/{id}/result (READING) → "practice/rl-result" |
| `getResultDetail_writingSkill_returnsResultDetailTemplate` | → "practice/result-detail" |
| `getResultDetail_readingSkill_returnsRlResultDetailTemplate` | → "practice/rl-result-detail" |
| `getProfile_authenticated_returns200` | GET /practice/profile → 200 |
| `getUpload_lecturer_returns200` | GET /practice/manage/upload → 200 |
| `getUpload_student_returns403` | student → 403 |
| `getManualForm_lecturer_returns200` | GET /practice/manage/manual → 200 |
| `postPublishDraft_redirectsToSet` | POST /practice/manage/upload/publish → 302 |
| `legacyDetail_redirects` | GET /practice/{id} → 302 to /practice/sets/{id} |
| `legacyResult_redirects` | GET /practice/submissions/{id} → 302 to /practice/attempts/{id}/result |
| `postReEvaluate_redirectsToResult` | POST /practice/attempts/{id}/re-evaluate → 302 |
| `getResultDetail_writingSkill_addsQuestionsJson` | questionsJson attribute exists |
| `getResultDetail_readingSkill_addsGroupsJson` | groupsJson attribute exists |

---

## 3. Repository Tests (DataJpaTest)

**File**: `src/test/java/com/ksh/features/practice/repository/PracticeRepositoryTest.java`

> Dùng `@DataJpaTest` + H2 in-memory hoặc Testcontainers MySQL.

| Test Method | Scenario |
|---|---|
| `findByStatusOrderByCreatedAtDesc_onlyPublished` | Draft set không xuất hiện trong kết quả |
| `findBySetIdOrderByDisplayOrderAsc_questions` | Questions ordered đúng |
| `findBySetIdOrderByDisplayOrderAsc_groups` | Groups ordered đúng |
| `findByIdAndUserId_correctUser` | Trả về submission đúng user |
| `findByIdAndUserId_wrongUser_empty` | User khác → Optional.empty() |
| `findTop20ByUserIdOrderByCreatedAtDesc` | Chỉ lấy 20 gần nhất |
| `findBySetIdAndUserIdOrderByCreatedAtDesc` | Đúng setId và userId |
| `QuestionExplanationCacheRepository_findByQuestionIdAndHash` | Cache hit theo questionId + hash + correctAnswer |
| `QuestionExplanationCacheRepository_miss_empty` | Hash khác → empty |

---

## 4. Integration Tests (bổ sung)

**File**: `src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java`

| Thêm Test | Scenario |
|---|---|
| `testSubmitAttemptWriting_resultDetailView` | Đã có |
| **`testSubmitAttemptReading_correctCount`** | Sau submit R, getReadingListeningResult trả correctCount đúng |
| **`testSubmitAttemptReading_groupsPresent`** | groups không empty |
| **`testGetResultDetail_readingSkill_hasGroupsJson`** | model có groupsJson |
| **`testGetResultDetail_writingSkill_hasQuestionsJson`** | model có questionsJson |
| **`testLegacyRoutes_redirect`** | GET /practice/{id} → 302 |
| **`testLearningProfile_showsRecentAttempts`** | Profile view sau khi submit |

---

## 5. Test Configuration & Fixtures

### 5.1 Test Seed Data

Integration tests dựa vào `@BeforeEach` tạo data hoặc dùng `@Sql` seed script:

```sql
-- Tạo user student/lecturer (đã có trong seed)
-- Tạo practice sets (READING + WRITING)
-- Tạo questions
```

### 5.2 MockMvc Config cho WebMvcTest

```java
@WebMvcTest(PracticeController.class)
@Import({SecurityConfig.class})  // nếu cần security context
class PracticeControllerTest {
    @MockBean PracticeService practiceService;
    @MockBean PracticePdfImportService pdfImportService;
    @MockBean ObjectMapper objectMapper; // hoặc real instance
}
```

### 5.3 Mock AI trong Integration Tests

AI clients dùng API key thật sẽ fail trong CI. Giải pháp:

```java
// Option A: @MockBean WritingEvaluationClient, AnswerExplanationClient
@MockBean WritingEvaluationClient evaluationClient;
// Hoặc
// Option B: Set openai.api-key= (blank) → mock evaluator tự kích hoạt
// application-test.properties: openai.api-key=
```

Khuyến nghị: **Option B** — vì mock evaluator tự kích hoạt khi không có API key, không cần MockBean.

---

## 6. Lệnh chạy test

```powershell
# Chạy toàn bộ test practice
./mvnw test -pl . -Dtest="com.ksh.features.practice.*,com.ksh.common.storage.*"

# Chạy với coverage report (JaCoCo)
./mvnw verify -Djacoco.skip=false

# Chạy chỉ unit test (không integration)
./mvnw test -Dtest="WritingRuleEngineTest,WritingScoreMatrixTest,WritingRubricCriterionTest,WritingEvaluationCacheServiceTest,WritingEvaluationNormalizerTest,WritingMockEvaluatorServiceTest,WritingEvaluationClientTest,AnswerExplanationClientTest,ReadingListeningExplanationClientTest,ReadingListeningExplanationServiceTest,PracticeServiceTest,PracticePdfImportServiceTest,LocalAudioStorageServiceTest,PracticeControllerTest"

# Chạy chỉ integration test
./mvnw test -Dtest="PracticeIntegrationTest,PracticeRepositoryTest"
```

---

## 7. Coverage Report Target

Sau khi implement đủ các test trên, expected coverage:

| Package | Lines | Branches |
|---|---|---|
| `com.ksh.features.practice.ai` | ≥ 90% | ≥ 80% |
| `com.ksh.features.practice.service` | ≥ 95% | ≥ 85% |
| `com.ksh.features.practice.controller` | ≥ 95% | ≥ 90% |
| `com.ksh.features.practice.repository` | ≥ 85% | N/A (interface) |
| `com.ksh.common.storage` | ≥ 95% | ≥ 90% |
| **Overall** | **≥ 90%** | **≥ 80%** |

> Note: 100% line coverage không thực tế với catch blocks và NPE-guard code. Target 90%+ lines + 80%+ branches là production-quality standard.
