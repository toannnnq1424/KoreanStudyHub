# Practice Phase 13D Result Overview UX Correction Live Change Log

Status: `FOCUSED_NON_DB_GATE_GREEN_WITH_2_ENVIRONMENT_BLOCKED_INTEGRATION_CASES`

Opened: `2026-07-22`

Branch: `feature/practice-reduce-scope`

Baseline after synchronization: `e84af24`

Validation unit: `PHASE_13D_UX_CORRECTION`

This correction does not erase or falsify the original Phase 13D result and
immutable-explanation validation at `bcc1467`. It records a later user-approved
presentation and evidence-honesty correction after review of additional PREP
result-overview screenshots. Phase 13E remains unopened and its F10-F14 detail
scope is not silently absorbed here.

## 1. User Direction And Operating Policy

The user explicitly authorized Codex to:

- preserve the language-assessment design documents, fast-forward pull the
  current `feature/practice-reduce-scope` branch and restore the documents;
- revisit the Phase 13D result overview because the current KSH UI/UX does not
  meet the intended learner experience;
- use the supplied PREP screenshots only as UI/UX research;
- automatically coordinate multiple agents and separate KSH Project
  conversations for later phase slices;
- automatically generate/send steering prompts, pull, audit affected
  documentation, integrate, validate once, commit and push the approved branch;
- never test/build/lint/start the application after each small file, issue or
  patch. All implementation units in this correction form one validation unit.

The controlling policy is
`CODEX_PRACTICE_WORKFLOW.md`, Section
`Phase-Scoped Validation And Project-Conversation Protocol`.

## 2. Research Evidence

Reviewed local screenshot folders:

- `/Users/toanlamsaoduocc/Downloads/prep/4.1.result của r-l/` — two overview
  images, one Reading and one Listening;
- `/Users/toanlamsaoduocc/Downloads/prep/4.2. result của writing/` — three
  Writing overview images plus five Speaking images filed in the same folder;
- `/Users/toanlamsaoduocc/Downloads/prep/4.3 result của speaking/` — two
  Speaking overview images.

PREP is evidence for information hierarchy and interaction only. KSH must not
copy PREP/IELTS brand assets, mascot, CSS, routes, band 1-9, task names,
descriptors or score claims.

The representative Result and Result Detail screenshots were rechecked on
`2026-07-22` after the user's chip reminder. PREP's compact category chips are
transferable only as scan/filter/evidence-navigation primitives. KSH chip
labels and ordering come from task-native Korean rubric/construct policy;
counts come from normalized evidence-backed findings and are never scores,
IELTS bands or browser-invented error totals. Current transcript-only Speaking
cannot show pronunciation/fluency/acoustic chips from STT.

Transferable hierarchy:

```text
status/header
  -> one dominant result hero
  -> skill-native performance/profile
  -> progressive task/criterion disclosure where useful
  -> one clear route to evidence detail
```

KSH must retain its additional real states: partial, unanswered, pending,
unscorable, failed and unavailable. Absence of those states in a PREP screenshot
does not authorize deleting them.

## 3. Locked Architecture Boundary

Keep the validated Phase 13D architecture:

- one immutable-attempt result envelope and one `PracticeResultAssembler`;
- exactly one presenter for Objective R/L, Writing and Speaking;
- result GET remains read-only;
- immutable published/version locks remain authoritative;
- the Phase 13D Reading/Listening artifact, binding, task and retry lifecycle is
  reused rather than duplicated;
- official answer/key remains backend authority;
- templates render escaped typed fields and never accept provider HTML.

This correction may change result DTO/presenter semantics and overview
templates/CSS/JavaScript where necessary. It must not introduce a second result
assembler, score calculator, explanation generator, cache or route family.

Phase 13E still owns the complete evidence-detail redesign. This correction may
only change a legacy detail surface when leaving it unchanged would directly
contradict an overview contract or make an acoustic/score claim known to be
false; such work must be logged and kept minimal.

Only after this Phase 13D correction is green and the user gives a separate GO
may Phase 13E replace Result Detail with exactly three screen contracts:
Objective Reading/Listening, Writing and Speaking. Shared visual primitives and
one read-only dispatcher are allowed; a generic cross-skill browser JSON parser
or placeholder template is not.

The broader Writing/Speaking scoring and R/L explanation redesign in
`docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`
and the future Pre-Phase-15 inventory remains future work except for the bounded
evidence-honesty guards explicitly listed below.

## 4. Static Findings Before Implementation

### `13D-UX-F01` — generic hero has competing focal points

`result.html` gives the title/score and a large separate skill square similar
visual weight. The learner lacks one dominant result statement and a compact
explanation of score unit, coverage and feedback state.

Decision: keep one shared shell but make the score/status the dominant hero,
use the skill as contextual identity and remove the universal hard-coded
`>=70%` celebration claim unless a named score profile supplies that meaning.

`13D-UX-01` resolution: `IMPLEMENTED_PENDING_PHASE_VALIDATION` in conversation
`019f85c0-ed88-7171-baa1-00b28d1adc0b`. The shared hero now presents skill as
context, removes the competing skill square and removes the threshold-derived
celebration method/use. Score, scale, optional level, grading progress, elapsed
time and feedback remain separately labelled in `result.html` and the shared
result DTO.

### `13D-UX-F02` — Objective table mixes count and point semantics

`ObjectiveResultPresenter` computes `earned / possible`, while the view labels
the column `Độ chính xác`. With partial or weighted points this is a score rate,
not necessarily the percentage of fully correct questions.

Decision: expose and label score rate honestly, keep answer-state counts
separate, reduce default table density, and show exceptional
partial/pending/unscorable states only when present without losing them.

`13D-UX-01` resolution: `IMPLEMENTED_PENDING_PHASE_VALIDATION`. The Objective
contract now names `scoreRatePercentage/scoreRateDisplay`, exposes the actual
earned/possible point pair, and explicitly describes the percentage as point
attainment rather than fully-correct-question accuracy. The old nine-column
layout is reduced to five semantic columns; all material answer states remain
inside a compact list and become labelled table-row cards below 720px without a
minimum-width horizontal-scroll dependency. Zero denominators retain `0/0`
points with no fabricated percentage; nullable score components remain nullable.

### `13D-UX-F03` — explanation CTA ignores availability

Objective overview always says `Xem đáp án và giải thích` even when explanation
state is PARTIAL, PENDING, FAILED or UNAVAILABLE.

Decision: the official-answer review remains reachable after submit, but the
copy/state must say whether AI/lecturer explanation is complete, partial,
pending or unavailable. Never imply that visiting result GET will generate it.

