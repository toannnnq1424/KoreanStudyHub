# Workflow audit: Question Bank CRUD, review, bulk, Excel import và liên kết AI

Workflow **random đề từ bộ chung** đã được audit chi tiết tại `docs/audit/workflows/QUESTION_BANK_RANDOM_TEST.md`. Tài liệu này bao phủ các workflow Question Bank còn lại và chỉ tham chiếu random để tránh lặp sai khác.

## 1. Mở workspace, chọn môn, tìm và xem câu hỏi

Giảng viên/Leader vào:

```text
GET /lecturer/question-bank?subjectId=<id>&status=<status>&q=<text>
```

`LecturerQuestionBankController.list`, dòng 58–90:

1. Dùng principal id/role để lấy danh sách subject actor được truy cập qua access policy; không có subject thì render empty state, không gọi query item.
2. Chọn `subjectId` từ request hoặc subject đầu tiên; `requireSubject` kiểm active + scope trước khi đọc dữ liệu.
3. `workspace` tải **toàn bộ** item của subject bằng `QuestionBankItemRepository.findBySubjectIdOrderByUpdatedAtDescIdDesc`, lọc `q` trong memory, rồi chỉ group `APPROVED` và `REVIEW` theo chapter/bài. `list` cũng tải lại toàn bộ và lọc `status`/contributor/query trong memory; không có `Page`, `page` request parameter hay SQL keyword predicate trong workflow này.
4. Nạp toàn bộ `LessonTemplate` allowed để form/import/random chọn bài, chapter options của subject đang mở và ACTIVE class cùng subject đủ scope để random đề có thể phân phối ngay.

Search form thực ở `templates/questionbank/list.html:104–109`; chọn subject bằng link ở dòng 90–95. Card câu hỏi mở detail qua link dòng 192/218:

```text
GET /lecturer/question-bank/{id}
```

`LecturerQuestionBankController.detail`, dòng 159–170, gọi `QuestionBankItemService.detail`. Item phải ở subject active và actor phải trong scope; URL id sai bị flash rồi redirect workspace. Với `LECTURER`/`ADMIN`, policy hiện catalog-wide; chỉ `LEADER` bị giới hạn theo các subject được phân công (`QuestionBankAccessPolicy.java:40–53`).

### Client state của workspace random

`static/js/question-bank-workspace.js:4–26` không gọi HTTP hay ghi DB. Khi initial GET đã render form random, script đọc radio `scope`; chỉ select target của `CHAPTER` **hoặc** `LESSON` được hiện/enable, target không active bị `disabled`. Vì hai control cùng tên `lessonTemplateId`, guard DOM này bảo đảm native `POST /lecturer/question-bank/generate-test` chỉ gửi một target; server vẫn normalize scope và xác thực template/subject (`QuestionBankTestGenerationService.java:91–124`). Đổi scope về `SUBJECT` làm cả hai select disabled và không có target được persist.

## 2. Thêm câu: lưu nháp hoặc gửi Leader duyệt

### UI và request

Nút **“Thêm 1 câu”** tại `questionbank/list.html:26` mở:

```text
GET /lecturer/question-bank/new?subjectId=<id>
```

`LecturerQuestionBankController.createForm`, dòng 92–101, nạp subjects/lessons được phép và trả `questionbank/form.html`.

Form bắt đầu ở `form.html:35–38`, các field:

| Field | Dòng | Ý nghĩa |
|---|---:|---|
| `subjectId` | 47–54 | mã môn; khi edit bị khóa và gửi hidden |
| `questionType` | 58–61 | `MCQ` hoặc `MR` |
| `lessonTemplateId` | 65–78 | bài thuộc chính subject |
| `content` | 85–87 | nội dung đã sanitize |
| `explanation` | 92–94 | giải thích tùy chọn |
| `options[].content` | 106–118 | nội dung đáp án |
| `options[].correct` | 109 | cờ đúng/sai |
| `workflowAction` | 125–126 | `DRAFT` hoặc `REVIEW` tùy nút bấm |

Hai nút submit cùng endpoint form thường:

```text
POST /lecturer/question-bank
Content-Type: application/x-www-form-urlencoded
workflowAction=DRAFT|REVIEW
```

