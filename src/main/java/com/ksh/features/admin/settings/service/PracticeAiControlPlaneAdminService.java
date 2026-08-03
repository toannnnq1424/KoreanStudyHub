package com.ksh.features.admin.settings.service;

import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingForm;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingRow;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.CapabilityRunRow;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.FixedProviderPresetRow;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileForm;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileRow;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilityTestRunRepository;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneCodec;
import com.ksh.features.practice.ai.controlplane.PracticeAiCredentialMode;
import com.ksh.features.practice.ai.controlplane.PracticeDirectAudioCapabilityRegistry;
import com.ksh.features.practice.ai.controlplane.PracticeAiFixedProviderPresetRegistry;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderProfile;
import com.ksh.features.practice.ai.controlplane.PracticeAiProviderProfileRepository;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurposeBinding;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurposeBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.MASKED;

@Service
public class PracticeAiControlPlaneAdminService {

    private static final Logger log = LoggerFactory.getLogger(
            PracticeAiControlPlaneAdminService.class);

    private final PracticeAiProviderProfileRepository profileRepository;
    private final PracticeAiPurposeBindingRepository bindingRepository;
    private final PracticeAiCapabilityTestRunRepository testRunRepository;
    private final PracticeAiControlPlaneCodec codec;

    public PracticeAiControlPlaneAdminService(
            PracticeAiProviderProfileRepository profileRepository,
            PracticeAiPurposeBindingRepository bindingRepository,
            PracticeAiCapabilityTestRunRepository testRunRepository,
            PracticeAiControlPlaneCodec codec) {
        this.profileRepository = profileRepository;
        this.bindingRepository = bindingRepository;
        this.testRunRepository = testRunRepository;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public List<ProfileRow> profiles() {
        return profileRepository.findAllOrdered().stream()
                .map(profile -> new ProfileRow(
                        profile.getId(),
                        profile.getProfileCode(),
                        profile.getDisplayName(),
                        profile.getProviderFamily(),
                        profile.getCredentialMode(),
                        profile.getBaseUrl(),
                        profile.isEnabled(),
                        profile.getRevision(),
                        profile.getUpdatedAt(),
                        PracticeAiFixedProviderPresetRegistry
                                .findByProfileCode(profile.getProfileCode())
                                .isPresent()))
                .toList();
    }

    public List<FixedProviderPresetRow> fixedProviderPresets(
            List<ProfileRow> configuredProfiles) {
        Map<String, Long> configuredIds = new HashMap<>();
        for (ProfileRow profile : configuredProfiles) {
            configuredIds.put(profile.profileCode(), profile.id());
        }
        return PracticeAiFixedProviderPresetRegistry.all().stream()
                .map(preset -> new FixedProviderPresetRow(
                        preset.key(),
                        preset.profileCode(),
                        preset.displayName(),
                        preset.baseUrl(),
                        preset.keyConsoleUrl(),
                        configuredIds.get(preset.profileCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BindingRow> bindings() {
        Map<PracticeAiPurpose, PracticeAiPurposeBinding> configured =
                new EnumMap<>(PracticeAiPurpose.class);
        for (PracticeAiPurposeBinding binding : bindingRepository.findAllDetailed()) {
            configured.put(PracticeAiPurpose.valueOf(binding.getPurposeCode()), binding);
        }
        return java.util.Arrays.stream(PracticeAiPurpose.values())
                .map(purpose -> bindingRow(purpose, configured.get(purpose)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProfileForm> profileForm(Long id) {
        return profileRepository.findById(id).map(profile -> new ProfileForm(
                profile.getId(),
                profile.getRevision(),
                profile.getProfileCode(),
                profile.getDisplayName(),
                profile.getProviderFamily(),
                profile.getCredentialMode(),
                profile.getBaseUrl(),
                profile.getCredentialSecret() == null ? "" : MASKED,
                profile.isEnabled()));
    }

    @Transactional
    public Long createFixedProviderPreset(String presetKey, Long actorId) {
        var preset = PracticeAiFixedProviderPresetRegistry.findByKey(presetKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PRACTICE_AI_PROVIDER_PRESET_NOT_ALLOWED"));
        Optional<PracticeAiProviderProfile> existing = profileRepository
                .findByProfileCode(preset.profileCode());
        if (existing.isPresent()) {
            PracticeAiProviderProfile profile = existing.get();
            if (profile.isEnabled()
                    || !preset.baseUrl().equals(profile.getBaseUrl())
                    || !PracticeAiBindingResolver.PROVIDER_FAMILY
                            .equals(profile.getProviderFamily())
                    || !PracticeAiCredentialMode.STATIC_BEARER.name()
                            .equals(profile.getCredentialMode())) {
                throw new IllegalStateException(
                        "PRACTICE_AI_PROVIDER_PRESET_STATE_INVALID");
            }
            return profile.getId();
        }
        PracticeAiProviderProfile created = new PracticeAiProviderProfile(
                preset.profileCode(),
                preset.displayName(),
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                preset.baseUrl(),
                PracticeAiCredentialMode.STATIC_BEARER.name(),
                null,
                false,
                actorId);
        Long id = profileRepository.save(created).getId();
        log.info("Fixed disabled Practice AI preset {} created by admin {}",
                preset.profileCode(), actorId);
        return id;
    }

    @Transactional(readOnly = true)
    public BindingForm bindingForm(PracticeAiPurpose purpose) {
        return bindingRepository.findDetailed(purpose.name())
                .map(binding -> {
                    var capabilities = codec.parseCapabilities(
                            purpose, binding.getCapabilityJson());
                    var limits = codec.parseLimits(binding.getLimitsJson());
                    return new BindingForm(
                            purpose,
                            binding.getProviderProfile().getId(),
                            binding.getModel(),
                            capabilities.imageInput(),
                            limits.connectTimeoutMs(),
                            limits.readTimeoutMs(),
                            limits.maxRetries(),
                            limits.maxRequestBytes(),
                            limits.maxResponseBytes(),
                            binding.getRetentionCode(),
                            capabilities.directAudioInput(),
                            binding.getRegionEvidenceId(),
                            binding.getNonTrainingEvidenceId(),
                            binding.getRetentionEvidenceId(),
                            binding.getDeletionSlaEvidenceId(),
                            binding.isEnabled(),
                            binding.getRevision());
                })
                .orElseGet(() -> BindingForm.empty(purpose));
    }

    @Transactional
    public Long saveProfile(ProfileForm form, Long actorId) {
        String code = form.profileCode().trim().toUpperCase(Locale.ROOT);
        var fixedPreset = PracticeAiFixedProviderPresetRegistry
                .findByProfileCode(code);
        if (fixedPreset.isPresent()) {
            assertFixedPresetContract(form, fixedPreset.get());
        }
        String secret = newSecret(form.credentialSecret())
                ? form.credentialSecret().trim()
                : null;
        String credentialMode = form.credentialMode().trim();
        boolean adc = PracticeAiCredentialMode.GOOGLE_CLOUD_ADC.name()
                .equals(credentialMode);
        if (adc && secret != null) {
            throw new IllegalArgumentException("ADC_PROFILE_MUST_NOT_STORE_SECRET");
        }
        if (form.id() == null) {
            if (!adc && secret == null) {
                throw new IllegalArgumentException("PROFILE_SECRET_REQUIRED");
            }
            if (profileRepository.findByProfileCode(code).isPresent()) {
                throw new IllegalArgumentException("PROFILE_CODE_DUPLICATE");
            }
            PracticeAiProviderProfile created = new PracticeAiProviderProfile(
                    code,
                    fixedPreset.map(PracticeAiFixedProviderPresetRegistry.Preset::displayName)
                            .orElseGet(() -> form.displayName().trim()),
                    PracticeAiBindingResolver.PROVIDER_FAMILY,
                    fixedPreset.map(PracticeAiFixedProviderPresetRegistry.Preset::baseUrl)
                            .orElseGet(() -> PracticeAiBindingResolver
                                    .normalizeBaseUrl(form.baseUrl())),
                    credentialMode,
                    secret,
                    fixedPreset.isEmpty() && form.enabled(),
                    actorId);
            Long id = profileRepository.save(created).getId();
            log.info("Practice AI profile {} created by admin {}", code, actorId);
            return id;
        }
        PracticeAiProviderProfile profile = profileRepository.findByIdForUpdate(form.id())
                .orElseThrow(() -> new IllegalArgumentException("PROFILE_NOT_FOUND"));
        if (form.revision() == null || form.revision() != profile.getRevision()) {
            throw new IllegalStateException("PROFILE_REVISION_CONFLICT");
        }
        if (!profile.getProfileCode().equals(code)) {
            throw new IllegalArgumentException("PROFILE_CODE_IMMUTABLE");
        }
        if (!adc && secret == null && profile.getCredentialSecret() == null) {
            throw new IllegalArgumentException("PROFILE_SECRET_REQUIRED");
        }
        profile.update(
                fixedPreset.map(PracticeAiFixedProviderPresetRegistry.Preset::displayName)
                        .orElseGet(() -> form.displayName().trim()),
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                fixedPreset.map(PracticeAiFixedProviderPresetRegistry.Preset::baseUrl)
                        .orElseGet(() -> PracticeAiBindingResolver
                                .normalizeBaseUrl(form.baseUrl())),
                credentialMode,
                secret,
                fixedPreset.isEmpty() && form.enabled(),
                actorId);
        profileRepository.save(profile);
        log.info("Practice AI profile {} updated by admin {}", code, actorId);
        return profile.getId();
    }

    @Transactional
    public boolean toggleProfile(Long id, Long actorId) {
        return profileRepository.findByIdForUpdate(id).map(profile -> {
            if (PracticeAiFixedProviderPresetRegistry
                    .findByProfileCode(profile.getProfileCode()).isPresent()) {
                throw new IllegalStateException(
                        "PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED");
            }
            profile.toggle(actorId);
            profileRepository.save(profile);
            return profile.isEnabled();
        }).orElseThrow(() -> new IllegalArgumentException("PROFILE_NOT_FOUND"));
    }

    @Transactional
    public void deleteProfile(Long id) {
        if (bindingRepository.countByProviderProfileId(id) > 0) {
            throw new IllegalStateException("PROFILE_STILL_BOUND");
        }
        if (!profileRepository.existsById(id)) {
            throw new IllegalArgumentException("PROFILE_NOT_FOUND");
        }
        profileRepository.deleteById(id);
        log.info("Unbound Practice AI profile {} deleted", id);
    }

    @Transactional(readOnly = true)
    public Optional<String> revealSecret(Long id) {
        return profileRepository.findById(id)
                .filter(profile -> PracticeAiFixedProviderPresetRegistry
                        .findByProfileCode(profile.getProfileCode()).isEmpty())
                .map(PracticeAiProviderProfile::getCredentialSecret);
    }

    @Transactional
    public void saveBinding(BindingForm form, Long actorId) {
        PracticeAiProviderProfile profile = profileRepository
                .findById(form.providerProfileId())
                .orElseThrow(() -> new IllegalArgumentException("PROFILE_NOT_FOUND"));
        boolean directAudio = form.purpose()
                == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION;
        if (directAudio && !form.directAudioInput()) {
            throw new IllegalArgumentException("DIRECT_AUDIO_INPUT_CAPABILITY_REQUIRED");
        }
        if (directAudio) {
            assertDirectAudioCandidate(profile, form.model(), form.enabled());
        }
        if (directAudio && form.enabled() && !policyEvidenceComplete(form)) {
            throw new IllegalArgumentException("DIRECT_AUDIO_POLICY_EVIDENCE_INCOMPLETE");
        }
        String capabilityJson = codec.capabilityJson(
                form.purpose(), form.pdfImageInput(), form.directAudioInput());
        String limitsJson = codec.limitsJson(
                form.connectTimeoutMs(),
                form.readTimeoutMs(),
                form.maxRetries(),
                form.maxRequestBytes(),
                form.maxResponseBytes());
        codec.parseCapabilities(form.purpose(), capabilityJson);
        codec.parseLimits(limitsJson);
        Optional<PracticeAiPurposeBinding> existing = bindingRepository
                .findDetailedForUpdate(form.purpose().name());
        if (existing.isPresent()) {
            PracticeAiPurposeBinding binding = existing.get();
            if (form.revision() == null || form.revision() != binding.getRevision()) {
                throw new IllegalStateException("BINDING_REVISION_CONFLICT");
            }
            binding.update(
                    profile,
                    form.model().trim(),
                    PracticeAiBindingResolver.TRANSPORT_DIALECT,
                    capabilityJson,
                    limitsJson,
                    form.retentionCode().trim(),
                    form.enabled(),
                    actorId);
            binding.updatePolicyEvidence(
                    form.regionEvidenceId(), form.nonTrainingEvidenceId(),
                    form.retentionEvidenceId(), form.deletionSlaEvidenceId());
            bindingRepository.save(binding);
        } else {
            if (form.revision() != null) {
                throw new IllegalStateException("BINDING_REVISION_CONFLICT");
            }
            PracticeAiPurposeBinding created = new PracticeAiPurposeBinding(
                    form.purpose(),
                    profile,
                    form.model().trim(),
                    PracticeAiBindingResolver.TRANSPORT_DIALECT,
                    capabilityJson,
                    limitsJson,
                    form.retentionCode().trim(),
                    form.enabled(),
                    actorId);
            created.updatePolicyEvidence(
                    form.regionEvidenceId(), form.nonTrainingEvidenceId(),
                    form.retentionEvidenceId(), form.deletionSlaEvidenceId());
            bindingRepository.save(created);
        }
        log.info("Practice AI purpose {} binding updated by admin {}",
                form.purpose(), actorId);
    }

    @Transactional
    public boolean toggleBinding(PracticeAiPurpose purpose, Long actorId) {
        PracticeAiPurposeBinding binding = bindingRepository
                .findDetailedForUpdate(purpose.name())
                .orElseThrow(() -> new IllegalArgumentException("BINDING_NOT_FOUND"));
        if (!binding.isEnabled()
                && purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION
                && !policyEvidenceComplete(binding)) {
            throw new IllegalStateException("DIRECT_AUDIO_POLICY_EVIDENCE_INCOMPLETE");
        }
        if (!binding.isEnabled()
                && purpose == PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION) {
            assertDirectAudioCandidate(
                    binding.getProviderProfile(), binding.getModel(), true);
        }
        binding.toggle(actorId);
        bindingRepository.save(binding);
        return binding.isEnabled();
    }

    private BindingRow bindingRow(
            PracticeAiPurpose purpose,
            PracticeAiPurposeBinding binding) {
        List<CapabilityRunRow> runs = testRunRepository
                .findByPurposeCodeOrderByStartedAtDesc(
                        purpose.name(), PageRequest.of(0, 5))
                .stream()
                .map(run -> new CapabilityRunRow(
                        run.getId(),
                        run.getBindingRevision(),
                        run.getStatus(),
                        run.getDurationMs(),
                        run.getBoundedErrorCode(),
                        run.getStartedAt()))
                .toList();
        if (binding == null) {
            return new BindingRow(
                    purpose,
                    purpose.displayName(),
                    requiredCapabilities(purpose),
                    null, null, null, false, -1L, null, null,
                    false, false, runs);
        }
        boolean providerModelVerified = purpose
                != PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION
                || PracticeDirectAudioCapabilityRegistry.assess(
                        binding.getProviderProfile().getBaseUrl(), binding.getModel())
                        .verified();
        return new BindingRow(
                purpose,
                purpose.displayName(),
                requiredCapabilities(purpose),
                binding.getProviderProfile().getId(),
                binding.getProviderProfile().getProfileCode(),
                binding.getModel(),
                binding.isEnabled(),
                binding.getRevision(),
                binding.getRetentionCode(),
                binding.getUpdatedAt(),
                providerModelVerified,
                policyEvidenceComplete(binding),
                runs);
    }

    private static boolean newSecret(String value) {
        return value != null && !value.isBlank() && !MASKED.equals(value.trim());
    }

    private static void assertFixedPresetContract(
            ProfileForm form,
            PracticeAiFixedProviderPresetRegistry.Preset preset) {
        String normalizedBaseUrl = PracticeAiBindingResolver
                .normalizeBaseUrl(form.baseUrl());
        if (!preset.displayName().equals(form.displayName().trim())
                || !preset.baseUrl().equals(normalizedBaseUrl)
                || !PracticeAiBindingResolver.PROVIDER_FAMILY
                        .equals(form.providerFamily())
                || !PracticeAiCredentialMode.STATIC_BEARER.name()
                        .equals(form.credentialMode())) {
            throw new IllegalArgumentException(
                    "PRACTICE_AI_PROVIDER_PRESET_CONTRACT_MISMATCH");
        }
    }

    private static String requiredCapabilities(PracticeAiPurpose purpose) {
        return purpose.requiredCapabilities().stream()
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean policyEvidenceComplete(BindingForm form) {
        return present(form.regionEvidenceId())
                && present(form.nonTrainingEvidenceId())
                && present(form.retentionEvidenceId())
                && present(form.deletionSlaEvidenceId());
    }

    private static boolean policyEvidenceComplete(PracticeAiPurposeBinding binding) {
        return present(binding.getRegionEvidenceId())
                && present(binding.getNonTrainingEvidenceId())
                && present(binding.getRetentionEvidenceId())
                && present(binding.getDeletionSlaEvidenceId());
    }

    private static void assertDirectAudioCandidate(
            PracticeAiProviderProfile profile,
            String model,
            boolean enabling) {
        var verification = PracticeDirectAudioCapabilityRegistry
                .assess(profile.getBaseUrl(), model);
        if (!verification.verified()) {
            if (enabling) {
                throw new IllegalStateException(
                        "DIRECT_AUDIO_CAPABILITY_VERIFICATION_REQUIRED");
            }
            return;
        }
        if (!verification.credentialMode().name().equals(profile.getCredentialMode())) {
            throw new IllegalArgumentException("DIRECT_AUDIO_CREDENTIAL_MODE_MISMATCH");
        }
        if (enabling && !verification.runtimeAuthReady()) {
            throw new IllegalStateException(
                    "DIRECT_AUDIO_ENTERPRISE_ADC_ADAPTER_REQUIRED");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
