# Practice Writing Vertex Provider Contract Hotfix — Live Change Log

Date: 2026-07-29

Branch: `codex/writing-vertex-provider-contract-hotfix`

Baseline: `bd4a127e5fe10d1ea238bb7621f483392a0ba8f7`

## Scope and authority

This is a narrow provider-contract hotfix for Writing Q53. It does not change
Writing scoring, rubric weights, task retirement, Korean assessment criteria,
Result/Detail UX, Practice AI/storage ownership, Pre-14 scope, or the standalone
public Practice catalogue established by commit
`da166181ec9b485a17a206b73132dafe5336db4b`.

A comprehensive Practice audit is running separately. This branch must be
reviewed and reconciled before integration and must not be merged
automatically.

## Security boundary

- A previously pasted OAuth access token is treated as compromised and
  expiring. It is not used, copied, searched, logged, stored, tested, or
  committed.
- Local configuration references `VERTEX_ACCESS_TOKEN` only through an
  environment placeholder.
- No real provider smoke is authorized. Validation uses fixtures and mocks
  with provider-call count zero.
- Logs may contain only provider/model/task type, normalized finish reason,
  content length, a sanitized request ID, and exception category. They never
  contain provider content, learner answers, credentials, or authorization
  headers.

## Discovery and root-cause matrix

Confirmed:

- `WritingEvaluationClient` parsed only clean JSON text from
  `choices[0].message.content`.
- Chat content-parts arrays produced an empty string through `asText()`.
- Markdown fences, provider prefaces, and suffixes became
  `PROVIDER_MALFORMED_JSON`.
- `finish_reason` and Responses-style incomplete metadata were ignored, so
  output exhaustion was indistinguishable from malformed JSON.
- `max_tokens=4096` and `response_format=json_schema` were hard-coded.
  Reasoning capability was not configurable.
- The durable worker already bounds automatic retries at three attempts.
  Retryable Writing contract failures enter `RETRY_WAIT` and become terminal
  `FAILED` at exhaustion; the Result page reflects the terminal state after a
  reload and never fabricates a score.
- IntelliJ's checked-in Java 17 run configuration loads the classpath
  `application-local.properties` through `spring.config.import`. The real local
  file is ignored; the tracked example is the safe configuration surface.

Unproven hypotheses:

- The observed Vertex Q53 response may be fenced, prefaced, returned as content
  parts, or truncated because reasoning consumed the output budget.
- Authentication reaching the endpoint proves neither durable OAuth
  credentials nor a valid response contract.

No raw provider payload or live request is used to convert those hypotheses
into claims.

## Accepted implementation decisions

### Deterministic decoder

Added `WritingProviderResponseDecoder`:

1. Parse one complete trimmed JSON object first.
2. Accept one complete object from a single JSON/code fence.
3. Otherwise extract exactly one balanced object from provider preface/suffix.
4. Balance tracking is string/escape aware and handles nested arrays/objects.
5. Reject multiple objects, unbalanced/partial JSON, unknown content shapes,
   empty content, and non-object top-level evidence.
6. Support proven OpenAI-compatible string, chat content-parts, `output_text`,
   and Responses content-parts envelopes.
7. Inspect `finish_reason` and Responses incomplete metadata before payload
   normalization; output exhaustion maps to
   `PROVIDER_OUTPUT_TRUNCATED`.

`WritingEvaluationNormalizer` remains the strict schema/scoring authority and
is unchanged.

### Request and local configuration

`OpenAiProperties` now owns Writing-evaluator-only controls:

- maximum output tokens, default unchanged at `4096`;
- structured-output capability, default `true` to preserve the current
  request;
- reasoning-effort capability, default disabled;
- reasoning effort (`low`, `medium`, or `high`) only when explicitly enabled.

Non-default request capabilities participate in the cache contract identity.
The prior cache schema identity remains byte-for-byte stable under the current
default request contract.

The local example uses the requested Vertex endpoint/model/timeouts and an
environment-only credential placeholder. It documents that an OAuth access
token is short-lived and that production refresh/ADC is separate architecture
work.

### Lifecycle and diagnostics

Missing credential handling remains provider-call-free and non-retryable, now
with a safe preflight diagnostic. The client no longer constructs a blank
authorization header.

Malformed and truncated results remain scoreless. Truncation is retryable
through the existing bounded worker contract; retry exhaustion is explicitly
covered by a transaction test asserting terminal `FAILED`, no next retry, and
no fabricated score or feedback.

## Affected boundary

Production:

- `OpenAiProperties`
- `WritingEvaluationClient`
- `WritingProviderResponseDecoder`
- `application.properties`
- `application-local.properties.example`

Focused tests:

- `OpenAiPropertiesTest`
- `WritingEvaluationClientTest`
- `WritingProviderResponseDecoderTest`
- `PracticeAttemptEvaluationJobTransactionsTest`

Documentation:

- this live log
- the bounded hotfix overlay in `CODEX_PRACTICE_WORKFLOW.md`

## Validation plan

Initial state: `IMPLEMENTED_PENDING_CONSOLIDATED_VALIDATION`

