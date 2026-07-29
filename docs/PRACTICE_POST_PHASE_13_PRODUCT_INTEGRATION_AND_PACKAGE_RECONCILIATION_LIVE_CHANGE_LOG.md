# Practice Post-Phase-13 Product Integration And Package Reconciliation Live Change Log

Recorded: `2026-07-29`

Status: `VALIDATION_GREEN_READY_FOR_PUBLICATION`

> Supersession note (`2026-07-29`): a user clarification arrived after the
> initial A-D topology inventory and before any production-source edit. The
> provisional S1/S2 list below is paused until the complete Practice modality
> versus Admin/global AI control-plane matrix is recorded and independently
> reviewed. No production implementation has started.

> Closure note (`2026-07-29`): the expanded AI lane independently reviewed the
> complete matrix against the exact baseline and found no safe provider,
> configuration, log, prompt, credential or control-plane adapter/cutover. It
> re-accepted only S1/S2 as orthogonal internal Practice ownership corrections.
> The slice list is re-frozen below; this note supersedes the temporary pause.

## 1. Baseline and scope

- Remote baseline: `origin/main@f73684e6d21a3b3454fa889cfe3ecbcb1ead0928`.
- Baseline tree: `e595e328a053049bfbe816df94997b3fb875fd23`.
- Phase 13 remains closed: Flyway V1-V62 is unique and contiguous; its
  implementation, browser/device closure, scoring, UX and migration work are
  not reopened by this phase.
- Branch: `codex/post-phase13-product-package-reconciliation`, created from the
  exact remote baseline after a fresh fetch.
- Discovery was read-only. No build, test, database operation, provider call or
  file mutation occurred before the inventory and accepted slice list below
  were frozen.

This phase reconciles ownership and dependency direction. It does not select a
new AI or storage authority and does not make code movement a success metric.

## 2. Read-first authority and literal discovery

The owning audit read the current workflow, execution blueprint, Phase 13 gate,
post-Phase-13 phase contract, Phase 13H and post-13H logs, end-of-Phase-13
browser/device closure, Phase 13 main-migration reconciliation, Pre-14 audit
contract, Phase 15 compatibility inventory, language assessment design and the
historical whole-project pasted audit. The following current or boundary-owning
sources were also reconciled:

- `docs/architecture/practice/KSH_PRACTICE_ARCHITECTURE_MANIFEST.md`;
- current Practice class and sequence diagrams;
- `docs/decisions/0011-r2-object-storage.md`;
- `docs/operations/practice-ai-cache-retention-runbook.md`;
- `openspec/specs/admin/spec.md`, `openspec/specs/shared/spec.md` and the
  historical-overlay Practice spec;
- `docs/ULP_KSH_INTEGRATION_AUDIT.md` for the integrated global AI/storage and
  naming boundary;
- literal Markdown/source/config/bean/route/migration/caller searches for AI,
  provider, request log, cache, storage, upload, media, retention, secrets,
  Admin settings, package ownership and ULP naming.

The pasted whole-project audit predates the current source and is historical
input only. In particular, its global AI error-bound and fallback observations
were rechecked against current code rather than copied forward.

## 3. Independent audit lanes

All lanes used the same commit and tree and made no mutations.

| Lane | Boundary | Result |
|---|---|---|
| A | AI/provider/config | Practice AI clients, code-owned identities and purpose-specific lifecycle records remain separate from Admin DB-managed providers/prompts/logs. The expanded modality/control-plane second pass independently re-accepted only S1/S2 and no AI cutover/shared authority. |
| B | Storage/media | Global object storage, Practice lecturer assets, learner Speaking media and PDF workspace have different key, lifecycle, authorization and failure contracts. No storage adapter/cutover accepted. |
| C | Package/naming/dependency | No active KSH-owned ULP naming defect, duplicate Java basename, package/path mismatch or exact Practice route duplicate. Found one outside-to-Practice exception-advice dependency and one class cycle caused solely by misplaced persisted vocabulary. |
| D | Contract/regression/security | Independently accepted the two bounded ownership moves below with exact parity requirements. Confirmed that neither requires provider, storage, schema, route, DTO, cache, artifact or authorization change. |

