# Audit workflow toàn hệ thống KSH

Đây là trang bắt đầu của bộ audit. Báo cáo không chỉ liệt kê controller: mỗi walkthrough đi theo thao tác người dùng và ghi rõ UI/button → method + URL → controller → service/transaction/repository hoặc external provider → state/response → UI của actor tiếp theo.

## Kết quả độ phủ hiện tại

- 1.349 runtime file dưới `src/main` đã được inventory.
- 924 Java production source file có manifest và số dòng.
- 342 HTTP handler mapping và 684 UI action/link/fetch reference đã được catalog.
- 85/85 controller, 342/342 exact `Controller.method`, 8/8 controller advice, 16/16 class có `@Scheduled`, 3/3 `SmartLifecycle` worker đã có semantic anchor.
- 81/81 exact non-controller runtime hook đã được trace, gồm bootstrap/filter/auth handler/security SPI/WebSocket bootstrap/scheduler/event/lifecycle và 36/36 JPA persistence callback.
- 111/111 page template được controller render, 288/288 `Class.method` trong screen-query index rematch source và 64/64 browser JavaScript đã có semantic anchor; pure `th:fragment` được tính cùng màn cha, không giả thành route riêng.
- 35 tài liệu workflow, tổng 8.217 dòng; toàn bộ bộ audit hiện 14.676 dòng.

Xem trạng thái chi tiết tại [WORKFLOW_COVERAGE.md](WORKFLOW_COVERAGE.md), bằng chứng entrypoint tại [SEMANTIC_COVERAGE_GATE.md](SEMANTIC_COVERAGE_GATE.md), và bằng chứng không trỏ tới file/link ma tại [REFERENCE_INTEGRITY_GATE.md](REFERENCE_INTEGRITY_GATE.md).

Các defect/risk/no-op đã xác nhận được gom tại [AUDIT_FINDINGS.md](AUDIT_FINDINGS.md); từng finding vẫn trỏ lại walkthrough để xem đầy đủ ngữ cảnh FE → BE.

Riêng câu hỏi “AI nhận gì và bắt buộc trả chuỗi nào?” có bảng tra tập trung tại [AI_WORKFLOW_AND_CONTRACT_INDEX.md](AI_WORKFLOW_AND_CONTRACT_INDEX.md).

Câu hỏi “nhập cấu hình này sẽ mở khóa gì?” có bảng field/gate/consumer tại [CONFIGURATION_UNLOCK_MATRIX.md](CONFIGURATION_UNLOCK_MATRIX.md).

Câu hỏi “màn này lấy query nào, phần nào chỉ nằm trong JavaScript, khi nào mới ghi DB?” có ma trận 111 màn tại [SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md](SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md).

Khi cần tra ngược toàn bộ data-access declaration, dùng [DATA_ACCESS_QUERY_CATALOG.md](DATA_ACCESS_QUERY_CATALOG.md): 109 file Repository/JdbcStore/direct JDBC hoặc EntityManager và 543 method đã được parser rematch với source. Catalog này bổ sung exact query/SQL tĩnh; quyền, transaction và thứ tự gọi vẫn lấy từ walkthrough.

Khi cần tìm mọi property/prefix ngoài các field Admin có workflow mở khóa, dùng [RUNTIME_CONFIGURATION_CATALOG.md](RUNTIME_CONFIGURATION_CATALOG.md): 326 key/prefix từ bốn config file cùng `@Value`, `@ConfigurationProperties`, `@ConditionalOnProperty` và scheduled placeholder; secret/local override được redact. [CONFIGURATION_UNLOCK_MATRIX.md](CONFIGURATION_UNLOCK_MATRIX.md) vẫn là nguồn giải thích capability thực tế.

## Phân biệt lỗi tài liệu với lỗi runtime

Câu ghi trước đây rằng “import sinh viên Excel và Leader shell đang được tài liệu lớp trỏ sang file chưa tồn tại” mô tả **khoảng trống tài liệu ở thời điểm đó**, không tự chứng minh code runtime hỏng. Hai walkthrough [CLASS_STUDENT_IMPORT_EXCEL.md](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md) và [LEADER_WORKFLOWS.md](workflows/core/LEADER_WORKFLOWS.md) hiện đã tồn tại, và reference gate kiểm link/source filename/range dòng tự động.

