# KoreanStudyHub non-Practice product/schema audit

Status: audit baseline complete; implementation decisions approved and tracked below. Date: 2026-08-03 (Asia/Ho_Chi_Minh).

## 1. Baseline, method, and hard boundary

- Worktree: dedicated detached worktree `/Users/toanlamsaoduocc/.codex/worktrees/661b/ksh`.
- Baseline after `git fetch --prune origin`: `HEAD = origin/main = 3d38a2f0efff22e2257c46079ca1202bae556872`.
- Initial tree was clean. No `AGENTS.md` was found. Evidence was collected with `rg`, migration/source reads, Git, and a read-only DB connection attempt.
- The configured local MySQL endpoint rejected `root`/the documented development password (`ERROR 1045`). No database statement ran and no shared/disposable database was mutated. Consequently, live row/byte counts and `flyway_schema_history` are **not evidenced** and are a blocker to migration-rewrite approval.

### Protected Practice inventory (do not edit in this program)

Protection is dependency-based, not merely name-based.

- Schema: every `practice_*` table; `question_explanation_artifacts`, `question_version_explanation_bindings`, and `question_explanation_generation_tasks`; `storage_profiles`; `lecturer_assets`; Practice-owned columns/FKs on shared `users`; migrations V25–V39, V44, V55–V56, V61–V62, V69–V76, V83–V85, V87 and their checksum manifests.
- Code: `com.ksh.features.practice/**`; Practice entities/repositories; Practice AI/control-plane/storage settings; Practice branches of shared storage, security, preferences, and `lecturer_assets` code.
- Web/static/tests: `templates/practice/**`, Practice result/detail/manage pages, `static/**/practice*`, and all `src/test/**/practice/**` or Practice contract/migration tests.
- Shared hazard: `lecturer_assets` was introduced in V25 and is referenced by later Practice FKs. It must not be reused, renamed, or removed for the new Library model. `library_assets` (V42) is the non-Practice asset table.
- Name collision rule: non-Practice `categories`, `question_bank_categories`, admin/leader category pages may be removed; no Practice category/catalog concept, table, route, template, static, or test may be touched.

## 2. Findings and decisions

### Critical findings

1. **Department contradiction:** `departments` currently represents “bộ môn” and is not removable in isolation. It owns `users.department_id`, `classes.department_id`, question-bank scope, leader resolution/approval/report/assignment, and two audit families. Product language says remove department but retain exactly the subject codes. Recommended resolution is to replace the organizational department concept with a minimal subject catalog, not erase the concept.
2. **Leader assignment is destructive ownership transfer:** `/leader/assign/{classId}` calls `reassignLecturer`, changing `classes.lecturer_id`. Lecturer lists, edit gates, join approval, notifications and messaging all treat that column as owner. `created_by` does not preserve access. With no co-lecturer relation, the correct no-new-table decision is remove `/leader/assign` and its UI. Keeping co-lecturers requires one join table and explicit permissions.
3. **Class lifecycle mismatch:** database/code allow `DRAFT, UPCOMING, ACTIVE, COMPLETED, CANCELLED, REJECTED`; approval maps DRAFT to UPCOMING. Target permits only `DRAFT, ACTIVE, ARCHIVED`. `start_date` is editable and is not creation date. No evidenced scheduled auto-archive worker exists.
4. **Join mismatch:** current discovery is invite CODE/LINK; it provisions two tokens per class. The desired flow is searchable ACTIVE-class discovery then PENDING enrollment. `enrollments` can support this, but `invite_code_id`, `joined_via` CODE/LINK, generators, settings UI, join form, repository and tests become removable.
5. **Library mismatch:** current `/lecturer/library` manages owner-scoped loose `library_assets`, plus `lesson_templates`; `/lecturer/classes/{id}/lessons` owns sections/lessons and all create/edit/upload/publish actions. With the approved one-new-table ceiling already consumed by `class_co_lecturers`, the minimal target reuses `lesson_templates` as subject → chapter → lesson → materials and snapshots a published copy into existing class-owned `sections`/`lessons`. This avoids a second distribution table; it intentionally provides no live two-way sync/revocation identity.
6. **Question bank mismatch:** V46 requires both `department_id` and `category_id`; lecturer/leader controllers, import and bulk actions are category-oriented. Tests themselves are already class-scoped (`tests.class_id`), so “create inside class” can remain; importing/copying approved bank questions into a class test is preferable to coupling runtime test questions to bank rows.
7. **Comments mismatch:** `/api/lessons/{lessonId}/comments` is student-facing and includes moderation, but the requested product removes class lesson comments. Remove only after verifying all callers in lesson templates/JS and non-comment consumers; do not touch Practice feedback/explanations.

