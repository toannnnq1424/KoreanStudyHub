# Practice AIM-5 AI Control Plane Live Change Log

Recorded: `2026-08-02`

Status: `AIM_5_IMPLEMENTATION_COMPLETE_READY_FOR_COORDINATOR_AUDIT`

## 1. Exact baseline and authority checkpoint

Before the first AIM-5 edit, the worktree was clean and detached at the exact
merged AIM-4 baseline required by the delegation:

```text
HEAD:        98f443470d1b505ed951c61bb3a984c6b58f5c9e
origin/main: 98f443470d1b505ed951c61bb3a984c6b58f5c9e
subject:     Merge pull request #59 (AIM-4)
parents:     7fac2a9e9d53896151a725c0c3e831b24fbc23f7
             acec659508748925b6dcb24737feed429db1c3e3
```

The baseline was refreshed from `origin/main` again after implementation and
remained byte-exact. The following authority and current-state sources were
read in full before design:

1. `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`;
2. `architecture/practice/PRACTICE_AUTHORING_IMPORT_MODERNIZATION_CONTRACT.md`,
   especially Section 6;
3. `decisions/0012-practice-authoring-import-modernization-boundaries.md`;
4. `PRACTICE_AIM4_REVIEW_APPLY_PREVIEW_LIVE_CHANGE_LOG.md`;
5. the existing global Admin AI profile/client/prompt/settings flows; and
6. every existing Practice structured-generation, R/L explanation, Writing,
   Speaking evaluator/transcription, lecturer prompt STT/TTS and PDF provider
   seam touched by this implementation.

Immediately before allocating SQL, the refreshed Flyway inventory was unique
and continuous through V83 and V84 was absent. AIM-5 allocated exactly
`V84__practice_ai_control_plane.sql`. The final inventory is 84 unique files,
continuous V1-V84, zero gaps/duplicates; the next free version is V85.

## 2. Locked implementation slices

| Slice | State | Evidence |
|---|---|---|
| Exact purpose authority | `COMPLETE` | exactly six enum values and DB-checked purpose codes; purpose PK makes a second binding impossible |
| Provider profile control plane | `COMPLETE` | Admin CRUD/toggle, immutable profile code, masked ordinary form, explicit no-store reveal, optimistic revision, bound-delete refusal |
| Purpose binding control plane | `COMPLETE` | exact profile/model/dialect/capability/limits/retention authority, one row per purpose, optimistic revision and explicit enable/disable |
| Practice data plane | `COMPLETE` | Practice-owned resolver, immutable redacted snapshot, audit, allowlisted transport and purpose-specific structured/STT/TTS adapters |
| Capability verification | `COMPLETE` | one bounded project-owned fixture per exact purpose, explicit Admin-only invocation, revision-bound PASS/FAIL/CANCELLED audit |
| Fail-closed behavior | `COMPLETE` | missing, disabled, incompatible, invalid and changed bindings stop before transport; retry reuses only one resolved binding |
| Auth and secret boundary | `COMPLETE` | existing `/admin/**` `ROLE_ADMIN` boundary plus `PERM_system.ai`; no secret/prompt/response body in AIM-5 logs or execution audit |
| Migration and disposable DB proof | `COMPLETE` | two independent fresh V1-V84 migrations plus a third disposable regression catalog; no clean/reset/repair/destructive action |

## 3. Control-plane and schema ledger

Added the Practice-owned `features/practice/ai/controlplane` package with:

- `PracticeAiPurpose`, containing only:
  `PRACTICE_PDF_AUTHORING`, `PRACTICE_RL_EXPLANATION`,
  `PRACTICE_WRITING_EVALUATION`, `PRACTICE_SPEAKING_EVALUATION`,
  `PRACTICE_SPEAKING_STT` and `PRACTICE_SPEAKING_TTS`;
- strict exact-field codecs for capability and limit JSON, purpose-specific
  capability requirements and SHA-256 snapshot digests;
- provider profile and purpose binding JPA entities/repositories with JPA
  optimistic revisions;
- a resolver that accepts only `OPENAI_COMPATIBLE` plus
  `OPENAI_COMPATIBLE_V1`, validates the exact purpose capability and fails
  closed on missing/disabled/incompatible authority;
- an immutable execution snapshot containing purpose, binding/profile
  revisions, redacted provider identity, model, dialect, capability/limit
  digests and retention identity;
- persisted execution audits created before each production provider call and
  completed as `SUCCESS`, `FAILED` or `CANCELLED` with bounded error codes;
- an allowlisted Practice transport exposing only `/chat/completions`,
  `/audio/transcriptions` and `/audio/speech`, with binding-owned timeouts,
  retries/size limits and credentials; and
