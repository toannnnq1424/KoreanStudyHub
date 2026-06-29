# Design: Student Lesson Viewer

**Status:** 🔲 Draft  
**Feature:** KSH-4.1 / 4.2 / 4.3  
**Sprint:** 4  
**Depends on:** Sprint 3 — sections (V13), lessons (V14), lesson_attachments (V15) ✅ Done

---

## 1. Architecture Overview

```
Browser
  │
  ├─ GET /student/classes/{classId}/lessons[?section=][?lesson=]
  │       │
  │       └─► StudentLessonsTabController
  │                 │  ClassesService.getViewable(classId, userId, role)
  │                 │  SectionsService.listForClass(classId, ...)        ← đã có, đã student-safe
  │                 │  LessonsService.listPublishedForSection(...)        ← NEW
  │                 │  [if ?lesson=] LessonsService.getPublishedOrNull() ← NEW
  │                 │  [if ?lesson=] LessonAttachmentsService.listForStudent() ← NEW
  │                 └─► student/class-lessons.html
  │                           + panel HTML (hidden if inlineLesson == null)
  │
  └─ GET /student/classes/{classId}/sections/{sectionId}/lessons/{lessonId}
          │
          └─► StudentLessonDetailController
                    │  ClassesService.getViewable(...)
                    │  reorderService.verifySectionBelongsToClass(...)
                    │  lessonRepository.findByIdAndSectionId(...)
                    │  [gate] lesson.status == PUBLISHED → 404 if not
                    │  LessonAttachmentsService.listForStudent(...)       ← NEW
                    └─► student/lesson-detail.html
```

---

## 2. Package & File Placement

Theo cấu trúc hiện tại của dự án:

```
com.ksh.features.lessons.controller/
  ├── LessonsController.java              (lecturer CRUD — ✅ existing)
  ├── LessonsApiController.java           (lecturer JSON API — ✅ existing)
  ├── LessonsLifecycleController.java     (publish/unpublish — ✅ existing)
  ├── LessonsTabController.java           (lecturer tab view — ✅ existing)
  ├── SectionsController.java             (✅ existing)
  ├── SectionsApiController.java          (✅ existing)
  ├── LessonAttachmentsApiController.java (✅ existing)
  ├── StudentLessonsTabController.java    ◄─ [NEW] KSH-4.1
  └── StudentLessonDetailController.java  ◄─ [NEW] KSH-4.2

com.ksh.features.lessons.service/
  ├── LessonsService.java                 ← ADD listPublishedForSection()
  │                                       ← ADD getPublishedOrNull()
  ├── LessonAttachmentsService.java       ← ADD listForStudent()
  └── ... (rest unchanged)

com.ksh.features.lessons.repository/
  └── LessonRepository.java              ← ADD derived query (PUBLISHED filter)

src/main/resources/templates/student/
  ├── my-classes.html                    (✅ existing)
  ├── join-class.html                    (✅ existing)
  ├── class-lessons.html                 ◄─ [NEW] KSH-4.1 + 4.3
  └── lesson-detail.html                 ◄─ [NEW] KSH-4.2

src/main/resources/static/css/
  └── student-lessons.css                ◄─ [NEW]

src/main/resources/static/js/
  └── student-lessons.js                 ◄─ [NEW]
```

---

## 3. New & Modified Classes — Detailed

### 3.1 `LessonRepository` — Thêm 1 derived query

```java
// Thêm vào interface LessonRepository extends JpaRepository<Lesson, Long>

/**
 * Returns PUBLISHED lessons of a section in authored order — student-safe read.
 * @SQLRestriction("is_deleted = 0") trên entity tự filter soft-deleted rows.
 */
List<Lesson> findBySectionIdAndStatusOrderByDisplayOrderAsc(Long sectionId, String status);
```

> **Tại sao không dùng JPQL?** Derived query đủ đơn giản và hưởng lợi từ
> `@SQLRestriction` tự động. Không cần `@Query`.

---

### 3.2 `LessonsService` — Thêm 2 phương thức

