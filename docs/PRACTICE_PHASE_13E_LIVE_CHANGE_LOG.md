# Practice Phase 13E Live Change Log

> **Current-source note (`2026-07-22`, UX-05):** Phase 13E remains unopened.
> Historical PRE-13E findings below that say “six criteria” or “holistic” are
> superseded for current runtime by Phase 13D F06/UX-03..05: transcript-only
> four-row language profile, two acoustic `NOT_SCORABLE` rows, no subtotal or
> holistic/attempt score. Phase 13E must consume that contract rather than
> revive the old wording.

Status: `PREPARATION_COMPLETE_WAITING_EXPLICIT_GO`

Started: 2026-07-17

Branch: `feature/practice-reduce-scope`

This is the mandatory live record for Phase 13E and its pre-implementation
gate. It must be read before each work session, updated as requirements or
findings occur, and reread in full before the phase validation step.

## 1. Locked Phase Boundary

Phase 13E owns evidence-based Result Detail presentation. It must visually and
semantically separate:

- the learner answer;
- the immutable official answer/key where applicable;
- lecturer-authored explanation;
- the immutable Reading/Listening AI explanation artifact created by 13D;
- Writing submitted text, structured rubric evidence, corrections, rewrites
  and evaluator-generated sample outputs;
- Speaking per-question transcript/recording evidence and structured feedback.

For both Writing and Speaking, the AI feedback surface is locked to exactly
four tabs: `OVERVIEW`, `STRENGTHS`, `NEEDS_IMPROVEMENT` and
`UPGRADED_ANSWER`. Any lecturer-authored reference answer is immutable source
content rendered in a separate panel outside those tabs. An evaluator-generated
sample or sample response remains an AI artifact and must never be labelled,
styled or persisted as the lecturer reference.

Phase 13E must reuse the 13D artifact/binding/task lifecycle. It must not add a
second cache, generator, worker, retry pipeline or overview assembler.

User direction added `2026-07-22`: after the bounded
`PHASE_13D_UX_CORRECTION` has consolidated green validation and only after a
separate explicit Phase 13E GO, replace the legacy detail surfaces with exactly
three screen contracts: Objective Reading/Listening Detail, Writing Detail and
Speaking Detail. A canonical read-only route/assembler may dispatch among them,
and visual primitives may be shared, but there must be no generic
Writing/Speaking/R/L browser-side JSON parser or one template with cross-skill
placeholder tabs.

Validation note added `2026-07-22`: the correction currently has
`FOCUSED_NON_DB_GATE_GREEN_WITH_2_ENVIRONMENT_BLOCKED_INTEGRATION_CASES`, not
an unqualified complete gate. The two authenticated Result Detail route cases
must run on a fresh disposable current schema, or the user must explicitly
accept that debt, before this prerequisite is treated as closed. The stable
`ksh_phase13e_result_ui` fixture database must not be repaired or wiped as a
shortcut.

User direction added `2026-07-22` (chip reminder): Result and Result Detail may
learn PREP's compact category-chip hierarchy, counts and evidence navigation,
but only as an interaction pattern. KSH chip labels, order, applicability,
parent criterion and any score-bearing label/denominator/descriptor come from a
named versioned Korean task/rubric/construct policy; chip counts are exact
backend-validator-accepted normalized findings with evidence and never IELTS
bands, scores or copied PREP taxonomy. Transcript-only Speaking must not expose
acoustic chips inferred from STT. Phase 13E supplies typed Vietnamese/Korean UI
proof only; the complete construct matrix, Korean-SME sign-off and calibration
remain `P15-PRE-14`. Phase 13E remains unopened; this is a locked acceptance
input, not an authorization to implement the three detail screens now.

## 2. User Requirements Received During Preparation

### 2026-07-17 - branch and review-fixture policy

Request:

- continue on `feature/practice-reduce-scope`;
- commit/push only on that branch;
- do not merge main again unless explicitly requested;
- summarize remaining Phase 13 slices;
- create sample Result and Result Detail data for all four skills and provide
  URLs for PREP UI/UX comparison;
- add a dedicated live documentation rule from Phase 13E onward.

Decision:

- keep this work as a pre-13E preparation unit;
- use deterministic local fixture IDs and an idempotent local-only seed;
- keep final browser/device closure in 13H.

### 2026-07-17 - architecture artifact prerequisite

Request:

- read the supplied Draw.io XML and Use Case examples;
- read current code and approved future plans;
- split `/practice` into smaller feature/capability branches;
- generate formal Use Case documentation;
- generate one class diagram and multiple sequence diagrams per capability;
- use the supplied Draw.io XML only as a structural/style sample and model only
  the `/practice` feature; a fixed 52-page output is not a user requirement or
  an acceptance gate;
- complete this package before Phase 13E implementation.

Decision:

The architecture pack will use these ten bounded capabilities:

1. learner catalog, access, start/resume and immutable attempt lock;
2. lecturer manual authoring, validation, publication and revision;
3. Excel template, preview and deterministic draft import;
4. PDF region workspace, crop/evidence selection and AI-assisted draft import;
5. skill-native player, autosave/submit and attempt lifecycle;
6. Reading/Listening immutable explanation preparation, reuse and retry;
7. Writing AI evaluation and Korean rubric scoring;
8. Speaking private media, transcription and AI evaluation;
9. result overview and evidence-based result detail;
10. learner progress aggregation and operational recovery.

The current generator may lay these diagrams out across many Draw.io pages for
editability, but page count is derived from the capability model and is not a
product requirement. Acceptance is based on Practice-only scope, meaningful
capability decomposition and traceability, not on producing 52 pages.

Deliverables:

- `docs/architecture/practice/KSH_PRACTICE_USE_CASE_SPECIFICATIONS.docx`;
- `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE.drawio.xml`;
- rendered diagram previews for static visual review;
- a Markdown manifest mapping every diagram to current or planned code.

### 2026-07-17 - post-prerequisite Listening preflight correction

Request:

- after the seven current preparation steps, investigate the Listening flow
  where pressing the sound-check/start action does not navigate into the test;
- determine whether the fixture lacks playable audio or the preflight
  state/navigation is defective;
- write the correction plan before changing production code.

Decision:

- add a separate Step 8 after the architecture, fixture and URL review steps;
- reproduce the issue with the deterministic Listening fixture and inspect the
  delivered audio URL, browser playback event, button enablement and target
  route independently;
- record the root cause, correction boundary and acceptance cases before
  implementation;
- do not hide the defect by bypassing the sound check or requiring playback to
  reach the end. A successful audible playback start is sufficient evidence
  for the learner to continue.

### 2026-07-17 - final Jira Task, Bug and Sub-task synchronization

Request:

- add a final step against the `SCRUM` Jira backlog after the Listening
  correction;
- record implementation tasks, discovered bugs and their fixes as Tasks, Bugs
  and Sub-tasks, primarily in Scrum 3 and Scrum 4;
- reconstruct earlier work from the repository Markdown progress records.

Decision:

- add Jira synchronization as Step 9, after Step 8 has implementation and
  evidence to report;
- query the existing backlog and sprint metadata before creating anything, so
  duplicate issues and invalid parent/child relationships are avoided;
- classify each record from actual scope and evidence, then use Scrum 3 or
  Scrum 4 only where the real delivery boundary supports it;
- keep this Jira batch strictly inside module `/practice`; architecture records
  must describe the capability-based Use Case, class-diagram and
  sequence-diagram deliverables, while Flashcard, lesson, iConstant and other
  features remain out of scope;
- represent older work as explicitly labelled `retrospective/backfilled`, with
  the actual work date, commit and source Markdown section in the description.
  Do not falsify creation dates, work dates, resolution dates or sprint history.

## 3. Static Findings And Decisions

### PRE13E-F01 - supplied Draw.io source is truncated

Finding:

Both the Downloads file and the untracked repository copy have SHA-256
`e3e6036439a1920d1abb8e9c6c7a4d40b709fd8234afd02949aaa24258be0509`
and size 1,025 bytes. The file declares 84 pages but ends in the middle of an
`mxCell` start tag. `xmllint` reports a premature end of data.

Decision:

- do not overwrite or stage the supplied file;
- use the visible examples only as a structural reference;
- generate a separate valid multi-page Draw.io artifact from audited code;
- record this limitation in the workflow and Phase 13 gate.

### PRE13E-F02 - Flyway V29 failure is incompatible local history

Finding:

The old `ksh_db` contains the previous Practice migration numbering V16-V28.
After integration with main, current Practice migrations start at V25, so the
current `V29__practice_question_writing_task_type.sql` collides with schema
effects already applied under the old numbering. Flyway correctly refuses to
continue.

Decision and change:

- preserve `ksh_db` unchanged;
- create and use isolated local database `ksh_phase13e_result_ui`;
- update ignored `application-local.properties` to target that database;
- confirm clean Flyway V1-V37 with zero failed rows and learner `id=4` present;
- do not run Flyway repair against the incompatible history.

### PRE13E-F03 - local runtime baseline differs from validation baseline

Finding:

The IntelliJ runtime currently listening on port 8080 uses JDK 26.0.1.

Decision:

This process is accepted only for local visual fixture review. Any official
Phase 13E compile/test/build validation must use JDK 17 and the one-pass phase
validation policy.

### PRE13E-F04 - ClassGraph warning under the local JDK 26 runtime

Finding:

The local process reports that `classgraph:4.8.173` calls the terminally
deprecated `sun.misc.Unsafe.invokeCleaner` API. This is a warning emitted by
the JDK 26 visual-review runtime, not the application startup failure and not
evidence of a Practice functional defect.

Decision:

- record the warning without changing dependencies inside the pre-13E unit;
- keep JDK 17 as the official build/test baseline;
- revisit the dependency only if the warning reproduces on the supported
  baseline, becomes an error, or a separately approved dependency-maintenance
  slice is opened.

### PRE13E-F05 - generated DOCX list markers had no item text

Finding:

The first DOCX render showed numbering/bullet markers but omitted the actual
Precondition, Postcondition, Normal Flow, Alternative Flow, Business Rule and
System Message text. Numbering also continued across Use Cases.

Decision and change:

- replace inherited/list-style assumptions with explicit OOXML numbering and
  bullet definitions in the generator;
- write each item as a real paragraph and create a fresh decimal numbering
  instance for each Use Case normal flow;
- regenerate and visually inspect all 33 pages. All item text is now present,
  normal flows restart at `1`, and every Use Case stays on one page.

### PRE13E-F06 - capability overview labels overlapped

Finding:

The first diagram preview allowed the long `RES` purpose to overlap its status,
while several capability titles sat too close to their box boundaries.

Decision and change:

- increase capability-box height and vertical row spacing in both Draw.io source
  and QA preview;
- wrap titles with a smaller stable label font and reserve a fixed status row;
- confirm the regenerated overview has no overlap or text outside its box.

### PRE13E-F07 - Draw.io page count is not an acceptance requirement

Finding:

The generated layout was described as a 52-page deliverable, which could imply
that the user requested a fixed page count.

Decision and change:

- limit all artifact content to module `/practice` and its ten bounded
  capabilities;
- derive the current page count from the capability/Use Case model rather than
  treating `52` as a requirement;
- state explicitly in the DOCX, manifest and phase log that acceptance is based
  on Practice-only decomposition and traceability, not page count.

### PRE13E-F08 - DOCX QA used the wrong Use Case identifier pattern

Finding:

The first structural QA commands searched for identifiers shaped like
`CAT-UC-01`. The generated specification consistently uses the project
convention `UC-CAT-01`, so the check incorrectly reported zero Use Cases even
though the rendered document contained all entries.

Decision and verification:

- keep the artifact convention `UC-{CAPABILITY}-{NUMBER}` unchanged;
- inspect semantic Word paragraph/table text rather than relying on raw ZIP XML
  runs;
- rerun the corrected check and confirm 30 unique Practice Use Case identifiers
  across the ten capabilities.

### PRE13E-F09 - four-skill result fixtures need an isolated, repeatable loader

Finding:

The clean V1-V37 local schema contained the canonical Practice seed graph but
no stable graded attempts covering all four Result presenters. Reading and
Listening also needed READY immutable explanation artifacts so browser review
would never create provider work.

Decision, change and verification:

- add `scripts/dev/practice-phase13e-result-fixtures.sql` as a local-only,
  explicitly schema-scoped and idempotent loader;
- add attempts `13001` Reading, `13002` Listening, `13003` Writing and `13004`
  Speaking for learner `id=4` with complete immutable version locks;
- bind READY Vietnamese explanation artifacts to Reading/Listening question
  versions `1`, `2` and `3`;
- preserve the historical Korean Writing rubric shape and historical
  `speaking_ai_v1` fixture payload for reproducible baseline comparison;
- execute the loader twice successfully and confirm the same four rows,
  valid answer/feedback JSON and valid explanation JSON after both runs;
- document the eight stable authenticated review URLs in
  `docs/architecture/practice/PRACTICE_PHASE_13E_RESULT_FIXTURES.md`.

This proves fixture repeatability only. Browser/UI acceptance remains Step 7.

> **Current-runtime supersession:** the `13004` fixture statement above records
> the old baseline only. Phase 13D UX-03..05 now requires four trusted
> transcript-language score rows, two null acoustic `NOT_SCORABLE` rows and no
> subtotal/holistic/attempt score. Phase 13E must not reinterpret the historical
> fixture as current scoring authority.

### PRE13E-F10 - legacy Result Detail is not skill-native or fully semantic

Finding:

Authenticated Step 7 review confirmed that the Reading detail renders both
questions, learner/correct-answer states and READY explanations correctly.
However, the legacy detail shell has no `<main>` landmark, exposes an empty
level-four heading, and labels the Listening fixture `13002` as `읽기 (Đọc)`.
The Listening question and transcript are present, so this is a presentation
classification defect rather than missing attempt data.

Decision:

- keep the fixture and immutable explanation data unchanged;
- record the wrong skill label and semantic landmark/heading debt as explicit
  Phase 13E Result Detail acceptance criteria;
- do not patch the superseded detail shell during the pre-13E preparation
  unit;
- keep the later Step 8 Listening preflight navigation defect separate from
  this Result Detail presentation defect.

### PRE13E-F11 - Writing detail changes the official Q53 denominators

Finding:

Attempt `13003` overview correctly renders the current Q53 rubric as
`10/12`, `7/9` and `7/9`, totaling `24/30`. The legacy detail page renders the
same persisted rubric rows as `10/10`, `7/10` and `7/10`. This silently changes
the maximum for every criterion even though the total remains `24/30`.

Decision:

- treat the persisted rubric maxima and immutable assessment contract as the
  single source of truth;
- require Phase 13E detail to render `12/9/9` for Q53 and never normalize
  criterion rows to ten;
