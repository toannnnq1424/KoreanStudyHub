# Practice Phase 15 Compatibility Cleanup And Seed UAT Inventory

> Current AIM-0/AIM-1 overlay (`2026-08-02`): PR #55 merged
> `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` into `origin/main` at
> `3dfab18ae308005c018c9066256bb7c79b686e8e`; that gate is
> `CLOSED_MERGED`. The mandatory successor is now
> `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION`, followed by its consolidated
> epic gate. Only that accepted gate may hand off to
> `PRE_PHASE_15_RELEASE_CLOSURE_GATE`. Any lower historical sentence that says
> Pre-15 follows Pre-14 directly is superseded by this overlay. Contract and
> task authority live in
> `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md` and
> `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`.

> Current-source overlay (`2026-07-28`): Phase 13C3 is
> `CLOSED_VERIFIED_MERGED`. Both final independent audits accepted exact SHA
> `420a9a905cd202116158802eeaff799aab29e4b5`; PR #26 merged as
> `65328e9fae5201be2f154c90739bcb78f1034e4d`. Phase 13G is
> `COMPLETE_FOCUSED_GATE_GREEN`: its exact rerun passed `82/82`, fresh V56
> proof returned `56/56/0/1/7` and cleanup absence was `0`. Its branch
> publication is the terminal task-local step; Phase 13H requires a separate
> task. Historical pending 13C3 statuses below remain audit history and are
> not current instructions.

> Status: `REBASELINE_GO_WITH_GUARDS`; Phase 13E:
> `COMPLETE_FOCUSED_GATE_GREEN`; Phase 13F:
> `COMPLETE_FOCUSED_GATE_GREEN`; `13C3-00..04`:
> `IMPLEMENTED_AND_VALIDATED`;
> `PHASE_13C3_IMPLEMENTATION =
> CONSOLIDATED_VALIDATION_GREEN_PENDING_COMMIT_PUSH`;
> `PHASE_13C3_VALIDATION =
> GREEN_WITH_BROWSER_DEFERRED_TO_END_OF_PHASE_13`
>
> Recorded: `2026-07-22` (research started on `2026-07-20`; supersedes the inventory-only boundary recorded on
> `2026-07-16`)
>
> Two-gate roadmap amendment: `2026-07-23`
>
> Toolchain/dependency-security amendment: `2026-07-25`
>
> Speaking prompt-authoring amendment: `2026-07-25`
>
> Roadmap-order amendment: `2026-07-27`. Phase 14 keeps the stable `14A-14F`
> contract but is `DEFERRED_POST_MANUAL_UAT_NON_RELEASE_BLOCKING`. Current
> execution order is Pre-14 -> post-Pre14 authoring/import modernization -> its
> consolidated epic gate -> Pre-15 -> Phase 15 Manual UAT/release -> deferred
> Phase 14. This amendment changes scheduling only: every Pre-14 and Pre-15 debt
> below remains mandatory before Manual UAT. See
> `PRACTICE_PHASE_14_POST_MANUAL_UAT_ROADMAP_AMENDMENT.md`.
>
> Current roadmap position: the bounded Phase 13D result-overview UX correction
> is committed/pushed at `98153ac`; its two previously blocked authenticated
> Result Detail route cases passed `2/2` on a disposable fresh V44 schema.
> Phase 13E passed its one consolidated focused gate (`118/118`) and was
> committed/pushed at `93d87fd1f8dd93c93db592c3cf89bf352af23687`.
> Phase 13F is `COMPLETE_FOCUSED_GATE_GREEN`; `13F-01..06` are
> `IMPLEMENTED_AND_FOCUSED_TESTED`. Its accepted JDK 17 gate passed `331/331`
> with zero failures/errors/skips, the fresh disposable schema proved
> `44/44/0/1`, and cleanup plus the independent absence query returned `0`.
> No full-suite/browser/provider/Git evidence is claimed for Phase 13F. Phase
> 13C3's final isolated 167-path snapshot passed JDK 17 compile, 497/497, the
> bounded 31/31 authorization/material gap closure and fresh V1-V45 proof.
> Browser QA is deferred to the end of Phase 13 and live STT/TTS was not
> approved. That historical mandatory action is closed. Phase 13G has now
> completed its focused gate; only its coherent commit series and one branch
> push remain in the current task, after which work stops until the separate
> Phase 13H task.
> Current 13C3-04 lifecycle source also locks every historical asset row sharing
> an exact storage key at asset-delete claim and final confirmation; any sibling
> state/reference that still needs the bytes stops physical I/O without
> mutating the sibling and leaves the exact candidate task pending for bounded
> recheck after that key is released. Due selection is `nextAttemptAt` then ID,
> so permanently retained tasks cannot starve newer eligible cleanup.
> This document routes future work into two named gates; it does not authorize
> compatibility deletion, database mutation, audio scoring rollout, SME
> sign-off or calibration work during the current 13C3 implementation.
>
> Future owners: guarded Practice rebaseline only after mandatory
> 13C3/13G/13H validation plus commit/push, after the post-Phase-13
> product/package reconciliation and comprehensive `/practice`
> audit/cleanup program is accepted/validated/committed/pushed, and after final
> pre-14 schema contracts are frozen, before Pre-15 and Manual UAT;
> `PRE_PHASE_15_RELEASE_CLOSURE_GATE` follows the accepted authoring/import
> modernization epic gate and owns canonical UAT
> seed/release preparation before Phase 15 executes Manual UAT. Deferred Phase
> 14 starts only after that initial release verdict.

## 1. Purpose and current Phase 13E boundary

This document now has four future-facing jobs:

1. record compatibility paths, legacy test-data behavior, old routes, adapters,
   fixtures and documentation that must be resolved together;
2. route target-stability and assessment correctness work into the mandatory
   pre-14 gate; and
3. route the bounded authoring/import modernization epic and its consolidated
   acceptance gate between closed Pre-14 and Pre-15; and
4. keep final academic, destructive/environment and seed preparation in the
   Pre-15 gate, then execute Manual UAT in Phase 15 before deferred Phase 14.

Phase 13E is `COMPLETE_FOCUSED_GATE_GREEN`; `13E-01..05` are implemented and
focused-tested. Phase 13F is `COMPLETE_FOCUSED_GATE_GREEN`; `13F-01..06` are
`IMPLEMENTED_AND_FOCUSED_TESTED`, backed by `331/331`, schema proof
`44/44/0/1` and cleanup/absence `0`. The prior exact 143/144/163-path 13C3
snapshots are historical evidence only. The final 167-path isolated snapshot,
including the four-path authorization/material transaction correction, is
`CONSOLIDATED_VALIDATION_GREEN_PENDING_COMMIT_PUSH`, not
implementation of this inventory. Phase 13E implemented the
explicitly promoted runtime/UI portions of `P15-PRE-03`, `P15-PRE-06` and
`P15-PRE-14`; the future pre-14 gate audits that evidence and closes only the
promoted correctness contracts without redoing accepted work merely to touch
an old filename.
Final academic calibration, direct audio, destructive data/environment work
and seed remain Pre-15 work before Phase 15 Manual UAT.

Phase 15 is for release hardening, deterministic seed loading and Manual UAT.
It is not the phase in which KSH first discovers that a score has no supporting
evidence, that a rubric profile is ambiguous, or that one explanation JSON
shape is being forced onto three different question constructs.

The compatibility portion remains an inventory, not retroactive authorization
to delete historical paths. Historical labels such as
`FUTURE_PHASE_15_ENTRY_BLOCKER` are superseded by the authoritative routed-gate
sequence in Section 4.1. No item becomes executable until its named gate's
dependency window opens.

The required order is:

1. after static acceptance of `13C3-00..04`, validate the
   mandatory
   `PHASE_13C3_SPEAKING_PROMPT_AUTHORING_CORRECTION`, then create its coherent
   multi-commit series and push it before opening 13G;
2. finish 13G, run its one consolidated validation, create its coherent
   multi-commit series and push it before opening 13H;
3. finish 13H, run its one consolidated validation, create its coherent
   multi-commit series and push it,
   including
   PRE-10..13 plus the reproducible-toolchain/security-baseline items
   PRE-15/16 where assigned, without opportunistic inventory cleanup;
4. after end-of-Phase-13 browser/device closure, execute the audit-first
   `POST_PHASE_13_PRACTICE_PRODUCT_INTEGRATION_AND_PACKAGE_RECONCILIATION`.
   Map the currently separate Practice and non-Practice AI/storage facilities;
   implement only explicitly approved compatibility-first slices, never a bulk
   common/Admin/global merge; validate, commit and push that phase;
5. execute the mandatory multi-subagent comprehensive `/practice` audit and
   evidence-backed dead/duplicate route, code and schema cleanup defined in
   `docs/PRACTICE_PRE_PHASE_14_COMPREHENSIVE_AUDIT_AND_DEAD_SURFACE_CLEANUP.md`;
   validate that complete program once, create coherent commits and push it;
6. execute `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` and decide GO/NO-GO for
   every
   scoring/explanation/UI identity that Phase 14 can report, including binding
   supersession, Writing cache identity and configuration safety;
7. only after mandatory 13C3/13G/13H, product/package reconciliation and
   comprehensive-audit completion, and
   after final pre-14
   relational contracts are frozen, execute the guarded Practice rebaseline
   on a new disposable DB if and only if no
   retained/deployed/shared/canonical/upgrade obligation exists;
8. prove fresh Flyway/Hibernate validation and minimal technical R/L/W/S smoke
   identities;
9. execute `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION` as AIM-2 through AIM-8,
   preserving the AIM-1 contract, advanced Excel compatibility, the canonical
   editor/publisher and the separate Practice AI/storage data planes;
10. pass the epic's single consolidated acceptance gate, including schema,
   compatibility, idempotent-apply, fail-closed provider/storage and exact
   learner-preview proof;
11. execute `PRE_PHASE_15_RELEASE_CLOSURE_GATE`: final SME/calibration and the
   chosen disabled/direct-audio branch;
12. confirm environment and retention obligations, then resolve
   destructive/remaining compatibility decisions;
13. create the deterministic premium UAT seed only after cleanup is green; and
14. run browser/device/provider/load/security/manual UAT and record the Phase
   15 release verdict, explicitly excluding Report an Error; and
15. only after that verdict, execute the unchanged canonical Phase 14A-14F
   “Report an Error & Content Review” loop under its own validation/14F gate.

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
- The user-authorized exception is narrow: replace the disposable,
  Practice-only pre-production chain only after written no-obligation proof.
  Any retained/deployed/shared/canonical/upgrade obligation is an immediate
  stop condition; preserve checksums and use a reviewed forward migration.
- Never use Flyway repair, never reuse/clean an old schema in place and keep the
  old DB read-only as evidence. `validate-on-migrate=true`; clean is disabled by
  default and available only to an explicitly allowlisted disposable profile.

## 3. Canonical target contract

The two gates, Phase 15 cleanup and the final seed must converge on these
contracts:

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

