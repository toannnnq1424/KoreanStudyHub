# KSH-0: Shared Infrastructure Spec

## Overview

Cross-cutting concerns shared across all features: Security configuration,
mail sending, file upload, system settings, exception handling, and
Flyway database migrations.

**Labels:** shared, infrastructure, core
**Priority:** Highest (foundation for all epics)

---

## KSH-0.1 — Security Configuration

**Status:** Done

- Config: `com.ksh.shared.config.SecurityConfig`
- Patterns:
  - `/login`, `/register`, `/forgot-password`, `/reset-password`, `/css/**`, `/js/**`, `/images/**`, `/uploads/**` — public
  - `/admin/**` — ROLE_ADMIN only
  - `/lecturer/**` — ROLE_LECTURER, ROLE_HEAD, ROLE_ADMIN
  - `/student/**` — authenticated
  - Everything else — authenticated
- CSRF: enabled (Spring default)
- Session: Spring Security default session management
- Remember-me: not implemented (Sprint 5 candidate)

**OAuth2:**
- Provider: Google (OIDC)
- Callback: `/login/oauth2/code/google`
- Custom service: `CustomOidcUserService` handles account linking

---

## KSH-0.2 — Mail Service

**Status:** Done

- Interface: `com.ksh.shared.mail.MailService`
  - `send(to, subject, body): boolean` — best-effort, returns false on failure
  - `sendWithDetail(to, subject, body): MailSendResult` — returns ok + error message
- Implementation: `DbConfiguredMailSender` — reads SMTP config from `system_settings` at send time (no restart needed)
- `MailSendResult`: record with `ok`, `errorMessage`

**Configuration flow:**
1. Admin sets SMTP in `/admin/settings/email`
2. Settings stored in `system_settings` table
3. `DbConfiguredMailSender` reads settings fresh each send
4. No application restart required after config change

---

## KSH-0.3 — File Upload / Avatar Storage

**Status:** Done

- Service: `com.ksh.shared.upload.AvatarStorageService`
- Storage root: `${app.upload-dir:uploads}/avatars/`
- Config: `WebConfig` exposes `/uploads/**` as static resource
- Validation: non-empty, ≤2 MB, JPEG/PNG/WebP, magic bytes check (not just extension)
- File naming: UUID-based to prevent collisions

---

## KSH-0.4 — System Settings

**Status:** Done

- Entity: `com.ksh.shared.settings.entity.SystemSetting`
- Repository: `SystemSettingsRepository.loadGroupAsMap(group)` — returns all settings for a group as Map
- Groups constant: `com.ksh.shared.settings.SystemSettingGroups`
  - `SMTP = "smtp"`
  - Add new groups here as features require (e.g., `"storage"`, `"security"`)

---

## KSH-0.5 — Exception Handling

**Status:** Done

- Handler: `com.ksh.shared.exception.GlobalExceptionHandler`
- `EntityNotFoundException` → 404 error page
- `AccessDeniedException` → 403 error page
- Generic `Exception` → 500 error page
- Error templates: `error/404.html`, `error/403.html`, `error/500.html`

---

## KSH-0.6 — Database Migrations (Flyway)

**Status:** Ongoing

| Version | Description |
|---------|-------------|
| V1 | `init_schema.sql` — users, roles tables |
| V2 | `seed_roles.sql` — seed role data |
| V3 | `password_reset_tokens.sql` |
| V4 | `user_profile_fields.sql` — phone, bio, avatar_url |
| V5 | `classes.sql` — classes, class_activities |
| V6 | `enrollments.sql` |
| V7 | `system_settings.sql` |
| V8 | `user_oauth_providers.sql` |
| V9 | `seed_smtp_settings.sql` |
| V10 | `lessons_assignments.sql` [Sprint 3 — Planned] |
| V11 | `departments.sql` [Sprint 5 — Planned] |
| V12 | `vocabulary.sql` [Sprint 5 — Planned] |

**Conventions:**
- File pattern: `V{n}__{description}.sql`
- Never edit existing migrations; create new ones for changes
- Always include rollback considerations in PR description (Flyway is forward-only)
