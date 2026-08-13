# Korean Study Hub — Git History Fix Audit 2026-08-02 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Không có dated browser record cho ngày này;
> test file trong diff không phải execution evidence.

## Quy ước

- Commit của `toannq1424` được gắn **USER FIX**; commit của tác giả khác là
  **CONTRIBUTOR FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### AI-PROVIDER-PING-BUDGET-001 — Connectivity ping không đủ token cho reasoning model

- Mức độ: **Medium** — Availability.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `1896a9a7` (`fix(ai): expand provider connectivity request budget`).
- Phạm vi: `AiProviderService`.
- Bằng chứng: ping max-token budget tăng từ 5 lên 2048 để reasoning model có thể hoàn tất response.
- Tests: file trong diff `AiProviderConnectivityBudgetTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-BINDING-REVISION-ZERO-001 — Revision 0 bị coi là binding không hợp lệ

- Mức độ: **High** — Compatibility / Logic.
- Nguồn sửa: **USER FIX** — toannq1424, `6f3585ee` (`fix(practice): align AI binding revisions with zero-based identity`).
- Phạm vi: candidate/PDF assembler và control-plane structured adapter.
- Bằng chứng: validation đổi từ revision `< 1` sang `< 0`, cho phép zero-based revision 0.
- Tests: structured adapter, authoring candidate và PDF binding compatibility tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### SPEAKING-MEDIA-STORAGE-LIFECYCLE-001 — Playback/cleanup không nhớ backend lưu trữ gốc

- Mức độ: **High** — Data / Storage lifecycle.
- Nguồn sửa: **USER FIX** — toannq1424, `bc238795` (`feat(practice): harden speaking media storage lifecycle`).
- Phạm vi: media/cleanup entities, repositories/services và profiled audio storage.
- Bằng chứng: persist storage profile/provider/key và lease snapshot để playback/cleanup gọi
  đúng backend đã ghi object.
- Tests: `PracticeSpeakingMediaStorageLifecycleTest`, cleanup/media service và
  `ProfiledPracticeSpeakingAudioStorageTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE trong commit này**.
- Lifecycle: **Active**.

### AUTHORING-CANDIDATE-ATOMIC-AUTHORITY-001 — Candidate apply có thể dùng media sai owner hoặc apply dở dang

- Mức độ: **High** — Authorization / Data integrity.
- Nguồn sửa: **USER FIX** — toannq1424, `02f97802` (`feat(practice): add candidate review and atomic apply services`).
- Phạm vi: candidate preview/apply/material authority/repository/controllers.
- Bằng chứng: ownership check fail-closed và apply được đưa vào transaction/atomic boundary.
- Tests: không thấy file test trong chính diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical dependency**; commit không tạo
  bảng nhưng feature phụ thuộc candidate tables được tạo bởi `9a3c1afe` cùng ngày. Không tạo
  bảng mới trong thay đổi hiện tại.
- Lifecycle: **Active**.

### DATABASE-BOOTSTRAP-SCOPE-001 — Flyway tự tạo database và phụ thuộc global privilege

- Mức độ: **Critical** — Deployment / Migration.
- Nguồn sửa: **CONTRIBUTOR FIX** — thanhquach17, `b099beb7` (`chore(migrations): remove fragile database bootstrap and stale branding`).
- Phạm vi: V1/V54 và branding contract.
- Bằng chứng: bỏ `CREATE DATABASE IF NOT EXISTS ksh_db` khỏi Flyway, tránh chạy sai catalog
  hoặc đòi global privilege; đồng thời ULP được đổi về KSH.
- Tests: file trong diff `KshBrandingContractTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: bản sửa **không tạo bảng**.
- Lifecycle: **Active**.

### POST-PRE14-MIGRATION-IDENTITY-001 — Nhiều migration sau pre14 dùng trùng identity

- Mức độ: **Critical** — Migration / Deployment.
- Nguồn sửa: **USER FIX** — toannq1424, `8a2b9f4e` (`chore(db): reconcile post-pre14 migration identities`).
- Phạm vi: Discovery, class approval và Practice prompt migration identities.
- Bằng chứng: renumber migrations để loại version collisions sau baseline pre14.
- Tests: migration/static contract coverage có trong chuỗi thay đổi; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; resulting chain có table
  creation. Không dùng làm tiền lệ; thay đổi mới chỉ được thêm cột.
- Lifecycle: **Superseded** bởi reconcile migration ngày 2026-08-08.

### CLASS-LEADER-APPROVAL-001 — Lớp mới có thể join trước khi trưởng bộ môn duyệt

- Mức độ: **High** — Business / Authorization.
- Nguồn sửa: **CONTRIBUTOR FIX** — namdk24, `c75685b6` (`feat(classes): gate new classes behind department leader approval`).
- Phạm vi: class services/templates và leader approval flow.
- Bằng chứng: lớp mới ở `DRAFT`, không joinable cho tới khi leader approve.
- Tests: Classes/JoinClass/Leader integration tests trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **ADD COLUMN ONLY — compliant**; V80 thêm status/review fields cùng
  index/constraint vào `classes` hiện có.
- Lifecycle: **Active**.

### SUBJECTIVE-SCORING-EVIDENCE-001 — Điểm partial credit không gắn đúng evidence occurrence

- Mức độ: **High** — Assessment correctness.
- Nguồn sửa: **USER FIX** — toannq1424, `47cb150e` (`fix(practice): close subjective scoring and evidence contracts`).
- Phạm vi: speaking/writing rules, scoring engine và result presenter.
- Bằng chứng: partial-credit aggregation và exact evidence occurrence binding được chuẩn hóa.
- Tests: speaking/writing/scoring/result tests trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