`LecturerQuestionBankController.create`, dòng 134–157, bind `@Valid @ModelAttribute QuestionBankItemForm`, gọi `QuestionBankItemService.create/save`. Binding error render lại form cùng lỗi field; thành công flash message và redirect detail/list theo controller.

### Client state của form câu hỏi

Initial GET render HTML server-side rồi `static/js/question-bank-form.js:4–99` mount Quill cho content/explanation vào các textarea backing form (`data-qb-editor`), hydrate lại HTML `data-initial`, và đồng bộ mỗi `text-change` vào textarea thật. Script không upload ảnh, không fetch và không persist độc lập. Nó cũng:

- disable option bài học không thuộc subject đang chọn và clear selection invalid (`74–92`);
- tô đáp án đúng; nếu client đang chọn `MCQ`, tick một đáp án sẽ bỏ tick các đáp án khác (`32–72`).

Đây chỉ là UX. Một browser bỏ JS vẫn POST các field form; service quyết định subject/lesson, sanitize HTML và kiểm MCQ một đáp án/ít nhất hai options. Với `MR`, script không ép số đáp án đúng, đúng như validation server tối thiểu một đáp án.

### Service và entity transition

`QuestionBankItemService.save`, dòng 232–266:

1. `requireActor` dùng user DB và role thật; `requireSubject` dùng `QuestionBankAccessPolicy`.
2. `requireLesson`, dòng 405–413, buộc lesson tồn tại và thuộc subject.
3. Normalize type/content/explanation, validate options tại dòng 483–498: tối thiểu 2 options, ít nhất 1 đúng; MCQ đúng chính xác 1.
4. Tạo `QuestionBankItem`, `contributorId = actor.id`, rồi resolve action: chỉ string `REVIEW` mới tạo state `REVIEW`; mọi action khác (kể cả giá trị sửa tay) bị normalize thành `DRAFT`.
5. Lưu item để có id, sau đó ghi từng `QuestionBankOption` theo thứ tự.

Các trạng thái entity được khai báo tại `QuestionBankItem:22–29`:

```text
DRAFT → REVIEW → APPROVED
               ↘ REJECTED
any eligible state → ARCHIVED → previous state (unarchive)
```

Không có request gửi `ownerId`, `reviewerId` hoặc workflow status tùy ý; các giá trị này được service đặt từ principal/action.

## 3. Sửa câu và gửi duyệt lại

Detail/list chỉ hiện edit khi policy cho phép. Click edit:

```text
GET /lecturer/question-bank/{id}/edit
```

`LecturerQuestionBankController.editForm`, dòng 173–188, gọi service kiểm `canEdit`, load item/options rồi trả lại cùng `form.html`.

Submit:

```text
POST /lecturer/question-bank/{id}/edit
workflowAction=DRAFT|REVIEW
```

`LecturerQuestionBankController.update`, dòng 190–215, chuyển form sang `QuestionBankItemService.update/save`. Service:

- owner được sửa item của mình; curator theo service policy (`LEADER` đúng subject hoặc `ADMIN`) có quyền rộng hơn theo `canEdit`, dòng 344–354; tuy nhiên UI review route vẫn bắt buộc đúng role `LEADER`;
- không cho đổi subject bằng hidden-field tampering;
- item `APPROVED` hoặc `ARCHIVED` không sửa qua workflow này;
- validate lại toàn bộ question/options;
- xóa option rows cũ rồi insert snapshot options mới;
- transition về `DRAFT` hoặc `REVIEW` và clear `reviewedBy`, `reviewNote`, `reviewedAt`, `approvedAt`; item `REJECTED` vì vậy có thể được sửa rồi gửi lại.

Nếu Leader reject, người tạo có thể sửa rejected item rồi gửi `REVIEW` lại. Không có endpoint hard-delete Question Bank item trong source hiện tại; **Archive** là lifecycle removal có thể đảo ngược.

## 4. Leader mở inbox và duyệt một câu

Nút **“Quản lý & duyệt”** ở `questionbank/list.html:32` mở:

```text
GET /leader/question-bank?status=REVIEW|APPROVED|REJECTED|ARCHIVED|ALL
    &subjectId=<id>&contributorId=<id>&q=<text>
```

`LeaderQuestionBankController.manage`, dòng 43–70, bắt buộc role LEADER (`:26`), lọc subjects trong scope leader, contributors, status và query. Tab/filter UI nằm ở `subject-review.html:39–95`.

### Approve

Nút **“Duyệt”** trong row/modal (`subject-review.html:209–218`, `278`) submit:

```text
POST /leader/question-bank/{id}/approve
```

`LeaderQuestionBankController.approve`, dòng 72–79, gọi `QuestionBankReviewService.approve`. Service dòng 37–47 load item, kiểm Leader có quyền subject và state hiện tại là `REVIEW`, rồi transition `REVIEW → APPROVED`, set `reviewedBy`, `reviewedAt`, `approvedAt` và clear note. Repository hiện không có `find…ForUpdate`/`@Lock` cho `QuestionBankItem`: hai thao tác review đồng thời có thể cùng đọc `REVIEW`; state cuối/audit metadata theo lần save cuối.

### Reject/trả lại

```text
POST /leader/question-bank/{id}/reject
note=<ghi chú tùy chọn>
```

Controller dòng 81–90 → `QuestionBankReviewService.reject`, dòng 49–63. Chỉ `REVIEW` được reject; entity thành `REJECTED`, lưu `reviewedBy`/time/note. `note` hiện chỉ trim-to-null, không có giới hạn độ dài ở service; template render note bằng `th:text`, không raw HTML.

### Archive và unarchive

Row/modal tại `subject-review.html:227–243`, `280–281` gửi:

```text
POST /leader/question-bank/{id}/archive
POST /leader/question-bank/{id}/unarchive
```

Controller dòng 92–110 → service dòng 65–90. Archive ghi nhớ trạng thái trước rồi chuyển `ARCHIVED`; unarchive khôi phục trạng thái trước, nếu metadata cũ không hợp lệ thì fallback `REVIEW`. Item archived không xuất hiện trong picker/random candidates.

Mỗi controller giữ lại filters (`status`, `subjectId`, `contributorId`, `q`) khi redirect, nên UI quay về đúng inbox thay vì mất ngữ cảnh. Không có pagination trong màn này.

## 5. Leader bulk approve/reject/archive/unarchive

Bulk form bắt đầu `subject-review.html:109`; checkbox row dùng `name=itemIds` ở dòng 153. Nút ở dòng 123–131 đổi `formaction` tương ứng:

```text
POST /leader/question-bank/bulk/approve
POST /leader/question-bank/bulk/reject       note=<text>
POST /leader/question-bank/bulk/archive
POST /leader/question-bank/bulk/unarchive
```

`LeaderQuestionBankBulkController`, dòng 46–102, lấy principal Leader, truyền danh sách ids/note sang `QuestionBankReviewService.bulk*`, flash thống kê rồi redirect giữ filters.

`QuestionBankReviewService`, dòng 102–173:

1. Loại id null và deduplicate nhưng giữ thứ tự.
2. Chạy cùng state/subject authorization như single action cho từng id.
3. Bắt `RuntimeException` theo item: bulk method không có transaction bao ngoài; các repository `find/save` của từng lượt thực thi độc lập. Vì thế đây là partial success thực tế, item sai state/scope được tính `skipped` thay vì làm UI giả rằng toàn batch thành công.
4. Trả số success/skipped cho flash message.

Bulk không phải cách vượt state machine: ví dụ chọn một row `APPROVED` trong bulk approve sẽ bị skip.

### Client state modal/review

`static/js/question-bank-detail.js:1–172` không fetch detail API và không thay đổi DB. Initial GET của inbox đã đặt content/options/explanation trong `<template data-detail-for>` trên từng row. Click **Chi tiết** clone template vào modal; HTML question/options được đưa bằng `innerHTML` chỉ từ content server đã sanitize, còn review note dùng `textContent` (`111–145`). Script gắn nút footer vào các form POST server-rendered qua HTML5 `form="rowApprove_{id}"`/reject/archive/unarchive và chỉ show theo flag row (`78–100`); CSRF, action/path và authorization vẫn thuộc form/controller.

