# Practice Pre-production Legacy Import Retirement and Schema Compaction Live Report

Recorded: `2026-08-02`

Program: `PRACTICE_PREPRODUCTION_LEGACY_IMPORT_RETIREMENT_AND_SCHEMA_COMPACTION`

Current slice: `CLEAN_CUT_4_STORAGE_AND_SCHEMA_COMPACTION`

Status: `CLEAN_CUT_4_IMPLEMENTED__COORDINATOR_AUDIT_PENDING__CLEAN_CUT_5_NOT_STARTED`

## 1. Checkpoint and authority

### 1.1 Baseline evidence

The mandatory baseline was verified before this report was created:

| Check | Observed value | Result |
|---|---|---|
| `HEAD` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| local `main` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| `origin/main` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| worktree before work | no tracked or untracked changes | PASS |
| checkout during the mandatory clean-start check | detached at the exact required SHA | PASS; this slice did not create or change a branch |
| coordinator-owned branch observed during final evidence | `codex/practice-clean-cut-legacy-retirement-schema-compaction` at the same SHA | RECORDED; the checkout occurred externally after the clean-start check |

This slice did not create a task, worktree or branch and did not commit, push,
open a pull request, merge, run a database mutation or call a real AI, R2, STT
or TTS provider.

### 1.2 Documents read in full

The following authorities were read in full, not sampled:

1. `docs/PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`;
2. `docs/decisions/0012-practice-authoring-import-modernization-boundaries.md`;
3. `docs/architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`;
4. `docs/PRACTICE_AIM8_COMPATIBILITY_AND_EPIC_CLOSE_PREP_LIVE_CHANGE_LOG.md`;
5. `docs/PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE_LIVE_REPORT.md`; and
6. `docs/operations/practice-authoring-import-modernization-runbook.md`.

They establish the existing candidate boundary, the six exact Practice AI
purposes, the three exact storage profiles, exact-profile object reads, bounded
legacy-null reads, immutable V1-V85 migrations and forward-only rollback. The
old authorities deliberately left Legacy Excel V1, Advanced Excel V2, Advanced
PDF crop/region and V83-V85 additive compatibility in service pending this
separate inventory and approval. This report is that clean-cut inventory and
contract freeze; it does not rewrite the historical evidence.

### 1.3 Locked product and safety decisions

- The present database contains only disposable test data. No row-copy, object
  copy or preservation plan is authorized or required.
- No shared database, shared bucket or shared local upload root may be cleaned,
  reset, repaired, deleted or otherwise operated on by this program. Later
  migration validation uses newly created disposable catalogs only.
- Feature parity, authentication, ownership, target authorization, FK/object
  graph ordering, migration continuity, rollback/read compatibility and Practice
  regression remain mandatory.
- Excel and PDF importers stop at a persistent candidate. Only explicit candidate
  apply may mutate a draft; publish remains separate.
- Practice AI and R2 remain their own data plane. Admin manages only the control
  plane. There is no global AI client, cross-purpose fallback, storage-profile
  fallback or `GENERAL_UPLOADS` fallback.
- The six purpose identities stay exactly `PRACTICE_PDF_AUTHORING`,
  `PRACTICE_RL_EXPLANATION`, `PRACTICE_WRITING_EVALUATION`,
  `PRACTICE_SPEAKING_EVALUATION`, `PRACTICE_SPEAKING_STT` and
  `PRACTICE_SPEAKING_TTS`. Binding revision `0` is valid; only negative,
  missing or non-integral revision identity fails closed.
- The storage profiles stay exactly `GENERAL_UPLOADS`, `PRACTICE_AUTHORING` and
  `PRACTICE_SPEAKING`.
- Phase 14, Phase 15, Pre-15, Speaking direct-audio/acoustic evaluation and
  provider API-console/documentation links are excluded.
- Automated validation must make real calls `AI/R2/STT/TTS = 0/0/0/0`.
- CLEAN_CUT_1 through CLEAN_CUT_6 use this one worktree and coordinator-owned
  branch. After auditing both slices, the coordinator created the single local
  CLEAN_CUT_1 + CLEAN_CUT_2 commit
  `d02be7843c8fd77a72600121f8bc4f1e31a9ae76`. Later slices return to one
  coordinator-created audited local commit per slice. There is no inter-slice
  merge, push or PR. Only after CLEAN_CUT_6 and consolidated green evidence may
  the coordinator push the full chain and merge one PR.

## 2. Classification vocabulary

Two separate classifications are used so that “dead now” is not confused with
“safe to remove only after replacement.”

| Evidence class | Meaning |
|---|---|
| `PROVEN_DEAD` | No production writer/caller/consumer was found in the repository, or the only visible control is demonstrably a no-op. Tests may still name it. Remove only in its assigned slice and update tests at the same time. |
| `RETIRE_AFTER_REPLACEMENT` | A current route, UI, writer, reader or compatibility surface exists. It cannot be removed until the replacement and all parity/auth/ownership gates listed here pass. |
| `KEEP` | Canonical, shared, externally necessary, compatibility-critical or not proven dead. Absence of an in-repository link alone is not proof of an external route's death. |

| Lifecycle disposition | Meaning |
|---|---|
| `KEEP` | Remains an active production contract after CLEAN_CUT_6. |
| `RETIRE` | Production route/code/UI/writer/reader is removed in CLEAN_CUT_2, 3 or 4 after its gate passes. Historical migration bytes and persisted source identities are not erased. |
| `DROP_LATER` | Physical table/column/FK/index or compatibility tombstone is changed only by a new forward migration after owning runtime code is gone and the exact preflight passes. |

## 3. Canonical destination graph

All retained import paths converge on the same boundary:

```text
Quick Excel or Basic Text/PDF
  -> source-specific bounded parse/generation
  -> PracticeAuthoringCandidateService
  -> practice_authoring_candidates
  -> candidate-review.html + candidate-review.js/css
  -> explicit authorized apply + replay ledger
  -> canonical PracticeDraft exact target section
  -> editor.html + manage-authoring-contract.js
  -> PracticeDraftPreviewService + manage-draft-preview.js
  -> PracticeDraftValidator -> PracticePublisherService
  -> immutable published object graph and learner presenters
```

The candidate is persistent; a preview projection is in memory; a candidate is
never written into `PracticeDraft.snapshotJson`; and PDF session
`snapshot_json` is only the legacy Advanced PDF workspace snapshot. The latter
must not be reused as a candidate or canonical draft contract.

The V83 `source_kind` identities `QUICK_EXCEL`, `ADVANCED_EXCEL_V2`,
`LEGACY_EXCEL_V1` and `PDF_AI`, their check constraint, contract versions,
candidate JSON and apply-event rows remain readable. CLEAN_CUT_2/3 stop new
Advanced/Legacy producers but do not rewrite old source identities.

The retained UI/API owners used to satisfy replacement parity are:

| Canonical owner | Retained routes/surfaces |
|---|---|
| `PracticeAuthoringCandidateReviewController` + `candidate-review.html/js/css` | `GET /practice/manage/authoring-candidates/{id}`, data, review, ready, reject, learner-preview and apply routes |
| `PracticeDraftController` + `editor.html` | exact draft editor/catalog, preview, publish-blockers, autosave, publish, draft audio upload and draft image upload routes |
| `PracticePdfImportApiController` reduced asset API | owner-filtered asset list/delete and exact draft-asset link; PDF session query/update/promote/unlink shapes retire as classified below |
| `PracticeMaterialController` | authenticated, reference-authorized, ranged `GET /practice/materials/{assetId}/content`; this is the canonical private media URL used by Excel overrides and editor preview |
| `PracticeMaterialLibraryPageController` + `material-library.html` | lecturer library page and its separate asset-delete form route |
| `SpeakingPromptAuthoringController` + canonical prompt script/CSS | exact draft/question state, media, manual-text save, original `/audio` upload/delete, transcript revise/retry and TTS request; only `/audio/excel-staging` retires. Automated gates use fakes and make no STT/TTS call |

## 4. Excel ownership and consumer graph

### 4.1 Routes and UI consumers

All routes are lecturer-only through `Roles.PREAUTH_LECTURER` in
`PracticeAssessmentExcelController`.

| Route | Production owner and consumer | Current behavior | Evidence class | Final disposition |
|---|---|---|---|---|
| `GET /practice/manage/excel` | controller -> `practice/manage/excel-import.html`; editor builds the exact draft/test/skill/lesson query | shared upload/review page for all three formats | `RETIRE_AFTER_REPLACEMENT` for its multi-format shape | `KEEP` as Quick-only page |
| `GET /practice/manage/excel/template` | controller -> `PracticeAssessmentExcelService.buildTemplate()` -> V2 codec; Advanced button in `excel-import.html` | downloads Advanced V2 workbook | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` in CLEAN_CUT_2 |
| `GET /practice/manage/excel/template/quick-v1` | controller -> Quick codec; Quick button in `excel-import.html` | downloads Quick text-only workbook | `KEEP` | `KEEP` |
| `POST /practice/manage/excel/preview` | inline page JS -> controller -> `preview(file, context)` | detects and previews Quick, Advanced or Legacy | `RETIRE_AFTER_REPLACEMENT` for Advanced/Legacy branches | `KEEP` as Quick-only endpoint |
| `POST /practice/manage/excel/import` | inline page JS -> controller -> `createCandidate(...)` | creates a source-kind candidate, never writes a draft | `RETIRE_AFTER_REPLACEMENT` for Advanced/Legacy branches | `KEEP` as Quick-only endpoint |

The only production navigation owner is the canonical editor. Its initial Excel
link lacks full context but is hidden; editor JavaScript later writes
`draftId`, `testNo`, `skill` and `lessonCode`. This dynamic target construction
is `KEEP` and must retain exact authorization and stale-target behavior.

### 4.2 Format detection and parse graph

Detection order is contract-significant and currently exact:

```text
Quick marker
  -> QUICK_V1
else sheet 01_THONG_TIN_SET present
  -> ADVANCED_V2
else workbook sheet-name set equals exactly
     Manifest, Sections, Groups, Questions, OptionsAnswers
  -> LEGACY_V1
else
  -> SCHEMA_VERSION_UNSUPPORTED
