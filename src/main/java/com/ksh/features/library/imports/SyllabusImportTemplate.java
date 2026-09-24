package com.ksh.features.library.imports;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Generates the stable four-column workbook accepted by {@link SyllabusImportParser}. */
@Component
public class SyllabusImportTemplate {

    private static final String[] HEADERS = {
            "Số chương", "Tên chương", "Số bài", "Tên bài học"
    };
    private static final Object[][] EXAMPLES = {
            {1, "Hangeul và phát âm", 1, "Nguyên âm cơ bản"},
            {1, "Hangeul và phát âm", 2, "Phụ âm cơ bản"},
            {1, "Hangeul và phát âm", 3, "Ghép âm và phụ âm cuối"},
            {2, "Chào hỏi và giới thiệu", 4, "Cách chào hỏi lịch sự"},
            {2, "Chào hỏi và giới thiệu", 5, "Giới thiệu tên và quốc tịch"},
            {2, "Chào hỏi và giới thiệu", 6, "Nghề nghiệp và đuôi câu 입니다"},
            {3, "Đời sống hằng ngày", 7, "Đồ vật và trợ từ chủ ngữ"},
            {3, "Đời sống hằng ngày", 8, "Địa điểm và 있어요 / 없어요"},
            {3, "Đời sống hằng ngày", 9, "Hoạt động thường ngày"},
            {4, "Thời gian và mua sắm", 10, "Số đếm Hán Hàn"},
            {4, "Thời gian và mua sắm", 11, "Ngày tháng và giờ"},
            {4, "Thời gian và mua sắm", 12, "Hỏi giá và mua hàng"},
            {5, "Ăn uống và giao tiếp", 13, "Gọi món ăn"},
            {5, "Ăn uống và giao tiếp", 14, "Diễn đạt sở thích"},
            {5, "Ăn uống và giao tiếp", 15, "Hội thoại ôn tập tổng hợp"},
    };

    public byte[] build() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Syllabus");
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++) {
                header.createCell(column).setCellValue(HEADERS[column]);
                header.getCell(column).setCellStyle(headerStyle);
            }
            for (int rowIndex = 0; rowIndex < EXAMPLES.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue((Integer) EXAMPLES[rowIndex][0]);
                row.createCell(1).setCellValue((String) EXAMPLES[rowIndex][1]);
                row.createCell(2).setCellValue((Integer) EXAMPLES[rowIndex][2]);
                row.createCell(3).setCellValue((String) EXAMPLES[rowIndex][3]);
            }
            sheet.createFreezePane(0, 1);
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 34 * 256);
            sheet.setColumnWidth(2, 14 * 256);
            sheet.setColumnWidth(3, 46 * 256);
            Sheet guide = workbook.createSheet("Hướng dẫn");
            guide.setColumnWidth(0, 110 * 256);
            String[] notes = {
                "15 bài / 5 chương tiếng Hàn sơ cấp minh hoạ. Điều chỉnh theo syllabus của mã môn đang chọn.",
                "Giữ nguyên 4 cột trên sheet đầu. Số bài là duy nhất toàn môn, không bắt đầu lại từ 1 ở mỗi chương.",
                "Import cập nhật bài đã có cùng số bài: tên bài, chương và thứ tự có thể thay đổi; nội dung và tài nguyên được giữ.",
                "Số bài chưa tồn tại sẽ tạo bài mới. Import không tự xoá bài không có trong file.",
                "Chỉ người quản lý mã môn được import. Không import nguyên mẫu vào kho đang dùng nếu chưa kiểm tra số bài."
            };
            for (int i = 0; i < notes.length; i++) guide.createRow(i).createCell(0).setCellValue(notes[i]);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
