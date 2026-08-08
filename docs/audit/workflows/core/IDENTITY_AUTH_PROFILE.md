# Workflows Identity: đăng nhập, khôi phục mật khẩu, hồ sơ và đổi mật khẩu

Tài liệu này trace theo đúng thao tác thật của người dùng: phần tử UI/JS → HTTP verb + path → Spring Security/controller → service/repository/entity/storage/mail → redirect hoặc nội dung UI. Phạm vi bao phủ toàn bộ source production trong `features/auth`, `features/profile` và các nhánh security/storage trực tiếp được các màn hình này gọi. Hệ thống **không có workflow tự đăng ký tài khoản**; tài khoản chỉ được Admin tạo.

## 1. Mở màn hình đăng nhập

### Thao tác người dùng

Người dùng chưa đăng nhập mở `GET /login`. Logo và link trợ giúp nằm tại `templates/auth/login.html:74–79`; form đăng nhập nằm tại dòng 109–123.

### Controller và dữ liệu render

`AuthController.loginPage`, `features/auth/controller/AuthController.java:66–87`:

1. Nhận query `error` và `logout` ở dòng 67–69.
2. Gọi `OauthSettingsService.isGoogleEnabled()` ở dòng 70–76. Method này chỉ trả `true` khi cả client id và secret Google không blank (`OauthSettingsService.java:125–129`). Nếu DB lỗi, controller fail-open cho form login nhưng ẩn Google bằng `googleEnabled=false`.
3. Nếu có `?error=oauth_unregistered`, gắn thông báo Google chưa đăng ký; mọi lỗi khác dùng thông báo chung email/mật khẩu sai hoặc tài khoản bị khoá (`AuthController.java:79–82`).
4. Nếu có `?logout`, gắn thông báo đăng xuất thành công (`AuthController.java:83–85`).
5. Render `auth/login` (`AuthController.java:86`).

Template chỉ render nút **“Đăng nhập bằng Google”** khi `googleEnabled=true`, tại `login.html:125–133`. Flash được chuyển thành toast bởi JS tại `login.html:183–195`.

## 2. Đăng nhập bằng email/mật khẩu

### Thao tác người dùng và request

Người dùng điền:

| Field | UI | Giá trị server nhận |
|---|---|---|
| `username` | `login.html:111–113` | chuỗi người dùng nhập |
| `password` | `login.html:115–118` | mật khẩu thô |
| CSRF | Thymeleaf tự bổ sung cho form POST | token session |

Người dùng bấm **“Đăng nhập vào KSH”** tại `login.html:122`:

```text
POST /login
Content-Type: application/x-www-form-urlencoded
```

Đây không phải endpoint controller. `SecurityConfig.java:244–253` cấu hình Spring Security xử lý trực tiếp `POST /login`.

### 1. Chặn brute-force trước khi kiểm mật khẩu

`LoginThrottleFilter.shouldNotFilter`, `security/LoginThrottleFilter.java:22–26`, chỉ chạy cho đúng `POST /login`. `doFilterInternal`, dòng 28–39:

1. Đọc `username` và `request.getRemoteAddr()`.
2. Gọi `LoginAttemptThrottle.isBlocked`.
3. Nếu bị chặn, redirect ngay `/login?error`; password chưa được BCrypt-check.

`LoginAttemptThrottle.java:25–28` đặt cửa sổ 15 phút, tối đa 6 lỗi/account hoặc 30 lỗi/IP, tối đa 20.000 key. Account/IP được normalize rồi SHA-256 trước khi giữ trong map process-local (`LoginAttemptThrottle.java:91–110`). Đây không phải distributed throttle: restart app hoặc chuyển node sẽ mất/reset bucket.

### 2. Load tài khoản và quyền

`CustomUserDetailsService.loadUserByUsername`, `security/CustomUserDetailsService.java:56–65`:

1. Treat `username` là **email**, gọi `UserRepository.findByEmailIgnoreCase` (`UserRepository.java:43`).
2. `User` có `@SQLRestriction("is_deleted = 0")` tại `entities/User.java:30–33`, nên tài khoản soft-delete không được tìm thấy.
3. Gọi `PermissionResolver.resolvePermissions(userId)` và tạo `KshUserDetails`.
4. Nếu RBAC lỗi, `resolvePermissionsSafely` trả set rỗng để login vẫn tiếp tục bằng role-only (`CustomUserDetailsService.java:80–89`).

`PermissionResolver.resolvePermissions`, `features/admin/permissions/service/PermissionResolver.java:82–129`, mở rộng quyền theo `role_hierarchy`, sau đó áp precedence `REVOKE > GRANT > FROM_ROLE`. `KshUserDetails.java:62–82` gắn một `ROLE_<role>` và các `PERM_<featureKey>` vào session principal.

Spring Security dùng BCrypt bean tại `SecurityConfig.java:77–80`, rồi gọi:

- `KshUserDetails.isAccountNonLocked()` → `!locked`, dòng 131;
- `KshUserDetails.isEnabled()` → `active`, dòng 133.

Vì vậy form login chặn cả tài khoản inactive và locked.

> UI ghi “Email hoặc số điện thoại” (`login.html:111`), nhưng backend chỉ query email (`CustomUserDetailsService.java:58–60`). Nhập số điện thoại luôn thất bại.

### 3. Kết quả thất bại

`formFailureHandler`, `SecurityConfig.java:98–108`:

1. Ghi một failure cho account và IP.
2. Redirect `/login?error`.
3. `AuthController` cố ý trả cùng một thông báo cho email không tồn tại, sai password, inactive và locked; không lộ trạng thái account.

Phân biệt runtime: `LoginThrottleFilter.doFilterInternal` chạy trước `UsernamePasswordAuthenticationFilter`. Request đã blocked trả 302 `/login?error` ngay, **không** đi qua failure handler, không tăng counter và không BCrypt-check. Counter chỉ được `formFailureHandler` ghi sau một authentication failure; threshold 6 có hiệu lực với request kế tiếp. Toàn bộ bucket process-local, không có `login_history`/DB audit write và không dùng `X-Forwarded-For` (`LoginAttemptThrottle.java:42–110`).

### 4. Kết quả thành công và chọn trang đích

`formSuccessHandler`, `SecurityConfig.java:110–118`:

1. Xoá failure bucket của account; bucket IP không được xoá (`LoginAttemptThrottle.recordSuccess`, dòng 57–59).
2. Gọi `RoleAwareAuthenticationSuccessHandler.onAuthenticationSuccess`, `security/RoleAwareAuthenticationSuccessHandler.java:28–49`.
3. Saved request chỉ được resume nếu cùng origin và phù hợp role (`RoleNavigation.canResume`, `RoleNavigation.java:43–92`). Nếu không, saved request bị xoá.
4. Home mặc định (`RoleNavigation.java:21–36`): ADMIN → `/admin/dashboard`, LEADER → `/leader`, LECTURER → `/lecturer/classes`, STUDENT → `/my/classes`.

Request-cache loại các poll AJAX/unread ra khỏi post-login destination tại `SecurityConfig.java:120–136`.

`RoleAwareAuthenticationSuccessHandler` không chỉ chọn home: nó chỉ resume `SavedRequest` nếu URL same-origin và `RoleNavigation.canResume` hợp role; URL sai/stale bị remove rồi 302 role home (`RoleAwareAuthenticationSuccessHandler.java:28–81`). Đây không thay controller/service authorization. Form wrapper mới gọi `LoginAttemptThrottle.recordSuccess(username)`; OAuth success dùng trực tiếp role-aware handler nên không clear bucket failure form còn lại.

