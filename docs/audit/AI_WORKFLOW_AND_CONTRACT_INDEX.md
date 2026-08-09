# KSH AI workflow and response-contract index

Trang này trả lời nhanh “nút nào thật sự gọi AI, qua control plane nào và AI phải trả gì”. Schema đầy đủ, line code và bước persist/UI nằm trong walkthrough được link ở từng hàng.

## 1. Toàn bộ AI workflow đang có trong product

| # | User/system trigger | FE → BE entry | Binding/provider | Output provider bắt buộc | Persist/UI sau AI |
|---:|---|---|---|---|---|
| 1 | Lecturer tạo câu hỏi Test từ PDF/DOCX/text | `POST /lecturer/tests/{testId}/ai-questions/generate` → `AiQuestionGenerationController.generate` | Global AI + prompt `AI_QUESTION_GENERATOR` | JSON object `questions[]`; mỗi item đúng type, content, explanation, 2–6 options và correct flags hợp lệ | Lưu preview session DB 10 phút; chỉ `POST .../confirm` mới tạo `TestQuestion` |
| 2 | User tạo thẻ trong deck từ PDF/DOCX/text | `POST /api/flashcards/{deckId}/ai-generate` → `FlashcardApiController.generateCards` | Global AI + prompt `AI_FLASHCARD_GENERATOR` | `{"cards":[{"front":"한국어","back":"nghĩa"}]}`; parser bỏ blank/quá dài/trùng | Chỉ append editor; user phải bấm save deck mới persist |
| 3 | Lecturer tạo Practice candidate từ PDF/text | `POST /practice/manage/pdf-authoring/candidates` → `PracticePdfImportApiController.createBasicCandidate` | `PRACTICE_PDF_AUTHORING`; optional Admin prompt cùng tên | strict `practice-pdf-authoring-output-v1`: exact operation/digest, groups/questions/typed answerSpec/sourceRefs/warnings | Tạo candidate; lecturer review/ready/apply; AI không publish |
| 4 | Lecturer tạo explanation Reading/Listening | `POST /api/practice/manage/explanations/drafts/{draftId}/questions/{clientId}/generate` → `PracticeExplanationController.generateDraft` | `PRACTICE_RL_EXPLANATION` | strict JSON v4, exact strategy/question type/registry, evidence refs/offsets/digests và strategyBlock discriminator | Lưu editorial revision; lecturer sửa + approve; publish mới đưa vào learner version |
| 5 | Student submit Writing | `POST /practice/attempts/{attemptId}/submit` → durable evaluation job | `PRACTICE_WRITING_EVALUATION` | strict `ksh_writing_unified`: versions, rubricScores, taskCoverage, evidenceLedger, findings, upgradedAnswer | Backend normalize/tự tính score; job complete; result page pending/success/failure |
| 6 | Learner hoặc lecturer prompt audio → Korean transcript | worker gọi `/audio/transcriptions` sau media upload/authoring request | `PRACTICE_SPEAKING_STT` | JSON có `text` không blank; optional confidence/logprobs theo caller | Learner: transcript provenance cho evaluator; lecturer: context phải review/confirm |
| 7 | Speaking learner transcript được đánh giá | durable evaluation worker sau STT score-bearing | `PRACTICE_SPEAKING_EVALUATION` | strict `ksh_speaking_evaluation`; evidence/rubric IDs đúng allowlist; transcript-only bắt `score_available=false`, `overall_score=null` | Lưu normalized language feedback; không dựng acoustic/holistic score |
| 8 | Lecturer nhập text rồi bấm tạo audio Speaking | `POST .../speaking-prompt/tts` → task worker → `/audio/speech` | `PRACTICE_SPEAKING_TTS` | Binary audio không rỗng, MIME allowlisted; không phải JSON | Verify bytes/duration/hash, đăng ký lecturer asset, bind nếu source revision còn current |
| 9 | Direct-audio Speaking evaluation | Admin binding/test/reviewer dark path | `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` | Chưa có output được phép release trong live workflow | Capability probe chủ động fail dark-rollout; production vẫn STT → transcript evaluator |

Walkthrough tương ứng:

