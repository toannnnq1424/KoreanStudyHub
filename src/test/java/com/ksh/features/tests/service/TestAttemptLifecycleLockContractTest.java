package com.ksh.features.tests.service;

import com.ksh.features.tests.repository.TestAttemptRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-free contract for serializing attempt lifecycle mutations.
 */
class TestAttemptLifecycleLockContractTest {

    @Test
    void ownedAttemptMutationQueryUsesPessimisticWriteLock() throws Exception {
        Method method = TestAttemptRepository.class.getMethod(
                "findByIdAndUserIdForUpdate", Long.class, Long.class);

        Lock lock = method.getAnnotation(Lock.class);
        Query query = method.getAnnotation(Query.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
        assertNotNull(query);
        assertTrue(query.value().contains("a.id = :id"));
        assertTrue(query.value().contains("a.userId = :userId"));
    }

    @Test
    void heartbeatAndSubmitBothAcquireOwnedAttemptForUpdate() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/ksh/features/tests/service/TestAttemptService.java"),
                StandardCharsets.UTF_8);

        String heartbeat = methodBody(source, "public void heartbeat(");
        String submit = methodBody(source, "public SubmitResult submit(");

        assertTrue(heartbeat.contains(
                "accessResolver.requireOwnAttemptForUpdate(attemptId, userId)"));
        assertTrue(submit.contains(
                "accessResolver.requireOwnAttemptForUpdate(attemptId, userId)"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int nextMethod = source.indexOf("\n    @", start + signature.length());
        return nextMethod < 0 ? source.substring(start) : source.substring(start, nextMethod);
    }
}
