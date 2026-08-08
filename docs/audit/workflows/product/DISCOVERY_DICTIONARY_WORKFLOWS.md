# Workflow audit: Discovery, news ingestion/AI editorial và Korean Dictionary → Flashcard

## 1. Người dùng duyệt/search Discovery

Search form tại `templates/discovery/index.html:78–88` gửi browser GET:

```text
GET /discover?category=<slug>&q=<tối đa 80 ký tự>&lang=ko|vi&page=<1-based>
```

`DiscoveryController.index`, dòng 26–37, gọi `DiscoveryService.page`. Service dòng 63–180:

1. Normalize query/category/language/page; query dài hơn 80 bị giới hạn.
2. Query repository chỉ các article đủ điều kiện hiển thị; lấy pool tối đa 240 rồi page-size 18.
3. Ưu tiên field tiếng Hàn hoặc bản biên tập tiếng Việt theo `lang`.
4. Tạo các khu hero/digest/featured/latest/scholarship và vocabulary highlights từ cùng snapshot.
5. Trả SSR `discovery/index.html`; pagination links tại dòng 378–395 giữ nguyên filters.

Click card/hero gửi:

```text
GET /discover/{slug}?lang=ko|vi
```

`DiscoveryController.detail`, dòng 39–53, gọi `DiscoveryService.detail` (dòng 183–245). Service chỉ tìm `NewsArticleStatus.PUBLISHED`, load attachments/vocabulary/related stories; slug unpublished/deleted không được lộ. Route không có `@PreAuthorize`, nên Discovery đọc là public theo controller/security configuration.

Các nút copy URL/source article tại `detail.html:70–77` chỉ chạy client/navigate; không tạo backend state.

## 2. Chọn từ Hàn trên bài Discovery và mở dictionary drawer

Detail hiển thị action **tra/lưu từ** ở `detail.html:87–100`, quick buttons từ vocabulary có sẵn ở dòng 279–287, drawer form ở dòng 335–383.

`static/js/discovery.js:384–429` bắt selection có Hangul hoặc prefill button. Khi mở drawer, client hiện tại dùng **global dictionary API**:

```text
GET /api/korean-dictionary/decks
GET /api/korean-dictionary/lookup?word=<selected Hangul>
```

Các fetch nằm `discovery.js:267` và `345–350`. `KoreanDictionaryController`, dòng 31–43, gọi `KoreanDictionaryLearningService`.

### Lookup service → KRDICT external request

`KoreanDictionaryLearningService.lookup`, dòng 37–45:

1. Trim/normalize word, yêu cầu có Hangul và độ dài hợp lệ.
2. Đọc runtime dictionary settings; không configured thì trả `{configured:false, found:false}` thay vì gọi network.
3. Khi configured, gọi `KoreanDictionaryClient.lookup`.

`KoreanDictionaryClient`, dòng 55–143, gửi request GET tới URL KRDICT đã allowlist với query:

```text
GET https://krdict.korean.go.kr/api/search
    ?key=<secret>
    &q=<word>
    &part=word
    &method=exact
    &translated=y
    &trans_lang=7
    &num=10
```

Client dùng hardened XML parser, ưu tiên item exact rồi fallback hợp lý, lấy tối đa 2 nghĩa dịch; trả word, pronunciation, partOfSpeech, meaningVi và safe dictionary URL. URL từ response chỉ được giữ khi thuộc `krdict.korean.go.kr` (`DiscoveryVocabularyLearningService:197–203`). Network/XML failure không làm UI crash; service trả no-result/error envelope và user có thể nhập nghĩa thủ công.

## 3. Lưu từ vào bộ Flashcard của chính user

Drawer load deck options qua `KoreanDictionaryLearningService.decks`, dòng 47–52: chỉ deck owner, không liệt kê deck chỉ được share xem.

User chọn deck, sửa nghĩa nếu cần rồi submit form. `discovery.js:446–474` gửi:

```text
POST /api/korean-dictionary/flashcards
Content-Type: application/json
Body: {
  "deckId": 12,
  "word": "학교",
  "meaningVi": "trường học",
  "pronunciation": "...",
  "partOfSpeech": "...",
  "dictionaryUrl": "https://krdict.korean.go.kr/..."
}
```

`KoreanDictionaryController.save`, dòng 45–55, lấy user id từ principal và gọi `KoreanDictionaryLearningService.save`, dòng 54–70:

1. Load deck và buộc `ownerId == principal.id`.
2. Normalize word/meaning/metadata; không tin deck/user từ client.
3. Tìm card có front text trùng trong deck; nếu có trả `alreadySaved=true`, không tạo duplicate.
4. Nếu chưa có, insert `Flashcard` ở cuối deck với front=word, back=meaning; trả deck title/result cho toast.