## 4. Practice workload modality and capability matrix

This matrix is the additional control-plane gate requested before source
implementation. `Current` means an executable production path on this tree;
`reserved` means the contract exists but no provider client is allowed to
select it. All provider calls below are non-streaming.

| Path | Modality, dialect and response | Selection, secret, timeout/retry and limits | Lifecycle, identity and persistence | Ownership, privacy, media and operations |
|---|---|---|---|---|
| Writing evaluation and upgrade (`Current`) | Korean prompt/answer plus optional governed JPEG/PNG/WebP data URL -> OpenAI-compatible `/chat/completions` -> strict JSON-schema evaluation, criterion diagnostics, `upgraded_answer`, annotated upgrade and sentence rewrites. One unified call, `temperature=0`, `max_tokens=4096`. | Practice `openai.base-url`, `openai.evaluator-model` and `openai.api-key`; bounded 5s/60s defaults; up to five retries only for 429/500/502/503/504 with exponential backoff. No streaming, rate limit, price or cost budget. Image resolver enforces an authorized internal Practice material reference and 8 MiB per image. | Synchronous provider operation inside the durable asynchronous attempt-evaluation job. Contract identity includes endpoint/model/timeouts/retry policy, prompt `v5.0`, rubric `v4.1`, schema `v4.1:v6.0`. User-scoped SHA-256 cache includes prompt, answer, task type, model and all versions; TTL 30 minutes. Normalized output is persisted with the attempt/job result. | Learner/user scope and immutable attempt/version input stay Practice-owned. Missing key and deterministic invalid input fail closed without a provider call; malformed/HTTP/transport results return typed unavailable/contract results. Practice metrics record outcome/latency, but there is no global `ai_request_logs` row or provider-token/cost record. Required image bytes come from authorized Practice lecturer assets, not global object storage. |
| Writing full/question audit re-evaluation (`Current`) | Same text/image input, dialect and strict evaluation/upgrade schema as initial Writing. | Same endpoint/model/secret/limits. Re-evaluation is not a different Admin capability and cannot silently select another model. | Durable job operations are `FULL_REEVALUATE` or `QUESTION_REEVALUATE`; input fingerprint and evaluation-contract identity fence stale work. Cache read is bypassed, while a valid result refreshes the exact versioned cache entry. Manual retry and terminal failure/unavailable state remain in the Practice job. | Authorized learner/lecturer audit flow remains bound to the immutable attempt. A previous score-bearing result is not replaced by an unavailable empty result. No Admin prompt mutation, provider reorder or global fallback may alter the audit identity implicitly. |
| Learner Speaking transcription (`Current but disabled by default`) | Private learner WebM/MP4 audio stream -> OpenAI `/audio/transcriptions` multipart -> JSON transcript plus optional logprobs/confidence. Korean language is explicit. | Dedicated `app.practice.speaking-transcription.*` gate, provider, base URL, secret and model; 30s bounded timeout, 0-3 retries for 429/5xx or transport, 25 MiB default, MIME allow-list. No streaming or cost/token control. | Sequential STT stage inside the asynchronous attempt-evaluation worker. Identity includes learner media row/version/content metadata, transcription model and the downstream evaluation bundle. Stored result retains transcript, normalized transcript, confidence, provider/model, duration, category and retryability. | Audio is loaded only through owner/attempt/question-scoped private Practice media and is not copied to Admin/object storage. Disabled/missing-secret/audio-missing paths are fail closed. Practice metrics exist; global request logs cannot represent audio bytes, media identity, language/logprob policy, retention or deletion. |
| Learner Speaking transcript-grounded evaluation (`Current but disabled by default`) | Transcript, immutable prompt context and optional governed question image -> OpenAI-compatible `/chat/completions` -> strict JSON-schema Korean language profile. The evaluator never receives learner audio or derived acoustic measurements. | Dedicated `app.practice.speaking-evaluator.*` gate, endpoint, secret, model, timeout (30s default), retries (0-3), prompt/rubric/schema versions; `max_tokens=4096`. No stream, provider routing, rate or cost budget. | Runs after STT in the durable attempt worker. SHA-256 contract identity covers both STT and evaluator configs, prompt/rubric/schema and evidence contract. Reuse identity includes attempt/question/version, prompt-context fingerprint/contract, media ID/version, models and versions. | Learner ownership, immutable prompt-version context and image authorization remain Practice-owned. Current capability is `TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION`; pronunciation/fluency acoustic criteria and holistic score remain unavailable. Metrics record outcome/latency, but Admin request logs lack evidence mode and version/fingerprint fields. |
| Speaking text-grounded fallback (`Current gate, disabled by default`) | Learner text plus optional governed question image -> the same transcript-language evaluator JSON schema; no STT and no audio. | Separately gated by `text-fallback-enabled` and still requires the evaluator gate/secret. Same evaluator model/timeouts/retries/token ceiling. | Distinct `TEXT_FALLBACK` source and SHA-256 of normalized text participate in reuse identity; status/provenance is forced to the fallback contract. | It must stay visibly transcript/text-only. Acoustic rows remain `NOT_SCORABLE`, no holistic score is produced and fallback cannot be relabeled as audio evidence. This restriction cannot be expressed by the current Admin provider row; operational enablement remains `BLOCKED_BY_PRODUCT_DECISION`. |
| Direct-audio/multimodal Speaking evaluation (`Reserved, not implemented`) | Planned boundary is authorized learner audio + transcript + optional image -> a separately capable multimodal evaluator; no current client or request shape exists. | No selected provider/model/credential, privacy region, retention, timeout, rate/cost policy or rollout gate has been approved. | `AUDIO_DIRECT_FULL_RESERVED` and `DIRECT_AUDIO_AND_TRANSCRIPT` are extension contracts only. No current code may select them, calculate acoustic/holistic scores or reuse a transcript-only artifact as direct-audio evidence. | Requires product decision, learner consent/withdrawal, reviewer authorization, provider non-training/retention evidence, deletion SLA, Korean acoustic calibration and readiness. Classified `BLOCKED_BY_PRODUCT_DECISION`; this phase performs no implementation or transfer. |
| Reading/Listening typed explanation (`Current`) | Immutable question, answer-spec, long Korean passage or approved transcript, teacher context and zero or more governed images -> OpenAI-compatible `/chat/completions` -> strict discriminated JSON schema. Variants cover single-choice option elimination tables, fill-blank semantic/grammar/register constraints, TFNG reasoning/missing information, exact evidence spans/regions and per-evidence Vietnamese translations. Listening audio itself is never sent. | Practice `openai.*` model/endpoint/secret; operation-specific timeout 60s and shorter than the 5-minute task lease. The HTTP client makes one attempt; durable task retry handles retryable 408/425/429/5xx/transport/contract outcomes, maximum four attempts with bounded exponential delay. No explicit request-token, response-token, rate or cost limit. | Generated asynchronously after publication. Fingerprint includes immutable assessment contract `v1`, input/context `v2`, canonical question/stimulus/answer/media hashes, provider model, prompt `v8-objective-type-native`, response schema `v3` and language `vi`. Artifact, task and immutable question-version binding tables preserve status, input contract, output/error and exact fingerprint. | Shared published explanation has no learner answer. Only approved passage/transcript and digest-matched Practice images are evidence; transcript proves linguistic content only. Missing key/evidence fails closed. Practice task/artifact lifecycle is operational evidence; no Admin provider-attempt log, token count or deletion/retention control is attached. |
| Practice PDF/import question generation with vision (`Current`) | Lecturer-selected PDF text/regions plus cropped images -> OpenAI-compatible `/chat/completions` -> strict document/section/group/question/asset/warning JSON schema. It supports Reading, Listening, Writing and Speaking draft shapes, but never sends the raw PDF as a provider-native file. | Practice `openai.*` model/endpoint/secret and 5s/60s bounded transport; two retries for 429/500/502/503/504. Limits: 50 selected pages, 100 regions, 1,000,000 text characters, 5 MiB per image, 20 MiB total image bytes and 40M rendered pixels. No stream, max-response-token, rate or cost budget. | Synchronous provider call under an owner-scoped 10-minute generation claim; validated request schema `2.0` becomes a Practice draft. `practice_ai_request_audits` records session, prompt `practice-import-v3`, model, strategy, text/image counts/bytes, bounded summary, status/error. It is not a general provider attempt log or immutable model-policy registry. | Lecturer/uploader owns the 24-hour local PDF workspace and generated draft. Only selected regions/crops are sent. Missing key fails before transport. Audit lacks token/cost/provider-attempt and retention/deletion fields; the PDF and crop storage lifecycle remains separate from both AI configuration and global object storage. |
| Lecturer Speaking prompt STT (`Current contract, worker/provider disabled by default`) | Verified lecturer audio in the broad authoring MIME set -> OpenAI `/audio/transcriptions` multipart -> bounded JSON transcript/confidence. | Separate authoring STT gate/provider/base URL/secret/model/language, purpose and retention codes, max 50 MiB/10 minutes, MIME allow-list and 5s/60s defaults. One HTTP call per durable attempt; task max attempts, retry delays, active-task quotas and 20 requests/lecturer/hour are Practice-owned. | Durable leased task and immutable artifact carry lecturer owner, operation fingerprint, source revision, input asset/SHA, provider/model/language, contract, purpose/retention, request reference, transcript/confidence and status. Confirmed lecturer transcript revisions remain traceable. | Configuration must be operational before enqueue mutation. Input audio is a verified Practice `LecturerAsset`; orphan artifacts/assets have a 30-day default cleanup policy subject to references. Metrics and artifact metadata exist, but not an Admin text request log. |
| Lecturer Speaking prompt TTS (`Optional current contract, worker/provider disabled by default`) | Lecturer Korean prompt text -> OpenAI `/audio/speech` JSON -> verified MP3/WAV bytes. | Independent TTS gate/provider/base URL/secret/model/language/voice/speed/format, purpose/retention, 16k input characters, 50 MiB/10-minute output bounds, MIME/format allow-lists and 5s/90s defaults. Same durable task quotas/retry envelope; no streaming or cost/token budget. | Fingerprint includes lecturer owner, normalized exact prompt, model, voice, speed, format, purpose/retention and contract. Immutable artifact records output asset and provider identity; published prompt-version context pins the TTS artifact and active asset. | Output is verified before becoming a Practice lecturer asset and follows Practice reference/cleanup rules. Disabled configuration fails closed. Global object storage and Admin AI rows cannot express voice/format/output-media lifecycle. |
| Wider-product AI question generator (`Current comparator, not a Practice authority`) | Pasted or bounded extracted PDF/DOCX text -> Admin/global text-only `/chat/completions` -> parser-validated MCQ/MR JSON text. No image, raw PDF or audio is sent. | Uses enabled Admin provider rows in display-order fallback; each row supplies one endpoint/model/bearer secret. Global fixed 5s/30s timeout, caller-selected 400 tokens/question, `stream=false`; no per-capability routing/rate/cost configuration. | Synchronous per-user generation with an in-process duplicate guard. Admin prompt name `AI_QUESTION_GENERATOR` is mutable in place with a code fallback. A user/test-owned draft preview persists for 10 minutes and is consumed transactionally on confirmation. | `ai_request_logs` records one metadata/token row per provider attempt and source/user ID, not prompt/response. This proves the Admin plane can serve the existing text generator; it does not prove capability for Practice modalities or identities. |
| No-provider/mock/fail-closed paths (`Current`) | No outbound modality. Writing returns typed unavailable; learner Speaking is skipped/unavailable; R/L tasks and PDF import fail with explicit configuration categories; prompt STT/TTS reject configuration before a call. | Provider gates default off where present and all secrets default empty. `WritingMockEvaluatorService` is retained only as an unreachable/test-compatibility helper; it is not a live fallback authority. | Practice jobs/artifacts retain failed/unavailable/retry state and do not fabricate provider success. Provider-disabled validation must keep AI=0, STT=0 and TTS=0. | No global provider fallback may turn a deliberately disabled Practice capability on. Unused mock constructors and the unused legacy `openai.transcription-model` key remain `DEFER_PRE14/PRE15`, not a reason to commonize. |

