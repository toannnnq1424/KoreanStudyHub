# Practice Post-Pre-14 Authoring Import Modernization Roadmap Amendment

Recorded: `2026-08-02`

Status: `AIM_2_CANDIDATE_FOUNDATION_COORDINATOR_AUDIT_GREEN`

Epic: `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION`

## 1. Baseline and authority

This amendment was prepared only after fetching `origin/main` again. The
verified baseline is:

```text
HEAD:        3dfab18ae308005c018c9066256bb7c79b686e8e
origin/main: 3dfab18ae308005c018c9066256bb7c79b686e8e
PR:          #55
merge:       Merge pull request #55 from
             toannnnq1424/feature/practice-pre14-production-correctness-gate
```

Both the PR #55 merge commit and refreshed `origin/main` are ancestors of the
working snapshot. No stale local `main` checkout was used.

AIM-2 started from the later exact PR #56 merge commit
`813a54d7c5d2a02cc4cc1bf2213085db3907dfea`. Before any AIM-2 edit, detached
`HEAD` and refreshed `origin/main` were both verified at that SHA with a clean
working tree. PR #56 contains the merged AIM-0/AIM-1 contract freeze; this
newer baseline supersedes the preparation SHA above for implementation work.

The final Pre-14 evidence is accepted as one closed gate:

- the final live-log checkpoint has direct proof for all `48/48` master rows;
- the matrix has `0 MISSING`, `12 MATCH` and `36 PARTIAL` rows;
- `PARTIAL` records honest KSH-native taxonomy/content/visual differences or
  unavailable acoustic authority, not missing proof or a fabricated state;
- the four acoustic rows remain deliberately fail closed with zero inferred
  clips, phonemes, stress rows or acoustic transcript spans; and
- PR #55 merged that accepted branch into `origin/main` at the SHA above.

Therefore:

```text
PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE = CLOSED_MERGED
```

The 48-row matrix remains an evidence ledger. Its accepted authority-partial
rows are not converted to false pixel matches and do not reopen Pre-14. Future
visual convergence may improve a row, while direct-audio/acoustic capability
remains in its separately routed Pre-15 program.

## 2. Official remaining order

This amendment inserts one product epic and its consolidated gate after the
closed Pre-14 gate and before release closure:

```text
PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE — CLOSED_MERGED
  -> POST_PRE14_AUTHORING_IMPORT_MODERNIZATION
       -> AIM-0 roadmap amendment and merged-code baseline — CLOSED
       -> AIM-1 contract freeze — CLOSED
       -> AIM-2 candidate foundation — COORDINATOR_AUDIT_GREEN
       -> AIM-3 through AIM-8 implementation slices — NOT_STARTED
  -> POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE
  -> PRE_PHASE_15_RELEASE_CLOSURE_GATE
  -> Phase 15 Manual UAT & Release Hardening
  -> deferred Phase 14 Report an Error & Content Review (14A-14F)
  -> Phase 16 only after a separate product GO
```

This order supersedes current statements that hand a green Pre-14 gate directly
to Pre-15. It does not rewrite historical phase evidence or renumber Phase 14.

## 3. Epic outcome and bounded scope

The epic modernizes Practice authoring/import without replacing the canonical
draft editor, publisher, Practice AI data plane or Practice storage data plane.
Its required outcomes are:

1. one-sheet Quick Excel v1 for the explicitly supported simple formats, while
   preserving the current advanced Excel formats;
2. one persistent authoring-candidate lifecycle shared by Quick Excel and PDF
   AI, with normalize, validate, review and idempotent apply;
3. strict PDF authoring output that is independent from evaluation JSON;
4. Admin control-plane profiles and exact Practice purpose bindings, while all
   Practice provider calls stay on Practice-owned clients/transports with no
   global fallback;
5. Admin-managed named storage profiles, while Practice keeps its own storage
   adapters, authorization, keys, lifecycle and retention; and
6. one group/question staging review, one exact learner preview and the
   existing canonical editor/publisher after apply. Layout v1 is `AUTO` only.

