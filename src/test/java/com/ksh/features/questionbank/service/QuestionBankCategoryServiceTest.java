package com.ksh.features.questionbank.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.features.questionbank.dto.QuestionBankCategoryForm;
import com.ksh.features.questionbank.dto.QuestionBankViews.CategoryOption;
import com.ksh.features.questionbank.entity.QuestionBankCategory;
import com.ksh.features.questionbank.repository.QuestionBankCategoryRepository;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.security.Role;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for department-scoped category validation. */
class QuestionBankCategoryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final QuestionBankCategoryRepository categoryRepository = mock(QuestionBankCategoryRepository.class);
    private final QuestionBankItemRepository itemRepository = mock(QuestionBankItemRepository.class);
    private final LeaderDepartmentResolver resolver = mock(LeaderDepartmentResolver.class);
    private final QuestionBankAccessPolicy accessPolicy = new QuestionBankAccessPolicy(resolver);
    private final QuestionBankCategoryService service = new QuestionBankCategoryService(
            userRepository, accessPolicy, categoryRepository, itemRepository);

    @Test
    void authoring_options_use_only_active_categories_owned_by_the_department() {
        User lecturer = user(Role.LECTURER, 10L, 5L);
        QuestionBankCategory grammar = new QuestionBankCategory(
                5L, "Ngữ pháp", null, true, 11L);
        setId(grammar, 3L);
        when(categoryRepository.findByDepartmentIdAndActiveTrueOrderByNameAsc(5L))
                .thenReturn(List.of(grammar));

        List<CategoryOption> options = service.activeOptionsFor(lecturer);

        assertThat(options).containsExactly(new CategoryOption(3L, "Ngữ pháp", true));
        verify(categoryRepository).findByDepartmentIdAndActiveTrueOrderByNameAsc(5L);
    }

    @Test
    void contribution_resolves_an_active_category_in_the_actor_department() {
        User lecturer = user(Role.LECTURER, 10L, 5L);
        QuestionBankCategory category = new QuestionBankCategory(
                5L, "Ngữ pháp", null, true, 11L);
        setId(category, 21L);
        when(categoryRepository.findByIdAndDepartmentId(21L, 5L))
                .thenReturn(Optional.of(category));

        QuestionBankCategory resolved = service.resolveForContribution(21L, lecturer);

        assertThat(resolved).isSameAs(category);
    }

    @Test
    void contribution_rejects_a_legacy_admin_taxonomy_reference() {
        User lecturer = user(Role.LECTURER, 10L, 5L);

        assertThatThrownBy(() -> service.resolveForContribution(-8L, lecturer))
                .isInstanceOf(QuestionBankValidationException.class);
    }

    @Test
    void rejects_duplicate_name_in_same_department() {
        User leader = user(Role.LEADER, 10L, 99L);
        Department department = department(5L, leader.getId());
        QuestionBankCategoryForm form = new QuestionBankCategoryForm();
        form.setName("Chương 1");

        when(userRepository.findById(leader.getId())).thenReturn(Optional.of(leader));
        when(resolver.resolve(leader.getId())).thenReturn(Optional.of(department));
        when(categoryRepository.existsByDepartmentIdAndNameIgnoreCase(5L, "Chương 1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(leader.getId(), form))
                .isInstanceOf(QuestionBankValidationException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    void deletes_only_when_category_has_no_items() {
        User leader = user(Role.LEADER, 10L, 99L);
        Department department = department(5L, leader.getId());
        QuestionBankCategory category = new QuestionBankCategory(5L, "Grammar", null, true, leader.getId());
        setId(category, 3L);

        when(userRepository.findById(leader.getId())).thenReturn(Optional.of(leader));
        when(resolver.resolve(leader.getId())).thenReturn(Optional.of(department));
        when(categoryRepository.findByIdAndDepartmentId(3L, 5L)).thenReturn(Optional.of(category));
        when(itemRepository.countByCategoryId(3L)).thenReturn(0L);

        service.delete(leader.getId(), 3L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void toggle_locks_the_department_scoped_category_before_flipping_state() {
        User leader = user(Role.LEADER, 10L, 99L);
        Department department = department(5L, leader.getId());
        QuestionBankCategory category =
                new QuestionBankCategory(5L, "Grammar", null, true, leader.getId());
        setId(category, 3L);

        when(userRepository.findById(leader.getId())).thenReturn(Optional.of(leader));
        when(resolver.resolve(leader.getId())).thenReturn(Optional.of(department));
        when(categoryRepository.findByIdAndDepartmentIdForUpdate(3L, 5L))
                .thenReturn(Optional.of(category));

        service.toggle(leader.getId(), 3L);

        assertThat(category.isActive()).isFalse();
        verify(categoryRepository).findByIdAndDepartmentIdForUpdate(3L, 5L);
        verify(categoryRepository).save(category);
    }

    @Test
    void toggle_lookup_declares_a_pessimistic_write_lock() throws Exception {
        Method lookup = QuestionBankCategoryRepository.class.getMethod(
                "findByIdAndDepartmentIdForUpdate", Long.class, Long.class);

        Lock lock = lookup.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private static User user(Role role, Long id, Long departmentId) {
        try {
            Constructor<User> ctor = User.class.getDeclaredConstructor(
                    String.class, String.class, String.class, Role.class,
                    boolean.class, boolean.class, boolean.class, boolean.class,
                    String.class, String.class);
            ctor.setAccessible(true);
            User user = ctor.newInstance("u@example.com", "hash", "User", role,
                    true, true, false, false, null, null);
            user.setDepartmentId(departmentId);
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            return user;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Department department(Long id, Long leaderUserId) {
        try {
            Department department = new Department("CNTT", "CNTT", null, true);
            department.assignLeader(leaderUserId);
            Field idField = Department.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(department, id);
            return department;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void setId(QuestionBankCategory category, Long id) {
        try {
            Field idField = QuestionBankCategory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
