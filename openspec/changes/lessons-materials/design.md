# Design: Lessons & Materials Management

**Status:** ✅ Approved (Completed)  
**Feature:** KSH-3.4, KSH-3.6  
**Sprint:** 3

---

## 1. Architecture Overview

```
User (Lecturer) ──► SectionsController ───────► SectionsService (Manage Chapters)
                ├──► LessonsController ────────► LessonsService (Manage Lessons)
                └──► LessonAttachmentsApiController ─► LessonAttachmentsService (Manage Files)
```

- **Drag-Reorder Flow:** Kéo thả sử dụng thư viện SortableJS ở giao diện Frontend, submit mảng JSON chứa danh sách `orderedIds` về các endpoint API tương ứng (`SectionsApiController` và `LessonsApiController`). Dịch vụ sắp xếp sẽ cập nhật lại số thứ tự `display_order` trong DB.
- **Sanitization:** Đầu vào HTML soạn thảo từ trình biên tập Quill được lọc qua thư viện `HtmlSanitizer` để loại bỏ các thẻ script độc hại hoặc sự kiện inline nguy hiểm trước khi lưu vào DB.
- **Xác thực tải file:** File đính kèm lưu tại máy chủ với tên ngẫu nhiên UUID. Endpoint tải file (`/api/lessons/{lessonId}/attachments/{attachmentId}/download`) kiểm tra quyền sở hữu lớp (giảng viên) hoặc quyền tham gia lớp học và bài giảng đó đã PUBLISHED (học viên) mới trả về Stream tải file.

---

## 2. Database Schema

### `sections` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `class_id` bigint NOT NULL (FK classes.id)
- `title` varchar(200) NOT NULL
- `display_order` short (thứ tự sắp xếp chương trong lớp)
- `is_deleted` tinyint(1) DEFAULT 0
- `created_by` bigint
- `created_at` timestamp DEFAULT CURRENT_TIMESTAMP

### `lessons` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `section_id` bigint NOT NULL (FK sections.id)
- `title` varchar(300) NOT NULL
- `status` varchar(20) DEFAULT 'DRAFT' (DRAFT, PUBLISHED)
- `display_order` short (thứ tự sắp xếp bài giảng trong chương)
- `content_richtext` longtext
- `is_deleted` tinyint(1) DEFAULT 0
- `created_by` bigint
- `published_at` timestamp NULL
- `created_at` timestamp DEFAULT CURRENT_TIMESTAMP

### `lesson_attachments` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `lesson_id` bigint NOT NULL (FK lessons.id)
- `original_filename` varchar(255) NOT NULL
- `stored_path` varchar(500) NOT NULL (đường dẫn tệp trên đĩa cứng)
- `mime_type` varchar(100)
- `size_bytes` bigint NOT NULL
- `uploaded_by` bigint
- `uploaded_at` timestamp DEFAULT CURRENT_TIMESTAMP

---

## 3. Storage Design (Lesson Materials)
Tệp đính kèm bài giảng được lưu trữ trên thư mục cục bộ của máy chủ: `uploads/lessons/attachments/`.
`LessonAttachmentStorageService` chịu trách nhiệm lưu tệp với UUID để chống trùng tên và trả về thông tin đường dẫn tệp.

---

## 4. UI Layout & Drag-Drop Design
- Trang quản lý bài giảng chia làm 2 cột: Cột bên trái hiển thị danh sách chương. Cột bên phải hiển thị danh sách bài giảng thuộc chương đang chọn.
- Cho phép chỉnh sửa tiêu đề chương dạng inline bằng double-click hoặc nhấn nút rename.
- Kéo thả sắp xếp lại thứ tự chương/bài bằng Sortable.js. Sau khi kết thúc thao tác kéo thả, danh sách ID mới được cập nhật tự động bằng AJAX gửi lên server.
- Sử dụng Quill.js editor để soạn thảo bài viết học tập.
- Phần quản lý tài liệu hiển thị dạng bảng nhỏ ở cuối form sửa bài học, cho phép chọn nhiều file kéo thả vào để upload bằng JS AJAX.
