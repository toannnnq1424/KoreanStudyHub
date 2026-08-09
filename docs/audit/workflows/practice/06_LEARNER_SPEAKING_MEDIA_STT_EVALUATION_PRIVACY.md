# Practice Speaking learner: ghi âm, lưu media, STT, AI feedback và direct-audio boundary

Tài liệu này nối phần thao tác player với pipeline Speaking AI. Workflow submit/job chung nằm tại `PRACTICE_SUBMIT_AND_AI_EVALUATION.md`; ở đây đi sâu vào exact audio request và hai lần gọi provider.

## 1. Player giao từng câu Speaking

`static/js/practice/player-speaking.js:411–525` đọc immutable `delivery.questions` do server render. Mỗi câu chạy đúng `delivery.steps`:

1. `PROMPT_PLAYBACK` → phát audio đề đúng `promptPlayLimit` (`:223–297`);
2. `PREPARATION` → countdown `preparationSeconds`;
3. `RECORDING` → mở microphone và ghi đúng `responseSeconds`.

Browser chọn MIME đầu tiên được `MediaRecorder` hỗ trợ trong `audio/webm;codecs=opus`, `audio/mp4`, `audio/webm` (`player-speaking.js:166–175`). `beginRecording`, dòng 299–339:

- gọi `getUserMedia` với echo cancellation, noise suppression, auto gain;
- thu chunk 250 ms;
- tự stop khi hết thời gian;
- ghép `Blob`; blob rỗng buộc học sinh làm lại.

## 2. Sau mỗi câu, frontend upload bản ghi

`uploadRecording`, `player-speaking.js:341–409`, tạo một multipart part duy nhất tên `file` rồi gửi:

```text
POST /practice/attempts/{attemptId}/questions/{questionId}/speaking-media
Content-Type: multipart/form-data
Accept: application/json
X-CSRF-TOKEN: ...

file=<recorded Blob>
```

Nếu request mạng lỗi, blob vẫn ở RAM trong tab và UI hiện **“Thử lưu lại”** (`:495–520`); player không nhảy câu cho đến khi server trả media `READY`.

`PracticeSpeakingMediaController.uploadOrReplace`, dòng 53–84:

1. controller chỉ được tạo khi `app.practice.speaking-media.upload-api-enabled=true`;
2. actor lấy từ `Authentication`, không lấy userId từ client;
3. `requireSingleLearnerAudio` buộc toàn request có đúng một file part và đúng tên `file` (`:104–113`);
4. gọi `SpeakingAudioUploadService.uploadOrReplaceForOwner`;
5. trả JSON `no-store`.

Response thành công:

```json
{
  "mediaId": 123,
  "attemptId": 45,
  "questionId": 67,
  "status": "READY",
  "active": true,
  "byteSize": 123456,
  "durationMs": 60321,
  "mimeType": "audio/webm",
  "playbackPath": "/practice/attempts/45/questions/67/speaking-media/123/content",
  "lockVersion": 0
}
```

Frontend chỉ chấp nhận `status=READY && active=true` tại `player-speaking.js:397–408`.

## 3. Backend xác minh và activate media

`SpeakingAudioUploadService.uploadOrReplaceForOwner`, dòng 39–78:

1. `PracticeSpeakingMediaService.validateUploadTargetForOwner` kiểm attempt thuộc actor, skill `SPEAKING`, status `IN_PROGRESS`, chưa hết deadline và question nằm trong immutable published version.
2. `SpeakingAudioPreparationService.prepare` đọc stream theo giới hạn, kiểm empty/declared size/MIME, probe container/codec/streams/duration, tính content hash và ghi temporary object bằng storage profile.
3. Transaction ghi row `UNREFERENCED_TEMPORARY`; đồng thời queue expiry cleanup (`PracticeSpeakingMediaService.java:80–103`).
4. Storage `promoteTemporary(profile,key)` đổi temporary object thành ready object.
5. Transaction `promoteTemporaryForOwner`, dòng 105–140, lock attempt + media, xác minh temporary identity/profile/key, supersede READY row cũ, promote row mới thành `READY` và enqueue cleanup temporary/superseded.
6. Nếu DB registration thất bại, service cố xóa object đã chuẩn bị; nếu activate thất bại sau physical promote, service ghi orphan-cleanup intent (`SpeakingAudioUploadService.java:110–151`).

