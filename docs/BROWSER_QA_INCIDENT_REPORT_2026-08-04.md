# Korean Study Hub — Git History Fix Audit 2026-08-04 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Không có dated browser record cho ngày này;
> test file trong diff chỉ là coverage evidence.

## Quy ước

- Commit của `toannq1424` được gắn **USER FIX**; commit của tác giả khác là
  **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### CLASS-TEST-CONTEXT-PRESERVATION-001 — Result/review làm rơi class context

- Mức độ: **High** — Authorization / Navigation / Data context.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `4f71641a` (`fix(tests): preserve class context and compact test actions`).
- Phạm vi: `StudentClassTestsController`, Test DTO/services và detail/take/result/review templates.
- Bằng chứng: `requireClassScope` từ chối class mismatch; DTO giữ `classId` và
  `lastCompletedAttemptId`; links result/review tiếp tục mang class context.
- Tests: không có `src/test` file trong commit; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### TOPIK35-CANDIDATE-GATE-001 — Candidate TOPIK 35 bị block bởi điều kiện không bắt buộc

- Mức độ: **High** — Business / Content contract.
- Nguồn sửa: **USER FIX** — toannq1424, `30d0ca9d` (`fix(practice): narrow TOPIK 35 candidate gates`).
- Phạm vi: `AnswerSpec`, assessment codec, TOPIK 35 importer và Practice service.
- Bằng chứng: listening timing thành optional; chỉ defer allowed package blockers; answer-spec-v2
  nêu writing evaluation mode và structured blank authority.
- Tests: codec/importer/persistence/service tests trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### CANONICAL-SUBJECT-LEARNING-FLOW-001 — Các module dùng subject identity không đồng nhất

- Mức độ: **High** — Business / Data model.
- Nguồn sửa: **USER FIX** — toannq1424, `0c499785` (`feat(nonpractice): complete subject learning flows`).
- Phạm vi: subjects, classes, library, Question Bank, tests, leader, admin và student flows.
- Bằng chứng: thiết lập canonical subject learning flow xuyên hệ thống và nối test bank với
  lesson/subject authority.
- Tests: nhiều integration/contract tests ở admin/classes/library/QB/tests/student trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; tạo `subjects`,
  `activity_subjects`, `subjects_activities`, drop `activity_subjects`, thêm nhiều cột và sửa
  base migrations. Không được tái áp dụng; thay đổi hiện tại chỉ được thêm cột vào bảng sẵn có.
- Lifecycle: **Active**, với hierarchy snapshot bổ sung ngày 2026-08-10.
