# ULP → KSH Integration Incident and Audit Checklist

> Living control document for the selective integration of ULP commit
> `32d394c5f6d0818955455bc01f20633b66d594b5` into KoreanStudyHub (KSH).
> This document records evidence, immutable decisions, implementation work,
> verification gates, commit ownership, and PR handoff. It is not evidence that
> an unchecked implementation has passed.

## 1. Document control

| Field | Value |
|---|---|
| Audit status | PR #33 merged to `main` at `8b80e498`; the current non-Practice follow-up group is remediated locally, with database-backed concurrency/full-suite verification still explicitly open below |
| KSH audit baseline | `2549438c1a327b6932dc78d5284d7feaf5daf628` |
| Integration merge commit | `27466f69a6f94f239f05a44d22b26616a01a8fe0` |
| Working branch observed | `codex/e2e-assignment-messaging-fixes` |
| ULP reference | `https://github.com/dikhamchua/ulp/tree/32d394c5f6d0818955455bc01f20633b66d594b5` |
| ULP local snapshot used | `C:\Users\Admin\AppData\Local\Temp\ksh-ulp-32d394c5-20260729\ulp-32d394c5f6d0818955455bc01f20633b66d594b5` |
| KSH root | `D:\Downloads\ksh` |
| Comparison method | Path inventory, normalized namespace/branding comparison, semantic review, call-site tracing, and test-contract review |
| Phase-3 verification | 15/15 focused database-free tests passed; `node --check` passed for `import-excel.js`; no full or DB-backed suite was run |
| Current follow-up verification | `mvnw.cmd -q -DskipTests compile` and `git diff --check` passed; tests were not run per project-owner request, and real-database concurrency remains unchecked |
| Last updated | 2026-07-29, Asia/Bangkok |
| Primary constraint | Preserve the existing `/practice` foundation, AI configuration, and storage configuration |

The working tree is shared by parallel agents. File presence or a local diff
means “implementation in progress,” not “verified.” Re-read `git status`,
`git diff`, and this document before taking ownership of any item.

### Follow-up remediation scope freeze

- [x] The development password value `123456` is explicitly accepted by the
  project owner for the current non-production environment; this branch does
  not change or report it as a production-secret remediation.
- [x] Migration-chain, empty-schema, Flyway/MySQL-version, and migration CI work
  is deferred. This branch neither adds nor edits a migration.
- [x] `/practice` runtime code, AI configuration, storage configuration,
  schema, assets, and tests remain frozen.
- [x] `TEST-ISO-001` belongs to another not-yet-merged branch. This follow-up
  does not duplicate it and does not run database-backed/full-suite tests
  against the developer schema.
- [x] Verification on this branch is limited to focused, database-free unit or
  source-contract tests plus static diff/compile checks.

### Checkbox semantics

- `[x]` means the audit fact or architecture decision is conclusive.
- `[ ]` means implementation, verification, review, commit, or merge is still
  outstanding.
- An implementation must not be changed to `[x]` merely because files exist.
  It requires the verification evidence named under that issue.
- Each agent must add actual commit hashes and the PR URL when available.

### Severity

| Severity | Meaning |
|---|---|
| Critical | Can break Practice, expose private data, corrupt schema/history, or cause material data loss |
| High | Functional, authorization, concurrency, delivery, or transaction correctness regression |
| Medium | User-visible reliability, accessibility, maintainability, or bounded-resource issue |
| Low | Documentation, naming, or optional progressive enhancement |

## 2. Executive decision register

| Stable ID | Severity | Decision or finding | Audit decision | Implementation |
|---|---:|---|---|---|
| SCOPE-001 | High | The “202 changed files” scope is reconciled after namespace normalization and exclusion of five base/meta files | [x] | N/A |
| BRAND-001 | High | All imported ULP symbols, routes, text, sources, and thread names must become KSH equivalents | [x] | [x] |
| ROLE-001 | Critical | KSH `LEADER` and `/leader/**` are canonical; ULP `HEAD` and `/head/**` must not return | [x] | [x] |
| PRACTICE-001 | Critical | Strict Practice runtime/config/storage freeze applies throughout this integration | [x] | [x] |
| SEC-001 | Critical | Reject ULP’s public `"/uploads/**"` authorization; retain KSH fail-closed upload policy | [x] | [x] |
| MIG-001 | Critical | Reject wholesale ULP migration copying and preserve KSH Flyway history | [x] | [x] |
| CONC-001 | Critical | Retain KSH class-row/invite locking; ULP class approval code is a downgrade | [x] | [x] |
| FORM-001 | High | ULP contains a valid deferred form re-submit fix missing from the audit baseline | [x] | [x] |
| FLASH-001 | Medium | `notifications.js` must be the sole first-party flash-to-toast drainer | [x] | [x] |
| FLASH-002 | Medium | Notifications index loads `notifications.js` twice through two template paths | [x] | [x] |
| AI-TRANSPORT-001 | High | Selectively port non-streaming JSON and embedded-provider-error handling | [x] | [x] |
| AI-BOUNDS-001 | High | Provider success and error bodies require byte bounds | [x] | [x] |
| AIQ-001 | High | ULP AI question generation is additive but must be hardened before acceptance | [x] | [x] |
| AIQ-SESSION-001 | Critical | Reject ULP’s in-memory non-atomic draft session store; use durable atomic consumption | [x] | [x] |
| MAIL-001 | High | SMTP must not run synchronously inside notification transactions/fan-out loops | [x] | [x] |
| MAIL-ARCH-001 | Critical | Reject ULP’s in-memory queue for KSH; durable database outbox is the selected design | [x] | [x] |
| UX-TABS-001 | Medium | ULP’s loading lifecycle for exam AJAX tabs is safe to adapt selectively | [x] | [x] |
| UX-TABS-002 | Medium | Generic ULP detail-tab redesign is a separate integration surface, not a bulk-copy candidate | [x] | [x] |
| CONST-001 | Medium | Keep KSH constants and add enforcement; do not replace `IConstant` with ULP’s version | [x] | [x] |
| TEST-ISO-001 | High | Database-backed tests need disposable database and upload-storage isolation | [x] | [x] DB/profile + public uploads; [ ] V1 cross-schema migration review deferred |
| CONTRIBUTOR-ID-001 | Low | Historic KSH commits are split across three verified GitHub identities | [x] | [x] Future commits pinned; [x] historic transfer declined |
| CONTRIBUTOR-NAMDK-LINEAGE-001 | Low | Retired `feature/profile` commits need attribution without importing superseded code | [x] | [x] History-only `ours` merge; tree unchanged |
| UX-DIRTY-001 | High | AJAX detail-tab navigation can discard unsaved form changes without warning | [x] | [x] Contract verified; browser UAT open |
| CLASS-NAV-001 | Medium | Creating a test from a class loses class selection and returns to the global test list | [x] | [x] |
| QB-IMPORT-001 | High | Question-bank Excel preview omits CSRF and returns 403 | [x] | [x] |
| QB-TAXONOMY-001 | High | Admin course categories and department question-bank categories are disconnected taxonomies | [x] | [x] Compatibility bridge; [ ] schema consolidation |
| DB-INVENTORY-001 | Medium | The V60 developer schema contains 104 base tables and one view | [x] | [x] Inventory recorded; [ ] broader rationalization |
| TEST-CONC-001 | High | Concurrent submit/heartbeat requests can both mutate one test attempt | [x] | [x] |
| ASSIGN-CONC-001 | High | Concurrent assignment submit/grade can reopen graded work or race first insert | [x] | [x] |
| AUTH-RESET-001 | High | Password-reset bearer tokens were stored/logged in reusable form and consumed without a lock | [x] | [x] |
| IMPORT-REPLAY-001 | High | Concurrent confirmation could consume one import preview twice | [x] | [x] |
| AUTH-RESET-002 | High | Forgot-password requests were unthrottled and terminal tokens had no retention | [x] | [x] |
| MSG-CONC-001 | Medium | Concurrent conversation creation could surface a unique-constraint failure | [x] | [x] |
| ASSIGN-AUTH-001 | Medium | Learner assignment routes accepted any authenticated role with a stale enrollment | [x] | [x] |
| UX-TABS-003 | Medium | Clicking the exact active exam tab prompted and reloaded an unsaved draft | [x] | [x] |
| AUTH-LOGIN-001 | High | Form login allowed unlimited password guesses | [x] | [x] |
| TEST-AUTH-001 | High | Learner test routes accepted elevated roles that retained an enrollment | [x] | [x] |
| PUBLIC-VIEW-001 | Medium | Public attachment responses could remain in caches after bearer-token expiry | [x] | [x] |
| FLASH-REVIEW-001 | High | Concurrent flashcard ratings could lose an SM-2 transition or collide on first insert | [x] | [x] |
| LESSON-ORDER-001 | Medium | Concurrent section/lesson reorder could collide during temporary ordering | [x] | [x] |
| IMPORT-STALE-001 | High | A stale student-import upload response can replace a newer preview/session | [x] | [x] |
| DEPT-LEADER-CONC-001 | High | Concurrent leader reassignment can desynchronize department pointers and user role/department | [x] One user leads at most one department | [x] Remediated; real-DB concurrency unchecked |
| PROGRESS-TOGGLE-001 | Medium | Concurrent lesson-completion toggles can collide or lose parity | [x] Two serialized toggles cancel | [x] Focused verification |
| MSG-REALTIME-001 | Medium | Realtime conversation bubbles use the truncated sidebar snippet | [x] | [x] |
| MSG-READ-001 | Medium | A message received in an open conversation remains unread server-side | [x] | [x] |
| COMMENT-DUPE-001 | Medium | Double-clicking root lesson-comment submit sends duplicate POSTs | [x] | [x] |
| SECTION-DELETE-STATE-001 | Medium | Deleting the selected section leaves its lesson pane and client selection stale | [x] | [x] |
| PUBLIC-VIEW-TOKEN-001 | Medium | Public attachment bearer tokens remain reusable plaintext database values | [x] Replacement semantics selected | [x] Remediated; focused tests not run |
| MSG-RELATION-REVOKE-001 | Medium | Existing conversations survive enrollment/role revocation by deliberate D2 policy | [x] Superseded by global-directory policy | [x] Deliberate behavior |
| LEADER-SCOPE-001 | Critical | Legacy class policies treat every LEADER as a global ADMIN across departments | [x] | [x] Focused verification |
| STORAGE-TX-001 | Critical | DB rollback cannot compensate object-storage writes/deletes performed inside the transaction | [x] | [x] Focused verification |
| CLASS-CODE-RETRY-001 | High | Class/invite collision retries continue inside a transaction poisoned by `saveAndFlush` | [x] | [x] |
| PUBLIC-VIEW-CONC-001 | High | Concurrent public-token creation can leave multiple live rows and break Optional lookup | [x] | [x] |
| LIBRARY-BIND-DELETE-001 | High | Library deletion can race a lesson bind and delete a newly referenced blob | [x] | [x] |
| COMMENT-BULK-TX-001 | High | Bulk moderation self-invocation bypasses the advertised per-item transaction boundary | [x] | [x] |
| COMMENT-MUTATION-CONC-001 | Medium | Concurrent comment mutations can duplicate audits or overwrite state | [x] | [x] |
| QB-IMPORT-STALE-002 | High | A stale Question Bank preview can replace the current workbook session | [x] | [x] |
| PROGRESS-STALE-001 | Medium | Delayed progress detail can render student A beneath student B's selection | [x] | [x] |
| LIBRARY-PICKER-STALE-001 | Medium | A closed library request can populate a later picker with the wrong asset kind | [x] | [x] |
| CLONE-WIZARD-STALE-001 | Medium | Delayed section results can mismatch the clone wizard's selected class | [x] | [x] |
| TEST-MONITOR-STALE-001 | Medium | Overlapping monitor polls can roll the live test UI backward | [x] | [x] |
| PERMISSION-OVERRIDE-CONC-001 | High | Permission override races and pre-commit cache eviction can retain stale authorization | [x] | [x] Focused verification |
| LESSON-APPEND-CONC-001 | High | Concurrent section/lesson/template append can collide on sibling display order | [x] | [x] Focused verification |
| LIBRARY-ATTACH-WIZARD-STALE-001 | High | Stale target/preflight responses can bind library content using mismatched state | [x] | [x] |
| FLASHCARD-DECK-DUPE-001 | Medium | Double submit can persist a flashcard deck twice before native resubmit | [x] | [x] |
| LESSON-FORM-DUPE-001 | High | Repeated lesson submit can upload or bind media more than once | [x] | [x] |
| COMMENT-DELETE-SCOPE-001 | Medium | Department LEADER/ADMIN moderators could hide but not delete comments | [x] | [x] |
| TEST-LEADER-SCOPE-001 | High | Test-management services do not honor department-scoped LEADER/global ADMIN class access | [x] | [x] Remediated; focused tests not run |
| EXAM-IMAGE-ORPHAN-001 | Medium | Unclaimed exam image uploads have no owner-bound staging lifecycle or cleanup | [x] | [x] Remediated; focused tests not run |
| GENERIC-TOGGLE-CONC-001 | Medium | Several admin parity toggles read and write state without serialization | [x] | [x] Remediated; real-DB concurrency unchecked |
| AI-PROVIDER-ORDER-CONC-001 | Medium | Concurrent AI provider creation can collide on global display order | [x] | [x] Remediated; real-DB concurrency unchecked |