`13D-UX-01` resolution: `IMPLEMENTED_PENDING_PHASE_VALIDATION`. Objective
feedback keeps the existing `READY`, `PARTIAL`, `PENDING`, `FAILED` and
`UNAVAILABLE` state/count contract, normalizes learner-facing Vietnamese copy,
and changes the one detail CTA from “đáp án và giải thích” to state-honest
wording. The canonical detail deep link remains reachable for official answers
in every state; no rendering path starts a task, retries, writes data or calls a
provider.

### `13D-UX-F04` — Writing score rubric and diagnostic lenses compete

Three score-bearing long-form criteria and four diagnostic lenses are styled
almost equally. Two diagnostic lenses share one Language score and are not
separate points, while generic 40/60/80 bands look authoritative without a
task-profile descriptor policy.

Decision: score-bearing rubric is primary. Diagnostic lenses are secondary,
explicitly non-additive and must not display fabricated score/band semantics.
Pending/failed/unanswered tasks use a state panel instead of an empty pseudo
rubric. Long prompts are summarized/truncated in overview, not allowed to
dominate the page. The selected task's detail CTA carries `questionId`.

`13D-UX-02` resolution: `IMPLEMENTED_PENDING_PHASE_VALIDATION` in conversation
`019f85cd-fcac-7c33-9b91-ceb5aad65516`. Evaluated Q51/Q52 keep their six
task-native per-blank score rows, while evaluated Q53/Q54 keep the current
three score-bearing criterion IDs and `30/50` task maxima. The Writing view no
longer derives or renders generic 40/60/80 bands. Long-form diagnostic lenses
now contain learning evidence only, are placed in a visually secondary block
and are labelled as scoreless/non-additive. Pending, failed, unavailable and
unanswered tasks receive a state panel with no score, rubric row, diagnostic
scale or qualitative level. Q54-length prompts use an escaped three-line
preview plus native disclosure for the full text, and every task panel owns a
detail link bound to that immutable task `questionId`.

`13D-UX-05` refinement: the current detail route selects only immutable
`ESSAY` rows. Canonical ESSAY tasks retain the exact CTA; historical
`FILL_BLANK`/`GAP_FILL` Writing rows now show a clear unavailable-detail state
and no link, so they cannot fall back to another essay.

### `13D-UX-F05` — Writing profile disclosure is missing

The current Writing score profile is internal and unnamed in the result DTO.

Decision: at minimum label the current output as a KSH practice evaluation and
avoid any official-equivalence claim. A fully versioned `scoringProfileId`
remains governed by the language-assessment design/inventory unless the bounded
Writing slice can add it without inventing an unapproved profile.

`13D-UX-02` resolution: `IMPLEMENTED_PENDING_PHASE_VALIDATION`. The fragment
now visibly says `Đánh giá luyện tập KSH`, states that the task scale is not an
official TOPIK score or certificate, and keeps the real task score/max visible.
No scoring-profile ID, descriptor thresholds, TOPIK equivalence or future
Phase 13E assessment-policy storage was invented in this bounded correction.

### `13D-UX-F06` — transcript-only Speaking is presented as full numeric score

The evaluator receives transcript/text and optional question image, not learner
audio. Current normalization/persistence can include all six criteria, and the
overview only marks Fluency/Pronunciation as `Tham khảo` while still showing
their numeric score, band and contribution to the top score.

Decision: this correction must not ship a cosmetic redesign that preserves the
false measurement claim. Under transcript-only evidence:

- Fluency and Pronunciation/Delivery are `NOT_SCORABLE`, with no numeric score,
  qualitative band, progress/radar value or contribution to a displayed full
  Speaking `/100`;
- Content, Grammar, Vocabulary and Coherence may be shown only as a clearly
  labelled transcript-grounded language profile;
- missing/pending/failed segment evidence is not zero;
- audio file existence, duration, MIME metadata or ASR confidence is not
  acoustic scoring evidence;
- the full Speaking rollout remains `NO-GO` until an authorized scoring
  component consumes learner audio and is calibrated.

The Speaking implementation slice must audit provider, prompt, normalizer,
attempt scoring, presenter, DTO and all result surfaces. It may choose the
smallest safe contract (including withholding a holistic score) but may not
merely hide two rows while another surface continues the same overclaim.

`13D-UX-03` resolution: `IMPLEMENTED_PENDING_PHASE_VALIDATION` in conversation
`019f85dc-1d8e-71d0-b005-9308643d9905`. The current capability is now the
versioned `TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION` / `TRANSCRIPT_ONLY`
contract. The provider schema permits exactly the four transcript-grounded
language criteria, forces `score_available=false`, `overall_score=null` and
`level_label=null`, and sends neither learner-audio identity/metadata nor a
private locator as text evidence. Defensive normalization also accepts a
provider's old six-row shape, removes both acoustic measurements, persists
explicit `NOT_SCORABLE` rows and never constructs a holistic score. Actual STT
confidence is retained as transcription provenance only.

The result/persistence contract, reuse identity, compatibility reader,
PracticeService attempt grading and canonical presenter/DTO all fail closed:
current transcript-only results retain the four-criterion language profile but
cannot produce attempt points; missing/failed evidence stays unavailable;
unknown or historical envelopes are `LEGACY_UNVERIFIED` and are never silently
upgraded. `AUDIO_DIRECT_FULL_RESERVED` / `DIRECT_AUDIO_AND_TRANSCRIPT` is an
explicit disabled extension seam. The current text/image chat client rejects
that capability without making a provider call. Acoustic criteria and a
holistic score may be enabled only after a future authorized evaluator actually
consumes learner audio and both acoustic-calibration and rollout-readiness gates
pass. Live Speaking AI remains `NO-GO`.

## 5. Approved Implementation Slices And Conversation Order

All rows are implementation units inside one validation unit. They run in this
dependency order. Read-only research may run in parallel; overlapping code
edits are serialized.