CSRF header được JS lấy từ meta. Mọi `/api/korean-dictionary/**` yêu cầu authenticated (`KoreanDictionaryController:23`).

## 4. Global Korean Dictionary trên các màn hình khác

`static/js/korean-dictionary.js` gắn widget dùng chung lên các trang cho phép. Dòng 15 bắt mouse selection, buộc có Hangul, tối đa 120 ký tự và bỏ selection trong input/textarea/select. Dòng 13–17 dùng đúng ba endpoint global ở mục 2–3.

Nó không chạy trên các màn Practice/Discovery đã có handler riêng theo guard đầu file, nhằm tránh hai popup cùng xử lý selection.

## 5. Article-scoped Discovery vocabulary API tồn tại nhưng UI detail hiện không gọi

Source còn một API có article context:

```text
GET  /api/discovery/articles/{articleId}/dictionary?word=<word>
POST /api/discovery/articles/{articleId}/flashcards
```

`DiscoveryVocabularyController`, dòng 41–81, yêu cầu authenticated. `DiscoveryVocabularyLearningService.lookup`, dòng 56–82:

- buộc article tồn tại và `PUBLISHED`;
- ưu tiên `NewsVocabulary` đã cache cho article;
- nếu chưa có thì gọi KRDICT và trả lookup.

`save`, dòng 84–129, kiểm article published và deck owner, **lookup lại authoritative dictionary value** thay vì tin toàn bộ nghĩa/URL browser gửi, dedupe card rồi insert.

Tuy nhiên audit `templates/discovery/detail.html` + `static/js/discovery.js` xác nhận production detail JS hiện gọi `/api/korean-dictionary/*`, không gọi hai URL article-scoped. Vì vậy đây là alternate/orphan API contract hiện có, không nên mô tả là click path đang chạy.

## 6. Admin bấm “Cào tin ngay”

### Màn Admin News lấy query nào khi mở

Màn `templates/admin/news.html` không load bảng bằng AJAX. Browser gửi:

```text
GET /admin/news?page=<1-based>&runId=<optional>&aiRunId=<optional>&ai=generated|pending|failed
    -> AdminNewsController.index()
    -> AdminNewsService.overview(...)
    -> render toàn bộ Overview vào HTML
```

`AdminNewsController.java:61–90` đặt `overview` và trạng thái KRDICT vào model. `AdminNewsService.overview`, `AdminNewsService.java:48–68`, chạy các read sau:

- `NewsSourceRepository.findAll()` cho danh sách nguồn;
- `NewsIngestionRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0,10))` cho 10 lần crawl gần nhất;
- các `countByStatus`, `countBySourceBodyHtmlIsNotNull`, `countByImageUrlIsNotNull` và blacklist count cho KPI;
- `NewsArticleRepository.findAdminArticles(runId,aiRunId,safeAiStatus,PageRequest.of(page-1,20))` cho bảng article đang lọc.

`ai` ngoài ba giá trị whitelist bị đổi thành `null`; `page < 1` bị ép thành 1. KRDICT mask/configured là read riêng qua `DiscoveryDictionarySettingsService`; secret thật không được đưa vào template. Các checkbox article chỉ tồn tại trong DOM cho request bulk kế tiếp; rời/reload trang làm mất selection và không tạo DB state tạm.

Admin controls trên public Discovery chỉ render cho Admin. Form ở `discovery/index.html:48–61` gửi:

```text
POST /admin/news/refresh
returnTo=discover|admin
```

`AdminNewsController.refresh`, dòng 116–137, bắt buộc ADMIN (`:26`), gọi `NewsIngestionOrchestrator.run(MANUAL)`, tạo flash summary và redirect. Message dòng 123–132 nói rõ lần crawl **chưa gọi AI**.

`NewsIngestionOrchestrator.run`, dòng 57–89:

1. Acquire DB lease 45 phút; nếu node khác đang chạy thì tạo/return skipped summary, tránh crawl song song.
2. Tạo `NewsIngestionRun` status `RUNNING`.
3. Load enabled `NewsSource`; adapter RSS/KoreaNet/StudyInKorea fetch candidates qua hardened HTTP client.
4. Nếu candidate thiếu body, `NewsSourceContentCrawler` fetch source detail; sanitizer loại unsafe markup.
5. Mỗi candidate qua blacklist, canonical URL/hash dedupe, ranking/policy rồi `NewsArticleWriter.persist`.
6. `NewsArticleWriter`, dòng 43–87, update duplicate hoặc tạo article mới `PUBLISHED`/`REJECTED`, lưu attachments metadata an toàn.
7. `NewsVocabularyEnrichmentService` enrich từ cho recent candidates nếu dictionary configured.
8. Run thành `SUCCEEDED`, `PARTIAL` hoặc `FAILED`; release lease.

