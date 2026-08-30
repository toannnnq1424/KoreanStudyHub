# Korean Study Hub — Browser QA Incident Report 2026-08-11

> Baseline: `origin/main` tại `c9437426`.
>
> Runtime QA: Java 17.0.9, Spring Boot 3.5.16, MySQL 9.2, baseline schema V119
> được migrate và xác minh tới V123,
> `http://localhost:18080`.

## Cách dùng checklist

- `[ ]`: chưa xử lý.
- `[~]`: đã có bản sửa hoặc đã điều tra nhưng chưa hoàn tất browser/regression QA.
- `[x]`: đã sửa và có bằng chứng xác minh.

## Incidents

### ADMIN-ROLE-TRANSITION-001 — Cho phép đổi chéo tài khoản học viên và nhân sự

- [x] Đã sửa ở tầng service và giới hạn lựa chọn trên form.
- [x] Đã chạy focused regression.
- Mức độ: **High**.
- Hiện tượng cũ:
  - Admin có thể đổi `LECTURER`/`LEADER` thành `STUDENT`; hệ thống chỉ trả warning nếu
    tài khoản đang đứng lớp nhưng vẫn lưu role mới.
  - Cùng API cập nhật cũng cho phép đổi `STUDENT` thành nhân sự hoặc đổi qua lại với
    `ADMIN`, làm sai định danh nghiệp vụ của một tài khoản hiện hữu.
- Quy tắc chốt:
  - Khi tạo mới, admin vẫn được chọn `STUDENT`, `LECTURER` hoặc `ADMIN`.
  - Sau khi tạo, chỉ cho phép `LECTURER ↔ LEADER` và giữ nguyên role hiện tại.
  - `STUDENT`, nhân sự và `ADMIN` không được đổi chéo; trường hợp học viên trở thành
    nhân sự phải cấp tài khoản mới.
- Bản sửa:
  - `AdminUsersGuard` kiểm tra transition trước khi entity bị cập nhật; request giả mạo
    role ngoài dropdown vẫn bị từ chối.
  - Form edit chỉ cung cấp tập role hợp lệ theo role hiện tại.
  - Loại bỏ warning “hạ Lecturer xuống Student” và dependency truy vấn lớp không còn cần.
- Bằng chứng:
  - `AdminUsersGuardTest` và `AdminUsersLeaderAssignmentGuardTest`: 17/17 đạt.
  - Integration contract đã đổi từ kỳ vọng warning sang field error và xác nhận role
    trong DB không đổi.

### ADMIN-LAST-ADMIN-GUARD-ORDER-001 — Validation mới làm yếu HTTP contract bảo vệ admin cuối cùng

- [x] Phát hiện bằng DB-backed integration và đã sửa thứ tự guard.
- Mức độ: **High**.
- Hiện tượng trong bản vá đầu:
  - Request tự đổi role hoặc hạ role admin cuối cùng bị validation loại tài khoản bắt
    trước, controller render lại form với HTTP 200.
  - Entity không bị lưu sai nhưng contract bảo mật 403 bị mất, khiến client/audit khó
    phân biệt request bị cấm với lỗi nhập liệu thông thường.
- Bản sửa:
  - Chạy self-role guard và last-active-admin guard trước invariant phân loại tài khoản.
  - Vi phạm invariant bảo mật cụ thể tiếp tục nhận 403; đổi chéo loại tài khoản từ một
    request hợp lệ về actor vẫn nhận field error và không thay đổi dữ liệu.

### ACCOUNT-FIRST-LOGIN-NPE-001 — Tài khoản vừa tạo đăng nhập rồi gặp 500/NPE

- [x] Không tái hiện trên baseline mới; đã khóa bằng browser và DB-backed regression.
- Mức độ báo cáo ban đầu: **Critical** vì chặn toàn bộ người dùng mới.
- Cách tái hiện đã thử:
  - Admin tạo mới `STUDENT` không gán mã môn, đăng xuất, đăng nhập bằng tài khoản mới.
  - Admin tạo mới `LECTURER` gán KOR311, đăng xuất, đăng nhập bằng tài khoản mới.
