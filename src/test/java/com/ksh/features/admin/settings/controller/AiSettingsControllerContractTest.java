package com.ksh.features.admin.settings.controller;

import com.ksh.features.admin.settings.service.AiLogQueryService;
import com.ksh.features.admin.settings.service.AiProviderService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSettingsControllerContractTest {

    @Test
    void revealKeyDisablesCachingForSecretPayload() {
        AiProviderService service = mock(AiProviderService.class);
        when(service.revealKey(7L)).thenReturn(Optional.of("secret-value"));

        var response = new AiSettingsController(service, mock(AiLogQueryService.class))
                .revealKey(7L);

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ok()).isTrue();
        assertThat(response.getBody().apiKey()).isEqualTo("secret-value");
    }
}
