# Practice Module — Design Proposal (v2)

> **Folder**: `openspec/changes/practice-v2-refactor/`
> **Ngày tạo**: 2026-07-01
> **Phạm vi**: Feature Practice — AI Scoring 4 Skills

---

## 1. Bối cảnh & Vấn đề hiện tại

Feature Practice đã được implement tương đối đầy đủ cho 4 kỹ năng TOPIK. Tuy nhiên còn một số vấn đề cần giải quyết trước khi ship production:

### 1.1 Bugs quan trọng

| # | Vấn đề | Impact |
|---|---|---|
| B1 | `extractAiScore()` fallback cứng về `11.11/100` khi parse lỗi | Điểm Writing sai hoàn toàn khi AI trả JSON không hợp lệ |
| B2 | Speaking submit: `mockSpeakingFeedback()` đúng nhưng `score` gán chưa nhất quán | Điểm Speaking có thể bị ghi đè bởi giá trị sai |
| B3 | `PracticeServiceTest.testSubmitMcq()` assert `assertNull(subId)` — logic sai | Test xanh nhưng không phản ánh đúng behavior |
| B4 | `ReadingListeningExplanationService` dùng `String.valueOf(id).intern()` | Intern pool có thể bị saturation nếu nhiều questionId |

### 1.2 Thiếu coverage test

- `WritingEvaluationCacheService` — chưa có unit test nào.
- `WritingEvaluationNormalizer` — test quá ít, thiếu annotation build logic.
- `PracticeService.getReadingListeningResult()` — chưa có unit test.
- `ReadingListeningExplanationService` — chưa có unit test.
- `PracticeController` — chưa có slice test (WebMvcTest).
- Repository tests — chưa có DataJpaTest.

### 1.3 Thiếu documentation

- OpenAPI spec chưa phản ánh routing mới (v2 routes với `/sets/{setId}/tests/{testId}`).
- Chưa có design proposal riêng cho practice module trong `changes/`.
- Annotation JSON schema chưa được document chính thức.

---

## 2. Đề xuất thay đổi

### 2.1 Fix Bugs (Priority: High)

#### B1 — Fix extractAiScore()

Thay hardcode fallback bằng default score 1.0 (band "Không phản hồi") và log warning rõ:

```java
// Before
return WritingScoreMatrix.toHundredPointScale(1.0); // silent fallback

// After
log.warn("[PracticeService] Failed to extract AI score from feedback, defaulting to band 1.0. Raw: {}", aiFeedback);
return WritingScoreMatrix.toHundredPointScale(1.0);
```

Thêm validation: nếu score nằm ngoài [0, 9] thì clamp trước khi convert.

#### B2 — Fix Speaking score consistency

Đảm bảo sau khi `mockSpeakingFeedback()` trả về JSON, `extractAiScore()` parse đúng `score` field.

#### B3 — Fix PracticeServiceTest assertion

```java
// submit() với mock trả về Submission không có ID → assertNull là sai
// Fix: dùng when(save).thenAnswer để trả entity với ID set
```

#### B4 — Fix synchronized mechanism

Thay `String.valueOf(id).intern()` bằng `ConcurrentHashMap<Long, Object>` làm lock map:

```java
private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

synchronized(locks.computeIfAbsent(questionId, k -> new Object())) {
    // double-check
}
```

### 2.2 Test Coverage (Priority: High)

Xem file `tasks.md` — mục tiêu 100% class coverage.

### 2.3 Không thay đổi routing hiện tại

Routes v2 đã được implement và hoạt động tốt. Giữ nguyên.

### 2.4 Không thay đổi AI prompt logic

WritingPromptRules và hệ thống 3-pass đang hoạt động đúng. Không sửa.

---

## 3. Architecture Decisions

### 3.1 Tại sao giữ `submit()` legacy?

`submit()` (cũ) vẫn được giữ để không break các code path chưa migrate. Khi `submitAttempt()` (mới) đã stable, sẽ deprecate `submit()`.

### 3.2 Tại sao dùng `String.intern()` cho lock?

Ban đầu để đơn giản, nhưng intern pool không được GC → memory leak tiềm ẩn. Sẽ migrate sang ConcurrentHashMap<Long, Object>.

### 3.3 Tại sao không dùng Spring Cache cho QuestionExplanationCache?

Spring Cache (Caffeine/Redis) không phải lựa chọn vì cache cần persist qua restart. DB cache (`question_explanation_cache`) là correct approach.

### 3.4 Tại sao AudioStorageService là interface?

Để người tích hợp Cloudflare R2/S3 chỉ cần implement 1 interface mà không cần hiểu codebase. `LocalAudioStorageService` là default fallback với `@ConditionalOnMissingBean`.

---

## 4. Non-Goals (Out of Scope)

- Speaking AI thật (audio pipeline) — cần quyết định architecture riêng.
- Won-go-ji mode / Error tracking / Diff view — backlog F2-F4.
- Admin giao diện quản lý practice sets — chưa yêu cầu.
- Webhook/notification khi AI chấm xong — chưa yêu cầu.
