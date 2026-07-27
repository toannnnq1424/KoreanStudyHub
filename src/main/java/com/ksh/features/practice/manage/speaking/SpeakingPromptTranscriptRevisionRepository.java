package com.ksh.features.practice.manage.speaking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpeakingPromptTranscriptRevisionRepository
        extends JpaRepository<SpeakingPromptTranscriptRevision, Long> {

    @Query("""
            select coalesce(max(r.revisionNumber), 0)
            from SpeakingPromptTranscriptRevision r
            where r.artifactId = :artifactId
            """)
    int findMaximumRevisionNumber(@Param("artifactId") Long artifactId);

    Optional<SpeakingPromptTranscriptRevision>
    findFirstByArtifactIdAndRevisionSourceOrderByRevisionNumberDesc(
            Long artifactId, String revisionSource);

    void deleteByArtifactId(Long artifactId);
}
