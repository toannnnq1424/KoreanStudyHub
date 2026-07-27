# Practice Phase 13C3 Speaking Prompt Authoring Correction

Last updated: 2026-07-27

> **Post-validation roadmap supersession (`2026-07-27`, documentation-only):**
> the final isolated validation snapshot contains 167 non-excluded paths. It
> supersedes the earlier 163-path pre-validation union without erasing that
> history. Scheduling after Phase 13 now includes the audit-first product and
> package reconciliation phase before Pre-14 -> Pre-15 -> Phase 15
> Manual UAT/release -> deferred Phase 14 14A-14F. No 13C3 scope or validation
> claim is borrowed from that future phase.

## 1. Gate status and execution order

- `PHASE_13C3_DESIGN = LOCKED`
- `PHASE_13C3_IMPLEMENTATION = CONSOLIDATED_VALIDATION_GREEN_PENDING_COMMIT_PUSH`
- `PHASE_13C3_VALIDATION = GREEN_WITH_BROWSER_DEFERRED_TO_END_OF_PHASE_13`
- `13C3-00 = IMPLEMENTED_AND_VALIDATED`
- `13C3-01 = IMPLEMENTED_AND_VALIDATED`
- `13C3-02 = IMPLEMENTED_AND_VALIDATED`
- `13C3-03 = IMPLEMENTED_AND_VALIDATED`
- `13C3-04 = IMPLEMENTED_AND_VALIDATED`
- `PHASE_13F_PREREQUISITE = COMPLETE_FOCUSED_GATE_GREEN`
- `BROWSER_QA = NOT_RUN_USER_DEFERRED_TO_END_OF_PHASE_13`
- `LIVE_STT_TTS = NOT_RUN_NOT_APPROVED`
- `CURRENT_REQUIRED_ACTION = GRANULAR_COMMITS_ONE_PUSH_THEN_TWO_POST_PUSH_AUDITS`
- `PHASE_14 = DEFERRED_POST_MANUAL_UAT_NON_RELEASE_BLOCKING`

This correction is mandatory before Pre-15/Manual UAT and the later Phase 14,
and has the following fixed
execution order:

```text
13C3-00 contract/migration lock
  -> 13C3-01 persistence/provider orchestration
  -> 13C3-02 Editor UI/API
  -> 13C3-03 publish/player/evaluator identity
  -> 13C3-04 compatibility/reconciliation
  -> one consolidated Phase 13C3 validation
  -> coherent 13C3 commit series + one push
  -> two fresh independent post-push audits
  -> Phase 13G + one consolidated validation
  -> coherent 13G commit series + one push
  -> Phase 13H + one consolidated validation
  -> coherent 13H commit series + one push
  -> end-of-Phase-13 browser/device closure
  -> POST_PHASE_13_PRACTICE_PRODUCT_INTEGRATION_AND_PACKAGE_RECONCILIATION
  -> one approved integration validation + coherent commits + one push
  -> comprehensive /practice audit/cleanup with multiple subagents
  -> one audit/cleanup validation + coherent commits + one push
  -> PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE
  -> PRE_PHASE_15_RELEASE_CLOSURE_GATE
  -> Phase 15 Manual UAT/release
  -> deferred Phase 14A-14F
```

The `13C3` name records capability ownership: it corrects the skill-native
Speaking authoring/player contract delivered by 13B/13C. Phase 13F has now
passed its bounded focused gate; its evidence and history remain separate from
this migration/provider/Editor program. Phase 14 remains “Report an Error &
Content Review”, is deferred until after Manual UAT and must not become the
owner of question authoring.

Each implementation unit is a separate Codex task, but the complete correction
is one validation unit:

| Unit | Scope | Dependency |
| --- | --- | --- |
| `13C3-00` | Lock v2 contract, forward migration, compatibility decision, provider-neutral configuration and live-log inventory. | Phase 13F consolidated gate green. |
| `13C3-01` | Lecturer-owned source/artifact/task persistence, asset bindings, STT/TTS adapters, fingerprints, leases, retry and stale-result reconciliation. | `13C3-00`. |
| `13C3-02` | Editor UI/API, upload/manual modes, state polling, preview, explicit generate/regenerate, retry and stale copy. | `13C3-01`. |
| `13C3-03` | Validator, publisher, immutable version context, learner player branches and evaluator question-context identity. | `13C3-01..02`. |
| `13C3-04` | Excel boundary, authorization/cleanup, compatibility reconciliation, tests/docs/diagram inventory and `READY_FOR_PHASE_VALIDATION`. | `13C3-03`. |

No test, compile, build, lint, startup, database/Flyway execution, provider,
browser, `git diff --check` or Git mutation has run for `13C3-00`.

### 1.1 `13C3-00` implementation checkpoint

The static implementation now contains the contract and forward foundation,
but it is not a validated or runtime-integrated capability:

- `QuestionContent` preserves the v1 writer alias and exact v1 shape while the
  codec accepts an explicit `question-content-v2` Speaking contract;
- v2 accepts exactly three product combinations:
  `audio_upload/audio_only/teacher_upload`,
  `manual_text/text_only/none`, and
  `manual_text/text_and_audio/ai_tts`;
- `V45__practice_speaking_prompt_authoring_foundation.sql` is the next free
  additive migration. It defines lecturer-owned source, reusable artifact,
  append-only transcript revision, durable task and immutable version-context
  tables. Retained inactive source data does not block mode switching, while
  owner/operation and artifact input/output asset identities are bound by
  composite foreign keys. It has not been executed;
- a dedicated `manage.speaking` STT/TTS port and configuration boundary is
  disabled by default, bounded for bytes/duration/timeouts/retries/tasks and
  separate from learner-response `speaking-transcription`. TTS keeps the
  original prompt text while hashing its Unicode-NFC view for fingerprinting;
- static specification tests were authored but not run.

`13C3-00` does not switch the existing Editor, Excel, publisher, player or
evaluator to v2. Those integrations remain owned by `13C3-02..04`; current v1
writers remain unchanged so this foundation cannot silently rewrite history.

### 1.2 `13C3-00` static-audit disposition

The first independent correctness and scope/security audits rejected the
candidate on bounded contract issues. One concentrated correction:

- made exact lowercase JSON identities and unknown-field rejection fail closed;
- aligned Java/configuration/database widths, quotas, cooldowns and disabled
  provider semantics;
- verified exact audio hashes and Unicode-NFC TTS fingerprint input while
  preserving original prompt text;
- bound owner, operation and STT-input/TTS-output asset identities through
  composite keys;
- retained inactive source data and historical tasks without blocking a mode
  switch;
- closed SQL `NULL`/three-valued-logic gaps and removed task cascades.

Fresh read-only correctness and scope/security audits both returned
`ACCEPT_STATIC`. This promotes only `13C3-00` to
`IMPLEMENTED_STATIC_ACCEPTED`; `PHASE_13C3_VALIDATION` remains `NOT_STARTED`,
and `13C3-01` owns the next implementation handoff. Neither audit ran tests,
builds, Flyway/database execution, provider/browser calls, `git diff --check`
or a Git mutation.

### 1.3 `13C3-01` static acceptance

The `13C3-01` snapshot is `IMPLEMENTED_STATIC_ACCEPTED`. It adds only the
lecturer-owned persistence and provider-orchestration core needed by
`13C3-02/03`:

- owner/draft/question-client authorization precedes exact source-revision
  mutation; original audio additionally requires its exact draft placement and
  question reference, so knowing an asset/source/artifact/task ID grants
  nothing;
- reusable artifacts are immutable owner-scoped outcomes. Source-local
  cancellation, mode changes, transcript edits and task failures change only
  source/task attachment state; a verified provider completion is the only
  operation that resolves an artifact;
- a source queues an exact TTS artifact ID. Completion rechecks owner,
  operation, fingerprint, source revision, mode, exact prompt hash and exact
  TTS artifact/config identity before attachment;
- one active task key covers each owner/operation/fingerprint. A task row is
  claimed for at most one provider call; every automatic, manual or
  expired-lease retry is a visible successor row carrying cumulative attempt
  count. The owner row serializes cross-node claim, quota and concurrency
  checks. Per-draft concurrency follows each task's still-current
  operation-specific artifact attachments, not its historical origin source;
- the global pessimistic lock order is owner sentinel when needed, ordered
  drafts, ordered sources, reusable artifact, then durable task. Media loading,
  ffprobe, provider HTTP and generated-object storage stay outside the task
  transaction;
- source fan-out is explicit: every source currently bound to a shared
  artifact is locked and advanced together. A binding-set change during the
  short transaction defers before charge or creates a no-provider
  reconciliation successor, so a source cannot remain queued behind another
  source's task;
- STT hashes exact verified original bytes and binds the exact original asset.
  TTS sends the original prompt but fingerprints only its Unicode-NFC view,
  plus the locked owner/provider/model/language/voice/speed/format/contract
  identity. No Korean case, punctuation or whitespace normalization occurs;
- worker and both providers remain disabled by default. Disabled worker,
  disabled provider, missing key/base/model and unsupported provider fail at
  the operational gate before a source, artifact or task is queued. Adapter
  invocation has no internal retry and maps only provider-neutral results and
  public error categories. TTS validates and honors each already-bounded
  per-request voice/speed/format snapshot instead of replacing it with mutable
  global defaults;
- switching from upload to manual text retains the original upload as inactive
  source data. TTS never regenerates, overwrites or replaces it;
- no Editor/API polling facade and no draft-deletion/retention cleanup wiring
  is present. Those remain `13C3-02` and `13C3-04` respectively.

`V45` remains unexecuted. It was reopened only to restore/retain the accepted
exact STT source/version-context input-asset composites and to represent exact
queued TTS artifact identity plus a source-local immutable transcript-revision
pointer. The complete schema remains additive and statically re-auditable; no
new gratuitous migration was added.

The first final concurrency/schema audit found that per-draft concurrency was
charged through the task's historical source instead of its still-current
operation-specific artifact attachments. The first final scope/privacy audit
found that the TTS adapter rejected bounded request snapshots whose
voice/speed differed from mutable global defaults. One concentrated correction
changed the attachment-aware query, added the cross-draft reroute
specification, honored request voice/speed/format through the adapter and added
the non-default snapshot specification. Fresh read-only audits of both
dimensions returned `ACCEPT_STATIC`.

This is static acceptance only. No test, compile/build, lint, application
startup, Docker, Flyway/database execution, provider/API call, browser QA,
`git diff --check` or Git action ran. `PHASE_13C3_VALIDATION` remains
`NOT_STARTED`; the next action is `13C3-02`.

### 1.4 `13C3-02` implementation static acceptance and correction ledger

`13C3-02` is `IMPLEMENTED_STATIC_ACCEPTED`. This is read-only static acceptance
only; it does not claim any validation execution.

Implemented invariants:

- the lecturer-only controller exposes the locked PUT/upload/GET/retry/TTS/
  unlink boundary with source-revision checks and Vietnamese
  `409/422/429/503` mappings;
- PUT, toggle, GET, polling, preview and reload have no provider port. Upload
  stores, registers and verifies one bounded private original without a draft
  material reference outside the short binding transaction. Only after the
  locked exact draft/source recheck does the binding transaction create the
  exact question reference and enqueue STT. TTS is reachable only from the
  explicit Generate/Regenerate command; exact READY reuse is `200`, while active
  queued/processing work is `202`;
- retry accepts only the current retryable failure or current
  `needs_review` STT projection, and preserves cooldown/hourly-quota semantics;
- authorized GET projects opaque media URLs, public states and lecturer-only
  context without source/artifact/task/asset/storage/fingerprint/provider
  request identities;
- current manual text plus voice/speed/format/provider/model/language/contract/
  purpose/retention identity determines TTS currentness. A mismatch projects
  retained generated audio as `Đã cũ`/`Bản cũ`;
- the generic whole-draft autosave is no longer a competing prompt/options
  writer once a v2 source exists. Server-side merge preserves the accepted
  prompt/options authority, generic autosave requires exact (not merely
  non-older) whole-draft version equality, API responses return the current
  draft version, and browser draft state mirrors only accepted API state;
- the Editor has the two locked modes, bounded original upload with real
  browser upload progress, exact original/generated previews and provenance,
  durable polling/retry states, lecturer-only context disclosure, Korean text,
  explicit TTS, text-only copy and delivery-aware preview. Speaking dirty/
  in-flight work participates in the Editor leave, preview, publish and tool-
  navigation lifecycle; node changes capture the old question save before
  deactivation. Same-question responses are monotonic by request order, source
  revision and edit generation, while cross-question responses are discarded
  by client/token guards. Text-only mode disables and hides audio play-limit
  controls and omits them from both preview renderers;
- every dedicated mutation carries both the expected source revision and the
  expected whole-draft version. The server verifies the draft version while
  holding the draft lock before changing source/task/binding state, including
  the post-storage upload bind recheck. The first clean foreground load for a
  client within the page lifetime may establish its initial mutation base;
  revisiting that client and background GET/poll projections update only
  display state and never advance either expected source revision or the
  whole-draft version. A changed source/draft projection, dirty local prompt or
  transcript, late request, or partial projection is ignored while its own
  mutation is in flight and otherwise forces/requires reload. Transcript dirty
  state is cleared only after its mutation response passes all
  revision/version/sequence acceptance checks, and mode/unlink/structural
  actions cannot invalidate that dirty transcript authority. Upload/replace and
  unlink disable transcript editing and all source-invalidating controls from
  the post-flush boundary through request completion, so an edit cannot begin
  between the precheck and the destructive response;
- inactive prompt/config/original/generated data is retained on mode switches;
  unlink removes only the exact current draft reference/source binding.

