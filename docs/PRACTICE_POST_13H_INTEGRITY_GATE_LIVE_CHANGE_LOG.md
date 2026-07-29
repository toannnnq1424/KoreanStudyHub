# Practice Post-13H Integrity Gate — Live Change Log

## 1. Gate identity

- Gate: `POST_13H_PRACTICE_INTEGRITY_GATE`
- Base branch contract: `feature/practice-reduce-scope`
- Exact audited base commit:
  `422aa362c7ac4b55604a933202543c83503cac7a`
- Exact audited base tree:
  `4ba78a0872ec57f2dc8eac3bce23cd849ad1f035`
- Working branch:
  `codex/post-13h-practice-integrity-gate`
- Publication boundary: this branch may merge only into
  `feature/practice-reduce-scope`. This gate must not open or merge a PR to
  `main`.
- Current decision: **GROUPED_CORRECTION_AUTHORIZED_IN_PROGRESS**. The user
  explicitly authorized correction of the proven supplemental-validation
  blockers. Publication remains blocked until a fresh consolidated lifecycle
  is green.

Phase 13H is an accepted baseline. Its focused 235/235, full 2398/2398,
fresh Flyway V1→V57 and Hibernate startup, 35 browser assertions with 19
screenshots, dependency scan, and zero-provider-call audit are not being
reopened without new evidence.

## 2. Mandatory discovery completed before mutation

The current versions of the following authority documents were read and
reconciled before this log or implementation code was created:

1. `CODEX_PRACTICE_WORKFLOW.md`
2. `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`
3. `docs/PRACTICE_PHASE_13H_STABILIZATION_VISUAL_JOURNEY_LIVE_CHANGE_LOG.md`
4. `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`
5. `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`
6. `docs/PRACTICE_PRE_PHASE_14_COMPREHENSIVE_AUDIT_AND_DEAD_SURFACE_CLEANUP.md`
7. `docs/PRACTICE_POST_PHASE_13_PRODUCT_INTEGRATION_AND_PACKAGE_RECONCILIATION.md`
8. The whole-project audit attachment at
   `/Users/toanlamsaoduocc/.codex/attachments/fc57d33d-219c-4699-9795-a5b205897482/pasted-text.txt`

Focused `rg --files` and Markdown contract searches also reconciled the
Phase 12/13/13C3/13D/13E/13F/13G plans and live logs, the architecture
manifest and sequence diagrams, the single-scope audit, the Phase 14 research
checkpoint, and the OpenSpec Practice contracts. The old whole-project audit
is treated as input, not as a current pass.

## 3. Independent current-snapshot audit evidence

Three independent read-only audits ran against the exact clean base commit
and tree above. They did not edit files, compile, test, migrate, use a browser,
or call AI/STT/TTS providers.

### 3.1 Architecture, ownership, duplication, and dead surfaces

- Confirmed that the old R25 synchronous lifecycle concern remains after
  13H's transport fixes.
- Confirmed R27's missing public learner autosave and server deadline.
- Proved a cross-collaborator stored-XSS sink in draft validation rendering.
- Proved an owner/hostile-filename stored-XSS sink in the import asset drawer.
- Proved that a legacy mixed/ESSAY Speaking submit can still reach
  text-simulated score production.
- Proved the generic Result Detail template cluster is dead, but classified
  its removal as non-blocking cleanup rather than part of this integrity
  patch.
- Confirmed Practice-local AI and storage ownership is coherent and must
  remain separate from Admin/common implementations.

### 3.2 Scope, regression, security, and data integrity

- Confirmed global resume selection is independent of catalog filters, but
  found that the shared detail/catalog resume policy had no deadline
  rejection and could disagree with the global query after expiry.
- Confirmed typed player/result ownership and answer-leak protections.
- Confirmed tests currently default to the developer `ksh_db`, while
  non-transactional Practice integration selectors can commit destructive
  setup writes.
- Confirmed saved answers are neither exposed through a learner route nor
  hydrated into the player.
- Confirmed the timer is resettable client state with no server enforcement.
- Confirmed provider-disabled Speaking can remain misleadingly pending.
- Confirmed R26/PDF has no current regression evidence and is not reopened.

### 3.3 Assessment, provider, persistence, and journey correctness

- Confirmed Writing and Speaking provider work still completes inside the
  submit HTTP lifecycle, before the terminal attempt write.
- Confirmed current Writing and Speaking transports now have explicit bounds;
  the stale R25 “missing timeout” wording is closed.
- Confirmed concurrent first submits can duplicate provider work before the
  later optimistic conflict.
- Confirmed current transcript-only Speaking correctly refuses acoustic,
  pronunciation, fluency, and holistic scores. This contract is preserved.
- Confirmed provider failures are fail-closed and no canonical pre-submit
  answer leak is present.
- Confirmed Writing re-evaluation can preserve an old result but still flash
  a false success message; the async command outcome must correct this.

Provider-call count through discovery: AI `0`, STT `0`, TTS `0`.

## 4. R25–R28 current verdict

