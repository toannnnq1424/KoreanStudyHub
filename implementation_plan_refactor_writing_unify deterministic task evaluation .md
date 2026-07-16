# One-Call Writing Evaluation & Scoring Ổn Định — Plan v3 (FINAL)

## Proposed Changes

### Component 1: WritingPromptRules

#### [MODIFY] [WritingPromptRules.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/ai/WritingPromptRules.java)

**1. Version constants:**
```java
public static final String PROMPT_VERSION = "v2.0";
public static final String RUBRIC_VERSION = "v2.0";
public static final String EVALUATION_SCHEMA_VERSION = "v2.0";
```

**2. Q51/Q52 rubric constants (`Q51_52` naming):**
```java
public static final String RUBRIC_Q51_52_CONTENT = "Hoàn thành đúng nội dung & ngữ cảnh (내용의 적절성)";
public static final String RUBRIC_Q51_52_GRAMMAR = "Ngữ pháp & cấu trúc câu (문법 및 문장 구성)";
public static final String RUBRIC_Q51_52_VOCAB   = "Từ vựng, register & tính tự nhiên (어휘 및 자연스러움)";
```

**3. `rubricNamesForTask(taskType)` helper.**

**4. `buildUnifiedPrompt(taskType, isReEvaluation)` — gộp tất cả, giữ đầy đủ bands/anchors/FEW-SHOT/lexical calibration. Không yêu cầu model trả score/raw_score/raw_score_max.**

**5. Xóa `buildOverviewPrompt`, `buildDetailsPrompt`, `buildUpgradePrompt` — 0 caller sau refactor.**

**6. Giữ `taskSpecificRules`, `taskDetailRules`, `taskUpgradeRules`, `auditRules`.**

---

### Component 2: WritingEvaluationClient — One Call

#### [MODIFY] [WritingEvaluationClient.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/ai/WritingEvaluationClient.java)

**Flow mới của `evaluate()`:**
```
1. ruleAnalysis = ruleEngine.analyze(prompt, learnerAnswer)
2. Deterministic spam check (task-aware, xem mục Spam bên dưới)
   → nếu spam chắc chắn: return normalizer.spamResponse(taskType, learnerAnswer), 0 call
3. Nếu apiKey rỗng → mock mode (giữ nguyên)
4. Nếu !isReEvaluation → cache lookup (expanded key)
   → nếu hit: return cached
5. callPass("unified", prompt, userPayload, unifiedResponseFormat())
   - temperature = 0.0
   - top_p = 1.0
   - max_tokens = 4096 (đủ cho Q54 + rubric + findings + upgrade + sample + rewrites)
6. normalized = normalizer.normalize(response, taskType, learnerAnswer, ruleAnalysis)
7. cacheService.put(..., normalized)  ← cả submit lẫn re-evaluate đều ghi cache
8. Return normalized
```

**Spam detection — task-aware, dùng ruleAnalysis:**
- Chỉ short-circuit khi answer **rỗng** (`null`, empty, hoặc chỉ whitespace).
- Chỉ short-circuit khi answer **không chứa bất kỳ ký tự Hangul nào** VÀ taskType không phải Q51_52 (vì Q51/Q52 có thể rất ngắn).
- **Không** dùng `length < 8` blanket cho mọi task.
- **Không** short-circuit Q51/Q52 hợp lệ do answer ngắn.
- Không cache spam response.

**Unified provider schema (additionalProperties=false, required fields, không có score/raw_score/raw_score_max):**
```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["summary","rubric_scores","strengths","needs_improvement",
               "upgraded_answer","upgraded_answer_annotated","sample_answer","sentence_rewrites"],
  "properties": {
    "summary": {"type":"string"},
    "rubric_scores": {"type":"array","items":{...name,score,feedback}},
    "strengths": {"type":"array","items":{...finding}},
    "needs_improvement": {"type":"array","items":{...finding}},
    "upgraded_answer": {"type":"string"},
    "upgraded_answer_annotated": {"type":"string"},
    "sample_answer": {"type":"string"},
    "sentence_rewrites": {"type":"array","items":{...original,upgraded,reason}}
  }
}
```

**Request config:**
```java
request.put("model", properties.evaluatorModel());
request.put("temperature", 0.0);
request.put("top_p", 1.0);
request.put("max_tokens", 4096);
request.put("response_format", unifiedResponseFormat());
```

