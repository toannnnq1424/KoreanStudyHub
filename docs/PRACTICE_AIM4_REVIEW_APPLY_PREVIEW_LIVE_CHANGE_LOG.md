# Practice AIM-4 Review / Apply / Preview Live Change Log

Recorded: `2026-08-02`

Status: `AIM_4_IMPLEMENTATION_COMPLETE_READY_FOR_COORDINATOR_AUDIT`

## 1. Exact baseline and authority checkpoint

Before the first AIM-4 edit, the worktree was clean and detached at the exact
merged AIM-3 baseline:

```text
HEAD:        7fac2a9e9d53896151a725c0c3e831b24fbc23f7
origin/main: 7fac2a9e9d53896151a725c0c3e831b24fbc23f7
subject:     Merge pull request #58 (AIM-3)
```

The following authority and implementation seams were read before editing:

1. `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`;
2. `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`,
   including Sections 3.1–4 and Section 8;
3. `decisions/0012-practice-authoring-import-modernization-boundaries.md`;
4. `PRACTICE_AIM3_QUICK_EXCEL_V1_LIVE_CHANGE_LOG.md`;
5. every Java source under
   `features/practice/manage/authoringcandidate/**`;
6. `PracticeDraftPreviewService`, its response mapper, learner-preview modal,
   canonical editor preview flow and delivery presenter; and
7. `PracticeDraftValidator` and the current publisher validation/material
   seams.

The pre-edit Flyway inventory was unique and continuous through V83: `83/83`.
AIM-4 needs no schema change and does not allocate or modify a migration.

## 2. Pre-change implementation audit

The merged AIM-2/AIM-3 foundation already provided:

- persistent owner-scoped candidates, lifecycle normalization/validation,
  stable source/target identity, candidate digest and JPA optimistic version;
- an atomic apply service with candidate-then-draft write locking, UUID apply
  ledger, replay handling, base-draft conflict checks and one draft save;
- an in-memory append projector and canonical draft normalizer/validator; and
- Quick Excel candidate creation returning candidate identity without mutating
  the draft.

The evidenced AIM-4 gaps were:

- no candidate review controller, API, Vietnamese page or focused UI tests;
- review mutation checked only version, not the submitted version/digest pair;
- no full-draft in-memory learner preview coordinator;
- no explicit material-reference authority preflight at preview/apply;
- issue paths sorted lexicographically rather than numeric Auto group/question
  order; and
- the AIM-3 Excel page stopped at identity text instead of navigating to a
  review route.

## 3. Locked implementation slices

| Slice | State | Evidence |
|---|---|---|
| API/auth/version | `COMPLETE` | lecturer class guard; owner-scoped candidate lookup; exact target READ/EDIT authorization; submitted version + SHA-256 digest on review, ready, reject, preview and apply |
| Editable review/issues | `COMPLETE` | Auto-ordered group/question surface; numeric severity/group/question/field issue sorting; field pointers; question/group rejection; exact R/L strategy catalog; typed W/S JSON retained |
| Warning and blocker gate | `COMPLETE` | every edit re-normalizes, validates and recalculates digest; warning acknowledgement is explicit; blockers cannot become READY |
| Canonical learner preview | `COMPLETE` | pessimistic read of candidate and current draft; exact base-version check; in-memory append only; canonical normalize/validate/material authority; existing `PracticeDraftPreviewService`; one shared preview mapper/renderer/fragment for Editor and candidate |
| Explicit atomic apply | `COMPLETE` | UUID request, submitted version/digest, replay/mismatch/not-ready/stale fail-closed behavior, canonical validator/material authority, one atomic draft mutation, Auto layout and redirect to the existing editor |
| AIM-3 handoff | `COMPLETE` | candidate import response supplies an owner-scoped review URL and the Excel page navigates to it; codec/parser behavior is unchanged |
| Focused/canonical/DB verification | `COMPLETE` | Java 17 compile, Java/JS/static tests, real MySQL lineage/persistence/apply proof and no-go scans recorded below |

