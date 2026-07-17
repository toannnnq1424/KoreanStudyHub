# Practice Jira Synchronization Manifest

Status: `STEP_9_COMPLETE`

Project: `SCRUM`

Board: `https://nhhuy322003.atlassian.net/jira/software/projects/SCRUM/boards/1/backlog`

Date prepared: 2026-07-17

## 1. Scope And Integrity Rules

- This synchronization is limited to module `/practice`.
- Do not create or update Flashcard, lesson, iConstant, class-management or any
  other product-module issue in this batch.
- `SCRUM-363 Tests & Assignments` is the classroom test/assignment feature and
  is explicitly outside `/practice`. Do not update it, add Practice subtasks to
  it or use it as a Practice parent.
- `SCRUM-321 Implement Optional AI Prototype/Exam Readiness Refinement` is also
  not a Practice parent. Generic matches on Test, AI, Writing or Exam are not
  reusable unless the issue explicitly owns the `/practice` module.
- Search for an existing issue before every create action.
- Historical issues must say `retrospective/backfilled` in the summary or first
  description paragraph. Their Jira creation date must remain the real creation
  date; evidence records the actual implementation date and commit instead.
- Use Scrum 3 for completed Phase 13A-13D work only when Jira permits the real
  sprint relationship. Never alter a closed sprint history to simulate earlier
  creation.
- Use Scrum 4 for pre-13E, 13E-13H and the Step 8 Listening correction.
- If a closed sprint cannot accept a new retrospective issue, leave the issue in
  the backlog/current sprint and record `Intended historical boundary: Scrum 3`
  in its description.
- Confirm the site's exact issue type names and Sprint field before writes.
- Task is the parent planning/delivery unit. Bugs remain Bugs. Implementation
  units below a Task use the site's supported Sub-task type.

## 2. Duplicate Search

Run the broad query first:

```text
project = SCRUM AND (summary ~ "Practice" OR text ~ "/practice") ORDER BY created ASC
```

Then run focused queries before each candidate:

```text
project = SCRUM AND summary ~ "13A 13B 13C"
project = SCRUM AND summary ~ "13D result explanation"
project = SCRUM AND summary ~ "architecture use case class sequence"
project = SCRUM AND summary ~ "listening sound check preflight"
project = SCRUM AND summary ~ "timer 00:00"
project = SCRUM AND summary ~ "practice progress 500"
project = SCRUM AND summary ~ "PDF crop"
project = SCRUM AND summary ~ "13E result detail"
project = SCRUM AND summary ~ "13F progress recovery"
project = SCRUM AND summary ~ "13G 13H accessibility stabilization"
```

For a strong match, update/comment the existing issue instead of creating a
duplicate. Record every reused key in Section 5.

Read-only result on 2026-07-17:

- exact backlog search for `Practice` returned zero work items;
- focused searches for Speaking, Listening, PDF, Excel, Progress and Timer
  returned no Practice-owned issue;
- broader Test/Writing/Exam searches returned unrelated classroom testing,
  system testing and optional AI tickets;
- the user confirmed those generic tickets are different features, so this
  batch must create an independent `[Practice]` hierarchy.

## 3. Candidate Hierarchy

### P1 - Phase 13A-13C learner experience

- Type: Task
- Intended sprint: Scrum 3
- Delivery state: Done, retrospective/backfilled
- Summary: `[Practice][Backfill][13A-13C] Catalog, immutable entry and skill-native players`
- Actual work: 2026-07-14 through 2026-07-15
- Evidence commits: `25b5ffc`, `3d5b944`, `eaf55f8`, `c3ba3a9`
- Evidence docs: `CODEX_PRACTICE_WORKFLOW.md` Phase 13A-13C rows and
  `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`

Sub-tasks:

1. `[Practice][13A] Build bounded catalog, access and learner state`
2. `[Practice][13B] Lock set/test/skill entry and device preflight`
3. `[Practice][13C] Deliver Reading, Listening, Writing and Speaking players`
4. `[Practice][13C] Stabilize immutable media, editor and attempt contracts`