The first independent API audit rejected retry handling for `needs_review`,
operational-gate ordering, out-of-band stale TTS projection and a long/double-
verification upload transaction. The first UI/privacy audit rejected the
competing whole-draft writer, delivery-mode-blind preview, missing
`retry_wait`, and locked copy/duration/action gaps. One grouped correction
addressed all findings. On the first corrected frozen snapshot
`c3501c6e27adcf55ac9db0b9af240e31ea6ec13f8c01ad440d0710d9014ad36c`,
the fresh API/side-effect audit accepted, while the fresh UI/privacy audit
rejected four bounded lifecycle/state issues: Speaking saves were outside
Editor leave/preview flow, same-question responses could arrive out of order,
question teardown left pending UI/save state, and text-only delivery still
showed audio play limits. A second grouped correction addressed those four
findings. On the twice-corrected frozen snapshot
`80b388203f03c0dc7aa4f74356c16d9a08bb3779225d8a16a1c6c193102a8bb4`,
the API/side-effect audit again accepted. Its paired UI/privacy audit found one
remaining interleaving: a later-issued old GET could advance the request
sequence and suppress an earlier-issued PUT response carrying a strictly newer
source revision. A third grouped correction makes source revision and then
draft version authoritative; request sequence breaks ties only for equivalent
versions, and a focused source-level regression specification locks that
ordering. Both independent audits must re-read the next exact frozen snapshot;
neither earlier acceptance is reused. On the three-times-corrected frozen
snapshot
`8f7a643669abe40657b15e14505be2380821f337d50cbd206ff9182fb9dc2b5d`,
UI/privacy accepted, but API/side-effect review found that a
background projection could still advance both the source cache and global
whole-draft version while local state was partial. A fourth grouped correction
separates read projection from mutation bases and requires the exact locked
whole-draft version on PUT/upload/retry/transcript/TTS/unlink. Both independent
audits must again re-read the next exact frozen snapshot; no earlier verdict is
reused. On the four-times-corrected frozen snapshot
`f660dc0d9c30764893167a8dbaf7b24f32d19e6013dbc3ef8b0e822017cb5526`,
both fresh audits rejected bounded issues: omitted JSON source revisions could
default to zero, revisiting a question could reinitialize an existing mutation
base, raw English validation messages could cross the `422` boundary, and
mode/unlink/tree actions could invalidate a dirty transcript. A fifth grouped
correction makes both JSON concurrency tokens mandatory, initializes a client
base only once per page lifetime, normalizes public `422` copy to Vietnamese,
and flushes/blocks the invalidating actions. Both independent audits must
re-read the next exact frozen snapshot; neither rejection nor any earlier
acceptance is reused. On the five-times-corrected frozen snapshot
`0d062a889396b913c8603232e44c3595423f974e19bcd770b0e36027325e7907`,
both fresh audits found one remaining race at their respective boundary. The
API/concurrency audit found that upload storage created the exact draft
material reference before the post-storage locked revision/version recheck.
The UI/state audit found that transcript controls remained editable after the
clean precheck while upload or unlink was in flight, allowing a newly dirty
transcript to be stranded by the accepted destructive response. A sixth
grouped correction now stages a verified private asset without a material
reference and creates the exact reference only inside the locked binding
transaction; it also holds a source-destructive UI lock from after flush until
`finally`. Focused static specifications cover both boundaries. Both auditors
independently re-read the six-times-corrected frozen snapshot
`10a9c8ea687ac469d70e043d0207b99d7ab99c770ae407dc3bada852c0c689fb`
without reusing any earlier verdict. The API/auth/revision/idempotency/
side-effect/transaction audit returned `ACCEPT_STATIC`, and the UI/state/
privacy/accessibility/scope audit returned `ACCEPT_STATIC`; neither reported a
blocker or edited the snapshot. The accepted `13C3-04` limitation is that a
failed or stale post-storage bind can leave an unbound private asset for later
cleanup, but never a stale question binding.

Exact `13C3-02` source ledger:

- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringController.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringControllerAdvice.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringStateService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptOriginalAudioUploadCoordinator.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAutosaveAuthorityMerger.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptDraftAuthority.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAssetService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTranscriptService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSource.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSourceRepository.java`
- `src/main/java/com/ksh/features/practice/manage/service/LecturerAssetService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java`
- `src/main/java/com/ksh/features/practice/manage/controller/PracticeDraftController.java`
- `src/main/java/com/ksh/features/practice/repository/PracticeMaterialReferenceRepository.java`
- `src/main/resources/templates/practice/manage/editor.html`
- `src/main/resources/static/js/practice/manage-authoring-contract.js`
- `src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js`
- `src/main/resources/static/css/practice/manage-speaking-prompt-authoring.css`

Exact authored/updated specification ledger:

- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringControllerTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringStateServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringEditorStateTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAutosaveAuthorityMergerTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAssetAuthorizationTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptDraftVersionConflictTest.java`
- `src/test/java/com/ksh/features/practice/manage/SpeakingPromptAuthoringUiContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java`

Direct current-source documentation ledger:

- `CODEX_PRACTICE_WORKFLOW.md`
- `docs/PRACTICE_PHASE_13C3_SPEAKING_PROMPT_AUTHORING_LIVE_CHANGE_LOG.md`
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`

Explicit deferrals:

- `13C3-03` still owns validator/publisher, immutable version context,
  learner player and evaluator switching. No learner runtime is switched here;
- `13C3-04` still owns physical/orphan cleanup, draft deletion/retention
  reconciliation, Excel boundary and whole-correction gate preparation. A
  verified upload whose final locked revision/version recheck loses a race may
  leave only an unbound private asset for that later reconciliation; it cannot
  create a stale question reference, and this slice never physically deletes
  it;
- V45 is unchanged and unexecuted. Published v1 rows and old v1 reads are not
  rewritten.

No unit/integration/full test, compile/build, Maven/Gradle, lint, application
startup, Docker, browser QA, database/Flyway execution, provider/API call,
`git diff --check` or Git action has run for `13C3-02`.

### 1.5 `13C3-03` static acceptance

`13C3-03` is `IMPLEMENTED_STATIC_ACCEPTED`. This records exact
read/edit/static-reasoning evidence only; it does not claim validation or
runtime evidence. The first frozen implementation/specification snapshot was
`d1644da67b184900a285c67c9a9dee8f0f82e81884775405568ab9fc9deefc21`;
the digest covers the exact production and static-specification files in the
ledgers below.

The first fresh audits both returned `REJECT_STATIC`. The publication audit
found that reusable artifact provenance revision was incorrectly compared with
the currently locked source revision, and that retained inactive upload assets
were copied into manual contexts contrary to V45. The player/evaluator audit
found that malformed explicit v2 content could be downgraded to a v1 fallback
in learner delivery and lecturer preview. One grouped correction now:

- treats artifact input revision as immutable creation provenance while
  verifying the current locked source revision/attachment, exact asset,
  fingerprint, text/config and contract identity;
- keeps retained inactive upload assets only on mutable authoring state and
  writes `NULL` original asset identity for both manual context shapes;
- fails closed on explicit malformed v2 player/preview content instead of
  inventing a historical v1 branch; and
- clears the previous prompt-audio subtree before every learner question so a
  following text-only branch has no stale playback control in its accessibility
  tree.

Both independent audits must re-read the next exact frozen snapshot; neither
earlier verdict is reusable. The grouped-correction implementation/
specification snapshot is
`704f68eb3989132d744e2f9c82b79d788d2459aa696f5e56d971ffb3151e655d`;
its digest uses the production ledger followed by the specification ledger in
the exact order below.

On that snapshot the fresh publication/transaction/context audit returned
`ACCEPT_STATIC`, while the fresh player/evaluator audit returned
`REJECT_STATIC`: the configured evaluator prompt defaults still named v3 while
the current immutable-context prompt contract is v4, so an otherwise exact new
result could not satisfy the current-contract/reuse guard. The bounded
correction aligns both supported defaults with
`speaking-eval-v4-immutable-context-transcript-language-only` and adds a
default-identity/current-contract/reuse specification. Because the source
snapshot changed, both audits must run fresh again; the backend acceptance is
not reused. The resulting exact implementation/specification snapshot is
`af3ad33cee080c366e52463d7e801089e583c654e0200691e3d8f029592e9458`.

On that snapshot the fresh publication/transaction/context audit again
returned `ACCEPT_STATIC`, while the fresh player/evaluator audit returned
`REJECT_STATIC`: text-only preview correctly omitted playback, its control and
its limit but still displayed a decorative tile labelled `Audio đề bài`. The
bounded correction now derives that entire tile from the shared presenter's
`PROMPT_PLAYBACK` step, so it remains on both audio branches and is absent from
text-only, and adds a focused static UI specification for that condition. Both
audits must re-read the new frozen snapshot; neither verdict is reused. The
resulting exact implementation/specification snapshot is
`455b546d085e9114d8c582f182a163391a4a25c75240c2925f6e68c3cdfeb4f4`.

Both required independent audits re-read that final exact snapshot. The
validator/publisher/transaction/immutability/v1-v2/context audit returned
`ACCEPT_STATIC`, and the player/evaluator/privacy/reuse/scope audit returned
`ACCEPT_STATIC`; neither edited the snapshot or reused an earlier verdict.
Therefore `13C3-03 = IMPLEMENTED_STATIC_ACCEPTED`,
`PHASE_13C3_VALIDATION = NOT_STARTED`, and the only next action is `13C3-04`.

Implemented invariants:

- publication accepts only upload/audio-only/teacher-upload,
  manual/text-only/none and manual/text-and-audio/AI-TTS. Upload requires the
  exact verified original/active asset, current READY STT artifact and matching
  source revision/fingerprint; low-confidence STT additionally requires the
  lecturer's current confirmation. Text-only requires exact nonblank
  Korean-capable canonical text and no TTS artifact, audio or play limit.
  Text-and-audio requires the exact current verified READY/SYNCED generated
  artifact matching text, config, source revision and fingerprint;
- publication does not call STT/TTS or mutate source, task or artifact state.
  It locks the current draft/source/artifact identities, writes learner-safe
  `question-content-v2`, promotes only the exact current lecturer asset and
  persists exactly one immutable `SpeakingPromptVersionContext` for each new
  immutable question version in the same publication transaction. Republish
  creates a new context; existing question versions, references and v1 attempts
  are not rewritten;
- immutable context uses the confirmed uploaded-audio transcript or exact
  canonical manual prompt, includes the accepted provenance contract and
  verifies its SHA/fingerprint plus source/draft/question-version identity
  before flush. Evaluator context resolution for v2 reads only this immutable
  row; the historical v1 branch remains deterministic from immutable
  question-version content and performs no backfill;
- preview and learner delivery share one backend presenter. Its exact branches
  are audio playback/preparation/recording, text plus TTS playback/preparation/
  recording, and text-only preparation/recording. Text-only has no invented
  playback limit. Browser autoplay rejection exposes an explicit play action
  and does not silently advance;
- learner DTO/HTML/JavaScript expose only delivery text, playable material URL,
  public timings/step tokens and recording state. They do not serialize
  transcript/context, provider, task, artifact, fingerprint, storage or
  lecturer-private identities;
- evaluator input carries immutable lecturer `promptContext` and learner media
  `transcription` as separate authorities. Reuse identity now includes exact
  question-version, prompt-context fingerprint and context-contract identity.
  Context cannot become learner evidence or acoustic evidence. Transcript-only
  fluency/pronunciation remain `NOT_SCORABLE`; direct-audio Speaking rollout
  remains `NO-GO`.

Exact `13C3-03` production source ledger:

- `src/main/java/com/ksh/features/practice/assessment/AssessmentContractCodec.java`
- `src/main/java/com/ksh/features/practice/assessment/SpeakingPromptDelivery.java`
- `src/main/java/com/ksh/features/practice/assessment/SpeakingPromptDeliveryPresenter.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptContextIdentity.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptVersionContext.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSourceRepository.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptPublicationService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptEvaluationContextService.java`
- `src/main/java/com/ksh/features/practice/service/PracticePublishedVersionService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftPreviewService.java`
- `src/main/java/com/ksh/features/practice/manage/validator/PracticeDraftValidator.java`
- `src/main/java/com/ksh/features/practice/service/PracticeService.java`
- `src/main/resources/static/js/practice/manage-authoring-contract.js`
- `src/main/resources/static/js/practice/manage-draft-preview.js`
- `src/main/resources/templates/practice/manage/editor.html`
- `src/main/resources/static/js/practice/player-speaking.js`
- `src/main/resources/templates/practice/player-speaking.html`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationService.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationIdentity.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationNormalizer.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationOrchestrator.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilder.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationRequest.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationResult.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingPromptRules.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluatorProperties.java`
- `src/main/resources/application.properties`

Exact authored/updated static specification ledger:

- `src/test/java/com/ksh/features/practice/assessment/AssessmentContractCodecTest.java`
- `src/test/java/com/ksh/features/practice/assessment/SpeakingPromptDeliveryPresenterTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptVersionContextTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptEvaluationContextServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptPublicationServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/SpeakingPromptPublicationTransactionContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/validator/PracticeDraftValidatorTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeDraftPreviewServiceTest.java`
- `src/test/java/com/ksh/features/practice/PracticeSpeakingMediaUiResourceTest.java`
- `src/test/java/com/ksh/features/practice/manage/SpeakingPromptAuthoringUiContractTest.java`
- `src/test/java/com/ksh/features/practice/service/SpeakingPlayerPayloadPrivacyTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilderTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationReusePolicyTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationServiceTest.java`
- `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java`

Direct current-source documentation ledger:

- `CODEX_PRACTICE_WORKFLOW.md`
- `docs/PRACTICE_PHASE_13C3_SPEAKING_PROMPT_AUTHORING_LIVE_CHANGE_LOG.md`
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`
- `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`

Explicit `13C3-04` deferrals remain unchanged: Excel stays outside this slice;
draft deletion, physical/orphan asset cleanup, retention/reconciliation,
whole-diff compatibility inventory, V45 execution and consolidated validation
are not implemented here. V45 remains additive, unchanged and unexecuted.

No unit/integration/full test, compile/build, Maven/Gradle, lint, application
startup, Docker, browser QA, database/Flyway execution, provider/API call,
`git diff --check` or Git action has run for `13C3-03`.

## 2. Product objective and non-goals

Lecturer Editor must support exactly two ways to create a Speaking prompt:

1. `audio_upload`: the lecturer uploads the original prompt audio. KSH stores
   and plays that exact audio to the learner and runs Speech-to-Text (STT) so
   the evaluator can understand the prompt. The transcript is internal AI
   context only. KSH must not call Text-to-Speech (TTS) for this mode.
2. `manual_text`: the lecturer enters prompt text. The lecturer may leave
   `Tạo audio đề bài bằng AI` off for a text-only question, or turn it on and
   explicitly generate TTS audio, preview it and regenerate when needed.

The product purpose is to let a Speaking task assess listening comprehension
and spoken response when the lecturer deliberately supplies audio. It does not
mean every Speaking question must test listening: `manual_text` with TTS off is
a supported text-only delivery.

This correction does **not**:

- make the prompt transcript visible to learners;
- use the prompt transcript as the learner answer;
- send learner response audio directly to the scorer;
- claim to score pronunciation, fluency, intonation or other acoustic
  constructs from STT;
- regenerate or replace lecturer-uploaded audio;
- call providers from autosave, GET, preview playback, publish or page reload;
- create a parallel generic Assessment Editor;
- turn Excel import into a bulk TTS billing surface.

## 3. Current-source contradiction and supersession

The active authoring/player source remains audio-only:

- `QuestionContent.SpeakingDelivery` and `AssessmentContractCodec` in
  `src/main/java/com/ksh/features/practice/assessment/QuestionContent.java`
  now define and dual-read the v1/v2 contract, but current Editor/Excel writers
  still emit the historical prompt-audio/timing-only v1 shape;
- `PracticeDraftValidator.validateSpeakingDelivery` blocks publication without
  a material audio reference;
- `PracticeService.toSpeakingPlayerQuestion` throws when immutable prompt audio
  is absent;
- `static/js/practice/player-speaking.js` always starts with `playPrompt()`;
- `templates/practice/manage/editor.html` exposes only a required upload;
- the historical Phase 13B/13C record says text-only Speaking is unacceptable.

Those statements remain valid evidence for the already accepted v1 behavior,
but they are superseded for new writes by the v2 mode-dependent policy in this
document. They must be labelled historical rather than silently deleted.

There is also a media-policy mismatch to close: Editor upload currently accepts
MP3/WAV/M4A/OGG/WebM up to 50 MB, while the learner-answer transcription path
has a narrower media/size configuration. The authoring STT boundary must publish
one explicit accepted-media policy and must not pretend every uploaded audio
file is already compatible with its provider.

## 4. UI/UX contract

### 4.1 Shared layout

The Speaking section begins with a two-option segmented control:

- `Tải file audio`
- `Nhập nội dung bằng văn bản`

Switching mode never deletes data immediately and never calls a provider. It
updates the draft source revision, shows the inactive source as retained draft
data, and requires an explicit confirmation only when the lecturer chooses to
discard that source.

Timing controls remain shared below both modes:

- prompt play limit, applicable only when active delivery has audio;
- preparation seconds;
- response seconds;
- lecturer reference answer, governed separately from prompt delivery.

### 4.2 Audio upload

The audio branch contains:

- drag/drop and file picker with the exact supported types/size;
- upload progress and a playable original-audio preview;
- filename, duration when verified, source badge `Audio của giảng viên`;
- transcript status chip;
- an expandable panel labelled
  `Ngữ cảnh cho AI — học viên không nhìn thấy`;
- `Thử lại chuyển giọng nói`, `Thay file` and lecturer confirmation when the
  transcript is low confidence.

Required copy:

> Học viên nghe file gốc này. Bản chép lời chỉ giúp AI hiểu đề bài; KSH không
> tạo lại hoặc thay thế audio của giảng viên.

STT is enqueued once after a successful verified upload. The upload response
must not wait synchronously for the provider.

### 4.3 Manual text

The manual branch contains:

- required prompt textarea;
- toggle `Tạo audio đề bài bằng AI`;
- optional approved voice/speed controls when TTS is on;
- explicit `Tạo audio` or `Tạo lại audio` action;
- job state, playable preview and generated-audio provenance.

Toggle off:

- copy says `Câu hỏi chỉ sử dụng văn bản`;
- no audio is required;
- no TTS request is created.

Toggle on:

- turning the toggle on does not call TTS;
- only the explicit Generate/Regenerate action can enqueue TTS;
- the lecturer can preview generated audio before publish;
- editing text, voice, speed, format, model or contract version immediately
  marks existing audio `Đã cũ`;
- stale audio may remain playable with a visible `Bản cũ` badge but cannot be
  published as current.

### 4.4 Loading, retry and error presentation

Backend machine states are mapped to Vietnamese UI states:

| Machine state | UI |
| --- | --- |
| `idle` | `Chưa xử lý` |
| `queued` | `Đang chờ` |
| `processing` | `Đang xử lý` |
| `ready` | `Sẵn sàng` or `Đồng bộ` |
| `needs_review` | `Cần giảng viên kiểm tra` |
| `stale` | `Đã cũ — cần tạo lại` |
| `failed_retryable` | `Tạm thời chưa xử lý được` + Retry |
| `failed_final` | `Không thể xử lý tệp/nội dung này` |
| `superseded` | hidden from current state; retained in audit history |
| `cancelled` | `Đã huỷ` |

The screen preserves the last saved source and current job state after refresh.
It does not fake progress percentages. Errors distinguish invalid input,
missing configuration, quota/rate limit, timeout/transport, provider rejection,
empty/malformed output and stale completion without exposing provider secrets.

## 5. Data and immutable-contract model

### 5.1 Learner-safe `question-content-v2`

New writes use `question-content-v2`. Existing v1 published questions and
attempts remain dual-read and immutable.

```json
{
  "schemaVersion": "question-content-v2",
  "speakingDelivery": {
    "inputType": "audio_upload",
    "deliveryMode": "audio_only",
    "promptAudioReference": "/practice/materials/123/content",
    "audioOrigin": "teacher_upload",
    "promptPlayLimit": 1,
    "preparationSeconds": 30,
    "responseSeconds": 60
  }
}
```

Allowed values:

- `inputType`: `audio_upload | manual_text`;
- `deliveryMode`: `audio_only | text_only | text_and_audio`;
- `audioOrigin`: `teacher_upload | ai_tts | none`.

The values are not freely cross-combinable:

| Input | Delivery | Audio origin | Meaning |
| --- | --- | --- | --- |
| `audio_upload` | `audio_only` | `teacher_upload` | play the verified original upload; prompt transcript stays internal |
| `manual_text` | `text_only` | `none` | show the immutable prompt text; no provider/audio requirement |
| `manual_text` | `text_and_audio` | `ai_tts` | show prompt text and play the verified current generated asset |

Every other cross-mode combination fails closed at the codec and immutable
version-context schema boundary.

The learner-visible manual prompt stays in `PracticeQuestion.prompt` and is
snapshotted to `PracticeQuestionVersion.prompt`. Transcript, task status,
fingerprint, confidence and provider metadata are forbidden in
`QuestionContent`, `PlayerQuestionPayload`, rendered HTML and learner JSON.

### 5.2 Draft source state

Add `practice_speaking_prompt_sources`, uniquely keyed by
`(draft_id, question_client_id)`. `question_client_id` is the stable draft
identity before a database question ID exists.

| Field group | Required data |
| --- | --- |
| identity | `id`, `draft_id`, `question_client_id`, `owner_lecturer_id` |
| mode | `input_type`, `tts_enabled` |
| text identity | `manual_text_sha256`; the actual current manual text remains the draft question `prompt`, updated in the same application transaction |
| audio | `original_audio_asset_id`, `generated_audio_asset_id`, `active_audio_asset_id` |
| artifacts | `current_stt_artifact_id`, `current_tts_artifact_id` |
| states | `transcript_status`, `audio_sync_status`, `lecturer_transcript_confirmed_at` |
| concurrency | `source_revision`, optimistic version |
| audit | creator/updater and created/updated timestamps |

There must not be two mutable authorities for manual text. The draft question
`prompt` is canonical; the source row records its exact hash/revision and the
task input snapshot. Service and publisher reject a hash mismatch.

### 5.3 Reusable AI artifacts and durable tasks

Add `practice_speaking_prompt_ai_artifacts`:

- lecturer owner and operation `stt | tts`;
- owner-scoped operation fingerprint;
- exact input revision/hash;
- provider/model/language;
- TTS voice/speed/format when applicable;
- prompt-authoring contract version;
- transcript text or generated asset ID;
- confidence/provenance;
- status, public error category and timestamps.

Add `practice_speaking_prompt_ai_tasks`:

- artifact/source IDs and expected source revision;
- `queued | processing | retry_wait | succeeded | failed | superseded |
  cancelled`;
- attempt count, next attempt time;
- lease owner and lease expiry;
- retryability/public error;
- requested-by and timestamps.

Use one active task per owner-scoped fingerprint. Learn claim/lease semantics
from `QuestionExplanationGenerationTask`, but do not reuse its R/L table or
domain. Provider calls follow:

```text
claim in short transaction
  -> read verified source outside transaction
  -> provider call outside transaction
  -> verify output media/response
  -> complete in short transaction only if source revision + fingerprint match
```

A late result is retained as `superseded` audit evidence and never attached to
the new source.

### 5.4 Assets and immutable evaluator context

Both lecturer upload and TTS output use `LecturerAsset` plus
`PracticeMaterialReference`:

- upload: `sourceType=MANUAL_UPLOAD`;
- generated: `sourceType=AI_TTS`;
- both remain private and owner/draft scoped until publish;
- publication promotes/binds only the active verified asset;
- republishing never overwrites or deletes an asset retained by an older
  immutable version.

Add `practice_speaking_prompt_version_contexts`, keyed by
`question_version_id`, containing:

- input type and prompt-context source;
- immutable transcript or manual-text context snapshot;
- context SHA-256;
- original and active audio asset IDs;
- STT/TTS artifact/provenance and contract versions.

This table is evaluator/audit-only. It is the authority that lets the scorer
distinguish the question prompt context from the learner response transcript.

### 5.5 Explicit v1/v2 and transcript disposition

| Data | Disposition |
| --- | --- |
| published v1 question/version/attempt | exact dual-read; never rewritten, backfilled to v2 or used to trigger an authoring provider |
| new 13C3 Speaking publication | v2 only after `13C3-03` wires validator/publisher/player; `13C3-00` supplies the accepted codec/schema foundation without changing current writers |
| lecturer uploaded prompt transcript | original provider text plus append-only correction revisions; evaluator prompt context only |
| manual prompt text | draft `PracticeQuestion.prompt` is the sole mutable authority; source stores its exact SHA-256/revision, immutable version stores the exact snapshot/context identity |
| learner response transcription | remains learner-answer evidence under the existing learner media/transcription authorization boundary |

A lecturer prompt transcript cannot populate `LearnerAnswer`, learner response
transcription, criterion evidence or acoustic evidence. Conversely, learner
`PracticeSpeakingMedia` cannot be stored as a lecturer prompt asset.

## 6. API and provider flow

Use a dedicated lecturer-authoring boundary; do not modify generic
`/upload-audio` to transcribe every Listening/group audio.

| Endpoint | Behavior |
| --- | --- |
| `PUT /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt` | Save mode, text hash, TTS toggle/config and expected source plus whole-draft versions. No provider call. |
| `POST .../speaking-prompt/audio` | Verify/store original audio, recheck expected source plus whole-draft versions, bind asset and enqueue one STT task; return `202`. Never call TTS. |
| `GET .../speaking-prompt` | Lecturer-authorized current source/artifact/task state for reload/polling. No provider call. |
| `POST .../speaking-prompt/transcription/retry` | Manual retry only for a current retryable/needs-review source and exact draft version, with rate limit. |
| `POST .../speaking-prompt/tts` | Explicit Generate/Regenerate for current manual text/config and exact draft version; idempotently return existing task/artifact or `202`. |
| `DELETE .../speaking-prompt/audio` | With exact source/draft versions, unlink the current draft asset; never delete an asset referenced by a published version. |

HTTP behavior:

- `200` for an exact ready artifact reused without charge;
- `202` for queued/in-progress work;
- `409` for stale expected revision, mode mismatch or publish sync conflict;
- `422` for unsupported/empty/no-speech content;
- `429` for KSH quota/rate limit;
- `503` for disabled/missing provider configuration or bounded temporary
  provider unavailability.

STT may reuse the low-level provider transport only after it is separated from
learner-response media resolution and authorization. Lecturer prompt audio must
never become `PracticeSpeakingMedia`, and learner recording must never become a
lecturer asset.

TTS is a new provider-neutral port with bounded connect/read timeouts, output
type/size/duration verification and a fake transport for automated validation.
No provider-specific response shape may leak above the adapter.

## 7. Avoiding unnecessary provider calls

STT fingerprint:

```text
owner + source audio SHA-256 + language + provider + model
  + transcription contract version
```

TTS fingerprint:

```text
owner + exact Unicode-NFC text + language + provider + model
  + voice + speed + format + TTS contract version
```

Normalization may standardize Unicode NFC and line endings. It must not
lowercase, strip Korean punctuation, collapse meaningful whitespace or otherwise
change pronunciation/prosody.

Cost invariants:

- no provider call from GET, autosave, preview, publish, toggle or reload;
- double-click, multiple tabs and multiple nodes converge on one task/artifact;
- re-upload/generate with the exact current fingerprint reuses a ready artifact;
- dedupe is lecturer/tenant scoped; never reuse private text/audio/transcript
  across owners;
- bounded automatic retry only for 429, 5xx and transport/timeout failures;
- 4xx, malformed/empty output and unsupported media do not retry forever;
- per-lecturer/draft quota, cooldown and concurrency are enforced before claim;
- logs contain IDs/categories, never API keys, raw private audio or full prompt
  text.

## 8. Save, preview, regenerate and publish

Save draft is allowed in pending, stale or failed state. Publication is stricter:

| Mode | Publish condition |
| --- | --- |
| `audio_upload` | Verified original audio exists; current STT is `ready`; current artifact fingerprint matches audio; low-confidence transcript has lecturer acknowledgement or the audio is replaced. TTS is forbidden. |
| `manual_text`, TTS off | Nonblank text exists; delivery is `text_only`; no TTS task/audio is required. |
| `manual_text`, TTS on | Nonblank text exists; current TTS artifact/audio is verified and `synced`; fingerprint matches current text/config. `queued`, `processing`, `stale` and either failed state block publish. |

Preview must consume the same presenter/delivery DTO as the learner player. It
must not reproduce delivery logic in Editor JavaScript.

Learner state machine:

- `audio_only`: prompt playback -> preparation -> recording;
- `text_and_audio`: show text and play prompt -> preparation -> recording;
- `text_only`: show text and skip `playPrompt()` -> preparation -> recording.

An autoplay block exposes an explicit play action. It never silently advances.
The evaluator request uses:

- `promptContext`: immutable lecturer prompt/transcript context;
- `transcription`: learner-answer transcript.

`questionVersionId` and prompt-context fingerprint become part of
`SpeakingEvaluationIdentity` and reuse policy so feedback cannot be reused
against a changed prompt.

## 9. Security, privacy and lifecycle

- All endpoints authorize draft ownership or existing lecturer collaboration;
  knowing draft/client/asset/task IDs grants no access.
- Transcript panels are lecturer-only and excluded from learner HTML, JSON,
  network payloads and accessibility tree.
- Source audio and generated audio use existing private storage/content
  verification/access boundaries.
- Draft deletion cancels or supersedes current tasks and schedules cleanup only
  for assets/artifacts with no retained reference.
- Switching mode, replacing/deleting audio and editing text increment source
  revision before any async result may attach.
- Provider purpose/retention configuration is recorded per artifact; secrets and
  signed storage internals are not persisted in public DTOs.
- Lecturer edits to an STT transcript record original provider transcript,
  corrected context, editor, time and confirmation; history is not overwritten.

## 10. File inventory

### 10.1 Exact `13C3-00` changed-file ownership

Contract and v1/v2 static reconciliation:

- `src/main/java/com/ksh/features/practice/assessment/QuestionContent.java`
- `src/main/java/com/ksh/features/practice/assessment/AssessmentContractCodec.java`
- `src/main/java/com/ksh/features/practice/assessment/PlayerQuestionPayload.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftContractService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftPreviewService.java`

Provider-neutral authoring foundation:

- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiContract.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSttPort.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTtsPort.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringAiProperties.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties.example`

Forward schema and unexecuted specifications:

- `src/main/resources/db/migration/V45__practice_speaking_prompt_authoring_foundation.sql`
- `src/test/java/com/ksh/features/practice/assessment/AssessmentContractCodecTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringFoundationMigrationTest.java`

Canonical current-source ledgers:

- `CODEX_PRACTICE_WORKFLOW.md`
- `docs/PRACTICE_PHASE_13C3_SPEAKING_PROMPT_AUTHORING_LIVE_CHANGE_LOG.md`
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`
- `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`

No entity, repository, worker, controller/API, Editor JS/CSS/template,
publisher/player/evaluator integration or provider adapter is owned or claimed
by `13C3-00`.

### 10.2 Exact `13C3-01` file-necessity and duplication ledger

All unqualified production names in the Keep list resolve under
`src/main/java/com/ksh/features/practice/manage/speaking/`; all unqualified
focused specification names resolve under
`src/test/java/com/ksh/features/practice/manage/speaking/`.

Keep — bounded lecturer authoring domain:

- `SpeakingPromptSource.java`,
  `SpeakingPromptAiArtifact.java`,
  `SpeakingPromptTranscriptRevision.java`,
  `SpeakingPromptAiTask.java` and
  `SpeakingPromptVersionContext.java`: exact V45 JPA mappings for mutable
  per-source attachments, reusable outcomes, append-only transcript history,
  one-call durable attempts and the immutable identity handed to `13C3-03`;
- the matching five `*Repository.java` files: only the pessimistic source,
  artifact and task claims, owner/fingerprint insertion, current-source fan-out
  and immutable mapping persistence used by this slice;
- `SpeakingPromptDraftAuthority.java`,
  `SpeakingPromptAuthoringService.java` and
  `SpeakingPromptTranscriptService.java`: exact owner/draft/client/revision
  authorization, mode-specific enqueue/cancel/retry commands and source-local
  transcript revision selection; no HTTP or polling state facade;
- `SpeakingPromptFingerprintService.java`,
  `SpeakingPromptTaskTransactions.java`,
  `SpeakingPromptWorkLoader.java`,
  `SpeakingPromptAiTaskProcessor.java` and
  `SpeakingPromptAiTaskWorker.java`: locked fingerprints, short transactions,
  source-safe fan-out, one-row/one-call attempts, provider IO outside
  transactions, bounded successors and disabled-by-default scheduling;
- `SpeakingPromptAiContract.java`,
  `SpeakingPromptAuthoringAiProperties.java`,
  `SpeakingPromptSttPort.java`, `SpeakingPromptTtsPort.java` and
  `SpeakingPromptAudioVerifier.java`: provider-neutral private payloads, exact
  bounds and operational gating separated from learner responses;
- `SpeakingPromptAssetService.java`,
  `FfprobeSpeakingPromptAudioVerifier.java`,
  `OpenAiSpeakingPromptSttAdapter.java` and
  `OpenAiSpeakingPromptTtsAdapter.java`: only authoring-specific
  owner/draft/question asset policy, accepted-media policy and provider result
  mapping. They do not own generic storage, HTTP or ffprobe parsing;
- `SpeakingPromptAuthoringConflictException.java`: the narrow optimistic
  source-revision conflict signal required by later API mapping.

Collapse — narrow shared primitives instead of parallel copies:

- `src/main/java/com/ksh/features/practice/manage/service/LecturerAssetService.java`
  plus
  `src/main/java/com/ksh/features/practice/repository/LecturerAssetRepository.java`
  retain
  storage, SHA dedupe, rollback compensation, generated-audio registration,
  material-reference linkage and lifecycle ownership; the Speaking wrapper
  adds only its exact question boundary;
- `src/main/java/com/ksh/features/practice/service/audio/OpenAiAudioHttpTransport.java`
  owns shared bounded RestClient construction and timeouts;
  `src/main/java/com/ksh/features/practice/ai/speaking/transcription/OpenAiSpeakingTranscriptionClient.java`
  now uses it while
  retaining its learner authorization/result/retry semantics;
- `src/main/java/com/ksh/features/practice/service/audio/FfprobeAudioProbe.java`
  owns the shared runner/JSON/stream/duration parser;
  `src/main/java/com/ksh/features/practice/service/audio/FfprobeSpeakingAudioInspector.java`
  retains learner media policy and
  `src/main/java/com/ksh/features/practice/manage/speaking/FfprobeSpeakingPromptAudioVerifier.java`
  retains authoring policy;
- `src/main/java/com/ksh/features/practice/repository/PracticeDraftRepository.java`
  supplies the shared draft row lock;
  `src/main/java/com/ksh/features/practice/ai/metrics/PracticeAiMetrics.java`
  adds only the two bounded authoring feature tags;
- `src/main/resources/application.properties`,
  `src/main/resources/application-local.properties.example` and the reopened
  unexecuted
  `src/main/resources/db/migration/V45__practice_speaking_prompt_authoring_foundation.sql`
  are the single configuration/schema authorities.

Focused static specifications, authored but not run:

- `SpeakingPromptAuthoringContractTest.java`;
- `SpeakingPromptAuthoringFoundationMigrationTest.java`;
- `SpeakingPromptAssetAuthorizationTest.java`;
- `SpeakingPromptPersistenceInvariantTest.java`;
- `SpeakingPromptProviderAdapterTest.java`;
- `SpeakingPromptRetryPolicyTest.java`;
- `SpeakingPromptTaskOrchestrationTest.java`.

Canonical current-source updates:

- `CODEX_PRACTICE_WORKFLOW.md`;
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
- `docs/PRACTICE_PHASE_13C3_SPEAKING_PROMPT_AUTHORING_LIVE_CHANGE_LOG.md`;
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`;
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
- `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`.

Remove/defer — no material `13C3-01` boundary:

- `SpeakingPromptAuthoringStateService` and polling/state DTOs are deferred to
  `13C3-02`;
- `SpeakingPromptDraftCleanupService`,
  `SpeakingPromptGeneratedAssetCleanup` and `PracticeDraftService` cleanup
  wiring are deferred to `13C3-04`;
- adapter-local retry loops/config, artifact reset/fail/cancel/supersede
  mutators, in-place task retry reset, unused polling/cleanup repository
  methods and copied storage/OpenAI/ffprobe implementations were removed;
- the only retained candidate-discard/rollback seam compensates a verified TTS
  object when the current provider completion fails closed. It is part of the
  current one-call orchestration, not later draft-retention cleanup.

### 10.3 Whole-correction inventory for later slices

Existing files expected to change:

- `src/main/java/com/ksh/features/practice/assessment/QuestionContent.java`
- `src/main/java/com/ksh/features/practice/assessment/AssessmentContractCodec.java`
- `src/main/java/com/ksh/features/practice/assessment/PlayerQuestionPayload.java`
- `src/main/resources/static/js/practice/manage-authoring-contract.js`
- `src/main/resources/templates/practice/manage/editor.html`
- `src/main/java/com/ksh/features/practice/manage/controller/PracticeDraftController.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftContractService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftPreviewService.java`
- `src/main/java/com/ksh/features/practice/manage/validator/PracticeDraftValidator.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java`
- `src/main/java/com/ksh/features/practice/manage/service/LecturerAssetService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelV2Codec.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelService.java`
- `src/main/java/com/ksh/features/practice/service/PracticeService.java`
- `src/main/resources/templates/practice/player-speaking.html`
- `src/main/resources/static/js/practice/player-speaking.js`
- `src/main/resources/static/css/practice/player-speaking.css`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationService.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationRequest.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilder.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationIdentity.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationReusePolicy.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties.example`

Add narrow controller/service/DTO/entity/repository boundaries for Speaking
prompt authoring, STT/TTS artifact/task orchestration, fingerprinting, worker
claim/completion and reconciliation; add dedicated JS/CSS:

- `static/js/practice/manage-speaking-prompt-authoring.js`
- `static/css/practice/manage-speaking-prompt-authoring.css`

`13C3-00` added the next available forward migration as unexecuted `V45`.
Do not add another migration unless a concrete mapping blocker requires it, and
never edit an applied checksum. A later guarded Practice-only rebaseline may
consume the accepted final schema only if its existing no-obligation stop
conditions pass.

Excel is explicitly limited in this correction: it remains Speaking
audio-upload-only, writes v2 delivery and states the limitation. Import never
auto-calls TTS. Manual text plus optional TTS remains Editor-only until a
separately approved bulk-cost/preview design exists.

Tests to update/read include contract codec, Phase 11 authoring UI, draft
controller upload security, draft contract/preview/validator/service/publisher,
Excel, asset ownership/reference/material access, learner player/service,
Speaking UI resources/integration and evaluation request/identity/reuse.
Add focused tests for TTS adapter, fingerprint/idempotency, concurrent claim,
stale completion, authorization, cleanup and v1-to-v2 dual-read.

## 11. Acceptance criteria

1. Lecturer can upload supported original audio, preview the same stored asset
   and save; STT never changes or regenerates that audio.
2. A successful upload enqueues one STT task and shows durable status after
   refresh; exact repeated input/config does not create another charge.
3. Prompt transcript is available to authorized lecturers/evaluator only and is
   absent from every learner payload/surface.
4. Lecturer can create, publish and complete a text-only Speaking question with
   zero TTS request.
5. Enabling TTS does not call a provider until explicit Generate.
6. Generated audio is verified, previewable and bound to the question only when
   its fingerprint matches the current text/config.
7. Editing text/config immediately makes old audio stale; stale audio cannot
   publish, and Regenerate produces/reuses the correct current artifact.
8. `audio_upload` never invokes TTS; `manual_text` never requires STT.
9. Double submit, multiple tabs and concurrent workers produce at most one
   active charge per owner-scoped fingerprint.
10. A provider result for an old source revision cannot overwrite the new
    source.
11. Save permits pending/failed drafts; publish rejects missing, pending, stale,
    failed or unconfirmed required artifacts with Vietnamese actionable errors.
12. `audio_only`, `text_only` and `text_and_audio` enter the correct learner
    state machine without answer leakage.
13. Evaluator input proves prompt context and learner transcript are separate;
    context identity changes invalidate result reuse.
14. Old v1 published questions/attempts keep their exact audio/timing and never
    trigger STT/TTS from GET/player/result.
15. Unauthorized lecturers cannot read source, transcript, task or asset state.
16. Draft/session/orphan cleanup leaves every asset retained by material,
    source, task/artifact or immutable published context unchanged and never
    deletes it logically or physically.
17. Accepted upload types, byte limits and provider compatibility are identical
    across UI, controller, verification and STT configuration.
18. Missing key, timeout, 429/5xx, invalid media, no speech, empty transcript,
    malformed response and invalid generated audio have bounded, correctly
    retryable Vietnamese states.
19. Korean Unicode, punctuation, spacing and line breaks survive storage,
    fingerprinting, TTS and immutable snapshot without lossy normalization.
20. One approved cost-bounded real STT/TTS smoke is recorded before Phase 15
    Manual UAT if
    the capability is enabled for production; default automated validation uses
    fake transports and makes no live provider call.

## 12. Mandatory edge-case inventory

- empty, corrupt, oversized, mislabeled MIME and zero-duration audio;
- MP3/WAV/M4A/OGG/WebM compatibility and provider conversion policy;
- no-speech and low-confidence transcript;
- text edit while TTS is running;
- audio replace/delete while STT is running;
- mode switch while either task is queued/running;
- generated output arrives after draft question deletion or `clientId` change;
- two tabs save different source revisions;
- duplicate upload and text returning to an older exact fingerprint;
- provider disabled/missing key, quota exhausted, timeout, 429, 4xx, 5xx,
  transport failure, empty/malformed output;
- application restart or expired worker lease;
- source asset retained by an old published version;
- lecturer collaboration revoked while a task is in progress;
- old v1, new v2 and copied-question/new-`clientId` behavior;
- Unicode NFC/NFD and Korean whitespace/punctuation;
- learner autoplay denied and text-only no-audio branch;
- draft autosave conflict and browser refresh during generation.

## 13. Deferred validation inventory

No implementation-unit test is authorized. After `13C3-00..04` are complete,
the coordinator must reread this log, reconcile every file/edge case and report
`READY_FOR_PHASE_VALIDATION`. The one validation unit runs:

1. `git diff --check`;
2. one JDK 17 compile/build;
3. the smallest combined contract/service/security/UI/integration selector that
   covers all changed boundaries;
4. one disposable fresh Flyway/Hibernate validation because this program adds a
   forward schema;
5. one explicit browser journey for each delivery mode and stale/regenerate;
6. only when separately approved and cost-bounded, one real STT/TTS smoke.

For `13C3-00`, the explicit not-run list is: unit/integration/full tests,
compile/build, Maven/Gradle, lint, application startup, Docker, browser QA,
database or Flyway execution, provider/API calls, `git diff --check`, and every
Git staging/commit/push/pull/merge/rebase action.

A validation failure is handled as one batch:

```text
analyze the complete failure set
  -> group root causes
  -> one concentrated fix cycle
  -> rerun the same validation unit once
