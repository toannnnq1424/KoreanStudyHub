# Practice Authoring Import Modernization Contract

Recorded: `2026-08-02`

Status: `AIM_1_CONTRACT_FROZEN_IMPLEMENTATION_NOT_STARTED`

Authority: ADR 0012 and
`PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`.

## 1. Merged-code reconciliation

This contract was written against refreshed
`origin/main@3dfab18ae308005c018c9066256bb7c79b686e8e`, not an earlier audit
snapshot.

| Area | Current merged behavior | Frozen modernization decision |
|---|---|---|
| Excel route context | `/practice/manage/excel` authorizes `draftId`, `testNo` and `lessonCode`; the service discovers skill from the selected section. | Quick v1 requires `draftId`, `testNo`, `skill`, `lessonCode`; the server compares all four against the authorized current draft section. The file cannot carry or override target context. |
| Excel format detection | `01_THONG_TIN_SET` selects advanced v2; otherwise five legacy sheets are required. | Detect Quick v1 first, then preserve advanced v2 and legacy v1. A malformed Quick sentinel is a Quick validation error, not a fallthrough. |
| Excel mutation | Preview produces normalized draft JSON and import saves/merges the linked `PracticeDraft`. | Parser produces only a persistent candidate. Only explicit candidate apply may mutate the draft. |
| PDF mutation | `PracticePdfDraftAssembler` parses provider JSON and creates or merges `PracticeDraft`; `PracticeImportDraftService` can copy/merge that temporary draft. | Strict PDF authoring output is normalized into the shared candidate. Direct create/copy/merge paths are retired only after candidate parity; no second apply path survives. |
| PDF snapshot | `snapshotJson` stores selected pages, current page, extraction strategy and annotations. | Keep it as Advanced workspace restore state. Never use it as candidate data, apply identity or provider output. |
| Canonical draft/editor | `practice-draft-v3`, `PracticeDraftContractService`, `PracticeDraftValidator`, one editor and `PracticePublisherService`. | Reuse them. Candidate review is not another draft editor; publisher input remains only an authorized `PracticeDraft`. |
| AI | Practice has `PracticeStructuredGenerationPort` and Practice clients/transports; global `AiClient` uses enabled provider fallback. | Admin supplies exact purpose bindings; Practice resolves one binding and uses a Practice client/transport. No global client or fallback. |
| Storage | Global uploads use `ObjectStorage`; Practice lecturer assets, PDF workspace and learner audio use separate local services/lifecycles. | Admin supplies named profiles; Practice keeps distinct authoring and Speaking adapters/lifecycles. Profile identity is persisted and writes fail closed. |
| Flyway | The refreshed tree currently contains duplicate version filenames for V73, V74 and V75 from independently merged lines. | AIM-1 adds no SQL and freezes no migration number. Before AIM-2 SQL, reconcile the actual integrated chain without editing applied bytes, then allocate the next globally free version. |

## 2. Quick Excel v1 workbook contract

### 2.1 File identity and detection

The workbook contains exactly one non-hidden worksheet named
`QUICK_QUESTIONS`.

```text
A1 = KSH_PRACTICE_QUICK_EXCEL
B1 = practice-quick-excel-v1
row 3 = exact header row below
row 4+ = question rows
```

Rows after the last non-empty `question_key` are ignored only when every cell
in the row is blank. Merged cells, formulas, macros, external links, hidden
data sheets and extra non-empty columns are blocked. Formula cells are not
evaluated. Upload remains `.xlsx` and uses the existing bounded file-size and
ZIP/workbook safety controls.

Detection is deterministic:

1. if either Quick sentinel cell or the `QUICK_QUESTIONS` name is present,
   validate the complete Quick identity and never fall through;
2. otherwise, if `01_THONG_TIN_SET` exists, use advanced v2;
3. otherwise, if the exact legacy sheet set exists, use legacy v1; and
4. otherwise return `WORKBOOK_SCHEMA_UNSUPPORTED`.

The current advanced template endpoint and both current readers remain
compatible. Quick uses a separate template action or an explicit
`format=quick-v1`; it does not replace the advanced download.

### 2.2 Exact columns

The row-3 headers, in order, are:

```text
group_key
group_label
group_instruction
stimulus_text
question_key
question_order
question_type
writing_task
prompt
option_a
option_b
option_c
option_d
option_e
option_f
option_g
option_h
correct_answer
blank_1_answers
blank_2_answers
points
teacher_explanation_vi
preparation_seconds
response_seconds
```

