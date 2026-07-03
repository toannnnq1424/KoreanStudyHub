# Đề xuất: Kế hoạch kiểm thử & Chiến lược di cư dữ liệu (Practice Module Refactor)

> **Folder**: `openspec/changes/practice-manage-refactor/`
> **Trạng thái**: Đang đề xuất (Cấu trúc lại và gộp gãy gọn vào V16)

---

## 1. Kế hoạch di cư dữ liệu (Migration Strategy)

Để nâng cấp từ mô hình phẳng (`PracticeSet` → `PracticeSubmission`) lên mô hình phân cấp (`PracticeSet` → `PracticeTest` → `PracticeSection` → `PracticeAttempt`) mà không làm gián đoạn bài làm của học sinh hiện có, toàn bộ lịch sử migration phức tạp từ V16 đến V29 đã được gộp lại thành một tệp duy nhất để làm sạch dữ liệu:

### Giai đoạn 1: Gộp và chạy Flyway Migration V16
- Tạo mới tất cả các bảng dữ liệu mô-đun Practice dưới trạng thái cấu trúc hoàn thiện nhất (bao gồm `practice_tests`, `practice_attempts`, `practice_ai_analysis_usage` và các trường bổ sung).
- Loại bỏ hoàn toàn các file migration lẻ tẻ V17-V29 để giảm độ phức tạp trong quản lý schema.

### Giai đoạn 2: Tự động khởi tạo dữ liệu Seed (Backfill tự động)
- Chèn trực tiếp các dữ liệu câu hỏi, bài nghe và bài thi mẫu thông qua file `V16__practice_hub.sql`.
- Tự động sinh sẵn các bản ghi `PracticeTest` và `PracticeSection` liên kết đúng ngay từ đầu thay vì chạy các câu lệnh UPDATE/ALTER sau này.
- Đảm bảo các kết quả làm bài cũ (`PracticeSubmission`) vẫn có thể xem lại qua giao diện kết quả cũ (đảm bảo tính tương thích ngược).

### Giai đoạn 3: Cập nhật luồng nghiệp vụ Học viên
- Thay thế luồng tạo lượt làm bài trọn đề bằng luồng tạo lượt làm bài theo kỹ năng riêng biệt (`PracticeAttempt`).
- Cập nhật CBT Player để chỉ tải câu hỏi của Section thuộc kỹ năng đang làm bài thay vì tải toàn bộ đề.
- Thiết kế giao diện Kết quả mới: Tách biệt trang kết quả tự động chấm (Đọc/Nghe) tại `practice/rl-result` và kết quả tự luận (Viết/Nói) chấm bằng AI tại `practice/result`. Loại bỏ hoàn toàn cơ chế trung gian `result-shell`.

### Giai đoạn 4: Thu hồi tài nguyên rác (Cleanup)
- Vô hiệu hóa các endpoint và route redirect cũ (`legacy...`).
- Xoá bỏ các tệp template HTML không sử dụng (`detail.html`, `profile.html`, `upload.html`, `upload-preview.html`).

---

## 2. Kế hoạch kiểm thử (Test Plan)

### 2.1 Unit Tests (Độ bao phủ tối thiểu 90% cho phần thêm mới)

#### `PracticeAttemptServiceTest` (Mới)
- Test tạo thành công attempt ở trạng thái `IN_PROGRESS`.
- Test nộp bài trắc nghiệm tự động chấm điểm và ghi nhận trạng thái `SUBMITTED`.
- Test chấm điểm tự luận bằng AI: lưu trạng thái `GRADED`, cộng hạn ngạch.
- Test chặn tạo attempt khi chưa cấu hình đề thi hợp lệ.

#### `PracticeAiQuotaServiceTest` (Mới)
- Test kiểm tra hạn ngạch của học sinh trong ngày (Asia/Ho_Chi_Minh).
- Test giữ lượt (Reserve) thành công khi số lượt làm bài còn lại > 0.
- Test chặn giữ lượt khi học sinh đã sử dụng hết 10 lượt trong ngày.
- Test xử lý tranh chấp đồng thời (race condition) bằng khóa bi quan (pessimistic lock) hoặc giao dịch cô lập.

### 2.2 Integration Tests

#### `PracticeAttemptIntegrationTest`
- Giả lập luồng làm bài của học sinh: GET thông tin bài thi thử → POST tạo attempt Đọc → gửi đáp án qua API nộp bài → kiểm tra điểm số hiển thị trên trang `rl-result`.
- Kiểm thử luồng Viết: Tạo attempt Viết → nộp bài tự luận → kiểm tra API quota giảm xuống 1 → kiểm tra điểm và feedback hiển thị chi tiết trên trang `result`.
- Đảm bảo quyền truy cập: Học sinh không được gọi sang các API quản lý và dọn dẹp nháp của Giảng viên.

---

## 3. Các kịch bản kiểm thử thủ công (UI Manual Verification)
1.  **Làm bài trắc nghiệm độc lập**: Bấm làm bài Đọc, hoàn thành và nộp bài. Hệ thống phải điều hướng trực tiếp sang trang xem giải thích đáp án trắc nghiệm mà không hiển thị các kỹ năng khác.
2.  **Hộp thoại nộp bài tự luận hai tùy chọn**: Khi làm bài viết, bấm nộp bài phải hiển thị modal có 2 nút lựa chọn: "Lưu bài và chấm sau" (không tốn lượt AI) và "Chấm bằng AI" (tiêu tốn 1 lượt AI và hiển thị hạn ngạch còn lại).
3.  **Kiểm tra ảnh bìa**: Truy cập trang chi tiết bộ đề, kiểm tra ảnh bìa được render đúng từ URL cấu hình. Nếu không có ảnh, hệ thống phải render fallback gradient theo danh mục tương ứng.

