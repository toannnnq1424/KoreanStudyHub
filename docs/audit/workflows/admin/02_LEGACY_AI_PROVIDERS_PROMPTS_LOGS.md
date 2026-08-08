# Workflow audit: AI provider, system prompt và request log toàn hệ thống

Đây là control plane AI dùng chung cho Tests, Flashcards và Korea Discovery. Nó **không phải** Practice AI control plane và Practice không fallback sang danh sách provider này. Riêng catalog system prompt có một key được Practice PDF authoring đọc làm lớp hướng dẫn sư phạm; transport/model vẫn lấy từ Practice binding.

## 1. Admin thêm provider/model toàn hệ thống

### Mở form và nhập field

Admin có `PERM_system.ai` mở danh sách:

```text
GET /admin/settings/ai
```

`AiSettingsController.list`, `src/main/java/com/ksh/features/admin/settings/controller/AiSettingsController.java:80-84`, gọi `AiProviderService.listRows()` và render `templates/admin/settings-ai.html`. Nút **“Thêm provider”** mở:

```text
GET /admin/settings/ai/new
```

`AiSettingsController.create`, dòng 92-97, render `settings-ai-form.html`. Form ở `templates/admin/settings-ai-form.html:93-164` có:

| Field | Dòng | Ý nghĩa runtime |
|---|---:|---|
| `name` | 103-109 | tên/log snapshot; không quyết định dialect |
| `model` | 113-119 | gửi nguyên văn trong JSON `model` |
| `baseUrl` | 123-132 | hệ thống tự nối `/chat/completions` |
| `apiKey` | 136-146 | header `Authorization: Bearer ...` |
| `enabled` | 150-155 | có nằm trong fallback chain hay không |

Bấm **“Thêm provider”** hoặc **“Lưu thay đổi”** gửi form:

```text
POST /admin/settings/ai
Content-Type: application/x-www-form-urlencoded
```

`AiSettingsController.save`, dòng 143-184:

1. Principal phải là `KshUserDetails`.
2. Tên không được trùng; create bắt buộc API key.
3. Create gọi `AiProviderService.create`; edit gọi `update`.
4. Redirect `GET /admin/settings/ai` với flash.

`AiProviderService.create`, `.../AiProviderService.java:165-178`, normalize trailing slash của Base URL, append `display_order=max+1`, ghi actor. `update`, dòng 190-209, giữ API key cũ khi input blank hoặc `********`. Provider list đọc trực tiếp DB ở mỗi AI call, nên save/toggle có hiệu lực ở request kế tiếp mà không restart (`AiClient.java:34-40`).

### Provider được dùng theo chuỗi fallback như nào

Khi một workflow gọi AI, `AiClient.chat`/`chatJsonObject`, `src/main/java/com/ksh/features/ai/client/AiClient.java:141-182`:

1. Query `AiProviderRepository.findEnabledOrdered()`.
2. Không có provider bật: throw “Chưa cấu hình AI provider nào đang bật”.
3. Đi theo `display_order` tăng dần.
4. Mỗi provider nhận cùng system/user contract; network, HTTP, response JSON hoặc embedded provider error đều chuyển sang provider tiếp theo.
5. Chỉ throw aggregated error sau khi tất cả provider bật thất bại.

Mỗi lần thử gửi chính xác:

```text
POST {normalizedBaseUrl}/chat/completions
Authorization: Bearer {apiKey}
Content-Type: application/json
Accept: application/json
```

Payload tại `AiClient.java:240-260`:

```json
{
  "model": "<provider.model>",
  "max_tokens": 2048,
  "stream": false,
  "messages": [
    {"role":"system","content":"<optional system prompt>"},
    {"role":"user","content":"<workflow material>"}
  ],
  "response_format": {"type":"json_object"}
}
```

