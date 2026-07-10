# Practice Phase 10-16 Execution Blueprint

Korean Study Hub - Feature `/practice`

## 1. Mục đích và phạm vi

Tài liệu này là bản đánh giá và định hướng triển khai chuyên sâu cho `/practice`
từ trạng thái hiện tại đến hết Phase 16. Baseline ban đầu được lập dựa trên:

- code tại HEAD `448bdb1` trên nhánh `feature/practice`;
- toàn bộ `CODEX_PRACTICE_WORKFLOW.md` tại cùng HEAD;
- migration hiện có đến `V24__practice_immutable_versions.sql`;
- các test và contract hiện có của learner flow, lecturer authoring/import,
  Reading/Listening explanation, Writing AI và Speaking AI.

`CODEX_PRACTICE_WORKFLOW.md` vẫn là phase ledger và workflow guard chính. Tài
liệu này không tự thay đổi trạng thái phase, không thay thế bằng chứng test và
không phải quyền tự động bắt đầu phase mới. Khi code, workflow và tài liệu này
mâu thuẫn, dùng thứ tự ưu tiên đã ghi trong workflow.

Cập nhật sau triển khai ngày 2026-07-10:

- `PRE_PHASE_10_SECURITY_AND_GRAPH_GATE` đã hoàn tất;
- Phase 10A-10H đã triển khai và đóng với accepted debt;
- migration hiện tại là `V25__assessment_program_configuration.sql`;
- focused stabilization gate đạt 41/41;
- full suite cuối đạt 1208/1208, không failure/error/skip;
- phụ lục baseline tại HEAD `448bdb1` được giữ như bằng chứng lịch sử; phần
  current-state delta ở cuối tài liệu là nguồn định hướng cho Phase 11.

## 2. Kết luận điều hành

Hướng đi tổng thể là hợp lý: Phase 9 đã tạo nền immutable version cần thiết;
Phase 10 đặt program/question/scoring/prompt policy trước khi mở rộng lecturer UI;
Phase 11-15 đi từ authoring, governance, learner UX, content review đến release
hardening; Phase 16 là tùy chọn.

Các khoảng trống từng chặn Phase 10 đã được xử lý như sau:

1. Learner answer, stable content identity, scoring result, answer spec,
   stimulus và explanation context đã có typed contract.
2. Phase 11 được ghi `NOT_STARTED`, nhưng repo đã có editor, PDF import, draft,
   publisher và revision flow khá lớn. Phase 11 phải được hiểu là
   certification-aware authoring/import hardening, không phải xây mới từ đầu.
3. Phase 13 được mô tả chủ yếu là visual polish, trong khi chưa phase nào sở hữu
   rõ learner player/result behavior cho `MULTIPLE_CHOICE`, `FILL_BLANK` và
   `MATCHING`. Đây là chức năng mới, không phải chỉ polish.
4. Draft/import/asset ownership và publisher test graph đã được sửa; các
   regression test khóa boundary này trước Phase 11.

Khuyến nghị thứ tự thực hiện:

1. Commit/push Phase 10 cùng closure evidence.
2. Chạy Phase 11 audit-only để lập contract-to-editor/import gap map.
3. Triển khai Phase 11-15 tuần tự; Phase 16 chỉ bắt đầu sau GO riêng.

## 3. Product north star

KSH `/practice` cần trở thành một assessment platform cho người học tiếng Hàn,
không bị khóa cứng vào TOPIK hoặc MCQ.

MVP hiện tại và tương lai gần phải hỗ trợ:

- TOPIK Reading/Listening: hiện giữ `SINGLE_CHOICE` làm mặc định;
- TOPIK Writing: Q51/Q52/Q53/Q54 với rubric và điểm task-native hiện có;
- TOPIK/KSH Speaking: text/audio evaluation, nhưng live provider vẫn NO-GO cho
  đến khi Phase 15 chấp thuận;
- chương trình/chứng chỉ khác hoặc bài giáo viên tự tạo: cho phép policy riêng
  theo program, skill và question type;
- Reading/Listening objective questions: giáo viên cung cấp answer spec trước
  khi publish, backend chấm deterministic, AI chỉ giải thích dựa trên nội dung
  và đáp án đã khóa;
- Writing/Speaking: backend chọn prompt/rubric/scoring profile đã được duyệt,
  version hóa và audit được;
- attempt cũ luôn đọc được bằng compatibility path; không sửa ngược published
  content để làm cho code mới dễ hơn.

Các invariant bắt buộc:

- không gửi answer spec/correct answer xuống player trước khi nộp;
- không dùng AI để quyết định đáp án objective nếu giáo viên đã cung cấp key;
- không để AI thay đổi correct answer;
- không mutate published version;
- không dùng prompt text do lecturer nhập làm system prompt trực tiếp;
- không claim điểm chính thức tương đương TOPIK/KIIP/KLAT khi chưa có bằng chứng;
- không gọi provider trong audit/test mặc định;
- mọi policy ảnh hưởng điểm phải có code/version và được snapshot vào attempt;
- mọi giải thích phải bám vào immutable question/stimulus version;
- lỗi provider không được biến thành điểm giả hoặc explanation giả có vẻ chắc chắn.

## 4. Đánh giá trạng thái Phase 1-16

