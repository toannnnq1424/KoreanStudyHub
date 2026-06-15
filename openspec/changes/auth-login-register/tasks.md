## 1. Database & Entities

- [x] 1.1 Create `Role` entity (id, name: ROLE_ADMIN, ROLE_SUBJECT_LEADER, ROLE_TEACHER, ROLE_STUDENT).
- [x] 1.2 Create `User` entity (id, email, password, status, failedAttemptCount, lockTime) and Many-to-Many mapping with `Role`.
- [x] 1.3 Create `UserRepository` and `RoleRepository` interfaces.

## 2. Core Security Configuration

- [x] 2.1 Add Spring Boot Security and Thymeleaf Extras Security dependencies to `pom.xml`.
- [x] 2.2 Implement `CustomUserDetails` and `CustomUserDetailsService` for authentication.
- [x] 2.3 Create `SecurityConfig` to configure `SecurityFilterChain`, `BCryptPasswordEncoder`, and secure public routes (`/login`, `/register`, `/css/**`, `/js/**`).

## 3. Account Lockout Logic

- [x] 3.1 Implement `CustomAuthenticationFailureHandler` to track failed login attempts (BR-08) and lock account for 30 minutes if >= 5 attempts (BR-09).
- [x] 3.2 Implement `CustomAuthenticationSuccessHandler` to reset failed attempts upon successful login and record login activity (BR-07).

## 4. User Service (Registration)

- [x] 4.1 Implement `UserService.registerUser(UserDto)` to handle user registration.
- [x] 4.2 Add validation for missing fields (BR-03, MSG-03) and email format (BR-01).
- [x] 4.3 Add validation for duplicate email (BR-05, MSG-04).
- [x] 4.4 Hash password using BCrypt (BR-02) and assign default `ROLE_STUDENT` (BR-04).

## 5. Controllers & Web Layer

- [x] 5.1 Create `AuthController` with GET/POST mapping for `/register` and `/login`.
- [x] 5.2 Handle mapping validation messages and redirect logic in controllers.

## 6. Frontend UI (Thymeleaf & Bootstrap)

- [x] 6.1 Create `register.html` using Bootstrap forms and Thymeleaf data binding.
- [x] 6.2 Display relevant messages during registration (MSG-02, MSG-03, MSG-04).
- [x] 6.3 Create `login.html` using Bootstrap forms.
- [x] 6.4 Display relevant messages during login (MSG-05, MSG-06, MSG-07, MSG-08) and logout confirmation (MSG-20).