## 3. Scope reconciliation and source inventory

### SCOPE-001 — normalized semantic-diff scope

**Audit decision**

- [x] Inventoried 760 ULP source files and 1,309 KSH source files.
- [x] Found 59 ULP-only paths and 701 common paths.
- [x] Normalized `com/ulp → com/ksh`, `Ulp → Ksh`,
  `ULP → KSH`, and `ulp → ksh`.
- [x] Of the 701 common paths, 499 are normalized-identical and 202 contain a
  semantic diff requiring disposition.
- [x] Determined that most of the 202 differences are KSH evolution, branding,
  role naming, comments, responsive UI, or Practice integration—not missing ULP
  behavior.

**ULP-only production surfaces found**

- [x] Nine AI question-generation Java classes under
  `ULP:src/main/java/com/ulp/features/ai/questiongen/`.
- [x] Five in-memory mail-job Java classes under
  `ULP:src/main/java/com/ulp/features/mail/job/`.
- [x] `ULP:src/main/resources/static/js/detail-tabs.js`.
- [x] `ULP:src/main/resources/static/js/test-lecturer-ai-questions.js`.
- [x] Regression tests for deferred re-submit, single-owner flash drain, AI
  parser/extractor behavior, embedded provider errors, and the ULP mail queue.

**Disposition**

- [x] Selective semantic port only.
- [x] No directory-level or whole-file overwrite from ULP without an issue ID.
- [ ] Record every imported file or hunk in the implementation ledger.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

## 4. Non-negotiable guardrails

### BRAND-001 — ULP-to-KSH namespace and product parity

**Evidence**

- ULP uses `com.ulp`, `Ulp*`, `ULP`, and names such as
  `"ulp-mail-job-worker"`.
- KSH uses `com.ksh`, `Ksh*`, `KSH`, and its own constants, templates, sources,
  thread names, subjects, and operational labels.

**Decision**

- [x] Package names must be `com.ksh`.
- [x] Types such as `UlpUserDetails` must map to existing KSH types rather than
  creating parallel duplicates.
- [x] UI labels, email subjects, log sources, CSS/JS globals, worker names, rule
  documentation, and tests must use KSH naming.

**Remediation and verification**

- [x] Run a production/test source scan for `com.ulp`, `Ulp`, `ULP`, and
  ULP-specific URLs.
- [x] Review every match; do not hide a real branding leak with a blanket
  replacement or broad exclusion.
- [x] Confirm imported Java packages match their KSH directory.
- [x] Preserve the byte-identical, already-published V54 migration even though
  its historical comment says ULP; `KshBrandingContractTest` pins SHA-256
  `f03dfecb7e6c9e4ea4ec2b66b893d249ea988d38e158f614b85e49a20ac828ba`.

**Tracking**

- Owner: Codex root
- Verification command/output: production/test scan found no live ULP symbols;
  only structural test strings and the immutable V54 exception remain.
- Commit(s):
- PR:

### ROLE-001 — canonical LEADER role and route

**Evidence**

- `KSH:src/main/java/com/ksh/config/SecurityConfig.java` authorizes
  `Roles.LEADER` and `/leader/**`.
- ULP authorizes `Roles.HEAD` and `/head/**`.
- `KSH:src/main/resources/db/migration/V48__standardize_subject_leader_role.sql`
  records the KSH role migration.

**Decision**

- [x] `LEADER` is canonical in KSH.
- [x] Reject ULP `HEAD` role constants, authority expressions, controller
  routes, templates, and documentation.
- [x] Existing Head-named ULP classes must map to KSH Leader equivalents when
  functionality is already present.

**Remediation and verification**

- [x] Scan `src/main` and `src/test` for `Roles.HEAD`, `ROLE_HEAD`,
  `/head/`, `PREAUTH_*HEAD*`, and user-facing “Head” role labels.
- [x] Run `PermissionAuthorityFailSafeTest` as part of the 2,439-test full suite.
- [x] Run affected Leader controller/service integration tests as part of the
  2,439-test full suite.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### PRACTICE-001 — strict Practice freeze

**Protected behavior**

- `/practice/**`, `/practice/manage/**`, `/practice/progress`, and
  `/practice/profile`.
- Practice AI clients, prompts, evaluation policies, caches, readiness rules,
  metrics, and provider configuration.
- Practice speaking/transcription/storage, private audio, PDF storage, material
  storage, cleanup workers, and storage-readiness policy.
- Practice schema, immutable versions, attempts, authoring, media, and
  performance migrations.
- Practice templates, JavaScript, CSS, tests, and published-version contracts.

**Protected evidence paths**

- `KSH:src/main/java/com/ksh/features/practice/**`
- `KSH:src/main/resources/templates/practice/**`
- `KSH:src/main/resources/static/js/practice/**`
- `KSH:src/main/resources/static/css/practice/**`
- `KSH:src/test/java/com/ksh/features/practice/**`
- `KSH:src/main/resources/db/migration/V25__practice_hub.sql`
- `KSH:src/main/resources/db/migration/V28__practice_attempt_optimistic_lock.sql`
- `KSH:src/main/resources/db/migration/V29__practice_question_writing_task_type.sql`
- `KSH:src/main/resources/db/migration/V30__practice_speaking_media.sql`
- `KSH:src/main/resources/db/migration/V31__practice_speaking_media_cleanup.sql`
- `KSH:src/main/resources/db/migration/V32__practice_attempt_discard_tombstone.sql`
- `KSH:src/main/resources/db/migration/V33__practice_immutable_versions.sql`
- `KSH:src/main/resources/db/migration/V34__practice_single_scope_final.sql`
- `KSH:src/main/resources/db/migration/V35__practice_catalog_performance_indexes.sql`
- `KSH:src/main/resources/db/migration/V36__practice_section_delivery.sql`
- `KSH:src/main/resources/db/migration/V44__practice_seed_listening_check_audio.sql`
- `KSH:src/main/resources/db/migration/V55__practice_speaking_prompt_authoring_foundation.sql`
- `KSH:src/main/resources/db/migration/V56__practice_phase13g_catalog_progress_indexes.sql`

**Decision**

- [x] No ULP code may replace or reconfigure Practice AI or storage.
- [x] No Practice migration may be renamed, reused, reordered, or edited.
- [x] Cross-cutting changes to `SecurityConfig`, `application.properties`,
  shared AI, shared upload, shared templates, or shared JavaScript require
  explicit Practice regression evidence.
- [x] Any unavoidable Practice change requires a new stable issue ID, owner
  rationale, and an isolated commit. `PRACTICE-PORT-001` is the sole exception:
  a one-line Windows path-normalization fix in a source-level test, with no
  runtime/configuration/storage impact.

**Freeze checklist**

- [x] Before each commit, inspect `git diff --name-only` for protected runtime
  paths; the commit ledger keeps the test-only exception isolated.
- [x] Compare Practice-relevant `application.properties` keys before and after.
  The integration changed none; the existing local port/database/base-URL diff
  belongs to the user and is intentionally left uncommitted.
- [x] Confirm Practice security configuration has no diff from `origin/main`.
- [x] Run the full Practice unit/integration suite.
- [ ] Manually smoke-test learner Practice catalog, player, progress, result,
  and lecturer Practice manage flows.
- [ ] Manually verify Practice speaking upload/playback and existing AI flows use
  their original providers and storage.
- [x] Record before/after evidence in this report for the PR.

**Tracking**

- Freeze owner: Codex root
- Exception issue, if any: `PRACTICE-PORT-001` (test-only Windows separator fix)
- Practice production diff: empty for Java, templates, JavaScript, and CSS
- Baseline SHA-1 inventory:
  - Java: `1375f09a01fe2fe3e0d0c00dcdb2b578dfd745e6`
  - templates: `14f25d9f88154f36f6cd6c21ddf0bae63d99ffd2`
  - JavaScript: `b06a101988b6b9c43fde65adff65c5364a68efef`
  - CSS: `6e3548945292281edcb011926fabffd4bc5f7f5e`
- Practice test evidence: `mvn "-Dtest=com.ksh.features.practice.**.*Test" test`
  — 1,346 tests, 0 failures, 0 errors, 2 skipped.
- Manual UAT evidence: pending; do not treat automated coverage as manual sign-off.
- Commit(s):
- PR:

## 5. Security, concurrency, and migration rejection list

### SEC-001 — public upload fail-closed policy

**Evidence**

- ULP `SecurityConfig` contains
  `.requestMatchers("/uploads/**").permitAll()`.
- KSH `SecurityConfig` permits only `/uploads/avatars/**` and
  `/uploads/exams/**`, then denies the remaining `/uploads/**`.
- `KSH:src/main/java/com/ksh/features/upload/PublicUploadsController.java`
  serves exactly `avatars/{file}` and `exams/{file}` and rejects nested,
  traversal, and unknown paths.

**Decision**

- [x] Reject ULP `SecurityConfig`.
- [x] Retain KSH’s ordered allowlist followed by deny-all.
- [x] Retain Practice-specific authorization matchers.

**Verification**

- [x] Run `PublicUploadsControllerTest` — 12 tests, 0 failures/errors.
- [ ] Verify avatar and exam public objects remain readable.
- [ ] Verify private lesson, Practice, nested, traversal, backslash, and unknown
  upload paths are denied or return 404.
- [ ] Review matcher order after all merges.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### CONC-001 — class approval locking

**Evidence**

- `KSH:src/main/java/com/ksh/features/classes/service/JoinClassService.java`
  acquires a class-row lock before enrollment/invite capacity checks, then locks
  the invite and rechecks limits.
- ULP checks capacity before obtaining equivalent serialization and can admit
  competing approvals into the final slot.

**Decision**

- [x] Keep KSH `JoinClassService`, repositories, and validator behavior.
- [x] Do not overwrite these files with ULP versions.

**Verification**

- [x] Run `JoinClassServiceTest` — 24 tests, 0 failures/errors.
- [ ] Run `JoinClassConcurrencyTest`.
- [ ] Confirm class capacity and invite `max_uses` cannot be exceeded under
  concurrent approval.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### MIG-001 — Flyway history and version map

**Decision**

- [x] Never copy ULP migration filenames `V25` through `V41` directly.
- [x] KSH versions `V25` through `V37` are reserved by Practice and adjacent
  KSH cache/lifecycle work.
- [x] Existing KSH migrations are immutable.
- [x] Every new integration migration uses the next free KSH version at the
  time it is finalized.

**Established rebase map**

| ULP migration | KSH equivalent | Decision |
|---|---|---|
| `V25__reset_dev_passwords_to_123456.sql` | `V43__reset_demo_passwords_to_123456.sql` | [x] Already rebased; do not duplicate |
| `V26__test_media.sql` | `V38__test_media.sql` | [x] Already rebased |
| `V27__exam_question_content_mediumtext.sql` | `V39__exam_question_content_mediumtext.sql` | [x] Already rebased |
| `V28__classes_department_id.sql` | `V40__classes_department_id.sql` | [x] Already rebased |
| `V29__department_activities.sql` | `V41__department_activities.sql` | [x] Already rebased |
| `V30__library_assets.sql` | `V42__library_assets.sql` | [x] Already rebased |
| `V31__lesson_templates.sql` | `V45__lesson_templates.sql` | [x] Already rebased |
| `V32__department_question_bank.sql` | `V46__department_question_bank.sql` | [x] Already rebased |
| `V35__question_bank_status_before_archive.sql` | `V47__question_bank_status_before_archive.sql` | [x] Already rebased |
| `V36__rbac_permissions_backfill.sql` | `V49__rbac_permissions_backfill.sql` | [x] Already rebased |
| `V37__ai_providers.sql` | `V50__admin_ai_providers.sql` | [x] Already rebased |
| `V38__ai_request_logs.sql` | `V51__admin_ai_request_logs.sql` | [x] Already rebased |
| `V39__storage_r2_settings.sql` | `V52__admin_object_storage.sql` | [x] Already rebased |
| `V40__ai_system_prompts.sql` | `V54__ai_system_prompts.sql` | [x] Already rebased |
| `V41__seed_ai_question_generator_prompt.sql` | KSH `V57__seed_ai_question_generator_prompt.sql` plus conditional hardening in V60 | [x] UTF-8/idempotent seed retained without rewriting an applied migration |

**KSH-only integration migrations**

- [x] `V57__seed_ai_question_generator_prompt.sql`: real UTF-8, idempotent
  insert-by-name, and preserved byte content after local application.
- [x] `V58__durable_ai_question_draft_sessions.sql`: status/expiry indexes and
  pessimistic atomic consumption validated. Actor/test ids intentionally have
  no foreign keys: they are short-lived authorization-binding snapshots, and
  deleting a user/test must not be blocked by an expired preview.
