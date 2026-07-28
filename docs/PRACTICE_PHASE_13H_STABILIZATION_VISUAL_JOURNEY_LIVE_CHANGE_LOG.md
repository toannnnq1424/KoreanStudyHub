# Practice Phase 13H Stabilization, Visual And Journey Live Change Log

Opened: `2026-07-28`

Status: `CONSOLIDATED_VALIDATION_IN_PROGRESS`

Branch: `codex/practice-phase13h-stabilization-gate`

Baseline: `2549438c1a327b6932dc78d5284d7feaf5daf628`

Validation unit: `PHASE_13H_STABILIZATION_VISUAL_JOURNEY_GATE`

Implementation state: `READY_FOR_PHASE_VALIDATION`

Consolidated validation: `IN_PROGRESS_STEP_6_SECURITY_AND_DEPENDENCY_GATE`

Current required action:
`GENERATE_RESOLVED_TREE_SBOM_AND_CURRENT_ADVISORY_REACHABILITY_EVIDENCE`

## 1. Entry And Publication Reconciliation

Before any source edit, branch, HEAD, local `main`, `origin/main` and ancestry
were verified at exact merge SHA
`2549438c1a327b6932dc78d5284d7feaf5daf628`. The branch was created cleanly
from that tip. `stash@{0}` remains
`pre14-sidecars-before-main-integration-20260727` and has not been applied or
popped.

Phase 13G is `CLOSED_VERIFIED_MERGED`:

- exact head `68f3801214a741688499f9091f6821a03d5f8e0b`;
- PR #27 merge commit `4f09dd9faa22d3aec5d11dd40c7a82144664ea4a`;
- PR #28/main merge commit
  `2549438c1a327b6932dc78d5284d7feaf5daf628`;
- byte-identical trees across those three commits; and
- preserved Phase 13G commits `74a3026`, `85c61ab`, `81d78e8`, `68f3801`.

Its evidence remains: corrected 56-path snapshot, three fresh
`ACCEPT_STATIC`, JDK 17, `82/82`, V56 proof `56/56/0/1/7`, cleanup absence
`0` and PREP/KSH static image audit `52/52`. There is no Phase 13G full-suite,
browser/device or provider claim.

## 2. Read-First And File Safety Evidence

All twelve mandatory authority documents were read completely before code
implementation. The Practice catalog was recorded using
`rg --files docs | rg 'PRACTICE|practice'`; the architecture manifest,
result-fixture contract and all 30 Practice sequence diagrams were read as
direct Phase 13H references.

The complete `stash@{0}` patch was read path by path without applying it.
Initial ownership:

| Candidate | Initial disposition |
|---|---|
| Writing bounded HTTP transport | `13H_IMPLEMENT` candidate for `P15-PRE-10` |
| Writing mock-fallback removal and strict cache acceptance | `13H_IMPLEMENT` candidate for `P15-COMP-20` |
| Java 17/release/enforcer and dependency changes | `13H_IMPLEMENT` candidate, pending current-source and official-support audit |
| `practice.js` deletion | `13H_IMPLEMENT` only after fresh caller/replacement proof |
| construct/scoring/rebaseline or retained-data work | defer to its named post-13H/Pre-14/Pre-15 owner |

Excluded user paths remain untouched:

- `.tmp-ksh-audio-generator.html`;
- `.tmp/`;
- `SEP490_G103_KoreanHub.drawio.xml`;
- `openspec-temp/`;
- `scripts/docs/__pycache__/`.

The untracked `.java-version` was inspected byte-for-byte and is exactly three
bytes, `17\n`. Phase 13H conditionally accepts ownership of this file as a
portable repository Java-version guard. `.idea/` is entirely ignored in this
checkout; local files contain stale `ulp` and Lombok `1.18.36` compiler state
but are not portable tracked configuration and will not be staged as a fix.

## 3. Locked Scope And Classification

Phase 13H owns:

- `13H-TOOLCHAIN-01` / `P15-PRE-15`;
- `13H-SEC-01` / `P15-PRE-16`;
- `P15-PRE-10..13`;
- candidate `P15-COMP-20` and evidence-proven dead route/resource cleanup;
- functional R/L/W/S, resume/retake/re-evaluate, Result/Detail/Progress/deep
  link, partial/pending/failure/capture-error/empty journeys;
- desktop/mobile accessibility, visual, overflow/reflow, reduced-motion,
  long Vietnamese/Korean and UTF-8/icon closure;
- large-catalog and multi-set/test/skill journeys; and
- learner-payload/HTML/JSON/JavaScript/media answer-leakage closure.

Every finding receives one disposition:

- `13H_IMPLEMENT`;
- `DEFER_POST13_RECONCILIATION`;
- `DEFER_PRE14`;
- `DEFER_PRE15`;
- `REJECT_OUT_OF_SCOPE`.

Practice-specific AI/storage and project-wide/Admin AI/storage remain present,
operational and separate. No provider call, direct-audio score, scoring
construct expansion, retained-data destruction, rebaseline, premium seed or
post-Phase-13 package reconciliation is authorized inside this phase.

## 4. Validation Policy

Implementation uses read/edit/static review only. There is no per-file test,
compile, build, lint, application startup, browser, database, migration or
security scan.

Only after the complete diff is reconciled and this log records
`READY_FOR_PHASE_VALIDATION` may the single consolidated lifecycle run:

1. `git diff --check`;
2. clean Java-17 Enforcer/toolchain build/compile;
3. focused tests covering the whole diff;
4. fresh disposable Flyway/Hibernate proof;
5. one full suite;
6. resolved tree/SBOM/advisory/reachability evidence; and
7. one provider-disabled application/browser/device journey matrix with
   deterministic fixtures and zero unintended provider calls.

If that lifecycle fails, all failures are grouped into one correction pass,
then the affected lifecycle/tail is rerun once.

