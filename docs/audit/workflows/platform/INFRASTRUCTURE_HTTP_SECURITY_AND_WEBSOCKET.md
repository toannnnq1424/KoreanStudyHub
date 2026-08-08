# Infrastructure workflows: probe, static/upload boundary, lỗi và WebSocket

Các route trong tài liệu này không phải thao tác nghiệp vụ có button riêng, nhưng chúng là bề mặt HTTP/runtime mà audit controller phải tính đến.

## 1. SecurityFilterChain: thành phần HTTP thật, thứ tự và boundary

`SecurityConfig.filterChain` (`src/main/java/com/ksh/config/SecurityConfig.java:201–282`) là **một** `SecurityFilterChain` application config; ngoài các Spring Security filter chuẩn mà framework thêm, source chỉ thêm hai `OncePerRequestFilter` sau. Không có servlet filter app nào khác trong `src/main/java/com/ksh/security`.

| Thành phần / trigger | Thứ tự runtime có bằng chứng source | Read/write state | Response / downstream |
|---|---|---|---|
| Authorization matcher trong `SecurityConfig.filterChain` | Sau khi Spring Security thiết lập authentication, trước controller. Public: assets, three public upload namespaces, `/login`, forgot/reset, `/public/view/**`, `/s/**`; raw `/uploads/**` bị `denyAll`; role route và `anyRequest().authenticated()` tại `:217–242`. | Chỉ đọc request path + authorities trên principal/session; không query business DB hay mutate. Method security vẫn chạy sau đó ở controller/service. | Anonymous protected page đi vào Spring Security entry point/login workflow; forbidden role không trở thành controller call. `permitAll` chỉ bỏ chain authorization, không bypass validation/policy bên trong controller public. |
| Spring `CsrfFilter` (framework component, không custom source) | Source không tắt CSRF và đặt `CsrfTokenEagerFilter` **after** `CsrfFilter` (`:275–280`). Vì vậy unsafe request token invalid/missing bị CSRF reject trước custom eager filter và trước login authentication. | Framework validates request token; source không chọn repository hay error handler custom. | 403 của framework, không redirect/flash controller. Thymeleaf/fetch đúng token mới qua được mutation. |
| `SecurityConfig.CsrfTokenEagerFilter.doFilterInternal` | Runs immediately after successful CSRF filter pass for each dispatch selected by `OncePerRequestFilter`; code read request attribute `CsrfToken.class.getName()` then calls `token.getToken()` before `chain.doFilter` (`:301–314`). | Không write business DB. Token materialization can create the `HttpSession` and persist the CSRF token before template output; if attribute absent it is no-op. | Không tự trả response, không authorize request; only prevents late session creation while rendering large form pages. |
| `LoginThrottleFilter.shouldNotFilter` / `.doFilterInternal` | Explicitly installed **before** `UsernamePasswordAuthenticationFilter` (`SecurityConfig:272–274`). It runs only exact `POST /login` (`LoginThrottleFilter:22–38`); all other HTTP paths skip. | Reads `username` + `request.getRemoteAddr`; `LoginAttemptThrottle.isBlocked` prunes/reads in-memory hashed account/IP windows. No DB, no password comparison. | At threshold, sends 302 `<context>/login?error`, terminates chain: UsernamePasswordAuthenticationFilter and form failure handler do not run. |
| `UsernamePasswordAuthenticationFilter` + configured form handlers | Reached only after CSRF and throttle allow POST `/login`; `.loginProcessingUrl("/login")` plus handlers at `SecurityConfig:244–253`. | Calls `CustomUserDetailsService.loadUserByUsername` → `UserRepository.findByEmailIgnoreCase`; BCrypt compares provider password. On success security context/session principal is established by framework. | Failure calls `SecurityConfig.formFailureHandler`; success calls `SecurityConfig.formSuccessHandler`, both redirect as detailed in identity workflow. |

Không nên ghi tài liệu như thể toàn bộ thứ tự filter chuẩn (SecurityContext, exception translation, request cache, logout, OAuth callback...) do source ấn định. Chỉ hai `addFilter…` position ở trên là thứ tự application explicit; các filter chuẩn và session-fixation/logout mechanics là Spring Security runtime defaults trừ khi `SecurityConfig` override rõ.

### Login throttle state machine và handlers

`LoginAttemptThrottle.isBlocked` (`LoginAttemptThrottle.java:42–47`) checks before authentication: account reaches `>=6` failures or IP `>=30` in a 15-minute window (`:25–28`), so the sixth credential failure itself reaches the threshold; a later request is blocked by filter. `recordFailure` (`:49–55`) is invoked only by `SecurityConfig.formFailureHandler` (`SecurityConfig:98–108`) after a credential/account failure and increments both keys. `recordSuccess` (`:57–59`) is invoked only by `formSuccessHandler` (`SecurityConfig:110–118`) and removes **account** key, not IP key. Keys are normalized/SHA-256 and map is LRU-bounded to 20,000 (`:78–110`); it is process-local, restart/node change loses it, and source deliberately ignores forwarding headers.

