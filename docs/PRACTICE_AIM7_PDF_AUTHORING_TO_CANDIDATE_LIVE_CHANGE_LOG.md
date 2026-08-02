# Practice AIM-7 PDF Authoring to Candidate Live Change Log

Recorded: `2026-08-02`

Status: `AIM_7_IMPLEMENTED_GREEN_HANDOFF`

## 1. Exact baseline and authority checkpoint

Before the first AIM-7 edit, this worktree was clean and detached at the exact
merged AIM-6 baseline required by the delegation:

```text
HEAD:        63e7c00bb23539805f8ace3a47d7e26961720603
main:        63e7c00bb23539805f8ace3a47d7e26961720603
subject:     Merge pull request #61 (AIM-6)
```

The following authority sources were read in full before implementation:

1. `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`;
2. `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`,
   especially Sections 3–8;
3. `decisions/0012-practice-authoring-import-modernization-boundaries.md`;
4. the merged AIM-2 checkpoint in the roadmap amendment;
5. `PRACTICE_AIM4_REVIEW_APPLY_PREVIEW_LIVE_CHANGE_LOG.md`;
6. `PRACTICE_AIM5_AI_CONTROL_PLANE_LIVE_CHANGE_LOG.md`;
7. `PRACTICE_AIM6_STORAGE_PROFILES_LIVE_CHANGE_LOG.md`; and
8. both frozen JSON schemas plus the PDF authoring example.

The pre-edit Flyway inventory is unique and continuous through V85. AIM-7
needs no schema change and will not allocate or modify a migration.

## 2. Pre-change implementation audit

The merged foundation already provides persistent candidate normalization,
validation, owner/target snapshots, editable review, canonical learner preview,
atomic apply, exact `PRACTICE_PDF_AUTHORING` resolution and exact
`PRACTICE_AUTHORING` PDF/lecturer storage.

The evidenced AIM-7 gaps are:

- the current provider response schema is the legacy draft-shaped
  `documentTitle/sections/assets/warnings` envelope rather than
  `practice-pdf-authoring-output-v1`;
- `PracticePdfDraftAssembler` still parses provider output and saves or merges
  a `PracticeDraft` before candidate review/apply;
- the current endpoint returns a draft, and the Advanced workspace still
  exposes manual create/attach direct-draft actions;
- there is no Basic pasted-Text/PDF `EXTRACT`/`GENERATE` flow;
- prompt layers do not yet separate immutable contract/safety, Admin
  pedagogical prompt, lecturer request and untrusted source content; and
- strict unknown-field, forbidden evaluation-field, source-evidence and full
  canonical R/L/W/S reconstruction are not enforced at the PDF boundary.

## 3. Locked implementation slices

| Slice | State | Evidence |
|---|---|---|
| Strict output schema/decoder | `COMPLETE` | code-owned closed schema plus independent recursive server validation; exact canonical R/L/W/S, Q51–Q54, multi-answer/multi-blank and Speaking text-only contracts |
| Candidate assembler | `COMPLETE` | validated output maps only to `PracticeAuthoringCandidateService.createOrReuse`; all questions start `REVIEW_REQUIRED`; source/provider warnings become server-owned candidate issues |
| Purpose/prompt binding | `COMPLETE` | exact AIM-5 purpose before and after generation, immutable binding/profile/model snapshot, no fallback, four separated prompt/data layers and bounded audits |
| Basic Text/PDF UI/API | `COMPLETE` | Text/PDF + EXTRACT/GENERATE + lecturer request/page range creates candidate and redirects to the existing editable review |
| Advanced parity | `COMPLETE` | page/crop/region workspace remains under Advanced and now creates the same candidate instead of a direct draft |
| Verification/no-go evidence | `COMPLETE` | 115 focused tests green, fresh V1–V85 database proof, Java package and static scans green |

## 4. Migration and no-go checkpoint

```text
new/modified migration:                         0
shared AiClient/global provider fallback:       0
GENERAL_UPLOADS Practice write fallback:        0
real provider/R2/STT/TTS call in tests:          0
evaluation/submission/result authoring fields:  0
snapshotJson candidate staging:                 0
PracticeDraft mutation before explicit apply:   0
Speaking direct-audio/acoustic scoring:          0
player/scoring/publisher change:                 0
task/worktree/branch/commit/push/PR:             0
```

## 5. Implemented boundary

The Basic API is `POST /practice/manage/pdf-authoring/candidates` with an exact
authorized draft/test/skill/lesson target. It accepts pasted Text or a private
PDF source, exact `EXTRACT`/`GENERATE`, bounded lecturer requirements and a
bounded PDF page range. PDF creation continues through the merged AIM-6
`PracticePdfStorageService`/`PRACTICE_AUTHORING` path before extraction.

