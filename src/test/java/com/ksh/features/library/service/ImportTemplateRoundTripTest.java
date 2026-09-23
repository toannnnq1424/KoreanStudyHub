package com.ksh.features.library.service;

import com.ksh.features.library.imports.SyllabusImportParser;
import com.ksh.features.library.imports.SyllabusImportTemplate;
import com.ksh.features.flashcards.imports.FlashcardImportParser;
import com.ksh.features.flashcards.imports.FlashcardImportTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;

class ImportTemplateRoundTripTest {
    @Test void syllabus_sample_is_accepted_without_edits() throws Exception {
        var file = new MockMultipartFile("file", "syllabus.xlsx", "application/octet-stream", new SyllabusImportTemplate().build());
        assertThat(new SyllabusImportParser().parse(file)).hasSize(4);
    }
    @Test void flashcard_sample_is_accepted_without_edits() throws Exception {
        var file = new MockMultipartFile("file", "cards.xlsx", "application/octet-stream", new FlashcardImportTemplate().build());
        assertThat(new FlashcardImportParser().parse(file)).hasSize(2);
    }
}