Bulk select-all, count và disabled state cũng chỉ là DOM (`17–59`). Kể cả client tự enable button hay thay id checkbox, `QuestionBankReviewService` vẫn dedupe, kiểm state + subject scope từng item và trả partial success như mô tả ở trên.

### Trace handler-level: quyết định Leader và bulk

Các trace sau phân biệt rõ HTTP handler với state DB; tất cả route trong bảng bị chặn ngay ở class bằng `hasRole('LEADER')` (`LeaderQuestionBankController:25–26`, `LeaderQuestionBankBulkController:36–37`), actor id luôn lấy từ principal và POST cần CSRF.

| Handler | Route, input và thao tác thực | Query/mutation, guard và state | Response / UI downstream |
|---|---|---|---|
| `LeaderQuestionBankController.reject` | `POST /leader/question-bank/{id}/reject`, `note` và `ReviewFilters` | `QuestionBankReviewService.reject` đọc `UserRepository` để require curator, `QuestionBankItemRepository` để load item trong subject Leader; chỉ `REVIEW` → `REJECTED`, ghi `reviewedBy/reviewedAt/reviewNote` rồi `save`. Item thiếu/sai subject/sai state ném lỗi, không có mutation. | flash “đã từ chối”, redirect `/leader/question-bank` giữ `subjectId/status/contributorId/q`; SSR inbox reload từ DB. |
| `LeaderQuestionBankController.archive` | `POST /leader/question-bank/{id}/archive`, `note`, filters | Cùng curator/item lookup; từ mọi state **trừ** `ARCHIVED`, entity nhớ previous workflow state, chuyển `ARCHIVED`, ghi auditor/note/time và repository `save`. Archive lần hai bị validation error. | flash archive rồi redirect giữ filter; item biến mất khỏi candidate picker/random ở request sau. |
| `LeaderQuestionBankController.unarchive` | `POST /leader/question-bank/{id}/unarchive`, filters | Cùng scope lookup; chỉ `ARCHIVED` hợp lệ. Entity khôi phục remembered state (metadata legacy thiếu thì `REVIEW`), stamp restoring reviewer/time, `save`; state khác lỗi. | flash unarchive + redirect giữ filter; UI sau redirect thể hiện state khôi phục. |
| `LeaderQuestionBankBulkController.bulkApprove` | `POST /leader/question-bank/bulk/approve`, repeated `itemIds`, filters | Danh sách null/rỗng chỉ flash error, không query/write. Còn lại `approveAll` loại null/dedupe theo thứ tự rồi gọi transition `REVIEW` → `APPROVED`/`itemRepository.save` cho từng id; missing, out-of-scope hoặc invalid state được catch thành skipped. | flash số approved/skipped và redirect inbox giữ filters; không có JSON/partial DOM update. |
| `LeaderQuestionBankBulkController.bulkReject` | `POST /leader/question-bank/bulk/reject`, `itemIds`, optional `note`, filters | Empty như trên; `rejectAll` dùng một note trim-to-null cho từng item và chỉ persist `REVIEW` → `REJECTED`; từng failure skipped. | flash rejected/skipped, redirect SSR inbox. |
| `LeaderQuestionBankBulkController.bulkArchive` | `POST /leader/question-bank/bulk/archive`, `itemIds`, optional `note`, filters | Empty như trên; `archiveAll` chỉ persist item chưa archived, nhớ previous state; failure per-item skipped. | flash archived/skipped, redirect SSR inbox. |
| `LeaderQuestionBankBulkController.bulkUnarchive` | `POST /leader/question-bank/bulk/unarchive`, `itemIds`, filters | Empty như trên; `unarchiveAll` chỉ persist archived items về remembered state/fallback `REVIEW`; failure per-item skipped. | flash unarchived/skipped, redirect SSR inbox. |

Bulk service không có transaction bao ngoài. Lời gọi trực tiếp sang method `@Transactional` cùng bean không tạo một transaction batch mới; vì vậy khi nhiều request/row tranh chấp, contract UI chỉ là đếm kết quả cuối mỗi item, không phải compare-and-set hoặc all-or-nothing.

## 6. Download Excel template

