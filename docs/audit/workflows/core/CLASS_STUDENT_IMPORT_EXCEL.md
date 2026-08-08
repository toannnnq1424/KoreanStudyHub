# Workflow: giảng viên import sinh viên vào lớp bằng Excel

Đây là toàn bộ luồng đang chạy khi chủ lớp tải file Excel, xem preview rồi xác nhận thêm sinh viên. Import này **không tạo tài khoản mới**: nó chỉ tìm user đã tồn tại bằng email và tạo/kích hoạt lại `enrollments`.

## 1. Chủ lớp mở màn Thành viên

Người dùng mở:

```text
GET /lecturer/classes/{classId}/members
```

`ClassDetailController.detailMembers`, `src/main/java/com/ksh/features/classes/controller/ClassDetailController.java:79-96`, nạp lớp, teaching team, member ACTIVE và request PENDING rồi render `src/main/resources/templates/classes/detail-members.html`.

Hai control import chỉ xuất hiện khi model `isPrimaryClassOwner=true`:

- **Import Excel** ở `detail-members.html:38-44` có `data-action="open-import-excel"` và `data-class-id`;
- **Tải mẫu** ở `detail-members.html:45-49` là link GET trực tiếp;
- nút **Thêm học sinh** thủ công ở `detail-members.html:50-53` đang disabled, không có backend flow từ UI.

`ImportStudentsController` có class-level `@PreAuthorize(PREAUTH_LECTURER_OR_ABOVE)` tại `src/main/java/com/ksh/features/classes/imports/controller/ImportStudentsController.java:48-50`, nhưng cả preview và confirm còn gọi `ClassesService.getOwnerManaged`: chỉ immutable owner hoặc `ADMIN` mới qua được. Đồng giảng/Leader nhìn thấy trang thành viên nhưng UI không hiện nút và service không cho import.

## 2. Bấm “Tải mẫu”

Browser gửi:

```text
GET /lecturer/classes/{classId}/import-students/template
```

`ImportStudentsController.downloadTemplate`, `ImportStudentsController.java:129-140`, gọi `ExcelTemplateBuilder.build()` và trả byte array với:

- MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`;
- `Content-Disposition: form-data; name="attachment"; filename="mau-import-sinh-vien.xlsx"`;
- HTTP 200, hoặc 500 không body nếu builder ném `IOException`.

File được dựng động; project không đọc một binary template có sẵn trên disk.

## 3. Bấm “Import Excel” và chọn file

`src/main/resources/static/js/import-excel.js:350-361` bắt click bằng event delegation rồi gọi:

```javascript
window.openImportExcelModal({ classId: classId })
```

`openImportExcelModal`, dòng 335-340, dựng hai URL upload/template và mở dialog trong `detail-members.html:173-295`. UI nói rõ `.xlsx/.xls`, tối đa 2 MiB và 500 dòng (`detail-members.html:183-186`). Chọn file mới chỉ cập nhật tên file và bật button ở `import-excel.js:111-140`; chưa có request server.

## 4. Bấm “Tải lên & xem trước”

`doUpload`, `import-excel.js:184-215`, tạo `FormData` với field `file`, lấy CSRF header từ meta tag và gửi:

```http
POST /lecturer/classes/{classId}/import-students/upload
Content-Type: multipart/form-data
Credentials: same-origin

