# Practice Feature — AI Scoring (4 kỹ năng TOPIK)

> **Tài liệu kỹ thuật** | KSH Korean Study Hub | Phiên bản: 2.0
> Phạm vi: `com.ksh.features.practice.*` · `com.ksh.common.storage.*` · `src/main/resources/templates/practice/*`

---

## 1. Tổng quan Feature

Feature Practice cho phép học viên luyện tập 4 kỹ năng TOPIK (Nghe, Đọc, Viết, Nói) với phản hồi AI hoàn toàn bằng tiếng Việt. Giáo viên upload đề từ PDF, AI parse thành câu hỏi, học viên làm bài theo nhóm (Group-based CBT), hệ thống chấm và trả về feedback chi tiết theo từng skill.

### Luồng dữ liệu tổng quát

```
PDF Upload → PracticePdfImportService (AI parse) → practice_sets / practice_question_groups / practice_questions (MySQL)
                                                                │
Student → GET /practice/sets/{setId}/tests/{testId}/mode → mode.html
                                                                │
        POST /practice/sets/{setId}/tests/{testId}/attempts ──┘  (tạo PracticeSubmission IN_PROGRESS)
                                                                │
        GET  /practice/attempts/{attemptId}             → player.html (CBT, Group 3-Column)
                                                                │
        POST /practice/attempts/{attemptId}/submit ─────────────┘
               │                  │                  │
          MCQ/key-match    ESSAY→WritingEval    SPEAKING→mockSpeaking
               │                  │                  │
               └──────────────────┴──────────────────┘
                                   │
                    practice_submissions (STATUS_GRADED / STATUS_SUBMITTED)
                                   │
         ┌─────────────────────────┴──────────────────────────┐
         ▼                                                     ▼
  READING/LISTENING                                   WRITING/SPEAKING
  GET /practice/attempts/{id}/result → rl-result.html    → result.html
  GET /practice/attempts/{id}/result/detail → rl-result-detail.html → result-detail.html
```

---

## 2. Domain Model

### 2.1 Entities

| Entity | Bảng DB | Trường quan trọng |
|---|---|---|
| `PracticeSet` | `practice_sets` | `skill` (READING/LISTENING/WRITING/SPEAKING), `topik_level`, `scope` (GLOBAL/CLASS), `status` (PUBLISHED/DRAFT), `class_id`, `source_pdf_path`, `metadata_json`, `created_by_id` |
| `PracticeQuestionGroup` | `practice_question_groups` | `set_id`, `group_label`, `question_from`, `question_to`, `instruction`, `audio_url`, `example_json`, `display_order` |
| `PracticeQuestion` | `practice_questions` | `set_id`, `group_id`, `question_no`, `question_type`, `prompt`, `options_json`, `answer_key`, `explanation`, `points`, `display_order` |
| `PracticeSubmission` | `practice_submissions` | `set_id`, `user_id`, `score`, `total_points`, `answers_json`, `ai_feedback_json`, `status` (IN_PROGRESS/SUBMITTED/GRADED), `submitted_at` |
| `QuestionExplanationCache` | `question_explanation_cache` | `question_id`, `test_id`, `skill_type`, `question_type`, `question_hash` (SHA-256), `correct_answer`, `explanation_json`, `ai_model` |

### 2.2 Hằng số Skill

```java
PracticeSet.SKILL_READING    = "READING"
PracticeSet.SKILL_LISTENING  = "LISTENING"
PracticeSet.SKILL_WRITING    = "WRITING"
PracticeSet.SKILL_SPEAKING   = "SPEAKING"
```

### 2.3 Các loại câu hỏi (question_type)

| Type | Hằng số | Chấm điểm |
|---|---|---|
| MCQ | `TYPE_MCQ` | Key matching (normalize) |
| SHORT_TEXT | `TYPE_SHORT_TEXT` | Key matching |
| TRUE_FALSE_NOT_GIVEN | `TYPE_TRUE_FALSE_NOT_GIVEN` | Key matching |
| MATCHING_INFORMATION | `TYPE_MATCHING_INFORMATION` | Key matching |
| FILL_BLANK | `TYPE_FILL_BLANK` | Key matching |
| ORDERING | `TYPE_ORDERING` | Key matching |
| TEXT_COMPLETION | `TYPE_TEXT_COMPLETION` | Key matching |
| **ESSAY** | `TYPE_ESSAY` | **AI 3-pass evaluation** (WritingEvaluationClient) |
| **SPEAKING** | `TYPE_SPEAKING` | **Mock heuristic** (word-count) |

**Normalize key**: `trim()` → `replaceAll("\\s+"," ")` → replace `／→/` và `，→,` → `toUpperCase()`.

---

## 3. Endpoints

### 3.1 Bảng Route đầy đủ

| Method | URL | Mô tả | Auth | View |
|---|---|---|---|---|
| GET | `/practice` | Kho luyện tập | Authenticated | `practice/index` |
| GET | `/practice/sets/{setId}` | Chi tiết bộ đề | Authenticated | `practice/set-detail` |
| GET | `/practice/sets/{setId}/tests/{testId}` | Chi tiết đề thi + lịch sử làm bài | Authenticated | `practice/test-detail` |
| GET | `/practice/sets/{setId}/tests/{testId}/mode` | Chọn chế độ làm bài | Authenticated | `practice/mode` |
| POST | `/practice/sets/{setId}/tests/{testId}/attempts` | Tạo lượt làm bài (IN_PROGRESS) | Authenticated | redirect → player |
| GET | `/practice/attempts/{attemptId}` | Phòng làm bài (CBT) | Authenticated | `practice/player` |
| POST | `/practice/attempts/{attemptId}/submit` | Nộp bài | Authenticated | redirect → result |
| GET | `/practice/attempts/{attemptId}/result` | Kết quả tổng quan | Authenticated | `practice/result` hoặc `practice/rl-result` |
| GET | `/practice/attempts/{attemptId}/result/detail` | Kết quả chi tiết từng câu | Authenticated | `practice/result-detail` hoặc `practice/rl-result-detail` |
| POST | `/practice/attempts/{attemptId}/re-evaluate` | Chấm lại Writing/Speaking | Authenticated | redirect → result |
| GET | `/practice/profile` | Learning profile | Authenticated | `practice/profile` |
| GET | `/practice/manage/upload` | Form upload PDF | LECTURER+ | `practice/upload` |
| GET | `/practice/manage/manual` | Form tạo đề thủ công | LECTURER+ | `practice/upload-preview` |
| POST | `/practice/manage/upload/preview` | Preview bản nháp từ PDF | LECTURER+ | `practice/upload-preview` |
| POST | `/practice/manage/upload/publish` | Xuất bản bản nháp | LECTURER+ | redirect → set-detail |

