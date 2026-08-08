# Workflows Admin: ma trận quyền, ngoại lệ quyền và quyền hiệu lực

Tài liệu trace toàn bộ UI → backend của hai màn `admin/permissions-roles.html`, `admin/permissions-overrides.html`, cùng tab phân quyền user đã được mô tả trong `ADMIN_USERS.md`. Không có workflow tạo/xoá permission catalogue từ UI; catalogue và role hierarchy do Flyway sở hữu.

## Mô hình dữ liệu quyết định hành vi

- `permissions`: một `feature_key` duy nhất và `permission_group` (`Permission.java:27–50`). Authority runtime là `PERM_<featureKey>` (`Permission.java:52–72`).
- `role_permissions`: direct grant `(role_code, permission_id)` (`RolePermission.java:29–49`).
- `role_hierarchy`: `(parent, child)` nghĩa child kế thừa parent; chain seeded `STUDENT ← LECTURER ← LEADER ← ADMIN` (`RoleHierarchy.java:15–25`).
- `user_permission_overrides`: một row duy nhất/user/permission; `GRANT` thêm, `REVOKE` bớt, có active/expiry (`UserPermissionOverride.java:35–77`).
- `permission_activities`: audit accepted role-grant và override mutations (`PermissionActivity.java:35–68`).

Quyền hiệu lực được tính `REVOKE > GRANT > FROM_ROLE`, không phải chỉ nhìn checkbox ma trận.

## Quyền truy cập chung

Security filter bắt buộc `ROLE_ADMIN` cho `/admin/**` (`SecurityConfig.java:238`). Cả hai controller còn yêu cầu `PERM_system.permissions`:

- `AdminPermissionsController.java:31–34`;
- `AdminPermissionOverridesController.java:34–37`.

Mọi POST có CSRF hidden trong template. Actor id lấy từ principal, không nhận từ client.

## 1. Mở ma trận role × permission

### Thao tác người dùng

Admin bấm **“Phân quyền”** trong sidebar `fragments/admin-sidebar.html:87–94`:

```text
GET /admin/permissions
```

### Controller/service/query

`AdminPermissionsController.matrix`, `features/admin/permissions/controller/AdminPermissionsController.java:45–50`, gọi `PermissionMatrixService.loadMatrix` và render `admin/permissions-roles`.

`PermissionMatrixService.loadMatrix`, `PermissionMatrixService.java:64–95`, read-only transaction:

1. Lấy bốn role theo enum order (`:66`, helper `:141–148`).
2. Với mỗi role, query các row **direct** `RolePermissionRepository.findByIdRoleCode` (`:68–76`).
3. Load catalogue theo group + feature key (`:78–80`).
4. Mỗi cell có `granted=direct row exists`; không mở rộng inheritance (`:81–85`).
5. `AdminPermissionsGuard.isCoreAdminPermission` đánh dấu cell ADMIN thuộc group SYSTEM/USER_MANAGE hoặc key `system.permissions` là core (`AdminPermissionsGuard.java:77–82`).

Template nói rõ đây là direct grants tại `permissions-roles.html:17–21`, render master/detail group ở dòng 46–115. JS `admin-permissions.js:18–51` chỉ chọn pane theo URL hash, không gọi backend.

## 2. Tick để cấp direct permission cho role

### Thao tác người dùng và request

Mỗi non-core cell là form tại `permissions-roles.html:90–100`:

| Field | Line | Ý nghĩa |
|---|---:|---|
| `roleCode` | 95 | target role |
| `featureKey` | 96 | target permission |
| `granted` | 97 | state mới (`true` khi cell đang unchecked) |
| CSRF | 94 | token |

Admin tick checkbox. JS `admin-permissions.js:54–73` giữ group trong URL hash rồi `requestSubmit()`:

```text
POST /admin/permissions
roleCode=...
featureKey=...
granted=true
```

### Controller/service/transaction

`AdminPermissionsController.toggle`, dòng 57–76, nhánh `granted=true` gọi `PermissionMatrixService.attach` (`:64–66`).

`PermissionMatrixService.attach`, dòng 104–114:

1. Load permission by unique feature key; unknown → `NoSuchElementException` (`:106`, helper `:150–154`).
2. `Role.valueOf` whitelist role (`:107`, helper `:156–162`).
3. Nếu direct row đã tồn tại → no-op, không audit/cache eviction (`:108–110`).
4. Insert `RolePermission(roleCode, permissionId)` (`:111`).
5. Insert audit `ROLE_PERMISSION_ATTACHED` qua `PermissionAuditWriter` (`:112`; writer `PermissionAuditWriter.java:43–55`).
6. Sau transaction commit, evict cache cho user thuộc role này và mọi descendant role (`:113`; `PermissionResolver.evictRole`, `PermissionResolver.java:151–169`).

Controller flash success và redirect `/admin/permissions`; browser hash đưa admin về group đang mở.

