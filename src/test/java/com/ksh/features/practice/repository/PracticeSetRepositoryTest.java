package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
class PracticeSetRepositoryTest {

    @Autowired
    private PracticeSetRepository setRepository;

    @Test
    void testSaveAndFindByStatus() {
        PracticeSet set1 = new PracticeSet(
                "TOPIK II - Đọc hiểu 35",
                "Mô tả đề thi đọc hiểu TOPIK II kì 35",
                "READING",
                "GLOBAL",
                null,
                "practice-pdfs/test.pdf",
                "{}",
                "PUBLISHED",
                1L
        );
        setRepository.saveAndFlush(set1);

        List<PracticeSet> published = setRepository.findByStatusOrderByCreatedAtDesc("PUBLISHED");
        assertFalse(published.isEmpty());
        assertTrue(published.stream().anyMatch(s -> "TOPIK II - Đọc hiểu 35".equals(s.getTitle())));
    }
}
