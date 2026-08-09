# Audit schema lớp học và activity log

Ngày audit: 2026-07-30  
Phạm vi: luồng lớp học, bài học, ngân hàng câu hỏi và activity log.  
Ngoài phạm vi: toàn bộ `practice_*` và các migration phục vụ practice; không được xoá/sửa trong đợt này.

## Kết luận ngắn

Ứng dụng hiện chỉ còn một tổ chức lớp học trong database: bảng `classes` là bảng dùng chung, còn `/lecturer/classes/**` và `/my/classes/**` là hai surface UI cho hai vai trò khác nhau. Không có bằng chứng về hai bảng lớp học song song. Vì vậy không nên tạo thêm bảng hoặc gộp hai bảng; cần dùng cùng một `classes` + `enrollments`, rồi chặn quyền ở controller/service.

`courses` đã bị tháo khỏi `classes` từ migration `V7`; `sections` đã được chuyển từ `course_id` sang `class_id` ở `V13`. Tuy nhiên bảng `courses`, `course_categories`, `categories` và `activity_courses` vẫn tồn tại từ schema/migration cũ. Đây là nhóm ứng viên loại bỏ cao nhất nếu sản phẩm xác nhận không còn roadmap course/catalog.

`departments` không phải bảng vô dụng: đang là scope của HEAD, được tham chiếu bởi `users.department_id`, `classes.department_id`, question bank và các permission/audit service. Có thể đơn giản hoá thành một department mặc định “Korean”, nhưng chưa thể xoá an toàn ở đợt audit này.

`question_bank_categories` cũng đang được dùng thật bởi question bank (`question_bank_items.category_id`) và các controller/service/test. Không nên xoá; nếu mục tiêu chỉ là một lĩnh vực, nên chuyển thành category dùng chung hoặc seed một bộ category cố định, không xoá bảng.

## Bằng chứng chính

| Thành phần | Trạng thái | Bằng chứng |
|---|---|---|
| `classes` | Đang dùng, bảng hợp nhất | `V1`, `V7`, entity/repository/controller dưới `features/classes` |
| `enrollments` | Đang dùng | `StudentClassesService`, `JoinClassService`, `/my/classes` |
| `/lecturer/classes/**` | Đang dùng | lecturer controllers/templates |
| `/my/classes/**` | Đang dùng cho học sinh | student lessons/tests/messages/progress controllers |
| lecturer truy cập `/my/classes` | **Lỗi phân quyền** | `StudentClassesController` có `@PreAuthorize("isAuthenticated()")`; tương tự `StudentLessonsController`, `StudentClassTestsController`, `StudentClassMessagesController` |
| `courses` | Legacy, không còn FK từ `classes`/`sections` | `V7` drop `classes.course_id`, `V13` drop `sections.course_id`; chỉ còn migration/schema cũ và admin dashboard count |
| `course_categories`, `categories` | Legacy/độc lập | khai báo ở `V1`; `Category`/admin category service vẫn tồn tại, cần kiểm kê route và dữ liệu trước khi drop |
| `departments` | Đang dùng | `users.department_id`, `classes.department_id`, `V40`, `V41`, `V46`, HEAD services |
| `question_bank_categories` | Đang dùng | `V46`, entity/service/controller/repository và test question bank |
| `activity_lessons` | Đang dùng | entity, writer, page loader, lesson tests |
| `activity_sections` | Đang dùng | entity, writer, page loader, section tests |
| `activity_classes` | Đang dùng | entity, writer, join/invite audit, integration tests |
| `activity_enrollments` | Có FK và được xoá trong test cleanup; cần kiểm tra production writer | tạo ở `V3`, cascade từ `enrollments`; chưa thấy writer rõ ràng ngoài join audit |
| `activity_tests` | Đang dùng | `TestActivity`, `TestActivityWriter`, `ExamMonitorService` |
| `activity_users` | Đang dùng | admin users audit writer/read service |
| `activity_departments` | Đang dùng | `DepartmentActivity`, repository, query/audit service |
| `activity_assignments`, `activity_submissions`, `activity_comments`, `activity_content_versions`, `activity_flashcard_decks` | Chưa thấy consumer tương ứng trong mã hiện tại | chỉ thấy định nghĩa ở `V3`; cần xác nhận dữ liệu/ngoại lệ trước khi xoá |

## Phân tích `/my/classes`

Đây không phải là “tổ chức lớp học thứ hai”. Đây là namespace student-facing trên cùng một aggregate:

```text
classes
  └── enrollments ── users
```

