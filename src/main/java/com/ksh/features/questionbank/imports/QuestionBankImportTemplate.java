package com.ksh.features.questionbank.imports;

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
import java.util.List;

/** Builds the .xlsx template used for subject Question Bank imports. */
@Component
public class QuestionBankImportTemplate {

    private static final String SHEET_ROWS = "Cau hoi";
    private static final String SHEET_GUIDE = "Huong dan";
    private static final String[] HEADERS = {
            "Mã môn", "Loại câu hỏi", "Nội dung câu hỏi", "Giải thích",
            "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D", "Đáp án E", "Đáp án F",
            "Đáp án đúng"
    };
    private static final String[][] SAMPLE_ROWS = {
            {null, "MCQ", "학교 có nghĩa là gì?", "학교 nghĩa là trường học.", "Trường học", "Bệnh viện", "Ngân hàng", "Nhà hàng", "", "", "A"},
            {null, "MCQ", "Chọn lời chào lịch sự.", "안녕하세요 là lời chào lịch sự thông dụng.", "안녕하세요", "미안해", "잘 자", "아니요", "", "", "A"},
            {null, "MCQ", "Điền: 저는 학생___.", "Danh từ kết hợp 입니다 trong văn phong trang trọng.", "입니다", "합니다", "갑니다", "먹습니다", "", "", "A"},
            {null, "MCQ", "책 có nghĩa là gì?", "책 là sách.", "Bút", "Sách", "Bàn", "Ghế", "", "", "B"},
            {null, "MCQ", "오늘 có nghĩa là gì?", "오늘 là hôm nay.", "Hôm qua", "Ngày mai", "Hôm nay", "Tuần sau", "", "", "C"},
            {null, "MCQ", "Chọn từ nghĩa là nước.", "물 là nước.", "밥", "빵", "우유", "물", "", "", "D"},
            {null, "MCQ", "Điền: 도서관___ 공부해요.", "에서 chỉ nơi diễn ra hành động.", "에서", "을", "은", "도", "", "", "A"},
            {null, "MCQ", "Chọn câu diễn đạt cảm ơn trang trọng.", "감사합니다 dùng để cảm ơn.", "안녕히 가세요", "감사합니다", "괜찮아요", "처음 뵙겠습니다", "", "", "B"},
            {null, "MCQ", "내일 có nghĩa là gì?", "내일 là ngày mai.", "Hôm nay", "Hôm qua", "Ngày mai", "Bây giờ", "", "", "C"},
            {null, "MCQ", "Chọn động từ nghĩa là học.", "공부하다 nghĩa là học.", "먹다", "자다", "가다", "공부하다", "", "", "D"},
            {null, "MR", "Chọn các từ chỉ người.", "학생 là sinh viên, 선생님 là giáo viên.", "학생", "선생님", "책", "물", "", "", "A,B"},
            {null, "MR", "Chọn các từ chỉ thời gian.", "오늘, 내일, 어제 là các mốc ngày.", "오늘", "학교", "내일", "어제", "", "", "A,C,D"},
            {null, "MR", "Chọn các địa điểm.", "학교, 도서관, 병원 là địa điểm.", "학교", "도서관", "병원", "친구", "", "", "A,B,C"},
            {null, "MR", "Chọn các động từ.", "먹다 và 읽다 là động từ.", "먹다", "사과", "읽다", "책", "", "", "A,C"},
            {null, "MR", "Chọn các loại đồ uống.", "물 là nước, 우유 là sữa.", "물", "책", "우유", "학교", "", "", "A,C"},
    };
    private static final String[] GUIDE_LINES = {
            "1. Dòng đầu tiên là tiêu đề, không được xoá hoặc đổi tên cột.",
            "2. Mã môn phải khớp chính xác với mã môn ACTIVE đã chọn khi tải file mẫu.",
            "3. Loại câu hỏi chỉ chấp nhận MCQ hoặc MR.",
            "4. Cần ít nhất hai đáp án không rỗng; có thể để trống đáp án E/F nếu không dùng.",
            "5. Cột 'Đáp án đúng' dùng chữ cái A-F, ngăn cách bằng dấu phẩy cho câu MR.",
            "6. MCQ phải có đúng một đáp án đúng; MR cần ít nhất một đáp án đúng.",
            "7. 15 câu tiếng Hàn sơ cấp minh hoạ; thay nội dung theo trình độ của môn đang chọn.",
            "8. Kiểm tra màn hình xem trước trước khi xác nhận; file mẫu không tự phát hành câu hỏi."
    };
    private static final int COLUMN_WIDTH = 256 * 24;
    private static final int CONTENT_WIDTH = 256 * 42;

    /** Builds the workbook and serializes it to bytes for HTTP download. */
    public byte[] build(String subjectCode) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            buildRowsSheet(workbook, headerStyle, subjectCode);
            buildGuideSheet(workbook, headerStyle);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buildRowsSheet(Workbook workbook,
                                CellStyle headerStyle,
                                String subjectCode) {
        Sheet sheet = workbook.createSheet(SHEET_ROWS);
        sheet.createFreezePane(0, 1);
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, i >= 2 && i <= 9 ? CONTENT_WIDTH : COLUMN_WIDTH);
        }
        for (int r = 0; r < SAMPLE_ROWS.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < SAMPLE_ROWS[r].length; c++) {
                row.createCell(c).setCellValue(c == 0 ? subjectCode : SAMPLE_ROWS[r][c]);
            }
        }
    }

    private void buildGuideSheet(Workbook workbook, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(SHEET_GUIDE);
        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Quy tắc import");
        titleCell.setCellStyle(headerStyle);
        sheet.setColumnWidth(0, 256 * 100);
        for (int i = 0; i < GUIDE_LINES.length; i++) {
            sheet.createRow(i + 1).createCell(0).setCellValue(GUIDE_LINES[i]);
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        style.setFont(bold);
        return style;
    }
}
