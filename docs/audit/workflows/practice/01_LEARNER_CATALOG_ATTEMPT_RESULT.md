# Practice learner: từ kho đề đến attempt và kết quả

Tài liệu submit/chấm điểm chi tiết nằm ở `../PRACTICE_SUBMIT_AND_AI_EVALUATION.md`. Chương này bao phủ phần trước submit và phần người dùng đọc kết quả.

## 1. Tìm và lọc kho Practice

### Thao tác UI

Học viên mở `/practice`. `practice/index.html:91–140` có chip skill/Writing task; form tìm kiếm `GET /practice` ở dòng 140–151 gửi `q`, `skill`, `writingTask`. Nút tải thêm trong catalog dùng `GET /practice/catalog?batch=N` để lấy fragment card tiếp theo.

### Backend

- `PracticeController.index`, dòng 134–149, dựng `PracticeCatalogQuery` và gọi `PracticeCatalogService.loadBatch(userId, query)`.
- `catalogBatch`, dòng 151–166, gọi cùng service nhưng trả fragment `practice/fragments/catalog-cards`, không trả cả layout.
- Service chỉ trả published version user được nhìn thấy; learner identity được truyền xuống để tính resume/recent/progress. Search/filter không làm thay đổi DB.

### UI kết quả

Full GET render `practice/index`; batch GET append card. Card dẫn đến `GET /practice/sets/{setId}`. Lecturer thấy thêm form `POST /practice/manage/create` ở `index.html:37`, nhưng learner không được vào `/practice/manage/**` do SecurityConfig và `PREAUTH_LECTURER`.

## 2. Mở bộ đề và bài test

1. Click card → `GET /practice/sets/{setId}`.
2. `PracticeController.setDetail`, dòng 169–181:
   - `PracticeLearnerAccessService.requireVisiblePublishedSet(setId,userId)` chặn unpublished/class ngoài scope;
   - `PracticeService.getPracticeSummary` nạp snapshot set/tests;
   - `PracticeDetailPageService.buildTestCards` gắn trạng thái attempt của user;
   - render `practice/set-detail`.
3. Click test card tại `set-detail.html:73` → `GET /practice/sets/{setId}/tests/{testId}`.
4. `testDetail`, dòng 184–218, xác minh test thật sự nằm trong set, nạp sections và build skill cards; render resume/new/history buttons trong `test-detail.html:99–181`.

Không có mutation trong hai GET. ID URL không đủ cấp quyền: set phải là published graph learner được phép xem.

## 3. Bấm “Bắt đầu” một skill

Form tại `test-detail.html:120–127` gửi:

```text
POST /practice/sets/{setId}/tests/{testId}/attempts
sectionId=<id>&mode=practice
```

`PracticeController.createAttempt`, dòng 235–252:

1. Kiểm visible published set.
2. `requireSection` buộc section thuộc đúng set + test.
3. Speaking không tạo player ngay: redirect speaking preflight.
4. Listening redirect listening preflight.
5. Reading/Writing gọi `startRestartableAttempt`, sau đó redirect `/practice/attempts/{id}?mode=practice`.

`PracticeService.startAttempt`, vùng dòng 1840–1905, tái sử dụng đúng attempt `IN_PROGRESS` nếu immutable version lock còn canonical; attempt cũ/stale bị discard; attempt mới khóa `publishedVersionId/setVersionId/testVersionId/sectionVersionId`, cấu hình deadline và save `IN_PROGRESS`. Vì attempt khóa published snapshot, giảng viên xuất bản version mới không đổi đề giữa lượt đang làm.

`GET` vào chính URL collection chỉ là fallback redirect về test detail (`PracticeController:443–447`), không tạo attempt; mutation bắt buộc POST + CSRF.

## 4. Listening preflight

### UI

`practice/listening-preflight.html:27–49`: học viên bấm phát audio thử; nút tiếp tục chỉ enable sau client check, rồi form POST tới action server cung cấp.

### Backend