```

| Format | Code owner | Source shape and capabilities | Candidate identity | Decision |
|---|---|---|---|---|
| Quick text-only | `PracticeAssessmentQuickExcelCodec` plus `PracticeAssessmentExcelService` | R/L single choice, multiple answer, TFNG and exactly one-token `FILL_BLANK`; Writing Q51/Q52 exact two-blank authority and Q53/Q54 essay; Speaking manual Korean text only; no media/MATCHING/complex R/L blanks | `QUICK_EXCEL` | `KEEP` |
| Advanced V2 | `PracticeAssessmentExcelV2Codec` plus service adapter | `01_THONG_TIN_SET`, `02_TAI_NGUYEN`, `03_SINGLE_CHOICE`, `04_TRUE_FALSE_NG`, `05_FILL_BLANK`, `06_ESSAY`, `07_SPEAKING`, `08_MULTIPLE_ANSWER`, `09_MATCHING`; group/question/option media columns and multi-blank authority; Speaking requires owned verified prompt audio. `SECTION` material metadata is accepted but has no downstream attachment consumer | `ADVANCED_EXCEL_V2` | `RETIRE` after replacement gate |
| Legacy V1 | legacy readers/builders inside `PracticeAssessmentExcelService` | exact five sheets; old options/answer/blank shape; group image/audio fields; Speaking is blocked because the format cannot supply required prompt audio | `LEGACY_EXCEL_V1` | `RETIRE` after replacement gate |

All three flow through candidate normalization/validation/review/apply. No Excel
path owns canonical draft persistence.

### 4.3 Excel code, template and asset ownership

| Surface | Ownership | Decision |
|---|---|---|
| `PracticeAssessmentExcelController` | live Quick and legacy/Advanced routes | `KEEP` reduced to Quick |
| `PracticeAssessmentExcelService` | target context, detection, legacy adapter, V2/Quick orchestration, candidate creation | `KEEP` reduced to Quick; retire legacy/V2 branches |
| `PracticeAssessmentQuickExcelCodec` | retained Quick template/parser | `KEEP` |
| `PracticeAssessmentExcelV2Codec` | Advanced template/parser | `RETIRE` in CLEAN_CUT_2 |
| `PracticeAssessmentExcelException` and Excel DTO/records nested in the service/controller | stable issue envelope and response payloads | retain only Quick-owned members; remove proven-unused/retired members |
| `practice/manage/excel-import.html` | all Excel CSS and JavaScript are inline except shared `app-shell.css` and `manage-editor.css` | `KEEP` as Quick-only template; remove Advanced controls, columns, handlers and selectors together |
| `EXCEL_MEDIA` material placement and `LecturerAssetService.linkExcelManagedUploadToDraft` | Advanced Excel media authorization/reference handoff | `RETIRE_AFTER_REPLACEMENT`; remove producer and exact Excel-only seam in CLEAN_CUT_2 |
| `SPEAKING_PROMPT_EXCEL_STAGING`, Excel staging adoption endpoint/service/UI | Advanced Excel Speaking audio handoff into canonical prompt state | `RETIRE_AFTER_REPLACEMENT`; manual canonical prompt upload must pass first |
| V83 source kinds and old candidate contract versions | read/review/audit identity | `KEEP` read-only after producer retirement |

## 5. PDF ownership and consumer graph

### 5.1 Retained Basic path versus Advanced workspace

```text
Basic TEXT
  -> buildBasicText -> strict PRACTICE_PDF_AUTHORING request
  -> PracticePdfAiOrchestrator -> candidate assembler -> PDF_AI candidate

