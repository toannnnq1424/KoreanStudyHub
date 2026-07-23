# Practice Phase 13E Live Change Log

> **Current-source note (`2026-07-24`, consolidated validation closure):**
> Phase 13E is `COMPLETE_FOCUSED_GATE_GREEN`. `13E-00` is complete and
> `13E-01..05` are `IMPLEMENTED_AND_FOCUSED_TESTED`. The final JDK 17 focused
> selector passed `118/118` with zero failures, errors or skips on a fresh
> disposable current-schema database migrated through V44; that database was
> deleted after the gate and the stale configured database was not repaired or
> reused. Historical PRE-13E findings
> below that say “six
> criteria” or “holistic” are superseded for current runtime by Phase 13D
> F06/UX-03..05: transcript-only four-row language profile, two acoustic
> `NOT_SCORABLE` rows, no subtotal or holistic/attempt score. Phase 13E must
> consume that contract rather than revive the old wording.

Status: `COMPLETE_FOCUSED_GATE_GREEN`

Started: 2026-07-17

Implementation opened: 2026-07-22

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

Validation closure added `2026-07-22`: the two previously blocked authenticated
route cases passed on a fresh disposable schema migrated from V1 through V44:

- `PracticeIntegrationTest#testWritingResultDetailDoesNotRestoreMcqQuestionId`;
- `PracticeIntegrationTest#testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm`.

The run completed `2/2`, zero failures/errors/skips on JDK 17. The disposable
database was dropped after the gate. The stable `ksh_phase13e_result_ui`
fixture database and its history were not repaired, wiped or treated as proof.

User direction added `2026-07-22` (chip reminder): Result and Result Detail may
learn PREP's compact category-chip hierarchy, counts and evidence navigation,
but only as an interaction pattern. KSH chip labels, order, applicability,
parent criterion and any score-bearing label/denominator/descriptor come from a
named versioned Korean task/rubric/construct policy; chip counts are exact
backend-validator-accepted normalized findings with evidence and never IELTS
bands, scores or copied PREP taxonomy. Transcript-only Speaking must not expose
acoustic chips inferred from STT. Phase 13E supplies typed Vietnamese/Korean
  runtime/UI proof; versioned runtime/construct/evidence/descriptor/bundle
  correctness remains `PRE_PHASE_14`, while final Korean-SME sign-off,
  golden/adversarial corpus and calibration/fairness/repeatability remain
  `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F.

## 2. User Requirements Received During Preparation

### 2026-07-22 - explicit Phase 13E GO and cross-skill scope promotion

Request:

- proceed from the completed Phase 13D correction into Phase 13E;
- correct the Writing hierarchy so score-bearing content/task achievement is
  in `Tổng quan`, while `Điểm mạnh` contains only evidence-backed findings;
- replace learner-visible English category/chip labels such as `Content` and
  `Coherence` with Vietnamese/Korean product labels;
- make Writing and Speaking grammar, vocabulary, register and delivery
  descriptors specific to Korean rather than copying IELTS constructs;
- give Speaking Result Detail compact chip navigation and audio/transcript
  evidence organization inspired by PREP without inventing acoustic evidence;
- replace the visually weak Reading/Listening detail with question-type-native
  explanation modes, including evidence trace, relevant translation, tables,
  option elimination, fill-blank constraints and TFNG relation reasoning;
- apply this correction across Reading, Listening, Writing and Speaking now,
  while leaving versioned runtime/registry correctness in `PRE_PHASE_14` and
  final Korean-SME sign-off, golden/adversarial corpus,
  calibration/fairness/repeatability and audio-capable scoring in
  `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F;
- capture the current local Result/Detail surfaces for direct comparison with
  the supplied PREP screenshots.

Decision:

- accept this message as the explicit Phase 13E GO;
- implement runtime contracts, localization, screen-specific presenters and
  evidence-honest UI in Phase 13E;
- do not claim that a finite rule/prompt registry covers all historical and
  modern Korean; publish only the declared supported construct/task domain;
- keep PREP as interaction/IA evidence only. KSH Korean task policy, immutable
  answer authority and backend-validated evidence own all labels and meaning.

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
| 2026-07-22 | 13D route prerequisite | Run the two previously blocked authenticated Result Detail route cases on a disposable schema migrated from V1 through V44 | Complete; JDK 17, `2/2`, zero failures/errors/skips; disposable database removed |
| 2026-07-22 | Phase authorization | Record the user's explicit Phase 13E GO and promote cross-skill Korean localization/taxonomy/explanation runtime work into 13E | Complete; `13E-00` opened |
| 2026-07-22 | Current KSH baseline | Capture the already-open local Writing/Speaking/Listening Result and Reading/Speaking Detail surfaces without provider calls | Stored under `docs/architecture/practice/evidence/phase13e-current-baseline-2026-07-22/`; remaining routes retain the authenticated Step 7 baseline |
| 2026-07-22 | Speaking Detail recapture | Replace the accidental repeated/full-page capture with one visible viewport of `/practice/attempts/13004/result/detail` | Complete; `speaking-detail.png` is the single-screen baseline |
| 2026-07-22 | PREP comparison audit | Re-audit all 46 supplied Result/Detail images and separate reusable IA from IELTS/English constructs | Complete; PREP contributes split-pane, chip navigation, evidence linking and progressive disclosure only |
| 2026-07-22 | 13E-00 document reconciliation | Align workflow, Phase 13 gate/blueprint, assessment design, PREP research supersession and pre-Phase-15 inventory with explicit GO and the runtime-vs-retained-debt split | Complete; no production code or phase validation run |
| 2026-07-23 | 13E-01 typed Result Detail boundary | Add the immutable read-only assembler, sealed three-kind DTO/presenter dispatch, backend KSH descriptors and three server-rendered template seams | Implemented; static review only, consolidated Phase 13E validation deferred |
| 2026-07-23 | 13E-02 Objective R/L Detail | Add three type-native per-question DTO/renderers, authority-locked answer mapping, strict typed explanation/evidence reads, bounded v2 single-choice compatibility and v3 future generation contract | Implemented; static review only, consolidated Phase 13E validation deferred |
| 2026-07-24 | 13E-02 independent static acceptance closure | Replace provider-incompatible composed schema with one type-specific root object, reject duplicate evidence ids before persistence, remove unauthoritative page claims and bind each v3 translation entry to exactly one validated evidence id | Blockers closed statically; no validation command run |
| 2026-07-24 | 13E-03 Writing Result Detail | Add the bounded KSH Writing diagnostic seam, exact four-tab server-rendered detail, task-native score trust labels, evidence navigation and provenance-safe upgrade artifacts | `IMPLEMENTED_PENDING_PHASE_VALIDATION`; implementation static reread complete, independent static acceptance and consolidated validation still pending |
| 2026-07-24 | 13E-04 Speaking Result Detail | Scope the detail body to one immutable `activeQuestionId`, retain four transcript-grounded language criteria plus two acoustic `NOT_SCORABLE` rows, expose playback without claiming that the evaluator heard audio, and keep four feedback tabs | `IMPLEMENTED_PENDING_PHASE_VALIDATION`; independent static acceptance complete; no validation command run |
| 2026-07-24 | 13E-05 reconciliation | Fail closed on untrusted Writing contract markers, make non-selected Speaking tasks navigation-only, reconcile legacy JSON as bounded read-only compatibility and audit all three typed screens | `IMPLEMENTED_PENDING_PHASE_VALIDATION`; independent static acceptance complete after the final Việt–Hàn display-label correction; no validation command run |
| 2026-07-24 | Phase 13E static closure | Reconcile all implementation ledgers/current-source documents, reread the complete diff and record the one exact consolidated selector | `READY_FOR_PHASE_VALIDATION`; validation not yet run |

## 5. Approved Phase 13E Implementation Slices

1. `13E-00` — reconcile the gate, source documents and captured baseline —
   `COMPLETE`;
2. `13E-01` — introduce one typed read-only dispatcher, exactly three screen
   contracts and backend-owned Vietnamese/Korean chip descriptors —
   `IMPLEMENTED_PENDING_PHASE_VALIDATION`;