Text is normalized to Unicode NFC, line endings to LF and surrounding
whitespace is trimmed. Internal whitespace in prompts, stimuli and explanations
is preserved. IDs use `[A-Za-z0-9._-]{1,80}` and are unique within the
workbook. Answer lists use `|` as the only separator; an answer containing a
literal `|` is not a Quick-v1 value and must use Advanced/editor.

### 2.3 Column-to-candidate mapping

| Excel column | Candidate field | Canonical apply target | Rule |
|---|---|---|---|
| `group_key` | `candidateGroupId` | new group `clientId` seed; final `groupCode` is server-generated | Required; repeated rows form one group. It never names an existing database group. |
| `group_label` | group `label` | group `label` | Required on the first group row; repeated non-empty values must be byte-equivalent after normalization. |
| `group_instruction` | group `instruction` | stimulus/group instruction | Optional; group-consistent. |
| `stimulus_text` | group `stimulus.passageText` or `transcriptText` | `practice-stimulus-v2` | Reading maps to passage, Listening to transcript. Writing/Speaking must leave it blank. Media references are not accepted. |
| `question_key` | `candidateQuestionId` | question `clientId` seed | Required and unique; stable across review/retry. |
| `question_order` | `questionOrder` | order within the new Auto group | Positive integer, unique within group. It is not the published question number. |
| `question_type` | `questionType` | canonical question type | Exact uppercase enum; only the v1 support matrix below. |
| `writing_task` | `essayTaskType` | Writing task identity | Required only for Writing and exactly `Q51`, `Q52`, `Q53` or `Q54`. |
| `prompt` | `prompt` | question prompt | Required; Q51/Q52 may describe two response blanks but objective blank-token syntax is not reused. Manual Speaking text must contain Korean. |
| `option_a`..`option_h` | `questionContent.options[]` as `opt_A`..`opt_H` | typed content plus editor option view | Contiguous from A; 2–8 for single/multiple choice. Blank for all other types. Media options are unsupported. |
| `correct_answer` | typed correct IDs/value | `answerSpec` | Single choice: one letter. Multiple answer: at least two unique letters separated by `|`. TFNG: `TRUE`, `FALSE` or `NOT_GIVEN`. Blank otherwise. |
| `blank_1_answers` | objective `blank_1` or Writing Q51/Q52 blank 1 authority | typed blank/authority | Objective FILL_BLANK requires one or more accepted values. Q51/Q52 requires one or more exact accepted answers. Blank for other types. |
| `blank_2_answers` | Writing Q51/Q52 blank 2 authority | `writing-blanks.v1` blank 2 | Required only for Q51/Q52. Its presence for objective FILL_BLANK makes the row complex and routes it to Advanced. |
| `points` | `points` suggestion | canonical points | R/L optional, default 1, positive. Writing is blank or must equal `10/10/30/50`. Speaking is blank or must equal the current profile default; the server remains authority. |
| `teacher_explanation_vi` | `explanationVi` | editor/publisher field | Optional. It never becomes AI evaluation feedback. |
| `preparation_seconds` | Speaking delivery | typed `speakingDelivery.preparationSeconds` | Speaking only, 0–600; default 30 when blank. |
| `response_seconds` | Speaking delivery | typed `speakingDelivery.responseSeconds` | Speaking only, 1–1800; default 60 when blank. |

Quick v1 has no target, media, layout, source-region, provider, evaluation,
score, rubric, publication or storage columns. An extra header that attempts to
add one is `QUICK_COLUMN_UNSUPPORTED`.

### 2.4 Supported row matrix

| Route skill | Quick row | Required authority | Candidate review required before apply |
|---|---|---|---|
| Reading/Listening | `SINGLE_CHOICE` | 2–8 options and one correct option ID | Lecturer selects one allowed current R/L explanation strategy. |
| Reading/Listening | `MULTIPLE_ANSWER` | 2–8 options and at least two correct option IDs | Lecturer selects one allowed current R/L explanation strategy. |
| Reading/Listening | `TRUE_FALSE_NOT_GIVEN` | one canonical value | Lecturer selects one allowed current R/L explanation strategy. |
| Reading/Listening | simple `FILL_BLANK` | exactly `{{blank:blank_1}}` once in the normalized prompt and accepted values in `blank_1_answers` | Lecturer selects one allowed current R/L explanation strategy. |
| Writing | Q51/Q52 `ESSAY` | two stable response blank definitions and two typed exact-answer authorities | Reviewer confirms both blank contexts/answers; fixed question number and points are server-owned. |
| Writing | Q53/Q54 `ESSAY` | task identity, prompt and fixed points | Reviewer confirms task; no structured blank authority is allowed. |
| Speaking | `SPEAKING` | `question-content-v3`, `manual_text`, `text_only`, `none`, no audio/play limit | Reviewer confirms Korean prompt and timing. No STT/TTS task is created. |

