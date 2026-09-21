package com.ksh.features.assignments;

import com.ksh.features.assignments.service.AssignmentAttachmentStorage;
import com.ksh.features.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssignmentAttachmentStorageTest {
    ObjectStorage objects = mock(ObjectStorage.class);
    AssignmentAttachmentStorage storage = new AssignmentAttachmentStorage(objects);
    @Test void acceptsPdfWithPrivateScopedKey() throws Exception {
        String key = storage.store(new MockMultipartFile("attachment", "answer.pdf", "text/plain", "%PDF-1.7\ncontent".getBytes()), 3L, 9L);
        assertThat(key).startsWith("assignment-submissions/3/9/").endsWith(".pdf");
        verify(objects).put(eq(key), any(), eq("application/pdf"), anyLong());
    }
    @Test void rejectsDisguisedHtml() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("attachment", "answer.pdf", "application/pdf", "<html>".getBytes()), 3L, 9L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(objects);
    }
    @Test void rejectsOversizedFile() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("attachment", "answer.pdf", "application/pdf", new byte[AssignmentAttachmentStorage.MAX_BYTES + 1]), 3L, 9L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(objects);
    }
    @Test void refusesForeignStorageKey() {
        assertThatThrownBy(() -> storage.read("assignment-submissions/3/8/other.pdf", 3L, 9L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verifyNoInteractions(objects);
    }
    @Test void removesObjectOnTransactionRollback() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            String key = storage.store(new MockMultipartFile("attachment", "answer.pdf", "application/pdf", "%PDF-1.7".getBytes()), 3L, 9L);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(objects).delete(key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