3. `13E-02` — implement discriminated Reading/Listening explanation contracts
   and renderers for the supported canonical question types —
   `IMPLEMENTED_PENDING_PHASE_VALIDATION`;
4. `13E-03` — rebuild Writing Detail with score-bearing rubric in Overview,
   evidence-backed Korean diagnostics and exactly four feedback tabs —
   `IMPLEMENTED_PENDING_PHASE_VALIDATION / ACCEPT_STATIC`;
5. `13E-04` — rebuild Speaking Detail with audio/transcript provenance,
   evidence navigation and no transcript-inferred acoustic assessment —
   `IMPLEMENTED_PENDING_PHASE_VALIDATION / ACCEPT_STATIC`;
6. `13E-05` — reconcile localization, ARIA/keyboard behavior, mobile layout,
   compatibility inventory and all affected design/workflow documents —
   `IMPLEMENTED_PENDING_PHASE_VALIDATION / ACCEPT_STATIC`;
7. after all implementation slices and static reconciliation are complete,
   declare `READY_FOR_PHASE_VALIDATION` and run one consolidated JDK 17 gate.

Next action: run the one consolidated Phase 13E validation sequence recorded in
Section 12. Do not start Phase 14/15 or run a second per-slice validation.

Implementation units do not run test, compile, build, lint, application startup,
Docker/frontend build, migration test, provider call, `git diff --check` or Git
mutation. Tests may be read and updated as contracts, but execution is deferred
to the single Phase 13E validation unit.

## 6. 13E-01 Implementation Ledger

Implemented `2026-07-23` without running tests, compile, build, lint, browser,
provider, database or Git mutation commands.

Production files changed:

- `src/main/java/com/ksh/features/practice/controller/PracticeController.java`;
- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/ai/writing/WritingFeedbackViewMapper.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultAssembler.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultDetailAssembler.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultDetailPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/ResultDetailDescriptorRegistry.java`;
- `src/main/java/com/ksh/features/practice/result/ObjectiveResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/web/PracticeModelAttributes.java`;
- `src/main/java/com/ksh/features/practice/web/PracticeViews.java`;
- `src/main/resources/templates/practice/result-detail-objective.html`;
- `src/main/resources/templates/practice/result-detail-writing.html`;
- `src/main/resources/templates/practice/result-detail-speaking.html`;
- `src/main/resources/static/css/practice-result-detail.css`.

Contract-test files changed or added:

- `src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java`;
- `src/test/java/com/ksh/features/practice/ai/writing/WritingFeedbackViewMapperTest.java`;
- `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java`.

Locked implementation decisions:

- `PracticeResultDetailAssembler` reuses the owner/result-state/immutable
  snapshot/skill-match context loader of `PracticeResultAssembler`, then
  requires exactly one detail presenter; zero or multiple matches fail closed;
- the sealed envelope contains exactly one `OBJECTIVE_DETAIL`,
  `WRITING_DETAIL` or `SPEAKING_DETAIL` payload, and the controller switches on
  that typed kind without a skill fallback;
- `questionId` is rejected outside Writing and Speaking. A Writing/Speaking
  selection is accepted only when it belongs to a detail-capable task in the
  immutable snapshot; invalid or foreign ids select the first owned task of
  the active skill without leaking foreign content;
- controller-side `questionsJson`/`groupsJson` serialization and `[]` error
  fallback are removed from the active route. The three active boundaries are
  server-rendered and do not count diagnostics in browser code;
- scoring criterion, diagnostic finding and UI filter chip are separate DTOs.
  Chip descriptors carry stable id, Vietnamese/Korean labels, polarity, parent
  criterion, applicability, stable order, validator-accepted count,
  `countedSeparately=false` and evidence availability;
- Writing detail counts only raw stored findings that pass the essential KSH
  normalizer invariants: active non-alias criterion, task/polarity match,
  explicit supported evidence scope, authorized evidence shape, exact text
  span, nonblank explanation and polarity-appropriate correction. Compatibility
  mapper readability remains separate and cannot make a finding count;
- Q51/Q52 stored findings currently have no authoritative blank id/index.
  Therefore their diagnostic/chip descriptor mapping fails closed instead of
  inventing `W_CLOZE_CONTEXT`, assigning essay-only parents, choosing blank 1
  or 2, or duplicating one finding across both blanks. Q53/Q54/GENERAL
  descriptors remain mapped only to score criteria present in the task-native
  official rubric;
- Writing provider category/display labels cannot override the KSH registry.
  Writing annotations are retained only for active KSH criterion ids, and their
  canonical criterion/category is registry-owned; unknown and inactive raw ids
  cannot reclassify a long-form analysis lens. Speaking descriptors are created
  only from current verified transcript evidence, from the validated parent
  criterion plus its KSH-owned subcriterion id; provider display labels cannot
  replace them. The current bounded registry has 16 transcript-language
  subcriteria: 3 content, 6 grammar, 4 vocabulary and 3 coherence. Each has its
  own stable Vietnamese/Korean descriptor, parent and order, so findings such
  as particles and endings do not collapse into the grammar score-card label.
  Unknown, blank, cross-parent and acoustic subcriteria fail closed in
  `TRANSCRIPT_ONLY`; the acoustic guard is mode-scoped so a separately governed
  future direct-audio contract is not permanently closed. These 16 entries are
  a bounded runtime foundation, not a claim that three examples (particles,
  endings and honorific/register) or this registry exhaust Korean constructs;
- current transcript-only Speaking preserves four independent numeric language
  rows plus two null `NOT_SCORABLE` acoustic rows and exposes neither subtotal,
  holistic score nor acoustic chip in the active detail template. The acoustic
  state remains `NOT_SCORABLE` when current low-confidence transcript evidence
  coexists with legacy-unverified rows;
- focused Writing, mixed-Writing and Listening integration fixtures publish a
  version and start attempts through the immutable lock boundary before applying
  their historical test state; cleanup removes the published-version fixture
  before deleting the live graph;
- legacy `result-detail.html`, `rl-result-detail.html`, their constants and
  compatibility readers remain present for the Phase 15 inventory. No schema,
  migration, provider pipeline, cache or new scoring calculation was added.

Deliberately deferred:

- `13E-02`: per-question Objective DTOs and discriminated R/L explanation
  renderers;
- `13E-03`: full Writing split-pane evidence rendering and exactly-four-tab
  interaction, plus typed blank identity and the per-blank diagnostic contract
  needed to map Q51/Q52 findings to one truthful official criterion. No provider
  prompt/schema change is introduced in 13E-01. Writing also currently has only
  a bounded finding registry with broad categories; 13E-03 must audit and
  design task-bounded typed Writing diagnostic subcriteria/construct
  presentation and authoritative evidence-to-parent mapping without treating
  the 13E-01 categories as exhaustive;
- `13E-04`: Speaking media/transcript provenance, per-finding navigation and
  exactly-four-tab body interaction. Typed backend descriptor truth is already
  owned by 13E-01 and is not deferred. 13E-04 must add evidence/media
  navigation, governed legacy Speaking content/evidence rendering and restore
  a deep body assertion for
  `PracticeIntegrationTest#testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates`;
- `13E-05`: final localization, ARIA/keyboard, responsive and compatibility
  reconciliation;