The detailed target contract is frozen in:

- `docs/decisions/0012-practice-authoring-import-modernization-boundaries.md`;
- `docs/architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`;
- `docs/architecture/practice/schemas/practice-authoring-candidate-v1.schema.json`;
- `docs/architecture/practice/schemas/practice-pdf-authoring-output-v1.schema.json`;
- the R/L/W/S and validation examples beside those schemas.

## 4. Preserved current compatibility

The following current behavior is compatibility authority, not a reason to
reuse the unsafe mutation path:

- advanced Excel v2 is detected by `01_THONG_TIN_SET`; legacy v1 uses
  `Manifest`, `Sections`, `Groups`, `Questions` and `OptionsAnswers`;
- current `PracticeDraft` JSON is `practice-draft-v3`, with current canonical
  question/answer codecs and publisher validation;
- current PDF crop/region/session workspace is an Advanced authoring surface;
- `PracticePdfImportSession.snapshotJson` is only workspace state (selected
  pages, extraction strategy and annotations), never authoring staging;
- the existing draft editor, preview service, publisher and immutable published
  graph remain canonical; and
- Practice-local AI ports/transports and Practice-local material, PDF and
  learner Speaking lifecycles remain domain-owned.

Quick Excel detection is inserted before advanced v2 and legacy v1. Advanced
files keep their existing detector, parser and accepted semantics, while AIM-3
adapts both existing readers to the shared candidate/apply boundary with parity
tests. No existing valid workbook may silently change meaning, and no Excel
reader remains as an alternate direct-draft writer.

## 5. Explicit no-go boundaries

This epic must not:

- let Excel or AI write or merge `PracticeDraft` directly;
- reuse PDF `snapshotJson` as the candidate store;
- parse authoring output with Writing/Speaking evaluation schemas or accept
  score, rubric, learner-feedback or acoustic-evidence fields in authoring JSON;
- route Practice through `com.ksh.features.ai.client.AiClient`, an enabled-row
  global fallback loop or mutable global prompt fallback;
- route Practice private bytes through the generic public-upload surface;
- fall back from `PRACTICE_AUTHORING` or `PRACTICE_SPEAKING` storage writes to
  `GENERAL_UPLOADS` or local storage when their active profile is unavailable;
- create a second canonical draft editor, publisher or learner renderer;
- implement `MATCHING`, media upload/reference authoring, arbitrary complex
  blanks or manual layout in Quick Excel v1;
- implement, simulate or qualify direct-audio/acoustic Speaking; or
- call a real AI/STT/TTS/storage provider as part of AIM-0 or AIM-1.

## 6. Direct-audio routing remains unchanged

Direct-audio/acoustic Speaking is outside this epic. It remains the Pre-15
branch `P15-PRE-01B` and may start only after:

1. `P15-PRE-08` policy/privacy/consent/retention approval;
2. an authorized dark path that proves the scorer consumes the intended audio;
3. `P15-PRE-07` Korean-SME calibration/readiness; and
4. the applicable Phase 15D, 15E and 15H provider/security/UAT gates.

Until then, transcript-only and acoustic-unavailable behavior stays
fail-closed. This epic creates no synthetic waveform, alignment, phoneme,
stress, pronunciation, fluency or acoustic score fixture.

## 7. Epic slices and dependency order

