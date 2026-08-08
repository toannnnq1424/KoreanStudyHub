# Practice AI contract robustness and Korean alignment audit — 2026-08-03

Status: `EVIDENCE_BASELINE / ALIGNMENT_CONTRACT_IMPLEMENTED_DARK_ONLY / ENVELOPE_REPLACEMENT_BOUNDED / DOMAIN_SCHEMA_RETRY_OPEN`.

This audit describes the code through local branch commit `1846bd0f` plus the
bounded transport-replacement boundary under validation. It does not claim a real
provider run, Korean acoustic calibration, learner score release or production
alignment capability.

## Structured-output request and validation matrix

| Skill / purpose | Request schema actually sent | Provider support evidence enforced | Response parser and downstream validation | Retry / repair today | Exact verdict |
| --- | --- | --- | --- | --- | --- |
| Writing / `PRACTICE_WRITING_EVALUATION` | `WritingEvaluationClient.unifiedSchema()` is passed through `PracticeStructuredGenerationRequest`; `PracticeControlPlaneStructuredGenerationAdapter.wireBody` sends `response_format={type:json_schema,json_schema:{name,strict:true,schema}}`. Object schemas are closed and required fields cover the evidence ledger, complete task-native rubric set, coverage, findings and upgrade. | Resolver snapshot must declare `strictJsonSchema`; request capability must be native JSON schema with no plain-JSON/tool/streaming fallback. The Admin capability test sends a small real strict-schema probe when deliberately invoked. A stored capability flag or passing small probe is not proof every large Writing schema is supported. | `StrictOpenAiStructuredResponseDecoder` atomically checks one choice, `finish_reason=stop`, no refusal, strict UTF-8/single JSON/NFC. `WritingEvidenceLedgerVerifier` then validates source identity, evidence and the entire criterion set. `WritingEvaluationNormalizer` derives all scores backend-side; any verifier exception returns non-score `EVALUATION_CONTRACT_FAILED`. | HTTP `429/500/502/503/504` and at most one HTTP-200 refusal/truncation/malformed-output full replacement share the binding hard budget `1 + maxRetries` (`1..4`). Replacement uses a derived idempotency key and separate audit; the malformed response is never sent back or merged. Downstream domain schema mismatch still has no replacement coordinator. The stale Writing `max-retries=5` identity was replaced by the purpose-binding authority identity. | `practice-ai-result-completeness-v1` is persisted and required by parser/presenter. `PARTIAL_NON_SCORE` is labelled honestly and numeric/rubric fields are hidden, but the current producer keeps its linked finding/evidence graph atomic. |
| Reading/Listening explanation / `PRACTICE_RL_EXPLANATION` | `ReadingListeningExplanationClient.schema(context, images)` selects a closed strict schema per canonical question type and exact strategy discriminator; the same control-plane adapter sends it as strict `json_schema`. | Same resolver/capability gate and optional deliberate capability probe as Writing. Image input additionally requires `imageInput`; no provider-name inference. | Strict envelope decoder runs first. `cleanAndValidateJson` then checks exact schema/strategy/question identity, evidence offsets/digests, translations and type-specific coverage. Any bad leaf returns `null`; the generation processor records task failure. Explanation has no learner score. | Same shared-budget HTTP retry plus one possible envelope-level full replacement. Worker/task lifecycle can later reclaim or manually retry categories according to task policy. Domain evidence/coverage mismatch still has no immediate replacement and no item-level salvage. | Backend-owned completeness v1 is injected only after full validation and required by the v4 reader. Claims/evidence remain atomic because removing one may change official-answer correctness. |
| Speaking transcript evaluation / `PRACTICE_SPEAKING_EVALUATION` | `SpeakingEvaluationPromptBuilder.responseFormat(request)` builds the closed required v4 schema. `OpenAiCompatibleSpeakingEvaluationClient` extracts that exact schema and sends it through the shared strict adapter. | Same strict capability/resolver gate and deliberate small probe. Current contract is explicitly transcript-grounded and rejects direct-audio capability. | Strict envelope decoder precedes `SpeakingEvaluationNormalizer`. Evaluation/provenance and complete rubric set are atomic; transcript annotations/evidence are parsed as collections, but contradictions or missing criterion rows fail the evaluation. Low-transcript-confidence becomes non-score-bearing. | Same shared-budget HTTP retry plus one possible envelope-level full replacement; no recursive repair. Domain rubric/evidence contradiction is not retried. | Current transcript score identity is atomic. Diagnostic parsing has some bounded filtering, but does not expose a unified `COMPLETE/PARTIAL_NON_SCORE/UNAVAILABLE` contract. |
| Speaking direct-audio acoustic observation / `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` | `ksh-speaking-direct-audio-acoustic-v1` exists as a closed schema and strict parser, but no production provider-response transport is wired. | Exact direct-audio registry evidence, provider policy, consent, reviewer, calibration and dark rollout are required. The production calibration authority resolves nothing. | Critical identity, consumption receipt, policy/calibration and the two-dimension total are atomic. Malformed input is `REJECTED_NON_SCORE_BEARING`; no learner surface consumes it. | None; fake captured response only. A retry cannot be enabled until a real response transport exists. | Correctly dark and non-score-bearing. |
| Korean alignment / future dedicated component | `ksh-speaking-korean-alignment-v1` is a separate closed schema. It requires exact authorized audio/transcript identities and a dedicated forced-aligner or ASR word-timestamp component; `LLM_ONLY` is impossible. | Only `TEST-*` fixtures exist. No Gemini or selected model is claimed to provide phoneme timestamps. Production needs immutable Korean timestamp-capability evidence and calibration IDs. | Critical envelope/provenance/authorized-audio identity is atomic. Each eojjeol/syllable/jamo/phoneme span is validated independently; invalid items are dropped to `PARTIAL_NON_SCORE`, while an invalid critical envelope is `UNAVAILABLE`. | None; no production component call exists. | Bounded contract is green for fake data only; always dark, no score and no learner visibility. |

