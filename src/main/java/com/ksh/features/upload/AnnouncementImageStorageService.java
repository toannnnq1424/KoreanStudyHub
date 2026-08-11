package com.ksh.features.upload;

import com.ksh.common.HtmlSanitizer;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StorageTransactionLifecycle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_EMPTY;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_INVALID;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_STAGED_INVALID;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_TOO_LARGE;
import static com.ksh.common.IConstant.MSG_ANNOUNCEMENT_IMAGE_TYPE;

/**
 * Stores images embedded in class announcement HTML via {@link ObjectStorage}.
 * Returns public relative URLs under {@code /uploads/announcements/}.
 *
 * <p>Mirrors {@code ExamImageStorageService}'s staged/claim lifecycle rather
 * than parameterizing it: that service hardcodes {@code exams/} in four
 * constants and backs a shipped feature, so adding a prefix parameter would
 * change every call site of working code for the benefit of a new one
 * (design D8). The duplication is deliberate and each copy carries its own
 * tests, so divergence surfaces as a test failure rather than silent breakage.
 *
 * <p><b>The durable key is exactly two path segments</b> —
 * {@code announcements/{uuid}.{ext}}. {@code PublicUploadsController} splits the
 * request path on {@code /} and 404s anything that is not folder/filename, so
 * nesting by owner would pass every upload test and fail only at retrieval.
 */
@Service
public class AnnouncementImageStorageService {

    private static final Logger log =
            LoggerFactory.getLogger(AnnouncementImageStorageService.class);
    private static final long MAX_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final String KEY_PREFIX = "announcements/";
    private static final String STAGED_KEY_PREFIX = KEY_PREFIX + "staged-";
    private static final String PUBLIC_PREFIX = "/uploads/announcements/";
    private static final String STAGED_PUBLIC_PREFIX = PUBLIC_PREFIX + "staged-";
    private static final Duration STAGED_TTL = Duration.ofHours(24);
    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(5);
    private static final Pattern STAGED_KEY_PATTERN = Pattern.compile(
            "^announcements/staged-(\\d+)-(\\d+)-"
                    + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.(jpg|png|webp)$");
    private static final Pattern STAGED_URL_PATTERN = Pattern.compile(
            "^/uploads/announcements/staged-(\\d+)-(\\d+)-"
                    + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.(jpg|png|webp)$");

    private final ObjectStorage objectStorage;
    private final Clock clock;
    private final Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();

    @Autowired
    public AnnouncementImageStorageService(ObjectStorage objectStorage) {
        this(objectStorage, Clock.systemUTC());
    }

    AnnouncementImageStorageService(ObjectStorage objectStorage, Clock clock) {
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    /**
     * Stores an owner-bound staged image and returns its temporary public URL.
     * The staged object is not a durable announcement asset until a
     * transactional announcement save claims it through {@link #beginClaim(Long)}.
     *
     * @param ownerId id of the uploading user; embedded in the staged key so
     *                only that user can later claim the object
     * @param file    the uploaded multipart image
     * @return the staged public URL under {@code /uploads/announcements/}
     * @throws IllegalArgumentException when the file is empty, too large, of a
     *                                  disallowed type, or fails the magic-byte check
     * @throws IOException              when the object store rejects the write
     */
    public String store(Long ownerId, MultipartFile file) throws IOException {
        requireOwner(ownerId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_IMAGE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_IMAGE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_IMAGE_TYPE);
        }
        // Content type is client-supplied, so the bytes decide: a text file
        // renamed .png and declared image/png must not reach storage.
        if (!isValidImageContent(file)) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_IMAGE_INVALID);
        }
        String ext = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
        String filename = "staged-" + ownerId + "-" + clock.millis()
                + "-" + UUID.randomUUID() + ext;
        String key = KEY_PREFIX + filename;
        try (InputStream in = file.getInputStream()) {
            objectStorage.put(key, in, contentType, file.getSize());
        }
        log.debug("Stored staged announcement image {}", key);
        return PUBLIC_PREFIX + filename;
    }

