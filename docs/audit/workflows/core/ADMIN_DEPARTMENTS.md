# Workflows Admin: danh mục mã môn và gán trưởng bộ môn

Tài liệu này bao phủ toàn bộ thao tác production ở màn hình legacy `/admin/departments`: xem/tìm/lọc/sắp xếp danh mục môn, tạo mã môn, mở/sửa thông tin, xem lịch sử, ẩn/hiện và gán/bỏ/đổi trưởng bộ môn. Tên URL và class vẫn là “department”, nhưng entity thật ánh xạ bảng `subjects` (`Department.java:14–23`); UI gọi đây là **Mã môn**.

## Quyền truy cập chung

Mọi request đi qua hai lớp bảo vệ:

1. `SecurityConfig.java:217–242` buộc mọi `/admin/**` có `ROLE_ADMIN`, cụ thể matcher tại dòng 238.
2. `AdminDepartmentsController.java:32–35` buộc authority `PERM_subject.manage` cho cả GET và POST.

Mọi form POST có CSRF hidden token (`departments.html:147–151`, `departments-form.html:72–77`). Actor không lấy từ input: controller lấy `@AuthenticationPrincipal KshUserDetails` và chuyển thành actor id tại `AdminDepartmentsController.java:168–170`.

Không workflow nào trong tài liệu này gọi AI, gửi email hay tạo notification/WebSocket event. Các thay đổi role do gán leader chỉ ghi DB + audit; người bị đổi role không nhận thông báo.

## 1. Mở danh mục, tìm kiếm, lọc và sắp xếp

### Thao tác người dùng

Admin bấm **“Mã môn”** trên sidebar tại `fragments/admin-sidebar.html:46–57`:

```text
GET /admin/departments
```

Toolbar ở `templates/admin/departments.html:28–78` là một form GET. Người dùng có thể nhập/chọn:

| Query | UI line | Giá trị |
|---|---:|---|
| `q` | 37–41 | substring của tên hoặc mã môn |
| `status` | 43–51 | rỗng, `active`, `inactive` |
| `sort` | 53–67 | `name,asc/desc`, `code,asc/desc`, `createdAt,asc/desc` |

Nút **“Áp dụng”** tại dòng 69 gửi browser navigation GET, không dùng fetch.

### Controller → query service → repository

`AdminDepartmentsController.list`, `AdminDepartmentsController.java:51–62`:

1. Bind ba query param thành `DepartmentFilter` (`DepartmentDtos.java:16–25`).
2. Gọi `DepartmentQueryService.list(filter)` ở dòng 58.
3. Đặt `departments`, `filter`, `activeTab=departments` vào model và render `admin/departments`.

`DepartmentQueryService.list`, `DepartmentQueryService.java:55–78`, chạy transaction read-only:

1. `DepartmentRepository.findAllByOrderByNameAsc()` tải **toàn bộ** rows `subjects` (`DepartmentRepository.java:23`). Không phân trang ở DB.
2. Gom các `leader_user_id`, batch-load `User` để map id → tên tại `DepartmentQueryService.java:120–135`. Vì `User` có SQL restriction bỏ soft-deleted user, leader đã soft-delete sẽ hiện nhãn `—` dù pointer vẫn còn.
3. Filter được thực hiện **in-memory** (`:65–68`): `q` trim/lowercase rồi tìm trong name/code (`:137–145`); status chỉ hiểu `active/inactive`, giá trị lạ trở thành “tất cả” (`:147–159`).
4. Sort cũng in-memory theo whitelist (`:161–176`); key lạ mặc định name tăng dần.
5. Mỗi row chứa id/code/name/description/active/leader id/leader label/created time (`:69–74`).

### UI nhận kết quả

Template dựng table ở `departments.html:81–155`: tên/mã, trưởng phụ trách, trạng thái **Hiện/Ẩn**, menu chỉnh sửa và toggle. Không có paging; số bản ghi lớn sẽ tải, filter và render toàn bộ trong một request.

Empty state nằm tại `departments.html:158–161`. Đây là read-only workflow, không audit và không có notification.

## 2. Mở form tạo mã môn

### Thao tác người dùng

Admin bấm **“Thêm mã môn”** tại `departments.html:70–76`:

```text
GET /admin/departments/new
```

`AdminDepartmentsController.createForm`, `AdminDepartmentsController.java:64–71`, tạo `DepartmentForm.empty()` với `active=true` (`DepartmentDtos.java:63–65`), rồi gọi `populateFormModel` để nạp candidate leader tại controller dòng 155–161.

