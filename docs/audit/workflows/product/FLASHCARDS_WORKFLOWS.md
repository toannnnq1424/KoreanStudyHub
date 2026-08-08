# Workflow audit: Flashcards CRUD, import, AI, study/review, sharing và public link

## 1. Danh sách, tạo bộ thẻ và orchestration lưu hai pha

`GET /my/flashcards?page=n` được `StudentFlashcardController.list`, dòng 75–82, xử lý cho mọi authenticated user. `DeckService` trả deck owner và deck được share theo quyền truy cập, không expose deck soft-deleted.

User mở form bằng:

```text
GET /my/flashcards/new
```

`StudentFlashcardController.newForm`, dòng 85–93, trả `templates/flashcards/deck-form.html`. Form thật bắt đầu dòng 29–32; `title` ở dòng 37, description, danh sách cards và `cardsJson` hidden.

Khi bấm **“Lưu bộ thẻ”** ở `deck-form.html:25/202`, `static/js/flashcard-deck-form.js:487–536` không submit form ngay. Luồng là:

### Pha A — tạo deck và card rows qua JSON

`collectCards`, dòng 410–433, đọc thứ tự, `id`, `front`, `back`, image URLs và alternatives của từng row. Với deck mới, JS gửi:

```text
POST /api/flashcards/decks
Content-Type: application/json
Body: {"title":"...","description":"...","cards":[...]}
```

`FlashcardApiController.createDeck`, dòng 110–125, gọi `DeckService.create` rồi `CardService.replaceCards`; trả `{deckId,cards}`. `DeckService.create`, dòng 62–77, tạo `FlashcardDeck` ACTIVE, owner từ principal. `CardService.replaceCards`, dòng 62–105, validate toàn bộ front/back trước khi ghi để tránh half-save, insert cards theo position.

Với deck đã tồn tại, JS gửi:

```text
POST /api/flashcards/{deckId}/cards
Body: {"cards":[...]}
```

`FlashcardApiController.saveCards`, dòng 152–170, buộc owner, rồi `CardService.replaceCards`:

- id cũ phải thuộc chính deck;
- update row hiện có và giữ SM-2 review state của card đó;
- insert row mới;
- card bị bỏ khỏi payload bị delete cùng review rows theo cascade/schema;
- ghi lại position theo thứ tự drag/drop UI.

### Pha B — upload ảnh sau khi card đã có id

`uploadPendingImages`, `flashcard-deck-form.js:446–478`, lần lượt gửi mỗi mặt ảnh:

```text
POST /api/flashcards/cards/{cardId}/image
Content-Type: multipart/form-data
side=front|back
file=<image>
```

`FlashcardApiController.uploadImage`, dòng 127–149, kiểm card ownership trước/sau store, gọi `FlashcardImageStorageService.store` rồi `CardService.setImage`.

`FlashcardImageStorageService`, dòng 24–35, giới hạn 5 MB, MIME + magic bytes JPEG/PNG/WebP, ghi key:

```text
flashcards/{cardId}-{side}-{uuid}.{ext}
```

và trả `/uploads/flashcards/...`. Public upload controller chỉ cho đúng folder/filename, không cho traversal/nested path.

### Pha C — native metadata POST

Sau JSON + mọi image upload thành công, JS dòng 480–529 gọi `form.requestSubmit()`. Form action đã đổi thành:

```text
POST /my/flashcards/{deckId}
```

`StudentFlashcardController.update`, dòng 141–158, bind `DeckForm`, gọi `DeckService.updateMetadata`, flash và redirect detail. Với create, endpoint HTML `POST /my/flashcards` tại controller dòng 96–110 vẫn là non-JS fallback; JS path chủ động tạo qua API trước rồi chuyển action sang update.

Hệ quả vận hành: nếu pha A thành công nhưng upload ảnh hoặc pha C lỗi, deck/cards có thể đã tồn tại; UI báo lỗi và cho retry. Đây không phải một transaction xuyên HTTP.

## 2. Xem, sửa và soft-delete deck

