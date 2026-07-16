package com.ksh.features.practice.repository;

import com.ksh.entities.PracticeSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeSectionRepository extends JpaRepository<PracticeSection, Long> {
    List<PracticeSection> findBySetIdOrderByDisplayOrderAsc(Long setId);
    List<PracticeSection> findBySetIdInOrderBySetIdAscDisplayOrderAsc(List<Long> setIds);
    void deleteBySetId(Long setId);

    @Query(value = "SELECT * FROM practice_sections WHERE id = :id FOR SHARE", nativeQuery = true)
    Optional<PracticeSection> findByIdForShare(@Param("id") Long id);
}