Acceptance already evidenced:

- bounded learner catalog and real progress state;
- immutable version lock before player delivery;
- skill-native players and preflight boundaries;
- focused gates plus the recorded 1321-test full-suite correction.

### P2 - Phase 13D result and explanation lifecycle

- Type: Task
- Intended sprint: Scrum 3
- Delivery state: Done, retrospective/backfilled
- Summary: `[Practice][Backfill][13D] Skill-native result and immutable explanation lifecycle`
- Actual work: 2026-07-15 through 2026-07-17
- Evidence commits: `bcc1467`, `a089fd1`, `da350b5`
- Evidence docs: Phase 13D rows in `CODEX_PRACTICE_WORKFLOW.md` and Section 6.9
  in `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`

Sub-tasks:

1. `[Practice][13D] Build canonical result envelope and three presenters`
2. `[Practice][13D] Persist immutable Reading/Listening explanation artifacts`
3. `[Practice][13D] Keep result GET read-only and add retry/reconciliation`
4. `[Practice][13D] Inventory compatibility and premium-seed cleanup debt`
5. `[Practice][13D] Run consolidated 311/311 and 1642/1642 validation`

Acceptance already evidenced:

- objective Reading/Listening overview;
- Korean-native Writing overview;
- holistic Speaking overview without per-question overview analysis;
- content-fingerprinted artifact reuse and immutable version bindings;
- JDK 17 compile, focused gate and full suite green.

### B1 - Timer starts at 00:00

- Type: Bug
- Intended sprint: Scrum 3
- Delivery state: Fixed, retrospective/backfilled
- Summary: `[Practice][Backfill][Bug] Attempt opens with timer at 00:00`
- Evidence: Phase 13D implementation row and commit `bcc1467`
- Expected: a new timed attempt starts at the section duration and a resumed
  attempt restores its immutable remaining time.
- Fixed result: the Step 8 Chrome journey opened Listening attempt `13006` at
  `30:00`.

Sub-task:

1. `[Practice][Fix] Restore immutable timed-attempt countdown and resume state`

### B2 - Progress route returns HTTP 500

- Type: Bug
- Intended sprint: Scrum 3
- Delivery state: Fixed, retrospective/backfilled
- Summary: `[Practice][Backfill][Bug] /practice/progress returns HTTP 500`
- Evidence: Phase 13D implementation row and commit `bcc1467`
- Expected: bounded real aggregates render without decorative percentages or
  an unbounded attempt scan.

Sub-task:

1. `[Practice][Fix] Bound progress aggregation and render real attempt state`

### B3 - PDF crop action disappeared

- Type: Bug
- Intended sprint: Scrum 3
- Delivery state: Fixed, retrospective/backfilled
- Summary: `[Practice][Backfill][Bug] PDF workspace loses visible crop action`
- Evidence: Phase 13D implementation row and commit `bcc1467`
- Expected: crop is visible, tied to the current page/region and invalidated when
  its source becomes stale.

Sub-task:

1. `[Practice][Fix] Restore source-aware PDF crop action in workspace`

### P3 - Pre-13E architecture and deterministic fixtures

- Type: Task
- Intended sprint: Scrum 4
- Delivery state: Done in working tree
- Summary: `[Practice][Pre-13E] Capability architecture and four-skill result fixtures`
- Evidence docs:
  - `docs/architecture/practice/KSH_PRACTICE_USE_CASE_SPECIFICATIONS.docx`
  - `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE.drawio.xml`
  - `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE_MANIFEST.md`
  - `docs/architecture/practice/PRACTICE_PHASE_13E_RESULT_FIXTURES.md`
  - `docs/PRACTICE_PHASE_13E_LIVE_CHANGE_LOG.md`

Sub-tasks:

1. `[Practice][Architecture] Specify capability-based Use Cases`
2. `[Practice][Architecture] Create one class diagram per capability`
3. `[Practice][Architecture] Create sequence diagrams per Practice Use Case`
4. `[Practice][Fixtures] Load deterministic Result and Detail data for R/L/W/S`
5. `[Practice][QA] Review eight authenticated result URLs`