### Resolving “remove department, retain bộ môn”

| Model | Shape | Tables | Assessment |
|---|---|---:|---|
| A — rename/re-scope (recommended) | `subjects(id, code, name, leader_user_id, active, timestamps)`; class/user/QB point to subject | Reuse `departments` via a fresh-schema rename or semantic rewrite | Minimal, preserves leader scope, supports KOR311/KOR321/KOR411, avoids a parallel table. “Department” admin/report semantics are removed. |
| B — immutable code vocabulary | `subject_code` VARCHAR stored on classes/users/QB/library and application enum/seed validation | No catalog table | Few tables but duplicates identity, weak FK integrity, awkward leader-to-multiple-subject assignment, and risky code renames. Not recommended. |
| C — new subject catalog | Add `subjects`, migrate all department FKs, remove departments | +1 table | Cleanest naming for upgrades, but violates the “avoid new table” preference when the existing row shape is reusable. Use only if applied migrations prohibit rewriting/renaming V1/V40/V46. |

Recommended default: Model A. Seed stable unique codes `KOR311`, `KOR321`, `KOR411`; class creation requires `subject_id`; display/filter by `subjects.code`. A class has exactly one subject. Users need not be restricted to exactly one subject unless product owners explicitly require it; current `users.department_id` is an authorization assumption that must be decided separately.

## 3. Current and target schema map

### Impacted table disposition

