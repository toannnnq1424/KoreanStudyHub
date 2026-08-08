# Practice Speaking direct-audio evidence intake

This directory accepts only redacted, reviewable evidence reports. Do not place
learner audio, API keys, bearer/ADC tokens, storage keys, raw playback URLs or
unredacted provider payloads in the repository.

The authoritative intake file is
`docs/operations/practice-speaking-direct-audio-release-evidence-manifest.json`.
Its current `EXPERIMENTAL_DEMO_CONFIGURATION_REQUIRED` state is intentional.
Six local aligner/calibration reports are available as experimental evidence,
not accepted production evidence. Four provider artifacts remain deferred or
not required for the experimental demo: non-training policy, retention policy,
redacted captured request and redacted captured response receipt. Product/SME
approval authorizes evidence collection and review; it is not standardized
result acceptance.

For each supplied report:

1. store a redacted report below this directory;
2. calculate `shasum -a 256 <report>`;
3. assign `artifactId` as
   `KSH-DA-EVIDENCE-<KIND>-<YYYYMMDD>-<first-12-sha256>`;
4. set its production-review state to `SUPPLIED_REVIEW_PENDING` (the demo
   manifest may label the same digest `AVAILABLE_EXPERIMENTAL_EVIDENCE`); and
5. set `ACCEPTED` only after `reviewDecisionId` names the immutable review
   decision covering that exact digest.

The forced-aligner report must name exact component type/provider/model/version
and demonstrate Korean eojjeol or word timestamps. Syllable, jamo and phoneme
claims need separate captured evidence. The provider capture must contain only
redacted request/response metadata and prove the exact receipt fields required
by the manifest. The corpus report must enumerate device, environment, voice,
repeated-take and SME-rater coverage without committing raw recordings.

The explicitly separated experimental demo may expose clearly labeled,
non-score-bearing feedback. It does not authorize standardized pronunciation,
fluency or holistic scores, attempt points, best/latest progress or production
assessment claims. Those uses require a separate future score-release decision
and are not representable in this manifest version.

Review authority and separation-of-duties rules are defined in
`speaking-scoring-review-and-acceptance-policy.md`. Current independent records
are in `speaking-scoring-review-decisions-2026-08-07.json`. User review feedback
left the aligner capability record `PENDING` and marked the other five
`CHANGES_REQUIRED`; none has a `reviewDecisionId`, and the developer has not
self-accepted them. Thresholds registered before the next evaluation are in
`speaking-scoring-calibration-preregistered-thresholds-v1.json`.
