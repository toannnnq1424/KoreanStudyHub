# KSH workflow coverage register

Register này phân biệt ba khái niệm:

- **Inventory**: file/endpoint/UI action có trong catalog tự sinh.
- **Semantic walkthrough**: đã đọc UI → HTTP → controller → service/repository/external call → state/response → UI bên nhận.
- **Capability status**: code có thực sự hoạt động, đang fail-closed hay chỉ là placeholder. Một capability chưa implement vẫn có thể đã được audit đầy đủ.

Coverage gate hiện **PASS**: 85/85 controller, 342/342 exact handler mapping, 8/8 controller advice, 81/81 exact non-controller runtime hook (trong đó 36/36 JPA callback), 111/111 page template controller-rendered, 288/288 `Class.method` trong screen-query index rematch source và 64/64 browser JavaScript đều có semantic anchor. Chi tiết máy kiểm tại [SEMANTIC_COVERAGE_GATE.md](SEMANTIC_COVERAGE_GATE.md).

Reference gate cũng **PASS**: mọi local Markdown link, rooted source path và inline source filename trong bộ audit đều trỏ tới file đang tồn tại. Xem [REFERENCE_INTEGRITY_GATE.md](REFERENCE_INTEGRITY_GATE.md). Đây là kiểm tra tính đúng của tài liệu tham chiếu, không phải tuyên bố code runtime không có defect.

Screen-level initial query và ranh giới client-only/persistence được đối chiếu riêng tại [SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md](SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md). Đây là lớp coverage bổ sung sau controller gate; nó ngăn một controller nhiều màn/JS bị đánh dấu “đã xong” chỉ vì class name xuất hiện một lần.

## 1. Platform, identity và Admin nghiệp vụ

| Workflow family | Actor | Nội dung đã trace | Walkthrough |
|---|---|---|---|
| HTTP/security/WebSocket infrastructure | Browser/all | Chrome probe, static/public upload boundary, authorized private media, WebSocket handshake/broker, global errors | [INFRASTRUCTURE_HTTP_SECURITY_AND_WEBSOCKET.md](workflows/platform/INFRASTRUCTURE_HTTP_SECURITY_AND_WEBSOCKET.md) |
| Login/logout/throttle/session/OAuth login | All | form login, saved request theo role, failure throttle, logout, Google callback/account binding | [IDENTITY_AUTH_PROFILE.md](workflows/core/IDENTITY_AUTH_PROFILE.md) |
| Forgot/reset password | Anonymous/user | neutral request, token hash/TTL/use, direct SMTP, reset password/session outcome | [IDENTITY_AUTH_PROFILE.md](workflows/core/IDENTITY_AUTH_PROFILE.md) |
| Profile/password/avatar | All roles | GET/edit, validation, password check, upload/public delivery, principal refresh | [IDENTITY_AUTH_PROFILE.md](workflows/core/IDENTITY_AUTH_PROFILE.md) |
| Admin dashboard/settings hub | Admin | aggregate SQL, Chart/UI, settings navigation, `/admin/classes` placeholder | [00_ADMIN_SHELL_DASHBOARD.md](workflows/admin/00_ADMIN_SHELL_DASHBOARD.md) |
| Admin users | Admin | search/page/create/edit/reset/toggle/lock/delete/restore/role transitions | [ADMIN_USERS.md](workflows/core/ADMIN_USERS.md) |
| Departments/subjects | Admin | create/edit/toggle, Leader binding, downstream class/QB scope | [ADMIN_DEPARTMENTS.md](workflows/core/ADMIN_DEPARTMENTS.md) |
| Role permission + overrides | Admin | permission matrix, override grant/deny/reset, runtime authority resolution/audit | [ADMIN_PERMISSIONS.md](workflows/core/ADMIN_PERMISSIONS.md) |

## 2. Admin configuration và external runtime

| Workflow family | Nội dung đã trace | Capability status | Walkthrough |
|---|---|---|---|
| General settings | form/key/cache/runtime consumers | Lưu DB được; bốn key hiện chưa đổi product UI | [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md) |
| SMTP + mail outbox | save/test/sender, reset mail, notification whitelist, retry/retention | Hoạt động khi host/credential/network hợp lệ | [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md), [MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md) |
| Google OAuth config | save id/secret/scope, dynamic registration, login/callback/binding | Chỉ user local có sẵn; không self-register | [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md) |
| KRDICT | key/base URL, exact GET/XML, shared dictionary/Flashcard consumers | Không phải dependency Practice | [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md), [../../KOREAN_DICTIONARY_FLASHCARD_WORKFLOW.md](../../KOREAN_DICTIONARY_FLASHCARD_WORKFLOW.md) |
| Global AI providers/models | CRUD/toggle/test/reveal/fallback/log, exact chat contract và consumers | Test/QB-related authoring, Flashcard; không fallback cho Practice | [02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md](workflows/admin/02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md) |
| System prompts | CRUD/toggle và mapping exact prompt name → consumer | Chỉ tên runtime được code gọi mới có hiệu lực | [02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md](workflows/admin/02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md) |
| Practice AI control plane | profile/secret/preset/binding/model/capability/test fixture/runtime resolver | Một số preset ADC/direct-audio đang fail-closed; không dùng global AI | [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md) |
| Local/R2 storage profiles | `GENERAL_UPLOADS`, `PRACTICE_AUTHORING`, `PRACTICE_SPEAKING`, secret/toggle/delete/read/write/migration boundary | Profile phải active+complete; đổi bucket không tự chuyển object cũ | [04_STORAGE_PROFILES_R2_LOCAL.md](workflows/admin/04_STORAGE_PROFILES_R2_LOCAL.md) |

