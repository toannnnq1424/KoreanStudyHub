# Workflow audit: Tests, attempts, practice, monitor và AI question generation

Tài liệu này đi từ thao tác trên màn hình đến request, controller, service, repository/entity và kết quả quay lại UI. Workflow random đề trực tiếp từ Question Bank được tách riêng tại `docs/audit/workflows/QUESTION_BANK_RANDOM_TEST.md`; phần dưới vẫn bao phủ mọi luồng còn lại của module Tests.

## 1. Giảng viên tạo hoặc sửa bài test thủ công

### Initial list và class-tab handler traces

`LecturerTestController.list` (`:123–142`) là initial screen thật của kho đề: `GET /lecturer/tests?page=0&q=&status=&type=&classId=` dưới class gate `PREAUTH_LECTURER_OR_ABOVE`. Nó trước hết gọi `LecturerExamService.ledClasses`, lấy role authoritative và query classes actor quản lý để làm option filter. `ExamFilter.of` chỉ giữ `classId` nếu nó có trong option đó; tiếp theo `listOwned` query `TestRepository.searchManageable` với pageable (page âm clamp 0, sort `updatedAt DESC`) và scope role: Lecturer own/created, Leader department classes, Admin global, đồng thời exclude Practice theo management query. Không mutation. Response là `tests/lecturer-list.html` với `examsPage`, `examFilter`, `ledClasses`, `examPagerParams`; native filters/pager phát GET lại. URL/role/class forged không mở rộng result vì service recompute scope, nhưng query/state không hợp lệ chỉ cho list rỗng/filtered chứ không persist gì.

`ClassDetailController.detailTests` (`:136–145`) là sibling class-shell route `GET /lecturer/classes/{id}/tests?page=0`, cũng Lecturer-or-above. Initial query trước tiên `ClassesService.getViewable(id, principal, role)` để load/authorize `ClassEntity`, rồi `ClassDetailModelSupport.populateDetail` nạp class chrome/sidebar. Sau đó `LecturerExamService.listForClass` **lặp lại** `TestAccessResolver.requireManageableClass`, query `TestRepository.findByClassId(classId, PageRequest(max(page,0), ..., updatedAt DESC))`, map class name rows, không write. Return `classes/detail-tests.html` với `examsPage`; pager giữ class route. Không xem được class hoặc cross-department/owner scope bị AccessDenied/NotFound theo resolver trước khi model/UI render.

### Thao tác người dùng

Từ **Kho bài test**, giảng viên bấm **“Tạo bài test”** tại `templates/tests/lecturer-list.html:19`:

```text
GET /lecturer/tests/new
```

`LecturerTestController.newForm`, dòng 145–160, tải các lớp actor được quản lý và trả `tests/lecturer-form.html`. Khi sửa, icon **“Sửa bài test”** tại `lecturer-list.html:72` gửi:

```text
GET /lecturer/tests/{id}/edit?tab=info
```

`LecturerTestController.editForm`, dòng 217–248, gọi `LecturerExamService.loadForEdit`; service dòng 143–164 kiểm quyền quản lý đề rồi tạo DTO chứa đề, câu hỏi và đáp án.

Form và các trường thật nằm tại `lecturer-form.html:83–241`:

| Control | Dòng | JSON gửi server |
|---|---:|---|
| Tiêu đề | 134 | `title` |
| Lớp nguồn | 139 | `classId` |
| Loại đề | 150 | `testType` |
| Trạng thái | 157 | `status` (`DRAFT`/`PUBLISHED`) |
| Chế độ thời gian | 165 | `timeMode` |
| Thời lượng | 172 | `durationMinutes` |
| Bắt đầu/kết thúc | 176/180 | `startAt`, `endAt` |
| Điểm đạt | 184 | `passingScore` |
| Trộn câu/đáp án | 187–188 | `shuffleQuestions`, `shuffleOptions` |
| Loại/URL media | 213/221 | `mediaType`, `mediaUrl` |
| Câu hỏi, điểm, options đúng/sai | 445–473 | `questions[]` và `questions[].options[]` |

Nút **“Lưu bài test”** ở dòng 44 hoặc 100 không submit form HTML thẳng. `static/js/test-lecturer-form.js:320–426` chặn submit, lần lượt:

1. `rewriteAllDataImages()` ở dòng 297–313 upload các ảnh data-URL còn trong editor.
2. `collect()` ở dòng 96–120 đọc metadata, questions và options thành `ExamForm` JSON.
3. Client loại câu hoàn toàn trống ở dòng 351–356, rồi chặn câu/options thiếu nội dung và data-URL chưa upload ở dòng 370–396.
4. `FcCommon.postJson` ở dòng 399 gửi:

```text
POST /lecturer/tests/save
Content-Type: application/json
Body: ExamForm
```