## 4. Authoritative two-gate routing

### 4.1 Entry rule and blocker register

The following work was designed before Phase 13E opened. The user has since
promoted the bounded runtime/UI subset of `P15-PRE-03`, `P15-PRE-06` and
`P15-PRE-14` into the approved Phase 13E program. The old rule that routed the
entire register to one gate after Phase 14 is superseded. Pre-14 is now closed;
the authoring/import modernization epic and its consolidated gate follow it,
then Pre-15 must close before Phase 15 Manual UAT. Phase 14 is executed later.

#### `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE`

Execution window: only after `13E-13H` are complete with accepted consolidated
validation and each completed phase has been committed/pushed, and after the
separate comprehensive `/practice` audit/cleanup program is accepted,
validated, committed and pushed. It runs before 14A and decides whether the
reportable target identity is production-ready:

Every implementation/remediation item in this subsection belongs to its named
earlier phase or to the post-13H comprehensive audit/cleanup program. The gate
only inspects the already integrated and pushed evidence. A blocker discovered
here sends work back to one grouped cleanup/validation/commit/push cycle before
the gate is re-entered.

- mandatory `P15-PRE-02`, `04`, `09` and the runtime/contract portion of
  `P15-PRE-14`;
- mandatory `P15-COMP-18`, `19`, `20`; `P15-COMP-10` only after consolidated
  Phase 13E validation; and forward-only `P15-COMP-16`;
- lecturer reference must either round-trip immutably through draft, publish,
  question version and old-attempt result, or stay hidden/unreportable with
  explicit debt;
- screenshot/audio report attachment consent, authorization, private storage,
  redaction, retention/deletion and audit policy must be fixed before 14B;
- only the data-decision/new-write/dual-read/invalidate/rebind portion of
  `P15-PRE-05`, plus the separately guarded Practice-only rebaseline after
  final relational contracts are frozen;
- datasource/Flyway config safety, fresh Flyway/Hibernate validation and
  minimal technical R/L/W/S smoke fixtures with stable immutable report
  identities;
- accepted 13H evidence for one reproducible JDK 17 toolchain and a dated,
  coherent dependency-security baseline. This is not canonical content UAT.
- accepted `P15-PRE-17`/13C3 evidence for mode-dependent Speaking prompt
  authoring, learner-safe v2 delivery, owner-scoped STT/TTS artifacts/tasks,
  immutable evaluator-only prompt context and no provider call from
  GET/autosave/preview/publish.

The expanded versioned `P15-PRE-14` contract covers Writing and Speaking, plus
typed R/L explanation. Current Writing finding categories/IDs are not
exhaustive; Q51/Q52/Q53/Q54/GENERAL task applicability, evidence authority,
descriptor/impact and parent-score mapping must be audited/versioned. The
current 16 Speaking transcript subcriteria/examples are likewise a bounded
subset, not complete Korean coverage. Adding enum values is not SME or
calibration proof. Writing keeps the three stable scoring criteria and expands
a separate diagnostic registry across Q51/Q52/Q53/Q54/GENERAL: morphology/
particles; endings/speech level/register/honorific; tense/aspect/modality/
negation; predicate-valency/호응; connectives; adnominal/relative/embedded
clauses, quotation/nominalization; passive/causative; word order/ellipsis/
reference; spelling/spacing/punctuation; and vocabulary sense-in-context,
collocation, Sino-Korean usage, precision, naturalness and repetition. Each
finding carries evidence, impact, frequency, confidence, observability and task
applicability instead of becoming a new score row.

Current strict Writing provider schema still omits `subtype`, `impact`,
`frequency` and `confidence` even though the normalizer reads/interprets part
of that data. This is mandatory pre-14 runtime debt. `13E-03` builds only the
typed UI/contract seam and honest non-exhaustive copy. The post-13H
comprehensive audit/cleanup program implements the approved Writing and
Speaking registry, provider schema/prompt, normalizer, bounded rule engine and
cache/`AssessmentPolicyBundle` identity corrections; the pre-14 gate only
verifies that accepted, validated and pushed evidence.

`P15-PRE-10..13` and `P15-PRE-15..16` remain assigned to 13H;
`P15-PRE-17` is assigned to the mandatory 13C3 correction. The green Phase 13F
gate supplies bounded progress/recovery evidence but does not claim these open
operational debts. Their accepted evidence is a prerequisite before Pre-15,
Manual UAT and the later Phase 14, not work reimplemented inside this gate.
PRE-16 additionally requires a fresh release rescan in Pre-15 because
advisories are time-sensitive. `P15-COMP-01..09`,
`11..12`, `14`, `21` are conditional: promote only
when caller or retained-data evidence proves that the path destabilizes a
Phase 14 target; otherwise leave them for release cleanup.

#### `PRE_PHASE_15_RELEASE_CLOSURE_GATE`

Execution window: only after the accepted consolidated gate for
`POST_PRE14_AUTHORING_IMPORT_MODERNIZATION` and before Phase 15 Manual UAT. It
does not wait for deferred Phase 14. It owns:

- final `P15-PRE-07` Korean-SME sign-off and calibration;
- `P15-PRE-01` branch A disable proof, or branch B direct-audio rollout only
  after `P15-PRE-08`, scorer-consumed audio proof and dark calibration;
- destructive/environment/schema work in `P15-COMP-13/15/17/22`, retained-data
  removal or approved disposable-UAT reset that remains after consuming the
  pre-14 baseline evidence;
- remaining compatibility decisions and premium seed preparation. Phase 15
  then owns browser/device/provider/load/security/manual UAT and the initial
  release verdict.

Phase 12R already removed all 14 generic governance/legacy tables and recorded
green fresh proof with `removed_tables_remaining=0`; neither gate opens a
generic table-pruning batch. Applied migrations remain immutable for every
retained/deployed chain and for non-Practice migrations. The sole exception is
the guarded Practice-only pre-production rebaseline in Section 4.1.3 after
no-obligation proof; outside it schema work is forward-only.

Every routed row must have one explicit closure:

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

