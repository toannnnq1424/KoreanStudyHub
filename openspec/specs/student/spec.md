# KSH-4: Student Lesson Viewer Epic

## Overview

Enrolled students can browse all **published** lessons in a class, organised by
section (chương). Clicking a lesson opens a detail page showing the rich-text
body and downloadable attachments. An optional `?lesson=` query parameter on the
lessons-list URL opens an inline slide-in panel without a full navigation, so the
student stays in context.

**Sprint:** 4 (Student Viewer)  
**Labels:** student, lessons, materials  
**Priority:** High  
**Depends on:** KSH-3.4 (Sections + Lessons + Attachments — ✅ Done)

---

## Data visibility rules (all enforced at the service layer)

| Entity | Student visibility |
|--------|--------------------|
| `Section` | Always visible when enrolled (sections are structural containers) |
| `Lesson` | Only `status = PUBLISHED`; drafts are invisible |
| `LessonAttachment` | Only when parent lesson is `PUBLISHED` + student is enrolled |

> **Auth invariant:** every student-facing endpoint calls
> `ClassesService.getViewable(classId, userId, role)` which resolves the class
> and verifies that the student is actively enrolled (status = `ACTIVE` in
> `enrollments`). Non-enrolled users get 403 / redirect to `/student/join`.

---

## KSH-4.1 — Story: Student Lessons Tab (Sections Grouped)

> As an enrolled student, I want to see all published lessons in a class
> grouped by section, so I can navigate the course content in the order the
> lecturer intended.

### KSH-4.1-DB: [DB] Schema

**Status:** ✅ Not needed — reuses `sections` (V13), `lessons` (V14),
`lesson_attachments` (V15) and `enrollments` (V5) tables unchanged.

---

### KSH-4.1-BE: [BE] Student Lessons Tab Controller

**Status:** 🔲 Not started

**New file:** `com.ksh.features.lessons.controller.StudentLessonsTabController`

**Endpoint:**

```
GET /student/classes/{classId}/lessons
    @PreAuthorize(Roles.PREAUTH_STUDENT_OR_ABOVE)
    ?section=<sectionId>   (optional — pre-selects a section in the sidebar)
```

**Behaviour:**

1. Call `ClassesService.getViewable(classId, userId, role)` — throws 404 / 403
   if not enrolled or class deleted.
2. Load `SectionsService.listForClass(classId, userId, role)` — returns all
   sections (sections are always visible; the lessons inside are filtered).
3. If `?section=` is present, validate it belongs to `classId`; silently fall
   back to `null` on mismatch (same pattern as `LessonsTabController`).
4. Load lessons filtered to `PUBLISHED` only:
   `LessonsService.listPublishedForSection(classId, sectionId, userId, role)` — **new method to add.**
5. Model attributes:

| Attribute | Type | Description |
|-----------|------|-------------|
| `clazz` | `ClassEntity` | Needed by sidebar fragment |
| `sections` | `List<SectionRow>` | All sections for left nav |
| `selectedSectionId` | `Long` | Null when "all" |
| `selectedSection` | `Section` | Entity or null |
| `lessons` | `List<LessonRow>` | PUBLISHED lessons in selected section (empty when none selected) |
| `activeTab` | `String` | `"lessons"` |

**Return template:** `student/class-lessons` (new)

**Authorization rules (service layer):**
- A `LECTURER` or `HEAD` or `ADMIN` can also hit this URL (they are also enrolled
  conceptually); `ROLE_STUDENT` is the primary audience.
- If `ClassesService.getViewable` finds the class but the user is neither a
  lecturer-owner nor enrolled → throw `AccessDeniedException`.

**New service method to add to `LessonsService`:**

