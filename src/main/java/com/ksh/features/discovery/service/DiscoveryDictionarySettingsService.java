package com.ksh.features.discovery.service;

import com.ksh.config.CacheConfig;
import com.ksh.entities.SystemSetting;
import com.ksh.features.admin.settings.SystemSettingGroups;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DiscoveryDictionarySettingsService {

    public static final String API_KEY = "app.news.dictionary.api-key";
    private static final String GROUP = SystemSettingGroups.DISCOVERY;

    private final SystemSettingsRepository repository;

    public DiscoveryDictionarySettingsService(SystemSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String apiKey(String environmentFallback) {
        Map<String, String> settings = repository.loadGroupAsMap(GROUP);
        String stored = settings.get(API_KEY);
        return stored == null || stored.isBlank()
                ? normalize(environmentFallback)
                : stored.trim();
    }

    @Transactional(readOnly = true)
    public boolean hasStoredApiKey() {
        String value = repository.loadGroupAsMap(GROUP).get(API_KEY);
        return value != null && !value.isBlank();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_SETTINGS_GROUP, key = "'" + GROUP + "'")
    public void saveApiKey(String apiKey, Long currentUserId) {
        String normalized = normalize(apiKey);
        if (!normalized.isBlank() && !normalized.matches("(?i)[0-9a-f]{32}")) {
            throw new IllegalArgumentException("API key KRDICT phải gồm đúng 32 ký tự hex.");
        }

        SystemSetting setting = repository.findBySettingKey(API_KEY)
                .orElseGet(() -> new SystemSetting(API_KEY, "", GROUP));
        setting.setSettingValue(normalized);
        setting.setEncrypted(true);
        setting.setDescription("Korean Basic Dictionary Open API key (masked in admin UI)");
        setting.setUpdatedBy(currentUserId);
        repository.save(setting);
    }

    public String maskedApiKey(String environmentFallback) {
        String key = apiKey(environmentFallback);
        if (key.isBlank()) {
            return "";
        }
        return "••••••••••••••••••••••••••••" + key.substring(Math.max(0, key.length() - 4));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
