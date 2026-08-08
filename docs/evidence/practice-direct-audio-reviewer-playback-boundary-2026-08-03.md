# Direct-audio reviewer playback boundary — 2026-08-03

Status: `IMPLEMENTED_DEFAULT_OFF / REVIEWER_ONLY / AUTHORIZED_ACCESS_AUDITED / NON_SCORE_BEARING_UI`.

## Current authoritative boundary

| Concern | Enforced condition | Evidence |
| --- | --- | --- |
| Route | Separate default-off playback and metadata-inspection routes; neither is the learner `speaking-media` route. | `DirectAudioReviewerPlaybackController`, `DirectAudioReviewerInspectionController`, both default-off properties |
| Reviewer authorization | Exact reviewer ID, attempt and `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` named grant; revoked or expired grants never resolve media. | `DirectAudioReviewerPlaybackStore` query |
| Consent / withdrawal | The latest purpose-scoped consent event must be `GRANTED`. A later `WITHDRAWN` event blocks every future byte-range open. | same query, latest-event subquery |
| Dark-result binding | V108 binds each new V107 dark observation to exactly `attempt_id`, `question_id` and `media_id`; rows created before V108 have null binding and are not playable. | `V108__practice_speaking_direct_audio_observation_media_binding.sql` |
| Retention / deletion | The linked dark observation must be undeleted and before `delete_after`; media must be `READY`. Cleanup/deletion or expiry blocks the next request. | same query |
| Transport | Only the original private `PRACTICE_SPEAKING` object is opened after all SQL guards. HTTP ranges are served with `Cache-Control: no-store, private`, no URL/presign, storage key, token or audio bytes in logs/audit. | `DirectAudioReviewerPlaybackService`, controller tests |
| Authorized access audit | V110 records only reviewer/attempt/question/media/observation identity, exact purpose, `INSPECTION_METADATA` or `PLAYBACK_OPEN`, and time. V111 requires an explicit immutable retention-policy ID and per-row deletion deadline. Missing policy/duration or a failed insert blocks metadata response and storage open. | V110/V111 migrations, `DirectAudioReviewerAccessAudit` |
| Reviewer page | Separate default-off, server-rendered page uses the same audited metadata coordinator and same-origin range URL. `preload=none` prevents automatic byte open; no script, provider values, payload, score, URL signing or storage identity enters its model. | `DirectAudioReviewerPageController`, `direct-audio-reviewer.html`, focused/static tests |
| Learner score release | None. Reviewer routes are not imported by result, progress, presenter or learner DTO paths. Inspection exposes only contract/provenance/completeness/retention metadata and explicit `scoreReleaseEligible=false`; provider observations, aggregates, payload and score values remain server-side. | `DirectAudioDarkObservationService`, inspection controller/static scan |

## Negative cases

The database resolver returns no descriptor—and the service turns that into the
same bounded not-found response—when any one of: reviewer grant is missing,
revoked or expired; latest consent is withdrawn; dark observation is missing,
expired or deleted; `question_id`/`media_id` differs; media is not `READY`; or
the private descriptor is malformed. Future opens re-check the complete query;
an already-open stream is intentionally not treated as an authorization cache.

## Verification

JDK `17.0.19`, no provider, object-storage or database connection:

```text
mvnw -Dtest=DirectAudioDarkObservationServiceTest,DirectAudioDarkObservationPersistenceStaticTest,DirectAudioReviewerPlaybackServiceTest,DirectAudioReviewerPlaybackControllerTest,PracticeAim8CompatibilityStaticContractTest,PracticeAim7PdfAuthoringStaticContractTest test
```

The counts below are the original pre-integration capture. The exact Practice
migration bytes were subsequently renumbered without content changes:
V92→V107, V93→V108, V94→V109, V95→V110 and V96→V111. Current source and test
references use the V103–V111 mapping; the historical counts remain labeled here
instead of being rewritten as a new database rehearsal.

The V95 focused reviewer gate passed `15/15`; the combined branch-B/result/
migration static gate passed `84/84`. `mvnw -DskipTests package` also passed.

A fresh, named MySQL 8.4 disposable database applied exactly V1–V95. The
dedicated Spring schema integration test passed `1/1` and verified the V95
columns and purpose/action/outcome CHECK constraints. The 768 MiB container was
stopped with `--rm`; its database and synthetic credential were removed. No
shared database was touched. The existing cleanup integration suite passed
`18/18` on the V95 catalog when the disposable JDBC URL used the application's
timezone convention; an intentionally investigated conflicting
`serverTimezone=UTC` override was removed after trace evidence showed it shifted
JDBC-written `LocalDateTime` leases by seven hours on JPA read.

The forward-only V96 retention follow-up then passed `31/31` focused and
`89/89` combined tests. A second fresh disposable database applied exactly
V1–V96; its schema/cleanup integration gate passed `19/19`, and package passed.
The purge worker is default-off and capped at 1,000 expired rows per run. The
product/data owner subsequently approved `P90D` under immutable policy ID
`KSH-SPEAKING-DIRECT-AUDIO-REVIEWER-ACCESS-AUDIT-RETENTION-V1`; those values
are now the repository defaults. Invalid runtime overrides still fail closed.

## Remaining release blockers

- The disposable-DB migration rehearsal is green, but enablement still needs a
  real, approved consent/grant/deletion operational runbook; the endpoint
  remains off by default.
- Provider capture, immutable provider policy evidence and Korean acoustic
  corpus/calibration/fairness/repeatability evidence are still red.
- The minimal visual inspection/playback page is implemented default-off; an
  audit-event history presenter remains a separate non-score-bearing slice.
  Denied-probe auditing is intentionally not in current V110:
  identifiers/network metadata and their retention require a separate approved
  privacy/security policy. The V111 purge mechanism and `P90D` policy identity
  are approved, but the worker and reviewer APIs remain default-off pending the
  wider operational release gate. V109 now enqueues exact consent-withdrawal cleanup
  through the existing private-media worker, which remains default-off until
  an operational release configuration is approved. No learner acoustic score
  or playback UI is authorized by this work.