- Kết quả hiện tại:
  - Student redirect đúng tới `/my/classes`, render empty state 0 lớp, HTTP 200.
  - Lecturer redirect đúng tới `/lecturer/classes`, render empty state 0 lớp, HTTP 200.
  - Server log không có `NullPointerException` hoặc lỗi request tương ứng.
- Bằng chứng tự động:
  - Hai regression tạo mới entity bằng cùng `UserFactory`, đăng nhập qua Spring Security
    form, giữ session và mở landing tương ứng `/my/classes` hoặc `/lecturer/classes`;
    cả hai trả HTTP 200, không có NPE.
- Lưu ý:
  - Nếu lỗi cũ chỉ nằm ở một route khác landing, cần bổ sung URL và stack trace cụ thể;
    contract create → authenticate → landing hiện đã được bảo vệ.

### TEST-STUDENT-ROLE-BOUNDARY-001 — Luồng luyện đề cá nhân chỉ kiểm tra đăng nhập

- [x] Đã sửa và có contract test.
- Mức độ: **High**.
- Route: `/my/tests/practice/new`, `POST /my/tests/practice`, `/my/tests/readiness`.
- Nguyên nhân:
  - `StudentPracticeController` dùng `@PreAuthorize("isAuthenticated()")`, trong khi
    đây là dữ liệu và thao tác chỉ dành cho học viên.
  - Lecturer, Leader và Admin có thể gọi endpoint học viên rồi mới thất bại muộn hoặc
    tạo dữ liệu luyện tập sai không gian vai trò.
- Bản sửa:
  - Đổi sang `Roles.PREAUTH_STUDENT`.
  - Mở rộng `StudentTestRoleBoundaryContractTest` để khóa cả controller này.
- Bằng chứng: focused test 22/22 đạt, gồm contract exact `hasRole('STUDENT')`.

### TEST-AUTHORING-SHELL-UI-002 — Create/edit bài test không cùng class shell

- [x] Đã sửa và xác minh desktop/responsive.
- Mức độ: **Medium**.
- Hiện tượng:
  - Create từ lớp từng mất sidebar và dùng shell khác edit.
  - Edit có main content bám sát cạnh sidebar/viewport.
  - Các panel cấp cao lồng trong một panel lớn làm giao diện nặng và thiếu nhất quán.
- Bản sửa:
  - Create với `classId` dựng cùng class context/sidebar như edit.
  - Cả create/edit dùng cùng toolbar, gutter và một surface chính; top-level panel được
    phân chia bằng divider thay vì card lồng card.
  - Giữ chế độ global `/lecturer/tests/new` không sidebar.
- Bằng chứng browser desktop:
  - `/lecturer/tests/new?classId=1` hiển thị sidebar KOR311, active “Bài test”, nút quay
    lại đúng `/lecturer/classes/1/tests`.
  - Form có gutter hai bên, background phủ viewport và không còn bám mép.
  - `/lecturer/tests/1/edit?tab=info` dùng cùng sidebar/surface; tại viewport 1280px,
    main bắt đầu sau sidebar ở x=280, rộng 985px và body không tràn ngang.

### TEST-AUTHORING-MOBILE-SIDEBAR-001 — Sidebar che toàn bộ form ở 390px

- [x] Đã sửa và browser QA lại ở 390×844.
- Mức độ: **Medium**.
- Route: `/lecturer/tests/new?classId=1` và edit test có class context.
- Bằng chứng browser 390×844:
  - `scrollWidth=375`, không tạo thanh cuộn ngang nhưng sidebar rộng toàn viewport;
    phần form nằm ngoài vùng nhìn nên người dùng không thể thao tác.
  - Background body đã phủ toàn màn hình (`rgb(245, 248, 251)`), vì vậy đây là lỗi
    breakpoint/layout chứ không phải lỗi background trung tâm.
- Bản sửa:
  - Ẩn class sidebar ở breakpoint hẹp và chuyển shell về một cột; header vẫn cung cấp
    điều hướng chính.
- Bằng chứng hồi quy:
  - Sidebar có `display:none`, form rộng 347px trong viewport 390px.
  - `body.scrollWidth=375`, không có tràn ngang và form nhìn thấy/thao tác được.

### TEST-AUTHORING-MOBILE-TOOLBAR-001 — Tiêu đề đè link quay lại ở 390px

