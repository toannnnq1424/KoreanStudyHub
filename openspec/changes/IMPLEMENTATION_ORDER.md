# KSH OpenSpec — Implementation Order

This document tracks the sprint-by-sprint rollout order for all KSH features.
Use it to decide which `openspec/specs/` to pick from next.

---

## ✅ Sprint 1 — Auth & Account Foundation (Completed)

| ID | Story | Status |
|----|-------|--------|
| KSH-1.1 | User Registration (DB + BE + FE) | ✅ Done |
| KSH-1.2 | Email/Password Login (Spring Security) | ✅ Done |
| KSH-1.3 | Google OAuth2 Login | ✅ Done |
| KSH-1.4 | Forgot Password / Password Recovery | ✅ Done |
| KSH-0.1 | Security Config | ✅ Done |
| KSH-0.2 | Mail Service (DbConfiguredMailSender) | ✅ Done |
| KSH-0.4 | System Settings entity/repository | ✅ Done |
| KSH-0.5 | Global Exception Handler | ✅ Done |

---

## ✅ Sprint 2 — Profile & Class Management (Completed)

| ID | Story | Status |
|----|-------|--------|
| KSH-2.1 | View & Edit User Profile | ✅ Done |
| KSH-2.2 | Avatar Upload | ✅ Done |
| KSH-2.3 | Change Password | ✅ Done |
| KSH-3.1 | Lecturer Class CRUD | ✅ Done |
| KSH-3.2 | Class Detail Tabs (Board + Members live; rest placeholder) | ✅ Partial |
| KSH-3.3 | Class Members & Enrollment view | ✅ Done |
| KSH-4.1 | Admin Dashboard (stats, donut chart, recent classes) | ✅ Done |
| KSH-4.2 | Email Settings (SMTP config + test) | ✅ Done |
| KSH-0.3 | Avatar Storage Service | ✅ Done |

---

## ✅ Sprint 3 — Lessons & Materials (Completed)

> All backend + lecturer-side frontend for the lessons tab has shipped.
> The student-side viewer (KSH-4.1) is the **next planned story** — see Sprint 4 below.

| ID | Story | Status | Notes |
|----|-------|--------|-------|
| KSH-3.4-DB | Sections schema (V13) | ✅ Done | `sections` table; soft-delete via `is_deleted`; `uk_section_class_order` unique key |
| KSH-3.4-DB2 | Lessons schema (V14) | ✅ Done | `lessons` table; `content_richtext LONGTEXT`; `uk_lesson_section_order` unique key |
| KSH-3.4-DB3 | Lesson Attachments schema (V15) | ✅ Done | `lesson_attachments` table; FK `lesson_id`; stores `stored_path`, `mime_type`, `size_bytes` |
| KSH-3.4-BE | Sections CRUD (create/rename/reorder/delete) | ✅ Done | `SectionsController`, `SectionsApiController`, `SectionsService`, `SectionsReorderService` |
| KSH-3.4-BE2 | Lessons CRUD (create/edit/publish/unpublish/reorder/delete) | ✅ Done | `LessonsController`, `LessonsApiController`, `LessonsLifecycleController`, `LessonsService`, `LessonsPublishService`, `LessonsReorderService` |
| KSH-3.4-BE3 | Lesson Attachments upload/download/delete | ✅ Done | `LessonAttachmentsApiController`, `LessonAttachmentsService`, `LessonAttachmentStorageService` |
| KSH-3.4-FE | Lessons Tab (sections sidebar + lesson list) | ✅ Done | `LessonsTabController` → `classes/detail-lessons.html`; `?section=` query param supported |
| KSH-3.6 | Material file upload (PDF, DOCX, ZIP) | ✅ Done | Embedded inside lesson edit form; `LessonAttachmentStorageService` |
| KSH-0.6 | V13–V15 Flyway migrations (sections + lessons + attachments) | ✅ Done | V13 sections, V14 lessons, V15 lesson_attachments |

**Sprint 3 owner:** toannq1424

---

## 🔲 Sprint 4 — Student Lesson Viewer (Next)

> Student-side lesson viewing is the immediate next feature to implement.
> Spec: [student/spec.md](../specs/student/spec.md)