| Current table/family | Decision | Exact reason and dependency impact |
|---|---|---|
| `departments` | RENAME/REPURPOSE → `subjects` | Row shape already supplies code/name/leader/active. Update users, classes, QB, leader services, admin UI, permissions and audit naming. |
| `courses`, `course_categories`, `categories` | REMOVE | Classes stopped referencing courses in V7. Remaining category admin/tests and seed/FKs are legacy. Verify no flashcard/discovery caller before deletion. |
| `activity_courses` | REMOVE | Parent is removed and no production writer/entity was found. |
| `activity_departments` | REMOVE | V3 duplicate has no entity, repository, writer, reader, route or template caller. |
| `department_activities` | RENAME → `subject_activities` | This is not dead: `DepartmentService` writes create/update/toggle/leader events through `SubjectAuditWriter`; `DepartmentQueryService.listActivities` reads it for the paged “Lịch sử cập nhật” tab in `admin/departments-form.html`. Rename table/column/entity/repository/writer/DTO vocabulary to subject; do not keep a table named `department_activities`. |
| `activity_sections`, `activity_lessons`, `activity_classes`, `activity_tests` | MERGE | These have active writer/entity code. Uniform shape supports one append-only audit table; index by `(entity_type, entity_id, created_at)` and `(type, created_at)`. |
| other V3 `activity_*` (`enrollments`, assignments, submissions, users, comments, content_versions, flashcard_decks) | REMOVE | No matching application writer/entity/reader exists. Comments were removed in V94; V96 removes the other six. `user_activities` and `permission_activities` are separate live audit implementations and remain protected. |
| proposed `entity_activities` | ADD only if audit retention is required | Columns: id, entity_type, entity_id, event_type, description, metadata, actor_id, created_at. No polymorphic FK. Ownership by a single audit service; monthly/age retention; indexes above. This is one replacement for up to 15 fragmented logs. |
| `classes` | KEEP/RESHAPE | Replace `department_id` with `subject_id`; drop random class `code` if it is only an invite identifier; statuses exactly three; immutable DB `created_at`; optional `end_date`; approval DRAFT→ACTIVE; end-date worker ACTIVE→ARCHIVED. Keep `created_by` and owner lecturer. |
| `class_invite_codes` | REMOVE | CODE/LINK flow is explicitly removed. Drop enrollment FK first and remove provisioning/backfill/UI/tests. |
| `enrollments` | KEEP/RESHAPE | Supports PENDING→ACTIVE/REJECTED teacher approval. Remove invite FK; normalize `joined_via` to REQUEST/MANUAL/IMPORT or remove if unused. Student catalog query must show ACTIVE non-deleted non-archived classes only. |
| `sections`, `lessons` | KEEP as distributed snapshots | Existing class-owned rows remain the read/progress delivery model. Only Library distribution creates published snapshots; remove class authoring routes. |
| `lesson_attachments` | KEEP/RESHAPE | Materials under lessons. Prefer references to non-Practice `library_assets`; preserve download authorization. |
| `lesson_templates`, `lesson_template_attachments` | KEEP/RESHAPE as canonical Library lessons | Add subject/chapter/order to templates; attachments continue referencing non-Practice `library_assets`. This becomes the only authoring source. |
| `library_assets` | KEEP/RESHAPE | Non-Practice upload storage, but assets must be created/attached within the subject hierarchy, not presented as an unowned loose-upload product. |
| `lesson_class_distributions` | DO NOT ADD under approved table ceiling | Distribution snapshots canonical Library content into existing class sections/lessons. Trade-off: no canonical distribution identity, withdrawal or live update; re-distribution of the same title is rejected to prevent silent duplicates. |
| `learning_progress` | KEEP | Continue per user/lesson; access must require ACTIVE enrollment plus distribution. Decide how progress behaves after archive/revocation. |
| `comments`, `comment_moderation`, `activity_comments` | REMOVE for class lessons | Remove controller/service/repository/template fragments/static/tests only after caller scan. Do not confuse with Practice editorial feedback. |
| `question_bank_categories` | REMOVE | Product scopes by subject code. |
| `question_bank_items` | KEEP/RESHAPE | `subject_id NOT NULL`; remove category FK/index; preserve contributor/reviewer workflow. |
| `question_bank_options` | KEEP | Child of bank item. |
| `tests`, `questions`, `question_options`, attempts/responses | KEEP/RESHAPE | Non-Practice tests remain class-scoped; class supplies subject. Bank-to-test creation copies approved compatible questions. Add direct provenance column only if traceability is required; no new table needed. |
| all protected inventory above | PROTECTED_PRACTICE | Explicit exclusion, including shared `lecturer_assets`. |

### Current → target relationships

Current: `departments → users/classes/question_bank_categories/question_bank_items`; `classes → sections → lessons → attachments`; `classes → tests`; invite tokens → enrollments; loose library assets/templates are cloned/attached into class lessons.

Target: physical `departments` acts as the subject catalog; `subjects → classes`, `subjects → lesson_templates(chapter) → template materials`, and Library distribution snapshots into `classes → sections → published lessons → attachments`; `subjects → question_bank_items → options`; `classes → enrollments/tests`; tests copy questions from the same-subject bank.

## 4. Activity size/usage audit

Source usage proves writers only for `activity_classes`, `activity_sections`, `activity_lessons`, and `activity_tests`. V3 creates 13 near-identical tables; V10 adds `user_activities`, V41 adds a second `department_activities`, and V49 adds `permission_activities`. The split creates repeated repositories/indexes/retention work and cannot answer cross-domain actor timelines efficiently. Merge is appropriate for audit events because write shape/retention/ownership are uniform; do **not** merge business histories such as attempts, progress, submissions, or notifications.