| Finding | Current verdict | Gate action |
|---|---|---|
| R25 synchronous W/S evaluation | Open, narrowed to HTTP lifecycle, durability, retry, and idempotency | Implement durable bounded asynchronous evaluation |
| R26 PDF AI bounds | Closed by 13H | Do not reopen |
| R27 autosave/global resume/timer | Autosave and timer open; filter-independent global selection closed; expired-attempt consistency open | Add CAS autosave/hydration and one shared server-deadline resume rule |
| R28 XSS/dead Result surfaces | Two current XSS sinks open; typed result overview active; generic detail cluster dead but non-blocking | Fix proven sinks; defer broad dead cleanup |
| R32 test DB isolation | Open and stronger than old audit | Add fail-fast disposable test DB contract |

## 5. Accepted implementation scope

### IG-01 — Test database isolation

- Add a test-resource datasource override that never falls back to
  `DB_URL`/`ksh_db`.
- Require an explicit disposable `TEST_DB_URL` for database-backed tests and
  reject production/developer-like catalogs before Flyway or test setup can
  write.
- Keep Flyway forward-only and `clean-disabled=true`.
- Do not rewrite unrelated test fixtures in this gate unless the isolation
  guard exposes a specific collision.

### IG-02 — Learner answer durability and CAS

- Add an authenticated, CSRF-protected learner answer-save route.
- Require the expected attempt `lockVersion`.
- Owner-check, require canonical immutable IN_PROGRESS delivery, enforce the
  server deadline, and accept only question IDs from the locked snapshot.
- Return a typed saved response with the new version and authoritative time
  data; return typed HTTP 409 on stale revisions without overwriting.
- Hydrate server-saved answers into Reading/Listening/Writing players.
- Debounce saves in the learner player and expose saved/conflict/failure
  states without making browser storage authoritative.
- Keep Speaking draft interruption non-resumable.

### IG-03 — Server deadline authority

- Freeze an explicit `deadline_at` from attempt start and immutable section
  duration, with a forward-only migration/backfill.
- Render the authoritative deadline/server time; the browser timer becomes a
  display and auto-submit convenience only.
- At or after the deadline, reject late answer mutations and terminalize from
  the last server-saved answer snapshot.
- Exclude expired attempts from the global resume candidate and the shared
  detail/catalog resume policy. Starting the same section after expiry
  preserves and terminalizes the existing server snapshot rather than
  discarding it.

### IG-04 — Durable subjective evaluation

- Persist learner answers and the submitted attempt before provider work.
- Create one idempotent durable evaluation job per attempt, keyed to the
  immutable attempt graph and a frozen answer/media fingerprint. This is the
  smallest unit that reuses the existing whole-attempt and single-question
  immutable grading snapshots without creating duplicate assessment
  ownership.
- Claim the attempt job with a lease; run providers outside database
  transactions; enforce bounded attempt/deadline limits; and fence completion
  against stale attempt/job/input identity. The existing whole-attempt or
  single-question grading snapshot remains the only evaluation unit—this gate
  does not add a second per-question task aggregate.
- Apply the attempt result only from a terminal, actively leased job outcome.
- Expose honest QUEUED, PROCESSING, SUCCEEDED, FAILED, and UNAVAILABLE
  presentation states.
- Provider-disabled Speaking must become terminal UNAVAILABLE with zero calls,
  never permanent PENDING.
- Re-evaluation becomes an idempotent enqueue command with an honest outcome,
  not a synchronous provider request or unconditional success flash.

### IG-05 — Stored-XSS authority boundaries

- Replace shared-draft validation message interpolation with DOM construction
  and `textContent`.
- Replace import asset-card title interpolation/inline handlers with DOM
  construction and property assignment.
- Validate stored asset titles at the service authority for database length
  and unsafe control characters while preserving exact Korean, Vietnamese,
  emoji, quotes, ampersands, and other ordinary Unicode text.
- Do not HTML-encode or destructively sanitize stored language content.

### IG-06 — Speaking assessment integrity

- Remove every production path that creates Speaking fluency/pronunciation or
  numeric Speaking evidence from text length or a text ESSAY compatibility
  route.
- Fail closed for non-canonical Speaking question graphs.
- Preserve retained-history readers; do not destructively rewrite old rows.

## 6. Explicitly out of scope

- R26/PDF AI work already closed by 13H.
- Common/Admin AI or storage unification, package moves, consumer redirects,
  or shared-implementation cleanup.
- Broad legacy redirect removal or external-bookmark assumptions.
- Generic Result Detail dead-template removal and other non-blocking dead
  methods.
- Phase 14 bundle/model feature work.
- Provider smoke tests or any real AI/STT/TTS call.
- Any `main` mutation, PR, or merge.

## 7. Validation ledger

Implementation units are not validation units. Until
`READY_FOR_PHASE_VALIDATION`, changes receive static diff review only—no
compile, build, test, migration, browser run, or provider smoke.

The one consolidated validation lifecycle will use Java 17 and a fresh,
disposable database/environment:

