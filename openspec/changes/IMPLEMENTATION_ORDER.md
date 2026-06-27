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

## 🔲 Sprint 3 — Lessons & Materials (Planned)

| ID | Story | Spec |
|----|-------|------|
| KSH-3.4 | Lessons CRUD (create/edit/publish) | [classes/spec.md](../specs/classes/spec.md#ksh-34) |
| KSH-3.4-FE | Lessons Detail Tab (replaces placeholder) | [classes/spec.md](../specs/classes/spec.md#ksh-34-fe) |
| KSH-3.6 | Material file upload (PDF, DOCX, ZIP) | classes/spec.md [TBD] |
| KSH-0.6 | V10 Flyway migration (lessons + assignments) | [shared/spec.md](../specs/shared/spec.md) |

**Sprint 3 owner:** toannq1424

---

## 🔲 Sprint 4 — Assignments & Grade Book (Planned)

| ID | Story | Spec |
|----|-------|------|
| KSH-3.5 | Assignment CRUD | [classes/spec.md](../specs/classes/spec.md#ksh-35) |
| KSH-3.5-FE | Scores Tab (grade book table) | [classes/spec.md](../specs/classes/spec.md#ksh-35) |
| KSH-3.7 | Student Assignment Submission | classes/spec.md [TBD] |

---

## 🔲 Sprint 5 — Department, User Mgmt & Vocabulary (Planned)

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

## Team Split (Sprint 3 Example)

```
Member 1 (toannq1424)  — KSH-3.4-DB + KSH-3.4-BE (Lessons CRUD backend)
Member 2              — KSH-3.4-FE (Lessons detail-lessons.html template)
Member 3              — KSH-3.6-BE (Material upload service)
Member 4              — KSH-3.6-FE (Materials tab template)
Member 5              — KSH-0.6 V10 migration + unit tests
```
