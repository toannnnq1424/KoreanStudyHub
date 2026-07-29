package com.ksh.features.admin.settings.service;

import com.ksh.entities.AiProvider;
import com.ksh.entities.SystemSetting;
import com.ksh.features.admin.settings.dto.AiSettingsDtos.AiProviderForm;
import com.ksh.features.admin.settings.repository.AiProviderRepository;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import com.ksh.features.ai.client.AiClient;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract tests for serializing the global AI-provider fallback order.
 *
 * <p>These tests deliberately cover the empty-provider-table case: locking an
 * {@code ai_providers} row cannot protect the first insert because no such row
 * exists yet.
 */
@ExtendWith(MockitoExtension.class)
class AiProviderOrderingConcurrencyContractTest {

    private static final String ORDER_LOCK_SETTING_KEY = "ai.provider";

    @Mock
    private AiProviderRepository providerRepository;

    @Mock
    private SystemSettingsRepository systemSettingsRepository;

    @Mock
    private AiClient aiClient;

    @Test
    void orderingAnchorUsesAPessimisticDatabaseLock() throws Exception {
        Method lockMethod = SystemSettingsRepository.class.getMethod(
                "findBySettingKeyForUpdate", String.class);

        Lock lock = lockMethod.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void firstCreateLocksStableAnchorBeforeEmptyTableMaxReadAndInsert() {
        SystemSetting anchor = new SystemSetting(
                ORDER_LOCK_SETTING_KEY, "", "AI");
        when(systemSettingsRepository.findBySettingKeyForUpdate(ORDER_LOCK_SETTING_KEY))
                .thenReturn(Optional.of(anchor));
        when(providerRepository.findMaxDisplayOrder()).thenReturn(Optional.empty());
        AiProviderService service = new AiProviderService(
                providerRepository, systemSettingsRepository, aiClient);
        AiProviderForm form = new AiProviderForm(
                null, "Primary", "https://example.test/v1", "model", "secret", true);
        ArgumentCaptor<AiProvider> savedProvider = ArgumentCaptor.forClass(AiProvider.class);

        service.create(form, 42L);

        InOrder lockOrder = inOrder(systemSettingsRepository, providerRepository);
        lockOrder.verify(systemSettingsRepository)
                .findBySettingKeyForUpdate(ORDER_LOCK_SETTING_KEY);
        lockOrder.verify(providerRepository).findMaxDisplayOrder();
        lockOrder.verify(providerRepository).save(savedProvider.capture());
        assertThat(savedProvider.getValue().getDisplayOrder()).isEqualTo((short) 1);
    }

    @Test
    void createFailsClosedBeforeOrderingReadWhenAnchorIsMissing() {
        when(systemSettingsRepository.findBySettingKeyForUpdate(ORDER_LOCK_SETTING_KEY))
                .thenReturn(Optional.empty());
        AiProviderService service = new AiProviderService(
                providerRepository, systemSettingsRepository, aiClient);
        AiProviderForm form = new AiProviderForm(
                null, "Primary", "https://example.test/v1", "model", "secret", true);

        assertThatThrownBy(() -> service.create(form, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_LOCK_SETTING_KEY);
        verifyNoInteractions(providerRepository);
    }
}