1. `git diff --check`
2. one compile/build
3. the smallest focused selector covering every gate boundary
4. fresh Flyway V1→new head plus Hibernate startup because this gate changes
   persistence
5. browser QA for autosave/resume/CAS/deadline/async state/XSS journeys
6. full suite only if final breadth or uncertainty requires it
7. static and runtime provider-call count audit

If validation fails, all failures will be analyzed together, one grouped
correction will be made, and the same consolidated lifecycle will be rerun
once.

Current status: `SUPPLEMENTAL_VALIDATION_AUTHORIZED_IN_PROGRESS`

## 8. Implementation ledger

### 8.1 Persistence and job authority

- Added forward-only Flyway V58 with `deadline_at`, `last_saved_at`, a
  deadline-aware resume index, and the durable
  `practice_attempt_evaluation_jobs` table.
- Added a single-attempt unique job identity, frozen SHA-256 input
  fingerprint, explicit persisted model/prompt/rubric/schema contract
  identity, bounded attempts and 30-minute job window, lease owner and expiry,
  retry wait/backoff, terminal result/error evidence, and bounded manual retry
  audit fields.
- Added a scheduled worker with a two-slot dedicated executor. It claims in a
  new transaction, evaluates outside database transactions, renews a
  two-minute lease every 30 seconds, interrupts work at a 20-minute execution
  bound, and applies only a live leased completion.
- Shortened the generated lease owner below the database/entity 100-character
  bound after current-diff review proved the first implementation generated
  101 characters and could not claim any job.
- Replaced the reusable JVM/job lease owner with a fresh persisted UUID
  fencing token for every claim. Renewal, timeout failure, completion, and
  ordinary failure all require that exact live token, so reclaimed work
  cannot accept output from its previous execution.
- Split lease-heartbeat work from timeout authority. A dedicated timeout
  scheduler now fences the durable job independently at the execution bound
  and cancels further heartbeat work even when provider code ignores
  interruption; late output remains unable to complete the job.
- Retryable provider outcomes enter bounded retry-wait with exponential
  backoff; expired leases, exhausted attempts, changed fingerprints/contracts,
  and stale completions cannot overwrite a newer result.
- Provider-disabled Speaking is terminalized as unavailable at enqueue time
  with zero provider calls.
- Concurrent first Writing re-evaluation requests now observe the same durable
  job identity before taking the row lock. A request that waited behind the
  active job returns `ALREADY_QUEUED`, and a losing atomic insert reloads the
  winner instead of surfacing a duplicate-key failure or starting duplicate
  provider work.
- A retryable job failure now leaves the attempt in its honest queued state
  without publishing a transient terminal-unavailable payload. First
  submission therefore remains pending, while re-evaluation preserves the
  last graded result until a later leased attempt reaches a terminal outcome.
- Writing provider interruption is now propagated as worker lifecycle control
  instead of being normalized into a terminal provider-unavailable result.
  Graceful executor shutdown leaves the active lease untouched, allowing the
  durable job to be reclaimed after lease expiry on restart; timeout and
  lease-loss fencing remain independently authoritative.

### 8.2 Learner durability and deadline

- Added the owner-scoped CSRF PUT answer route with expected `lockVersion`,
  immutable-question whitelisting, typed SAVED/CONFLICT/DEADLINE_EXPIRED
  responses, and no silent merge on stale revisions.
- Reading, Listening, and Writing now hydrate saved server answers.
- The player debounces server saves, tracks dirty and persisted generations,
  drains edits made during an in-flight request, uses a bounded three-retry
  exponential backoff for transient failures, updates the returned revision,
  blocks a stale tab, and makes the visible timer derive only from server
  deadline and server render time.
- Submit is single-flight: repeated clicks/Enter events share one autosave
  drain, submit controls remain fenced during it, and exactly one native POST
  can leave the page. A server-reported deadline still uses that same fenced
  path and submits only the last durable snapshot.
- Learner exit now uses the same autosave drain before navigation, including
  edits made during an in-flight save. It navigates only after the durable
  generation is acknowledged and routes a server-reported deadline through
  the fenced terminal-submit path.
- Normal submit flushes autosave first. Deadline submit ignores late browser
  values and terminalizes the last server-saved snapshot.
- The server-rendered timer hook was restored on both ordinary and Writing
  players, so the visible countdown again binds to the authoritative
  `deadline_at`/server-time values instead of silently disappearing.
- Expired attempts are excluded from global and shared detail/catalog resume
  eligibility. A bounded server-side deadline reconciler now finalizes closed-
  browser Reading/Listening/Writing attempts from the last saved snapshot and
  discards incomplete Speaking attempts through the existing media cleanup
  authority.
- Deadline reconciliation failures now persist a bounded retry count,
  exponential next-attempt time, error category, and final quarantine marker.
  The due query skips backed-off/quarantined poison rows, preventing the
  oldest malformed snapshot from starving later learner deadlines while
  retaining explicit operational evidence instead of silently deleting it.
- Failure accounting re-checks due/backoff/quarantine eligibility under the
  pessimistic row lock, so stale selections from multiple application nodes
  cannot consume several retry slots or quarantine a row in one cycle.
