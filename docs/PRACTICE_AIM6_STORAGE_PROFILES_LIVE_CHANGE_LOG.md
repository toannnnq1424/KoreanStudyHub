# Practice AIM-6 Storage Profiles Live Change Log

Recorded: `2026-08-02`

Status: `AIM_6_IMPLEMENTATION_COMPLETE_READY_FOR_COORDINATOR_AUDIT`

## 1. Exact baseline and authority checkpoint

Before the first AIM-6 edit, this worktree was clean and detached at the exact
merged AIM-5 baseline required by the delegation:

```text
HEAD:        549376e10d1106d03867bf69f3100533547dbd74
origin/main: 549376e10d1106d03867bf69f3100533547dbd74
subject:     Merge pull request #60 (AIM-5)
```

The following sources were read in full before implementation:

1. `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`,
   especially AIM-6 ownership and no-go boundaries;
2. `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`,
   especially Section 7;
3. `decisions/0012-practice-authoring-import-modernization-boundaries.md`;
4. `PRACTICE_AIM5_AI_CONTROL_PLANE_LIVE_CHANGE_LOG.md`; and
5. the existing global ObjectStorage/Admin storage settings, Practice source
   PDF/import assets, lecturer authoring assets, and learner Speaking media
   upload/playback/transcription/discard/cleanup paths.

Immediately before allocating SQL, the refreshed `origin/main` Flyway
inventory contained 84 unique, continuous migrations V1-V84 with no gap or
duplicate. AIM-6 therefore allocated exactly
`V85__practice_storage_profiles.sql`. The final inventory is continuous
V1-V85; V86 remains the next free identity.

## 2. Locked implementation slices

| Slice | State | Evidence |
|---|---|---|
| Exact profile authority | `COMPLETE` | exactly `GENERAL_UPLOADS`, `PRACTICE_AUTHORING`, `PRACTICE_SPEAKING`; fixed immutable prefixes |
| Admin control plane | `COMPLETE` | existing `PERM_system.storage`, CRUD/config, revision checks on save/toggle/delete, ordinary mask, explicit no-store reveal |
| Backend readiness | `COMPLETE` | only `LOCAL` or `R2`; LOCAL is opt-in outside test/local; incomplete/invalid/disabled writes fail closed |
| Wider-product bridge | `COMPLETE` | new generic writes use `GENERAL_UPLOADS`; bounded legacy generic reads remain; no Practice adapter injects it |
| Practice authoring data plane | `COMPLETE` | source PDF and lecturer assets use `PRACTICE_AUTHORING`; private app-authorized reads; exact profile/key locks and durable cleanup |
| Speaking data plane | `COMPLETE` | private `PRACTICE_SPEAKING`; exact owner/attempt/question/media authorization; no URL/presign; durable bounded cleanup |
| Nullable compatibility | `COMPLETE` | non-null rows resolve exactly one profile/key; null rows resolve only the current legacy-local root; no bucket/profile search |
| Byte migration seam | `COMPLETE` | explicit job planning, copy and source hash, target size/hash verification, transactional CAS row switch, delayed confirmed source delete |
| Production migration boundary | `COMPLETE` | no job seed, startup runner or scheduler; no production bytes or R2 endpoint were touched |

## 3. Schema and control-plane ledger

Migration V85 adds `storage_profiles` with the fixed profile-code and backend
checks, revision, actor/timestamps, R2 configuration and secret fields. It
seeds only the three required LOCAL development/test identities. Production
has `app.storage-profiles.allow-local=false` by default, so these bootstrap
rows cannot silently authorize production writes.

Nullable `storage_profile_code` identities were added to:

- `lecturer_assets`;
- `practice_pdf_import_sessions`;
- `practice_asset_lifecycle_tasks`;
- `practice_speaking_media`; and
- `practice_speaking_media_cleanup_tasks`.

The migration also adds the explicit-only
`practice_storage_migration_jobs` durable state machine. It creates no work
rows and has no destructive data statement.

The resolver requires the exact fixed prefix, lowercase safe relative object
keys, bounded lengths and no traversal, backslash, drive/colon or absolute
path form. A write requires an enabled profile. R2 additionally requires a
complete account/access/secret/bucket/HTTPS endpoint/region configuration.
Reads may retain an exact disabled profile solely for forward-only rollback
and already-profile-coded object continuity.

## 4. Admin control-plane ledger

- Added `/admin/settings/storage-profiles` list/create/edit/toggle/delete and
  explicit secret-reveal flows.
- The whole controller uses the existing
  `@PreAuthorize("hasAuthority('PERM_system.storage')")`; the existing
  `/admin/**` role boundary still applies independently.
