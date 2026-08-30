# Korean Study Hub — Git History Fix Audit 2026-07-30 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Không có dated browser record cho ngày này;
> file test trong diff không chứng minh test đã chạy.

## Quy ước

- Commit của `toannq1424`/`toannnnq1424` được gắn **USER FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### FLASHCARD-FLIP-GESTURE-001 — Thao tác lật flashcard bị giật và xung đột touch

- Mức độ: **Medium** — UI / Interaction.
- Nguồn sửa: **USER FIX** — toannnnq1424, `6274b6de` (`fix(flashcards): smooth tactile flip gestures`).
- Phạm vi: flashcard gesture CSS/JavaScript.
- Bằng chứng: bổ sung drag/touch state, `touch-action: none` và chờ card thoát trước khi
  chuyển trạng thái để tránh animation giật.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### FLASHCARD-IMAGE-TERM-OVERLAP-001 — Ảnh che nội dung thuật ngữ ở màn hình hẹp

- Mức độ: **Medium** — UI / Responsive.
- Nguồn sửa: **USER FIX** — toannnnq1424, `9d99951e` (`fix(flashcards): prevent image term overlap on narrow screens`).
- Phạm vi: flashcard responsive layout.
- Bằng chứng: điều chỉnh flex và image width để ảnh không đè lên term ở viewport hẹp.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### DISCOVERY-INGESTION-OPTIN-001 — News ingestion tự chạy khi chưa được bật rõ ràng

- Mức độ: **High** — External side effect / Security.
- Nguồn sửa: **USER FIX** — toannq1424, `3369bb97` (`fix(discovery): require explicit ingestion opt-in`).
- Phạm vi: Discovery ingestion scheduler/config.
- Bằng chứng: scheduler mặc định `enabled: false` và chỉ chạy khi có opt-in cấu hình.
- Tests: file trong diff `NewsIngestionSchedulerTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Retired** — capability được gỡ bởi `3c6bbf0a` ngày 2026-08-09.

### DISCOVERY-MIGRATION-SEQUENCE-001 — Migration Discovery trùng version với Practice

- Mức độ: **Critical** — Migration / Deployment.
- Nguồn sửa: **USER FIX** — toannq1424, `841b0459` (`fix(discovery): sequence migrations after practice V67`).
- Phạm vi: Flyway V63–V72 cho Discovery/Practice.
- Bằng chứng: renumber Discovery V63–V67 thành V68–V72 để không collision với Practice V67.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; chuỗi migration lịch sử
  có tạo bảng Discovery. Không được dùng làm tiền lệ; thay đổi mới chỉ được thêm cột.
- Lifecycle: **Retired** — Discovery được gỡ bởi `3c6bbf0a` ngày 2026-08-09.

### PRACTICE-AUTHORITY-RESULT-HARDENING-001 — Binding và kết quả Practice thiếu authority fail-closed

- Mức độ: **High** — Logic / Authorization.
- Nguồn sửa: **USER FIX** — toannq1424, `da85443b` (`feat(practice): complete authority cleanup and result hardening`).
- Phạm vi: AI transport/contracts, binding supersession, result presentation và role gate.
- Bằng chứng: transport/authority được siết chặt, binding cũ bị supersede, kết quả và route
  role được kiểm tra trước khi trình bày.
- Tests: không thấy test execution record tương ứng trong lịch sử; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; commit tạo
  `practice_user_preferences` và thêm cột. Không tạo bảng mới trong thay đổi hiện tại.
- Lifecycle: **Active**, trừ các phần legacy bị retirement sau này.
