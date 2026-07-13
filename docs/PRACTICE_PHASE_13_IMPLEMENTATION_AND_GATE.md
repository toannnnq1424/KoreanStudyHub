# Practice Phase 13 Implementation and Gate

Last updated: 2026-07-14

## 1. Authority and scope lock

This document is the implementation checkpoint for Phase 13. It supplements,
and does not replace, `CODEX_PRACTICE_WORKFLOW.md` or
`PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`.

The user explicitly opened Phase 13 after the reduced-scope documentation was
committed and pushed in commit `c362ef4`. The earlier deferred Phase 12 browser
closure remains historical evidence; it must not be represented as a passed
browser gate.

The active product scope is fixed:

- one implicit KSH practice scope;
- hierarchy `Set > Test > Skill > Group > Question`;
- no program, certificate, TOPIK-level, scenario, template or generic policy
  selector;
- Reading and Listening support `SINGLE_CHOICE`, `FILL_BLANK` and
  `TRUE_FALSE_NOT_GIVEN`;
- Writing keeps Q51-Q54 as `ESSAY`;
- Speaking remains a first-class skill;
- lecturer-to-lecturer collaboration, immutable history, private materials and
  Reading/Listening deterministic scoring plus AI explanation remain intact.

PREP research and the supplied screenshots are capability evidence only. They
may inform hierarchy, preflight, player, result, progress and state design, but
KSH must not copy PREP branding, assets, wording, CSS, APIs or routes.

## 2. Slice ledger

| Slice | Status | Evidence / next action |
|---|---|---|
| Phase 13 entry audit | `COMPLETE` | Existing routes, repositories, DTOs, templates, CSS and attempt semantics inspected on 2026-07-14 |
| 13A library/state foundation | `COMPLETE_FOCUSED_GATE_GREEN` | Bounded catalog, learner access, navigation/performance correction, focused tests and route/dead-code audit are complete; no browser QA is claimed |
| 13B mode/preflight | `NOT_STARTED` | Start only after the 13A documentation, commit and push checkpoint |
| 13C shared player | `NOT_STARTED` | Preserve answer-leak guard and version-locked attempts |
| 13D result overview | `NOT_STARTED` | Keep score, scale, completion, timing and feedback availability separate |
| 13E result evidence | `NOT_STARTED` | Separate official key, teacher explanation and AI artifact |
| 13F progress/recovery | `NOT_STARTED` | Real aggregates only; no decorative percentages |
| 13G responsive/a11y/performance | `NOT_STARTED` | Includes UTF-8, icon, reduced-motion and large-catalog sweeps |
| 13H stabilization gate | `NOT_STARTED` | Static/dead-route audit, focused/full tests and browser journeys before closure |

After each completed slice, this ledger and the detailed evidence section must
be updated before starting the next slice.

The slice protocol is fixed by the user: audit changed and obsolete routes, run
only tests related to the slice, update Markdown, then commit and push. Browser
QA and the full regression suite are consolidated into 13H before Phase 13 can
close. The user performs interim visual review; an individual slice must not
claim browser evidence that was not run.

## 3. Phase 13 entry audit findings

This section records the pre-implementation baseline. Resolutions completed in
13A are recorded in Sections 4.5-4.7 instead of rewriting the historical
finding.

### 3.1 Library and access

- `GET /practice` currently calls `PracticeService.listPublished()` and loads
  every published set without a bounded lazy-loading contract.
- Search and skill filtering are client-only DOM operations, are not reflected
  in the URL and cannot scale to a realistic catalog.
- Published set/detail routes do not currently enforce the GLOBAL versus CLASS
  learner visibility rule. Material playback has a stricter access boundary,
  so catalog/content access must be brought into alignment.
- Cards infer skill from the legacy set field and `metadataJson.contains(...)`
  instead of the actual test/section graph.
- The right rail contains placeholder AI quota and skill percentages. Phase 13
  must never present these as real learner data.

### 3.2 Hierarchy and journey

