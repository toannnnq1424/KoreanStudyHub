# Korean Study Hub — Git History Fix Audit 2026-08-08 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Test file trong diff không phải bằng chứng
> execution; không có dated browser record cho ngày này.

## Quy ước

- Các commit của `toannq1424` được gắn **USER FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### PRACTICE-MIGRATION-IDENTITY-COLLISION-001 — Direct-audio migrations trùng version

- Mức độ: **Critical** — Migration / Deployment.
- Nguồn sửa: **USER FIX** — toannq1424, `df08c25c` (`fix(practice): reconcile migrations and full-suite contracts`).
- Phạm vi: Flyway direct-audio V88–V111 và migration/static contracts.
- Bằng chứng: di chuyển V88–V96 sang V103–V111 để reconcile identity và tránh collision.
- Tests: nhiều migration/static/integration contracts trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **OUTSIDE CURRENT CONSTRAINT — historical only**; dù diff chủ yếu rename,
  fresh chain có CREATE TABLE consent/grants/dark-observation/reviewer-audit. Không dùng làm
  tiền lệ; schema mới chỉ được thêm cột vào bảng có sẵn.
- Lifecycle: **Superseded/Retired** — direct-audio capability bị gỡ ngày 2026-08-09.

### PRACTICE-LEGACY-ASSESSMENT-TYPE-001 — Dữ liệu assessment legacy không decode được

- Mức độ: **High** — Logic / Compatibility.
- Nguồn sửa: **USER FIX** — toannq1424, `df08c25c`.
- Phạm vi: `AssessmentContractCodec`, `PracticeService`.
- Bằng chứng: map `MCQ`→`SINGLE_CHOICE`, `MCQ_MULTIPLE`→`MULTIPLE_ANSWER`,
  `MATCHING_INFORMATION`→`MATCHING`; writing provider unavailable chỉ được chấp nhận như
  intentionally non-scoring với task/policy bất biến và max score 0.
- Tests: compatibility/integration/attempt tests trong diff; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE cho incident này**.
- Lifecycle: **Active** cho compatibility codec.

### DIRECT-AUDIO-READINESS-EVIDENCE-001 — Readiness dùng evidence ID không chứng minh release safety

- Mức độ: **High** — Governance / Security.
- Nguồn sửa: **USER FIX** — toannq1424, `0432b279` (`fix(practice): simplify direct audio release governance`)
  và `52820d60` (`feat(practice): separate experimental audio demo readiness`).
- Phạm vi: Practice AI binding/control plane, direct-audio evaluation/adapter và readiness manifest.
- Bằng chứng: loại region/deletion-SLA IDs khỏi readiness proof; tách
  `EXPERIMENTAL_DEMO`; production fail-closed, demo chỉ nhận response schema-valid và active consent.
- Tests: control-plane/readiness/disclosure/evaluation/adapter tests trong diff;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: không tạo bảng mới trong fix; `0432b279` sửa file migration đã tồn tại nên có
  checksum risk, sau đó được reconcile bởi `df08c25c`.
- Lifecycle: **Retired** — capability bị gỡ bởi `3c6bbf0a` ngày 2026-08-09.
