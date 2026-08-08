# Admin configuration → runtime unlock matrix

Đây là bảng tra tập trung cho câu hỏi “Admin nhập field này thì điều gì bắt đầu hoạt động?”. Chi tiết từng form/controller/service/line nằm trong walkthrough được link.

| Cấu hình UI | Save handler và nơi lưu | Gate runtime thật | Được mở khóa | Không được mở khóa |
|---|---|---|---|---|
| General: site name/description/logo/contact | `POST /admin/settings/general` → `GeneralSettingsController.save` → `system_settings` group `GENERAL` | Không có consumer product hiện tại | Chỉ Admin save/load lại form | Không đổi header/login/site identity |
| SMTP host/port/encryption/user/password/from/reply-to | `POST /admin/settings/email` → `EmailSettingsController.save` → group `SMTP` | `smtp.host` nonblank; sender dựng được và network/auth thành công | Forgot-password direct mail; outbox mail cho `LESSON_PUBLISHED`, `ASSIGNMENT_PUBLISHED`; test email | Không gửi email cho mọi notification; join/class/grading chủ yếu in-app |
| Google OAuth client id/secret/scope | `POST /admin/settings/oauth` → `OauthSettingsController.save` → group `OAUTH` | Cả id + secret nonblank → dynamic Google `ClientRegistration` | Hiện nút Google, authorization/callback, bind Google sub vào local user có sẵn | Không self-register/cấp role; hiện còn bỏ sót check inactive |
| KRDICT key/base URL | `POST /admin/settings/dictionary` → `KoreanDictionarySettingsController.save` → group `DICTIONARY` | API key nonblank + base URL hợp lệ/provider trả XML | Global dictionary lookup; Discovery lookup/save word; Flashcard dictionary helper | Không bật Practice AI hoặc generative AI |
| Global AI provider base URL/key/model/enabled/order | `POST /admin/settings/ai` → `AiSettingsController.save` → `ai_providers` | Ít nhất một provider enabled; client thử theo `display_order` | Test AI question, Flashcard AI, Discovery AI editorial | Không bật bất kỳ Practice purpose nào |
| System prompt `name/content/enabled` | `POST /admin/settings/ai/prompts` → `AiSystemPromptController.save` → prompt table | Exact runtime name phải được consumer code lookup | `AI_QUESTION_GENERATOR`, `AI_FLASHCARD_GENERATOR`, `DISCOVERY_NEWS_EDITOR`, instruction bổ sung `PRACTICE_PDF_AUTHORING` | Tên tùy ý không tự tạo feature; prompt không thay model/transport |
| Practice provider profile | `POST /admin/settings/practice-ai/profiles` → `PracticeAiControlPlaneController.saveProfile` | Profile enabled, base URL/family/credential hợp lệ | Chỉ tạo provider authority để binding có thể dùng | Chưa chọn purpose/model thì không AI call |
| Practice purpose binding | `POST /admin/settings/practice-ai/bindings/{purpose}` → `saveBinding` | Binding + profile enabled, model/capabilities/limits parse, secret resolve, revision current | Chỉ capability exact purpose: PDF, explanation, Writing, Speaking STT/eval/TTS | Không fallback purpose khác/global; direct-audio/ADC/presets còn gate riêng |
| `GENERAL_UPLOADS` Local/R2 | `POST /admin/settings/storage-profiles` → `StorageProfileController.save` → `storage_profiles` | Profile exact code active+complete; Local còn cần `app.storage-profiles.allow-local` | New avatar, lesson/library file, flashcard/test image writes và exact-profile reads | Không bật Practice authoring/speaking storage; không migrate object cũ |
| `PRACTICE_AUTHORING` Local/R2 | Cùng profile endpoint | Active+complete exact profile | Lecturer asset/PDF/ảnh/audio prompt authoring, material delivery/lifecycle | Không lưu learner speaking audio; không bật AI |
| `PRACTICE_SPEAKING` Local/R2 | Cùng profile endpoint | Active+complete exact profile + speaking-media feature gates | Learner speaking temporary/ready media, playback, STT input, cleanup | Không tự bật upload/playback controller hoặc evaluator nếu flags/bindings tắt |

## Những gate cấu hình ngoài Admin form

Một số capability còn bị `application.properties`/environment gate trước khi bean/endpoint tồn tại:

| Property | Default source | Effect |
|---|---|---|
| `app.storage-profiles.allow-local` | `application.properties:141`, default `false` | Cho phép profile backend `LOCAL`; false làm resolver fail-closed |
| `app.practice.speaking-media.upload-api-enabled` | `application.properties:200`, default `false` | Tạo/không tạo `PracticeSpeakingMediaController` |
| `app.practice.speaking-media.playback-api-enabled` | dòng 201, default `false` | Tạo playback controller và hiện playback path |
| `app.practice.speaking-media.cleanup-worker-enabled` | dòng 202, default `false` | Chạy physical media cleanup worker |
| `app.practice.speaking-direct-audio.reviewer-access-audit.retention-worker-enabled` | dòng 219, default `false` | Xóa audit event sau `delete_after`; tắt thì deadline retention không tự tạo physical DELETE |
| `app.practice.speaking-prompt-authoring.worker-enabled` | dòng 57, default `false` | Chạy STT/TTS prompt task worker |
| `app.practice.attempt-evaluation.worker-enabled` | dòng 39, default `true` | Worker xử lý Writing/Speaking jobs |
| `app.practice.attempt-deadline.worker-enabled` | dòng 42, default `true` | Worker finalize/discard attempt quá hạn |
| `app.practice.explanation-generation.worker-enabled` | dòng 228, default `true` | Preparation/reconciliation/generation explanation nền |
| `app.practice.speaking-direct-audio.authorization.enabled` | dòng 210, default `false` | Cho phép authorization lifecycle, chưa đồng nghĩa live score |
| ba `reviewer-*-enabled` direct-audio | dòng 214-216, default `false` | Tạo reviewer playback/inspection/page endpoints tương ứng |

Một Admin save DB không thể override các property khởi động này. Ngược lại, bật property nhưng thiếu storage profile/AI binding/consent/ownership vẫn fail-closed.

Chi tiết:

- [01_GENERAL_SMTP_OAUTH_DICTIONARY.md](workflows/admin/01_GENERAL_SMTP_OAUTH_DICTIONARY.md)
- [02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md](workflows/admin/02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md)
- [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md)
- [04_STORAGE_PROFILES_R2_LOCAL.md](workflows/admin/04_STORAGE_PROFILES_R2_LOCAL.md)
- [07_BACKGROUND_JOBS_FAILURE_RETENTION.md](workflows/practice/07_BACKGROUND_JOBS_FAILURE_RETENTION.md)
