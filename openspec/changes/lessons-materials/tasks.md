# Tasks: Lessons & Materials Management

**Status:** 🔲 Ready for implementation (Simulated Plan)  
**Sprint:** 3  
**Design doc:** [design.md](./design.md)  
**Spec:** [specs/classes/spec.md](../../specs/classes/spec.md)

---

## Phase 1 — Database Schema & Migrations

- [ ] **[DB-1] Tạo bảng `sections`**
  - Viết file migration `V13__sections.sql` tạo bảng `sections` lưu trữ chương học.
  - Thiết lập unique constraint `uk_section_class_order`.

- [ ] **[DB-2] Tạo bảng `lessons`**
  - Viết file migration `V14__lessons.sql` tạo bảng `lessons` để lưu nội dung bài học.
  - Thiết lập unique constraint `uk_lesson_section_order`.

- [ ] **[DB-3] Tạo bảng `lesson_attachments`**
  - Viết file migration `V15__lesson_attachments.sql` để lưu thông tin file tài liệu đính kèm.

---

## Phase 2 — Backend Services

- [ ] **[BE-1] Xử lý Chương học (Sections)**
  - Viết controller `SectionsController` phục vụ giao diện và `SectionsApiController` xử lý API JSON.
  - Viết `SectionsService` thực hiện CRUD và `SectionsReorderService` xử lý logic kéo thả.
  - Ghi nhận audit log thông qua `SectionActivityWriter`.

- [ ] **[BE-2] Xử lý Bài học (Lessons)**
  - Viết controller `LessonsController`, `LessonsApiController` và `LessonsLifecycleController`.
  - Thực hiện các nghiệp vụ CRUD bài học, lưu bản nháp, xuất bản bài học.
  - Lọc XSS bằng `HtmlSanitizer` khi lưu nội dung rich-text của bài học.

- [ ] **[BE-3] Quản lý File tài liệu đính kèm**
  - Viết `LessonAttachmentsApiController` và `LessonAttachmentsService`.
  - Thực hiện tải tệp tin lên máy chủ thông qua `LessonAttachmentStorageService`, lưu trữ dưới tên file UUID.
  - Ràng buộc quyền tải xuống đối với học viên trong lớp và bài học đã PUBLISHED.

---

## Phase 3 — Frontend Views & Integration

- [ ] **[FE-1] Giao diện quản lý Chương và Bài giảng**
  - Thiết kế trang `classes/detail-lessons.html` chia làm 2 cột.
  - Tích hợp thư viện `Sortable.js` cho phép kéo thả reorder chương/bài.

- [ ] **[FE-2] Màn hình Soạn thảo / Sửa bài học**
  - Tạo trang sửa bài học dạng form toàn trang.
  - Tích hợp trình soạn thảo `Quill.js` editor cho trường nhập nội dung bài giảng.
  - Nhúng bảng danh sách tài liệu đính kèm bên dưới bài soạn thảo, hỗ trợ tải file lên bằng AJAX.

---

## Phase 4 — Security & Testing

- [ ] **[Test-1] Kiểm thử kéo thả (Reorder)**
  - Kiểm tra xem việc gửi danh sách mảng ID mới qua API có cập nhật đúng thứ tự `display_order` trong DB hay không.
- [ ] **[Test-2] Kiểm thử bảo mật tải file**
  - Học sinh lớp khác truy cập vào link download của file đính kèm phải bị trả về lỗi 403 Forbidden.
  - Học sinh trong lớp truy cập vào link download của bài giảng DRAFT phải bị trả về lỗi 403 Forbidden.
- [ ] **[Test-3] Kiểm thử cascade xóa bài học**
  - Kiểm tra xem khi xóa bài học thì các tệp đính kèm tương ứng trên đĩa cứng có thực sự bị xóa đi hay không.
