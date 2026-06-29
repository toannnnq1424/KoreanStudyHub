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

## KSH-3.4 — Story: Lessons & Assignments [Sprint 3 — Planned]

> As a lecturer, I want to create lesson content and assignments for a class
> so that students have structured learning material.

**Status:** Planned

### KSH-3.4-DB: [DB] Lessons & Assignments — Schema

**Status:** Not started

- Table: `lessons` — `id`, `class_id`, `title`, `content_html`, `order`, `published_at`
- Table: `assignments` — `id`, `class_id`, `title`, `description`, `due_date`, `max_score`
- Table: `lesson_attachments` — `id`, `lesson_id`, `file_url`, `file_name`, `file_size`
- Migration: `V10__lessons_assignments.sql` (planned)

### KSH-3.4-BE: [BE] Lessons CRUD

**Status:** Not started

- Controller: `com.ksh.classes.lessons.controller.LessonsController` [NEW]
- Service: `com.ksh.classes.lessons.service.LessonsService` [NEW]

### KSH-3.4-FE: [FE] Lessons Detail Tab

**Status:** Not started

- Template: `classes/detail-lessons.html` [NEW] (replaces placeholder)

---

## KSH-3.5 — Story: Grade Book / Scoring [Sprint 4 — Planned]

> As a lecturer, I want to enter and update student scores for assignments
> so that students can view their grade progress.

**Status:** Planned

- Table: `submissions` — `id`, `assignment_id`, `student_id`, `file_url`, `submitted_at`, `score`, `feedback`
- Controller: `com.ksh.classes.scores.controller.ScoresController` [NEW]
- Template: `classes/detail-scores.html` [NEW]