Quick v1 blocks `MATCHING`, image/audio/media references, Speaking upload/TTS
modes, two-or-more objective blanks, per-blank normalization rules, regex or
semantic answer equivalence, custom scoring, arbitrary group layout and mixed
skills. Its response is `ADVANCED_AUTHORING_REQUIRED`, not a lossy conversion.

Quick appends new Auto groups to the authorized target section. It does not
replace existing groups or update a group by a spreadsheet key. The canonical
normalizer assigns final group codes and R/L/S question numbers. Applying a
Writing candidate is allowed only when the simulated full section has exactly
one Q51, Q52, Q53 and Q54 under current point contracts. A Listening target
must already have its required check-audio authority; Quick cannot create it.

These restrictions belong to the `QUICK_EXCEL` source policy, not to the
canonical candidate envelope. Candidates adapted from `ADVANCED_EXCEL_V2`,
`LEGACY_EXCEL_V1` or `PDF_AI` may carry the canonical `MATCHING`, media,
multi-blank and typed Speaking shapes already allowed by their owning format
and current codecs. The server always applies source-specific validation; a
caller cannot relabel a Quick source to escape Quick rules.

## 3. Canonical candidate contract

The machine-readable envelope is
`schemas/practice-authoring-candidate-v1.schema.json`.

Source identity is an exact pair and is checked before normalization:

| `source.kind` | `source.contractVersion` | Source-specific authority |
|---|---|---|
| `QUICK_EXCEL` | `practice-quick-excel-v1` | The exact one-sheet/24-column restrictions in Section 2. |
| `ADVANCED_EXCEL_V2` | `practice-excel-v2` | Current V2 type, media, material and workbook semantics. |
| `LEGACY_EXCEL_V1` | `practice-excel-v1` | Historical candidate-envelope identity only. The current interactive workbook entry point deterministically rejects legacy v1 with `LEGACY_EXCEL_V1_RETIRED`; there is no current parser or writer. Retain the enum/schema identity until stored candidate inventory authorizes removal. |
| `PDF_AI` | `practice-pdf-authoring-output-v1` | Exact `EXTRACT`/`GENERATE`, request evidence and binding snapshot. |

A mismatched pair fails closed. `operation` and `aiExecution` are required only
for `PDF_AI`; they are forbidden for Excel sources. JSON Schema locks the
structural vocabulary, while the server validator enforces these cross-field
and source-specific invariants before candidate state can become `VALIDATED`.

### 3.1 Identity and lifecycle

```text
PARSED
  -> NORMALIZED
  -> VALIDATED
  -> REVIEWING
  -> READY_TO_APPLY
  -> APPLIED

Any pre-apply state -> REJECTED or EXPIRED
Validation/provider/source failure -> FAILED (retry creates or reuses by the
same idempotency identity; it never writes a draft)
```

Required immutable identity:

- `candidateId` UUID;
- owner and authorized target `draftId/testNo/skill/lessonCode`;
- target `baseDraftVersion` captured when the candidate is created/refreshed;
- source kind, source contract version and SHA-256 source digest;
- normalizer and validator versions;
- stable candidate group/question IDs;
- canonical candidate JSON digest; and
- optional exact PDF AI binding/provider/request snapshot.

Candidate content is mutable only through optimistic-lock review while not
applied/rejected/expired. Every accepted review update recalculates validation
and digest. `READY_TO_APPLY` requires no blocking issues and explicit
acknowledgement of warnings.

### 3.2 Relational target

The forward schema uses these logical shapes; exact SQL types/index names are
owned by AIM-2 after the integrated migration-chain rescan.

`practice_authoring_candidates`:

```text
id UUID/CHAR PK
owner_id FK users
source_kind QUICK_EXCEL | ADVANCED_EXCEL_V2 | LEGACY_EXCEL_V1 | PDF_AI
source_contract_version
source_digest CHAR(64)
source_revision
target_draft_id FK practice_drafts
target_test_no
target_skill
target_lesson_code
base_draft_version
state
normalizer_version
validator_version
candidate_json LONGTEXT
content_digest CHAR(64)
warning_acknowledged_at/by
expires_at
applied_at
applied_draft_version
lock_version
created_at / updated_at
```

The idempotency unique key covers owner, source kind/digest/revision, target
identity, base draft version and normalizer version. Re-uploading identical
bytes to the same unchanged target returns the same candidate; changed source
or target version creates a distinct candidate.

`practice_authoring_candidate_apply_events`:

```text
id PK
candidate_id FK
apply_request_id UUID
candidate_version
candidate_digest
base_draft_version
result DRAFT_APPLIED | CONFLICT | REJECTED
result_draft_version nullable
actor_id
created_at
UNIQUE(candidate_id, apply_request_id)
```

Events contain no workbook/PDF bytes, prompt body, provider body or secret.
Candidate expiration is at least seven days and configurable; an applied
candidate/event is retained through the epic audit window and then follows the
approved authoring-audit retention policy. Source PDFs/assets follow their own
storage lifecycle and are not kept alive merely by copying bytes into JSON.

### 3.3 Explicit apply algorithm

The server performs these steps in one transaction:

1. authorize actor for candidate and `PracticeAction.EDIT` on the exact draft;
2. lock candidate and draft in stable order;
3. if the `(candidateId, applyRequestId)` event already succeeded, return the
   recorded draft/version;
4. require candidate `READY_TO_APPLY`, submitted candidate version/digest and
   current draft version equal to `baseDraftVersion`;
5. locate the exact draft section matching all of test, skill and lesson;
6. append reviewed groups/questions in Auto order to an in-memory copy;
7. call the canonical draft normalizer and strict codecs;
8. run the canonical draft validator and source/material authority checks;
9. on any blocker, persist only a rejected/conflict apply event as appropriate
   and leave the draft unchanged; and
10. save one draft version, record one successful event and mark the candidate
    `APPLIED`.

There is no partial apply. Publication remains a separate explicit action in
the existing editor/publisher.

## 4. Candidate-to-draft/editor/publisher mapping

| Candidate | `PracticeDraft` after apply | Editor/publisher behavior |
|---|---|---|
| `target` | selects one existing section; never stored as imported document metadata authority | Editor route and draft authorization remain unchanged. |
| group `candidateGroupId` | new stable group `clientId`; server creates final `groupCode`, range and ordering | Existing group editor owns all later edits. |
| group stimulus | normalized `practice-stimulus-v2`, source `QUICK_EXCEL` or `PDF_AI`, `approved=false` until lecturer review | Current preview/publisher material and evidence validation apply. |
| question ID/order/type/prompt/points | canonical draft question fields; number and fixed Writing points are server-owned | Existing type-specific editor and validator own later mutation. |
| `questionContent` | strict current `question-content-v1/v3` as produced by server codecs | Existing player/publisher dual-read remains authoritative. |
| `answerSpec` | strict `answer-spec-v1` | Never inferred from explanation/provider prose. |
| Writing blank response/authority | `writing-blanks.v1` plus `writing-blank-authority.v1` | Current Q51/Q52 editor/validator; Q53/Q54 reject these fields. |
| R/L explanation selection | exact current registry/strategy version selected in review | Existing editorial approval and published artifact lifecycle remain required. |
| Speaking delivery | manual text, text only, no audio | Existing Speaking authoring/publish contract; no STT/TTS task. |
| warnings/issues | not copied into learner content; selected source/review provenance may be stored in safe draft metadata | Editor shows validation; publisher never exposes internal issue/debug text. |

## 5. PDF AI authoring contract

### 5.1 Basic and Advanced entry paths

Basic supports pasted Text or an uploaded PDF:

```text
Text -> EXTRACT when structure is present, or GENERATE when author asks for
        new questions
PDF  -> bounded text extraction -> EXTRACT/GENERATE
     -> optional bounded page images only when the active purpose capability
        and request policy allow image input
```

Advanced preserves the current page range, crop, region lock, region note,
asset and workspace-restore UX. It adapts its selected evidence to the same
strict provider output and candidate boundary. Basic does not expose crop or
region controls.