- Global resume compares against an application-bound `now` parameter instead
  of a database-session timestamp, avoiding JVM/database timezone disagreement
  at the deadline boundary.
- At the submit/deadline crossing, non-Writing essays reload and compare the
  locked durable answers before scoring; Writing deadline terminalization
  similarly reloads the locked snapshot instead of rejecting it as a stale
  browser revision. Controller redirects now tolerate a worker winning the
  same terminal race and route submitted/graded attempts to result while
  discarded Speaking attempts return to test detail.
- Speaking media upload now recognizes the typed HTTP 410
  `DEADLINE_EXPIRED` response on both initial and retry upload paths, removes
  the local blob, disables retry, and submits the terminal route rather than
  leaving a permanently retryable client-only draft.

### 8.3 Honest assessment lifecycle

- Production Writing/Speaking submit now persists SUBMITTED + QUEUED before
  provider work and redirects immediately.
- Result overview exposes queued, processing, unavailable, and successful
  lifecycle states.
- Split terminal analysis `FAILED` from `UNAVAILABLE` at the attempt
  authority. Provider-disabled/unavailable Speaking now renders UNAVAILABLE
  consistently in Result and catalog/shared-state policy, while internal
  worker failures remain honest FAILED states and preserved prior grades
  remain PARTIAL.
- Writing transport failures now carry explicit retryability: missing
  configuration and permanent HTTP/contract failures are non-retryable,
  retryable HTTP/transport failures remain bounded retries, and unexpected
  failures terminate as failed. Per-question re-evaluation keeps prior
  feedback separately from the new failure metadata so preserved grades do
  not mask the current terminal class.
- Writing and Speaking aggregation now assign `UNAVAILABLE` only when every
  non-success entry is an explicit provider/media unavailable outcome.
  Contract, malformed, untrusted, missing, or otherwise invalid evidence
  remains `FAILED`, including mixed-result payloads; error codes are selected
  from that same terminal class.
- V58 replaces the existing attempt-analysis check constraint so the new
  `UNAVAILABLE` authority value is legal on both upgraded and fresh schemas.
- Speaking job contract identity now hashes every behavior-affecting
  transcription/evaluator gate, provider endpoint, model, language,
  log-probability policy, media limit/MIME set, timeout/retry policy,
  evaluator version, evidence contract, and text-fallback flag. Queued work
  fails closed on node/deployment drift instead of silently changing
  low-confidence or availability classification.
- Speaking pipeline preflight now requires both feature gates, the supported
  transcription/evaluator provider names, and both nonblank credentials
  before either provider can run. The contract identity records only
  credential-presence booleans (never credential contents), so incomplete or
  unsupported configuration is a zero-call unavailable path.
- Partial or missing Speaking feedback cannot be reported as a successful
  evaluation; every immutable Speaking question must have a trusted current
  evidence contract (with an explicit current low-confidence exception).
  Failure aggregation considers every non-success question so a later
  retryable failure cannot be masked by an earlier success.
- Writing re-evaluation now enqueues idempotently and reports QUEUED,
  ALREADY_QUEUED, RATE_LIMITED, or RETRY_LIMIT_REACHED instead of flashing
  unconditional success. A job grants at most two lifetime manual
  re-evaluation requests.
- Removed the text-length simulated Speaking producer; non-canonical/mixed
  Speaking graphs now fail closed and retained-history readers remain.

### 8.4 Static regression coverage staged

- Added focused unit/static-resource coverage for Writing retryability and
  terminal classification, graceful worker interruption/reclaim semantics,
  Speaking preflight/terminal classification, restored timer hooks, exit
  autosave drainage, and typed Speaking deadline handling.
- Added integration scenarios for transient Writing retry staying pending,
  permanent Writing unavailable completion, preservation of a previously
  graded question during failed re-evaluation, concurrent first
  re-evaluation idempotency, and discarded Speaking redirect behavior.
- These selectors are staged only. Per the gate policy, no compile, test,
  migration, browser run, or provider smoke has been performed during
  implementation; they will run together only after final static review marks
  `READY_FOR_PHASE_VALIDATION`.

### 8.5 XSS and test-data boundaries

- Shared draft validation messages and import asset cards now render
  untrusted text with DOM `textContent`/properties and fixed event listeners.
- Remaining dynamic draft-preview values at the reviewed stored-content
  boundaries are escaped before HTML insertion; crop preview URLs and region
  identifiers are attribute/text escaped.
- Asset-title authority enforces nonblank, 255-code-point, no-control-character
  storage while preserving ordinary Unicode content exactly.
- Added a test-classpath datasource override requiring explicit
  `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`, plus an
  environment post-processor that rejects `ksh_db`, shared `ksh_test`, and
  every catalog not named with a unique `ksh_test_<run_id>` pattern before
  DataSource/Flyway/test fixture creation.

Provider-call count through implementation: AI `0`, STT `0`, TTS `0`.

## 9. Final static review and validation readiness

