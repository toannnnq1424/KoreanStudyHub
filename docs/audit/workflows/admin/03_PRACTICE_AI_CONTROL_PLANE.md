# Workflow audit: Admin cấu hình Practice AI provider/model theo purpose

Practice có control plane riêng tại `/admin/settings/practice-ai`. Việc nhập một API key **chưa đủ** để mở AI: runtime chỉ chạy khi profile hợp lệ + profile bật + binding đúng purpose tồn tại + binding bật + capability/limits parse được. Không bước nào fallback sang `/admin/settings/ai`.

## 1. Bảy capability độc lập

Enum nguồn sự thật là `src/main/java/com/ksh/features/practice/ai/controlplane/PracticeAiPurpose.java:5-33`:

| Purpose | Thao tác được mở khi ready | Provider path |
|---|---|---|
| `PRACTICE_PDF_AUTHORING` | giảng viên tạo candidate từ PDF/text bằng AI | `/chat/completions` strict JSON schema |
| `PRACTICE_RL_EXPLANATION` | sinh explanation/evidence cho Reading/Listening | `/chat/completions` strict JSON schema, có image input |
| `PRACTICE_WRITING_EVALUATION` | chấm bài Writing sau submit | `/chat/completions` strict JSON schema, có image input |
| `PRACTICE_SPEAKING_EVALUATION` | chấm transcript Speaking sau STT | `/chat/completions` strict JSON schema |
| `PRACTICE_SPEAKING_STT` | audio learner/teacher → transcript tiếng Hàn | `/audio/transcriptions` multipart |
| `PRACTICE_SPEAKING_TTS` | text đề bài giảng viên → MP3 | `/audio/speech` JSON → audio bytes |
| `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` | nhánh đánh giá trực tiếp audio dự kiến | capability đặc biệt; hiện fail closed |

Mỗi purpose là một binding riêng; một model cho Writing không tự mở Speaking hay PDF.

## 2. Bước 1 — Admin tạo provider profile

### UI và request

Từ `GET /admin/settings/practice-ai`, nút **“Thêm nhà cung cấp”** tại `templates/admin/settings-practice-ai.html:18-26` mở:

```text
GET /admin/settings/practice-ai/profiles/new
```

`PracticeAiControlPlaneController.newProfile`, `.../PracticeAiControlPlaneController.java:61-66`, render `settings-practice-ai-profile-form.html`. Form `templates/admin/settings-practice-ai-profile-form.html:31-65` gửi:

| Field | Dòng | Ảnh hưởng |
|---|---:|---|
| `profileCode` | 59 | identity bất biến, uppercase; không phải model |
| `displayName` | 48 | chỉ UI/Admin log |
| `providerFamily` | 60 | hiện chỉ chấp nhận `OPENAI_COMPATIBLE` |
| `credentialMode` | 50 | `STATIC_BEARER` hoặc `GOOGLE_CLOUD_ADC` |
| `baseUrl` | 49 | root endpoint; runtime nối path theo purpose |
| `credentialSecret` | 51 | bearer; blank/`********` giữ secret khi edit |
| `enabled` | 52 | gate toàn bộ binding dùng profile |
| `revision` | hidden 33 | optimistic concurrency |

Bấm **“Lưu và chọn model”**:

```text
POST /admin/settings/practice-ai/profiles
Content-Type: application/x-www-form-urlencoded
```

`PracticeAiControlPlaneController.saveProfile`, `.../PracticeAiControlPlaneController.java:82-126`, validate rồi gọi `PracticeAiControlPlaneAdminService.saveProfile`. Create thường redirect đến:

```text
GET /admin/settings/practice-ai/bindings/PRACTICE_PDF_AUTHORING/edit?profileId={id}
```

Profile direct-audio preset redirect purpose direct audio (`PracticeAiControlPlaneController.java:105-119`).

### Service ghi và kiểm gì

`PracticeAiControlPlaneAdminService.saveProfile`, `.../PracticeAiControlPlaneAdminService.java:183-248`:

1. Normalize `profileCode`; fixed preset phải khớp allowlist displayName/baseUrl/family/credential mode.
2. `STATIC_BEARER` create bắt buộc secret; edit giữ secret cũ khi masked/blank.
3. `GOOGLE_CLOUD_ADC` cấm lưu secret (`ADC_PROFILE_MUST_NOT_STORE_SECRET`).
4. Code duplicate bị từ chối; edit không được đổi code.
5. Revision phải khớp row DB, nếu không `PROFILE_REVISION_CONFLICT`.
6. Profile thường có thể lưu enabled theo checkbox; fixed preset luôn được lưu disabled.

