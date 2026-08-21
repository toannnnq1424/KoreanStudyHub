package com.ksh.features.admin.users.imports.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builds the .xlsx template streamed by the roster-import modal.
 *
 * <p>Separate from {@code ExcelTemplateBuilder} in the classes package, which
 * ships the student columns (Email / MSSV / Họ và tên / Số điện thoại). A user
 * account carries a role and a subject instead of a student id, so the two
 * templates cannot share a header row.
 *
 * <p>The template must round-trip: what this builder emits has to parse cleanly
 * back through {@code UserRosterParser}. {@code UserImportTemplateRoundTripTest}
 * guards that, because the header text here and the alias table there are
 * edited in different files and drift silently otherwise.
 */
@Component
public class UserImportTemplateBuilder {

    private static final String SHEET_NAME = "Tài khoản";

    /**
     * Headers carry their own rules in a trailing {@code (...)} annotation, so an
     * admin editing the file offline can see which columns are required without
     * going back to the modal. {@code UserRosterParser} strips that annotation
     * before matching, so this template imports back in unchanged — see
     * {@code UserRosterParser#resolveHeader}. Keep the annotation parenthesised
     * and keep the bare name in front of it, or that round-trip breaks.
     */
    private static final String[] HEADERS = {
            "Email (bắt buộc)",
            "Họ và tên (có thể để trống)",
            "Vai trò (trống = STUDENT; hoặc LECTURER/HEAD/ADMIN)",
            "Khoa/Bộ môn (có thể để trống)",
            "Số điện thoại (có thể để trống)"
    };

    /**
     * Two shapes on purpose: a fully-populated row, and one that leaves every
     * optional cell blank. The blank row is the point — filling every cell in
     * every sample quietly implies all of them are mandatory.
     *
     * <p>The subject sample uses the short code rather than the full name.
     * The validator accepts either, but a code survives an admin renaming the
     * subject, so the shipped sample keeps resolving.
     */
    private static final String[][] SAMPLE_ROWS = {
            {"nguyen.van.a@example.com", "Nguyễn Văn A", "LECTURER", "KOR311", "0901234567"},
            {"tran.thi.b@example.com",   "",             "",         "",     ""},
    };

    /**
     * Per-column width in POI units (256 = one character at the default font),
     * sized so each annotated header is readable without the admin having to
     * widen anything on open.
     */
    private static final int[] COLUMN_WIDTHS = {
            256 * 30,  // Email (bắt buộc)
            256 * 34,  // Họ và tên (có thể để trống)
            256 * 54,  // Vai trò — longest annotation, lists the accepted values
            256 * 36,  // Khoa/Bộ môn (có thể để trống)
            256 * 36   // Số điện thoại (có thể để trống)
    };

    /**
     * Builds the .xlsx workbook and returns it as a byte array. Callers forward
     * the result through an HTTP response without further processing.
     *
     * @throws IOException if the workbook fails to serialize
     */
    public byte[] build() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(SHEET_NAME);

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            for (int r = 0; r < SAMPLE_ROWS.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < SAMPLE_ROWS[r].length; c++) {
                    row.createCell(c).setCellValue(SAMPLE_ROWS[r][c]);
                }
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i]);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}