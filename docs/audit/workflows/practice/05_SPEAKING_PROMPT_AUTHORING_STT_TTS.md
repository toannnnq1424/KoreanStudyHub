# Practice Speaking authoring: upload prompt, STT, xác nhận context và TTS

Đây là workflow **giảng viên soạn đề bài Nói**. Audio/transcript ở đây là prompt của đề, tách biệt hoàn toàn với bản ghi câu trả lời của học viên.

## 1. Giảng viên mở một câu Speaking

Khối UI nằm tại `templates/practice/manage/editor.html:614–727` với hai mode:

- **Tải tệp âm thanh** — học viên nghe audio gốc của giảng viên; backend dùng STT chỉ để tạo context kín cho AI chấm.
- **Nhập nội dung bằng văn bản** — học viên đọc text; giảng viên có thể bật TTS để tạo audio đề bài.

Khi active câu, `manage-speaking-prompt-authoring.js:352–399` gửi:

```text
GET /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt
```

`SpeakingPromptAuthoringController.get`, dòng 62–68, gọi `SpeakingPromptAuthoringStateService.load`. Service kiểm quyền actor trên draft, load source/artifact/task/asset và trả `EditorState` gồm `sourceRevision`, `draftVersion`, input mode, original/generated audio, transcript/TTS status, operation status, option catalogue và giới hạn upload.

Frontend giữ revision/version này làm optimistic authority. Mọi mutation sau đều gửi `expectedSourceRevision` và `expectedDraftVersion`; nếu một tab khác đã sửa, backend trả conflict thay vì ghi đè.

## 2. Đổi mode hoặc sửa text/TTS options

Button mode ở `editor.html:620–629`; listener tại `manage-speaking-prompt-authoring.js:303–349`. Text và option change được debounce 700 ms (`:837–843`) rồi gửi:

```text
PUT /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt
Content-Type: application/json

{
  "inputType":"audio_upload|manual_text",
  "expectedSourceRevision":12,
  "expectedDraftVersion":8,
  "manualText":"...",
  "ttsEnabled":true,
  "voiceCode":"...",
  "speed":1.0,
  "outputFormat":"mp3"
}
```

Body được dựng tại `manage-speaking-prompt-authoring.js:845–895`. `SpeakingPromptAuthoringController.save`, dòng 102–135:

- `audio_upload` → `SpeakingPromptAuthoringService.selectAudioMode`;
- `manual_text` → `saveManualPrompt`.

Đổi mode hoặc save **không tự gọi AI** (`editor.html:616–618`). Service lock source/draft, kiểm revision, lưu lựa chọn; text/options đổi làm artifact TTS cũ `stale`/superseded. GET và save cũng không được phép ngầm enqueue TTS; chỉ button **Tạo audio** làm việc đó (`SpeakingPromptAuthoringService.java:384–388`).

## 3. Chọn/kéo file audio

UI ở `editor.html:631–670`; frontend chấp nhận `.mp3/.wav/.m4a/.ogg/.webm`, kiểm extension/MIME, dung lượng và metadata duration tại `manage-speaking-prompt-authoring.js:1010–1093`.

`upload`, dòng 1095–1198, flush thay đổi trước, khóa các control phá source, rồi dùng `XMLHttpRequest` để hiện progress:

```text
POST /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt/audio
Content-Type: multipart/form-data

file=<binary>
expectedSourceRevision=<long>
expectedDraftVersion=<long>
```

`SpeakingPromptAuthoringController.upload`, dòng 137–155:

1. `validateAudioFilename` chỉ nhận năm extension, chặn empty file (`:283–299`).
2. `SpeakingPromptOriginalAudioUploadCoordinator.uploadAndEnqueueStt` chạy.
3. Trả `202 Accepted` cùng state mới; frontend bắt đầu poll.

Coordinator, `SpeakingPromptOriginalAudioUploadCoordinator.java:25–35`:

1. `requireUploadAllowed` kiểm quyền + expected revisions trước khi tốn I/O.
2. `SpeakingPromptAssetService.uploadOriginal` tạo asset draft chưa bind.
3. Đọc lại exact bytes và `ffprobe`/verifier xác thực MIME, duration, size, SHA-256 (`SpeakingPromptAssetService.java:103–133,269–305`).
4. `bindVerifiedOriginalUpload` lock authority lần hai, activate asset, link placement `SPEAKING_PROMPT_ORIGINAL`, retire binding cũ và enqueue STT.

Giới hạn contract là tối đa 52,428,800 bytes và 600,000 ms (`SpeakingPromptAiContract.java:22–26`). Kiểm phía client chỉ để báo sớm; backend vẫn kiểm lại.

## 4. Job STT chạy nền

`SpeakingPromptAiTaskWorker`, dòng 9–40, chỉ tồn tại khi:

```properties
app.practice.speaking-prompt-authoring.worker-enabled=true
```

Mặc định scheduler chạy sau 30 giây và mỗi 30 giây. `SpeakingPromptAiTaskProcessor.processDue`, dòng 47–85:

1. lấy claimable tasks theo batch size;
2. xác nhận purpose/provider cho operation đang operational;
3. claim bằng lease token `speaking-prompt-{uuid}:{uuid}`;
4. load immutable audio/source snapshot;
5. STT gọi `sttPort.transcribe` (`:87–103`).

Purpose runtime là `PRACTICE_SPEAKING_STT` (`OpenAiSpeakingPromptSttAdapter.java:74–97`). Binding resolver cung cấp base URL, credential, model, limits và retention; adapter còn `assertCurrent` trước call để không dùng binding vừa bị admin thay.

## 5. STT gửi gì tới provider và provider phải trả gì

`OpenAiSpeakingPromptSttAdapter.callOnce`, dòng 178–219, gửi:

```text
POST {providerBaseUrl}/audio/transcriptions
Content-Type: multipart/form-data
Accept: application/json

model=<resolved model>
language=<configured language>
response_format=json
file=<exact verified audio bytes>
```

Internal request contract tại `SpeakingPromptAiContract.java:127–137`:

```text
SttRequest(VerifiedAudio, languageTag, "speaking-prompt-authoring-v1")
```

`VerifiedAudio` mang bytes, filename, MIME, SHA-256 và duration; constructor tự hash exact bytes và buộc digest khớp (`:62–90`).

Provider response tối thiểu phải là JSON:

```json
{"text":"<bản chép lời không rỗng>","confidence":0.91}
```

- `text` bắt buộc là string không blank;
- `confidence` không bắt buộc, nhưng nếu có phải là number từ 0 đến 1;
- malformed/empty bị loại tại `OpenAiSpeakingPromptSttAdapter.java:221–273`.

Backend bọc output thành `SttResult(providerTranscript, confidence, provider, model, language, requestReference, purpose, retention)` (`SpeakingPromptAiContract.java:143–169`) rồi transaction complete chỉ khi lease và exact input hash vẫn còn current. Completion từ file/source cũ bị discard (`SpeakingPromptAiTaskProcessor.java:90–103`).

## 6. UI poll và giảng viên xác nhận context

Khi status nằm trong `queued`, `processing`, `retry_wait`, frontend poll GET state mỗi 2.5 giây tại `manage-speaking-prompt-authoring.js:1343–1370`.

Kết quả STT trở thành **Ngữ cảnh cho AI — học viên không nhìn thấy** (`editor.html:660–670`). Nếu confidence thấp, UI hiện cảnh báo cần review. Giảng viên sửa transcript/context, tick xác nhận rồi bấm **Lưu ngữ cảnh**; `saveTranscript`, JS dòng 1262–1309, gửi:

```text
PUT /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt/transcription
Content-Type: application/json

{
  "expectedSourceRevision":12,
  "expectedDraftVersion":8,
  "lecturerContext":"...",
  "confirmed":true
}
```

Controller dòng 176–197 bắt buộc `confirmed=true`, rồi `SpeakingPromptTranscriptService.revise` lưu revision context của giảng viên. Transcript provider gốc, correction history và confirmation là dữ liệu bền; transcript không trở thành learner answer.

Giảng viên không thể chuyển câu, đổi source hoặc gỡ audio khi context đang dirty (`manage-speaking-prompt-authoring.js:352–425,800–821,1200–1215`).

## 7. STT lỗi và “Thử lại chuyển giọng nói”

Button chỉ hiện khi `failed_retryable` (`manage-speaking-prompt-authoring.js:661–670`). Click gửi:

```text
POST /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt/transcription/retry
Content-Type: application/json

{"expectedSourceRevision":12,"expectedDraftVersion":8}
```

Controller dòng 158–174 gọi `retryCurrentOperation(..., STT)`. Kết quả:

- `queued`/`already_active` → `202` và tiếp tục poll;
- `needs_review` → `200`;
- cooldown/quota → `429` + `Retry-After`;
- source/artifact không retry được → `409 NOT_RETRYABLE` (`SpeakingPromptAuthoringController.java:252–280`).

Task có tối đa 4 attempts, giới hạn mặc định 4 active/lecturer, 2 active/draft và 20 request/lecturer/hour (`SpeakingPromptAuthoringAiProperties.java:36–45`). Provider 429/5xx/timeout/transport được phân loại retryable; invalid input/malformed output không retry tự động.

## 8. Nhập text và bấm “Tạo âm thanh”

UI text/TTS nằm tại `editor.html:673–705`. Giảng viên nhập tối đa 16,000 ký tự, bật TTS, chọn voice/speed/format. `generateTts`, `manage-speaking-prompt-authoring.js:1311–1341`, flush save rồi gửi:

```text
POST /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt/tts
Content-Type: application/json

{
  "expectedSourceRevision":15,
  "expectedDraftVersion":9,
  "voiceCode":"...",
  "speed":1.0,
  "outputFormat":"mp3"
}
```

`SpeakingPromptAuthoringController.generateTts`, dòng 199–233, gọi `SpeakingPromptAuthoringService.requestTts`:

1. bắt buộc source mode `manual_text`, TTS enabled và nonblank text;
2. option request phải khớp option vừa lưu;
3. fingerprint gồm exact Unicode-NFC text hash + provider/model/language/voice/speed/format/contract;
4. artifact READY cùng fingerprint được reuse và trả `200`;
5. nếu không, insert/reuse pending task và trả `202`.

Worker dùng cùng processor nhưng nhánh `TTS`: `ttsPort.synthesize`, lưu generated bytes thành candidate asset rồi transactionally bind nếu source/fingerprint còn current; completion stale làm candidate bị discard (`SpeakingPromptAiTaskProcessor.java:106–123`).

## 9. TTS gửi gì và provider phải trả gì

Purpose là `PRACTICE_SPEAKING_TTS` (`OpenAiSpeakingPromptTtsAdapter.java:68–92`). Adapter gửi:

```text
POST {providerBaseUrl}/audio/speech
Content-Type: application/json

{
  "model":"<resolved model>",
  "input":"<exact prompt text>",
  "voice":"<voiceCode>",
  "response_format":"<mp3/...>",
  "speed":1.0
}
```

Code nằm tại `OpenAiSpeakingPromptTtsAdapter.java:210–233`. Request nội bộ còn mang SHA-256 của Unicode-NFC text và contract `speaking-prompt-authoring-v1`; constructor buộc hash, speed 0.25–4.00 và format hợp lệ (`SpeakingPromptAiContract.java:193–217,304–311`).

TTS không trả JSON. Provider phải trả **binary audio không rỗng** với content type nằm trong allowlist. `OpenAiSpeakingPromptTtsAdapter.java:93–135`:

1. chặn body rỗng;
2. chặn MIME ngoài allowed output types;
3. `SpeakingPromptAudioVerifier.verifyTtsOutput` kiểm bytes, duration, MIME và SHA-256;
4. tạo `TtsResult` cùng provenance provider/model/language/voice/speed/format/request reference/purpose/retention.

UI poll cho đến `ready`, rồi GET media preview:

```text
GET /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt/media/{origin}?sourceRevision=...
```

Controller dòng 70–100 kiểm ownership/binding và trả private `no-store` audio. `origin` chỉ resolve original hoặc generated hiện hành; không phải URL storage công khai.

## 10. Gỡ/thay audio

Button **Gỡ âm thanh**, `editor.html:655`, gọi `removeOriginal`, JS dòng 1200–1234:

```text
DELETE /practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt/audio?expectedSourceRevision=...&expectedDraftVersion=...
```

Controller dòng 235–249 gọi `unlinkCurrentOriginalAudio`. Service unlink material reference, supersede STT context/artifact hiện hành và queue asset private nếu không còn reference. **Thay tệp** thực chất mở picker và chạy lại workflow upload; source revision bảo vệ việc completion của file cũ quay về muộn.

## 11. Điều kiện để xuất bản Speaking

`SpeakingPromptPublicationService.prepare`, dòng 65–135, lock mọi source và buộc mỗi câu Speaking có đúng một source hiện hành.

Với audio upload, `SpeakingPromptPublicationService.java:169–249` yêu cầu:

- delivery là `AUDIO_UPLOAD + AUDIO_ONLY + TEACHER_UPLOAD`;
- transcript status `READY`;
- original asset đang active và đúng placement;
- STT artifact READY, đúng asset/digest/fingerprint/provider/model/contract;
- transcript revision đã confirmed; low-confidence bắt buộc giảng viên xác nhận;
- draft audio reference trỏ đúng asset.

Với manual text, service tương tự kiểm exact text hash; nếu TTS bật thì generated artifact/audio phải READY và current, nếu tắt thì delivery text-only không được giữ audio cũ.

Sau khi publisher tạo immutable question versions, `persistContexts`, dòng 137–167, ghi `SpeakingPromptVersionContext` cho evaluator. Learner-facing content chỉ mang delivery được duyệt; transcript/context nội bộ không hiển thị cho học viên.

## Tóm tắt hai nhánh

Mọi exception từ các endpoint authoring trên được `SpeakingPromptAuthoringControllerAdvice`, `src/main/java/com/ksh/features/practice/manage/speaking/SpeakingPromptAuthoringControllerAdvice.java:18-109`, chuẩn hóa thành JSON `ApiFailure(code,message,retryAfterSeconds)`: source/optimistic conflict 409 `SOURCE_CONFLICT`, forbidden 403, not found 404, input/malformed provider output 422, rate limit 429 + `Retry-After: 30`, configuration/transport/timeout 503. Vì vậy JS nhận error contract ổn định; lỗi AI không render trang lỗi MVC.

```text
[Upload audio]
  → POST .../audio → verify bytes/digest/duration → queue STT
  → worker → POST provider /audio/transcriptions
  → {text, confidence} → lecturer sửa/xác nhận context
  → publish immutable prompt audio + evaluator context

[Nhập text + bật TTS]
  → PUT source/text/options
  → POST .../tts → queue TTS
  → worker → POST provider /audio/speech
  → verified binary audio → bind generated asset
  → publish text/audio hiện hành
```