Profile list không chứa secret. Nút **“Hiện / ẩn”** dùng `static/js/admin-settings-practice-ai.js:299-331` gọi:

```text
GET /admin/settings/practice-ai/profiles/{id}/secret
```

Controller dòng 179-188 trả JSON `no-store`; fixed preset cố định không cho reveal. Endpoint dùng permission `PERM_system.ai` nhưng không yêu cầu re-auth.

### Bật/tắt/xóa profile

```text
POST /admin/settings/practice-ai/profiles/{id}/toggle
POST /admin/settings/practice-ai/profiles/{id}/delete
```

Controller dòng 149-177. Toggle lock row và tăng revision; fixed xAI/Groq preset bị chặn `PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED`. Delete chỉ cho profile không còn binding (`PracticeAiControlPlaneAdminService.java:250-273`). Tắt profile khiến tất cả binding trỏ tới nó fail closed ngay ở request mới.

## 3. Bước 2–3 — Gán profile + model cho từng purpose

Admin bấm **“Chọn model”** trên card purpose (`settings-practice-ai.html:142-185`):

```text
GET /admin/settings/practice-ai/bindings/{purpose}/edit
```

`PracticeAiControlPlaneController.editBinding`, dòng 190-210, nạp profile list, binding hiện tại, capability bắt buộc và render `settings-practice-ai-binding-form.html`.

### Field binding

Form `templates/admin/settings-practice-ai-binding-form.html:24-140` gửi:

| Field | Dòng | Runtime |
|---|---:|---|
| `purpose` | hidden 26 | phải khớp path enum |
| `providerProfileId` | 32-58 | profile duy nhất cho purpose; không fallback |
| `model` | 60-119 | gửi nguyên văn cho provider |
| `enabled` | 121 | gate purpose |
| `pdfImageInput` | 129 | thêm image content cho PDF purpose |
| `directAudioInput` | 120 | bắt buộc cho direct-audio, nhưng checkbox không tự chứng minh capability |
| `retentionCode` | 132 | policy identity được snapshot/audit |
| `connectTimeoutMs` | 133 | 100-30.000 ms |
| `readTimeoutMs` | 134 | 1.000-120.000 ms |
| `maxRetries` | 135 | 0-3; chỉ status retryable |
| `maxRequestBytes` | 136 | request-size fence |
| `maxResponseBytes` | 137 | response-size fence |
| evidence IDs | 130-131 | direct-audio: non-training + retention bắt buộc khi bật |
| `revision` | hidden 26 | optimistic concurrency |

Model suggestions tại dòng 69-113 là hard-coded UI catalog; trang **không gọi** provider `/models`. User vẫn có thể gõ model tùy chỉnh. Vì vậy chọn một suggestion không chứng minh account/key thật có quyền dùng model; phải chạy capability test.

Bấm **“Lưu mục đích”**:

```text
POST /admin/settings/practice-ai/bindings/{purpose}
Content-Type: application/x-www-form-urlencoded
```

`PracticeAiControlPlaneController.saveBinding`, `.../PracticeAiControlPlaneController.java:212-249`, chống purpose mismatch, gọi `PracticeAiControlPlaneAdminService.saveBinding`.

Service `.../PracticeAiControlPlaneAdminService.java:284-351`:

1. Load profile theo id; browser không thể tự gửi baseUrl/secret khác trong binding.
2. Build canonical capability JSON và limits JSON qua `PracticeAiControlPlaneCodec`, parse lại để fail closed.
3. Direct-audio bắt buộc `directAudioInput=true`; khi enable còn bắt buộc verified provider/model và hai evidence ID.
4. Update row đang lock khi revision khớp, hoặc tạo binding mới.
5. Lưu model, dialect cố định `OPENAI_COMPATIBLE_V1`, capability, limits, retention, enabled, evidence, actor; revision tăng khi update.

Toggle card gửi:

```text
POST /admin/settings/practice-ai/bindings/{purpose}/toggle
```

Controller dòng 251-265; service dòng 353-370 lock binding, re-check direct-audio gates rồi flip. Tắt binding không xóa cấu hình và không hủy call đang chạy; request mới fail closed.

## 4. Runtime resolve: điều kiện thật sự để “được mở khóa”

Mọi client Practice gọi `PracticeAiBindingResolver.resolve(purpose)`, `src/main/java/com/ksh/features/practice/ai/controlplane/PracticeAiBindingResolver.java:25-128`.

Resolver chỉ trả `PracticeAiResolvedBinding` khi:

1. Có binding đúng `purposeCode`.
2. Profile không phải fixed preset đang chờ verification.
3. `binding.enabled=true` **và** `profile.enabled=true`.
4. Provider family là `OPENAI_COMPATIBLE`, dialect là `OPENAI_COMPATIBLE_V1`.
5. Model, retention, Base URL, capability JSON, limits JSON hợp lệ.
6. Credential secret không blank.
7. Direct-audio còn phải đủ evidence, registry verified, credential mode đúng và runtime auth ready.

Snapshot gồm binding revision, profile revision, model, capabilities, limit digests và retention. Trước/giữa các call nhạy cảm, `assertCurrent`, dòng 43-65, so lại snapshot; admin đổi/tắt cấu hình giữa chừng sinh `PROVIDER_BINDING_CHANGED` thay vì tiếp tục bằng authority cũ.

Transport chung `RestClientPracticeAiProviderTransport.exchange`, `.../RestClientPracticeAiProviderTransport.java:30-73`, chỉ allowlist ba path:

```text
POST {profile.baseUrl}/chat/completions
POST {profile.baseUrl}/audio/transcriptions
POST {profile.baseUrl}/audio/speech
Authorization: Bearer {credentialSecret}
```

Nó áp connect/read timeout từ binding, giới hạn request JSON/response bytes và không log secret.

### Mapping consumer cụ thể

- PDF authoring: `PracticePdfAiOrchestrator` lấy identity `PRACTICE_PDF_AUTHORING`, gọi strict structured port; response phải đúng `practice-pdf-authoring-output-v1` trước khi tạo candidate.
- Reading/Listening explanation: `ReadingListeningExplanationClient` lấy `PRACTICE_RL_EXPLANATION`; output phải đúng schema/evidence contract rồi mới vào editorial workflow.
- Writing: `WritingEvaluationClient` lấy `PRACTICE_WRITING_EVALUATION`; submit không đồng bộ chờ AI, worker xử lý và normalize JSON contract.
- Speaking STT: `OpenAiSpeakingTranscriptionClient` và authoring STT adapter lấy `PRACTICE_SPEAKING_STT`, gửi multipart audio.
- Speaking evaluation: `OpenAiCompatibleSpeakingEvaluationClient` lấy `PRACTICE_SPEAKING_EVALUATION`, chỉ nhận transcript/evidence, không nhận raw learner audio ở live path.
- Speaking TTS: `OpenAiSpeakingPromptTtsAdapter` lấy `PRACTICE_SPEAKING_TTS`, nhận binary audio và lưu thành lecturer asset.
- Direct audio: source có registry/control-plane entities, nhưng production evaluation hiện vẫn STT → transcript evaluator; xem `practice/06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md`.

## 5. Nút “Kiểm tra” gửi fixture thật như nào

Chỉ card binding đã cấu hình+bật mới hiện nút ở `settings-practice-ai.html:183-184`. JS `static/js/admin-settings-practice-ai.js:334-361` gửi:

```text
POST /admin/settings/practice-ai/bindings/{purpose}/test
Accept: application/json
```

`PracticeAiControlPlaneController.testBinding`, dòng 268-285, gọi `PracticeAiCapabilityTestService.test`. Service `.../PracticeAiCapabilityTestService.java:25-51` resolve binding, ghi test-run với revision, assert snapshot còn current, chạy probe rồi ghi `PASS|FAIL|CANCELLED`, duration/error code.

`BoundedPracticeAiCapabilityProbe`, `.../BoundedPracticeAiCapabilityProbe.java:40-152` gửi:

- Structured purposes: `/chat/completions`, max 64 tokens, strict schema bắt model trả đúng `{"purpose":"<PURPOSE>","ok":true}`; nếu capability image bật, kèm PNG 1x1 base64.
- STT: `/audio/transcriptions`, multipart `model`, `language=ko`, `response_format=json`, file silent WAV; response phải có string `text`.
- TTS: `/audio/speech`, body `model`, `input="안녕하세요."`, `voice="alloy"`, `response_format="mp3"`; body không rỗng và Content-Type phải `audio/*`.
- Direct audio: probe luôn throw `DIRECT_AUDIO_DARK_ROLLOUT_REQUIRED`, chưa có test PASS công khai.

JSON trả UI:

```json
{
  "ok": true,
  "status": "PASS",
  "errorCode": null,
  "bindingRevision": 3,
  "durationMs": 842
}
```