- `PRE_PHASE_14`: correctness gate for versioned runtime, construct, evidence,
  descriptor and bundle policy beyond the bounded Writing/Speaking registries.
  Final Korean-SME sign-off, golden/adversarial corpus,
  calibration/fairness/repeatability belong to
  `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F; none is claimed by 13E-01.

Focused validation deferred to the one final Phase 13E gate:

- `PracticeResultDetailContractTest`;
- `PracticeResultPresenterTest`;
- `WritingFeedbackViewMapperTest` and `SpeakingFeedbackViewMapperTest`;
- `PracticeServiceTest#testWritingFeedbackViewPreservesSelectedEntryOrderAndDefaultsOptionalLists`;
- `PracticeFunctionalUiContractTest`;
- `PracticeIntegrationTest#testReadingResultGetsAreReadOnlyAndNeverInvokeProvider`;
- `PracticeIntegrationTest#testSubmitWritingAttemptAndGetResult`;
- `PracticeIntegrationTest#testResultRenderSecurityEscaping`;
- `PracticeIntegrationTest#testWritingQuestionReEvaluateEndpointUsesQuestionIdParameter`;
- `PracticeIntegrationTest#testWritingResultDetailRendersPerQuestionReEvaluateForm`;
- `PracticeIntegrationTest#testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion`;
- `PracticeIntegrationTest#testWritingResultDetailDoesNotRestoreMcqQuestionId`;
- `PracticeIntegrationTest#testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak`;
- `PracticeIntegrationTest#testWritingQuestionReEvaluateConflictRedirectsDetailWithFlashError`;
- `PracticeIntegrationTest#testReadingResultDetailDoesNotRenderPerQuestionReEvaluateForm`;
- `PracticeIntegrationTest#testListeningResultDetailDoesNotRenderPerQuestionReEvaluateForm`;
- `PracticeIntegrationTest#testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm`;
- `PracticeIntegrationTest#testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates`
  for the 13E-01 typed Speaking skeleton; 13E-04 must deepen its body/evidence
  acceptance rather than deleting the legacy-content debt;
- existing Result Detail owner/non-leak, result-state and immutable-snapshot
  integration cases selected by the final validation unit.

Static risks remain: no compiler or template engine has executed in this
implementation unit; the new templates intentionally expose only the typed
skeleton and backend descriptors until 13E-02/03/04; and the code-backed
Writing/Speaking descriptor policies are bounded runtime policies, not
exhaustive Korean-construct coverage. They do not claim the final Korean-SME,
golden/adversarial corpus, calibration, fairness or repeatability evidence
reserved for `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F.

## 7. 13E-02 Implementation Ledger

Implemented `2026-07-23` without running tests, compile, build, lint, browser,
provider, database, migration, Docker or Git commands. Static source review
used `/usr/bin/grep`, `find` and `sed`; the workspace-bundled `rg` was observed
to stall, was terminated and is not validation evidence.

Production files changed:

- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/result/ObjectiveResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationReadService.java`;
- `src/main/java/com/ksh/features/practice/ai/readinglistening/ReadingListeningExplanationClient.java`;
- `src/main/resources/templates/practice/result-detail-objective.html`;
- `src/main/resources/static/css/practice-result-detail.css`.

Contract-test files changed or added:

- `src/test/java/com/ksh/features/practice/result/ObjectiveResultDetailTypeNativeContractTest.java`
  (added);
- `src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationReadServiceTest.java`;
- `src/test/java/com/ksh/features/practice/ai/readinglistening/ReadingListeningTypedClientContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`.

Locked implementation decisions:

- `ObjectiveDetailPayload` now carries one sealed item for every immutable
  question version and is sealed to exactly `ObjectiveSingleChoiceDetail`,
  `ObjectiveFillBlankDetail` and `ObjectiveTfngDetail`. Source-group/question
  navigation is backend-built, unique and cross-checked against the immutable
  question-version ids; no browser JSON/tree/map contract was introduced;
- `ObjectiveResultPresenter` derives visible options, official option ids,
  accepted fill values, normalization policy, TFNG official value/relation,
  learner state, prompt, teacher explanation, labels and order only from the
  immutable `PracticeQuestionVersion`, typed `QuestionContent`/`AnswerSpec`,
  locked attempt answer and scoring engine. Artifact content cannot replace
  any of that authority, and unknown learner option ids are never emitted;
- single-choice rows preserve stable backend option ids/visible order and show
  backend learner/correct state plus one rationale for every option. A valid
  artifact must cover the correct option and every eliminated wrong option;
- fill-blank rows preserve stable backend blank ids, learner value, immutable
  accepted values/aliases and the exact scoring normalization contract.
  Artifact rows may supply context/semantic/grammar/register explanations and
  evidence ids, but the v3 schema has no accepted-value field and therefore
  cannot mutate answer authority;
- TFNG maps the immutable official value to exactly `TRUE -> ENTAILED`,
  `FALSE -> CONTRADICTED`, `NOT_GIVEN -> NOT_STATED`; only the two
  non-authoritative labels are rendered as alternatives. A READY
  `NOT_GIVEN` artifact must carry an explicit missing-information statement;
- `QuestionExplanationReadService` remains the sole canonical artifact/binding
  read path. Its new read-only typed method verifies the artifact question
  discriminator, binding/artifact fingerprint identity, response schema and
  locked input-contract discriminator before returning a sealed type
  explanation. Unknown/cross-type fields,
  missing per-option/per-blank coverage, foreign evidence ids and unsupported
  schemas fail to an honest per-question unavailable state;
- v2 compatibility is a bounded dual-read only when artifact metadata says
  `v2`, the immutable type is `SINGLE_CHOICE`, the quote is an exact approved
  passage/transcript span and elimination covers every wrong canonical option.
  v2 image evidence lacks digest/index/region and is unavailable. v2
  `FILL_BLANK`, v2 `TRUE_FALSE_NOT_GIVEN` and migrated `unknown` response
  schemas are not coerced into an option shape;
- v3 text evidence requires exact Korean source substring equality at the
  supplied offsets. Listening transcript spans require an approved immutable
  transcript and are marked `LINGUISTIC_CONTENT_ONLY`; they cannot substantiate
  pronunciation, prosody or another acoustic claim. Image evidence requires a
  matching immutable input media role and SHA-256 digest, authoritative media
  index and either a complete positive rectangle or explicit `WHOLE_IMAGE`.
  The current immutable media contract has no trusted page metadata, so v3,
  the DTO and the learner UI do not accept or display `pageIndex`. Free-text
  `[IMAGE]` never populates the factual evidence contract;
- v3 translation is a list of `Dịch đoạn liên quan` entries, each bound to
  exactly one already validated evidence id; foreign and duplicate bindings
  fail closed. The unscoped v2 `relatedTranslationVi` field is shape-checked
  for compatibility but is not promoted into the typed learner view. This
  structural scope does not claim semantic proof that a generated translation
  is exhaustive or perfectly minimal;
- learner answer, immutable official key, immutable teacher explanation and
  bound AI artifact are four separate provenance rows. The split source pane
  and review pane are server-rendered with semantic navigation, tables,
  headings, focus targets, responsive stacking and no re-evaluation/provider
  action;
- construct output fails closed because current persisted artifacts carry no
  approved typed construct code. The DTO has a future descriptor seam, but the
  runtime emits no guessed chip and declares
  `DEFERRED_PRE_PHASE_14_REGISTRY`. The future approved registry must govern
  task/evidence-bounded main idea, detail, inference, reference, discourse
  relation, vocabulary-in-context, communicative intent and speaker relation
  descriptors with Vietnamese/Korean labels; they must not become scores or a
  claim of exhaustive Korean coverage;
- `ReadingListeningExplanationClient` now fingerprints prompt
  `v8-objective-type-native` and response schema `v3`. It builds one
  provider-supported strict root object for the immutable question type,
  including immutable option/blank ids, instead of a root or nested `oneOf`.
  Text and image evidence use separate typed arrays; duplicate evidence ids,
  cross-type fields and foreign ids are rejected before persistence.
  Generated material remains learner-facing Vietnamese tied to exact Korean
  text/image evidence and excludes learner answers, provider-supplied official
  answers and construct labels;
- the active Result Detail GET remains read-only: no generation, queue, retry,
  task, binding or persistence mutation was added. The existing artifact,
  binding, task and preparation lifecycle remains canonical; old result
  compatibility readers/templates remain present for the named cleanup gate;
- no Writing/Speaking production path, Report an Error flow, schema, migration,
  rebind, delete, reset, cache, worker or database state was changed.

Deliberately deferred and handoff:

- `13E-03` must keep the three Writing scoring parents stable and add a typed,
  non-additive, task-bounded diagnostic/subcriterion seam with Vietnamese and
  Korean labels, evidence-to-parent authority and explicit copy that the
  surfaced descriptors do not exhaust Korean writing. Writing needs a richer
  Korean diagnostic taxonomy than the broad 13E-01 categories, but 13E-02 did
  not change Writing production code;