Required read-only production/disposable query before choosing retention or migration batching:

```sql
SELECT table_name, table_rows,
       data_length, index_length, data_free
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND (table_name LIKE 'activity\_%' ESCAPE '\\'
       OR table_name IN ('user_activities','department_activities','permission_activities'))
ORDER BY data_length + index_length DESC;

SELECT 'activity_classes' t, COUNT(*) n, MIN(created_at) oldest, MAX(created_at) newest FROM activity_classes
UNION ALL SELECT 'activity_sections',COUNT(*),MIN(created_at),MAX(created_at) FROM activity_sections
UNION ALL SELECT 'activity_lessons',COUNT(*),MIN(created_at),MAX(created_at) FROM activity_lessons
UNION ALL SELECT 'activity_tests',COUNT(*),MIN(created_at),MAX(created_at) FROM activity_tests;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) columns
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name LIKE 'activity\_%' ESCAPE '\\'
GROUP BY table_name,index_name;
```

Also capture event-type cardinality and 30/90/365-day counts per active table. Recommended default retention: product audit 365 days, security/user/permission audit longer and separate access policy; confirm legal/product requirements first.

## 5. Route/UI ownership matrix

| Surface now | Current owner/dependencies | Target and dead-reference risk |
|---|---|---|
| `/lecturer/classes/new` | ClassesController/service/entity; `classes/form.html`, `classes.css/js`; random class code + date fields | Required native/component subject select; created date read-only/display-only; optional end date. Remove code/invite assumptions. Validate labels/errors/focus. |
| `/lecturer/library` | LibraryController + asset service/repo; `library/index.html`, `library.css/js`, picker/attach wizard | Becomes subject/section/lesson/material authoring root. Remove loose upload list and class-section target APIs after new distribution caller lands. |
| `/lecturer/classes/{id}/lessons` | LessonsTabController plus section/lesson lifecycle/content/attachment/clone controllers; class detail template and many lesson JS/CSS files | Read-only distributed content for lecturer. Move create/edit/publish/upload to Library. Remove mutations only after new Library owns them. |
| `/lecturer/classes/{id}/tests` | class detail tests + LecturerTestController/services/templates/static | Keep class-local create. Add same-subject bank picker/distribute action. |
| `/lecturer/question-bank` | LecturerQuestionBankController/import/service; list/form/detail templates + `question-bank*.js/css` | Replace category picker/filter with required subject code and same-subject filtering. |
| `/leader/question-bank` and `/categories/**` | Leader controllers incl. bulk category endpoints; manage/category-detail/review | Keep subject-scoped review; delete category CRUD/bulk-by-category routes. Update permissions seeded by V46. |
| `/leader/approvals` | LeaderController + approval service; department-scoped DRAFT query | Keep, scope by subject leadership; approve directly to ACTIVE. |
| `/leader/assign` | Leader controller/service; `leader/assign.html`, leader CSS/JS | Recommended REMOVE. Current behavior transfers owner. Keeping correct co-lecturer needs M:N table and broad authorization updates. |
| `/my/classes` and `/my/classes/join` | student controller/service; my-classes/join-class templates; invite JS/CSS and token validator | `/my/classes` gains ACTIVE catalog/filter by class name/subject code plus request button and separate memberships. Remove join-token page/route/link. Never return DRAFT/ARCHIVED. Archived remains visible to teacher, not student. |
| `/admin/departments` | admin controller/service/repositories; two templates + CSS/JS | Replace with minimal `/admin/subjects` only if subject CRUD is wanted; otherwise seed-only immutable catalog. Remove old nav/callers. |
| `/admin/categories` | admin category stack/templates/static | REMOVE after navigation and test callers are clean. |
| lesson comments API/UI | LessonCommentsApiController and comment service/repositories; lesson-comment JS/CSS | REMOVE from class lessons. Verify API callers and moderation links first. |