Both paths call only purpose `PRACTICE_PDF_AUTHORING`. Operation is exactly
`EXTRACT` or `GENERATE` and is part of idempotency/audit identity.

### 5.2 Provider output

The provider must return only
`practice-pdf-authoring-output-v1`, defined in the adjacent schema. The server
rejects unknown fields. Required source references are stable text spans,
page numbers and/or Advanced region IDs that were actually in the request.

The following keys are forbidden at every depth outside quoted source text:

```text
evaluation_status, evaluation_source, evaluation_reason, score,
overall_score, rubric, criteria, findings, feedback, upgraded_answer,
transcript_alignment, audio_alignment, phonemes, stress, pronunciation,
fluency_score, publish, draftId, targetDraftId
```

The authoring normalizer independently:

- rejects unknown types/skills and cross-skill output;
- creates server-owned candidate IDs when safe source IDs are absent;
- rebuilds all typed content/answer objects with the canonical codecs;
- verifies every source reference belongs to the request;
- marks low-confidence or incomplete output for review;
- never trusts provider points, group codes, question numbers, strategy,
  storage keys, media URLs or publication state; and
- emits one persistent candidate or a structured failure, never a draft.

## 6. Practice AI control plane

### 6.1 Purpose/capability map

| Purpose | Required data-plane capability in v1 | Current/epic boundary |
|---|---|---|
| `PRACTICE_PDF_AUTHORING` | strict text JSON; optional image input only when declared/tested | Authoring only; never evaluation JSON. |
| `PRACTICE_RL_EXPLANATION` | strict text/image JSON schema | Current R/L artifact/editorial lifecycle remains. |
| `PRACTICE_WRITING_EVALUATION` | strict text/image JSON schema | Current Writing policy/evidence/cache identity remains. |
| `PRACTICE_SPEAKING_EVALUATION` | transcript text plus allowed question image, strict JSON | Transcript-only v1. Direct learner audio is not a declared capability. |
| `PRACTICE_SPEAKING_STT` | bounded batch transcription | Request operation distinguishes learner response from authoring prompt and retains separate privacy/retention data class. |
| `PRACTICE_SPEAKING_TTS` | bounded speech synthesis | Lecturer prompt output only; never learner response audio. |

### 6.2 Relational target

`practice_ai_provider_profiles` contains Admin-managed connection identity:

```text
id, profile_code, display_name, provider_family, base_url,
credential_secret (masked/reveal-gated under the existing accepted secret
handling boundary), enabled, revision, updated_by, timestamps
```

`practice_ai_purpose_bindings` contains one row per exact purpose:

```text
purpose_code PK, provider_profile_id FK, model,
transport_dialect, capability_json, limits_json, retention_code,
enabled, revision, updated_by, timestamps
```

The primary-key-per-purpose design makes more than one active binding
impossible in v1. Updating a binding increments its revision. A job persists a
redacted snapshot of purpose, binding revision, provider family/profile code,
model, transport dialect and relevant contract versions before calling.

`practice_ai_capability_test_runs` records:

```text
id, purpose_code, binding_revision, required_capability,
status PASS | FAIL | CANCELLED, duration_ms, bounded_error_code,
tested_by, started_at, completed_at
```

No logical Practice request selects `ai_providers` or asks global `AiClient` to
fallback. Admin CRUD is control plane; request construction, transport,
decoding, retry classification, idempotency, audit, metrics and lifecycle stay
in Practice. Retry may repeat the same binding for retryable failures within
its bound; it cannot select a second binding.

Purpose capability tests use bounded, project-owned fixtures and the real
purpose adapter when explicitly invoked by an authorized Admin. Default CI and
all AIM-0/AIM-1 validation use disabled fakes and make zero provider calls.

## 7. Storage profile and migration contract

### 7.1 Profiles and adapter ownership

| Profile | Bytes | Data-plane adapter | Access and retention |
|---|---|---|---|
| `GENERAL_UPLOADS` | avatar, exam, lesson and library uploads | existing wider-product `ObjectStorage` family | Existing public/private route rules and bounded legacy behavior. It is never a Practice fallback. |
| `PRACTICE_AUTHORING` | source PDF, extracted/cropped lecturer assets and draft/published Practice material | Practice-owned authoring storage adapter | Private by default; authorized lecturer/app delivery; unbound temp/crop/PDF workspace expires within 24h; bound/published asset follows reference locks and durable cleanup. |
| `PRACTICE_SPEAKING` | learner response audio | Practice-owned Speaking audio adapter | Private bucket/root, no public URL, exact owner/attempt/question/media authorization, temporary-to-ready promotion and durable deletion. |

