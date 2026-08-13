# Korean Study Hub — Browser QA Incident Report Index

> Index lập ngày 2026-08-11 từ 400 commit gần nhất trong cửa sổ
> 2026-07-25 đến 2026-08-10, cộng với incident report hiện tại ngày 2026-08-11.

## Quy tắc bằng chứng

- **USER FIX** là commit của chủ repository qua các alias `toannqhe180972`, `toannq1424`
  hoặc `toannnnq1424`.
- **CONTRIBUTOR FIX** là commit của tác giả khác.
- “Tests” trong các file backfill chỉ nêu file test xuất hiện trong diff. Nếu không có execution
  record, tài liệu ghi rõ **execution evidence not preserved** và không tuyên bố test đã pass.
- “Browser QA: Missing dated browser record” nghĩa là không có bằng chứng browser theo ngày;
  không được chuyển thành trạng thái đã xác minh.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.
  Migration lịch sử bị gắn **OUTSIDE CURRENT CONSTRAINT** chỉ nhằm bảo toàn audit trail,
  không phải tiền lệ triển khai.

## Actual dated browser QA

Chỉ hai tài liệu dưới đây có browser evidence thực tế. Ngày trong cột đầu là ngày QA
được ghi nhận, không phải ngày backfill.

| Ngày QA | Tài liệu | Nguồn | Trọng tâm |
| --- | --- | --- | --- |
| 2026-07-29 | [Mở browser QA 29/07](BROWSER_QA_INCIDENT_REPORT_2026-07-29.md) | USER FIX | Test/QB/class/assignment/messaging/storage regressions |
| 2026-08-11 | [Mở browser QA 11/08](BROWSER_QA_INCIDENT_REPORT_2026-08-11.md) | Current fixes | Role/OIDC/session, class import, assignment, test deadline/XSS/QB, resource access, UI/editor, explanation reconcile |

## Git-history fix audits — reconstructed, no historical browser run

Các tài liệu dưới đây là incident audit theo đúng ngày commit/fix. Chúng được lập ngày
2026-08-11 từ Git history và **không phải browser QA chạy ngược thời gian**.

| Ngày fix | Tài liệu audit | Nguồn | Trọng tâm |
| --- | --- | --- | --- |
| 2026-07-26 | [Mở audit 26/07](BROWSER_QA_INCIDENT_REPORT_2026-07-26.md) | CONTRIBUTOR FIX | Storage delivery, admin authority, class-capacity race |
| 2026-07-27 | [Mở audit 27/07](BROWSER_QA_INCIDENT_REPORT_2026-07-27.md) | USER FIX | Practice responsive/a11y, private media, import authority, asset lifecycle |
| 2026-07-28 | [Mở audit 28/07](BROWSER_QA_INCIDENT_REPORT_2026-07-28.md) | USER FIX | Practice a11y/performance, canonical Question Bank navigation |
| 2026-07-30 | [Mở audit 30/07](BROWSER_QA_INCIDENT_REPORT_2026-07-30.md) | USER FIX | Flashcard UI, Discovery opt-in/migrations, Practice authority |
| 2026-07-31 | [Mở audit 31/07](BROWSER_QA_INCIDENT_REPORT_2026-07-31.md) | USER FIX + CONTRIBUTOR FIX | Test lifecycle/deadline, auth/session, AI JSON, responsive/data fixes |
| 2026-08-01 | [Mở audit 01/08](BROWSER_QA_INCIDENT_REPORT_2026-08-01.md) | USER FIX + CONTRIBUTOR FIX | Practice authority/UI, upload durability, student class-route boundary |
| 2026-08-02 | [Mở audit 02/08](BROWSER_QA_INCIDENT_REPORT_2026-08-02.md) | USER FIX + CONTRIBUTOR FIX | AI budget, candidate/storage authority, migrations, class approval |
| 2026-08-03 | [Mở audit 03/08](BROWSER_QA_INCIDENT_REPORT_2026-08-03.md) | USER FIX | Direct-audio privacy, AI completeness, QB/library/class canonical data |
| 2026-08-04 | [Mở audit 04/08](BROWSER_QA_INCIDENT_REPORT_2026-08-04.md) | USER FIX + CONTRIBUTOR FIX | Class-test context, TOPIK gate, canonical subject flow |
| 2026-08-05 | [Mở audit 05/08](BROWSER_QA_INCIDENT_REPORT_2026-08-05.md) | CONTRIBUTOR FIX | Flashcard Excel import hygiene |
| 2026-08-07 | [Mở audit 07/08](BROWSER_QA_INCIDENT_REPORT_2026-08-07.md) | CONTRIBUTOR FIX | Library subject/chapter/lesson navigation |
| 2026-08-08 | [Mở audit 08/08](BROWSER_QA_INCIDENT_REPORT_2026-08-08.md) | USER FIX | Migration identity, legacy codec, direct-audio readiness |
| 2026-08-09 | [Mở audit 09/08](BROWSER_QA_INCIDENT_REPORT_2026-08-09.md) | USER FIX + CONTRIBUTOR FIX | Sidebar context, legacy capability retirement |
| 2026-08-10 | [Mở audit 10/08](BROWSER_QA_INCIDENT_REPORT_2026-08-10.md) | USER FIX | QB hierarchy snapshots, one-shot submission, nested-surface polish |

## Ngày không lập report

- **2026-07-25**: hai commit platform/merge, không có qualifying bug-fix incident trong phạm vi audit.
- **2026-08-06**: không có commit trong cửa sổ lịch sử được audit.

## Schema-impact watchlist

Các report sau chứa thay đổi lịch sử **OUTSIDE CURRENT CONSTRAINT** và phải được đọc như
audit trail, không phải hướng dẫn migration:

- 2026-07-30: Discovery table creation chain; `practice_user_preferences` table creation.
- 2026-08-02: authoring-candidate table dependency; post-pre14 migration chain.
- 2026-08-03: reviewer-audit table creation; Question Bank/class destructive compaction.
- 2026-08-04: canonical-subject table creation/drop/refactor.
- 2026-08-08: direct-audio fresh-chain table creation.
- 2026-08-09: intentional drop-table/drop-column retirement.

Các thay đổi **ADD COLUMN ONLY — compliant** được ghi ở report 2026-07-28, 2026-08-01,
2026-08-02, 2026-08-03 và 2026-08-10.

## Lifecycle cross-reference

- Discovery và Practice direct-audio/reviewer legacy capabilities được retirement bởi
  USER FIX `3c6bbf0a` ngày 2026-08-09.
- Các incident lịch sử liên quan vẫn được giữ để giải thích risk/fix evolution nhưng phải có
  lifecycle **Retired** hoặc **Superseded**.

## Deep-audit tracks tiếp theo

1. Role/route boundaries và saved-request compatibility.
2. Flyway version identity, migration immutability và schema-policy compliance.
3. `classId` propagation, app shell/sidebar và responsive background coverage.
4. Canonical subject → chapter → lesson lineage và snapshot fallback.
5. AI fail-closed authority, bounded retries và result completeness.
6. Upload/media/session cleanup lifecycle.
7. One-shot business transitions và concurrency guards.
