# Practice Phase 13F Live Change Log

Last updated: 2026-07-26

## 1. Gate status

- `PHASE_13F_PREPARATION = COMPLETE`
- `PHASE_13F_IMPLEMENTATION = COMPLETE`
- `PHASE_13F_VALIDATION = COMPLETE_FOCUSED_GATE_GREEN`
- `13F-01 = IMPLEMENTED_AND_FOCUSED_TESTED`
- `13F-02 = IMPLEMENTED_AND_FOCUSED_TESTED`
- `13F-03 = IMPLEMENTED_AND_FOCUSED_TESTED`
- `13F-04 = IMPLEMENTED_AND_FOCUSED_TESTED`
- `13F-05 = IMPLEMENTED_AND_FOCUSED_TESTED`
- `13F-06 = IMPLEMENTED_AND_FOCUSED_TESTED`
- `13F-06_FINAL_STATIC_AUDITS = ACCEPT_STATIC_13F_06`
- `13F-03_PARTIAL_PRESENTATION_FIX = ACCEPT_STATIC_AFTER_FOCUSED_FIX`
- `13F-03_STATIC_REAUDIT = ACCEPT_STATIC_AFTER_FOCUSED_FIX`
- `13F-02_STATIC_REAUDIT = ACCEPT_STATIC_AFTER_FOCUSED_FIX`
- `13F-01_STATIC_REVIEW = COORDINATOR_ACCEPTED_AFTER_CONCENTRATED_FIX`
- `PHASE_13 = OPEN`
- Phase 13E prerequisite is `COMPLETE_FOCUSED_GATE_GREEN`; the user reports its
  implementation committed/pushed at
  `93d87fd1f8dd93c93db592c3cf89bf352af23687` on
  `feature/practice-reduce-scope`.
- The coordinator accepted the serial implementation/static cycle through
  `13F-04`. Its focused player/result fail-closed fix and final contract-only
  P2 closure received independent `ACCEPT_STATIC` verdicts.
  `13F-05` completed two focused rejection-fix cycles, and its final
  independent correctness and scope/side-effect re-audits both returned
  `ACCEPT_STATIC`. The initial `13F-06` static audits returned one
  `ACCEPT` and two `REJECT` verdicts. One grouped correction pass, tracked
  while active as `IMPLEMENTED_PENDING_STATIC_REAUDIT`, fixed every concrete
  finding. Final correctness, scope/side-effect and validation-readiness
  re-audits all returned `ACCEPT_STATIC_13F_06`.
- The consolidated validation then closed through grouped analyze-all/fix/rerun
  cycles. The final exact lifecycle passed both whitespace checks, JDK 17
  compile, all `331/331` selected tests with zero failures/errors/skips, the
  Flyway/schema proof `44/44/0/1`, trapped cleanup and the independent
  database-absence proof `0`.

The preparation entry was documentation/read-only audit only. The subsequent
`13F-01` and `13F-02` implementation/static cycles changed the production/test
files listed below, but did not run validation, use a
database/provider/browser, or perform any Git operation.

Phase 13F follows the `2026-07-25` commit-organization lock in
`CODEX_PRACTICE_WORKFLOW.md`: after the single green phase gate, the coordinator
may create several coherent, reviewable commits for this phase and then push
the complete series once. This does not permit commit/push between `13F-01..06`
and does not create an extra validation run per commit.

Final static review produced one independent `ACCEPT_STATIC` and one
`REJECT_STATIC` limited to the browser trend collision: the old map overwrote a
second same-skill point at the same timestamp. The concentrated fix replaced
that scalar map with chronological event slots, added a static UI contract and
updated this log. The rejecting auditor's requested rerun could not execute
because the delegated agent hit its usage limit. The coordinator inspected the
exact changed algorithm and test and accepts the blocker as resolved; this is
not represented as a second independent verdict and is not validation evidence.
No test/build was run to compensate for the agent quota.

## 2. Authority and exact purpose

This log consumes:

1. `CODEX_PRACTICE_WORKFLOW.md`;
2. `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
3. `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`;
4. `docs/PRACTICE_PHASE_13E_LIVE_CHANGE_LOG.md`;
5. `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
6. `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`;
7. current progress/profile/result/retry/recovery production source and tests.

Phase 13F establishes one honest, read-only Practice progress/profile contract:

- real all-time aggregates plus separately bounded recent detail;
- dimensions by skill and canonical immutable Writing task;
- nullable score facts with numerator, denominator, unit, profile/scale,
  availability and legacy/trust state kept separate;
- sample size, activity count, observation window, recency/as-of and evidence
  coverage accompanying every trend, comparison or insight;
- objective partial credit preserved as earned/possible evidence;
- filters, honest empty/error/retry states and real `Practice more` links;
- one resumability/state policy shared by catalog, detail and progress;
- bounded operational recovery that consumes existing canonical commands;
- no write, task creation or provider call caused by GET/refresh.

For 13F, `confidence context` means source facts only: sample size, recency,
eligible/evaluated/excluded coverage and authoritative stored confidence when
the owning typed contract supplies it. It does not authorize a synthetic
confidence percentage, threshold, grade or heat color.

Phase 13F must not:

- create decorative percentages, inferred proficiency levels or fake time;
- turn missing, pending, failed, unscorable or legacy-unverified evidence into
  zero;
- merge incomparable Writing tasks, maxima or profiles;
- assign one whole-attempt Writing score to every task;
- add any Speaking subtotal, holistic/attempt total, acoustic score, numeric
  trend, best/latest number or cross-skill average;
- reopen the exactly-three 13E Detail screen/presenter contract;
- use PREP/IELTS scoring, taxonomy, bands, denominators or product claims;
- implement Writing local 1-9 removal, text-simulated Speaking producer cleanup,
  direct-audio scoring, Phase 14 or Phase 15 release closure.

## 3. Historical pre-implementation ownership and blockers

This section records the `2026-07-24` baseline that justified `13F-01`. It is
not a statement of the current implementation. Sections 11 and 12 supersede
these findings and are the authority for the implemented-pending-validation
state.

| Surface | Baseline owner | Historical finding (superseded by 13F-01 unless explicitly deferred) |
| --- | --- | --- |
| Profile/progress route | `PracticeController.java:812-848` | `/practice/profile` intentionally redirects to student-only `/practice/progress`; page-data serialization failure becomes untyped `{}`. |
| Progress aggregate | `PracticeService.java:911-1431` | Overview, weekly metrics, trend, task/type rows, highlights and history are coupled in one large service. |
| Attempt reads | `PracticeAttemptRepository.java:33-38` | Progress uses only latest 100 attempts and has no real all-time aggregate projection. |
| DTO | `PracticeDtos.java:923-987` | No availability/sample/recency/coverage/profile fields; numeric primitives conflate no-data with zero. `LearningProfileView` at `855-861` is an unused duplicate DTO. |
| Progress UI | `progress.html`, `practice-progress.js`, `practice-progress.css` | Missing full filters/error/retry contract; several labels and charts overclaim the underlying data. |
| Attempt state | `PracticeCatalogService`, `PracticeDetailPageService`, `PracticeService` | Catalog/detail/progress interpret completion/resume separately from canonical exact-version reuse/discard logic. |
| R/L retry | `PracticeExplanationController`, `QuestionExplanationRetryService` | Existing command is terminal-failure-only, idempotent/rate-limited and set-level `PUBLISH` authorized; it is not a learner command. |
| Writing/Speaking re-evaluate | `PracticeController.java:584-602`, `PracticeService.java:270-359,3082-3359` | One generic owner POST can reach provider-capable work; Writing always shows “Chấm lại”, Speaking has no explicit retry UX policy. |
| Result/Detail | `PracticeController.java:554-581` and typed presenters | One overview and exactly three typed Detail screens; verify only in 13F. |

### Historical P0 correctness findings

1. `loadProgressAttemptData()` loads latest 100 non-discarded attempts and gives
   the same list to `allAttempts` and `recentAttempts`
   (`PracticeService.java:1352-1391`). Totals and “all/full” claims are therefore
   capped silently. At that baseline, `PracticeServiceTest.java:1489-1509`
   locked this misleading behavior and had to be replaced.
2. Writing Q51-Q54 rows each receive
   `getNormalizedAttemptScore(attempt)` (`PracticeService.java:1225-1266`).
   Multi-task attempts therefore repeat one whole-attempt number as every task
   score instead of using immutable task-native evidence.
3. Re-evaluation snapshots do not consistently reject in-progress/non-terminal
   state before provider-capable work; Speaking re-evaluation also does not use
   the submit path's ready-media gate. Any approved recovery command must fail
   before provider/media work for in-progress, discarded, foreign, stale or
   non-retryable attempts.

### Historical P1 misleading and duplicate semantics

- no scored sample becomes `0.0%`;
- “Xét 20 bài gần nhất” averages the shared top-100 snapshot;
- “Vững/Khá/Đang tiến bộ” is inferred from heterogeneous R/L/W scores without a
  named policy;
- attempt totals include in-progress rows while the UI says submitted;
- missing/invalid duration is invented as 30 minutes;
- missing weekly samples become score/delta zero;
- same-skill attempts in the same minute overwrite each other in browser trend
  grouping;
- “Lịch sử luyện tập đầy đủ” returns at most 30 rows;
- malformed/unscorable/legacy exclusions disappear without coverage reasons;
- history titles read mutable live set/test/section identity;
- catalog, detail and progress duplicate attempt-state/latest/best/resume logic;
- the catalog resume banner only sees the current 12-card batch;
- `MOST_STABLE` and `MOST_IMPROVED` are permanent no-data placeholders.

At that baseline, immutable question-version reads and objective partial-credit
scoring were correct foundations and had to be preserved.

## 4. Boundary classification

### `PHASE_13F_IMPLEMENTATION`

- real all-time counts and separately bounded recent detail;
- one typed aggregate contract with nullable score, unit/profile, sample,
  recency, coverage and exclusion reasons;
- skill and immutable Writing-task dimensions;
- objective partial credit;
- Speaking activity/profile coverage only, never a numeric aggregate;
- profile filters, empty/error/retry states and real deep links;
- shared read-only attempt-state/resumability semantics;
- terminal-state gates before provider-capable re-evaluate;
- lecturer/operator R/L recovery through the existing 13D command;
- the Speaking retry UX selected in Section 8.

### `VERIFY_ONLY_FROM_13E`

- one result overview and exactly three Detail screens: Objective R/L, Writing
  and Speaking;
- typed immutable Writing task/evidence and selector ownership;
- Speaking four independent language rows plus two null acoustic
  `NOT_SCORABLE` rows, with no `/70`, subtotal, aggregate, holistic or attempt
  score;
- immutable owned deep links and provider-free Result/Detail GET;
- accepted 13E `118/118` evidence is not rerun or relabelled by this phase.

### `PRE_PHASE_14_DEBT`

- COMP-18 Writing local 1-9 removal and profile decision;
- COMP-19 text/word-count simulated Speaking score producer removal;
- COMP-10 verified-dead generic Detail cluster;
- COMP-16 binding history plus PRE-02/04/05/09/14 and other target-stability,
  construct/profile/configuration/baseline work assigned by the inventory.

13F may label/exclude untrusted legacy evidence but may not normalize or remove
these producers.

### `PHASE_13G/13H`

- 13G: broad responsive, accessibility, reduced-motion, UTF-8/mojibake, icon and
  large-catalog/query/index sweep;
- 13H: browser/device/visual journeys, full failure/route closure, answer-leak
  audit and stabilization evidence;
- PRE-10 Writing HTTP timeouts, PRE-11 collaborator N+1, PRE-12 cleanup
  claim/lease and PRE-13 PDF AI double-submit/idempotency plus bounded crop
  semantics are assigned to 13H; pre-14 verifies their accepted evidence;
- COMP-20 unreachable Writing mock fallback may be removed in 13H.

### `POST_14/PRE_15`

- direct-audio/acoustic/holistic Speaking remains `NO-GO`;
- SME/calibration/fairness/repeatability and official-equivalence claims;
- retained-data/destructive cleanup, canonical UAT seed and final
  browser/device/provider/load/security/manual UAT.

Phase 14 remains Report an Error and must not start.

## 5. Implementation slices and dependencies

Every slice is a separate KSH Project task. No slice may run test, compile,
build, lint, startup, database/migration, provider/API, browser QA or Git
integration. The coordinator owns one final Phase 13F validation unit.

| Slice | Exact scope | Dependency/status |
| --- | --- | --- |
| `13F-01` | Extract one real read-only progress query/DTO boundary; separate all-time projections from bounded recent reads; add nullable sample/window/recency/coverage/unit/profile fields; remove top-100-as-total, fake 30 minutes, pseudo level and zero-as-no-data. | Independent of retry decision. `IMPLEMENTED_PENDING_PHASE_VALIDATION`. |
| `13F-02` | Immutable skill/Writing-task aggregation; objective partial credit; task/profile/max cohort separation; excluded/legacy/unscorable coverage; Speaking numeric aggregates absent. | After `13F-01`. `IMPLEMENTED_PENDING_PHASE_VALIDATION`. |
| `13F-03` | Profile filters, real table/chart fallback, exact sample/recency/coverage copy, complete empty/error/retry states and real practice-more links. | After `13F-02`. |
| `13F-04` | Shared attempt-state/resumability policy; global resume outside current catalog batch; invalid re-evaluate state rejected before provider/media work. | `IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`; after `13F-01..03`. |
| `13F-05` | Implement the chosen Speaking retry UX and lecturer/operator R/L failed-artifact recovery through the existing 13D service. | `IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`; after `13F-04`. |
| `13F-06` | Reconcile consumers/tests/docs, remove only superseded progress aggregation paths, record the narrowed final selector and candidate validation lifecycle; do not validate. | `READY_FOR_PHASE_VALIDATION`; all three final audit axes returned `ACCEPT_STATIC_13F_06`. |

```text
13F-01 -> 13F-02 -> 13F-03
    \----> 13F-04 -> 13F-05
13F-03 + 13F-05 -> 13F-06 -> one Phase 13F validation task
```

## 6. Planned file inventory

This is an ownership plan, not a production-change claim.

### `13F-01/02`

