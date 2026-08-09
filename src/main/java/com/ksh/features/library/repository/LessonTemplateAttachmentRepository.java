package com.ksh.features.library.repository;

import com.ksh.entities.LessonTemplateAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for supplementary attachments on lesson templates.
 */
public interface LessonTemplateAttachmentRepository
        extends JpaRepository<LessonTemplateAttachment, Long> {

    List<LessonTemplateAttachment> findByTemplateIdOrderByDisplayOrderAsc(Long templateId);

    Optional<LessonTemplateAttachment> findByTemplateIdAndLibraryAssetId(
            Long templateId, Long libraryAssetId);

    void deleteByTemplateId(Long templateId);
}