Lecturer quản lý lớp qua `/lecturer/classes/**`; student học trong lớp qua `/my/classes/{id}/...`. Cách tách URL là hợp lý. Cách kiểm soát hiện tại không hợp lý vì chỉ ẩn header. Cần:

1. Đổi `StudentClassesController` thành `@PreAuthorize("hasRole('STUDENT')")`.
2. Áp dụng cùng policy cho `StudentLessonsController`, `StudentClassTestsController`, `StudentClassMessagesController`, `LearningProgressController` và `InviteLinkController` (riêng endpoint invite cần quyết định có cho lecturer tham gia hay không; khuyến nghị từ chối).
3. Thêm integration tests: lecturer nhận `403` hoặc redirect an toàn cho mọi `GET/POST /my/**`; student vẫn truy cập được.
4. Xoá comment hiện tại nói lecturer được phép join để tránh tái diễn.

## Đề xuất giảm schema theo pha

### Pha 0 — an toàn, không đổi dữ liệu

- Chặn quyền `/my/**` như trên.
- Bổ sung inventory runtime: số bản ghi, kích thước bảng, số FK và số truy vấn theo bảng.
- Không chạy `DROP TABLE` trong cùng release với thay đổi quyền.

### Pha 1 — loại legacy course

Chỉ thực hiện sau khi `rg`/runtime xác nhận không còn route/service/repository nghiệp vụ dùng course:

- deprecate rồi xoá `course_categories`;
- xoá `activity_courses`;
- xoá `courses`;
- xoá `categories` nếu category chỉ phục vụ course và không có dữ liệu/route cần giữ;
- xoá các entity/repository/service/template/admin dashboard metric tương ứng.

Thứ tự migration đề xuất: archive/export → kiểm tra FK → drop activity child → drop join table → drop courses → drop categories. Không sửa bất kỳ bảng `practice_*`.

### Pha 2 — đơn giản hoá department

Không xoá `departments` ngay. Nếu hệ thống chỉ có Korean:

- giữ bảng và tạo một dòng chuẩn `KOREAN`;
- chuyển `users.department_id`, `classes.department_id`, `question_bank_* .department_id` về dòng này;
- ẩn CRUD department khỏi UI, giữ API read-only cho tương thích;
- chỉ drop bảng sau khi loại bỏ hoàn toàn HEAD và question bank scope.

### Pha 3 — hợp nhất activity log (tuỳ chọn)

Không nên xoá hàng loạt `activity_*` chỉ vì tên tương tự. Các bảng lesson/section/class/user/test/department đang có consumer. Nếu cần giảm số bảng, dùng một bảng append-only kiểu `audit_events` (`entity_type`, `entity_id`, `event_type`, `actor_id`, `metadata`, `created_at`) và dual-write/backfill theo từng nhóm; chỉ drop bảng cũ sau khi đối soát và có rollback.

## Danh sách cần xác nhận trước khi migration xoá

- `SELECT COUNT(*)` và kích thước cho từng bảng legacy.
- `information_schema` để kiểm tra FK ngoài các migration đã biết.
- log truy vấn/metrics trong ít nhất một chu kỳ sử dụng.
- dữ liệu export và kế hoạch rollback.
- kiểm tra các script seed/demo và dashboard admin.

## Quyết định đề xuất

- **Giữ**: `classes`, `enrollments`, `sections`, `lessons`, `departments`, `question_bank_*`, và activity tables có consumer.
- **Ứng viên xoá sau kiểm chứng**: `courses`, `course_categories`, `categories` (nếu không còn dùng ngoài course), `activity_courses`; các activity table không có consumer chỉ xoá sau inventory dữ liệu.
- **Không động**: mọi bảng và migration `practice_*`.
- **Sửa ngay**: authorization `/my/**`; ẩn header chỉ là UX, không phải access control.

## Phụ lục: kiểm kê toàn bộ schema hiện tại

Parser trên các migration hiện có cho thấy khoảng **110 tên bảng unique** (một số
bảng được `CREATE` ở V1 rồi `ALTER`/tái khai báo ở version sau). Trong đó khoảng
**36 bảng thuộc practice hoặc hạ tầng trực tiếp của practice**, nên không nằm
trong đợt giảm scope này. Phần còn lại khoảng **74 bảng non-practice-ish**; con số
này giải thích vì sao mục tiêu 60–70 bảng là khả thi nhưng không thể đạt chỉ bằng
việc gộp `activity_*`.

### Activity log: không nên giữ 13 bảng riêng

