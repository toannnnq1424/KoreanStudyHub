## Why

The current authentication system only supports email/password login. Two new capabilities are needed:

1. **Google OAuth2 Login**: Users want the convenience of signing in with their existing Google account instead of creating a separate email/password account, reducing friction during onboarding.

2. **Admin Reset Password**: Admins need the ability to reset a user's password when a user is locked out or unable to recover their account via the forgot-password flow. The system will generate a secure random password and email it directly to the user.

## What Changes

### Google OAuth2 Login
- Add `spring-boot-starter-oauth2-client` dependency.
- Configure Google OAuth2 Client ID and Client Secret in `application.properties`.
- Create a `CustomOAuth2UserService` to handle post-authentication: look up or auto-create the user in the database using the Google account's email.
- Add a "Sign in with Google" button to `login.html`.
- Update `SecurityConfig` to enable `oauth2Login()`.

### Admin Reset Password
- Add `spring-boot-starter-mail` (JavaMailSender) dependency.
- Create an `EmailService` to send plain text emails via SMTP (Gmail).
- Add a `UserAdminService.resetPassword(userId)` method that: generates a secure random 10-character password, hashes it with BCrypt, updates the user record, and sends the new password to the user's email.
- Add a "Reset Password" button/form in the Admin's User Management page wired to a POST endpoint.

## Capabilities

### New Capabilities
- Google OAuth2 Sign-In for users.
- Admin-initiated password reset via email.

### Modified Capabilities
- `SecurityConfig`: Add `oauth2Login()` and OAuth2 user service.
- `login.html`: Add "Sign in with Google" button.
- `AdminController` (or `UserAdminController`): Add reset password POST endpoint.

## Impact

- **Dependencies**: Adds `spring-boot-starter-oauth2-client` and `spring-boot-starter-mail`.
- **Configuration**: Requires `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and SMTP mail credentials (stored securely, NOT hardcoded).
- **Database**: Google OAuth2 users will have a `NULL` password (they authenticate via Google, not locally). The `User` entity must handle this nullable password.
