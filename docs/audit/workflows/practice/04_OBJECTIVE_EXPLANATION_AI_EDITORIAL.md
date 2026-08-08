# Practice Reading/Listening: tạo, duyệt và phát hành lời giải AI typed

Workflow này khác với chấm bài: AI tạo **lời giải dùng chung của câu hỏi đã xuất bản**, không đọc câu trả lời của một học viên và không được tự đổi đáp án/chiến lược mà giảng viên chọn.

## 1. Giảng viên chọn chiến lược giải thích

Trong editor `templates/practice/manage/editor.html:765–835`, khi đang sửa câu Reading/Listening, UI hiện select **“Chiến lược giải thích cho học viên”** và ba button:

- **Tạo bản nháp** — dòng 801–805.
- **Lưu chỉnh sửa** — dòng 806–810.
- **Duyệt để xuất bản** — dòng 811–815.

Select gọi `saveCurrentNode()` tại dòng 772–775. Giá trị `question.explanationStrategy.strategyCode` đi vào JSON draft ở `editor.html:5408–5426`, rồi được lưu bởi workflow autosave đã mô tả trong `02_AUTHORING_DRAFT_PUBLISH_GOVERNANCE.md`. Strategy vì thế là authority của draft; AI không được chọn lại strategy.

## 2. Mở câu hỏi: UI tải revision hiện tại

`refreshObjectiveEditorialPanel`, `editor.html:4422–4457`, chỉ chạy khi:

1. câu có `clientId`;
2. draft đã tồn tại ở backend;
3. câu đã có `strategyCode`.

Frontend gửi:

```text
GET /api/practice/manage/explanations/drafts/{draftId}/questions/{questionClientId}/current
```
`PracticeExplanationController.current`, dòng 79–87, lấy actor từ principal rồi gọi `ObjectiveExplanationEditorialService.current`.

Service, `ObjectiveExplanationEditorialService.java:118–143`:

1. `requireDraft(..., EDIT)` kiểm quyền sửa draft.
2. `currentAuthority` dựng lại authority hiện tại từ chính JSON draft.
3. Lấy revision mới nhất của đúng `draftId + questionClientId`.
4. `requireRevisionMatches` và `client.cleanAndValidateJson` loại revision cũ/sai contract.
5. Không có revision hợp lệ: controller trả 404/empty; UI hiện “Chưa có bản nháp”.
6. Có revision: UI render state `GENERATED_DRAFT`, `APPROVED` hoặc `INVALIDATED` tại `editor.html:4385–4419`.

## 3. Bấm “Tạo bản nháp”

`generateObjectiveExplanationDraft`, `editor.html:4459–4477`:

1. gọi `saveCurrentNode()`;
2. bắt buộc `performAutosave()` thành công để backend có đúng nội dung/strategy mới nhất;
3. gửi:

```text
POST /api/practice/manage/explanations/drafts/{draftId}/questions/{questionClientId}/generate
Accept: application/json
X-CSRF-TOKEN: ...
```

`PracticeExplanationController.generateDraft`, dòng 67–76, gọi `ObjectiveExplanationEditorialService.generateDraft` và trả HTTP `202 Accepted` kèm `EditorialView`.

`ObjectiveExplanationEditorialService.generateDraft`, dòng 103–115:

1. yêu cầu quyền `EDIT`;
2. đọc draft và dựng `DraftQuestionAuthority`: question type, skill, prompt, instruction, content, official answer spec, stimulus/evidence, teacher explanation và strategy;
3. gọi `ReadingListeningExplanationClient.generate(context, List.of())`;
4. lưu output thành revision mới bằng `saveRevision`; các revision đang hiệu lực trước đó bị `invalidate` tại dòng 379–400.

Lưu ý: endpoint trả `202`, nhưng lệnh gọi provider ở nhánh editorial hiện chạy đồng bộ trong request. `202` ở đây không có nghĩa là UI phải poll một job riêng.

## 4. Request backend gửi tới AI

`ReadingListeningExplanationClient.generateThroughStructuredPort`, dòng 236–289, tạo `PracticeStructuredGenerationRequest`:

```text
purpose           = PRACTICE_RL_EXPLANATION
operation         = reading-listening-explanation
capability        = STRICT_STRUCTURED_TEXT_VISION
schemaVersion     = v4
promptVersion     = v9-objective-lecturer-strategy
language          = vi
maxOutputTokens   = 4096
response schema   = rl_answer_explanation_{questionType}
```

Authority snapshot ghép `questionId`, `questionVersionId`, skill, question type, strategy code/version và registry version tại dòng 247–276. Runtime AI transport sẽ resolve provider/model bằng binding của purpose `PRACTICE_RL_EXPLANATION`; không lấy provider/model từ browser.

Payload người dùng được dựng tại `ReadingListeningExplanationClient.java:174–213` và có cấu trúc logic:

```json
{
  "contextSchemaVersion": "...",
  "skill": "READING|LISTENING",
  "questionType": "SINGLE_CHOICE|MULTIPLE_ANSWER|MATCHING|TRUE_FALSE_NOT_GIVEN|FILL_BLANK",
  "strategyRegistryVersion": "...",
  "strategyCode": "...",
  "strategyVersion": "...",
  "prompt": "...",
  "instruction": "...",
  "questionContent": {},
  "answerSpec": {},
  "evidenceText": "...",
  "evidenceSourceRole": "...",
  "transcriptEvidenceScope": "LINGUISTIC_CONTENT_ONLY|NOT_APPLICABLE",
  "questionImages": [{"imageIndex": 0, "role": "...", "sha256": "..."}],
  "teacherExplanation": "...",
  "optionLabelMode": "...",
  "explanationLanguage": "vi"
}
```

Trước khi gọi AI, `generate`, dòng 53–89, chặn hai trường hợp:

- provider purpose chưa available → `PROVIDER_NOT_CONFIGURED`, không retry;
- không có text evidence được duyệt và cũng không có image evidence → `EVIDENCE_UNAVAILABLE`, không retry.

System prompt nằm tại `ReadingListeningExplanationClient.java:292–334`. Nó buộc AI:

- chỉ dùng evidence text/ảnh đã cung cấp;
- không suy diễn audio hoặc dữ kiện không tồn tại;
- không chấm và không nhắc `learnerAnswer`;
- không thay đổi/nhắc lại/đề xuất official `answerSpec`;
- exact quote phải khớp offset; image evidence phải khớp role, digest và region;
- trả JSON v4 đúng discriminator/strategy do giảng viên chọn.

## 5. AI bắt buộc trả chuỗi nào

Root JSON được khai báo strict tại `ReadingListeningExplanationClient.schema`, dòng 350–397. Các field gốc bắt buộc:

```json
{
  "schemaVersion": "v4",
  "strategyRegistryVersion": "<đúng registry của request>",
  "strategyCode": "<đúng strategy của request>",
  "strategyVersion": "<đúng version của request>",
  "questionType": "<đúng loại câu của request>",
  "explanation": {
    "textEvidenceRefs": [],
    "imageEvidenceRefs": [],
    "relevantTranslations": [],
    "strategyBlock": {}
  }
}
```

`strategyBlock` thay đổi theo loại câu/strategy:

| Loại/strategy family | Field bắt buộc trong `strategyBlock` | Code schema |
|---|---|---|
| lựa chọn — evidence | `evidenceClaims[]` | dòng 399–447 |
| lựa chọn — elimination | `optionRationales[]` phủ stable option IDs | dòng 405–447 |
| lựa chọn — full context | `contextClaims[]`, `answerClaim` | dòng 425–429 |
| evidence + elimination | ba nhóm trên | dòng 430–441 |
| `FILL_BLANK` | `blankExplanations[]` phủ stable blank IDs | dòng 450–478 |
| `MATCHING` | `targetExplanations[]`, đúng `candidateOptionId` chính thức | dòng 480–509 |
| `TRUE_FALSE_NOT_GIVEN` | `claim`, `whyTrue`, `whyFalse`, `whyNotGiven`, `missingInformation` | dòng 511–530 |

Mỗi claim gồm `claimId`, nội dung tiếng Việt và `evidenceIds`. Text evidence bắt buộc có kind/purpose/source role, Korean exact quote và offsets (`ReadingListeningExplanationClient.java:577–604`). Image evidence chứa digest/index/region (`:607` trở đi). Schema dùng `additionalProperties=false`, nên field tự phát không được chấp nhận.

## 6. Backend kiểm response

`cleanAndValidateJson`, dòng 92–146:

1. parse JSON và buộc root là object;
2. nếu transport có `_resultCompleteness`, chỉ nhận `COMPLETE` rồi bỏ field tạm;
3. kiểm đủ sáu field root;
4. so sánh tuyệt đối schema v4, question type, registry/strategy code/version với authority;
5. `validateTypeExplanation` kiểm coverage stable IDs, evidence references, offsets, digest và strategy-specific shape;
6. hợp lệ thì thêm completeness chuẩn hóa và serialize lại;
7. sai bất kỳ bước nào → `null`, caller đổi thành `INVALID_PROVIDER_RESPONSE`; output không được lưu làm READY.

Frontend nhận `EditorialView`, lưu vào map local, mở phần JSON kỹ thuật và báo **“Đã tạo bản nháp lời giải. Hãy kiểm tra trước khi duyệt.”** (`editor.html:4472–4476`). Bản này chưa hiển thị cho học viên.

## 7. Giảng viên sửa JSON và bấm “Lưu chỉnh sửa”

`saveObjectiveExplanationRevision`, `editor.html:4479–4496`, gửi:

```text
PUT /api/practice/manage/explanations/drafts/{draftId}/questions/{questionClientId}/revisions
Content-Type: application/json

{"explanationJson":"<JSON trong textarea>"}
```

Controller dòng 90–108 chặn body trống. `ObjectiveExplanationEditorialService.saveEditedDraft`, dòng 146–165, dựng lại authority và chạy lại toàn bộ `cleanAndValidateJson`; JSON sai strategy/evidence bị HTTP error, không lưu. Khi lưu revision mới, revision cũ bị invalidated và UI bắt duyệt lại.

