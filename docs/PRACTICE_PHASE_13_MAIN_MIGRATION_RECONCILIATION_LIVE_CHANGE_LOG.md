# Phase 13 Main Migration Reconciliation Live Change Log

Date: `2026-07-29`

Task: `PHASE_13_MAIN_MIGRATION_RECONCILIATION_GATE`

Status: `PHASE_13_MAIN_MIGRATION_RECONCILIATION_NO_GO`

## 1. Locked inputs

- `origin/feature/practice-reduce-scope` was fetched and verified at the locked
  End-of-Phase-13 merge `c9960d5a9b85e12abce5da2b94e9d8f03eb0361d`
  (tree `8a771cdb820ac8277a0bb8bc4c3c2543590573e1`).
- `origin/main` was fetched and verified at
  `3382347c60662a62c5914ef945e119af5e441972`
  (tree `6fa1a8ee4fbf66b1187827957482d9edd36b9bf7`).
- The merge base is `2549438c1a327b6932dc78d5284d7feaf5daf628`.
- Main has 60 commits and the feature has 21 commits after the merge base.
- The starting reconciliation worktree was clean and detached at the exact
  feature commit. The working branch
  `codex/phase13-main-migration-reconciliation` was created from that commit.
- Excluded and unrelated user paths were not read, staged, modified or removed.

## 2. Collision and free-version proof

The feature owns two unpublished Practice migrations whose filenames collide
with independently published main migrations. A literal filename scan across
every fetched `origin/*` ref found no `V61__*.sql` or `V62__*.sql`. Main's
latest migration is V60, so V61 and V62 were the next two consecutive free
versions at inventory time.

The dependency order is unchanged: the operational claim/lease columns must be
installed before the later attempt integrity/evaluation-job migration. The SQL
does not contain an internal version comment that needs correction.

| Historical feature filename | Reconciled filename | SHA-256 before | SHA-256 after | Bytes | Result |
|---|---|---|---|---:|---|
| `V57__practice_phase13h_operational_claims.sql` | `V61__practice_phase13h_operational_claims.sql` | `73e188ca16ad6354f34b85d3772499b365636e30631f44be0064622f2572bc63` | `73e188ca16ad6354f34b85d3772499b365636e30631f44be0064622f2572bc63` | 867 | byte-identical |
| `V58__practice_attempt_integrity_gate.sql` | `V62__practice_attempt_integrity_gate.sql` | `b01c99a66c49822b1887cff2f62ac2c424e51feee6ebb3ee5eefc0ee244a6629` | `b01c99a66c49822b1887cff2f62ac2c424e51feee6ebb3ee5eefc0ee244a6629` | 4413 | byte-identical |

The historical V57 file was introduced by feature commit
`51f30306b52e594496b9f762859feb95ebec0ed5`; the historical V58 file was
introduced by `8b722ffbed27f5b63b92cd8712fd27826f392f81`. Neither path has any history
on `origin/main`. Renumbering is therefore safe: these are unpublished feature
migrations, not applied main history. No Flyway repair, clean or retained-schema
mutation is authorized or used.

## 3. Active-reference decision

The exact V57/V58 Practice references occur only in historical Phase-13H,
Post-13H and End-of-Phase-13 evidence logs. Those records remain truthful for
the snapshots that actually ran. Each receives an appended reconciliation note
instead of rewritten counts or claims.

Active runtime and test sources have no V57/V58/V59/V60/V61/V62 max-version
assumption requiring a code change. `PracticePublishedMigrationChecksumTest`
and `practice-migrations-v1-v56.sha256` intentionally lock only the already
published V1-V56 chain and remain unchanged. Main's V57-V60 Java/test/doc
references still describe those main-owned migrations and remain unchanged.

## 4. Main migration preservation ledger

The complete `origin/main` migration inventory at the locked SHA is reproduced
below. The integration gate must preserve every filename and byte checksum in
this ledger. In particular, main-owned V57-V60 are never edited, deleted,
renamed, reordered or squashed.