| Thao tác UI | HTTP | Controller/service |
|---|---|---|
| Mở deck | `GET /my/flashcards/{id}` | `StudentFlashcardController.detail:113–123` → `DeckService.getDetail` |
| Sửa | `GET /my/flashcards/{id}/edit` | controller 126–138 → owner-only edit DTO |
| Lưu metadata | `POST /my/flashcards/{id}` | controller 141–158 → `DeckService.updateMetadata` |
| Xóa | `POST /my/flashcards/{id}/delete` | controller 161–168 → `DeckService.softDelete` |

Nút xóa nằm `deck-detail.html:30–31` và có browser confirm. `DeckService.softDelete`, dòng 87–93, không hard-delete blob/rows ngay; đánh dấu deck deleted/inactive để biến mất khỏi catalog/access. User không phải owner không thấy edit/delete controls và service vẫn chặn request giả.

## 3. Excel template và import vào editor

User tải mẫu ở `deck-form.html:57`:

```text
GET /api/flashcards/import-template
```

`FlashcardApiController.importTemplate`, dòng 264–275, trả XLSX attachment.

User bấm import ở dòng 58, chọn `.xlsx` dòng 62. `flashcard-deck-form.js:344–406` gửi một trong hai request:

```text
# Form tạo mới, chưa có deck id
POST /api/flashcards/import-preview

# Form edit deck đã có id
POST /api/flashcards/{deckId}/import

Content-Type: multipart/form-data
file=<xlsx>
```

Controllers tại `FlashcardApiController:197–229` gọi `FlashcardImportParser`. Parser dòng 40–127 giới hạn 2 MB/500 rows, đọc sheet đầu, coi hai cột đầu là front/back và bỏ header row. Row có một mặt trống vẫn được trả về editor để user sửa, không tự persist.

Ngay cả endpoint có `{deckId}` cũng chỉ kiểm owner rồi parse/return card DTO; `flashcard-deck-form.js:360–400` append kết quả vào rows và toast **“Kiểm tra rồi bấm Lưu”**. Persistence chỉ xảy ra khi user bấm save, qua workflow mục 1.

## 4. Sinh flashcard bằng AI từ PDF/DOCX/text

### UI và request

Nút AI ở `deck-form.html:63` chỉ hiện khi editor có deck id. Panel có file ở dòng 97, count dòng 104, language dòng 113 và nút **“Tạo bản nháp”** dòng 122.

`static/js/flashcard-ai-generate.js:116–171` buộc có file hoặc text, giữ file khi retry, rồi gửi:

```text
POST /api/flashcards/{deckId}/ai-generate
Content-Type: multipart/form-data
file=<pdf|docx>          # optional nếu có text
text=<pasted material>  # optional nếu có file
count=1..50
language=<auto|...>
```

`FlashcardApiController.generateCards`, dòng 235–261, lấy principal, gọi `AiFlashcardGenerationService.generate` và trả cards preview.

### Service → AI → parser

`AiFlashcardGenerationService`, dòng 34–68:

1. `DeckAccessResolver` buộc actor là owner deck; deck shared/public viewer không được dùng AI mutate editor.
2. Shared `DocumentTextExtractor` đọc PDF/DOCX/text với giới hạn an toàn.
3. `KoreanFlashcardMaterialSelector`, dòng 37–63, ưu tiên phần nội dung học/đọc tiếng Hàn và loại header/page-number nhiễu.
4. `AiFlashcardPromptBuilder` nạp prompt DB `AI_FLASHCARD_GENERATOR`, fallback nếu thiếu, clamp count 1–50 và budget `120 × count` tokens (`:12,66–70,111–123`).
5. Gọi `AiProviderClient.chatJsonObject` ở service dòng 42.

AI được yêu cầu chỉ trả object:

```json
{"cards":[{"front":"한국어","back":"nghĩa ngắn gọn"}]}
```

Runtime contract `AiFlashcardPromptBuilder:18–57` yêu cầu front là từ/cụm tiếng Hàn ngắn nhất có ý nghĩa từ material, back là nghĩa theo language, không prose/Markdown. Nếu parser không nhận được cards, service retry một lần bằng `chat` với repair prompt (`AiFlashcardGenerationService:53–63`).