| ID | Historical severity / current route | Current source truth | Required decision/change at assigned gate | Exit proof |
| --- | --- | --- | --- | --- |
| `P15-PRE-01` | `PRE_PHASE_15_RELEASE_CLOSURE_BLOCKER`; transcript-only guard `IMPLEMENTED_AND_PHASE_13D_FOCUSED_GATE_GREEN` | Phase 13D UX-03..05 use `TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION` / `TRANSCRIPT_ONLY`: `SpeakingEvaluationResult`, `SpeakingEvaluationNormalizer`, `SpeakingScorePolicy`, `PracticeService`, `SpeakingResultPresenter` and `speaking.html` keep only four native numeric language rows, make both acoustic rows null/`NOT_SCORABLE`, expose no `/70` subtotal, aggregate, holistic or attempt score and fail legacy/reserved envelopes closed. A trusted score-bearing typed envelope requires exactly all six rows; authoritative `TRANSCRIPTION_LOW_CONFIDENCE` keeps current transcript provenance with an empty rubric profile and no number. `OpenAiCompatibleSpeakingEvaluationClient` still sends only transcript plus optional question image; `AUDIO_DIRECT_FULL_RESERVED` is a disabled seam, not a capability. | Preserve the validated transcript-only, low-confidence and evidence guards. In Pre-15 choose exactly one branch: (A) keep full/audio-grounded Speaking disabled with proof; or (B) after PRE-08 policy approval, implement a dark separately named evaluator/API that demonstrably consumes authorized learner audio, then pass PRE-07 calibration/readiness before rollout. Configuration flags alone cannot authorize branch B. | Branch A closes as `DISABLED_BY_POLICY_WITH_PROOF`: no request sends audio to the scorer, readiness stays blocker-red and every surface exposes only the transcript profile plus null acoustic rows. Branch B closes as `IMPLEMENTED_AND_ACCEPTED`: captured request proves authorized audio reached the scorer and PRE-07/PRE-08 evidence is green. Both branches must preserve no-claim tests for low-confidence/current, legacy/reserved/malformed payloads. |
| `P15-PRE-02` | `PRE_PHASE_14_BLOCKER` | Writing has useful task-native rubrics and strict evidence spans, but the scoring profile is unnamed: Q53 is `12/9/9`, Q54 is `20/15/15`; prompt text also mixes conditional authoritative-task rules with unconditional Q54 “three questions” requirements. `TASK_METADATA` exists but provider findings using it are rejected. Character warnings describe deductions that the backend does not deterministically apply, and there is no governed performance-descriptor plus finding-impact/score-consistency model. | Before 14A choose and version one explicit profile. `KSH_INTERNAL_BALANCED` may retain justified internal weights but must never be presented as official TOPIK. `TOPIK_ALIGNED` must use the public official task criteria and weights recorded in the design source. Add versioned performance descriptors and score/finding consistency/impact invariants; remove prompt contradictions, make task requirements/data authoritative only when supplied, implement deterministic length/format policy, and either support validated `TASK_METADATA` or remove claims that require it. This contract closure is not final SME/calibration acceptance. | Contract fixtures for Q51-Q54 and GENERAL produce stable criterion totals, evidence, descriptors and labels. Major findings and criterion levels satisfy measurable consistency rules; every displayed score names its profile and maximum. No prompt says a structured requirement is optional in one paragraph and mandatory in another. |
| `P15-PRE-03` | `PHASE_13E_RUNTIME_PREREQUISITE`; contract/data closure feeds pre-14 | Reading/Listening still use one MCQ-shaped response schema (`meaningVi`, one `evidenceQuote`, `correctReasonVi`, `eliminatedOptions`) for all three constructs. Type-specific behavior mainly changes through prompt prose; image evidence is not yet bound to an asset/region. | Phase 13E owns the versioned discriminated provider/read/display contract and renderer for `SINGLE_CHOICE`, `FILL_BLANK` and `TRUE_FALSE_NOT_GIVEN`, including backend-owned Vietnamese labels and honest unavailable states. Pre-14 verifies the typed contract, bound evidence authority and explicit v2 `KEEP`/`DUAL_READ`/`MIGRATE`/`REGENERATE`/`DELETE_UAT_ONLY` decision; final Korean-SME/calibration acceptance remains in Pre-15 before Manual UAT. | Phase 13E proof rejects cross-type shapes and renders type-native fixtures without provider HTML. Pre-14 proof additionally covers text/visual/no-evidence cases and one explicit v2 disposition/rebind decision. Final academic corpus/calibration proof belongs to release closure. |
| `P15-PRE-04` | `PRE_PHASE_14_BLOCKER` | Writing and Speaking rule engines contain short substring/list rules. Such rules cannot represent the historical and current diversity of Korean and may create lexical false positives (for example matching `랑` inside a larger lexical item). | Before 14A reclassify deterministic rules as narrow signals, use token/boundary-aware matching where deterministic detection is retained, attach task/register/evidence scope, deduplicate overlaps and prohibit a rule hit from directly manufacturing a score. The rubric/model evaluates open Korean expression; code validates contracts and high-confidence invariants. | Counterexample tests cover lexical containment, acceptable colloquial/contextual use, spacing, Unicode normalization and repeated signals. Rule-only output cannot reduce an unrelated criterion without evidence. |
| `P15-PRE-05` | `PRE_14_DATA_DECISION_AND_GUARDED_REBASELINE; RETAINED_DATA_CLOSURE_PRE_15` | Compatibility JSON and database history are being used as a reason to postpone correctness decisions. Writing cache/result and attempt AI feedback are versioned/flexible JSON, while R/L artifact rebinding has a real relational constraint. The current local database is evidence, not a master/canonical database. | During Pre-14 record per-payload `KEEP`/`DUAL_READ`/`MIGRATE`/`REGENERATE`/`DELETE_UAT_ONLY`, enforce new-write/dual-read or invalidation and implement append-only active/superseded binding history. After consolidated Phase 13E and final schema freeze, rebaseline only when written evidence proves no retained/deployed/shared/canonical/upgrade obligation; otherwise stop and use forward-only. Pre-15 release closure handles retained-data removal and canonical-UAT reset before Manual UAT. | Pre-14 proof includes the written payload decision, binding/cache identity behavior, no-obligation decision, fresh Flyway/Hibernate validation and technical smoke identity. Pre-15 proof records retained-data/UAT disposition. No correctness blocker is deferred merely with “database còn dữ liệu”. |
| `P15-PRE-06` | `PHASE_13E_REPLACEMENT + PRE_PHASE_14_REFERENCE/LEGACY_PROOF` | Current detail is split between legacy `result-detail.html` and `rl-result-detail.html`; Writing/Speaking still share a generic browser-side JSON parser and evaluator sample tab. Phase 13D supplied only bounded safety guards. Lecturer `essaySample`/`speakingSample` is not yet proven to round-trip into canonical immutable published content. | Phase 13E owns exactly three typed skill-native screen contracts. Before 14A, lecturer reference must prove immutable round-trip or remain hidden/unreportable with debt; after consolidated 13E validation, COMP-10 removes only the verified-dead generic detail cluster. | Phase 13E proves one-of-three dispatch, type-native rendering, four W/S tabs and evidence honesty. Pre-14 proof verifies lecturer-reference visibility policy and one canonical report-entry screen context with no parallel parser. |
| `P15-PRE-07` | `PRE_PHASE_15_RELEASE_CLOSURE_BLOCKER` | `AiCalibrationReadinessPolicy` can pass with five fixtures per skill and broad percentage ranges; teacher review is only a warning. `AiCalibrationFixture` has no task/response/evidence mode, criterion/audio diversity, rater/adjudication/agreement or provider/bundle digest. `SpeakingProviderRolloutReadiness` correctly blocks the current transcript evaluator but has no teacher-reviewed acoustic validity evidence. | In Pre-15, Academic SME + backend/readiness calibrate every released Writing/Speaking/R/L-AI policy bundle with a versioned Korean corpus, annotation guide, multiple raters/adjudication, task/criterion/evidence diversity and repeat/provider-drift checks. If PRE-01 branch B supplies direct audio, add representative audio/device/accent/environment coverage; branch A must not pretend to have acoustic calibration. | Released bundles close as `IMPLEMENTED_AND_ACCEPTED` only when required dimensions/digests and Section 15 agreement/error/fairness/repeatability thresholds are green. Branch A closes acoustic scope as `NOT_APPLICABLE_WITH_PROOF` while readiness remains blocker-red. |
| `P15-PRE-08` | `PRE_PHASE_15_RELEASE_CLOSURE_BLOCKER_FOR_DIRECT_AUDIO`; report-attachment privacy is a separate pre-14 contract | `PracticeSpeakingMedia`, `PracticeSpeakingMediaService`, cleanup tasks, transcription resolver and owner playback provide technical lifecycle/access controls, but not an explicit consent/purpose/provider-disclosure contract, provider non-retention proof, withdrawal semantics or approved evaluator retention schedule. The playback controller is student-only/owner-scoped, so reviewer access is also not an authorized product boundary. | Security/Privacy + Backend define policy before any PRE-01 branch-B audio transfer. If direct audio stays disabled, prove scorer requests cannot include audio and document the existing recording/transcription/playback boundary. If enabled in Pre-15, additionally approve evaluator provider/purpose/region, non-training/retention terms, withdrawal semantics, deletion SLA, audit fields and explicit reviewer grants. | Branch A closes as `DISABLED_BY_POLICY_WITH_PROOF` for evaluator transfer. Branch B requires consent/withdrawal and owner/reviewer authorization tests, provider non-retention evidence and privacy-safe audit. The deferred Phase 14 screenshot/audio attachment policy is still closed independently before 14B. |
| `P15-PRE-09` | `PRE_PHASE_14_BLOCKER` | Writing/Speaking policy identity is split across task resolver/spec assumptions, `WritingScoringPolicy`, rubric/taxonomy, prompt/schema constants, normalizers, cache/reuse keys and calibration fixtures. `WritingEvaluationCacheService` identity includes prompt/rubric/schema/model inputs but not one canonical task-spec + scoring-profile + descriptor + taxonomy + evidence-policy + evidence-validator version + ASR confidence/availability policy + calibration-set bundle. | Before 14A introduce one immutable `AssessmentPolicyBundle` identity used by request construction, cache/reuse, provider provenance, normalizer and persisted result. Evidence-validator behavior, ASR threshold/policy and score-availability semantics are versioned inputs; incompatible old JSON follows the pre-14 portion of PRE-05. Final calibration-set acceptance is bound later without changing the stable identity model. | Mutating each identity component, validator version, ASR threshold or availability rule causes cache/reuse miss and prevents stale reinterpretation; persisted/current result proves exact bundle match; source scan finds no independent fallback version that can authorize a score. |
| `P15-PRE-10` | `PHASE_13H_PREREQUISITE_BEFORE_PHASE_14; IMPLEMENTED_PENDING_OWNING_VALIDATION` | The pulled-forward sidecar adds bounded `openai.connect-timeout` / `openai.read-timeout` properties and wires a `SimpleClientHttpRequestFactory` into the production Writing `RestClient`. Maven source/test compilation is green, but no timeout test method or provider/load UAT has run. | Owner: 13H/provider operational hardening. Validate it once in the owning unit; the pre-14 gate only verifies accepted evidence and does not reimplement it. Provider/load UAT remains Phase 15 work after Pre-15. | Source/config scan shows the Writing client applies bounded connect/read timeouts; focused timeout/retry tests return `EVALUATION_UNAVAILABLE` with no fake score; provider/load UAT later records bounded latency. |
| `P15-PRE-11` | `PHASE_13H_PREREQUISITE_BEFORE_PHASE_14` | `PracticeManageController` loads collaborators per set and users per grant (`findBySetIdAndRevokedAtIsNull`, then `userRepository.findById`), preserving authorization but producing N+1 queries. | Owner: 13H/performance hardening. Complete and validate it in 13H; the pre-14 gate only verifies evidence. | Query-count fixture over multiple sets/collaborators is bounded independent of row count, and authorization regression proves no additional set/user visibility. |
| `P15-PRE-12` | `PHASE_13H_PREREQUISITE_BEFORE_PHASE_14` | `PracticeSpeakingMediaCleanupTaskRepository.findDueTaskIds` selects due IDs without an atomic claim/lease; optimistic completion limits stale status writes but two nodes may perform duplicate external deletion/work. | Owner: 13H/operational storage hardening. Complete and validate it in 13H; the pre-14 gate only verifies evidence, while multi-node/R2 UAT remains Phase 15 work after Pre-15. | Concurrent-worker test proves one active claimant per task/lease; retry and expired-claim recovery are deterministic; storage logs show no duplicate non-idempotent operation. |
| `P15-PRE-13` | `PHASE_13H_PREREQUISITE_BEFORE_PHASE_14` | Practice PDF AI generation still lacks a durable atomic double-submit/idempotency boundary, and `PracticePdfCropService.cropRegion` renders/crops synchronously in the request path. | Owner: 13H/provider-load hardening. Phase 13F is green but does not claim this debt. Complete and validate it in 13H; the pre-14 gate only verifies evidence, while provider/load UAT remains Phase 15 work after Pre-15. | Concurrent-submit fixture produces one generation; crop workload has bounded request latency/resource evidence and durable failure/retry state; no duplicate provider charge or orphan crop is observed. |
| `P15-PRE-14` | `SPLIT_ROUTE — VERSIONED_RUNTIME_CONTRACT_PRE_14; FINAL_SME_CALIBRATION_PRE_15`; learner-surface subset `IN_PROGRESS_PHASE_13E` | Speaking instructions/labels still contain English; Writing mixes Vietnamese with internal English; W/S diagnostics are broad and R/L lacks typed learning lenses. Current Writing finding categories/IDs and the 16 bounded Speaking transcript subcriteria/examples cannot truthfully represent all historical or contemporary Korean. | Phase 13E owns backend-controlled Vietnamese/Korean learner labels, separation of score criterion/diagnostic/filter chip, a bounded registry subset, typed R/L learning lenses and evidence-honest no-claim copy; 13E-03 is only the Writing UI/contract seam under three stable scoring criteria. During Pre-14, expand/version the construct/subcriterion contract for both Writing and Speaking, audit Q51/Q52/Q53/Q54/GENERAL applicability, evidence authority, descriptor/impact and parent score mapping, complete the strict provider schema/prompt/normalizer/bounded-rule/cache identity, keep stable IDs ASCII, and bind typed R/L explanation plus bundle identity. Adding enums is not academic proof. In Pre-15, close Korean-SME sign-off, golden/adversarial corpus, calibration, fairness and repeatability. | Phase 13E source/render snapshots show no raw English learner label or unsupported evidence/acoustic claim. Pre-14 proof covers the versioned task × construct × evidence × descriptor/parent contract and explicit no-exhaustive-Korean claim. Pre-15 proof separately records SME/calibrated supported-domain acceptance. |
| `P15-PRE-15` | `PHASE_13H_REPRODUCIBLE_TOOLCHAIN_PREREQUISITE_BEFORE_PHASE_14; IMPLEMENTED_PENDING_CONSOLIDATED_VALIDATION` | Historical workstation-local `.idea` state (ignored by Git) selected `openjdk-26`, pinned Lombok `1.18.36` and named stale module `ulp`, causing the observed javac 26/Lombok `ExceptionInInitializerError` / `TypeTag.UNKNOWN`. The pulled-forward working-tree candidate adds `.java-version` `17`, compiler release `17`, Maven Enforcer `[17,18)` and BOM-managed Lombok `1.18.46`. This workstation's project/module SDK, Maven importer/runner and `KshApplication` module JRE resolve to Homebrew JDK `17.0.19`. A user-authorized diagnostic reload plus Maven production/test compilation and IntelliJ build completed with zero errors; no test method or startup ran. | Owner remains 13H/toolchain hardening or its explicitly grouped pre-14 predecessor. Preserve the repository guard and local JDK-17 alignment; never stage ignored `.idea` files as the portable fix. Close this only in the owning consolidated validation, including clean import and CLI/IDE agreement; the diagnostic build is evidence, not phase closure. | The one owning validation uses the same JDK 17 for clean import, compile/test gate and run configuration; Enforcer rejects outside `[17,18)`; IntelliJ no longer reports the Lombok initializer error; no stale local `ulp` processor profile is active. Current status is candidate evidence, not green. |
| `P15-PRE-16` | `PHASE_13H_DEPENDENCY_SECURITY_BASELINE_PREREQUISITE_BEFORE_PHASE_14; IMPLEMENTED_PENDING_CONSOLIDATED_VALIDATION_AND_SUPPORT_DECISION; RELEASE_RESCAN_PRE_15` | The working-tree candidate moves Spring Boot `3.4.4` to the coherent Java-17-compatible `3.5.16` BOM, updates POI `5.2.5→5.5.1`, PDFBox `3.0.3→3.0.8` and jsoup `1.18.3→1.22.2`, and uses named BOM overrides for Commons Lang `3.20.0`, Logback `1.5.38` and Tomcat `10.1.57`. Commons Lang is above the fixed boundary for CVE-2025-48924. Commons Codec is held at POI 5.5.1's declared `1.20.0` level instead of Boot's older `1.18.0`. A focused JDK-17 `dependency:resolve` resolved the graph; Maven then compiled `644` production and `233` test sources, and IntelliJ finished with zero errors. No test method, application startup, migration or security scan is claimed. Spring Boot `3.5.16` is a bounded final-OSS bridge, not a closed production-support decision. | Owner remains 13H/platform-security hardening or its explicitly grouped pre-14 predecessor. In the owning consolidated validation capture the resolved tree, attach dated SBOM/advisory/reachability evidence, regression-check direct parsers/sanitizer and record the supported Spring Boot line or commercial-support choice. Do not add arbitrary Spring Framework/Security/Jackson overrides. Pre-14 verifies the accepted baseline; Pre-15 reruns the time-sensitive scan immediately before Manual UAT. | Dated resolved tree/SBOM and scan are attached; the BOM is coherent; direct parser/sanitizer regression is green; no exploitable runtime Critical/High remains without owner, mitigation and expiry; the production support-lifecycle decision is explicit. A fresh release rescan is green or produces an explicit NO-GO/exception before Manual UAT. |
| `P15-PRE-17` | `PHASE_13C3_PREREQUISITE_BEFORE_PHASE_14`; `CONSOLIDATED_VALIDATION_GREEN_PENDING_COMMIT_PUSH`; `BROWSER_DEFERRED_END_PHASE_13`; `LIVE_STT_TTS_NOT_APPROVED` | The prior 143/144/163-path snapshots are historical for their exact digests. The final 167-path isolated snapshot includes the Vietnamese/responsive/private-material union plus the four-path authorization/material transaction correction. JDK 17 compile, the 53-class 497/497 selector, bounded 31/31 gap closure and fresh V1-V45 Flyway/Hibernate/authenticated Practice proof are green. Excel still ends at exact private staging; only a separate ID-free Editor action may verify/bind and enqueue STT, never TTS. Learner-answer STT remains a different authorization/domain boundary and cannot safely stand in for lecturer authoring. Browser QA is explicitly deferred; diagnostic browser activity is not gate evidence. | Owner: `PHASE_13C3_SPEAKING_PROMPT_AUTHORING_CORRECTION`; create granular commits and push the validated series once before 13G, then run the two fresh independent audits only on that pushed snapshot. Pre-14 JDK/dependency, Writing/cache and dead-resource sidecars are not 13C3 evidence. Keep both the project-wide and Practice-specific AI/storage implementations present, operational and separate for now: no current redirection or commonization. A later common/Admin/global reconciliation belongs only to the audit-first post-Phase-13 phase and must be approved slice by slice. | The accepted validation plus two fresh post-push static acceptances prove the transcript never reaches learner payloads, text-only makes zero TTS requests, uploaded audio never invokes TTS, every purpose/retention drift misses reuse, published-byte reupload yields a private logical identity, late results cannot overwrite newer revisions, stale/pending/failed required artifacts block publish and old v1 attempts remain immutable. Authorized end-of-Phase-13 Editor/player/browser evidence and separately approved cost-bounded provider evidence remain required before Pre-15/Manual UAT; neither is falsely claimed here. |

