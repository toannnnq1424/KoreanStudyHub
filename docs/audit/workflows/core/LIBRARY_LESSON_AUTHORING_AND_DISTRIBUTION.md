# Workflows: Library, biên soạn bài học chuẩn và phân phối vào lớp

Library là nơi duy nhất có UI biên soạn bài học. Lớp chỉ nhận **snapshot đã publish**; màn hình lớp không có route upload/sửa attachment (`LessonAttachmentsApiController.java:29-32`). Module này không gọi AI và không gửi notification/mail.

## 1. Mở Library và chọn mã môn

### Thao tác người dùng

Card **Kho học liệu** trên home trỏ tới `/lecturer/library` (`home.html:68-77`). Browser gửi:

```text
GET /lecturer/library
```

`LibraryController.library`, dòng 28-31, chỉ redirect:

```text
GET /lecturer/library/list
```

`LibraryController.listSubjects`, dòng 33-39, gọi `LessonTemplateService.getLibrarySubjectStats(userId,role)` (`LessonTemplateService.java:228-251`). Service:

1. `LibrarySubjectResolver.allowed` tải actor DB và buộc role entity khớp principal (`LibrarySubjectResolver.java:31-36`).
2. `LEADER` chỉ nhận các subject được `LeaderDepartmentResolver` phân công (`37-43`). `LECTURER` và `ADMIN` hiện được xem tất cả department active (`44-50`).
3. Với từng subject, query toàn bộ template để đếm số chapter và lesson (`LessonTemplateService.java:230-249`).
4. Render `library/list-library.html`; mỗi subject là link ở dòng 24-46.

Ô tìm kiếm tại `list-library.html:16-21` chỉ lọc card trong browser bằng JavaScript dòng 57-71; không gửi backend.

### Kết quả và lưu ý

- Click card gửi `GET /lecturer/library/templates?subjectId={id}` (`list-library.html:26-28`).
- Không có mã môn được phép thì trang empty (`50-53`) hoặc resolver báo access denied tùy dữ liệu actor.
- Việc đếm dùng một query template mỗi subject, tức N+1 theo số subject (`LessonTemplateService.java:230-250`).

## 2. Mở, tìm kiếm và phân trang cây bài học của một mã môn

### HTTP và input

```text
GET /lecturer/library/templates?subjectId={id}&q={text}&page={0-based}&size={n}
```

Form tìm kiếm ở `library/index.html:36-46`; pagination ở dòng 137-144. Nút **Tạo bài học** ở dòng 21-23.

### Controller → query → UI

`LessonTemplateController.page`, dòng 60-82, gọi `LessonTemplateService.list` ở dòng 68-69.

Service read-only `LessonTemplateService.java:129-160`:

1. `subjectResolver.require(userId,role,subjectId)` buộc subject nằm trong allowlist và active (`LibrarySubjectResolver.java:57-76`).
2. Page âm được ép 0; size không hợp lệ dùng default, quá lớn bị clamp (`LessonTemplateService.java:650-654`).
3. `q` trim; rỗng thành null (`657-661`).
4. `LessonTemplateRepository.searchSubject` tìm title/chapter case-insensitive, sort chapter/order/update (`LessonTemplateRepository.java:35-45`). Query này là **subject-scoped, không owner-scoped**.
5. Mỗi template query attachments để đếm (`LessonTemplateService.java:136-140`), rồi group các row của page hiện tại theo chapter (`142-148`).
6. `canManage = ownerId.equals(template.ownerId)`; người cùng subject có thể đọc tên/template, nhưng chỉ owner thấy link Sửa/Xóa (`library/index.html:110-131`).
7. Controller render `library/index.html` với subject, class options, chapter tree và page.

Click header chapter chỉ toggle DOM (`library/index.html:93-109,150-161`), không gọi server.

## 3. Mở form tạo hoặc sửa bài học

### Tạo mới

Nút **Tạo bài học** gửi:

```text
GET /lecturer/library/templates/new?subjectId={subjectId}
```

`LessonTemplateController.createForm`, dòng 84-90 → `LessonTemplateService.loadForm(..., templateId=null,subjectId)` (`service:162-184`). Nếu subject chưa có template, mặc định chapter 1/lesson 1; nếu có, mặc định chapter cuối và vị trí kế tiếp.

### Sửa

