# Workflows của Leader: dashboard, duyệt lớp, phân công đồng giảng và báo cáo

Tài liệu này bao phủ toàn bộ bảy handler trong `LeaderController`. Mọi route có class-level `@PreAuthorize("hasRole('LEADER')")` tại `src/main/java/com/ksh/features/leader/controller/LeaderController.java:29-32`.

## 1. Cách hệ thống xác định bộ môn Leader được quản lý

Mọi service gọi `LeaderDepartmentResolver.resolveAll`, `src/main/java/com/ksh/features/leader/service/LeaderDepartmentResolver.java:42-56`:

1. ưu tiên tất cả subject active có `subjects.leader_user_id = userId`, sort code;
2. nếu không có, fallback `users.subject_id` rồi yêu cầu subject active;
3. không match thì trả list rỗng, UI hiện “Bạn chưa được gán bộ môn”.

Leader có thể quản nhiều mã môn; các màn gộp dữ liệu của toàn bộ danh sách đó.

## 2. Leader mở dashboard

```text
GET /leader
GET /leader/
```

`LeaderController.dashboard`, `LeaderController.java:85-94`, gọi `LeaderDashboardService.load(user.id)` và render `templates/leader/dashboard.html`.

`LeaderDashboardService.load`, `src/main/java/com/ksh/features/leader/service/LeaderDashboardService.java:42-90`, với từng subject:

- count mọi class chưa soft-delete qua repository;
- SQL count user active role `LECTURER/LEADER` có `users.subject_id` tương ứng (`58-61`);
- SQL count distinct student có enrollment ACTIVE trong class của subject (`62-66`);
- SQL count question bank `APPROVED` (`67-69`);
- lấy lớp mới nhất rồi gộp/sort/cắt 5 (`70-82`).

Template `leader/dashboard.html:30-73` chỉ hiển thị KPI và bảng lớp gần đây; không có form mutation.

## 3. Leader mở hàng đợi duyệt lớp

```text
GET /leader/approvals
```

`LeaderController.approvals`, `LeaderController.java:49-56`, gọi `LeaderClassApprovalService.load`. Service tại `src/main/java/com/ksh/features/leader/service/LeaderClassApprovalService.java:40-59` query từng subject với `status=DRAFT`, gộp theo `createdAt DESC`, lookup tên owner và trả `PendingClassRow`.

`templates/leader/approvals.html:32-51` tạo hai form POST có CSRF cho từng lớp. Lớp xuất hiện ở đây nhờ workflow tạo lớp đã commit `DRAFT`; notification `CLASS_PENDING_APPROVAL` chỉ báo chuông, queue tự đọc DB chứ không phụ thuộc notification.

## 4. Leader bấm “Duyệt lớp”

```text
POST /leader/approvals/{classId}/approve
```

`LeaderController.approveClass`, `LeaderController.java:58-69`, gọi `LeaderClassApprovalService.approve(user.id,classId)`.

Service `LeaderClassApprovalService.java:61-69,82-90`:

1. resolve subject của Leader;
2. `ClassRepository.findByIdForUpdate` khóa row pessimistic;
3. chặn nếu `class.subjectId` không thuộc Leader;
4. `ClassEntity.approve` set `status=ACTIVE`, `approvedBy`, `approvedAt`;
5. save class;
6. gọi `NotificationService.create` cho immutable owner, type `CLASS_APPROVED`, reference `CLASS/{id}`.

Notification lỗi bị nuốt ở dòng 101-107 để không rollback transition. Controller set flash rồi redirect `GET /leader/approvals`. Từ commit này, class biến khỏi queue DRAFT và xuất hiện trong catalog ACTIVE của student.

## 5. Leader nhập lý do và bấm “Từ chối”

Form `approvals.html:44-50` gửi field `note`, UI giới hạn 500 ký tự:

```text
POST /leader/approvals/{classId}/reject
Content-Type: application/x-www-form-urlencoded

note=<optional>
```

`LeaderController.rejectClass`, `LeaderController.java:71-83`, gọi `LeaderClassApprovalService.reject`. Service `LeaderClassApprovalService.java:71-79` khóa/kiểm scope giống approve, gọi `ClassEntity.reject`, save rồi báo owner type `CLASS_REJECTED`; body notification nối rejection note nếu có.