`DepartmentQueryService.leaderCandidates`, `DepartmentQueryService.java:105–112`, gọi `UserRepository.findByRoleInAndActiveTrueOrderByFullNameAsc` (`UserRepository.java:269`): dropdown chỉ có user active mang role `LECTURER` hoặc `LEADER`. SQL restriction trên `User` tự loại soft-deleted user, nhưng query **không loại user đang locked**.

Controller render `admin/departments-form`. Form thật bắt đầu ở `departments-form.html:71–142`.

## 3. Tạo mã môn

### Dữ liệu UI gửi

Form create ở `departments-form.html:72–77` gửi browser POST thường. Nút ở toolbar `:33–35` và nút cuối form `:138–141` đều submit cùng form:

```text
POST /admin/departments
Content-Type: application/x-www-form-urlencoded
```

| Field | UI line | Server validation |
|---|---:|---|
| `name` | 82–87 | nonblank, tối đa 200 (`DepartmentDtos.java:48–50`) |
| `code` | 90–96 | nonblank, tối đa 20 (`DepartmentDtos.java:52–54`) |
| `description` | 99–104 | optional, tối đa 65.535 (`DepartmentDtos.java:56–57`) |
| `leaderUserId` | 107–119 | optional Long; service xác thực lại user/role/state |
| `active` | 121–131 | checkbox boolean, mặc định true khi mở form |
| CSRF | 77 | token chống request giả mạo |

### Controller

`AdminDepartmentsController.create`, `AdminDepartmentsController.java:73–92`:

1. Spring bind + Bean Validation. Lỗi thì nạp lại candidate và render form ngay (`:79–81`), không gọi service.
2. Thành công gọi `DepartmentService.create(form, actorId)` tại dòng 84.
3. `DepartmentValidationException` được hiển thị trong form (`:87–90`).
4. Thành công flash `MSG_DEPARTMENT_CREATED + savedName`, redirect:

```text
GET /admin/departments
```

### Service/entity/repository/transaction

`DepartmentService.create`, `DepartmentService.java:62–86`, là một transaction duy nhất:

1. `normalizeCode` trim + uppercase bằng `Locale.ROOT` (`:284–286`).
2. `DepartmentRepository.existsByCode` kiểm trùng (`:64–67`, repository dòng 27).
3. Tạo `Department` với name trim, description blank → null, trạng thái từ checkbox (`:68–72`). Entity ánh xạ bảng `subjects`; timestamp được đặt trong `@PrePersist` (`Department.java:21–48`, `:69–74`).
4. Nếu có `leaderUserId`, service **khóa mutex toàn cục trước khi ghi** bằng `lockLeaderAssignmentAnchor()` (`DepartmentService.java:73–75`). Mutex là `PESSIMISTIC_WRITE` trên row `system_settings.setting_key='ai.provider'` (`DepartmentService.java:247–258`, `SystemSettingsRepository.java:43–55`). Giá trị setting AI không được dùng; row chỉ bị tái sử dụng làm lock anchor.
5. Save `subjects`, rồi ghi `subjects_activities` type `CREATED` (`DepartmentService.java:76–79`).
6. Nếu chọn leader, gọi `applyLeaderAssignment`, save lại subject (`:81–84`). Nhánh này được trace chi tiết ở workflow 6.
7. Bất kỳ lỗi nào sau đó rollback subject, role/pointer và cả audit `CREATED` vì cùng transaction.

DB có unique index `subjects.code` tại `V1__init_schema.sql:96–108`. Check-then-insert của service không khóa khoảng mã, nên hai request tạo đồng thời cùng code vẫn có thể cùng vượt `existsByCode`; constraint sẽ chặn request thua. Controller không catch `DataIntegrityViolationException`, nên race này có thể thành HTTP 500 thay vì inline “Mã môn đã tồn tại”.

### UI nhận kết quả

Redirect GET tải lại danh sách và app header drain flash thành toast. Không có mail/notification tới leader được chọn.

## 4. Mở chi tiết, chuyển tab và xem lịch sử audit

### Mở tab thông tin

Admin bấm tên môn hoặc **“Chỉnh sửa”** tại `departments.html:93–98` / `:117–124`:

```text
GET /admin/departments/{id}/edit?tab=info
```

`AdminDepartmentsController.editForm`, `AdminDepartmentsController.java:94–117`:

1. `DepartmentQueryService.loadForm(id)` đọc subject bằng `findById` và map sang form (`DepartmentQueryService.java:94–103`).
2. Không tồn tại → flash `MSG_DEPARTMENT_NOT_FOUND`, redirect danh sách (`AdminDepartmentsController.java:100–104`).
3. Tab chỉ chấp nhận `info/history`; giá trị lạ về `info` (`:108–110`).
4. Nạp leader candidates và render `admin/departments-form` (`:105–116`).

