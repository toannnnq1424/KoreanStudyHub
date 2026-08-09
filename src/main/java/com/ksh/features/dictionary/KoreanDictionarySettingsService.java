package com.ksh.features.dictionary;

import com.ksh.config.CacheConfig;
import com.ksh.entities.SystemSetting;
import com.ksh.features.admin.settings.SystemSettingGroups;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class KoreanDictionarySettingsService {
    public static final String API_KEY = "dictionary.krdict.api-key";
    public static final String BASE_URL = "dictionary.krdict.base-url";
    private static final String DEFAULT_BASE_URL = "https://krdict.korean.go.kr/api/search";
    private static final String GROUP = SystemSettingGroups.DICTIONARY;
    private final SystemSettingsRepository repository;

    public KoreanDictionarySettingsService(SystemSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String apiKey(String environmentFallback) {
        Map<String, String> settings = repository.loadGroupAsMap(GROUP);
        String stored = settings.get(API_KEY);
        return stored == null || stored.isBlank() ? normalize(environmentFallback) : stored.trim();
    }

    @Transactional(readOnly = true)
    public String baseUrl(String environmentFallback) {
        String stored = repository.loadGroupAsMap(GROUP).get(BASE_URL);
        if (stored != null && !stored.isBlank()) return stored.trim();
        String fallback = normalize(environmentFallback);
        return fallback.isBlank() ? DEFAULT_BASE_URL : fallback;
    }

    @Transactional(readOnly = true)
    public boolean hasStoredApiKey() {
        return !apiKey("").isBlank();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_SETTINGS_GROUP, allEntries = true)
    public void save(String apiKey, String baseUrl, Long currentUserId) {
        String key = normalize(apiKey);
        if (!key.isBlank() && !isMasked(key) && !key.matches("(?i)[0-9a-f]{32}")) {
            throw new IllegalArgumentException("API key KRDICT phải gồm đúng 32 ký tự hex.");
        }
        String endpoint = normalize(baseUrl);
        if (endpoint.isBlank()) endpoint = DEFAULT_BASE_URL;
        if (!endpoint.startsWith("https://krdict.korean.go.kr/")) {
            throw new IllegalArgumentException("Base URL phải thuộc krdict.korean.go.kr.");
        }
        if (!key.isBlank() && !isMasked(key)) {
            upsert(API_KEY, key, true, "Korean Basic Dictionary Open API key", currentUserId);
        }
        upsert(BASE_URL, endpoint, false, "Korean Basic Dictionary Open API endpoint", currentUserId);
    }

    public String maskedApiKey(String environmentFallback) {
        String key = apiKey(environmentFallback);
        return key.isBlank() ? "" : "****************************" + key.substring(Math.max(0, key.length() - 4));
    }

    private void upsert(String key, String value, boolean encrypted, String description, Long userId) {
        SystemSetting setting = repository.findBySettingKey(key)
                .orElseGet(() -> new SystemSetting(key, "", GROUP));
        setting.setSettingValue(value);
        setting.setEncrypted(encrypted);
        setting.setDescription(description);
        setting.setUpdatedBy(userId);
        repository.save(setting);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isMasked(String value) {
        return value.matches("\\*{8,}[0-9a-fA-F]{0,4}");
    }
}