- [x] Phát hiện trong vòng xác minh sidebar, đã sửa và browser QA lại.
- Mức độ: **Low**.
- Hiện tượng:
  - Sau khi form hiện đúng ở mobile, link “Quay lại” và tiêu đề “Tạo bài test” chạm/đè
    nhau do toolbar ba phần không có gap và tiêu đề không co giãn.
- Bản sửa:
  - Bổ sung gap, cho tiêu đề co trong phần còn lại, căn giữa và giảm cỡ chữ ở breakpoint
    hẹp; giữ nút lưu luôn nhìn thấy.
- Bằng chứng hồi quy:
  - Ba vùng có biên lần lượt `36–102`, `114–223`, `235–339px`; không còn overlap.

### TEST-RESULT-CLASS-SHELL-001 — Kết quả/review test theo lớp mất sidebar và lạc giao diện

- [x] Đã sửa và browser QA bằng attempt thật của học viên.
- Mức độ: **Medium**.
- Route: `/my/classes/{classId}/tests/{testId}/result/{attemptId}` và `/review/{attemptId}`.
- Bản sửa:
  - Controller nạp class view sau khi xác thực attempt thuộc đúng class.
  - Result và review render `student-class-sidebar`, active “Bài test”, dùng cùng
    background/shell với lớp.
  - Dưới 900px sidebar được ẩn và nội dung trở về một cột.
- Bằng chứng desktop:
  - Result và review đều có sidebar KOR311, mục “Bài test” active và background phủ
    viewport; giải thích render thành nội dung, không còn lộ literal `<p>`.
- Phát hiện trong responsive QA:
  - Result đã có rule ẩn sidebar, nhưng review 390×844 vẫn xếp toàn bộ sidebar lên trên
    câu trả lời. Đã bổ sung cùng quy tắc một cột/ẩn sidebar cho review.
- Bằng chứng mobile sau sửa:
  - Result: sidebar `display:none`, card 318px, không tràn ngang.
  - Review: sidebar `display:none`, main 343px, `body.scrollWidth=375` trong viewport
    390px, không tràn ngang.

### TEST-AUTHORING-DIRTY-INIT-001 — Form trắng vẫn chặn Back/đăng xuất

- [x] Đã sửa và browser QA lại trong một phiên giảng viên mới.
- Mức độ: **Medium**.
- Hiện tượng:
  - Mở `/lecturer/tests/new?classId=1`, không nhập dữ liệu, sau đó chuyển trang hoặc
    đăng xuất; browser abort navigation như một form có thay đổi chưa lưu.
- Nguyên nhân:
  - Dirty guard có thể chụp baseline trước khi `learning-select.js` di chuyển native
    select vào combobox được enhance, làm trạng thái DOM khởi tạo bị coi là thay đổi.
- Bản sửa:
  - Sau khi enhance toàn bộ select ở `DOMContentLoaded`, re-baseline riêng khi trang có
    `#lfForm`; thời điểm này vẫn trước tương tác người dùng nên không xoá thay đổi thật.
  - Có thêm baseline sau `load`, nhưng chỉ khi chưa phát sinh `input/change`, để bao phủ
    widget khởi tạo muộn mà không ghi đè thao tác thật của người dùng.
- Bằng chứng:
  - Mở mới `/lecturer/tests/new?classId=1`, không nhập gì, đợi toàn bộ widget khởi tạo,
    bấm “Quay lại” và điều hướng thành công tới `/lecturer/classes/1/tests`.

### PRACTICE-EXPLANATION-RECONCILE-001 — Worker ghi ERROR lặp vô hạn mỗi hai phút

- [x] Đã sửa bốn nhóm nguyên nhân và xác minh runtime/DB sau V122.
- Mức độ: **Medium**.
- Bằng chứng runtime:
  - `QuestionExplanationPreparationReconciler` lặp `IllegalArgumentException` cho
    `publishedVersionId=1,5,8,9,17` mỗi chu kỳ hai phút.
  - Các request tạo/đăng nhập tài khoản vẫn thành công; lỗi thuộc worker giải thích
    Reading/Listening, không phải NPE tài khoản.
