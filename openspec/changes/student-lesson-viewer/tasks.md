# Tasks: Student Lesson Viewer (KSH-4.1 / 4.2 / 4.3)

**Status:** 🔲 Ready for implementation  
**Sprint:** 4  
**Design doc:** [design.md](./design.md)  
**Spec:** [specs/student/spec.md](../../specs/student/spec.md)  
**Estimated:** ~2–3 ngày (1 dev full-stack)

---

## Checklist tổng quan

```
Phase 1 — Backend (no DB migration)          ~4–6h
Phase 2 — Frontend templates                 ~4–6h
Phase 3 — CSS + JavaScript                   ~2–3h
Phase 4 — Security review + tests            ~2–3h
```

---

## Phase 1 — Backend

### [DB] Không cần migration
> Reuse V13 (sections), V14 (lessons), V15 (lesson_attachments). Không có schema change.

---

### [BE-1] Thêm derived query vào `LessonRepository`

- **File:** `com.ksh.features.lessons.repository.LessonRepository`
- **Việc cần làm:**
  ```java
  List<Lesson> findBySectionIdAndStatusOrderByDisplayOrderAsc(Long sectionId, String status);
  ```
- **Acceptance:** `findBySectionIdAndStatusOrderByDisplayOrderAsc(sectionId, "PUBLISHED")` không trả về bài DRAFT. `@SQLRestriction("is_deleted = 0")` tự filter soft-deleted.
- **Người làm:** ___
- **Status:** `[ ]`

---

### [BE-2] Thêm `listPublishedForSection()` vào `LessonsService`

- **File:** `com.ksh.features.lessons.service.LessonsService`
- **Việc cần làm:** Thêm method dưới Javadoc:
  ```java
  /**
   * Student-safe list: chỉ trả về PUBLISHED lessons trong section.
   * Dùng getViewable (không phải getEditable) để student không bị block.
   */
  @Transactional(readOnly = true)
  public List<LessonRow> listPublishedForSection(Long classId, Long sectionId,
                                                  Long userId, Role role) { ... }
  ```
- **Lưu ý:** Method `toRow()` hiện là `private static` — cần đổi thành `package-private static` hoặc duplicate inline.
- **Acceptance:** Gọi với student enrolled → trả về PUBLISHED; bài DRAFT không xuất hiện.
- **Người làm:** ___
- **Status:** `[ ]`

---

### [BE-3] Thêm `getPublishedOrNull()` vào `LessonsService`

- **File:** `com.ksh.features.lessons.service.LessonsService`
- **Việc cần làm:**
  ```java
  /**
   * Trả về lesson PUBLISHED, hoặc null nếu không tồn tại / DRAFT / không có quyền.
   * Dùng cho ?lesson= inline panel — không ném exception.
   */
  @Transactional(readOnly = true)
  public Lesson getPublishedOrNull(Long classId, Long sectionId, Long lessonId,
                                    Long userId, Role role) { ... }
  ```
- **Acceptance:** `?lesson=<draftId>` → return null (panel absent). `?lesson=<wrongClassId>` → return null.
- **Người làm:** ___
- **Status:** `[ ]`

---

### [BE-4] Thêm `listForStudent()` vào `LessonAttachmentsService`

- **File:** `com.ksh.features.lessons.service.LessonAttachmentsService`
- **Việc cần làm:**
  ```java
  /**
   * Student-safe: list attachments nếu lesson PUBLISHED.
   * Trả về empty list nếu lesson là DRAFT (không 403).
   */
  @Transactional(readOnly = true)
  public List<LessonAttachmentRow> listForStudent(Long classId, Long sectionId,
                                                   Long lessonId, Long userId, Role role) { ... }
  ```
- **Acceptance:** Draft lesson → `List.of()`. PUBLISHED lesson, enrolled student → đầy đủ attachments.
- **Người làm:** ___
- **Status:** `[ ]`

---

### [BE-5] Tạo `StudentLessonsTabController`

- **File mới:** `com.ksh.features.lessons.controller.StudentLessonsTabController`
- **Route:** `GET /student/classes/{classId}/lessons`
- **Query params:** `?section={Long}` (optional), `?lesson={Long}` (optional)
- **Việc cần làm:** Xem skeleton trong [design.md §3.4](./design.md)
- **Acceptance criteria:**
  - `[ ]` Student enrolled → 200 với `sections` populated
  - `[ ]` `?section=99` (wrong class) → `selectedSection = null`, no 404
  - `[ ]` `?lesson=5` (PUBLISHED, correct class) → `inlineLesson` populated
  - `[ ]` `?lesson=5` (DRAFT) → `inlineLesson = null`
  - `[ ]` Student not enrolled → 403 (AccessDeniedException từ `getViewable`)