```text
397d8aba7289aa5e4795731a70912639f88fd59c15ef484d347201363e684729  V1__init_schema.sql
c7273031a5be2f3357a8af88a8029102c74e8b49e79822dfad88de8a1af1c5b0  V2__seed_data.sql
e12f03e5fd541ab7ef0603e73b2f8c613cdf404651d286ac070e00a335bf8a3c  V3__activity_tables.sql
a0d4e0da60c9f4cde8c88297f9c2a25e5b1d3895fd05b22cbae061c6544f2a72  V4__rbac_enhancement.sql
ae9f9fa9d3ade1eab3eca32ec835bfcabcdba647359a34bc6426a120bddb3ba0  V5__seed_test_users.sql
8b73c9cfabc5d1966d8bff98cf3b2eafe698919a3af7868f08b5cf77d5022ffb  V6__fix_test_user_passwords.sql
7ee90c8798dd05d3e7234d6e9e91e13d615fd4334b5a17d6343c6645d06ccb6d  V7__classes_drop_course_and_add_code.sql
fff77b1adc4b2df8ae019b916f9f369c5fb794e5d5fa23d912801407eeed2708  V8__seed_fake_students.sql
b689c154d4f24a0364e67eb2c20301b4754a9901eb9d196dbcedc40018a23841  V9__seed_email_settings_extras.sql
b4b52e3d16014aee95edb532739c40a9c5c94b8c62c092dbc41f6eec735f7b95  V10__user_activities.sql
8b18586c529da1322c8b21a54ba80573f0be749e09aa30493014973eeb62cec5  V11__lowercase_existing_emails.sql
fcfbf7ea99af2e678492ddc64fee9f3bb62c6a0a7d3b1e08a148084b7557019b  V12__seed_class_invite_codes.sql
562ca159a91d4df01e1f8924dff2bc9c86616159a6e355b2d4a52df5d85e39ec  V13__sections_table.sql
0367bdc590b0dc2ce3c0fea33a80903c31635d74eeb7bb879cc80d36f5dade8b  V14__lessons_table.sql
c2e47a996fdfde6f13f580aef030cc8c7f388b5bf22c40912d1b709ef867f4a2  V15__lesson_attachments_table.sql
f37861c0a52d716da20a173bd45ad0796736cb7700c7152a01588b22bb7e915e  V16__lesson_content_types.sql
cec264c87d5b529e0cde84b623c518f9aad43a33267bd19466507eba738ca2c6  V17__public_view_tokens.sql
b8a61ef85eb07e7acc8e3a9c79549830d0d90b0bb2075b9d928df3cba7c80af2  V18__flashcard_images_and_review_unique.sql
f2211d0dea05af9cfcd9b96e2f42274ae02522bc8634df4265adf5143497fa58  V19__flashcard_drop_card_images.sql
69ee1d0108f9e5deda1324d11ac347c983998277d1aa0f94e79aba39f1615896  V20__mcq_exam_scheduling_and_seed.sql
5c11de901bf0d7fde68edfe7aeed31c4c438448fa998dfdec54ab94237215ee7  V21__student_lecturer_messaging.sql
ea38009805aeedc3705539236d69ae80b013e2eb7f9b49e640b95a432526e6ea  V22__seed_demo_notifications.sql
3349e8ff7c27f3517573209945a5644a0c16fc1db97e31c0fabc86c597327241  V23__seed_demo_assignments.sql
6a4c62a98df998aa8e1431e623da9d5faed6327d3202392ad08a0e4806f03397  V24__enrollment_pending_rejected.sql
08a8ac8258fbb1881785958eec8700d883b63e20455e4af62edc6ba4557dfdda  V25__practice_hub.sql
46a01c338cd651e173124603d5b2c20cbda3641fc852fb4d58eaafafceefd212  V26__writing_evaluation_cache.sql
e3727debb5891c2c51b44deeccf623a3b6c3059081393065c35600a8c7dee0a7  V27__version_question_explanation_cache.sql
a2f5f6c9a45541b70704fe2d09580db8c1e8a374587102876607d6a04074ec3d  V28__practice_attempt_optimistic_lock.sql
92bd9339f6162c8aa2af944c9b777eac3edf138e710528afac6bdcff613b2e3c  V29__practice_question_writing_task_type.sql
f5c531fa5b810407dabe7a10f3b7eab51310c188105f0c6099ec81a7f0665065  V30__practice_speaking_media.sql
5328d56e97a977e15daa5525f901b8c6924956a4eee7a3a2138fe99ad85dc7eb  V31__practice_speaking_media_cleanup.sql
eb35ae0f5239e798b2be9bbc833d3dfa862aab9a2de88899b2bf58b9d0273b08  V32__practice_attempt_discard_tombstone.sql
82477cb5f77d9d286c6539f1f5f786047c1157f8b8c5a062814becfb76d4a426  V33__practice_immutable_versions.sql
747cd83fe27cef95af3eb260cd4e2ddbbfce5bd0e15166803ddaa421e0adfdbf  V34__practice_single_scope_final.sql
9488f1e77a128cb3245b496e3254a398b93e07897c98c9b9a91b03de7324a8a3  V35__practice_catalog_performance_indexes.sql
ef66f8f2af19448c704ca7fffa2f4d0f3e410163fa8ab99bec0a02e09471dcd3  V36__practice_section_delivery.sql
1500a939101aa018bf91d123976d5ffce4d596b44c84079b61ca503dc15fac9a  V37__question_explanation_artifact_lifecycle.sql
9a70da8f4856206a18753fa24f9f6eb554747beb749c87778cb55082f7ea80c0  V38__test_media.sql
5f8d447570da685c90b7589b87943175897095af4b5b4304d1ea272a43f43b45  V39__exam_question_content_mediumtext.sql
ff4552d0bebcb3b66cdd338f4e2bee491235508f367067c22f64fe7a44a96f09  V40__classes_department_id.sql
688ab37a1b208a138ef93eab1a92a7c3f1e5855e5366de0f92c443bc19014370  V41__department_activities.sql
e095af19d9f35765dfa278dac1f2189d32652aad06097b3a77d9acdab61ea126  V42__library_assets.sql
12dc7a7739253efd82de0e5fd2e20814ceee801a96c5bf0a39a3c7a4489c30f5  V43__reset_demo_passwords_to_123456.sql
fa9ddcb87ff894857b920245d5cb0a81f8258914c2b649efed67e740416a543d  V44__practice_seed_listening_check_audio.sql
44799073ab848daaa45650aab6ed7b048781e6534e2a7f5d6f37770dc32bb177  V45__lesson_templates.sql
0beba34b16ed175e851a498551344ae865c0de3e61e849a0734eaac947130026  V46__department_question_bank.sql
f4cef8ad92546d5f0d4149ad05b36491cc303f726e78b11764a34a464b37a4d5  V47__question_bank_status_before_archive.sql
d40444fc2dd6b441b5adcb33145eb849d94255866d82cabf284097aa06cb4543  V48__standardize_subject_leader_role.sql
f93cb10d4f4622078d5b27633840e1f412a65edf60206093d85184b94c8ebead  V49__rbac_permissions_backfill.sql
4aa79dde7400cb71dd0fd22ff87bdc8e5f661a5ca1cdff60ac1b1bb3ebd05878  V50__admin_ai_providers.sql
2a6d1899eeb3753f51179d3f4b4b1083c9cbb283e38607d9d53c6fd8b05baaaa  V51__admin_ai_request_logs.sql
bc57de10fc7c6cb348caf3420f3931a062f6b708bb79cabd623b7843f19c2bab  V52__admin_object_storage.sql
c44e1c4e22f0f263e734368be07392764d34511e8d6466a2505796b369ee93c3  V53__standardize_ksh_site_name.sql
f03dfecb7e6c9e4ea4ec2b66b893d249ea988d38e158f614b85e49a20ac828ba  V54__ai_system_prompts.sql
1e19d25c312f881eb9b2573e57092df93241801b6a870cf45361dc0fbdc5164a  V55__practice_speaking_prompt_authoring_foundation.sql
0a5f80477a2264bf7567129651e88959bb8ade218d8af26e2e9fc6ac8a9a073a  V56__practice_phase13g_catalog_progress_indexes.sql
b88e682cb926a92a5b6012a04f6319977b950818dc62785a4866f1fba593f473  V57__seed_ai_question_generator_prompt.sql
0496da3cbc670f20ee4353057acd0db9dbfc6fb3ec64e6a97370f4a5255e73b3  V58__durable_ai_question_draft_sessions.sql
dcafc441478f3bf83e69f2a9d1f7726322efcea39c240cc744768e9ea77d8947  V59__durable_mail_outbox.sql
234250962b4c8fc66ac9e3e788d9a6f726040dbe553cf9f1aa9d138458dc78f0  V60__harden_ai_question_generator_prompt.sql
```

