# CODEX Practice Workflow Standard

Korean Study Hub — Feature `/practice`

This file must be read before Codex performs any audit, implementation,
stabilization, commit/push, or roadmap update task for this project.

## Source of Truth Priority

Use this priority:

1. Actual repository code at current HEAD.
2. Latest user-provided Codex report.
3. This workflow MD.
4. Latest context handoff.
5. Locked product policy confirmed by the user.
6. DOCX/technical notes.
7. Older proposals.

Rules:

- Actual code/report describes current behavior.
- Product policy defines target behavior only after compatibility impact is audited.
- Candidate directions are not locked decisions.
- Do not silently resolve contradictions.

When sources conflict, classify as:

- CODE_DEFECT
- STALE_DOCUMENTATION
- UNRESOLVED_PRODUCT_DECISION
- BACKWARD_COMPATIBILITY_CONSTRAINT
- PHASE_DEPENDENCY

## Phase Documentation Ledger Rule

From this point forward, every practice phase and every approved sub-slice
within a phase must leave enough written evidence in this workflow file for
future audit, comparison, and commit review.

Before the user is asked to commit/push a slice, phase, closure document, or
major stabilization batch, Codex must update `CODEX_PRACTICE_WORKFLOW.md` when
the task changed implementation, operating policy, phase status, accepted debt,
test evidence, or downstream routing.

The workflow update must record, as applicable:

- the phase and sub-slice objective;
- the actual implementation scope;
- the files or component areas affected at a useful summary level;
- the operating rules and invariants introduced or preserved;
- focused test evidence and any full-suite evidence;
- accepted debt, explicit deferrals, and downstream phase routing;
- rollout or production-readiness verdicts;
- compatibility notes for legacy data/attempts/views;
- any user decisions that changed the implementation direction.

Do not defer this documentation until memory is stale. The workflow file should
be updated during the final validated step before the corresponding commit
request, unless the user explicitly asks for commit-only handling of an already
prepared documentation diff.

Do not mark a top-level phase closed merely because the ledger was updated.
Closure still requires the relevant review/gate outcome or an explicit user
override.

## Product Language Policy

KSH is a Vietnamese/Korean learning website. Learner-facing website copy,
feedback, and UI labels must use Vietnamese and/or Korean only unless a
specific technical/admin context explicitly requires English. KSH is not an
English-learning product, and it is not primarily designed for American or
British English-speaking learners studying Korean.

## Workflow Modes

Define exactly four modes.

### 1. AUDIT ONLY

Purpose:

- read actual code;
- build call graph;
- identify current behavior;
- compare with locked policy;
- find risks/gaps;
- recommend exact next slice.

Rules:

- no file changes;
- no formatting;
- no migration;
- no provider call;
- no DB mutation;
- no stage;
- no commit;
- no push;
- no implementation.

Audit reports may say that this MD needs a future status update,
but AUDIT ONLY must not edit the MD unless the user explicitly changes the task to docs update.

### 2. IMPLEMENT CODE + FOCUSED TEST

Purpose:

- implement one approved slice;
- add/update focused tests;
- validate the behavior of the slice.

Rules:

- must be based on prior audit GO;
- no unrelated cleanup;
- no next-phase feature;
- no stage/commit/push;
- no full test suite by default;
- focused tests only unless user explicitly approves broader testing.

Implementation output status can be:

- IMPLEMENTED_PENDING_REVIEW
- IMPLEMENTED_AND_FOCUSED_TESTED

Do not mark top-level phase closed from implementation alone.

### 3. STABILIZATION / PHASE-GATE REVIEW

Updated rule:

Stabilization is normally reserved for top-level phase gates, not every tiny sub-slice.

Top-level phase gates include:

- end of Phase 8C;
- end of Phase 8D;
- end of Phase 8E;
- end of Phase 8F;
- end of Phase 8G;
- end of Phase 9;
- end of later major phases.

Small sub-slices such as:

- 8D-B;
- 8D-C;
- 8D-D;
- 8C.2A;
- 8C.2B;

should normally use implementation validation/review, not a separate stabilization phase.

Exception:

A sub-slice stabilization/review is allowed only when:

- the user explicitly asks for it;
- the slice is security/privacy/API-contract critical;
- the slice was already started under the older workflow;
- the user overrides the rule.

Even during stabilization/review for a small slice:

- focused tests only by default;
- no full suite unless user explicitly asks;
- no new feature;
- no stage/commit/push.

### 4. COMMIT AND PUSH

Purpose:

- package an already reviewed diff.

Rules:

- only after user/assistant review says GO;
- stage exact files only;
- no `git add .`;
- no `git add -A`;
- no `git add --all`;
- verify cached diff;
- commit with approved message;
- push only to approved remote/branch;
- no amend;
- no squash;
- no rebase;
- no force-push.

## Maven and Test Execution Policy

This section is critical.

### Default rule for small slices

For implementation and review of small slices:

- run focused tests only;
- do not run full suite;
- do not run repeated root Maven lifecycle;
- do not run parent/root build just to "check everything";
- do not run `mvn clean test` unless user explicitly approves.

Focused tests must target only the slice or directly affected behavior.

Focused test reruns are required during an active implementation or test-fix
task when the previous focused run found a compile error, test failure, or
test-fixture defect and the code or test was changed to address that evidence.

This repository policy overrides later task wording that says a focused Maven
command may run only once or must not be rerun after an evidence-based fix.
Codex must continue the same focused validation/fix cycle until the focused
tests pass or there is no concrete in-scope fix available. Future workflow
updates must not restore or record a one-run-only Maven restriction.

Each rerun must:

- keep the same narrow test scope, or reduce it further;
- have a concrete fix made since the previous run;
- avoid `clean`, the full suite, and unrelated modules;
- continue after each concrete in-scope fix until the focused tests pass;
- stop only when repeated failures no longer produce a concrete in-scope fix.

Do not rerun an unchanged command against unchanged code merely to see whether
an intermittent failure disappears.

### Full suite rule

Full suite is only expected for:

- top-level phase gate stabilization;
- before closing major phases such as 8D, 8E, 8F, 8G, Phase 9;
- high-risk security/privacy/transaction changes when user approves;
- explicit user request.

Full suite is not required for a small sub-slice commit unless user asks.

### Known Maven sandbox policy

If prior task evidence shows the current environment blocks Maven parent POM
resolution without elevated/sandbox permission, Codex must NOT first run a
known-to-fail non-escalated Maven command.

This is a hard rule, not a suggestion.

Do not waste a test attempt, tool call, or token budget just to reproduce the
same known sandbox failure.

Do not run the parent/root Maven command normally first when prior evidence
already shows that parent POM resolution or Maven execution needs elevated test
permission in the current environment.

Instead:

1. Ask the user for permission before the first Maven run when the current
   prompt does not already grant the needed test permission; OR

2. If the current prompt already grants test permission, run the focused Maven
   command with the required permission from the start and report that it used
   the known required test permission.

3. If a focused run finds an actionable in-scope defect, fix that defect and
   rerun the focused command with the same required permission. Do not insert a
   non-elevated probe between elevated runs.

When the prompt explicitly authorizes required test permission, Codex should use
that permission at the start of the Maven command. It must not run a doomed
non-escalated command first and then ask for permission afterward.

Forbidden pattern:

- run Maven normally;
- let it fail due known sandbox parent POM resolution;
- then run the same command again escalated.

That wastes tokens and is not allowed.

### Test escalation wording

Use this decision:

If Maven is required and prior evidence shows sandbox permission is needed:

- do not run non-escalated first;
- request or declare required permission before running;
- run only the focused command;
- rerun it after every evidence-based in-scope fix until it passes or becomes
  genuinely blocked;
- report exact reason.

If permission is unavailable:

report:

NEEDS_TEST_PERMISSION

and do not fabricate test results.

### Provider/network rule

Never call real AI providers in audit, implementation, or tests unless the user explicitly approves a provider-integration phase.

No real OpenAI/Gemini/Claude/provider calls by default.

## Escalation and Permission Policy

Default posture:

For repository-local work in `D:\Downloads\ksh` that the user has explicitly
requested, Codex should use the required tool permission immediately when the
operation is known or likely to need it.

Do not deliberately run a command in a restricted mode first when prior evidence
or the operation type already shows that it will need elevated/test/git/network
permission. That creates predictable failure noise and wastes tokens.

This applies to approved repo-local commands such as:

- focused Maven test commands;
- exact-path `git add`;
- `git commit` with the approved message;
- `git push origin feature/practice` when the user explicitly requested push;
- read-only or write operations inside the approved workspace.

Codex should still describe what it is doing and keep scope exact, but it should
not pause for an extra permission conversation when the current prompt already
authorizes the work and the required permission is only needed to complete that
same repo-local task.

Codex must stop and ask user before actions outside the normal repo-local trust
boundary, including:

- elevated OS permissions;
- administrator permissions;
- global environment changes;
- installing dependencies;
- external network calls unrelated to the explicitly requested git push or
  approved dependency/test operation;
- real AI/provider API calls;
- production DB access;
- DB mutation outside test context;
- cleanup jobs;
- deleting files;
- changing Git remote/branch;
- force-push;
- rewriting history;
- repeated expensive Maven/root builds;
- full suite outside phase gate;
- migrations;
- security/auth config changes;
- storage/object-storage config changes.

Codex must also stop before accessing suspicious, phishing-like, or unrelated
websites, and before reading, writing, deleting, staging, committing, or pushing
files outside the approved repository scope.

For Maven specifically:

Do not run a known-to-fail non-escalated command first.

If the current prompt already authorizes the test command, run every authorized
focused test attempt with the required permission from the start. This includes
evidence-based reruns after an in-scope compile or test fix.

If the current prompt does not authorize tests, ask once, then run the focused
command with the required permission if approved.

## Known Patch File Rule

Known unrelated file:

.codex-test-detail-csrf.patch

Allowed:

- mention its filename from git status.

Forbidden:

- open;
- read;
- apply;
- modify;
- stage;
- commit;
- push;
- delete.

This patch must remain untracked and untouched.

## Phase Status Labels

Use only these labels:

- NOT_STARTED
- AUDIT_IN_PROGRESS
- AUDIT_GO
- IMPLEMENTATION_IN_PROGRESS
- IMPLEMENTED_PENDING_REVIEW
- IMPLEMENTED_AND_FOCUSED_TESTED
- COMMITTED
- STABILIZATION_REQUIRED
- STABILIZATION_IN_PROGRESS
- STABILIZED_PENDING_COMMIT
- CLOSED_VERIFIED
- CLOSED_WITH_ACCEPTED_DEBT
- PROVISIONALLY_CLOSED_NEEDS_REVALIDATION
- REOPEN_REQUIRED
- BLOCKED
- DEFERRED

Rules:

1. Codex must not mark a phase CLOSED_VERIFIED just because code was written.

2. Code + focused tests means:
   IMPLEMENTED_AND_FOCUSED_TESTED

3. Sub-slice commit means:
   COMMITTED

4. Top-level phase closure requires:
   - audit evidence;
   - implementation evidence;
   - focused or phase-gate tests;
   - review acceptance;
   - commit/push if code changed;
   - phase-gate stabilization if required;
   - accepted debt recorded.

5. If user asks to mark a phase done without enough evidence, stop and ask.

## Phase Request Guard

Codex must immediately stop and ask if user request does not match current workflow state.

Examples:

1. User asks for implementation without audit GO:
NEEDS_AUDIT_FIRST

2. User asks for commit/push before review:
NEEDS_REVIEW_BEFORE_COMMIT

3. User asks for stabilization of a small slice:
Ask:
Do you want to override the rule that stabilization is normally reserved for top-level phase gates?

4. User asks for Phase 9 before Phase 8G is closed:
PHASE_9_BLOCKED_UNTIL_8G_CLOSED

5. User asks to work outside `/practice`:
NEEDS_SCOPE_CONFIRMATION

6. User asks to mark phase closed without evidence:
NEEDS_COMPLETION_EVIDENCE

7. User asks for React migration during current roadmap:
REACT_MODERNIZATION_IS_POST_PHASE_16_SCOPE

8. User asks for repeated full Maven/root build:
NEEDS_TEST_SCOPE_CONFIRMATION

9. User asks to touch `.codex-test-detail-csrf.patch`:
REFUSE_WITH_PATCH_GUARD

10. User asks to claim official TOPIK scoring:
REFUSE_OFFICIAL_TOPIK_CLAIM

## Roadmap Summary

Include this roadmap and status.

### Phase 1 — Remove Legacy R/L Explanation

Status:
CLOSED_VERIFIED based on prior roadmap context.

Purpose:
remove duplicate provider call, unify explanation source, reduce cost/privacy exposure.

### Phase 2 — AI Logging & Privacy Hardening

Status:
CLOSED_VERIFIED based on prior roadmap context.

Purpose:
avoid logging provider body, prompt, learner answer, evidence, cache key.

### Phase 3 — Transaction Boundary Hardening

Status:
CLOSED_VERIFIED based on prior roadmap context.

Purpose:
avoid long DB transaction around provider calls.

### Phase 4 — Per-Question Re-evaluate

Status:
CLOSED_VERIFIED based on prior roadmap context.

Purpose:
re-evaluate one Writing question while preserving other feedback.

### Phase 5 — Typed AI Result DTO

Status:
CLOSED_VERIFIED based on prior roadmap context.

Purpose:
typed score/rubric/evidence/result DTO and legacy compatibility.

### Phase 6 — Explicit Writing Task Metadata

Status:
CLOSED_VERIFIED as foundation.

Purpose:
support Q51/Q52/Q53/Q54/GENERAL identity.

### Phase 7 — Cache Retention Operations

Status:
CLOSED_VERIFIED as MVP, with operational debt.

Purpose:
Writing logical TTL, RL version-based retention.

### Phase 8 — Advanced Practice, Writing & Speaking AI

Status:
CLOSED_WITH_ACCEPTED_DEBT

Closure note:

Phase 8 is closed with accepted debt after Speaking media, Speaking AI
schema/transcription/evaluator/persistence/reuse, AI readiness, practice-wide
functional flow stabilization, and architecture/security boundary
maintainability were implemented, committed, and focused-validated. This
closure does not approve live Speaking AI rollout. Live Speaking AI rollout
remains NO-GO until the later rollout readiness, object-storage, calibration,
manual UAT, and operator approval gates are explicitly satisfied.

#### Phase 8B — Text Speaking

Status:
CLOSED_VERIFIED

#### Phase 8C — Writing Rubric, Criteria & Calibration

Status:
CLOSED_WITH_ACCEPTED_DEBT

Audit verdict:
8C_CLOSED_WITH_ACCEPTED_DEBT

Closure meaning:
Closed for internal KSH practice-score workflow dependencies.

Not allowed:

- do not claim official TOPIK-equivalent scoring;
- do not claim teacher-calibrated production scoring;
- do not claim perfect production calibration.

Accepted debt:

- teacher-reviewed calibration fixtures;
- repeated provider consistency;
- score weighting decisions;
- Phase 9 immutable rubric/content/scoring binding;
- Phase 13 broader result UI polish;
- safe-but-sparse rendering for unknown legacy criterion labels.

#### Phase 8D-A — Speaking Audio & Media Baseline Audit

Status:
AUDIT_GO

Verdict:
8D_FOUNDATION_VERIFIED_WITH_ACCEPTED_DEBT

Meaning:
Backend media foundation exists, but not production/browser ready.

#### Phase 8D-B — Speaking Audio Contract Stabilization

Status:
COMMITTED

Commit:
3156139081ef1480f605df91c8b99216069d43a3

Parent:
ca2f6f1298b7e556757b1e8fc1c840bb7fce44f4

Message:
feat(practice): stabilize speaking media contract

Meaning:
Safe upload/delete response contract metadata added and pushed.

#### Phase 8D-D — Range Playback and Browser Compatibility

Status:
COMMITTED

Commit:
0680e04c7c0f41e35b69a7307d804ef6695fb3a2

Parent:
03f781a74f46cd312c6ffe804dc9278b1f1414e3

Message:
feat(practice): add private audio range playback

Meaning:
Private local Speaking media playback now has focused-tested HTTP byte Range support for browser-compatible seeking.

#### Phase 8D-C — Browser Recording and Functional Audio UI

Status:
COMMITTED

Meaning:
Optional browser recording, session-only consent, upload progress, retry/delete/re-record,
player preview, and result/detail playback are implemented behind disabled-by-default gates.

#### Phase 8D-E — Cleanup Worker and Retention

Status:
COMMITTED

Meaning:
The disabled-by-default scheduled cleanup worker processes bounded due batches.
Cleanup eligibility requires both `due_at` and `next_attempt_at`, and discarded media
is not physically eligible before its 24-hour retention deadline.

#### Phase 8D-F — Production Storage

Status:
CLOSED_WITH_ACCEPTED_DEBT

Decision:
PRODUCTION_OBJECT_STORAGE_DEFERRED

