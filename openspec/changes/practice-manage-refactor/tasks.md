# Danh sách đầu việc: Refactor Quản lý đề và Luồng làm bài (Practice Module Refactor)

> **Folder**: `openspec/changes/practice-manage-refactor/`
> **Ghi chú**: Đánh dấu `[ ]` cho các việc cần làm, `[x]` khi đã hoàn thành.

---

## Giai đoạn 1: Database & Entities
- [x] Tạo file migration Flyway gộp hoàn chỉnh `V16__practice_hub.sql` (loại bỏ V17-V29)
- [x] Tạo thực thể `PracticeTest.java`
- [x] Tạo thực thể `PracticeAttempt.java`
- [x] Tạo thực thể `PracticeAiAnalysisUsage.java`
- [x] Cập nhật `PracticeSection.java` (thêm trường `testId`)
- [x] Cập nhật `PracticeSet.java` (thêm trường `coverImageUrl`)
- [x] Tạo `PracticeTestRepository.java`
- [x] Tạo `PracticeAttemptRepository.java`
- [x] Tạo `PracticeAiAnalysisUsageRepository.java`
- [x] Đảm bảo ứng dụng compile và khởi chạy Flyway thành công

---

## Giai đoạn 2: Cải tạo trang Danh mục & Chi tiết Đề thi
- [ ] Thiết kế các DTO mới phục vụ hiển thị (`PracticeSetDetailPage`, `PracticeTestCardView`, `PracticeTestDetailPage`, `PracticeSkillCardView`, `TestScoreBoard`)
- [ ] Cập nhật controller `/practice/sets/{setId}` (hiển thị danh sách các `PracticeTest` dưới dạng timeline card kèm ảnh cover chuẩn)
- [ ] Cập nhật controller `/practice/sets/{setId}/tests/{testId}` (hiển thị danh sách các phần thi theo kỹ năng kèm lịch sử attempt tương ứng)
- [ ] Viết lại file template `set-detail.html`
- [ ] Viết lại file template `test-detail.html`

---

## Giai đoạn 3: Luồng làm bài & Xem kết quả theo Kỹ năng
- [ ] Xây dựng `PracticeAttemptService` quản lý khởi tạo, lưu tạm và submit bài làm (`PracticeAttempt`)
- [ ] Tạo `PracticeAttemptController` xử lý khởi tạo lượt làm bài theo kỹ năng riêng lẻ: `POST /practice/sets/{setId}/tests/{testId}/skills/{skill}/attempts`
- [ ] Điều chỉnh player (`player.html`) để chỉ nạp và hiển thị các câu hỏi thuộc Section của kỹ năng đang chọn
- [ ] Tạo `PracticeResultController` tự động điều hướng kết quả dựa theo kỹ năng của attempt:
  - `READING` / `LISTENING` → chuyển hướng sang `practice/rl-result`
  - `WRITING` / `SPEAKING` → chuyển hướng sang `practice/result`
- [ ] Chỉnh sửa template `rl-result.html` và `result.html` để nạp dữ liệu từ thực thể `PracticeAttempt` mới

---

## Giai đoạn 4: Hộp thoại Nộp bài tự luận & Hệ thống Quản lý hạn ngạch AI (10 lượt/ngày)
- [ ] Thiết kế `PracticeAiQuotaService` để kiểm tra và tiêu tốn hạn ngạch AI hàng ngày (tối đa 10 lượt/ngày/user, múi giờ Việt Nam, sử dụng pessimistic lock tránh race condition)
- [ ] Tích hợp Hộp thoại nộp bài (submit modal) trong player cho kỹ năng tự luận (Viết/Nói) hiển thị số lượt AI còn lại
- [ ] Cung cấp hai nút bấm trong modal:
  - "Lưu bài và chấm sau" (gọi `/attempts/{id}/submit-without-analysis`)
  - "Chấm bằng AI" (gọi `/attempts/{id}/submit-and-analyze`)
- [ ] Tạo `PracticeAiAnalysisController` phục vụ yêu cầu học viên chủ động bấm chấm điểm lại hoặc chấm muộn từ trang chi tiết kết quả

---

## Giai đoạn 5: Dọn dẹp mã nguồn & Bàn giao
- [ ] Vô hiệu hóa và deprecate các route cũ trong `PracticeController.java`
- [ ] Xóa bỏ các template rác không dùng (`detail.html`, `profile.html`, `upload.html`, `upload-preview.html`)
- [ ] Xóa bỏ các class kiểm thử rác đang chạy cho code cũ
- [ ] Viết Integration Tests kiểm thử toàn bộ các kịch bản nộp bài mới và kiểm tra hạn ngạch AI
- [ ] Xác nhận dự án chạy ổn định và đạt chỉ số test coverage > 90% cho các module nâng cấp