Advanced `POST /practice/manage/import-sessions/{id}/generate` retains the
existing short claim transaction, page/region/crop validation and asset
authorization. Its terminal response is now a candidate identity and existing
review URL. A legacy already-completed direct-draft session fails closed with a
conflict and cannot return or merge that draft through this route.

`PracticePdfAuthoringJsonContract` owns an inline, provider-portable JSON
Schema with `additionalProperties=false` at every object. The server validator
then independently enforces:

- exact schema version, operation and source digest;
- unique stable group/question/option/blank identity;
- exact requested evidence membership, page identity and text-span bounds;
- exact target skill/type and canonical assessment codecs;
- multiple-answer and multi-blank accepted values;
- Writing Q51/Q52 two-blank response/authority plus Q53/Q54 essay contracts;
- Speaking `manual_text + text_only + none`, null prompt audio/play limit; and
- recursive rejection of evaluation, submission, result, scoring, acoustic,
  publication and arbitrary target fields.

The assembler replaces provider points with code-owned authoring policy,
resolves only request-authorized image asset refs, sets PDF provenance to
unapproved and questions to `REVIEW_REQUIRED`, and calls only the AIM-2
candidate service. The retired direct-draft assembler/import service and their
controller/UI actions were removed.

The orchestrator calls only `PracticeStructuredGenerationPort` with exact
`PRACTICE_PDF_AUTHORING`. Authority is checked before and after the fake/real
port boundary. Candidate revision and provider idempotency include binding
revision, provider-profile revision/code/model, Admin prompt digest, lecturer
requirements, source digest, operation and target. Prompt authority remains
separate as immutable code safety/contract, exact named Admin pedagogy,
lecturer requirements and structured untrusted Text/PDF content.

## 6. Verification ledger

All commands used JDK `17.0.19`. No test called a real AI provider, R2, STT or
TTS service.

| Verification | Result |
|---|---|
| Java compile/package | `GREEN` — `mvn -q -DskipTests package` |
| Focused AIM-7 + inherited AIM-2/4/5/6 + Advanced regression | `GREEN` — 112/112, 0 failures/errors/skips |
| Real candidate persistence/apply selector | `GREEN` — 3/3, including idempotent candidate reuse, stale optimistic writer, atomic apply and exact replay |
| Fresh Flyway catalog | `GREEN` — 85 successful, 0 failed, max V85; candidate/apply, AIM-5 binding and storage-profile tables present |
| Auth | `GREEN` — class method-security boundary, student 403, revoked draft 403 before provider, exact target authorization before PDF storage/provider |
| Strict output | `GREEN` — R/L/W/S, Q51/Q54, multi-answer/multi-blank, unknown/forbidden fields, evidence bounds, digest/operation identity |
| Candidate-only | `GREEN` — PDF AI execution snapshot persists in `REVIEWING`; no draft save; legacy direct routes/classes absent |
| Advanced parity | `GREEN` — full-page synthetic regions, crop budgets, region/asset ownership, claim duplicate/expiry/release and UI handoff |
| Storage | `GREEN` — exact `PRACTICE_AUTHORING`, no General fallback/public URL, private app-authorized content routes |
| Static/diff checks | `GREEN` — closed schema recursion, no shared `AiClient`/provider fallback, no authoring `snapshotJson`, no direct-draft endpoint, `git diff --check` |

The disposable audit database is intentionally isolated and retained for the
coordinator:

```text
container: ksh-aim7-e45b-20260802-mysql
port:      127.0.0.1:52736
catalog:   ksh_test_aim7_e45b_fresh
flyway:    85 successful / 0 failed / max V85
```

No pre-existing catalog/container was cleaned, reset, dropped or modified.
The first non-privileged bootstrap attempt was rejected at V1 because that
historical migration requests `CREATE DATABASE`; the fresh catalog above was
then migrated with the isolated container root and is the only catalog used
for green persistence evidence. The existing expiry-claim integration fixture
was made timezone-independent by expiring its own row with database-side
`DATE_SUB`; no production claim semantics changed beyond allowing the new
`REVIEWING` release state.

## 7. Handoff inventory

- New production boundary: request model, code-owned schema, strict validator
  and candidate assembler.
- Updated production integration: controller, payload builder, orchestrator,
  candidate source-issue intake, generation release state, preview prompt and
  Basic/Advanced templates.
- Retired production direct path: `PracticePdfDraftAssembler`,
  `PracticeImportDraftService`, `PracticePdfAiPromptRules`.
- New focused tests: strict validator, assembler and AIM-7 static contract;
  existing controller/orchestrator/payload/candidate/claim tests were migrated
  to the candidate boundary.
- Migration diff: zero; inventory remains V1–V85.
- Worktree remains detached; no branch, commit, push or PR was created.
