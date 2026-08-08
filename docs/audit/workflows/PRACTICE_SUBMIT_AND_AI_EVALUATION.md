# Workflow: học sinh nộp Practice và chuỗi chấm AI

## 1. Button và request từ frontend

Với Reading/Listening, form player nằm ở `practice/player.html:7–10`; nút **“Nộp bài”** ở dòng 41 và 319. Form gửi:

```text
POST /practice/attempts/{attemptId}/submit
Content-Type: application/x-www-form-urlencoded
```

Các field quan trọng:

- answer field theo question id;
- `mode` (`practice` mặc định);
- `expectedLockVersion`: optimistic concurrency token;
- CSRF token.

Writing dùng `practice/player-writing.html`; Speaking dùng player/recording/media workflow riêng nhưng điểm hội tụ cuối vẫn là attempt submit/evaluation job.

Trước khi nộp, player có thể autosave bằng:

```text
PUT /practice/attempts/{attemptId}/answers?expectedLockVersion=...
```

`PracticeController.saveAttemptAnswers`, dòng 581–650, trả JSON `SAVED`, lock version mới, saved time/deadline/answers; stale tab trả `409 CONFLICT`, hết hạn trả `410 GONE`.

## 2. Controller nhận submit

`PracticeController.submitAttempt`, dòng 652–692:

1. Lấy attempt bằng `(attemptId, principal.userId)`; user không đọc/nộp attempt người khác.
2. Nếu attempt đã terminal, không nộp lại; redirect theo trạng thái hiện tại.
3. Gọi `PracticeService.submitAttempt(attemptId, userId, form, expectedLockVersion)` tại dòng 666–667.
4. Hết deadline: Speaking chưa hoàn tất media bị discard; skill khác nộp đáp án đã autosave trước deadline.
5. Conflict giữa hai tab/request: reload winner state thay vì chấm hai lần.
6. Thành công xóa preflight session state, flash “Đã nộp bài luyện tập”, redirect `GET /practice/attempts/{id}/result`.

HTTP request không chờ AI trả lời. Redirect result có thể hiển thị trạng thái đang phân tích.

## 3. Service chia ba nhánh

`PracticeService.submitAttempt`, dòng 1929–1983:

1. Đọc attempt, buộc `IN_PROGRESS`.
2. Kiểm deadline và `expectedLockVersion`.
3. Nếu deadline đã qua, chỉ dùng answers đã lưu; bỏ form đến muộn.
4. Thử dựng `WritingGradingSnapshot`.
5. Nếu không phải Writing, thử dựng `SpeakingGradingSnapshot`.
6. Nếu không phải subjective skill, đi `submitAttemptInTransaction` cho Reading/Listening.

### Nhánh A — Reading/Listening: không gọi AI khi submit

`submitAttemptInTransaction`, dòng 2155–2245:

- khóa attempt bằng `findByIdAndUserIdForUpdate`;
- xác minh attempt/section/set/test/skill khớp immutable published version;
- merge chỉ answer field thuộc question trong section, chống gửi thêm question id;
- từng câu gọi `scoreObjective` theo canonical assessment contract/answer key;
- cộng `earnedPoints`, `total`;
- `attempt.markSubmitted(score, total, answersJson)` và save.

Kết luận: nút submit Reading/Listening chấm đúng/sai ở Java. AI explanation, nếu có, là pipeline/result capability riêng; không phải authority quyết định điểm objective.

### Nhánh B — Writing: lưu snapshot và queue AI

`queueWritingSubmission`, dòng 2002–2045:

1. Khóa attempt.
2. Verify DB lock version vẫn bằng snapshot và browser token.
3. Serialize answers; nếu deadline vừa hết thì dùng autosaved answers.
4. Tính total points.
5. Tạo fingerprint từ immutable version + answers.
6. `markSubmittedForAnalysis`: attempt rời `IN_PROGRESS`, lưu answers/total và trạng thái analysis pending.
7. Flush attempt.
8. Insert duy nhất một `PracticeAttemptEvaluationJob` với:
   - operation `SUBMIT`;
   - status `QUEUED`;
   - fingerprint;
   - evaluation contract identity;
   - max attempts `3`;
   - expiry window.

