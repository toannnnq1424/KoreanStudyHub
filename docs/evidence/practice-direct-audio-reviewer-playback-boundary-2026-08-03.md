# Direct-audio reviewer playback boundary — 2026-08-03

Status: `IMPLEMENTED_DEFAULT_OFF / REVIEWER_ONLY / NON_SCORE_BEARING`.

## Current authoritative boundary

| Concern | Enforced condition | Evidence |
| --- | --- | --- |
| Route | Separate default-off `DIRECT_AUDIO_REVIEW_MEDIA_CONTENT`; it is not the learner `speaking-media` route. | `DirectAudioReviewerPlaybackController`, `app.practice.speaking-direct-audio.reviewer-playback-api-enabled=false` |
| Reviewer authorization | Exact reviewer ID, attempt and `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` named grant; revoked or expired grants never resolve media. | `DirectAudioReviewerPlaybackStore` query |
| Consent / withdrawal | The latest purpose-scoped consent event must be `GRANTED`. A later `WITHDRAWN` event blocks every future byte-range open. | same query, latest-event subquery |
| Dark-result binding | V93 binds each new dark observation to exactly `attempt_id`, `question_id` and `media_id`; rows created before V93 have null binding and are not playable. | `V93__practice_speaking_direct_audio_observation_media_binding.sql` |
| Retention / deletion | The linked dark observation must be undeleted and before `delete_after`; media must be `READY`. Cleanup/deletion or expiry blocks the next request. | same query |
| Transport | Only the original private `PRACTICE_SPEAKING` object is opened after all SQL guards. HTTP ranges are served with `Cache-Control: no-store, private`, no URL/presign, storage key, token or audio bytes in logs/audit. | `DirectAudioReviewerPlaybackService`, controller tests |
| Learner score release | None. The reviewer route is not imported by result, progress, presenter or learner DTO paths. Direct-audio observations keep score/presenter eligibility false and holistic/attempt points null. | `DirectAudioDarkObservationService`, static scan |

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

The focused command passed after this slice. The wider existing Spring MVC
media suite requires the repository's disposable `TEST_DB_URL`; when that
variable is intentionally absent, `DisposableTestDatabaseEnvironmentGuard`
fails context startup before any application DB connection. This is an
environment guard verdict, not permission to use a shared database.

## Remaining release blockers

- Enablement needs a disposable-DB migration rehearsal and a real, approved
  consent/grant/deletion operational runbook; the endpoint remains off by
  default.
- Provider capture, immutable provider policy evidence and Korean acoustic
  corpus/calibration/fairness/repeatability evidence are still red.
- Reviewer inspection UI and audit event presentation remain separate
  non-score-bearing slices. V94 now enqueues exact consent-withdrawal cleanup
  through the existing private-media worker, which remains default-off until
  an operational release configuration is approved. No learner acoustic score
  or playback UI is authorized by this work.
