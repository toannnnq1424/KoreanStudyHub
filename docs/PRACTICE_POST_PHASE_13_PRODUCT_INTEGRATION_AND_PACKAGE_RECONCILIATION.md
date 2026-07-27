# Practice Post-Phase-13 Product Integration And Package Reconciliation

Recorded: `2026-07-27`

Status: `PLANNED_MANDATORY_AFTER_PHASE_13_CLOSURE`

## 1. Decision and current boundary

The repository currently has two related but separate implementation families:

- `/practice` owns its current AI clients, prompts, cache/identity rules,
  metrics, audio processing, private material storage and lifecycle contracts;
- other product areas also contain AI-call and storage facilities with their
  own configuration, authorization, lifecycle and operational assumptions.

Neither family is declared the canonical replacement for the other. Phase
13C3, 13G and 13H must preserve the current `/practice` boundaries. They must
not move Practice AI/storage classes into `common`, Admin configuration or a
project-wide provider/storage package, and must not make other product flows
depend on Practice internals.

This is a temporary coexistence decision for the current integration, not a
permanent ban on future reconciliation. Both the project-wide AI/storage/Admin
stack and the Practice-specific AI/storage stack must remain present,
operational and separate. Integrating `main` must not treat either family as a
branch deletion, redirect one family's consumers into the other, or silently
make their configuration authorities equivalent. A later approved slice may
touch the boundary only after it proves contract parity, data/config migration
and rollback safety.

This future phase is audit-first. It may propose a shared seam, adapter or
package reconciliation, but implementation starts only after the inventory is
frozen and each movement is explicitly approved. A bulk package move or config
unification is forbidden.

## 2. Roadmap position

```text
Phase 13C3 validation -> granular commits -> one push -> post-push audits
  -> Phase 13G validation/commits/push
  -> Phase 13H validation/commits/push
  -> end-of-Phase-13 browser/device closure
  -> POST_PHASE_13_PRACTICE_PRODUCT_INTEGRATION_AND_PACKAGE_RECONCILIATION
       -> multi-subagent read-only discovery
       -> one frozen ownership/dependency inventory
       -> explicitly approved compatibility-first slices, if any
       -> one consolidated validation
       -> granular commits -> one push
  -> comprehensive /practice dead/duplicate-surface cleanup
  -> PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE
  -> PRE_PHASE_15_RELEASE_CLOSURE_GATE
  -> Phase 15 Manual UAT/release
  -> deferred Phase 14 Report an Error
```

This phase cannot start merely because 13C3 is green. All remaining Phase 13
work must be validated, committed and pushed first.

Each completed phase PR is integrated before the following phase starts. Once
a PR is validated, independently audited and merged, refresh from the full
`origin/main` tip and create the next phase branch from that exact tip. The
coordinator must also fork a new Codex task with a written handoff containing
the active contracts, deferred debts, validation evidence and mandatory
read-first Markdown list; it must not rely on conversational memory alone.

## 3. Mandatory read-first authority

The coordinator and audit agents must read before proposing movement:

- every Markdown file whose path or content concerns Practice, AI, provider,
  storage, upload, media, retention, cache, configuration, authorization,
  migration, seed, package ownership or Manual UAT;
- `CODEX_PRACTICE_WORKFLOW.md` and
  `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`;
- all Practice architecture manifests, diagrams and phase live logs;
- every `/practice` AI/storage caller and consumer, including configuration,
  controllers, services, repositories, jobs, metrics, cache keys, templates
  and tests;
- the corresponding non-Practice AI/storage implementations and their live
  consumers, without assuming similarly named classes have the same contract.

The literal repository search and the authority/supersession decisions must be
recorded. Reading only selected roadmap files is insufficient.

## 4. Parallel read-only audit lanes

Use multiple independent subagents on one frozen commit.

1. **AI topology and identity**
   - map provider clients, request/response schemas, prompts, normalizers,
     retries, timeouts, cache/reuse identities, metrics and cost controls;
   - distinguish learner-answer STT, lecturer-prompt STT/TTS, evaluation and
     explanation boundaries;
   - prove which behavior is domain-specific before suggesting a shared seam.
2. **Storage and lifecycle topology**
   - map physical stores, logical assets, ownership, public/private delivery,
     immutable references, retention, deletion, orphan reconciliation and
     retry jobs;
   - preserve storage keys, asset IDs and historical media until an explicit
     compatibility/migration proof exists.