`V3__activity_tables.sql` tạo 13 bảng có cùng hình dạng gần như hoàn toàn:
`(id, <entity>_id, type, description, metadata, created_by, created_at)`.
Trong mã hiện tại đã xác nhận consumer cho:

- `activity_users` — admin users audit;
- `activity_departments` — department audit/history;
- `activity_classes` — class CRUD, join/leave, invite;
- `activity_sections`, `activity_lessons` — activity tab và writer;
- `activity_tests` — test authoring/monitoring.

Chưa thấy consumer Java tương ứng cho:
`activity_courses`, `activity_assignments`, `activity_submissions`,
`activity_comments`, `activity_content_versions`, `activity_flashcard_decks`;
đây là ứng viên xoá đầu tiên sau khi kiểm tra row count và dữ liệu production.
`activity_enrollments` có FK/cascade và được dùng trong cleanup test, nhưng cần
xác nhận production writer trước khi quyết định.

Kiến trúc hợp nhất đề xuất:

```text
audit_events
  id BIGINT
  entity_type VARCHAR(40)       -- USER, DEPARTMENT, CLASS, SECTION, LESSON, TEST...
  entity_id BIGINT
  event_type VARCHAR(60)
  description TEXT
  metadata JSON
  actor_id BIGINT NULL
  created_at DATETIME
  INDEX(entity_type, entity_id, created_at)
  INDEX(actor_id, created_at)
```

Không đặt FK đa hình từ `entity_id`; thay vào đó enforce referential integrity
ở service và giữ `actor_id -> users`. Migration nên dual-write, backfill, đối
soát số lượng theo từng bảng, rồi mới xoá các bảng cũ. Với dữ liệu audit cần giữ,
không hard-delete; archive sang object storage hoặc partition theo thời gian.

### Các nhóm bảng non-practice có thể gọn hơn

- Course legacy: `courses`, `course_categories`, `categories`,
  `activity_courses` — xoá 3–4 bảng nếu category không còn dùng.
- Messaging: `conversations` + `messages` — giữ, vì có luồng student/lecturer.
- Lesson templates: `lesson_templates` + `lesson_template_attachments` — giữ
  nếu lecturer còn tạo template; nếu chỉ là placeholder chưa dùng, xoá cả nhóm.
- Content review: `content_versions` và `activity_content_versions` — nếu không
  còn workflow duyệt phiên bản ngoài lesson, có thể gộp activity vào
  `audit_events`, nhưng không xoá `content_versions` khi lesson còn tham chiếu.
- Flashcards: `flashcard_decks`, `flashcards`, `flashcard_reviews` và
  `activity_flashcard_decks` — giữ nếu tính năng flashcard còn public; nếu scope
  lớp học Hàn không bao gồm flashcard thì đây là nhóm loại bỏ độc lập.
- Admin AI/mail/library: chỉ xoá khi feature flag và route đã bị loại khỏi sản
  phẩm; không suy luận từ việc ít thấy entity.

### Cho phép chỉnh migration cũ

Có thể sửa các version cũ để môi trường mới không tạo bảng legacy, nhưng không
được sửa lịch sử đã chạy trên production mà không có chiến lược Flyway rõ ràng.
Khuyến nghị:

1. Chốt danh sách bảng xoá và snapshot dữ liệu.
2. Với database mới/chưa migrate: chỉnh `V1`/`V3` để không tạo bảng, đồng thời
   loại entity/repository/seed tương ứng.
3. Với database đã migrate: tạo migration mới `V63__remove_legacy_*` để
   `DROP TABLE` theo thứ tự FK; không rewrite checksum của version đã chạy.
4. Nếu bắt buộc rewrite version cũ, phải xoá/rebuild schema trong môi trường đó
   và chạy `flyway repair` theo runbook, tuyệt đối không dùng cho production
   đang có dữ liệu.

### Mục tiêu 60–70 bảng

Mục tiêu này nên tính trên schema production sau khi loại practice khỏi phạm vi.
Một kịch bản khả thi là:

- hợp nhất 6–8 activity bảng có consumer vào `audit_events`;
- xoá 3–4 activity bảng không có consumer;
- xoá course legacy 3–4 bảng;
- xoá thêm flashcard/template/library chỉ khi product owner xác nhận.

Như vậy có thể giảm khoảng 12–20 bảng, đưa phần non-practice về vùng 54–62
tuỳ các feature giữ lại. `departments` được giữ nguyên trong mọi kịch bản hiện
tại, chờ quyết định cuối của product owner.