## 8. Bấm “Duyệt để xuất bản”

`approveObjectiveExplanationRevision`, `editor.html:4498–4513`, gửi:

```text
POST /api/practice/manage/explanations/drafts/{draftId}/questions/{questionClientId}/revisions/{revisionId}/approve
```

Controller dòng 111–121 gọi `ObjectiveExplanationEditorialService.approve`, dòng 167–198:

1. yêu cầu quyền mạnh hơn: `PUBLISH`;
2. buộc revision thuộc đúng draft/câu;
3. so fingerprint/authority hiện tại;
4. validate JSON strict một lần nữa;
5. `revision.approve(actorId, now)` rồi save.

Nếu câu hỏi, source, answer hoặc strategy đổi sau lần duyệt, fingerprint không còn khớp. `publishBlockers`, dòng 221–264, trả `OBJECTIVE_EXPLANATION_APPROVAL_STALE`; editor refresh blocker và không cho xuất bản.

## 9. Bấm “Xuất bản” và lời giải được promote

Publisher gọi `requireApprovedForPublish` (`ObjectiveExplanationEditorialService.java:203–213`). Mọi câu objective phải có revision typed đã duyệt và còn đúng authority; thiếu/sai thì publish dừng trước khi mutate live graph.

Sau commit version bất biến, `PublishedVersionExplanationListener.prepare`, dòng 31–52:

1. gọi `QuestionExplanationPreparationService.preparePublishedVersion` để tạo/reuse artifact + binding theo immutable fingerprint;
2. gọi `editorialService.promoteApproved(draftId, questionVersionIdsByClient)`;
3. service reload question/section/group version, rebuild evidence/fingerprint, validate output lần cuối;
4. lock artifact và `markReady(validated, now)` tại `ObjectiveExplanationEditorialService.java:290–374`.

Chỉ artifact `READY` đã bind với đúng immutable question version mới đi tới result presenter. Học viên mở chi tiết kết quả; `templates/practice/result-detail-objective.html:399–749` render theo renderer code, evidence, translations và claim. Draft/generated-but-not-approved không có đường hiển thị này.

## 10. Nhánh job nền khi chuẩn bị explanation cho version

Ngoài editorial approve, hệ thống còn có pipeline durable cho artifact chưa READY:

1. `QuestionExplanationPreparationService.preparePublishedVersion`, dòng 59–183, duyệt chỉ Reading/Listening, dựng fingerprint, reuse artifact READY giống hệt hoặc insert `PENDING`; readiness lỗi bị mark `FAILED`; task mới có tối đa 4 attempts.
2. `QuestionExplanationGenerationWorker`, dòng 21–28, mặc định chạy sau 20 giây rồi mỗi 30 giây, mỗi lượt lấy tối đa 20 task.
3. `QuestionExplanationGenerationProcessor.processDue`, dòng 47–86, claim task bằng owner UUID; loader tái dựng immutable work.
4. Ảnh được resolve lại và SHA-256 phải khớp descriptor (`QuestionExplanationGenerationProcessor.java:89–111`).
5. Processor gọi cùng `ReadingListeningExplanationClient.generate`; transaction helper chỉ complete khi lease/task còn hợp lệ. Completion cũ bị discard.
6. Lỗi provider ghi category + retryable; task transaction quyết định retry/backoff hoặc terminal fail.

## 11. Lecturer bấm “Thử lại” explanation lỗi

Trang revision có form tại `templates/practice/manage/revisions.html:158–165`:

```text
POST /practice/manage/sets/{setId}/explanations/{questionVersionId}/retry
```

`PracticeManageController.retryQuestionExplanation`, dòng 250–275, gọi `QuestionExplanationRetryService.retryQuestionVersion` rồi redirect lại trang cùng flash message.

Retry service chỉ cho actor có quyền `PUBLISH`, lock artifact/task canonical và chỉ reset trạng thái `FAILED_RETRYABLE` về `PENDING` (`QuestionExplanationRetryService.java:68–137`). Cooldown manual là 1 phút (`:37`): quá sớm → `RATE_LIMITED`; READY/PENDING → không tạo task trùng; lỗi non-retryable → yêu cầu sửa nội dung và publish version mới.

API tương đương cho client JSON là:

```text
POST /api/practice/manage/explanations/{artifactId}/retry
```

`PracticeExplanationController.retry`, dòng 42–64, trả `202` nếu queued, `429 + Retry-After` nếu rate limited, `409` nếu không thể retry, hoặc `200` khi artifact đã READY/PENDING và không cần queue thêm.

## Tóm tắt luồng

```text
[Chọn strategy + autosave]
  → POST .../generate
  → Controller.generateDraft
  → ObjectiveExplanationEditorialService
  → Practice AI purpose PRACTICE_RL_EXPLANATION
  → strict JSON v4 + evidence validation
  → GENERATED_DRAFT
  → [Sửa] PUT .../revisions
  → [Duyệt] POST .../approve (quyền PUBLISH)
  → [Xuất bản] fingerprint/revalidate/promote artifact READY
  → GET result detail render lời giải cho học viên
```