### Controller và service

`LecturerTestController.save`, dòng 285–303:

- actor lấy từ principal;
- bind `@RequestBody ExamForm`, gọi `LecturerExamService.save(userId, role, form)`;
- trả JSON `SaveResult` khi thành công;
- lỗi validate/quyền được exception handler chuyển thành response lỗi để JS toast tại `test-lecturer-form.js:418–423`.

`LecturerExamService.save`, dòng 183–248, là transaction chính:

1. Validate form và lớp đích; không tin `classId` do browser gửi, mà kiểm lại actor có quản lý lớp và subject phù hợp.
2. `ExamImageStorageService.beginClaim` tạo claim session của đúng owner.
3. Tạo `Test` mới hoặc lock/load đề cũ; set title, description, class, type, status, time window, passing score, shuffle và media.
4. Claim mọi staged image xuất hiện trong description/question/option HTML. URL staged của user khác, quá 24 giờ hoặc không tồn tại bị từ chối.
5. Nếu chưa có student activity, service thay toàn bộ question/options; nếu đã có activity, chỉ cho cập nhật nội dung theo chiến lược bảo toàn cấu trúc nhằm không phá dữ liệu attempt.
6. Tính lại `totalQuestions`, lưu `Test`, `TestQuestion`, `TestOption`, rồi ghi `TestActivity` (`CREATED`, `UPDATED`, `PUBLISHED` tùy thay đổi).

### Initial client state và các module form

Initial GET create đưa `examForm=null`, classes và selected class vào model; initial GET edit đưa `ExamForm` (test, question/options, `questionBankLocked`) thành JSON island `#lfData` trong `#tabPanel` (`LecturerTestController.java:145–160,217–247`). `static/js/test-lecturer-form.js:40–140,428–450` parse island, hydrate controls/Quill/question rows hoặc tạo một question trống trong create mode. `questionBankLocked` là client guard để chặn add/picker sau student activity, nhưng không phải quyền: `LecturerExamService`/writer kiểm lại khi POST.

- `static/js/test-lecturer-form-builder.js:139–234` giữ question/option id khi hydrate, tạo mới mặc định hai options, và derive `MCQ`/`MR` từ số option được tick (hơn một là `MR`). IDs này là cần thiết cho server update-in-place khi shape đã lock; script không ghi DB.
- `static/js/test-lecturer-form-mode.js:30–143` là state machine UI READING/MEDIA + INDIVIDUAL/FIXED_WINDOW. Reading mode khiến collector gửi `mediaType/mediaUrl=null`; media mode bắt buộc client chọn type/URL. Quill description là passage/note trong JSON, còn server sanitize/validate final fields.
- `static/js/test-lecturer-form-quill.js:41–166` là ngoại lệ có I/O trước Save: chèn/paste/drop JPEG/PNG/WebP gửi `POST /lecturer/tests/images` multipart ngay, nhận staged URL rồi đặt vào editor. Data URL còn sót được rewrite tuần tự trước collect. Staged object chưa là nội dung durable của đề cho đến `ExamImageStorageService.ClaimSession` được gọi trong transaction save; upload/parse fail chỉ toast, không tạo `TestQuestion`.
- Submit handler `test-lecturer-form.js:297–426` khóa mutation/dirty guard, rewrite ảnh, client-check empty question/option/media rồi mới `POST /lecturer/tests/save`. Save thường redirect list; **Tạo nháp & sinh AI** lọc question trống, ép `DRAFT`, save trước rồi redirect `/edit?tab=info&openAi=1`. Server validation vẫn authoritative.

UI nhận JSON; create thành công quay về `GET /lecturer/tests`, còn thao tác **“Lưu để dùng AI”** ở `lecturer-form.html:238` lưu trước rồi mở lại edit của test vừa có id (`test-lecturer-form.js:315–418`). Đây là lý do AI generation không hoạt động trên một form chưa từng lưu.

### Quy tắc trạng thái và lỗi quan trọng

- `PUBLISHED` không tự gửi đề cho lớp khác; phân phối là workflow riêng.
- Các giá trị time window/duration/passing và mỗi question/options được validate lại ở server; validate JS chỉ là UX.
- Actor phải là Lecturer/Leader/Admin (`LecturerTestController:87`) và còn nằm trong scope quản lý đề.
- Không có endpoint delete test trong controller hiện tại; UI cũng không có nút xóa. “Toàn bộ CRUD” thực tế của Tests hiện chỉ có create/read/update, không có delete product workflow.

## 2. Upload và claim ảnh trong editor đề

Khi user chèn ảnh, form khai báo `data-image-url=/lecturer/tests/images` tại `lecturer-form.html:83–92`. Client gửi multipart từ editor:

```text
POST /lecturer/tests/images
Content-Type: multipart/form-data
file=<JPEG|PNG|WebP>
```

`LecturerTestController.uploadImage`, dòng 309–327, gọi `ExamImageStorageService.store(userId, file)` và trả URL `/uploads/exams/staged-{owner}-{timestamp}-{uuid}.{ext}`.

`ExamImageStorageService.store`, dòng 84–113:

- giới hạn 2 MB;
- kiểm MIME và magic bytes JPEG/PNG/WebP;
- ghi key staged qua `ObjectStorage`;
- staged URL public dùng `Cache-Control: private, no-store` tại `PublicUploadsController:117–121`.

Ảnh mới chỉ bền vững khi save đề. `ClaimSession.claimIn`, dòng 240–319, sanitize HTML, buộc owner/timestamp/key hợp lệ, copy sang `exams/{uuid}.ext`; rollback xóa durable copy, commit mới xóa staged source. Scheduler `cleanupExpiredStagedImages`, dòng 133–163, dọn staged quá 24 giờ mỗi giờ.

## 3. Chèn câu đã duyệt từ Question Bank vào đề đang sửa

Giảng viên bấm **“+ Chọn câu đã duyệt”** tại `lecturer-form.html:237`, nhập từ khóa ở dòng 251 rồi bấm **“Tìm”** dòng 253. `test-lecturer-form.js:246–267` gửi:

```text
GET /lecturer/tests/{testId}/question-bank/search?q=<text>
```

`LecturerTestQuestionBankController.search`, dòng 49–59, gọi picker service và trả JSON `AjaxResult`. Service resolve test có quyền quản lý → class của test → subject class, chỉ lấy `APPROVED`, search plain-text content/subject code trong memory và giới hạn **20** results (`ExamQuestionBankPickerService.java:59–72`); không có pagination. Test không có class không resolve được working subject nên picker trả 403.

Khi bấm một kết quả, `test-lecturer-form.js:202–244` gửi:

```text
POST /lecturer/tests/{testId}/question-bank/insert
Content-Type: application/json
Body: {"itemIds":[123]}
```

`LecturerTestQuestionBankController.insert`, dòng 66–83, gọi `LecturerExamService.insertFromBank`. Service dòng 257–290:

1. Lock đề và kiểm quyền quản lý.
2. Từ chối danh sách rỗng hoặc đề đã có student activity.
3. Load lại item theo id và bắt buộc vẫn `APPROVED`; browser không thể chèn draft bằng sửa JSON.
4. Copy content/explanation/options/correct flags thành snapshot `TestQuestion`/`TestOption` mới; writer sanitize HTML lần nữa và append sau câu hiện có, không giữ live-link làm đề đổi theo Question Bank.
5. Cập nhật `totalQuestions`, ghi `TestActivity.UPDATED`, trả số câu đã chèn.

API insert revalidate approved/same-subject tại thời điểm POST, nên item vừa archive/reject sau search không được chèn. Tuy nhiên `approvedSnapshotsByIds` bảo toàn thứ tự danh sách browser và **không deduplicate**: POST trực tiếp `{"itemIds":[123,123]}` sẽ append hai snapshot giống nhau nếu item vẫn approved. UI bình thường chọn mỗi card một lần; đây là guard thiếu ở API/service.

JS reload trang edit sau thành công để lấy bản authoritative. Mọi thay đổi form chưa save được cảnh báo vì reload sẽ làm mất chúng (`test-lecturer-form.js:215–229`).

`test-lecturer-form.js:176–295` render response bằng escaped plain-text preview (không đưa bank HTML thẳng vào dynamic DOM), chỉ mở picker ở edit mode, và POST một id cho mỗi click. Vì client guard không phải authorization, direct request vẫn đi qua same-subject/approved/locked checks ở service.

## 4. Sinh câu hỏi bằng AI: material → preview → chọn → persist

### UI và request generate

Nút **“Sinh câu hỏi bằng AI”** tại `lecturer-form.html:240` mở panel. User cung cấp:

| Field | Dòng | Quy tắc |
|---|---:|---|
| `file` | 272 | `.txt`, `.pdf`, `.docx`, không bắt buộc nếu có text |
| `text` | panel editor | nội dung paste, chọn một trong file/text |
| `count` | 276 | 1–20 |
| `type` | 282 | `MCQ` hoặc `MR` |
| `difficulty` | 286 | mức prompt |

`test-lecturer-ai-questions.js:107–150` tạo `FormData` và gửi:

```text
POST /lecturer/tests/{testId}/ai-questions/generate
Content-Type: multipart/form-data
file, text, count, type, difficulty
```

`AiQuestionGenerationController.generate`, dòng 55–88, lấy actor từ principal, gọi `AiQuestionGenerationService.generate`, rồi trả `{sessionId, questions}`. Kết quả chỉ là preview, chưa ghi `TestQuestion`.

