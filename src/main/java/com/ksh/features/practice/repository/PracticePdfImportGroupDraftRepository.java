package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePdfImportGroupDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticePdfImportGroupDraftRepository extends JpaRepository<PracticePdfImportGroupDraft, String> {
    List<PracticePdfImportGroupDraft> findBySessionIdOrderByDisplayOrderAsc(Long sessionId);
    List<PracticePdfImportGroupDraft> findBySessionIdAndSectionTempIdOrderByDisplayOrderAsc(Long sessionId, String sectionTempId);
    void deleteBySessionId(Long sessionId);
}
