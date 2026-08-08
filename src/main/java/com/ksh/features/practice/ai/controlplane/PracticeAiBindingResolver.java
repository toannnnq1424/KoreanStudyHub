package com.ksh.features.practice.ai.controlplane;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Optional;

@Service
public class PracticeAiBindingResolver {

    public static final String PROVIDER_FAMILY = "OPENAI_COMPATIBLE";
    public static final String TRANSPORT_DIALECT = "OPENAI_COMPATIBLE_V1";

    private final PracticeAiPurposeBindingRepository bindingRepository;
    private final PracticeAiControlPlaneCodec codec;

    public PracticeAiBindingResolver(
            PracticeAiPurposeBindingRepository bindingRepository,
            PracticeAiControlPlaneCodec codec) {
        this.bindingRepository = bindingRepository;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public PracticeAiResolvedBinding resolve(PracticeAiPurpose purpose) {
        PracticeAiPurposeBinding binding = bindingRepository
                .findDetailed(purpose.name())
                .orElseThrow(PracticeAiBindingResolver::unavailable);
        return resolve(purpose, binding);
    }

    @Transactional(readOnly = true)
    public Optional<PracticeAiExecutionSnapshot> availableSnapshot(
            PracticeAiPurpose purpose) {
        try {
            return Optional.of(resolve(purpose).snapshot());
        } catch (PracticeAiControlPlaneException exception) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public void assertCurrent(PracticeAiExecutionSnapshot snapshot) {
        PracticeAiPurposeBinding current = bindingRepository
                .findDetailed(snapshot.purpose().name())
                .orElseThrow(PracticeAiBindingResolver::changed);
        PracticeAiProviderProfile profile = current.getProviderProfile();
        if (!current.isEnabled()
                || !profile.isEnabled()
                || PracticeAiFixedProviderPresetRegistry
                        .findByProfileCode(profile.getProfileCode()).isPresent()
                || current.getRevision() != snapshot.bindingRevision()
                || profile.getRevision() != snapshot.providerProfileRevision()
                || !current.getModel().equals(snapshot.model())
                || !current.getTransportDialect().equals(snapshot.transportDialect())
                || !profile.getProfileCode().equals(snapshot.providerProfileCode())
                || !profile.getProviderFamily().equals(snapshot.providerFamily())
                || !codec.digest(current.getCapabilityJson())
                        .equals(snapshot.capabilityDigest())
                || !codec.digest(current.getLimitsJson())
                        .equals(snapshot.limitsDigest())
                || !current.getRetentionCode().equals(snapshot.retentionCode())) {
            throw changed();
        }
    }

    private PracticeAiResolvedBinding resolve(
            PracticeAiPurpose purpose,
            PracticeAiPurposeBinding binding) {
        PracticeAiProviderProfile profile = binding.getProviderProfile();
        if (PracticeAiFixedProviderPresetRegistry
                .findByProfileCode(profile.getProfileCode()).isPresent()) {
            throw new PracticeAiControlPlaneException(
                    "PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED", false);
        }
        if (!binding.isEnabled() || !profile.isEnabled()) {
            throw unavailable();
        }
        if (!PROVIDER_FAMILY.equals(profile.getProviderFamily())
                || !TRANSPORT_DIALECT.equals(binding.getTransportDialect())) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_CAPABILITY_INCOMPATIBLE", false);
        }
        String model = required(binding.getModel());
        String retentionCode = required(binding.getRetentionCode());
        URI baseUrl = validateBaseUrl(profile.getBaseUrl());
        PracticeAiCapabilitySet capabilities = codec.parseCapabilities(
                purpose, binding.getCapabilityJson());
        if (purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION
                && (!requiredEvidence(binding.getNonTrainingEvidenceId())
                || !requiredEvidence(binding.getRetentionEvidenceId()))) {
            throw new PracticeAiControlPlaneException(
                    "DIRECT_AUDIO_POLICY_EVIDENCE_INCOMPLETE", false);
        }
        if (purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION) {
            var verification = PracticeDirectAudioCapabilityRegistry
                    .assess(profile.getBaseUrl(), binding.getModel());
            if (!verification.verified()) {
                throw new PracticeAiControlPlaneException(
                        "DIRECT_AUDIO_CAPABILITY_VERIFICATION_REQUIRED", false);
            }
            if (!verification.credentialMode().name().equals(profile.getCredentialMode())) {
                throw new PracticeAiControlPlaneException(
                        "DIRECT_AUDIO_CREDENTIAL_MODE_MISMATCH", false);
            }
            if (!verification.runtimeAuthReady()) {
                throw new PracticeAiControlPlaneException(
                        "DIRECT_AUDIO_ENTERPRISE_ADC_ADAPTER_REQUIRED", false);
            }
        }
        String secret = required(profile.getCredentialSecret());
        PracticeAiLimits limits = codec.parseLimits(binding.getLimitsJson());
        PracticeAiExecutionSnapshot snapshot = new PracticeAiExecutionSnapshot(
                purpose,
                binding.getRevision(),
                profile.getRevision(),
                profile.getProviderFamily(),
                profile.getProfileCode(),
                model,
                binding.getTransportDialect(),
                capabilities,
                limits,
                codec.digest(binding.getCapabilityJson()),
                codec.digest(binding.getLimitsJson()),
                retentionCode);
        return new PracticeAiResolvedBinding(snapshot, baseUrl, secret);
    }

    public static String normalizeBaseUrl(String raw) {
        URI uri = validateBaseUrl(raw);
        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static URI validateBaseUrl(String raw) {
        String value = required(raw);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_PROFILE_INCOMPATIBLE", false, exception);
        }
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_PROFILE_INCOMPATIBLE", false);
        }
        return uri;
    }

    private static String required(String raw) {
        if (raw == null || raw.isBlank()) {
            throw unavailable();
        }
        return raw.trim();
    }

    private static boolean requiredEvidence(String value) {
        return value != null && !value.isBlank();
    }

    private static PracticeAiControlPlaneException unavailable() {
        return new PracticeAiControlPlaneException(
                "PROVIDER_PURPOSE_UNAVAILABLE", false);
    }

    private static PracticeAiControlPlaneException changed() {
        return new PracticeAiControlPlaneException(
                "PROVIDER_BINDING_CHANGED", false);
    }
}
