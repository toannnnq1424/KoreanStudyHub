# Practice AI Cache Retention and Inspection Runbook

## Scope

This runbook covers operational inspection and future approved retention work for:

- `practice_writing_evaluation_cache`
- `question_explanation_cache`

Default mode is **READ-ONLY**.

Do not run cleanup from this document unless production aggregate results have been reviewed and a maintenance approval explicitly authorizes DML. Cache data is an optimization, not the source of truth for learner attempts or results. Cleanup failure must not affect learner attempt state, scoring, or result rendering.

Last verified application assumptions:

- Writing cache TTL is 30 minutes.
- Writing cache rows use `expires_at DATETIME`; `idx_pwec_expires_at` exists.
- Writing payload column is expected as `result_json LONGTEXT` unless real DDL proves otherwise.
- Reading/Listening explanation cache has no TTL.
- Reading/Listening invalidation uses a versioned cache key.
- Reading/Listening payload column is expected as `explanation_json LONGTEXT` unless real DDL proves otherwise.
- Reading/Listening cache has no FK/cascade to `practice_questions`.
- Local/test database data is not representative of production.

Last local audit sample, for context only:

- Database version observed locally: MySQL 9.2.0.
- Writing cache local rows: 0.
- Reading/Listening cache local rows: 107.
- Local Reading/Listening rows were all test/local version rows, obsolete versus current constants, and orphaned.

Do not use the local sample above to set production thresholds or retention policy. Production decisions require production read-only aggregate results.

## Safe Session Pre-Checks

Before inspection, the operator must verify:

- The target environment is correct.
- The connection used for inspection is read-only.
- The database/server version is recorded.
- The deployed application version or commit is recorded.
- The current successful Flyway version is recorded.
- The current cache version constants are read from deployed source/config.
- Maintenance approval exists before any DML is considered.

Read-only pre-checks:

```sql
SELECT VERSION();

SELECT version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;

SHOW CREATE TABLE practice_writing_evaluation_cache;

SHOW CREATE TABLE question_explanation_cache;

SHOW INDEX FROM practice_writing_evaluation_cache;

SHOW INDEX FROM question_explanation_cache;
```

Do not run Flyway or any migration as part of this runbook.

The SQL templates in this document assume MySQL-compatible syntax for temporary tables, `INSERT ... SELECT ... LIMIT`, multi-table `DELETE ... JOIN`, `UTC_TIMESTAMP()`, `DATE_SUB`, and null-safe equality (`<=>`). Verify the production database version before using any approved cleanup template.

## Privacy Rules

Routine inspection must not output:

- raw `cache_key`
- raw `user_scope_hash`
- `result_json`
- `explanation_json`
- prompt text
- learner answers
- AI feedback
- provider payload
- raw user ID, email, or name
- credentials or connection strings

Allowed routine output:

- aggregate counts
- `MIN` / `MAX` timestamps
- grouped bounded version labels
- average and maximum payload byte size
- orphan counts
- expired counts
- duplicate counts

Version and model labels may be output only when they are bounded operational metadata and do not contain user content.

Never use `SELECT *` in routine inspection.

## Writing Cache Read-Only Inspection

### Total Rows

```sql
SELECT COUNT(*) AS total_rows
FROM practice_writing_evaluation_cache;
```

### Expired And Unexpired Rows

Use the same database clock convention consistently. If the application and database operate in UTC, use `UTC_TIMESTAMP()`. If production is configured for a specific database timezone, record that before comparing.

```sql
SELECT
  COUNT(*) AS total_rows,
  COALESCE(SUM(expires_at <= UTC_TIMESTAMP()), 0) AS expired_rows,
  COALESCE(SUM(expires_at > UTC_TIMESTAMP()), 0) AS unexpired_rows
FROM practice_writing_evaluation_cache;
```

### Expired Age Buckets

These buckets are mutually exclusive. Do not add any extra overlapping bucket without labeling it as cumulative.

```sql
SELECT
  COALESCE(SUM(expires_at <= UTC_TIMESTAMP()
      AND expires_at > DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)), 0) AS expired_le_1d,
  COALESCE(SUM(expires_at <= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)
      AND expires_at > DATE_SUB(UTC_TIMESTAMP(), INTERVAL 7 DAY)), 0) AS expired_gt_1d_le_7d,
  COALESCE(SUM(expires_at <= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 7 DAY)
      AND expires_at > DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY)), 0) AS expired_gt_7d_le_30d,
  COALESCE(SUM(expires_at <= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY)), 0) AS expired_gt_30d
FROM practice_writing_evaluation_cache;
```

