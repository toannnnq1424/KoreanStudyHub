# Workflows: mở bài học đã phân phối, xem file và phát video

Module Lesson hiện có hai mặt reachable: học sinh/giảng viên **đọc bài đã publish**, và các endpoint stream file/video. UI authoring trực tiếp trong lớp đã bị thay bằng Library; phần cuối tài liệu chỉ rõ các service mutation không còn controller/UI gọi.

## 1. Học sinh mở tab bài giảng của lớp

### Thao tác người dùng và request

Từ sidebar lớp, học sinh mở:

```text
GET /my/classes/{classId}/lessons
GET /my/classes/{classId}/lessons?section={sectionId}
```

Trong `student/class-lessons.html`, link chọn chapter nằm tại dòng 87-100. Đây là navigation GET, không dùng fetch.

### Controller → service → repositories

`StudentLessonsController.view`, dòng 75-114:

1. Endpoint yêu cầu authenticated (`controller:53-54`). Nếu principal không phải STUDENT, controller redirect sang `/lecturer/classes/{classId}/lessons` và giữ query (`81-83`, helper `116-127`).
2. Học sinh gọi `StudentLessonsService.listClassLessons(classId,userId,role)` (`85-86`).
3. Service transaction read-only `StudentLessonsService.java:91-155`:
   - load class sống; STUDENT chỉ vào class `ACTIVE` (`93-100`);
   - `ClassAccessPolicy.requireViewAccess` buộc enrollment `ACTIVE` nếu không phải viewer privileged (`102-106`; policy `ClassAccessPolicy.java:53-64`);
   - query section theo display order (`108-109`);
   - với mỗi section, chỉ lấy Lesson `PUBLISHED`, DRAFT không vào DTO (`111-121`, query call `157-160`);
   - gom mọi published id và query completed ids một lần (`124-128`);
   - tính completed/total/percent, lecturer name và subject code (`130-154`).
4. Controller chỉ chấp nhận `section` thuộc DTO; id sai tự fallback section đầu (`88`, helper `172-184`).
5. Tải flashcard deck share vào sidebar (`93-94`) và render `student/class-lessons.html`.

### UI nhận kết quả

- Sidebar hiển thị progress tổng ở `class-lessons.html:52-67`, shared decks ở `71-83`, chapter ở `85-101`.
- Không section: empty state dòng 110-128.
- Có section nhưng chưa chọn lesson: hero **“Chọn bài giảng để bắt đầu”** dòng 215-241.
- Rail chỉ chứa lesson publish và icon theo `RICHTEXT/PDF/VIDEO` (`244-335`).

Chỉ mở danh sách/chapter chưa ghi learning progress; progress được ghi khi detail lesson resolve thành công.

## 2. Học sinh click một bài và backend tự ghi IN_PROGRESS

### Thao tác người dùng

Card lesson ở `student/class-lessons.html:291-335` gửi:

```text
GET /my/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
```

### Controller và gate chống dò id

`StudentLessonsController.view`, dòng 96-112:

1. Trước tiên lesson id phải xuất hiện trong active section DTO (`99-100`, helper `154-163`). Cross-section/foreign id không được gọi detail service và UI trở về hero.
2. `StudentLessonDetailService.getLessonDetail` (`102-104`).
3. Detail failure bị nuốt thành hero để không lộ existence (`108-111`).
4. Chỉ sau khi detail hợp lệ, `recordOpenedQuietly` chạy (`105-107`). Lỗi progress bị log nhưng không làm hỏng màn bài (`141-151`).

### Detail service → body URL

`StudentLessonDetailService.getLessonDetail`, dòng 102-163:

1. `LessonAccessResolver.resolveInClass` buộc class tồn tại, section của lesson thuộc đúng class, lesson `PUBLISHED` (`LessonAccessResolver.java:49-68`). Mọi fail cùng dạng 404 no-leak.
2. STUDENT chỉ xem class `ACTIVE`; sau đó `ClassAccessPolicy.requireViewAccess` buộc enrollment `ACTIVE` (`StudentLessonDetailService.java:113-121`).
3. Query attachments, loại main PDF khỏi danh sách accessory (`123-140`).
4. Xây URL theo content type (`142-162`):
   - PDF: download + `/file-viewer?type=pdf...` (`165-187`);
   - YouTube/Vimeo: chuyển URL sang embed; UPLOAD: `/api/lessons/{id}/video/stream` (`217-233`);
   - accessory PDF/DOCX/PPT/XLS: chọn internal viewer hoặc Office redirect (`189-209`).

