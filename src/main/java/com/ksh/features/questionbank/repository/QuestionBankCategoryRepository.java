package com.ksh.features.questionbank.repository;

import com.ksh.features.questionbank.entity.QuestionBankCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    Optional<QuestionBankCategory> findByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);

    /**
     * Atomically creates the department mirror for an ADMIN taxonomy choice.
     * The unique (department_id, name) key makes concurrent authoring/import
     * requests converge on one row without weakening any other constraint.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO question_bank_categories
                (department_id, name, description, is_active, created_by)
            VALUES
                (:departmentId, :name, :description, 1, :createdBy)
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """, nativeQuery = true)
    int insertAdminMirrorIfAbsent(@Param("departmentId") Long departmentId,
                                  @Param("name") String name,
                                  @Param("description") String description,
                                  @Param("createdBy") Long createdBy);

    boolean existsByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);

    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(Long departmentId, String name, Long id);
}
