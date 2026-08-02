# Practice Authoring Import Modernization Operations Runbook

Recorded: `2026-08-02`

Scope: AIM-2 through AIM-8 operational verification and bounded forward-only
rollback. This runbook prepares evidence for, but does not close,
`POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE`.

## 1. Invariants

- Excel and PDF authoring stop at a persistent candidate. Only explicit
  candidate apply may write `PracticeDraft`; publishing stays separate.
- Practice resolves exactly one of the six purpose bindings. Missing,
  disabled, incompatible or revision-changed authority fails closed; there is
  no global provider fallback.
- Binding revisions are zero-based: the first persisted binding revision `0`
  is valid, while negative, missing or non-integral candidate identities fail
  closed. V84/JPA revision semantics remain unchanged.
- Practice authoring and learner Speaking bytes resolve their exact persisted
  profile/key. Writes never fall back to `GENERAL_UPLOADS`, another profile or
  legacy local storage.
- Profile-coded and legacy-null reads remain distinct. Null is only the bounded
  pre-AIM-6 local identity; it never means search every configured location.
- V83-V85 are additive. Candidate/apply/audit/profile/migration-job rows and
  profile columns remain present during rollback.
- Real provider, R2, STT and TTS calls are not part of automated acceptance.

## 2. Pre-deploy evidence

Run with JDK 17 and providers disabled:

1. Verify `HEAD`, `main` and the audited integration SHA agree and the worktree
   diff is the reviewed package.
2. Verify the migration filenames are unique and continuous V1-V85. Check
   every byte against
   `practice-migrations-v1-v56.sha256` and
   `practice-migrations-v57-v85.sha256`.
3. On a new disposable catalog, run Flyway V1-V85, Hibernate schema validation
   and the AIM-8 consolidated persistence journey.
4. On a different new disposable catalog, run the authoritative V1-V75 lineage
   selector, then upgrade in place to V85 and repeat schema/application
   validation. Never use Flyway `repair`, `clean`, schema reset or a reused
   application catalog as proof.
5. Run the cross-slice Excel/PDF/candidate/preview/publisher/AI/storage selector,
   JavaScript syntax checks and the AIM-8 static ownership scans.
6. Record test totals, migration row counts, zero no-go matches and real-call
   counts `AI/R2/STT/TTS = 0/0/0/0` in the AIM-8 live change log.

## 3. Read-only database checks

The following checks are read-only; substitute the catalog through the normal
database client rather than embedding credentials in a command history.

```sql
SELECT COUNT(*) AS successful,
       SUM(success = 0) AS failed,
       MAX(CAST(version AS UNSIGNED)) AS latest
FROM flyway_schema_history;

SELECT source_kind, state, COUNT(*)
FROM practice_authoring_candidates
GROUP BY source_kind, state
ORDER BY source_kind, state;

SELECT result, COUNT(*)
FROM practice_authoring_candidate_apply_events
GROUP BY result;

SELECT purpose_code, enabled, revision
FROM practice_ai_purpose_bindings
ORDER BY purpose_code;

SELECT profile_code, backend, enabled, revision, key_prefix
FROM storage_profiles
ORDER BY profile_code;

SELECT logical_type, state, COUNT(*)
FROM practice_storage_migration_jobs
GROUP BY logical_type, state
ORDER BY logical_type, state;
```

Expected migration identity is 85 successful rows, zero failed rows and latest
V85. The only profile codes are `GENERAL_UPLOADS`, `PRACTICE_AUTHORING` and
`PRACTICE_SPEAKING`; the only purpose codes are the six frozen Practice
purposes. A nonzero candidate, apply, audit or migration-job count is an
inventory item, not permission to delete it.

## 4. Incident containment

For a candidate/import incident:

1. Deny the new lecturer entry POST routes at the authenticated application
   edge while keeping candidate review/read and already-authorized exact-profile
   reads available:
   - `/practice/manage/excel/import`;
   - `/practice/manage/pdf-authoring/candidates`; and
   - `/practice/manage/import-sessions/{id}/generate`.
