# Schema reduction audit — 2026-08-09

## Scope and evidence

This audit uses the Flyway chain through `V114`, JPA entity ownership, and the
current runtime workflow documentation. A chronological reconciliation of
`CREATE TABLE` and `DROP TABLE` statements resolves **87 active table names**
after V114. That is a migration-source count, not a substitute for checking a
specific deployed schema: the authoritative count for a deployed database is:

```sql
SELECT COUNT(*) AS base_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE';
```

Run that query against the disposable development database after Flyway V114
has completed. It excludes views and detects an environment that is behind the
reconciled migration state.

## Completed reduction: Korea Discovery News

`V112__remove_discovery_news_and_promote_dictionary_settings.sql` retires the
complete Discovery News product boundary. It removes the four surviving tables:

| Removed table | Former responsibility |
| --- | --- |
| `news_sources` | external feed registry and crawl state |
| `news_articles` | imported public news metadata/editorial material |
| `news_vocabularies` | article-scoped vocabulary cache |
| `news_ingestion_runs` | scheduled/manual crawl history |

The migration first promotes any legacy KRDICT key/base URL into the shared
`DICTIONARY` setting group, then removes Discovery-only settings and the
retired AI editorial prompt. This avoids dropping the user-facing Korean
dictionary lookup or the save-to-Flashcard flow.

## Practice audit: no safe table merge in the current increment

The current Practice boundary has an intentionally separate configuration,
storage and evaluation lifecycle. The following groups look numerous but are
not duplicates. Merging or deleting them before a product-scope decision would
break an existing invariant.

| Table family | Why it remains separate | Safe action now |
| --- | --- | --- |
| Published/version graph (`practice_*_versions`, sections, groups, questions) | preserves an immutable attempt snapshot while live authoring content changes | Keep |
| Explanation artifacts, bindings and generation tasks | artifact payload, current/superseded version binding and leased background work have distinct retention and retry semantics | Keep |
| AI profiles, purpose bindings, capability runs and execution audits | separates credentials/configuration from capability negotiation and request evidence | Keep |
| Authoring candidates and apply events | candidate review and idempotent apply audit are different lifecycle records | Keep |
| Speaking prompt/source/revision/artifact/task/version-context tables | preserves provenance, review/retry state and the exact prompt visible to an attempt | Keep |
| Speaking media, consent/grants and cleanup | storage lifecycle, privacy authorization and deletion evidence cannot share a destructive table safely | Keep |
| Storage profiles and migration jobs | profile configuration and asynchronous migration status are separate operational state | Keep |

The previous compact migrations already removed the obvious transient Practice
tables. There is no cache/version/authoring-table merge that is both reversible
and semantics-preserving in this codebase today.

## Completed reduction: four unreferenced non-Practice legacy tables

The following tables have no current production Java reference after tracing
the migration chain. Their removal was explicitly approved and is isolated in
`V113__remove_unreferenced_legacy_tables.sql`:

| Removed table | Evidence | Associated cleanup |
| --- | --- | --- |
| `content_versions` | No entity/repository/service reference; its only historical child, `activity_content_versions`, was removed earlier | Table removed after V96 made it independent. |
| `lesson_contents` | No entity/repository/service reference; current lesson content uses the later lesson/template model | Table removed; current lesson and Library snapshots remain intact. |
| `login_history` | No current writer/reader/controller reference | Table and dead `system.login_history` RBAC permission removed. |
| `user_verification_tokens` | No entity/repository/service reference; current authentication uses the active password-reset/session flows | Table removed; active password-reset/session tables are untouched. |

Removing these four takes the reconciled count from 94 to 90. It still does not
justify deleting Practice version, authoring, media or AI-evidence tables to
force a nominal 70-table limit.

## Completed reduction: Practice collaboration and dark-audio review experiment

The owner explicitly retired cross-lecturer co-authoring and the non-score-bearing
direct-audio reviewer experiment. `V114__retire_practice_collaboration_and_dark_audio_review.sql`
removes three tables and the now-orphan set self-lock columns:

| Removed object | Why removal is capability-safe |
| --- | --- |
| `practice_authoring_collaborations` | It only granted another Lecturer mutation rights. The authenticated `/practice` catalog still returns every `PUBLISHED + GLOBAL` set regardless of creator; `/practice/manage` is now owner-only. |
| `practice_speaking_direct_audio_dark_observations` | It held reviewer-only experimental observations and never contributed to learner scores. |
| `practice_speaking_audio_reviewer_access_events` | It audited only inspection/playback of the dark observations and carried a foreign key to the retired table. |
| `practice_sets.owner_locked`, `locked_by`, `locked_at` | They existed to stop collaborator edits. With owner-only mutation they formed an orphan self-lock rather than a useful authorization boundary. |

This removal deliberately retains `practice_speaking_media`, consent and future
reviewer-grant governance, media cleanup jobs, OpenAI STT, the Speaking evaluator,
AI control-plane bindings and immutable attempt/version state. It therefore does
not remove audio upload, transcription or AI scoring.

## What would be required to approach a ~70 table cap

Removing the four Discovery tables, four unreferenced legacy tables, and three
retired Practice capability tables is implemented, reducing the
migration-reconciled count to 87. Reaching roughly
70 still requires a product decision, not a blind normalization pass. The viable scope
choices are to retire one or more whole Practice capabilities (for example
direct-audio review evidence, AI authoring workflow, or immutable historical
versioning) and then remove their complete routes, workers, configuration,
tests and tables in a separate migration.

Do not merge live and versioned Practice tables merely to lower the count: it
would let later edits rewrite an attempt's original question, rubric or media
context. Do not merge job/audit tables into content rows: it would lose leases,
retry evidence or retention boundaries.

## Follow-up measurement

After applying V114, record the exact count from `information_schema` and the
table list in the audit report. Any later proposal must name:

1. the complete retired user workflow;
2. every owning service, route, worker and setting;
3. data retention/export handling; and
4. a forward Flyway migration plus a rollback/data-loss plan.