```java
/**
 * Lists PUBLISHED lessons for a section — student-safe read.
 * Does NOT call getEditable; calls getViewable instead so students can read.
 */
@Transactional(readOnly = true)
public List<LessonRow> listPublishedForSection(Long classId, Long sectionId,
                                               Long userId, Role role) {
    classesService.getViewable(classId, userId, role);
    reorderService.verifySectionBelongsToClass(sectionId, classId);
    return lessonRepository
            .findBySectionIdAndStatusOrderByDisplayOrderAsc(sectionId, Lesson.STATUS_PUBLISHED)
            .stream().map(LessonsService::toRow).toList();
}
```

**New repository method to add to `LessonRepository`:**

```java
List<Lesson> findBySectionIdAndStatusOrderByDisplayOrderAsc(Long sectionId, String status);
```

**Acceptance criteria:**
- Drafts never appear in the student-facing list even if guessed by URL.
- Non-enrolled user gets redirected to `/student/join` (or 403 if XHR).
- Selecting an invalid or cross-class section degrades gracefully (empty lessons
  list, no 404).

---

### KSH-4.1-FE: [FE] Student Lessons Tab Template

**Status:** 🔲 Not started

**New template:** `src/main/resources/templates/student/class-lessons.html`

**Layout:** Reuse `app-shell.css` + `class-sidebar` fragment (student view).

**Structure:**

```
┌─ app-header ─────────────────────────────────────────────────────────────┐
├─ class-detail-layout ────────────────────────────────────────────────────┤
│  ├─ [aside] class-sidebar (activeTab = lessons)                          │
│  └─ [main]                                                               │
│      ├─ Section nav (left column or top bar — mirrors detail-lessons.html)│
│      │   each section = clickable chip/accordion heading                 │
│      └─ Lessons list (right column)                                      │
│          each lesson = card with title, publishedAt, attachment count    │
│          clicking opens lesson detail (/student/classes/{id}/lessons/{lesId})
└──────────────────────────────────────────────────────────────────────────┘
```

**Section navigation chips:**
- "Tất cả" chip (no section selected) — shows nothing or a welcome message.
- One chip per `SectionRow` in `sections`, linked to `?section={id}`.
- Active chip highlighted when `selectedSectionId` matches.

**Lesson cards (per `LessonRow` in `lessons`):**
- Title (bold), publishedAt (formatted `dd/MM/yyyy`).
- `📎 n tệp` badge if `LessonRow.attachmentCount > 0` — **requires adding `attachmentCount` to `LessonRow`** (see note below).
- Full-card click → `GET /student/classes/{classId}/sections/{sectionId}/lessons/{lessonId}`.
- If `?lesson={id}` is present in the current URL, that card shows a highlighted
  "đang xem" border and triggers the inline panel (see KSH-4.3).

**Empty states:**
- No section selected → "Chọn chương để xem bài giảng."
- Section selected but 0 published lessons → "Chương này chưa có bài giảng nào."

> **Note on `attachmentCount`:** To show the attachment badge without an N+1
> query, `LessonRow` should be extended with an `int attachmentCount` field.
> The repository query can use `COUNT(la.id)` via a JPQL join. Alternatively,
> start with `attachmentCount = 0` (badge hidden) and add counts in a follow-up
> ticket.

---

## KSH-4.2 — Story: Student Lesson Detail Page

> As an enrolled student, I want to open a published lesson and read its
> rich-text content and download its attachments.

### KSH-4.2-BE: [BE] Student Lesson Detail Controller

**Status:** 🔲 Not started

**New file:** `com.ksh.features.lessons.controller.StudentLessonDetailController`

**Endpoint:**

```
GET /student/classes/{classId}/sections/{sectionId}/lessons/{lessonId}
    @PreAuthorize(Roles.PREAUTH_STUDENT_OR_ABOVE)
```

**Behaviour:**

1. `ClassesService.getViewable(classId, userId, role)` — enrollment check.
2. `reorderService.verifySectionBelongsToClass(sectionId, classId)`.
3. Load lesson: `lessonRepository.findByIdAndSectionId(lessonId, sectionId)` →
   404 if missing.