## 3. Class, Leader, lesson, assignment và progress

| Workflow family | Actor | Nội dung đã trace | Walkthrough |
|---|---|---|---|
| Class lifecycle | Lecturer/Leader/Admin | list/create/edit/delete/settings/detail/archive scheduler, event + Leader notification | [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) |
| Student request join | Student/owner | catalog/request/idempotency/capacity/approve/reject/leave/member UI/notifications | [CLASS_MANAGEMENT_AND_ENROLLMENT.md](workflows/core/CLASS_MANAGEMENT_AND_ENROLLMENT.md) |
| Excel student import | Owner/Admin | template/upload/preview/session/confirm/enrollment/activity/UI reload/failure limits | [CLASS_STUDENT_IMPORT_EXCEL.md](workflows/core/CLASS_STUDENT_IMPORT_EXCEL.md) |
| Leader shell | Leader | dashboard, approval/rejection, co-lecturer replacement, report SQL | [LEADER_WORKFLOWS.md](workflows/core/LEADER_WORKFLOWS.md) |
| Home/lecturer dashboard | User/Lecturer | role routing, dashboard read models, recent/summary widgets | [HOME_AND_LECTURER_DASHBOARD.md](workflows/core/HOME_AND_LECTURER_DASHBOARD.md) |
| Lesson authoring/distribution | Lecturer | create/edit/publish/delete, file/video, template/library distribution, notification | [LIBRARY_LESSON_AUTHORING_AND_DISTRIBUTION.md](workflows/core/LIBRARY_LESSON_AUTHORING_AND_DISTRIBUTION.md) |
| Lesson learner consumption | Student/public-token | view, attachment/video range/download, completion and authorization | [LESSON_CONSUMPTION_FILES_AND_VIDEO.md](workflows/core/LESSON_CONSUMPTION_FILES_AND_VIDEO.md) |
| Assignments | Lecturer/Student | author/publish/close, submit/resubmit, feedback/grade, notification/state | [ASSIGNMENTS.md](workflows/core/ASSIGNMENTS.md) |
| Learning progress | Student/Lecturer | completion/progress/report aggregation and class-scoped visibility | [LEARNING_PROGRESS.md](workflows/core/LEARNING_PROGRESS.md) |

## 4. Tests, Question Bank, Flashcards và communication

| Workflow family | Actor | Nội dung đã trace | Walkthrough |
|---|---|---|---|
| Tests authoring/distribution | Lecturer | form/questions/images/publish/distribute/activity/state guards | [TESTS_WORKFLOWS.md](workflows/product/TESTS_WORKFLOWS.md) |
| Test learner attempt | Student | readiness/start/autosave/submit/timeout/result/review | [TESTS_WORKFLOWS.md](workflows/product/TESTS_WORKFLOWS.md) |
| Test monitor | Lecturer | monitor polling/activity/read model | [TESTS_WORKFLOWS.md](workflows/product/TESTS_WORKFLOWS.md) |
| Test AI question generation | Lecturer/AI | material extraction, prompt, exact JSON, repair, preview session, confirm, retention | [TESTS_WORKFLOWS.md](workflows/product/TESTS_WORKFLOWS.md) |
| Random test from Question Bank | Lecturer | approved candidates/scope/shuffle/snapshot/distribution/redirect | [QUESTION_BANK_RANDOM_TEST.md](workflows/QUESTION_BANK_RANDOM_TEST.md) |
| Question Bank governance | Lecturer/Leader | draft/edit/submit/approve/reject/archive/bulk/history | [QUESTION_BANK_WORKFLOWS.md](workflows/product/QUESTION_BANK_WORKFLOWS.md) |
| Question Bank Excel import | Lecturer | template/upload/preview/confirm/session cleanup/contracts | [QUESTION_BANK_WORKFLOWS.md](workflows/product/QUESTION_BANK_WORKFLOWS.md) |
| Flashcards | User | deck/card CRUD/import/share/public token, study/review, AI generation/dictionary | [FLASHCARDS_WORKFLOWS.md](workflows/product/FLASHCARDS_WORKFLOWS.md) |
| Shared Korean dictionary | Authenticated user/Admin | common KRDICT lookup, owned-deck selection and Flashcard save; Admin configures the provider | [../../KOREAN_DICTIONARY_FLASHCARD_WORKFLOW.md](../../KOREAN_DICTIONARY_FLASHCARD_WORKFLOW.md) |
| Messaging/class chat | Authenticated/class actors | conversation/create/send/read/unread, STOMP delivery, SSR fallback, class scope | [MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md) |
| Notifications/mail | User/worker | producer types, recent/list/open/count, safe redirect, outbox/SMTP/retry/retention | [MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md](workflows/product/MESSAGING_NOTIFICATIONS_MAIL_WORKFLOWS.md) |

