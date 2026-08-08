# Workflows: học sinh ghi tiến độ và giảng viên theo dõi cohort

Progress là logic database thuần, không gọi AI, không gửi notification/mail. Có hai nguồn ghi: tự động khi học sinh mở lesson hợp lệ và nút toggle hoàn thành; giảng viên chỉ đọc aggregate/drill-down.

## 1. Tự ghi “Đang học” khi học sinh mở lesson

### Thao tác người dùng

Học sinh click lesson ở `student/class-lessons.html:291-335`:

```text
GET /my/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
```

### Chuỗi backend

`StudentLessonsController.view`, dòng 75-114:

1. `StudentLessonsService.listClassLessons` kiểm class `ACTIVE`, enrollment `ACTIVE`, chỉ trả PUBLISHED lesson.
2. Lesson phải thuộc active section (`StudentLessonsController.java:96-100`).
3. `StudentLessonDetailService.getLessonDetail` chạy lại live class/cross-class/PUBLISHED/access gates (`102-104`).
4. Chỉ khi detail hợp lệ, controller gọi `recordOpenedQuietly` (`105-107`).
5. `recordOpenedQuietly` chỉ chạy cho role STUDENT và nuốt/log lỗi để không làm hỏng màn bài (`141-151`).

`LearningProgressService.recordOpened`, dòng 50-61:

1. `runGates` buộc enrollment `ACTIVE`, rồi `LessonAccessResolver.resolveInClass` buộc lesson thuộc đúng class và PUBLISHED (`96-104`).
2. Query `LearningProgressRepository.findByUserIdAndLessonId` (`LearningProgressRepository.java:21-25`). Row có sẵn thì return, kể cả đang COMPLETED.
3. Chưa có thì insert `LearningProgress(userId,lessonId)` trạng thái `IN_PROGRESS`, percent 0, `startedAt=now` (`LearningProgress.java:71-84`).
4. Unique `(user_id,lesson_id)` đảm bảo tối đa một row (`LearningProgress.java:15-20`); concurrent first-open vi phạm unique bị nuốt vì một request kia đã đạt end-state (`LearningProgressService.java:56-60`).

Đây là side effect của GET nhưng idempotent. Không redirect/flash riêng; UI lesson vẫn render dù ghi progress thất bại.

## 2. Học sinh đánh dấu hoàn thành hoặc bỏ hoàn thành

### Thao tác và payload

Ở từng lesson card, form toggle nằm tại `student/class-lessons.html:336-350`. Button có vòng tròn/check theo `lesson.completed` (`344-349`) và gửi:

```text
POST /my/classes/{classId}/lessons/{lessonId}/progress/toggle
Content-Type: application/x-www-form-urlencoded

section={sectionId}
```

`section` là hidden input dòng 343 để redirect về đúng chapter/lesson; actor id không nằm trong form. Source không viết `_csrf` thủ công tại đây, nhưng form dùng `th:action` và Spring Security/Thymeleaf request-data processor chèn CSRF hidden vào POST khi render.

### Controller → khóa → state

`LearningProgressController.toggle`, dòng 45-55:

1. Controller chỉ cho STUDENT (`LearningProgressController.java:28-30`).
2. Gọi `LearningProgressService.toggleCompletion(classId,lessonId,userId)` (`51`).
3. Set flash hoàn thành/chưa hoàn thành rồi redirect canonical lesson URL (`52-54`).

Service transaction `LearningProgressService.java:72-89`:

1. `runToggleGates` dùng `EnrollmentRepository.findByUserIdAndClassIdForUpdate` để khóa pessimistic enrollment ổn định, buộc ACTIVE, sau đó resolve live class/cross-class/PUBLISHED (`111-116`; repository method `EnrollmentRepository.java:101-102`).
2. Tìm progress; chưa có thì tạo row IN_PROGRESS (`75-77`).
3. Nếu đang `COMPLETED`: gọi `revertToInProgress`, chuyển `COMPLETED → IN_PROGRESS`, clear `completedAt`, percent 0 (`80-82`; entity `LearningProgress.java:122-130`).
4. Nếu chưa completed: `markCompleted`, chuyển `IN_PROGRESS/row mới → COMPLETED`, set `completedAt`, percent 100 và bảo đảm `startedAt` (`83-85`; entity `113-120`).
5. `saveAndFlush`, trả boolean trạng thái mới (`87-88`).

Khóa enrollment serialize hai toggle đồng thời của cùng student/class trước khi đọc progress, nên hai click concurrent có cùng parity như hai thao tác tuần tự.

### UI nhận kết quả

Redirect:

```text
GET /my/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
```

Request GET mới query completed ids, nên check, `completed/total` chapter và progress tổng đều refresh. Flash được toast hiển thị. Không notification cho giảng viên; dashboard giảng viên phản ánh DB khi tải lại.

### State thực tế

```text
không có row --open--> IN_PROGRESS
không có row --toggle--> COMPLETED
IN_PROGRESS --toggle--> COMPLETED
COMPLETED --toggle--> IN_PROGRESS
COMPLETED --open--> COMPLETED (không đổi)
```

`NOT_STARTED` không được persist; absence của row đại diện chưa bắt đầu (`LearningProgress.java:22-26`).

