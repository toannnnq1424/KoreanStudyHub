## Why

Currently, the KoreanStudyHub platform lacks user authentication, meaning users cannot register or log in. This change implements the foundational authentication flows (Registration, Login, and Session Management) required to secure the platform and identify users, specifically for the "Student" role initially.

## What Changes

- Implement user registration endpoint with email, password, and basic profile info.
- Implement user login endpoint with session generation (JWT or Session cookie).
- Implement password hashing using BCrypt.
- Create UI forms for Registration and Login using Thymeleaf and Bootstrap.
- Set up Spring Security to protect authenticated routes and manage user sessions.
- Automatically assign the `ROLE_STUDENT` to newly registered users.
- Implement account lockout logic (lock for 30 mins after 5 failed attempts).

## Capabilities

### New Capabilities
*(None - implementing existing specs)*

### Modified Capabilities
*(None - implementing existing specs from `openspec/specs/authentication/spec.md` without changing the requirements)*

## Impact

- **Database**: Adds `users` and `roles` tables (or updates existing ones) to store credentials and role mappings.
- **Backend**: Integrates Spring Security into the application architecture.
- **Frontend**: Adds public-facing authentication pages.