- a full versioned Writing/Speaking registry plus strict provider
  schema/prompt, normalizer/rule identity and cache/fingerprint identity is a
  correctness blocker for `PRE_PHASE_14`, not an opportunistic 13E-03
  expansion. `13E-03` must not claim the final Korean-SME sign-off,
  golden/adversarial corpus, calibration, fairness or repeatability closure
  reserved for `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F;
- `13E-04` still owns Speaking evidence/media navigation and exactly-four-tab
  body work; 13E-02 does not broaden the bounded 16-item Speaking registry;
- `13E-05` owns final cross-skill localization, ARIA/keyboard/mobile and
  compatibility reconciliation plus the six shared documents intentionally
  left untouched during this slice;
- `PRE_PHASE_14` owns versioned R/L and W/S runtime/construct/evidence/
  descriptor/bundle correctness only. Final Korean-SME sign-off,
  golden/adversarial corpus, calibration/fairness/repeatability remain
  `PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F;
- `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE`, after consolidated Phase 13E and
  before 14A, owns append-only active/superseded R/L binding history, Writing
  cache `AssessmentPolicyBundle` identity, Flyway configuration safety and the
  guarded Practice rebaseline/fresh Flyway-Hibernate smoke proof. Rebaseline
  remains plan-only during Phase 13E and may run only when written evidence
  proves no retained, deployed, shared, canonical or upgrade-supported
  database obligation; otherwise stop, preserve checksums and use a separately
  reviewed forward path. Never Flyway repair or reuse an old database; preserve
  non-Practice V38-V43 bytes/checksums and choose the next free version at
  implementation time (`V44__practice_baseline.sql` only if still free). The
  baseline is schema-only and excludes legacy backfill, content seed and the
  V44 local repair. Phase 15 after 14F owns retained-data cleanup, canonical
  SME-reviewed Vietnamese/Korean UAT seed and Manual UAT. The current local
  database remains read-only evidence, not a master/canonical database. No
  schema, migration or database action was authorized inside 13E-02.

Exact focused selector to include in the one consolidated Phase 13E validation
unit (recorded only; not run in 13E-02):

```bash
./mvnw "-Dtest=PracticeResultDetailContractTest,ObjectiveResultDetailTypeNativeContractTest,PracticeResultPresenterTest,QuestionExplanationReadServiceTest,ReadingListeningExplanationClientTest,ReadingListeningTypedClientContractTest,QuestionExplanationLifecycleContractTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testSubmitAttemptAndGetResult+testReadingResultGetsAreReadOnlyAndNeverInvokeProvider+testResultAccessDeniedForOtherUser+testReadingResultDetailUsesTypedImmutableBoundary+testReadingResultDetailWithoutImmutableSnapshotFailsClosed+testReadingResultDetailDoesNotRenderPerQuestionReEvaluateForm+testListeningResultDetailDoesNotRenderPerQuestionReEvaluateForm" test
```

Static risks remain:

- no Java compiler, Thymeleaf renderer, CSS/browser engine or test runner has
  executed in this implementation unit;
- existing v2 artifacts that do not carry full wrong-option elimination or an
  exact approved text quote now fail closed in the new type-native detail even
  if a legacy compatibility page could display their generic prose;
- the v3 contract structurally scopes translations per validated evidence id,
  but Phase 13E does not claim semantic proof of translation minimality;
- raw-English/unversioned construct inference remains intentionally unavailable
  rather than synthesized;
- consolidated validation and documentation reconciliation remain mandatory
  before Phase 13E can be closed.

## 8. Pre-Validation Reread

13E-02 static reread completed `2026-07-24` across its DTO/presenter,
artifact-read/client schema, server template/CSS and focused contract tests.
The independent static-acceptance blockers were reconciled as recorded above.
The reported duplicate `startOffset` local declaration was checked in current
source; exactly one declaration remains.

Slice state: `13E-02 = IMPLEMENTED_PENDING_PHASE_VALIDATION`.

Next implementation slice: `13E-03`.

The consolidated Phase 13E validation gate has not been reached and no test,
compile, build, lint, browser, provider, database, migration, Docker or Git
command was run during this reread.

## 9. 13E-03 Writing Result Detail Implementation Ledger

Implemented and statically reread `2026-07-24`.

Slice state: `13E-03 = IMPLEMENTED_PENDING_PHASE_VALIDATION`.

This implementation unit does not record `ACCEPT_STATIC`. Independent static
acceptance and the one consolidated Phase 13E validation gate remain pending.

Production files changed or added:

- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringPolicy.java`;
- `src/main/java/com/ksh/features/practice/result/WritingDiagnosticDescriptorRegistry.java`;
- `src/main/java/com/ksh/features/practice/result/ResultDetailDescriptorRegistry.java`;
- `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java`;
- `src/main/resources/templates/practice/result-detail-writing.html`;
- `src/main/resources/static/css/practice-result-detail.css`;
- `src/main/resources/static/js/practice-result.js`.

Contract-test files changed or added:

- `src/test/java/com/ksh/features/practice/result/WritingDiagnosticDescriptorRegistryTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/ai/writing/WritingScoringPolicyTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`.

The existing
`src/main/java/com/ksh/features/practice/ai/writing/WritingFeedbackViewMapper.java`
and its test were reread as a compatibility boundary. Its legacy missing-scope
default remains `TEXT_SPAN`; the trusted typed Detail path does not use that
default and instead requires an explicit accepted raw `evidenceScope`.

Locked code and UI contracts:

- `WritingDetailPayload` carries the stable score profile
  `KSH_INTERNAL_TASK_NATIVE_V1` and diagnostic seam
  `ksh-writing-detail-diagnostics-seam-v1` /
  `BOUNDED_CURRENT_EVIDENCE`. Its diagnostic groups, target identity,
  evidence scope/availability, score effect and answer-artifact
  availability/provenance codes are allowlisted and fail closed;
- the backend-owned hierarchy exposes exactly seven ordered Vietnamese/Korean
  KSH categories. Only exact active current criterion ids become findings;
  inactive aliases, unknown ids and provider category/label/parent claims do
  not count. Broad `W_GRAMMAR_ERRORS` remains broad and is not guessed into a
  Korean subtype;
- long-form task/content findings link only to the content parent, discourse
  only to organization, and morphology/lexicon/register/orthography only to
  language. Length/format remains `DIAGNOSTIC_ONLY`, with no parent and no
  additive chip score;
- Q51 and Q52 retain their exact task identities and six official per-blank
  score allocations. Because the current immutable finding contract has no
  authoritative blank id/index, typed diagnostics fail closed with
  `BLANK_IDENTITY_UNAVAILABLE` rather than inventing or duplicating a blank
  parent;
- typed Detail score rows, diagnostics and qualitative artifacts require the
  selected question-keyed current contract, a READY task and exact stored
  `task_type` identity. Missing/mismatched identity makes the selected Detail
  score and qualitative artifacts unavailable. The accepted 13E-01
  invalid/foreign `questionId` compatibility fallback remains limited to the
  first detail-capable task inside the same immutable attempt;
- only validator-accepted raw findings with explicit supported evidence scope
  render. Exact text evidence must be a learner-answer substring;
  `WHOLE_ANSWER` carries no fabricated highlight. Future impact, frequency,
  confidence and observability fields remain honestly null;
- the active Writing page has exactly four accessible tabs:
  `Tổng quan / 개요`, `Điểm mạnh / 강점`,
  `Cần cải thiện / 개선 필요`, and
  `Bài nâng cấp / 개선된 답안`. Task score, denominators and official
  criterion cards occur only in Overview, together with the server-owned
  profile id, earned/max display and evaluation state;
- the server renders all finding groups, labels, counts and evidence.
  Localized chip buttons are scoped to their own tabpanel and use
  interaction-only JavaScript to filter/focus matching findings. The backend
  `chip.id()` contract is exactly
  `featureCode + "_" + applicability`, matching each finding's
  `data-writing-feature`; the browser parses no feedback JSON and computes no
  assessment meaning, count or score;
- the fourth tab separates a learner-submission-derived upgraded answer,
  significant rewrites with exact learner span/upgraded Korean/Vietnamese
  reason, and an optional evaluator-generated sample explicitly labelled as
  not a lecturer reference. The immutable lecturer reference remains entirely
  unrendered until its authoritative question-version round trip exists;
- missing immutable prompt and missing learner answer have explicit UI states.
  The existing per-question re-evaluate form ownership, CSRF field,
  selected-question isolation, escaping boundary and read-only GET contract are
  preserved;
- tab ids, `aria-controls`, `aria-labelledby`, `aria-selected`, roving
  `tabindex`, Left/Right/Up/Down/Home/End handling, focus movement, active/focus
  styling and narrow-viewport stacking are present in the scoped Writing
  template/CSS/JS.

Exact focused selector recorded for the one consolidated Phase 13E validation
unit; it was not run in 13E-03:

```bash
./mvnw "-Dtest=WritingDiagnosticDescriptorRegistryTest,WritingScoringPolicyTest,WritingFeedbackViewMapperTest,PracticeResultDetailContractTest,PracticeResultPresenterTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testSubmitWritingAttemptAndGetResult+testResultAccessDeniedForOtherUser+testResultRenderSecurityEscaping+testWritingQuestionReEvaluateEndpointUsesQuestionIdParameter+testWritingResultDetailRendersPerQuestionReEvaluateForm+testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion+testWritingResultDetailDoesNotRestoreMcqQuestionId+testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak+testWritingQuestionReEvaluateConflictRedirectsDetailWithFlashError+testWritingQuestionReEvaluateRequiresCsrf" test
```

`PRE_PHASE_14` correctness debt remains separate:

- replace the bounded current seam with the full versioned Writing/Speaking
  diagnostic registry and explicit compatibility classification;
- introduce the strict provider schema/prompt and authoritative target/evidence
  contract, including immutable Q51/Q52 blank identity and typed subtype,
  impact, frequency, confidence and observability where truthfully available;
- bind normalizer/rule/cache fingerprint identity and persist the full
  `AssessmentPolicyBundle`;
- add the immutable lecturer-reference question-version round trip before any
  lecturer answer may render as a sibling panel outside the four AI tabs;
- close the guarded production-correctness/rebaseline prerequisites only under
  their documented no-obligation rule. No schema, migration, database or
  rebaseline action belongs to 13E-03.

Final Korean-SME sign-off, golden/adversarial corpus and
calibration/fairness/repeatability remain
`PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F and are not claimed here.