Nút **“Tải file mẫu”** tại `questionbank/list.html:28` gửi:

```text
GET /lecturer/question-bank/import/template?subjectId=<id>
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

`QuestionBankImportController.template`, dòng 57–77, kiểm actor có quyền subject, gọi template service và trả bytes XLSX với `Content-Disposition: attachment`. File mẫu mang subject context để giảm nhập nhầm, nhưng confirm vẫn kiểm lại subject server-side.

## 7. Excel import: chọn file → preview toàn bộ → confirm atomic

### UI preview

Input file ẩn `.xlsx/.xls` ở `questionbank/list.html:41`, lesson target ở dòng 44–53. `static/js/question-bank-import.js:26–120` bắt file change, tạo `FormData`:

```text
POST /lecturer/question-bank/import/preview
Content-Type: multipart/form-data
file=<workbook>
subjectId=<selected subject>
lessonTemplateId=<selected lesson>
```

`QuestionBankImportController.preview`, dòng 79–100, gọi `QuestionBankImportService.preview`; trả session id, row previews và validation errors. JS render mỗi row ở dòng 123–145 và chỉ enable **“Xác nhận import”** (`list.html:64`) khi toàn bộ preview confirmable.

### Parser và validation thật

`QuestionBankImportParser`, dòng 30–165:

- giới hạn 2 MB, tối đa 500 data rows;
- kiểm magic ZIP/OLE thay vì tin extension;
- chỉ đọc sheet đầu;
- map header bắt buộc và cell values thành row input.

`QuestionBankImportService.preview`, dòng 115–148:

1. Kiểm actor/subject/lesson như CRUD.
2. `LECTURER` đặt workflow target `REVIEW`; `LEADER` và `ADMIN` được service đặt thẳng `APPROVED` (`importedWorkflowStatus`, dòng 312–316). Điều này là import shortcut riêng; review HTTP routes vẫn chỉ nhận `LEADER`.
3. Validate từng row tại dòng 199–276: mã môn phải khớp subject đã chọn, type `MCQ|MR`, content bắt buộc, explanation tối đa 5.000, tối thiểu 2 options, correct labels hợp lệ; MCQ đúng 1, MR ít nhất 1 đúng.
4. Lưu snapshot preview trong `QuestionBankImportSessionStore`, session UUID gắn actor.

Lưu ý contract import MR hiện cho **ít nhất 1** đáp án đúng, trong khi AI question generator yêu cầu MR có **ít nhất 2** đúng. Đây là hai validator thật khác nhau trong source, không nên mô tả chúng giống nhau.

### Confirm

`question-bank-import.js:147–172` gửi:

```text
POST /lecturer/question-bank/import/confirm
Content-Type: application/json
Body: {"sessionId":"uuid"}
```

`QuestionBankImportController.confirm`, dòng 102–117, gọi `QuestionBankImportService.confirm`. Service dòng 150–186:

1. Atomically claim session đúng actor; session in-memory TTL 10 phút (`QuestionBankImportSession:13,83–90`).
2. Revalidate snapshot và authorization tại thời điểm confirm.
3. Nếu bất kỳ row lỗi, không insert row nào; session được restore khi transaction rollback để user có thể xử lý phù hợp.
4. Nếu hợp lệ, persist tất cả `QuestionBankItem` và `QuestionBankOption` theo trạng thái đã quyết định.

Đây là import atomic toàn workbook. Preview không ghi item; refresh/restart JVM làm mất session vì store hiện là in-memory, và session quá 10 phút bị cleanup mỗi 60 giây.

## 8. Random đề từ Question Bank

Form `questionbank/list.html:122–182`, request `POST /lecturer/question-bank/generate-test`, thuật toán `Collections.shuffle` và quá trình snapshot/distribute đã được mô tả đầy đủ tại:

```text
docs/audit/workflows/QUESTION_BANK_RANDOM_TEST.md
```

Không có AI trong workflow random này.

## 9. AI liên quan Question Bank: ranh giới thật của source

Source hiện tại **không có** controller/service nào sinh AI rồi tạo `QuestionBankItem`. Nút AI chỉ tồn tại trong màn sửa **Test** (`templates/tests/lecturer-form.html:240`) và endpoint là:

```text
POST /lecturer/tests/{testId}/ai-questions/generate
POST /lecturer/tests/{testId}/ai-questions/confirm
```

Confirm append `TestQuestion`/`TestOption`, không append Question Bank. Toàn bộ contract AI, retry, preview session và confirm persistence nằm trong `product/TESTS_WORKFLOWS.md`, mục 4.

Nếu được hỏi “AI question generation có đưa vào ngân hàng câu hỏi không?”, câu trả lời theo code hiện tại là **không**. Muốn vào shared Question Bank, user phải tạo/import item qua các workflow trên rồi Leader approve.

## 10. Legacy URL

`LegacyQuestionBankRedirectController`, dòng 26–35, chỉ redirect:

| URL cũ | Kết quả |
|---|---|
| `GET /lecturer/tests/{testId}/question-bank` | workspace `/lecturer/question-bank` |
| `GET /leader/tests/{testId}/question-bank/review` | `/leader/question-bank` |

Không có business write tại các URL legacy.

Trace đầy đủ của hai compatibility handler (`LegacyQuestionBankRedirectController:22–35`):

| Handler | Route / initial query-mutation | Response và guard thật |
|---|---|---|
| `LegacyQuestionBankRedirectController.lecturerTestQuestionBankRedirect` | `GET /lecturer/tests/{testId}/question-bank`; không truyền `testId` vào service/repository, không đọc `Test`/Question Bank và không ghi state. | Class gate là `PREAUTH_LECTURER_OR_ABOVE`; trả 302 tới `/lecturer/question-bank`, nên workspace đích tự thực hiện initial subject/workspace query và chọn subject mặc định. `testId` bị bỏ hoàn toàn. |
| `LegacyQuestionBankRedirectController.leaderTestQuestionBankRedirect` | `GET /leader/tests/{testId}/question-bank/review`; cũng không query/mutate và bỏ `testId`. | Handler này chỉ có gate Lecturer-or-above, sau đó 302 `/leader/question-bank`; destination `LeaderQuestionBankController` mới yêu cầu đúng `LEADER`. Vì vậy một Lecturer có thể nhận redirect nhưng bị destination từ chối; đây không phải đường vòng review authorization. |

## Repository/entity và security summary

| Thành phần | Vai trò |
|---|---|
| `QuestionBankItemRepository` | tải danh sách theo subject/status; search/filter hiện do service làm trong memory; không có lock row cho review |
| `QuestionBankOptionRepository` | options theo item; replace khi edit/import |
| `QuestionBankAccessPolicy` | subject scope theo actor/role |
| `QuestionBankItemService` | CRUD validation và owner/edit rules |
| `QuestionBankReviewService` | state machine single/bulk |
| `QuestionBankImportSessionStore` | preview tạm, owner-bound, TTL 10 phút, in-memory |

- Lecturer routes yêu cầu Lecturer trở lên; review routes bắt buộc chính xác Leader.
- Mọi POST dùng CSRF; actor id không đến từ browser.
- Random, test picker và distribution chỉ dùng `APPROVED`.
- Không có hard delete endpoint; archive/unarchive là lifecycle được hỗ trợ.
- Content được sanitize/validate lại ở server, không tin HTML/options trong form hoặc Excel.

## Audit gaps / giới hạn hiện hữu

- Workspace và inbox không phân trang; số item của một subject càng lớn thì danh sách, name lookup và lesson lookup càng tăng theo toàn bộ subject. `page` từng được mô tả trong tài liệu cũ nhưng controller không bind nó.
- Review đơn/bulk không lock `QuestionBankItem` và entity không có version field. Nếu cần audit quyết định cạnh tranh nghiêm ngặt, cần row lock hoặc optimistic locking cùng xử lý conflict; hiện workflow không phải atomic compare-and-set.
- Shared bank chỉ lưu link `lesson_template_id`; xóa/soft-delete template không cascade sang item. Read row có fallback “Chưa gắn bài học”, còn random theo `LESSON`/`CHAPTER` sẽ loại item mà lesson không còn resolve được. Đây là dữ liệu mồ côi không được dọn tự động.
