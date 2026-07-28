# Practice Phase 13G Responsive, Accessibility And Performance Live Change Log

Opened: `2026-07-28`

Status: `COMPLETE_FOCUSED_GATE_GREEN`

Branch: `codex/practice-phase13g-responsive-a11y-performance`

Baseline:
`65328e9fae5201be2f154c90739bcb78f1034e4d`

Validation unit: `PHASE_13G_RESPONSIVE_A11Y_PERFORMANCE`

## 1. Entry And Safety Closure

Phase 13C3 is `CLOSED_VERIFIED_MERGED`. Both final independent audits accepted
exact SHA `420a9a905cd202116158802eeaff799aab29e4b5`; PR #26 merged into
`main` as `65328e9fae5201be2f154c90739bcb78f1034e4d`.

The Phase 13G branch, local `main` and `origin/main` all started at that exact
merge SHA. `stash@{0}` remains
`pre14-sidecars-before-main-integration-20260727` and must not be applied or
popped in 13G. The following excluded user paths may remain untracked and are
not part of this phase:

- `.java-version`;
- `.tmp-ksh-audio-generator.html`;
- `.tmp/`;
- `SEP490_G103_KoreanHub.drawio.xml`;
- `openspec-temp/`;
- `scripts/docs/__pycache__/`.

The ten user-locked authority documents were read completely before this file
or production code was changed. The literal Practice Markdown inventory was
recorded with `rg --files docs | rg 'PRACTICE|practice'`; the Phase 13D/13E
Result/Detail logs, architecture manifest, fixture contract and current KSH
baseline README were then loaded as directly relevant references.

## 2. Locked Scope And Non-Scope

Phase 13G owns:

- end-to-end UTF-8 and mojibake review for active Vietnamese/Korean surfaces;
- desktop/mobile responsive behavior without control/text overlap;
- focus, label, error-summary, contrast and reduced-motion behavior;
- replacement of emoji-style product icons with the repository's consistent
  Lucide/SVG architecture;
- realistic catalog/query/index review with bounded server-side loading; and
- static PREP/KSH image comparison used only to choose presentation issues
  inside this phase.

Phase 13G does not own Result Detail redesign, scoring, prompt,
subcriterion/taxonomy expansion, provider smoke, direct-audio evaluation,
migration cleanup, 13H security/toolchain work or Pre-14 cleanup. Browser,
device and visual journeys remain
`NOT_RUN_USER_DEFERRED_TO_13H_OR_END_OF_PHASE_13`.

Practice-specific AI/storage and project-wide/Admin AI/storage remain present,
operational and separate. No consumer redirect, commonization, bulk package
move or configuration merge is authorized.

## 3. PREP Image Research Boundary

Every image must be listed and actually opened with an image inspection tool;
filenames alone are not evidence. PREP images are external read-only research
and must never be copied or staged into the repository.

The comparison ledger is organized by:

1. Reading/Listening Result;
2. Writing Result;
3. Speaking Result;
4. Listening Detail;
5. Reading Detail;
6. Writing Detail; and
7. Speaking Detail.

Each evidence-backed observation receives exactly one Phase 13G disposition:

- `ADOPT_KSH_13G`;
- `DEFER_13H_OR_LATER`; or
- `REJECT_PREP_SPECIFIC`.

Assessment, taxonomy or AI gaps found in the images are recorded as
`DEFER_POST_13H_COMPREHENSIVE_AUDIT_OR_PRE15` and are not implemented in
Phase 13G. PREP brand/assets/content/CSS/API/URL, IELTS taxonomy, bands,
criteria and scores are always `REJECT_PREP_SPECIFIC`.

KSH keeps backend-owned Vietnamese/Korean labels, Korean task-native
descriptors, exactly four Writing/Speaking feedback tabs and exactly three
typed Result Detail screen contracts.

## 4. Audit Lanes

| Lane | Boundary | Status |
|---|---|---|
| UTF-8 and icon consistency | Active Practice Java/template/CSS/JS/config copy; distinguish immutable migration/history | `COMPLETE_READ_ONLY` |
| Responsive and accessibility | Result, Detail, Progress, catalog, authoring/import/player surfaces; static source evidence only | `COMPLETE_READ_ONLY` |
| Catalog/query/index scale | Repository/controller/service/query/index/test graph; no fake pagination or N+1 | `COMPLETE_READ_ONLY` |
| PREP/KSH image comparison | All supplied PREP images plus all current KSH baseline images, actually opened | `COMPLETE_READ_ONLY_52_OF_52` |