- **Người làm:** ___
- **Status:** `[ ]`

---

### [BE-6] Tạo `StudentLessonDetailController`

- **File mới:** `com.ksh.features.lessons.controller.StudentLessonDetailController`
- **Route:** `GET /student/classes/{classId}/sections/{sectionId}/lessons/{lessonId}`
- **Việc cần làm:** Xem skeleton trong [design.md §3.5](./design.md)
- **Acceptance criteria:**
  - `[ ]` PUBLISHED lesson, enrolled student → 200
  - `[ ]` DRAFT lesson → EntityNotFoundException → 404
  - `[ ]` Not enrolled → 403
  - `[ ]` Cross-class section → `verifySectionBelongsToClass` → 404
  - `[ ]` `lesson.contentRichtext == null` → template renders empty gracefully
- **Người làm:** ___
- **Status:** `[ ]`

---

### [BE-7] Kiểm tra Security Config

- **File:** `SecurityConfig` (hoặc tương đương trong `com.ksh.security` / `com.ksh.config`)
- **Việc cần làm:** Đảm bảo routes mới được permit đúng:
  - `/student/classes/**/lessons/**` → authenticated (không anonymous)
  - `/api/lessons/**/attachments/**/download` → đã permit (kiểm tra lại)
- **Acceptance:** Unauthenticated request → redirect `/login`. Authenticated non-enrolled → 403 từ service.
- **Người làm:** ___
- **Status:** `[ ]`

---

## Phase 2 — Frontend Templates

### [FE-1] Tạo `student/class-lessons.html`

- **File mới:** `src/main/resources/templates/student/class-lessons.html`
- **Tham chiếu:** [design.md §4.1](./design.md), fragment head từ `my-classes.html`
- **Checklist UI:**
  - `[ ]` `<head th:replace>` → `fragments/head` + link `student-lessons.css`
  - `[ ]` `<header th:replace>` → `fragments/app-header`
  - `[ ]` Section chips: "Tất cả" + 1 chip per `SectionRow`, active state khi `selectedSectionId` match
  - `[ ]` Lesson cards: title, publishedAt, no-DRAFT content, `data-lesson-id` attribute
  - `[ ]` Lesson card `href` = `?section={secId}&lesson={l.id}`
  - `[ ]` Empty state khi `lessons` empty: "Chọn chương để xem bài giảng." (no section) / "Chương này chưa có bài giảng nào." (section selected)
  - `[ ]` Inline panel `<aside id="lesson-panel">` với `th:classappend="'open'"` nếu `inlineLesson != null`
  - `[ ]` Panel: header title, close button `id="panel-close"`, richtext `th:utext`, attachment list, "Xem trang đầy đủ" link
  - `[ ]` Panel silently absent (không render `<aside>`) nếu `inlineLesson == null`
- **Người làm:** ___
- **Status:** `[ ]`

---

### [FE-2] Tạo `student/lesson-detail.html`

- **File mới:** `src/main/resources/templates/student/lesson-detail.html`
- **Tham chiếu:** [design.md §4.2](./design.md)
- **Checklist UI:**
  - `[ ]` `<head th:replace>` → `fragments/head` + link `student-lessons.css`
  - `[ ]` `<header th:replace>` → `fragments/app-header`
  - `[ ]` Breadcrumb: "Lớp học / Bài giảng / {section.title} / {lesson.title}" với links đúng
  - `[ ]` `<h1 th:text="${lesson.title}">` — 1 h1 duy nhất
  - `[ ]` Meta: publishedAt format `dd/MM/yyyy`, badge "Đã xuất bản"
  - `[ ]` `<div class="lesson-richtext" th:utext="${lesson.contentRichtext}">` — nếu null thì empty div
  - `[ ]` Section attachments `th:if="${!#lists.isEmpty(attachments)}"`:
    - file type icon by mimeType
    - filename, size formatted (KB/MB)
    - download `<a th:href="${att.downloadUrl}" download>`
  - `[ ]` Graceful nếu `contentRichtext` null: hiện "Bài giảng chưa có nội dung."
- **Người làm:** ___
- **Status:** `[ ]`

---

## Phase 3 — CSS + JavaScript

### [CSS-1] Tạo `student-lessons.css`