Static reread covered the typed DTO constructor/accessors and allowlists,
registry uniqueness/order/task/parent resolution, presenter selection and
trust gates, template property names and four-tab containment, chip/finding
selector identity, scoped JS behavior, CSS active/focus/responsive selectors,
and the focused test fixtures. No test, compile, build, lint, application
startup, browser, provider, database, migration, Docker or Git command was run.

Next action: independent static acceptance of `13E-03`.

## 10. 13E-04 Speaking Result Detail Implementation Ledger

Implemented and statically reread `2026-07-24`.

Slice state: `13E-04 = IMPLEMENTED_PENDING_PHASE_VALIDATION`.

This implementation unit does not record `ACCEPT_STATIC`. Independent review
and the one consolidated Phase 13E validation gate remain pending.

Production files changed:

- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultDetailAssembler.java`;
- `src/main/java/com/ksh/features/practice/result/ResultDetailDescriptorRegistry.java`;
- `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java`;
- `src/main/resources/templates/practice/result-detail-speaking.html`;
- `src/main/resources/static/css/practice-result-detail.css`;
- `src/main/resources/static/js/practice-result.js`.

Focused contract-test files changed:

- `src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`.

Locked contract and UI behavior:

- `questionId` is now a valid selector for Writing and Speaking Detail only.
  Speaking resolves it only against immutable `SPEAKING`/governed legacy
  `ESSAY` tasks in the owned attempt; an invalid or foreign value falls back
  without disclosing whether the supplied id exists. Canonical `SPEAKING` is
  preferred for the bounded default and legacy `ESSAY` is explicitly labelled
  compatibility data;
- non-selected Speaking tasks carry navigation identity only. The selected
  immutable question id owns all six criterion states, transcript/media
  provenance, diagnostic findings/groups/chips and upgrade artifacts. No
  Detail score row or finding is aggregated across Speaking questions;
- the selected current transcript profile preserves four numeric KSH language
  criteria with their stored maxima and two null `NOT_SCORABLE` acoustic rows.
  It creates no task subtotal, holistic/attempt score, additive chip score,
  local 1-9 band or browser recomputation. Legacy/untrusted score material
  fails closed;
- the canonical submission sentinel is consumed only as a recording-source
  state. It is rejected by both the learner-text and transcript DTO
  boundaries, so it cannot render as Korean learner speech. When no
  authoritative current-contract transcript exists, the UI shows source,
  recording and processing/compatibility state instead of fake text;
- transcript provenance is sourced only from the selected current evidence
  contract's `actuallyHeardTranscript`. The learner-facing copy explicitly
  states that the current evaluator receives the transcript and does not hear
  the recording. Transcript-only or recording-source-only profiles cannot
  score fluency, rhythm, pronunciation, delivery or another acoustic family;
- the presenter reads READY recording metadata only through
  `PracticeSpeakingMediaService.findReadyMediaViewsForOwner`. A playback path
  is exposed only when the existing playback feature gate is enabled and the
  media belongs to the selected immutable question. Recording-to-transcript
  binding is labelled matched only when both stored media id and media lock
  version equal the current evaluation provenance; otherwise it is explicitly
  unverified/not applicable. Playback is labelled as proof of recording source
  only, never proof that the evaluator consumed audio;
- the bounded 16 exact current Speaking subcriteria remain the only accepted
  diagnostic ids. They are grouped server-side into bilingual Korean-speaking
  families for task/response relevance, discourse/organization,
  morphosyntax, lexicon/collocation and
  sociolinguistic/register/honorific/pragmatics. Unknown, cross-parent and
  acoustic ids fail closed; broad current ids are not guessed into narrower
  linguistic subtypes;
- all localized chip labels, order and counts are server-owned non-additive
  navigation metadata. The browser only filters/focuses already rendered
  selected-question findings and parses no feedback JSON;
- the active page contains exactly four accessible tabs:
  `Tổng quan / 개요`, `Điểm mạnh / 강점`,
  `Cần cải thiện / 개선 필요`, and
  `Bài nâng cấp / 개선된 답변`. It reuses the shared roving-tab behavior for
  `aria-selected`, `aria-controls`, `aria-labelledby`, Left/Right/Up/Down,
  Home/End and focus movement, with scoped active/focus/mobile CSS;
- Overview owns selected question/version/type identity, compatibility label,
  profile/evidence/evaluator/task-score state and the six canonical criterion
  rows. Strengths and Needs contain only exact transcript-grounded findings,
  not Writing labels and not inferred acoustic observations;
- Upgrade separates learner-transcript-derived evaluator output, significant
  phrasing rewrites whose original is an exact selected transcript span and an
  optional evaluator-generated sample explicitly labelled as not a teacher
  reference. No lecturer reference is rendered because its immutable
  question-version round trip is still unavailable.

Static reread checks completed:

- DTO allowlists, selected-question equality checks, task navigation
  uniqueness, sentinel rejection, transcript/media/acoustic invariants,
  exact-rewrite binding and evaluator-sample provenance were reread;
- presenter selection/default fallback, current-contract trust gate,
  per-question rubric lookup, exact selected diagnostic resolution, broad
  bilingual family mapping, media owner lookup and compatibility branches were
  reread;
- template property names and containment were checked statically. The source
  contains four `role="tab"` buttons and four corresponding tabpanels; the
  transcript, recording, acoustic, criterion, diagnostic and upgrade selectors
  remain under the one selected-task block;
- `data-speaking-diagnostic-filter` and `data-speaking-feature` identity,
  scoped focus/filter behavior, `hidden` tabpanel behavior and responsive
  Speaking selectors were reread;
- focused tests were updated for Speaking `questionId` forwarding,
  selected-question-only score/evidence, bilingual family/chip binding,
  sentinel non-rendering, owner-bound playback metadata, acoustic
  `NOT_SCORABLE`, future governed direct-audio seam and exact four-tab static
  containment.

Deferred selector for the one consolidated Phase 13E validation unit; recorded
only and not run in 13E-04:

```bash
./mvnw "-Dtest=PracticeResultDetailContractTest,PracticeResultPresenterTest,SpeakingFeedbackViewMapperTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates+testResultAccessDeniedForOtherUser+testResultRenderSecurityEscaping+testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm+speakingMediaAndResultRemainIntactWhenRepublishIsBlockedBeforeForeignKeyDelete" test
```

`PRE_PHASE_14` correctness debt remains separate:

- replace the bounded in-code Speaking descriptor/family seam with the full
  versioned Korean Speaking construct/task/evidence/descriptor registry and
  explicit canonical/legacy compatibility classification;
- bind the strict provider schema, prompt, normalizer/rule identity,
  transcript source, media version and cache/fingerprint to the persisted
  `AssessmentPolicyBundle`, including truthful target/evidence observability;
- define the governed direct-audio capability contract without enabling it
  prematurely, and add the immutable lecturer-reference question-version
  round trip before any teacher reference may render;
- reconcile these changes only in the documented Phase 14 production
  correctness gate. No schema, migration, rebaseline or database action
  belongs to 13E-04.

`PRE_PHASE_15_RELEASE_CLOSURE_GATE` after 14F still owns:

- a production API/provider path that directly receives authorized learner
  audio rather than only speech-to-text, plus acoustic feature provenance,
  calibration and explicit failure/low-confidence behavior;
- Korean-SME approval for pronunciation, rhythm, fluency, delivery,
  sociolinguistic/register/honorific/pragmatic interpretation and the expanded
  registry;
- golden/adversarial audio-text corpora, repeatability, fairness, retention/
  deletion/privacy proof and canonical Vietnamese/Korean seed Manual UAT.

Static risk remains: no Java compiler, template renderer, CSS/browser engine or
test runner has executed this unit. Current Speaking feedback does not contain
an authoritative standalone rewrite list, so the Detail exposes only
significant phrase rewrites recoverable from validated exact-span corrections.
Audio playback availability still does not make acoustic scoring available.

No test, compile, build, lint, application startup, browser, provider,
database, migration, Docker or Git command was run during 13E-04.

Next action: independent static acceptance of `13E-04`, followed by the single
consolidated Phase 13E validation gate when orchestration authorizes it.

## 11. 13E-05 Result Detail Reconciliation / Static Acceptance Ledger

Implemented and statically reread `2026-07-24`.

Slice state: `13E-05 = IMPLEMENTED_PENDING_PHASE_VALIDATION`.

No Phase 13E static blocker remains after the bounded fixes recorded below.
This slice does not claim `ACCEPT_STATIC`, because production and focused-test
files changed and the one consolidated Phase 13E validation gate has not run.

Production files changed:

- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java`;
- `src/main/resources/templates/practice/result-detail-speaking.html`.

