package com.ksh.features.questionbank.imports;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBankImportTemplateTest {

    @Test
    void template_uses_subject_code_as_its_first_column_and_sample_scope() throws Exception {
        byte[] bytes = new QuestionBankImportTemplate().build("KOR311");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã môn");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("KOR311");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("KOR311");
        }
    }
}