4. **Gate:** if `lesson.getStatus() != PUBLISHED` → throw
   `EntityNotFoundException` (treat draft as non-existent for students).
5. Load attachments via `LessonAttachmentsService.listForStudent(classId, sectionId, lessonId, userId, role)` — **new method** (see below).
6. Load prev/next lesson for navigation:
   `LessonsService.findAdjacentPublished(sectionId, lesson.getDisplayOrder())` — **new method** (optional, can ship later).

**Model attributes:**

| Attribute | Type |
|-----------|------|
| `clazz` | `ClassEntity` |
| `section` | `Section` |
| `lesson` | `Lesson` |
| `attachments` | `List<LessonAttachmentRow>` |
| `prevLesson` | `LessonRow` or null |
| `nextLesson` | `LessonRow` or null |
| `activeTab` | `"lessons"` |

**Return template:** `student/lesson-detail` (new)

**New service method on `LessonAttachmentsService`:**

```java
/**
 * Student-safe list: verifies enrollment + lesson published.
 * Reuses the same auth guard already in download().
 */
@Transactional(readOnly = true)
public List<LessonAttachmentRow> listForStudent(Long classId, Long sectionId,
                                                Long lessonId, Long userId, Role role) {
    classesService.getViewable(classId, userId, role);
    reorderService.verifySectionBelongsToClass(sectionId, classId);
    Lesson lesson = loadLesson(sectionId, lessonId);
    if (!LESSON_STATUS_PUBLISHED.equals(lesson.getStatus())) return List.of();
    return mapRows(attachmentRepository.findByLessonIdOrderByUploadedAtAsc(lessonId));
}
```

