# Practice Post-Pre-14 Authoring Import Modernization Roadmap Amendment

Recorded: `2026-08-02`

Status: `AIM_0_CLOSED_AIM_1_CONTRACT_FROZEN`

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
       -> AIM-2 through AIM-8 implementation slices — NOT_STARTED
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
production implementation AIM-2..AIM-8:    NOT_STARTED
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

Handoff: start AIM-2 from the integrated commit containing this contract,
refresh `origin/main`, preserve the file ownership table above, and stop on any
product decision that changes a frozen schema, supported Quick Excel family,
purpose code, storage profile, apply atomicity or direct-audio boundary.
