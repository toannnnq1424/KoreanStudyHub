# Practice Pre-15 release-closure live report

Status: `IN_PROGRESS / NO_GO`

Baseline: `origin/main` at `3d38a2f0` (PR #67 merged)

Branch: `codex/practice-pre15-release-closure`

This gate prioritizes Pre-15 and then Phase 15 Manual UAT/release. Phase 14
feature work remains explicitly deferred. No item in this report authorizes
Phase 14, a real provider/storage call, a shared-data mutation, or a destructive
retained-data reset.

## 1. Post-merge checkpoint

- Fresh disposable Flyway chain: V1 through V87, 87 unique versions, no gap or
  duplicate.
- Focused JDK 17 checkpoint: 35 tests, 0 failures, 0 errors; 4 contract skips.
- Hibernate startup validation completed against the fresh V87 schema.
- Real AI/R2/STT/TTS calls: `0/0/0/0`.
- The fresh disposable database contains no Practice attempts or retained AI
  feedback payloads. It therefore proves the new-write baseline, but cannot by
  itself authorize removal of historical compatibility readers.

### 1.1 Authoritative V87 freeze (`2026-08-03`)

The release-closure worktree was re-audited at exact baseline `3d38a2f0`.
Migration source inventory is frozen as `87` files, versions `1..87`, with no
gap and no duplicate. No migration after V87 existed at this freeze checkpoint;
the later approved forward-only V88/V89 branch-B migrations are recorded in
sections 6.8/6.9. The canonical upstream historical bytes and the integrated
Practice compaction byte are:

| Migration | SHA-256 | Disposition |
| --- | --- | --- |
| `V1__init_schema.sql` | `64c75c28f72f0bea75c538fcaafaa2048588558f81abdfa9c2c0f4a87f74c93f` | Preserve canonical upstream rewrite. |
| `V54__ai_system_prompts.sql` | `8d974851b8ccc00d30124e97b911a35fa521e2a2ebd444652c5ea14bea09b48f` | Preserve canonical upstream rewrite. |
| `V87__practice_legacy_import_schema_compaction.sql` | `02035739158ef5977ddab8197b5a2e841fc3c5dee6b40e6f7192bd8b626356d2` | Preserve applied migration; forward-only from this point. |

Read-only inspection of dedicated disposable catalog
`ksh_test_postmerge_67` confirmed `87` successful Flyway rows, min/max
`1/87`, zero failed rows, `113` base tables and `1` view. The six V87-retired
Practice PDF/session/audit tables are absent. Persisted inventory is:

| Boundary | V87 evidence | Release disposition |
| --- | --- | --- |
| live questions | `9`; types `ESSAY,FILL_BLANK,SINGLE_CHOICE,SPEAKING,TRUE_FALSE_NOT_GIVEN`; all `9` ungrouped | Technical fresh fixtures only; does not satisfy canonical UAT grouping. |
| question versions | `10`; same five canonical types; all `10` ungrouped | Preserve compatibility until canonical seed/data decision. |
| attempts / non-empty AI feedback | `0 / 0` | Cannot authorize removal of retained-payload readers. |

This freeze is the authoritative Pre-15 V87 inventory. It makes no production,
shared-database or retained-data claim and did not mutate the catalog.

## 2. Refreshed compatibility inventory

The clean-cut merge already removed the old generic detail/static cluster,
text-simulated Speaking scoring, the Writing score matrix, and old progress
fallback symbols. Current persisted question types in the fresh V87 database
are canonical: `ESSAY`, `FILL_BLANK`, `SINGLE_CHOICE`, `SPEAKING`, and
`TRUE_FALSE_NOT_GIVEN`.

The following remaining paths require an explicit retained-data or product
decision before removal:

| Inventory | Current disposition | Reason |
| --- | --- | --- |
| mixed Speaking/Writing feedback envelope | `REMOVED` | Development-only retained-data disposition received; current Speaking writes and reads use only `speaking_ai_v1` plus `speaking_feedback_by_question`. |
| legacy Speaking/Writing feedback readers and reuse statuses | `REMOVED` | Replaced by typed current-contract parsers; malformed, reserved-audio and low-confidence payloads remain non-score-bearing and fail closed. |
| ungrouped question compatibility | `REVIEW_REQUIRED` | Fresh schema permits nullable `group_id`; nine fresh technical questions are ungrouped, so canonical seed/grouping work remains. |
| old import aliases and `practice-excel-v1` | `REVIEW_REQUIRED` | Supported import window and canonical template version are a product/release decision. |

No compatibility branch will be deleted merely because the disposable fresh
database contains zero historical attempts.

### 2.1 Caller/compatibility evidence matrix (`2026-08-03`)

This matrix distinguishes current writes from historical reads. Database facts
come only from read-only inspection of the dedicated disposable V87 catalog;
zero fresh rows are not retained-data evidence.

| ID | Current caller/source and schema evidence | Classification / exact decision still required |
| --- | --- | --- |
| `COMP-01` | `WritingResultPresenter.scoreObjective` still reads immutable non-ESSAY Writing versions through legacy content/answer adapters; `historicalWritingFillBlankUsesLockedAnswerSpecWithoutAiFeedback` pins the read. Current authoring rules write only Writing `ESSAY`; fresh V87 has no non-Writing canonical mismatch. | `READ_ONLY_HISTORICAL_COMPATIBILITY`; retained Writing-version inventory must choose `REMOVE` or bounded `MIGRATE`. Do not delete from fresh evidence. |
| `COMP-02` | `PracticeService.NonWritingEssayGradingSnapshot` still has submit/re-evaluate callers. Current draft/candidate validators restrict Writing to `ESSAY` and Speaking to `SPEAKING`; fresh V87 has zero non-Writing `ESSAY`. | `ACTIVE_COMPATIBILITY_GRADING_PATH`; removal requires retained non-Writing ESSAY inventory plus publication-rejection proof. Recommended default: preserve until that proof exists. |
| `COMP-03` | Owner disposition in section 6 authorizes removal of development-only retained payloads. `PracticeService` now writes/merges only `speaking_ai_v1`; `SpeakingResultPresenter` reads only canonical Speaking questions and `speaking_feedback_by_question`. | `CLOSED`; mixed Speaking/Writing envelopes, ESSAY Speaking presentation and their fixtures are removed. |
| `COMP-04` | Current callers now use `SpeakingFeedbackContractParser` and `WritingFeedbackContractParser`; the parsers accept typed current contracts only. Legacy result, flat/band parsing and legacy reuse branches are removed. | `CLOSED`; invalid current contracts remain explicit `FAILED`/contract-failure, never score-bearing. |
| `COMP-05` | Production snapshot, player, re-evaluation and state-policy paths already reject any missing/incoherent version lock as `INCOMPLETE_VERSION_LOCK`; focused tests pin the fail-closed boundary. | `CLOSED_BY_PROOF`; nullable V87 columns do not create a runtime compatibility reader. Development rows may be reset under section 6 disposition. |
| `COMP-06` | Publication now rejects every ungrouped question before writing a published-version row. Objective overview/detail and DTO contracts require canonical group and group-version identity; `legacyFallback` is removed. | `CLOSED`; V87 nullable columns remain historical schema capability only. Canonical/UAT content must be grouped before publication. |
| `COMP-07` | Seven `PracticeRoutes.LEGACY_*` constants are mapped by `PracticeController` to redirects; functional route tests assert redirect targets. No template/static hit was found as a canonical producer. | `ACTIVE_REDIRECT_API`; caller/bookmark/support-window decision required. Recommended default: time-bound redirects, not silent removal. |
| `COMP-08` | `QuestionTypeResolver` accepts `MCQ`, `MCQ_SINGLE`, `TFNG`, `GAP_FILL` and other aliases; result/codec tests pin normalization. New authoring validators emit canonical types. Fresh live/version rows contain no alias. | `ACTIVE_READ/IMPORT_ADAPTER; CANONICAL_NEW_WRITE`; stored candidate/import and retained-row inventory must define the alias expiry. |
| `COMP-09` | Current `PracticeAssessmentExcelService` deterministically rejects legacy v1 and advanced v2 workbook entry with `*_RETIRED`; Quick Excel is the only interactive writer. `LEGACY_EXCEL_V1/practice-excel-v1` remains an enum/JSON-schema identity for historical candidate envelopes. | `WRITER_RETIRED; STORED-ENVELOPE_IDENTITY_RETAINED`; inventory stored candidates before removing enum/schema identity. Current contract wording was corrected and statically locked in this slice. |
| `COMP-12` | Tests intentionally pin non-ESSAY Writing, mixed/legacy feedback, ungrouped graphs, missing locks, redirects and aliases. Each corresponds to an active compatibility branch above rather than obsolete test-only code. | `MIGRATION/COMPATIBILITY_FIXTURES`; delete only together with the matching production branch after its decision/evidence closes. |
| `COMP-14` | Current architecture contract incorrectly described legacy Excel v1 as a current bounded reader although runtime rejects it. Other hits for old result/cache names are predominantly dated phase/audit history and must not be bulk-edited. | `CURRENT_DOC_CORRECTED`; preserve archival records with dates. Continue per-document current-vs-history review, never global search/replace. |
| `COMP-21` | `PracticeSpeakingMediaRepository.findAuthorizedTranscriptionCandidates` still requires `g.id=q.groupId` and binds owner, attempt, section, set, question, skill and media status. Publication now guarantees group ownership. | `CLOSED_WITH_AUTHORIZATION_JOIN_PRESERVED`; no join was weakened and ungrouped content cannot publish. |

No row above authorizes deletion. The only automatic cleanup selected from this
audit is the COMP-14 current-document correction for the already-retired
interactive legacy Excel reader claim; the stored envelope identity remains.

### 2.2 Automatic cleanup/proof slice

Status: `IMPLEMENTED_AND_FOCUSED_TESTED`.

The current authoring/import architecture contract now matches runtime: legacy
Excel v1 interactive upload is retired and deterministically rejected, while
its stored candidate enum/schema identity remains pending inventory. A static
contract prevents the current-reader claim from returning and a focused service
test preserves deterministic rejection without invoking candidate creation.
JDK 17 gate: `11` tests, `0` failures, `0` errors, `0` skips.

## 3. Speaking audio scoring decision

Current release behavior is transcript-grounded language evaluation. Recording,
owner playback and STT may consume authorized audio, but the scoring client does
not receive learner audio or acoustic measurements. Fluency and
pronunciation/delivery remain non-score-bearing, and no holistic Speaking score
is exposed.

`AUDIO_DIRECT_FULL_RESERVED` remains a disabled historical extension seam. It
is not the implementation mechanism for direct audio. The product owner has
selected branch B; branch A below is retained as dated proof of the old/current
transcript path, not as the intended Speaking release target:

- **A — disabled with proof:** preserve transcript-only scoring, prove no scorer
  request can contain audio, keep acoustic readiness blocked, and expose no
  acoustic/holistic claim.
- **B — separately implemented and accepted:** only after explicit product,
  privacy/consent/withdrawal, reviewer authorization, provider
  non-training/retention/region, deletion-SLA, and Korean acoustic-calibration
  approval. This must be a separately named evaluator whose captured request
  proves authorized audio reached the scorer.

Branch B must remain separately named, fail closed and non-score-bearing until
its consent, provider-policy, deletion, reviewer-access and acoustic-readiness
evidence is materialized. Configuration still cannot turn
`AUDIO_DIRECT_FULL_RESERVED` into branch B.

### 3.1 Branch-A automated proof slice (`2026-08-03`)

Status: `IMPLEMENTED_AND_FOCUSED_TESTED / HISTORICAL_BRANCH_A_PROOF`.

- Added a transport-boundary regression that supplies distinctive learner
  media ID, media version, MIME type, byte size and duration to the internal
  transcript request, then proves none reaches the structured scorer request.
- The only permitted binary evidence remains an optional governed
  `QUESTION_IMAGE`; the authority strategy is exactly `TRANSCRIPT_ONLY`.
- `AUDIO_DIRECT_FULL_RESERVED` continues to fail before the provider port and
  the readiness report remains blocker-red even when both transcript provider
  gates are configured. No setting can turn the reserved enum into an
  audio-consuming evaluator.
- Focused JDK 17 gate: `53` tests, `0` failures, `0` errors, `0` skips across
  client transport, prompt, normalizer, score policy, rendering and rollout
  readiness. The provider was an in-memory fake; real AI/R2/STT/TTS/storage
  calls remained `0/0/0/0/0`.
- V87/AIM-7/AIM-8/storage static gate: `16` tests, `0` failures, `0` errors,
  `0` skips. Combined slice evidence is `69/69` green.

This slice proves the transcript mechanism remains isolated while branch B is
built. It does not claim branch-B acoustic readiness.

## 4. Dated dependency/SBOM/advisory baseline (`2026-08-03`)

All commands used JDK `17.0.19`, read Maven/advisory metadata only and wrote
ignored artifacts under `target/phase13h-security`. No application, provider,
storage or database call was made.

- CycloneDX 1.6 JSON: timestamp `2026-08-03T02:38:51Z`, `197` components
  (`195` required, `2` optional), SHA-256
  `26b14b317a84eb9beafc583153e6c30d71c4b57c1c4f72c947f681c95134860c`.
- Resolved bridge versions include Spring Boot `3.5.16`, Tomcat `10.1.57`,
  Logback `1.5.38`, Commons Lang `3.20.0`, POI `5.5.1`, PDFBox `3.0.8`, jsoup
  `1.22.2` and Netty `4.1.136.Final`.
- OWASP Dependency-Check `12.2.2` downloaded and processed all `372,543` NVD
  records. Its first fail-closed run stopped because the CISA KEV feed returned
  HTTP `403`; it produced no accepted report. A second offline-cache run with
  only that unavailable KEV datasource disabled analyzed `198` dependencies
  and produced JSON SHA-256
  `11a35d740e2c9aa40521318f381c8d137daa8277b4e26e0007022e90c77a5377`.
- The report contains one NVD `HIGH`: `CVE-2026-56816` attributed broadly to
  `netty-transport:4.1.136.Final`, CVSS `7.5`. Vendor advisory
  `GHSA-hpcc-26xq-25fv` identifies the affected Maven package as
  `io.netty:netty-codec-http3 <=4.2.15.Final`; the resolved graph contains no
  `netty-codec-http3`, and the resolved 4.1 transport JAR contains no
  `Http3FrameCodec`/HTTP3/QUIC class. The Netty path is runtime-transitive from
  AWS SDK S3 -> `netty-nio-client`, while configured project storage clients
  use AWS URLConnection HTTP. Classification:
  `NOT_REACHABLE_COMPONENT_MISMATCH / TOOL_CPE_FALSE_POSITIVE`, not an ignored
  reachable High.
- Coverage limitations remain explicit: CISA KEV status is `UNKNOWN` because
  its feed returned 403; Sonatype OSS Index is `UNKNOWN` because current access
  requires credentials not present in the repository. The NVD scan is dated
  and complete, but the overall advisory gate is therefore
  `GREEN_NVD_WITH_EXTERNAL_FEED_GAPS`, not unconditional green.
- Support lifecycle is a separate decision from CVE reachability. This audit
  verifies the repository still uses the bounded Spring Boot `3.5.16` OSS
  bridge; it does not select a supported production line or commercial support
  contract. Recommended default before Manual UAT: choose a currently supported
  Spring Boot line under a separately tested upgrade slice; if release remains
  on 3.5, record named commercial-support owner/term or explicit release NO-GO.

No dependency was upgraded or overridden in this slice.

### 4.1 External-feed coverage closure attempt (`2026-08-03`)

The two missing official-source checks were retried independently of the
scanner. Neither response is evidence that the coordinate is clean:

| Source | Reproducible request and output marker | Verdict / owner action |
| --- | --- | --- |
| CISA KEV | `curl -L --fail-with-body -A 'KSH-Pre15-Release-Audit/1.0' -o /tmp/ksh-cisa-kev.json -w 'cisa_http=%{http_code}\n' https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json` -> `cisa_http=403`, 456-byte CISA `Access Denied` body | `BLOCKED_EXTERNAL / KEV_UNKNOWN`. Security/Release Ops must provide approved egress or a dated official KEV artifact with digest, then rerun the SBOM coordinate match. A 403 must never be converted to zero KEV findings. |
| Sonatype OSS Index v3 | `curl --fail-with-body -X POST -H 'Content-Type: application/json' --data '{"coordinates":["pkg:maven/io.netty/netty-transport@4.1.136.Final"]}' -o /tmp/ksh-oss-index.json -w 'sonatype_http=%{http_code}\n' https://ossindex.sonatype.org/api/v3/component-report` -> `sonatype_http=401`, empty response body | `BLOCKED_CREDENTIAL / OSS_INDEX_UNKNOWN`. Security/Release Ops must inject a real OSS Index token through the approved runtime secret channel and rerun; do not commit or print credentials and do not use a fake secret. |

The authoritative vendor advisory remains coordinate-specific:
`GHSA-hpcc-26xq-25fv` affects `io.netty:netty-codec-http3`, whereas the SBOM
finding was name/CPE-matched to `io.netty:netty-transport:4.1.136.Final` and
the project resolves no `netty-codec-http3`. Reachability is therefore
`NOT_APPLICABLE_COMPONENT_MISMATCH` for that advisory, while KEV and OSS Index
coverage remain independently `UNKNOWN`. Scanner name matching alone is not a
vulnerability determination.

### 4.2 Spring support-line evidence (`2026-08-03`)

JDK 17 `mvn dependency:tree` resolves the current production bridge as Boot
`3.5.16`, Spring Framework `6.2.19`, Spring Security `6.5.11`, Spring Data JPA
`3.5.13`, Tomcat `10.1.57`, Hibernate `6.6.53.Final`, Jackson `2.21.5` and
Flyway `11.7.2`. A read-only Maven versions metadata run reports Boot `4.1.0`
as the available parent/plugin update and Security `7.1.0` as an available
major update. This is upgrade discovery, not source/runtime compatibility
proof.

The supported-line choices are:

1. Move to the current Boot 4.1 line in a separate upgrade branch. Treat it as
   a major framework/security/runtime migration and require compile, Flyway,
   security, serialization, template, browser and full regression evidence.
2. Keep Boot 3.5.16 only with named commercial-support provider, contract term,
   patch SLA and release owner evidence.
3. Declare release `NO_GO` until either choice 1 or 2 closes.

Recommended default is choice 1 as a separately approved/tested slice; this
closure branch does not change the BOM. OSS support status, commercial support
entitlement and runtime vulnerability reachability are three independent
questions. The dated NVD analysis found no accepted reachable High, but that
does not establish future patch support and the two external feeds above are
still unknown.

## 4.3 Deterministic canonical UAT seed contract (`2026-08-03`)

Status: `IMPLEMENTED_AND_FOCUSED_TESTED / BLOCKED_SME_REQUIRED`.

- The repository now contains a closed Draft 2020-12 static schema and a
  deterministic `KSH-PRE15-UAT-SKELETON-V1` manifest. Its authority is exactly
  `REPO_MANIFEST_ONLY_DO_NOT_LOAD`; this slice performs no database load.
- The manifest covers Reading, Listening, Writing and Speaking with `11`
  stable question keys, canonical types only, stable group keys, and complete
  `SET/TEST/SECTION/GROUP/QUESTION` immutable version-lock requirements.
- Reading/Listening explanation schema IDs and the Writing/Speaking policy
  bundle IDs are pinned to current source constants. Writing explicitly covers
  Q51/Q52/Q53/Q54 at `10/10/30/50` points.
- Content, Korean prompts/transcripts/answers/explanations, listening/prompt
  audio, optional question images, provenance/licensing and calibration remain
  `SME_REQUIRED`. Only the learner-recording target contract is classified
  `TECHNICAL`. No Korean content, approval, fairness or calibration evidence
  was fabricated.
- This dated skeleton is pinned to `TRANSCRIPT_ONLY`, no holistic score. It
  cannot enable `AUDIO_DIRECT_FULL_RESERVED`; a separately versioned branch-B
  manifest revision is required before direct-audio UAT.
- The fail-closed validator rejects ungrouped questions, incomplete version
  locks and a falsely `READY_FOR_LOAD` skeleton retaining SME blockers; it
  also binds policy IDs to implementation constants. Focused JDK 17 gate:
  `4` tests, `0` failures, `0` errors, `0` skips. Combined manifest,
  transcript-only scorer-boundary and AIM-8 compatibility regression gate:
  `25` tests, `0` failures, `0` errors, `0` skips.

Exact unblock package: Korean Academic SME owns approved/licensed content and
answer/explanation evidence; Academic SME plus Content Operations own asset
provenance, digests and accessibility metadata; Academic SME plus Backend
Readiness own the versioned corpus, multi-rater adjudication, fairness and
repeatability evidence. Recommended default is to keep the skeleton blocked
and load nothing until all three evidence records are supplied.

## 5. Remaining release blockers

- Materialize and verify branch-B consent, reviewer ACL, provider data-policy,
  deletion-SLA and acoustic-readiness evidence; complete dark rollout before
  any pronunciation/fluency score is enabled.
- Produce retained/canonical-UAT data disposition for compatibility payloads,
  grouping, version locks, old routes, and import aliases.
- Complete versioned Korean SME corpus, multi-rater/adjudication, agreement,
  fairness, repeatability and provider-drift evidence for every released
  assessment bundle. Acoustic coverage applies only if branch B is selected.
- Refresh the time-sensitive dependency/SBOM/advisory scan immediately before
  Manual UAT.
- Replace the deterministic blocked canonical UAT skeleton with SME-approved
  content/assets/calibration evidence, validate it, and load only an approved
  isolated UAT environment; do not reuse ad-hoc/local evidence schemas.
- Run Phase 15 browser/device/provider/load/security/manual-UAT matrix only
  after this gate reaches `GO`.

### 5.1 Exact decision package

| Decision | Options and impact | Risk | Recommended default |
| --- | --- | --- | --- |
| retained attempt/payload compatibility (`COMP-01..05,12`) | `KEEP/RETAIN_WITH_EXPIRY` preserves reads; `MIGRATE` converts bounded known rows; `REMOVE/DELETE_UAT_ONLY` eliminates paths only after authoritative counts/backups. | Premature removal makes immutable history unreadable or silently reinterprets scores. | `KEEP` fail-closed readers until authoritative environment counts exist; then prefer bounded `MIGRATE`, never infer from fresh zero rows. |
| grouping/transcription (`COMP-06/21`) | Canonically group/reset/migrate data, or retain ungrouped display fallback while transcription stays unavailable. | Weakening the repository join can cross owner/attempt/question authority. | Group canonical/UAT data and migrate retained rows; never weaken authorization. |
| legacy routes (`COMP-07`) | Remove now, retain indefinitely, or publish a dated redirect window with telemetry/caller inventory. | Removal breaks bookmarks/integrations; indefinite retention expands support surface. | Dated redirect window, then evidence-based removal. |
| aliases/stored import identity (`COMP-08/09`) | Remove aliases/enum after stored-row and caller scan, or retain read-only with expiry while all writers stay canonical. | Removal can make stored candidates/content unreadable; keeping writers would prolong ambiguity. | Canonical writers plus expiring read-only adapters; current legacy workbook upload remains rejected. |
| Speaking | Branch B is selected. Implement a separately named direct-audio evaluator through governed dark capture, then explicitly approve score release. | Audio transfer without active consent/provider policy/reviewer ACL/deletion and acoustic evidence creates privacy and invalid-measurement risk. | Keep `AUDIO_DIRECT_FULL_RESERVED` disabled; branch-B score release stays red until captured consumption and every readiness gate are green. |
| Spring Boot support | Move to a currently supported production line with regression evidence, buy/record commercial support for 3.5, or declare release NO-GO. | A clean CVE snapshot does not supply future fixes after OSS support. | Supported-line upgrade in a separate tested slice; otherwise explicit NO-GO rather than an ownerless exception. |
| external advisory feeds | Supply approved CISA/OSS metadata access and rerun, or accept a named time-bounded gap. | KEV/OSS unknown can miss exploited or ecosystem-only advisories. | Restore both feeds and rerun immediately before Manual UAT; no silent waiver. |

Verdict at this historical checkpoint was `NO_GO` while these
external/product/data decisions were open; section 6 records the subsequent
owner dispositions.

## 6. Owner decisions received (`2026-08-03`)

The product owner has now supplied the following release decisions:

- Korean SME content, assets and calibration are approved. This closes the
  governance decision, but does not permit this repository to invent missing
  content or asset identifiers. The static seed remains fail-closed until the
  approved artifacts and their evidence references are materialized in the
  manifest.
- Practice has not had an official production deployment. Attempts, AI
  feedback, ungrouped questions, incomplete locks, import aliases and legacy
  routes are development-only and may be reset/removed completely. This is the
  authoritative retained-data disposition for `COMP-01..09,12,14,21`; no
  production migration or compatibility window is required.
- Spring Boot `3.5.16` is the selected support line. The release owner accepts
  its support-lifecycle risk independently of the dated vulnerability result.
  The BOM remains unchanged; security metadata must still be refreshed for
  every release candidate.
- For this pre-production closure, the security decision is a time-bounded
  exception: the complete dated NVD baseline plus exact vendor-coordinate
  review is sufficient to enter Manual UAT. CISA KEV `403` and OSS Index `401`
  remain recorded coverage gaps, not clean results. Both official feeds must
  be restored and rerun before the first public production release; any
  reachable High/Critical or KEV match returns the gate to `NO_GO`.
- Speaking branch B is the selected release direction. Learner audio transfer
  is authorized only behind explicit consent/withdrawal/disclosure, reviewer
  least privilege, verified provider region/non-training/retention/deletion
  evidence, approved Korean corpus/calibration/fairness/repeatability evidence
  and dark rollout. This direction does not authorize invented evidence IDs,
  real credentials, or immediate pronunciation/fluency score release.

### 6.1 COMP-07 legacy route retirement

Status: `IMPLEMENTED_AND_FOCUSED_TESTED`.

All seven legacy learner route constants and redirect handlers were removed.
Canonical `/practice/sets/...` and `/practice/attempts/...` routes remain. The
three existing integration assertions now require `404`, and a DB-free static
contract prevents the removed constants/handlers from returning. This is safe
under the development-only retained-data/caller disposition above. The first
integration invocation was rejected before context startup because
`TEST_DB_URL` was absent; a retry against the named disposable container was
rejected because its persisted root credential no longer matched its creation
environment. Neither attempt mutated a database. The DB-free JDK 17 compile and
static/manifest/AIM-8 gate passed `13/13`.

### 6.2 COMP-08 server alias retirement

Status: `IMPLEMENTED_AND_FOCUSED_TESTED` for the authoritative Java resolver.

`QuestionTypeResolver` now accepts canonical enum codes only. The historical
aliases `MCQ`, `MCQ_SINGLE`, `MCQ_MULTIPLE`, `MULTIPLE_CHOICE`, `TFNG`,
`GAP_FILL`, `MATCHING_INFORMATION`, `MATCHING_FEATURES` and `MATCHING_LABELS`
fail closed instead of being normalized. Extended canonical codes
`MULTIPLE_ANSWER` and `MATCHING` remain unchanged; this does not open deferred
Phase 14 feature work. Presenter regression proves unsupported stored values
are explicitly `UNSCORABLE`, never silently reinterpreted. JDK 17 resolver,
presenter and route-retirement gate passed `56/56`.

Remaining occurrences in historical UI fixtures/authoring compatibility are
not claimed removed by this sub-slice and must be retired with their owning
contract, not by an unsafe global replacement.

### 6.3 COMP-09 stored legacy Excel source identity retirement

Status: `IMPLEMENTED_AND_FOCUSED_TESTED` for current application code.

`SourceKind.LEGACY_EXCEL_V1` and its `practice-excel-v1` contract identity were
removed from the current candidate model and convergence fixture. The Quick
Excel upload boundary continues to reject a legacy workbook deterministically;
it cannot create a legacy candidate. V83 is an already-applied canonical
migration and was not rewritten, so its historical database enum literal is
preserved until a future forward-only schema compaction. JDK 17 Excel,
candidate-static, resolver and route gate passed `19/19`.

Current verdict remains `NO_GO`: owner decisions are now recorded, but the
approved SME artifacts still need concrete manifest references and the
remaining development-only compatibility branches require tested retirement.

### 6.4 COMP-02 non-Writing ESSAY grading retirement

Status: `IMPLEMENTED_AND_FOCUSED_TESTED`.

The development-only `NonWritingEssayGradingSnapshot` load, grade, verify and
persist paths were removed from submit and re-evaluation together with their
positive compatibility fixtures. Canonical Writing still uses its immutable
per-question typed evaluation envelope. Reading/Listening objective scoring is
unchanged; any non-Writing `ESSAY` now reaches the existing fail-closed guard
`Essay attempt must use snapshot grading path` and cannot call the Writing
provider. Speaking remains on its canonical media/transcript path.

The caller audit also found stale `MCQ` question codes in service fixtures left
after COMP-08 server alias retirement. Those fixtures were canonicalized to
`SINGLE_CHOICE`; no production alias was restored. JDK 17 service,
authoring/publisher and static gate: `156` tests, `0` failures, `0` errors,
`0` skips. Compile-only gate also completed successfully. No provider, storage
or database call was made.

### 6.5 COMP-03/04 mixed-envelope and feedback-reader retirement

Status: `IMPLEMENTED_AND_COMBINED_TESTED`.

The development-only mixed Speaking envelope and its Writing feedback map were
removed end to end. `PracticeService` now emits only contract
`speaking_ai_v1` with `speaking_feedback_by_question`; the result presenter
selects canonical `SPEAKING` questions only. Dead ESSAY Speaking modes,
constructor dependencies and fixture authorities were removed. No migration
was changed.

The old compatibility readers were replaced by current-contract parsers.
Writing requires explicit status/source/reason/retryable/score-availability
provenance and a valid per-question score range. Speaking requires the typed
camel-case `SpeakingEvaluationResult` contract and current evidence/rubric
invariants. Legacy result, flat/band parsing and reuse status branches are
gone. Malformed current payloads, reserved direct-audio payloads,
transcription-low-confidence results, missing provenance and unsafe acoustic
rows remain fail closed and non-score-bearing. At this historical COMP-03/04
checkpoint Speaking remained transcript-only; branch B is opened only by the
later isolated contract in section 6.7.

Evidence (`2026-08-03`, JDK `17.0.19`):

- compile-only gate: `mvn -DskipTests package` -> `BUILD SUCCESS`;
- focused parser/reuse/presenter/service gate: `190` tests after retirement
  (`46` presenter tests separately rechecked while converging), all green;
- combined R/L/W/S result, service and AI contract gate: `329` tests,
  `0` failures, `0` errors, `0` skips;
- current-source scan across `src/main` and `src/test` returned zero hits for
  the retired mixed-envelope, feedback-map, legacy-result/reader/band/status,
  legacy ESSAY Speaking and mixed-field identities;
- the updated pre-14 scenario JSON validates with `jq -e`; its optional
  DB-backed seed test remained skipped because no disposable DB was supplied.

Real provider, storage and database calls remained `0/0/0`; Phase 14 remains
deferred.

### 6.6 COMP-05/06/21 version-lock and grouping closure

Status: `IMPLEMENTED_AND_FOCUSED_TESTED`.

Read-only caller audit first established that COMP-05 no longer had an active
production fallback: incomplete or incoherent attempt version locks are
rejected by snapshot loading, player delivery, re-evaluation and the canonical
attempt-state policy. The nullable V87 columns therefore remain schema history,
not permission to reconstruct mutable content. Existing focused tests preserve
the `INCOMPLETE_VERSION_LOCK` result, so no COMP-05 code deletion was needed.

For COMP-06, publication now rejects any question without canonical group
ownership before saving the published version. Objective overview/detail and
their DTO contract require non-null group and group-version IDs; the ungrouped
result fallback and its `legacyFallback` field were removed. This is an
application boundary only: no applied migration was rewritten and the blocked
UAT manifest still cannot load until its approved content/asset references are
materialized.

For COMP-21, the transcription authorization query was intentionally left
unchanged. It still binds media to owner, attempt, question, set, section,
Speaking skill, READY status and `g.id = q.groupId` plus
`g.sectionId = a.sectionId`. Canonical grouping now closes the availability
side without weakening authorization.

JDK 17 compile-only gate passed. Focused publication, objective result,
functional UI, transcription resolver, Speaking application and core service
gate passed `132/132`. One attempted DB-backed media-service invocation failed
before application context startup because `TEST_DB_URL` was absent; it made no
database/storage mutation and is not counted as evidence. Real provider,
storage and database calls remained `0/0/0`. The combined DB-free R/L/W/S
result, service and AI contract gate also passed `329/329`.

### 6.7 Speaking branch-B authorized dark-capture contract

Status: `IMPLEMENTED_AND_FOCUSED_TESTED / READINESS_RED / NO_SCORES`.

The first branch-B boundary is deliberately separate from the existing
transcript evaluator. `DirectAudioSpeakingEvaluationPort` is the only new
provider-facing audio port; `AUDIO_DIRECT_FULL_RESERVED` remains disabled and
was not repurposed. `DirectAudioSpeakingEvaluationService` requires all of the
following before it creates an authorized provider request:

- owner-bound, READY, non-deleted bytes whose SHA-256 matches their provenance
  digest;
- active purpose-specific consent, disclosure version and withdrawal support;
- explicit reviewer-audio access with least-privilege evidence;
- an allowlisted, verified provider profile with region, non-training,
  retention and deletion-SLA evidence IDs;
- approved Korean corpus, acoustic-calibration, fairness and repeatability
  evidence IDs; and
- `DARK_CAPTURE` rollout state.

Every missing, malformed, withdrawn, deleted, unauthorized or premature
score-enabled request is rejected before the provider port. The dedicated
request defensively copies bytes and binds the exact policy-bundle fingerprint
and deterministic cache identity. Its string representation and metadata-only
audit events redact audio bytes, handles, digests and provider request IDs.
The fake provider must return captured `audioConsumed=true`; otherwise the
result remains rejected. Even successful dark capture always returns
`DARK_CAPTURED_NON_SCORE_BEARING`: this slice has no pronunciation, fluency or
holistic score-release path.

Implementation plan and remaining boundaries:

1. `B1 AUTHORIZED_TRANSFER_CONTRACT` — this slice; fake/captured port only.
2. `B2 LIFECYCLE_AND_ACL` — persist consent/withdrawal/deletion-SLA and explicit
   reviewer grants with metadata-only audit; any schema change is forward-only
   after V87.
3. `B3 PROVIDER_PROFILE` — bind the port to an approved runtime allowlist and
   supplied legal/policy evidence without committing credentials or invented
   region/retention values.
4. `B4 CALIBRATION_AND_DARK_ROLLOUT` — materialize the approved Korean corpus,
   device/environment/voice coverage, calibration, fairness and repeatability
   evidence IDs; run dark evaluation and drift/repeatability gates.
5. `B5 SCORE_RELEASE` — separately approve pronunciation/fluency availability
   only after captured authorized consumption, deletion/withdrawal exercises,
   readiness green and Phase 15 manual UAT. Holistic score semantics are not
   introduced by this plan.

Focused JDK 17 evidence: `DirectAudioSpeakingEvaluationServiceTest` passed
`7/7`. It covers authorized byte capture, digest binding, defensive/redacted
transport, consent withdrawal/deletion, owner authorization, reviewer/provider/
calibration evidence absence, disabled/premature score rollout and unproven
provider consumption. Consent evidence, disclosure version and reviewer-policy
evidence now participate in the deterministic cache identity; a blank provider
receipt ID cannot prove consumption. The combined direct-audio, existing
transcript-client, rollout-readiness, score-policy and normalizer regression
gate passed `54/54`,
with `0` failures, errors or skips. Provider/storage/database calls were
`0/0/0`; no migration was changed. Readiness remains red because repository
tests use only clearly named `TEST-*` evidence and no real provider policy,
legal, corpus or calibration record was fabricated.

### 6.8 Branch-B lifecycle/ACL schema audit and decision boundary

Status: `B2_SCHEMA_AND_DOMAIN_CONTRACT_IMPLEMENTED / RUNTIME_UNWIRED`.

The post-B1 read-only audit found reusable controls but no persisted branch-B
authority. `practice_speaking_media` already carries immutable attempt/question
identity, content hash, optimistic lock, READY/deletion-pending/deleted states
and exact storage identity. Owner deletion enqueues the existing retryable
cleanup lifecycle. Owner playback is `STUDENT`-only and its repository query
binds user, attempt, question, media, READY status and allowed attempt status.
The transcription resolver has the stronger canonical set/section/group/skill
join recorded under COMP-21.

V87 had no purpose-specific learner consent/withdrawal journal or explicit
named reviewer-audio grant. Forward-only V88 now adds only those two
metadata-only authorities. Existing `PRACTICE_SPEAKING_EVALUATION` remains the
transcript purpose and was not repurposed. V1..V87 bytes remain unchanged.
V88 stores no audio bytes, storage keys, provider request IDs or credentials.

`DirectAudioAuthorizationLifecycleService` implements the matching fail-closed
domain contract without a Spring/controller/provider binding. Consent is
append-only and scoped to learner + attempt + the exact direct-audio purpose;
withdrawal immediately makes the latest authority inactive. Reviewer access
requires a separately injected grant authority, a named reviewer and attempt,
a bounded future expiry, and explicit revocation evidence. Self-grant is
rejected and there is no role-only fallback. Persistence adapters must
serialize writes per attempt; they are intentionally deferred until the
remaining runtime authority values below are supplied.

Exact values/ownership required before B2 runtime wiring and B3 provider work:

| Boundary | Required choice | Impact/risk | Recommended default |
| --- | --- | --- | --- |
| consent authority | Supply disclosure artifact ID/version and choose whether authority is attempt-scoped or media-scoped. | Account-wide or implicit consent is too broad; media-only consent can become incoherent after replacement. | Append-only consent event journal scoped to learner + attempt + exact direct-audio purpose; bind the selected media ID/digest at transfer time. |
| withdrawal after transfer | Decide whether withdrawal immediately requests provider deletion and how an outstanding request affects evaluator/reviewer access. | Allowing new transfer/read during deletion violates withdrawal; claiming deletion before confirmation is false. | Immediately block all new transfer/reviewer access, enqueue deletion, and remain `DELETION_PENDING` until provider confirmation or explicit terminal escalation. |
| reviewer grants | Name the role allowed to grant/revoke, maximum grant lifetime and whether self-review is forbidden. | A generic lecturer/admin role check is not explicit least privilege and can expose unrelated learner audio. | Named reviewer + attempt + purpose grant, bounded expiry, revocation timestamp, no role-only fallback, self-review denied by default. |
| provider profile | Supply direct-audio profile code, region/non-training/retention/deletion evidence IDs and numeric deletion SLA. | Invented values would make readiness appear green and hard-code an unsupported legal claim. | New direct-audio purpose binding; disabled by default; exact evidence bundle and SLA come from approved runtime configuration. |
| audit retention | Supply the retention term for consent/grant/transfer/deletion metadata. | Keeping raw handles/provider IDs leaks linkage; deleting all metadata prevents SLA and consent proof. | Append-only metadata audit with opaque digests and bounded error codes; never store bytes, raw storage keys, secrets or raw provider request IDs. |

JDK 17 focused evidence: lifecycle, B1 evaluator, V88 static and AIM migration
contracts passed `18/18` with no failure, error or skip. The static V88 contract
pins both tables, the separately named purpose and forward-only/metadata-only
constraints; the migration-chain contract is advanced to continuous `V1..V88`
while its historical digest locks remain unchanged. A fresh isolated MySQL 8.4
catalog named `ksh_test_pre15_b2_v88` then applied all `88` migrations with max
version `88`, zero failed rows, created exactly both B2 tables, and passed the
three-test Hibernate/Spring startup gate. Its dedicated container was stopped
and auto-removed after the read-only verdict query.

Until the remaining values are supplied, B1/B2 remain unwired production
contracts: no controller or provider adapter can transfer audio, and score
release is structurally unavailable.

The next isolated B2 slice adds `DirectAudioAuthorizationJdbcStore`, the
production V88 persistence adapter only. Consent lookup is deterministically
latest-event-first. Reviewer lookup requires named reviewer + attempt + exact
purpose, unexpired state and no revocation; mutation locking uses `FOR UPDATE`
and refuses to run outside an active transaction. Queries and mappings contain
no audio bytes, storage key, provider request ID or credential field. No
controller, lifecycle facade or evaluator wiring was added.

Adapter evidence (`2026-08-03`, JDK 17): focused lifecycle/evaluator/migration/
adapter gate passed `20/20`. The first fresh Spring startup exposed and rejected
a final-class CGLIB proxy incompatibility before any application operation; the
adapter was made proxy-compatible and the retry passed `KshApplicationTests`
`3/3` against the isolated V88 catalog. The catalog remained at 88 successful
migrations and its dedicated auto-remove container was stopped. This failed
startup is not counted as green evidence. Provider/storage calls remained
`0/0`; no shared database was contacted.

The B2 runtime coordinator is implemented behind
`app.practice.speaking-direct-audio.authorization.enabled=false`. Consent
authority queries exact learner ownership plus Speaking skill. Reviewer grants
require an active approved business-authority assignment, forbid self-grant/
reviewer ownership of the attempt, and default to a maximum `P7D` lifetime.
Every write is transaction-bound. No web route or evaluator/provider adapter
consumes this coordinator yet.

Coordinator evidence: the expanded focused gate passed `23/23`; tests pin
missing-config rejection, configured disclosure binding, owned-attempt scope,
named manager enforcement and bounded expiry. Fresh V1..V88 Spring/Hibernate
startup passed `4/4` and explicitly proved the coordinator bean is absent under
default configuration. The isolated catalog/container was then stopped and
auto-removed. Real provider/storage calls remained `0/0` and score release is
still structurally unavailable.

### 6.9 Direct-audio disclosure V1 and grant-manager authority model

Status: `PREPRODUCTION_BASELINE_MATERIALIZED / PROVIDER_FIELDS_UNRESOLVED`.

The product owner confirmed this is a self-created pre-production system with
no customer, institution or real trial population. The repository now carries
human-readable and machine-readable immutable disclosure artifact
`KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1`. It binds the exact branch-B purpose
and policy bundle, optional/default-unchecked consent, withdrawal, no-training,
named reviewer access, 30-day audio-retention ceiling, 7-day provider-deletion
SLA ceiling, 7-day reviewer-grant ceiling and fail-closed score release.

Provider display name, processing region, effective retention/SLA and evidence
IDs remain mandatory runtime disclosure fields. The artifact intentionally
does not invent a provider or legal claim. Minor/guardian use also remains
disabled until a separate lawful-consent flow exists.

Grant management is defined by stable business authorities rather than fake
development users: `ACADEMIC_LEADER` and `PRIVACY_RELEASE_OWNER`. An existing
KSH `LEADER` may receive the academic authority through an explicit assignment;
the role alone is not enough. Privacy/release ownership must likewise be
assigned to a separately named account when one exists; it must not silently
mean every `ADMIN`. No fake user row or production identity was created.

The disclosure/artifact plus B1/B2 regression gate passed `25/25` on JDK 17;
machine validation binds the implementation purpose/policy constants, limits,
default-unchecked consent, withdrawal and score-release blocker. The first
wording assertion exposed only a Markdown line-wrap mismatch and is not counted
as green evidence; whitespace-normalized learner-copy validation then passed.

Forward-only V89 replaces the temporary numeric-manager configuration seam
with an append-only authority event journal. It accepts only
`ACADEMIC_LEADER` and `PRIVACY_RELEASE_OWNER`, records explicit
`ASSIGNED/REVOKED` evidence and seeds zero identities. The coordinator resolves
the latest event per account and authority; only latest `ASSIGNED` is active.
The disclosure artifact ID and both authority codes are now safe defaults while
the outer authorization capability remains disabled by default.

JDK 17 authority/disclosure/B1/B2 gate passed `26/26`. Fresh isolated MySQL 8.4
applied continuous `V1..V89`, max version `89`, zero failed migrations and zero
grant-manager rows; Spring/Hibernate default-off startup passed `4/4`. The
container was stopped and auto-removed. V1..V88 bytes were not edited; provider
and storage calls remained `0/0`.

### 6.10 B3 direct-audio purpose in the existing Practice AI control plane

Status: `GENERIC_BINDING_IMPLEMENTED / CAPABILITY_TEST_RED / NO_PROVIDER_SELECTED`.

B3 reuses the existing Admin Practice AI provider-profile → model → purpose
binding flow. It does not introduce a second provider registry, global-AI
fallback, credential property or provider discovery request. The six existing
purposes remain unchanged and a seventh separately named purpose,
`PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION`, owns data class
`LEARNER_SPEAKING_AUDIO` and requires both `STRICT_JSON_SCHEMA` and the new
explicit `DIRECT_AUDIO_INPUT` capability.

Forward-only V90 extends the existing binding table with four exact policy
evidence identifiers: processing region, non-training, retention and deletion
SLA. It backfills `directAudioInput=false` into existing capability documents,
adds no profile/model/binding/credential row and preserves V1..V89 bytes. Both
the service and database reject a direct-audio binding without explicit audio
input capability. An enabled binding additionally requires every non-blank
policy evidence ID. The resolver repeats those checks before exposing a
runtime snapshot.

The Admin form remains simple-first: select an existing Practice-only profile,
enter the exact model and explicitly attest documented direct-audio input,
then bind the purpose. Existing text/STT/TTS presets are not presented as
direct-audio proof. No vendor/model is preselected and the page performs no
`/models` fetch. Region/non-training/retention/deletion-SLA evidence IDs stay
under advanced details. Missing evidence renders `Cần kiểm tra`; enabling is
rejected. Secrets are not added to either binding page or list response, and
the existing controller permission, CSRF forms, routes and no-global-fallback
contract remain intact.

This slice intentionally cannot make direct audio ready. The bounded
capability probe returns `DIRECT_AUDIO_DARK_ROLLOUT_REQUIRED` before transport,
so no generic button test can create a false green status or release a score.
The existing B1 data-plane gates for consent/disclosure, named reviewer access,
provider policy bundle, captured authorized audio consumption, calibration,
fairness, repeatability and dark rollout remain authoritative.

Dated evidence (`2026-08-03`, JDK 17):

- the focused codec/resolver/admin-service/presenter/template/static/migration
  suite passed `49/49` with zero failures/errors/skips;
- the broader source scan found no remaining six-purpose UI count and confirmed
  the central provider transport still rejects `/models`;
- fresh isolated MySQL 8.4 catalogs `ksh_test_pre15_b3_v90_ai` and
  `ksh_test_pre15_b3_v90_aim8` each validated and applied continuous
  `V1..V90`, max version `90`, zero failed Flyway rows;
- the AI control-plane persistence plus default-off Spring startup gate passed
  `5/5`, and the consolidated AIM-8 persistence gate passed `1/1`; and
- real provider/storage calls remained `0/0`. No shared database/object was
  mutated and no score-release path was added.

The remaining external B3 decision is deliberately narrow: choose a provider
profile and exact model only after official model documentation proves direct
audio input, then attach immutable evidence IDs for processing region,
non-training, retention and deletion SLA. Provider-family presets and model
names are convenience only, never readiness evidence.

### 6.11 B3 dual Gemini provider selection

Status: `BOTH_CANDIDATES_LOCKED / DEVELOPER_DARK_CONFIGURABLE / ENTERPRISE_AUTH_RED`.

The product owner selected both previously proposed Gemini deployment options.
This does not create two active evaluators or a fallback chain: the existing
`practice_ai_purpose_bindings` primary key still permits exactly one active
binding for `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION`. Switching profiles is
an explicit admin revision and therefore changes the binding/cache authority;
the other profile may coexist only as an unbound or inactive profile.

The repository-controlled catalog now accepts exactly these dated pairs:

| Candidate | Exact model/endpoint contract | Runtime disposition |
| --- | --- | --- |
| `GEMINI_DEVELOPER_DIRECT_AUDIO` | `gemini-3.6-flash` at `https://generativelanguage.googleapis.com/v1beta/openai` | Existing static-bearer transport is technically compatible. It may be configured only behind all B1/B2/B3 evidence and remains dark/non-score-bearing. |
| `GEMINI_ENTERPRISE_DIRECT_AUDIO` | `gemini-3.5-flash` at a concrete Google `aiplatform.googleapis.com` project/location `.../endpoints/openapi` endpoint | Binding may be saved disabled. Enabling/resolution fails with `DIRECT_AUDIO_ENTERPRISE_ADC_ADAPTER_REQUIRED`; Google Cloud OAuth/ADC uses short-lived access tokens, so the static API-key field must not masquerade as production Enterprise authentication. |

Official documentation reviewed on `2026-08-03` records Developer API
OpenAI-compatible bearer authentication, `gemini-3.6-flash`, direct
`input_audio` and structured output:
`https://ai.google.dev/gemini-api/docs/openai`. Google Cloud documents the
OpenAI-compatible endpoint, Google Cloud auth/ADC and short-lived token refresh
at `https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/migrate/openai/auth-and-credentials`.
The Enterprise `gemini-3.5-flash` model card records audio input, structured
output, Chat Completions, supported processing regions and GA lifecycle at
`https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/3-5-flash`.

Backend enforcement rejects arbitrary/lookalike hosts, placeholder
project/location paths, wrong model/endpoint combinations, insecure HTTP and
Enterprise enable/toggle attempts through a static bearer. The Admin model
picker exposes both choices without `/models` discovery and explains the
Enterprise ADC blocker. Matching a catalog candidate still supplies no legal
or privacy evidence: region, non-training, retention and deletion-SLA evidence
IDs remain mandatory and no policy value was fabricated.

JDK `17.0.19` focused catalog/admin/resolver/template/transport gate passed
`29/29`. The consolidated B1/B2/B3 regression gate passed `55` tests with
`50` executed green and `5` existing DB/auth integration guards skipped for
missing disposable-DB configuration. The initial focused run exposed one
test fixture that paired the Enterprise endpoint with the Developer model; the
catalog rejected it as designed, the fixture was corrected, and the green
rerun is the accepted evidence. Real provider/storage/database calls remained
`0/0/0`; no migration, dependency, credential, score-release path or shared
state changed.

Next isolated boundary: implement a Google Cloud ADC credential-source port
and metadata-safe authorization adapter before Enterprise can be enabled. That
slice must use fake token-source tests, avoid logging tokens, recheck the
dependency/SBOM delta if a Google auth library is introduced, and still stop
before any provider request. Developer dark rollout separately remains blocked
on real policy evidence IDs, consent/grants and approved calibration artifacts.

### 6.12 B3 credential-mode persistence boundary

Status: `V91_APPLIED / ADC_SECRETLESS_PROFILE_IMPLEMENTED / TOKEN_SOURCE_UNWIRED`.

The dual-provider audit found that the original profile form/database required
a static credential for every OpenAI-compatible endpoint. That contract was
correct for Gemini Developer but unsafe and misleading for Enterprise: Google
Cloud ADC issues short-lived access tokens and must not be represented by a
long-lived value in `credential_secret`.

Forward-only V91 adds exact profile credential modes `STATIC_BEARER` and
`GOOGLE_CLOUD_ADC`. Existing rows deterministically remain static bearer.
Database constraints require non-blank credential material for static bearer
and require `credential_secret IS NULL` for ADC. No profile, identity, token,
project or region is seeded. V1..V90 bytes remain unchanged.

The existing Admin profile flow now exposes the two authentication choices.
ADC disables and clears the password field in the browser; the controller and
service independently accept a new ADC profile without a secret and reject any
attempt to persist token material as `ADC_PROFILE_MUST_NOT_STORE_SECRET`.
Profile list/read DTOs expose only the credential mode, never the credential.
Creating either named direct-audio candidate routes to the existing direct-
audio purpose binding rather than an unrelated PDF purpose. A provider/model
binding must match the catalog's expected credential mode.

Enterprise remains fail closed. The resolver validates the concrete Vertex
endpoint/model and the `GOOGLE_CLOUD_ADC` mode, then returns
`DIRECT_AUDIO_ENTERPRISE_ADC_ADAPTER_REQUIRED` before transport because no
production token source is wired. Developer continues to require
`STATIC_BEARER`. No access token, placeholder secret or real Google call was
introduced.

Dated `2026-08-03` JDK `17.0.19` evidence:

- consolidated profile/admin/catalog/resolver/UI/B1/B2/migration-chain gate
  ran `79` tests: `75` executed green and `4` existing authorization guards
  skipped because that DB-backed role fixture was not enabled;
- fresh disposable MySQL 8.4 catalog `ksh_test_pre15_b3_v91` validated and
  applied continuous `V1..V91`, exactly `91` successful rows, min/max `1/91`
  and zero failures;
- the V91 control-plane persistence test plus Spring/Hibernate startup passed
  `5/5`; the schema proved `credential_mode NOT NULL DEFAULT STATIC_BEARER`,
  nullable secret storage governed by the cross-field check, and a disposable
  ADC profile persisted with SQL `credential_secret IS NULL`; and
- the tmpfs-backed dedicated container was stopped and auto-removed. The
  pre-existing evidence container was not touched.

Real provider/storage calls remained `0/0`; the only database mutation was the
explicitly named disposable catalog. No dependency or score-release path was
added. The remaining Enterprise boundary is now narrower: supply a production
ADC token-source adapter with refresh/expiry/audience/scope checks and redacted
fake-port tests. Any library addition requires a dated SBOM/advisory delta.

### 6.13 B3 custom-model draft and capability-verification boundary

Status: `CUSTOM_MODEL_DRAFT_ALLOWED / UNVERIFIED_ENABLE_FAIL_CLOSED / NO_PROVIDER_DISCOVERY`.

User clarification makes the Admin model catalog advisory rather than an
exhaustive persistence allowlist. The exact Gemini Developer and Enterprise
pairs remain `VERIFIED_PRESET`, backed by the repository artifact
`KSH_PRACTICE_DIRECT_AUDIO_CAPABILITY_VERIFICATION_V1`. That artifact maps
separate immutable IDs for audio input, strict structured output and compatible
auth/endpoint to the dated official documentation. It explicitly does not
claim region, non-training, retention or deletion-SLA approval; those four
binding evidence IDs remain independently mandatory.

Any custom or newly released provider/model is assessed as `UNVERIFIED` without
name-based inference. Admin may save it only as a disabled draft. Both the
enable service boundary and runtime resolver return
`DIRECT_AUDIO_CAPABILITY_VERIFICATION_REQUIRED`; therefore even a draft with
all four policy IDs cannot resolve credentials or reach transport. Adding a
future verified model requires an intentional registry/artifact review, not a
provider name match or an Admin checkbox.

The responsive Admin picker now labels presets `Gợi ý đã xác minh`, accepts
free model text, and announces `Model tùy chỉnh · Cần kiểm tra` through an
accessible live status. The list page also distinguishes verified presets from
custom drafts. The page still performs no `/models` fetch, renders no secret,
has no global-AI fallback, and leaves direct-audio dark rollout default-off.

Dated `2026-08-03` JDK `17.0.19` evidence:

- focused registry/admin/resolver/status/template/static command
  `mvnw -Dtest=PracticeDirectAudioCapabilityRegistryTest,PracticeAiControlPlaneAdminServiceTest,PracticeAiControlPlaneContractTest,PracticeAiBindingStatusPresentationTest,AdminSettingsInformationArchitectureStaticContractTest,PracticeAiControlPlaneStaticContractTest test`
  passed `41/41`, including JSON parsing and registry-to-artifact ID mapping;
- the wider command
  `mvnw -Dtest=PracticeAi*Test,DirectAudio*Test,SpeakingProviderRolloutReadinessTest,ProviderOperationalReadinessPolicyTest,AdminSettingsInformationArchitectureStaticContractTest test`
  passed `98` tests: `92` executed green and `6` existing DB/auth guards skipped;
- source scans found no current `PracticeDirectAudioProviderCatalog`, old
  `DIRECT_AUDIO_PROVIDER_MODEL_UNVERIFIED`, or UI claim that only two pairs may
  be entered;
- `mvnw -DskipTests package` completed green on JDK 17; and
- real provider/storage calls and database mutations remained `0/0`; this slice
  adds no migration or dependency and releases no scores.

Verdict: `GREEN` for custom-model draft persistence plus fail-closed
verification. Enterprise remains `ADC_ADAPTER_REQUIRED`; a custom model remains
red until its technical artifact entry and all provider-policy evidence are
reviewed and added.

### 6.14 B3 Enterprise short-lived credential and captured-transport boundary

Status: `ADC_PORT_DEFINED / PRODUCTION_TOKEN_SOURCE_DISABLED / CAPTURED_TRANSPORT_GREEN / NO_GOOGLE_CALL`.

The existing purpose-bound HTTP transport accepts a static bearer secret and
is shared by text/STT/TTS flows. It was not repurposed for Enterprise audio.
This slice adds the separately named
`GoogleCloudShortLivedAccessTokenSource` and
`GeminiEnterpriseDirectAudioEvaluationAdapter` boundaries. No Google auth
dependency was necessary for this contract slice, so the Spring Boot `3.5.16`
support line, dependency graph and SBOM remain unchanged.

The only production token-source bean is deliberately disabled and returns
`GOOGLE_CLOUD_ADC_WORKLOAD_IDENTITY_UNAVAILABLE`. It reads no environment
credential, service-account JSON, private key, access token or application
property. The Enterprise evaluator itself is deliberately not a Spring bean,
and contains no HTTP client; production audio cannot reach Google until an
approved workload-identity source and captured transport are implemented and
wired.

The request-local token contract binds exact resource audience, the sole
`cloud-platform` scope, project, location, concrete HTTPS Vertex endpoint and
credential-mode revision. Tokens with blank material, no more than 60 seconds
remaining, wrong audience, a different scope set, project, location, endpoint
or revision fail before transport. Technical registry evidence and all four
provider-policy evidence IDs are required again at the Enterprise adapter
boundary. Profile revision is the current credential-mode revision authority,
so any profile/auth-mode change invalidates previous provenance.

Provider request provenance includes provider/binding revisions, credential
mode/revision, audience, scope, project, location, endpoint, technical evidence
IDs and policy-bundle fingerprint. Only its SHA-256 digest is appended to the
governed cache identity; token, project/location and raw provenance do not
appear in that cache key. Token and audio byte arrays are redacted from request
string representations, and the existing direct-audio audit events contain
neither.

Dated `2026-08-03` JDK `17.0.19` evidence:

- focused token/security/service/resolver command
  `mvnw -Dtest=GoogleCloudAdcBoundaryStaticContractTest,GeminiEnterpriseDirectAudioEvaluationAdapterTest,DirectAudioSpeakingEvaluationServiceTest,PracticeAiControlPlaneContractTest,PracticeAiControlPlaneAdminServiceTest,PracticeDirectAudioCapabilityRegistryTest test`
  passed `38/38`;
- captured fake transport tests proved a fully governed dark request transfers
  exactly once, remains non-score-bearing, and produces redacted audit/request
  output; withdrawn consent issues zero tokens and transfers zero audio;
- blank/expired/wrong-audience/wrong-scope/wrong-project/wrong-location/
  wrong-endpoint/wrong-revision tokens each produced a bounded rejection with
  zero transport calls;
- combined B1/B2/B3/control-plane/readiness command
  `mvnw -Dtest=PracticeAi*Test,DirectAudio*Test,GoogleCloud*Test,GeminiEnterprise*Test,SpeakingProviderRolloutReadinessTest,ProviderOperationalReadinessPolicyTest,AdminSettingsInformationArchitectureStaticContractTest test`
  passed `106` tests: `100` executed green and `6` existing DB/auth guards
  skipped; and
- `mvnw -DskipTests package` completed green. Real provider/storage/DB calls,
  dependency additions, migrations and score releases remained `0`.

Exact next blocker audit: `DirectAudioSpeakingEvaluationPort.Receipt` still
contains only request/consumption/cache proof, and the Enterprise captured
transport has no provider response body or response schema. The current
`SpeakingEvaluationNormalizer` and rubric contract are transcript-grounded;
they intentionally suppress acoustic criteria, pronunciation/fluency scoring
and holistic score availability. The next isolated slice must therefore define
a separately versioned direct-audio strict JSON schema, bounded provider
response decoder, acoustic evidence/rubric reconciliation and non-score-bearing
normalizer. Score release must remain off until real captured responses plus
approved calibration/fairness/repeatability evidence satisfy that contract;
none of those results are fabricated here.

### 6.15 B3 strict acoustic observation contract boundary

Status: `STRICT_V1_OBSERVATIONS_GREEN / DARK_ONLY / PRODUCTION_CALIBRATION_RED / NO_SCORE_RELEASE`.

This slice defines the separately versioned
`ksh-speaking-direct-audio-acoustic-v1` JSON contract. The provider response is
an observation envelope, not a backend grading result: it carries exactly the
Korean locale, direct-audio evidence mode, evaluator/model/capability identity,
policy bundle, immutable calibration references, audio-consumption receipt,
pronunciation and fluency observations, timestamped evidence spans and provider
confidence. Every object is closed to unknown fields. The release section is
fixed to `eligible=false` and `DARK_ROLLOUT_ONLY`; holistic score and attempt
points are absent from both schema and result type.

`DirectAudioAcousticProviderResponseParser` first applies the existing strict
structured-response envelope checks, including completion state and outer
provider request identity. `DirectAudioAcousticResponseNormalizer` then accepts
only the exact current contract and exact governed request context. It rejects
malformed or transcript-only payloads, unsupported language, wrong
evaluator/model/capability/policy/calibration, unavailable calibration,
unproven audio consumption, receipt/provenance/cache mismatches, invalid or
duplicate evidence spans, incomplete/duplicate dimensions, values outside
`0..1`, and inconsistent provider total/confidence. Rejections are bounded and
non-score-bearing.

No scoring weights, fairness thresholds or Korean corpus conclusions were
invented. The immutable calibration authority requires corpus, acoustic,
fairness and repeatability evidence IDs, while the only production bean returns
no profile. The test authority and fixture use conspicuous `TEST-*` identities
and approve observation parsing only; any profile claiming score-release
approval is rejected. The dark result constructor independently forces
presenter eligibility and score-release eligibility false and forces holistic
score/attempt points to null. Static consumer scans prove the new acoustic
result/parser/version are not referenced by `SpeakingResultPresenter`,
`PracticeService`, `PracticeProgressService`, result DTOs or the current
Speaking score policy.

Dated `2026-08-03` JDK `17.0.19` evidence:

- focused acoustic/Enterprise/service command
  `mvnw -Dtest=DirectAudioAcousticResponseNormalizerTest,DirectAudioAcousticContractStaticTest,GeminiEnterpriseDirectAudioEvaluationAdapterTest,DirectAudioSpeakingEvaluationServiceTest test`
  passed `21/21`;
- captured fixtures include one internally consistent dark observation and
  adversarial unknown-field, transcript-only, wrong model/capability/policy/
  calibration, missing/false receipt, invalid timestamp/range/total/confidence,
  unsupported-language and attempted-score-release cases;
- strict provider-envelope cases cover malformed JSON, incomplete generation,
  response/request receipt mismatch and one valid captured fake response;
- combined B1/B2/B3/control-plane/readiness/acoustic command
  `mvnw -Dtest=PracticeAi*Test,DirectAudio*Test,GoogleCloud*Test,GeminiEnterprise*Test,*Acoustic*Test,SpeakingProviderRolloutReadinessTest,ProviderOperationalReadinessPolicyTest,AdminSettingsInformationArchitectureStaticContractTest test`
  passed `113` tests: `107` executed green and `6` existing DB/auth guards
  skipped; and
- real provider/storage/DB calls, dependency additions, migrations and learner
  score releases remained `0`.

Remaining evidence is deliberately external and exact: capture real response
envelopes from the selected endpoint/model with matching request receipts;
validate schema stability across the approved Korean device/environment/voice
corpus; issue immutable corpus/acoustic/fairness/repeatability evidence IDs;
and approve any weights, acceptance thresholds and release policy. Until then
production calibration resolution is empty and readiness remains red.

Next isolated boundary audit: determine whether dark observations can be
persisted and exposed to explicitly authorized reviewers without importing a
learner score path. Any persistence must be a new forward-only migration and
must store bounded observation/provenance data without raw audio, tokens or
secrets. Learner presenter/progress/result surfaces and score release must stay
disconnected.

### 6.16 B3 dark-observation persistence and reviewer inspection boundary

Status: `V92_APPLIED / REVIEWER_GRANT_ENFORCED / NON_SCORE_BEARING / NO_WEB_ROUTE`.

Forward-only V92 adds
`practice_speaking_direct_audio_dark_observations`; V1..V91 bytes remain
unchanged. Capture uses `INSERT ... SELECT` restricted to a real Speaking
attempt. The record contains contract/evaluator/calibration identities,
provider total/confidence, a backend-built numeric/timestamp evidence
projection, and SHA-256 receipt/cache fingerprints. It contains no raw audio,
linguistic/free-text observation, raw provider request ID, token, credential,
storage key, holistic score or attempt points.

The 30-day ceiling comes from the approved
`KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1` artifact and is enforced both before
write and by the database check. Deleted or expired observations cannot be
read. The JDBC read embeds an `EXISTS` check for an active, non-revoked,
unexpired reviewer grant on the same attempt and exact direct-audio purpose;
there is no role-wide fallback. The reviewer view hard-codes score release
false and exposes no holistic/attempt score. No controller, learner route,
presenter, progress service or result DTO consumes this boundary.

Dated `2026-08-03` JDK `17.0.19` evidence:

- focused persistence/authorization/acoustic command passed `17/17` after a
  source assertion was corrected to inspect schema identities rather than a
  negative word appearing only in a migration comment;
- a fresh tmpfs MySQL 8.4 container and catalog `ksh_test_pre15_v92` applied
  continuous V1..V92: `92` successful rows, min/max `1/92`, zero failures,
  exactly one dark-observation table and zero observation rows;
- Spring/Flyway/Hibernate startup passed `4/4`. Its first startup caught that
  the new `@Repository` had been declared final and could not receive Spring's
  exception-translation proxy; the class was corrected and the accepted rerun
  validated all 92 migrations and the full context;
- combined B1/B2/B3/control-plane/readiness/acoustic/persistence command passed
  `119` tests: `113` executed green and `6` existing DB/auth guards skipped;
  the migration ceiling/continuity guards were intentionally advanced from V91
  to exact V92 and remain closed to V93+; and
- the disposable container was stopped and auto-removed. Real provider/storage
  calls, shared-DB mutations, dependencies and learner score releases remained
  `0`.

This slice deliberately stops before a web inspection page and before physical
retention cleanup. A future reviewer UI must preserve the same named grant,
CSRF/auth and ranged-audio controls. A deletion worker must claim due rows and
record deletion evidence before production capture can be enabled; expiry
already hides due rows fail closed.

### 6.17 Cross-skill strict-output audit and Korean alignment contract

Status: `DATED_MATRIX_COMPLETE / ALIGNMENT_V1_DARK_GREEN / TEXT_REPLACEMENT_RETRY_OPEN / NO_LEARNER_UI`.

The dated evidence artifact
`practice-ai-contract-robustness-and-korean-alignment-2026-08-03.md` maps the
actual Writing, R/L explanation, transcript Speaking, direct-audio acoustic and
future alignment paths from request schema through provider capability,
decoder/domain parser and retry policy. Writing, R/L and transcript Speaking do
send the complete domain schema as OpenAI-compatible
`response_format.type=json_schema` with `strict=true`; the shared adapter also
requires the resolved binding to advertise strict JSON schema and forbids
plain-JSON/tool/streaming fallback. An explicit Admin capability test can send
a bounded strict fixture. Neither a stored capability flag nor that small
fixture proves every production-sized schema/model response.

The audit also records the unresolved robustness gap instead of calling it
green. Current transport retries only HTTP `429/500/502/503/504` within the
binding's `0..3` retry limit. An HTTP-200 refusal, truncation, malformed
structured output or downstream schema mismatch receives no bounded complete-
replacement retry. Writing and R/L keep score/publication safe by rejecting the
whole response, but independent findings/rationales are still coupled to one
invalid leaf and there is no cross-skill `PARTIAL_NON_SCORE` status. Writing's
contract identity string still says `max-retries=5` even though the authoritative
control plane allows at most `3`; that stale identity is an exact next-slice
correction, not current retry evidence.

The separate closed schema `ksh-speaking-korean-alignment-v1` models Korean
`EOJJEOL`, `SYLLABLE`, `JAMO` and `PHONEME` spans without importing English IPA
assumptions. It binds transcript token/UTF-16 identity to authorized audio time
ranges, expected/observed pronunciation, confidence, issue code and evidence
provenance. Grammar and lexical issue codes are absent; those remain in the
transcript-language analysis. The engine must identify a dedicated forced
aligner or ASR word-timestamp component. `LLM_ONLY` is invalid, and no Gemini
model is claimed to guarantee phoneme timestamps.

The alignment normalizer implements the requested tiers: audio/transcript/
engine/policy/calibration/release identity is atomic; independent spans are
validated item-by-item; one invalid child produces `PARTIAL_NON_SCORE` while
retaining safe siblings; no valid spans or a critical mismatch yields
`UNAVAILABLE`. Every result constructor forces score release and learner
visibility false and clears playback URL, holistic score and attempt points.
Static scans show no learner presenter/progress/result/storage consumer and no
per-word audio object path.

Dated `2026-08-03` JDK `17.0.19` focused alignment/acoustic/control-plane/
evidence command passed `22/22`. Fake fixtures cover a valid eojjeol+syllable
hierarchy and adversarial wrong audio, LLM-only engine, score-release attempt,
raw-URL field, invalid child timestamp, grammar-as-acoustic issue and orphan
child. Real provider/storage/DB calls, migrations, dependencies, audio objects,
web routes and score releases remained `0`.

The combined B1/B2/B3/control-plane/readiness/acoustic/persistence/alignment
selector passed `126` tests: `120` executed green and `6` existing DB/auth
guards skipped. `mvnw -DskipTests package` also completed green.

Current playback supports an owner-authorized application route with no-store
headers and HTTP byte ranges over the original private audio; it exposes no raw
storage key. Target word/phoneme playback must seek within that one authorized
audio, not create a file per span. Reviewer playback/time-to-byte mapping and
signed/ranged authorization do not yet exist; learner exposure remains behind
the score-release/readiness gate.

Exact blockers: select and verify a Korean forced-aligner/ASR timestamp
component; capture eojjeol/syllable/jamo/phoneme evidence across the approved
corpus; issue immutable capability/calibration IDs; implement reviewer ranged
playback plus withdrawal/deletion propagation; and separately add one bounded,
cost-controlled full-replacement retry/status contract for Writing and R/L.
No malformed provider payload may be recursively repaired or merged into a
score-bearing response.

### 6.18 Bounded structured-response full replacement

Status: `ENVELOPE_REPLACEMENT_GREEN / SHARED_COST_CEILING_GREEN / DOMAIN_SCHEMA_RETRY_OPEN`.

The shared purpose-bound structured-generation adapter now permits at most one
complete replacement after an HTTP-200 refusal, truncation, malformed/non-object
structured output or empty content. HTTP retries and that replacement share the
existing binding authority ceiling of `1 + maxRetries` (`1..4` total transport
calls); a replacement is not attempted when an HTTP retry has consumed the
remaining capacity. The stale Writing contract marker `max-retries=5` was
replaced with the purpose-binding retry authority identity.

Replacement is a new strict-schema request over the original trusted input. It
uses a derived idempotency key, revalidates the exact binding, and owns a
separate execution-audit row and reason. The rejected provider payload is not
quoted or replayed, fields are never patched/merged, and a rejected replacement
cannot recurse into another replacement. No global provider fallback, real
provider call, secret, storage call, DB mutation, migration or score release was
introduced.

Dated `2026-08-03` JDK `17.0.19` focused command:

`mvnw -Dtest=PracticeControlPlaneStructuredGenerationAdapterTest,StrictOpenAiStructuredResponseDecoderTest,WritingEvaluationClientTest,ReadingListeningExplanationClientTest,OpenAiCompatibleSpeakingEvaluationClientTest,PracticeAiRobustnessEvidenceStaticTest test`

passed `76/76`. Captured fake tests prove distinct idempotency/audit identity,
absence of the refusal body from the replacement request, one replacement only,
zero-retry no-replacement, and no replacement after a retryable HTTP response
has exhausted the shared call budget.

The combined B1/B2/B3/control-plane/Writing/R/L/Speaking/acoustic/persistence/
alignment selector passed `201` tests: `195` executed green and `6` existing
DB/auth guards skipped.

This closes envelope-level replacement only. Domain validators run after the
shared port returns, so a Writing evidence/rubric mismatch, R/L evidence/
coverage mismatch or transcript-Speaking rubric contradiction cannot yet
request a bounded full replacement safely. The next safe design boundary is a
domain-owned replacement coordinator or validator callback with the same hard
budget and explicit `COMPLETE`, `PARTIAL_NON_SCORE` and `UNAVAILABLE` result;
Writing/R/L independent diagnostics also still need item-level validation.

### 6.19 Unified Practice AI result completeness

Status: `COMPLETENESS_V1_GREEN / DIRECT_AUDIO_PARTIAL_DARK_ONLY / WRITING_RL_SCORE_ATOMIC / NO_MIGRATION`.

The single domain model `practice-ai-result-completeness-v1` now defines only
`COMPLETE`, `PARTIAL_NON_SCORE`, and `UNAVAILABLE`, with a bounded reason code
and rejected-item count. The dated matrix
`practice-ai-result-completeness-matrix-2026-08-03.md` maps producer, persisted
JSON envelope/cache, current reader, presenter and score consumer for Writing,
R/L explanations and direct-audio Speaking. Existing queue/artifact lifecycle
states remain operational state, not a parallel result authority.

Writing normalized feedback now persists completeness in the same per-question
JSON used by attempts and cache. The parser rejects missing, malformed, future,
or score/status-inconsistent metadata as non-current. `scoreAvailableFlag`, the
exact policy predicate, progress consumers and result presenter remain gated on
`COMPLETE`. A partial payload carrying fake numeric fields renders those fields
as `null`, has no rubric score/latest/best/progress authority, and is labelled
`PARTIAL` rather than zero or failed. Independent diagnostics may remain in the
view, but current Writing producer findings are still atomically rejected
because their evidence graph participates in criterion coverage.

R/L strict v4 output receives backend-owned `COMPLETE` metadata only after the
full strategy, official-answer coverage and evidence contract validates. The
current reader requires that exact metadata; missing/malformed/non-complete v4
does not count ready or reach Result Detail. Historical v2/v3 readers remain
explicitly versioned and are not treated as current v4. R/L has no learner score
and no unsafe claim/evidence salvage was added.

Direct-audio keeps provider/model/policy/calibration/receipt, both acoustic
dimensions, totals and confidence atomic. Independent evidence spans now
validate item-by-item: an invalid sibling is dropped and yields reviewer-only
`PARTIAL_NON_SCORE`; losing all evidence for a dimension rejects the whole
result with its original reason. V92 `observation_payload` stores the exact
status/reason/count. Reviewer inspection re-parses it and hides legacy,
malformed or unavailable payloads. Score-release eligibility and learner
presenter eligibility remain false, with holistic score and attempt points
forced null.

No schema migration was required: the authoritative stores already persist
canonical JSON envelopes. V1–V92 migration bytes were not modified. Real
provider/storage/shared-DB calls, secrets and learner acoustic score releases
remained `0`.

Dated `2026-08-03`, JDK `17.0.19` focused producer/parser/presenter/progress/
transport gate passed `167/167`. The combined Writing/R/L/Speaking AI,
replacement, artifact lifecycle, result presenter, service and progress gate
passed `572` tests: `566` executed green and `6` existing DB/auth guards
skipped. Refusal, truncation, malformed envelope, replacement exhaustion,
missing/future completeness, inconsistent score status and invalid independent
evidence are covered with fake/adversarial inputs only.

The remaining safe boundary is reviewer-only dark-result inspection plus
authorized ranged playback. It must reuse the named active reviewer grant,
consent/withdrawal/deletion state and the original private audio object; no raw
URL, storage key, per-word audio file or learner acoustic score may be exposed.

### 6.20 Reviewer-only dark audio range playback

Status: `V93_FORWARD_ONLY / REVIEWER_RANGE_PLAYBACK_GREEN / DEFAULT_OFF / NO_LEARNER_SCORE`.

The dated boundary evidence
`practice-direct-audio-reviewer-playback-boundary-2026-08-03.md` closes the
previously audited service/transport gap without adding a learner route or UI.
Forward-only V93 binds every newly captured V92-style dark observation to its
exact `attempt_id`, `question_id` and `media_id`. V1–V92 bytes remain unchanged;
historical V92 rows without that binding remain non-playable rather than being
guessed into a current authorization state.

The separately named, default-off direct-audio reviewer route does not reuse
the STUDENT owner endpoint. On every open it resolves a descriptor only when
all independent factors match: the requester's named, exact-purpose reviewer
grant is unrevoked and unexpired; the latest consent event is `GRANTED` (so a
later withdrawal wins); the linked dark observation is unexpired and undeleted;
the exact media is still private `READY` Speaking media; and the stored
attempt/question/media binding agrees. Any missing or malformed factor becomes
the existing bounded not-found response before storage is opened. The controller
permits only HTTP byte ranges over the original private object with no-store,
private cache headers; it returns neither URL/presign, storage key, audio token
nor a score field.

JDK `17.0.19` focused service/controller/authorization/migration test command
passed `25/25` with no provider, storage or DB call:

`mvnw -Dtest=DirectAudioDarkObservationServiceTest,DirectAudioDarkObservationPersistenceStaticTest,DirectAudioReviewerPlaybackServiceTest,DirectAudioReviewerPlaybackControllerTest,PracticeAim8CompatibilityStaticContractTest,PracticeAim7PdfAuthoringStaticContractTest test`

The wider non-network branch-B authorization/acoustic/alignment selector also
passed `55/55` on the same JDK17:

`mvnw -Dtest=DirectAudio*Test,KoreanDirectAudio*Test,GeminiEnterpriseDirectAudioEvaluationAdapterTest,PracticeAiResultCompletenessStaticTest test`

The wider existing Spring MVC media selector was also invoked but stopped at
the repository's `DisposableTestDatabaseEnvironmentGuard` because `TEST_DB_URL`
is unset. It made no DB connection; this is correctly an environment setup
failure, not a reason to select a shared database. A disposable-DB V93 migration
rehearsal remains required before enablement.

No reviewer UI, audit-event presenter, real provider/storage call, secret,
direct-audio score release or learner playback exposure was added. Provider
policy evidence, captured provider consumption, Korean corpus/calibration/
fairness/repeatability evidence and explicit dark-rollout approval remain red.

### 6.21 Consent withdrawal closes every reviewer read path

Status: `WITHDRAWAL_READ_GATE_GREEN / NO_MIGRATION / NO_SCORE`.

The post-V93 audit found that reviewer ranged playback already derived the
latest direct-audio consent event, but the reviewer-only dark-observation
inspection query had only enforced named grant and retention. The JDBC reader
now joins the owned Speaking attempt and requires that the latest event for the
same learner, attempt and exact direct-audio purpose is `GRANTED`. A later
`WITHDRAWN` event therefore makes both the numeric diagnostic inspection and
the private-audio range route return no descriptor. This adds no audio transfer,
provider/storage call, controller/UI or score field, and V1–V93 remain
unchanged.

The JDK `17.0.19` direct-audio focused selector passed `55/55` for this
boundary; the immutable query/static gate covers latest-event ordering, exact
purpose, grant expiry/revocation, retention/deletion and the no-learner-surface
scan.

The targeted JDK `17.0.19` withdrawal/authorization/inspection/playback gate
passed `22/22` with mocks/static fixtures only:

`mvnw -Dtest=DirectAudioAuthorizationCoordinatorTest,DirectAudioAuthorizationLifecycleServiceTest,DirectAudioAuthorizationJdbcStoreTest,DirectAudioDarkObservationPersistenceStaticTest,DirectAudioDarkObservationServiceTest,DirectAudioReviewerPlaybackServiceTest,DirectAudioReviewerPlaybackControllerTest test`

### 6.22 Consent-withdrawal private-audio cleanup

Status: `V94_FORWARD_ONLY / DURABLE_CLEANUP_ENQUEUED / WORKER_DEFAULT_OFF / NO_PROVIDER_CALL`.

The raw private-audio audit found that withdrawal already blocked all future
reviewer reads, but physical objects depended on unrelated owner/discard paths.
Forward-only V94 adds the exact `CONSENT_WITHDRAWAL` cleanup reason and a bounded
`authorization_evidence_id` to the existing durable Speaking cleanup task. It
does not add audio bytes, URLs, provider identifiers, credentials or score
fields. V1–V93 bytes remain unchanged.

`DirectAudioAuthorizationCoordinator.withdrawConsent` now performs three steps
inside one transaction: append the immutable `WITHDRAWN` event, logical-delete
the attempt's dark observations using the learner/evidence identity, then lock
the owned Speaking attempt and every associated media row, move live media to
`DELETION_PENDING`, and enqueue immediate exact-profile cleanup. The existing
worker owns retry/lease/terminal behavior and marks a media row `DELETED` only
after physical storage deletion is confirmed. Wrong owner/skill, malformed
evidence and non-transactional enqueue fail closed. A previously deleted media
row is not re-enqueued.

A fresh disposable MySQL 8.4 container/database named
`ksh_test_pre15_v94_20260803` applied and validated exactly `94` Flyway
migrations. The Spring cleanup integration gate passed `18/18`, including the
new persisted reason/evidence assertion. The container used a `768 MiB` memory
limit and was stopped with `--rm`; the disposable database and synthetic test
credential were removed with it. No shared database or real storage/provider
was accessed.

The non-DB JDK `17.0.19` branch-B authorization/acoustic/alignment/cleanup gate
passed `77/77`; `mvnw -DskipTests package` also passed. The migration rehearsal
detected and closed a Spring constructor
selection defect in the default-off reviewer playback service by marking its
production constructor explicitly for injection.

Operational enablement remains red: the existing cleanup worker and reviewer
playback endpoint are default-off; real provider-side deletion acknowledgment,
provider deletion SLA evidence and production monitoring/escalation have not
been fabricated. Learner acoustic score release remains unavailable.