## Required validation tiers

1. **Atomic critical envelope/provenance/score identity.** Provider/model,
   schema/prompt/rubric, source hash, consent/policy/calibration/receipt and
   score-release identity fail as one unit. No default or inferred value may
   make them complete.
2. **Atomic criterion set.** A score-bearing rubric is accepted only when every
   required criterion, maximum/weight identity and evidence reconciliation is
   complete. A second request may replace the full response; it must never patch
   a missing score field into the first response.
3. **Independent diagnostics.** Findings, evidence claims and alignment spans
   should validate item-by-item where one invalid item cannot change the score
   or authority of siblings. A surviving set is explicitly
   `PARTIAL_NON_SCORE`; it cannot silently remain complete.
4. **Presentation copy.** Summary, labels and suggested copy may be unavailable
   or partial. They never create evidence, a score or a retry-success claim.

Writing and R/L satisfy tiers 1/2 and carry the shared completeness model, while
their linked finding/claim evidence remains atomic. Direct-audio acoustic and
alignment contracts implement atomic critical identity plus item-level partial
diagnostics without score authority.

## Bounded replacement retry boundary

The shared transport now permits at most one additional structured call after
`PROVIDER_REFUSAL`, `PROVIDER_TRUNCATED_RESPONSE`, malformed/non-object output
or empty content, only when capacity remains in the same binding request budget.
The replacement gets a derived idempotency identity and a trusted backend
instruction to return a complete replacement. It does not send the rejected
payload back, recursively repair it, merge fields or exceed `1 + maxRetries`.
HTTP retry and full replacement use distinct reasons; the replacement owns a
separate execution-audit row.

Downstream domain schema mismatch cannot currently trigger replacement safely because the
shared port returns before domain validation. That needs either a validator
callback/result contract at the port boundary or a domain-owned bounded
replacement coordinator; silently catching the mismatch inside the normalizer
is insufficient.

## Korean alignment and playback target

`ksh-speaking-korean-alignment-v1` models the hierarchy
`EOJJEOL -> SYLLABLE -> JAMO/PHONEME` only when evidence supports each level.
It does not assume English IPA. Each span binds transcript token identity,
UTF-16 offsets, authorized-audio timestamps, expected/observed pronunciation,
an acoustic issue code, confidence and evidence source. Grammar/lexical
analysis stays in the transcript-language contract; acoustic issue codes do not
contain grammar or lexical errors. The same `token_id` may link the two views
without converting language analysis into acoustic evidence.

The future UI may render clickable highlighted transcript ranges, issue cards,
and Strengths/Needs-improvement tabs. “Nghe đoạn của bạn” must seek/range the
original authorized audio. The current owner playback route already enforces
owner scope, no-store headers and HTTP byte ranges, and exposes no raw storage
key; it does not yet provide reviewer playback or a time-to-byte mapping API.
No per-word audio objects should be created. Learner alignment and playback
remain disconnected until direct-audio score/readiness release is explicitly
green; reviewer access must reuse the exact active named grant and withdrawal/
deletion state.

## Exact external blockers

- select and verify a dedicated Korean forced-aligner or ASR component with
  eojjeol/word timestamps; syllable/jamo/phoneme output requires separate
  captured evidence and cannot be inferred from Gemini marketing or an LLM;
- capture Korean responses across the approved device/environment/voice corpus
  and issue immutable capability/calibration/fairness/repeatability IDs;
- define the reviewed mapping between milliseconds and authorized ranged
  playback without raw URL/key leakage, plus reviewer endpoint auth/CSRF tests;
- implement withdrawal/deletion propagation and the V107 due-row deletion worker;
- approve learner exposure and any scoring thresholds separately; and
- add a domain-owned replacement coordinator or validator callback for
  post-envelope schema mismatches, and introduce explicit tiered status for
  Writing/R/L diagnostics before calling their robustness closure complete.