- [x] `V59__durable_mail_outbox.sql`: lease/due indexes, status/attempt checks,
  notification uniqueness, `ON DELETE SET NULL`, rollback, and two-worker
  claim behavior validated. Retention/metrics remain separate open work.
- [x] `V60__harden_ai_question_generator_prompt.sql`: conditionally upgrades
  only the exact original seed, preserving administrator-customized prompts.
- [x] Confirm no parallel agent allocated the same versions.
- [ ] Run Flyway from an empty database.
- [x] Run/validate Flyway on the existing local KSH schema through V60.
- [x] Confirm no existing migration diff/checksum change; V57/V58 were not
  rewritten after local application and later prompt hardening uses V60.

**Tracking**

- Owner: Codex root
- Empty-schema evidence:
- Upgrade-schema evidence: MySQL `ksh_dba` reached V60 and subsequent application
  starts reported “Schema is up to date.” This is local upgrade evidence, not a
  substitute for the still-open empty-schema gate.
- Commit(s):
- PR:

## 6. Functional integration findings

### FORM-001 — deferred media form re-submit

**Evidence**

- Audit-baseline KSH
  `src/main/resources/static/js/lesson-form-type.js` calls
  `form.requestSubmit()` synchronously from its current submit-event dispatch.
- ULP defers the second submit with `window.setTimeout(..., 0)`, preserves
  `e.submitter`, and releases the `proceeding` guard on failed/cancelled gates.
- ULP regression contract:
  `src/test/java/com/ulp/common/DeferredResubmitGuardTest.java`.
- KSH `flashcard-deck-form.js` already re-submits from a Promise callback and is
  useful as a positive control.

**Impact**

The first click can appear to do nothing because browsers drop a re-entrant
submit; the second click succeeds after the guard was already flipped.

**Implementation checklist**

- [x] Port a KSH-named `submitForReal(submitter)` helper.
- [x] Set the guard before scheduling the deferred native submit.
- [x] Preserve `e.submitter` for multi-submit-button semantics.
- [x] Release the guard on type-switch cancellation, video failure, and PDF
  failure.
- [x] Adapt `DeferredResubmitGuardTest` to `com.ksh`.
- [x] Preserve the real submit event so Quill/content-copy listeners still run.
- [ ] Browser-test create and edit flows with no media, video, PDF, upload
  failure, cancellation, and retry.

**Verification**

- [x] Static regression test passes.
- [ ] First-click browser behavior passes.
- [x] No Practice runtime file or configuration changed.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### FLASH-001 — one flash-to-toast owner

**Evidence**

- `notifications.js` already drains `#flash-data`, sets
  `data-flash-drained="1"`, and removes drained attributes.
- Audit-baseline KSH also drained flash independently in nine page scripts:
  - `src/main/resources/static/js/assignments.js`
  - `src/main/resources/static/js/class-detail.js`
  - `src/main/resources/static/js/classes.js`
  - `src/main/resources/static/js/flashcard-common.js`
  - `src/main/resources/static/js/leader-department.js`
  - `src/main/resources/static/js/lecturer-dashboard.js`
  - `src/main/resources/static/js/question-bank.js`
  - `src/main/resources/static/js/student-classes.js`
  - `src/main/resources/static/js/student-lesson-nav.js`
- ULP removes those page-level drainers and contains
  `FlashDrainSingleOwnerTest`.
- Reviewed templates using those page scripts include the shared app header,
  which loads `notifications.js`.

**Decision**

- [x] `notifications.js` is the single flash-to-toast owner.
- [x] Feature-local toasts for non-flash events remain allowed.

**Implementation checklist**

- [x] Remove only the nine page-level flash-drain blocks.
- [x] Preserve feature-local success/error toasts.
- [x] Adapt `FlashDrainSingleOwnerTest` to KSH globals and paths.
- [x] Ensure the allowlisted `notifications.js` drainer remains functional.
- [ ] Browser-test assignment, class, flashcard, and student lesson redirects.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### FLASH-002 — duplicate notifications script boot

**Evidence**

- `src/main/resources/templates/notifications/index.html` includes the shared
  app header and also includes `/js/notifications.js` locally.
- `src/main/resources/templates/fragments/app-header.html` already loads
  `/js/notifications.js`.
- ULP has the same defect; this is a newly discovered KSH/ULP issue, not an ULP
  fix.

**Remediation**

- [x] Remove the page-local include; the shared app header remains the sole
  script load.
- [x] Structural regression confirms one first-party boot path.
- [x] Structural regression confirms one flash-drain owner.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### UX-TABS-001 — exam AJAX-tab loading lifecycle

**Evidence**

- ULP `test-detail-tabs.js` adds an accessible loading status, `aria-busy`,
  stable panel height, and monitor teardown before fetch.
- ULP `detail-page.css` adds the matching loading/spinner styles.
- Audit-baseline KSH tears down its monitor only after the response arrives,
  allowing polling to continue during slow or hung navigation.

**Implementation checklist**

- [x] Adapt loading markup and CSS under KSH tokens.
- [x] Stop the monitor before issuing fetch.
- [x] Restore or replace the monitor after a successful swap.
- [x] On fetch/parse failure, hand off safely to full navigation.
- [x] Preserve history, modified-click, pager, and save-button behavior.
- [x] Make rapid click/Back navigation latest-wins with request cancellation so
  URL and panel state cannot be completed by a stale response.
- [x] Add a source-level lifecycle/race regression contract.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### UX-TABS-002 — generic detail-tab progressive enhancement

**Evidence**

- ULP’s generic `detail-tabs.js` fetches whole pages, extracts `#tabPanel`,
  swaps content, updates history, and invokes mount hooks.
- It affects admin department/user forms, AI provider forms/history, class
  settings, invite-code delegation, and potentially other evolved KSH pages.

**Decision**

- [x] Treat this as a separate, broad UX integration surface.
- [x] Do not infer that every ULP template activation is safe for KSH.

**Implementation checklist**

- [x] Inventory every template loading `detail-tabs.js`.
- [x] Define ownership when page-specific and generic tab scripts coexist via
  `data-ajax-tabs="owned"`.
- [ ] Manually verify forms, CSRF, focus, history/back-forward, pagination, modal hooks,
  unsaved data, and non-JavaScript fallback on each page.
- [x] Split activation changes by page family in the commit ledger.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

## 7. AI transport and question-generation hardening

### AI-TRANSPORT-001 — OpenAI-compatible response contract

**Evidence**

- Audit-baseline KSH omits explicit `"stream": false` and an
  `Accept: application/json` header.
- It does not reject an HTTP-200 response whose first choice contains
  `finish_reason: "error"` and a nested `error`.
- ULP tests:
  - `Sprint8AiSettingsIntegrationTest#embedded_429_in_a_200_body_continues_the_chain_to_the_next_provider`
  - `Sprint8AiSettingsIntegrationTest#embedded_400_in_a_200_body_halts_the_chain_and_never_contacts_the_next_provider`

**Decision**

- [x] Explicitly request non-streaming JSON.
- [x] Every HTTP or embedded provider-specific rejection (including
  400/401/429/5xx) advances the fallback chain. Credentials, model names, and
  API dialects are provider-scoped, so one provider's 4xx is not evidence that
  the next provider will reject the request.
- [x] Reject ULP's embedded-400 fail-fast policy as a KSH downgrade.
- [x] Do not copy ULP’s longer 60-second read timeout as part of this port.

**Implementation checklist**

- [x] Add `"stream": false`.
- [x] Add `Accept: application/json`.
- [x] Parse embedded error details safely without assuming code type.
- [x] Log each provider attempt and preserve provider-scoped failures.
- [x] Ensure partial content does not convert an embedded error into success.
- [x] Adapt ULP error-response tests to the approved KSH fallback policy.
- [x] Add malformed/missing-code embedded-error coverage.
- [x] Confirm shared AI changes do not alter Practice-specific AI clients or
  configuration.

**Resolved policy divergence**

- [x] A concurrent local `AiClient` diff was observed that classified every
  provider rejection identically and continued the chain after all runtime
  failures.
- [x] KSH explicitly approved all-provider fallback and verified 400, 401, 429,
  500, malformed, missing-code, bounded-body, and success-chain behavior.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### AI-BOUNDS-001 — bounded provider success and error bodies

**Evidence**

- Both audit-baseline KSH and ULP call `readAllBytes()` for error bodies and use
  an unbounded JSON decoder for successful bodies, so a hostile or broken
  endpoint can consume unbounded memory before application-level validation.

**Decision**

- [x] Bound bytes while reading, not after reading.

**Implementation checklist**

- [x] Use `readNBytes()` for error and success streams.
- [x] Bound errors to 2 KiB/300 displayed characters and success bodies to
  1 MiB before JSON decoding.
- [x] Avoid logging authorization headers, API keys, or complete provider
  payloads.
- [x] Add oversized success/error response tests.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### AIQ-001 — AI question generator feature boundary

**ULP feature inventory**

- `AiQuestionDraftSelector`
- `AiQuestionDraftSession`
- `AiQuestionDraftSessionStore`
- `AiQuestionGenDtos`
- `AiQuestionGenerationController`
- `AiQuestionGenerationService`
- `AiQuestionPromptBuilder`
- `AiQuestionResponseParser`
- `DocumentTextExtractor`
- `test-lecturer-ai-questions.js`
- Lecturer-form modal/preview integration
- AI system prompt seed and parser/extractor/integration tests

**Positive ULP controls**

- [x] Controller uses lecturer-or-above preauthorization.
- [x] Service calls `TestAccessResolver.requireManageable`.
- [x] Any student attempt/activity is checked before generation and again under
  the exam-row lock before confirmation.
- [x] Provider I/O is outside the confirm write transaction.
- [x] Generated questions are previewed and explicitly selected before insert.
- [x] Persistence uses the existing exam question-bank writer.
- [x] Document handling checks PDF/DOCX signatures, limits upload to 5 MiB,
  caps extracted text at 30,000 characters, limits PDF pages, and preflights
  DOCX decompression without mutating POI's process-global ZIP settings used by
  Practice.

**Required KSH hardening**

- [x] Use KSH namespace, constants, routes, sources, UI globals, and prompt
  naming exclusively.
- [x] Preserve existing lecturer-form changes with a hand merge; the user's
  sidebar/back-link hunks remain uncommitted and will be excluded from staging.
- [x] Keep this feature on the general/admin AI provider chain only; do not
  reuse or reconfigure Practice AI.
- [x] Enforce CSRF, manageable-test authorization, actor ownership, exam
  identity, expiry, and student-response checks on every confirm.
- [x] Reject HTML in generated content/options/explanations and route insertion
  through the existing KSH question-bank sanitization boundary.
- [x] Treat uploaded text as untrusted data, not executable prompt
  instructions.
- [x] Set 5-second connect/30-second read timeouts and generic user-visible AI
  failure behavior.
- [x] Validate exact count/type, 2–6 unique options, MCQ/MR correctness, text
  length, response size, and malformed/partial model output.
- [x] Verify uploaded file magic/content, not only filename or content type.
- [x] Confirm temporary bytes/text are not logged.
- [x] Add controller/service, replay, concurrency, expiry, malformed document,
  zip-bomb, oversized document, and parser-boundary tests.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### AIQ-SESSION-001 — durable, atomic draft confirmation

**Evidence**

- ULP stores previews in a process-local `ConcurrentHashMap`.
- ULP confirmation performs `get()` and later `delete()` as separate
  operations.
- Concurrent confirms can both observe the same pending session and insert
  duplicate questions.
- Process restart or a multi-node deployment loses or cannot see the preview.

**Decision**

- [x] Reject ULP’s in-memory session store for KSH.
- [x] Persist short-lived sessions and make consumption atomic.

**Implemented design evidence**

- `src/main/resources/db/migration/V58__durable_ai_question_draft_sessions.sql`
  defines a durable session table with status, expiry, consumed time,
  and version fields.

**Implementation checklist**

- [x] Record actor/test ids without foreign keys by explicit short-lived
  snapshot policy; access authorization runs before session lookup.
- [x] Lock and transition exactly one row from `PENDING` to `CONSUMED`.
- [x] Make question insertion and session consumption one transaction.
- [x] Define failure behavior so a rolled-back insert does not consume the
  session.
- [x] Delete expired rows through a dedicated isolated retention worker in
  bounded batches of 500; request/save paths no longer perform cleanup.
- [x] Bound each run to 20 batches, use `REQUIRES_NEW` batch transactions, and
  expose non-PII count/age/deletion metrics.
- [x] Add two-thread confirmation coverage proving one winner.
- [x] Use a database repository/pessimistic lock compatible with restart and
  multiple application instances.
- [x] Repeat/expired/foreign confirmation returns the same non-disclosing
  expired-session bad request.

**Tracking**

- Owner: Codex root
- Verification evidence: focused AI draft maintenance/metrics/worker/store tests
  were included in the 50-test database-free remediation run; scheduler and
  duration validation are covered without touching Practice.
