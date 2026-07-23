# Practice Phase 13 Implementation and Gate

Last updated: 2026-07-24

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

Migration-chain reconciliation on `2026-07-24` returned
`REBASELINE_GO_WITH_GUARDS`. This is plan-only during active Phase 13E: no SQL,
database, Flyway or fixture action is authorized here. The earliest execution
window is after consolidated Phase 13E validation and before 14A, after the
final pre-14 relational contracts are frozen. It stops if any retained,
deployed, shared, canonical or upgrade-supported database must preserve the
current Practice chain.

PREP research and the supplied screenshots are capability evidence only. They
may inform hierarchy, preflight, player, result, progress and state design, but
KSH must not copy PREP branding, assets, wording, CSS, APIs or routes.

## 2. Slice ledger

| Slice | Status | Evidence / next action |
|---|---|---|
| Phase 13 entry audit | `COMPLETE` | Existing routes, repositories, DTOs, templates, CSS and attempt semantics inspected on 2026-07-14 |
| 13A library/state foundation | `COMPLETE_FOCUSED_GATE_GREEN` | Bounded catalog, learner access, navigation/performance correction, focused tests and route/dead-code audit are complete; no browser QA is claimed |
| 13B detail/preflight/version lock | `COMPLETE_FOCUSED_GATE_GREEN` | Set/test detail, immutable attempt routing, canonical Speaking delivery, full-screen microphone preflight and immutable Listening speaker preflight passed the consolidated 13C2 focused validation. Browser/device QA remains in 13H |
| 13C skill-native players | `13C2_FULL_SUITE_GREEN_COMMITTED_PUSHED_PHASE_13_OPEN` | Speaking, Writing, adaptive Reading/Listening, structured media, image-aware AI and visual fill-blank authoring are implemented. The correction passed the complete 1321-test suite and was committed/pushed as `c3ba3a9`; Phase 13 is not closed and final browser/device plus post-13D-13G closure validation remains in 13H |
| 13D result overview and immutable explanation lifecycle | `FULL_SUITE_GREEN_COMMITTED_PUSHED` | JDK 17 compile passed, the consolidated targeted gate passed 311/311 and the requested full suite passed 1642/1642. Implementation is committed as `bcc1467`; migration renumbering is `a089fd1`; main integration on the feature branch is `da350b5`. Browser/device QA remains in 13H |
| 13D bounded result-overview UX/evidence correction | `COMPLETE_WITH_FOCUSED_AND_AUTHENTICATED_ROUTE_GATE_GREEN` | `13D-UX-01..05` correct the shared/Objective/Writing hierarchy and make current transcript-only Speaking a four-row language profile with two null `NOT_SCORABLE` acoustic rows and no subtotal/holistic/attempt score. `git diff --check`, JDK 17 compile and 251/251 non-DB focused tests passed. The two previously blocked authenticated Result Detail cases subsequently passed `2/2` on a fresh disposable schema migrated V1-V44; the disposable schema was removed and the stale fixture schema was not repaired. Exact evidence lives in `docs/PRACTICE_PHASE_13D_RESULT_OVERVIEW_UX_CORRECTION_LIVE_CHANGE_LOG.md` Section 14 and the workflow ledger dated 2026-07-22 |
| 13E result evidence | `READY_FOR_PHASE_VALIDATION` | `13E-00` reconciled the gate; `13E-01..05` implemented the typed three-screen boundary, type-native Objective R/L, four-tab Writing/Speaking Detail and final localization/compatibility reconciliation. Every slice is `IMPLEMENTED_PENDING_PHASE_VALIDATION / ACCEPT_STATIC`; the complete diff and current-source documents are reconciled. Run only the one consolidated JDK 17 gate recorded in the 13E live log Section 12. Pre-14 correctness and post-14 academic/audio/destructive closure remain unopened |
| 13F progress/recovery | `NOT_STARTED` | Real aggregates and operational UX only; no decorative percentages and no replacement explanation pipeline |
| 13G responsive/a11y/performance | `NOT_STARTED` | Includes UTF-8, icon, reduced-motion and large-catalog sweeps |
| 13H stabilization gate | `NOT_STARTED` | Static/dead-route audit, focused/full tests and browser journeys before closure |

The later `13E-03` Writing slice is limited to the typed UI/contract seam:
preserve three stable scoring criteria, render task-bounded diagnostics as
non-additive evidence and state that coverage is not exhaustive. It does not
complete the expanded Korean registry or academic proof.
`PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` later owns the Writing/Speaking
registry plus provider schema/prompt/normalizer/rule/cache identity; final SME
and calibration remain post-14.

After each completed slice, this ledger and the detailed evidence section must
be updated before starting the next slice.

### 2.1 Mandatory prerequisites before 13E implementation

The user added the following precondition on 2026-07-17:

- read the supplied Draw.io XML and available visual Use Case examples;
- audit current `/practice` code together with approved roadmap documents;
- split Practice into bounded capabilities instead of one oversized feature;
- create formal Use Case specifications for those capabilities;
- create one class diagram per capability and multiple sequence diagrams per
  capability;
- create stable Result and Result Detail fixtures for all four skills and give
  the learner URLs for PREP comparison;
- record every request, design decision, static finding, edit and deferral in
  the dedicated Phase 13E live log as work happens.

After those seven preparation steps, a separate correction step must reproduce
and plan the reported Listening preflight defect before implementation: when
the learner can start audible playback, the sound-check action must allow entry
to the test and must not require the sample audio to finish. The investigation
must distinguish missing fixture media from browser playback/state/navigation
defects rather than bypassing the preflight.

After that correction is implemented and evidenced, Step 9 must synchronize the
work with Jira project `SCRUM`. It must inspect existing issues before creating
new records, use the correct Task, Bug and Sub-task hierarchy, and assign work to
Scrum 3 or Scrum 4 according to the actual phase boundary. Work completed before
the synchronization may be added only as transparently labelled
`retrospective/backfilled` issues with real dates and links to the relevant
Markdown ledger and commits; Jira history must not be fabricated.
The Jira batch is limited to module `/practice`. Architecture issues must name
the capability-based Use Case, class-diagram and sequence-diagram work, and must
not absorb unrelated Flashcard, lesson, iConstant or other product modules.

Step 8 completed on 2026-07-17. The canonical seed now has a deterministic
1.8-second check-audio reference in a new immutable published version through
Flyway V44 on integrated main (V38 on the feature branch); arbitrary static media remains rejected and Test Detail renders the
failure feedback. JDK 17 compile and the focused `101/101` gate passed. A real
Chrome journey proved that the confirmation unlocked while audio was still
playing, then opened attempt `13006` with a `30:00` Listening timer. Step 9 Jira
reconciliation created the independent Practice hierarchy `SCRUM-438..SCRUM-480`:
43/43 items, 17 in Scrum 3, 26 in Scrum 4, 28 Done and 15 future items To Do.
The prerequisite gate is complete. On `2026-07-22`, the user gave explicit
Phase 13E GO; the previously blocked authenticated Writing/Speaking Result
Detail routes then passed `2/2` on a disposable fresh V44 schema. Phase 13E is
now `IMPLEMENTATION_IN_PROGRESS_13E_03`: `13E-01` and Objective R/L `13E-02`
are `IMPLEMENTED_PENDING_PHASE_VALIDATION / ACCEPT_STATIC` with no consolidated
validation/test/build/Git evidence; Writing Detail `13E-03` is the only active
implementation conversation.