- **File mới:** `src/main/resources/static/css/student-lessons.css`
- **Checklist:**
  - `[ ]` `.section-chips`, `.section-chip`, `.section-chip.active` — chip nav
  - `[ ]` `.lesson-card` — card layout, hover border + shadow
  - `[ ]` `.lesson-card.inline-active` — highlighted khi đang xem inline
  - `[ ]` `.lesson-panel` — position fixed, right, height 100vh, translateX(100%)
  - `[ ]` `.lesson-panel.open` — translateX(0), transition 0.28s ease
  - `[ ]` `.panel-header` — flex between title + close button
  - `[ ]` `.lesson-richtext` — scopes Quill output (heading margins, pre, blockquote)
  - `[ ]` `.attachment-list`, `.attachment-list li` — file row layout
  - `[ ]` Mobile: `width: min(560px, 95vw)` cho panel, chips wrap trên màn nhỏ
- **Người làm:** ___
- **Status:** `[ ]`

---

### [JS-1] Tạo `student-lessons.js`

- **File mới:** `src/main/resources/static/js/student-lessons.js`
- **Checklist:**
  - `[ ]` Guard: `if (!document.getElementById('lesson-panel')) return;`
  - `[ ]` Mở panel nếu `URLSearchParams` có `?lesson=` khi page load
  - `[ ]` Close button click → `panel.classList.remove('open')` + `history.pushState` xoá `?lesson=`
  - `[ ]` Escape key → đóng panel (keyboard accessibility)
  - `[ ]` Phase 1: lesson card click = navigate bình thường (browser handles)
  - `[ ]` ARIA: `panel.setAttribute('aria-hidden', 'true/false')` khi toggle
- **Người làm:** ___
- **Status:** `[ ]`

---

## Phase 4 — Security Review + Tests

### [TEST-1] Unit test — `LessonsService.listPublishedForSection`

- `[ ]` Draft lesson không xuất hiện trong kết quả
- `[ ]` Soft-deleted lesson không xuất hiện
- `[ ]` Student không enrolled → `AccessDeniedException` propagated
- **Người làm:** ___
- **Status:** `[ ]`

---

### [TEST-2] Unit test — `LessonsService.getPublishedOrNull`

- `[ ]` DRAFT lesson → return null (không throw)
- `[ ]` Cross-class lesson (wrong sectionId) → return null
- `[ ]` AccessDeniedException inside → return null (swallowed)
- **Người làm:** ___
- **Status:** `[ ]`

---

### [TEST-3] Unit test — `LessonAttachmentsService.listForStudent`

- `[ ]` PUBLISHED lesson, enrolled → return attachments
- `[ ]` DRAFT lesson → return `List.of()` (không throw)
- **Người làm:** ___
- **Status:** `[ ]`

---

### [TEST-4] Integration / manual test — UI flow

| # | Scenario | Pass/Fail |
|---|----------|-----------|
| T1 | Login sv1@ksh.edu.vn → /student/classes/2/lessons → thấy sections | `[ ]` |
| T2 | Click section chip → lessons listed, DRAFT không hiện | `[ ]` |
| T3 | Click lesson card → panel mở, URL thêm `?lesson=` | `[ ]` |
| T4 | Click close / Escape → panel đóng, URL xoá `?lesson=` | `[ ]` |
| T5 | Paste deep-link `?section=X&lesson=Y` → panel mở ngay | `[ ]` |
| T6 | "Xem trang đầy đủ" → lesson-detail.html, breadcrumb đúng | `[ ]` |
| T7 | Download attachment từ detail page → file tải về | `[ ]` |
| T8 | Student không enroll → 403 | `[ ]` |
| T9 | Lesson DRAFT URL → 404 | `[ ]` |
| T10 | Mobile 375px → panel chiếm full width, không bị vỡ layout | `[ ]` |

---

## Definition of Done

- `[ ]` Tất cả task Phase 1–4 được check
- `[ ]` Không có regression trên trang lecturer lessons (SectionsController, LessonsController)
- `[ ]` Không có warning/error mới trong Spring log
- `[ ]` `mvn test` pass (nếu có test suite)
- `[ ]` Code review bởi ít nhất 1 thành viên khác
- `[ ]` Cập nhật `IMPLEMENTATION_ORDER.md` Sprint 4 → ✅ Done

---

## Assignment (Sprint 4)

| Phần | Người nhận | Deadline |
|------|-----------|----------|
| BE-1 → BE-7 | ___ | ___ |
| FE-1 (class-lessons.html) | ___ | ___ |
| FE-2 (lesson-detail.html) | ___ | ___ |
| CSS-1 + JS-1 | ___ | ___ |
| TEST-1 → TEST-4 | ___ | ___ |