`AiFlashcardResponseParser` permissive hơn prompt: tìm `cards|flashcards`, chấp nhận một số alias keys/JSON được bọc, bỏ row rỗng, quá 500 ký tự hoặc trùng; sau đó service cắt tối đa requested count. Vì vậy số cards trả có thể ít hơn count.

### Kết quả UI và persistence

JS dòng 153–160 append response vào cuối editor và ghi rõ **“thẻ chưa lưu”**. Không có AI session/confirm backend riêng như AI Tests. User review/chỉnh sửa rồi bấm **Lưu bộ thẻ**; lúc đó `CardService.replaceCards` mới persist. Đóng trang trước khi save làm mất AI draft.

## 5. Study modes phía client

Detail render cards và các link mode tại `deck-detail.html:41–70`:

| Mode | Route |
|---|---|
| Flip | `GET /my/flashcards/{id}/flip` |
| Learn | `GET /my/flashcards/{id}/learn` |
| Test | `GET /my/flashcards/{id}/test` |
| Match | `GET /my/flashcards/{id}/match` |
| Tiles | `GET /my/flashcards/{id}/tiles` |
| Word search | `GET /my/flashcards/{id}/word-search` |
| Word connect | `GET /my/flashcards/{id}/word-connect` |
| Blast | `GET /my/flashcards/{id}/blast` |

`FlashcardStudyController.flip`, dòng 58–68, và `learning`, dòng 88–100, gọi `FlashcardStudyService`/access resolver rồi render deck/cards JSON. Legacy `/blocks` redirect ở dòng 103–104.

Các mode learn/test/match/blast/tiles/word-search/word-connect chạy trong `static/js/flashcard-learning.js`; nút submit/hint/retry/game chỉ thay state trong browser. **Không có HTTP submit-attempt, entity attempt hay grade persistence** cho các mode này. Flip/detail tracking “known/unknown” cũng là interaction client nếu không đi qua Smart Review endpoint.

Controller mixed-study (các nhánh tiếp theo ở `FlashcardStudyController:108–138`) giới hạn tối đa 8 deck ids, kiểm từng deck viewable rồi hợp cards; browser không thể mix deck ngoài quyền bằng sửa query.

## 6. Smart Review và SM-2 persistence

User mở:

```text
GET /my/flashcards/{id}/review
```

`FlashcardStudyController.review`, dòng 71–80, gọi `SmartReviewService.getDueCards`. Service dòng 45–60 chỉ lấy card chưa có review hoặc `nextReviewAt <= now` sau khi kiểm deck viewable.

Ở `flashcard-review.html:47–50`, user bấm quality 1/3/4/5. `flashcard-review.js:66–79` gửi:

```text
POST /api/flashcards/cards/{cardId}/review
Content-Type: application/json
Body: {"quality":1|3|4|5}
```

`FlashcardApiController.review`, dòng 173–190, gọi `SmartReviewService.recordRating`. Service dòng 70–105:

1. Kiểm quality 0–5 và card/deck viewable.
2. Lock stable card row để serialize review của cùng card.
3. Load/upsert `FlashcardReview` theo `(userId, cardId)`.
4. `Sm2Scheduler.schedule`, dòng 46–71:
   - quality `<3`: repetitions=0, interval=1 ngày;
   - lần đúng đầu: 1 ngày; lần hai: 6 ngày;
   - sau đó `round(previousInterval × easinessFactor)`;
   - easiness factor không thấp hơn 1.3.
5. Lưu quality/EF/repetitions/interval/nextReviewAt; trả remaining due và interval mới để JS chuyển card.

Review state là per-user, kể cả khi deck được share; không ghi vào deck owner.

## 7. Chia sẻ deck vào lớp

Owner chọn lớp và bấm **“Chia sẻ”** tại `deck-detail.html:252–256`:

```text
POST /my/flashcards/{id}/share
classId=<id>
```

`StudentFlashcardController.share`, dòng 171–179, gọi `DeckService.share`. Service dòng 177–194:

- buộc actor owner deck;
- class phải ACTIVE;
- student owner phải ACTIVE-enrolled; lecturer/staff owner phải quản lý class theo rule helper dòng 212–226;
- set shared class reference để các user đủ class access thấy deck.

Nút **“Ngừng chia sẻ”** ở dòng 259–260 gửi `POST /my/flashcards/{id}/unshare`; controller dòng 182–189 gọi service clear share. Existing per-user review rows không bị chuyển owner.

## 8. Link công khai

Owner controls tại `deck-detail.html:267–286`:

| Button | Request | Service effect |
|---|---|---|
| Bật link | `POST /my/flashcards/{id}/public-link/enable` | tạo token nếu thiếu, set enabled |
| Tắt link | `POST /my/flashcards/{id}/public-link/disable` | disable nhưng giữ token |
| Tạo link mới | `POST /my/flashcards/{id}/public-link/regenerate` | tạo token mới, token cũ mất hiệu lực |

Controllers nằm `StudentFlashcardController:192–219`; `DeckPublicLinkService` logic tại dòng 27–61. User anonymous mở:

```text
GET /s/{token}
```

`PublicDeckController.view`, dòng 36–52, validate token format, enabled flag và deck chưa deleted, rồi render read-only public deck. Public token không cấp quyền edit/API review/share.

## Entity/repository và security summary

| Entity | State/lifecycle |
|---|---|
| `FlashcardDeck` | ACTIVE → soft-deleted; optional shared class/public token |
| `Flashcard` | ordered child; replaceCards update/insert/delete theo payload |
| `FlashcardReview` | per user/card SM-2 state |

- Tất cả write controllers lấy user id từ principal và API có CSRF.
- View access được gom tại `DeckAccessResolver`: owner, eligible class share hoặc public route riêng.
- AI/import response chỉ vào editor; save mới persist.
- Game modes không persist kết quả; chỉ Smart Review POST persist learning schedule.
- Card image URL là public allowlisted object, nhưng upload/set-image luôn owner-gated.


## 9. Audit bổ sung: initial data, state thực tế và đầy đủ game

Các kết luận phần này được đối chiếu trực tiếp controller → service/repository → template → JavaScript. Số dòng là snapshot lúc audit.

### Dữ liệu khi mở từng màn hình

| Màn hình / route | Nguồn dữ liệu trước render | Fetch/ghi sau load |
|---|---|---|
| Library GET /my/flashcards?page=n | StudentFlashcardController.list:75-82 → DeckService.listForStudent:123-131: một page deck owner và toàn bộ deck share cho lớp ACTIVE-enrolled. SQL restriction loại soft-delete (FlashcardDeck:28-31). | Không fetch list; list.html:24-84 là SSR + pager own decks. |
| New GET /new | newForm:85-93 cấp DeckForm rỗng và đúng hai card row rỗng; chưa có record DB. | flashcard-deck-form.js:23-550 chỉ dựng DOM tới lúc Save/import/AI. |
| Detail GET /{id} | detail:113-123 gọi getDetail, countDue và getStudyCards; term list/quick viewer đã có toàn cards SSR. | deck-detail.js:49-330 chỉ DOM/timer/sessionStorage; không POST rating. |
| Edit GET /{id}/edit | editForm:126-138 → CardService.getEditorView:44-53; owner-only, cards theo sort_order. | Editor JS; AI panel chỉ có khi deckId tồn tại. |
| Flip GET /{id}/flip | FlashcardStudyController.flip:58-68 serialize cards vào data-cards. | flashcard-flip.js:22-350, không network. |
| Review GET /{id}/review | SmartReviewService.getDueCards:45-55 lấy card mới hoặc nextReviewAt <= now. | Đây là màn hình duy nhất POST kết quả học (flashcard-review.js:66-81). |
| Learn/test/game | learning:88-100 chỉ nhận 7 mode và truyền card JSON. | flashcard-learning.js không chứa fetch, FcCommon.post*, localStorage hay sessionStorage. |
| Public GET /s/{token} | PublicDeckController.view:36-52 resolve token enabled/non-deleted, query cards sort_order. | public-deck.html:8-38 SSR text/image, không JS/đăng nhập. |