- new `src/main/java/com/ksh/features/practice/service/PracticeProgressService.java`
  or a strictly equivalent single owner;
- `PracticeAttemptRepository.java`, `PracticeDtos.java`, `PracticeService.java`
  only to delegate/remove its superseded progress assembler, and
  `PracticeController.java` only to consume the typed page contract;
- immutable question-version repositories, canonical objective scorer and typed
  Writing compatibility/evidence reader as read/reuse dependencies, not a
  second presentation or grading pipeline;
- new `PracticeProgressServiceTest.java`, existing `PracticeServiceTest.java`
  and selected `PracticeIntegrationTest` progress cases.

### `13F-03`

- `progress.html`, `practice-progress.js`, `practice-progress.css`;
- controller/DTO filter and page-error state;
- catalog query/repository/template only if a real server-owned task/type
  deep-link requires it; a fake query parameter is forbidden;
- wording/UI contract and selected integration tests.

### `13F-04/05`

- a new narrow `PracticeAttemptStatePolicy.java` or equivalent;
- `PracticeCatalogService`, `PracticeDetailPageService`,
  `PracticeProgressService`, the smallest shared DTO/UI consumers and tests;
- only after the Section 8 decision: `PracticeController`, `PracticeService`,
  the affected Writing/Speaking action surface, `PracticeExplanationController`,
  `QuestionExplanationRetryService`, an authorized operator presentation and
  their contract/integration tests;
- `SpeakingEvaluationReusePolicy` may be consumed, not replaced.

Verify-only: result assemblers, Objective/Writing/Speaking presenters, the
result shell and exactly three Detail templates. No SQL/Flyway file is approved.

## 7. Acceptance cases and deferred validation

Required cases:

1. no attempts renders unavailable score/delta/level/duration, not fake zero;
2. 101+ rows keep correct all-time counts and a separately labelled recent cap;
3. missing duration is excluded with coverage, not replaced by 30 minutes;
4. objective partial credit retains earned/possible and denominator;
5. multi-task Writing never repeats the attempt score per Q51-Q54;
6. Writing pending/failed/unavailable/legacy/incomparable cohorts never enter a
   numeric average as zero;
7. retained numeric Speaking never enters average/latest/best/delta/trend/type
   performance; only honest activity/profile coverage is exposed;
8. two same-minute attempts both survive trend rendering;
9. immutable identity survives live edits; incomplete locks are labelled;
10. filters/deep links round-trip to real authorized routes;
11. missing comparison samples produce unavailable delta, not “no change”;
12. resume/latest/best/completed/stale/discarded agree across consumers;
13. invalid re-evaluate state fails before provider/media work;
14. R/L retry preserves terminal-failure-only authorization/idempotency/cooldown;
15. progress/player/result/detail GET causes zero writes/provider calls;
16. exactly three typed Detail screens and all 13E Speaking/Writing guards remain;
17. page-data failure has explicit safe error/retry, not stale/fake charts;
18. no PREP/IELTS assessment semantics enter the product.

No validation ran. The deferred one-gate class selector inventory is:

```text
PracticeProgressServiceTest,
PracticeControllerProgressTest,
PracticeServiceTest,
PracticeDetailPageServiceTest,
PracticeCatalogServiceTest,
QuestionExplanationRetryServiceTest,
PracticeExplanationControllerTest,
SpeakingEvaluationReusePolicyTest,
PracticeResultPresenterTest,
PracticeResultDetailContractTest,
ObjectiveResultDetailTypeNativeContractTest,
PracticeFunctionalUiContractTest,
PracticeResultWordingTest,
PracticeIntegrationTest
```

Only after `13F-01..06`, full-log reconciliation and an accepting final
13F-06 static re-audit may the coordinator declare
`READY_FOR_PHASE_VALIDATION`, then run exactly one sequence:
`git diff --check` -> one JDK 17 compile -> the final focused selector ->
required integration cases. No full suite unless the final diff genuinely needs
it or the user requests it. Failures use one analyze-all/grouped-fix cycle, not
per-fix test loops.

## 8. One remaining product question

The only unresolved product question blocking the Speaking-retry part is:

> Should Phase 13F allow a learner to retry only terminal, retryable failed
> Speaking evaluation for the same immutable attempt while reusing every
> successful result, or keep Speaking submit-only and offer only honest
> failed/unavailable state plus “Luyện lại”?

Recommended answer: keep Speaking submit-only in 13F unless a separately
approved command, authorization, cost/idempotency and media policy is provided.
R/L explanation retry remains lecturer/operator `PUBLISH` authorized. Writing
local 1-9 and text-simulated Speaking remain pre-14 debt.

This question does not change the `13F-01` aggregate contract. After the user
answers, the coordinator may record the exact retry disposition and grant
implementation GO; this audit does not do so automatically.

## 9. Exact next-task prompt for `13F-01`

```text
KSH /practice — PHASE 13F / 13F-01 REAL PROGRESS AGGREGATION CONTRACT

Mode: IMPLEMENTATION UNIT; validation stays deferred to the Phase 13F
coordinator. Starting point reported by the coordinator:
feature/practice-reduce-scope /
93d87fd1f8dd93c93db592c3cf89bf352af23687.

Read completely: CODEX_PRACTICE_WORKFLOW.md;
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md;
PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md Section 11/13F;
docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md;
docs/PRACTICE_PHASE_13E_LIVE_CHANGE_LOG.md final contracts/evidence;
the Phase 15 inventory PRE-10..14 and COMP-05/10/18/19; and the KSH
language-assessment design. Then read current progress repository/DTO/service/
controller and matching tests. Update the 13F live log immediately for every
material finding or ownership change.

Implement only one canonical read-only progress query/DTO foundation. Extract
PracticeProgressService (or a strictly equivalent single owner); add real
all-time repository projections and separately bounded recent-detail reads;
introduce nullable typed facts for availability, numerator/denominator/unit/
profile, sample size, activity count, observation window/as-of/last-observed,
coverage and legacy/exclusion reason.

Remove top-100-as-all-time semantics, fake 30-minute duration, pseudo proficiency
levels and zero-as-no-data. Distinguish total, completed and in-progress counts
and label every bounded window honestly. Delegate/remove only the superseded
progress assembler after call-site review.

Preserve immutable attempt/version identity, Objective partial credit and the
exact three 13E Detail screens. Provide the typed seam for later Q51-Q54
aggregation but do not implement 13F-02. Current Speaking exposes no normalized
score, delta, trend, best/latest, subtotal, holistic/attempt total or acoustic
score. No GET may write or call a provider. PREP is UI/IA reference only.

Out of scope: progress UI redesign; practice-more filters; Writing task
aggregation; attempt-state/retry UX; the unresolved Speaking retry choice;
Writing local 1-9; text-simulated Speaking cleanup; direct audio; 13G/13H;
Phase 14; SQL/Flyway/database/fixture work.

Add/update contract tests for 101-row all-time versus bounded recent, no-data
nullability, missing duration and Speaking guards. Do not run tests, compile,
build, lint, startup, Docker/frontend build, migration checks, browser QA,
provider/API calls or git diff --check. Do not stage, commit, pull, push, merge
or rebase.

Handoff: update the live log; report exact files changed, the single aggregate
owner, removed/delegated duplicate paths, query/window semantics, exclusions
and exact deferred selectors. Mark only 13F-01 implemented-pending-phase-
validation. Do not call it validated and do not start 13F-02.
```

## 10. Preparation ledger

| Date | Event | Evidence/decision | Mutation |
| --- | --- | --- | --- |
| 2026-07-24 | Phase 13F gate/scope preparation | Mandatory docs, current source and tests audited; boundary, slices, files, acceptance, selector, independent 13F-01 and one Speaking-retry question locked. | Documentation only; no production/test/SQL, validation, Git, DB, provider, browser or startup action. |

## 11. 13F-01 implementation ledger

| Date | Event | Evidence/decision | Mutation |
| --- | --- | --- | --- |
| 2026-07-25 | Implementation GO consumed | Direct coordinator prompt authorizes only `13F-01`; all validation and Git integration remain deferred. | Status only. |
| 2026-07-25 | Canonical owner and caller trace locked | `PracticeProgressService` is the single read-only aggregate owner. The only production caller of `PracticeService.getProgressPageData(...)` is `PracticeController`; `LearningProfileView` has no caller. | Ownership recorded before source changes. |
| 2026-07-25 | Query/window contract locked | Repository all-time projections own total/completed/in-progress/activity, valid-duration coverage and Objective earned/possible. A separate recent-detail read is explicitly capped at 100 and reports returned count/truncation/as-of/last-observed. | No SQL/Flyway/schema/fixture mutation. |
| 2026-07-25 | Compatibility boundary locked | Recent history resolves immutable set/test/section version identity. Incomplete/legacy locks are labelled and excluded where evidence requires a lock; they never fall back to mutable live titles/questions. Q51-Q54 receive typed deferred seams only. Speaking exposes activity/profile/coverage and no numeric aggregate. | 13F-02/03/04/05 and COMP-05/10/18/19 remain out of scope. |
| 2026-07-25 | Canonical owner implemented | `PracticeProgressService` now owns the page aggregate. `PracticeController` consumes it; the 517-line superseded progress assembler and entrypoint were removed from `PracticeService`, and the unused `LearningProfileView` DTO was removed. | Production and static test-contract source only. |
| 2026-07-25 | Static acceptance contracts written | Contracts cover zero-attempt nullability, 101 all-time versus recent-100 truncation, invalid-duration exclusion, Objective earned/possible, Speaking numeric absence, immutable identity/incomplete-lock reasons, Q51-Q54 deferred seams and typed serialization failure. | Tests were written/read only and were not run. |
| 2026-07-25 | Independent static acceptance returned `REJECT_STATIC` | No P0. Required P1: coherent immutable chain for every Objective number, nullable recent duration and minimal current-template null compatibility. Required P2: canonical activity order, per-skill windows, dead top-100 removal and executable native/controller contracts. | One focused `13F-01` fix-cycle opened; no validation or Git action. |
| 2026-07-25 | Focused static-rejection fix-cycle completed | All-time Objective eligibility now joins the exact published/set/test/section chain; recent eligibility reuses the same typed identity verification as history. Heatmap duration is nullable with per-day coverage. Recent order uses submitted/updated/created activity. Skill windows use row-local observations. Behavioral native-query and ObjectMapper-failure contracts were added. | `13F-01` remains `IMPLEMENTED_PENDING_PHASE_VALIDATION`; independent static re-acceptance requested. |
| 2026-07-25 | Two independent re-audits remained `REJECT_STATIC` | Required final closures were duration-domain coverage reconciliation, visible typed page-failure presentation, independent weekly per-skill windows, removal of the last dead created-at top-100 path, honest bounded-source copy/typed history state and complete last-resort unavailable JSON. | Exactly one final concentrated `13F-01` fix-cycle opened; no 13F-02, validation or Git action. |
| 2026-07-25 | Final concentrated static-rejection fix-cycle completed | Heatmap coverage now partitions every displayed activity into valid duration, completed-invalid duration or incomplete/non-completed duration-not-applicable. Weekly skill metrics own separate bounded windows while preserving the global recent-source truncation/as-of context. Both page failure reasons render an explicit Vietnamese reload state, history uses typed presentation, bounded copy is exact, the dead derived query is gone and the independent fallback mapper serializes the complete unavailable DTO graph. | `13F-01` remains `IMPLEMENTED_PENDING_PHASE_VALIDATION`; independent re-acceptance is required and requested. |
| 2026-07-25 | Final audit reconciliation completed as one static fix-cycle | Weekly UI and observation labels now state that the seven-day view is evaluated only inside the bounded recent-detail last-100 source. Writing/Speaking aggregate, weekly and history coverage now partitions every completed and incomplete activity, with incomplete rows typed as `SCORE_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY`; Speaking remains numberless. The serialization contract uses Java-time Jackson support and asserts a non-null `LocalDateTime`. The last truly uncalled created-at repository method was removed, and Section 3 was relabelled as historical baseline rather than current blockers. | Source and contracts were inspected statically only. `13F-01` remains `IMPLEMENTED_PENDING_PHASE_VALIDATION`; Q51-Q54 aggregation remains deferred to `13F-02`. |
| 2026-07-25 | Final score-trend collision closed | The renderer no longer indexes a point only by timestamp and skill. It builds chronologically stable event slots keyed by timestamp plus same-skill occurrence, so two points for the same skill at the same timestamp remain distinct while first occurrences of different skills can share the same time slot. A static UI contract rejects the former overwrite path and locks the occurrence/event-key construction. | Minimal `practice-progress.js`, UI contract and live-log edits only; no validation or Git action. `13F-01` remains `IMPLEMENTED_PENDING_PHASE_VALIDATION`. |

Exact files changed by this focused fix-cycle:

```text
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/resources/static/js/practice-progress.js
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Exact files changed by the final concentrated fix-cycle:

```text
src/main/java/com/ksh/features/practice/controller/PracticeController.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Exact files changed by the final audit reconciliation cycle:

```text
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Exact files changed by the final score-trend collision fix:

```text
src/main/resources/static/js/practice-progress.js
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

The shared-worktree edits in `CODEX_PRACTICE_WORKFLOW.md`,
`PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md` and
`docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md` belong to the coordinator
and are deliberately preserved without reconciliation by `13F-01`.

## 12. 13F-01 implemented contract and deferred validation

Canonical query/window semantics:

- `findProgressAllTime(...)` returns true all-time non-discarded total,
  completed, in-progress and other counts plus valid-duration coverage.
- Duration is eligible only for completed activity with a measured elapsed
  value from 1 through 239 minutes. Missing, non-positive or four-hour-plus
  evidence is excluded as `MISSING_OR_INVALID_DURATION`; no 30-minute value is
  invented.
- `findProgressAllTimeBySkill(...)` aggregates Objective
  `earned_points / total_points` only for completed, fully version-locked
  Reading/Listening evidence with the canonical `EARNED_POINTS` unit. Its
  left-join eligibility proves one coherent
  published-version -> set-version -> test-version -> section-version chain
  and exact set/test/section identity; merely non-null IDs are insufficient.
  Numerator and denominator remain present in `ProgressNumericFact`.