Thus a throttled request merely gets the same generic login redirect; it does not increment counter, audit DB, login-history row or invoke BCrypt. OAuth success also does **not** call `recordSuccess`, so a form-failure account bucket can remain until expiry even if that account later authenticates with Google.

### Saved-request, OAuth and response mapping outside controllers

`SecurityConfig.requestCache` (`:120–136`) saves only non-AJAX GETs and excludes `…/unread-count`/`…/recent`; it is an HTTP-session request cache, no DB. Both form success wrapper and OAuth success call `RoleAwareAuthenticationSuccessHandler.onAuthenticationSuccess` (`RoleAwareAuthenticationSuccessHandler.java:28–49`): it reads `SavedRequest`, permits resume only when redirect URL is same-origin (`:59–75`) and `RoleNavigation.canResume` accepts the new role (`RoleNavigation.java:43–92`), then 302s to it. Invalid/stale/other-role target is removed and handler 302s role home. This is a redirect guard, not final resource authorization.

OAuth trigger is `GET /oauth2/authorization/google`. `DbClientRegistrationRepository.findByRegistrationId` (`security/oauth/DbClientRegistrationRepository.java:72–103`) reads cached OAUTH settings and returns `null` before any Google redirect if client id/secret absent; Spring therefore returns **404**, not `/login?error=oauth_unregistered`. Only a flow that starts and subsequently fails reaches `SecurityConfig.oauthFailureHandler` (`:92–96`) and its 302 `/login?error=oauth_unregistered`. A successful callback invokes `CustomOidcUserService.loadUser` (`:64–100`), reads local `User` by email, then conditionally writes `users.google_id` and a `user_oauth_providers` row before producing `CustomOidcUserPrincipal`; success is then mapped by the same role-aware handler. `authorizedClientService` is `InMemoryOAuth2AuthorizedClientService` (`SecurityConfig:152–170`), so authorized-client/token state is not durable across process restart.

### Session, logout, revocation và absence of remember-me

`SecurityConfig.sessionRegistry` và `SecurityConfig.httpSessionEventPublisher` (`:172–180`) plus `sessionConcurrency.maximumSessions(-1)` (`:268–271`) register sessions for observation/revocation but impose **no** concurrent-session cap. `SecurityConfig.roleAwareSuccessHandler` (`:138–142`) chỉ dựng handler dùng chung với `RequestCache`; quyết định redirect thật nằm ở `RoleAwareAuthenticationSuccessHandler.onAuthenticationSuccess`. `POST /logout` reaches Spring Security logout config (`:255–259`): source only selects URL/success 302 `/login?logout`; standard logout runtime clears authentication and invalidates its current HTTP session. There is no custom logout handler, DB session table, JWT, API token, or remember-me configuration in source: no `.rememberMe(...)`, `RememberMeServices`, persistent-token repository, or UI checkbox. “Remember me” must therefore not be documented as supported; browser persistence beyond current HTTP session is container cookie policy, not an application feature.

Only `ChangePasswordController.change` invokes `SessionRevocationService.revokeOtherSessions` after `UserRepository.save` (`ChangePasswordController.java:110–115`). `SessionRevocationService.revokeOtherSessions` (`:23–38`) reads `SessionRegistry`, matches both `KshUserDetails` and `CustomOidcUserPrincipal` by username, and calls `SessionInformation.expireNow()` on all *other* records. It does not call `HttpSession.invalidate()` on them directly; interruption is observed by Spring concurrent-session handling on a subsequent request. It keeps current session, is local to the registry/node, and is not invoked by email reset, admin credential/user-state changes, ordinary logout, or OAuth linkage.

### Runtime/documentation distinctions and current gaps

- `CsrfTokenEagerFilter` is a rendering/session-timing fix, not a CSRF bypass or a source of CSRF validation. Documenting it as “all requests create a session” is false: only a request with a token attribute that is touched can materialize one.
- `LoginThrottleFilter` redirect is indistinguishable in UI from bad credentials, but its state lives only memory; no `login_history` write follows from this chain.
- Disabled OAuth has two distinct outcomes: login page hides its button when settings lookup says disabled, while a direct authorization URL returns framework 404; `oauthFailureHandler` is not a disabled-provider handler.
- SessionRegistry expiry is not an instant cross-node kill switch. Existing docs correctly identify reset/admin actions as not revoking old sessions; this section adds that even self-service revocation is registry-marking rather than direct servlet invalidation.