## 5. Practice thường, AI, media và background

| Workflow family | Nội dung đã trace | Walkthrough |
|---|---|---|
| Autosave/submit/result/re-evaluate | objective/Writing/Speaking branch, lock/deadline/idempotency, queue, UI states | [PRACTICE_SUBMIT_AND_AI_EVALUATION.md](workflows/PRACTICE_SUBMIT_AND_AI_EVALUATION.md) |
| Learner catalog/start/preflight/player/result | filter/detail/version lock, listening/speaking preflight, cancel, result, progress/font | [01_LEARNER_CATALOG_ATTEMPT_RESULT.md](workflows/practice/01_LEARNER_CATALOG_ATTEMPT_RESULT.md) |
| Lecturer authoring/governance | owner-only draft/autosave/upload/publish/version/restore/archive/delete | [02_AUTHORING_DRAFT_PUBLISH_GOVERNANCE.md](workflows/practice/02_AUTHORING_DRAFT_PUBLISH_GOVERNANCE.md) |
| Excel/PDF/Text AI authoring | target/template/preview/candidate/exact AI JSON/review/apply/material library | [03_IMPORT_EXCEL_PDF_AI_CANDIDATE.md](workflows/practice/03_IMPORT_EXCEL_PDF_AI_CANDIDATE.md) |
| Objective explanation AI | prepare/generate/repair/editorial approve/reject/publish/result visibility | [04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md](workflows/practice/04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md) |
| Speaking prompt authoring | upload/STT/poll/confirm/retry, text/TTS, exact provider contracts/publish gate | [05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md](workflows/practice/05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md) |
| Learner speaking media/evaluation | record/upload/activate/playback/delete/submit, STT→transcript→evaluator exact JSON | [06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md](workflows/practice/06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md) |
| Direct-audio/privacy | consent, learner media, retained provider evaluation boundary and non-release policy; reviewer dark-observation experiment retired in V114 | [06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md](workflows/practice/06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md), [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md) |
| Jobs/cleanup/retention | deadline, evaluation, explanation, prompt, media/asset cleanup and storage migration | [07_BACKGROUND_JOBS_FAILURE_RETENTION.md](workflows/practice/07_BACKGROUND_JOBS_FAILURE_RETENTION.md) |
| Practice authoring source-of-truth | mọi authoring screen/initial read, draft/import/candidate/material/explanation/Speaking mutation map và response code | [08_AUTHORING_SOURCE_OF_TRUTH_AUDIT.md](workflows/practice/08_AUTHORING_SOURCE_OF_TRUTH_AUDIT.md) |

## 6. Inventory hỗ trợ kiểm tra toàn source

| Artifact | Coverage hiện tại |
|---|---:|
| [RUNTIME_FILE_MANIFEST.md](RUNTIME_FILE_MANIFEST.md) | 1.349 file trong `src/main` |
| [SOURCE_MANIFEST.md](SOURCE_MANIFEST.md) | 924 Java production file |
| [HTTP_ENDPOINT_CATALOG.md](HTTP_ENDPOINT_CATALOG.md) | 342 handler mapping |
| [UI_ACTION_CATALOG.md](UI_ACTION_CATALOG.md) | 684 action/link/fetch reference |
| [DATA_ACCESS_QUERY_CATALOG.md](DATA_ACCESS_QUERY_CATALOG.md) | 109 Repository/JdbcStore/direct JDBC/EntityManager file; 543 declared method, source self-check PASS |
| [RUNTIME_CONFIGURATION_CATALOG.md](RUNTIME_CONFIGURATION_CATALOG.md) | 326 property/env/prefix row từ config + Java annotations/placeholders; secret redaction và source self-check PASS |
| [SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md](SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md) | 111 controller-rendered screen + pure-fragment ownership; initial query và JS/DB boundary |
| [SEMANTIC_COVERAGE_GATE.md](SEMANTIC_COVERAGE_GATE.md) | PASS cho exact HTTP/runtime/JPA hooks, screen và browser-JS entry points |
| [REFERENCE_INTEGRITY_GATE.md](REFERENCE_INTEGRITY_GATE.md) | PASS cho local links, rooted source paths và inline source filenames |
| [NON_HTTP_RUNTIME_HOOKS.md](workflows/platform/NON_HTTP_RUNTIME_HOOKS.md) | bootstrap + 16 scheduler methods + 3 SmartLifecycle + 3 AFTER_COMMIT listeners + init/shutdown |
| [ENTITY_LIFECYCLE_CALLBACKS.md](workflows/platform/ENTITY_LIFECYCLE_CALLBACKS.md) | 36 JPA callbacks trên 22 entity và write effect thực tế |

Không suy diễn “PASS” thành “code không có bug”. Các walkthrough ghi rõ defect/placeholder/fail-closed ngay tại bước mà hành vi phát sinh; audit coverage và implementation correctness là hai trục khác nhau.