- `findRecentProgressAttempts(...)` is a separate read ordered by
  canonical activity
  `COALESCE(submittedAt, updatedAt, createdAt) DESC, id DESC`, capped at 100 with
  `RECENT_DETAIL_LAST_100`, returned count, truncation, observed-from/to,
  `asOf` and `lastObservedAt`. History is presentation-bounded to 30 and the
  overview subset to 8; neither defines an all-time total.
- Both obsolete created-at-only derived recent paths were removed after a
  complete static caller scan. `findRecentProgressAttempts(...)` is the only
  progress recent-detail query.
- Recent Objective eligibility uses the exact same immutable set/test/section
  chain verification as history identity. A row labelled
  `LEGACY_UNVERIFIED` cannot enter earned/possible, average, trend or history
  score.
- Each all-time skill metric owns a separate window populated from that skill
  projection's `observedFrom`/`observedTo`/database `asOf`, with its own
  `lastObservedAt`; it does not inherit another skill's global range.
- Weekly metrics use the current seven days inside that explicitly bounded
  recent source. After skill filtering, each skill owns a separate bounded
  `CURRENT_7_DAYS_<SKILL>_WITHIN_RECENT_DETAIL_LAST_100` window with its own
  returned count, observed-from/to and last-observed value; the global source's
  `truncated` and `asOf` context is retained. Comparison/delta remains typed
  unavailable until comparable evidence exists; missing samples are never
  serialized as zero/no-change. The visible heading, explanatory copy, card
  footer and typed observation-window label all say this is a seven-day window
  within the bounded recent-detail last-100 source, not an unqualified complete
  weekly history.

Nullable/coverage/exclusion semantics:

- level, duration, recent score and delta use typed availability and nullable
  values; no pseudo proficiency level remains;
- each heatmap day has nullable `totalMinutes` plus duration coverage satisfying
  `activityCount = eligibleCount + excludedCount`. Completed invalid evidence
  is excluded as `MISSING_OR_INVALID_DURATION`; in-progress/other activity is
  excluded as `DURATION_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY`. A day without
  any valid duration serializes `null`, never zero minutes, and exclusion
  reason counts sum to `excludedCount`;
- `ProgressAttemptCounts` keeps total/completed/in-progress/other separate;
- Writing and Speaking score coverage satisfies
  `activityCount = eligibleCount + excludedCount` in all-time skill metrics,
  weekly skill metrics and individual history rows. Completed Writing/Speaking
  activities carry their skill-specific deferred/not-scorable reason, while
  incomplete activities carry
  `SCORE_NOT_APPLICABLE_FOR_INCOMPLETE_ACTIVITY`. Speaking still exposes no
  numeric value, numerator, denominator, normalized score, delta or trend;
- every numeric fact carries value, numerator, denominator, unit, profile,
  sample size, activity count, observation window and coverage;
- immutable recent identity carries published/set/test/section version IDs and
  resolves titles only from version tables. Incomplete/mismatched retained data
  receives `INCOMPLETE_VERSION_LOCK` or `LEGACY_UNVERIFIED` and never reads a
  mutable live title;
- Q51-Q54 are typed `DEFERRED` seams with
  `WRITING_TASK_AGGREGATION_DEFERRED_13F_02`; no Writing task score was
  implemented;
- Speaking is `NOT_SCORABLE` for numeric aggregation and exposes activity,
  profile and coverage only. Stored legacy numbers cannot enter a score,
  latest/best, delta, trend, task/type row, subtotal, holistic/attempt total or
  acoustic aggregate;
- page-data and JSON failure replace the complete model/JSON payload with a
  typed unavailable state and empty series, never `{}` or a previously
  serialized chart snapshot. `progress.html` consumes availability, reason and
  `RELOAD` retry hint, shows explicit Vietnamese unavailable/reload UI for both
  `PAGE_DATA_UNAVAILABLE` and `SERIALIZATION_UNAVAILABLE`, and suppresses the
  normal dashboard while unavailable. Injected mapper failure falls back to an
  independent deterministic mapper over the canonical unavailable DTO, keeping
  both windows and every numeric fact's complete metadata/coverage shape.
  Service serialization tests use registered Java-time Jackson support with
  string dates and assert the non-null `recentDetailWindow.asOf` value rather
  than avoiding the production `LocalDateTime` contract.
- trend copy states that its eligible Objective points come from at most 100
  recent activities; history labels state their actual maxima of 8 and 30; the
  heatmap states that its 12-week display is derived from the bounded recent-100
  source. Both history blocks present typed Vietnamese state for in-progress,
  Speaking-not-holistically-scorable, Writing-task-deferred and
  Objective-unavailable rows; earned/possible is rendered only when both values
  and the typed available score fact are present.
- score-trend points are stored as a stable chronological event-slot array. The
  event key is timestamp plus the zero-based occurrence of that timestamp for
  the same skill, so repeated same-skill/same-timestamp observations cannot
  overwrite one another; a different skill's matching occurrence may still
  share the same x-axis slot.

Exact files owned by this slice:

```text
src/main/java/com/ksh/features/practice/controller/PracticeController.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/java/com/ksh/features/practice/service/PracticeService.java
src/main/java/com/ksh/features/practice/web/PracticeModelAttributes.java
src/main/resources/static/js/practice-progress.js
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

`progress.html` and `practice-progress.js` received only the minimal
compatibility required for the typed/null contract: explicit unavailable/reload
presentation, honest bounded-source and total-activity wording, typed history
state and a null-duration tooltip. Redesign, filters/deep links and all other
13F-03 work remain deferred. No progress CSS, SQL/Flyway/schema/seed/fixture,
provider, retry/re-evaluate/recovery, Result/Detail presenter or route was
changed.

The exact `13F-01` selector contribution deferred to the one Phase 13F
validation unit is:

```text
PracticeProgressServiceTest,
PracticeControllerProgressTest,
PracticeServiceTest,
PracticeFunctionalUiContractTest,
PracticeResultPresenterTest,
PracticeResultDetailContractTest,
ObjectiveResultDetailTypeNativeContractTest,
PracticeResultWordingTest,
PracticeIntegrationTest
```

It has not been run. No compile, build, lint, startup, database/migration,
browser, provider/API, `git diff --check` or Git mutation was performed.
At this historical `13F-01` handoff snapshot, `13F-02` had not started.

## 13. Out-of-slice toolchain/security observation (`2026-07-25`)

During the pending 13F-01 static handoff, IntelliJ reported
`ExceptionInInitializerError` and `TypeTag.UNKNOWN`. Read-only inspection found:

- `pom.xml` declares Java 17;
- tracked `.idea/misc.xml` selects `openjdk-26`;
- tracked `.idea/compiler.xml` pins Lombok `1.18.36` as an annotation processor
  and still names stale module `ulp`;
- Homebrew JDK `17.0.19` is installed and is the toolchain used by accepted
  recent phase gates;
- IntelliJ/Mend separately reports repeated direct/transitive dependency
  advisories. These are security findings, not 57 independent Maven XML or
  compile failures.

No IDE/POM/dependency change belongs to 13F-01 and none was made from this
observation. The coordinator added `P15-PRE-15` reproducible JDK 17 and
`P15-PRE-16` dependency-security baseline/release-rescan to the inventory.
Implementation belongs to `13H-TOOLCHAIN-01` and `13H-SEC-01`; the pre-14 gate
verifies accepted evidence and post-14/pre-15 reruns the time-sensitive scan.

## 14. 13F-02 implementation ledger

| Date | Event | Evidence/decision | Mutation |
| --- | --- | --- | --- |
| 2026-07-25 | Implementation GO consumed | Direct coordinator prompt authorizes only `13F-02`; all validation and Git integration remain deferred. The complete mandatory workflow, blueprint, Phase 13 gate, this log, Phase 15 inventory, language-assessment design, current 13F-01 diff and relevant Writing/progress production/tests were read before source mutation. | Status and audit record only. |
| 2026-07-25 | Immutable Writing evidence boundary locked | A Writing attempt stores per-question evaluator entries under immutable live question IDs in `ai_feedback_json`; canonical task identity and configured maximum come from `practice_question_versions.writing_task_type` and `points` reached through the attempt's exact published/set/test/section version lock. Whole-attempt `score`/`earned_points` is not Writing task evidence and will not be reused for Q51-Q54. | `PracticeProgressService` will remain the only read-only aggregate owner and will consume the existing question-version repository plus typed Writing compatibility reader. No grading/result/detail pipeline or SQL/Flyway change is approved. |
| 2026-07-25 | Writing cohort identity and exclusion policy locked | Current stored attempt feedback has an explicit `scoring_contract` and engine but no persisted `AssessmentPolicyBundle` ID. Eligible current evidence therefore requires canonical immutable task agreement, current `TASK_NATIVE_RUBRIC_V1`, explicit engine, score-bearing current status/source, in-range raw earned/maximum and agreement between feedback maximum and immutable question-version points. Cohorts split by task + explicit scoring-contract/engine identity + maximum. Missing/legacy/mock/unavailable/malformed/mismatched evidence is excluded with a typed reason; a missing bundle ID is never guessed or synthesized. | Writing skill remains deliberately numberless because Q51-Q54/profile/max cohorts are not one comparable scale. Numeric numerator/denominator/value exist only inside task cohorts. PRE_PHASE_14 still owns canonical bundle persistence and Writing local 1-9/profile cleanup. |
| 2026-07-25 | Coverage and Speaking boundary locked | Overall Writing attempt coverage and per-task evidence coverage will separately expose eligible and excluded counts/reasons; incomplete activity remains non-score-bearing rather than zero. Objective aggregation retains earned/possible partial credit unchanged. Speaking remains activity/profile/coverage-only and absent from every numeric aggregate, history score, delta, trend, latest/best and type-performance path. | `13F-03+`, 13C3, retry/recovery, direct audio, Phase 14/15 and UI redesign remain untouched. |
| 2026-07-25 | DTO/reader/repository seams implemented | `PracticeDtos` now carries typed Writing exclusion reasons, per-task score cohorts and overall Writing-attempt coverage. `WritingFeedbackCompatibilityReader` exposes stored scoring-contract/bundle identity without changing grading arithmetic. Read-only repositories expose all non-discarded Writing attempts and immutable question versions by locked section version. | Material files: `PracticeDtos.java`, `WritingEvaluationResult.java`, `WritingFeedbackCompatibilityReader.java`, `PracticeAttemptRepository.java`, `PracticeQuestionVersionRepository.java`. No query was executed and no schema or persisted feedback format was changed. |
| 2026-07-25 | Canonical aggregation implemented | `PracticeProgressService` now reads all non-discarded Writing attempts, proves the complete immutable version chain, resolves only locked question versions, parses only the matching per-question feedback entry and partitions Q51-Q54 by explicit profile/bundle/max cohort. Each cohort carries nullable value plus raw earned/possible, percentage unit, profile, sample/activity, all-time window and coverage. The Writing skill/history facts remain numberless with `WRITING_SKILL_AGGREGATION_REQUIRES_TASK_COHORT`; no whole-attempt score is consumed. | Material production file: `PracticeProgressService.java`. Objective paths were not changed. Speaking remains excluded from recent average, trend, type performance and history numbers. |
| 2026-07-25 | Static contract fixtures written | Focused tests now lock per-question Q51/Q53 evidence versus an intentionally different whole-attempt score, cohort separation by profile/bundle/maximum, typed mismatch/unavailable/legacy exclusions, empty-null semantics, overall Writing attempt coverage and persisted scoring identity parsing. The existing Speaking numberless and Objective partial-credit contracts remain in the selector. | Material tests: `PracticeProgressServiceTest.java`, `WritingFeedbackCompatibilityReaderTest.java`, `PracticeControllerProgressTest.java`. Tests were edited/read only and were not executed. |
| 2026-07-25 | Minimal current-template compatibility updated | Completed Writing history no longer claims 13F-02 is deferred; it states that Writing scores are separated by task. This does not render the new cohorts, add filters/deep links or redesign the page; those remain 13F-03. | Material compatibility files: `progress.html`, `PracticeFunctionalUiContractTest.java`, `PracticeResultWordingTest.java`. |
| 2026-07-25 | Static signature/constructor audit closed | All `PracticeAnalytics`, `WritingTaskProgressSeam`, `WritingTaskScoreCohort` and `WritingEvaluationResult` construction sites were reconciled with the extended DTO/reader contract. Static scans found no production use of a whole-attempt Writing score in the task aggregation path and no active reference to the superseded 13F-02 deferred reason/copy. The remaining `DEFERRED` enum member and historical 13F-01 log text are compatibility/history, not an active Writing task result. | No concrete static blocker remains. No test, compile, build, lint, startup, database, provider, browser, `git diff --check` or Git operation was performed. `13F-02 = IMPLEMENTED_PENDING_PHASE_VALIDATION`. |

## 15. 13F-02 implemented contract and deferred validation

Implemented contract:

- `PracticeProgressService` remains the single read-only aggregate owner. It
  loads non-discarded Writing attempts, proves their immutable
  published/set/test/section version chain, and reads only question-version
  task/maximum plus the matching per-question stored feedback entry.
- Whole-attempt Writing score/earned/possible compatibility fields never become
  Q51/Q52/Q53/Q54 scores. Writing skill, weekly and history numeric facts remain
  `NOT_SCORABLE`; task-native numbers exist only in explicit task cohorts.
- Eligible cohorts split by immutable Writing task, explicit
  scoring-contract/engine profile, optional persisted policy-bundle identity
  and immutable maximum. Each cohort carries value, raw numerator/denominator,
  percentage unit, profile, sample/activity counts, all-time observation window
  and coverage.
- Unknown, incomplete, mismatched, missing, malformed, non-score-bearing,
  legacy, unsupported-profile and maximum-mismatch evidence is excluded with a
  typed reason. It is never guessed from mutable live content and never
  normalized to zero. Overall attempt coverage and per-task evidence coverage
  expose eligible/excluded partitions separately.
- Objective Reading/Listening aggregation remains the 13F-01
  earned-over-possible partial-credit contract. Speaking remains activity,
  profile and coverage only and cannot enter numeric aggregate, history,
  latest/best, delta, trend or type-performance output.
- Progress GET/render remains read-only and provider-free. Existing accepted
  13E Result/Detail DTO/presenter contracts were not reopened.

Exact files changed by `13F-02`:

```text
src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationResult.java
src/main/java/com/ksh/features/practice/ai/writing/WritingFeedbackCompatibilityReader.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/repository/PracticeQuestionVersionRepository.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/ai/writing/WritingFeedbackCompatibilityReaderTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Deferred scope/debt:

- 13F-03 owns rendering/filtering task cohorts, table/chart fallback, exact
  coverage copy and real deep links. This slice exposes DTO seams only.
- 13F-04 owns shared attempt-state/re-evaluation gates; 13F-05 owns
  retry/recovery; 13F-06 owns final reconciliation and removal of superseded
  compatibility paths.
- 13G owns the large-history query/index sweep. PRE_PHASE_14 still owns
  canonical policy-bundle persistence and Writing local 1-9/profile cleanup.
  13C3 Speaking authoring, direct-audio scoring and Phase 14/15 remain outside
  this slice.

The exact `13F-02` selector contribution deferred to the single Phase 13F
validation unit is:

```text
PracticeProgressServiceTest,
WritingFeedbackCompatibilityReaderTest,
WritingTaskNativeScoringTest,
PracticeControllerProgressTest,
PracticeServiceTest,
PracticeFunctionalUiContractTest,
PracticeResultPresenterTest,
PracticeResultWordingTest,
PracticeIntegrationTest
```

This selector has not been run. No validation claim is made. The subsequent
scope-audit rejection and concentrated fix in Section 16 supersede the initial
static-blocker statement from this handoff.

## 16. 13F-02 concentrated static-rejection fix cycle

Two independent static findings were received after the implementation handoff:

- one production-logic audit returned `ACCEPT_STATIC`;
- one scope/contract audit returned `REJECT_STATIC`. It found no production
  arithmetic defect, but rejected the slice because
  `PracticeProgressServiceTest` did not explicitly cover the Writing
  missing/malformed/unsupported-profile/maximum-mismatch/incomplete-task and
  absent-policy-bundle branches, and because the current Phase 13 gate and
  blueprint still described 13F-02 as the next slice.

The concentrated fix is limited to focused test contracts and the three
authoritative status documents. Production code is frozen unless constructing
the required fixtures exposes a concrete signature defect; no such defect is
currently known. `13F-03` must not start until this fix is handed back and the
coordinator accepts an independent re-audit. This entry records the open fix
cycle only and does not claim re-acceptance or validation.

Concentrated fix completed:

- one parameterized `PracticeProgressServiceTest` contract now covers
  `WRITING_SCORE_EVIDENCE_MISSING`,
  `WRITING_SCORE_EVIDENCE_MALFORMED`,
  `WRITING_SCORING_PROFILE_UNSUPPORTED`,
  `WRITING_MAXIMUM_MISMATCH` and
  `WRITING_TASK_IDENTITY_MISSING` from incomplete stored task evidence;
- a separate immutable-question fixture proves that a missing
  `PracticeQuestionVersion.writingTaskType` is excluded at overall Writing
  attempt coverage without guessing Q51 from stored feedback; the existing
  incomplete-version-lock fixture remains intact;
- every excluded parameterized case asserts an unavailable task seam, no cohort
  creation, zero eligible coverage and nullable Writing value/numerator/
  denominator rather than a normalized numeric zero;
- the eligible no-bundle fixture now explicitly asserts
  `policyBundleId == null`, the scoring profile contains only the actual
  contract/engine identity, and the cohort ID contains no synthesized
  `BUNDLE=` component;
- the Phase 13 gate and execution blueprint now describe 13F-02 as implemented
  with this static fix cycle pending phase validation/re-audit. They identify
  13F-03 as the next slice only after coordinator acceptance.

Exact files changed by this concentrated fix:

```text
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md
docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Static construction of the focused fixtures found no production signature
mistake, so no production file was changed. Tests were not executed. No compile,
build, lint, application, database/migration, provider/API, browser,
`git diff --check` or Git operation was performed. The fix is ready to hand
back for independent static re-audit; it is not validation.

Independent re-audit disposition:

- the correctness re-audit returned `ACCEPT_STATIC`: the new fixtures are
  statically constructible and lock typed exclusions, no cohorts, nullable
  rather than zero score facts, and absent bundle identity;
- the scope re-audit returned `ACCEPT_STATIC`: the earlier P1/P2 findings are
  closed, the fix changed only one test plus the three status documents, and no
  production, validation or Git boundary was crossed.

Coordinator disposition:
`13F-02 = IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`. This remains
static evidence only. `13F-03` is the next approved implementation slice; all
tests/build/Git stay deferred to the single Phase 13F gate.

## 17. 13F-03 implementation ledger

| Date | Event | Evidence/decision | Mutation |
| --- | --- | --- | --- |
| 2026-07-25 | Implementation GO consumed | The coordinator prompt authorizes only the Progress presentation/filter/fallback slice and the smallest real Writing-task catalog deep-link. `PracticeProgressService` arithmetic/aggregation, Result/Detail, evaluator/grading/evidence, SQL/Flyway and 13F-04+ remain outside the slice. Validation and every Git mutation remain deferred. | Status/log entry only before production mutation. |
| 2026-07-25 | Typed normalized Progress query locked | Canonical route is `GET /practice/progress?tab=<overview|test-practice>&skill=<ALL|READING|LISTENING|WRITING|SPEAKING>&writingTask=<ALL|Q51|Q52|Q53|Q54>&profile=<ALL|canonical-cohort-id>`. `writingTask` and `profile` are effective only with `skill=WRITING`; profile IDs are accepted only when they equal a backend-generated cohort option in the selected canonical task facts. Unknown, unauthorized or inapplicable values normalize to typed `ALL`; raw request strings are never echoed. Tab/reload/clear/retry URLs preserve only this normalized state. | Planned DTO/controller/template/JS contract; no aggregation or scoring arithmetic moves out of `PracticeProgressService`. |
| 2026-07-25 | Filter-support boundary locked | Skill filters may select skill metrics, weekly facts, Objective trends/type rows and skill-bearing recent history. Writing task/profile filters may select only `WritingTaskProgressSeam`/`WritingTaskScoreCohort`. The global summary and heatmap remain explicitly global because their canonical facts do not carry every requested dimension; the page must say that those sections are not task/profile-filtered. | Prevents a false claim that heatmap/global activity is filtered on unsupported dimensions. |
| 2026-07-25 | Honest progressive-enhancement contract locked | Radar, activity distribution, score trend and heatmap each keep a server-rendered semantic table/card sourced from the same DTO. Radar includes only eligible Reading/Listening facts and excludes Writing, Speaking and every unavailable/deferred/not-scorable value. Canvas is hidden until Chart.js successfully renders; CDN/JS/chart failure leaves the canonical fallback visible and exposes typed Vietnamese/Korean enhancement-failure copy without replacing page data. Browser code may select/format DTO fields but may not calculate score, average, cohort, confidence, trend or coverage. | Planned `progress.html`, `practice-progress.js` and `practice-progress.css` changes. |
| 2026-07-25 | State/copy contract locked | The presentation distinguishes: global no-attempt; activity exists but the normalized filter has no supported data; one fact/section is unavailable, deferred or not scorable; `PAGE_DATA_UNAVAILABLE`; `SERIALIZATION_UNAVAILABLE`; and chart enhancement failure with fallback still usable. Every numeric fact exposes available sample/activity, eligible/evaluated/excluded coverage, localized exclusion reasons, observed-from/to, as-of/last-observed, source cap and truncation. “Độ tin cậy” is used only as a source-fact summary and never as a percentage, grade, color or quality claim. | Planned server-rendered Vietnamese/Korean copy and exact wording/static contracts. |
| 2026-07-25 | Real Writing practice-more route locked | Canonical deep link is `/practice?skill=WRITING&writingTask=<Q51|Q52|Q53|Q54>`. `writingTask` must travel initial controller query -> typed `PracticeCatalogQuery` normalization -> `PracticeCatalogService` -> `PracticeSetRepository` learner-visible/search/class/paging query -> `PracticeCatalogBatch` -> catalog forms/links/lazy-load URL. Repository filtering uses canonical `PracticeQuestion.writingTaskType`; `q=Q51`, cosmetic parameters and client-only filtering are forbidden. Other skills use only their real `/practice?skill=<canonical-skill>` link. | Planned catalog controller/DTO/service/repository/template/JS-preservation tests; authorization, search and pagination predicates remain conjunctive. |
| 2026-07-25 | Typed Progress presentation implemented | `PracticeController` now creates a backend-owned `ProgressFilterState`, normalizes tab/skill/task/profile, accepts profile only from the selected canonical cohort options, filters DTO collections without recomputing arithmetic, and preserves normalized state through tab/reload/retry including serialization failure. The template uses only normalized enum/cohort values; raw request strings are never rendered. | Material production files: `PracticeController.java`, `PracticeDtos.java`, `PracticeModelAttributes.java`, `progress.html`. `PracticeProgressService` was not changed. |
| 2026-07-25 | Honest fallback/copy/state layer implemented | Radar, activity distribution, heatmap and Objective trend now have server-rendered semantic tables/cards. Canvas/heatmap visuals stay hidden until enhancement succeeds; CDN, JavaScript or renderer failure leaves the fallback visible and reports `CHART_ENHANCEMENT_UNAVAILABLE`. Vietnamese/Korean source-fact fragments expose sample/activity, eligible/excluded partitions, every typed exclusion reason, observed-from/to, as-of/last-observed, source cap/returned/truncation and a non-numeric confidence explanation. Global no-attempt, filter-empty, section unavailable/not-scorable/deferred, page-data failure, serialization failure and chart-enhancement failure remain distinct. | Material production files: `progress.html`, `fragments/progress-facts.html`, `practice-progress.js`, `practice-progress.css`. No stale snapshot, fake series, null-to-zero conversion or browser-owned scoring/average/cohort/confidence/coverage arithmetic was added. |
| 2026-07-25 | Skill-native presentation guards implemented | Objective partial-credit facts remain earned/possible and unavailable facts stay nonnumeric. Q51-Q54 render separately; profile/policy/maximum cohorts use learner-facing group labels while raw profile/bundle/cohort IDs stay in supporting details. Writing cohorts never enter the radar or a skill-average claim. Speaking renders activity/profile/coverage only and remains absent from score/latest/best/delta/trend/type/subtotal/holistic/acoustic/cross-skill numeric paths. Same-timestamp trend events remain distinct in the enhancement. | Presentation-only mutation; Result/Detail and evaluator/grading/evidence pipelines were not touched. |
| 2026-07-25 | Real Writing catalog vertical slice implemented | Initial and lazy catalog routes accept `writingTask`; `PracticeCatalogService` normalizes only Q51-Q54 and makes the dimension effective only with `skill=WRITING`; `PracticeSetRepository` requires an existing canonical `PracticeQuestion.writingTaskType` while retaining published status, learner ownership/global/class visibility, selected class, search, skill and paging predicates. `PracticeCatalogBatch`, search/class forms, skill/task links and existing `URLSearchParams(window.location.search)` lazy loading retain the normalized task. | Material production files: `PracticeController.java`, `PracticeDtos.java`, `PracticeCatalogService.java`, `PracticeSetRepository.java`, `index.html`. The catalog JavaScript owner required no edit because it already round-trips the complete normalized query string. |
| 2026-07-25 | Static contracts written and scope closed | Controller tests cover unknown normalization, canonical cohort acceptance, filter-empty state and serialization retry state. Catalog service/repository/integration tests cover real task filtering with authorization/search/paging and initial/lazy round-trip. UI/wording contracts cover semantic fallbacks, no-null-as-zero/no-fake-link guards, bilingual source facts, chart failure, numberless Speaking scoring and repeated same-timestamp trend slots. Static constructor/signature/template-route scans found no concrete blocker. | Tests were edited/read only and not run. No compile, build, lint, startup, database/migration, provider/API, browser, `git diff --check` or Git mutation/integration was performed. `13F-03 = IMPLEMENTED_PENDING_PHASE_VALIDATION`; `13F-04` was not started. |

## 18. 13F-03 deferred selector contribution

No validation has run. The current exact selector contribution planned for the
single Phase 13F validation unit is:

```text
PracticeControllerProgressTest,
PracticeCatalogServiceTest,
PracticeSetRepositoryTest,
PracticeFunctionalUiContractTest,
PracticeResultWordingTest,
PracticeIntegrationTest
```

This selector may be narrowed or extended only if the completed static diff
introduces another real boundary. No compile, build, lint, startup,
database/migration, browser, provider/API, `git diff --check` or Git operation
is authorized in this implementation unit.

## 19. 13F-03 implemented contract and handoff

Normalized Progress route:

```text
GET /practice/progress
  ?tab=<overview|test-practice>
  &skill=<ALL|READING|LISTENING|WRITING|SPEAKING>
  &writingTask=<ALL|Q51|Q52|Q53|Q54>
  &profile=<ALL|canonical-cohort-id>
```

`writingTask` and `profile` are effective only for Writing. Unknown or
inapplicable enum values normalize to `ALL`; a profile is retained only when it
matches a canonical cohort option from the selected task facts. Apply, tab,
reload and retry preserve normalized state. Clear-filter preserves the current
tab and intentionally clears the other dimensions. Writing task/profile does
not claim to filter the global summary, heatmap, all-Writing activity or
skill-only history where the DTO has no such dimension.

Real practice-more/catalog routes:

```text
/practice?skill=<ALL|READING|LISTENING|WRITING|SPEAKING>
/practice?skill=WRITING&writingTask=<Q51|Q52|Q53|Q54>
/practice/catalog?q=<search>&skill=WRITING&writingTask=<Q51|Q52|Q53|Q54>&classId=<authorized-class>&batch=<page>
```

The lazy route inherits the full initial query through
`URLSearchParams(window.location.search)` and changes only `batch`. The
repository task predicate is an `exists` over canonical
`PracticeQuestion.writingTaskType` inside the existing learner-visible,
published, class, search, skill and pageable query.

Implemented UI states:

1. global no-attempt;
2. global activity with no data for the normalized current filter;
3. per-fact/per-section unavailable, deferred or not-scorable;
4. `PAGE_DATA_UNAVAILABLE` with normalized reload;
5. `SERIALIZATION_UNAVAILABLE` with normalized reload and a complete canonical
   unavailable payload;
6. `CHART_ENHANCEMENT_UNAVAILABLE` while the server table/card remains usable.

Exact files changed by `13F-03`:

```text
src/main/java/com/ksh/features/practice/controller/PracticeController.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeSetRepository.java
src/main/java/com/ksh/features/practice/service/PracticeCatalogService.java
src/main/java/com/ksh/features/practice/web/PracticeModelAttributes.java
src/main/resources/static/css/practice-progress.css
src/main/resources/static/js/practice-progress.js
src/main/resources/templates/practice/fragments/progress-facts.html
src/main/resources/templates/practice/index.html
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
src/test/java/com/ksh/features/practice/repository/PracticeSetRepositoryTest.java
src/test/java/com/ksh/features/practice/service/PracticeCatalogServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

