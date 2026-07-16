package com.ksh.common.storage;

/**
 * Skeleton for Cloudflare R2 (or any S3-compatible) audio storage.
 *
 * <h2>Hướng dẫn tích hợp cho developer</h2>
 *
 * <h3>Bước 1 — Thêm dependency vào pom.xml</h3>
 * <pre>{@code
 * <dependency>
 *   <groupId>software.amazon.awssdk</groupId>
 *   <artifactId>s3</artifactId>
 *   <version>2.25.x</version>  <!-- check Maven Central for latest -->
 * </dependency>
 * }</pre>
 *
 * <h3>Bước 2 — Cấu hình (application-local.properties hoặc env vars)</h3>
 * <pre>
 * storage.provider=r2
 * storage.r2.account-id=${STORAGE_R2_ACCOUNT_ID}
 * storage.r2.access-key=${STORAGE_R2_ACCESS_KEY}
 * storage.r2.secret-key=${STORAGE_R2_SECRET_KEY}
 * storage.r2.bucket=${STORAGE_R2_BUCKET:ksh-audio}
 * # Public domain (set up Cloudflare R2 custom domain or use r2.dev subdomain)
 * storage.r2.public-domain=${STORAGE_R2_PUBLIC_DOMAIN:https://audio.ksh.edu.vn}
 * </pre>
 *
 * <h3>Bước 3 — Uncomment và hoàn thiện class này</h3>
 * <ul>
 *   <li>Uncomment các annotation {@code @Service}, {@code @Primary}, {@code @ConditionalOnProperty}</li>
 *   <li>Inject {@link CloudStorageProperties} và build S3Client trỏ đến R2 endpoint</li>
 *   <li>Implement {@link #resolveUrl}: return {@code publicDomain + "/" + audioKey}</li>
 *   <li>Implement {@link #upload}: dùng S3Client.putObject() để upload file</li>
 * </ul>
 *
 * <h3>Security notes</h3>
 * <ul>
 *   <li>Audio practice content thường là public (không cần signed URL)</li>
 *   <li>Nếu cần bảo vệ nội dung premium: dùng Cloudflare signed URLs
 *       (thêm {@code CF-Access-Client-Id} header hoặc Cloudflare Access policy)</li>
 *   <li>KHÔNG bao giờ expose {@code access-key} và {@code secret-key} ra client</li>
 * </ul>
 *
 * <h3>R2 Endpoint format</h3>
 * <pre>
 * https://{ACCOUNT_ID}.r2.cloudflarestorage.com
 * </pre>
 *
 * <p>Sau khi implement xong, {@link LocalAudioStorageService} sẽ tự động bị deactivate
 * nhờ {@code @ConditionalOnMissingBean(name = "cloudStorageService")}.
 */
// Uncomment toàn bộ khi ready:
// @Service("cloudStorageService")
// @Primary
// @ConditionalOnProperty(name = "storage.provider", havingValue = "r2")
public class CloudflareR2StorageService implements AudioStorageService {

    // TODO: inject CloudStorageProperties

    /**
     * Resolve audio key to public CDN URL.
     *
     * <p>Example: {@code "practice/set-1/audio.mp3"}
     *   → {@code "https://audio.ksh.edu.vn/practice/set-1/audio.mp3"}
     */
    @Override
    public String resolveUrl(String audioKey) {
        if (audioKey == null || audioKey.isBlank()) return null;
        // TODO: return properties.publicDomain() + "/" + audioKey;
        throw new UnsupportedOperationException(
            "CloudflareR2StorageService chưa được implement. " +
            "Xem Javadoc trong class này để biết hướng dẫn tích hợp."
        );
    }

    /**
     * Upload audio file to R2 bucket.
     *
     * @param fileBytes  raw bytes of the audio file
     * @param audioKey   logical key to store under (e.g. {@code practice/set-1/audio.mp3})
     * @param mimeType   MIME type (e.g. {@code audio/mpeg}, {@code audio/mp4})
     * @return the audioKey (same as input) — store this in DB
     */
    public String upload(byte[] fileBytes, String audioKey, String mimeType) {
        // TODO:
        // S3Client s3 = S3Client.builder()
        //     .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
        //     .credentialsProvider(StaticCredentialsProvider.create(
        //         AwsBasicCredentials.create(accessKey, secretKey)))
        //     .region(Region.of("auto"))
        //     .build();
        //
        // s3.putObject(
        //     PutObjectRequest.builder()
        //         .bucket(bucket)
        //         .key(audioKey)
        //         .contentType(mimeType)
        //         .build(),
        //     RequestBody.fromBytes(fileBytes)
        // );
        // return audioKey;
        throw new UnsupportedOperationException("Chưa implement upload R2.");
    }
}
