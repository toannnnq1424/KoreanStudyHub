# KSH-3: Class Management Epic

## Overview

Lecturers can create, update, and soft-delete Korean study classes.
Students can be enrolled. Classes have tabs: Board, Members, Schedule,
Assignments, Scores, Lessons, Materials, Settings.

**Sprint:** 2 (Class CRUD + Members done), Sprint 3–5 (remaining tabs)
**Labels:** classes, core
**Priority:** Highest

---

## KSH-3.1 — Story: Lecturer Class CRUD

> As a LECTURER (or HEAD/ADMIN), I want to create, edit, and delete my Korean study classes
> so that I can manage which classes are active on the platform.

### KSH-3.1-DB: [DB] Class Schema

**Status:** Done

- Entity: `com.ksh.entities.ClassEntity`
- Table: `classes`
- Migration: `V5__classes.sql`
- Key columns: `id`, `name`, `code` (unique, 6-char invite code), `lecturer_id` (FK users),
  `description`, `start_date`, `end_date`, `max_students`, `status`, `deleted_at`
- Soft-delete: `deleted_at IS NULL` filter on all queries

- Entity: `com.ksh.entities.ClassActivity`
- Table: `class_activities`
- Records every create/update/delete with `type`, `description`, `metadata_json`, `actor_id`

---

### KSH-3.1-BE: [BE] Class CRUD

**Status:** Done

- Controller: `com.ksh.features.controller.classes.ClassesController`
  - `GET /lecturer/classes` — list (own classes for LECTURER; all for HEAD/ADMIN)
  - `GET /lecturer/classes/new` — create form
  - `POST /lecturer/classes` — submit create
  - `GET /lecturer/classes/{id}/edit` — edit form
  - `POST /lecturer/classes/{id}` — submit update
  - `POST /lecturer/classes/{id}/delete` — soft-delete
- DTOs: `com.ksh.features.dto.classes.ClassesDtos` (ClassRow, ClassForm)
- Service: `com.ksh.features.service.classes.ClassesService`
- Code generator: `ClassCodeGeneratorImpl` — 6-char alphanumeric, retries up to 3 times on collision

**Authorization rules (enforced in service, not controller):**
- LECTURER: can only view/edit/delete classes where `lecturer_id == user.id`
- HEAD/ADMIN: can view/edit/delete any class
- Violation → `AccessDeniedException` → 403

**Validation (ClassForm):**
- `name`: required, 3–300 chars
- `description`: optional, ≤2000 chars
- `maxStudents`: optional, 1–1000
- `endDate` must be strictly after `startDate` (cross-field `@AssertTrue`)

**Acceptance criteria:**
- Invite code collision retried up to 3 times; `ClassCodeGenerationException` after that
- Every create/update/soft-delete writes a row to `class_activities`
- Mutations are `@Transactional` (activity log rolled back together with class on failure)
- 404 on non-existent or soft-deleted class id

---

### KSH-3.1-FE: [FE] Class Management View

**Status:** Done

- Templates:
  - `classes/manage.html` — card grid list
  - `classes/form.html` — shared create/edit form
- Gradient colors per class via `ClassGradient.css()`
- Thumbnail label = first 2 chars of class name (uppercase)

---

## KSH-3.2 — Story: Class Detail Tabs

> As a lecturer or student, I want to navigate class detail pages via a sidebar tab system
> so that I can access board, members, schedule, lessons, assignments, scores, and materials.

### KSH-3.2-BE: [BE] Class Detail Endpoints

**Status:** Partial (Board + Members done; others are placeholder)

- `GET /lecturer/classes/{id}` → redirect to `/board`
- `GET /lecturer/classes/{id}/board` — board tab
- `GET /lecturer/classes/{id}/members` — members tab (real data)
- `GET /lecturer/classes/{id}/schedule|roles|groups|assignments|scores|lessons|materials` — placeholder tabs
- `GET /lecturer/classes/{id}/settings` — edit form reuse

---

## KSH-3.3 — Story: Class Members & Enrollment

> As a lecturer, I want to see a list of enrolled students and manage their enrollment status.

### KSH-3.3-DB: [DB] Enrollment Schema

**Status:** Done

- Entity: `com.ksh.entities.Enrollment`
- Table: `enrollments`
- Key columns: `id`, `class_id`, `user_id`, `join_method` (CODE/MANUAL/OAUTH), `enrolled_at`, `status`

---

### KSH-3.3-BE: [BE] Members Service

**Status:** Done

- Service: `com.ksh.features.service.classes.ClassMembersService`
- DTO: `com.ksh.features.dto.classes.MemberDtos`
- Loads enrollments + user info for the members tab

---

## KSH-3.4 — Story: Lessons, Sections & Attachments [Sprint 3 — ✅ Done]

> As a lecturer, I want to create structured course content (sections + lessons +
> file attachments) so that students have organised learning material.

**Status:** Done (Lecturer-facing backend + frontend complete)

### KSH-3.4-DB: [DB] Schema

**Status:** ✅ Done

- `sections` (V13): `id`, `class_id`, `title`, `display_order`, `is_deleted`,
  `created_by`, `created_at`, `updated_at`. Soft-delete via `@SQLRestriction`.
  Unique key `uk_section_class_order` (class_id, display_order).