## 5. Pending gate sequence

1. Commit the byte-identical rename and this bounded evidence separately.
2. Fetch and merge the then-current `origin/main` with a normal merge commit.
3. Audit both-parent diffs, class/route/config/package boundaries and the final
   one-file-per-version V1-V62 inventory.
4. Run the single consolidated Java 17 validation lifecycle only after the
   integrated tree is frozen.
5. Publish through checked merge-commit PRs only if every gate remains green.

No post-Phase-13 package reconciliation, rebaseline or Pre-14 work is opened by
this task.

## 6. Main integration and integrated static gate

Immediately before integration, a fresh fetch reconfirmed the feature lock at
`c9960d5a9b85e12abce5da2b94e9d8f03eb0361d`, main at
`3382347c60662a62c5914ef945e119af5e441972`, and main's max migration at V60.
The reconciliation branch then merged that exact main commit with a normal
merge commit:

- merge commit: `5693a1195329a2c5e02278a7009566e121b6c182`;
- first parent: `9e6f91259069db3f3d21c24b047f6165ec972e9e`;
- second parent: `3382347c60662a62c5914ef945e119af5e441972`;
- integrated tree: `fbdc2458610095bf455b2f7b7ff336ffd55e207d`.

Git found no textual conflict. The stronger integrated audit then established:

- 62 migration files with exactly one file for each version V1 through V62;
- zero duplicate or missing versions and max version 62;
- zero byte mismatches across all 60 main-owned migration files;
- V61/V62 retain the pre-rename hashes and the old Practice V57/V58 paths are
  absent;
- no path was independently changed by both sides after merge base `2549438`;
  the integration therefore contains no silent same-file auto-combination;
- the full diff contains 139 paths relative to main and 232 paths relative to
  the original feature ref, with zero excluded-path matches;
- no duplicate Java filename, JPA table mapping, configuration-properties
  prefix or application property key was introduced;
- `SecurityConfig.java` is byte-identical to latest main, while `pom.xml`,
  `.java-version` and Practice runtime properties retain the reviewed feature
  versions;
- zero imports connect the Practice AI/storage packages to the independently
  added project-wide AI/storage packages in either direction; both families
  remain present, operationally separate and owned by their original domains;
- both locked histories are ancestors of the integrated merge and the worktree
  is clean.

This closes static reconciliation only. Compile, focused/full tests, disposable
fresh/upgrade Flyway, Hibernate/Tomcat startup, browser smoke and provider-call
counts must all come from the one consolidated validation lifecycle below.

## 7. Consolidated validation result — NO_GO

The gate began on OpenJDK `17.0.19` and Maven `3.9.16`, with provider
credentials empty and AI/STT/TTS plus all relevant background workers disabled.
Five uniquely named MySQL 8.0 disposable catalogs were proved absent before
creation. The tracked Maven wrapper has mode `100644`, so the initial
`./mvnw` launcher probe returned permission denied before Maven executed; the
same tracked wrapper was then invoked through `bash`, without changing its
mode or repository bytes.

The single clean package/compile succeeded:

- Maven Enforcer accepted Java 17 and Maven 3.9;
- 785 production sources compiled with `release 17`;
- 328 test sources compiled;
- tests were skipped for this package step;
- `target/ksh-0.0.1-SNAPSHOT.jar` was built successfully.

The focused selector then started on the fresh focus catalog. Flyway validated
and applied exactly 62 successful migrations from V1 through V62, with zero
failed migrations. The run discovered 559 tests and ended with zero assertion
failures, 213 errors and zero skips. The errors collapse to one Spring context
root cause:

```text
Error creating bean with name 'passwordResetTokenRetention'
Failed to instantiate PasswordResetTokenRetention: No default constructor found
Caused by: NoSuchMethodException: PasswordResetTokenRetention.<init>()
```