Findings were de-duplicated before implementation. The inventory and final
verdict passes are read-only. Later correction tasks were separately bounded
to non-overlapping assigned files. No agent has run tests/builds, started the
app, used browser/device QA or mutated Git.

## 5. Implementation And Validation Policy

Individual fixes may form separate logical patches, but the entire approved
Phase 13G scope is one validation unit. Until the implementation inventory is
complete and the whole diff is reconciled, do not run tests, compile, build,
lint, start the application, Docker, migrations or browser journeys.

Only after `READY_FOR_PHASE_VALIDATION`:

1. run `git diff --check`;
2. run one JDK 17 compile/build;
3. run the smallest focused test set covering the whole phase; and
4. run integration tests only when a changed boundary requires them.

If the gate fails, analyze the complete failure set, group by root cause, make
one concentrated correction pass and rerun the same consolidated unit once.
After green validation, create multiple reviewable logical commits and push
the complete series once. Do not squash, rebase or merge `main` in this task.

## 6. Finding And Decision Ledger

### 6.1 Image evidence inventory

The image audit listed and opened every file with the image inspection tool at
original detail. It inspected `47/47` PREP images and `5/5` current KSH
baseline images, including the decomposed-Unicode filename
`vùng chatbot.png`; total evidence is `52/52`. Nothing was inferred from a
filename and no PREP image was copied into the repository.

Folder counts:

| Supplied source | Opened |
|---|---:|
| `4.1.result của r-l` | 2 |
| `4.2. result của writing` | 8 |
| `4.3 result của speaking` | 2 |
| `5.1.result detail của listening` | 7 |
| `5.2.result detail của reading` | 5 |
| `5.3 result detail của writing` | 11 |
| `5.4 result detail của speaking` | 12 |
| KSH Phase 13E current baseline | 5 |

Visual content, rather than folder names, produced the final family counts:
Reading/Listening Result `3`, Writing Result `4`, Speaking Result `8`,
Listening Detail `7`, Reading Detail `6`, Writing Detail `11` and Speaking
Detail `13`. No supplied image is a mobile screenshot, so no runtime mobile
claim is made from this evidence.

### 6.2 Seven-family PREP/KSH comparison ledger