2. Do not re-enable retired direct-draft Excel/PDF actions. Existing unapplied
   candidates remain inert and retained for audit.
3. A target draft version conflict is resolved by reopening the target and
   creating a new candidate; never rewrite `base_draft_version` or its digest.
4. Do not reverse a successful apply by deleting staging rows. Use ordinary
   draft revision/history semantics if an authorized lecturer needs a content
   correction.

For an AI incident:

1. Disable the affected exact purpose binding in Admin. Disable the provider
   profile too only when all purposes using it should stop.
2. Preserve purpose binding revisions, capability-test rows and redacted
   execution audits. Do not change a snapshot to point at another provider.
3. Missing/disabled authority must remain a closed failure. Do not configure a
   shared `AiClient` or global-provider fallback.

For a storage incident:

1. Disable the affected profile to stop new writes. Exact profile-coded reads
   remain the rollback authority, including reads of a disabled profile.
2. Stop invoking explicit migration processing. AIM-6 has no startup runner,
   scheduler or seeded job, so no worker shutdown is required for this seam.
3. Preserve pending lifecycle/migration jobs. A physical delete is complete
   only after absence confirmation; retry state remains durable after failure.
4. Never redirect a Practice write to `GENERAL_UPLOADS`, another Practice
   profile or legacy-null local storage.

## 5. Forward-only application rollback

1. Keep V83-V85 and their Flyway history intact. Do not drop tables/columns,
   delete candidates/audits/jobs, edit migration bytes, run Flyway repair, or
   reset/clean the database.
2. Keep the entry-route deny rules from Section 4 in force so an older runtime
   cannot expose an importer direct-draft path.
3. Disable all new Practice purpose bindings before an application downgrade.
4. Stop new R2 writes while preserving exact-profile reads and bounded
   legacy-null reads.
5. Before any pre-AIM-6 application is considered, inventory every non-null
   storage identity. Profile-coded `GENERAL_UPLOADS` objects must be explicitly
   exported to its legacy settings representation and verified. Practice
   private profiles are not exported into general uploads.
6. Preserve logical location until copy size/SHA-256 verification and the
   transactional compare-and-set succeed. Old-byte deletion stays delayed and
   separately confirmed.
7. Roll forward to the corrected application when possible. Database rollback
   is never implemented by reverse SQL or destructive cleanup.

## 6. Lifecycle and cleanup checks

- Unreferenced Practice authoring/PDF workspace objects: retain the existing
  24-hour expiry policy, reference locks and durable retry state.
- Speaking `UNREFERENCED_TEMPORARY`: delete by 24 hours through a durable task.
- Speaking `SUPERSEDED`: enqueue immediately and delete by 24 hours unless a
  retained reference blocks it.
- Speaking `READY`: never age-delete while its immutable attempt is retained.
- Mark `DELETED` only after physical absence is confirmed; otherwise keep a
  bounded retry/error state.
- Reads require exact owner/draft/session or owner/attempt/question/media
  authority and are always application-mediated; there is no public/presigned
  Practice URL in v1.

## 7. Post-epic follow-ups, not AIM-8 work

- Legacy Excel V1, Advanced Excel V2, Advanced PDF crop/region workspace and
  all AIM-2/AIM-5/AIM-6 additive tables remain in service. Retirement and
  schema compaction require a persisted-use inventory, a separate approval and
  their own compatibility/migration plan.
- Provider API-console/documentation links remain a bounded control-plane UX
  refinement after AIM-8. They do not alter provider binding identity or
  authorize this slice to add external links.
- Speaking direct-audio/acoustic scoring remains outside this epic and follows
  its existing Pre-15 policy/privacy/calibration gates.

Do not declare Phase 14, Phase 15, Pre-15 or legacy retirement from this
runbook. The consolidated gate remains a separate coordinator decision over
the exact integrated evidence set.