populateStudySession (FlashcardStudyController:108-138) thêm deck chính và tối đa 7 query mix; từng deck qua FlashcardStudyService.getStudyCards:31-40 nên sửa query không đọc được deck không có quyền. Header picker chỉ GET form (flashcard-study-header.html:68-91). Topbar vẫn hiển thị deck.cardCount() của deck chính (flashcard-learning.html:18-20), không phải tổng cards đã trộn.

### State/session và randomization

- flashcard-learning.js:9-90,106-146 parse cards, bỏ card thiếu front/back, xáo Fisher-Yates bằng Math.random, rồi giữ score và state mỗi mode trong RAM. Refresh/navigate tạo session mới. Copy Tự luyện · không lưu lượt chơi (flashcard-learning.html:27-33) là đúng.
- Detail quick viewer ghi IDs unknown vào sessionStorage key ksh:flashcards:{deckId}:unknown khi completion có tracking (flashcard-deck-detail.js:193-220), và clear lúc mount/pageshow (:98-99,312-325). Flip ghi cùng key (flashcard-flip.js:152-157); learn?focus=unknown đọc/lọc key đó (flashcard-learning.js:21-29). Đây là theo-tab, không SM-2 hay durable.
- Quick viewer và Flip known/unknown chỉ local; không gọi review endpoint và không đổi due count/EF/review row.


### Anchor coverage: flip template/logic và common HTTP helper

**Flip template + client state.** Template flashcard-flip.html:10-147 không tự query hay có form submit. GET controller đã đưa cardsJson và id **deck chính** vào #fcStudy qua data-cards/data-deck-id (:13); header fragment vẫn có thể mang query mix, nên cardsJson có thể chứa card của nhiều deck. JS parse JSON một lần, giữ order, pos, ratings, classifyMode, focusMode và drag velocity trong RAM (flashcard-flip.js:22-99). Nội dung card được gán bằng textContent, image URL chỉ vào img.src (:216-237); HTML user content không được insert làm markup.

- Không có card thì JS chỉ bỏ hidden cho #fcEmpty (:26-35). Có card thì viewer mặc định hidden được mở, render mặt front/back/image và progress (:216-251).
- Nút next/prev chỉ đổi pos trong order; shuffle xáo order, reset ratings và xóa unknown key (:267-291,337-344). Focus chỉ che text front khi card có frontImage; reveal chỉ là browser state (:193-200,253-265).
- Rate button hoặc swipe ngang khi bật classify ghi ratings[card.id]=known|unknown. Khi tất cả card đã rate, complete render counts và tiếp tục link Learn (:294-335; template :130-141). Mỗi lần rate/complete, chỉ IDs unknown được ghi sessionStorage key ksh:flashcards:{primaryDeckId}:unknown (:152-159,304-310). Không có POST, không có review quality/SM-2/time/score persistence. Với session mixed, key vẫn là deck chính nhưng ID list có thể bao gồm card deck trộn; Learn cùng primary deck sẽ dùng chính key này để lọc cards session.
- Gesture dọc chỉ lật; gesture ngang khi không classify chỉ chuyển thẻ. Chỉ ngang trong classify mới map right→known, left→unknown (flashcard-flip.js:435-475). Vì vậy nhãn UI “Vuốt để chuyển” là navigation mặc định, không phải auto-rating.

**flashcard-common.js là transport helper, không phải persistence riêng.** File chỉ export window.FcCommon gồm toast, csrfHeader, postJson và postForm (flashcard-common.js:11-63). csrfHeader đọc hai meta CSRF render sẵn; postJson gửi POST JSON, postForm gửi multipart và cả hai resolve **chỉ** HTTP ok + AjaxResult.ok=true, còn lại reject Error từ data.message/fallback (:20-59). Nó không lưu request/response, retry, queue, cache, telemetry hay session state. Các caller quyết định persistence: editor dùng nó cho create/replace cards và image upload (flashcard-deck-form.js:446-535), import/AI dùng multipart preview (flashcard-deck-form.js:344-406; flashcard-ai-generate.js:149-160), còn Smart Review dùng postJson để ghi SM-2 (flashcard-review.js:66-81). Flip có tải common script (flashcard-flip.html:145-147) nhưng không gọi FcCommon, nên mở/chơi Flip không phát sinh HTTP.