- keep the fixture unchanged because it exposes the defect deterministically;
- add an overview/detail denominator-consistency scenario to the 13E focused
  tests and Manual UAT evidence.

### PRE13E-F12 - legacy Writing tabs expose incomplete ARIA and cross-skill debt

Finding:

All five Writing detail tabs switch visible content correctly, but the elements
declared as `role="tab"` have no `aria-selected` or `aria-controls`, and their
panels have no `role="tabpanel"`. The same Writing aside also retains hidden
English placeholder panels for `Fluency` and `Pronunciation & Delivery`, which
belong to Speaking rather than the Korean Writing presentation.

Decision:

- Phase 13E must ship a complete tab/tabpanel accessibility contract and
  remove cross-skill placeholder panels from the active Writing presenter;
- preserve only evidence-backed Korean Writing lenses and task-native KSH
  rubric rows from the named versioned policy;
- leave broader historical template/dead-code deletion aligned with the
  existing Phase 15 compatibility cleanup inventory when 13E no longer owns
  the active surface.

### PRE13E-F13 - Speaking detail drops one criterion and leaks Writing labels

Finding:

Attempt `13004` overview correctly aggregates all six Korean Speaking criteria.
The legacy detail renders the six score rows in its first tab, but its criterion
tab strip has only five entries and omits `S_COHERENCE_ORGANIZATION`. Strength
and improvement categories backed by Speaking criterion IDs fall back to
`Khác 1`. The same screen also exposes English criterion titles and the Writing
label `Bài viết nâng cấp` inside a Speaking journey.

Decision:

- require Phase 13E Speaking detail to preserve all six criteria end to end,
  including a dedicated coherence/organization evidence view;
- map criterion IDs to Korean-native Vietnamese labels instead of raw IDs,
  English IELTS labels or `Khác` fallback categories;
- use Speaking-specific transcript and upgraded-response copy; if an
  evaluator-generated sample response is retained, identify it only as an AI
  artifact and never as the immutable lecturer reference;
- keep detail content as supporting evidence, never a second attempt score.

> **Superseded scoring wording:** “all six” here now means preserve all six
> criterion identities/states end to end, not six numeric scores. Current
> transcript-only Speaking has four numeric language rows, two null acoustic
> `NOT_SCORABLE` rows and no subtotal, aggregate, holistic or attempt score. The
> historical “keep the overview holistic” decision is replaced by the Phase 13D
> evidence-honest profile.

### PRE13E-F14 - fixed Result Detail footer obscures the final mobile action

Finding:

At the available 480-pixel mobile viewport, all eight fixture routes avoid
horizontal page overflow and text clipping. The Reading detail footer is fixed
at the bottom with a height of 70 pixels, while the document/body reserves zero
bottom padding. At maximum scroll the question aside still ends at the viewport
bottom, leaving an unscrollable 70-pixel overlap; the final `Giải thích đáp án`
button remains partially behind the footer.

Decision:

- require the Phase 13E detail shell to reserve bottom space at least equal to
  the sticky/fixed navigator including safe-area inset;
- verify the final question, explanation toggle and expanded explanation can
  all scroll completely above the navigator at mobile widths;
- retain the no-horizontal-overflow result as a passing responsive baseline,
  but do not mark the detail route responsive-ready until the footer overlap is
  fixed.

### PRE13E-F15 - canonical Listening seed loops silently before preflight

Status: `RESOLVED_STEP_8`

Finding:

Authenticated browser reproduction from `/practice/sets/2/tests/2` confirmed
that `Kiểm tra loa để bắt đầu` navigates to the correct preflight URL and is
immediately redirected back to Test Detail. The canonical Listening section has
neither `delivery_json` nor question-group audio, so the service correctly
rejects it. The controller adds a flash error, but the Test Detail page does not
render that message, making the failed closed transition look like a dead link.
The browser never reaches `listening-preflight.js`; waiting for `ended` is not
involved in this reproduction.

Decision:

- correct the canonical seed with a deterministic audible check asset and a
  new forward-only migration; do not modify the applied V25 migration;
- allow only the exact bundled seed check-audio path in addition to canonical
  `/practice/materials/{id}/content` references, without broadening arbitrary
  URL trust for lecturer-authored content;
- preserve publication validation for every newly authored Listening section;
- make the preflight failure message visible on the return surface;
- then run a real browser journey proving `playing` unlocks confirmation and
  the POST reaches the player in the same session.

### PRE13E-F16 - split Result Detail into exactly three screens

Status: `REQUIREMENT_LOCKED_WAITING_PHASE_13E_GO`

Decision:

- `OBJECTIVE_DETAIL` owns both Reading and Listening, with discriminated
  `SINGLE_CHOICE`, `FILL_BLANK` and `TRUE_FALSE_NOT_GIVEN` payloads;
- `WRITING_DETAIL` owns Q51-Q54/GENERAL learner text, task-native rubric,
  findings, corrections and upgraded answer;
- `SPEAKING_DETAIL` owns recording/transcript, capability/evidence provenance,
  findings and upgraded response without reviving acoustic scores from text;