## 5. User-Authorized Mid-Phase IntelliJ Diagnostic

This is explicitly diagnostic evidence, not the Phase 13H validation gate.

At approximately `2026-07-28 14:46-14:53 +07`, an IntelliJ
`KshApplication` compile/start reached Flyway but failed against ignored local
datasource `ksh_phase13e_result_ui` at V55 line 21 with
`42S21/1060 duplicate claim_token`. Read-only inspection proved a
noncanonical migration collision: that schema had previously applied the old
`V45__practice_speaking_prompt_authoring_foundation.sql`, which contains the
DDL now assigned to V55, while current V45 is `lesson_templates` and had been
skipped. `validate-on-migrate=false` allowed the mismatch; the schema contains
a failed V55 row and no V56.

Per `P15-COMP-22`, that schema must not be repaired or reused. The user
explicitly authorized a fresh local database named
`ksh_phase13h_intellij_fresh`. Only ignored
`src/main/resources/application-local.properties` was pointed at it; neither
that local config nor database state belongs in Git. The old database remains
preserved.

One IntelliJ run then applied all 56 migrations, initialized Hibernate's
`EntityManagerFactory`, started Tomcat on port 8080 and logged
`KshApplication Started in 8.701s`. MySQL 9.7/Flyway-support, deprecated
integer display width/`VALUES()` and explicit `MySQLDialect` warnings were
non-blocking. This run made no provider call and is not full-suite, browser,
device, security-scan or consolidated validation evidence.

The exact warning inventory contains `35` migration warnings:

- one V1 line 8 `CREATE DATABASE IF NOT EXISTS ksh_db` warning
  (`1007`, database exists) while the application is connected to an isolated
  database; the hard-coded cross-schema create is architecturally wrong/noisy
  but nonblocking; and
- 34 MySQL `1681` warnings for deprecated integer display width, caused by
  `TINYINT(1)` declarations across V1/V4/V13/V14/V25/V34/V42/V45/V46/V50/V54/V55.

`TINYINT(1)` retains ordinary `TINYINT` storage semantics; these warnings are
compatibility debt rather than a runtime blocker. Phase 13H must not edit
already-published migrations blindly. Any correction must use an authorized
forward or guarded-new-baseline strategy consistent with the non-master DB
policy; otherwise it is deferred with an owner.

Phase 13H will assess a repository safeguard that restores
validate-on-migrate/immutable-published-migration failure behavior while
preserving the explicitly disposable test-database contract.

A later user-supplied IntelliJ stack trace is a separate compile/output
diagnostic, not a Flyway regression. Static inspection found two real
Phase-13H compile defects before the DevTools cascade:

- the six-dependency `WritingEvaluationClient` constructor forwarded two
  untyped `null` values into overloaded constructors, so Java could not choose
  between the `RestClient` and compatibility-seam signatures; the forwarding
  call now uses explicit `RestClient` and `AiQuestionImageResolver` null
  casts; and
- `PracticePdfImportApiControllerTest` instantiated `PracticeDraft` without
  importing `com.ksh.entities.PracticeDraft`; the missing import is restored.

While those errors were present, IntelliJ/DevTools reported 1,758 classpath
changes and rebuilt `target/classes` partially. Spring Boot 3.4.4/Spring
6.2.5 then parsed `SpeakingPromptAuthoringControllerAdvice.class`, whose
`assignableTypes` annotation names `SpeakingPromptAuthoringController`, while
the matching top-level class file was temporarily absent. This produced the
later `ClassNotFoundException` cascade even though the controller source was
unchanged and present. Read-only inspection after IntelliJ reparsing shows
both the controller and advice class files again, but that generated state is
not accepted as validation evidence.

The Maven model visible in IntelliJ has refreshed to Boot 3.5.16, but the old
run process PID `25566` still carried the Boot 3.4.4 classpath. That exact
process was stopped; no broad kill, database change, controller deletion or
ignored-config staging occurred. The clean Java-17 consolidated compile below
must replace all stale output before the application is run again.

## 6. Publication Override

After and only after a green consolidated gate:

1. create granular reviewable commits and push the 13H branch once;
2. open `codex/practice-phase13h-stabilization-gate` ->
   `feature/practice-reduce-scope`;
3. merge by **Create a merge commit**, never squash/rebase;
4. re-fetch/audit drift;
5. open `feature/practice-reduce-scope` -> `main`;
6. request approval from an authorized write reviewer and obey branch
   protection;
7. merge by **Create a merge commit** only when approved and drift-free; and
8. verify exact SHAs, ancestry and tree identity without deleting branches or
   rewriting history.

Any missing approval, drift or conflict ends as `NEEDS_ATTENTION`; no bypass is
allowed. No later phase starts from this task.

## 7. Read-Only Audit And Scope Lock

The three required independent read-only audits were started in parallel
after the main-agent read-first gate. All three audit workers were stopped by
the Codex service usage quota before returning findings. They made no file,
database, browser, provider or Git change. To avoid silently dropping the
gate, the main agent repeated each audit directly and de-duplicated the
following implementation matrix before changing runtime source.

