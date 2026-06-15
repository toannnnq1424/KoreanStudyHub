## Context

The system currently uses form-based login with Spring Security. Adding Google OAuth2 requires integrating the `spring-oauth2-client` module. Adding admin reset-password requires an email-sending capability via SMTP/JavaMail. Both features touch the security configuration and expand the user management capabilities.

## Goals / Non-Goals

**Goals:**
- Allow any visitor to sign in with their Google Account.
- Automatically register a new user if they sign in with Google for the first time (assign `ROLE_STUDENT` by default).
- Allow Admin to trigger a password reset for any user, generating a random password and delivering it via email.

**Non-Goals:**
- Self-service "Forgot Password" email flow (separate scope).
- Linking an existing email/password account to a Google account (account linking).
- Supporting other OAuth2 providers (Facebook, GitHub, etc.) in this change.

## Decisions

**1. OAuth2 User Registration Strategy: Auto-Register on First Login**
- *Decision:* When a Google-authenticated user is not found in the database, automatically create a new `User` record using the Google profile's email and name.
- *Rationale:* The simplest and most user-friendly approach. The alternative (requiring a separate registration step) adds unnecessary friction for new OAuth2 users.
- *Key detail:* Google-OAuth2 users will have `password = null`. The `CustomUserDetailsService` (used for form login) will never be called for these users; Spring Security handles them through the OAuth2 flow separately.

**2. Google OAuth2 Credentials: Environment Variables / application.properties**
- *Decision:* Store `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `application.properties` using `${GOOGLE_CLIENT_ID}` placeholders. In production, inject via environment variables or secrets management.
- *Rationale:* Never hardcode secrets in source code. This is a mandatory security baseline.

**3. Random Password Generation for Admin Reset**
- *Decision:* Use `java.security.SecureRandom` with alphanumeric characters to generate a 10-character random password. Hash it with BCrypt before storing.
- *Rationale:* `SecureRandom` is cryptographically strong, making the generated password hard to guess. It's readily available in the JDK with no extra dependencies.

**4. Email Sending: Spring JavaMailSender with Gmail SMTP**
- *Decision:* Use `spring-boot-starter-mail` with Gmail SMTP (`smtp.gmail.com:587` with STARTTLS) as the default mail provider.
- *Rationale:* Gmail SMTP is free, reliable, and widely used for development/staging. The `spring-boot-starter-mail` module provides a clean `JavaMailSender` interface. For production, swap to a dedicated email service (e.g., SendGrid) by just changing the `application.properties` config.

## Risks / Trade-offs

- **Risk:** Google OAuth2 users have `password = null`, which can break the `CustomUserDetailsService` if it's called for them.
  *Mitigation:* OAuth2 users are authenticated exclusively through the OAuth2 filter chain; the `UserDetailsService` (for form login) is never invoked for them. However, we must ensure the `CustomLoginFailureHandler` does not crash when `findByEmail` returns a user with a null password.
- **Risk:** Gmail SMTP credentials exposed in config.
  *Mitigation:* Use application-specific passwords (not the main Gmail password). Externalize credentials via environment variables, not hardcoded.
- **Risk:** Admin resets a user's password without their knowledge.
  *Mitigation:* The system sends the new password directly to the user's registered email, so the user is immediately notified and can log in.