Known dead/deprecated evidence: `templates/student/lesson-detail.html` declares itself deprecated and unrouted. Confirm with route/template-return and link scans, then remove in an implementation commit. Legacy question-bank redirect routes still have callers only for compatibility; decide whether bookmarked URLs matter before removal. `library attach targets` endpoints become dead after class authoring is removed.

UI target: use the login page's light ocean-blue tokens as the KSH base; one responsive shell and consistent cards/tables; native `<select>` or the established accessible component (never invented tags); explicit `<label for>`, visible `:focus-visible`, WCAG AA contrast, 44px targets, inline validation, mobile stacked filters/actions, `min-width:0`, wrapping long codes/names and scroll containers only for true data tables. CI should parse rendered HTML and run axe/browser checks at mobile/tablet/desktop widths. No malformed custom HTML element was established by this source-only scan; make HTML validation a gate rather than claiming absence.

## 6. Migration strategy and checksum decision

### Current evidence

- Flyway has `validate-on-migrate=true`, `clean-disabled=true`, and no baseline-on-migrate. Editing an applied migration will cause checksum validation failure.
- Repository language and prior audit evidence show local schemas have been upgraded through at least V60; V48 explicitly calls itself a forward-only upgrade. This disproves any blanket assertion that old migrations are unapplied.
- The task says non-production, but source text alone cannot prove that no deployed/shared database or checksum obligation exists. The attempted local DB login failed, so applied versions/checksums could not be queried.

Therefore **fresh-only rewrite eligibility is NOT YET APPROVED**. Before implementation, collect from every developer/demo/CI/shared DB:

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Obtain an explicit owner decision that every affected database may be destroyed/recreated and no external consumer depends on its data. Otherwise use forward migrations.

### Exact existing migrations whose content would be candidates in a fresh-only rewrite

| Migration | Concern | SHA-256 at baseline |
|---|---|---|
| V1 | departments/categories/courses/classes/invites/enrollments/content/comments/tests | `64c75c28f72f0bea75c538fcaafaa2048588558f81abdfa9c2c0f4a87f74c93f` |
| V2 | department/category/course seeds/permissions | `c7273031a5be2f3357a8af88a8029102c74e8b49e79822dfad88de8a1af1c5b0` |
| V3 | 13 activity tables | `e12f03e5fd541ab7ef0603e73b2f8c613cdf404651d286ac070e00a335bf8a3c` |
| V7 | class course removal/random code | `7ee90c8798dd05d3e7234d6e9e91e13d615fd4334b5a17d6343c6645d06ccb6d` |
| V10/V12 | user audit/invite seeds | `b4b52e3d…f7b95` / `fcfbf7ea…7019b` |
| V13–V16 | class-owned section/lesson/material schema | `562ca159…39ec`, `0367bdc5…ade8b`, `c2e47a99…f4a2`, `f37861c0…915e` |
| V24 | PENDING/REJECTED enrollment | `6a4c62a9…3397` |
| V40/V41 | class department FK/duplicate department audit | `ff4552d0…f09`, `688ab37a…4370` |
| V42/V45 | library assets/templates | `e095af19…126`, `44799073…b177` |
| V46/V47/V48 | department/category QB and leader naming | `0beba34b…026`, `f4cef8ad…a4d5`, `d40444fc…4543` |
| V80 | approval statuses | `6640bec6…35fa` |

Full hashes are reproducible with `sha256sum src/main/resources/db/migration/{V1...,V80...}` and were captured during this audit. Any byte edit changes Flyway checksum and also requires updating any repository checksum manifest that covers the version. Do not edit Practice migrations or Practice checksum manifests.