- Rủi ro:
  - Dữ liệu không hợp lệ bị retry vĩnh viễn, làm nhiễu giám sát và có thể khiến các
    explanation gap không bao giờ đạt trạng thái kết thúc.
- Bản sửa chẩn đoán:
  - Log thêm message nội bộ đã ép một dòng và giới hạn 240 ký tự, không log prompt,
    đáp án hay payload; nhờ đó có thể phân biệt strategy/schema/evidence lỗi nào thay vì
    chỉ thấy tên `IllegalArgumentException`.
- Nguyên nhân xác định từ log mới:
  - Published versions cũ thiếu toàn bộ strategy authority.
  - TFNG lưu đáp án trong `correctOptionIds` thay vì `correctValue`, và gán
    `NOT_GIVEN_BOUNDARY` cho cả đáp án `FALSE`.
  - FILL_BLANK seed cũ còn field dẫn xuất `ordinal` trong từng blank, không thuộc
    strict immutable question-content schema hiện tại.
  - TOPIK35 Listening có transcript chưa được duyệt audio QA; input readiness đã nhận
    ra thiếu evidence nhưng constructor lại ném exception trước khi tạo failed artifact.
- Bản sửa dữ liệu/runtime:
  - V120 backfill strategy v1 tương thích cho immutable version cũ và chuẩn hoá TFNG
    `correctValue`/`correctOptionIds`.
  - Readiness input thiếu evidence vẫn tạo fingerprint + failed artifact terminal, không
    tạo generation context và không bị retry vô hạn.
  - V121 đổi TFNG `FALSE/TRUE` đang dùng `NOT_GIVEN_BOUNDARY` sang strategy
    `TFNG_CONTRADICTION_TABLE` hợp lệ.
  - V122 bỏ `ordinal` khỏi JSON blank cũ; thứ tự vẫn được giữ nguyên bởi vị trí mảng.
- Bằng chứng sau sửa:
  - Flyway validate thành công 122 migrations và nâng dev schema từ V121 lên V122.
  - Chu kỳ runtime sau restart reconcile nốt một published version và worker xử lý bốn
    task; các dữ liệu không đủ evidence được lưu failed artifact/task terminal thay vì
    quay lại thành preparation gap.
  - Chạy lại đúng truy vấn native tìm published version còn thiếu binding/artifact/task
    không trả về dòng nào; log sau V122 không còn `Could not reconcile` lặp lại.

### AUTH-OIDC-PRINCIPAL-001 — Principal Google không tương thích với controller nội bộ

- [x] Đã sửa principal, quyền hiệu lực và khóa bằng OAuth integration.
- Mức độ: **Critical**.
- Nguyên nhân:
  - `CustomOidcUserPrincipal` không kế thừa `KshUserDetails`, trong khi nhiều controller
    nhận trực tiếp `@AuthenticationPrincipal KshUserDetails`.
  - Đăng nhập Google thành công nhưng principal bị inject thành `null`, tạo NPE/HTTP 500;
    quyền runtime của OIDC cũng có thể lệch với đăng nhập mật khẩu.
- Bản sửa:
  - Principal OIDC dùng cùng contract `KshUserDetails` và cùng tập effective permissions.
  - Từ chối email Google chưa xác minh, tài khoản local inactive/locked/deleted và liên
    kết subject không khớp hoặc đã thuộc tài khoản khác.
  - Khóa pessimistic user/provider trong transaction khi liên kết lần đầu để tránh race.
- Bằng chứng: `OAuthLoginIntegrationTest` 9/9 đạt, gồm principal type, permission và các
  nhánh fail-closed nêu trên.

### ADMIN-SESSION-REVOCATION-001 — Session cũ sống tiếp sau thay đổi bảo mật

- [x] Đã sửa lifecycle revoke và cache eviction.
- Mức độ: **High**.
- Hiện tượng cũ:
  - Deactivate/lock/delete/reset mật khẩu hoặc đổi role/email/subject có thể để session
    hiện hữu tiếp tục dùng quyền cũ tới khi hết hạn.
  - Đổi role matrix, override hoặc toggle permission chỉ xoá cache một phần, không chấm
    dứt session của các user bị ảnh hưởng.