### Từng mode/game và persistence

| Mode | Thuật toán/luật thực tế | Score/time/state | Ghi DB |
|---|---|---|---|
| Flip | Order/rating local, shuffle và swipe/classify/focus (flashcard-flip.js:74-157). | seen/known/unknown; unknown có thể vào sessionStorage. | Không. |
| Học | Shuffle; choice/write random 50% (ép mỗi loại nếu >1), tối đa 3 distractor (flashcard-learning.js:57-66,106-166). | +10 đúng, correct/review/hint RAM (:241-326). | Không. |
| Kiểm tra | Shuffle; prompt=back, choice=front; submit tự chấm (:382-500). | +15/câu, %; template nói Không giới hạn thời gian (:117-155). | Không attempt/entity/endpoint. |
| Ghép cặp | Queue shuffle, batch 1..N/default 4, tile xáo; media random một mặt (:599-667). | +12/cặp, round/progress. | Không. |
| Xếp âm tiết | Chỉ một mặt match ^[가-힣]{2,8}$, chọn mặt nhiều Hangul hơn (:47-55). | Bank/distractor, combo/countdown 12s. | Không. |
| Săn chữ Hàn | Cùng eligibility Hangul; grid, word và filler đều JS (:930-1066). | found/selection/restart. | Không. |
| Nối âm tiết | Cùng eligibility; tokens/distractor shuffle, pointer trace/SVG (:1298-1612). | completed/combo/level. | Không. |
| Bắn từ | Shuffle; choices, anchors, entities random; 60,000ms tính bằng performance.now + RAF (:1750-1914). | +20..40/hit, combo/level/hits. | Không. |

/blocks không còn game riêng: FlashcardStudyController.legacyBlocks:102-106 redirect thẳng /tiles. Quyền được kiểm ở request tiles kế tiếp, không ở redirect source.

## 10. Gaps/sai lệch xác minh từ source

1. Import preview không owner-only khi tạo deck mới. Comment FlashcardApiController:192-195 nói owner-only, nhưng previewExcel:197-207 không nhận deck/user và chỉ bị class-level isAuthenticated(). Chỉ /{deckId}/import:210-228 requireOwner. Dữ liệu vẫn không persist trước Save.
2. Game/Test không thể dùng làm completion/progress analytics. Dù UI có Gửi bài kiểm tra, score, timer, level, flashcard-learning.js không gửi HTTP; controller cũng nêu client-only tại FlashcardStudyController:83-86. Smart Review POST là con đường duy nhất ghi kết quả.
3. Public link không là public study room. Người có token nhận toàn bộ front/back/image SSR nhưng không Flip/game/review/API token; PublicDeckController:44-51 chỉ tạo PublicDeckView identity-free + CardView list.
4. Ảnh không có cleanup lifecycle trong feature. FlashcardImageStorageService:24-35 chỉ put/store; source không delete khi replaceCards xóa card, deck soft-delete, thay ảnh, hay upload xong rồi metadata POST thất bại. Đây là orphan-object risk.
5. Save editor multi-request, không atomic xuyên HTTP: deck/cards commit trước, rồi upload từng ảnh, rồi native metadata POST (flashcard-deck-form.js:446-535). Error về sau có thể để deck/cards/blob; UI chỉ toast retry. Bên trong replaceCards, validation trước mutate và transaction (CardService:62-105).
6. Mixed session tối đa 8 deck, nhưng count headline chỉ deck chính. Đây là lệch hiển thị, không phải access/persistence bug.