### Người dùng bấm “Lịch sử cập nhật”

Link tại `departments-form.html:58–65` gửi:

```text
GET /admin/departments/{id}/edit?tab=history&page=0
Accept: text/html
X-Requested-With: XMLHttpRequest   # khi JS hoạt động
```

`detail-tabs.js:188–220` bắt click; `navigate` fetch cùng URL tại dòng 114–147, parse full HTML và chỉ thay `#tabPanel` tại dòng 148–175. Nếu JS tắt, link vẫn là full-page GET (`detail-tabs.js:8–13`). Nếu fetch lỗi, thiếu panel hoặc bị redirect login, JS fallback `window.location.href=url` (`:179–185`).

Controller clamp page về ≥0 và tạo `PageRequest(page, 20)` tại `AdminDepartmentsController.java:111–115`. `DepartmentQueryService.listActivities`, `DepartmentQueryService.java:114–118`, gọi JPQL projection:

- bảng `subjects_activities`, chỉ rows của subject;
- left join actor `users` để lấy email;
- order `created_at DESC, id DESC`;
- query nằm tại `SubjectActivityRepository.java:15–23`.

Template hiển thị type/message/actor/time tại `departments-form.html:145–190`; pager cũng được AJAX orchestrator bắt. Cột `metadata` được service ghi cho `UPDATED` nhưng **không hiển thị trên UI**.

`detail-tabs-dirty-guard.js:64–85,133–152` snapshot form và hỏi xác nhận nếu admin đổi dữ liệu nhưng chưa lưu rồi chuyển tab. Toolbar Save bị disable ngoài tab `info` bởi template dòng 33–35 và được đồng bộ lại ở `detail-tabs.js:81–91`.

## 5. Sửa tên, mã, mô tả hoặc trạng thái

### Thao tác người dùng

Ở tab **“Thông tin chung”**, admin sửa fields rồi bấm **“Lưu”** tại `departments-form.html:33–35`. Form action được chọn ở dòng 72–76:

```text
POST /admin/departments/{id}/edit
Content-Type: application/x-www-form-urlencoded
```

Form luôn gửi cả `leaderUserId` và `active`, nên một lần Save có thể đồng thời đổi thông tin, visibility và leader.

### Controller

`AdminDepartmentsController.update`, `AdminDepartmentsController.java:119–139`:

1. Bean Validation lỗi → render lại edit/info và candidate list (`:126–128`).
2. Gọi `DepartmentService.update(id, form, actorId)` (`:130–131`).
3. Business validation lỗi → render form với `flashError` (`:134–137`).
4. Thành công flash `MSG_DEPARTMENT_UPDATED`, redirect canonical URL:

```text
GET /admin/departments/{id}/edit?tab=info
```

### Service, lock order và state transition

`DepartmentService.update`, `DepartmentService.java:88–127`, chạy transaction:

1. Luôn khóa global leader anchor `system_settings['ai.provider']`, kể cả form không đổi leader (`:90–92`). Thiếu seed row → `IllegalStateException` và HTTP 500; service chủ ý fail-closed (`:247–258`).
2. Khóa target subject bằng `PESSIMISTIC_WRITE`, `DepartmentRepository.findByIdForUpdate` (`DepartmentService.java:93–94`; repository `:18–21`).
3. Normalize code, kiểm code khác row hiện tại (`DepartmentService.java:95–98`).
4. Tính diff name/code/description/active trước khi mutate (`:100–102`, helper `:260–277`). Với description, audit chỉ lưu `true`, không lưu nội dung cũ/mới.
5. `Department.applyEdit` thay bốn field (`Department.java:81–87`).
6. `applyLeaderAssignment` kiểm và đồng bộ leader/user role (`DepartmentService.java:108`), rồi save entity.
7. Nếu name/code/description thay đổi, ghi một audit `UPDATED` + JSON metadata; `active` bị tách khỏi metadata này (`:111–118`).
8. Nếu active đổi, ghi riêng `ACTIVATED` hoặc `DEACTIVATED` (`:119–126`).

Toàn bộ subject edit, user role/subject, và audit commit/rollback cùng nhau. Hai update cùng target được serialize bởi anchor rồi row lock.

### UI nhận kết quả

Sau redirect, form được query lại nên hiển thị state đã commit. Flash thành toast. Không có push update cho các browser/session khác.

## 6. Gán, đổi hoặc bỏ trưởng bộ môn