Link title/**Sửa bài học** ở `library/index.html:113-126` gửi:

```text
GET /lecturer/library/templates/{templateId}/edit
```

`LessonTemplateController.editForm`, dòng 92-98 → `LessonTemplateService.loadForm` dòng 185-202. `getOwned` (`service:576-578`) buộc template thuộc actor; không thể sửa template chung của người khác dù actor thấy nó trong cây.

### Dữ liệu form

Controller `populateForm` (`LessonTemplateController.java:186-193`) thêm:

- `materialOptions`: asset của chính owner (`LessonTemplateService.java:205-211`);
- subject context và subject allowlist;
- `form` để bind.

Form multipart nằm tại `library/lesson-form.html:17-22`:

| Field | UI |
|---|---|
| `id`, `subjectId` | hidden, dòng 21-22 |
| `chapterNumber`, `chapterTitle` | dòng 33-44 |
| `lessonNumber` | hidden, dòng 48-51; backend tự tính lại |
| `title` | dòng 53-59 |
| `contentType` | `RICHTEXT/PDF/VIDEO`, dòng 61-71 |
| `contentRichtext` | dòng 78-82 |
| `pdfUpload` hoặc `pdfLibraryAssetId` | dòng 85-100 |
| `videoProvider`, `videoUpload`, `videoLibraryAssetId`, `videoUrl` | dòng 102-129 |
| `materialUploads`, `materialAssetIds` | dòng 132-150 |
| CSRF | dòng 20 |

`library-lesson-form.js:4-21` chỉ hiện và enable control của `contentType` đang chọn; field vùng inactive bị disable nên không được submit.

## 4. Lưu bài học RICHTEXT/PDF/VIDEO

### Thao tác người dùng

Click **“Lưu bài học”** (`lesson-form.html:153-156`):

```text
POST /lecturer/library/templates
Content-Type: multipart/form-data
```

### Controller

`LessonTemplateController.save`, dòng 100-126:

1. Spring bind `LessonTemplateForm` và Bean Validation: chapter/title không trống, giới hạn kích thước, content type bắt buộc (`LessonTemplateForm.java:16-39`).
2. Binding error render lại form ngay (`controller:106-109`).
3. Hợp lệ thì gọi `LessonTemplateService.saveForm` dòng 111.
4. Thành công flash và redirect về subject Library (`112-113`). Business/not-found lỗi redirect lại create/edit; runtime khác log server và trả thông báo generic (`114-125`).

### Service và transaction

Toàn bộ save chạy trong `@Transactional` tại `LessonTemplateService.java:254-326`:

1. Resolve subject theo actor/role (`256`); số chapter phải dương, chapter/title phải có text (`257-261`); `Lesson.validateContentType` chỉ nhận type hợp lệ (`262-263`).
2. `ingestInlineUploads` (`264`, chi tiết `328-351`) lưu file mới qua `LibraryService.upload`.
3. `LibraryService.upload`, dòng 32-41, gọi `LibraryStorageService.store`, đăng ký xóa object nếu transaction rollback qua `StorageTransactionLifecycle.deleteOnRollback`, rồi insert `LibraryAsset` chứa owner, key, MIME, size, kind.
4. Nếu tạo mới: lấy cây subject, dùng chapter title sẵn có hoặc canonical hóa thành `Chương N · ...`, tính vị trí insert sau bài cuối chapter, shift các display order sau đó và tạo `LessonTemplate` (`LessonTemplateService.java:266-278`).
5. Nếu edit: `getOwned`; không cho đổi template sang subject khác. Cùng chapter thì đổi canonical chapter title cho toàn chapter; chuyển chapter thì khép order cũ, chèn vào order mới (`279-304`).
6. Save các order thay đổi, rồi `applyFormBody` (`305-309`, logic `461-510`).
7. Save/flush template; xóa toàn bộ attachment mapping cũ rồi deduplicate `materialAssetIds`, khóa từng asset owner-scoped và tạo lại `LessonTemplateAttachment` theo order (`309-324`).

### Nhánh nội dung

- `RICHTEXT`: sanitize HTML bằng `HtmlSanitizer` trước khi persist (`LessonTemplateService.java:461-466`).
- `PDF`: bắt buộc asset chính thuộc owner, kind `DOCUMENT`, MIME `application/pdf` (`468-479`).
- `VIDEO/UPLOAD`: bắt buộc asset owner-scoped, kind `VIDEO`; lưu library asset id + storage key (`481-497`).
- `VIDEO/YOUTUBE` hoặc `VIDEO/VIMEO`: URL phải qua matcher tương ứng, sau đó mới persist (`498-507`).
- Type/body thiếu hoặc sai: transaction rollback và object mới upload được callback xóa.

`LibraryAssetRepository.findByIdAndOwnerIdForUpdate` dùng `PESSIMISTIC_WRITE` (`LibraryAssetRepository.java:22-29`) để serialize việc tạo durable reference với xóa asset. Form không thể gắn asset của user khác bằng sửa id.

### UI nhận kết quả

Redirect `GET /lecturer/library/templates?subjectId=...`; flash **“Đã lưu bài học trong Library”**; cây subject query lại và hiện snapshot template. Chưa có lớp nào nhận nội dung ở bước này.

## 5. Phân phối toàn bộ subject vào một hoặc nhiều lớp

### Thao tác người dùng

Khi subject có template, form **“Phân phối trọn bộ”** nằm tại `library/index.html:49-82`. User tick các `classIds` ở dòng 66-73 và bấm **“Phân phối toàn bộ học liệu”** dòng 79-80:

```text
POST /lecturer/library/templates/subjects/{subjectId}/distribute
Content-Type: application/x-www-form-urlencoded
```

Form có CSRF dòng 59. UI chỉ liệt kê class cùng subject, chưa archived, nằm trong scope actor (`LessonTemplateService.java:113-127`), nhưng backend vẫn kiểm lại.

### Controller → toàn bộ template

`LessonTemplateController.distributeSubject`, dòng 148-168:

1. Bind `classIds`, lấy actor từ principal.
2. Gọi `LessonTemplateService.distributeSubject` dòng 154-156.
3. Tính số class distinct từ kết quả, flash số bài snapshot và số lớp (`157-159`).
4. Business lỗi thành flash; runtime lỗi thành generic; luôn redirect lại subject (`160-167`).

Service transaction `LessonTemplateService.java:403-417`:

1. Resolve subject trong allowlist.
2. Lấy mọi template subject theo chapter/order; rỗng thì từ chối.
3. Với mỗi template, gọi `distribute(templateId,classIds,actor,role)`.

Vì `distributeSubject` là transaction ngoài và lời gọi `distribute` diễn ra cùng bean, toàn bộ subject/class set tham gia cùng transaction; một duplicate/lớp sai ở giữa sẽ rollback cả lần phân phối.

### Clone một template vào từng lớp

Logic chính ở `LessonTemplateService.distribute`, dòng 354-396:

1. `classIds` null/rỗng → từ chối; deduplicate bằng `LinkedHashSet` (`357-365`).
2. Load template; resolve subject từ template (`360-363`).
3. Với từng class, `ClassesService.getEditable` kiểm role/scope; buộc class cùng `subjectId` và không `ARCHIVED` (`366-370`).
4. Tìm section có title bằng chapter template; chưa có thì `SectionsService.create` rồi query lại (`371-379`).
5. Nếu cùng section đã có lesson title case-insensitive thì từ chối (`380-384`). Không silently overwrite.
6. `snapshotTemplateToSection` tạo bản độc lập (`385-386`, chi tiết `433-456`).
7. Load lesson vừa tạo, chuyển `DRAFT → PUBLISHED`, save và ghi `LessonActivity.TYPE_PUBLISHED` (`387-393`).

### Snapshot body và materials

`snapshotTemplateToSection`, dòng 433-456:

1. Khóa section để serialize display order (`436`).
2. `materializeDraft` (`563-573`) tính `max(display_order)+1`, tạo Lesson RICHTEXT/DRAFT và flush để có id.
3. `applyTemplateBodyToLesson` (`512-560`):
   - RICHTEXT được sanitize/copy;
   - PDF tạo `LessonAttachment` trỏ cùng Library object, set `pdf_attachment_id`, rồi chuyển type;
   - video external copy provider/url; video upload copy library asset id/storage key.
4. Copy mọi material extra thành `LessonAttachment` tham chiếu LibraryAsset, không copy bytes (`443-452`).
5. Ghi activity `CREATED`; sau khi caller publish, ghi thêm activity `PUBLISHED`.

Kết quả là snapshot DB độc lập về title/body/mapping. Sửa template về sau không tự sửa lesson đã phân phối; blob Library có thể được cùng tham chiếu.

### UI và side effects

- Redirect về Library và flash, ví dụ **“Đã phân phối toàn bộ 12 bài học tới 3 lớp”**.
- Học sinh `ACTIVE` thấy lesson ngay vì snapshot được publish.
- Không notification hoặc mail cho học sinh.
- Không có logic tránh phân phối lại toàn subject; duplicate title khiến toàn transaction fail/rollback.

## 6. Phân phối một template: API backend có nhưng UI hiện tại không có nút

Backend vẫn expose:

```text
POST /lecturer/library/templates/{templateId}/distribute
classIds=...
```

`LessonTemplateController.distribute`, dòng 128-146, gọi cùng `LessonTemplateService.distribute` và flash số lớp. Tuy nhiên, search toàn bộ templates/JS hiện tại chỉ thấy form phân phối subject tại `library/index.html:57-80`; không có form/link gọi route per-template. Vì vậy đây là route reachable bằng HTTP trực tiếp hoặc client cũ, không phải workflow người dùng có nút trên UI hiện tại.

## 7. Xóa bài học chuẩn khỏi Library

### Thao tác và backend

Owner thấy nút **Xoá** tại `library/index.html:125-130`:

```text
POST /lecturer/library/templates/{id}/delete
```

Form có CSRF dòng 128, nhưng không có confirm client-side.

`LessonTemplateController.delete`, dòng 170-184 → `LessonTemplateService.softDelete(ownerId,id)` dòng 175.

Service transaction `LessonTemplateService.java:419-430`:

1. `getOwned` buộc owner; leader/admin không thể xóa template người khác chỉ vì có role cao.
2. `markDeleted`, save; entity soft-delete restriction làm row biến mất khỏi query Library.
3. Lấy các template còn lại cùng subject, shift display order sau vị trí vừa xóa xuống 1 và save all.

Controller flash rồi redirect list. `LessonTemplateAttachment` cũ vẫn giữ để đảm bảo FK (`service:419`); LibraryAsset/object không bị xóa. Các lesson snapshot đã phân phối vào lớp không bị ảnh hưởng.

Lưu ý UI: hai route POST theo từng template (`/{id}/distribute` và `/{id}/delete`) return `redirect:/lecturer/library/templates` không mang `subjectId` (`LessonTemplateController.java:145,183`). Browser sẽ mở subject mặc định theo actor, không nhất thiết subject của template vừa thao tác. Phân phối trọn subject giữ được context vì controller biết `subjectId` path.

## 8. Downstream: lớp/learner chỉ đọc snapshot đã publish

Sau phân phối, class shell và learner không đọc `LessonTemplate`; chúng chỉ query `Section` + `Lesson` của class:

```text
GET /my/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
GET /lecturer/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
```

`StudentLessonsController.view`, dòng 74–113, nạp `StudentLessonsService.listClassLessons`. Query service lấy section theo class rồi chỉ lấy `Lesson.STATUS_PUBLISHED` theo thứ tự (`StudentLessonsService.java:108–161`); learner phải ACTIVE-enrolled và class ACTIVE, trong khi owner lecturer/ADMIN/LEADER đúng subject có thể inspect. ID section/lesson sai hoặc cross-section không leak existence: list fallback section đầu/placeholder; detail gate ném not-found rồi controller giữ hero (`StudentLessonsController.java:96–111`). Lecturer route `LessonsTabController.viewDistributedLessons`, dòng 49–76, render cùng template nhưng kiểm class view scope trước.

Khi có `lesson`, `StudentLessonDetailService.getLessonDetail` (`103–162`) check class live, section thuộc class, lesson published, sau đó access policy; build view khác nhau theo snapshot body:

- `RICHTEXT`: HTML đã sanitize được render từ `Lesson.contentRichtext`.
- `PDF`: main attachment được stream bằng `GET /api/lessons/{lessonId}/attachments/{attachmentId}/download`; attachment extra cũng có download/view URL. API chỉ read (`LessonAttachmentsApiController.java:29–84`), kiểm access trước open object; missing blob trả 404.
- `VIDEO/UPLOAD`: UI gọi `GET /api/lessons/{lessonId}/video/stream`; endpoint check published/enrollment cho student, hoặc editable class scope cho staff, và hỗ trợ Range (`LessonVideoStreamController.java:78–208`). YouTube/Vimeo dùng embed URL đã chuẩn hóa từ snapshot.

Mở detail của STUDENT cố gắng ghi progress `IN_PROGRESS` sau khi tất cả gate pass; lỗi ghi progress bị log và không làm hỏng render (`StudentLessonsController.java:99–151`). Không có update ngược template hay class snapshot, notification/mail hay synchronization sau phân phối.

## Security và giới hạn xuyên suốt

- Tất cả controller Library có `@PreAuthorize(PREAUTH_LECTURER_OR_ABOVE)` (`LibraryController.java:14-16`, `LessonTemplateController.java:45-47`).
- Actor id luôn lấy từ principal; owner id không phải form field.
- Subject được resolve lại ở service; class được check lại khi distribute; asset được khóa và owner-scope.
- `searchSubject` cho phép mọi actor được phép với subject đọc template của các owner khác; UI chỉ cấp sửa/xóa cho owner. Phân phối template dùng `findById` + subject allowlist, nên actor cùng subject có thể phân phối template của người khác theo thiết kế kho chung.
- Không có AI generation, notification, email, WebSocket hay background job trong các workflow Library này.
- Asset/material selector lại luôn owner-scoped. Vì vậy actor có thể phân phối template chung của người khác, nhưng không thể mở edit để thay attachment bằng asset của owner khác; snapshot distribution đọc asset dưới `template.ownerId` và sẽ fail/rollback nếu Library asset đã bị soft-delete. Storage object thiếu không được mở ở lúc distribute, nên snapshot vẫn có thể tạo nhưng download/stream downstream trả 404.