Fresh rewrite implication: an empty DB gets the target directly and removed tables never exist; all disposable DBs must be recreated, not upgraded. Upgrade implication: deployed schema/data require ordered FK-safe ALTER/COPY/validation/drop migrations and rollback is backup/restore or compensating forward migration. MySQL DDL is not reliably transactional; rehearse from a snapshot. Rollback for fresh rewrite is Git revert + database recreation, not Flyway down migration.

## 7. Phased implementation plan (atomic commits)

1. **Decision/evidence gate (docs only):** capture all Flyway histories, DB size queries, deployment inventory, data-reset sign-off, user↔subject cardinality, library distribution revocation/version behavior, and co-lecturer decision. Gate: no Practice diff; baseline hashes recorded.
2. **Subject model + seed:** one commit for `departments`→subjects semantic change and KOR311/KOR321/KOR411 deterministic seeds; next commit for class required subject selection/filter. Tests: uniqueness/FK, required create validation, authorization and exact seed codes.
3. **Class lifecycle:** statuses only DRAFT/ACTIVE/ARCHIVED, approval mapping, immutable created date, optional end date and idempotent scheduled archive. Tests: boundary timezone/date, archived teacher visibility/student exclusion, DRAFT exclusion, concurrency.
4. **Join discovery:** ACTIVE catalog query/search/page, request creates/reopens PENDING, teacher approve/reject. Then atomically remove invite routes/services/entities/table/UI/static/tests and obsolete columns. Tests: information leakage, ownership, capacity locking, duplicate requests.
5. **Leader co-lecturer:** approved one new table `class_co_lecturers`; leader assign adds/removes co-lecturer membership only and never changes `classes.lecturer_id` or `created_by`.
6. **Library hierarchy:** reuse `lesson_templates` as canonical subject lessons, snapshot to existing same-subject class sections/lessons, make class views read-only, and remove class authoring/clone/attach-target dead surfaces. Tests: cross-subject denial, duplicate defense, download auth, archive visibility, no Practice asset access.
7. **Question/test bank:** remove QB categories and category permissions/routes/UI; scope items by subject; add copy-to-test/distribute to matching class while retaining inside-class authoring. Tests: cross-subject denial, approved-only copy, immutable copied question behavior, Practice route/table non-interaction.
8. **Comments/categories/courses cleanup:** remove verified dead non-Practice code/schema/static/tests in small domain commits. Each deletion commit includes `rg` caller evidence and route 404/nav assertions.
9. **Activity consolidation:** only after live counts/retention decision. Backfill active audit tables in bounded batches, validate per-source counts/hashes, switch writers/readers, then remove old tables. Security audit retention remains separately protected.
10. **UI redesign/QA:** shared ocean-blue tokens and components, then page groups. Gates: HTML validator, axe, keyboard/focus, contrast, 320/768/1280px screenshots, no horizontal overflow, native/established dropdowns.
11. **Migration gates:** fresh DB migrate to latest + seed assertions; supported upgrade snapshot migrate + row-count/FK checks; Flyway validate; backup/restore rehearsal; full non-Practice tests plus explicit Practice untouched smoke/route/schema diff gate.

Every commit must be single-domain and reversible in source. Data reset must be explicit: export any data to retain, recreate disposable schemas only after owner approval, seed subject codes idempotently, and never run Flyway clean/destructive SQL against a shared database.

## 8. Blockers and required choices

- **Blocker:** applied Flyway histories and real table sizes are unavailable. Fresh rewrite and activity migration batching cannot be approved yet.
- **Approved:** retain current one-subject-per-user authorization assumption for this release.
- **Approved:** add exactly one table, `class_co_lecturers`, and preserve primary owner/creator.
- **Approved default forced by the table ceiling:** Library distribution is a published snapshot into existing class lesson tables; no live sync/withdrawal identity in this release.
- Define test-bank “distribute”: create/copy a class test (recommended) versus shared test identity; set scheduling and editing-after-distribution rules.
- Confirm whether subject catalog is admin-editable or seed-only. Recommended seed-only initially, with stable codes and editable display names only if required.

