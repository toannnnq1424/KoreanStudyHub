# Kế hoạch tái cấu trúc phòng thi TOPIK CBT theo Group-based Rendering

Tài liệu này đề xuất kế hoạch tái cấu trúc dữ liệu và giao diện phòng thi TOPIK từ dạng **danh sách phẳng (flat list)** sang dạng **nhóm câu hỏi (group-based)** khớp hoàn toàn cấu trúc đề thi TOPIK PDF chính thức.

---

## 1. Phân tích & Giải pháp kiến trúc

### A. Cơ sở dữ liệu (Database Schema)
*   **Bảng mới `practice_question_groups`**: Lưu thông tin của từng nhóm câu hỏi (ví dụ: `17-21`).
    *   `id` BIGINT AUTO_INCREMENT PRIMARY KEY
    *   `set_id` BIGINT NOT NULL (Foreign Key liên kết tới `practice_sets`)
    *   `group_label` VARCHAR(50) NOT NULL (ví dụ: "17-21", "1-4")
    *   `question_from` INT NOT NULL
    *   `question_to` INT NOT NULL
    *   `instruction` TEXT NULL (ví dụ: `[17~21] 다음을 듣고...`)
    *   `audio_url` VARCHAR(500) NULL (quản lý file âm thanh riêng cho nhóm nghe)
    *   `example_json` JSON NULL (lưu thông tin đề mẫu `<보기>`: label, content, choices, answer)
    *   `display_order` INT NOT NULL DEFAULT 0
*   **Liên kết bảng `practice_questions`**:
    *   Thêm cột `group_id` (Foreign Key liên kết tới `practice_question_groups.id`).

### B. Lớp thực thể & DTO (JPA & Java DTO)
*   **Tạo Entity mới `PracticeQuestionGroup.java`**: Khai báo bảng `practice_question_groups`.
*   **Cập nhật Entity `PracticeQuestion.java`**: Thêm trường `groupId` cùng getter/setter.
*   **Tạo DTO mới**:
    *   `ExampleBox`: Chứa `label`, `content`, `choices`, `answer`.
    *   `PracticeQuestionGroupRow`: Chứa thông tin nhóm và danh sách các câu hỏi thuộc nhóm `List<PracticeQuestionRow> questions`.
*   **Cập nhật `PracticeSetView`**:
    *   Thay thế `List<PracticeQuestionRow> questions` bằng `List<PracticeQuestionGroupRow> groups`.
    *   Thêm helper method `totalQuestions()` để đếm tổng số câu hỏi trong các nhóm.

### C. Logic xử lý & Tính tương thích ngược (Backward Compatibility)
*   Trong `PracticeService.getPractice(Long setId)`:
    *   Nếu tập đề đã có phân nhóm trong DB, ta map dữ liệu thành các `groups`.
    *   **Fallback Logic**: Nếu tập đề lịch sử chưa có phân nhóm (bảng `practice_question_groups` trống), hệ thống tự động gom nhóm bằng thuật toán trong Java (phân bổ theo số câu như trước: `1-2`, `3-4`, `5-8`...) và trả về DTO group-based chuẩn hóa. Điều này đảm bảo toàn bộ dữ liệu lịch sử hoạt động hoàn hảo mà không cần chạy script update DB phức tạp.

### D. Tinh chỉnh AI Generator (`PracticePdfQuestionGenerator.java`)
*   **Schema Schema mới**: AI sẽ trả về schema dạng `groups` thay vì `questions` phẳng.
*   **Prompt System**: Chỉ dẫn AI nhận diện các nhóm câu `[17-21]`, phân tích phần instruction, trích xuất cấu trúc đề mẫu `<보기>` vào trường `exampleBox` (bao gồm nội dung thoại, các phương án và đáp án mẫu), và trả về danh sách câu hỏi nằm trong nhóm đó.

### E. Giao diện người dùng (`player.html` & `practice.js` & `practice.css`)
*   **player.html**:
    *   Lặp trực tiếp qua các nhóm câu `view.groups()`.
    *   Sidebar trái hiển thị instruction của nhóm câu hiện tại + card ghi chú.
    *   Cột giữa: Hiển thị trình phát audio riêng của nhóm (nếu có `audioUrl`), hiển thị instruction, hiển thị khung ví dụ `<보기>` (exampleBox) được thiết kế viền nét đứt sang trọng, và lặp qua các câu hỏi thuộc nhóm hiện tại.
    *   MCQ option hiển thị vòng tròn (A, B, C, D) như trước.
    *   Học viên làm bài tập trung vào nhóm câu hiện tại.
