# Tasks: Authentication & Account Foundation

**Status:** 🔲 Ready for implementation (Simulated Plan)  
**Sprint:** 1  
**Design doc:** [design.md](./design.md)  
**Spec:** [specs/auth/spec.md](../../specs/auth/spec.md)

---

## Phase 1 — Database & Flyway Migrations

- [ ] **[DB-1] Tạo bảng `users`**
  - Tạo file migration `V1__init_schema.sql` (bảng `users`, `departments`, `classes`, `enrollments`).
  - Cột `role` lưu vai trò người dùng (STUDENT, LECTURER, HEAD, ADMIN).
  - Thêm UNIQUE constraint trên cột `email`.

- [ ] **[DB-2] Tạo bảng `user_oauth_providers`**
  - Tạo file migration `V2__oauth_providers.sql`.
  - Cột `user_id` khóa ngoại liên kết tới bảng `users`.
  - Thêm UNIQUE constraint ghép (`provider`, `provider_user_id`).

- [ ] **[DB-3] Tạo bảng `password_reset_tokens`**
  - Tạo file migration `V3__password_reset_tokens.sql`.
  - Thiết lập khóa ngoại `user_id` liên kết tới bảng `users`.
  - Token được lưu trữ bằng chuỗi VARCHAR(255).

---

## Phase 2 — Backend Implementation

- [ ] **[BE-1] Cấu hình Spring Security**
  - Viết `SecurityConfig.java` bảo vệ các route theo vai trò.
  - Kích hoạt `.formLogin()` và `.oauth2Login()`.
  - Viết success handler role-based redirect sau khi login thành công.

- [ ] **[BE-2] Custom User Details Service**
  - Viết `CustomUserDetailsService` thực hiện truy vấn từ DB.
  - Viết `KshUserDetails` kế thừa `UserDetails` để lưu thông tin định danh mở rộng (full_name, role, v.v.).

- [ ] **[BE-3] Xử lý Đăng ký tài khoản**
  - Viết `RegisterForm` DTO với các annotations validation như `@Email`, `@NotBlank`, `@Size`.
  - Cấu hình controller xử lý POST `/register`, mã hóa mật khẩu với `PasswordEncoder` (BCrypt).
  - Xử lý lỗi trùng lặp email và trả về lỗi phù hợp cho view.

- [ ] **[BE-4] Google OAuth2 Authentication**
  - Viết `CustomOidcUserService` để xử lý thông tin trả về của Google.
  - Tự động tạo user mới trong DB nếu email chưa có (gán vai trò STUDENT).
  - Liên kết bản ghi `user_oauth_providers`.

- [ ] **[BE-5] Quên mật khẩu & Token reset**
  - Tạo token an toàn (96 bytes Base64 ngẫu nhiên).
  - Viết logic gửi mail khôi phục thông qua `MailService` (DbConfiguredMailSender).
  - Đảm bảo endpoint quên mật khẩu là Enumeration-safe.

---

## Phase 3 — Frontend Views

- [ ] **[FE-1] View Đăng nhập (login.html)**
  - Thiết kế màn hình đăng nhập với input Email, Password.
  - Thêm nút "Đăng nhập bằng Google".
  - Hiển thị banner lỗi khi có tham số `?error`.

- [ ] **[FE-2] View Đăng ký (register.html)**
  - Form đăng ký đầy đủ Họ tên, Email, Mật khẩu, Nhập lại mật khẩu.
  - Sử dụng Javascript để kiểm tra độ khớp của mật khẩu trước khi submit.
  - Hiển thị validation error từng trường bằng Thymeleaf `th:errors`.

- [ ] **[FE-3] Màn hình Quên mật khẩu & Reset mật khẩu**
  - Tạo form điền email yêu cầu cấp lại mật khẩu.
  - Tạo form nhập mật khẩu mới khi người dùng click vào link token từ email.

---

## Phase 4 — Testing & Verification

- [ ] **[Test-1] Unit test kiểm tra đăng ký**
  - Đăng ký thành công, mật khẩu trong DB đã được mã hóa.
  - Đăng ký thất bại khi email đã tồn tại.
- [ ] **[Test-2] Unit test đăng nhập form login**
  - Đăng nhập thành công với vai trò STUDENT / LECTURER.
  - Đăng nhập thất bại khi sai thông tin.
- [ ] **[Test-3] Kiểm thử luồng Quên mật khẩu**
  - Gửi mail thành công, sinh token có giá trị sử dụng 1 giờ.
  - Token chỉ được sử dụng tối đa 1 lần để đổi mật khẩu.