### Oldest And Newest Timestamps

```sql
SELECT
  MIN(created_at) AS oldest_created_at,
  MAX(created_at) AS newest_created_at,
  MIN(updated_at) AS oldest_updated_at,
  MAX(updated_at) AS newest_updated_at,
  MIN(expires_at) AS oldest_expires_at,
  MAX(expires_at) AS newest_expires_at
FROM practice_writing_evaluation_cache;
```

### Counts By Bounded Versions And Task Type

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

### Payload Size And Blank Payload Anomalies

```sql
SELECT
  COALESCE(SUM(result_json IS NULL OR TRIM(result_json) = ''), 0) AS null_or_blank_payload_rows,
  AVG(OCTET_LENGTH(result_json)) AS avg_payload_bytes,
  MAX(OCTET_LENGTH(result_json)) AS max_payload_bytes
FROM practice_writing_evaluation_cache;
```

### Duplicate Cache Key Anomalies

This should be zero because `cache_key` is the primary key.

```sql
SELECT COUNT(*) AS duplicate_cache_key_groups
FROM (
  SELECT cache_key
  FROM practice_writing_evaluation_cache
  GROUP BY cache_key
  HAVING COUNT(*) > 1
) AS duplicate_keys;
```

Do not export or print the keys from this diagnostic subquery.

### Table Size Estimate

```sql
SELECT
  table_name,
  table_rows,
  data_length,
  index_length,
  data_length + index_length AS total_bytes
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'practice_writing_evaluation_cache';
```

## Reading/Listening Cache Read-Only Inspection

Before running version classification, read these values from the deployed application source/config:

- `<CURRENT_PROMPT_VERSION>`
- `<CURRENT_SCHEMA_VERSION>`
- `<CURRENT_LANGUAGE>`
- `<CURRENT_MODEL>`

Do not guess or hardcode these values from memory.

### Total Rows

```sql
SELECT COUNT(*) AS total_rows
FROM question_explanation_cache;
```

### Counts By Version Tuple

```sql
SELECT
  ai_model,
  prompt_version,
  schema_version,
  explanation_language,
  COUNT(*) AS rows_count
FROM question_explanation_cache
GROUP BY ai_model, prompt_version, schema_version, explanation_language
ORDER BY rows_count DESC;
```

### Rows Outside Current Version Tuple

Replace placeholders with values verified from the deployed application.

```sql
SELECT COUNT(*) AS obsolete_version_rows
FROM question_explanation_cache
WHERE NOT (
  ai_model <=> '<CURRENT_MODEL>'
  AND prompt_version <=> '<CURRENT_PROMPT_VERSION>'
  AND schema_version <=> '<CURRENT_SCHEMA_VERSION>'
  AND explanation_language <=> '<CURRENT_LANGUAGE>'
);
```

The null-safe `<=>` operator is intentional: rows with any `NULL` version component are classified outside the exact current tuple instead of disappearing through SQL three-valued logic.

### Orphan Rows

```sql
SELECT COUNT(*) AS orphan_rows
FROM question_explanation_cache qec
LEFT JOIN practice_questions pq
  ON pq.id = qec.question_id
WHERE pq.id IS NULL;
```

### Payload Size And Blank Payload Anomalies

```sql
SELECT
  COALESCE(SUM(explanation_json IS NULL OR TRIM(explanation_json) = ''), 0) AS null_or_blank_payload_rows,
  AVG(OCTET_LENGTH(explanation_json)) AS avg_payload_bytes,
  MAX(OCTET_LENGTH(explanation_json)) AS max_payload_bytes
FROM question_explanation_cache;
```

### Duplicate Cache Key Anomalies

This should be zero because `cache_key` has a unique key.

```sql
SELECT COUNT(*) AS duplicate_cache_key_groups
FROM (
  SELECT cache_key
  FROM question_explanation_cache
  GROUP BY cache_key
  HAVING COUNT(*) > 1
) AS duplicate_keys;
```

Do not export or print the keys from this diagnostic subquery.

### Oldest And Newest Timestamps

```sql
SELECT
  MIN(created_at) AS oldest_created_at,
  MAX(created_at) AS newest_created_at,
  MIN(updated_at) AS oldest_updated_at,
  MAX(updated_at) AS newest_updated_at
FROM question_explanation_cache;
```

### Table Size Estimate

```sql
SELECT
  table_name,
  table_rows,
  data_length,
  index_length,
  data_length + index_length AS total_bytes
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'question_explanation_cache';
```

