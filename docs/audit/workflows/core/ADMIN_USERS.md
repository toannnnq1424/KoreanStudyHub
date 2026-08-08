# Workflows Admin: quản lý toàn bộ vòng đời tài khoản

Tài liệu này bao phủ mọi thao tác có thể thực hiện từ `admin/users.html` và `admin/users-form.html`: xem/lọc, tạo, mở chi tiết, đổi thông tin/role/mã môn, xem audit, phân quyền riêng, kích hoạt, vô hiệu hoá, khoá, mở khoá, reset mật khẩu, soft-delete và restore.

## Quyền truy cập chung

Mọi URL `/admin/**` trước hết bắt buộc `ROLE_ADMIN` tại `SecurityConfig.java:238`. Sau đó:

- list + create: `@PreAuthorize("hasAuthority('PERM_user.view')")`, `AdminUsersController.java:52–55`;
- edit + update + tab phân quyền: `PERM_user.edit`, `AdminUsersEditController.java:46–49`;
- lifecycle: class-level `PERM_user.edit`, `AdminUsersLifecycleController.java:35–38`, cộng quyền riêng cho activate/deactivate và lock/unlock tại dòng 66/77/88/107.

CSRF bảo vệ mọi POST; template đặt hidden token trong từng form. Actor id luôn lấy từ principal, không nhận từ client.

> Phân tách quyền hiện tại không khớp tên catalogue hoàn toàn: `POST /admin/users` tạo account chỉ cần `user.view`, không kiểm `user.create`; `POST /admin/users/{id}/permissions` chỉ cần `user.edit`, không bắt buộc `system.permissions`.

## 1. Xem, tìm kiếm, lọc và phân trang tài khoản

### Thao tác người dùng

Admin mở **Tài khoản** từ sidebar `fragments/admin-sidebar.html:35–44`:

```text
GET /admin/users
```

Toolbar `templates/admin/users.html:35–85` có:

| Query | UI | Giá trị |
|---|---|---|
| `q` | dòng 43–46 | substring tên/email |
| `role` | dòng 48–54 | `ADMIN/LEADER/LECTURER/STUDENT` hoặc rỗng |
| `status` | dòng 55–61 | `ACTIVE/INACTIVE/LOCKED/DELETED` hoặc rỗng |
| `sort` | dòng 62–75 | createdAt, fullName hoặc rolePriority |
| `page`,`size` | link dòng 256–266 | trang và kích thước |

Người dùng bấm **“Áp dụng”** tại dòng 76; đây là browser GET thông thường.

### Controller → query

`AdminUsersController.list`, `features/admin/users/controller/AdminUsersController.java:83–106`:

1. Bind query và `Pageable`.
2. Role/sort blank → null; `StatusFilter.normalize` chỉ chấp nhận bốn enum, giá trị lạ → null (`StatusFilter.java:10–18`).
3. `AdminUsersReadService.list` (`AdminUsersReadService.java:64–86`) ép page ≥0 và size 10–100 (default 20), whitelist sort.
4. Sort rolePriority dùng native query riêng; sort khác gọi `UserRepository.searchUsersForAdmin`.

Native SQL `UserRepository.java:124–163` quyết định status:

- ACTIVE: nondeleted + active + unlocked;
- INACTIVE: nondeleted + inactive + unlocked;
- LOCKED: nondeleted + locked, bất kể active;
- DELETED: `is_deleted=1`;
- không status: mọi nondeleted.

Tìm kiếm dùng `LOWER(full_name/email) LIKE %q%`, role exact. Projection trả status, subject id, last login, created time và avatar. `UserRow.statusLabel`, `UserRow.java:36–41`, ưu tiên `DELETED > LOCKED > INACTIVE > ACTIVE`.

### UI nhận kết quả

Controller render `admin/users` với page/filter/roles/status/currentUserId (`AdminUsersController.java:99–105`). Template dựng table ở `users.html:88–249`, ẩn các thao tác self-destructive dựa trên `currentUserId` (`:104–107`, `:147–201`). Đây chỉ là UX; service guard vẫn là authority cuối cùng.

