# Practice AI result completeness matrix — 2026-08-03

Status: `V1_DOMAIN_MODEL_IMPLEMENTED / DIRECT_AUDIO_ITEM_PARTIAL_GREEN / WRITING_RL_SCORE_AUTHORITY_ATOMIC`.

`practice-ai-result-completeness-v1` is the only domain completeness model. Its
closed states are `COMPLETE`, `PARTIAL_NON_SCORE`, and `UNAVAILABLE`; every
instance also carries a bounded reason code and `rejected_item_count`.
Operational queue/artifact lifecycle states such as pending, retry-wait, ready,
and failed are not competing result enums and do not authorize a score.

| Contract | Producer | Persisted envelope/cache | Current reader | Presenter / score consumer | Verdict |
| --- | --- | --- | --- | --- | --- |
| Writing | `WritingEvaluationNormalizer` adds `result_completeness` after the evidence ledger and complete task-native criterion set validate. Contract/provider failures produce `UNAVAILABLE`. | Per-question feedback JSON and Writing evaluation cache retain the same canonical normalized JSON. | `WritingFeedbackContractParser` requires the exact v1 object and rejects missing, future, malformed, or score/status-inconsistent metadata. | `WritingFeedbackViewMapper` removes every numeric/rubric field unless status is `COMPLETE`. `WritingEvaluationResult.scoreAvailableFlag()` and `WritingAssessmentPolicyBundle.hasExactCurrentScoreProvenance()` keep partial/unavailable out of attempt, latest/best and progress paths. | Existing complete score semantics are unchanged. A partial envelope can retain independent diagnostics but is always non-score-bearing. The current producer still treats its linked evidence/criterion graph atomically rather than guessing which provider findings are independent. |
| Reading/Listening explanation | `ReadingListeningExplanationClient` validates the exact strict provider response, then backend-injects `COMPLETE`; the provider is not asked to author backend state. | Current v4 `question_explanation_artifacts.explanation_json` contains the completeness object. Task/artifact lifecycle failure remains separately recorded by the existing operational state and error category. | `QuestionExplanationReadService` requires exact completeness for v4 and returns no current artifact for missing/malformed/non-complete state. v2/v3 readers remain explicitly versioned historical readers, never current v4. | Existing availability/presenter states remain honest: only a fully parsed current artifact counts ready; malformed/non-complete is unavailable, and a mix of complete and unavailable questions is labelled partial coverage without inventing a question score. | R/L has no learner score. Strategy claims, official-answer coverage, and evidence references remain atomic because removing one can change explanation correctness. No unsafe item salvage was introduced. |
| Speaking direct audio | `DirectAudioAcousticResponseNormalizer` keeps policy/calibration/receipt, pronunciation+fluency dimension set, totals and confidence atomic. Evidence spans are independent: invalid siblings are rejected item-by-item when at least one valid span remains per dimension. | V107 `observation_payload` embeds the v1 state and rejected count; no schema migration is required. The payload remains reviewer-only, retained and deletion-bound. | `DirectAudioDarkObservationService.inspect` re-parses exact metadata and hides legacy/malformed/unavailable payloads even after the named-grant query succeeds. | There is no standardized-score presenter. `PARTIAL_NON_SCORE` and `COMPLETE` are non-score-bearing observations; score-release eligibility, holistic score and attempt points remain forced false/null. | Partial diagnostic evidence is available without contributing to score, coverage, calibration readiness or standardized result UI. |

## Atomic and item-level boundaries

- Provider/model/purpose, schema/prompt/policy/calibration identity, source and
  receipt provenance are all-or-nothing.
- Writing rubric criteria and direct-audio pronunciation/fluency totals are
  accepted only as complete sets with consistent totals.
- Direct-audio evidence spans can be rejected individually because they do not
  authorize the provider signal or any released score. If a dimension loses all
  evidence, the entire result is unavailable with the original bounded reason.
- Writing and R/L provider findings are not salvaged yet: their current evidence
  graph participates in score or official-answer coverage, so treating arbitrary
  leaves as independent would be unsafe.
- Optional text never creates a score or changes completeness.

## Retry exhaustion and unavailable reasons

The shared transport boundary from commit `4a9853e0` already converts refusal,
truncation, malformed/non-object and empty output into bounded failures after at
most one complete replacement inside the shared request budget. Writing maps
those terminal failures to `UNAVAILABLE` normalized feedback. R/L records the
terminal task/artifact failure and returns unavailable rather than persisting a
fake explanation. Direct-audio captured parsing maps the same envelope failures
to unavailable/rejected dark results. No malformed response is patched or
merged.

## Persistence decision

No migration is needed. The authoritative stores are already JSON envelopes,
so completeness is versioned inside the same payload as its result. Missing or
malformed metadata is not silently upgraded by a reader. Applied migrations
V1–V102 remain the protected integration baseline; the Practice direct-audio
schema is forward-only in V103–V111.