| Slice | Owner and primary files | Dependencies | Acceptance gate |
|---|---|---|---|
| `AIM-2 — CANDIDATE_FOUNDATION` | Practice authoring backend; new candidate entity/repository/service/normalizer/validator/apply ledger plus one forward migration | AIM-1 | Persistent candidate state and optimistic locking; stable source/group/question IDs; no importer writes a draft; apply replay is idempotent; stale draft version fails without partial mutation. |
| `AIM-3 — QUICK_EXCEL_V1` | Excel controller/service, new quick detector/codec/template, candidate adapters for existing V2/legacy readers and focused tests; existing codecs remain format-compatibility owners | AIM-2 | Exact one-sheet detection runs first; route owns draft/test/skill/lesson; all supported R/L/W/S simple rows reach a candidate; unsupported media/MATCHING/complex blanks route to Advanced; V2/v1 golden workbooks keep their meaning and also stop at candidate review rather than direct draft mutation. |
| `AIM-4 — REVIEW_APPLY_PREVIEW` | Candidate API/page/template/JS, canonical draft preview/editor services and apply coordinator | AIM-2, then AIM-3 for end-to-end proof | Review is grouped by candidate group/question; errors are field-addressable; `View as learner` uses the canonical projection/renderer; explicit atomic apply is idempotent; redirect enters the existing editor; layout is `AUTO`. |
| `AIM-5 — PRACTICE_AI_CONTROL_PLANE` | Admin AI settings plus a new Practice control-plane resolver/profile/binding package and Practice-owned data-plane adapters; never shared `AiClient` | AIM-2; can run beside AIM-3/4 with separate files | Six exact purpose bindings, at most one active provider per purpose, purpose-specific capability test, immutable request snapshot, disabled/missing binding fails closed, and zero global fallback/import. No real call in default automated tests. |
| `AIM-6 — PRACTICE_STORAGE_PROFILES` | Admin storage profiles, Practice authoring/speaking storage adapters, lifecycle services/entities and additive migration | AIM-2; may run beside AIM-5 | Three exact profile codes; private app-authorized reads; writes fail closed; legacy local rows remain readable only through bounded identity-aware compatibility; lifecycle/retention and rollback evidence pass. |
| `AIM-7 — PDF_AUTHORING_TO_CANDIDATE` | PDF import API/orchestrator/prompt/output validator/assembler and Basic Text/PDF flow; existing crop/region workspace stays Advanced | AIM-2, AIM-4, AIM-5; AIM-6 when bytes use managed profiles | Text/PDF -> `EXTRACT`/`GENERATE` -> strict authoring output -> server normalize/validate -> candidate -> editable review -> explicit apply. No evaluation fields, `snapshotJson` staging or direct draft write. Advanced region/crop golden journeys stay green. |
| `AIM-8 — COMPATIBILITY_AND_EPIC_CLOSE_PREP` | Cross-slice tests, migrations, docs, operations and static ownership scans; production feature owners change only for a proven integration defect | AIM-2..7 | Quick/Advanced/PDF parity, authorization, idempotency, migration/rollback, private storage, provider-disabled, exact learner preview and publisher regressions pass as one evidence set; no-go scans are zero. |

File-ownership locks for parallel slice work:

- AIM-2 exclusively owns the new
  `features/practice/manage/authoringcandidate/**` package, candidate entities,
  repositories and its migration;
- AIM-3 owns `PracticeAssessmentExcelController`,
  `PracticeAssessmentExcelService`, `PracticeAssessmentExcelV2Codec`, the
  Quick codec/template/import page and their focused tests;
- AIM-4 owns the candidate-review controller/template/JavaScript and apply
  coordinator. Existing `PracticeDraftPreviewService`, `editor.html` and
  `PracticePublisherService` are integration dependencies, not fork points;
- AIM-5 owns new Admin Practice-purpose settings/resolution plus Practice AI
  binding adapters. It does not own or modify shared `features/ai/client/AiClient`;
- AIM-6 owns storage-profile Admin configuration and Practice authoring/
  Speaking adapters and lifecycle mappings. Existing general-upload behavior
  changes only where the `GENERAL_UPLOADS` profile mapping requires parity;
- AIM-7 owns the Basic authoring path around `PracticePdfImportApiController`,
  `PracticePdfAiPayloadBuilder`, `PracticePdfAiOrchestrator`,
  `PracticePdfAiGenerationService` and replacement of
  `PracticePdfDraftAssembler`; crop/region services remain Advanced owners; and
- AIM-8 owns cross-slice tests, static guards, operations and closure docs. It
  may return an integration defect to the owning slice, not absorb unrelated
  production files.

