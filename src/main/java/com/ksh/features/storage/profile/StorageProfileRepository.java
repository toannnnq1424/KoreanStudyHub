package com.ksh.features.storage.profile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StorageProfileRepository extends JpaRepository<StorageProfile, StorageProfileCode> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StorageProfile p where p.profileCode = :code")
    Optional<StorageProfile> findByCodeForUpdate(@Param("code") StorageProfileCode code);

    @Query("select p from StorageProfile p order by p.profileCode")
    List<StorageProfile> findAllOrdered();
}