### Chuỗi xử lý service và request AI

`AiQuestionGenerationService.generate`, dòng 80–117:

1. Kiểm actor quản lý test và test chưa có student activity.
2. Chuẩn hóa `count` 1–20, type chỉ `MCQ|MR`; khóa concurrent generation theo user trong node.
3. `DocumentTextExtractor` đọc material: tối đa 5 MB/30.000 ký tự; PDF tối đa 100 trang nhưng chỉ lấy 50 trang đầu; DOCX được preflight chống zip bomb (`DocumentTextExtractor:24–30,47–149`).
4. `AiQuestionPromptBuilder` nạp prompt runtime key `AI_QUESTION_GENERATOR`; nếu prompt DB thiếu thì dùng fallback (`AiQuestionPromptBuilder:20–83`).
5. Gọi `AiProviderClient.chat(systemPrompt, userPrompt, maxTokens)` với budget `400 × count` (`AiQuestionPromptBuilder:119–149`).

Contract bắt buộc AI trả đúng JSON object, không prose/Markdown:

```json
{
  "questions": [
    {
      "type": "MCQ",
      "content": "Nội dung câu hỏi",
      "explanation": "Giải thích",
      "options": [
        {"content": "Đáp án A", "correct": true},
        {"content": "Đáp án B", "correct": false}
      ]
    }
  ]
}
```

Runtime contract tại `AiQuestionPromptBuilder:34–69` yêu cầu đúng số câu, đúng type, 2–6 options, MCQ đúng 1 đáp án; MR có ít nhất 2 đúng và 1 sai; không HTML.

`AiQuestionResponseParser:51–136` giới hạn response 200.000 ký tự, question 2.000, option 1.000, explanation 4.000; kiểm count/type/correct constraints. Parse/validate fail thì service gọi AI lại đúng một lần với repair prompt (`AiQuestionGenerationService:103–111`, `AiQuestionPromptBuilder:102–117`). Lần hai fail thì HTTP lỗi, UI giữ form và toast.

Preview hợp lệ được lưu thành session DB qua `AiQuestionSessionStore`; TTL 10 phút, gắn `userId + testId`, chưa làm thay đổi đề.

Session hết hạn không chỉ bị từ chối khi confirm. `AiQuestionDraftRetentionWorker`, `src/main/java/com/ksh/features/ai/questiongen/AiQuestionDraftRetentionWorker.java:22-88`, là `SmartLifecycle` auto-start (có thể tắt bằng `app.ai.question-draft.retention.worker-enabled=false`), mặc định đợi 5 phút rồi sweep mỗi giờ. Nó gọi `AiQuestionDraftMaintenanceService.cleanupExpired`; service xóa `expires_at <= UTC now` theo batch mặc định 500, tối đa 20 batch/sweep, ghi metrics và không đụng `TestQuestion` đã confirm (`AiQuestionDraftMaintenanceService.java:36-93`). Worker dùng private single-thread executor, không dùng Spring `@Scheduled`; vì vậy catalog scheduler thuần annotation sẽ không tự thấy luồng retention này.

### User xác nhận và backend persist

User tick các preview rồi bấm **“Chèn câu đã chọn”** tại `lecturer-form.html:300`. JS dòng 153–193 gửi:

```text
POST /lecturer/tests/{testId}/ai-questions/confirm
Content-Type: application/json
Body: {"sessionId":"...", "selectedIndexes":[0,2,3]}
```

`AiQuestionGenerationController.confirm`, dòng 90–112, gọi service. `AiQuestionGenerationService.confirm`, dòng 123–151:

1. Lock test và draft session.
2. Kiểm session thuộc đúng user/test, chưa quá TTL, chưa consumed.
3. Kiểm lại đề chưa có activity của học sinh.
4. Validate index/deduplicate, append đúng các question đã chọn thành entity snapshot.
5. Cập nhật `totalQuestions`, ghi activity và atomically consume session.

Controller trả số inserted và edit URL; JS redirect/reload để render state DB. AI không thể trực tiếp “ghi câu hỏi” chỉ bằng response generate; persistence luôn cần request confirm có CSRF và authorization.

## 5. Phân phối một đề đã publish tới nhiều lớp

Icon **“Phân phối”** ở `lecturer-list.html:75` mở:

```text
GET /lecturer/tests/{id}/distribute
```

`LecturerTestController.distributionForm`, dòng 164–172, gọi `LecturerExamService.distributionView`; service dòng 297–314 chỉ trả các lớp actor được quản lý. Form `lecturer-distribute.html:29–55` gửi checkbox `classIds`:

```text
POST /lecturer/tests/{id}/distribute
Content-Type: application/x-www-form-urlencoded
classIds=1&classIds=2&...
```

`LecturerTestController.distribute`, dòng 175–195, gọi `distributePublished`, flash kết quả rồi redirect list.

`LecturerExamService.distributePublished`, dòng 322–379:

- source phải `PUBLISHED`, không phải `PRACTICE`, có question snapshot;
- deduplicate class ids;
- mỗi class phải ACTIVE, actor quản lý được, cùng subject, không phải chính source class;
- class đã có đề cùng title sẽ bị bỏ qua để chống phát tán trùng;
- tạo một `Test` PUBLISHED độc lập cho từng lớp, deep-copy toàn bộ questions/options và ghi activity.

Do là snapshot, sửa source sau phân phối không tự đồng bộ các bản ở lớp.

## 5a. Preview giảng viên và tab AJAX

Preview action là:

```text
GET /lecturer/tests/{id}/preview
```

`LecturerTestController.preview`, dòng 202–208 → `LecturerExamService.previewAsStudent` (`170–174`) chỉ load owned test và `TakeViewBuilder.buildPreview`; không tạo `TestAttempt`, `TestResponse` hoặc `TestActivity`. `templates/tests/lecturer-preview.html:11–131` render initial `preview` model (title/window/media/questions), dùng `test-take.js` chỉ để điều hướng question + lựa chọn radio/checkbox cục bộ. Nút submit bị disabled, template không đặt attempt id/API URL, nên không có heartbeat/submit/persistence; link quay lại `GET .../edit?tab=info`.

`static/js/test-detail-tabs.js:42–218` giữ `#tabPanel` client state. Click tab/pager hoặc submit search bài nộp (`.sb-search`) fetch chính URL SSR `GET /lecturer/tests/{id}/edit?tab=...` với `X-Requested-With`, parse response, chỉ swap `#tabPanel`, remount form/monitor và `pushState`. Nó abort request cũ, dùng dirty-form confirmation, teardown monitor trước swap và fall back full navigation nếu response/error không hợp lệ. Các GET này chỉ query model lazy theo controller; tab switching không persist test.

## 6. Học sinh tìm đề, bắt đầu hoặc resume

Có hai route shell nhưng chung service:

| Màn hình | Request | Controller |
|---|---|---|
| Kho đề của tôi | `GET /my/tests?page=n` | `StudentTestController.list`, dòng 58–64 |
| Đề theo lớp | `GET /my/classes/{classId}/tests?page=n` | `StudentClassTestsController.list`, dòng 64–75 |
| Chi tiết global | `GET /my/tests/{id}` | `StudentTestController.detail`, dòng 67–72 |
| Chi tiết trong lớp | `GET /my/classes/{classId}/tests/{testId}` | `StudentClassTestsController.detail`, dòng 78–85 |

`templates/student/class-tests.html:14–83` là initial SSR model `ClassTestsView`: class chrome, `view.query()` và một `Page<ExamListRow>`. Search form native gửi `GET /my/classes/{classId}/tests?q=...`; pager giữ `q`. `TestCatalogService.listClassTests` kiểm ACTIVE enrollment + class live trước, query `TestRepository.findByClassIdAndStatusAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc`, rồi batch đọc attempt để row chỉ hiển thị `lastAttemptStatus`, best percent và completed attempt id (`TestCatalogService.java:99–126`). Template không có JS/persistence: row chỉ POST start với CSRF khi chưa attempt, còn in-progress/detail/result là GET links.

Ở `tests/detail.html:140–144`, nút **“Bắt đầu làm bài”** submit form có CSRF:

```text
POST /my/tests/{testId}/start
```

hoặc trong class shell:

```text
POST /my/classes/{classId}/tests/{testId}/start
```

Controllers tại `StudentTestController:76–86` và `StudentClassTestsController:88–98` lấy `studentId` từ principal, gọi `TestAttemptService.startOrResume` hoặc `startOrResumeInClass`, rồi redirect `.../{testId}/take`.

`TestAttemptService.startOrResume`, dòng 101–130:

1. Load test và kiểm published/accessibility; class route còn chứng minh ACTIVE enrollment và test thuộc class.
2. Kiểm `startAt/endAt`, status và question availability (`validateStartable`, dòng 251–268).
3. Với đề thường, tìm open attempt hiện có và resume; không tạo attempt thứ hai.
4. Nếu chưa có, tạo `TestAttempt` status `IN_PROGRESS`, `startedAt=now`, deadline theo duration/time mode.

Link **“Tiếp tục”** trong detail đi thẳng `GET .../take`; controller gọi `resumeForTake` (`StudentTestController:90–101`). Service dòng 142–161 vẫn kiểm owner/test/status/deadline, nên sửa URL không mở attempt người khác.