## 5. Actual Admin/global AI control-plane capability

| Dimension | Current whole-project/Admin support | Practice-fit conclusion |
|---|---|---|
| Provider/model topology | `ai_providers` supports multiple ordered rows, but one row is exactly one name, OpenAI-compatible chat base URL, one model, one bearer API key, enabled flag and display order. Multiple models are possible only as unrelated fallback rows. | Not a per-capability multi-model router. It cannot pin Writing, R/L, Speaking STT/evaluator, prompt STT/TTS and PDF vision concurrently to independent compatible bundles. |
| API dialect and modality | `AiClient` always posts string system/user messages to `/chat/completions`; payload is model, `max_tokens`, `stream=false` and messages. | Text-only. No multipart audio transcription, binary speech output, image content, provider-native file/PDF input, structured response format or direct-audio request contract. |
| Routing and fallback | Every enabled row is tried in display order after any runtime failure, including a provider-specific 4xx. Admin can test one selected row. | No capability, modality, tenant, lecturer, region, policy-version or data-class route; fallback can change model/credential without preserving a Practice fingerprint. |
| Credentials and limits | One plaintext/revealable global secret per row; fixed 5s/30s client timeouts. Callers pass response token limit. | No modality-specific secret scope, separate STT/TTS credential, input-byte/duration/MIME/voice/format limits, retry policy, rate quota, price, cost budget or context-token policy. |
| Prompts and schemas | `ai_system_prompts` is a mutable named catalog with hard update/delete semantics. The global question generator reads one named prompt with a code fallback. The provider client itself has no prompt binding. | No immutable prompt/rubric/policy/schema versions, capability binding, structured-output schema registry or provider/model compatibility declaration. It cannot own audit re-evaluation identity. |
| Lifecycle/concurrency | Generic client calls are synchronous. The global question generator has a per-process user guard and a 10-minute DB preview. | No durable cross-node leases/retries for concurrent Practice attempt evaluation, R/L publication artifacts, prompt STT/TTS or PDF generation claims. |
| Request logging | `ai_request_logs` stores one provider-attempt row: provider ID/name/model, success/failure, token counts when returned, duration, bounded error, source, actor and time; no prompt/response text. | Useful neutral metadata, but missing logical request/fallback correlation, capability/modality, immutable input/artifact/fingerprint/version IDs, byte/duration/media refs, tenant/lecturer, purpose/retention/deletion and privacy fields. Current Practice direct clients do not write it. |
| Ownership and privacy | Providers/prompts are global Admin settings with `updated_by`; request log has optional `created_by`. | No tenant/lecturer ownership, consent, data region, provider retention/training policy, deletion SLA, reviewer grant or Practice publication/attempt identity. |
| Operations/health | Enabled flag, order and manual test connection; fallback is request-time. | No capability health, model readiness/calibration, circuit/bulkhead, quota/cost alarm or direct-audio privacy gate. |