## 9. Recommended execution order: easiest to hardest

Difficulty is not the only ordering rule: a low-code deletion stays behind its replacement flow when deleting it first would break users. Estimates are relative, not delivery promises.

| Order | Difficulty | Work package | Why here / dependency | Exit gate |
|---:|---|---|---|---|
| 0 | Gate | Collect DB/Flyway/deployment evidence and settle product choices | Prevents checksum damage and schema rework. Must precede every migration edit. | All DB histories/sizes captured; reset vs forward decision signed off; subject/user, co-lecturer and distribution semantics decided. |
| 1 | Easy | UI/token hygiene and automated HTML/accessibility/overflow gates | Read-only visual baseline and test harness can land without changing data flows. | HTML validation, axe, keyboard/focus and 320px overflow checks run on impacted pages. |
| 2 | Easy | Remove confirmed unrouted/deprecated non-Practice UI only | `student/lesson-detail.html` is explicitly deprecated, but still requires final caller proof. This establishes the safe dead-code deletion pattern. | `rg` + route test prove no caller; no Practice diff. |
| 3 | Easy–medium | Subject vocabulary decision and KOR seed contract tests | Establishes stable identity used by every later class/library/QB change. Do schema only after gate 0. | Exactly KOR311/KOR321/KOR411 are seeded/idempotent; FK/unique tests pass. |
| 4 | Medium | Class create form requires one subject; rename created-date UI | Mostly localized controller/DTO/template work once subject identity exists. Keep current backend lifecycle temporarily if needed. | Missing/invalid subject rejected; labels/focus responsive; creation timestamp cannot be posted/edited. |
| 5 | Medium | Normalize class lifecycle and leader approval | Central state change with bounded surface. Must precede searchable student catalog. | Only DRAFT/ACTIVE/ARCHIVED; approval is DRAFT→ACTIVE; invalid transitions rejected. |
| 6 | Medium | Auto-archive and visibility rules | Depends on normalized statuses/end date. | Idempotent timezone-aware worker; students never see archived/draft, teachers still see archived. |
| 7 | Medium | ACTIVE class discovery and request-to-join | Reuses enrollments PENDING flow, replacing token entry without first deleting it. | Search by class name/subject code; pagination; cross-status leakage tests; duplicate/capacity/concurrency tests. |
| 8 | Medium | Remove invite code/link end-to-end | Now safe because discovery/request is live. Despite simple product intent, deletion spans schema, FK, service, backfill, settings UI/static and many tests. | No `class_invite_codes`, token routes, generators, copy UI or callers; enrollment approval remains correct. |
| 9 | Medium | Remove `/leader/assign` (recommended path) | A bounded deletion once navigation/callers are enumerated; immediately eliminates ownership-transfer bug. | Route absent, nav absent, creator/owner access regression tests pass. If co-lecturer is chosen, defer to order 14 instead. |
| 10 | Medium | Remove admin/general and QB category UI/code | Can be done after subject filters exist, avoiding a functionality gap. | Category routes/controllers/templates/static/permissions have no caller; Practice catalog untouched. |
| 11 | Medium–hard | Reshape Question Bank to subject and integrate test creation | Crosses workflow, import, review and class test authoring, but can reuse existing test/question tables. | Same-subject and approved-only rules; copied test remains valid if bank item later changes; in-class creation preserved. |
| 12 | Hard | Build subject Library hierarchy | Changes ownership of sections/lessons/materials and moves authoring out of classes. Must be additive first. | Library can author complete subject→section→lesson→material tree; authorization/download tests pass. |
| 13 | Hard | Snapshot Library lesson to classes and make class lessons read-only | Reuses current sections/lessons because the single approved new table is co-lecturers. | Multi-class same-subject snapshot is atomic; duplicate/cross-subject denied; both roles view; no class authoring endpoint remains. |
| 14 | Hard | Remove old class authoring and loose attach/clone surfaces | Deletion becomes safe only after Library create/distribute exists. Requires static/API caller cleanup and content validation. | No dead attach-target/clone/class-upload route; retained content counts match; Practice `lecturer_assets` unchanged. |
| 15 | Hard | Remove class lesson comments | Code deletion is moderate, but must follow the final lesson access model to avoid misclassifying callers. | Comment API/UI/moderation/activity callers absent; unrelated comments/Practice feedback untouched. |
| 16 | Very hard | Department→subject physical rename/removal across all remaining domains | Broad FK, authorization, admin, leader, report and seed migration. Semantic subject work lands earlier; physical cleanup waits until dependencies are drained. | No live `department*` product concept/reference; upgrade/fresh migrations and role gates pass. |
| 17 | Very hard | Courses/categories/table cleanup and migration compaction | Physical deletion/rewrite is checksum- and FK-sensitive. Do after all semantic replacements and deployment proof. | Empty-schema + supported-upgrade + rollback rehearsal; no caller; exact row/FK validation. |
| 18 | Very hard / last | Consolidate `activity_*` | Requires real volume/retention data, batch backfill, writer cutover and audit reconciliation. It has the highest data-integrity/operational risk and little user-visible urgency. | Per-source counts/event samples reconcile, retention/index plan observed under load, old writers stopped before old tables are removed. |