Không có code production nào cập nhật `users.last_login_at`; field chỉ được đọc (`User.java:71–72`, các projection `UserRepository.java:124–163`). Vì vậy “Đăng nhập cuối” không phản ánh workflow login hiện tại.

## 3. Đăng nhập Google OAuth/OIDC

### Thao tác người dùng và chuỗi HTTP

Người dùng bấm **“Đăng nhập bằng Google”** tại `login.html:128–131`:

```text
GET /oauth2/authorization/google
→ redirect sang Google
→ Google callback vào Spring Security OAuth2 filter
→ CustomOidcUserService.loadUser(...)
```

Không có controller app cho authorization/callback. `SecurityConfig.java:260–267` nối OIDC user service, failure handler và cùng role-aware success handler.

`DbClientRegistrationRepository.findByRegistrationId`, `security/oauth/DbClientRegistrationRepository.java:72–103`, chỉ build registration `google` khi **cả** client id và secret có giá trị (`:78–86`), parse scopes (`:88–95`) và dùng endpoints chuẩn của `CommonOAuth2Provider.GOOGLE` (`:97–102`). Thiếu cấu hình → trả `null`, flow không khởi động.

Direct `GET /oauth2/authorization/google` lúc thiếu client id hoặc secret nhận framework **404** từ registration lookup; không đi qua `oauthFailureHandler`. Chỉ error sau khi OAuth flow đã bắt đầu mới 302 `/login?error=oauth_unregistered`. Điều này khác UI `GET /login`: `AuthController.loginPage` bắt exception khi đọc setting, fail-open form login và chỉ ẩn button Google.

### Backend liên kết tài khoản local

`CustomOidcUserService.loadUser`, `security/CustomOidcUserService.java:64–101`:

1. `super.loadUser` gọi Google user-info/OIDC (`:67`).
2. Bắt buộc claim email không blank (`:69–72`).
3. Tìm `users` bằng email, không tự tạo account (`:74–77`).
4. Chặn `is_locked` (`:79–82`); soft-delete bị `@SQLRestriction` loại khỏi lookup.
5. Nếu `users.google_id` trống, ghi Google `sub` (`:85–90`).
6. Nếu chưa có `(provider="google", provider_user_id=sub)`, insert `user_oauth_providers` (`:92–97`). Unique constraint cặp provider/sub nằm tại `UserOAuthProvider.java:26–29`.
7. Trả `CustomOidcUserPrincipal` và redirect theo `RoleAwareAuthenticationSuccessHandler`.

### Kết quả lỗi

Email chưa có trong KSH, thiếu email hoặc locked đều ném `OAuth2AuthenticationException`; `oauthFailureHandler` redirect `/login?error=oauth_unregistered` (`SecurityConfig.java:92–96`), rồi `AuthController` hiển thị thông báo chung.

### Sai khác bảo mật/runtime quan trọng của nhánh Google

- `CustomOidcUserService` **không kiểm `user.isActive()`** ở `:79–83`. Do đó tài khoản inactive bị chặn ở form login nhưng vẫn có thể đăng nhập Google nếu chưa locked/deleted.
- `CustomOidcUserPrincipal` chỉ gắn `ROLE_<role>` tại `CustomOidcUserPrincipal.java:27–34`; nó không gọi `PermissionResolver` và không có `PERM_*`. Vì các màn Admin dùng `@PreAuthorize("hasAuthority('PERM_...')")`, Admin đăng nhập Google có thể được redirect tới `/admin/dashboard` nhưng nhận 403.
- Các controller Profile/Admin trong tài liệu này inject trực tiếp `@AuthenticationPrincipal KshUserDetails`; OIDC principal là type khác. Spring inject `null`, rồi code gọi `principal.getId()`. Dù project có `AuthenticatedUserIdResolver` hỗ trợ cả hai type (`security/AuthenticatedUserIdResolver.java:10–21`), các controller này không dùng nó. Profile/Admin mutation qua Google vì vậy có thể lỗi 500/NPE ngay cả khi route chỉ cần role.
- Binding Google hiện dựa vào email local ở mỗi lần login; nếu row provider theo `sub` đã tồn tại, code chỉ bỏ qua insert, không xác minh row đó thuộc đúng local user (`CustomOidcUserService.java:92–97`).