Thay bản ghi dùng cùng endpoint: READY cũ bị `SUPERSEDED`, không có hai bản active cho cùng attempt/question (`PracticeSpeakingMediaService.java:128–140`).

## 4. Quyền và phạm vi media

`PracticeSpeakingMediaService.validateMutableAttempt`, dòng 251–261, không cho upload/delete sau submit hoặc sau deadline. `immutableSpeakingQuestionIds`, dòng 273–292, rebuild snapshot từ `publishedVersionId/setVersionId/testVersionId/sectionVersionId`; client không thể thay path `questionId` để gắn audio sang câu ngoài đề.

`PracticeSpeakingMediaControllerAdvice`, `src/main/java/com/ksh/features/practice/controller/PracticeSpeakingMediaControllerAdvice.java:24-133`, chuyển lỗi upload/delete thành JSON `{code,message}` kèm `no-store`: multipart/input 400, quá dung lượng 413, container/codec/type sai 415, probe/storage tạm lỗi 503, state conflict 409, deadline 410, auth 401. Frontend không nhận raw exception/provider detail.

Học sinh có thể nghe lại qua:

```text
GET /practice/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}/content
Range: bytes=...
```

`PracticeSpeakingMediaPlaybackController`, dòng 50–82, chỉ role STUDENT, lấy owner từ principal, `openForOwner` kiểm exact ownership/scope/status rồi stream. Hỗ trợ `200`, `206` và `416`; header `private, no-store`, `nosniff`, inline, accept-ranges (`:85–94`). Endpoint chỉ tồn tại khi `playback-api-enabled=true`.

`PracticeSpeakingMediaPlaybackControllerAdvice`, `src/main/java/com/ksh/features/practice/controller/PracticeSpeakingMediaPlaybackControllerAdvice.java:16-51`, cũng áp dụng cho direct-audio reviewer inspection/playback. Nó trả 401 cho chưa xác thực, 403 cho authorization denial nhưng dùng cùng public message “target was not found” để không lộ sự tồn tại, và 404 khi media unavailable; tất cả `private, no-store`.

Delete trước submit:

```text
DELETE /practice/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}
```

Controller dòng 86–102 gọi `deleteForOwner`; DB chuyển `DELETION_PENDING`, ghi durable cleanup, rồi cố physical delete ngay. Nếu storage lỗi, worker xử lý lại; API nói `pendingCleanup=true` (`SpeakingAudioUploadService.java:80–96`).

## 5. Hoàn tất mọi câu và nộp attempt

Sau mỗi upload READY, player tự chuyển câu. Câu cuối gọi `finalizeAttempt`, `player-speaking.js:527–537`, rồi submit form thường tới endpoint submit đã mô tả trong `PRACTICE_SUBMIT_AND_AI_EVALUATION.md`.

Trước khi queue evaluation, `PracticeSpeakingMediaService.requireReadyMediaForOwner`, dòng 174–219, buộc **mỗi immutable Speaking question có đúng một media READY**. Thiếu một câu → không nộp; browser không thể chỉ gửi danh sách media giả trong answer JSON.

## 6. Worker đánh giá reload exact media identity

Evaluation job không nhét audio bytes trong DB. `SpeakingEvaluationApplicationService.evaluateAudio`, dòng 312–345:

1. dựng `SpeakingEvaluationIdentity` từ attempt/question version, immutable prompt-context fingerprint, mediaId + media lockVersion, STT/evaluator models và prompt/rubric/schema versions;
2. reuse chỉ khi stored result khớp toàn bộ identity;
3. gọi STT;
4. gọi transcript-grounded evaluator;
5. resolve media lại sau provider call; nếu mediaId/version đổi, trả `STALE_AUDIO_IDENTITY`, không commit completion cũ.

`SpeakingTranscriptionMediaResolver.resolveForOwner`, dòng 30–76, query chỉ media owner-bound `READY`, profile `PRACTICE_SPEAKING`, allowed MIME/size/duration và object còn tồn tại. Request mở stream lazily từ storage, không cấp public object URL.

## 7. Lần gọi AI thứ nhất: learner-response STT

`OpenAiSpeakingTranscriptionClient.transcribe`, dòng 78–148, resolve purpose:

```text
PRACTICE_SPEAKING_STT
operation audit = LEARNER_RESPONSE_STT
data class      = LEARNER_RESPONSE_AUDIO
```

Request provider tại `OpenAiSpeakingTranscriptionClient.java:177–188,248–275`:

```text
POST {providerBaseUrl}/audio/transcriptions
Content-Type: multipart/form-data
Accept: application/json

model=<binding model>
file=<stream của media READY>
language=<configured language>
response_format=json
include[]=logprobs        # chỉ khi config bật và model hỗ trợ
```

Provider phải trả JSON có tối thiểu:

```json
{"text":"<transcript không rỗng>","logprobs":[...]}
```

`parse`, dòng 285–325:

- JSON hỏng → `INVALID_PROVIDER_RESULT/PROVIDER_MALFORMED_JSON`;
- thiếu/blank `text` → `TRANSCRIPTION_UNAVAILABLE/PROVIDER_EMPTY_TRANSCRIPT`;
- nếu có logprobs, backend tự tính confidence; dưới threshold → `TRANSCRIPTION_LOW_CONFIDENCE`, còn lại `EVALUATED`;
- lưu cả transcript, normalized transcript, provider/model/language/confidence/duration/latency; không coi provider text là điểm.

## 8. Lần gọi AI thứ hai: đánh giá transcript

`SpeakingEvaluationOrchestrator.evaluate`, dòng 29–47, chỉ gọi evaluator nếu transcription status score-bearing. `OpenAiCompatibleSpeakingEvaluationClient`, dòng 38–70, chỉ chấp nhận capability `TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION`, policy versions hiện hành và binding available.

Request structured được dựng tại dòng 96–145:

```text
purpose         = PRACTICE_SPEAKING_EVALUATION
operation       = speaking-transcript-evaluation
capability      = STRICT_STRUCTURED_TEXT_VISION
authority       = question/version + prompt-context + policy bundle
schema name     = ksh_speaking_evaluation
max tokens      = 4096
image evidence  = QUESTION_IMAGE (nếu có)
```

Payload `SpeakingEvaluationPromptBuilder.userPayloadObject`, dòng 40–117, gồm:

- task attempt/question/question-version + target level;
- immutable lecturer prompt context, fingerprint và contract identity;
- transcript + normalized transcript + confidence + STT provider/model;
- policy bundle/prompt/rubric/schema/evidence versions;
- allowed transcript-grounded rubric/subcriteria;
- deterministic pre-evaluation rule signals;
- explicit flags `learner_audio_received_by_evaluator=false`, `acoustic_criteria_available=false`, `holistic_score_available=false`.

Nghĩa là evaluator thứ hai **không nhận audio learner**. Nó chỉ nhận transcript và ảnh câu hỏi đã duyệt; pronunciation/fluency/acoustic criteria không được bịa từ chữ.

## 9. AI evaluator bắt buộc trả chuỗi nào

Schema strict nằm tại `SpeakingEvaluationPromptBuilder.java:120–253`; `additionalProperties=false`. Root bắt buộc:

```json
{
  "evaluation_status":"EVALUATED",
  "score_available":false,
  "interpreted_intent":null,
  "intent_confidence":null,
  "overall_score":null,
  "level_label":null,
  "overall_summary":"...",
  "task_achievement_summary":"...",
  "action_plan":[
    {"criterion_id":"...","sub_criterion_id":"...","title":"...","instruction":"...","reason":"...","priority":"..."}
  ],
  "criterion_feedback":[
    {
      "criterion_id":"<allowed transcript criterion>",
      "display_name":"...","score":0,"max_score":0,"level_label":"...","summary":"...",
      "strengths":[],"needs_improvement":[],"subcriteria":[]
    }
  ],
  "transcript_annotations":[
    {
      "finding_id":"...","evidence_id":"...","criterion_id":"...","sub_criterion_id":"...",
      "evidence_source":"TRANSCRIPT","annotation_type":"strength|needs_improvement|advisory",
      "operation":"KEEP|REPLACE|REDUNDANT","category":"...","severity":"LOW|MEDIUM|HIGH",
      "confidence":0.0,"explanation_vi":"...","suggestion_ko":"..."
    }
  ],
  "rubric_scores":[
    {"criterion":"...","score":0,"max_score":0,"feedback":"...","evidence_ids":[]}
  ],
  "confidence_notes":"...",
  "evidence":[
    {
      "evidence_id":"...","source":"TRANSCRIPT","criterion_id":"...","sub_criterion_id":"...",
      "evidence_scope":"TEXT_SPAN","exact_text":"...","start_offset":0,"end_offset":1,
      "occurrence_index":1,"occurrence_count":1,"normalization":"UTF16_EXACT_V1",
      "source_hash":"...","confidence":0.0
    }
  ],
  "recommendations":[],
  "upgraded_answer":"...",
  "sample_answer":"...",
  "error_category":"",
  "retryable":false
}
```

