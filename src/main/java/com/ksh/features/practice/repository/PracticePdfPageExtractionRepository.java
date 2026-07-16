package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePdfPageExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PracticePdfPageExtractionRepository extends JpaRepository<PracticePdfPageExtraction, Long> {
    Optional<PracticePdfPageExtraction> findBySessionIdAndPageNumber(Long sessionId, Integer pageNumber);
    List<PracticePdfPageExtraction> findBySessionIdOrderByPageNumberAsc(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
