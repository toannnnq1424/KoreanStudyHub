package com.ksh.features.questionbank.controller;

import com.ksh.features.questionbank.dto.QuestionBankViews.CategoryOption;
import com.ksh.features.questionbank.service.QuestionBankItemService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LecturerQuestionBankControllerTest {

    private final QuestionBankItemService itemService = mock(QuestionBankItemService.class);
    private final LecturerQuestionBankController controller = new LecturerQuestionBankController(itemService);
    private final KshUserDetails user = mock(KshUserDetails.class);

    @Test
    void create_form_exposes_actionable_empty_category_state() {
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(itemService.hasDepartment(7L, Role.LECTURER)).thenReturn(true);
        when(itemService.categoriesFor(7L, Role.LECTURER)).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.createForm(user, model);

        assertThat(view).isEqualTo("questionbank/form");
        assertThat(model.get("emptyDepartment")).isEqualTo(false);
        assertThat(model.get("emptyCategories")).isEqualTo(true);
        assertThat(model.get("categories")).isEqualTo(List.of());
    }

    @Test
    void create_form_is_enabled_when_an_active_category_exists() {
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(itemService.hasDepartment(7L, Role.LECTURER)).thenReturn(true);
        when(itemService.categoriesFor(7L, Role.LECTURER))
                .thenReturn(List.of(new CategoryOption(3L, "Ngữ pháp", true)));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.createForm(user, model);

        assertThat(model.get("emptyCategories")).isEqualTo(false);
        assertThat(model.get("categories"))
                .isEqualTo(List.of(new CategoryOption(3L, "Ngữ pháp", true)));
    }

    @Test
    void create_form_does_not_query_categories_without_a_department() {
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(itemService.hasDepartment(7L, Role.LECTURER)).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.createForm(user, model);

        assertThat(model.get("emptyDepartment")).isEqualTo(true);
        assertThat(model.get("emptyCategories")).isEqualTo(true);
        assertThat(model.get("categories")).isEqualTo(List.of());
        verify(itemService, never()).categoriesFor(7L, Role.LECTURER);
    }
}