- First start: `GET/POST /practice/sets/{setId}/tests/{testId}/sections/{sectionId}/listening-check` (`PracticeController:254–295`).
- Resume: `GET/POST /practice/attempts/{attemptId}/listening-check` (`396–440`).
- GET gọi `PracticeService.getListeningPreflightDelivery` hoặc attempt variant. Service chỉ chấp nhận canonical check audio/internal material reference; thiếu/unsafe reference redirect test detail với error.
- POST start/reuse attempt, xác minh immutable attempt snapshot vẫn có audio hợp lệ, ghi marker preflight trong `HttpSession`, redirect player.

Preflight marker không thay đổi điểm/attempt answers. Nếu user gõ thẳng player URL nhưng session chưa hoàn tất preflight, route player đẩy họ về check page.

## 5. Speaking preflight

### UI

`practice/speaking-preflight.html:51–89`: test speaker, ghi một sample browser-local, nghe lại; nút **Bắt đầu phần Nói** chỉ enable sau khi kiểm tra thành công và POST action.

### Backend

- First start: `GET/POST .../speaking-check`, controller dòng 297–341.
- Resume: `GET/POST /practice/attempts/{attemptId}/speaking-check`, dòng 343–394.
- POST yêu cầu feature `speaking-media.upload-api-enabled`; start/reuse attempt; `PracticeService.getSpeakingPlayerDelivery` xác minh mọi question là `SPEAKING`, immutable prompt/audio/delivery contract hợp lệ; ghi session marker rồi redirect player.
- Delivery sai hoặc feature tắt fail closed; attempt vừa tạo được xử lý/discard qua invalid-delivery handler, user trở lại test detail.

Sample microphone ở preflight không upload và không dùng làm bằng chứng chấm.

## 6. Mở/resume player

`GET /practice/attempts/{attemptId}` vào `PracticeController.attempt`, dòng 449–579:

1. Load attempt theo `(attemptId,userId)` và canonical route state.
2. Terminal attempt redirect result; discarded redirect test detail.
3. Speaking/Listening buộc preflight session marker.
4. Speaking render `practice/player-speaking` với immutable question delivery, upload/interrupt URLs.
5. Reading/Listening/Writing gọi `PracticeService.getAttemptPlayerView`, decode saved answers, redact answer authority khỏi learner view, đặt lockVersion và deadline; Writing render `player-writing`, objective render `player`.

Frontend autosave dùng `PUT /practice/attempts/{attemptId}/answers` tại `player-exam.js:572`. Contract/status conflict và deadline đã được phân tích tại `PRACTICE_SUBMIT_AND_AI_EVALUATION.md` §1.

`PracticeAttemptControllerAdvice`, `src/main/java/com/ksh/features/practice/controller/PracticeAttemptControllerAdvice.java:18-41`, giữ contract lỗi riêng cho `PracticeController`: optimistic/state conflict trả HTTP 409 với body message plain text; deadline server đã hết trả 410. Frontend phải xử lý status này, không giả định mọi lỗi autosave/submit là 500 HTML.

## 7. Hủy/thoát attempt

### Từ test detail

Form `test-detail.html:147–152` gửi `POST /practice/attempts/{id}/discard` kèm `setId/testId`. `PracticeController.discardAttempt:221–233` gọi `PracticeAttemptDiscardService.discardForOwner`, xóa preflight session và redirect test detail.

Service khóa owner attempt, chuyển `DISCARDED`; Speaking enqueue cleanup media thay vì xóa object mù trong request. `setId/testId` từ form chỉ dùng redirect, không quyết định ownership.

### Từ Speaking player

Click **“Thoát và hủy lượt”** (`player-speaking.html:73–74`, `player-speaking.js:542`) gửi `POST /practice/attempts/{id}/interrupt`. Controller dòng 796–805 yêu cầu in-progress Speaking owner, discard + clear preflight, trả `204 No Content`; JS điều hướng về `returnUrl`.

## 8. Speaking recording/upload rồi submit

