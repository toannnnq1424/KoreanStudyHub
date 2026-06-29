# Proposal: Authentication & Account Foundation (KSH-1.1 - 1.4)

**Status:** ✅ Approved (Completed)  
**Proposed by:** toannq1424  
**Date:** 2026-06-08  
**Sprint target:** Sprint 1  
**Spec reference:** [specs/auth/spec.md](../../specs/auth/spec.md)

---

## 1. Problem Statement

Hệ thống KSH (Korean Study Hub) yêu cầu một giải pháp định danh và xác thực đầy đủ để người dùng (Học viên, Giảng viên, Quản trị viên) có thể tạo tài khoản, đăng nhập an toàn, sử dụng tài khoản Google để đăng nhập nhanh, và khôi phục mật khẩu khi quên. Đây là nền tảng bảo mật cho toàn bộ các chức năng lớp học, bài giảng và chấm điểm tiếp theo.

---

## 2. Proposed Solution

Xây dựng hệ thống tài khoản và phân quyền sử dụng Spring Security 6 và cơ sở dữ liệu MySQL, bao gồm:
1. **Đăng ký tài khoản (KSH-1.1):** Tạo tài khoản học viên/giảng viên, mã hóa mật khẩu bằng BCrypt, ngăn chặn trùng lặp email.
2. **Đăng nhập Email/Mật khẩu (KSH-1.2):** Cấu hình Spring Security form-login, hỗ trợ phân quyền vai trò (STUDENT, LECTURER, HEAD, ADMIN) và chuyển hướng sau đăng nhập tương ứng.
3. **Đăng nhập Google OAuth2 (KSH-1.3):** Liên kết đăng nhập Google qua `oauth2Login()`. Tự động tạo tài khoản STUDENT và liên kết nhà cung cấp nếu trùng email.
4. **Khôi phục mật khẩu (KSH-1.4):** Quy trình gửi email chứa token khôi phục ngẫu nhiên (TTL 1 giờ), đổi mật khẩu an toàn, phòng chống dò quét email (Enumeration-safe).

---

## 3. Scope — In / Out

| In scope | Out of scope |
|----------|-------------|
| Đăng ký tài khoản mới | Đăng ký trực tiếp vai trò ADMIN/HEAD (phải được nâng quyền thủ công) |
| Đăng nhập Email/Mật khẩu với BCrypt | MFA (Multi-Factor Authentication) |
| Đăng nhập Google OAuth2 (OIDC) | Facebook/Github OAuth2 |
| Gửi mail token khôi phục mật khẩu | Quản lý lịch sử thiết bị đăng nhập |
| Form login có giao diện đẹp mắt | SSO (Single Sign-On) liên kết nhiều hệ thống khác |

---

## 4. Affected Files

### Mới tạo (NEW) / Sửa đổi (MODIFY)

- `com.ksh.entities.User` - Entity ánh xạ bảng `users`.
- `com.ksh.entities.PasswordResetToken` - Entity lưu token reset mật khẩu.
- `com.ksh.entities.UserOAuthProvider` - Entity lưu liên kết mạng xã hội Google.
- `com.ksh.config.SecurityConfig` - Cấu hình Spring Security.
- `com.ksh.features.service.auth.CustomUserDetailsService` & `CustomOidcUserService` - Service tải thông tin người dùng.
- `com.ksh.features.controller.auth.AuthController` & `PasswordRecoveryController` - Xử lý login, register, reset.
- `templates/auth/login.html`, `register.html`, `forgot-password.html`, `reset-password.html` - Giao diện người dùng.

---

## 5. Security Checklist

- [x] Mật khẩu lưu dưới dạng mã hóa BCrypt, không bao giờ lưu plain-text.
- [x] Phòng tránh dò quét tài khoản (Enumeration-safe): Gửi yêu cầu quên mật khẩu luôn trả về cùng thông điệp chung.
- [x] Token reset mật khẩu có thời gian hết hạn là 1 giờ và chỉ sử dụng được 1 lần.
- [x] Token không được in ra trong logs ở mức độ INFO/WARN.
