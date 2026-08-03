package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.ProfileForm;
import com.ksh.features.admin.settings.service.PracticeAiControlPlaneAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.MASKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeAiFixedProviderPresetTest {

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
    void registryPinsOnlyTheTwoRequestedProviderIdentitiesWithoutModels() {
        assertThat(PracticeAiFixedProviderPresetRegistry.all())
                .extracting(
                        PracticeAiFixedProviderPresetRegistry.Preset::key,
                        PracticeAiFixedProviderPresetRegistry.Preset::profileCode,
                        PracticeAiFixedProviderPresetRegistry.Preset::baseUrl,
                        PracticeAiFixedProviderPresetRegistry.Preset::keyConsoleUrl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "XAI_GROK",
                                "PRACTICE_XAI_GROK",
                                "https://api.x.ai/v1",
                                "https://console.x.ai/team/default/api-keys"),
                        org.assertj.core.groups.Tuple.tuple(
                                "GROQ",
                                "PRACTICE_GROQ",
                                "https://api.groq.com/openai/v1",
                                "https://console.groq.com/keys"));
        assertThat(PracticeAiFixedProviderPresetRegistry.Preset.class
                .getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("model", "modelId", "capability");
    }

    @Test
    void creatingPresetPersistsSeparateDisabledSecretlessProfileOnly() {
        when(profiles.findByProfileCode("PRACTICE_XAI_GROK"))
                .thenReturn(Optional.empty());
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createFixedProviderPreset("XAI_GROK", 9L);

        ArgumentCaptor<PracticeAiProviderProfile> captor =
                ArgumentCaptor.forClass(PracticeAiProviderProfile.class);
        verify(profiles).save(captor.capture());
        PracticeAiProviderProfile profile = captor.getValue();
        assertThat(profile.getProfileCode()).isEqualTo("PRACTICE_XAI_GROK");
        assertThat(profile.getBaseUrl()).isEqualTo("https://api.x.ai/v1");
        assertThat(profile.getCredentialMode()).isEqualTo("STATIC_BEARER");
        assertThat(profile.getCredentialSecret()).isNull();
        assertThat(profile.isEnabled()).isFalse();
        verifyNoInteractions(bindings, runs);
    }

    @Test
    void fixedPresetCannotBeEnabledTamperedOrHaveStoredSecretRevealed() {
        PracticeAiProviderProfile profile = new PracticeAiProviderProfile(
                "PRACTICE_GROQ",
                "Groq cho Practice",
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                "https://api.groq.com/openai/v1",
                "GROQ_TEST_SECRET",
                false,
                9L);
        when(profiles.findByIdForUpdate(7L)).thenReturn(Optional.of(profile));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.toggleProfile(7L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED");
        assertThat(profile.isEnabled()).isFalse();
        assertThat(service.revealSecret(7L)).isEmpty();

        assertThatThrownBy(() -> service.saveProfile(new ProfileForm(
                7L,
                0L,
                "PRACTICE_GROQ",
                "Groq cho Practice",
                "OPENAI_COMPATIBLE",
                "STATIC_BEARER",
                "https://attacker.invalid/v1",
                MASKED,
                true), 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PRACTICE_AI_PROVIDER_PRESET_CONTRACT_MISMATCH");
        verify(profiles, never()).save(any());
        verifyNoInteractions(bindings, runs);
    }

    @Test
    void validPresetKeyUpdateRemainsDisabledAndKeepsSecretServerSide() {
        PracticeAiProviderProfile profile = new PracticeAiProviderProfile(
                "PRACTICE_GROQ",
                "Groq cho Practice",
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                "https://api.groq.com/openai/v1",
                "OLD_SERVER_SECRET",
                false,
                9L);
        when(profiles.findByIdForUpdate(7L)).thenReturn(Optional.of(profile));

        service.saveProfile(new ProfileForm(
                7L,
                0L,
                "PRACTICE_GROQ",
                "Groq cho Practice",
                "OPENAI_COMPATIBLE",
                "STATIC_BEARER",
                "https://api.groq.com/openai/v1",
                "NEW_SERVER_SECRET",
                true), 9L);

        assertThat(profile.isEnabled()).isFalse();
        assertThat(profile.getCredentialSecret()).isEqualTo("NEW_SERVER_SECRET");
        verify(profiles).save(profile);
        verifyNoInteractions(bindings, runs);
    }

    @Test
    void unknownPresetFailsBeforeAnyPersistenceOrProviderBoundary() {
        assertThatThrownBy(() -> service.createFixedProviderPreset("UNKNOWN", 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PRACTICE_AI_PROVIDER_PRESET_NOT_ALLOWED");
        verify(profiles, never()).save(any());
        verifyNoInteractions(bindings, runs);
    }

    @Test
    void evenAnAccidentallyEnabledPresetBindingFailsBeforeTransportResolution() {
        PracticeAiProviderProfile profile = new PracticeAiProviderProfile(
                "PRACTICE_XAI_GROK",
                "xAI / Grok cho Practice",
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                "https://api.x.ai/v1",
                "SERVER_SECRET",
                true,
                9L);
        PracticeAiControlPlaneCodec codec = new PracticeAiControlPlaneCodec(
                new ObjectMapper());
        PracticeAiPurpose purpose = PracticeAiPurpose.PRACTICE_WRITING_EVALUATION;
        PracticeAiPurposeBinding binding = new PracticeAiPurposeBinding(
                purpose,
                profile,
                "unverified-model",
                PracticeAiBindingResolver.TRANSPORT_DIALECT,
                codec.capabilityJson(purpose, false),
                codec.limitsJson(5_000, 60_000, 0, 8_388_608, 2_097_152),
                "WRITING_EVALUATION_V1",
                true,
                9L);
        when(bindings.findDetailed(purpose.name())).thenReturn(Optional.of(binding));

        assertThatThrownBy(() -> new PracticeAiBindingResolver(bindings, codec)
                .resolve(purpose))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error)
                        .errorCode())
                .isEqualTo("PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED");
    }

    @Test
    void conflictingExistingPresetIdentityFailsClosed() {
        PracticeAiProviderProfile conflicting = new PracticeAiProviderProfile(
                "PRACTICE_GROQ",
                "Conflicting",
                PracticeAiBindingResolver.PROVIDER_FAMILY,
                "https://attacker.invalid/v1",
                "SECRET",
                true,
                9L);
        when(profiles.findByProfileCode("PRACTICE_GROQ"))
                .thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> service.createFixedProviderPreset("GROQ", 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PRACTICE_AI_PROVIDER_PRESET_STATE_INVALID");
        verify(profiles, never()).save(any());
        verifyNoInteractions(runs);
    }
}
