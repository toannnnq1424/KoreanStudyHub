# Workflows: vòng đời lớp học và sinh viên xin vào lớp

Tài liệu này trace bề mặt lớp học đang chạy thực tế: giảng viên/đồng giảng/Leader/Admin xem lớp, tạo–sửa–xóa lớp, lớp chờ Leader duyệt, học sinh tìm lớp `ACTIVE`, gửi yêu cầu, chủ lớp duyệt/từ chối, học sinh vào lớp hoặc rời lớp. Import Excel được tách riêng trong [CLASS_STUDENT_IMPORT_EXCEL.md](CLASS_STUDENT_IMPORT_EXCEL.md); bài giảng, bài tập, bài test và tiến độ được trace ở tài liệu module tương ứng.

State machine thực tế:

```text
Class:       CREATE -> DRAFT --Leader approve--> ACTIVE --scheduler--> ARCHIVED
                         |--Leader reject------> DRAFT (có rejection_note, vẫn ở queue)

Enrollment: REQUEST -> PENDING --owner approve--> ACTIVE --student leave--> REMOVED
                              |--owner reject---> REJECTED --request again--> PENDING
              IMPORT/MANUAL -------------------> ACTIVE
```

## 1. Mở danh sách lớp phía giảng dạy

### Thao tác người dùng

Người dùng role `LECTURER`, `LEADER` hoặc `ADMIN` mở **Lớp học**:

```text
GET /lecturer/classes?page={0-based}&size={n}&sort={property,direction}
```

`ClassesController.list`, dòng 70-80, nhận `Pageable`; mặc định 20 dòng và `createdAt DESC` ở dòng 72-73. Controller gọi `ClassesService.listForUser` dòng 75 rồi render `classes/manage.html`.

### Service, repository và UI

`ClassesService.listForUser`, dòng 86-114, chọn scope theo role:

- `LECTURER`: lớp mà actor là chủ **hoặc** đồng giảng, qua query subquery `ClassRepository.findAllAccessibleToLecturer` (`ClassRepository.java:61-73`).
- `LEADER`: resolve tất cả mã môn phụ trách rồi query `subject_id IN (...)` (`ClassesService.java:91-95`, `ClassRepository.java:145-146`).
- `ADMIN`: mọi lớp chưa soft-delete (`ClassesService.java:96-97`).

Service batch-load mã môn và map DTO (`ClassesService.java:102-113`). `@SQLRestriction("is_deleted = 0")` trên entity (`ClassEntity.java:25-30`) tự loại lớp đã xóa khỏi mọi query JPA mặc định.

Trang `classes/manage.html` có:

- nút **Tạo lớp học** ở dòng 72-75;
- click vùng tên lớp gửi `GET /lecturer/classes/{id}` ở dòng 98-116;
- menu **Xóa lớp** và hidden POST form có CSRF ở dòng 131-145;
- phân trang server ở dòng 152-162;
- tìm kiếm/sắp xếp chỉ trên page DOM hiện tại (`classes.js:11-69,105-116`), không gọi backend;
- **Lớp đã ẩn**, **Thùng rác**, **Quản lý học sinh** chỉ là visual/disabled (`manage.html:34-43,126-129`).

Phát hiện audit: bốn cột Học sinh/Bài giảng/Bài tập/Tài liệu luôn bằng `0`, vì mapper hard-code bốn số 0 tại `ClassRowMapper.java:32-47`; sort “Sĩ số” vì vậy chưa phản ánh DB.

## 2. Giảng viên bấm “Tạo lớp học”

### Mở form và input

Nút `classes/manage.html:72-75` gửi:

```text
GET /lecturer/classes/new
```

`ClassesController.createForm`, dòng 87-97, tạo `ClassForm.empty()`, nạp mọi department active (`addSubjectOptions`, dòng 207-209), rồi render `classes/form.html`.

Form bắt đầu ở `classes/form.html:16`, có CSRF dòng 17 và các field:

| Field | UI | Ràng buộc backend |
|---|---|---|
| `name` | dòng 20-28 | bắt buộc, 3-300 ký tự (`ClassesDtos.java:65-68`) |
| `subjectId` | select dòng 30-46 | bắt buộc; subject phải tồn tại và active |
| `description` | dòng 48-57 | tối đa 2.000 ký tự |
| `endDate` | dòng 65-72 | có thể trống |
| `maxStudents` | dòng 75-84 | 1-1.000; form mặc định 100 |
| `startDate` | **không có control trong template** | DTO có field ở `ClassesDtos.java:73`, nhưng browser không gửi |

