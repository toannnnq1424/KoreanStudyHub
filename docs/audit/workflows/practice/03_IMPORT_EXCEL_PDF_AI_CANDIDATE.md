# Practice import: Excel, Text/PDF AI và candidate review/apply

Import không ghi thẳng nội dung provider/file vào draft. Cả Excel và PDF AI đều tạo `PracticeAuthoringCandidate`, bắt giảng viên review, rồi apply có optimistic/version/digest/idempotency gate.

## 1. Chọn target trong editor

Editor xác định chính xác:

- `draftId`;
- `testNo`;
- `skill`;
- `lessonCode`.

Target được backend `PracticeImportTargetService.requireExactTarget` đối chiếu với draft owner và cấu trúc hiện tại. Client không thể đổi hidden target để append vào lesson/skill không tồn tại.

## 2. Quick Excel: tải template

Từ trang import, click tải mẫu:

```text
GET /practice/manage/excel/template/quick-v1
```

`PracticeAssessmentExcelController.quickTemplate`, dòng 59–68, gọi `PracticeAssessmentExcelService.buildQuickTemplate`, trả bytes XLSX với Content-Disposition download. Không DB mutation.

## 3. Quick Excel: xem trước

Button **“Xem trước Quick Excel”** tại `practice/manage/excel-import.html:43`; script dòng 151 gửi multipart:

```text
POST /practice/manage/excel/preview
file, draftId, testNo, skill, lessonCode
```

`PracticeAssessmentExcelController.preview`, dòng 70–100:

1. `requireExcelImportContext` owner/target-check.
2. `PracticeAssessmentExcelService.preview` kiểm file/XLSX contract, parse workbook, normalize rows sang canonical assessment model và chạy validation.
3. Trả preview DTO/errors; draft chưa đổi.
4. Bad format 400 có code; missing 404; forbidden 403; unexpected 500 khẳng định draft chưa thay đổi.

## 4. Quick Excel: tạo candidate

Sau preview, button **“Tạo candidate”** (`excel-import.html:81`) gọi cùng JS với action `import`:

```text
POST /practice/manage/excel/import
```

`PracticeAssessmentExcelController.createCandidate`, dòng 102–146, parse lại file server-side (không tin preview DOM), tạo candidate qua service và trả:

```json
{
  "candidateId": "uuid",
  "state": "REVIEWING (hoặc state của candidate idempotent đã có)",
  "candidateVersion": "<lock version hiện tại; không phải hằng số>",
  "contentDigest": "sha256...",
  "reviewUrl": "/practice/manage/authoring-candidates/{id}"
}
```

JS điều hướng review URL. Chưa append draft ở bước này.

`candidateVersion` là JPA `lockVersion`; client phải echo đúng giá trị trả về, không được giả định luôn là `0`. Quick Excel hiện trả key response `contentDigest` (`PracticeAssessmentExcelController.java:117–124`), trong khi Text/PDF trả `candidateDigest` (`PracticePdfImportApiController.java:169–177`). Hai giá trị là cùng canonical content digest của candidate; trang review không dùng response tạo candidate để mutation mà tải `GET /{candidateId}/data` và gửi lại `candidateDigest` trong mọi POST.

## 5. Text/PDF AI: thao tác UI và HTTP

Editor link import (`editor.html:2646`) mở `GET /practice/manage/import?...`; `PracticeImportController` render `practice/manage/import-wizard`.

User chọn:

- source type `TEXT` hoặc `PDF`;
- operation `EXTRACT` hoặc `GENERATE`;
- pasted `sourceText` hoặc multipart `file`;
- `lecturerRequest`;
- exact target fields;
- PDF `startPage/endPage`.

Bấm submit tại `import-wizard.html:95`; JS dòng 209 gửi:

```text
POST /practice/manage/pdf-authoring/candidates
Content-Type: multipart/form-data
```

`PracticePdfImportApiController.createBasicCandidate`, dòng 70–126:

1. authorize exact target;
2. normalize operation (chỉ EXTRACT/GENERATE);
3. `PracticePdfAiPayloadBuilder.buildBasicText/buildBasicPdf`;
4. `PracticePdfAiOrchestrator.generate`;
5. `PracticePdfAuthoringCandidateAssembler.assemble` validate/normalize provider output và persist candidate;
6. trả candidate identity/review URL.

## 6. PDF/Text input safety

`PracticePdfAiPayloadBuilder`:

- Text không rỗng, dưới configured character budget; tạo evidence `TEXT_SPAN/text-1` và SHA-256 (`37–66`).
- PDF tối đa 20 MiB, MIME `application/pdf`, `.pdf`, magic `%PDF-`, không encrypted (`69–121`, `160–197`).
- page range hợp lệ và không quá max selected pages;
- PDFBox extract từng page thành evidence `PAGE/page-N`; tổng text bounded; scan-only/no text bị từ chối (`124–148`).
- raw byte array được zero-fill ở finally.
- nguồn luôn đặt dưới `untrustedSource`; text trong PDF không trở thành system instruction.

## 7. Request gửi AI cho PDF authoring

`PracticePdfAiOrchestrator.generate/request`, dòng 58–135:

1. Resolve enabled binding purpose `PRACTICE_PDF_AUTHORING`; unavailable fail trước network.
2. Load optional enabled Admin system prompt tên `PRACTICE_PDF_AUTHORING`, tối đa 20.000 chars.
3. Input JSON gồm:

```json
{
  "contract": "practice-pdf-authoring-output-v1",
  "operation": "EXTRACT|GENERATE",
  "target": {"skill":"...","testNo":1,"lessonCode":"..."},
  "lecturerRequirements": "...",
  "untrustedSource": {"evidence":[...]},
  "sourceDigest": "sha256:...",
  "requestEvidenceIds": ["page-1"]
}
```

4. `PracticeStructuredGenerationRequest`:
   - capability `STRICT_STRUCTURED_TEXT_VISION`;
   - immutable authority snapshot/prompt version;
   - code-owned system prompt separates safety > Admin prompt > lecturer request > untrusted source;
   - response schema `practice_pdf_authoring_output_v1`;
   - optional bounded images;
   - max output 16.384 tokens;
   - deterministic idempotency key from source/target/binding/profile/model/prompts.
5. Sau response, orchestrator resolve binding lần nữa; provider/profile/model revision đổi giữa request thì `PROVIDER_BINDING_CHANGED`, output bị bỏ.

## 8. AI bắt buộc trả gì

Không Markdown/prose. Root strict JSON (`PracticePdfAuthoringJsonContract:11–55`):

```json
{
  "schemaVersion": "practice-pdf-authoring-output-v1",
  "operation": "EXTRACT|GENERATE",
  "sourceDigest": "sha256:...",
  "groups": [
    {
      "sourceGroupId": "...",
      "label": "...",
      "instruction": "...",
      "stimulus": {
        "type": "...",
        "passageText": "...",
        "transcriptText": "...",
        "sourceRefs": []
      },
      "sourceRefs": [],
      "questions": [
        {
          "sourceQuestionId": "...",
          "questionType": "SINGLE_CHOICE|MULTIPLE_ANSWER|TRUE_FALSE_NOT_GIVEN|FILL_BLANK|MATCHING|ESSAY|SPEAKING",
          "essayTaskType": "...",
          "prompt": "...",
          "points": 1,
          "explanationVi": "...",
          "questionContent": {"schemaVersion":"question-content-v..."},
          "answerSpec": {"schemaVersion":"answer-spec-v1"},
          "sourceRefs": [],
          "confidence": 0.0
        }
      ]
    }
  ],
  "warnings": [{"code":"...","messageVi":"...","sourceRefs":[]}]
}
```

`additionalProperties=false` ở mọi object. Source refs phải thuộc evidence request. AnswerSpec buộc typed correct option/value/blanks/scoring policy; Q51/Q52 buộc đúng hai blank và `writing-blank-authority.v1`. Contract cấm score/result/rubric/findings/learner submission/storage URL/publication action. Backend output validator kiểm lại semantics; JSON qua schema nhưng evidence/type không nhất quán vẫn bị reject.

## 9. Candidate state machine

`PracticeAuthoringCandidate` lưu owner/source digest/revision/exact target/base draft version/candidate JSON/content digest/expiry/lock version.

```text
PARSED → NORMALIZED → VALIDATED → REVIEWING → READY_TO_APPLY → APPLIED
                                      └──────────────→ REJECTED
non-terminal quá hạn ───────────────────────────────→ EXPIRED
parse/normalize failure ────────────────────────────→ FAILED
```

Candidate terminal immutable; warnings phải do chính actor acknowledge trước READY (`PracticeAuthoringCandidate:171–267`).

## 10. Trang review và các button

`GET /practice/manage/authoring-candidates/{id}` (`PracticeAuthoringCandidateReviewController:53–64`) owner-load rồi render `candidate-review.html`. JS tải `GET /{id}/data`.