Audit source anchors refreshed on `2026-07-27`:

- [Lombok changelog](https://projectlombok.org/changelog): `1.18.36` adds JDK
  23 support; JDK 26 support begins at `1.18.46`.
- [IntelliJ SDK configuration](https://www.jetbrains.com/help/idea/sdk.html)
  and [Maven JDK configuration](https://www.jetbrains.com/help/idea/maven-support.html):
  project/module SDK, Maven runner and Maven importer are distinct settings
  that must be aligned.
- [Spring Boot 3.4.13 release notice](https://spring.io/blog/2025/12/18/spring-boot-3-4-13-available-now/):
  `3.4.x` has reached end of open-source support.
- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html):
  the 3.5 line uses Java 17 as its minimum.
- [Spring Boot 3.5.16 release notice](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/):
  the working-tree BOM is the bounded final open-source 3.5 bridge; supported
  production-line or commercial-support approval remains separate.
- [Spring Boot dependency coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
  and [override properties](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/properties.html):
  security bridges use documented BOM properties rather than duplicate direct
  dependencies.
- [CVE-2025-48924](https://nvd.nist.gov/vuln/detail/CVE-2025-48924)
  and [Commons Lang changes](https://commons.apache.org/proper/commons-lang/changes.html):
  affected versions are below `3.18.0`; the candidate uses `3.20.0`.
- [Apache POI 5.5.1 changes](https://poi.apache.org/changes.html):
  POI `5.5.1` upgrades Commons Codec to `1.20.0`; the repository override keeps
  that declared dependency level instead of Boot `3.5.16`'s older management.
- [Commons Codec changes](https://commons.apache.org/proper/commons-codec/changes.html):
  `1.20.0` is a Java-8-or-later maintenance release and remains compatible
  with the repository's enforced JDK 17.

#### 4.1.1 Design / workflow / inventory production-decision crosswalk

This table is the current-source bridge between
`KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`, this inventory and the
workflow. `ALIGNED` means the three documents now say the same thing, not that
future implementation or validation has passed. `PARTIAL` means the runtime has
a safe subset but one or both named gate proofs remain open. `MISSING` means a
replacement capability is intentionally absent and is therefore a `NO-GO`, not
an accepted silent debt.

| Production decision | Alignment after UX-05 | Inventory owner/dependency | Workflow gate/current action | Current source files | Measurable acceptance proof |
| --- | --- | --- | --- | --- | --- |
| Current Speaking is transcript-only; four native language rows may be numeric, acoustic rows are null/`NOT_SCORABLE`, and no subtotal/holistic/attempt score exists. | `ALIGNED`; Phase 13D focused gate and fresh-route gate green | `P15-PRE-01`; compatibility `P15-COMP-03/04/12/19` | Preserve the committed Phase 13D contract during Phase 13E. Historical “six criteria/holistic” ledger rows are superseded, not rewritten. | `SpeakingEvaluationResult`, `SpeakingEvaluationNormalizer`, `SpeakingEvaluationOrchestrator`, `SpeakingScorePolicy`, `SpeakingFeedbackCompatibilityReader`, `SpeakingResultPresenter`, `PracticeDtos`, `PracticeService`, `result/speaking.html`, bounded `result-detail.html` | Current/low-confidence/legacy/reserved/malformed fixtures expose no unsupported acoustic number, `/70`, level or holistic score; only current validated transcript rows contribute coverage. Low-confidence remains current provenance with no profile. |
| Future full Speaking requires authorized direct audio, academic calibration, privacy/retention and rollout readiness. | `MISSING` capability; correctly `NO-GO` | Staged path: PRE-08 policy -> PRE-01 dark capability -> PRE-07 calibration/readiness -> rollout; PRE-12 is operational coordination, not a predecessor cycle | No Phase 15 Manual UAT/full rollout until branch-B blockers are green. Branch A may enter UAT only with full/audio scoring disabled and fail-closed proof; UX-05 does not implement direct audio. | `OpenAiCompatibleSpeakingEvaluationClient`, `SpeakingEvaluatorCapability`, `SpeakingProviderRolloutReadiness`, `AiCalibrationReadinessPolicy`, media/playback/cleanup services | Branch B: captured request proves authorized learner audio reached the scorer; calibration/privacy/readiness pass. Branch A: no request can carry audio and readiness remains blocker-red while transcript-only UI is honest. |
| Writing new scoring remains task-native (`Q51/Q52 /10`, `Q53 /30`, `Q54 /50`, `GENERAL /100`); diagnostics are non-additive; local band 1–9 is compatibility only. | `PARTIAL` | `P15-PRE-02/09`; retire band path in `P15-COMP-18` | Current UX stays KSH-practice-labelled; profile/bundle and removal from canonical new-write/runtime/UI must finish before 14A. Any retained read-only adapter gets an explicit expiry; destructive history disposition waits for release closure. | `WritingScoringPolicy`, `WritingScoringRubric`, `WritingEvaluationNormalizer`, `WritingScoreMatrix`, `WritingResultPresenter`, `result/writing.html` | Canonical prompt/provider/new-write/UI scan has no 1–9 inference or band label; task maxima unchanged. Retained/migrated history additionally needs a bounded expiring read adapter. |
| R/L explanations are discriminated by canonical question type, and a new artifact version must use an explicit relational supersession/rebind decision. | `PARTIAL`; typed runtime/renderer `COMPLETE_FOCUSED_GATE_GREEN_13E_02` | `P15-PRE-03/05`; `P15-COMP-10/16`; Phase 13E renderer | Phase 13E owns the typed current runtime/detail contract. Pre-14 owns v2 disposition and append-only active/superseded binding history, either inside the guarded rebaseline after the no-obligation proof or through a separately reviewed forward migration when that guard stops. Final SME/calibration and canonical UAT seed remain Pre-15 work before Manual UAT. | `ReadingListeningExplanationClient`, `QuestionExplanationReadService`, `QuestionVersionExplanationBindingRepository`, Phase 13E objective-detail files | Cross-type schemas are rejected; MCQ/fill/TFNG fixtures render their own evidence; exactly one active compatible binding resolves while prior history is auditable. |
| Rule engines are bounded deterministic signals and cannot manufacture score or acoustic evidence. | `ALIGNED` as design boundary; hardening open | `P15-PRE-04`, bundle identity `P15-PRE-09` | Preserve fail-closed UX now; counterexample hardening is a pre-14 blocker. | `WritingRuleEngine`, `SpeakingRuleEngine`, prompt builders and normalizers | Boundary/Unicode/context counterexamples pass; duplicate signals collapse; rule-only output cannot alter an unrelated criterion or create audio claims. |
| Assessment field language and Korean construct coverage are explicit and task-bounded across W/S/R/L. | Learner-surface/runtime subset `COMPLETE_FOCUSED_GATE_GREEN`; final calibrated acceptance correctly not claimed | `P15-PRE-14`, drawing contract artifacts from PRE-02/03/04/09, UI proof from PRE-06 and later academic proof from PRE-07 | Phase 13E supplies the bounded typed presentation. During Pre-14, version the expanded Writing/Speaking construct/evidence/descriptor/parent contract and typed R/L identity without claiming all Korean. During Pre-15, close SME/calibration. Stable machine IDs remain ASCII. | `SpeakingPromptRules`, `SpeakingEvaluationPromptBuilder`, `SpeakingRubricCriterion`, `SpeakingFeedbackViewMapper`, `WritingPromptRules`, `WritingRubricCriterion`, `ReadingListeningExplanationClient`, normalizers and typed detail DTOs | Pre-14 proof covers task applicability—including Writing Q51/Q52/Q53/Q54/GENERAL—evidence authority, descriptor and parent mapping; adding enums is insufficient. Pre-15 proof adds Korean-SME and calibrated supported-domain acceptance. |
| Result Detail is exactly three screens (Objective R/L, Writing, Speaking); W/S each have four AI-feedback tabs and lecturer reference is separate. | Replacement `COMPLETE_FOCUSED_GATE_GREEN`; design/inventory aligned | `P15-PRE-06`; Phase 13E; cleanup `P15-COMP-10` | Phase 13E implements the replacement. After consolidated validation, pre-14 removes only verified-dead generic paths and either proves lecturer-reference immutable round-trip or keeps it hidden/unreportable with debt. | Legacy `result-detail.html`/`rl-result-detail.html`, `PracticeController`, `PracticePublisherService`, Phase 13E typed DTO/presenters/templates | Exactly one of three screen kinds renders; W/S have four feedback tabs; any visible lecturer reference round-trips outside AI JSON; old generic parser symbols have no references after cleanup. |
| Prompt/rubric/schema/task/profile/evidence identity invalidates cache/reuse and prevents stale reinterpretation. | `PARTIAL` | `P15-PRE-09` with JSON decision `P15-PRE-05`; calibration digest binds at release closure | Pre-14 owns bundle identity and version bumps; Pre-15 calibration acceptance binds the approved corpus/digest without stale reinterpretation. | `WritingEvaluationCacheService`, `SpeakingEvaluationReusePolicy`, prompt rules/builders, normalizers, persisted evaluation envelopes | Mutating every bundle component misses cache/reuse; stored results require exact identity; old payload follows named dual-read/migrate/delete decision. |
| Database/migration/seed/UAT reset is environment-evidence-driven, never an opportunistic Phase 13E deletion. | `REBASELINE_GO_WITH_GUARDS`; execution future | `P15-PRE-05`; `P15-COMP-13/15/16/17/18/19/22`; `P15-KEEP-01/05` | Phase 13E performs no mutation. Pre-14 may rebaseline only after no-obligation proof, final schema freeze and fresh disposable validation. Canonical SME UAT seed loads in Pre-15/Phase 15 before Manual UAT. | V25-V44 audit, attempt/feedback JSON, explanation bindings, smoke/UAT manifests | Stop on any retained/deployed/canonical obligation; otherwise fresh Flyway/Hibernate plus technical smoke proof. No repair/reused DB, no content seed in baseline and no “master DB” claim. |
| Java toolchain and dependency-security state are reproducible, coherent and release-rescanned. | `IMPLEMENTED_PENDING_CONSOLIDATED_VALIDATION_AND_SUPPORT_DECISION`; local IDE is aligned but no green dependency gate is claimed | `P15-PRE-15/16`; pulled-forward candidate, 13H/pre-14 evidence acceptance, pre-15 rescan | Preserve Phase 13F evidence. Validate `.java-version`, compiler release/enforcer and the Boot `3.5.16` plus named security-bridge/direct-pin candidate as one owning unit. Decide the supported production line/commercial support before GO; re-scan in Pre-15 immediately before Manual UAT. | `pom.xml`, `.java-version`, ignored local IntelliJ settings, resolved dependency tree/SBOM and security report | IDE/Maven/run/CLI agree on JDK 17; no initializer failure; dependency resolution and direct-parser regression pass; BOM and support lifecycle are governed; runtime Critical/High findings are fixed or accepted with owner/expiry; final dated rescan is attached. |
| Historical workflow/gate/design statements remain audit history but cannot override current source truth. | `ALIGNED` after supersession notes | `P15-COMP-14` | Phase 13E and Phase 13F are `COMPLETE_FOCUSED_GATE_GREEN`; `13F-01..06` are `IMPLEMENTED_AND_FOCUSED_TESTED`, with `331/331`, schema proof `44/44/0/1` and cleanup/absence `0`. The prior 143-path 13C3 acceptance and failed pre-union 144-path audit attempts are historical for their exact digests; the 163-path union is ready for one consolidated validation, and the two fresh audits are scheduled after green validation/commit/push. Historical ledger rows stay unchanged. | `CODEX_PRACTICE_WORKFLOW.md`, Phase 13 gate/blueprint, design, Phase 13E/13F/13C3 live logs | Current instruction sections identify the exact union validation before coherent commits/push and the two post-push audits, without reopening or downgrading accepted Phase 13E/13F evidence; archival rows remain dated/superseded. |

#### 4.1.2 Two-gate owner / staged execution ledger

`Implementation predecessor` is deliberately separated from `closure /
coordination`. The latter may follow the row and therefore must not be read as
a circular prerequisite. This order is executable: define contracts/policy ->
make data/bundle decisions -> implement dark capability/view -> calibrate and
accept rollout -> remove compatibility paths.

| ID | Accountable owner | Implementation predecessor | Closure / coordinated item | Current execution status |
| --- | --- | --- | --- | --- |
| `P15-PRE-01` | Backend + Academic SME | Branch A disabled: none. Branch B direct audio: PRE-08 policy approval before any evaluator transfer | Pre-15 branch B feeds PRE-07 calibration/readiness; COMP-19 producer removal is pre-14, retained-row destruction is release closure | Transcript guard green; full Speaking `NO-GO`; release-closure decision pending |
| `P15-PRE-02` | Academic contract owner + Writing backend | None; first choose/version the scored construct/profile/descriptors | Feeds PRE-05/09 and pre-14 COMP-18 canonical-path removal; final academic acceptance is PRE-07 later | `NOT_STARTED — PRE_PHASE_14_BLOCKER` |
| `P15-PRE-03` | R/L explanation backend + Phase 13E | Phase 13E defines the discriminated runtime schema | Feeds PRE-05/06 and pre-14 COMP-16 supersession; final SME/calibration is Pre-15 | Runtime subset in Phase 13E; pre-14 v2/data proof pending |
| `P15-PRE-04` | Assessment contract + rule-engine backend | None; define bounded-signal policy and counterexamples | Feeds PRE-09 bundle identity | `NOT_STARTED — PRE_PHASE_14_BLOCKER` |
| `P15-PRE-05` | Data/compatibility owner | PRE-02/03 contract decisions plus mandatory 13C3/13G/13H completion | Pre-14 data/new-write/dual-read/rebind, no-obligation proof and guarded final-state baseline feed PRE-09/COMP-16; retained-data/UAT reset closes in Pre-15 | `REBASELINE_GO_WITH_GUARDS — PLAN_ONLY; NO CURRENT_SLICE_ACTION` |
| `P15-PRE-06` | Phase 13E result-detail owner | Phase 13E typed R/L/display contract; pre-14 cleanup follows named data decisions | COMP-10 legacy detail removal follows consolidated validated replacement | Replacement in Phase 13E; compatibility/reference proof remains open |
| `P15-PRE-07` | Academic SME + backend/readiness | Released bundle/capability exists in dark/shadow form; branch-B acoustic calibration follows PRE-01 | Pre-15 rollout acceptance; branch-A acoustic scope may close `NOT_APPLICABLE_WITH_PROOF` | `NOT_STARTED — PRE_PHASE_15_RELEASE_CLOSURE_BLOCKER` |
| `P15-PRE-08` | Security/Privacy + Backend | None; policy precedes any PRE-01 branch-B transfer | Direct-audio closure in Pre-15; deferred Phase 14 report-attachment privacy is a separate contract closed before 14B | `NOT_STARTED — DIRECT AUDIO NO_GO` |
| `P15-PRE-09` | Assessment-contract backend | PRE-02/03/04 artifacts and PRE-05 data decision | Feeds cache/reuse/result identity and the stable Phase 14 report target | `NOT_STARTED — PRE_PHASE_14_BLOCKER` |
| `P15-PRE-10` | 13H/provider operations | None | Implement/validate in 13H; later provider UAT closure | `PHASE_13H_PREREQUISITE; NOT_REIMPLEMENTED_BY_PRE_14_GATE` |
| `P15-PRE-11` | 13H/performance | None | Implement/validate in 13H; later performance UAT closure | `PHASE_13H_PREREQUISITE; NOT_REIMPLEMENTED_BY_PRE_14_GATE` |
| `P15-PRE-12` | 13H/storage operations | None | Implement/validate in 13H; later operational UAT evidence | `PHASE_13H_PREREQUISITE; NOT_REIMPLEMENTED_BY_PRE_14_GATE` |
| `P15-PRE-13` | 13H PDF/provider operations | None | Implement/validate in 13H; later provider/load UAT | `PHASE_13H_PREREQUISITE; PHASE_13F_GREEN_DOES_NOT_CLAIM_IT` |
| `P15-PRE-14` | Assessment-contract backend, then Korean Academic SME | Phase 13E supplies bounded typed UI/runtime artifacts; PRE-02/03/04/09 complete the governed contract | Pre-14 closes expanded W/S + typed R/L identity; PRE-07 closes final SME/calibration in Pre-15 | Runtime subset in Phase 13E; pre-14 contract and Pre-15 academic proof both pending |
| `P15-PRE-15` | 13H/toolchain owner | None; candidate uses installed JDK 17 | Pulled-forward implementation candidate; close only in one owning consolidated validation, then pre-14 verifies | `IMPLEMENTED_PENDING_CONSOLIDATED_VALIDATION` |
| `P15-PRE-16` | 13H/platform-security owner | PRE-15 candidate toolchain; supported-line decision still required | Pulled-forward BOM/direct-dependency candidate; resolve/audit in one owning validation; pre-14 verifies; pre-15 rescans | `IMPLEMENTED_PENDING_CONSOLIDATED_VALIDATION_AND_SUPPORT_DECISION` |

#### 4.1.3 Guarded Practice rebaseline decision

The migration audit verdict is `REBASELINE_GO_WITH_GUARDS`, not “rename the
files” and not “repair the local database”:

- `question_explanation_*` is R/L-only; Writing uses
  `practice_writing_evaluation_cache`;
- V26 and V39 perform the same exam `MEDIUMTEXT` widening; misleadingly named
  V27 creates the Writing cache;
- V34 is stale squashed residue;
- V37 is unsafe as a legacy upgrade because it ignores V34's
  `question_version_id`, assumes ID/fingerprint/language equivalence and then
  drops the old cache; and
- V44 is a hard-coded local seed repair, not production schema.

Implementation is after mandatory 13C3/13G/13H validation plus commit/push and
inside the separately approved comprehensive `/practice` audit/cleanup
program, after the final pre-14 relational contracts are frozen and before
14A. First pull/reconcile the inventory and prove no retained, deployed,
shared, canonical or upgrade-supported database obligation. Any positive or
unknown obligation is a hard stop: preserve bytes/checksums and design forward
migration instead. The pre-14 gate verifies the resulting integrated evidence;
it does not construct the baseline.

When the guard is green, preserve non-Practice V38-V43 exactly and choose the
next free version at implementation time. `V44__practice_baseline.sql` is only
the proposed name if still free after excluding the local repair and checking
upstream. The new Practice baseline is schema-only final state: immutable
published graph, attempt/media lifecycle, Writing cache with full bundle
identity, R/L artifact/task, append-only active/superseded binding history and
all required runtime tables. It excludes transient create/drop, legacy
backfill/cache IDs/tables, content/demo seed and V44 local repair.

Use a newly named disposable DB; retain old DBs read-only. Never Flyway repair
or reuse an existing schema. Keep validate-on-migrate enabled and clean disabled
except for an explicit disposable-profile allowlist. Exit requires fresh
Flyway and Hibernate validation plus minimal technical R/L/W/S smoke fixtures
with stable report-target identity. The canonical Vietnamese/Korean SME-reviewed
UAT seed remains a Pre-15/Phase 15 artifact loaded before Manual UAT. Global
non-Practice demo seed debt stays separate.

### 4.2 Pre-15 Speaking evidence and score target locked at design level

This subsection locks the future direct-audio evidence boundary for
`PRE_PHASE_15_RELEASE_CLOSURE_GATE`.
The Phase 13D UX correction has implemented and validated the safer current
subset: transcript-only evidence produces a four-criterion
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

### 4.3 Current file ownership by assigned gate

This is the current production change set, based on source review on
`2026-07-20`. Pre-14 changes only the target-stability responsibilities routed
in 4.1; direct-audio/calibration/destructive work remains Pre-15 work before
Manual UAT. It is
intentionally separated from the compatibility-removal set in Section 6. A
later audit may split a class, but it may not silently drop a responsibility.

> UX-05 current-source overlay (`2026-07-22`): the transcript-only portions of
> the Speaking rows below are now `IMPLEMENTED_AND_PHASE_13D_FOCUSED_GATE_GREEN`; their
> older “must change” descriptions remain audit context. Remaining work is the
> authorized direct-audio capability and PRE-07/08/09 evidence. Nothing in this
> historical change map authorizes a current subtotal or holistic score.
> PRE-09 contract identity is now pre-14; direct-audio and PRE-07/08 academic/
> privacy rollout evidence remain Pre-15 requirements before Manual UAT.

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
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringCriterion.java` | Keep the three canonical scored criteria stable; carry profile/criterion metadata needed by prompt, normalizer and result UI. Diagnostic expansion never creates one score row per Korean feature. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoringRubric.java` | Represent the selected task-native rubric/profile as the single source of truth. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingPromptRules.java` | Generate task-applicable diagnostic instructions from the versioned Q51/Q52/Q53/Q54/GENERAL registry, remove contradictory prose and bump prompt/rubric/schema/contract versions atomically. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingRubricCriterion.java` | Own versioned diagnostic/subtype, parent score criterion, evidence authority, impact, frequency, confidence, observability and task applicability; do not advertise evidence that normalization discards. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingRuleEngine.java` | Replace unsafe substring hits with boundary-aware, task/evidence-bounded advisory signals; never manufacture a diagnostic, impact or score outside observable evidence. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingTaskResolver.java` | Prefer explicit immutable task metadata and constrain heuristic prompt detection to a named fallback. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationClient.java` | Send resolved policy/task metadata and build the matching strict schema. The current schema omission of `subtype`, `impact`, `frequency` and `confidence` is mandatory pre-14 runtime debt; keep prompt/payload/schema versions atomic. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationNormalizer.java` | Validate diagnostic subtype/parent, task applicability, evidence authority, impact/frequency/confidence/observability and profile; safely support authoritative task metadata and compute rather than trust derived scores. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingScoreMatrix.java` | Retire local 1–9 conversion/labels under P15-COMP-18; if retained history proves a need, isolate only the required conversion in an expiring read-only compatibility adapter. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationResult.java` | Expose scoring profile, evidence coverage and availability needed by persistence/presentation. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingMockEvaluatorService.java` | Mirror the production contract; a mock must not preserve an obsolete rubric shape. |
| `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationCacheService.java` | Include registry/provider-schema/prompt/normalizer/rule-policy identity in the bundle key so incompatible results miss cache; no blanket table deletion is required. |
| `src/main/java/com/ksh/features/practice/result/WritingResultPresenter.java` | `13E-03` supplies the typed diagnostic UI seam under three stable scored criteria and honest non-exhaustive copy; pre-14 fills the governed registry. Keep legacy results explicitly labelled. |
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
| Canonical result-detail templates/fragments selected by the active Phase 13E implementation | Render per-option, per-blank and TFNG relation evidence without parallel legacy parsing. If `src/main/resources/templates/practice/rl-result-detail.html` remains after the verified replacement, migrate or remove it with `P15-COMP-10`, not as an unreviewed Phase 13E deletion. |

#### D. Skill-native result detail and lecturer reference — future ownership map

This table records both runtime ownership now active under explicit Phase 13E
GO and the remaining future entry audit. Phase 13E may edit only its approved
typed DTO/presenter/template/localization subset; it does not authorize
lecturer-reference database migration, retained-data disposition or legacy
deletion. At the future gate, a file already replaced by 13E is checked through
its canonical successor.
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
| A guarded Practice baseline or a new forward Flyway migration, selected only after the no-obligation proof | Add immutable reference/binding storage through the schema-only rebaseline only when `REBASELINE_GO_WITH_GUARDS` passes; otherwise preserve applied checksums and use a separately reviewed forward migration. A versioned JSON field may avoid a new column, but still needs explicit dual-read and snapshot behavior. |
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
| Writing cache JSON | `WritingEvaluationCacheEntry.java`, `WritingEvaluationCacheService.java`; despite the historical filenames, the current `practice_writing_evaluation_cache` DDL is in `V27__version_question_explanation_cache.sql`, while V26 contains only the exam rich-text widening duplicated semantically by V39 | Pre-14 adds complete bundle identity. Put the final relation directly in the guarded baseline; if the stop condition fires, use a forward migration. Filename repair alone is not a valid rebaseline objective. |
| R/L artifact schema/rebinding | `QuestionExplanationArtifact.java`, `QuestionVersionExplanationBinding.java`, `QuestionExplanationArtifactRepository.java`, `QuestionVersionExplanationBindingRepository.java`, `QuestionExplanationPreparationService.java`, `QuestionExplanationReadService.java` | `question_explanation_*` is R/L-only. Replace bind-if-absent uniqueness with append-only active/superseded history in the guarded baseline, or forward-only when rebaseline is blocked. Prior history remains auditable. |
| Historical Practice schema and seed | Practice V25-V37 plus local-repair V44; non-Practice V38-V43 | V34 is stale squashed residue; V37 legacy backfill is unsafe; V44 is excluded local seed repair. Rebaseline only after no-obligation proof, preserving non-Practice V38-V43 bytes/checksums. Otherwise preserve the chain and stop. |

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
`WHOLE_IMAGE` scope.

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

### 4.7 Two-gate exit checklists

`PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` is `NO-GO` until:

- 13C3, 13G and 13H each have separately accepted consolidated validation
  evidence, a coherent commit series and a successful push;
- the comprehensive `/practice` authority/assessment/code/route/schema audit
  has reconciled all findings, and every approved correction has been
  validated, coherently committed and pushed;
- PRE-02/04/09 and the runtime-contract half of PRE-14 have accepted contract
  evidence; PRE-10..13 have accepted 13H evidence rather than duplicate work;
- canonical new writes/runtime/UI have no Writing 1-9, Speaking word-count fake
  score or Writing mock-score fallback; any required Writing historical adapter
  is read-only, labelled, owned and expiring;
- consolidated Phase 13E validation has already proved the typed replacement,
  then COMP-10 leaves exactly one canonical report-entry screen context;
- typed R/L explanations and COMP-16 resolve one active compatible artifact via
  append-only active/superseded binding history while keeping prior history
  auditable, whether delivered by the guarded baseline or the stop-triggered
  forward-migration path;
- every visible lecturer reference round-trips immutably; otherwise it is
  hidden/unreportable with explicit debt;
- screenshot/audio report attachment privacy and retention are fixed before 14B;
- PRE-05 has per-payload new-write/dual-read/invalidate/rebind decisions, with no
  retained-data deletion; guarded baseline proof or the stop-triggered
  forward-migration decision is recorded;
- fresh Flyway/Hibernate validation runs only on a newly named disposable DB
  with safe config, followed by minimal technical smoke fixtures and stable
  Report-an-Error identity;
- Writing Q51-Q54/GENERAL and Speaking have versioned task applicability, evidence
  authority, descriptor/impact and parent mappings; typed R/L identity is
  stable; neither current Writing IDs nor 16 Speaking subcriteria/examples are
  represented as exhaustive Korean coverage; and
- every conditional COMP-01..09/11..12/14/21 promotion has caller or
  retained-data evidence.

`PRE_PHASE_15_RELEASE_CLOSURE_GATE` is `NO-GO` until:

- final PRE-07 Korean-SME/calibration/fairness/repeatability proof is accepted;
- PRE-01 branch A is disabled with proof, or branch B satisfies PRE-08,
  scorer-consumed audio, dark evaluation and acoustic calibration;
- destructive/environment/schema and retained-data decisions in
  COMP-15/22 are closed after consuming the pre-14 baseline evidence;
- approved disposable-UAT reset evidence is recorded without repair/reuse; and
- remaining compatibility cleanup and premium seed preparation are complete.

After that gate closes, Phase 15 remains `NO-GO` until its
browser/device/provider/load/security/manual-UAT matrix records the final
capability-specific verdict for the initial scope, explicitly excluding Report
an Error.

## 5. Classification

| Status | Meaning |
| --- | --- |
| `REMOVE_CANDIDATE_CONFIRMED` | Static evidence shows the path is unused or contradicts the canonical production contract. The assigned gate still performs caller/retained-data impact review before removal. |
| `REVIEW_REQUIRED` | The path is active or may protect historical data. Remove only after its replacement/data decision is complete. |
| `KEEP` | The behavior is intentional architecture or security and must survive cleanup. |

## 6. Compatibility and legacy inventory

Routing overlay: `P15-COMP-10/16/18/19/20` are mandatory pre-14 under the
conditions in Section 4.1. `P15-COMP-01..09`, `11..12`, `14`, `21` are
conditional pre-14 only with caller/retained-data evidence; otherwise they stay
in Pre-15. `P15-COMP-13/15/17/22` are destructive/environment release closure.

| ID | Status | Current evidence | Assigned gate action | Acceptance proof |
| --- | --- | --- | --- | --- |
| `P15-COMP-01` | `REVIEW_REQUIRED` | `WritingResultPresenter` still locally scores historical non-ESSAY Writing questions from immutable content/answer specs. `PracticeResultPresenterTest.historicalWritingFillBlankUsesLockedAnswerSpecWithoutAiFeedback` pins that behavior in tests. | After confirming no retained attempt requires it, remove the historical non-ESSAY Writing branch and its fixture. Keep immutable ESSAY history. | Every Writing version in the fresh database is Q51-Q54 `ESSAY`; no local objective scoring branch remains in the Writing presenter. |
| `P15-COMP-02` | `CLOSED_2026-08-03` | Historical development-only `PracticeService.NonWritingEssayGradingSnapshot` submit/re-evaluation compatibility was removed under the data-owner reset disposition. | Canonical authoring/publisher rules permit Writing `ESSAY` only; non-Writing subjective types remain fail-closed at submission/re-evaluation. | No non-Writing call path reaches the Writing evaluator; source contract plus service/authoring/publisher gate passed `156/156`. |
| `P15-COMP-03` | `REVIEW_REQUIRED` | `PracticeService` and `SpeakingResultPresenter` retain `speaking_mixed_v1` and `essay_feedback_by_question`. UX-04/05 now keep ESSAY copy legacy/unverified and exclude it from current coverage/readiness/trust, but the mixed compatibility envelope still exists. | Remove the mixed feedback contract and ESSAY merge after P15-COMP-02 and retained-attempt review. | Speaking persistence/result contracts contain only Speaking feedback; no `essay_feedback_by_question` reference remains; retained history has migration/reset proof. |
| `P15-COMP-04` | `REVIEW_REQUIRED` | `SpeakingFeedbackCompatibilityReader`, `SpeakingEvaluationReusePolicy`, `SpeakingEvaluationStatus.LEGACY_RESULT` and `WritingFeedbackCompatibilityReader` retain legacy payload/status branches. | Build a current-payload inventory, migrate or discard test rows, then remove only statuses/readers no longer needed by the current provider contract. | Current provider fixtures still parse; removed legacy statuses have no source/test/schema references. |
| `P15-COMP-05` | `REVIEW_REQUIRED` | `PracticeService.loadProgressAttemptData`, `progressQuestions`, `versionSnapshot` and related result/player paths retain live-question fallbacks for attempts with no version lock. | Reset test data or migrate retained attempts, then make incomplete/missing locks fail closed or appear as an explicit unavailable historical record. | New and seeded attempts all have complete locks; progress/result never query live questions for an attempt. |
| `P15-COMP-06` | `REVIEW_REQUIRED` | `PracticePublishedVersionService` and `PracticeService` retain single-section ungrouped-question compatibility plus synthetic/orphan group rendering. | Canonicalize seed/import data into groups, then remove only fallback branches unsupported by the final authoring contract. | Fresh publication contains no orphan question version; player/result tests use canonical groups only. |
| `P15-COMP-07` | `REVIEW_REQUIRED` | `PracticeRoutes` and `PracticeController` still expose legacy set/mode/room/submit/submission routes and redirect handlers. | Inventory real callers and bookmarks, retain intentional redirects for one documented window or remove them together with route tests. | Each old route is either absent or has one documented redirect contract; no hidden service path depends on it. |
| `P15-COMP-08` | `REVIEW_REQUIRED` | `QuestionTypeResolver`, editor/player JavaScript and templates still accept aliases such as `MCQ`, `MCQ_SINGLE`, `TFNG` and `GAP_FILL`. | Normalize/reset imported fixture data, then shrink runtime authoring/player aliases while preserving only any explicitly supported import boundary. | Persisted live/version question types are canonical; runtime templates do not branch on removed aliases. |
| `P15-COMP-09` | `REVIEW_REQUIRED` | `PracticeAssessmentExcelService` accepts `practice-excel-v1`; `PracticeAssessmentExcelV2Codec` and draft adapters still emit/read legacy `options`, `answerKey` and answer aliases beside canonical content/spec fields. | Decide the supported import window, remove v1/runtime duplicate fields together, and version the final template contract. | One documented Excel schema round-trips canonical fields without duplicate legacy answer sources. |
| `P15-COMP-10` | `MANDATORY_PRE_14_AFTER_CONSOLIDATED_13E_VALIDATION` | Active detail routes still use `PracticeService.getResult`, `getReadingListeningResult`, `PracticeResultView`, `ReadingListeningResultView`, `result-detail.html` and `rl-result-detail.html`. | Do not remove during implementation slices. Only after consolidated Phase 13E validation proves exactly three canonical evidence-detail screens, delete the verified-dead old route/DTO/service/template/parser cluster in one pre-14 patch. | `/attempts/{id}/result/detail` dispatches to exactly one of three typed screen contracts; old generic DTO/template/parser symbols have no references, leaving one canonical Phase 14 entry context. |
| `P15-COMP-11` | `IMPLEMENTED_PENDING_OWNING_VALIDATION` | The pulled-forward cleanup deletes unreferenced `src/main/resources/static/js/practice.js` and removes the two asset-only preservation checks from `PracticeResultWordingTest`. A current-source scan found no template/controller/runtime loader; active Result/Detail behavior remains in the skill-native assets. | Validate `PracticeResultWordingTest` and repeat the exact runtime reference scan once in the owning grouped unit; do not restore the dead generic parser/editor/player bundle merely to satisfy an obsolete test. | No template/resource/controller reference exists before deletion; current result assets cover required wording/behavior. |
| `P15-COMP-12` | `REVIEW_REQUIRED` | Tests still deliberately create historical Writing fill-blank, Speaking ESSAY/mixed feedback, ungrouped questions, no-version attempts, old routes and legacy import aliases. | Classify each fixture as current regression, migration fixture or obsolete test-data compatibility. Delete obsolete fixtures with the production branch they pin. | Test names and fixtures describe only canonical runtime or an explicitly retained migration boundary. |
| `P15-COMP-13` | `PRE_14_REBASELINE_GO_WITH_GUARDS` | Practice V25-V37 is not a coherent final-state chain: V26 duplicates non-Practice V39 semantics, V27 creates the Writing cache under an R/L name, V34 is stale squashed residue and V37 performs an unsafe legacy cache backfill/drop. | After consolidated Phase 13E and final pre-14 schema freeze, prove no retained/deployed/shared/canonical/upgrade obligation. If green, create one schema-only final-state Practice baseline after preserved V38-V43 at the next free version; if unknown/false, stop and preserve checksums/forward-only. | Written no-obligation or stop verdict; preserved V38-V43 checksums; no repair/reused schema; fresh Flyway/Hibernate and technical R/L/W/S smoke identity proof. |
| `P15-COMP-14` | `REVIEW_REQUIRED` | Current and historical docs still mention retired `rl-result.html`, `QuestionExplanationCache`, lazy explanation generation and other superseded paths. Historical phase evidence may be intentionally archival. | Separate current contracts from historical records. Correct current specs/runbooks and preserve historical evidence with explicit superseded labels. | Current specs match source; archival docs are clearly dated and are not used as implementation instructions. |
| `P15-COMP-15` | `REVIEW_REQUIRED` | Local database fixtures and ad-hoc attempts such as `687`/`688` were created for UI investigation and do not form a coherent product seed. | Inventory IDs needed only for debugging, export evidence if required, then reset the dedicated local/UAT database before premium seed loading. | UAT starts from a documented clean database and deterministic seed manifest, not ad-hoc rows. |
| `P15-COMP-16` | `MANDATORY_PRE_14_APPEND_ONLY_BINDING_HISTORY` | Explanation artifact JSON is versionable, but `question_version_explanation_bindings` is unique on `(question_version_id, explanation_language)` and the repository currently binds only if absent. A bound READY v2 artifact therefore has no normal supersession path for v3. | Before 14A implement append-only active/superseded binding history so 14D can correct an explanation without mutating an artifact. Include it in the guarded schema-only baseline only when the no-obligation proof passes; otherwise preserve applied checksums and deliver it through a separately reviewed forward migration. | A version resolves exactly one active compatible artifact while prior bindings remain auditable; stale-schema tests cannot reuse v2 as v3. |
| `P15-COMP-17` | `PRE_14_BASELINE_EXCLUDE_LOCAL_REPAIR` | `V44__practice_seed_listening_check_audio.sql` is a hard-coded local development seed repair, not production schema. | Exclude it from the guarded schema-only baseline. At implementation time use `V44__practice_baseline.sql` only if V44 is genuinely free; otherwise choose the next free version. Keep the listening asset/data in a technical fixture manifest, not Flyway schema. | Baseline contains no content seed/local IDs; technical smoke loader is separate and canonical SME UAT seed is loaded in Pre-15/Phase 15 before Manual UAT. |
| `P15-COMP-18` | `MANDATORY_PRE_14 — RETIRE_WRITING_LOCAL_1_9` | `WritingScoreMatrix` still owns `bands()`, `clampAndRound()`, `toHundredPointScale()` and `bandLabel()`. `WritingEvaluationNormalizer` retains `LEGACY_BAND_V1`, `band_label` and band fallbacks; `WritingScoringPolicy` infers legacy semantics when score `<=9`; `WritingMockEvaluatorService` and `PracticeService` still call the matrix. Focused `WritingScoreMatrixTest`, `WritingEvaluationNormalizerTest`, `WritingScoringPolicyTest`, `WritingTaskNativeScoringTest` and integration fixtures preserve the path. New canonical scoring is Q51/Q52 `/10`, Q53 `/30`, Q54 `/50`, GENERAL `/100`. | After PRE-02/09, remove 1-9 from canonical prompt/provider/new-write/runtime/UI before 14A. If retained history proves a need, isolate only an expiring read-only adapter at the compatibility boundary with owner/removal date; destructive migrate/reset of history waits for release closure. | Canonical production scan has no new-write/runtime/UI `WritingScoreMatrix`, `LEGACY_BAND_V1`, `band_label`, score-`<=9` inference or 1-9 label; failures remain non-score-bearing and task-native maxima unchanged. Any history-only adapter is labelled, read-only and expiring. |
| `P15-COMP-19` | `MANDATORY_PRE_14 — REMOVE_SPEAKING_TEXT_SIMULATED_SCORE`; consumer guards `IMPLEMENTED_AND_PHASE_13D_FOCUSED_GATE_GREEN` | `PracticeService.mockSpeakingFeedback` and three legacy submit/re-evaluate/fallback call sites fabricate `3/5.5/7` from word count, convert it with `WritingScoreMatrix`, emit `text_simulated_mock` / `practice_speaking_mock`, `S_FLUENCY` and `sample_answer`, then can mark an attempt graded. `SpeakingFeedbackCompatibilityReaderTest` retains a mock fixture. Phase 13D now guards current consumers: `PracticeDetailPageService`/test detail show no latest/best number; `PracticeService` excludes Speaking from progress average/trend/type/highlight metrics and nulls score history; `progress.html` and result detail show explicit no-holistic-score copy instead of `0%`. | Preserve consumer guards and remove all canonical producer paths before 14A; unavailable evaluation persists an explicit non-score-bearing result. If old rows are retained, read them only as legacy/unverified with expiry; destructive retained-row cleanup waits for release closure. | Exact source/config/fixture scan has no `mockSpeakingFeedback`, `text_simulated_mock`, `practice_speaking_mock` or word-count Speaking score in canonical runtime; provider-disabled tests assert null score/unavailable; retained historical numbers never re-enter progress/history/latest/best. |
| `P15-COMP-20` | `MANDATORY_PRE_14; IMPLEMENTED_PENDING_OWNING_VALIDATION` | The pulled-forward Writing sidecar removes `WritingMockEvaluatorService` from `WritingEvaluationClient`, deletes `fallbackToMock(...)` and the unreachable branches, and keeps missing-key/HTTP/transport/unexpected paths fail closed. Cache rehydration now also requires current provider provenance, task/scoring contract, the exact unique task-native rubric IDs/names/maxima and internally consistent criterion sum, raw maximum, percentage, score and overall score; legacy/mock/partial/inconsistent JSON is deleted rather than returned before the missing-key guard. The standalone compatibility mock class remains only for the separate `P15-COMP-18` local-1–9 retirement decision and is no longer a client dependency. | Validate timeout/retry/fail-closed and strict cache acceptance once in the owning 13H or grouped pre-14 unit; then the pre-14 gate verifies accepted integrated evidence. Do not include this sidecar in 13C3 evidence and do not describe the compatibility class as a production fallback. | Static source scan finds no unreachable mock fallback; missing key, HTTP, timeout and unexpected failures remain `EVALUATION_UNAVAILABLE` with null score; no mock/legacy/partial or rubric-inconsistent provider result is accepted from cache or persisted as current. |
| `P15-COMP-21` | `REVIEW_REQUIRED — SPEAKING_UNGROUPED_TRANSCRIPTION_DECISION` | `PracticeSpeakingMediaRepository.findAuthorizedTranscriptionCandidates` joins `PracticeQuestionGroup g` and requires `g.id = q.groupId`. Canonical Speaking is grouped, but a retained ungrouped legacy attempt cannot reach transcription even though other result/player fallbacks may still read it. | Owner: Phase 15 compatibility with P15-COMP-06, before any live Speaking provider UAT. Prove no retained attempt needs transcription and remove the broader ungrouped compatibility, or add one explicitly bounded migration path; do not weaken owner/attempt/question/media authorization. | Persisted live/version scan is fully grouped or a migration proof exists; canonical transcription authorization tests remain owner- and immutable-question-bound; no unexplained ungrouped fallback is exercised in Manual UAT. |
| `P15-COMP-22` | `READ_ONLY_LOCAL_EVIDENCE; ROUTE_GATE_GREEN` | `ksh_phase13e_result_ui` reports V44 but lacks `tests.media_type`; it is collided local evidence, not a master/canonical DB. The fresh disposable V1→V44 route gate passed `2/2` and was removed. | Never repair/reuse the collided schema. Preserve it read-only until its export/discard decision; create a newly named disposable baseline/smoke DB and later a separately approved UAT DB. | No “master DB” claim; old schema untouched; pre-14 fresh baseline proof and Pre-15/Phase 15 UAT reset are separately recorded. |

### 6.1 Compatibility decision / dependency / UAT ledger

Every row must be resolved by its assigned gate; this ledger assigns an owner
and prevents a generic “legacy cleanup” ticket from hiding different data
decisions. A conditional row remains in Pre-15 unless caller/retained-data
evidence promotes it into Pre-14.

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
| `P15-COMP-10` | Pre-14 detail owner after consolidated Phase 13E validation; PRE-06 | Prove replacement first, then remove verified-dead generic route/DTO/service/template/parser as one cluster |
| `P15-COMP-11` | 13H/Phase 15 dead-resource cleanup | Final reference scan then remove asset and test-only preservation |
| `P15-COMP-12` | Test/fixture owner; depends on matching production removals | Classify current vs migration vs obsolete; names/expiry and source parity proof |
| `P15-COMP-13` | Pre-14 database owner; no-obligation proof | Guarded schema-only Practice baseline or hard stop/forward-only; preserve V38-V43; fresh Flyway/Hibernate smoke evidence |
| `P15-COMP-14` | Documentation owner; current-source crosswalk | Correct current docs, add supersession labels to history; source/doc scan proof |
| `P15-COMP-15` | UAT environment owner; PRE-05 | Export required evidence then approved clean reset; deterministic seed proof |
| `P15-COMP-16` | Pre-14 R/L artifact owner; PRE-03/05 | Forward supersession/rebind with auditable prior artifact; approved UAT reset is Pre-15/Phase 15 only |
| `P15-COMP-17` | Pre-14 migration/fixture owner | Exclude V44 local seed repair; choose next free baseline version at implementation time; keep fixtures separate |
| `P15-COMP-18` | Pre-14 Writing canonical-path owner; PRE-02/09 | No canonical new-write/runtime/UI 1–9 path; history-only read adapter requires proof and expiry; destructive disposition in Pre-15 |
| `P15-COMP-19` | Pre-14 Speaking producer owner; PRE-01/05 | Remove simulated score producer; provider-disabled null-score proof; destructive retained-row action in Pre-15 |
| `P15-COMP-20` | 13H or pre-14 dead-code owner; COMP-18 | Accept prior 13H evidence or remove remaining unreachable fallback; fail-closed provider error tests |
| `P15-COMP-21` | Speaking data/provider owner; COMP-06 | Group/reset/migrate retained rows without weakening authorization; provider-UAT proof |
| `P15-COMP-22` | Database/release owner; COMP-13/15/17 | Keep collided DB read-only evidence, never repair/reuse; separate pre-14 technical-smoke DB from Pre-15/Phase 15 UAT DB |

## 7. Architecture that must remain

| ID | Status | Required behavior |
| --- | --- | --- |
| `P15-KEEP-01` | `KEEP` | Immutable published version graph and old-attempt isolation. A cleanup may remove unsupported legacy shapes, but must never make an attempt read the newest live question/version. |
| `P15-KEEP-02` | `KEEP` | Canonical `AssessmentContractCodec`, question-type validation and scoring for supported objective types. Only obsolete adapters/aliases are candidates. |
| `P15-KEEP-03` | `KEEP` | Current external-provider response normalization that is still required by tested provider payloads. Compatibility with a live API is not legacy debt merely because it normalizes fields. |
| `P15-KEEP-04` | `KEEP` | Material ownership, immutable reference checks, lifecycle states, private access and audit/security behavior. Data reset does not authorize weakening these controls. |
| `P15-KEEP-05` | `KEEP` | R/L-only explanation artifact, append-only active/superseded question-version binding history, durable task, retry, reconciliation and read-only result lifecycle. Keep the architecture, not V37's unsafe legacy backfill. |
| `P15-KEEP-06` | `KEEP` | The result-detail route until Phase 13E has replaced its implementation and equivalent learner evidence is verified. |

## 8. `PRE_PHASE_15_RELEASE_CLOSURE_GATE` cleanup program

This mandatory pre-Manual-UAT program is distinct from the post-13H comprehensive `/practice`
audit/cleanup. The earlier audit may remove only evidence-proven production
dead/duplicate surfaces and implement correctness prerequisites for release and
the later Phase 14.
It must not pull retained-data destruction, premium canonical seed loading,
provider/load Manual UAT or release-environment cleanup forward from this
section.

Immediately after Pre-14, release closure executes the remaining work as one reviewable cleanup
program, not as isolated deletions discovered during Manual UAT. It consumes
accepted pre-14 evidence and does not reopen those correctness contracts:

1. **Environment freeze and proof**: identify local/UAT/production databases,
   backup retained data, record migration versions and consume the accepted
   pre-14 baseline/no-obligation evidence. Do not reopen the baseline decision.
2. **Inventory resolution**: close remaining/conditional `P15-COMP-*` items as
   `REMOVE`, `MIGRATE`, `RETAIN_WITH_EXPIRY` or `KEEP`, with owner and evidence.
3. **Controlled cleanup batch**: remove obsolete code, route, DTO, template,
   static asset, test, seed and current-doc references together so no parallel
   half-path remains.
4. **Static no-legacy audit**: scan canonical types, route aliases, old feedback
   contracts, no-version fallbacks, dead resources and superseded result/cache
   names. Any retained hit must point to an approved migration boundary.
5. **Database rehearsal**: create/reset only the explicitly approved disposable
   UAT database from the accepted pre-14 baseline and validate its schema.
   Never repair/reuse the old evidence DB.
6. **Automated gate**: compile once, run the smallest focused cleanup/migration
   tests, required integration tests and the full suite once for release
   confirmation.
7. **Release dependency rescan**: consume the accepted PRE-16 baseline, refresh
   the resolved tree/SBOM and advisories, and stop on an unowned exploitable
   Critical/High runtime finding.
8. **Premium seed creation**: load only after the cleanup gate is green.
9. **Phase 15 Manual-UAT handoff**: after the Pre-15 gate closes, run role,
   browser, device, media, immutable-result and AI journeys against the premium
   seed; capture screenshots and the initial GO/NO-GO evidence. That matrix
   excludes Report an Error.

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
- that database is identified as the dedicated UAT database, never as a
  repository-wide “master” database;
- the premium seed manifest matches persisted question types, weights, assets
  and immutable version locks;
- runtime logs show no unexpected compatibility fallback during the UAT matrix;
- old routes are either intentionally documented redirects or return the
  approved unavailable response;
- old ad-hoc attempts are absent from the dedicated UAT database;
- focused, integration and full-suite evidence is recorded once after the
  cleanup batch;
- the final dated dependency/SBOM rescan has no unowned exploitable
  Critical/High runtime finding and records every bounded exception/expiry; and
- Manual UAT screenshots/results identify capability-specific GO/NO-GO status.

## 11. Linked gates

- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, Sections 6.9 and 6.9.12
- `CODEX_PRACTICE_WORKFLOW.md`, current Phase 13E action and both named gates
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`, Pre-14 -> Pre-15 -> Phase 15
  Manual UAT/release -> deferred Phase 14 14A-14F order
