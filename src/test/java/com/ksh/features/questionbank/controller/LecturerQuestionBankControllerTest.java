package com.ksh.features.questionbank.controller;

import com.ksh.features.questionbank.service.QuestionBankItemService;
import com.ksh.features.questionbank.service.QuestionBankTestGenerationService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LecturerQuestionBankControllerTest {

    private final QuestionBankItemService itemService = mock(QuestionBankItemService.class);
    private final QuestionBankTestGenerationService generationService =
            mock(QuestionBankTestGenerationService.class);
    private final LecturerQuestionBankController controller =
            new LecturerQuestionBankController(itemService, generationService);
    private final KshUserDetails user = mock(KshUserDetails.class);

    @Test
    void create_form_is_enabled_when_actor_has_a_subject() {
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(itemService.hasSubject(7L, Role.LECTURER)).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.createForm(user, model)).isEqualTo("questionbank/form");
        assertThat(model.get("emptyDepartment")).isEqualTo(false);
        assertThat(model).doesNotContainKeys("categories", "emptyCategories");
    }

    @Test
    void create_form_is_blocked_without_a_subject() {
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(itemService.hasSubject(7L, Role.LECTURER)).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.createForm(user, model);

        assertThat(model.get("emptyDepartment")).isEqualTo(true);
    }
}