Unique/insert-if-absent và fingerprint ngăn double-submit tạo hai job khác nhau.

### Nhánh C — Speaking: media là bằng chứng bắt buộc

`queueSpeakingSubmission`, dòng 2047–2098:

- snapshot phải chứa immutable speaking media identity;
- hết deadline thì reject/discard, không nộp form rỗng như objective;
- fingerprint gồm answers và media identity;
- nếu speaking evaluator bị tắt, attempt chuyển ngay `analysis unavailable`, job lưu `UNAVAILABLE/SPEAKING_AI_DISABLED`;
- nếu enabled, job `QUEUED` giống Writing.

Không có đường gửi text giả thay audio để được chấm Speaking.

## 4. Worker lấy job và gọi evaluator

`PracticeAttemptEvaluationProcessor`:

1. `runScheduledBatch`, dòng 166–173, chạy mặc định mỗi 2 giây.
2. `processDue`, dòng 175–225, tìm claimable id, dùng semaphore concurrency 2, claim lease có owner/expiry.
3. Worker tạo heartbeat 30 giây và timeout tối đa 20 phút.
4. Gọi `PracticeService.evaluateClaimedAttempt` tại dòng 291–292.
5. Service reload attempt, xác minh contract identity và input fingerprint chưa đổi (`PracticeService:3399–3447`).
6. Chọn `gradeWritingSnapshot`, per-question Writing re-evaluation, hoặc `gradeSpeakingSnapshot` (`3485–3505`).
7. Serialize `PracticeAttemptEvaluationOutcome`, complete job trong transaction có fencing; kết quả từ lease cũ/timeout bị bỏ.
8. Contract changed là terminal failure; internal/provider retryable failure được job transaction xử lý theo attempts/backoff.

## 5. Writing gửi gì đến AI

`WritingEvaluationClient.evaluate`, dòng 115–265:

1. Resolve ảnh đề được phép và task type (`Q51/Q52/Q53/Q54/GENERAL`).
2. Rule engine phân tích deterministic.
3. Answer rỗng hoặc không có Hangul: trả spam response, **0 provider call**.
4. Submit thường đọc cache; re-evaluate bỏ qua cache.
5. Binding `PRACTICE_WRITING_EVALUATION` không available: trả envelope `MISSING_API_KEY`, không gọi mạng.
6. Build system prompt bằng `WritingPromptRules.buildUnifiedPrompt`.
7. Build user payload tại `WritingEvaluationClient:382–431` gồm:
   - `skill_type=WRITING`, platform, level;
   - policy bundle id/components;
   - prompt đề bài;
   - `learner_answer` đã NFC normalize;
   - source SHA-256 và offset unit UTF-16;
   - task type, character count, warnings/rule violations;
   - image metadata nếu có;
   - `is_re_evaluation`, `audit_mode`;
   - allowed rubric, scoring anchors;
   - task requirements;
   - output contract version.
8. Tạo `PracticeStructuredGenerationRequest` tại dòng 338–357:
   - purpose `PRACTICE_WRITING_EVALUATION`;
   - capability `STRICT_STRUCTURED_TEXT_VISION`;
   - system prompt + JSON user payload;
   - optional image evidence;
   - strict JSON schema name `ksh_writing_unified`;
   - max output tokens 4096.
9. Structured generation port resolve đúng enabled purpose binding → provider profile → credential → model/base URL, rồi mới gọi provider.

## 6. AI bắt buộc trả chuỗi nào

AI không được trả Markdown, prose hay JSON tùy ý. `WritingEvaluationClient.unifiedSchema`, dòng 545–650, yêu cầu strict JSON object:

```json
{
  "schemaVersion": "<đúng EVALUATION_SCHEMA_VERSION>",
  "promptVersion": "<đúng PROMPT_VERSION>",
  "scoreAnchorVersion": "<đúng score anchor version>",
  "taskRequirementVersion": "<đúng task requirement version>",
  "rubricScores": [
    {
      "criterionId": "...",
      "score": 0,
      "maxScore": 0,
      "evidenceIds": ["..."],
      "findingIds": ["..."],
      "requirementIds": ["..."]
    }
  ],
  "taskCoverage": [
    {"requirementId": "...", "status": "MET|PARTIAL|NOT_MET|NOT_APPLICABLE", "evidenceIds": []}
  ],
  "evidenceLedger": [
    {
      "evidenceId": "...",
      "sourceRole": "LEARNER_ANSWER",
      "exactText": "...",
      "startOffset": 0,
      "endOffset": 1,
      "occurrenceIndex": 1,
      "occurrenceCount": 1,
      "normalization": "NFC",
      "sourceHash": "<SHA-256 nguồn>"
    }
  ],
  "findings": [
    {
      "findingId": "...",
      "polarity": "STRENGTH|IMPROVEMENT",
      "operation": "KEEP|MISSING|REPLACE|REDUNDANT",
      "criterionId": "...",
      "subtype": "...",
      "scoringCriterionId": null,
      "errorCategory": "...",
      "evidenceIds": [],
      "requirementIds": [],
      "explanationVi": "...",
      "replacementKo": "...",
      "impact": "MINOR|MODERATE|MAJOR|BLOCKING",
      "frequency": 1,
      "confidence": 0.0,
      "observability": "DIRECT|INFERRED_BOUNDED"
    }
  ],
  "upgradedAnswer": {
    "content": "...",
    "rewrites": [
      {"findingIds": [], "evidenceId": "...", "replacementKo": "...", "reasonVi": "..."}
    ]
  }
}
```

Giá trị version, criterion id, requirement id và score không phải placeholder tự do: phải nằm đúng allowlist/anchor gửi trong request. Evidence offset phải khớp exact substring theo UTF-16 và source hash.

## 7. Backend xử lý response AI

`WritingEvaluationClient:233–265` serialize provider output rồi gọi `WritingEvaluationNormalizer.normalize`.

Normalizer/backend là authority cuối:

- verify schema/version/evidence ledger/source hash/offset;
- verify criterion/subtype/requirement/score anchors;
- tự tính envelope score, không tin `overall score` do provider tự bịa;
- malformed/contract failure thành non-score-bearing result;
- chỉ cache kết quả AI hợp lệ;
- HTTP 429/5xx/transport được phân loại retryable; unexpected/contract error fail closed.

Stored feedback mỗi essay question phải parse thành current contract bằng `WritingFeedbackContractParser`. Score-bearing entry cần `raw_score`, `raw_score_max`, evaluation metadata và `result_completeness` nhất quán. `PracticeService.extractAiScore`, dòng 1435–1470, từ chối JSON sai current score contract.

## 8. Hoàn tất job và UI kết quả

Worker complete transaction cập nhật attempt bằng result đã normalized, không phải raw provider response:

- success: score, total, answers, feedback JSON, analysis success;
- unavailable/failed: không dựng điểm giả; lưu error code/retryable/completeness;
- job lưu terminal result JSON/audit.

Browser đã được redirect tới `GET /practice/attempts/{id}/result`. `PracticeController.attemptResult`, dòng 807–818, gọi result assembler. UI có thể hiển thị pending, succeeded, failed hoặc unavailable. Trang detail dùng `PracticeResultDetailAssembler` và chọn template objective/writing/speaking ở dòng 821–834.

Nút chấm lại gửi `POST .../re-evaluate` (`PracticeController:837–860`), tạo operation re-evaluation mới với fingerprint/contract hiện tại; không sửa trực tiếp điểm từ UI.