- Profile code and key prefix are fixed. All mutations lock the row, compare
  the submitted revision, and let JPA `@Version` advance it.
- Ordinary form/list responses never contain the stored secret. Blank or the
  `********` sentinel retains it. Reveal is a distinct authorized JSON read
  with `Cache-Control: no-store`.
- Enabling LOCAL when local use is not explicitly allowed, or enabling an
  incomplete/invalid R2 profile, fails before persistence.
- A profile must be disabled and unreferenced before deletion. Reference
  checks include every AIM-6 logical/lifecycle/migration identity.

## 5. Practice authoring and PDF ledger

- `ProfiledPracticeAuthoringStorage` is the Practice-owned primary adapter.
  New writes always return `PRACTICE_AUTHORING` plus their physical backend.
- Object namespaces are restricted to `lecturer-assets/` and
  `practice-pdfs/`. Objects remain private; lecturer/student access continues
  through the existing app authorization service and material controller.
- Lecturer asset row creation, deduplication, promotion, reads, inspection,
  reference locks and cleanup tasks carry the exact `(profile, key)` identity.
  Legacy lock queries explicitly require a null profile.
- Unbound temporary assets retain the existing 24-hour policy. Bound/draft/
  published assets remain protected by reference locks. Physical deletion is
  claimed durably, rechecks all exact-identity references, confirms absence,
  and only then completes the logical deletion state.
- PDF upload sessions now store the exact profile. Preview, page extraction,
  region text extraction, crop generation, AI payload image reads and session
  deletion all use the adapter. A null historical path is accepted only when
  it is a regular file below the current `practice-pdfs` legacy root.
- Session deletion fails the database delete closed when physical PDF
  deletion cannot be confirmed; scheduled expiry therefore retains durable
  retry authority in the session row.

## 6. Learner Speaking ledger

- `ProfiledPracticeSpeakingAudioStorage` writes private temporary objects to
  `PRACTICE_SPEAKING`, promotes by private copy to a fresh ready key, and
  exposes only server-side streams. It has no public or presigned URL API.
- Playback requires the exact owner, attempt, question and media row in an
  allowed attempt state. Transcription uses the same owner/attempt/question
  authority projection. Cross-profile codes and non-local legacy null rows
  fail closed before storage reads.
- New writes follow
  `UNREFERENCED_TEMPORARY -> READY -> SUPERSEDED/DELETION_PENDING -> DELETED`.
  Temporary objects receive a 24-hour expiry task. Superseded objects receive
  a 24-hour retention task. READY objects are never age-deleted.
- Cleanup rows retain media/profile/key identity, use leased claims and
  bounded exponential retry, and stop after eight failed physical attempts.
  A media tombstone is written only after delete plus absence confirmation.
- Attempt discard, explicit delete, activation compensation and promoted-temp
  cleanup use the same durable mechanism. No acoustic/direct-audio grading
  behavior was introduced.

## 7. Explicit byte-migration and rollback ledger

The AIM-6 seam is intentionally inert until an authorized caller explicitly
plans and processes a job. Its order is:

```text
claim source identity
  -> copy source while computing size/SHA-256
  -> write target exact profile/key
  -> read target and verify size/SHA-256
  -> transactionally compare-and-set logical row identity
     and persist delayed cleanup intent
  -> after the delay, claim source deletion
  -> delete source and confirm physical absence
  -> complete the durable job
```

Claims are leased; copy and cleanup each stop after eight failures. A failed
verification never updates the logical row. Target and logical identities are
unique and type-bound (`LECTURER_ASSET`/`PDF_IMPORT_SESSION` to authoring,
`SPEAKING_MEDIA` to Speaking). Tests use an in-memory fake only.

Rollback remains forward-only per Contract Section 7.4:

1. disable new R2 writes without dropping V85 schema or audit/job evidence;
2. continue reading every non-null row through its exact profile adapter;
3. continue reading null historical rows only through their bounded current
   legacy-local root;
4. do not search another profile/bucket and do not move/delete bytes blindly;
5. only consider a pre-AIM-6 application after profile-coded
   `GENERAL_UPLOADS` objects have been explicitly exported to its legacy
   settings and verified; and
6. preserve durable cleanup/migration state until every physical action is
   confirmed.

## 8. Verification ledger

All Java commands used OpenJDK 17 (`17.0.19`). All object/provider behavior in
tests used local disposable roots, mocks or in-memory fakes. No test invoked a
real R2/storage provider, STT or TTS endpoint.