| Slice | Conversation ownership | Primary scope | Initial status |
| --- | --- | --- | --- |
| `13D-UX-01` | Shared shell + Objective R/L overview | `result.html`, `objective.html`, Objective result DTO/presenter semantics, shared CSS portions and availability-aware CTA | `IMPLEMENTED_PENDING_PHASE_VALIDATION` — conversation `019f85c0-ed88-7171-baa1-00b28d1adc0b` |
| `13D-UX-02` | Writing overview | Writing fragment/presenter/DTO, task-aware detail link, score-vs-diagnostic hierarchy, Writing CSS/JS and pending/failed states | `IMPLEMENTED_PENDING_PHASE_VALIDATION` — conversation `019f85cd-fcac-7c33-9b91-ceb5aad65516` |
| `13D-UX-03` | Speaking evidence and score guard | evaluator capability, prompt/normalizer/persistence/presenter/DTO and cross-surface transcript-only score honesty | `IMPLEMENTED_PENDING_PHASE_VALIDATION` — conversation `019f85dc-1d8e-71d0-b005-9308643d9905` |
| `13D-UX-04` | Speaking overview presentation | Speaking overview fragment, criterion profile, N/A states, action-plan hierarchy, playback placement and responsive CSS | `IMPLEMENTED_PENDING_PHASE_VALIDATION` |
| `13D-UX-05` | Cross-skill reconciliation | accessibility/responsive/static route/dead-contract audit, focused test-code updates, docs reconciliation and deferred validation inventory | `IMPLEMENTED_PENDING_PHASE_VALIDATION` |

The coordinator creates one KSH Project conversation per row, sends the locked
steering prompt automatically, waits for its handoff and audits the working tree
before dispatching the next overlapping slice.

## 6. Acceptance Contract

### Shared shell

- Information order is hero -> skill-native performance/profile -> one clear
  detail action.
- Score unit, numerator/denominator, coverage and feedback state are distinct.
- No universal pass/celebration label is inferred from 70% without policy.
- Long Korean/Vietnamese titles wrap without breaking layout.
- Desktop, tablet and approximately 360-390px mobile remain usable without
  whole-page horizontal overflow.

### Reading/Listening

- Shared shell, canonical KSH question types only.
- Answer-state totals remain internally consistent.
- `earned/possible` is labelled as score rate; fully-correct question rate is
  not fabricated.
- READY/PARTIAL/PENDING/FAILED/UNAVAILABLE explanation states change the CTA
  description honestly while official-answer review remains reachable.
- Dense 9-column desktop data becomes a compact readable table/card layout on
  narrow screens.

### Writing

- Task navigation and criterion hierarchy are progressive and keyboard usable.
- The selected task opens detail with the correct `questionId`.
- Score-bearing rubric and non-additive diagnostic lenses cannot be confused.
- Pending/failed/unanswered states show no pseudo score/band/rubric.
- Overview cannot be broken by a long Q54 prompt.
- Learner copy says KSH practice evaluation and contains no IELTS taxonomy or
  official score equivalence.

### Speaking

- Transcript-only mode exposes exactly four independently scored transcript-
  language rows at `20/20/15/15` and two null acoustic `NOT_SCORABLE` rows.
- It exposes no `/70`, subtotal, aggregate, holistic or attempt score and no
  numeric or qualitative acoustic claim.
- Criterion/profile UI supports explicit N/A and evidence coverage without
  inventing a partial denominator.
- Summary/action plan is concise and evidence-grounded.
- Overview links to detail without pretending current detail has completed the
  future Phase 13E four-tab redesign.
- Legacy acoustic-like scores are not silently presented as current valid
  measurement.

### Safety and accessibility

- Vietnamese/Korean product language only; no PREP/IELTS brand or descriptors.
- Tab semantics, keyboard arrows/Home/End, visible focus, text labels and color
  contrast remain valid.
- Provider/prompt text is escaped; no provider HTML controls layout.
- No internal storage key, content hash, credential or private media locator is
  exposed.

## 7. Deferred Single Phase Validation

No command in this section runs until all five slices are implemented, the
whole diff and this log are reconciled, and the coordinator reports
`READY_FOR_PHASE_VALIDATION`.

Deferred inventory currently includes:

1. `git diff --check`;
2. changed JavaScript syntax check only if JavaScript changed;
3. one JDK 17 compile;
4. the smallest focused set covering result presenter/wording/rendering,
   Speaking evaluation contract and changed controller/integration boundaries;
5. integration tests only for actually changed route/persistence boundaries;
6. no full suite unless the final phase diff is broad enough to require it or
   the user explicitly asks.

After green validation, the coordinator stages exact owned files, commits and
pushes `feature/practice-reduce-scope` automatically. It never stages the
existing unrelated `.tmp*`, truncated Draw.io source, `openspec-temp/` or
`__pycache__` paths.

## 8. `13D-UX-01` Implementation Handoff

Status: `IMPLEMENTED_PENDING_PHASE_VALIDATION`

Conversation: `019f85c0-ed88-7171-baa1-00b28d1adc0b`

Changed files owned by this slice:

- `src/main/resources/templates/practice/result.html`;
- `src/main/resources/templates/practice/result/objective.html`;
- shared/Objective portions of
  `src/main/resources/static/css/practice-result.css`;
- `src/main/java/com/ksh/features/practice/result/ObjectiveResultPresenter.java`;
- the canonical result/Objective records in
  `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java`;
- this live log.

Contract and UI decisions:

- the shared hero has one dominant score/result focus and no generic skill
  square or `>=70%` celebration claim;
- `ResultScoreSummary.primaryDisplay()` falls back to truthful earned points
  when the stored generic score is null, without fabricating a denominator;
- Objective percentages are earned points divided by possible points and are
  therefore named score rate/point attainment, while fully correct, partial,
  incorrect, unanswered, pending and unscorable counts remain separate;
- READY and PARTIAL are the only states whose action label promises an
  explanation. PENDING, FAILED, UNAVAILABLE and unknown states link to official
  answer review without promising explanation content;
- the result GET, immutable identity and existing detail route are unchanged;
  no provider, task, retry, database mutation or controller boundary was added;
- the existing Writing/Speaking fragment dispatch and their `.pr-actions`
  layout contract remain present. Their skill-specific implementations are not
  changed by this slice.

Focused test code updated, but not executed:

- `PracticeResultPresenterTest`: Objective exceptional answer states, weighted
  point rate versus answer accuracy, zero denominator, nullable stored score /
  possible points, earned-point fallback and all five explanation lifecycle
  states;
- `PracticeFunctionalUiContractTest`: three-fragment dispatch preservation,
  no threshold celebration/skill square, five-column score-rate wording,
  lifecycle-aware CTA branches and narrow-screen table-card CSS;
- `PracticeResultWordingTest`: canonical score helpers remain in use and
  threshold-derived celebration wording remains absent.

Read without editing: `QuestionExplanationReadServiceTest` for immutable
READY/PARTIAL/PENDING/FAILED/UNAVAILABLE availability behavior. No test,
compile, build, lint, startup, JavaScript check, browser QA, provider call or
Git command was run in this implementation unit.

