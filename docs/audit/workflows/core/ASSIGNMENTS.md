# Workflows: bài tập từ giảng viên đến học sinh và chấm điểm

Tài liệu này trace toàn bộ bề mặt Assignment đang reachable từ UI. Module này không gọi AI và chưa có upload file; `attachment_url`, rubric và `is_ai_generated` chỉ là cột dự phòng (`Assignment.java:43-58`, `AssignmentFeedback.java:47-53`).

## 1. Giảng viên mở danh sách bài tập của lớp

### Thao tác người dùng

Từ sidebar lớp, giảng viên mở tab **Bài tập**, browser gửi:

```text
GET /lecturer/classes/{classId}/assignments
```

Trang kết quả là `assignments/lecturer-list.html`. Nút **“+ Tạo bài tập”** nằm ở dòng 22-23; mỗi dòng có các thao tác **Bài nộp** (62-63), **Sửa** (66-68), **Xuất bản** (71-79) hoặc **Đóng** (82-90), tùy trạng thái.

### Controller và service

`LecturerAssignmentController.list`, dòng 70-84:

1. `loadClass` gọi `ClassesService.getViewable` ở dòng 57-64 để dựng sidebar và trả `404/403` đúng scope.
2. Gọi `LecturerAssignmentService.listForLecturer` ở dòng 76-77.
3. Service chạy transaction read-only, kiểm lại `AssignmentAccessSupport.requireEditableClass`, lấy toàn bộ assignment chưa xóa rồi đếm submission từng assignment (`LecturerAssignmentService.java:66-73`).
4. Query danh sách ở `AssignmentRepository.java:29-30`; đếm từng đề qua `AssignmentSubmissionRepository.countByAssignmentId` dòng 51-52.
5. Render `assignments/lecturer-list` ở controller dòng 81-83.

Lưu ý hiệu năng: số bài nộp được đếm theo từng assignment, nên trang này có dạng query N+1 (`LecturerAssignmentService.java:69-72`).

## 2. Giảng viên tạo bài tập nháp

### Thao tác người dùng và payload

Người dùng bấm **“+ Tạo bài tập”** (`lecturer-list.html:22-23`) → `GET /lecturer/classes/{classId}/assignments/new` → `LecturerAssignmentController.newForm` dòng 89-104. Controller tạo `AssignmentForm.empty()`, populate shell lớp và render `assignments/lecturer-form.html`.

Form tạo nằm tại `lecturer-form.html:27-36`, có CSRF dòng 30 và submit bằng browser thường:

| Field | Nguồn UI | Ý nghĩa |
|---|---|---|
| `title` | `assignments/fragments.html:8-10` | tên, `required`, tối đa 300 ký tự |
| `description` | dòng 14-16 | yêu cầu bài làm |
| `maxScore` | dòng 21-23 | điểm tối đa, bước 0.01 |
| `dueDate` | dòng 26-28 | chuỗi HTML `datetime-local` |
| `allowLateSubmission` | dòng 32-35 | checkbox cho nộp muộn |
| CSRF | `lecturer-form.html:30` | chống request giả mạo |

Người dùng bấm **“Tạo bài tập”** ở dòng 34:

```text
POST /lecturer/classes/{classId}/assignments
Content-Type: application/x-www-form-urlencoded
```

### Controller → service → DB

`LecturerAssignmentController.create`, dòng 107-130:

1. Actor lấy từ `KshUserDetails`; client không gửi `createdBy`.
2. `parseDateTime` dòng 295-302 parse hạn nộp. Giá trị sai định dạng bị đổi thành `null`, không báo lỗi.
3. Tạo `AssignmentForm` rồi gọi `LecturerAssignmentService.create` dòng 117-120.
4. Service `@Transactional` (`LecturerAssignmentService.java:81-94`) kiểm scope lớp (`AssignmentAccessSupport.java:51-55`), title không trống và `maxScore >= 0` (`AssignmentAccessSupport.java:87-93`).
5. Tạo `Assignment`: `classId`, `createdBy`, timestamps; trạng thái ban đầu `DRAFT`; `maxScore` trống mặc định 100, description trống thành chuỗi rỗng (`LecturerAssignmentService.java:86-93`, `AssignmentAccessSupport.java:78-84`).
6. `AssignmentRepository.save` insert bảng `assignments`.

### UI nhận kết quả và lỗi

- Thành công: flash `MSG_ASSIGNMENT_CREATED`, redirect `GET /lecturer/classes/{classId}/assignments` (`controller:120,129`).
- Class/actor sai scope: 404, không lộ assignment/class (`controller:121-122`).
- Input sai: flash error + flash lại form, redirect `/new` (`controller:123-127`).
- Không notification/mail; DRAFT chưa hiển thị cho học sinh.