Three independent read-only reviews re-opened the exact latest shared diff
after the grouped corrections. Architecture/ownership, scope/security/data
integrity, and assessment/provider/persistence reviewers all returned
`CLEAR`. Their final delta review specifically covered:

- locked deadline crossing, timer hooks, exit autosave drainage, controller
  terminal races, and discarded Speaking routing;
- async lease/input/contract fencing, concurrent first re-evaluation,
  retry-payload preservation, strict provider preflight, and honest
  `FAILED`/`UNAVAILABLE` classification;
- Writing interruption propagation plus processor-owned graceful-shutdown
  fencing before claim, after a provider return, and through exception flow,
  including a provider that consumes interrupt and still returns success;
- V58, entity/repository/native-insert/constructor/test call shapes, XSS/title
  boundaries, answer redaction, and disposable test-database enforcement.

No reviewer edited files or ran build, test, migration, browser, diff-check,
or provider operations. Final static-review provider counts remain AI `0`,
STT `0`, TTS `0`.

The consolidated focused selector is:

`DisposableTestDatabaseEnvironmentGuardTest,PracticeAttemptDeadlineReconciliationTest,PracticeAttemptEvaluationJobTest,LecturerAssetTitleValidationTest,PracticeAttemptDeadlineTransactionsTest,PracticeAttemptEvaluationProcessorTest,PracticeFunctionalUiContractTest,PracticeSpeakingMediaUiResourceTest,SpeakingEvaluationApplicationServiceTest,OpenAiCompatibleSpeakingEvaluationClientTest,OpenAiSpeakingTranscriptionClientTest,WritingEvaluationClientTest,WritingEvaluationNormalizerTest,PracticeResultPresenterTest,PracticeAttemptStatePolicyTest,PracticeCatalogServiceTest,PracticeServiceTest,PracticeSpeakingMediaServiceTest,PracticeIntegrationTest`

It will run inside the one clean Java 17 package/build against a uniquely
named disposable MySQL catalog. Because this gate changes a core attempt
aggregate, controller/player paths, durable scheduling, result presentation,
test bootstrap, and a forward migration, breadth requires one full-suite tail
on a second disposable catalog after the focused/migration proof. Browser QA
will then cover only the changed Practice journeys with all provider and
background-generation flags disabled.

## 10. Consolidated validation lifecycle

### 10.1 First run and grouped correction

- `git diff --check`: `PASS`, exit `0`.
- Runtime/toolchain preparation confirmed OpenJDK `17.0.19`, Maven `3.9.16`,
  and four previously nonexistent empty `ksh_test_post13h_*_054205`
  catalogs. A local-only `ksh_gate_054205` account has privileges only on
  those four disposable catalogs.
- The one clean Java 17 focused package began with provider credentials empty
  and all Practice evaluation, explanation, prompt AI/STT/TTS, cleanup, and
  deadline workers disabled.
- Maven Enforcer passed and production compilation examined all `750`
  sources, then stopped before test compilation, migration, or test execution
  on exactly two compile errors in `PracticeService`:
  1. the new async unsupported-action branch referenced the nested
     `PracticeReEvaluationNotAllowedException` without its owning
     `PracticeAttemptStatePolicy` qualifier; and
  2. the retained compatibility re-evaluation log referenced the removed
     local `score` variable instead of `earnedPoints`.
- The grouped correction qualifies the nested exception and logs
  `earnedPoints`. No behavior, persistence, provider, or test contract changed
  beyond those two compile corrections.

No migration, browser, provider, STT, or TTS action ran in the failed build.
Provider counts remain AI `0`, STT `0`, TTS `0`. Per the locked policy, the
same consolidated lifecycle will be rerun once after static compile-shape
review; there will be no class-by-class probe or extra intermediate build.

The three independent static sweeps of that grouped correction are `CLEAR`:
the two compiler failures are resolved, changed production and test
constructor/record/repository/method shapes reconcile, and every focused
selector class resolves. No sweep edited files or ran build/test/provider
work.

### 10.2 Single permitted rerun result — NO_GO

- The rerun began with a second `git diff --check`: `PASS`, exit `0`.
- Maven Enforcer again confirmed Java `17.0.19` and Maven `3.9.16`.
- All `750` production sources compiled successfully with release 17. The
  only production warning is the pre-existing unannotated deprecation warning
  in `LibraryStorageService`.
- Test compilation then examined all `271` test sources and stopped on one
  missing static AssertJ import: the new CAS integration assertion at
  `PracticeIntegrationTest` used `assertThatThrownBy` without importing it.
- The source now includes
  `org.assertj.core.api.Assertions.assertThatThrownBy`. This is a test-only
  compile correction; it does not change production behavior.

The permitted grouped-correction rerun is exhausted. No focused test,
Flyway migration, Hibernate startup, full suite, browser QA, provider, STT, or
TTS action ran because Maven stopped at test compilation. A third lifecycle
has not been attempted. The four disposable catalogs are retained untouched
for an explicitly authorized additional rerun; a read-only
`information_schema` check confirms `0` tables in each catalog.

