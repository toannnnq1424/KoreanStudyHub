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
| 13B detail/preflight/version lock | `COMPLETE_FOCUSED_GATE_GREEN` | Set/test detail, immutable attempt routing, canonical Speaking delivery, full-screen microphone preflight and immutable Listening speaker preflight passed the consolidated 13C2 focused validation. Browser/device QA remains in 13H |
| 13C skill-native players | `13C2_FULL_SUITE_GREEN_PHASE_13_OPEN` | Speaking, Writing, adaptive Reading/Listening, structured media, image-aware AI and visual fill-blank authoring are implemented. The post-commit correction passed the complete 1321-test suite; Phase 13 is not closed and final browser/device plus post-13D-13G closure validation remains in 13H |
| 13D result overview | `NOT_STARTED` | Keep score, scale, completion, timing and feedback availability separate |
| 13E result evidence | `NOT_STARTED` | Separate official key, teacher explanation and AI artifact |
| 13F progress/recovery | `NOT_STARTED` | Real aggregates only; no decorative percentages |
| 13G responsive/a11y/performance | `NOT_STARTED` | Includes UTF-8, icon, reduced-motion and large-catalog sweeps |
| 13H stabilization gate | `NOT_STARTED` | Static/dead-route audit, focused/full tests and browser journeys before closure |

After each completed slice, this ledger and the detailed evidence section must
be updated before starting the next slice.

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
  can be published; the learner must finish playback and confirm audibility.
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
| Speaking transcription query filters legacy ungrouped questions | **CONFIRMED COMPATIBILITY DEBT** | The current implicit join still requires `q.groupId`. New canonical content is grouped, but the reduced-scope contract preserves bounded legacy ungrouped compatibility. Correct before enabling live Speaking AI or, at latest, before Phase 15 UAT. |
| Writing catch blocks contain unreachable mock fallback | **CONFIRMED DEAD CODE; PROPOSED MOCK BEHAVIOR REJECTED** | The always-true branch makes the later mock call unreachable. Current tests intentionally require `EVALUATION_UNAVAILABLE` and no mock score when a provider/key fails; silently grading with fake scores would violate the fail-closed AI contract. Remove the dead branch in the 13H dead-code pass while preserving provider-unavailable semantics. |
| Writing `RestClient` has no explicit connect/read timeout | **CONFIRMED RESILIENCE DEBT** | Add bounded, configurable timeouts and timeout tests before AI release/Phase 15 UAT. Do not treat mock scoring as the timeout fallback. |
| Lecturer dashboard performs per-set/per-user lookups | **CONFIRMED N+1 DEBT** | Batch collaborators and users with `IN` queries before Phase 15 performance UAT; retain current authorization filtering. |
| Cleanup worker can claim the same task on multiple nodes | **CONFIRMED WITH NUANCE** | Local deletion is idempotent and optimistic version checks bound stale status writes, but duplicate external deletion/work remains possible. Add atomic claim/lease or skip-locked behavior before multi-node/R2 rollout and Phase 15 operational UAT. |
| PDF AI generate double-submit and synchronous crop | **CONFIRMED** | Add an atomic session status transition/idempotency key for generation. Design crop offloading with transaction/context/error semantics; a bare `@Async` annotation is not an adequate fix. Complete before provider/load UAT. |
| Lecturer receives 403 on learner Speaking playback | **CONFIRMED FUTURE REVIEW DEBT** | Current endpoint is student-only and owner-scoped. Lecturer access must use an explicit reviewer authorization path, not `hasRole('LECTURER')` alone. Complete before manual-grading/reviewer UAT. |
| Collaborator publish is an authorization bypass | **REJECTED AGAINST LOCKED PRODUCT CONTRACT** | The reduced-scope policy explicitly permits an active lecturer collaborator to edit/publish/restore/material while owner lock is off. Changing this requires a product-policy decision, not a security hotfix. |
| MCQ breaks above four options | **OUTDATED STATIC FINDING** | Current player iterates dynamic `optionRows()` and has no fixed four-option cap. Responsive browser evidence is still required in 13H. |
| Duplicate skill labels in result templates | **CONFIRMED LOW-SEVERITY UI DEBT** | Remove the duplicate dynamic label during 13D result work or the 13H UI pass, then cover both result templates. |
| Speaking local preflight is disabled | **OUTDATED / FIXED IN 13C2** | Local sample recording works independently from the disabled production upload gate. Production upload/playback remains gated and is not claimed live-ready. |

Runtime-only evidence still required includes actual provider latency and audio
capability, FFmpeg/ffprobe availability, multipart limits, PDF memory under
concurrency, browser/device recording/playback, responsive/a11y and clean
migration rehearsal. These are not implied by the green Maven suite.

## 7. Continuation Prompt For The Next Agent

Start by reading these files in order:

1. `CODEX_PRACTICE_WORKFLOW.md`, especially the latest Phase 13 rows and
   "Current Required Next Action".
2. `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, especially Sections 1,
   2 and 6.
3. The current `git status --short`; do not stage `openspec-temp/`.
4. The attached audit notes only as supporting evidence. Do not expand scope to
   broad permissions, result pages or program/certificate governance.

Continue with this sequence:

1. Preserve both the `247/247` focused evidence for commit `eaf55f8` and the
   post-commit `1321/1321` correction-suite evidence. Do not rerun either merely
   to repeat the same checkpoint.
2. The fill-blank/full-suite correction is currently an uncommitted diff based
   on `eaf55f8`. Do not stage `.tmp-ksh-audio-generator.html` or
   `openspec-temp/` with it.
3. Do not start 13D or reopen broad governance/permission scope without a
   separate user instruction. The audit table above is a disposition ledger,
   not approval to mix every debt into this correction.
4. Keep browser/device, responsive, accessibility, real provider and complete
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