## 3. Giảng viên sửa bài tập

### Thao tác người dùng

UI chỉ hiện **Sửa** khi `status == DRAFT` (`lecturer-list.html:65-68`). Click gửi:

```text
GET /lecturer/classes/{classId}/assignments/{assignmentId}/edit
```

`LecturerAssignmentController.editForm`, dòng 135-155, gọi `LecturerAssignmentService.getFormForEdit` (`service:245-250`) rồi render cùng form. Nút **“Lưu thay đổi”** ở `lecturer-form.html:38-46` gửi:

```text
POST /lecturer/classes/{classId}/assignments/{assignmentId}/edit
```

Payload giống workflow tạo và có CSRF ở dòng 41.

### Backend xử lý

`LecturerAssignmentController.update`, dòng 158-180, parse form và gọi `LecturerAssignmentService.update` dòng 171. Service transaction ở `LecturerAssignmentService.java:101-109`:

1. Kiểm actor được sửa lớp.
2. Validate title/maxScore.
3. Tìm assignment theo cả `assignmentId + classId + is_deleted=false` (`AssignmentAccessSupport.java:65-68`, query `AssignmentRepository.java:40-41`).
4. Ghi đè nội dung/hạn/điểm/cờ nộp muộn, cập nhật `updatedAt`, save.

Thành công flash rồi redirect list (`controller:172,179`); validation lỗi redirect lại edit (`176-177`); not-found trả 404.

### Phát hiện audit

Comment service nói chỉ DRAFT hoặc PUBLISHED được sửa (`LecturerAssignmentService.java:97`), nhưng code `update` không kiểm trạng thái. UI giấu nút với PUBLISHED/CLOSED, song request POST trực tiếp vẫn sửa được cả `PUBLISHED` và `CLOSED` nếu actor quản lý lớp. Đây là chênh lệch giữa UI/comment và state machine thực tế.

## 4. Giảng viên xuất bản bài tập và hệ thống báo học sinh

### Thao tác người dùng

Ở assignment `DRAFT`, người dùng bấm **“Xuất bản”** (`lecturer-list.html:70-79`). Inline `confirm` tại dòng 76 có thể hủy submit. Nếu đồng ý:

```text
POST /lecturer/classes/{classId}/assignments/{assignmentId}/publish
```

Form có CSRF ở dòng 74.

### Controller → state transition → notification

`LecturerAssignmentController.publish`, dòng 185-199, gọi `LecturerAssignmentService.publish` dòng 191.

Trong transaction `LecturerAssignmentService.java:117-128`:

1. `requireEditableClass` kiểm scope.
2. `requireAssignment` buộc assignment thuộc class và chưa soft-delete.
3. Chỉ chấp nhận `DRAFT`; trạng thái khác ném `MSG_ASSIGNMENT_INVALID_TRANSITION` (`121-123`).
4. Chuyển `DRAFT → PUBLISHED`, cập nhật thời gian và save (`124-126`).
5. `fanOutAssignmentPublished` dòng 257-277 lấy mọi enrollment `ACTIVE` của lớp (`259-260`).
6. Với từng học sinh, tạo notification type `ASSIGNMENT_PUBLISHED`, reference type `ASSIGNMENT`, reference id là assignment id (`263-270`).

Notification là best-effort: lỗi từng người và lỗi toàn fan-out đều bị nuốt (`271-276`), vì vậy publish vẫn commit. Không gửi email/mail outbox.

### UI nhận kết quả

Controller flash thành công hoặc lỗi transition, rồi redirect list (`controller:192-198`). Request GET mới hiển thị badge **Đã xuất bản**; học sinh bắt đầu thấy bài trong danh sách vì repository student lọc `status <> DRAFT` (`AssignmentRepository.java:59-60`).

## 5. Giảng viên đóng bài tập

### Thao tác và backend

Với bài `PUBLISHED`, nút **“Đóng”** ở `lecturer-list.html:81-90` có confirm dòng 87 và submit CSRF-protected:

```text
POST /lecturer/classes/{classId}/assignments/{assignmentId}/close
```

`LecturerAssignmentController.close`, dòng 202-216 → `LecturerAssignmentService.close` dòng 208. Transaction service ở `LecturerAssignmentService.java:135-145` chỉ cho `PUBLISHED → CLOSED`; trạng thái khác báo transition invalid.

Controller flash rồi redirect list. Không notification/mail. Student vẫn thấy CLOSED trong list/detail nhưng form submit bị ẩn và backend cũng từ chối vì submit chỉ chấp nhận PUBLISHED.

## 6. Học sinh mở danh sách và chi tiết bài tập

### Danh sách

Sidebar học sinh mở:

```text
GET /classes/{classId}/assignments
```

