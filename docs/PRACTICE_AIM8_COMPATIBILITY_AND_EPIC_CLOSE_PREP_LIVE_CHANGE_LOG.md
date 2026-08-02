# Practice AIM-8 Compatibility and Epic Close Prep Live Change Log

Recorded: `2026-08-02`

Status: `AIM_8_EVIDENCE_COMPLETE_CONSOLIDATED_GATE_READY`

## 1. Exact integrated baseline

Before the first AIM-8 edit, the worktree was clean and detached at the exact
merged AIM-7 baseline required by the delegation:

```text
HEAD:        4e4b42733e9b4535f7babd4f4b1ba8d4a90d895c
main:        4e4b42733e9b4535f7babd4f4b1ba8d4a90d895c
origin/main: 4e4b42733e9b4535f7babd4f4b1ba8d4a90d895c
subject:     Merge pull request #62 (AIM-7)
worktree:    clean
```

The following authority was read in full before implementation:

1. `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`,
   including its embedded AIM-2 live checkpoint;
2. `decisions/0012-practice-authoring-import-modernization-boundaries.md`;
3. `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`;
4. every AIM-3, AIM-4, AIM-5, AIM-6 and AIM-7 live change log; and
5. both frozen schemas and all adjacent R/L/W/S, Advanced matching,
   validation-error and PDF-output examples.

The pre-edit migration inventory is exactly 85 files, unique and continuous
from V1 through V85. The published V1-V56 checksum manifest has zero mismatch.
AIM-8 owns no migration unless a reproducible integration defect proves one is
necessary; no migration identity has been allocated.

## 2. Locked ownership and no-go boundary

AIM-8 owns only cross-slice compatibility tests, migration/rollback
verification, operations/runbook documentation, static ownership scans and
honest consolidated-gate preparation. Production feature-owner files may
change only for a reproduced integration defect, with the smallest owner-local
fix and a regression test.

The product owner subsequently resolved `AIM8-INT-001`: Practice AI binding
revisions are zero-based and revision `0` is the first valid identity. AIM-8
therefore owns the minimal frozen-schema and two owner-guard corrections plus
regression evidence. V84/JPA semantics remain unchanged and no migration is
allocated.

This slice will not delete legacy Excel/PDF/tables, add provider-console links,
call a real provider/R2/STT/TTS endpoint, add secrets, implement Speaking
direct-audio/acoustic scoring, open Phase 14/15/Pre-15, repair/reset/drop an
existing database, or claim the consolidated gate itself.

## 3. Requirement-to-evidence trace matrix

`PASS` means that the named AIM-8 integrated selector/static/database evidence
is green on the exact baseline. `FAIL` is a consolidated-gate blocker even
when the other assertions in the row pass.

