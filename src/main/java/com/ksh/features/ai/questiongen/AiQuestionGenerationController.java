package com.ksh.features.ai.questiongen;

import com.ksh.features.ai.client.AiClientException;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.ConfirmRequest;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.GenerateRequest;
import com.ksh.security.AuthenticatedUserIdResolver;
import com.ksh.security.Roles;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.ksh.common.IConstant.BASE_LECTURER_TESTS;
import static com.ksh.features.lessons.controller.support.AjaxResponses.badRequest;
import static com.ksh.features.lessons.controller.support.AjaxResponses.forbidden;
import static com.ksh.features.lessons.controller.support.AjaxResponses.internalError;
import static com.ksh.features.lessons.controller.support.AjaxResponses.notFound;
import static com.ksh.features.lessons.dto.SectionDtos.AjaxResult;

/** JSON endpoints behind the lecturer's AI question preview panel. */
@RestController
@RequestMapping(BASE_LECTURER_TESTS + "/{testId}/ai-questions")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class AiQuestionGenerationController {

    private static final Logger log =
            LoggerFactory.getLogger(AiQuestionGenerationController.class);

    private static final String MSG_NO_MATERIAL =
            "Vui lòng tải lên file PDF/DOCX hoặc dán nội dung tài liệu";
    private static final String MSG_AI_UNAVAILABLE =
            "Dịch vụ AI tạm thời không khả dụng, vui lòng thử lại sau";

    private final AiQuestionGenerationService generationService;
    private final AuthenticatedUserIdResolver userIdResolver;

    public AiQuestionGenerationController(AiQuestionGenerationService generationService,
                                          AuthenticatedUserIdResolver userIdResolver) {
        this.generationService = generationService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generate(
            @PathVariable Long testId,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "text", required = false) String text,
            @RequestParam(name = "count", defaultValue = "5") int count,
            @RequestParam(name = "type", defaultValue = "MCQ") String type,
            @RequestParam(name = "difficulty", defaultValue = "medium") String difficulty,
            Authentication authentication) {
        if ((file == null || file.isEmpty()) && (text == null || text.isBlank())) {
            return badRequest(MSG_NO_MATERIAL);
        }
        try {
            Long userId = userIdResolver.resolve(authentication);
            return ResponseEntity.ok(AjaxResult.success(generationService.generate(
                    userId, testId, file, text,
                    new GenerateRequest(count, type, difficulty))));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AiClientException ex) {
            log.warn("AI question generation failed for test {}: {}", testId, ex.toString());
            return badRequest(MSG_AI_UNAVAILABLE);
        } catch (AccessDeniedException ex) {
            return forbidden();
        } catch (AuthenticationException ex) {
            return forbidden();
        } catch (EntityNotFoundException ex) {
            return notFound(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected AI question generation failure for test {}", testId, ex);
            return internalError();
        }
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> confirm(
            @PathVariable Long testId,
            @RequestBody(required = false) ConfirmRequest request,
            Authentication authentication) {
        try {
            Long userId = userIdResolver.resolve(authentication);
            return ResponseEntity.ok(AjaxResult.success(
                    generationService.confirm(userId, testId, request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (AccessDeniedException ex) {
            return forbidden();
        } catch (AuthenticationException ex) {
            return forbidden();
        } catch (EntityNotFoundException ex) {
            return notFound(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected AI question confirmation failure for test {}", testId, ex);
            return internalError();
        }
    }
}
