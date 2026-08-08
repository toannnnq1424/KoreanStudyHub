# Workflow audit: General, SMTP, Google OAuth và KRDICT

Tài liệu này trả lời theo hướng thao tác người dùng: Admin nhập field nào, nút nào phát request gì, controller/service ghi gì và capability runtime nào thật sự thay đổi. Bốn màn đều có hiệu lực bằng dữ liệu DB, không cần restart, nhưng không phải field nào được lưu cũng đang có consumer.

## 1. Cài đặt chung: lưu được nhưng chưa đổi giao diện runtime

### Thao tác người dùng

Admin có quyền `PERM_system.settings` mở:

```text
GET /admin/settings/general
```

`GeneralSettingsController.view`, `src/main/java/com/ksh/features/admin/settings/controller/GeneralSettingsController.java:66-73`, gọi `GeneralSettingsService.load()` rồi render `templates/admin/settings-general.html`.

Form tại `settings-general.html:31-87` có bốn field:

| Field UI | Dòng | DB key |
|---|---:|---|
| `siteName` | 38-44 | `site.name` |
| `siteDescription` | 48-56 | `site.description` |
| `siteLogoUrl` | 60-68 | `site.logo_url` |
| `siteContactEmail` | 72-80 | `site.contact_email` |

Khi bấm **“Lưu cài đặt”** ở dòng 86, browser submit form thường:

```text
POST /admin/settings/general
Content-Type: application/x-www-form-urlencoded
```

### Controller → service → DB

`GeneralSettingsController.save`, dòng 80-100:

1. Bean Validation kiểm độ dài/email của DTO.
2. Principal phải là `KshUserDetails`; OAuth-only admin bị redirect với lỗi session không hỗ trợ.
3. Gọi `GeneralSettingsService.save(form, principal.id)` ở dòng 97.
4. Redirect lại `GET /admin/settings/general` với flash “Đã lưu cài đặt chung”.

`GeneralSettingsService.save`, `.../GeneralSettingsService.java:79-89`, trim bốn giá trị; `upsertAll` dòng 93-115 ghi atomically vào `system_settings`, group `GENERAL`, set `updated_by`, rồi evict cache `settingsGroup['GENERAL']`.

### Điều gì được mở khóa?

Không có capability product nào hiện được mở khóa bởi bốn key này. Toàn source chỉ có `GeneralSettingsController`, DTO và service tham chiếu `site.name`, `site.description`, `site.logo_url`, `site.contact_email`; không có header/page renderer nào đọc chúng. Chính controller cũng ghi rõ phạm vi MVP ở `GeneralSettingsController.java:30-33`.

Vì vậy câu trả lời chính xác là: nút Lưu cập nhật DB và trang Admin đọc lại được ngay, nhưng tên/logo/mô tả/contact của UI công khai hiện chưa thay đổi theo cấu hình này.

## 2. SMTP: mở reset-password và email outbox của lesson/assignment

### Thao tác người dùng và các field

Admin có `PERM_system.smtp` mở:

```text
GET /admin/settings/email
```

`EmailSettingsController.view`, `.../EmailSettingsController.java:82-92`, nạp form, email mặc định của admin và snapshot không-PII của outbox.

Form `templates/admin/settings-email.html:39-147` gửi:

| Field | Dòng | DB key | Runtime |
|---|---:|---|---|
| `host` | 46-52 | `smtp.host` | gate chính; trống thì mail bị skip |
| `port` | 56-62 | `smtp.port` | cổng SMTP, fallback runtime 587 nếu dữ liệu hỏng |
| `encryption` | 65-87 | `smtp.encryption` | `none`, `tls`/STARTTLS, `ssl` |
| `username` | 91-97 | `smtp.username` | SMTP auth; cũng là fallback From |
| `password` | 101-106 | `smtp.password` | blank/`********` giữ secret cũ |
| `fromName` | 114-120 | `smtp.from_name` | display name của sender |
| `fromEmail` | 124-130 | `smtp.from_email` | From; trống runtime fallback username |
| `replyTo` | 134-139 | `smtp.reply_to` | chỉ set header khi không trống |

Bấm **“Lưu cài đặt”**:

```text
POST /admin/settings/email
Content-Type: application/x-www-form-urlencoded
```

`EmailSettingsController.save`, dòng 100-125, validate, gọi `EmailSettingsService.save` dòng 122 rồi redirect.

`EmailSettingsService.save`, `.../EmailSettingsService.java:102-121`, ghi các key trong một transaction; chỉ ghi `smtp.password` nếu user nhập secret mới ở dòng 114-118; sau đó evict cache SMTP. Form GET luôn trả `********`, không đưa password DB vào HTML (`EmailSettingsService.java:70-82`).

### Runtime gửi mail thật

`DbConfiguredMailSender.sendWithDetail`, `src/main/java/com/ksh/features/mail/DbConfiguredMailSender.java:77-98`:

1. Đọc group `SMTP` từ cache/DB ở dòng 78.
2. Nếu `smtp.host` trống, trả failure và không network call ở dòng 80-84.
3. `buildSender`, dòng 102-123, tạo `JavaMailSenderImpl`, set host/port/username/password, auth=true, timeout 10 giây; map `tls` thành `mail.smtp.starttls.enable`, `ssl` thành `mail.smtp.ssl.enable`.
4. `buildMessage`, dòng 126-150, set recipient/subject/plain-text UTF-8, From/Reply-To.
5. `sender.send(message)` ở dòng 90; lỗi được chuyển thành `MailSendResult.failure`, không throw tới product UI.

Nhập SMTP hợp lệ mở hai nhóm workflow thật:

- Forgot password: `PasswordRecoveryService.requestReset`, `.../PasswordRecoveryService.java:76-107`, tạo token SHA-256/TTL 1 giờ, dựng link `${app.base-url}/reset-password?token=...`, gọi `MailService.send` ở dòng 102-103. SMTP lỗi vẫn trả response trung tính, token DB vẫn tồn tại nhưng user không nhận link.
- Notification email bền vững: `NotificationService.create`, `.../NotificationService.java:62-83`, chỉ enqueue email cho `LESSON_PUBLISHED` và `ASSIGNMENT_PUBLISHED` theo whitelist `NotificationType.EMAIL_TYPES` (`NotificationType.java:47-52`). Join request/approve/reject, class approve/reject và grading chỉ in-app.

`MailOutboxWorker`, `.../MailOutboxWorker.java:19-75`, mặc định tự chạy, poll sau 30 giây rồi mỗi 10 giây. `MailOutboxProcessor.deliver`, `.../MailOutboxProcessor.java:55-80`, gọi SMTP ngoài transaction rồi ghi `SENT` hoặc retry/failure; SMTP chưa cấu hình không ngăn notification DB được tạo và job vẫn nằm trong outbox để retry.

### Nút “Gửi email thử”

Nút tại `settings-email.html:190-198`; `static/js/admin-settings.js:29-99` gửi:

```text
POST /admin/settings/email/test
Content-Type: application/x-www-form-urlencoded
Accept: application/json
testRecipient=<email>
```

`EmailSettingsController.sendTest`, dòng 136-140, gọi `EmailSettingsService.sendTest`; service dòng 137-153 validate recipient và gửi subject/body cố định qua cấu hình **đã lưu**, không dùng draft chưa bấm Lưu. JSON luôn có dạng:

```json
{"ok": true, "error": null}
```

hoặc `{"ok":false,"error":"..."}`; JS toast kết quả. Snapshot outbox ở `settings-email.html:150-181` chỉ đọc số liệu tổng hợp, không có nút retry/purge thủ công.

## 3. Google OAuth: hiện nút đăng nhập nhưng không tự tạo tài khoản

### Admin nhập cấu hình

Admin có `PERM_system.oauth` mở `GET /admin/settings/oauth`. Form `templates/admin/settings-oauth.html:38-97` gồm:

| Field | Dòng | DB key |
|---|---:|---|
| `googleClientId` | 53-57 | `oauth.google.client_id` |
| `googleClientSecret` | 63-76 | `oauth.google.client_secret` |
| `googleScope` | 79-89 | `oauth.google.scope` |

Redirect URI được UI hướng dẫn là `/login/oauth2/code/google` (`settings-oauth.html:44-50`). Bấm **“Lưu cài đặt”** gửi:

```text
POST /admin/settings/oauth
Content-Type: application/x-www-form-urlencoded
```

`OauthSettingsController.save`, `.../OauthSettingsController.java:82-114`, buộc secret phải tồn tại nếu Client ID có giá trị. `OauthSettingsService.save`, `.../OauthSettingsService.java:94-107`, ghi ID/scope, chỉ thay secret khi input không blank, rồi evict cache OAUTH. Scope trống được runtime fallback `openid,profile,email`.

Lưu ý code-level: template cố ý render `googleClientSecret` bằng `type="text"` và `th:value` ở dòng 63-74; vì vậy secret đã lưu hiện xuất hiện rõ trong HTML/admin UI. Đây khác với SMTP/AI/storage vốn mask secret.

### Capability được mở khóa

Chỉ khi **cả Client ID và Client Secret** không blank, `OauthSettingsService.isGoogleEnabled`, dòng 125-129, trả true. Khi user mở login:

1. `AuthController.loginPage`, `.../AuthController.java:66-86`, gọi `isGoogleEnabled` và đưa `googleEnabled` vào model.
2. `templates/auth/login.html:125-133` mới render nút **“Đăng nhập bằng Google”** tới `GET /oauth2/authorization/google`.
3. `DbClientRegistrationRepository.findByRegistrationId`, `src/main/java/com/ksh/security/oauth/DbClientRegistrationRepository.java:72-103`, chỉ nhận id đúng `google`, đọc ID/secret/scope từ DB, dựng `CommonOAuth2Provider.GOOGLE`. Thiếu một trong hai thì trả null và Spring trả 404 thay vì bắt đầu flow nửa cấu hình.
4. Spring Security redirect Google; callback về `GET /login/oauth2/code/google` do framework xử lý, rồi gọi `CustomOidcUserService` được cấu hình trong `SecurityConfig`.
5. `CustomOidcUserService.loadUser`, `src/main/java/com/ksh/security/CustomOidcUserService.java:64-100`, lấy email/sub từ Google; bắt buộc email đã có trong bảng `users`, user không bị locked/deleted. Lần đầu mới set `users.google_id` và upsert `user_oauth_providers(provider='google', provider_user_id=sub)`.
6. Không có local user tương ứng thì throw `oauth_unregistered`; failure handler redirect `/login?error=oauth_unregistered`, UI báo liên hệ quản trị.