```

Phase 13G cannot start until the complete correction is accepted. Pre-15 and
Phase 15 Manual UAT cannot start until 13G, 13H and the pre-14
production-correctness gate also pass; deferred Phase 14 follows UAT.

## 14. `13C3-04` reconciliation candidate

Status at this snapshot:

- `13C3-04 = IMPLEMENTED_STATIC_ACCEPTED`;
- `PHASE_13C3_IMPLEMENTATION = READY_FOR_PHASE_VALIDATION`;
- `PHASE_13C3_VALIDATION = NOT_STARTED`;
- Phase 13 remains `OPEN`;
- V45 remains additive and unexecuted. Its task-to-source FK is corrected in
  place because current draft deletion proves the accepted `NOT NULL` composite
  FK was a blocker: task creator `source_id` is now nullable, the composite
  `(source_id, owner)` FK remains exact, and lifecycle teardown explicitly
  nulls it under lock before source deletion. Immutable owner/artifact/
  operation/fingerprint/source-input/revision/requester identity remains
  retained. The applied V34 lifecycle table gains the proven nullable
  `claim_token` column plus exact storage-key indexes required for bounded
  cleanup/registration locking. No new migration exists and no applied
  checksum is edited.

### 14.1 Excel disposition

- Modern `practice-excel-v2` writes exactly `question-content-v2` with
  `audio_upload + audio_only + teacher_upload` for Speaking. Workbooks
  historically exported as `practice-excel-v1` remain on the exact
  case-sensitive legacy sheet reader; the current template endpoint emits v2
  only and there is no current v1 export endpoint. The historical four-field
  `question-content-v1` Speaking shape remains exact in the immutable
  contract/player dual-read. A legacy workbook that cannot identify
  per-question verified audio remains blocked instead of being silently
  upgraded or used to fabricate provider/private state.
- A missing or malformed modern workbook schema declaration fails closed.
  Explicit malformed `question-content-v2` in immutable player reads also
  fails closed even when the JSON syntax itself is damaged; it cannot enter
  the historical v1 adapter.
- Excel resolves only an existing, verified, active, private
  `AUDIO + MANUAL_UPLOAD` lecturer asset already authorized through the exact
  locked draft upload or same-question staging/original reference. Unknown,
  external, extra/unreferenced override, wrong-owner, unverified, deleted,
  non-audio, non-private and non-manual references fail before draft
  persistence/linkage.
- Excel creates no Speaking source, artifact, task, transcript, provider
  request or hidden private identifier. The workbook and lecturer page state
  in Vietnamese that Excel is upload-only, never enables/calls TTS, and that
  each imported Speaking question must be opened in Editor for its exact
  source/audio verification/STT handoff before publication.
- Editor GET exposes only an authorized `excelStagingAudioAvailable` boolean,
  never staged asset/reference/storage identity. A separate explicit
  Vietnamese button submits only expected draft/source revisions. Its
  coordinator reauthorizes the exact draft/question, resolves the sole staging
  reference internally, verifies bytes outside the transaction, then rechecks
  the same asset/reference under the locked bind transaction before converting
  staging to the original binding and enqueueing STT. This user action is not
  Excel import and has no TTS path.

### 14.2 Lifecycle, authorization and retention disposition

- `PracticeDraftService` locks the authorized draft before source teardown.
  Autosave compares the normalized live `clientId` set with ordered locked
  sources; removed/copied/changed IDs lose only their exact source-local
  bindings. Draft deletion and empty-draft cleanup tear down all exact sources
  before the draft row.
- Source-local cancel, mode switch, original unlink and question/draft teardown
  detach the exact source revision/artifact first. A sole-current active task
  becomes terminal `cancelled`; a task with another exact owner-local source
  attachment remains reusable and routes through the retained attachment.
  Deleted historical creator sources become `NULL` on task rows without
  weakening owner/artifact/fingerprint identity. Restart/expired provider
  leases retain the accepted visible-successor behavior.
- Copied Speaking questions receive a new `clientId` and an explicit
  unconfigured `manual_text + text_only + none` state with no copied audio or
  presentation identity. Publication remains fail closed until the new
  question is configured in Editor.
- A failed/stale bind leaves only a verified private `TEMPORARY` upload with a
  24-hour staging deadline. Successful exact locked bind promotes it to
  `ACTIVE` and clears staging retention. Active dedupe reuse has no staging
  deadline. Excel-imported audio is already an exact active private upload and
  remains only a question-scoped staging reference until the lecturer performs
  the explicit Editor adoption command.
- One central logical-and-physical deletion guard covers material references, current
  source original/generated/active assets, artifact input/generated assets and
  immutable version-context original/active assets. User deletion and
  import-session cleanup first authorize the exact owner/session, lock the
  exact asset row and recheck this guard. Any retained asset remains completely
  unchanged; only an unreferenced row can become `DELETION_PENDING`. A
  lifecycle delete claims the exact task and locks every asset row sharing its
  physical storage key in deterministic ID order. It verifies the candidate's
  `DELETION_PENDING` state and rechecks every candidate/sibling reference and
  sibling lifecycle state before storage I/O. A retained or still-usable
  sibling, or a late retained-reference finding, defers the same durable task
  to a bounded hourly recheck without consuming storage-failure attempts and
  without archiving or otherwise mutating any asset row. If a movable sibling
  later receives a fresh key, the surviving candidate task becomes eligible
  and completes physical cleanup.
- Irreversible storage deletion is outside database transactions. Claim and
  complete/retry are separate `REQUIRES_NEW` transactions. Every claim writes
  a durable random token plus ten-minute lease; a fresh `RUNNING` row cannot
  be reclaimed, and a stale worker cannot complete/retry over its successor.
  Asset-specific and non-asset cleanup lock every row using the exact storage
  key at claim and immediately before delete. Every new temporary,
  draft-upload, generated-audio or promotion store uses a fresh UUID physical
  namespace and accepts only a newly created result inside it; an old or
  lease-reclaimed worker therefore cannot already target the new exact key.
  Registration then takes the inverse cleanup-task key lock: pending cleanup
  becomes obsolete and every running cleanup rejects registration regardless
  of lease age. Due work is selected by `nextAttemptAt` then stable ID, so
  long-retained deferred tasks rotate behind already-due newer cleanup instead
  of starving it. Storage failures use the existing bounded retry
  count/backoff.
- The bounded prompt retention worker removes only artifacts older than the
  local 30-day orphan window when there is no current source attachment,
  active task or immutable version context. It deletes terminal task/revision
  rows in FK order, then hands now-unreferenced private manual/TTS assets to the
  central lifecycle queue. Generated TTS bytes immediately gain a private
  `TEMPORARY / AI_TTS` row with the same 24-hour unbound window as manual
  staging, so restart before task completion leaves durable, bounded cleanup
  evidence. It performs no provider call.
- Asset link paths and every logical-delete decision take the same parent asset
  row lock and reject deleted/non-linkable state. Publication deterministically
  locks every parent asset before inserting an immutable reference and never
  revives archived/deleted state. Replacing original or generated question
  audio deletes only prior bindings for the exact draft/placement/client ID;
  after commit, opaque old asset IDs enter a fresh centralized reference
  recheck. This closes bind/publish/replacement-versus-cleanup races without
  touching another question or published version. Every managed Excel override
  must name a material reference actually present in the imported workbook and
  a private verified manual upload already attached to the locked linked draft;
  Speaking further requires its exact same-question
  upload/staging/original identity.
  Knowing another owner-private asset ID is insufficient. Public ID knowledge
  never grants access. Revocation immediately removes collaborator
  read/mutate/media authority but does not corrupt owner-scoped durable work
  or immutable published evidence.
- The generic lecturer asset PATCH delegates to the same locked service
  boundary. It authorizes the exact owner, rejects every retained asset before
  changing even display metadata, and treats asset type/status as immutable
  verified/lifecycle identity. Equal type/status inputs are compatibility
  no-ops; an attempted transition fails closed.
- PDF-import manual-copy and attach-to-existing routes lock the exact
  session-linked source draft before any new/target mutation. A source with a
  Speaking prompt row, any draft material reference, explicit v2 Speaking
  delivery/authoring identity or unverifiable JSON fails closed. The flow does
  not copy client/audio authority and does not directly delete a source-bearing
  draft; pristine import-only drafts keep the historical bounded flow.
- Every test/section/group/question add, duplicate, move and delete action in
  Editor awaits the dedicated Speaking flush before normalizing, copying or
  mutating `DRAFT_DATA`. A failed or slow flush therefore cannot be overtaken by
  generic autosave, and copied questions receive their fresh identity only from
  the latest synchronized source state.
- Old published question versions/attempts and their contexts/assets are never
  rewritten, backfilled, mutated or physically deleted.

### 14.3 Whole-correction compatibility and scope

- Accepted v1 rows remain exact dual-read. All new Speaking writes use v2 and
  the only valid combinations in Section 3. Explicit v2 is always fail closed.
- Prompt transcript/context remains lecturer/evaluator-only and never appears
  in learner payloads. Evaluator identity/reuse remains bound to immutable
  question version plus exact context/contract identity.
- Transcript-only pronunciation/fluency/acoustic rows remain `NOT_SCORABLE`;
  no aggregate acoustic Speaking score is created. Live audio-grounded
  Speaking AI remains `NO-GO` until the separate audio-capable evaluator,
  privacy, calibration and approval work.
- P15-COMP, PRE_PHASE_14/PRE_PHASE_15, academic calibration, learner-answer
  audio scoring, 13G, 13H, Phase 15 and deferred Phase 14 remain outside this
  correction.

### 14.4 Exact `13C3-04` file/necessity ledger

Acceptance-criteria reconciliation (implementation/specification disposition;
execution remains deferred):

| # | Static disposition |
| ---: | --- |
| 1 | Original upload remains the same verified stored asset; STT only produces private transcript context. |
| 2 | Owner/fingerprint uniqueness, durable task state and refresh projection remain; lifecycle does not duplicate shared work. |
| 3 | Transcript/context stays lecturer/evaluator-only and is absent from learner delivery. |
| 4 | `manual_text + text_only + none` publishes without a TTS task. |
| 5 | TTS enablement is configuration only; Generate remains the sole provider command. |
| 6 | TTS output verification and exact text/config fingerprint binding remain fail closed; generated bytes immediately gain a retention-bounded private staging row. |
| 7 | Text/config edits detach stale TTS work; replacement removes only the prior exact-question binding and queues its old asset after commit. |
| 8 | Upload mode has no TTS path; manual mode has no STT requirement. |
| 9 | Owner-operation-fingerprint active uniqueness plus locked recheck protects double submit/tabs/workers. |
| 10 | Source revision/current attachment checks reject late provider completion. |
| 11 | Draft saves accept pending/failed state; publisher retains actionable Vietnamese fail-closed errors. |
| 12 | Shared presenter retains exact audio-only, text-only and text+audio learner state machines. |
| 13 | Immutable prompt context and learner transcript identities remain separate in evaluator/reuse input. |
| 14 | Historical v1 player/attempt rows remain exact dual-read and make no authoring provider call. |
| 15 | Draft/source/media routes retain actor authorization; ID knowledge alone grants no access. |
| 16 | Owner/session/orphan cleanup, generic metadata mutation and publication use the same parent asset lock; material/source/artifact/immutable-context retention leaves the asset unchanged and prevents logical/metadata/physical mutation. Physical deletion additionally locks every historical sibling row sharing the exact storage key at claim and final confirmation, and defers a durable bounded recheck if any sibling state/reference still needs the bytes. |
| 17 | Existing upload verifier/config bounds remain the single accepted audio contract. |
| 18 | Existing bounded provider categories/retry successors remain; cleanup retry is separately bounded, and retained-key deferral is ordered by due time then ID so permanent retention cannot starve newer eligible cleanup. |
| 19 | Existing NFC byte/text identity and immutable context retain Korean punctuation/spacing/line breaks. |
| 20 | Live STT/TTS is `NOT_RUN_NOT_APPROVED`; the gate may run it only with separate cost approval. |

Mandatory-edge reconciliation:

- invalid/oversized/MIME/duration/no-speech/low-confidence/provider error and
  Unicode cases remain covered by accepted 13C3-00..03 specifications;
- queued/running text edit, audio replacement, unlink, mode switch and
  question/draft deletion now detach exact source state and cancel only
  sole-current work;
- copied/new-clientId, deleted clientId, autosave conflict, multiple tabs,
  stale completion, refresh, restart/expired leases and old fingerprint reuse
  have explicit identity/lifecycle seams; fresh physical namespaces prevent an
  old worker from targeting a later allocation, and abandoned TTS output keeps
  a bounded private staging row;
- linked Excel replacement, same-clientId Speaking-to-non-Speaking changes and
  same-clientId audio replacement run the same locked source/staging teardown
  before the merged draft save;
- structural add/duplicate/move/delete waits for the dedicated Speaking flush;
  PDF-import copy/attach rejects source/material/v2 identity before target
  mutation or source deletion;
- published-version retention and revoked-collaborator denial remain fail
  closed without cancelling another owner-current source;
- historical/content-addressed sibling asset rows sharing one storage key are
  locked together at lifecycle claim and final confirmation; an active or
  referenced sibling prevents physical I/O without changing either row, while
  the candidate task survives for bounded recheck and becomes eligible after a
  movable sibling receives a fresh key; due-time-first selection prevents a
  permanently retained low-ID prefix from starving newer cleanup;
- learner autoplay and all three delivery branches remain owned by the shared
  accepted presenter/player specifications.

Production kept/changed because a current call-site or invariant requires it:

- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelV2Codec.java`
  — explicit v2 upload tuple, declared workbook schema and Vietnamese contract.
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelService.java`
  — locked linked-draft replacement lifecycle plus fail-closed exact
  draft-referenced private verified audio resolution with no AI seam.
- `src/main/java/com/ksh/features/practice/manage/service/LecturerAssetService.java`
  — staged upload retention, Excel resolver, exact owner/session row-locked
  logical-delete/metadata decision, fresh physical allocation namespaces,
  durable generated-audio staging and centralized lifecycle handoff.
- `src/main/java/com/ksh/features/practice/manage/controller/PracticePdfImportApiController.java`
  — generic asset PATCH delegates to the centralized locked/retained-safe
  service instead of mutating an ID-loaded entity.
- `src/main/java/com/ksh/features/practice/manage/service/PracticeImportDraftService.java`
  — both PDF-import copy/attach routes lock and prove a pristine import-only
  source before any target mutation or source delete.
- `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceService.java`
  and
  `src/main/java/com/ksh/features/practice/repository/PracticeMaterialReferenceRepository.java`
  — parent-asset lock/status check, deterministic fail-closed publication locks
  before immutable reference insert, plus exact draft-reference proof.
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java`
  — locked question/draft lifecycle wiring.
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleProcessor.java`
  and `PracticeAssetLifecycleTaskExecutor.java` — bounded orphan discovery and
  storage I/O outside transactions.
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskTransactions.java`,
  `PracticeAssetReferenceGuard.java` and `PracticeAssetOrphanReconciler.java`
  — smallest new claim/reference/staging seams, deterministic all-sibling
  storage-key locks, retained-state no-mutation rechecks and final pre-I/O
  proof; no copied storage logic.
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptLifecycleService.java`,
  `SpeakingPromptRetentionService.java` and `SpeakingPromptRetentionWorker.java`
  — exact source teardown and bounded orphan-artifact retention.
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringService.java`,
  `SpeakingPromptAssetService.java`,
  `SpeakingPromptOriginalAudioUploadCoordinator.java`,
  `SpeakingPromptAuthoringController.java`,
  `SpeakingPromptAuthoringStateService.java`,
  `SpeakingPromptTaskTransactions.java` and `SpeakingPromptAiTask.java` —
  sole-versus-shared cancellation, ID-free Excel-staging adoption, staged
  bind, exact prior question-binding retirement with post-commit cleanup,
  detachable historical creator identity and current-source successor routing.
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSourceRepository.java`,
  `SpeakingPromptAiArtifactRepository.java`,
  `SpeakingPromptAiTaskRepository.java`,
  `SpeakingPromptTranscriptRevisionRepository.java` and
  `SpeakingPromptVersionContextRepository.java` — only the exact reference,
  retention and FK-safe queries used by those services.
- `src/main/java/com/ksh/features/practice/repository/LecturerAssetRepository.java`
  and `PracticeAssetLifecycleTaskRepository.java` — exact row locks, bounded
  expiry selectors, due-time-first fair cleanup selection, storage-key
  registration serialization and lease-token stale-running recovery.
- `src/main/java/com/ksh/entities/PracticeAssetLifecycleTask.java` — durable
  lifecycle claim token/lease plus non-terminal retained-key deferral state, so
  competing workers cannot share a claim and a later key release cannot lose
  its cleanup candidate.
- `src/main/java/com/ksh/features/practice/service/PracticeService.java` —
  syntax-damaged explicit-v2 declaration still cannot downgrade to v1.
- `src/main/resources/templates/practice/manage/editor.html` and
  `src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js`
  — copied Speaking identity reset plus the explicit Vietnamese, ID-free
  Excel-staging adoption affordance.
- `src/main/resources/templates/practice/manage/excel-import.html` — actionable
  Vietnamese upload-only/Editor handoff.

Focused specifications authored/updated, intentionally not executed:

- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelSpeakingBoundaryTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeDraftContractServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/LecturerAssetServiceOwnershipTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeImportDraftOwnershipTest.java`;
- `src/test/java/com/ksh/features/practice/manage/SpeakingPromptAuthoringUiContractTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskExecutorTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskTransactionsTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetReferenceGuardTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetOrphanReconcilerTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptLifecycleServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptRetentionServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptCollaborationRevocationTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAssetAuthorizationTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringControllerTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringStateServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringFoundationMigrationTest.java`;
- `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java`.

Unchanged regression dependencies intentionally retained in the consolidated
selector, not misreported as authored/updated:

- `PracticeAssessmentExcelControllerTest` for exact linked-draft route
  authorization;
- `PracticePdfImportApiControllerTest` for the unchanged authenticated route
  and response contract around the newly centralized asset mutation;
- `PracticeDraftServiceTest` for the pre-existing draft save/delete contract
  surrounding the newly authored lifecycle specifications;
- `OpenAiSpeakingTranscriptionClientTest`,
  `FfprobeSpeakingAudioInspectorTest`,
  `SpeakingEvaluationNormalizerTest`,
  `SpeakingEvaluationOrchestratorTest` and `SpeakingPromptRulesTest` for the
  directly modified shared audio/evaluator/rule paths included in the whole
  13C3 validation unit.

Migration/configuration kept/changed:

- `src/main/resources/db/migration/V45__practice_speaking_prompt_authoring_foundation.sql`
  — unexecuted proven nullable composite task/source FK correction plus the
  durable lifecycle claim token and exact storage-key lock indexes required
  because applied V34 is immutable;
- `src/main/resources/application.properties` and
  `src/main/resources/application-local.properties.example` — explicit
  bounded cleanup window/worker cadence; no provider enablement.

Current-source documentation/architecture kept/changed:

- `CODEX_PRACTICE_WORKFLOW.md`;
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`;
- `docs/PRACTICE_PHASE_13C3_SPEAKING_PROMPT_AUTHORING_LIVE_CHANGE_LOG.md`;
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
- `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`;
- `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE_MANIFEST.md`;
- `docs/architecture/practice/mermaid/KSH_PRACTICE_CLASS_DIAGRAMS.md`;
- `docs/architecture/practice/mermaid/KSH_PRACTICE_SEQUENCE_DIAGRAMS.md`.

#### Literal complete-current-diff necessity ledger

The current non-excluded correction diff contains exactly 163 paths. Every path
below has disposition `KEEP`: it is either production behavior required by the
accepted 13C3 contract, a focused specification, the one additive unexecuted
schema/configuration boundary, or current-source documentation. No path is
present merely because it was convenient to touch.

Necessity codes:

- `DOC`: current workflow, gate, debt, design or repository-owned architecture
  truth;
- `EVAL`: prompt/evaluator identity, privacy, reuse and honest transcript-only
  scoring;
- `CONTRACT`: v1/v2 learner-safe contract and delivery presentation;
- `BOUNDARY`: Excel, draft/import, asset, cleanup, publication and reference
  safety;
- `PROMPT`: lecturer-owned source/artifact/task/transcript/Editor orchestration;
- `RUNTIME`: publisher/player/shared audio integration;
- `CFG`: additive V45 or bounded disabled-by-default configuration;
- `UI`: lecturer/player contract, help and state-machine resources;
- `SPEC`: focused authored/updated specification, intentionally not executed.

`DOC`:

- `CODEX_PRACTICE_WORKFLOW.md`
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`
- `docs/PRACTICE_PHASE_13C3_SPEAKING_PROMPT_AUTHORING_LIVE_CHANGE_LOG.md`
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`
- `docs/architecture/practice/KSH_LANGUAGE_ASSESSMENT_AND_EXPLANATION_DESIGN.md`
- `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE_MANIFEST.md`
- `docs/architecture/practice/mermaid/KSH_PRACTICE_CLASS_DIAGRAMS.md`
- `docs/architecture/practice/mermaid/KSH_PRACTICE_SEQUENCE_DIAGRAMS.md`

`EVAL`:

- `src/main/java/com/ksh/features/practice/ai/metrics/PracticeAiMetrics.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationService.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationIdentity.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationNormalizer.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationOrchestrator.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilder.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationRequest.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationResult.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluatorProperties.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/SpeakingPromptRules.java`
- `src/main/java/com/ksh/features/practice/ai/speaking/transcription/OpenAiSpeakingTranscriptionClient.java`

`CONTRACT`:

- `src/main/java/com/ksh/features/practice/assessment/AssessmentContractCodec.java`
- `src/main/java/com/ksh/features/practice/assessment/PlayerQuestionPayload.java`
- `src/main/java/com/ksh/features/practice/assessment/QuestionContent.java`
- `src/main/java/com/ksh/features/practice/assessment/SpeakingPromptDelivery.java`
- `src/main/java/com/ksh/features/practice/assessment/SpeakingPromptDeliveryPresenter.java`

`BOUNDARY`:

- `src/main/java/com/ksh/config/SecurityConfig.java`
- `src/main/java/com/ksh/config/WebConfig.java`
- `src/main/java/com/ksh/entities/PracticeAssetLifecycleTask.java`
- `src/main/java/com/ksh/features/practice/controller/PracticeMaterialController.java`
- `src/main/java/com/ksh/features/practice/manage/controller/PracticeDraftController.java`
- `src/main/java/com/ksh/features/practice/manage/controller/PracticePdfImportApiController.java`
- `src/main/java/com/ksh/features/practice/manage/service/LecturerAssetService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelV2Codec.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleProcessor.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskExecutor.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskTransactions.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetOrphanReconciler.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeAssetReferenceGuard.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftContractService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftPreviewService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeImportDraftService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceService.java`
- `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java`
- `src/main/java/com/ksh/features/practice/repository/LecturerAssetRepository.java`
- `src/main/java/com/ksh/features/practice/repository/PracticeAssetLifecycleTaskRepository.java`
- `src/main/java/com/ksh/features/practice/repository/PracticeDraftRepository.java`
- `src/main/java/com/ksh/features/practice/repository/PracticeMaterialReferenceRepository.java`

`PROMPT`:

- `src/main/java/com/ksh/features/practice/manage/speaking/FfprobeSpeakingPromptAudioVerifier.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/OpenAiSpeakingPromptSttAdapter.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/OpenAiSpeakingPromptTtsAdapter.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiArtifact.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiArtifactRepository.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiContract.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiTask.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiTaskProcessor.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiTaskRepository.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAiTaskWorker.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAssetService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAudioVerifier.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringAiProperties.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringConflictException.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringController.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringControllerAdvice.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringStateService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAutosaveAuthorityMerger.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptContextIdentity.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptDraftAuthority.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptEvaluationContextService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptFingerprintService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptLifecycleService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptOriginalAudioUploadCoordinator.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptPublicationService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptRetentionService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptRetentionWorker.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSource.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSourceRepository.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptSttPort.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTaskTransactions.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTranscriptRevision.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTranscriptRevisionRepository.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTranscriptService.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTtsPort.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptVersionContext.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptVersionContextRepository.java`
- `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptWorkLoader.java`

`RUNTIME`:

- `src/main/java/com/ksh/features/practice/manage/validator/PracticeDraftValidator.java`
- `src/main/java/com/ksh/features/practice/service/PracticePublishedVersionService.java`
- `src/main/java/com/ksh/features/practice/service/PracticeService.java`
- `src/main/java/com/ksh/features/practice/service/audio/FfprobeAudioProbe.java`
- `src/main/java/com/ksh/features/practice/service/audio/FfprobeSpeakingAudioInspector.java`
- `src/main/java/com/ksh/features/practice/service/audio/OpenAiAudioHttpTransport.java`

`CFG`:

- `src/main/resources/application-local.properties.example`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V45__practice_speaking_prompt_authoring_foundation.sql`

`UI`:

- `src/main/resources/static/css/app-shell.css`
- `src/main/resources/static/css/practice-catalog.css`
- `src/main/resources/static/css/practice-index.css`
- `src/main/resources/static/css/practice/manage-dashboard.css`
- `src/main/resources/static/css/practice/manage-editor.css`
- `src/main/resources/static/css/practice/manage-speaking-prompt-authoring.css`
- `src/main/resources/static/js/practice/manage-authoring-contract.js`
- `src/main/resources/static/js/practice/manage-draft-preview.js`
- `src/main/resources/static/js/practice/manage-speaking-prompt-authoring.js`
- `src/main/resources/static/js/practice/player-speaking.js`
- `src/main/resources/static/js/practice/speaking-preflight.js`
- `src/main/resources/templates/fragments/app-header.html`
- `src/main/resources/templates/fragments/practice-sidebar.html`
- `src/main/resources/templates/practice/manage/dashboard.html`
- `src/main/resources/templates/practice/manage/editor.html`
- `src/main/resources/templates/practice/manage/excel-import.html`
- `src/main/resources/templates/practice/manage/import-wizard.html`
- `src/main/resources/templates/practice/manage/import-workspace.html`
- `src/main/resources/templates/practice/player-speaking.html`
- `src/main/resources/templates/practice/speaking-preflight.html`

`SPEC`:

- `src/test/java/com/ksh/features/practice/PracticeFunctionalUiContractTest.java`
- `src/test/java/com/ksh/features/practice/PracticeSpeakingMediaUiResourceTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/OpenAiCompatibleSpeakingEvaluationClientTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationApplicationServiceTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationPromptBuilderTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingEvaluationReusePolicyTest.java`
- `src/test/java/com/ksh/features/practice/ai/speaking/SpeakingPromptRulesTest.java`
- `src/test/java/com/ksh/features/practice/assessment/AssessmentContractCodecTest.java`
- `src/test/java/com/ksh/features/practice/assessment/SpeakingPromptDeliveryPresenterTest.java`
- `src/test/java/com/ksh/features/practice/controller/PracticeMaterialControllerTest.java`
- `src/test/java/com/ksh/features/practice/manage/PracticePhase11AuthoringUiContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/SpeakingPromptAuthoringUiContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/LecturerAssetServiceOwnershipTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssessmentExcelSpeakingBoundaryTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskExecutorTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetLifecycleTaskTransactionsTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetOrphanReconcilerTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeAssetReferenceGuardTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeDraftContractServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeDraftPreviewServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeImportDraftOwnershipTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/PracticeMaterialReferenceServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/service/SpeakingPromptPublicationTransactionContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/FfprobeSpeakingPromptAudioVerifierTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAssetAuthorizationTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringContractTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringControllerTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringEditorStateTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringFoundationMigrationTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringStateServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAutosaveAuthorityMergerTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptCollaborationRevocationTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptDraftVersionConflictTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptEvaluationContextServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptLifecycleServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptPersistenceInvariantTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptProviderAdapterTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptPublicationServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptRetentionServiceTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptRetryPolicyTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptTaskOrchestrationTest.java`
- `src/test/java/com/ksh/features/practice/manage/speaking/SpeakingPromptVersionContextTest.java`
- `src/test/java/com/ksh/features/practice/manage/validator/PracticeDraftValidatorTest.java`
- `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java`
- `src/test/java/com/ksh/features/practice/service/SpeakingPlayerPayloadPrivacyTest.java`

Removed/not added because there is no independent call-site/schema need:

- no V46 or casual cleanup migration;
- no automatic Excel STT/TTS/source/task bridge and no bulk provider
  preview/cost path; the only handoff is the explicit authorized Editor
  adoption command;
- no second storage implementation, direct physical-delete helper or
  provider-specific cleanup client;
- no source-local artifact mutation/cascade and no backfill/rewrite job;
- no learner transcript/acoustic scoring or later-phase compatibility work.

Deferred honestly:

- consolidated execution evidence, DOCX/Drawio regeneration and browser/device
  visuals belong to the separate validation/artifact task; the excluded
  `SEP490_G103_KoreanHub.drawio.xml` was never inspected or changed;
- real STT/TTS smoke requires separate approval, bounded cost and configured
  credentials;
- storage providers without an object-list/reconciliation API cannot discover
  a crash-created object that failed before any durable lifecycle intent row;
  current post-storage compensation covers every object for which a candidate
  or lifecycle row exists.

### 14.5 Exact separate consolidated validation handoff

Do not execute in `13C3-04`. The coordinator runs one unit, in this order:

1. `git diff --check`;
2. one clean JDK 17 compile/build;
3. one JDK 17 test invocation with this exact combined selector (and no broader
   suite unless it exposes a dependency gap):

   ```text
   AssessmentContractCodecTest,
   PracticeAssessmentExcelServiceTest,
   PracticeAssessmentExcelSpeakingBoundaryTest,
   PracticeAssessmentExcelControllerTest,
   PracticePdfImportApiControllerTest,
   PracticeImportDraftOwnershipTest,
   PracticeDraftServiceTest,
   PracticeDraftContractServiceTest,
   PracticeDraftValidatorTest,
   PracticeDraftPreviewServiceTest,
   LecturerAssetServiceOwnershipTest,
   PracticeMaterialReferenceServiceTest,
   SpeakingPromptAuthoringContractTest,
   SpeakingPromptAuthoringControllerTest,
   SpeakingPromptAuthoringStateServiceTest,
   SpeakingPromptAuthoringEditorStateTest,
   SpeakingPromptAutosaveAuthorityMergerTest,
   SpeakingPromptDraftVersionConflictTest,
   SpeakingPromptTaskOrchestrationTest,
   SpeakingPromptRetryPolicyTest,
   SpeakingPromptPersistenceInvariantTest,
   SpeakingPromptProviderAdapterTest,
   SpeakingPromptLifecycleServiceTest,
   SpeakingPromptRetentionServiceTest,
   SpeakingPromptCollaborationRevocationTest,
   SpeakingPromptAssetAuthorizationTest,
   SpeakingPromptAuthoringFoundationMigrationTest,
   PracticeAssetLifecycleTaskExecutorTest,
   PracticeAssetLifecycleTaskTransactionsTest,
   PracticeAssetReferenceGuardTest,
   PracticeAssetOrphanReconcilerTest,
   SpeakingPromptPublicationServiceTest,
   SpeakingPromptPublicationTransactionContractTest,
   SpeakingPromptVersionContextTest,
   SpeakingPromptDeliveryPresenterTest,
   PracticeSpeakingMediaUiResourceTest,
   PracticePhase11AuthoringUiContractTest,
   PracticeFunctionalUiContractTest,
   PracticeMaterialControllerTest,
   SpeakingPlayerPayloadPrivacyTest,
   SpeakingEvaluationPromptBuilderTest,
   SpeakingEvaluationReusePolicyTest,
   SpeakingPromptEvaluationContextServiceTest,
   SpeakingPromptAuthoringUiContractTest,
   PracticeServiceTest,
   SpeakingEvaluationApplicationServiceTest,
   OpenAiCompatibleSpeakingEvaluationClientTest,
   OpenAiSpeakingTranscriptionClientTest,
   FfprobeSpeakingPromptAudioVerifierTest,
   FfprobeSpeakingAudioInspectorTest,
   SpeakingEvaluationNormalizerTest,
   SpeakingEvaluationOrchestratorTest,
   SpeakingPromptRulesTest
   ```

   The coordinator translates that comma-separated list into the repository's
   single Maven `-Dtest=...` selector under the same JDK 17 environment as step
   2; fake transports remain the default and no live provider is configured.
4. one disposable fresh-schema Flyway-through-V45 plus Hibernate mapping proof,
   with this lifecycle: generate a unique task-scoped schema name; create only
   that empty schema; run the application/test migration lifecycle with Flyway
   enabled and Hibernate `ddl-auto=validate`; assert Flyway success through
   V45 and inspect the five prompt tables plus their FKs/checks/indexes and
   the additive `practice_asset_lifecycle_tasks.claim_token` plus exact
   lifecycle/asset storage-key indexes; run
   the smallest authenticated Practice integration smoke needed to force the
   mappings; then drop only that exact disposable schema in a trapped cleanup.
   Check nullability, widths, enums/checks, composites, indexes, nullable
   composite task/source identity, explicit detach-before-delete and exact
   lifecycle claim-token/lease mapping. Never
   repair, reuse or mutate a retained/shared database;
5. explicit browser journeys for upload/audio-only, Excel-staged audio
   adoption, manual/text-only and manual/text+audio, including stale/regenerate,
   copy/new-clientId, same-client audio replacement, question deletion, refresh
   and revoked-collaborator denial;
6. only if separately approved and cost-bounded, one live STT and one live TTS
   smoke. Otherwise record `NOT_RUN_NOT_APPROVED`, not a green provider claim.

Not run in `13C3-04`: every test, compile/build, Maven/Gradle, lint, startup,
Docker, browser, database/MySQL/Flyway, provider/API, security scan,
`git diff --check` and all Git staging/commit/push/pull/merge/rebase/stash or
branch actions.

### 14.6 First frozen-snapshot audit and grouped correction

The first exact candidate snapshot,
`c767ed12bd300dc6bacd4ab559866e30a4f962e9288131c87a88a5fcd59e64a3`,
was independently reread from scratch by both required final auditors. Both
returned `REJECT_STATIC`; neither edited or validated it. Their complete
findings were resolved as one grouped correction:

- Excel now authorizes and pessimistically locks the exact linked draft before
  asset resolution; extra override keys are ignored and every managed asset
  must already have an exact verified upload reference on that draft. Each
  Speaking asset additionally requires the same-question staging/original
  identity on retry. Successful import converts the generic upload reference
  to exact question staging, preserving a separate non-Speaking media
  reference if the same file is reused.
- Linked Excel replacement invokes the same Speaking source/staging lifecycle
  before saving. Retained staging identity includes actual Speaking client ID
  plus exact managed asset, so same-clientId type/audio changes tear down stale
  bindings instead of leaking them.
- The workbook schema identifier is case-sensitive and malformed variants fail
  closed. Preview performs no asset lookup; import performs no
  source/artifact/task/transcript/provider/TTS creation.