Deferred slice selectors for the coordinator to merge into the one phase gate:

1. `git diff --check`;
2. `env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw -DskipTests compile`;
3. `env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw "-Dtest=PracticeResultPresenterTest,PracticeFunctionalUiContractTest,PracticeResultWordingTest,QuestionExplanationReadServiceTest" test`.

Deferred cases: Reading and Listening graded/partial/pending/failed/unavailable
explanation combinations; weighted and partial objective scoring; null stored
score, null possible points and zero denominator; approximately 360-390px table
cards without material horizontal overflow; keyboard focus on the one canonical
detail link; and unchanged Writing/Speaking fragment rendering.

Cross-slice dependency/risk: later Writing and Speaking overview slices share
`result.html` and `practice-result.css`, so the coordinator must serialize those
edits and retain this hero/metadata hierarchy plus the restored `.pr-actions`
rule. The Objective DTO accessor rename from `accuracy*` to `scoreRate*` is
intentional and must not be reverted by a later shared-contract merge. Browser
and device evidence remains deferred; responsive acceptance here is static
reasoning plus focused contract test code only.

## 9. `13D-UX-02` Implementation Handoff

Status: `IMPLEMENTED_PENDING_PHASE_VALIDATION`

Conversation: `019f85cd-fcac-7c33-9b91-ceb5aad65516`

Changed files owned by this slice:

- `src/main/resources/templates/practice/result/writing.html`;
- `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java`;
- the Writing/result records in
  `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- Writing-specific portions of
  `src/main/resources/static/css/practice-result.css`;
- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java`;
- this live log.

Task-native KSH rubric and diagnostic decisions:

- current `WritingScoringPolicy` IDs, Q51/Q52 `2/2/1` allocation per blank and
  Q53/Q54 `12/9/9` plus `20/15/15` maxima remain unchanged; this slice does not
  resolve the future scoring-profile decision in the language-assessment design;
- the score-bearing task total and criterion point pairs are the primary block;
  stored max values that contradict the current task policy make the overview
  unavailable instead of changing the scale or displaying a pseudo result;
- `WritingAnalysisLens` no longer carries score, max, percentage, threshold band
  or `countedSeparately`. Its four long-form views contain only categorized
  learning evidence, are explicitly non-additive and do not appear for Q51/Q52;
- `ResultRubricCriterion` no longer carries the generic `ResultEvaluationBand`.
  This edit was necessary to remove the source of invented Writing descriptors;
  static call-site review found this record is used only by the Writing presenter,
  while Speaking keeps its separate `SpeakingCriterionResult` contract;
- an evaluation is overview-ready only when every task-policy criterion has a
  valid score and task-native max. All other answered states render a truthful
  pending, failed or unavailable panel; unanswered tasks ignore stale feedback.

Interaction and safety decisions:

- each rendered task panel owns a detail CTA whose query parameter is
  `questionId=${task.questionId()}`; the generic attempt-only Writing CTA was
  removed, and the existing controller/detail route was not changed;
- the full prompt remains escaped through `th:text`. A native `<details>`
  disclosure exposes it after a constrained three-line preview, with visible
  summary focus and Korean/Vietnamese wrap safety;
- task and diagnostic tab ARIA plus Arrow/Home/End behavior continue to use the
  existing `practice-result.js`. Initial server markup no longer hides later
  Writing task/lens panels, so all content remains readable without JavaScript;
  JavaScript applies the selected-panel state when available;
- no result GET, controller, provider, task, retry, database, migration or
  Phase 13E detail behavior changed.

Focused tests read and updated, but not executed:

- `PracticeResultPresenterTest`: Q51 native per-blank rows, Q53/Q54 real maxima,
  scoreless diagnostics, policy-max mismatch rejection and pending/unavailable/
  failed/unanswered state suppression;
- `PracticeFunctionalUiContractTest`: KSH/non-official copy, task-bound
  `questionId`, escaped prompt disclosure, state branches, absence of Writing
  bands/scales, focus/mobile selectors, tab keys and no-JavaScript content;
- `PracticeResultWordingTest`: KSH practice wording, non-additive diagnostics,
  task-bound action and removal of Writing band/hidden-panel wording contracts.

No test, compile, build, lint, startup, `git diff --check`, JavaScript syntax
check, browser QA, provider call or Git command was run in this implementation
unit. `src/main/resources/static/js/practice-result.js` was read and left
unchanged because its existing task/lens tab ARIA and Arrow/Home/End handling
already satisfies the bounded interaction contract.

Deferred slice selectors for the coordinator to merge into the one phase gate:

1. `git diff --check`;
2. the Phase 13D-UX JDK 17 compile already listed in Section 8;
3. `env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw "-Dtest=PracticeResultPresenterTest,PracticeFunctionalUiContractTest,PracticeResultWordingTest" test`.

Deferred cases: Q51/Q52 evaluated per-blank rubric without essay diagnostics;
Q53/Q54 evaluated task score/max with scoreless evidence lenses; mixed ready,
pending, failed, unavailable and unanswered tasks; one immutable `questionId`
per task action; long Korean Q54 prompt at desktop and approximately 360-390px;
task/lens Arrow keys plus Home/End; visible disclosure focus; all Writing panels
readable without JavaScript; KSH practice/non-official wording; and unchanged
Objective plus Speaking fragment dispatch.

Cross-slice dependency/risk: `practice-result.css` remains shared. This slice
adds Writing-prefixed layout/state/prompt/action selectors and retains the
13D-UX-01 hero, Objective table/card rules, `.pr-actions` and all existing
Speaking selectors. Generic `.pr-band-chip` and `.pr-scale` rules remain only
because the current Speaking fragment still consumes them; later Speaking work
must not reconnect those generic bands to Writing. The shared DTO file changed
only for current Writing-only records, but the coordinator should retain that
separation when integrating `13D-UX-03/04`. Browser/device evidence remains
deferred to the consolidated phase gate/roadmap boundary.

## 10. `13D-UX-03` Implementation Handoff

Status: `IMPLEMENTED_PENDING_PHASE_VALIDATION`

Conversation: `019f85dc-1d8e-71d0-b005-9308643d9905`

Changed production/configuration files owned by this slice:

- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluatorCapability.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvidenceMode.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingCriterionAvailability.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingContractTrust.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationRequest.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/OpenAiCompatibleSpeakingEvaluationClient.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationOrchestrator.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilder.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingPromptRules.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingRuleEngine.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationNormalizer.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationResult.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingRubricCriterion.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvidenceSource.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationSource.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingScorePolicy.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluatorProperties.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationIdentity.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationReusePolicy.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationService.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingFeedbackCompatibilityReader.java`;
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingFeedbackViewMapper.java`;
- `src/main/java/com/ksh/features/practice/ai/readiness/SpeakingProviderRolloutReadiness.java`;
- `src/main/java/com/ksh/features/practice/service/PracticeService.java`;
- `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java`;
- the Speaking/result records in
  `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/resources/application.properties`.

Changed focused test-contract files, read and edited but not executed:

- `SpeakingEvaluationNormalizerTest`;
- `SpeakingEvaluationOrchestratorTest`;
- `SpeakingEvaluationPromptBuilderTest`;
- `SpeakingPromptRulesTest`;
- `SpeakingEvaluationRuleEngineTest`;
- `OpenAiCompatibleSpeakingEvaluationClientTest`;
- `SpeakingEvaluationApplicationServiceTest`;
- `SpeakingEvaluationReusePolicyTest`;
- `SpeakingFeedbackCompatibilityReaderTest`;
- `SpeakingFeedbackViewMapperTest`;
- `SpeakingResultRenderingContractTest`;
- `SpeakingScorePolicyTest`;
- `SpeakingProviderRolloutReadinessTest`;
- `PracticeResultPresenterTest`;
- `PracticeServiceTest`.

Documentation changed by this slice: this live change log (F06, the
`13D-UX-03` row and this dedicated handoff).

`SpeakingScorePolicyTest` was additionally read and updated; the current
transcription/media/readiness test inventory was statically discovered for
deferred regression ownership. No test, compile, build, lint,
startup, `git diff --check`, JavaScript check, migration check, provider call,
browser QA or Git command was run in this implementation unit.

End-to-end evidence, score and persistence behavior:

1. `SpeakingEvaluationOrchestrator` supplies the actual
   `transcriptConfidence`, declares the current capability/evidence version and
   overwrites provider-supplied provenance. STT and text fallback both remain
   non-acoustic.
2. The text/image chat payload contains transcript/task context and optional
   governed question image only. It omits learner-audio media ID/version,
   duration, size, MIME type and any audio locator/content. The client rejects
   the reserved direct-audio capability instead of pretending to satisfy it.
3. Prompt rules, pre-evaluation signals and strict schema contain no instruction
   to score hesitation, fillers, pauses, pacing, pronunciation, intelligibility,
   rhythm, intonation, linking or acoustic listener burden. `AUDIO_METADATA` is
   prohibition/provenance language only and is not an allowed evidence source.
4. Four transcript-grounded rows remain numeric at their original criterion
   maxima (`20/20/15/15`). They are not redistributed or rescaled to `/100`.
   Fluency and Pronunciation/Delivery persist as `NOT_SCORABLE` with null score,
   max, percentage and band/level; provider six-row acoustic output and related
   feedback/evidence/action items are removed deterministically.
5. New typed results persist capability, evidence mode/version, criterion
   availability and trust with `scoreAvailable=false` and `overallScore=null`.
   `PracticeService` asks `SpeakingScorePolicy` for points only through
   `holisticScoreAvailable`; current transcript-only attempts therefore persist
   a null attempt score while retaining safe language-profile feedback.
6. Reuse requires a matching model/prompt/rubric/schema identity and a current
   capability contract with a complete four-row language profile. Version
   defaults are now `speaking-eval-v3-transcript-language-only`,
   `speaking-rubric-v2-transcript-language-profile` and
   `speaking-schema-v2-partial-language-profile`; the evidence contract is
   `speaking-evidence-v1-transcript-language-only`.
7. Stored camel-case current envelopes retain the typed partial profile.
   Snake-case/pre-capability, mock, unknown-capability and unknown-evidence
   values fail closed as `LEGACY_UNVERIFIED`; their old score is neither
   rewritten in the database nor exposed as a current measurement.
8. The canonical presenter/DTO now carries evaluator capability, evidence mode,
   evidence-contract version, trust, profile/holistic availability, legacy
   count and per-criterion availability. Unknown values become unavailable or
   legacy/unverified rather than selecting a future UI branch.
9. Speaking rollout readiness now always reports
   `DIRECT_AUDIO_FULL_EVALUATOR_NOT_READY` for the current transcript-only
   capability, even if both legacy provider configuration gates are manually
   enabled. Configuration alone therefore cannot turn the current path into a
   live full-scoring rollout.

Documentation/debt discovery completed for this slice:

- current controlling documents read: `CODEX_PRACTICE_WORKFLOW.md`, this live
  log, `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE_MANIFEST.md` and
  `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
- design/current-target document read:
  `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`;
  its older recommendation to expose a four-criterion partial overall score is
  superseded for this locked correction by holistic/attempt score unavailable;
- historical/roadmap documents read:
  `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md` and
  `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`; their older “limited/advisory”
  acoustic and holistic Speaking-overview language is stale implementation
  history and must not override F06 or this handoff;
- the Phase-15 inventory's `P15-PRE-01` “current source truth” becomes stale
  after this unvalidated slice, but `P15-PRE-01` still owns the authorized
  direct-audio evaluator, academic calibration, readiness blocker and production
  proof. `P15-PRE-04` retains broader Korean rule-engine counterexample debt;
  `P15-PRE-05` owns the eventual JSON data disposition; `P15-PRE-06`/Phase 13E
  owns detail replacement; and `P15-COMP-03`, `P15-COMP-04` and
  `P15-COMP-12` own mixed/legacy payload cleanup and fixture decisions. No
  compatibility branch or historical row was migrated/deleted here.

Deferred selector for the coordinator to merge into the one phase gate:

`env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin bash mvnw "-Dtest=SpeakingEvaluationNormalizerTest,SpeakingEvaluationOrchestratorTest,SpeakingEvaluationPromptBuilderTest,SpeakingPromptRulesTest,SpeakingEvaluationRuleEngineTest,OpenAiCompatibleSpeakingEvaluationClientTest,SpeakingEvaluationApplicationServiceTest,SpeakingEvaluationReusePolicyTest,SpeakingFeedbackCompatibilityReaderTest,SpeakingFeedbackViewMapperTest,SpeakingResultRenderingContractTest,SpeakingScorePolicyTest,SpeakingProviderRolloutReadinessTest,PracticeResultPresenterTest,PracticeServiceTest" test`.

Deferred cases: provider six-row and four-row outputs; STT versus text fallback;
low/high/missing transcript confidence with low confidence producing no numeric
profile; audio ID/duration/MIME and
`AUDIO_METADATA` non-evidence; no media/private-locator text leak; four safe
criteria preserved only at their individual maxima `20/20/15/15`, never
combined into `/70`, a subtotal or a redistributed `/100`;
two acoustic rows null/`NOT_SCORABLE`; provider acoustic feedback, level,
listener-burden, annotation and action stripping; holistic/result/attempt score
null; partial, pending, failed and unanswered segments not converted to zero;
matching-current versus version-changed reuse; legacy snake/camel/mock and
unknown enum fail-closed behavior; reserved direct-audio capability rejected by
the chat client and score-disabled; readiness remains blocked even when both
legacy provider gates are configured; presenter/DTO capability, trust,
coverage, N/A and legacy state; and unchanged protected media playback routing.

`13D-UX-04` must render this contract rather than recalculate it: label the
current output as a transcript-grounded language profile; show the four scored
language criteria with their own score/max and segment coverage; show Fluency
and Pronunciation/Delivery as explicit `NOT_SCORABLE` N/A states with no bar,
band, percentage or advisory score; keep the holistic hero unavailable; expose
partial/pending/failed and legacy-unverified notices; and treat playback/media
metadata as provenance/navigation only. It must not replace the presenter or
open the Phase 13E four-tab detail redesign.

Residual `NO-GO` debt: no evaluator currently receives learner audio. The
reserved capability is disabled, its chat-client use is rejected, both
transcription/evaluator feature gates remain false by default, and no audio
provider/transport, calibration change or rollout activation was added. Full
Speaking scoring remains blocked until `P15-PRE-01` supplies an authorized
direct-audio API/model path, `P15-PRE-07` supplies Korean teacher-reviewed
calibration/readiness and `P15-PRE-08` supplies privacy/governance proof.
Storage/provider/device/manual UAT debt remains in its named Phase 15/13H owners.

## 11. `13D-UX-04` Implementation Handoff

Status: `IMPLEMENTED_PENDING_PHASE_VALIDATION`

Changed production files owned by this slice:

- `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java`;
- the Speaking overview records and display helpers in
  `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java`;
- `src/main/resources/templates/practice/result/speaking.html`;
- the Speaking-specific portions of
  `src/main/resources/static/css/practice-result.css`.

Changed focused test-contract files, edited but not executed:

- `src/test/java/com/ksh/features/practice/result/PracticeResultPresenterTest.java`;
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingResultRenderingContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`;
- `src/test/java/com/ksh/features/practice/PracticeResultWordingTest.java`.

Documentation changed by this slice: this live change log (`13D-UX-04` row and
this handoff). The shared `result.html`, `practice-result.js`, Objective and
Writing fragments/presenters/payloads were inspected where relevant and left
unchanged. The existing owner-protected Speaking playback path and current
attempt-detail CTA were retained; no media URL, storage field or new route was
introduced.

Implemented overview behavior:

1. The first Speaking section now leads with learner-facing profile state,
   coverage, evidence source, scope and trust, followed by the holistic result
   state. The current transcript-only path shows no holistic number and states
   explicitly that four transcript-language criteria are not combined into a
   full Speaking score.
2. The criterion profile shows all six KSH criteria. Content/Task Fulfillment,
   Grammar/Sentence Control, Vocabulary/Expressions and
   Coherence/Organization use their native `20/20/15/15` maxima and explicit
   covered/total response-segment counts. Fluency and Pronunciation/Delivery
   remain visible as textual `NOT_SCORABLE` states with no score, denominator,
   percentage, generic band, progress fill or radar value; the copy explains
   that the evaluator did not consume direct learner-audio evidence.
3. `READY`, `PARTIAL`, `PENDING`, `FAILED`, `UNAVAILABLE` and
   `LEGACY_UNVERIFIED` are distinct display states. Pending, failed, missing and
   unavailable evidence never becomes zero. Unknown contracts/capabilities and
   the disabled `AUDIO_DIRECT_FULL_RESERVED` seam fail closed to an unavailable
   legacy/unverified profile.
4. Historical Speaking rows cannot contribute acoustic, holistic or criterion
   numbers. Retained legacy `ESSAY` compatibility copy is now counted as
   legacy/unverified, does not contribute current Speaking coverage/readiness,
   and cannot manufacture a current transcript profile.
5. Presenter aggregation filters free-text summaries and action-plan rows that
   make acoustic claims under the transcript-only capability. Current action
   items require a mapped transcript-grounded criterion/subcriterion, and the
   overview displays at most four concise items with a learner-facing criterion
   label.
6. The result order is evidence/profile state, transcript-language criteria,
   concise summary/action plan, existing recordings, then the existing detail
   CTA. No secondary tab set was added because the overview remains readable as
   one short progressive document without JavaScript. Long Vietnamese/Korean
   copy wraps, nonnumeric states are conveyed in text rather than color, and
   the Speaking layout collapses to one column on narrow screens.
7. A governed future seam remains: the DTO/template can render a holistic score
   only when a non-reserved direct-audio capability is current, supplies an
   available score and is admitted by enabled acoustic/holistic readiness
   flags. The currently reserved capability cannot activate this branch.

Contract documents audited before implementation:

- full audit: `CODEX_PRACTICE_WORKFLOW.md`;
- full audit: this live change log;
- full audit:
  `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`;
- full audit:
  `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
- full audit: `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`;
- full audit:
  `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE_MANIFEST.md`;
- full audit: `docs/PRACTICE_PHASE_13E_LIVE_CHANGE_LOG.md`;
- full audit:
  `docs/research-input/phase-13-14-prep-ui-ux-research-checkpoint.md`;
- full audit:
  `docs/architecture/practice/PRACTICE_PHASE_13E_RESULT_FIXTURES.md`;
- focused Phase 13/result/Speaking audit:
  `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
- focused overview/presenter boundary audit:
  `docs/architecture/practice/mermaid/KSH_PRACTICE_CLASS_DIAGRAMS.md` and
  `docs/architecture/practice/mermaid/KSH_PRACTICE_SEQUENCE_DIAGRAMS.md`.

The older six-criterion holistic/advisory Speaking recommendations in the Phase
13 gate/blueprint/design history are superseded for the current capability by
F06 and `13D-UX-03`. The PREP checkpoint informed information hierarchy only;
no PREP branding, IELTS band vocabulary, radar or native-like judgement was
copied.