`player-speaking.js:357` gửi mỗi recording dưới multipart field duy nhất `file` tới:

```text
POST /practice/attempts/{attemptId}/questions/{questionId}/speaking-media
```

Chuỗi upload/media được audit ở `04_SPEAKING_MEDIA_DIRECT_AUDIO.md`. Khi mọi question có media READY, hidden form `player-speaking.html:54–56` submit attempt. Speaking không nhận text field thay audio.

## 9. Nộp bài và result

Xem toàn bộ submit → objective Java scoring hoặc Writing/Speaking durable AI worker tại `../PRACTICE_SUBMIT_AND_AI_EVALUATION.md`.

Sau submit browser redirect:

```text
GET /practice/attempts/{attemptId}/result
```

`PracticeController.attemptResult`, dòng 807–818, owner-load attempt, gọi `PracticeResultAssembler.assemble`; Speaking bổ sung media model; render shell `practice/result` với fragment objective/writing/speaking theo state.

Click câu hỏi/detail:

```text
GET /practice/attempts/{attemptId}/result/detail?questionId=...
```

`attemptResultDetail`, dòng 821–834, gọi `PracticeResultDetailAssembler`; chọn đúng template objective, Writing hoặc Speaking. Assembler dùng published question version + stored answers/normalized feedback, không gọi provider khi render GET.

## 10. Bấm “Chấm lại”

Form result/detail gửi `POST /practice/attempts/{attemptId}/re-evaluate`, optional `questionId`. `PracticeController.reEvaluateAttempt:837–860` gọi `PracticeService.requestReEvaluation`:

- chỉ owner attempt và subjective skill/state được phép;
- fingerprint answers/media + current contract;
- Writing hỗ trợ full hoặc từng question; Speaking theo media snapshot;
- insert durable job idempotent; existing queued job trả thông báo thay vì nhân đôi;
- redirect result/detail với flash queued/info/error.

Provider không được gọi trong POST; worker xử lý sau. Objective answer key không bị AI/chấm lại sửa.

## 11. Tiến độ và font preference

- Click **Xem tiến độ** (`index.html:262`) → `GET /practice/progress`; `PracticeController.progress` từ dòng 1135 gọi `PracticeProgressService` dựng filter/window/facts từ terminal attempts và normalized result completeness; render `practice/progress`. Chart là enhancement, bảng facts là authority.
- `/practice/profile` chỉ redirect về progress (`PracticeController:1129–1133`).
- `/practice/preferences` (`PracticeKoreanFontPreferenceController:37–48`) render font options hiện tại.
- `POST /practice/preferences/korean-font` (`51–73`) validate font code từ allowlist, persist preference cho principal và redirect preferences. Font không thay đổi content/score, chỉ CSS class/font presentation.
- Trên GET của `PracticeController` và `PracticeDraftController`, `PracticeKoreanFontPreferenceAdvice`, `src/main/java/com/ksh/features/practice/preferences/PracticeKoreanFontPreferenceAdvice.java:15-49`, tự đọc preference cho role STUDENT/LECTURER và inject cùng model font. Advice bỏ qua POST và role khác, nên lựa chọn CSS đi theo tài khoản ở các page Practice được scope, không phải global theme setting.

## 12. Exact learner template/JS anchors: không thêm network hoặc state ẩn

Phần này neo các file UI thực tế để tránh suy diễn browser interaction thành API/DB mutation.