Do đó OAuth **không mở self-registration** và không cấp role mới. Nó chặn locked/deleted, nhưng `CustomOidcUserService.java:79-83` hiện **không kiểm `user.isActive()`**; account inactive vẫn có thể vào bằng Google. Đây là khác biệt với form login, không được mô tả chung là “mọi trạng thái tài khoản đều được chặn”. Chỉ Google được hỗ trợ; nhập tên/provider khác không có endpoint hay repository mapping.

## 4. KRDICT: mở tra Hàn → Việt và lưu thẻ, không dùng trong Practice

### Admin lưu key

Admin có `PERM_system.settings` mở `GET /admin/settings/dictionary`. Form `templates/admin/settings-dictionary.html:27-50` có:

- `apiKey` dòng 32-35: key 32 ký tự hex; masked value giữ key cũ.
- `baseUrl` dòng 38-40: bắt buộc bắt đầu bằng `https://krdict.korean.go.kr/`.

Bấm **“Lưu cấu hình”**:

```text
POST /admin/settings/dictionary
Content-Type: application/x-www-form-urlencoded
```

`KoreanDictionarySettingsController.save`, `.../KoreanDictionarySettingsController.java:50-69`, gọi `KoreanDictionarySettingsService.save`. Service `src/main/java/com/ksh/features/dictionary/KoreanDictionarySettingsService.java:49-65` validate key/domain, ghi:

- encrypted setting `dictionary.krdict.api-key`;
- plain setting `dictionary.krdict.base-url`;
- evict toàn bộ settings cache.

Nếu DB chưa có key, runtime còn fallback key legacy group Discovery rồi environment `app.news.dictionary.api-key`; base URL fallback environment/default (`KoreanDictionarySettingsService.java:26-42`).

### Request ra KRDICT và UI được mở

`KoreanDictionaryClient.lookupVietnamese`, `.../KoreanDictionaryClient.java:55-72`, chỉ gọi khi key tồn tại và word không blank. Request là HTTP GET:

```text
GET {baseUrl}?key={apiKey}&q={koreanWord}&part=word&method=exact
    &translated=y&trans_lang=7&num=10
Accept: application/xml
```

Client parse XML an toàn ở dòng 82-143, chọn exact word hoặc item đầu, lấy tối đa hai Vietnamese translations, pronunciation/POS/level và link KRDICT.

Key này mở:

- lookup từ/cụm Hangul trong Korea Discovery; nếu article đã có `NewsVocabulary` cache thì không gọi provider (`DiscoveryVocabularyLearningService.java:131-155`);
- lookup toàn cục và lưu vào flashcard deck do chính user sở hữu qua `KoreanDictionaryLearningService`;
- enrichment vocabulary của pipeline Discovery.

Nếu key thiếu/provider lỗi, UI trả trạng thái `configured=false`/không có kết quả và cho phép user nhập nghĩa thủ công trước khi lưu (`DiscoveryVocabularyLearningService.java:56-128`). Template và controller xác định rõ phạm vi loại trừ Practice; không có Practice controller/service nào inject `KoreanDictionaryClient`, nên nhập key không thay đổi workflow Practice.

## Tóm tắt “nhập gì mở gì”

| Cấu hình đủ | Capability thật sự |
|---|---|
| General 4 field | chỉ lưu/read trong Admin; chưa đổi UI product |
| SMTP host + auth/sender hợp lệ | password reset + email async cho lesson/assignment + test send |
| Google Client ID + Secret | hiện nút Google, tạo client registration; chỉ link local user có sẵn |
| KRDICT key 32 hex + official base URL | lookup Hàn→Việt/Discovery/global dictionary và lưu flashcard; không Practice |

## Method-level initial-read ledger

| Handler exact | Màn hình lấy gì trước khi user thao tác |
|---|---|
| `OauthSettingsController.view` | `GET /admin/settings/oauth` → `OauthSettingsService.load()` đọc Google client id/secret/scopes/enabled từ settings DB (giữ flash form nếu save lỗi), set active tab rồi render `templates/admin/settings-oauth.html` (`OauthSettingsController.java:68–74`). Secret hiện có đi từ model vào HTML, không phải JS tự fetch. |
| `KoreanDictionarySettingsController.view` | `GET /admin/settings/dictionary` → `maskedApiKey(environmentFallback)`, `baseUrl(environmentFallback)` và `apiKey(...).isBlank()` để đặt form/configured (`KoreanDictionarySettingsController.java:38–47`). Màn chỉ đọc mask/trạng thái; KRDICT network request chỉ xảy ra ở lookup consumer, không xảy ra khi mở settings. |