| Finding | Disposition | Phase 13H action |
|---|---|---|
| Repository has only `<java.version>17</java.version>` and permits Maven to run on newer JDKs | `13H_IMPLEMENT` | Accept `.java-version`, add compiler release and Maven Enforcer range `[17,18)`, and document portable IntelliJ Maven/importer/runner/run-configuration alignment |
| Ignored local `.idea/compiler.xml` pins Lombok `1.18.36` and stale module `ulp` | `13H_IMPLEMENT` for audit/evidence; `REJECT_OUT_OF_SCOPE` for staging ignored noise | The coherent Boot BOM supplies current Lombok; no ignored `.idea` file is staged and no immutable migration comment is rewritten |
| Boot `3.4.4` manages vulnerable/outdated dependency boundaries, including Commons Lang `3.17.0` | `13H_IMPLEMENT` | Move to the final supported Boot 3.5 line and add only official-advisory-supported post-BOM security/compatibility bridges; generate a resolved tree, CycloneDX SBOM and OWASP advisory report |
| Flyway runtime disables checksum validation and permits `clean` | `13H_IMPLEMENT` | Enable validate-on-migrate and disable clean by default; disposable tests must opt in explicitly where needed |
| V1 hard-coded database creation and published `TINYINT(1)` warnings | `DEFER_PRE14` | Preserve V1-V56 bytes; remove this debt only in the guarded new baseline/migration policy, never by mutating published history |
| Writing HTTP transport is unbounded; unreachable mock fallback and permissive cache acceptance remain | `13H_IMPLEMENT` | Bounded transport, provider fail-closed semantics and task-native internally consistent provider-cache contract |
| Practice manage dashboard performs per-set/per-collaborator user and grant lookups | `13H_IMPLEMENT` | Bulk grant and user lookup with fixed query-count and authorization-preservation tests |
| Speaking media cleanup performs delete I/O after an unowned snapshot, so concurrent workers can duplicate work | `13H_IMPLEMENT` | Durable claim token/lease, expired-claim recovery and token-fenced completion/retry/terminal transitions |
| PDF AI generation has no durable double-submit boundary and crop/payload resource limits are unbounded | `13H_IMPLEMENT` | Session-scoped generation claim/lease/result reuse, bounded page/region/text/image/pixel budgets and bounded provider transport |
| `static/js/practice.js` has no production caller and duplicates replaced player/editor/result implementations | `13H_IMPLEMENT` | Delete it and update only the two obsolete static assertions after final caller/replacement proof |
| Learner player delivery carries answer-bearing internal rows before a dedicated redaction projection | `13H_IMPLEMENT` proof | Preserve the projection, add explicit serialized HTML/JSON/JS/media/context leakage tests, and forbid authoring assets on learner routes |
| R/L/W/S, resume/retake/re-evaluate, result/detail/progress, partial/failure/empty and large-catalog UI surfaces already exist | `13H_IMPLEMENT` proof/fixes | Exercise deterministic full journeys once after `READY`; fix only evidenced defects in the single grouped correction pass |
| Practice-specific and project-wide/Admin AI/storage packages are separate | `DEFER_POST13_RECONCILIATION` | Keep both operational and separate; do not redirect or commonize consumers |
| Writing construct expansion/local 1-9 retirement and Speaking simulated-score retirement | `DEFER_PRE14` | No scoring-construct change in 13H |
| Real provider/load, premium seed and SME calibration | `DEFER_PRE15` | Default gates keep every provider disabled |

Primary-source decisions recorded on `2026-07-28`:

- Spring's support policy identifies the last minor of a major line as the
  long-lived line; Spring Boot 3.5.16 requires Java 17 and Spring Framework
  6.2.19 or newer:
  <https://spring.io/support-policy> and
  <https://docs.spring.io/spring-boot/3.5/system-requirements.html>.
- Apache POI before 5.4.0 is affected by CVE-2025-31672; 5.5.1 is the current
  supported 5.5 line and aligns Commons Codec 1.20.0:
  <https://poi.apache.org/index.html>,
  <https://poi.apache.org/versioning.html> and
  <https://poi.apache.org/changes.html>.
- Apache PDFBox 3.0.8 is the fixed boundary for CVE-2026-33929:
  <https://pdfbox.apache.org/security.html>.
- Apache Commons Lang 3.18.0 is the fixed boundary for CVE-2025-48924:
  <https://nvd.nist.gov/vuln/detail/CVE-2025-48924>.
- Logback 1.5.37 is the definitive CVE-2026-13006 fix and 1.5.38 is the
  current 1.5 maintenance release:
  <https://logback.qos.ch/news.html>.
- Apache Tomcat's official 10.x advisory inventory contains fixes through
  10.1.57:
  <https://tomcat.apache.org/security-10.html>.
- jsoup 1.22.2 and AWS SDK for Java v2 are current upstream-supported lines:
  <https://jsoup.org/news/release-1.22.2> and
  <https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html>.
- CycloneDX Maven plugin 2.9.2 and OWASP Dependency-Check Maven 12.2.2 are
  pinned audit tools:
  <https://github.com/CycloneDX/cyclonedx-maven-plugin/releases> and
  <https://dependency-check.github.io/DependencyCheck/dependency-check-maven/check-mojo.html>.

## 8. Implemented Phase 13H Diff

Static implementation is complete and remains inside the locked matrix:

- Java/toolchain: Boot 3.5.16, Java release 17, Maven/Java Enforcer,
  repository `.java-version`, shared Java-17 run configuration and operations
  guide;
- security/dependencies: current supported POI, PDFBox, jsoup and AWS SDK,
  targeted Commons Lang/Codec, Logback and Tomcat maintenance bridges, plus a
  dated CycloneDX/OWASP audit profile;
- migration safety: normal Flyway validation enabled, clean disabled,
  byte-lock manifest/test for published V1-V56, and forward-only V57 claim
  columns/check/indexes;
- Writing: bounded provider transport, no live mock bean/fallback, fail-closed
  unavailable results and strict task-native cache acceptance with
  criterion/max/total/percentage consistency;
- manage dashboard: bulk collaboration and user resolution with fixed query
  count independent of catalog size;
- Speaking cleanup: durable PROCESSING token/lease ownership, pessimistic
  claim, expired-lease recovery, version/token-fenced outcomes and
  out-of-transaction storage deletion;
- PDF AI: normalized crop/pixel/byte/page/region/text limits, actual loaded
  byte accounting, bounded transport and a durable session generation claim
  that reuses the completed draft and blocks duplicate provider calls;
