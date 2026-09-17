package com.ksh.features.admin.settings;

import com.ksh.features.admin.settings.controller.PracticeAiControlPlaneController;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingForm;
import com.ksh.features.admin.settings.service.PracticeAiControlPlaneAdminService;
import com.ksh.features.practice.ai.controlplane.PracticeAiCapabilityTestService;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PracticeAiBindingFormRegressionTest {
    @Test
    void validationAndSaveErrorsKeepFormAndErrorsTogether() {
        for (boolean validationError : new boolean[]{true, false}) {
            var service = mock(PracticeAiControlPlaneAdminService.class);
            var controller = new PracticeAiControlPlaneController(service, mock(PracticeAiCapabilityTestService.class));
            var principal = mock(KshUserDetails.class);
            when(principal.getId()).thenReturn(1L);
            var purpose = PracticeAiPurpose.PRACTICE_WRITING_EVALUATION;
            var form = BindingForm.empty(purpose);
            var errors = new BeanPropertyBindingResult(form, "form");
            if (validationError) errors.reject("invalid");
            else doThrow(new IllegalStateException("BINDING_REVISION_CONFLICT"))
                    .when(service).saveBinding(form, 1L);
            var model = new ExtendedModelMap();
            assertThat(controller.saveBinding(purpose, form, errors, principal, model,
                    new RedirectAttributesModelMap())).isEqualTo("admin/settings-practice-ai-binding-form");
            assertThat(model.get("form")).isSameAs(form);
            assertThat(model.get(BindingResult.MODEL_KEY_PREFIX + "form")).isSameAs(errors);
            assertThat(errors.hasErrors()).isTrue();
        }
    }

    @Test
    void hiddenBooleanInputsAndNativeClipboardRemainAvailable() throws Exception {
        String form = Files.readString(Path.of("src/main/resources/templates/admin/settings-practice-ai-binding-form.html"));
        assertThat(form).contains("name=\"directAudioInput\" value=\"false\"",
                "name=\"pdfImageInput\" value=\"false\"");
        String player = Files.readString(Path.of("src/main/resources/static/js/practice/player-exam.js"));
        assertThat(player).doesNotContain("addEventListener('paste'", "addEventListener('copy'",
                "addEventListener('cut'", "onpaste", "oncopy", "oncut");
    }
}