Tách riêng khỏi lỗi trỏ file, source runtime của hai nhóm này vẫn có defect thật đã xác nhận: import không kiểm `maxStudents`, chỉ resolve account bằng email và có thể rollback cả batch ở `saveAll`; Leader reject giữ class ở `DRAFT`. Các lỗi đó nằm trong [AUDIT_FINDINGS.md](AUDIT_FINDINGS.md), không bị “xóa” chỉ vì tài liệu đã được bổ sung.

## Cách tìm câu trả lời khi được hỏi một thao tác

Ví dụ “Tạo random đề từ Question Bank như nào?”: mở [QUESTION_BANK_RANDOM_TEST.md](workflows/QUESTION_BANK_RANDOM_TEST.md), tìm button/form, route POST, controller `generateTest`, service `generate`, query approved candidates, `Collections.shuffle`, snapshot/distribution rồi redirect.

Với workflow khác, chọn nhóm dưới đây:

### Identity, Admin và configuration

- [Đăng nhập, OAuth callback, forgot/reset, profile/avatar/password](workflows/core/IDENTITY_AUTH_PROFILE.md)
- [Admin dashboard và settings hub](workflows/admin/00_ADMIN_SHELL_DASHBOARD.md)
- [Admin users](workflows/core/ADMIN_USERS.md)
- [Departments/subjects và Leader binding](workflows/core/ADMIN_DEPARTMENTS.md)
- [Role permissions và user overrides](workflows/core/ADMIN_PERMISSIONS.md)
- [General, SMTP, Google OAuth và KRDICT](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md)
- [Global AI providers/models/prompts/logs](workflows/admin/02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md)
- [Practice AI profiles/bindings/purpose](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md)
- [Local/R2 và ba storage profile](workflows/admin/04_STORAGE_PROFILES_R2_LOCAL.md)

### Class, learning và assessment thường

- [Class lifecycle, join request, member approve/reject/leave/archive](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md)
- [Import sinh viên Excel](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md)
- [Leader dashboard/approval/assignment/report](workflows/core/LEADER_WORKFLOWS.md)
- [Home và lecturer dashboard](workflows/core/HOME_AND_LECTURER_DASHBOARD.md)
- [Library, lesson authoring và distribution](workflows/core/LIBRARY_LESSON_AUTHORING_AND_DISTRIBUTION.md)
- [Student lesson/file/video/completion](workflows/core/LESSON_CONSUMPTION_FILES_AND_VIDEO.md)
- [Assignments author/submit/grade](workflows/core/ASSIGNMENTS.md)
- [Learning progress](workflows/core/LEARNING_PROGRESS.md)
- [Tests: authoring, learner attempt, monitor và AI question generation](workflows/product/TESTS_WORKFLOWS.md)
- [Question Bank governance/import](workflows/product/QUESTION_BANK_WORKFLOWS.md)
- [Random test từ Question Bank](workflows/QUESTION_BANK_RANDOM_TEST.md)
- [Flashcards](workflows/product/FLASHCARDS_WORKFLOWS.md)
- [Shared Korean dictionary and Flashcard capture](../KOREAN_DICTIONARY_FLASHCARD_WORKFLOW.md)
- [Messaging, notifications và mail](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md)

### Practice thường, AI và media

- [Submit và AI evaluation end-to-end](workflows/PRACTICE_SUBMIT_AND_AI_EVALUATION.md)
- [Learner catalog/start/preflight/player/result/preferences](workflows/practice/01_LEARNER_CATALOG_ATTEMPT_RESULT.md)
- [Lecturer owner-only draft/autosave/publish/version](workflows/practice/02_AUTHORING_DRAFT_PUBLISH_GOVERNANCE.md)
- [Excel/PDF/Text AI candidate + material library](workflows/practice/03_IMPORT_EXCEL_PDF_AI_CANDIDATE.md)
- [Objective explanation AI/editorial](workflows/practice/04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md)
- [Speaking prompt STT/TTS authoring](workflows/practice/05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md)
- [Learner speaking media/STT/evaluation/direct-audio/privacy](workflows/practice/06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md)
- [Background jobs, cleanup và retention](workflows/practice/07_BACKGROUND_JOBS_FAILURE_RETENTION.md)
- [Practice authoring source-of-truth: mọi màn hình, initial read và mutation map](workflows/practice/08_AUTHORING_SOURCE_OF_TRUTH_AUDIT.md)

### Platform boundary