## 3. Giảng viên mở dashboard tiến độ của một lớp

### Thao tác và request

Từ class sidebar hoặc link **Xem tiến độ** ở teaching dashboard (`lecturer/dashboard.html:114-119`):

```text
GET /lecturer/classes/{classId}/progress?status={all|completed|in-progress|not-started}&q={text}&page={0-based}&size={n}
```

Filter links nằm tại `classes/detail-progress.html:45-58`; form tìm theo tên/email tại dòng 59-66; pager dòng 123-125.

### Controller → aggregate

`ClassProgressController.progress`, dòng 58-80:

1. Chỉ role LECTURER/LEADER/ADMIN (`ClassProgressController.java:43-45`).
2. Bind filter/query/page/size.
3. Gọi `LecturerProgressService.getProgressPage` (`67-68`).
4. Populate class shell, summary, page/filter và render `classes/detail-progress.html` (`70-79`).

Service read-only `LecturerProgressService.java:70-92`:

1. `ClassesService.getViewable` kiểm class tồn tại và actor đúng scope (`73`).
2. Query toàn bộ PUBLISHED lesson ids của class (`75`).
3. Một grouped query `LearningProgressRepository.aggregateByLessonIds` tính `completedCount` và `MAX(updatedAt)` theo user (`76-77`; repository `49-55`). Không query mỗi student.
4. Query mọi enrollment `ACTIVE` (`79-80`). Removed/completed enrollment không nằm cohort.
5. Map mỗi student: completed, total, percent, last activity và bucket (`82-86`, mapper `105-120`). Opened nhưng 0 completed thuộc `in-progress`, không phải `not-started` (`113-115`).
6. Summary total/average/not-started/completed được tính trên **toàn cohort trước filter/page** (`87`, `122-134`).
7. Sau đó filter status + substring name/email và paginate in-memory (`89-91`, `136-168`). Size clamp; page âm về 0; offset cực lớn về page rỗng.

### UI kết quả

- 4 KPI cards ở `detail-progress.html:22-40`.
- Table student, percent, lesson count, joined/last activity ở dòng 69-111.
- Empty state phân biệt class chưa có student và filter không match (`113-121`).
- Mọi phép tính chỉ dựa trên PUBLISHED lessons hiện tại. Lesson bị unpublish/xóa không còn trong denominator khi reload.

## 4. Giảng viên click một học sinh để xem từng bài

### Thao tác UI → fetch

Mỗi `<tr>` mang `data-student-id/name` tại `classes/detail-progress.html:80-83`. `class-progress.js:168-176` bắt click row và gọi `loadStudent`.

Fetch ở `class-progress.js:109-165`:

```text
GET /lecturer/classes/{classId}/progress/{studentId}/lessons
Accept: application/json
```

JS dùng `AbortController`; click student khác hoặc đóng panel sẽ abort request cũ (`38-53,109-120`). CSRF meta được gửi nếu có dù method là GET (`126-138`).

### Controller → service → JSON

`ClassProgressApiController.breakdown`, dòng 41-55:

1. Chỉ LECTURER/LEADER/ADMIN (`31-33`).
2. Gọi `LecturerProgressBreakdownService.getStudentLessonBreakdown` (`47-49`).
3. Thành công `200` JSON `StudentBreakdown`; access denied dùng envelope 403, class/member missing dùng envelope 404 (`50-54`).

Service read-only `LecturerProgressBreakdownService.java:63-99`:

1. `ClassesService.getViewable` kiểm actor/class (`66`).
2. Target phải có enrollment `ACTIVE` đúng class (`67-69`); sửa `studentId` sang user ngoài lớp không đọc được dữ liệu.
3. Query sections và PUBLISHED lessons theo order, gom ids (`71-80`).
4. Một query `findByUserIdAndLessonIdIn` lấy progress của target (`82-84`, helper `103-109`).
5. Lesson không có row map `NOT_STARTED`; section không có published lesson bị bỏ (`86-98`).

JSON có dạng logic:

```json
{
  "sections": [
    {
      "title": "Chương 1",
      "lessons": [
        {"title": "Bài 1", "status": "COMPLETED"},
        {"title": "Bài 2", "status": "NOT_STARTED"}
      ]
    }
  ]
}
```

### JS render kết quả

`class-progress.js:73-107` tạo node bằng `textContent`, không `innerHTML`, nên title chứa markup không gây XSS. Mapping badge: `COMPLETED/IN_PROGRESS/NOT_STARTED` ở dòng 13-18 và 97-101. Response content-type sai hoặc HTTP lỗi hiện toast + message trong panel (`139-162`).

## Security và edge cases

- Student progress write luôn dùng user id principal; không nhận target user từ form.
- Mọi write yêu cầu enrollment ACTIVE và lesson PUBLISHED đúng class, failure collapse no-leak 404 ở service.
- Toggle có stable-row lock; auto-open dùng unique-key race handling.
- Giảng viên chỉ đọc class trong scope và breakdown chỉ target ACTIVE member.
- Không có endpoint sửa percent tùy ý, reset toàn bộ cohort, export, AI phân tích, notification hay mail trong module hiện tại.
