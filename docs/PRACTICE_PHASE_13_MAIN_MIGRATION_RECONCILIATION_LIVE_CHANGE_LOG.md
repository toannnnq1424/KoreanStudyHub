# Phase 13 Main Migration Reconciliation Live Change Log

Date: `2026-07-29`

Task: `PHASE_13_MAIN_MIGRATION_RECONCILIATION_GATE`

Status: `STATIC_RECONCILIATION_COMPLETE_PENDING_MAIN_INTEGRATION_AND_VALIDATION`

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