- capability-test run persistence and the bounded, explicitly invoked
  structured/STT/TTS fixture probe. It has no startup trigger.

Migration V84 adds only:

1. `practice_ai_provider_profiles`;
2. `practice_ai_purpose_bindings`;
3. `practice_ai_capability_test_runs`; and
4. `practice_ai_execution_audits`.

The purpose binding table uses `purpose_code` as its primary key. The
execution table intentionally stores no endpoint or credential column. The
migration contains no update/delete/drop, storage profile, bucket, R2/S3 or
other later-phase schema.

## 4. Admin control-plane ledger

- Added `/admin/settings/practice-ai` pages and controller/service DTOs for
  profile and exact-purpose binding management.
- The controller has class-level
  `@PreAuthorize("hasAuthority('PERM_system.ai')")`; the existing security
  chain independently keeps `/admin/**` behind `ROLE_ADMIN`.
- The list/edit flows expose revisions and recent purpose test results.
- Ordinary profile reads return only the mask sentinel. Secret reveal is a
  separate authorized endpoint with `Cache-Control: no-store`; logs contain
  only profile code/purpose and actor ID.
- Updating with the mask retains the current credential. Stale profile or
  binding revisions fail. A profile cannot be deleted while any purpose row
  references it.
- Capability tests are explicit POST actions with CSRF and run only against
  the selected exact purpose revision. Generic connection ping is not a
  readiness signal.

## 5. Practice data-plane integration ledger

- Replaced the environment-owned primary structured adapter with
  `PracticeControlPlaneStructuredGenerationAdapter`. PDF authoring, R/L
  explanation, Writing evaluation and transcript-only Speaking evaluation now
  construct requests with an exact purpose and resolve exactly one DB binding.
- The shared logical capability name is
  `STRICT_STRUCTURED_TEXT_VISION`; PDF continues to use its strict authoring
  schema and never an evaluation/scoring schema.
- The structured adapter creates the immutable audit snapshot before
  transport, checks the binding is still current, bounds request/response
  bytes, decodes strict JSON and retries only the already resolved binding.
- Learner Speaking STT production wiring now resolves
  `PRACTICE_SPEAKING_STT`, records operation `LEARNER_RESPONSE_STT`, validates
  the audio bound and uses only the central Practice transport. The old
  package-private constructor survives solely for fake unit contracts; no
  direct production HTTP transport remains in the client.
- Lecturer prompt STT/TTS production wiring resolves the same exact STT/TTS
  purposes while retaining separate operation/data-class/retention audit
  identities. Prompt fingerprints and work snapshots now include binding and
  profile revisions so stale authority cannot be silently reused.
- Speaking evaluation availability and contract identity use both the STT and
  evaluator purpose identities. The evaluator remains transcript-only; no
  learner audio is sent to the evaluator.
- The existing PDF workspace call seam was changed only from direct
  `OpenAiProperties`/`RestClient` use to `PRACTICE_PDF_AUTHORING`. No AIM-7
  Basic PDF generator route, candidate assembler, page or UI was added.
- The previous environment `app.practice.ai.openai-primary.*` capability
  authority and its disabled/direct adapters were removed. Remaining legacy
  property objects supply bounded workflow/policy metadata or fake-only test
  compatibility and are not production provider-selection authority.

## 6. Test and verification ledger

All Java commands used Temurin/OpenJDK 17 (`17.0.19`). No test used a real
provider, storage service, STT or TTS endpoint.

| Check | Result |
|---|---|
| Java 17 compilation | `GREEN` |
| AIM-5 focused contracts | `GREEN` — 33/33, including exact purposes, resolver, admin service, bounded fixtures, immutable binding/retry, learner STT and lecturer STT/TTS production constructors |
| Authorization Spring context | `GREEN` — 4/4; anonymous redirect, missing permission 403, missing Admin role 403, exact principal success and missing binding fail-closed |
| Fresh disposable persistence integration | `GREEN` — 1/1 with real MySQL/JPA/Flyway and fake capability/data-plane transport |
| Broad Practice regression selector | `GREEN` — 691 tests, 0 failures, 0 errors, 1 intentionally skipped DB-only test across 88 reports |
| Migration inventory | `GREEN` — 84 unique continuous files/rows, latest V84, zero failed rows, next V85 |
| JavaScript syntax | `GREEN` — `admin-settings-practice-ai.js` |
| `git diff --check` | `GREEN` |
| Auth/fail-closed/no-go scans | `GREEN` — zero shared `AiClient`/global repository/fallback import; zero direct runtime provider transport outside the Practice transport; zero secret/body logging; zero locked later-phase file diff |

The broad selector was:

```text
PracticeAi*Test,*ReadingListening*Test,*Writing*Test,*Speaking*Test,*PracticePdf*Test
```

