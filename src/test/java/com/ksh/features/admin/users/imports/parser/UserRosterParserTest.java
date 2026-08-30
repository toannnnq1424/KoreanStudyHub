package com.ksh.features.admin.users.imports.parser;

import com.ksh.features.admin.users.imports.InvalidRosterFileException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRosterParserTest {

    private final UserRosterParser parser = new UserRosterParser();

    @Test
    void generatedTemplate_roundTripsBothSampleRows() throws Exception {
        byte[] workbook = new UserImportTemplateBuilder().build();

        UserRosterParser.ParsedRoster parsed = parser.parse(new MockMultipartFile(
                "file", "admin-account-import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbook));

        assertThat(parsed.fileName()).isEqualTo("admin-account-import-template.xlsx");
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0))
                .extracting(UserRosterParser.RawRosterRow::rowNumber,
                        UserRosterParser.RawRosterRow::email,
                        UserRosterParser.RawRosterRow::fullName,
                        UserRosterParser.RawRosterRow::role,
                        UserRosterParser.RawRosterRow::subject,
                        UserRosterParser.RawRosterRow::phone)
                .containsExactly(2, "student01@example.edu.vn", "Nguyễn Minh Anh",
                        "STUDENT", "", "0901000001");
        assertThat(parsed.rows().get(1).subject()).isEqualTo("KOR20");
    }

    @Test
    void parser_acceptsVietnameseAliasesAndSkipsCompletelyBlankRows() throws Exception {
        byte[] workbook;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Accounts");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Địa chỉ email");
            header.createCell(1).setCellValue("Họ tên");
            header.createCell(2).setCellValue("Vai trò");
            header.createCell(3).setCellValue("Mã môn");
            header.createCell(4).setCellValue("SĐT");
            sheet.createRow(1);
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("  MINJI@EXAMPLE.EDU.VN  ");
            row.createCell(1).setCellValue("Minji Kim");
            row.createCell(2).setCellValue("LECTURER");
            row.createCell(3).setCellValue("KOR20");
            row.createCell(4).setCellValue("0901234567");
            book.write(out);
            workbook = out.toByteArray();
        }

        UserRosterParser.ParsedRoster parsed = parser.parse(new MockMultipartFile(
                "file", "accounts.xlsx", "application/octet-stream", workbook));

        assertThat(parsed.rows()).singleElement().satisfies(row -> {
            assertThat(row.rowNumber()).isEqualTo(3);
            assertThat(row.email()).isEqualTo("MINJI@EXAMPLE.EDU.VN");
            assertThat(row.phone()).isEqualTo("0901234567");
        });
    }

    @Test
    void parser_rejectsMissingEmailHeaderAndFakeExcelPayload() throws Exception {
        byte[] workbook;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            book.createSheet("Accounts").createRow(0).createCell(0).setCellValue("Họ tên");
            book.write(out);
            workbook = out.toByteArray();
        }

        assertThatThrownBy(() -> parser.parse(new MockMultipartFile(
                "file", "missing-email.xlsx", "application/octet-stream", workbook)))
                .isInstanceOf(InvalidRosterFileException.class)
                .hasMessage("File phải có cột Email ở dòng tiêu đề.");
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile(
                "file", "fake.xlsx", "application/octet-stream", "not excel".getBytes())))
                .isInstanceOf(InvalidRosterFileException.class)
                .hasMessage("File không phải Excel .xlsx hoặc .xls hợp lệ.");
    }
}
