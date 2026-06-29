# Proposal: Student Lesson Viewer (KSH-4.1 / 4.2 / 4.3)

**Status:** 🔲 Proposed — Awaiting approval  
**Proposed by:** toannq1424  
**Date:** 2026-06-30  
**Sprint target:** Sprint 4  
**Spec reference:** [specs/student/spec.md](../../specs/student/spec.md)

---

## 1. Problem Statement

Sprint 3 đã hoàn thiện toàn bộ phía giảng viên: tạo/chỉnh sửa chương (Section),
bài giảng (Lesson), file đính kèm (Attachment). Tuy nhiên sinh viên hiện chưa có
giao diện nào để xem nội dung đó — tab Lessons trong student view vẫn là placeholder
hoặc không tồn tại.

**Gap cụ thể:**
- Không có route `/student/classes/{id}/lessons` cho student.
- `LessonsService.listForSection` yêu cầu `getEditable` (chỉ giảng viên) — chưa có
  phương thức tương đương cho sinh viên (chỉ đọc PUBLISHED).
- Không có template `student/class-lessons.html` hay `student/lesson-detail.html`.

---

## 2. Proposed Solution

Thêm **3 tính năng nhỏ liên tiếp**, mỗi cái build trên cái trước:

### Tính năng A — Danh sách bài giảng theo chương (KSH-4.1)

Trang `/student/classes/{classId}/lessons` hiển thị tất cả chương và bài giảng
đã **PUBLISHED** trong lớp học của sinh viên. Layout: chip chọn chương (trái/top),
danh sách bài giảng (chính). URL `?section={id}` pre-select chương.

### Tính năng B — Trang chi tiết bài giảng (KSH-4.2)

Trang `/student/classes/{classId}/sections/{sectionId}/lessons/{lessonId}` hiển thị
nội dung rich-text đầy đủ, danh sách file đính kèm có thể tải xuống, breadcrumb,
và nút Bài trước / Bài sau.

### Tính năng C — Xem nội dung inline qua `?lesson=` (KSH-4.3)

Khi người dùng nhấn vào một bài giảng từ trang danh sách, panel trượt vào từ
bên phải (slide-in) mà **không rời trang**. URL cập nhật thành
`?section={secId}&lesson={lessonId}` để deep-link + back-button hoạt động đúng.
Fallback không cần JS: href vẫn trỏ về trang detail đầy đủ.

---

## 3. Scope — In / Out

| In scope | Out of scope |
|----------|-------------|
| Student xem lessons PUBLISHED | Giảng viên xem/sửa lesson (đã done Sprint 3) |
| Download file đính kèm | Upload file (đã done Sprint 3) |
| Inline panel `?lesson=` | Video streaming / embedded player |
| Prev/Next navigation | Comment / discussion on lesson |
| Breadcrumb | Progress tracking (xem bài nào rồi) |
| Responsive mobile layout | Offline mode / PWA |

---

## 4. Affected Files

### Mới tạo (NEW)

| File | Loại | Mô tả |
|------|------|--------|
| `com.ksh.features.lessons.controller.StudentLessonsTabController` | Java Controller | Route GET `/student/classes/{id}/lessons` |
| `com.ksh.features.lessons.controller.StudentLessonDetailController` | Java Controller | Route GET `.../lessons/{lessonId}` |
| `templates/student/class-lessons.html` | Thymeleaf | Danh sách bài giảng theo chương |
| `templates/student/lesson-detail.html` | Thymeleaf | Trang chi tiết bài giảng |
| `static/css/student-lessons.css` | CSS | Styles cho section chips, lesson cards, inline panel |
| `static/js/student-lessons.js` | JavaScript | Logic mở/đóng panel, pushState, keyboard close |

### Sửa đổi (MODIFY)

| File | Lý do |
|------|-------|
| `com.ksh.features.lessons.service.LessonsService` | Thêm method `listPublishedForSection(classId, sectionId, userId, role)` |
| `com.ksh.features.lessons.service.LessonAttachmentsService` | Thêm method `listForStudent(classId, sectionId, lessonId, userId, role)` |
| `com.ksh.features.lessons.repository.LessonRepository` | Thêm derived query `findBySectionIdAndStatusOrderByDisplayOrderAsc` |
| `com.ksh.security.SecurityConfig` (hoặc tương đương) | Thêm permit cho `/student/classes/**/lessons/**` |

### Không cần thay đổi

- Schema DB (không cần migration mới — reuse V13/V14/V15)
- `SectionsService.listForClass` (đã dùng `getViewable` — student-safe)
- `LessonAttachmentsService.download` (đã có auth student cho PUBLISHED)

---

## 5. Risk & Dependencies

| Rủi ro | Mức độ | Giải pháp |
|--------|--------|-----------|
| Student đoán URL bài giảng DRAFT | Medium | Service gate `status == PUBLISHED` → 404 |
| `?lesson=` deep-link khi JS disabled | Low | Fallback: href trỏ KSH-4.2, trang đầy đủ |
| XSS trong `contentRichtext` | Low | `HtmlSanitizer` đã chạy khi save (Sprint 3); `th:utext` an toàn |
| Performance: N+1 query để đếm attachment | Low | Start với `attachmentCount = 0`, lazy add sau |

---

## 6. Acceptance Criteria (top-level)

- [ ] Sinh viên đã enroll thấy đủ bài giảng PUBLISHED, không thấy DRAFT.
- [ ] Sinh viên chưa enroll nhận 403 (không lộ thông tin lớp).
- [ ] Click bài giảng → panel trượt vào, URL cập nhật, back-button đóng panel.
- [ ] Link deep `?lesson=5` → panel mở ngay khi load trang.
- [ ] Tải file đính kèm hoạt động cho bài giảng PUBLISHED.
- [ ] Mobile: layout không bị vỡ (panel chiếm 95vw trên màn nhỏ).