### 3.2 Legacy Redirects (backward compat)

| Legacy URL | Redirect Target |
|---|---|
| GET `/practice/{setId}` | `/practice/sets/{setId}` |
| GET `/practice/{setId}/detail` | `/practice/sets/{setId}` |
| GET `/practice/{setId}/mode` | `/practice/sets/{setId}/tests/{setId}/mode` |
| GET `/practice/{setId}/room` | `/practice/sets/{setId}/tests/{setId}` |
| POST `/practice/{setId}/submit` | `/practice/sets/{setId}` |
| GET `/practice/submissions/{id}` | `/practice/attempts/{id}/result` |
| POST `/practice/submissions/{id}/re-evaluate` | `/practice/attempts/{id}/result` |

### 3.3 URL Routing theo Skill

Controller tự động phân nhánh template theo skill:

```java
if ("READING" or "LISTENING"):
    GET /result       → "practice/rl-result"
    GET /result/detail → "practice/rl-result-detail"
else (WRITING / SPEAKING):
    GET /result        → "practice/result"
    GET /result/detail → "practice/result-detail"
```

### 3.4 Model Attributes

**GET /practice/sets/{setId}**
```
model.view      : PracticeSetView   (set + groups)
model.submissions: List<PracticeSubmission> (filtered: not IN_PROGRESS)
```

**GET /practice/sets/{setId}/tests/{testId}**
```
model.view      : PracticeSetView
model.testId    : Long
model.attempts  : List<PracticeSubmission> (filtered: not IN_PROGRESS)
model.bestScore : BigDecimal (max score của user cho set này)
```

**GET /practice/attempts/{attemptId}**
```
model.view      : PracticeSetView
model.mode      : String ("practice" | "exam")
model.attemptId : Long
```

**GET /practice/attempts/{attemptId}/result** (WRITING/SPEAKING)
```
model.result    : PracticeResultView
model.attemptId : Long
```

**GET /practice/attempts/{attemptId}/result** (READING/LISTENING)
```
model.result    : ReadingListeningResultView
model.attemptId : Long
```

**GET /practice/attempts/{attemptId}/result/detail** (WRITING/SPEAKING)
```
model.result        : PracticeResultView
model.attemptId     : Long
model.questionsJson : String (JSON array for JS)
```

**GET /practice/attempts/{attemptId}/result/detail** (READING/LISTENING)
```
model.result     : ReadingListeningResultView
model.attemptId  : Long
model.groupsJson : String (JSON array for JS)
```

---

## 4. DTO Definitions

```java
// PracticeSetRow — summary card
record PracticeSetRow(Long id, String title, String description,
                      String skill, String topikLevel, String badgeText)

// PracticeQuestionGroupRow — group with nested questions
record PracticeQuestionGroupRow(Long id, String groupLabel, Integer questionFrom, Integer questionTo,
                                String instruction, String audioUrl, ExampleBox exampleBox,
                                List<PracticeQuestionRow> questions)

// PracticeQuestionRow — single question
record PracticeQuestionRow(Long id, Integer questionNo, String questionType,
                           String prompt, List<String> options,
                           String answerKey, String explanation, String groupLabel)

// ExampleBox — optional example in group header
record ExampleBox(String label, String content, List<String> choices, Integer answer)

// PracticeSetView — full view for player/detail
record PracticeSetView(PracticeSetRow set, List<PracticeQuestionGroupRow> groups)
// helpers: writing(), listening(), reading(), speaking(), totalQuestions()

// PracticeResultView — Writing/Speaking result
record PracticeResultView(Long submissionId, PracticeSetRow set,
                          BigDecimal score, BigDecimal totalPoints,
                          String scoreLabel, String answersJson,
                          String aiFeedbackJson, List<PracticeQuestionRow> questions,
                          List<PracticeAnswerReviewRow> answerReviews,
                          List<PracticeAnswerExplanationRow> answerExplanations)
// helpers: hasAiFeedback(), hasRubricFeedback(), hasAnswerExplanations()

// PracticeAnswerReviewRow — for W/S result detail (JS consumption)
record PracticeAnswerReviewRow(Long questionId, Integer questionNo,
                               String questionType, String prompt, String learnerAnswer)

// PracticeAnswerExplanationRow — for R/L old-style result
record PracticeAnswerExplanationRow(Integer questionNo, String questionType, String prompt,
                                    String learnerAnswer, String answerKey,
                                    String meaningVi, String transcriptExplanationVi,
                                    String eliminatedOptionsVi)

// ReadingListeningResultView — R/L result (overall + detail)
record ReadingListeningResultView(Long submissionId, PracticeSetRow set,
                                  BigDecimal score, BigDecimal totalPoints,
                                  int correctCount, int incorrectCount, int totalCount,
                                  List<PerformanceByTypeRow> performanceByType,
                                  List<ReviewGroupRow> groups, String answersJson)

// PerformanceByTypeRow — accuracy by question type
record PerformanceByTypeRow(String questionType, String label,
                            int total, int correct, int incorrect, String accuracyPct)

// ReviewGroupRow — group with review questions
record ReviewGroupRow(String groupLabel, String instruction,
                      String passageText, String audioUrl,
                      List<ReviewQuestionRow> questions)

// ReviewQuestionRow — individual question review with explanation
record ReviewQuestionRow(Long questionId, Integer questionNo, String questionType,
                         String prompt, List<String> options, String correctAnswer,
                         String userAnswer, boolean isCorrect, String explanationJson)

// LearningProfileView — user history
record LearningProfileView(List<PracticeResultSummary> recent,
                           long readingCount, long listeningCount,
                           long writingCount, long speakingCount,
                           BigDecimal averageScore)

// PracticeResultSummary — one row in profile
record PracticeResultSummary(Long id, String title, String skill,
                             BigDecimal score, BigDecimal totalPoints, LocalDateTime submittedAt)

// PracticePdfDraftView — import draft
record PracticePdfDraftView(String title, String description, String skill, String topikLevel,
                            String scope, Long classId, String sourcePdfPath,
                            String metadataJson, List<PracticeDraftGroup> groups,
                            String originalFilename)
// helper: questions() → flatten tất cả câu hỏi từ groups

// PracticeDraftGroup / PracticeDraftQuestion — draft structures
// PracticePdfImportResult — result after publish: (setId, title, questionCount)
```

