# Korean Study Hub — Git History Fix Audit 2026-08-05 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Không có dated browser record hoặc execution
> record được lưu cho ngày này.

## Quy ước

- Commit của tác giả ngoài aliases chủ repository được gắn **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### FLASHCARD-EXCEL-EMPTY-ROW-001 — Import Excel để lại dòng thẻ trống/nhân đôi

- Mức độ: **Medium** — UI / Data import.
- Nguồn sửa: **CONTRIBUTOR FIX** — namdk24, `10784886` (`fix (flashcard): Display error when importing Excel file`).
- Phạm vi: `flashcard-deck-form.js`, `KshUserDetails`.
- Bằng chứng: zero-card import hiển thị info; dữ liệu nhập điền vào empty unsaved rows, xóa
  empty rows còn lại, đánh số lại và catch lỗi.
- Tests: không thấy `src/test` file liên quan trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