Focused contract-test file changed:

- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`.

This ledger file was also changed. The following audited files required no
code change:

- `src/main/resources/templates/practice/result-detail.html`;
- `src/main/resources/templates/practice/result-detail-writing.html`;
- `src/main/resources/static/js/practice-result.js`;
- `src/main/resources/static/css/practice-result-detail.css`;
- `src/main/resources/templates/practice/result-detail-objective.html`;
- the Result Detail assembler, objective presenter, descriptor registries,
  compatibility readers and related typed DTOs.

### Reconciled active dispatch and shared UI boundary

- `PracticeController.attemptResultDetail` delegates to the read-only
  `PracticeResultDetailAssembler` and dispatches exactly three active views:
  objective Reading/Listening, Writing and Speaking. The old generic
  `PracticeViews.RESULT_DETAIL` and
  `PracticeViews.READING_LISTENING_RESULT_DETAIL` constants have no production
  caller;
- `PracticeResultDetailAssembler` reuses the owned immutable attempt context,
  permits `questionId` only for Writing/Speaking and requires exactly one
  skill presenter. `PracticeDtos.ResultDetailPayload` remains sealed to exactly
  the three active screen payloads;
- `practice-result.js` parses no JSON and derives no score, criterion, label or
  count. It only owns accessible tab movement and scoped diagnostic
  filter/focus interaction. Writing/Speaking each retain exactly four
  accessible tabs. The scoped CSS stacks the two-column layouts, releases the
  sticky source panel on narrow viewports, permits tab scrolling and contains
  no active fixed footer that could cover mobile content.

### Legacy JSON reconciliation

Legacy JSON remains readable only at explicit compatibility boundaries:

1. Objective Result Detail reads the typed current R/L explanation schema for
   `SINGLE_CHOICE`, `FILL_BLANK` and `TRUE_FALSE_NOT_GIVEN`. It also dual-reads
   the prior v2 single-choice schema only after binding fingerprint,
   discriminator, immutable input and exact evidence checks. This is bounded,
   read-only compatibility, not an answer or score source of truth.
2. Immutable historical assessment rows may still adapt legacy
   `options_json`/`answer_key`/plain learner answers when the typed snapshot
   fields do not exist. The locked question version remains authoritative and
   the adapter performs no new write.
3. Writing still parses stored entries so old rows can be identified. Before
   this reconciliation, a question-keyed legacy entry with plausible numeric
   fields and a matching `task_type` could be promoted into current Detail
   score criteria and upgrade artifacts. The presenter now requires all of
   `TASK_NATIVE_RUBRIC_V1`, `KSH_WRITING_EVALUATOR_V2`, explicit
   `score_available=true`, exact task type, `EVALUATED`, and source
   `PROVIDER`/`CACHE`. Any score-bearing entry outside that contract is
   `LEGACY_UNVERIFIED`: score, criteria, summary, diagnostics and upgrade
   artifacts are hidden, and the attempt-level displayed score is unavailable.
4. Speaking current typed evidence is separated from old Speaking and governed
   legacy-essay mirrors. Legacy rows may contribute only explicitly labelled
   read-only compatibility text/state; all legacy score material remains
   `LEGACY_UNVERIFIED` and cannot become current acoustic, language or
   holistic scoring evidence.
5. The old generic `result-detail.html`, `rl-result-detail.html`, legacy
   service result methods and generic display-JSON readers still contain raw
   compatibility parsing. Static production-caller tracing found this cluster
   unreachable from the active Result Detail controller dispatch. It is
   verified-dead/read-only compatibility inventory, not a canonical runtime
   screen; deletion remains `P15-COMP-10` only after consolidated 13E
   validation proves the replacement route.

Why the old JSON is still readable: immutable historical attempts and
explanation artifacts must remain inspectable long enough to classify retained
data and avoid inventing a migration. It is not trusted as current KSH
assessment truth. Before Phase 14A, `PRE-05` must assign every retained payload
an explicit `KEEP`, bounded `DUAL_READ`, `MIGRATE/REBIND`, `REGENERATE`, or
`DELETE_UAT_ONLY` disposition with expiry/removal evidence. The dead generic
route/template/parser cluster is removed under `P15-COMP-10` after the
consolidated 13E gate and before Phase 15 Manual UAT; payload adapters that
receive no approved retained-data disposition must expire or be removed at
the pre-14 gate rather than becoming permanent compatibility.

### Speaking reconciliation

- the template renders only the block whose `questionId` equals
  `activeQuestionId`. Each non-selected task now carries an empty prompt in
  addition to empty submission/summary and `NAVIGATION_ONLY` states; the DTO
  constructor now enforces that navigation-only boundary;
- the four active tabs remain Overview, Strengths, Needs and Upgrade. Only the
  selected task can own six ordered criterion rows, evidence, diagnostics and
  upgrade artifacts;
- `AUDIO_SUBMITTED` is consumed only as submission-source state and is rejected
  by both learner-text and transcript DTO boundaries. It cannot render as a
  transcript;
- transcript-only evidence keeps fluency and pronunciation/delivery
  `NOT_SCORABLE`, with null score/max. No acoustic chip, task total or
  holistic score is produced. UI copy now calls the four language rows
  current KSH criteria rather than “official” criteria;
- the existing direct-audio API/scorer, acoustic provenance and calibration
  obligation remains `PRE-01` /
  `PRE_PHASE_15_RELEASE_CLOSURE_GATE`. Playback proves only an owned recording
  source; it does not prove that the evaluator heard audio.

### Writing reconciliation

- official task-native content/task-achievement, organization/coherence and
  language/expression score cards occur only in Overview. Strengths and Needs
  contain only validated, non-additive diagnostic groups/chips;
- score and chip labels are server-owned Vietnamese/Korean descriptors.
  Provider English category/label/parent claims do not render as display-chip
  authority;
- the active Detail template and newly tightened presenter cannot display or
  promote a local 1-9 band. A regression fixture now proves a
  `LEGACY_BAND_V1` row with otherwise plausible current metadata remains
  `LEGACY_UNVERIFIED`.

Blocking pre-14 handoff: `P15-COMP-18` is still open outside the 13E UI slice.
`WritingEvaluationNormalizer`, `WritingScoreMatrix`,
`WritingScoringPolicy`, provider fallback/rule/cache paths and old
`PracticeService` aggregation still contain local 1-9 compatibility
production code. Although the active Result/Detail presenter now refuses that
material, these producers/readers must be removed from canonical prompt,
provider acceptance, new-write, runtime and UI paths before Phase 14A, after
the `PRE-05` retained-data decision. This is release/pre-14 blocking debt and
must not be normalized into a permanent dual-read path.

### Reading/Listening reconciliation

- Result Detail is not limited to wrong-option elimination. The sealed typed
  contract and template render all three current variants: per-option
  rationale/evidence for single choice, per-blank accepted-value and
  context/semantic/grammar/register explanation for fill blank, and
  relation/missing-information/alternative reasoning for TFNG;
- exact text/transcript spans, image-region provenance and approved
  Vietnamese translations remain type checked and bound to immutable
  artifacts;
- typed variant coverage is sufficient for current 13E scope. The remaining
  gap is the Korean-native construct/learning-lens registry: current artifacts
  do not carry an approved typed construct code, so construct chips correctly
  fail closed as `DEFERRED_PRE_PHASE_14_REGISTRY`. `PRE-14` owns the versioned
  VI/KO registry and any later truthful chip exposure.

Deferred selector proposed for the single consolidated Phase 13E validation
unit; recorded only and not run in 13E-05:

```bash
./mvnw "-Dtest=ObjectiveResultDetailTypeNativeContractTest,QuestionExplanationReadServiceTest,WritingFeedbackCompatibilityReaderTest,SpeakingFeedbackCompatibilityReaderTest,WritingDiagnosticDescriptorRegistryTest,PracticeResultDetailContractTest,PracticeResultPresenterTest,SpeakingFeedbackViewMapperTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testSubmitWritingAttemptAndGetResult+testResultAccessDeniedForOtherUser+testResultRenderSecurityEscaping+testWritingResultDetailRendersPerQuestionReEvaluateForm+testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion+testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak+testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates+testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm+speakingMediaAndResultRemainIntactWhenRepublishIsBlockedBeforeForeignKeyDelete" test
```

Static reread covered controller/view dispatch, assembler ownership, sealed DTO
invariants, presenter trust and task selection, template containment, all four
Writing/Speaking tabs, bilingual descriptor use, browser interaction-only JS,
responsive CSS, typed R/L variant rendering, legacy production-caller tracing
and focused fixture consistency.

No test, compile, build, lint, application startup, browser, provider,
database, migration, Docker or Git command was run during 13E-05.

Historical 13E-05 handoff (superseded by Section 12): independent static
acceptance, then the single consolidated Phase 13E validation gate. Do not
start Phase 14 or Phase 15 from this ledger.

## 12. 13E-03..05 Independent Static Acceptance And Phase Readiness

Accepted statically and reconciled `2026-07-24`.

Phase state: `READY_FOR_PHASE_VALIDATION`.

The implementation handoffs for `13E-03`, `13E-04` and `13E-05` were reread
independently against the approved live-log boundary. Together with the earlier
`ACCEPT_STATIC` results for `13E-01` and `13E-02`, every approved Phase 13E
slice now has static acceptance. No P0/P1 trust, dispatch, evidence or score
blocker remains.

The final static pass found and fixed one learner-facing P2 localization leak:
Objective Detail exposed raw provenance/TFNG enum values and Writing Detail
exposed the raw feedback-state code. The backend DTO now supplies Vietnamese
and Korean display labels; the templates render those labels and do not map
technical values in browser JavaScript. Canonical machine identifiers remain
unchanged for validation and persistence.

Static acceptance evidence:

- the controller dispatches exactly one typed Objective R/L, Writing or
  Speaking Detail screen through the read-only assembler;
- Objective Detail supports `SINGLE_CHOICE`, `FILL_BLANK` and
  `TRUE_FALSE_NOT_GIVEN`, with bounded v2 compatibility restricted to
  single-choice immutable historical artifacts;
- Writing trusts score, criteria, diagnostics and upgrade artifacts only for
  the current task-native contract/engine/task/status/source markers; local
  band 1–9 material is `LEGACY_UNVERIFIED` and invisible on the canonical
  Detail screen;
- Speaking renders one active immutable question, treats non-selected tasks as
  navigation-only, never treats `AUDIO_SUBMITTED` as transcript text and keeps
  both acoustic criteria `NOT_SCORABLE` without governed direct-audio scoring;
- the canonical JavaScript owns only accessible tabs and diagnostic
  filter/focus behavior; it parses no provider JSON and computes no score,
  criterion, label or count;
- the raw-JSON generic templates/services have no active production Result
  Detail caller. They remain time-bounded `P15-COMP-10` cleanup debt after this
  consolidated gate, not a second canonical screen.

Exact Phase 13E production files in the validation unit:

- `src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationReadService.java`;
- `src/main/java/com/ksh/features/practice/ai/readinglistening/ReadingListeningExplanationClient.java`;
- `src/main/java/com/ksh/features/practice/ai/writing/WritingFeedbackViewMapper.java`;
- `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringPolicy.java`;
- `src/main/java/com/ksh/features/practice/controller/PracticeController.java`;
- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/result/ObjectiveResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultAssembler.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultDetailAssembler.java`;
- `src/main/java/com/ksh/features/practice/result/PracticeResultDetailPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/ResultDetailDescriptorRegistry.java`;
- `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/result/WritingDiagnosticDescriptorRegistry.java`;
- `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java`;
- `src/main/java/com/ksh/features/practice/web/PracticeModelAttributes.java`;
- `src/main/java/com/ksh/features/practice/web/PracticeViews.java`;
- `src/main/resources/templates/practice/result-detail-objective.html`;
- `src/main/resources/templates/practice/result-detail-speaking.html`;
- `src/main/resources/templates/practice/result-detail-writing.html`;
- `src/main/resources/static/css/practice-result-detail.css`;
- `src/main/resources/static/js/practice-result.js`.

