# ULP → KSH Integration Incident and Audit Checklist

> Living control document for the selective integration of ULP commit
> `32d394c5f6d0818955455bc01f20633b66d594b5` into KoreanStudyHub (KSH).
> This document records evidence, immutable decisions, implementation work,
> verification gates, commit ownership, and PR handoff. It is not evidence that
> an unchecked implementation has passed.

## 1. Document control

| Field | Value |
|---|---|
| Audit status | Local implementation complete; full automated suite green; PR #29 open, manual UAT and required approval pending |
| KSH audit baseline | `2549438c1a327b6932dc78d5284d7feaf5daf628` |
| Working branch observed | `codex/ulp-ksh-integration-hardening` |
| ULP reference | `https://github.com/dikhamchua/ulp/tree/32d394c5f6d0818955455bc01f20633b66d594b5` |
| ULP local snapshot used | `C:\Users\Admin\AppData\Local\Temp\ksh-ulp-32d394c5-20260729\ulp-32d394c5f6d0818955455bc01f20633b66d594b5` |
| KSH root | `D:\Downloads\ksh` |
| Comparison method | Path inventory, normalized namespace/branding comparison, semantic review, call-site tracing, and test-contract review |
| Last updated | 2026-07-29, Asia/Bangkok |
| Primary constraint | Preserve the existing `/practice` foundation, AI configuration, and storage configuration |

The working tree is shared by parallel agents. File presence or a local diff
means “implementation in progress,” not “verified.” Re-read `git status`,
`git diff`, and this document before taking ownership of any item.

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
| TEST-ISO-001 | High | Tests currently use the default developer MySQL schema instead of an isolated test profile | [x] | [ ] External branch pending |

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

- [ ] Run `PublicUploadsControllerTest`.
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

- [ ] Run `JoinClassServiceTest`.
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
- [x] Delete expired rows opportunistically in bounded batches of 500; a
  dedicated retention sweep remains open under `NEW-20260729-006`.
- [x] Add two-thread confirmation coverage proving one winner.
- [x] Use a database repository/pessimistic lock compatible with restart and
  multiple application instances.
- [x] Repeat/expired/foreign confirmation returns the same non-disclosing
  expired-session bad request.

**Tracking**

- Owner:
- Verification evidence:
- Commit(s):
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
- [ ] Metrics cover pending, processing, retry, sent, failed, expired leases,
  latency, and oldest pending age.
- [ ] Retention/cleanup for sent and failed rows is defined.
- [ ] Remaining tests: explicit restart recovery, large backlog fairness, and
  notification deletion. Commit/rollback, retry, exhaustion, lease collision,
  lease expiry, duplicate worker claim, worker isolation, and delivery outcome
  are covered.
- [x] Verify `LESSON_PUBLISHED` and `ASSIGNMENT_PUBLISHED` behavior against the
  approved KSH product policy.

**Tracking**

- Owner: Codex root
- Operational runbook: inspect `mail_outbox_jobs` by status/`available_at`;
  correct SMTP configuration; move a deliberately reviewed `FAILED` row to
  `RETRY` with a new `available_at` only through a future admin/runbook action.
  Do not bulk-update rows or promise exactly-once SMTP.
- Verification evidence: focused mail tests plus
  `MailOutboxRepositoryIntegrationTest` — 3 tests, including rollback and two
  concurrent workers, all green.
- Commit(s):
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
| Draft/ready | Ready; GitHub reports required approval pending |
| Head SHA reviewed | `47f7ffea98ed3e2b703268a945c78fbe13c3f7a0` (code/evidence head before this documentation-only C22) |
| CI run | GitHub reports 0 configured checks; local full-suite evidence is Gate C |
| Security reviewer |  |
| Migration reviewer |  |
| Practice freeze reviewer |  |
| Final approver |  |
| Merge commit |  |
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
- Status: [x] Confirmed; [ ] remediated on `main`; [x] risk documented
- Evidence:
  - No `src/test/resources` directory or active isolated test profile exists.
  - Spring test logs show the default profile and
    `jdbc:mysql://localhost:3306/ksh_dba`.
- Risk: integration tests may use default application datasource/Flyway
  settings and mutate a developer database.
- Disposition: pre-existing repository-wide infrastructure gap, not caused by
  the ULP integration. New committed-row concurrency tests delete only their
  exact rows in `finally`; other integration tests rely on transaction rollback.
- Owner: external contributor on a not-yet-merged branch (per project owner);
  this integration intentionally does not duplicate that work.
- Confirmation steps:
  - [x] Confirm current datasource resolution from Spring/Flyway logs.
  - [ ] Prove tests use an isolated disposable database and isolated upload
    directories.
  - [ ] Add an explicit test profile if isolation is not already guaranteed by
    the build environment.
- Test/evidence: repeated local runs expose the default `ksh_dba` URL.
  The unconstrained full suite reached 2,438 tests but failed with 54
  connection errors after MySQL reported `Too many connections`; the same
  source state passed all 2,439 tests when the process-local Hikari pool was
  capped at 2 and the Spring test context cache at 8. This is mitigation
  evidence, not proof of database isolation.
- Commit:
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

### NEW-20260729-006 — AI draft cleanup is opportunistic

- Severity: Medium
- Status: [x] Finding confirmed; [ ] remediated; [x] bounded request cleanup verified
- Evidence: generation deletes at most 500 expired sessions before saving a new
  preview. If generation stops permanently, expired/consumed JSON rows remain.
- Current safeguard: 10-minute authorization validity, no document bytes stored,
  bounded cleanup, atomic consume, and no Practice scheduler changes.
- Remediation:
  - [ ] Add an isolated scheduled retention sweep or database event.
  - [ ] Define operational age/count metrics and an explicit retention SLA.
- Owner: unassigned
- Commit:
- PR:

### NEW-20260729-007 — mail outbox retention and metrics gap

- Severity: Medium
- Status: [x] Finding confirmed; [ ] remediated; [x] delivery safety documented
- Evidence: durable states, leases, retries, and dead-letter rows exist, but
  sent/failed rows have no cleanup job and there are no backlog/age metrics.
- Risk: recipient addresses and message snapshots persist indefinitely and an
  operator cannot alert on backlog using application metrics.
- Remediation:
  - [ ] Define retention for `SENT` and `FAILED` rows.
  - [ ] Add pending/retry/failed/oldest-age metrics and an operator dashboard.
- Owner: unassigned
- Commit:
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