State thực tế đáng chú ý: `ClassEntity.reject`, `src/main/java/com/ksh/entities/ClassEntity.java:161-167`, giữ class ở `DRAFT`, chỉ set review metadata/note. `load` lại query toàn bộ DRAFT và `approvals.html` không render note; vì vậy class vừa reject vẫn xuất hiện lại trong queue và có thể được review tiếp. Không có trạng thái `REJECTED` riêng và không có nút “gửi lại duyệt”.

## 6. Leader phân công đồng giảng

Mở màn:

```text
GET /leader/assign
```

`LeaderController.assign`, `LeaderController.java:96-105`, gọi `LeaderLecturerAssignmentService.load`. Service `src/main/java/com/ksh/features/leader/service/LeaderLecturerAssignmentService.java:50-83` lấy mọi class của các subject, owner name, co-lecturer IDs/names và danh sách user active role `LECTURER` hoặc `LEADER` trên **toàn hệ thống** (`173-180`). Template `leader/assign.html:42-83` render checkbox `lecturerIds` trừ owner hiện tại.

Leader chọn nhiều checkbox rồi bấm **Lưu phân công**:

```text
POST /leader/assign/{classId}
Content-Type: application/x-www-form-urlencoded

lecturerIds=12&lecturerIds=19
```

`LeaderController.reassign`, `LeaderController.java:107-129`, hỗ trợ cả list mới `lecturerIds` và field legacy đơn `lecturerId`; list rỗng có nghĩa gỡ toàn bộ đồng giảng. Nó gọi `LeaderLecturerAssignmentService.updateCoLecturers`.

Service tại `LeaderLecturerAssignmentService.java:100-140`:

1. yêu cầu class thuộc ít nhất một subject của Leader;
2. validate từng selected user tồn tại, active, không deleted, role `LECTURER/LEADER`, không phải owner;
3. lock logic ở mức transaction nhưng dùng `findById`, không dùng pessimistic class lock;
4. delete assignment hiện tại không còn được chọn;
5. insert `ClassCoLecturer(classId,lecturerId,assignedByLeaderId)` cho lựa chọn mới;
6. không đổi `classes.lecturer_id` và `created_by`.

Controller redirect `/leader/assign` với flash. Không có notification/email cho người vừa được thêm/gỡ. Co-lecturer mới được `ClassesService` coi là có quyền truy cập lớp và các bề mặt policy tương ứng, nhưng không trở thành primary owner nên không sửa/xóa lớp hoặc duyệt join request.

## 7. Leader mở báo cáo bộ môn

```text
GET /leader/report
```

`LeaderController.report`, `LeaderController.java:131-139`, gọi `LeaderReportService.load` rồi render `templates/leader/report.html`.

`LeaderReportService.load`, `src/main/java/com/ksh/features/leader/service/LeaderReportService.java:36-64`, lấy mọi class theo subject và với **mỗi class** chạy ba query:

- count enrollment `ACTIVE` (`66-70`);
- `AVG(test_attempts.score)` khi attempt `SUBMITTED` hoặc `TIMED_OUT`, test chưa deleted (`73-80`);
- `AVG(assignment_feedback.score)` qua submission/assignment chưa deleted (`83-90`).

Template `leader/report.html:33-59` hiển thị bảng; null average thành `—`. Đây là read-only snapshot khi page load, không export và không gọi AI.

## Tóm tắt

`static/js/leader-department.js` hiện chỉ là IIFE rỗng (`:1–8`); các màn `templates/leader/dashboard.html`, `approvals.html`, `assign.html`, `report.html` đều lấy dữ liệu từ GET/controller/service nêu trên. Checkbox phân công và note từ chối chỉ nằm trong DOM đến khi form POST; không có Leader draft/session state ở JavaScript.

```text
[Leader /approvals]
  -> GET queue DRAFT trong subject scope
  -> POST approve/reject
  -> LeaderController
  -> LeaderClassApprovalService + row lock
  -> classes state/review metadata
  -> NotificationService báo owner
  -> redirect queue

[Leader /assign]
  -> chọn lecturerIds
  -> POST /leader/assign/{classId}
  -> replace class_co_lecturers
  -> redirect và quyền đồng giảng có hiệu lực ở lần request sau
```