The source XML currently available at
`SEP490_G103_KoreanHub.drawio.xml` is truncated after 1,025 bytes even though it
declares 84 pages. It is useful only for the initial page metadata/style hint;
it is not valid XML and cannot be treated as a complete design source. The new
artifact pack must therefore be generated from audited code and roadmap facts,
and this limitation must remain documented until a complete source is supplied.

Local UI review uses the isolated schema `ksh_phase13e_result_ui`, migrated from
V1 through V37. The old `ksh_db` is preserved because it contains the previous
Practice V16-V28 history and reports a false/current V29 collision after the
main-branch migration integration. Do not use Flyway repair to conceal that
incompatible test history.

For 13C2, implementation units were individual issues/patches while the
validation unit was the whole phase. All approved issues were implemented and
the complete diff was reviewed before the consolidated validation began. The
final correction cycle ran `git diff --check`, changed-JavaScript syntax checks,
one compile and the smallest focused test set covering the phase. Browser QA and
the final post-13D-13G regression suite remain consolidated into 13H. The user
later explicitly requested one early full-suite correction cycle; that separate
evidence is recorded in Section 6.7. No checkpoint may claim browser or
automated evidence that was not actually run.

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

Visual/interaction expectation clarified from the supplied PREP Speaking
screenshots on 2026-07-15:

- PREP is only a capability and interaction reference. KSH must keep original
  Vietnamese/Korean copy, KSH/Baekho visual identity, routes, CSS and assets.
- The Speaking device check should feel like a focused full-screen step, not a
  small inline widget: back control, clear microphone prompt, test playback,
  live microphone level, playback confirmation, retry and start-disabled until
  the checks pass.
- The learner Speaking room should progress through visible full-screen states:
  guidance/prompt playback, preparation countdown, recording countdown,
  uploading/processing, per-question completion and automatic next-question or
  final submit.
- The learner should see the active part/question number, the prompt or guidance
  card when the delivery contract includes it, a large audio/prompt affordance,
  a compact bottom status panel for countdown and recording waveform, and a
  clear completion state before advancing.
- Audio quality language in KSH may be simpler than PREP during 13C, but the
  preflight must preserve the intent: learners can hear the sample, hear their
  own sample recording and retry before entering the real attempt.
- Lecturer authoring and preview are part of this expectation: a Speaking
  question must expose prompt audio upload/selection, prompt play limit,
  preparation seconds and response seconds, and preview must render the same
  canonical delivery contract the learner player consumes. A text-only Speaking
  authoring path is not acceptable for new published content.

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

### 6.6 13C2 skill-player, media and editor correction checkpoint

The user opened 13C2 as one implementation phase containing several related
learner/player and lecturer/editor corrections. The current working tree
contains these implementation units:

- Speaking preflight remains usable for local microphone recording even when
  the production upload gate is disabled. The dedicated player resolves title,
  prompt, image, audio and timing from the immutable attempt snapshot; invalid
  legacy attempts are discarded with a learner-facing message instead of
  rendering an empty player or leaking a null-pointer failure. The Baekho
  companion is positioned beside the desktop preflight panel and remains
  responsive on smaller screens.
- Lecturer image/audio uploads now keep canonical structured references through
  draft normalization, safe preview, publish, immutable versions and learner
  delivery. Legacy top-level media and governed `![image](...)` references have
  bounded compatibility paths. Upload callbacks capture the original
  section/group/question/option so navigating the editor while an upload is in
  flight cannot attach the result to another item.
- Governed question images can be sent to the configured multimodal AI model for
  Reading/Listening explanation and Writing/Speaking evaluation. Only authorized
  `/practice/materials/{id}/content` JPEG, PNG or WebP assets are resolved; reads
  are bounded, cache identities include image evidence where applicable and raw
  image data is not logged.
- Writing is routed to a dedicated split player. Reading automatically uses a
  focused full-width layout without source material, a stacked layout for short
  source material and a split resizable layout for long passages/images.
  Listening keeps a focused question layout. Reading/Listening/Writing disable
  context-menu/copy/cut/paste/drop, support green highlights and yellow notes,
  and expose the note drawer.
- Listening has a separate speaker preflight backed by canonical section-level
  delivery JSON copied into immutable section versions. Lecturer authoring and
  validation require an internal check-audio asset before new Listening content
  can be published; the learner may confirm audibility as soon as playback
  starts and does not have to wait for the sample to finish.
- Fill-blank authoring keeps stable `{{blank:id}}` values only as an internal
  serialization contract. The lecturer sees a visual content composer with
  numbered inline answer slots and never has to read or paste canonical token
  strings. Paste is plain-text sanitized, placement is explicit, and strict
  missing/unknown/duplicate-slot validation remains aligned with lecturer
  preview and learner delivery. Lecturer preview now has
  skill-native Writing and Speaking states and adaptive Reading/Listening
  layouts closer to their learner players.
- Canonical-only draft reloads preserve option IDs, selected single-choice or
  true/false/not-given answers, fill-blank IDs and accepted values instead of
  recreating legacy editor state. New Speaking publication also rejects prompt
  audio outside the governed KSH material route, matching learner delivery.
- Speaking missing-audio delivery now fails with a controlled immutable-content
  error rather than a null-pointer failure. Prompt-audio listeners are removed
  between retries so repeated playback failures cannot accumulate callbacks.
- Browser-cancelled material range requests are treated as normal stream aborts
  while unrelated I/O failures still propagate.

Validation status at this checkpoint:

- Earlier 13C checkpoint evidence remains historical and was not reused as
  validation for the expanded 13C2 diff.
- `STEP 7/8` implementation and complete diff/static review were completed
  before validation. `STEP 8/8` completed on 2026-07-15 at 08:37
  Asia/Ho_Chi_Minh.
- Final correction-cycle static checks passed: `git diff --check` and all six
  changed-script checks below returned exit code 0:

```text
node --check src/main/resources/static/js/practice/manage-authoring-contract.js
node --check src/main/resources/static/js/practice/manage-draft-preview.js
node --check src/main/resources/static/js/practice/player-speaking.js
node --check src/main/resources/static/js/practice/speaking-preflight.js
node --check src/main/resources/static/js/practice/listening-preflight.js
node --check src/main/resources/static/js/practice/player-exam.js
```

