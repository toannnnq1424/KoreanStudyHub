# Korean Study Hub — Git History Fix Audit 2026-07-31 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Các file test được liệt kê là evidence-in-diff;
> không có execution record hoặc dated browser record cho ngày này.

## Quy ước

- Commit của `toannnnq1424` được gắn **USER FIX**; commit của tác giả khác được gắn
  **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### CLASS-TEST-GET-AUTOSTART-001 — Xem trang test vô tình tiêu thụ lượt làm

- Mức độ: **Critical** — Business integrity.
- Nguồn sửa: **USER FIX** — toannnnq1424, `7acb59d6` (`feat(ai): harden learning generation and class test flows`).
- Phạm vi: `StudentTestController`, `TestAttemptService`, test detail/take flow.
- Bằng chứng: GET trở thành landing read-only; chỉ POST rõ ràng mới bắt đầu; bài không phải
  Practice giữ ràng buộc một lượt làm.
- Tests: `StudentTestFlowIntegrationTest`, `StudentTestFlowUiContractTest`,
  `TestAttemptServiceTest`, `TestCatalogServiceTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### CLASS-TEST-DEADLINE-DRIFT-001 — Đồng hồ test lệch khi tab chạy nền

- Mức độ: **High** — Correctness.
- Nguồn sửa: **USER FIX** — toannnnq1424, `7acb59d6`.
- Phạm vi: `ExamDeadline`, `test-take.js`.
- Bằng chứng: dùng absolute epoch deadline, tính lại mỗi tick và cap elapsed ở deadline có
  authority thay vì tích lũy interval.
- Tests: file trong diff `ExamDeadlineTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### AUTH-SAVED-REQUEST-ROLE-001 — Đăng nhập xong resume URL không phù hợp role

- Mức độ: **High** — Authorization / Navigation.
- Nguồn sửa: **USER FIX** — toannnnq1424, `7acb59d6`.
- Phạm vi: `RoleAwareAuthenticationSuccessHandler`, `RoleNavigation`.
- Bằng chứng: chỉ resume saved request cùng origin và tương thích role; request stale bị xóa
  và người dùng được đưa về role home.
- Tests: `RoleAwareAuthenticationSuccessHandlerTest`, `RoleNavigationTest`,
  `RoleNavigationUiContractTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### AI-GENERATION-INVALID-JSON-001 — AI trả JSON lỗi làm hỏng luồng tạo câu hỏi/thẻ

- Mức độ: **High** — Reliability / Data integrity.
- Nguồn sửa: **USER FIX** — toannnnq1424, `7acb59d6`.
- Phạm vi: AI question/flashcard services, parser và Korean material selector.
- Bằng chứng: một retry có giới hạn, bracket-aware JSON extraction, giới hạn số phần tử trả
  về và chọn material sát TOPIK hơn.
- Tests: các `AiFlashcard*Test`, `AiQuestion*Test`, `KoreanFlashcardMaterialSelectorTest`
  trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### CLASS-APPROVAL-MIGRATION-SEQUENCE-001 — Migration approval chạy trước dependency Discovery

- Mức độ: **Critical** — Migration / Deployment.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `e259f899` (`fix(classes): sequence approval migration after discovery`).
- Phạm vi: Flyway migration identity/order.
- Bằng chứng: migration class approval được chuyển sau Discovery để loại collision/order fault.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **Historical sequencing only**; chuỗi cũ có thể chứa table creation và không
  phải tiền lệ. Chính sách hiện tại vẫn chỉ cho phép thêm cột.
- Lifecycle: **Superseded** bởi các lần reconcile migration sau.

### AI-REASONING-READ-TIMEOUT-001 — AI reasoning bị timeout trước khi trả lời

- Mức độ: **Medium** — Availability.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `36e8fd0a` (`fix(ai): allow longer reasoning responses`).
- Phạm vi: AI client timeout.
- Bằng chứng: tăng read timeout lên 60 giây cho reasoning response.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### AUTH-CONCURRENT-LOGIN-POLICY-001 — Cấu hình security vô tình giới hạn đăng nhập đồng thời

- Mức độ: **Medium** — Security policy / Business.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `12b803bc` (`fix(security): keep concurrent logins unrestricted`).
- Phạm vi: session concurrency configuration.
- Bằng chứng: `maximumSessions(-1)` khôi phục chính sách cho phép concurrent login.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### AUTH-PASSWORD-SESSION-REVOCATION-001 — Đổi mật khẩu không thu hồi phiên cũ

- Mức độ: **High** — Security.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `dae6e8e2` (`security: revoke stale sessions after password changes`).
- Phạm vi: session registry và profile password-change service.
- Bằng chứng: thu hồi mọi phiên khác sau đổi mật khẩu nhưng giữ phiên hiện tại.
- Tests: file trong diff `SessionRevocationServiceTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### LESSON-CONTENT-TYPE-PRESERVATION-001 — Tạo bài học làm mất loại nội dung đã chọn

- Mức độ: **High** — Data loss / Business.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `743cf547` (`fix(lessons): preserve selected content type on create`).
- Phạm vi: lesson create flow.
- Bằng chứng: giữ nguyên selected external-video content type khi tạo nội dung.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### FLASHCARD-UNICODE-LONG-CARD-UI-001 — Nội dung Korean dài phá layout flashcard

- Mức độ: **Medium** — UI / Typography.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `ddb48449` (`fix(flashcards): stabilize fonts and long-card layouts`).
- Phạm vi: flashcard typography/layout CSS.
- Bằng chứng: Korean-capable font stack, clamped text sizing và `overflow-wrap` cho nội dung dài.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### ADMIN-RESPONSIVE-DASHBOARD-001 — Dashboard/news admin không responsive

- Mức độ: **Medium** — UI / Responsive.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `1bde7cf0` (`fix(admin): refine responsive news and dashboard UI`).
- Phạm vi: admin dashboard/news CSS/templates.
- Bằng chứng: điều chỉnh responsive layout cho các trang admin/news.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### DISCOVERY-SELECTIVE-AI-EDITORIAL-001 — Editorial AI xử lý ngoài tập bản ghi được chọn

- Mức độ: **High** — Data integrity.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `c0d11545` (`fix(discovery): harden selective AI editorial runs`).
- Phạm vi: Discovery AI editorial/admin news.
- Bằng chứng: chỉ xử lý explicit selected IDs và dùng JSON-object response contract.
- Tests: `NewsAiEditorialServiceTest`, `AdminNewsControllerTest`;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Retired** — Discovery được gỡ bởi `3c6bbf0a` ngày 2026-08-09.

### DICTIONARY-SETTINGS-IA-001 — Settings từ điển không giải thích rõ prompt runtime

- Mức độ: **Low/Medium** — UI / Information architecture.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `32c8e7fe` (`fix: polish dictionary settings and clarify prompt runtime`).
- Phạm vi: dictionary/admin settings UI copy.
- Bằng chứng: tinh chỉnh settings IA và mô tả prompt runtime.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
