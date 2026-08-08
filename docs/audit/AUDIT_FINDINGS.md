# KSH consolidated audit findings

Tài liệu này gom các hành vi đáng chú ý đã xác nhận trực tiếp từ source. Đây không phải danh sách “workflow chưa audit”; tất cả mục dưới đây đã có walkthrough. Nhãn dùng để phân loại tác động, không thay thế quyết định severity chính thức của product/security team.

## 1. Security và authorization/session

| ID | Finding đã xác nhận | Tác động thực tế | Source/walkthrough |
|---|---|---|---|
| SEC-01 | Google OIDC chỉ chặn `locked`, không kiểm `user.isActive()` | Account deactivated vẫn có thể đăng nhập Google nếu row chưa deleted/locked và OAuth đã bật | `CustomOidcUserService.java:74-100`; [IDENTITY_AUTH_PROFILE.md](workflows/core/IDENTITY_AUTH_PROFILE.md) §4 |
| SEC-02 | `AdminUsersController` đặt cả list/new/create dưới `PERM_user.view` | Admin có quyền xem nhưng không có permission create riêng vẫn gọi được `POST /admin/users` | `AdminUsersController.java:52-55,108-135`; [ADMIN_USERS.md](workflows/core/ADMIN_USERS.md) phần permission |
| SEC-03 | Admin thay role/permission, deactivate/lock/delete/reset password và reset qua email không revoke/refresh session hiện hữu | Principal/authority snapshot có thể tiếp tục dùng tới logout/session expiry; khóa DB không được re-check trên mọi request. Ngoại lệ: self-service `POST /change-password` có gọi `SessionRevocationService` để expire **các session khác**, giữ session hiện tại | [ADMIN_USERS.md](workflows/core/ADMIN_USERS.md) §session effects; [IDENTITY_AUTH_PROFILE.md](workflows/core/IDENTITY_AUTH_PROFILE.md) §password/session |
| SEC-04 | Update assignment thiếu state guard mà comment mô tả | UI giấu edit ở state PUBLISHED/CLOSED, nhưng request POST trực tiếp của actor có quyền vẫn sửa được | `LecturerAssignmentService.java:97` và body update; [ASSIGNMENTS.md](workflows/core/ASSIGNMENTS.md) “Phát hiện audit” |
| SEC-05 | Student leave service không bắt buộc enrollment ACTIVE | POST trực tiếp có thể biến một request PENDING thành REMOVED dù UI chỉ hiện leave cho ACTIVE | [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) §13 |
| SEC-06 | OAuth client secret được load nguyên văn và render bằng input `type=text` | Bất kỳ session có `PERM_system.oauth` mở page đều nhận secret hiện tại trong HTML/DOM; khác với SMTP/AI/storage masking | `OauthSettingsService.java:55-71`, `settings-oauth.html:62-74`; [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md) §3 |

## 2. State machine và data integrity

| ID | Finding đã xác nhận | Tác động thực tế | Source/walkthrough |
|---|---|---|---|
| STATE-01 | Leader reject giữ class ở `DRAFT` | Lớp vừa từ chối lập tức vẫn nằm trong queue và có thể approve/reject lại; note không hiện ở queue | `ClassEntity.java:161-167`, `LeaderClassApprovalService.java:40-59`; [LEADER_WORKFLOWS.md](workflows/core/LEADER_WORKFLOWS.md) §5 |
| STATE-02 | Excel student import không kiểm `classes.max_students` | Confirm file hợp lệ có thể tạo enrollment ACTIVE vượt capacity; không có Leader/student approval | `ImportStudentsService.java:116-196`; [CLASS_STUDENT_IMPORT_EXCEL.md](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md) §5 |
| STATE-03 | Excel import chỉ resolve user bằng email | File/UX nói “Email hoặc MSSV”, nhưng row chỉ có MSSV không thể match account và luôn lỗi hướng dẫn bổ sung email | `RowValidator.java:193-211`; [CLASS_STUDENT_IMPORT_EXCEL.md](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md) §4 |
| STATE-04 | Batch import không thật sự commit per-row độc lập | Constraint/flush failure ở `saveAll` có thể rollback cả transaction dù loop đã đếm row success | `ImportStudentsService.java:159-167`; [CLASS_STUDENT_IMPORT_EXCEL.md](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md) §5 |
| STATE-05 | `startDate` có trong DTO nhưng không có input và entity update không assign | User không thể tạo/cập nhật ngày bắt đầu qua UI; POST field thủ công cũng không được apply ở update | [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) §2/§4 |
| STATE-06 | Đổi bucket/endpoint/key của storage profile không migrate object | Read mới dùng config mới ngay; object chỉ tồn tại ở backend cũ có thể mất khả năng truy cập | [04_STORAGE_PROFILES_R2_LOCAL.md](workflows/admin/04_STORAGE_PROFILES_R2_LOCAL.md) §7 |
| STATE-07 | Notification assignment không có redirect resolver | Click vẫn mark read nhưng quay về inbox thay vì mở assignment | `NotificationController.resolveRedirect`, `NotificationController.java:161-180`; [MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md) §8 |
| STATE-08 | Question Bank insert vào Test không deduplicate `itemIds` | POST trực tiếp `{"itemIds":[123,123]}` append hai snapshot giống nhau nếu item vẫn approved; UI thường chỉ gửi một id nên không che được guard thiếu ở API/service | `ExamQuestionBankPickerService.approvedSnapshotsByIds`; [TESTS_WORKFLOWS.md](workflows/product/TESTS_WORKFLOWS.md) §3 |
| STATE-09 | Question Bank giữ link tới lesson template đã soft-delete | Item không bị cascade/dọn; list fallback “Chưa gắn bài học”, còn random scope LESSON/CHAPTER loại item không resolve được | [QUESTION_BANK_WORKFLOWS.md](workflows/product/QUESTION_BANK_WORKFLOWS.md) phần repository/edge |
| STATE-10 | Review Question Bank không có row lock hoặc optimistic version | Hai Leader review đồng thời không có compare-and-set; quyết định commit sau có thể ghi đè quyết định trước | `QuestionBankItemRepository`/`QuestionBankItem`; [QUESTION_BANK_WORKFLOWS.md](workflows/product/QUESTION_BANK_WORKFLOWS.md) phần concurrency |
| STATE-11 | Share Flashcard deck không kiểm `ClassEntity.status` | Student vẫn cần ACTIVE enrollment, nhưng nhánh lecturer chỉ cần là primary lecturer của class tồn tại; class không ACTIVE không bị service chặn như tài liệu cũ từng mô tả | `DeckService.java:177-218`; [FLASHCARDS_WORKFLOWS.md](workflows/product/FLASHCARDS_WORKFLOWS.md) phần source findings |