## JSON And LONGTEXT Verification

Always verify real production DDL:

```sql
SHOW CREATE TABLE practice_writing_evaluation_cache;
SHOW CREATE TABLE question_explanation_cache;
```

Record the actual type of:

- `practice_writing_evaluation_cache.result_json`
- `question_explanation_cache.explanation_json`

If Reading/Listening `explanation_json` is `LONGTEXT`:

- The Java parser is the validation boundary.
- Do not use `JSON_EXTRACT`, `JSON_VALID`, generated JSON columns, or DB JSON indexes as policy assumptions.
- Do not propose an `ALTER` from this runbook alone.

If production type is `JSON`:

- Record the divergence from migration history.
- Do not fix it in place.
- Escalate a schema alignment decision.

Classification guidance:

- `LONGTEXT` plus no DB JSON operators: likely harmless runtime declaration drift.
- DB JSON operators or native JSON queries introduced later: schema alignment review required.

## Decision Worksheet

Thresholds require operations approval. The examples below are evaluation aids, not policy.

Writing signals:

- total rows
- expired percentage
- expired rows older than 7 days and 30 days
- table bytes
- row growth per day/week
- cache delete failure metrics
- DB latency or lock evidence

Reading/Listening signals:

- total rows
- obsolete version percentage
- orphan percentage
- number of retained version tuples
- growth per deployment/version
- table bytes
- rollback requirement

Non-binding evaluation examples:

- A table with low row count and no latency impact may remain lazy-cleanup only.
- A table with sustained growth and old expired rows may justify bounded cleanup.
- A table with many old R/L versions may still be retained if rollback policy requires it.

## Bounded Writing Cleanup Template

**NOT AUTHORIZED BY DEFAULT.**

Use only after:

- production aggregates are reviewed
- a grace period is approved
- an operator owner is assigned
- backup and rollback implications are understood
- application metrics/logs are available
- maintenance window or risk acceptance is approved

Placeholders:

- `<BATCH_SIZE>`
- `<APPROVED_EXPIRED_BEFORE_UTC>`

Do not paste this block directly into production while placeholders remain unresolved. Replacement values must be reviewed, and the operator must confirm the intended cutoff before starting a transaction.

Do not run an unbounded delete such as deleting every row matching `expires_at <= ...` in one statement.

Recommended bounded pattern for MySQL:

```sql
DROP TEMPORARY TABLE IF EXISTS tmp_writing_cache_delete_candidates;

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_writing_cache_delete_candidates (
  cache_key CHAR(64) PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_writing_cache_delete_candidates (cache_key)
SELECT cache_key
FROM practice_writing_evaluation_cache
WHERE expires_at < '<APPROVED_EXPIRED_BEFORE_UTC>'
ORDER BY expires_at ASC, cache_key ASC
LIMIT <BATCH_SIZE>;

SELECT COUNT(*) AS candidate_rows
FROM tmp_writing_cache_delete_candidates;

SELECT
  COUNT(*) AS candidate_rows,
  MIN(pwec.expires_at) AS oldest_candidate_expires_at,
  MAX(pwec.expires_at) AS newest_candidate_expires_at,
  COALESCE(SUM(pwec.expires_at >= '<APPROVED_EXPIRED_BEFORE_UTC>'), 0) AS candidates_not_older_than_cutoff
FROM practice_writing_evaluation_cache pwec
JOIN tmp_writing_cache_delete_candidates c
  ON c.cache_key = pwec.cache_key;

DELETE pwec
FROM practice_writing_evaluation_cache pwec
JOIN tmp_writing_cache_delete_candidates c
  ON c.cache_key = pwec.cache_key;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_writing_cache_delete_candidates;
```

The temporary table is session-scoped and internally contains keys, but operators must not spool, export, or paste keys into tickets, chat, or logs. Drop it before and after each batch so stale candidate sets cannot be reused accidentally.

If any error occurs before `COMMIT`, run:

```sql
ROLLBACK;
DROP TEMPORARY TABLE IF EXISTS tmp_writing_cache_delete_candidates;
```

Do not leave the transaction open. Temporary table behavior is session-scoped and should not be treated as business-data rollback protection.

Between batches:

- pause
- check DB latency, lock waits, replication lag if applicable, application errors, and cache metrics
- keep batch size fixed unless a new approval changes it

## Writing Cleanup Stop Conditions

Stop immediately if:

- lock wait increases
- replication lag increases
- application DB latency increases
- application error rate increases
- cache delete failure metric increases
- candidate counts are unexpected
- schema or index differs from the expected shape
- current or unexpired rows appear in the candidate set
- transaction timeout or deadlock occurs