The exact selector contribution remains:

```text
PracticeControllerProgressTest,
PracticeCatalogServiceTest,
PracticeSetRepositoryTest,
PracticeFunctionalUiContractTest,
PracticeResultWordingTest,
PracticeIntegrationTest
```

Static blockers: none found. `PracticeProgressService` aggregation/arithmetic is
unchanged and remains the only owner. All validation and Git integration are
deferred to the coordinator's single Phase 13F gate.

## 20. 13F-03 focused PARTIAL Objective presentation fix (`2026-07-26`)

An independent correctness audit found one bounded P1 presentation defect:
`PracticeProgressService` intentionally emits a non-null Objective
`ProgressNumericFact` with `ProgressAvailability.PARTIAL` when eligible
earned/possible evidence coexists with excluded activity, but the 13F-03
template and JavaScript previously rendered only exact `AVAILABLE`. This hid a
valid canonical percentage and its earned/possible evidence from the recent
card, radar fallback/enhancement and weekly presentation.

Focused correction:

- `ProgressNumericFact.renderableValue()` is the backend presentation
  predicate: only `AVAILABLE` or `PARTIAL` with a non-null value is renderable.
  `PARTIAL` with a null value remains non-renderable and is never changed to
  zero. `partialCoverage()` identifies only a non-null partial fact for honest
  copy;
- Objective server presentation now uses that predicate for the recent score,
  Reading/Listening radar fallback, recent history, trend fallback, weekly
  facts and the existing Objective type table. The same predicate also keeps
  any valid task-cohort value visible without changing cohort arithmetic;
- the semantic radar and trend tables retain percentage plus canonical
  numerator/denominator. The shared source-fact fragment labels
  `Độ phủ một phần · 부분 범위`, states that excluded activity is not converted
  to zero, and renders
  `Điểm đạt / điểm có thể đạt · 획득 점수 / 가능 점수` before the existing
  sample, eligible/excluded coverage, exclusion-reason and observation-window
  facts;
- optional radar/trend enhancement uses
  `renderableNumericFact(...)`, accepts only `AVAILABLE|PARTIAL` plus a
  non-null finite DTO value and performs no score/coverage recalculation;
- one controller fixture carries a `PARTIAL` Reading fact with `75%`, `3/4`,
  two eligible and one `LEGACY_UNVERIFIED` exclusion through normalized
  controller presentation and JSON. It also proves a `PARTIAL` fact with a
  null value is not renderable. One static UI contract locks the DTO predicate,
  bilingual copy, semantic fallback evidence and chart helper.

Exact files changed by this focused fix:

```text
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/resources/static/js/practice-progress.js
src/main/resources/templates/practice/fragments/progress-facts.html
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

The deferred selector contribution is unchanged because the focused fixtures
are inside already-owned classes:

```text
PracticeControllerProgressTest,
PracticeCatalogServiceTest,
PracticeSetRepositoryTest,
PracticeFunctionalUiContractTest,
PracticeResultWordingTest,
PracticeIntegrationTest
```

`PracticeProgressService` aggregation/arithmetic was inspected but not changed.
No Writing aggregation, Speaking scoring, catalog behavior, Result/Detail,
retry/recovery, 13C3, SQL/Flyway or dependency work was opened. No test,
compile, build, lint, application, database/migration, provider/API, browser
QA, `git diff --check` or Git operation ran. `13F-03` remains
`IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`.

Two independent post-fix static audits returned `ACCEPT_STATIC`. The first
verified that non-null `PARTIAL` Objective facts retain their canonical
percentage, numerator/denominator, coverage and bilingual copy in server and
optional-chart presentation while null remains non-renderable. The second
verified that the fix stayed inside DTO presentation predicates, Progress
templates/JavaScript, focused contracts and this ledger, with no aggregation,
catalog, Result/Detail, provider/media or `13F-04+` scope change. This is static
acceptance only; no phase validation has run. At that audit snapshot,
`13F-04` was the next slice.

## 21. 13F-04 shared attempt-state, global resume and early command gate (`2026-07-26`)

`13F-04 = IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`.

The pure typed `PracticeAttemptStatePolicy` is now the shared interpretation
owner used by catalog, set/test detail, progress and result/re-evaluation
guards. It owns completion, canonical resumability, stale/restart-required
state, result eligibility, action-specific re-evaluation eligibility and
stable newest activity order. It does not own score math, best-score
calculation, Objective partial credit, Writing cohort arithmetic, Speaking
number presentation or provider reuse.

State matrix:

| Stored attempt | Display/result interpretation | Resume | Re-evaluate |
| --- | --- | --- | --- |
| no attempt | `NOT_STARTED` | no | no |
| canonical `IN_PROGRESS`, complete coherent immutable lock, compatible state | `IN_PROGRESS` | yes | no; non-terminal |
| `IN_PROGRESS` with missing/incomplete lock, incompatible compatibility state or incoherent identity | `STALE`, restart required | no | no |
| canonical `SUBMITTED` | `SUBMITTED`; analysis `QUEUED/PROCESSING` augments to `SCORING`, `SUCCEEDED` to `SCORED`, `FAILED` to Objective `PARTIAL` or subjective `FAILED` | no | full only for supported terminal R/L/W; per-question only Writing |
| canonical `GRADED` | `SCORED` | no | full only for supported terminal R/L/W; per-question only Writing |
| terminal retained attempt with incomplete/incompatible lock or incoherent immutable identity | terminal display state remains factual, but canonical result is unavailable and no result link is advertised | no | no |
| `DISCARDED`, unknown or unavailable lifecycle | historical/unavailable; discarded is excluded from normal consumers | no | no |

Analysis `QUEUED/PROCESSING/SUCCEEDED/FAILED` augments terminal display only.
It never makes an attempt complete, resumable, result-eligible or
re-evaluable. Activity order is exactly
`submittedAt -> updatedAt -> createdAt -> id DESC` and the ID tie-break is
locked both in the pure comparator and the global repository query.

Action matrix and early-gate order:

| Command | Allowed terminal action | Early rejection before downstream work |
| --- | --- | --- |
| full `reEvaluate` | canonical terminal Writing, Reading or Listening; retained non-Writing ESSAY compatibility remains after the common gate | wrong owner/not found, `IN_PROGRESS`, `DISCARDED`, incomplete lock, incompatible compatibility state, incoherent immutable identity and unsupported skill |
| `reEvaluateQuestion` | canonical terminal Writing only; target membership/type checks follow the common gate | the same common failures plus non-Writing action |
| generic Speaking re-evaluate | none in 13F-04 | fails closed as `UNSUPPORTED_ACTION` before version identity lookup, question/group snapshot, media/STT/Speaking evaluator, Writing evaluator or mutation |
| Speaking submit by ready audio | unchanged | existing submit/media/reuse path remains the owner |

Both public re-evaluation entrypoints now perform owner lookup first, then the
pure lifecycle/discard/lock/action gate, then a four-entity immutable identity
check that deliberately does not load groups or questions. Only after those
checks can a section/question snapshot or provider-capable path run. The full
command no longer calls the retained legacy
`loadSpeakingReEvaluationSnapshot(...)` branch. Fixed Vietnamese copy is
carried by the typed
`PracticeReEvaluationNotAllowedException`; the controller returns it as a safe
flash and never opens a new Speaking retry command.

Global resume evidence:

- `PracticeCatalogBatch.globalResume` is a separate DTO and
  `index.html` consumes it directly; `resumeCard()` and derivation from the
  current 12 cards were removed;
- one bounded native read selects at most one owner-matching canonical
  `IN_PROGRESS` attempt without catalog search, skill, class filter, batch or
  page predicates;
- the query inner-joins the exact published/set/test/section version chain and
  exact set/test/section/skill identity, rejects incompatible compatibility
  state, requires a live non-deleted `PUBLISHED` set, and applies only
  `GLOBAL`, same creator, or active-class membership visibility;
- catalog cards and set/test detail use one bounded per-page/per-set identity
  query, rather than per-attempt lookups, so a complete-looking but
  relationally incoherent lock is shown as `STALE` without creating an N+1;
- immutable set/test titles feed the resume DTO. The lazy card fragment
  contains neither the banner nor global-resume rendering, so subsequent
  batches cannot duplicate it;
- repository/integration contracts cover an attempt outside the current
  search/skill/page, equal-timestamp ID ordering and a deliberately stored
  attempt for an unrelated class that must not appear.

Progress all-time and per-skill persistence projections now count only
coherent compatible locked `IN_PROGRESS` rows as in-progress; stale rows enter
`other`. Recent history carries the shared display state and exposes neither a
resume link nor a result link for `STALE`. Score arithmetic, Writing task
cohorts, Objective earned/possible and numberless Speaking facts were not
changed.

Focused static-rejection closure:

- every direct attempt player path (`getAttemptPlayerView`,
  `getAttemptSectionDelivery`, `getPlayerQuestionGroupsForAttempt`, attempt
  Listening preflight and Speaking player delivery) now performs owner lookup,
  shared lifecycle/lock policy and four-level immutable identity coherence
  before `snapshot(...)`; only canonical `IN_PROGRESS` can continue;
- missing locks, incompatible compatibility state and incoherent identity use
  typed Vietnamese restart guidance. The controller performs that gate before
  session preflight dispatch and does not discard/mutate the rejected attempt;
  terminal attempts still redirect to result and discarded attempts retain
  not-found semantics;
- the attempt player graph no longer has `liveSectionForAttempt`,
  `loadPublished` or live group/question fallback. Immutable snapshot rows are
  the only player source for generic, Listening and Speaking delivery;
- `ResultEligibility` is now fail-closed: terminal status alone is
  insufficient. `ELIGIBLE_CANONICAL` requires a complete compatible lock and
  coherent published/set/test/section identity. Incomplete, incompatible and
  incoherent retained attempts have distinct typed rejection states;
- progress and test-detail result links use that same final policy result.
  The existing bounded coherent-identity repository read now covers every
  active attempt in the visible set batch, so result-link decisions do not add
  an N+1 and incompatible/incoherent history never links to a rejecting
  assembler;
- `PracticeResultAssembler.loadContext` applies the same structural and
  coherence gate before `snapshot(...)`, then rechecks attempt
  set/test/section/skill against the returned immutable snapshot before answer
  parsing or presenter dispatch. `PracticeResultDetailAssembler` continues to
  reuse this exact context gate; no fourth Result Detail contract or template
  was introduced;
- the retained `PracticeService.getResult(...)` and
  `getReadingListeningResult(...)` consumers now use the same canonical result
  gate and immutable snapshot-only assembly. Their former mutable live
  set/section/question result branches are gone.

Focused player/result matrix:

| Read surface | Canonical identity | Missing/incompatible/incoherent identity |
| --- | --- | --- |
| direct generic/Listening/Speaking player GET | shared resume gate, then immutable snapshot delivery | typed restart redirect before snapshot groups/questions, mutable repositories, media or provider |
| progress/test-detail result link | shown only for canonical terminal result eligibility | suppressed |
| Result overview | shared result gate, coherence check, immutable snapshot and exact source-identity recheck | typed fail-closed before snapshot for early coherence failure and before presenter for defensive snapshot mismatch |
| Result Detail | reuses overview `loadContext` unchanged | same rejection semantics before detail presenter |

Static interaction evidence is explicit in contracts: missing/incompatible
player locks do not call even the identity/snapshot loader; incoherent player
and result identities may perform only the four-entity coherence lookup and do
not call `snapshot(...)`, mutable set/section/group/question repositories,
audio/media/STT, Writing/Speaking evaluators, presenters or persistence
mutation. Canonical terminal overview/detail and canonical immutable players
remain covered by the existing positive contracts.

Second re-audit P2 contract closure: the shared
`assertNoReEvaluationDownstreamInteractions()` helper now explicitly verifies
that every early full/per-question re-evaluation rejection performs neither
`publishedVersionService.snapshot(...)` nor `setRepository.findById(...)`,
while retaining its existing section/group/question/provider/media and
attempt-mutation guards. This is a contract-only change; production behavior
is unchanged.

Coordinator disposition: the independent state/coherence re-audit returned
`ACCEPT_STATIC`. The independent side-effect re-audit initially requested the
two explicit negative assertions above as a P2, then returned `ACCEPT_STATIC`
after that contract-only closure. Therefore
`13F-04 = IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`. No phase
validation or Git action is implied; `13F-05` is the next approved slice.

Contract coverage locks the lifecycle/analysis/action matrix, catalog and
detail complete-looking incoherent locks as `STALE`, progress canonical versus
stale links/counts, global resume outside the current catalog result and stable
ID ordering. Full and per-question invalid commands assert no Writing or
Speaking evaluator, media/STT service, question snapshot repository or
attempt mutation. Authenticated catalog, set detail, test detail, progress,
result and result-detail GET contracts assert provider-free/read-only
behavior.

Exact production/template files changed by `13F-04`:

```text
src/main/java/com/ksh/features/practice/controller/PracticeController.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/service/PracticeAttemptStatePolicy.java
src/main/java/com/ksh/features/practice/service/PracticeCatalogService.java
src/main/java/com/ksh/features/practice/service/PracticeDetailPageService.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/java/com/ksh/features/practice/service/PracticePublishedVersionService.java
src/main/java/com/ksh/features/practice/service/PracticeService.java
src/main/resources/templates/practice/index.html
src/main/resources/templates/practice/progress.html
```

Exact test-contract files written/updated:

```text
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/service/PracticeAttemptStatePolicyTest.java
src/test/java/com/ksh/features/practice/service/PracticeCatalogServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeDetailPageServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Exact files changed by the focused static-rejection fix:

```text
src/main/java/com/ksh/features/practice/controller/PracticeController.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/result/PracticeResultAssembler.java
src/main/java/com/ksh/features/practice/service/PracticeAttemptStatePolicy.java
src/main/java/com/ksh/features/practice/service/PracticeCatalogService.java
src/main/java/com/ksh/features/practice/service/PracticeDetailPageService.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/java/com/ksh/features/practice/service/PracticeService.java
src/main/resources/templates/practice/test-detail.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java
src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java
src/test/java/com/ksh/features/practice/service/PracticeAttemptStatePolicyTest.java
src/test/java/com/ksh/features/practice/service/PracticeCatalogServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeDetailPageServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

The exact `13F-04` selector contribution deferred to the one consolidated
Phase 13F validation task is:

```text
PracticeAttemptStatePolicyTest,
PracticeCatalogServiceTest,
PracticeDetailPageServiceTest,
PracticeProgressServiceTest,
PracticeServiceTest,
PracticeFunctionalUiContractTest,
PracticeIntegrationTest,
PracticeResultPresenterTest,
PracticeResultDetailContractTest
```

These tests were written/read only and were not run. No compile, build, lint,
application startup, Docker, database/migration, provider/API, browser QA,
`git diff --check` or Git mutation ran.

Residual debt remains open and is not represented as repaid:

- `P15-COMP-02` non-Writing ESSAY compatibility remains behind the common
  terminal gate;
- `P15-COMP-04` legacy Speaking status/reuse remains unchanged;
- `P15-COMP-05` missing/incomplete immutable locks are now fail-closed for
  resume/player/result/re-evaluation and displayed honestly, but are not
  migrated;
- `P15-COMP-19` legacy/mock Speaking re-evaluate production remains retained
  as debt but is unreachable from the generic command.

No SQL/Flyway/schema/dependency cleanup and no typed Result Detail template was
changed. The focused contracts were written/read only and were not run. No
compile, build, lint, application startup, Docker, database/migration,
provider/API, browser QA, `git diff --check` or Git operation ran.
At the `13F-04` handoff, `13F-05` had not started; Section 22 supersedes that
historical status.

## 22. 13F-05 recovery UX implementation (`2026-07-26`)

`13F-05 = IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`.

This slice implements only the locked recovery UX. Its final correctness and
scope/side-effect re-audits returned `ACCEPT_STATIC`; this does not claim phase
validation.

### 22.1 Lecturer Reading/Listening explanation recovery

The existing 13D retry command remains the sole mutation owner. Both REST and
server-rendered paths now enforce these boundaries:

- the HTTP controller boundary is exact `LECTURER`, never
  `LECTURER_OR_ABOVE`, `HEAD`, `ADMIN` or operator override;
- the retained artifact-ID REST command calls
  `requireGlobal(actorId, PUBLISH)` before its first binding, question,
  published-version or any other repository read. A global denial therefore
  performs no protected lookup, lock, persistence or provider work;
- `PracticeAuthorizationService.requireSet(setId, actorId, PUBLISH)` runs
  as soon as a candidate bound set is known and before section, artifact,
  generation-task or lock access. It preserves active exact Lecturer,
  effective `practice.publish`, owner or unlocked active collaborator
  semantics;
- the stable revisions-page command resolves `setId + questionVersionId` only
  after that authorization. The retained programmatic REST command resolves an
  authorized published binding before any lock;
- only coherent Vietnamese Reading/Listening objective explanation bindings
  with matching question/section/published-version identity, artifact skill,
  question type and fingerprint are eligible. The task source is separately
  resolved as an immutable objective Reading/Listening question with a
  coherent section and Vietnamese binding to the same artifact and same
  fingerprint. It may be a different question, including a legitimate shared
  source outside the selected revision;
- a coherently bound artifact in `READY` is authoritative even when its
  completed generation task/source graph has later been deleted by retention.
  Command and projection return `READY` immediately and expose no retry action,
  lock or mutation. Task/source coherence remains mandatory for `PENDING` and
  `FAILED`;
- `READY` and artifact `PENDING` with an active
  `PENDING/PROCESSING/RETRY_WAIT` task are idempotent no-ops;
- queueing requires both artifact `FAILED` and task `FAILED`, a matching known
  retryable safe category, and an expired server-owned 60-second manual retry
  cooldown;
- invalid/missing binding, fingerprint mismatch, unsupported skill/type,
  missing task/source question/source section/source binding, task-to-artifact
  mismatch, incoherent source identity, state mismatch, unknown failure
  category and mismatched artifact/task category fail closed and never queue;
- a `PROVIDER_HTTP_` category is accepted only with exactly three decimal
  digits and is retryable only for `408`, `425`, `429` or `500..599`.
  `600`, `999`, `0500`, malformed and out-of-range values fail closed;
- before locks, the command verifies the current task source. The queue path
  then clears the JPA persistence context so a pre-lock managed FAILED entity
  cannot survive into the locking decision. It reloads the task under
  pessimistic lock, resolves and verifies that fresh locked task source before
  taking the canonical artifact lock, and rechecks the complete fresh locked
  state before mutation. A concurrent waiter therefore observes the first
  transaction's committed `PENDING`/other terminal state rather than queueing
  stale FAILED state. A changed/invalid locked source cannot lock or mutate
  the artifact. The path creates no alternate artifact/task and invokes no
  provider itself.

REST keeps the existing external status contract:

| State | HTTP result |
| --- | --- |
| `READY` or active `PENDING` no-op | `200` |
| retry queued | `202` |
| invalid/non-retryable | `409` |
| server cooldown | `429` plus `Retry-After` |

Service, REST and SSR messages are fixed safe Vietnamese copy and never
include stored/provider errors.

### 22.2 Batch recovery projection and selected revisions PRG

`QuestionExplanationRecoveryQueryService` is a narrow read-only projection. It
authorizes the selected set before reads and validates every supplied
published version belongs to that set. It then uses bounded selected-graph
batches for questions, sections, Vietnamese bindings and artifacts; one task
batch through
`QuestionExplanationGenerationTaskRepository.findByArtifactIdIn(...)`; and
bounded source-graph batches for every referenced source question, source
section and Vietnamese source binding. This deliberately supports a coherent
shared-artifact source outside the selected questions without any per-row
query. A valid selected binding delegates directly to the shared state machine
with the computed task-source-validity flag, so `READY` survives a missing
retained task while `PENDING`/`FAILED` still fail closed on invalid sources.
The query has no provider dependency/call, lock or mutation.

The exact server-derived UI/action matrix is:

| Projection state | Revisions-page action |
| --- | --- |
| `READY` | status only; no form |
| `PENDING` | active processing/idempotency copy; no form |
| `FAILED_RETRYABLE` | the only state with a CSRF-protected retry form |
| `RATE_LIMITED` | disabled wait control with server seconds; no form |
| `FAILED_NON_RETRYABLE` | correction plus republish guidance; no form |

The projection is rendered only for a selected
`/practice/manage/revisions?setId=...` history. The POST uses stable
question-version identity, safe success/error flash, and redirects to the
exact same selected `setId`. Artifact/task/provider identifiers are not exposed
by the server-rendered page.

### 22.3 Speaking submit-only learner recovery

Only the current Speaking Result overview changed. When the existing
authoritative `result.feedback().state()` is exactly `FAILED` or
`UNAVAILABLE`, it now explains in Vietnamese/Korean that the submitted attempt
is immutable and cannot be re-evaluated. Its real
`Luyện lại · 다시 연습` link targets the canonical
`/practice/sets/{setId}/tests/{testId}` identity and opens the existing
preflight for another practice. The copy does not promise unconditional
creation: if another coherent `IN_PROGRESS` attempt is already valid, the
global preflight may resume that other attempt. It never resumes or reuses the
failed submission or recording shown on this Result.

There is no retry/re-evaluate form or `/re-evaluate` link, no reuse of the
failed attempt/recording, no media/STT/provider/reuse/scoring mutation, and no
new acoustic, holistic or numeric claim. Other feedback states and the
exactly-three typed Result Detail contract are unchanged.

The accepted 13F-04 fail-closed canonical Result/player behavior remains
untouched. In particular, `PracticeController`, `PracticeService`,
`PracticeAttemptStatePolicy`, `PracticeProgressService`, Result assemblers and
Result Detail templates were not edited.

### 22.4 Exact implementation ledger

Production/template/style files changed by `13F-05`:

```text
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryService.java
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryService.java
src/main/java/com/ksh/features/practice/manage/controller/PracticeExplanationController.java
src/main/java/com/ksh/features/practice/manage/controller/PracticeManageController.java
src/main/java/com/ksh/features/practice/repository/QuestionExplanationGenerationTaskRepository.java
src/main/resources/static/css/practice/manage-dashboard.css
src/main/resources/static/css/practice-result.css
src/main/resources/templates/practice/manage/revisions.html
src/main/resources/templates/practice/result/speaking.html
```

Focused contract files written/updated:

```text
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryServiceTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryServiceTest.java
src/test/java/com/ksh/features/practice/governance/PracticeAuthorizationServiceTest.java
src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java
src/test/java/com/ksh/features/practice/manage/controller/PracticeExplanationControllerTest.java
src/test/java/com/ksh/features/practice/manage/controller/PracticeManageRecoveryControllerTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

The exact `13F-05` selector deferred to the one consolidated Phase 13F
validation task is:

```text
QuestionExplanationRetryServiceTest,
QuestionExplanationRecoveryQueryServiceTest,
PracticeExplanationControllerTest,
PracticeManageRecoveryControllerTest,
PracticeAuthorizationServiceTest,
PracticePhase11AuthoringUiContractTest,
PracticeFunctionalUiContractTest,
PracticeResultWordingTest,
SpeakingResultRenderingContractTest,
PracticeSpeakingMediaUiResourceTest,
PracticeIntegrationTest
```

`PracticeIntegrationTest` adds the smallest real Spring/JPA seam: selected-set
batch rendering, CSRF rejection, owner-authorized PRG queue persistence,
server cooldown PRG, separate exact-role denial for `STUDENT`, `HEAD` and
`ADMIN` through both SSR and REST, unchanged artifact/task/manual-retry state
under every denial, and no provider interaction. Unit/static contracts prove
that artifact-command global denial precedes every repository read, while
source-identity and strict HTTP-category cases prove no lock or queue. The
integration selector also re-proves the accepted 13F-04
historical/incoherent Result fail-closed behavior; 13F-05 did not overwrite
those contracts.

The second focused hardening pass adds unrun coverage for both remaining P1s:

- unit and projection contracts assert a coherent `READY` artifact with no
  retained task projects/returns `READY`, exposes no retry action and performs
  no lock or mutation; separate cases keep invalid-source `PENDING`/`FAILED`
  fail closed;
- a real integration concurrency contract uses two distinct connections and
  `REQUIRES_NEW` transactions. Both transactions deliberately preload the same
  FAILED task/artifact into their persistence contexts before release. The
  service-level persistence-context clear plus task-then-artifact pessimistic
  lock sequence must yield exactly one queued result, exactly one
  `manualRetryCount` increment, a final `PENDING` task/artifact, and zero
  provider interaction.

Focused static-rejection fix files:

```text
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryService.java
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryService.java
src/main/resources/templates/practice/result/speaking.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryServiceTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryServiceTest.java
src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

Second focused hardening-pass files:

```text
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryService.java
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryService.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryServiceTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryServiceTest.java
src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
```

### 22.5 Deferred scope and prohibited actions

Still deferred: learner Reading/Listening retry, any Speaking re-evaluation,
audio/media/STT/provider/reuse change, scoring/prompt/subcriterion work, Result
Detail redesign, SQL/Flyway/schema/seed, broad manage-query cleanup, 13C3,
13G/13H and Phase 14/15 debt. `P15-COMP-02`, `P15-COMP-04`,
`P15-COMP-05`, `P15-COMP-19`, COMP-18 and KEEP-05 remain open exactly as
owned by their later gates.

All listed tests/contracts were written/read only and were not run. No unit,
integration or full test, compile, build, lint, application/startup, Docker,
database/migration, provider/API, browser QA, `git diff --check`, pull, stage,
commit, push or any Git mutation ran during `13F-05`.

### 22.6 Final static verdict and handoff

The first audit cycle rejected authorization ordering, task-source identity,
HTTP-category bounds, Speaking resume wording and exact-role denial evidence.
One focused pass closed those findings. The second cycle then identified
READY-after-retention ordering and stale managed entities before pessimistic
locks; one grouped hardening pass closed both and added the unrun retention and
two-transaction concurrency contracts described above.

The final independent correctness re-audit and the final independent
scope/side-effect re-audit both returned `ACCEPT_STATIC`. Therefore:

- `13F-05 = IMPLEMENTED_STATIC_ACCEPTED_PENDING_PHASE_VALIDATION`;
- this verdict is static evidence only and does not imply compile/test/gate
  success;
- `13F-06` is `READY_FOR_PHASE_VALIDATION` after one grouped correction pass
  whose active state was `IMPLEMENTED_PENDING_STATIC_REAUDIT`;
- its initial static audits returned one `ACCEPT` and two `REJECT` verdicts;
  the concrete findings are corrected without validation;
- the final correctness, scope/side-effect and validation-readiness re-audits
  all returned `ACCEPT_STATIC_13F_06`;
- the next action is the exact consolidated Section 23 validation lifecycle.

## 23. 13F-06 full reconciliation/pre-gate (`2026-07-26`)

### 23.1 Static reconciliation result

The complete Phase 13F working-tree diff was reconciled against its production
call sites, constructors, repository signatures, routes, templates,
JavaScript/CSS, tests and current-source documents. This was a static-only pass;
it did not execute any validation command.

The initial 13F-06 static audits returned one `ACCEPT` and two `REJECT`
verdicts. The rejecting findings had five concrete root causes:

1. `progress.html` called nonexistent
   `ProgressNumericFact.profile()` even though the DTO exposes
   `profileId()`, and `PracticeResultWordingTest` preserved the invalid call.
2. Current-status/current-action blocks still instructed a future agent to
   create or run 13F-06, or reported only `13F-01..05`.
3. The candidate integration gate inherited
   `ksh_phase13e_result_ui`, a stale schema that reports V44 but lacks
   `tests.media_type`.
4. The selector included bare `PracticeIntegrationTest`, expanding the gate to
   the entire integration class rather than the changed Phase13F seams.
5. `git diff --check` cannot see intended untracked Phase13F additions, and no
   bounded read-only whitespace/final-newline check covered them.

One grouped focused correction pass fixed all five causes. During the edit the
slice status was `IMPLEMENTED_PENDING_STATIC_REAUDIT`; after this static
reconciliation it is `READY_FOR_STATIC_REAUDIT_13F_06`. The template now calls
`profileId()` for both presence and rendering, and the static wording contract
both requires `.profileId()` and rejects reintroduction of `.profile()`.
Current-source status pointers now require the final re-audit before any
candidate `READY_FOR_PHASE_VALIDATION` promotion. Sections 23.4-23.5 contain
the narrowed method filter, newly named disposable Phase13F database lifecycle
and explicit untracked-file allowlist. None of those commands was run.

The final preflight re-audit then returned `ACCEPT` for correctness and scope,
with one remaining P1 in the proposed lifecycle: separate create/test/proof/drop
blocks could strand the disposable database or let cleanup mask the gate
status. One final docs-only focused correction replaced those blocks with the
single trapped wrapper in Section 23.5. The lifecycle correction itself still
awaits static re-audit; no validation or database command was run.

The following contract groups were checked together after the edits and have
no remaining concrete mismatch found by this correction pass. This statement
is static reasoning, not the pending independent re-audit:

1. `PracticeController` constructor/model attributes and its single
   `PracticeProgressService.getProgressPageData(...)` consumer align with the
   progress DTO graph and the controller-only filter projection.
2. `PracticeAttemptRepository` projection aliases, parameters and callers align
   for all-time counts, per-skill facts, bounded recent detail, all-time Writing
   evidence, coherent attempt identity and global resume. The global resume
   remains outside catalog search/skill/class/page filters.
3. `PracticeAttemptStatePolicy` is the shared interpretation used by
   catalog, set/test detail, player/resume, result eligibility, progress history
   and re-evaluation gates. Canonical player/result commands fail closed on an
   incomplete, incompatible or incoherent immutable lock.
4. Progress keeps Objective earned/possible evidence, nullable duration and
   score facts, partial/legacy exclusions, independently labelled bounded
   recent windows and numberless Speaking activity. Writing task rows use
   immutable Q51-Q54 identity and separate scoring-profile/maximum cohorts;
   catalog and progress deep links preserve the normalized Writing task.
5. Lecturer Reading/Listening recovery remains exact-role `LECTURER`, checks
   global or selected-set `PUBLISH` authorization before protected target
   reads, requires coherent Vietnamese objective source/binding evidence,
   treats retained `READY` independently of task retention, and keeps
   task-then-artifact pessimistic locking after clearing stale managed state.
   The selected revisions POST is CSRF-protected PRG back to the same `setId`;
   the revisions GET uses the batched read-only projection and does not mutate
   or call a provider.
6. Speaking remains submit-only and numberless for nullable, partial, legacy
   and failed feedback. Its failed/unavailable result CTA returns to the
   canonical set/test detail for another preflight/new-or-existing canonical
   in-progress attempt; it does not retry the failed attempt or expose a
   re-evaluate action.
7. The active Result/Result Detail contract still dispatches through the
   canonical result context and exactly three typed Detail screens. The 13F
   progress, catalog, detail and recovery JavaScript/templates do not parse or
   restore raw provider JSON. Verified-dead generic raw-JSON templates remain
   deferred cleanup and were not reopened.

No scoring/evaluator/prompt/subcriterion, Speaking media/STT/TTS, Result Detail
redesign, SQL/Flyway/schema/seed, dependency/toolchain/security, provider
behavior, 13C3/13G/13H, pre-14 or Phase 14/15 surface was changed by this
reconciliation.

### 23.2 Exact files reconciled

The exact Phase 13F diff/source files reconciled in this pass are:

```text
CODEX_PRACTICE_WORKFLOW.md
PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md
docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md
docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryService.java
src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryService.java
src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationResult.java
src/main/java/com/ksh/features/practice/ai/writing/WritingFeedbackCompatibilityReader.java
src/main/java/com/ksh/features/practice/controller/PracticeController.java
src/main/java/com/ksh/features/practice/dto/PracticeDtos.java
src/main/java/com/ksh/features/practice/manage/controller/PracticeExplanationController.java
src/main/java/com/ksh/features/practice/manage/controller/PracticeManageController.java
src/main/java/com/ksh/features/practice/repository/PracticeAttemptRepository.java
src/main/java/com/ksh/features/practice/repository/PracticeQuestionVersionRepository.java
src/main/java/com/ksh/features/practice/repository/PracticeSetRepository.java
src/main/java/com/ksh/features/practice/repository/QuestionExplanationGenerationTaskRepository.java
src/main/java/com/ksh/features/practice/result/PracticeResultAssembler.java
src/main/java/com/ksh/features/practice/service/PracticeAttemptStatePolicy.java
src/main/java/com/ksh/features/practice/service/PracticeCatalogService.java
src/main/java/com/ksh/features/practice/service/PracticeDetailPageService.java
src/main/java/com/ksh/features/practice/service/PracticeProgressService.java
src/main/java/com/ksh/features/practice/service/PracticePublishedVersionService.java
src/main/java/com/ksh/features/practice/service/PracticeService.java
src/main/java/com/ksh/features/practice/web/PracticeModelAttributes.java
src/main/resources/static/css/practice-progress.css
src/main/resources/static/css/practice-result.css
src/main/resources/static/css/practice/manage-dashboard.css
src/main/resources/static/js/practice-progress.js
src/main/resources/templates/practice/fragments/progress-facts.html
src/main/resources/templates/practice/index.html
src/main/resources/templates/practice/manage/revisions.html
src/main/resources/templates/practice/progress.html
src/main/resources/templates/practice/result/speaking.html
src/main/resources/templates/practice/test-detail.html
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryServiceTest.java
src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRetryServiceTest.java
src/test/java/com/ksh/features/practice/ai/writing/WritingFeedbackCompatibilityReaderTest.java
src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java
src/test/java/com/ksh/features/practice/governance/PracticeAuthorizationServiceTest.java
src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java
src/test/java/com/ksh/features/practice/manage/controller/PracticeExplanationControllerTest.java
src/test/java/com/ksh/features/practice/manage/controller/PracticeManageRecoveryControllerTest.java
src/test/java/com/ksh/features/practice/repository/PracticeSetRepositoryTest.java
src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java
src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java
src/test/java/com/ksh/features/practice/service/PracticeAttemptStatePolicyTest.java
src/test/java/com/ksh/features/practice/service/PracticeCatalogServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeDetailPageServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java
```

The grouped 13F-06 correction pass edited exactly:

```text
CODEX_PRACTICE_WORKFLOW.md
PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md
docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md
docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md
docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md
src/main/resources/templates/practice/progress.html
src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java
```

All other Phase 13F files listed above were read/reconciled without a
13F-06 correction edit.

The final disposable-lifecycle correction edited only this live log.

### 23.3 Concrete dead-path decision

**Production paths removed by `13F-06`: none removed.**

The call-site audit gives concrete reasons to retain the locked surfaces:

- `PracticeProgressService` is injected into and consumed by
  `PracticeController`; its four canonical progress repository reads are
  consumed by that service and covered by focused/integration contracts.
- `findGlobalResumeCandidates(...)` is consumed by
  `PracticeCatalogService.loadGlobalResume(...)`; the catalog template consumes
  `PracticeCatalogBatch.globalResume` independently of lazy card batches.
- `findCoherentAttemptIdentityIds(...)` is consumed by both
  `PracticeCatalogService` and `PracticeDetailPageService`.
- the unrelated currently-unused
  `findByTestIdAndUserIdAndSkillOrderByCreatedAtDesc(...)` and
  `findBySetIdAndUserIdOrderByCreatedAtDesc(...)` repository methods are
  retained exactly as preflight required;
- `PerformanceByTypeRow` is retained with its existing
  `PracticeService.getReadingListeningResult(...)` construction path;
- the private legacy Speaking re-evaluation snapshot helper is not called by
  either public re-evaluation command, but deleting or changing that deferred
  provider/re-evaluation implementation is outside 13F-06. The early shared
  attempt-state gate remains the active submit-only boundary.

The superseded progress assembler/entrypoint, old derived top-100 progress
queries and `LearningProfileView` had already been removed in the accepted
13F-01 cycles. No duplicate active aggregation owner or stale consumer remains
to remove in this pass.

### 23.4 Locked whole-phase focused selector

The locked non-integration class selector remains intact except that bare
`PracticeIntegrationTest` is removed:

```text
PracticeProgressServiceTest,
PracticeControllerProgressTest,
PracticeAttemptStatePolicyTest,
PracticeServiceTest,
PracticeDetailPageServiceTest,
PracticeCatalogServiceTest,
PracticeSetRepositoryTest,
WritingFeedbackCompatibilityReaderTest,
WritingTaskNativeScoringTest,
QuestionExplanationRetryServiceTest,
QuestionExplanationRecoveryQueryServiceTest,
PracticeExplanationControllerTest,
PracticeManageRecoveryControllerTest,
PracticeAuthorizationServiceTest,
PracticePhase11AuthoringUiContractTest,
SpeakingEvaluationReusePolicyTest,
SpeakingResultRenderingContractTest,
PracticeSpeakingMediaUiResourceTest,
PracticeResultPresenterTest,
PracticeResultDetailContractTest,
ObjectiveResultDetailTypeNativeContractTest,
PracticeFunctionalUiContractTest,
PracticeResultWordingTest
```

The exact integration boundary is:

```text
PracticeIntegrationTest#globalResumeSurvivesCurrentSearchSkillPageAndIsNotRenderedByLazyFragment+globalResumeRepositoryUsesIdAsStableTieBreakForEqualActivityTime+catalogAndDetailGetsRemainReadOnlyAndProviderFree+writingTaskCatalogFilterRoundTripsThroughInitialAndLazyAuthorizedPages+unrelatedClassSetIsHiddenFromCatalogAndDirectLearnerRoutes+testSetDetailUsesPerTestProgressAndIgnoresOtherUsersAndSets+testTestDetailView+overviewAndDetailRejectIncoherentTerminalIdentityBeforePresenters+testPublishedLegacySpeakingEssayStillSubmitsAndRendersButReEvaluateFailsClosed+progressFilterNormalizesRoundTripsAndGetRemainsReadOnlyProviderFree+lecturerExplanationRecoveryUsesBatchProjectionCsrfPrgPersistenceAndCooldown+concurrentExplanationRetriesUseFreshLockedStateAndQueueExactlyOnce+studentCannotQueueExplanationRecoveryThroughSsrOrRest+headCannotQueueExplanationRecoveryThroughSsrOrRest+adminCannotQueueExplanationRecoveryThroughSsrOrRest+testProgressInProgressAttemptShowsContinueOnly+testProgressStaleInProgressAttemptHasNoResumeOrResultLink+testProgressDoesNotShowOtherUsersAttemptsOrCreateSubmission+progressNativeProjectionsKeepAllTimeIdentityDurationAndActivityOrderCoherent+testDiscardAttempt+directPlayerFailsClosedForMissingIncompatibleAndIncoherentLocks
```

Every method name above exists exactly in the current
`PracticeIntegrationTest` source. This is the smallest adequate integration
boundary because it retains only Phase13F changed seams:

- catalog/global-resume filtering, stable ordering, authorization and
  Writing-task round-trip;
- set/test-detail, progress and direct-player attempt-state behavior plus
  terminal result eligibility, including numberless legacy Speaking;
- progress read-only/filter/link/identity/window/coverage behavior, including
  discarded-attempt exclusion through the canonical progress service; and
- selected-set lecturer recovery, CSRF/PRG/cooldown/persistence, exact
  STUDENT/HEAD/ADMIN denial and two-connection concurrency.

The bare class would also execute unrelated legacy publishing, authoring,
media, Writing evaluator and result-detail journeys. Fixture-only adjustments
in those older methods do not broaden the Phase13F integration boundary; their
changed production contracts remain covered by the locked focused classes.

### 23.5 Exact proposed end-of-phase validation commands

These commands are recorded as the candidate consolidated Phase 13F gate only.
They were **not executed** by `13F-06`. Run them in the exact order shown only
after an accepting final static re-audit and an explicit
`READY_FOR_PHASE_VALIDATION` handoff.

The gate starts with the required tracked-diff whitespace check:

```bash
git diff --check
```

Because `git diff --check` does not inspect untracked files, run this separate
read-only check against the explicit intended Phase13F untracked allowlist. It
does not stage, mutate or compare content, and exits nonzero only for a missing
allowlisted file, trailing spaces/tabs or a missing final newline. Ordinary
no-index content differences therefore cannot be mistaken for whitespace
failure, and unrelated untracked user files are never opened:

```bash
perl -e '
my $bad = 0;
for my $path (@ARGV) {
    open my $fh, "<:raw", $path or do {
        warn "$path: cannot read: $!\n";
        $bad = 1;
        next;
    };
    local $/;
    my $text = <$fh>;
    $text = "" unless defined $text;
    my $line_number = 0;
    for my $line (split /\n/, $text, -1) {
        $line_number++;
        if ($line =~ /[ \t]+$/) {
            warn "$path:$line_number: trailing whitespace\n";
            $bad = 1;
        }
    }
    if (length($text) && substr($text, -1) ne "\n") {
        warn "$path: missing final newline\n";
        $bad = 1;
    }
}
exit $bad;
' \
  docs/PRACTICE_PHASE_13F_LIVE_CHANGE_LOG.md \
  src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryService.java \
  src/main/java/com/ksh/features/practice/service/PracticeAttemptStatePolicy.java \
  src/main/java/com/ksh/features/practice/service/PracticeProgressService.java \
  src/main/resources/templates/practice/fragments/progress-facts.html \
  src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationRecoveryQueryServiceTest.java \
  src/test/java/com/ksh/features/practice/controller/PracticeControllerProgressTest.java \
  src/test/java/com/ksh/features/practice/manage/controller/PracticeExplanationControllerTest.java \
  src/test/java/com/ksh/features/practice/manage/controller/PracticeManageRecoveryControllerTest.java \
  src/test/java/com/ksh/features/practice/service/PracticeAttemptStatePolicyTest.java \
  src/test/java/com/ksh/features/practice/service/PracticeProgressServiceTest.java