- both `WRITING_DETAIL` and `SPEAKING_DETAIL` expose exactly four AI feedback
  tabs: `OVERVIEW`, `STRENGTHS`, `NEEDS_IMPROVEMENT` and `UPGRADED_ANSWER`;
  the immutable lecturer reference is a separate panel outside the tablist;
  evaluator-generated samples remain AI artifacts and cannot substitute for or
  be presented as lecturer-authored reference content;
- each screen gets a distinct presenter/DTO/template/test contract. Shared
  shell components are allowed, but the current generic parser is not;
- implementation starts only after the bounded Phase 13D UX correction has a
  green consolidated validation and the user gives explicit Phase 13E GO.

### Step 7 authenticated browser baseline verdict

Evidence:

- all eight stable Result and Result Detail URLs for attempts `13001..13004`
  loaded while authenticated as the local learner, without a redirect, HTTP
  500 response or browser console warning/error;
- Reading and Listening Result overviews render objective score and
  correct/incorrect counts; Writing renders its persisted Korean Q53 rubric;
  Speaking historically rendered a holistic six-criterion Korean evaluation.
  That Speaking observation is visual-baseline evidence only and is superseded
  by the current four transcript-row/two `NOT_SCORABLE`/no-holistic contract;
- Reading and Listening Detail render learner answer, official answer and the
  READY immutable explanation artifact without calling an AI provider on GET;
- at the available 480-pixel mobile CSS viewport, all eight routes avoid
  horizontal page overflow and detected text clipping;
- the Retina in-app screenshot backend duplicates page pixels outside the real
  CSS viewport. Visual conclusions were therefore checked against DOM geometry,
  computed styles and a crop of the real viewport tile; duplicated/black pixels
  outside that tile are tool artifacts, not application findings.

Verdict:

- the four deterministic fixtures and eight URLs are accepted as the Phase 13E
  visual-comparison baseline;
- the four skill-specific Result overview screenshots are retained as the
  historical comparison baseline. Current Phase 13D UX-01..05—not the old
  Speaking score semantics—owns the overview contract;
- the legacy Result Detail shell is intentionally **not** accepted as 13E-ready;
  findings `PRE13E-F10..F14` are mandatory Phase 13E acceptance inputs;
- Step 7 is complete. No production Result code was changed during this review.

### Step 8 Listening preflight correction plan

This plan was written before browser reproduction or production edits.

Observed static evidence:

- `listening-preflight.js` already treats the media `playing` event as
  successful playback and does not wait for `ended`;
- both new-attempt and resumed-attempt POST handlers mark the server session
  complete and redirect to the attempt player;
- the canonical Listening seed at set `2`, test `2`, section `2` has
  `delivery_json = NULL`, no question-group audio fallback and no material
  reference, so the preflight GET cannot render a playable sample;
- the existing integration test proves the server transition only with a
  synthetic `/practice/materials/12/content` reference; it does not prove that
  the delivered seed journey contains media or that a browser can play it.

Hypotheses to verify independently:

1. the visible `Kiểm tra loa để bắt đầu` action for the canonical Listening
   seed redirects back to Test Detail because the seed has no valid audio;
2. a real playable check-audio response fires `playing`, immediately enables
   explicit confirmation and then enables the continue submit action;
3. after confirmation, the POST keeps the same session and reaches the
   Listening player instead of looping through attempt preflight.

Correction boundary:

- provide one deterministic, audible and stable check-audio reference for the
  canonical Listening seed without rewriting an applied Flyway migration;
- keep new lecturer-authored Listening publication strict: it must still
  provide a validated immutable check-audio reference;
- do not broaden arbitrary media URL trust or bypass the preflight;
- keep the explicit learner confirmation, but unlock it as soon as audible
  playback starts;
- add focused contracts for seed media delivery, immediate `playing` unlock and
  the completed-session redirect.

Acceptance cases:

- seed Test Detail -> Listening preflight renders a playable sample;
- the confirmation becomes available on `playing`, before `ended`;
- checked confirmation enables `Bắt đầu phần Nghe`;
- submit creates/resumes an immutable Listening attempt and reaches the player;
- invalid/missing authored media still fails closed with a useful message;
- direct player access without a completed preflight session still redirects
  to the attempt preflight.

Resolution and evidence:

- `V44__practice_seed_listening_check_audio.sql` (renumbered from the feature
  branch's V38 during the main merge) repairs only the canonical
  development seed and publishes a new immutable version; the already applied
  V25 history and version 1 snapshot remain unchanged;
- the deterministic `listening-speaker-check.wav` fixture is 1.8 seconds,
  mono PCM at 44.1 kHz, and loaded in the browser with `readyState = 4` and no
  media error;
- the service accepts only canonical governed material references or the exact
  bundled seed path. Another arbitrary static audio path remains rejected;
- Test Detail now renders the controller flash failure instead of silently
  returning to the same screen;
- JDK 17 compile passed and the focused service/UI/integration gate passed
  `101/101`, with zero failures and zero errors;
- Flyway applied the migration successfully to the local UI schema (V38 on the
  feature branch; V44 after integration into main). Published Listening
  version 2 contains the bundled check-audio reference while historical
  version 1 remains available to its existing attempt;
- the in-app test browser loaded the asset but its environment blocks audio
  output. A real Chrome journey therefore supplied the audible playback proof:
  while the tab still reported `Phat am thanh`, the progress slider was at 9%,
  the status said the audio was playing and the confirmation checkbox was
  already enabled. This proves unlock occurs on `playing`, before `ended`;
- after checking `Toi nghe ro doan am thanh mau`, the start action enabled and
  opened `/practice/attempts/13006?mode=practice`. The Listening player rendered
  one immutable question and a `30:00` timer, not `00:00`.

Verdict:

- all Step 8 acceptance cases are covered by focused contracts plus the real
  Chrome seed journey;
- `PRE13E-F15` is closed. Step 8 is complete and Step 9 Jira synchronization is
  the only remaining pre-13E prerequisite.

### PRE13E-F16 - generic Jira Test/AI tickets are not Practice ownership

Status: `RESOLVED_STEP_9`

Finding:

- the intended Jira profile is authenticated and Atlassian Rovo exposes project
  `SCRUM` with writable `Task`, `Bug` and `Subtask` issue types;
- exact backlog search for `Practice` returns no work item;
- `SCRUM-363 Tests & Assignments` owns classroom tests and assignments, not the
  standalone `/practice` learning module;
- `SCRUM-321 Implement Optional AI Prototype/Exam Readiness Refinement` and
  other generic Test/Writing matches likewise do not establish Practice
  ownership;
- the user explicitly rejected treating those existing features as Practice.

Decision:

- do not edit, parent under or add Practice subtasks to `SCRUM-363`,
  `SCRUM-321` or any other generic feature ticket;
- create an independent hierarchy whose summaries begin with `[Practice]` and
  whose descriptions explicitly limit scope to `/practice`;
- use Scrum 3 id `135` only for transparent retrospective/backfilled Phase
  13A-13D work and Scrum 4 id `136` for pre-13E through 13H work;
- keep real Jira creation timestamps and place the actual implementation dates,
  commits and documentation evidence in descriptions;
- the reviewed batch contains 10 parent Task/Bug issues and 33 Subtasks: 17
  items in Scrum 3, 26 in Scrum 4, 28 completed items to Done and 15 future
  items left To Do;
- no Jira write has occurred yet. Creating, assigning and transitioning this
  batch is waiting for action-time confirmation.

Action-time confirmation:

- the user confirmed the reviewed 43-item Jira write batch on 2026-07-17;
- execution must retain the approved independent `[Practice]` hierarchy,
  Scrum 3/4 placement and Done/To Do split;
- this confirmation does not authorize edits to `SCRUM-363`, `SCRUM-321` or
  any other non-Practice work item.

Jira write ledger:

- `SCRUM-438` created as the independent Scrum 3 parent for retrospective
  Phase 13A-13C Practice delivery; Sprint field id `135` was accepted.
- Practice-only parent batch created without modifying any pre-existing issue:
  `SCRUM-439` timer bug, `SCRUM-440` Phase 13D, `SCRUM-441` PDF crop bug,
  `SCRUM-442` progress bug, `SCRUM-443` pre-13E architecture/fixtures,
  `SCRUM-444` Listening preflight bug, `SCRUM-445` Phase 13E,
  `SCRUM-446` Phase 13F and `SCRUM-447` Phase 13G-13H.
- first 12-Subtask create attempt was rejected atomically because this
  team-managed project forbids setting Sprint directly on a Subtask; Jira states
  that Subtasks inherit their parent's Sprint. No Subtask was created by that
  failed attempt. Retry omits `customfield_10020` while retaining the parent.
- Scrum 3 Subtasks created successfully through parent Sprint inheritance:
  `SCRUM-448..SCRUM-451` for Phase 13A-13C, `SCRUM-452..SCRUM-456` for Phase
  13D, and `SCRUM-457..SCRUM-459` for the timer, progress and PDF fixes.
- Scrum 4 Subtasks created: architecture/fixtures `SCRUM-460..SCRUM-464`,
  Listening fix evidence `SCRUM-465`, `SCRUM-466`, `SCRUM-468`, `SCRUM-470`,
  Phase 13E `SCRUM-467`, `SCRUM-469`, `SCRUM-471..SCRUM-473`, Phase 13F
  `SCRUM-474`, `SCRUM-475`, `SCRUM-477`, and Phase 13G-13H
  `SCRUM-476`, `SCRUM-478..SCRUM-480`.
- all 43 approved work items now exist as `SCRUM-438..SCRUM-480`; none was
  created under or linked to a non-Practice parent.
- transition `Done` was available as workflow id `2`; child evidence was
  transitioned before completed parents;
- final JQL reconciliation returned exactly 43 items: 6 Tasks, 4 Bugs and 33
  Subtasks; 17 inherit Scrum 3, 26 inherit Scrum 4; 28 are Done and the 15
  planned Phase 13E-13H items remain To Do;
- Step 9 is complete. The architecture/fixture/Listening/Jira prerequisites are
  green; no Phase 13E production code was implemented during this step.

### PRE13E-F17 - phase-only Jira hierarchy hides the ten Practice modules

Status: `RESOLVED_STEP_9_PRODUCT_VIEW`

Finding:

- the first Jira synchronization accurately recorded Phase 13 delivery, bugs
  and sprint history, but its top-level Tasks were phase containers;
- it did not give CAT, AUT, XLS, PDF, PLY, RLE, WRT, SPK, RES and PRG their own
  product Tasks with formal Use Case children;
- this made the backlog look like a Phase 13 checklist instead of the complete
  Practice feature decomposition.

Decision and correction:

- retain `SCRUM-438..SCRUM-480` as truthful delivery/sprint evidence;
- create `SCRUM-481..SCRUM-490` as the canonical ten-module product hierarchy;
- create `SCRUM-491..SCRUM-520` as exactly three Use Case Subtasks per module;
- mark only code-backed current capabilities Done and leave the five planned
  13E/13F Use Cases To Do;
- keep the module Tasks outside closed historical sprints because they span
  multiple phases; their descriptions link back to delivery evidence;
- verify by JQL: 40/40 issues, 10 Tasks, 30 Subtasks, six Tasks Done, four Tasks
  In Progress, 25 Subtasks Done and five Subtasks To Do;
- `SCRUM-363` and all other non-Practice features remain untouched.

### PRE13E-F18 - formal Use Case DOCX mixed Vietnamese and technical rules

Status: `RESOLVED_DOCUMENTATION`

Finding:

- the architecture DOCX covered all 30 Use Cases, but its reader-facing copy
  was Vietnamese and its Business Rules used capability-local technical ids;
- the requested submission format is English, and Business Rules must be short
  enough for a non-technical reader to understand without reading the code.

Correction and evidence:

- add a dedicated English content source for ten capabilities, 30 Use Cases and
  globally ordered Business Rules `BR-01..BR-60`;
- keep current/planned status and code traceability, but explain preconditions,
  success, failure-safe outcomes, flows and messages in plain English;
- render each Business Rule as a bold id followed by one direct policy
  statement, matching the requested `BR-01` example style;
- regenerate only the DOCX so the reviewed Draw.io architecture source remains
  unchanged;
- structural QA confirms 30 unique Use Case ids, 60 unique sequential Business
  Rules, no Vietnamese characters and a valid DOCX archive;
- LibreOffice render QA confirms 33 clean pages with no clipped tables,
  overflow, overlap or broken title wrapping.

### PRE13E-F19 - ten top-level DOCX modules felt inflated and fixed rule counts prevented reuse

Status: `RESOLVED_DOCUMENTATION`

Finding:

- the ten technical modules are useful for code, Draw.io and Jira traceability,
  but presenting all ten as equal reader-facing DOCX sections made the Practice
  specification feel unnecessarily fragmented;
- forcing every Use Case to own the same number of local Business Rules and
  System Messages repeated shared authorization, role and version policies;
- reader-facing actor terminology still used `Learner` in places although the
  requested product term is `Student`.

Correction and evidence:

- group the unchanged 30 Use Cases into four functional areas: Practice Test
  Management; Skill-based Attempt Lifecycle; Versioned Results and Evidence;
  and Practice Progress Management;
- preserve all ten internal module codes and all Use Case ids for code, Draw.io
  and Jira traceability; the Jira hierarchy remains ten module Tasks and thirty
  Use Case Subtasks;
- replace fixed local rule/message counts with shared catalogs containing 49
  Business Rules and 44 System Messages; each Use Case references only the
  identifiers it needs and shared policies may be reused;
- use `Student` consistently in reader-facing copy while retaining current
  implementation class names such as `PracticeLearnerAccessService`;
- structural QA confirms four groups, ten internal modules, 30 unique Use Case
  ids, 49 continuously numbered Business Rules, 44 shared System Messages and
  no reader-facing `Learner` term;
- DOCX archive validation and full 33-page render review confirm no table
  overflow, overlap, clipping or broken title wrapping. Apparent black regions
  in two image previews were viewer artifacts; source PNG pixel bounds and the
  rendered PDF remain intact.

### PRE13E-F20 - Jira work order must follow four Practice dependencies, not reconstructed timestamps

Status: `RESOLVED_DOCUMENTATION`

Finding:

- the existing Jira phase and ten-module hierarchies remain truthful evidence,
  but neither by itself explains the safest future implementation order;
- ordering planned work from timestamps in Markdown logs would confuse
  evidence chronology with architecture dependency and could imply false Jira
  creation or sprint history;
- Bug work must sit beside the Task it affects and must not become a fictional
  catch-all Subtask.

Correction and evidence:

- append a prospective Jira delivery blueprint to the Use Case DOCX with four
  parent Tasks ordered `MGT -> ATT -> RSL -> PRG`;
- preserve the ten technical module codes as Components, labels and Use Case
  traceability rather than ten competing delivery streams;
- give every parent Task eight ordered Sub-tasks for requirements, class
  diagram, sequence diagrams, architecture contracts, backend, frontend, tests
  and UAT;
- provide three risk-based Bug summary templates per Task and require a real
  standalone Bug linked with `blocks` or `relates to` only after reproduction;
- state explicitly that Jira dates, Sprint placement and work logs must remain
  truthful and must never be backdated from `.md` timestamps;
- regenerate the DOCX to 38 pages; pages 1-33 are byte-identical to the prior
  verified render, and pages 34-38 were visually inspected with no clipping,
  overflow, overlap or split tables.

### PRE13E-F21 - Mermaid delivery code needed renderer-level validation

Status: `RESOLVED_DOCUMENTATION`

Finding:

- the requested paste-ready package requires four class diagrams and 30
  sequence diagrams, but block-count validation alone cannot prove that Mermaid
  accepts every diagram;
- Mermaid CLI found a trailing semicolon on generated `cssClass` statements
  that a lightweight sequence parser did not expose.

Correction and evidence:

- add `generate_practice_mermaid_artifacts.py` and generate exactly four
  functional-area class diagrams plus one sequence diagram for each of the 30
  formal Use Cases;
- remove the invalid `cssClass` terminator and regenerate both Markdown files;
- preserve current/planned status styling, ten internal module namespaces,
  stable Use Case ids and reader-facing `Student` participant labels;
- render all four class diagrams and all 30 sequence diagrams successfully with
  Mermaid CLI and local Chrome; generated SVGs remain temporary QA evidence and
  are not added to the repository.

## 4. Change Inventory

| Date | Area | Change | Status |
|---|---|---|---|
| 2026-07-17 | Local environment | Point ignored local datasource at `ksh_phase13e_result_ui`; migrate cleanly through V37 | Complete |
| 2026-07-17 | Workflow | Add mandatory live phase log rule from 13E onward | Complete |
| 2026-07-17 | Phase gate | Update 13D evidence and lock pre-13E architecture/fixture prerequisites | Complete |
| 2026-07-17 | Architecture pack | Generate and visually review Practice-only DOCX/Draw.io for ten bounded capabilities; page count is not an acceptance target | Complete |
| 2026-07-17 | Result fixtures | Add and load idempotent attempts `13001..13004`, READY R/L explanations and eight stable review URLs | Complete; authenticated browser baseline accepted |
| 2026-07-17 | Result browser baseline | Review all eight desktop/mobile routes, interaction tabs, immutable explanations and console state | Step 7 complete; Detail findings F10-F14 feed 13E |
| 2026-07-17 | Result Detail baseline | Confirm R/L answer/explanation rendering; record wrong Listening-as-Reading label and missing semantic landmarks | Finding recorded for 13E |
| 2026-07-17 | Listening preflight | Add post-Step-7 reproduce/plan/fix task for sound check that does not enter the test | Step 8 complete; F15 resolved |
| 2026-07-17 | Listening preflight | Add seed-only immutable version repair (V44 on main), exact bundled check-audio allowlist and visible Test Detail feedback | Complete; compile, 101/101 focused gate, Flyway and real Chrome journey green |
| 2026-07-17 | Jira synchronization | Create and reconcile independent Practice hierarchy `SCRUM-438..SCRUM-480`; exclude SCRUM-363/SCRUM-321 as other features | Step 9 complete; 43/43, 28 Done, 15 To Do |
| 2026-07-17 | Jira product hierarchy | Add canonical ten-module Tasks `SCRUM-481..490` and thirty Use Case Subtasks `SCRUM-491..520`; retain phase tickets only as delivery evidence | Complete; 10 Tasks, 30 Subtasks, 25 current Use Cases Done, 5 planned Use Cases To Do |
| 2026-07-17 | Use Case document | Rewrite all reader-facing content in English and replace local technical rules with plain-language `BR-01..BR-60` | Complete; structural checks and 33-page visual render QA passed |
| 2026-07-17 | Runtime warning | Classify JDK 26/ClassGraph `Unsafe.invokeCleaner` warning without dependency churn | Recorded; no code change |
| 2026-07-18 | Use Case document grouping | Present 30 Use Cases in four reader-facing areas; use shared `BR-01..BR-49` and 44 reusable System Messages; standardize `Student` terminology | Complete; valid DOCX and 33-page visual QA passed |
| 2026-07-18 | Jira delivery appendix | Add dependency-ordered `MGT -> ATT -> RSL -> PRG` parent Tasks, 32 ordered Sub-tasks, 12 linked Bug templates and no-backdating policy | Complete; DOCX now 38 pages, prior 33 unchanged and five appendix pages visually reviewed |
| 2026-07-18 | Mermaid delivery code | Generate four functional-area class diagrams and 30 Use Case sequence diagrams | Complete; all 34 blocks rendered successfully with Mermaid CLI |

## 5. Open Items Before 13E Implementation

- obtain user approval before changing Phase 13E production code.

## 6. Pre-Validation Reread

Not reached. No Phase 13E validation has been run.