Gate decision: **NO_GO**. There is no green validation evidence, so this
branch must not be committed, pushed, opened as a PR, or merged. Publication
can resume only if the user explicitly authorizes one additional complete
validation rerun after this recorded test-import correction.

### 10.3 Explicit Recovery GO

The user explicitly authorized one supplemental consolidated validation as a
narrow exception to the earlier two-cycle cap because the prior rerun stopped
at the single missing `assertThatThrownBy` test import before any intended
runtime evidence.

Recovery preflight confirms:

- branch `codex/post-13h-practice-integrity-gate` still points to the exact
  Phase 13H base commit `422aa362c7ac4b55604a933202543c83503cac7a` and base
  tree `4ba78a0872ec57f2dc8eac3bce23cd849ad1f035`;
- the implementation diff/file inventory is unchanged in scope;
- the only source correction after the recorded NO_GO is the intended static
  AssertJ import in `PracticeIntegrationTest`; other post-NO_GO edits are this
  recovery evidence in the dedicated live log; and
- all four restricted disposable catalogs still exist with `0` tables.

The supplemental run will repeat the same diff-check, clean Java 17 focused
package, fresh V1->V58/Hibernate proof, breadth-required full suite, changed-
journey browser QA, and provider-count checks exactly once. Provider/API
credentials remain empty and all AI/STT/TTS/background-generation workers
remain disabled.

### 10.4 Supplemental consolidated run result — material NO_GO

- Recovery `git diff --check`: `PASS`, exit `0`.
- The same clean Java 17 focused package compiled both production and test
  sources successfully, proving the single `assertThatThrownBy` import
  correction. Surefire then executed all `19` requested suites and `444`
  tests: `292` passed, `2` failed, `150` errored, and `0` were skipped.
- Two direct assertion failures remain:
  1. `PracticeFunctionalUiContractTest`
     `dedicatedExamPlayersShareNavigationSafetyAndAdaptiveReadingContracts`
     still requires three removed client-owned timer implementation strings
     (`configured <= 0`, `ksh-exam-timer:v2:${attemptId}`, and
     `storedValue === null ? Number.NaN`) even though the gate replaced that
     authority with server deadline epochs.
  2. `PracticeResultPresenterTest`
     `speakingMissingFeedbackRemainsPendingWithoutFabricatingZero` expected
     `PENDING`, but the current presenter returned `FAILED` for the tested
     missing-feedback state.
- Five `PracticeServiceTest` submission paths errored with
  `EntityNotFoundException: Không tìm thấy lượt làm bài` at the new locked
  `findByIdAndUserIdForUpdate` lookup, showing that retained unit fixtures do
  not satisfy the strengthened transactional ownership lookup.
- The remaining `145` errors are Spring-context/database fallout, but their
  root cause is itself a gate blocker rather than test noise. V1 still executes
  `CREATE DATABASE IF NOT EXISTS ksh_db`; the intentionally restricted
  `ksh_gate_054205` account is allowed only on the named disposable catalogs.
  Flyway therefore recorded failed V1 in
  `ksh_test_post13h_focus_054205`, after which the integration and Speaking
  media contexts failed or refused validation. The post-run catalog proof is
  exactly `1` table (`flyway_schema_history`) with `1` row: `0` successful,
  `1` failed, version `1`.
- Because the focused validation is not green, the gated V1->V58/Hibernate
  startup, full suite, and browser QA did not run. Running those downstream
  units could not supply valid evidence from a failed validation unit.
- Provider/API credentials were empty, all relevant workers and provider
  flags were disabled, no provider audit/log table was created before V1
  failed, and no real external provider action ran. Counts remain AI `0`, STT
  `0`, TTS `0`.

Final supplemental decision: **NO_GO**. Per Recovery GO, no additional patch,
commit, push, PR, or merge was performed. The feature branch and `main` remain
untouched.

### 10.5 User-authorized grouped correction after NO_GO

The user explicitly authorized correction of every proven blocker that can be
closed safely. The grouped static correction is intentionally limited to the
supplemental evidence:

- the dedicated-player contract now asserts the server deadline epoch inputs
  and deadline-submit path instead of three removed client-owned timer/cache
  implementation strings;
- the Speaking missing-feedback pending test now supplies the required durable
  lifecycle evidence (`ANALYSIS_QUEUED`) rather than treating an unknown/null
  lifecycle as pending forever; and
- the five objective submit fixtures now stub the transaction-locking
  `findByIdAndUserIdForUpdate` authority used by production submission.

No production behavior or historical Flyway checksum was changed. The V1
failure was caused by the validation account being too restricted to execute
the accepted historical bootstrap statement
`CREATE DATABASE IF NOT EXISTS ksh_db`; the next lifecycle will use a fresh
uniquely named disposable catalog with the normal local migration bootstrap
credential, while the datasource URL and test guard still bind all generated
tables and test fixtures to that disposable catalog.

No compile, test, migration, browser, provider, commit, push, PR, or merge has
run during this grouped correction. Provider counts remain AI `0`, STT `0`,
TTS `0`.

### 10.6 First post-NO_GO correction run and complete failure analysis

