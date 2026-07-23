# Practice AI Artifact Retention and Inspection Runbook

## Scope

This runbook covers operational inspection for:

- `practice_writing_evaluation_cache`;
- Reading/Listening-only `question_explanation_artifacts`;
- `question_version_explanation_bindings`;
- `question_explanation_generation_tasks`.

The default and only unapproved mode is **READ-ONLY**. Do not copy cleanup SQL
from an older revision of this runbook. The former
`question_explanation_cache` table was backfilled and dropped by current
Flyway V37, not V28. The `2026-07-24` audit found that V37 backfill unsafe as
legacy-upgrade evidence: it ignores V34's `question_version_id`, assumes
question ID/fingerprint/language equivalence and then drops the old table.
Table presence does not prove that historical bindings were migrated safely.

Despite the historical filenames, V26 and V39 are semantic duplicates of the
exam rich-text widening; misleadingly named V27 creates
`practice_writing_evaluation_cache`. The Writing cache is independent from the
R/L `question_explanation_*` lifecycle.

An explanation artifact is published assessment evidence, not a learner cache:

- the artifact is keyed by a content fingerprint that does not contain database
  IDs or a learner answer;
- one artifact may be bound to multiple immutable question versions;
- historical attempts read the binding for their locked question version;
- result GET requests only read READY artifacts and never invoke the provider;
- generation and retry state lives in a durable task row.

Deleting an artifact by age can therefore break both current and historical
results. There is no automatic Reading/Listening artifact TTL.

## Safe Pre-Checks

Before inspection, record:

- target environment and database host;
- deployed application commit;
- database version;
- latest successful Flyway version;
- current prompt, response-schema, assessment-schema, language and model
  constants from deployed code;
- whether the connection is read-only.

```sql
SELECT VERSION();

SELECT version, description
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;

SHOW CREATE TABLE practice_writing_evaluation_cache;
SHOW CREATE TABLE question_explanation_artifacts;
SHOW CREATE TABLE question_version_explanation_bindings;
SHOW CREATE TABLE question_explanation_generation_tasks;

SHOW INDEX FROM question_explanation_artifacts;
SHOW INDEX FROM question_version_explanation_bindings;
SHOW INDEX FROM question_explanation_generation_tasks;
```

Do not run Flyway, a worker, a provider request or DML as part of inspection.

## Guarded Rebaseline Safety

`REBASELINE_GO_WITH_GUARDS` is a future pre-14 plan, not an instruction to
mutate the inspected database. It may execute only after consolidated Phase
13E, after final pre-14 schema contracts are frozen, and only with written
proof that no retained/deployed/shared/canonical/upgrade obligation exists.
Any positive or unknown obligation is a hard stop.

Never use Flyway repair, never clean/reuse an old schema and keep it read-only
as evidence. A later authorized run creates a newly named disposable DB with
validate-on-migrate enabled and clean disabled by default; clean is available
only through an explicit disposable-profile allowlist. Preserve non-Practice
V38-V43 bytes/checksums, pull before choosing the next free version, and treat
`V44__practice_baseline.sql` as proposed only if V44 is then free.

The final Practice baseline is schema-only and contains no legacy backfill or
content seed. Before 14A it receives only minimal technical R/L/W/S smoke
fixtures. Canonical Vietnamese/Korean SME-reviewed UAT content is loaded only
after 14F. No local schema is a “master” database.

## Privacy Rules

Routine inspection must not output:

- `input_contract_json`, `explanation_json` or writing `result_json`;
- a raw fingerprint, hash or cache key;
- prompt text, transcript, learner answer or AI feedback;
- raw user ID, email, name, credential or connection string;
- `last_error_message`, which may contain provider detail.

Allowed routine output is limited to aggregate counts, bounded status/version
labels, timestamp ranges, payload byte sizes and bounded error categories.
Never use `SELECT *` in routine inspection.

## Writing Cache Inspection

The Writing evaluator cache is an optimization and retains its configured TTL.
It is independent from immutable Reading/Listening explanation artifacts.

