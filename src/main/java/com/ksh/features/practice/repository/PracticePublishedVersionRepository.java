package com.ksh.features.practice.repository;

import com.ksh.entities.PracticePublishedVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface PracticePublishedVersionRepository extends JpaRepository<PracticePublishedVersion, Long> {

    Optional<PracticePublishedVersion> findFirstBySetIdAndStatusOrderByVersionNumberDesc(
            Long setId, String status);

    Optional<PracticePublishedVersion> findFirstBySetIdOrderByVersionNumberDesc(Long setId);

    List<PracticePublishedVersion> findBySetIdOrderByVersionNumberDesc(Long setId);

    @Query("select coalesce(max(v.versionNumber), 0) from PracticePublishedVersion v where v.setId = :setId")
    Integer maxVersionNumberBySetId(@Param("setId") Long setId);
}
