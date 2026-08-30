# Korean Study Hub — Git History Fix Audit 2026-08-07 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Ngày 2026-08-06 không có commit cần lập report;
> ngày này không có dated browser hoặc test execution record.

## Quy ước

- Commit của tác giả ngoài aliases chủ repository được gắn **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### LIBRARY-SUBJECT-NAVIGATION-001 — Kho học liệu thiếu phân cấp theo mã môn/chương/bài

- Mức độ: **Medium** — UX / Information architecture.
- Nguồn sửa: **CONTRIBUTOR FIX** — namdk24, `653acdd9` (`fix (library): Separate lecture folders using lecture list pages organized by course code.`).
- Phạm vi: `LibraryController`, DTO/service, library index/list/detail templates và CSS/header.
- Bằng chứng: thêm subject list/search theo course code, back link, collapsible chapters với
  `aria-expanded` và chapter/lesson counts.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
