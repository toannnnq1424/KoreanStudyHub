# Practice End-Of-Phase-13 Browser And Device Closure — Live Change Log

Opened: `2026-07-29`

Status: `PHASE_VALIDATION_GREEN_READY_FOR_AUTHORIZED_PUBLICATION`

Branch: `codex/end-phase13-browser-device-closure`

Baseline: `5c8528e0613b8142ae4020906352a01667da86d3`

Validation unit: `END_OF_PHASE_13_BROWSER_DEVICE_CLOSURE`

## 1. Authority, ancestry and scope lock

- `origin/feature/practice-reduce-scope` was fetched before mutation and resolves
  exactly to required merge commit `5c8528e0`.
- The baseline tree is `48023bd9bb0b35431e6d3131db8c9fbfdad26183`;
  `5c8528e0` has parents `422aa362` and `9b936564`, and its tree is identical to
  the validated POST-13H branch tip `9b936564`.
- Accepted POST-13H evidence remains authoritative and is not reopened without a
  concrete regression: focused `444/444`, full suite `2439/2439`, fresh Flyway
  V1-to-V58 plus Hibernate/Tomcat, changed-journey browser QA `21` assertions at
  mobile `390x844` with a clean console, and real provider calls AI `0`, STT `0`,
  TTS `0`.
- This gate owns only final learner/lecturer browser and device closure. It does
  not start post-Phase-13 package reconciliation, comprehensive cleanup,
  Pre-14, Pre-15, the premium seed, Manual UAT or Phase 15.
- Practice AI/storage and project-wide/Admin AI/storage remain operational and
  separate. No commonization, package move, global configuration merge or
  consumer redirection is authorized.
- No scoring, rubric, prompt-policy or migration/schema change is authorized for
  visual QA. No real provider smoke is authorized.

## 2. Mandatory discovery completed before UI source mutation

The owning agent and bounded read-only audit agents read the following current
documents completely and reconciled stale status paragraphs against the newer
POST-13H authority:

1. `CODEX_PRACTICE_WORKFLOW.md`
2. `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`
3. `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`
4. `docs/PRACTICE_PHASE_13H_STABILIZATION_VISUAL_JOURNEY_LIVE_CHANGE_LOG.md`
5. `docs/PRACTICE_POST_13H_INTEGRITY_GATE_LIVE_CHANGE_LOG.md`
6. `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`
7. `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`
8. `docs/PRACTICE_POST_PHASE_13_PRODUCT_INTEGRATION_AND_PACKAGE_RECONCILIATION.md`

Focused `rg --files` and route/contract searches inventoried the Practice Java,
Thymeleaf, JavaScript, CSS, tests, migrations and documentation surfaces. The
seven supplied PREP screenshot groups were inspected by skill/question type.
They are treated only as information-hierarchy and interaction references;
KSH Vietnamese/Korean semantics and typed assessment contracts remain
authoritative. No IELTS terminology, asset or content is copied.

## 3. Locked result and assessment truth

- Exactly three active typed result-detail families are allowed: Objective
  Reading/Listening, Writing and Speaking. The dispatcher must render exactly
  one; a generic detail fallback must not replace them.
- Writing and Speaking have exactly four feedback tabs: overview, strengths,
  needs improvement and upgraded answer. Lecturer reference is separate and is
  shown only when backed by a truthful immutable source.
- Speaking remains transcript-only: four language rows, Fluency and
  Pronunciation/Delivery `NOT_SCORABLE`, and no subtotal, holistic or attempt
  score. No acoustic finding may be inferred from transcript evidence.
- Objective explanations remain immutable/read-only and typed by canonical
  question type. Result GET requests never write or call a provider.
- Unavailable evidence is not score zero. Pending, failed, unavailable, partial
  and empty states must be represented honestly.

## 4. Fresh baseline runtime

- Runtime source: the existing POST-13H JAR from branch tip `9b936564`, whose
  tree is byte-identical to baseline merge `5c8528e0`; no pre-inventory build or
  test was run.
