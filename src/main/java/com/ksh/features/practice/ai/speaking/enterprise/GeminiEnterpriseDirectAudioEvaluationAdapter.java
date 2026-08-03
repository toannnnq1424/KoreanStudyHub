package com.ksh.features.practice.ai.speaking.enterprise;

import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneException;
import com.ksh.features.practice.ai.controlplane.PracticeAiCredentialMode;
import com.ksh.features.practice.ai.controlplane.PracticeDirectAudioCapabilityRegistry;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationPort;
import com.ksh.features.practice.ai.speaking.DirectAudioSpeakingEvaluationService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise-only direct-audio boundary. It is deliberately not a Spring bean
 * until workload identity and the captured provider transport are approved.
 */
public final class GeminiEnterpriseDirectAudioEvaluationAdapter
        implements DirectAudioSpeakingEvaluationPort {

    static final long MINIMUM_REMAINING_TOKEN_SECONDS = 60;
    public static final String GOOGLE_CLOUD_PLATFORM_SCOPE =
            "https://www.googleapis.com/auth/cloud-platform";
    private static final Pattern VERTEX_PATH = Pattern.compile(
            "^/v1(?:beta1)?/projects/([^/]+)/locations/([^/]+)/endpoints/openapi$");

    private final EnterpriseBindingAuthority bindingAuthority;
    private final GoogleCloudShortLivedAccessTokenSource tokenSource;
    private final EnterpriseAudioTransport transport;
    private final Clock clock;

    public GeminiEnterpriseDirectAudioEvaluationAdapter(
            EnterpriseBindingAuthority bindingAuthority,
            GoogleCloudShortLivedAccessTokenSource tokenSource,
            EnterpriseAudioTransport transport,
            Clock clock) {
        this.bindingAuthority = Objects.requireNonNull(bindingAuthority);
        this.tokenSource = Objects.requireNonNull(tokenSource);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Receipt evaluate(AuthorizedRequest request) {
        assertGovernedRequest(request);
        AuthorizedEnterpriseBinding binding = bindingAuthority.resolve();
        assertBinding(binding);
        GoogleCloudShortLivedAccessTokenSource.TokenRequest tokenRequest =
                new GoogleCloudShortLivedAccessTokenSource.TokenRequest(
                        binding.audience(), binding.scope(), binding.project(),
                        binding.location(), binding.endpoint(),
                        binding.credentialModeRevision());
        GoogleCloudShortLivedAccessTokenSource.AccessToken token =
                tokenSource.issue(tokenRequest);
        assertToken(tokenRequest, token);

        ProviderRequestProvenance provenance = new ProviderRequestProvenance(
                binding.providerProfileCode(),
                binding.providerProfileRevision(),
                binding.bindingRevision(),
                PracticeAiCredentialMode.GOOGLE_CLOUD_ADC.name(),
                binding.credentialModeRevision(),
                binding.project(),
                binding.location(),
                binding.endpoint(),
                binding.audience(),
                binding.scope(),
                binding.audioInputEvidenceId(),
                binding.strictStructuredOutputEvidenceId(),
                binding.authEndpointEvidenceId(),
                request.policyBundleFingerprint());
        String providerCacheIdentity = request.cacheIdentity()
                + "|gemini-enterprise-adc|sha256|" + sha256(provenance.identity());
        ProviderReceipt providerReceipt = transport.exchange(new ProviderRequest(
                binding.endpoint(), binding.model(), request.audioBytes(),
                request.mimeType(), token.value(), provenance,
                providerCacheIdentity));
        if (providerReceipt == null) {
            return new Receipt("", false, providerCacheIdentity);
        }
        return new Receipt(
                providerReceipt.providerRequestId(),
                providerReceipt.audioConsumed(),
                providerCacheIdentity);
    }

    private void assertGovernedRequest(AuthorizedRequest request) {
        if (request == null
                || !DirectAudioSpeakingEvaluationService.PURPOSE.equals(request.purpose())
                || !DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_ID
                        .equals(request.policyBundleId())
                || !DirectAudioSpeakingEvaluationService.POLICY_BUNDLE_FINGERPRINT
                        .equals(request.policyBundleFingerprint())
                || request.audioBytes().length == 0
                || blank(request.audioDigest())
                || !request.audioDigest().equals(sha256(request.audioBytes()))
                || blank(request.cacheIdentity())) {
            throw rejected("DIRECT_AUDIO_AUTHORIZED_REQUEST_INVALID");
        }
    }

    private static void assertBinding(AuthorizedEnterpriseBinding binding) {
        if (binding == null) {
            throw rejected("DIRECT_AUDIO_ENTERPRISE_BINDING_NOT_READY");
        }
        var verification = PracticeDirectAudioCapabilityRegistry.assess(
                binding.endpoint() == null ? null : binding.endpoint().toString(),
                binding.model());
        Matcher endpointPath = binding.endpoint() == null
                ? VERTEX_PATH.matcher("")
                : VERTEX_PATH.matcher(binding.endpoint().getPath());
        boolean endpointBound = endpointPath.matches()
                && Objects.equals(binding.project(), endpointPath.group(1))
                && Objects.equals(binding.location(), endpointPath.group(2));
        String endpointAudience = binding.endpoint() == null
                ? ""
                : binding.endpoint().resolve("/").toString();
        if (!PracticeAiCredentialMode.GOOGLE_CLOUD_ADC.name()
                        .equals(binding.credentialMode())
                || binding.providerProfileRevision() < 0
                || binding.bindingRevision() < 0
                || binding.credentialModeRevision()
                        != binding.providerProfileRevision()
                || !verification.verified()
                || !PracticeDirectAudioCapabilityRegistry.GEMINI_ENTERPRISE_CODE
                        .equals(verification.code())
                || !verification.code().equals(binding.providerProfileCode())
                || !PracticeDirectAudioCapabilityRegistry.ENTERPRISE_AUDIO_INPUT_EVIDENCE
                        .equals(binding.audioInputEvidenceId())
                || !PracticeDirectAudioCapabilityRegistry.ENTERPRISE_STRICT_OUTPUT_EVIDENCE
                        .equals(binding.strictStructuredOutputEvidenceId())
                || !PracticeDirectAudioCapabilityRegistry.ENTERPRISE_AUTH_ENDPOINT_EVIDENCE
                        .equals(binding.authEndpointEvidenceId())
                || !endpointBound
                || !endpointAudience.equals(binding.audience())
                || !GOOGLE_CLOUD_PLATFORM_SCOPE.equals(binding.scope())
                || blank(binding.regionEvidenceId())
                || blank(binding.nonTrainingEvidenceId())
                || blank(binding.retentionEvidenceId())
                || blank(binding.deletionSlaEvidenceId())) {
            throw rejected("DIRECT_AUDIO_ENTERPRISE_BINDING_NOT_READY");
        }
    }

    private void assertToken(
            GoogleCloudShortLivedAccessTokenSource.TokenRequest request,
            GoogleCloudShortLivedAccessTokenSource.AccessToken token) {
        if (token == null || blank(token.value())) {
            throw rejected("GOOGLE_CLOUD_ACCESS_TOKEN_INVALID");
        }
        Instant minimumExpiry = clock.instant().plusSeconds(
                MINIMUM_REMAINING_TOKEN_SECONDS);
        if (!token.expiresAt().isAfter(minimumExpiry)) {
            throw rejected("GOOGLE_CLOUD_ACCESS_TOKEN_EXPIRED");
        }
        if (!request.audience().equals(token.audience())) {
            throw rejected("GOOGLE_CLOUD_ACCESS_TOKEN_AUDIENCE_MISMATCH");
        }
        if (!Set.of(request.scope()).equals(token.scopes())) {
            throw rejected("GOOGLE_CLOUD_ACCESS_TOKEN_SCOPE_MISMATCH");
        }
        if (!request.project().equals(token.project())
                || !request.location().equals(token.location())
                || !request.endpoint().equals(token.endpoint())
                || request.credentialModeRevision()
                        != token.credentialModeRevision()) {
            throw rejected("GOOGLE_CLOUD_ACCESS_TOKEN_PROVENANCE_MISMATCH");
        }
    }

    private static PracticeAiControlPlaneException rejected(String code) {
        return new PracticeAiControlPlaneException(code, false);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record AuthorizedEnterpriseBinding(
            String providerProfileCode,
            long providerProfileRevision,
            long bindingRevision,
            String credentialMode,
            long credentialModeRevision,
            String project,
            String location,
            URI endpoint,
            String audience,
            String scope,
            String model,
            String audioInputEvidenceId,
            String strictStructuredOutputEvidenceId,
            String authEndpointEvidenceId,
            String regionEvidenceId,
            String nonTrainingEvidenceId,
            String retentionEvidenceId,
            String deletionSlaEvidenceId) {
    }

    public record ProviderRequestProvenance(
            String providerProfileCode,
            long providerProfileRevision,
            long bindingRevision,
            String credentialMode,
            long credentialModeRevision,
            String project,
            String location,
            URI endpoint,
            String audience,
            String scope,
            String audioInputEvidenceId,
            String strictStructuredOutputEvidenceId,
            String authEndpointEvidenceId,
            String policyBundleFingerprint) {
        String identity() {
            return String.join("|",
                    providerProfileCode,
                    Long.toString(providerProfileRevision),
                    Long.toString(bindingRevision),
                    credentialMode,
                    Long.toString(credentialModeRevision),
                    project,
                    location,
                    endpoint.toString(),
                    audience,
                    scope,
                    audioInputEvidenceId,
                    strictStructuredOutputEvidenceId,
                    authEndpointEvidenceId,
                    policyBundleFingerprint);
        }
    }

    public record ProviderRequest(
            URI endpoint,
            String model,
            byte[] audioBytes,
            String mimeType,
            String accessToken,
            ProviderRequestProvenance provenance,
            String providerCacheIdentity) {
        public ProviderRequest {
            audioBytes = audioBytes == null ? new byte[0] : audioBytes.clone();
        }

        @Override
        public byte[] audioBytes() {
            return audioBytes.clone();
        }

        @Override
        public String toString() {
            return "ProviderRequest[endpoint=" + endpoint + ",model=" + model
                    + ",audioBytes=<redacted>,mimeType=" + mimeType
                    + ",accessToken=<redacted>,provenance=" + provenance
                    + ",providerCacheIdentity=" + providerCacheIdentity + "]";
        }
    }

    public record ProviderReceipt(String providerRequestId, boolean audioConsumed) {
    }

    public interface EnterpriseBindingAuthority {
        AuthorizedEnterpriseBinding resolve();
    }

    public interface EnterpriseAudioTransport {
        ProviderReceipt exchange(ProviderRequest request);
    }
}