Một direct grant có thể dư thừa nếu role đã kế thừa permission đó; UI cố ý vẫn cho tick vì ma trận phản ánh storage direct, không effective permission.

## 3. Untick để gỡ direct permission khỏi role

Request giống trên nhưng `granted=false`. `AdminPermissionsController.toggle` gọi `PermissionMatrixService.detach` (`AdminPermissionsController.java:67–69`).

`PermissionMatrixService.detach`, dòng 125–139:

1. Validate feature/role.
2. `AdminPermissionsGuard.checkDetachAllowed` chạy **trước write** (`:129–130`). Core ADMIN → `AccessDeniedException`, không delete và không audit.
3. Direct row không tồn tại → no-op (`:131–135`).
4. Có row → delete, audit `ROLE_PERMISSION_DETACHED`, after-commit evict affected caches (`:136–138`).

Controller bắt `AccessDeniedException` và flash chính reason về ma trận (`AdminPermissionsController.java:71–74`). Unknown role/feature không được bắt, rơi vào catch-all 500 (`GlobalExceptionHandler.java:156–162`).

### Core cell và khoảng hở

Template không render form cho core; nó luôn render checkbox `checked disabled` (`permissions-roles.html:102–107`) bất kể `cell.granted`. Nếu DB migration thiếu direct core grant, UI vẫn hiển thị checked và user không thể attach từ UI.

Guard chỉ bảo vệ **direct role detach**. Nó không bảo vệ mọi đường thu hồi effective permission; global override workflow bên dưới vẫn có thể tạo `REVOKE` core permission cho một ADMIN.

## 4. Mở danh sách ngoại lệ quyền

### Thao tác người dùng

Admin bấm **“Ghi đè quyền”** tại `permissions-roles.html:29–37`:

```text
GET /admin/permissions/overrides
```

### Controller/service/UI

`AdminPermissionOverridesController.list`, `AdminPermissionOverridesController.java:52–63`:

1. Bind form rỗng hoặc form flashed từ POST lỗi.
2. `PermissionOverrideService.listOverrides()` lấy mọi row newest first, kể cả inactive/expired (`PermissionOverrideService.java:72–97`). Service batch-load email và feature key; `expired = expiresAt <= now`.
3. `listCatalog()` load mọi permission (`:118–123`).
4. `listCandidates()` load user `active=true` ở mọi role (`:104–111`). `@SQLRestriction` loại deleted, nhưng query không loại locked.
5. Render `admin/permissions-overrides`.

UI form nằm `permissions-overrides.html:37–103`; table history nằm dòng 105–157. `OverrideRow.inEffect()` chỉ khi active và chưa expired (`PermissionDtos.java:69–76`).

## 5. Tạo hoặc thay thế một user override

### Thao tác người dùng và request

Admin chọn:

| Field | UI line | Ý nghĩa |
|---|---:|---|
| `userId` | 43–54 | active candidate |
| `featureKey` | 56–67 | permission catalogue |
| `overrideType` | 69–75 | `GRANT` hoặc `REVOKE` |
| `reason` | 77–88 | lý do, HTML maxlength 255 |
| `expiresAt` | 90–97 | optional `datetime-local` |
| CSRF | 41 | token |

Bấm **“Lưu ngoại lệ”** tại `permissions-overrides.html:99–101`:

```text
POST /admin/permissions/overrides
Content-Type: application/x-www-form-urlencoded
```

`AdminPermissionOverridesController.save`, dòng 70–90, bind request params và `LocalDateTime`, tạo `OverrideForm`, gọi service. `IllegalArgumentException` (reason/type) → flash field error + giữ form; luôn redirect GET để tránh resubmit.

### Service, lock và precedence

`PermissionOverrideService.createOrReplace`, dòng 133–157, transaction:

1. Reason nonblank và type đúng enum string (`:135`, validation `:182–190`).
2. Load permission by feature key (`:136`).
3. Pessimistic lock target user (`:137`, helper `:225–232`) để serialize mọi override mutation của user.
4. Query unique pair `(userId,permissionId)` (`:139–140`).
5. Có row → `replaceWith` update type/reason/actor/expiry và reactivate (`:142–146`; entity `UserPermissionOverride.java:100–119`).
6. Chưa có → insert row (`PermissionOverrideService.java:147–151`).
7. Audit `OVERRIDE_REPLACED` hoặc `OVERRIDE_CREATED` (`:154–155`).
8. Sau commit, evict permission cache của target (`:156`).

Controller flash “Đã lưu ngoại lệ quyền” và redirect list. Không gửi notification/mail cho target.

### Hành vi biên/bảo mật