---

## 5. Service Layer

### 5.1 PracticeService — Public API

| Method | Signature | Ghi chú |
|---|---|---|
| `listPublished` | `() → List<PracticeSetRow>` | Chỉ trả về status=PUBLISHED |
| `getPractice` | `(Long setId) → PracticeSetView` | Throw EntityNotFoundException nếu không tồn tại hoặc không PUBLISHED |
| `startAttempt` | `(Long setId, Long userId) → Long` | Tạo submission IN_PROGRESS, trả về ID |
| `submitAttempt` | `(Long attemptId, Long userId, Map<String,String> form) → Long` | Chấm điểm + AI feedback, update status |
| `submit` (legacy) | `(Long setId, Long userId, Map<String,String> form) → Long` | Legacy flow, tạo submission mới |
| `reEvaluate` | `(Long submissionId, Long userId) → Long` | Chấm lại Writing/Speaking với Audit Mode |
| `getResult` | `(Long submissionId, Long userId) → PracticeResultView` | Dành cho W/S |
| `getReadingListeningResult` | `(Long submissionId, Long userId) → ReadingListeningResultView` | Dành cho R/L, trigger lazy cache |
| `getPracticeSubmission` | `(Long submissionId, Long userId) → PracticeSubmission` | Get raw entity |
| `getAttempts` | `(Long setId, Long userId) → List<PracticeSubmission>` | Lịch sử làm bài |
| `getLearningProfile` | `(Long userId) → LearningProfileView` | Profile với top 20 kết quả gần nhất |

### 5.2 Scoring Logic

**MCQ và auto-scored types**: `answersMatch(answer, answerKey)` → `score += points`.

**Essay (Writing)**:
1. Gọi `WritingEvaluationClient.evaluate(prompt, answer)`.
2. Parse JSON, extract `score` hoặc `overall_score`.
3. Chuyển sang thang 100 điểm: `WritingScoreMatrix.toHundredPointScale(score)`.

**Speaking**: `mockSpeakingFeedback(prompt, answer)` — word-count heuristic.

**Reading/Listening**: Key-match chấm điểm; `ReadingListeningExplanationService.getOrCreateExplanation()` lazy-load explanation.

### 5.3 PracticePdfImportService

| Method | Ghi chú |
|---|---|
| `previewDraft(file, metadata...) → PracticePdfDraftView` | Gọi AI parse PDF, trả về draft chưa lưu |
| `saveDraft(draft, userId) → PracticePdfImportResult` | Persist PracticeSet + Groups + Questions |

### 5.4 ReadingListeningExplanationService

```
getOrCreateExplanation(question, passageText, skillType, testId) → String (JSON)
```
- **Double-checked locking** trên `String.intern()` của questionId.
- Cache key: SHA-256(prompt + optionsJson + answerKey).
- Miss → gọi `ReadingListeningExplanationClient.explain()` → lưu vào `question_explanation_cache`.

---

## 6. AI Clients

### 6.1 WritingEvaluationClient — 3-Pass Evaluation

| Pass | Method | System prompt | JSON schema | Output fields |
|---|---|---|---|---|
| 1: Overview | `evaluateOverview()` | `WritingPromptRules.buildOverviewPrompt(taskType, isReEval)` | `ksh_writing_overview` | `score`, `raw_score`, `raw_score_max`, `summary`, `rubric_scores[]` |
| 2: Details | `evaluateDetails()` | `WritingPromptRules.buildDetailsPrompt(taskType)` | `ksh_writing_details` | `strengths[]`, `needs_improvement[]` |
| 3: Upgrade | `evaluateUpgrade()` | `WritingPromptRules.buildUpgradePrompt(taskType)` | `ksh_writing_upgrade` | `upgraded_answer`, `upgraded_answer_annotated`, `sample_answer`, `sentence_rewrites[]` |

**Merge**: `merge(overview, details, upgrade, ruleAnalysis, learnerAnswer)` → `ObjectNode`.
**SPAM**: Nếu `summary.contains("[SPAM_DETECTED]")` → bỏ qua Pass 2 & 3.
**Retry**: 5 lần, exponential backoff 3s→30s, HTTP 429/500/502/503/504.
**Cache**: `WritingEvaluationCacheService` in-memory SHA-256, TTL 30 phút.
**Mock fallback**: `WritingMockEvaluatorService` khi `apiKey` null/blank hoặc exception.

### 6.2 WritingEvaluationNormalizer

Normalize raw AI JSON thành chuẩn:
- Extract `score` (fallback `overall_score`).
- `backendScoreFromEvidence(rawScore, strengthCount, needCount)` → điều chỉnh theo evidence.
- Build `annotations[]` từ `findEvidencePosition(evidence, studentText)`.
- Output: `score`, `overall_score`, `raw_score`, `raw_score_max`, `task_type`, `band_label`, `summary`, `rubric_scores[]`, `strengths[]`, `needs_improvement[]`, `student_text`, `annotations[]`, `upgraded_answer`, `sample_answer`, `sentence_rewrites[]`.

### 6.3 WritingRuleEngine

```
analyze(prompt, answer) → RuleAnalysis(taskType, characterCount, charCountWarning, ruleViolations)
```
- `detectTaskType()`: Q51_52 / Q53 / Q54 / GENERAL từ keyword trong prompt.
- `detectSpokenLanguage()`: 18 khẩu ngữ blacklist (근데, 해요, 같아요, ...).
- `buildCharCountWarning()`: Kiểm tra character count theo yêu cầu dạng câu.

