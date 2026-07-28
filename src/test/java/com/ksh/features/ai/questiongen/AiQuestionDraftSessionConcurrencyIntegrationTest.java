package com.ksh.features.ai.questiongen;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** MySQL locking contract: two confirms of one durable preview yield one winner. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AiQuestionDraftSessionStore.class,
        AiQuestionDraftSessionConcurrencyIntegrationTest.JsonConfig.class
})
class AiQuestionDraftSessionConcurrencyIntegrationTest {

    @Autowired
    private AiQuestionDraftSessionRepository repository;

    @Autowired
    private AiQuestionDraftSessionStore store;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrent_consumers_allow_exactly_one_commit() throws Exception {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        repository.saveAndFlush(new AiQuestionDraftSessionEntity(
                id, 42L, 9L,
                """
                [{"type":"MCQ","content":"Câu hỏi","explanation":null,
                "options":[{"content":"A","correct":true},{"content":"B","correct":false}]}]
                """,
                now, now.plusMinutes(10)));

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> consume(id, start));
            Future<Boolean> second = executor.submit(() -> consume(id, start));
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
            repository.deleteById(id);
        }
    }

    private boolean consume(String id, CountDownLatch start) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                var loaded = store.requireForUpdate(id, 42L, 9L);
                store.consume(loaded);
            });
            return true;
        } catch (IllegalArgumentException expectedReplay) {
            return false;
        }
    }

    @TestConfiguration
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
