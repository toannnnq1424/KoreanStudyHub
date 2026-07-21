# Practice Phase 15 Compatibility Cleanup And Seed UAT Inventory

> Status: `FUTURE_PRE_PHASE_15_DESIGN_AND_INVENTORY_DEFINED`
>
> Recorded: `2026-07-22` (research started on `2026-07-20`; supersedes the inventory-only boundary recorded on
> `2026-07-16`)
>
> Current roadmap position: the bounded Phase 13D result-overview UX correction
> is `IMPLEMENTED_PENDING_PHASE_VALIDATION`; Phase 13E is still unopened and in
> preparation. This document is research/design inventory now, not permission
> to start the work below or to skip Phase 13E, 13F-13H or Phase 14.
>
> Future owner: Pre-Phase-15 production correctness, compatibility closure and
> Manual UAT entry gate

## 1. Purpose and Phase 13D boundary

This document now has two future-facing jobs:

1. record compatibility paths, legacy test-data behavior, old routes, adapters,
   fixtures and documentation that must be resolved together; and
2. design and inventory the production-correctness work that must eventually be
   implemented **after its roadmap dependencies and before Phase 15 is allowed
   to start**.

It does **not** change the current required next action in
`CODEX_PRACTICE_WORKFLOW.md`: the coordinator must review the complete
`PHASE_13D_UX_CORRECTION` diff, declare `READY_FOR_PHASE_VALIDATION` and run its
single consolidated gate before resuming Phase 13E preparation. No
`P15-PRE-*` item is authorized for opportunistic implementation during that
correction/preparation. If Phase 13E, 13F-13H or Phase 14 legitimately replaces
one of the listed files/contracts, the future gate audits the replacement and
does not redo the work merely to touch the old filename.

Phase 15 is for release hardening, deterministic seed loading and Manual UAT.
It is not the phase in which KSH first discovers that a score has no supporting
evidence, that a rubric profile is ambiguous, or that one explanation JSON
shape is being forced onto three different question constructs.

The compatibility portion remains an inventory, not retroactive authorization
to delete historical paths. The production-correctness portion is different:
its `FUTURE_PHASE_15_ENTRY_BLOCKER` items become mandatory only when their
approved execution window is reached after the current roadmap dependencies
and before the Phase 15 premium seed or Manual UAT.

The required order is:

1. continue the current Phase 13E preparation/implementation only after its
   separate GO, then finish the approved Phase 13/14 dependencies without
   opportunistic deletion of this inventory;
2. finish any replacement surface that an active compatibility path depends on,
   especially the canonical result-detail and content-review boundaries;
3. execute the mandatory Pre-Phase-15 production-correctness slices in Section
   4 and obtain their automated/calibration evidence;
4. confirm the target environment, deployed-database history and data-retention
   obligation;
5. resolve compatibility code, tests, routes, schema history and current docs as
   one reviewed program;
6. prove a fresh canonical database and, where required, a representative
   upgrade path;
7. only then mark the Pre-Phase-15 gate green, create the deterministic premium
   UAT seed and enter Phase 15 Manual UAT.

## 2. Safety assumptions

- Current local data, including ad-hoc attempts such as `687`, `688` and the
  later `13001..13006` result/player fixtures, is
  development fixture data and is not production evidence.
- The current project is treated as pre-production for planning purposes only.
  Phase 15 must reconfirm that no deployed database or retained learner attempt
  depends on a removal candidate before destructive schema/data work.
- If production or retained historical data exists, immutable attempt isolation
  and required migrations are not removable debt. The cleanup plan must migrate
  or archive that data before code removal.
- A confirmed removal candidate is not authorization to edit historical
  migrations or delete retained data. Environment/deployment proof still comes
  first.

## 3. Canonical target contract

Phase 15 cleanup and the final seed must converge on these contracts:

- Reading and Listening use only `SINGLE_CHOICE`, `FILL_BLANK` and
  `TRUE_FALSE_NOT_GIVEN`, backed by immutable published versions.
- Writing uses only Q51-Q54 as `ESSAY`, with fixed maximum points
  `10/10/30/50`, the Writing evaluator and task-native Korean rubrics.
- Speaking uses only `SPEAKING` and must not route through Writing/ESSAY
  grading. The current Phase 13 runtime exposes a transcript-grounded language
  profile only: Content `20`, Grammar `20`, Vocabulary `15` and Coherence `15`
  retain their native rows; Fluency and Pronunciation/Delivery are
  `NOT_SCORABLE` with null numeric fields; there is no `/70` subtotal and no
  holistic Speaking score. A future full overview is permitted only after an
  authorized evaluator actually consumes learner audio and clears the
  calibration, privacy and rollout gates below.
- Every new attempt has a complete immutable published/set/test/section version
  lock. Result, progress and media access do not read mutable live content.
- Reading/Listening explanations use the Phase 13D artifact, version binding and
  durable task lifecycle. Their explanation payload is construct-native for
  `SINGLE_CHOICE`, `FILL_BLANK` and `TRUE_FALSE_NOT_GIVEN`. Result GET remains
  read-only.

## 4. Future mandatory Pre-Phase-15 production-correctness gate

### 4.1 Entry rule and blocker register

The following work is being designed now while Phase 13E is still only in
preparation. It is not current-phase implementation authorization. At the
future Phase 15 entry boundary, however, it is not accepted debt that may be
discovered during Manual UAT. Every row must have one explicit closure before
Phase 15 can move from `NOT_STARTED` to `IN_PROGRESS`:

- `IMPLEMENTED_AND_ACCEPTED` when new code/data/policy was required;
- `SATISFIED_BY_PRIOR_PHASE_WITH_EVIDENCE` when a prior phase already delivered
  the exact contract and the entry audit verifies it without duplicate work;
- `DISABLED_BY_POLICY_WITH_PROOF` or `NOT_APPLICABLE_WITH_PROOF` only for an
  optional capability that will remain unavailable in production/UAT.

The disabled path is not a paperwork escape. It must prove the capability
cannot be enabled by configuration alone, no unsupported score/claim appears,
and readiness/UI/provider request behavior stays fail-closed. Focused tests,
academic calibration and privacy evidence apply to the capability actually
being released; acoustic calibration is not fabricated for a direct-audio
capability deliberately kept disabled.