```

Then compile once on JDK 17:

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  bash mvnw -DskipTests compile
```

Never point the integration gate at `ksh_phase13e_result_ui`: it reports V44
but lacks `tests.media_type` and must not be repaired or reused. The compile
above remains a separate single compile. The complete database-owned portion
below is one exact Bash wrapper: it registers `EXIT` and signal traps before
the creation attempt, refuses a pre-existing database, creates the fresh
Phase13F database, runs the one focused selector, proves Flyway/schema state,
and always attempts cleanup plus absence proof.

```bash
bash <<'PHASE13F_DISPOSABLE_GATE'
set -Eeuo pipefail

phase13f_db_name='ksh_phase13f_validation_20260726'
phase13f_db_password=''
phase13f_db_url=''
phase13f_db_create_attempted=0
phase13f_existing_database=''
phase13f_schema_proof=''
phase13f_test_selector='PracticeProgressServiceTest,PracticeControllerProgressTest,PracticeAttemptStatePolicyTest,PracticeServiceTest,PracticeDetailPageServiceTest,PracticeCatalogServiceTest,PracticeSetRepositoryTest,WritingFeedbackCompatibilityReaderTest,WritingTaskNativeScoringTest,QuestionExplanationRetryServiceTest,QuestionExplanationRecoveryQueryServiceTest,PracticeExplanationControllerTest,PracticeManageRecoveryControllerTest,PracticeAuthorizationServiceTest,PracticePhase11AuthoringUiContractTest,SpeakingEvaluationReusePolicyTest,SpeakingResultRenderingContractTest,PracticeSpeakingMediaUiResourceTest,PracticeResultPresenterTest,PracticeResultDetailContractTest,ObjectiveResultDetailTypeNativeContractTest,PracticeFunctionalUiContractTest,PracticeResultWordingTest,PracticeIntegrationTest#globalResumeSurvivesCurrentSearchSkillPageAndIsNotRenderedByLazyFragment+globalResumeRepositoryUsesIdAsStableTieBreakForEqualActivityTime+catalogAndDetailGetsRemainReadOnlyAndProviderFree+writingTaskCatalogFilterRoundTripsThroughInitialAndLazyAuthorizedPages+unrelatedClassSetIsHiddenFromCatalogAndDirectLearnerRoutes+testSetDetailUsesPerTestProgressAndIgnoresOtherUsersAndSets+testTestDetailView+overviewAndDetailRejectIncoherentTerminalIdentityBeforePresenters+testPublishedLegacySpeakingEssayStillSubmitsAndRendersButReEvaluateFailsClosed+progressFilterNormalizesRoundTripsAndGetRemainsReadOnlyProviderFree+lecturerExplanationRecoveryUsesBatchProjectionCsrfPrgPersistenceAndCooldown+concurrentExplanationRetriesUseFreshLockedStateAndQueueExactlyOnce+studentCannotQueueExplanationRecoveryThroughSsrOrRest+headCannotQueueExplanationRecoveryThroughSsrOrRest+adminCannotQueueExplanationRecoveryThroughSsrOrRest+testProgressInProgressAttemptShowsContinueOnly+testProgressStaleInProgressAttemptHasNoResumeOrResultLink+testProgressDoesNotShowOtherUsersAttemptsOrCreateSubmission+progressNativeProjectionsKeepAllTimeIdentityDurationAndActivityOrderCoherent+testDiscardAttempt+directPlayerFailsClosedForMissingIncompatibleAndIncoherentLocks'

phase13f_cleanup() {
  local phase13f_gate_status="$?"
  local phase13f_cleanup_status=0
  local phase13f_absence_query_status=0
  local phase13f_remaining_database=''
  local phase13f_final_status=0

  set +e
  trap - EXIT HUP INT TERM
  trap '' HUP INT TERM

  if [ "${phase13f_db_create_attempted:-0}" -eq 1 ]; then
    if ! MYSQL_PWD="${phase13f_db_password:-}" \
      mysql -h 127.0.0.1 -u root \
      -e "DROP DATABASE IF EXISTS ${phase13f_db_name};"; then
      printf '%s\n' \
        'Phase13F cleanup failure: DROP DATABASE IF EXISTS failed.' >&2
      phase13f_cleanup_status=90
    fi

    phase13f_remaining_database=$(MYSQL_PWD="${phase13f_db_password:-}" \
      mysql -h 127.0.0.1 -u root -N -B \
      -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '${phase13f_db_name}';")
    phase13f_absence_query_status=$?
    if [ "$phase13f_absence_query_status" -ne 0 ]; then
      printf '%s\n' \
        'Phase13F cleanup failure: database-absence proof query failed.' >&2
      phase13f_cleanup_status=90
    elif [ "$phase13f_remaining_database" != '0' ]; then
      printf '%s\n' \
        "Phase13F cleanup failure: ${phase13f_db_name} still exists." >&2
      phase13f_cleanup_status=90
    fi
  fi

  if [ "$phase13f_gate_status" -ne 0 ]; then
    phase13f_final_status="$phase13f_gate_status"
    if [ "$phase13f_cleanup_status" -ne 0 ]; then
      printf 'Phase13F gate failed with status %s; cleanup also failed with status %s; returning the original gate status.\n' \
        "$phase13f_gate_status" "$phase13f_cleanup_status" >&2
    fi
  elif [ "$phase13f_cleanup_status" -ne 0 ]; then
    phase13f_final_status="$phase13f_cleanup_status"
    printf 'Phase13F gate work succeeded, but cleanup/absence proof failed; returning status %s.\n' \
      "$phase13f_cleanup_status" >&2
  fi

  unset phase13f_db_password phase13f_db_url phase13f_db_name \
    phase13f_db_create_attempted phase13f_existing_database \
    phase13f_schema_proof phase13f_test_selector \
    phase13f_remaining_database
  trap - HUP INT TERM
  exit "$phase13f_final_status"
}

phase13f_signal_exit() {
  local phase13f_signal_status="$1"
  local phase13f_signal_name="$2"
  printf 'Phase13F gate received %s; exiting with status %s before trapped cleanup.\n' \
    "$phase13f_signal_name" "$phase13f_signal_status" >&2
  exit "$phase13f_signal_status"
}

trap phase13f_cleanup EXIT
trap 'phase13f_signal_exit 129 HUP' HUP
trap 'phase13f_signal_exit 130 INT' INT
trap 'phase13f_signal_exit 143 TERM' TERM

phase13f_db_password=$(sed -n 's/^DB_PASSWORD=//p' \
  src/main/resources/application-local.properties)
if [ -z "$phase13f_db_password" ]; then
  printf '%s\n' 'Phase13F gate failure: DB_PASSWORD is empty.' >&2
  exit 64
fi

phase13f_existing_database=$(MYSQL_PWD="$phase13f_db_password" \
  mysql -h 127.0.0.1 -u root -N -B \
  -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '${phase13f_db_name}';")
if [ "$phase13f_existing_database" != '0' ]; then
  printf '%s\n' \
    "Phase13F gate refusal: ${phase13f_db_name} already exists; it will not be reused or repaired." >&2
  exit 65
fi

phase13f_db_create_attempted=1
MYSQL_PWD="$phase13f_db_password" mysql -h 127.0.0.1 -u root \
  -e "CREATE DATABASE ${phase13f_db_name} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

phase13f_db_url="jdbc:mysql://127.0.0.1:3306/${phase13f_db_name}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8"
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  DB_URL="$phase13f_db_url" \
  DB_USERNAME=root DB_PASSWORD="$phase13f_db_password" \
  bash mvnw "-Dtest=${phase13f_test_selector}" test

phase13f_schema_proof=$(MYSQL_PWD="$phase13f_db_password" \
  mysql -h 127.0.0.1 -u root -N -B \
  -e "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), 0), COALESCE(SUM(success = 1), 0), COALESCE(SUM(success = 0), 0), (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '${phase13f_db_name}' AND table_name = 'tests' AND column_name = 'media_type') FROM ${phase13f_db_name}.flyway_schema_history;")
if [ "$phase13f_schema_proof" != $'44\t44\t0\t1' ]; then
  printf 'Phase13F schema proof mismatch: expected 44<TAB>44<TAB>0<TAB>1, received %q.\n' \
    "$phase13f_schema_proof" >&2
  exit 66
fi

exit 0
PHASE13F_DISPOSABLE_GATE
```

Cleanup/status semantics are locked as follows:

- every exit after the create-attempt flag is set, including HUP/INT/TERM,
  attempts `DROP DATABASE IF EXISTS` and then queries
  `information_schema.schemata` to prove absence;
- the cleanup handler clears its traps before its final `exit`, so it cannot
  recurse, and unsets the password, URL, database, selector and proof variables;
- a failed selector/schema gate returns its original status when cleanup
  succeeds; if cleanup also fails, both diagnostics are printed and the
  original gate status remains authoritative;
- a green gate with failed cleanup or absence proof returns deterministic
  status `90`, so cleanup can never mask failure or turn the gate green;
- successful cleanup leaves the fixed database name absent, making the wrapper
  reproducibly rerunnable.

### 23.6 Static handoff

`13F-06 = READY_FOR_PHASE_VALIDATION`.

Correctness, scope/side-effect and validation-readiness final re-audits all
returned `ACCEPT_STATIC_13F_06`, including the final docs-only trapped-wrapper
correction. The coordinator handoff uses the exact ordered lifecycle in
Section 23.5 once. This document does not claim compile, tests, database
lifecycle or phase validation have run.

No unit/integration/full test, Maven/Gradle compile or build, lint, startup,
Docker, database/migration, provider/API, browser QA, `git diff --check` or Git
mutation was run during `13F-06`.

## 24. Consolidated validation correction ledger

The single Phase 13F validation unit was started after every implementation
slice and static review had completed. Failures are handled as grouped
correction cycles: all diagnostics are collected and classified before one
concentrated patch, and the complete Section 23.5 lifecycle is rerun only after
that patch is finished.

### 24.1 Validation attempts completed so far

1. The first lifecycle stopped at test compilation with two independent
   compile-time blockers. Both were analyzed together and corrected in one
   patch; no focused test was run.
2. The second lifecycle stopped at test compilation because one integration
   assertion lambda captured a reassigned local variable. The identifier was
   frozen after persistence in one grouped correction; no focused test was
   run.
3. The third lifecycle passed `git diff --check`, the explicit untracked-file
   whitespace/final-newline inspection and the JDK 17 compile. Its disposable
   database started from the canonical Flyway chain at V44 and the selector
   executed 331 tests before reporting four failures and four errors. The
   trapped cleanup deleted the disposable database, and the follow-up
   information-schema query returned `0`, proving absence.
4. The fourth lifecycle again passed both whitespace checks and the JDK 17
   compile, compiled all 207 test sources, started the fresh V44 database and
   executed all 331 selected tests. It reported zero failures and three errors,
   all in the three updated `PracticeServiceTest` fixtures and all with the
   same `UnfinishedStubbingException`. The helper evaluated
   `versionSnapshot("READING")` inside an unfinished outer Mockito
   `thenReturn`; that snapshot builder itself stubs mocks. The disposable
   database was deleted by the trap, and the independent absence query again
   returned `0`.
5. The fifth lifecycle passed `git diff --check`, the explicit untracked
   allowlist check and the JDK 17 compile. It compiled 207 test sources and
   passed all `331/331` selected tests with zero failures, errors or skips on a
   newly created disposable database. The required schema query matched
   `44/44/0/1`, the wrapper exited `0`, its trap deleted the database and an
   independent information-schema query returned `0`.

The schema-proof success assertion did not execute on attempt three because the
test selector failed first, so attempt three itself carried no migration or
phase-gate claim. Attempt five subsequently supplied the authoritative green
schema proof and Phase 13F result recorded above.

### 24.2 Complete grouped diagnosis for attempt three

- One production defect existed in `PracticeService.startAttempt`: an
  `IN_PROGRESS` attempt with matching immutable identifiers could be reused
  even after its compatibility state became `STALE`. The reuse decision now
  requires both the exact lock and canonical resumability, so restart discards
  the stale row and creates a fresh attempt.
- Three `PracticeServiceTest` cases predated the mandatory published-version
  lock/snapshot contract. Their fixtures now provide the current immutable
  Reading snapshot; the legitimate reuse case also carries the matching lock.
- The direct-player integration case now proves that restart creates a
  different attempt and that the old incompatible attempt is discarded before
  it exercises the separate incoherent-section path.
- One progress fixture represented a persisted resumable attempt without an
  identifier; it now has a persisted identity.
- One progress assertion compared `BigDecimal` scale-sensitive object identity
  instead of numeric value; it now checks the two maxima numerically.
- One incompatible-terminal progress case configured canonical identity reads
  that the fail-closed compatibility branch intentionally never performs; the
  unnecessary stubbing was removed.
- The retry authorization-order test assumed exactly one question read. The
  production command intentionally revalidates the selected immutable source
  before and after locking. The test now preserves the authorization-before-read
  ordering while allowing those required defensive rereads.

The fourth-attempt failure had one test-only root. The helper now constructs
the snapshot before opening the outer `publishedVersionService.snapshot(...)`
stubbing expression. This does not alter production behavior or weaken an
assertion.

`PHASE_13F_VALIDATION = COMPLETE_FOCUSED_GATE_GREEN` is now authoritative.
The gate does not claim a full suite, browser/device QA, standalone application
startup, Docker build or live provider/API call. Phase 13 remains open. The
next required implementation unit is the separately scoped `13C3-00`; all
`13C3-00..04` work and its own consolidated gate must finish before 13G.
