package com.ksh.features.ai.questiongen;

import com.ksh.features.ai.client.AiClientException;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.ConfirmRequest;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.ConfirmResult;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.GenerateRequest;
import com.ksh.features.lessons.dto.SectionDtos.AjaxResult;
import com.ksh.security.AuthenticatedUserIdResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiQuestionGenerationControllerTest {

    private final AiQuestionGenerationService service =
            mock(AiQuestionGenerationService.class);
    private final AuthenticatedUserIdResolver userIdResolver =
            mock(AuthenticatedUserIdResolver.class);
    private final Authentication authentication = mock(Authentication.class);
    private AiQuestionGenerationController controller;

    @BeforeEach
    void setUp() {
        controller = new AiQuestionGenerationController(service, userIdResolver);
        when(userIdResolver.resolve(authentication)).thenReturn(42L);
    }

    @Test
    void rejects_request_without_file_or_text_before_calling_service() {
        var response = controller.generate(9L, null, " ", 5,
                "MCQ", "medium", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void provider_details_are_not_exposed_to_the_lecturer() {
        when(service.generate(eq(42L), eq(9L), any(), any(), any(GenerateRequest.class)))
                .thenThrow(new AiClientException(
                        "PrivateProvider: HTTP 401 — secret upstream response"));
        MockMultipartFile file =
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF".getBytes());

        var response = controller.generate(9L, file, null, 5,
                "MCQ", "medium", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        AjaxResult body = (AjaxResult) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message())
                .contains("tạm thời")
                .doesNotContain("PrivateProvider", "401", "secret");
    }

    @Test
    void confirm_returns_the_service_result_in_the_shared_ajax_envelope() {
        ConfirmRequest request = new ConfirmRequest(
                "3bde5f97-6573-44d8-94c7-019128de5e0b", List.of(0, 2));
        when(service.confirm(42L, 9L, request)).thenReturn(new ConfirmResult(2));

        var response = controller.confirm(9L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AjaxResult body = (AjaxResult) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.ok()).isTrue();
        assertThat(body.data()).isEqualTo(new ConfirmResult(2));
    }
}
