package com.ksh.features.questionbank.service;

import com.ksh.entities.Department;
import com.ksh.entities.LessonTemplate;
import com.ksh.entities.User;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.library.repository.LessonTemplateRepository;
import com.ksh.features.questionbank.dto.QuestionBankItemForm;
import com.ksh.features.questionbank.entity.QuestionBankItem;
import com.ksh.features.questionbank.entity.QuestionBankOption;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.features.questionbank.repository.QuestionBankOptionRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBankItemServiceSaveTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DepartmentRepository subjectRepository = mock(DepartmentRepository.class);
    private final QuestionBankAccessPolicy accessPolicy = mock(QuestionBankAccessPolicy.class);
    private final QuestionBankItemRepository itemRepository =
            mock(QuestionBankItemRepository.class);
    private final QuestionBankOptionRepository optionRepository =
            mock(QuestionBankOptionRepository.class);
    private final LessonTemplateRepository lessonRepository =
            mock(LessonTemplateRepository.class);

    private final QuestionBankItemService service = new QuestionBankItemService(
            userRepository,
            subjectRepository,
            accessPolicy,
            itemRepository,
            optionRepository,
            lessonRepository
    );

    @Test
    void save_withNewQuestion_returnsSavedQuestionIdAndSavesOptions() {
        User lecturer = user(10L, Role.LECTURER);
        QuestionBankItemForm form = form(null, 20L, 30L, "<p>안녕하세요 means?</p>");

        mockActorAndSubject(lecturer);
        LessonTemplate lessonTemplate = lesson(30L);
        when(lessonRepository.findByIdAndSubjectId(30L, 20L))
                .thenReturn(Optional.of(lessonTemplate));
        when(itemRepository.save(any(QuestionBankItem.class)))
                .thenAnswer(invocation -> {
                    QuestionBankItem item = invocation.getArgument(0);
                    ReflectionTestUtils.setField(item, "id", 100L);
                    return item;
                });

        Long result = service.save(10L, Role.LECTURER, form);

        assertThat(result).isEqualTo(100L);
        verify(optionRepository).deleteByItemIdIn(List.of(100L));
        verify(optionRepository, org.mockito.Mockito.times(2))
                .save(any(QuestionBankOption.class));
    }

    @Test
    void save_withExistingQuestion_updatesAndReturnsSameQuestionId() {
        User lecturer = user(10L, Role.LECTURER);
        QuestionBankItem existing = new QuestionBankItem(
                20L,
                30L,
                10L,
                QuestionBankItem.TYPE_MCQ,
                QuestionBankItem.STATUS_DRAFT,
                "<p>Old question</p>",
                null
        );
        ReflectionTestUtils.setField(existing, "id", 100L);
        QuestionBankItemForm form = form(100L, 20L, 30L, "<p>Updated question</p>");

        mockActorAndSubject(lecturer);
        LessonTemplate lessonTemplate = lesson(30L);
        when(lessonRepository.findByIdAndSubjectId(30L, 20L))
                .thenReturn(Optional.of(lessonTemplate));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(itemRepository.save(existing)).thenReturn(existing);

        Long result = service.save(10L, Role.LECTURER, form);

        assertThat(result).isEqualTo(100L);
        assertThat(existing.getContent()).isEqualTo("<p>Updated question</p>");
        verify(optionRepository).deleteByItemIdIn(List.of(100L));
    }

    @Test
    void save_withNoCorrectAnswer_throwsQuestionBankValidationException() {
        User lecturer = user(10L, Role.LECTURER);
        QuestionBankItemForm form = form(null, 20L, 30L, "<p>안녕하세요 means?</p>");
        form.getOptions().forEach(option -> option.setCorrect(false));

        mockActorAndSubject(lecturer);
        LessonTemplate lessonTemplate = lesson(30L);
        when(lessonRepository.findByIdAndSubjectId(30L, 20L))
                .thenReturn(Optional.of(lessonTemplate));

        assertThatThrownBy(() -> service.save(10L, Role.LECTURER, form))
                .isInstanceOf(QuestionBankValidationException.class);
    }

    private void mockActorAndSubject(User actor) {
        Department subject = subject(20L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(actor));
        when(subjectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(subject));
        when(subjectRepository.findById(20L)).thenReturn(Optional.of(subject));
        when(accessPolicy.canAccessSubject(actor, 20L)).thenReturn(true);
    }

    private static QuestionBankItemForm form(
            Long id,
            Long subjectId,
            Long lessonId,
            String content
    ) {
        QuestionBankItemForm form = new QuestionBankItemForm();
        form.setId(id);
        form.setSubjectId(subjectId);
        form.setLessonTemplateId(lessonId);
        form.setQuestionType(QuestionBankItem.TYPE_MCQ);
        form.setContent(content);
        form.setWorkflowAction(QuestionBankItem.STATUS_DRAFT);
        QuestionBankItemForm.OptionField correct = new QuestionBankItemForm.OptionField();
        correct.setContent("<p>Hello</p>");
        correct.setCorrect(true);
        QuestionBankItemForm.OptionField wrong = new QuestionBankItemForm.OptionField();
        wrong.setContent("<p>Goodbye</p>");
        wrong.setCorrect(false);
        form.setOptions(List.of(correct, wrong));
        return form;
    }

    private static User user(Long id, Role role) {
        User user = UserFactory.newAdminCreated(
                "lecturer@ksh.test",
                "encoded-password",
                "Lecturer User",
                role,
                true,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Department subject(Long id) {
        Department subject = mock(Department.class);
        when(subject.getId()).thenReturn(id);
        when(subject.isActive()).thenReturn(true);
        when(subject.getCode()).thenReturn("KOR101");
        return subject;
    }

    private static LessonTemplate lesson(Long id) {
        LessonTemplate lesson = mock(LessonTemplate.class);
        when(lesson.getId()).thenReturn(id);
        return lesson;
    }
}