| Family | Evidence-backed pattern | Decision |
|---|---|---|
| Reading/Listening Result | Preserve KSH summary -> state distribution -> type breakdown -> Detail CTA hierarchy; keep textual Correct/Incorrect/Unanswered/Pending/Unavailable states and semantic desktop table with labelled mobile cards. | `ADOPT_KSH_13G` |
| Reading/Listening Result | Mobile/card-table screen-reader and real-device behavior cannot be proved from desktop screenshots and static source. | `DEFER_13H_OR_LATER` |
| Reading/Listening Result | PREP background, medal/certificate/mascot, score badge, URLs, IELTS labels and content. | `REJECT_PREP_SPECIFIC` |
| Writing Result | Preserve KSH task navigation, score summary, criterion rows and prompt disclosure; long task/criterion labels must wrap without adding a fifth Detail tab. | `ADOPT_KSH_13G` |
| Writing Result | Small-viewport density still requires the deferred browser/device gate. | `DEFER_13H_OR_LATER` |
| Writing Result | PREP certificate hero, bands, descriptors, criteria, content and samples. | `REJECT_PREP_SPECIFIC` |
| Speaking Result | Keep trust/evidence availability before criterion state and Detail CTA; unavailable evidence stays unavailable and never becomes zero. Long labels wrap and color is not the sole state. | `ADOPT_KSH_13G` |
| Speaking Result | Radar/action-plan visualization is not justified by the static screenshots or current KSH data contract. | `DEFER_13H_OR_LATER` |
| Speaking Result | PREP radar, five-criterion IELTS navigation, band descriptors, medal/certificate and provider action-plan claims. | `REJECT_PREP_SPECIFIC` |
| Listening Detail | Preserve typed Objective split source/audio/transcript and review panes, local sticky source, jump chips, progressive disclosure and explicit unanswered/unavailable states; stack source before review on mobile. | `ADOPT_KSH_13G` |
| Listening Detail | Sticky/focus/reflow/anchor behavior needs the deferred runtime journey. | `DEFER_13H_OR_LATER` |
| Listening Detail | PREP chatbot, floating provider button, three-column drawer, IELTS part rail, content and assets. | `REJECT_PREP_SPECIFIC` |
| Reading Detail | Preserve synchronized source/review panes, Korean `lang` metadata, long Vietnamese/Korean wrapping and source/question navigation; keep current responsive sticky-to-stacked behavior. | `ADOPT_KSH_13G` |
| Reading Detail | Pin/unpin state and runtime overflow/focus verification are not justified for static 13G implementation. | `DEFER_13H_OR_LATER` |
| Reading Detail | PREP chatbot, IELTS passage/task content and provider explanation copy. | `REJECT_PREP_SPECIFIC` |
| Writing Detail | Preserve master/detail, exactly four tabs, secondary chips, pressed/selected state and evidence-to-finding disclosure; retain chip wrapping rather than a clipped PREP strip. | `ADOPT_KSH_13G` |
| Writing Detail | Runtime tab/filter announcement and scroll-edge behavior remain browser/screen-reader evidence work. | `DEFER_13H_OR_LATER` |
| Writing Detail | PREP fifth Sample tab, branded upgrade/sample, IELTS task URL, taxonomy and content. | `REJECT_PREP_SPECIFIC` |
| Speaking Detail | Preserve prompt/transcript/audio and feedback panes, exactly four tabs, secondary chips, selected finding/evidence mapping and explicit no-recording/no-authoritative-transcript/not-scorable states. Extend consistent focus visibility and bilingual wrapping. | `ADOPT_KSH_13G` |
| Speaking Detail | Keyboard/screen-reader/audio/mobile verification remains the deferred runtime gate. | `DEFER_13H_OR_LATER` |
| Speaking Detail | PREP question drawer, IELTS Part 1-3, pronunciation/phonetics/word-stress tables, provider sample/upgrade and mascot. | `REJECT_PREP_SPECIFIC` |

For every family, any taxonomy, rubric, criterion, band, scoring, acoustic,
assessment or AI-generation change is
`DEFER_POST_13H_COMPREHENSIVE_AUDIT_OR_PRE15`. It is not a 13G implementation
issue.

### 6.3 De-duplicated active-source findings

The UTF-8 audit read `530` scoped files successfully as UTF-8 and found no
active mojibake, BOM, replacement character, supplementary emoji product icon
or unsafe default-charset conversion. Existing database, JDBC, Thymeleaf,
servlet and shared-head UTF-8 configuration is explicit. Therefore 13G does
not rewrite stored history or applied migrations. The actionable gap is the
regression contract: it scans raw resources while claiming rendered evidence,
omits Practice Java/config, uses narrow markers and mixes immutable migrations
with active presentation.

Icon and accessibility audits converged on the same control defects:

- seven editor/PDF icon-only controls have no meaningful accessible name;
- active player/editor/PDF controls still use text glyphs such as `▶`, `Ⅱ`,
  `✕`, `☰` and `⋮`;
- editor/PDF dialogs and drawers lack a complete initial-focus, Escape,
  containment and focus-return lifecycle;
- closed player/editor/PDF drawers retain focusable descendants;
- essential editor/PDF cards, drop zones, thumbnails and validation rows have
  mouse-only activation or incomplete labels;
- the Speaking timer updates inside an overly broad polite live region;
- current filter/navigation state, async error/status messages, table labels,
  reduced motion, small-text contrast and mobile autosave state are
  inconsistent.

Responsive audit also found the PDF workspace locked to a fixed-height,
fixed-side-column layout without a narrow-screen media contract. Typed
Objective Detail blockifies tables on mobile without durable per-cell labels,
and long fixed Detail headers need a safe stacked narrow-screen layout. These
are targeted responsive corrections, not a Result Detail redesign.

Catalog/query evidence:

