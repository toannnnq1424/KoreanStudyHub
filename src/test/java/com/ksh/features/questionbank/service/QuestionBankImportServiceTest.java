package com.ksh.features.questionbank.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.questionbank.dto.QuestionBankImportDtos.Preview;
import com.ksh.features.questionbank.dto.QuestionBankViews.CategoryOption;
import com.ksh.features.questionbank.imports.QuestionBankImportParser;
import com.ksh.features.questionbank.imports.QuestionBankImportParser.ParsedFile;
import com.ksh.features.questionbank.imports.QuestionBankImportParser.RawRow;
import com.ksh.features.questionbank.imports.QuestionBankImportSession;
import com.ksh.features.questionbank.imports.QuestionBankImportSessionStore;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.features.questionbank.repository.QuestionBankOptionRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Focused unit coverage for question-bank Excel preview validation. */
class QuestionBankImportServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final QuestionBankAccessPolicy accessPolicy = mock(QuestionBankAccessPolicy.class);
    private final QuestionBankCategoryService categoryService = mock(QuestionBankCategoryService.class);
    private final QuestionBankItemRepository itemRepository = mock(QuestionBankItemRepository.class);
    private final QuestionBankOptionRepository optionRepository = mock(QuestionBankOptionRepository.class);
    private final QuestionBankImportParser importParser = mock(QuestionBankImportParser.class);
    private final QuestionBankImportSessionStore sessionStore = mock(QuestionBankImportSessionStore.class);
    private final QuestionBankImportService service = new QuestionBankImportService(
            userRepository,
            accessPolicy,
            categoryService,
            itemRepository,
            optionRepository,
            importParser,
            sessionStore);

    @Test
    void preview_explains_unknown_category_and_lists_active_department_choices_without_writing() {
        Long userId = 41L;
        Long departmentId = 7L;
        User lecturer = mock(User.class);
        MultipartFile file = mock(MultipartFile.class);
        RawRow row = new RawRow(
                2,
                "Giải tích 1",
                "MCQ",
                "Đạo hàm của x^2 là gì?",
                "",
                List.of("2x", "x"),
                "A");

        when(lecturer.getId()).thenReturn(userId);
        when(lecturer.getRole()).thenReturn(Role.LECTURER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(lecturer));
        when(accessPolicy.resolveDepartmentId(lecturer)).thenReturn(departmentId);
        when(accessPolicy.canAccessDepartment(lecturer, departmentId)).thenReturn(true);
        when(categoryService.activeOptionsFor(lecturer)).thenReturn(List.of(
                new CategoryOption(11L, "Kinh tế học", true),
                new CategoryOption(12L, "namdk", true)));
        when(importParser.parse(file)).thenReturn(new ParsedFile("questions.xlsx", List.of(row)));

        QuestionBankImportSession session = service.previewUpload(
                userId, Role.LECTURER, file);

        Preview preview = session.toPreview();
        assertThat(preview.acceptedRows()).isZero();
        assertThat(preview.errorRows()).isEqualTo(1);
        assertThat(preview.confirmable()).isFalse();
        assertThat(preview.rows().get(0).message())
                .contains(
                        "Giải tích 1",
                        "cột Danh mục",
                        "Kinh tế học",
                        "namdk");
        assertThat(session.getItems()).isEmpty();
        verify(sessionStore).save(session);
        verifyNoInteractions(itemRepository, optionRepository);
    }
}