`response_format` chỉ có trong `chatJsonObject`. Response bắt buộc có `choices[0].message.content`; usage nếu có đọc từ `usage.prompt_tokens`, `completion_tokens`, `total_tokens` (`AiClient.java:325-383`). Connect timeout 5 giây, read timeout 60 giây, success body tối đa 1 MiB, error prefix tối đa 2 KiB.

### Field nào thực sự mở khóa field nào

- `enabled=true` mới đưa provider vào chain; chỉ lưu key/model nhưng để tắt thì không workflow nào gọi.
- `baseUrl` phải là root tương thích OpenAI; nếu admin nhập URL đã chứa `/chat/completions`, code vẫn nối thêm và call sai.
- `model` không được kiểm tra khi save; tên sai chỉ lộ ra khi Test hoặc khi product call.
- `name` chỉ phục vụ hiển thị/log và uniqueness.
- Provider đầu tiên thành công chặn các provider sau; không round-robin, không weighting, không chọn theo loại workflow.

Nhập đủ provider bật mở ba nhóm data-plane:

1. AI question generation cho một Test (`SOURCE_QUESTION_GEN`): material PDF/DOCX/text → preview JSON → user xác nhận mới ghi đề; xem `product/TESTS_WORKFLOWS.md`.
2. AI flashcard generation (`SOURCE_FLASHCARD_GEN`): material → JSON cards preview; xem `product/FLASHCARDS_WORKFLOWS.md`.
3. Korea Discovery editorial (`SOURCE_DISCOVERY_NEWS`): source news → JSON `titleVi/excerptVi/bodyVi`; xem `product/DISCOVERY_DICTIONARY_WORKFLOWS.md`.

Không có `AiClient` trong Practice evaluation/STT/TTS/explanation. Các luồng đó dùng `PracticeAiBindingResolver` và fail closed nếu Practice binding thiếu.

## 2. Các thao tác provider trên UI

### Bật/tắt

Icon power trong list submit form:

```text
POST /admin/settings/ai/{id}/toggle
```

`AiSettingsController.toggle`, dòng 187-203, gọi `AiProviderService.toggleEnabled`; service lock row `findByIdForUpdate`, flip flag (`AiProviderService.java:218-225`). Tắt provider chỉ bỏ qua ở call mới; không hủy HTTP request đang chạy.

### Xóa

JS `static/js/admin-settings-ai.js:173-225` mở modal rồi set action:

```text
POST /admin/settings/ai/{id}/delete
```

`AiSettingsController.delete`, dòng 206-214, hard-delete row. Display order của các row sống sót không compact; provider mới append `max+1`. Log cũ giữ snapshot provider name/model và vẫn xem được.

### Hiện/copy API key

Nút eye/copy không có key trong HTML ban đầu. JS dòng 66-130 gọi:

```text
GET /admin/settings/ai/{id}/key
Accept: application/json
```

`AiSettingsController.revealKey`, dòng 245-250, trả `{"ok":true,"apiKey":"..."}`. Endpoint cùng permission `PERM_system.ai`, nhưng đây là GET trả secret rõ; JS chỉ bỏ khỏi DOM khi user bấm ẩn, không có re-auth hay audit riêng cho reveal.

### Kiểm tra provider

Nút **“Kiểm tra”** dùng `admin-settings-ai.js:134-170` gửi:

```text
POST /admin/settings/ai/{id}/test
Accept: application/json
```

`AiSettingsController.test`, dòng 226-233, gọi `AiProviderService.test`. Service `AiProviderService.java:260-273` gọi **đúng provider được chọn**, không fallback:

- user message `ping`;
- `max_tokens=2048`;
- source log `TEST_CONNECTION`.

Provider không cần `enabled=true` để test vì service lookup theo id và gọi `AiClient.callOne` trực tiếp. Test chỉ chứng minh endpoint/key/model trả được một OpenAI-compatible completion; không chứng minh output contract cụ thể của Question/Flashcard/Discovery.

## 3. System Prompt: tên kỹ thuật quyết định consumer

Admin mở:

```text
GET /admin/settings/ai/prompts
```

Form `templates/admin/settings-ai-prompts.html:137-210` nhập `name`, `description`, `content`, `enabled`, submit:

```text
POST /admin/settings/ai/prompts
```

`AiSystemPromptController.save`, `.../AiSystemPromptController.java:101-131`, kiểm name unique rồi create/update. `AiSystemPromptService.create/update`, `.../AiSystemPromptService.java:96-129`, trim và lưu full content tối đa 20.000 ký tự.

Toggle/delete:

```text
POST /admin/settings/ai/prompts/{id}/toggle
POST /admin/settings/ai/prompts/{id}/delete
```

Controller dòng 133-161; delete là hard delete. Disabled/deleted prompt làm consumer dùng fallback built-in, không tắt capability AI.

### Chỉ bốn tên có consumer thật

Source query `findByNameAndEnabledTrue(...)` cho thấy các key runtime sau:

| Prompt name chính xác | Consumer | Transport/model |
|---|---|---|
| `AI_QUESTION_GENERATOR` | `AiQuestionPromptBuilder.systemPrompt`, `.../AiQuestionPromptBuilder.java:77-83` | global `AiClient` fallback |
| `AI_FLASHCARD_GENERATOR` | `AiFlashcardPromptBuilder.systemPrompt`, `.../AiFlashcardPromptBuilder.java:65-71` | global `AiClient` fallback |
| `DISCOVERY_NEWS_EDITOR` | `NewsAiEditorialService.systemPrompt`, `.../NewsAiEditorialService.java:173-179` | global `AiClient` fallback |
| `PRACTICE_PDF_AUTHORING` | `PracticePdfAiOrchestrator.adminPrompt`, `.../PracticePdfAiOrchestrator.java:137-149` | Practice purpose binding |

Name khác chỉ nằm trong catalog, không tự gắn vào màn hình nghiệp vụ. Với ba global prompt, code luôn append một `RUNTIME_CONTRACT` bất biến sau nội dung Admin; prompt Admin không thể đổi schema/safety constraints. Với `PRACTICE_PDF_AUTHORING`, content chỉ trở thành developer pedagogical instruction; immutable system contract và JSON schema của Practice vẫn có ưu tiên cao hơn (`PracticePdfAiOrchestrator.java:151-190`).

`AiSystemPromptService.java:24-25` nói service catalog không tự gọi `AiClient`; điều đó đúng ở tầng service nhưng không có nghĩa prompt vô dụng: bốn consumer trên đọc repository trực tiếp.

## 4. Request logs: quan sát, không mở capability

Mỗi provider attempt của global `AiClient` gọi `AiRequestLogger.logSuccess/logFailure`, `src/main/java/com/ksh/features/ai/log/AiRequestLogger.java:56-81`. Một row lưu:

- provider id nếu row còn tồn tại, snapshot name/model;
- status `SUCCESS|FAILED`;
- source `TEST_CONNECTION|CHAT|QUESTION_GEN|DISCOVERY_NEWS|FLASHCARD_GEN`;
- token usage nullable, duration, bounded error, actor id.

Admin mở:

```text
GET /admin/settings/ai/logs?provider=<name>&status=SUCCESS|FAILED&page=0
```

`AiLogsController.list`, `.../AiLogsController.java:56-70`, sanitize status, gọi `AiLogQueryService.list/totals/providerNames` và render read-only. Không có POST/delete/purge endpoint; log UI không bật/tắt AI và không có retention worker (`AiLogsController.java:21-27`). History tab của một provider dùng `GET /admin/settings/ai/{id}/edit?tab=history&page=n`, gọi `listByProvider` ở `AiSettingsController.java:108-132`.

Practice control plane ghi bảng/audit riêng; các call Practice không xuất hiện trong `ai_request_logs` này.

### Màn hình log lấy dữ liệu bằng query nào

