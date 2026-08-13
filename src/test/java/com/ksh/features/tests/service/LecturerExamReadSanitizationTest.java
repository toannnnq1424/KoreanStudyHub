package com.ksh.features.tests.service;

import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.tests.dto.LecturerTestDtos.ExamForm;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.QuestionOption;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.support.TestAccessResolver;
import com.ksh.features.upload.ExamImageStorageService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LecturerExamReadSanitizationTest {

    @Mock private TestRepository testRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private ClassRepository classRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private TestAccessResolver accessResolver;
    @Mock private TestActivityWriter activityWriter;
    @Mock private TakeViewBuilder takeViewBuilder;
    @Mock private ExamQuestionBankWriter questionBankWriter;
    @Mock private ExamQuestionBankPickerService questionBankPicker;
    @Mock private ExamImageStorageService examImageStorage;

    @InjectMocks private LecturerExamService service;

    @org.junit.jupiter.api.Test
    void getForEdit_sanitizesLegacyRichTextBeforeQuillHydration() {
        Test exam = org.mockito.Mockito.mock(Test.class);
        Question question = org.mockito.Mockito.mock(Question.class);
        QuestionOption option = org.mockito.Mockito.mock(QuestionOption.class);

        when(accessResolver.requireManageable(1L, 7L)).thenReturn(exam);
        when(exam.getId()).thenReturn(1L);
        when(exam.getDescription()).thenReturn(
                "<p onclick=\"alert(1)\">Passage</p><script>alert(2)</script>");
        when(exam.getSubjectId()).thenReturn(5L);
        when(exam.getType()).thenReturn(Test.TYPE_MOCK);
        when(exam.getStatus()).thenReturn(Test.STATUS_DRAFT);
        when(exam.getTimeMode()).thenReturn(Test.TIME_MODE_INDIVIDUAL);
        when(questionRepository.findByTestIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(question));
        when(question.getId()).thenReturn(11L);
        when(question.getQuestionType()).thenReturn(Question.TYPE_MCQ);
        when(question.getContent()).thenReturn(
                "<p onmouseover=\"alert(3)\">Question</p><iframe src=\"https://evil.test\"></iframe>");
        when(question.getExplanation()).thenReturn(
                "<a href=\"javascript:alert(4)\">Explanation</a>");
        when(question.getPoints()).thenReturn(BigDecimal.ONE);
        when(option.getId()).thenReturn(21L);
        when(option.getContent()).thenReturn(
                "<img src=\"/uploads/exams/safe.png\" onerror=\"alert(5)\"><b>Answer</b>");
        when(option.isCorrect()).thenReturn(true);
        when(questionBankWriter.loadOptions(List.of(question)))
                .thenReturn(Map.of(11L, List.of(option)));

        ExamForm form = service.getForEdit(1L, 7L);

        assertThat(form.description()).isEqualTo("<p>Passage</p>");
        assertThat(form.questions()).singleElement().satisfies(actual -> {
            assertThat(actual.content()).isEqualTo("<p>Question</p>");
            assertThat(actual.explanation()).isEqualTo("<a>Explanation</a>");
            assertThat(actual.options()).singleElement().satisfies(actualOption ->
                    assertThat(actualOption.content()).isEqualTo(
                            "<img src=\"/uploads/exams/safe.png\"><b>Answer</b>"));
        });
    }
}
