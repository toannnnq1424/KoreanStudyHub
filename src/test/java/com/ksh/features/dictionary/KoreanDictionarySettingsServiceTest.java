package com.ksh.features.dictionary;

import com.ksh.features.admin.settings.SystemSettingGroups;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoreanDictionarySettingsServiceTest {
    @Mock
    private SystemSettingsRepository repository;

    @InjectMocks
    private KoreanDictionarySettingsService service;

    @Test
    void resolvesKeyAndEndpointFromTheSharedDictionaryGroupOnly() {
        when(repository.loadGroupAsMap(SystemSettingGroups.DICTIONARY)).thenReturn(Map.of(
                KoreanDictionarySettingsService.API_KEY, "0123456789abcdef0123456789abcdef",
                KoreanDictionarySettingsService.BASE_URL, "https://krdict.korean.go.kr/api/search"));

        assertThat(service.apiKey("legacy-value")).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(service.baseUrl("https://elsewhere.example/api"))
                .isEqualTo("https://krdict.korean.go.kr/api/search");

        verify(repository, times(2)).loadGroupAsMap(SystemSettingGroups.DICTIONARY);
        verifyNoMoreInteractions(repository);
    }
}