- learner safety: explicit non-null JSON projection, player redaction of
  answer key, explanation, transcript and provenance, with serialized-payload
  regression coverage; and
- dead surface: the unreferenced root `static/js/practice.js` bundle and only
  its two obsolete preservation assertions were removed after a fresh caller
  scan found no production loader.

No V1-V56 byte, ignored `.idea`/local datasource file, excluded user path,
provider setting, scoring construct, retained-data baseline or post-13 package
was changed.

## 9. Final Static Review Before Validation

The full changed surface was re-read after implementation. Static call-site
and ownership checks found:

- all `WritingEvaluationClient` compatibility constructors resolve to a
  unique target after the typed-null correction; production injection no
  longer contains `WritingMockEvaluatorService`;
- the PDF controller slice supplies every new mock and imports
  `PracticeDraft`; completed-result reuse re-authorizes the draft before
  returning it;
- PDF completion runs inside the assembler transaction through a
  `MANDATORY` token-fenced transition, while claim/release commit in short
  independent transactions;
- cleanup workers cannot both obtain a live database claim, an expired claim
  receives a new token and stale completion/retry cannot overwrite the new
  owner;
- payload budgets use `String.length()` and loaded byte arrays rather than
  trusting extraction/asset metadata; oversized pages are rejected before
  raster allocation;
- learner set/test detail uses the summary projection with no questions;
  active R/L/W delivery calls `redactPlayerGroups`; Speaking delivery exposes
  only `SpeakingPromptDelivery`; player templates/scripts never access answer
  key, explanation, transcript or provenance; and
- `/practice/materials/{id}/content` remains authenticated and authorizes
  through owner, readable draft, owned attempt or currently visible published
  version before loading bytes.

Answer/explanation references that remain are confined to lecturer authoring
or post-submit Result/Detail presentation. The root `practice.js` scan has no
production reference; active player/result assets are explicitly loaded by
their templates.

The implementation diff is therefore frozen at
`READY_FOR_PHASE_VALIDATION`. No build, focused suite, fresh-database proof,
full suite, advisory scan, application start or browser/device run is claimed
by this status. Those operations now run once as the consolidated lifecycle
defined in section 4.

## 10. User-Directed Login Slice After The Initial Freeze

After the first static freeze, the user explicitly rejected the cosmetic
lecturer/student selector shown before authentication because it did not
control authorization. The selector, its hash-routing state and its duplicate
role labels were removed. `/login` now exposes one direct authentication form,
explains that the authenticated account determines the destination and keeps
the existing KSH blue visual language. The replacement illustration is inline,
local SVG; no new remote asset, tracker, provider or role hint is introduced.

This late addition is intentional and in scope for the 13H authenticated-role
and visual-journey boundary. Its final static reconciliation confirms:

- the server-side username/password and optional Google authentication
  contracts are unchanged;
- no role value is accepted from the client, so authorization remains derived
  from the authenticated `User`;
- login failure/success toast handling and forgot-password navigation remain;
- desktop owns the two-column form/illustration layout while the single-column
  mobile layout has no horizontal overflow;
- focus-visible and reduced-motion behavior are explicit; and
- `AuthLoginIntegrationTest` asserts the direct form and absence of the former
  selector.

The already-executed `AuthLoginIntegrationTest` plus
`PracticeFunctionalUiContractTest` run (`25` tests, `0` failures, `0` errors),
the skip-test package, and the `1440x900`/`390x844` login-only browser checks
are retained strictly as **partial user-directed evidence**. They are not the
focused whole-diff suite, fresh-database proof, full suite, security gate or
mandatory 13H browser/device matrix.

The frozen validation surface at the end of the login-only correction was
`46` tracked changed paths plus `13`
intended new paths. Excluded/untracked user paths remain outside that set and
untouched. With this reconciliation the complete 13H diff again remains
`READY_FOR_PHASE_VALIDATION`.

## 11. Partial User-Directed Progress And Result UI Correction Evidence

The user then rejected the dense, monochrome Progress and Result presentation
and the generic rounded-card/chip treatment. The correction uses the PREP
references only as hierarchy and journey guidance, not as copied markup or
artwork: a flat editorial system now relies on rules, spacing, type scale and
skill/status color. The final user-approved Stitch-derived geometry uses
complete four-edge borders with `12px` compact, `16px` standard and `20px`
focal/detail radii. It does not use colored left-edge-only state accents,
excessive nested cards, large shadows, blur or gradients. Diagnostic chips
remain secondary filters and technical provenance stays inside closed native
`details` disclosures.

The correction was limited to the owned presentation surfaces:

- Progress:
  `templates/practice/progress.html`,
  `templates/practice/fragments/progress-facts.html`,
  `static/css/practice-progress.css` and
  `static/js/practice-progress.js`;
- Result:
  `templates/practice/result.html`,
  `templates/practice/result/speaking.html` and
  `static/css/practice-result.css`; and
- typed Result Detail:
  `templates/practice/result-detail-objective.html`,
  `templates/practice/result-detail-writing.html`,
  `templates/practice/result-detail-speaking.html` and
  `static/css/practice-result-detail.css`.

The first real-server Progress render exposed two Thymeleaf defects that the
static fixture did not: a block combined `th:each` and `th:replace`, and a
replacement fragment evaluated `point.scoreFact` after `point` had left
scope. The iteration and replacement are now separated and the fragment
receives the score fact explicitly. A provider-disabled server request to
`/practice/progress?tab=test-practice&skill=ALL&writingTask=ALL&profile=ALL`
then returned HTTP `200` with `155400` response bytes.

Partial route-level in-app browser checks covered:

- objective attempt `16615`: `/practice/attempts/16615/result` and
  `/practice/attempts/16615/result/detail`;
- Writing attempt `16616`: `/practice/attempts/16616/result` and
  `/practice/attempts/16616/result/detail`; and
