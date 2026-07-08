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

1. Ask the user for permission before running Maven with the needed permission;
   OR

2. If the current prompt already grants test permission, run exactly one
   focused Maven command with the required permission and report that it used
   the known required test permission.

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
- run focused command once;
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

If the current prompt already authorizes the test command, run the focused
command once with the required permission from the start.

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
IN_PROGRESS

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
IMPLEMENTED_AND_FOCUSED_TESTED

Commit:
N/A

Parent:
03f781a74f46cd312c6ffe804dc9278b1f1414e3

Message:
N/A

Meaning:
Private local Speaking media playback now has focused-tested HTTP byte Range support for browser-compatible seeking.

Top-level Phase 8D remains:
IN_PROGRESS

Remaining 8D areas:

- browser recording / MediaRecorder;
- cleanup worker / deployment ownership;
- production object storage;
- consent;
- UI wiring;
- mobile/browser UAT.

#### Phase 8E — Speaking AI Evaluation

Status:
NOT_STARTED

Purpose:
audio/transcript-based Speaking evaluation with fixed schema, internal score only.

#### Phase 8F — Calibration & Production Hardening

Status:
NOT_STARTED

Purpose:
calibration, provider consistency, browser/device testing, monitoring, runbook, privacy/security.

#### Phase 8G — Practice-Wide Functional UI, Integration Regression Audit & Stabilization

Status:
PLANNED

Must happen before Phase 9.

### Phase 9 — Immutable Published Practice Versions

Status:
BLOCKED until Phase 8G is CLOSED_VERIFIED or CLOSED_WITH_ACCEPTED_DEBT by user decision.

Purpose:
immutable published content/rubric/scoring graph and stable historical attempts.

### Phase 10 — Academic Configuration & Multi-Certification

Status:
NOT_STARTED

### Phase 11 — Lecturer Authoring & Import

Status:
NOT_STARTED

### Phase 12 — Materials & Permissions

Status:
NOT_STARTED

### Phase 13 — Results, Progress & UI/UX

Status:
NOT_STARTED

Important:
Phase 13 is for visual polish and richer UX.
Functional breakage belongs to Phase 8G.

### Phase 14 — Report an Error & Content Review

Status:
NOT_STARTED

### Phase 15 — Manual UAT

Status:
NOT_STARTED

### Phase 16 — Chatbot Optional

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

## Current Required Next Action

Current next action:

User review for 8D-D Range Playback and Browser Compatibility implementation summary.

After user review, commit/push only after explicit GO.

Likely candidates:

- 8D-C Browser Recording Implementation;
- 8D-E Cleanup Worker and Operations;
- 8D-F Production Storage Abstraction.

Do not choose automatically.

Do not start 8E until the required 8D path is confirmed or explicitly deferred.

Do not start Phase 9 before Phase 8G.

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