### 6.4 WritingScoreMatrix

| Method | Ghi chú |
|---|---|
| `clampAndRound(score)` | Clamp [1.0, 9.0] → round 0.5 |
| `backendScoreFromEvidence(score, strengths, needs)` | Điều chỉnh điểm theo evidence count |
| `rawScoreMax(taskType)` | Q51_52→10; Q53→30; Q54→50; GENERAL→100 |
| `rawScoreFromNormalized(normalizedScore, taskType)` | Tính điểm thô TOPIK |
| `toHundredPointScale(score)` | Chuyển sang thang 100 điểm UI |
| `bandLabel(score)` | "Xuất sắc"→"Không phản hồi" (9 band) |

### 6.5 WritingRubricCriterion (enum)

**STRENGTH (7)**: W_ADVANCED_GRAMMAR_STRUCTURES, W_REGISTER_HONORIFIC_ACCURACY, W_APPROPRIATE_VOCABULARY_USAGE, W_TOPIC_SPECIFIC_EXPRESSIONS, W_NATURAL_KOREAN_EXPRESSIONS, W_WON_GO_JI, W_SENTENCE_VARIETY

**NEEDS_IMPROVEMENT (8)**: W_VOCABULARY_ERRORS, W_GRAMMAR_ERRORS, W_PARTICLE_ERRORS, W_REPETITIVE_WORDS_EXPRESSIONS, W_AWKWARD_UNNATURAL_EXPRESSIONS, W_SENTENCE_STRUCTURE_ISSUES, W_REGISTER_CONSISTENCY_ISSUES, W_SPELLING_SPACING_ERRORS

### 6.6 AnswerExplanationClient (Legacy R/L)

- Gọi AI giải thích từng câu R/L (theo batch set).
- JSON schema `ksh_practice_explanations`, output: `items[]` với `questionId`, `meaningVi`, `transcriptExplanationVi`, `eliminatedOptionsVi`.
- Retry: 3 lần, backoff 1.5s→3s→6s.
- Fallback: JSON với field từ `question.explanation` hoặc message lỗi.

### 6.7 ReadingListeningExplanationClient (New per-question cache)

- Gọi AI giải thích **từng câu** R/L riêng lẻ.
- JSON schema `rl_answer_explanation`, output: `questionId`, `questionType`, `correctAnswer`, `correctAnswers[]`, `keywords{question[], passage[]}`, `evidence[]`, `reasonVi`, `wrongOptions[]`, `matchingExplanations[]`, `acceptedAnswers[]`, `translationVi`.
- Retry: 3 lần, backoff 1.5s→3s→6s.
- Fallback: JSON với `reasonVi` = `question.explanation` hoặc message.

---

## 7. Storage Layer

### 7.1 AudioStorageService (Interface)

```java
String resolveUrl(String audioKey)          // Throw nếu null
String resolveUrlSafe(String audioKey)      // Return "" nếu null
String store(String audioKey, byte[] bytes)
void delete(String audioKey)
```

**audioKey**: Logical key lưu trong DB (không phải full URL), ví dụ `practice/set-12/audio.mp3`.

### 7.2 LocalAudioStorageService (Default)

- Kích hoạt khi không có CloudflareR2StorageService.
- Serve từ `/static/audio/` trong classpath.
- `resolveUrl()` → `"/static/audio/" + audioKey`.

### 7.3 CloudflareR2StorageService (Skeleton)

- Implement `@ConditionalOnMissingBean(AudioStorageService.class)`.
- Dùng AWS S3 SDK (S3-compatible với R2 endpoint).
- Developer tích hợp: uncomment `@Service`, cấu hình `app.storage.*` properties.

### 7.4 Config Properties (application.properties)

```properties
app.storage.provider=local          # "local" | "r2"
app.storage.r2.account-id=          # Cloudflare account ID
app.storage.r2.access-key=          # R2 access key
app.storage.r2.secret-key=          # R2 secret key
app.storage.r2.bucket-name=         # R2 bucket name
app.storage.r2.cdn-url=             # Public CDN URL (optional)
```

---

## 8. Frontend Templates

| File | Route | Mô tả |
|---|---|---|
| `practice/index.html` | `/practice` | Dashboard kho luyện tập — 3 cột (sidebar filter, cards, rightbar) |
| `practice/set-detail.html` | `/practice/sets/{setId}` | Chi tiết bộ đề + lịch sử làm bài |
| `practice/test-detail.html` | `/practice/sets/{setId}/tests/{testId}` | Chi tiết đề + lịch sử thi + bestScore |
| `practice/mode.html` | `…/mode` | Chọn chế độ (Practice / Exam) |
| `practice/player.html` | `/practice/attempts/{id}` | CBT player — 3-column group layout |
| `practice/result.html` | `…/result` (W/S) | Kết quả tổng quan Writing/Speaking |
| `practice/result-detail.html` | `…/result/detail` (W/S) | Chi tiết W/S: rubric, strengths, needs, upgrade, sample |
| `practice/rl-result.html` | `…/result` (R/L) | Kết quả tổng quan Reading/Listening |
| `practice/rl-result-detail.html` | `…/result/detail` (R/L) | Chi tiết R/L: đáp án từng câu + AI explanation |
| `practice/profile.html` | `/practice/profile` | Learning profile — lịch sử + thống kê |
| `practice/upload.html` | `/practice/manage/upload` | Form upload PDF |
| `practice/upload-preview.html` | `/practice/manage/upload/preview` | Preview draft + chỉnh sửa trước khi publish |

### 8.1 Annotations JSON Schema (W/S result detail)

Frontend nhận `annotations[]` từ `aiFeedbackJson`:

```json
{
  "annotations": [
    {
      "id": "ann_s1",
      "kind": "strength" | "need",
      "criterionId": "W_NATURAL_KOREAN_EXPRESSIONS",
      "category": "Diễn đạt tự nhiên",
      "evidence": "한국어를 배우는",
      "start": 0,
      "end": 8,
      "explanationVi": "...",
      "correction": "",
      "severity": "LOW" | "MEDIUM" | "HIGH",
      "displayType": "PHRASE" | "WORD" | "SENTENCE",
      "index": 1
    }
  ]
}
```