Meaning:
Phase 8D remains on private local storage. This is suitable for development, testing,
and single-node staging only unless explicitly accepted for a small controlled deployment.
Object storage must be revisited after a provider and runtime access policy are selected.

Current planning update (2026-07-11): Cloudflare R2 is the selected production
object-storage target in principle. This does not reopen or rewrite the historical
Phase 8D-F decision: KSH has no R2 API credentials, bucket binding, public/custom
domain or runtime access policy yet, so integration remains `NOT_STARTED` and
local private storage remains the only implemented adapter.

Top-level Phase 8D:
CLOSED_WITH_ACCEPTED_DEBT

Closure note:
Phase 8D closed with accepted debt after the committed Speaking audio media flow,
smoke/debt acceptance, and explicit production debt confirmation.

Accepted debt:

- Historical Phase 8D-F state was `PRODUCTION_OBJECT_STORAGE_DEFERRED`; the
  later Cloudflare R2 target selection does not constitute an implemented or
  production-approved provider.
- Local private storage is limited to dev/test/single-node staging or an explicitly controlled deployment.
- Cleanup supports the single-node MVP assumption; multi-node duplicate processing and version conflicts remain accepted.
- Consent is session-only with no persisted or auditable consent record.
- Browser/device and recorder lifecycle smoke items are `NOT_TESTED_ACCEPTED_DEBT`.
- Full manual UAT remains deferred to Phase 15.
- Production hardening, stronger cleanup claiming/locking, and media operations remain deferred to Phase 8F.
- Practice-wide functional UI regression remains assigned to Phase 8G.
- Closure does not approve live Speaking audio rollout.

#### Phase 8E — Speaking AI Evaluation

Status:
CLOSED_WITH_ACCEPTED_DEBT

Purpose:
audio/transcript-based Speaking evaluation with fixed schema, internal score only.

Closure note:

Phase 8E is implementation-complete and focused phase-gate reviewed, but live
Speaking AI rollout remains NO-GO. Closure is with accepted debt because real
provider rollout, calibration, object storage, monitoring, manual UAT, broader
route/player/template stabilization, and architecture decomposition remain in
later phases.

#### Phase 8E-A — Speaking AI Schema, Status & Normalizer Foundation

Status:
COMMITTED

Commit:
6bccffec14e5df13c155327f369eaed420bbd38a

Meaning:
Typed Speaking evaluation status/result/evidence/rubric contracts, 100-point
internal scoring helpers, deterministic normalizer behavior, low-transcript-
confidence safeguards, legacy mock compatibility, and existing Speaking view
mapping foundation are implemented without provider or transcription calls.

Focused test evidence:
83 tests, 0 failures, 0 errors, 0 skips on JDK 17. The focused scope covered
the new Speaking AI foundation tests and existing `PracticeServiceTest`.

#### Phase 8E-B — Korean Transcription Integration Abstraction

Status:
COMMITTED

Commit:
d1960b4941cb039693000ed4d8b3670f818abcc8

Dependency:
OpenAI STT primary strategy selected for the MVP transcription adapter.

Meaning:
Speaking transcription client abstraction, safe request/result DTOs, disabled-by-
default OpenAI STT adapter, READY local-media resolver, failure/status mapping,
and privacy-safe `toString` behavior are implemented without evaluator scoring,
result persistence, UI tabs, or real provider calls in tests.

Focused test evidence:
38 tests, 0 failures, 0 errors, 0 skips on JDK 17. The focused scope covered
OpenAI STT adapter behavior, transcription media eligibility, and existing
Speaking evaluation normalizer/score policy guard tests.

Provider strategy note:

- `OPENAI_STT_PRIMARY` is the current 8E-B MVP default.
- `gpt-4o-mini-transcribe` is the default cost-oriented MVP model.
- `gpt-4o-transcribe` remains an accepted quality/confidence upgrade candidate.
- Groq Whisper and Deepgram remain accepted future fallback providers.
- Gemini remains evaluator-oriented for 8E-C unless native audio strategy changes.
- SpeechSuper/Azure remain deferred pronunciation-assessment providers.
- LangChain4j remains deferred and must not be added in 8E-B.
- Provider abstraction must remain open so KSH can swap or run multiple transcription providers later.

#### Phase 8E-C — Speaking Evaluator Fixed-Schema Provider Integration

Status:
CLOSED_WITH_ACCEPTED_DEBT

Dependency:
8E-B transcription foundation.

Meaning:
Speaking evaluator prompt rules, fixed-schema OpenAI-compatible chat adapter,
provider-neutral `SpeakingEvaluationClient`, evaluator handoff/orchestrator,
S_* Speaking criterion IDs, and rich feedback contract are implemented without
persistence, UI rendering, cache, migrations, or real provider calls.

Focused test evidence:
46 tests, 0 failures, 0 errors, 0 skips on JDK 17. The focused scope covered
Speaking evaluator prompt rules, fixed-schema request building, OpenAI-
compatible adapter failure mapping, orchestrator handoff behavior, rich
normalizer contract, and score policy guard tests.

Evaluator strategy note:

- `EXISTING_GEMINI_EVALUATOR_COMPATIBLE` is the current 8E-C MVP default.
- Speaking evaluator uses separate `app.practice.speaking-evaluator.*` config.
- OpenAI structured outputs remain accepted fallback/upgrade path.
- Provider-neutral `SpeakingEvaluationClient` must remain open for future evaluator swaps.
- `SpeakingPromptRules` uses KSH `allowed_rubric` `max_score` discipline and
  does not use 9-band or few-shot calibration.
- `SpeakingRuleEngine` provides deterministic pre-evaluation signals adapted
  for spoken Korean.
- Speaking criterion IDs use the `S_*` namespace.
- 8E-C evaluator output supports overall summary, strengths, needs improvement,
  action plan, criterion breakdowns, subcriteria, transcript annotations,
  upgraded answer, and sample answer.
- The result contract is PREP-inspired only as product structure; KSH must not
  copy UI or claim official IELTS/TOPIK scoring.
- Pronunciation remains advisory unless a specialized provider/timestamp/alignment
  pipeline is added later.
- Writing rubric scale consistency requires the separate 8E-CW audit.
- 8E-C does not cache evaluator results.
- 8E-C does not persist or render results; 8E-D owns persistence/UI.

8E-C follow-up:

- Speaking prompt contract cleanup is implemented and focused-tested.
- Evidence must preserve exact transcript text without translation,
  normalization, or rewriting.
- The Speaking prompt no longer assumes fixed max 20/max 15 weights; it uses
  each `max_score` supplied by `allowed_rubric`.
- `criterion_feedback` and `action_plan` item schemas are explicit.
- No Writing files were modified.
- Focused validation: 42 tests, 0 failures, 0 errors, 0 skips on JDK 17.
- Top-level 8E remains `IN_PROGRESS`; 8E-D and 8E-CW remain `NOT_STARTED`;
  8H remains `PLANNED`.

#### Phase 8E-CW — Writing Rubric Scale Consistency Follow-up

Status:
IMPLEMENTED_AND_FOCUSED_TESTED

Purpose:
A narrow maintenance slice, audited before implementation, to remove remaining
Writing 1–9 band, few-shot, and sample-answer scoring assumptions and enforce
`allowed_rubric` `max_score` discipline. This does not reopen all of Phase 8C.

Implementation note:

- `WritingPromptRules` was restored from current HEAD before surgical cleanup,
  preserving detailed evidence, finding, task-specific, upgrade, and output rules.
- The provider prompt no longer uses 1-9 bands, lexical score caps, or few-shot
  calibration and no longer requests an independent `sample_answer`.
- Provider scoring uses `allowed_rubric.scoring_criteria` with stable
  40/30/30 `max_score` values; backend computes percentage from rubric scores.
- `needs_improvement` correction safeguards and upgraded-answer/rewrite rules
  were strengthened.
- Legacy 1-9/raw-score/sample-answer stored JSON remains readable.
- Focused validation: 101 tests, 0 failures, 0 errors, 0 skips on JDK 17.
- No Speaking files were modified.

#### Phase 8E-CW2 — Writing Task-Native Scoring Matrix

Status:
COMMITTED

Commit:
this commit

Parent:
5908a93755003f2c1df37899a95dd11e1a4be076

Message:
feat(practice): add task-native writing scoring

Pushed branch:
origin/feature/practice

Purpose:
Replace the temporary uniform 40/30/30 provider rubric with task-native Writing
maximums while preserving historical Writing feedback compatibility.

Task-native scoring policy:

- Q51: two answer blanks, 5 points per blank, 10 points total. Each blank uses
  2 points for context, 2 points for grammar, and 1 point for expression.
- Q52: two answer blanks, 5 points per blank, 10 points total, with the same
  2/2/1 breakdown.
- Q53: Content 12, Organization 9, Language 9, 30 points total.
- Q54: Content 20, Organization 15, Language 15, 50 points total.
- GENERAL: Content 40, Organization 30, Language 30, 100 points total.

Implementation note:

- `WritingScoringPolicy` is the single task-native scoring source for provider
  criteria, maximum totals, and earned-score percentage calculation.
- New normalized results explicitly expose earned `raw_score`,
  task `raw_score_max`, `percentage`, and `TASK_NATIVE_RUBRIC_V1`.
- `PracticeService` prefers explicit percentage/contract metadata and no longer
  infers every score at or below 9 as a legacy Writing band.
- Writing result rendering uses each rubric row's `maxScore` instead of a
  hard-coded `/10`.
- Legacy 1-9 Writing JSON remains readable through the existing compatibility
  path; no migration or database schema change was added.
- Focused CW2-A validation: 5 tests passed.
- Focused CW2-B validation: 77 tests passed after an evidence-based stale
  cache-version fixture correction.
- Focused CW2-C validation: 79 tests passed.
- Consolidated CW2-D validation: final evidence-based rerun passed 182 tests,
  0 failures, 0 errors, 0 skips on JDK 17 after correcting the legacy-band
  percentage projection; no full suite and no provider calls.
- No Speaking files were modified.

#### Phase 8E-D — Speaking AI Persistence and Result Rendering

Status:
COMMITTED

Purpose:
persist normalized Speaking AI feedback and render result/detail tabs while
preserving 8D playback and current legacy Speaking feedback compatibility.

Implementation notes:

- 8E-D1 persisted/read the `speaking_ai_v1` per-question Speaking AI feedback
  envelope through the existing `ai_feedback_json` JSON flow without adding a
  migration.
- 8E-D1 preserved legacy/mock Speaking feedback and mixed Speaking/Essay
  compatibility.
- 8E-D2 extended Speaking result view mapping for rich result/detail rendering:
  status, score availability, summaries, criterion feedback, transcript
  annotations, action plan, upgraded answer, sample answer, confidence notes,
  advisory pronunciation, and safe media metadata.
- 8E-D2 result/detail rendering supports functional Speaking tabs for Overall,
  Content, Vocabulary, Grammar, Fluency, Pronunciation, Upgraded answer, and
  Sample answer while preserving protected 8D audio playback.
- 8E-D2 removed native-like pronunciation wording from the Speaking rendering
  path; pronunciation remains advisory.
- 8E-D3 focused validation passed for the persistence/readback, view-mapping,
  result-rendering contract, and legacy compatibility slice; no full suite and
  no provider/API call.
- 8E-D is committed. Phase 8E-F later owns top-level 8E closure decision.

#### Phase 8E-E — Speaking Re-evaluation and Transcript Reuse Stabilization

Status:
COMMITTED

Purpose:
stabilize per-question re-evaluation, transcript reuse, prior-valid-result
preservation, and failure behavior without introducing a Speaking audio cache.

Implementation notes:

- 8E-E1 added an explicit Speaking AI identity/reuse policy for audio
  evaluations using `audioMediaId` plus `mediaVersion`/`lockVersion`, evaluator
  model, transcription model, prompt version, rubric version, schema version,
  and source/mode. No storage key/path is stored in the identity.
- 8E-E1 reuses stored successful or non-retryable same-identity results, but
  retryable failures remain eligible for a fresh submit-time attempt.
- 8E-E2 wires Speaking AI evaluation only on submit when both transcription and
  evaluator gates are enabled. Result page loads and re-evaluation flows do not
  call real Speaking AI providers.
- 8E-E2 text fallback is disabled by default and runs only when no valid READY
  audio exists, the learner text answer is nonblank, and product config enables
  text fallback. Text fallback results remain explicitly marked
  `TEXT_FALLBACK_EVALUATED`.
- 8E-E2 preserves a prior same-identity successful result when a new transient
  provider/evaluator failure is retryable; identity changes do not reuse old
  success.
- 8E-E3 rejects stale audio successes if active media identity changes after
  resolution and before persistence, persisting a safe `AUDIO_UNAVAILABLE`
  failure instead.
- 8E-E focused validation passed for reuse policy, submit-only integration,
  text fallback constraints, failure persistence, stale media identity guard,
  legacy/result rendering compatibility, and no provider/API call in tests.

#### Phase 8E-F — Speaking AI Focused Phase-Gate Review

Status:
STABILIZED_PENDING_COMMIT

Purpose:
focused Phase 8E review after 8E-D and 8E-E. Do not close top-level 8E before
this review and explicit accepted-debt decisions.

Focused phase-gate evidence:

- Foundation verified on branch `feature/practice` at
  `aa988d65e989e4091ee27b973af58a9c137a4c38`, matching
  `origin/feature/practice`; no tracked or staged diff before review.
- Focused command passed: `mvn "-Dtest=SpeakingEvaluationNormalizerTest,SpeakingScorePolicyTest,SpeakingPromptRulesTest,SpeakingEvaluationRuleEngineTest,SpeakingEvaluationOrchestratorTest,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingTranscriptionMediaResolverTest,OpenAiSpeakingTranscriptionClientTest,SpeakingFeedbackCompatibilityReaderTest,SpeakingFeedbackViewMapperTest,SpeakingResultRenderingContractTest,SpeakingEvaluationReusePolicyTest,SpeakingEvaluationApplicationServiceTest,PracticeServiceTest#speakingAiEnvelopeBuildsAndReadsRichPerQuestionFeedback+speakingFeedbackMapBuildsPerQuestionRowsWithoutLeakingAnswers+speakingLegacyOneObjectFeedbackIsMarkedAsGlobalCompatibility+speakingEmptyOrMalformedFeedbackProducesSafeRowsWithoutFeedback+mixedLegacySpeakingEssayPersistsVersionedEnvelopeAndAggregatesByQuestionRegardlessOfOrder+speakingAiSubmitEvaluatesOnceAndPersistsVersionedEnvelope+speakingReEvaluateDoesNotCallRealSpeakingAiService,WritingScoringPolicyTest,WritingTaskNativeScoringTest,WritingPromptRulesTest,WritingEvaluationClientTest,WritingEvaluationNormalizerTest,WritingFeedbackCompatibilityReaderTest,PracticeResultWordingTest" test`.
- Result: 196 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and
  no real provider/API call.
- Phase-gate inspection confirmed Speaking schema/status/normalizer,
  transcription, evaluator, persistence/rendering, and reuse/invalidation
  acceptance criteria for Phase 8E.
- Phase-gate inspection confirmed Writing 8E-CW/CW2 remains stable enough for
  the Phase 8E scope and was not reopened.

Accepted debt:

- Real provider live rollout remains disabled by default.
- No full production calibration or teacher-reviewed calibration fixture set.
- No production object storage switch.
- No multi-node cleanup, provider monitoring, or operational runbook.
- No background re-evaluation worker.
- No user-facing Speaking retry button.
- No full manual UAT.
- No Phase 9 immutable version graph.
- Broader route/player/template integration remains for Phase 8G.
- Architecture/service decomposition remains for Phase 8H.

Accepted debt routing:

- Real provider live rollout remains disabled by default -> Phase 8F.
- No production calibration -> Phase 8F and Phase 15.
- No teacher-reviewed calibration fixture set -> Phase 8F and Phase 15.
- No object storage production switch -> Phase 8F.
- No multi-node cleanup, provider monitoring, or operational runbook -> Phase 8F.
- No background re-evaluation worker -> Phase 8F if required for operations;
  otherwise explicitly defer from the MVP.
- No user-facing Speaking retry button -> Phase 8F if operationally required;
  otherwise Phase 13 UX.
- No full manual UAT -> Phase 15.
- No immutable version graph -> Phase 9.
- Broader route/player/template integration -> Phase 8G.
- Architecture, service decomposition, and security boundary cleanup -> Phase 8H.
- Visual polish / PREP-like redesign -> Phase 13.

Rollout verdict:

NO-GO FOR LIVE SPEAKING AI ROLLOUT.

#### Phase 8F — AI Production Hardening, Calibration & Rollout Readiness

Status:
IMPLEMENTED_AND_FOCUSED_TESTED