`PasswordResetTokenRetention` is a main-owned `@Component` with two
constructors and no constructor selected for dependency injection. The
integrated and `origin/main` blobs are identical at
`a8375fb4353060c0b777deef2ac1f9b68cdde01b`; main commit
`379c19236ea9c610ff4c854dc5791e93b9229d83` introduced the file. This is not a
Flyway collision, Practice regression or same-file merge artifact.

Correcting the bean would modify unrelated Auth production code outside the
task's explicitly bounded migration/reference reconciliation allowance. The
gate therefore stopped instead of guessing, patching main-owned Auth code or
publishing stale/partial evidence. No full suite, upgrade rehearsal, standalone
startup, browser smoke, push, PR or remote merge was attempted.

Before cleanup, the focus catalog proved `62` successful Flyway rows, `0`
failed rows and max version `62`; `ai_request_logs=0` and
`practice_ai_request_audits=0`, so real provider counts are AI `0`, STT `0`,
TTS `0`. All five gate-created catalogs were then dropped by exact name without
Flyway clean or repair, and the final `information_schema` absence count was
`0`. No temporary application server was started by this gate.

Final decision:
`PHASE_13_MAIN_MIGRATION_RECONCILIATION_NO_GO`. Remote feature and main remain
untouched. Resume requires a separately authorized, reviewed correction to the
main-owned Auth bean (or a newer green main containing that fix), followed by a
fresh reconciliation and full validation lifecycle.

## 8. Authorized Auth recovery — validation remains NO_GO

Recovery authorization allowed only the concrete constructor-injection blocker
above. Read-only ownership review found two runtime constructors, with the
three-argument value-backed constructor intended for Spring and the four-argument
`Clock` constructor used only by direct unit tests. The bounded local correction
therefore adds `@Autowired` to the existing value-backed constructor and adds a
Spring context regression while retaining both direct-construction tests. No
retention defaults, bounds, clock semantics, scheduling, repository behavior or
security behavior changed. The resulting uncommitted production and test file
SHA-256 values are respectively
`11b7f3849ff1d3b860aa497fc0e211b5f8b5cff16b8ef99e438fb3f285cd8550`
and `bf32d3651ef25d0e76f5222263db42475621520e14715387804e2bb4585eb7e5`.

The first recovery lifecycle compiled and packaged successfully on OpenJDK
`17.0.19` and Maven `3.9.16` (785 production sources, 328 test sources). Its
focused selector discovered 562 tests and returned two failures: the new minimal
context runner lacked Boot's `Duration` conversion service, and the existing
Practice native-time projection test observed a precise seven-hour offset because
the validation URL omitted the application's
`serverTimezone=Asia/Ho_Chi_Minh` setting. Neither failure required a production
semantic change. The single concentrated correction pass registered Boot's
conversion service in the regression test and corrected only the validation URL.

The permitted lifecycle rerun again passed `git diff --check`, the V1-V62
uniqueness/hash proof and the Java 17 clean package. The focused stage then ended
with 562 tests, zero assertion failures, 230 errors and zero skips. All nine
failing suites collapsed to one validation-environment root cause before their
application contexts could start:

```text
Could not resolve placeholder 'TEST_DB_USERNAME' in value "${TEST_DB_USERNAME}"
```

The focused command supplied `TEST_DB_URL` and runtime `DB_USERNAME`/
`DB_PASSWORD`, but omitted the test-safety guard's separately required
`TEST_DB_USERNAME`/`TEST_DB_PASSWORD` variables. The constructor regression
itself passed all three tests, including Spring context construction and both
existing direct-test construction paths. This is an operator configuration
failure rather than a new application root cause, but the gate permits no
additional validation rerun or line-test thrashing. Fresh V1-V62 startup,
Hibernate/Tomcat, the full suite and browser smoke were therefore not attempted,
and no recovery provider-call total is claimed.

All eight recovery catalogs across the initial attempt and its rerun were dropped
by exact name without Flyway clean or repair; the final `information_schema`
absence count was `0`. At the stop point, remote feature remained
`c9960d5a9b85e12abce5da2b94e9d8f03eb0361d` and remote main remained
`3382347c60662a62c5914ef945e119af5e441972`. No correction commit, push, PR or
remote merge was created.

Recovery decision:
`PHASE_13_MAIN_MIGRATION_RECONCILIATION_NO_GO`. The authorized constructor fix
and regression remain as a reviewed local diff, but they are not publication
evidence until a newly authorized complete lifecycle is green.