### 8.2 R/L Explanation JSON Schema (per question)

```json
{
  "questionId": "123",
  "questionType": "MCQ",
  "correctAnswer": "2",
  "correctAnswers": ["2"],
  "keywords": {
    "question": ["keyword1"],
    "passage": ["evidence_word"]
  },
  "evidence": [
    { "source": "passage", "text": "...", "paragraphIndex": 0, "sentenceIndex": 2 }
  ],
  "reasonVi": "Đáp án 2 đúng vì...",
  "wrongOptions": [
    { "option": "1", "reasonVi": "Sai vì..." }
  ],
  "matchingExplanations": [],
  "acceptedAnswers": ["2"],
  "translationVi": "Dịch nghĩa câu..."
}
```

---

## 9. Database Migrations

| File | Nội dung |
|---|---|
| `V1__init_*.sql` – `V15__*.sql` | Các migration cũ (auth, classes, lessons, attachments...) |
| `V16__practice_hub.sql` | Consolidated migration: Thiết lập toàn bộ các bảng dữ liệu mô-đun Practice (`sets`, `tests`, `sections`, `question_groups`, `questions`, `submissions`, `attempts`, `ai_analysis_usage`, `explanation_cache`, `drafts`, `edit_logs`, `workspace_sessions`, `region_annotations`, `draft_asset_usages`, `lecturer_assets`, `page_extractions`, `ai_request_audits`) và dữ liệu Seed mẫu.

**`question_explanation_cache` schema**:
```sql
CREATE TABLE question_explanation_cache (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id     BIGINT NOT NULL,
  test_id         BIGINT,
  skill_type      VARCHAR(20) NOT NULL,
  question_type   VARCHAR(40) NOT NULL,
  question_hash   VARCHAR(64) NOT NULL,
  correct_answer  VARCHAR(500),
  explanation_json LONGTEXT NOT NULL,
  ai_model        VARCHAR(100),
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_explanation (question_id, question_hash, correct_answer(100))
);
```

---

## 10. AI Feedback JSON Schema — Writing (Full, post-normalize)

```json
{
  "score": 7.5,
  "overall_score": 7.5,
  "raw_score": 41.6,
  "raw_score_max": 50.0,
  "task_type": "Q54",
  "band_label": "Tốt",
  "summary": "...",
  "summary_vi": "...",
  "rubric_scores": [
    { "name": "...", "score": 7.0, "feedback": "..." }
  ],
  "strengths": [
    {
      "criterionId": "W_NATURAL_KOREAN_EXPRESSIONS",
      "category": "Diễn đạt tự nhiên",
      "subcategory": "...",
      "evidence": "한국어를",
      "explanationVi": "...",
      "correction": "",
      "severity": "LOW",
      "displayType": "PHRASE",
      "uiLabel": "...",
      "errorType": "",
      "whyItIsGood": "...",
      "topikTip": "..."
    }
  ],
  "needs_improvement": [ /* same schema as strengths */ ],
  "student_text": "...",
  "annotations": [ /* start/end index array, built by normalizer */ ],
  "upgraded_answer": "...",
  "upgraded_answer_annotated": "",
  "sample_answer": "...",
  "sentence_rewrites": [
    { "original": "...", "upgraded": "...", "reason": "..." }
  ]
}
```

---

## 11. Configuration

```properties
# OpenAI / AI
openai.api-key=${OPENAI_API_KEY}
openai.evaluator-model=${OPENAI_EVALUATOR_MODEL:gpt-4o}
openai.transcription-model=${OPENAI_TRANSCRIPTION_MODEL:whisper-1}
openai.base-url=${OPENAI_BASE_URL:https://api.openai.com/v1}

# Upload
app.upload.dir=${UPLOAD_DIR:uploads}
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=2MB

# Storage
app.storage.provider=local
app.storage.r2.account-id=
app.storage.r2.access-key=
app.storage.r2.secret-key=
app.storage.r2.bucket-name=
app.storage.r2.cdn-url=
```

**Behavior khi thiếu API key**:
- Writing → `WritingMockEvaluatorService` tự động kích hoạt.
- Speaking → Luôn dùng mock (chưa implement AI thật).
- R/L per-question cache → `ReadingListeningExplanationClient.fallbackJson()`.

---

## 12. Known Issues & Backlog

### Bugs đã xác định

| # | Mô tả | File | Priority |
|---|---|---|---|
| B1 | `extractAiScore()` fallback cứng `11.11` khi parse lỗi | `PracticeService.java` | 🔴 Critical |
| B2 | Speaking hardcode score trong submit flow ghi đè mockSpeaking score | `PracticeService.java` | 🔴 Critical |
| B3 | `PracticeServiceTest.testSubmitMcq()` assert `assertNull(subId)` nhưng logic nên trả về ID | `PracticeServiceTest.java:150` | 🟡 Test Bug |
| B4 | `PracticeService.submit()` (legacy) không dùng trong flow mới nhưng vẫn tồn tại | `PracticeService.java` | 🟡 Dead code |
| B5 | `ReadingListeningExplanationService` dùng `synchronized(String.intern())` — có thể intern pool pollution | `ReadingListeningExplanationService.java:50` | 🟡 Medium |

### Feature Backlog

| # | Mô tả | Priority |
|---|---|---|
| F1 | Speaking AI thật (text-based → gõ text, hoặc audio-based → Whisper) | 🟠 High |
| F2 | `writing_format` field trên `PracticeSet` cho Won-go-ji mode | 🟡 Medium |
| F3 | Error Pattern Tracking — lưu lịch sử lỗi theo thời gian | 🟢 Low |
| F4 | Diff-based Upgraded Answer (Track Changes view) | 🟢 Low |
| F5 | Rubric Confidence Score cho giáo viên review | 🟢 Low |
| F6 | Paginate `getLearningProfile()` — hiện top 20 cứng | 🟡 Medium |
| F7 | `QuestionExplanationCache` — TTL/invalidation khi answerKey thay đổi | 🟡 Medium |

---