### Side effect GET: tạo progress

`LearningProgressService.recordOpened`, dòng 50-61:

1. Kiểm enrollment `ACTIVE`, rồi tái kiểm live class/cross-class/PUBLISHED (`96-104`).
2. Nếu `(userId,lessonId)` đã có row thì không sửa.
3. Nếu chưa có, insert `LearningProgress` trạng thái `IN_PROGRESS`, percent 0, `startedAt=now` (`LearningProgress.java:78-84`).
4. Race hai GET đầu tiên được unique key chặn; `DataIntegrityViolationException` bị nuốt vì end-state đã đạt (`LearningProgressService.java:53-60`).

Vì vậy request GET xem lesson có side effect DB idempotent. Không notification/mail.

### UI render theo content type

- RICHTEXT dùng `th:utext` tại `class-lessons.html:143-152`; nội dung đã sanitize lúc persist từ Library.
- PDF iframe + link download tại dòng 154-164.
- YouTube/Vimeo iframe hoặc `<video>` uploaded tại dòng 166-183.
- Accessory download/**Xem** tại dòng 192-210.

## 3. URL detail cũ được chuyển sang URL query chuẩn

Client/bookmark cũ gọi:

```text
GET /my/classes/{classId}/lessons/{lessonId}
```

`StudentLessonsController.redirectStandaloneLesson`, dòng 203-218:

1. Gọi `StudentLessonDetailService.getLessonDetail`, nên vẫn có đủ class/section/PUBLISHED/access gate.
2. Lấy `sectionId` thật từ DTO.
3. Trả HTTP `301 Moved Permanently`, header:

```text
Location: /my/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
```

Request legacy này không tự ghi progress; browser follow 301 sang URL chuẩn mới kích hoạt `recordOpened`.

## 4. Giảng viên/Leader/Admin mở tab lesson của lớp

### Request và backend

```text
GET /lecturer/classes/{classId}/lessons?section={sectionId}&lesson={lessonId}
```

`LessonsTabController.viewDistributedLessons`, dòng 49-77:

1. `@PreAuthorize(PREAUTH_LECTURER_OR_ABOVE)` (`30-31`).
2. `ClassesService.getViewable` kiểm scope class (`55`).
3. Tái dùng `StudentLessonsService.listClassLessons`; policy cho admin, leader đúng department hoặc teaching staff đúng scope đi qua mà không cần enrollment (`56-57`, `ClassAccessPolicy.java:38-43`).
4. Chỉ lesson PUBLISHED hiện trên UI. Nếu `lesson` nằm đúng section, gọi cùng detail service; foreign id silently bị bỏ (`66-74`).
5. Set `teachingView=true`, dùng shared template `student/class-lessons.html` (`59-76`). Không ghi learning progress cho viewer giảng dạy.

UI chỉ đọc. Empty state chỉ dẫn **“Tạo bài học trong Library rồi phân phối tới lớp này”** (`class-lessons.html:120-125`). Không có create/edit/upload/publish control trong màn class.

### Phát hiện UI

Form completion tại `class-lessons.html:336-350` không có điều kiện `!teachingView`, nên cũng render trên shared lecturer view. Nếu giảng viên bấm, POST bị `LearningProgressController` chặn bởi `PREAUTH_STUDENT` (`LearningProgressController.java:28-30`) và trả 403. Đây là control thừa trên teaching view, không tạo progress giảng viên.

## 5. Download trực tiếp một attachment

Click tên file hoặc **Tải PDF xuống** gửi:

```text
GET /api/lessons/{lessonId}/attachments/{attachmentId}/download
```

`LessonAttachmentsApiController.download`, dòng 48-85:

1. Yêu cầu authenticated (`49`).
2. Gọi `LessonAttachmentsService.download` (`55-56`).
3. Service transaction read-only ở `LessonAttachmentsService.java:294-311`:
   - attachment phải có đúng `lessonId`;
   - lesson phải tồn tại;
   - traverse lesson → section → class;
   - LECTURER/LEADER/ADMIN phải qua `ClassesService.getEditable`; STUDENT phải enrollment `ACTIVE` và lesson `PUBLISHED` (`301-305`, helpers `332-355`);
   - chuẩn hóa storage key bằng `StorageKeys.requireSafeKey` (`307-310`).
4. Controller kiểm object tồn tại, `ObjectStorage.open`, set `Content-Disposition: attachment`, MIME và length, stream `StoredObjectResource` (`63-84`).

Kết quả: 200 stream; access denied 403; row/object thiếu 404; storage I/O 500 (`controller:57-71`). Không đọc toàn file vào memory controller.

## 6. Xem PDF hoặc DOC/DOCX trong viewer nội bộ

### Từ UI đến viewer shell

PDF main iframe hoặc accessory **Xem** trỏ tới:

```text
GET /file-viewer?type=pdf|docx&lessonId={id}&attachmentId={id}&filename={name}
```

`FileViewerController.view`, dòng 43-69:

1. Yêu cầu authenticated.
2. Gọi lại `attachmentsService.download` chỉ để chạy cùng authz gate (`51-59`).
3. Không stream file vào HTML; model chỉ chứa authenticated download URL và filename (`60-63`).
4. `type=docx` render `student/docx-viewer`, mọi type khác render `student/pdfjs-viewer` (`65-68`).

### Browser fetch bytes

- PDF template dùng PDF.js CDN, lấy `downloadUrl` (`pdfjs-viewer.html:76-93`) và PDF.js gọi URL với credentials (`177-187`).
- DOCX template tải JSZip/docx-preview CDN (`docx-viewer.html:68-79`), rồi `fetch(downloadUrl,{credentials:'include'})`, đọc `arrayBuffer` và render (`81-117`).
- Nút download ở hai viewer chỉ mở lại endpoint download.

Tức chuỗi thực tế:

```text
Click Xem
  → GET /file-viewer (server authorize, trả HTML shell)
  → browser JS GET /api/lessons/.../download (server authorize lần nữa)
  → bytes được PDF.js/docx-preview render
```

Viewer phụ thuộc CDN bên ngoài; CDN lỗi thì hiện fallback tải file. Không AI.

## 7. Xem PPT/XLS bằng Microsoft Office Online và public token một giờ

### Click từ UI

Với `.ppt/.pptx/.xls/.xlsx`, `StudentLessonDetailService.buildAttachmentViewUrl` tạo:

```text
GET /file-viewer/office?lessonId={id}&attachmentId={id}
```

`FileViewerController.viewOffice`, dòng 77-95:

1. Yêu cầu authenticated và gọi `attachmentsService.download` để authorize **trước khi mint token** (`77-90`).
2. Gọi `PublicViewTokenService.createPublicViewUrl(attachmentId)` (`91`).
3. URL public được URL-encode vào `https://view.officeapps.live.com/op/embed.aspx?src=...`, controller redirect external (`92-94`).

### Mint/revoke token

`PublicViewTokenService.createPublicViewUrl`, dòng 54-69, trong transaction:

1. Khóa attachment `FOR UPDATE` (`56-57`).
2. Tìm mọi token chưa hết hạn của attachment và xóa, nên mỗi click thay bearer credential cũ (`58-62`; repository query `PublicViewTokenRepository.java:21-23`).
3. Sinh 32 random bytes bằng `SecureRandom`, Base64URL (`107-110`).
4. DB chỉ lưu SHA-256 digest, không lưu raw token; expiry 1 giờ (`64-68`, `32`, hash `113-120`).

### Office server lấy file ẩn danh

Microsoft gọi:

```text
GET /public/view/{rawToken}
```

Route được `permitAll` tại `SecurityConfig.java:230`. `PublicViewController.view`, dòng 49-89:

1. `PublicViewTokenService.resolve` hash raw token, có fallback token legacy; invalid/expired → 404 và expired row bị xóa (`PublicViewTokenService.java:76-99`).
2. Resolve attachment/storage key, mở object.
3. Trả inline file với MIME/length, `Cache-Control: private,no-store`, `Referrer-Policy:no-referrer` và CSP chỉ cho Office frame ancestor (`PublicViewController.java:74-100`).

Scheduler `TokenCleanupScheduler.cleanupExpiredTokens` chạy mỗi 30 phút (`TokenCleanupScheduler.java:26-31`) để xóa token hết hạn chưa từng được truy cập.

Rủi ro chức năng: Office Online cần `app.base-url` public có thể truy cập; mặc định service là `http://localhost:8080` (`PublicViewTokenService.java:40-45`), nên môi trường chưa cấu hình public base URL sẽ không render từ server Microsoft.

## 8. Phát uploaded MP4 và xử lý HTTP Range

Khi `contentType=VIDEO`, provider `UPLOAD`, template đặt `<video src="/api/lessons/{id}/video/stream">` (`class-lessons.html:166-181`). Browser gửi GET, thường kèm `Range`:

```text
GET /api/lessons/{lessonId}/video/stream
Range: bytes={start}-{end?}
```

`LessonVideoStreamController.stream`, dòng 78-175:

1. Authenticated; load lesson (`78-89`).
2. Chỉ nhận `VIDEO + UPLOAD` có storage reference (`91-95`).
3. Resolve class từ section; privileged viewer phải `ClassesService.getEditable`; student phải lesson PUBLISHED + enrollment ACTIVE (`97-100`, helper `195-208`). Failure trả 404 để không lộ object.
4. Ưu tiên storage key từ `videoLibraryAssetId`; fallback `lesson.videoUrl`; cả hai qua `StorageKeys.requireSafeKey` (`181-188`).
5. Probe `ObjectStorage` lấy size; size unknown/I/O trả 500 (`109-123`).
6. Không Range: stream toàn file `200 OK` (`131-140`).
7. Range sai: `416` + `Content-Range: bytes */size` (`143-155`).
8. Range hợp lệ: chỉ phục vụ range đầu và cap mỗi response 1 MiB (`51-52`, `157-170`), trả `206 Partial Content`, `Accept-Ranges`, `Content-Range`, `Content-Length`.

YouTube/Vimeo không đi endpoint này; browser tải embed trực tiếp từ provider external.

## 9. Các service authoring tồn tại nhưng không còn workflow HTTP/UI

Source vẫn còn các method mutation class-scoped:

- `LessonsService`: create `110/120`, update `172/392`, delete `230`, reorder `266`, set/bind video `280/309/342`;
- `LessonsPublishService`: publish `64`, unpublish `83`;
- `SectionsService`: create `78`, rename `93`, delete `124`, reorder `140`;
- `LessonAttachmentsService`: upload/main PDF/bind/delete tại dòng `112-259`.

Nhưng toàn bộ controller Lesson hiện chỉ expose các GET liệt kê trong `LessonsTabController`, `LessonAttachmentsApiController`, `LessonVideoStreamController`, `PublicViewController`, `StudentLessonsController` và `FileViewerController`; không có POST/PUT/DELETE gọi các mutation trên. Ngoại lệ nội bộ: Library dùng `SectionsService.create`, repository và snapshot helpers khi phân phối. Vì vậy các method còn lại là code service không reachable như một thao tác màn hình hiện tại, không được mô tả giả thành workflow người dùng.

## 10. State JavaScript của shell bài học

`static/js/student-lesson-nav.js:14–35` chỉ xử lý nút **Rời lớp** trong sidebar: tìm hidden form có CSRF, mở `KshModal.confirm`, rồi submit `POST /my/classes/{id}/leave`. Nó không load lesson, không giữ progress và không thay enrollment trước khi server trả redirect.

Các select/filter dùng `static/js/learning-select.js` chỉ lọc option/link đã được `templates/student/class-lessons.html` render. Query lesson authoritative vẫn là `LessonsTabController`/`StudentLessonsController` → service/repository ở mục 1–2. Chọn lesson tạo GET mới; chỉ side effect `IN_PROGRESS` nêu ở mục 2 mới ghi DB. Trạng thái mở/đóng chapter/sidebar chỉ sống trong DOM và mất khi reload.