- `lessons` (V14): `id`, `section_id`, `title`, `status` (DRAFT/PUBLISHED),
  `content_richtext LONGTEXT`, `display_order`, `published_at`, `is_deleted`,
  `created_by`, `created_at`, `updated_at`. Unique key `uk_lesson_section_order`.
- `lesson_attachments` (V15): `id`, `lesson_id`, `original_filename`,
  `stored_path`, `mime_type`, `size_bytes`, `uploaded_by`, `uploaded_at`.

---

### KSH-3.4-BE: [BE] Sections CRUD

**Status:** ✅ Done

- `com.ksh.features.lessons.controller.SectionsController` — full-page create/rename form
- `com.ksh.features.lessons.controller.SectionsApiController` — JSON delete + reorder
- `com.ksh.features.lessons.service.SectionsService` — create, rename, soft-delete
- `com.ksh.features.lessons.service.SectionsReorderService` — drag-reorder validation
- `com.ksh.features.lessons.service.SectionActivityWriter` — audit log rows
- `com.ksh.features.lessons.repository.SectionRepository` — `findByIdAndClassId`, etc.

**Activity log types:** `CREATED`, `RENAMED`, `DELETED`, `REORDERED`

---

### KSH-3.4-BE2: [BE] Lessons CRUD

**Status:** ✅ Done

- `com.ksh.features.lessons.controller.LessonsController` — create/edit forms (full-page)
- `com.ksh.features.lessons.controller.LessonsApiController` — JSON delete + reorder
- `com.ksh.features.lessons.controller.LessonsLifecycleController` — publish/unpublish POST
- `com.ksh.features.lessons.controller.LessonsTabController` — GET lessons tab (both lecturer + ?)
- `com.ksh.features.lessons.service.LessonsService` — list, create, update, soft-delete, reorder
- `com.ksh.features.lessons.service.LessonsPublishService` — publish/unpublish transitions
- `com.ksh.features.lessons.service.LessonsReorderService` — reorder + cross-class guard
- `com.ksh.features.lessons.service.LessonsUpdateHelper` — diff tracking + status transitions
- `com.ksh.features.lessons.service.LessonActivityWriter` — audit log rows
- `com.ksh.features.lessons.repository.LessonRepository` — `findByIdAndSectionId`, `findMaxDisplayOrder`, etc.

**Content sanitisation:** `HtmlSanitizer.sanitize()` called on `contentHtml` before save.

**Authorization:**
- `LessonsService.listForSection` calls `classesService.getEditable` (lecturer-only read).
- Student read uses a separate method `listPublishedForSection` — see **KSH-4.1-BE**.

**Activity log types:** `CREATED`, `UPDATED`, `PUBLISHED`, `UNPUBLISHED`, `DELETED`, `REORDERED`

---

### KSH-3.4-BE3: [BE] Lesson Attachments

**Status:** ✅ Done

- `com.ksh.features.lessons.controller.LessonAttachmentsApiController` — upload / delete / list
- `com.ksh.features.lessons.service.LessonAttachmentsService`
  - `listForLesson` — lecturer read (calls `getEditable`)
  - `upload` — store file + save DB row
  - `delete` — hard-delete row + on-disk file
  - `deleteAllByLesson` — cascade called from `LessonsService.delete`
  - `download` — auth-aware; students may download only PUBLISHED lesson attachments

**Download URL pattern:** `/api/lessons/{lessonId}/attachments/{attachmentId}/download`

**Three-layer auth on upload/delete:** `getEditable` → `verifySectionBelongsToClass` → `findByIdAndSectionId`

---

### KSH-3.4-FE: [FE] Lessons Tab (Lecturer)

**Status:** ✅ Done

- Template: `classes/detail-lessons.html`
- Left column: section list (drag-to-reorder, create button)
- Right column: lesson list per selected section (drag-to-reorder, create button, publish toggle)
- History tab on lesson edit: `LessonActivityRow` timeline
- `?section=<id>` query param pre-selects a section; cross-class links degrade gracefully

---

## KSH-4.1 — Student Lessons Tab [Sprint 4 — Planned]

> As an enrolled student, I want to browse published lessons grouped by section.

**Status:** 🔲 Not started

See full spec in **[student/spec.md](../student/spec.md)**.

Sub-tasks:

| Task | Description |
|------|-------------|
| KSH-4.1-BE | `StudentLessonsTabController` + `LessonsService.listPublishedForSection` |
| KSH-4.1-FE | `student/class-lessons.html` template |
| KSH-4.2-BE | `StudentLessonDetailController` + `LessonAttachmentsService.listForStudent` |
| KSH-4.2-FE | `student/lesson-detail.html` template |
| KSH-4.3-FE | Inline `?lesson=` slide-in panel (progressive enhancement) |

---

## KSH-3.5 — Story: Grade Book / Scoring [Sprint 5 — Planned]

> As a lecturer, I want to enter and update student scores for assignments
> so that students can view their grade progress.

**Status:** Planned

- Table: `submissions` — `id`, `assignment_id`, `student_id`, `file_url`, `submitted_at`, `score`, `feedback`
- Controller: `com.ksh.classes.scores.controller.ScoresController` [NEW]
- Template: `classes/detail-scores.html` [NEW]