- Commit(s): `4bcc39a0`
- PR:

## 8. Notification email delivery architecture

### MAIL-001 — synchronous SMTP in a transaction

**Evidence**

- Audit-baseline
  `src/main/java/com/ksh/features/notifications/service/NotificationService.java`
  saves the notification and calls `MailService.send()` inside a
  `@Transactional` method.
- Fan-out call sites include
  `LessonsPublishService` and `LecturerAssignmentService`.
- This holds request/database work open across SMTP latency and can send an
  email before an outer transaction later rolls back.

**Decision**

- [x] Notification-triggered SMTP must leave the request transaction.
- [x] The delivery job must be created in the same transaction as the
  notification row.
- [x] Password reset and other security-sensitive direct mail callers retain
  their existing synchronous contract; this change is notification-scoped.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### MAIL-ARCH-001 — reject in-memory queue; use durable outbox

**Rejected ULP design**

- Bounded process-local `ArrayBlockingQueue` with capacity 2,000.
- One daemon worker thread.
- Jobs disappear on restart and are invisible to other nodes.
- Full/shutdown queues drop jobs.
- No retry schedule, dead-letter state, durable lease, or operational recovery.
- Recipient email is present in warning logs.
- ULP also changes product behavior by removing `LESSON_PUBLISHED` from the
  email whitelist.

**Architecture decision**

- [x] Do not port `com.ulp.features.mail.job.*`.
- [x] Use a database outbox with transactionally inserted jobs.
- [x] Preserve KSH’s existing email whitelist unless a separate product
  decision explicitly changes it.
- [x] Document at-least-once delivery: SMTP cannot provide a portable atomic
  handoff with the database, so a crash after SMTP acceptance may duplicate a
  message.

**Implemented design evidence**

- `src/main/resources/db/migration/V59__durable_mail_outbox.sql`
- `src/main/java/com/ksh/features/mail/outbox/`

**Required outbox checklist**

- [x] Notification and outbox insert commit or roll back together; verified
  with a real MySQL rollback contract.
- [x] Worker claims due rows with a pessimistic multi-node-safe lease; a
  two-thread MySQL test proves exactly one claimant.
- [x] Expired leases are recoverable.
- [x] Retry uses at most eight attempts with exponential backoff capped at one
  hour.
- [x] Terminal failures remain observable as `FAILED` rows with non-PII error
  codes.
- [x] Successful delivery atomically marks the job `SENT` and notification
  `is_email_sent=true`.
- [x] Notification jobs are unique by `notification_id`; non-notification mail
  is deliberately outside this outbox scope.
- [x] Password reset and other security-sensitive synchronous mail behavior is
  unchanged.
- [x] Shutdown stops new claims; an interrupted/stale lease becomes recoverable.
- [x] Logs omit recipient addresses and SMTP/provider detail.
- [x] Metrics cover pending, processing, retry, sent, failed, expired leases,
  latency, and oldest pending age.
- [x] Retention is defined as 30 days for `SENT` and 90 days for `FAILED`;
  live `PENDING`, `PROCESSING`, and `RETRY` rows are never selected.
- [x] Explicit restarted-service lease recovery, large-backlog batching, and
  notification-deletion FK behavior have focused coverage. The new MySQL
  retention boundary integration case is recorded but intentionally not run
  while `TEST-ISO-001` remains external.
- [x] Verify `LESSON_PUBLISHED` and `ASSIGNMENT_PUBLISHED` behavior against the
  approved KSH product policy.

**Tracking**

- Owner: Codex root
- Operational runbook: inspect `mail_outbox_jobs` by status/`available_at`;
  correct SMTP configuration; move a deliberately reviewed `FAILED` row to
  `RETRY` with a new `available_at` only through a future admin/runbook action.
  Do not bulk-update rows or promise exactly-once SMTP.
- Verification evidence: focused database-free mail/controller tests were part
  of the 50-test remediation run. The live development runtime executed the
  native bounded retention query successfully with an empty outbox at
  2026-07-29 04:10:24 +07:00; this proves SQL execution, not deletion-boundary
  behavior.
- Commit(s): `9c6991d2`
- PR:

## 9. Constants, controllers, uploads, and broad-copy rejects

### CONST-001 — controller string constants

**Evidence**

- ULP contains a rule document prohibiting hard-coded controller contracts and
  recommending static imports from `IConstant`.
- After role/branding normalization, KSH’s existing `IConstant` already covers
  the common controller contract.
- ULP-only constants mainly support its broader AI provider modal/history UI.

**Decision**

- [x] Keep and extend KSH `IConstant`; do not replace it.
- [x] Prefer static imports; do not make controllers implement `IConstant`.
- [x] Only add constants required by accepted KSH features.

**Implementation checklist**

- [ ] Adapt the rule documentation to KSH terminology.
- [ ] Add a structural test for controller contract literals if practical.
- [x] Review new AI question-generation and AI settings controllers.
- [x] Verify no `HEAD` or ULP constants are reintroduced.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
- PR:

### REJECT-001 — files and surfaces that must not be wholesale replaced

**Conclusive reject list**

- [x] `SecurityConfig`
- [x] `CustomOidcUserPrincipal`
- [x] `JoinClassService` and class/invite locking repositories
- [x] `LessonAttachmentsApiController` and KSH upload/storage routing
- [x] `IConstant`
- [x] `app.js`
- [x] app header/head fragments
- [x] `pom.xml`
- [x] KSH migrations
- [x] Practice code/configuration/assets
- [x] templates and CSS changed mainly by LEADER naming, Practice, or KSH
  responsive evolution

**Verification**

- [x] Every accepted broad diff has an issue ID and written semantic rationale.
- [ ] Reviewers reject “copy ULP version” commits lacking call-site and test
  evidence.

## 10. Verification gates

No item may be merged solely because its focused test passes.

### Gate A — static integrity

- [x] `git diff --check` passes.
- [x] No merge markers or generated build output are staged.
- [x] No live ULP package/product/role leaks remain in `src/main` or `src/test`;
  the byte-pinned V54 historical comment is the documented exception.
- [x] No protected Practice runtime path changed; the sole test portability
  exception is `PRACTICE-PORT-001`.
- [x] No existing migration changed checksum.
- [x] New migrations use unique monotonic V57–V60 versions.
- [x] Source encoding is UTF-8; Vietnamese SQL/UI compiled and Flyway applied.

### Gate B — focused regressions

- [x] Deferred re-submit guard tests.
- [x] Flash single-owner guard tests.
- [x] Notification page single-boot structural test.
- [x] AI transport HTTP and embedded-error tests.
- [x] AI oversized success/error body tests.
- [x] AI question parser/extractor/security tests.
- [x] AI draft atomic-consumption concurrency tests.
- [x] Mail outbox transaction, lease, retry, rollback, and collision tests.
- [x] Public upload tests pass as part of the full suite; no upload
  authorization/configuration code changed.
- [x] Class approval concurrency tests pass as part of the full suite; KSH's
  existing class/invite locking implementation was retained.
- [x] Generic and exam-specific tab lifecycle/latest-wins structural tests.

### Gate C — full automated suite

- [x] `mvn "-Dspring.datasource.hikari.maximum-pool-size=2"
  "-Dspring.datasource.hikari.minimum-idle=0"
  "-Dspring.test.context.cache.maxSize=8" test` passes from a clean process:
  2,439 tests, 0 failures, 0 errors, 2 intentional skips, 3:23.
- [x] Failures from the preceding unconstrained run are linked to
  `TEST-ISO-001` / `NEW-20260729-003` and `NEW-20260729-002`; there are no
  unexplained failures.
- [ ] Tests do not use or mutate a developer/production database.
- [ ] A second full-suite repeat passes without relying on prior test state.
  Per project-owner direction, no more local test runs were started because an
  isolated-test-environment change is already being handled on another
  not-yet-merged branch.

### Gate D — Practice freeze regression

- [x] Practice unit and integration tests pass: 1,346 run, 0 failures/errors,
  2 skipped.
- [x] `PracticeIntegrationTest` passes as part of the full Practice selection.
- [x] `PracticeFunctionalUiContractTest` passes as part of the selection.
- [x] Practice AI readiness/provider tests pass.
- [x] Practice speaking upload/playback/storage tests pass.
- [ ] Manual learner and lecturer Practice smoke tests pass.
- [x] Practice configuration diff reviewed: integration diff is empty; the
  user's pre-existing local port/database/base-URL changes remain unstaged.

### Gate E — browser/manual integration

- [ ] Lesson create/edit first-click submit, cancellation, failure, and retry.
- [ ] Assignment/class/flashcard/student-lesson flash messages appear once.
- [ ] Notification page starts one poller and one handler set.
- [ ] Exam detail tab loading, failure fallback, history, pager, and monitor.
- [ ] Generic detail tabs on every activated template.
- [ ] AI question generation: authorization, upload/text generation, preview,
  selection, confirmation, replay, expiry, concurrent confirmation, and exam
  with existing student attempts.
- [ ] Email outbox: commit, rollback, delivery, retry, terminal failure, and
  restart recovery.
- [ ] Public upload allowlist and private-object denial.
- [ ] Leader routes and authorization.

### Gate F — review and release

- [ ] Security review complete.
- [ ] Migration review complete.
- [ ] Practice freeze review complete.
- [ ] Operational review for mail outbox complete.
- [ ] PR CI green.
- [ ] Review comments resolved.
- [ ] Main updated before final merge and conflicts re-audited.
- [ ] Post-merge smoke test complete.

## 11. Suggested logical commit ledger

The ledger keeps concerns reviewable. A commit row remains unchecked until its
hash exists and its focused verification is attached.

| Slot | Intended concern | Issue IDs | Ready | Commit hash | Verification | PR |
|---|---|---|---|---|---|---|
| C01 | Audit/control document | SCOPE-001, PRACTICE-001 | [x] | `8e7b51d7` | Report created |  |
| C02 | Practice Windows contract portability | PRACTICE-PORT-001 | [x] | `beb08e01` | 13 contract + 1,346 Practice green |  |
| C03 | Deferred lesson re-submit and guard test | FORM-001 | [x] | `025e8716` | Focused + full suite green |  |
| C04 | Single-owner flash and duplicate include cleanup | FLASH-001, FLASH-002 | [x] | `4a196902` | Structural + full suite green |  |
| C05 | Latest-wins generic and exam detail-tab engines | UX-TABS-001, UX-TABS-002 | [x] | `96d6826b` | 3 structural + full suite green |  |
| C06 | Generic detail-tab page activation | UX-TABS-002 | [x] | `a845b3b9` | Controller + full suite green |  |
| C07 | AI provider detail history | AI-TRANSPORT-001 | [x] | `6e606cda` | Sprint 8 + full suite green |  |
| C08 | AI transport, fallback, bounds, and logging contracts | AI-TRANSPORT-001, AI-BOUNDS-001 | [x] | `096b5da4` | 39 settings + 5 logging + full suite green |  |
| C09 | AI question prompt seed and conditional hardening | AIQ-001, MIG-001 | [x] | `7328f95f` | Flyway schema current at V60 |  |
| C10 | AI question DTO, prompt, parser, and tests | AIQ-001 | [x] | `0f23b706` | Focused + full suite green |  |
| C11 | Secure PDF/DOCX/text extraction | AIQ-001 | [x] | `e35d6d40` | Focused + full suite green |  |
| C12 | Durable AI draft schema, store, and concurrency tests | AIQ-SESSION-001, MIG-001 | [x] | `f2eb51c0` | Concurrency + full suite green |  |
| C13 | Attempt/grading-semantic locks | CONC-001, AIQ-001 | [x] | `cee17ca5` | 14 integration + full suite green |  |
| C14 | Atomic AI generation/confirmation service | AIQ-001, AIQ-SESSION-001 | [x] | `c2d2f2ad` | Focused + full suite green |  |
| C15 | AI question controller authorization/contracts | AIQ-001, CONST-001 | [x] | `0ef7ed0c` | Controller + full suite green |  |
| C16 | AI question lecturer UI | AIQ-001 | [x] | `8914cfee` | Structural + full suite green; manual UAT open |  |
| C17 | Durable mail outbox schema/domain/repository | MAIL-ARCH-001, MIG-001 | [x] | `257a61b6` | Flyway + repository integration green |  |
| C18 | Mail outbox transaction and retry state machine | MAIL-ARCH-001 | [x] | `70b95e09` | Focused + full suite green |  |
| C19 | Mail outbox delivery worker and scheduler isolation | MAIL-ARCH-001 | [x] | `4b67e4ce` | Processor/scheduler + full suite green |  |
| C20 | Notification/outbox atomic integration | MAIL-001, MAIL-ARCH-001 | [x] | `18768865` | 3 DB integration + full suite green |  |
| C21 | Final evidence, branding, and handoff record | BRAND-001, CONST-001 | [x] | `47f7ffea` | Static diff/report review | #29 |
| C22 | PR URL/head handoff record | SCOPE-001 | [x] | PR-head documentation commit | PR #29 created as HiuHi32 | #29 |

