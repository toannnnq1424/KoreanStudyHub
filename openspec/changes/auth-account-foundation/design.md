# Design: Authentication & Account Foundation

**Status:** ✅ Approved (Completed)  
**Feature:** KSH-1.1 - 1.4  
**Sprint:** 1

---

## 1. Architecture Overview

```
User/Browser ──► AuthController / Spring Security Filter Chain
                    │
                    ├──► CustomUserDetailsService ──► UserRepository (Email login)
                    ├──► CustomOidcUserService   ──► UserOAuthProviderRepository (Google OAuth)
                    └──► PasswordRecoveryService  ──► PasswordResetTokenRepository (Reset flow)
```

### Flow Google OAuth2
1. Người dùng nhấn nút "Đăng nhập bằng Google".
2. Spring Security redirect đến trang đăng nhập Google.
3. Google trả về OAuth2 Authorization Code.
4. Spring Security trao đổi lấy ID Token, gọi `CustomOidcUserService`.
5. Hệ thống tìm kiếm user theo `email`.
   - Nếu đã tồn tại: Liên kết với bản ghi `user_oauth_providers` nếu chưa có.
   - Nếu chưa tồn tại: Tạo mới `User` (role = STUDENT, is_email_verified = true), ghi nhận vào `user_oauth_providers`.
6. Trả về `CustomOidcUserPrincipal` chứa thông tin đăng nhập thành công.

---

## 2. Database Schema

### `users` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `email` varchar(255) UNIQUE NOT NULL
- `password_hash` varchar(255) (null đối với tài khoản chỉ dùng Google OAuth2)
- `full_name` varchar(255) NOT NULL
- `role` varchar(50) NOT NULL (STUDENT, LECTURER, HEAD, ADMIN)
- `is_active` tinyint(1) DEFAULT 1
- `is_email_verified` tinyint(1) DEFAULT 0
- `avatar_url` varchar(500)
- `created_at` timestamp DEFAULT CURRENT_TIMESTAMP

### `user_oauth_providers` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `user_id` bigint NOT NULL (FK users.id)
- `provider` varchar(50) NOT NULL (e.g., 'GOOGLE')
- `provider_user_id` varchar(255) NOT NULL
- UNIQUE KEY `uk_provider_user` (`provider`, `provider_user_id`)

### `password_reset_tokens` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `user_id` bigint NOT NULL (FK users.id)
- `token` varchar(255) UNIQUE NOT NULL
- `expires_at` datetime NOT NULL
- `used_at` datetime DEFAULT NULL

---

## 3. Spring Security Configuration
Cấu hình trong `SecurityConfig.java` bảo mật tất cả các endpoint ngoại trừ:
- Trang chủ `/`, `/about`, `/contact`
- Giao diện login, register, forgot/reset password: `/login`, `/register`, `/forgot-password`, `/reset-password`
- Các tài nguyên tĩnh: `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/css/**", "/js/**").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .requestMatchers("/lecturer/**").hasAnyRole("LECTURER", "HEAD", "ADMIN")
        .requestMatchers("/student/**").hasAnyRole("STUDENT", "LECTURER", "HEAD", "ADMIN")
        .anyRequest().authenticated()
    )
    .formLogin(form -> form
        .loginPage("/login")
        .successHandler(customAuthenticationSuccessHandler)
        .permitAll()
    )
    .oauth2Login(oauth -> oauth
        .loginPage("/login")
        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
        .successHandler(customAuthenticationSuccessHandler)
    )
    .logout(logout -> logout
        .logoutSuccessUrl("/login?logout")
        .permitAll()
    );
```
---

## 4. UI Templates
Sử dụng Thymeleaf + app.css để tạo giao diện hiện đại, responsive, bo tròn các góc và dùng background gradient chuyên nghiệp.
- `auth/login.html`: Box đăng nhập, nút Google, hiển thị thông báo lỗi `?error` hoặc thành công `?registered`.
- `auth/register.html`: Các trường nhập thông tin, check trùng mật khẩu bằng javascript.
- `auth/forgot-password.html` & `auth/reset-password.html`: Biểu mẫu đơn giản gửi link mail và đổi mật khẩu mới.