No slice may use a migration version selected from an old inventory. The owning
slice must refresh `origin/main`, list the complete Flyway chain and allocate a
globally free version immediately before creating SQL.

## 8. Consolidated epic gate

`POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE` is a separate
GO/NO-GO checkpoint after AIM-8. It closes only when:

- AIM-2 through AIM-8 are individually accepted and integrated in dependency
  order;
- all exact route, schema, purpose and storage-profile identities match AIM-1;
- current valid advanced Excel and Advanced PDF workspace journeys remain
  compatible;
- no importer/provider response writes `PracticeDraft` before explicit apply;
- apply replay, concurrent draft edit, rejected row and rollback cases are
  proven on a disposable schema;
- Practice source has no dependency on shared `AiClient` or global provider
  fallback and no Practice-private write uses `GENERAL_UPLOADS`;
- learner audio remains private/app-authorized and direct-audio scoring is
  absent;
- focused/integration/static checks and one proportionate consolidated Java 17
  lifecycle are green with providers disabled; and
- the handoff identifies the exact merge SHA that Pre-15 must consume.

Only that green gate hands off to `PRE_PHASE_15_RELEASE_CLOSURE_GATE`.

## 9. AIM-0/AIM-1 checkpoint

```text
AIM-0 roadmap/baseline:                         CLOSED
AIM-1 architecture/contract freeze:             CLOSED
production implementation AIM-2:           COORDINATOR_AUDIT_GREEN
production implementation AIM-3..AIM-8:    NOT_STARTED
real AI/STT/TTS/storage provider calls:      0/0/0/0
direct-audio/acoustic data created:                 0
shared AI/storage runtime merge:                    0
```

Validation recorded on `2026-08-02`:

- both schemas pass Draft 2020-12 meta-schema validation;
- five candidate examples cover R/L/W/S plus Advanced `MATCHING`, and the
  strict PDF output example validates through its local cross-schema reference;
- the validation-issue envelope validates and negative injections of
  `score`/`evaluation_status` are rejected;
- static contract checks confirm the exact 24 Quick columns, six AI purposes,
  three storage profiles, official roadmap order and all 48 audit rows;
- `git diff --check` is green and all 19 changed paths are Markdown/JSON only;
  no Java, SQL, provider or runtime configuration changed; and
- the refreshed migration inventory still has pre-existing duplicate versions
  V73, V74 and V75. AIM-1 intentionally leaves the migration number unassigned
  for AIM-2's integrated-chain reconciliation.

## 10. AIM-2 candidate foundation checkpoint

Recorded on `2026-08-02` from exact PR #56 merge baseline
`813a54d7c5d2a02cc4cc1bf2213085db3907dfea`.

### 10.1 Approved migration reconciliation

The integrated main tree combined two previously valid parent migration lines
at V73-V75. The coordinator's repository/environment audit found `40` local
schemas, but no schema with a retained/deployed/canonical obligation for the
former-main V73-V79 identities. GitHub evidence was deployments `0`,
environments `0` and workflows `0`. The user therefore approved the formal
classification `NO_RETAINED_DEPLOYED_CANONICAL_DATABASE_OBLIGATION`, with an
explicit ban on deleting, repairing, reusing or mutating old databases.

Practice V73-V75 remain unchanged. The former-main line was renamed by the
approved constant `+3` offset, preserving its dependency order and exact SQL
bytes:

| Final identity | Preserved SHA-256 |
|---|---|
| `V76__practice_evaluation_contract_identity_capacity.sql` | `f07c0ea1a78f2dc467e5eb82e03c2de698c3e6a4be3450181fcd5a4e6e153922` |
| `V77__discovery_ai_editorial.sql` | `eef20a33ba0a5a04e51a3c038066bbfb04c402dd89cdc39ca32aa652f5af98b8` |
| `V78__news_run_traceability.sql` | `5fb8c6efa3e32e5f4a1bfae0874e01b98ac63194b0741005d348917d30524618` |
| `V79__backfill_news_ai_run_trace.sql` | `6439f7ea24f882fb7da7125017cc929e25db96016511bbdf0aec2cf13c892ee6` |
| `V80__class_approval_lifecycle.sql` | `6640bec6daebb1afd226b49bb9ae87509a5f97418c8c7c04a684e8581abf35fa` |
| `V81__seed_ai_flashcard_generator_prompt.sql` | `7da3da706dda38b1c2b16ce25e086644e3db1dfb374f4541bc5f24b7fbedd435` |
| `V82__refine_ai_generation_prompts.sql` | `5b81c392ce68cfe603bad71b1f9cd07d2170e9d155f7f39af3a50729753431a1` |

The resulting chain is exactly `83/83`, unique and continuous from V1 through
V83. AIM-2 owns the single new additive migration
`V83__practice_authoring_candidate_foundation.sql`; it creates only the
persistent candidate and apply-event ledger tables. No Flyway repair,
destructive database action or old-database mutation occurred.

### 10.2 Candidate/apply implementation

The new exclusively AIM-2-owned
`features/practice/manage/authoringcandidate/**` package now provides:

- persistent candidate and apply-event entities/repositories, immutable
  owner/source/target/base-version snapshots, stable candidate/group/question
  identities, at-least-seven-day configurable expiry and JPA optimistic lock;
- server-owned canonical normalization with NFC/LF text handling, strict typed
  codec reconstruction, nested unknown-field removal and exact JSON Pointer
  issues, plus source/target/type/skill/authority validation;
- review lifecycle, warning acknowledgement, content digest recalculation,
  owner authorization and fail-closed stale candidate versions; and
- candidate-first/draft-second pessimistic locking, in-memory exact-section
  projection, canonical draft normalization/validation, one-draft atomic apply
  and a unique replay ledger. An exact replay returns its recorded result;
  stale target versions and canonical blockers persist only a conflict/rejected
  event and leave the draft unchanged.

No existing production owner was changed. AIM-3 Quick detection/template,
AIM-4 UI/learner preview, AIM-5 provider binding/calls, AIM-6 storage profiles,
AIM-7 PDF workspace/provider changes, Pre-15/direct-audio/acoustic work and
shared AI/storage rewrites remain absent.

### 10.3 Deterministic evidence

Java 17 evidence is green:

- AIM-2 unit/static-focused tests: `22/22`;
- existing canonical draft contract/validator/service regressions: `46/46`;
- fresh disposable catalog
  `ksh_test_aim2_candidate_foundation_fresh`: Flyway `83/83`, Hibernate/JPA
  startup/schema mapping and persistence/optimistic-lock/candidate-service/
  real atomic replay integration `4/4`;
- separate disposable catalog
  `ksh_test_aim2_candidate_foundation_upgrade_final`: authoritative Practice V75
  lineage `75/75` (`1/1`), followed by exact reconciled V76-V83 upgrade and
  application integration `4/4` on the final schema; and
- `git diff --check`, migration hash/continuity guards and static no-go scans
  are green. Provider/storage/STT/TTS calls are `0/0/0/0`; acoustic fixtures,
  production JavaScript/UI files and AIM-3+ runtime entry points added are `0`.

The named final-evidence catalogs remain isolated in the dedicated disposable
AIM-2 MySQL container. The earlier exploratory
`ksh_test_aim2_candidate_foundation_upgrade` catalog is retained there too;
none was dropped or used to mutate an older local schema.

The coordinator independently repeated the Java 17 unit/regression gates and
the fresh V1-V83 plus V75-to-V83 integration paths on two additional disposable
catalogs. A first fresh harness attempt failed before V1 because its scoped
test user lacked the legacy V1 `CREATE DATABASE` privilege; that catalog was
retained without repair or deletion, and the corrected least-privilege rerun
passed without changing production code or migration SQL.

Handoff: package and merge AIM-2, verify refreshed `origin/main`, archive its
implementation task, then open AIM-3 Quick Excel v1 from the exact merge commit.
