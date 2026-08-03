package com.ksh.features.practice.ai.speaking;

/**
 * Provider boundary dedicated to governed direct-audio Speaking evaluation.
 * This is intentionally separate from the transcript-only structured client.
 */
public interface DirectAudioSpeakingEvaluationPort {

    Receipt evaluate(AuthorizedRequest request);

    record AuthorizedRequest(
            String requestId,
            Long attemptId,
            Long questionId,
            byte[] audioBytes,
            String mimeType,
            String audioDigest,
            String purpose,
            String policyBundleId,
            String policyBundleFingerprint,
            String cacheIdentity
    ) {
        public AuthorizedRequest {
            audioBytes = audioBytes == null ? new byte[0] : audioBytes.clone();
        }

        @Override
        public byte[] audioBytes() {
            return audioBytes.clone();
        }

        @Override
        public String toString() {
            return "AuthorizedRequest[requestId=" + requestId
                    + ",attemptId=" + attemptId
                    + ",questionId=" + questionId
                    + ",audioBytes=<redacted>,mimeType=" + mimeType
                    + ",purpose=" + purpose
                    + ",policyBundleId=" + policyBundleId + "]";
        }
    }

    record Receipt(String providerRequestId, boolean audioConsumed) {
    }
}