3. **Package and dependency ownership**
   - build caller/callee graphs for Practice and non-Practice facilities;
   - identify real duplicate primitives separately from domain orchestration;
   - flag cycles, cross-feature imports and classes whose package does not
     match their actual owner.
4. **Configuration and operations**
   - compare properties, secrets, provider selection, Admin surfaces,
     observability, rate limits and runbooks;
   - do not collapse two configurations merely because both call the same
     vendor or store files.
5. **Naming and stale-reference correctness**
   - scan live classes, methods, beans, routes, templates, JavaScript, config,
     migrations and seed identities for stale project names such as `Ulp*`;
   - classify each match as live defect, historical evidence, ignored local
     IDE state or applied-migration history before changing it;
   - use forward migration/rebaseline decisions for applied SQL rather than
     editing an applied migration and changing its checksum.
6. **Regression and compatibility**
   - enumerate route, JSON, database, cache, storage, provider-idempotency,
     authorization and UI contracts that any approved slice must preserve;
   - define rollback and parity evidence before implementation.

At least two independent agents must review the same frozen inventory. An
agent may not approve its own implementation.

## 5. Required disposition for every candidate

Every candidate receives exactly one recorded disposition:

- `KEEP_SEPARATE_DOMAIN_OWNER`: intentionally Practice-private or
  non-Practice-private;
- `SHARE_INTERFACE_ONLY`: expose a narrow provider/storage port while keeping
  domain orchestration separate;
- `ADAPTER_BRIDGE`: consume a shared primitive through a compatibility adapter;
- `MOVE_WITH_COMPATIBILITY_FACADE`: move only after caller parity, data/config
  migration and rollback are proven;
- `DEFER_WITH_OWNER_AND_TRIGGER`: not yet safe or valuable to reconcile;
- `DELETE_ONLY_WITH_LIVE_CONSUMER_PROOF`: dead/duplicate and safe to remove.

“Both call AI” and “both store files” are not sufficient reasons to share a
class, configuration object, table or lifecycle job.

## 6. Compatibility-first implementation rules

If the frozen inventory approves implementation:

1. introduce the smallest interface/adapter at the consumer boundary;
2. keep existing Practice routes, DTO/JSON identities, prompt/cache versions,
   database rows, asset/storage identities and authorization behavior stable;
3. preserve current responsive/Vietnamese Practice surfaces;
4. use a compatibility facade before moving a live class or configuration;
5. provide an explicit rollback seam and do not delete the old path in the
   same slice unless parity and retained-data obligations are proven;
6. never route Practice provider calls through a generic Admin/common switch
   that weakens purpose, retention, idempotency or cost identity;
7. never route Practice private media through a generic public upload path;
8. do not combine schema/data migration with unrelated package cleanup.

Any proposal that cannot preserve these invariants is `NO_GO` and remains
separate.

## 7. Known naming evidence to carry forward

The current live-source scan found no production Java/package identity named
`Ulp*`. Existing documentation mentions stale ignored IntelliJ module state as
historical diagnostic evidence and should not be blindly rewritten.

The pre-integration feature snapshot contained `@ulp.edu.vn` seed identities in
V23 and a provenance-only `ULP` comment in V41. The integrated `main` snapshot
already carries KSH identities/text there and V53 standardizes the site name,
so those findings remain historical audit evidence rather than current-source
defects. Any retained database with old checksums still follows the explicit
forward/rebaseline decision; do not use Flyway repair as a shortcut. Ignored
`.idea` references such as `UlpApplication` or an `ulp` module are local IDE
debt and must never be staged.

## 8. Validation, commits and exit criteria

During discovery, agents perform read-only inspection and static diff review;
they do not run tests after each finding. Approved implementation slices form
one validation unit for this phase.

The phase can exit only when:

- the two-family AI/storage ownership map and consumer graph are complete;
- every candidate has one disposition and owner;
- no unapproved commonization or package movement is present;
- route/JSON/schema/cache/storage/provider/auth parity is proven for every
  implemented adapter or move;
- rollback and retained-data handling are documented;
- consolidated JDK 17 validation and the smallest complete integration/browser
  matrix for the approved boundaries are green;
- commits are granular and reviewable, then pushed once for the phase; and
- the resulting snapshot is independently audited before the next gate.

The phase may legitimately close with most candidates marked
`KEEP_SEPARATE_DOMAIN_OWNER`. Integration is a correctness decision, not a
quota of classes that must be moved.