- Catalog: `ksh_test_phase13_closure_baseline_20260729_0715`, verified absent and
  at `0` tables before creation, UTF-8 `utf8mb4/utf8mb4_0900_ai_ci`.
- Startup: Java `17.0.19`, Flyway validated and applied V1 through V58, Hibernate
  initialized the default entity manager, and Tomcat started on
  `http://127.0.0.1:18094`.
- Provider boundary: OpenAI and transcription credentials empty; attempt
  evaluation, deadline, prompt AI, prompt STT/TTS, learner transcription,
  Speaking evaluator, explanation generation and cleanup workers disabled.
- Local-only device support: Speaking upload/playback enabled against disposable
  storage `/tmp/ksh-phase13-closure-media.ATTlV7`; this does not enable an
  external provider.

## 5. Baseline inventory matrix

The first current UI inventory was completed before mutation as `36` named
PNGs. The subsequent required `mvn clean` removed that ignored `target/`
directory. To preserve a reviewable baseline, the exact POST-13H JAR was then
restarted from worktree `9cf5` at validated tip `9b936564` (tree-identical to
baseline merge `5c8528e0`) and seven canonical baseline screenshots were
recaptured under
`target/end-phase13-browser-device-closure/baseline-recapture/`. They cover
catalog desktop/mobile, set detail, player, and exactly the three typed detail
families: Objective Reading/Listening, Writing and Speaking. The original
36-image inventory observations below remain the pre-mutation record; only the
seven exact-runtime recaptures are claimed as retained baseline artifacts.

Current route evidence:

- Objective Reading/Listening detail rendered only
  `prd-objective-shell`; Writing rendered only `prd-writing-shell`. No active
  typed route rendered `rl-result-detail.html` or a generic detail root.
- The Writing detail exposed exactly four tabs and keyboard `ArrowRight`
  selected/focused the next tab while hiding the previous panel.
- A stale browser tab at lock version `0` received the truthful CAS conflict
  after the current tab autosaved lock version `1`; the current attempt then
  submitted normally.
- Reload and exit/back preserved the autosaved Reading selection and the server
  deadline attributes remained finite and server-authored.
- Listening speaker-check audio played to completion; its range control exposed
  min/max/value semantics. Seeded Listening content had no per-question audio,
  so seek/reload evidence is limited to the speaker-check asset.
- Lecturer manual authoring autosaved, validated both Speaking prompt modes,
  rejected audio mode without a live audio binding, accepted UTF-8 Korean text,
  previewed the learner-facing prompt, and published a local text-mode set. No
  PDF/AI generation action was invoked.
- `390x844` catalog, player, Objective detail, Writing detail and Speaking
  preflight plus `844x390` catalog/Writing detail were captured. DOM diagnostics
  reported no document-level horizontal scroll; the full-page image review also
  identified a possible visual clipping/scale discrepancy on player/Writing
  pages that must be rechecked against the rebuilt runtime before being called
  green.

Concrete baseline blockers admitted to this closure diff:

1. Test detail exposes zero-based learner copy (`Bài test 0`).
2. TFNG player controls expose English-only `True`, `False`, `Not Given`.
3. An empty authored blank prompt leaves the fill-blank input named only `1`
   instead of a descriptive accessible label.
4. A queued Writing detail overwrites its truthful pending state with
   `Không thể xác minh contract hiện hành của phản hồi`.
5. Lecturer draft creation, published-set edit/preview draft creation, empty
   draft exit cleanup, and dashboard-load empty-draft cleanup are writes
   reachable by `GET`.
6. Directly loading an already-expired attempt finalizes or discards it inside
   the player `GET` handler instead of leaving the accepted POST/deadline-worker
   terminal transition authoritative.

`BLOCKED_BY_ENVIRONMENT` entries, not passes:

- The in-app browser reached the native microphone permission request, but the
  host Mac was locked and computer control could not answer the permission
  sheet. Permission denial/revocation/retry and real recorder upload therefore
  remain blocked; no media/provider call is claimed.
- Native discard confirmation opened, but the in-app driver could not accept or
  dismiss the JavaScript confirm without losing the tab. The POST discard
  contract remains covered by the accepted suite and will be rechecked if the
  rebuilt browser session can execute it.