Không gọi AI, notification hay mail.

## 2. Tạo tài khoản

### Mở form và nhập dữ liệu

Admin bấm **“Tạo tài khoản”** tại `users.html:77–83`:

```text
GET /admin/users/new
```

`AdminUsersController.createForm`, dòng 109–116, bind `CreateUserForm.empty`, rồi `AdminUsersFormSupport.populateFormModel`, `AdminUsersFormSupport.java:45–53`, nạp tất cả role và tất cả department.

Form `templates/admin/users-form.html:105–205` gửi:

| Field | UI line | Server validation |
|---|---:|---|
| `email` | 117–121 | nonblank, email, ≤255 |
| `fullName` | 124–129 | nonblank, ≤150 |
| `phone` | 132–136 | ≤20 |
| `role` | 139–147 | enum, nonnull |
| `subjectId` | 150–158 | optional Long |
| `emailVerified` | 160–165 | boolean |
| `bio` | 169–175 | không có Bean Validation size |
| `password` | 179–192 | chỉ nonblank, không min/max |
| CSRF | 111 | token session |

Nút **“Tạo tài khoản”** ở dòng 203:

```text
POST /admin/users
Content-Type: application/x-www-form-urlencoded
```

### Controller

`AdminUsersController.create`, dòng 119–138:

1. Bean Validation lỗi → render lại form và departments/roles.
2. Gọi `AdminUsersWriteService.create(form, actorId)` (`:130`).
3. Email trùng do service phát hiện → inline error field email (`:133–136`).
4. Thành công flash “Đã tạo tài khoản <email>”, redirect `GET /admin/users` (`:131–132`).

### Service/entity/transaction

`AdminUsersWriteService.create`, `AdminUsersWriteService.java:75–97`, là một transaction:

1. Trim + lowercase email (`:77`, helper `:183–186`).
2. `findFirstByEmailIgnoreCase` kiểm trùng (`:78–80`).
3. BCrypt password (`:82–90`).
4. `UserFactory.newAdminCreated`, `UserFactory.java:32–50`, tạo `active=true`, `locked=false`, `deleted=false`, role/emailVerified/contact theo form.
5. Gán `subjectId` trực tiếp (`AdminUsersWriteService.java:91`).
6. Save `users`, rồi insert một `user_activities` type `CREATED` qua `AdminUsersAuditWriter` (`:92–95`). Audit fail → toàn transaction rollback.

### Hành vi biên thực tế

- `findFirstByEmailIgnoreCase` chịu `User.@SQLRestriction(is_deleted=0)`, nên **không thấy email của user soft-deleted**, dù DB email unique. Create cùng email deleted có thể rơi vào constraint/500 thay vì inline “Email đã được sử dụng”.
- Service không validate `subjectId` tồn tại/active và không ép quan hệ role–subject. FK DB (nếu có) là lớp cuối; ADMIN/STUDENT vẫn có thể được gán subject.
- Chọn role `LEADER` chỉ đổi `users.role/subject_id`; không gán `subjects.leader_user_id`. Leader catalogue phải được gán ở màn Mã môn.
- Password admin chọn chỉ cần khác blank; có thể là một ký tự. Không gửi email/notification mật khẩu tạm thời.

## 3. Mở chi tiết và chuyển tab

### Thao tác người dùng

Từ menu row, admin bấm **“Chỉnh sửa”** tại `users.html:139–146`:

```text
GET /admin/users/{id}/edit?tab=info|activity|history|permissions&page=N
```

Các tab nằm tại `users-form.html:81–94`.

### Controller và dữ liệu từng tab

`AdminUsersEditController.editForm`, `AdminUsersEditController.java:100–137`:

1. `AdminUsersReadService.getEditable` dùng native `findByIdIncludingDeleted`, nên mở được cả deleted user (`AdminUsersReadService.java:92–96`; `UserRepository.java:83–85`).
2. Bind `EditUserForm.fromUser`, nạp roles/departments.
3. Whitelist tab; giá trị lạ về `info` (`AdminUsersEditController.java:112–115`).
4. Derive status `DELETED > LOCKED > INACTIVE > ACTIVE` (`:117–123`, helper `:193–198`).
5. `created_at` đọc native vì entity không map field (`AdminUsersReadService.java:125–139`).
6. `history`: query 20 row/page, newest first, join actor email (`AdminUsersEditController.java:127–131`; `UserActivityRepository.java:39–46`).
7. `permissions`: build effective matrix theo role + overrides (`AdminUsersEditController.java:132–135`).
8. `activity`: template chỉ hiện placeholder “Sắp ra mắt”, không query activity (`users-form.html:208–216`).

### AJAX tab

`static/js/detail-tabs.js:188–220` intercept click, fetch cùng URL với `X-Requested-With: XMLHttpRequest`, parse response và chỉ thay `#tabPanel` (`detail-tabs.js:139–178`). Lỗi/auth redirect/no panel → full navigation (`:179–185`). `detail-tabs-dirty-guard.js` cảnh báo nếu form info có thay đổi chưa lưu.

Backend luôn trả full HTML; không có JSON endpoint riêng cho tab.

## 4. Sửa thông tin, role và mã môn của user

### Thao tác người dùng

Ở tab **Thông tin tài khoản**, admin sửa form `users-form.html:105–205`, bấm **“Lưu”** toolbar tại dòng 43–47:

```text
POST /admin/users/{id}
```

Form edit gửi email, fullName, phone, role, subjectId, emailVerified, bio; không gửi password.

### Controller

`AdminUsersEditController.update`, dòng 162–186:

1. Bean validation lỗi → reload model, render `info`.
2. Gọi `AdminUsersWriteService.update(id, form, actorId)`.
3. Thành công flash success; nếu giảng viên/leader bị đổi thành student mà còn sở hữu class, thêm flash warning; redirect `/admin/users/{id}/edit?tab=info` (`:173–178`).
4. Email trùng → inline email error; phá invariant leader → inline role error (`:179–184`).

### Service, lock và mutation

`AdminUsersWriteService.update`, dòng 104–179, là một transaction:

1. Lock global leader-assignment anchor bằng row `system_settings['ai.provider']` (`:106`, helper `:208–214`).
2. Pessimistic-write lock target user qua `UserRepository.findByIdForUpdate` (`:107–108`; repository `:105–107`).
3. Nếu target đang được một department trỏ làm leader, form phải giữ role LEADER và đúng `subjectId`; nếu không phải đổi/gỡ từ màn Mã môn trước (`AdminUsersWriteService.java:194–205`).
4. Cấm admin đổi role của chính mình (`:111–115`).
5. Chặn hạ role last active ADMIN (`:117–120`, `AdminUsersGuard.java:87–97`).
6. Normalize email, kiểm trùng user khác (`AdminUsersWriteService.java:122–126`).
7. Snapshot old, gọi `User.updateAdminFields`, save (`:128–140`; entity `User.java:216–221`).
8. Ghi audit `UPDATED` với old/new JSON (`AdminUsersWriteService.java:142–148`). Nếu role đổi, ghi thêm audit `ROLE_CHANGED` (`:150–159`). Do đó comment “exactly one activity” ở class javadoc không đúng cho role change.
9. Nếu LECTURER/LEADER → STUDENT, query các class do target sở hữu và chỉ **cảnh báo**, không tự reassign (`:161–178`).

Không gửi notification/mail cho target. Role/email/permission authorities trong các session hiện hữu không được refresh; thay đổi thực sự tác động Spring `@PreAuthorize` sau lần login mới.

## 5. Tick/untick quyền riêng tại tab “Phân quyền”

### Thao tác người dùng

Tab render mọi permission catalogue tại `users-form.html:268–325`. Mỗi row là một form:

- `featureKey` hidden dòng 301;
- `granted` là trạng thái mong muốn sau click, dòng 302–303;
- checkbox dòng 304–308;
- CSRF dòng 300.

JS `admin-users.js:279–288` disable checkbox rồi `form.requestSubmit()`:

```text
POST /admin/users/{id}/permissions
```

### Controller/service

`AdminUsersEditController.togglePermission`, dòng 145–159, gọi `UserPermissionToggleService.toggle` rồi redirect lại `?tab=permissions`.

`UserPermissionToggleService.toggle`, `UserPermissionToggleService.java:86–109`, transaction:

1. Pessimistic lock user (`:88–89`).
2. Load permission catalogue by feature key (`:90–91`).
3. Với target ADMIN, permission nhóm SYSTEM/USER_MANAGE hoặc key `system.permissions` bị guard khoá (`:93–96`; `AdminPermissionsGuard.java:77–82`).
4. Query permission mà role chain cấp qua recursive hierarchy (`UserPermissionToggleService.java:98`, helper `:148–155`).
5. Load row override `(user,permission)`.
6. Tính before/after. Nếu desired state bằng role state → deactivate override; nếu khác → create/update `GRANT` hoặc `REVOKE` (`:102–105`, `:117–131`).
7. Ghi `user_activities` type `PERMISSION_CHANGED` với before/after (`:134–145`).
8. Chỉ sau commit mới evict permission cache user (`:107`).

UI source badge được `UserPermissionViewBuilder.java:65–114` tính theo precedence `REVOKE > GRANT > FROM_ROLE > NONE`; expired/inactive override bị bỏ (`:118–126`).

> Cache eviction không thay authorities đã copy vào `KshUserDetails` session (`KshUserDetails.java:72–82`). Checkbox đổi DB ngay nhưng request của user đang đăng nhập vẫn dùng authority cũ đến khi re-authenticate.

## 6. Kích hoạt tài khoản

### Thao tác người dùng

Row inactive hiện nút **“Kích hoạt”** tại `users.html:167–173`. JS mở confirm (`admin-users.js:29–35`, `:141–145`), rồi submit hidden form `users.html:207–211`:

```text
POST /admin/users/{id}/activate
```

### Backend/UI

`AdminUsersLifecycleController.activate`, dòng 65–74, yêu cầu `PERM_user.activate_deactivate`, gọi `AdminUsersLifecycleService.activate`.

Service `AdminUsersLifecycleService.java:61–69`:

1. Pessimistic lock nondeleted user (`lockForLifecycle`, `:152–155`).
2. `target.setActive(true)`, save.
3. Insert audit `ACTIVATED` trong cùng transaction.

Controller flash success, redirect `/admin/users`. Account vẫn có thể locked; activate không unlock.

## 7. Vô hiệu hoá tài khoản

### Thao tác và request

Nút **“Vô hiệu hoá”** `users.html:160–166` → confirm JS → hidden form `:212–216`:

```text
POST /admin/users/{id}/deactivate
```

### Backend

`AdminUsersLifecycleController.deactivate`, dòng 76–85, yêu cầu `PERM_user.activate_deactivate`.

`AdminUsersLifecycleService.deactivate`, dòng 49–59:

1. Lock target.
2. Cấm self (`AdminUsersGuard.requireNotSelf`, `AdminUsersGuard.java:52–57`).
3. Chặn last active admin (`AdminUsersGuard.java:68–77`).
4. Set `active=false`, save, audit `DEACTIVATED`, cùng transaction.

Form login sau đó bị `KshUserDetails.isEnabled=false` chặn. Tuy nhiên các session target đang đăng nhập không bị expire và principal cũ vẫn `active=true`; user có thể tiếp tục dùng session hiện hữu.

## 8. Khoá kỷ luật

### Thao tác và request

Nút **“Khoá kỷ luật”** ở `users.html:174–180` mở modal `:269–282`. Admin nhập lý do, JS kiểm nonblank, copy vào hidden form và submit (`admin-users.js:154–190`; form `users.html:232–238`):

