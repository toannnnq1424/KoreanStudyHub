package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePdfImportSectionDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticePdfImportSectionDraftRepository extends JpaRepository<PracticePdfImportSectionDraft, String> {
    List<PracticePdfImportSectionDraft> findBySessionIdOrderByDisplayOrderAsc(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
