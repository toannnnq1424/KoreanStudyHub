package com.ksh.common.storage;

/**
 * Port (interface) for audio/media storage.
 *
 * <p>Design rules:
 * <ul>
 *   <li>The stored value in DB is always a <b>logical key</b> (e.g. {@code practice/set-12/audio.mp3}),
 *       never a full URL. This keeps the DB clean and provider-agnostic.</li>
 *   <li>Call {@link #resolveUrl(String)} at render time to convert key → playable URL.</li>
 *   <li>To plug in a new provider (Cloudflare R2, AWS S3, Cloudinary…), implement this
 *       interface and annotate with {@code @Primary} to override the default local impl.</li>
 * </ul>
 *
 * <h3>For the developer integrating Cloudflare R2 / AWS S3:</h3>
 * <ol>
 *   <li>Add the AWS S3 SDK dependency (R2 is S3-compatible):
 *       {@code software.amazon.awssdk:s3}</li>
 *   <li>Create {@code CloudflareR2StorageService implements AudioStorageService}</li>
 *   <li>Annotate it with {@code @Service @Primary @ConditionalOnProperty(name="storage.provider", havingValue="r2")}</li>
 *   <li>Set env vars: {@code STORAGE_R2_ACCOUNT_ID}, {@code STORAGE_R2_ACCESS_KEY},
 *       {@code STORAGE_R2_SECRET_KEY}, {@code STORAGE_R2_BUCKET}, {@code STORAGE_R2_PUBLIC_DOMAIN}</li>
 *   <li>Done — no other code changes needed.</li>
 * </ol>
 */
public interface AudioStorageService {

    /**
     * Convert a stored logical audio key to a playable URL.
     *
     * <p>Examples:
     * <ul>
     *   <li>Local dev: {@code "practice/set-1/audio.mp3"} → {@code "/audio/practice/set-1/audio.mp3"}</li>
     *   <li>Cloudflare R2: {@code "practice/set-1/audio.mp3"} → {@code "https://cdn.ksh.edu.vn/practice/set-1/audio.mp3"}</li>
     * </ul>
     *
     * @param audioKey logical key as stored in DB; may be {@code null} or blank
     * @return a playable URL, or {@code null} if key is blank
     */
    String resolveUrl(String audioKey);

    /**
     * Check whether a given string is already a full URL (starts with http/https).
     * Useful for backward compat when old data has full URLs instead of keys.
     */
    static boolean isFullUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    /**
     * Normalize stored value: if it is already a full URL, return as-is (backward compat).
     * Otherwise delegate to {@link #resolveUrl(String)}.
     */
    default String resolveUrlSafe(String audioKeyOrUrl) {
        if (isFullUrl(audioKeyOrUrl)) return audioKeyOrUrl;
        return resolveUrl(audioKeyOrUrl);
    }
}
