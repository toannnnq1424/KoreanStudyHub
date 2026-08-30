# Korean Study Hub — Git History Fix Audit 2026-07-27 (No Browser Run)

> Backfill lập ngày 2026-08-11 từ lịch sử Git. Tài liệu chỉ ghi nhận bằng chứng trong diff;
> test và browser QA không được coi là đã chạy nếu lịch sử không lưu execution record.

## Quy ước

- Các commit của `toannq1424` được gắn **USER FIX**.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.

## Incidents

### PRACTICE-RESPONSIVE-VIETNAMESE-NAV-001 — Workspace Practice vỡ điều hướng ở màn hình hẹp

- Mức độ: **Medium** — UI / Accessibility.
- Nguồn sửa: **USER FIX** — toannq1424, `8f551eab` (`fix(practice): preserve responsive Vietnamese practice workspace`).
- Phạm vi: CSS và templates learner/authoring Practice.
- Bằng chứng: điều hướng compact có accessible state, xử lý overflow/ellipsis, bảng responsive
  và giữ nhãn tiếng Việt nhất quán.
- Tests: file trong diff `PracticeFunctionalUiContractTest`,
  `PracticePhase11AuthoringUiContractTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-PRIVATE-MATERIAL-DELIVERY-001 — Học liệu riêng có thể bị phục vụ như file công khai

- Mức độ: **Critical** — Security / Authorization.
- Nguồn sửa: **USER FIX** — toannq1424, `393a39b2` (`fix(practice): secure private practice material delivery`).
- Phạm vi: `SecurityConfig`, material controller, access/authorization services.
- Bằng chứng: chỉ whitelist public upload paths rõ ràng, deny `/uploads/**`, phục vụ private
  material qua controller có authorization và header private/no-store.
- Tests: file trong diff `PracticeMaterialControllerTest`, `PracticeAuthorizationServiceTest`,
  `PracticeMaterialAccessServiceTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-SPEAKING-IMPORT-ASSET-AUTHORITY-001 — Import Speaking nhận asset không thuộc draft

- Mức độ: **Critical** — Authorization / Data integrity.
- Nguồn sửa: **USER FIX** — toannq1424, `a961d7ad` (`feat(practice): enforce Excel and PDF speaking import boundaries`).
- Phạm vi: Excel/PDF import services và ownership checks.
- Bằng chứng: asset Speaking chỉ được import khi đã xác minh và gắn đúng owner/draft.
- Tests: file trong diff `PracticeAssessmentExcelServiceTest`,
  `PracticeAssessmentExcelSpeakingBoundaryTest`, `PracticeImportDraftOwnershipTest`;
  **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-ASSET-LIFECYCLE-001 — Asset mồ côi và cleanup thiếu ràng buộc sở hữu

- Mức độ: **High** — Storage / Data lifecycle.
- Nguồn sửa: **USER FIX** — toannq1424, `df1ff7d0` (`feat(practice): harden prompt asset lifecycle and retention`).
- Phạm vi: lease/claim, orphan reconciler, reference guard và retention services.
- Bằng chứng: bổ sung lease/claim cleanup, orphan reconciliation, ownership/reference guard
  và retention policy cho prompt assets.
- Tests: các file lifecycle/ownership/reference/retention test trong diff, gồm
  `LecturerAssetServiceOwnershipTest`, `PracticeAssetOrphanReconcilerTest` và
  `SpeakingPromptLifecycleServiceTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.

### PRACTICE-SPEAKING-AUDIO-BOUNDARY-001 — Audio provider nhận nội dung không được xác minh đầy đủ

- Mức độ: **High** — Security / Reliability.
- Nguồn sửa: **USER FIX** — toannq1424, `b66b1700` (`feat(practice): add bounded speaking prompt audio providers`).
- Phạm vi: ffprobe verifier và speaking provider adapter.
- Bằng chứng: xác minh MIME/content bằng ffprobe và đặt giới hạn cho provider transport.
- Tests: file trong diff `FfprobeSpeakingPromptAudioVerifierTest`,
  `SpeakingPromptProviderAdapterTest`; **execution evidence not preserved**.
- Browser QA: **Missing dated browser record**; không tuyên bố đã chạy browser.
- Schema impact: **NONE**.
- Lifecycle: **Active**.