Static source review only was performed in this implementation unit: call-site
searches for the Speaking payload/criterion/action/media route, template/CSS
searches for generic band/scale/radar/IELTS/raw capability output, and scoped
diff inspection of the files listed above. No unit, integration or full test,
Maven/Gradle compile/build, application startup, Docker/frontend build, lint,
`git diff --check`, database/migration check, browser/manual UI test, provider
call or Git staging/commit/push operation was run.

Debt intentionally deferred:

- Phase 13E retains per-question transcript/media synchronization, question
  navigation and the result-detail redesign; this overview does not parse raw
  JSON or add per-question panels.
- `P15-PRE-01` retains the authorized direct-audio evaluator/API;
  `P15-PRE-07` owns academic calibration/readiness and `P15-PRE-08` owns
  consent/privacy/retention/reviewer authorization. Full Speaking stays `NO-GO`.
- Phase 15 compatibility cleanup retains physical legacy JSON/row disposition,
  fixture cleanup and destructive removal decisions; this slice only fails old
  artifacts closed at presentation time.
- `13D-UX-05` must reconcile the combined Objective/Writing/Speaking diff,
  accessibility/responsive contracts and stale cross-document source-truth
  wording, then hand the coordinator the single phase-validation selector. No
  blocker is known for opening that reconciliation slice.

## 12. `13D-UX-05` Cross-skill Reconciliation Handoff

Status: `IMPLEMENTED_PENDING_PHASE_VALIDATION`

Production/source files changed by UX-05:

- `SpeakingEvaluationResult.java`, `SpeakingFeedbackCompatibilityReader.java`,
  `SpeakingFeedbackViewMapper.java`, `SpeakingEvaluationApplicationService.java`;
- `SpeakingResultPresenter.java`, `ObjectiveResultPresenter.java`,
  `WritingResultPresenter.java`, `PracticeDtos.java`;
- `templates/practice/result/writing.html` and Writing state/responsive rules in
  `static/css/practice-result.css`.

Focused test source changed but not executed:

- `SpeakingFeedbackViewMapperTest`, `SpeakingFeedbackCompatibilityReaderTest`,
  `SpeakingEvaluationApplicationServiceTest`, `SpeakingEvaluationReusePolicyTest`;
- `PracticeResultPresenterTest`, `PracticeServiceTest`,
  `PracticeFunctionalUiContractTest`, `PracticeIntegrationTest`,
  `SpeakingResultRenderingContractTest`.

Current-source documentation changed by UX-05:

- `CODEX_PRACTICE_WORKFLOW.md`, the Phase 13 implementation gate and execution
  blueprint, and the Phase 13E live log/fixture notes;
- `KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`,
  `PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`, the
  practice architecture manifest, and the Speaking class/sequence diagrams.

Fail-closed hardening decisions:

1. `AUDIO_DIRECT_FULL_RESERVED` remains only a future seam. A matching reserved
   mode/version can no longer become `CURRENT_VERIFIED`, cannot satisfy
   `currentEvidenceContract()` and cannot expose a score.
2. The pre-capability `SpeakingEvaluationResult` constructor now always creates
   `LEGACY_UNKNOWN / UNKNOWN / null / LEGACY_UNVERIFIED`. Every audited current
   production/test caller uses the explicit canonical constructor; legacy
   readers remain bounded readers rather than auto-upgraders.
3. `SpeakingFeedbackViewMapper` handles null/unknown rubric criteria without a
   dereference and suppresses score/max/percentage for untrusted, non-scored,
   acoustic-unavailable and malformed rows.

Cross-skill reconciliation:

- no representative Speaking evaluation now means unknown/unverified
  provenance, not a fabricated current capability; pending/failed/unanswered
  rows remain numberless;
- only a complete current transcript result contributes Speaking coverage and
  criterion aggregation. Legacy ESSAY/mixed/reserved copy cannot raise
  readiness/trust or manufacture a holistic score;
- Objective aliases (`MCQ`, `MCQ_SINGLE`, `TFNG`, `GAP_FILL`) are grouped and
  labelled by canonical type before aggregation. Unsupported values enter one
  explicit unscorable bucket and raw English codes are not used as labels;
- Writing detail CTA is available only for current route-selectable `ESSAY`
  tasks. Historical objective Writing remains visible/scored from its immutable
  answer contract but shows a responsive status instead of a misleading link;
- templates/DTO/presenters were statically rechecked for headings, typed empty/
  partial/pending/failed/legacy states, 360-390px stacking, focus/ARIA, route
  truthfulness and absence of new band/radar/IELTS claims. Phase 13E four-tab
  detail was not implemented.

Debt-clarity audit and crosswalk:

- the Phase 15 inventory Section 4.1.1 maps the design, workflow action, exact
  source owner and proof for Speaking, Writing, R/L, rules, detail/reference,
  bundle/cache and database/seed decisions;
- PRE-01 is updated to the implemented-pending transcript guard and retains the
  real direct-audio `NO-GO`; PRE-07..13 give separate calibration, privacy,
  bundle identity and source-backed operational owners/proofs;
- PRE-14 owns the future versioned field-language matrix, Korean construct
  registry, SME sign-off and calibration. Phase 13D/13E provide only bounded
  fail-closed evidence and learner-language UI proof; they do not claim to
  enumerate all Korean or close PRE-14 early;
- COMP-18 separately retires Writing local 1–9; COMP-19 removes word-count
  simulated Speaking scores; COMP-20 removes unreachable Writing mock fallback;
  COMP-21 records the grouped-transcription compatibility decision. These are
  not implemented or physically deleted in Phase 13D;
- historical six-criterion/holistic statements remain dated evidence. Current
  workflow/design/inventory wording now points to F06/UX-05 rather than
  rewriting history as if it never occurred.

Validation is deliberately deferred. Recommended coordinator gate targets are:

1. static whitespace/source check and one JDK 17 compile;
2. focused Speaking constructor/compatibility/mapper/normalizer/reuse/provider/
   readiness tests, including reserved, malformed, mixed and legacy envelopes;
3. `PracticeResultPresenterTest`, `PracticeServiceTest`,
   `PracticeFunctionalUiContractTest`, `PracticeResultWordingTest` and the mixed
   Writing detail controller cases in `PracticeIntegrationTest`;
4. the phase-level integration/full-suite selector already owned by the
   coordinator, followed by responsive/a11y browser evidence only in its
   authorized gate.

No unit/integration/full test, Maven/Gradle compile/build, startup, lint,
frontend/Docker/database/migration/browser/provider check, `git diff --check`,
stage, commit, push or pull ran in UX-05.

## 13. Final pre-validation hardening and PREP/KSH boundary

Status: `IMPLEMENTED_PENDING_PHASE_VALIDATION`