Purpose:
provider/live-rollout readiness, production calibration, teacher-reviewed
calibration fixtures, object-storage production switch, provider monitoring,
runbook, multi-node cleanup, privacy/security, and operational readiness.

Live Speaking AI rollout remains NO-GO until the Phase 8F rollout readiness
gate passes.

Implementation note:

- 8F-A provider rollout gates are implemented as backend readiness checks only.
  Live Speaking transcription allows the current OpenAI STT primary path, and
  Speaking evaluator allows the current OpenAI-compatible evaluator path. No new
  provider was added in 8F-A.
- 8F-B calibration fixture framework is implemented with minimum MVP counts:
  five Speaking fixtures and five Writing fixtures. Expected outcomes use score
  ranges / qualitative bands, not exact hard scores. Teacher-reviewed fixtures
  remain accepted debt for Phase 15, but 8F now has a fixture contract and
  expected-range validation.
- 8F-C provider reliability readiness is implemented through bounded
  observability tags and operator runbook checks for outage, rate limit, cost
  spike, bad feedback, media storage failure, and privacy incident. User-facing
  retry button remains deferred to Phase 13. Background re-evaluation remains
  deferred; 8E-E submit-only evaluation remains the MVP-safe path.
- 8F-D storage readiness policy records that local private storage is accepted
  only for dev/single-node staging. It is not production-final. Object storage
  remains a live-rollout blocker/decision before real Speaking AI rollout.
- 8F-E rollout checklist is implemented. Live Speaking AI remains blocked until
  provider gates, calibration framework, runbook readiness, storage decision,
  Phase 8G, Phase 8H, and manual UAT / accepted Phase 15 decision are all
  satisfied.

Focused validation:

`mvn "-Dtest=SpeakingProviderRolloutReadinessTest,AiCalibrationReadinessPolicyTest,ProviderOperationalReadinessPolicyTest,SpeakingStorageProductionReadinessPolicyTest,AiRolloutReadinessChecklistTest,PracticeAiMetricsTest" test`

Result: 31 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS on JDK 17.

Rollout verdict:

NO-GO FOR LIVE SPEAKING AI ROLLOUT.

Phase-gate review:

- 8F phase gate passed focused validation on 2026-07-10.
- Provider readiness checks remain backend/operator readiness checks only.
- Provider gates remain disabled by default and live Speaking AI rollout is not
  approved.
- Calibration fixture framework, bounded metrics/runbook readiness, storage
  production-readiness policy, and rollout checklist are accepted as the 8F MVP.

Accepted debt:

- Real Speaking AI live rollout remains NO-GO.
- Teacher-reviewed real calibration fixture set remains Phase 15 debt.
- Object storage production implementation/decision remains a blocker before
  rollout.
- Multi-node cleanup hardening remains accepted/deferred debt.
- Background re-evaluation remains deferred.
- User-facing Speaking retry button remains Phase 13 UX scope.
- Phase 8G functional integration is still required.
- Phase 8H architecture/security cleanup is still required.
- Phase 9 remains blocked until Phase 8G and Phase 8H closure or accepted
  deferral.
- Manual UAT remains Phase 15.

#### Phase 8G — Practice-Wide Functional UI / Integration Regression

Status:
CLOSED_WITH_ACCEPTED_DEBT

Must happen before Phase 9 unless explicitly accepted/deferred by user
decision.

Implemented slices:

- 8G-A route/data binding stabilization:
  set detail now renders actual PracticeTest IDs, legacy one-id mode/room routes
  redirect to canonical set detail without assuming a default test, mode
  create-attempt forms submit sectionId, and result back links preserve attempt
  testId.
- 8G-B player/template/JS contract stabilization:
  player question block selector now matches the rendered player template.
- 8G-C result/result-detail integration stabilization:
  Writing/Speaking result and Reading/Listening result navigation preserve
  setId/testId context, and Spring test context starts without real provider
  calls.
- 8G-D focused navigation and smoke contract tests:
  route/template/JS regressions are covered by focused MockMvc and file
  contract tests.
- 8G-E implementation validation:
  focused phase-gate review passed and Phase 8G is closed with accepted debt,
  with closure documentation committed.

Focused evidence:

`mvn "-Dtest=PracticeFunctionalUiContractTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId" test`

Result: 10 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS.

Notes:

- No full suite was run.
- No real AI/provider API call was made.
- No stage, commit, or push was performed.
- Phase 8H remains PLANNED.
- Phase 9 remains blocked until Phase 8H is closed, accepted, or explicitly
  deferred.
- Live Speaking AI rollout remains NO-GO.

Accepted debt:

- Broader manual browser/device UAT remains Phase 15.
- Visual polish and PREP-like redesign remain Phase 13.
- Player architecture split remains Phase 8H or Phase 13 if still desired.
- Any stale unused result fragments remain deferred unless confirmed as active
  breakage.
- Playback role/security boundary review remains Phase 8H.
- Phase 9 remains blocked until Phase 8H closure or accepted deferral.

#### Phase 8H — Practice Architecture, Security Boundary & Maintainability

Status:
CLOSED_WITH_ACCEPTED_DEBT

Scope:

- Constants boundary audit: `IConstant` must not keep growing as a
  route/view/model/flash/tab/pagination catch-all. Future split candidates are
  `PracticeRoutes`, `PracticeViews`, `PracticeModelAttributes`,
  `PracticeFlashMessages`, `PracticeTabs`, `PracticePagination`, and
  `PracticeDiscriminators`. UI text may later move to `messages.properties`
  when localization is needed.
- `PracticeService` decomposition audit: it currently coordinates attempts,
  questions, results, Writing AI, Speaking feedback, Reading/Listening
  explanation, audio, and transactions. Future split candidates are
  `PracticeAttemptCommandService`, `PracticeAttemptQueryService`,
  `PracticeAttemptGradingService`, `PracticeResultAssembler`,
  `PracticeAnswerMapper`, `WritingEvaluationApplicationService`,
  `SpeakingEvaluationApplicationService`,
  `ReadingListeningExplanationService`, and
  `PracticeAttemptTransactionCoordinator`.
- Practice AI package boundary audit: preserve focused tests while reviewing
  the current Speaking transcription/evaluation package candidates. Avoid a
  broad package refactor.
- Public uploads versus private learner media security audit: `/uploads/**`
  `permitAll` must remain clearly separated from authenticated private
  Speaking playback. Private learner media must never be served under public
  upload paths.
- Narrow refactor stabilization only: no big-bang cleanup; audit first;
  focused tests only; no Phase 9 until Phase 8H is accepted or explicitly
  deferred.

Implemented slices:

- 8H-A constants and route/view/model/form boundary:
  introduced narrow practice web constants for learner route paths, template
  names, model attributes, form answer fields, and private Speaking media
  route builders. Practice controllers and submit/save answer handling now use
  these focused constants without adding a broad catch-all constants class.
- 8H-B security and private media boundary stabilization:
  preserved current owner-protected private Speaking playback boundary,
  confirmed private learner media remains separate from public `/uploads/**`,
  and added tests to guard against storage key/path/provider diagnostic leaks.
  Playback role policy remains accepted debt for final 8H review because the
  product still needs to decide whether reviewer roles should access learner
  recordings.
- 8H-C narrow PracticeService maintainability extraction:
  extracted `PracticeAnswerFormMapper` for the player form answer-field
  contract, reducing duplicated `answer_` parsing in submit/save paths without
  moving grading, transactions, or Phase 9-sensitive attempt logic.
- 8H-D AI/result mapper boundary cleanup:
  added result/template boundary checks so result pages and fragments do not
  render provider raw body, API key, prompt body, storage key/path, or local
  private file path fields. No prompt, provider, scoring, schema, or AI gate
  behavior was changed.
- 8H-E focused implementation validation:
  focused tests passed; top-level 8H is closed with accepted debt as part of
  the final Phase 8 closure stabilization after the architecture-boundary
  implementation was committed.

Focused evidence:

`mvn "-Dtest=PracticeFunctionalUiContractTest,PracticeSpeakingMediaUiResourceTest,PracticeAnswerFormMapperTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId,PracticeSpeakingMediaPlaybackControllerTest,PracticeSpeakingMediaPlaybackServiceTest,LocalPrivateSpeakingAudioStorageTest" test`

Result: 68 tests, 0 failures, 0 errors, 2 skips, BUILD SUCCESS.

Final Phase 8 closure evidence:

`mvn "-Dtest=SpeakingEvaluationNormalizerTest,SpeakingScorePolicyTest,SpeakingPromptRulesTest,SpeakingEvaluationRuleEngineTest,SpeakingEvaluationOrchestratorTest,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingTranscriptionMediaResolverTest,OpenAiSpeakingTranscriptionClientTest,SpeakingFeedbackCompatibilityReaderTest,SpeakingFeedbackViewMapperTest,SpeakingResultRenderingContractTest,SpeakingEvaluationReusePolicyTest,SpeakingEvaluationApplicationServiceTest,SpeakingProviderRolloutReadinessTest,AiCalibrationReadinessPolicyTest,ProviderOperationalReadinessPolicyTest,SpeakingStorageProductionReadinessPolicyTest,AiRolloutReadinessChecklistTest,PracticeFunctionalUiContractTest,PracticeSpeakingMediaUiResourceTest,PracticeAnswerFormMapperTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId,PracticeSpeakingMediaPlaybackControllerTest,PracticeSpeakingMediaPlaybackServiceTest,LocalPrivateSpeakingAudioStorageTest" test`

Result: 182 tests, 0 failures, 0 errors, 2 skips, BUILD SUCCESS.

Playback range MockMvc stabilization evidence:

- `PracticeSpeakingMediaPlaybackControllerTest.rangeHeaderReturnsPartialContentWithRangeHeaders`
  preserves 206 Partial Content, `Content-Range`, `Accept-Ranges`, bounded
  `Content-Length`, and content type assertions without forcing the flaky
  async body dispatch path.
- `PracticeSpeakingMediaPlaybackControllerTest.getDoesNotRequireCsrf` uses a
  releasable test stream so GET/CSRF coverage remains intact without racing
  MockMvc async streaming against security header writes.
- Streaming body behavior remains covered by playback controller/service and
  local private storage focused tests.

Notes:

- No full suite was run.
- No real AI/provider API call was made.
- No stage, commit, or push was performed.
- Phase 8H is closed with accepted debt after final Phase 8 closure
  stabilization.
- Phase 9 may begin only as AUDIT ONLY after this Phase 8 closure docs update
  and playback test stabilization are committed and pushed.
- Live Speaking AI rollout remains NO-GO.

#### Final Phase 8 Speaking Evaluation Deep-Dive Policy

Speaking evaluation must keep four independent evidence layers:

1. Original audio reference:
   learner recording reference, media id, and route-owned playback reference.
   Do not expose public storage keys, storage paths, or local private file paths.
2. Transcript layer:
   transcript text, normalized transcript, transcript confidence, and
   timestamped transcript when the provider supports it.
3. Acoustic / speech evidence layer:
   pronunciation evidence, fluency evidence, pauses, repetition,
   self-correction, speech rate, and batchim / liaison / linking / intonation
   evidence only when supported by audio, timestamp, or provider evidence.
4. Language evaluation layer:
   task fulfillment, content development, vocabulary, grammar, spoken endings,
   honorifics / politeness / register, coherence, and organization.

No single-label collapse:

- A word, token, or phrase can simultaneously appear in multiple evidence
  categories.
- Example: a phrase can be lexically strong because it is used correctly and
  naturally, a pronunciation weakness if it is pronounced incorrectly, and
  fluency evidence if it is preceded by hesitation.
- Do not collapse the word into one label.

Korean Speaking criteria must remain independently auditable:

- answering the question/task;
- content development;
- coherence and flow;
- pauses;
- repetition;
- self-correction;
- speech rate;
- vocabulary;
- grammar;
- spoken sentence endings;
- honorifics / politeness / register;
- pronunciation;
- batchim;
- liaison / linking;
- stress / intonation only when evidence is reliable enough.

Pronunciation / acoustic evidence rule:

- Do not claim pronunciation or acoustic analysis from transcript alone.
- Transcript-only input may support language evaluation.
- Transcript-only input may say pronunciation evidence is unavailable or
  limited.
- Audio-backed provider evidence may support pronunciation and acoustic
  findings.
- Timestamps or provider evidence may support time-aligned findings.
- Do not claim exact pronunciation defects from plain transcript only.
- Do not claim batchim, liaison, linking, or intonation diagnosis without
  audio, timestamp, or provider evidence.
- Do not present suspected pronunciation issues as certain facts.

Status wording:

- Use "suspected pronunciation issue" only when evidence is partial.
- Use "audio evidence unavailable" or "transcript-only evaluation" where
  appropriate.
- Do not use native-like claims.
- Do not claim official TOPIK equivalence.
- Do not present medical or speech-therapy diagnosis.

Accepted Phase 8 debt:

- Real live Speaking AI rollout remains NO-GO.
- Full timestamped transcript support depends on provider capability and
  calibration.
- Full acoustic/speech evidence modeling remains accepted Phase 8 debt before
  live rollout.
- Production pronunciation/acoustic claims require provider evidence and
  teacher/UAT validation.
- Teacher-reviewed calibration remains Phase 15 debt.
- Object storage production decision remains a rollout blocker.
- Playback role/security boundary review remains accepted 8H debt unless later
  resolved by explicit product/security decision.
- Manual browser/device UAT remains Phase 15.
- UI/UX visual polish remains Phase 13.
- Immutable practice versioning remains Phase 9.

Phase routing:

- These Speaking evidence-layer rules belong to Phase 8 / Speaking evaluation
  policy.
- They must not be hidden inside Writing DTO work.
- They must not be treated as UI modernization.
- Later UI phases may render the evidence better, but the evidence model and
  policy belong to Speaking evaluation.

### Phase 9 — Immutable Published Practice Versions

Status:
CLOSED_WITH_ACCEPTED_DEBT.

Purpose:
immutable published content/rubric/scoring graph and stable historical attempts.

Locked Phase 9 decisions:

- Use normalized immutable version tables, not JSON-only snapshots.
- Store question options as JSON snapshot inside `practice_question_versions`
  for MVP.
- Published versions are append-only.
- Editing after publish creates a draft/new version; published versions are not
  mutated.
- Unpublish/archive blocks or hides new attempts only; old attempts must still
  render.
- New attempts must lock to `publishedVersionId`, `setVersionId`,
  `testVersionId`, and `sectionVersionId`.
- Section-specific attempts lock a section version. Full-test attempts must be
  supported by the test-level version graph, but full-test vs skill-specific
  product policy remains Phase 10.
- Result/detail rendering must use question version rows for prompt, options,
  answer key, points, and explanation snapshot when an attempt has a version
  lock.
- Legacy attempts without version IDs must keep a compatibility path.
- Existing attempts are handled by baseline migration/compatibility in 9E.
  Attempts created before immutable versioning are best-effort historical
  reconstruction if source content was already mutated before Phase 9.
- Media/materials use version-safe references only; do not copy private files
  and do not persist storage keys/private paths in version rows.
- Hidden/internal prompt bodies, provider raw bodies, API keys, and storage
  keys/private paths must not be persisted or rendered.
- Speaking Phase 8 evidence-layer policy becomes a versioned evaluation
  contract in Phase 9D: transcript-only supports language evaluation only;
  pronunciation/acoustic findings require audio/timestamp/provider evidence.
- Lecturer import validation remains Phase 11. UI/UX/version-selection work
  remains Phase 13. Program/certification mode rules remain Phase 10.

Implemented Phase 9 slices:

- 9A: normalized immutable version schema/entities/repositories and baseline
  migration design.
- 9B: publish creates immutable published graph versions; start attempt locks
  latest published version; no published version means no versioned start path.
- 9C: attempt scoring/result rendering use versioned question snapshots where
  locked, with legacy fallback.
- Phase 9 ungrouped/null-group stabilization is included: immutable version
  creation and the V24 baseline migration include grouped questions with
  `group_version_id` and null-group questions with `group_version_id = null`
  when the source test has a single section; versioned result rendering includes
  null-group questions through the same fallback/synthetic grouping style used
  by the legacy live path. Old live-fallback attempts remain compatible.
- 9D: AI/rubric/prompt/media compatibility is tied to the attempt version lock
  and existing Writing/Speaking compatibility readers; no provider raw body or
  prompt body storage added.
- 9E: baseline migration and legacy compatibility path implemented; old
  pre-Phase-9 attempts remain best-effort if historical source content was
  previously mutated.
- 9F: gate/closure review accepted Phase 9 as CLOSED_WITH_ACCEPTED_DEBT.

Phase-gate evidence:

- Review verdict: PHASE9_CLOSED_WITH_ACCEPTED_DEBT.
- Closure documentation commit: `ac658dc32a6b98d01760fd2cce352825876d53e5`.
- Full suite command: `mvn test`.
- Full suite result: 1160 tests, 0 failures, 0 errors, 2 skipped, BUILD
  SUCCESS.
