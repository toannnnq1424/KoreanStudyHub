# Proposal: Lessons & Materials Management (KSH-3.4, KSH-3.6)

**Status:** ✅ Approved (Completed)  
**Proposed by:** toannq1424  
**Date:** 2026-06-25  
**Sprint target:** Sprint 3  
**Spec reference:** [specs/classes/spec.md](../../specs/classes/spec.md)

---

## 1. Problem Statement

Giảng viên cần thiết lập hệ thống bài giảng khoa học theo từng chương (Section) và bài (Lesson), đi kèm nội dung học dạng rich-text (văn bản định dạng) và các tài liệu đính kèm (PDF, DOCX, ZIP, v.v.) để học sinh tải về học tập. Họ cũng cần kéo thả sắp xếp lại thứ tự bài giảng và chương học một cách dễ dàng.

---

## 2. Proposed Solution

Xây dựng hệ thống quản lý chương, bài giảng và tài liệu học tập:
1. **Chương học (Section - KSH-3.4-BE):** Giảng viên CRUD chương học và kéo thả sắp xếp lại thứ tự chương.
2. **Bài giảng (Lesson - KSH-3.4-BE2):** Giảng viên tạo/sửa bài giảng, soạn thảo bằng Quill Editor. Cho phép ẩn/hiện bài giảng (DRAFT/PUBLISHED) và ghi nhận lịch sử chỉnh sửa bài giảng.
3. **Tài liệu đính kèm (Material - KSH-3.4-BE3 & 3.6):** Giảng viên tải lên các file tài liệu đính kèm ngay trong bài giảng. File được lưu trữ trên local disk với cơ chế bảo mật chống tải trộm (chỉ cho học viên đã tham gia lớp tải về đối với bài giảng PUBLISHED).

---

## 3. Scope — In / Out

| In scope | Out of scope |
|----------|-------------|
| CRUD chương và bài giảng | Xem bài giảng ở giao diện học sinh (Sprint 4) |
| Soạn thảo rich-text bằng Quill Editor | Tích hợp công cụ dịch trực tiếp trên bài học |
| Drag-drop sắp xếp chương/bài bằng SortableJS | Slide bài giảng trình chiếu tương tác trực tiếp |
| Tải tài liệu PDF, DOCX, ZIP | Video streaming riêng biệt |
| Nhật ký thay đổi bài học (Auditing) | Tự động hóa tạo bài viết từ file tài liệu |

---

## 4. Affected Files

### Mới tạo (NEW) / Sửa đổi (MODIFY)

- `com.ksh.entities.Section` & `Lesson` - Ánh xạ chương học và bài học.
- `com.ksh.entities.LessonAttachment` - Ánh xạ các file đính kèm.
- `com.ksh.features.lessons.controller.SectionsController` & `SectionsApiController` - CRUD chương học.
- `com.ksh.features.lessons.controller.LessonsController` & `LessonsApiController` - CRUD bài học.
- `com.ksh.features.lessons.controller.LessonAttachmentsApiController` - Tải lên/xóa tài liệu.
- `com.ksh.features.lessons.service.SectionsService` & `LessonsService` - Xử lý nghiệp vụ chương/bài.
- `com.ksh.features.lessons.service.LessonAttachmentsService` - Nghiệp vụ tài liệu đính kèm.
- `templates/classes/detail-lessons.html` - Giao diện quản lý chương, bài giảng của Giảng viên.

---

## 5. Security Checklist

- [x] Lọc mã độc HTML (XSS) trên nội dung soạn thảo bằng `HtmlSanitizer` trước khi lưu vào DB.
- [x] Kiểm tra ba lớp quyền trước khi sửa đổi: Quyền sửa đổi lớp học -> Chương học thuộc lớp -> Bài học thuộc chương.
- [x] Cascade xóa tài liệu khỏi đĩa cứng và DB khi bài giảng tương ứng bị soft-delete để tránh rác máy chủ.
- [x] Khóa tệp tải về: endpoint tải tài liệu chỉ cho phép download khi học viên thuộc lớp học và bài giảng đã ở trạng thái PUBLISHED.