`NewsVocabularyEnrichmentService`, dòng 43–94, xử lý batch 1–30 article, selector chọn tối đa 3 từ/category deterministic, gọi KRDICT và lưu `NewsVocabulary` đã verify. Dictionary chưa configured thì bước này skip; crawl/article vẫn hoạt động.

## 7. Scheduled ingestion không cần button

`NewsIngestionScheduler`, dòng 26–45, chỉ được tạo/chạy khi feature enabled; initial delay 2 phút và fixed delay 5 giờ. Nó gọi cùng orchestrator với trigger scheduled, vì vậy có cùng DB lease/dedupe/policy như manual.

Scheduler không tự gọi AI editorial. “Crawl tin” và “AI biên tập” là hai workflows độc lập trong code.

## 8. Admin chọn bài và chạy AI editorial

Trong `/admin/news`, admin tick article ids rồi submit:

```text
POST /admin/news/articles/ai-editorial
articleIds=1&articleIds=2&...
page=<n>
```

`AdminNewsController.aiEditorial`, dòng 191–205, gọi `NewsAiEditorialService.enrichSelected`, flash generated/candidates/failed và redirect lại section articles.

`NewsAiEditorialService`, dòng 109–215:

1. Deduplicate ids, chọn tối đa 20 candidates.
2. Load prompt runtime key `DISCOVERY_NEWS_EDITOR`; build user message từ source title/excerpt/body, body input tối đa 14.000 ký tự.
3. Gọi `AiProviderClient.chatJsonObject`, maxTokens 2400.
4. AI phải trả đúng object:

```json
{
  "titleVi": "Tiêu đề tiếng Việt",
  "excerptVi": "Tóm tắt ngắn",
  "bodyVi": "Nội dung biên tập tiếng Việt"
}
```

Runtime prompt contract nằm `NewsAiEditorialService:32–82`. Parser giới hạn title 180, excerpt 480, body 4.000 ký tự (`:181–190`). Reply malformed thì retry đúng một lần; provider failure làm dừng các article tiếp theo trong batch để không spam upstream (`:109–170`).

5. Thành công ghi AI fields/status/token usage vào article/run; lỗi ghi error state (`:193–215`).

UI `lang=vi` sau đó chọn field AI tiếng Việt; bản source gốc vẫn giữ để `lang=ko`/source attribution. AI không được gọi trong request public GET.

## 9. Admin delete, blacklist và reset sample

| UI action | HTTP | Backend effect |
|---|---|---|
| Bulk delete | `POST /admin/news/articles/delete` | `AdminNewsController:158–174` → `AdminNewsService.deleteArticles`; optional `blacklistBeforeDelete` |
| Bulk blacklist | `POST /admin/news/articles/blacklist` | controller 176–189 → canonical URL blacklist; article bị ngăn nhập lại |
| Reset sample | `POST /admin/news/reset-sample` | controller 139–156 → xóa tối đa một recent article mỗi source |

Reset button tại `discovery/index.html:62–72` chỉ render khi `app.news.raw-preview-enabled=true`; controller vẫn trả 404 nếu flag false. Delete/blacklist dedupe ids và report affected/skipped; request giả của non-Admin bị method security chặn.

## 10. Khi cấu hình KRDICT thì điều gì được “mở khóa”

Không đi sâu vào admin settings form ở tài liệu này, nhưng runtime read path phải được nêu rõ:

- `KoreanDictionarySettingsService`/`DiscoveryDictionarySettingsService` resolve API key theo stored DB setting, legacy setting rồi environment fallback (`KoreanDictionarySettingsService:26–41`).
- Stored key phải đúng 32 hex, endpoint bị khóa về HTTPS host `krdict.korean.go.kr`; secret không trả ra browser (`:49–65`).
- Có key hợp lệ mở external lookup cho global widget, article-scoped API và vocabulary enrichment sau crawl.
- Không có key: Discovery feed/crawl vẫn chạy; lookup trả `configured=false`; user vẫn có thể nhập nghĩa thủ công; enrichment skip.

## Security và external-I/O summary

- Public chỉ đọc article `PUBLISHED`; admin write routes role ADMIN.
- Dictionary save routes authenticated, deck luôn owner-scoped.
- KRDICT secret chỉ ở backend request; response URL được host-allowlist.
- Crawl dùng canonicalization, blacklist, dedupe, sanitizer và DB lease.
- Crawl không gọi AI; AI editorial chỉ chạy khi Admin chọn và POST.
- External failure được cô lập thành partial/no-result; public GET không chờ AI.


