# Practice AIM-3 Quick Excel v1 Live Change Log

Recorded: `2026-08-02`

Status: `AIM_3_COORDINATOR_AUDIT_GREEN_READY_TO_PACKAGE`

## 1. Exact baseline and authority checkpoint

Before the first AIM-3 edit, the worktree was clean and detached at the exact
merged AIM-2 baseline:

```text
HEAD:        599b9b613260901dd920eef46befa131d24e9469
origin/main: 599b9b613260901dd920eef46befa131d24e9469
subject:     Merge pull request #57 from
             toannnnq1424/codex/practice-aim2-candidate-foundation
```

The following authority was read in full before implementation:

1. `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`;
2. `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`;
3. `decisions/0012-practice-authoring-import-modernization-boundaries.md`;
4. every Java source in
   `features/practice/manage/authoringcandidate/**`; and
5. `V83__practice_authoring_candidate_foundation.sql`.

The refreshed migration inventory is unique and continuous through V83:
`83/83`. AIM-3 requires no schema change and does not allocate a migration.

## 2. Pre-change implementation audit

The merged Excel flow had these evidenced boundaries:

- format order was Advanced v2 (`01_THONG_TIN_SET`) followed by the five-sheet
  Legacy v1 reader; no Quick identity existed;
- Advanced v2 and Legacy v1 both produced normalized `practice-draft-v3` JSON;
- `PracticeAssessmentExcelService#importDraft` still locked the linked draft,
  merged/replaced imported sections, called `setDraftJson`, persisted through
  `PracticeDraftRepository.saveAndFlush`, reconciled Speaking questions and
  linked/consumed staged media references;
- the controller returned the mutated draft and redirected directly to the
  canonical editor; and
- the AIM-2 candidate service already accepts exact source identities
  `QUICK_EXCEL`, `ADVANCED_EXCEL_V2` and `LEGACY_EXCEL_V1`, authorizes the exact
  route, captures the draft base version and persists normalized candidates.

Therefore the AIM-3 implementation seam is parser/codec -> canonical candidate.
Only the existing candidate apply service may mutate `PracticeDraft`; AIM-3
does not add an apply endpoint or candidate review UI.

## 3. Locked implementation slices

| Slice | State | Evidence target |
|---|---|---|
| Strict Quick identity, package safety and exact one-sheet/24-column template | `COMPLETE` | Quick marker wins detection; package preflight plus workbook validation rejects malformed identity, formulas, merges, macros, external links, hidden/extra sheets and unsupported columns |
| Quick R/L/W/S row-to-candidate mapping | `COMPLETE` | both R/L map all four simple objective types; Writing preserves exact Q51-Q54 and canonical `q51-b1`..`q52-b2` blank identity; Speaking emits only `manual_text + text_only + none` |
| Advanced v2 and Legacy v1 candidate adapters | `COMPLETE` | exact route section only; Advanced `MATCHING` and both V2/v1 typed golden meanings pass canonical normalization; no draft write/merge/bind/consume |
| Controller/import entry handoff | `COMPLETE` | candidate identity/state/version/digest returned; Advanced endpoint retained; separate Quick endpoint added; no review/apply endpoint or UI |
| Focused, parity, canonical regression and static verification | `COMPLETE` | Java 17 compile/test evidence, V83 continuity/hash and static no-go scans recorded below |

## 4. No-go ledger

The slice will keep all of these at zero:

```text
AIM-4 review/apply/learner-preview UI:       0
AI/provider/STT/TTS calls or shared AiClient: 0
storage-profile/R2 implementation:           0
PDF AI authoring implementation:             0
Speaking learner direct-audio/acoustic work: 0
new migration or old migration mutation:     0
database deletion/repair/destructive cleanup: 0
commit/push/PR:                               0
```

## 5. Verification ledger

This section is live and will be updated after each proportionate checkpoint.

| Check | Result |
|---|---|
| Exact baseline and clean-tree audit | `GREEN` |
| Authority/package/V83 read | `GREEN` |
| Java 17 main/test compilation | `GREEN` — 895 main and 409 test Java sources |
| Excel focused tests | `GREEN` — 36/36 (Quick 14, service/parity 13, controller 3, Speaking boundary 6) |
| Advanced/Legacy parity | `GREEN` — included in the 13 service tests; exact target typed content/answers and Advanced `MATCHING` preserved |
| AIM-2 candidate and canonical draft regressions | `GREEN` — 93/93 executed; 5 DB integration tests skipped by their existing environment guards |
| Scoped UI/boundary regressions | `GREEN` — 16/16 |
| Migration/static no-go scans | `GREEN` — unique continuous 83/83, V76–V82 hashes and additive V83 ownership test; zero Excel draft-mutation/AI-provider/AIM-4 diff matches |
| Database/container identity | `NONE_USED` (no AIM-3 schema change) |

The wider `PracticePhase11AuthoringUiContractTest` class still contains one
unrelated baseline failure for the pre-existing `→` text glyph in
`practice/result-detail-writing.html`; the same glyph is present at the locked
`599b9b6` baseline. The AIM-3 Excel method in that class is green and no
out-of-scope UI file was changed.

## 6. Final handoff boundary

- `HEAD` and `origin/main` remain
  `599b9b613260901dd920eef46befa131d24e9469`; only unstaged AIM-3 worktree
  changes exist.
- No database, container, migration, provider, secret or destructive action was
  used.
- No commit, push or pull request was created.
- Remaining work is coordinator diff/test audit, then commit, push and PR
  packaging.

## 7. Coordinator audit and packaging gate

The coordinator independently repeated the completion gate on the untouched
AIM-3 worktree:

- `git diff --check`: green;
- exact baseline: `HEAD == origin/main == 599b9b613260901dd920eef46befa131d24e9469`;
- Excel focused suite: `36/36` passed (`14 + 13 + 3 + 6`);
- candidate/canonical/publisher regression selector: green; DB-backed candidate
  integration tests remained skipped only by their existing environment guard;
- scoped Phase-11 Excel UI contract method: `1/1` passed;
- Flyway inventory: unique and continuous `83/83`, with no migration diff and
  no destructive token in V83; and
- production diff scan found no direct draft mutation/apply route, shared
  `AiClient`, provider/storage/STT/TTS call or Speaking acoustic implementation.

Coordinator verdict: `GREEN_TO_PACKAGE_AIM_3`.