- [HTTP/security/static upload/WebSocket/global error infrastructure](workflows/platform/INFRASTRUCTURE_HTTP_SECURITY_AND_WEBSOCKET.md)
- [Toàn bộ scheduler/event/listener/bootstrap/lifecycle hook ngoài HTTP](workflows/platform/NON_HTTP_RUNTIME_HOOKS.md)
- [Toàn bộ JPA `PrePersist`/`PreUpdate` callback và ảnh hưởng lên write](workflows/platform/ENTITY_LIFECYCLE_CALLBACKS.md)

## Kiến trúc runtime dùng khi đọc mọi workflow

```text
Thymeleaf/JavaScript
  -> Spring Security + CSRF/session
  -> @Get/Post/Put/DeleteMapping controller
  -> DTO/Bean Validation + principal
  -> service ownership/state guard + @Transactional/lock
  -> repository/entity | AI/OAuth/KRDICT/SMTP/R2
  -> view/redirect/JSON/stream
  -> notification/outbox/WebSocket/scheduler
  -> UI hiện tại hoặc UI actor tiếp theo
```

Browser không gọi repository trực tiếp. Controller không phải nguồn quyết định quyền cuối: `@PreAuthorize` là gate ngoài, service tiếp tục kiểm ownership, department, immutable version và state transition. Notification/email thường là side effect có thể được cô lập; cần đọc transaction phase trước khi kết luận mutation chính có rollback hay không.

## Các catalog line-level

- [RUNTIME_FILE_MANIFEST.md](RUNTIME_FILE_MANIFEST.md): mọi file Java/template/JS/CSS/migration/config/asset runtime.
- [SOURCE_MANIFEST.md](SOURCE_MANIFEST.md): mọi Java production file, package, type, line count.
- [HTTP_ENDPOINT_CATALOG.md](HTTP_ENDPOINT_CATALOG.md): verb, mapping expression, `Controller.method`, line khai báo.
- [UI_ACTION_CATALOG.md](UI_ACTION_CATALOG.md): target/action/fetch và dòng template/JavaScript.
- [DATA_ACCESS_QUERY_CATALOG.md](DATA_ACCESS_QUERY_CATALOG.md): 543 method khai báo trong 109 Repository/JdbcStore/direct JDBC hoặc EntityManager source, kèm derived query/JPQL/SQL khi có thể trích tĩnh.
- [RUNTIME_CONFIGURATION_CATALOG.md](RUNTIME_CONFIGURATION_CATALOG.md): 326 property/env/prefix cùng declaration, fallback và Java consumer tĩnh; mọi giá trị local/secret được che.

Các catalog là inventory, không thay thế walkthrough. Ngược lại, walkthrough nhóm logic theo hành trình người dùng; khi cần exact line cho một endpoint ít gặp, catalog đưa thẳng tới source.

## Rebuild và kiểm gate

```bash
python3 scripts/docs/generate_system_audit_catalog.py
python3 scripts/docs/generate_data_access_audit_catalog.py --self-check
python3 scripts/docs/generate_runtime_config_catalog.py --self-check
python3 scripts/docs/check_workflow_audit_coverage.py
python3 scripts/docs/check_workflow_audit_references.py
```

Hai generator có `--self-check` exit non-zero nếu row query/config sinh ra không còn rematch declaration/placeholders trong source. Coverage gate exit non-zero nếu source thêm controller/advice/runtime hook/JPA callback/page template/browser JavaScript mà chưa có walkthrough gọi đúng tên; handler và hook phải có exact `Class.method`. Reference gate fail nếu local Markdown link, rooted source path, inline source filename hoặc range dòng vượt EOF. Điều này ngăn lặp lại cả lỗi “controller đã được nhắc tên nên coi như mọi nhánh màn hình đã audit” lẫn lỗi tài liệu trỏ tới file chưa được tạo. Pure fragment không có route được gắn với màn cha thay vì tính như một screen độc lập.

## Cách ghi defect trong audit

Audit status và code status tách biệt:

- **Đã audit, hoạt động**: flow có consumer/runtime path hoàn chỉnh.
- **Đã audit, placeholder/no-op**: UI/setting tồn tại nhưng không có consumer, ví dụ General settings hiện chưa đổi product UI.
- **Đã audit, fail-closed**: code chủ động không chạy khi thiếu binding/storage/consent/capability.
- **Đã audit, defect/risk**: hành vi chạy nhưng có bất nhất, ví dụ Leader reject vẫn để class `DRAFT`, import sinh viên không kiểm `maxStudents`.

Do đó một dòng “đã audit” không có nghĩa source không có bug; nó có nghĩa hành vi thực tế và giới hạn đã được chỉ ra bằng code.
