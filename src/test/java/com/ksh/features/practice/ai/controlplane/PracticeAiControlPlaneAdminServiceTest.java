package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileForm;
import com.ksh.features.admin.settings.service.PracticeAiControlPlaneAdminService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.MASKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
