# Practice Module v2 — Task List

> **Folder**: `openspec/changes/practice-v2-refactor/`
> **Ghi chú**: Tasks phải làm → đánh `[ ]`. Đã done → `[x]`.

---

## Phase 0: Documentation (Done trước khi push)

- [x] Cập nhật `openspec/specs/practice/practice-feature.md` (v2.0 — full route table, AI pipeline, DTOs, Storage)
- [x] Tạo `openspec/changes/practice-v2-refactor/design.md` (vấn đề hiện tại + đề xuất sửa đổi)
- [x] Tạo `openspec/changes/practice-v2-refactor/proposal.md` (test plan 100% coverage)
- [x] Tạo `openspec/changes/practice-v2-refactor/tasks.md` (file này)

---

## Phase 1: Bug Fixes

### 1.1 B1 — extractAiScore() fallback logging
- [ ] Thêm `log.warn()` khi fallback về score 1.0 trong `PracticeService.extractAiScore()`
- [ ] Validate score nằm trong [0, 9] trước khi convert, clamp nếu ngoài range

### 1.2 B2 — Speaking score consistency
- [ ] Kiểm tra lại `submitAttempt()` — ensure `extractAiScore(aiFeedback)` được gọi đúng sau `mockSpeakingFeedback()`
- [ ] Verify score được gán vào submission đúng (không bị ghi đè bởi default BigDecimal.ZERO)

### 1.3 B3 — Fix PracticeServiceTest assertion
- [ ] Sửa `testSubmitMcq()`: mock `submissionRepository.save()` trả về entity với ID → assert ID khác null

### 1.4 B4 — Fix synchronized mechanism trong ReadingListeningExplanationService
- [ ] Thay `String.valueOf(id).intern()` bằng `ConcurrentHashMap<Long, Object>` lock map
- [ ] Đảm bảo lock được remove sau khi xử lý xong (tránh memory leak)

---

## Phase 2: Unit Tests — AI Layer

### 2.1 WritingScoreMatrixTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/ai/WritingScoreMatrixTest.java`
- [ ] Implement 20 test cases (xem proposal.md §2.2)
- [ ] Verify `clampAndRound`, `backendScore`, `rawScoreMax`, `toHundredPointScale`, `bandLabel`, `bands`

### 2.2 WritingRubricCriterionTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/ai/WritingRubricCriterionTest.java`
- [ ] Implement 11 test cases (xem proposal.md §2.3)

### 2.3 WritingEvaluationCacheServiceTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/ai/WritingEvaluationCacheServiceTest.java`
- [ ] Implement 10 test cases (xem proposal.md §2.4)
- [ ] TTL test: dùng reflection thay `createdAt` hoặc subclass override `TTL`

### 2.4 WritingRuleEngineTest (bổ sung)
- [ ] Bổ sung 19 test cases vào `WritingRuleEngineTest.java` (xem proposal.md §2.1)
- [ ] Đảm bảo tất cả 18 blacklist entries được covered

### 2.5 WritingEvaluationNormalizerTest (bổ sung)
- [ ] Bổ sung 11 test cases vào `WritingEvaluationNormalizerTest.java` (xem proposal.md §2.5)
- [ ] Test annotation building từ evidence start/end

### 2.6 WritingMockEvaluatorServiceTest (bổ sung)
- [ ] Bổ sung 8 test cases vào `WritingMockEvaluatorServiceTest.java` (xem proposal.md §2.6)
- [ ] Test spam detection, Q53/Q54 rawScoreMax

### 2.7 WritingEvaluationClientTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/ai/WritingEvaluationClientTest.java`
- [ ] Strategy: mock RestClient bằng WireMock hoặc MockRestServiceServer
- [ ] Implement 11 test cases (xem proposal.md §2.7)
- [ ] Verify 3-pass logic, retry, cache hit/miss, SPAM detection

### 2.8 AnswerExplanationClientTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/ai/AnswerExplanationClientTest.java`
- [ ] Implement 8 test cases (xem proposal.md §2.8)
- [ ] Mock HTTP với WireMock hoặc MockRestServiceServer

### 2.9 ReadingListeningExplanationClientTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/ai/ReadingListeningExplanationClientTest.java`
- [ ] Implement 9 test cases (xem proposal.md §2.9)

---

## Phase 3: Unit Tests — Service Layer

### 3.1 ReadingListeningExplanationServiceTest (mới)
- [ ] Tạo `src/test/java/com/ksh/features/practice/service/ReadingListeningExplanationServiceTest.java`
- [ ] Implement 7 test cases (xem proposal.md §2.10)
- [ ] Test cache hit, miss, double-check locking, save failure

### 3.2 PracticeServiceTest (bổ sung)
- [ ] Sửa `testSubmitMcq()` assertion (xem Phase 1.3)
- [ ] Thêm `ReadingListeningExplanationService` mock vào `setUp()` (service bây giờ có thêm dependency)
- [ ] Thêm `AudioStorageService` mock vào `setUp()`
- [ ] Implement 29 test cases mới (xem proposal.md §2.11)

### 3.3 PracticePdfImportServiceTest (bổ sung)
- [ ] Implement 6 test cases mới (xem proposal.md §2.12)

---

## Phase 4: Unit Tests — Controller & Storage

### 4.1 PracticeControllerTest (mới — WebMvcTest)
- [ ] Tạo `src/test/java/com/ksh/features/practice/controller/PracticeControllerTest.java`
- [ ] Setup `@WebMvcTest` + `@MockBean` cho services
- [ ] Implement 21 test cases (xem proposal.md §2.14)
- [ ] Verify redirect routes (legacy), template names, model attributes

### 4.2 LocalAudioStorageServiceTest (mới)
- [ ] Tạo `src/test/java/com/ksh/common/storage/LocalAudioStorageServiceTest.java`
- [ ] Implement 4 test cases (xem proposal.md §2.13)

---

## Phase 5: Repository Tests

### 5.1 PracticeRepositoryTest (mới — DataJpaTest)
- [ ] Tạo `src/test/java/com/ksh/features/practice/repository/PracticeRepositoryTest.java`
- [ ] Setup `@DataJpaTest` (H2 in-memory)
- [ ] Implement 9 test cases (xem proposal.md §3)
- [ ] Test tất cả custom query methods

---

## Phase 6: Integration Tests (bổ sung)

### 6.1 PracticeIntegrationTest (bổ sung)
- [ ] Thêm `testSubmitAttemptReading_correctCount`
- [ ] Thêm `testSubmitAttemptReading_groupsPresent`
- [ ] Thêm `testGetResultDetail_readingSkill_hasGroupsJson`
- [ ] Thêm `testGetResultDetail_writingSkill_hasQuestionsJson`
- [ ] Thêm `testLegacyRoutes_redirect`
- [ ] Thêm `testLearningProfile_showsRecentAttempts`
- [ ] Verify: `application-test.properties` có `openai.api-key=` (blank) để mock evaluator tự kích hoạt

---

## Phase 7: Coverage Check & Cleanup

- [ ] Chạy `./mvnw verify -Djacoco.skip=false`
- [ ] Kiểm tra report: mỗi class trong `practice.*` và `common.storage.*` ≥ 85% line coverage
- [ ] Fix các branch coverage gap (thường là catch blocks, null guards)
- [ ] Remove dead code: `PracticeService.submit()` legacy method nếu không còn dùng
- [ ] Đảm bảo tất cả test chạy được trong CI (không cần API key thật)

---

## Phase 8: Git Push Checklist

- [ ] `git add openspec/`
- [ ] `git add src/test/`
- [ ] `git commit -m "docs: add practice v2 openspec + comprehensive test plan"`
- [ ] `git push origin main` (hoặc PR)
- [ ] Verify CI pipeline xanh

---

## Summary Statistics (after all done)

| Item | Count |
|---|---|
| Test classes mới | 8 |
| Test classes bổ sung | 6 |
| Test methods tổng cộng (estimate) | ~150 |
| Doc files tạo mới | 3 (design, proposal, tasks) |
| Doc files cập nhật | 1 (practice-feature.md) |