### Trace handler-level: start/take/result/review cho hai student shell

Mọi handler dưới đây dùng `PREAUTH_STUDENT`, id học sinh từ principal; không nhận `userId` từ request. `TestAttemptService.result/review` đọc `TestAttemptRepository` qua `TestAccessResolver.requireOwnAttempt`, bắt buộc `{attemptId}` thuộc actor **và** `attempt.testId == {testId}`, rồi load `TestRepository` và builders đọc question/options/responses. Chúng không thay đổi DB.

| Handler | Route / initial service work | DB state, guard | Response / downstream UI |
|---|---|---|---|
| `StudentTestController.start` | `POST /my/tests/{id}/start` → `startOrResume(id, userId)` | `requireAttemptableForUpdate` load/authorize published, enrolled test; query attempts user+test. Non-practice có completed attempt thì reject; open attempt được reuse, nếu không `ensureStartable` window/questions rồi insert `TestAttempt(IN_PROGRESS)`. | 302 `/my/tests/{id}/take`; unavailable flash và 302 detail. Take page sau đó sends heartbeat/submit APIs. |
| `StudentTestController.take` | `GET /my/tests/{id}/take` → `resumeForTake` | Same authoritative test/access + attempts query. Không tạo class-exam khi GET: chỉ return open attempt; non-practice absent open trả “hãy bấm Bắt đầu”. Chỉ practice legacy GET có thể insert new attempt. | Model `take`, view `tests/take.html`; unavailable flash → detail. |
| `StudentTestController.review` | `GET /my/tests/{id}/review/{attemptId}` → `attemptService.review` | Own-attempt + test-path pairing query, then result builder reads snapshot questions/options/responses; no mutation. | Model `review`, `tests/review.html`; mismatched/stolen/missing attempt is 404-style resolver failure, no UI data leak. |
| `StudentClassTestsController.start` | `POST /my/classes/{classId}/tests/{testId}/start` → `startOrResumeInClass` | Same attemptable-for-update checks plus service verifies `test.classId == classId` before reuse/insert; class accessibility is enforced in test resolver. | 302 class-scoped take; unavailable flash → class detail. |
| `StudentClassTestsController.take` | `GET /my/classes/{classId}/tests/{testId}/take` → `resumeForTake`, then controller `requireClassScope(classId, take.classId)` | Reads only; GET cannot start ordinary class test. A test whose returned class id differs throws NotFound after service lookup. | `tests/take.html` model `take` + `classScopeId`; unavailable flash → class detail. |
| `StudentClassTestsController.result` | `GET /my/classes/{classId}/tests/{testId}/result/{attemptId}` → `attemptService.result`, then `requireClassScope` | Own attempt/test pairing query + result builder; controller denies mismatch between route class and DTO class; no mutation. | model `result`, `classScopeId`, view `tests/result.html`. |
| `StudentClassTestsController.review` | `GET /my/classes/{classId}/tests/{testId}/review/{attemptId}` → `attemptService.review`, then `requireClassScope` | Same read-only owner/test/class triplet guard and review builder queries; no mutation. | model `review`, `classScopeId`, view `tests/review.html`; invalid path does not fall back to global route. |

## 7. Học sinh chọn đáp án, heartbeat, nộp và chấm điểm

`tests/take.html:19–26` gắn các URL authoritative lên form; radio/checkbox ở dòng 82–103, nút **“Nộp bài”** dòng 110. `test-take.js:34–49` gom mỗi question thành danh sách `optionIds`.

### Heartbeat

Cứ 30 giây, JS dòng 205–211 gửi:

```text
POST /api/tests/attempts/{attemptId}/heartbeat
Body: {}
```

`TestApiController.heartbeat`, dòng 71–84, gọi `TestAttemptService.heartbeat` (dòng 168–176). Service chỉ cập nhật `lastActivityAt` cho đúng owner và attempt còn open; heartbeat không lưu đáp án.

### Submit

Submit tay ở `test-take.js:168–176` hoặc countdown về 0 ở dòng 179–194 đều gọi:

```text
POST /api/tests/attempts/{attemptId}/submit
Content-Type: application/json
Body: {"answers":[{"questionId":10,"optionIds":[101]}]}
```

`TestApiController.submit`, dòng 51–67, truyền principal student id và DTO sang `TestAttemptService.submit`.

`TestAttemptService.submit`, dòng 183–217:

1. Lock attempt theo id; buộc owner đúng principal.
2. Attempt đã đóng trả kết quả cũ (idempotent), không chấm hai lần.
3. Load bộ questions/options authoritative từ DB; option id lạ/thuộc câu khác không trở thành đáp án đúng.
4. Index answer theo question, tạo/cập nhật một `TestResponse` cho từng câu.
5. `GradingService.grade`, dòng 40–53, so sánh exact set option đã chọn với exact set correct. Không partial credit cho MR.
6. Cộng `score`, `earnedPoints`, `correctAnswers`; nếu quá deadline đặt `TIMED_OUT`, ngược lại `SUBMITTED`.
7. Set submitted time/time spent và lưu attempt.

