package com.ksh.features.mail.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MailOutboxRepositoryContractTest {

    @Test
    void sent_retention_query_is_literal_bounded_and_uses_v59_due_index_order() throws Exception {
        String sql = query("deleteSentBefore");

        assertThat(sql)
                .contains("status = 'SENT'")
                .contains("available_at < :cutoff")
                .contains("ORDER BY available_at ASC, id ASC")
                .contains("LIMIT :batchSize")
                .doesNotContain("PENDING", "RETRY", "PROCESSING", ":status");
    }

    @Test
    void failed_retention_query_is_literal_bounded_and_uses_v59_due_index_order() throws Exception {
        String sql = query("deleteFailedBefore");

        assertThat(sql)
                .contains("status = 'FAILED'")
                .contains("available_at < :cutoff")
                .contains("ORDER BY available_at ASC, id ASC")
                .contains("LIMIT :batchSize")
                .doesNotContain("PENDING", "RETRY", "PROCESSING", ":status");
    }

    private static String query(String methodName) throws Exception {
        Method method = MailOutboxRepository.class.getMethod(
                methodName,
                LocalDateTime.class,
                int.class);
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        return query.value();
    }
}