- The persisted graph already supports multiple tests and multiple skill
  sections, but the library flattens each set into one decorative skill card.
- Set and test details contain inconsistent totals: some labels use set-wide
  attempts or question counts where test-specific values are required.
- A mode route exists, but the normal test-detail action can start a section
  directly and bypass preflight.
- Attempts are currently per section. Full-test aggregation therefore requires
  an explicit session/composition contract in 13B/13C; it must not be faked by
  a visual toggle.

### 3.3 State and presentation

- Current UI does not share a single attempt-state vocabulary across library,
  player and result.
- The catalog template contains PREP-labelled implementation comments and a
  large inline script. External research wording must be removed from active
  product source.
- Current practice CSS uses large radii, decorative covers and a narrow
  blue-only palette. Phase 13 should move toward a quiet, task-oriented KSH
  interface without a framework rewrite.
- Bookmarking is local-browser state. It may remain a convenience, but it must
  be labelled and implemented independently from server progress.

## 4. 13A implementation decisions

### 4.1 Backend boundaries

Add a dedicated learner-facing catalog/access service instead of extending the
already large `PracticeService`:

1. Resolve active class IDs from enrollments.
2. Query only published sets visible to the learner:
   - GLOBAL sets;
   - CLASS sets for an active class enrollment;
   - the learner's own published set where applicable.
3. Apply normalized search and skill filters on the server.
4. Return bounded batches; the learner-facing UI lazy-loads the next batch when
   the catalog sentinel enters the viewport and never renders page-number UI.
5. Batch-load tests, sections and attempts for the current lazy batch. Do not
   query questions merely to decorate catalog cards.
6. Derive card skills and completed-test counts from the graph, never from JSON
   string search or per-skill question limits.
7. Reuse the same visibility check for set, test, mode and attempt-start routes.

Cross-owner lecturer collaboration remains an authoring concern and must not
silently grant learner visibility to unpublished or class-restricted content.

### 4.2 Shared learner state vocabulary

The UI state contract is:

- `NOT_STARTED`: no usable attempt;
- `IN_PROGRESS`: a resumable section attempt exists;
- `SUBMITTED`: learner submission is persisted and deterministic result exists,
  but required asynchronous feedback is not complete;
- `SCORING`: asynchronous evaluation is queued or processing;
- `SCORED`: required grading/evaluation succeeded;
- `PARTIAL`: deterministic result is usable but optional explanation/evidence
  is unavailable;
- `FAILED`: required evaluation failed and a recovery action is available;
- `STALE`: a saved attempt cannot safely resume against the current delivery
  contract.

`DISCARDED` is historical and does not become a catalog state. A newer usable
attempt always takes precedence over an older discarded attempt.

### 4.3 UI foundation

- Keep the current server-rendered Thymeleaf architecture.
- Replace client-only filtering with a GET form and URL-backed filters.
- Render Set cards with real test count, skills and progress. Question count is
  neither queried nor exposed by the catalog contract.
- Lazy-load bounded server batches with loading, end-of-list, error and retry
  states. Search/filter state remains in the URL; internal batch numbers do not.
- Remove fake quota/progress values and show only real aggregates or an honest
  empty state.
- Introduce Baekho as an original KSH companion using the supplied sprite atlas,
  a small state machine and a static fallback. Respect reduced motion and allow
  collapse; do not use GIFs as the runtime architecture.
- Keep familiar SVG controls and avoid emoji product icons.

### 4.4 Catalog-card visual contract

The supplied PREP library screenshot informed only the compact scan hierarchy:
resume strip, skill filters, search, count and a dense cover grid. KSH keeps its
own routes, copy, colors, access states and Baekho identity.

- A one-skill set uses that skill's cover treatment.
- A two-skill set uses a neutral `2 KỸ NĂNG` cover and both relevant icons.
- A three- or four-skill set uses a neutral `TỔNG HỢP` cover and all relevant
  icons. It must never inherit the first section's skill cover.