file=<binary .xls/.xlsx>
```

Nó dùng `AbortController`; đóng modal hoặc chọn upload khác hủy response cũ (`import-excel.js:34-39,100-109,183-214`).

### Controller → parse → validate → session

`ImportStudentsController.upload`, `ImportStudentsController.java:79-95`:

1. lấy `classId`, multipart `file` và `user.id/role` từ principal;
2. gọi `ImportStudentsService.previewUpload(file,classId,userId,role)` ở dòng 84-85;
3. serialize bằng `ImportPayloads.preview`;
4. trả 400 cho `InvalidFileException`, 403 cho `AccessDeniedException`, 500 cho lỗi runtime khác.

`ImportStudentsService.previewUpload`, `src/main/java/com/ksh/features/classes/imports/service/ImportStudentsService.java:94-104`, chạy read-only transaction:

1. `ClassesService.getOwnerManaged` xác nhận quyền owner/Admin;
2. `ExcelParser.parse` đọc file;
3. `RowValidator.validate` gắn status từng dòng;
4. tạo UUID, pin session với exact `classId`, `lecturerId`, `uploadedAt`, filename và immutable row list;
5. lưu vào `ImportSessionStore` trong heap, rồi trả session.

### File được chấp nhận như nào

`ExcelParser.parse`, `src/main/java/com/ksh/features/classes/imports/parser/ExcelParser.java:86-117`, không tin extension: kiểm magic byte ZIP/OLE2 (`196-203`), giới hạn 2 MiB, dùng Apache POI đọc sheet đầu. Static block dòng 65-72 bật giới hạn zip-bomb. `readSheet`, dòng 119-189:

- coi row đầu là header;
- normalize chữ thường, bỏ dấu/ký tự phân cách (`205-215`);
- nhận alias email/MSSV/họ tên/điện thoại ở dòng 218-244;
- bắt buộc có ít nhất cột Email hoặc MSSV;
- bỏ row trống hoàn toàn;
- dừng khi quá 500 data rows.

### Từng dòng được phân loại như nào

`RowValidator.validate`, `src/main/java/com/ksh/features/classes/imports/validator/RowValidator.java:68-169`, xét theo thứ tự first-match:

1. thiếu cả email và MSSV → `MISSING_REQUIRED`;
2. trùng email/MSSV trong file → `DUPLICATE_IN_FILE`;
3. sai regex email → `INVALID_EMAIL`;
4. MSSV không phải 4-15 ký tự chữ/số → `INVALID_STUDENT_ID`;
5. không tìm thấy user → `USER_NOT_FOUND`;
6. user không phải `STUDENT` → `NOT_A_STUDENT`;
7. user inactive/locked → `USER_INACTIVE`;
8. enrollment đã ACTIVE → `DUPLICATE_IN_CLASS`;
9. enrollment trạng thái khác ACTIVE → `RE_ENROLL`;
10. còn lại → `OK`.

Giới hạn quan trọng: `resolveUser`, `RowValidator.java:193-211`, **chỉ lookup bằng email**. MSSV chỉ phục vụ validate/hiển thị/trùng trong file; row chỉ có MSSV luôn báo cần bổ sung email. Import không gọi create-user service.

### JSON frontend nhận

`ImportPayloads.preview`, `src/main/java/com/ksh/features/classes/imports/dto/ImportPayloads.java:20-30`, trả:

```json
{
  "sessionId": "uuid",
  "fileName": "students.xlsx",
  "totalRows": 12,
  "okCount": 9,
  "warningCount": 1,
  "errorCount": 2,
  "rows": [{
    "rowNumber": 2,
    "email": "student@example.com",
    "studentId": "HE1234",
    "fullName": "Nguyen Van A",
    "phone": "...",
    "status": "OK",
    "statusMessage": "OK",
    "isError": false,
    "isWarning": false,
    "isImportable": true,
    "detail": null
  }]
}
```

`renderPreview`, `import-excel.js:217-244`, đổ các count và table. Button xác nhận chỉ bật khi có ít nhất một row importable, và nếu có error thì user phải tick **Bỏ qua các dòng lỗi**.

## 5. Bấm “Xác nhận import”

`doConfirm`, `import-excel.js:294-319`, gửi:

```http
POST /lecturer/classes/{classId}/import-students/{sessionId}/confirm
Content-Type: application/json
Credentials: same-origin

{"skipErrors":true}
```

`ImportStudentsController.confirm`, `ImportStudentsController.java:101-121`, bind `ConfirmRequest`; body null được hiểu là `skipErrors=false`, rồi gọi:

```java
importService.confirmImport(sessionId, classId, user.getId(), user.getRole(),
    new ImportStudentsService.ImportOptions(skip));