## 4. Implementation ledger

### Candidate backend and authorization

- Added `PracticeAuthoringCandidateReviewController` under
  `/practice/manage/authoring-candidates/{candidateId}` with data, review,
  ready, reject, learner-preview and apply endpoints.
- Existing owner-filtered service lookups remain the non-disclosure boundary;
  the exact target draft still passes through `PracticeAuthorizationService`.
- Review, ready and reject now compare both submitted candidate version and
  digest. JPA `@Version` remains the final concurrent-writer guard.
- Controller payload versions use nullable identities and fail with HTTP 400
  when version/digest is omitted or malformed; an absent JSON field can no
  longer silently become candidate version zero.
- Invalid terminal/repeated lifecycle operations return stable candidate
  errors instead of leaking entity state exceptions as HTTP 500.
- Validator issues now sort by severity, numeric group index, numeric question
  index, field pointer and stable code.

### Review and learner surface

- Added a responsive Vietnamese review page, focused CSS and safe DOM-based
  JavaScript. It has no draft autosave or publication action.
- Review supports label, instruction, stimulus, prompt, points, explanation,
  options, review acceptance, typed `questionContent`/`answerSpec`, group and
  question removal, and exact selectable R/L explanation strategies.
- The R/L selector consumes the authority field `supportedQuestionTypes` and
  filters `requiredEvidence`; the server independently invokes the existing
  stimulus + typed-content + answer authority overload before READY.
- Initial actions stay disabled until owner data loads, and READY stays
  disabled until displayed warnings are acknowledged.
- Added `PracticeAuthoringCandidatePreviewService`. It locks candidate/draft
  for a coherent read, rejects stale base versions, builds the complete draft
  projection in memory, then uses the canonical contract, validator, material
  authority and `PracticeDraftPreviewService`. It does not call a repository
  save or persist the projection.
- Extracted the existing editor learner-preview modal and rendering behavior
  into one shared fragment/module. Editor and candidate now use that same
  template, mapper and renderer, including Matching, Fill, Writing and
  Speaking presentation branches; the old inline editor renderer was removed.

### Apply and material authority

- Existing candidate-first/draft-second locking and one-transaction apply
  remain authoritative.
- Added a fail-closed material-reference preflight for managed material URLs,
  unresolved material placeholders and cross-draft private media routes.
- Material authority is a mandatory dependency and oversized/unparseable
  material identities fail as stable candidate errors; it cannot be bypassed
  by a null test seam.
- Apply tests now cover exact successful replay, reused-request mismatch,
  stale submitted candidate version, not-ready state, stale target version,
  canonical blocker and material-authority rejection without draft mutation.
- Candidate/draft version conflicts map consistently to HTTP 409 so the review
  client reloads state and does not pin a stale apply request replay.
- Projected groups explicitly carry `layout=AUTO`; reviewed groups/questions
  append to the exact target and successful API responses navigate to the
  existing editor. Publication remains separate.

## 5. Verification ledger

| Check | Result |
|---|---|
| Exact baseline / detached HEAD | `GREEN` — `HEAD == origin/main == 7fac2a9e9d53896151a725c0c3e831b24fbc23f7` |
| Java 17 main compilation | `GREEN` |
| JavaScript syntax | `GREEN` — candidate review and shared preview modules |
| Focused + candidate/canonical/publisher/Excel/UI regression | `GREEN` — 140 executed, 140 passed, 0 failed/error; 6 DB-guarded tests skipped in this non-DB lane |
| Disposable DB integration lane | `GREEN` — 6 executed, 6 passed, 0 skipped/failed/error |
| Combined proof matrix | `GREEN` — 146/146 tests passed across the two lanes |
| Migration inventory | `GREEN` — 83 files, continuous V1–V83, 83 successful rows, 0 failed rows, latest V83 |
| Candidate tables | `GREEN` — exactly 2 expected AIM-2 candidate/apply-ledger tables on the disposable schema |
| `git diff --check` | `GREEN` |
| Static no-go/mutation scans | `GREEN` — no new AI client/provider/storage/STT/TTS/acoustic call; no candidate preview/controller draft save/autosave/publish path; no migration diff |