- #1: [TESTS_WORKFLOWS.md](workflows/product/TESTS_WORKFLOWS.md) §4.
- #2: [FLASHCARDS_WORKFLOWS.md](workflows/product/FLASHCARDS_WORKFLOWS.md) §4.
- #3: [03_IMPORT_EXCEL_PDF_AI_CANDIDATE.md](workflows/practice/03_IMPORT_EXCEL_PDF_AI_CANDIDATE.md) §5–11.
- #5: [04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md](workflows/practice/04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md) §3–9.
- #6: [PRACTICE_SUBMIT_AND_AI_EVALUATION.md](workflows/PRACTICE_SUBMIT_AND_AI_EVALUATION.md) §2–8.
- #7–10: [05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md](workflows/practice/05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md), [06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md](workflows/practice/06_LEARNER_SPEAKING_MEDIA_STT_EVALUATION_PRIVACY.md).

## 2. Global AI transport contract (#1–#3)

`AiClient` đọc enabled providers theo `displayOrder` và thử fallback. Với mỗi provider, request thực tế là:

```http
POST {normalizedBaseUrl}/chat/completions
Authorization: Bearer {apiKey}
Content-Type: application/json

{
  "model": "<provider.model>",
  "messages": [
    {"role":"system","content":"<system prompt + immutable runtime contract>"},
    {"role":"user","content":"<material/request>"}
  ],
  "max_tokens": 2048,
  "stream": false,
  "response_format": {"type":"json_object"}
}
```

`2048` ở trên là ví dụ kiểu dữ liệu; giá trị thật là integer do consumer truyền (Test theo số câu, Flashcard theo số thẻ). `response_format` chỉ được thêm khi consumer gọi `chatJsonObject`; Test question hiện gọi `chat` nên không có field này. Provider response phải có string:

```json
{"choices":[{"message":{"content":"<JSON text mà consumer parser yêu cầu>"}}]}
```

HTTP/network/provider body lỗi làm client thử provider kế tiếp và ghi `ai_request_logs`. Sau khi lấy `message.content`, **consumer parser** mới quyết định hợp lệ; JSON malformed thường được repair/retry đúng một lần, không tự chuyển sang dữ liệu giả. Chi tiết fallback/log/test tại [02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md](workflows/admin/02_LEGACY_AI_PROVIDERS_PROMPTS_LOGS.md).

## 3. Practice structured transport contract (#4–#6, #8)

Practice không dùng fallback list global. Mỗi request resolve đúng `PracticeAiPurpose`, profile, model, capability, limits, credential và revision. Structured request gọi:

```http
POST {practiceProfile.baseUrl}/chat/completions
Authorization: Bearer {profile.credentialSecret}
Content-Type: application/json

{
  "model":"<binding.model>",
  "messages":[...],
  "temperature":0.0,
  "top_p":1.0,
  "response_format":{
    "type":"json_schema",
    "json_schema":{"name":"<contract name>","strict":true,"schema":{...}}
  },
  "max_tokens":4096
}
```

Một số request thêm image content đã được authority cho phép. Response vẫn phải đưa strict JSON vào `choices[0].message.content`. Sau network call, backend resolve/assert revision lại; Admin đổi/tắt profile/binding giữa request làm output stale bị bỏ. JSON schema pass vẫn chưa đủ: normalizer kiểm evidence IDs, offset/hash, version, rubric/strategy/answer authority.

Control-plane gates/test fixture nằm tại [03_PRACTICE_AI_CONTROL_PLANE.md](workflows/admin/03_PRACTICE_AI_CONTROL_PLANE.md).

## 4. Practice audio contracts (#7, #9)

STT:

```http
POST {baseUrl}/audio/transcriptions
Authorization: Bearer {secret}
Content-Type: multipart/form-data

model=<binding model>
language=ko
response_format=json
file=<verified exact bytes>
```

Response tối thiểu:

```json
{"text":"<nonblank Korean transcript>"}
```

Learner path có thể yêu cầu `include[]=logprobs`; prompt-authoring path có thể nhận numeric `confidence`. Backend không coi transcript là điểm.

TTS:

```http
POST {baseUrl}/audio/speech
Authorization: Bearer {secret}
Content-Type: application/json

{"model":"...","input":"...","voice":"...","response_format":"mp3","speed":1.0}
```

Response là audio bytes, phải không rỗng và qua MIME/container/duration/hash verifier trước khi thành asset.

## 5. Những workflow dễ bị gọi nhầm là AI nhưng không gọi AI ở thao tác đó

- Random đề Question Bank dùng `Collections.shuffle()` trong Java; không prompt/model/provider.
- Submit Reading/Listening chấm answer key bằng Java; explanation AI là pipeline riêng và không quyết định điểm objective.
- KRDICT là external dictionary HTTP/XML, không phải generative AI.
- Lưu/toggle model/provider chỉ đổi control plane; AI call thật chỉ xảy ra khi consumer workflow được user/worker kích hoạt và mọi gate pass.
