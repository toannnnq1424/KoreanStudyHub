package com.ksh.features.flashcards.imports;

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
 * Builds the .xlsx template offered on the deck editor's Import Excel control:
 * a data sheet with a bold header row ("Mặt trước" | "Mặt sau") and twenty
 * example rows so the user sees the expected two-column layout.
 */
@Component
public class FlashcardImportTemplate {

    private static final String SHEET_NAME = "Thẻ";
    private static final String[] HEADERS = {"Mặt trước", "Mặt sau"};
    private static final String[][] SAMPLE_ROWS = {
            {"안녕하세요", "Xin chào (lịch sự)"},
            {"감사합니다", "Xin cảm ơn"},
            {"죄송합니다", "Xin lỗi"},
            {"네", "Vâng"},
            {"아니요", "Không"},
            {"학교", "Trường học"},
            {"학생", "Học sinh / sinh viên"},
            {"선생님", "Giáo viên"},
            {"책", "Sách"},
            {"도서관", "Thư viện"},
            {"집", "Nhà"},
            {"친구", "Bạn bè"},
            {"가족", "Gia đình"},
            {"물", "Nước"},
            {"밥", "Cơm / bữa ăn"},
            {"사과", "Quả táo"},
            {"오늘", "Hôm nay"},
            {"내일", "Ngày mai"},
            {"어제", "Hôm qua"},
            {"공부하다", "Học"},
    };

    /** Approximate cell width in POI units (256 = one character at default font). */
    private static final int COLUMN_WIDTH = 256 * 28;

    /**
     * Builds the .xlsx workbook and returns it as a byte array.
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
                sheet.setColumnWidth(i, COLUMN_WIDTH);
            }

            sheet.createFreezePane(0, 1);
            Sheet guide = workbook.createSheet("Hướng dẫn");
            guide.setColumnWidth(0, 100 * 256);
            guide.createRow(0).createCell(0).setCellValue("20 thẻ Hàn–Việt minh hoạ. Chỉ sheet đầu tiên được import; mỗi dòng cần đủ mặt trước và mặt sau.");
            guide.createRow(1).createCell(0).setCellValue("Kiểm tra bộ thẻ đích trước khi import lại để tránh thêm thẻ không mong muốn.");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