- The 2 skipped tests are environment/permission-dependent symlink checks in
  `LocalPrivateSpeakingAudioStorageTest`
  (`rejectsSymlinkEscapeWhereSupported`, `rejectsSymlinkObjectWhereSupported`),
  not functional regressions.

Accepted Phase 9 debt:

- Existing pre-Phase-9 attempts are best-effort historical reconstruction if
  source content was already mutated before immutable versioning.
- Full-test vs skill-specific mode product policy remains Phase 10.
- Lecturer authoring/import validation remains Phase 11.
- UI/UX version selection, large catalog UX, and visual polish remain Phase 13.
- Broader manual UAT remains Phase 15.
- Live Speaking AI rollout remains NO-GO.

Post-closure Phase 9G stabilization before Phase 10:

Status:
IMPLEMENTED_AND_FOCUSED_TESTED pending review/commit.

Purpose:
close the remaining immutable-version safety concerns found after Phase 9
closure, without starting Phase 10 or changing the product UI.

- 9G-A: null-group question ambiguity is rejected for new publish/version
  creation when a set has multiple tests or a target test has multiple
  sections. The V24 baseline path snapshots null-group questions only when the
  set has exactly one test and that test has exactly one section. Ambiguous
  legacy attempts remain on compatibility/fallback metadata instead of being
  locked to a wrong section/test.
  Focused evidence: `mvn "-Dtest=PracticeServiceTest#publishedVersionIncludesUngroupedQuestion+publishedVersionRejectsUngroupedQuestionInMultiSectionTest+publishedVersionRejectsUngroupedQuestionInMultiTestSingleSectionSet+readingResultUsesLockedUngroupedQuestionVersionSnapshot" test`;
  4 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS.
- 9G-B: runtime published-version content hashing now fails closed if the hash
  cannot be computed. The failure is logged and publish aborts instead of
  silently saving `null`; the nullable migration column remains for baseline
  compatibility.
  Focused evidence: `mvn "-Dtest=PracticeServiceTest#publishedVersionFailsClosedWhenContentHashCannotBeComputed+publishedVersionIncludesUngroupedQuestion" test`;
  2 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS.
- 9G-C: Speaking transcription retry rebuilds the multipart request and audio
  resource for each retry attempt, preventing reuse of an already-consumed
  stream. Tests use fake transport only; no provider/API call.
  Focused evidence: `mvn "-Dtest=OpenAiSpeakingTranscriptionClientTest#retryRebuildsMultipartAndReopensAudioStream+http429And503MapRetryableTrue+transportTimeoutMapsRetryableTrue+buildsMultipartRequestWithModelFileLanguageJsonAndLogprobs" test`;
  4 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS.
- 9G-D: narrow mojibake cleanup corrected obvious Vietnamese UTF-8 corruption
  in active practice service labels/error messages and the PDF import safety
  message. This is not UI redesign. Broader UI/UX encoding review remains
  Phase 13/15.
  Focused evidence: `mvn "-Dtest=PracticeServiceTest#testGetLearningProgressOverview+testGetPracticeAnalytics+testProgressAnalyticsUsesAllAttemptsNotTop100ForAverage" test`;
  3 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS.
- 9G-E: workflow status was corrected after the Phase 9 closure commit. Phase
  10 remains NOT_STARTED. Production configuration hardening remains accepted
  debt: local datasource credentials, Flyway clean/validation posture, and
  deployment-specific profile separation must be handled before production
  rollout.
- 9G-F: final focused 9G validation must pass before the 9G commit request.
  Focused evidence: `mvn "-Dtest=PracticeServiceTest#publishedVersionIncludesUngroupedQuestion+publishedVersionRejectsUngroupedQuestionInMultiSectionTest+publishedVersionRejectsUngroupedQuestionInMultiTestSingleSectionSet+publishedVersionFailsClosedWhenContentHashCannotBeComputed+readingResultUsesLockedUngroupedQuestionVersionSnapshot+testGetLearningProgressOverview+testGetPracticeAnalytics+testProgressAnalyticsUsesAllAttemptsNotTop100ForAverage,OpenAiSpeakingTranscriptionClientTest#retryRebuildsMultipartAndReopensAudioStream+http429And503MapRetryableTrue+transportTimeoutMapsRetryableTrue+buildsMultipartRequestWithModelFileLanguageJsonAndLogprobs" test`;
  12 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS. User-approved full
  suite evidence before commit: `mvn test`; 1163 tests, 0 failures, 0 errors,
  2 skipped, BUILD SUCCESS. The 2 skipped tests remain the
  environment/permission-dependent symlink checks in
  `LocalPrivateSpeakingAudioStorageTest`, not functional regressions.

### Phase 10 — Academic Program / Certification Configuration

Status:
CLOSED_WITH_ACCEPTED_DEBT

Purpose:
assessment/program policy foundation for multiple Korean certification
programs and custom teacher practice modes. Phase 10 is backend/model/policy
foundation, not UI/UX, not lecturer import implementation, and not visual
redesign.

Implemented Phase 10 slices:

- 10A: canonical assessment vocabulary and fail-closed legacy alias resolver,
  including `MCQ -> SINGLE_CHOICE` compatibility.
- 10B: typed question content, answer spec, learner answer, scoring result,
  stimulus, prompt profile, and explanation context contracts with stable IDs.
- 10C: deterministic objective scoring strategies for single choice, multiple
  choice, true/false/not-given, fill blank, and matching; no provider call is
  involved in objective scoring.
- 10D: normalized assessment program, program version, question policy,
  scoring profile, prompt profile, and rubric profile persistence. Flyway V25
  seeds TOPIK and CUSTOM defaults while keeping KIIP/KLAT disabled until their
  policies are verified.
- 10E: live and immutable question/set snapshots include canonical type,
  answer spec, scoring policy/profile, program code, and prompt/rubric profile
  identity. Legacy columns remain compatibility inputs only.
- 10F: objective submit/re-evaluate/result paths delegate to the typed scoring
  engine while preserving locked-version and legacy-attempt behavior.
- 10G: Reading/Listening explanation uses typed, versioned context and cache
  identity. Provider output must contain meaningful fields and an evidence
  quote present in approved evidence; learner-specific correctness remains a
  deterministic overlay.
- 10H: program-policy integration and stabilization gate completed. Player
  delivery uses the attempt's locked graph and redacts answer/explanation
  fields before submit.

Phase 10 stabilization and security closure:

- draft, publisher, linked-draft, import session, annotation, asset, revision,
  and draft-asset ownership boundaries fail closed;
- manual/PDF publish creates the `PracticeTest -> PracticeSection -> Group ->
  Question` graph required by learner flow;
- annotation and asset actions bind child IDs to the parent session/region in
  the route;
- draft audio/image upload rejects path-like or unsupported extensions,
  enforces bounded sizes, and normalizes destinations under the configured
  upload root;
- AI/provider error responses, audit codes, file paths, storage keys, and
  content hashes are not exposed through the stabilized paths;
- immutable snapshot rows preserve source identity without blocking safe live
  graph replacement when no attempt exists.

Phase-gate evidence:

- compile and test-compile: BUILD SUCCESS on JDK 26.0.1;
- focused stabilization gate: 41 tests, 0 failures, 0 errors, 0 skips;
- final full suite used `sh mvnw` with the JDK 26 Byte Buddy compatibility
  argument and isolated MySQL datasource; 1208 tests, 0 failures, 0 errors,
  0 skips, BUILD SUCCESS;
- V25 integration/migration tests passed, and a clean local development schema
  was migrated from V1 through V25 before commit;
- no real AI provider call was made by the test suite.

Accepted Phase 10 debt and routing:

- type-specific lecturer editors and PDF/import normalization remain Phase 11;
- explicit persisted passage/transcript/stimulus binding remains Phase 11F;
- Admin/Head program, prompt, rubric, and scoring-profile governance remains
  Phase 12;
- full typed learner rendering for every new objective type, full-test attempt
  aggregation, catalog scale, and accessibility remain Phase 13;
- program-version identity is not yet a first-class set-version/attempt field;
  effective question/scoring/prompt identities are snapshotted now, while the
  broader delivery-policy lock is routed to Phase 13 before multi-program
  rollout;
- TOPIK Writing task/profile compatibility still resolves through current
  fixed policy adapters; editable/version-admin governance remains Phase 12;
- `PracticeService` remains large and must be decomposed incrementally in
  Phase 11/13, not by a big-bang refactor;
- production datasource/Flyway profiles, migration rehearsal, browser/device
  UAT, load, and provider/calibration GO remain Phase 15;
- live Speaking AI rollout remains NO-GO.

Locked Phase 10 decisions:

- Canonical MVP question types:
  - `SINGLE_CHOICE`
  - `MULTIPLE_CHOICE`
  - `TRUE_FALSE_NOT_GIVEN`
  - `FILL_BLANK`
  - `MATCHING`
  - `ESSAY`
  - `SPEAKING`
- Deferred question types:
  - `ORDERING`
  - `TEXT_COMPLETION`
  - `DRAG_DROP`
  - `LONG_FORM_PROJECT`
  - `PAIR_SPEAKING`
  - `FILE_UPLOAD_ANSWER`
- Legacy `MCQ`:
  - keep existing `MCQ` rows/code readable as a backward-compatible alias only;
  - map `MCQ -> SINGLE_CHOICE` in a policy/resolver layer;
  - do not keep `MCQ` as separate product behavior;
  - new content should use canonical `SINGLE_CHOICE` where supported;
  - tests must prove `MCQ` and `SINGLE_CHOICE` score equivalently.
- Scoring decisions:
  - `SINGLE_CHOICE`: exactly one correct option.
  - `TRUE_FALSE_NOT_GIVEN`: canonical type; scores like single-choice with
    `TRUE`, `FALSE`, and `NOT_GIVEN` values.
  - `MULTIPLE_CHOICE`: default `ALL_OR_NOTHING`; backend may also support
    `PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO`.
  - `FILL_BLANK`: accepted answers plus teacher-defined aliases using
    normalized exact matching. Regex is deferred/admin-only future work.
  - `MATCHING`: default `PER_PAIR`; optional `ALL_OR_NOTHING` may be modeled
    but does not require UI in Phase 10.
  - `ESSAY` and `SPEAKING`: use prompt/rubric/scoring profile references, not
    inline editable UI in Phase 10.
- Program configuration:
  - use normalized DB tables plus seed TOPIK defaults;
  - do not use config files as the source of truth;
  - UI for Admin/Head editing programs, prompts, rubrics, and scoring profiles
    is deferred.
- Data model:
  - do not rely on legacy `answer_key VARCHAR` as the long-term contract for
    complex question types;
  - add a versioned answer spec contract such as `answer_spec_json`,
    `scoring_policy_code`, and `canonical_question_type`;
  - snapshot the same fields into immutable question version rows, not only
    live question rows;
  - keep `answer_key` for legacy compatibility only.
- AI explanation:
  - standardize a backend `ExplanationContext` for Reading/Listening with
    `programCode`, `skill`, `questionType`, passage/instruction, prompt,
    options, answer spec, learner answer, correct answer, and explanation
    language;
  - AI explanation should consume normalized answer specs, not ad-hoc
    `answer_key` parsing.

Suggested normalized DB concepts:

- `assessment_programs`: `TOPIK`, `KIIP`, `KLAT`, `CUSTOM`, etc.
- `assessment_skills`: `READING`, `LISTENING`, `WRITING`, `SPEAKING`.
- `assessment_question_type_policies`: program, skill, question type, enabled
  flag, and default scoring policy.
- `assessment_scoring_profiles`: code, version, and config JSON.
- `assessment_prompt_profiles`: code, version, skill, task type, and enabled
  flag.

Seed TOPIK defaults:

- TOPIK Reading: `SINGLE_CHOICE` only for now.
- TOPIK Listening: `SINGLE_CHOICE` only for now.
- TOPIK Writing: `ESSAY` with Q51/Q52/Q53/Q54 policy references.
- TOPIK Speaking: `SPEAKING`.

Other certification/custom modes may later enable `MULTIPLE_CHOICE`,
`FILL_BLANK`, `MATCHING`, and `TRUE_FALSE_NOT_GIVEN`.

Allowed Phase 10 debt:

- no UI editor for program/prompt/rubric/scoring profiles yet;
- no `ORDERING` / `TEXT_COMPLETION` yet;
- no regex fill-blank yet;
- no product visual redesign.

Not allowed before later UI/UX work:

- missing canonical question type resolver;
- missing answer spec contract;
- missing scoring policy contract;
- missing immutable version snapshot for new answer/scoring fields;
- missing standardized Reading/Listening explanation context;
- missing tests for scoring behavior by question type.

Phase 10 operating process:

- Implement 10A, then run focused tests.
- If 10A passes, do a small audit before 10B to confirm whether the original
  10B plan still fits. Then implement 10B, or implement 10B version 2 if the
  audit changes the scope, and run focused tests.
- Repeat the same pattern for 10C, 10D, 10E, 10F, 10G, and 10H.
- After 10H, run a full test suite.
- If the full suite has any failure or error, stop and wait for user approval
  before fixing.
- If the full suite succeeds, update this workflow document with the final
  Phase 10 evidence and wait for user approval before commit/push.
- Every sub-slice must record implementation purpose, operating rules, focused
  evidence, accepted debt, and next-slice guidance in this workflow before the
  commit request.
- Do not start Phase 11, Phase 13, UI modernization, lecturer import
  implementation, or React modernization during Phase 10.

### Phase 11 — Lecturer Authoring & Import

Status:
CLOSED_WITH_ACCEPTED_DEBT

`PRE_PHASE_11_AUTHORING_AND_IMPORT_CONTRACT_GATE` was completed as an
audit-only slice on 2026-07-10. The user then explicitly approved complete
Phase 11 implementation. Phase 11A-11G were implemented on 2026-07-11 and the
11H automated/runtime stabilization gate is `CLOSED_GREEN`. The implementation
was accepted, committed and pushed on 2026-07-11 as `324dad9`. Phase 11 is
`CLOSED_WITH_ACCEPTED_DEBT`; its routed governance/material/learner/release debt
remains open in Phase 12/13/15. This closure does not start Phase 12.

Implemented scope:

- 11A introduced `practice-draft-v3`, stable client IDs, canonical typed
  question/answer data, typed stimulus/provenance persistence and explicit
  attempt score-unit/earned-points/percentage fields. Legacy `score` remains a
  compatibility field, so Phase 8 rubric/provider formulas are unchanged.
- 11B added V26 `assessment_exam_templates` and first-class program-version /
  template identity on draft, live set and immutable set version. The server
  authoring catalog resolves enabled skill/type/scoring/profile policy.
- 11C hardened the existing Thymeleaf editor for `SINGLE_CHOICE`,
  `MULTIPLE_CHOICE`, `TRUE_FALSE_NOT_GIVEN`, `FILL_BLANK`, `MATCHING`, `ESSAY`
  and `SPEAKING`; fields and policy controls follow the selected template.
- 11D added machine-coded backend validation and a sanitized draft preview
  built from learner delivery DTOs. It omits correct answers, answer specs,
  explanations, profile identities and listening transcript.
- 11E added deterministic `practice-excel-v2` workbook generation, one parser
  pipeline, row-level issues, centered preview and owner-scoped draft import
  for TOPIK and custom templates. The contract is `Set > Test > Skill Section
  > Group > Question`; enabled L/R/W/S skills can import when Admin/Head policy
  permits it. Invalid rows remain visible in preview but confirm silently
  excludes them, then compacts question numbers inside each lesson/section.
  The source row/question number remains visible for traceability.
- Excel preview exposes Test/Section, group instruction/passage/transcript,
  group/question media, prompt, teacher note/explanation, answer and Option
  A-H. Companion local image/audio files are matched by safe basename, rendered
  from local `blob:` URLs before confirm, uploaded only when used, and converted
  from `material:<ref>` to system-managed upload URLs. Full local paths are not
  persisted or returned.
- `MATCHING` uses stable left IDs `L1-L8` with dedicated text/image columns,
  Option A-H as right-side text/image items, and a correct map such as
  `L1=A;L2=B`. Preview shows both sides of each pair; pair IDs are validated
  against the supplied left and right items.
- 11F added guided full-selected-page PDF mode plus the existing advanced crop
  mode, synthetic traceable regions, canonical draft assembly, confidence /
  teacher-review gates and role-restricted raw prompt/request debug details.
  Raw page text is represented once in selected regions, not duplicated in
  page context and base text.
- 11G removed six dead editor modules that were not loaded and added two
  actually loaded canonical modules for authoring contract and preview. The
  inline editor remains large and is accepted incremental-maintainability debt.
- V26 also backfills existing draft/set/version bindings, group stimuli and
  attempt score fields. Restore of a legacy revision preserves the current
  authoring binding when the old snapshot has no Phase 11 metadata. All
  unreleased Phase 11 migration work was squashed into the single
  `V26__practice_authoring_contract.sql`; commit `324dad9` shipped no V27-V29.
  Any accepted post-closure correction must therefore use a forward migration
  rather than modifying V26.