Architecture description required in Jira:

- ten bounded capabilities: CAT, AUT, XLS, PDF, PLY, RLE, WRT, SPK, RES and PRG;
- one class diagram for each capability;
- three formal Use Cases and three sequence diagrams for each capability;
- planned nodes are labelled 13E/13F/13H and are not presented as current code;
- generated page count is incidental and is not an acceptance requirement.

### B4 - Listening preflight loops back silently

- Type: Bug
- Intended sprint: Scrum 4
- Delivery state: Fixed in working tree
- Summary: `[Practice][Bug] Listening sound check loops without entering test`
- Root cause: canonical seed section `2` had no delivery audio; the service
  failed closed and Test Detail hid the flash error. The browser never reached
  the preflight script, so waiting for `ended` was not the original cause.
- Evidence: `PRE13E-F15`, Flyway V38, focused `101/101` gate and Chrome attempt
  `13006`.

Sub-tasks:

1. `[Practice][Listening] Reproduce and separate media/session/UI causes`
2. `[Practice][Listening] Add immutable seed check audio and exact allowlist`
3. `[Practice][Listening] Render visible preflight failure feedback`
4. `[Practice][Listening] Prove playing-event unlock and 30:00 player entry`

Acceptance already evidenced:

- deterministic 1.8-second WAV loads with no media error;
- confirmation unlocks while audio is still playing;
- the learner does not wait for `ended`;
- confirmation starts an immutable Listening attempt;
- authored invalid media still fails closed.

### P4 - Phase 13E skill-native result evidence

- Type: Task
- Intended sprint: Scrum 4
- Delivery state: To Do
- Summary: `[Practice][13E] Implement skill-native Result Detail evidence`
- Evidence: `PRE13E-F10..F14` in the live log.

Sub-tasks:

1. `[Practice][13E][R/L] Separate learner answer, official key and explanation`
2. `[Practice][13E][Writing] Preserve official Korean denominators and evidence`
3. `[Practice][13E][Speaking] Preserve all six criteria without Writing labels`
4. `[Practice][13E][A11y] Complete tab/tabpanel semantics and landmarks`
5. `[Practice][13E][Mobile] Prevent fixed footer from covering final actions`

### P5 - Phase 13F progress and recovery

- Type: Task
- Intended sprint: Scrum 4
- Delivery state: To Do
- Summary: `[Practice][13F] Implement real progress drill-down and recovery UX`

Sub-tasks:

1. `[Practice][13F] Render bounded real aggregates with confidence and recency`
2. `[Practice][13F] Add skill/status/date filters and attempt drill-down`
3. `[Practice][13F] Expose pending/failed/retry recovery states`

### P6 - Phase 13G-13H closure

- Type: Task
- Intended sprint: Scrum 4
- Delivery state: To Do
- Summary: `[Practice][13G-13H] Responsive, accessibility and stabilization gate`

Sub-tasks:

1. `[Practice][13G] Run UTF-8, icon, reduced-motion and large-catalog sweep`
2. `[Practice][13G] Complete responsive and semantic accessibility fixes`
3. `[Practice][13H] Run browser/device journeys and answer-leak checks`
4. `[Practice][13H] Run static audit, focused gate and final full suite`

## 4. Write Procedure

1. Authenticate through the Atlassian connector or the intended Jira account.
2. Read project `SCRUM` issue types, Sprint field and Scrum 3/4 metadata.
3. Run the broad duplicate query.
4. For each candidate, run its focused query and inspect any strong match.
5. Reuse/update strong matches. Create only missing parents.
6. Create missing Sub-tasks only after the parent key is known.
7. Assign Scrum 3/4 only where Jira permits the truthful relationship.
8. Transition completed retrospective items only when the workflow exposes a
   valid Done transition; otherwise leave their evidence and state explicit.
9. Re-query all created/reused keys and record the final state below.
10. Update the Phase 13E live log before declaring Step 9 complete.