- Bản sửa:
  - Thu thập user bị ảnh hưởng và revoke session sau transaction commit thành công.
  - `PermissionResolver.evictRole` trả lại đúng user IDs để các service permission revoke
    đồng bộ; password recovery cũng chấm dứt các session cũ.
- Bằng chứng: `SessionRevocationServiceTest`, permission/admin lifecycle tests và DB-backed
  admin integration đều đạt trong bộ regression hiện tại.

### ADMIN-WRITE-AUTHORITY-001 — Endpoint tạo user chỉ yêu cầu quyền xem

- [x] Đã sửa cả GET/POST và khóa validation mật khẩu phía server.
- Mức độ: **High**.
- Nguyên nhân:
  - Route tạo tài khoản chỉ gate `PERM_user.view`; user có quyền đọc nhưng không có
    `PERM_user.create` vẫn có thể gọi POST trực tiếp.
  - Giới hạn 6–64 ký tự trước đây dựa nhiều vào UI/DTO, chưa đồng nhất ở service reset.
- Bản sửa:
  - Yêu cầu đồng thời `user.view` và `user.create` cho form/create action.
  - Enforce password 6–64 ở create/reset DTO và write service.

### ADMIN-AI-KEY-CACHE-001 — Response reveal API key có thể bị cache

- [x] Đã sửa và có controller contract test.
- Mức độ: **Medium**.
- Nguyên nhân: response chứa plaintext provider key không đặt chính sách cache rõ ràng.
- Bản sửa: trả `Cache-Control: no-store` trên reveal endpoint.
- Bằng chứng: `AiSettingsControllerContractTest` đạt.

### CLASS-CREATE-DATES-001 — Ngày bắt đầu bị bỏ mất khi tạo lớp

- [x] Đã sửa constructor mapping và DB-backed regression.
- Mức độ: **Medium**.
- Nguyên nhân: `ClassCreator` truyền `null` thay vì `form.startDate()`; đồng thời method
  update của entity không gán lại `startDate`.
- Bản sửa: ánh xạ đúng start date ở create và update.

### CLASS-IMPORT-CAPACITY-001 — Import có thể vượt sĩ số hoặc mở lại enrollment đã hoàn thành

- [x] Đã sửa dưới class/user/enrollment locks và có integration test.
- Mức độ: **High**.
- Hiện tượng cũ:
  - Preview và confirm tách thời điểm; import đồng thời có thể cùng thấy còn chỗ rồi ghi
    vượt `maxStudents`.
  - Enrollment `COMPLETED` bị gom chung với trạng thái có thể re-enroll.
  - Role/trạng thái tài khoản thay đổi sau preview không được revalidate dưới lock.
- Bản sửa:
  - Khóa class, đếm active enrollment và trừ seat theo từng row thành công.
  - Khóa/revalidate user + enrollment khi confirm; `COMPLETED` và `CLASS_FULL` có status
    lỗi riêng, không tự mở lại lịch sử đã hoàn thành.
- Bằng chứng: `ImportStudentsServiceIntegrationTest` và `RowValidatorTest` đạt.

### ASSIGNMENT-WRITE-INVARIANTS-001 — Bài đã publish vẫn sửa được và dữ liệu số vượt schema

- [x] Đã sửa service + UI và có regression.
- Mức độ: **High**.
- Nguyên nhân:
  - POST edit không khóa assignment sau publish dù UI có thể ẩn nút.
  - Submission trắng, title quá dài hoặc score có scale/range vượt `DECIMAL(5,2)` có thể
    đi tới persistence exception hoặc tạo dữ liệu nghiệp vụ không hợp lệ.
- Bản sửa:
  - Chỉ `DRAFT` được edit; trim/require submission content.
  - Enforce title 300 ký tự, max score và grade range/precision trước khi lưu.
  - Đồng bộ trạng thái lựa chọn/detail của catalog assignment khi filter/sort.
- Bằng chứng: `AssignmentServiceTest`, `AssignmentLeaderDepartmentAccessTest` và
  `AssignmentCatalogUiContractTest` đạt.

### DELETED-CLASS-CHILD-ACCESS-001 — Enrollment active còn mở tài nguyên của lớp đã xoá

