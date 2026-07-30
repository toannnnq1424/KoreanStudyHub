package com.ksh.features.questionbank.repository;

import com.ksh.features.questionbank.entity.QuestionBankCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for department-scoped question bank categories.
 */
public interface QuestionBankCategoryRepository extends JpaRepository<QuestionBankCategory, Long> {

    List<QuestionBankCategory> findByDepartmentIdOrderByNameAsc(Long departmentId);

    List<QuestionBankCategory> findByDepartmentIdAndActiveTrueOrderByNameAsc(Long departmentId);

    Optional<QuestionBankCategory> findByIdAndDepartmentId(Long id, Long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT category
            FROM QuestionBankCategory category
            WHERE category.id = :id
              AND category.departmentId = :departmentId
            """)
    Optional<QuestionBankCategory> findByIdAndDepartmentIdForUpdate(
            @Param("id") Long id,
            @Param("departmentId") Long departmentId);

    boolean existsByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);

    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(Long departmentId, String name, Long id);
}