PASS là bằng chứng đúng fixture tại đúng revision, không tự toggle binding. Status card chỉ hiện “Sẵn sàng” khi binding enabled và lần test gần nhất PASS đúng current revision (`PracticeAiSettingsDtos.BindingRow.statusCode`, `.../PracticeAiSettingsDtos.java:183-209`).

## 6. Các nhánh có UI nhưng hiện chưa thể unlock production

### Google Cloud ADC

Form cho chọn `GOOGLE_CLOUD_ADC` và cấm lưu secret (`PracticeAiControlPlaneAdminService.java:194-203`), nhưng resolver hiện vẫn gọi `required(profile.getCredentialSecret())` không phân nhánh credential mode tại `PracticeAiBindingResolver.java:112`. Transport cũng luôn dựng `Authorization: Bearer {credentialSecret}` ở `RestClientPracticeAiProviderTransport.java:47-52`; không có token-source ADC bean trong transport này.

Kết luận: profile ADC có thể được lưu nháp, nhưng không resolve/call được runtime hiện tại. UI direct-audio enterprise cũng ghi rõ fail closed vì thiếu ADC adapter (`settings-practice-ai-binding-form.html:91-94`).

### xAI/Groq fixed preset

Nút **“Tạo profile tắt”** gửi:

```text
POST /admin/settings/practice-ai/profiles/presets/{presetKey}
```

Controller dòng 128-147, service tạo endpoint allowlisted nhưng `enabled=false`. Toggle bị chặn và resolver từ chối mọi fixed preset trước verification. Nhập key/model vào preset không mở request production.

### Direct audio evaluation

Ngay cả provider/model được registry nhận diện và đủ policy evidence, capability test chủ động trả `DIRECT_AUDIO_DARK_ROLLOUT_REQUIRED`; evaluator live vẫn transcript-only. Binding này là control-plane/readiness seam, không phải bằng chứng raw audio đang được gửi cho model.

## Tóm tắt thao tác

```text
[Thêm profile + secret + bật]
  -> POST /admin/settings/practice-ai/profiles
  -> practice_ai_provider_profiles
  -> [chọn purpose/profile/model/capabilities + bật]
  -> POST /admin/settings/practice-ai/bindings/{purpose}
  -> practice_ai_purpose_bindings
  -> [Kiểm tra]
  -> POST .../bindings/{purpose}/test
  -> fixture thật tới provider path đúng purpose
  -> capability test run PASS đúng revision
  -> user thực hiện Practice workflow
  -> resolver xác minh lại profile+binding+revision
  -> provider request thật
  -> strict parser/normalizer
  -> DB result/candidate/task
  -> UI poll/render kết quả
```

## Method-level ledger cho các nhánh profile/binding còn lại

| Handler exact | Query/mutation và response thật |
|---|---|
| `PracticeAiControlPlaneController.list` | `GET /admin/settings/practice-ai` → `adminService.profiles()` + `bindings()` + fixed presets; controller tự đếm enabled/configured và chọn binding chưa hoàn tất đầu tiên, render `settings-practice-ai.html` (`:55–58,287–309`). Không probe provider khi chỉ mở page. |
| `PracticeAiControlPlaneController.editProfile` | GET id → `adminService.profileForm(id)`; tồn tại render form edit với masked/retained credential, không tồn tại redirect + code `PROFILE_NOT_FOUND` (`:68–80`). |
| `PracticeAiControlPlaneController.createFixedProviderPreset` | POST preset key → service tạo một profile preset **disabled**, redirect form edit; preset lạ/lỗi trả flash code (`:128–146`). Không tự bind purpose hoặc gọi provider. |
| `PracticeAiControlPlaneController.toggleProfile` | POST → service transaction toggle profile, ghi actor/audit; runtime resolver request sau mới thấy enabled state. Missing/bound invariant lỗi redirect với safe code (`:149–164`). |
| `PracticeAiControlPlaneController.deleteProfile` | POST → `adminService.deleteProfile`; chỉ profile không còn binding mới được xóa, nếu vi phạm service ném và controller redirect lỗi (`:166–177`). |
| `PracticeAiControlPlaneController.revealSecret` | authenticated GET JSON → `adminService.revealSecret(id)`, trả `{ok,secret}` hoặc `{ok:false,errorCode}` với `Cache-Control: no-store`; secret không nằm trong initial HTML (`:179–188`). |
| `PracticeAiControlPlaneController.toggleBinding` | POST purpose → `adminService.toggleBinding(purpose,actor)`; chỉ đổi enabled flag/audit, không chạy fixture; redirect list (`:251–266`). Capability runtime còn phải qua profile/credential/model/capability gates ở mục 4. |