Before validation, this log must be reread and reconciled against the complete
diff. The single Java 17 validation unit will run:

1. `git diff --check` and non-printing secret-pattern checks;
2. one skip-tests package/compile;
3. the smallest provider/decoder/worker/config/cache selectors;
4. a provider-disabled zero-call smoke through focused tests.

No persistence schema changed, so no disposable database lifecycle is planned.
If the focused tests show that shared `OpenAiProperties` breadth requires it,
the selector may include its direct Reading/Listening/PDF consumers; a full
suite is not otherwise justified.

## Pre-validation static reconciliation

The complete live log, production diff, focused tests, current request shape,
cache identity, worker transitions, Result state mapping, and standalone
catalogue guard were reread together before validation.

Reconciliation result:

- every required decoder acceptance/rejection case has a focused fixture;
- complete schema-invalid JSON still reaches the unchanged strict normalizer;
- request capabilities are absent unless explicitly enabled, while the
  existing structured-output/default-token request remains unchanged;
- default cache identity is unchanged and non-default capabilities invalidate
  reuse;
- missing credential and provider-disabled paths make zero provider calls;
- retry exhaustion persists terminal `FAILED` with a null score and no
  feedback fabrication;
- no class-scoped catalogue file or behavior is changed;
- no persistence, migration, scoring, rubric, Result/Detail, Speaking, STT,
  TTS, or Pre-14 production boundary is changed.

No open implementation item remains. The slice is
`READY_FOR_CONSOLIDATED_VALIDATION`.

## Validation attempt and grouped correction

The first validation command stopped before Maven because the untracked-file
whitespace check found two Markdown hard-break trailing spaces in this log.
No compile or test ran. The complete whitespace diagnostic found no other
issue. One grouped correction replaced those hard breaks with blank lines.

The documented Homebrew JDK 17 and `bash mvnw` invocation were then selected
after generic macOS Java lookup resolved Java 26 and the non-executable wrapper
blocked Maven. The first substantive Java 17 package run compiled all 788
production sources, then test compilation reported exactly two identical
AssertJ overload ambiguities in the new reflection-based cache identity test.
No test method ran. One grouped test-only correction assigns the reflected
values to typed `String` variables before assertion. The unchanged full ordered
gate must now rerun once.

## Consolidated validation result

`COMPLETE_FOCUSED_GATE_GREEN`

The final unchanged gate ran with Homebrew OpenJDK `17.0.19`:

- tracked and untracked `git diff --check`: pass;
- non-printing changed-file credential checks: pass;
- Maven package with tests skipped: `BUILD SUCCESS`;
- compiled baseline: 788 production sources and 345 test sources;
- focused selector: `106/106`, zero failures, zero errors, zero skips;
- provider-disabled missing-credential smoke: pass with no `RestClient`
  interaction and no cache write;
- external calls: Writing AI `0`, STT `0`, TTS `0`;
- database/migration changes or lifecycle: none.

The focused selector covered `OpenAiPropertiesTest`,
`WritingProviderResponseDecoderTest`, `WritingEvaluationClientTest`,
`WritingEvaluationNormalizerTest`, `WritingEvaluationCacheServiceTest`,
`PracticeAttemptEvaluationJobTransactionsTest`,
`PracticeAttemptEvaluationProcessorTest`, and
`PracticeAttemptEvaluationJobTest`.

No full suite, browser/device journey, application startup, live provider
smoke, OAuth durability, production ADC/refresh, or comprehensive-audit
integration is claimed.

The hotfix is ready for commit/push/draft-PR publication, then must stop for
coordinated integration.

## Re-fetch and comprehensive-audit overlap

The required pre-PR fetch confirmed `origin/main` is unchanged at the baseline
`bd4a127e5fe10d1ea238bb7621f483392a0ba8f7`.

The active comprehensive-audit worktree is also based on that SHA and has
uncommitted overlap in exactly five hotfix paths:

- `src/main/java/com/ksh/features/practice/ai/OpenAiProperties.java`
- `src/main/java/com/ksh/features/practice/ai/writing/WritingEvaluationClient.java`
- `src/main/resources/application.properties`
- `src/test/java/com/ksh/features/practice/ai/OpenAiPropertiesTest.java`
- `src/test/java/com/ksh/features/practice/ai/writing/WritingEvaluationClientTest.java`

The audit additionally changes Writing policy/schema/version identities and
removes the historical mock-evaluator seam. Therefore this hotfix must not be
blindly merged or cherry-picked over the audit.

Safe integration order:

1. let the comprehensive audit reach a clean, reviewable commit;
2. apply the hotfix commits onto that audited head with manual reconciliation
   of the five paths above;
3. preserve the audit's policy bundle, schema versions, constructor cleanup,
   and mock removal;
4. preserve the hotfix decoder, truncation reason, evaluator-only capability
   controls, environment-only local example, safe logging, and terminal
   exhaustion proof;
5. port the hotfix client tests to the audit's constructor/version contract;
6. run the combined Writing/config/cache/worker selectors before integrating
   only the reconciled branch.

The new decoder, local example, worker transaction test, and this log have no
direct file overlap with the current audit worktree.