- Speaking attempt `16602`: `/practice/attempts/16602/result/detail`.

At `1440x900` and `390x844`, the checked pages had no body-level horizontal
overflow. Evidence/provenance disclosures were closed initially, Writing and
Speaking detail tabs reflowed to a two-by-two mobile grid, and objective
question anchors reported a `76px` scroll margin so the fixed header does not
cover the selected question. Provider, STT, TTS and evaluation network paths
remained disabled/guarded throughout this QA process; zero unintended AI or
speech-provider calls were observed.

Captured partial UI-correction evidence:

- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-progress-flat-final-desktop.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-progress-flat-final-mobile.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-objective-flat-final-desktop.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-objective-flat-final-desktop.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-objective-flat-final-mobile.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-writing-flat-desktop.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-writing-flat-mobile.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-writing-flat-desktop.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-writing-flat-mobile.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-speaking-flat-desktop.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-speaking-flat-mobile.png`
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-speaking-flat-mobile-feedback.png`

The final accepted radius/border correction is additionally captured in:

- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-final.png`;
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-result-detail-final.png`; and
- `/Users/toanlamsaoduocc/.codex/visualizations/2026/07/28/019fa7af-c879-7072-a323-549cf40b8990/ksh-progress-final.png`.

The UI-focused automated evidence was `80/80` across
`PracticeResultDetailContractTest`, `PracticeResultPresenterTest`,
`PracticeResultWordingTest` and `PracticeFunctionalUiContractTest`; after the
last border/radius correction, `PracticeFunctionalUiContractTest` was rerun
and remained `19/19`. The accompanying skip-test package was a packaging
check, not a test-suite result.

The section-10 login `25`-test run and login-only desktop/mobile browser
checks, together with every Progress/Result/Result Detail render, device,
static-contract and focused presenter check recorded in this section, remain
strictly **partial user-directed evidence**. None of them is the consolidated
Phase 13H focused whole-diff suite, fresh-database proof, full suite,
dependency/advisory gate or mandatory provider-disabled browser/device matrix.
They do not advance or close any consolidated lifecycle stage.

## 12. Consolidated Phase 13H Lifecycle Execution

### 12.1 Step 1 — Diff reconciliation and whitespace/static gate

The latest user-directed Login, Progress, Result and typed Result Detail
corrections were reconciled into the complete frozen surface before any
consolidated build or test was started.

Current surface:

- `65` tracked changed paths: `64` modified and one intentional deletion;
- `13` intended new Phase 13H paths;
- `78` phase-owned paths in total; and
- `1,028` other nonignored untracked user-owned paths excluded from the phase
  (`openspec-temp/`, `.tmp/`, `scripts/docs/__pycache__/`,
  `.tmp-ksh-audio-generator.html` and
  `SEP490_G103_KoreanHub.drawio.xml`).

Ignored `.idea/` and `application-local.properties` remain local-only and
untouched. No unmerged entry, rename anomaly, conflict marker, empty intended
file or added `TODO`/`FIXME`/`WIP` placeholder was found. The sole deletion,
`src/main/resources/static/js/practice.js`, still has no production loader.

Exact Step-1 evidence:

- `git diff --check`: exit `0`;
- conflict-marker scan across the phase-owned source/docs surface: no match;
- `node --check` for `practice-progress.js` and `practice-result.js`: both
  exit `0`;
- Java-17 shared run-configuration XML parse: exit `0`; and
- the 56-entry published migration manifest verified every current V1-V56
  byte with `shasum -a 256 -c`: `56/56` `OK`.

The user review server on port `18088` was not stopped or rebuilt in place
during this reconciliation. Step 1 is `PASS`; the locked lifecycle has moved
to the clean Java-17 Enforcer/toolchain build and compile.

### 12.2 Step 2 — Clean Java-17 Enforcer/toolchain build and compile

The complete `78`-path candidate was materialized in the unique disposable
snapshot `/private/tmp/ksh-13h-consolidated.c5Vnyi`. It was assembled from the
baseline Git tree, the current tracked patch and only the `13` intended new
paths; excluded user-owned and ignored local paths were not copied. This also
kept the running port-`18088` review JAR isolated from Maven `clean`.

Command:

```text
env JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  PATH=/opt/homebrew/opt/openjdk@17/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  bash mvnw -B -ntp clean package -DskipTests
```

Result:

- Java `17.0.19` detected and normalized by Maven Enforcer;
- Java-version and Maven-version rules: `PASS`;
- `741` production sources compiled with `release 17`;
- `264` test sources compiled with `release 17`;
- Spring Boot `3.5.16` repackaged the application JAR;
- `BUILD SUCCESS` in `10.414 s`; and
- tests were intentionally skipped at this build/compile stage only.

The compiler repeated the pre-existing warning that
`LibraryStorageService.java:33` is a deprecated item without an
`@Deprecated` annotation, plus informational deprecated/unchecked-use notes.
No warning was promoted to a build failure and no runtime/provider action was
performed. Step 2 is `PASS`; the lifecycle has moved to the whole-diff
focused selector.

### 12.3 Step 3 — Smallest complete focused selector for the whole 13H diff

One selector was assembled across every functional group in the frozen
candidate: Java-17 and published-migration safeguards, login, dashboard,
Writing evaluation integrity, Speaking media/lifecycle fail-closed paths,
PDF import/AI ownership and operational claims, progress, result, result
detail, immutable player payloads and active UI contracts.

The first run inside the restricted execution sandbox completed collection
instead of being retried class by class:

- tests run: `235`;
- assertion failures: `0`;
- errors: `154`; and
- skipped: `0`.

All errors grouped into two execution-environment causes: sandbox denial of
the local MySQL connection and sandbox denial of Mockito/ByteBuddy inline
mock-maker self-attachment. Spring context failure-threshold propagation was
downstream of those two causes; it was not counted as a third product cause.
There was no product assertion failure to correct.