7. Documentation cũ ở mục 7 nói class “phải ACTIVE”; source không kiểm ClassEntity.status trong DeckService.share:177-218. Điều kiện thực là owner ACTIVE-enrolled *hoặc* ownerId là lecturerId của class; class có tồn tại là đủ cho nhánh lecturer. Không nên xem status class là enforcement flashcard hiện hữu.
8. Có nhánh Match deck-picker chưa được nối UI. JS có renderMatchDeckPicker và đọc data-match-decks/data-match-selected (flashcard-learning.js:669-720), nhưng không có #fcMatchDeckOptions hay #fcMatchDeckPicker trong template (rg chỉ thấy JS), nên hàm return ngay. Controller cũng chỉ model.add studyDeckOptions/studySelectedDeckIds (FlashcardStudyController:131-137), không có matchDeckOptionsJson/matchSelectedDeckIdsJson mà template tham chiếu ở flashcard-learning.html:23-26. Trộn deck đang hoạt động qua header GET picker, không qua nhánh Match này.


## 11. Method-level handler trace (coverage gate)

- **FlashcardApiController.previewExcel** — POST /api/flashcards/import-preview multipart file, authenticated but has no deck/owner parameter. It parses only (2 MB, 500 rows, sheet 0 A/B), returns AjaxResult.success({cards,count}), maps validation to 400 and does not write deck/card/review state (FlashcardApiController:197-207; FlashcardImportParser:60-112). The new-editor JS appends this response; subsequent editor Save is the write boundary.
- **FlashcardApiController.importExcel** — POST /api/flashcards/{deckId}/import multipart file. It calls DeckAccessResolver.requireOwner *before* parsing, then returns the same draft envelope; no import persistence (FlashcardApiController:210-228). It is the existing-deck editor's source of preview rows.
- **FlashcardStudyController.learning** — GET /my/flashcards/{id}/{mode:learn|test|match|blast|tiles|word-search|word-connect}, optional repeated mix query. It checks primary detail, builds a read-only session from primary + at most seven additional viewable decks, serializes CardView JSON and activeMode into the common learning template (FlashcardStudyController:88-100,108-148). It performs no attempt/session/score write; every game state lives in flashcard-learning.js.
- **StudentFlashcardController.create** — POST /my/flashcards non-JS fallback consumes validated DeckForm plus hidden cardsJson; malformed JSON adds BindingResult error and re-renders new form, otherwise createDeckWithCards transaction then redirects detail (StudentFlashcardController:95-110,226-249). It is distinct from the normal JS API-first sequence.
- **StudentFlashcardController.editForm** — GET /my/flashcards/{id}/edit calls owner-gated CardService.getEditorView, supplies title/description/cards/mode=edit to deck-form; no mutation (StudentFlashcardController:126-138).
- **StudentFlashcardController.delete** — POST /my/flashcards/{id}/delete calls owner-gated DeckService.softDelete, flashes success and redirects library (StudentFlashcardController:160-168; DeckService:87-93). It sets is_deleted; it does not hard-delete child DB rows or image objects.
- **StudentFlashcardController.unshare** — POST /my/flashcards/{id}/unshare owner-gated service changes visibility to PRIVATE and clears class_id, flashes and redirects detail; it does not remove per-user review rows (StudentFlashcardController:181-189; DeckService:188-194; FlashcardDeck:120-124).
- **StudentFlashcardController.enablePublicLink** — POST /my/flashcards/{id}/public-link/enable, owner-only; enables anonymous access, retains a non-null token or generates one, flashes and redirects detail#share (StudentFlashcardController:191-199; DeckPublicLinkService:26-32). It mutates only deck link fields, never cards/reviews.
- **StudentFlashcardController.disablePublicLink** — POST /my/flashcards/{id}/public-link/disable, owner-only; sets is_public false but retains the distributed token for later re-enable, then flashes and redirects detail#share (StudentFlashcardController:201-209; DeckPublicLinkService:34-40). No card/review mutation occurs.
- **StudentFlashcardController.regeneratePublicLink** — POST /my/flashcards/{id}/public-link/regenerate, owner-only; replaces share_token, enables the fresh link immediately, flashes and redirects detail#share (StudentFlashcardController:211-219; DeckPublicLinkService:42-49). Prior URL stops resolving; no card/review mutation occurs.