- Lifecycle source deletion and explicit original unlink hand every
  now-unreferenced private candidate to the centralized locked reference guard.
- Asset-cleanup workers use a durable random claim token and ten-minute lease;
  fresh `RUNNING` work cannot be reclaimed, and old workers cannot finalize a
  successor claim. V45 adds only this proven column to the applied V34 table
  and retains the nullable composite task/source owner FK correction.
- Duplicate upload objects and the repository-absent compatibility fallback no
  longer delete physical storage inside a transaction; durable lifecycle
  intent or post-completion compensation owns that I/O.
- Current Mermaid sequences/classes now match preview/import persistence and
  actual lifecycle dependencies. The consolidated selector now includes the
  validator, preview, all three delivery branches, immutable transaction
  context, learner privacy, evaluator reuse, copied/editor state and the new
  lifecycle/authorization/concurrency specifications.

This correction requires a new exact snapshot and two new audits from scratch.
`PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.7 Second frozen-snapshot audit and grouped correction

The next exact candidate snapshot,
`9a854c2db17dfc39dc08c306258492008a8312308710607a9930c033f19bb36a`,
was independently reread from scratch by both required final auditors. Both
returned `REJECT_STATIC`; neither edited or validated it. The recovery
continuation confirmed the same non-excluded candidate state before applying
this second grouped correction:

- delayed `ORPHAN_RECONCILE`/`PROMOTE_CLEANUP` work now locks all
  `LecturerAsset` rows for its exact storage key both at claim and immediately
  before physical I/O. Every production asset allocation registers through the
  inverse task-key reservation: pending cleanup is completed as obsolete and
  running cleanup rejects registration. Candidate/rollback cleanup is durable
  whenever the production lifecycle repository exists, so no unguarded delayed
  key delete can race a reused asset. V45 remains unexecuted and adds only the
  exact indexes required for these proven bounded locks.
- same-clientId replacement compares the normalized managed audio identity with
  the current source input mode/original asset. A different managed asset or a
  manual-mode source is torn down under the existing task/reference/source lock
  order; same-client Speaking content without a managed replacement retains its
  current source.
- Excel import still ends at exact private staging with zero prompt AI state.
  Editor now exposes only a staging-available boolean and one explicit
  Vietnamese action. That ID-free action reauthorizes exact draft/question
  tokens, verifies the internally resolved staged bytes outside the
  transaction, rechecks the sole staging reference after draft/source/asset
  locks, then converts it to the original binding and queues STT. No Excel or
  adoption path invokes TTS.
- the file ledger no longer labels unchanged
  `PracticeMaterialReferenceServiceTest` as authored/updated; unchanged
  controller/draft/reference regressions are classified separately as selector
  dependencies. The duplicated lifecycle-repository description was removed
  and the handoff/architecture inventory now describes the explicit Editor
  adoption boundary.

This correction requires a new exact snapshot and two new audits from scratch.
No earlier verdict can be reused. `PHASE_13C3_VALIDATION` remains
`NOT_STARTED`.

### 14.8 Third frozen-snapshot rejection, recovery and grouped correction

The third candidate was frozen as
`2d6cb5a9639883e6b66c018efa547cc9d505db4fedd8ba72c11338ec026b0d06`.
Its Excel/lifecycle auditor returned `REJECT_STATIC`: the owner delete and
import-session cleanup paths could mark a retained asset `ARCHIVED`, and they
did not take the asset row lock before the all-reference decision. The same
late-reference branch in lifecycle claim/confirmation also archived the
already pending row. The independent whole-correction audit did not deliver a
verdict to the coordinator before the recovery thread ended with
`systemError`, so no pairwise verdict is claimed for that snapshot.

Recovery found the same 138 non-excluded paths but a different current content
digest,
`54a62fe2ca148838df845839716a764c7f2dc4464fea7c5d3b70b6225f3446cf`;
therefore the old digest was not reused. A new bounded read-only whole-13C3
audit of that current pre-correction state returned `REJECT_STATIC`. In
addition to the retained-asset defect, it required concrete proof of the
actual historical Excel-v1 boundary, the omitted direct shared-path
regressions in the consolidated selector/ledger and current post-rejection
status text.

This single grouped static correction resolves those findings:

- owner deletion authorizes and locks the exact asset, then checks the central
  material/source/artifact/immutable-context guard before any logical
  mutation. A retained asset produces an actionable error and remains
  unchanged. A missing central guard also fails closed. Import-session cleanup
  obtains owner/session candidate IDs, locks and rechecks each exact row, skips
  retained rows unchanged and queues only an unreferenced temporary asset;
- lifecycle claim and the final pre-I/O confirmation complete obsolete delete
  work without archiving or mutating a newly retained asset. Successful
  completion takes the same task-then-asset lock order. Existing storage-key
  task reservation and the final storage-key reuse check remain unchanged;
- a real in-memory `practice-excel-v1` sheet fixture proves the exact legacy
  reader remains usable for its historical contract and retains
  `question-content-v1`. A separate legacy Speaking fixture proves that the
  old workbook shape, which has no exact per-question verified audio
  authority, fails closed instead of bypassing the v2 asset boundary. The
  existing contract normalization specification now asserts the exact
  four-field v1 Speaking shape and absence of v2 identity. Current template
  output remains v2 only;
- the exact selector now includes `PracticeDraftContractServiceTest` plus
  `OpenAiSpeakingTranscriptionClientTest`,
  `FfprobeSpeakingAudioInspectorTest`,
  `SpeakingEvaluationNormalizerTest`,
  `SpeakingEvaluationOrchestratorTest` and `SpeakingPromptRulesTest`; the
  necessity ledger classifies authored versus unchanged dependencies
  honestly.

No test or prohibited validation was executed. This correction requires a new
exact snapshot and both independent final audits from scratch on that same
digest. `PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.9 Fourth frozen-snapshot rejection and grouped correction

Both required independent auditors confirmed and reread the same 139-path
snapshot,
`9d2f3db290e8ed3f647a130992c560a96a7aef203b96eb035842224345371ba7`.
Both returned `REJECT_STATIC` without editing or validating it. Their complete
blocking set was:

- published material promotion inserted immutable references before locking the
  parent asset, used an unlocked load and could resurrect an archived/deleted
  row;
- the focused promotion/session ownership specification still stubbed an
  obsolete unlocked repository call;
- content-addressed storage became visible before inverse task-key reservation,
  so an old or lease-reclaimed physical worker could target a newly stored key;
- replacement original/generated prompt audio left prior exact-question
  material bindings behind, preventing bounded orphan retirement.

One grouped static-only correction resolves all four findings:

- promotion gathers all exact asset IDs, locks them in ascending order, rejects
  any missing/deleted/non-linkable row before the first immutable reference
  insert and activates only already-live or temporary rows. Focused
  specifications now prove lock order and no resurrection;
- every physical allocation uses a new UUID namespace and accepts only a newly
  created key under that namespace before the existing task-key reservation.
  Generated TTS bytes immediately create an exact private
  `TEMPORARY / AI_TTS` row; registration reauthorizes and locks that row, checks
  its complete identity and unreferenced state, then either activates it or
  queues the duplicate staging row. Registration/cleanup is single-use, and the
  orphan selector remains bounded to exact `MANUAL_UPLOAD` or `AI_TTS` staging;
- original/generated replacement links the new exact question binding, locks
  and deletes only prior rows for the same draft/placement/client ID, and sends
  opaque prior asset IDs through an `AFTER_COMMIT` + `REQUIRES_NEW` centralized
  reference-safe cleanup handoff. Other questions, sources and immutable
  published references are untouched;
- the ownership specification now stubs the exact locked repository call.

No V45 change, test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action occurred. This
grouped correction requires a new exact non-excluded snapshot and both
independent audits rerun from scratch; no earlier verdict may be reused.
`PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.10 Fifth frozen-snapshot rejection and grouped correction

Both required independent auditors independently confirmed and reread the same
140-path non-excluded snapshot,
`24dd6708515734bf9df7ee00ef0507d1d099b2c8b7c203a2177b699a522225ea`.
Both returned `REJECT_STATIC`; neither edited or validated it. The complete
blocking set was:

- add and duplicate structure actions could mutate/copy generic `DRAFT_DATA`
  before the dedicated Speaking flush, allowing dirty or in-flight prompt state
  to race generic autosave and copied/new-clientId identity;
- generic lecturer asset PATCH loaded without a row lock and could mutate
  retained asset metadata/type/status, including making immutable published
  delivery unavailable;
- both PDF-import manual-copy/attach routes could copy source-local
  Speaking/material/client/audio identity; attach also directly deleted the
  source draft without exact Speaking teardown;
- the whole-diff necessity ledger used shorthand for 26 paths instead of a
  literal repository-relative disposition.

One grouped static-only correction resolves the full set:

- every test/section/group/question add and duplicate path, including its
  dispatcher/compatibility callers, awaits
  `flushSpeakingBeforeStructureMutation()` before normalization/copy/mutation;
  test deletion is covered by the same boundary, the existing move/delete
  boundary is preserved, and the focused static UI specification proves guard
  ordering;
- generic asset PATCH delegates to a transactional service that locks the exact
  asset, authorizes the owner, rejects deleted/non-manageable/retained state
  before any mutation, and forbids type/status transitions;
- both PDF-import copy/attach routes authorize and lock the session-linked
  source, then reject any Speaking source, material reference, explicit v2
  Speaking/authoring/audio identity or unverifiable JSON before target
  mutation. Pristine PDF-only flow remains; no source-bearing draft is copied
  or directly deleted;
- Section 14.4 gains one literal complete-current-diff path/necessity ledger,
  including the newly touched controller/import service/specification paths.

The stale publication comment now names current 13C3-04 lifecycle ownership.
No V45 change, test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action occurred. This
grouped correction requires a new exact non-excluded snapshot and both
independent audits rerun from scratch; no earlier verdict may be reused.
`PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.11 Sixth frozen-snapshot rejection and grouped correction

Both required independent auditors independently confirmed and reread the same
143-path non-excluded snapshot,
`46de24fdd612cb882c280d703f9f9d4ed406e02e9173acceb075d3c7df8f71e4`.
The Excel/lifecycle/schema audit returned `ACCEPT_STATIC`; the independent
whole-13C3 compatibility/privacy/scope audit returned `REJECT_STATIC`. Neither
edited or validated the snapshot. Its one confirmed blocker was:

- PDF-import attach accepted the session-linked temporary source draft as its
  own target. The same managed row could therefore be merged and saved into
  itself, deleted as the temporary source, then left as the session's linked
  draft identity.

One bounded static-only correction rejects identical source/target identity
immediately after exact session-owner authorization and before any draft load,
lock, JSON parse, reference probe, target mutation, save, deletion or session
mutation. A focused no-side-effect specification covers the invariant; the
valid distinct pristine-PDF source/target flow remains unchanged.

No V45 change, test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action occurred. This
grouped correction requires a new exact non-excluded snapshot and both
independent audits rerun from scratch; neither verdict on the rejected digest
may be reused. `PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.12 Seventh frozen-snapshot rejection and grouped correction

Both required independent auditors independently confirmed and reread the same
143-path non-excluded snapshot,
`1d47020698db9f39bd9d8c3f53a9d93d41ebd528f4dac222e1ede2715a45defb`.
The whole-13C3 compatibility/privacy/scope audit returned `ACCEPT_STATIC`; the
Excel/lifecycle/schema audit returned `REJECT_STATIC`. Neither edited or
validated the snapshot. Its one confirmed blocker was:

- asset-specific lifecycle claim and final confirmation locked and guarded
  only the candidate asset ID. Historical/content-addressed sibling asset rows
  can share the same non-unique physical storage key, so deleting an
  unreferenced candidate could remove bytes still needed by an active or
  retained sibling.

One grouped static-only correction makes both asset-specific decision points
lock all exact storage-key rows in deterministic ID order, require the
candidate's exact pending/key identity, recheck the central reference guard for
the candidate and every sibling, and fail closed if a sibling is active or
otherwise still needs the bytes. No asset row is mutated on rejection, and the
executor performs no physical I/O. Focused claim-time, late pre-I/O,
active-sibling and executor no-I/O specifications cover the invariant.

No V45 change, test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action occurred. This
grouped correction requires a new exact non-excluded snapshot and both
independent audits rerun from scratch; neither verdict on the rejected digest
may be reused. `PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.13 Eighth frozen-snapshot rejection and grouped correction

Both required independent auditors independently confirmed and reread the same
143-path non-excluded snapshot,
`49cf65bb084c22b089e2320c59628be6d6e16390f0e50613b790936eb78acba4`.
The whole-13C3 compatibility/privacy/scope audit returned `ACCEPT_STATIC`; the
Excel/lifecycle/schema audit returned `REJECT_STATIC`. Neither edited or
validated the snapshot. Its one confirmed blocker was:

- shared-key rejection was physically safe but terminally completed the
  candidate task while leaving its asset `DELETION_PENDING`. If the blocking
  sibling later moved to a fresh promoted key, non-asset cleanup still stopped
  on the stranded candidate and no selector could recreate its completed
  asset-specific task, leaving old private bytes outside bounded cleanup.

One grouped static-only correction distinguishes invalid terminal identity from
temporarily retained storage. Retained candidate/sibling decisions now keep the
same task `PENDING`, clear any claim token and set a bounded hourly recheck
without consuming storage-failure attempts. Exact all-row locks and zero-I/O
rejection remain unchanged. A focused blocked-candidate then
sibling-promoted-to-fresh-key specification proves the surviving task becomes
claimable, passes the final recheck and completes the candidate; existing
claim/final/executor specifications now assert the non-terminal outcome.

No V45 change, test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action occurred. This
grouped correction requires a new exact non-excluded snapshot and both
independent audits rerun from scratch; neither verdict on the rejected digest
may be reused. `PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.14 Ninth frozen-snapshot rejection and grouped correction

Both required independent auditors independently confirmed and reread the same
143-path non-excluded snapshot,
`f4ce01bebfee96a07bb02d98b2753801b2010e435f60002e23b2359f3d98fc87`.
The whole-13C3 compatibility/privacy/scope audit returned `ACCEPT_STATIC`; the
Excel/lifecycle/schema audit returned `REJECT_STATIC`. Neither edited or
validated the snapshot. Its one confirmed blocker was:

- retained-reference and transient sibling-state deferrals both correctly
  survived, but due work was ordered only by monotonically increasing task ID.
  A sufficiently large permanently retained low-ID prefix could become due
  every hour, fill every fixed-size five-minute batch and indefinitely starve
  newer eligible cleanup.

One bounded static-only correction orders due work by `nextAttemptAt` and then
stable ID, matching the existing `(status, next_attempt_at, id)` V34 index.
Once a retained task is deferred into the future, already-due cleanup sorts
ahead of it; same-time work remains deterministic. A focused repository-query
contract locks this fairness rule. Exact all-row locks, durable retention
deferral, invalid terminal identity, bounded storage retry and zero-I/O
rejection remain unchanged.

No V45 change, test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action occurred. This
grouped correction requires a new exact non-excluded snapshot and both
independent audits rerun from scratch; neither verdict on the rejected digest
may be reused. `PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.15 Final static acceptance and validation handoff

Both required independent auditors independently recomputed and reread the same
143-path non-excluded implementation/specification snapshot,
`71f401bbc5d9941da6c52606ce14de4badeb93dae314870192bc36c6f881e57a`,
from scratch. The Excel/cleanup/lifecycle/reference-safety/authorization/
concurrency/schema audit returned `ACCEPT_STATIC`. The independent whole-13C3
compatibility/privacy/scope/file-necessity/docs/validation-readiness audit also
returned `ACCEPT_STATIC`. Neither edited the snapshot, reused an earlier
verdict or executed validation.