## 13. Thứ tự ưu tiên implement

1. ✅ 3-pass Writing AI evaluation
2. ✅ Mock fallback khi không có API key
3. ✅ Cache + retry + audit mode
4. ✅ Reading/Listening AI explanation (AnswerExplanationClient)
5. ✅ CBT Player Group-based 3-Column Layout
6. ✅ Fix XML tag leak → annotations start/end
7. ✅ R/L per-question explanation cache (QuestionExplanationCache + ReadingListeningExplanationClient)
8. ✅ R/L result routing (rl-result, rl-result-detail)
9. ✅ AudioStorageService interface + LocalAudioStorageService + CloudflareR2StorageService skeleton
10. ✅ `startAttempt()` + `submitAttempt()` flow (IN_PROGRESS → GRADED)
11. 🔲 Fix B1, B2, B5 (score bugs, synchronized)
12. 🔲 Speaking AI (text-based)
13. 🔲 Unit test 100% coverage (xem test plan)

---

## 14. Phân tích Hiện trạng Mã nguồn & Thành phần Dư thừa (Codebase Audit)

### 14.1 Nhóm 1: Các định nghĩa thực thể (Entities) kết nối DB
*   **`PracticeSet.java`**: Thực thể đại diện bảng `practice_sets` (bộ đề). Các hàm chính: `getId`, `getTitle`/`setTitle`, `getDescription`/`setDescription`, `getSkill`/`setSkill`, `getTopikLevel`/`setTopikLevel`, `getScope`/`setScope`, `getClassId`/`setClassId`, `getSourcePdfPath`, `getAudioPath`, `getMetadataJson`/`setMetadataJson`, `getStatus`/`setStatus`, `getCreatedBy`, `getCreatedAt`/`getUpdatedAt`, `isDeleted`, `getSkillsList` (bóc danh sách kỹ năng từ JSON), `getCreationMethod`/`setCreationMethod`, `getCoverImageUrl`/`setCoverImageUrl`.
*   **`PracticeTest.java`**: Thực thể đại diện bảng `practice_tests` (bài thi thử thuộc bộ đề). Các hàm chính: `getId`, `getSetId`/`setSetId`, `getTitle`/`setTitle`, `getDescription`/`setDescription`, `getDisplayOrder`/`setDisplayOrder`, `getEstimatedMinutes`/`setEstimatedMinutes`.
*   **`PracticeSection.java`**: Thực thể đại diện bảng `practice_sections` (kỹ năng con trong bài thi). Các hàm chính: `getId`, `getSetId`/`setSetId`, `getTestId`/`setTestId`, `getTitle`/`setTitle`, `getSkill`/`setSkill`, `getSectionType`/`setSectionType`, `getInstructions`/`setInstructions`, `getDurationMinutes`/`setDurationMinutes`, `getTotalPoints`/`setTotalPoints`, `getDisplayOrder`/`setDisplayOrder`.
*   **`PracticeQuestionGroup.java`**: Thực thể đại diện bảng `practice_question_groups` (nhóm câu hỏi). Các hàm chính: `getId`, `getSetId`, `getSectionId`/`setSectionId`, `getGroupLabel`, `getQuestionFrom`/`getQuestionTo`, `getInstruction`, `getAudioUrl`, `getExampleJson`, `getDisplayOrder`.
*   **`PracticeQuestion.java`**: Thực thể đại diện bảng `practice_questions` (câu hỏi). Các hàm chính: `getId`, `getSetId`, `getQuestionNo`, `getQuestionType`, `getPrompt`, `getOptionsJson`, `getAnswerKey`, `getExplanation`, `getPoints`, `getDisplayOrder`, `getGroupId`/`setGroupId`.
*   **`PracticeSubmission.java`**: Thực thể lưu lịch sử bài làm cũ (luồng cũ). Các hàm chính: `updateEvaluation`, `onPersist`/`onUpdate`.
*   **`PracticeAttempt.java`**: Thực thể lưu bài làm mới (từng kỹ năng riêng biệt). Các hàm chính: `markSubmitted` (đáp án trắc nghiệm), `markGraded` (điểm tự luận), `markAnalysisSucceeded`, `markAnalysisFailed`, `isObjectiveSkill`/`isSubjectiveSkill`.
*   **`PracticeAiAnalysisUsage.java`**: Lượt sử dụng AI hàng ngày để kiểm soát hạn ngạch. Các hàm chính: `markSucceeded`, `markFailed`, `markCancelled`.
*   **`QuestionExplanationCache.java`**: Bộ đệm lưu trữ giải thích đáp án bóc từ OpenAI.
*   **`PracticePdfImportSession.java`**: Lưu trạng thái phiên nhập tài liệu PDF.
*   **`PracticePdfRegionAnnotation.java`**: Lưu toạ độ vẽ vùng của giáo viên trên giao diện Workspace.
*   **`PracticePdfPageExtraction.java`**: Lưu văn bản thô bóc theo từng trang PDF.
*   **`LecturerAsset.java`**: Thực thể quản lý các file ảnh được crop từ PDF hoặc audio.
*   **`PracticeDraft.java`**: Lưu nháp đề thi thiết kế dạng JSON thô.
*   **`PracticeDraftAssetUsage.java`**: Ánh xạ vị trí sử dụng ảnh cắt trong các câu hỏi nháp.
*   **`PracticeEditLog.java`**: Nhật ký chỉnh sửa phiên bản của đề thi.

#### 🔴 Thực thể dư thừa:
*   **`PracticeCreationMethod.java`** (Enum): Định nghĩa enum cho phương thức tạo đề, nhưng `PracticeSet.creationMethod` lưu trực tiếp dạng `String` (VARCHAR) nên Enum này hoàn toàn dư thừa.

---

