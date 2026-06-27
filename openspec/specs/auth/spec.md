# KSH-1: Authentication & Account Epic

## Overview

Users must be able to create accounts, log in (email/password and Google OAuth2),
recover forgotten passwords, and manage basic account security.
This is the core identity layer that all other KSH features depend on.

**Sprint:** 1 (Completed)
**Labels:** auth, core
**Priority:** Highest

---

## KSH-1.1 — Story: User Registration

> As a new user, I want to create a KSH account using my full name, email, and password
> so that I can log in and access the platform.

### KSH-1.1-DB: [DB] User Registration — Schema

**Status:** Done

- Entity: `com.ksh.auth.entity.User`
- Table: `users`
- Migration: `V1__init_schema.sql`
- Key columns: `id`, `email` (unique), `full_name`, `password_hash`, `role`, `is_active`, `is_email_verified`, `created_at`
- Role stored as VARCHAR ENUM: STUDENT, LECTURER, HEAD, ADMIN

**Acceptance criteria:**
- `email` column has a UNIQUE constraint
- `role` defaults to STUDENT on insert
- `is_active` defaults to TRUE

---

### KSH-1.1-BE: [BE] User Registration — Backend

**Status:** Done

- Controller: `com.ksh.auth.controller.AuthController` → `POST /register`
- DTOs: `com.ksh.auth.dto.AuthDtos.RegisterForm`
- Service: `AuthController` inline (simple path), password encoded with BCrypt
- Validation: `@NotBlank`, `@Email`, `@Size`, password confirm match (cross-field)

**Acceptance criteria:**
- Duplicate email returns form with error message
- Password is BCrypt-encoded before persisting
- User is redirected to `/login?registered=true` on success

---

### KSH-1.1-FE: [FE] User Registration — View

**Status:** Done

- Template: `src/main/resources/templates/auth/register.html`
- Inline validation errors via `th:errors`
- Password confirmation field validated client-side AND server-side

**Acceptance criteria:**
- All field errors shown inline under the respective field
- Form re-populated on validation failure (no data loss)

---

### KSH-1.1-Test: [Test] User Registration

**Status:** Planned

- Unit test: `AuthControllerTest` — GET /register, POST /register (success, duplicate email, weak password)
- Unit test: `RegisterForm` cross-field validation
- Integration test: end-to-end register → login

---

## KSH-1.2 — Story: Email/Password Login

> As a registered user, I want to log in with my email and password
> so that I can access my dashboard.

### KSH-1.2-BE: [BE] Login — Spring Security

**Status:** Done

- Config: `com.ksh.shared.config.SecurityConfig`
- `UserDetailsService`: `com.ksh.auth.service.CustomUserDetailsService`
- `UserDetails` impl: `com.ksh.auth.service.KshUserDetails`
- Login page: `GET /login` | form action: `POST /login` (handled by Spring Security)
- Success redirect: role-based (`/admin/dashboard`, `/lecturer/classes`, `/student/home`)

**Acceptance criteria:**
- Invalid credentials → back to `/login?error`
- Disabled account → `/login?disabled`
- Session established with correct ROLE_ authority

---

### KSH-1.2-FE: [FE] Login — View

**Status:** Done

- Template: `src/main/resources/templates/auth/login.html`
- Shows error banner when `?error` param present
- Shows success banner when `?registered=true` param present

---

## KSH-1.3 — Story: Google OAuth2 Login

> As a user, I want to sign in with my Google account
> so that I don't need to manage a separate password.

### KSH-1.3-BE: [BE] Google OAuth2

**Status:** Done

- Config: `SecurityConfig` — `oauth2Login()` enabled
- Custom service: `com.ksh.auth.service.CustomOidcUserService`
- Principal wrapper: `com.ksh.auth.service.CustomOidcUserPrincipal`
- On first OAuth2 login: creates User row (role=STUDENT, is_email_verified=true)
- On subsequent logins: loads existing user by email
- Linked provider stored in `user_oauth_providers` table

**Acceptance criteria:**
- New Google user gets a STUDENT account auto-created
- Existing email/password user who signs in via Google gets accounts linked
- OAuth2 callback returns 302 to role-based dashboard

---

## KSH-1.4 — Story: Forgot Password / Password Recovery

> As a user who has forgotten their password, I want to request a reset link via email
> so that I can regain access to my account.

### KSH-1.4-DB: [DB] Password Reset Token — Schema

**Status:** Done

- Entity: `com.ksh.auth.entity.PasswordResetToken`
- Table: `password_reset_tokens`
- Migration: `V3__password_reset_tokens.sql`
- Key columns: `token` (unique, VARCHAR 200), `user_id` (FK), `expires_at`, `used_at`

---

### KSH-1.4-BE: [BE] Password Recovery Flow

**Status:** Done

- Controllers: `com.ksh.auth.controller.PasswordRecoveryController`
  - `GET /forgot-password` — show request form
  - `POST /forgot-password` — create token, send email
  - `GET /reset-password?token=…` — show new password form
  - `POST /reset-password` — validate token, set new password
- Service: `com.ksh.auth.service.PasswordRecoveryService`
- Token: 96 random bytes → URL-safe Base64, TTL = 1 hour
- Email: best-effort (`MailService.send`), WARN logged on failure, token logged at DEBUG only

**Security notes:**
- Enumeration-safe: always returns same UX regardless of whether email exists
- Token never logged at INFO/WARN (prevents leak to aggregators)

---

### KSH-1.4-FE: [FE] Password Recovery Views

**Status:** Done

- Templates: `auth/forgot-password.html`, `auth/reset-password.html`

---
