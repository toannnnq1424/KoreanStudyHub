package com.ksh.features.library.service;

import com.ksh.features.library.imports.SyllabusImportParser;
import com.ksh.features.library.imports.SyllabusImportTemplate;
import com.ksh.features.flashcards.imports.FlashcardImportParser;
import com.ksh.features.flashcards.imports.FlashcardImportTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;

class ImportTemplateRoundTripTest {
    @Test void admin_sample_has_twenty_rows_without_subject_dependencies() throws Exception {
        var file = new MockMultipartFile("file", "accounts.xlsx", "application/octet-stream", new com.ksh.features.admin.users.imports.parser.UserImportTemplateBuilder().build());
        var rows = new com.ksh.features.admin.users.imports.parser.UserRosterParser().parse(file).rows();
        assertThat(rows).hasSize(20);
        var validator = new com.ksh.features.admin.users.imports.validator.UserRosterRowValidator(
                org.mockito.Mockito.mock(com.ksh.features.auth.repository.UserRepository.class),
                org.mockito.Mockito.mock(com.ksh.features.admin.subjects.repository.SubjectRepository.class));
        assertThat(validator.validate(rows)).allSatisfy(row ->
                assertThat(row.getStatus()).isEqualTo(com.ksh.features.admin.users.imports.dto.UserImportRowStatus.CREATABLE));
        assertThat(rows).extracting(row -> row.email()).doesNotHaveDuplicates();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.role()).isIn("STUDENT", "LECTURER");
            assertThat(row.subject()).isNullOrEmpty();
            assertThat(row.phone()).isNullOrEmpty();
        });
    }
    @Test void korean_questions_round_trip_all_rows() throws Exception {
        var file = new MockMultipartFile("file", "questions.xlsx", "application/octet-stream", new com.ksh.features.questionbank.imports.QuestionBankImportTemplate().build("KOR111"));
        assertThat(new com.ksh.features.questionbank.imports.QuestionBankImportParser().parse(file).rows()).hasSize(15);
    }
    @Test void syllabus_sample_is_accepted_without_edits() throws Exception {
        var file = new MockMultipartFile("file", "syllabus.xlsx", "application/octet-stream", new SyllabusImportTemplate().build());
        assertThat(new SyllabusImportParser().parse(file)).hasSize(15);
    }
    @Test void flashcard_sample_is_accepted_without_edits() throws Exception {
        var file = new MockMultipartFile("file", "cards.xlsx", "application/octet-stream", new FlashcardImportTemplate().build());
        assertThat(new FlashcardImportParser().parse(file)).hasSize(20);
    }
}