- The card footer always reserves four Lucide-style skill icons in the order
  Listening, Reading, Writing and Speaking. Skills present in the real section
  graph are highlighted; absent skills remain muted. This replaces the visible
  question-count label.
- On hover or keyboard focus, Baekho reflects the card's real skills. For a
  multi-skill set the mascot cycles only through present skills every two
  seconds, stops immediately when the card loses hover/focus, and does not
  auto-cycle when the learner requests reduced motion.

### 4.5 Automated evidence (2026-07-14)

- JDK 17 focused unit/service/static gate: `92/92`, zero failures and zero
  errors. It covers the catalog, learner access, repository contract, shared UI
  dependency contract and the affected `PracticeService` regressions.
- JDK 17 login integration gate: `8/8`, including authenticated access to
  `/login` redirecting to `/` while preserving valid Spring Security saved
  requests.
- JDK 17 selected Practice integration gate: `8/8`, covering the index, real
  completed-test semantics, bounded lazy fragment, unrelated-class denial and
  four progress-page state/privacy journeys.
- `git diff --check` and the changed JavaScript syntax checks are required to be
  green immediately before commit.
- The integration environment currently points to legacy schema
  `ksh_phase12_browser` at Flyway V29 while this reduced-scope branch ends at
  V25. These tests prove current rendering/runtime behavior only; they are not
  clean-migration evidence. Clean V1-to-V25 migration verification remains part
  of the consolidated 13H stabilization requirement.

### 4.6 Navigation and refresh performance correction

- An authenticated GET `/login` now redirects to `/`; ordinary successful login
  still uses `/`, while a valid protected deep-link saved request remains
  supported.
- Shared pages no longer block first render on Google Fonts or iziToast CDN
  requests. Toasts use a small local implementation and the existing local CSS.
- The progress page no longer parser-blocks navigation on Chart.js. Its chart
  bundle is requested after page load during an idle window, with a text
  fallback when the optional CDN is unavailable.
- One progress request now loads a single shared attempt/set/test/section
  snapshot for both overview and analytics instead of loading the same data
  twice. A focused service test locks this query-reuse contract.
- These changes address blocking dependencies and redirect behavior only. They
  do not claim browser timing measurements because browser QA was not requested
  for this slice.

### 4.7 Route and dead-code audit

- `/practice/catalog` is live and consumed by the catalog lazy-loader; search,
  skill and class filters remain URL-backed.
- Set, test, mode and attempt-start routes reuse the learner visibility guard.
- Existing legacy practice aliases remain intentional compatibility redirects
  and retain route tests; they were not misclassified as dead routes.
- The superseded unbounded catalog pipeline, its model attributes, repository
  methods, DTO and tests were removed.
- The obsolete catalog question-count query and DTO field were removed after
  the UI replaced that label with real skill indicators.

## 5. 13A closure decision

13A is complete at the focused code gate:

- repository/service coverage includes GLOBAL, enrolled CLASS, unrelated CLASS,
  owner, search, skill filter, batch bounds and empty results;
- integration coverage confirms URL query/model behavior, bounded fragments and
  inaccessible learner routes;
- catalog enrichment uses bounded batch queries rather than a repository call
  per card;
- active catalog templates contain no fake quota, fake skill percentage or
  visible question-count cap;
- changed-route and superseded-code audit is complete.

Desktop/mobile/no-result visual review and console/network inspection were not
run by Codex in this slice, by explicit user direction. They remain user review
input and are consolidated as mandatory browser evidence in 13H. Therefore
13A is not a browser-QA or production-readiness claim.

## 6. Phase closure rule

Phase 13 cannot be marked complete after visual implementation alone. 13H must
run a stabilization pass covering dead code, controller-template route wiring,
learner authorization, answer leakage, UTF-8, responsive/a11y behavior,
focused/full automated tests and browser journeys. Browser QA is run once at
that closure gate rather than after every implementation slice. Any deferred provider,
Cloudflare R2 or production Speaking-media item must be recorded explicitly and
must not be described as green evidence.
