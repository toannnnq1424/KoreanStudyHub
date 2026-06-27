package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePdfRegionAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticePdfRegionAnnotationRepository extends JpaRepository<PracticePdfRegionAnnotation, Long> {
    List<PracticePdfRegionAnnotation> findBySessionIdOrderByPageNumberAscDisplayOrderAsc(Long sessionId);
    List<PracticePdfRegionAnnotation> findBySessionIdAndPageNumberOrderByDisplayOrderAsc(Long sessionId, Integer pageNumber);
    void deleteBySessionId(Long sessionId);
}