```sql
SELECT
  COUNT(*) AS total_rows,
  COALESCE(SUM(expires_at <= UTC_TIMESTAMP()), 0) AS expired_rows,
  COALESCE(SUM(expires_at > UTC_TIMESTAMP()), 0) AS unexpired_rows,
  MIN(created_at) AS oldest_created_at,
  MAX(updated_at) AS newest_updated_at,
  MIN(expires_at) AS oldest_expires_at,
  MAX(expires_at) AS newest_expires_at
FROM practice_writing_evaluation_cache;
```

```sql
SELECT
  prompt_version,
  rubric_version,
  evaluation_schema_version,
  task_type,
  COUNT(*) AS rows_count
FROM practice_writing_evaluation_cache
GROUP BY prompt_version, rubric_version, evaluation_schema_version, task_type
ORDER BY rows_count DESC;
```

```sql
SELECT
  COALESCE(SUM(result_json IS NULL OR TRIM(result_json) = ''), 0)
    AS blank_payload_rows,
  AVG(OCTET_LENGTH(result_json)) AS avg_payload_bytes,
  MAX(OCTET_LENGTH(result_json)) AS max_payload_bytes
FROM practice_writing_evaluation_cache;
```

An expired-row cleanup must use the approved application maintenance path or a
separately reviewed change ticket. This runbook intentionally provides no DML.

## Explanation Artifact Inspection

### Status and age

```sql
SELECT
  status,
  COUNT(*) AS artifact_count,
  MIN(created_at) AS oldest_created_at,
  MAX(updated_at) AS newest_updated_at
FROM question_explanation_artifacts
GROUP BY status
ORDER BY status;
```

### Bounded generation contract

Compare these values with constants from the deployed application. A different
tuple is not automatically obsolete because historical versions may still bind
to it.

```sql
SELECT
  skill,
  question_type,
  assessment_schema_version,
  provider_model,
  prompt_version,
  response_schema_version,
  explanation_language,
  status,
  COUNT(*) AS artifact_count
FROM question_explanation_artifacts
GROUP BY
  skill,
  question_type,
  assessment_schema_version,
  provider_model,
  prompt_version,
  response_schema_version,
  explanation_language,
  status
ORDER BY artifact_count DESC;
```

### Payload shape and size

This query does not print explanation content.

```sql
SELECT
  COALESCE(SUM(status = 'READY' AND explanation_json IS NULL), 0)
    AS ready_without_payload,
  COALESCE(SUM(status <> 'READY' AND explanation_json IS NOT NULL), 0)
    AS non_ready_with_payload,
  AVG(CASE WHEN explanation_json IS NOT NULL
      THEN OCTET_LENGTH(explanation_json) END) AS avg_payload_bytes,
  MAX(OCTET_LENGTH(explanation_json)) AS max_payload_bytes
FROM question_explanation_artifacts;
```

### Content reuse

High binding counts are expected when unchanged questions are republished.

```sql
SELECT
  binding_count,
  COUNT(*) AS artifact_count
FROM (
  SELECT
    qea.id,
    COUNT(qveb.id) AS binding_count
  FROM question_explanation_artifacts qea
  LEFT JOIN question_version_explanation_bindings qveb
    ON qveb.artifact_id = qea.id
  GROUP BY qea.id
) reuse
GROUP BY binding_count
ORDER BY binding_count;
```

### Binding integrity

Both results should be zero. Foreign keys protect row existence; the fingerprint
check protects the application-level content identity contract.

```sql
SELECT COUNT(*) AS fingerprint_mismatch_count
FROM question_version_explanation_bindings qveb
JOIN question_explanation_artifacts qea
  ON qea.id = qveb.artifact_id
WHERE qveb.fingerprint <> qea.fingerprint;
```

```sql
SELECT
  question_version_id,
  explanation_language,
  COUNT(*) AS duplicate_count
FROM question_version_explanation_bindings
GROUP BY question_version_id, explanation_language
HAVING COUNT(*) > 1;
```

The duplicate query above matches the current one-row binding schema. The
pre-14 target replaces it with append-only active/superseded binding history:
exactly one compatible row may be active while prior rows remain auditable.
Update this inspection query with the deployed status-column contract when
that schema is implemented; do not guess a future column name in advance.