If implementation reality requires a split or combination, update this table
before committing. Do not mix unrelated issue IDs merely to reach a target
commit count.

## 12. Pull request record

| Field | Value |
|---|---|
| Branch | `codex/ulp-ksh-integration-hardening` |
| Base | `main` |
| PR URL | `https://github.com/toannnnq1424/KoreanStudyHub/pull/29` |
| Draft/ready | Merged by the project owner |
| Head SHA reviewed | `47f7ffea98ed3e2b703268a945c78fbe13c3f7a0` (code/evidence head before this documentation-only C22) |
| CI run | GitHub reports 0 configured checks; local full-suite evidence is Gate C |
| Security reviewer |  |
| Migration reviewer |  |
| Practice freeze reviewer |  |
| Final approver | Project owner performed the merge |
| Merge commit | `27466f69a6f94f239f05a44d22b26616a01a8fe0` |
| Post-merge verification |  |

### Required PR summary checklist

- [x] Enumerates accepted ULP behavior, not just copied filenames.
- [x] Enumerates rejected ULP behavior and KSH safeguards retained.
- [x] Links every commit to stable issue IDs.
- [ ] Includes migration upgrade and empty-schema evidence.
- [x] Includes Practice freeze evidence.
- [ ] Includes browser evidence for first-click submit, single toast, tabs, AI
  generation, and outbox recovery.
- [x] Calls out at-least-once email duplicate risk and operational recovery.
- [x] Contains no claim that an unchecked item is complete.

## 13. Newly discovered issues

Use the next ID in the form `NEW-YYYYMMDD-NNN`. Do not silently fix an
unrelated discovery inside another commit.

### NEW-20260729-001 — duplicate notifications script inclusion

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] verified
- Related issue: FLASH-002
- Evidence:
  - `templates/notifications/index.html`
  - `templates/fragments/app-header.html`
- Owner: Codex root
- Remediation: removed the page-local script include; app header remains owner.
- Test/evidence: `FlashDrainSingleOwnerTest` confirms one include and one drainer.
- Commit:
- PR:

### NEW-20260729-002 — AiClient fallback-policy divergence

- Severity: High
- Status: [x] Finding confirmed; [x] reconciled; [x] verified
- Related issue: AI-TRANSPORT-001
- Evidence: ULP stops after an embedded 400; KSH's configured providers may use
  independent credentials, models, and API dialects.
- Owner: Codex root
- Remediation: explicitly approved all-provider fallback and updated tests/docs
  rather than accidentally inheriting ULP fail-fast semantics.
- Test/evidence: `Sprint8AiSettingsIntegrationTest` — 39/39 green, including
  embedded 400/429, HTTP 400/401/500, malformed bodies, and fallback order.
- Commit:
- PR:

### NEW-20260729-003 — integration-test environment isolation

- Severity: High
- Status: [x] Confirmed; [x] database guard merged on `main`; [x] public upload
  isolation added; [ ] cross-schema migration review deferred
- Evidence:
  - Commit `41dc2e49` added a high-precedence test datasource resource and an
    `EnvironmentPostProcessor` that accepts only an explicit disposable
    `ksh_test_<run_id>` catalog; `ksh_db`, `ksh_dba`, and shared `ksh_test`
    catalogs fail closed.
  - `src/test/resources/config/application.properties` now routes
    `app.upload.dir` to `target/test-uploads` unless a caller supplies an
    isolated `TEST_UPLOAD_DIR`.
- Risk: integration tests may use default application datasource/Flyway
  settings and mutate a developer database or developer-owned files. The
  datasource and public-upload paths now fail closed or stay test-local.
- Deferred boundary: V1 still contains a cross-schema
  `CREATE DATABASE IF NOT EXISTS ksh_db`; changing or replacing that migration
  is intentionally deferred with the project owner's migration freeze.
- Confirmation steps:
  - [x] Require an explicit disposable database URL and credentials.
  - [x] Reject non-`ksh_test_<run_id>` database catalogs before context startup.
  - [x] Isolate the shared public-object root from the developer `uploads`
    directory.
  - [ ] Review the V1 cross-schema statement in the dedicated migration phase.
- Verification boundary: no focused/full test was run in this follow-up at the
  project owner's request; the guard itself was previously merged on `main`.
- Commit: `41dc2e49` (database guard); current follow-up (upload root)
- PR:

### NEW-20260729-004 — Practice contract path separator on Windows

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] verified
- Related issue: PRACTICE-001 / `PRACTICE-PORT-001`
- Evidence: `SpeakingPromptAuthoringUiContractTest` filtered
  `Path#toString()` with forward-slash-only fragments, producing an empty
  learner file set on Windows.
- Remediation: normalize `\` to `/` inside the test before filtering.
- Impact: test-only; no Practice runtime, AI, storage, schema, or configuration
  change.
- Test/evidence: exact contract 13/13 green; full Practice 1,346/1,346
  non-skipped tests green (2 intentional skips).
- Owner: Codex root
- Commit:
- PR:

### NEW-20260729-005 — MySQL version exceeds Flyway tested range

- Severity: Medium
- Status: [x] Finding confirmed; [ ] remediated; [ ] verified on supported CI DB
- Evidence: every Spring/Flyway integration start reports MySQL 9.2 while the
  bundled Flyway version declares support through MySQL 8.1.
- Risk: migrations passed locally, but vendor/version edge cases are outside
  the library's declared tested range.
- Remediation:
  - [ ] Align CI/deployment MySQL with a supported version, or upgrade Flyway
    after compatibility review.
  - [ ] Run empty-schema and V56-upgrade migration jobs in CI.
- Owner: unassigned
- Commit: N/A (operational/dependency follow-up)
- PR:

### NEW-20260729-006 — AI draft cleanup was opportunistic

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Original evidence: generation deleted at most 500 expired sessions before
  saving a preview, so expired rows remained indefinitely when generation
  stopped.
- Implemented safeguard: the request/save path no longer performs retention.
  A private daemon worker starts after five minutes, runs hourly, deletes
  `expires_at <= cutoff` in batches of 500, and stops after 20 batches per run.
  Every batch uses a separate `REQUIRES_NEW` transaction with a timeout.
- Remediation:
  - [x] Add an isolated scheduled retention sweep.
  - [x] Define expired count, oldest-expired age, deleted-row, failure, and
    duration metrics without session payload or actor data.
  - [x] Keep the worker, configuration, thread names, and storage independent
    from `/practice`.
- Verification: focused maintenance, metrics, worker, and store tests were part
  of the database-free 50-test remediation run.
- Owner: Codex root
- Commit: `4bcc39a0`
- PR:

### NEW-20260729-007 — mail outbox retention and metrics gap

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused/runtime verification
- Original evidence: durable states, leases, retries, and dead-letter rows
  existed, but terminal rows had no cleanup job and backlog/age metrics were
  absent.
- Implemented behavior:
  - bounded native deletion selects only `SENT` older than 30 days and `FAILED`
    older than 90 days;
  - each run is capped at ten batches of 500 and stops after a short batch;
  - status, claimable, expired-lease, oldest-age, deletion, failure, and
    duration metrics contain no recipient or message data;
  - an ADMIN-only settings panel exposes the same non-PII operational snapshot;
  - retention counters publish only after the transactional proxy returns, so
    rolled-back deletes are not reported as committed.
- Remediation:
  - [x] Define and implement terminal-row retention.
  - [x] Add pending/retry/failed/oldest-age metrics and an operator snapshot.
  - [x] Add restarted-service recovery, backlog, FK deletion, SQL contract,
    metrics-failure isolation, and worker-bound tests.
  - [ ] Run the new terminal-boundary integration case after `TEST-ISO-001`
    provides an isolated database.
- Verification: focused database-free mail tests passed in the 50-test run. The
  live MySQL runtime executed a retention sweep at 2026-07-29 04:10:24 +07:00
  with zero rows and no SQL error.
- Owner: Codex root
- Commit: `9c6991d2`
- PR:

### NEW-20260729-008 — grading semantics mutable after an attempt

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] full-suite verified
- Related issue: CONC-001 / AIQ-001
- Evidence: the baseline in-place writer preserved row ids but accepted changes
  to question type, points, and option `is_correct`, allowing review content to
  contradict an already-stored grade.
- Remediation: once any attempt exists, validate the complete grading contract
  before mutation and allow only question/option display text and explanation
  changes. Type, points, answer key, ids, and shape are immutable.
- Test/evidence: focused integration test rejects an answer-key flip and
  confirms persisted correctness remains unchanged.
- Owner: Codex root
- Commit:
- PR:

### NEW-20260729-009 — stale AJAX tab response can beat Back/new click

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [ ] browser UAT
- Related issue: UX-TABS-001 / UX-TABS-002
- Evidence: both orchestrators returned immediately while `navigating=true`,
  dropping a new click or `popstate` and permitting stale URL/panel state.
- Remediation: monotonically sequence requests, abort the prior fetch when
  supported, and apply only the latest response while retaining hard-navigation
  fallback.
- Test/evidence: `DetailTabsContractTest` pins latest-wins/abort seams; manual
  rapid-click/Back UAT remains open.
- Owner: Codex root
- Commit:
- PR:

### CONTRIBUTOR-ID-001 — historic commits are attributed to separate accounts

- Severity: Low
- Status: [x] Root cause confirmed; [x] future identity pinned; [x] history rewrite
  and email transfer declined by the project owner
- Evidence:
  - The project Contributors graph links 178 recent commits to `toannq1424`
    and 73 to `HiuHi32`; it does not merge them into repository owner
    `toannnnq1424`.
  - Root commit `8a349530` is authored as `toannq1424` with the historic
    verified email that GitHub maps to `/toannq1424`.
  - `origin/main` contains 206 commits with that historic identity, 73 with
    the `HiuHi32` identity, and four with the current account's noreply
    identity.
  - PR #29's 22 non-merge commits are authored as `HiuHi32`; only merge commit
    `27466f69` carries the current account's GitHub noreply identity.
- Decision:
  - [x] Do not rewrite merged commit history to manufacture a different
    contribution graph.
  - [x] Do not add, remove, or transfer account email addresses.
  - [x] Pin this repository's future author and Git credential username to
    `toannnnq1424` using its GitHub noreply address.
  - [x] Keep the browser signed in to the main `toannnnq1424` account.
- Verification: local `user.name`, `user.email`, and
  `credential.https://github.com.username` resolve to the selected account;
  the next real commit must be rechecked with `git show --format=fuller`.
- Owner: Codex root
- Commit: N/A (repository-local Git configuration; not committed)
- PR: N/A

### CONTRIBUTOR-NAMDK-LINEAGE-001 — preserve genuine namdk24 history without a code rollback

- Severity: Low for attribution; Critical if merged with the normal recursive
  strategy.
- Status: [x] Branch audited; [x] author mapping verified; [x] merged into
  `main` with an unchanged tree; [ ] Contributors cache refresh observed
- Evidence:
  - `origin/feature/profile` had five commits not reachable from `main`: four
    non-merge commits plus one merge commit, all authored as
    `namdk24 <dokhacnamhda@gmail.com>`.
  - GitHub rendered every commit author as a link to `/namdk24`, proving the
    historic email is already associated with that account; logging into the
    contributor account was not required.
  - A normal merge would have re-opened all `/uploads/**`, removed login
    throttling, weakened current Practice/LEADER route policy, and restored
    superseded Question Bank/templates/tests.
  - The password-change session-revocation idea is potentially useful, but its
    implementation was coupled to those security regressions and therefore was
    not imported. It requires a separate current-main port and review.
- Decision:
  - [x] Use Git's `ours` merge strategy, not the recursive strategy with
    `-X ours`.
  - [x] State explicitly in the merge message that the branch implementation
    is superseded and intentionally not imported.
  - [x] Keep the original branch commits and authors unchanged; do not create
    synthetic or backdated commits.
- Verification:
  - Pre-merge `origin/main` tree:
    `74fb84c5ddcf5cb36a4c3e12f4759e67c33c6959`.
  - Merge commit tree:
    `74fb84c5ddcf5cb36a4c3e12f4759e67c33c6959`.
  - `git diff origin/main^..origin/main` is empty for the history-only merge,
    while GitHub now reports that `main` contains all commits from
    `feature/profile`.
  - GitHub Contributors excludes merge commits, so the expected attribution
    increase is the four genuine non-merge commits after its statistics cache
    refreshes.
- Owner: Codex root / repository owner account
- Commit: `f2ae67f9eb77fc4119ec1b04bc47a05ccbd80e6a`
- PR: existing PR #43 became fully contained by `main`

### UX-DIRTY-001 — AJAX tabs can discard unsaved form changes

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused contract verified;
  [ ] browser UAT
- Related issues: UX-TABS-001 / UX-TABS-002 / NEW-20260729-009
- Evidence: both `detail-tabs.js` and `test-detail-tabs.js` can replace
  `#tabPanel` after click or history navigation without first checking whether
  an editable form differs from its mounted baseline.