The non-DB regression selector includes all `PracticeAuthoringCandidate*`
tests plus `PracticeDraftPreviewServiceTest`, `PracticeDraftContractServiceTest`,
`PracticeDraftValidatorTest`, `PracticePublisherServiceTest`, the Quick Excel
codec/service/controller tests, and the affected Editor/Speaking preview UI
contract methods.

## 6. Disposable database evidence

No existing database/container was modified. A new disposable container was
created solely for AIM-4 verification and intentionally left running; no
destructive cleanup was performed:

```text
container:  ksh-aim4-review-4b23-20260802-mysql
containerId: c69f3f2ef40e
image:      mysql:8.0
host bind:  127.0.0.1:63428 -> 3306
```

The first fresh catalog, `ksh_aim4_review`, was migrated only through V75 by
the pre-14 lineage test. Spring's test safety guard then correctly refused it
because it lacked the required `ksh_test_` prefix; it was not used for
persistence tests and was not deleted.

The safety-compliant fresh catalog
`ksh_test_aim4_4b23_20260802` then provided the authoritative proof:

1. clean Flyway migration V1→V75 and the lineage assertion passed (`1/1`);
2. application migration/validation through V83 succeeded;
3. candidate persistence, optimistic-writer rejection, real atomic apply
   replay and real read-locked/non-mutating learner preview tests passed
   (`5/5`);
4. `flyway_schema_history`: `83` successful, `0` failed, latest `83`; and
5. exactly `practice_authoring_candidates` and
   `practice_authoring_candidate_apply_events` were present for this feature.

No database repair, deletion, reset, migration rewrite or container cleanup
was performed.

## 7. No-go ledger

```text
AIM-5 AI control plane/provider/admin:             0
AIM-6 R2/storage-profile implementation:           0
AIM-7 PDF AI implementation:                       0
shared AiClient/provider/STT/TTS real calls:       0
Speaking direct-audio/acoustic authoring:           0
second editor/player/publisher/preview renderer:    0
candidate-owned full-draft autosave/publication:    0
new/modified migration:                             0
existing database/container destructive mutation:  0
commit/push/PR:                                     0
```

The shared preview module still renders the existing canonical Speaking
delivery payload for the existing Editor; AIM-4 does not create Speaking
audio, invoke acoustic processing, or add a candidate audio-authoring path.

## 8. Final handoff boundary

- `HEAD` and `origin/main` remain the exact AIM-3 commit
  `7fac2a9e9d53896151a725c0c3e831b24fbc23f7`; only unstaged AIM-4 worktree
  changes exist.
- No task, worktree, branch, commit, push or pull request was created.
- No AIM-5+ surface was opened.
- Remaining work is only coordinator diff/test audit, then coordinator-owned
  commit, push and PR packaging.

## 9. Coordinator audit closure

- Re-ran the AIM-4 review/controller/service/material-authority/preview/apply
  tests together with the affected Excel and Phase 11 UI contract methods on
  Java 17; all selected AIM-4 checks passed.
- Updated two stale Phase 11 source-contract assertions so they follow the
  deliberate AIM-4 handoff to `result.reviewUrl` and the shared canonical
  preview renderer. No production behavior was changed by this audit fix.
- The two unrelated full-class baseline failures (legacy text-glyph policy and
  pre-existing editor close-fragment count) remain outside AIM-4 and were not
  masked or rewritten.
- Reconfirmed `git diff --check`, JavaScript syntax, unchanged V1-V83 migration
  inventory, owner authorization, version+digest stale-state rejection,
  UUID apply replay semantics and mandatory material authority.