## 11. Method-level handler trace (coverage gate)

### Global Korean dictionary → owner flashcard

- **KoreanDictionaryController.lookup** — GET /api/korean-dictionary/lookup?word=..., authenticated. KoreanDictionaryLearningService.lookup normalizes selected text (Hangul required, <=120), performs configured KRDICT lookup or returns no-result; it reads no deck/card and writes nothing. HTTP 200 AjaxResult wraps found/configured/word/pronunciation/meaning/part-of-speech/URL; invalid word is 400 (KoreanDictionaryController:31-38; KoreanDictionaryLearningService:37-45,73-80). korean-dictionary.js calls it after deck list load and lets the user manually supply meaning if no result.
- **KoreanDictionaryController.decks** — GET /api/korean-dictionary/decks, authenticated principal. It reads only non-deleted decks owned by that principal newest-updated plus card count per deck; shared decks are intentionally absent (controller:40-43; service:47-52). Response is AjaxResult {decks:[id,title,cardCount]}; widget caches it in its browser variable decksLoaded, no server session/write (korean-dictionary.js:13).
- **KoreanDictionaryController.save** — POST /api/korean-dictionary/flashcards JSON, CSRF-protected through widget. Service requires an owner deck, normalizes Korean word and Vietnamese meaning (truncates to 1000; strips text after ' — '), dedupes exact frontText, otherwise inserts Flashcard at count-based sortOrder (controller:45-55; service:54-97). Response AjaxResult gives deck/card/title/url/alreadySaved; invalid input=400, foreign/missing deck=404. Pronunciation/POS/source URL submitted by client are not persisted in Flashcard schema.

### Article-scoped dictionary → owner flashcard

- **DiscoveryVocabularyController.lookup** — GET /api/discovery/articles/{articleId}/dictionary?word=..., authenticated. It first requires a PUBLISHED article, normalizes Hangul text, then reads per-article NewsVocabulary cache before configured KRDICT; upstream failure is converted to no-result, not a write (controller:41-58; DiscoveryVocabularyLearningService:56-82,131-159). Returns AjaxResult DictionaryLookup or 400 invalid/404 unpublished-or-missing/500 unexpected. No current static JS/template caller targets this article-scoped route; the active discovery widget calls the global KoreanDictionaryController endpoints instead.
- **DiscoveryVocabularyController.save** — POST /api/discovery/articles/{articleId}/flashcards JSON, authenticated. It requires the article PUBLISHED, resolves cached/upstream dictionary data when available (overriding client metadata), otherwise validates manual meaning/optional metadata, then owner-gates deck and dedupes or inserts one Flashcard (controller:60-81; service:84-128). The only durable write is Flashcard; no NewsVocabulary row is created by this method. Response is AjaxResult SaveVocabularyResult; validation=400, inaccessible article/deck=404.

### Admin news mutations (all require ROLE_ADMIN)

- **AdminNewsController.saveDictionaryApiKey** — POST /admin/news/dictionary form apiKey. Null principal produces 401; otherwise delegates to platform dictionary setting save with actor id, adds flash success/error and redirects /admin/news (controller:93-114; DiscoveryDictionarySettingsService:25-26). It writes/deletes the stored secret setting, never returns raw key; admin/news.html:105 form is SSR, no AJAX.
- **AdminNewsController.resetSample** — POST /admin/news/reset-sample?returnTo=discover|admin. If raw-preview feature flag is false it is 404; otherwise deleteOneRecentArticlePerSource(5) selects at most one PUBLISHED recent article per enabled source, hard-deletes selected rows and flushes, then flash+redirects (controller:139-156; AdminNewsService:71-92). It is a local preview cleanup, not an ingestion reset or blacklist write.
- **AdminNewsController.deleteArticles** — POST /admin/news/articles/delete with repeated articleIds, blacklistBeforeDelete and page. Service de-duplicates IDs and loads extant rows; with false it hard-deletes/flushes, with true it changes selected rows to BLACKLISTED and scrubs source/media fields rather than deleting (NewsBlacklistService:41-71), then flash summary and redirect /admin/news?page=max(1,page)#articles (controller:158-174; AdminNewsService:94-105). Missing IDs are skipped; no partial client state is stored.
- **AdminNewsController.blacklistArticles** — POST /admin/news/articles/blacklist repeated articleIds/page. It de-duplicates/load existing rows, delegates blacklist service, flash-reports blacklist/skipped and redirects same anchor; it does not delete article rows in this handler (controller:176-189; AdminNewsService:108-113).