Per the one-correction/one-tail-rerun rule, the exact same selector was then
run once outside those sandbox restrictions, still under Java `17.0.19`,
against the uniquely named focused-test database
`ksh_phase13h_focused_20260729_0035`. That rerun also migrated its isolated
schema from V1 through V57 and started the required Spring/Hibernate test
contexts.

Final focused result:

- tests run: `235`;
- failures: `0`;
- errors: `0`;
- skipped: `0`;
- `BUILD SUCCESS` in `28.870 s`; and
- no provider/STT/TTS/evaluation call was enabled or made.

This is the whole-diff focused-suite evidence only. Its incidental test
database migration is not substituted for the next locked canonical
fresh-database proof. Step 3 is `PASS`; the lifecycle has moved to a new
disposable V1-to-V57 Flyway validation and Hibernate startup.

### 12.4 Step 4 — Canonical fresh disposable V1-to-V57 database proof

The canonical proof used a second, previously nonexistent schema:
`ksh_phase13h_gate_v1_v57_20260729_0100`. It was not the stable IntelliJ
database, the earlier focused-test database, or
`ksh_phase13e_result_ui`. The clean Step-2 JAR was started with Java
`17.0.19`, a random HTTP port, an empty provider credential and every
AI/STT/TTS/evaluation and related cleanup/generation worker explicitly
disabled.

Flyway and startup evidence:

- Flyway resolved and successfully validated `57` migrations;
- schema history did not exist before this run;
- migrations V1 through V57 were applied in order;
- Flyway reported `57` successfully applied and current version `v57`;
- Hibernate `ddl-auto=validate` initialized the default
  `EntityManagerFactory`;
- the complete application started on disposable port `53629` in
  `7.049 s`; and
- the proof process was then shut down gracefully without touching the
  user-review process.

Read-only post-startup database checks:

- Flyway history: `57` rows, `57` success, `0` failed, maximum version `57`;
- V57 ownership/lease columns: `4/4`;
- V57 lease indexes: `2/2`;
- base tables: `102`; and
- `ai_request_logs`: `0`.

The warnings are classified, not hidden: MySQL `9.7` is newer than the
Flyway release's tested maximum; published V1 contains the historical
hardcoded `CREATE DATABASE ksh_db`; older migrations use deprecated integer
display widths/`TINYINT(1)` and the deprecated `VALUES(...)` form; and
Hibernate notes that explicitly setting `MySQLDialect` is unnecessary.
These warnings did not prevent migration, validation or runtime startup.
Published V1-V56 bytes remain immutable and still match the checked-in
`56/56` checksum safeguard; remediation must be forward-only.

Only the disposable proof process was stopped. The separate review server
remained live as PID `87713` on port `18088`. Step 4 is `PASS`; the locked
lifecycle has moved to the one complete test suite.

### 12.5 Step 5 — One complete test suite and bounded correction pass

The first complete Java-17 suite ran all `2,398` tests against the separate
disposable schema `ksh_phase13h_full_20260729_0046`. It produced:

- tests run: `2,398`;
- assertion failures: `0`;
- errors: `147`; and
- skipped: `0`.

All `147` errors grouped into one test-runtime infrastructure root cause,
MySQL error `1040` (`Too many connections`). Exactly three Spring contexts
failed their real load once at `flywayInitializer`/`entityManagerFactory`:

- `PracticeIntegrationTest`: `98` errors (`1` real context failure plus
  `97` failure-threshold consequences);
- `PracticeSpeakingMediaServiceTest`: `31` (`1 + 30`); and
- `PracticeSpeakingMediaCleanupTaskServiceTest`: `18` (`1 + 17`).

The JVM had retained 15 distinct cached Spring contexts, each using Hikari's
default ten-connection pool, against MySQL `max_connections=151`. Pools
16-18 then failed. MySQL recorded peak usage `155` and
`Connection_errors_max_connections=747`. This was context-cache/pool fan-out,
not test parallelism or a product assertion failure.

The single bounded correction pass changed no product or test source and did
not raise the database limit. The same complete suite was run once more,
serially, on the fresh schema
`ksh_phase13h_full_retry_20260729_005318`, with Hikari maximum pool size `4`,
minimum idle `0`, Spring test-context cache maximum `8` and one reusable
Surefire fork. During the run the schema held only `14` observed connections.

Final complete-suite result:

- tests run: `2,398`;
- failures: `0`;
- errors: `0`;
- skipped: `0`;
- `BUILD SUCCESS` in `01:23 min`;
- Flyway history: `57/57`, maximum version `57`;
- `Connection_errors_max_connections` remained `747`, proving the corrected
  run caused no further rejected connection; and
- `ai_request_logs`: `0`.

The Sprint-8 provider-management integration tests intentionally exercised
their own test-only failure sentinels at the reserved TEST-NET endpoint
`192.0.2.1:9` and unresolvable `*.example.test` names. They carried no real
provider credential and reached no real AI provider. Runtime provider,
STT/TTS, evaluation and background-generation flags remained disabled.

The former user-review process on port `18088` was later stopped at the
user's explicit request; it is not validation evidence and is not claimed to
be live. Step 5 is `PASS`; the locked lifecycle has moved to the resolved
dependency tree, SBOM, current official-advisory and reachability gate.

### 12.6 Step 6 — Resolved dependency, SBOM and current advisory gate

The first current-feed Dependency-Check run correctly stopped the lifecycle
instead of being waived. It grouped the runtime Critical/High blockers into
two dependency roots:

- Apache HttpCore `5.3.6` / HttpCore HTTP/2 `5.3.6`, brought into the
  executable archive by the AWS SDK Apache 5 transport, matched
  CVE-2026-54399 and CVE-2026-54428 (CVSS `7.5`); and