Do not retry indefinitely. Do not automatically increase batch size.

Post-checks:

```sql
SELECT
  COUNT(*) AS total_rows,
  COALESCE(SUM(expires_at <= UTC_TIMESTAMP()), 0) AS expired_rows,
  COALESCE(SUM(expires_at > UTC_TIMESTAMP()), 0) AS unexpired_rows
FROM practice_writing_evaluation_cache;
```

Also verify:

- learner attempt/result health
- cache/provider metrics
- application logs
- no learner attempt data changed

## Reading/Listening Cleanup Template

**NOT AUTHORIZED BY DEFAULT.**

Do not apply a TTL to Reading/Listening cache. Cleanup must be approved by explicit category, and each category must run separately.

Do not paste any cleanup block directly into production while placeholders remain unresolved. Replacement values must be reviewed, and the operator must verify the current-version tuple from the deployed application before starting a transaction.

Approved category A: exact obsolete version tuple.

```sql
DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_rl_cache_delete_candidates (
  cache_key CHAR(64) PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_rl_cache_delete_candidates (cache_key)
SELECT cache_key
FROM question_explanation_cache
WHERE ai_model = '<APPROVED_OBSOLETE_MODEL>'
  AND prompt_version = '<APPROVED_OBSOLETE_PROMPT_VERSION>'
  AND schema_version = '<APPROVED_OBSOLETE_SCHEMA_VERSION>'
  AND explanation_language = '<APPROVED_OBSOLETE_LANGUAGE>'
  AND (
    '<APPROVED_OBSOLETE_MODEL>' <> '<CURRENT_MODEL>'
    OR '<APPROVED_OBSOLETE_PROMPT_VERSION>' <> '<CURRENT_PROMPT_VERSION>'
    OR '<APPROVED_OBSOLETE_SCHEMA_VERSION>' <> '<CURRENT_SCHEMA_VERSION>'
    OR '<APPROVED_OBSOLETE_LANGUAGE>' <> '<CURRENT_LANGUAGE>'
  )
  AND NOT (
    ai_model <=> '<CURRENT_MODEL>'
    AND prompt_version <=> '<CURRENT_PROMPT_VERSION>'
    AND schema_version <=> '<CURRENT_SCHEMA_VERSION>'
    AND explanation_language <=> '<CURRENT_LANGUAGE>'
  )
ORDER BY updated_at ASC, cache_key ASC
LIMIT <BATCH_SIZE>;

SELECT COUNT(*) AS candidate_rows
FROM tmp_rl_cache_delete_candidates;

DELETE qec
FROM question_explanation_cache qec
JOIN tmp_rl_cache_delete_candidates c
  ON c.cache_key = qec.cache_key;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;
```

Approved category B: orphaned question rows.

```sql
DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_rl_cache_delete_candidates (
  cache_key CHAR(64) PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_rl_cache_delete_candidates (cache_key)
SELECT qec.cache_key
FROM question_explanation_cache qec
LEFT JOIN practice_questions pq
  ON pq.id = qec.question_id
WHERE pq.id IS NULL
ORDER BY qec.updated_at ASC, qec.cache_key ASC
LIMIT <BATCH_SIZE>;

SELECT COUNT(*) AS candidate_rows
FROM tmp_rl_cache_delete_candidates;

DELETE qec
FROM question_explanation_cache qec
JOIN tmp_rl_cache_delete_candidates c
  ON c.cache_key = qec.cache_key;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;
```

Approved category C: malformed or blank payload rows.

Routine inspection does not read raw payloads. The template below targets only null, blank, or whitespace-only payloads; it does not classify every malformed JSON payload.

```sql
DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_rl_cache_delete_candidates (
  cache_key CHAR(64) PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_rl_cache_delete_candidates (cache_key)
SELECT cache_key
FROM question_explanation_cache
WHERE explanation_json IS NULL
   OR TRIM(explanation_json) = ''
ORDER BY updated_at ASC, cache_key ASC
LIMIT <BATCH_SIZE>;

SELECT COUNT(*) AS candidate_rows
FROM tmp_rl_cache_delete_candidates;

DELETE qec
FROM question_explanation_cache qec
JOIN tmp_rl_cache_delete_candidates c
  ON c.cache_key = qec.cache_key;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;
```

If any Reading/Listening cleanup category errors before `COMMIT`, run:

```sql
ROLLBACK;
DROP TEMPORARY TABLE IF EXISTS tmp_rl_cache_delete_candidates;
```

Do not leave the transaction open. Do not retry indefinitely or increase batch size without a new approval.

Do not combine categories with a broad `OR` condition in one large cleanup. Current-version tuples must be excluded from obsolete-version cleanup. Versions inside an approved rollback window must be retained.

## Reading/Listening FK And Cascade Policy

Current state:

- no FK from `question_explanation_cache.question_id` to `practice_questions.id`
- no cascade cleanup on question deletion
- orphan cleanup is an operational/data policy decision

Adding FK/cascade requires a separate migration and must audit:

- existing orphan counts
- delete behavior of `PracticeQuestion`
- published-set edit/restore flows
- import and revision restore flows
- MySQL migration lock and cost

Read-only investigation:

```sql
SELECT COUNT(*) AS orphan_rows
FROM question_explanation_cache qec
LEFT JOIN practice_questions pq
  ON pq.id = qec.question_id
WHERE pq.id IS NULL;
```

## Metrics Operations

Current Phase 7B state:

- Micrometer in-process registry exists.
- No exporter or collector is configured.
- No Actuator endpoint is exposed.
- No Prometheus or OpenTelemetry bridge is configured.
- Metrics cannot support production alerts until an export/scrape path exists.

Metric names:

- `practice.ai.cache.operations`
- `practice.ai.cache.duration`
- `practice.ai.provider.operations`
- `practice.ai.provider.duration`

Bounded tags:

- `cache`: `writing`, `rl_explanation`
- `operation`: `lookup`, `parse`, `write`, `delete`
- cache `outcome`: `hit`, `miss`, `expired`, `malformed`, `success`, `failure`
- `feature`: `writing`, `rl_explanation`
- provider `outcome`: `success`, `fallback`, `failure`

Future export options, requiring a separate approved phase:

- secured Actuator endpoint
- Prometheus registry
- OpenTelemetry or another existing platform integration

## Metrics To Watch During Future Cleanup

If exporter/collector support exists later, watch:

- cache lookup failure
- cache write failure
- cache delete failure
- provider fallback
- provider failure
- cache and provider latency

Also watch database indicators:

- query latency
- lock waits
- deadlocks
- connection pool pressure
- replication lag, if applicable
- table size

Do not invent alert thresholds in this runbook. Thresholds require operations approval.

## Approval Matrix

| Area | Required decisions |
| --- | --- |
| Technical | Batch query and index safety; schema verification; transaction size; application compatibility |
| Operations | Production counts; cleanup timing; batch size; pause interval; monitoring; ownership; backup/recovery |
| Product/Data | R/L rollback window; old-version retention; orphan policy; whether cache has audit value |
| Security/Privacy | Access control; output handling; no raw payloads or keys; ticket/log redaction |

## Incident-Specific Payload Inspection

Routine runbook usage must not read payloads.

If an incident requires malformed payload inspection:

- require incident approval
- restrict to the smallest possible sample
- do not expose learner content
- use secure access
- do not paste payloads into tickets, chat, or logs
- redact and delete temporary outputs
- follow organizational privacy policy

This document intentionally provides no routine query returning raw payload.

## Accepted Debt Register

| Debt | Reopening condition |
| --- | --- |
| No production volume audit yet | Production aggregate inspection is completed and reviewed |
| No automated Writing cleanup | Expired rows/table growth are measured, grace period is approved, and operations approves batch limits |
| No R/L retention policy | Current/obsolete version tuples are confirmed, rollback window is approved, and orphan policy is decided |
| No R/L FK/cascade | Orphan count is reviewed and a separate migration risk assessment is approved |
| R/L entity declares JSON while migrations/local DDL use LONGTEXT | Production DDL differs, DB JSON operators are introduced, or schema alignment is requested |
| No metrics exporter/collector | Operations chooses a secured export path and alert ownership |
| No alert thresholds | Production baseline data and operational thresholds are approved |
| Local database is non-representative | Production read-only aggregate results are captured |

## Validation Checklist

Before committing changes to this runbook:

- Markdown renders with fenced SQL blocks.
- No credentials, hosts, usernames, emails, or real connection strings are included.
- No routine query uses `SELECT *`.
- No routine query outputs raw cache keys, user hashes, or payloads.
- Every DML template is marked **NOT AUTHORIZED BY DEFAULT**.
- Cleanup templates use bounded candidate batches.
- No unbounded production delete is suggested.
- Placeholders remain visibly unresolved.
- Table and column names match migrations and verified local DDL.