| Check | Result |
|---|---|
| Java 17 compile and test-compile | `GREEN` |
| AIM-6 focused unit/static contracts | `GREEN` |
| Admin auth/revision/masking/no-store contracts | `GREEN` |
| Path escape, cross-profile and legacy-null compatibility contracts | `GREEN` |
| Authoring/Speaking lifecycle, idempotency, race and bounded retry contracts | `GREEN` |
| Fake byte migration copy/verify/CAS/delayed-delete contracts | `GREEN` |
| Fresh V1-V85 disposable migration and Hibernate validate | `GREEN` |
| Broad Practice regression selector | `GREEN` — 509 tests, 0 failures, 0 errors, 0 skipped across 76 reports |
| JavaScript syntax and `git diff --check` | `GREEN` |
| Static no-go scans | `GREEN` |

The broad selector is:

```text
PracticeStorage*Test,StorageProfile*Test,ProfiledPractice*Test,
PracticeAsset*Test,PracticePdf*Test,PracticeSpeaking*Test,Speaking*Test
```

## 9. Disposable database evidence

No existing database/container was reset, cleaned, repaired, deleted or
reused as a fresh proof. Only these AIM-6 disposable MySQL 8.0 containers and
catalogs were used; they are intentionally left available for coordinator
inspection:

```text
ksh-aim6-storage-20260802-1524-mysql
  127.0.0.1:65185 -> 3306
  ksh_test_aim6_storage_20260802_1524

ksh-aim6-storage-final-20260802-1525-mysql
  127.0.0.1:65196 -> 3306
  ksh_test_aim6_storage_final_20260802_1525

ksh-aim6-regression-20260802-1526-mysql
  127.0.0.1:65207 -> 3306
  ksh_test_aim6_regression_20260802_1526
```

The first fresh attempt used the repository's limited application user and
stopped safely at V1 because V1 requires `CREATE DATABASE`; it was left
untouched and was never repaired or cleaned.

The second catalog is the authoritative fresh proof. Root authority was used
only inside that newly created disposable container. Flyway applied V1-V85
from empty, Hibernate schema validation passed, and the AIM-6 persistence
integration passed. Final read-only evidence is:

```text
successful Flyway rows / latest / failed: 85 / 85 / 0
configured / distinct / enabled profiles: 3 / 3 / 3
migration jobs:                           0
```

The third catalog runs focused and broad regressions on V85. Expected FK and
unique-constraint messages in negative-path tests are not test failures.

## 10. No-go ledger

```text
real R2/provider/storage call:                    0
real STT/TTS call:                                0
production byte migration/job seed/auto worker:  0
AIM-7 Basic generator/UI/candidate orchestration: 0
Practice player/scoring/publisher changes:        0
Speaking direct-audio/acoustic grading:           0
Practice fallback to GENERAL_UPLOADS:             0
profile/bucket search for Practice reads:          0
production data migration/destructive DB action:  0
existing DB reset/clean/repair/delete:             0
task/worktree/branch/commit/push/PR:               0
```

The only AIM-5 integration file changed is the learner transcription media
resolver, solely to carry and enforce the AIM-6 exact storage identity. No AI
profile, binding, transport, model, prompt or provider-selection authority was
changed.

## 11. Final handoff boundary

- `HEAD` and `origin/main` remain the exact AIM-5 merge commit
  `549376e10d1106d03867bf69f3100533547dbd74`; all AIM-6 changes are unstaged
  worktree changes.
- No task, worktree, branch, commit, push or pull request was created.
- No approval, secret, real provider/storage operation, destructive action or
  unresolved product decision was required.
- Remaining work is coordinator-owned diff/security/test audit and packaging.

## 12. Coordinator acceptance audit

The coordinator independently re-read the migration seam, exact-profile
authoring/PDF paths, Speaking cleanup lifecycle and Admin/storage-profile
boundary. Static scans found no Practice fallback to `GENERAL_UPLOADS`, no
public/presigned Practice URL, no migration scheduler/startup runner and no
AIM-7/player/scoring/publisher scope expansion. JavaScript syntax and
`git diff --check` are clean.

An additional empty catalog was migrated and validated independently:

```text
ksh-aim6-regression-20260802-1526-mysql
  127.0.0.1:65207 -> 3306
  ksh_test_aim6_coord_final_20260802_0900
```

Using the repository's production-equivalent JDBC timezone
`serverTimezone=Asia/Ho_Chi_Minh`, the broad AIM-6 selector passed again with
509 tests, 0 failures, 0 errors and 0 skipped. Two earlier coordinator-only
diagnostic catalogs used `serverTimezone=UTC`; their fixed-clock lease tests
correctly exposed the timezone mismatch and are not accepted regression
evidence. No catalog was reset, cleaned, repaired or deleted.