Template render chính xác là `templates/admin/settings-ai-logs.html`; đây không phải màn hình giữ sẵn log trong JavaScript. Mỗi lần mở hoặc đổi bộ lọc đều tạo một GET mới:

```text
GET /admin/settings/ai/logs?provider=<exact-name>&status=SUCCESS|FAILED&page=<0-based>
    -> AiLogsController.list()
    -> AiLogQueryService.list(filter, PageRequest.of(page, 20))
    -> AiRequestLogRepository.findFiltered(...)
```

`AiLogsController.java:61–70` đồng thời thực hiện ba read độc lập cho model:

1. `list` gọi JPQL `findFiltered` tại `AiRequestLogRepository.java:32–38`: hai filter nullable, sort cố định `createdAt DESC, id DESC`;
2. `totals` gọi `sumTokens` tại repository dòng 51–56: `COUNT/SUM` trên **toàn bộ tập đang lọc**, không chỉ 20 row của trang;
3. `providerNames` gọi `findDistinctProviderNames` dòng 66–67: lấy snapshot name từ chính bảng log, nên provider đã xóa vẫn còn trong dropdown.

`static/js/admin-settings-ai-logs.js:18–29` chỉ bắt sự kiện `change` của select, bỏ input `page` rồi submit lại form GET. Nó không gọi AJAX, không cache row và không mutate DB. Vì vậy filter “nhanh” vẫn là query server mới; JavaScript chỉ bỏ bớt một click.

### Màn hình prompt lấy dữ liệu và xóa như nào

`templates/admin/settings-ai-prompts.html` được `AiSystemPromptController.list` render qua `populate` (`AiSystemPromptController.java:66–72,166–169`). Hàm này gọi `AiSystemPromptService.listRows`; service dùng `AiSystemPromptRepository.findAllByOrderByNameAsc()` (`AiSystemPromptService.java:51–60`) trước khi trả HTML. `static/js/admin-settings-ai-prompts.js:17–79` không lưu prompt ở client: nó chỉ dựng action `POST /admin/settings/ai/prompts/{id}/delete`, hiển thị modal, trap focus và để form có CSRF submit tới controller. Sau redirect, GET list query DB lại; modal state không được persist.

## Tóm tắt luồng nút “Thêm provider”

```text
[Admin nhập name/baseUrl/model/apiKey + bật enabled]
    -> POST /admin/settings/ai
    -> AiSettingsController.save()
    -> AiProviderService.create()/update()
    -> ai_providers
    -> request product kế tiếp query findEnabledOrdered()
    -> POST {baseUrl}/chat/completions
    -> parse choices[0].message.content
    -> ghi ai_request_logs
    -> product parser validate JSON riêng
    -> preview/result quay lại UI
```

## Method-level read/mutation ledger của các nhánh phụ

| Handler exact | UI → query/mutation → response |
|---|---|
| `AiSettingsController.edit` | `GET /admin/settings/ai/{id}/edit?tab=info|history&page=n` → `AiProviderService.findDetailById/loadForm`; tab history mới gọi `AiLogQueryService.listByProvider(id,PageRequest.of(max(page,0),10))`; missing redirect list + flash, còn hợp lệ render `settings-ai-form.html` (`AiSettingsController.java:108–132`). |
| `AiSystemPromptController.edit` | `GET /admin/settings/ai/prompts/{id}/edit` → `AiSystemPromptService.loadForm/findById`; tồn tại thì query lại catalog `listRows` và render form inline, không thì redirect + flash (`:82–91`). |
| `AiSystemPromptController.toggle` | POST form có CSRF → `AiSystemPromptService.toggleEnabled`; update `enabled/updatedBy`, redirect catalog; prompt disabled làm runtime consumer fallback, không tắt provider (`:134–149`). |
| `AiSystemPromptController.delete` | POST → `AiSystemPromptService.delete` hard-delete row nếu có, redirect + flash (`:153–160`); consumer request sau dùng built-in fallback. |