### Practical release slices

- **Slice 1 — safe foundation:** orders 0–4. No legacy flow removed.
- **Slice 2 — correct class flow:** orders 5–9. Students use discovery/request; ownership bug is eliminated.
- **Slice 3 — subject Test Bank:** orders 10–11.
- **Slice 4 — Library redesign:** orders 12–15, additive before subtractive.
- **Slice 5 — physical schema reduction:** orders 16–18 only after migration rehearsals.

Within every order, prefer three atomic commits where applicable: additive schema/model, caller cutover with tests, then verified dead-code/schema removal. Never combine Practice changes, mass UI redesign, and schema removal in one commit.

## 10. Audit conclusion

The requested direction is feasible without touching Practice. The approved minimal target reuses physical `departments` as the subject catalog, renames the live audit to `subject_activities`, removes dead `activity_departments`, keeps classes/enrollments/tests/QB items, removes course/category/invite/comment legacies, and uses the single approved new table only for `class_co_lecturers`. Library distribution uses existing class lesson tables as snapshots. Fresh-only migration rewriting remains blocked until Flyway/deployment evidence is available; implementation uses forward migrations meanwhile.

## 11. Implementation checkpoints (forward-migration path)

- `171b2b37`: subject seeds/class lifecycle/join discovery/invite retirement/co-lecturer membership; V88–V91 also rename the live audit table to `subject_activities` and remove the unused duplicate `activity_departments`.
- `a8923e24`: removes non-Practice course/general-category schema and code.
- `273c297f`: scopes the non-Practice Question Bank to subject and removes its category ownership.
- `54e5a2d8`: removes class lesson comments and their audit table.
- `d58dafa7`: V95 reuses `lesson_templates` for subject → chapter → lesson → materials, snapshots published lessons into existing same-subject class rows, removes class-scoped authoring/loose-attach routes, and adds no table.
- Activity cleanup under validation: V96 removes the six remaining V3 tables with no application writer or reader, while retaining all seven live audit streams.
- Random class-code cleanup under validation: V97 drops `classes.code`; class lists/search/sidebar/approval/assign/report use the catalog subject code instead, and the generator/repository lookup are removed. V98 normalizes historical `joined_via` CODE/LINK rows to REQUEST and removes those invite values from the constraint/enum.

The one-new-table gate remains exact: only V89 creates `class_co_lecturers`; V88 and V90–V98 create no tables. Practice paths and migrations remain outside every implementation diff.