### 14.2 Nhóm 2: Lớp khách AI (AI Clients & Services) - `practice.ai`
*   **`AnswerExplanationClient.java`**: `explain(...)` - Gửi batch câu hỏi/đáp án lên OpenAI tạo giải thích bằng tiếng Việt.
*   **`PracticePdfQuestionGenerator.java`**: `generateQuestionsText(...)`/`generateQuestionsMultimodal(...)` - Gọi API GPT sinh câu hỏi đề thi từ text/ảnh PDF.
*   **`ReadingListeningExplanationClient.java`**: `explain(...)` - Giải thích chi tiết từng câu hỏi Nghe/Đọc đơn lẻ.
*   **`WritingEvaluationClient.java`**: `evaluate(...)` - Chấm điểm bài viết dựa trên Rubric TOPIK.
*   **`WritingEvaluationNormalizer.java`**: `normalize(...)` - Ép cấu trúc phản hồi thô của AI về JSON KSH chuẩn hóa.
*   **`WritingRuleEngine.java`**: `validate(...)` - Kiểm tra nhanh bài viết (black-list khẩu ngữ, độ dài) trước khi gọi AI.
*   **`OpenAiProperties.java`**: Cấu hình API OpenAI từ properties.
*   **`ReadingListeningMockExplanationService.java`**: `explain(...)` - Sinh giải thích giả lập khi OpenAI offline.
*   **`WritingEvaluationCacheService.java`**: `get(...)`/`put(...)` - Caching kết quả AI chấm Writing để giảm chi phí API.
*   **`WritingPromptRules.java`**: Hệ thống prompts chấm Rubric cho câu viết tự luận TOPIK II.
*   **`WritingRubricCriterion.java`**: Tiêu chí đánh giá bài luận.
*   **`WritingScoreMatrix.java`**: Ma trận chuyển đổi điểm thô TOPIK sang thang 100 điểm.

#### 🔴 Thành phần AI dư thừa:
*   **`MatchingGapFillMockEvaluatorService.java`** (Cả class): Chứa logic sinh điểm giả lập cho câu hỏi nối cột/điền từ, hoàn toàn không được gọi ở bất cứ đâu.
*   **`WritingMockEvaluatorService.java`**: Chức năng mock feedback viết khi API lỗi. Hiện tại logic này đã được code cứng trực tiếp qua hàm `mockWritingFeedback` nội bộ của `PracticeService`.

---

### 14.3 Nhóm 3: Điều hướng và Routing (Controllers)
*   **`PracticeController.java`**: Điều phối giao diện học sinh. Các hàm chính: `index` (danh mục đề), `setDetail` (chi tiết đề), `testDetail` (chi tiết bài thi), `discardAttempt` (hủy bài), `testMode` (chọn chế độ), `createAttempt` (tạo lượt làm bài), `attempt` (player màn hình làm bài), `submitAttempt` (nộp bài), `restPeriod` (nghỉ ngơi), `attemptResult`/`attemptResultDetail` (xem kết quả), `reEvaluateAttempt` (chấm lại tự luận), `progress` (biểu đồ học tập cá nhân).
*   **`PracticeDraftController.java`**: Biên tập nháp đề thi. Các hàm chính: `createEmptyDraft`, `exitDraft`, `editDraft`, `autosave`, `publishDraft`, `deleteDraft`, `uploadAudio`/`uploadImage`.
*   **`PracticeImportController.java`**: Điều phối trang wizard import. Các hàm chính: `showImportStartPage`, `showWorkspace`.
*   **`PracticeManageController.java`**: Dashboard của giảng viên. Các hàm chính: `dashboard`, `editSet` (chuyển set thành nháp), `revisions`/`restoreRevision` (nhật ký và rollback).
*   **`PracticePdfImportApiController.java`**: AJAX API của Workspace annotation. Các hàm chính: `uploadPdf`, `getSession`, `updatePageRange`, `getPdfFile`, `saveState`, `cancelChanges`, `deleteSession`, `getExtractedText`, `getAnnotations` / `addAnnotation` / `updateAnnotation` / `deleteAnnotation`, `getPayloadPreview`, `generateDraft`, `createManualDraft`, `attachToDraft`, `getAssetsList`/`getAssetContent`, `updateAsset`/`deleteAsset`, `promoteAsset`, `linkAsset`/`unlinkAsset`.

#### 🔴 Lượt điều hướng dư thừa:
*   Các API Redirect cũ (`legacyDetail`, `legacyDetailView`, `legacyMode`, `legacyPlayer`, `legacySubmit`, `legacyResult`, `legacyReEvaluate`, `profileRedirect`, `uploadFormRedirect`, `manualFormRedirect`).

---

### 14.4 Nhóm 3.5: DTO (Data Transfer Objects) - `practice.dto`
*   **`PracticeDtos.java`**: Gom nhóm các Java Record trung chuyển dữ liệu (như `PracticeSetRow`, `SectionView`, `ReadingListeningResultView`, `LearningProgressOverview`, v.v.). Các hàm chính: `getSkillLabel` (dịch tên kỹ năng), `getCategoryLabel` (dịch phân loại đề), `getOptionLabelMode` (xác định định dạng A/B/C/D hay 1/2/3/4).

---