| ID | Requirement | Route/schema/owner | AIM-8 test or evidence | State |
|---|---|---|---|---|
| C01 | Quick, Advanced V2 and Legacy Excel persist a candidate and do not mutate a draft before explicit apply | `/practice/manage/excel/**`; `PracticeAssessmentExcelService`; candidate schema/source kinds | Cross-source Excel journey plus production mutation scan | `PASS` |
| C02 | Basic Text/PDF and Advanced crop/region persist the same candidate boundary; no legacy direct-draft/snapshot staging | `/practice/manage/pdf-authoring/candidates`; `/practice/manage/import-sessions/{id}/generate`; strict PDF schema | Basic/Advanced controller-orchestrator-assembler selector plus retired-path scan | `PASS` |
| C03 | Lecturer owner succeeds; student, other lecturer, revoked target, cross-owner/cross-draft/cross-session fail closed | Excel/PDF/review/material/private-media routes and `PracticeAuthorizationService` | Role/owner/revocation controller and service selector | `PASS` |
| C04 | Candidate create/reuse and immutable source/target/base-version identity | `practice_authoring_candidates`; `PracticeAuthoringCandidateService` | Consolidated MySQL candidate journey | `PASS` |
| C05 | Warning acknowledgement, rejected rows, stale version/digest and optimistic concurrency fail closed | candidate lifecycle/review; JPA `lock_version` | Lifecycle/unit plus real MySQL optimistic-writer proof | `PASS` |
| C06 | Atomic explicit apply and exact idempotent replay mutate one draft once | apply service/event ledger; `UNIQUE(candidate_id, apply_request_id)` | Consolidated MySQL apply/replay journey | `PASS` |
| C07 | Objective single/multiple/TFNG/blanks and Advanced matching preserve strict typed authority | candidate normalizer/validator; canonical codecs | Excel/PDF/candidate normalization compatibility selector | `PASS` |
| C08 | Writing is exactly Q51-Q54; Speaking candidate authoring remains `manual_text + text_only + none` | frozen schemas/examples; draft validator/publisher | R/L/W/S projection and publisher regressions | `PASS` |
| C09 | Candidate learner preview is an exact in-memory canonical projection and never persists | candidate preview coordinator; shared canonical preview mapper/fragment | Real MySQL non-mutation proof plus shared-renderer static scan | `PASS` |
| C10 | Exactly six AI purposes; one immutable binding/profile/model/prompt snapshot; missing/disabled/stale binding fails closed | V84; Practice AI resolver/transport/audits | Mock-only control-plane/capability selector and ownership scan | `PASS` — zero-based identity is aligned; negative/missing/disabled/stale remain closed |
| C11 | Practice has no shared `AiClient`, global provider fallback or unbounded provider route; real provider/STT/TTS calls are zero | `features/practice/ai/**` | Whole-Practice source scan and fake transport counters | `PASS` |
| C12 | Exactly three storage profiles; exact-profile private app reads; no Practice fallback to `GENERAL_UPLOADS` | V85; authoring/Speaking adapters and controllers | Storage authorization/path/profile selector and ownership scan | `PASS` |
| C13 | Legacy-null compatibility is bounded; migration copy/verify/CAS/delete and cleanup/rollback remain forward-only | storage migration coordinator, lifecycle tasks, nullable profile columns | Fake-object migration/lifecycle selector plus runbook rollback checklist | `PASS` |
| C14 | Fresh V1-current and authoritative V75-current upgrade/validation pass on disposable catalogs without repair/reset/drop | V1-V85 Flyway chain and Hibernate mappings | Two new isolated MySQL catalogs with retained read-only evidence | `PASS` |
| C15 | Migrations are unique/continuous/checksummed; route/template/JS syntax and no-go/ownership scans are green | migrations, controllers, templates, JS, production ownership graph | AIM-8 static contract, Node syntax, checksum/continuity commands | `PASS` |
| C16 | Operations truthfully records disable/rollback, inventory, cleanup and post-epic follow-ups without opening a later phase | AIM-8 operations runbook and live log | Documentation/static assertions | `PASS` |

## 4. Evidence ledger

| Check | Result |
|---|---|
| Exact baseline and clean-tree audit | `PASS` |
| Authority, live-log, schema and example read | `PASS` |
| Pre-edit Flyway inventory | `PASS` — 85 unique, continuous V1-V85 |
| Published V1-V56 checksum manifest | `PASS` — 56/56, zero mismatch |
| Zero-based focused compatibility lane | `PASS` — 29 run, 29 passed, 0 failed/errors/skipped across six owner/adapter/static classes |
| AIM-8 focused compatibility/static tests | `PASS` — 9/9 (seven static/ownership assertions plus two revision boundary runtime proofs) |
| Cross-slice regression selector | `PASS` — 337 run, 333 passed, 4 guard-skipped, 0 failed, 0 errors across 50 classes |
| Fresh V1-V85 disposable MySQL + Hibernate validation | `PASS` — 1/1 consolidated journey; 85 successful migrations, 0 failed, latest V85 |
| V75-V85 upgrade/validation on a separate disposable catalog | `PASS` — 1/1 V1-V75 lineage, then 1/1 V76-V85 upgrade/application journey; 85 successful, 0 failed |
| JavaScript syntax / route-template checks | `PASS` — Node checked 75/75 standalone files; route/template assertions are green |
| Frozen schema/example JSON parse | `PASS` — 9/9, zero syntax errors; strict semantic validators are green in the selector |
| Static ownership/no-go scans | `PASS` — seven prohibited categories and stale one-based guards each returned zero |
| Real AI/R2/STT/TTS calls | `0/0/0/0` |