- JDK 17 compile command passed with `BUILD SUCCESS`:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw -DskipTests compile
```

- The focused phase command passed `247/247`, with zero failures, zero errors
  and zero skips:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw '-Dtest=AiQuestionImageResolverTest,ReadingListeningTypedClientContractTest,ReadingListeningTypedExplanationTest,WritingEvaluationClientTest,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingEvaluationApplicationServiceTest,SpeakingEvaluationOrchestratorTest,SpeakingEvaluationPromptBuilderTest,SpeakingPromptRulesTest,PracticeMaterialControllerTest,PracticeFunctionalUiContractTest,PracticeSpeakingMediaUiResourceTest,PracticePhase11AuthoringUiContractTest,PracticeDraftContractServiceTest,PracticeDraftPreviewServiceTest,PracticePublisherServiceTest,PracticeDraftValidatorTest,PracticeServiceTest,PracticeIntegrationTest#speakingAttemptPreflightDiscardsLegacySnapshotMissingDeliveryJson+listeningPreflightUnlocksImmutableAttemptOnlyForCompletedSession' test
```

- The two selected integration journeys passed against the configured local
  MySQL schema at Flyway V27. This is focused route/session evidence, not a clean
  migration gate. No browser QA or real AI provider call was run in this
  checkpoint.
- `13C2_COMPLETE_FOCUSED_PHASE_GATE_GREEN` was justified for commit `eaf55f8`.
  The correction and full-suite evidence below supersedes the statement that a
  full suite has not been run. Overall Phase 13 remains open through 13D-13H.

### 6.7 Post-commit fill-blank correction, full suite and audit disposition

The user explicitly requested a full suite while reviewing the fill-blank
lecturer editor. This is an approved exception to the normal rule that the full
regression suite is consolidated in 13H. It does not remove the need for the
final 13H validation after 13D-13G change the branch again.

Implemented correction units:

- The fill-blank lecturer field is now a visual `contenteditable` composer.
  Canonical blank IDs remain hidden in `q.prompt`; numbered slots, placement
  state and learner-like preview are the only lecturer-facing representation.
  Raw canonical values pasted from old content are displayed as ordinary
  underscores rather than leaking implementation syntax into the UI.
- Excel v2 import converts matching legacy blank markers into the canonical
  internal prompt representation. Listening/Speaking media-readiness findings
  remain warnings during workbook preview so the lecturer can attach governed
  media in the editor; all other blocking draft errors remain blocking.
- The first full-suite run exposed a real published-graph mutation hole: a
  versioned attempt could coexist with Speaking media that still referenced the
  live question graph, allowing republish to reach a database foreign-key
  failure. The production mutation guard now checks live Speaking media under
  the same set lock and fails with the bounded republish/restore message.
- Integration fixtures that create immutable published versions now remove the
  version graph in foreign-key order. This is test cleanup only and does not
  weaken production history retention.

Consolidated validation evidence:

- First full-suite cycle: `1320` tests with one failure and two errors, all in
  `PracticeIntegrationTest`. The complete error set was analyzed before one
  grouped fix pass; no line-by-line test loop was used.