- Program-version/template columns on live sets and immutable set versions stay
  nullable for legacy or historically ambiguous rows. New first-publish and
  republish paths require and persist both bindings. This is an intentional
  compatibility boundary: old attempts/sets are not silently rewritten merely
  to satisfy the new authoring contract.
- First publish now binds program version and exam template on the live set as
  well as the immutable version; republish no longer has to repair metadata
  omitted by the first publish.

Post-closure corrective slice 11I (2026-07-11):

- status: `IMPLEMENTED_AUTOMATED_GATE_GREEN_BROWSER_UAT_DEFERRED`; this is a
  bounded Phase 11 corrective stabilization and does not reopen the full Phase
  or start Phase 12;
- V27 adds program/template and target `Test/Skill/Lesson` context to PDF import
  sessions. `category` remains a legacy compatibility value derived from the
  resolved template; it is not the primary authoring selector;
- linked PDF import targets one selected section, appends imported groups without
  replacing sibling tests/sections, keeps `sourceQuestionNo`, and lets the draft
  contract assign local display numbering in the destination lesson;
- PDF wizard/workspace uses teacher-facing terms, renders question/image fields
  immediately after content-type changes, shows learner-display semantics and
  reuses the sanitized editor preview for `Xem như học sinh`;
- normal Lecturer payload preview shows destination/content/image scope; model,
  system prompt and raw request remain Head/Admin-only;
- Excel preview defaults to a compact Test/Section/Group/Question view, shows
  shared passage/transcript/media once, keeps row issues with jump links, and
  retains the complete A-H/MATCHING/media table as an explicit detail view;
- invalid Excel rows stay visible and are automatically excluded on confirm;
  imported numbering is compacted deterministically in each lesson;
- automated evidence is green: compile and loaded-template JavaScript checks,
  focused contract/UI tests, the broader Practice authoring/PDF/Excel regression
  bundle, clean isolated Flyway V1-V27 plus Hibernate validation, and the final
  pool-bounded full suite all passed. The final suite ran 1244 tests with 0
  failures, 0 errors and 0 skips on JDK 26.0.1/MySQL V27;
- the user explicitly removed browser QA from this corrective slice. No browser
  result is implied. The browser QA is consolidated into the mandatory
  end-of-Phase-12 stabilization below; full Manual UAT remains Phase 15. No
  provider call was made;
- at the 11I checkpoint, those changes remained uncommitted and Phase 12 stayed
  `NOT_STARTED` pending a separate GO. That implementation GO was granted later;
  11I and Phase 12 are now reviewed together as one uncommitted worktree.

Phase 11 operating rules now locked:

- backend policy and validator remain the source of truth; frontend checks are
  only authoring assistance;
- Admin/Head policy controls enabled skills, Excel availability, question
  types, option count, max questions, points and profile routing. UI and
  downloaded workbook must consume the same resolved policy;
- objective answer keys are lecturer/import data, never inferred by AI during
  scoring;
- low-confidence PDF content cannot publish until teacher confirmation;
- raw AI request/system prompt is not part of the normal Lecturer surface;
- no provider call is allowed in the automated Phase 11 gate;
- learner rendering for every new objective type remains Phase 13;
- collaboration, immutable history UI and `LOCKED_BY_OWNER` remain Phase 12;
- no React rewrite and no broad visual redesign are part of Phase 11.

11H final evidence:

- JS syntax checks passed for both loaded Phase 11 modules;
- inline scripts in `editor.html` and `excel-import.html` parse successfully,
  and `git diff --check` is clean;
- focused authoring/import/PDF/score gate: 31 tests, 0 failures/errors/skips;
- final focused Excel/media/MATCHING and related Phase 11 gate: 47 tests, 0
  failures/errors/skips;
- clean isolated MySQL migration V1-V26 and Hibernate schema validation passed;
- V26/Phase 10 compatibility integration gate: 5 tests, 0 failures/errors;
- closure rerun of the focused menu/Excel/controller stabilization tests:
  20 tests, 0 failures, 0 errors, 0 skips;