## 9. Credential-unblocked complete lifecycle — full-suite NO_GO

The user then supplied the missing disposable-test database credentials and
authorized continuation. The password was used only as an environment value and
is not recorded in this log. A read-only gate reconfirmed remote feature at
`c9960d5a9b85e12abce5da2b94e9d8f03eb0361d`, remote main at
`3382347c60662a62c5914ef945e119af5e441972`, the local integration at
`1f48913b84258ac7047ad347d61fc8b4c0acb3c9`, and 62 unique contiguous
migrations through V62. Four newly named catalogs were proved absent before
creation. The server identified itself as MySQL `9.7.1`; Flyway logged that its
latest tested MySQL version is 8.1. This environment difference did not prevent
the green focused or standalone-startup stages, but it is not treated as MySQL
8 validation evidence.

The complete lifecycle produced the following green stages:

- Java `17.0.19` / Maven `3.9.16` clean package: 785 production sources, 328
  test sources and a successfully repackaged application JAR;
- focused selector: 562 tests, zero failures, zero errors and zero skips;
- focused catalog: 62 successful Flyway rows, zero failed rows, max V62,
  `ai_request_logs=0` and `practice_ai_request_audits=0`;
- standalone fresh startup: Flyway applied all 62 migrations, Hibernate
  initialized the persistence unit, Tomcat started on isolated port 18082, the
  root request returned HTTP 302, and graceful shutdown completed;
- startup catalog: 62 successful Flyway rows, zero failed rows, max V62 and
  both provider-audit tables at zero;
- the Auth constructor regression passed all three tests in both the focused
  run and the later full run.

The full suite then completed with 2,626 tests, 17 failures, 14 errors and zero
skips. All 31 failing cases were collected before the decision and group into
three independent, non-Practice/non-Auth-correction families:

1. 23 Comments moderation cases across
   `LessonCommentsServiceTest` and `LessonCommentsApiControllerTest`: MySQL
   pessimistic `FOR UPDATE` lock waits/timeouts, with dependent bulk counts of
   zero and HTTP 500 results;
2. six authorization/leader-visibility cases across Assignments, Classes,
   Sections and Student lesson tests: expected success/404/content visibility
   but observed 403 or entity-not-found behavior;
3. two file-deletion cases in lesson attachments and library services where the
   on-disk file remained present.

The full catalog itself still proved 62 successful Flyway rows, zero failed
rows, max V62, `ai_request_logs=0` and `practice_ai_request_audits=0`. These
failures are materially different root causes outside the only authorized
production correction (`PasswordResetTokenRetention`). No corrective edit,
test retry, browser smoke or publication step was attempted after the full-suite
result.

All four catalogs from this lifecycle, including the unused browser catalog,
were dropped by exact name without Flyway clean or repair. The final count of
all `ksh_test_p13recovery%` schemas was `0`, isolated port 18082 had no listener,
and both remote refs remained unchanged. No correction commit, push, PR or
remote merge was created.

Latest recovery decision:
`PHASE_13_MAIN_MIGRATION_RECONCILIATION_NO_GO`.

## 10. Concentrated integration-failure correction — locally green

The user authorized a bounded correction of the 31 cases collected in the
preceding full run. Ownership review reduced them to three causes, and the
correction stayed within those causes:

1. `CommentModerationWriter` used `REQUIRES_NEW` underneath transactional
   single-item service methods. Transactional integration tests held
   uncommitted comment fixtures in the outer transaction, so the independent
   writer connection waited on its own fixture locks. The writer now uses the
   default `REQUIRED` propagation: a direct moderation call joins the service
   transaction, while the deliberately non-transactional bulk loop still opens
   one proxy-owned writer transaction per item in production. Moderation state,
   audit writes and partial-success ordering are unchanged. The corrected
   writer SHA-256 is
   `e378f82ac5125e1cb1c6f39610638206709d5772d8f2a740b1cb23c0c774d234`.
2. Six authorization expectations predated main's department-scoped LEADER
   policy. Their fixtures either omitted `classes.department_id` or supplied a
   STUDENT id with `Role.LEADER`; one test also expected 404 for an existing
   unauthorized class although the active contract is 403. Tests now use the
   seeded LEADER identity, assign the owning lecturer's department to created
   classes and assert the current 403 contract. Active service documentation
   now states LEADER department scope and ADMIN global scope. Production access
   policy was not widened.
