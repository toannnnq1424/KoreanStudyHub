package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileForm;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingForm;
import com.ksh.features.admin.settings.service.PracticeAiControlPlaneAdminService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.MASKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAiControlPlaneAdminServiceTest {

    private final PracticeAiProviderProfileRepository profiles =
            mock(PracticeAiProviderProfileRepository.class);
    private final PracticeAiPurposeBindingRepository bindings =
            mock(PracticeAiPurposeBindingRepository.class);
    private final PracticeAiCapabilityTestRunRepository runs =
            mock(PracticeAiCapabilityTestRunRepository.class);
    private final PracticeAiControlPlaneAdminService service =
            new PracticeAiControlPlaneAdminService(
                    profiles,
                    bindings,
                    runs,
                    new PracticeAiControlPlaneCodec(new ObjectMapper()));

    @Test
    void ordinaryEditFormMasksSecretAndDedicatedRevealIsExplicit() {
        PracticeAiProviderProfile profile = profile();
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));

        ProfileForm form = service.profileForm(7L).orElseThrow();

        assertThat(form.credentialSecret()).isEqualTo(MASKED);
        assertThat(form.toString()).doesNotContain("AIM5_REALISTIC_TEST_SECRET");
        assertThat(service.revealSecret(7L))
                .contains("AIM5_REALISTIC_TEST_SECRET");
    }

    @Test
    void maskedUpdateRetainsExistingSecretAndRejectsStaleRevision() {
        PracticeAiProviderProfile profile = profile();
        when(profiles.findByIdForUpdate(7L)).thenReturn(Optional.of(profile));
        ProfileForm form = new ProfileForm(
                7L,
                0L,
                "PRACTICE_PRIMARY",
                "Renamed",
                "OPENAI_COMPATIBLE",
                "https://provider.invalid/v1/",
                MASKED,
                true);

        service.saveProfile(form, 9L);

        assertThat(profile.getCredentialSecret())
                .isEqualTo("AIM5_REALISTIC_TEST_SECRET");
        assertThat(profile.getBaseUrl())
                .isEqualTo("https://provider.invalid/v1");
        assertThatThrownBy(() -> service.saveProfile(
                new ProfileForm(
                        7L, 8L, "PRACTICE_PRIMARY", "Stale",
                        "OPENAI_COMPATIBLE", "https://provider.invalid/v1",
                        MASKED, true),
                9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROFILE_REVISION_CONFLICT");
    }

    @Test
    void boundProfileCannotBeDeleted() {
        when(bindings.countByProviderProfileId(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteProfile(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROFILE_STILL_BOUND");
        verify(profiles, never()).deleteById(7L);
    }

    @Test
    void directAudioBindingRequiresExplicitCapabilityBeforePersistence() {
        when(profiles.findById(7L)).thenReturn(Optional.of(profile()));

        assertThatThrownBy(() -> service.saveBinding(
                directAudioForm(false, false, "", "", "", ""), 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DIRECT_AUDIO_INPUT_CAPABILITY_REQUIRED");
        verify(bindings, never()).save(any());
    }

    @Test
    void enabledDirectAudioBindingRequiresAllPolicyEvidenceBeforePersistence() {
        when(profiles.findById(7L)).thenReturn(Optional.of(profile()));

        assertThatThrownBy(() -> service.saveBinding(
                directAudioForm(true, true,
                        "region/1", "non-training/1", "retention/1", ""), 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DIRECT_AUDIO_POLICY_EVIDENCE_INCOMPLETE");
        verify(bindings, never()).save(any());
    }

    private static BindingForm directAudioForm(
            boolean directAudioInput,
            boolean enabled,
            String region,
            String nonTraining,
            String retention,
            String deletionSla) {
        return new BindingForm(
                PracticeAiPurpose.PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION,
                7L,
                "explicit-audio-model",
                false,
                5_000,
                60_000,
                0,
                8_388_608,
                2_097_152,
                "SPEAKING_DIRECT_AUDIO_EVAL_V1",
                directAudioInput,
                region,
                nonTraining,
                retention,
                deletionSla,
                enabled,
                null);
    }

    private static PracticeAiProviderProfile profile() {
        return new PracticeAiProviderProfile(
                "PRACTICE_PRIMARY",
                "Primary",
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                "https://provider.invalid/v1",
                "AIM5_REALISTIC_TEST_SECRET",
                true,
                9L);
    }
}