## 4. Đăng xuất

### Thao tác người dùng

Trong menu tài khoản, form logout ở `templates/fragments/app-header.html:224–230`; người dùng bấm **“Đăng xuất”**:

```text
POST /logout
```

### Backend và UI

Spring Security xử lý trực tiếp, không qua controller. `SecurityConfig.java:255–259` đặt logout URL và redirect `/login?logout`. CSRF hidden nằm ở `app-header.html:225`; POST thiếu/sai token bị 403. Sau khi Security xoá authentication/session theo mặc định, `AuthController.loginPage` thấy `logout != null` và tạo toast “Bạn đã đăng xuất thành công” (`AuthController.java:83–85`).

Source không configure remember-me, persistent token, JWT hay custom logout handler. Session concurrency là unlimited (`maximumSessions(-1)`), còn `SessionRegistry` được dùng cho revoke theo profile; không được diễn giải logout là revoke mọi device/session.

## 5. Quên mật khẩu: yêu cầu gửi link

### Thao tác người dùng

Từ login, người dùng bấm **“Quên mật khẩu?”** tại `login.html:121`, browser mở:

```text
GET /forgot-password
```

`PasswordRecoveryController.forgotForm`, `features/auth/controller/PasswordRecoveryController.java:53–58`, bind DTO rỗng và render `auth/forgot-password`.

Người dùng nhập `email` tại `forgot-password.html:50–68`, bấm **“Tiếp tục”** tại dòng 71–73:

```text
POST /forgot-password
Content-Type: application/x-www-form-urlencoded
```

JS `forgot-password.html:122–136` chỉ disable nút/đổi chữ “Đang gửi...”; không dùng fetch.

### Controller

`PasswordRecoveryController.forgotSubmit`, dòng 64–74:

1. Bean Validation bắt buộc nonblank + email (`AuthDtos.java:10–12`). Sai → render lại cùng view.
2. Gọi `PasswordRecoveryService.requestReset(email, request.getRemoteAddr())` (`:71`).
3. Luôn flash cùng thông báo trung tính và redirect `GET /forgot-password` (`:72–73`), bất kể email có tồn tại hay mail có gửi được.

### Service, DB token và mail

`PasswordRecoveryService.requestReset`, `features/auth/service/PasswordRecoveryService.java:76–108`, chạy trong một transaction:

1. `PasswordResetRequestThrottle.allow` (`:78`) giới hạn 3 request/15 phút cho cả email và IP, lưu key SHA-256, process-local (`PasswordResetRequestThrottle.java:23–51`). Quá ngưỡng → return im lặng.
2. `UserRepository.findByEmailIgnoreCase` (`:81`). Không có user/deleted → return im lặng.
3. `PasswordResetTokenRepository.invalidateUnusedForUser` đánh dấu `used_at` cho mọi token cũ chưa dùng (`PasswordRecoveryService.java:86–88`; repository `:31–35`).
4. Sinh 96 random bytes bằng `SecureRandom`, Base64 URL-safe (`PasswordRecoveryService.java:170–174`).
5. Chỉ lưu SHA-256 digest của raw token và `expiresAt=now+1h` (`:89–92`, digest `:192–199`).
6. Tạo link `${app.base-url}/reset-password?token=<raw>` và body plain-text (`:94–100`).
7. Gọi mail **đồng bộ** `MailService.send` (`:102–103`), không đưa vào mail outbox.
8. `DbConfiguredMailSender.sendWithDetail`, `features/mail/DbConfiguredMailSender.java:77–97`, load nhóm SMTP, bỏ gửi nếu host trống, build JavaMailSender với timeout 10 giây, gửi MIME, rồi trả boolean. Gửi lỗi vẫn giữ token DB và UI vẫn báo chung; raw link không được log (`PasswordRecoveryService.java:104–107`).