```text
POST /admin/users/{id}/lock
lockedReason=<text>
```

### Controller/service

`AdminUsersLifecycleController.lock`, dòng 87–104, yêu cầu `PERM_user.lock_unlock`:

- Bean Validation `@NotBlank @Size(max=255)` (`LockForm.java:7–10`). Sai → flash error + payload, redirect list; JS đọc payload và mở lại modal với reason (`AdminUsersLifecycleController.java:95–100`; `admin-users.js:254–266`).
- Hợp lệ → `AdminUsersLifecycleService.lock`.

Service `AdminUsersLifecycleService.java:71–96` lock target, cấm self/last admin, trim/cap 255/loại control chars, rồi `User.lock` set đồng thời `is_locked=true` + reason (`User.java:152–155`). Save và audit `LOCKED` với reason JSON trong một transaction.

Không revoke session hiện hữu. Chỉ lần authenticate sau mới bị `isAccountNonLocked=false` chặn.

## 9. Mở khoá

Nút **“Mở khoá”** `users.html:181–187` → confirm → hidden form `:217–221`:

```text
POST /admin/users/{id}/unlock
```

`AdminUsersLifecycleController.unlock`, dòng 106–115, yêu cầu `PERM_user.lock_unlock`. `AdminUsersLifecycleService.unlock`, dòng 98–105, lock row, gọi `User.unlock()` để clear cả flag và reason (`User.java:160–163`), save + audit `UNLOCKED`, redirect list. Unlock không tự activate account inactive.

## 10. Admin đặt lại mật khẩu cho user

### Thao tác và request

Nút **“Đặt lại mật khẩu”** tại `users.html:147–158` mở modal `:284–298`. JS copy password chính xác (không trim) vào hidden form (`admin-users.js:193–228`; form `users.html:239–245`):

```text
POST /admin/users/{id}/reset-password
newPassword=<plain text>
```

### Backend

`AdminUsersLifecycleController.resetPassword`, dòng 117–132:

1. `ResetPasswordForm` chỉ `@NotBlank`, không min/max (`ResetPasswordForm.java:6–9`).
2. Blank → flash + redirect/reopen modal nhưng cố ý không flash lại password.
3. Hợp lệ → `AdminUsersLifecycleService.resetPassword`.

Service `AdminUsersLifecycleService.java:107–122` lock target, cấm self, BCrypt encode, save và audit `PASSWORD_RESET`; password không đi vào metadata. Controller redirect list với success toast.

Không gửi mail/notification. Modal nhắc admin tự báo qua kênh khác (`users.html:287–289`). Không revoke session target, nên session cũ vẫn dùng được.

## 11. Soft-delete tài khoản

### Thao tác và request

Nút **“Xoá”** từ list `users.html:196–202` hoặc toolbar detail `users-form.html:48–51` mở confirm; hidden forms tại `users.html:222–226` và `users-form.html:334–339`:

```text
POST /admin/users/{id}/delete
```

`AdminUsersLifecycleController.softDelete`, dòng 134–142, gọi service.

`AdminUsersLifecycleService.softDelete`, dòng 124–134:

1. Pessimistic lock target nondeleted.
2. Cấm self và chặn last active admin.
3. `User.softDelete()` set `is_deleted=true` (`User.java:165–171`).
4. Save + audit `DELETED` trong cùng transaction.

Sau commit, default JPA query không còn thấy target vì `@SQLRestriction`; admin vẫn thấy qua native status `DELETED`. Không hard-delete dữ liệu liên quan, không gửi notification và không revoke active session.

## 12. Khôi phục tài khoản soft-deleted

### Thao tác và request

Admin lọc `status=DELETED`, bấm **“Khôi phục”** tại `users.html:189–195`; hidden form `:227–231`:

```text
POST /admin/users/{id}/restore
```

