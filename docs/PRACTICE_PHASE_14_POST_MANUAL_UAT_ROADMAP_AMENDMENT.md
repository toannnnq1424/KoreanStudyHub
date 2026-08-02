# Practice Phase 14 Post-Manual-UAT Roadmap Amendment

Recorded: `2026-07-27`

Status: `ROADMAP_ORDER_LOCKED`

> Post-Pre-14 overlay (`2026-08-02`): Pre-14 is now `CLOSED_MERGED` by PR #55
> at `origin/main@3dfab18a`. The authoritative remaining order inserts
> `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION` and its consolidated gate before
> Pre-15. The deferred placement and unchanged 14A-14F scope below remain
> locked. See
> `PRACTICE_POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_ROADMAP_AMENDMENT.md`.

## 1. User decision

The canonical `14A-14F` **Report an Error & Content Review** capability is
deferred until after Phase 15 Manual UAT. It is no longer an entry condition
for the first production-readiness/Manual-UAT decision because that capability
is not a current product priority.

The historical label **Phase 14** and its `14A-14F` contracts are retained to
avoid renumbering existing documentation, Jira references and audit history.
The phase number is therefore a stable work-package identifier, not the
remaining execution order.

## 2. Authoritative remaining order

```text
Phase 13C3 validation -> coherent commits -> one push -> post-push audits
  -> Phase 13G validation/commits/push
  -> Phase 13H validation/commits/push
  -> end-of-Phase-13 browser/device closure
  -> audit-first Practice/non-Practice AI-storage product/package reconciliation
       -> approved compatibility-first slices + validation/commits/push
  -> comprehensive /practice audit + cleanup validation/commits/push
  -> PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE — CLOSED_MERGED
  -> POST_PRE14_AUTHORING_IMPORT_MODERNIZATION
  -> POST_PRE14_AUTHORING_IMPORT_MODERNIZATION_CONSOLIDATED_GATE
  -> PRE_PHASE_15_RELEASE_CLOSURE_GATE
  -> Phase 15 Manual UAT & Release Hardening
  -> deferred Phase 14 Report an Error & Content Review (14A-14F)
  -> Phase 16 only after its separate product GO
```

## 3. Work that is not deferred

This amendment changes only the placement of the Report-an-Error feature. It
does **not** reduce, postpone or waive either prerequisite program:

- `PRE_PHASE_14_PRODUCTION_CORRECTNESS_GATE` and all already routed Pre-14
  scoring, explanation, Korean assessment, cache/bundle identity, schema,
  compatibility, privacy, toolchain and production-correctness work remain
  mandatory before Manual UAT;
- `PRE_PHASE_15_RELEASE_CLOSURE_GATE` and all final SME/calibration,
  direct-audio disable/rollout decision, retained-data/environment cleanup,
  compatibility disposition, dependency rescan and premium-seed preparation
  remain mandatory before Phase 15 Manual UAT;
- `POST_PRE14_AUTHORING_IMPORT_MODERNIZATION` is now a mandatory predecessor of
  Pre-15. It modernizes authoring/import only under its frozen candidate,
  purpose-binding, storage-profile, compatibility and no-go contracts; and
- Phase 15 still owns the full browser/device/provider/load/security/manual-UAT
  matrix and the initial release GO/NO-GO decision.

Any Pre-14 contract that also prepares a future immutable report target or
attachment/privacy boundary remains implemented as planned. Its acceptance is
useful production hardening even though the learner-facing report workflow is
deferred.

## 4. Deferred Phase 14 release rule

The first Manual-UAT/release decision must not claim that Report an Error is
available. Routes, controls, permissions and documentation for the capability
remain absent or explicitly unavailable until 14A begins.

When Phase 14 is implemented later:

1. consume the already accepted immutable-target and correctness evidence;
2. recheck only time-sensitive/security/schema assumptions that could have
   drifted since Manual UAT;
3. implement `14A-14F` without mutating historical published content or old
   attempts;
4. run the phase's own consolidated validation and 14F end-to-end gate before
   releasing that capability; and
5. do not retroactively relabel the earlier Manual-UAT evidence as Phase 14
   evidence.

This later feature release may require a bounded regression/UAT pass for the
new report workflow, but it does not invalidate the earlier initial-release
decision for the product scope that explicitly excluded Report an Error.

## 5. Supersession rule

Current roadmap statements that require this sequence:

```text
Pre-14 -> Phase 14 -> Pre-15 -> Phase 15
```

are superseded only with respect to execution order. Historical phase results,
the content of the Pre-14/Pre-15 work, and the detailed 14A-14F feature contract
remain audit history or future implementation authority unless separately
amended.

Statements that route a now-closed Pre-14 gate directly to Pre-15 are also
superseded. The current route is Pre-14 closed -> post-Pre-14 authoring/import
epic -> epic consolidated gate -> Pre-15 -> Phase 15 -> deferred Phase 14.
