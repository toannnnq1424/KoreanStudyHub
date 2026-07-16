package com.ksh.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Default (local) audio storage implementation.
 *
 * <p>Serves audio files from the local filesystem (configurable via {@code storage.local.base-url}).
 * Active only when no other {@link AudioStorageService} bean is registered (e.g. CloudflareR2StorageService).
 *
 * <p><b>Configuration</b> (application.properties / environment):
 * <pre>
 * # Base URL prefix for local audio files served by Spring static resources
 * storage.local.base-url=/audio
 * # Optionally point to an absolute URL for a local dev CDN or nginx proxy
 * # storage.local.base-url=http://localhost:9000/audio
 * </pre>
 *
 * <p>Files should be placed at: {@code src/main/resources/static/audio/practice/set-{id}/...}
 * or served via a configured static-resource handler.
 */
@Service
@ConditionalOnMissingBean(name = "cloudStorageService")
public class LocalAudioStorageService implements AudioStorageService {

    private final String baseUrl;

    public LocalAudioStorageService(
            @Value("${storage.local.base-url:/audio}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Maps {@code practice/set-1/audio.mp3} → {@code /audio/practice/set-1/audio.mp3}
     */
    @Override
    public String resolveUrl(String audioKey) {
        if (audioKey == null || audioKey.isBlank()) return null;
        String key = audioKey.startsWith("/") ? audioKey : "/" + audioKey;
        return baseUrl + key;
    }
}
