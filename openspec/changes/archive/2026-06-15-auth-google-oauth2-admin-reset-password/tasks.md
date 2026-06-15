## 1. Dependencies & Configuration

- [x] 1.1 Add `spring-boot-starter-oauth2-client` and `spring-boot-starter-mail` dependencies to `pom.xml`.
- [x] 1.2 Add Google OAuth2 client config to `application.properties` (`spring.security.oauth2.client.registration.google.*`).
- [x] 1.3 Add SMTP mail config to `application.properties` (`spring.mail.*`).
- [x] 1.4 Update `User` entity to allow nullable password (for OAuth2 users) and add `provider` field.

## 2. Google OAuth2 Login

- [x] 2.1 Create `CustomOAuth2UserService` to handle OAuth2 post-authentication (lookup or auto-create user with `ROLE_STUDENT`).
- [x] 2.2 Create `CustomOAuth2LoginSuccessHandler` to redirect Google users after login (same logic as form login: Admin → `/admin`, others → `/student/dashboard`).
- [x] 2.3 Update `SecurityConfig` to enable `oauth2Login()` with custom user service and success handler.
- [x] 2.4 Add "Sign in with Google" button to `login.html`.

## 3. Admin Reset Password

- [x] 3.1 Create `EmailService` with a `sendNewPassword(toEmail, newPassword)` method using `JavaMailSender`.
- [x] 3.2 Add `resetPasswordByAdmin(Long userId)` method to `UserAdminService` that generates a random password, hashes it, updates the user, and sends the email.
- [x] 3.3 Add `POST /admin/users/{id}/reset-password` endpoint to `AdminController`.
- [x] 3.4 Add "Reset Password" button in Admin user management UI with confirmation dialog.