### Thao tác người dùng và endpoint

Leader không có nút POST riêng. Admin chọn **“Trưởng bộ môn phụ trách”** tại `departments-form.html:107–119`, rồi bấm Save. Request vẫn là:

```text
POST /admin/departments/{id}/edit
leaderUserId=<user-id>   # gán/đổi
leaderUserId=            # bỏ gán
```

Dropdown chỉ gợi ý active LECTURER/LEADER, nhưng client có thể sửa id; service không tin dropdown.

### Logic cốt lõi

Update đã khóa global anchor và subject. Sau đó `DepartmentService.applyLeaderAssignment`, `DepartmentService.java:158–215`:

1. Đọc `oldLeaderId`; old/new đều null → return (`:159–162`).
2. `lockAffectedUsers` gom old + new, bỏ null/trùng, sort id tăng dần rồi gọi `UserRepository.findByIdForUpdate` từng row (`:217–235`; repository `UserRepository.java:87–107`). Lock order cố định giảm deadlock.
3. New id không tồn tại → `MSG_LEADER_NOT_FOUND` (`DepartmentService.java:167–169`).
4. Candidate phải active, không soft-delete và role thuộc `LECTURER/LEADER` (`:170–173`). **Locked account vẫn hợp lệ**, vì service không kiểm `candidate.isLocked()`.
5. Nếu old id = new id, service vẫn repair candidate thành `LEADER` và `subjectId=target subject` nếu lệch, rồi return không ghi leader audit (`:177–187`).
6. Nếu đổi thật, `User.promoteToLeader(subjectId)` đặt role `LEADER` và ghi `users.subject_id` bằng subject hiện tại (`DepartmentService.java:190–194`; `User.java:223–230`).
7. `Department.assignLeader(newId)` thay `subjects.leader_user_id`; `saveAndFlush` đưa pointer xuống DB **trước** khi kiểm demote old leader (`DepartmentService.java:196–199`).
8. Old leader chỉ bị hạ thành `LECTURER` khi không còn subject nào có pointer tới họ (`:200–203`, `:237–245`). `demoteFromLeaderToLecturer` giữ nguyên `subjectId` (`User.java:232–241`).
9. New null → audit `LEADER_CLEARED`; new có giá trị → audit `LEADER_ASSIGNED` kèm email (`DepartmentService.java:205–213`). Khi đổi A → B, chỉ ghi `LEADER_ASSIGNED` cho B, không có event cleared riêng cho A.

`DepartmentService.assignLeader`, dòng 142–156, là public service method thực hiện cùng lock/logic nhưng **không có controller, route hay nút UI production nào gọi trực tiếp**. Mọi thao tác người dùng hiện đi qua form update.

### State sau commit và ảnh hưởng đăng nhập

```text
subjects.leader_user_id = newLeaderId hoặc NULL
users[newLeaderId].role = LEADER
users[newLeaderId].subject_id = subject đang sửa
users[oldLeaderId].role = LECTURER  # chỉ khi không còn pointer leader nào
subjects_activities += leader audit
```

`KshUserDetails` snapshot role/authorities khi login. Workflow này không revoke session và không refresh principal:

- lecturer vừa được nâng LEADER phải đăng nhập lại mới có `ROLE_LEADER` và vào `/leader/**`;
- người vừa bị hạ có thể giữ `ROLE_LEADER` trong session cũ cho tới logout/session hết hạn;
- không mail, toast cá nhân hay notification nào báo cho old/new leader.

### Mâu thuẫn invariant quan trọng trong source

Actual code cho phép một user đứng trong `leader_user_id` của nhiều subject:

- comment thực thi nói leader có thể curate nhiều subject-code rows (`DepartmentService.java:174`);
- DB chỉ tạo index thường trên `leader_user_id`, không unique (`V48__standardize_subject_leader_role.sql:78–84`);
- service không gọi `existsByLeaderUserIdAndIdNot` và không dùng `MSG_LEADER_ALREADY_ASSIGNED`.

Nhưng `DepartmentRepository.java:37–41` lại mô tả product rule “một user tối đa một department”, và `User` chỉ có **một** scalar `subjectId`. Mỗi lần gán cùng leader sang subject khác, `promoteToLeader(newSubjectId)` ghi đè `users.subject_id`, trong khi pointer của subject trước vẫn giữ nguyên. Nhánh “repair invariant” old=new (`DepartmentService.java:177–186`) cũng không thể đồng thời làm `user.subjectId` khớp nhiều subject.

Vì vậy dữ liệu có thể trở thành:

```text
subject A.leader_user_id = user 9
subject B.leader_user_id = user 9
user 9.subject_id        = B       # assignment gần nhất thắng
```

Đây là mâu thuẫn giữa pointer nhiều-nhiều thực tế và ownership một-nhiều của user; các workflow downstream dựa vào `user.subject_id` có thể chỉ nhìn thấy môn gán cuối.

## 7. Ẩn/hiện nhanh từ danh sách

### Thao tác UI → request

Admin mở menu row và bấm **“Ẩn môn học”** hoặc **“Hiện môn học”** tại `departments.html:109–143`. `admin-departments.js:12–19` bắt click, tìm hidden form và gọi native `form.submit()`:

```text
POST /admin/departments/{id}/toggle
Content-Type: application/x-www-form-urlencoded
```

Hidden form + CSRF nằm tại `departments.html:147–151`. Không có confirm dialog và không dùng fetch/JSON.

### Controller/service/state

`AdminDepartmentsController.toggle`, `AdminDepartmentsController.java:141–153`, gọi `DepartmentService.toggleActive(id, actorId)`, đặt flash theo state mới rồi luôn redirect danh sách. Business not-found thành flash error.

`DepartmentService.toggleActive`, `DepartmentService.java:129–140`, là transaction:

1. khóa subject row `PESSIMISTIC_WRITE` (`DepartmentRepository.java:18–21`);
2. `Department.toggleActive()` đảo boolean (`Department.java:94–98`);
3. save;
4. ghi `ACTIVATED` hoặc `DEACTIVATED` audit.

Toggle không lấy global leader anchor vì không đổi pointer/role. Hai toggle đồng thời cùng row được serialize; mỗi request đảo state một lần, nên hai click đồng thời có thể đưa state trở lại ban đầu nhưng vẫn tạo hai audit events.

Ẩn subject **không** bỏ leader, không hạ role, không sửa lớp/test/library đang tồn tại. Nó chỉ làm `subjects.is_active=false`; các consumer dùng `DepartmentQueryService.activeOptions()` (`DepartmentQueryService.java:87–92`) sẽ ngừng đưa môn đó vào dropdown chọn mới.

## 8. Những endpoint/trạng thái không tồn tại

- Không có delete/restore subject trong controller, template hay JS.
- Không có endpoint JSON cho danh mục; list/form/history trả HTML, POST dùng redirect-after-post.
- Không có nút gán leader độc lập; `assignLeader` là service surface không được route.
- Không có workflow AI trong phần này.
- Không gửi notification/mail khi tạo, sửa, ẩn/hiện hoặc đổi leader.

## Ma trận endpoint

| UI action | Verb + path | Controller | Service/query | Kết quả |
|---|---|---|---|---|
| Mở/lọc danh mục | `GET /admin/departments` | `list` `:52–62` | `DepartmentQueryService.list` `:59–78` | HTML list |
| Mở form tạo | `GET /admin/departments/new` | `createForm` `:64–71` | `leaderCandidates` `:105–112` | HTML form |
| Tạo mã môn | `POST /admin/departments` | `create` `:73–92` | `DepartmentService.create` `:62–86` | flash + redirect list |
| Mở info | `GET /admin/departments/{id}/edit?tab=info` | `editForm` `:94–117` | `loadForm` `:94–103` | HTML/partial panel |
| Xem history | `GET /admin/departments/{id}/edit?tab=history&page=N` | `editForm` `:94–117` | `listActivities` `:114–118` | HTML/partial panel |
| Sửa/gán leader | `POST /admin/departments/{id}/edit` | `update` `:119–139` | `DepartmentService.update` `:88–127` | flash + redirect info |
| Ẩn/hiện | `POST /admin/departments/{id}/toggle` | `toggle` `:141–153` | `toggleActive` `:129–140` | flash + redirect list |

## Edge/security cần nhớ

- Backend gate là `ROLE_ADMIN` **và** `PERM_subject.manage`; ẩn/hiện button không phải lớp bảo vệ.
- Candidate dropdown không loại locked user; service cũng không loại locked user.
- Role/subject thay đổi không revoke/refresh session và không thông báo người bị tác động.
- List không phân trang và filter/sort in-memory.
- Duplicate-code create có race; DB unique chặn nhưng controller không chuyển constraint exception thành lỗi thân thiện.
- Edit phụ thuộc seed row `system_settings['ai.provider']`; thiếu row làm mọi edit/gán leader fail 500.
- Metadata audit được ghi nhưng history UI không render.
- Cho phép một leader nhiều subject mâu thuẫn với scalar `users.subject_id` và repository contract như phân tích ở workflow 6.