Basic PDF today
  -> create practice_pdf_import_sessions row
  -> store practice-pdfs/** object
  -> select page range -> page extraction rows
  -> buildBasicPdf -> same strict orchestrator/candidate boundary

Advanced PDF today
  -> session + stored PDF + workspace snapshot
  -> page extraction + crop/region annotations
  -> optional temporary/promoted lecturer assets
  -> generation claim/lease -> payload preview/builder
  -> same strict orchestrator/candidate boundary
```

Basic PDF therefore cannot survive a blind table drop. CLEAN_CUT_3 must first
replace its session/object persistence with a bounded request-local PDF parser
and temporary-file/byte lifecycle, retaining the 20 MiB limit, `%PDF-` header
validation, page-range validation, exact target authorization, source evidence
and digest, strict provider schema, candidate persistence and cleanup in every
success/error/cancel path. Basic Text remains request-local.

### 5.2 Complete PDF/import route ownership

All controller routes below are lecturer-only. Ownership checks resolve the
authenticated user and, where applicable, the exact target draft/session/region.

| Route | Current consumer/owner | Evidence class | Final disposition |
|---|---|---|---|
| `GET /practice/manage/import` | dashboard and editor links -> `PracticeImportController` -> `import-wizard.html` | `RETIRE_AFTER_REPLACEMENT` for mixed Basic/Advanced page | `KEEP` as Basic Text/PDF page with exact target context |
| `GET /practice/manage/import-sessions/{id}/workspace` | recent-session links and wizard redirect -> `import-workspace.html` | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` CLEAN_CUT_3 |
| `POST /practice/manage/import-sessions` | Advanced wizard upload | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `GET /practice/manage/import-sessions/{id}` | workspace bootstrap | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `PUT /practice/manage/import-sessions/{id}/page-range` | Advanced wizard; sent `extractionMode` is ignored | `RETIRE_AFTER_REPLACEMENT`; ignored DTO member is `PROVEN_DEAD` | `RETIRE` |
| `GET /practice/manage/import-sessions/{id}/file` | PDF.js workspace | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `POST /practice/manage/import-sessions/{id}/save` | workspace autosave and broken rename control | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `POST /practice/manage/import-sessions/{id}/cancel-changes` | workspace undo -> snapshot restore | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `DELETE /practice/manage/import-sessions/{id}` | workspace delete | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `GET /practice/manage/import-sessions/{id}/extracted-text` | workspace page text | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `GET /practice/manage/import-sessions/{id}/annotations` | workspace | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `POST /practice/manage/import-sessions/{id}/annotations` | workspace | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `PUT /practice/manage/import-sessions/{id}/annotations/{annotationId}` | workspace autosave | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `DELETE /practice/manage/import-sessions/{id}/annotations/{annotationId}` | workspace | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `GET /practice/manage/import-sessions/{id}/payload-preview` | workspace review | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `POST /practice/manage/import-sessions/{id}/generate` | workspace -> fenced claim -> Advanced authoring | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `POST /practice/manage/pdf-authoring/candidates` | Basic Text/PDF form | `KEEP` after Basic PDF replacement | `KEEP` |
| `GET /practice/manage/assets` | workspace and canonical editor asset library | `KEEP`; remove session query shape only | `KEEP` |
| `PATCH /practice/manage/assets/{assetId}` | Advanced workspace asset metadata editor only | `RETIRE_AFTER_REPLACEMENT` unless refreshed inventory finds a canonical consumer | `RETIRE` currently locked for CLEAN_CUT_3 |
| `DELETE /practice/manage/assets/{assetId}` | canonical editor and workspace; material-library deletion is a separate controller route over the same shared service | `KEEP` shared | `KEEP` |
| `POST /practice/manage/import-sessions/{id}/regions/{regionId}/promote-asset` | workspace only | `RETIRE_AFTER_REPLACEMENT` | `RETIRE` |
| `POST /practice/manage/drafts/{draftId}/assets` | canonical editor asset linking | `KEEP` | `KEEP` |
| `DELETE /practice/manage/drafts/{draftId}/assets/{referenceId}` | no production HTML/JS caller found; service ownership test only | `PROVEN_DEAD` for this application UI contract | `RETIRE` in CLEAN_CUT_3 after route scan repeats |
| `GET /practice/manage/upload` | compatibility redirect to `/practice/manage/import`; no in-repository link | external/bookmark ownership is unknown | `RETIRE_AFTER_REPLACEMENT`, not proven dead |
| `GET /practice/manage/manual` | compatibility redirect to `/practice/manage`; no in-repository link | external/bookmark ownership is unknown and it is not PDF-specific | `KEEP` pending generic route telemetry/approval; re-evaluate CLEAN_CUT_4 |

The dashboard direct import link currently supplies no draft target. Basic
authoring is consequently disabled there while Advanced can still start a new
session. CLEAN_CUT_3 must remove/retarget that entry or provide an authorized
canonical target-selection flow; it must not leave a Basic-only half-dead page.
The editor link remains the canonical target-aware Basic entry.

### 5.3 PDF controller/service/entity/repository graph

| Layer | Exact owners | Decision |
|---|---|---|
| Page/API controllers | `PracticeImportController`, `PracticePdfImportApiController` | retain reduced Basic page/API; retire every session/region/workspace branch |
| Workspace/session | `PracticePdfImportSessionService`, `PracticeImportSnapshotService`, `PracticePdfPreviewService`, `PracticePdfAiGenerationService` | `RETIRE` after Basic PDF is independent |
| Region/crop/extraction | `PracticePdfRegionService`, `PracticePdfCropService`, `PracticePdfRegionAssetSelector`, `PracticePdfPageExtractionService`, `PracticePdfPayloadPreviewService` | `RETIRE` |
| Request builder | `PracticePdfAiPayloadBuilder` | keep Basic Text/PDF request building after removing session/region/section/group/storage-null branches |
| Advanced payload DTO/validator | `AiDocumentImportRequest`, `ImportAiPayloadValidator` | `RETIRE` with Advanced region/crop payload; they are consumed by the payload builder/preview and their guided-mode tests, not by retained Basic Text/PDF |
| Strict retained provider boundary | `PracticePdfAuthoringRequest`, `PracticePdfAuthoringJsonContract`, `PracticePdfAuthoringOutputValidator`, `PracticePdfAiLimits`, `PracticePdfAiOrchestrator`, `PracticePdfAuthoringCandidateAssembler` | `KEEP`; remove only `ADVANCED_PDF` request branch and legacy session-audit dual write |
| PDF storage | `PracticePdfStorageService` | `RETIRE` after request-local Basic replacement |
| Session graph entities | `PracticePdfImportSession`, `PracticePdfRegionAnnotation`, `PracticePdfPageExtraction`, `PracticePdfImportSectionDraft`, `PracticePdfImportGroupDraft`, `PracticeAiRequestAudit` | `RETIRE` code in CLEAN_CUT_3; tables `DROP_LATER` CLEAN_CUT_4 |
| Session graph repositories | matching six repositories | `RETIRE` with entities after all consumers are removed |
| Shared asset core | `LecturerAsset`, `LecturerAssetRepository`, `LecturerAssetService`, `PracticeMaterialReference*`, lifecycle services | `KEEP` reduced to canonical manual/TTS/material behavior; remove PDF provenance and import-session methods only |

`PracticePdfAiOrchestrator` currently writes the old
`practice_ai_request_audits` record only when a session ID exists, while the
V84 purpose-specific execution audit is the retained control-plane record.
Removing the session-only dual write does not remove or weaken V84 auditing.

### 5.4 Relational object graph frozen for compaction

The exact legacy PDF graph is:

```text
practice_pdf_import_sessions
  <- practice_pdf_page_extractions.session_id
       FK fk_page_extract_session ON DELETE CASCADE
  <- practice_pdf_region_annotations.session_id          (logical; no FK)
  <- practice_pdf_import_section_drafts.session_id        (logical; no FK)
  <- practice_pdf_import_group_drafts.session_id          (logical; no FK)
  <- practice_ai_request_audits.session_id                (logical; no FK)
  <- lecturer_assets.source_import_session_id             (logical; no FK)
       + source_region_id/page/crop provenance            (logical; no FK)
  -> storage_profiles.profile_code through
       fk_pdf_session_storage_profile
```

Exact table/index/FK identities:

| Table | Identity to preserve until CLEAN_CUT_4 drop |
|---|---|
| `practice_pdf_import_sessions` | PK `id`; indexes `idx_pdf_session_uploader`, `idx_pdf_session_target`, `idx_pdf_session_generation_lease`, `idx_pdf_session_profile_path`; FK `fk_pdf_session_storage_profile`; columns added later include target triplet, generation claim/lease and `storage_profile_code` |
| `practice_pdf_region_annotations` | PK `id`; indexes `idx_pdf_region_session_page`, `idx_pdf_region_session_type`, `idx_pdf_region_session_order`; no DB FK |
| `practice_pdf_import_section_drafts` | PK `temp_id`; index `idx_pdf_section_draft_session`; no DB FK |
| `practice_pdf_import_group_drafts` | PK `temp_id`; index `idx_pdf_group_draft_session`; no DB FK |
| `practice_pdf_page_extractions` | PK `id`; index `idx_page_extract_session_page`; FK `fk_page_extract_session` to session with cascade |
| `practice_ai_request_audits` | PK `id`; index `idx_ai_audit_session`; no DB FK |
| `lecturer_assets` PDF-only provenance | `source_import_session_id`, `source_region_id`, `source_page_number`, `crop_x`, `crop_y`, `crop_width`, `crop_height`; index `idx_lecturer_assets_session`; no DB FK |

Section/group draft rows have repository readers in the payload builder but no
production writer. With the locked empty/disposable database they cannot be
created by the application, and the builder already has a target-section
fallback. They are `PROVEN_DEAD`, not replacement state.

## 6. Storage and legacy-null consumer graph

### 6.1 Object-key identities

| Profile/root and key family | Current owner | Decision |
|---|---|---|
| `PRACTICE_AUTHORING` + `practice-pdfs/{uploaderId}/temporary/objects/{uuid}/...` | `PracticePdfStorageService` through profiled authoring storage | `RETIRE`; no object deletion is performed |
| bounded local root `${app.upload.dir}/practice-pdfs` for null-profile absolute PDF paths | `PracticePdfStorageService.open/delete/existsLegacy` | `RETIRE` only after zero-null/zero-object preflight; never scan/delete a shared root |
| `PRACTICE_AUTHORING` + `lecturer-assets/{ownerId}/imports/{sessionId}/temporary/...` | crop temporary asset writer | `RETIRE`; no object deletion is performed |
| `PRACTICE_AUTHORING` + `lecturer-assets/{ownerId}/imports/{sessionId}/library/...` | region promotion | `RETIRE`; no object deletion is performed |
| `PRACTICE_AUTHORING` + `lecturer-assets/{ownerId}/drafts/{draftId}/private/{assetType}/...` | canonical manual editor/material library | `KEEP` |
| `PRACTICE_AUTHORING` + draft generated-audio keys | canonical lecturer prompt TTS artifact | `KEEP`; tests remain fake-only |
| `PRACTICE_SPEAKING` + `learner-speaking/temporary/**` and `learner-speaking/ready/**` | learner response media lifecycle | `KEEP`; this is separate from authoring and direct-audio evaluation remains excluded |

`ProfiledPracticeAuthoringStorage` is the primary writer and accepts only
`lecturer-assets/**` or `practice-pdfs/**`; `LocalAssetStorageService` is its
bounded null-profile compatibility reader/deleter. The equivalent Speaking
pair is `ProfiledPracticeSpeakingAudioStorage` and
`LocalPrivateSpeakingAudioStorage`. New writes already resolve an exact profile.

### 6.2 Complete storage-profile null consumers in Practice scope

The complete production ownership set that branches on, queries, transports or
delegates a nullable storage-profile identity belongs to the CLEAN_CUT_4 refresh
inventory:

- authoring/material identity and repositories: `LecturerAsset`,
  `PracticeAssetLifecycleTask`, `LecturerAssetRepository` (including the
  null-profile `findByStorageKeyForUpdate` lock),
  `PracticeAssetLifecycleTaskRepository` (including the null-profile active-task
  lock), `AssetStorageService`, `ProfiledPracticeAuthoringStorage`,
  `LocalAssetStorageService`, `LecturerAssetService`,
  `PracticeMaterialAccessService`, `PracticeAssetLifecycleTaskExecutor`,
  `PracticeAssetLifecycleTaskTransactions` and
  `PracticeAssetOrphanReconciler`;
- PDF identity consumers scheduled for earlier retirement:
  `PracticePdfImportSession`, `PracticePdfStorageService`,
  `PracticePdfImportSessionService`, `PracticePdfPreviewService`,
  `PracticePdfPageExtractionService`, `PracticePdfCropService` and
  `PracticePdfAiPayloadBuilder`;
- Speaking identity, projections, repositories and services:
  `PracticeSpeakingMedia`, `PracticeSpeakingMediaCleanupTask`,
  `CleanupProcessingSnapshot`, `PracticeSpeakingMediaRepository`,
  `PracticeSpeakingMediaCleanupTaskRepository`, `SpeakingAudioStorage`,
  `ProfiledPracticeSpeakingAudioStorage`, `LocalPrivateSpeakingAudioStorage`,
  `PreparedSpeakingAudio`, `StoredSpeakingAudioObject`,
  `SpeakingAudioPreparationService`, `ValidatedSpeakingMediaDescriptor`,
  `SpeakingAudioUploadService`, `PracticeSpeakingMediaService`,
  `PracticeSpeakingMediaPlaybackService`,
  `PracticeSpeakingMediaCleanupTaskService`,
  `PracticeSpeakingMediaCleanupProcessor`,
  `PracticeAttemptDiscardTransactionService` and
  `SpeakingTranscriptionMediaResolver`; and
- explicit migration compatibility: `PracticeStorageMigrationJob`,
  `PracticeStorageMigrationLogicalType`, `PracticeStorageMigrationClaim`,
  `PracticeStorageMigrationCoordinator`, `PracticeStorageMigrationJobService`,
  `PracticeStorageMigrationIdentityService`,
  `ProfiledPracticeStorageMigrationObjectPort` and
  `PracticeStorageMigrationJobRepository`.

This list is deliberately separate from unrelated content/result legacy readers
such as old answer JSON, published question snapshots or learner scoring. Those
are `KEEP`; the program does not infer that “legacy import retirement” permits
removing learner/read compatibility.

Admin's `StorageProfileAdminService.referenceCount` is a cross-feature consumer:
its SQL names both `practice_pdf_import_sessions` and
`practice_storage_migration_jobs`. CLEAN_CUT_3 must remove the session-table
subquery before that table can be dropped. The migration-job table remains an
empty rollback tombstone through CLEAN_CUT_6, so its Admin subquery remains
valid for the supported prior runtime.

## 7. Dead and retirement surface ledger

### 7.1 Proven dead now

| ID | Candidate and evidence | Class | Owning slice |
|---|---|---|---|
| EX-D01 | `PracticeAssessmentExcelService.preview(MultipartFile)` has test callers only; production controller uses the context-aware overload | `PROVEN_DEAD` | CLEAN_CUT_2 |
| EX-D02 | compatibility Excel service constructors are instantiated by tests only | `PROVEN_DEAD` | CLEAN_CUT_2, after Spring constructor and tests are normalized |
| EX-D03 | Advanced `02_TAI_NGUYEN` accepts `material_level=SECTION` and serializes the row into root `materials`, but no adapter/projector/editor/preview field attaches it to a section; candidates project exact target groups only | `PROVEN_DEAD` advertised metadata path | CLEAN_CUT_2 |
| PDF-D01 | `PracticePdfTextExtractionService` has no production injector/caller | `PROVEN_DEAD` | CLEAN_CUT_3 |
| PDF-D02 | `PracticePdfImportSectionDraft` and `PracticePdfImportGroupDraft` plus their repository reads have no production writer | `PROVEN_DEAD` | code CLEAN_CUT_3, schema CLEAN_CUT_4 |
| PDF-D03 | group repository `findBySessionIdAndSectionTempIdOrderByDisplayOrderAsc`, region repository page-only lookup, page-extraction ordered-list lookup and AI-audit ordered-list lookup have no production caller | `PROVEN_DEAD` | CLEAN_CUT_3 |
| PDF-D04 | session service `updateStatus` and `updateDraftId` have no production caller | `PROVEN_DEAD` | CLEAN_CUT_3 |
| PDF-D05 | `PageRangeRequest.extractionMode` is sent as `FULL_SELECTED_PAGES` by the wizard but ignored by controller/service and has no entity mapping; DB `extraction_mode` is likewise unmapped | `PROVEN_DEAD` | CLEAN_CUT_3/4 |
| PDF-D06 | `renameWorkspaceSession()` prompts for a name but POSTs only current page and extraction strategy to a DTO with no title field, then reloads; the name can never change | `PROVEN_DEAD` broken UI | CLEAN_CUT_3 |
| PDF-D07 | editor's “Từ tệp PDF này” asset tab reads `window.currentSessionId`; no Practice production code assigns it, so the live tab can only show the no-session message | `PROVEN_DEAD` orphan tab/handler | CLEAN_CUT_3 |
| PDF-D08 | draft asset unlink API has no production template/JavaScript caller; only ownership tests name the service method | `PROVEN_DEAD` within the application UI contract | CLEAN_CUT_3, after repeated route scan |
| ST-D01 | the explicit storage migration coordinator has no startup runner, scheduler, route or other production invoker | `PROVEN_DEAD` as an automatic/live execution surface; its compatibility schema is handled separately | CLEAN_CUT_4 |

### 7.2 Live surfaces that require replacement

| ID | Candidate and consumer evidence | Class | Required replacement/remediation |
|---|---|---|---|
| EX-R01 | Advanced template button, V2 template/parser, preview/import detection and media UI are live | `RETIRE_AFTER_REPLACEMENT` | Quick-only page plus canonical editor/preview parity gate |
| EX-R02 | Legacy workbooks are accepted by shared upload detection even though no legacy template is offered | `RETIRE_AFTER_REPLACEMENT` | stable retired-format response and canonical alternatives |
| EX-R03 | Excel media and Speaking staging placements/services/UI are live Advanced handoffs | `RETIRE_AFTER_REPLACEMENT` | canonical manual media and prompt-audio flows proven before deletion |
| EX-R04 | Advanced group image is flattened into each question by the candidate adapter, and non-Speaking question audio has no general canonical editor add/replace/remove control | `RETIRE_AFTER_REPLACEMENT` parity gap | prove canonical group-image semantics and add/normalize question-audio authoring before old media UI is removed |
| EX-R05 | Advanced `isManagedReference` accepts raw `http://`/`https://` media while canonical managed media uses exact private `/practice/materials/{id}/content` authority; external availability/ownership is not lifecycle-controlled | `RETIRE_AFTER_REPLACEMENT` ownership/availability gap | retained authoring uses owned managed uploads only; no external fallback or broken-link dependency |
| PDF-R01 | Advanced wizard, recent sessions, workspace and all session APIs are mutually live | `RETIRE_AFTER_REPLACEMENT` | Basic-only target-aware page; remove all links and handlers atomically |
| PDF-R02 | Basic PDF uses session row, PDF object and extraction rows | `RETIRE_AFTER_REPLACEMENT` for persistence only | bounded request-local Basic PDF processing |
| PDF-R03 | session/crop services and PDF-specific asset provenance are live Advanced dependencies | `RETIRE_AFTER_REPLACEMENT` | remove workspace producer first, then code, then forward schema |
| PDF-R04 | legacy request audit dual write is live for session generation | `RETIRE_AFTER_REPLACEMENT` | retained V84 purpose execution audit |
| PDF-R05 | `cleanupExpiredSessions()` is an hourly `@Scheduled` consumer of `findByExpiresAtBefore` and session/object/asset deletion | `RETIRE_AFTER_REPLACEMENT` | retire the scheduler with session persistence after request-local Basic cleanup is proven |
| UI-R01 | dashboard `/practice/manage/import` link has no target and becomes half-dead when Advanced is removed | `RETIRE_AFTER_REPLACEMENT` | remove/retarget or add authorized canonical target selection |
| UI-R02 | PDF workspace imports PDF.js 3.4.120 from a CDN and touches `pdfjsLib` during startup; unavailable script/network can break the workspace | `RETIRE_AFTER_REPLACEMENT` risk | workspace retirement removes the dependency; no new CDN is added |
| UI-R03 | `/practice/manage/upload` has no in-repository consumer but may have external bookmarks | `RETIRE_AFTER_REPLACEMENT` | route telemetry/compatibility decision and no live internal links |

The absence of a caller is not used to remove a security check from a retained
route. Every retained endpoint continues lecturer preauthorization plus exact
owner/draft/test/skill/lesson/asset authority.

### 7.3 Templates, JavaScript, CSS, assets and failure states

- Excel import and both PDF templates contain their feature-specific CSS and
  JavaScript inline. Removing a template requires removing its inline handlers,
  selectors and actions in the same slice; no legacy import bundle is separate.
- Every standalone file currently under `static/**/practice/**` has at least one
  source/test ownership reference. No standalone static asset is classified
  dead in CLEAN_CUT_1. Shared `app-shell.css`, `practice-index.css`,
  `practice-progress.css`, `manage-editor.css`, candidate review assets,
  canonical preview and authoring-contract scripts are `KEEP`.
- CLEAN_CUT_2 must remove Advanced Excel buttons, media inputs, columns,
  selectors and event handlers together. A hidden control, stale fetch or
  retained unsupported-format branch is a failure.
- CLEAN_CUT_3 must remove recent-session cards, workspace links, every
  session/region fetch, the broken rename control, PDF-only asset tab and CDN
  import together. A white screen, nonresponsive control, unhandled rejected
  fetch, stale spinner, 404/500 from visible UI or dangling navigation is a
  failure.
- The live hourly PDF expiry cleanup is part of the session/object graph, not
  dead code. Retirement must replace retained Basic PDF with request-scoped
  cleanup, remove the scheduler atomically with session persistence and never
  compensate by deleting a shared root or bucket.
- CLEAN_CUT_4 repeats template-to-route, route-to-template, selector-to-element,
  event-handler-to-control, asset-reference and static-bundle ownership scans.
  Only newly proven generic dead Practice code/UI/static is removed.

## 8. R/L/W/S replacement matrix and parity gate

The authoritative catalog permits exactly:

- Reading and Listening: `SINGLE_CHOICE`, `MULTIPLE_ANSWER`, `MATCHING`,
  `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN`;
- Writing: `ESSAY` with required Q51, Q52, Q53 and Q54 policies; and
- Speaking: `SPEAKING`.

| Skill/capability | Quick text-only retained path | Canonical editor/preview replacement | Gate before old format/UI removal |
|---|---|---|---|
| R/L single choice | supported | editor options/answer authority and learner preview supported | round trip Quick -> candidate review -> apply -> editor -> preview/publish |
| R/L multiple answer | supported | editor supports multiple correct keys and blocks fewer than two | same round trip with exact answer identity |
| R/L TFNG | supported | canonical type/scoring/preview supported | true/false/not-given round trip |
| R/L MATCHING | intentionally rejected to Advanced | editor provides targets A-H, option/match authority, validation and preview | create/edit/preview/publish matching without Advanced workbook |
| R/L simple blank | exactly one `{{blank:blank_1}}` | canonical blank editor/preview supported | Quick round trip |
| R/L complex blanks | intentionally rejected to Advanced | editor owns multiple stable blank IDs, prompt-token insertion, accepted values and duplicate/missing-token blockers | multi-blank edit/preview/publish proves full replacement |
| Reading media | Quick rejects media/layout | canonical group/question/option image upload/library, exact draft references and preview | owner-isolated upload/link/read/delete and all three rendered placements; dead section-only metadata is not carried forward |
| Listening media | Quick rejects media/layout | canonical group audio/listening check plus group/question/option images exist; a general non-Speaking question-audio editor control is still a replacement gap | exact owner/draft auth, add/replace/remove and preview for every retained group/question audio/image placement, or an explicitly tested lossless normalization of question audio to the canonical group model |
| Writing Q51/Q52 | exact two-blank response authority | canonical Writing task editor/validator/preview | both tasks preserve blank definitions, accepted answers and points |
| Writing Q53/Q54 | essay, no structured blanks | canonical Writing editor/validator/preview | task identity/order/10-10-30-50 scoring and publish round trip |
| Speaking manual text | supported, Korean text required | canonical prompt state/editor/preview supports manual text | Quick apply opens canonical state and publishes with required authority |
| Speaking prompt audio | Quick intentionally has no media; Advanced uses Excel staging | canonical original-audio upload/adoption, owner/draft/question authorization, prompt state and preview exist | prove canonical manual upload without Excel staging; then remove staging endpoint/placement/callout only |
| Candidate review/apply | supported | shared review, learner projection, stale-base conflict and atomic replay | unchanged for every retained type/source |

“Text-only Quick” is not permission to lose capabilities. Quick is the compact
bulk input path; canonical editor plus canonical preview is the required
replacement for media, MATCHING, multiple-answer editing, complex blanks and
all retained authoring types. Advanced/Legacy rejection may become permanent
only after all rows above are green.

## 9. KEEP / RETIRE / DROP_LATER contract freeze

### 9.1 KEEP

- Canonical draft, editor, target authorization, autosave, preview, validator,
  publisher, version/history and learner rendering/scoring/read compatibility.
- Persistent candidate/review/apply boundary and both V83 tables, all V83
  PK/UK/index/FK/check identities and historical source-kind values.
- Quick Excel template/preview/candidate route and Quick codec.
- Basic Text/PDF authoring route, strict JSON request/output contract,
  `PRACTICE_PDF_AUTHORING` purpose resolution, candidate assembler and V84
  execution audit.
- All four skill policies and all seven canonical question types.
- Shared lecturer assets, manual draft asset upload/library/link/access,
  material references, reference locks and durable lifecycle tasks.
- All six V84 purpose bindings and four V84 control-plane tables. Revision zero
  remains valid.
- All three V85 storage profile identities and exact-profile data-plane reads.
- Complete learner Speaking media/storage/lifecycle and prompt authoring, except
  the Excel-specific prompt staging handoff.
- Historical V1-V85 migration filenames and bytes. There are exactly 85 files,
  85 unique versions, minimum V1, maximum V85, no duplicate and no missing
  version at this checkpoint.
- All non-import content/result legacy readers; this program cannot remove them.
- `practice_storage_migration_jobs` physical table and its V85 constraints/index
  through CLEAN_CUT_6 as an empty rollback/read tombstone. No new job is seeded
  or processed. Its eventual removal requires a later rollback-window decision.

### 9.2 RETIRE

- Advanced Excel V2 template endpoint, codec, detection/parse/candidate producer,
  media UI and exact Excel-only material handoff.
- Legacy Excel V1 detection/read/build/candidate producer.
- Excel-specific Speaking staging placement, services, endpoint, editor callout
  and tests, but only after canonical prompt upload parity.
- Advanced PDF new-session, history, workspace, page/extraction, snapshot,
  region/crop, payload-preview and generation-claim surfaces.
- PDF session storage, PDF-specific crop/promotion asset methods and session asset
  query/update shape.
- Session-only legacy AI audit dual write; V84 audit remains.
- The proven-dead helpers/routes/DTO fields/handlers in Section 7.
- Authoring and Speaking legacy-null read branches/local adapters in CLEAN_CUT_4
  only after the refreshed zero-null preflight and rollback gate.
- Generic dead Practice code/UI/static found by the CLEAN_CUT_4 ownership refresh;
  no name-only or broad `legacy` match is sufficient evidence.

### 9.3 DROP_LATER by forward migration

CLEAN_CUT_4 may allocate only the first free unique migration version at or
above V86 after refreshing `main`, `origin/main`, the migration directory and
both checksum manifests. It never edits V1-V85.

After CLEAN_CUT_3 has removed every JPA mapping/SQL consumer and the exact
preflight is zero, the new migration drops in dependency order:

1. `practice_pdf_page_extractions` first because it owns
   `fk_page_extract_session`;
2. logical children `practice_pdf_region_annotations`,
   `practice_pdf_import_section_drafts`, `practice_pdf_import_group_drafts` and
   `practice_ai_request_audits`;
3. `practice_pdf_import_sessions`, which also removes its four indexes and
   `fk_pdf_session_storage_profile`; and
4. PDF-only `lecturer_assets` columns `source_import_session_id`,
   `source_region_id`, `source_page_number`, `crop_x`, `crop_y`, `crop_width`,
   `crop_height` and index `idx_lecturer_assets_session` after the entity/service
   fields are gone.

`lecturer_assets.source_type` is retained because `MANUAL_UPLOAD` and `AI_TTS`
remain authoritative; the obsolete `PDF_REGION` writer/default is removed or
changed to the canonical manual default only after a refreshed writer scan.
`storage_provider` columns and `practice_storage_migration_jobs` remain physical
rollback tombstones through CLEAN_CUT_6 and are `DROP_LATER` only after the
supported prior-runtime window closes.

The same migration may tighten `storage_profile_code` to non-null for retained
`lecturer_assets`, `practice_asset_lifecycle_tasks`, `practice_speaking_media`
and `practice_speaking_media_cleanup_tasks` only if every row is already exact-
profile-coded. There is no null-row backfill or object copy. Any unexpected row,
null identity, pending lifecycle/migration job or object inventory fails the
cutover; it is not permission to delete or rewrite shared state.

No migration deletes an object. Retired key families simply lose their runtime
writer/reader. Bucket/root cleanup is outside this program.

## 10. Forward-only rollback/read compatibility

The post-compaction rollback target is the audited CLEAN_CUT_3 runtime, not a
runtime that still exposes Advanced/Legacy importers.

Before CLEAN_CUT_4 schema drop, CLEAN_CUT_3 must:

1. contain no entity/repository/native-SQL startup dependency on the six PDF
   tables or PDF-only lecturer-asset columns;
2. remove the PDF-session subquery from Admin storage profile reference counts;
3. leave no route, template or static entry that can recreate PDF workspace
   state; and
4. keep exact-profile readers and make no automatic storage migration call.

The physical `practice_storage_migration_jobs` table and retained provider
columns stay through CLEAN_CUT_6 so the CLEAN_CUT_3 runtime can start and read on
the compacted schema. Rollback means deny new Quick/Basic candidate POSTs at the
authenticated edge, disable the affected exact purpose bindings/new R2 writes,
preserve schema/Flyway history/candidates/audits, and run the CLEAN_CUT_3 binary
read-only against canonical profile-coded data. It never reverse-migrates,
restores Advanced/Legacy UI, drops schema, deletes rows/objects or uses Flyway
`repair`/`clean`.

CLEAN_CUT_6 must prove both current-runtime reads and this immediate prior-
runtime read/start path on disposable catalogs. If CLEAN_CUT_3 is not tolerant
of the proposed schema, CLEAN_CUT_4 cannot drop that identity.

## 11. Frozen remaining slices on this worktree

### CLEAN_CUT_2 — Excel replacement and retirement

Entry gate: coordinator audited and accepted CLEAN_CUT_1, then explicitly kept
its sole report uncommitted so CLEAN_CUT_1 + CLEAN_CUT_2 can be audited and
committed together. The branch and baseline remained unchanged.

Work:

1. prove every R/L/W/S row in Section 8 using Quick plus canonical editor and
   preview, including media, MATCHING, multiple answer and complex blanks;
2. make Excel detection/import Quick-only with stable unsupported/retired-format
   failures and no Legacy/Advanced fallback;
3. remove Advanced template endpoint/codec, Legacy parser/build branches,
   Advanced/Legacy DTO/helpers and production-dead overloads/constructors;
4. reduce `excel-import.html` to Quick-only and remove orphan controls,
   handlers, selectors and media upload logic;
5. retire exact Excel media and Speaking staging seams only after canonical
   manual media/prompt-audio parity passes; and
6. retain candidate source kinds as read-only identities and run Excel,
   candidate, editor/preview/publisher, auth/ownership and static route/UI tests
   with real-call count zero.

Stop after coordinator audit. The coordinator alone may then create exactly one
local commit containing CLEAN_CUT_1 + CLEAN_CUT_2. Do not push, open a PR,
merge or begin CLEAN_CUT_3 in the same audit step.

### CLEAN_CUT_3 — PDF workspace retirement

Entry gate: CLEAN_CUT_1 + CLEAN_CUT_2 audited and committed together locally;
worktree clean.

Work:

1. replace Basic PDF session/object persistence with bounded request-local PDF
   extraction while preserving auth, page bounds, digest/evidence, strict AI
   contract, candidate boundary and guaranteed temporary cleanup;
2. retain Basic Text/PDF page and endpoint, `PRACTICE_PDF_AUTHORING`, candidate
   review/apply and V84 audit;
3. retire Advanced wizard/history/workspace/session/region/crop/snapshot/claim
   APIs, entities, repositories and services plus legacy request-audit dual write;
4. remove PDF-specific asset methods/fields/query shapes, broken rename, dead
   session asset tab, CDN PDF.js, every route consumer and the dashboard
   half-dead entry; keep shared material library/manual assets;
5. remove the PDF-session Admin reference-count query and all mappings to schema
   identities scheduled for CLEAN_CUT_4; and
6. run Basic PDF/Text, candidate, editor/preview/publish, auth/ownership,
   route/template/JS/static and no-real-call regression.

Stop at the completed CLEAN_CUT_3 checkpoint for coordinator audit. The
coordinator alone may then create exactly one local CLEAN_CUT_3 commit. Do not
operate on a shared database and do not begin CLEAN_CUT_4 in the same audit step.

### CLEAN_CUT_4 — Storage/schema compaction and residual ownership cleanup

Entry gate: CLEAN_CUT_3 audited/committed locally; worktree clean; refresh this
entire inventory and migration maximum before naming a migration.

Work:

1. repeat code/entity/repository/native SQL/Admin/template/static/object-key and
   null-profile scans; fail on any revived consumer or unknown identity;
2. add one or more forward-only, unique migrations beginning at the first free
   version `V86+`; never modify a historical migration or checksum;
3. on new disposable catalogs only, assert no row/object needs preservation,
   no PDF graph row exists, all retained storage rows are exact-profile-coded,
   and lifecycle/migration jobs are empty before destructive DDL;
4. drop the frozen PDF graph and provenance identities in Section 9.3, remove
   legacy-null runtime branches/adapters where the exact-profile gate passes,
   and retain the rollback tombstones named there;
5. remove remaining generic `PROVEN_DEAD` Practice code/UI/static only with
   exact consumer/ownership evidence; and
6. validate fresh and V85-upgrade migration, Hibernate/current runtime, the
   CLEAN_CUT_3 rollback-read runtime, exact profiles/FKs and zero provider calls.

No row-copy/preservation job is created, seeded or run. No shared database,
bucket or upload root is modified or deleted.

Stop after coordinator audit and exactly one local CLEAN_CUT_4 commit. Do not
push or begin CLEAN_CUT_5 in the same audit step.

### CLEAN_CUT_5 — Admin settings IA and visual UI/UX redesign

Entry gate: CLEAN_CUT_4 audited/committed locally; worktree clean.

The user has inserted this slice after C4. Its detailed implementation scope is
coordinator-owned and intentionally not inferred here. The C4 compatibility
freeze is that `/admin/settings/storage` and its shared `GENERAL_UPLOADS`
control-plane ownership remain operational; C4 performs no Admin IA or visual
redesign. C5 must receive its explicit scope before work begins and must return
to its own coordinator audit/commit checkpoint without opening C6.

### CLEAN_CUT_6 — Consolidated validation

Entry gate: CLEAN_CUT_5 audited/committed locally; worktree clean.

Required consolidated evidence:

1. fresh disposable V1-to-current migration, checksum/continuity and Hibernate
   validation;
2. separate disposable V85-to-current upgrade with the exact preflight and no
   Flyway `repair`, `clean`, reverse SQL or reused catalog;
3. current-runtime read/write regression plus CLEAN_CUT_3 prior-runtime
   rollback/read/start compatibility under denied import entries;
4. full Practice authoring, candidate, editor, preview, publisher, immutable
   version, learner R/L/W/S and legacy-read regression;
5. lecturer auth, cross-owner denial, exact target/asset/session absence,
   FK/object graph and stale/replay behavior;
6. bidirectional route/template/fragment/selector/handler/bundle/static ownership
   scans proving no visible control calls a retired endpoint and no retired UI
   is navigable;
7. exact six AI purposes, three storage profiles, valid binding revision zero,
   separate Practice data planes and no fallback; and
8. real-call counters `AI/R2/STT/TTS = 0/0/0/0` plus explicit confirmation that
   Phase 14/15/Pre-15, direct-audio/acoustic and provider links remain absent.

Only after this evidence is consolidated and CLEAN_CUT_6 has one audited local
commit may the coordinator push the full commit chain and create/merge one PR.

## 12. CLEAN_CUT_1 exit evidence (accepted by coordinator)

- Baseline and clean-start checks passed.
- All six named authority documents were read in full.
- Excel V1/V2/Quick, PDF Basic/Advanced, route/controller/service/entity/
  repository/template/inline JS/CSS/static/table/FK/index/object-key and
  legacy-null ownership graphs were inventoried.
- Dead candidates were separated from live replacement-bound surfaces and
  canonical KEEP contracts.
- R/L/W/S replacement gates and forward-only schema/rollback boundaries are
  frozen.
- Migration scan is
  `85 files / 85 unique versions / min V1 / max V85 / 0 missing / 0 duplicates`.
- No production code, test, migration or schema was changed. No database or
  provider was contacted.
- The only worktree file created by CLEAN_CUT_1 was this live report.
- At that checkpoint `CLEAN_CUT_2` had not started. The coordinator subsequently
  accepted CLEAN_CUT_1 and explicitly authorized CLEAN_CUT_2 without first
  committing the report.

## 13. CLEAN_CUT_2 implementation and exit evidence

### 13.1 Start checkpoint

Before production edits, the following state was re-verified:

| Check | Observed value | Result |
|---|---|---|
| branch | `codex/practice-clean-cut-legacy-retirement-schema-compaction` | PASS |
| `HEAD` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| local `main` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| `origin/main` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| initial worktree delta | only this untracked CLEAN_CUT_1 report | PASS |

No task, worktree or branch was created. No commit, push, PR or merge was made.
No database, bucket, upload root or provider endpoint was contacted.

### 13.2 Replacement/parity disposition

| Authority | Replacement proven before retirement | C2 result |
|---|---|---|
| R/L text-only | Quick v1 retains exact `SINGLE_CHOICE`, `MULTIPLE_ANSWER`, `TRUE_FALSE_NOT_GIVEN` and one-token simple `FILL_BLANK`; target skill, package, points and typed candidate contracts remain fail-closed | PASS |
| R/L advanced types | canonical Editor already owns `MATCHING`, multiple-answer option authority and complex blank placement/accepted values; shared preview renders the same typed contracts | PASS |
| group audio | canonical Listening group control writes `stimulus.mediaReference`; the Advanced adapter that could flatten group media into question media is removed | PASS |
| group image | canonical group control writes `stimulus.imageReference`; no Excel normalization/flattening path remains | PASS |
| question image | canonical question control writes `questionContent.imageReference` and preview consumes it | PASS |
| question audio | new Listening-only question control uploads, previews, removes and writes `questionContent.audioReference`; it is explicitly separate from group audio | PASS |
| option image | retained canonical option-image controls and preview path; no Excel media override remains | PASS |
| Writing | Quick simulation still requires exactly Q51/Q52/Q53/Q54 with points 10/10/30/50; Q51/Q52 blank authority and Q53/Q54 essay authority remain typed and publisher-validated | PASS |
| Speaking text | Quick remains manual Korean prompt text only | PASS |
| Speaking original audio | canonical original-audio upload, ownership, verification, revision/stale rules and optional explicit STT/TTS authoring remain; Excel staging/adoption is removed | PASS |

Quick rejects media, `MATCHING`, complex blanks and other non-Quick authoring
with stable code `CANONICAL_EDITOR_REQUIRED` and an Editor handoff message. It
does not silently discard or normalize those values.

### 13.3 Excel write surface after clean cut

The authenticated lecturer controller now exposes exactly:

1. `GET /practice/manage/excel` for the exact draft/Test/skill/lesson target;
2. `GET /practice/manage/excel/template/quick-v1`;
3. `POST /practice/manage/excel/preview`; and
4. `POST /practice/manage/excel/import` to create/reuse a persistent candidate.

`GET /practice/manage/excel/template` is removed. Import accepts no
`mediaOverrides`. Detection has no parser fallback:

| Workbook identity | Stable result |
|---|---|
| Quick v1 | parsed and validated; candidate source kind is only `QUICK_EXCEL` |
| Advanced v2 marker `01_THONG_TIN_SET` | `ADVANCED_EXCEL_V2_RETIRED` |
| exact Legacy V1 five-sheet identity | `LEGACY_EXCEL_V1_RETIRED` |
| anything else | `WORKBOOK_SCHEMA_UNSUPPORTED` |

V83 persisted identities `QUICK_EXCEL`, `ADVANCED_EXCEL_V2` and
`LEGACY_EXCEL_V1` were not renamed or removed. Candidate review/apply, exact
target authority, owner authorization, base-draft stale detection, content
digest/reuse, replay ledger and publisher validation remain shared and
unchanged. Old candidates therefore remain readable/applyable under the V83
contract even though no new Advanced/Legacy upload can be created.

### 13.4 Retired dead surface

The following production seams were removed only after Section 13.2 passed:

- `PracticeAssessmentExcelV2Codec` and the Advanced template endpoint;
- Legacy manifest/section/group/question/answer readers, builders, issue
  pruning and Advanced/Legacy candidate adapters;
- tests-only context-free preview overload and compatibility constructors;
- Excel `mediaOverrides`, media-reference replacement, generic Excel asset
  linking and verified-Excel-audio helpers;
- `SPEAKING_PROMPT_EXCEL_STAGING`, its state projection, lifecycle branch,
  adopt route/coordinator/service methods, callout, selector, handler and JS
  export;
- the old mixed-format Excel template controls, 20-column detail table,
  filters/view switch, media picker, object URLs and upload handlers.

`excel-import.html` is now a compact Quick-only surface. Its controls have a
one-to-one owner in the inline script; the only fetch targets are the retained
`preview` and `import` routes, and the only template link is the retained Quick
template. Editor navigation continues to provide all four exact route target
parameters. Static contract tests check both positive ownership and absence of
retired endpoints/selectors/handlers.

### 13.5 Validation evidence

- `bash ./mvnw -q -Denforcer.skip=true -DskipTests compile`: PASS. The host has
  only JDK 26 while the repository enforcer requires JDK 17, so the enforcer was
  skipped solely to compile/test with source settings unchanged; no toolchain
  file was modified.
- focused Excel/Quick/candidate/editor-preview/publisher/Speaking/auth/static
  suite: PASS, `22` test classes / `172` tests / `0` failures / `0` errors.
- `PracticePhase11AuthoringUiContractTest#excelImportSurfaceIncludesRowPreviewAndCandidateHandoff`:
  PASS.
- `git diff --check`: PASS.
- production scans: zero references to the deleted V2 codec, Excel media
  overrides, Excel Speaking staging route/state/handler or generic Excel media
  linker. The remaining `ADVANCED_EXCEL_V2`/`LEGACY_EXCEL_V1` names are the
  intentionally retained V83 read identities and deterministic retirement
  recognition.
- a full unconfigured `mvn test` audit reached `2970` tests but is not a green
  gate: `1042` errors are dominated by the repository-wide disposable-database
  guard rejecting missing `TEST_DB_URL`; unrelated existing static/migration
  fixtures also report `8` failures. No database connection was attempted after
  the guard failed. Focused C2 suites are the authoritative result for this
  slice; disposable-catalog consolidated regression remains CLEAN_CUT_6 work.
- real external calls during C2 validation:
  `AI/R2/STT/TTS = 0/0/0/0`.

### 13.6 CLEAN_CUT_2 checkpoint

`CLEAN_CUT_2_EXCEL_REPLACEMENT_AND_RETIREMENT` is complete and accepted by the
coordinator. At that C2 checkpoint `CLEAN_CUT_3` had not started. There is no
migration or schema change in this slice, and no shared database or object
storage operation was performed. The coordinator alone may commit the uncommitted CLEAN_CUT_1 report
and all CLEAN_CUT_2 changes together after acceptance; this task must not
commit, push, open a PR, merge or begin PDF retirement.

### 13.7 Coordinator acceptance evidence

The coordinator independently audited the complete CLEAN_CUT_1 + CLEAN_CUT_2
working tree before the local commit:

- `git diff --check`: PASS;
- JDK 17 focused union rerun: `189/189`, `0` failures, `0` errors;
- a wider `208`-test probe had exactly three pre-existing failures in the
  Result/PDF/icon portions of `PracticePhase11AuthoringUiContractTest`; direct
  baseline-source comparison confirmed that C2 did not change their failing
  inputs, while the Excel-owned method passed independently;
- production reference scans found no remaining consumer of the removed V2
  codec, Advanced template route, Legacy parser, Excel media overrides or
  Speaking Excel staging endpoint/state/handler;
- no migration, schema or application configuration file changed; and
- no database, object store or real AI/R2/STT/TTS provider was contacted.

Verdict: `CLEAN_CUT_1_AND_CLEAN_CUT_2_ACCEPTED_FOR_ONE_LOCAL_COMMIT`.
`CLEAN_CUT_3` was held until that commit existed.

The coordinator then created that one local commit:
`d02be7843c8fd77a72600121f8bc4f1e31a9ae76 refactor(practice): retire legacy
Excel authoring paths`. It was not pushed, merged or used to open a PR.

## 14. CLEAN_CUT_3 implementation and exit evidence

### 14.1 Start checkpoint

The PDF slice began only after the coordinator-owned C1+C2 commit existed:

| Check | Observed value | Result |
|---|---|---|
| branch | `codex/practice-clean-cut-legacy-retirement-schema-compaction` | PASS |
| `HEAD` | `d02be7843c8fd77a72600121f8bc4f1e31a9ae76` | PASS |
| worktree before C3 edits | clean | PASS |
| local `main` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |
| `origin/main` | `0361372f44843e4410602e1482c05c6f5dedef49` | PASS |

This task did not create a task, worktree or branch and did not commit, push,
open a PR or merge. No shared database, shared bucket or shared upload root was
read, scanned, mutated or deleted.

### 14.2 Request-local Basic Text/PDF replacement

The retained surface is one target-aware page opened only from a selected
canonical draft section:

```text
editor exact draft/section action
  -> GET /practice/manage/import?draftId&testNo&skill&lessonCode
  -> exact EDIT authorization + target resolution
  -> bounded Text or in-memory PDF extraction
  -> immutable PRACTICE_PDF_AUTHORING request snapshot
  -> purpose-bound structured generation + V84 execution audit
  -> independent strict output validation
  -> persistent PDF_AI candidate only
  -> review -> explicit apply -> canonical draft
```

The replacement preserves the following contracts:

- `PracticeImportTargetService` requires exact draft `EDIT` authority and
  resolves the target only from the authorized draft snapshot. Candidate POST
  rechecks exact `draftId/testNo/skill/lessonCode`; mismatches fail closed.
- Text is normalized and bounded. PDF requires a non-empty `.pdf` upload with
  exact `application/pdf`, at most `20 MiB`, a `%PDF-` header, a valid PDFBox
  document, a valid page interval, the configured selected-page ceiling and
  the configured total character ceiling.
- The raw PDF SHA-256 digest, selected page evidence, evidence digest, page
  numbers, source metadata and target are carried in the immutable request
  context. The live source-ref vocabulary is now exactly `TEXT_SPAN`/`PAGE`;
  the unreachable Advanced `REGION` vocabulary is removed. Source text remains
  untrusted data, never an instruction.
- The upload stream and PDF document are closed by try-with-resources. No
  application temp file or storage object is created. The bounded byte buffer
  is zeroed in `finally`, including success, parse/contract error and request
  cancellation unwinding; there is no root/bucket scan or delete path.
- The request remains exact-purpose `PRACTICE_PDF_AUTHORING`. Binding revision
  `0` is accepted; negative/unavailable identities fail closed; provider/profile/
  model authority is rechecked after generation.
- Provider JSON Schema is supplied to the structured-generation port and
  `PracticePdfAuthoringOutputValidator` independently rejects unknown fields,
  forbidden evaluation data, wrong skill/type, wrong digest/operation,
  unrequested evidence and invalid typed answer/Writing/Speaking authority.
- `PracticePdfAuthoringCandidateAssembler` is the sole persistence step and
  writes only a `PDF_AI` authoring candidate. The controller has no
  `PracticeDraftRepository`, draft JSON setter or direct draft write. Review,
  stale/replay checks, explicit apply and publish remain canonical.
- The V84 purpose execution audit remains owned by
  `PracticeControlPlaneStructuredGenerationAdapter` for start/success/failure/
  cancellation. The older PDF-session `PracticeAiRequestAudit` dual write is
  removed.

### 14.3 Retired Advanced/session runtime graph

The entire Advanced PDF workspace production graph was removed:

- workspace/history/session/page-range/save/cancel/delete/extracted-text/
  annotation/payload-preview/generate routes and their DTOs;
- `import-workspace.html`, recent-session cards, broken rename, PDF.js authoring
  CDN, region/crop/page snapshot UI, session-only asset tab and every associated
  selector/handler;
- session, page-extraction, region-annotation, temporary section/group draft
  and legacy request-audit entities and repositories;
- session storage, extraction, preview, crop, region, payload-preview,
  snapshot, generation claim/lease, scheduler cleanup and legacy audit services;
- old unused `PracticePdfDraftView`, `PracticePdfImportResult`,
  `PracticeDraftGroup` and `PracticeDraftQuestion` DTOs/imports; and
- Advanced-only PDF config keys for regions, rendered pixels, image byte
  budgets and generation leases.

Production source scans now have zero mapping/query/reference to
`practice_pdf_import_sessions`, `practice_pdf_page_extractions`,
`practice_pdf_region_annotations`, `practice_pdf_import_section_drafts`,
`practice_pdf_import_group_drafts`, `practice_ai_request_audits`, their retired
entity types or PDF provenance columns. Historical V1-V85 migration bytes
remain unchanged. The V85 `PDF_IMPORT_SESSION` migration-job enum identity stays
readable as a rollback tombstone, but every execution port rejects it with
`PDF_IMPORT_SESSION_MIGRATION_RETIRED` and contains no PDF session table/object
operation.

`LecturerAsset` no longer maps the session/region/page/crop provenance columns.
PDF-session repository/query/update/promote/unlink and migration-object shapes
are gone. The reduced shared asset controller retains only authenticated,
owner-filtered asset list/delete and exact authorized draft link. Manual
uploads, private material content, material library lifecycle and canonical
draft references remain active.

### 14.4 Route/UI/static and Admin disposition

| Surface after C3 | Disposition/evidence |
|---|---|
| `GET /practice/manage/import` | KEEP; requires exact `draftId/testNo/skill/lessonCode`, never falls back to another section, and renders the Basic page; stale targets return stable 400 |
| `POST /practice/manage/pdf-authoring/candidates` | KEEP; exact target recheck, Text/PDF-only, candidate response/review URL only |
| `GET /practice/manage/assets` | KEEP; canonical editor consumer |
| `DELETE /practice/manage/assets/{assetId}` | KEEP; canonical editor/library lifecycle consumer and owner check |
| `POST /practice/manage/drafts/{draftId}/assets` | KEEP; canonical editor asset link with draft/asset authorization |
| `GET /practice/manage/upload` | RETIRE; the targetless compatibility redirect was removed and now resolves as 404 |
| all `/practice/manage/import-sessions/**` and workspace routes | RETIRE; no controller mapping or production consumer remains |

The dashboard targetless PDF card is removed. The editor Text/PDF action is
hidden until an exact section is selected and then receives the exact draft,
test, skill and lesson context. Production Practice scans have zero Advanced workspace,
recent-session, targetless redirect, PDF.js authoring CDN, stale fetch, dead
session tab or retired selector/handler reference. Unrelated learner PDF viewer
and non-Practice Font Awesome CDN consumers are outside this ownership graph and
remain untouched.

`StorageProfileAdminService.referenceCount` no longer queries PDF sessions.
Storage migration identity/port code can deserialize the historical PDF-session
logical type but cannot query, copy, update or delete a PDF session row/object.
Practice AI/R2 remain separate data planes; Admin remains the control plane.

### 14.5 Schema and rollback/read freeze

No migration, schema or V1-V85 source was added, removed or edited. The physical
PDF tables, FKs, indexes and `lecturer_assets` provenance columns frozen in
Section 9.3 therefore still exist for the moment. The C3 runtime deliberately
does not map or query any of them, which is the required rollback/read boundary
before a C4 forward migration may remove them. V83 `PDF_AI` candidates and V84
purpose execution audits remain readable; there is no row/object preservation
or copy plan because the authorized environment contains only disposable test
data.

C4 still must refresh the full inventory, migration maximum and disposable-
catalog preflight before naming any `V86+` migration. This C3 checkpoint does
not authorize a shared-database operation or any schema drop.

### 14.6 Validation evidence

- `bash ./mvnw -q -Denforcer.skip=true -DskipTests test-compile`: PASS. The
  current host has JDK 26 while the repository enforcer requires JDK 17; source
  and repository toolchain configuration were not changed.
- focused request-local builder, target/auth, controller, orchestrator/binding,
  strict output, candidate, asset ownership, Admin count and static compatibility
  plus retained storage-migration rollback contracts: PASS, `16` classes /
  `83` tests / `0` failures / `0` errors.
- three touched PDF/editor methods in
  `PracticePhase11AuthoringUiContractTest`: PASS, `3/3`.
- the complete `PracticePhase11AuthoringUiContractTest` probe ran `20` tests
  with exactly one failure: the coordinator-confirmed pre-existing text-glyph
  arrow in `practice/result-detail-writing.html`. That file has zero diff from
  `d02be784`; all C3-owned PDF/editor methods above pass independently.
- `git diff --check`: PASS.
- production runtime retired table/column/entity scan: zero matches outside
  immutable migrations; the only retained PDF migration-job type is the
  fail-closed V85 tombstone above. Practice route/template/JS/static retirement
  scan: zero matches.
- a DB-backed integration probe is intentionally not treated as runnable in
  this unconfigured worktree: the repository-wide guard rejects missing
  `TEST_DB_URL` before any connection. The changed integration fixture now opens
  Basic import with an owned exact target and expects the retired targetless
  bookmark to return 404. Disposable fresh/upgrade/rollback catalogs remain C6
  consolidated work.
- real external calls during implementation and validation:
  `AI/R2/STT/TTS = 0/0/0/0`.

### 14.7 CLEAN_CUT_3 checkpoint

`CLEAN_CUT_3_PDF_WORKSPACE_RETIREMENT` is implemented and ready for coordinator
audit. `HEAD` remains the accepted C1+C2 commit; all C3 changes, including this
report update, are intentionally uncommitted. No shared database or object
storage was touched, and no real provider call occurred.

`CLEAN_CUT_4` has not started. The coordinator alone may create one local C3
commit after acceptance; this task must not commit, push, open a PR, merge or
begin storage/schema compaction.

### 14.8 Coordinator acceptance evidence

The coordinator independently audited the complete CLEAN_CUT_3 working tree
before the local commit:

- `git diff --check`: PASS;
- JDK 17 focused request-local PDF/target/auth/controller/orchestrator/strict
  output/candidate/asset/Admin/storage-migration/static union: `16` suites,
  `83/83`, `0` failures, `0` errors;
- the three touched editor/PDF/icon contract methods in
  `PracticePhase11AuthoringUiContractTest`: `3/3`, `0` failures, `0` errors;
- production scans found no mapping, query or runtime reference to the six
  retired PDF tables/entities outside immutable migrations; the V85
  `PDF_IMPORT_SESSION` logical identity remains only as a readable fail-closed
  tombstone;
- route/template/static scans found no retained Advanced workspace/session,
  targetless redirect, PDF.js authoring or retired selector/handler consumer;
- no migration, schema or historical V1-V85 file changed; and
- no database, object store or real AI/R2/STT/TTS provider was contacted.

Verdict: `CLEAN_CUT_3_ACCEPTED_FOR_ONE_LOCAL_COMMIT`. The commit is coordinator
owned, remains local and does not authorize a push, PR or merge.

The coordinator-created local commit is
`a5623b47a21c42458fcf8349338a38fd827edae1` (`refactor(practice): retire PDF
workspace sessions`). It was the clean C4 entry point and remains unpushed.

## 15. CLEAN_CUT_4 storage and schema compaction checkpoint

### 15.1 Entry checkpoint and refreshed ownership inventory

Before C4 changes, the worktree was clean on
`codex/practice-clean-cut-legacy-retirement-schema-compaction` at exact `HEAD`
`a5623b47a21c42458fcf8349338a38fd827edae1`. Local and remote main remained the
program baseline `0361372f44843e4410602e1482c05c6f5dedef49`. The refreshed
migration inventory was `85 files / 85 unique versions / V1..V85 / no gap / no
duplicate`; both historical checksum manifests still end at V85 and no V1-V85
byte was changed.

The runtime/entity/repository/native-SQL/Admin/static/object-key refresh proved:

- the six C3-retired PDF workspace tables and seven lecturer-asset provenance
  columns have no current runtime mapping/query/writer;
- the retained authoring and Speaking data planes already have exact
  `PRACTICE_AUTHORING` and `PRACTICE_SPEAKING` replacements, while nullable
  provider/key-only and local-root adapters were compatibility-only;
- `GENERAL_UPLOADS`, `SystemSettingGroups.STORAGE`, `ObjectStorageConfig`,
  `DualReadObjectStorage` and `GeneralUploadsObjectStorage` still have
  non-Practice consumers or bounded pre-AIM6 read-compatibility ownership and
  therefore remain `KEEP`;
- `/admin/settings/storage` remains the compatibility route for the inserted C5
  Admin slice; C4 does not redesign it; and
- no additional generic Practice UI/static surface met the exact dead-owner
  threshold. The only generic dead production removals are the two Practice-
  local storage adapters whose callers were fully replaced.

### 15.2 Forward migration V86

The first free version is
`V86__practice_legacy_import_schema_compaction.sql`. It is clean-cut and has no
row copy, backfill, object scan or object delete. A retry-safe stored-procedure
preflight runs before destructive DDL and signals one of four stable failures:

| Guard | Required zero/exact state | Stable failure |
|---|---|---|
| retired PDF workspace | all six tables empty | `C4_PDF_WORKSPACE_ROWS_MUST_BE_EMPTY` |
| lecturer PDF provenance | all seven columns null and no `PDF_REGION` row | `C4_PDF_PROVENANCE_MUST_BE_EMPTY` |
| retained asset/media identity | no null profile in lecturer assets or Speaking media | `C4_RETAINED_STORAGE_PROFILE_REQUIRED` |
| storage work queues | authoring lifecycle, Speaking cleanup and migration-job tables empty | `C4_STORAGE_WORK_QUEUES_MUST_BE_EMPTY` |

After the guard passes, V86 performs this exact dependency-ordered DDL:

1. drop `fk_page_extract_session` and `idx_page_extract_session_page`, then
   `practice_pdf_page_extractions`;
2. drop the three `idx_pdf_region_session_*` indexes, then
   `practice_pdf_region_annotations`;
3. drop `idx_pdf_section_draft_session` and
   `practice_pdf_import_section_drafts`;
4. drop `idx_pdf_group_draft_session` and
   `practice_pdf_import_group_drafts`;
5. drop `idx_ai_audit_session` and `practice_ai_request_audits`;
6. drop `fk_pdf_session_storage_profile`, `idx_pdf_session_uploader`,
   `idx_pdf_session_target`, `idx_pdf_session_generation_lease` and
   `idx_pdf_session_profile_path`, then `practice_pdf_import_sessions`;
7. drop `idx_lecturer_assets_session` and lecturer-asset columns
   `source_import_session_id`, `source_region_id`, `source_page_number`,
   `crop_x`, `crop_y`, `crop_width`, `crop_height`; change the retained
   `source_type` default to `MANUAL_UPLOAD`; and make
   `lecturer_assets.storage_profile_code` non-null;
8. make `practice_asset_lifecycle_tasks.storage_profile_code`,
   `practice_speaking_media.storage_profile_code` and
   `practice_speaking_media_cleanup_tasks.storage_profile_code` non-null; and
9. drop provider/key-only uniques `uk_psm_storage` and
   `uk_psm_cleanup_storage`, retaining exact-profile uniques
   `uk_psm_profile_storage` and `uk_psm_cleanup_profile_storage`.

V86 deliberately retains V83 `practice_authoring_candidates`, V84
`practice_ai_execution_audits`, all six `PracticeAiPurpose` identities, V85
`practice_ai_purpose_bindings`, all three `storage_profiles`,
`practice_storage_migration_jobs`, canonical lecturer/material assets and the
complete Speaking media/cleanup graph. Provider columns remain evidence;
profile code is the dispatch authority. Storage profile revision `0` and AI
binding revision `0` remain valid.

### 15.3 Exact-profile runtime cut

Practice authoring now accepts only exact `PRACTICE_AUTHORING` operations under
the `lecturer-assets/` key family. Practice Speaking write, promotion, open,
existence, delete, playback, transcription, discard and durable cleanup accept
only exact `PRACTICE_SPEAKING`; no path falls back to `GENERAL_UPLOADS`, the old
public upload root or the old private Speaking root. Missing/wrong profiles and
unsafe prefixes fail closed.

The nullable/default overloads, compatibility record constructors, provider-
key repository methods and null-profile lifecycle locks were removed. The
production-dead `LocalAssetStorageService` and
`LocalPrivateSpeakingAudioStorage` plus their owned tests were deleted.
Profiled adapters now depend only on `StorageProfileObjectStore`. Speaking
upload persists an unreferenced temporary identity, promotes the exact-profile
object, then explicitly activates it; compensation and deletion keep a durable
exact-profile cleanup intent. The exact cleanup upsert retains reason priority,
retry/backoff and idempotence, and dispatches by profile even when the retained
provider value records an object-storage backend.

Historical `PDF_IMPORT_SESSION` migration-job values remain readable but the
execution port returns `PDF_IMPORT_SESSION_MIGRATION_RETIRED`; null source
profiles fail with `STORAGE_MIGRATION_SOURCE_PROFILE_REQUIRED`. No automatic
migration runner, shared bucket scan or shared-root deletion was introduced.

### 15.4 Disposable migration and rollback/read evidence

Validation used a new isolated Docker network/container and three uniquely
named catalogs only:

- `ksh_test_c4fresh_19b6`: fresh Flyway V1 through V86 and current Hibernate
  validation passed; schema history is exactly `86`, max `86`;
- `ksh_test_c4upgrade_19b6`: Flyway stopped at exact V85 (`85/85`), zero-state
  preflight was independently queried, V86 applied as one forward migration,
  and current Hibernate validation passed;
- `ksh_test_c4guard_19b6`: one intentionally seeded disposable PDF session made
  V86 fail with exit `1`, SQLSTATE `45000` and exact
  `C4_PDF_WORKSPACE_ROWS_MUST_BE_EMPTY`; successful history remained max V85,
  the session table/row and all seven provenance columns remained intact.

Post-V86 catalog inspection found zero retired tables, zero retired provenance
columns, all four retained profile columns `NOT NULL`, all eight named retained
tables present, both legacy Speaking uniques absent, lecturer asset default
`MANUAL_UPLOAD`, and exactly the three profiles
`GENERAL_UPLOADS/PRACTICE_AUTHORING/PRACTICE_SPEAKING`.

The exact C3 source snapshot at `a5623b47` started against the V86 upgrade
catalog with Flyway disabled and Hibernate validation enabled. Its denied-route
rollback/read probe passed `2/2`: both retired/gated Speaking media endpoints
remain 404 while current canonical schema is readable. No reverse SQL, Flyway
repair/clean or reused/shared catalog was used.

### 15.5 JDK17 regression evidence

- JDK17 production plus all `430` test sources: `test-compile` PASS.
- exact-profile adapters, authoring/Speaking lifecycle, promotion/compensation,
  playback/transcription, migration tombstone and V86 static contracts:
  `12` suites / `77` tests / `0` failures / `0` errors / `0` skipped.
- DB-backed Speaking media and durable cleanup: `2` suites / `50` tests / `0`
  failures / `0` errors; this includes FK/unique, transaction boundary,
  ownership, stale/retry/idempotence and exact-profile dispatch coverage.
- fresh storage-profile persistence: `1/1`; explicitly enabled AIM-8 integrated
  candidate/six-purpose/revision-zero/storage contract: `1/1`.
- C3 rollback/read binary: `2/2`.
- V86 fresh, V85 upgrade, negative guard, migration continuity and current
  Hibernate validation: PASS.
- real external calls throughout C4 implementation and validation:
  `AI/R2/STT/TTS = 0/0/0/0`.

### 15.6 CLEAN_CUT_4 checkpoint

`CLEAN_CUT_4_STORAGE_AND_SCHEMA_COMPACTION` is implemented and ready for
coordinator audit. `HEAD` remains `a5623b47a21c42458fcf8349338a38fd827edae1`;
all C4 source, tests, V86 and this one live report are intentionally uncommitted.
Historical V1-V85 bytes/checksum manifests are unchanged. No shared database,
bucket or upload root was touched, and no real provider call occurred.

The disposable C4 container/catalogs, isolated network and C3 filesystem
snapshot were removed after evidence capture. C5 has not started. Only the
coordinator may audit and create the one local C4 commit; this task must not
commit, push, open a PR, merge or infer the pending C5 Admin redesign scope.

### 15.7 Coordinator acceptance evidence

The coordinator independently audited the complete C4 delta at clean entry
`a5623b47a21c42458fcf8349338a38fd827edae1`: `55` paths, `757` insertions and
`1897` deletions before this acceptance note. Historical V1-V85 migration files
remain unchanged; V86 is the only new migration. The dependency order,
fail-before-DDL preflight, retained table/profile/purpose ownership, exact
Practice profile enforcement, rollback tombstone and deletion of the two
proven-dead local adapters were reviewed against the refreshed inventory.

Coordinator reruns with JDK 17 produced:

- full production and all test-source compilation: PASS;
- the exact C4 focused static/unit selection: `12` suites / `77` tests / `0`
  failures / `0` errors;
- `git diff --check`: PASS; and
- source scans found no retained runtime caller for the removed nullable/local
  Practice storage contracts and no newly introduced global fallback.

The coordinator also accepted the isolated disposable-catalog evidence recorded
in Sections 15.4-15.5: fresh V1-to-V86, V85-to-V86 upgrade, negative SQLSTATE
`45000` preflight with V85 left intact, current Hibernate validation and C3
read-only startup/probes against V86. No shared database or object store was
used, and real external calls remain `AI/R2/STT/TTS = 0/0/0/0`.

Verdict: `CLEAN_CUT_4_ACCEPTED_FOR_ONE_LOCAL_COMMIT`. The commit is coordinator
owned, remains local and does not authorize a push, PR or merge.