### 14.5 Nhóm 4: Dịch vụ xử lý nghiệp vụ (Services)
*   **`PracticeService.java`**: Thao tác làm bài và chấm điểm chính. Các hàm: `listPublished`, `getPractice`, `reEvaluate`, `getResult`, `startAttempt`/`submitAttempt`, `saveInProgressAnswers`/`discardAttempt`, `getAttemptResult`/`getReadingListeningResult`, `getLearningProgressOverview`/`getPracticeAnalytics`.
*   **`ReadingListeningExplanationService.java`**: `getOrCreateExplanation`/`persistCache` - Giải thích đáp án Đọc/Nghe sử dụng lock luồng câu hỏi và cache DB.
*   **`LecturerAssetService.java`**: `getSessionAssets`/`getLibraryAssets`, `loadAssetResource`/`deleteAsset`, `promoteToActiveLibrary`, `linkAssetToDraft` - Quản lý file ảnh crop và audio.
*   **`PracticeDraftService.java`**: `getDraft`/`deleteDraft`, `getOrCreateEmptyDraft`, `saveDraftState`, `createDraftFromPublishedSet`, `cleanupEmptyDrafts` - CRUD dữ liệu nháp đề thi.
*   **`PracticeImportDraftService.java`**: `createManualDraftFromSession`/`attachToExistingDraft` - Bóc PDF thành nháp đề.
*   **`PracticePdfAiPayloadBuilder.java`**: `buildPayload(...)` - Tạo payload dữ liệu toạ độ và OCR gửi lên OpenAI.
*   **`PracticePdfCropService.java`**: `cropRegion(...)` - Cắt trang PDF thành ảnh PNG.
*   **`PracticePdfDraftAssembler.java`**: `assembleAndSaveDraft(...)` - Chuyển JSON AI sinh thành các câu hỏi nháp trong DB.
*   **`PracticePdfImportSessionService.java`**: Quản lý phiên import PDF.
*   **`PracticePdfPageExtractionService.java`**: `extractOrGetPageText(...)` - Bóc văn bản thô theo trang PDF.
*   **`PracticePdfPayloadPreviewService.java`**: `getPreview(...)` - Tạo JSON preview toạ độ vùng vẽ.
*   **`PracticePublisherService.java`**: `publish(...)` - Đọc Draft JSON lớn và lưu chính thức vào DB sets/questions.
*   **`AssetStorageService.java` / `LocalAssetStorageService.java`**: Lưu trữ vật lý tệp ảnh cắt hoặc file audio cục bộ.
*   **`PracticeDocumentAnalyzer.java`**: Wrapper gọi API OpenAI bóc tách đề.
*   **`PracticeImportSnapshotService.java`**: `saveSnapshot`/`restoreSnapshot` - Undo/Redo thao tác vẽ vùng.
*   **`PracticePdfPreviewService.java`**: `getPdfStream(...)` - Cung cấp stream file PDF cho viewer.
*   **`PracticePdfRegionService.java`**: CRUD annotations toạ độ vùng vẽ của giáo viên.
*   **`PracticePdfTextExtractionService.java`**: `extractPageRangeText(...)`/`extractRegionText(...)` - Bóc văn bản thô theo vùng cắt.
*   **`PracticeRevisionService.java`**: `restoreRevision(...)` - Khôi phục phiên bản lịch sử của đề.

#### 🔴 Class & Hàm nghiệp vụ dư thừa:
*   **`PracticePdfImportService.java`** (Cả class): Dịch vụ import PDF kiểu cũ, hoàn toàn không được sử dụng.
*   **`PracticeImportOrchestrator.java`** (Cả class): Điều phối import thô trực tiếp kiểu cũ, không được sử dụng.
*   **`PracticePdfImportValidationService.java`** (Cả class): Dịch vụ kiểm định PDF, không sử dụng.
*   **`PracticeDraftService.java`** -> `createEmptyDraft(Long)`: Không dùng (dùng `getOrCreateEmptyDraft`).
*   **`PracticeDraftService.java`** -> `saveDraftState(...)` bản 5 tham số: Không dùng.
*   **`PracticePdfAiOrchestrator.java`** -> `callAi(PayloadInfo)` bản 1 tham số: Không dùng.
*   **`PracticePdfImportSessionService.java`** -> `createSession(Long, MultipartFile)` bản 2 tham số: Không dùng.
*   **`PracticeService.java`** -> `submit(...)` (line 122): Hàm nộp bài cũ khi chưa có Attempt.
*   **`PracticeService.java`** -> `getLearningProfile(Long)` (line 255): Trả về hồ sơ cũ, không sử dụng.

---

### 14.6 Nhóm 4.5: Kho dữ liệu (Repositories) - `practice.repository`
*   **`LecturerAssetRepository.java`**: CRUD dữ liệu file ảnh/audio tài nguyên.
*   **`PracticeAiRequestAuditRepository.java`**: Nhật ký request/response OpenAI.
*   **`PracticeDraftAssetUsageRepository.java`**: Ánh xạ vị trí sử dụng ảnh cắt.
*   **`PracticeDraftRepository.java`**: Lưu trữ đề nháp JSON thô.
*   **`PracticeEditLogRepository.java`**: Quản lý lịch sử sửa đề.
*   **`PracticePdfImportGroupDraftRepository.java`**: CRUD nhóm tạm của phiên import.
*   **`PracticePdfImportSectionDraftRepository.java`**: CRUD kỹ năng tạm của phiên import.
*   **`PracticePdfImportSessionRepository.java`**: Trạng thái phiên import PDF.
*   **`PracticePdfPageExtractionRepository.java`**: Lưu text OCR theo trang PDF.
*   **`PracticePdfRegionAnnotationRepository.java`**: Lưu toạ độ vùng vẽ trên PDF.
*   **`PracticeQuestionGroupRepository.java`**: Quản lý nhóm câu hỏi đề thi chính thức.
*   **`PracticeQuestionRepository.java`**: Quản lý câu hỏi đề thi chính thức.
*   **`PracticeSectionRepository.java`**: Quản lý kỹ năng đề thi chính thức.
*   **`PracticeSetRepository.java`**: Quản lý đề thi chính thức.
*   **`PracticeSubmissionRepository.java`**: Quản lý bài làm học sinh (luồng cũ).
*   **`PracticeTestRepository.java`**: Quản lý các bài thi thử chi tiết thuộc bộ đề (mới bổ sung).
*   **`PracticeAttemptRepository.java`**: Lưu lượt làm bài kỹ năng riêng lẻ của học sinh (mới bổ sung).
*   **`QuestionExplanationCacheRepository.java`**: Bộ đệm cache giải thích câu hỏi R/L.

---

### 14.7 Nhóm 5: Giao diện và Style dư thừa
*   🔴 Templates rác: `practice/detail.html`, `practice/profile.html`, `practice/upload.html`, `practice/upload-preview.html`.
*   🔴 CSS tĩnh rác: `static/css/practice.css` (chỉ còn player.html sử dụng, có thể loại bỏ sau refactor).

---

### 14.8 Nhóm 6: Tệp kiểm thử dư thừa (đang kiểm thử cho code chết)
*   🔴 Tests rác:
    1.  `ai/MatchingGapFillMockEvaluatorServiceTest.java` (cho `MatchingGapFillMockEvaluatorService`)
    2.  `ai/WritingMockEvaluatorServiceTest.java` (cho `WritingMockEvaluatorService`)
    3.  `service/PracticePdfImportServiceTest.java` (cho `PracticePdfImportService`)