- Risk: an administrator or lecturer can lose unsaved edits when switching a
  tab, following Back/Forward, or entering the hard-navigation fallback.
- Remediation:
  - [x] Track a fresh form baseline after every successful panel swap.
  - [x] Prompt only when editable controls changed; exclude read-only
    pager/search interactions.
  - [x] Cancel preserves DOM, URL, history state, active monitors, and
    in-flight request ownership.
  - [x] Confirm navigates exactly once; a valid native form submit does not
    prompt.
  - [x] Async save, bank insertion, and AI insertion freeze the panel from
    payload capture through success/failure; failure unlocks without clearing
    the dirty baseline.
  - [x] Add deterministic contract coverage for click, `popstate`, fallback,
    submit, reset, and successful swap.
- Practice impact: none permitted; this issue must not modify `/practice`
  code, configuration, storage, schema, or AI setup.
- Verification: `DetailTabsContractTest` passed 5/5 before the final
  synchronous-exception wrapper; the wrapper and all affected scripts pass
  `node --check`. Manual cancel/confirm/Back race UAT remains open.
- Owner: Codex root
- Commit: `49ce1e5d`
- PR:

### CLASS-NAV-001 — class-scoped test creation loses its return context

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused/browser verification
- Reproduction: `/lecturer/classes/2/tests` linked to
  `/lecturer/tests/new`; the form did not select class 2 and Back targeted the
  global `/lecturer/tests` page.
- Remediation:
  - [x] Add the class id to the create link.
  - [x] Resolve it only against `examService.ledClasses`; never accept an
    arbitrary return URL.
  - [x] Preselect the owned class and use the canonical class-test URL for Back
    and the post-save redirect.
  - [x] Add “Kho đề test” to the Giảng dạy menu.
- Verification: `LecturerTestNavigationTest` passed 3/3. Browser inspection
  confirmed `classId=2`, selected value `2`, and Back target
  `/lecturer/classes/2/tests`; menu verification remains role/session-dependent.
- Owner: Codex root
- Commit: `7bc1a49d`
- PR:

### QB-IMPORT-001 — Excel preview is rejected by CSRF

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] source-contract verification
- Root cause: `/lecturer/question-bank/import/preview` is a multipart POST, but
  `question-bank-import.js` sent the CSRF header only for the later JSON confirm
  request.
- Remediation:
  - [x] Read Spring's `_csrf` and `_csrf_header` meta values.
  - [x] Attach the header to multipart preview without setting a manual
    `Content-Type`, retaining the browser-generated boundary and same-origin
    credentials.
- Verification: `node --check` passed and
  `QuestionBankFrontendContractTest` pins the preview header contract. A
  lecturer-role real-file browser UAT remains recommended.
- Owner: Codex root
- Commit: `a517c6c9`
- PR:

### QB-TAXONOMY-001 — Admin and question-bank taxonomies are disconnected

- Severity: High
- Status: [x] Finding confirmed; [x] compatibility bridge implemented;
  [ ] long-term schema consolidation
- Evidence:
  - `/admin/categories` reads the global hierarchical `categories` table.
  - Question-bank authoring originally read only department-scoped
    `question_bank_categories`.
  - The development DB has 16 global rows (eight active top-level categories
    and eight children), but zero question-bank categories/items/options.
- Current compatibility decision:
  - [x] Offer active top-level Admin categories together with active
    department-specific categories.
  - [x] Represent an Admin choice as a transient negative reference so GET and
    preview requests do not write the DB.
  - [x] On manual save or import confirm, atomically mirror the selected name
    into the actor's department using the unique `(department_id, name)` key,
    then persist the positive mirror id required by the existing FK.
  - [x] Let an existing department row, including an inactive one, shadow the
    Admin name so LEADER hide decisions cannot be bypassed.
  - [x] Keep CSRF, actor role, department access, and FK enforcement intact.
- Long-term consolidation checklist:
  - [ ] Decide whether global `categories` is the sole canonical taxonomy or
    whether department overrides remain a product requirement.
  - [ ] If global-only is approved, add a new migration that maps existing
    mirrors, moves `question_bank_items.category_id` to the canonical model,
    updates import/review services, validates orphan counts, and only then
    drops `question_bank_categories`.
  - [ ] Do not edit V46 or drop the current table in place.
- Verification: category/controller/frontend focused batches passed 10/10;
  the final atomic-mirror hardening passed 6/6. Browser UAT with a lecturer
  assigned to a department remains open.
- Owner: Codex root
- Commit: `a517c6c9`
- PR:

### DB-INVENTORY-001 — table-count and taxonomy storage audit

- Severity: Medium
- Status: [x] Inventory captured; [x] immediate drop decision recorded;
  [ ] broader table rationalization
- Direct `information_schema` evidence at approximately 2026-07-29 04:08 +07:00:
  - 104 base tables and one view;
  - 31 `practice_*` tables and 16 activity/audit tables by migration inventory;
  - `categories`: 16 rows, 49,152 allocated bytes;
  - `question_bank_categories`: zero rows, 65,536 allocated bytes;
  - `question_bank_items`: zero rows, 98,304 allocated bytes;
  - `question_bank_options`: zero rows, 32,768 allocated bytes.
- Dependency evidence: `question_bank_items.category_id` references
  `question_bank_categories.id`; the table also owns department/name uniqueness,
  department/creator FKs, and active lookup indexes.
- Decision:
  - [x] Do not drop `question_bank_categories` on this no-migration branch:
    64 KiB is not material bloat, while a direct drop breaks the FK and eight
    production files.
  - [x] Record schema consolidation under `QB-TAXONOMY-001`.
  - [ ] Audit the 31 Practice and 16 activity/audit tables by ownership,
    retention, row count, and actual size before proposing broader deletion.
- Owner: Codex root
- Commit: documentation-only follow-up
- PR:

### TEST-CONC-001 — test-attempt lifecycle mutations were not serialized

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: concurrent submit requests could both observe `IN_PROGRESS`, create
  duplicate response rows, and overwrite the final score/status; heartbeat
  could also race finalization.
- Remediation:
  - [x] Add an owner-scoped `PESSIMISTIC_WRITE` attempt lookup.
  - [x] Use the same locked lookup for both `submit` and `heartbeat`.
  - [x] Preserve the existing idempotent response for an already-closed attempt.
- Verification: `TestAttemptLifecycleLockContractTest` passed; the contract
  verifies the owner predicate, pessimistic lock, and both lifecycle call sites.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `d633c175`
- PR:

### ASSIGN-CONC-001 — assignment submit and grade could lose updates

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk:
  - a student re-submit could pass the pre-grade check, race a lecturer grade,
    and restore a `GRADED` submission to `SUBMITTED`;
  - two first submissions could both observe no row and one would fail with a
    raw unique-constraint error.
- Remediation:
  - [x] Lock the stable assignment parent before submit or grade.
  - [x] Lock an existing student submission before updating it.
  - [x] Use the same assignment-then-submission lock order in both workflows.
- Verification: `AssignmentConcurrencyContractTest` and
  `TestAttemptLifecycleLockContractTest` passed together, 2/2.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `26f8d327`
- PR:

### AUTH-RESET-001 — password-reset bearer credential exposure and consume race

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Original risk:
  - raw bearer tokens were stored in the database and emitted in a DEBUG reset
    URL when email delivery failed;
  - unknown submitted email addresses were logged;
  - two reset requests could consume the same still-valid token concurrently;
  - reset pages did not explicitly disable caching or referrer disclosure.
- Remediation:
  - [x] Persist SHA-256 token digests for all newly issued links.
  - [x] Retain a read-only raw-token compatibility fallback only for links
    already issued within their one-hour TTL.
  - [x] Use `PESSIMISTIC_WRITE` for token consumption.
  - [x] Remove raw token, reset URL, recipient, and unknown-email PII logs.
  - [x] Apply `Cache-Control: no-store` and `Referrer-Policy: no-referrer` to
    reset GET and POST responses.
- Verification: `PasswordRecoveryServiceSecurityTest` and
  `PasswordRecoveryControllerSecurityTest` passed 6/6.
- Scope: no Practice, migration, or developer-password change.
- Follow-up:
  - [x] Add bounded forgot-password throttling and terminal-token retention;
    tracked under `AUTH-RESET-002`.
- Owner: Codex root
- Commit: `902e8ea2`
- PR:

### IMPORT-REPLAY-001 — concurrent confirmation could replay an import session

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Affected workflows: class-student Excel import and Question Bank Excel import.
- Original risk: both services read a JVM session and removed it only after DB
  writes, allowing two concurrent confirm requests to process the same preview.
- Remediation:
  - [x] Atomically claim an owned, unexpired session with
    `ConcurrentHashMap.computeIfPresent`.
  - [x] Wrong-owner claims leave the session intact.
  - [x] Validation/explicit-confirmation early exits restore the session.
  - [x] Transaction `afterCompletion` restores a claim after rollback; a
    committed transaction leaves it consumed.
  - [x] Expired sessions are never restored.
- Verification: `ImportSessionAtomicClaimTest` passed 3/3, covering both stores,
  concurrent exactly-one claim, restoration, and wrong-owner behavior.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `a1317a94`
- PR:

### AUTH-RESET-002 — reset-request abuse and token retention were unbounded

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Original risk: a caller could generate unlimited reset emails and valid
  tokens; terminal rows accumulated indefinitely.
- Remediation:
  - [x] Apply a neutral process-local limit of three requests per 15 minutes to
    both normalized email and servlet remote address.
  - [x] Store only hashed limiter keys, prune expired windows, and cap the
    access-ordered map at 10,000 keys.
  - [x] Do not trust proxy headers without an explicit trusted-proxy policy.
  - [x] Invalidate prior unused tokens transactionally before issuing a new one.
  - [x] Run hourly retention with a default 500-row batch, hard cap 1,000, and
    seven-day cutoff for expired/used rows.
  - [x] Keep throttled responses enumeration-neutral with no user lookup, DB
    write, email, token log, or PII metric.
- Verification: `PasswordRecoveryServiceSecurityTest`,
  `PasswordRecoveryControllerSecurityTest`,
  `PasswordResetRequestThrottleTest`, and
  `PasswordResetTokenRetentionTest` passed in one focused database-free run.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `379c1923`
- PR:

### MSG-CONC-001 — conversation get-or-create was not idempotent under concurrency

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: two concurrent requests for the same normalized participant pair could
  both miss the lookup; the database unique key prevented duplicates but one
  request surfaced a constraint failure.
- Remediation:
  - [x] Route generic and class-scoped conversation creation through one helper.
  - [x] Lock the lower participant's stable user row before lookup/insert so
    both orders of the same pair serialize on the same row.
  - [x] Avoid catch/requery inside a rollback-only transaction and avoid
    database-specific native SQL.
- Verification: `MessagingConversationCreationLockTest` passed 3/3 and verifies
  lock-before-lookup, shared helper use, and no lock for ineligible pairs.
- Trade-off: different pairs sharing the same lower user id serialize briefly.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `c6703a1c`
- PR:

### ASSIGN-AUTH-001 — learner assignment controller lacked an exact role boundary

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: the route fell through to `anyRequest().authenticated()` and the service
  checked only active enrollment. An account changed to lecturer/leader/admin
  while retaining an enrollment could still read or submit through learner
  assignment endpoints.
- Remediation:
  - [x] Apply `@PreAuthorize(Roles.PREAUTH_STUDENT)` at controller scope.
  - [x] Preserve the existing enrollment and ownership checks as the
    resource-level gate.
- Verification: `StudentAssignmentRoleBoundaryContractTest` passed 1/1 and
  pins the exact `hasRole('STUDENT')` runtime annotation.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `5ef0056b`
- PR:

### UX-TABS-003 — exact active exam-tab clicks reloaded the current draft

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: clicking “Thông tin chung” while already on that exact URL prompted to
  discard edits and could fetch/replace the same form unnecessarily.
- Remediation:
  - [x] Detect an active tab whose normalized absolute URL exactly equals the
    rendered URL.
  - [x] Prevent the no-op before dirty confirmation or AJAX navigation.
  - [x] Retain normal navigation when query parameters or destination differ.
- Verification: `DetailTabsContractTest` passed 5/5; `node --check` passed for
  `test-detail-tabs.js`. The earlier four dirty/history findings were re-audited
  and remain fixed.
- Scope: no Practice, migration, or developer-password change.
- Owner: Codex root
- Commit: `774b8ce4`
- PR:

### AUTH-LOGIN-001 — form login accepted unlimited password guesses

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: an attacker could issue unbounded password checks against one account
  or from one client address.
- Remediation:
  - [x] Add a bounded 15-minute process-local failure window for normalized
    account and servlet remote-address keys.
  - [x] Hash retained keys, prune expired windows, and cap state at 20,000 keys.
  - [x] Reject blocked attempts before password verification with the same
    neutral `/login?error` redirect used for ordinary failures.
  - [x] Clear the account bucket after a successful login while preserving
    saved-request navigation.
  - [x] Do not trust forwarded client headers without a trusted-proxy policy.