The green selector above is the final correctly configured run. Earlier
misconfigured reruns were discarded because the disposable-database guard
rejected missing required connection values before any Spring context could
execute. After the zero-based change, the first fresh integration fixture also
attempted learner preview while its PDF candidate intentionally retained
blocking review issues; that expected fail-closed result was removed from the
fixture and the lane was rerun on a new catalog. No failed-attempt catalog was
reused as fresh evidence.

The retained disposable MySQL container is
`ksh-aim8-compat-20260802-mysql` on loopback port `53379`. No catalog was
repaired, cleaned, reset or dropped. Read-only post-run inventory is:

| Catalog | Flyway | Candidates | Apply events | AI bindings | Storage profiles |
|---|---:|---:|---:|---:|---:|
| `ksh_test_aim8_zb_fresh_r2` | 85 success / 0 failed / V85 | 4 | 2 | 6 | 3 |
| `ksh_test_aim8_zb_upgrade` | 85 success / 0 failed / V85 | 4 | 2 | 6 | 3 |
| `ksh_test_aim8_zb_regression` | 85 success / 0 failed / V85 | 0 | 0 | 0 | 3 |

Both application journeys contain exactly one candidate for Quick Excel,
Advanced Excel V2, Legacy Excel V1 and PDF AI. The stored PDF candidate has
`bindingRevision = 0`; draft state remains unchanged before explicit apply.
Each journey records exactly one `DRAFT_APPLIED` and one `CONFLICT` event, and
zero AI execution audit, capability-test or storage-migration-job rows.

## 5. Resolution of `AIM8-INT-001`

The product decision explicitly defines binding revision `0` as the first
valid Practice AI identity. The minimal compatibility correction is:

1. frozen candidate schema `bindingRevision.minimum`: `1` to `0`;
2. PDF orchestrator availability guard: reject `< 0`, not `< 1`;
3. candidate PDF execution validation: reject integral values `< 0`, not `< 1`;
4. the already-correct control-plane adapter preserves revision `0` unchanged;
5. V84, JPA entities, resolver snapshots and database checks remain unchanged.

Regression evidence proves an enabled revision-`0` identity reaches exactly
its fake PDF transport, flows through the candidate assembler/validator and is
persisted/reused at candidate review on both fresh and upgraded MySQL. A
negative revision still fails before transport and before candidate
authorization/persistence. Existing missing/disabled, capability mismatch,
stale binding and post-response revision-change regressions remain green.

`AIM8-INT-001` is resolved without a migration, data rewrite, repair or
identity rebase. No other approval or product decision blocker was found.

## 6. Retirement and bounded follow-up inventory

Legacy Excel V1, Advanced Excel V2, Advanced PDF crop/region workspace and the
additive AIM-2/AIM-5/AIM-6 tables remain live compatibility surfaces in AIM-8.
Their removal or schema compaction is a post-epic decision after persisted-use
inventory and a separately approved retirement plan.

Provider API-console/documentation links are not implemented here. They remain
a bounded post-AIM-8 Practice control-plane UX refinement and do not affect the
six-purpose data-plane acceptance contract.

## 7. Exact AIM-8 worktree diff

The evidence-close diff contains exactly 13 files: six additions and seven
modifications. Migration files changed or added: zero. `git diff --check` is
clean.

- added this live change log;
- added the operations runbook and V57-V85 checksum manifest;
- added the static compatibility contract, consolidated MySQL journey and
  zero/negative revision boundary regression proof;
- changed the frozen candidate minimum and exactly two production owner guards;
- added zero-based adapter/assembler/candidate regression assertions; and
- changed `PracticeAuthoringCandidateMigrationTest` so its historical
  V83 assertion derives the current contiguous tip, while the AIM-8 static
  contract independently locks the exact V85 tip and bytes.

Line delta at evidence close: `+1189/-11` lines.

## 8. Final verdict

```text
POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE_PREP = READY
resolved defect = AIM8-INT-001 (zero-based binding revision)
production feature-owner changes = 2 minimal guards
frozen schema changes = 1 minimum alignment
new migrations = 0
real provider/R2/STT/TTS calls = 0/0/0/0
```

All AIM-8 trace rows are now PASS. `READY` means the exact evidence package is
ready for the coordinator-owned consolidated gate; it does not close that gate,
open Phase 14/15/Pre-15, or retire any legacy path.