```

### Transaction và ghi DB

`ImportStudentsService.confirmImport`, `ImportStudentsService.java:116-146`:

1. kiểm lại quyền owner/Admin trên class hiện tại;
2. `ImportSessionStore.claim` atomically lấy-xóa session của đúng lecturer (`ImportSessionStore.java:76-87`), nên double-click/replay không import lần hai;
3. buộc `session.classId == URL classId`;
4. nếu có hard error và `skipErrors=false`, không ghi DB, restore session và trả summary lỗi;
5. đăng ký restore session nếu transaction rollback (`ImportStudentsService.java:148-156`);
6. gọi `processAllRows`, gom tối đa 50 enrollment rồi `saveAll` (`169-196`);
7. ghi một `ClassActivity` type `UPDATED` với filename/các count (`198-219`);
8. commit rồi trả `ImportResult`.

`ImportRowProcessor.process`, `src/main/java/com/ksh/features/classes/imports/service/ImportRowProcessor.java:34-83` map:

- `OK` → `Enrollment.createFor(..., JoinedVia.IMPORT, null)` → `status=ACTIVE`;
- `RE_ENROLL` → nếu row biến mất thì insert mới, nếu non-ACTIVE thì `reactivateVia(IMPORT)`, nếu đã ACTIVE thì skip;
- `DUPLICATE_IN_CLASS` → skip;
- mọi hard error → skip.

Không có bước student/Leader duyệt sau import: enrollment được ACTIVE ngay. Không có notification/email cho student. Luồng này cũng **không kiểm `classes.max_students`**, nên một file hợp lệ có thể làm sĩ số vượt capacity cấu hình.

Comment service nói “per-row best effort”, nhưng `ImportStudentsService.java:159-167` tự ghi rõ thực tế: `saveAll`/flush constraint error có thể rollback cả transaction/batch. Vì vậy count success trước flush không đồng nghĩa một phần đã commit.

### JSON kết quả và UI cuối

`ImportPayloads.result`, `ImportPayloads.java:33-43`, trả `totalProcessed`, `imported`, `reactivated`, `skippedDuplicate`, `skippedError`, `failed` và rows sau xử lý. `renderSummary`, `import-excel.js:321-327`, hiện bốn count.

Khi user bấm **Hoàn tất**, `onDoneClick`, dòng 79, đóng modal và `window.location.reload()`. Browser gọi lại `GET /lecturer/classes/{id}/members`; member ACTIVE mới xuất hiện trong table.

## 6. TTL, multi-instance và failure behavior

`ImportSession` đặt TTL cố định 10 phút (`src/main/java/com/ksh/features/classes/imports/session/ImportSession.java:23-24,67-70`). `ImportSessionStore.evictExpired`, `ImportSessionStore.java:102-116`, chạy sau 60 giây rồi mỗi 60 giây để xóa session hết hạn.

Store là `ConcurrentHashMap` trong JVM (`ImportSessionStore.java:40-45`): restart làm mất preview; deployment nhiều instance sẽ lỗi nếu upload và confirm rơi vào hai node khác nhau. Session pin owner và class, nhưng UUID không được lưu DB/Redis.

Tóm tắt:

```text
[Chủ lớp: Import Excel]
  -> JS mở modal, chọn file
  -> POST .../upload (multipart)
  -> ImportStudentsController.upload
  -> ImportStudentsService.previewUpload
  -> ExcelParser -> RowValidator -> in-memory ImportSession
  <- JSON sessionId + preview rows/counts
  -> POST .../{sessionId}/confirm {skipErrors}
  -> ImportStudentsController.confirm
  -> ImportStudentsService.confirmImport
  -> Enrollment ACTIVE/REACTIVATED + ClassActivity
  <- JSON summary
  -> reload GET /members -> thấy sinh viên mới
```