- `PracticeCatalogService` requests only `12` sets and performs bounded bulk
  test/section reads;
- the first implementation still loaded every retained non-discarded attempt
  and every coherent identity for those 12 sets, so the first independent
  static re-audit correctly rejected the claimed history bound;
- the correction replaces those reads with a distinct completed-section
  projection limited to current-page section ids and a window-ranked lifecycle
  projection that returns at most one attempt id per current-page set, followed
  by one `findAllById` hydration of at most 12 entities; there is no per-card
  query or application load proportional to retained attempt history;
- the corrected specification fixes the service-side repository interaction
  count for a 10,000-set catalog page and limits hydration to 12 candidates,
  while a focused MySQL integration case inserts 10,000 attempts for one
  current set and requires the ranked repository query to return exactly one
  newest candidate. These specifications are authored but not yet run;
- the audited initial `practice-catalog.js` appended every subsequent batch to
  the same DOM and the no-JavaScript path could not reach later batches;
- the implementation removes that append/fetch path and renders real
  server-owned Previous/Next pages of 12 while retaining the authorized
  fragment route;
- the authorized JPQL query has real `Page` semantics and a stable
  `createdAt,id` order, so the correct fix is server-owned Previous/Next
  pagination, not a fake client paginator;
- wildcard Vietnamese/Korean search cannot safely gain a B-tree prefix index
  without changing search semantics, so no fake FULLTEXT change is approved.

Progress/query evidence:

- all-time scalar/skill facts already use aggregate projections and remain
  truthful;
- the audited initial Writing task/cohort detail loaded every non-discarded
  Writing attempt, every related immutable identity and every related question
  version into memory;
- this is the Phase-13F-deferred large-history issue owned by 13G;
- the implemented fix is an explicitly labelled recent-Writing source window,
  fetched as `limit + 1` to expose truncation, while all-time aggregate facts
  remain all-time;
- a forward V56 index migration may support the active catalog, Writing-task
  existence and user-Writing filters. The correction adds a stored generated
  `activity_at = COALESCE(submitted_at, updated_at, created_at)` column so the
  bounded Writing query orders by the same indexed activity contract rather
  than sorting the full matching history. Applied migration/history files
  remain immutable.

### 6.4 Approved implementation units

| Unit | Exact implementation boundary | Status |
|---|---|---|
| `13G-UTF8-01` | Separate active-source byte/marker/icon regression checks from immutable migration audit; add exact Vietnamese/Korean persistence-to-render evidence and explicit response charset. No historical rewrite. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-ICON-01` | Replace active text-glyph product controls with local consistent outline SVG, add meaningful names and establish a reusable local icon fragment for touched server-rendered controls. No CDN/dependency. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-A11Y-01` | Correct labels, current/selected states, live status/error summaries, table names/scope, timer announcements and keyboard-operable validation/drop-zone/card controls. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-A11Y-02` | Add dialog/drawer focus, Escape, inert/hidden and focus-return lifecycle without changing editor/import/player data contracts. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-RWD-01` | Add bounded PDF workspace narrow-screen reflow, durable typed-Objective mobile cell labels, safe Detail header stacking, long Vietnamese/Korean wrapping and visible mobile autosave text. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-MOTION-01` | Complete reduced-motion overrides for active Progress/Detail/player/editor/import transitions and smooth scrolling; correct audited small-text contrast and focus visibility. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-PERF-01` | Replace infinite DOM append/no-script dead end with real server Previous/Next catalog pages of 12 while preserving filters, authorization, stable order, fragment compatibility and DB-ranked bounded-result attempt reads. Add realistic-volume/N+1 contracts. | `IMPLEMENTED_STATIC_REVIEWED` |
| `13G-PERF-02` | Bound Writing detail evidence to the latest 500 attempts with transparent source-window/truncation metadata; keep all-time projections unchanged; add only justified forward V56 indexes and realistic-volume contracts. | `IMPLEMENTED_STATIC_REVIEWED` |

The player exit/data-loss route behavior, full icon-library migration, broad
dead-control cleanup, PDF drawing model redesign, radar/visualization,
assessment/taxonomy/AI changes and every browser/device/screen-reader journey
are `DEFER_13H_OR_LATER` or their already documented later owner. They are not
silently fixed in this phase.