| ID | Severity | Current source truth | Required decision/change before Phase 15 | Exit proof |
| --- | --- | --- | --- | --- |
| `P15-PRE-01` | `FUTURE_PHASE_15_ENTRY_BLOCKER`; transcript-only guard `IMPLEMENTED_PENDING_PHASE_VALIDATION` | Phase 13D UX-03..05 now use `TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION` / `TRANSCRIPT_ONLY`: `SpeakingEvaluationResult`, `SpeakingEvaluationNormalizer`, `SpeakingScorePolicy`, `PracticeService`, `SpeakingResultPresenter` and `speaking.html` keep only four native numeric language rows, make both acoustic rows null/`NOT_SCORABLE`, expose no `/70` subtotal, aggregate, holistic or attempt score and fail legacy/reserved envelopes closed. A trusted score-bearing typed envelope requires exactly all six rows; authoritative `TRANSCRIPTION_LOW_CONFIDENCE` keeps current transcript provenance with an empty rubric profile and no number. `OpenAiCompatibleSpeakingEvaluationClient` still sends only transcript plus optional question image; `AUDIO_DIRECT_FULL_RESERVED` is a disabled seam, not a capability. | Preserve the transcript-only, low-confidence and evidence guards through consolidated `PHASE_13D_UX_CORRECTION` validation. Before Phase 15 Manual UAT, choose exactly one branch: (A) keep full/audio-grounded Speaking disabled; or (B) after PRE-08 policy approval, implement a dark separately named evaluator/API that demonstrably consumes authorized learner audio, then pass PRE-07 calibration/readiness before rollout. Configuration flags alone cannot authorize branch B. | Branch A closes as `DISABLED_BY_POLICY_WITH_PROOF`: no request sends audio to the scorer, readiness stays blocker-red and every surface exposes only the transcript profile plus null acoustic rows. Branch B closes as `IMPLEMENTED_AND_ACCEPTED`: captured request proves authorized audio reached the scorer and PRE-07/PRE-08 evidence is green. Both branches must preserve no-claim tests for low-confidence/current, legacy/reserved/malformed payloads. |
| `P15-PRE-02` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Writing has useful task-native rubrics and strict evidence spans, but the scoring profile is unnamed: Q53 is `12/9/9`, Q54 is `20/15/15`; prompt text also mixes conditional authoritative-task rules with unconditional Q54 “three questions” requirements. `TASK_METADATA` exists but provider findings using it are rejected. Character warnings describe deductions that the backend does not deterministically apply, and there is no governed performance-descriptor plus finding-impact/score-consistency model. | Choose and version one explicit profile. `KSH_INTERNAL_BALANCED` may retain justified internal weights but must never be presented as official TOPIK. `TOPIK_ALIGNED` must use the public official task criteria and weights recorded in the design source. Add approved performance descriptors and score/finding consistency/impact invariants; remove prompt contradictions, make task requirements/data authoritative only when supplied, implement deterministic length/format policy, and either support validated `TASK_METADATA` or remove claims that require it. | Golden fixtures for Q51-Q54 and GENERAL produce stable criterion totals, evidence, descriptors and labels. Major findings and criterion levels satisfy measurable consistency rules; every displayed score names its profile and maximum. No prompt says a structured requirement is optional in one paragraph and mandatory in another. |
| `P15-PRE-03` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Reading/Listening use one MCQ-shaped response schema (`meaningVi`, one `evidenceQuote`, `correctReasonVi`, `eliminatedOptions`) for all three constructs. Type-specific behavior currently changes mainly through prompt prose. Image evidence is accepted from a free-text `[IMAGE]` prefix without a bound image/region ID. | Introduce a versioned, discriminated explanation contract: option elimination for `SINGLE_CHOICE`; per-blank accepted-value reasoning for `FILL_BLANK`; explicit entailed/contradicted/not-stated relation for `TRUE_FALSE_NOT_GIVEN`. Bind visual evidence to an input image identity/region or mark it as whole-image evidence. Declare whether teacher explanation is authoritative, supporting or absent. | Provider schema, normalizer/read validator and result view reject cross-type shapes. Seed fixtures cover all three constructs, text evidence, visual evidence and no-evidence failure. Existing v2 artifacts have an explicit read/migrate/regenerate decision. |
| `P15-PRE-04` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Writing and Speaking rule engines contain short substring/list rules. Such rules cannot represent the historical and current diversity of Korean and may create lexical false positives (for example matching `랑` inside a larger lexical item). | Reclassify deterministic rules as narrow signals, use token/boundary-aware matching where deterministic detection is retained, attach task/register/evidence scope, deduplicate overlaps and prohibit a rule hit from directly manufacturing a score. The rubric/model evaluates open Korean expression; code validates contracts and high-confidence invariants. | Counterexample tests cover lexical containment, acceptable colloquial/contextual use, spacing, Unicode normalization and repeated signals. Rule-only output cannot reduce an unrelated criterion without evidence. |
| `P15-PRE-05` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Compatibility JSON and database history are being used as a reason to postpone correctness decisions. In fact, Writing cache/result and attempt AI feedback are versioned/flexible JSON, while R/L artifact rebinding has a real relational constraint. | Separate code-contract migration from relational migration. Introduce new-write/dual-read or explicit invalidation for JSON payloads. For R/L schema evolution, implement a reviewed forward-only artifact binding replacement/supersession path or reset only an approved dedicated UAT database. Never silently edit V25/V33/V34/V37/V44 after deployment obligation exists. | A written data decision names `KEEP`, `DUAL_READ`, `MIGRATE`, `REGENERATE` or `DELETE_UAT_ONLY` for every affected payload. Fresh migration and required upgrade rehearsal pass. No correctness blocker is deferred merely with “database còn dữ liệu”. |
| `P15-PRE-06` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Current detail is split between legacy `result-detail.html` and `rl-result-detail.html`. Writing/Speaking detail remains a generic browser-side JSON parser with five AI tabs; its Sample tab reads evaluator `sample_answer`. The bounded Phase-13D guard removed the extra hardcoded Speaking criterion/acoustic tabs and permits chips only from backend-accepted `SCORED` rows, but it is not the three-screen replacement. Lecturer `essaySample`/`speakingSample` exists in the draft editor, while `PracticePublisherService` still does not persist either value into canonical immutable question content/version. | After the bounded `PHASE_13D_UX_CORRECTION` has consolidated green validation and Phase 13E receives a separate explicit GO, deliver exactly three skill-native screen contracts: Objective Reading/Listening Detail (then discriminated by canonical question type), Writing Detail and Speaking Detail. W/S each have exactly four feedback tabs `OVERVIEW`, `STRENGTHS`, `NEEDS_IMPROVEMENT`, `UPGRADED_ANSWER`. A lecturer-provided reference is a separate immutable authoring asset/panel, never an evaluator-generated fifth tab. Shared visual primitives/read-only dispatch are allowed; a generic cross-skill browser JSON parser/template is not. Screens, evidence highlights and PREP-style chip counts may use only backend-validator-accepted typed evidence. | Distinct presenter/DTO/template/test contracts prove exactly one of three screens renders. Typed fields create table cells/highlights, not AI HTML. Rejected/forged evidence cannot create a highlight or chip count. Teacher reference round-trips draft -> publish -> immutable version -> result outside feedback JSON. W/S expose four feedback tabs; R/L fixtures prove per-type explanations; transcript-only Speaking exposes no acoustic claim. |
| `P15-PRE-07` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | `AiCalibrationReadinessPolicy` can pass with five fixtures per skill and broad percentage ranges; teacher review is only a warning. `AiCalibrationFixture` has no task/response/evidence mode, criterion/audio diversity, rater/adjudication/agreement or provider/bundle digest. `SpeakingProviderRolloutReadiness` correctly blocks the current transcript evaluator but has no teacher-reviewed acoustic validity evidence. | Owner: Academic SME + backend/readiness. Calibrate every released Writing/Speaking/R/L-AI policy bundle with a versioned Korean corpus, annotation guide, multiple raters/adjudication, task/criterion/evidence diversity and repeat/provider-drift checks. If PRE-01 branch B supplies a dark direct-audio capability, add representative audio/device/accent/environment coverage before rollout; branch A must not pretend to have acoustic calibration. | Released bundles close as `IMPLEMENTED_AND_ACCEPTED` only when required dimensions/digests and Section 15 agreement/error/fairness/repeatability thresholds are green. Direct-audio branch A closes its acoustic subsection as `NOT_APPLICABLE_WITH_PROOF` while readiness remains blocker-red; transcript/Writing/R/L calibration obligations still apply. |
| `P15-PRE-08` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | `PracticeSpeakingMedia`, `PracticeSpeakingMediaService`, cleanup tasks, transcription resolver and owner playback provide technical lifecycle/access controls, but not an explicit consent/purpose/provider-disclosure contract, provider non-retention proof, withdrawal semantics or approved evaluator retention schedule. The playback controller is student-only/owner-scoped, so reviewer access is also not an authorized product boundary. | Owner: Security/Privacy + Backend, with 13H role/device review. Define the policy before any PRE-01 branch-B audio transfer. If direct-audio stays disabled, prove scorer requests cannot include audio and document the existing recording/transcription/playback consent, purpose, retention and authorization boundary. If enabled, additionally approve evaluator provider/purpose/region, non-training/retention terms, withdrawal semantics, deletion SLA, audit fields and explicit reviewer grants. | Branch A closes as `DISABLED_BY_POLICY_WITH_PROOF` for evaluator transfer while current media lifecycle/access controls still pass. Branch B requires consent/withdrawal and owner/reviewer authorization tests with cross-user/role negatives; audit records provider/model/purpose/evidence digest without locator/audio bytes/secrets; deletion/cleanup and provider non-retention evidence is recorded. |
| `P15-PRE-09` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Writing/Speaking policy identity is split across task resolver/spec assumptions, `WritingScoringPolicy`, rubric/taxonomy, prompt/schema constants, normalizers, cache/reuse keys and calibration fixtures. `WritingEvaluationCacheService` identity includes prompt/rubric/schema/model inputs but not one canonical task-spec + scoring-profile + descriptor + taxonomy + evidence-policy + evidence-validator version + ASR confidence/availability policy + calibration-set bundle. | Owner: Pre-Phase-15 assessment-contract/backend. Introduce one immutable `AssessmentPolicyBundle` identity used by request construction, cache/reuse, provider provenance, normalizer and persisted result. Evidence-validator behavior, ASR threshold/policy and its score-availability semantics are versioned inputs; incompatible old JSON follows P15-PRE-05. | Mutating each identity component, validator version, ASR threshold or availability rule causes cache/reuse miss and prevents stale reinterpretation; persisted/current result proves exact bundle match; source scan finds no independent fallback version that can authorize a score. |
| `P15-PRE-10` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | `WritingEvaluationClient` builds its production `RestClient` without a request factory or explicit connect/read timeout; `OpenAiProperties` has no timeout field, while Speaking and R/L provider paths already expose bounded timeout configuration. | Owner: 13H/provider operational hardening before Phase 15 provider UAT. Add positive bounded configuration, transport timeout handling/metrics and retry semantics that remain fail-closed; never fall back to mock scores. | Source/config scan shows the Writing client applies bounded connect/read timeouts; focused timeout/retry tests return `EVALUATION_UNAVAILABLE` with no fake score; provider/load UAT records bounded latency. |
| `P15-PRE-11` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | `PracticeManageController` loads collaborators per set and users per grant (`findBySetIdAndRevokedAtIsNull`, then `userRepository.findById`), preserving authorization but producing N+1 queries. | Owner: 13H/performance hardening before Phase 15 performance UAT. Add set-ID/user-ID batch reads without weakening owner/collaborator visibility. | Query-count fixture over multiple sets/collaborators is bounded independent of row count, and authorization regression proves no additional set/user visibility. |
| `P15-PRE-12` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | `PracticeSpeakingMediaCleanupTaskRepository.findDueTaskIds` selects due IDs without an atomic claim/lease; optimistic completion limits stale status writes but two nodes may perform duplicate external deletion/work. | Owner: 13H/operational storage hardening before multi-node or R2/Phase 15 operational UAT. Add atomic claim/lease or skip-locked semantics with retry/expiry and keep deletion idempotent. | Concurrent-worker test proves one active claimant per task/lease; retry and expired-claim recovery are deterministic; storage logs show no duplicate non-idempotent operation. |
| `P15-PRE-13` | `FUTURE_PHASE_15_ENTRY_BLOCKER` | Practice PDF AI generation still lacks a durable atomic double-submit/idempotency boundary, and `PracticePdfCropService.cropRegion` renders/crops synchronously in the request path. | Owner: 13F/13H provider/load hardening before Phase 15 provider/load UAT. Add an atomic session transition/idempotency key; move heavy crop work only with explicit transaction/context/error semantics rather than a bare async annotation. | Concurrent-submit fixture produces one generation; crop workload has bounded request latency/resource evidence and durable failure/retry state; no duplicate provider charge or orphan crop is observed. |
| `P15-PRE-14` | `FUTURE_PHASE_15_ENTRY_BLOCKER — ASSESSMENT_LANGUAGE_AND_KOREAN_CONSTRUCT_COVERAGE` | Speaking system instructions and some rubric labels are still English; Writing mixes Vietnamese with internal English terms; provider-owned labels/explanations are not uniformly language-validated. Current W/S diagnostic IDs are broad/flat, and R/L still uses one MCQ-shaped explanation envelope. The prompts mention useful Korean features but do not form a complete task × construct × evidence × descriptor × diagnostic-code × calibration contract. Phase 13D adds a fail-closed Speaking transcript-evidence guard, but the equivalent governed rule must still be uniform across all released skills/contracts. No finite prompt/rule/taxonomy can truthfully cover all historical and contemporary Korean. | Owner: Korean Academic SME + assessment backend, coordinated with PRE-02/03/04/06/07/09. Lock the supported domain as task-bounded modern Korean practice and publish out-of-domain/no-claim behavior. Define a field-language matrix: code-owned human-readable instructions in Vietnamese with Korean examples/evidence/corrections; learner-facing explanations/labels in Vietnamese/Korean; stable machine keys, enum values and criterion IDs remain ASCII and are not translated. Add task-native W/S construct registries and R/L learning/explanation lenses with typed parent mapping, evidence requirements, descriptor/impact policy and `NOT_SCORABLE` rules. For every typed finding, backend must validate the parent criterion/task mapping and evidence authority: `TEXT_SPAN` is a non-empty exact substring of the authoritative answer/transcript with backend-derived bounded offsets; `WHOLE_ANSWER` carries empty evidence and no fake highlight; `TASK_METADATA` requires an authoritative bound metadata object; image/audio evidence requires a bound asset/region/time reference. Backend owns display labels and must reject/repair provider output that would leak unsupported English learner-facing text. | Source/schema snapshot proves no learner-facing English label/instruction leak while stable IDs/keys remain unchanged. Golden and adversarial fixtures cover each supported skill/task/construct/evidence combination, including nonexistent spans, forged/out-of-range offsets, repeated spans, non-empty `WHOLE_ANSWER`, unauthorized `TASK_METADATA`, wrong parent-child mapping, acceptable Korean variation and out-of-domain cases. Persisted/current results and chip counts include only validator-accepted evidence; Korean SME signs the registry/descriptors, PRE-07 calibration records bundle coverage, and product copy explicitly avoids a claim of exhaustive Korean-language coverage. |

#### 4.1.1 Design / workflow / inventory production-decision crosswalk

