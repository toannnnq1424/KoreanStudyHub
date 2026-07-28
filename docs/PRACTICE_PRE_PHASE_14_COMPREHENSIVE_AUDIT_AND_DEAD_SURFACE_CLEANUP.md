# Practice Pre-14 Comprehensive Audit And Dead-Surface Cleanup

Last updated: 2026-07-28

Roadmap-order amendment (`2026-07-27`): this program and the Pre-14 gate stay
exactly where they are. Their immediate handoff is now Pre-15 -> Phase 15
Manual UAT/release; Phase 14 14A-14F is deferred until after that verdict.

> Status: `PLANNED_MANDATORY_BEFORE_PRE_PHASE_14_GATE`
>
> This is a future execution contract. Phase 13C3 is
> `CLOSED_VERIFIED_MERGED` at main merge SHA `65328e9`; Phase 13G is
> `COMPLETE_FOCUSED_GATE_GREEN` with `82/82`, fresh V56 proof
> `56/56/0/1/7` and cleanup absence `0`. Phase 13H requires a separate task.
> This program remains closed until all Phase 13 work and the post-Phase-13
> product/package reconciliation close.

## 1. Position in the roadmap

The mandatory order is:

1. finish Phase 13C3, run its one consolidated stabilization/validation unit,
   create multiple coherent commits and push the complete 13C3 series once;
2. finish Phase 13G, run its one consolidated stabilization/validation unit,
   create multiple coherent commits when warranted and push the complete 13G
   series once;
3. finish Phase 13H, run its one consolidated stabilization/validation unit,
   create multiple coherent commits when warranted and push the complete 13H
   series once;
4. complete end-of-Phase-13 browser/device closure, then execute the
   audit-first Practice/non-Practice AI-storage product/package reconciliation
   defined in
   `docs/PRACTICE_POST_PHASE_13_PRODUCT_INTEGRATION_AND_PACKAGE_RECONCILIATION.md`;
   keep both current organizations separate unless an explicit
   compatibility-first slice is approved, then validate/commit/push it;
5. execute this comprehensive `/practice` audit and its approved grouped
   cleanup slices with multiple independent subagents;
6. validate the complete audit/cleanup program once, create a coherent
   multi-commit series and push it once;
7. run `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` as a GO/NO-GO checkpoint;
8. execute `PRE_PHASE_15_RELEASE_CLOSURE_GATE`, then Phase 15 Manual UAT and
   the initial release verdict; and
9. open deferred Phase 14 14A-14F only after that verdict.

No validated phase may remain uncommitted while a later phase begins. A phase
may use several reviewable commits, but those commits are pushed together after
the phase validation; there is no test or push after every small patch.

## 2. What this program is, and what it is not

Phase 13H remains the Phase 13 stabilization owner for the agreed browser/device
journeys, responsive/accessibility/performance checks, reproducible JDK 17
toolchain, dependency-security baseline and already-routed operational debts.

This program is a separate whole-product production-shape audit after 13H and
after the product/package reconciliation phase. It
reconciles all `/practice` documentation, scoring/explanation contracts,
runtime routes, source dependencies, persistence objects, migrations and seed
assumptions. It then removes only dead or duplicate surfaces proven safe to
remove.

`PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` is not another implementation phase.
It consumes accepted 13C3/13G/13H and audit/cleanup evidence and returns GO or
NO-GO before Pre-15/Manual UAT and before the later Phase 14. It must not become
the first place where broad dead code, ambiguous scoring or unused tables are
discovered.

## 3. Mandatory read-first authority set

Before freezing the inventory, the coordinator and audit subagents must read:

- every repository Markdown file whose path or content concerns `/practice`,
  Practice phases/gates, scoring, rubric, explanation, result/result-detail,
  question types, provider contracts, media, migration/schema, seed,
  compatibility, retention or Manual UAT;