- Verification: `LoginAttemptThrottleTest` 4/4 and
  `LoginThrottleFilterTest` 2/2 passed database-free.
- Scope: no Practice, migration, or developer-password change.
- Commit: `f87eaf2d`

### TEST-AUTH-001 — learner test endpoints lacked an exact role boundary

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: an account promoted to lecturer, leader, or admin while retaining an
  active enrollment could still start, heartbeat, submit, or review a learner
  test attempt.
- Remediation:
  - [x] Require `Roles.PREAUTH_STUDENT` on `StudentTestController`,
    `StudentClassTestsController`, and `TestApiController`.
  - [x] Keep enrollment and ownership checks as the resource-level boundary.
  - [x] Leave `StudentPracticeController` unchanged under the Practice freeze.
- Verification: `StudentTestRoleBoundaryContractTest` passed 1/1.
- Scope: no Practice, migration, or developer-password change.
- Commit: `459b71df`

### PUBLIC-VIEW-001 — public attachment responses could outlive token expiry in caches

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Remediation:
  - [x] Add `Cache-Control: private, no-store` and
    `Referrer-Policy: no-referrer` to success, not-found, and internal-error
    responses.
  - [x] Remove token/storage-key material from error logs.
- Verification: `PublicViewControllerSecurityHeadersTest` passed 2/2.
- Follow-up: plaintext bearer-token storage was tracked and is now remediated
  under `PUBLIC-VIEW-TOKEN-001`; focused tests for that later change were not
  run per request.
- Scope: no Practice, migration, or developer-password change.
- Commit: `642a664a`

### FLASH-REVIEW-001 — concurrent ratings could lose Smart Review state

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: concurrent first ratings could collide on the unique review row, while
  existing-row ratings could both derive from the same prior SM-2 state.
- Remediation:
  - [x] Authorize access before acquiring a database lock.
  - [x] Lock the stable flashcard row before reading or mutating review state.
- Verification: `ParentLockBeforeMutationContractTest` includes the pessimistic
  lock and lock-before-review contract; 4/4 tests passed across both data fixes.
- Scope: no Practice, migration, or developer-password change.
- Commit: `fcd397fc`

### LESSON-ORDER-001 — concurrent reorder could collide on temporary positions

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Remediation:
  - [x] Lock the class row before loading/mutating its sections.
  - [x] Lock the class-scoped section row before loading/mutating its lessons.
  - [x] Retain the existing two-phase temporary/final ordering algorithm.
- Verification: `ParentLockBeforeMutationContractTest` passed 4/4 across
  `FLASH-REVIEW-001` and this issue.
- Scope: no Practice, migration, or developer-password change.
- Commit: `fcd397fc`

### IMPORT-STALE-001 — stale upload callbacks can replace a newer import preview

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Reproduction:
  - [x] Start upload A, close the modal, reopen it, and start upload B.
  - [x] Resolve B first and A second; the old callback replaces the visible
    rows and session id, so confirmation can target the unintended workbook.
- Remediation checklist:
  - [x] Invalidate and abort the active upload on modal close/open.
  - [x] Ignore callbacks whose request/open generation is no longer current.
  - [x] Prove only the newest session can render and be confirmed.
- Verification: `ImportExcelUploadRaceContractTest` passed 2/2;
  `node --check src/main/resources/static/js/import-excel.js` passed.
- Scope: student Excel import only; no Practice or migration change.
- Commit: `b3de78e2`

### Follow-up findings and resolved product decisions

#### DEPT-LEADER-CONC-001 — department leader reassignment is not serialized

- Severity: High
- Status: [x] Finding confirmed; [x] policy decided; [x] remediated;
  [ ] real-database concurrency/full-suite verification
- Evidence: `DepartmentService.applyLeaderAssignment()` updates department and
  user rows through unlocked read/check/write sequences.
- Risk: concurrent edits can leave multiple department pointers inconsistent
  with `users.department_id`, or demote a newly assigned leader.
- Product decision: [x] one user may lead at most one department.
- Remediation:
  - [x] Acquire the stable `system_settings` row keyed by `ai.provider` as a
    shared database anchor before department-leader mutations, including the
    empty-department case; fail closed if that seeded row is missing.
  - [x] Lock the target department and old/new users in deterministic order
    before validating or mutating either side of the relationship.
  - [x] Reject assigning a candidate already referenced as leader by another
    department.
  - [x] Treat a same-leader request as a repair operation: restore the user's
    `LEADER` role and matching `department_id` when legacy drift is detected.
  - [x] Make Admin user edits acquire the same anchor and reject a role or
    department change while the user is still referenced as a department
    leader; the operator must reassign/clear the department first.
- Verification boundary: source-contract tests were added but not run at the
  project owner's request. Compile/static integrity passed; multi-connection
  lock behavior on a real database remains unchecked.

#### PROGRESS-TOGGLE-001 — concurrent lesson-completion toggles lose parity

- Severity: Medium
- Status: [x] Finding confirmed; [x] existing toggle semantics retained;
  [x] remediated; [x] focused verification
- Evidence: `LearningProgressService.toggleCompletion()` performs an unlocked
  find-or-create/update on unique `(user_id, lesson_id)`.
- Product decision: [x] preserve toggle parity: two serialized toggles cancel
  each other. Do not replace the operation with an idempotent set-state API.

#### MSG-REALTIME-001 — realtime message body is truncated to sidebar snippet

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] verified
- Evidence: `MessagingService` sends only `snippet(body)` and
  `messaging.js` renders that snippet as the open-thread bubble.
- Remediation checklist:
  - [x] Add an exact full-body field while retaining the bounded sidebar snippet.
  - [x] Render the full body only in the active conversation.

#### MSG-READ-001 — incoming message in an open thread remains unread

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] verified
- Evidence: STOMP handling appends the bubble and refreshes unread count but
  does not mark the newly received owned conversation read.
- Remediation checklist:
  - [x] Add an owned mark-read endpoint/service action.
  - [x] Mark read before refreshing the visible badge.

#### COMMENT-DUPE-001 — root lesson-comment submit has no in-flight guard

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Evidence: `lesson-comments.js` permits two submit events before the first POST
  clears the composer.
- Remediation checklist:
  - [x] Disable/guard submit while the POST is in flight.
  - [x] Preserve text and restore the button after failure.

#### SECTION-DELETE-STATE-001 — selected-section deletion leaves stale client state

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Evidence: `sections.js` removes the section entry but leaves the deleted
  section's lesson pane and `state.selectedSectionId` active.
- Risk: subsequent lesson actions can target a section that no longer exists.
- Remediation checklist:
  - [x] Clear or select a valid replacement section after successful deletion.
  - [x] Reload/clear the right pane before enabling further lesson actions.

#### PUBLIC-VIEW-TOKEN-001 — public attachment tokens are stored as plaintext

- Severity: Medium
- Status: [x] Finding confirmed; [x] design approved; [x] remediated;
  [ ] focused/full-suite verification
- Constraint: the current service reuses a live raw token to reconstruct its
  public URL, so a digest-only change cannot be made safely in place.
- Product decision: [x] use non-reuse/replacement semantics; do not add a
  migration during the current freeze.
- Remediation:
  - [x] Generate a fresh 32-byte URL-safe bearer on every public-URL request,
    revoke all previous live rows for that attachment, and persist only its
    SHA-256 hexadecimal digest in the existing column.
  - [x] Resolve new links by digest. Fall back to a raw database lookup only
    when the submitted value exactly matches the legacy 32-character lowercase
    hexadecimal token shape, so arbitrary new bearers cannot enter the legacy
    lookup path.
  - [x] Keep already-issued legacy URLs usable only through their normal
    one-hour lifetime.
  - [x] Use `noRollbackFor = EntityNotFoundException.class` so deletion of an
    expired row is committed even though resolution finishes with a not-found
    response.
- Verification boundary: focused token tests were updated but not run at the
  project owner's request; compile/static integrity passed.

#### MSG-RELATION-REVOKE-001 — conversations persist after relationship revocation

- Severity: Medium
- Status: [x] Finding confirmed; [x] policy decided; [x] closed as deliberate
  behavior; [x] superseded by the accepted global-directory policy
- Evidence: role/enrollment is checked at conversation creation; later
  open/send checks membership only.
- Product decision: [x] retain D2 conversation persistence. Messaging is now a
  system directory rather than a class-relationship feature, so losing an
  enrollment is not a revocation boundary for an existing conversation.
- Related implementation: recipient discovery and conversation creation use
  one role matrix, exclude self/inactive/locked/deleted accounts, and were
  browser-checked separately under `MSG-RECIPIENT-ROSTER-001`.

### Phase 5 focused verification (2026-07-29)

- [x] `mvn.cmd -q -DskipTests compile`
- [x] DB-free tests: `MessagingRealtimeReadTest`,
  `MessagingConversationCreationLockTest`, `LessonUiMutationContractTest`, and
  `LearningProgressToggleLockTest`.
- [x] `node --check` for `messaging.js`, `lesson-comments.js`, and `sections.js`.
- [x] `git diff --check`.
- [x] No Practice, migration, developer-password, or user-owned config/template
  changes included.
- [ ] Real-database lock behavior remains deferred by request.

### Post-PR34 audit findings

#### LEADER-SCOPE-001 — legacy policies grant LEADER global cross-department access

- Severity: Critical
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Root cause: `ClassesService.isEditableBy`, `AssignmentAccessSupport`, and
  `ClassAccessPolicy` treat every `LEADER` like a global `ADMIN`; they do not
  compare the class department with the leader's resolved department.
- Confirmed impact:
  - [x] Update/delete/reorder class, section, lesson, content, and invite data
    in another department.
  - [x] Download unpublished private lesson attachments from another department.
  - [x] List foreign classes/invite codes and view student name, email, phone,
    enrollment, and lesson-progress data.
  - [x] Create/publish/close assignments, read submissions, and grade students
    in another department.
  - [x] View hidden comments and moderate a foreign department's lesson thread.
- Architecture evidence: leader dashboard, lecturer reassignment, and Question
  Bank already resolve and enforce a department; older class tests instead pin
  the obsolete “leader can access any class” behavior.
- Remediation checklist:
  - [ ] Introduce one department-aware class access policy shared by all modules.
  - [ ] Keep `ADMIN` global, lecturer owner-scoped, and leader department-scoped.
  - [ ] Add cross-department denial tests before changing legacy positive tests.

#### STORAGE-TX-001 — object storage is mutated inside rollback-capable DB transactions

- Severity: Critical
- Status: [x] Finding confirmed; [x] design approved; [x] remediated; [x] focused verification
- Evidence paths: `LibraryService`, `LessonAttachmentsService`,
  `LessonContentTypeSwitcher`, `LessonsService`, and
  `LessonContentApiController`.
- Risk:
  - a failed DB save/activity/constraint after `store` leaves an orphan blob;
  - a DB rollback after `delete` restores a row that points to an already
    deleted object;
  - replacement can delete working media before the new reference commits.
- Remediation checklist:
  - [ ] Compensate newly stored objects on transaction rollback.
  - [ ] Defer destructive object deletion until after DB commit.
  - [ ] Cover upload, replacement, attachment/lesson deletion, and library delete.
  - [ ] Test with a fake object store and forced late repository/activity failure.

#### CLASS-CODE-RETRY-001 — collision retry runs in a rollback-only transaction

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Evidence: `ClassCreator.create()` and `InviteCodeService.insertWithRetry()`
  catch `DataIntegrityViolationException` from `saveAndFlush()` and continue in
  the same transaction/persistence context.
- Risk: Hibernate may already mark the transaction rollback-only, so a later
  unique generated code still ends in rollback or `UnexpectedRollbackException`.
- Remediation checklist:
  - [ ] Retry the whole transaction boundary, or use an atomic insert strategy.
  - [ ] Test collision-then-success for class creation, default invites, and
    invite regeneration against a real isolated database.

#### PUBLIC-VIEW-CONC-001 — public token get-or-create is not concurrency-safe

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Evidence: `PublicViewTokenService.createPublicViewUrl()` performs an unlocked
  live-token lookup followed by insert; the schema has no unique attachment
  constraint and the repository returns `Optional`.
- Risk: concurrent calls can create multiple live rows; subsequent Optional
  queries can fail with an incorrect-result-size exception until expiry.
- Additional defect: `resolve()` is `readOnly` but attempts to delete an expired
  token.
- Remediation checklist:
  - [ ] Lock the stable attachment row before live-token lookup/insert.
  - [ ] Make duplicate-row recovery deterministic.
  - [ ] Move access-time expiry deletion to a write transaction.

#### LIBRARY-BIND-DELETE-001 — library deletion races lesson binding

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] compile verification
- Evidence: library delete performs count-references then soft-delete/blob
  delete, while attachment/PDF/video bind can concurrently read the still-live
  asset and add a reference.
- Risk: both requests can succeed, leaving a live lesson reference to a deleted
  asset whose object no longer exists.
