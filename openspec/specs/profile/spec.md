# KSH-2: User Profile Epic

## Overview

Authenticated users can view and update their personal profile information,
upload a custom avatar, and change their password.

**Sprint:** 2 (Completed)
**Labels:** profile, core
**Priority:** High

---

## KSH-2.1 — Story: View & Edit Profile

> As an authenticated user, I want to view and update my full name, phone number,
> and bio on my profile page.

### KSH-2.1-DB: [DB] Profile Fields — Schema

**Status:** Done

- Entity: `com.ksh.entities.User` (extended with profile fields)
- Added columns: `phone`, `bio`, `avatar_url`, `updated_at`
- Migration: `V4__user_profile_fields.sql`

---

### KSH-2.1-BE: [BE] Profile View & Update

**Status:** Done

- Controller: `com.ksh.features.controller.profile.ProfileController`
  - `GET /profile` — load current user data into form
  - `POST /profile` — validate and save
- DTOs: `com.ksh.features.dto.profile.ProfileDtos.ProfileForm`
- Service: `com.ksh.features.service.profile.ProfileService`

**Acceptance criteria:**
- Only the authenticated user can update their own profile
- Full name is required (non-blank, ≤100 chars)
- Phone is optional (validated format if provided)
- Flash message shown on successful save

---

### KSH-2.1-FE: [FE] Profile View

**Status:** Done

- Template: `src/main/resources/templates/profile/profile.html`
- Shows avatar, full name, email (read-only), role badge
- Edit form with inline validation errors

---

## KSH-2.2 — Story: Avatar Upload

> As a user, I want to upload a profile picture (JPEG/PNG/WebP, max 2 MB)
> so that my profile is personalized.

### KSH-2.2-BE: [BE] Avatar Upload

**Status:** Done

- Controller: `ProfileController` → `POST /profile/avatar`
- Service: `com.ksh.shared.upload.AvatarStorageService`
- Storage: local filesystem under `uploads/avatars/<uuid>.<ext>`
- Validation: non-empty, ≤2 MB, JPEG/PNG/WebP MIME only, magic-bytes check
- Config: `WebConfig` serves `/uploads/**` as static resource

**Acceptance criteria:**
- Old avatar file is deleted when new one is uploaded
- Invalid file type returns error flash, no file saved
- File > 2 MB rejected before writing to disk

---

## KSH-2.3 — Story: Change Password

> As an authenticated user, I want to change my password by providing my current password
> and a new password, so that I can maintain account security.

### KSH-2.3-BE: [BE] Change Password

**Status:** Done

- Controller: `com.ksh.features.controller.profile.ChangePasswordController`
  - `GET /profile/change-password`
  - `POST /profile/change-password`
- DTOs: `com.ksh.features.dto.profile.ProfileDtos.ChangePasswordForm`
- Service: `ProfileService.changePassword()`

**Acceptance criteria:**
- Current password must match BCrypt hash in DB
- New password must be ≥8 chars
- Confirm password must match new password
- OAuth2-only users who have no password_hash are shown an appropriate message
- Flash success → redirect to profile page

---