| ID | Story | Spec |
|----|-------|------|
| KSH-4.1-DB | No schema change required (reuses existing tables) | — |
| KSH-4.1-BE | Student Lessons Tab Controller | [student/spec.md#ksh-41-be](../specs/student/spec.md) |
| KSH-4.1-FE | Student Lessons Tab — sections grouped list view | [student/spec.md#ksh-41-fe](../specs/student/spec.md) |
| KSH-4.2-BE | Student Lesson Detail Page | [student/spec.md#ksh-42-be](../specs/student/spec.md) |
| KSH-4.2-FE | Lesson Detail — rich-text + attachments | [student/spec.md#ksh-42-fe](../specs/student/spec.md) |
| KSH-4.3-FE | Inline Lesson Detail via `?lesson=` query param | [student/spec.md#ksh-43-fe](../specs/student/spec.md) |

---

## 🔲 Sprint 5 — Assignments & Grade Book (Planned)

| ID | Story | Spec |
|----|-------|------|
| KSH-3.5 | Assignment CRUD | [classes/spec.md](../specs/classes/spec.md#ksh-35) |
| KSH-3.5-FE | Scores Tab (grade book table) | [classes/spec.md](../specs/classes/spec.md#ksh-35) |
| KSH-3.7 | Student Assignment Submission | classes/spec.md [TBD] |

---

## 🔲 Sprint 6 — Department, User Mgmt & Vocabulary (Planned)

| ID | Story | Spec |
|----|-------|------|
| KSH-4.3 | Admin User Management (activate/deactivate, role change) | [admin/spec.md](../specs/admin/spec.md#ksh-43) |
| KSH-4.4 | Department Management | [admin/spec.md](../specs/admin/spec.md#ksh-44) |
| KSH-5.1 | Vocabulary Set CRUD | [vocabulary/spec.md](../specs/vocabulary/spec.md) |
| KSH-5.2 | Spaced Repetition Practice | [vocabulary/spec.md](../specs/vocabulary/spec.md#ksh-52) |
| KSH-5.3 | Korean Pronunciation Audio | [vocabulary/spec.md](../specs/vocabulary/spec.md#ksh-53) |
| KSH-0.6 | V11 Departments migration | [shared/spec.md](../specs/shared/spec.md) |
| KSH-0.6 | V12 Vocabulary migration | [shared/spec.md](../specs/shared/spec.md) |

---

## Team Split (Sprint 4 Example)

```
Member 1 (toannq1424)  — KSH-4.1-BE (StudentLessonsTabController)
Member 2              — KSH-4.1-FE (student/class-lessons.html template)
Member 3              — KSH-4.2-BE (StudentLessonDetailController)
Member 4              — KSH-4.2-FE (student/lesson-detail.html template)
Member 5              — KSH-4.3-FE (?lesson= inline detail + slide-in panel)
```

---

## ✅ Sprint 7 — Practice AI Scoring (4 kỹ năng TOPIK)

> Spec: [practice/practice-feature.md](../specs/practice/practice-feature.md)

### Đã hoàn thành (Shipped)

| ID | Story | Status | Notes |
|----|-------|--------|-------|
| KSH-7.0-DB | Practice schema (practice_sets, practice_questions, practice_submissions) | ✅ Done | Flyway migrations; soft-delete on sets |
| KSH-7.1-BE | PDF Import pipeline (PracticePdfImportService, AI parse) | ✅ Done | Upload PDF → AI extract questions → DB |
| KSH-7.2-BE | Practice submission flow (PracticeService.submit) | ✅ Done | MCQ key-match + ESSAY AI + SPEAKING mock |
| KSH-7.3-BE | Writing AI 3-pass evaluation (WritingEvaluationClient) | ✅ Done | Pass1: overview, Pass2: details, Pass3: upgrade |
| KSH-7.4-BE | WritingRuleEngine pre-processing (blacklist, task type, char count) | ✅ Done | 18 spoken-language violations; Q51/52/53/54/GENERAL |
| KSH-7.5-BE | WritingScoreMatrix (1.0–9.0 → /100 scale) | ✅ Done | `toHundredPointScale()` |
| KSH-7.6-BE | WritingEvaluationCacheService (SHA-256, TTL 30m) | ✅ Done | In-memory ConcurrentHashMap |
| KSH-7.7-BE | WritingMockEvaluatorService (fallback khi không có API key) | ✅ Done | Heuristic char-count scoring |
| KSH-7.8-BE | Re-evaluation / Audit Mode | ✅ Done | `POST /practice/submissions/{id}/re-evaluate` |
| KSH-7.9-BE | AnswerExplanationClient (Reading/Listening AI giải thích) | ✅ Done | 3 trường: meaningVi, transcriptExplanationVi, eliminatedOptionsVi |
| KSH-7.10-FE | Practice index — 3-col dashboard layout, pill filter, right panel | ✅ Done | Redesign theo phong cách SaaS; practice-index.css |
| KSH-7.11-FE | Result page — 5 tabs: Tổng quan / Điểm mạnh / Cần cải thiện / Bài nâng cấp / Bài mẫu | ✅ Done | JS renders từ aiFeedbackJson |
| KSH-7.12-FE | practice.js — chip filter, XML annotation parser, rubric toggle | ✅ Done | —|

### Bugs đã fix trong sprint này

| ID | Bug | Status |
|----|-----|--------|
| B1 | Score fallback `11.11` vô nghĩa | ✅ Fixed → `WritingScoreMatrix.toHundredPointScale(1.0)` |
| B2 | Speaking hardcode score `70` | ✅ Fixed → `extractAiScore(aiFeedback)` |
| B3 | Rubric `matchOrFallback()` exact match vỡ khi AI trả tên khác | ✅ Fixed → `contains()` substring |
| B4 | `AnswerExplanationClient.systemPrompt()` viết không dấu | ✅ Fixed → tiếng Việt đầy đủ |
| B5 | Score UI hiển thị `/9` gây nhầm IELTS | ✅ Fixed → `toHundred()` → `/100` |

### In Progress / Planned trong sprint này

| ID | Story | Status | Notes |
|----|-------|--------|-------|
| KSH-7.13-BE | Refactor Details pass: bỏ XML annotation, dùng `annotations[]` JSON start/end | 🔄 In Progress | Xem ADR bên dưới |
| KSH-7.14-FE | Refactor practice.js: render highlight từ `annotations[]` thay parse XML | 🔄 In Progress | — |
| KSH-7.15-BE | Speaking AI thật (text-based evaluation) | 🔲 Planned | Sau khi Writing hoàn thiện |
| KSH-7.16-DB | `writing_format` field (Won-go-ji mode flag trên practice_sets) | 🔲 Planned | Flyway migration V?? |

---

### ADR-001: Bỏ XML Annotation, dùng `annotations[]` JSON start/end

**Ngày:** 2026-07-01  
**Trạng thái:** Accepted & In Progress  
**Người đề xuất:** Expert review

**Vấn đề với XML annotation cũ:**
- AI có thể sinh tag sai syntax hoặc thiếu tag đóng → ký tự Hàn bị nuốt (đã xảy ra, xem screenshot)
- FE phải viết parser XML/regex phức tạp và dễ lỗi
- Tag có thể bị lộ nguyên văn ra UI nếu regex không match
- Evidence trùng nhiều lần khó map đúng vị trí
- Khoảng trắng, xuống dòng, ký tự Hàn đặc biệt gây lỗi parse

**Giải pháp mới: `annotations[]` JSON với start/end index**

```json
{
  "student_text": "제가 한국어를 배우는 이유는...",
  "annotations": [
    {
      "criterionId": "W_REGISTER_HONORIFIC_ACCURACY",
      "kind": "strength",
      "evidence": "습니다",
      "start": 45,
      "end": 48,
      "explanationVi": "Đuôi câu 습니다 nhất quán với văn phong viết trang trọng.",
      "correction": ""
    }
  ]
}
```

**Lợi ích:**
- FE chỉ cần slice `text.substring(start, end)` — không parse XML
- Backend Java hậu xử lý: sau khi AI trả `strengths/needs`, tự tính `start/end` bằng `String.indexOf(evidence)` trên `learnerAnswer`
- Không bao giờ lộ tag sai ra UI
- Dễ mở rộng thêm field (confidence, severity)

**Files thay đổi:**
- `WritingPromptRules.buildDetailsPrompt()` — bỏ [QUY TẮC XML ANNOTATION], bỏ yêu cầu `student_strengths_annotated/student_needs_annotated`
- `WritingEvaluationClient.detailsSchema()` — thay `student_*_annotated: string` bằng `annotations: array`
- `WritingEvaluationClient.merge()` — tính start/end từ evidence và đưa vào `annotations`
- `practice.js` — `renderFindings()` và `updateAnswerHighlights()` dùng `annotations` JSON

---
