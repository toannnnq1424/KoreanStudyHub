package com.ksh.features.practice.ai.controlplane;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeAiProviderProfileRepository
        extends JpaRepository<PracticeAiProviderProfile, Long> {

    Optional<PracticeAiProviderProfile> findByProfileCode(String profileCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PracticeAiProviderProfile p where p.id = :id")
    Optional<PracticeAiProviderProfile> findByIdForUpdate(@Param("id") Long id);

    @Query("select p from PracticeAiProviderProfile p order by p.profileCode")
    List<PracticeAiProviderProfile> findAllOrdered();
}
