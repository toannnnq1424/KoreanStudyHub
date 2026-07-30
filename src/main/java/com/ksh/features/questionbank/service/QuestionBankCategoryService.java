package com.ksh.features.questionbank.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.questionbank.dto.QuestionBankCategoryForm;
import com.ksh.features.questionbank.dto.QuestionBankViews.CategoryOption;
import com.ksh.features.questionbank.dto.QuestionBankViews.CategoryRow;
import com.ksh.features.questionbank.entity.QuestionBankCategory;
import com.ksh.features.questionbank.repository.QuestionBankCategoryRepository;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Department-scoped category CRUD for question bank curation. */
@Service
public class QuestionBankCategoryService {

    private static final String MSG_EMPTY_DEPARTMENT =
            "Bạn chưa được gán bộ môn để quản lý ngân hàng câu hỏi";
    private static final String MSG_FORBIDDEN =
            "Bạn không có quyền quản lý danh mục bộ môn này";
    private static final String MSG_NOT_FOUND = "Không tìm thấy danh mục";
    private static final String MSG_DUPLICATE = "Tên danh mục đã tồn tại trong bộ môn";
    private static final String MSG_IN_USE = "Không thể xoá: danh mục đang có câu hỏi";

    private final UserRepository userRepository;
    private final QuestionBankAccessPolicy accessPolicy;
    private final QuestionBankCategoryRepository categoryRepository;
    private final QuestionBankItemRepository itemRepository;

    public QuestionBankCategoryService(UserRepository userRepository,
                                       QuestionBankAccessPolicy accessPolicy,
                                       QuestionBankCategoryRepository categoryRepository,
                                       QuestionBankItemRepository itemRepository) {
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Returns the single canonical authoring catalogue for the actor's
     * department. Only active department-owned question-bank categories are
     * exposed, so lecturer authoring/filtering and LEADER curation always refer
     * to the same rows and identifiers.
     */
    @Transactional(readOnly = true)
    public List<CategoryOption> activeOptionsFor(User user) {
        Long departmentId = requireDepartment(user);
        return categoryRepository.findByDepartmentIdAndActiveTrueOrderByNameAsc(departmentId).stream()
                .map(category -> new CategoryOption(category.getId(), category.getName(), true))
                .toList();
    }

    /**
     * Resolves a real, active category owned by the actor's department.
     */
    @Transactional(readOnly = true)
    public QuestionBankCategory resolveForContribution(Long categoryReference, User actor) {
        Long departmentId = requireDepartment(actor);
        if (!accessPolicy.canAccessDepartment(actor, departmentId) || categoryReference == null) {
            throw new QuestionBankValidationException(MSG_FORBIDDEN);
        }
        if (categoryReference <= 0L) {
            throw new QuestionBankValidationException(MSG_NOT_FOUND);
        }
        QuestionBankCategory category = categoryRepository
                .findByIdAndDepartmentId(categoryReference, departmentId)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
        if (!category.isActive()) {
            throw new QuestionBankValidationException("Danh mục đang bị ẩn");
        }
        return category;
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> rowsForCurator(Long userId) {
        User actor = requireCurator(userId);
        Long departmentId = requireDepartment(actor);
        return categoryRepository.findByDepartmentIdOrderByNameAsc(departmentId).stream()
                .map(category -> new CategoryRow(
                        category.getId(),
                        category.getName(),
                        category.getDescription(),
                        category.isActive(),
                        itemRepository.countByCategoryId(category.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionBankCategoryForm loadForm(Long userId, Long categoryId) {
        User actor = requireCurator(userId);
        QuestionBankCategory category = requireSameDepartmentCategory(categoryId, actor);
        QuestionBankCategoryForm form = new QuestionBankCategoryForm();
        form.setName(category.getName());
        form.setDescription(category.getDescription());
        form.setActive(category.isActive());
        return form;
    }

    @Transactional
    public void create(Long userId, QuestionBankCategoryForm form) {
        User actor = requireCurator(userId);
        Long departmentId = requireDepartment(actor);
        String name = normalizeName(form.getName());
        if (categoryRepository.existsByDepartmentIdAndNameIgnoreCase(departmentId, name)) {
            throw new QuestionBankValidationException(MSG_DUPLICATE);
        }
        categoryRepository.save(new QuestionBankCategory(
                departmentId,
                name,
                blankToNull(form.getDescription()),
                form.isActive(),
                actor.getId()));
    }

    @Transactional
    public void update(Long userId, Long categoryId, QuestionBankCategoryForm form) {
        User actor = requireCurator(userId);
        QuestionBankCategory category = requireSameDepartmentCategory(categoryId, actor);
        String name = normalizeName(form.getName());
        if (categoryRepository.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(
                category.getDepartmentId(), name, category.getId())) {
            throw new QuestionBankValidationException(MSG_DUPLICATE);
        }
        category.updateDetails(name, blankToNull(form.getDescription()), form.isActive());
        categoryRepository.save(category);
    }

    @Transactional
    public void toggle(Long userId, Long categoryId) {
        User actor = requireCurator(userId);
        Long departmentId = requireDepartment(actor);
        if (!accessPolicy.canCurateDepartment(actor, departmentId)) {
            throw new QuestionBankValidationException(MSG_FORBIDDEN);
        }
        QuestionBankCategory category = categoryRepository
                .findByIdAndDepartmentIdForUpdate(categoryId, departmentId)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
        category.updateDetails(category.getName(), category.getDescription(), !category.isActive());
        categoryRepository.save(category);
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        User actor = requireCurator(userId);
        QuestionBankCategory category = requireSameDepartmentCategory(categoryId, actor);
        if (itemRepository.countByCategoryId(category.getId()) > 0) {
            throw new QuestionBankValidationException(MSG_IN_USE);
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public QuestionBankCategory requireVisibleCategory(Long categoryId, User actor) {
        Long departmentId = requireDepartment(actor);
        if (!accessPolicy.canAccessDepartment(actor, departmentId)) {
            throw new QuestionBankValidationException(MSG_FORBIDDEN);
        }
        return categoryRepository.findByIdAndDepartmentId(categoryId, departmentId)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
    }

    private User requireCurator(Long userId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_FORBIDDEN));
        if (actor.getRole() != Role.LEADER && actor.getRole() != Role.ADMIN) {
            throw new QuestionBankValidationException(MSG_FORBIDDEN);
        }
        requireDepartment(actor);
        return actor;
    }

    private QuestionBankCategory requireSameDepartmentCategory(Long categoryId, User actor) {
        Long departmentId = requireDepartment(actor);
        if (!accessPolicy.canCurateDepartment(actor, departmentId)) {
            throw new QuestionBankValidationException(MSG_FORBIDDEN);
        }
        return categoryRepository.findByIdAndDepartmentId(categoryId, departmentId)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
    }

    private Long requireDepartment(User actor) {
        Long departmentId = accessPolicy.resolveDepartmentId(actor);
        if (departmentId == null) {
            throw new QuestionBankValidationException(MSG_EMPTY_DEPARTMENT);
        }
        return departmentId;
    }

    private static String normalizeName(String value) {
        String name = blankToNull(value);
        if (name == null) {
            throw new QuestionBankValidationException("Tên danh mục không được để trống");
        }
        return name;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