- Remediation checklist:
  - [ ] Use one pessimistic library-asset lock order for bind and delete.
  - [ ] Combine with the after-commit storage lifecycle from `STORAGE-TX-001`.

#### COMMENT-BULK-TX-001 — bulk moderation bypasses per-item transactions

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Evidence: `hideAll()`/`unhideAll()` call the same bean's transactional
  `hide()`/`unhide()` methods directly. Spring proxy transaction advice does not
  apply to self-invocation.
- Risk: comment state and moderation audit writes are not atomic per item,
  despite the documented contract; a late audit failure can leave an unaudited
  state change.
- Remediation checklist:
  - [ ] Move single-item moderation to a separate transactional bean or explicit
    `REQUIRES_NEW` transaction template.
  - [ ] Force the audit insert to fail and prove the comment transition rolls back.

#### COMMENT-MUTATION-CONC-001 — comment mutations use unlocked stale state

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] focused verification
- Risk: concurrent hide calls can write duplicate transition audits; hide,
  unhide, edit, and delete can overwrite one another based on stale state.
- Remediation checklist:
  - [ ] Lock the owner-scoped comment row for every mutation.
  - [ ] Audit only a real state transition and verify concurrent idempotence.

#### QB-IMPORT-STALE-002 — old Question Bank preview can replace the current session

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [x] syntax verification
- Evidence: `question-bank-import.js` has no abort/request generation; every
  upload completion overwrites global preview rows and `sessionId`.
- Reproduction: start A, reset/select B, resolve B then A; Confirm submits A's
  session while the interaction is expected to represent B.
- Remediation checklist:
  - [ ] Apply the generation/AbortController pattern from `IMPORT-STALE-001`.
  - [ ] Prove only the latest preview can render or enable Confirm.

#### PROGRESS-STALE-001 — progress detail response can mismatch the selected student

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] syntax verification
- Evidence: `class-progress.js` renders every delayed `loadStudent()` response
  while title/selection use newer global state.
- Remediation: [ ] Invalidate pending loads on selection/close and render only
  the current student generation.

#### LIBRARY-PICKER-STALE-001 — an old picker request can cross modal generations

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] syntax verification
- Evidence: `library-picker.js` reuses global modal state; `close()` clears only
  the callback, while old GET callbacks can populate a later open with another
  asset kind and its new callback.
- Remediation: [ ] Bind results to open generation, kind, query, and page; abort
  or ignore all older callbacks.

#### CLONE-WIZARD-STALE-001 — section results can belong to the previous class

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] syntax verification
- Evidence: `lesson-clone-wizard.js` reads class id when starting the request
  but renders unconditionally after Back/select/Next state changes.
- Risk: Finish sends current class B with stale section A and the server rejects
  the workflow.
- Remediation: [ ] Invalidate section loads when class selection changes and
  render only a response bound to the current class.

#### TEST-MONITOR-STALE-001 — overlapping polls can roll monitor state backward

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated; [x] syntax verification
- Evidence: `test-monitor.js` uses `setInterval` without an in-flight or request
  generation guard; every response overwrites counts and rows.
- Remediation: [ ] Serialize polls or discard any response older than the last
  applied request; invalidate outstanding work on teardown.

### Phase 4 focused verification (2026-07-29)

- [x] `mvn.cmd -q -DskipTests compile`
- [x] Focused unit tests: class/leader policy, invite preflight, attachment
  authorization, comment moderation, and storage transaction lifecycle.
- [x] Public-view attachment-lock/duplicate-token unit tests.
- [x] `node --check` for all five stale-response JavaScript fixes.
- [x] `git diff --check`
- [x] No `practice` source, configuration, schema, or test path changed.
- [ ] Database-backed concurrency/integration tests remain deferred by request.

### Phase 6 findings and remediation (2026-07-29)

- [x] `PERMISSION-OVERRIDE-CONC-001`: lock the affected user before override
  mutation and evict permission caches only after successful commit.
- [x] `LESSON-APPEND-CONC-001`: lock the stable class/section parent before
  computing `MAX(display_order) + 1` for section, lesson, and template clones.
- [x] `LIBRARY-ATTACH-WIZARD-STALE-001`: bind async results and replacement
  preflight to a request generation and immutable target snapshot.
- [x] `FLASHCARD-DECK-DUPE-001`: lock submit controls during async persistence.
- [x] `LESSON-FORM-DUPE-001`: lock submit controls through confirm/upload/bind
  and release them only on abort/failure or guarded native resubmit.
- [x] `COMMENT-DELETE-SCOPE-001`: use canonical moderator policy for deletion
  while preserving author deletion.
- [x] `TEST-LEADER-SCOPE-001`: apply the canonical role/class policy to direct
  test access, global and class listings, class picker, save, and bank insertion.
- [x] `EXAM-IMAGE-ORPHAN-001`: add owner-bound staged uploads, transactional
  claim/compensation, durable URL rewriting, and scheduled expiry cleanup.
- [x] `GENERIC-TOGGLE-CONC-001`: serialize Category, Department, AI Provider,
  AI System Prompt, and department Question Bank category parity toggles with
  pessimistic row locks.
- [x] `AI-PROVIDER-ORDER-CONC-001`: lock a stable global settings anchor before
  reading `MAX(display_order)` and inserting, including the empty-provider case.

Verification:

- [x] Maven compile.
- [x] Focused DB-free backend and source-contract tests.
- [x] JavaScript syntax checks for all three changed frontend files.
- [x] `git diff --check`.
- [x] No Practice, migration, developer-password, or user-owned config/template
  changes included.
- [ ] Real-database lock behavior remains deferred by request.

### Phase 6 follow-up implementation detail (2026-07-29)

#### TEST-LEADER-SCOPE-001 — canonical role scope now covers test management

- Severity: High
- Status: [x] Finding confirmed; [x] remediated; [ ] focused/full-suite tests
- Remediation:
  - [x] Resolve the actor's current persisted role and reuse
    `ClassRoleAccessPolicy` rather than treating creator/class ownership as a
    substitute for ADMIN/LEADER policy.
  - [x] Keep ADMIN global only for non-Practice test management, limit LEADER
    to classes in their resolved department, and keep LECTURER on owned/created
    tests and classes.
  - [x] Apply the same boundary to the global list, per-class list, class
    picker, direct get/update, save, and question-bank insertion.
  - [x] Keep student-owned Practice tests isolated from elevated management
    rules.
- Verification boundary: `TestAccessResolverLeaderScopeTest` was added but not
  run per request; Java compile and static diff checks passed.

#### EXAM-IMAGE-ORPHAN-001 — owner-bound staged image lifecycle

- Severity: Medium
- Status: [x] Finding confirmed; [x] design approved; [x] remediated;
  [x] browser lifecycle verified; [ ] focused/full-suite tests
- Remediation:
  - [x] Bind each uploaded filename to the authenticated owner and creation
    timestamp, and keep it under a flat `exams/staged-*` key compatible with
    the existing public upload route.
  - [x] Sanitize rich HTML before claim, inspect only canonical relative
    `img[src]` values, and reject foreign-host, percent-encoded, non-image, or
    residual staged references.
  - [x] On transactional exam save, reject malformed, missing, expired,
    future-dated, or other-owner staged URLs; copy each valid object to a fresh
    durable key and rewrite exam/question/explanation/option HTML. Plain
    `mediaUrl` is intentionally not interpreted as rich HTML.
  - [x] Deduplicate repeated staged URLs within one save, delete the durable
    copy on rollback, and delete the staged source only after commit.
  - [x] Add storage-prefix listing to local, R2, and dual-read backends and
    scheduled cleanup for abandoned staged images older than 24 hours.
  - [x] Propagate/verify backend delete failure, retry failed cleanup in memory,
    and serve staged responses with `Cache-Control: private, no-store`.
- Browser evidence:
  - Test `#97` was created with a staged source
    `/uploads/exams/staged-3-1785321814407-….png`.
  - Reopening edit after save showed a durable source
    `/uploads/exams/d6c2e725-96f7-415b-85b5-82194816b250.png`; the staged URL
    was no longer persisted in the form.
- Residual operational boundary: the retry set is process-local. A JVM crash
  between a failed object delete and the next cleanup loses that retry entry;
  durable recovery needs an outbox/queue table and is deferred with the
  migration freeze.
- Verification boundary: `ExamImageStorageServiceTest` and storage contract
  coverage were added/updated but not run per request; Java compile and static
  diff checks passed.

#### GENERIC-TOGGLE-CONC-001 — parity toggles are serialized

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated;
  [ ] real-database concurrency/full-suite verification
- Remediation:
  - [x] Add `PESSIMISTIC_WRITE` lookup methods for Admin Category, Department,
    AI Provider, AI System Prompt, and department Question Bank category rows.
  - [x] Make every read-invert-save toggle use its locked lookup while retaining
    parity semantics: two serialized toggles cancel.
- Verification boundary: contract/unit tests were added or updated but not run
  per request; multi-connection lock behavior remains unchecked.

#### AI-PROVIDER-ORDER-CONC-001 — first and later inserts share one lock

- Severity: Medium
- Status: [x] Finding confirmed; [x] remediated;
  [ ] real-database concurrency/full-suite verification
- Remediation:
  - [x] Lock the stable `system_settings.ai.provider` seed row before reading
    provider order or inserting.
  - [x] Fail closed when the anchor is absent, instead of silently reverting to
    an unsafe `MAX(display_order) + 1` race.
  - [x] Preserve the existing append-only display-order behavior while covering
    concurrent first inserts into an empty provider table.
- Verification boundary: `AiProviderOrderingConcurrencyContractTest` was
  added but not run per request; multi-connection lock behavior remains
  unchecked.

Current follow-up verification:

- [x] `.\mvnw.cmd -q -DskipTests compile` completed successfully.
- [x] `git diff --check` completed successfully.
- [x] No migration, demo-password, or `/practice` source/config/test path was
  changed by this follow-up group.
- [ ] Focused tests and the full suite were not run, per project-owner request.
- [ ] Real-database multi-connection concurrency behavior remains unchecked.

### New issue template

#### NEW-YYYYMMDD-NNN — concise title

- Severity:
- Status: [ ] Confirmed; [ ] remediated; [ ] verified
- Reporter:
- Related issue IDs:
- Evidence paths/call sites:
- Reproduction:
- Expected behavior:
- Actual behavior:
- Security/data/Practice impact:
- Decision:
- Remediation checklist:
- Verification:
- Owner:
- Commit(s):
- PR:

## 14. Handoff protocol

Every agent taking or releasing work must follow this sequence.

### Before editing

- [ ] Read this document completely.
- [ ] Read current `git status`, branch, HEAD, and relevant diffs.
- [ ] Confirm the intended issue ID and claim it with owner/name.
- [ ] Check whether another agent is editing overlapping files.
- [ ] Re-read the Practice freeze and reject list.
- [ ] Capture baseline tests relevant to the issue.

### While editing

- [ ] Touch only files required by the claimed issue.
- [ ] Preserve unrelated user/agent changes in the shared worktree.
- [ ] Add or adapt tests with the implementation.
- [ ] Record new findings under a new stable ID.
- [ ] Do not rename/reuse migrations.
- [ ] Do not broaden authorization, public upload access, AI provider scope, or
  storage scope without a separately approved issue.

### Before handoff

- [ ] Rebase the issue description against the actual current diff.
- [ ] Run focused tests and record exact commands/results.
- [ ] Run `git diff --check`.
- [ ] List every changed file.
- [ ] State explicitly whether any Practice path/config changed.
- [ ] Leave implementation unchecked if full required verification is pending.
- [ ] Fill owner, evidence, commit, and PR fields.
- [ ] Notify the next agent of conflicts, assumptions, failures, and unverified
  behavior.

### Before commit

- [ ] Stage only the files for one logical ledger slot.
- [ ] Re-inspect the staged diff.
- [ ] Include stable issue IDs in the commit message/body.
- [ ] Record the real commit hash in this document after commit.
- [ ] Never mark tests, review, push, PR, or merge complete before confirmation.

### Before merge

- [ ] All Critical and High issues have an explicit disposition.
- [ ] All required gates are checked with evidence.
- [ ] Practice freeze reviewer signs off.
- [ ] Migration and security reviewers sign off.
- [ ] Main is incorporated and conflicts are semantically re-audited.
- [ ] PR head SHA matches the reviewed SHA.

## 15. Final acceptance statement

The integration is acceptable only when all of the following are true:

- [x] Accepted ULP behavior is implemented under KSH naming and contracts.
- [x] Rejected ULP behavior has not displaced stronger KSH behavior.
- [x] AI question drafts are durable and atomically consumed.
- [x] Notification email uses the durable outbox and is operationally
  recoverable.
- [x] Upload authorization remains fail-closed.
- [x] LEADER parity is preserved.
- [ ] Flyway history is valid from empty and existing schemas.
- [ ] Full automated and manual gates pass.
- [x] `/practice` production behavior, AI configuration, storage configuration, schema,
  and tests remain intact.
- [ ] Commits, PR, reviews, merge SHA, and post-merge evidence are recorded.
