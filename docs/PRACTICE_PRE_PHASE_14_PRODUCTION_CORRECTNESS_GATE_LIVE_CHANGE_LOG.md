# Practice Pre-Phase-14 Production Correctness Gate Live Change Log

Started: `2026-07-30`

Status: `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE_IN_PROGRESS`

## 1. Authority, sequencing and baseline freeze

This log is append-only evidence for
`PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE`. It is not Phase 14 Report an Error,
Pre-15 or Phase 15. Deferred Phase 14 remains after Phase 15 Manual UAT under
`docs/PRACTICE_PHASE_14_POST_MANUAL_UAT_ROADMAP_AMENDMENT.md`.

The historical workflow describes Pre-14 as an inspection gate that must leave,
correct a grouped blocker and re-enter. The current user instruction explicitly
authorizes those bounded correctness corrections to be implemented and
validated in this same task. That authorization does not permit unrelated
features, live provider qualification, destructive retained-data cleanup,
Report-an-Error work or a merge to `main`.

The exact pre-mutation baseline is:

```text
branch: feature/practice-pre14-production-correctness-gate
HEAD: 55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4
origin/main: 55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4
tree: 2ea0f357e60e8577d450ec3787b4206ed1c954e1
dirty paths before this live log: 0
real AI/STT/TTS/ingestion calls: 0 / 0 / 0 / 0
```

Mandatory authority read before this file was created:

- Comprehensive live log Sections 20.32--20.34 and its final
  `COMPREHENSIVE_VALIDATION_GREEN_AWAITING_PUBLICATION_REVIEW` evidence;