- OS-level Korean IME composition events and alternate browser engines are not
  available in this environment. Korean UTF-8 authoring and rendering were
  exercised with the semantic browser input surface; this is not reported as an
  IME-composition pass.
- Active reduced-motion emulation is unavailable in the browser capability;
  reduced-motion CSS/JavaScript remains subject to static contract review.

## 6. Implementation and validation ledger

No UI source mutation occurred before the inventory above was frozen. The
closure diff is limited to the seven reproduced/static-proven defect groups:

- Learner test numbering now translates the stored zero-based display order to
  one-based Vietnamese copy without changing the persisted contract.
- TFNG choices now use Vietnamese labels while preserving canonical submitted
  values; empty fill-blank prompts receive the descriptive `Ô trống N` visible
  and accessible-name fallback.
- An untrusted Writing detail continues to scrub score, criteria, diagnostics
  and upgrade material, but preserves an already-closed `PENDING`, `FAILED`,
  `UNAVAILABLE` or `LEGACY_UNVERIFIED` feedback state rather than mislabelling
  it as a contract failure.
- Lecturer create/edit/preview draft mutations are POST-only with CSRF-backed
  forms. Empty-editor exit uses the existing POST delete form; the legacy
  manual/create GETs are read-only dashboard redirects, published-set edit GET
  returns to learner-visible set detail, and dashboard GET no longer performs
  legacy empty-draft deletion.
- An expired attempt player GET is read-only and returns to the test detail
  with truthful deadline-processing copy. Existing timer POST submission and
  the server deadline processor remain the only terminal transition paths.
- The editor no-section PDF affordance now links to standalone import instead
  of fabricating a draft id. If a stale or directly-entered import URL targets
  an empty draft, the controller detaches it and returns an actionable warning
  instead of throwing a server error. Existing non-empty linked-draft import
  behavior is unchanged.
- Focused contracts cover the HTTP methods, read-only expired GET, learner
  labels/accessibility fallback, one-based display copy and truthful pending
  Writing detail, plus the stale/empty linked-draft import boundary.

Static reconciliation found no remaining template or Java reference to the
removed GET exit endpoint and no GET links/forms to POST-only draft creation or
published-set edit. The result dispatcher still has exactly the three typed
screen kinds and the detail assembler still requires exactly one matching
renderer. The legacy generic result-detail constant/template is untouched for
the future Phase-15 cleanup boundary and is not reachable from the typed
dispatcher. No schema, provider, package, scoring, rubric, prompt, storage or
seed source changed.

The complete accepted diff was frozen at `READY_FOR_PHASE_VALIDATION` before
the consolidated unit below. No source patch was followed by an ad-hoc build or
test loop.

## 7. Consolidated validation result

- `git diff --check`: green.
- Java: Temurin `17.0.19`.
- Build/compile: `750` production and initially `271` test sources compiled;
  the single grouped import-boundary correction compiled `272` test sources.
- Initial focused changed-seam selector: `6/6`, failures `0`, errors `0`, on a
  fresh disposable catalog.
- Import boundary selector added after the rebuilt browser exposed the empty
  linked-draft defect: `3/3`, failures `0`, errors `0`.
- The first full-suite launch was invalid environmental evidence: `167`
  connection errors after MySQL reported `Too many connections`. No product
  assertion failed before resource exhaustion. After bounding the test pool,
  the rerun was `2440/2440` green.
- Final source-complete publication-risk suite on fresh catalog
  `ksh_test_phase13_closure_full_final_20260729_0811`: `2441/2441`, failures
  `0`, errors `0`, skipped `0`, elapsed `01:18`.
- Fresh Flyway catalog: V1 through V58 applied, `58` successful migrations,
  `0` failed; Hibernate and Tomcat initialized successfully.
- Final package: current sources packaged successfully after the complete
  suite; Maven reported nothing stale to compile.
- Provider proof across the final browser and suite catalogs:
  `ai_request_logs=0`, `practice_ai_request_audits=0`; real AI `0`, STT `0`,
  TTS `0`.

