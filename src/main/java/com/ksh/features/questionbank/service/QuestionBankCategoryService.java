package com.ksh.features.questionbank.service;

import com.ksh.entities.Category;
import com.ksh.entities.User;
import com.ksh.features.admin.categories.repository.CategoryRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private final CategoryRepository courseCategoryRepository;

    public QuestionBankCategoryService(UserRepository userRepository,
                                       QuestionBankAccessPolicy accessPolicy,
                                       QuestionBankCategoryRepository categoryRepository,
                                       QuestionBankItemRepository itemRepository,
                                       CategoryRepository courseCategoryRepository) {
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.courseCategoryRepository = courseCategoryRepository;
    }

    /**
     * Authoring catalogue composed from department-owned question-bank categories
     * and the active top-level course taxonomy managed by ADMIN.
     *
     * <p>A negative option id is an internal, transient reference to an ADMIN
     * course category. It is resolved into a department-scoped question-bank
     * category only when a question is actually saved or an import is confirmed;
     * opening a GET page never writes taxonomy rows. An existing department row
     * with the same name shadows the ADMIN source even when inactive, so a
     * LEADER's explicit hide decision cannot be bypassed.</p>
     */
    @Transactional(readOnly = true)
    public List<CategoryOption> activeOptionsFor(User user) {
        Long departmentId = requireDepartment(user);
        List<QuestionBankCategory> departmentCategories =
                categoryRepository.findByDepartmentIdOrderByNameAsc(departmentId);
        List<CategoryOption> options = new ArrayList<>();
        Set<String> shadowedNames = new LinkedHashSet<>();
        for (QuestionBankCategory category : departmentCategories) {
            shadowedNames.add(normalizeLookupName(category.getName()));
            if (category.isActive()) {
                options.add(new CategoryOption(category.getId(), category.getName(), true));
            }
        }
        for (Category category : courseCategoryRepository.findByParentIdIsNullOrderByNameAsc()) {
            String normalizedName = normalizeLookupName(category.getName());
            if (!category.isTopLevel() || !category.isActive() || shadowedNames.contains(normalizedName)) {
                continue;
            }
            options.add(new CategoryOption(adminCategoryReference(category.getId()), category.getName(), true));
            shadowedNames.add(normalizedName);
        }
        options.sort(Comparator.comparing(CategoryOption::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(options);
    }

    /**
     * Resolves either a real department category id or a transient ADMIN category
     * reference for a write. ADMIN-backed choices are mirrored lazily to satisfy
     * the existing {@code question_bank_items.category_id} foreign key.
     */
    @Transactional
    public QuestionBankCategory resolveForContribution(Long categoryReference, User actor) {
        Long departmentId = requireDepartment(actor);
        if (!accessPolicy.canAccessDepartment(actor, departmentId) || categoryReference == null) {
            throw new QuestionBankValidationException(MSG_FORBIDDEN);
        }
        if (categoryReference > 0L) {
            QuestionBankCategory category = categoryRepository
                    .findByIdAndDepartmentId(categoryReference, departmentId)
                    .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
            if (!category.isActive()) {
                throw new QuestionBankValidationException("Danh mục đang bị ẩn");
            }
            return category;
        }
        if (categoryReference == 0L || categoryReference == Long.MIN_VALUE) {
            throw new QuestionBankValidationException(MSG_NOT_FOUND);
        }

        Long sourceId = -categoryReference;
        Category source = courseCategoryRepository.findById(sourceId)
                .filter(Category::isTopLevel)
                .filter(Category::isActive)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
        QuestionBankCategory existing = categoryRepository
                .findByDepartmentIdAndNameIgnoreCase(departmentId, source.getName())
                .orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                throw new QuestionBankValidationException("Danh mục đang bị ẩn");
            }
            return existing;
        }
        categoryRepository.insertAdminMirrorIfAbsent(
                departmentId,
                source.getName(),
                source.getDescription(),
                actor.getId());
        QuestionBankCategory resolved = categoryRepository
                .findByDepartmentIdAndNameIgnoreCase(departmentId, source.getName())
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
        if (!resolved.isActive()) {
            throw new QuestionBankValidationException("Danh mục đang bị ẩn");
        }
        return resolved;
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

    private static Long adminCategoryReference(Long id) {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("ADMIN category id must be positive");
        }
        return -id;
    }

    private static String normalizeLookupName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