Người dùng bấm **Tạo lớp** ở `form.html:89-90`:

```text
POST /lecturer/classes
Content-Type: application/x-www-form-urlencoded
```

### Controller -> transaction -> DB

`ClassesController.create`, dòng 104-129:

1. Spring bind/validate `ClassForm`; lỗi render lại cùng form ở dòng 110-116.
2. Actor lấy từ principal, không nhận `lecturerId`/`createdBy` từ client.
3. Gọi `ClassesService.create(form,userId)` dòng 119.
4. Subject sai/inactive được bind lại vào field `subjectId` (`120-125`).

`ClassesService.create` là `@Transactional` tại dòng 157-160 và chuyển sang `ClassCreator.create`, dòng 41-62:

1. `DepartmentRepository.findById(...).filter(active)` ở dòng 42-44.
2. Tạo `ClassEntity` với `lecturerId=userId`, `createdBy=userId`, `startDate=null`, các field còn lại từ form (`ClassCreator.java:45-49`).
3. Constructor đặt `status=DRAFT` và mặc định `maxStudents=100` nếu null (`ClassEntity.java:107-118`).
4. `ClassRepository.saveAndFlush` insert `classes` (`ClassCreator.java:50`).
5. `ClassActivityWriter` insert một activity type `CREATED` (`ClassCreator.java:51-52`). DB lỗi khi ghi activity làm rollback cả lớp, vì writer không mở transaction độc lập (`ClassActivityWriter.java:59-63`).
6. Publish `ClassPendingReviewEvent` chứa class/subject/owner/name/code (`ClassCreator.java:53-56`, payload `ClassPendingReviewEvent.java:3-10`).

### Hệ thống đưa lớp tới UI của Leader

`ClassPendingReviewNotifier.notifyLeader` dùng `@TransactionalEventListener(AFTER_COMMIT)` (`ClassPendingReviewNotifier.java:27-32`), nên chỉ chạy sau khi class và activity commit:

1. Tải subject và lấy `leader_user_id` (`37-39`).
2. Nếu Leader không chính là người tạo, gọi `NotificationService.create` (`40-47`) với:
   - type `CLASS_PENDING_APPROVAL`;
   - reference type `CLASS`, reference id = class id;
   - tiêu đề **Lớp mới chờ duyệt**.
3. Không có Leader, Leader trùng owner hoặc notification lỗi: lớp vẫn tồn tại `DRAFT`; lỗi bị log/nuốt (`48-51`). Không gửi email.

Controller flash `MSG_CLASS_CREATED` và redirect `GET /lecturer/classes` (`ClassesController.java:127-128`). Lớp xuất hiện cho owner nhưng học sinh chưa thấy vì catalog chỉ query `ACTIVE`.

Lưu ý quyền: class-level pre-authorize cho phép cả `LEADER` và `ADMIN` gọi create (`ClassesController.java:50-52`). Backend không ép actor phải là `LECTURER`; bất kỳ role được phép nào tạo thì chính actor trở thành immutable owner.

## 3. Leader duyệt/từ chối lớp

Luồng UI và backend chi tiết nằm trong [LEADER_WORKFLOWS.md](LEADER_WORKFLOWS.md). Hai handler nằm ở `LeaderController.approveClass/rejectClass`, `src/main/java/com/ksh/features/leader/controller/LeaderController.java:58-83`. Phần giao tiếp state của lớp là:

- `POST /leader/approvals/{classId}/approve` -> khóa class `PESSIMISTIC_WRITE` -> `DRAFT -> ACTIVE` -> báo owner type `CLASS_APPROVED`.
- `POST /leader/approvals/{classId}/reject` -> khóa class -> gọi `ClassEntity.reject` -> **vẫn là `DRAFT`**, chỉ set `approvedBy`, `approvedAt`, `rejectionNote` (`ClassEntity.java:161-167`) -> báo owner type `CLASS_REJECTED`.

