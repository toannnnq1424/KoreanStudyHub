# Practice Phase 13 Implementation and Gate

Last updated: 2026-07-15

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
| 13B detail/preflight/version lock | `WORKING_TREE_PREFLIGHT_IMPLEMENTED_FINAL_RERUN_PENDING` | Set/test detail was pushed; immutable attempt routing and canonical Speaking delivery were green on JDK 17 before the latest 13C edits. The working tree now includes the full-screen microphone preflight, but final rerun evidence is still required before marking 13B closed |
| 13C skill-native players | `TEMP_CHECKPOINT_COMMIT_PENDING_FINAL_GATE` | Canonical Speaking delivery, authoring/preview parity, route-performance fixes and a dedicated `player-speaking` implementation are in the working tree. This is not closed: rerun focused tests/static audits after the checkpoint commit before claiming 13C green |
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

## 6. 13B correction checkpoint and 13C boundary

The first 13B implementation checkpoint delivered the reduced-scope set/test
detail journey, independent skill attempts, latest-two expandable history and a
blocking Speaking device check. User review then exposed two remaining contract
gaps, so 13B is reopened rather than falsely marked complete.

### 6.1 Immutable attempt-route correction

- Attempt delivery now validates one coherent immutable chain:
  `published version -> set version -> test version -> section version`.
- The attempt skill is derived from the immutable section snapshot, not from a
  mutable live section after publication.
- An in-progress attempt is reused only when its skill and all four version-lock
  IDs match the current immutable delivery. A stale/mismatched attempt is
  discarded before a fresh attempt is created.
- Focused evidence: JDK 17 `PracticeServiceTest` is green `76/76`, zero failures
  and zero errors. No browser QA or provider call is claimed.

### 6.2 Canonical Speaking delivery JSON

Speaking timing and prompt-delivery settings belong in the existing canonical
`practice_questions.question_content_json`, under a typed
`speakingDelivery` object. Do not create another timing/policy table.

Required shape:

```json
{
  "schemaVersion": "question-content-v1",
  "speakingDelivery": {
    "promptAudioReference": "/practice/materials/{assetId}/content",
    "promptPlayLimit": 1,
    "preparationSeconds": 30,
    "responseSeconds": 60
  }
}
```

The publisher must copy this JSON unchanged into
`practice_question_versions.question_content_json`; the player reads only the
attempt-locked version. A new Speaking question must fail publish when prompt
audio or valid timing/play-limit values are missing. Legacy content may be read
through a narrow compatibility path, but it must not weaken new authoring.

Implementation checkpoint (2026-07-14):

- `QuestionContent.SpeakingDelivery` is the typed canonical object; no timing,
  replay-limit or prompt-delivery table was added.
- Legacy top-level `speakingPromptAudioUrl`, `speakingPromptPlayLimit`,
  `prepTimeSeconds` and `respTimeSeconds` values are normalized into the typed
  object and mirrored only as a compatibility boundary.
- New Speaking drafts fail publish without prompt audio or when replay,
  preparation or response limits are invalid. Historical JSON without the new
  object remains readable but cannot be used to weaken new publication.
- Immutable `PracticeQuestionVersion` construction preserves the canonical JSON
  exactly.
- Excel v2 uses its existing per-question audio reference and adds teacher-facing
  replay, preparation and response columns; its generated Speaking examples and
  preview/import path produce the same canonical object.
- Focused JDK 17 evidence: codec, normalization, validator, preview and immutable
  snapshot tests are green `28/28`; Excel template/preview/import tests are green
  `4/4`; zero failures and zero errors. No browser QA, full suite or provider call
  is claimed.

### 6.3 Speaking authoring and preview checkpoint

- The manual editor now exposes prompt audio per Speaking question, replay limit,
  preparation time and response time. It reuses the governed draft-audio upload
  boundary and persists only the material content reference in canonical
  `speakingDelivery`; no route or table was added for timing.
- Removing prompt audio clears both the compatibility field and canonical
  reference so stale audio cannot silently reappear during normalization.
- The teacher preview reads the same canonical object as the learner contract,
  renders the prompt audio player and delivery timings, and treats missing audio
  as a blocking quality issue rather than inventing a text-only Speaking flow.
- The editor's stale image-material drag URL was corrected to the governed
  `/practice/materials/{id}/content` boundary. Its current material drawer remains
  image-only; per-question prompt audio is selected through the dedicated audio
  control.
- Focused JDK 17 authoring/preview gate is green `41/41`, zero failures and zero
  errors. The run covers the UI contract, canonical codec/normalizer/validator,
  immutable preview and Excel import. No browser QA, full suite or provider call
  is claimed.

### 6.4 13B/13C Speaking implementation checkpoint

Current working-tree state:

- Full-screen KSH Speaking preflight is implemented as a separate route/template
  before the learner enters the Speaking player. It checks output confirmation,
  browser recording support, live microphone permission, in-memory test
  recording/playback/quality state and retry. Device-check audio is never
  uploaded.
- Speaking dispatches away from the general player to dedicated
  `player-speaking` template/CSS/JavaScript assets.
- The intended state machine remains one question at a time:
  `PROMPT_PLAYBACK -> PREPARING -> RECORDING -> UPLOADING -> QUESTION_DONE`, then
  automatic next question or final submit. If autoplay is blocked, the player
  must show an explicit learner action instead of silently advancing.
- Recorded audio is the required primary answer; a textarea cannot substitute
  for it. Leaving interrupts/discards the Speaking attempt and must not create a
  resumable draft.
