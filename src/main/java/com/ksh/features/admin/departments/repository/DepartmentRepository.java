package com.ksh.features.admin.departments.repository;

import com.ksh.entities.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Department}.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** Locks one department row for state mutations such as toggles and leader assignment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Department d WHERE d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") Long id);

    List<Department> findAllByOrderByNameAsc();

    List<Department> findByActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    Optional<Department> findFirstByLeaderUserId(Long leaderUserId);

    boolean existsByLeaderUserId(Long leaderUserId);

    /**
     * Enforces the product rule that one user can lead at most one department.
     * Leader mutations are serialized by the service before this predicate runs.
     */
    boolean existsByLeaderUserIdAndIdNot(Long leaderUserId, Long id);

    long countByLeaderUserId(Long leaderUserId);
}