Conclusion: the Admin layer is **multi-row but single-model-per-row and
text-chat-only**, not a complete multimodal Practice control plane. It can
continue owning the wider-product text question generator. It cannot replace
or route the richer Practice clients without a new capability/routing schema,
immutable version model, lifecycle/audit design and explicit product/privacy
decisions. No such unification is implemented in this phase.

Object/media storage is a separate axis. Admin-configured `ObjectStorage`
selects where global avatar/exam/lesson/library bytes live; it neither selects
AI providers nor supplies request-log, prompt/rubric, evaluation artifact,
fingerprint or provider-retention identity. Practice lecturer assets, learner
audio and PDF workspace likewise do not become AI configuration by holding the
bytes used by a request.

## 6. Capability-mismatch classification

| ID | Classification | Decision |
|---|---|---|
| `CAP-01` | `KEEP_PRACTICE_SEPARATE` | Keep Writing evaluation/re-evaluation, learner Speaking STT/evaluator/fallback, R/L explanations, Practice PDF vision import and lecturer prompt STT/TTS on their current Practice owners and configuration. |
| `CAP-02` | `SHARE_NEUTRAL_CAPABILITY_INTERFACE_ONLY` | A future neutral capability descriptor or privacy-safe provider-attempt metadata port may be considered, but only beneath existing Practice ports and without redirecting consumers/configuration. No interface extraction is needed by the accepted source slices. |
| `CAP-03` | `COMPATIBILITY_ADAPTER` | A future adapter is permissible only for a provider/dialect already proven equivalent for one exact capability and only if Practice remains the configuration, identity and lifecycle authority. No adapter is accepted now. |
| `CAP-04` | `SCHEMA_CONTROL_PLANE_DEBT` | Admin lacks capability/modality routing, multi-model bundles, immutable prompt/rubric/schema identities, modality-specific credentials/limits, durable lifecycle, cost/quota fields and Practice-grade audit correlation/privacy/retention fields. This is recorded debt, not authorization for a schema merge. |
| `CAP-05` | `DEFER_PRE14/PRE15` | Unreachable Writing mock seams, the unused generic transcription key, retained-row cleanup, broader telemetry/retention hardening and release calibration stay in their named later gates. |
| `CAP-06` | `BLOCKED_BY_PRODUCT_DECISION` | Direct-audio Speaking, provider/control-plane unification, credential migration, global fallback for Practice, shared mutable prompts, schema/table merge and consumer cutover require product, academic, privacy, retention and rollout approval. |
| `CAP-07` | `SHARE_NEUTRAL_CAPABILITY_INTERFACE_ONLY` | `AiQuestionImageResolver` is already the narrow governed-image boundary shared by Writing, Speaking and R/L inside Practice. PDF crops remain separate because their session/source-region/multi-image contract differs. No global storage or Admin provider extraction follows. |

