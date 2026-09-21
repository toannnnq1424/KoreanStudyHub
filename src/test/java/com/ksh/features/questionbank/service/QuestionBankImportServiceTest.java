package com.ksh.features.questionbank.service;

import com.ksh.entities.Subject;
import com.ksh.entities.User;
import com.ksh.features.admin.subjects.repository.SubjectRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.questionbank.imports.QuestionBankImportParser;
import com.ksh.features.questionbank.imports.QuestionBankImportParser.ParsedFile;
import com.ksh.features.questionbank.imports.QuestionBankImportParser.RawRow;
import com.ksh.features.questionbank.imports.QuestionBankImportSession;
import com.ksh.features.questionbank.imports.QuestionBankImportSessionStore;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.features.questionbank.repository.QuestionBankOptionRepository;
import com.ksh.features.library.repository.LessonTemplateRepository;
import com.ksh.entities.LessonTemplate;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBankImportServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
    private final QuestionBankAccessPolicy accessPolicy = mock(QuestionBankAccessPolicy.class);
    private final QuestionBankItemRepository itemRepository = mock(QuestionBankItemRepository.class);
    private final QuestionBankOptionRepository optionRepository = mock(QuestionBankOptionRepository.class);
    private final LessonTemplateRepository lessonRepository = mock(LessonTemplateRepository.class);
    private final QuestionBankImportParser parser = mock(QuestionBankImportParser.class);
    private final QuestionBankImportSessionStore sessionStore = mock(QuestionBankImportSessionStore.class);
    private final QuestionBankImportService service = new QuestionBankImportService(
            userRepository, subjectRepository, accessPolicy, itemRepository, optionRepository,
            lessonRepository, parser, sessionStore);

    private final User lecturer = mock(User.class);
    private final MockMultipartFile file = new MockMultipartFile("file", "bank.xlsx", "application/octet-stream", new byte[]{1});

    @BeforeEach
    void setUp() {
        Subject subject = new Subject("Tiếng Hàn 3.1.1", "KOR311", null, true);
        ReflectionTestUtils.setField(subject, "id", 5L);
        when(lecturer.getId()).thenReturn(7L);
        when(lecturer.getRole()).thenReturn(Role.LECTURER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(lecturer));
        when(accessPolicy.resolveSubjectId(lecturer)).thenReturn(5L);
        when(accessPolicy.canAccessSubject(lecturer, 5L)).thenReturn(true);
        when(subjectRepository.findById(5L)).thenReturn(Optional.of(subject));
        LessonTemplate lesson = mock(LessonTemplate.class);
        when(lesson.getId()).thenReturn(11L);
        when(lessonRepository.findBySubjectIdOrderByChapterOrderAscDisplayOrderAscTitleAsc(5L))
                .thenReturn(List.of(lesson));
        when(lessonRepository.findByIdAndSubjectId(11L, 5L)).thenReturn(Optional.of(lesson));
    }

    @Test
    void matching_subject_code_is_accepted_for_preview() {
        when(parser.parse(file)).thenReturn(parsed("KOR311"));

        QuestionBankImportSession session = service.previewUpload(7L, Role.LECTURER, file);

        assertThat(session.getSubjectId()).isEqualTo(5L);
        assertThat(session.toPreview().acceptedRows()).isEqualTo(1);
        assertThat(session.toPreview().confirmable()).isTrue();
        verify(sessionStore).save(any(QuestionBankImportSession.class));
    }

    @Test
    void cross_subject_code_is_a_blocking_preview_error() {
        when(parser.parse(file)).thenReturn(parsed("KOR321"));

        QuestionBankImportSession session = service.previewUpload(7L, Role.LECTURER, file);

        assertThat(session.toPreview().acceptedRows()).isZero();
        assertThat(session.toPreview().confirmable()).isFalse();
        assertThat(session.toPreview().rows().get(0).message()).contains("Mã môn phải là KOR311");
    }

    private ParsedFile parsed(String subjectCode) {
        RawRow row = new RawRow(2, subjectCode, "MCQ", "Câu hỏi", null,
                List.of("Đáp án A", "Đáp án B"), "A");
        return new ParsedFile("bank.xlsx", List.of(row));
    }

    @Test
    void mixedWorkbookKeepsOnlyValidRowsAndAllowsConfirmation() {
        when(parser.parse(file)).thenReturn(new ParsedFile("bank.xlsx", List.of(
                parsed("KOR311").rows().get(0), parsed("KOR321").rows().get(0))));
        var session = service.previewUpload(7L, Role.LECTURER, file);
        assertThat(session.toPreview().confirmable()).isTrue();
        assertThat(session.toPreview().acceptedRows()).isEqualTo(1);
        assertThat(session.toPreview().errorRows()).isEqualTo(1);
        assertThat(session.getItems()).hasSize(1);
        when(sessionStore.claim(session.getId(), 7L)).thenReturn(Optional.of(session));
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            var item = (com.ksh.features.questionbank.entity.QuestionBankItem) invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 99L);
            return item;
        });
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            var result = service.confirm(7L, Role.LECTURER, session.getId());
            assertThat(result.createdCount()).isEqualTo(1);
            verify(itemRepository, org.mockito.Mockito.times(1)).save(any());
            verify(optionRepository, org.mockito.Mockito.times(2)).save(any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