Static acceptance therefore sets:

- `13C3-04 = IMPLEMENTED_STATIC_ACCEPTED`;
- `PHASE_13C3_IMPLEMENTATION = READY_FOR_PHASE_VALIDATION`;
- `PHASE_13C3_VALIDATION = NOT_STARTED`;
- `CURRENT_REQUIRED_ACTION = SEPARATE_CONSOLIDATED_PHASE_13C3_VALIDATION`.

The separate coordinator must run only the Section 14.5 lifecycle: `git diff
--check`, one JDK 17 compile/build, the one 50-class combined selector, a
disposable fresh V1-V45 Flyway/Hibernate proof, explicit authorized browser
journeys for upload-audio-only/manual-text-only/manual-text+audio plus
stale-regenerate, and live STT/TTS only if separately approved and
cost-bounded. No test, compile/build, lint, startup, browser, database/Flyway,
provider/API, security scan, `git diff --check` or Git action has run in
`13C3-04`; V45 remains unexecuted. Phase 13 remains open, and no later phase or
deferred compatibility/audio-scoring work is authorized by this acceptance.

After a green consolidated 13C3 gate, the complete validated diff must be split
into multiple coherent, meaningfully named commits and pushed once before 13G
opens. 13G and 13H each repeat that separate validation/commit/push boundary.
After the 13H push, the multi-subagent comprehensive `/practice` audit/cleanup
defined in
`docs/PRACTICE_PRE_PHASE_14_COMPREHENSIVE_AUDIT_AND_DEAD_SURFACE_CLEANUP.md`
must itself be accepted, validated, committed and pushed. Only then may the
pre-14 checkpoint inspect the integrated state and return GO/NO-GO.

### 14.16 Post-acceptance readiness re-audit and grouped correction

The `14.15` verdict remains historical evidence for its exact 143-path digest,
but it cannot authorize validation of the later working tree. Before the
consolidated gate, two fresh independent read-only readiness audits inspected
the newer non-excluded state. Both rejected it, and two older agents that had
ended through usage limits were explicitly not counted as passes. The
deduplicated 13C3 blockers were:

- STT and TTS fingerprints omitted the configured `purpose_code` and
  `retention_code`, although provider invocation, currentness and publication
  treated both values as policy identity;
- same-owner/manual byte deduplication could return an immutable
  `PUBLISHED` logical asset to the Editor, which requires a verified
  `PRIVATE` authoring identity;
- the HTML picker still exposed unrestricted `audio/*`, while the backend
  contract accepts only MP3, WAV, M4A, OGG and WebM;
- asynchronous/provider failures kept public categories in the backend but the
  Editor collapsed them into generic copy; and
- the acceptance set lacked the complete positive
  `needs_review -> lecturer confirmation -> save -> publish` journey and the
  bounded provider/verifier failure matrix.

One grouped static-only correction now:

- binds `purpose_code` and `retention_code` into both fingerprints and proves
  independent policy drift invalidates each operation;
- reuses only an eligible active/private/unbound logical upload. If matching
  bytes already belong to a published row, it creates a new private logical
  authoring row over the safely locked shared physical key and sends the unused
  fresh object through the existing orphan lifecycle;
- uses one closed extension/MIME policy for picker and drop, with size and
  best-effort duration prevalidation while preserving the server as final
  authority;
- maps every public API/async category to safe actionable Vietnamese without
  exposing provider text; and
- adds focused, unexecuted specifications for low-confidence confirmation,
  successful publication and timeout/transport/HTTP/empty/malformed/invalid
  media plus corrupt/MIME/size/duration rejection.

`FfprobeSpeakingPromptAudioVerifierTest.java` is a new `SPEC` path, so the
corrected 13C3 ledger contains 144 paths rather than the previously accepted
143. It belongs to the verifier/failure-matrix proof and is not a Pre-14
sidecar.

The shared checkout also contains independent Pre-14 candidates
(`pom.xml`/`.java-version`, Writing fail-closed/cache work and verified-dead
generic JavaScript cleanup). They are not 13C3 evidence. The 13C3 gate must be
run from an isolated exact 144-path patch over the phase baseline; a green run
on the mixed checkout would not certify the phase boundary.

No unit/integration/full test, compile/build, lint, application startup,
browser, database/Flyway, provider/API, network, Git staging, commit or push
was run for this correction. The next action is to freeze the corrected
non-excluded 13C3 snapshot and rerun both required independent audits from
scratch on that same digest. Only two fresh `ACCEPT_STATIC` verdicts may restore
`READY_FOR_PHASE_VALIDATION`; `PHASE_13C3_VALIDATION` remains `NOT_STARTED`.

### 14.17 Validation-worktree union and user-locked validation order

The validation worktree was frozen after it disclosed a newer lecturer-facing
Vietnamese/responsive correction batch and a storage-lifecycle defect in the
image library path. It was not safe to validate that worktree directly because
it was simultaneously missing later 13C3 fingerprint, private-identity,
verifier and acceptance corrections from the shared checkout.

The coordinator performed a read-only two-checkout comparison over the same
phase baseline and formed one explicit union in the shared checkout:

- the twelve additional UI resources localize lecturer/student-visible
  Practice navigation, import, preflight and responsive layouts without
  changing persisted enum/JSON identities;
- the image library now creates the exact draft-side server reference before
  mutating editor state, rejects stale targets and never writes a raw dropped
  URL directly into a question/group;
- selection and question-type changes await Speaking flush/deactivation so
  navigation cannot silently race an in-flight prompt save;
- raw `/uploads/**` routing is fail-closed except for the explicit
  lowercase public/legacy allowlist, and authenticated private material is
  returned with `private, no-store, must-revalidate` plus no-cache headers;
- the closed Speaking picker remains exactly MP3/WAV/M4A/OGG/WebM; the broad
  `audio/*` value present in the older worktree was deliberately not merged;
  and
- three additional focused test classes join the selector:
  `PracticeFunctionalUiContractTest`,
  `PracticeMaterialControllerTest` and
  `OpenAiCompatibleSpeakingEvaluationClientTest`.

These nineteen paths extend the corrected 144-path candidate to one exact
163-path 13C3 validation unit. The Section 14.5 selector is therefore 53
classes. The thirteen unrelated JDK/dependency, Writing/cache, later-phase
ledger and dead-resource sidecars in the shared checkout remain excluded.

Two audit retries independently confirmed the pre-union 144-path digest but
ended with a system/usage failure before returning verdicts; they are not
passes. The user subsequently locked the order for this union: synchronize the
exact 163-path snapshot into the isolated validation worktree, run the single
consolidated 13C3 validation, split the green result into multiple coherent
commits and push once, and only then run the two independent post-push audits.
No earlier failed or historical audit may be reused.

No validation or Git action is claimed here.
`PHASE_13C3_VALIDATION = NOT_STARTED`; the immediate action is
`SYNC_UNION_SNAPSHOT_AND_RUN_ONE_CONSOLIDATED_VALIDATION`.

### 14.18 Consolidated validation, four-path correction and commit handoff

This section supersedes only the current status of Sections 14.16-14.17. Their
pre-validation path counts and rejected/failed audit history remain evidence
for those exact snapshots.

The isolated recovery worktree was frozen at base
`fec64dbb3cd15bb7a2886c1444503d3257a9484a`. Validation disclosed one
transactional material-access defect: catching a throwing authorization check
inside the same transaction could leave it rollback-only before the published
access fallback ran. One concentrated correction added non-throwing
`canReadDraft` / `canReadSet` probes and their focused specifications. The
final validation snapshot therefore contains 167 paths, adding these four to
the pre-validation 163-path union:

- `src/main/java/com/ksh/features/practice/governance/PracticeAuthorizationService.java`;
- `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialAccessService.java`;
- `src/test/java/com/ksh/features/practice/governance/PracticeAuthorizationServiceTest.java`;
- `src/test/java/com/ksh/features/practice/manage/service/PracticeMaterialAccessServiceTest.java`.

The canonical 167-path snapshot digest is
`d4063b1a2b77b3cdaa97534bd6be5ad02aaf61480a95e390f85aab4aa99485be`,
computed over lexicographically sorted entries as
`path UTF-8 + NUL + exact file bytes + NUL`. This supersedes the old
`fe9e432...` 163-path digest. An independent audit using
`path + NUL + SHA-256(content) + NUL` produced
`bdb08b781e495e8cf72214edc7a06a1b79303dabccd34c02f293158cbf39178c`;
the values differ because the algorithms differ, not because the worktree
changed.

Final evidence on JDK `17.0.19`:

- `git diff --check`: green;
- clean production compilation: 644 sources, green;
- exact 53-class selector: 497 tests, zero failures/errors/skips;
- the final evidence audit found that the selector compiled but did not execute
  the two new authorization/material service test classes. One bounded
  gap-closure selector ran
  `PracticeAuthorizationServiceTest`, `PracticeMaterialAccessServiceTest` and
  `PracticeMaterialControllerTest`: 31 tests, zero failures/errors/skips;
- disposable schema `ksh_phase13c3_union_b991_20260727_c8`: Flyway V1-V45
  `45/45/0`, Hibernate validation and the authenticated Practice smoke green;
  all five Speaking tables, 28 `NO ACTION` foreign keys, nullable
  `claim_token varchar(64)` and the three required lifecycle/storage indexes
  were asserted; the schema was dropped and two absence checks returned `0`;
- `BROWSER_QA = NOT_RUN_USER_DEFERRED_TO_END_OF_PHASE_13`;
- `LIVE_STT_TTS = NOT_RUN_NOT_APPROVED`.

Diagnostic browser activity performed while finding the transaction defect is
not browser-gate evidence. Transcript-only acoustic criteria remain
`NOT_SCORABLE`; live learner-audio-grounded Speaking evaluation remains
`NO_GO`.

The 28 code/UI/test paths that differed in the main checkout were reconciled
byte-for-byte from the validated worktree. Existing responsive/Vietnamese
Practice surfaces are preserved. Newer roadmap and Pre-14 sidecars are kept by
semantic documentation merge, not by overwriting them with the older worktree.
The current `/practice` AI/storage organization also remains separate from the
other project AI/storage facilities; no common/Admin/global extraction is part
of 13C3.

Current handoff:

- `PHASE_13C3_VALIDATION = GREEN_WITH_BROWSER_DEFERRED_TO_END_OF_PHASE_13`;
- `PHASE_13C3_IMPLEMENTATION = CONSOLIDATED_VALIDATION_GREEN_PENDING_COMMIT_PUSH`;
- `CURRENT_REQUIRED_ACTION = GRANULAR_COMMITS_ONE_PUSH_THEN_TWO_POST_PUSH_AUDITS`;
- overall Phase 13 remains open; 13G must not start before the push and the two
  required post-push read-only audits.

### 14.19 Latest-main integration reconciliation (validation pending)

The V1-V45 proof in Section 14.18 remains valid historical evidence for the
isolated pre-integration snapshot only. Before the requested single push, the
coordinator created fifteen granular local commits, confirmed the six Phase
13F commits were already present on `origin/feature/practice-reduce-scope`, and
merged `origin/main` at `1e7cf38` without rebase or force-push.

Main already owns Flyway V45-V53. The unexecuted Practice foundation migration
is therefore renamed forward to
`V54__practice_speaking_prompt_authoring_foundation.sql`; no applied main
migration is overwritten or repaired. The integrated gate must prove both a
fresh V1-V54 path and an upgrade starting from the current main maximum before
the merge is allowed to reach the PR.

Semantic reconciliation preserves all of the following:

- the responsive Vietnamese Practice navigation while adopting canonical
  `LEADER`, `/leader`, the lecturer question-bank route and logout CSRF;
- exact-`LECTURER` Practice authoring authorization;
- main's controller-backed public uploads for avatars/exams only, followed by
  fail-closed denial of every other raw `/uploads/**` path;
- authenticated/no-store Practice material delivery at
  `/practice/materials/{assetId}/content` without a broad disk handler; and
- both the project-wide AI/storage/Admin implementation and the
  Practice-specific AI/storage implementation, present and operational but
  separate. Neither consumer/configuration family is redirected into the
  other, and neither is treated as branch residue.

Legacy `/uploads/questions/**` and `/uploads/options/**` strings remain parser
or sanitization fixtures, not public serving contracts. There is no active
producer or local object directory for those namespaces. A later retained-data
inventory may authorize a narrowly scoped Practice migration/bridge only if it
proves real rows and bytes; this integration does not reopen a global public
route.

Current integration status is
`MAIN_MERGED_RESOLUTION_READY_FOR_CONSOLIDATED_VALIDATION`. No integrated
compile, test, Flyway/Hibernate, browser or provider result is claimed by this
section. The next action is one consolidated integration validation, followed
by the merge commit and one normal push only if that gate is green.

### 14.20 Latest-main consolidated validation evidence

This section supersedes only the pending validation status in Section 14.19.
The isolated pre-main evidence in Section 14.18 remains historical evidence
for that earlier snapshot.

The integration gate ran against `origin/main` at `1e7cf38` plus the complete
Phase 13C3 candidate, including the preserved Phase 13F ancestry. Failures from
the first two integration cycles were fully inventoried before each grouped
correction. The final cycle completed successfully on JDK `17.0.19` with:

- `git diff --check` against `origin/feature/practice-reduce-scope`: green;
- clean production compilation: 734 source files, green;
- full Maven suite: 254 suites / 2,341 tests / 0 failures / 0 errors /
  0 skipped;
- fresh disposable schema: Flyway `V1-V54` = `54/54/0`, with exactly one
  `practice_speaking_prompt_sources` table;
- upgrade rehearsal before the Practice migration: `V1-V53` = `53/53/0`,
  with no Speaking prompt source table;
- forward upgrade rehearsal: `V1-V54` = `54/54/0`, with exactly one Speaking
  prompt source table;
- targeted integrated contracts
  (`PracticeIntegrationTest#testModeView`,
  `PracticePhase11AuthoringUiContractTest`, and
  `SpeakingPromptAuthoringFoundationMigrationTest`): 19 tests / 0 failures /
  0 errors / 0 skipped; and
- validation wrapper exit code `0`; both disposable databases were dropped
  and the cleanup absence assertion passed.

The grouped corrections were test/environment isolation only: bounded Hikari
pool settings, canonical media feature-gate clamps that neutralize a local
developer opt-in, current immutable Speaking prompt-version fixtures,
independent SMTP/question-bank fixtures, and current immutable-result/progress
test contracts. Production AI/storage ownership, provider behavior, security
and immutable mutation guards were not weakened.

`BROWSER_QA` remains `NOT_RUN_USER_DEFERRED_TO_END_OF_PHASE_13`.
`LIVE_STT_TTS` and live Speaking provider evaluation remain
`NOT_RUN_NOT_APPROVED`; controlled TEST-NET timeout specifications are not
provider smoke evidence.

Current handoff:

- `PHASE_13C3_MAIN_INTEGRATION_VALIDATION = GREEN`;
- `PHASE_13C3_IMPLEMENTATION = GREEN_PENDING_MERGE_COMMIT_AND_PUSH`;
- `CURRENT_REQUIRED_ACTION = MERGE_COMMIT_ONE_PUSH_THEN_TWO_FRESH_POST_PUSH_AUDITS`;
- the earlier usage-failed audits remain non-passes and cannot be reused.