- Log4j API `2.24.3`, used directly by POI and bridged by the logging stack,
  matched CVE-2026-34478, CVE-2026-34479, CVE-2026-34480,
  CVE-2026-34481 and CVE-2026-49844.

This was a real runtime dependency failure, not a false-positive suppression
candidate. The single allowed correction pass added only the two supported
post-BOM version boundaries `log4j2.version=2.25.5` and
`httpcore5.version=5.4.3`. No suppression rule, CVSS downgrade or package
exclusion was introduced.

The affected consolidated tail was then rerun once:

- clean Java-17 package: `BUILD SUCCESS`, 741 production and 264 test source
  files compiled, executable archive repackaged;
- smallest whole-diff selector: `235/235`, 0 failures/errors/skips,
  `29.286 s`;
- executable-archive startup on fresh schema
  `ksh_phase13h_security_startup_20260729_0149`: Flyway `57/57`, Hibernate
  `EntityManagerFactory` initialized and Tomcat started on random port
  `55799`; the disposable proof process alone was then stopped;
- complete suite on fresh schema
  `ksh_phase13h_security_full_20260729_0150`: `2,398/2,398`, 0
  failures/errors/skips, `BUILD SUCCESS` in `01:19 min`, Flyway `57/57`,
  102 base tables and `ai_request_logs=0`; and
- post-correction `git diff --check`: clean.

The test-only provider failure sentinels again used the reserved TEST-NET
address `192.0.2.1:9` and `*.example.test` names without credentials. They
are expected negative-path fixtures, not live AI calls.

The final Java-17 security profile produced the following artifacts from that
exact corrected candidate:

- resolved Maven tree:
  `target/phase13h-security/practice-phase13h-resolved-dependency-tree-2026-07-28.txt`,
  517 lines, SHA-256
  `ed28f147120fbc4d05db255d0953261fc0eb5367a8936a663e1e7c0b3edae670`;
- CycloneDX 1.6 JSON SBOM:
  `target/phase13h-security/practice-phase13h-sbom-2026-07-28.json`,
  197 components / 198 dependency-graph nodes, SHA-256
  `438a6bbf47159fd29e2f41b630c276b51c8a8d7a6470aecef4d13f8068bb6d11`;
- Dependency-Check JSON:
  `target/phase13h-security/dependency-check-report.json`, SHA-256
  `a6eede6f1b101891afeb3bc76b4d171562065e85fe65d0a40c5fcb56e8054bef`;
  and
- Dependency-Check HTML:
  `target/phase13h-security/dependency-check-report.html`, SHA-256
  `f0f93d59e75bc4ea6315bf51ef242abd48a75fab0dc2f672f1d7859f2bab6c15`.

The filename date is the audit artifact name pinned by the Phase 13H Maven
profile; execution continued on 29 July 2026. The executable Boot archive
contains 158 `BOOT-INF/lib` JARs. The runtime inspection confirms, among
other boundaries, Spring Boot `3.5.16`, Spring Framework `6.2.19`, Spring
Security `6.5.11`, Tomcat `10.1.57`, Jackson Databind `2.21.5`, Logback
`1.5.38`, Netty `4.1.136.Final`, POI `5.5.1`, Jsoup `1.22.2`, PDFBox
`3.0.8`, AWS SDK `2.46.8`, Flyway `11.7.2`, MySQL Connector/J `9.7.0` and
Nimbus JOSE JWT `9.37.4`.

The executable archive specifically contains Log4j API `2.25.5`, HttpCore
`5.4.3` and HttpCore HTTP/2 `5.4.3`; it contains neither Log4j API `2.24.3`
nor HttpCore `5.3.6`. The resolved tree records each BOM override explicitly.

OWASP Dependency-Check `12.2.2` then completed offline against the populated
current local NVD cache, with `failBuildOnCVSS=7`, `failOnError=true`, no
suppression file and no NVD API key. It analyzed 201 dependency records,
wrote both reports and returned `BUILD SUCCESS`: 0 vulnerable dependencies,
0 reported vulnerabilities and 0 Critical/High findings. The previously
blocking CVEs are absent from the corrected report.

The parallel official/primary-advisory review currently finds zero unresolved
runtime Critical/High advisories in the reviewed resolved versions. In
particular, the exact fixed boundaries include Spring Framework `6.2.19`,
Spring Security `6.5.11`, Spring Data `3.5.13`, Tomcat `10.1.57`, Jackson
Databind `2.21.5`, Netty `4.1.136.Final`, POI `5.5.1`, PDFBox `3.0.8`,
Jsoup `1.22.2`, Nimbus JOSE JWT `9.37.4` and AWS SDK `2.46.8`.
SAML2, CloudFront, Netty XML/HAProxy/SSL-OCSP/HTTP3, PDFBox examples and
direct Nimbus password/JWE paths are absent from this runtime/source tree.

This is not a claim that the dependency posture is vulnerability-free.
Oracle's 21 July 2026 Connector/J advisory places the resolved MySQL
Connector/J `9.7.0` inside the affected range for CVE-2026-61082, CVSS 6.5
Medium. That qualified finding does not cross the locked Critical/High
failure threshold, but it must be upgraded as soon as Oracle publishes a
fixed Connector/J release. POI and PDFBox are also genuinely reachable on
untrusted import paths; their fixed versions do not remove the need for
bounded input/page/region/text/image/pixel and runtime resource controls.

Runtime Critical/High decision: **GO**. Step 6 is `PASS`. The qualified
Connector/J Medium and the immutable V1/MySQL 9.7 warning debt remain recorded
without being mislabeled as runtime failures. The lifecycle may now enter the
mandatory provider-disabled browser/device matrix; this security result alone
does not complete the Phase 13H browser gate.

### 12.7 Step 7 — Provider-disabled in-app browser and device matrix

