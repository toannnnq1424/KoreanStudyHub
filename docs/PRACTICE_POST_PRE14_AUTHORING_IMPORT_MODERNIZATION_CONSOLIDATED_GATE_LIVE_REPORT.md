# Practice post-Pre-14 authoring/import modernization consolidated gate

- Gate: `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE`
- Recorded: 2026-08-02
- Baseline: `cc83d25cf280212a3b0e7f466efe0fd331a0a7fd` (`Merge PR #64`)
- Verdict: **GO**

## Baseline and scope controls

Before any audit command, `HEAD`, local `main`, and `origin/main` each resolved to the required baseline SHA, and `git status --porcelain=v1 --untracked-files=all` was empty. The audit then used that merged tree directly. No rebase, merge, branch, commit, push, pull request, production/test/migration/schema edit, provider-console work, or later-phase implementation was performed.

The gate read the roadmap amendment (including the AIM-2 checkpoint), ADR 0012, the frozen architecture contract, all AIM-3 through AIM-8 live change logs, the operations runbook, both frozen JSON schemas, and all seven frozen examples. It then checked the implementation and tests on the merged baseline rather than accepting live-log claims as sufficient evidence.

## Requirement-to-evidence matrix

| Requirement | Evidence on merged baseline | Result |
|---|---|---|
| Quick/Advanced/Legacy Excel and Basic/Advanced PDF stop at candidate review; only explicit apply writes a draft; stale/idempotent/auth paths fail closed | Excel detection remains Quick -> Advanced -> exact Legacy. Both Basic and Advanced PDF modes validate/assemble into the same frozen candidate boundary. Excel preview and PDF assembly call candidate creation/reuse only. Direct mutation scan found `0` pre-apply draft writes; `PracticeAuthoringCandidateApplyService` is the sole candidate draft writer. Current V85 DB journeys produced four source candidates, left the draft unchanged before apply, applied Quick explicitly once, replayed idempotently, and rejected Advanced stale apply without mutation. Preview is read-only and owner/material authorization is rechecked. | PASS |
| Canonical R/L/W/S contract, Writing Q51-Q54, and Speaking text-only authority | Frozen schema/examples, codecs, validators, publisher, and the shared player-preview fragment agree: Q51/Q52 each carry exactly two typed blanks, Q53/Q54 carry none, with points 10/10/30/50. Speaking authoring is `manual_text`/`text_only`/`none`, with null prompt audio/play limit. Evaluator payload declares no learner audio and no acoustic evidence. Candidate review and editor use the existing preview authority. | PASS |
| Zero-based Practice AI binding and fail-closed routing | Candidate schema has `bindingRevision.minimum = 0`. A focused compatibility test proves revision `0` reaches the fake transport while a negative revision is rejected before transport. Missing, disabled, stale, capability-mismatch, profile-mismatch, and digest/revision changes fail closed. Exactly six Practice purposes are present. Practice production scans found `0` shared `AiClient`, global-provider repository, or global fallback matches. | PASS |
| Exactly three storage profiles with exact private reads and preserved compatibility/lifecycle semantics | Enum, resolver, adapters, V85 data, and tests contain exactly `GENERAL_UPLOADS`, `PRACTICE_AUTHORING`, and `PRACTICE_SPEAKING`. Non-null profile codes resolve exactly; null legacy rows use local storage only. New writes are profile-bound. Migration copy/hash/verify precedes the CAS logical switch; deletion remains delayed and confirmed; lifecycle states and retained READY behavior remain intact. No public/presigned/general fallback was found in the private Practice paths. | PASS |
| Fresh and authoritative upgrade migration integrity | Fresh disposable MySQL: V1 -> V85, `85` successful / `0` failed / max rank `85`. Authoritative upgrade: V1 -> V75 first (`75/0/75`), then V76 -> V85 (`85/0/85`). Migration inventory is `85` unique files with no missing V1-V85 version. Both checksum manifests passed `85/85`. V83-V85 prohibited destructive-token count is `0`; no Flyway repair/clean, reset, database drop, or rollback-down path was used. | PASS |
| Static ownership/no-go scans, syntax checks, and no real external calls | JS syntax passed `75/75`; frozen JSON passed `9/9`. Direct scans: pre-apply Excel/PDF draft mutation `0`, shared AI/global fallback `0`, private-storage public/general fallback `0`, retired direct-PDF class files `0`, candidate draft-writer files `1`. Real AI/R2/STT/TTS calls were `0/0/0/0`; provider behavior tests used fakes/mocks only. | PASS |
| Evidence is reconciled with merged main | Contracts and historical AIM-2 through AIM-8 evidence were traced to current source, migrations, tests, templates, and disposable-database observations at the exact required SHA. Historical V83/V84 guarded persistence tests were not misapplied to a V85 database; the current V85 consolidated journey and checksum/static evidence cover their retained contracts. | PASS |

## Exact verification counts

Counts below are reported per lane because selectors intentionally overlap; they must not be added as a unique-test total.

| Verification lane | Tests run | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| AIM-8 focused compatibility/unit lane | 29 | 29 | 0 | 0 | 0 |
| Broad current cross-slice regression selector | 270 | 258 | 0 | 0 | 12 |
| AIM-5 authorization guard lane | 4 | 4 | 0 | 0 | 0 |
| Fresh V1 -> V85 consolidated DB journey | 1 | 1 | 0 | 0 | 0 |
| Pre-14 lineage V1 -> V75 DB checkpoint | 1 | 1 | 0 | 0 | 0 |
| Authoritative V76 -> V85 consolidated DB journey | 1 | 1 | 0 | 0 | 0 |
| Storage/profile/lifecycle focused lane | 104 | 104 | 0 | 0 | 0 |

The broad lane's 12 guard-skips were: four AIM-5 authorization tests, one current AIM-8 DB test, one V75 lineage test, one historical V84 AI persistence test, and five historical V83 candidate persistence tests. The first six current gates were executed explicitly in the separate lanes above. The six historical schema-pinned tests deliberately assert V84/V83 and were reconciled through the current V85 journey, focused behavior tests, source audit, and checksum history instead of being run against the wrong schema version.

Additional exact counts:

- Java 17 package build: passed.
- Standalone JavaScript syntax: `75/75`.
- Frozen schema/example JSON syntax: `9/9` (`2` schemas, `7` examples).
- Migration inventory: `85` files, `85` unique versions, `0` missing versions.
- Migration checksums: `85/85` across `practice-migrations-v1-v56.sha256` and `practice-migrations-v57-v85.sha256`.
- Fresh migration: `85` successful, `0` failed, max installed rank `85`.
- Upgrade checkpoint: `75` successful, `0` failed, max installed rank `75`; after upgrade: `85/0/85`.
- Fresh and upgraded V85 journey state: `4` candidates, `1` applied candidate, `1` `DRAFT_APPLIED` event, `1` stale `CONFLICT` event, `6` distinct enabled purpose bindings, `3` distinct enabled storage profiles, `0` AI execution audits, `0` capability runs, and `0` storage migration jobs.
- Real provider calls: AI `0`, R2 `0`, STT `0`, TTS `0`.

The database verification used isolated disposable local catalogs and no real secret. They were left intact after read-only evidence queries because this gate did not authorize destructive cleanup.

## Blockers and approvals

There are no remaining blockers or approvals required to close this epic. Provider-console links, later legacy retirement, Phase 14/15/Pre-15, and Speaking direct-audio/acoustic work remain outside this gate and were not opened or implemented.

The authoring/import modernization epic is eligible to close. This verdict does not authorize or start a later phase.