## 6. Mở link và đặt lại mật khẩu

### GET kiểm tra token

Người dùng bấm link trong mail:

```text
GET /reset-password?token=<raw-token>
```

`PasswordRecoveryController.resetForm`, dòng 80–92:

1. Set `Cache-Control: no-store` và `Referrer-Policy: no-referrer` (`:83`, helper `:116–119`).
2. `PasswordRecoveryService.validateToken` digest raw token, load row và kiểm `usedAt == null && now <= expiresAt` (`PasswordRecoveryService.java:125–135`; `PasswordResetToken.java:67–83`).
3. Token invalid/expired/used → `invalid=true`, template hiển thị “Liên kết không hợp lệ” tại `reset-password.html:16–22`.
4. Token hợp lệ → hidden `token` + form new password (`reset-password.html:28–40`).

`findToken` còn thử lookup raw token sau digest (`PasswordRecoveryService.java:176–182`) để tương thích token legacy lưu plain-text.

### POST đổi password

Người dùng nhập mật khẩu 6–64 ký tự và bấm **“Đặt lại mật khẩu”** (`reset-password.html:31–40`):

```text
POST /reset-password
```

`PasswordRecoveryController.resetSubmit`, dòng 98–114:

1. Set no-store/no-referrer.
2. Bean validation sai → giữ token và render lại.
3. Gọi `PasswordRecoveryService.resetPassword(rawToken, newPassword)` (`:107`).
4. Service `@Transactional` dùng `findByTokenForUpdate` với `PESSIMISTIC_WRITE` (`PasswordRecoveryService.java:149–168`; `PasswordResetTokenRepository.java:27–29`), đóng race double-submit.
5. Token vẫn hợp lệ → BCrypt password, save `User`, `markUsed`, save token trong cùng transaction (`PasswordRecoveryService.java:160–167`).
6. Thành công redirect `GET /login` (`PasswordRecoveryController.java:112–113`).

Điểm thực tế:

- Controller flash `resetSuccess=true`, nhưng `AuthController`/`login.html` không đọc/render thuộc tính này; user không nhận toast “reset thành công”.
- Reset qua link không gọi `SessionRevocationService`; các session đang đăng nhập bằng password cũ không bị expire.
- Job retention không có UI: `PasswordResetTokenRetention.cleanup`, `PasswordResetTokenRetention.java:42–53`, mặc định chạy sau 120 giây rồi mỗi giờ, xoá tối đa 500 token đã expired/used cũ hơn 7 ngày (configurable, hard cap 1.000).

## 7. Xem hồ sơ

### Thao tác người dùng

Người dùng bấm **“Hồ sơ”** ở header (`app-header.html:215–218`):

```text
GET /profile
```

### Controller/service/UI

`ProfileController.view`, `features/profile/controller/ProfileController.java:67–76`:

1. Lấy id từ `KshUserDetails`.
2. `ProfileService.getCurrentUser`, `ProfileService.java:31–35`, query `UserRepository.findById` trong read-only transaction.
3. Model có entity `user` và `profileForm(fullName,bio,phone)`.
4. Render `profile.html`: avatar/name/email/role ở dòng 36–52, form upload ở 56–71, form thông tin ở 85–134.

Route `/profile` chỉ cần authenticated theo `SecurityConfig.java:242`; không có permission chi tiết.

## 8. Cập nhật họ tên/bio/điện thoại

### Thao tác người dùng và request

Form `profile.html:85–134` gửi `fullName`, `phone`, `bio` và CSRF. Người dùng bấm **“Lưu thay đổi”** tại dòng 129–132:

```text
POST /profile
Content-Type: application/x-www-form-urlencoded
```