- `CODEX_PRACTICE_WORKFLOW.md`;
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`;
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`;
- `docs/architecture/practice/` and the current Practice architecture manifest
  and diagrams;
- all `/practice` controllers, services, repositories, entities, DTOs,
  provider clients, normalizers, prompts/rules, templates, JavaScript, CSS,
  migrations and tests that can establish a live consumer or compatibility
  obligation.

The repository Markdown search must be literal and recorded. Reading only the
four roadmap documents is insufficient. Superseded statements must be marked
as history rather than silently treated as current authority.

## 4. Parallel audit lanes

Use multiple independent subagents in bounded waves. Each audit is read-only
until the coordinator freezes one shared inventory and resolves overlapping
ownership.

1. **Authority and debt reconciliation**
   - read the complete Markdown authority set;
   - produce one current/deferred/superseded/contradictory decision ledger;
   - verify every current action and future owner.
2. **Reading/Listening assessment and explanation**
   - verify construct-native scoring and typed explanation for
     `SINGLE_CHOICE`, `FILL_BLANK` and `TRUE_FALSE_NOT_GIVEN`;
   - verify answer evidence, passage/transcript authority, Vietnamese learner
     copy and question-type-specific presentation;
   - reject a single exclusion-only JSON/layout forced onto every construct.
3. **Writing/Speaking Korean assessment**
   - verify stable score criteria, diagnostic parent mapping, evidence scope,
     provider schema, prompt, normalizer, cache/policy identity and learner UI;
   - verify transcript-only Speaking never claims fluency, pronunciation or
     other acoustic measurement;
   - verify authorized learner audio is required for any acoustic score.
4. **Code, route and presentation graph**
   - map controller routes, redirects, links, forms, fetch/WebSocket calls,
     template returns/fragments, JavaScript/CSS imports and external contracts;
   - identify dead routes, duplicate controllers/services/classes/methods,
     superseded templates/fragments/scripts and unreachable fallbacks.
5. **Persistence, migration and retained-data graph**
   - map entities, repositories, JPQL/native queries, foreign keys, indexes,
     views/triggers/jobs, migrations, seed/import/export and immutable-history
     readers;
   - identify unused tables/columns/indexes and duplicate persistence owners;
   - classify retained/deployed/shared/canonical/upgrade obligations before any
     destructive proposal.
6. **Authorization, privacy, provider and lifecycle**
   - verify lecturer/learner ownership, immutable version isolation, private
     asset access, STT/TTS/evaluation separation, idempotency, retry, retention,
     deletion and provider observability;
   - identify routes or jobs that bypass the canonical lifecycle.

At least two fresh independent read-only audits must review the same frozen
candidate inventory before implementation. A subagent may not approve its own
edits.

## 5. Korean assessment completeness boundary

KSH uses PREP only as UI/IA research. PREP branding, IELTS bands, IELTS
criterion names and English-specific taxonomy are not assessment authority.
Human-facing explanations and chips are Vietnamese/Korean; stable machine IDs
may remain language-neutral.

The audit must check more than particles, endings and honorifics. Writing and
transcript-grounded Speaking diagnostics must have an explicit, task-applicable
registry that can cover, where observable:

- morphology and particles;
- sentence endings, speech level, register and honorific agreement;
- tense, aspect, modality and negation;
- predicate valency and `호응`;
- connectives and discourse relations;
- adnominal, relative and embedded clauses;
- quotation and nominalization;
- passive and causative constructions;
- word order, ellipsis and reference;
- spelling, spacing and punctuation;
- vocabulary sense in context, collocation, Sino-Korean usage, precision,
  naturalness and repetition;
- task achievement, development, organization/coherence and task-specific
  constraints.

These are diagnostic dimensions, not automatic extra score rows. Every finding
requires an allowed parent criterion, evidence authority, task applicability,
impact, confidence and observability. Rule-engine hits are narrow advisory
signals and may not manufacture a provider finding or score.

