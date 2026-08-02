# 0012 Practice Authoring Import Modernization Boundaries

Date: 2026-08-02

## Status

Accepted for `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION` AIM-1. Implementation
starts at AIM-2; this ADR itself authorizes no runtime, schema or provider call.

## Context

At PR #55 merge commit `3dfab18a`, the canonical draft/editor/publisher stack
is usable but the import mutation boundary is inconsistent:

- Excel detects the advanced `practice-excel-v2` workbook before legacy v1,
  normalizes a draft-shaped JSON document and writes the linked
  `PracticeDraft` from the import service;
- PDF AI validates an import request, parses provider JSON, assembles a
  draft-shaped document and creates or merges a `PracticeDraft` directly;
- `PracticePdfImportSession.snapshotJson` stores selected pages, extraction
  strategy and region annotations for workspace restore, not reviewed
  authoring content;
- the canonical editor, preview, `practice-draft-v3` normalizer/validator and
  publisher already exist and must not be duplicated;
- Practice has domain-specific AI ports/transports, immutable contract
  identities, audit/lifecycle rules and provider-disabled behavior, while the
  global `AiClient` is an ordered text-chat fallback loop; and
- Practice lecturer assets, PDF workspace and learner Speaking media have
  different private keys, authorization, cleanup and retention behavior from
  general uploads.

The modernization epic needs a simple one-sheet authoring path and Admin
configuration without collapsing these domain boundaries.

## Decision

### 1. One persistent candidate data plane

Quick Excel and PDF AI both produce
`practice-authoring-candidate-v1`. The lifecycle is:

```text
source parse/extract/generate
  -> server normalize
  -> server validate
  -> persistent candidate
  -> grouped editable review
  -> exact learner projection
  -> explicit idempotent apply
  -> existing PracticeDraft/editor
  -> existing publisher
```

Neither an Excel parser nor an AI response can save, merge or publish a
`PracticeDraft`. Candidate apply is the only mutation seam. It locks the
candidate and target draft, checks candidate version/digest plus the draft's
base optimistic-lock version, simulates the full normalized draft, runs the
canonical validator and commits all selected candidate content atomically.

An already applied candidate returns its recorded result for the same apply
request. A changed target draft returns a conflict and writes nothing. Partial
apply is not supported in v1; reviewers remove/reject unwanted items before
marking the candidate ready.

`PracticePdfImportSession.snapshotJson` remains workspace state and must not be
read, copied or migrated as candidate JSON.

### 2. Quick Excel is a bounded convenience format

Quick Excel v1 is exactly one non-hidden sheet with the exact sentinel and
headers frozen in the architecture contract. Its target identity comes from
the authorized route:

```text
draftId + testNo + skill + lessonCode
```

The workbook cannot override that identity. Detection order is Quick v1,
advanced v2, legacy v1, unsupported. A malformed Quick sentinel fails as Quick
instead of falling through to an advanced parser.

Quick v1 supports only:

- `SINGLE_CHOICE`;
- `MULTIPLE_ANSWER`;
- `TRUE_FALSE_NOT_GIVEN`;
- one-token `FILL_BLANK`;
- Writing Q51-Q54 under the existing task/cardinality/point contracts; and
- Speaking `manual_text + text_only + none`.

Media, `MATCHING`, arbitrary multi-blank objective structures, Speaking audio
modes and other complex layouts stay in Advanced/editor. Current advanced
Excel v2 and legacy v1 remain format-compatible. Their existing readers are
adapted to the common candidate boundary before the old direct-draft mutation
seam is retired; parity evidence is mandatory and the canonical candidate may
therefore carry their broader canonical types and media references.

### 3. Admin AI is control plane; Practice remains data plane

Admin manages provider profiles and exactly these purpose bindings:

```text
PRACTICE_PDF_AUTHORING
PRACTICE_RL_EXPLANATION
PRACTICE_WRITING_EVALUATION
PRACTICE_SPEAKING_EVALUATION
PRACTICE_SPEAKING_STT
PRACTICE_SPEAKING_TTS
```

There is at most one active provider binding per purpose in v1. A logical job
resolves and snapshots one binding revision before execution. It never walks a
fallback list, changes provider/model mid-job or falls through to a global
provider. Missing, disabled, incompatible or changed authority fails closed.

Every provider call remains behind a Practice-owned purpose-specific client or
transport. Practice code does not import, inject or call
`com.ksh.features.ai.client.AiClient`. The current global `ai_providers`,
`AiClient`, prompts and wider-product consumers remain unchanged.

Admin capability tests are purpose-specific and execute the same Practice
request builder/decoder with project-owned bounded fixtures. A generic
connection ping cannot mark a purpose ready. Test runs record binding revision,
capability, status, duration and a bounded redacted error; no learner content,
prompt body, response body or secret is retained. AIM-1 performs zero tests
against a real provider.

`PRACTICE_SPEAKING_STT` may serve separate authoring-prompt and learner-response
operations, but each request retains its operation, data class, purpose and
retention identity. This does not authorize sending learner audio to the
Speaking evaluator.

### 4. PDF authoring is not evaluation

The Basic flow is:

```text
Text/PDF -> EXTRACT or GENERATE
  -> practice-pdf-authoring-output-v1
  -> server normalize/validate
  -> practice-authoring-candidate-v1
  -> review -> explicit apply
```