This table is the current-source bridge between
`KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`, this inventory and the
workflow. `ALIGNED` means the three documents now say the same thing, not that
future implementation or validation has passed. `PARTIAL` means the runtime has
a safe subset but the named pre-Phase-15 proof is still open. `MISSING` means a
replacement capability is intentionally absent and is therefore a `NO-GO`, not
an accepted silent debt.

| Production decision | Alignment after UX-05 | Inventory owner/dependency | Workflow gate/current action | Current source files | Measurable acceptance proof |
| --- | --- | --- | --- | --- | --- |
| Current Speaking is transcript-only; four native language rows may be numeric, acoustic rows are null/`NOT_SCORABLE`, and no subtotal/holistic/attempt score exists. | `ALIGNED` for source contract; validation pending | `P15-PRE-01`; compatibility `P15-COMP-03/04/12/19` | `PHASE_13D_UX_CORRECTION` must reach `READY_FOR_PHASE_VALIDATION`; Phase 13E stays unopened. Historical “six criteria/holistic” ledger rows are superseded, not rewritten. | `SpeakingEvaluationResult`, `SpeakingEvaluationNormalizer`, `SpeakingEvaluationOrchestrator`, `SpeakingScorePolicy`, `SpeakingFeedbackCompatibilityReader`, `SpeakingResultPresenter`, `PracticeDtos`, `PracticeService`, `result/speaking.html`, bounded `result-detail.html` | Current/low-confidence/legacy/reserved/malformed fixtures expose no unsupported acoustic number, `/70`, level or holistic score; only current validated transcript rows contribute coverage. Low-confidence remains current provenance with no profile. |
| Future full Speaking requires authorized direct audio, academic calibration, privacy/retention and rollout readiness. | `MISSING` capability; correctly `NO-GO` | Staged path: PRE-08 policy -> PRE-01 dark capability -> PRE-07 calibration/readiness -> rollout; PRE-12 is operational coordination, not a predecessor cycle | No Phase 15 Manual UAT/full rollout until branch-B blockers are green. Branch A may enter UAT only with full/audio scoring disabled and fail-closed proof; UX-05 does not implement direct audio. | `OpenAiCompatibleSpeakingEvaluationClient`, `SpeakingEvaluatorCapability`, `SpeakingProviderRolloutReadiness`, `AiCalibrationReadinessPolicy`, media/playback/cleanup services | Branch B: captured request proves authorized learner audio reached the scorer; calibration/privacy/readiness pass. Branch A: no request can carry audio and readiness remains blocker-red while transcript-only UI is honest. |
| Writing new scoring remains task-native (`Q51/Q52 /10`, `Q53 /30`, `Q54 /50`, `GENERAL /100`); diagnostics are non-additive; local band 1–9 is compatibility only. | `PARTIAL` | `P15-PRE-02/09`; retire band path in `P15-COMP-18` | Current UX stays KSH-practice-labelled; profile/bundle and cleanup must finish before Phase 15 Manual UAT. | `WritingScoringPolicy`, `WritingScoringRubric`, `WritingEvaluationNormalizer`, `WritingScoreMatrix`, `WritingResultPresenter`, `result/writing.html` | Canonical prompt/provider/new-write/UI scan has no 1–9 inference or band label; task maxima unchanged. `DELETE_UAT_ONLY` needs approved reset proof; retained/migrated history additionally needs a bounded expiring read adapter. |
| R/L explanations are discriminated by canonical question type, and a new artifact version must use an explicit relational supersession/rebind decision. | `PARTIAL` | `P15-PRE-03/05`; `P15-COMP-10/16`; Phase 13E renderer | Phase 13D keeps current read-only artifact lifecycle; Phase 13E owns typed detail; pre-Phase-15 owns v2 disposition/rebind or approved UAT reset. | `ReadingListeningExplanationClient`, `QuestionExplanationReadService`, `QuestionVersionExplanationBindingRepository`, `result-detail` replacement files | Cross-type schemas are rejected; MCQ/fill/TFNG fixtures render their own evidence; exactly one active compatible binding resolves while prior history is auditable or reset-approved. |
| Rule engines are bounded deterministic signals and cannot manufacture score or acoustic evidence. | `ALIGNED` as design boundary; hardening open | `P15-PRE-04`, bundle identity `P15-PRE-09` | Preserve fail-closed UX now; counterexample hardening precedes Phase 15 Manual UAT. | `WritingRuleEngine`, `SpeakingRuleEngine`, prompt builders and normalizers | Boundary/Unicode/context counterexamples pass; duplicate signals collapse; rule-only output cannot alter an unrelated criterion or create audio claims. |
| Assessment field language and Korean construct coverage are explicit, task-bounded and calibrated across W/S/R/L. | `MISSING` unified contract; correctly not claimed complete | `P15-PRE-14`, drawing artifacts from PRE-02/03/04/07/09 and UI proof from PRE-06 | Do not translate stable machine IDs. Before Phase 15, remove learner-facing English leakage and prove the supported modern-Korean task domain without claiming all Korean. | `SpeakingPromptRules`, `SpeakingEvaluationPromptBuilder`, `SpeakingRubricCriterion`, `SpeakingFeedbackViewMapper`, `WritingPromptRules`, `WritingRubricCriterion`, `ReadingListeningExplanationClient`, normalizers and typed detail DTOs | Field-language matrix passes source/schema snapshots; task × construct × evidence × descriptor fixtures and counterexamples have Korean-SME sign-off and calibration coverage; out-of-domain behavior is explicit. |
| Result Detail is exactly three screens (Objective R/L, Writing, Speaking); W/S each have four AI-feedback tabs and lecturer reference is separate. | `MISSING` runtime replacement; design/inventory aligned | `P15-PRE-06`; Phase 13E; cleanup `P15-COMP-10` | Phase 13E remains unopened during UX-05. Legacy detail receives only bounded safety guards until the three-screen replacement is verified. PREP chip organization is IA reference only; KSH policy/validator owns every chip semantic. | `result-detail.html`, `rl-result-detail.html`, `PracticeController`, `PracticePublisherService`, future typed DTO/presenters/templates | Exactly one of three screen kinds renders; W/S have four feedback tabs; lecturer reference round-trips outside AI JSON; old generic parser symbols have no references after replacement; no PREP/IELTS taxonomy or unsupported acoustic chip enters KSH. |
| Prompt/rubric/schema/task/profile/evidence/calibration identity invalidates cache/reuse and prevents stale reinterpretation. | `PARTIAL` | `P15-PRE-09` with JSON decision `P15-PRE-05` | No early Phase 15 cleanup; version bumps remain required for current changes. | `WritingEvaluationCacheService`, `SpeakingEvaluationReusePolicy`, prompt rules/builders, normalizers, persisted evaluation envelopes | Mutating every bundle component misses cache/reuse; stored results require exact identity; old payload follows named dual-read/migrate/delete decision. |
| Database/migration/seed/UAT reset is environment-evidence-driven, never an opportunistic Phase 13D deletion. | `ALIGNED` policy; execution future | `P15-PRE-05`; `P15-COMP-13/15/16/17/18/19`; `P15-KEEP-01/05` | UX-05 performs no DB/migration mutation. Phase 15 seed loads only after pre-gate and compatibility resolutions. | V25/V33/V34/V37/V44, attempt/feedback JSON, explanation bindings, seed manifest | Deployment/retention proof chooses migrate/retain/reset per payload; fresh plus required upgrade rehearsal recorded; deterministic seed contains no compatibility-only scoring fixture presented as current. |
| Historical workflow/gate/design statements remain audit history but cannot override current source truth. | `ALIGNED` after supersession notes | `P15-COMP-14` | Latest workflow action is full UX-correction diff review -> `READY_FOR_PHASE_VALIDATION`; historical ledger rows stay unchanged. | `CODEX_PRACTICE_WORKFLOW.md`, Phase 13 gate/blueprint, design, UX live log | Current instruction sections link UX-05/F06 and this crosswalk; archival rows are dated/superseded and are not used as implementation instructions. |

#### 4.1.2 Pre-Phase-15 owner / staged execution ledger

`Implementation predecessor` is deliberately separated from `closure /
coordination`. The latter may follow the row and therefore must not be read as
a circular prerequisite. This order is executable: define contracts/policy ->
make data/bundle decisions -> implement dark capability/view -> calibrate and
accept rollout -> remove compatibility paths.

| ID | Accountable owner | Implementation predecessor | Closure / coordinated item | Current execution status |
| --- | --- | --- | --- | --- |
| `P15-PRE-01` | Backend + Academic SME | Branch A disabled: none. Branch B direct audio: PRE-08 policy approval before any evaluator transfer | Branch B then feeds PRE-07 calibration/readiness; COMP-19 retained rows are a cleanup decision, not a predecessor | Transcript guard `IMPLEMENTED_PENDING_PHASE_VALIDATION`; full Speaking `NO-GO` |
| `P15-PRE-02` | Academic SME + Writing backend | None; first choose the scored construct/profile/descriptors | Feeds PRE-05/09; coordinate removal/data disposition in COMP-18 afterward | `NOT_STARTED — PHASE_15_ENTRY_BLOCKER` |
| `P15-PRE-03` | R/L explanation backend + Phase 13E | None; first define the discriminated current/future schema | Feeds PRE-05/06; COMP-16 follows the data decision | `NOT_STARTED — PHASE_15_ENTRY_BLOCKER` |
| `P15-PRE-04` | Academic SME + rule-engine backend | None; define bounded-signal policy and counterexamples | Feeds PRE-09 bundle identity | `NOT_STARTED — PHASE_15_ENTRY_BLOCKER` |
| `P15-PRE-05` | Database/compatibility owner | PRE-02/03 contract decisions plus environment/deployment evidence | Feeds PRE-09 and resolves COMP-13/15/16/17/18/19 dispositions | `NOT_STARTED — NO DESTRUCTIVE PHASE 13D ACTION` |
| `P15-PRE-06` | Phase 13E result-detail owner | PRE-03 typed R/L contract and PRE-09 bundle identity | COMP-10 legacy detail removal follows verified replacement | `NOT_STARTED — PHASE 13E UNOPENED` |
| `P15-PRE-07` | Academic SME + backend/readiness | Each released bundle/capability must exist in dark/shadow form; branch-B acoustic calibration follows PRE-01 dark capability | Rollout acceptance; branch-A acoustic subsection may close `NOT_APPLICABLE_WITH_PROOF` | `NOT_STARTED — FULL SPEAKING NO-GO` |
| `P15-PRE-08` | Security/Privacy + Backend | None; policy is authored before PRE-01 branch-B transfer | PRE-12 cleanup is coordinated operational evidence, not a circular predecessor | `NOT_STARTED — DIRECT AUDIO NO-GO` |
| `P15-PRE-09` | Assessment-contract backend | PRE-02/03/04 contract artifacts and PRE-05 data decision | Feeds PRE-06 and all cache/reuse/result identity checks | `NOT_STARTED — PHASE_15_ENTRY_BLOCKER` |
| `P15-PRE-10` | 13H/provider operations | None | Writing provider UAT closure | `NOT_STARTED — PROVIDER UAT BLOCKER` |
| `P15-PRE-11` | 13H/performance | None | Performance UAT closure | `NOT_STARTED — PERFORMANCE UAT BLOCKER` |
| `P15-PRE-12` | 13H/storage operations | None | Supplies cleanup evidence to PRE-08/operational UAT | `NOT_STARTED — MULTI-NODE/R2 UAT BLOCKER` |
| `P15-PRE-13` | 13F/13H PDF/provider operations | None | Provider/load UAT closure | `NOT_STARTED — PROVIDER/LOAD UAT BLOCKER` |
| `P15-PRE-14` | Korean Academic SME + assessment backend | PRE-02/03/04 define skill/type contracts; PRE-09 provides bundle identity | PRE-06 UI language proof and PRE-07 calibrated coverage close the row | `NOT_STARTED — CROSS-SKILL LANGUAGE/CONSTRUCT BLOCKER` |