Các rubric/subcriterion IDs là enum allowlist từ request, evidence exact text/offset/hash phải khớp transcript. Đặc biệt schema buộc `score_available=false`, `overall_score=null`, `level_label=null`; Speaking transcript-only tạo hồ sơ phản hồi ngôn ngữ, không tạo điểm Speaking tổng thể (`SpeakingEvaluationPromptBuilder.java:131–160`).

Normalizer ghi lại authority fields, verify evidence/criterion/score contract và fail closed. UI result chỉ render pronunciation/acoustic chip khi một capability direct-audio được governed thực sự; transcript-only giữ acoustic evidence `NOT_SCORABLE`.

## 10. Direct-audio hiện có và chưa có gì

Source có policy/control-plane cho purpose `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION`, nhưng cần phân biệt **code contract** với **workflow production đang nối dây**:

- `DirectAudioSpeakingEvaluationService` và `GeminiEnterpriseDirectAudioEvaluationAdapter` là plain/final classes, không có `@Service/@Component`.
- Search production source không có nơi khởi tạo `new DirectAudioSpeakingEvaluationService`; adapter tự ghi rõ “not a Spring bean until workload identity and captured provider transport are approved” tại `GeminiEnterpriseDirectAudioEvaluationAdapter.java:21–24`.
- Không có controller/form dành cho learner grant/withdraw consent hoặc manager grant/revoke reviewer; hiện chỉ có coordinator/service methods và DB schema.
- Vì vậy cấu hình một binding direct-audio chưa làm submit Speaking gửi audio thẳng tới evaluator. Runtime live vẫn là **audio → STT → transcript-only evaluator**.

`V114` đã retire toàn bộ persisted dark-observation/reviewer surface: ba route `/practice/direct-audio/review/**`, ba reviewer controller, template/CSS, dark store/coordinator, reviewer playback/audit/retention worker và hai bảng dark/access-event không còn trong runtime. Đây là capability thử nghiệm, không mang điểm, nên việc retire không thay đổi chuỗi live.

Method-level boundary còn lại: `PracticeSpeakingMediaController.delete` là `DELETE /practice/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}` khi upload API bật: resolve owner, gọi `SpeakingAudioUploadService.deleteForOwner`, chuyển media sang lifecycle cleanup (`DELETION_PENDING` khi cần) và trả JSON no-store `{status,active,pendingCleanup}`. `PracticeSpeakingMediaPlaybackController.content` là student-only `GET .../speaking-media/{mediaId}/content` khi playback API bật: `openForOwner` rechecks exact owner/attempt/question/media, streams private storage by byte range (200/206 or 416) with inline/no-store/nosniff; no DB state changes or public object URL.

## 11. Consent withdrawal và cleanup boundary

Khi control-plane authorization feature được nối với caller tương lai, `DirectAudioAuthorizationCoordinator.withdrawConsent` sẽ:

1. append immutable `WITHDRAWN` consent event;
2. `DirectAudioWithdrawalMediaService.enqueueForWithdrawal` queue xóa exact private learner media trong cùng transaction.

Không còn dark-observation hoặc reviewer-access retention worker sau V114. Media withdrawal vẫn đi qua durable speaking cleanup task; consent evidence vẫn được giữ cho direct-provider evaluator tương lai.

## Tóm tắt luồng live

```text
[MediaRecorder từng câu]
  → POST multipart speaking-media
  → validate attempt/question/audio
  → temporary storage + DB row
  → promote READY, supersede bản cũ
  → POST submit attempt
  → durable evaluation job
  → provider STT nhận audio
  → provider evaluator nhận transcript (không nhận audio)
  → strict non-holistic JSON feedback
  → normalized result UI
```
