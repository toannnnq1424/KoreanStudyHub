package com.ksh.features.admin.settings.service;

import com.ksh.entities.AiProvider;
import com.ksh.features.admin.settings.repository.AiProviderRepository;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProviderConnectivityBudgetTest {

    @Test
    void connectionTestUsesReasoningSafeOutputBudget() {
        AiProviderRepository repository = mock(AiProviderRepository.class);
        AiClient aiClient = mock(AiClient.class);
        AiProvider provider = new AiProvider(
                "Reasoning provider", "https://example.test/v1", "reasoning-model", "secret");
        when(repository.findById(7L)).thenReturn(Optional.of(provider));
        when(aiClient.callOne(provider, "ping", AiProviderService.PING_MAX_TOKENS,
                AiRequestLogger.SOURCE_TEST_CONNECTION, 42L)).thenReturn("pong");
        AiProviderService service = new AiProviderService(
                repository, mock(SystemSettingsRepository.class), aiClient);

        assertThat(service.test(7L, 42L).ok()).isTrue();
        assertThat(AiProviderService.PING_MAX_TOKENS).isEqualTo(2048);
        verify(aiClient).callOne(provider, "ping", 2048,
                AiRequestLogger.SOURCE_TEST_CONNECTION, 42L);
    }
}