### 4.2 Future Speaking evidence and score target locked at design level

This subsection locks the future evidence boundary for the Pre-Phase-15 gate.
The Phase 13D UX correction has already implemented the safer current subset,
pending validation: transcript-only evidence produces a four-criterion
diagnostic language profile with **no aggregate/subtotal/holistic or attempt
score**. The `earned/possible/fullPossible` design below is not current runtime
authorization; adopting any aggregate later requires an explicit policy-bundle
decision under P15-PRE-09 and must still never be presented as full Speaking.

| Evidence available to the scoring component | Score-bearing capability | Required result state |
| --- | --- | --- |
| Transcript/prompt/task metadata only | Four native criterion rows: task achievement/content, grammar/sentence control, vocabulary/expression and transcript-grounded coherence | Current: transcript-grounded diagnostic profile, per-row score/max only, no `/70`, aggregate, proficiency band or holistic/attempt score. Any future partial aggregate needs an explicit PRE-09 policy and separate acceptance. |
| Authorized learner audio plus transcript, with a provider path that actually consumes the audio | The above plus only the calibrated audio-derived criteria supported by that provider | Full or partial according to actual active criteria; provenance identifies the audio-scoring provider/model/version |
| Missing/unreadable audio and no valid fallback text | None | `NOT_SCORABLE`; never zero, midpoint or fabricated advisory score |

The current `50%` Pronunciation cap is not an acceptable substitute for
`NOT_SCORABLE`. A numeric score is a measurement claim even when the UI adds the
word “tham khảo”. Transcript confidence is confidence in the transcription,
not pronunciation accuracy.

### 4.3 Current files expected to change before Phase 15

This is the current production change set, based on source review on
`2026-07-20`. It is intentionally separated from the compatibility-removal set
in Section 6. A later implementation audit may split a class, but it may not
silently drop the responsibility listed here.

> UX-05 current-source overlay (`2026-07-22`): the transcript-only portions of
> the Speaking rows below are now `IMPLEMENTED_PENDING_PHASE_VALIDATION`; their
> older “must change” descriptions remain audit context. Remaining work is the
> authorized direct-audio capability and PRE-07/08/09 evidence. Nothing in this
> historical change map authorizes a current subtotal or holistic score.

#### A. Speaking correctness — mandatory production change set

| Current file | Why it must change or be explicitly replaced |
| --- | --- |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationOrchestrator.java` | Preserve the UX-03 authoritative `transcriptConfidence`/capability handoff; a future direct-audio branch must use a separately authorized input contract rather than reinterpreting this transcript request. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationRequest.java` | Carry explicit evidence mode/available criteria and, only for a supported audio evaluator, a governed audio input descriptor. |
| `src/main/java/com/ksh/features/practice/ai/speaking/OpenAiCompatibleSpeakingEvaluationClient.java` | Enforce the client capability honestly: text/image requests cannot claim learner-audio evaluation; add an actual authorized audio part/reference only if the selected provider contract supports it. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilder.java` | Preserve the current four-row transcript schema and capability prohibitions; under PRE-09/14 derive future active criteria, construct registry and localized human instructions from the approved bundle. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingPromptRules.java` | Preserve current `NOT_SCORABLE`/no-acoustic-claim rules; localize code-owned human instructions and align task-native construct/descriptors under PRE-14 without translating stable IDs. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingRubricCriterion.java` | Preserve typed evidence requirements/capability added by UX-03; move learner display labels and construct/descriptor ownership into the approved bundle. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationNormalizer.java` | Preserve exact active-row/evidence filtering and null acoustic status; validate policy identity and do not invent an aggregate total for transcript-only evidence. Enforce exact authoritative `TEXT_SPAN` substrings with backend-derived bounded offsets, empty/no-highlight `WHOLE_ANSWER`, authorized `TASK_METADATA`, source authority and criterion/subcriterion parent mapping; rejected evidence cannot reach findings, highlights or counts. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationResult.java` | Preserve current per-criterion availability/provenance and coverage. Add aggregate `earned`/`possible`/`fullPossible` or partial/full denominator only if a later approved direct-audio scoring policy defines it; current transcript-only has four independent score/max rows, two null `NOT_SCORABLE` rows and no subtotal/holistic/attempt score. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationStatus.java` | Distinguish partial evidence from full score-bearing evaluation and keep unavailable states non-score-bearing. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingScorePolicy.java` | Preserve current null attempt score for transcript-only capability. Define eligible criteria/denominators only if a later explicitly approved policy introduces a bounded aggregate; never include unavailable audio as zero. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationService.java` | Preserve the new evidence/coverage fields through reuse and persistence; reject stale results with a mismatched contract. |
| `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingRuleEngine.java` | Make transcript signals boundary-aware/advisory and prevent filler/register heuristics from becoming acoustic evidence. |
| `src/main/java/com/ksh/features/practice/service/PracticeService.java` | Persist the new Speaking envelope. Current transcript-only must keep attempt score null and must exclude retained legacy/text-simulated numbers from progress/best/average consumers; calculate an attempt score only after an explicitly approved calibrated direct-audio policy exists. |
| `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java` | Preserve UX-04/05 exclusion of unavailable/legacy criteria and the no-holistic transcript profile; later consume only the approved bundle/direct-audio contract. |
| `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java` | Preserve criterion availability, capability/evidence/trust and coverage; add only the screen-specific Phase 13E/future bundle fields that have an owner. |
| `src/main/resources/templates/practice/result/speaking.html` | Preserve explicit `NOT_SCORABLE`, no acoustic number and no transcript subtotal; future full display remains gated by a non-reserved authorized capability. |
| `src/main/java/com/ksh/features/practice/ai/readiness/SpeakingProviderRolloutReadiness.java` | Add a blocker when a configuration claims full Speaking scoring without an audio-consuming evaluator capability. |
| `src/main/java/com/ksh/features/practice/ai/readiness/AiRolloutReadinessChecklist.java` | Manual UAT completion may not waive evidence validity, calibration or the audio-capability blocker. |
| `src/main/resources/application.properties` | Separate transcription, transcript-language evaluation and audio-grounded evaluation capabilities/gates; keep unsafe/full rollout disabled by default. |

`OpenAiSpeakingTranscriptionClient.java` remains the STT boundary and does not
need to become a pronunciation grader. It must be regression-tested, and it is
changed only if the transcription/confidence provider contract itself changes.

#### B. Writing correctness — mandatory production change set