Speaking content, grammar, vocabulary/expression and transcript-grounded
coherence may use an authoritative learner transcript. Fluency, pronunciation,
prosody, pace, pause, hesitation and other acoustic constructs are scoreable
only when an approved evaluator actually consumes authorized learner audio.
Otherwise they remain null `NOT_SCORABLE`, never zero or simulated from text.

## 6. Removal proof standard

Search absence alone is not deletion proof. Every candidate receives a literal
path/object ledger, known consumers, compatibility/data obligations, decision,
owner and acceptance evidence.

### 6.1 Java class or method

Prove no direct, reflective, Spring bean, annotation, serialization, template,
test, script, migration or provider consumer. For duplicates, identify one
canonical owner and prove behavior, identity, authorization and transaction
parity before consolidation.

### 6.2 Route

Prove no server link, form action, browser fetch, WebSocket/STOMP destination,
redirect, bookmarked/contractual client, test, report target or retained
compatibility consumer. A replacement route and bounded compatibility decision
must be explicit.

### 6.3 Template, JavaScript or CSS

Prove no controller return, fragment include, dynamic selector/import,
manifest/bundle reference, browser event contract or test fixture. Visual
similarity is not duplication proof.

### 6.4 Database object

Prove no JPA mapping, JPQL/native query, foreign key, view, trigger, scheduled
job, migration/backfill, seed/import/export, immutable history, retained data or
upgrade obligation. Applied migrations are not rewritten casually. Use a
reviewed forward migration, or the separately guarded disposable
Practice-rebaseline path only after its no-obligation proof.

### 6.5 Compatibility disposition

Every candidate is assigned exactly one current disposition:

- `KEEP`;
- `DUAL_READ`;
- `MIGRATE`;
- `REGENERATE`;
- `DELETE_UAT_ONLY`.

Anything without sufficient proof remains `KEEP` with a named evidence gap. Do
not delete code or schema merely to reduce counts.

## 7. Execution slices

- `PRE14-AUDIT-00` — read all authority Markdown, freeze the current action and
  produce the complete file/route/object inventory.
- `PRE14-AUDIT-01` — R/L/W/S academic scoring and explanation audit.
- `PRE14-AUDIT-02` — code/route/template/script/dependency graph and duplicate
  ownership audit.
- `PRE14-AUDIT-03` — persistence/table/column/index/migration/seed/retained-data
  audit.
- `PRE14-AUDIT-04` — coordinator-approved grouped cleanup implementation;
  separate tasks for non-overlapping ownership boundaries.
- `PRE14-AUDIT-05` — whole-diff reconciliation, two independent final static
  audits and one consolidated validation handoff.

Each slice is an implementation unit and should use its own Codex task. The
whole audit/cleanup program is one validation unit. Do not compile/test/build,
start the app, mutate a database, commit or push after each small slice.

## 8. Required deliverables and exit criteria

Exit requires:

- a literal Markdown authority and contradiction ledger;
- before/after route, class/service, template/script and Practice-table/object
  inventories;
- a live-consumer and removal/defer proof for every candidate;
- an academic assessment matrix for all four skills and every supported
  question/task type;
- no English-only human-facing prompt instruction left without an explicit
  machine-contract reason;
- no transcript-only acoustic scoring claim;
- no unowned duplicate or dead surface among the audited candidates;
- reviewed forward migrations and retained-data decisions for every schema
  change;
- focused regression coverage for each removed/consolidated boundary;
- one final `READY_FOR_PHASE_VALIDATION` declaration;
- one consolidated validation cycle under the phase-scoped test policy;
- multiple coherent commits when the breadth warrants it, followed by one push;
- two fresh independent final audit verdicts on the exact final snapshot;
- a GO/NO-GO handoff to
  `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE`.

If any reportable scoring identity, evidence authority, authorization boundary,
retained-data obligation or deletion proof is unresolved, the result is
`NO_GO_PRE_PHASE_14_GATE`.