| Phase | Trạng thái ledger | Đánh giá thực tế | Mức tin cậy | Việc còn lại chính |
|---|---|---|---|---|
| 1 | `CLOSED_VERIFIED` | Legacy R/L explanation path đã được dọn và source được gom lại | Trung bình | Ledger cũ thiếu commit/test evidence chi tiết |
| 2 | `CLOSED_VERIFIED` | Có privacy-safe logging và test chống leak ở nhiều AI/media path | Khá | Cần release audit toàn bộ error response/log ở Phase 15 |
| 3 | `CLOSED_VERIFIED` | Writing/Speaking provider call đã tách khỏi DB transaction bằng snapshot/read-call-write flow | Cao | Tiếp tục giữ provider ngoài transaction ở các phase sau |
| 4 | `CLOSED_VERIFIED` | Writing per-question re-evaluate đã có và giữ feedback câu khác | Cao | Chỉ mở rộng khi profile/version identity được chuẩn hóa |
| 5 | `CLOSED_VERIFIED` | Typed Writing/Speaking result/compatibility reader và typed objective scoring result đã có | Cao | Phase 13 nối typed result vào toàn bộ learner UI |
| 6 | `CLOSED_VERIFIED` | `WritingTaskType`, Q51-Q54 policy và profile identity compatibility đã có | Cao | Phase 12 mở governance/version administration |
| 7 | `CLOSED_VERIFIED` với operational debt | Writing cache có TTL 30 phút; R/L cache có versioned key | Khá | Cleanup topology, retention policy và collision fail-safe cần release hardening |
| 8 | `CLOSED_WITH_ACCEPTED_DEBT` | Speaking media/AI, Writing scoring, readiness checks, functional flow và boundary tests đã triển khai | Cao | Live Speaking AI vẫn NO-GO; debt phải trả ở Phase 12/13/15 |
| 9 | `CLOSED_WITH_ACCEPTED_DEBT` | Immutable set/test/section/group/question versions và attempt lock đã có | Cao | Legacy reconstruction tiếp tục là best-effort compatibility |
| 10 | `CLOSED_WITH_ACCEPTED_DEBT` | Canonical contracts, deterministic scoring, V25 policy persistence, immutable policy snapshot, typed R/L explanation và stabilization đã triển khai | Cao | Debt được route rõ sang Phase 11/12/13/15 |
| 11 | Ledger ghi `NOT_STARTED` | Authoring/import foundation thực tế đã tồn tại | Cao | Reclassify thành hardening + mở rộng program/question types |
| 12 | `NOT_STARTED` | RBAC schema và asset foundation có sẵn, practice governance chưa nối đầy đủ | Cao | Ownership, permission, material security, prompt governance |
| 13 | `NOT_STARTED` | Có learner player/result/progress UI cho loại cũ | Cao | New-type delivery, full-test mode, catalog scale, accessibility, polish |
| 14 | `NOT_STARTED` | Chưa thấy content error-report/review workflow | Cao | Xây workflow gắn immutable content và correction version |
| 15 | `NOT_STARTED` | Có test automation đáng kể nhưng chưa có release/UAT gate hoàn chỉnh | Cao | Manual UAT, load, security, migration rehearsal, provider/calibration GO |
| 16 | `OPTIONAL / NOT_STARTED` | Chỉ có permission seed `ai.chatbot`, chưa có practice chatbot | Cao | Chỉ làm sau decision gate và retrieval/ACL design |

### 4.1 Phase 1-7

Các phase này có dấu vết commit tương ứng và code hiện tại cho thấy mục tiêu chính
đã được hấp thụ vào hệ thống. Tuy nhiên status `CLOSED_VERIFIED based on prior
roadmap context` yếu hơn ledger của Phase 8-9 vì thiếu commit/test/debt mapping
ngay tại section của từng phase.

Không nên reopen toàn bộ Phase 1-7. Thay vào đó, trước release Phase 15 cần lập
một retrospective evidence index ngắn, nối mỗi phase với commit, test và debt
đang còn sống. Đây là documentation debt, không phải lý do chặn Phase 10.

Phase 7 có hai điểm cần giữ trong backlog vận hành:

- Writing cache dọn expired rows trong write path; cần xác nhận chi phí khi dữ
  liệu lớn và có thể chuyển sang bounded cleanup job.
- fallback hash trong `WritingEvaluationCacheService` không nên âm thầm dùng
  `String.hashCode()` nếu SHA-256 không khả dụng; production nên fail closed hoặc
  bypass cache.

### 4.2 Phase 8

Phase 8 đóng với accepted debt là hợp lý vì feature gates mặc định tắt, test
không gọi provider thật và workflow không claim production readiness.

Debt hợp lý để hoãn:

- object storage cho Speaking;
- multi-node cleanup/claiming;
- persisted consent;
- teacher-reviewed calibration fixtures;
- manual browser/device UAT;
- user-facing retry/background re-evaluation nếu MVP chưa cần;
- pronunciation/acoustic evidence nâng cao phụ thuộc provider.

Debt không được phép biến mất khỏi gate:

- live Speaking AI vẫn NO-GO;
- transcript-only không được claim pronunciation/batchim/intonation;
- provider, prompt, API key, storage key/path không được render/log;
- reviewer playback role policy phải được quyết định trước release;
- calibration và object storage phải có quyết định cụ thể ở Phase 15.

Architecture debt còn tồn tại rõ: `PracticeService` vẫn khoảng 3.400 dòng. Phase
8H đã tạo boundary nhỏ nhưng chưa decomposition toàn bộ. Cách trả nợ hợp lý là
extract theo feature khi Phase 10-13 đi qua, không làm big-bang refactor:

- Phase 10 tạo scoring/explanation policy service riêng;
- Phase 11 tách authoring/publish validation;
- Phase 13 tách result/progress assembler;
- Phase 15 chỉ cleanup phần còn lại nếu có risk/release value.

### 4.3 Phase 9

Normalized immutable graph và append-only publish là quyết định đúng. Việc giữ
`options_json` trong question version là chấp nhận được cho MVP nếu Phase 10 bổ
sung schema/version rõ ràng và stable option IDs.

Debt Phase 9 hợp lý:

- attempt trước V24 chỉ có thể best-effort nếu source từng bị mutate;
- ambiguous legacy null-group giữ live fallback thay vì khóa sai version;
- lecturer import và version-selection UX thuộc phase sau.

Debt Phase 9 phải được xử lý trong Phase 10:

- `practice_question_versions` chưa snapshot canonical question type, answer
  spec, scoring policy/profile và prompt/rubric profile;
- attempt lock hiện là một section; full-test attempt chưa có aggregate model;
- explanation snapshot hiện là một field text chung, chưa phải artifact có
  model/prompt/schema identity.

## 5. Vấn đề hiện tại cần phân loại lại

### 5.1 P0 - Draft ownership boundary

`PracticeDraftService.getDraft`, `saveDraftState` và `deleteDraft` nhận
`ownerId` nhưng không so sánh với `draft.ownerId`. `PracticePublisherService`
cũng load draft theo ID mà chưa kiểm tra owner. Class-level role check chỉ đảm
bảo người gọi là Lecturer/Head/Admin, không ngăn Lecturer A đọc/sửa/publish draft
của Lecturer B.

Phân loại: `CODE_DEFECT`, không phải accepted debt.

Gate đề xuất:

- audit toàn bộ draft/import/revision ownership path;
- repository/service query phải scope theo owner hoặc dùng explicit policy cho
  Head/Admin override;
- test IDOR cho read, autosave, delete, publish, restore và asset access;
- không bắt đầu mở rộng authoring UI trước khi gate này GO.

### 5.2 P1 - Authoring graph không khớp learner graph

Learner flow yêu cầu `PracticeSet -> PracticeTest -> PracticeSection` và
`startAttempt(setId, testId, sectionId)`. Publisher hiện tạo set, section, group,
question nhưng không tạo `PracticeTest` và không set `section.testId`. Draft JSON
cũng chỉ model `sections`, chưa model `tests`.

Phân loại: `CODE_DEFECT` hoặc `PHASE_DEPENDENCY` cần audit xác nhận bằng
authoring-to-learner integration test.

Gate đề xuất:

- publish một manual draft thực tế;
- assert set detail có test;
- assert section thuộc test;
- assert immutable version có test/section/question graph;
- assert học viên start, submit và xem result được;
- Phase 11 không được đóng nếu thiếu end-to-end này.

### 5.3 P1 - Question type chỉ có tên, chưa có semantics

Hiện trạng:

- entity có `TRUE_FALSE_NOT_GIVEN`, `MATCHING_INFORMATION`, `FILL_BLANK`,
  `ORDERING`, `TEXT_COMPLETION`;
- `isAutoScoredByKey` chấm tất cả bằng cùng một string equality;
- publisher map cả `SINGLE_CHOICE` và `MULTIPLE_CHOICE` về `MCQ`;
- player chỉ render radio riêng cho `MCQ` và TFNG, các loại khác dùng text input;
- answer persistence hiện là `Map<questionId, String>`.

Đây là lý do Phase 10 không thể chỉ thêm enum/column. Cần contract typed từ
authoring đến player answer, scoring, result và explanation.

### 5.4 P1 - AI explanation lifecycle sau Phase 9 chưa hoàn chỉnh

Compatibility attempt không có version lock có thể gọi
`ReadingListeningExplanationService`. Attempt có version lock lại đọc thẳng
`PracticeQuestionVersion.explanation` và test hiện còn khẳng định không gọi
explanation service.

Điều này bảo vệ tính bất biến nhưng có thể làm mất mục tiêu "AI giải thích sau
khi nộp" nếu explanation chưa được tạo trước lúc publish.

Quyết định cần khóa ở Phase 10G:

- teacher explanation snapshot là source riêng;
- AI explanation là versioned artifact riêng, không mutate question version;
- artifact key phải gồm `questionVersionId`, stimulus hash, answer spec hash,
  program/skill/type, model, prompt profile version, schema và language;
- có thể generate lazy sau submit hoặc precompute sau publish;
- result chỉ gọi/generate khi attempt đã submitted/graded;
- learner-specific correctness overlay nên deterministic và không làm mất khả
  năng share explanation artifact;
- không dùng live question content cho version-locked attempt.

### 5.5 P1 - Listening evidence persistence còn thiếu

Phase 10 đã định nghĩa typed `AssessmentStimulus`, provenance và rule fail-safe:
provider explanation không chạy khi evidence không usable, và evidence quote
không thuộc approved evidence sẽ bị reject. Reading hiện dùng nội dung group đã
được duyệt làm evidence compatibility.

Phần còn lại thuộc Phase 11F:

- persisted `READING_PASSAGE`/`LISTENING_AUDIO` binding rõ ràng;
- transcript text/timestamps nếu có;
- source/provenance: teacher, import, transcription provider;
- approval/confidence status;
- version-safe material reference không lộ storage key.

### 5.6 P2 - Roadmap state/documentation inconsistency

Historical finding: workflow từng ghi docs-only Phase 10 là next action và có
wording Phase 9G đã stale.

Resolution: `RESOLVED_IN_PHASE_10_CLOSURE_DOCS`. Workflow hiện ghi Phase 10 đã
đóng với accepted debt và next action là Phase 11 audit-only sau commit/user GO.

## 6. Kiến trúc domain đích

### 6.1 Assessment program

Không hard-code logic theo tên chứng chỉ trong `PracticeService`. Dùng program
identity và versioned policy:

- `assessment_programs`: identity ổn định như `TOPIK`, `CUSTOM`;
- `assessment_program_versions`: immutable version của tên, status, locale và
  default delivery policy;
- `assessment_program_skill_policies`: skill được bật, timer, full-test hoặc
  skill-specific mode;
- `assessment_question_type_policies`: program + skill + canonical type +
  default scoring profile;
- `assessment_scoring_profiles` và immutable versions;
- `assessment_prompt_profiles` và immutable versions;
- `assessment_rubric_profiles` và immutable versions nếu rubric cần lifecycle
  độc lập.

Chỉ seed policy TOPIK đã được KSH xác nhận và `CUSTOM`. Không tự seed scoring
rule cho KIIP/KLAT hoặc chứng chỉ khác dựa trên giả định. Có thể tạo identity
disabled, nhưng chỉ enable khi có spec đã kiểm chứng hoặc policy do Head/Admin
phê duyệt.

### 6.2 Canonical question type

MVP canonical types giữ như workflow:

- `SINGLE_CHOICE`
- `MULTIPLE_CHOICE`
- `TRUE_FALSE_NOT_GIVEN`
- `FILL_BLANK`
- `MATCHING`
- `ESSAY`
- `SPEAKING`

Alias resolver:

- `MCQ -> SINGLE_CHOICE`
- `MATCHING_INFORMATION -> MATCHING`
- `GAP_FILL -> FILL_BLANK`

`ORDERING`, `TEXT_COMPLETION` và `SHORT_TEXT` cần explicit compatibility
decision. Không được âm thầm map sang type khác nếu semantics khác. Legacy row
vẫn đọc được, nhưng new publish phải reject type chưa được program policy enable.

### 6.3 Stable content identity

`options_json` dạng list string không đủ cho multiple choice/matching. Schema
mới cần stable IDs:

```json
{
  "schemaVersion": "question-content-v1",
  "options": [
    {"id": "opt_1", "text": "..."},
    {"id": "opt_2", "text": "..."}
  ]
}
```

Legacy list có thể được adapter thành `opt_1`, `opt_2` theo snapshot order. Sau
khi publish, ID không được tái sử dụng cho nội dung khác trong cùng version.

### 6.4 Answer spec

Answer spec là teacher-owned, server-only và versioned. Ví dụ:

```json
{
  "schemaVersion": "answer-spec-v1",
  "questionType": "MULTIPLE_CHOICE",
  "correctOptionIds": ["opt_1", "opt_3"],
  "scoringPolicyCode": "ALL_OR_NOTHING"
}
```

Contract tối thiểu theo type:

| Type | Answer spec |
|---|---|
| SINGLE_CHOICE | đúng một `correctOptionId` tồn tại |
| MULTIPLE_CHOICE | tập `correctOptionIds` không rỗng, unique |
| TRUE_FALSE_NOT_GIVEN | một value thuộc `TRUE`, `FALSE`, `NOT_GIVEN` |
| FILL_BLANK | stable blank IDs, accepted values và aliases theo từng blank |
| MATCHING | stable left/right IDs và correct pair map |
| ESSAY | rubric/profile refs, không có objective key |
| SPEAKING | prompt/rubric/scoring refs, media/text mode policy |

Publish validation phải fail closed nếu answer spec không khớp content/type.

### 6.5 Learner answer contract

Không tiếp tục giả định mọi answer là string. Dùng versioned typed payload:

```json
{
  "schemaVersion": "learner-answer-v1",
  "questionId": 123,
  "questionVersionId": 456,
  "questionType": "MATCHING",
  "value": {
    "pairs": [
      {"leftId": "p1", "rightId": "h3"}
    ]
  }
}
```

Adapter phải đọc được `Map<questionId, String>` cũ. Player DTO tuyệt đối không
chứa answer spec hoặc correct IDs.

### 6.6 Scoring engine