- final pool-bounded full suite on JDK 17 and MySQL V26:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17 sh mvnw test
  -Dspring.datasource.hikari.maximum-pool-size=2
  -Dspring.datasource.hikari.minimum-idle=0`; 1242 tests, 0 failures, 0
  errors, 0 skips, BUILD SUCCESS;
- browser smoke on a controlled port 8082 passed with `admin@ksh.edu.vn`:
  editor loaded the Set/Test/Skill/Group tree, PDF navigation opened the linked
  draft import page, Excel navigation opened with exact draft/test/lesson
  parameters, and R1/L1/W1/S1 each exposed the policy-enabled Excel action.
  The closure rerun navigated L1 to
  `draftId=10&testNo=1&lessonCode=L1` and returned safely to the editor;
- the three-dot menu opens/closes with correct ARIA state, rename focuses and
  selects the draft title, and delete requires native confirmation. The QA
  dialog was dismissed and draft 10 remained intact;
- teacher preview rendered real prompts and options and did not expose answer
  keys/explanations; Excel preview uses a centered native dialog (`fixed`,
  full inset, auto margin); runtime visual inspection found no incoherent
  overlap in the preview and no browser console warning/error was observed;
- temporary QA servers on ports 8080, 8081 and 8082 were stopped after smoke;
- no real AI provider call was made.

`PHASE_11_CLOSURE_STABILIZATION_GATE = CLOSED_GREEN`.

Accepted debt and routing:

- large inline manual-editor script: incremental extraction in Phase 13A;
- PDF workspace remains a large inline/alert-heavy interaction surface after
  the bounded 11I wording/reactivity correction: module extraction and deeper
  guided UX remain Phase 13, operational hardening remains Phase 15;
- Excel keeps the wide A-H/media table as a secondary evidence view after 11I;
  large-catalog responsiveness/accessibility and richer media mapping remain
  Phase 13 without weakening row-level evidence;
- unmatched/duplicate companion media filenames remain visible as pending
  attachments; a future asset-bundle/folder manifest may improve bulk media UX
  without ever trusting arbitrary local paths from spreadsheet cells;
- companion media orphan cleanup/retention remains Phase 12D/15; deeper
  magic-byte/content inspection and production upload rehearsal remain Phase 15;
- external CDN/CSP/supply-chain hardening remains Phase 15;
- typed learner controls/results for all new objective types: Phase 13B/C;
- broader attempt delivery-policy/program-version lock: Phase 13C before
  multi-program learner rollout;
- granular action RBAC, shared editing and owner lock: Phase 12A/B;
- Admin/Head template/prompt/rubric/scoring governance: Phase 12C;
- provider/browser/device/load/migration rehearsal: Phase 15.

Top-level Phase 11 was accepted and closed with routed debt at commit
`324dad9`. At that closure point Phase 12 remained `NOT_STARTED`; its later
implementation required and received a separate explicit user GO.

### Phase 12 — Materials & Permissions

Status:
STABILIZED_AUTOMATED_GATE_GREEN_BROWSER_QA_DEFERRED

`PRE_PHASE_12_MATERIALS_PERMISSIONS_AND_GOVERNANCE_GATE` was completed as an
audit-only slice on 2026-07-11 against committed HEAD `324dad9`.

Verdict:
`AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES`.

This verdict confirmed that Phase 12 was feasible, but did not itself start
implementation. No production code, migration or provider call was made in
that audit. The user subsequently gave an explicit implementation GO and the
Phase 12 implementation was stabilized on 2026-07-12. The original
gap map remains below as historical acceptance criteria. Neither the Phase 11
commit nor this Phase 12 feature-branch checkpoint authorizes merge into
`main` or product/live rollout; both remain NO-GO until their own explicit gate.

A parallel Codex audit supplied supplementary findings rather than replacing
this audit. Its snapshot fail-open and DB/filesystem asset-consistency claims
were rechecked against local source before being added below.

Audit positives that must be preserved:

- Student routes are already separated from lecturer management routes;
- draft, PDF-session, region and asset operations have owner checks and
  cross-owner denial coverage;
- immutable published versions and attempt-version locks exist from Phase 9;
- V26 carries program-version/template/profile identity through authoring and
  publish paths;
- raw PDF request/system detail is redacted server-side for Lecturer and is
  available only to Head/Admin;
- CSRF remains enabled and temporary PDF sessions/assets have bounded expiry
  cleanup.

Required gap map:

| Priority | Finding | Required routing |
|---|---|---|
| P0 | V4 permission tables, hierarchy and user overrides exist, but `/practice` authorization still uses broad role checks plus owner checks; effective action permissions are not wired into Java services | 12A must implement deny-by-default action authorization at service boundaries |
| P0 | No authoring collaboration relation, shared scope, owner lock or audited emergency override exists | 12A/12B must define explicit shared-authoring policy and `LOCKED_BY_OWNER`; learner `GLOBAL/CLASS` scope must not be reused |
| P0 | Restore replaces the live graph and republish is blocked once any attempt/submission exists | 12B must restore a selected version into a new draft/revision/version and add a safe append-only republish path while preserving legacy fail-closed behavior |
| P0 | Publisher snapshot capture catches any exception and returns `{}`, so publish/edit logging can appear successful with unusable history; restore rejects `{}` and its rollback log has no complete before snapshot | 12B must fail closed when required history cannot be recorded and write complete before/after audit evidence for restore |
| P0 | Private asset intent conflicts with delivery: `/uploads/**` is public, while owner-only `/practice/manage/assets/{id}/content` URLs can be persisted into published content that learners cannot read; published/version references are not part of deletion retention | 12D must introduce one governed material identity and separate private authoring access from published learner delivery with version-safe references |
| P1 | Program/template/profile data is runtime read-only; activation, rollback, audit and immutable configuration administration do not exist. Prompt `system_rules` is not mapped into runtime and exam template config can drift under a stable code | 12C must add version-safe Admin/Head governance and one resolved policy consumed by manual, Excel, PDF and learner delivery |
| P1 | `ARCHIVED` exists in schema but no archive/unarchive action exists; upload MIME response/content inspection and orphan/retention handling are incomplete | 12B/12D |
| P1/release | Lecturer asset promote/delete performs filesystem mutation and database mutation without a durable compensation/outbox boundary; rollback or commit failure can leave DB and storage inconsistent | 12D must use staged mutation plus after-commit/failure compensation and durable bounded retry before product rollout |
| P2 | Revision diff/history UX, large shared lists and material-management ergonomics are not implemented | Phase 12 bounded UX, then Phase 13 visual/accessibility work |
| P2/release | Cloudflare R2 is the selected target in principle, but there is no API/bucket/domain/credential or multi-node cleanup contract; virus scanning is also undecided | 12D provider-neutral boundary plus 12E decision record; real R2 integration/rehearsal in Phase 15E; live Speaking audio remains NO-GO |

Required authorization matrix:

| Actor/context | Allowed authoring behavior |
|---|---|
| Student | No create/edit/publish/archive/lock/unlock/restore/config action; only consume authorized published content |
| Lecturer owner | Create, read, edit, publish, archive, lock, unlock and restore own content subject to explicit action grants and state invariants |
| Lecturer collaborator | View and, only when explicitly granted and owner lock is off, edit/publish/restore shared content; cannot change ownership or lock/unlock; archive is denied by default unless a separate grant is approved |
| Unrelated Lecturer or any collaborator on locked content | Deny mutation server-side |
| Head/Admin | Normal granted actions plus a separate emergency override; every override requires a reason and immutable audit event |

The owner lock must be checked before draft creation from a published set,
autosave, import attachment, publish/republish, restore and material mutation.
Hiding or disabling a button is not authorization. Role may supply default
grants, but effective permission, ownership/collaboration scope, content state
and lock state must all pass at the service boundary.

Compatibility and migration guards:

- V26 is committed and pushed; Phase 12 uses forward-only migration numbering
  and must not rewrite V26;
- old attempts, submissions and immutable versions are never deleted, silently
  re-bound or rewritten;
- keep the current mutation guard for legacy/unversioned attempts that cannot
  prove a safe immutable source; add append-only behavior only for safely
  version-locked data;
- owner identity stays stable while actor/collaborator actions are recorded
  separately;
- `PracticeSet` learner-delivery scope `GLOBAL/CLASS` is not an authoring
  collaboration model;
- exact table/column/API names require an implementation contract review of
  the live schema. Do not invent equivalent fields or rename existing fields
  merely for cleaner terminology;
- no edit/publish notification is required by Phase 12B.

Required execution order after explicit approval:

Phase 12 must add bounded application services/components instead of continuing
to place authorization, collaboration, revision and lifecycle logic directly
inside the already large editor script, PDF workspace, Excel codec,
`PracticePublisherService` or `PracticeService`. Suggested responsibilities are
an authorization decision boundary, material-access boundary, revision
application boundary, assessment-governance boundary and asset-lifecycle
boundary; exact class names are decided only after implementation source review.

- 12A: wire effective permissions, canonical practice actions, ownership,
  collaboration scope and deny-by-default service checks; test the complete
  Lecturer/Head/Admin owner/collaborator/cross-owner matrix;
- 12B: add direct shared editing plus owner lock, archive behavior and
  append-only restore/republish from any selected immutable version without
  changing historical attempts; snapshot/history creation must fail closed and
  every restore carries complete before/after evidence;
- 12C: add immutable, auditable Admin/Head lifecycle for program/template,
  prompt, rubric and scoring profiles; validate fixtures before activation and
  rollback by version/activation rather than mutating old config;
- 12D: unify manual/PDF/Excel material identity, private authoring delivery,
  published learner delivery, durable version references, MIME/content checks,
  retention/ref-count, transaction-aware compensation and durable bounded
  orphan cleanup;
- 12E: lock reviewer playback, consent, retention and multi-node cleanup policy.
  Cloudflare R2 is the target, but no live adapter/network call, secret or fake
  credential may be added before API/bucket details are explicitly supplied.
  Non-audio Phase 12 work may continue; production Speaking audio stays blocked;
- 12F: run fresh and representative legacy migration rehearsals, permission /
  IDOR and lock/override tests, config activation/rollback tests, version-safe
  restore/republish and snapshot-failure tests, material privacy/retention plus
  DB/filesystem fault-injection tests, full suite and the consolidated browser
  QA. No real provider call is allowed in the automated gate.

End-of-Phase-12 stabilization is mandatory before Phase 13:

- run one complete controlled browser QA journey across `/practice`:
  program/certificate selection, library, Set > Test > Skill authoring, manual /
  Excel / PDF import, preview, publish, shared/locked/history/config/material
  flows, learner attempt/result paths and cross-role denial. Use sufficient
  deterministic test fixtures for route/state coverage. This replaces the
  browser pass deliberately skipped in 11I and does not replace Phase 15 Manual
  UAT or its high-quality multi-certificate dataset;
- the Phase 12 exit stabilization must inventory controllers/routes/templates/
  loaded scripts and remove or explicitly route dead code. It must fail on a
  broken click target, wrong redirect/parameter binding, 4xx/5xx on a valid
  journey, missing content, console/network error, incoherent overlap or
  mojibake. Fixes remain bounded and regression-tested;
- Phase 13 cannot start until this stabilization is `CLOSED_GREEN`, the full
  suite is green and temporary servers are stopped. Manual UAT remains a Phase
  15 release gate and is not moved earlier by this stabilization requirement.

Phase 12B direction refined by the Phase 11 audit:

- provide separate `Của tôi` and `Được chia sẻ` material views;
- use a simple content state model: `DRAFT`, `PUBLISHED`, `ARCHIVED`; do not
  require `IN_REVIEW` or `APPROVED` for the normal lecturer workflow;
- published shared material can be edited and republished directly by a
  lecturer allowed by the institutional collaboration policy;
- the owner can enable `LOCKED_BY_OWNER`. While locked, other lecturers cannot
  edit, restore or publish a new version; Admin/Head emergency override is
  explicit and audited;
- do not add edit/publish notifications as a Phase 12B requirement;
- list the complete immutable revision history with actor, time, summary and
  diff metadata. Restoring any selected historical version creates a new
  revision and never deletes or rewrites history;
- keep owner identity and all collaborator actions auditable. Lock/unlock and
  Admin/Head override are separate audit events;
- authorization remains service-side and action-based; the owner lock is not
  implemented only as a hidden/disabled UI control.

Phase 12 research preparation locked on 2026-07-11:

- the supplied PREP research is learner-account evidence only. It does not
  establish teacher/Admin behavior and must not be used as an RBAC acceptance
  criterion;
- program/certification identity must be first-class in permission scope,
  template/profile governance, material views and later reviewer queues. Reuse
  the Phase 10/11 program-version and exam-template model rather than adding
  route-specific certificate configuration;
- 12A must produce an action matrix for create, edit, publish, archive, lock,
  unlock, restore and emergency override across Lecturer/Head/Admin, including
  owner and cross-owner denial tests;
- 12B keeps the simple `DRAFT`, `PUBLISHED`, `ARCHIVED` lifecycle. Report-ticket
  states in Phase 14 are separate and must not reintroduce a mandatory content
  approval workflow;
- 12C owns immutable Admin/Head configuration for enabled skills, question
  types, limits, timers, scoring and approved prompt/rubric/profile versions;
- teacher/admin governance still requires KSH-native schema/RBAC audit and UAT.
  No external teacher/admin journey was observed in the research input.

Phase 12 implementation and automated stabilization outcome on 2026-07-12:

- 12A added canonical action-based authorization, effective role/permission
  resolution, explicit authoring collaboration, owner lock, scoped Head/Admin
  override and immutable governance audit events. Student, unrelated lecturer,
  collaborator and locked-content denial remain service-side invariants;
- 12B added the bounded `DRAFT/PUBLISHED/ARCHIVED` lifecycle and append-only
  restore from any selected immutable version. Restore creates a fresh draft
  and published version, preserves prior history/attempt bindings and records
  complete before/after audit envelopes instead of replacing old history;
- 12C added immutable Admin/Head governance for program, exam-template,
  scoring, prompt and rubric versions with validation, row-locked activation
  and rollback-by-activation semantics. Manual, Excel and PDF authoring continue
  to resolve the same active program/template policy;
- 12D replaced raw practice upload delivery with authenticated
  `/practice/materials/{assetId}/content`, explicitly denies the private raw
  upload namespaces, verifies supported content signatures, preserves draft /
  published-version references and uses a durable idempotent lifecycle task
  boundary for promotion, deletion, retry and orphan cleanup;
- 12E keeps storage provider-neutral. Local storage remains the development
  adapter; Cloudflare R2 is the selected production target but its adapter is
  `NOT_STARTED_API_UNAVAILABLE`. No SDK, network call, secret, bucket/domain or
  fake credential was introduced;
- forward migration `V27__practice_phase12_governance.sql` carries the
  unreleased 11I target-context delta and Phase 12 governance/material schema
  without rewriting committed V26. Fresh MySQL V1-V27 migration plus Hibernate
  validation is green and old attempt/version rows remain preserved;
- focused evidence is green: restore/governance 17/17, storage/material 22/22,
  governance hardening 13/13 and `PracticeIntegrationTest` 78/78. The final
  pool-bounded full suite is 1293/1293, zero failures/errors/skips,
  `BUILD SUCCESS` on JDK 26.0.1/MySQL V27; no provider call was made;
- static closure checks confirmed the private upload deny ordering, governed
  material/restore/upload routes, scheduling worker registration, V1-V27-only
  migration inventory, effective permission seed/query and no newly introduced
  production TODO/FIXME/HACK. UTF-8/mojibake cleanup and regression guards were
  retained.

Gate state:

- `PHASE_12_AUTOMATED_STABILIZATION_GATE = CLOSED_GREEN`;
- `PHASE_12_CLOSURE_STABILIZATION_GATE = OPEN_BROWSER_QA_DEFERRED`;
- browser QA was explicitly skipped by the user for this run. Therefore this
  document does not claim browser/runtime closure, top-level
  `CLOSED_WITH_ACCEPTED_DEBT`, Phase 13 GO, merge GO or product rollout GO;
- Phase 15 still owns full Manual UAT, clean high-quality multi-certificate
  seed data, cross-browser/device/load testing and release approval. It was not
  moved into Phase 12.

Explicit remaining debt:

- managed prompt/rubric/scoring identity and activation are implemented, but
  replacing every legacy hard-coded Writing/Speaking prompt-rule adapter with
  database-managed `system_rules` is not claimed complete. Existing runtime
  adapters remain compatibility behavior until a focused evaluator migration,
  calibration and provider-safe UAT is approved;
- the real Cloudflare R2 adapter, bucket/domain/credential policy, local-to-R2
  migration rehearsal, virus-scanning decision and multi-node reconciliation
  remain Phase 15E blockers. Live Speaking audio/AI remains NO-GO;
- history diff ergonomics, large shared-material lists and the large inline
  editor/PDF-workspace UI remain bounded Phase 13/15 debt;
- the consolidated browser route/UI pass remains required before declaring the
  Phase 12 closure gate green and opening Phase 13.

### Phase 13 — Results, Progress & UI/UX Polish

Status:
NOT_STARTED

Phase 13 is learner-delivery feature work plus UX stabilization, not visual
polish alone. It must preserve the existing KSH route chain
`set -> test -> mode -> attempt -> result -> result/detail` and must not copy
external branding, assets, content, CSS, API or URLs.

Research basis and evidence boundary:

- the two supplied research documents cover 96 learner screenshots in 14
  evidence groups plus learner-visible IELTS/TOEIC-like routes;
- observations from that account may inform capability and state design only;
  teacher/admin behavior remains unobserved;
- speaking controls/transcript/IPA were visually inspected, but audio accuracy
  remains `PENDING_AUDIO_UAT` because system audio was not captured.

Research is additive. It must not replace or silently close older Phase 13/14
directions that have not passed their own audit and UAT. The following baseline
items remain explicitly open:

- Mojibake / Unicode / UTF-8: fix Vietnamese/Korean UI corruption such as
  `Cáº¥p`, `Äá»c` and similar encoding artifacts. Templates, database
  seed/import text, resource bundles and rendered pages must consistently use
  UTF-8. Add UI/UAT regression checks so mojibake does not reappear. Phase 9G
  completed only a narrow cleanup, not this practice-wide sweep;
- Icons: avoid emoji-style product UI icons such as `🚀`. Prefer Lucide icons
  or a consistent SVG icon component system. This has not yet been audited or
  completed across `/practice`;
- retain the original learning-profile, practice-library, set-detail,
  skill-list, result/progress/retake/detail and mobile/responsive roadmap;
- retain bounded catalog loading and the full-test/skill-specific composition
  rules. Do not assume one set equals one test or one test equals one skill;
- retain the original validation routing: Phase 9 immutable structure, Phase 10
  certification policy and Phase 11 import validation are prerequisites already
  implemented, while Phase 13 still owns the learner UX and Phase 15 still owns
  manual large-catalog/encoding/icon/multi-test/multi-skill UAT.

Baseline-to-slice mapping is preserved: typed player -> 13C; result/explanation
-> 13D-13E; full-test/skill flow -> 13B-13C; catalog scale -> 13A/13G;
progress analytics and retry/operational UX -> 13F; visual/encoding/icons/a11y
-> 13G; the original functional gate -> 13H.

Required Phase 13 slices:

- 13A: design/state foundation and bounded server-backed library preserving the
  `Set > Test > Skill` hierarchy, program/certificate filter state in the URL,
  pagination/cursor loading and explicit empty/error/loading states;
- 13B: explicit full-test versus skill-mode selection and preflight. Keep
  latest submitted, best score, in-progress attempt and attempt count as
  separate concepts;
- 13C: shared player shell with skill-native interaction, timer, navigation,
  autosave, exit warning and resumable attempt states. Typed R/L/W/S controls
  follow the Phase 10/11 contracts and never expose answer specs before submit;
- 13D: result overview with exam-native score scales and recent-attempt
  semantics. Preserve `score`, `scale`, `level`, `completion`, `timing` and
  `feedbackAvailability` as distinct fields;
- 13E: evidence-based result detail for learner answer, official answer,
  explanation, passage/transcript anchors, rubric, correction, upgraded answer
  and sample content. AI is advisory and cannot replace the official answer key;
- 13F: progress/profile aggregation by program, skill and question type, with
  filters, sample-size/recency/confidence context and deep links to practice.
  Preserve the earlier operational UX scope: policy-gated Speaking retry,
  explanation unavailable/retry states, idempotency and rate limits, and no
  provider call caused only by refreshing a page;
- 13G: responsive, accessibility, encoding and performance pass for large
  catalogs. Complete the explicit UTF-8/mojibake regression sweep, replace
  emoji product icons with Lucide/consistent SVG components, and prevent
  overlapping controls/text;
- 13H: visual QA and manual learner journeys across IELTS/TOEIC-like modes,
  canonical question types, desktop/mobile and failure/empty states. The gate
  must include large-catalog performance plus encoding/icon and multi-test /
  multi-skill UAT; research evidence alone cannot satisfy this gate.

Result states must not collapse distinct conditions. At minimum render
`not_answered`, `not_qualified`, `capture_failed` and `graded` separately, and
keep `NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED`, `SCORING`, `SCORED`, `PARTIAL`,
`FAILED` and `STALE` semantics consistent across library, player and result.

### Phase 14 — Report an Error & Content Review Workflow

Status:
NOT_STARTED

Phase 14 is a learner-visible review loop, not only a backend ticket table:

- 14A adds report entry points from player, question review and result detail.
  The server auto-attaches program, set/test/section/group/question identity,
  immutable content version, attempt, active view/tab, reporter/timestamps and
  correlation context. Targets retain prompt, option, answer, explanation,
  translation, stimulus/passage/transcript, audio, scoring and UI;
- 14B provides structured reason/category, optional note and a safe evidence
  snapshot. Screenshot/audio attachment is category-dependent, consented and
  governed by retention/privacy rules;
- 14C audits and locks deduplication and learner-safe status history. The older
  roadmap vocabulary `OPEN`, `TRIAGED`, `IN_REVIEW`, `CHANGES_REQUESTED`,
  `RESOLVED`, `REJECTED`, `DUPLICATE` must not be discarded; research adds
  `NEW` and `NEEDS_INFO` as candidates. Phase 14 audit must map compatibility
  and semantics before choosing the canonical set. These are report states,
  not content lifecycle states;
- 14D provides the reviewer queue with certificate/program scope, immutable
  content/version/question context, severity, ownership and full audit trail.
  A correction creates a draft/new published version; explanation correction
  creates a new artifact and marks the old one superseded. History is never
  mutated and AI never publishes a correction automatically;
- 14E returns resolution feedback to the learner and owner, preserves the
  original notification/SLA-age dashboard/duplicate-grouping/metrics scope,
  and may temporarily block new attempts for a reviewed high-severity wrong
  answer under policy without deleting history. Confirmed defects create
  regression fixtures;
- 14F is the explicit gate: report-to-review-to-correction-to-new-version E2E,
  permission/privacy/deduplication/attachment/malformed-content UAT, immutable
  history and complete audit log.

The learner UI must never expose internal moderation notes and must never imply
that a report changed a score until review is complete and the applicable
corrected version or score decision has actually been published.

### Phase 15 — Manual UAT & Release Hardening

Status:
NOT_STARTED

Manual UAT data contract (locked 2026-07-11):

- the current development rows are experimental fixtures and are not accepted
  as meaningful Manual UAT or product evidence;
- when Phase 15 Manual UAT starts, the user authorizes dropping/recreating only
  a dedicated local/UAT database. Never apply that permission to production or
  an unidentified schema. Run fresh migration plus representative legacy
  upgrade rehearsal before loading UAT fixtures;
- use a deterministic, versioned UAT fixture pack or UAT-only seed loader,
  separate from production Flyway seed data unless separately approved.
  Content must be original/licensed, realistic Korean and teacher-reviewed,
  rather than placeholder text;
- coverage is program/certificate driven, not TOPIK-only. Seed every
  Admin/Head-enabled certificate and approved template, including planned TOPIK
  I, TOPIK II, KSH custom, KIIP, KLAT, KLPT, OPIc and TOPIK Speaking profiles
  plus certificates added later. Generate set/test/skill/type/media data from
  each certificate's resolved immutable config; never copy TOPIK constraints
  into another certificate;
- every enabled certificate needs at least one representative set, multiple
  tests where policy permits, every enabled skill and question type, shared
  group material, question material, profiles/rubrics/scoring and valid/warning/
  invalid import cases;
- the TOPIK II full-form fixture specifically includes 50 Reading questions,
  50 Listening questions and Writing questions 51, 52, 53 and 54, with local
  numbering, answer keys, teacher explanations, passages/transcripts and media
  placement. Other certificates use their own approved counts, skills, timers,
  scoring and profile contracts;
- Phase 15 then runs the full Manual UAT/release matrix across roles, supported
  browsers/devices, authoring/import, learner attempts/results, audio/media,
  Unicode/IME, scale and operational failure states. This is distinct from the
  bounded end-of-Phase-12 browser stabilization required before Phase 13.

### Phase 16 — Optional Chatbot AI

Status:
OPTIONAL / NOT_STARTED

## Phase 8G Scope Clarification

Phase 8G is not a single `set-detail.html` bug-fix phase.

The known `set-detail.html` / wrong `testId` issue is only a representative sentinel defect.

Phase 8G must proactively audit all currently implemented `/practice` functional UI and backend-model-template integration.

It must search for both:

- known defects;
- unknown defects.

Phase 8G must audit:

- all `/practice` routes;
- controller/model/template contracts;
- Thymeleaf templates and fragments;
- existing Practice JavaScript;
- functional CSS where it blocks action;
- start/resume/submit/result/history flows;
- role/action consistency;
- empty states;
- wrong entity ID usage;
- Reading/Listening/Writing/Speaking flows;
- result/detail/history navigation;
- old result rendering;
- status/action matrix.

Known defect class:

A page may return 200 and display some data,
but the primary learner action may be missing or wrong.

A test that only asserts HTTP 200 is insufficient.

Phase 8G must build a route/page matrix:

- route;
- controller method;
- model attributes;
- template;
- primary user action;
- required entity IDs;
- allowed statuses;
- expected empty/error state;
- current test evidence.

Functional breakage belongs to 8G.
Visual redesign belongs to Phase 13.

## Speaking AI Evaluation Policy

Speaking evaluation is not only pronunciation.

The system should distinguish:

### Stronger-confidence scoring

Can be evaluated reasonably from transcript/prompt:

- content/task fulfillment;
- vocabulary and expressions;
- grammar and sentence control;
- register/style consistency;
- coherence and organization.

### Advisory/reference scoring

Can be evaluated but must be presented carefully:

- fluency;
- hesitation;
- fillers;
- pacing;
- listener burden;
- suspected pronunciation issues;
- suspected batchim/linking/vowel issues;
- intonation/rhythm naturalness.

### Must not overclaim

Do not claim:

- exact phoneme-level diagnosis;
- native-like pronunciation judgment;
- official TOPIK Speaking equivalence;
- medical/speech-therapy accuracy;
- absolute pronunciation correctness.

Required future schema concepts for 8E:

- transcript;
- interpreted_intent;
- evidence;
- confidence;
- listener_burden;
- rubric scores;
- findings;
- recommendations.

All Speaking/Writing scores are KSH internal scores.

No official TOPIK score claim.

When implementing provider/audio features, verify current official provider documentation at that time.

## How To Update This MD After Work

Codex must update this MD whenever a task changes phase/slice status,
unless the active task is AUDIT ONLY or the user explicitly forbids file edits.

Rules:

1. After AUDIT ONLY

Do not edit this MD during audit unless explicitly allowed.

Audit report must say whether a status update is needed.

2. After IMPLEMENT CODE + FOCUSED TEST

If docs update is allowed in the task, update status to:

- IMPLEMENTED_PENDING_REVIEW
- IMPLEMENTED_AND_FOCUSED_TESTED

Do not mark top-level phase closed.

3. After COMMIT AND PUSH of a sub-slice

Update the sub-slice status to:

COMMITTED

and record:

- commit hash;
- parent hash;
- commit message;
- pushed branch;
- test evidence summary.

4. After top-level phase-gate stabilization

If successful and accepted, mark top-level phase as:

- CLOSED_VERIFIED
  or
- CLOSED_WITH_ACCEPTED_DEBT

depending on evidence.

5. If user/assistant review rejects the work

Update to:

- BLOCKED
  or
- REOPEN_REQUIRED

with reason.

6. Every status update must include:

- date;
- phase/slice;
- previous status;
- new status;
- commit hash if applicable;
- parent hash if applicable;
- test evidence;
- decision;
- remaining debt;
- next action.

7. Do not leave this MD dirty across task boundaries.

If `CODEX_PRACTICE_WORKFLOW.md` is updated as part of a code/test slice, commit
the MD in the same slice commit unless the user explicitly requires a separate
docs commit.

If `CODEX_PRACTICE_WORKFLOW.md` is updated as a docs-only policy/status change
after the related slice is already committed, commit the docs-only change
immediately after review.

Do not start a later audit while this MD has an uncommitted tracked diff.
Foundation checks are expected to block in that state.

8. If a requested task would require editing this MD but the prompt says no file edits,
Codex must stop and ask:

MD_STATUS_UPDATE_REQUIRES_PERMISSION

## Phase Change Log

| Date | Phase/Slice | Previous Status | New Status | Commit | Parent | Evidence | Decision | Next Action |
|---|---|---|---|---|---|---|---|---|
| 2026-07-09 | 8C-R Writing Closure Re-validation | PROVISIONALLY_CLOSED_NEEDS_REVALIDATION | CLOSED_WITH_ACCEPTED_DEBT | N/A | N/A | 8C_CLOSED_WITH_ACCEPTED_DEBT audit verdict. | Closed for internal practice-score workflow dependencies with accepted debt. | Continue Phase 8D path. |
| 2026-07-09 | 8D-A Speaking Audio & Media Baseline Audit | NOT_STARTED | AUDIT_GO | N/A | N/A | 8D_FOUNDATION_VERIFIED_WITH_ACCEPTED_DEBT. | Backend media foundation exists, but not production/browser ready. | Confirm next 8D implementation slice. |
| 2026-07-09 | 8D-B Speaking Audio Contract Stabilization | IMPLEMENTED_AND_FOCUSED_TESTED / READY_FOR_COMMIT_REVIEW | COMMITTED | 3156139081ef1480f605df91c8b99216069d43a3 | ca2f6f1298b7e556757b1e8fc1c840bb7fce44f4 | Focused tests 310 pass, 0 failures, 0 errors, 2 skips. Full suite was run historically for this slice but future small slices should not require full suite by default. | Committed and pushed to origin/feature/practice. | Confirm next 8D slice with user. |
| 2026-07-09 | 8D-D Range Playback and Browser Compatibility | AUDIT_GO / IMPLEMENTATION_IN_PROGRESS | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 03f781a74f46cd312c6ffe804dc9278b1f1414e3 | Focused tests 81 pass, 0 failures, 0 errors, 2 skips. | Implemented private local playback Range support; not staged, committed, or pushed. | User review for 8D-D implementation summary, then commit/push only after explicit GO. |
| 2026-07-09 | 8D-C Browser Recording and Functional Audio UI | IMPLEMENTATION_IN_PROGRESS | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | Consolidated focused suite: 244 tests, 0 failures, 0 errors, 2 skips on JDK 17. | Implemented optional MediaRecorder flow, session-only consent, upload/delete/re-record, player preview, and result/detail playback behind disabled gates. | User review, then top-level 8D phase-gate stabilization. |
| 2026-07-09 | 8D-E Cleanup Worker and Retention | IMPLEMENTATION_IN_PROGRESS | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | Consolidated focused suite: 244 tests, 0 failures, 0 errors, 2 skips on JDK 17. | Added disabled-by-default bounded scheduler and enforced due_at plus next_attempt_at with 24-hour discard retention. | User review, then top-level 8D phase-gate stabilization. |
| 2026-07-09 | 8D-F Production Storage | IN_PROGRESS | CLOSED_WITH_ACCEPTED_DEBT | N/A | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | Source inspection and explicit user decision; no object-storage dependency or adapter added. | PRODUCTION_OBJECT_STORAGE_DEFERRED; local private storage remains a single-node deployment limitation. | Reopen production storage hardening after provider/target selection. |
| 2026-07-09 | Phase 8D Speaking Audio and Media | IN_PROGRESS | STABILIZATION_REQUIRED | N/A | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | 8D-C and 8D-E focused-tested; 8D-F accepted debt recorded. | Implementation complete but top-level phase is not closed. | User review, then Phase 8D phase-gate stabilization. |
| 2026-07-09 | Phase 8D Speaking Audio and Media | STABILIZATION_REQUIRED | STABILIZED_PENDING_COMMIT | N/A | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | Focused stabilization rerun: 244 tests, 0 failures, 0 errors, 2 skips on JDK 17. Fixed nested consent form markup, preserved local preview when playback gate is disabled, and stabilized async playback test dispatch. | Phase-gate source review and focused regression passed; live rollout remains NO-GO pending manual browser/device smoke and accepted debt confirmation. | User review, then commit/push only after explicit approval. |
| 2026-07-09 | Consolidated Phase 8D Speaking Audio and Media | STABILIZED_PENDING_COMMIT | COMMITTED | this commit | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | Phase-gate focused tests: 244 tests, 0 failures, 0 errors, 2 skips on JDK 17; no full suite. | 8D-C and 8D-E committed; 8D-F remains CLOSED_WITH_ACCEPTED_DEBT / PRODUCTION_OBJECT_STORAGE_DEFERRED; top-level 8D is not closed. | Manual browser/device smoke, accepted-debt confirmation, then Phase 8D closure review. |
| 2026-07-09 | Phase 8D Speaking Audio and Media Closure | COMMITTED | CLOSED_WITH_ACCEPTED_DEBT | c30ce7505cf1b70074f1d97864c1cfa107c1b0ac | 9c978ce332fc60de2118d2e6bbdcd4243d89485c | Gates confirmed disabled by default; all remaining browser/device, recorder lifecycle, playback, reload, and text-fallback smoke items explicitly accepted as NOT_TESTED_ACCEPTED_DEBT. | Closed with explicit acceptance of object-storage, local-storage, cleanup topology, session-consent, Phase 15 UAT, and Phase 8F production-hardening debt. Live rollout remains NO-GO. | Phase 8E audit for Speaking AI Evaluation. |
| 2026-07-09 | 8E-A Speaking AI Schema, Status & Normalizer Foundation | IMPLEMENTATION_IN_PROGRESS | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | a6c2504a684b2aa3d86b07d4ba9136b55c3c7c20 | Focused rerun: 83 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Foundation implemented with typed result/status/evidence/rubric contracts, 100-point scoring, low-confidence safeguards, legacy compatibility, and view mapping. | User review and commit, then provider docs verification / 8E-B audit. |
| 2026-07-09 | 8E-B Speaking Transcription Abstraction and OpenAI STT Adapter | NOT_STARTED | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 6bccffec14e5df13c155327f369eaed420bbd38a | Focused command: `mvn "-Dtest=OpenAiSpeakingTranscriptionClientTest,SpeakingTranscriptionMediaResolverTest,SpeakingEvaluationNormalizerTest,SpeakingScorePolicyTest" test`; 38 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Implemented disabled-by-default OpenAI STT transcription abstraction, safe DTOs, READY local-media resolver, logprob-derived confidence, and provider strategy note. Live rollout remains NO-GO. | 8E-B user review and commit, then 8E-C audit for Speaking evaluator fixed-schema provider integration and prompt rules. |
| 2026-07-09 | 8E-C Speaking Evaluator Fixed-Schema Provider Integration | NEEDS_COMPILE_FIX | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | d1960b4941cb039693000ed4d8b3670f818abcc8 | Focused command: `mvn "-Dtest=SpeakingEvaluation*Test,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingEvaluationPromptBuilderTest,SpeakingPromptRulesTest,SpeakingEvaluationOrchestratorTest,SpeakingEvaluationNormalizerTest,SpeakingScorePolicyTest" test`; final focused rerun 46 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Implemented KSH allowed_rubric rules, deterministic Speaking rule signals, S_* criterion IDs, rich feedback contract, provider-neutral evaluator client, OpenAI-compatible chat adapter, orchestrator handoff, failure mapping, and disabled-by-default evaluator config. No persistence/UI/cache/migration. | 8E-C user review and commit, then 8E-D audit for Speaking AI persistence and result/detail rendering. |
| 2026-07-09 | 8E-CW2 Writing Task-Native Scoring Matrix | IMPLEMENTED_AND_FOCUSED_TESTED | COMMITTED | this commit | 5908a93755003f2c1df37899a95dd11e1a4be076 | Sequential focused validation passed for CW2-A, CW2-B, and CW2-C; final evidence-based consolidated CW2-D rerun passed 182 tests, 0 failures, 0 errors, 0 skips on JDK 17. | Implemented Q51/Q52 10-point two-blank rubrics, Q53 30-point rubric, Q54 50-point rubric, explicit percentage compatibility, and maxScore-aware result rendering; committed and pushed to origin/feature/practice. | Run the Phase 8E-D audit for Speaking AI persistence and result/detail rendering. |
| 2026-07-09 | 8E-E Speaking Re-evaluation and Transcript Reuse Stabilization | NOT_STARTED | COMMITTED | aa988d65e989e4091ee27b973af58a9c137a4c38 | ad21d2aceda173781c7acec4a3f2d8dc61262e9f | Focused command: `mvn "-Dtest=SpeakingEvaluationReusePolicyTest,SpeakingEvaluationApplicationServiceTest,SpeakingEvaluationOrchestratorTest,SpeakingTranscriptionMediaResolverTest,SpeakingFeedbackCompatibilityReaderTest,SpeakingFeedbackViewMapperTest,SpeakingResultRenderingContractTest,PracticeServiceTest#speakingAiEnvelopeBuildsAndReadsRichPerQuestionFeedback+speakingFeedbackMapBuildsPerQuestionRowsWithoutLeakingAnswers+speakingLegacyOneObjectFeedbackIsMarkedAsGlobalCompatibility+speakingEmptyOrMalformedFeedbackProducesSafeRowsWithoutFeedback+mixedLegacySpeakingEssayPersistsVersionedEnvelopeAndAggregatesByQuestionRegardlessOfOrder+speakingAiSubmitEvaluatesOnceAndPersistsVersionedEnvelope+speakingReEvaluateDoesNotCallRealSpeakingAiService" test`; 50 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Implemented submit-only Speaking AI evaluation behind both gates, identity-based reuse/invalidation, stale media guard, text fallback constraints, prior-success preservation for same-identity transient failures, and disabled-by-default fallback config; committed and pushed to origin/feature/practice. | 8E-F focused Phase 8E phase-gate review. |
| 2026-07-09 | 8E-F Speaking AI Focused Phase-Gate Review | NOT_STARTED | COMMITTED | d6dbbb98bff4791c67ae6e501ea295387ca394cb | aa988d65e989e4091ee27b973af58a9c137a4c38 | Focused command: `mvn "-Dtest=SpeakingEvaluationNormalizerTest,SpeakingScorePolicyTest,SpeakingPromptRulesTest,SpeakingEvaluationRuleEngineTest,SpeakingEvaluationOrchestratorTest,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingTranscriptionMediaResolverTest,OpenAiSpeakingTranscriptionClientTest,SpeakingFeedbackCompatibilityReaderTest,SpeakingFeedbackViewMapperTest,SpeakingResultRenderingContractTest,SpeakingEvaluationReusePolicyTest,SpeakingEvaluationApplicationServiceTest,PracticeServiceTest#speakingAiEnvelopeBuildsAndReadsRichPerQuestionFeedback+speakingFeedbackMapBuildsPerQuestionRowsWithoutLeakingAnswers+speakingLegacyOneObjectFeedbackIsMarkedAsGlobalCompatibility+speakingEmptyOrMalformedFeedbackProducesSafeRowsWithoutFeedback+mixedLegacySpeakingEssayPersistsVersionedEnvelopeAndAggregatesByQuestionRegardlessOfOrder+speakingAiSubmitEvaluatesOnceAndPersistsVersionedEnvelope+speakingReEvaluateDoesNotCallRealSpeakingAiService,WritingScoringPolicyTest,WritingTaskNativeScoringTest,WritingPromptRulesTest,WritingEvaluationClientTest,WritingEvaluationNormalizerTest,WritingFeedbackCompatibilityReaderTest,PracticeResultWordingTest" test`; 196 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Phase 8E gate review passed and top-level 8E closed as CLOSED_WITH_ACCEPTED_DEBT; live Speaking AI rollout remains NO-GO. | Route accepted debt through Phase 16, then Phase 8F audit only after user approval. |
| 2026-07-10 | Phase 8F AI Production Hardening, Calibration & Rollout Readiness | NOT_STARTED | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | d174971251339ead75b0e8a1e55d2456b7b77389 | Focused command: `mvn "-Dtest=SpeakingProviderRolloutReadinessTest,AiCalibrationReadinessPolicyTest,ProviderOperationalReadinessPolicyTest,SpeakingStorageProductionReadinessPolicyTest,AiRolloutReadinessChecklistTest,PracticeAiMetricsTest" test`; 31 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Implemented provider gate readiness, calibration fixture framework, bounded provider metrics/runbook readiness, storage production-readiness policy, and rollout checklist. Live Speaking AI remains NO-GO until object storage decision, 8G, 8H, and Phase 15/UAT decisions are satisfied. | User review, then commit/push only after explicit approval. |
| 2026-07-10 | Phase 8F AI Production Readiness Gate | IMPLEMENTED_AND_FOCUSED_TESTED | CLOSED_WITH_ACCEPTED_DEBT | N/A | 9ecd9924a4ca4c83044ec7f81fc7c09a1cd5ea04 | Focused command: `mvn "-Dtest=SpeakingProviderRolloutReadinessTest,AiCalibrationReadinessPolicyTest,ProviderOperationalReadinessPolicyTest,SpeakingStorageProductionReadinessPolicyTest,AiRolloutReadinessChecklistTest,PracticeAiMetricsTest" test`; 31 tests, 0 failures, 0 errors, 0 skips on JDK 17; no full suite and no provider calls. | Phase 8F gate review passed. Provider gates remain disabled by default; bounded metrics/runbook, calibration fixture framework, storage production-readiness policy, and rollout checklist are accepted with debt. Live Speaking AI remains NO-GO. | Commit/push 8F closure docs, then Phase 8G audit only after user approval. |
| 2026-07-10 | Phase 8G Practice-Wide Functional UI / Integration Regression | PLANNED | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 7dc79f1ccafa0c19f3d9474ef86c4ebc96cf4f42 | Focused command: `mvn "-Dtest=PracticeFunctionalUiContractTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId" test`; 10 tests, 0 failures, 0 errors, 0 skips; no full suite and no real provider calls. | Implemented route/data binding, legacy mode/room redirects to canonical set detail without default-test assumptions, mode sectionId, player JS selector alignment, Writing/Speaking and Reading/Listening result navigation, and Spring DI constructor selection needed for focused app-context tests. | User review, then commit/push only after explicit approval; after commit, run Phase 8G phase-gate closure review before starting 8H. |
| 2026-07-10 | Phase 8G Practice Functional Flow Gate | IMPLEMENTED_AND_FOCUSED_TESTED | CLOSED_WITH_ACCEPTED_DEBT | N/A | e737b282981b84eeefb759490d3d6ca524dae08c | Focused command: `mvn "-Dtest=PracticeFunctionalUiContractTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId" test`; 10 tests, 0 failures, 0 errors, 0 skips; no full suite and no real provider calls. | Phase 8G gate review passed. Route/data binding, sectionId mode flow, legacy route correction, player/template JS contract, and result navigation are accepted with debt. Live Speaking AI remains NO-GO. | Commit/push 8G closure docs, then Phase 8H audit only after user approval. |
| 2026-07-10 | Phase 8H Practice Architecture, Security Boundary & Maintainability | PLANNED | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 59706a879db88291859777286f9208635e914d26 | Focused command: `mvn "-Dtest=PracticeFunctionalUiContractTest,PracticeSpeakingMediaUiResourceTest,PracticeAnswerFormMapperTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId,PracticeSpeakingMediaPlaybackControllerTest,PracticeSpeakingMediaPlaybackServiceTest,LocalPrivateSpeakingAudioStorageTest" test`; 68 tests, 0 failures, 0 errors, 2 skips; no full suite and no real provider calls. | Implemented narrow route/view/model/form/media constants, private Speaking media boundary tests, `PracticeAnswerFormMapper` extraction, safe result/provider/private-storage no-leak checks, and stabilized one async playback CSRF test to avoid streaming-body dispatch flake. Phase 8H is not closed. | User review, then commit/push only after explicit approval; after commit, run Phase 8H phase-gate closure review before Phase 9. |
| 2026-07-10 | Phase 8 Closure Stabilization After Playback Test Fix | IMPLEMENTED_AND_FOCUSED_TESTED | CLOSED_WITH_ACCEPTED_DEBT | N/A | 95d50cd8efa1e4b38f15eda256e9fdbf2c22f8d1 | Focused command: `mvn "-Dtest=SpeakingEvaluationNormalizerTest,SpeakingScorePolicyTest,SpeakingPromptRulesTest,SpeakingEvaluationRuleEngineTest,SpeakingEvaluationOrchestratorTest,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingTranscriptionMediaResolverTest,OpenAiSpeakingTranscriptionClientTest,SpeakingFeedbackCompatibilityReaderTest,SpeakingFeedbackViewMapperTest,SpeakingResultRenderingContractTest,SpeakingEvaluationReusePolicyTest,SpeakingEvaluationApplicationServiceTest,SpeakingProviderRolloutReadinessTest,AiCalibrationReadinessPolicyTest,ProviderOperationalReadinessPolicyTest,SpeakingStorageProductionReadinessPolicyTest,AiRolloutReadinessChecklistTest,PracticeFunctionalUiContractTest,PracticeSpeakingMediaUiResourceTest,PracticeAnswerFormMapperTest,PracticeIntegrationTest#setDetailLinksUseActualPracticeTestIds+testModeView+legacyModeRedirectsToSetDetail+legacyRoomRedirectsToSetDetail+resultBackLinkUsesAttemptTestId,PracticeSpeakingMediaPlaybackControllerTest,PracticeSpeakingMediaPlaybackServiceTest,LocalPrivateSpeakingAudioStorageTest" test`; final evidence-based rerun: 182 tests, 0 failures, 0 errors, 2 skips; no full suite and no provider calls. | Phase 8 closed with accepted debt. Speaking Evaluation Deep-Dive evidence-layer policy recorded. Playback range MockMvc async stabilization preserved. Live Speaking AI rollout remains NO-GO. | Commit/push Phase 8 closure docs and playback test stabilization, then Phase 9 audit only after explicit user approval. |
| 2026-07-10 | Phase 9 Immutable Published Practice Versions | READY_FOR_AUDIT_ONLY | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 10768b25f41ac2ecba8e4b8de2229d14672bb595 | Focused commands: `mvn "-Dtest=PracticeServiceTest#startAttemptLocksLatestPublishedVersion+readingResultUsesLockedQuestionVersionAnswerAndExplanationSnapshot,PracticePublisherServiceTest" test`; 31 tests, 0 failures, 0 errors, 0 skips. App-context/Flyway evidence-based rerun after schema/constructor fixes: `mvn "-Dtest=PracticeIntegrationTest#testModeView" test`; 1 test, 0 failures, 0 errors, 0 skips. No full suite and no provider calls. | Implemented normalized immutable version tables, baseline migration, append-only publish graph service, attempt version locks, versioned question snapshot grading/rendering for locked attempts, legacy compatibility fallback, and Phase 9 decision/debt notes. Phase 9 is not closed. | User review, then commit/push only after explicit approval; then Phase 9 gate/closure review before Phase 10 audit. |
| 2026-07-10 | Phase 9 Ungrouped Question Version Snapshot Stabilization | IMPLEMENTED_AND_FOCUSED_TESTED | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | 10768b25f41ac2ecba8e4b8de2229d14672bb595 | Focused command: `mvn "-Dtest=PracticeServiceTest#startAttemptLocksLatestPublishedVersion+readingResultUsesLockedQuestionVersionAnswerAndExplanationSnapshot+publishedVersionIncludesUngroupedQuestion+readingResultUsesLockedUngroupedQuestionVersionSnapshot,PracticePublisherServiceTest,PracticeIntegrationTest#testModeView" test`; 34 tests, 0 failures, 0 errors, 0 skips. No full suite and no provider calls. | Narrow stabilization added immutable snapshot and versioned rendering coverage for null-group questions, updated V24 baseline handling for grouped and ungrouped question versions, and preserved old live-fallback attempts. Phase 9 is not closed. | Phase 9 A-F implementation review again, then commit/push only after explicit approval. |
| 2026-07-10 | Phase 9 Final Review and Full-Suite Evidence | IMPLEMENTED_AND_FOCUSED_TESTED | ACCEPTED_READY_FOR_COMMIT | N/A | 10768b25f41ac2ecba8e4b8de2229d14672bb595 | Review verdict: PHASE9_ACCEPTED_READY_FOR_COMMIT. Full suite command: `mvn test`; 1160 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS. Skips are environment/permission-dependent symlink checks in `LocalPrivateSpeakingAudioStorageTest`: `rejectsSymlinkEscapeWhereSupported` and `rejectsSymlinkObjectWhereSupported`; not functional regressions. | Final stabilization notes: fixtures create immutable published versions before attempts; ungrouped/null-group questions are snapshotted only when section mapping is unambiguous; multi-section null-group ambiguity is not assigned silently; V24 marks ambiguous legacy paths with compatibility metadata instead of locking to a wrong version; versioned result rendering includes null-group questions exactly once; MockMvc playback range test avoids async streaming/header race while preserving 206/range header coverage. Phase 9 is implemented/focused-tested and accepted for commit, not closed. | Commit/push Phase 9 implementation, then Phase 9 gate/closure review before Phase 10 audit. |
| 2026-07-10 | Phase 9 Immutable Published Practice Versions Gate | ACCEPTED_READY_FOR_COMMIT | CLOSED_WITH_ACCEPTED_DEBT | ac658dc32a6b98d01760fd2cce352825876d53e5 | b764ba5c6de70bc2275b7a25c8722e83cda88f78 | Source review plus prior full suite evidence: `mvn test`; 1160 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS. No provider calls. | Phase 9 gate accepted and closure docs committed. Normalized immutable version graph, append-only publish, attempt version locks, snapshot-based scoring/rendering, legacy compatibility, null-group stabilization, and privacy guardrails are accepted with debt. Phase 10/11/13 remain not started; live Speaking AI rollout remains NO-GO. | Phase 9G stabilization before Phase 10 audit. |
| 2026-07-10 | Phase 9G Immutable Version Stabilization & Encoding Cleanup | CLOSED_WITH_ACCEPTED_DEBT | IMPLEMENTED_AND_FOCUSED_TESTED | N/A | ac658dc32a6b98d01760fd2cce352825876d53e5 | Focused evidence: 9G-A null-group ambiguity rejection/fallback, 4 tests passed; 9G-B content hash fail-closed, 2 tests passed; 9G-C transcription retry multipart rebuild, 4 tests passed; 9G-D mojibake cleanup, 3 tests passed; 9G-F focused gate, 12 tests passed. User-approved full suite: `mvn test`; 1163 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS. No provider calls. | Rejects ambiguous null-group publish/version creation across multi-test or multi-section graphs; V24 baseline avoids wrong locks and uses compatibility fallback; runtime publish hash fails closed; transcription retry reopens audio stream; narrow UTF-8 cleanup applied. Phase 10/11/13 remain NOT_STARTED. | Commit/push Phase 9G stabilization, then Phase 10 audit. |
| 2026-07-10 | Phase 10 Academic Program / Certification Configuration Decisions | NOT_STARTED | READY_FOR_IMPLEMENTATION_PLANNING | N/A | 97625154832b26cb71302814ecd4e91161c84bce | Docs-only decision record; no tests run, no provider calls. | Locked Phase 10 as backend assessment/program policy foundation: canonical MVP question types, MCQ aliasing, scoring policy contracts, normalized DB program config, answer spec/version snapshot requirements, Reading/Listening explanation context, and A→H implementation process. | Commit/push docs-only decision record, then begin Phase 10A only after user approval. |
| 2026-07-10 | Phase 10 Assessment Foundation and Stabilization Gate | READY_FOR_IMPLEMENTATION_PLANNING | CLOSED_WITH_ACCEPTED_DEBT | this commit | 448bdb1 | Focused stabilization gate: 41 tests, 0 failures, 0 errors, 0 skips. Final full suite: 1208 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS on JDK 26.0.1 and isolated MySQL schema at V25. | Implemented 10A-10H canonical contracts, deterministic scoring, normalized policy persistence, immutable policy snapshots, typed R/L explanation, player redaction, ownership/graph fixes, import child-parent binding, upload path hardening, and error/log hygiene. Phase 10 closes with explicitly routed Phase 11/12/13/15 debt; live Speaking AI remains NO-GO. | Commit/push Phase 10; then begin Phase 11 audit-only baseline and contract-to-editor gap map after explicit user approval. |
| 2026-07-10 | Pre-Phase 11 Authoring and Import Contract Gate | NOT_STARTED | AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES | N/A | b9cd754 | Audit-only source/schema/UI review; seven supplied screenshots mapped to editor, publisher, PDF import, scoring and revision code. No tests and no provider calls because no production code changed. | Phase 10 closure confirmed. Found P0 passage/transcript persistence loss risk, policy/legacy type mismatch, missing exam-template identity, legacy/TOPIK-hard-coded PDF normalization, score-unit inconsistency and missing deterministic Excel import. Phase 12B simplified to direct collaboration plus owner lock, immutable history and no notification requirement. | User review, then begin Phase 11A foundation fixes only after explicit approval. |
| 2026-07-11 | Phase 11 Lecturer Authoring and Import Implementation | AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES | IMPLEMENTED_PENDING_REVIEW | N/A | b9cd754 | JS and inline-script syntax green; focused Phase 11 gate 31/31; final Excel/media/MATCHING gate 47/47; clean V1-V26 migration/Hibernate validation and compatibility integration green. Pool-bounded full suite: 1240 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS. Browser smoke passed for editor, PDF, R/L/W/S Excel actions, centered Excel preview contract and safe teacher preview; no console error or provider call. | Implemented 11A-11G canonical draft-v3/stimulus/score contract, single squashed V26 template persistence, Set/Test/Skill/Group hierarchy, policy-driven type editors, safe learner preview, Excel-v2 all-skill import with detailed row/media/options preview and compact numbering, typed MATCHING pairs, guided/canonical PDF flow, first-publish binding fix, confidence gates and dead-module cleanup. V26 preserves nullable legacy bindings while new publish requires metadata. Automated/runtime 11H is green. | Run the mandatory closure stabilization gate before top-level acceptance. |
| 2026-07-11 | Phase 11 Closure Stabilization Gate | IMPLEMENTED_PENDING_REVIEW | STABILIZED_PENDING_USER_ACCEPTANCE | N/A | b9cd754 | Static JS/inline-script and diff checks green; focused closure rerun 20/20; final pool-bounded full suite 1242/1242, 0 failures/errors/skips, BUILD SUCCESS on JDK 17/MySQL V26. Runtime QA verified Set/Test/Skill/Group, menu ARIA, rename focus/select, dismissible delete confirm, sanitized preview, real PDF navigation, exact L1 Excel route and zero console warning/error. Ports 8080/8081/8082 were closed; no provider call. | `PHASE_11_CLOSURE_STABILIZATION_GATE = CLOSED_GREEN`. Stabilization fixed menu semantics, delete-exit behavior, unused/raw editor state exposure, unused controller dependencies/logging and Excel JSON 403/404 handling. Research was merged additively; all prior Phase 13/14 baseline directions remain mapped and open until their own audit/UAT. | User acceptance, then stage/commit/push only on explicit instruction. Do not audit/start Phase 12 in this gate. |
| 2026-07-11 | Phase 11 Lecturer Authoring and Import Acceptance | STABILIZED_PENDING_USER_ACCEPTANCE | CLOSED_WITH_ACCEPTED_DEBT | 324dad9019e61d4c814c350c6ce6ac88247c8997 | b9cd7545e3f39e517453817de4ba76aaff2d14f3 | User explicitly approved stage/commit/push after the green closure gate; pushed `feature/practice` to origin. The accepted evidence remains focused closure 20/20, full suite 1242/1242 and runtime browser QA with no provider call. | Phase 11 is closed with its governance, material, learner UX and release debt routed to Phase 12/13/15. | Run the dedicated pre-Phase 12 audit-only gate; do not start implementation automatically. |
| 2026-07-11 | Pre-Phase 12 Materials, Permissions and Governance Gate | NOT_STARTED | AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES | N/A | 324dad9019e61d4c814c350c6ce6ac88247c8997 | Static source/schema/security audit of V4/V25/V26 RBAC, ownership, revision/republish behavior, material storage/delivery, profile/template governance and compatibility. No production code/migration changed, no tests rerun and no provider call. | Found four required P0 foundations: effective action permissions, collaboration/owner lock, append-only restore/republish, and unified private/published material identity with immutable references. P1 covers config governance, archive action and upload/retention hardening; Phase 12 remains `NOT_STARTED`. | User reviews the audit and docs; commit/push docs only on explicit instruction, then begin 12A only after a separate implementation GO. |
| 2026-07-11 | Phase 11I PDF/Excel Teacher UX Correction | CLOSED_WITH_ACCEPTED_DEBT | IMPLEMENTED_AUTOMATED_GATE_GREEN_BROWSER_UAT_DEFERRED | N/A | 324dad9019e61d4c814c350c6ce6ac88247c8997 | Compile and loaded-template JS checks green; focused and broad Practice/PDF/Excel regressions green; isolated Flyway V1-V27 plus Hibernate validation green; pool-bounded full suite 1244/1244, 0 failures/errors/skips, BUILD SUCCESS on JDK 26.0.1/MySQL V27. Browser QA was explicitly removed by the user and no provider call was made. | Added program/template-first PDF target context, source/display numbering, reactive teacher-facing PDF controls, role-safe payload preview and compact/detail Excel preview. Browser QA is deferred to the mandatory end-of-Phase-12 route/dead-code/UI stabilization. Full Manual UAT and clean high-quality multi-certificate seed data remain Phase 15. | Review these uncommitted changes. Do not start Phase 12 until the user gives a separate implementation GO. |
| 2026-07-12 | Phase 12 Materials, Permissions and Governance Implementation / Automated Stabilization | AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES | STABILIZED_AUTOMATED_GATE_GREEN_BROWSER_QA_DEFERRED | this commit | 324dad9019e61d4c814c350c6ce6ac88247c8997 | Focused gates: restore/governance 17/17, storage/material 22/22, governance hardening 13/13 and Practice integration 78/78. Fresh MySQL V1-V27/Hibernate validation and static security/route/migration/UTF-8 checks are green. Final pool-bounded full suite: 1293/1293, zero failures/errors/skips, BUILD SUCCESS on JDK 26.0.1/MySQL V27; no provider call. Browser QA was explicitly skipped by the user. | Implemented 12A-12E action RBAC, collaboration/owner lock, archive lifecycle, append-only historical restore, immutable assessment governance, authenticated material delivery, content-signature checks, durable asset lifecycle and provider-neutral R2 readiness. `PHASE_12_AUTOMATED_STABILIZATION_GATE = CLOSED_GREEN`; closure remains open because browser QA was deferred. R2 integration, complete managed system-rule runtime migration, production Speaking media and release UAT remain routed debt. | User explicitly requested stage/commit/push of this checkpoint including `docs/research-input`. Next, review the deferred browser closure decision; do not merge or open Phase 13 without explicit instruction. |

## Current Required Next Action

Current next action:

Review the committed Phase 12 feature-branch checkpoint and automated/static
evidence. It was stage/commit/pushed only after explicit user instruction.
`PHASE_12_AUTOMATED_STABILIZATION_GATE = CLOSED_GREEN`, but the user explicitly
skipped browser QA, so `PHASE_12_CLOSURE_STABILIZATION_GATE` remains
`OPEN_BROWSER_QA_DEFERRED`. Do not merge, claim product GO or open Phase 13
without the next explicit user instruction.

Phase 8 overall is CLOSED_WITH_ACCEPTED_DEBT. Phase 9 is
CLOSED_WITH_ACCEPTED_DEBT, with Phase 9G stabilization committed. Phase 10 is
CLOSED_WITH_ACCEPTED_DEBT after its implementation and stabilization gate.
Phase 11 is `CLOSED_WITH_ACCEPTED_DEBT`; Phase 12 is
`STABILIZED_AUTOMATED_GATE_GREEN_BROWSER_QA_DEFERRED`; Phase 13+ remain
NOT_STARTED. Live Speaking AI rollout remains NO-GO. React modernization
remains future-only after Phase 16. Do not start Phase 13 or perform broad
UI/React modernization without the next explicit phase approval.

## Long-Term Direction After Phase 16

After Phase 16, the project may continue into a separate modernization and commercialization program.

Direction:

- expand beyond `/practice`;
- improve other product features;
- prepare commercial product capabilities;
- create a separate clone/fork/branch if needed;
- keep Spring Boot backend for trusted business logic;
- gradually replace Thymeleaf server-rendered UI with React client;
- move toward API-first contracts;
- migrate feature by feature, not big-bang rewrite.

Important:

React modernization is not current scope.

During current roadmap:

- do not migrate Thymeleaf to React;
- do not introduce a second frontend;
- do not redesign the whole app;
- do not move scoring/auth/storage/provider secrets into browser.

Target future architecture:

React client
-> versioned API
-> Spring Boot backend
-> MySQL / private storage / AI providers

Commercial readiness also requires:

- security;
- privacy;
- monitoring;
- operations;
- support;
- deployment;
- compliance;
- UAT;
- possibly tenancy/billing if introduced.

## Before Every Task Checklist

Codex must answer internally:

1. Have I read `CODEX_PRACTICE_WORKFLOW.md`?
2. What is the current required next action?
3. Does the user request match the allowed workflow state?
4. Is this audit, implementation, stabilization, or commit/push?
5. Is the request trying to skip a required phase?
6. Is the request trying to enter Phase 9 before 8G?
7. Is the request outside `/practice`?
8. Is `.codex-test-detail-csrf.patch` untouched?
9. If Maven/git/test/push needs permission, has the current prompt already
   authorized it so I should use the required permission immediately?
10. Am I about to run full suite for a small slice?
11. Am I about to claim live rollout readiness without evidence?
12. Am I about to mark a phase closed without commit/review/test evidence?

If mismatch exists, stop and ask user.