    /**
     * Starts one transaction-bound claim session. Reusing the returned session
     * across a single announcement body deduplicates repeated staged URLs.
     *
     * @param ownerId id of the user whose staged images may be claimed
     * @return a claim session scoped to the current transaction
     * @throws IllegalStateException when no synchronized transaction is active,
     *                               because rollback and commit hooks could not
     *                               otherwise be registered
     */
    public ClaimSession beginClaim(Long ownerId) {
        requireOwner(ownerId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Announcement image claims require an active synchronized transaction");
        }
        return new ClaimSession(ownerId, clock.instant());
    }

    /**
     * Periodically removes abandoned staged uploads. Inline defaults keep the
     * lifecycle active without requiring environment-specific configuration.
     */
    @Scheduled(
            initialDelayString = "${app.announcement-image.cleanup.initial-delay:PT5M}",
            fixedDelayString = "${app.announcement-image.cleanup.fixed-delay:PT1H}")
    public void cleanupExpiredStagedImages() {
        retryPendingDeletes();
        Instant cutoff = clock.instant().minus(STAGED_TTL);
        List<String> keys;
        try {
            keys = objectStorage.listKeys(STAGED_KEY_PREFIX);
        } catch (IOException | RuntimeException ex) {
            log.error("Failed to list staged announcement images for cleanup", ex);
            return;
        }
        for (String key : keys) {
            Matcher matcher = STAGED_KEY_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            try {
                Instant createdAt = Instant.ofEpochMilli(Long.parseLong(matcher.group(2)));
                if (createdAt.isBefore(cutoff)) {
                    deleteAndVerify(key);
                    log.debug("Deleted expired staged announcement image {}", key);
                }
            } catch (IOException | RuntimeException ex) {
                pendingDeletes.add(key);
                log.warn("Failed to delete expired staged announcement image {}: {}",
                        key, ex.getMessage());
            }
        }
    }