```java
/**
 * Student-safe list: chỉ trả về lessons có status = PUBLISHED.
 * Gọi getViewable thay vì getEditable để student không bị chặn.
 */
@Transactional(readOnly = true)
public List<LessonRow> listPublishedForSection(Long classId, Long sectionId,
                                               Long userId, Role role) {
    classesService.getViewable(classId, userId, role);
    reorderService.verifySectionBelongsToClass(sectionId, classId);
    return lessonRepository
            .findBySectionIdAndStatusOrderByDisplayOrderAsc(sectionId, Lesson.STATUS_PUBLISHED)
            .stream().map(LessonsService::toRow).toList();
    // Note: toRow() là private static, cần nâng lên package-private
    // hoặc duplicate nếu muốn giữ private.
}

/**
 * Trả về lesson PUBLISHED theo id+section, null nếu không tồn tại hoặc là DRAFT.
 * Dùng cho ?lesson= inline panel — không ném exception, chỉ trả null.
 */
@Transactional(readOnly = true)
public Lesson getPublishedOrNull(Long classId, Long sectionId, Long lessonId,
                                 Long userId, Role role) {
    try {
        classesService.getViewable(classId, userId, role);
        reorderService.verifySectionBelongsToClass(sectionId, classId);
        return lessonRepository.findByIdAndSectionId(lessonId, sectionId)
                .filter(l -> Lesson.STATUS_PUBLISHED.equals(l.getStatus()))
                .orElse(null);
    } catch (Exception e) {
        return null; // Graceful: stale URL, wrong class, not enrolled → silently absent
    }
}
```

> **Lưu ý về `toRow()`:** Hiện là `private static` trong `LessonsService`.
> Giải pháp đơn giản: tạo 1 package-private helper static trong cùng class,
> hoặc inline `new LessonRow(l.getId(), l.getTitle(), l.getStatus(), l.getDisplayOrder())`.

---

### 3.3 `LessonAttachmentsService` — Thêm 1 phương thức

```java
/**
 * Student-safe: liệt kê attachments, chỉ khi lesson đã PUBLISHED.
 * Khác listForLesson() (dùng getEditable) — đây dùng getViewable.
 */
@Transactional(readOnly = true)
public List<LessonAttachmentRow> listForStudent(Long classId, Long sectionId,
                                                Long lessonId, Long userId, Role role) {
    classesService.getViewable(classId, userId, role);
    reorderService.verifySectionBelongsToClass(sectionId, classId);
    Lesson lesson = loadLesson(sectionId, lessonId);  // throws 404 if not found
    if (!LESSON_STATUS_PUBLISHED.equals(lesson.getStatus())) {
        return List.of(); // Draft → empty list, không 403
    }
    return mapRows(attachmentRepository.findByLessonIdOrderByUploadedAtAsc(lessonId));
}
```

---

### 3.4 `StudentLessonsTabController` — Controller mới

```java
package com.ksh.features.lessons.controller;

@Controller
@RequestMapping("/student/classes/{classId}/lessons")
@PreAuthorize("hasAnyRole('STUDENT','LECTURER','HEAD','ADMIN')")
public class StudentLessonsTabController {

    // ── Constants
    private static final String VIEW = "student/class-lessons";

    // ── Dependencies (inject via constructor)
    // ClassesService, SectionsService, LessonsService,
    // LessonAttachmentsService, SectionRepository

    @GetMapping
    public String renderLessonsTab(
            @PathVariable Long classId,
            @RequestParam(required = false) Long section,
            @RequestParam(required = false) Long lesson,   // ?lesson= inline
            @AuthenticationPrincipal KshUserDetails user,
            Model model) {

        // 1. Auth: enrolled check
        ClassEntity clazz = classesService.getViewable(classId, user.getId(), user.getRole());

        // 2. Sections (all, student-safe — SectionsService.listForClass dùng getViewable)
        List<SectionRow> sections = sectionsService.listForClass(classId, user.getId(), user.getRole());

        // 3. Selected section validation
        Section selectedSection = null;
        if (section != null) {
            selectedSection = sectionRepository.findByIdAndClassId(section, classId).orElse(null);
        }

        // 4. Lessons (PUBLISHED only)
        List<LessonRow> lessons = selectedSection != null
                ? lessonsService.listPublishedForSection(classId, selectedSection.getId(),
                        user.getId(), user.getRole())
                : Collections.emptyList();

        // 5. Inline lesson (KSH-4.3)
        Lesson inlineLesson = null;
        List<LessonAttachmentRow> inlineAttachments = List.of();
        if (lesson != null && selectedSection != null) {
            inlineLesson = lessonsService.getPublishedOrNull(classId, selectedSection.getId(),
                    lesson, user.getId(), user.getRole());
            if (inlineLesson != null) {
                inlineAttachments = attachmentsService.listForStudent(
                        classId, selectedSection.getId(), lesson,
                        user.getId(), user.getRole());
            }
        }

        // 6. Model
        model.addAttribute("clazz", clazz);
        model.addAttribute("activeTab", "lessons");
        model.addAttribute("sections", sections);
        model.addAttribute("selectedSectionId", selectedSection != null ? selectedSection.getId() : null);
        model.addAttribute("selectedSection", selectedSection);
        model.addAttribute("lessons", lessons);
        model.addAttribute("inlineLesson", inlineLesson);
        model.addAttribute("inlineAttachments", inlineAttachments);

        return VIEW;
    }
}
```