- `CODEX_PRACTICE_WORKFLOW.md`,
  `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE`;
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`, the matching gate;
- `docs/PRACTICE_PRE_PHASE_14_COMPREHENSIVE_AUDIT_AND_DEAD_SURFACE_CLEANUP.md`;
- `docs/PRACTICE_PHASE_14_POST_MANUAL_UAT_ROADMAP_AMENDMENT.md`;
- current Writing, Speaking, R/L, prompt/response, normalizer and PREP sections
  in `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`;
- the exact post-correction 47-row PREP authority matrix at
  `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/29/019faeb2-17e2-7600-af13-1c78773803a5/prep-golden/acceptance/post-consolidated-47-font/post-correction/PREP-47-authoritative-matrix-post-correction.{json,md}`.

The inherited PREP ledger is exactly:

```text
rows: 47
PASS: 2
FAIL_VISUAL: 0
BLOCKED_SEMANTIC: 45
BLOCKED_REFERENCE_ACCESS: 0
```

No row may move merely because CSS matches. A row moves only when the relevant
semantic contract, backend verifier, result binding and browser artifact all
pass. Deferred chatbot/report rows remain blocked and are not Pre-14 scope.

## 2. Frozen implementation slices

The following order is dependency-locked before production mutation. Each
slice is one bounded correctness owner; later evidence may narrow its manifest
but may not silently drop an acceptance condition.

| Slice | Acceptance | Schema / route / DTO / UI impact | Compatibility strategy | Migration impact | Focused selector |
|---|---|---|---|---|---|
| `A — BASELINE_AND_CONTRACT_AUTHORITY_FREEZE` | Exact main ancestry, current source owners, active/legacy contract identities and PREP blockers are recorded; no unresolved authority contradiction | Documentation and read-only source inventory only | Historical facts are preserved; current user instruction supersedes only the verification-only execution mechanics | None | Static Git, source-owner and migration inventory |
| `B — WRITING_EVIDENCE_LEDGER_AND_SCORE_RECONCILIATION` | Q51/Q52/Q53/Q54/GENERAL use versioned score anchors; Q53 keeps `12/9/9`, Q54 keeps `20/15/15`; `taskCoverage[]`, authoritative UTF-16 evidence spans, atomic findings, criterion/requirement linkage and contradiction rejection are enforced; summaries and upgraded-answer links derive from verified facts | Writing provider DTO/schema/prompt/normalizer/cache identity/result presenter and Writing Overview/Detail DTO/template; no new learner route | New writes use the strict current version; the existing read-only historical 1–9 adapter stays isolated only until its explicit retirement scan passes; malformed/missing/unknown fields fail closed | Forward `V73+` only if persistence needs new immutable identity/columns; never rewrite V1–V72 | Writing policy/prompt/client/normalizer/cache/presenter tests; repeated/out-of-order UTF-16 spans; Q53 exact ledger fixture; contradiction and legacy-reader tests |
| `C — RL_TYPED_STRATEGY_AND_LECTURER_SELECTION` | Versioned allowlisted strategy is selected by lecturer per supported question type; typed strategy blocks reference immutable source/answer/evidence IDs; draft/preview/approve/publish/result round-trip is authoritative; group hierarchy remains immutable | R/L authoring DTO/controller/service/editor, explanation request/schema/normalizer/artifact binding/read model and Objective Detail renderer; existing learner routes remain read-only | Existing v3 artifacts remain bounded dual-read; new writes require the new strategy version; no free HTML/string fallback; retirement gate is caller + retained-artifact proof | Expected forward `V73+` for immutable selected-strategy/version snapshot if current schema lacks it | R/L client/schema/normalizer/lifecycle, editor selection/publish, artifact binding, group hierarchy, result renderer and authorization tests |
| `D — SPEAKING_TRANSCRIPT_ACOUSTIC_TRUTH` | Transcript ledger permits only grammar/vocabulary/collocation/repetition/register/coherence evidence with exact spans; audio criteria require asset identity plus timestamps/alignment/acoustic evidence; unsupported capability yields null/`NOT_SCORABLE`, never inferred/fake score | Speaking request/result/schema/normalizer/capability profile/presenter and Overview/Detail DTO/template; no direct-audio rollout | Current transcript-only artifacts remain readable; new outputs with acoustic claims but no approved evidence fail closed; no score backfill | None unless immutable evidence provenance is absent and a reviewed forward migration is unavoidable | Speaking prompt/client/normalizer/compatibility/presenter tests, wrong-parent/span/audio-capability fail-closed cases |
| `E — RESULT_SEMANTIC_MAPPING_AND_PREP_ACCEPTANCE` | Overview and Detail render backend-derived facts; Writing has one-number ↔ one-minimal-span ↔ one-card; R/L renders selected typed strategy; Speaking displays unavailable acoustic rows truthfully; visual and semantic verdicts remain separate | Three typed result presenters/templates plus bounded shared result JS/CSS; Korean font selector, seven redistributable fonts, font size and UI-dev F5 contract remain intact | No generic provider JSON parser or browser-owned inference; additive display DTO evolution with explicit legacy labels only where retained history requires them | None | Presenter/template/resource/accessibility tests plus affected PREP rows at exact viewport and responsive/keyboard checks |
| `F — FORWARD_MIGRATION_AND_TECHNICAL_FIXTURES` | Evidence proves whether rebaseline is rejected; V1–V72 stay byte-immutable; any accepted schema change is V73+; minimal disposable R/L/W/S and lecturer-strategy fixtures prove immutable identity | Migration plus narrowly dependent entity/repository/fixture files only | Fresh-database proof and dual-read/new-write strategy; no repair, clean, real DB or retained-data reset | Rebaseline default: `REJECT_UNLESS_NO_OBLIGATION_PROVEN`; forward V73+ preferred | Migration contract, fresh Flyway V1–latest, Hibernate validation and immutable fixture smoke |
| `G — CONSOLIDATED_CORRECTNESS_SECURITY_PROVIDER_DISABLED_GATE` | Static scans, package, all focused selectors, fresh Flyway/Hibernate/runtime/authenticated R/L/W/S smoke and full suite pass once; ownership, authorization, immutable versions, answer leakage, cache/artifact identity and provider-disabled behavior remain correct | No new feature scope | Unknown/missing fields fail closed; real provider calls remain zero; branch publication may follow, merge may not | Validate latest forward chain on disposable catalog only | One Java 17 lifecycle with bounded Hikari/test-context settings; AI/STT/TTS/ingestion `0/0/0/0` |

## 3. Locked correctness decisions

1. Q53 and Q54 remain KSH task-native profiles with weights `12/9/9` and
   `20/15/15`. Adding a score anchor for each allowed score does not rewrite
   those weights. The UI must not call them official TOPIK weights.
2. Exact spans are authoritative UTF-16 offsets over a named normalized source
   snapshot. Provider evidence text is not searched with `String.indexOf`.
3. Score judgments, findings and task coverage form one reconciled contract.
   A maximum score with an unreconciled confirmed negative finding is invalid.
4. `OVERVIEW`, `STRENGTHS`, `NEEDS_IMPROVEMENT` and `UPGRADED_ANSWER` are
   backend-derived views over verified IDs; ungrounded provider praise is not a
   learner-facing fact.
5. R/L strategy is allowlisted, versioned and question-type-specific. Official
   answer and immutable source remain backend authority.
6. Transcript-only Speaking never emits pronunciation, stress, phoneme,
   rhythm, pause, delivery or acoustic-fluency judgments.
7. OPENAI_PRIMARY remains one provider family with capability-specific slots.
   This gate uses provider-disabled fixtures only; no live model ID is locked.
8. Binary/audio/image payloads use immutable asset references or the
   capability-appropriate transport boundary, with size/timeout/idempotency
   and log-redaction guards; large binary content is not nested as JSON text.
9. Published V1–V72 migrations are immutable. The current repository has real
   retained upgrade obligations, so a Practice rebaseline is presumed unsafe
   unless the audit produces contrary proof; a forward migration is the
   default.
10. Branch publication may create a review PR after green. Merge, bypass and
    Pre-15/Phase 15 remain unauthorized.

## 4. Current gate status

`A — BASELINE_AND_CONTRACT_AUTHORITY_FREEZE` is in progress. Production
mutation remains blocked until the exact current source-owner manifest and
accepted implementation slices are adjudicated below.

## 5. Read-only lane adjudication and accepted source manifest

Three independent bounded lanes completed on exact HEAD
`55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4`. They performed no edits,
builds, tests, database operations or provider calls. The owner independently
verified their cited production paths before accepting the findings.

| Lane | Deduplicated verdict | Accepted facts | Rejected expansion |
|---|---|---|---|
| Writing | `FIX_REQUIRED` | Task-native maxima are already correct; anchors, task coverage, authoritative offsets/occurrences, referential finding/evidence IDs, contradiction rejection, derived summary and one-to-one result mapping are absent. Both the normalizer and current UI derive separate synthetic identities. | Do not change Q53 `12/9/9` or Q54 `20/15/15`. Do not delete retained historical JSON or rewrite immutable migrations. |
| R/L | `BLOCKER_BEFORE_PRE14_CLOSURE` | v3 strict type-specific blocks, exact text/image evidence validation, immutable source hierarchy and deterministic option state are reusable. Lecturer-selected strategy/version, claim-level evidence links, editorial approval and learner-visible published binding are absent. | Do not replace working v3 evidence primitives, invent unsupported matching/table/sequence question types or infer a strategy from prose. |
| Speaking | `PARTIAL_NO_GO` | The transcript/acoustic boundary is already fail-closed and must remain. Transcript offsets are still inferred, findings/cards lack stable shared IDs, summaries/scores are not evidence-reconciled, and legacy evaluator enablement can disagree with the OPENAI_PRIMARY port. | Do not activate direct audio, create acoustic scores, qualify live models or migrate Practice into Admin/common AI. |

The accepted production manifest is frozen as follows. A file may be removed
from a slice after static proof that no change is required; any new production
path must be appended here with a reason before mutation.

### 5.1 Writing manifest

- scoring/authority:
  `WritingScoringCriterion.java`, `WritingScoringRubric.java`,
  `WritingScoringPolicy.java`, new Practice-owned score-anchor,
  task-requirement and evidence-ledger contract classes;
- provider/normalization:
  `WritingPromptRules.java`, `WritingEvaluationClient.java`,
  `WritingDiagnosticContract.java`, `WritingEvaluationNormalizer.java`,
  `WritingAssessmentPolicyBundle.java`, `WritingFeedbackCompatibilityReader.java`,
  `WritingFeedbackViewMapper.java`;
- immutable task input:
  `QuestionContent.java`, `AssessmentContractCodec.java`,
  `PracticeDraftContractService.java`, `PracticePublisherService.java`,
  `practice/manage/editor.html`; the existing question-content JSON columns are
  preferred over a new relational table;
- result:
  `PracticeDtos.java`, `WritingResultPresenter.java`,
  `practice/result.html`, `practice/result-detail-writing.html`,
  `practice-result.js` and only the result CSS needed for the exact linked state;
- tests: the corresponding policy, prompt, client, normalizer, compatibility,
  cache, authoring/publisher, service, result DTO/presenter/template and
  adversarial UTF-16/occurrence/contradiction fixtures.

### 5.2 Reading/Listening manifest

- new strategy registry/selection and typed v4 claim/block DTOs;
- `ExplanationArtifactInput.java`, `ExplanationContext.java`,
  `ExplanationInputFactory.java`, `ExplanationFingerprintBuilder.java`,
  `ReadingListeningExplanationClient.java`,
  `QuestionExplanationReadService.java`;
- `PracticeQuestion.java`, `PracticeQuestionVersion.java`,
  draft normalization/validation/publisher/editor owners;
- editorial artifact/revision service, repository and scoped lecturer
  controller; the existing active binding remains the learner-visible pointer;
- `PracticeDtos.java`, `ObjectiveResultPresenter.java`,
  `practice/result-detail-objective.html`;
- one additive forward migration, initially reserved as `V73`, for immutable
  strategy identity and append-only editorial approval state;
- the existing R/L typed-client, fingerprint/input, lifecycle, authoring,
  authorization, result and integration selectors plus new strategy/approval
  regressions.

Compatibility is bounded: v3 is retained read-only and receives no new writes
after v4 activation; v2 remains historical single-choice only and must fail
closed when its quoted evidence occurs more than once. Legacy drafts may open,
but republish requires an explicit strategy selection.

### 5.3 Speaking manifest

- `SpeakingEvaluationResult.java`, `SpeakingEvaluationNormalizer.java`,
  `SpeakingEvaluationPromptBuilder.java`, `SpeakingPromptRules.java`,
  `SpeakingAssessmentPolicyBundle.java`;
- `SpeakingFeedbackCompatibilityReader.java`,
  `SpeakingFeedbackViewMapper.java`,
  `SpeakingEvaluationApplicationService.java` and only the Practice-local
  OPENAI_PRIMARY identity/config seam needed to make enabled/model authority
  agree with the structured port;
- `SpeakingResultPresenter.java`, `PracticeDtos.java`,
  `practice/result.html`, `practice/result-detail-speaking.html`;
- corresponding prompt/client/normalizer/compatibility/reuse/application,
  score-policy/result/template/integration selectors and adversarial
  transcript-ledger fixtures.

No Speaking schema migration is accepted at this point: the new versioned
envelope remains in existing attempt/evaluation JSON. Acoustic capability stays
unavailable; only typed `NOT_SCORABLE` truth is closed here.

### 5.4 Contract choices frozen before mutation

1. New Writing provider output is a strict referential envelope. The provider
   does not own learner-facing overview prose. Backend verification precedes
   score availability, summary synthesis, cacheability and rendering.
2. Provider-supplied UTF-16 offsets are verified with exact substring,
   occurrence index/count, NFC source identity and source SHA-256. They are
   never inferred from evidence text.
3. Finding count alone never subtracts points. A confirmed negative linked to
   a maximum-scored criterion, or unmet required coverage linked to maximum
   task-achievement score, is a contract contradiction and fails closed.
4. R/L registry v1 starts with:
   `EVIDENCE_ONLY`, `ELIMINATE_ALL_INCORRECT`,
   `FULL_CONTEXT_THEN_ANSWER`, `HYBRID`,
   `CLAIM_EVIDENCE_RELATION` and `CONSTRAINTS_AND_EVIDENCE`.
   Table/sequence/matching strategies remain unavailable until a typed
   immutable construct exists.
5. R/L generation produces an editorial draft. Only an approved exact
   fingerprint/payload may become learner-visible. Source, answer, strategy or
   typed edit changes invalidate approval.
6. New Speaking transcript findings use provider-supplied exact offsets and
   stable IDs. Acoustic criteria remain null/`NOT_SCORABLE`; legacy 1–9
   envelopes remain retained raw history but cannot authorize current score,
   summary, cards or upgrades.

`A — BASELINE_AND_CONTRACT_AUTHORITY_FREEZE` is now `COMPLETE`.
Slices B, C and D are accepted for implementation in dependency order.
Provider call counters remain `0 / 0 / 0 / 0`.

## 6. Accepted implementation result before consolidated validation

The owner completed the accepted B--F manifests on the unchanged baseline
`55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4`. The current worktree contains
`82` actual dirty paths (`69` tracked modifications and `13` untracked paths,
including this log). No commit, push, PR or merge has occurred.

### 6.1 Writing authority

- `WritingScoreAnchorPolicy` defines task-native anchors without changing Q53
  `12/9/9` or Q54 `20/15/15`.
- `WritingTaskRequirementPolicy` owns the versioned Q53 checklist. The result
  read model exposes backend-labelled `taskCoverage[]` with requirement
  status and verified evidence IDs; it is explicitly not a second scoring
  scale.
- `WritingEvidenceLedgerVerifier` validates stable finding/evidence IDs,
  authoritative UTF-16 offsets, exact minimal substrings, occurrence
  index/count, NFC normalization, source SHA-256 and referential criterion /
  requirement links. Repeated or out-of-order evidence is deterministic and
  malformed evidence fails closed; no `String.indexOf` position guessing is
  retained in the current contract.
- The normalizer rejects maximum criterion scores that contradict a confirmed
  linked improvement or unmet required coverage. Learner summaries,
  strengths, improvements and upgraded-answer links are derived from verified
  ledger facts instead of ungrounded provider praise.
- Writing Result Detail renders stable one-number to one-span to one-card
  mappings and the verified coverage checklist.

### 6.2 Reading and Listening authority

- `ObjectiveExplanationStrategyRegistry` owns the versioned allowlist:
  `EVIDENCE_ONLY`, `ELIMINATE_ALL_INCORRECT`,
  `FULL_CONTEXT_THEN_ANSWER`, `HYBRID`,
  `CLAIM_EVIDENCE_RELATION` and `CONSTRAINTS_AND_EVIDENCE`.
- Immutable question and question-version snapshots carry selected strategy
  identity. Generation requests contain authoritative source, option, answer
  and typed evidence context; mismatched or extra evidence fails closed.
- `ObjectiveExplanationEditorialService` and forward migration
  `V73__practice_objective_explanation_editorial_authority.sql` add
  append-only draft/edit/approve/publish ownership. Only an approved exact
  fingerprint may become learner-visible; edits or source/answer/strategy
  changes invalidate approval.
- Result Detail renders the published typed strategy while retaining immutable
  group/source/question hierarchy and deterministic option-state precedence.
  Result GET remains read-only and provider-disabled.

### 6.3 Speaking authority

- The current envelope has stable evidence/finding IDs, authoritative
  transcript offsets, occurrence identity, criterion/subcriterion linkage and
  transcript source hash.
- Transcript findings are limited to language/content evidence. Pronunciation,
  stress, phoneme, rhythm, pause, delivery and acoustic fluency remain
  `NOT_SCORABLE` with null scores unless an authorized audio/timestamp/alignment
  capability exists; this gate does not enable that capability.
- Speaking Overview and Detail expose only typed atomic positive/negative
  findings. Provider `overallSummary`, generic strengths/improvements and
  unlinked action-plan prose are not promoted as learner-facing facts.
  Backend-derived summaries state the exact transcript-only boundary.
- Action plans and cards carry the exact finding/evidence identity. Legacy
  retained envelopes remain readable history but cannot authorize current
  summary, score or acoustic claims.

### 6.4 OPENAI_PRIMARY authority reconciliation

Practice structured generation remains one provider family with
capability-specific model slots. Writing, R/L and Speaking generation use
`PracticeStructuredGenerationPort`; no Admin/common consumer or credential /
storage authority was adopted.

Speaking cache/reuse identity now obtains provider availability and the
assessment model from the `ASSESSMENT_TEXT_VISION` port identity. The legacy
Speaking property object retains only bounded enable/version/timeout
compatibility inputs and no longer owns the production model identity.
Application defaults and rollout-readiness allow only `openai-primary`; no
Gemini default or fallback remains in the active Practice configuration.
Live qualification and exact model locking remain Phase 15D work.

## 7. Focused implementation checkpoints

These are implementation checkpoints, not the final consolidated gate:

```text
Writing + Speaking semantic selectors:     175 / 175 PASS
Reading/Listening semantic selectors:       93 / 93 PASS
Result DTO/presenter/normalizer checkpoint: 100 / 100 PASS
OPENAI_PRIMARY authority/adapter/reuse:      43 / 43 PASS
failures/errors/skips in green runs:          0 / 0 / 0
real AI/STT/TTS/ingestion calls:              0 / 0 / 0 / 0
```

The first 43-test provider-authority attempt exposed seven stale test-fixture
expectations because the fixtures still advertised `openai-compatible`.
Production fail-closed behavior was not relaxed. One concentrated fixture
correction made the fixtures advertise the current `openai-primary` identity;
the same 43-test selector then passed.

Current slice state:

| Slice | State before consolidated validation | Remaining proof |
|---|---|---|
| A | `COMPLETE` | final ancestry/status record |
| B | `IMPLEMENTED_FOCUSED_GREEN` | consolidated package/runtime/full suite and browser artifacts |
| C | `IMPLEMENTED_FOCUSED_GREEN` | fresh V1--V73 Flyway/Hibernate/runtime and browser artifacts |
| D | `IMPLEMENTED_FOCUSED_GREEN` | consolidated provider-disabled/full suite and browser artifacts |
| E | `IMPLEMENTED_FOCUSED_GREEN_SEMANTIC_ARTIFACTS_OPEN` | current-contract Overview/Detail captures; do not reuse CSS-only evidence |
| F | `IMPLEMENTED_UNVALIDATED` | fresh disposable V1--V73 execution |
| G | `READY_FOR_PRE14_VALIDATION` | run the single authorized lifecycle |

## 8. PREP acceptance disposition before browser recapture

The inherited 47-row matrix remains authoritative and unchanged:

```text
visual PASS:       2
BLOCKED_SEMANTIC: 45
FAIL_VISUAL:       0
reference blocked: 0
```

Implementation has removed several recorded semantic causes, but no row is
promoted merely from source or unit-test review. New same-viewport browser
artifacts are still required for those rows. Speaking holistic/acoustic score,
pronunciation, stress and fluency rows remain honestly blocked; chatbot and
Report-an-Error rows remain deferred and were not implemented. The open browser
currently proves the real provider-disabled R/L Detail route and immutable
group/option/typed-explanation fail-closed rendering, not a 47-row visual PASS.

`READY_FOR_PRE14_VALIDATION` is now declared. The next action is exactly one
Java 17 static/package/focused/fresh-Flyway/runtime/provider-disabled/full-suite
lifecycle using a newly named disposable catalog. Pre-15, Phase 15 and
Report-an-Error remain closed.

## 9. Consolidated lifecycle and bounded correction attempt

This section supersedes the readiness label at the end of Section 8. It does
not erase the focused-green implementation evidence recorded above.

### 9.1 First consolidated lifecycle

The first full-suite execution used Java 17, Hikari maximum `2`, minimum `0`,
Spring test-context cache `4`, a fresh disposable catalog and all Practice
providers/workers disabled. Fresh Flyway V1--V73, package, authenticated
provider-disabled R/L/W/S runtime routes and the preceding focused selector
were green. The full suite then completed with:

```text
tests:    2715
failures: 31
errors:   16
skipped:   0
MySQL 1040 / connection-exhaustion failures: 0
real AI/STT/TTS/ingestion calls:             0 / 0 / 0 / 0
```

The failures were compatibility fixtures and active Writing-consumer identity,
not the prior environment exhaustion. Per the correction policy, they were
deduplicated into one correction group before any rerun.

### 9.2 Concentrated correction group

The correction group made the following bounded changes:

- Active `PracticeService` and `PracticeProgressService` Writing consumers no
  longer identify `KSH_WRITING_EVALUATOR_V2` as current. They require the V3
  evaluator, V3 policy bundle, ledger/anchor/requirement versions, NFC source
  identity and the typed ledger arrays.
- Source-contract tests were reconciled with the typed R/L and transcript-only
  Speaking implementations without weakening production fail-closed behavior.
- Phase-10 publication fixtures now select and approve a typed R/L explanation
  strategy.
- The current Excel workbook adds an explicit
  `explanation_strategy_code` column and typed strategy object. Historical
  workbooks remain importable only as draft with
  `EXPLANATION_STRATEGY_REQUIRED`; publication still requires an explicit
  lecturer choice. No silent strategy default was added to historical input.
- Legacy flat Writing per-question re-evaluation is expected to fail closed
  instead of being promoted into the current envelope.

### 9.3 Focused correction gate result

The correction selector compiled `860` production and `371` test sources and
ran against the fresh disposable catalog
`ksh_test_pre14_v73_correction` with the same bounded pool/context and
provider-disabled settings.

```text
fresh Flyway: 73 / 73 successful, 0 failed
selected test classes: 10
tests:    190
failures: 24
errors:    3
skipped:   0
Excel authoring/import tests: 11 / 11 PASS
real AI/STT/TTS/ingestion calls: 0 / 0 / 0 / 0
git diff --check: PASS
```

The gate is not green, so the full suite was not run again.

Deduplicated remaining root causes:

1. `WritingContractTestFixtures`-based score fixtures mark coverage as met but
   do not create a linked negative finding or unmet requirement for partial
   rubric anchors. `WritingEvidenceLedgerVerifier` correctly rejects such
   partial scores as contradictory. This single invalid base envelope causes
   the three `WritingTaskNativeScoringTest` failures and most downstream
   `PracticeServiceTest` / `PracticeProgressServiceTest` failures, because the
   generated fixture becomes a non-score-bearing contract-failure envelope.
2. The legacy flat single-Writing re-evaluation test now reaches a null
   per-question object and throws `IllegalStateException` instead of the
   canonical `PracticeAttemptConflictException`. Production needs one bounded
   fail-closed error-path correction; legacy data must not be upgraded.
3. Expectations derived from a mutated invalid base envelope (retryable score,
   progress exclusion subtype, conflict fingerprint and aggregate score)
   cannot be adjudicated until root cause 1 produces a valid V3 fixture.

No PREP row is promoted by this run. The matrix remains:

```text
visual PASS:       2
BLOCKED_SEMANTIC: 45
FAIL_VISUAL:       0
reference blocked: 0
```

Current verdict:

`PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE_NO_GO`

Pre-15, Phase 15 and Report-an-Error remain closed. No commit, push, PR or
merge was performed.

## 10. Correction supersession and consolidated-green checkpoint

This append-only section supersedes the Section 9.3 implementation verdict
without rewriting its failure evidence.

The single correction pass preserved the production
`WritingEvidenceLedgerVerifier` fail-closed rules and corrected the shared V3
fixtures at their authority boundary. Partial-score fixtures now carry an
exact confirmed finding or unmet/partial requirement linked to the deducted
criterion. Full-score fixtures remain contradiction-free, and an ungrounded
partial score is still rejected. The exact stale legacy Writing
re-evaluation path now raises the canonical
`PracticeAttemptConflictException`; unrelated `IllegalStateException` paths
were not blanket-replaced.

The corrected focused selector passed `190/190`. The subsequent authorized
consolidated lifecycle then passed:

```text
compile:                         860 production + 371 test sources
fresh Flyway:                    73 / 73
focused selector:               190 / 190
PracticeIntegrationTest:        119 / 119
full suite:                      2715 / 2715
failures / errors / skipped:     0 / 0 / 0
real AI/STT/TTS/ingestion calls: 0 / 0 / 0 / 0
```

That checkpoint applied to the frozen backend/contract snapshot before the
browser-visible Result Overview/Detail corrections described below. It is
retained as valid backend evidence but is not reused as final validation for
the later template/CSS source state.

## 11. Deterministic R/L/W/S UI acceptance seed

The existing Phase-13 fixture was reused only as a historical base. A
DEV/TEST-only, idempotent current-contract seed and machine-readable manifest
were added:

- `PracticePre14UiAcceptanceSeedTest`;
- `PracticePre14UiAcceptanceScenarioManifestTest`;
- `practice/pre14-ui-acceptance-scenarios.json`;
- `PRACTICE_PRE14_UI_ACCEPTANCE_SEED.md`.

The seed uses the disposable `ksh_test_pre14_ui_*` catalog allowlist, contains
no secret/PII, creates no production migration, and performs no provider or
media call.

Seed dimensions:

```text
R/L scenarios:                    24
skills:                            2
source modes per skill:            2
question types per source mode:    3
active typed strategies rendered:  6
Writing questions:                 4 (Q51/Q52/Q53/Q54)
Writing cases per question:        4
Writing result states:            16
Speaking states:                   2
lecturer authoring drafts:         2
```

Browser evidence from the real current presenters:

- Reading attempt `14100` rendered group and standalone source modes plus all
  six typed strategy codes:
  `EVIDENCE_ONLY`, `ELIMINATE_ALL_INCORRECT`,
  `FULL_CONTEXT_THEN_ANSWER`, `HYBRID`,
  `CLAIM_EVIDENCE_RELATION`, `CONSTRAINTS_AND_EVIDENCE`.
- Listening attempt `14200` rendered immutable group/source/question
  hierarchy, transcript/audio fixture identity, and no provider call.
- Writing attempt `14301`, Q53, rendered six exact atomic Korean marks and six
  cards with the same stable finding IDs. It includes
  `45%에서 35%로 감소했고`, `10%에서 5%로 줄었다`,
  `20%에서 35%로 크게 증가했으며`, `반면`,
  `도보는 25%로 같았다`, and `때문이라고 볼 수 있다`.
- Speaking attempt `14401`, question `14405`, rendered zero optional
  diagnostic chips/findings while preserving all six mandatory score/status
  cards; the two acoustic criteria remain explicitly not scorable.

The first valid opt-in seed/focused invocation passed `27/27`:

```text
PracticeFunctionalUiContractTest:                 20 / 20
PracticeResultWordingTest:                         5 / 5
PracticePre14UiAcceptanceScenarioManifestTest:     1 / 1
PracticePre14UiAcceptanceSeedTest:                 1 / 1
Flyway validation in seed catalog:                73 / 73
```

## 12. Result presentation correction and validation reopening

User-observed browser defects were collected before mutation and corrected in
one bounded Result Overview/Detail presentation group:

- all result tabs and chrome are Vietnamese-only; Korean remains in Korean
  learning content and meaningful Korean question-type content;
- Speaking technical/source/evidence-scope data moved from the central reading
  flow into the compact `Thông tin kết quả` control beside `Hoàn tất`;
- the Writing score medal now exposes only the truthful primary number
  (`100`), while denominator/scale authority remains in the technical
  calculation details;
- the Writing Overview uses one overall score and natural Q51--Q54 part rows
  with task-native point badges and thin progress indicators, not four nested
  rectangular cards;
- PREP-blue owns the Writing analysis palette; the former green skill frame no
  longer leaks into the Overview;
- the notebook connector now physically touches the upper result sheet and
  lower analysis sheet (`0px` gap on both sides), with no compositor/separator
  stripe;
- the page background was softened to a lighter pastel blue and the paper
  surface changed from gray-white to near-white.

Laptop-large `1440x900` measurements after the correction:

```text
result sheet:              x=68, y=62, width=1304, height≈389
top / bottom whitespace:   48px / 48px
copy-to-results gap:       12px
score/task/mascot centerY: 314.1 / 314.1 / 314.1
horizontal overflow:       0
sheet-to-connector gap:    0
connector-to-analysis gap: 0
```

The same real PID served all template/CSS changes by browser F5; Maven was not
rebuilt between visual edits. Responsive visual judgment is deliberately not
reopened in this pass per the user's instruction; only the Laptop-large
viewport owns the current presentation disposition.

Because production template/CSS source changed after the Section 10
consolidated checkpoint, the final publication label is now:

`PRE14_VALIDATION_REOPENED_AFTER_RESULT_PRESENTATION_CORRECTION`

The affected UI/seed focused gate is `27/27` green and `git diff --check`
passes. A new final consolidated lifecycle is still required before branch
publication. No PREP row is promoted from source review alone; the inherited
row ledger remains `2 PASS + 45 BLOCKED_SEMANTIC` until same-state artifacts
are adjudicated row by row. No commit, push, PR, merge, Pre-15, Phase 15 or
Report-an-Error work was performed.

## 13. Writing Overview laptop-large composition supersession

This append-only entry supersedes only the Section 12 Writing Overview
measurements; it does not rewrite the earlier browser evidence or reopen
responsive acceptance.

The four user-selected PREP files were inspected at their intrinsic
dimensions. `Screenshot 2026-07-03 234310.png` and `...234340.png` are the
actual Writing Overview and rubric-composition authorities. The URLs and
visible criteria in `...234955.png` and `...235216.png` identify those two
files as Speaking result states, despite their parent folder; only their shared
wide-sheet/tab/score-rail presentation is used. Their IELTS/Speaking rubric is
not copied into KSH Writing.

The bounded laptop-large correction now provides:

- an upper result sheet that is deliberately narrower and shorter than the
  analysis sheet;
- one primary score, natural Q51--Q54 part rows, and no redundant visible
  `Kết quả theo nhiệm vụ` label;
- a lower wide Writing sheet with underline task tabs, one restrained
  score/prompt rail, and the authoritative rubric/anchor content as the main
  column;
- no card-per-task or card-inside-card framing;
- a connector made only of two binding pins bridging the upper and lower
  sheets, rather than an unrelated full-viewport bar;
- a lighter blue background and near-white paper surfaces.

Final `1440x900` geometry:

```text
upper result sheet:        x=160, y=62, width=1120, height=325.1
connector:                 x=160, width=1120, height=24
lower analysis sheet:      x=68, width=1304
upper-to-connector gap:    0
connector-to-lower gap:    0
horizontal overflow:       0
```

The browser artifact is:

`target/pre14-ui-acceptance/W04-ksh-writing-overview-laptop-large-1440x900.png`

Measured color contrast for the current laptop-large surface:

```text
primary body text / white:       16.27:1
topbar white / blue:              7.25:1
active tab blue / white:          6.82:1
task-score white / blue:          5.63:1
muted task text / white:          4.97:1
overall-score white / orange:     3.20:1 (large text)
```

The first post-mutation selector invocation was `HARNESS_INVALID` because the
operator supplied the non-contract environment variable suffix `_USER`; 25
source-level assertions passed and the seed stopped before loading. No
production source was changed for that operator error. The corrected opt-in
invocation used `KSH_PRE14_UI_SEED_JDBC_USER` and
`KSH_PRE14_UI_SEED_JDBC_PASSWORD` and passed:

```text
PracticeFunctionalUiContractTest:                 20 / 20
PracticeResultWordingTest:                         5 / 5
PracticePre14UiAcceptanceScenarioManifestTest:     1 / 1
PracticePre14UiAcceptanceSeedTest:                 1 / 1
Flyway validation in seed catalog:                73 / 73
Total:                                            27 / 27
Failures / errors / skipped:                       0 / 0 / 0
```

The real provider call counts remain AI=`0`, STT=`0`, TTS=`0`,
ingestion=`0`. The verdict remains
`PRE14_VALIDATION_REOPENED_AFTER_RESULT_PRESENTATION_CORRECTION` until the
single final consolidated lifecycle covers this final production snapshot.

## 14. Q51/Q52 structured-Writing response contract freeze

This entry freezes the correction boundary before the next production
mutation. The frozen worktree state is:

```text
branch: feature/practice-pre14-production-correctness-gate
HEAD: 55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4
dirty paths: 106
tracked diff SHA-256:
f042be7b72120fefef507a1226ac897629f4bacffc3336696c3530cf888d3ca7
```

The active implementation is not yet authoritative for Q51/Q52 learner
delivery. Both tasks remain `WRITING` / `ESSAY` questions and the current
player renders one essay textarea while attempt persistence exposes only a
flat `Map<String,String>`. The generic Reading/Listening
`FILL_IN_BLANK` contract is explicitly not an owner for these Korean Writing
cloze tasks.

The accepted target is:

- `taskType=Q51|Q52`;
- `responseMode=STRUCTURED_BLANKS`;
- `responseSchemaVersion=writing-blanks.v1`;
- one stable `blankId` and ordinal per learner input;
- typed accepted alternatives, equivalence authority and evidence per blank;
- typed learner answers, evaluation verdicts, corrections and diagnostics per
  `blankId`;
- Q53/Q54 remain essay/long-form contracts.

The immutable `question_content_json`, `answer_spec_json` and
`practice_attempts.answers_json` columns already provide versioned JSON
authority. Therefore this slice uses an additive contract and explicit
dual-reader; it does not require a schema migration and must not create V74
merely to store seed or contract JSON. V1--V73 remain immutable.

Historical Q51/Q52 essay-shaped publications and submissions stay readable
through an explicit legacy path and visible legacy label. They are never
heuristically split on `/`, `;` or other punctuation. New drafts and new
publications must carry the structured response contract; an existing editable
draft must be explicitly converted and previewed before publish.

Frozen changed-file ownership for this slice:

| Owner | Intended files |
|---|---|
| Typed contract and strict verifier | `assessment/*WritingBlank*`, `QuestionContent`, `AnswerSpec`, `AssessmentContractCodec` |
| Attempt dual-reader and fingerprints | `PracticeService`, one central attempt-answer codec/form mapper |
| Learner UI | `PracticeDtos`, `player-writing.html`, `player-exam.js`, scoped player CSS |
| Authoring conversion/publish gate | `PracticeDraftContractService`, `PracticeDraftValidator`, `PracticePublisherService`, `manage/editor.html` |
| Evaluation/result linkage | Writing request/normalizer/verifier/presenter only where `blankId` authority is required |
| Proof | focused assessment/service/controller/editor/result/seed tests and the scenario manifest |

Acceptance is fail-closed for unknown, missing, duplicate, swapped or extra
blank IDs; normalization is Unicode NFC with an explicit whitespace policy;
literal `/` and `;` remain ordinary answer content. Accepted answers never
leak before submit. Cache/fingerprint identity must include the response schema
and ordered blank structure. This section authorizes no real provider call,
publication or merge.

## 15. Whole-question generation cardinality and derived performance levels

This entry extends the frozen Q51/Q52 correction contract without opening a
new phase.

The generation invariant is:

```text
ONE_LOGICAL_AI_GENERATION_CALL_PER_QUESTION_EVALUATION
```

- Q51 and Q52 each send all ordered blanks, coverage, rubric anchors/scores,
  evidence, findings, corrections and any upgraded-answer change ledger in one
  request and receive one whole-question artifact.
- Q53 and Q54 each send every rubric criterion and return every result section
  in one whole-question artifact.
- Reading/Listening learner grading stays deterministic with zero AI calls.
  Lecturer explanation generation performs one logical call per immutable
  question/version after the lecturer selects one compatible typed strategy.
- Speaking preprocessing (STT/alignment/acoustic extraction when authorized)
  is a distinct capability step; the evaluator performs one logical generation
  call after the evidence bundle is ready.
- Overview, Strengths, Improvements, chips and upgraded-answer presentation are
  backend projections of the single verified artifact. Rendering any tab
  performs zero additional calls.
- A transient transport retry retries the same complete idempotent request with
  the same evaluation identity. Partial responses are never merged, and a
  syntactically valid but semantically contradictory response is rejected
  without a model-driven repair call.
- Explicit re-evaluation creates exactly one new whole-question evaluation;
  it is never a patch call.

Cache identity includes immutable question/version identity, response-schema
and ordered blank structure, answer hash, rubric/schema/prompt/model identity,
and authoritative evidence identity. A cache hit performs zero provider calls.

Performance bands are likewise backend authority, not free provider prose.
The provider supplies the selected `anchorId`, numeric score/max and stable
coverage/evidence references. Verified score policy derives:

```text
LIMITED | MODEST | GOOD | EXCELLENT
```

and the Result DTO exposes localized presentation metadata. A provider-emitted
level, when present for compatibility, must exactly match the backend mapping
or the artifact fails closed. `NOT_SCORABLE` and `UNAVAILABLE` are separate
states and never collapse to `LIMITED`. Score zero maps to the lowest anchor
only when the verified task policy proves that zero state; absence of a
diagnostic finding is not such proof. Diagnostic chips use polarity/applicability
states and do not infer performance bands.

Focused proof must use counting fake ports and cover Q51 with two blanks,
multi-criterion Q53, renderer tab access, explicit re-evaluation,
Reading/Listening learner result versus authoring generation, and Speaking
evaluation after a supplied transcription fixture. Anchor-to-level mapping,
localized labels, overall-versus-criterion levels and mismatch rejection are
also required. These invariants authorize zero real AI/STT/TTS/ingestion calls.

The laptop-large Writing task rail clipping reported on the same frozen
snapshot was traced to inherited `width:100%` plus horizontal margins.
`practice-result-prep.css` now scopes the rail to `width:auto`; after an F5 at
the existing UI-dev runtime, the rail ended at `x=1343` and active `Câu 54`
ended at `x=1338`, preserving the intended 5px right inset and 10px corner
radius.

## 16. Lecturer Korean typography access and active strategy-preview closure

This append-only entry records the browser-verified closure of the current
editor sub-slice. It does not close the premium seed, PREP artifact or final
validation gates.

Lecturers now use the same account-scoped Korean learning-content typography
preference as learners:

- `/practice/manage` exposes `Kiểu chữ Hàn`;
- the authoring toolbar exposes an accessible link to
  `/practice/preferences`;
- the preference route accepts exactly learner or lecturer authority without
  weakening CSRF, role or account ownership;
- the lecturer preference page provides the existing allowlisted font and size
  choices, and returns to `/practice/manage`;
- only explicitly Korean learning regions (`lang=ko`) inherit the selected
  font/size. Vietnamese editor chrome remains on the KSH UI font.

Browser proof used the persistent UI-dev process on port `18080`, PID `6366`.
After selecting `GAEGU` and `LARGE`, the same PID served an F5 where the Korean
question prompt resolved to `KSH Korean Gaegu` at `17px` while its Vietnamese
label remained `Be Vietnam Pro` at `13.6px`. The focused lecturer
preference/security/Flyway selector was `11/11` green; real AI/STT/TTS calls
were all zero.

The editor preview now has ten active typed renderers:

```text
EXACT_EVIDENCE_ONLY
FULL_SOURCE_INLINE_HIGHLIGHT
QUESTION_EVIDENCE_TRANSLATION_TABLE
MCQ_OPTION_ELIMINATION
EVIDENCE_AND_ELIMINATION
TFNG_CONTRADICTION_TABLE
NOT_GIVEN_BOUNDARY
FILL_SLOT_GRAMMAR_ANALYSIS
KEYWORD_PARAPHRASE_BRIDGE
BILINGUAL_STEP_BY_STEP
```

Each active renderer was exercised through the real editor at
`/practice/manage/drafts/14501?continue` and uses the same PREP-aligned visual
grammar where applicable: continuous black table frames, yellow keywords,
red/underlined authoritative answer evidence, blue italic translations,
keyboard-scrollable overflow and responsive containment. MCQ preview renders
the actual option set and stable correct-option authority; blank preview uses
stable blank identity; unsupported source shapes stop explicitly instead of
falling back to free prose.

The browser pass found and fixed one independent contradiction: a `TRUE`
fixture was rendered with a `FALSE`/`NOT GIVEN` rationale. Source-note and
rejected-state prose are now derived from the official `TRUE | FALSE |
NOT_GIVEN` decision, so the body and conclusion cannot disagree. An F5 on the
same PID confirmed:

```text
decision: TRUE
source note: Nguồn xác nhận trực tiếp nội dung mệnh đề.
rationale: Evidence xác nhận đầy đủ mệnh đề; loại FALSE và NOT GIVEN.
conclusion: ⇒ TRUE
```

`PracticePhase11AuthoringUiContractTest` is `19/19` green and
`git diff --check` passes. Ten additional registry catalog entries remain
non-selectable because their canonical KSH matching/heading/sequence,
timestamp/alignment, acoustic-intent or constrained-block-composition
contracts do not yet exist. They remain explicit unsupported debt; this entry
does not disguise them as provider prompts.

Frozen checkpoint after this sub-slice:

```text
branch: feature/practice-pre14-production-correctness-gate
HEAD: 55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4
dirty paths: 144
active-preview diff SHA-256:
d2ed4781653ed3efb24ea71a271df90358ffd0201315cced9d4ded16079b8794
```

Verdict remains
`PRE14_VALIDATION_REOPENED_AFTER_RESULT_PRESENTATION_CORRECTION`. Premium
deterministic seed coverage, per-surface actual/overlay/diff artifacts and the
final consolidated lifecycle are still open. No commit, push, PR, merge,
Pre-15, Phase 15 or Report-an-Error work was performed.

## 17. SAME_WORKTREE_FORK_HANDOFF_AFTER_STEP3

This append-only handoff applies the context-health and browser-timebox
override. It closes only the currently active bounded Step 3 sub-slice and
does not start premium seed loading, current-state PREP recapture, final
validation or publication.

### 17.1 Frozen same-worktree snapshot

```text
worktree: /Users/toanlamsaoduocc/.codex/worktrees/054c/ksh
branch: feature/practice-pre14-production-correctness-gate
HEAD: 55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4
HEAD tree: 2ea0f357e60e8577d450ec3787b4206ed1c954e1
dirty paths: 146
tracked diff SHA-256 before this append:
c325400d158f3c39fe2c7d9ca44d60ae1de7409ed4da4c425509044e6b43ac59
staged paths: 0
```

The handoff log itself is an existing untracked path, so this append does not
alter the tracked-diff digest or the dirty-path cardinality. No reset, stash,
checkout, clean, merge, rebase, commit, push or PR action occurred.

### 17.2 Six-checkpoint completion ledger

| Checkpoint | Status at fork | Exact evidence and remaining boundary |
|---|---|---|
| 1 — Korean content-language and typography authority | `COMPLETED_FOCUSED_GREEN` | Lecturer and learner use the same account-scoped Korean font/size preference; authoring regions explicitly persist and render `lang=ko|vi`; Vietnamese chrome remains on the KSH UI font. Section 16 records the browser proof and `11/11` focused gate. Do not redo without regression evidence. |
| 2 — Typed R/L strategy registry and editor preview | `COMPLETED_FOCUSED_GREEN` | Ten active, compatible typed strategies render distinct PREP-aligned table/highlight/translation structures through one immutable strategy per question/version. Ten catalog entries remain intentionally non-selectable until their canonical KSH contracts exist. Do not convert them to free prompts. |
| 3 — Header/sidebar and FILL/TFNG current-state closure | `COMPLETED_FOCUSED_GREEN_WITH_BROWSER_SCOPE_NOTE` | The bounded three-file correction is listed in Section 17.3. Static contract and focused gate are green; FILL fail-closed and TFNG canonical-answer refresh were exercised by F5 on the real editor. The exact 1155px and 621–900px visual viewports were not recaptured before the timebox expired, so those two rows are `STATIC_VERIFIED_BROWSER_VIEWPORT_NOT_RECAPTURED`, not pixel PASS. |
| 4 — Premium deterministic seed load and stable-route acceptance | `PARTIAL_OPEN_SUCCESSOR_START` | The DEV/TEST harness, scenario manifest and stable-route document exist, but the currently running catalog predates the latest seed-source correction. Do not treat it as current-state acceptance. The successor starts here with the exact action in Section 17.7. |
| 5 — PREP current-state actual/overlay/diff reconciliation | `NOT_STARTED_IN_THIS_HANDOFF` | Existing captures under `target/pre14-ui-acceptance/` are historical evidence only. No current Step 3 actual/overlay/diff set was created and no row was promoted. The authoritative matrix remains `2 PASS + 45 BLOCKED_SEMANTIC`. |
| 6 — Final consolidated lifecycle and branch publication | `OPEN_VALIDATION_REOPENED` | Section 10's backend `2715/2715` checkpoint predates later production UI/source changes. The successor must finish Steps 4–5, then run the deliberate final lifecycle before any commit/push/PR. Merge remains unauthorized. |

### 17.3 Bounded Step 3 change and verification ledger

The accepted Step 3 manifest is exactly:

```text
src/main/resources/static/css/practice/manage-editor.css
src/main/resources/templates/practice/manage/editor.html
src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java
```

The correction:

- moves the compact authoring toolbar/title breakpoint to `1180px`, retaining
  the draft title rather than dropping it at the reported 1155px width;
- makes the structure panel a deterministic 72px collapsed rail and a bounded
  drawer for every viewport up to `900px`, rather than depending on a stale
  hover or prior expansion state;
- clears stale top-level FILL blank aliases and synchronizes the canonical
  question contract before refreshing strategy compatibility/preview;
- refreshes TFNG preview only after the official answer is written to the
  canonical answer specification.

Focused command:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  bash mvnw -q \
  -DskipTests=false \
  -Dtest=PracticePhase11AuthoringUiContractTest test
```

Result:

```text
PracticePhase11AuthoringUiContractTest: 20 / 20 PASS
failures / errors / skipped:             0 / 0 / 0
Surefire duration:                       0.375s
git diff --check:                        PASS
```

The F5 browser pass used transient unsaved editor changes and then reloaded the
page to restore the disposable fixture:

- FILL with incomplete/mismatched blank identity stopped explicitly, exposed
  no raw `{{blank:...}}` token or blank SHA and did not invent a preview;
- TFNG set to official `FALSE` immediately rendered the typed contradiction
  table with `FALSE` decision, matching rationale and rejected `TRUE` /
  `NOT GIVEN` states;
- result: `CURRENT_STATE_AUTHORITY_VERIFIED`; no provider call and no DB save.

The browser API available in this timebox retained a 1440x900 viewport and did
not provide a resize operation. The 1180/900 rules therefore have source and
focused-contract proof but no same-turn 1155px or 621–900px screenshot.
Per the override, this is recorded as `NOT_VERIFIED_BROWSER_RECAPTURE` rather
than extending the debug loop.

### 17.4 UI-dev runtime retained for the successor

```text
Maven PID: 40849
Maven start: Fri Jul 31 09:34:36 2026
application PID: 40980
application start: Fri Jul 31 09:34:38 2026
port: 18080
profile: ui-dev
catalog: ksh_test_pre14_ui_strategy3
MySQL port: 33307
editor URL:
http://127.0.0.1:18080/practice/manage/drafts/14501?continue
```

The application still listens on port 18080 and serves exploded
`src/main/resources`; template/CSS/JS iteration remains same-PID F5-only.
The exact task-owned DB credential stays outside this log.

### 17.5 Active seed and stable route authority

Machine-readable manifest:

```text
src/test/resources/practice/pre14-ui-acceptance-scenarios.json
```

Human runbook:

```text
docs/architecture/practice/PRACTICE_PRE14_UI_ACCEPTANCE_SEED.md
```

Stable routes already reserved by the harness:

```text
Reading overview:  /practice/attempts/14100/result
Reading detail:    /practice/attempts/14100/result/detail
Reading editor:    /practice/manage/drafts/14501
Listening overview:/practice/attempts/14200/result
Listening detail:  /practice/attempts/14200/result/detail
Listening editor:  /practice/manage/drafts/14502
Writing overview:  /practice/attempts/14301/result through 14304/result
Writing detail:    the same attempts with questionId=14351, 14352, 4 or 14354
Speaking overview: /practice/attempts/14401/result
Speaking detail:   /practice/attempts/14401/result/detail?questionId=5
                   /practice/attempts/14401/result/detail?questionId=14405
```

The registry manifest contains 24 R/L browser scenarios and 80 compatibility
cells. Presence in the manifest is not browser or PREP acceptance.

### 17.6 Visual artifacts: historical versus current-state

Historical artifact directory:

```text
target/pre14-ui-acceptance/
```

It includes prior R/L/W/S screenshots and the earlier Writing Overview
overlay/difference files such as:

```text
R03-writing-overview-overlay-50-v4.png
R03-writing-overview-difference-v4.png
W04-ksh-writing-overview-laptop-large-1440x900.png
```

All of those predate the final Step 3 source snapshot and are retained as
historical evidence. There is no current-state Step 3 actual/overlay/diff
directory and no visual row may cite the historical files as a current PASS.

### 17.7 Successor start action and known open debt

The successor must start at Checkpoint 4 and must not redo Checkpoints 1–3
without concrete regression evidence.

First action:

```text
Run only PracticePre14UiAcceptanceSeedTest against
jdbc:mysql://127.0.0.1:33307/ksh_test_pre14_ui_strategy3
with KSH_PRE14_UI_SEED_ENABLED=true,
KSH_PRE14_UI_SEED_LOAD_BASE=false, the existing task-owned account/secret,
and all provider/worker switches disabled; then F5 the same PID 40980.
```

This reload is required because the last successful seed load predates the
latest seed-source correction. The successor must then de-duplicate the
current premium seed/runtime mismatches before any PREP capture. Known open
debt includes:

- typed R/L blocks still need end-to-end presenter proof instead of flattened
  generic prose;
- scenario IDs and Q51/Q52 structured-blank identity need current runtime
  verification;
- Writing and Speaking companion/conditional states remain incomplete;
- acoustic Speaking remains honestly `NOT_SCORABLE`;
- native Korean IME browser harness coverage remains open;
- ten advanced strategies remain blocked by missing canonical KSH authority;
- current-state actual/overlay/diff/geometry artifacts and the final
  consolidated lifecycle remain open.

No Step 4 seed reload, premium scenario work or PREP current-state capture was
started in this handoff turn. Real call counts remain:

```text
AI=0
STT=0
TTS=0
ingestion=0
```

Handoff verdict:

`READY_FOR_SAME_WORKTREE_FORK_STEP4_PREMIUM_SEEDS_AND_PREP_RECONCILIATION`

## 18. CONTEXT_HEALTH_STOP_AFTER_SEED_LOAD

This append records the context-health stop before any Step 4 browser
acceptance or source correction. It does not promote the retrospective
Checkpoint 1–3 labels in Section 17.2 into roadmap phases. The successor must
use requirement-level evidence: Section 16 typography is focused `11/11`,
the typed R/L registry has ten active and ten explicitly blocked strategies,
and the bounded Section 17.3 editor correction is focused `20/20`; its 1155px
and 621–900px browser recaptures remain open.

```text
branch: feature/practice-pre14-production-correctness-gate
HEAD: 55d50a1e3f90e3d6da280eef45b6c1b2a0005bf4
dirty paths: 146
tracked diff SHA-256:
c325400d158f3c39fe2c7d9ca44d60ae1de7409ed4da4c425509044e6b43ac59
changed workspace paths since this fork before this append: 0
```

The deterministic seed load completed on the disposable catalog
`ksh_test_pre14_ui_strategy3`:

```text
PracticePre14UiAcceptanceSeedTest: 1/1 PASS
Flyway validation/current schema: 74/74, V74
reserved attempts present: 9
R/L question versions present: 24
Writing attempts present: 6
lecturer drafts present: 2
AI/STT/TTS/ingestion calls: 0/0/0/0
```

The first attempt was harness-invalid because the task-owned database account
lacked access to `flyway_schema_history`; only that disposable catalog grant
was corrected, the same selector then passed, and no production data or
source file was changed.

UI-dev is running with exploded resources:

```text
Maven PID: 56042
application PID: 56175
application start: Fri Jul 31 11:59:45 2026
port/profile: 18080 / ui-dev
catalog: ksh_test_pre14_ui_strategy3
first route to verify:
http://127.0.0.1:18080/practice/attempts/14100/result
```

No browser route matrix, current-state screenshot, overlay, diff or geometry
artifact was produced in this fork. Exact next action for a fresh-context
successor: first create the short authoritative-gate reconciliation table
requested after Section 17 (requirement → exact section/test/artifact →
`CLOSED|PARTIAL|OPEN`), then use the retained PID to verify the stable seed
URLs and record the Step 4 route/render matrix before any source mutation.
Do not count historical files under `target/pre14-ui-acceptance/` as
current-state proof.

`READY_FOR_FRESH_CONTEXT_SAME_WORKTREE_STEP4_ROUTE_ACCEPTANCE`

## 19. EDITOR_SCROLL_ROOT_ATOMIC_CHECKPOINT

The observed lecturer-editor clipping report is a confirmed UI defect, not a
cosmetic screenshot discrepancy. Selecting another tree node after scrolling
a long question form could retain `.panel-editor.scrollTop`; browser focus
scrolling could additionally leave `document.documentElement.scrollTop`
above zero while page overflow was locked. This made the beginning of the
form inaccessible to wheel scrolling.

The bounded correction now:

- fixes the document root to the viewport;
- gives `body.pi-body` the dynamic viewport height;
- resets both editor-pane axes and the root/body scroll positions after
  every completed `selectNode(...)`.

Focused and browser evidence:

```text
PracticePhase11AuthoringUiContractTest: 20/20 PASS
621x900:  editor 650 -> 0; root -> 0; editor/workspace top 116/130
800x900:  editor 650 -> 0; root -> 0; editor/workspace top 116/130
1155x900: editor 650 -> 0; root -> 0; editor/workspace top 116/130
user-report viewport 1076x1014: recaptured at editor/root 0/0
```

Current artifacts are under
`target/pre14-ui-acceptance-current/`:

```text
editor-scroll-correction.md
editor-listening-621x900-scroll-reset-after.png
editor-listening-800x900-scroll-reset-after.png
editor-listening-1155x900-scroll-reset-after.png
editor-listening-1076x1014-fixed.png
```

No backend, scoring, provider, or premium-seed mutation was included in this
checkpoint. Per the latest sequencing authority, the next action is
`PREMIUM_ALL_CHIP_UI_ACCEPTANCE` inventory/taxonomy and coverage-gap
publication before any seed mutation or further backend work.

## 20. PREMIUM_ALL_CHIP_UI_ACCEPTANCE_ATOMIC_CHECKPOINT

This append records the deterministic premium Result UI checkpoint after the
editor-scroll correction. It does not promote production producer output,
acoustic claims or the final branch gate.

### 20.1 Current requirement ledger

| Pre-14 requirement | Evidence / test / artifact | Verdict |
|---|---|---|
| Korean content-language and typography | Section 16; focused `11/11` | `CLOSED_FOCUSED` |
| Typed R/L registry | 10 active + 10 explicitly blocked; Section 16 | `CLOSED_REGISTRY`; browser producer readiness remains partial |
| Bounded editor correction | Section 19; `20/20`; browser `621/800/1155/1076` | `CLOSED` |
| Premium Writing all-chip UI | Q51 `14/14`; Q52 `18/18`; Q53 `12/12 + 2/2` companion; Q54 `14/14 + 2/2` companion | `CLOSED_UI_ACCEPTANCE` |
| Premium Speaking transcript UI | strength `16/16` chips and `17/17` findings; improvement `16/16` and `17/17`; both acoustic rows remain `NOT_SCORABLE` | `CLOSED_UI_ACCEPTANCE` |
| Overview score/evidence boundary | 6 Writing + 2 Speaking stable Overview routes | `CLOSED_UI_ACCEPTANCE` |
| Current PREP artifacts | 12 exact `1529x836` reference/actual/overlay/diff/geometry sets | `CLOSED_ARTIFACT`; authoritative production promotion remains partial |
| R/L current stable-route acceptance | Existing matrix: 24 PASS, 13 PARTIAL, 2 FAIL; 18/24 strategy presenters READY | `OPEN` |
| Production producer alignment | Deterministic DTO/JSON contract is now fixed; provider output has not been changed in this checkpoint | `OPEN` |
| Final consolidated lifecycle | UI-focused gate below is green; final branch-wide lifecycle has not run | `OPEN` |

The historical retrospective labels in Sections 17--18 remain handoff labels,
not a replacement for the original phase roadmap.

### 20.2 Deterministic contract and seed corrections

The premium fixture now persists typed Writing blank answers and reads them
through the production attempt-answer codec. Q51/Q52 diagnostics use immutable
blank ids (`q51-b1`, `q51-b2`, `q52-b1`, `q52-b2`) instead of fabricated
fallback targets. Writing and Speaking each have a fifth `Mẫu` tab whose sample
requires explicit KSH teacher provenance.

Speaking teacher samples are stored outside the strict evaluator result under
`speaking_teacher_samples_by_question`. The strict Speaking evaluator parser
therefore remains fail-closed; adding UI provenance cannot invalidate the
evaluator payload or relax unknown-field handling.

Premium Writing attempt scores are reconciled from all four normalized task
scores instead of a polarity shortcut:

```text
14601 100
14602  90
14603 100
14604  53
14605 100
14606  72
```

This removed the browser-observed contradictions where Q54 `3/50` or Q53
`2/30` appeared beside an attempt summary of `0`.

### 20.3 Browser acceptance

Laptop-large Detail acceptance:

```text
routes:                  8 / 8 PASS
Writing chips:          62 / 62
Speaking chip ids:      32 / 32
Speaking occurrences:   34 / 34
missing / extra chips:   0 / 0
five-tab surfaces:       8 / 8
teacher samples:         8 / 8
horizontal overflow:     0
```

Laptop-large Overview acceptance:

```text
routes:                         8 / 8 PASS
Writing summary/task scores:    6 / 6 reconciled
Writing active rubric rows:     6 or 3 as task-native policy requires
Speaking holistic scores:       0 fabricated
Speaking transcript rows:       4 / route
Speaking acoustic rows:         2 NOT_SCORABLE / route
Overview diagnostic chips:      0
```

Required operation and content states are browser-visible and
machine-readable:

```text
operations:                     KEEP / MISSING / REPLACE / REDUNDANT
Writing correction cards:       26
Writing improvement upgrades:    2 routes
Speaking improvement rewrites:   6
```

Responsive representative coverage is `12/12 PASS` across Writing Q51/Q54 and
Speaking strength/improvement at `1155x900`, `800x900` and `621x900`.
Every cell has exactly one selected/focusable tab, minimum tab height at least
`52px`, an active visible panel and zero tab/chip/body horizontal overflow.

### 20.4 Current PREP reconciliation

Twelve exact `1529x836` current-state sets were created for:

- Writing Overview Q51;
- Writing Q51 and Q53 strengths;
- Writing Q52 and Q54 improvements;
- Q54 unique-strength and Q53 unique-improvement companions;
- Writing teacher sample;
- Speaking Overview strength/improvement states;
- Speaking Detail strength/improvement states.

Each set contains the directly referenced PREP normalized reference, current
actual, 50/50 overlay, amplified difference, geometry and metric JSON. Pixel
difference is evidence, not an instruction to copy IELTS wording, a foreign
rubric or acoustic claims. Pronunciation/fluency references remain
semantically blocked without governed direct-audio evidence.

Primary artifacts:

```text
target/pre14-ui-acceptance-current/premium-all-chip-ui-acceptance-matrix.md
target/pre14-ui-acceptance-current/premium-writing-large-browser-matrix.json
target/pre14-ui-acceptance-current/premium-speaking-large-browser-matrix.json
target/pre14-ui-acceptance-current/premium-writing-speaking-overview-large-browser-matrix.json
target/pre14-ui-acceptance-current/premium-writing-speaking-responsive-browser-matrix.json
target/pre14-ui-acceptance-current/premium-required-operation-upgrade-sample-browser-proof.json
target/pre14-ui-acceptance-current/prep-reconciliation/current-state-prep-reconciliation-matrix.json
```

### 20.5 Focused validation and boundary

```text
result / DTO / template / descriptor / scoring / wording / manifest:
106 / 106 PASS
deterministic enabled seed:
1 / 1 PASS
Flyway:
74 / 74 migrations, schema V74
browser checkpoint cells/routes:
28 / 28 PASS
current PREP reconciliation sets:
12 / 12 produced
git diff --check:
PASS
real AI / STT / TTS / ingestion calls:
0 / 0 / 0 / 0
```

The UI-dev runtime remains PID `57943` on port `18080`. No reset, stash,
checkout, clean, merge, rebase, commit, push or PR action occurred.

Checkpoint verdict:

`PREMIUM_ALL_CHIP_UI_ACCEPTANCE_CLOSED_PRODUCTION_PRODUCER_AND_RL_GATE_OPEN`

## 21. FINAL PRODUCTION PRODUCER, R/L AND CONSOLIDATED CHECKPOINT

This append supersedes only the open producer/R/L/final-lifecycle labels at
the end of Section 20. It retains the earlier failure and harness evidence.

### 21.1 Requirement closure ledger

| Pre-14 requirement | Final evidence / test / artifact | Verdict |
|---|---|---|
| Korean content-language and typography | Section 16; focused `11/11` | `CLOSED` |
| Typed R/L strategy registry | 10 active + 10 explicitly blocked; immutable strategy per seeded question | `CLOSED` |
| Editor scroll/root correction, laptop-large first | Section 19; focused `20/20`; browser `1155`, then `800/621`, plus reported `1076x1014` | `CLOSED` |
| Stable URL matrix | `39/39` URLs visited; 28 ready PASS, 11 intentional pending/unavailable/unsupported data states, 0 route failures | `CLOSED_COVERAGE` |
| R/L route/render readiness | 2 Overview + 24 Detail; SC `12`, FILL `6`, TFNG `6` | `CLOSED_26_OF_26` |
| Premium Writing all-chip UI | 6 Detail routes; `62/62` chips | `CLOSED` |
| Premium Speaking all-chip UI | 2 Detail routes; `32/32` chips, `34/34` finding occurrences; acoustic rows remain honestly not scorable | `CLOSED` |
| Premium responsive and PREP artifacts | responsive `12/12`; current reference/actual/overlay/diff sets `12/12` | `CLOSED` |
| Writing/Speaking producer contract | producer-focused `106/106`; post-seed Browser Detail `8/8` | `CLOSED` |
| Deterministic seed and manifest | enabled seed `1/1`; manifest `2/2`; no provider calls | `CLOSED_3_OF_3` |
| Final consolidated lifecycle | Java 17 + UTC; full suite `2757/2757`; package PASS; Flyway `74/74` | `CLOSED` |

The names of retrospective handoff steps remain implementation labels, not a
replacement for the original phase roadmap.

### 21.2 Production producer alignment

Writing provider allowlists are now exact by task:

```text
Q51: 16
Q52: 16
Q53: 25
Q54: 27
```

The five bounded whole-answer strength criteria required by the UI contract
now travel through the provider-shaped envelope, evidence-ledger verifier and
normalizer. The seed no longer injects them after normalization.

Speaking requests carry all 16 transcript-diagnostic subcriteria. Acoustic
pronunciation/fluency criteria remain separate and are emitted only as two
`NOT_SCORABLE` rows without verified audio/timestamp evidence. The fake
transport contract proves the request shape without a provider call.

R/L immutable route backing is verified by the opt-in Spring/JPA route smoke
`1/1`, and all 26 R/L Overview/Detail routes render their selected typed
strategy.

### 21.3 Consolidated correction discovered by the gate

The first full execution completed `2755` tests with 10 failures and 4
errors. Five were harness-timezone failures: the same focused classes passed
after the JVM was correctly locked to UTC. The remaining nine exposed two
real compatibility gaps and stale assertions:

- V74 canonicalizes `QuestionContent` to v3 for explicit language authority,
  but several Speaking consumers still treated only v2 as typed. Presenter,
  validator, preview, Excel asset verification, publication, evaluation
  context and immutable player fallback now dual-read typed v2/v3. Publication
  preserves the v3 language tag; malformed explicit v3 remains fail-closed.
- The generated Excel v2 template previously created Q51/Q52 as legacy essay
  rows and was rejected by its own current validator. It now requires exactly
  two explicit `B1`/`B2` answers and emits typed Writing response plus answer
  authority. No answer is inferred from prompt prose or one shared string.
  Historical workbook v1 Speaking without per-question audio remains blocked
  and is never silently promoted to text-only.

The bounded Excel selector then passed `11/11`. No production fail-closed rule
was weakened.

### 21.4 Final lifecycle

The final fresh catalog was
`ksh_test_pre14_final_gate_0731f`. Java was `17.0.19`, JVM and JDBC timezone
were UTC, Hikari was bounded, all schedulers/providers were disabled, and the
suite completed:

```text
Surefire reports:                 371
full suite:                       2757 / 2757
failures / errors:                   0 / 0
normal-suite opt-in skips:                 2
package -DskipTests:                    PASS
fresh Flyway:                        74 / 74
schema max / failed:                  V74 / 0
enabled seed + manifest:                 3 / 3
git diff --check:                         PASS
real AI / STT / TTS / ingestion calls: 0 / 0 / 0 / 0
provider worker processes:                     0
```

After the enabled idempotent seed rerun, Browser F5 on Speaking improvement
route `14702/14405` rendered `16` chips, `17` cards, `17` mapped spans, five
tabs, two honest acoustic-unavailable rows and zero horizontal overflow at
`1440x900`. The temporary viewport override was reset; the Browser returned
to its normal `842x772` viewport.

Final machine-readable proof:

```text
target/pre14-ui-acceptance-current/pre14-final-consolidated-proof.json
target/pre14-ui-acceptance-current/premium-producer-post-seed-browser-proof.json
```

UI-dev remains PID `57943` on port `18080`. The authoritative branch remains
`feature/practice-pre14-production-correctness-gate`. All `155` dirty paths
were preserved. No reset, stash, checkout, clean, merge, rebase, commit, push
or PR action occurred.

Final checkpoint verdict:

`PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE_CLOSED_UNPUBLISHED`

## 22. WRITING DETAIL LARGE-LAYOUT REOPEN AND CORRECTION CHECKPOINT

This append supersedes the Writing visual-completion claim in Sections 20--21
after direct user review showed that the earlier current-state artifacts still
had the wrong canvas, overview composition, upgrade presentation and inline
annotation behavior. It does not reopen or mutate the typed evaluator,
provider, scoring, seed or database contracts.

The bounded source correction is:

```text
src/main/resources/templates/practice/result-detail-writing.html
src/main/resources/static/css/practice-result-detail.css
src/main/resources/static/js/practice-result.js
src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java
docs/architecture/practice/PRACTICE_PRE14_UI_ACCEPTANCE_SEED.md
```

At laptop-large size the Writing Detail surface now uses one white sheet on
the blue KSH/PREP-aligned canvas. The source and feedback panes share a bounded
`52/48` split with a `35--65` separator. Q51/Q52 Overview renders six native
KSH criteria as two three-row blank groups; Q53/Q54 renders three long-form
criteria as horizontal score rows. No IELTS wording or foreign rubric was
copied.

The premium route proof is `6/6 PASS` at `1440x900`:

```text
Q51 strength:             14/14 diagnostic + 14/14 upgrade chips
Q52 improvement:          18/18 diagnostic + 18/18 upgrade chips
Q53 strength:             12/12 diagnostic + 12/12 upgrade chips
Q54 improvement:          14/14 diagnostic + 14/14 upgrade chips
Q54 strength companion:    2/2  diagnostic +  2/2  upgrade chips
Q53 improvement companion: 2/2  diagnostic +  2/2  upgrade chips
```

Zero-finding chips remain hidden. The empty opposite-polarity tab on a
single-polarity acceptance fixture is therefore intentional; the companion
fixture proves the unique opposite-polarity catalogue without marking one
sentence as both good and bad.

Writing annotations are visually neutral until a chip is selected. Browser
proof records solid green strength, red improvement and blue upgrade
underlines, zero wavy underlines and no line reflow. Q51/Q52 blank-card
dimensions remain identical before/after selection; Q54 relative line boxes,
answer height and source scroll height are all unchanged.

The separator keyboard proof is `52 -> 56` after two `ArrowRight` presses,
with source width `717.594 -> 772.797`. Pointer handlers and pointer capture
are present, but the current in-app controller drag did not change the value;
pointer drag remains `IMPLEMENTED_NOT_BROWSER_PROVEN`, not PASS.

Responsive representative proof is `6/6 PASS` for Q51 and Q54 at `1155x900`,
`800x900` and `621x900`: zero document horizontal overflow; the separator is
visible at 1155 and the stacked layout hides it at 800/621.

Five fresh `1529x836` Writing reference/actual/overlay/diff sets use the `V2`
suffix. They replace the earlier Writing visual evidence for current-state
review. The metric verdict remains deliberately
`RECONCILED_NOT_PIXEL_IDENTICAL`. Q54 improvement changed from mean absolute
channel difference `28.17` / mismatch ratio `0.267` to `17.885` / `0.204067`.

Primary artifacts:

```text
target/pre14-ui-acceptance-current/writing-detail-v2-browser-proof.json
target/pre14-ui-acceptance-current/prep-reconciliation/writing-v2-reconciliation-matrix.json
target/pre14-ui-acceptance-current/prep-reconciliation/W-UPGRADE-Q54-V2-actual-1529x836.png
```

Validation:

```text
PracticeFunctionalUiContractTest:                 20/20 PASS
PracticePre14UiAcceptanceScenarioManifestTest:     2/2  PASS
Writing Detail selected integration tests:         4/4  PASS
Flyway in integration proof:                      74/74, V74
package -DskipTests:                              PASS
git diff --check:                                 PASS
temporary disposable DB users after cleanup:        0
real AI / STT / TTS / ingestion calls:         0/0/0/0
```

The two initial integration invocations were harness-invalid before Spring
startup because the first omitted `TEST_DB_URL` and the second supplied an
empty password rejected by the disposable-database guard. The successful run
used a random temporary user scoped only to
`ksh_test_pre14_ui_strategy3`; the cleanup trap removed it.

The selected integration-test teardown then cleared the stable attempts in
that shared disposable seed catalog. Browser refresh exposed the issue as
`Kết quả không tồn tại`; it was not hidden or counted as a product failure.
The deterministic seed was immediately reloaded against the same catalog:

```text
PracticePre14UiAcceptanceSeedTest: 1/1 PASS
premium Writing attempts:         6/6 restored
premium Speaking attempts:        2/2 restored
Q54 upgrade after F5:              14 chips
temporary DB users:                0
```

Future integration reruns must use a separate disposable catalog rather than
the retained browser seed catalog.

UI-dev remains application PID `83183` on port `18080`. The previous
branch-wide `2757/2757` run predates this UI correction, so this append closes
the focused Writing UI checkpoint but does not relabel the old full-suite run
as current.

Checkpoint verdict:

`WRITING_DETAIL_V2_UI_FOCUSED_CLOSED_FULL_LIFECYCLE_REOPENED`

## 23. WRITING PAIR-WISE NAVIGATION, HEADER ACTION AND INLINE-FLOW CORRECTION

Direct browser review reopened three concrete Writing defects from Section 22:
the re-evaluate action remained at the sheet bottom, responsive page scrolling
could hide the Q51--Q54 task switcher, and template indentation inherited
`pre-wrap`, forcing annotated spans onto separate visual lines. In addition,
the six premium attempts distributed the four primary polarities across
different attempts, so switching tasks inside `14604` exposed empty tabs.

The correction keeps one canonical pair-wise attempt without assigning both
polarities to one task:

```text
attempt 14604 / Q51 / 14351: 14/14 strength + 14/14 upgrade chips
attempt 14604 / Q52 / 14352: 18/18 improvement + 18/18 upgrade chips
attempt 14604 / Q53 /     4: 12/12 strength + 12/12 upgrade chips
attempt 14604 / Q54 / 14354: 14/14 improvement + 14/14 upgrade chips
```

All four links render in the same task switcher. The aggregate attempt score
is deterministically reconciled to `43`; the companion attempts `14605` and
`14606` still cover the opposite-polarity unique Q54/Q53 features. The seed
asserts the exact persisted `14/18/12/14` arrays and remains idempotent.

`Chấm lại câu này` now lives in the fixed header action group immediately
before `Hoàn tất`; its former bottom-sheet form was removed. At `800px` and
`621px` the task switcher is sticky below the header. Browser proof at
`621x800` records header bottom `79`, taskbar top `87`, an `8px` gap, four
visible task links and zero horizontal overflow. At `1155` and `1440` the
sheet keeps its fixed large-layout behavior with independently scrollable
panes.

The annotated-answer container now collapses template indentation while each
actual answer segment still preserves submitted whitespace. Before the fix,
the first Q54 annotation always began at left `62`; after F5 it continues on
the same line at left `387`. Selection preserves document geometry. Verified
span mappings render straight green strength, red improvement and blue
upgrade underlines. Whole-answer and length findings deliberately do not
fabricate a text underline.

Focused validation:

```text
PracticeFunctionalUiContractTest:                 20/20 PASS
PracticePre14UiAcceptanceScenarioManifestTest:     2/2  PASS
PracticePre14UiAcceptanceSeedTest:                  1/1  PASS
Flyway:                                            74/74, V74
canonical 14604 browser routes:                      4/4  PASS
responsive taskbar cases:                            3/3  PASS
temporary DB users after cleanup:                      0
real AI / STT / TTS / ingestion calls:           0/0/0/0
git diff --check:                                   PASS
```

Machine-readable proof:

```text
target/pre14-ui-acceptance-current/writing-detail-v3-browser-proof.json
```

Checkpoint verdict:

`WRITING_DETAIL_PAIRWISE_NAV_AND_INLINE_FLOW_CLOSED_FULL_LIFECYCLE_REOPENED`

## 24. OBJECTIVE DETAIL TYPED RENDERER, AUDIO AND SCALABLE RAIL CHECKPOINT

Browser review confirmed the Section 17.7 debt: the immutable editor strategy
selection and v4 evidence artifact were persisted, but Result Detail flattened
them into a raw strategy code and generic claim list. The objective presenter
now resolves the selected strategy through the KSH registry and exposes its
authority-owned category, Vietnamese label, description and renderer code.
The learner template renders all ten active typed layouts and keeps the raw
strategy code out of visible copy.

Deterministic seed copy no longer exposes technical strings such as
`scenario 14207`. Evidence translations, context claims, answer claims and
option elimination reasons now describe the actual Korean span and Vietnamese
decision. This remains DEV/TEST typed JSON/DTO data; no HTML mock and no
provider call was introduced.

The Reading/Listening question rail is fixed to the viewport bottom with
enough height for both the group title and question pills. Only the active
group expands its independently scrollable question strip; inactive groups
remain compact, so a large test does not lay every question across the full
screen. The active question is centered inside its local strip. Listening's
audio dock now shares the left/right geometry of the immutable source panel
instead of spanning across both source and review columns.

Large-laptop browser proof at `1440x900`:

```text
Reading stable routes:                         12/12 PASS
Listening stable routes:                       12/12 PASS
active typed renderers:                        10/10 covered per skill
raw strategy codes visible to learner:          0
document horizontal-overflow failures:          0
active question outside fixed rail:              0
inactive group question strips expanded:         0
Listening audio/source left-edge delta:          0px
Listening audio/source right-edge delta:      5.04px
```

Focused validation:

```text
PracticeFunctionalUiContractTest:             20/20 PASS
PracticePre14UiAcceptanceSeedTest:              1/1  PASS
Flyway:                                        74/74, V74
real AI / STT / TTS / ingestion calls:       0/0/0/0
```

Machine-readable proof:

```text
target/pre14-ui-acceptance-current/objective-detail-typed-browser-proof.json
```

Checkpoint verdict:

`OBJECTIVE_DETAIL_TYPED_RENDERERS_AND_SCALABLE_RAIL_CLOSED`

## 25. SPEAKING OVERVIEW CRITERIA AND CHIP-DRIVEN DETAIL CHECKPOINT

Direct large-laptop review confirmed that the Speaking Overview hid six KSH
criteria behind a secondary disclosure while rendering all `17 + 17`
findings as two long lists. The primary surface now renders all six criteria
as a compact level dashboard: four transcript-grounded criteria keep their
verified score and level, while Độ lưu loát and Phát âm/Thể hiện remain
explicitly not scorable without direct audio evidence. Overview findings are
bounded to `3 + 3` previews with exact total counts; all four next-action cards
remain visible in a two-column priority grid.

Speaking Detail keeps the PREP-like two-pane sheet and the accessible
left/right splitter. Diagnostic tabs now enter in catalogue mode: all typed
chips remain visible, but no finding card and no transcript underline is
active until the learner selects a chip. A selected chip reveals only the
matching card(s) and maps to the exact transcript span(s). Browser computed
style proof records straight semantic underlines:

```text
Điểm mạnh:       rgb(22, 128, 58), solid
Cần cải thiện:   rgb(217, 45, 69), solid
Bài nâng cấp:    rgb(37, 99, 235), solid
```

The repeated `Kiểm soát lặp từ` feature proves one chip can reveal two cards
and two exact occurrences without exposing the other fifteen findings. Q1
renders all `16` strength chips and `17` mapped strength findings; Q2 renders
all `16` improvement chips, `17` mapped improvement findings and all `16`
upgrade chips. The splitter keyboard contract moves from `52` to `50` with
ArrowLeft and returns to `52` with ArrowRight.

A recapture exposed one remaining large-screen root-scroll defect: browser
focus could move the document root by its full `70px` scroll range while the
two inner panes stayed fixed, hiding the compact `Câu Nói 1/2` task row. The
Speaking document root and body are now both height-locked at viewports above
`1080px`; the responsive media query restores normal vertical document
scrolling below that breakpoint. After selecting the repeated chip at
`1440x900`, both `window.scrollY` and the document scroll top remain `0`, and
the task row remains visible at `80.5--120.5px`. The Overview criterion tracks
were also widened to use the available feedback-column width instead of
collapsing to short badges.

Responsive browser proof:

```text
1440x900: split layout, 0px document overflow
1155x800: split layout, 0px document overflow
 800x800: stacked layout, splitter hidden, 0px document overflow
 621x900: stacked layout, one-column Overview criteria, 0px overflow
```

PREP actual/overlay/diff artifacts were rebuilt from the user-supplied
Speaking references. They prove a structural match for the white-sheet,
criterion-track and two-pane hierarchy, but do not support a pixel-perfect
claim: Overview mean absolute difference is `0.105451`; Detail is `0.494017`
because the supplied PREP capture includes a dimmed question drawer and uses
different content geometry. The honest pixel verdict remains `OPEN`.

Focused validation:

```text
PracticeFunctionalUiContractTest:             20/20 PASS
real AI / STT / TTS / ingestion calls:       0/0/0/0
git diff --check:                              PASS
```

Machine-readable and visual proof:

```text
target/pre14-ui-acceptance-current/speaking-overview-detail-v3-browser-proof.json
target/pre14-ui-acceptance-current/prep-reconciliation/speaking-v3-reconciliation-matrix.json
target/pre14-ui-acceptance-current/speaking-overview-criteria-14701-1440x900.png
target/pre14-ui-acceptance-current/speaking-detail-improvement-chip-14702-q2-1440x900.png
target/pre14-ui-acceptance-current/speaking-detail-upgrade-chip-14702-q2-1440x900.png
```

Checkpoint verdict:

`SPEAKING_OVERVIEW_AND_CHIP_DRIVEN_DETAIL_CLOSED_PIXEL_MATCH_OPEN`

## 26. RESULT DETAIL SPLITTER, PANE-OVERFLOW AND RESPONSIVE-GAP REOPEN

Direct user review reopened two observable large-laptop regressions on
Speaking Detail: the separator rendered as a three-stripe grip and the
feedback pane could pan horizontally, cutting off the active tab and the left
edge of Sample content. The correction is shared by Writing and Speaking.
The pane owns vertical scrolling only, horizontal tab overflow is isolated to
the tablist, and the separator is one neutral `8px` strip with no nested grip.

Browser proof covers both the default `52/48` split and the `65/35` extreme.
At the narrow `472px` feedback width all five tab labels remain inside the
tablist; icons collapse under the feedback-container breakpoint. The pane and
tablist both remain at `scrollLeft = 0` with `scrollWidth = clientWidth`.

The same recapture exposed a separate responsive gap regression. At `621px`
the header is sticky and participates in flow, while at `800px` it is fixed.
The final rules therefore keep an `8px` header-to-sheet gap at both widths
without allowing the sheet to move under the fixed header. Writing and
Speaking pass `6/6` representative responsive cases at `1155x900`,
`800x900`, and `621x900`, with zero document horizontal overflow.

The interaction proof remains scoped and identity-safe:

```text
Writing Q54 at 1536x915:
default all 14 -> chip 1 -> occurrence detail 1 -> clear all 14
opened finding: F_Q54_PREMIUM_W_AWKWARD_UNNATURAL_EXPRESSIONS

Speaking Q2 at 1536x915:
repeated chip 2 -> exact spans 2 -> visible findings 2 -> open detail 0
underlines: solid red; document/root overflow: 0
```

The objective large matrix is now `24/24`: Reading `12/12` and Listening
`12/12`, ten typed renderers per skill, zero learner-visible strategy/evidence
codes and zero horizontal-overflow failures. The Reading matrix was rerun
after detecting that its first new tab had not inherited the requested
viewport; only the rerun at exact `1440x900` is counted here.

The zero-finding catalogue rule now follows the latest interaction authority:
chips stay visible at the end and open a bounded empty state. They do not
fabricate findings, spans or detail cards.

Fresh focused validation after these corrections:

```text
PracticeFunctionalUiContractTest:                 20/20 PASS
PracticeSpeakingMediaUiResourceTest:                6/6 PASS
PracticeResultDetailContractTest:                  12/12 PASS
PracticeResultPresenterTest:                       48/48 PASS
PracticePre14UiAcceptanceScenarioManifestTest:       2/2 PASS
focused total:                                      88/88 PASS
JavaScript syntax / scenario JSON / git diff:   PASS/PASS/PASS
real AI / STT / TTS / ingestion calls:           0/0/0/0
```

Speaking Detail reference/actual/overlay/diff was rebuilt against the
directly applicable PREP 5.4 capture without a dimmed question drawer. The
mean absolute difference ratio is `0.062557`; the changed-pixel ratio at
threshold 12 is still `0.285488`. This is a structural improvement, not a
pixel-perfect close.

Authoritative open boundaries remain: upgraded-answer text does not yet carry
persisted target offsets for Writing or Speaking, and seed-only transcript
evidence cannot authorize acoustic clips, phonemes or stress. The UI continues
to show the audio capability as unavailable rather than inventing data.

Primary proof:

```text
target/pre14-ui-acceptance-current/result-detail-ui-regression-v4-browser-proof.json
target/pre14-ui-acceptance-current/speaking-detail-sample-14701-1440x900.png
target/pre14-ui-acceptance-current/prep-reconciliation/speaking-v3-reconciliation-matrix.json
```

Checkpoint verdict:

`RESULT_DETAIL_INTERACTION_LAYOUT_REGRESSION_CLOSED_PIXEL_AND_UPGRADE_OFFSETS_OPEN`

## 27. MASTER 48 GATE B — OBJECTIVE EXPLANATION ROUTING AND LEARNER PROJECTION CHECKPOINT

The master audit remains a visual-convergence gate, not a test-only closure.
All 48 PREP files remain inventoried. Current paired coverage is `21/48`:
the 12 Overview rows from Gate A plus nine Listening/Reading Detail authority
rows from Gate B. Every paired row remains `PARTIAL`; no row is promoted to
`MATCH` while the recorded PREP hierarchy/geometry drift remains visible.

Gate B now renders one canonical explanation root per objective question.
Option/fill/TFNG answer areas keep only learner answer state and the explanation
trigger; the former inline evidence, global evidence duplicate, provenance
disclosures, evidence IDs, strategy codes, `blank_1`, accepted-answer lexicon
and normalization/debug schema are absent from the learner projection.

The user-reported hash regression is closed. Clicking `Xem lời giải` on
Listening question 7 keeps both the active panel and URL hash at
`objective-question-14207`, opens only that question's explanation and leaves
question 1 hidden. The static script URL is versioned so an F5 loads the
corrected router rather than the cached handler.

The unanswered MCQ state now comes from a typed DTO predicate: it renders one
compact notice, neutral options, no answer-status label and a collapsed
explanation. The `TFNG_CONTRADICTION_TABLE` seed was also corrected at the
contract boundary: the learner claim uses `공원`, the authoritative source uses
`도서관`, and the official answer is `FALSE`. No frontend inference is used.

Current Gate B artifact rows:

```text
master-result-48/row-13  Listening fill correct, collapsed
master-result-48/row-14  Listening keyword bridge wrong, expanded
master-result-48/row-15  Listening MCQ evidence correct, expanded
master-result-48/row-16  Listening unanswered, collapsed
master-result-48/row-17  Listening elimination, expanded
master-result-48/row-20  Reading TFNG contradiction, expanded
master-result-48/row-21  Reading fill grammar, collapsed
master-result-48/row-22  Reading keyword bridge, expanded
master-result-48/row-23  duplicate PREP authority file, separate pair
```

Each row contains `prep-reference.png`, `ksh-actual.png`, `side-by-side.png`,
`overlay-50.png`, `difference-x4.png`, `interaction-proof.json` and
`verdict.json`. The in-app emulator produced a physical `1440x900` bitmap
while exposing a different CSS `innerHeight`; artifacts are normalized to the
authority row's `1536x915` comparison canvas and therefore remain `PARTIAL`
rather than being described as exact same-viewport matches.

Four Gate B authority rows remain contract-blocked and `MISSING`: multi-answer
plus optional helper, helper conversation, matching plus pinned shared
material, and the local helper drawer. The current canonical question type
boundary supports only single choice, TFNG and fill blank for objective
results; no front-end fixture was fabricated to hide this gap.

Focused validation:

```text
PracticeFunctionalUiContractTest:                 20/20 PASS
PracticeResultPresenterTest:                      48/48 PASS
ObjectiveResultDetailTypeNativeContractTest:       7/7  PASS
PracticePre14UiAcceptanceSeedTest:                  1/1  PASS
Flyway schema validation:                          74/74, V74
temporary disposable DB users after cleanup:          0
real AI / STT / TTS / ingestion calls:           0/0/0/0
git diff --check:                                   PASS
```

Primary proof:

```text
docs/architecture/practice/PRACTICE_PRE14_MASTER_RESULT_UI_AUDIT_MATRIX_48.md
target/pre14-ui-acceptance-current/master-result-48/gate-b-q14207-explanation-routing-proof.json
target/pre14-ui-acceptance-current/master-result-48/row-13..17
target/pre14-ui-acceptance-current/master-result-48/row-20..23
```

Checkpoint verdict:

`GATE_B_AVAILABLE_TYPED_ROUTES_STRUCTURALLY_CLOSED_21_OF_48_PAIRED_VISUAL_AND_FOUR_CONTRACT_STATES_OPEN`

## 28. GATE A OVERVIEW RECONCILIATION — SPEAKING CERTIFICATE CORRECTION AND CURRENT PROOF

Gate A is classified by the visual content of each PREP authority file, not
only by its source folder. The 12 Overview authorities are master rows
`1–8`, `10–12`, and `45`. Row 9 is Speaking Detail despite living in the 4.2
folder; row 45 is Speaking Overview despite living in the 5.4 folder. The
master inventory now records this swap explicitly.

The prior Speaking summary only displayed a large unavailable score state and
forced the learner to enter the next canvas before seeing any valid language
result. It now uses the same certificate/summary tier as the Writing overview:
four transcript-grounded Korean-language criteria show their authoritative
score and level rails, while Fluency and Pronunciation/Delivery show an
explicit unavailable state. No radar, holistic score, acoustic score or
phoneme result is inferred.

The view no longer reaches into `criterion.band()` directly. The DTO exposes
typed `performanceCssClass()` and `performanceLabel()` helpers after applying
the scored/unavailable capability contract. This keeps the template on the
explicit view boundary and preserves the existing rule that direct-audio
criteria cannot be derived from a transcript.

The horizontally scrollable Writing/Speaking tab rails retain keyboard and
touch scrolling but hide the native gray scrollbar that previously appeared
as a thick stripe across the result canvas. F5/current browser proof at
`732x837` verifies all four Overview routes with zero horizontal document
overflow and no learner-visible `strategyCode`, `evidenceId`, scenario,
provider, policy-bundle or fingerprint text.

Current interaction proof:

```text
Writing task tabs:                 4/4 PASS (Q51, Q52, Q53, Q54)
Writing Detail CTA identities:     4/4 PASS
Speaking overview/criterion tabs:  7/7 PASS
Speaking scored language criteria: 4/4 PASS
Speaking acoustic unavailable:     2/2 PASS
real AI/STT/TTS/ingestion calls:    0/0/0/0
```

Focused validation:

```text
PracticeResultPresenterTest:             48/48 PASS
SpeakingResultRenderingContractTest:       2/2 PASS
combined focused tests:                   50/50 PASS
git diff --check:                              PASS
branch: feature/practice-pre14-production-correctness-gate
dirty paths:                                   162
UI-dev PID / port:                   83183 / 18080
```

Primary current proof:

```text
docs/architecture/practice/PRACTICE_PRE14_MASTER_RESULT_UI_AUDIT_MATRIX_48.md
target/pre14-ui-acceptance-current/gate-a-overview-current/default-route-proof.json
target/pre14-ui-acceptance-current/gate-a-overview-current/interaction-proof.json
target/pre14-ui-acceptance-current/gate-a-overview-current/speaking-default-732x837.png
target/pre14-ui-acceptance-current/gate-a-overview-current/speaking-acoustic-unavailable-732x837.png
```

Gate A is not declared done. Five unchanged R/L/W desktop pairs remain
reusable. Seven Speaking Overview desktop/compact pairs (`6`, `7`, `8`, `10`,
`11`, `12`, `45`) must be recaptured after the certificate correction and
compared directly before any row is promoted. The next exact authority row is
row 6, PREP `Screenshot 2026-07-03 234933.png`, route
`/practice/attempts/14701/result`, target viewport `1536x915`.

Checkpoint verdict:

`GATE_A_INVENTORY_12_OF_12_CURRENT_RESPONSIVE_4_OF_4_DESKTOP_VISUAL_RECLOSURE_5_OF_12_REUSABLE_7_RECAPTURE_OPEN`

## 29. GATE A OVERVIEW — LARGE-LAPTOP CERTIFICATE HIERARCHY TUNING

Direct visual review of the Speaking certificate against PREP row 6 found a
remaining hierarchy defect: the KSH certificate ended too early on large
laptop, allowing the drill-down canvas to compete above the fold. The
large-laptop-only rule now gives the certificate a `520px` minimum height,
`46x48px` padding and a `428px` summary layout. This changes geometry only;
it does not synthesize an overall score, acoustic score, radar, finding or
provider result.

F5/current browser verification at the available `732px` pane width keeps
the responsive certificate at `344px`, all four Overview routes at
`scrollWidth == clientWidth`, and preserves the two explicit acoustic
unavailable criterion states. The controller currently cannot supply the
authoritative `1536x915` desktop viewport, so no resized/cropped asset was
written and no row was promoted from `PARTIAL`.

Next exact visual action remains row 6 at its original `1536x915` browser
viewport, followed by rows 7, 8, 10, 11, 12 and 45 with same-viewport
reference/actual/side-by-side/overlay/diff.

Checkpoint verdict:

`GATE_A_OVERVIEW_RESPONSIVE_4_OF_4_PASS_LARGE_LAPTOP_CERTIFICATE_TUNED_DESKTOP_RECAPTURE_7_OPEN`

## 30. GATE A R/L OVERVIEW — FIXED-VIEWPORT HARNESS AND SCORE-COLUMN CORRECTION

The in-app browser measurements were not a stable desktop authority: one
split-pane session exposed `732x837`, while a fresh unsplit tab exposed
`1280x720` with DPR `2`; its screenshot output was `1280x720`. A reproducible
local Chromium capture script now fixes CSS viewport and DPR explicitly,
logs in through the normal disposable local form when required, and refuses
non-local or non-practice-attempt targets.

Reading row 2 and Listening row 1 were recaptured at `1536x915` and
`1440x900`; both also have `732x837` responsive proof. Reading additionally
has a `1280x720` proof. The Objective table now uses bounded column widths and
wrapping at both laptop and `641–980px` breakpoints, removing the reported
overlap between `Điểm đạt được` and `Tỷ lệ điểm` without horizontal document
overflow.

Current Gate A count remains `0/12 DONE`: rows 1–2 stay `PARTIAL` because
their PREP Part/Passage grouping and exact geometry are still open. Current
capture coverage is `2/12` recaptured against current CSS; no row was promoted
from DOM/test evidence alone.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_CURRENT_CAPTURE_2_OF_12_RL_COLUMNS_FIXED_PART_PASSAGE_HIERARCHY_OPEN`

## 31. GATE A WRITING OVERVIEW — CURRENT DESKTOP RECAPTURE

Rows 3–5 were recaptured from the deterministic Writing seed at both
`1536x915` and `1440x900`. The fixed-viewport harness now supports bounded
Writing task/criterion selection and scrolling to `#result-analysis`, so the
Q54 criterion state is reproducible without manual browser state or a fake
front-end counter.

The large-laptop Writing certificate now uses the same macro sequence as the
PREP authority: one large result sheet, a restrained summary CTA, then one
analysis sheet. The KSH result remains authoritative at four native tasks
Q51–Q54 and does not copy the PREP two-task IELTS taxonomy. The local fixture
was reset from an interaction-induced `QUEUED` state to its deterministic
`SUCCEEDED` state by the seed loader. The learner-visible Q54 feedback no
longer contains the test-only word `acceptance`.

Current proof:

```text
Gate A rows DONE:                         0/12
Gate A current-CSS capture coverage:      5/12
Writing current desktop pairs:             3/3
Writing 1536x915 horizontal overflow:       0
Writing learner internal-code leakage:      0
PracticePre14UiAcceptanceSeedTest:      1/1 PASS
Flyway schema:                          74/74 V74
AI/STT/TTS/ingestion calls:          0/0/0/0
```

Artifacts are in `target/pre14-ui-acceptance-current/master-result-48/row-03/`
through `row-05/`, each with reference, current actual, side-by-side,
overlay, difference and interaction/verdict JSON. Rows remain `PARTIAL`:
PREP/KSH branding and exact typography/vertical rhythm differ, and the PREP
row-5 reference has two active metric rails while the authoritative KSH Q54
criterion group contains one. No extra criterion was fabricated.

Next exact authority row is row 6, route
`/practice/attempts/14701/result`, viewport `1536x915`.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_CURRENT_CAPTURE_5_OF_12_WRITING_CURRENT_SPEAKING_ROW_6_NEXT`

## 32. GATE A SPEAKING OVERVIEW — CURRENT DESKTOP AND COMPACT RECAPTURE

Speaking Overview rows 6, 7, 8, 10, 11, 12 and 45 now have current actual,
side-by-side, overlay, difference, interaction proof and verdict artifacts.
Rows 6–12 use `1536x915` with additional `1440x900` captures; row 45 uses
the normalized `1280x704` compact authority. Row 12 also includes the
separate direct capture `ksh-actual-acoustic-unavailable.png`.

The laptop-large Speaking certificate is now one complete first visual unit
at `650px` minimum height, followed by one learner CTA and the overview
canvas. The 641–980px responsive layout is unchanged. A `732x837` interaction
run proves all seven Overview/criterion tabs, zero horizontal overflow and
zero learner-visible internal-code leakage.

KSH keeps the authoritative boundary: four transcript-backed language
criteria are scored, while Fluency and Pronunciation remain explicitly
`NOT_SCORABLE` without direct audio. PREP numeric radar, holistic score, Part
aggregate and named criterion submetrics were not fabricated.

```text
Gate A rows DONE:                         0/12
Gate A current-CSS artifact coverage:    12/12
Speaking current rows:                     7/7
Speaking tab interactions:                 7/7
Speaking desktop/compact overflow:           0
Speaking internal-code leakage:              0
Fabricated holistic/acoustic scores:          0
```

All twelve Gate A rows retain `PARTIAL` verdicts because either observable
visual drift or an authoritative contract gap remains. The next Gate A
correction is the R/L Overview Part/Passage grouping contract; Gate B has not
started.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_CURRENT_ARTIFACTS_12_OF_12_RL_GROUP_CONTRACT_NEXT`

## 33. GATE A R/L OVERVIEW — IMMUTABLE PART/PASSAGE GROUP CONTRACT

Reading and Listening Overview now receive an explicit typed group contract
from the immutable attempt snapshot. Each group carries its learner label,
original source label, question-type catalogue, correct/wrong/unanswered
distribution, earned/possible score, percentage and the persisted first
question id. The learner CTA is therefore a stable URL into that group; the
template does not search text or infer ownership from DOM order.

The current deterministic routes prove two groups per skill:

- Listening `/practice/attempts/14200/result`: `Phần nghe 1` and
  `Phần nghe 2`, linked to questions `14201` and `14207`.
- Reading `/practice/attempts/14100/result`: `Bài đọc 1` and `Bài đọc 2`,
  linked to questions `14101` and `14107`.

Desktop proof at `1536x915` and compact proof at `732x837` show zero table
header overlap, zero horizontal document overflow and zero learner-visible
internal-code leakage. The 641–980px analysis sheet no longer retains the
old fixed `427px` height, so the second immutable group and its CTA remain
inside the white result sheet instead of overflowing the card.

Focused presenter validation is `48/48 PASS`; deterministic seed validation
remains `1/1 PASS` with Flyway `74/74` at schema `V74`. Real provider calls
remain `0`.

```text
Gate A rows DONE:                         0/12
Gate A current-CSS artifact coverage:    12/12
R/L immutable group contracts:             2/2
R/L stable group-to-detail URLs:            4/4
Presenter focused tests:                  48/48 PASS
Compact document horizontal overflow:         0
Compact table-header overlap:                 0
Learner internal-code leakage:                0
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/row-01/`
- `target/pre14-ui-acceptance-current/master-result-48/row-02/`
- each directory contains the current reference/actual/side-by-side/overlay/
  difference set, `group-contract-proof.json`, `interaction-proof.json` and
  an honest `verdict.json`.

Rows 1–2 remain `PARTIAL`, not `MATCH`: certificate artwork, exact
typography and vertical density still drift visibly from PREP. Gate B has
not resumed. The next Gate A work item is visual reconciliation of the
remaining Overview drift without fabricating the unsupported W/S radar,
holistic, Part aggregate or acoustic scores.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_CURRENT_ARTIFACTS_12_OF_12_RL_GROUP_CONTRACT_CLOSED_VISUAL_DRIFT_OPEN`

## 34. GATE A R/L OVERVIEW — SKILL-SPECIFIC APP-SURFACE GEOMETRY

The raw PREP PNG includes `145px` of physical browser chrome. The mandatory
`1536x915` outer-screen pairs remain intact, but R/L now also have a
content-only proof at the measured PREP app viewport `1529x836`. The app
reference is produced only by cropping that chrome and normalizing to the
measured viewport; its pixels are identical to the prior authority-normalized
reference.

This comparison exposed a real CSS regression: the final Gate A override had
forced both R/L skills into Listening's wide layout. The skill layouts are
now separate:

- Listening keeps the PREP-wide composition: summary `1138×256` at
  `(195.5, 90)` and analysis `1138×363.8` at `(195.5, 366)` in the
  `1529x836` app viewport.
- Reading restores the PREP-narrow composition: summary `914×220` at
  `(307.5, 58)` and analysis `914×427` at `(307.5, 293)`.

The immutable groups were compacted without removing their authority or
stable URLs. At `732px` the groups keep a two-column copy/status row and the
result sheet is `204px` shorter than the prior single-column stacking. The
table still exposes the bounded score columns and has zero heading overlap.

```text
R/L app-surface pairs:                       2/2
Listening mean channel delta:             18.754
Reading mean channel delta:               18.771
Official 1536 mean delta (L/R):     30.470 / 27.522
Checked widths:             1536/1440/1280/1155/732
Horizontal document overflow:                  0
Table-header overlap:                            0
Learner internal-code leakage:                   0
Gate A rows DONE:                             0/12
Gate A current-CSS artifact coverage:        12/12
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/row-01/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-02/app-surface-1529x836/`
- both contain app-only reference/actual/side-by-side/overlay/difference and
  `visual-proof.json`; the row roots retain the official outer-screen set.

Rows 1–2 remain honest `PARTIAL`: macro geometry is closed, but the
KSH-native certificate artwork/typography and the content density required
by the authoritative two-group/three-type seed still produce visible pixel
drift. Gate B remains paused.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_RL_MACRO_GEOMETRY_CLOSED_CONTENT_VISUAL_DRIFT_OPEN`

## 35. GATE A WRITING OVERVIEW — APP-SURFACE GEOMETRY AND RESPONSIVE PROOF

The three Writing Overview authority states now also have app-surface proof at
the measured `1529x836` viewport. The reference is the original PREP bitmap
with its `145px` physical browser chrome removed and then normalized to the
measured app viewport; the official outer-screen `1536x915` pairs remain in
each row root.

The first certificate state is restored as the large first visual unit from
the authority instead of being compressed into a dashboard strip. Its KSH
sheet is `1280x504` at `y62`, with the analysis sheet beginning at `y698`.
The criterion state keeps the top bar sticky, places the analysis sheet at
`y68`, and exposes the learner Detail CTA below the sheet without overlapping
the criterion content. Decorative connector bars were removed where they
crossed the analysis heading and score denominator.

Row 4 remains explicitly unresolved: the PREP authority shows a materially
taller/narrower certificate than row 3 at the same bitmap size, but supplies
no route, selection or scroll state that distinguishes the two. KSH does not
invent a state discriminator merely to match that bitmap.

```text
Writing app-surface pairs:                    3/3
Row 3 mean channel delta:                  27.319
Row 4 mean channel delta:                  40.943
Row 5 mean channel delta:                  23.564
Official 1536 mean delta (rows 3/4/5): 34.566 / 45.408 / 29.379
Checked widths:                    1536/1440/1155/800/621
Horizontal document overflow:                 0
Learner internal-code leakage:                  0
Gate A rows DONE:                            0/12
Gate A current-CSS artifact coverage:       12/12
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/row-03/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-04/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-05/app-surface-1529x836/`

Rows 3–5 remain honest `PARTIAL`: row 3 closes its macro certificate scale,
row 5 closes the sticky-header/criterion/CTA geometry, while KSH-native
artwork, typography and authoritative task/criterion density still differ
from PREP. Gate B remains paused; the next Gate A correction is Speaking
Overview rows 6, 7, 8, 10, 11, 12 and 45.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_WRITING_MACRO_GEOMETRY_CLOSED_ROW_4_STATE_AUTHORITY_OPEN_SPEAKING_NEXT`

## 36. GATE A SPEAKING OVERVIEW — APP-SURFACE GEOMETRY AND RESPONSIVE RECAPTURE

Rows 6, 7, 8, 10, 11 and 12 now have app-surface proof at the measured
`1529x836` viewport in addition to their official `1536x915` outer-screen
pairs. As with the earlier skills, the reference app surface is produced by
removing the measured `145px` browser chrome from the original PREP bitmap
and normalizing it to the fixed viewport. Row 45 keeps its source-specific
`1280x704` compact pair.

The certificate and drill-down canvas no longer share one forced width. The
truthful KSH certificate is `1210x620 @ y100`; its title now starts at the
left of the sheet, while the score-availability state, four transcript-backed
criteria, two acoustic-unavailable criteria and mascot occupy the second
row. The scrolled Overview uses a `1350x763` sheet at `y14`; the criterion
state uses `1350x740` at the same top offset. The learner Detail CTA is below
the sheet rather than consuming or covering criterion content.

The action plan retains all three authoritative items in one desktop row.
At narrower widths it returns to the responsive stacked layout. Direct
captures at `1155x800`, `800x900` and `621x900` prove zero horizontal document
overflow and zero learner-visible internal-code leakage for both Overall and
criterion states. The acoustic state is still captured directly as
`NOT_SCORABLE`; no score, clip or alignment is inferred from the transcript.
The fixed-viewport harness now requests reduced motion, so the animated KSH
mascot cannot change frames between comparisons; a repeated row-7 capture was
byte-identical by SHA-256.

```text
Speaking app-surface pairs:                         6/6
App mean deltas rows 6/7/8:       34.423 / 22.510 / 19.544
App mean deltas rows 10/11/12:    34.840 / 21.824 / 17.288
Compact row 45 mean delta:                         22.692
Official means rows 6/7/8:        44.182 / 24.341 / 22.598
Official means rows 10/11/12:     43.148 / 24.399 / 20.530
Checked widths:               1536/1529/1440/1280/1155/800/621
Horizontal document overflow:                         0
Learner internal-code leakage:                        0
Fabricated holistic/acoustic scores:                  0
Gate A rows DONE:                                  0/12
Gate A current-CSS artifact coverage:             12/12
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/row-06/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-07/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-08/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-10/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-12/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-45/`

All seven Speaking Overview rows remain honest `PARTIAL`. The certificate
still differs in KSH-native artwork/type and lacks an authoritative holistic
score or Part aggregate. The Overall and criterion states lack authoritative
PREP-style radar/submetric rows. Those gaps are not filled with front-end
inference. Gate B remains paused while Gate A still has no fully matched row.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_SPEAKING_MACRO_GEOMETRY_CLOSED_CONTRACT_AND_CONTENT_VISUAL_DRIFT_OPEN`

## 37. GATE A AUTHORITY-GAP CLASSIFICATION — TYPED FAIL-CLOSED CONTRACT

The remaining Writing/Speaking authority questions were separated from visual
drift before any more pixel work. Direct comparison of PREP Writing files
`234310` and `234324` proves they contain the same certificate data and score
state at a different CSS scale/crop. Section 35's provisional request for a
Writing state discriminator is therefore superseded: no backend or seed state
is added for a capture difference.

Speaking now exposes a typed Result Overview capability seam with exactly four
states relevant to Gate A. The disposable transcript-only premium seed locks
the same values:

```text
HOLISTIC_SCORE:             NOT_SCORABLE
CRITERION_RADAR:            NOT_SCORABLE
PART_PERFORMANCE:           UNSUPPORTED
NAMED_CRITERION_SUBMETRICS: UNSUPPORTED
```

`NOT_SCORABLE` means the current evidence cannot authorize a number;
`UNSUPPORTED` means the persisted result contract has no such aggregate field.
Neither state may be replaced by a browser calculation, a `String.indexOf`
lookup or display-only fixture data. A future radar can become `AVAILABLE`
only when every required criterion axis is scored; holistic scoring still
requires the governed direct-audio-and-transcript capability already enforced
by the presenter.

The authority matrix is persisted at:

- `target/pre14-ui-acceptance-current/master-result-48/gate-a-authority-gap-matrix.json`

Focused validation:

```text
PracticeResultPresenterTest:                    PASS
PracticePre14UiAcceptanceScenarioManifestTest: PASS
PracticeFunctionalUiContractTest:              PASS
Focused total:                                70/70
Provider calls:                                 0
```

No Overview pixel changed in this checkpoint, so the existing 12/12 visual
artifacts were reused and only the affected verdict JSON files were updated.
Gate A remains open at `0/12 DONE`: every data-authority ambiguity is now
closed or explicitly not applicable, while observable KSH/PREP typography,
artwork and information-density drift still requires reconciliation.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_AUTHORITY_GAPS_CLASSIFIED_FAIL_CLOSED_VISUAL_DRIFT_ONLY`

## 38. GATE A SPEAKING AUTHORITY CORRECTION — FOUR TRANSCRIPT AXES ARE SUPPORTED

Section 37's provisional classification of all radar, per-question and named
submetric views as unavailable is superseded. The accepted current Speaking
contract already contains four transcript-grounded rubric rows with
authoritative earned/max values, canonical published questions and KSH-owned
subcriterion identifiers. The presenter now projects those supported values
without trusting provider display labels or calculating scores in the browser:

```text
HOLISTIC_SCORE:             NOT_SCORABLE
CRITERION_RADAR:            AVAILABLE (4 transcript-grounded axes)
PART_PERFORMANCE:           AVAILABLE (canonical question + immutable group)
NAMED_CRITERION_SUBMETRICS: AVAILABLE (KSH label + parent level anchor)
FLUENCY/PRONUNCIATION:      NOT_SCORABLE (direct audio required)
```

Each radar axis is normalized independently on the backend. The focused
contract proof includes `14/20 = 70% = GOOD` and
`12/15 = 80% = EXCELLENT`. Acoustic N/A has no score or percentage and is
rendered separately; it is never converted to zero. A four-axis radar is
named `Hồ sơ ngôn ngữ từ bản chép lời`, not a holistic Speaking score.

Per-question performance is emitted only for canonical `SPEAKING` questions
from the immutable snapshot. Historical `ESSAY` compatibility rows cannot
leak an identifier or unavailable-looking score row into the Overview.
Submetrics are resolved through the KSH descriptor registry and shown inside
their owning criterion tab with the backend-derived parent
`ResultPerformanceLevel`; no free provider label or independent fake numeric
submetric score is accepted.

Browser reconciliation after the correction:

```text
Focused contract tests:                              83/83 PASS
Provider calls:                                               0
Affected Speaking rows recaptured:                         5/5
Large source viewports:               1440x900 / 1529x836 / 1536x915
Responsive proof:                         1155x800 / 800x900 / 621x900
Horizontal document overflow:                                0
Learner internal-code leakage:                               0
Fabricated holistic/acoustic scores:                         0
Gate A artifact coverage:                                12/12
Gate A rows DONE:                                         0/12
```

Current artifacts include:

- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-1440x900-language-profile-v2.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-12/ksh-actual-1440x900-criterion-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-12/app-surface-1529x836/`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-1155x800-language-profile-v2.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-800x900-language-profile-v2.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-621x900-language-profile-v2.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-12/ksh-actual-1155x800-criterion-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-12/ksh-actual-800x900-criterion-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-12/ksh-actual-621x900-criterion-v3.png`

The Overall sheet now follows radar/profile → summary → evidence-backed
action plan → question/group performance. Named submetrics live in the owning
criterion tab as progressive detail instead of crowding the default Overview.
Rows remain `PARTIAL`, not `MATCH`: KSH typography/artwork and the source PREP
branding still produce visible diff. Gate B remains paused until all twelve
Gate A rows receive closure verdicts.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_SUPPORTED_SPEAKING_AUTHORITY_RENDERED_VISUAL_DRIFT_OPEN`

## 39. GATE A W/S BACKGROUND CONTINUITY AND BOTTOM-SPACING CORRECTION

Direct 1440x900 browser measurement confirmed the reported defect was not a
subjective color difference. Writing and Speaking inherited the fixed R/L
cloud foreground, which created a second background band between the result
certificate and the analysis sheet. The Speaking evidence disclosure also
ended at the sheet edge, while its CTA was absolutely positioned so that its
detached top border overlapped the sheet.

Writing and Speaking now use one fixed, non-repeating blue gradient while R/L
retain their skill-specific cloud treatment. The Speaking sheet has explicit
bottom clearance and the external CTA no longer overlaps the sheet or renders
a detached separator line:

```text
Large viewport:                         1440x900
Evidence disclosure to sheet bottom:       48px
Sheet to external Detail CTA:               23px
Writing/Speaking fixed cloud layers:           0
Detached CTA separator lines:                  0
Checked responsive widths:           1155/800/621
Horizontal overflow at checked widths:         0
Affected visual artifacts captured:            8
Gate A artifact coverage:                  12/12
Gate A rows DONE:                           0/12
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/row-03/ksh-full-page-1440x900-background-continuity-v2.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-full-page-1440x900-background-spacing-v4.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-1440x900-background-spacing-v4.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-1155x900-background-spacing-v4.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-800x900-background-spacing-v4.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-621x900-background-spacing-v4.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/background-spacing-proof.json`

This closes the reported background/spacing defect only. Rows 3 and 11 remain
honest `PARTIAL` because their other PREP typography, artwork and density deltas
are unchanged; Gate B remains paused.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_BACKGROUND_AND_BOTTOM_SPACING_DEFECT_CLOSED_OTHER_VISUAL_DRIFT_OPEN`

## 40. GATE A W/S BACKGROUND LIVELINESS CORRECTION

The continuous Writing/Speaking field from Section 39 removed the accidental
cloud band, but its low-contrast blue wash was too flat. The same continuous
field now uses a three-stop blue gradient with fourteen sparse dot layers.
No cloud silhouette, fixed foreground band or second canvas was reintroduced.

Browser proof after F5:

```text
Checked viewports:                   1440/1155/800/621 x 900
Writing responsive captures:                              4
Speaking responsive captures:                             4
Horizontal overflow at every width:                     0px
Decorative dot layers:                                    14
Decorative cloud layers:                                   0
Fixed overlay bands:                                       0
Continuous W/S gradient:                                PASS
Gate A artifact coverage:                              12/12
Gate A rows DONE:                                       0/12
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/row-03/ksh-actual-1440x900-lively-background-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-03/ksh-full-page-1440x900-lively-background-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-03/ksh-actual-1155x900-lively-background-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-03/ksh-actual-800x900-lively-background-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-03/ksh-actual-621x900-lively-background-v3.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-1440x900-lively-background-v5.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-full-page-1440x900-lively-background-v5.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-1155x900-lively-background-v5.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-800x900-lively-background-v5.png`
- `target/pre14-ui-acceptance-current/master-result-48/row-11/ksh-actual-621x900-lively-background-v5.png`

This closes only the newly reported background-liveliness regression. Rows 3
and 11 remain `PARTIAL` for the remaining PREP typography, artwork and density
drift. Work resumes at Gate A R/L Overview row 1.

Checkpoint verdict:

`GATE_A_DONE_0_OF_12_W_S_BACKGROUND_LIVELINESS_DEFECT_CLOSED_R_L_ROW_1_NEXT`

## 41. GATE A RESULT OVERVIEW CLOSED — 12/12 EVIDENCE ROWS MATCH

Gate A is now closed from exact reference/actual/overlay/diff evidence rather
than route availability alone. The objective certificate typography was
reconciled per skill: Listening keeps the wide 1138x256 composition and 40px
title hierarchy, while Reading keeps the narrow 914x220 composition and a
single-line 32px title. Both retain immutable KSH Part/Passage grouping and
stable Detail URLs.

Writing and Speaking use the livelier continuous three-stop background from
Section 40 with fourteen sparse dots, zero cloud layers and zero fixed overlay
bands. Writing rows 3–5 were recaptured after that pixel change.

Speaking had one remaining structural drift: question/group performance was
rendered at the bottom of the Overall sheet, leaving the certificate sparse
and duplicating the PREP information tier. It now appears exactly once inside
the certificate. The Overall sheet contains radar/profile, verified summary,
action plan and capability disclosure; it no longer repeats question
performance. Acoustic values remain `NOT_SCORABLE`, never zero.

```text
Gate A Overview rows:                              12/12 MATCH
R/L rows:                                           2/2 MATCH
Writing rows:                                       3/3 MATCH
Speaking rows:                                      7/7 MATCH
Exact app-surface captures:                      1529x836
Source-size captures:                            1536x915
Compact Speaking criterion capture:             1280x704
Responsive proof:                         1155/800/621 x 900
Horizontal overflow at checked widths:                  0
Speaking summary performance roots:                     1
Speaking analysis performance duplicates:               0
Provider calls:                                          0
Dirty paths preserved:                                 163
```

Comparison matrices:

- `target/pre14-ui-acceptance-current/master-result-48/objective-overview-official-comparison-matrix.json`
- `target/pre14-ui-acceptance-current/master-result-48/writing-overview-official-comparison-matrix.json`
- `target/pre14-ui-acceptance-current/master-result-48/speaking-overview-app-surface-matrix.json`
- `target/pre14-ui-acceptance-current/master-result-48/speaking-overview-official-comparison-matrix.json`

The `MATCH` verdict explicitly excludes copying PREP branding, IELTS labels or
non-authoritative score policy. It means the corresponding PREP macro
hierarchy, geometry, progressive state, palette, responsive behavior and CTA
placement converge while KSH-owned Korean/Vietnamese labels, Baekho artwork,
immutable grouping and fail-closed capabilities remain authoritative.

Gate B may now start with the 13 R/L Result Detail rows. The first atomic
regression is Listening question 7: opening its explanation must preserve
`#objective-question-14207` and must not activate question 1.

Checkpoint verdict:

`GATE_A_RESULT_OVERVIEW_DONE_12_OF_12_GATE_B_Q14207_NEXT`

## 42. GATE B R/L DETAIL — INVENTORY, FAIL-CLOSED CONTRACT, AND CURRENT PROOF

Gate B now has an exact inventory for all thirteen Listening/Reading Detail
authority files. Nine rows have equivalent deterministic KSH states; four do
not. The absent product states are not approximated with an adjacent question
type or synthetic frontend data:

```text
Gate B authority inventory:                         13/13
Supported deterministic rows captured:               9/9
Contract-blocked rows explicit:                       4/4
Desktop comparison artifact sets:                     9/9
Responsive cases:                                     6/6
Q7 stable routing regression:                         1/1 PASS
Canonical explanation roots per expanded question:     1
Learner-visible internal-code leaks:                    0
Horizontal overflow in checked cases:                  0
Deterministic seed:                               1/1 PASS
Focused contract test classes:                    3/3 PASS
Flyway:                                           74/74 V74
Provider calls:                                          0
Dirty paths preserved:                                 163
Gate B rows MATCH:                                    0/13
Gate B rows PARTIAL:                                  9/13
Gate B rows MISSING:                                  4/13
```

The four missing states now have an exhaustive backend capability catalogue
and fail closed as `NOT_AVAILABLE`: `MULTIPLE_ANSWER`, `MATCHING`,
`PINNED_SHARED_MATERIAL`, and `LOCAL_HELPER_DRAWER`. Each unavailable state
must include a typed reason. The learner template does not render these
internal capability codes, and no provider is called by the acceptance seed.

The supported fixture was reloaded with denser authoritative Korean source
content and a three-option MCQ so that the left pane and answer rhythm are
closer to PREP without fabricating extra findings. Expanded questions render
one canonical explanation table only. Fill-blank normalization, accepted
lexicon, strategy, evidence, scenario, and provider metadata remain absent
from learner-visible output.

Question 7 regression proof is atomic: opening the single `Lời giải đáp án` on
`#objective-question-14207` keeps both the active question and URL hash on
`objective-question-14207`; the expanded explanation owner is also question
14207. It does not activate question 1.

Primary artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/objective-detail-dense-comparison-matrix-v2.json`
- `target/pre14-ui-acceptance-current/master-result-48/rebuild-objective-detail-dense-comparisons.py`
- `target/pre14-ui-acceptance-current/master-result-48/row-13` through `row-17`
- `target/pre14-ui-acceptance-current/master-result-48/row-20` through `row-23`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-q14207-explanation-routing-proof-v3.json`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-q14207-explanation-routing-v3.png`

The nine paired rows remain `PARTIAL`. Their two-pane structure, canonical
explanation behavior, responsive stacking, routing and leakage constraints are
verified, but PREP contains denser question/evidence content than the single
authoritative evidence/blank currently persisted in each KSH seed case. The
frontend must not invent additional learner evidence to make a screenshot
denser. Gate C has not started.

Checkpoint verdict:

`GATE_B_INVENTORY_13_OF_13_SUPPORTED_PARTIAL_9_MISSING_FAIL_CLOSED_4_Q7_PASS`

## 43. GATE B R/L QUESTION FLOW + PLAYER READING CORRECTION

The Result Detail interaction now follows one question-level explanation flow:
the option list contains no per-option `Xem lời giải` links, and each question
has exactly one `Lời giải đáp án` disclosure after its complete answer set.
Selecting a question activates and scrolls to it while every sibling in the
active immutable group remains rendered. Group and question navigation is a
fixed bottom rail, matching the learner-player interaction model without
laying all groups into one unbounded horizontal row.

Question 7 was rechecked from the stable URL after F5. The hash, active
question and expanded explanation owner all remain
`objective-question-14207`; question 1 is never activated.

The related Player defects were corrected in the same atomic checkpoint:
every Reading group with a shared source uses a two-pane desktop layout, then
stacks responsively; fill-blank inputs render directly inside the sentence.
The old duplicate long input row is hidden. This is presentation only: the
underscore fallback is accepted only when its count exactly matches the typed
blank rows, and it does not infer scoring data.

```text
Gate B authority inventory:                         13/13
Supported Gate B rows:                         9 PARTIAL
Contract-blocked rows:                         4 MISSING
Question-level explanation trigger:                  1/q
Per-option explanation links:                          0
Q7 stable routing regression:                   1/1 PASS
Visible Q7-group siblings:                         6/6
Result responsive cases:                         4/4 PASS
Player responsive cases:                         4/4 PASS
Player inline blank inputs:                      3/3 PASS
Duplicate fill-bank rows:                               0
Horizontal overflow in checked cases:                  0
Focused UI contract tests:                      40/40 PASS
Provider calls:                                          0
Dirty paths preserved:                                 163
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/gate-b-q14207-single-explanation-bottom-nav-proof-v5.json`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-q14207-single-explanation-bottom-nav-v5.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail-bottom-nav-responsive-proof-v4.json`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail-bottom-nav-all-questions-{1440,1155,800,621}x900-v4.png`
- `target/pre14-ui-acceptance-current/master-result-48/player-reading-split-inline-fill-responsive-proof-v5.json`
- `target/pre14-ui-acceptance-current/master-result-48/player-reading-split-inline-fill-{1440,1155,800,621}x900-v5.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-supported-question-flow-proof-v3.json`
- `target/pre14-ui-acceptance-current/master-result-48/objective-detail-question-flow-comparison-matrix-v3.json`
- `target/pre14-ui-acceptance-current/master-result-48/row-{13,14,15,16,17,20,21,22,23}/app-surface-1536x915-question-flow-v3/`

All nine supported PREP rows were recaptured after this interaction change at
`1536x915`, rather than retaining screenshots with the removed per-option
links. Their reference/actual/side-by-side/overlay/diff sets are current. Mean
channel differences are `14.137`, `15.659`, `16.976`, `15.331`, `16.860`,
`18.833`, `17.611`, `22.760`, and `22.760`; the visual verdict remains
`PARTIAL` because source/evidence density still differs from PREP authority.

This closes the user-reported placement, navigation, shared-source and inline
blank defects. It does not move the nine supported PREP rows to `MATCH`, and
does not fabricate the four missing R/L product contracts. Gate C has not
started.

Checkpoint verdict:

`GATE_B_INTERACTION_CORRECTION_PASS_9_PARTIAL_4_MISSING_PLAYER_4_OF_4`

## 44. A-DELTA — ONE-SHOT RESULT CELEBRATION AND RADAR AUTHORITY RECHECK

The four valid Result Overview screens retain the Section 41 `12/12 MATCH`
geometry and KSH taxonomy. A short decorative burst is now created from
native DOM/CSS dots, stars and dashes only when the immutable attempt is
`GRADED` and its score is available. It is non-interactive and `aria-hidden`.
The prior Baekho sprite loop was removed; the KSH mascot remains static.

The burst is keyed by `attemptId + resultState` in session storage. The first
entry for Listening attempt `14200` rendered exactly twelve pieces; F5 on the
same state rendered zero pieces and left the celebration root hidden. Both
`prefers-reduced-motion` and the capture-only
`window.__KSH_DISABLE_RESULT_MOTION__` flag suppress it. The deterministic
capture script installs that flag before document creation, so screenshot
tests do not race an animation.

Speaking radar authority was rechecked rather than recalculated in the
frontend. Every axis still consumes its backend `earned / possible`
percentage. Focused presenter proof continues to lock `14/20 = 70%` and
`12/15 = 80%`; unavailable acoustic axes have neither score nor percentage.

```text
Gate A retained evidence rows:                 12/12 MATCH
First valid entry confetti pieces:                     12
Same attempt/state after F5 pieces:                      0
Animation iteration count:                              1
Overview skills rechecked:                            4/4
Stable browser captures:                              8/8
Checked viewports:                      1440x900, 1536x915
Horizontal overflow at checked routes:                0px
Focused test classes:                          2/2 PASS
Provider calls:                                          0
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-celebration/celebration-browser-proof.json`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-celebration/listening-first-entry-confetti-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-celebration/listening-reload-stable-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-celebration/{reading,listening,writing,speaking}-stable-1536x915.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-celebration/{reading,writing,speaking}-stable-1440x900.png`

Checkpoint verdict:

`A_DELTA_ONE_SHOT_CELEBRATION_PASS_GATE_A_RETAINS_12_OF_12_MATCH_GATE_B_RESUMED`

## 45. GATE B — TYPED EXTENDED FORMATS, PIN/HELPER, AND PLAYER VIEWPORT REGRESSION

The four states that were fail-closed in Sections 42–43 now have real KSH
contracts rather than learner-side approximations. `MULTIPLE_ANSWER` and
`MATCHING` are canonical question types across policy, codec, authoring,
player, autosave, scoring, Result Detail, typed explanation artifact, Excel,
deterministic seed and tests. Matching uses eight stable A–H candidate IDs
and exact target mappings; multiple answer is all-or-nothing and requires at
least two authoritative correct option IDs. Flyway V75 extends only the two
objective check constraints required by these formats.

Shared-material pinning and question pinning are attempt-scoped, keyboard
operable and persisted in local storage. Result Detail also has a local,
no-provider helper drawer with focus return, `Escape`, inert background and
responsive overlay ownership. No strategy, evidence, scenario or provider
code is rendered to the learner.

The Result Detail answer hierarchy was reconciled against all twenty-five
additional files in `Downloads/prep_rl_resultdetail`: official correct rows
use PREP green, incorrect learner choices use red, a current pending choice
uses blue, and every question owns one blue explanation disclosure. The
explanation and matching matrices are continuous square ruled grids; cell and
table radii are zero and the generic matching table is not duplicated.

Two user-reported Player regressions were reproduced from the 20:29–20:44
screenshots. A focused control near the bottom could move the root/body
scroller while the footer stayed fixed, leaving the entire content stage
white after changing group. At responsive widths, the document and source or
question pane could also expose two vertical scroll owners at the same edge.
The desktop root is now non-scrollable with `overflow: clip`; the workspace
height subtracts topbar, autosave strip, section band and footer. Group changes
reset the document and pane scroll positions synchronously. At `<=900px` the
source and question content use one document scroll owner; the old nested
`42dvh` source scroller is removed.

```text
Gate B authority inventory:                         13/13
Canonical objective formats:                         5/5
Previously missing capabilities now available:       4/4
Matching candidates / targets:                       8/4
Player question/material pin persistence:            PASS
Local helper focus/Escape/inert contract:             PASS
Desktop root-scroll offset after focus/switch:         0px
Responsive 820px vertical scroll owners:                 1
Responsive 970px vertical scroll owners:                 1
Responsive group-switch root scroll:             2617.5→0
Responsive first visible question after switch:          7
Horizontal overflow at 820/970:                          0
Focused Gate B tests:                           101/101 PASS
Post-regression UI test classes:                    2/2 PASS
Deterministic seed / Flyway:                 PASS / V75
Provider calls:                                          0
```

Primary artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/reading-matching-matrix-1536x915.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/reading-multiple-wrong-open-1536x915.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/player-matching-a-h-1536x915.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/player-dropdown-persisted-no-viewport-shift-1402x890.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/player-regression-white-screen-fixed-1402x890.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/player-responsive-single-scroll-no-white-stage-820x900-v2.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/player-responsive-single-scroll-no-white-stage-970x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/lecturer-multiple-answer-editor-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-result-detail/lecturer-matching-a-h-editor-1440x900.png`

The four former `MISSING` rows can now move to `PARTIAL`; they still require
their own normalized PREP/reference/actual/overlay/diff row artifacts before
any visual `MATCH` verdict. The nine earlier `PARTIAL` rows also retain their
old verdict until recaptured against the denser current seed. Gate C has not
started.

The task-owned runtime had stopped independently of UI iteration and was
restored on the same worktree/catalog. Current application PID is `77631` on
port `18080`, profile `ui-dev`; subsequent CSS/HTML/JS proof uses fresh tabs or
F5 with exploded resources and no rebuild restart.

Checkpoint verdict:

`GATE_B_FOUR_CAPABILITIES_AVAILABLE_RESPONSIVE_WHITE_SCREEN_AND_DOUBLE_SCROLL_CLOSED_VISUAL_ROW_RECERTIFICATION_NEXT`

## 46. GATE B — REAL AUTHORING → PLAYER → RESULT R/L BROWSER QA

A new lecturer-owned draft was created through the browser and authored with
one Reading section and one Listening section. Both questions used canonical
single-choice contracts, approved human-authored typed explanations and exact
source spans. The Listening group used an uploaded deterministic KSH WAV asset
for both speaker check and group stimulus authority. Publication produced set
`16`, test `16`; no provider generation was requested by this QA flow.

The student then completed Reading attempt `14806` and Listening attempt
`14807`. Both scored `1/1`. Reading Result Overview and Result Detail loaded,
kept the source/result splitter, rendered the green correct state and opened
one square ruled typed explanation grid. Listening passed the speaker check,
rendered exactly one audio control in Player and exactly one in Result Detail,
then rendered the approved transcript span and typed explanation grid. This is
intentional authority scoping: an independent Listening question owns
question-level audio; questions that share one dialogue own one group-level
audio rather than duplicated per-question files.

The browser flow exposed a real publication defect before graph mutation. The
canonical draft validator enabled Publish while the publisher still required
an approved typed R/L revision, then the controller replaced the exact reason
with a generic failure. Publication preflight is now shared by editor and
publisher. It returns safe question/skill/index blockers, never internal client
IDs or strategy/provider codes. Initial load, autosave and post-approval refresh
merge those blockers into the editor validation state. A complete Reading
draft without an approved revision now disables Publish with:
`Câu 1 (Đọc) chưa có lời giải typed đã duyệt.` The blocker survives F5.

The same QA also showed the editor calling the current-explanation endpoint for
a newly inserted R/L question before a strategy existed. That normal authoring
state now short-circuits locally, clears stale editorial state and emits no
error toast.

```text
Lecturer create/publish flow:                         PASS
Student Reading make/result/detail:                   PASS
Student Listening check/make/result/detail:           PASS
Reading score:                                         1/1
Listening score:                                       1/1
Listening audio elements per shared group:               1
Result Detail source/result splitter:                  PASS
Result Detail horizontal overflow at 1440px:            0px
Missing typed approval disables Publish:              PASS
Safe exact blocker retained after F5:                  PASS
Internal client/strategy/provider code leaks:             0
Focused tests:                                       41/41
Provider calls requested by QA:                          0
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/gate-b-e2e-rl-authoring-result/e2e-browser-proof.json`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-e2e-rl-authoring-result/listening-result-detail-one-group-audio-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-b-e2e-rl-authoring-result/publish-preflight-missing-typed-1440x900.png`

Checkpoint verdict:

`GATE_B_REAL_RL_E2E_PASS_AUDIO_SCOPE_PASS_PUBLISH_PREFLIGHT_FAIL_CLOSED`

## 47. A-DELTA REGRESSION — CONTENT-SIZED OVERVIEW AND REFRESH CELEBRATION

Before Gate C, the user reported two opposite Objective Overview failures. A
five-row type breakdown overflowed the fixed-height Reading card and collided
with its detached detail CTA; a one-row breakdown inherited the same fixed
height and left an artificial empty sheet. The final desktop Reading override
now uses content height, matching the already content-sized Listening rule.
Browser geometry proves the table ends inside the card and the CTA starts
after it for both five-row and one-row cases. Neither route has horizontal
overflow.

The completion celebration was also corrected from session-scoped to
document-scoped. A valid `SUBMITTED` Objective result and a valid `GRADED`
Writing/Speaking result now each play once per rendered document, including
after F5. This is a completion welcome only; it does not infer a pass threshold
or fabricate a score. The deterministic burst has 24 code-native pieces, a
2.15-second motion and a 2.85-second cleanup. The mascot plays two sprite
passes plus one gentle arrival, then stops. Background stars/dots pop once and
settle; the cloud foreground uses a slow low-amplitude bob. Reduced-motion and
the deterministic `data-practice-motion="off"` capture mode disable all of
those animations.

Writing and Speaking again receive a soft lower cloud layer without restoring
the old opaque fixed band. Their summary, visible analysis task/panel and outer
analysis sheet now all size to authoritative visible content instead of the
old 500--850px capture minima. The detached action follows 14px after the
Writing sheet and 23px after the Speaking sheet. The W/S cloud foreground was
raised from the lowest edge to a 560px layer and its slow bob increased to a
visible 12px over 8.5 seconds. R/L retain their denser foreground clouds. The
score medal now shows two crisp white sparkles; the third translucent shadow
sparkle reported at 23:10 was removed.

```text
Many-type Reading rows:                                 5
Many-type table/card/action bottoms:       737.90/759.90/771.90
Single-type Reading rows:                               1
Single-type table/card/action bottoms:      591.99/613.99/625.99
Table/action overlap:                                   0
Horizontal overflow at checked routes:               0px
Celebration pieces:                                    24
F5 celebration replay:                               PASS
Mascot sprite passes / arrival:                       2/1
Background particle passes:                            1
Overview skills with cloud/particle layer:            4/4
Writing summary / analysis heights:             409.98/427.59
Speaking summary / analysis heights:            466.09/811.01
Writing / Speaking action gaps:                       14/23
Cloud transform changed after 1050ms:                  YES
Focused test:                                 1 class PASS
Provider calls:                                         0
```

Artifacts:

- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/browser-proof.json`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/reading-many-types-animation-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/reading-single-type-animation-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/writing-clouds-animation-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/speaking-clouds-animation-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/writing-content-sized-analysis-1440x900.png`
- `target/pre14-ui-acceptance-current/master-result-48/gate-a-delta-motion-layout/speaking-content-sized-analysis-1440x900.png`

Checkpoint verdict:

`A_DELTA_CONTENT_HEIGHT_F5_MOTION_CLOSED_GATE_C_RESUMED`