Exact focused contract files in the validation unit:

- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java`;
- `src/test/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationReadServiceTest.java`;
- `src/test/java/com/ksh/features/practice/ai/readinglistening/ReadingListeningTypedClientContractTest.java`;
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingFeedbackCompatibilityReaderTest.java`;
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingFeedbackViewMapperTest.java`;
- `src/test/java/com/ksh/features/practice/ai/writing/WritingFeedbackCompatibilityReaderTest.java`;
- `src/test/java/com/ksh/features/practice/ai/writing/WritingFeedbackViewMapperTest.java`;
- `src/test/java/com/ksh/features/practice/ai/writing/WritingScoringPolicyTest.java`;
- `src/test/java/com/ksh/features/practice/result/ObjectiveResultDetailTypeNativeContractTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultDetailContractTest.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/result/WritingDiagnosticDescriptorRegistryTest.java`;
- `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java`.

The current-source document reconciliation also covers:

- `CODEX_PRACTICE_WORKFLOW.md`;
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`;
- this live log;
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
- `docs/PRACTICE_SINGLE_SCOPE_REDUCTION_AUDIT.md`;
- `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`;
- `docs/operations/practice-ai-cache-retention-runbook.md`;
- `docs/research-input/phase-13-14-prep-ui-ux-research-checkpoint.md`.