- Teacher authoring and teacher preview expose the same prompt audio, playback
  count, preparation time and response time used by the immutable learner
  player.

This checkpoint is intentionally not final 13C closure. The implementation was
interrupted before the final post-cleanup focused rerun and static route audit,
so the next agent must verify before marking this slice green.

### 6.5 13C route-performance and UI integrity checkpoint

User review during 13C exposed two direct product issues: `/practice` felt slow
on refresh/navigation, and a set containing multiple skills could render as
multiple catalog cards. A supplemental route audit also identified concrete
template/CSS regressions. These items are in 13C scope because they affect the
player entry path and the skill-native library/detail journey.

Implementation checkpoint (2026-07-14):

- Non-Speaking `/practice/attempts/{attemptId}` no longer loads the full live
  set graph and then discards it. The controller now asks for a single
  `AttemptPlayerView`, and the service reuses one immutable snapshot for both
  section delivery and player question groups.
- `/practice/progress` analytics are bounded to the latest 100 non-discarded
  attempts for the current server-rendered view instead of loading every
  historical attempt into memory.
- The catalog service de-duplicates set rows defensively before enrichment, so
  a multi-skill set must render as one card with multiple highlighted skills.
- First publish now writes the published set ID back onto the draft, so future
  publishes of the same draft update the same set instead of creating a new set
  per skill-shaped publication path.
- `practice_sections(set_id, skill)` is indexed for skill-filtered catalog
  lookups, and the catalog query now compares normalized skill values directly.
- The direct Thymeleaf `th:classappend` quote bug in set/test detail templates
  is fixed so skill/state classes match the CSS selectors.
- The duplicate/invalid gradient declaration in `practice-index.css` is removed.

Additional checkpoint note (2026-07-15):

- A supplemental audit of `/practice` was triaged. The template/CSS defects are
  direct 13C regressions and have been patched. The legacy `/mode` references
  are compatibility/history only. Speaking learner-media playback for
  lecturer/admin review is deferred to result/review capability work because it
  does not block learner Speaking delivery in 13C.
- Static scans after the patch found no remaining broad attempt-player loads
  (`getPractice(attempt...)`, duplicate `getAttemptSectionDelivery(attemptId)`,
  duplicate `getPlayerQuestionGroupsForAttempt(attemptId)`) and no remaining bad
  `th:classappend="|'...` pattern. The old unlimited attempt-repository method
  was then removed, so focused tests must be rerun before closure.
- Temporary checkpoint evidence before commit: `git diff --check` passed;
  changed JavaScript syntax checks passed for `player-speaking.js`,
  `speaking-preflight.js`, `practice-test-detail.js`,
  `manage-authoring-contract.js` and `manage-draft-preview.js`; JDK 17
  `mvn -DskipTests compile` passed at 2026-07-15 00:43 Asia/Ho_Chi_Minh.
  Focused tests are still pending after the latest cleanup, so this evidence is
  not enough to close 13C.

Guardrail for future Codex work:

- Player and detail routes must not call broad graph-loading methods when the
  route only needs one attempt, one test or one skill section. Load the narrow
  immutable snapshot once and pass it through the request path.
- If a repository query can return duplicate parent rows because of joins or
  skill filters, the service boundary must de-duplicate before rendering and
  lock that behavior with a focused test.

Deferred from this checkpoint:

- The Speaking learner-media playback controller still only supports the
  learner-owner path. Lecturer/admin review playback is a future result/review
  capability, not a blocking 13C player-state-machine requirement.
- The legacy `/mode` route remains intentionally removed from the active learner
  journey; old aliases are compatibility redirects only. Any stale docs or tests
  must describe this as historical compatibility rather than a live mode page.

## 7. Continuation Prompt For The Next Agent

Start by reading these files in order:

1. `CODEX_PRACTICE_WORKFLOW.md`, especially the latest Phase 13 rows and
   "Current Required Next Action".
2. `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, especially Sections 1,
   2 and 6.
3. The current `git status --short`; do not stage `openspec-temp/`.
4. The attached audit notes only as supporting evidence. Do not expand scope to
   broad permissions unless it blocks the learner 13C flow.

Continue with this sequence:

1. Re-run static audits for `/practice` route performance, stale `/mode`
   references, bad Thymeleaf classappend syntax, duplicate/invalid CSS variables
   and changed JavaScript syntax.
2. Re-run focused JDK 17 tests for `PracticeServiceTest`,
   `PracticeCatalogServiceTest`, `PracticePublisherServiceTest`,
   Speaking media/preflight/player contracts, canonical Speaking delivery codec,
   draft validator, publisher and preview/import tests.
3. Fix only failures related to 13B/13C: Speaking preflight, dedicated
   `player-speaking`, mandatory prompt audio, mandatory recording upload,
   auto-next, discard-on-interrupt, `/practice` catalog duplicate cards and route
   performance. Do not restart program/certificate governance.
4. Update this document and `CODEX_PRACTICE_WORKFLOW.md` after the rerun with
   exact commands and counts.
5. Commit/push a green 13C checkpoint only after the focused gate is actually
   green. Browser/product QA remains consolidated in 13H unless the user
   explicitly changes that.

## 8. Phase closure rule

Phase 13 cannot be marked complete after visual implementation alone. 13H must
run a stabilization pass covering dead code, controller-template route wiring,
learner authorization, answer leakage, UTF-8, responsive/a11y behavior,
focused/full automated tests and browser journeys. Browser QA is run once at
that closure gate rather than after every implementation slice. Any deferred provider,
Cloudflare R2 or production Speaking-media item must be recorded explicitly and
must not be described as green evidence.