Tạo strategy registry riêng, ví dụ `AssessmentScoringEngine`, không thêm switch
lớn vào `PracticeService`.

Scoring result typed:

- question/version identity;
- `earnedPoints` và `possiblePoints`;
- `CORRECT`, `PARTIALLY_CORRECT`, `INCORRECT`, `NOT_ANSWERED`, `PENDING_AI`,
  `UNSCORABLE`;
- scoring policy code/version;
- deterministic detail rows cho blank/pair/option;
- safe explanation inputs, không chứa provider raw body.

Policy cần khóa chính xác:

- `ALL_OR_NOTHING`: selected set phải bằng correct set;
- `PARTIAL_BY_CORRECT_OPTION_WITH_WRONG_ZERO`: nếu chọn bất kỳ option sai thì
  điểm 0; nếu không chọn sai thì `points * selectedCorrect / totalCorrect`;
- `FILL_BLANK_NORMALIZED_EXACT`: normalize Unicode NFC, trim và collapse
  whitespace; case/punctuation policy phải explicit; không tự xóa dấu hoặc sửa
  chính tả tiếng Hàn;
- `MATCHING_PER_PAIR`: tính từng left ID; rounding chỉ ở aggregate boundary;
- ESSAY/SPEAKING trả score theo profile và có trạng thái unavailable rõ ràng.

### 6.7 Full-test và skill-specific attempts

Current attempt row khóa một section. Để hỗ trợ full test, không nên nhồi nhiều
section vào một `section_version_id`.

Đề xuất:

- thêm `practice_attempt_sessions` làm aggregate của một lần làm test;
- session khóa program/test version, mode, timer và status;
- current `practice_attempts` tiếp tục là child per section;
- skill-specific mode tạo một session + một child;
- full-test mode tạo một session + nhiều child section attempts;
- submit aggregate chỉ hoàn tất khi policy yêu cầu các child hợp lệ;
- result/progress có thể aggregate mà không phá compatibility attempt cũ.

Phase 10 chỉ cần khóa model/policy. Learner flow hoàn chỉnh có thể triển khai ở
Phase 13.

### 6.8 Explanation architecture

Tách ba lớp:

1. `TeacherExplanation`: nội dung giáo viên nhập, snapshot cùng question version.
2. `AiExplanationArtifact`: artifact versioned, gắn question version + stimulus
   version + prompt/model/schema/language identity.
3. `LearnerAnswerOverlay`: deterministic comparison giữa learner answer và
   scoring result; chỉ xuất hiện sau submit.

`ExplanationContext` tối thiểu:

- program code/version;
- skill;
- canonical question type;
- question version ID;
- instruction;
- stimulus/passage/transcript đã được phép dùng;
- stable options/items;
- normalized answer spec;
- scoring policy code/version;
- explanation language;
- teacher explanation nếu policy cho phép;
- learner answer và score detail chỉ ở personalized layer.

Schema explanation phải type-aware. MCQ-style `eliminatedOptions` không đủ cho
fill blank hoặc matching.

### 6.9 Writing/Speaking prompt governance

Phase 10 tạo profile model và runtime resolver. Phase 12 mới mở quyền quản trị:

- Admin quản lý provider-level system rules và activation;
- Head có thể tạo/duyệt academic profile theo program/skill/task;
- Lecturer chỉ chọn approved profile được program policy cho phép;
- mỗi thay đổi tạo immutable profile version, không sửa version đang được attempt
  dùng;
- publish snapshot profile IDs/versions;
- có audit log, rollback-by-reactivation và calibration fixture requirement;
- user content luôn nằm trong data payload, không được nối trực tiếp thành
  system instruction;
- custom profile không được claim official certification equivalence.

## 7. Pre-Phase 10 security and graph gate

Đây là audit gate hẹp, không phải một phase sản phẩm mới.

Status: `COMPLETED_GATE_GO`

Kết quả:

- P0 draft ownership và publisher ownership đã được sửa bằng owner-scoped
  repository/service paths;
- publisher tạo graph test/section hợp lệ cho learner flow;
- linked draft, annotation, asset, usage và route child-parent binding fail
  closed;
- player đọc locked attempt graph và redacts answer/explanation trước submit;
- draft audio/image upload được khóa extension, size và normalized destination;
- end-to-end authoring -> publish -> attempt -> submit -> explanation đã có
  integration evidence;
- accepted debt còn lại được route sang Phase 11/12/13/15.

### Scope

- draft read/autosave/delete/publish ownership;
- import session/annotation/asset ownership;
- revision restore authorization;
- Head/Admin override policy;
- manual authoring publish graph;
- test/section assignment;
- immutable version creation sau publish;
- learner start/submit/result smoke.

### Exit criteria

- không có IDOR giữa hai Lecturer;
- mọi override của Head/Admin explicit và tested;
- publisher không thể tạo published set không có test/section graph hợp lệ;
- có ít nhất một end-to-end test authoring -> publish -> attempt -> result;
- findings được phân loại `CODE_DEFECT`, `DEFERRED` hoặc `ACCEPTED_DEBT` có owner.

## 8. Kế hoạch Phase 10 chuyên sâu

### Phase 10 closure result

Status: `CLOSED_WITH_ACCEPTED_DEBT`

Kế hoạch 10A-10H dưới đây đã được triển khai theo backend/model/policy scope đã
khóa. Không thêm editor/admin UI mới trong Phase 10.

Evidence cuối:

- canonical/contract/scoring/policy/explanation focused suites đều pass;
- focused stabilization: 41 tests, 0 failures, 0 errors, 0 skips;
- full suite: 1208 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS;
- clean-schema rehearsal: V1 -> V25 và application startup thành công trên
  MySQL local;
- không có real provider call trong test.

Stabilization bổ sung ngoài happy path:

- immutable-to-live FK topology không còn chặn safe graph replacement khi
  không có attempt;
- R/L provider output rỗng hoặc evidence quote không thuộc approved stimulus
  bị reject và không cache như success;
- typed cache không ghi answer-spec hash vào legacy correct-answer field;
- import/draft/asset IDOR, upload traversal và raw error leakage được khóa bằng
  regression tests.

### Phase 10A - Canonical vocabulary and compatibility resolver

Mục tiêu:

- tạo canonical enums/value objects cho program, skill, question type, delivery
  mode và scoring policy code;
- tạo resolver aliases cũ;
- loại bỏ mapping `MULTIPLE_CHOICE -> MCQ` trong domain path mới;
- chưa đổi UI, chưa migration nếu chưa cần.

Focused tests:

- alias equivalence;
- unknown type fail closed;
- legacy MCQ score behavior vẫn tương đương SINGLE_CHOICE;
- deferred type không được publish như supported type.

### Phase 10B - Question content, AnswerSpec and LearnerAnswer contracts

Mục tiêu:

- typed DTO/codec/validator;
- stable option/item/blank IDs;
- legacy adapters cho `options_json`, `answer_key`, string answers;
- redaction boundary giữa authoring DTO và learner delivery DTO.