---

### 3.5 `StudentLessonDetailController` — Controller mới

```java
package com.ksh.features.lessons.controller;

@Controller
@RequestMapping("/student/classes/{classId}/sections/{sectionId}/lessons")
@PreAuthorize("hasAnyRole('STUDENT','LECTURER','HEAD','ADMIN')")
public class StudentLessonDetailController {

    private static final String VIEW = "student/lesson-detail";

    @GetMapping("/{lessonId}")
    public String renderDetail(
            @PathVariable Long classId,
            @PathVariable Long sectionId,
            @PathVariable Long lessonId,
            @AuthenticationPrincipal KshUserDetails user,
            Model model) {

        ClassEntity clazz = classesService.getViewable(classId, user.getId(), user.getRole());
        Section section = sectionRepository.findByIdAndClassId(sectionId, classId)
                .orElseThrow(() -> new EntityNotFoundException("Chương không tồn tại"));

        Lesson lesson = lessonRepository.findByIdAndSectionId(lessonId, sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Bài giảng không tồn tại"));

        // Gate: DRAFT → treat as not found
        if (!Lesson.STATUS_PUBLISHED.equals(lesson.getStatus())) {
            throw new EntityNotFoundException("Bài giảng không tồn tại");
        }

        List<LessonAttachmentRow> attachments = attachmentsService.listForStudent(
                classId, sectionId, lessonId, user.getId(), user.getRole());

        model.addAttribute("clazz", clazz);
        model.addAttribute("section", section);
        model.addAttribute("lesson", lesson);
        model.addAttribute("attachments", attachments);
        model.addAttribute("activeTab", "lessons");

        return VIEW;
    }
}
```

---

## 4. Template Design

### 4.1 `student/class-lessons.html`

```
Head: include app-shell.css + student-lessons.css
Body:
  <header> → app-header fragment
  <div class="class-detail-layout">          ← reuse từ lecturer views
    <aside> class-sidebar với activeTab=lessons
    <main>
      <!-- Section chips (KSH-4.1) -->
      <nav class="section-chips">
        <a chip "Tất cả" active nếu selectedSectionId==null>
        <a th:each chip cho mỗi SectionRow>
      </nav>

      <!-- Lesson list (KSH-4.1) -->
      <div th:if="${lessons.isEmpty()}"> empty state </div>
      <div th:each="l : ${lessons}" class="lesson-card"
           th:classappend="${l.id == inlineLesson?.id} ? 'inline-active'"
           th:href="?section={secId}&lesson={l.id}">
        icon + title + meta + attachment badge (if count > 0)
      </div>

      <!-- Inline panel (KSH-4.3 — server rendered, JS toggles .open class) -->
      <aside id="lesson-panel" class="lesson-panel"
             th:classappend="${inlineLesson != null} ? 'open'">
        th:if="${inlineLesson != null}"
          panel-header: title + close button
          lesson-richtext: th:utext="${inlineLesson.contentRichtext}"
          attachments: ul.attachment-list
          link → full detail page (KSH-4.2)
      </aside>
  </div>
<script> student-lessons.js </script>
```

### 4.2 `student/lesson-detail.html`

```
Head: include app-shell.css + student-lessons.css
Body:
  <header> → app-header fragment
  <div class="class-detail-layout">
    <aside> class-sidebar với activeTab=lessons
    <main>
      Breadcrumb: Lớp > Bài giảng > {section.title} > {lesson.title}
      <h1 th:text="${lesson.title}">
      <p class="meta"> publishedAt, status badge
      <div class="lesson-richtext" th:utext="${lesson.contentRichtext}">
      <section class="attachments" th:if="${!attachments.isEmpty()}">
        <h4>Tài liệu đính kèm</h4>
        <ul class="attachment-list">
          <li th:each="att"> icon + filename + size + download link </li>
        </ul>
      </section>
      <nav class="lesson-nav"> ← Prev | Next → </nav>
  </div>
```

---

## 5. JavaScript — `student-lessons.js`

