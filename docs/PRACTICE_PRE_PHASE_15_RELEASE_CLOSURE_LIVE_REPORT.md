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
gap and no duplicate. No migration after V87 exists. The canonical upstream
historical bytes and the integrated Practice compaction byte are:

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
| `speaking_mixed_v1` / `essay_feedback_by_question` | `REVIEW_REQUIRED` | Still-active compatibility envelope; fresh DB has no retained rows to prove removal safe. |
| `SpeakingFeedbackCompatibilityReader` / `LEGACY_RESULT` | `REVIEW_REQUIRED` | Read/reuse boundary for old payloads; requires retained-payload inventory or approved UAT-only reset. |
| `WritingFeedbackCompatibilityReader` / `LEGACY_BAND_V1` | `REVIEW_REQUIRED` | Legacy marker remains confined to compatibility reading/tests; requires retained-payload disposition. |
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
| `COMP-03` | `PracticeService` still writes/merges `speaking_mixed_v1`; `SpeakingResultPresenter` reads `essay_feedback_by_question`; service/presenter tests pin mixed results while excluding legacy values from trusted coverage. | `ACTIVE_MIXED_WRITE_AND_READ`; first stop/migrate the writer under a retained-payload decision, then remove only when stored payload inventory is green. |
| `COMP-04` | `SpeakingFeedbackCompatibilityReader`, `WritingFeedbackCompatibilityReader`, `LEGACY_RESULT` and progress/result callers actively parse old payloads but label them unverified/non-score-bearing. `practice_attempts.ai_feedback_json` remains nullable JSON; fresh V87 has zero attempts. | `ACTIVE_READ_ONLY_COMPATIBILITY`; per-contract `KEEP`, `MIGRATE`, `RETAIN_WITH_EXPIRY` or `REMOVE` needs real retained-payload counts. Recommended default: keep fail-closed readers. |
| `COMP-05` | `PracticeService` progress/result snapshot paths retain fallback handling for incomplete version locks. V87 permits nullable `published_version_id`, `test_version_id` and `section_version_id`; fresh attempts are zero. | `ACTIVE_HISTORICAL_READ_FALLBACK`; require retained incomplete-lock inventory and migration/reset choice before making missing locks fail closed. |
| `COMP-06` | Runtime DTO/service paths still represent `legacyFallback` for ungrouped questions. V87 keeps live `group_id` and version `group_version_id` nullable; all `9` live and `10` version technical questions are ungrouped. New candidate/PDF validators require stable groups. | `ACTIVE_HISTORICAL_READ + NONCANONICAL_TECHNICAL_FIXTURE`; canonical seed must group all questions, but broader fallback removal needs retained graph evidence. |
| `COMP-07` | Seven `PracticeRoutes.LEGACY_*` constants are mapped by `PracticeController` to redirects; functional route tests assert redirect targets. No template/static hit was found as a canonical producer. | `ACTIVE_REDIRECT_API`; caller/bookmark/support-window decision required. Recommended default: time-bound redirects, not silent removal. |
| `COMP-08` | `QuestionTypeResolver` accepts `MCQ`, `MCQ_SINGLE`, `TFNG`, `GAP_FILL` and other aliases; result/codec tests pin normalization. New authoring validators emit canonical types. Fresh live/version rows contain no alias. | `ACTIVE_READ/IMPORT_ADAPTER; CANONICAL_NEW_WRITE`; stored candidate/import and retained-row inventory must define the alias expiry. |
| `COMP-09` | Current `PracticeAssessmentExcelService` deterministically rejects legacy v1 and advanced v2 workbook entry with `*_RETIRED`; Quick Excel is the only interactive writer. `LEGACY_EXCEL_V1/practice-excel-v1` remains an enum/JSON-schema identity for historical candidate envelopes. | `WRITER_RETIRED; STORED-ENVELOPE_IDENTITY_RETAINED`; inventory stored candidates before removing enum/schema identity. Current contract wording was corrected and statically locked in this slice. |
| `COMP-12` | Tests intentionally pin non-ESSAY Writing, mixed/legacy feedback, ungrouped graphs, missing locks, redirects and aliases. Each corresponds to an active compatibility branch above rather than obsolete test-only code. | `MIGRATION/COMPATIBILITY_FIXTURES`; delete only together with the matching production branch after its decision/evidence closes. |
| `COMP-14` | Current architecture contract incorrectly described legacy Excel v1 as a current bounded reader although runtime rejects it. Other hits for old result/cache names are predominantly dated phase/audit history and must not be bulk-edited. | `CURRENT_DOC_CORRECTED`; preserve archival records with dates. Continue per-document current-vs-history review, never global search/replace. |
| `COMP-21` | `PracticeSpeakingMediaRepository.findAuthorizedTranscriptionCandidates` requires `g.id=q.groupId` while also binding owner, attempt, section, set, question, skill and media status. Ungrouped fresh technical questions therefore cannot transcribe. | `AUTHORIZATION-SENSITIVE_COMPATIBILITY_DECISION`; do not weaken the join. Group/reset/migrate retained rows or prove none require transcription. Recommended default: canonical grouping. |

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

`AUDIO_DIRECT_FULL_RESERVED` remains a disabled extension seam. Pre-15 must
close exactly one branch:

- **A — disabled with proof:** preserve transcript-only scoring, prove no scorer
  request can contain audio, keep acoustic readiness blocked, and expose no
  acoustic/holistic claim.
- **B — separately implemented and accepted:** only after explicit product,
  privacy/consent/withdrawal, reviewer authorization, provider
  non-training/retention/region, deletion-SLA, and Korean acoustic-calibration
  approval. This must be a separately named evaluator whose captured request
  proves authorized audio reached the scorer.

Until that decision is approved, the implementation remains fail-closed on
branch A behavior. Configuration alone cannot enable branch B.

### 3.1 Branch-A automated proof slice (`2026-08-03`)

Status: `IMPLEMENTED_AND_FOCUSED_TESTED`; product selection of branch A remains
an explicit release decision.

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

This slice proves the current branch-A mechanism but does not manufacture the
missing product/privacy/SME decision and does not claim acoustic readiness.

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
- Speaking is pinned to `TRANSCRIPT_ONLY`, no holistic score. The manifest
  cannot enable `AUDIO_DIRECT_FULL_RESERVED` or branch B.
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

- Select and approve Speaking branch A or B.
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
| Speaking | Branch A transcript-only disabled-full-score, or separately approved branch B. | Branch B without consent/provider/SME evidence creates privacy and invalid-measurement risk. | Branch A; `AUDIO_DIRECT_FULL_RESERVED` remains fail-closed. |
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

Current verdict remains `NO_GO`: owner decisions are now recorded, but the
approved SME artifacts still need concrete manifest references and the
remaining development-only compatibility branches require tested retirement.