Controller trả JSON; JS redirect đến `GET .../result/{attemptId}` (`test-take.js:149–165`). `StudentTestController.result:105–110`/class controller `117–125` kiểm owner/scope rồi render `tests/result.html`. Link **“Xem lại bài làm”** ở `result.html:41` gọi GET review; review service chỉ trả dữ liệu của attempt được phép xem.

Lưu ý quan trọng: lựa chọn chỉ nằm trong DOM cho đến lúc submit; mất mạng trước submit đồng nghĩa heartbeat có thể còn nhưng đáp án chưa được server lưu.

## 8. Tạo bài practice ngẫu nhiên từ đề được phép truy cập

Từ `tests/list.html:21`, user bấm **“Tạo bài luyện tập”**:

```text
GET /my/tests/practice/new
```

`StudentPracticeController.practiceForm` (`:46–51`) là GET initial model của màn này; class gate chỉ là `isAuthenticated()` (không phải exact STUDENT). Nó gọi `PracticeTestService.sources(principalId)`: `TestAccessQueries.accessibleExams` query các test accessible của actor, service loại `isPractice`, map source-test options, dedupe `classId` rồi `ClassRepository.findAllById` để map source-class options. Không `Test`, attempt, question hay session nào được insert. Response `tests/practice-new.html` mang `practice` options; `test-practice.js` chỉ toggle field DOM như mô tả dưới. Guard nguồn sâu hơn vẫn xảy ra khi POST create, nên GET form không cấp quyền sử dụng test/class forged.

Form `practice-new.html:22–49` gửi `sourceClassId`, `sourceTestId`, `count` (1–50):

```text
POST /my/tests/practice
```

`StudentPracticeController.createPractice`, dòng 55–70, gọi `PracticeTestService.create` rồi redirect thẳng đến `/my/tests/{newId}/take`.

`PracticeTestService.create`, dòng 79–120:

1. Resolve nguồn mà actor thực sự được truy cập; nếu chọn class thì bắt buộc ACTIVE enrollment.
2. Lấy question pool của source test; ép count hợp lệ.
3. `Collections.shuffle(pool)` rồi lấy N câu đầu.
4. Tạo `Test` mới owner là learner, type `PRACTICE`, status `PUBLISHED`.
5. Deep-copy question/options thành snapshot; practice không làm thay đổi source.
6. `TestAttemptService` tạo attempt và UI dùng cùng take/submit/grading workflow ở mục 7.

Practice có thể tạo nhiều bản; quy tắc “một attempt cho đề thường” không biến các practice test độc lập thành cùng một attempt.

`static/js/test-practice.js:16–29` chỉ giữ client state của `practice-new.html`: chọn `sourceTestId` thì clear/disable class selector; chọn class clear test selector. Native form vẫn POST cả hai field nếu bị forged; server ưu tiên `sourceTestId` (`PracticeTestService.resolveSources`, dòng 107–120), access-check test/class pool và transaction deep-copy snapshot. Vì vậy script không phải authorization hay persistence.

## 9. Readiness

`GET /my/tests/readiness` được `StudentPracticeController.readiness`, dòng 74–79, chuyển sang `ReadinessService.compute`.

Service dòng 42–67 lấy các graded exams actor được truy cập, với mỗi đề dùng best score percent; đề chưa làm đóng góp 0, sau đó tính mean và map band readiness. Đây chỉ là DB aggregation, không gọi AI. UI `tests/readiness.html` render band và link quay lại ở dòng 43.

## 10. Giảng viên monitor, xem bài nộp và review

Icon **“Theo dõi”** tại `lecturer-list.html:76` mở tab monitor qua `GET /lecturer/tests/{id}/edit?tab=monitor`. `LecturerTestController.editForm:217–248` lazy-load monitor/submissions/history theo tab, tránh query tất cả cùng lúc.

Các request liên quan:

| Thao tác | HTTP | Controller → service |
|---|---|---|
| Legacy monitor URL | `GET /lecturer/tests/{id}/monitor` | `LecturerMonitorController:59–62` redirect tab monitor |
| Poll snapshot | `GET /lecturer/tests/{id}/monitor/data` | controller 65–70 → monitor snapshot service |
| Legacy submissions | `GET /lecturer/tests/{id}/submissions` | controller 76–82 → redirect/load submissions |
| Lọc submissions | `GET /lecturer/tests/{id}/edit?tab=submissions&q=&page=` | form `lecturer-form.html:353–357` → edit controller |
| Review attempt | `GET /lecturer/tests/{id}/review/{attemptId}` | controller 90–100 → review service |
| Lịch sử | `GET /lecturer/tests/{id}/edit?tab=history&page=` | links `lecturer-form.html:427–431` |

