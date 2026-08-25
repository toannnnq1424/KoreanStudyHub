package com.ksh.features.upload;

import com.ksh.features.storage.LocalObjectStorage;
import com.ksh.features.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link AnnouncementImageStorageService}'s scheduled sweeper.
 *
 * <p>Exists because {@code ExamImageStorageServiceTest} covers a <b>different
 * class</b>: the two services duplicate the staged/claim lifecycle deliberately
 * (design D8), so exam coverage says nothing about the announcement copy. Before
 * this class, {@code cleanupExpiredStagedImages} had no test at all and the spec
 * scenario "Abandoned upload never becomes durable" was unmapped.
 *
 * <p>Driven through the package-private {@code (ObjectStorage, Clock)}
 * constructor with a fixed clock, mirroring the exam test. Age is encoded in the
 * staged key at {@code store} time, so an "old" object is produced by storing
 * through a service whose clock is in the past — no filesystem timestamp
 * manipulation is involved and the assertion targets the real key parsing.
 */
class AnnouncementImageStorageServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    /** STAGED_TTL is 24h, so 25h back is expired and 23h back is not. */
    private static final Duration PAST_TTL = Duration.ofHours(25);
    private static final Duration WITHIN_TTL = Duration.ofHours(23);

    @TempDir
    Path storageRoot;

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /**
     * The abandoned-upload scenario. Both halves matter: an expired object is
     * swept, and a fresh one is not. A sweeper that deleted everything under the
     * staged prefix would satisfy the first assertion alone while destroying
     * every in-flight upload, so the survival assertion is what pins the cutoff.
     */
    @Test
    void cleanup_removesExpiredStagedImagesAndSparesFreshOnes() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        String expired = service(storage, NOW.minus(PAST_TTL)).store(7L, png());
        String fresh = service(storage, NOW.minus(WITHIN_TTL)).store(7L, png());

        service(storage, NOW).cleanupExpiredStagedImages();

        assertThat(storage.exists(keyFor(expired)))
                .as("a staged object older than the 24h window must be swept")
                .isFalse();
        assertThat(storage.exists(keyFor(fresh)))
                .as("a staged object still inside the window must survive")
                .isTrue();
    }

    /**
     * The other half of "never becomes durable": sweeping an abandoned upload
     * must not manufacture a durable copy. Nothing may exist under the
     * announcements prefix that is not itself a staged key.
     */
    @Test
    void cleanup_neverPromotesAnAbandonedUploadToADurableKey() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        service(storage, NOW.minus(PAST_TTL)).store(7L, png());

        service(storage, NOW).cleanupExpiredStagedImages();

        assertThat(storage.listKeys("announcements/"))
                .as("the sweeper must delete, never promote")
                .allMatch(key -> key.startsWith("announcements/staged-"));
    }

    private static AnnouncementImageStorageService service(
            ObjectStorage storage, Instant instant) {
        return new AnnouncementImageStorageService(
                storage, Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** Minimal bytes that satisfy the PNG magic-byte check. */
    private static MockMultipartFile png() {
        return new MockMultipartFile(
                "file", "board.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    }

    private static String keyFor(String publicUrl) {
        return publicUrl.substring("/uploads/".length());
    }
}