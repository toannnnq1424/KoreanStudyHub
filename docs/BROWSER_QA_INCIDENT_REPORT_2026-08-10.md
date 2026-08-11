# Korean Study Hub — Git History Fix Audit 2026-08-10 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Không có dated browser record cho ngày này;
> file test trong diff chỉ là coverage evidence, không phải execution evidence.

## Quy ước

- Các commit của `toannqhe180972` được gắn **USER FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### QB-HIERARCHY-SNAPSHOT-001 — Câu hỏi mất phân cấp khi chapter/lesson bị đổi hoặc xóa

- Mức độ: **High** — Business / Data integrity.
- Nguồn sửa: **USER FIX** — toannqhe180972, `74d07649` (`feat: streamline lecturer learning workspaces`).
- Phạm vi: `QuestionBankItem`, QB item/import services, V116/V117 và QB templates/JS.
- Bằng chứng: `bindLesson()` snapshot chapter title/order và lesson title/order; read service
  fallback snapshot khi canonical lesson mất; import re-fetch theo lesson id + subject và abort
  nếu bài đã đổi/xóa; V117 repair demo binding theo canonical title thay vì ordinal đơn thuần.
- Tests: QB controller/frontend/layout/service/import tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **ADD COLUMN ONLY — compliant**; V116 thêm bốn snapshot columns vào
  `question_bank_items`, V117 chỉ UPDATE. V115 cùng commit thêm `subject_id` vào
  `flashcard_decks` hiện có, cũng compliant. Thay đổi destructive V114 là carry-over retirement,
  không thuộc incident này.
- Lifecycle: **Active**.

### ASSIGNMENT-ONE-SHOT-SUBMISSION-001 — Học viên có thể nộp lại bài đã gửi

- Mức độ: **High** — Business integrity / Concurrency.
- Nguồn sửa: **USER FIX** — toannqhe180972, `74d07649`.
- Phạm vi: `StudentAssignmentService`, `AssignmentSubmission`, student assignment templates.
- Bằng chứng: submit/re-submit được chốt thành đúng một lần; repository row lock
  `findByAssignmentIdAndUserIdForUpdate`; submission đã tồn tại thì reject; unique key là lớp
  bảo vệ thứ hai.
- Tests: `AssignmentServiceTest`, `AssignmentCatalogUiContractTest` trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### UX-NESTED-SURFACE-POLISH-001 — Workspace tạo cảm giác quá nhiều card lồng nhau

- Mức độ: **Medium** — UI / UX.
- Nguồn sửa: **USER FIX** — toannqhe180972, `b55723ff` (`polish learning workspace experience`).
- Phạm vi: `experience-polish.css` và 13 templates assignment/class/leader/test.
- Bằng chứng: design note trong CSS chốt “one clear surface per task”; nested content dùng
  spacing/divider, bỏ border/radius/shadow lặp, responsive grids; test review render explanation
  bằng `th:utext`.
- Tests: file trong diff `ExperiencePolishFrontendContractTest`;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active baseline**; các lỗi shell/sidebar/form mới ngày 2026-08-11 được ghi ở report
  2026-08-11 và không lặp lại tại đây.