### 6.5 First static re-audit and grouped correction

Three independent read-only re-audits returned corrections instead of an
acceptance. Their de-duplicated blockers were:

- catalog attempt history remained application-unbounded;
- a positive catalog batch beyond the last page rendered `0–0 trên N`;
- V56 did not support the actual Writing activity order;
- task/cohort source windows did not expose that their dates/counts describe
  the shared bounded Writing source;
- the UTF/BOM/icon regression omitted root Practice JavaScript and the shared
  Practice sidebar;
- PDF import upload/loading, dialog shortcut isolation and AI-status
  disclosure still had accessibility lifecycle gaps; and
- several current-source ledgers still described 13C3 or 13G inaccurately.

One grouped static-only correction now:

- uses current-section completion evidence and one ranked lifecycle candidate
  per current-page set, with at most 12 entity hydrations;
- clamps an out-of-range positive batch to the real last server page;
- orders Writing through generated/indexed `activity_at`;
- renders the shared-source label explicitly for Writing task/cohort facts;
- expands strict UTF-8/BOM and DOM-control icon coverage;
- completes the PDF import keyboard/loading/reduced-motion/overlay and
  AI-status disclosure lifecycle; and
- reconciles the stale 13C3 current-source headers.

This correction has not been compiled or tested. It must receive fresh
read-only static acceptance before the phase can become
`READY_FOR_PHASE_VALIDATION`.

### 6.6 Second static re-audit and grouped correction

The three fresh cross-audits independently returned `REJECT_STATIC`. Their
de-duplicated blockers were:

- root `practice.js` was UTF-scanned but not checked for JavaScript-created
  text-glyph product controls, leaving two raw group-navigation arrows;
- the V56 contract checked only required substrings rather than the exact
  statement whitelist, and the realistic-volume unit did not prove constant
  repository interactions or execute the ranked query against large history;
- PDF workspace region selection still forced JavaScript smooth scrolling
  under reduced-motion;
- the editor validation drawer was visually off-screen while its descendants
  remained focusable; and
- PDF AI processing/success states were not announced although failure was.

One grouped static-only correction now:

- renders the root group-navigation controls with local outline chevron SVGs,
  explicit Vietnamese names and JavaScript-control glyph regression coverage;
- requires the exact eight V56 statements, extracts the native queries from
  their compiled repository declarations, proves constant service repository
  interactions for a 10,000-set catalog and adds the unrun 10,000-attempt
  MySQL cardinality case;
- chooses `auto` scrolling when reduced motion is requested;
- gives the editor validation drawer synchronized `hidden`/`inert`/
  `aria-hidden`, initial focus, Escape/close and focus-return behavior; and
- announces PDF AI processing and success politely while retaining assertive
  failure disclosure.

This second correction also remains static-only. Fresh cross-audits must
accept the corrected snapshot before validation may start.

### 6.7 Third static re-audit and contrast correction

On the unchanged 56-path snapshot, the UTF/icon/query audit returned
`ACCEPT_STATIC`. The responsive/accessibility audit and the independent
whole-phase/image-ledger audit both returned `REJECT_STATIC` for the same
remaining blocker: normal and small import-wizard text used the old accent,
warning and success colors below the WCAG AA 4.5:1 contrast requirement.
They reported no other blocker.

One bounded static correction changes only those active text tokens and their
regression contract:

- submit text is white on `#3B57D4` (`6.00:1`);
- the recent-session link is `#3B57D4` on `#F8FAFD` (`5.74:1`);
- uploaded status text is `#3B57D4` on the blended badge background
  (`5.09:1`);
- annotating status text is `#8A5700` on its blended badge background
  (`5.33:1`); and
- completed/reviewing status text is `#087A4F` on its blended badge
  background (`4.63:1`).

Non-text icon and border colors, import behavior, backend contracts and PREP
boundaries are unchanged. The static test now requires the corrected
declarations and rejects the five old low-contrast text declarations.

During the whole-phase read-only audit, one auditor accidentally invoked the
scoped command
`git diff --check -- PracticeCatalogService.java`. It produced no output,
changed no file and did not run a test, build, lint, browser, database or
provider operation. It is recorded for transparency and is not Phase 13G
validation evidence. The consolidated validation unit remains
`NOT_STARTED_BY_POLICY`.

