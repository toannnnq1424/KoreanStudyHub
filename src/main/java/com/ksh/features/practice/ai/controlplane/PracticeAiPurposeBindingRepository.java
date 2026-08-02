package com.ksh.features.practice.ai.controlplane;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeAiPurposeBindingRepository
        extends JpaRepository<PracticeAiPurposeBinding, String> {

    @Query("select b from PracticeAiPurposeBinding b join fetch b.providerProfile "
            + "where b.purposeCode = :purposeCode")
    Optional<PracticeAiPurposeBinding> findDetailed(
            @Param("purposeCode") String purposeCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PracticeAiPurposeBinding b join fetch b.providerProfile "
            + "where b.purposeCode = :purposeCode")
    Optional<PracticeAiPurposeBinding> findDetailedForUpdate(
            @Param("purposeCode") String purposeCode);

    @Query("select b from PracticeAiPurposeBinding b join fetch b.providerProfile "
            + "order by b.purposeCode")
    List<PracticeAiPurposeBinding> findAllDetailed();

    long countByProviderProfileId(Long providerProfileId);
}