Focused tests:

- round-trip mỗi type;
- malformed/duplicate/missing IDs bị reject;
- answer spec không xuất hiện trong serialized player DTO;
- legacy payload đọc được nhưng new payload không ghi ngược về contract cũ.

### Phase 10C - Deterministic scoring engine

Mục tiêu:

- strategy per canonical type;
- typed score result;
- exact policy formula và rounding rule;
- không provider call.

Focused tests:

- table-driven cases cho từng type;
- empty/partial/wrong/duplicate answers;
- Korean whitespace/Unicode normalization;
- multi-select wrong-zero semantics;
- matching per-pair aggregation;
- points không âm, không vượt max.

### Phase 10D - Program, scoring, rubric and prompt profile persistence

Mục tiêu:

- thêm migration mới sau V24, không sửa V24;
- normalized identity/version tables;
- seed TOPIK + CUSTOM;
- TOPIK Reading/Listening chỉ enable SINGLE_CHOICE trong seed MVP;
- TOPIK Writing/Speaking trỏ tới profile hiện có bằng compatibility adapter;
- KIIP/KLAT chưa enable khi chưa có verified policy.

Focused tests:

- Flyway migration + JPA mapping;
- unique version/code constraints;
- active profile resolver;
- invalid program/skill/type combination fail closed;
- seed idempotence theo Flyway semantics.

### Phase 10E - Live question and immutable snapshot expansion

Mục tiêu:

- thêm canonical type, answer spec, scoring policy/profile và prompt/rubric refs
  vào live question/section/set theo thiết kế đã audit;
- snapshot cùng fields vào version rows;
- content hash bao gồm toàn bộ field ảnh hưởng chấm/giải thích;
- backfill legacy rows bằng compatibility metadata, không suy đoán phức tạp.

Focused tests:

- publish snapshot giữ nguyên policy sau khi live row đổi;
- old attempt vẫn đọc compatibility path;
- content hash đổi khi answer/scoring/profile đổi;
- provider secret/raw prompt body không được snapshot.

### Phase 10F - Practice grading integration

Mục tiêu:

- `PracticeService` delegate objective grading sang scoring engine;
- persist typed learner answers hoặc versioned envelope;
- aggregate score đúng cho partial credit;
- analytics không coi mọi câu chỉ binary đúng/sai;
- giữ Writing/Speaking snapshot-call-write boundary.

Focused tests:

- submit/re-evaluate/result cho từng canonical objective type;
- version-locked grading dùng answer spec snapshot;
- legacy MCQ/string answer vẫn chạy;
- unsupported type không âm thầm được 0 điểm;
- optimistic lock/conflict behavior không đổi.

### Phase 10G - Standardized Reading/Listening explanation

Mục tiêu:

- typed `ExplanationContext` và type-aware output schema;
- `AssessmentStimulus` cho passage/transcript/evidence;
- versioned explanation artifact/cache;
- lazy-after-submit hoặc precompute policy rõ ràng;
- deterministic learner overlay;
- không mutate immutable question version.

Focused tests:

- explanation dùng version snapshot, không live row;
- no explanation generation trước submit;
- cache identity đổi theo question/stimulus/answer/prompt/model/schema/language;
- multiple choice/fill blank/matching output validation;
- listening thiếu transcript/evidence trả limited/unavailable, không fabricate;
- malformed provider output không được cache như success;
- logs không lộ answer/learner data/provider body.

### Phase 10H - Program policy integration gate

Mục tiêu:

- kiểm tra end-to-end backend từ program policy đến publish snapshot, submit,
  scoring và explanation;
- xác nhận Phase 11 authoring có contract ổn định để dùng;
- cập nhật workflow ledger;
- chạy full suite theo workflow sau các focused gates.

Exit criteria:

- đủ canonical resolver, answer spec, scoring policy, immutable snapshot,
  explanation context và tests cho từng type;
- TOPIK behavior hiện tại không regression;
- không UI editor/admin UI mới trong Phase 10;
- full suite pass hoặc phase dừng chờ user approval theo workflow;
- chưa commit/push nếu chưa được user yêu cầu.

## 9. Kế hoạch Phase 11 - Lecturer Authoring and Import

Phase 11 phải bắt đầu bằng audit code hiện hữu, không xây song song một editor
thứ hai.

### 11A - Baseline and graph repair

- chốt draft schema có `tests -> sections -> groups -> questions`;
- migration/adapter cho draft cũ chỉ có sections;
- publisher tạo PracticeTest và gắn section đúng;
- end-to-end authoring-to-learner contract test.

### 11B - Program-aware authoring

- chọn program, mode, skill và allowed question types;
- UI chỉ hiển thị policy đã enable;
- lecturer không được tự tạo system rule;
- custom program dùng approved default profiles.

### 11C - Type-specific editors

- SINGLE_CHOICE: radio correct option;
- MULTIPLE_CHOICE: checkbox correct options + scoring policy;
- TFNG: canonical three-value selector;
- FILL_BLANK: blank IDs, accepted values, aliases và normalization preview;
- MATCHING: left/right item editor + pair mapping;
- ESSAY/SPEAKING: approved rubric/prompt profile selector và task metadata.

### 11D - Validation and preview

- shared backend validator là source of truth;
- frontend validation chỉ hỗ trợ UX;
- preview dùng cùng sanitized delivery DTO với learner player;
- publish fail closed khi thiếu answer/stimulus/policy/profile;
- warning và blocking code có machine-readable IDs.

### 11E - PDF/AI import normalization

- import output map vào canonical draft contract;
- AI không được tự invent answer khi evidence không đủ;
- lecturer phải xác nhận low-confidence answer/type/stimulus;
- TOPIK import giữ SINGLE_CHOICE mặc định;
- program khác dùng selected policy, không hard-code TOPIK prompt;
- import prompt/output schema versioned và privacy-safe.

### 11F - Stimulus and material binding

- passage, instruction, image, audio và transcript là typed references;
- one stimulus có thể phục vụ nhiều questions;
- publish snapshot version-safe refs;
- không lưu private path/storage key trong version row.

### 11G - Editor maintainability

Editor template hiện khoảng 3.000 dòng và còn inline script lớn song song với
các file static. Trước khi thêm nhiều type, cần tách dần state/tree/actions/
validation thành module đang thực sự được load; xóa duplication chỉ sau contract
test. Đây vẫn là Thymeleaf/JS hiện tại, không phải React migration.

### 11H - Phase gate

- manual authoring cho tất cả MVP types;
- PDF import ít nhất cho TOPIK single-choice và một custom objective type;
- republish tạo version mới, old attempt không đổi;
- ownership/permission tests pass;
- no provider calls trong automated tests;
- focused suites pass, sau đó full suite theo phase-gate policy.

## 10. Kế hoạch Phase 12 - Materials, Permissions and Governance

