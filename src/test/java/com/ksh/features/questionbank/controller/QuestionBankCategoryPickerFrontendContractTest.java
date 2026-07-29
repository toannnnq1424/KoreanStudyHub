package com.ksh.features.questionbank.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBankCategoryPickerFrontendContractTest {

    private static final Path FORM_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/form.html");
    private static final Path QUESTION_BANK_STYLES =
            Path.of("src/main/resources/static/css/question-bank.css");
    private static final Path COMBOBOX_SCRIPT =
            Path.of("src/main/resources/static/js/ksh-combobox.js");

    @Test
    void authoring_category_picker_uses_the_bounded_accessible_combobox() throws IOException {
        String form = Files.readString(FORM_TEMPLATE);
        String styles = Files.readString(QUESTION_BANK_STYLES);
        String combobox = Files.readString(COMBOBOX_SCRIPT);

        assertThat(form)
                .contains("th:href=\"@{/css/ksh-combobox.css}\"")
                .contains("id=\"categoryId\" th:field=\"*{categoryId}\" required")
                .contains("data-combobox")
                .contains("th:src=\"@{/js/ksh-combobox.js}\"");
        assertThat(styles)
                .contains(".qb-form .ksh-combo")
                .contains("max-width: none")
                .contains("max-height: min(260px, 45vh)");
        assertThat(combobox)
                .contains("input.setAttribute('role', 'combobox')")
                .contains("list.setAttribute('role', 'listbox')")
                .contains("input.setAttribute('aria-activedescendant', li.id)")
                .contains("event.key === 'ArrowDown'")
                .contains("event.key === 'Enter'")
                .contains("event.key === 'Escape'")
                .contains("input.addEventListener('click'")
                .contains("event.preventDefault()")
                .contains("root.appendChild(select)")
                .contains("select.dispatchEvent(new Event('change', { bubbles: true }))");
    }
}