- Service không gọi `AdminPermissionsGuard`. Admin có thể chọn target ADMIN + `system.permissions`/USER_MANAGE/SYSTEM + `REVOKE`, vượt qua core lock ở matrix và tab permission user.
- Không chặn actor tự revoke quyền của chính mình và không bảo vệ “last admin with permission”. Sau lần login mới, UI RBAC có thể bị khoá.
- `expiresAt` ở quá khứ vẫn được lưu/audit success nhưng row lập tức không có hiệu lực.
- HTML cap reason 255 nhưng service/entity cho tối đa DB 500 và không server-side cap; crafted request dài hơn column có thể lỗi DB.
- Unknown user/permission ném `NoSuchElementException`, controller không bắt → 500 thay vì validation toast.

## 6. Huỷ một override

### Thao tác người dùng

Mỗi row `active=true` có nút **“Huỷ”** tại `permissions-overrides.html:146–151`:

```text
POST /admin/permissions/overrides/{id}/deactivate
```

### Controller/service

`AdminPermissionOverridesController.deactivate`, dòng 92–100, gọi `PermissionOverrideService.deactivate`.

Service `PermissionOverrideService.java:165–180`, transaction:

1. Query target user id theo override id (`:167–168`).
2. Lock user trước rồi reload override (`:169–171`), giữ lock order đồng nhất với create/toggle.
3. `override.deactivate()` set `is_active=false`, không delete history (`:172`; entity `UserPermissionOverride.java:121–124`).
4. Resolve feature key cho audit, ghi `OVERRIDE_DEACTIVATED` (`:174–178`).
5. After commit evict target cache (`:179`).

Controller flash success, redirect list. Bấm Huỷ lại bằng crafted request là idempotent về flag nhưng vẫn ghi thêm audit deactivation; unknown id → unhandled 500.

## 7. Quyền hiệu lực được tính và đưa vào request như nào

### Khi form-login

`CustomUserDetailsService.loadUserByUsername`, `CustomUserDetailsService.java:56–65`, gọi `PermissionResolver.resolvePermissions(userId)`.

`PermissionResolver.java:82–129`:

1. `EffectivePermissionRepository.findRoleDerivedPermissions` chạy recursive CTE từ `users.role` qua parent hierarchy (`EffectivePermissionRepository.java:39–54`).
2. Load override user, bỏ inactive/expired bằng JVM `LocalDateTime.now` (`PermissionResolver.java:93–109`).
3. Từ role rows, loại permission nằm trong REVOKE (`:111–118`).
4. Thêm GRANT chưa có từ role (`:120–127`).
5. Cache set theo user id (`@Cacheable`, `:82–84`). Caffeine TTL 5 phút, max 5.000 (`CacheConfig.java:90–93`).
6. `KshUserDetails` copy set thành immutable authorities `PERM_*` (`KshUserDetails.java:72–82`).

`@PreAuthorize` đọc authorities trên principal session, **không gọi resolver mỗi request**.

### Propagation sau Admin thay quyền

`TransactionLifecycle.afterCommit`, `common/TransactionLifecycle.java:12–26`, đảm bảo cache chỉ bị evict sau commit. Tuy vậy:

- session đã đăng nhập giữ `KshUserDetails.authorities` cũ đến lần authenticate mới;
- role/override hết hạn theo thời gian không tự thay session authority;
- ngay cả caller gọi resolver động, một temporary permission có thể còn trong cache tối đa 5 phút sau `expiresAt`, vì không có scheduled eviction tại chính expiry instant;
- Google OIDC principal chỉ có `ROLE_*`, không có `PERM_*` (`CustomOidcUserPrincipal.java:27–34`), nên các controller trong tài liệu này trả 403 cho OIDC Admin.

## 8. Concurrency và audit

- Override write/toggle serialize theo pessimistic user-row lock.
- Matrix attach/detach không lock role/permission; hai attach đồng thời cùng thấy absent có thể đụng composite PK và một request lỗi 500.
- Audit insert nằm cùng DB transaction; audit fail rollback mutation.
- Rejected core detach không audit (`AdminPermissionsGuard` chạy trước write).
- Audit UI riêng cho `permission_activities` không tồn tại trong hai template này; records chỉ được lưu DB. Tab history user chỉ hiện `user_activities` của quick-toggle, không hiện global override/matrix records.

## Ma trận endpoint Permissions

| Thao tác | Verb/path | Controller | Service |
|---|---|---|---|
| Xem matrix | `GET /admin/permissions` | `AdminPermissionsController.matrix:45` | `PermissionMatrixService.loadMatrix:64` |
| Attach/detach | `POST /admin/permissions` | `toggle:57` | `attach:104` / `detach:125` |
| Xem overrides | `GET /admin/permissions/overrides` | `AdminPermissionOverridesController.list:52` | list/catalog/candidates |
| Create/replace override | `POST /admin/permissions/overrides` | `save:70` | `createOrReplace:133` |
| Deactivate override | `POST /admin/permissions/overrides/{id}/deactivate` | `deactivate:93` | `deactivate:165` |
| Quick-toggle user | `POST /admin/users/{id}/permissions` | `AdminUsersEditController.togglePermission:145` | `UserPermissionToggleService.toggle:86` |
