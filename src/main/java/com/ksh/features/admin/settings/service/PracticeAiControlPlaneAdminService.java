package com.ksh.features.admin.settings.service;

import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingForm;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingRow;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.CapabilityRunRow;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileForm;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileRow;
import com.ksh.features.practice.ai.controlplane.PracticeAiBindingResolver;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilityTestRunRepository;
import com.ksh.features.practice.ai.controlplane.PracticeAiControlPlaneCodec;
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
                        profile.getBaseUrl(),
                        profile.isEnabled(),
                        profile.getRevision(),
                        profile.getUpdatedAt()))
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
                profile.getBaseUrl(),
                MASKED,
                profile.isEnabled()));
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
                            binding.isEnabled(),
                            binding.getRevision());
                })
                .orElseGet(() -> BindingForm.empty(purpose));
    }

    @Transactional
    public Long saveProfile(ProfileForm form, Long actorId) {
        String code = form.profileCode().trim().toUpperCase(Locale.ROOT);
        String secret = newSecret(form.credentialSecret())
                ? form.credentialSecret().trim()
                : null;
        if (form.id() == null) {
            if (secret == null) {
                throw new IllegalArgumentException("PROFILE_SECRET_REQUIRED");
            }
            if (profileRepository.findByProfileCode(code).isPresent()) {
                throw new IllegalArgumentException("PROFILE_CODE_DUPLICATE");
            }
            PracticeAiProviderProfile created = new PracticeAiProviderProfile(
                    code,
                    form.displayName().trim(),
                    PracticeAiBindingResolver.PROVIDER_FAMILY,
                    PracticeAiBindingResolver.normalizeBaseUrl(form.baseUrl()),
                    secret,
                    form.enabled(),
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
        profile.update(
                form.displayName().trim(),
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                PracticeAiBindingResolver.normalizeBaseUrl(form.baseUrl()),
                secret,
                form.enabled(),
                actorId);
        profileRepository.save(profile);
        log.info("Practice AI profile {} updated by admin {}", code, actorId);
        return profile.getId();
    }

    @Transactional
    public boolean toggleProfile(Long id, Long actorId) {
        return profileRepository.findByIdForUpdate(id).map(profile -> {
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
                .map(PracticeAiProviderProfile::getCredentialSecret);
    }

    @Transactional
    public void saveBinding(BindingForm form, Long actorId) {
        PracticeAiProviderProfile profile = profileRepository
                .findById(form.providerProfileId())
                .orElseThrow(() -> new IllegalArgumentException("PROFILE_NOT_FOUND"));
        String capabilityJson = codec.capabilityJson(
                form.purpose(), form.pdfImageInput());
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
            bindingRepository.save(binding);
        } else {
            if (form.revision() != null) {
                throw new IllegalStateException("BINDING_REVISION_CONFLICT");
            }
            bindingRepository.save(new PracticeAiPurposeBinding(
                    form.purpose(),
                    profile,
                    form.model().trim(),
                    PracticeAiBindingResolver.TRANSPORT_DIALECT,
                    capabilityJson,
                    limitsJson,
                    form.retentionCode().trim(),
                    form.enabled(),
                    actorId));
        }
        log.info("Practice AI purpose {} binding updated by admin {}",
                form.purpose(), actorId);
    }

    @Transactional
    public boolean toggleBinding(PracticeAiPurpose purpose, Long actorId) {
        PracticeAiPurposeBinding binding = bindingRepository
                .findDetailedForUpdate(purpose.name())
                .orElseThrow(() -> new IllegalArgumentException("BINDING_NOT_FOUND"));
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
                    null, null, null, false, -1L, null, null, runs);
        }
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
                runs);
    }

    private static boolean newSecret(String value) {
        return value != null && !value.isBlank() && !MASKED.equals(value.trim());
    }

    private static String requiredCapabilities(PracticeAiPurpose purpose) {
        return purpose.requiredCapabilities().stream()
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