The proposed S1 exception-advice move and S2 placement-vocabulary extraction do
not touch any AI class, bean, key, table, route or runtime request in this
matrix. Independent review confirmed S1/S2 remain safe under this audit; no
`COMPATIBILITY_ADAPTER` is warranted. They are re-frozen below.

## 7. De-duplicated classification matrix

| ID | Classification | Decision and owner |
|---|---|---|
| `R-AI-01` | `KEEP_SEPARATE` | Admin/global `AiClient`, mutable provider/prompt administration and `AiRequestLog` remain the wider-product owner. Practice does not import them. |
| `R-AI-02` | `KEEP_SEPARATE` | Practice Writing, R/L explanation and PDF AI remain owned by Practice `OpenAiProperties`, code-owned prompts/contracts, Practice caches/artifacts and purpose-specific audit records. |
| `R-AI-03` | `KEEP_SEPARATE` | Learner Speaking STT/evaluator and lecturer prompt STT/TTS remain separately gated Practice authorities with distinct quotas, purpose/retention and identity. |
| `R-AI-04` | `SHARE_INTERFACE_ONLY` | Existing Practice ports and the narrow `OpenAiAudioHttpTransport` seam are sufficient. They remain Practice-private; there is no new global interface or consumer cutover. |
| `R-AI-05` | `BLOCKED_BY_DECISION` | A common provider-attempt/audit port or Admin provider takeover requires privacy, retention, fallback, quota, credential and immutable-identity decisions not made here. |
| `R-STO-01` | `KEEP_SEPARATE` | Global Admin-configured `ObjectStorage` owns avatar/exam/lesson/library objects. It does not own Practice bytes. |
| `R-STO-02` | `KEEP_SEPARATE` | Practice lecturer assets keep checksum/new-object/image metadata, immutable reference locks and durable lifecycle tasks. |
| `R-STO-03` | `KEEP_SEPARATE` | Learner Speaking keeps its private root, canonical UUID keys, temporary-to-ready promotion, owner/status playback and durable cleanup queue. |
| `R-STO-04` | `KEEP_SEPARATE` | PDF import remains a local, owner-scoped, 24-hour random-access workspace required by PDFBox consumers. |
| `R-STO-05` | `BLOCKED_BY_DECISION` | Practice-to-global object-storage adapters, learner Speaking R2 cutover and global/Practice library-row convergence lack failure-parity, provider-identity, migration and retention decisions. |
| `R-PKG-01` | `SAFE_PACKAGE_MOVE` | Move only the Practice 409/410 exception mappings from global advice to a `PracticeController`-scoped advice. This removes the only production dependency from outside Practice into Practice. Accepted as slice `S1`. |
| `R-PKG-02` | `SAFE_PACKAGE_MOVE` | Extract byte-identical Speaking material placement codes into a non-Spring neutral Practice material vocabulary and retain the old constants as aliases. This removes the sole class-level cycle. Accepted as slice `S2`. |
| `R-PKG-03` | `KEEP_SEPARATE` | Practice entities, routes, DTOs, JPA mappings, AI packages and storage ports remain where they are; cosmetic relocation has no demonstrated value. |
| `R-PKG-04` | `DEFER_PRE14/PRE15` | Metrics-configuration relocation, broad service/controller splitting, legacy routes, readiness-only components and dead common R2/config surfaces remain with the comprehensive dead-surface or release audits. |
| `R-NAME-01` | `KEEP_SEPARATE` | `OpenAi*` is provider terminology, not stale product branding. No active Java/UI `ULP/Ulp/ulp` product name exists. |
| `R-NAME-02` | `DEFER_PRE14/PRE15` | Historical ULP documentation and ignored IDE state stay historical. The applied V54 ULP provenance comment remains byte-identical and checksum-pinned. |
| `R-COMP-01` | `COMPATIBILITY_ADAPTER` | No adapter is accepted in this phase. This category is reserved for a future implementation beneath an existing Practice port after exact parity and migration approval. |