`profile.js:14–22` chỉ đếm ký tự bio, không gọi API.

### Controller/service/entity

`ProfileController.update`, dòng 86–100:

1. Reload chính user từ principal id (`:92`). Client không truyền user id nên không sửa hồ sơ người khác.
2. DTO bắt buộc name 2–100, bio ≤500, phone ≤20 (`ProfileDtos.java:9–13`). Lỗi → render lại view với errors (`ProfileController.java:93–96`).
3. `ProfileService.updateProfile` transaction (`ProfileService.java:46–50`) gọi `User.updateProfile`.
4. Entity giữ fullName và đổi bio/phone blank thành `null` (`User.java:126–130`), save `users`.
5. Flash `profileUpdated=true`, redirect `GET /profile` (`ProfileController.java:97–99`); UI hiện success block `profile.html:20–24`.

Không gửi notification/mail và không cập nhật `KshUserDetails.fullName` trong session. Header dùng principal có thể tiếp tục hiển thị tên cũ đến lần đăng nhập mới, dù trang profile đọc entity mới.

## 9. Tải ảnh đại diện

### Thao tác người dùng và request

Người dùng chọn file `avatar` tại `profile.html:56–70`, bấm **“Tải ảnh lên”**:

```text
POST /profile/avatar
Content-Type: multipart/form-data
```

`profile.js:5–12` chỉ hiện filename; không upload AJAX.

### Controller → storage → DB

`ProfileController.uploadAvatar`, dòng 110–134:

1. Reload chính user theo principal id (`:114`).
2. Gọi `AvatarStorageService.store(file)` (`:116`).
3. `AvatarStorageService.java:36–66` kiểm file không rỗng, ≤2 MB, MIME thuộc JPEG/PNG/WebP, rồi kiểm magic bytes (`:37–50`, `:68–82`).
4. Sinh UUID + extension, key `avatars/<uuid>.<ext>`, gọi `ObjectStorage.put` (`:52–63`). Adapter general uploads ghi đúng storage profile `GENERAL_UPLOADS` (`GeneralUploadsObjectStorage.java:26–30`).
5. Trả public URL `/uploads/avatars/<filename>` (`AvatarStorageService.java:65`).
6. `ProfileService.updateAvatar` lưu URL vào `users.avatar_url` trong transaction (`ProfileService.java:59–63`).
7. `principal.updateAvatarUrl(url)` làm header của form-login session đổi ngay (`ProfileController.java:117–119`).
8. Flash success rồi redirect `/profile`.

Validation/storage lỗi được bắt và đổi thành flash user-facing ở `ProfileController.java:120–132`; không lộ infrastructure exception.

### Browser tải ảnh sau redirect

Template render `<img src="/uploads/avatars/...">` tại `profile.html:38–39`. `GET /uploads/**` do `PublicUploadsController.serve`, `features/upload/PublicUploadsController.java:54–58`, xử lý:

1. Chỉ cho đúng hai segment và folder allowlist `avatars|exams|flashcards` (`:69–87`).
2. Chặn traversal và validate storage key (`:90–95`).
3. `exists/open` object storage, set MIME/length và cache public 1 ngày (`:97–122`).

Điểm transaction/lifecycle: bytes được ghi trước transaction DB cập nhật URL; nếu DB save thất bại, object mới có thể orphan. Ảnh cũ cũng không bị xoá khi thay ảnh.

## 10. Đổi mật khẩu khi đang đăng nhập

### GET form

Người dùng bấm **“Đổi mật khẩu”** tại `app-header.html:219–222`:

```text
GET /change-password
```

`ChangePasswordController.form`, `features/profile/controller/ChangePasswordController.java:57–61`, bind form rỗng và render `change-password.html`.

### POST thay password

Form `change-password.html:24–55` gửi `currentPassword`, `newPassword`, `confirmPassword`, CSRF. Nút **“Đổi mật khẩu”** ở dòng 54:

```text
POST /change-password
```

`ChangePasswordController.change`, dòng 84–119:

1. DTO bắt buộc mọi field; new password 6–64 (`ProfileDtos.java:15–19`).
2. Load user theo principal id (`ChangePasswordController.java:95–96`).
3. BCrypt-check current password (`:98–102`). Sai → render `wrongCurrent`.
4. So sánh new/confirm exact (`:104–108`). Lệch → render `mismatch`.
5. BCrypt encode và save `users.password_hash` (`:110–111`). Controller method không có `@Transactional`; riêng repository `save` có transaction của Spring Data.
6. Lấy current session id, gọi `SessionRevocationService.revokeOtherSessions(email,currentId)` (`:113–115`). Service duyệt `SessionRegistry` và expire mọi session form/OIDC cùng username trừ session hiện tại (`SessionRevocationService.java:22–38`).
7. Flash `passwordChanged`, redirect `GET /change-password` (`:117–118`); UI hiển thị success tại `change-password.html:13–15`.

Session hiện tại vẫn hợp lệ và principal vẫn chứa password hash cũ, nhưng Spring không re-check password mỗi request nên user tiếp tục sử dụng bình thường. Các session khác chỉ bị `SessionInformation.expireNow()` đánh dấu; source không trực tiếp `HttpSession.invalidate()` chúng, nên Spring concurrent-session handling chặn ở request tiếp theo. Registry này in-memory/local instance, không phải cross-node revoke. Không gửi mail/notification.

## Ma trận endpoint Identity/Profile

| Thao tác | Verb/path | Handler chính | Kết quả |
|---|---|---|---|
| Mở login | `GET /login` | `AuthController.loginPage:66` | render `auth/login` |
| Login form | `POST /login` | Spring Security `SecurityConfig:244` | role home/saved request hoặc `/login?error` |
| Login Google | `GET /oauth2/authorization/google` + callback | Spring OAuth + `CustomOidcUserService.loadUser:64` | role home hoặc `/login?error=oauth_unregistered` |
| Logout | `POST /logout` | Spring Security `SecurityConfig:255` | `/login?logout` |
| Mở quên password | `GET /forgot-password` | `PasswordRecoveryController.forgotForm:54` | render form |
| Yêu cầu reset | `POST /forgot-password` | `forgotSubmit:64` | gửi mail best-effort, redirect trung tính |
| Mở link reset | `GET /reset-password?token=` | `resetForm:80` | form hoặc invalid |
| Đặt password mới | `POST /reset-password` | `resetSubmit:98` | update + consume token, redirect login |
| Xem hồ sơ | `GET /profile` | `ProfileController.view:67` | render profile |
| Sửa hồ sơ | `POST /profile` | `ProfileController.update:86` | update DB, redirect profile |
| Upload avatar | `POST /profile/avatar` | `ProfileController.uploadAvatar:110` | storage + DB, redirect profile |
| Serve avatar | `GET /uploads/avatars/{file}` | `PublicUploadsController.serve:54` | stream public object |
| Mở đổi password | `GET /change-password` | `ChangePasswordController.form:57` | render form |
| Đổi password | `POST /change-password` | `ChangePasswordController.change:84` | update + revoke session khác |

## Kết luận hành vi và khoảng hở cần nhớ

- Không có signup/self-registration; Admin phải tạo account trước.
- Form login chỉ dùng email dù label nói có số điện thoại.
- Form login chặn inactive; Google login hiện không chặn inactive.
- OIDC principal thiếu `PERM_*` và không tương thích các controller inject `KshUserDetails`.
- Reset bằng email và Admin reset password không revoke session cũ; chỉ self-service change-password revoke các session khác.
- Permission/role/name được snapshot trong principal lúc login; thay DB không tự cập nhật session hiện hữu.
- Reset success flash hiện không được login template render.
- Avatar mới có thể để lại object orphan và ảnh cũ không được cleanup.