### 12A - Permission model wiring

- nối RBAC permission tables hiện có vào application authorization;
- tách quyền create/edit/delete/publish/submit-for-review/approve;
- role chỉ là default grant, ownership và scope vẫn được kiểm tra ở service;
- test Lecturer, Head, Admin và cross-owner denial.

### 12B - Content lifecycle governance

- `DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED -> ARCHIVED` nếu product chọn
  approval flow;
- Lecturer submit, Head approve/request changes, Admin override có audit;
- direct publish chỉ cho role/policy được phép;
- published mutation luôn tạo version mới.

### 12C - Prompt/rubric/scoring profile administration

- Admin/Head UI/API cho immutable profile versions;
- activation/deactivation, audit log, rollback-by-version;
- fixture validation trước activation;
- lecturer chỉ chọn approved version;
- system rule và provider config không lộ ra learner/client.

### 12D - Material security

- validate MIME bằng content inspection, size, extension allowlist;
- random server-side names, traversal/symlink protection;
- public authoring content và private learner media là hai storage boundary;
- signed/authenticated access khi material không public;
- virus scanning hook nếu production requirement có;
- lifecycle/ref-count/orphan cleanup và legal retention policy.

### 12E - Speaking media policy debt

- quyết định object storage;
- reviewer playback role policy;
- persisted consent nếu cần;
- multi-node cleanup strategy;
- no public learner media URL.

### 12F - Gate

- permission matrix documented và tested;
- không còn draft/import/asset IDOR;
- profile change không làm đổi attempt cũ;
- material security tests pass;
- unresolved object storage decision vẫn là Phase 15 release blocker.

## 11. Kế hoạch Phase 13 - Learner Delivery, Results, Progress and UI/UX

Tên phase nên được hiểu rộng hơn "polish". New-type interaction là feature work
hợp lệ của Phase 13, không phải bug Phase 8G.

### 13A - Typed learner player

- component/fragment theo canonical type;
- keyboard và screen-reader semantics;
- autosave typed learner answer;
- restore/reload không mất state;
- answer spec không xuất hiện trong HTML/JSON trước submit.

### 13B - Result and explanation rendering

- correct/partial/incorrect/not-answered/pending/unscorable states;
- multiple-choice option-level detail;
- fill-blank per-blank aliases/evidence;
- matching per-pair detail;
- teacher explanation và AI artifact được phân biệt rõ;
- limited evidence state cho Listening;
- no official-equivalence wording.

### 13C - Full-test and skill-specific flow

- chọn full test hoặc skill theo program policy;
- aggregate attempt session/timer;
- resume, submit và result navigation xuyên sections;
- section-level failure không corrupt toàn session;
- history/progress group đúng theo attempt session.

### 13D - Catalog scale

- server-side pagination/filter/search;
- program, level, skill, type, teacher/class filters;
- bounded initial load;
- query/index review với realistic data volume;
- không load hàng nghìn set/test card một lần.

### 13E - Progress analytics

- program/skill/question type/scoring policy dimensions;
- partial credit không bị tính như binary sai;
- Writing/Speaking unavailable score không biến thành 0;
- legacy compatibility rows có nhãn/handling rõ.

### 13F - Retry and operational UX

- user-facing Speaking retry chỉ khi policy cho phép;
- explanation unavailable/retry state;
- idempotency và rate limit;
- không gọi provider chỉ vì refresh result page.

### 13G - Visual, encoding and accessibility polish

- UTF-8/mojibake sweep;
- consistent icon system;
- responsive/mobile/browser matrix;
- no overlapping controls/text;
- focus states, labels, error summaries và contrast;
- PREP-like reference chỉ là inspiration, không copy.

### 13H - Gate

- functional E2E cho mỗi canonical type;
- full-test + skill-specific paths;
- large catalog performance target đạt;
- mobile/desktop accessibility smoke pass;
- không có answer leakage.

## 12. Kế hoạch Phase 14 - Report an Error and Content Review

### 14A - Report model

Report phải gắn immutable identity:

- program/set/test/section/question version;
- attempt ID nếu người học report từ result;
- target: prompt, option, answer, explanation, translation, stimulus, audio,
  scoring hoặc UI;
- category, description, reporter, timestamps và safe attachment refs.

### 14B - Workflow

Đề xuất status:

- `OPEN`
- `TRIAGED`
- `IN_REVIEW`
- `CHANGES_REQUESTED`
- `RESOLVED`
- `REJECTED`
- `DUPLICATE`

### 14C - Authorization and privacy

- Student report nội dung mình truy cập được;
- Lecturer xem content mình sở hữu/phụ trách;
- Head/Admin triage theo scope;
- learner answer/audio chỉ hiển thị khi cần và có quyền;
- attachment/log không lộ provider/storage secret.

### 14D - Correction semantics

- không mutate published question version;
- sửa content tạo draft/new published version;
- sửa explanation tạo version artifact mới và đánh dấu artifact cũ superseded;
- old attempt vẫn audit được artifact từng dùng;
- không để AI tự động publish correction.

### 14E - Feedback loop

- notification cho reporter và content owner;
- SLA/age dashboard;
- duplicate grouping;
- metrics theo program/type/source/import;
- high-severity wrong-answer report có thể tạm block new attempts theo policy,
  nhưng không xóa lịch sử.

### 14F - Gate

- report-to-correction-to-new-version E2E;
- permission/privacy tests;
- immutable history preserved;
- audit log đầy đủ.

## 13. Kế hoạch Phase 15 - Manual UAT and Release Hardening

Phase 15 là nơi trả debt vận hành và quyết định GO/NO-GO, không phải nơi vá dồn
mọi feature còn thiếu.

### 15A - Automated release gate

- full test suite;
- migration tests từ representative pre-V24 và post-V24 database;
- security/ownership regression suite;
- no-provider test profile;
- serialization compatibility tests;
- static answer-leak scan cho player/result contracts.

### 15B - Manual browser/device UAT

- Chrome, Firefox, Safari/Edge theo target support;
- desktop/mobile;
- recorder permission denied/revoked/retry;
- audio seek/range/reload;
- full-test resume/timer;
- all question types và result explanations;
- Korean/Vietnamese IME, Unicode và spacing.

### 15C - Performance and scale

- catalog hàng nghìn tests;
- large attempt history;
- concurrent submit/autosave;
- explanation cache miss burst;
- provider timeout/rate limit;
- cleanup job bounded behavior;
- DB index/query plan review.

### 15D - AI calibration

- teacher-reviewed Writing/Speaking fixtures;
- repeated provider consistency;
- false confidence/fabrication review;
- R/L explanation factuality theo answer spec và stimulus;
- prompt/rubric version approval;
- no official scoring claim.

### 15E - Production storage and privacy

- object storage decision/implementation;
- encryption/access policy;
- retention/deletion/consent;
- backup/restore;
- multi-node cleanup/locking;
- private media incident drill.