*   **practice.js**:
    *   Logic điều hướng `showGroup` được đơn giản hóa: Hiển thị thẻ nhóm đề bài (`ksh-source-card` có `data-group`) ở cột trái và danh sách câu hỏi thuộc nhóm đó ở cột giữa.
    *   Cập nhật trạng thái màu sắc nút nhóm trong Drawer:
        *   `status-current`: Nhóm đang làm.
        *   `status-done`: Nhóm đã hoàn thành tất cả câu hỏi.
        *   `status-red`: Nhóm có đánh dấu review.
        *   `status-gray`: Nhóm chưa làm.

---

## 2. Kế hoạch thay đổi chi tiết các tệp tin

### [NEW] [V19__create_practice_question_groups.sql](file:///d:/Downloads/ksh/src/main/resources/db/migration/V19__create_practice_question_groups.sql)
*   Tạo bảng `practice_question_groups`, thêm cột `group_id` vào `practice_questions` và khóa ngoại tương ứng.

### [NEW] [PracticeQuestionGroup.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/entities/PracticeQuestionGroup.java)
*   Thực thể JPA mới đại diện cho bảng `practice_question_groups`.

### [NEW] [PracticeQuestionGroupRepository.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/repository/PracticeQuestionGroupRepository.java)
*   Repository JPA hỗ trợ truy vấn các nhóm câu hỏi của một tập đề.

### [MODIFY] [PracticeQuestion.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/entities/PracticeQuestion.java)
*   Thêm trường `groupId` (`group_id`) cùng getter/setter.

### [MODIFY] [PracticeDtos.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/dto/PracticeDtos.java)
*   Định nghĩa các record `ExampleBox` và `PracticeQuestionGroupRow`.
*   Tái cấu trúc `PracticeSetView` và `PracticePdfDraftView` sang dạng group-based.

### [MODIFY] [PracticePdfQuestionGenerator.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/ai/PracticePdfQuestionGenerator.java)
*   Cập nhật response schema của OpenAI và system/user prompt định dạng group JSON.

### [MODIFY] [PracticePdfImportService.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/service/PracticePdfImportService.java)
*   Cập nhật logic `previewDraft` và `saveDraft` để lưu cả nhóm câu hỏi vào DB.

### [MODIFY] [PracticeService.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/service/PracticeService.java)
*   Cập nhật `getPractice` để trả về dữ liệu nhóm và logic fallback gom nhóm tự động cho đề cũ.

### [MODIFY] [player.html](file:///d:/Downloads/ksh/src/main/resources/templates/practice/player.html)
*   Restructure template lặp qua các nhóm câu `groups` thay vì câu hỏi flat.
*   Hiển thị audio và `<보기>` mẫu đúng nhóm.

### [MODIFY] [practice.js](file:///d:/Downloads/ksh/src/main/resources/static/js/practice.js)
*   Cập nhật logic điều hướng active group và tính toán tiến độ hoàn thành theo nhóm câu hỏi thực tế.

---

## 3. Kế hoạch xác minh

### A. Kiểm tra tự động
*   Chạy biên dịch dự án: `.\mvnw.cmd clean compile`
*   Chạy unit tests: `.\mvnw.cmd clean test -Dtest=KshApplicationTests`

### B. Kiểm tra thủ công
1.  **DB Migration**: Xác thực Flyway migration V19 khởi tạo thành công và không xung đột.
2.  **Đề thi cũ**: Vào một bộ đề đọc/nghe đã có sẵn, kiểm tra xem hệ thống có tự động gom nhóm hiển thị đúng cấu trúc không.
3.  **Đề thi mới (Import PDF)**: Chạy thử PDF import để AI phân tích theo cấu trúc nhóm câu hỏi mới. Chỉnh sửa draft nhóm và lưu lại. Vào làm bài để kiểm tra audio, `<보기>` và danh sách câu hỏi hiển thị chuẩn chỉnh từng nhóm.