- [x] Đã sửa đồng nhất và có regression theo từng resolver.
- Mức độ: **High**.
- Phạm vi: assignment, bài test, flashcard deck chia sẻ và lesson attachment.
- Nguyên nhân: các access check chỉ nhìn enrollment `ACTIVE`; soft-delete parent class
  không tự vô hiệu hoá các enrollment lịch sử.
- Bản sửa: yêu cầu parent class còn live trước khi chấp nhận enrollment cho mọi child
  resource nêu trên.
- Bằng chứng: `AssignmentLeaderDepartmentAccessTest`, `TestAccessResolverLeaderScopeTest`,
  `DeckAccessResolverTest` và `LessonAttachmentsServiceTest` đạt.

### TEST-DEADLINE-AUTHORITY-001 — URL cũ tiếp tục lộ đề và payload muộn vẫn có điểm

- [x] Đã sửa server-authoritative deadline lifecycle.
- Mức độ: **Critical**.
- Hiện tượng cũ:
  - Attempt `IN_PROGRESS` quá hạn vẫn có thể resume/render nội dung bằng bookmark.
  - Submit payload tới sau deadline vẫn được chấm đáp án rồi chỉ đổi status thành
    `TIMED_OUT`, vì vậy bài quá giờ vẫn có thể nhận điểm.
- Bản sửa:
  - Detail/resume/take/heartbeat đều chốt attempt quá hạn thành `TIMED_OUT` trước khi
    render hoặc cập nhật activity.
  - Payload nhận sau deadline được lưu như câu trả lời rỗng và nhận 0 điểm; thời điểm
    kiểm tra deadline dùng một timestamp authoritative duy nhất.
- Bằng chứng: `TestAttemptServiceTest`, `TestCatalogServiceTest` và
  `StudentTestFlowIntegrationTest` đạt.

### TEST-EXAM-IMMUTABILITY-001 — Đề thi thay đổi sau khi học viên đã bắt đầu

- [x] Đã khóa toàn bộ assessment contract sau student activity.
- Mức độ: **High**.
- Nguyên nhân: code cũ chỉ giữ ổn định shape/IDs sau khi có response nhưng vẫn cho đổi
  lớp, lịch, nội dung, đáp án hiển thị; attempt chưa trả lời câu nào cũng chưa khóa đề.
- Bản sửa:
  - Chỉ cần đã có attempt/response là reject toàn bộ save đề hiện hữu.
  - Form disabled và hiển thị notice rõ ràng; backend vẫn là authority nếu request giả mạo.
- Bằng chứng: `LecturerExamManagementIntegrationTest`, `LecturerTestNavigationTest` và
  `LecturerAiQuestionUiContractTest` đạt.

### TEST-QB-INTEGRITY-001 — Duplicate insert và concurrent review ghi đè quyết định

- [x] Đã sửa và migration chỉ thêm cột vào bảng hiện hữu.
- Mức độ: **High**.
- Nguyên nhân:
  - Danh sách ID question-bank lặp làm cùng snapshot được append nhiều lần.
  - Hai curator review đồng thời có thể để request tới sau silently overwrite quyết định
    trước đó.
- Bản sửa:
  - Dedupe IDs nhưng giữ thứ tự first occurrence trước khi copy snapshot.
  - V123 thêm `question_bank_items.row_version` và JPA `@Version` để stale review fail.
- Bằng chứng: `ExamQuestionBankWriterTest`, `ExamQuestionBankInsertIntegrationTest` và
  migration smoke V1–V123 đạt. V123 không tạo bảng mới.

### TEST-LEGACY-HTML-XSS-001 — HTML lịch sử đi thẳng vào `th:utext`

- [x] Đã sanitize cả write path và read path, có regression mới.
- Mức độ: **High**.
- Phạm vi: mô tả đề, câu hỏi, lựa chọn và explanation trong take/preview/result/review;
  đặc biệt trang detail sinh viên từng trả raw `test.description` vào `th:utext`.
- Bản sửa:
  - Sanitize khi snapshot/persist câu hỏi và khi build mọi DTO có thể render rich HTML.
  - Read-time sanitization bảo vệ cả dữ liệu legacy có trước bản sửa write path.