## 8. Frozen accepted slice list

Only these independently reviewed slices may modify production source in this
phase.

### S1 - Practice-owned attempt exception advice

Implemented by `f7727a1cf541103bd996c2d21677c4a2f180bf68`.

- Old owner: `GlobalExceptionHandler` in the project-wide exception package.
- New owner: a Practice controller advice scoped exactly to
  `PracticeController`.
- Dependency direction: project-wide exception handling no longer imports
  Practice; Practice web handling depends only on Practice exceptions.
- Compatibility: preserve exact HTTP 409/410, `ResponseEntity<String>`, message
  body and empty fallback, plus INFO request logging. Existing controller-local
  typed autosave responses and `PracticeSpeakingMediaControllerAdvice` remain
  untouched.
- Rollback: restore the two global imports/methods and delete the scoped advice.
- Why `/practice` is unchanged: the same two exception types are mapped to the
  same status and body; only resolver ownership narrows to their sole consumer.

### S2 - Neutral Practice material-placement vocabulary

Implemented by `988389655cf43e5db28c56c06b40d510c442c040`.

- Old owners: `PracticeAssessmentExcelService` and
  `SpeakingPromptAssetService`.
- New owner: one non-Spring Practice material value holder.
- Dependency direction: Excel, material and Speaking services depend on the
  neutral value holder; `SpeakingPromptLifecycleService` no longer imports the
  Excel service merely to read a constant.
