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
            {"ksh.sample.01@example.com", "Sinh viên mẫu 01", "STUDENT", "", ""},
            {"ksh.sample.02@example.com", "Sinh viên mẫu 02", "STUDENT", "", ""},
            {"ksh.sample.03@example.com", "Sinh viên mẫu 03", "STUDENT", "", ""},
            {"ksh.sample.04@example.com", "Sinh viên mẫu 04", "STUDENT", "", ""},
            {"ksh.sample.05@example.com", "Sinh viên mẫu 05", "STUDENT", "", ""},
            {"ksh.sample.06@example.com", "Sinh viên mẫu 06", "STUDENT", "", ""},
            {"ksh.sample.07@example.com", "Sinh viên mẫu 07", "STUDENT", "", ""},
            {"ksh.sample.08@example.com", "Sinh viên mẫu 08", "STUDENT", "", ""},
            {"ksh.sample.09@example.com", "Sinh viên mẫu 09", "STUDENT", "", ""},
            {"ksh.sample.10@example.com", "Sinh viên mẫu 10", "STUDENT", "", ""},
            {"ksh.sample.11@example.com", "Sinh viên mẫu 11", "STUDENT", "", ""},
            {"ksh.sample.12@example.com", "Sinh viên mẫu 12", "STUDENT", "", ""},
            {"ksh.sample.13@example.com", "Sinh viên mẫu 13", "STUDENT", "", ""},
            {"ksh.sample.14@example.com", "Sinh viên mẫu 14", "STUDENT", "", ""},
            {"ksh.sample.15@example.com", "Sinh viên mẫu 15", "STUDENT", "", ""},
            {"ksh.sample.16@example.com", "Giảng viên mẫu 16", "LECTURER", "", ""},
            {"ksh.sample.17@example.com", "Giảng viên mẫu 17", "LECTURER", "", ""},
            {"ksh.sample.18@example.com", "Giảng viên mẫu 18", "LECTURER", "", ""},
            {"ksh.sample.19@example.com", "Giảng viên mẫu 19", "LECTURER", "", ""},
            {"ksh.sample.20@example.com", "Giảng viên mẫu 20", "LECTURER", "", ""},
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
            Sheet guide = workbook.createSheet("Hướng dẫn");
            guide.setColumnWidth(0, 110 * 256);
            String[] notes = {
                "Mẫu gồm 20 tài khoản MỚI: 15 STUDENT và 5 LECTURER. Không tạo ADMIN trong dữ liệu mẫu.",
                "Email @example.com là dữ liệu minh hoạ, không phải hộp thư nhận được email kích hoạt.",
                "Để sử dụng thật, thay email bằng địa chỉ do bạn quản lý trước khi xác nhận import.",
                "Email phải chưa tồn tại. Import lại cùng danh sách không tạo tài khoản mới cho email đã có.",
                "Mã môn để trống để không phụ thuộc dữ liệu hệ thống; chỉ nhập mã môn hiện có nếu cần.",
                "Số điện thoại để trống, không dùng số điện thoại giả của người khác.",
                "Chỉ sheet đầu tiên được import. Kiểm tra kết quả xem trước trước khi xác nhận."
            };
            for (int i = 0; i < notes.length; i++) guide.createRow(i).createCell(0).setCellValue(notes[i]);
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