The mandatory browser tail ran only after Steps 1-6 were green. Runtime AI,
STT, TTS, evaluation and background-generation flags remained disabled. The
review applications on ports `18091`, `18092` and `18093` were deliberately
left running so the user can continue reviewing the screens; they are not
background provider workers. Port `18093` serves the final corrected candidate
used for the closing reruns.

The structured browser record is
`target/phase13h-browser-evidence/phase13h-browser-qa.json` in the frozen
candidate, with `35` route/state assertions and no remaining `FAIL` result.
The same directory contains `19` named PNG captures across `1440x900` desktop
and `390x844` mobile viewports. The matrix covered:

- authenticated `STUDENT`, `LECTURER` and `ADMIN` boundaries: students and
  lecturers were denied outside their authorized surfaces, lecturers reached
  Practice management, and administrators reached the admin dashboard;
- the large student catalog and Reading-filtered catalog, multi-set/test/skill
  navigation, overview/facts progress tabs and deep links;
- Reading, Listening, Writing and Speaking result and typed result-detail
  routes, including single-choice, fill-blank and true/false/not-given
  presentation contracts;
- Writing task/result states, Speaking transcript-only and fail-closed states,
  resume persistence, retake/re-evaluate entry points, pending/failure/empty
  states and invalid-media preflight;
- desktop/mobile reflow, horizontal overflow, keyboard tab navigation,
  visible focus behavior, reduced-motion contract, long Vietnamese/Korean
  content, UTF-8 rendering and image integrity; and
- pre-submit payload/HTML/JSON/JavaScript/media leakage checks for answer,
  storage and privileged AI fields.

The resume proof changed an answer on attempt `16613`, reloaded the route and
confirmed option `2` remained selected. The result-detail deep link for
attempt `16614` resolved `#objective-question-9` to the intended active target.
Writing feedback tabs exposed four keyboard-operable tabs; `ArrowRight` moved
both focus and the selected panel from Overview to Strengths. Browser console
collection reported zero error or warning entries.

Two real responsive defects were grouped and corrected once:

1. the Speaking score-criterion grid allowed the Vietnamese title
   `Độ lưu loát` to collapse into a seven-pixel vertical glyph stack; and
2. the objective fill-blank answer-table caption could collapse to a
   38-pixel vertical column on mobile.

The first correction gives the criterion title, score and note explicit
bounded grid tracks plus safe word wrapping. The second makes objective table
captions block-level, full-width boxes at the mobile breakpoint. Desktop and
mobile reruns on port `18093` measured the Speaking title at a normal
`207.78 x 21` desktop box and a readable two-line `60.45 x 42` mobile box; the
corrected mobile caption measured `328 x 29`. All rerun routes had
`scrollWidth == clientWidth`. The static regression contract
`PracticeFunctionalUiContractTest` then passed `19/19`, and the corrected
candidate package rebuilt successfully.

The capture-error journey is qualified rather than overstated: the available
Speaking fixtures fail closed at invalid prompt media before a recorder can be
opened, while the client contract contains explicit empty-recording,
unsupported-device, upload-failure and retry states. The empty submitted
Speaking attempt `16602` displayed no fabricated score or transcript. This is
recorded as a fail-closed precondition proof, not as a successful microphone
upload.

The pre-submit attempt rendered none of `correctAnswer`, `answerKey`,
`storageKey`, `sha256`, `systemPrompt`, `requestJsonPreview` or equivalent
privileged answer/provider fields, and loaded no external provider or media
URL. After the browser run, both provider-observability tables remained empty:
`ai_request_logs=0` and `practice_ai_request_audits=0`. No unintended AI,
STT/TTS or evaluation provider call occurred.

The earlier 25-test login run and login-only desktop/mobile review remain
useful user-directed partial evidence, but they are not represented as the
consolidated Phase 13H validation or browser gate.

Step 7 decision: **PASS**. Browser QA is complete with the two grouped defects
corrected and rerun. The lifecycle has moved to final evidence reconciliation,
complete-diff review, granular commits, one branch push and publication only
to `feature/practice-reduce-scope`.

### 12.8 Step 8 — Complete-diff audit correction pass

The final independent UI audit found no remaining concrete blocker: both
changed JavaScript files parse cleanly, no live template references the deleted
legacy `static/js/practice.js`, the UI contract tests are green and
`git diff --check` remains clean.

The independent backend audit found one grouped PDF-AI concurrency and
authorization defect set before publication:

- the controller loaded a session before acquiring the durable generation
  claim, so an edit committed between those operations could be omitted from
  the claimed payload;
- snapshot restoration changed generation inputs without invalidating an
  existing claim;
- linked-draft `EDIT` or standalone `CREATE` authorization was rechecked only
  while assembling the provider result, after provider capacity had already
  been consumed; and
- the new generation claim token had a public entity getter and could therefore
  be serialized by entity-returning session APIs.

One correction pass closed the complete group. `ClaimResult` now carries the
exact session state loaded under the pessimistic claim lock, and the controller
builds the payload only from that fenced state. Snapshot restoration now locks
the session and calls `markContentChanged`, clearing any live token before
replacing annotations. Granular target authorization runs immediately after
claim and before payload/provider work, while the assembler retains its
transactional authorization check as defense in depth. A denied pre-provider
check releases the claim and rethrows the access denial. The claim-token getter
is now excluded from JSON serialization.

The affected consolidated tail then ran once on Java 17:
`PracticePdfImportApiControllerTest`,
`PracticePdfAiGenerationServiceTest` and
`PracticePublishedMigrationChecksumTest`. It passed `15/15` tests with zero
failure, error or skip. The regression set proves use of the claim-fenced
session, zero provider interaction after permission revocation, snapshot
invalidation of an old claim, JSON token redaction and unchanged V1-V56
migration checksums.

Final audit-correction decision: **PASS**. No provider call was enabled or
performed by this correction run. The candidate may proceed to granular
commits and the single branch push.
