package com.ksh.features.discovery.service;

import com.ksh.features.dictionary.KoreanDictionarySettingsService;
import org.springframework.stereotype.Service;

/** @deprecated Use the platform-wide KoreanDictionarySettingsService. */
@Deprecated
@Service
public class DiscoveryDictionarySettingsService {
    public static final String API_KEY = KoreanDictionarySettingsService.API_KEY;
    private final KoreanDictionarySettingsService delegate;

    public DiscoveryDictionarySettingsService(KoreanDictionarySettingsService delegate) {
        this.delegate = delegate;
    }

    public String apiKey(String environmentFallback) {
        return delegate.apiKey(environmentFallback);
    }

    public boolean hasStoredApiKey() {
        return delegate.hasStoredApiKey();
    }

    public void saveApiKey(String apiKey, Long currentUserId) {
        delegate.save(apiKey, null, currentUserId);
    }

    public String maskedApiKey(String environmentFallback) {
        return delegate.maskedApiKey(environmentFallback);
    }
}
