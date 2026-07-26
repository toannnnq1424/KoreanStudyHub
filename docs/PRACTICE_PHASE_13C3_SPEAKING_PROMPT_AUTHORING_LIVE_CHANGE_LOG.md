# Practice Phase 13C3 Speaking Prompt Authoring Correction

Last updated: 2026-07-26

## 1. Gate status and execution order

- `PHASE_13C3_DESIGN = LOCKED`
- `PHASE_13C3_IMPLEMENTATION = NOT_STARTED`
- `PHASE_13C3_VALIDATION = NOT_STARTED`
- `PHASE_13F_PREREQUISITE = COMPLETE_FOCUSED_GATE_GREEN`
- `CURRENT_REQUIRED_ACTION = 13C3-00`
- `PHASE_14 = BLOCKED`

This correction is mandatory before Phase 14 and has the following fixed
execution order:

```text
13C3-00 contract/migration lock
  -> 13C3-01 persistence/provider orchestration
  -> 13C3-02 Editor UI/API
  -> 13C3-03 publish/player/evaluator identity
  -> 13C3-04 compatibility/reconciliation
  -> one consolidated Phase 13C3 validation
  -> Phase 13G
  -> Phase 13H
  -> PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE
  -> Phase 14A-14F
```

The `13C3` name records capability ownership: it corrects the skill-native
Speaking authoring/player contract delivered by 13B/13C. Phase 13F has now
passed its bounded focused gate; its evidence and history remain separate from
this migration/provider/Editor program. Phase 14 remains “Report an Error &
Content Review” and must not become the owner of question authoring.

Each implementation unit is a separate Codex task, but the complete correction
is one validation unit:

| Unit | Scope | Dependency |
| --- | --- | --- |
| `13C3-00` | Lock v2 contract, forward migration, compatibility decision, provider-neutral configuration and live-log inventory. | Phase 13F consolidated gate green. |
| `13C3-01` | Lecturer-owned source/artifact/task persistence, asset bindings, STT/TTS adapters, fingerprints, leases, retry and stale-result reconciliation. | `13C3-00`. |
| `13C3-02` | Editor UI/API, upload/manual modes, state polling, preview, explicit generate/regenerate, retry and stale copy. | `13C3-01`. |
| `13C3-03` | Validator, publisher, immutable version context, learner player branches and evaluator question-context identity. | `13C3-01..02`. |
| `13C3-04` | Excel boundary, authorization/cleanup, compatibility reconciliation, tests/docs/diagram inventory and `READY_FOR_PHASE_VALIDATION`. | `13C3-03`. |

No test, compile, build, lint, startup, database, migration, provider, browser or
Git action has run for this design checkpoint.

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

The current source is audio-only:

- `QuestionContent.SpeakingDelivery` in
  `src/main/java/com/ksh/features/practice/assessment/QuestionContent.java`
  stores prompt audio and timing only under `question-content-v1`;
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

## 6. API and provider flow

Use a dedicated lecturer-authoring boundary; do not modify generic
`/upload-audio` to transcribe every Listening/group audio.

| Endpoint | Behavior |
| --- | --- |
| `PUT /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt` | Save mode, text hash, TTS toggle/config and expected revision. No provider call. |
| `POST .../speaking-prompt/audio` | Verify/store original audio, update revision, bind asset and enqueue one STT task; return `202`. Never call TTS. |
| `GET .../speaking-prompt` | Lecturer-authorized current source/artifact/task state for reload/polling. No provider call. |
| `POST .../speaking-prompt/transcription/retry` | Manual retry only for a current retryable/needs-review source, with rate limit. |
| `POST .../speaking-prompt/tts` | Explicit Generate/Regenerate for current manual text/config; idempotently return existing task/artifact or `202`. |
| `DELETE .../speaking-prompt/audio` | Unlink current draft asset; never delete an asset referenced by a published version. |

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

Add the next available **forward** Flyway migration at implementation time.
Never edit an applied checksum. A later guarded Practice-only rebaseline may
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
16. Draft cleanup never deletes an asset retained by a published version.
17. Accepted upload types, byte limits and provider compatibility are identical
    across UI, controller, verification and STT configuration.
18. Missing key, timeout, 429/5xx, invalid media, no speech, empty transcript,
    malformed response and invalid generated audio have bounded, correctly
    retryable Vietnamese states.
19. Korean Unicode, punctuation, spacing and line breaks survive storage,
    fingerprinting, TTS and immutable snapshot without lossy normalization.
20. One approved cost-bounded real STT/TTS smoke is recorded before Phase 14 if
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

A validation failure is handled as one batch:

```text
analyze the complete failure set
  -> group root causes
  -> one concentrated fix cycle
  -> rerun the same validation unit once
```

Phase 13G cannot start until the complete correction is accepted. Phase 14
cannot start until 13G, 13H and the pre-14 production-correctness gate also pass.