### 15F - Operations

- provider outage/rate-limit/cost runbook;
- bad feedback/content report runbook;
- metrics/alerts/dashboards;
- feature flags và rollback;
- deployment profile tách secret/local config;
- Flyway validation/clean policy production-safe.

### 15G - Debt closure review

Mỗi accepted debt Phase 1-9 phải có một trong ba kết quả:

- `PAID` với evidence;
- `DEFERRED_WITH_OWNER_AND_TRIGGER`;
- `RELEASE_BLOCKER`.

Không dùng `accepted debt` như trạng thái vô thời hạn mà không có trigger.

### 15H - Release verdict

Các verdict tách riêng:

- objective Reading/Listening rollout;
- Writing AI rollout;
- Speaking text rollout;
- Speaking audio upload/playback rollout;
- live Speaking transcription/evaluation rollout;
- admin prompt-profile editing rollout.

Một capability GO không tự động làm capability khác GO.

## 14. Kế hoạch Phase 16 - Optional Chatbot AI

Phase 16 chỉ bắt đầu nếu core practice đã release-ready và có product decision
riêng. Permission seed `ai.chatbot` không phải implementation.

### 16A - Product decision gate

- use case ưu tiên: hỏi tiếp sau result/explanation, không phải open-ended bot;
- đối tượng và quota;
- dữ liệu nào được phép dùng;
- success metric và cost ceiling;
- explicit GO/NO-GO.

### 16B - Grounded retrieval and ACL

- chỉ retrieve content/material/version user được phép xem;
- không lộ correct answer trước submit;
- không retrieve learner answer/audio của user khác;
- source citations trỏ immutable content version;
- no cross-class/cross-tenant leakage.

### 16C - Chat contract

- Vietnamese/Korean response policy;
- context gồm program, skill, question, explanation và learner-selected topic;
- refusal/limited-answer state;
- prompt/profile versioned như các AI feature khác;
- history retention policy rõ.

### 16D - Safety and pedagogy

- bot không tự đổi điểm/answer key;
- không claim official certification;
- không thay thế teacher/content review;
- chống prompt injection từ imported material;
- moderation và privacy filters phù hợp dữ liệu học sinh.

### 16E - Evaluation and operations

- groundedness, answer leakage, privacy, language quality fixtures;
- cost/rate/latency limits;
- feedback/report integration;
- provider outage fallback;
- audit/metrics.

### 16F - Optional UI and gate

- chỉ render ở context có ích như result detail;
- không che khuất core explanation;
- accessibility/mobile;
- rollout bằng feature flag và cohort nhỏ;
- GO riêng sau UAT.

## 15. Ma trận trả nợ kỹ thuật

| Debt/Vấn đề | Nguồn | Mức | Phase trả | Release blocker? |
|---|---|---:|---|---|
| Phase 1-7 thiếu ledger evidence chi tiết | 1-7 | P2 | 15G | Không |
| Writing cache cleanup trong write path và hash fallback | 7 | P2 | 15C/15F | Tùy scale |
| Teacher-reviewed Writing calibration | 8C/8F | P1 | 15D | Có cho claim/rollout AI mạnh |
| Object storage Speaking | 8D/8F | P1 | 12E/15E | Có cho live audio AI production |
| Multi-node media cleanup | 8D/8F | P1 | 12E/15E | Có nếu multi-node |
| Persisted consent | 8D | P1/P2 | 12E/15E | Theo legal/product decision |
| Browser/device audio UAT | 8D/8G | P1 | 15B | Có cho audio rollout |
| Pronunciation/acoustic evidence | 8E | P1 | 15D hoặc defer | Có cho strong pronunciation claims |
| Speaking retry/background worker | 8E/8F | P2 | 13F/15F | Không nếu submit-only MVP được chấp nhận |
| Reviewer playback role | 8H | P1 | 12A/12E | Có |
| `PracticeService` quá lớn | 8H | P2 | 10/11/13 incremental | Không tự thân |
| Legacy pre-V24 reconstruction | 9 | Permanent compatibility | Duy trì | Không, nhưng phải hiển thị an toàn |
| Snapshot thiếu answer/scoring/prompt fields | 9/10 | RESOLVED | 10E hoàn tất | Đã đóng |
| Full-test attempt aggregate chưa có | 9/10 | P1 | 10 contract + 13 flow | Có cho full-test mode |
| Draft ownership chưa enforce | Pre-10 baseline | RESOLVED | Pre-10 gate hoàn tất | Đã đóng |
| Publisher không tạo test graph | Pre-10 baseline | RESOLVED | Pre-10 gate hoàn tất | Đã đóng |
| Multiple choice bị collapse về MCQ | Pre-10 baseline | RESOLVED_BACKEND | 10A-10F hoàn tất | Phase 11/13 còn editor/player UI |
| AI explanation version-locked lifecycle thiếu | 9/10 | RESOLVED_BACKEND | 10G hoàn tất | Phase 13 còn typed rendering đầy đủ |
| Listening thiếu explicit persisted transcript/evidence binding | 10/11 | P1 | 11F | Có cho trustworthy listening explanation rollout |
| Practice RBAC schema chưa được wire theo action | Hiện tại | P1 | 12A | Có |
| Public authoring upload validation/lifecycle | Pre-10 baseline | PARTIAL | Path/type/size đã khóa; lifecycle Phase 12D | Có cho production |
| Program-version identity chưa là first-class set-version/attempt lock | 10 | P1 | 13C trước multi-program rollout | Có cho multi-program delivery |
| Type-specific lecturer editor/import normalization | 10/11 | P1 | 11B-11F | Có cho new-type authoring rollout |
| Typed new-type player/result rendering | 10/13 | P1 | 13A-13C | Có cho learner rollout |
| Catalog load toàn bộ published sets | Hiện tại | P1/P2 | 13D | Có khi catalog lớn |
| Mojibake còn trong active source comment/UI sweep | 9G/13 | P2 | 13G/15B | Không, trừ learner-facing corruption |
| Production datasource/Flyway profile hardening | 9G | P1 | 15E/15F | Có |

## 16. Dependency map và phase gates

Luồng phụ thuộc bắt buộc:

```text
Pre-10 ownership/graph audit
  -> Phase 10 domain + scoring + explanation contracts
  -> Phase 11 lecturer authoring/import
  -> Phase 12 permissions/materials/profile governance
  -> Phase 13 learner delivery/results/progress
  -> Phase 14 report/correction workflow
  -> Phase 15 UAT/release hardening
  -> Phase 16 optional chatbot
```

Cho phép overlap có kiểm soát:

- Phase 11 UX wireframe có thể chuẩn bị khi Phase 10 contract gần ổn định, nhưng
  không merge implementation dựa trên contract chưa khóa.
- Phase 12 permission audit có thể chạy sớm vì đã có P0 ownership risk.
- Phase 15 fixture collection có thể bắt đầu từ Phase 10, nhưng release verdict
  vẫn chỉ ở Phase 15.
