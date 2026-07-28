package com.ksh.features.lessons.service;

import com.ksh.features.flashcards.repository.FlashcardRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParentLockBeforeMutationContractTest {

    @Test
    void flashcardAndSectionParentQueriesUseWriteLocks() throws Exception {
        assertWriteLock(FlashcardRepository.class.getMethod(
                "findByIdForUpdate", Long.class));
        assertWriteLock(SectionRepository.class.getMethod(
                "findByIdAndClassIdForUpdate", Long.class, Long.class));
    }

    @Test
    void smartReviewLocksCardBeforeReadingReviewState() throws Exception {
        String source = read("flashcards/service/SmartReviewService.java");
        int lock = source.indexOf("cardRepository.findByIdForUpdate(cardId)");
        int read = source.indexOf("reviewRepository", lock);
        assertTrue(lock >= 0 && read > lock);
    }

    @Test
    void sectionReorderLocksClassBeforeLoadingChildren() throws Exception {
        String source = read("lessons/service/SectionsReorderService.java");
        int lock = source.indexOf("classRepository.findByIdForUpdate(classId)");
        int load = source.indexOf(
                "sectionRepository.findByClassIdOrderByDisplayOrderAsc(classId)", lock);
        assertTrue(lock >= 0 && load > lock);
    }

    @Test
    void lessonReorderLocksSectionBeforeLoadingChildren() throws Exception {
        String source = read("lessons/service/LessonsReorderService.java");
        int lock = source.indexOf(
                "sectionRepository.findByIdAndClassIdForUpdate(sectionId, classId)");
        int load = source.indexOf(
                "lessonRepository\n                .findBySectionIdOrderByDisplayOrderAsc(sectionId)",
                lock);
        assertTrue(lock >= 0 && load > lock);
    }

    private static void assertWriteLock(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/ksh/features").resolve(relative),
                StandardCharsets.UTF_8);
    }
}