```javascript
// Runs on student/class-lessons.html only
(function () {
  const panel  = document.getElementById('lesson-panel');
  const close  = document.getElementById('panel-close');
  if (!panel) return; // guard: if template rendered without panel

  // Open panel if ?lesson= in URL on first load
  const params = new URLSearchParams(location.search);
  if (params.has('lesson')) panel.classList.add('open');

  // Lesson card click → update URL + open panel via fetch or reload
  document.querySelectorAll('.lesson-card[data-lesson-id]').forEach(card => {
    card.addEventListener('click', e => {
      e.preventDefault();
      const url = card.getAttribute('href');
      history.pushState({}, '', url);          // update URL
      // Simple approach: let browser navigate (full page reload)
      // Advanced: fetch the panel HTML from server and inject — Phase 2
      window.location.href = url;
    });
  });

  // Close button
  if (close) {
    close.addEventListener('click', () => {
      panel.classList.remove('open');
      const p = new URLSearchParams(location.search);
      p.delete('lesson');
      history.pushState({}, '', '?' + p.toString());
    });
  }

  // Escape key closes panel
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && panel.classList.contains('open')) {
      close && close.click();
    }
  });
})();
```

> **Approach:** Phase 1 dùng full-page navigation (đơn giản, reliable, SEO-friendly).
> Phase 2 có thể upgrade thành `fetch()` để load panel content không cần reload.

---

## 6. CSS — `student-lessons.css`

Xem chi tiết trong [specs/student/spec.md](../../specs/student/spec.md#shared-css-additions).

Key classes:
- `.section-chips`, `.section-chip`, `.section-chip.active`
- `.lesson-card`, `.lesson-card.inline-active`
- `.lesson-panel`, `.lesson-panel.open` (slide-in via `transform`)
- `.lesson-richtext` (scopes Quill HTML)
- `.attachment-list`, `.attachment-list li`

---

## 7. Security Design

### 7.1 Phân lớp auth

```
Controller layer:  @PreAuthorize("hasAnyRole('STUDENT','LECTURER','HEAD','ADMIN')")
Service layer:     classesService.getViewable(classId, userId, role)
                   → throws AccessDeniedException nếu không enrolled
Data layer:        listPublishedForSection() chỉ query status = PUBLISHED
                   @SQLRestriction("is_deleted = 0") tự filter soft-deleted
```

### 7.2 Bảng kiểm tra security

| Scenario | Kết quả mong đợi |
|----------|-----------------|
| Student enrolled, lesson PUBLISHED | ✅ Hiển thị đầy đủ |
| Student enrolled, lesson DRAFT | ❌ 404 (treat as non-existent) |
| Student không enrolled | ❌ 403 / redirect |
| Student đoán `?lesson=` của lớp khác | ❌ Panel silently absent (null) |
| Lecturer xem trang student | ✅ Cho phép (cũng thấy như student) |
| XSS trong contentRichtext | ✅ Sanitised at save time bởi HtmlSanitizer |

---

## 8. URL Routing Summary

| Method | Path | Controller | Auth |
|--------|------|------------|------|
| GET | `/student/classes/{classId}/lessons` | `StudentLessonsTabController` | STUDENT+ |
| GET | `/student/classes/{classId}/lessons?section={id}` | idem | STUDENT+ |
| GET | `/student/classes/{classId}/lessons?section={id}&lesson={id}` | idem + inline | STUDENT+ |
| GET | `/student/classes/{classId}/sections/{sectionId}/lessons/{lessonId}` | `StudentLessonDetailController` | STUDENT+ |
| GET | `/api/lessons/{lessonId}/attachments/{attId}/download` | `LessonAttachmentsApiController` | STUDENT+ (đã có) |

---

## 9. Open Questions

| # | Câu hỏi | Quyết định mặc định |
|---|---------|---------------------|
| Q1 | Có nên đếm attachment per lesson (badge) trong Phase 1? | Không — set `attachmentCount = 0`, thêm sau. |
| Q2 | Panel: full-page reload hay fetch? | Phase 1: reload (đơn giản). |
| Q3 | Có nút "Đánh dấu đã xem"? | Ngoài scope Sprint 4. |
| Q4 | Prev/Next lesson có cần BE thêm query? | Optional — bỏ qua Phase 1, thêm KSH-4.2 Phase 2. |
| Q5 | Student có thể xem lớp mà mình là giảng viên? | Có — `getViewable` cho phép giảng viên sở hữu. |