| Current file | Why it must change or be explicitly replaced |
| --- | --- |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringPolicy.java` | Name/version the chosen score profile and centralize task weights, deterministic length/format policy and percentage semantics. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringCriterion.java` | Carry stable profile/criterion metadata needed by prompt, normalizer and result UI. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringRubric.java` | Represent the selected task-native rubric/profile as the single source of truth. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingPromptRules.java` | Remove duplicated/contradictory policy prose, compose task-specific modules deliberately and bump prompt/rubric/schema/contract versions. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingRubricCriterion.java` | Align finding taxonomy with authoritative metadata availability and the selected task profile; do not advertise provider-active evidence that normalization always discards. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingRuleEngine.java` | Replace unsafe substring hits and misleading warning-only deductions with boundary-aware signals plus a deterministic policy output. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingTaskResolver.java` | Prefer explicit immutable task metadata and constrain heuristic prompt detection to a named fallback. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationClient.java` | Send the resolved policy/task metadata, build the matching strict schema and keep prompt/payload/schema versions atomic. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationNormalizer.java` | Validate task/profile/evidence, safely support authoritative task metadata where enabled, and compute rather than trust all derived scores. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoreMatrix.java` | Retire local 1–9 conversion/labels under P15-COMP-18; if retained history proves a need, isolate only the required conversion in an expiring read-only compatibility adapter. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationResult.java` | Expose scoring profile, evidence coverage and availability needed by persistence/presentation. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingMockEvaluatorService.java` | Mirror the production contract; a mock must not preserve an obsolete rubric shape. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationCacheService.java` | Verify that the new profile/prompt/rubric/schema identity invalidates old cached results; no blanket table deletion is required. |
| `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java` | Display the actual profile/maxima and keep legacy results explicitly labelled rather than reinterpreting them as the new contract. |
| `src/main/java/com/ksh/features/practice/service/PracticeService.java` | Persist/aggregate the new Writing contract without the `score <= 9` ambiguity leaking into new writes. |
| `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java` | Carry the score profile, criterion maxima and availability into the canonical view. |
| `src/main/resources/templates/practice/result/writing.html` | Show internal-versus-aligned profile wording and task-native denominator without implying official equivalence. |

#### C. Reading/Listening explanation correctness — mandatory production change set

| Current file | Why it must change or be explicitly replaced |
| --- | --- |
| `src/main/java/com/ksh/features/practice/ai/readinglistening/ExplanationArtifactInput.java` | Version the construct-native input and record teacher-explanation authority and evidence references explicitly. |
| `src/main/java/com/ksh/features/practice/ai/readinglistening/ExplanationInputFactory.java` | Build the right construct/evidence payload from immutable published content; fail closed when required evidence is absent. |
| `src/main/java/com/ksh/features/practice/ai/readinglistening/ReadingListeningExplanationClient.java` | Replace the universal MCQ-shaped schema with a discriminated per-type schema and validate stable text/image evidence identity. |
| `src/main/java/com/ksh/features/practice/ai/readinglistening/ExplanationFingerprintBuilder.java` | Include the new input/prompt/response schema identity so incompatible v2 artifacts are never reused as v3. |
| `src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationPreparationService.java` | Detect a bound artifact whose fingerprint/schema is stale and route it through the approved regenerate/rebind policy instead of automatically reusing READY. |
| `src/main/java/com/ksh/features/practice/ai/readinglistening/QuestionExplanationReadService.java` | Validate and map the discriminated contract by question type; reject v2-as-v3 and preserve stable option/blank IDs. |
| `src/main/java/com/ksh/features/practice/service/PracticeService.java` | Stop flattening every explanation into option-elimination fields when assembling detail/result data. |
| `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java` | Replace the old common explanation row with type-safe display payloads or a discriminated view contract. |
| Canonical result-detail template/fragment selected by the completed Phase 13E boundary | Render per-option, per-blank and TFNG relation evidence without parallel legacy parsing. If `src/main/resources/templates/practice/rl-result-detail.html` is still active at implementation time, it must be migrated or removed with `P15-COMP-10`, not left as a second parser. |

#### D. Skill-native result detail and lecturer reference — future ownership map

This table records current files, not a command to edit them during Phase 13E
preparation. Phase 13E owns its separately approved F10-F14 scope. At the future
gate, a file already replaced by 13E is checked through its canonical successor.
The replacement has exactly three presenter/DTO/template/test contracts:
Objective Reading/Listening, Writing and Speaking. One read-only dispatcher and
shared visual primitives are permitted; one generic cross-skill parser is not.

| Current file | Future responsibility or replacement requirement |
| --- | --- |
| `src/main/java/com/ksh/features/practice/controller/PracticeController.java` | Stop assembling detail through two legacy service DTOs and serialized browser blobs once the canonical detail contract exists. Keep result GET read-only and select exactly one skill-native presenter. |
| `src/main/java/com/ksh/features/practice/result/PracticeResultAssembler.java` and a canonical detail assembler/dispatcher boundary | Reuse immutable attempt identity, score/evidence state and dispatch to exactly one Objective-R/L, Writing or Speaking detail presenter. Detail must not become another parallel score calculator. |
| `src/main/java/com/ksh/features/practice/result/ObjectiveResultPresenter.java` | Supply question/group identity and explanation availability needed by the R/L detail navigator without flattening type-specific explanation data. |
| `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java` | Supply task profile, exact learner answer, rubric rows, typed findings, upgraded answer and lecturer reference identity for the four-tab Writing detail. |
| `src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java` | Supply evidence mode, playable learner media references, transcript/timestamp evidence when available, criterion availability, typed findings and upgraded response. Do not synthesize phoneme/stress/acoustic rows from transcript. |
| `src/main/java/com/ksh/features/practice/dto/PracticeDtos.java` | Add one discriminated detail envelope with exactly three screen kinds and screen-specific payloads. Table rows, option rationales, blank reasoning, TFNG relations and tab panels come from typed fields, not raw provider HTML. |
| `src/main/java/com/ksh/features/practice/service/PracticeService.java` | Retire the old `PracticeResultView`/`ReadingListeningResultView` detail assembly after the canonical replacement; preserve only approved historical dual-read boundaries. |
| `src/main/java/com/ksh/features/practice/assessment/QuestionContent.java` or a dedicated immutable lecturer-reference contract | Represent lecturer reference text/audio with schema, language, provenance and optional asset identity. Do not mix it with answer spec or AI evaluation output. |
| `src/main/java/com/ksh/entities/PracticeQuestion.java` and `src/main/java/com/ksh/entities/PracticeQuestionVersion.java` | Persist and snapshot the lecturer reference if the selected contract uses entity fields/JSON. Live edits must not change the reference shown for an old attempt. |
| `src/main/resources/templates/practice/manage/editor.html` | Keep the existing Writing/Speaking reference inputs but label them as lecturer-provided, validate language/media type and bind them to the canonical authoring contract. |
| `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftContractService.java`, `src/main/java/com/ksh/features/practice/manage/validator/PracticeDraftValidator.java` and `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java` | Preserve, validate and publish `essaySample`/`speakingSample` into the immutable contract. Current publisher only notices these keys when classifying edit type; it drops them when creating `PracticeQuestion`. |
| A new forward Flyway migration after the current deployed chain, only if relational storage is selected | Add the immutable reference/binding storage. Never rewrite V25/V33/V34/V37/V44 for this feature. A versioned JSON field may avoid a new column, but still needs explicit dual-read and snapshot behavior. |
| `src/main/resources/templates/practice/result-detail.html` | Retire the generic five-plus-criterion-tab cross-skill parser after replacement. Do not evolve it into the new canonical screen. |
| `src/main/resources/templates/practice/rl-result-detail.html` | Retire the legacy universal option-shaped screen after Objective Detail replaces it. |
| Three Phase 13E screen templates/fragments selected by the implementation | Objective Detail renders discriminated `SINGLE_CHOICE`, `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN`; Writing Detail and Speaking Detail each expose exactly four feedback tabs. Lecturer reference is a separate panel. No screen imports another skill's placeholder/semantics. |
| `src/main/resources/static/css/practice-result-detail.css` and `src/main/resources/static/css/practice-rl-result.css` | Support responsive split panes, sticky question/group navigation, accessible tabs/tables, exact evidence highlighting and narrow-screen stacking without encoding assessment logic in CSS. |
| `src/main/resources/static/js/practice-result.js` plus the canonical detail script selected by Phase 13E | Limit JavaScript to navigation, tabs, playback, synchronized highlight/scroll and accessibility state. Provider JSON validation and score semantics stay on the server. |

Authoring/import parity also requires a decision for Excel/PDF paths. If the
lecturer reference is supported there, the corresponding codec/preview/import
tests must round-trip the same immutable field; if it is intentionally manual-
editor-only, that limitation must be explicit rather than silently discarding
the value.

#### E. Assessment language and Korean construct coverage — PRE-14 ownership map

Localization is field-specific, not a blind search-and-replace:

| Boundary | Required future handling |
| --- | --- |
| `SpeakingPromptRules`, `SpeakingEvaluationPromptBuilder`, `SpeakingRubricCriterion` | Rewrite code-owned human-readable scoring instructions/descriptors for Vietnamese review, retain Korean examples and stable `S_*`/schema IDs, and stop exposing English enum labels as learner labels. |
| `SpeakingEvaluationNormalizer`, `SpeakingFeedbackViewMapper`, Speaking detail DTO/view | Validate or replace provider-owned `display_name`, level and explanation fields so unsupported English text cannot leak to learners; backend-owned Vietnamese/Korean labels remain authoritative. Preserve the Phase 13D fail-closed transcript-evidence validator: exact authoritative substring, backend-derived bounded offsets, empty `WHOLE_ANSWER`, no unauthorized `TASK_METADATA`, valid criterion parent and no chip/count from rejected evidence. |
| `WritingPromptRules`, `WritingRubricCriterion`, `WritingEvaluationNormalizer` | Normalize human-readable instruction/descriptor language, keep stable `W_*` keys, and implement the approved typed construct/impact hierarchy rather than adding an unbounded blacklist. |
| `ReadingListeningExplanationClient`, its normalizer/read mapper and typed detail views | Add type-native explanation plus optional typed Korean learning points anchored to evidence. Do not force every comprehension item into a grammar lesson. |
| `AssessmentPolicyBundle`, calibration fixtures and readiness | Version the field-language matrix and construct registry; prove task/evidence coverage, acceptable Korean variation, counterexamples and explicit out-of-domain behavior with Korean-SME sign-off. |

Minimum construct inventory for supported modern-Korean tasks includes, where
the task and evidence make it observable: particles/case/topic marking,
predicate/argument and valency control, tense/aspect, negation, modality,
sentence endings and speech level, honorifics, modifiers/adnominal and embedded
clauses, quotation/nominalization, passive/causative voice, agreement/호응,
lexical meaning/collocation/idiomaticity, reference/ellipsis, discourse
organization/cohesion, register/pragmatics, and Writing-only orthography/
spacing. The list defines audit dimensions, not automatic penalties. Speaking
transcripts cannot support spelling/spacing or acoustic conclusions, and any
unobservable dimension must be `NOT_SCORABLE` rather than guessed.

### 4.4 Compatibility/database files: change only after the data decision

These files are not permission to postpone Sections 4.1-4.3. They are the
bounded compatibility layer that may need dual-read, a forward migration or
reviewed removal after the environment inventory:

| Boundary | Current files | Required handling |
| --- | --- | --- |
| Historical Speaking/Writing AI JSON | `SpeakingFeedbackCompatibilityReader.java`, `SpeakingEvaluationReusePolicy.java`, `WritingFeedbackCompatibilityReader.java`, `PracticeAttempt.java` | Prefer new-write plus bounded dual-read. Record an expiry/removal decision. The `ai_feedback_json` JSON column does not by itself require a relational migration for the new contract. |
| Writing cache JSON | `WritingEvaluationCacheEntry.java`, `WritingEvaluationCacheService.java`, `V26__writing_evaluation_cache.sql` | Bump identity versions and ignore old entries. Do not edit V26 merely to change result JSON shape. |
| R/L artifact schema/rebinding | `QuestionExplanationArtifact.java`, `QuestionVersionExplanationBinding.java`, `QuestionExplanationArtifactRepository.java`, `QuestionVersionExplanationBindingRepository.java`, `QuestionExplanationPreparationService.java`, `QuestionExplanationReadService.java` | The JSON column can store a new shape, but the unique `(question_version_id, explanation_language)` binding plus `bindIfAbsent` cannot supersede a v2 artifact. Choose a forward-only rebind/supersession migration or a proven UAT-only reset. |
| Historical Practice schema and seed | `V25__practice_hub.sql`, `V33__practice_immutable_versions.sql`, `V34__practice_single_scope_final.sql`, `V37__question_explanation_artifact_lifecycle.sql`, `V44__practice_seed_listening_check_audio.sql` | Do not rewrite after deployment obligation exists. V44 is development seed repair and must be classified for production baseline versus UAT-only fixture handling. Any relational change uses the next available forward migration unless a baseline squash is separately proven safe. |

### 4.5 Mandatory verification files and fixtures

At minimum, implementation updates the focused tests whose contracts change:

- Speaking: `SpeakingEvaluationOrchestratorTest.java`,
  `OpenAiCompatibleSpeakingEvaluationClientTest.java`,
  `SpeakingEvaluationPromptBuilderTest.java`, `SpeakingPromptRulesTest.java`,
  `SpeakingEvaluationNormalizerTest.java`, `SpeakingScorePolicyTest.java`,
  `SpeakingEvaluationApplicationServiceTest.java`,
  `SpeakingResultRenderingContractTest.java`,
  `SpeakingProviderRolloutReadinessTest.java` and
  `AiRolloutReadinessChecklistTest.java`;
- Writing: `WritingScoreMatrixTest.java`, `WritingScoringPolicyTest.java`,
  `WritingTaskNativeScoringTest.java`,
  `WritingPromptRulesTest.java`, `WritingRuleEngineTest.java`,
  `WritingEvaluationClientTest.java`, `WritingEvaluationNormalizerTest.java`,
  `WritingEvaluationCacheServiceTest.java` and the Writing result/persistence
  contract tests;
- Reading/Listening: `ReadingListeningExplanationClientTest.java`,
  `ReadingListeningTypedClientContractTest.java`,
  `ExplanationFingerprintBuilderTest.java`,
  `QuestionExplanationPreparationServiceTest.java`,
  `QuestionExplanationReadServiceTest.java`, lifecycle/reconciliation tests and
  canonical result-detail rendering tests;
- shared persistence/integration: focused `PracticeServiceTest.java`, result
  presenter tests, a fresh-schema migration test and the representative upgrade
  test required by the selected data decision.

Teacher-reviewed calibration fixtures must include both acceptable variation
and adversarial counterexamples. A few Java string rules or a few thousand
lines of prompt code are not a claim to enumerate Korean. Production coverage
comes from construct coverage, evidence contracts, calibrated examples,
counterexamples, monitoring and a controlled abstention path.

### 4.6 PREP-derived result-detail information architecture, adapted for KSH

The local screenshot folders supplied on `2026-07-20` were reviewed as product
research:

- `5.1.result detail của listening`;
- `5.2.result detail của reading`;
- `5.3 result detail của writing`;
- `5.4 result detail của speaking`.

They are interaction/information-architecture references only. KSH must not
copy PREP branding, assets, IELTS band descriptors, criterion names or score
claims. The transferable ideas are split evidence panes, question navigation,
typed explanation tables, exact answer highlighting, audio replay and a clear
separation among score, diagnosis, upgrade and teacher reference.

#### A. Common detail shell

| Area | KSH contract |
| --- | --- |
| Source/evidence pane | Show immutable prompt, passage/transcript/chart/image or learner answer on the left. Long source and right-side analysis scroll independently; source can remain visible/pinned while a learner reviews a finding. |
| Review pane | Render server-validated typed DTO fields. AI returns JSON facts/relationships; Thymeleaf/controlled JavaScript lays them into cards or table cells. Never allow AI-returned HTML to define the table. |
| Navigator | Preserve set/test/skill/group/question identity, previous/next and direct question/group selection. Changing selection also changes the source, learner answer and explanation as one atomic view state. |
| Evidence linking | A right-side item carries a stable `evidenceRefId`; selecting it highlights an exact text/transcript span, image region or audio time range in the left pane. A superscript number without a validated reference is not enough. |
| Responsive/accessibility | Desktop may use two panes. Narrow screens stack source then review, keep tab/table semantics keyboard-accessible and never require color alone to distinguish correct/incorrect/needs-improvement. |

#### B. Reading/Listening renderer by canonical question type

| Type | Required structured explanation | Recommended rendering |
| --- | --- | --- |
| `SINGLE_CHOICE` | Correct option ID from backend; learner option; one rationale per visible option; evidence refs; optional relevant translation | Show correct/learner choice first, then a two-column evidence/translation table and explicit option elimination. Explain why the correct option is supported and why each distractor is wrong; do not merely say “other options are incorrect”. |
| `FILL_BLANK` | One row per stable `blankId`; learner value; backend-owned accepted values; semantic/grammar/register constraints; reconstructed Korean context; evidence refs | Table by blank. Show struck/incorrect learner value and accepted value, the nearby Korean context, Vietnamese explanation/translation and a syntax hint such as the required part of speech. A provider suggestion never mutates accepted values. |
| `TRUE_FALSE_NOT_GIVEN` | Claim; backend answer; `ENTAILED`, `CONTRADICTED` or `NOT_STATED`; supporting/contrasting evidence; missing-information statement for `NOT_STATED` | Table the statement against the relevant source region and translation. For FALSE, show the contradiction. For NOT GIVEN, state which necessary fact is absent. Explain why the other two labels do not apply. |

For a long Reading passage, default translation is the relevant evidence region
plus enough context to understand the answer. Full-passage translation is a
separately governed teacher/learning artifact or an explicit on-demand view;
it must not be fabricated merely to fill the screen. For Listening, retain the
audio player and, when authoritative timestamps exist, synchronize transcript
speaker blocks and evidence segments. Transcript text alone does not prove an
acoustic feature.

Image evidence uses `assetId/digest + imageIndex + region` or an explicit
`WHOLE_IMAGE` scope. A free-text `[IMAGE]` prefix cannot populate a factual
table cell without a bound source.

#### C. Writing and Speaking: exactly four feedback tabs

| Tab | Writing | Speaking |
| --- | --- | --- |
| `OVERVIEW` | Task/profile name, earned/max, task-native KSH score rows from a named versioned policy, requirement/data-coverage checklist and evidence limitations | Evidence mode, four transcript score/max rows, task/response coverage and audio availability. Current transcript-only has no partial/full aggregate denominator; fluency/pronunciation are null `NOT_SCORABLE`. A full denominator is future direct-audio-policy-only. |
| `STRENGTHS` | Exact learner spans grouped by diagnostic lenses such as task coverage, organization, grammar, vocabulary/register and spelling/spacing; every lens maps to a score criterion and declares `countedSeparately=false` when diagnostic-only | Transcript/timestamp/audio-backed strengths grouped under active Korean Speaking criteria. No “good pronunciation” row without acoustic evidence. |
| `NEEDS_IMPROVEMENT` | Exact evidence, error/quality category, Vietnamese reason, Korean correction, severity/impact and criterion mapping | Exact transcript or audio-time evidence, Vietnamese reason, Korean correction/practice action and criterion mapping. Acoustic diagnosis requires audio-derived evidence/provenance. |
| `UPGRADED_ANSWER` | A Korean revision derived strictly from the learner's submitted ideas/data; annotated diff and sentence rewrites; not an independent model essay | An upgraded Korean response/transcript that preserves learner intent and task facts; optionally an approved synthesized reference audio, clearly labelled and never treated as what the learner actually said. |

The PREP screenshots expose a fifth `Sample` tab, but KSH intentionally does
not copy that contract. `LECTURER_REFERENCE` is a separate read-only authoring
asset shown outside the four AI-feedback tabs. It is optional, immutable per
published question version, clearly labelled “Giảng viên cung cấp”, and never
used to retroactively score the learner. `upgraded_answer` is derived from the
learner submission; lecturer reference is independent. Evaluator-generated
`sample_answer` is not allowed to masquerade as lecturer content.

Speaking may borrow PREP's useful playback concepts only after evidence exists:
whole-response audio, selected time ranges, learner/reference comparison and
word/phoneme/stress tables. Such rows require an audio-consuming scoring path,
alignment provenance and Korean-specific calibration. STT text, spelling or
ASR confidence may not manufacture those rows.

PREP-style category chips are adopted only as an information-navigation
pattern. A KSH chip is a server-validated filter/anchor over typed evidence,
uses a Vietnamese/Korean label, order, parent and applicability from the named
versioned task-native Korean construct registry, and may show only an exact
normalized-finding count. Any score-bearing label, denominator or descriptor
likewise comes from the named versioned KSH policy. A chip is never an IELTS
criterion, band, hidden score, browser-derived count or copied PREP taxonomy.
Writing chips map to KSH task/content, discourse, morphosyntax, vocabulary/
register and spelling/spacing parents as applicable. Speaking transcript chips
map only to transcript-grounded constructs; acoustic chips remain absent or
`NOT_SCORABLE` until the authorized direct-audio/calibration gate is green.

#### D. Minimal discriminated view envelope

```json
{
  "schemaVersion": "result-detail-vNext",
  "skill": "READING|LISTENING|WRITING|SPEAKING",
  "questionType": "SINGLE_CHOICE|FILL_BLANK|TRUE_FALSE_NOT_GIVEN|ESSAY|SPEAKING",
  "source": { "immutableVersionId": 0, "evidenceRefs": [] },
  "learnerSubmission": {},
  "scoreView": { "profileId": "...", "evidenceMode": "...", "coverage": null },
  "feedbackTabs": ["OVERVIEW", "STRENGTHS", "NEEDS_IMPROVEMENT", "UPGRADED_ANSWER"],
  "explanation": { "kind": "TYPE_SPECIFIC" },
  "lecturerReference": { "source": "AUTHOR", "immutable": true }
}
```

The actual per-skill payload must be a discriminated Java/JSON contract, not a
map in which every field is nullable. R/L need no four-tab fiction; they render
answer/explanation structures by type. W/S use the four tabs, while lecturer
reference remains a sibling panel.

### 4.7 Future Pre-Phase-15 exit checklist

Phase 15 entry is `NO-GO` until all of the following are true:

- `P15-PRE-01..14` each have an owner, an allowed closure status, focused test
  result and explicit acceptance evidence. A code diff is required only when
  the row is not already satisfied or deliberately disabled with proof;
- transcript-only Speaking cannot produce numeric Fluency or
  Pronunciation/Delivery scores anywhere in provider JSON normalization,
  persistence, aggregate score, DTO or UI;
- Writing score profile, maxima, task metadata authority and deterministic
  length policy are named and consistent across prompt, backend and view;
- all three R/L objective constructs have different validated explanation
  payloads and result rendering;
- W/S expose exactly four feedback tabs and the separately labelled lecturer
  reference round-trips through immutable publication without entering AI
  evaluation JSON or learner score;
- new prompt/rubric/schema/profile versions invalidate incompatible caches and
  stale artifacts;
- code-owned human-readable prompt instructions are reviewable in Vietnamese,
  stable machine IDs remain untranslated, learner-facing output cannot leak
  unsupported English, and the task-bounded Korean construct/evidence matrix
  has SME/calibration evidence without claiming exhaustive language coverage;
- current JSON payloads have new-write/dual-read decisions and relational
  changes have a forward migration/reset decision backed by environment proof;
- teacher-reviewed golden/counterexample fixtures and inter-rater tolerance are
  recorded; and
- the full Speaking feature remains labelled `NO-GO` unless real authorized
  audio reaches the scoring component and its acoustic criteria are calibrated.

## 5. Classification

| Status | Meaning |
| --- | --- |
| `REMOVE_CANDIDATE_CONFIRMED` | Static evidence shows the path is unused or contradicts the canonical production contract. Phase 15 still performs impact review before removal. |
| `REVIEW_REQUIRED` | The path is active or may protect historical data. Remove only after its replacement/data decision is complete. |
| `KEEP` | The behavior is intentional architecture or security and must survive cleanup. |

## 6. Compatibility and legacy inventory

| ID | Status | Current evidence | Phase 15 action | Acceptance proof |
| --- | --- | --- | --- | --- |
| `P15-COMP-01` | `REVIEW_REQUIRED` | `WritingResultPresenter` still locally scores historical non-ESSAY Writing questions from immutable content/answer specs. `PracticeResultPresenterTest.historicalWritingFillBlankUsesLockedAnswerSpecWithoutAiFeedback` pins that behavior in tests. | After confirming no retained attempt requires it, remove the historical non-ESSAY Writing branch and its fixture. Keep immutable ESSAY history. | Every Writing version in the fresh database is Q51-Q54 `ESSAY`; no local objective scoring branch remains in the Writing presenter. |
| `P15-COMP-02` | `REVIEW_REQUIRED` | `PracticeService.NonWritingEssayGradingSnapshot` lets non-Writing ESSAY content enter Writing evaluation on submit and re-evaluation. | Remove the snapshot/load/verify/grade path after Speaking data is canonical and no other skill is allowed to publish ESSAY. | Non-Writing submission has no call path to the Writing evaluator; a contract test rejects non-Writing ESSAY authoring/publication. |
| `P15-COMP-03` | `REVIEW_REQUIRED` | `PracticeService` and `SpeakingResultPresenter` retain `speaking_mixed_v1` and `essay_feedback_by_question`. UX-04/05 now keep ESSAY copy legacy/unverified and exclude it from current coverage/readiness/trust, but the mixed compatibility envelope still exists. | Remove the mixed feedback contract and ESSAY merge after P15-COMP-02 and retained-attempt review. | Speaking persistence/result contracts contain only Speaking feedback; no `essay_feedback_by_question` reference remains; retained history has migration/reset proof. |
| `P15-COMP-04` | `REVIEW_REQUIRED` | `SpeakingFeedbackCompatibilityReader`, `SpeakingEvaluationReusePolicy`, `SpeakingEvaluationStatus.LEGACY_RESULT` and `WritingFeedbackCompatibilityReader` retain legacy payload/status branches. | Build a current-payload inventory, migrate or discard test rows, then remove only statuses/readers no longer needed by the current provider contract. | Current provider fixtures still parse; removed legacy statuses have no source/test/schema references. |
| `P15-COMP-05` | `REVIEW_REQUIRED` | `PracticeService.loadProgressAttemptData`, `progressQuestions`, `versionSnapshot` and related result/player paths retain live-question fallbacks for attempts with no version lock. | Reset test data or migrate retained attempts, then make incomplete/missing locks fail closed or appear as an explicit unavailable historical record. | New and seeded attempts all have complete locks; progress/result never query live questions for an attempt. |
| `P15-COMP-06` | `REVIEW_REQUIRED` | `PracticePublishedVersionService` and `PracticeService` retain single-section ungrouped-question compatibility plus synthetic/orphan group rendering. | Canonicalize seed/import data into groups, then remove only fallback branches unsupported by the final authoring contract. | Fresh publication contains no orphan question version; player/result tests use canonical groups only. |
| `P15-COMP-07` | `REVIEW_REQUIRED` | `PracticeRoutes` and `PracticeController` still expose legacy set/mode/room/submit/submission routes and redirect handlers. | Inventory real callers and bookmarks, retain intentional redirects for one documented window or remove them together with route tests. | Each old route is either absent or has one documented redirect contract; no hidden service path depends on it. |
| `P15-COMP-08` | `REVIEW_REQUIRED` | `QuestionTypeResolver`, editor/player JavaScript and templates still accept aliases such as `MCQ`, `MCQ_SINGLE`, `TFNG` and `GAP_FILL`. | Normalize/reset imported fixture data, then shrink runtime authoring/player aliases while preserving only any explicitly supported import boundary. | Persisted live/version question types are canonical; runtime templates do not branch on removed aliases. |
| `P15-COMP-09` | `REVIEW_REQUIRED` | `PracticeAssessmentExcelService` accepts `practice-excel-v1`; `PracticeAssessmentExcelV2Codec` and draft adapters still emit/read legacy `options`, `answerKey` and answer aliases beside canonical content/spec fields. | Decide the supported import window, remove v1/runtime duplicate fields together, and version the final template contract. | One documented Excel schema round-trips canonical fields without duplicate legacy answer sources. |
| `P15-COMP-10` | `REVIEW_REQUIRED` | Active detail routes still use `PracticeService.getResult`, `getReadingListeningResult`, `PracticeResultView`, `ReadingListeningResultView`, `result-detail.html` and `rl-result-detail.html`. | Do not remove before Phase 13E supplies exactly three canonical evidence-detail screens (Objective R/L, Writing, Speaking). After replacement, delete the old DTO/service/template/script cluster in one patch. | `/attempts/{id}/result/detail` dispatches to exactly one of three typed 13E screen contracts; old generic DTO/template/parser symbols have no references. |
| `P15-COMP-11` | `REMOVE_CANDIDATE_CONFIRMED` | `src/main/resources/static/js/practice.js` is not loaded by a template/controller. `PracticeResultWordingTest` reads it directly, so tests currently preserve an otherwise dead asset. | Remove the asset and tests that exist only to inspect it, after a final resource-reference scan. | No template/resource/controller reference exists before deletion; current result assets cover required wording/behavior. |
| `P15-COMP-12` | `REVIEW_REQUIRED` | Tests still deliberately create historical Writing fill-blank, Speaking ESSAY/mixed feedback, ungrouped questions, no-version attempts, old routes and legacy import aliases. | Classify each fixture as current regression, migration fixture or obsolete test-data compatibility. Delete obsolete fixtures with the production branch they pin. | Test names and fixtures describe only canonical runtime or an explicitly retained migration boundary. |
| `P15-COMP-13` | `REVIEW_REQUIRED` | `V25__practice_hub.sql`, `V33__practice_immutable_versions.sql` and `V34__practice_single_scope_final.sql` carry old development seed, graph/type migration and alias normalization history. The files were renumbered without SQL changes when Practice joined `main` after its V24 migration. | First prove the project has no deployed database obligation. Then choose either an immutable deployed migration chain or a reviewed pre-production baseline squash plus fresh rehearsal. | Fresh schema and representative upgrade both have recorded outcomes; no migration is edited/deleted without deployment evidence. |
| `P15-COMP-14` | `REVIEW_REQUIRED` | Current and historical docs still mention retired `rl-result.html`, `QuestionExplanationCache`, lazy explanation generation and other superseded paths. Historical phase evidence may be intentionally archival. | Separate current contracts from historical records. Correct current specs/runbooks and preserve historical evidence with explicit superseded labels. | Current specs match source; archival docs are clearly dated and are not used as implementation instructions. |
| `P15-COMP-15` | `REVIEW_REQUIRED` | Local database fixtures and ad-hoc attempts such as `687`/`688` were created for UI investigation and do not form a coherent product seed. | Inventory IDs needed only for debugging, export evidence if required, then reset the dedicated local/UAT database before premium seed loading. | UAT starts from a documented clean database and deterministic seed manifest, not ad-hoc rows. |
| `P15-COMP-16` | `REVIEW_REQUIRED` | Explanation artifact JSON is versionable, but `question_version_explanation_bindings` is unique on `(question_version_id, explanation_language)` and the repository currently binds only if absent. A bound READY v2 artifact therefore has no normal supersession path for v3. | Choose a forward-only rebind/supersession contract with audit history, or explicitly delete/rebuild only an approved disposable UAT database. Do not treat JSON-column flexibility as proof that relational rebinding is solved. | A version can resolve exactly one active compatible artifact while prior bindings remain auditable or are removed only by an approved UAT reset; stale-schema tests cannot reuse v2 as v3. |
| `P15-COMP-17` | `REVIEW_REQUIRED` | `V44__practice_seed_listening_check_audio.sql` repairs a local development Listening seed in the integrated main Flyway chain (the same historical fixture had number V38 before integration). Its content is useful for current pre-13E fixtures but is not yet classified as production baseline, UAT seed or disposable local repair. | Before release baseline/UAT construction, classify V44 and separate durable schema migration from environment seed loading. Keep an immutable migration chain if any environment has applied it; otherwise use a reviewed pre-production baseline decision rather than silently rewriting history. | Fresh/upgrade evidence names V44's status; production contains no local credentials/ad-hoc fixture assumptions, and deterministic UAT seed loading is independently repeatable. |
| `P15-COMP-18` | `REVIEW_REQUIRED — RETIRE_WRITING_LOCAL_1_9` | `WritingScoreMatrix` still owns `bands()`, `clampAndRound()`, `toHundredPointScale()` and `bandLabel()`. `WritingEvaluationNormalizer` retains `LEGACY_BAND_V1`, `band_label` and band fallbacks; `WritingScoringPolicy` infers legacy semantics when score `<=9`; `WritingMockEvaluatorService` and `PracticeService` still call the matrix. Focused `WritingScoreMatrixTest`, `WritingEvaluationNormalizerTest`, `WritingScoringPolicyTest`, `WritingTaskNativeScoringTest` and integration fixtures preserve the path. New canonical scoring is Q51/Q52 `/10`, Q53 `/30`, Q54 `/50`, GENERAL `/100`. | Owner: Phase 15 compatibility/data decision after P15-PRE-02/09. Inventory persisted cache/result/attempt JSON. If no retained data is required or an approved UAT reset is allowed, choose `DELETE_UAT_ONLY` and remove the class, runtime calls and legacy-only fixtures. If history must remain, choose `MIGRATE` and isolate an expiring read-only adapter at the compatibility boundary; new prompt/provider/write/UI paths may never emit/infer band 1–9. | Canonical production scan has no `WritingScoreMatrix`, `LEGACY_BAND_V1`, `band_label`, score-`<=9` inference or 1–9 label; failures remain non-score-bearing; task-native maxima are unchanged. Historical attempts have migration/reset proof, and any migration-only fixture/adapter is clearly labelled with owner and expiry/removal decision. |
| `P15-COMP-19` | `REVIEW_REQUIRED — REMOVE_SPEAKING_TEXT_SIMULATED_SCORE`; consumer guards `IMPLEMENTED_PENDING_PHASE_VALIDATION` | `PracticeService.mockSpeakingFeedback` and three legacy submit/re-evaluate/fallback call sites fabricate `3/5.5/7` from word count, convert it with `WritingScoreMatrix`, emit `text_simulated_mock` / `practice_speaking_mock`, `S_FLUENCY` and `sample_answer`, then can mark an attempt graded. `SpeakingFeedbackCompatibilityReaderTest` retains a mock fixture. Phase 13D now guards current consumers: `PracticeDetailPageService`/test detail show no latest/best number; `PracticeService` excludes Speaking from progress average/trend/type/highlight metrics and nulls score history; `progress.html` and result detail show explicit no-holistic-score copy instead of `0%`. | Owner: Phase 15 compatibility after retained-attempt inventory and P15-PRE-01. Preserve the consumer guards, then remove/reset/migrate the producer paths; a disabled or unavailable evaluator must persist an explicit unavailable/non-score-bearing result, never a simulated Speaking score. If old rows are retained, read them only as legacy/unverified with an expiry decision. | Exact source/config/fixture scan has no `mockSpeakingFeedback`, `text_simulated_mock`, `practice_speaking_mock` or word-count Speaking score in canonical runtime; provider-disabled tests assert null score/unavailable; retained historical numbers never re-enter progress/history/latest/best, and current null Speaking is never rendered as `0%` or `Đang chấm`; retained rows have migration/reset proof. |
| `P15-COMP-20` | `REVIEW_REQUIRED — REMOVE_UNREACHABLE_WRITING_MOCK_FALLBACK` | `WritingEvaluationClient` catch blocks test `if (ex != null)` and return fail-closed unavailable, making later `fallbackToMock(...)` branches unreachable while retaining `WritingMockEvaluatorService` as misleading runtime dependency. | Owner: 13H dead-code cleanup, coordinated with P15-COMP-18. Remove unreachable fallback branches/dependency without changing the required provider failure semantics. | Static source scan finds no unreachable mock fallback; missing key, HTTP, timeout and unexpected failures remain `EVALUATION_UNAVAILABLE` with null score; no mock provider result is cached/persisted as current. |
| `P15-COMP-21` | `REVIEW_REQUIRED — SPEAKING_UNGROUPED_TRANSCRIPTION_DECISION` | `PracticeSpeakingMediaRepository.findAuthorizedTranscriptionCandidates` joins `PracticeQuestionGroup g` and requires `g.id = q.groupId`. Canonical Speaking is grouped, but a retained ungrouped legacy attempt cannot reach transcription even though other result/player fallbacks may still read it. | Owner: Phase 15 compatibility with P15-COMP-06, before any live Speaking provider UAT. Prove no retained attempt needs transcription and remove the broader ungrouped compatibility, or add one explicitly bounded migration path; do not weaken owner/attempt/question/media authorization. | Persisted live/version scan is fully grouped or a migration proof exists; canonical transcription authorization tests remain owner- and immutable-question-bound; no unexplained ungrouped fallback is exercised in Manual UAT. |
| `P15-COMP-22` | `REVIEW_REQUIRED — LOCAL_FIXTURE_MIGRATION_HISTORY_COLLISION` | The Phase 13D correction attempted two authenticated Result Detail integration cases against configured `ksh_phase13e_result_ui`. Flyway reported V44, but Hibernate validation failed before test setup because table `tests` lacks `media_type`. Evidence indicates this fixture schema consumed the old feature-branch V38 Listening seed, while the integrated chain assigns V38 to `V38__test_media.sql` and V44 to the Listening seed; `validate-on-migrate=false` did not surface the changed migration meaning. | Owner: database/release owner before Phase 15 Manual UAT, coordinated with COMP-13/15/17. Do not edit applied history or bypass Hibernate validation. Decide whether this local fixture database is exported then discarded, or retained only as historical evidence; build a fresh disposable/current UAT schema from the reviewed migration chain and run the two blocked route cases there. | A fresh V1-current migration rehearsal contains `tests.media_type`, Flyway checksums/history match the reviewed chain, the two named `PracticeIntegrationTest` routes execute and pass, and no stable fixture/UAT database is silently wiped or rewritten. |

### 6.1 Compatibility decision / dependency / UAT ledger

Every row remains `UNRESOLVED_BEFORE_PHASE_15_MANUAL_UAT`; this ledger assigns
an owner and prevents a generic “legacy cleanup” ticket from hiding different
data decisions.

| ID | Owner / dependency | Required resolution and evidence |
| --- | --- | --- |
| `P15-COMP-01` | Phase 15 Writing compatibility; retained-attempt inventory | `REMOVE` or bounded `MIGRATE`; immutable non-ESSAY Writing count and route/UI proof |
| `P15-COMP-02` | Phase 15 grading compatibility; canonical Speaking publication | Remove non-Writing ESSAY evaluator path; publication rejection proof |
| `P15-COMP-03` | Phase 15 Speaking compatibility; COMP-02/19 | Remove mixed envelope after retained-row decision; current-only persistence scan |
| `P15-COMP-04` | Phase 15 feedback compatibility; PRE-05 | Per payload/status migrate/retain-with-expiry/remove; current provider parse proof |
| `P15-COMP-05` | Phase 15 immutable-attempt migration; PRE-05 | Migrate/reset incomplete locks; no live-question query proof |
| `P15-COMP-06` | Phase 15 graph/seed compatibility; COMP-21 | Canonicalize groups; no orphan/ungrouped production fixture proof |
| `P15-COMP-07` | Phase 15 route owner; real caller inventory | Remove or time-bound redirect; route/caller proof |
| `P15-COMP-08` | Import/data owner; canonical stored-type scan | Normalize persisted aliases, retain only documented import adapter with expiry |
| `P15-COMP-09` | Excel import owner; supported-schema decision | One canonical round-trip schema and no duplicate answer source |
| `P15-COMP-10` | Phase 13E then Phase 15 cleanup; PRE-06 | Replace detail first, then remove old DTO/service/templates as one cluster |
| `P15-COMP-11` | 13H/Phase 15 dead-resource cleanup | Final reference scan then remove asset and test-only preservation |
| `P15-COMP-12` | Test/fixture owner; depends on matching production removals | Classify current vs migration vs obsolete; names/expiry and source parity proof |
| `P15-COMP-13` | Database release owner; environment/deployment proof | Immutable chain or approved pre-production baseline; fresh/upgrade evidence |
| `P15-COMP-14` | Documentation owner; current-source crosswalk | Correct current docs, add supersession labels to history; source/doc scan proof |
| `P15-COMP-15` | UAT environment owner; PRE-05 | Export required evidence then approved clean reset; deterministic seed proof |
| `P15-COMP-16` | R/L artifact/database owner; PRE-03/05 | Forward supersession/rebind or approved UAT reset; one-active binding proof |
| `P15-COMP-17` | Migration/seed owner; deployment proof | Classify V44 and separate durable migration from environment seed |
| `P15-COMP-18` | Writing compatibility/data owner; PRE-02/09 | `MIGRATE` or `DELETE_UAT_ONLY`; no canonical 1–9 path and task-max proof |
| `P15-COMP-19` | Speaking compatibility/data owner; PRE-01/05 | Remove simulated scoring; provider-disabled null-score and retained-row proof |
| `P15-COMP-20` | 13H dead-code owner; COMP-18 | Remove unreachable mock fallback; fail-closed provider error tests |
| `P15-COMP-21` | Speaking data/provider owner; COMP-06 | Group/reset/migrate retained rows without weakening authorization; provider-UAT proof |
| `P15-COMP-22` | Database/release owner; COMP-13/15/17 | Classify the collided fixture schema, build a fresh disposable/current schema, prove migration history plus `tests.media_type`, and execute the two blocked Result Detail route cases |

## 7. Architecture that must remain

| ID | Status | Required behavior |
| --- | --- | --- |
| `P15-KEEP-01` | `KEEP` | Immutable published version graph and old-attempt isolation. A cleanup may remove unsupported legacy shapes, but must never make an attempt read the newest live question/version. |
| `P15-KEEP-02` | `KEEP` | Canonical `AssessmentContractCodec`, question-type validation and scoring for supported objective types. Only obsolete adapters/aliases are candidates. |
| `P15-KEEP-03` | `KEEP` | Current external-provider response normalization that is still required by tested provider payloads. Compatibility with a live API is not legacy debt merely because it normalizes fields. |
| `P15-KEEP-04` | `KEEP` | Material ownership, immutable reference checks, lifecycle states, private access and audit/security behavior. Data reset does not authorize weakening these controls. |
| `P15-KEEP-05` | `KEEP` | Phase 13D explanation artifact, question-version binding, durable task, retry, reconciliation and read-only result lifecycle introduced by `V37__question_explanation_artifact_lifecycle.sql`. |
| `P15-KEEP-06` | `KEEP` | The result-detail route until Phase 13E has replaced its implementation and equivalent learner evidence is verified. |

## 8. Phase 15 cleanup gate

Phase 15 executes this as one reviewable cleanup program, not as isolated
deletions discovered during Manual UAT:

1. **Environment freeze and proof**: identify local/UAT/production databases,
   backup retained data, record migration versions and reconfirm that destructive
   reset/squash is limited to an approved non-production environment.
2. **Inventory resolution**: assign each `P15-COMP-*` item `REMOVE`, `MIGRATE`,
   `RETAIN_WITH_EXPIRY` or `KEEP`, with owner and evidence.
3. **Controlled cleanup batch**: remove obsolete code, route, DTO, template,
   static asset, test, seed and current-doc references together so no parallel
   half-path remains.
4. **Static no-legacy audit**: scan canonical types, route aliases, old feedback
   contracts, no-version fallbacks, dead resources and superseded result/cache
   names. Any retained hit must point to an approved migration boundary.
5. **Database rehearsal**: run fresh migration/schema validation and any required
   representative legacy upgrade. Record why migrations were kept or squashed.
6. **Automated gate**: compile once, run the smallest focused cleanup/migration
   tests, required integration tests and the full suite once for release
   confirmation.
7. **Premium seed creation**: load only after the cleanup gate is green.
8. **Manual UAT**: run role, browser, device, media, immutable-result and AI
   journeys against the premium seed; capture screenshots and GO/NO-GO evidence.

## 9. Premium canonical seed checklist

The Phase 15 seed must be deterministic, versioned and suitable for product UAT:

- one coherent `Set > Test > Skill > Section > Group > Question` hierarchy;
- Reading/Listening questions with Korean passage/transcript, valid audio/image
  assets, answer specs, teacher explanations and READY explanation artifacts;
- Writing Q51-Q54 as ESSAY with realistic Korean prompts, maximum points
  `10/10/30/50` and rubric-compatible responses/feedback;
- Speaking questions only as `SPEAKING`, with valid prompt audio/recording
  targets and a capability-labelled transcript-profile fixture with no
  aggregate/holistic score. A direct-audio holistic fixture is allowed only if
  P15-PRE-01/07/08 are green and the scorer-consumed audio provenance is part of
  the seed evidence;
- deterministic in-progress, submitted, pending, graded and failed-provider
  attempts, all with complete immutable version locks;
- documented development-only learner, lecturer and reviewer credentials plus
  stable seed IDs where UAT automation needs them;
- original/licensed or project-owned content, not placeholders or copied PREP
  branding/assets;
- no non-Writing ESSAY, no historical Writing fill-blank, no orphan questions,
  no missing required media and no mock/provider wording presented as real AI.

## 10. Manual UAT exit evidence

Phase 15 cannot declare the cleanup/seed step complete until:

- every inventory item has a recorded resolution and evidence;
- static scans contain no unexplained confirmed-removal symbols;
- a fresh canonical database reaches the expected latest migration;
- the premium seed manifest matches persisted question types, weights, assets
  and immutable version locks;
- runtime logs show no unexpected compatibility fallback during the UAT matrix;
- old routes are either intentionally documented redirects or return the
  approved unavailable response;
- old ad-hoc attempts are absent from the dedicated UAT database;
- focused, integration and full-suite evidence is recorded once after the
  cleanup batch; and
- Manual UAT screenshots/results identify capability-specific GO/NO-GO status.

## 11. Linked gates

- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, Sections 6.9 and 6.9.12
- `CODEX_PRACTICE_WORKFLOW.md`, latest Phase 13D status and current next action
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`, Phase 15 cleanup/seed/UAT order