Monitor là polling read-only: snapshot tổng hợp `IN_PROGRESS`, submitted/timed-out và activity gần nhất; không dùng WebSocket và không thay đổi attempt. Mọi service đều kiểm actor quản lý test; attempt review còn phải thuộc chính test trên URL.

### Trace handler-level: `LecturerMonitorController`

Class gate của controller là `PREAUTH_LECTURER_OR_ABOVE` (`LecturerMonitorController:39–40`); management authorization sâu hơn do `TestAccessResolver.requireManageable` thực hiện, không do browser tab quyết định.

| Handler | Route / query or mutation | Response, UI và guards |
|---|---|---|
| `LecturerMonitorController.monitor` | `GET /lecturer/tests/{id}/monitor`; handler không gọi service/repository, không validate id và không mutate. | 302 `/lecturer/tests/{id}/edit?tab=monitor`; destination edit handler mới load authorized form/monitor model. Legacy URL không thể bypass test access. |
| `LecturerMonitorController.monitorData` | `GET /lecturer/tests/{id}/monitor/data` → `ExamMonitorService.snapshotFor`. Service `requireManageable` load/authorize `Test`, query `TestAttemptRepository.findByTestId`, ACTIVE enrollment rows và users to calculate submitted/in-progress/active/remaining; no write. | JSON `MonitorSnapshot` cho `test-monitor.js`; inaccessible test fails resolver, stale/failed fetch only leaves client UI/retry, never changes DB. |
| `LecturerMonitorController.submissions` | `GET /lecturer/tests/{id}/submissions?q=`; this legacy handler has no service/repository query and no mutation; it URL-encodes nonblank `q`. | 302 `/lecturer/tests/{id}/edit?tab=submissions&q=...`; edit-tab initial GET then calls `ExamMonitorService.submissionsFor` (manageable-test check, `findByTestId`, in-memory name filter/paginate) to render panel. |
| `LecturerMonitorController.review` | `GET /lecturer/tests/{id}/review/{attemptId}` → `lecturerReview`. Service loads manageable test, requires attempt belongs to that test, resolves student name and `AttemptResultBuilder` reads snapshot response data; no mutation. Controller optionally queries `ClassesService.getViewable` only to populate sidebar, swallowing `AccessDeniedException` because exam ownership remains authoritative. | model `review`, optional class chrome, view `tests/review.html`; wrong test/attempt or non-manager cannot view student work. |

`static/js/test-monitor.js:48–159` mount chỉ khi initial/tab-swapped panel có `#mnPage`, lấy `data-poll-url`, then fetch `GET /lecturer/tests/{id}/monitor/data` ngay và mỗi 30 giây. Nó cập nhật count/student rows bằng `textContent`, resync countdown từ server and abort/ignore stale poll; lỗi poll không đổi DB và đợt sau retry. `test-detail-tabs.js` gọi teardown khi rời tab để clear two intervals/abort request, nên tab AJAX không leak polling loop.

## Bản đồ repository/entity chính

| Dữ liệu | Repository/service ghi | State đáng chú ý |
|---|---|---|
| `Test` | `TestRepository`, `LecturerExamService`, `PracticeTestService` | `DRAFT`/`PUBLISHED`, type thường/practice, class/source snapshot |
| `TestQuestion`, `TestOption` | question/option repositories và writers | snapshot nội dung/correct flag |
| `TestAttempt` | `TestAttemptRepository` qua `TestAttemptService` | `IN_PROGRESS` → `SUBMITTED` hoặc `TIMED_OUT` |
| `TestResponse` | `TestResponseRepository` lúc submit | selected option ids + correct/points |
| `TestActivity` | activity service/repository | audit create/update/publish/insert/distribute |
| AI preview session | `AiQuestionSessionStore` | active tối đa 10 phút → consumed khi confirm |

## Security và các điểm không nên suy diễn

- Tất cả student test/API routes dùng `PREAUTH_STUDENT`; lecturer routes dùng `PREAUTH_LECTURER_OR_ABOVE`.
- Principal id luôn đến từ Spring Security, không từ JSON/form.
- CSRF áp dụng cho POST form/fetch cùng origin; JS lấy token từ meta chung.
- Question Bank và AI đều chèn snapshot, không có live synchronization.
- AI response không được tin trực tiếp: parser validate, session ràng owner/test/TTL, confirm mới persist.
- Heartbeat không autosave answer.
- Không có thao tác xóa test trong source/controller/UI hiện tại.