Đây là chênh lệch đáng chú ý: schema mới chỉ cho `DRAFT/ACTIVE/ARCHIVED` (`V88__subject_catalog_and_class_lifecycle.sql:68-70,93-94`), nên reject được mô hình hóa bằng `DRAFT + rejection_note`. Query queue lại lấy mọi `DRAFT`, vì vậy lớp vừa “Từ chối” vẫn hiện lại trong queue và có thể được duyệt/từ chối tiếp; UI không hiển thị rejection note trong hàng queue (`leader/approvals.html:32-51`).

## 4. Sửa thông tin lớp từ form edit hoặc Cài đặt

### Mở form

Có hai entry point cùng dùng một POST backend:

1. `GET /lecturer/classes/{id}/edit` -> `ClassesController.editForm`, dòng 136-150 -> `ClassesService.getOwnerManaged` -> `classes/form.html`.
2. Owner mở **Cài đặt lớp học** từ sidebar (`fragments/class-sidebar.html:102-108`) -> `GET /lecturer/classes/{id}/settings` -> `ClassDetailController.detailSettings`, dòng 182-200 -> `classes/detail-settings.html`.

Settings chỉ hiện cho `isPrimaryClassOwner` trong sidebar; service vẫn cho `ADMIN` quản trị trực tiếp. `getOwnerManaged` gọi policy `role == ADMIN || actorId == lecturerId` (`ClassesService.java:123-126,224-231`; `ClassRoleAccessPolicy.java:43-52`). Leader và đồng giảng không được sửa owner-managed state.

Form thường submit ở `classes/form.html:16-17,89-90`; nút **Lưu** settings dùng HTML `form="classEditForm"` (`detail-settings.html:36-49`) và form ở dòng 83-89:

```text
POST /lecturer/classes/{id}
Content-Type: application/x-www-form-urlencoded
```

Payload là `name`, `description`, `endDate`, `maxStudents`, `subjectId` hidden và CSRF. Subject select bị disabled khi edit nhưng có hidden fallback (`classes/form.html:30-45`); settings chỉ có hidden subject (`detail-settings.html:88-89`).

### Backend

`ClassesController.update`, dòng 157-176, validate rồi gọi `ClassesService.update` dòng 173. Transaction service ở `ClassesService.java:163-185`:

1. tải class owner-managed;
2. snapshot old state;
3. gọi `ClassEntity.updateDetails`;
4. save class;
5. snapshot new state và ghi activity `UPDATED` với JSON `{old,new}`.

Không có state guard: owner/Admin có thể sửa cả `DRAFT`, `ACTIVE` và `ARCHIVED`. Không gửi lại yêu cầu duyệt khi sửa một lớp đã `ACTIVE`; subject không thể đổi qua UI và service update cũng không đụng `subjectId`.

Phát hiện audit: `ClassesService.update` truyền `form.startDate()` (`168-169`), nhưng `ClassEntity.updateDetails` không assign `startDate` (`ClassEntity.java:133-142`). Vì template cũng không có input start date, ngày bắt đầu hiện không thể được tạo/cập nhật từ UI.

Thành công flash rồi redirect list (`ClassesController.java:174-175`), kể cả submit từ trang Settings; không redirect lại Settings.

## 5. Xóa lớp

### Thao tác UI

- Ở danh sách: nút **Xóa lớp** + hidden form `classes/manage.html:131-145`; `classes.js:118-138` mở modal rồi `form.submit()`.
- Ở Settings: nút toolbar `detail-settings.html:50-52`, hidden form dòng 150-157; `class-detail.js:24-46` xác nhận rồi submit.

Cả hai gửi:

```text
POST /lecturer/classes/{id}/delete
```

`ClassesController.delete`, dòng 179-186 -> `ClassesService.softDelete`, transaction dòng 188-200. Service tải owner-managed, set `is_deleted=true` (`ClassEntity.java:149-151`), save và insert activity `DELETED`. Thành công redirect `/lecturer/classes`.

Đây là soft delete nhưng modal nói “không thể hoàn tác”; hiện không có route/UI restore. Enrollment và dữ liệu con không bị xóa trong workflow này, nhưng class biến mất khỏi JPA query vì `@SQLRestriction`. Không notification/mail cho học sinh.

## 6. Mở shell chi tiết lớp phía giảng dạy

Click lớp ở `classes/manage.html:98-116`:

```text
GET /lecturer/classes/{id}
302 -> GET /lecturer/classes/{id}/board
```

Redirect nằm ở `ClassesController.detailRoot`, dòng 188-192. `ClassDetailController.detailBoard`, dòng 72-79, gọi `ClassesService.getViewable` rồi `ClassDetailModelSupport.populateDetail`.

Quyền đọc được policy hóa tại `ClassRoleAccessPolicy.canAccess`, dòng 25-41:

- Admin: mọi class;
- Lecturer: owner hoặc có row `class_co_lecturers`;
- Leader: class thuộc một subject được resolver giao;
- role khác: từ chối.

`ClassDetailModelSupport.populateDetail`, dòng 43-58, nạp owner + đồng giảng, xác định label viewer và chỉ đặt `isPrimaryClassOwner=true` khi actor chính là `classes.lecturer_id`. Trang board hiện chỉ là placeholder “Sprint 3” (`classes/detail-board.html:17-24`), không có POST đăng thông báo.

Sidebar thật (`fragments/class-sidebar.html:51-108`) dẫn tới:

- `/members`: workflow thành viên bên dưới;
- `/assignments`, `/tests`, `/progress`, `/lessons`: route thật, được audit ở tài liệu module riêng;
- `/scores`, `/materials`: `ClassDetailController.detailPlaceholder`, dòng 158-174, chỉ render `classes/detail-placeholder.html:13-20`;
- Settings chỉ render cho primary owner.

`/schedule`, `/roles`, `/groups` có controller placeholder dù sidebar hiện không link tới chúng (`ClassDetailController.java:148-174`). Tests tab gọi `LecturerExamService.listForClass` (`ClassDetailController.java:136-145`) và render các link create/edit/preview/monitor/submissions ở `classes/detail-tests.html:20-57`.

## 7. Giảng viên mở danh sách thành viên và yêu cầu chờ duyệt

Sidebar **Thành viên** (`fragments/class-sidebar.html:58-62`) gửi:

```text
GET /lecturer/classes/{classId}/members
```

`ClassDetailController.detailMembers`, dòng 82-95 -> `ClassMembersService.listForClass` (`ClassMembersService.java:37-53`):

1. `ClassesService.getViewable` kiểm quyền;
2. query enrollment `ACTIVE` và `PENDING` riêng, cả hai `JOIN FETCH user` để tránh N+1 (`EnrollmentRepository.java:24-40`);
3. map tên/email/phone/joinedVia rồi populate owner + co-lecturer qua `ClassDetailModelSupport`.

`classes/detail-members.html` render teaching team ở dòng 68-99, học sinh ACTIVE dòng 100-124, pending dòng 133-164. Ô search chỉ lọc DOM (`class-detail.js:11-21`). **Xuất danh sách**, **Thêm học sinh** và xóa từng học sinh vẫn disabled (`detail-members.html:35-53,117-121`); Import Excel là workflow thật riêng.

Chỉ primary owner thấy hai form **Duyệt/Từ chối** (`detail-members.html:149-159`). Đồng giảng, Leader, Admin chỉ thấy note “GV chủ lớp xử lý” vì template dựa vào ownership, phù hợp với service approve/reject.

## 8. Học sinh mở “Lớp của tôi” và catalog lớp đang mở

### GET và hai tab

`StudentClassesController` đặt `/my` và `@PreAuthorize(STUDENT)` tại dòng 38-40. Browser gửi:

```text
GET /my/classes?tab=mine
GET /my/classes?tab=open&q={name-or-subject-code}&page={0-based}
```

`StudentClassesController.list`, dòng 59-78:

1. luôn gọi `listEnrolledClasses` và `listPendingClasses` (`65-66`);
2. chỉ khi tab `open` mới gọi catalog, size cố định 25 (`70-72`);
3. render `student/my-classes.html`.

`StudentClassesService.listEnrolledClasses/listPendingClasses` query theo `(user,status)` (`StudentClassesService.java:56-68`; repository `EnrollmentRepository.java:104-117`). `mapRows` batch-load class/lecturer/subject và chỉ render class vẫn `ACTIVE` (`StudentClassesService.java:107-148`). Hệ quả: enrollment của class `ARCHIVED` không xuất hiện ở “Lớp của tôi”, dù row vẫn `ACTIVE` trong DB.

