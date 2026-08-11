# Korean Study Hub — Git History Fix Audit 2026-08-01 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Test file trong diff chỉ là coverage evidence;
> execution/browser record không được suy diễn.

## Quy ước

- Commit của `toannq1424` được gắn **USER FIX**; commit của tác giả khác là
  **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### PRACTICE-SUBJECTIVE-SURFACE-RADIUS-001 — Card phân tích tạo cảm giác nhiều khối lồng nhau

- Mức độ: **Low/Medium** — UI / Visual consistency.
- Nguồn sửa: **USER FIX** — toannq1424, `9dbc6474` (`fix(practice): round subjective analysis surfaces`).
- Phạm vi: `practice-result-prep.css`.
- Bằng chứng: comment trong diff ghi square first child che góc bo của parent và double shadow
  tạo stacked-card effect; sửa về một surface/radius thống nhất.
- Tests: file trong diff `PracticeFunctionalUiContractTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-PUBLISH-AUTHORITY-001 — Publish nhận option/target không có authority bất biến

- Mức độ: **High** — Content integrity / Security.
- Nguồn sửa: **USER FIX** — toannq1424, `004e4e7b` (`feat(practice): harden authoring publication contracts`).
- Phạm vi: draft/publisher/validators, upload security và authoring contract JavaScript.
- Bằng chứng: fail-closed immutable option/target authority, instruction/stimulus language
  authority và upload boundary trước publish.
- Tests: draft/upload/preview/publisher/validator tests và
  `PracticePhase11AuthoringUiContractTest` trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **ADD COLUMN ONLY — compliant**; thêm authority columns vào group/version
  tables hiện có.
- Lifecycle: **Active**.

### SPEAKING-EVALUATION-AUTHORITY-001 — Speaking evaluation có thể trả kết quả khi provider chưa sẵn sàng

- Mức độ: **High** — Assessment correctness.
- Nguồn sửa: **USER FIX** — toannq1424, `999f7ff8` (`feat(practice): fail closed speaking evaluation authority`).
- Phạm vi: speaking readiness/orchestrator/normalizer/result mapping.
- Bằng chứng: evaluator authority và reuse policy fail-closed; score/evidence được reconcile,
  payload trình bày không lộ dữ liệu ngoài contract.
- Tests: nhiều speaking readiness/orchestrator/normalizer/rendering tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**, ngoại trừ direct-audio legacy bị retirement sau này.

### WRITING-DIAGNOSTIC-AUTHORITY-001 — Chấm Writing thiếu blank authority và evidence ledger

- Mức độ: **High** — Assessment correctness.
- Nguồn sửa: **USER FIX** — toannq1424, `7b181614` (`feat(practice): enforce writing diagnostic authority`).
- Phạm vi: writing blank contract/verifier, evaluation normalizer và result registry.
- Bằng chứng: thêm structured blank authority, score anchors và evidence ledger/diagnostic
  normalization trước khi trình bày kết quả.
- Tests: các `Writing*Test` và `WritingDiagnosticDescriptorRegistryTest` trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### LOCAL-UPLOAD-WORKTREE-DURABILITY-001 — Đổi branch/worktree làm upload cục bộ biến mất

- Mức độ: **High** — Data durability.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `27211927` (`fix(storage): keep local uploads outside worktrees`).
- Phạm vi: storage config/services.
- Bằng chứng: default local upload root chuyển sang `${user.home}/.ksh/uploads`, tách khỏi
  checkout/worktree lifecycle.
- Tests: file trong diff `PracticeSpeakingMediaUiResourceTest`;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### CLASS-STUDENT-AUTH-001 — Route lớp học viên chỉ kiểm tra đăng nhập

- Mức độ: **High** — Authorization / Role boundary.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `daf72f6c`
  (`fix(auth): enforce student-only class routes`).
- Phạm vi: `StudentClassesController`, `StudentLessonsController`,
  `StudentClassMessagesController`, `LearningProgressController` và `InviteLinkController`.
- Bằng chứng trong diff: thay `isAuthenticated()` bằng `Roles.PREAUTH_STUDENT`; thêm
  `StudentClassRoleBoundaryContractTest` để khóa ranh giới của các controller học viên.
- Tests: file contract có trong diff; **execution evidence for 2026-08-01 not preserved**.
- Browser QA: **Missing dated browser record**; các lần verify thực hiện ngày 2026-08-11
  chỉ thuộc report 11/08, không được ghi ngược thành browser evidence ngày 01/08.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