The regression catalog finished with all four AIM-5 tables empty and 84/84
successful Flyway history rows. This is independent evidence that context
startup/default tests cannot call a provider.

One final authorization command initially supplied the normal `DB_*`
namespace. The test-only database guard rejected it before any connection with
an unresolved `TEST_DB_URL`. The command was rerun with explicit
`TEST_DB_*` values against the already-created disposable regression catalog
and passed 4/4. This safe pre-connection failure changed no database or code.

## 7. Disposable database evidence

No pre-existing database or container was reset, repaired, deleted or cleaned.
Only these AIM-5 disposable MySQL 8.0 containers/catalogs were used, and all
three are intentionally left running for coordinator inspection:

```text
ksh-aim5-control-20260802-1320-mysql
  127.0.0.1:64887 -> 3306
  ksh_test_aim5_control_20260802_1320

ksh-aim5-final-20260802-1321-mysql
  127.0.0.1:64915 -> 3306
  ksh_test_aim5_final_20260802_1321

ksh-aim5-regression-20260802-1322-mysql
  127.0.0.1:64931 -> 3306
  ksh_test_aim5_regression_20260802_1322
```

The first fresh catalog migrated V1-V84 successfully. Hibernate validation
then found a `CHAR(64)` versus entity `VARCHAR(64)` mismatch on digest fields.
Only the JPA mapping was corrected with `columnDefinition = "CHAR(64)"`; the
catalog and migration were not reset, edited, repaired or deleted. Validation
then passed on that same catalog.

The second, independently fresh catalog is the authoritative clean proof:

1. Flyway applied all 84 migrations from V1 through V84;
2. Hibernate schema validation passed;
3. the persistence integration test passed with a fake capability probe and
   fake execution path;
4. the test persisted all six unique purpose rows, then deliberately disabled
   R/L to prove fail-closed behavior, leaving 6 configured / 6 distinct / 5
   enabled;
5. capability tests contain one `PASS`; execution audits contain one
   `SUCCESS`; and
6. the execution-audit table has zero secret/credential/API-key columns.

The third catalog ran the broad regression and authorization contexts. After
the final run its control-plane counts remained profiles/bindings/tests/audits
`0/0/0/0`, with Flyway `84/84`, latest V84.

## 8. No-go ledger

```text
AIM-6 storage profiles / R2 implementation:        0
AIM-7 Basic PDF generator route/UI/candidate work: 0
Quick Excel / AIM-4 changes outside AI seam:       0
Practice player/scoring changes:                    0
Practice publisher change:                         0
shared AiClient/global provider fallback:           0
implicit second-provider fallback:                  0
real provider/storage/STT/TTS calls in tests:       0
Speaking direct-audio/acoustic grading:             0
secret/prompt/response-body audit or log fields:    0
existing DB reset/clean/repair/delete:               0
task/worktree/branch/commit/push/PR:                 0
```

`SpeakingPromptPublicationService.java` is byte-identical to `origin/main`.
No Practice player, scoring, publisher, Quick Excel, AIM-4 candidate or storage
implementation file is changed.

## 9. Final handoff boundary

- `HEAD` and refreshed `origin/main` remain the exact AIM-4 merge commit
  `98f443470d1b505ed951c61bb3a984c6b58f5c9e`; only unstaged AIM-5 worktree
  changes exist.
- No task, worktree, branch, commit, push or pull request was created.
- No approval, migration identity conflict, real-provider requirement,
  destructive operation or unresolved product decision was encountered.
- Remaining work is coordinator-owned diff/test/security audit and then, only
  if accepted, coordinator-owned commit/push/PR packaging.

## 10. Coordinator acceptance audit

The coordinator independently accepted the handoff on 2026-08-02 after:

- rechecking the exact `origin/main` baseline, migration inventory, admin
  authorization boundary, six-purpose binding contract, immutable revision
  snapshots, fail-closed resolver behavior, redacted audit schema, allowlisted
  transport paths and all AIM-6+ no-go boundaries;
- rerunning the broad 691-test Practice selector against the disposable
  regression catalog with zero failures and zero errors;
- rechecking JavaScript syntax and `git diff --check`;
- querying the authoritative final catalog for Flyway `84/84`, six distinct
  bindings, one passing capability test and one successful execution audit;
  and
- creating a separate coordinator-only disposable catalog
  `ksh_test_aim5_coord_20260802_1409`, applying V1-V84 from empty, validating
  the schema and passing `PracticeAiControlPlanePersistenceIntegrationTest`
  without any real provider, storage, STT or TTS call.

The acceptance audit found no blocker and authorized coordinator-owned logical
commits, push and pull-request merge. The coordinator-only disposable catalog
is intentionally left available for inspection; no existing catalog or
container was reset, cleaned, repaired or deleted.