Tab **Lớp của tôi** và **Danh sách lớp đang mở** nằm ở `student/my-classes.html:23-35`. ACTIVE class có link board và hidden leave form (`78-123`); PENDING chỉ là row chờ duyệt, không có deep link (`128-153`). Search/sort tab mine chỉ thao tác DOM (`student-classes.js:49-88`).

Catalog form GET nằm ở `my-classes.html:163-171`. `StudentClassesService.listActiveCatalog`, dòng 70-105, gọi `ClassRepository.searchActiveCatalog`: chỉ `ClassEntity.status=ACTIVE`, match tên lớp hoặc mã môn, newest first (`ClassRepository.java:154-165`). Service batch-load subject/owner, rồi đọc mọi enrollment của actor để gắn `alreadyRequested`/`alreadyEnrolled` (`StudentClassesService.java:78-104`).

Trang hiển thị:

- **Đã tham gia** nếu status ACTIVE;
- **Đang chờ duyệt** nếu PENDING;
- form **Yêu cầu tham gia** nếu không phải hai trạng thái trên (`my-classes.html:175-194`);
- pagination giữ `q` ở dòng 197-204.

REJECTED/REMOVED/COMPLETED đều không có badge catalog; UI sẽ hiện lại nút request, nhưng backend từ chối COMPLETED.

## 9. Học sinh bấm “Yêu cầu tham gia”

### UI -> HTTP -> controller

Form `student/my-classes.html:190-193` gửi browser POST thường, CSRF được Spring/Thymeleaf chèn cho form `th:action`:

```text
POST /my/classes/{classId}/request
```

`StudentClassesController.requestJoin`, dòng 80-92, lấy actor từ principal và gọi `JoinClassService.requestJoin` dòng 85. Không nhận student id từ client.

### Transaction, khóa sức chứa và state

`JoinClassService.requestJoin` là transaction tại dòng 50-94:

1. load class; chỉ `ACTIVE` mới nhận request (`53-57`); owner không được tự request (`58-60`). Class không nằm catalog hoặc bị archive giữa GET/POST sẽ bị từ chối.
2. tìm row unique `(user_id,class_id)` (`62-64`; repository `EnrollmentRepository.java:84-96`). Unique index nằm ở `V1__init_schema.sql:222-239`.
3. Nếu row:
   - `ACTIVE` -> trả `AlreadyJoined`, không ghi DB;
   - `COMPLETED` -> lỗi;
   - `PENDING` -> trả `PendingRequested(alreadyPending=true)`;
   - `REJECTED` hoặc `REMOVED` -> kiểm capacity, đổi thành `PENDING`, `joined_via=REQUEST` (`JoinClassService.java:69-82`; `Enrollment.java:136-144`).
4. Nếu chưa có row: kiểm capacity, load user, tạo `Enrollment.createPending(...REQUEST...)` rồi save (`85-93`; `Enrollment.java:110-119`).

`enforceCapacity` (`JoinClassService.java:145-152`) khóa class bằng `ClassRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`, repository dòng 26-33), rồi dùng native locking count `FOR SHARE` trên enrollment ACTIVE (`EnrollmentRepository.java:130-138`). **PENDING không chiếm capacity**; hai request vẫn có thể cùng chờ khi chỉ còn một chỗ, nhưng approve sau đó kiểm/khóa lại.

Sau khi lưu PENDING:

- `JoinAuditWriter.writeJoin` insert activity type `MEMBER_JOINED`, description “Học viên tham gia lớp...” (`JoinAuditWriter.java:26-34`), dù nghiệp vụ thực tế mới đang PENDING. Đây là nhãn audit dễ gây hiểu sai.
- `emitJoinRequestToOwner` tạo notification cho `classes.lecturer_id`, type `JOIN_REQUEST`, ref class (`JoinClassService.java:162-170`). Notification lỗi bị nuốt; enrollment/activity vẫn commit. Không email.

Controller flash:

- request mới: “đã gửi/chờ duyệt”;
- đã pending: info “đang chờ duyệt”;
- đã active: info “đã ở trong lớp”;
- class/state/capacity sai: flash error;

rồi redirect `GET /my/classes` (`StudentClassesController.java:84-92,110-125`).

## 10. Chủ lớp bấm “Duyệt” yêu cầu