## 2. Chrome DevTools probe

Chrome có thể tự gọi:

```text
GET /.well-known/appspecific/com.chrome.devtools.json
```

`ChromeDevToolsProbeController.chromeDevToolsProbe`, `src/main/java/com/ksh/config/ChromeDevToolsProbeController.java:7-13`, luôn trả HTTP `204 No Content`. Không đọc principal, service hay DB; endpoint chỉ triệt request probe khỏi log/error UX. Đây là controller thứ 85 nhưng không phải workflow người dùng.

## 3. Static resource và raw upload boundary

`SecurityConfig.filterChain`, `src/main/java/com/ksh/config/SecurityConfig.java:217-242`:

- permit CSS/JS/images/fonts/favicon và webjars;
- chỉ permit public upload namespace `/uploads/avatars/**`, `/uploads/exams/**`, `/uploads/flashcards/**`;
- deny toàn bộ `/uploads/**` còn lại;
- Practice material phải qua authorized `/practice/materials/{id}/content`, không có disk mapping rộng.

`WebConfig`, `src/main/java/com/ksh/config/WebConfig.java:6-16`, cố ý không đăng ký `ResourceHandler /uploads/**`. Public image được `PublicUploadsController` stream; lesson/library/Practice object đi qua controller có policy. Vì vậy storage key không tự trở thành public URL.

## 4. WebSocket messaging

Sau khi user đã đăng nhập, client messaging mở SockJS handshake:

```text
/ws/**
```

`SecurityConfig.java:239-240` bắt authenticated session. `WebSocketConfig.registerStompEndpoints`, `src/main/java/com/ksh/config/WebSocketConfig.java:26-30`, đăng ký `/ws` với SockJS; `WebSocketConfig.configureMessageBroker`, dòng 32-37, dùng in-memory simple broker `/topic`,`/queue`, application prefix `/app`, user prefix `/user`.

Message service push vào `/user/{principal}/queue/messages`; trình duyệt subscribe nhận real-time, còn DB/message REST vẫn là source bền vững như mô tả trong `product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md`. Broker in-memory chỉ làm live delivery trên một instance; reconnect/cross-instance phải đọc REST/DB, không dựa vào broker để giữ lịch sử.

## 5. Lỗi MVC chung

Các controller nghiệp vụ ném `EntityNotFoundException`, `AccessDeniedException`, `ResponseStatusException`, storage-not-configured hoặc lỗi khác được `GlobalExceptionHandler`, `src/main/java/com/ksh/exception/GlobalExceptionHandler.java:40-164`, chuyển sang view/status phù hợp. Những REST controller có advice riêng (Practice attempt/media/speaking prompt) override bằng JSON/status contract được ghi trong tài liệu Practice tương ứng.

Điểm phân biệt khi trace UI: redirect/flash là do handler MVC tự xử lý; response lỗi không được catch sẽ đi qua advice/global handler, không quay lại service để “trả thao tác” lần hai.

Template fallback chính xác là `templates/error.html`. Màn này không tự query DB và không retry request: `GlobalExceptionHandler` đặt status/message/path/exception-safe metadata vào model rồi render HTML. Với AJAX/REST, các advice có thể trả JSON thay vì template; vì vậy không được suy diễn cứ có exception là browser luôn nhận `error.html`.

## 6. JavaScript dùng chung: state DOM, không phải workflow DB

`static/js/app.js` được nhiều shell nạp nhưng không fetch dữ liệu. Nó chỉ:

- mở/đóng dropdown và tab bằng class/style DOM (`:14–77`);
- tạo confirm dialog và chỉ gọi callback do màn nghiệp vụ truyền vào (`:79–152`);
- tạo toast tạm trong DOM, tự xóa theo timer (`:154–224`).

Toast/tab/dropdown biến mất khi reload; file này không có endpoint và không ghi record. Mutation chỉ xảy ra nếu callback cuối cùng submit một form/fetch của workflow khác.

Hai progressive-enhancement script cũng chỉ thao tác option đã có trong HTML:

- `static/js/ksh-combobox.js:20–131` copy `<option>` server đã render sang searchable list, lọc chuỗi trong browser và đồng bộ value về native `<select>`;
- `static/js/learning-select.js:26–216` dựng select/checklist/search UI và lọc link/item DOM. Nó không query thêm option, không random và không persist từ khóa tìm kiếm.

Do đó khi audit một combobox phải truy ngược controller/service đã đặt `option` vào model; không được coi hai JS này là nguồn dữ liệu. Chọn option chỉ được persist khi form chủ quản được submit.