- Bằng chứng: `ExamQuestionBankWriterTest`, test flow contracts và regression
  `student_detail_sanitizes_legacy_description_before_utext_rendering` đạt.

### TEST-OPTIONAL-SIDEBAR-500-001 — Result/review global crash khi không có class view

- [x] Phát hiện bằng full integration, đã sửa template precedence và chạy lại suite.
- Mức độ: **High**.
- Hiện tượng:
  - Route result/review global không nạp `view` nhưng element đặt `th:if` và `th:replace`
    cùng lúc.
  - Thymeleaf xử lý replace trước, vẫn evaluate fragment arguments từ `view == null` và
    trả HTTP 500.
- Bản sửa:
  - Đưa điều kiện ra outer `th:block`; fragment chỉ được resolve khi class view tồn tại.
- Bằng chứng: full DB-backed run tìm ra 1 lỗi trong 154 cases; sau sửa,
  `StudentTestFlowIntegrationTest` + UI contract 12/12 đạt và toàn bộ 154 integration
  cases đã có kết quả xanh.

### LIBRARY-INLINE-EDITOR-001 — Form standalone không hoạt động và response cũ ghi đè dialog mới

- [x] Đã sửa lifecycle editor và có UI contract.
- Mức độ: **Medium**.
- Nguyên nhân:
  - `library-inline.js` chỉ khởi tạo form được fetch vào dialog; trang lesson-form độc lập
    thiếu Quill/script và nút Huỷ gọi `dialog.close()` khi không có dialog.
  - Click nhanh hai lesson có thể để response request cũ tới sau ghi đè editor mới.
- Bản sửa:
  - Mount idempotent cho cả standalone/dialog, nạp Quill ở standalone, xử lý cancel đúng
    context, hỗ trợ keyboard/drop file.
  - Abort request cũ và dùng sequence guard trước khi thay DOM; error text dùng
    `textContent` thay vì interpolation vào `innerHTML`.
- Bằng chứng: `LibraryLearningFlowContractTest` đạt.

### UI-DYNAMIC-CONTROLS-001 — Widget động mất hành vi/a11y sau tab swap

- [x] Đã sửa mount idempotent và các trạng thái responsive liên quan.
- Mức độ: **Medium**.
- Phạm vi:
  - Enhanced select không mount lại sau AJAX detail-tab; accessible name lẫn text của
    native control và Enter trong ô search không chọn option đầu tiên.
  - Mobile compose messaging không chuyển sang pane phải vì chỉ conversation được tính
    là `has-open`.
  - Question-bank status từ query không hợp lệ truyền thẳng vào service/UI.
- Bản sửa:
  - Public `KshLearningSelect.mount(root)`, auto-mount sau tab event, tên accessible sạch
    và keyboard behavior ổn định.
  - Compose cũng kích hoạt mobile single-pane; normalize QB status về allowlist/`ALL`.
- Bằng chứng: UI contracts cho library, question bank, messaging và test authoring đạt.

## Bằng chứng kiểm thử hiện tại

- Main compile 870 source files bằng JDK 17: đạt.
- Bộ changed/database-free regression: 105/105 đạt.
- Full DB-backed integration trên disposable MySQL 9.2 catalog: 154 cases đều xanh sau
  khi sửa incident optional-sidebar; riêng rerun suite liên quan + static regression 12/12.
- Focused assignment + lesson integration: 29/29 đạt.
- OAuth integration: 9/9 đạt.
- Regression XSS cuối `TestCatalogServiceTest`: 3/3 đạt.
- Migration smoke trên disposable MySQL 9.2 DB: Flyway V1–V123 đạt; V120–V122 chỉ data
  repair, V123 chỉ `ADD COLUMN`, không migration nào trong batch hiện tại tạo bảng mới.
- Disposable database/user đã được xác minh đúng tên rồi xoá sau test.
- Browser create/login tài khoản mới Student: đạt, landing `/my/classes` HTTP 200.
- Browser create/login tài khoản mới Lecturer: đạt, landing `/lecturer/classes` HTTP 200.
- Browser form tạo test desktop: sidebar và gutter đúng, background phủ viewport.
- Browser form/result/review ở 390×844 sau sửa: một cột, không tràn ngang, không bị
  sidebar che nội dung.