### UI và HTTP

Tại `classes/detail-members.html:149-154`:

```text
POST /lecturer/classes/{classId}/members/{studentUserId}/approve
```

`ClassDetailController.approveMember`, dòng 98-114, gọi `JoinClassService.approve` dòng 104.

### Backend -> student UI

Transaction `JoinClassService.approve`, dòng 114-129:

1. khóa class row `PESSIMISTIC_WRITE` (`116-117`);
2. `requireOwner` gọi `ClassesService.getEditable`, sau đó bắt buộc `clazz.lecturerId == actorId` (`154-159`). Vì phép so cuối này, **Admin cũng không approve được**, dù `getEditable` cho Admin đọc;
3. load đúng enrollment `(student,class)`, bắt buộc `PENDING` (`119-123`);
4. kiểm capacity lần nữa dưới class lock (`124`);
5. `Enrollment.activateFromPending`: `PENDING -> ACTIVE`, save (`125-126`; entity `Enrollment.java:146-150`);
6. gửi hai notification best-effort cho học sinh: `JOIN_APPROVED` và `CLASS_ENROLLED`, ref class (`JoinClassService.java:173-183`). Không email.

Controller flash success và redirect `GET /lecturer/classes/{id}/members` (`ClassDetailController.java:104-113`). GET mới chuyển row từ khu “Chờ duyệt” sang danh sách ACTIVE. Lần sau học sinh mở `/my/classes`, `listEnrolledClasses` hiển thị class và cho vào board.

Không có activity riêng khi owner approve; activity `MEMBER_JOINED` đã được ghi lúc request.

## 11. Chủ lớp bấm “Từ chối” yêu cầu

Form `classes/detail-members.html:155-159` gửi:

```text
POST /lecturer/classes/{classId}/members/{studentUserId}/reject
```

`ClassDetailController.rejectMember`, dòng 117-133 -> `JoinClassService.reject`, transaction dòng 131-143:

1. bắt buộc actor là immutable owner;
2. enrollment phải `PENDING`;
3. `markRejected`: `PENDING -> REJECTED` (`Enrollment.java:152-155`);
4. save và gửi notification best-effort type `JOIN_REJECTED` cho student (`JoinClassService.java:186-194`).

Khác approve, reject không lấy class/enrollment pessimistic lock. Hai request đồng thời approve-vs-reject có khả năng cùng đọc `PENDING`; không có `@Version` trên `Enrollment`, nên kết quả có thể last-writer-wins tùy DB ordering. Đây là khoảng trống concurrency.

Controller redirect lại members; row biến mất khỏi pending. Student catalog sau đó hiện lại nút request và request mới đổi chính row này `REJECTED -> PENDING`.

## 12. Học sinh vào board/thành viên lớp

Hai GET dùng cùng read model nhưng phải tách đúng method/template:

- `StudentClassDetailController.board`: `GET /my/classes/{classId}/board` → `StudentClassDetailService.get(classId,userId)` kiểm ACTIVE enrollment, load class/lecturer/member summary rồi render `templates/student/class-board.html` (`StudentClassDetailController.java:31–36`). Board hiện không query announcement và không có POST tạo bài đăng.
- `StudentClassDetailController.members`: `GET /my/classes/{classId}/members` → cùng `StudentClassDetailService.get`/enrollment gate, render `templates/student/class-members.html` (`:39–44`). Đây là authoritative member snapshot ở mỗi GET; search/presentation nếu có chỉ lọc DOM, không tạo member state.

Click class ACTIVE ở `student/my-classes.html:78-108` gửi:

```text
GET /my/classes/{classId}
302 -> GET /my/classes/{classId}/board
```

Redirect ở `StudentClassDetailController.root`, dòng 26-29. Hai route thật:

- `GET /my/classes/{classId}/board` -> controller dòng 31-37 -> `student/class-board.html`;
- `GET /my/classes/{classId}/members` -> controller dòng 39-45 -> `student/class-members.html`.

Cả hai gọi `StudentClassDetailService.get`, transaction read-only dòng 42-71:

1. buộc actor có enrollment `ACTIVE` (`44-46`);
2. buộc class vẫn `ACTIVE` (`47-49`);
3. load owner, subject, co-lecturers, mọi enrollment ACTIVE;
4. de-duplicate member bằng `LinkedHashMap`, đánh `canMessage=false` cho chính actor (`57-70,73-82`).