`StudentAssignmentController.list`, dòng 65-78:

1. `@PreAuthorize(Roles.PREAUTH_STUDENT)` ở dòng 38 chặn role khác.
2. Load class để dựng sidebar.
3. `StudentAssignmentService.listPublishedForStudent` (`StudentAssignmentService.java:52-64`) buộc enrollment `ACTIVE` qua `AssignmentAccessSupport.java:58-62`.
4. Query chỉ assignment không xóa và khác `DRAFT` (`AssignmentRepository.java:52-60`).
5. Với mỗi assignment, query submission riêng của actor rồi map trạng thái/nộp muộn.
6. Render `assignments/student-list.html`; nút **Xem** nằm ở dòng 63-65.

Danh sách này cũng có query N+1 cho submission (`StudentAssignmentService.java:55-63`).

### Chi tiết

Click **Xem**:

```text
GET /classes/{classId}/assignments/{assignmentId}
```

`StudentAssignmentController.detail`, dòng 83-97 → `StudentAssignmentService.getForStudent` dòng 70-89. Service kiểm enrollment `ACTIVE`, buộc assignment đúng class và không phải DRAFT, rồi tải đúng submission `(assignmentId,userId)` và feedback của submission. Render `assignments/student-detail.html`.

UI hiện trạng thái/hạn/điểm (`student-detail.html:30-43`), nội dung (`45-48`), trạng thái bài nộp (`50-69`) và chỉ hiện form khi assignment `PUBLISHED` + submission chưa `GRADED` (`71-97`).

## 7. Học sinh nộp hoặc nộp lại bài

### Thao tác người dùng

Học sinh nhập `content` tại `student-detail.html:86-90` và bấm **“Nộp bài”** dòng 94. Form dòng 82-96 có CSRF và gửi:

```text
POST /classes/{classId}/assignments/{assignmentId}/submit
Content-Type: application/x-www-form-urlencoded
```

### Controller → khóa → upsert

`StudentAssignmentController.submit`, dòng 102-119, luôn lấy user id từ principal và gọi `StudentAssignmentService.submit` dòng 109.

Service transaction `StudentAssignmentService.java:106-143`:

1. Kiểm enrollment `ACTIVE` (`108`).
2. Khóa pessimistic parent assignment bằng `findByIdAndClassIdNotDeletedForUpdate` (`111-112`; repository `AssignmentRepository.java:43-50`). Khóa row ổn định này serialize cả lần nộp đầu khi chưa có submission.
3. Buộc assignment đang `PUBLISHED` (`113-115`).
4. Tìm và khóa submission `(assignment,user)` bằng `findByAssignmentIdAndUserIdForUpdate` (`118-121`; repository `AssignmentSubmissionRepository.java:31-35`).
5. Submission `GRADED` thì cấm nộp lại.
6. Nếu quá hạn: `allowLate=false` thì từ chối; `true` thì set `is_late=true` (`124-131`).
7. Upsert cùng một row: content, `SUBMITTED`, timestamps; không tạo history phiên bản (`133-142`). DB unique `(assignment_id,user_id)` là lớp bảo vệ cuối (`AssignmentSubmission.java:15-20`).

Lock order là assignment trước, submission sau; chấm điểm dùng cùng thứ tự để tránh submit-vs-grade ghi đè nhau.

### Kết quả và edge cases

- Thành công: flash `MSG_SUBMIT_SUCCESS`, redirect lại detail (`controller:110,118`).
- CLOSED/not-published, quá hạn không cho phép hoặc đã GRADED: flash lỗi, vẫn redirect detail (`113-118`).
- Không notification giảng viên và không email.
- Backend không validate `content` trống; UI textarea cũng không `required`. Một submission content null/trống vẫn có thể thành `SUBMITTED`.
- Mỗi lần nộp lại ghi đè content và `submittedAt`; không có audit/version history.

## 8. Giảng viên xem bài nộp và mở màn chấm

Nút **Bài nộp** (`lecturer-list.html:61-63`) gửi:

```text
GET /lecturer/classes/{classId}/assignments/{assignmentId}/submissions
```

`LecturerAssignmentController.submissions`, dòng 221-237 → `LecturerAssignmentService.listSubmissions` (`service:148-160`). Service kiểm scope, kiểm assignment, tải tất cả submission bằng query `AssignmentSubmissionRepository.java:48-49`, rồi với mỗi row resolve user name/email và feedback score. Render `assignments/lecturer-submissions.html`.

Mỗi dòng có **Chấm điểm** tại `lecturer-submissions.html:59-62`:

```text
GET /lecturer/classes/{classId}/assignments/{assignmentId}/submissions/{submissionId}/grade
```

