# KSH Speaking reviewer-access audit retention V1

Artifact ID:
`KSH-SPEAKING-DIRECT-AUDIO-REVIEWER-ACCESS-AUDIT-RETENTION-V1`

Status: `APPROVED_PREPRODUCTION_PRODUCT_PRIVACY_BASELINE`

Approved by the product/data owner on `2026-08-03`.

## Decision

Authorized reviewer-access metadata for
`PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` is retained for `P90D` from the
event time. This policy applies only to successful metadata inspection and
audio playback opens that already passed named grant, consent, binding and
retention checks.

The audit may contain reviewer, attempt, question, media and dark-observation
identities, action/outcome codes and timestamps. It must not contain audio,
storage keys, playback URLs, credentials/tokens, provider payload/request IDs,
acoustic values, scores, IP addresses or user-agent strings.

V111 binds the policy ID and exact deletion deadline to every new event. The
purge worker deletes only elapsed rows in bounded batches and remains default
off until the operational release owner enables and monitors it. Enabling this
retention policy does not enable the reviewer page, metadata API, playback API,
provider transport or learner score release.

Denied-probe metadata is not collected under V1. Adding it, extending `P90D`
or expanding retained fields requires a new immutable policy version.