Read-only Jira metadata confirmed before writes:

- cloud: `2d879d5c-649d-4ef3-bac8-da579c4d9946`;
- project: `SCRUM` (`SEP490_G103`);
- issue types: `Task`, `Bug` and `Subtask`;
- Sprint field: `customfield_10020`;
- Scrum 3: id `135`, state `active`, 2026-07-07 through 2026-07-15;
- Scrum 4: id `136`, state `future`;
- proposed batch: 10 independent Practice parents and 33 Practice subtasks;
- proposed state: 28 completed/backfilled items transitioned to Done and 15
  future Phase 13E-13H items left To Do;
- proposed placement: 17 items in Scrum 3 and 26 items in Scrum 4.

Subtask placement rule discovered during the write:

- Jira rejects `customfield_10020` on Subtasks and makes them inherit the
  parent's Sprint;
- the rejected request created no partial Subtasks;
- all Subtask creates therefore set only `parent`, labels and content.

## 5. Synchronization Results

Read-only reconciliation is complete. Atlassian Rovo is authenticated, the
project metadata and Sprint ids are confirmed, and no existing issue explicitly
owns `/practice`. `SCRUM-363` and `SCRUM-321` are recorded as out-of-scope false
matches and will not be modified.

No issue has yet been created, edited, transitioned or assigned to a sprint.
The user confirmed the single reviewed 43-item write batch on 2026-07-17.
Execution and post-write reconciliation are in progress.

Created so far:

- `SCRUM-438` - P1 Phase 13A-13C parent in Scrum 3.
- `SCRUM-439` - B1 timer bug in Scrum 3.
- `SCRUM-440` - P2 Phase 13D parent in Scrum 3.
- `SCRUM-441` - B3 PDF crop bug in Scrum 3.
- `SCRUM-442` - B2 progress HTTP 500 bug in Scrum 3.
- `SCRUM-443` - P3 pre-13E architecture/fixture parent in Scrum 4.
- `SCRUM-444` - B4 Listening preflight bug in Scrum 4.
- `SCRUM-445` - P4 Phase 13E parent in Scrum 4.
- `SCRUM-446` - P5 Phase 13F parent in Scrum 4.
- `SCRUM-447` - P6 Phase 13G-13H parent in Scrum 4.
- `SCRUM-448..SCRUM-451` - P1 Phase 13A-13C Subtasks.
- `SCRUM-452..SCRUM-456` - P2 Phase 13D Subtasks.
- `SCRUM-457` - B1 timer fix Subtask.
- `SCRUM-458` - B2 progress fix Subtask.
- `SCRUM-459` - B3 PDF crop fix Subtask.
- `SCRUM-460..SCRUM-464` - P3 architecture, fixtures and URL review Subtasks.
- `SCRUM-465`, `SCRUM-466`, `SCRUM-468`, `SCRUM-470` - B4 Listening fix
  Subtasks.
- `SCRUM-467`, `SCRUM-469`, `SCRUM-471..SCRUM-473` - P4 Phase 13E Subtasks.
- `SCRUM-474`, `SCRUM-475`, `SCRUM-477` - P5 Phase 13F Subtasks.
- `SCRUM-476`, `SCRUM-478..SCRUM-480` - P6 Phase 13G-13H Subtasks.

Creation result: the approved 43-item hierarchy exists as
`SCRUM-438..SCRUM-480`.

Final reconciliation:

- total: 43/43 work items;
- issue types: 6 Tasks, 4 Bugs and 33 Subtasks;
- Scrum 3: 17 items, all transparently retrospective/backfilled and Done;
- Scrum 4: 26 items, including 11 completed pre-13E/Listening items and 15
  planned Phase 13E-13H items;
- status split: 28 Done and 15 To Do;
- every Subtask has the intended Practice parent and inherits its Sprint;
- `SCRUM-363`, `SCRUM-321` and all other existing non-Practice issues remain
  unmodified;
- post-write JQL returned exactly `SCRUM-438..SCRUM-480` with no missing or
  duplicate key.