## 3. UI no-op, placeholder hoặc dữ liệu hiển thị sai

| ID | Finding đã xác nhận | Tác động thực tế | Source/walkthrough |
|---|---|---|---|
| UI-01 | Bốn key General settings không có runtime consumer | Save/cache/GET hoạt động nhưng site name/logo/description/contact không đổi giao diện công khai | [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md) §1 |
| UI-02 | Bốn cột số liệu class list bị mapper hard-code `0` | Học sinh/Bài giảng/Bài tập/Tài liệu và sort sĩ số trên page không phản ánh DB | `ClassRowMapper.java:32-47`; [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) §1 |
| UI-03 | Admin `/admin/classes` là placeholder | Tab không list/quản lý class; bề mặt thật vẫn là `/lecturer/classes` | [00_ADMIN_SHELL_DASHBOARD.md](workflows/admin/00_ADMIN_SHELL_DASHBOARD.md) §4 |
| UI-04 | Lecturer/student class board và một số detail tab là placeholder | Không có POST/read model cho board announcement; scores/materials/schedule/roles/groups không phải workflow hoàn chỉnh | [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) §6/§12 |
| UI-05 | Class soft-delete không có restore route/UI trong module class | Modal nói không thể hoàn tác; dữ liệu con còn nhưng class bị `@SQLRestriction` ẩn khỏi JPA read bình thường | [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) §5 |
| UI-06 | Current storage page không render legacy save/test form | `POST /admin/settings/storage` và `/test` vẫn callable bằng request thủ công, nhưng hướng Admin hiện hành phải dùng `GENERAL_UPLOADS` profile UI | [04_STORAGE_PROFILES_R2_LOCAL.md](workflows/admin/04_STORAGE_PROFILES_R2_LOCAL.md) §6 |
| UI-07 | Match deck-picker của Flashcard là dead branch | JS tìm `#fcMatchDeckOptions/#fcMatchDeckPicker` và JSON attrs mà template/model không cung cấp nên return ngay; trộn deck chỉ hoạt động qua header GET picker | `flashcard-learning.js:669-720`, `flashcard-learning.html:23-26`; [FLASHCARDS_WORKFLOWS.md](workflows/product/FLASHCARDS_WORKFLOWS.md) phần source findings |
| UI-08 | Hai action per-template của Library redirect thiếu `subjectId` | Sau distribute/delete một template, browser có thể về subject mặc định thay vì subject vừa thao tác; mutation vẫn commit nhưng context UI sai | `LessonTemplateController.java:145,183`; [LIBRARY_LESSON_AUTHORING_AND_DISTRIBUTION.md](workflows/core/LIBRARY_LESSON_AUTHORING_AND_DISTRIBUTION.md) §6–7 |

## 4. AI/control-plane capability gaps

| ID | Finding đã xác nhận | Tác động thực tế | Source/walkthrough |
|---|---|---|---|
| AI-01 | Practice profile chọn Google ADC vẫn bị resolver bắt stored secret; transport luôn Bearer secret | ADC profile lưu được nhưng không resolve/call production hiện tại | `PracticeAiBindingResolver.java` credential gate; [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md) §6 |
| AI-02 | Fixed xAI/Groq preset bị verification gate chặn | Preset endpoint/UI tồn tại nhưng chưa thể bật thành provider production | [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md) §6 |
| AI-03 | Direct-audio evaluation chưa nối vào live scoring path | Production Speaking vẫn audio → STT transcript → transcript evaluator; dark/reviewer path không release score | [06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md](workflows/practice/06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md) §10; [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md) §6 |
| AI-04 | Global AI request-log UI không có retention/purge workflow | Log chỉ đọc/filter; dữ liệu tăng trưởng cần vận hành DB bên ngoài source hiện tại | [02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md](workflows/admin/02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md) §4 |