**`userPayload()` — bỏ overview/details params. Giữ: prompt, learnerAnswer, taskType, charCount, charCountWarning, ruleViolations, allowedRubric, scoreMatrix, isReEvaluation.**

**Xóa hẳn (0 caller):** `evaluateOverview`, `evaluateDetails`, `evaluateUpgrade`, `merge`, `emptyDetails`, `emptyUpgrade`, `overviewResponseFormat`, `detailsResponseFormat`, `upgradeResponseFormat`, `overviewSchema`, `detailsSchema`, `upgradeSchema`.

---

### Component 3: WritingEvaluationNormalizer — Sole Score Source

#### [MODIFY] [WritingEvaluationNormalizer.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/ai/WritingEvaluationNormalizer.java)

**1. Production path — new overload:**
```java
public String normalize(String aiJson, String taskType, String learnerAnswer,
                        WritingRuleEngine.RuleAnalysis ruleAnalysis)
```
- Không đọc `student_text` từ AI JSON để khôi phục learnerAnswer.
- `taskType` truyền vào, không đọc từ JSON.

**2. Scoring — equal weights (không có weighting đáng tin cậy trong code hiện tại):**

`WritingRubricCriterion.weight()` trả `1.0` cho tất cả — đây là finding-level, không phải macro rubric. `WritingPromptRules` và `WritingScoreMatrix` không có rubric weights. Không tự phát minh weights mới.

```java
double finalScore = average(rubric_scores[0].score, rubric_scores[1].score, rubric_scores[2].score);
finalScore = WritingScoreMatrix.clampAndRound(finalScore);
```

> **Limitation:** Scoring weights chưa có trong code. Equal average dùng tạm. Task-specific weights sẽ xử lý ở task riêng khi có scoring policy.

**3. Không dùng `backendScoreFromEvidence`. Không dùng `strengths.size()` hoặc `needs.size()`.**

**4. Không parse chuỗi `charCountWarning` để tính penalty. Không tự thêm CRITICAL=-2.0 hay WARNING=-0.5.** Dữ liệu `ruleAnalysis` (taskType, characterCount) được truyền vào nhưng không có character penalty rule nào được định nghĩa trong `WritingScoreMatrix` hiện tại → không áp dụng penalty mới. `charCountWarning` đã được gửi cho AI trong prompt → AI tự điều chỉnh rubric scores.

**5. `enforceTaskRubrics(rows, taskType)`:** Thay `enforceThreeRubrics`.

**6. Evidence validation — dùng `learnerAnswer` nguyên bản:**
- Loại finding nếu evidence không phải substring.
- Loại sentence_rewrites nếu original không phải substring.
- Không sửa evidence.
- `criterionId` validate theo whitelist `WritingRubricCriterion.parse()`.

**7. `spamResponse(taskType, learnerAnswer)` — deterministic.**

**8. Giữ `normalize(String aiJson)` cho backward compatibility tạm (mock + PracticeService fallback). Không dùng trong production one-call.**

---

### Component 4: WritingEvaluationCacheService

#### [MODIFY] [WritingEvaluationCacheService.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/ai/WritingEvaluationCacheService.java)

**Cache key:**
```
SHA-256(normalizedPrompt + "|" + normalizedAnswer + "|" + taskType 
       + "|" + model + "|" + promptVersion + "|" + rubricVersion + "|" + schemaVersion)
```

**Injectable Clock cho TTL test.**

---

### Component 5: WritingScoreMatrix

#### [MODIFY] [WritingScoreMatrix.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/ai/WritingScoreMatrix.java)

Giữ `backendScoreFromEvidence` cho mock. Không thêm `rubricWeightsForTask` — equal average tạm, limitation ghi rõ.

---

### Component 6: Tests

Giữ nguyên test plan từ v2 + regression tests cho mock/fallback normalize compatibility.

---

## Limitations

- Cache in-memory, mất khi restart.
- Multi-question Writing section chưa hỗ trợ đầy đủ.
- Chưa tuyên bố Q51–Q54 tổng 100.
- Mock evaluator chưa hợp nhất.
- PracticeService fallback chưa hợp nhất.
- **Scoring weights: equal average tạm. Task-specific weights cần scoring policy riêng.**
- **Character penalty: không áp dụng Java-side. AI tự điều chỉnh qua charCountWarning trong prompt.**
