# Korean Study Hub — Git History Fix Audit 2026-07-28 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. File test xuất hiện trong diff không phải
> bằng chứng test đã chạy; browser QA chỉ được xác nhận khi có record riêng.

## Quy ước

- Các commit của `toannq1424` được gắn **USER FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### PRACTICE-LEARNER-RESPONSIVE-A11Y-001 — Learner Practice thiếu trạng thái accessible và responsive

- Mức độ: **Medium** — UI / Accessibility.
- Nguồn sửa: **USER FIX** — toannq1424, `81d78e8f` (`feat(practice): improve learner responsive accessibility`).
- Phạm vi: learner Practice CSS/templates.
- Bằng chứng: thêm `aria-live`/status, accessible icon toggles, focus states và wrapping.
- Tests: file trong diff `PracticeFunctionalUiContractTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-AUTHORING-A11Y-001 — Authoring Practice thiếu focus và semantics điều hướng

- Mức độ: **Medium** — UI / Accessibility.
- Nguồn sửa: **USER FIX** — toannq1424, `85c61ab4` (`feat(practice): harden authoring accessibility and icons`).
- Phạm vi: authoring templates/CSS/icons.
- Bằng chứng: bổ sung `aria-current`, semantic icon state, focus visibility và responsive overflow.
- Tests: file trong diff `PracticePhase11AuthoringUiContractTest`;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-CATALOG-PROGRESS-QUERY-001 — Catalog/progress dùng truy vấn không giới hạn

- Mức độ: **High** — Performance / Data access.
- Nguồn sửa: **USER FIX** — toannq1424, `74a30260` (`feat(practice): bound catalog and progress queries`).
- Phạm vi: catalog/progress repositories, projections/services và migration V56.
- Bằng chứng: thay các truy vấn unbounded/N+1 bằng page/bounded projections; thêm timestamp
  phục vụ activity ordering.
- Tests: file trong diff `PracticeIntegrationTest`, `PracticePhase13GPerformanceContractTest`,
  `PracticeCatalogServiceTest`, `PracticeProgressServiceTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **ADD COLUMN ONLY — compliant**; V56 thêm `activity_at` vào bảng hiện có.
- Lifecycle: **Active**.

### QUESTION-BANK-CANONICAL-NAV-001 — Question Bank dùng sai active navigation theo role

- Mức độ: **Medium** — UI / Navigation.
- Nguồn sửa: **USER FIX** — toannq1424, `569f1b5c` (`fix(ui): restore canonical role and question-bank navigation`).
- Phạm vi: shared app shell/header và leader Question Bank badge.
- Bằng chứng: khôi phục role/home canonical và active key chính xác cho Question Bank.
- Tests: file trong diff `AppShellNavigationContractTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