All seven disposable catalogs used by the closure were dropped after evidence
collection and an exact-name schema query returned `0`. Disposable media
directories were removed. No real provider credential or call was used.

## 8. Rebuilt browser/device closure

Final evidence is stored under
`target/end-phase13-browser-device-closure/final/` as `26` PNGs. Together with
the seven canonical baseline recaptures, the retained handoff inventory is
`33` PNGs.

Validated learner routes and states:

- Catalog at desktop and viewport-only `390x844`, set detail, full-test player,
  Objective result/detail, truthful pending Writing detail and truthful
  unavailable Speaking detail.
- The test heading is one-based; TFNG controls show Vietnamese labels while
  posting canonical values; an empty blank exposes `Ô trống 1` visibly and as
  its accessible name.
- Keyboard input and autosave persisted a Reading answer through reload. The
  selected canonical `FALSE` value survived the rebuilt page, the deadline
  remained server-authored, and no pre-submit answer attribute was exposed.
- The player at `844x390` had no viewport overflow. Desktop and `390x844`
  catalog/result diagnostics reported document width equal to viewport width.
  Wide structured tables retain their intentional local scroll container.
- Writing rendered exactly four semantic tabs; `ArrowRight` moved both focus
  and selection. Speaking rendered exactly one typed root, no generic detail,
  and no fabricated Fluency/Pronunciation score.
- Korean/Vietnamese long-form copy rendered without mojibake. Dynamic text
  remains text-bound rather than injected as raw HTML.

Validated lecturer routes and states:

- Dashboard create is a CSRF-backed POST form. Direct GET `/manual` and
  `/create` redirects left the draft count unchanged at `2`.
- Editor validation state, disabled publish boundary, text/audio prompt modes,
  media/import affordances and standalone import were rechecked.
- A stale empty linked-draft import URL now returns the explicit actionable
  warning instead of a `500`; the final lecturer console is clean.

Console capture returned `[]` on the final learner, Speaking detail and
lecturer tabs. The network/provider audit showed no provider request. A small
number of early in-app `fullPage` captures visually clipped content despite
DOM width diagnostics proving no document overflow; those driver stitching
artifacts are excluded from the canonical handoff in favor of viewport-only
captures listed above.

Honest `BLOCKED_BY_ENVIRONMENT` entries remain:

- Real microphone permission denial/revocation/retry and recorder upload: the
  native permission sheet was reached, but the host Mac was locked and UI
  control could not operate it.
- Alternate browser engines and physical hardware/device combinations are not
  exposed by this environment.
- OS-level Korean IME composition events are unavailable; UTF-8 Korean input
  and rendering passed, but this is not reported as a native IME pass.
- Active reduced-motion emulation is unavailable; reduced-motion behavior was
  verified by static CSS/JavaScript contract review only.

These entries are recorded as blocked, not fabricated passes, and do not
conceal a reproduced product failure.

## 9. Publication boundary

The diff changes no schema, provider implementation, package boundary, scoring
or rubric, prompt policy, storage architecture, seed data, Admin/common AI or
unrelated user path. It does not begin post-Phase-13 reconciliation or any
Pre-14/Pre-15/Phase-15 work.

The closure is green for the authorized merge-commit publication sequence:
closure branch to `feature/practice-reduce-scope`, followed only after verified
checks/ancestry/tree and a complete feature-versus-main scope review by
`feature/practice-reduce-scope` to `main`. Any conflict, failed check or scope
gap remains an automatic `NO_GO`.

## 10. Migration reconciliation note — 2026-07-29

The browser/device closure above remains historical evidence for the feature
snapshot that then reported V1-V58. Before the feature was integrated with an
independently advanced main, its two unpublished Practice migrations were
renamed byte-identically from V57/V58 to V61/V62. Their SHA-256 values remain
`73e188ca16ad6354f34b85d3772499b365636e30631f44be0064622f2572bc63`
and
`b01c99a66c49822b1887cff2f62ac2c424e51feee6ebb3ee5eefc0ee244a6629`.
The 2,441-test and browser screenshot counts above are not reinterpreted as an
integrated V62 run; the integrated tree requires its own validation gate.