`storage_profiles` is additive:

```text
profile_code PK
backend LOCAL | R2
account_id
access_key_id
secret_access_key (masked/reveal-gated under the existing accepted boundary)
bucket
endpoint
region
key_prefix
enabled
revision
updated_by
created_at / updated_at
```

R2 buckets are private. `key_prefix` is fixed/validated per profile and an
object key cannot escape it. A profile may be locally backed only in an
explicit development/test configuration. Production R2 writes require a
complete enabled profile; otherwise they fail closed.

### 7.2 Logical-row identity and bounded compatibility

Add nullable `storage_profile_code` where exact location is not already
expressible, including Practice lecturer assets, learner Speaking media and
PDF import sessions. Do not overload current provider enums/strings with a
compound value.

Read rules:

1. non-null profile code opens only that exact profile and key;
2. null on an existing Practice row means the current legacy local root only;
3. no read searches `GENERAL_UPLOADS`, another Practice profile or every
   configured bucket; and
4. legacy local read is removed only after a persisted inventory reaches zero
   or every retained object has a verified migrated identity.

Migration of one object is copy -> size/hash verify -> transactional logical
row update -> delayed old-object delete through the existing durable lifecycle.
Failure before the row update leaves the old object authoritative. New writes
never use the legacy-null identity.

### 7.3 Learner-audio lifecycle

```text
UNREFERENCED_TEMPORARY --promote after validated DB transaction--> READY
READY --new recording--> SUPERSEDED --durable cleanup--> DELETED tombstone
READY --attempt discard/approved deletion--> DELETION_PENDING
DELETION_PENDING --physical confirmation--> DELETED tombstone
```

- unreferenced temporary object: delete by 24h;
- superseded object: enqueue immediately and delete by 24h unless a retained
  reference blocks it;
- READY object: retain with the immutable attempt; do not age-delete while the
  attempt is retained;
- failed physical delete: keep task pending with bounded backoff and observable
  error; never mark bytes deleted before confirmation; and
- playback/download: app-authorized server response only, no public/presigned
  URL in v1.

Provider/evaluator transfer is a separate data-use event. Storage migration or
an active `PRACTICE_SPEAKING` profile does not authorize it.

### 7.4 Rollback

The migration is forward-only. Rollback means:

- disable new Quick/PDF candidate entry and Practice purpose bindings;
- stop new R2 writes for the affected profile;
- retain new tables/columns and candidate/apply audit rows;
- read already persisted profile-coded objects through the Practice adapter;
- continue legacy-local reads for null-profile rows; and
- restore the prior application version only after exporting any changed
  `GENERAL_UPLOADS` profile back to its legacy settings representation.

No rollback drops schema, deletes candidate rows, rewrites applied migrations,
moves bytes blindly or reverts an already applied draft edit outside normal
draft revision/history semantics.

## 8. Review, validation and error model

### 8.1 Review surface

The candidate page shows Auto-ordered groups, then questions. It may edit or
reject candidate fields and select required R/L strategies. It does not expose
the entire draft graph or own draft autosave.

`View as learner` builds an in-memory full-draft projection at the current
base version, normalizes and validates it, then uses the canonical learner
preview presenter/template. When the draft version changes, the projection is
stale and preview/apply returns a conflict. No projection is stored as a draft.

### 8.2 Issue envelope

Every issue has:

```text
severity: ERROR | WARNING | INFO
code: stable ASCII machine code
scope: WORKBOOK | SOURCE | CANDIDATE | GROUP | QUESTION | FIELD | TARGET
path: JSON Pointer into candidate content when applicable
sourceLocation: sheet/row/column or page/region/text span
messageVi: learner-safe lecturer message
remediation: EDIT_IN_REVIEW | USE_ADVANCED | FIX_SOURCE | RETRY | REOPEN_TARGET
blocking: true | false
```

The concrete example is
`examples/practice-authoring-validation-errors.json`. UI ordering is severity,
group order, question order, field. Logs keep only codes, IDs and bounded
metadata; workbook/PDF text, provider bodies, secrets and learner audio are not
logged.

Required stable error families include:

| Family | Examples | Apply behavior |
|---|---|---|
| Workbook/file | `QUICK_SHEET_COUNT_INVALID`, `QUICK_SENTINEL_INVALID`, `QUICK_HEADER_INVALID`, `WORKBOOK_SCHEMA_UNSUPPORTED` | No importable content; draft untouched. |
| Row/type | `QUESTION_TYPE_NOT_SUPPORTED_BY_QUICK`, `QUESTION_TYPE_NOT_ALLOWED_FOR_SKILL`, `OPTION_AUTHORITY_INVALID`, `SIMPLE_BLANK_REQUIRED`, `ADVANCED_AUTHORING_REQUIRED` | Candidate may persist for review, but blocker prevents READY/apply. |
| Writing/Speaking | `WRITING_TASK_CARDINALITY_INVALID`, `WRITING_BLANK_AUTHORITY_INVALID`, `SPEAKING_MODE_QUICK_UNSUPPORTED`, `SPEAKING_MANUAL_PROMPT_KOREAN_REQUIRED` | No lossy fallback or fabricated audio. |
| R/L/publish readiness | `EXPLANATION_STRATEGY_REVIEW_REQUIRED`, `LISTENING_CHECK_AUDIO_REQUIRED` | Fix in candidate/current editor before apply; no inferred strategy/media. |
| PDF/provider | `PDF_AUTHORING_SCHEMA_INVALID`, `PDF_SOURCE_REFERENCE_UNKNOWN`, `PROVIDER_PURPOSE_UNAVAILABLE`, `PROVIDER_BINDING_CHANGED` | Candidate/draft untouched; retry remains on the same purpose identity or creates a new revision-bound run. |
| Candidate/apply | `CANDIDATE_VERSION_CONFLICT`, `TARGET_DRAFT_VERSION_CONFLICT`, `CANDIDATE_NOT_READY`, `CANDIDATE_EXPIRED`, `APPLY_REQUEST_MISMATCH` | Atomic no-op; an exact successful replay returns its recorded result. |
| Storage | `STORAGE_PROFILE_UNAVAILABLE`, `STORAGE_IDENTITY_INVALID`, `PRIVATE_OBJECT_NOT_AUTHORIZED`, `STORAGE_DELETE_UNCONFIRMED` | Writes fail closed; reads do not search fallback profiles; lifecycle task remains observable. |

Warnings require acknowledgement but do not bypass canonical validation.
Unknown schema fields or issue severities fail closed.

## 9. JSON examples

The examples are candidate envelopes after server normalization:

- `examples/practice-authoring-candidate-reading.json` covers Reading
  single-choice, multiple-answer and simple fill-blank;
- `examples/practice-authoring-candidate-listening.json` covers Listening
  true/false/not-given;
- `examples/practice-authoring-candidate-writing.json` covers exactly Q51-Q54,
  including structured Q51/Q52 blank authority; and
- `examples/practice-authoring-candidate-speaking.json` covers only
  `manual_text + text_only + none`.

`examples/practice-authoring-candidate-advanced-matching.json` proves that the
shared candidate remains broad enough for the existing Advanced Excel
`MATCHING` contract even though Quick v1 routes that type to Advanced.

These examples contain no provider response, learner submission, evaluation,
audio alignment or acoustic data. Canonical Java codecs and the full-draft
validator remain stricter second-layer authority beyond JSON Schema.

`examples/practice-pdf-authoring-output-reading.json` separately demonstrates
the strict pre-normalization PDF authoring envelope: source evidence plus
canonical question/answer authority, with no target, score, evaluation or
publication field.

## 10. AIM-1 acceptance and implementation stop rules

AIM-1 is complete when the ADR, roadmap, mappings, schemas/examples, migration
decision, task ownership and no-go boundaries agree and static validation is
recorded. It does not require Java/SQL/UI implementation.

AIM-2+ must stop for product approval if a requested change would:

- expand Quick v1 types/columns or make it multi-sheet;
- allow partial apply or automatic publication;
- add manual layout to v1;
- add/change a purpose or permit multiple/fallback providers;
- move a Practice client into shared/global runtime;
- merge the three storage profiles or expose Practice-private objects publicly;
- change learner-audio retention/authorization or enable evaluator transfer;
- reuse evaluation JSON or `snapshotJson` for authoring staging; or
- implement direct-audio/acoustic Speaking inside this epic.

Pure implementation details that preserve these contracts do not require a new
product decision.
