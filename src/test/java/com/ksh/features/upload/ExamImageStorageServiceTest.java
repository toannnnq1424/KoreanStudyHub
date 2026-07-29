package com.ksh.features.upload;

import com.ksh.features.storage.LocalObjectStorage;
import com.ksh.features.storage.ObjectStorage;
import com.ksh.features.storage.StoredObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ksh.common.IConstant.MSG_EXAM_IMAGE_STAGED_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamImageStorageServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");

    @TempDir
    Path storageRoot;

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void upload_isOwnerBoundAndStagedUntilClaimCommit() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        ExamImageStorageService service = service(storage, NOW);
        String stagedUrl = service.store(41L, png());
        String stagedKey = keyFor(stagedUrl);

        assertThat(stagedUrl).startsWith("/uploads/exams/staged-41-" + NOW.toEpochMilli());
        assertThat(storage.exists(stagedKey)).isTrue();

        beginTransaction();
        var claim = service.beginClaim(41L);
        String rewritten = claim.claimIn("<p><img src=\"" + stagedUrl
                + "\"><img src=\"" + stagedUrl + "\"></p>");

        assertThat(rewritten).doesNotContain("staged-");
        List<String> durableKeys = durableKeys(storage);
        assertThat(durableKeys).hasSize(1);
        assertThat(storage.exists(stagedKey)).isTrue();

        commitTransaction();
        assertThat(storage.exists(stagedKey)).isFalse();
        assertThat(storage.exists(durableKeys.get(0))).isTrue();
    }

    @Test
    void claimRollback_deletesOnlyDurableCopyAndKeepsStagedSource() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        ExamImageStorageService service = service(storage, NOW);
        String stagedUrl = service.store(9L, png());
        String stagedKey = keyFor(stagedUrl);

        beginTransaction();
        service.beginClaim(9L).claimIn("<img src=\"" + stagedUrl + "\">");
        List<String> durableKeys = durableKeys(storage);
        assertThat(durableKeys).hasSize(1);

        rollbackTransaction();
        assertThat(storage.exists(stagedKey)).isTrue();
        assertThat(storage.exists(durableKeys.get(0))).isFalse();
    }

    @Test
    void claim_rejectsAnotherOwnerAndExpiredUpload() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        ExamImageStorageService uploader = service(storage, NOW.minus(Duration.ofHours(25)));
        String stagedUrl = uploader.store(9L, png());

        beginTransaction();
        ExamImageStorageService current = service(storage, NOW);
        assertThatThrownBy(() -> current.beginClaim(10L).claimIn(stagedUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MSG_EXAM_IMAGE_STAGED_INVALID);
        assertThatThrownBy(() -> current.beginClaim(9L).claimIn(stagedUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MSG_EXAM_IMAGE_STAGED_INVALID);
        assertThat(durableKeys(storage)).isEmpty();
    }

    @Test
    void cleanup_removesOnlyExpiredStagedImages() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        String expired = service(storage, NOW.minus(Duration.ofHours(25))).store(1L, png());
        String current = service(storage, NOW).store(1L, png());

        service(storage, NOW).cleanupExpiredStagedImages();

        assertThat(storage.exists(keyFor(expired))).isFalse();
        assertThat(storage.exists(keyFor(current))).isTrue();
    }

    @Test
    void beginClaim_requiresSynchronizedTransaction() {
        ExamImageStorageService service =
                service(new LocalObjectStorage(storageRoot), NOW);

        assertThatThrownBy(() -> service.beginClaim(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");
    }

    @Test
    void claim_rejectsMalformedStagedUrlInsteadOfPersistingIt() {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        ExamImageStorageService service = service(storage, NOW);
        beginTransaction();

        assertThatThrownBy(() -> service.beginClaim(1L)
                .claimIn("<img src=\"/uploads/exams/staged-not-valid.png\">"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MSG_EXAM_IMAGE_STAGED_INVALID);
    }

    @Test
    void claim_sanitizesBeforeLookingForStagedImages() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        ExamImageStorageService service = service(storage, NOW);
        String stagedUrl = service.store(1L, png());
        beginTransaction();

        String sanitized = service.beginClaim(1L).claimIn(
                "<p>Nội dung hợp lệ</p><script>window.x='"
                        + stagedUrl + "'</script>");

        assertThat(sanitized)
                .contains("Nội dung hợp lệ")
                .doesNotContain("script", "staged-");
        assertThat(durableKeys(storage)).isEmpty();
    }

    @Test
    void claim_rejectsForeignHostAndEncodedStagedSources() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(storageRoot);
        ExamImageStorageService service = service(storage, NOW);
        String stagedUrl = service.store(1L, png());
        beginTransaction();
        var claim = service.beginClaim(1L);

        assertThatThrownBy(() -> claim.claimIn(
                "<img src=\"https://evil.example" + stagedUrl + "\">"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MSG_EXAM_IMAGE_STAGED_INVALID);

        String encoded = stagedUrl.replace("staged-", "%73taged-");
        assertThatThrownBy(() -> claim.claimIn("<img src=\"" + encoded + "\">"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MSG_EXAM_IMAGE_STAGED_INVALID);
        assertThat(durableKeys(storage)).isEmpty();
    }

    @Test
    void failedAfterCommitDelete_isRetriedByCleanupWorker() throws Exception {
        LocalObjectStorage backend = new LocalObjectStorage(storageRoot);
        FailOnceDeleteStorage storage = new FailOnceDeleteStorage(backend);
        ExamImageStorageService service = service(storage, NOW);
        String stagedUrl = service.store(3L, png());
        String stagedKey = keyFor(stagedUrl);

        beginTransaction();
        service.beginClaim(3L).claimIn("<img src=\"" + stagedUrl + "\">");
        commitTransaction();

        assertThat(backend.exists(stagedKey)).isTrue();
        service.cleanupExpiredStagedImages();
        assertThat(backend.exists(stagedKey)).isFalse();
    }

    private static ExamImageStorageService service(
            ObjectStorage storage, Instant instant) {
        return new ExamImageStorageService(
                storage, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile(
                "file", "question.png", "image/png",
                new byte[]{
                        (byte) 0x89, 'P', 'N', 'G',
                        '\r', '\n', 0x1A, '\n'
                });
    }

    private static String keyFor(String publicUrl) {
        return publicUrl.substring("/uploads/".length());
    }

    private static List<String> durableKeys(LocalObjectStorage storage)
            throws Exception {
        return storage.listKeys("exams/").stream()
                .filter(key -> !key.startsWith("exams/staged-"))
                .toList();
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void commitTransaction() {
        var synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static void rollbackTransaction() {
        var synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static final class FailOnceDeleteStorage implements ObjectStorage {
        private final ObjectStorage delegate;
        private final AtomicBoolean failNextDelete = new AtomicBoolean(true);

        private FailOnceDeleteStorage(ObjectStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public void put(String key, InputStream data, String contentType, long contentLength)
                throws IOException {
            delegate.put(key, data, contentType, contentLength);
        }

        @Override
        public void delete(String key) throws IOException {
            if (failNextDelete.compareAndSet(true, false)) {
                throw new IOException("simulated delete failure");
            }
            delegate.delete(key);
        }

        @Override
        public boolean exists(String key) {
            return delegate.exists(key);
        }

        @Override
        public StoredObject open(String key) throws IOException {
            return delegate.open(key);
        }

        @Override
        public StoredObject openRange(String key, long start, long end) throws IOException {
            return delegate.openRange(key, start, end);
        }

        @Override
        public void copy(String sourceKey, String destKey) throws IOException {
            delegate.copy(sourceKey, destKey);
        }

        @Override
        public List<String> listKeys(String prefix) throws IOException {
            return delegate.listKeys(prefix);
        }
    }
}