- Final JDK 17 command:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw test
```

- Final result at 2026-07-15 15:17 Asia/Ho_Chi_Minh: `1321/1321`, zero
  failures, zero errors, zero skips, `BUILD SUCCESS`, total time `27:55`.
  `PracticeIntegrationTest` passed `81/81`. The extra test locks the Speaking
  media mutation guard. Runtime used the configured local MySQL/Flyway V27
  schema; this was not a clean migration rehearsal, browser run or provider
  call.

Audit findings were rechecked against the current source and locked contracts:

| Finding | Disposition | Required boundary |
| --- | --- | --- |
| Speaking pronunciation/fluency is evaluated without evaluator access to learner audio | **CONFIRMED, LIVE ROLLOUT BLOCKER** | Current flow is audio -> transcription -> evaluator. The evaluator receives transcript, media metadata and optional question image, but no audio bytes/URL. Pronunciation, intonation, hesitation and pacing are therefore not audio-grounded. Keep live Speaking AI `NO-GO`; before provider rollout/Phase 15 either add authorized provider-specific audio input and calibrate it with real recordings, or mark/exclude audio-only criteria instead of presenting them as measured scores. |
| `textFallbackAnswer` is swallowed | **OUTDATED / NOT REPRODUCED** | `SpeakingEvaluationApplicationService.evaluateTextFallback` builds a fallback transcription and passes both it and the fallback answer into the orchestrator. The remaining rule is that a text fallback cannot produce audio-grounded pronunciation/fluency claims. |
| Speaking transcription query filters legacy ungrouped questions | **CONFIRMED COMPATIBILITY DEBT** | The current implicit join still requires `q.groupId`. New canonical content is grouped. `P15-COMP-21` moves pre-14 only if caller/retained-data evidence proves this path affects a reportable target; otherwise resolve it in post-14 release cleanup before live provider UAT. |
| Writing catch blocks contain unreachable mock fallback | **CONFIRMED DEAD CODE; PROPOSED MOCK BEHAVIOR REJECTED** | The always-true branch makes the later mock call unreachable. Current tests intentionally require `EVALUATION_UNAVAILABLE` and no mock score when a provider/key fails. Remove it in 13H; pre-14 COMP-20 verifies accepted evidence or removes any remaining dead dependency without duplicating correct 13H work. |
| Writing `RestClient` has no explicit connect/read timeout | **CONFIRMED RESILIENCE DEBT** | PRE-10 is 13H implementation that must complete/validate before Phase 14; pre-14 only verifies evidence. Provider/load UAT remains post-14. Do not use mock scoring as timeout fallback. |
| Lecturer dashboard performs per-set/per-user lookups | **CONFIRMED N+1 DEBT** | PRE-11 batches collaborators/users in 13H and is validated before Phase 14; pre-14 does not reimplement it. Performance UAT remains post-14. |
| Cleanup worker can claim the same task on multiple nodes | **CONFIRMED WITH NUANCE** | Local deletion is idempotent and optimistic version checks bound stale status writes, but duplicate external work remains possible. PRE-12 adds atomic claim/lease or skip-locked behavior in 13H and is validated before Phase 14; multi-node/R2 UAT stays post-14. |
| PDF AI generate double-submit and synchronous crop | **CONFIRMED** | PRE-13 adds atomic transition/idempotency and bounded crop semantics in 13F/13H before Phase 14. The pre-14 gate verifies that evidence; provider/load UAT stays post-14. |
| Lecturer receives 403 on learner Speaking playback | **CONFIRMED FUTURE REVIEW DEBT** | Current endpoint is student-only and owner-scoped. Lecturer access must use an explicit reviewer authorization path, not `hasRole('LECTURER')` alone. Complete before manual-grading/reviewer UAT. |
| Collaborator publish is an authorization bypass | **REJECTED AGAINST LOCKED PRODUCT CONTRACT** | The reduced-scope policy explicitly permits an active lecturer collaborator to edit/publish/restore/material while owner lock is off. Changing this requires a product-policy decision, not a security hotfix. |
| MCQ breaks above four options | **OUTDATED STATIC FINDING** | Current player iterates dynamic `optionRows()` and has no fixed four-option cap. Responsive browser evidence is still required in 13H. |
| Duplicate skill labels in result templates | **CONFIRMED LOW-SEVERITY UI DEBT** | Remove the duplicate dynamic label during 13D result work or the 13H UI pass, then cover both result templates. |
| Speaking local preflight is disabled | **OUTDATED / FIXED IN 13C2** | Local sample recording works independently from the disabled production upload gate. Production upload/playback remains gated and is not claimed live-ready. |

Runtime-only evidence still required includes actual provider latency and audio
capability, FFmpeg/ffprobe availability, multipart limits, PDF memory under
concurrency, browser/device recording/playback, responsive/a11y and clean
migration rehearsal. These are not implied by the green Maven suite.

### 6.8 Post-commit pre-13D result and explanation audit

The 13C2 correction is committed and pushed as `c3ba3a9`. At audit start, the
worktree was clean apart from the pre-existing untracked
`.tmp-ksh-audio-generator.html` and `openspec-temp/`, neither of which belongs
to the Practice phase commit. This status-document update is the only later
tracked diff.

Static result-route review found that 13D must begin with a contract correction,
not only template styling:

- the live controller still splits Reading/Listening into `rl-result.html` and
  Writing/Speaking into `result.html`;
- the unused `PracticeAttemptResultView`, `SectionResultRow` and four
  `templates/practice/result/*.html` fragments are not wired to a runtime result
  shell, so they are not an authoritative unified implementation;
- current result DTOs do not keep score, scale, level, completion, timing,
  feedback availability and result state as separate fields;
- Reading/Listening aggregation collapses partial, unanswered and pending rows
  into incorrect counts instead of preserving denominator-aware states;
- active result templates duplicate the skill label and always present a
  congratulatory completion state; stale fragments also derive display values
  by arbitrary arithmetic. Phase 13D must render only persisted evidence and
  must never manufacture rubric values or convert an unavailable score to zero.

Reading/Listening explanation cache behavior was also rechecked:

- a cache hit for the same question/question-version, canonical content,
  answer/stimulus hashes, model, prompt, schema and language avoids another
  provider call and is shared across learners;
- duplicated content under a different question or question-version identity
  has a different key and may call the provider again;
- provider failure/fallback output is not persisted as a successful cache hit;
- the current per-key synchronization is JVM-local and does not by itself stop
  duplicate provider work across multiple application nodes;
- most importantly, the current Reading/Listening result GET path calls
  `getOrCreateExplanation()` on cache miss. This violates the locked rule that
  refresh/player/result GET requests are read-only. Phase 13D must stop provider
  creation from GET. Per the later user scope decision in Section 6.9, the
  publish-time generation, durable retry and idempotency foundation now belongs
  to 13D; 13E/13F may consume or present that lifecycle but must not replace it.

One deterministic local-only Speaking result fixture was created for UI review:

- attempt `687`, learner `4`, set/test/section `4`, immutable version chain
  `4/4/4/4`;
- state `GRADED` / `SUCCEEDED`, score `80%`, source `MOCK`, six canonical rubric
  rows and an explicit warning that fluency/pronunciation are not audio-grounded;
- marker `LOCAL_PHASE13D_FIXTURE`; no Flyway seed, production source change,
  learner audio, provider request or automated-test evidence is implied;
- learner URL: `/practice/attempts/687/result` while authenticated as user `4`;
- local DEV seed login for that fixture is `student@ksh.edu.vn` / `password`.
  This is a development credential already defined by V5/V6, not a production
  credential or a new secret introduced by this document.

The pre-13D implementation boundary is now:

1. choose one canonical overview DTO/assembler and remove or explicitly retire
   the unwired alternative result architecture;
2. map attempt, analysis and per-question states into honest overview states;
3. preserve objective correct/partial/incorrect/not-answered/pending/unscorable
   counts with a visible denominator;
4. keep score, scale, level, completion, timing and feedback availability
   independent, including null score semantics for unavailable Writing/Speaking;
5. make all result GET routes read-only and move explanation creation/retry to
   an explicit command boundary;
6. replace fabricated result values and unconditional celebration with
   skill-native, state-aware presentation.

This was a static/database-fixture audit only. No compile, test, build, provider
call or browser/device gate was run. The unauthenticated local route check only
confirmed that ownership remains protected by login; it is not result-page QA.

### 6.9 Exact Phase 13D approval scope

Status: `COMPLETE_FOCUSED_AND_AUTHENTICATED_ROUTE_GATE_GREEN`; committed/pushed
at `98153ac`.

The user gave an explicit implementation GO. The approved implementation passed
its consolidated focused gate; the two environment-blocked authenticated routes
were subsequently closed `2/2` against a disposable fresh V44 schema. This
section remains the historical acceptance lock and supersedes the earlier
allocation that deferred all explanation generation and retry work to 13E/13F.
Phase 13D owns the minimum complete explanation lifecycle needed to make result
GETs genuinely read-only.

#### 6.9.1 Product and data invariants

1. A Reading/Listening answer explanation is an immutable artifact for a
   published question-version contract. It is independent of learner identity,
   learner answer and learner correctness.
2. Learner-specific result state is a deterministic overlay containing the
   submitted answer, `CORRECT`, `PARTIAL`, `INCORRECT`, `NOT_ANSWERED`,
   `PENDING` or `UNSCORABLE`, and earned/possible points. It must not be part of
   the shared AI prompt or artifact fingerprint.
3. Publishing persists and commits the immutable question graph first. An
   after-commit preparation step then persists durable explanation work, and a
   bounded reconciler closes the commit/event crash gap. Provider I/O never
   runs inside the publish transaction and provider failure never rolls back an
   otherwise valid publication.
4. Player, submit, result overview, result detail and ordinary refresh GETs do
   not call an AI provider or create explanation state.
5. An attempt always reads through its locked `question_version_id`. It never
   falls back to the latest live question, latest published version or another
   version's binding.
6. Old attempts remain byte-for-byte associated with their old question and old
   explanation artifact after a lecturer edits and republishes content.
7. Mock/fallback explanation output is never persisted as `READY`. Missing or
   failed provider evidence is represented honestly as an availability state.

#### 6.9.2 Canonical result contract with three skill-native presenters

Phase 13D must promote the existing `PracticeAttemptResultView`,
`SectionResultRow` and `PracticeService.getAttemptResult()` direction into the
only top-level overview contract instead of adding a fourth result DTO or a
second result service. "Separate result for Writing and Speaking" means
separate, skill-native presentation payloads and wired views under this one
contract; it does not authorize duplicate controller routes, assemblers or
scoring pipelines.

The canonical result envelope must expose independent fields for:

- attempt ID, immutable set/test/section identity, skill and result state;
- nullable score value, earned points, possible points and explicit score
  scale/label;
- optional level label without deriving it from an unrelated percentage;
- completion state and denominator-aware `correct`, `partial`, `incorrect`,
  `notAnswered`, `pending` and `unscorable` counts;
- started/submitted timestamps and persisted/derivable elapsed time only when
  the source values exist;
- feedback availability separately from score availability;
- a discriminated skill payload where exactly one of `objectiveOverview`,
  `writingOverview` or `speakingOverview` is present.

`PracticeController.GET /practice/attempts/{attemptId}/result` must call one
top-level assembler for all four skills and render one shared page frame. That
frame owns navigation, learner/attempt identity, completion/evaluation state,
score availability and actions. It delegates the main content to exactly three
wired presenters:

1. one objective presenter shared by Reading and Listening;
2. one Writing presenter;
3. one Speaking presenter.

Skill payload mappers may exist behind the top-level assembler, but they are
not independent overview services and cannot be called directly by a second
controller path. Skill-specific detail assemblers/templates remain only as
13E infrastructure and must use read-only artifact lookup.

##### 6.9.2.1 Shared frame and semantic visual language

The supplied PREP screenshots are interaction references only. KSH must not
copy PREP branding, certificate artwork, assets, wording, IELTS band labels or
CSS. The result frame remains a light KSH interface and uses stronger hierarchy
and real data instead of decorative certificate claims.

Criterion scales may reuse the following accessible visual tokens:

- red: `Cần cải thiện` / limited evidence-backed performance;
- amber: `Đang phát triển`;
- green or teal: `Tốt`;
- blue: `Rất tốt`;
- neutral gray: `Chưa có dữ liệu`, `Đang chấm` or `Không đủ bằng chứng`.

These colors are presentation tokens, not one global performance taxonomy.
The sample phrases above are non-normative accessibility examples unless the
named, versioned KSH task policy itself emits that descriptor.
Visible chip/segment labels, ordering, denominators and descriptors must come
from the active KSH task-native Korean rubric/construct policy and its stored
evidence contract. PREP-style chips may be learned only as compact
scan/filter/evidence-navigation controls: their counts are exact normalized
evidence-backed findings, never scores, IELTS bands or copied PREP categories.
A transcript-only Speaking result must not create pronunciation, intonation,
rhythm, pause or fluency/acoustic chips from STT text.

Color is never the only signal: every segment includes a text level/status and
an accessible label. Thresholds come from the server-side scoring contract or
persisted level label. The browser must not add random variation to make bars
look richer. Segmented tracks, tabs and badges have stable responsive
dimensions, horizontal overflow or wrapping where needed, sufficient contrast
and no text overlap on mobile or desktop.

The shared frame renders celebration only for a semantically eligible outcome.
A zero, unavailable, failed, pending or unscorable result receives an honest
neutral status rather than `Congratulations` or `Complete` as a success claim.

##### 6.9.2.2 Reading and Listening objective overview

Reading and Listening share one objective presenter. The current information
model is retained but visually refined with clearer score hierarchy, a real
answer-state distribution, denominator-aware question-type rows and distinct
feedback-availability state. It must remove duplicate skill labels, improve
spacing and typography, and use semantic state colors without converting the
page into a decorative certificate.

The presenter renders only canonical `correct`, `partial`, `incorrect`,
`notAnswered`, `pending` and `unscorable` values. A 0-point result remains a
valid submitted result, but it is not congratulated and its denominator is not
collapsed to a binary correct/incorrect total.

##### 6.9.2.3 Writing overview

Writing receives its own presenter. Its first-level task selector is built from
the immutable submitted questions and their actual task type/number/title; it
must not hard-code `Writing Task 1`, `Writing Task 2` or Q1-Q4.

All four Writing questions remain canonical `ESSAY` questions so the existing
AI-scoring pipeline is unchanged. The immutable `essayTaskType` still selects
the Korean scoring contract enforced by `WritingScoringPolicy`:

- Q51/Q52 keep the native six-row, two-blank rubric: content/context,
  grammar/structure and expression/naturalness for each blank. These tasks are
  not forced into four essay criteria.
- Q53/Q54/general essays keep the three current KSH score-bearing criteria:
  `Hoàn thành nhiệm vụ và Nội dung`, `Cấu trúc và Mạch lạc`, and
  `Ngôn ngữ và Biểu đạt`, with their task-native maximum scores.

For long-form Q53/Q54/general essays, the PREP-like four-tab interaction is
adapted into four Korean-writing analysis lenses:

1. `Nhiệm vụ và Nội dung` maps to the current KSH content/task score;
2. `Cấu trúc và Mạch lạc` maps to the current KSH organization score;
3. `Từ vựng và Diễn đạt` is a structured drill-down of the current KSH language
   score;
4. `Ngữ pháp và Độ chính xác` is the other drill-down of that same current KSH
   language score, including grammar and supported spelling/spacing evidence.

The current `Ngôn ngữ và Biểu đạt` score is counted and displayed exactly
once. The two drill-down lenses must not each invent a second score, claim an
official TOPIK weighting or be labeled as IELTS bands. The current `12/9/9`
and `20/15/15` weights remain an unnamed/internal KSH practice policy pending
`P15-PRE-02`; they are not an official TOPIK scoring table. A lens without persisted
structured evidence shows `Chưa có dữ liệu`; it does not synthesize sliders,
descriptors or error counts. Descriptor text, findings and error chips come
only from normalized stored evaluator output and are labeled as Korean-writing
feedback, not `Band descriptors`.

##### 6.9.2.4 Speaking overview

> **Superseded current-runtime note (`2026-07-22`, UX-05):** the text below is
> the approved/original Phase 13D history. It no longer defines current score
> semantics. F06 and UX-03..05 define a transcript-only four-row language
> profile, two null `NOT_SCORABLE` acoustic rows, no `/70` subtotal and no
> holistic or attempt score. Future full scoring requires authorized direct
> audio.

Speaking receives its own presenter and presents one holistic evaluation of
the entire submitted Speaking attempt. The overview must not contain per-question
analysis tabs, repeated per-question rubric panels or a primary call to
"phân tích từng câu". Individual prompts, transcripts, recordings and evidence
remain available in 13E detail.

The holistic presenter aggregates the six existing Korean-speaking criteria:

- `S_CONTENT_TASK_FULFILLMENT`: Nội dung / hoàn thành nhiệm vụ, weight 20;
- `S_GRAMMAR_SENTENCE_CONTROL`: Ngữ pháp / kiểm soát câu, weight 20;
- `S_VOCABULARY_EXPRESSIONS`: Từ vựng / diễn đạt, weight 15;
- `S_COHERENCE_ORGANIZATION`: Mạch lạc / tổ chức, weight 15;
- `S_FLUENCY`: Độ lưu loát, weight 15;
- `S_PRONUNCIATION_DELIVERY`: Phát âm / thể hiện, weight 15.

Aggregation runs server-side from persisted, eligible question evaluations and
the immutable scoring/task weights. The overview carries earned/possible
denominators and evaluation coverage; the browser never averages displayed
percentages. Missing weights, missing required question evaluations or mixed
availability are represented explicitly instead of assuming equal weight or
turning missing values into zero.

The page shows one overall score/state, evaluation coverage, six criterion
summaries, major strengths, major improvement priorities and a concise action
plan derived deterministically from stored structured output. A recording
playlist may identify the submitted segments so the learner can replay them,
but it is media navigation, not per-question analysis.

Current live Speaking evaluation does not receive learner audio in the
evaluator request. Therefore transcript-only fluency/pronunciation output must
be labeled `Bằng chứng hạn chế` or `Tham khảo từ transcript`; it cannot claim
phoneme, individual-sound, rhythm, intonation or delivery measurements as
audio-grounded facts. Unsupported submetrics are unavailable. The existing
live Speaking AI rollout remains `NO-GO`; this result redesign does not silently
promote it.

Across all three presenters, the overview must remove:

- unconditional `Congratulations`/`Complete` claims;
- duplicate skill labels;
- null scores converted to zero;
- hard-coded task/question tabs;
- fabricated rubric scores, percentages, slider variations and fallback prose;
- browser arithmetic that invents values not present in persisted evidence.

#### 6.9.3 Version-independent explanation fingerprint

Create one canonical fingerprint builder. The fingerprint includes the frozen
provider input contract:

- assessment schema version, skill and canonical question type;
- normalized prompt, canonical `question_content_json` and canonical
  `answer_spec_json`;
- group instruction, passage, transcript and other approved stimulus evidence;
- immutable content digests for referenced question image and Listening audio,
  not merely a mutable URL;
- teacher explanation and option-label mode;
- evaluator model, prompt version, response schema version and explanation
  language.

The fingerprint explicitly excludes question ID, question-version ID, attempt
ID, user ID, learner answer, learner correctness, timestamps and mutable display
URLs. Canonical JSON ordering, line-ending normalization and framed hashing are
required so equivalent input produces the same fingerprint.

Invalidation rules are explicit:

- editing only question 1 changes only question 1's fingerprint;
- unchanged questions 2-4 bind to their existing `READY` artifact without a
  provider call;
- changing a shared passage/transcript/image/audio digest invalidates every
  question that consumes that evidence;
- changing answer spec, teacher explanation, model, prompt, schema or language
  intentionally creates a new artifact.

#### 6.9.4 Canonical artifact, binding and task storage

The final runtime model is one path:

- `question_explanation_artifacts`: content-addressed artifact and provider
  contract, with `PENDING`, `READY` or `FAILED` availability;
- `question_version_explanation_bindings`: immutable mapping from one published
  question version to its artifact;
- `question_explanation_generation_tasks`: durable bounded work queue with
  processing/retry/final-failure state, attempt count, next-attempt time and
  bounded error metadata.

Historical 13D design required `question_explanation_cache` data to be migrated
without guessing identity. The migration audit now proves that current
`V37__question_explanation_artifact_lifecycle.sql` did not meet that contract:
it ignored the `question_version_id` added by V34, treated incompatible
question IDs/fingerprints/language as equivalent and then dropped the legacy
table. V37 must not be treated as a safe representative-upgrade path.

Under the guarded rebaseline, the final schema contains the R/L artifact/task
model directly and performs no legacy cache backfill. If any retained/deployed
database obligation exists, the rebaseline stops and a separately reviewed
forward migration must preserve/reconstruct binding identity without guessing.
In neither branch may an old result be rebound to a newer live question.

Database uniqueness must enforce one artifact per fingerprint/provider contract,
one active compatible binding per question-version/language contract and one
active generation task per missing artifact. Binding history is append-only:
superseded rows remain auditable for old attempts and correction history.
JVM-local synchronization is not accepted as the primary duplicate-call guard.

These `question_explanation_*` tables are Reading/Listening-only. Writing cache
storage remains `practice_writing_evaluation_cache` and pre-14 must extend its
identity through the complete versioned `AssessmentPolicyBundle`; the two
lifecycles must not be described as one shared cache.

#### 6.9.5 Publish and republish flow

The required flow is:

1. `PracticePublisherService` validates and publishes the live graph.
2. `PracticePublishedVersionService` creates the complete immutable version
   graph and material references are promoted.
3. After that transaction commits, an event listener reads only the new
   immutable Reading/Listening objective question versions, computes each
   fingerprint and persists bindings plus any required durable tasks in its own
   transaction. It does no network/provider I/O.
4. A bounded scheduled reconciler discovers a committed latest publication or
   historical attempted version that is missing preparation and invokes the
   same idempotent preparation path. This closes the process-crash gap between
   commit and after-commit event delivery without generating duplicate work.
5. Workers can claim only committed durable tasks.
6. For an existing `READY` fingerprint, the new question version is bound to
   the existing artifact and no task/provider call is created.
7. For a new fingerprint, one `PENDING` artifact/binding/task is created.
8. Publish returns success even while explanations are pending. The lecturer
   receives honest reused/queued/failed counts rather than waiting for AI.

For a four-question republish where only question 1 changed, the acceptance
result is exactly one new provider task, three reused artifact bindings, the old
question-1 binding retained for old attempts, and the new question-1 binding
used only by attempts locked to the new published version.

#### 6.9.6 Generation, retry and concurrency flow

One bounded worker consumes committed tasks using database-backed claim/lease or
pessimistic-lock semantics compatible with multiple application nodes. It must
reuse the repository's established task/backoff patterns where practical rather
than adding a second generic scheduler framework.

The worker:

1. claims one bounded batch idempotently;
2. rebuilds `ExplanationContext` from the bound immutable version and authorized
   immutable material evidence;
3. calls the existing typed `ReadingListeningExplanationClient` once;
4. validates the response contract before marking the artifact `READY`;
5. records retryable failure with bounded backoff, then `FAILED` after the
   configured maximum;
6. never stores mock output as successful provider evidence.

Phase 13D also owns one authorized, idempotent, rate-limited retry command for
failed artifacts. 13F may improve operational presentation, filters and bulk
recovery, but it must call this same command/service and must not create another
generation route.

#### 6.9.7 Read-only learner result flow

The result read service resolves:

`attempt lock -> question_version_id -> binding -> artifact`.

It returns the stored artifact only when `READY`; otherwise it returns an
explicit `PENDING`, `FAILED` or `UNAVAILABLE` feedback-availability value. It
then computes the learner overlay deterministically from the immutable answer
spec and submitted answer. There is no provider call, cache write, task creation
or fallback to a newer question version from overview/detail GET.

This separation means the shared explanation can be generated before any
learner attempts the test, while correctness and points remain learner-specific
at result-render time.

#### 6.9.8 Dead-code and parallel-path closure

Phase 13D is incomplete until the superseded paths are removed in the same
implementation unit that introduces their replacement:

- remove every production `getOrCreateExplanation(...)` overload;
- remove the JVM `cacheLocks`, ID/version-based cache-key builders and old
  `upsert`/`upsertVersioned` repository APIs;
- retire the active `QuestionExplanationCache` entity/repository path after its
  migration to artifact/binding/task ownership;
- remove `ReadingListeningMockExplanationService` from production if it has no
  non-fallback caller; tests use mocked providers/fixtures instead;
- remove the R/L-specific overview controller branch and `rl-result.html` once
  the canonical overview route is wired;
- remove the hard-coded/fabricated inline result JavaScript and either delete or
  rebuild the four currently unwired `templates/practice/result/*.html`
  fragments. The final shell must wire exactly three owned overview fragments:
  objective R/L, Writing and an evidence-bounded Speaking profile. No unused fourth fragment or
  duplicate generic result fragment remains;
- rename or constrain the remaining skill-specific result assemblers as
  detail-only so they cannot silently become a second overview implementation;
- update or delete tests that assert removed templates/methods instead of
  retaining compatibility wrappers solely to keep stale tests green.

Before `READY_FOR_PHASE_VALIDATION`, run a static call-site/route/template audit
and demonstrate that exactly one top-level overview assembler, one overview
shell, three wired skill-native presenters, one fingerprint builder, one
artifact read path and one generation command path remain. The presenter
mappers may only be reached through the top-level assembler. Intentional legacy
redirects may remain only when they have a documented external caller and route
test.

#### 6.9.9 Phase 13D acceptance scenarios

The implementation must cover at least:

1. first publish queues each eligible R/L question once;
2. equivalent republish reuses every artifact and makes zero provider calls;
3. editing only q1 queues only q1 while q2-q4 reuse artifacts;
4. shared passage/transcript/media change invalidates all dependent questions;
5. model/prompt/schema/language change intentionally invalidates reuse;
6. two concurrent workers cannot call the provider twice for one artifact;
7. provider failure does not roll back publication and follows bounded retry;
8. repeated overview/detail GET causes zero provider calls and zero writes;
9. old attempt renders old immutable question/binding after republish;
10. new attempt renders the new binding;
11. learner correctness overlay differs per learner while the base artifact is
    shared;
12. Writing/Speaking unavailable or pending scores remain null/unavailable, not
    zero;
13. objective counts preserve partial/not-answered/pending/unscorable states and
    their denominator;
14. malformed or missing artifact JSON is unavailable/failed, never silently
    promoted to `READY`;
15. all four skills use one result route/envelope/shell and exactly one of the
    three skill-native presenters is rendered;
16. a multi-question Speaking attempt renders one transcript-language profile
    with no per-question rubric tabs in overview and no holistic score;
17. Speaking aggregation reports eligible/evaluated coverage per current
    transcript-grounded criterion; missing evaluations are not averaged as zero,
    and the four rows are not summed into a subtotal/denominator;
18. transcript-only Speaking feedback renders Fluency and
    Pronunciation/Delivery as null `NOT_SCORABLE` and never renders unsupported
    audio/phoneme metrics, band or qualitative level;
19. Q53/Q54 Writing renders four Korean-writing analysis lenses while counting
    the current KSH three-criterion score once, with no official-equivalence or IELTS labels or duplicated
    language points;
20. Q51/Q52 remain `ESSAY` questions, retain their task-native two-answer
    scoring rubric and are not forced into the four long-form lenses;
21. Writing task selectors come from immutable task metadata and support the
    actual number of submitted Writing questions without hard-coded Task 1/2;
22. every criterion bar, descriptor, finding and error chip is backed by stored
    structured evidence; absent submetrics render neutral unavailable state;
23. a submitted zero-point Reading/Listening result is not congratulated and
    retains its complete answer-state denominator;
24. semantic scales remain understandable without color, fit mobile/desktop and
    do not overlap at the longest Vietnamese/Korean labels;
25. route/template audit finds one top-level assembler, one shell and exactly
    three called presenter fragments, with no legacy result path left active.

#### 6.9.10 Boundary with later slices

- 13D owns the canonical result envelope/top-level assembler/shared frame, the
  three summary-level skill-native presenters, evidence-bounded Speaking profile aggregation,
  Korean-native Writing overview, artifact lifecycle, publish preparation,
  bounded worker/retry foundation and read-only result contract.
- 13E owns detailed evidence presentation, evidence anchors and explicit visual
  separation of learner answer, official key, teacher explanation and the 13D
  AI artifact. Per-question Speaking analysis, submitted Writing text detail and
  recording/transcript evidence live there. It does not build another
  cache/generator or another overview. After the bounded
  `PHASE_13D_UX_CORRECTION` has consolidated green validation and only with a
  separate explicit Phase 13E GO, 13E replaces the legacy generic parser with
  exactly three detail
  screens/contracts: Objective Reading/Listening, Writing and Speaking. They may
  share visual primitives and one read-only dispatcher, but not one cross-skill
  browser JSON parser/template.
- 13E must prove typed Vietnamese/Korean learner labels and validated evidence
  placement on those screens. The complete versioned construct/diagnostic
  matrix, Korean-SME sign-off and calibration remain `P15-PRE-14`; Phase 13E
  must not claim exhaustive Korean-language coverage or copy PREP/IELTS criteria.
- 13F owns real progress aggregates and richer recovery/operations UX. It reuses
  the 13D status and retry command.
- 13G owns responsive, accessibility and performance sweeps.
- 13H owns final browser/device journeys and post-13D-13G stabilization.
- Speaking multimodal audio-grounded evaluation remains a separate NO-GO debt;
  it is not smuggled into this Reading/Listening result phase.

#### 6.9.11 Locked Phase 13D testing policy

During implementation, Codex may read tests to understand contracts and may
perform static diff reasoning, but must not run unit tests, integration tests,
full suites, Maven/Gradle compile or build, application startup, Docker/frontend
build, project lint or migration tests after individual files/issues/patches.
Implementation units are not validation units.

All approved 13D issues must be implemented, all edited files reviewed and no
file left half-edited before Codex reports `READY_FOR_PHASE_VALIDATION`. Before
running validation, Codex must report:

- completed issue list and changed-file list;
- the exact proposed build/test commands and why they are the smallest set that
  covers the phase;
- whether targeted integration/migration coverage is required by the schema,
  transaction, worker and immutable-version boundaries;
- confirmation that no test/compile/build/lint/startup was run during phase
  implementation.

Only after that report may one consolidated validation run begin, in this order:

1. `git diff --check`;
2. one JDK 17 compile/build;
3. the smallest focused unit/service/controller tests covering the canonical
   overview, fingerprint, artifact binding, preparation, worker/retry and
   read-only result contracts;
4. targeted integration/migration tests because 13D changes schema, publish
   transaction boundaries, immutable version reuse and multi-node task claims.

Planned focused ownership includes the existing `PracticeServiceTest`,
`PracticePublisherServiceTest`, `ReadingListeningExplanationClientTest` and
selected `PracticeIntegrationTest` journeys plus focused new fingerprint,
artifact/preparation, worker, Speaking-attempt aggregation, Korean Writing
rubric mapping and three-presenter route/template tests. Exact selectors are
reported before the gate. A full suite is not run unless the user explicitly
requests it, the change is demonstrably broad enough to require it, the focused
set cannot be bounded, or focused validation has passed and a release
confirmation is separately approved. Browser/device validation remains
consolidated in 13H.

If consolidated validation fails, Codex first analyzes all failures, groups
common causes, makes one concentrated correction pass, reviews the complete
correction diff, and reruns the same validation set once. It must never use a
`fix one line -> test -> fix one line -> test` cycle. Any proposed mid-phase
exception requires advance notice of the exact check, why it cannot wait and
its expected resource scope.

A mid-phase check is permitted only when a change may break syntax badly enough
to block the rest of the phase, an irreversible migration assumption must be
verified, a technical assumption determines the whole implementation direction,
or the user explicitly requests the check immediately. Compile and unit tests
are not default exceptions. Before using an exception, Codex must state the
exact command/check, why it cannot wait and the expected resource cost.

Individual issues/patches are implementation units; the whole approved 13D
scope is the validation unit. There is no mandatory commit or test after each
implementation unit. After consolidated validation, Codex must report:

1. the phase validated;
2. every issue included in that phase;
3. every build/test command that ran;
4. the result of each command;
5. all failures found;
6. each grouped correction pass performed;
7. tests not run and the reason they were omitted;
8. whether a full test suite ran; and
9. confirmation that validation started only after the entire phase was
   implemented and reported `READY_FOR_PHASE_VALIDATION`.

#### 6.9.12 Step 6 compatibility inventory boundary

Step 6 is documentation and impact inventory only. Phase 13D does not delete,
disable, guard or rewrite historical Writing fill-blank handling, old Speaking
ESSAY/mixed-feedback handling, no-version attempt fallback, route/type aliases,
legacy import adapters, old detail surfaces, migration history or legacy test
fixtures merely because they are inconsistent with the intended production
seed.

The complete inventory and Phase 15 cleanup/seed order are recorded in
`docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`.
That document separates confirmed removal candidates, active paths requiring
replacement/data review and architecture that must remain. Phase 15 owns one
controlled cleanup batch, its fresh/upgrade database proof, the premium
canonical seed and Manual UAT. The cleanup must happen before seed loading, but
only after environment/deployment obligations and the Phase 13E detail
replacement boundary are verified.

> **Current-source supersession:** the paragraph above records the historical
> Step 6 boundary; it no longer routes every compatibility/correctness item into
> one post-Phase-14 batch. The current workflow and blueprint split it into
> `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` after validated 13E-13H and before
> 14A, then `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F. Only target-stability
> contracts, binding supersession, Writing cache identity, configuration safety
> and the guarded Practice rebaseline/fresh Flyway-Hibernate validation move to
> the first gate. Rebaseline uses a newly named disposable DB, never repair or
> reuse, and stops on any retained/deployed/canonical obligation. Minimal
> technical smoke fixtures precede 14A. Final SME/calibration, direct-audio
> rollout, retained-data cleanup, canonical Vietnamese/Korean UAT seed and
> Manual UAT remain after 14F. This note is roadmap reconciliation only and does
> not authorize either gate while Phase 13E is running.

Completing this inventory does not close or validate Phase 13D. It only allows
the final complete-diff review and the Section 6.9.11
`READY_FOR_PHASE_VALIDATION` report to begin.

## 7. Continuation Prompt For The Next Agent

Start by reading these files in order:

1. `CODEX_PRACTICE_WORKFLOW.md`, especially the latest Phase 13 rows and
   "Current Required Next Action".
2. `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, especially Sections 1,
   2, 6.7, 6.8 and the approved implementation contract in 6.9.
3. `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`, especially the 13D
   result-overview contract.
4. The current `git status --short`; do not stage
   `.tmp-ksh-audio-generator.html` or `openspec-temp/`.
5. The attached audit notes only as supporting evidence. Do not expand scope to
   broad permissions or program/certificate governance.

Historical 13D handoff sequence (completed and superseded as the current
instruction): preserve it as evidence, but do not use it to redirect the active
phase. The current action is the single consolidated Phase 13E validation:
`13E-01..05` are `IMPLEMENTED_PENDING_PHASE_VALIDATION / ACCEPT_STATIC` and
the exact gate is recorded in the 13E live log Section 12.

The completed 13D sequence was:

1. Preserve both the `247/247` focused evidence for `eaf55f8` and the
   `1321/1321` correction-suite evidence committed/pushed in `c3ba3a9`. Do not
   rerun either merely to repeat the same checkpoint.
2. Use local attempt `687` only as a deterministic Speaking result fixture. It
   is mock UI data, not provider, audio or release evidence.
3. The user approved Section 6.9 on 2026-07-15. Phase 13D implementation is
   complete and the supplemental timer, Listening preflight, progress, PDF crop
   and fixed Writing-task corrections are included in the same validation unit.
4. Preserve the completed Step 5 static diff, route and dead-code audit. Step 6
   is inventory-only: use Section 6.9.12 and the linked Phase 15 inventory; do
   not delete compatibility behavior during 13D.
5. Review the complete Phase 13D diff, then follow Section 6.9.11 exactly:
   report `READY_FOR_PHASE_VALIDATION`, the
   completed issues, changed files and proposed single consolidated validation
   before running any final check. The user explicitly requested a full suite
   in that final validation.
6. Do not reopen broad governance/permission scope or mix 13E-13H/Phase 15 debt
   into the historical 13D implementation. Later slices consume the 13D
   artifact lifecycle rather than replacing it.
7. Keep browser/device, responsive, accessibility, real provider and complete
   learner-journey validation in 13H. Run the final post-13D-13G regression
   suite there. Do not describe Phase 13 itself as closed before that gate.

## 8. Phase closure rule

Phase 13 cannot be marked complete after visual implementation alone. 13H must
run a stabilization pass covering dead code, controller-template route wiring,
learner authorization, answer leakage, UTF-8, responsive/a11y behavior,
focused/full automated tests and browser journeys. Browser QA is run once at
that closure gate rather than after every implementation slice. Any deferred provider,
Cloudflare R2 or production Speaking-media item must be recorded explicitly and
must not be described as green evidence.
