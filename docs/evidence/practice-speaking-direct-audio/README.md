# Practice Speaking direct-audio evidence intake

This directory accepts only redacted, reviewable evidence reports. Do not place
learner audio, API keys, bearer/ADC tokens, storage keys, raw playback URLs or
unredacted provider payloads in the repository.

The authoritative intake file is
`docs/operations/practice-speaking-direct-audio-release-evidence-manifest.json`.
Its current `BLOCKED_EXTERNAL_EVIDENCE` state is intentional. Product/SME
approval authorizes evidence collection and review; it is not evidence that a
component, provider response, corpus result or acceptance threshold exists.

For each supplied report:

1. store a redacted report below this directory;
2. calculate `shasum -a 256 <report>`;
3. assign `artifactId` as
   `KSH-DA-EVIDENCE-<KIND>-<YYYYMMDD>-<first-12-sha256>`;
4. set its manifest state to `SUPPLIED_REVIEW_PENDING`; and
5. set `ACCEPTED` only after `reviewDecisionId` names the immutable review
   decision covering that exact digest.

The forced-aligner report must name exact component type/provider/model/version
and demonstrate Korean eojjeol or word timestamps. Syllable, jamo and phoneme
claims need separate captured evidence. The provider capture must contain only
redacted request/response metadata and prove the exact receipt fields required
by the manifest. The corpus report must enumerate device, environment, voice,
repeated-take and SME-rater coverage without committing raw recordings.

Even a fully accepted v1 intake may authorize only dark validation. Learner
visibility, pronunciation/fluency scoring, holistic score and attempt points
require a separate future score-release decision and are not representable in
this manifest version.