The authoring output schema contains source references, groups, questions,
canonical content/answer authority and warnings. Evaluation status, score,
rubric, diagnostic finding, learner feedback, upgraded answer, transcript
alignment and acoustic fields are forbidden. Provider output never chooses a
draft target or publication action.

The current crop/region/page workspace remains under Advanced. It may later
adapt a strictly validated authoring output into the same candidate lifecycle,
but its session snapshot is not staging and its old direct-draft assembler must
not remain an alternate apply path.

### 5. Admin storage profiles; Practice storage adapters

Admin manages exactly three stable storage profile codes:

```text
GENERAL_UPLOADS
PRACTICE_AUTHORING
PRACTICE_SPEAKING
```

`GENERAL_UPLOADS` belongs to the current avatar/exam/lesson/library family.
`PRACTICE_AUTHORING` owns PDF sources, crops and lecturer authoring assets.
`PRACTICE_SPEAKING` owns private learner response audio. Profiles may select a
local development backend or private R2, but no Practice write may fall back to
another profile.

Practice keeps separate `PracticeAuthoringStorage` and
`PracticeSpeakingAudioStorage` adapters and all domain validation, promotion,
authorization and cleanup logic. Sharing an S3 SDK or immutable configuration
record is not sharing the domain adapter. Practice private bytes never use a
public upload route or public object URL.

Active-profile writes fail closed when configuration or health is insufficient.
Reads use the exact persisted provider/profile identity. Legacy rows without a
profile remain readable only through a bounded legacy-local branch; there is
no search across all profiles. Physical migration is explicit, checksummed and
updates the logical row only after copy verification. No bulk migration is
required to adopt the profile schema.

Learner audio remains app-authorized and private. Temporary objects expire no
later than 24 hours; superseded objects enter the durable cleanup queue no
later than 24 hours; READY audio is retained with its immutable attempt until
the attempt's approved retention/deletion event; deleted bytes leave an
auditable tombstone. An age-only job must not delete READY audio while its
attempt is retained. Evaluator transfer and its provider retention are still
blocked by `P15-PRE-08` and `P15-PRE-01B`.

### 6. One editor and one learner renderer

Candidate review is a staging editor scoped to imported groups/questions. It
does not become a draft editor. After apply, all editing uses the existing
canonical editor and all publishing uses the existing publisher.

`View as learner` projects the candidate plus the authorized target draft in
memory, normalizes and validates that projection, then invokes the canonical
preview/player presenter and template. It must not maintain a second renderer
or save the projection. Layout v1 is `AUTO`; manual columns/panes/page-layout
authoring is not part of the epic.

### 7. Additive schema and compatibility decision

AIM-2/AIM-5/AIM-6 use reviewed forward migrations. AIM-1 does not select a
Flyway number because the merged tree must be rescanned immediately before SQL
is added. Existing migrations and checksums are immutable.

The target relational additions are:

- `practice_authoring_candidates` and
  `practice_authoring_candidate_apply_events`;
- `practice_ai_provider_profiles`, `practice_ai_purpose_bindings` and
  `practice_ai_capability_test_runs`;
- `storage_profiles`; and
- nullable exact `storage_profile_code` identity on Practice logical rows that
  can move from legacy local storage.

The existing global `ai_providers`, `ai_request_logs`, global storage settings,
PracticeDraft tables, PDF session snapshot and published graph are not renamed,
reused or dropped. Compatibility is additive and bounded. Rollback disables
new entry points/bindings, leaves unapplied candidates inert, restores the old
runtime readers and retains new tables/columns for audit; it does not reverse a
published migration or delete bytes. Applied candidate mutations remain normal
draft edit history and are not rolled back by dropping staging rows.

## Rejected alternatives

1. **Use `PracticeDraft` as staging** — rejected because source retries and
   review mutate the canonical editor document before explicit acceptance.
2. **Reuse PDF `snapshotJson`** — rejected because it has workspace restore
   semantics, no common Excel identity and no candidate/apply lifecycle.
3. **Call shared `AiClient` with a purpose string** — rejected because its
   ordered fallback and text-chat dialect cannot preserve exact Practice
   purpose, modality, contract or provider identity.
4. **One global storage adapter/profile** — rejected because authoring assets,
   learner audio and general uploads have different privacy and lifecycle
   contracts.
5. **Create a second full editor for imports** — rejected because it would fork
   validation, preview and publisher semantics.
6. **Quick Excel supports every canonical type** — rejected because media,
   matching and arbitrary blank graphs require Advanced/editor review.

## Consequences

Positive:

- all machine-assisted input has one reviewable, replay-safe boundary;
- Quick Excel remains genuinely small without breaking advanced files;
- Admin gains operational control without acquiring Practice request/data
  ownership; and
- storage/provider migration is additive, fail-closed and rollback-safe.

Trade-offs:

- candidate persistence and apply coordination add schema and lifecycle work;
- v1 supports no partial apply or manual import layout;
- capability tests are six purpose-specific flows rather than one cheap ping;
  and
- legacy local bytes may require a bounded dual-read period before explicit
  copy/verification retires it.

## Follow-up

Implement AIM-2 through AIM-8 and pass the consolidated epic gate in the
roadmap amendment. Direct-audio/acoustic Speaking remains a separate Pre-15
decision and implementation program.