`LecturerAssignmentController.gradeForm`, dòng 242-263 → `LecturerAssignmentService.getSubmissionDetail` (`service:164-183`). Read-only GET cố ý không lấy pessimistic lock. Submission phải có `assignmentId` đúng URL. Trang `assignments/lecturer-grade.html` hiển thị nội dung ở dòng 25-51 và form chấm ở dòng 54-84.

## 9. Giảng viên lưu điểm và hệ thống báo học sinh

### Thao tác người dùng

Form chấm nhận:

| Field | UI |
|---|---|
| `score` | `lecturer-grade.html:60-69`, required, min 0, max từ assignment |
| `feedback` | dòng 72-76 |
| CSRF | dòng 56 |

Click **“Lưu điểm”** dòng 82:

```text
POST /lecturer/classes/{classId}/assignments/{assignmentId}/submissions/{submissionId}/grade
```

### Controller → transaction/locks/state

`LecturerAssignmentController.grade`, dòng 266-286 → `LecturerAssignmentService.grade` dòng 276.

Service transaction `LecturerAssignmentService.java:192-242`:

1. Kiểm quyền sửa lớp.
2. Khóa assignment theo class (`198-199`), rồi khóa submission id và kiểm nó thuộc assignment (`200-202`; repository lock `AssignmentSubmissionRepository.java:37-39`).
3. Backend xác minh `score != null` và nằm trong `[0,maxScore]` (`204-209`), không tin `min/max` HTML.
4. Upsert đúng một `AssignmentFeedback` theo `submission_id`: grader, score, feedback, timestamps (`211-222`). DB/entity unique tại `AssignmentFeedback.java:34-36`.
5. Chuyển submission sang `GRADED` (`224-227`).
6. Best-effort tạo notification `ASSIGNMENT_GRADED`, reference assignment id, body có title và điểm (`229-241`). Notification lỗi bị nuốt, điểm vẫn commit; không email.

### UI học sinh nhận lại

Controller flash thành công, redirect danh sách bài nộp (`controller:277,285`). Học sinh thấy trạng thái **Đã chấm**; nút **“Xem kết quả & nhận xét”** ở `student-detail.html:65-68` gửi:

```text
GET /classes/{classId}/assignments/{assignmentId}/feedback
```

`StudentAssignmentController.feedback`, dòng 124-138, tái dùng `StudentAssignmentService.getForStudent`, render `assignments/student-feedback.html`. Template hiển thị score/max (`41-45`), feedback (`49-52`) và bài đã nộp (`54-57`). Nếu chưa GRADED, route vẫn mở nhưng hiển thị **Chưa có kết quả** (`34-38`).

## State machine thực tế

```text
Assignment: DRAFT --publish--> PUBLISHED --close--> CLOSED
Submission: (không có row) --submit--> SUBMITTED --grade--> GRADED
                         SUBMITTED --re-submit--> SUBMITTED (ghi đè)
```

- State transition publish/close/submit/grade được kiểm ở service.
- Update assignment không enforce state dù comment mô tả có giới hạn.
- Không có endpoint delete assignment, save draft submission, reopen assignment, ungrade, attachment, rubric hay AI grading trong module hiện tại.

## Browser state và query boundary của các màn Assignment

`static/js/assignments.js:18–33` chỉ gắn `window.confirm` vào form có `data-confirm` cho publish/close. Nó không load assignment/submission, không autosave bài làm và không giữ grade; hủy dialog chỉ chặn POST, xác nhận thì browser submit form thường.

Vì vậy dữ liệu của từng template đều đến từ GET server:

| Template | Initial GET → read path |
|---|---|
| `templates/assignments/lecturer-list.html` | `LecturerAssignmentController.list` → `LecturerAssignmentService.listForLecturer` → assignment rows của class sau scope check |
| `templates/assignments/lecturer-form.html` | create: form rỗng; edit: `getFormForEdit` load đúng assignment thuộc class |
| `templates/assignments/lecturer-submissions.html` | `listSubmissions` → toàn bộ submission của assignment + user/feedback lookup |
| `templates/assignments/lecturer-grade.html` | `getSubmissionDetail` → submission + assignment + learner + feedback hiện tại |
| `templates/assignments/student-list.html` | `StudentAssignmentService.listPublishedForStudent` sau ACTIVE-enrollment, chỉ các assignment learner được xem |
| `templates/assignments/student-detail.html` | `getForStudent` load assignment và submission/feedback của chính principal nếu có |
| `templates/assignments/student-feedback.html` | cùng authoritative read `getForStudent`; template chỉ đổi cách trình bày |

Nội dung học sinh gõ ở detail chỉ ở `<textarea>` cho tới POST submit; không có request nền. Reload/mất tab trước POST làm mất nội dung và không tạo `assignment_submissions` rác.
