package com.ksh.features.tests.service;

import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.questionbank.entity.QuestionBankItem;
import com.ksh.features.questionbank.entity.QuestionBankOption;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.features.questionbank.repository.QuestionBankOptionRepository;
import com.ksh.features.questionbank.service.QuestionBankAccessPolicy;
import com.ksh.features.tests.dto.LecturerTestDtos.BankItemSnapshot;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.support.TestAccessResolver;
import com.ksh.security.Role;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuestionBankPickerServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository subjectRepository;
    @Mock private QuestionBankAccessPolicy accessPolicy;
    @Mock private TestAccessResolver testAccessResolver;
    @Mock private ClassRepository classRepository;
    @Mock private QuestionBankItemRepository itemRepository;
    @Mock private QuestionBankOptionRepository optionRepository;

    @InjectMocks private ExamQuestionBankPickerService service;

    @org.junit.jupiter.api.Test
    void searchApproved_sanitizesLegacyRichTextBeforePickerJson() {
        User actor = org.mockito.Mockito.mock(User.class);
        Test exam = org.mockito.Mockito.mock(Test.class);
        Department subject = org.mockito.Mockito.mock(Department.class);
        QuestionBankItem item = org.mockito.Mockito.mock(QuestionBankItem.class);
        QuestionBankOption option = org.mockito.Mockito.mock(QuestionBankOption.class);

        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(actor.getId()).thenReturn(7L);
        when(actor.getRole()).thenReturn(Role.LECTURER);
        when(testAccessResolver.requireManageable(1L, 7L, Role.LECTURER)).thenReturn(exam);
        when(exam.getSubjectId()).thenReturn(5L);
        when(accessPolicy.canAccessSubject(actor, 5L)).thenReturn(true);
        when(subjectRepository.findById(5L)).thenReturn(Optional.of(subject));
        when(subject.getCode()).thenReturn("KOR101");
        when(itemRepository.findBySubjectIdAndWorkflowStatusInOrderByUpdatedAtDescIdDesc(
                5L, List.of(QuestionBankItem.STATUS_APPROVED))).thenReturn(List.of(item));
        when(item.getId()).thenReturn(31L);
        when(item.getQuestionType()).thenReturn(QuestionBankItem.TYPE_MCQ);
        when(item.getContent()).thenReturn(
                "<p onclick=\"alert(1)\">Question</p><script>alert(2)</script>");
        when(item.getExplanation()).thenReturn(
                "<a href=\"javascript:alert(3)\">Explanation</a>");
        when(optionRepository.findByItemIdInOrderBySortOrderAscIdAsc(List.of(31L)))
                .thenReturn(List.of(option));
        when(option.getItemId()).thenReturn(31L);
        when(option.getContent()).thenReturn(
                "<img src=\"/uploads/exams/safe.png\" onerror=\"alert(4)\"><i>Answer</i>");
        when(option.isCorrect()).thenReturn(true);

        List<BankItemSnapshot> results = service.searchApproved(
                7L, Role.LECTURER, 1L, null);

        assertThat(results).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.content()).isEqualTo("<p>Question</p>");
            assertThat(snapshot.explanation()).isEqualTo("<a>Explanation</a>");
            assertThat(snapshot.options()).singleElement().satisfies(snapshotOption ->
                    assertThat(snapshotOption.content()).isEqualTo(
                            "<img src=\"/uploads/exams/safe.png\"><i>Answer</i>"));
        });
    }
}