    private boolean isValidImageContent(MultipartFile file) throws IOException {
        byte[] header;
        try (var in = file.getInputStream()) {
            header = in.readNBytes(12);
        }
        if (header.length < 4) return false;
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) return true;
        if (header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') return true;
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return true;
        }
        return false;
    }

    private static void requireOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException(MSG_ANNOUNCEMENT_IMAGE_STAGED_INVALID);
        }
    }

    private void retryPendingDeletes() {
        for (String key : List.copyOf(pendingDeletes)) {
            try {
                deleteAndVerify(key);
                log.info("Retried announcement-image cleanup successfully for {}", key);
            } catch (IOException | RuntimeException ex) {
                log.warn("Announcement-image cleanup retry still failed for {}: {}",
                        key, ex.getMessage());
            }
        }
    }

    private void deleteAndVerify(String key) throws IOException {
        objectStorage.delete(key);
        if (objectStorage.exists(key)) {
            throw new IOException("Object still exists after delete: " + key);
        }
        pendingDeletes.remove(key);
    }

    private void deleteUnchecked(String key) {
        try {
            deleteAndVerify(key);
        } catch (IOException ex) {
            pendingDeletes.add(key);
            throw new UncheckedIOException(ex);
        } catch (RuntimeException ex) {
            pendingDeletes.add(key);
            throw ex;
        }
    }

    /**
     * Claims staged URLs for exactly one authenticated owner and transaction.
     * Each claim is copied to a fresh durable key; rollback deletes only that
     * transaction's copies, while commit deletes the staged source.
     */
    public final class ClaimSession {

        private final Long ownerId;
        private final Instant now;
        private final Map<String, String> claimedUrls = new HashMap<>();

        private ClaimSession(Long ownerId, Instant now) {
            this.ownerId = ownerId;
            this.now = now;
        }

        /**
         * Sanitizes an HTML fragment, then claims only canonical staged URLs
         * used as relative {@code img[src]} values. A foreign-host, encoded,
         * non-image, or otherwise residual staged reference is rejected.
         *
         * <p>Sanitization happens here as the first step, so callers must pass
         * the <b>raw</b> body and run their emptiness and length checks on the
         * returned value — sanitizing beforehand would sanitize twice.
         *
         * @param html raw rich-text body straight from the compose form
         * @return the sanitized body with every staged URL rewritten to durable
         * @throws IllegalArgumentException when a staged reference is not
         *                                  claimable by this owner or has expired
         */
        public String claimIn(String html) {
            if (html == null || html.isBlank()) {
                return html;
            }

            String sanitized = HtmlSanitizer.sanitize(html);
            if (!containsStagedNamespace(sanitized)) {
                return sanitized;
            }

            Document document = Jsoup.parseBodyFragment(
                    sanitized, "https://ksh.local");
            document.outputSettings().prettyPrint(false);
            for (Element image : document.select("img[src]")) {
                String source = image.attr("src");
                if (!containsStagedNamespace(source)) {
                    continue;
                }
                String decodedSource = decodePercentRepeatedly(source);
                if (!source.equals(decodedSource)
                        || !STAGED_URL_PATTERN.matcher(source).matches()) {
                    throw invalidStagedImage();
                }
                image.attr("src", claimedUrls.computeIfAbsent(
                        source, this::claimOne));
            }

            String result = document.body().html();
            if (containsStagedNamespace(result)) {
                throw invalidStagedImage();
            }
            return result;
        }

        private String claimOne(String stagedUrl) {
            Matcher matcher = STAGED_URL_PATTERN.matcher(stagedUrl);
            if (!matcher.matches()) {
                throw invalidStagedImage();
            }
            Long stagedOwner;
            long createdAtMillis;
            try {
                stagedOwner = Long.valueOf(matcher.group(1));
                createdAtMillis = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException ex) {
                throw invalidStagedImage();
            }
            // Staged images are owner-bound: user B may not claim user A's upload.
            if (!Objects.equals(ownerId, stagedOwner)) {
                throw invalidStagedImage();
            }
            Instant createdAt = Instant.ofEpochMilli(createdAtMillis);
            if (createdAt.isBefore(now.minus(STAGED_TTL))
                    || createdAt.isAfter(now.plus(CLOCK_SKEW_TOLERANCE))) {
                throw invalidStagedImage();
            }

            String stagedKey = stagedUrl.substring("/uploads/".length());
            if (!objectStorage.exists(stagedKey)) {
                throw invalidStagedImage();
            }
            String extension = matcher.group(4).toLowerCase(Locale.ROOT);
            String durableKey = KEY_PREFIX + UUID.randomUUID() + "." + extension;
            try {
                objectStorage.copy(stagedKey, durableKey);
            } catch (IOException ex) {
                throw new UncheckedIOException(
                        "Failed to claim staged announcement image", ex);
            }
            try {
                StorageTransactionLifecycle.deleteOnRollback(
                        () -> deleteUnchecked(durableKey));
                StorageTransactionLifecycle.deleteAfterCommit(
                        () -> deleteUnchecked(stagedKey));
            } catch (RuntimeException registrationFailure) {
                // Do not leak a durable copy if transaction synchronization
                // unexpectedly disappears between beginClaim and registration.
                deleteUnchecked(durableKey);
                throw registrationFailure;
            }
            return "/uploads/" + durableKey;
        }

        private IllegalArgumentException invalidStagedImage() {
            return new IllegalArgumentException(MSG_ANNOUNCEMENT_IMAGE_STAGED_INVALID);
        }
    }

    private static boolean containsStagedNamespace(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return decodePercentRepeatedly(value)
                .toLowerCase(Locale.ROOT)
                .contains(STAGED_PUBLIC_PREFIX);
    }

    /**
     * Decodes ASCII percent escapes without URLDecoder's form-specific '+'
     * conversion. Repeating catches double-encoded staged paths while leaving
     * malformed escapes intact for the residual-reference check.
     */
    private static String decodePercentRepeatedly(String value) {
        String decoded = value;
        for (int round = 0; round < 3; round++) {
            String next = decodePercentOnce(decoded);
            if (next.equals(decoded)) {
                break;
            }
            decoded = next;
        }
        return decoded;
    }

    private static String decodePercentOnce(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '%' && i + 2 < value.length()) {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    decoded.append((char) ((high << 4) + low));
                    i += 2;
                    continue;
                }
            }
            decoded.append(current);
        }
        return decoded.toString();
    }
}