The final static reconciliation extends UX-05 without opening Phase 13E. It
closes the active Phase-13D claim/evidence leaks that would otherwise make a
transcript-only Speaking result look like a complete audio-scored result:

1. `TRANSCRIPTION_LOW_CONFIDENCE` is authoritative and fail-closed. The
   orchestrator cannot overwrite it with `EVALUATED`; the normalized/current
   envelope has no rubric profile, criterion subtotal, holistic score or
   attempt score. Transcript provenance, confidence and a warning may remain.
2. A trusted score-bearing transcript envelope has exactly six rubric rows:
   four canonical transcript-language rows marked `SCORED` at their own
   `20/20/15/15` maxima and two acoustic rows with null score/max marked
   `NOT_SCORABLE`. Missing, duplicate, malformed, reserved or pre-capability
   rows downgrade to unverified and cannot contribute a number.
3. Detailed Speaking evidence is accepted only from the authoritative
   actually-heard transcript. `TEXT_SPAN` must be a non-empty exact substring
   and backend derives bounded offsets; repeated spans are located
   deterministically. `WHOLE_ANSWER` has empty evidence and no highlight.
   Provider-authored `TASK_METADATA`, prompt/intent evidence, forged offsets,
   nonexistent spans and wrong parent/subcriterion mappings are rejected.
   The same rules apply when typed JSON is read from persistence.
4. The active legacy detail receives only a bounded safety guard: Speaking
   chips are rendered in backend order from validator-accepted rows whose
   profile is available and whose availability is `SCORED`; browser JavaScript
   no longer owns a Speaking criterion registry, hardcodes acoustic tabs or
   prints raw criterion IDs/provider display names as learner headings. The
   screen states once that the profile is transcript-only. The complete
   replacement remains the separately authorized Phase-13E three-screen work.
5. Result, catalog, test detail, progress, latest/best/history and state copy
   distinguish a processed Speaking feedback profile from a full score.
   Speaking activity still counts as a completed attempt, but it cannot enter
   averages, score trends, rankings or a fabricated zero.

The PREP evidence boundary is explicit and non-negotiable. KSH may learn from
PREP's information architecture: compact chips, grouping, navigation,
split-pane layout and progressive disclosure. KSH does **not** import PREP's
IELTS taxonomy, criterion names, band descriptors, score formulas, labels,
content or product claims. A chip is only a presentation/navigation primitive;
its identity, label, order, parent mapping, count and availability come from a
versioned KSH Korean task-native policy plus backend-validated evidence. The
Phase-13D bounded registry is not a claim to cover every historical or modern
form of Korean; the full supported-domain/construct/calibration contract remains
`P15-PRE-14`.

Additional production files covered by this reconciliation include:

- `SpeakingEvaluationNormalizer.java`, `SpeakingEvaluationResult.java`,
  `SpeakingEvaluationOrchestrator.java`, `SpeakingRubricCriterion.java`,
  `SpeakingEvaluationPromptBuilder.java`, `SpeakingPromptRules.java` and
  `SpeakingEvaluationStatus.java`;
- `SpeakingFeedbackCompatibilityReader.java`,
  `SpeakingFeedbackViewMapper.java`, `PracticeResultAssembler.java`,
  `PracticeService.java`, `PracticeCatalogService.java` and
  `PracticeDetailPageService.java`;
- `templates/practice/result.html`, `result-detail.html`, `progress.html` and
  `static/js/practice-progress.js`.

Adversarial/current-contract test source was updated for low-confidence,
nonexistent/repeated spans, forged offsets, non-empty whole-answer evidence,
unauthorized metadata, wrong parent mappings, exact six-row profiles,
persisted compatibility reads, backend-driven detail chips and nonnumeric
Speaking history/status copy. These tests have not run yet.

The single deferred phase gate remains:

1. `git diff --check`;
2. one JDK-17 compile;
3. one focused test command covering all changed Phase-13D presenters,
   services, templates/contracts and Speaking evaluator/evidence boundaries,
   including the two bounded controller integration cases;
4. no full suite, provider call, browser QA, migration rehearsal or application
   startup unless the focused gate proves that broader validation is required.

No validation or Git mutation was performed while recording this handoff.

## 14. Consolidated validation result

Status: `FOCUSED_NON_DB_GATE_GREEN_WITH_2_ENVIRONMENT_BLOCKED_INTEGRATION_CASES`

The coordinator declared `READY_FOR_PHASE_VALIDATION` only after all five
implementation slices, the combined static audit and the grouped Speaking
contract fixes were complete. The final JDK 17 validation result is:

1. `git diff --check`: passed with no output;
2. compile: `BUILD SUCCESS`, 591 production source files compiled for Java 17;
3. focused non-database gate: 251/251 tests passed, with zero failures, errors
   or skips.

The focused selector covered the shared Result contract, Objective/Writing/
Speaking presenters, catalog/detail/progress services, Speaking readiness,
provider/client, prompt, rule engine, normalizer, evidence validation,
compatibility reader, view mapper, reuse and rendering contracts. The first
post-fix run found one scale-sensitive test assertion (`20` versus `20.0`);
after analyzing the complete run, the assertion was changed to compare
`BigDecimal` values numerically and the same 251-test selector passed once.
Production scoring logic was not changed for that correction.

Two selected authenticated route cases were attempted but did not execute:

- `PracticeIntegrationTest#testWritingResultDetailDoesNotRestoreMcqQuestionId`;
- `PracticeIntegrationTest#testSpeakingResultDetailDoesNotRenderPerQuestionReEvaluateForm`.

The configured isolated datasource was `ksh_phase13e_result_ui`. Flyway
reported V44, but Hibernate validation stopped application-context creation
because table `tests` lacks `media_type`; neither test reached setup or an
assertion. They are therefore not counted as passed. The mismatch is consistent
with that fixture schema having consumed an older feature-branch V38 while the
integrated migration chain uses V38 for test media and V44 for the Listening
fixture. Existing service, presenter, static template and functional contract
tests cover the underlying guards, but they do not replace the two authenticated
controller/render assertions exactly.

No schema history was altered, validation bypassed, migration replayed or
fixture database written as a workaround. Exact execution requires a fresh
disposable schema migrated from the current chain; that is a separate database
authorization/gate. Consequently this correction must not be labelled
`COMPLETE_FOCUSED_GATE_GREEN`. Phase 13E remains unopened and PREP remains an
information-architecture/chip interaction reference only; KSH Korean policy
and backend-validated evidence remain the sole assessment authority.
