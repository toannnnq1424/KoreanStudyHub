package com.ksh.features.questionbank.imports;

import com.ksh.features.classes.imports.session.ImportSession;
import com.ksh.features.classes.imports.session.ImportSessionStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportSessionAtomicClaimTest {

    @Test
    void questionBankSessionCanBeClaimedExactlyOnceAndRestoredForRetry() throws Exception {
        QuestionBankImportSessionStore store = new QuestionBankImportSessionStore();
        QuestionBankImportSession session = new QuestionBankImportSession(
                UUID.randomUUID(), 7L, 3L, Instant.now(), "questions.xlsx",
                "PENDING_REVIEW", List.of(), List.of());
        store.save(session);

        assertEquals(1, concurrentSuccessfulClaims(
                () -> store.claim(session.getId(), 7L)));
        assertTrue(store.claim(session.getId(), 7L).isEmpty());

        store.restore(session);
        assertTrue(store.claim(session.getId(), 7L).isPresent());
    }

    @Test
    void classImportSessionCanBeClaimedExactlyOnceAndRestoredForRetry() throws Exception {
        ImportSessionStore store = new ImportSessionStore();
        ImportSession session = new ImportSession(
                UUID.randomUUID(), 2L, 7L, Instant.now(), "students.xlsx", List.of());
        store.save(session);

        assertEquals(1, concurrentSuccessfulClaims(
                () -> store.claim(session.getId(), 7L)));
        assertTrue(store.claim(session.getId(), 7L).isEmpty());

        store.restore(session);
        assertTrue(store.claim(session.getId(), 7L).isPresent());
    }

    @Test
    void wrongOwnerCannotConsumeEitherSession() {
        QuestionBankImportSessionStore questionStore = new QuestionBankImportSessionStore();
        QuestionBankImportSession questionSession = new QuestionBankImportSession(
                UUID.randomUUID(), 7L, 3L, Instant.now(), "questions.xlsx",
                "PENDING_REVIEW", List.of(), List.of());
        questionStore.save(questionSession);
        assertTrue(questionStore.claim(questionSession.getId(), 8L).isEmpty());
        assertTrue(questionStore.claim(questionSession.getId(), 7L).isPresent());

        ImportSessionStore classStore = new ImportSessionStore();
        ImportSession classSession = new ImportSession(
                UUID.randomUUID(), 2L, 7L, Instant.now(), "students.xlsx", List.of());
        classStore.save(classSession);
        assertTrue(classStore.claim(classSession.getId(), 8L).isEmpty());
        assertTrue(classStore.claim(classSession.getId(), 7L).isPresent());
    }

    private static int concurrentSuccessfulClaims(Claim claim) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> runClaim(claim, ready, start));
            Future<Boolean> second = executor.submit(() -> runClaim(claim, ready, start));
            ready.await();
            start.countDown();
            return (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean runClaim(Claim claim, CountDownLatch ready,
                                    CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return claim.execute().isPresent();
    }

    @FunctionalInterface
    private interface Claim {
        Optional<?> execute();
    }
}