- Pre-run `git diff --check`: `PASS`, exit `0`.
- An initial catalog-creation command stopped before Maven because the local
  bootstrap password key was read under the wrong property name; MySQL
  reported `using password: NO`. No catalog or validation action ran in that
  stopped command.
- The corrected lifecycle created fresh catalog
  `ksh_test_post13h_fix_061130` with `0` initial tables and ran the same clean
  Java 17 focused package with provider credentials empty and workers
  disabled.
- Production `750/750` and test `271/271` sources compiled. Flyway V1->V58
  completed with `58` successful and `0` failed migrations, and the catalog
  contains `104` tables. This proves the former `145` context errors were a
  validation-account bootstrap mismatch, not independent product failures.
- Surefire executed `444` tests: `433` passed, `2` failed, `9` errored, `0`
  skipped. The complete de-duplicated causes are:
  1. five objective submit fixtures now reach the deadline authority but lack
     their required future `deadlineAt` value;
  2. the non-writing essay stale-write integration callback enters the
     compatibility autosave overload, whose locked repository query lacked an
     outer transaction because the annotated overload is reached by
     self-invocation;
  3. two freshly queued Writing job tests call `claim` with a new wall-clock
     value rather than the persisted job due timestamp and receive an empty
     claim;
  4. the preserved Writing feedback assertion compares JSON formatting rather
     than JSON structure;
  5. the concurrent first re-evaluation requests can both reach `INSERT
     IGNORE`, producing a real MySQL deadlock rather than the required
     `QUEUED`/`ALREADY_QUEUED` pair; and
  6. Speaking discard retains the immutable attempt row by design, while the
     test cleanup deleted version parents without flushing its attempt delete,
     violating `fk_pa_section_version`.
- The grouped correction adds future deadlines to the five fixtures, makes the
  compatibility autosave entry point transactional, serializes first durable
  re-evaluation creation on the attempt lock while avoiding an active-job lock
  inversion, claims tests at the job's persisted due timestamp, compares
  preserved feedback as JSON, and flushes Speaking attempt deletion before
  version cleanup.

Both provider audit tables remain empty: `ai_request_logs=0` and
`practice_ai_request_audits=0`; real provider counts remain AI `0`, STT `0`,
TTS `0`. No browser, full-suite, commit, push, PR, or merge action ran.

### 10.7 Second correction run and final routed-discard fix

- Static review and `git diff --check` passed before a second fresh catalog,
  `ksh_test_post13h_fix2_061924`, was created with `0` tables.
- The clean Java 17 package again compiled all `750` production and `271` test
  sources and migrated V1->V58. Surefire executed `444` focused tests with
  `443` passed, `1` failed, `0` errors, and `0` skipped.
- Every deadline, transaction, due-time, JSON, concurrency, and FK-cleanup
  blocker from section 10.6 closed. The only remaining failure proved a real
  route-authority mismatch: `GET /practice/attempts/{id}` intended to redirect
  an owner-bound discarded attempt to its test detail, but the shared
  `getPracticeAttempt` method rejected discarded rows before the controller's
  terminal redirect branch and returned 404.
- The final correction adds a narrow owner-bound routing lookup that may read
  the discarded status but exposes no result/player content. Only the canonical
  attempt GET route uses it; result, media, submission, resume, and scoring
  authorities retain the existing fail-closed discarded-attempt rejection.

No provider action, browser, full-suite, commit, push, PR, or merge ran.
Provider counts remain AI `0`, STT `0`, TTS `0`.

### 10.8 Discard-route contract de-duplication

The next fresh focused run compiled and migrated cleanly, then executed `444`
tests with `443` passed, `1` failed, `0` errors, and `0` skipped. The new
owner-bound discarded Speaking route passed. The sole failure was the older
generic discard integration test still asserting 404 for the same owner GET.

That stale assertion now follows the single canonical contract: an owner GET
of a discarded attempt redirects to the immutable test-detail destination,
while the result URL remains 404 and discarded rows remain excluded from
progress/detail completion counts. Other-user access remains fail-closed.

No production code changed in this de-duplication. Provider counts remain AI
`0`, STT `0`, TTS `0`; no browser, full-suite, commit, push, PR, or merge ran.

### 10.9 Green supplemental lifecycle through the full suite

- The final fresh focused catalog, `ksh_test_post13h_fix4_062242`, started
  empty, passed `git diff --check`, compiled all `750` production and `271`
  test sources with Java 17, migrated Flyway V1->V58, built the JAR, and ran
  the complete gate selector: `444/444` passed with `0` failures, `0` errors,
  and `0` skipped.
- A separate initially empty startup catalog,
  `ksh_test_post13h_start_054205`, validated and applied all `58` migrations,
  initialized the Hibernate entity manager, and started embedded Tomcat on an
  ephemeral port. The process then shut down normally. Post-startup evidence
  is `58` successful migrations, `0` failed migrations, maximum version `58`,
  and `104` tables.
