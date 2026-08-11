# Korean Study Hub — Git History Fix Audit 2026-08-09 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Không có dated browser record cho ngày này;
> file test trong diff không chứng minh execution.

## Quy ước

- Commit của `toannqhe180972` được gắn **USER FIX**; commit khác là **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### CLASS-SIDEBAR-CONTEXT-001 — Sidebar học viên mất metadata lớp và hụt chiều cao

- Mức độ: **Medium** — UI / Navigation.
- Nguồn sửa: **CONTRIBUTOR FIX** — namdk24, `a4add4d8` (`fix UI (sidebar)`).
- Phạm vi: student-class CSS/templates và assignment fragments.
- Bằng chứng: min-height dùng viewport trừ đúng header; fragments nhận `classCode` và
  `lecturerName` thay vì null; duplicate/local sidebar fragments được bỏ.
- Tests: không thấy `src/test` file trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### LEGACY-CAPABILITY-RETIREMENT-001 — Discovery/direct-audio legacy tiếp tục mở attack surface

- Mức độ: **High** — Business / Attack-surface reduction.
- Nguồn sửa: **USER FIX** — toannqhe180972, `3c6bbf0a` (`refactor: retire discovery and practice legacy capabilities`).
- Phạm vi: Discovery controllers/services/templates; Practice direct-audio reviewer,
  dark-observation/collaboration và retirement migrations/contracts.
- Bằng chứng: xóa các capability/controller/template legacy và thêm static retirement contracts.
- Tests: retired-capability/static contracts xuất hiện trong diff; nhiều test legacy bị xóa;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — intentional historical retirement**; V112–V114
  drop news/content/login/token/direct-audio/collaboration tables và drop cột `practice_sets`.
  Đây không phải mẫu migration mới; thay đổi hiện tại chỉ được thêm cột.
- Lifecycle: **Completed retirement**. Mọi incident Discovery/direct-audio trước ngày này phải
  cross-reference commit `3c6bbf0a`.