This contrast correction requires another fresh static acceptance snapshot.

### 6.8 Final static acceptance

Three fresh independent read-only audits accepted the corrected 56-path
snapshot (`52` tracked modifications plus the same four intended Phase 13G
untracked artifacts). The coordinator stream digest was
`c1eefccef92487b2f092526bd06c488d400d8cdb0b7b6aa6c31fe097e327cb2d`.

- the UTF/icon/query audit returned `ACCEPT_STATIC`;
- the responsive/accessibility audit returned `ACCEPT_STATIC`; and
- the independent whole-phase/image-ledger audit returned `ACCEPT_STATIC`.

They confirmed the five contrast ratios, all six earlier correction families,
exact V56/query/10,000-volume specifications, bounded catalog/Writing
cardinality, 52/52 image ledger, typed Detail/four-tab boundaries, no PREP
copying and no scoring/taxonomy/provider scope creep. The snapshot is now
`READY_FOR_PHASE_VALIDATION`. No browser/device/provider evidence is added.

## 7. Validation Evidence

Status: `CONSOLIDATED_VALIDATION_GREEN`.

No test, compile, build, lint, application startup, browser/device journey,
provider call, Docker action, migration, database mutation, stage, commit or
push is claimed at phase entry.

### 7.1 First consolidated run and grouped correction

The first official validation run executed in the required order:

1. `git diff --check` passed;
2. JDK 17 compiled `739` production sources successfully;
3. the focused selector compiled `259` test sources, migrated the fresh
   disposable schema through V56 and ran `82` tests; and
4. the trapped cleanup dropped
   `ksh_phase13g_validation_20260728` and proved absence `0`.

The selector reported `1` failure, `4` errors and `0` skips. All five selected
integration cases passed, including the real 10,000-attempt ranked-query case,
and all 28 Progress service cases passed. Schema proof was not reached because
the selector failed.

The complete failure set had two causes:

- four Catalog service cases constructed Mockito projection mocks inside an
  unfinished outer repository stubbing expression; and
- the authoring UI contract still prohibited every `innerHTML` use in the
  image-preview slice although the accepted icon correction assigns only the
  local constant `editorCloseIcon(12)` to the remove button.

One grouped test-only correction replaces the two projection helpers with
deterministic interface implementations and narrows the image-preview contract
to allow that exact constant icon while continuing to reject container,
wrapper or URL-driven `innerHTML`. Production behavior, V56 and the accepted
static snapshot are unchanged.

Per the phase policy, the exact consolidated unit will now be rerun once.

### 7.2 Single exact rerun

The one permitted exact rerun passed:

1. `git diff --check` passed;
2. JDK 17 compile passed (`739` production sources remained up to date);
3. the same focused selector passed `82/82`, with zero failures, errors or
   skips:
   - `PracticePhase13GPerformanceContractTest`: `3/3`;
   - `PracticeFunctionalUiContractTest`: `18/18`;
   - `PracticePhase11AuthoringUiContractTest`: `17/17`;
   - `PracticeCatalogServiceTest`: `11/11`;
   - `PracticeProgressServiceTest`: `28/28`;
   - selected `PracticeIntegrationTest`: `5/5`;
4. the fresh disposable schema proof returned
   `56 / 56 / 0 / 1 / 7`: maximum Flyway version, successful migrations,
   failed migrations, stored generated `activity_at` column and exact V56
   index count; and
5. trapped cleanup dropped `ksh_phase13g_validation_20260728` and the
   independent absence query returned `0`.

The integration selector covered exact Vietnamese/Korean persistence/render,
completed-section catalog evidence, real server pagination plus last-page
clamping, an actual 10,000-attempt ranked state query and Progress projection/
activity ordering on MySQL 9.7.1. No full suite, browser/device journey,
application startup, Docker action or live provider/API call ran.

Phase 13G is `COMPLETE_FOCUSED_GATE_GREEN`. Its terminal transport step is the
reviewable multi-commit series and one branch push; this does not add product
or validation evidence. Phase 13 remains open, and 13H may begin only in a
separate task after that publication step.