| File thật | Trigger / dữ liệu đầu vào | Hành vi và boundary source-true |
|---|---|---|
| `src/main/resources/static/js/practice/baekho-mascot.js` | Chỉ có trên `templates/practice/index.html`; root `data-baekho`, custom event `ksh:baekho-state` từ catalog | `fetch('/images/baekho/baekho_atlas.json')` chỉ tải static sprite metadata; fallback PNG khi lỗi. Toggle chỉ lưu `ksh.baekho.collapsed` trong `localStorage`, tôn trọng reduced motion; không gọi Practice controller/không ghi DB. |
| `src/main/resources/static/js/practice/practice-catalog.js` | `#pc-catalog-grid` và card `data-skill-cycle`/`data-primary-skill` của catalog server-rendered | Hover/focus xoay state mascot theo skill mỗi 2 giây (trừ reduced motion), dispatch custom event sang mascot. Không tải batch, không search và không mutate attempt; các GET catalog/filter vẫn là form/controller tại §1. |
| `src/main/resources/static/js/practice/practice-test-detail.js` | `templates/practice/test-detail.html` attempt history | Toggle chỉ mở/đóng lịch sử `data-attempt-toggle`; submit guard `data-confirm-discard` chỉ là confirm browser trước form `POST /practice/attempts/{id}/discard` đã mô tả §7. Nó không tự gọi discard API. |
| `src/main/resources/static/js/practice/listening-preflight.js` | `templates/practice/listening-preflight.html` audio mẫu và form action do server render | Browser chỉ play/pause/seek local audio element. Sau event `playing` và checkbox xác nhận, JS enable nút submit; form POST mới ghi session preflight marker theo §4. Audio error disable control và không có request chấm/DB write. |
| `src/main/resources/static/js/practice/speaking-preflight.js` | `templates/practice/speaking-preflight.html`, `data-upload-enabled` | Web Audio phát tone, `getUserMedia` + `MediaRecorder` ghi sample tối đa 5 giây và dùng `blob:` URL để nghe lại **trong tab**. Start chỉ enable khi speaker/sample/confirm đều OK và server flag upload bật; sample bị revoke pagehide, không upload/không trở thành evidence. Form POST speaking-check ở §5 mới start/resume attempt và set session marker. |
| `src/main/resources/templates/practice/preferences.html` + `src/main/resources/static/js/practice-korean-font.js` | GET `/practice/preferences` server-inject font/size/account/schema meta; form radio POST `/practice/preferences/korean-font` | JS chỉ accept allowlist font/size + schema v2, set `html` dataset để preview và best-effort cache `practice-korean-font-preference-v2:{accountId}`. Không đọc cache để ghi đè model server. Persist chỉ xảy ra ở MVC POST `PracticeKoreanFontPreferenceController.update:51–80`; bad input 400, success redirect + flash. |
| `src/main/resources/static/js/practice-progress.js` | `templates/practice/progress.html` inline `OVERVIEW_DATA`/`ANALYTICS_DATA`, server facts/fallback table | Progressive enhancement duy nhất: heatmap DOM và lazy-load Chart.js 4.4.3 từ CDN khi chart gần viewport. Không fetch dữ liệu Practice; chart failure mở fallback và đặt `CHART_ENHANCEMENT_UNAVAILABLE`/`NO_RENDERABLE_DATA`, nên server facts vẫn authority. |
| `src/main/resources/templates/practice/result-detail-writing.html` | `GET /practice/attempts/{id}/result/detail?questionId`; model `resultDetail` từ `PracticeResultDetailAssembler` | Renders stored feedback, learner segments/blank annotations, criteria, diagnostic and upgrade evidence; task links chỉ GET detail khác. Header form `POST /practice/attempts/{id}/re-evaluate?questionId` queues durable re-evaluation (§10), then redirect; template never calls AI itself. |
| `src/main/resources/templates/practice/result-detail-speaking.html` | Same result-detail GET; `screenKind=SPEAKING_DETAIL` selects template | Renders transcript/evidence provenance, explicit recording/acoustic availability, task links and private media state. It deliberately labels transcript-only/no overall task score where applicable; no reviewer/direct-audio score is released by this template. |
| `src/main/resources/static/js/practice-result.js` | Loaded by result shell and all result-detail templates | Presentation/accessibility only: optional one-document celebration; speaking question `<dialog>`; tabs, keyboard custom select, diagnostic chips/occurrence focus, objective split/pinned material persisted in `localStorage` and `location.hash`. No `fetch`/provider/API mutation: result truth is the server-rendered immutable result model. |