**Acceptance criteria:**
- Opening a DRAFT lesson URL → 404 (same as if the lesson doesn't exist).
- Attachment download link reuses existing `/api/lessons/{id}/attachments/{id}/download`
  which already handles student auth for published lessons.
- Page renders even when `contentRichtext` is null or empty (graceful empty state).

---

### KSH-4.2-FE: [FE] Lesson Detail Template

**Status:** 🔲 Not started

**New template:** `src/main/resources/templates/student/lesson-detail.html`

**Structure:**

```
┌─ app-header ──────────────────────────────────────────────────────────────┐
├─ class-detail-layout ─────────────────────────────────────────────────────┤
│  ├─ [aside] class-sidebar (activeTab = lessons)                           │
│  └─ [main]                                                                │
│      ├─ Breadcrumb: Lớp học > Bài giảng > {section.title} > {lesson.title}│
│      ├─ Lesson header card                                                │
│      │   title (h1), publishedAt, status badge (PUBLISHED only shown)     │
│      ├─ Rich-text body                                                    │
│      │   <div class="lesson-richtext" th:utext="${lesson.contentRichtext}">│
│      │   th:utext is safe — content sanitised by HtmlSanitizer on save    │
│      ├─ Attachments section (if attachments non-empty)                    │
│      │   table: filename, type icon, size, download button               │
│      └─ Prev / Next navigation bar                                        │
│          ← {prevLesson.title}          {nextLesson.title} →              │
└───────────────────────────────────────────────────────────────────────────┘
```

**Attachment row UI:**
- File type icon based on `mimeType` prefix (pdf → 📄, image → 🖼, video → 🎬, other → 📎).
- Size formatted: < 1 KB → "< 1 KB"; < 1 MB → "X KB"; else "X.Y MB".
- Download button: `<a href="/api/lessons/{lessonId}/attachments/{id}/download" download>`.

**CSS note:** Reuse `app-shell.css` + define `.lesson-richtext` to scope the
Quill-generated HTML (headings, blockquotes, code blocks) without leaking into
the surrounding UI.

---

## KSH-4.3 — Story: Inline Lesson Detail via `?lesson=` Query Param

> As an enrolled student, I want to click a lesson from the list and see its
> content slide in on the right **without navigating away**, so I can quickly
> preview without losing the section overview.

### KSH-4.3-FE: [FE] Inline Slide-in Panel

**Status:** 🔲 Not started

**Approach:** Progressive-enhancement over the existing KSH-4.1 list page.

**URL contract:**
```
GET /student/classes/{classId}/lessons?section={secId}&lesson={lessonId}
```
When `?lesson=` is present:
- The **server** still renders the full list page (KSH-4.1) — no separate BE endpoint needed.
- The server additionally loads the lesson detail and passes it as `inlineLesson`
  + `inlineAttachments` model attributes (null when `?lesson=` absent).

**Backend change (extend `StudentLessonsTabController`):**

```java
// Add optional @RequestParam Long lesson (nullable)
// When present and lesson is PUBLISHED + belongs to class:
//   model.addAttribute("inlineLesson", lesson entity)
//   model.addAttribute("inlineAttachments", list)
// When absent or validation fails → set both to null (graceful)
```

**Frontend behaviour:**
- On page load: if `?lesson=` param is in the URL, JS opens the panel.
- Panel is a `<aside id="lesson-panel">` hidden by default, slides in via CSS
  `transform: translateX(100%)` → `translateX(0)` transition.
- Panel close button sets URL to `?section={secId}` (removes `?lesson=`) via
  `history.pushState` and hides the panel.
- Each lesson card has `href="?section={secId}&lesson={lessonId}"` so deep-linking
  works and back-button closes the panel correctly.
- If JS is disabled, clicking the card navigates to the full lesson detail page
  (KSH-4.2) — the `href` is the same full URL.

**Panel content (rendered server-side, shown/hidden client-side):**
```html
<aside id="lesson-panel" class="lesson-panel" th:if="${inlineLesson != null}">
  <div class="panel-header">
    <h2 th:text="${inlineLesson.title}">Tiêu đề</h2>
    <button id="panel-close" aria-label="Đóng">✕</button>
  </div>
  <div class="lesson-richtext" th:utext="${inlineLesson.contentRichtext}"></div>
  <!-- Attachments list -->
  <div th:if="${not #lists.isEmpty(inlineAttachments)}">
    <h4>Tài liệu đính kèm</h4>
    <ul class="attachment-list">
      <li th:each="att : ${inlineAttachments}">
        <a th:href="${att.downloadUrl}" th:text="${att.originalFilename}" download></a>
        <span th:text="${#numbers.formatDecimal(att.sizeBytes / 1024.0, 0, 'POINT', 0, 'COMMA')} + ' KB'"></span>
      </li>
    </ul>
  </div>
  <a class="btn-full-detail"
     th:href="@{'/student/classes/' + ${clazz.id} + '/sections/' + ${selectedSectionId} + '/lessons/' + ${inlineLesson.id}}">
    Xem trang đầy đủ →
  </a>
</aside>
```

**Acceptance criteria:**
- `?lesson=` param with a DRAFT lesson ID → panel silently absent (server returns
  `inlineLesson = null`), page renders normally.
- `?lesson=` param pointing to a lesson in a different class → panel absent (auth gate).
- With JS: clicking any lesson card updates URL and opens panel without full reload.
- Without JS: clicking any lesson card does a normal navigation to KSH-4.2 detail page.
- Panel has an accessible close button (`aria-label="Đóng"`, focus trapped while open).
- Deep-linked URL (`?lesson=`) renders the panel already open on page load.

---

## Shared CSS additions (both KSH-4.1 and KSH-4.3)

Add to `static/css/student-lessons.css` (new file, included by both templates):

```css
/* Section chips */
.section-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 20px; }
.section-chip { padding: 6px 16px; border-radius: 20px; border: 1px solid var(--border);
                background: var(--panel-bg); font-weight: 600; font-size: 13px;
                color: var(--text-soft); text-decoration: none; transition: all 0.2s; }
.section-chip.active,
.section-chip:hover { background: var(--primary); color: #fff; border-color: var(--primary); }

/* Lesson card */
.lesson-card { display: flex; align-items: center; gap: 12px; padding: 16px 20px;
               background: var(--card-bg); border: 1px solid var(--border);
               border-radius: 10px; text-decoration: none; color: inherit;
               transition: box-shadow 0.18s, border-color 0.18s; margin-bottom: 10px; }
.lesson-card:hover { border-color: var(--primary); box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
.lesson-card.inline-active { border-color: var(--primary); background: var(--primary-soft); }
.lesson-card-icon { font-size: 22px; flex-shrink: 0; }
.lesson-card-title { font-weight: 700; font-size: 15px; }
.lesson-card-meta { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

/* Inline panel */
.lesson-panel { position: fixed; top: 0; right: 0; width: min(560px, 95vw); height: 100vh;
                background: var(--card-bg); box-shadow: -4px 0 24px rgba(0,0,0,0.14);
                transform: translateX(100%); transition: transform 0.28s ease;
                z-index: 200; overflow-y: auto; padding: 28px 28px 80px; }
.lesson-panel.open { transform: translateX(0); }
.panel-header { display: flex; justify-content: space-between; align-items: flex-start;
                margin-bottom: 20px; gap: 12px; }
.panel-header h2 { font-size: 18px; margin: 0; line-height: 1.4; }

/* Rich-text scoping */
.lesson-richtext h1,.lesson-richtext h2,.lesson-richtext h3 { margin: 16px 0 8px; }
.lesson-richtext pre { background: #f4f6f8; padding: 12px; border-radius: 6px; overflow-x: auto; }
.lesson-richtext blockquote { border-left: 4px solid var(--primary); margin: 0;
                               padding: 8px 16px; color: var(--text-soft); }

/* Attachment list */
.attachment-list { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.attachment-list li { display: flex; align-items: center; justify-content: space-between;
                      padding: 10px 14px; background: var(--panel-bg);
                      border-radius: 8px; border: 1px solid var(--border); }
```

---

## Security Checklist

| Check | Guard |
|-------|-------|
| Student cannot see DRAFT lessons | `listPublishedForSection` filters by `status = PUBLISHED` |
| Student cannot access another class's content | `ClassesService.getViewable` verifies enrollment |
| Student cannot download attachments of draft lessons | `LessonAttachmentsService.download` checks `lesson.status == PUBLISHED` |
| Inline panel cannot expose cross-class lesson | Controller validates `lesson.sectionId` → `section.classId == classId` |
| Rich-text XSS | `HtmlSanitizer.sanitize` is called on every save; `th:utext` is safe here |

---

## Test Scenarios

### KSH-4.1 — List tab

| # | Given | When | Then |
|---|-------|------|------|
| T1 | Student enrolled, 2 sections with published lessons | GET `/student/classes/1/lessons?section=10` | Returns 200, `lessons` contains only PUBLISHED rows for section 10 |
| T2 | Student enrolled, section has only DRAFT lessons | GET `?section=10` | `lessons` is empty list, no 404 |
| T3 | Not enrolled student | GET `/student/classes/1/lessons` | 403 or redirect |
| T4 | Invalid `?section=99` (other class) | GET | Falls back gracefully, `selectedSection = null` |

### KSH-4.2 — Lesson detail

| # | Given | When | Then |
|---|-------|------|------|
| T5 | Student enrolled, lesson is PUBLISHED | GET `/student/.../lessons/5` | 200, richtext + attachments shown |
| T6 | Student enrolled, lesson is DRAFT | GET `/student/.../lessons/5` | 404 |
| T7 | Student not enrolled | GET any lesson detail | 403 |

### KSH-4.3 — Inline panel

| # | Given | When | Then |
|---|-------|------|------|
| T8 | `?lesson=5` points to PUBLISHED lesson | Page load | Panel open, content shown |
| T9 | `?lesson=5` points to DRAFT lesson | Page load | Panel absent, list page normal |
| T10 | `?lesson=5` points to lesson in different class | Page load | Panel absent, no error exposed |