## 5. Reliability và scale boundary

| ID | Finding đã xác nhận | Tác động thực tế | Source/walkthrough |
|---|---|---|---|
| OPS-01 | Student Excel preview session nằm trong JVM heap, TTL 10 phút | Restart mất session; nhiều instance có thể upload ở node A/confirm node B rồi báo không tồn tại | `ImportSessionStore.java:27-38`; [CLASS_STUDENT_IMPORT_EXCEL.md](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md) §6 |
| OPS-02 | WebSocket dùng in-memory simple broker | Live message push không cross-instance; DB/REST/SSR mới là fallback bền vững | `WebSocketConfig.java:19-37`; [INFRASTRUCTURE_HTTP_SECURITY_AND_WEBSOCKET.md](workflows/platform/INFRASTRUCTURE_HTTP_SECURITY_AND_WEBSOCKET.md) §3 |
| OPS-03 | Conversation đã tạo chỉ gate participant ở open/send | User rời class không tự mất lịch sử chat/conversation cũ | [MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md) §2 |
| OPS-04 | Gán Leader/đồng giảng không gửi notification, và candidate Leader có thể đang locked | Thay đổi quyền trách nhiệm chỉ có hiệu lực khi người dùng tự vào màn; không có báo chủ động | [ADMIN_DEPARTMENTS.md](workflows/core/ADMIN_DEPARTMENTS.md), [LEADER_WORKFLOWS.md](workflows/core/LEADER_WORKFLOWS.md) §6 |
| OPS-05 | Question Bank workspace và Leader inbox tải toàn bộ item của subject, lọc trong memory | Thời gian query, mapping, lesson/name lookup và heap tăng theo toàn subject; controller không bind `page` và repository không keyword-page query cho flow này | [QUESTION_BANK_WORKFLOWS.md](workflows/product/QUESTION_BANK_WORKFLOWS.md) §1/conclusion |
| OPS-06 | Flashcard image không có lifecycle cleanup | Thay/xóa card, soft-delete deck hoặc lỗi sau upload có thể để object mồ côi; feature chỉ có `put/store`, không gọi delete tương ứng | `FlashcardImageStorageService.java:24-35`; [FLASHCARDS_WORKFLOWS.md](workflows/product/FLASHCARDS_WORKFLOWS.md) phần source findings |
| OPS-07 | Notification “lớp chờ Leader duyệt” là `AFTER_COMMIT` best-effort, không có retry/outbox | Class và activity đã commit vẫn thành công khi listener lỗi; Leader có thể không nhận notification dù queue approval vẫn query thấy class | `ClassPendingReviewNotifier.notifyLeader`; [NON_HTTP_RUNTIME_HOOKS.md](workflows/platform/NON_HTTP_RUNTIME_HOOKS.md) §4 |
| OPS-08 | Hai cleanup/retention worker nhạy cảm mặc định tắt | `PracticeSpeakingMediaCleanupWorker` và `DirectAudioReviewerAccessAuditRetentionWorker` không chạy nếu operator không bật property; cleanup task/audio và reviewer-access audit row có thể được giữ quá lịch mong muốn | [NON_HTTP_RUNTIME_HOOKS.md](workflows/platform/NON_HTTP_RUNTIME_HOOKS.md) §2; [07_BACKGROUND_JOBS_FAILURE_RETENTION.md](workflows/practice/07_BACKGROUND_JOBS_FAILURE_RETENTION.md) |
| OPS-09 | Mail outbox có delivery semantics at-least-once | SMTP có thể đã nhận mail nhưng DB chưa ghi `SENT`; sau lease expiry job được retry và người nhận có thể nhận trùng | `MailOutboxWorker.start`, `MailOutboxProcessor.deliver`; [MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md) §mail outbox |

## Ưu tiên xử lý gợi ý

1. Xác nhận/fix `SEC-01`, `SEC-02`, `SEC-03`, `SEC-04` vì liên quan account/authorization/state bypass.
2. Sửa `STATE-01`, `STATE-02`, `STATE-04`, `STATE-06`, `STATE-08` và `STATE-10` trước khi scale dữ liệu thật.
3. Gắn rõ badge “chưa hoạt động” hoặc bỏ UI gây hiểu nhầm cho nhóm `UI-*`, đặc biệt dead Match picker.
4. Giữ các capability `AI-*` fail-closed cho tới khi adapter/verification/release gate hoàn chỉnh; không chỉ bật checkbox/config để vượt qua.

Audit này không tự sửa source ứng dụng. Mỗi finding cần product owner xác nhận behavior mong muốn trước khi thay state machine hoặc permission contract.