- Compatibility: exact persisted values `SPEAKING_PROMPT_ORIGINAL`,
  `SPEAKING_PROMPT_TTS` and `SPEAKING_PROMPT_EXCEL_STAGING` do not change. Old
  source fields remain constant aliases. No DB row or migration changes.
- Rollback: restore direct constant ownership/usages and delete the holder.
- Why `/practice` is unchanged: placement comparisons and persisted bytes are
  identical; lifecycle, authorization, cleanup, immutable references and media
  identity are not changed.

### S3 - Decision map and phase evidence

- Update this live log and active roadmap overlays with the audited ownership
  map, validation result and final publication evidence.
- No historical log is rewritten; current overlays supersede stale current
  action text.

Everything else is explicitly excluded. There is no AI/storage/Admin authority
unification, new adapter, bean replacement, config-key migration, credential
move, route/DTO/JSON change, schema/table change, consumer cutover or migration
edit.

## 9. Validation unit

Pre-validation static review passed on the complete phase diff: no whitespace
error; no remaining global-to-Practice exception import; no
Speaking-lifecycle-to-Excel import; only the neutral holder owns the three
persisted placement literals; no route/config/migration/template/static-resource
change; Flyway remains unique/contiguous V1-V62; the checksum-pinned V54 file is
still `f03dfecb7e6c9e4ea4ec2b66b893d249ea988d38e158f614b85e49a20ac828ba`.
The only live-source ULP match remains that immutable migration comment.

