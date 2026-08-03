package com.ksh.features.practice.ai.speaking.enterprise;

import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiCredentialMode;
import com.ksh.features.practice.ai.controlplane.PracticeDirectAudioCapabilityRegistry;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationPort;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiEnterpriseDirectAudioEvaluationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TOKEN = "TEST_SHORT_LIVED_TOKEN_MUST_STAY_REDACTED";

    @Test
    void productionTokenSourceIsDisabledAndReadsNoCredentialMaterial() {
        var request = tokenRequest(binding(7));

        assertThatThrownBy(() -> new DisabledGoogleCloudShortLivedAccessTokenSource()
                .issue(request))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                .isEqualTo("GOOGLE_CLOUD_ADC_WORKLOAD_IDENTITY_UNAVAILABLE");
        assertThat(request.toString())
                .doesNotContain("service_account", "private_key", "Bearer");
    }

    @Test
    void validShortLivedTokenCarriesBoundProvenanceAndRedactsTokenAndAudio() {
        AtomicReference<GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderRequest>
                captured = new AtomicReference<>();
        var binding = binding(7);
        var adapter = adapter(binding, validTokenSource(), request -> {
            captured.set(request);
            return new GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderReceipt(
                    "provider-request-1", true);
        });

        DirectAudioSpeakingEvaluationPort.Receipt receipt =
                adapter.evaluate(authorizedRequest());

        assertThat(receipt.audioConsumed()).isTrue();
        assertThat(receipt.providerCacheIdentity())
                .startsWith("base-cache|gemini-enterprise-adc|sha256|")
                .doesNotContain(TOKEN, binding.project(), binding.location());
        assertThat(captured.get().accessToken()).isEqualTo(TOKEN);
        assertThat(captured.get().provenance())
                .extracting(
                        GeminiEnterpriseDirectAudioEvaluationAdapter
                                .ProviderRequestProvenance::audience,
                        GeminiEnterpriseDirectAudioEvaluationAdapter
                                .ProviderRequestProvenance::scope,
                        GeminiEnterpriseDirectAudioEvaluationAdapter
                                .ProviderRequestProvenance::project,
                        GeminiEnterpriseDirectAudioEvaluationAdapter
                                .ProviderRequestProvenance::location,
                        GeminiEnterpriseDirectAudioEvaluationAdapter
                                .ProviderRequestProvenance::credentialModeRevision)
                .containsExactly(
                        binding.audience(), binding.scope(), binding.project(),
                        binding.location(), binding.credentialModeRevision());
        assertThat(captured.get().toString())
                .doesNotContain(TOKEN, new String(authorizedRequest().audioBytes(),
                        StandardCharsets.UTF_8), "Bearer", "Authorization")
                .contains("accessToken=<redacted>", "audioBytes=<redacted>");
        assertThat(validToken(tokenRequest(binding)).toString())
                .doesNotContain(TOKEN, "Bearer", "Authorization")
                .contains("value=<redacted>");
    }

    @Test
    void blankExpiredWrongAudienceScopeAndProvenanceTokensNeverReachTransport() {
        var binding = binding(7);
        var expected = tokenRequest(binding);
        List<InvalidToken> invalid = List.of(
                new InvalidToken(token(expected, "", NOW.plusSeconds(600),
                        expected.audience(), Set.of(expected.scope()), expected.project(),
                        expected.location(), expected.endpoint(), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_INVALID"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(60),
                        expected.audience(), Set.of(expected.scope()), expected.project(),
                        expected.location(), expected.endpoint(), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_EXPIRED"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(600),
                        "https://wrong-audience.invalid/", Set.of(expected.scope()),
                        expected.project(), expected.location(), expected.endpoint(), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_AUDIENCE_MISMATCH"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(600),
                        expected.audience(), Set.of("scope/wrong"), expected.project(),
                        expected.location(), expected.endpoint(), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_SCOPE_MISMATCH"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(600),
                        expected.audience(), Set.of(expected.scope()), "wrong-project",
                        expected.location(), expected.endpoint(), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_PROVENANCE_MISMATCH"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(600),
                        expected.audience(), Set.of(expected.scope()), expected.project(),
                        "wrong-location", expected.endpoint(), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_PROVENANCE_MISMATCH"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(600),
                        expected.audience(), Set.of(expected.scope()), expected.project(),
                        expected.location(), URI.create("https://wrong.invalid/"), 7),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_PROVENANCE_MISMATCH"),
                new InvalidToken(token(expected, TOKEN, NOW.plusSeconds(600),
                        expected.audience(), Set.of(expected.scope()), expected.project(),
                        expected.location(), expected.endpoint(), 8),
                        "GOOGLE_CLOUD_ACCESS_TOKEN_PROVENANCE_MISMATCH"));

        for (InvalidToken entry : invalid) {
            AtomicInteger transfers = new AtomicInteger();
            var adapter = adapter(binding, request -> entry.token(), request -> {
                transfers.incrementAndGet();
                return new GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderReceipt(
                        "must-not-run", true);
            });
            assertThatThrownBy(() -> adapter.evaluate(authorizedRequest()))
                    .isInstanceOf(PracticeAiControlPlaneException.class)
                    .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                    .isEqualTo(entry.errorCode());
            assertThat(transfers).hasValue(0);
        }
    }

    @Test
    void fullyGovernedDarkFlowTransfersOnceAndAuditContainsNoTokenOrAudio() {
        AtomicInteger transfers = new AtomicInteger();
        List<DirectAudioSpeakingEvaluationService.AuditEvent> audit =
                new ArrayList<>();
        var adapter = adapter(binding(7), validTokenSource(), request -> {
            transfers.incrementAndGet();
            return new GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderReceipt(
                    "provider-request-1", true);
        });
        DirectAudioSpeakingEvaluationService service =
                new DirectAudioSpeakingEvaluationService(
                        adapter, audit::add, readiness(true, true));

        var outcome = service.evaluate(candidate(
                DirectAudioSpeakingEvaluationService.ConsentState.ACTIVE,
                DirectAudioSpeakingEvaluationService.RolloutState.DARK_CAPTURE));

        assertThat(outcome.state()).isEqualTo("DARK_CAPTURED_NON_SCORE_BEARING");
        assertThat(outcome.scoreAvailable()).isFalse();
        assertThat(outcome.acousticScoresAvailable()).isFalse();
        assertThat(outcome.cacheIdentity())
                .contains("|gemini-enterprise-adc|sha256|")
                .doesNotContain(TOKEN, "ksh-project", "asia-southeast1");
        assertThat(transfers).hasValue(1);
        assertThat(audit).extracting(
                DirectAudioSpeakingEvaluationService.AuditEvent::eventType)
                .containsExactly(
                        "TRANSFER_AUTHORIZED",
                        "DARK_CAPTURE_COMPLETED");
        assertThat(audit.toString())
                .doesNotContain(TOKEN, "Bearer", "Authorization",
                        "authorized-audio-bytes");
    }

    @Test
    void bindingRevisionChangesProviderCacheIdentityWithoutEmbeddingProvenance() {
        String first = providerCacheIdentity(binding(7));
        String second = providerCacheIdentity(binding(8));

        assertThat(first).isNotEqualTo(second);
        assertThat(first + second)
                .doesNotContain(TOKEN, "ksh-project", "asia-southeast1");
    }

    @Test
    void withdrawnConsentStopsBeforeTokenIssuanceOrAudioTransport() {
        AtomicInteger tokenIssues = new AtomicInteger();
        AtomicInteger transfers = new AtomicInteger();
        var adapter = adapter(binding(7), request -> {
            tokenIssues.incrementAndGet();
            return validToken(request);
        }, request -> {
            transfers.incrementAndGet();
            return new GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderReceipt(
                    "must-not-run", true);
        });
        DirectAudioSpeakingEvaluationService service =
                new DirectAudioSpeakingEvaluationService(
                        adapter, event -> { }, readiness(true, true));

        var candidate = candidate(
                DirectAudioSpeakingEvaluationService.ConsentState.WITHDRAWN,
                DirectAudioSpeakingEvaluationService.RolloutState.DARK_CAPTURE);
        var outcome = service.evaluate(candidate);

        assertThat(outcome.rejectionReason()).isEqualTo(
                "CONSENT_NOT_ACTIVE_FOR_PURPOSE");
        assertThat(tokenIssues).hasValue(0);
        assertThat(transfers).hasValue(0);
    }

    @Test
    void developerOrIncompleteBindingCannotFallbackIntoEnterpriseTokenSource() {
        AtomicInteger tokenIssues = new AtomicInteger();
        AtomicInteger transfers = new AtomicInteger();
        var invalid = withProfileCode(
                binding(7),
                PracticeDirectAudioCapabilityRegistry.GEMINI_DEVELOPER_CODE);
        var adapter = adapter(invalid, request -> {
            tokenIssues.incrementAndGet();
            return validToken(request);
        }, request -> {
            transfers.incrementAndGet();
            return new GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderReceipt(
                    "must-not-run", true);
        });

        assertThatThrownBy(() -> adapter.evaluate(authorizedRequest()))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                .isEqualTo("DIRECT_AUDIO_ENTERPRISE_BINDING_NOT_READY");
        assertThat(tokenIssues).hasValue(0);
        assertThat(transfers).hasValue(0);
    }

    private static String providerCacheIdentity(
            GeminiEnterpriseDirectAudioEvaluationAdapter.AuthorizedEnterpriseBinding binding) {
        AtomicReference<String> identity = new AtomicReference<>();
        var adapter = adapter(binding, validTokenSource(), request -> {
            identity.set(request.providerCacheIdentity());
            return new GeminiEnterpriseDirectAudioEvaluationAdapter.ProviderReceipt(
                    "provider-request", true);
        });
        adapter.evaluate(authorizedRequest());
        return identity.get();
    }

    private static GeminiEnterpriseDirectAudioEvaluationAdapter adapter(
            GeminiEnterpriseDirectAudioEvaluationAdapter.AuthorizedEnterpriseBinding binding,
            GoogleCloudShortLivedAccessTokenSource tokenSource,
            GeminiEnterpriseDirectAudioEvaluationAdapter.EnterpriseAudioTransport transport) {
        return new GeminiEnterpriseDirectAudioEvaluationAdapter(
                () -> binding, tokenSource, transport, CLOCK);
    }

    private static GoogleCloudShortLivedAccessTokenSource validTokenSource() {
        return GeminiEnterpriseDirectAudioEvaluationAdapterTest::validToken;
    }

    private static GoogleCloudShortLivedAccessTokenSource.AccessToken validToken(
            GoogleCloudShortLivedAccessTokenSource.TokenRequest request) {
        return token(request, TOKEN, NOW.plusSeconds(600), request.audience(),
                Set.of(request.scope()), request.project(), request.location(),
                request.endpoint(), request.credentialModeRevision());
    }

    private static GoogleCloudShortLivedAccessTokenSource.AccessToken token(
            GoogleCloudShortLivedAccessTokenSource.TokenRequest request,
            String value,
            Instant expiresAt,
            String audience,
            Set<String> scopes,
            String project,
            String location,
            URI endpoint,
            long revision) {
        return new GoogleCloudShortLivedAccessTokenSource.AccessToken(
                value, expiresAt, audience, scopes, project, location,
                endpoint, revision);
    }

    private static GoogleCloudShortLivedAccessTokenSource.TokenRequest tokenRequest(
            GeminiEnterpriseDirectAudioEvaluationAdapter.AuthorizedEnterpriseBinding binding) {
        return new GoogleCloudShortLivedAccessTokenSource.TokenRequest(
                binding.audience(), binding.scope(), binding.project(),
                binding.location(), binding.endpoint(), binding.credentialModeRevision());
    }

    private static GeminiEnterpriseDirectAudioEvaluationAdapter
            .AuthorizedEnterpriseBinding binding(long revision) {
        URI endpoint = URI.create(
                "https://asia-southeast1-aiplatform.googleapis.com/v1/projects/"
                        + "ksh-project/locations/asia-southeast1/endpoints/openapi");
        return new GeminiEnterpriseDirectAudioEvaluationAdapter.AuthorizedEnterpriseBinding(
                PracticeDirectAudioCapabilityRegistry.GEMINI_ENTERPRISE_CODE,
                revision,
                11,
                PracticeAiCredentialMode.GOOGLE_CLOUD_ADC.name(),
                revision,
                "ksh-project",
                "asia-southeast1",
                endpoint,
                endpoint.resolve("/").toString(),
                GeminiEnterpriseDirectAudioEvaluationAdapter.GOOGLE_CLOUD_PLATFORM_SCOPE,
                PracticeDirectAudioCapabilityRegistry.GEMINI_ENTERPRISE_MODEL,
                PracticeDirectAudioCapabilityRegistry.ENTERPRISE_AUDIO_INPUT_EVIDENCE,
                PracticeDirectAudioCapabilityRegistry.ENTERPRISE_STRICT_OUTPUT_EVIDENCE,
                PracticeDirectAudioCapabilityRegistry.ENTERPRISE_AUTH_ENDPOINT_EVIDENCE,
                "region/evidence/1",
                "non-training/evidence/1",
                "retention/evidence/1",
                "deletion/evidence/1");
    }

    private static GeminiEnterpriseDirectAudioEvaluationAdapter
            .AuthorizedEnterpriseBinding withProfileCode(
                    GeminiEnterpriseDirectAudioEvaluationAdapter
                            .AuthorizedEnterpriseBinding binding,
                    String profileCode) {
        return new GeminiEnterpriseDirectAudioEvaluationAdapter.AuthorizedEnterpriseBinding(
                profileCode,
                binding.providerProfileRevision(),
                binding.bindingRevision(),
                binding.credentialMode(),
                binding.credentialModeRevision(),
                binding.project(),
                binding.location(),
                binding.endpoint(),
                binding.audience(),
                binding.scope(),
                binding.model(),
                binding.audioInputEvidenceId(),
                binding.strictStructuredOutputEvidenceId(),
                binding.authEndpointEvidenceId(),
                binding.regionEvidenceId(),
                binding.nonTrainingEvidenceId(),
                binding.retentionEvidenceId(),
                binding.deletionSlaEvidenceId());
    }

    private static DirectAudioSpeakingEvaluationPort.AuthorizedRequest authorizedRequest() {
        byte[] audio = "authorized-audio-bytes".getBytes(StandardCharsets.UTF_8);
        return new DirectAudioSpeakingEvaluationPort.AuthorizedRequest(
                "request-1", 10L, 20L, audio, "audio/webm", sha256(audio),
                DirectAudioSpeakingEvaluationService.PURPOSE,
                DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID,
                DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_FINGERPRINT,
                "base-cache");
    }

    private static DirectAudioSpeakingEvaluationService.Candidate candidate(
            DirectAudioSpeakingEvaluationService.ConsentState consent,
            DirectAudioSpeakingEvaluationService.RolloutState rollout) {
        byte[] audio = "authorized-audio-bytes".getBytes(StandardCharsets.UTF_8);
        return new DirectAudioSpeakingEvaluationService.Candidate(
                "request-1", 1L, 10L, 20L,
                new DirectAudioSpeakingEvaluationService.AudioEvidence(
                        "handle-1", audio, "audio/webm", sha256(audio),
                        true, true, false),
                new DirectAudioSpeakingEvaluationService.ConsentEvidence(
                        "consent/1", consent,
                        DirectAudioSpeakingEvaluationService.PURPOSE,
                        "KSH-DISCLOSURE-V1", true),
                new DirectAudioSpeakingEvaluationService.ReviewerPolicy(
                        "reviewer/1", true, true),
                new DirectAudioSpeakingEvaluationService.ProviderPolicy(
                        PracticeDirectAudioCapabilityRegistry.GEMINI_ENTERPRISE_CODE,
                        "region/evidence/1", "non-training/evidence/1",
                        "retention/evidence/1", "deletion/evidence/1"),
                new DirectAudioSpeakingEvaluationService.CalibrationEvidence(
                        "corpus/1", "acoustic/1", "fairness/1", "repeatability/1"),
                rollout);
    }

    private static DirectAudioSpeakingEvaluationService.ReadinessAuthority readiness(
            boolean provider, boolean calibration) {
        return new DirectAudioSpeakingEvaluationService.ReadinessAuthority() {
            @Override
            public boolean providerPolicyAllowed(
                    DirectAudioSpeakingEvaluationService.ProviderPolicy policy) {
                return provider;
            }

            @Override
            public boolean calibrationApproved(
                    DirectAudioSpeakingEvaluationService.CalibrationEvidence evidence) {
                return calibration;
            }
        };
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record InvalidToken(
            GoogleCloudShortLivedAccessTokenSource.AccessToken token,
            String errorCode) {
    }
}