One consolidated Phase 13E validation sequence is now authorized:

1. `git diff --check`;
2. one JDK 17 compile:
   `./mvnw -DskipTests compile`;
3. one focused selector:

```bash
./mvnw "-Dtest=ObjectiveResultDetailTypeNativeContractTest,QuestionExplanationReadServiceTest,WritingFeedbackCompatibilityReaderTest,SpeakingFeedbackCompatibilityReaderTest,WritingDiagnosticDescriptorRegistryTest,PracticeResultDetailContractTest,PracticeResultPresenterTest,SpeakingFeedbackViewMapperTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testSubmitWritingAttemptAndGetResult+testResultAccessDeniedForOtherUser+testResultRenderSecurityEscaping+testWritingResultDetailRendersPerQuestionReEvaluateForm+testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion+testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak+testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates+testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm+speakingMediaAndResultRemainIntactWhenRepublishIsBlockedBeforeForeignKeyDelete" test
```

No full suite, browser QA, provider call or Phase 14/15 action is part of this
gate. If the consolidated gate fails, analyze the entire failure set, make one
grouped correction pass and rerun this same validation unit once.

## 13. Consolidated Phase Validation Result

Validated `2026-07-24`.

Final phase state: `COMPLETE_FOCUSED_GATE_GREEN`.

`13E-01..05` state: `IMPLEMENTED_AND_FOCUSED_TESTED`.

Overall Phase 13 remains open. This gate does not authorize Phase 14, Phase 15,
the pre-14 correctness gate or any `P15-COMP` cleanup.

### Environment and exact validation commands

- macOS workspace `/Users/toanlamsaoduocc/ksh`;
- OpenJDK `17.0.19`, with
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17`;
- MySQL `9.7.1`;
- the wrapper file has no executable bit, so a literal `./mvnw` preflight
  returned exit `126` before Maven started. All actual Maven invocations used
  the repository wrapper through `bash mvnw`; this changed no source or Git
  metadata;
- no full suite, browser QA, standalone application startup, Docker, live
  provider/API call or Git stage/commit/push/pull/merge/rebase ran.

The ordered gate and correction-cycle commands were:

```bash
git diff --check

env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  bash mvnw -DskipTests compile

env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  bash mvnw "-Dtest=ObjectiveResultDetailTypeNativeContractTest,QuestionExplanationReadServiceTest,WritingFeedbackCompatibilityReaderTest,SpeakingFeedbackCompatibilityReaderTest,WritingDiagnosticDescriptorRegistryTest,PracticeResultDetailContractTest,PracticeResultPresenterTest,SpeakingFeedbackViewMapperTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testSubmitWritingAttemptAndGetResult+testResultAccessDeniedForOtherUser+testResultRenderSecurityEscaping+testWritingResultDetailRendersPerQuestionReEvaluateForm+testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion+testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak+testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates+testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm+speakingMediaAndResultRemainIntactWhenRepublishIsBlockedBeforeForeignKeyDelete" test
```

For the integration reruns, the same selector received an environment-only
`DB_URL` pointing to the newly named disposable
`ksh_phase13e_validation_20260724`; credentials came from the existing ignored
local configuration and were not written to tracked files or logs.

The exact disposable-database command shape was:

```bash
phase13e_db_password=$(sed -n 's/^DB_PASSWORD=//p' \
  src/main/resources/application-local.properties)

MYSQL_PWD="$phase13e_db_password" mysql -h 127.0.0.1 -u root \
  -e "CREATE DATABASE ksh_phase13e_validation_20260724 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  DB_URL='jdbc:mysql://127.0.0.1:3306/ksh_phase13e_validation_20260724?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8' \
  DB_USERNAME=root DB_PASSWORD="$phase13e_db_password" \
  bash mvnw "-Dtest=ObjectiveResultDetailTypeNativeContractTest,QuestionExplanationReadServiceTest,WritingFeedbackCompatibilityReaderTest,SpeakingFeedbackCompatibilityReaderTest,WritingDiagnosticDescriptorRegistryTest,PracticeResultDetailContractTest,PracticeResultPresenterTest,SpeakingFeedbackViewMapperTest,PracticeFunctionalUiContractTest,PracticeIntegrationTest#testSubmitWritingAttemptAndGetResult+testResultAccessDeniedForOtherUser+testResultRenderSecurityEscaping+testWritingResultDetailRendersPerQuestionReEvaluateForm+testWritingResultDetailInvalidQuestionIdFallsBackToFirstQuestion+testWritingResultDetailForeignQuestionIdFallsBackWithoutLeak+testPublishedLegacySpeakingEssayStillSubmitsRendersAndReEvaluates+testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm+speakingMediaAndResultRemainIntactWhenRepublishIsBlockedBeforeForeignKeyDelete" test

MYSQL_PWD="$phase13e_db_password" mysql -h 127.0.0.1 -u root \
  -e "DROP DATABASE ksh_phase13e_validation_20260724;"
```

### Failure analysis and grouped correction cycles

The initial actual compile compiled 595 production sources with Java release
17 and found two errors with one root cause: `PracticeDtos` used `Map` and
`LinkedHashMap` without importing them. One grouped patch added those imports.
The next compile passed.

The first focused selector against the configured
`ksh_phase13e_result_ui` reported `118` tests: `108` passed, `1` failed,
`9` errored and `0` skipped. The complete failure set had two root causes:

1. `PracticeResultPresenterTest` proved that pending Writing feedback was
   incorrectly classified as task-identity unavailable before its pending
   state was considered. `WritingResultPresenter` now maps pending, failed and
   unavailable feedback to the non-score-bearing feedback-unavailable state
   before applying the strict current-score contract check; unverified legacy
   score material remains hidden.
2. All nine selected `PracticeIntegrationTest` cases failed before assertions
   because the configured database reports Flyway V44 but lacks
   `tests.media_type`. That stale database was neither repaired nor reused as
   validation proof.

After that grouped correction, `git diff --check` and the JDK 17 compile
passed. A fresh disposable schema migrated successfully from V1 through V44;
Flyway recorded `44` successful and `0` failed migrations,
`tests.media_type` existed exactly once, and Hibernate schema validation
completed. The full selector then reported `118` tests: `117` passed, `1`
failed, `0` errored and `0` skipped. The only failure was a stale integration
assertion that still expected the local-band Q51 rubric label to render.

That assertion contradicted the accepted Phase 13E contract: the mock result is
not a current `EVALUATED` provider/cache task-native score and must remain
`LEGACY_UNVERIFIED`. The correction asserts the unavailable/unverified state
and the absence of the local-band criterion instead. It does not change the
mock producer or execute `P15-COMP-18`/pre-14 debt.

### Final evidence

- final `git diff --check`: pass, no output;
- JDK 17 compile after the production correction: `BUILD SUCCESS`, 595
  production sources, zero compile errors;
- final focused selector: `118/118` passed, `0` failures, `0` errors,
  `0` skipped;
- selector composition: `109/109` unit/static cases and `9/9` selected
  integration cases;
- current-schema evidence before cleanup: Flyway max version `44`, `44`
  successful migrations, `0` failed migrations and one
  `tests.media_type` column;
- disposable database cleanup: confirmed absent after `DROP DATABASE`
  (`remaining=0`);
- blocker: none.

Validation corrections changed exactly:

- `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java`;
- `src/test/java/com/ksh/features/practice/PracticeIntegrationTest.java`.

Validation documentation changed exactly:

- this live log;
- `CODEX_PRACTICE_WORKFLOW.md`.

No unrelated/untracked file was opened, modified, staged or removed. No file
was staged and no commit or push was created.