The phase validation unit ran on OpenJDK `17.0.19` with provider credentials
empty and every provider gate disabled:

1. `git diff --check` and static package/import/bean/config/route/ULP/migration
   scans, including no remaining lifecycle-to-Excel import and one canonical
   literal owner;
2. one clean package with tests skipped;
3. the focused S1/S2 tests plus provider-disabled R/L/W/S and local/global
   storage/media contract tests;
4. fresh Flyway V1-V62 plus Hibernate/Tomcat startup because S1 changes MVC
   advice wiring;
5. the full suite because this branch will be published to `main` across a
   project-wide exception boundary;
6. record real provider-call totals AI=0, STT=0 and TTS=0.

Result:

- static scans: green after the complete diff; `git diff --check`, package,
  import, advice scope, config, route, legacy-name and duplicate-literal scans
  all passed. Flyway remains unique and contiguous V1-V62. The immutable
  `V54__ai_system_prompts.sql` checksum remains
  `f03dfecb7e6c9e4ea4ec2b66b893d249ea988d38e158f614b85e49a20ac828ba`;
- package: green once on Java 17;
- focused boundary/Practice regression selectors: `230/230`, failures `0`,
  errors `0`, skipped `0`;
- runtime: a new disposable schema migrated V1-V62 with `62` successful and
  `0` failed migrations; Hibernate/Tomcat started and `/` returned HTTP `302`;
- full suite: `2631/2631`, failures `0`, errors `0`, skipped `0`;
- provider evidence: global `ai_request_logs=0` and Practice
  `practice_ai_request_audits=0` in focused, runtime and full-suite schemas;
  real calls remained `AI=0`, `STT=0`, `TTS=0`;
- disposal: all three exact-name validation schemas and the runtime process
  were removed after evidence capture.

The first lifecycle reached the full suite but the local MySQL server hit its
pre-existing connection ceiling (`max_connections=151`,
`Max_used_connections=152`). All six error-bearing report groups had the same
root `Too many connections` cause and produced `212` cascading context errors,
with no product assertion failure. The one grouped correction capped each
disposable-test Hikari pool at two connections with zero idle connections. The
entire lifecycle was then rerun once and produced the green result above; no
source edit was made in response to the infrastructure failure.

## 10. Publication gate

The two production ownership commits are recorded with S1/S2 above. This
decision/evidence log is the third coherent slice. Publication remains gated on
a fresh `origin/main` comparison, one push, a PR to `main`, complete
diff/ancestry/excluded-path review and required checks. Merge only with
`Create a merge commit`, then verify the remote main SHA/tree and stop.