- The full suite then ran once on the verified-empty disposable catalog
  `ksh_test_post13h_full_054205` with Java 17, all Practice provider and worker
  switches disabled, and provider credentials empty. It passed `2439/2439`
  tests with `0` failures, `0` errors, and `0` skipped in `01:34`.
- The `192.0.2.1:9` and `.example.test` failures emitted by the Admin AI test
  fixtures are reserved/non-provider endpoints exercising timeout and logging
  behavior. After the full suite, both durable provider audit tables are
  empty: `ai_request_logs=0` and `practice_ai_request_audits=0`. Real provider
  counts therefore remain AI `0`, STT `0`, TTS `0`.

### 10.10 Changed-journey browser QA

A fresh browser catalog, `ksh_test_post13h_browser_054205`, migrated V1->V58
and served the built JAR on the local test port with every Practice provider,
worker, upload, and playback switch disabled. The seeded learner account ran
the changed journeys in the in-app browser. Twenty-one bounded assertions
passed:

- the learner authenticated and the Vietnamese Practice catalog exposed all
  six seeded sets;
- a Writing attempt rendered finite server-now/deadline epoch values, no
  `correctAnswer`/`answerKey` field or attribute, and its CAS lock version;
- keyboard input reached the server-owned autosave endpoint, displayed
  `Đã lưu bài làm.`, incremented the lock version, and survived a full page
  reload as the resumed answer;
- Writing submission redirected to the typed Result contract with
  `Đang chờ chấm`, an explicit background-evaluation notice, no available
  score, and a per-task pending state while the worker was disabled;
- the Speaking device-check journey stated that recording storage was off and
  kept `Bắt đầu phần Nói` disabled. No text-only fluency/pronunciation score or
  synthetic fallback appeared;
- an objective Reading player also exposed server deadline authority and no
  pre-submit answer key. Moving only the disposable fixture deadline into the
  past caused the next safe player exit/autosave to finalize the attempt and
  redirect to the submitted result with two honestly unanswered questions;
- the discarded-attempt owner route redirected to the canonical immutable test
  detail, while the discarded result route remained fail-closed with 404. The
  browser's native confirmation layer stalled the disposable discard click,
  so the tombstone state was applied directly to the isolated fixture before
  testing the two read routes; controller/service discard behavior remains
  covered by the green integration selector; and
- at a `390x844` viewport the pending Writing result retained its Vietnamese
  lifecycle notice, main content, and zero horizontal overflow. The changed
  journey emitted zero browser-console errors.

The browser catalog ended with one intentionally queued Writing job and no
worker claim. Both provider tables remain empty: `ai_request_logs=0` and
`practice_ai_request_audits=0`; real provider counts are AI `0`, STT `0`, TTS
`0`. The application then completed graceful shutdown. No screenshot artifact
was required for these DOM/route assertions.

Final static review, commits, push, PR, and merge remain pending.

### 10.11 READY_FOR_PUBLICATION static reconciliation

- Final `git diff --check` passed after the validation evidence was recorded.
- The worktree remains on `codex/post-13h-practice-integrity-gate`; both its
  current HEAD and `origin/feature/practice-reduce-scope` still resolve to the
  authoritative Phase 13H merge `422aa362c7ac4b55604a933202543c83503cac7a`.
- Conflict-marker, accidental credential, TODO/FIXME/HACK, and unintended
  IELTS-label searches found no gate-introduced blocker. The only IELTS text
  in the searched Practice production surface is the pre-existing explicit
  prohibition inside the KSH Speaking prompt contract.
- No dependency file changed, so the already-green Phase 13H dependency scan
  was not reopened. No provider smoke or real AI/STT/TTS action ran.
- Production, migration, focused, full-suite, startup, browser, responsive,
  fail-closed, and provider-count evidence are all green. The implementation
  is ready to be divided into coherent review commits and published only to
  `feature/practice-reduce-scope`.

Commit, push, PR, merge-commit, ancestry, and tree verification remain pending.

### 10.12 Coherent review commit split

The validated tree was divided without semantic changes after validation:

1. `c7aaf053` — `fix(practice): harden asset title and markup boundaries`;
2. `41dc2e49` — `test: require disposable database catalogs`;
3. `8b722ffb` — `feat(practice): enforce durable attempt integrity`; and
4. `2bc1e8d5` — `test(practice): cover post-13H integrity boundaries`.

The only corrections made while staging were removal of trailing blank lines
from newly created source/resource files reported by `git diff --check`; no
runtime or test behavior changed. This live journal is the final documentation
commit. One push, the feature-targeted PR, merge commit, and ancestry/tree
verification remain pending.

## 11. Migration reconciliation note — 2026-07-29

This journal truthfully records the historical validation snapshot in which
the unpublished Practice integrity migration was named V58. Before integration
with independently advanced main, that exact 4,413-byte SQL payload was renamed
to `V62__practice_attempt_integrity_gate.sql`. Its SHA-256 is unchanged at
`b01c99a66c49822b1887cff2f62ac2c424e51feee6ebb3ee5eefc0ee244a6629`.
Historical test counts and V58 evidence above are not rewritten. Main's own
V57-V60 migrations remain untouched.
