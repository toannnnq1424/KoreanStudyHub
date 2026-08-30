package com.ksh.features.admin.users.imports.parser;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Generates the current admin-account import workbook without a binary asset. */
@Component
public class UserImportTemplateBuilder {
    private static final String[] HEADERS = {
            "Email (bắt buộc)",
            "Họ và tên (trống = phần trước @)",
            "Vai trò (trống = STUDENT; STUDENT/LECTURER/ADMIN)",
            "Mã môn (mã hoặc tên; có thể trống)",
            "Số điện thoại (có thể trống)"
    };
    private static final String[][] SAMPLES = {
            {"student01@example.edu.vn", "Nguyễn Minh Anh", "STUDENT", "", "0901000001"},
            {"lecturer01@example.edu.vn", "Trần Thu Hà", "LECTURER", "KOR20", "0901000002"}
    };

    public byte[] build() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Tài khoản mới");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }
            for (int rowIndex = 0; rowIndex < SAMPLES.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < SAMPLES[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(SAMPLES[rowIndex][column]);
                }
            }
            int[] widths = {34, 34, 56, 38, 34};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
