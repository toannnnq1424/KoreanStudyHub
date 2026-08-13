# Korean Study Hub — Git History Fix Audit 2026-07-26 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Tài liệu ghi nhận bằng chứng trong diff;
> không suy diễn rằng test hoặc browser QA đã được chạy nếu lịch sử không lưu bằng chứng đó.

## Quy ước

- **USER FIX**: commit của chủ repository qua các alias `toannqhe180972`, `toannq1424`
  hoặc `toannnnq1424`.
- **CONTRIBUTOR FIX**: commit của cộng tác viên khác.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### STORAGE-AUTHORIZED-OBJECT-DELIVERY-001 — Phân phối học liệu bỏ qua ranh giới lưu trữ

- Mức độ: **High** — Security / Storage.
- Nguồn sửa: **CONTRIBUTOR FIX** — HiuHi32, `58388a17` (`refactor(storage): serve lesson and library objects safely`).
- Phạm vi: controller bài học/kho học liệu, `ObjectStorage`, `StoredObjectResource`.
- Bằng chứng: luồng tải object được đưa qua storage abstraction có kiểm tra và xử lý lỗi,
  thay cho việc trả raw file path trực tiếp.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### ADMIN-GRANULAR-AUTHORITY-001 — Admin có quyền quá rộng theo role tổng

- Mức độ: **Critical** — Authorization.
- Nguồn sửa: **CONTRIBUTOR FIX** — HiuHi32, `87ed84f5` (`refactor(security): enforce granular admin authorities`).
- Phạm vi: admin controllers, permission resolver và security annotations.
- Bằng chứng: thêm authority `PERM_*` và `@PreAuthorize` theo từng controller, thay vì chỉ
  dựa vào role `ADMIN` tổng quát.
- Tests: không thấy file test trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### CLASS-CAPACITY-RACE-001 — Hai yêu cầu vào lớp có thể vượt sĩ số

- Mức độ: **High** — Concurrency / Business integrity.
- Nguồn sửa: **CONTRIBUTOR FIX** — HiuHi32, `6ff6dc72` (`fix(classes): serialize capacity checks for concurrent joins`).
- Phạm vi: `JoinClassService` và repository lớp/enrollment.
- Bằng chứng: khóa dòng lớp và dùng locking count trước khi kiểm tra `max_students`, tuần tự
  hóa các yêu cầu vào lớp cạnh tranh.
- Tests: file trong diff `JoinClassServiceTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
