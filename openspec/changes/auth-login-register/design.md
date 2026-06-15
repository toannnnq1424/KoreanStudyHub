## Context

The KoreanStudyHub project currently lacks user identity and session management. This change introduces the foundation of user authentication by implementing registration and login mechanisms. The project uses Spring Boot and Thymeleaf, so a traditional session-based authentication strategy using Spring Security fits the architecture best.

## Goals / Non-Goals

**Goals:**
- Implement secure User Registration (saving to MySQL with BCrypt hashed passwords).
- Implement User Login using Spring Security Form Login.
- Use session cookies to manage user state.
- Implement account lockout logic (5 failed attempts = 30-minute lock).
- Enforce Role-Based Access Control (RBAC) assigning `ROLE_STUDENT` to new users by default.
- Create user-friendly frontend UI with Thymeleaf and Bootstrap.

**Non-Goals:**
- Forgot password / Password reset (will be implemented in a separate change).
- Profile update (will be implemented in a separate change).
- Social login (OAuth2 / Google / Facebook).
- Stateless JWT implementation (since the frontend is server-side rendered via Thymeleaf, session cookies are more appropriate).

## Decisions

**1. Authentication Mechanism:** Session-based Authentication vs JWT
- *Decision:* Session-based Authentication using Spring Security.
- *Rationale:* The frontend uses Thymeleaf for server-side rendering. Handling JWTs securely in a traditional SSR app requires extra overhead (storing in HTTP-only cookies anyway). Spring Security's native `SecurityFilterChain` with `formLogin()` is robust and perfectly suited for this stack.

**2. Password Storage:** BCrypt
- *Decision:* Passwords will be hashed using `BCryptPasswordEncoder`.
- *Rationale:* Industry standard for Spring Boot applications, ensuring passwords are not stored in plaintext.

**3. Account Lockout Logic**
- *Decision:* Track failed login attempts in the `User` entity (e.g., `failedAttemptCount`, `lockTime`).
- *Rationale:* We can intercept failed logins using Spring Security's `AuthenticationFailureHandler` and successful logins using `AuthenticationSuccessHandler`. If `failedAttemptCount >= 5`, we set `lockTime` and reject authentication for 30 minutes.

## Risks / Trade-offs

- **Risk:** Session Fixation / Session Hijacking.
  *Mitigation:* Spring Security provides built-in protection against session fixation. Ensure HTTPS is enforced in production to protect the `JSESSIONID` cookie.
- **Risk:** Lockout logic might lock legitimate users if they forget their password.
  *Mitigation:* The 30-minute lock is a reasonable tradeoff to prevent brute-force attacks. Future implementation of "Forgot Password" will alleviate this friction.
