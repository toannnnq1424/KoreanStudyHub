# Speaking scoring review and acceptance policy

Each evidence artifact has one independent review decision. Allowed decisions
are `PENDING`, `CHANGES_REQUIRED`, `REJECTED`, and `ACCEPTED`. Only an
`ACCEPTED` decision receives a `reviewDecisionId`, and the acceptance applies
only to the exact artifact SHA-256 recorded in that decision.

The developer or artifact creator must not self-accept an artifact unless the
organization has formally assigned that person the required reviewer authority.
A final release owner cannot substitute for a prerequisite privacy, Korean SME,
acoustic, fairness, data, or calibration review.

Valid reviewer authority by artifact class:

- `ALIGNER_CAPABILITY_CAPTURE` and `KOREAN_TIMESTAMP_SAMPLE_REPORT`: Korean
  pronunciation/phonetics SME, speech/acoustic reviewer, or another formally
  assigned Korean speech-quality reviewer.
- `CORPUS_MANIFEST_REPORT`: Korean SME, data owner, or calibration owner.
- `ACOUSTIC_CALIBRATION_REPORT`: speech/acoustic reviewer, qualified Korean
  speech SME, or assigned speech-quality calibration owner.
- `FAIRNESS_REVIEW_REPORT`: Korean SME, acoustic reviewer, or fairness owner.
- `REPEATABILITY_REPORT`: scoring, calibration, or release owner, but only
  against thresholds that were defined before acceptance.
- Provider policy and captured-request/receipt artifacts: data, privacy, or
  security owner, or a release owner explicitly delegated that authority.

Every review record must identify the artifact, exact digest, review category,
reviewer name/role/authority, evidence reviewed, scope, findings, conditions,
decision, and decision date. If artifact content or a material production
configuration changes, the prior acceptance no longer applies and a new review
is required.

`PENDING`, `CHANGES_REQUIRED`, and `REJECTED` artifacts cannot enable learner
visibility, pronunciation/fluency/holistic scores, or attempt points. Completion
of technical evidence collection is not acceptance.