Mỗi mutation gửi cả `candidateVersion` và `candidateDigest`:

- **Lưu rà soát** (`candidate-review.html:50`) → `POST /{id}/review`, body thêm edited `groups`, `acknowledgeWarnings`; controller dòng 79–105 normalize + validate + increment lock version.
- **Đánh dấu sẵn sàng** (dòng 64) → `POST /{id}/ready` (`107–130`); blockers/warnings chưa acknowledged trả 422.
- **Từ chối candidate** (dòng 62) → `POST /{id}/reject` (`132–155`); state terminal rejected.
- **Xem như học viên** (dòng 63) → `POST /{id}/learner-preview` (`157–178`); projector tạo learner-safe preview, không đổi draft.
- **Áp dụng vào bản nháp** (dòng 65) → `POST /{id}/apply` (`180–222`) với UUID `applyRequestId`.

Stale version/digest trả 409; foreign candidate trả 403; validation/contract trả 422.

## 11. Apply candidate vào draft

`PracticeAuthoringCandidateApplyService.apply`, dòng 80–209:

1. Owner-visible candidate + `PracticeAction.EDIT` draft authorization.
2. Stable lock order: candidate row trước, exact draft row sau.
3. Tìm prior apply event theo `(candidateId,applyRequestId)`; cùng payload replay kết quả cũ, payload khác bị reject.
4. Buộc candidate `READY_TO_APPLY`, chưa expired, version/digest đúng.
5. Buộc draft version hiện tại bằng `baseDraftVersion`; nếu user sửa draft sau khi candidate tạo → `TARGET_DRAFT_VERSION_CONFLICT`, không merge đoán.
6. Project append candidate vào đúng target, normalize whole draft, chạy full validator và material authorization.
7. Save/flush draft một lần, ghi durable apply event, chuyển candidate `APPLIED` trong cùng transaction.
8. Response `DRAFT_APPLIED` có `editorUrl`; JS điều hướng editor.

AI output vì vậy không bao giờ tự publish và không bypass lecturer review/draft validation.

## 12. Material library trong import/editor

- `GET /practice/manage/assets` (`PracticePdfImportApiController:128–134`) trả assets của actor.
- `POST /practice/manage/drafts/{draftId}/assets` (`144–153`) link asset vào exact section/group/question/placement/alt text, owner + placement authority check.
- `DELETE /practice/manage/assets/{assetId}` (`PracticePdfImportApiController:136–142`) và form MVC dưới đây đều gọi cùng `LecturerAssetService.deleteAsset`.
- UI kho gọi `PracticeMaterialLibraryPageController.page`, `src/main/java/com/ksh/features/practice/manage/controller/PracticeMaterialLibraryPageController.java:34-39`, qua `GET /practice/manage/materials`. `PracticeMaterialLibraryService.catalog`, `.../PracticeMaterialLibraryService.java:51-87`, giới hạn 100 asset owner và 100 shared; shared được suy ra từ collaboration còn hiệu lực → material reference, không phải mọi public asset.
- Delete form tại `templates/practice/manage/material-library.html:74-76` gửi `POST /practice/manage/materials/{assetId}/delete` tới `PracticeMaterialLibraryPageController.delete`, dòng 41-53. `LecturerAssetService.deleteAsset`, `.../LecturerAssetService.java:342-372`, khóa asset, buộc đúng owner và **từ chối nếu còn bất kỳ reference**; nó không âm thầm giữ rồi báo xóa thành công. Chỉ asset unreferenced mới chuyển `DELETION_PENDING`, set `deletedAt` và queue `PracticeAssetLifecycleTask.DELETE`; worker mới xóa physical object.
- Preview/audio/link dùng `GET /practice/materials/{assetId}/content` → `PracticeMaterialController.content`, `src/main/java/com/ksh/features/practice/controller/PracticeMaterialController.java:41-68` → `PracticeMaterialAccessService.load`. Service cho owner, collaborator đọc được draft reference, student có exact attempt, author/editor được authorize, GLOBAL current published hoặc ACTIVE enrollment của class (`PracticeMaterialAccessService.java:53-137`). Biết asset id không đủ quyền.
- `PracticeMaterialController` parse header `Range`; range hợp lệ trả 206 + `Content-Range`, invalid trả 416, full trả 200. Mọi nhánh có `Cache-Control: no-store`, `Accept-Ranges: bytes`, inline filename và `nosniff` (`PracticeMaterialController.java:51-86`).