## 13. Method-level learner handler trace

| Exact handler | Route/read or write | Service/state/response |
|---|---|---|
| `PracticeController.catalogBatch` | `GET /practice/catalog?q&skill&writingTask&batch` | Calls `PracticeCatalogService.loadBatch` with principal query and renders only catalog-card fragment; read-only, no attempt/catalog mutation. |
| `PracticeController.testDetail` | `GET /practice/sets/{setId}/tests/{testId}` | Verifies visible published set and test membership, reads summary/section cards plus learner attempt state, renders `practice/test-detail`; unauthorized/out-of-graph IDs fail before view. |
| `PracticeController.listeningPreflight` | `GET .../sections/{sectionId}/listening-check` | Checks visible set + Listening section then reads canonical check-audio delivery. Renders preflight; invalid/missing audio redirects test detail with flash, no attempt/session write. |
| `PracticeController.completeListeningPreflight` | `POST` same new-attempt Listening-check route | Starts/reuses immutable `IN_PROGRESS` attempt, verifies attempt-bound delivery, writes only HttpSession listening marker, then redirects player. Bad delivery invokes discard/cleanup fallback and redirects test detail. |
| `PracticeController.speakingPreflight` | `GET .../sections/{sectionId}/speaking-check` | Validates published visible Speaking section, supplies title/action/upload flag to template; no microphone/sample or DB mutation occurs server-side. |
| `PracticeController.completeSpeakingPreflight` | `POST` same new-attempt Speaking-check route | Requires upload feature, starts/reuses attempt, validates Speaking player delivery, writes session marker and redirects player; invalid delivery follows discard/error redirect. |
| `PracticeController.attemptSpeakingPreflight` | `GET /practice/attempts/{attemptId}/speaking-check` | Requires owner `IN_PROGRESS` Speaking attempt and rereads immutable delivery; render check page or invalid-delivery cleanup/redirect. |
| `PracticeController.completeAttemptSpeakingPreflight` | `POST` attempt Speaking-check route | Requires owner/in-progress + upload enabled, revalidates delivery, sets session marker and redirects attempt; it does not upload the browser sample. |
| `PracticeController.attemptListeningPreflight` | `GET /practice/attempts/{attemptId}/listening-check` | Requires owner `IN_PROGRESS` Listening attempt, reads locked check audio and renders; unsafe delivery redirects via cleanup helper. |
| `PracticeController.completeAttemptListeningPreflight` | `POST` attempt Listening-check route | Revalidates locked delivery, writes listening marker to session, redirects player; no answers/scores are changed. |
| `PracticeController.attemptsGetFallback` | `GET /practice/sets/{setId}/tests/{testId}/attempts` | Deliberate non-mutating fallback: redirects test detail. Attempt creation remains POST + CSRF. |
| `PracticeController.interruptAttempt` | `POST /practice/attempts/{attemptId}/interrupt` | Requires owner in-progress Speaking attempt, `PracticeAttemptDiscardService.discardForOwner` transitions it to discarded/queues media cleanup, clears Speaking session marker, returns `204 No Content`. |
| `PracticeController.attemptResultDetail` | `GET /practice/attempts/{attemptId}/result/detail?questionId` | `PracticeResultDetailAssembler` reads owner-scoped stored result/published snapshot and returns objective/Writing/Speaking template by `screenKind`; no provider call or result write. |
| `PracticeController.profileRedirect` | `GET /practice/profile` (STUDENT) | Pure redirect to `/practice/progress`; no profile query/write. |
| `PracticeController.manualFormRedirect` | `GET /practice/manage/manual` (LECTURER) | Compatibility route only: redirect `/practice/manage`, never creates a draft. |
| `PracticeKoreanFontPreferenceController.view` | `GET /practice/preferences` | Reads principal preference snapshot and allowed font/size catalogs into model, renders preferences; no cache/browser value is trusted as persistence. |