`AdminUsersLifecycleController.restore`, dòng 144–152, gọi `AdminUsersLifecycleService.restore`.

Service `AdminUsersLifecycleService.java:136–148` dùng native `findByIdIncludingDeleted` (không dùng `findByIdForUpdate` vì restriction), gọi `User.restore()` set `is_deleted=false`, save + audit `RESTORED`. Các cờ active/locked cũ được giữ nguyên; restore một account từng locked/inactive không làm nó login được.

## 13. Quy tắc last-admin và concurrency thực tế

`AdminUsersGuard` cố bảo vệ self và last active admin. Các mutation lock target row bằng `PESSIMISTIC_WRITE`. Tuy nhiên có hai giới hạn quan trọng:

1. `countActiveAdmins` SQL tại `UserRepository.java:71–74` đếm `role=ADMIN AND active=1 AND deleted=0`, **không loại locked**. Hệ thống có thể giữ đúng một “active” admin nhưng admin đó locked và không login được.
2. Lock từng target khác nhau không serialize aggregate count. Hai transaction đồng thời demote/deactivate hai admin khác nhau có thể cùng thấy count=2, cùng pass và commit thành 0 active admin. Javadoc `AdminUsersGuard.java:22–29` tuyên bố target-row lock đóng race nhưng lock hai row khác nhau không tạo mutex chung.

Department workflow có global anchor lock, còn last-admin workflow hiện không có anchor/table lock tương đương.

## 14. Audit, notification và state propagation

Mọi mutation accepted insert `user_activities` qua `AdminUsersAuditWriter.java:42–55`; entity taxonomy ở `UserActivity.java:38–48`. History tab query actor + message, nhưng không render JSON metadata (`users-form.html:229–250`).

Không workflow Admin Users nào gọi NotificationService hoặc mail outbox. Toàn bộ feedback cho actor là flash/toast hoặc inline validation. Target user không nhận thông báo.

State propagation tới user đang đăng nhập:

| Admin đổi | Login mới | Session đang tồn tại |
|---|---|---|
| active/locked/deleted | bị áp dụng | không tự revoke |
| password | password mới cần cho login mới | session cũ không revoke |
| role | principal mới mang role mới | giữ role cũ |
| permission override | resolver đọc state mới | giữ `PERM_*` cũ |
| email/fullName/avatar | DB mới | principal có thể giữ snapshot cũ |

## Ma trận endpoint Admin Users

| Thao tác | Verb/path | Controller method | Service chính |
|---|---|---|---|
| List/filter | `GET /admin/users` | `AdminUsersController.list:83` | `AdminUsersReadService.list:64` |
| Form create | `GET /admin/users/new` | `createForm:109` | form support/query departments |
| Create | `POST /admin/users` | `create:119` | `AdminUsersWriteService.create:75` |
| Chi tiết/tab | `GET /admin/users/{id}/edit` | `AdminUsersEditController.editForm:100` | read/history/permission builder |
| Update | `POST /admin/users/{id}` | `update:162` | `AdminUsersWriteService.update:104` |
| Toggle permission user | `POST /admin/users/{id}/permissions` | `togglePermission:145` | `UserPermissionToggleService.toggle:86` |
| Activate | `POST /admin/users/{id}/activate` | `activate:67` | lifecycle `activate:61` |
| Deactivate | `POST /admin/users/{id}/deactivate` | `deactivate:78` | lifecycle `deactivate:49` |
| Lock | `POST /admin/users/{id}/lock` | `lock:89` | lifecycle `lock:71` |
| Unlock | `POST /admin/users/{id}/unlock` | `unlock:108` | lifecycle `unlock:98` |
| Reset password | `POST /admin/users/{id}/reset-password` | `resetPassword:118` | lifecycle `resetPassword:107` |
| Soft-delete | `POST /admin/users/{id}/delete` | `softDelete:135` | lifecycle `softDelete:124` |
| Restore | `POST /admin/users/{id}/restore` | `restore:145` | lifecycle `restore:136` |