Do not print raw fingerprints while investigating a mismatch. Use row IDs in a
restricted incident session.

## Generation Task Inspection

### Status and attempts

```sql
SELECT
  status,
  COUNT(*) AS task_count,
  MIN(attempt_count) AS min_attempt_count,
  MAX(attempt_count) AS max_attempt_count,
  MIN(created_at) AS oldest_created_at,
  MAX(updated_at) AS newest_updated_at
FROM question_explanation_generation_tasks
GROUP BY status
ORDER BY status;
```

### Due retries and expired leases

```sql
SELECT
  COALESCE(SUM(status IN ('PENDING', 'RETRY_WAIT')
      AND (next_attempt_at IS NULL OR next_attempt_at <= UTC_TIMESTAMP())), 0)
    AS due_tasks,
  COALESCE(SUM(status = 'PROCESSING'
      AND lease_expires_at <= UTC_TIMESTAMP()), 0)
    AS expired_leases,
  COALESCE(SUM(status = 'FAILED'), 0) AS terminal_failures
FROM question_explanation_generation_tasks;
```

### Bounded failure categories

```sql
SELECT
  error_category,
  status,
  COUNT(*) AS task_count
FROM question_explanation_generation_tasks
WHERE status IN ('RETRY_WAIT', 'FAILED')
GROUP BY error_category, status
ORDER BY task_count DESC;
```

### Task/artifact coherence

The following query reports states that require investigation. A migrated READY
artifact may legitimately have no task, so absence of a task is not itself an
error.

```sql
SELECT
  qegt.status AS task_status,
  qea.status AS artifact_status,
  COUNT(*) AS pair_count
FROM question_explanation_generation_tasks qegt
JOIN question_explanation_artifacts qea
  ON qea.id = qegt.artifact_id
GROUP BY qegt.status, qea.status
ORDER BY qegt.status, qea.status;
```

Expected terminal pairs are `SUCCEEDED/READY` and `FAILED/FAILED`. Active tasks
normally pair with a `PENDING` artifact. Capture counts before taking action.

## Operational Response

Use these rules in order:

1. `PENDING`: allow the worker to claim the task.
2. `PROCESSING` with a live lease: do not interfere.
3. `PROCESSING` with an expired lease: verify worker health; the durable claim
   path is designed to reclaim it.
4. `RETRY_WAIT`: verify provider health and wait for `next_attempt_at`.
5. `FAILED`: inspect the bounded category and published evidence before retry.
6. `READY`: no generation action is required.

Authorized manual retry uses:

```text
POST /api/practice/manage/explanations/{artifactId}/retry
```

The endpoint requires publish permission on a bound set, accepts only a terminal
failed task, is idempotent for active/ready states and enforces a cooldown. Do
not emulate it with direct `UPDATE` statements.

If immutable evidence is malformed, correct the draft and republish. Do not
rewrite a READY artifact in place, because historical attempts may share it.

## Retention Policy

- Writing cache rows may be removed after expiry only through an approved
  maintenance path.
- Explanation artifacts, bindings and tasks have no age-based TTL.
- A binding must remain while its immutable question version can be reached by
  a current publication or historical attempt.
- Shared artifacts must not be deleted while any binding exists.
- Task rows are lifecycle and audit evidence; do not delete them independently.
- Prompt/model/schema changes create new fingerprints. They do not authorize
  deletion of historical artifacts.
- Any future artifact purge requires a separate design covering old-attempt
  isolation, legal retention, backups, foreign keys and rollback.

## Incident Checklist

Record only aggregate evidence:

- application commit and Flyway version;
- worker instance count and scheduling state;
- artifact/task counts by status;
- due-task and expired-lease counts;
- bounded error categories;
- whether provider health is degraded;
- whether one current publication or many historical versions are affected;
- retry endpoint response status and `Retry-After`, when used.

Never fix a learner result by rebinding it to the latest question version. The
attempt must continue to read the immutable version and explanation binding it
was created against.
