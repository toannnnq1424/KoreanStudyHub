package com.ksh.features.admin.settings.service;

import com.ksh.entities.SystemSetting;
import com.ksh.features.admin.settings.SystemSettingGroups;
import com.ksh.features.admin.settings.dto.OauthSettingsDtos;
import com.ksh.features.admin.settings.dto.OauthSettingsDtos.OauthSettingsForm;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OauthSettingsServiceTest {

    private static final String STORED_SECRET = "stored-oauth-secret-sentinel";

    @Mock
    private SystemSettingsRepository repository;

    @Test
    void loadNeverReturnsTheStoredClientSecret() {
        when(repository.loadGroupAsMap(SystemSettingGroups.OAUTH)).thenReturn(Map.of(
                OauthSettingsService.KEY_GOOGLE_CLIENT_ID, "client-id",
                OauthSettingsService.KEY_GOOGLE_CLIENT_SECRET, STORED_SECRET,
                OauthSettingsService.KEY_GOOGLE_SCOPE, "openid,email"));

        OauthSettingsForm form = new OauthSettingsService(repository).load();

        assertThat(form.googleClientId()).isEqualTo("client-id");
        assertThat(form.googleScope()).isEqualTo("openid,email");
        assertThat(form.googleClientSecret()).isEmpty();
        assertThat(form.toString()).doesNotContain(STORED_SECRET);
    }

    @Test
    void blankSubmittedSecretKeepsTheStoredCredential() {
        assertKeepsStoredSecret("   ");
    }

    @Test
    void maskedSentinelKeepsTheStoredCredential() {
        assertKeepsStoredSecret("  " + OauthSettingsDtos.MASKED + "  ");
    }

    private void assertKeepsStoredSecret(String submittedSecret) {
        SystemSetting storedSecret = new SystemSetting(
                OauthSettingsService.KEY_GOOGLE_CLIENT_SECRET,
                STORED_SECRET,
                SystemSettingGroups.OAUTH);
        when(repository.findBySettingGroup(SystemSettingGroups.OAUTH))
                .thenReturn(List.of(storedSecret));

        new OauthSettingsService(repository).save(
                new OauthSettingsForm("client-id", submittedSecret, "openid,email"),
                7L);

        assertThat(storedSecret.getSettingValue()).isEqualTo(STORED_SECRET);
        verify(repository, never()).save(same(storedSecret));
    }
}