3. Two storage tests expected irreversible file deletion before their enclosing
   test transaction committed. Current production correctly registers deletion
   with `afterCommit`. Only those two positive deletion tests now run without an
   ambient test transaction, so the service transaction commits before the
   filesystem assertion. Production storage timing and rollback safety are
   unchanged.

The previously authorized Auth constructor correction is unchanged: the
runtime value-backed constructor remains explicitly selected with `@Autowired`,
the `Clock` constructor remains available to direct tests, and their SHA-256
values remain
`11b7f3849ff1d3b860aa497fc0e211b5f8b5cff16b8ef99e438fb3f285cd8550`
and
`bf32d3651ef25d0e76f5222263db42475621520e14715387804e2bb4585eb7e5`.

Validation used OpenJDK `17.0.19`, empty provider credentials and disabled
AI/STT/TTS/background workers. It produced:

- `git diff --check`: pass;
- static migration proof: exactly one migration at every version V1-V62;
- unchanged V61 SHA-256
  `73e188ca16ad6354f34b85d3772499b365636e30631f44be0064622f2572bc63`;
- unchanged V62 SHA-256
  `b01c99a66c49822b1887cff2f62ac2c424e51feee6ebb3ee5eefc0ee244a6629`;
- Java 17 package: 785 production and 328 test sources compiled, repackaged
  JAR created;
- concentrated 13-class selector: 197 tests, zero failures, zero errors and
  zero skips;
- full suite on a separately fresh catalog: 2,626 tests, zero failures, zero
  errors and zero skips;
- both catalogs: 62 successful Flyway rows, zero failed rows, max V62,
  `ai_request_logs=0` and `practice_ai_request_audits=0`.

The MySQL server identified itself as `9.7.1`; Flyway repeated that its latest
tested MySQL version is 8.1. This is green local integration evidence on the
available server, not a claim of MySQL 8 validation. The two exactly named
disposable catalogs were dropped after audit without Flyway clean or repair;
their final `information_schema` presence count was `0`.

The local integrated HEAD remains
`1f48913b84258ac7047ad347d61fc8b4c0acb3c9`, with integration merge
`5693a1195329a2c5e02278a7009566e121b6c182`. The local remote-tracking refs
remain at
`c9960d5a9b85e12abce5da2b94e9d8f03eb0361d` and
`3382347c60662a62c5914ef945e119af5e441972`; no commit, push, PR or remote merge
was created in this correction pass. Publication therefore remains closed
pending an explicitly resumed publication gate and a fresh remote-main check.

## 11. Publication-gate remote confirmation

The publication gate was explicitly resumed after accepting the green local
correction evidence above. A fresh `git fetch --prune origin` on 2026-07-29
proved that neither target had advanced since validation:

- `origin/feature/practice-reduce-scope`:
  `c9960d5a9b85e12abce5da2b94e9d8f03eb0361d`;
- `origin/main`: `3382347c60662a62c5914ef945e119af5e441972`;
- integrated merge: `5693a1195329a2c5e02278a7009566e121b6c182`, whose
  second parent is the exact main SHA above;
- accepted pre-recovery documentation HEAD:
  `1f48913b84258ac7047ad347d61fc8b4c0acb3c9`.

Because both fetched refs are byte-for-byte the validated baselines, there is
no unvalidated remote delta. The recovery changes were separated into:

- `06f0032d68b393d64ac2d3c432b531bd955242b3` — constructor selection and its
  Spring-context regression contract;
- `fb0e723a31a106b911ae5397ec356cf22909b195` — moderation transaction boundary,
  current authorization fixtures/contracts, and after-commit storage tests.

The third commit contains only this accumulated reconciliation evidence. Before
push, the complete branch must again prove a clean worktree, ancestry from both
locked histories, a unique contiguous V1-V62 migration set, unchanged V61/V62
hashes, and no excluded/unrelated path. Publication remains merge-commit-only;
checks, PR URLs and final remote merge SHAs are recorded in the gate handoff
because a commit cannot truthfully contain the SHA of a future merge that
includes that same commit.