- Phase 16 discovery không được tiêu thụ implementation budget của Phase 10-15.

## 17. Definition of Done chung cho Phase 10-16

Một phase chỉ được đề nghị đóng khi có:

- scope và out-of-scope được ghi rõ;
- compatibility impact đã audit;
- migration/backfill plan nếu có schema change;
- focused tests cho từng slice;
- security/privacy review tương ứng risk;
- no real provider call trong test trừ khi user phê duyệt riêng;
- phase-gate test/full suite theo workflow;
- accepted debt có owner, target phase và reopen trigger;
- workflow ledger được cập nhật trước commit request;
- user review GO trước commit/push;
- rollout verdict tách khỏi implementation-complete verdict.

## 18. Hành động tiếp theo được khuyến nghị

Sau khi commit/push Phase 10, bước kế tiếp nên là một audit-only slice với tên:

`PRE_PHASE_11_AUTHORING_AND_IMPORT_CONTRACT_GATE`

Audit report cần trả lời đúng sáu câu:

1. Editor hiện serialize/deserialize mỗi canonical question type đến đâu?
2. PDF/AI import đang tạo stable IDs và typed answer spec hay còn ghi legacy key?
3. Passage/transcript/stimulus nào cần persistence và version binding mới?
4. Preview/publish validator có cùng policy resolver với runtime hay chưa?
5. Asset/annotation snapshot restore có giữ đúng source-region linkage không?
6. Phase 11 có thể chia slice nhỏ nào mà không kéo learner UI Phase 13 vào sớm?

Sau audit, sửa P0/P1 authoring/import findings bằng focused slice trước. Chỉ
bắt đầu Phase 11A sau user GO; không kéo Admin/Head governance Phase 12 hoặc
learner rendering Phase 13 vào Phase 11.

## Phụ lục A - Historical baseline evidence map tại HEAD 448bdb1

Các pointer dưới đây ghi lại bằng chứng chính dùng cho đánh giá. Line number có
thể dịch chuyển sau khi code thay đổi; luôn đọc lại source tại HEAD mới.

| Nhận định | Evidence hiện tại |
|---|---|
| Question types đang là string constants và còn type legacy/deferred | `src/main/java/com/ksh/entities/PracticeQuestion.java:19-27` |
| Live question chỉ có `options_json`, `answer_key`, `explanation` | `src/main/java/com/ksh/entities/PracticeQuestion.java:39-55` |
| Immutable question version chưa có answer spec/scoring/profile fields | `src/main/java/com/ksh/entities/PracticeQuestionVersion.java:34-60` |
| Auto-scored types đang dùng chung key-based path | `src/main/java/com/ksh/features/practice/service/PracticeService.java:1538-1548` |
| Key scoring hiện chỉ normalize rồi string equality | `src/main/java/com/ksh/features/practice/service/PracticeService.java:1657-1674` |
| Publisher collapse SINGLE/MULTIPLE choice về MCQ | `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java:481-488` |
| Player chỉ có specialized MCQ/TFNG; type khác rơi vào text input | `src/main/resources/templates/practice/player.html:138-171` |
| Draft read không enforce owner | `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java:49-55` |
| Draft save/delete không enforce owner | `src/main/java/com/ksh/features/practice/manage/service/PracticeDraftService.java:103-150`, `:266-271` |
| Publisher load draft theo ID, không check owner | `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java:80-98` |
| Publisher tạo section/group/question nhưng không tạo PracticeTest | `src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java:182-303` |
| Learner start attempt bắt buộc testId/sectionId graph | `src/main/java/com/ksh/features/practice/service/PracticeService.java:1818-1908` |
| Version-locked R/L result đọc explanation snapshot và không gọi AI service | `src/main/java/com/ksh/features/practice/service/PracticeService.java:2141-2167`, `:2266-2299` |
| Test khóa hành vi không gọi AI trên versioned result | `src/test/java/com/ksh/features/practice/service/PracticeServiceTest.java:206-276` |
| R/L prompt hiện thiên về option elimination schema | `src/main/java/com/ksh/features/practice/ai/readinglistening/ReadingListeningExplanationClient.java:208-274` |
| Attempt version lock hiện khóa một section version | `src/main/resources/db/migration/V24__practice_immutable_versions.sql:112-123` |
| Question version schema V24 chỉ snapshot contract cũ | `src/main/resources/db/migration/V24__practice_immutable_versions.sql:89-110` |
| Practice management authorization hiện role-based Lecturer-or-above | `src/main/java/com/ksh/features/practice/manage/controller/PracticeDraftController.java:22-25` |
| RBAC DB đã có granular permissions nhưng practice chưa wire theo action | `src/main/resources/db/migration/V4__rbac_enhancement.sql:163-190`, `:224-268` |
| Public upload path được permit toàn cục | `src/main/java/com/ksh/config/SecurityConfig.java:133-143` |
| Published catalog hiện load toàn bộ rows | `src/main/java/com/ksh/features/practice/service/PracticeService.java:189-194` |
| Editor đang là template/JS surface rất lớn cần decomposition có kiểm soát | `src/main/resources/templates/practice/manage/editor.html` và `src/main/resources/static/js/practice/manage-editor-*.js` |

## Phụ lục B - Phase 10 current-state delta

| Baseline finding | Trạng thái sau Phase 10 | Evidence chính |
|---|---|---|
| String-only question type và MCQ collapse | Backend canonicalized, legacy alias giữ compatibility | `assessment/CanonicalQuestionType`, `AssessmentContractCodec`, scoring tests |
| Live/version row thiếu policy fields | Đã snapshot canonical type, answer spec, scoring và profile identity | `PracticeQuestion`, `PracticeQuestionVersion`, V25, publisher/revision tests |
| Objective scoring dùng string equality | Đã delegate sang deterministic typed scoring engine | `AssessmentScoringEngine`, strategy tests, integration flow |
| Draft/publisher ownership thiếu | Đã owner-scope và có boundary regression | draft/publisher/ownership tests |
| Publisher thiếu PracticeTest graph | Đã tạo graph learner-compatible | publisher và Phase10 assessment flow integration test |
| Player có nguy cơ đọc live graph/lộ key | Đã đọc locked graph và redact key/explanation | `PracticeService#getPlayerQuestionGroupsForAttempt`, player integration test |
| R/L explanation ad-hoc | Đã có typed context/cache identity/evidence validation | typed client/service contract tests |
| Import child IDOR và asset route mismatch | Đã bind session/annotation/region/asset/owner | import/region/asset ownership tests |
| Draft upload dùng extension thô | Đã allowlist, size-bound và normalize destination | `PracticeDraftControllerUploadSecurityTest` |
| Phase gate chưa có full evidence | Đã pass focused 41/41 và full 1208/1208 | Maven/JDK 26/MySQL V25 closure run |
