# Korean Study Hub (KSH) — OpenSpec

Tài liệu spec và kế hoạch triển khai tính năng cho dự án **Korean Study Hub**.
Theo mô hình OpenSpec: mỗi epic/story được mô tả bằng ngôn ngữ người dùng,
kèm sub-task [DB]/[BE]/[FE]/[Test] và trạng thái thực thi.

---

## 📁 Cấu trúc thư mục

```
openspec/
├── config.yaml                    # Tech stack, conventions, rules
├── README.md                      # File này
├── specs/
│   ├── auth/spec.md               # KSH-1: Auth & Account (Sprint 1 ✅)
│   ├── profile/spec.md            # KSH-2: User Profile (Sprint 2 ✅)
│   ├── classes/spec.md            # KSH-3: Class Management (Sprint 2–5)
│   ├── admin/spec.md              # KSH-4: Admin Dashboard (Sprint 2–5)
│   ├── vocabulary/spec.md         # KSH-5: Korean Vocabulary (Sprint 5)
│   └── shared/spec.md             # KSH-0: Infrastructure (ongoing)
└── changes/
    └── IMPLEMENTATION_ORDER.md    # Sprint roadmap & team split guide
```

---

## 🚀 Sprint Status

| Sprint | Theme | Status |
|--------|-------|--------|
| Sprint 1 | Auth, Registration, OAuth2, Password Recovery | ✅ Done |
| Sprint 2 | Profile, Avatar, Class CRUD, Members, Admin Dashboard, Email Settings | ✅ Done |
| Sprint 3 | Lessons, Materials Upload | 🔲 Planned |
| Sprint 4 | Assignments, Grade Book, Student Submissions | 🔲 Planned |
| Sprint 5 | Department Mgmt, User Mgmt, Korean Vocabulary, Spaced Repetition | 🔲 Planned |

---

## 👥 Team

| Role | GitHub |
|------|--------|
| Full-stack lead | toannq1424 |
| Member 2 | — |
| Member 3 | — |
| Member 4 | — |
| Member 5 | — |

See [IMPLEMENTATION_ORDER.md](changes/IMPLEMENTATION_ORDER.md) for sprint-level task splits.

---

## 🛠️ Tech Stack

- **Backend:** Java 21 · Spring Boot 3 · Spring Security 6 · Spring Data JPA · Flyway
- **Frontend:** Thymeleaf · Vanilla CSS · JavaScript
- **Database:** MySQL 8
- **Build:** Maven (mvnw)
- **Auth:** Email/Password + Google OAuth2 (OIDC)

---

## 📖 Conventions

- All Javadoc and inline comments must be in **English**
- User-facing strings (flash messages, validation errors, email bodies) remain in **Vietnamese**
- Architecture: **feature-based** packages under `com.ksh.*`
- Authorization enforced at the **service layer** (not controller)
- All mutations write to activity log tables where applicable
- Soft-delete preferred over hard-delete for user-visible entities
