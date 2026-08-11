# Korean Study Hub — Git History Fix Audit 2026-08-03 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Các test được nêu là file-in-diff;
> execution evidence và dated browser record không được lưu cho ngày này.

## Quy ước

- Các commit của `toannq1424` được gắn **USER FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.
- Discovery/direct-audio incidents được giữ để bảo toàn lịch sử nhưng capability đã retirement.

## Incidents

### DIRECT-AUDIO-CACHE-AUTHORITY-001 — Cache audio tái sử dụng xuyên consent/reviewer context

- Mức độ: **Critical** — Privacy / Security.
- Nguồn sửa: **USER FIX** — toannq1424, `96b4553a` (`fix(practice): bind direct audio cache to authorization`).
- Phạm vi: direct-audio evaluation service/cache identity.
- Bằng chứng: cache key được gắn consent evidence, disclosure version và reviewer evidence,
  ngăn dùng lại result qua authorization context khác.
- Tests: file trong diff `DirectAudioSpeakingEvaluationServiceTest`;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Retired** — capability được gỡ bởi `3c6bbf0a` ngày 2026-08-09.

### DIRECT-AUDIO-CONSENT-WITHDRAWAL-001 — Rút consent nhưng reviewer/media vẫn còn truy cập được

- Mức độ: **Critical** — Privacy.
- Nguồn sửa: **USER FIX** — toannq1424, cluster `59adc187`, `4fa542c5`, `ba32360c`.
- Phạm vi: authorization coordinator, dark-observation store và cleanup queue/services.
- Bằng chứng: latest consent được kiểm tra trước reviewer inspection; withdrawal logical-delete
  observations và enqueue durable media cleanup kèm authorization evidence.
- Tests: authorization, dark-observation, withdrawal cleanup/media tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: `ba32360c` **ADD COLUMN ONLY — compliant** trên cleanup table; lifecycle
  tables nền được tạo trước đó bởi `715a18f7`/`f3b62fee` là **historical dependency outside
  current constraint**.
- Lifecycle: **Retired** — capability được gỡ bởi `3c6bbf0a` ngày 2026-08-09.

### DIRECT-AUDIO-REVIEWER-AUDIT-001 — Reviewer playback thiếu audit fail-closed và retention

- Mức độ: **High** — Security / Privacy.
- Nguồn sửa: **USER FIX** — toannq1424, cluster `6de545fe`, `0317b7a1`, `c4dade20`, `24c87eb7`.
- Phạm vi: reviewer playback controller/service, audit store/worker và retention policy.
- Bằng chứng: bind question/media identity; playback trả `Cache-Control: no-store`; audit write
  failure chặn read; retention được pin P90D.
- Tests: playback/audit/retention tests và schema integration tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: V93/V96 **ADD COLUMN ONLY — compliant**; V95 tạo
  `practice_speaking_audio_reviewer_access_events` nên **OUTSIDE CURRENT CONSTRAINT — historical only**.
- Lifecycle: **Retired** — capability được gỡ bởi `3c6bbf0a` ngày 2026-08-09.

### PRACTICE-AI-BOUNDED-COMPLETENESS-001 — AI partial result có thể được trình bày như hoàn tất

- Mức độ: **High** — Logic / Reliability.
- Nguồn sửa: **USER FIX** — toannq1424, cluster `4a9853e0`, `8c308586`, `1846bd0f`.
- Phạm vi: structured adapter, shared completeness, writing/reading/listening/audio normalizers
  và Korean alignment contract.
- Bằng chứng: replacement calls bị giới hạn bởi retry/byte budgets; shared completeness ngăn
  partial result thành ready; Korean audio alignment được schema-validate chặt.
- Tests: robustness/completeness/alignment tests và evidence contract files trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**, trừ direct-audio branch đã retirement.

### QUESTION-BANK-SUBJECT-SCOPE-COMPACTION-001 — Question Bank bị chia sai theo category/lớp

- Mức độ: **High** — Business / Data model.
- Nguồn sửa: **USER FIX** — toannq1424, `273c297f` (`feat(nonpractice): scope question bank by subject`).
- Phạm vi: Question Bank controllers/services/entities, import/review và test picker.
- Bằng chứng: hợp nhất ngân hàng theo subject và bổ sung leader bulk-review trong cùng scope.
- Tests: QB frontend/import/access/review/category integrations và test-navigation tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; migration drop FK/index/cột
  và drop `question_bank_categories`. Không dùng làm tiền lệ cho schema mới.
- Lifecycle: **Superseded** một phần bởi canonical subject flow ngày 2026-08-04 và hierarchy
  snapshots ngày 2026-08-10.

### LIBRARY-AUTHORING-SOURCE-001 — Nhiều nguồn tạo chapter/lesson gây lệch canonical tree

- Mức độ: **High** — Business / Canonical data.
- Nguồn sửa: **USER FIX** — toannq1424, `d58dafa7` (`feat(nonpractice): make library the lesson authoring source`).
- Phạm vi: lesson controllers, library services/templates và legacy class authoring routes.
- Bằng chứng: legacy class lesson route trở thành authorized redirect; Library là nguồn tạo
  lesson canonical với subject/chapter/order.
- Tests: lesson/library controller/service/migration/UI và role-boundary tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **ADD COLUMN ONLY — compliant** trên `lesson_templates` hiện có.
- Lifecycle: **Active**.

### CLASS-IDENTITY-COMPACTION-001 — Class identity ngẫu nhiên và invite model lệch canonical subject

- Mức độ: **High** — Business / Authorization.
- Nguồn sửa: **USER FIX** — toannq1424, `e1b3e526` (`refactor(nonpractice): remove random class identifiers`)
  và `171b2b37` (`feat(nonpractice): compact subject and class flows`).
- Phạm vi: classes/invites, co-lecturer, student routes, messaging và dependent learning flows.
- Bằng chứng: bỏ persisted random class-code/invite semantics, chuyển sang canonical subject/name
  và bổ sung co-lecturer/audit flows.
- Tests: class/repository/role/messaging/student/test integrations trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; drop code/invite columns/table,
  rename table/cột và tạo `class_co_lecturers`. Không dùng làm tiền lệ.
- Lifecycle: **Active**, với schema đã được compact ở thời điểm lịch sử.

### ADMIN-AI-STORAGE-SETTINGS-UX-001 — Settings AI/storage trộn lẫn scope và khó đọc

- Mức độ: **Medium** — UI / Information architecture.
- Nguồn sửa: **USER FIX** — toannq1424, `fbce725e` (`feat(admin): redesign AI and storage settings`).
- Phạm vi: admin settings CSS/JS/templates/controllers.
- Bằng chứng: tách rõ global/Practice scope, tổ chức card/form dễ đọc và responsive grid.
- Tests: settings IA/presentation/storage controller và Practice UI contract tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