Board hiện chỉ là empty placeholder (`student/class-board.html:17-27`); không có thông báo class được persist/đọc. Members page có client-side filter (`student/class-members.html:24-45`) và mỗi người khác có form:

```text
POST /my/messages/new
to={userId}
```

Form nằm ở `student/class-members.html:40-43`; việc kiểm eligibility/tạo conversation thuộc module Messaging. UI cho nút với owner, co-lecturer và cả học sinh khác; backend Messaging vẫn là lớp kiểm quyền cuối.

Sidebar student (`fragments/student-class-sidebar.html:35-109`) dẫn tới board, members, assignments, lessons, tests; **Tài liệu** disabled. Nút **Rời khỏi lớp này** dùng cùng endpoint leave. Route tests/messages thực thi bởi controller module Tests/Messaging, không phải `StudentClassDetailController`.

## 13. Học sinh bấm “Rời khỏi lớp”

Nút ở list `student/my-classes.html:100-123` hoặc sidebar `fragments/student-class-sidebar.html:97-108` được `student-classes.js:13-31` bắt, mở confirm rồi submit:

```text
POST /my/classes/{classId}/leave
```

`StudentClassesController.leave`, dòng 94-108 -> `JoinClassService.leave`, transaction dòng 96-112:

1. tìm enrollment actor/class; `REMOVED` được coi như not found;
2. `COMPLETED` không được rời;
3. load class;
4. đổi bất kỳ trạng thái còn lại (`ACTIVE`, `PENDING`, thậm chí `REJECTED`) thành `REMOVED` (`Enrollment.java:157-160`);
5. save và insert activity `MEMBER_LEFT` (`JoinAuditWriter.java:37-45`).

UI chỉ render leave cho ACTIVE, nhưng backend không bắt buộc status ACTIVE; POST trực tiếp có thể hủy một PENDING request bằng cách chuyển thành REMOVED. Không notification owner/mail. Controller flash rồi redirect `/my/classes`.

Workflow không khóa enrollment (`findByUserIdAndClassIdForUpdate` tồn tại ở repository dòng 98-102 nhưng không được dùng), nên leave đồng thời với owner approve/reject cũng có race/last-writer risk.

## 14. Scheduler tự archive lớp hết hạn

Không có button. `ClassAutoArchiveWorker.archiveDueClasses` chạy theo cron cấu hình `ksh.classes.auto-archive-cron`, mặc định `00:10` mỗi ngày (`ClassAutoArchiveWorker.java:30-38`):

1. query mọi class `ACTIVE` có `endDate <= today` (`ClassRepository.java:150`);
2. gọi `ClassEntity.archive`: `ACTIVE -> ARCHIVED` (`ClassEntity.java:169-173`);
3. transaction dirty-check commit.

Worker không ghi `ClassActivity`, không gửi notification/mail, không đổi enrollment. Sau archive, lớp biến mất khỏi student catalog và “Lớp của tôi” vì read service chỉ giữ class ACTIVE.

## 15. Security, transaction và edge cases tổng hợp

- Mọi POST form dùng Spring Security CSRF; actor luôn đến từ `KshUserDetails`, không tin user id hiện tại do client gửi. Path `studentUserId` trong approve/reject vẫn được ràng buộc vào đúng class/enrollment.
- Class create/update/delete và join/leave/approve/reject đều transaction. Activity DB write nằm cùng transaction; notification join/member là best-effort trong try/catch, notification create-class chạy `AFTER_COMMIT`.
- Capacity chỉ đếm `ACTIVE`; approve serialize trên class row và dùng locking count. Reject/leave/request lookup enrollment không dùng lock row dù repository có query lock.
- `ClassEntity` không có optimistic `@Version`. Leader approval dùng pessimistic class lock; owner update/delete không khóa, nên concurrent update/review/delete có thể ghi đè state/chi tiết theo timing.
- Lớp reject vẫn `DRAFT` và tiếp tục ở queue. Không có route để owner “gửi lại duyệt”; sửa DRAFT cũng không phát event mới, nhưng queue vẫn thấy row tự động.
- Không có AI call trong toàn bộ workflow lớp/enrollment này.
