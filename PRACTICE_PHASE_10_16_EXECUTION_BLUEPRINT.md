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

Cập nhật tổng hợp đến ngày 2026-07-12:

Update 2026-07-13 reduce-scope: theo yêu cầu user trước commit, các migration
V25-V29 được squash thành final-state `V25__practice_single_scope_final.sql`.
Đây không phải copy-paste tuần tự các migration cũ; V25 chỉ giữ schema/data còn
cần cho single-scope practice runtime và bỏ hẳn program/governance/profile DDL
đã bị loại.
Những ghi chú V26/V27/V28 bên dưới là evidence lịch sử của các checkpoint trước
reduction, không còn là file migration riêng trong final tree của nhánh này.

- `PRE_PHASE_10_SECURITY_AND_GRAPH_GATE` đã hoàn tất;
- Phase 10A-10H đã triển khai và đóng với accepted debt;
- migration nền lịch sử của Phase 10 từng là `V25__assessment_program_configuration.sql`;
  trong branch reduce-scope hiện tại V25 đã được thay bằng
  `V25__practice_single_scope_final.sql`;
- focused stabilization gate đạt 41/41;
- full suite cuối đạt 1208/1208, không failure/error/skip;
- `PRE_PHASE_11_AUTHORING_AND_IMPORT_CONTRACT_GATE` đã hoàn tất audit-only với
  verdict `GO_WITH_REQUIRED_FOUNDATION_FIXES`;
- Phase 11A-11G đã được triển khai ngày 2026-07-11; migration nền Phase 11 là
  `V26__practice_authoring_contract.sql`;
- `PHASE_11_CLOSURE_STABILIZATION_GATE = CLOSED_GREEN`; Phase 11 đã được user
  chấp thuận, commit/push tại `324dad9` và đóng
  `CLOSED_WITH_ACCEPTED_DEBT`: focused closure rerun đạt 20/20, focused
  Excel/media/MATCHING đạt 47/47, clean V1-V26 integration gate xanh, full
  suite pool-bounded đạt 1242/1242 không failure/error/skip và runtime QA
  editor/menu/PDF/Excel/teacher preview đã pass;
- `PRE_PHASE_12_MATERIALS_PERMISSIONS_AND_GOVERNANCE_GATE` đã hoàn tất
  audit-only với verdict `AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES`;
  sau implementation GO riêng, Phase 12A-12E và automated/static portion của
  12F đã triển khai trên forward migration
  `V27__practice_phase12_governance.sql`;
- baseline `PHASE_12_AUTOMATED_STABILIZATION_GATE = CLOSED_GREEN`: focused restore /
  governance 17/17, storage/material 22/22, governance hardening 13/13,
  Practice integration 78/78, fresh MySQL V1-V27/Hibernate validation và final
  full suite 1293/1293 đều xanh, không provider call;
- browser QA không chạy trong baseline checkpoint, nên closure chưa từng đóng;
  post-commit audit sau đó còn xác nhận historical-material authorization và
  program/template activation P0. Trạng thái hiện tại là
  `PHASE_12R_SINGLE_SCOPE_REDUCTION_GATE = OPEN` và
  `PHASE_12_CLOSURE_STABILIZATION_GATE = OPEN`; chưa mở Phase 13, chưa
  merge/product rollout;
- audit 12R đếm 42 bảng trong practice/assessment boundary và chốt mandatory
  target drop 14 bảng. Generic program/certificate governance, duplicate type
  metadata, `MULTIPLE_CHOICE`, `MATCHING` và Admin/Head content override bị loại
  khỏi target; R/L single choice/fill blank/true-false-not-given cùng AI
  explanation, Writing Q51-Q54, Speaking, immutable history/material và
  Lecturer collaboration được giữ;
- phụ lục baseline tại HEAD `448bdb1` được giữ như bằng chứng lịch sử; phần
  current-state delta là nguồn định hướng cho Phase 11/12 và các phase sau.

## 2. Kết luận điều hành

Hướng đi đã được thu hẹp sau khi code/schema Phase 10-12 cho thấy generic
assessment governance vượt nhu cầu dự án. Phase 9 immutable version vẫn là nền
cần giữ; Phase 10 generic program/question/profile layer trở thành removal debt
của 12R. Phase 13-15 chỉ tiếp tục trên một KSH practice scope ngầm định, không
có program/certificate/TOPIK-level selector; Phase 16 là tùy chọn.

Các khoảng trống từng chặn Phase 10 đã được xử lý như sau:

1. Learner answer, stable content identity, scoring result, answer spec,
   stimulus và explanation context đã có typed contract.
2. Phase 11 đã harden editor, PDF import, draft, publisher và revision flow
   hiện hữu theo contract certification-aware; không xây editor song song.
3. Phase 13 được mô tả chủ yếu là visual polish, trong khi chưa phase nào sở hữu
   rõ learner player/result behavior cho `MULTIPLE_CHOICE`, `FILL_BLANK` và
   `MATCHING`. Đây là chức năng mới, không phải chỉ polish.
4. Draft/import/asset ownership và publisher test graph đã được sửa; các
   regression test khóa boundary này trước Phase 11.

Khuyến nghị thứ tự thực hiện:

1. Phase 10 đã commit/push và đóng theo gate đã khai báo.
2. Phase 11 đã đóng xanh, được chấp thuận và push tại `324dad9`.
3. Phase 12 đã triển khai và qua automated/static stabilization. User review
   code/evidence và quyết định lượt browser closure còn defer; chỉ mở Phase 13
   sau một quyết định rõ ràng. Phase 16 vẫn cần GO riêng.

## 3. Product north star

KSH `/practice` là một không gian luyện thi tiếng Hàn với một content model ngầm
định. Giáo viên tạo set/test mà không chọn program, certificate, TOPIK I/TOPIK
II, scenario hoặc policy.

MVP hiện tại và tương lai gần phải hỗ trợ:

- Reading/Listening: `SINGLE_CHOICE`, `FILL_BLANK` và
  `TRUE_FALSE_NOT_GIVEN`, không business-cap số câu;
- Writing: đúng Q51/Q52/Q53/Q54 với rubric và điểm task-native hiện có;
- Speaking: số câu tùy giáo viên, text/audio evaluation; live provider vẫn
  NO-GO cho đến khi Phase 15 chấp thuận;
- Reading/Listening objective questions: giáo viên cung cấp answer key hoặc
  accepted values trước publish, backend chấm deterministic, AI chỉ giải thích
  dựa trên nội dung và đáp án đã khóa;
- Writing/Speaking: backend dùng rule/rubric trong code; không yêu cầu
  DB-managed program/profile governance;
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
| 11 | `CLOSED_WITH_ACCEPTED_DEBT` | 11A-11G hoàn tất; 11H/closure gate `CLOSED_GREEN`; commit/push `324dad9` gồm contract/template, typed editor/preview, all-skill Excel, guided PDF, single V26, focused/full-suite và runtime QA xanh | Cao | Governance/material/learner/release debt được route sang Phase 12/13/15 |
| 12 | `PRACTICE_CODE_GATE_GREEN_BROWSER_QA_SKIPPED` | Baseline security/history/material được giữ; 12R đã bỏ generic program governance, type/profile policy và Admin/Head override vượt scope; Speaking vẫn giữ | Cao cho automated practice gate; browser/product QA chưa được claim green | Commit/push checkpoint 12R; chỉ mở Phase 13 sau GO riêng của user |
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

### Kết quả `PRE_PHASE_11_AUTHORING_AND_IMPORT_CONTRACT_GATE`

Verdict: `GO_WITH_REQUIRED_FOUNDATION_FIXES`.

Phase 10 đã hoàn tất đúng phạm vi assessment foundation. Các finding dưới đây
không reopen Phase 8/10, nhưng 11A phải xử lý P0/P1 trước khi thêm loại chứng
chỉ hay mở rộng UI:

| Finding | Mức | Phân loại/định hướng |
|---|---:|---|
| Editor lưu `passageText`, publisher/revision restore chỉ persist `instruction` | P0 | Contract/data loss; sửa 11A, không chờ Phase 13 UI/UX |
| Editor hiển thị mọi question type cho mọi skill và dùng legacy `GAP_FILL` | P1 | Policy/canonical mismatch; 11A-11C |
| Draft validator chưa dùng cùng program policy resolver với publisher/runtime | P1 | Invalid config fail quá muộn; 11A/11D |
| Draft/PDF category là enum/string tĩnh; PDF assembler mặc định TOPIK II | P1 | Thiếu program/template identity; 11B/11F |
| PDF AI trả legacy options/answer key thay vì typed contract | P1 | Import normalization; 11F |
| `points` là max point/weight nhưng objective attempt lưu raw earned points, Writing/Speaking lưu percentage | P1 | Score-unit contract; khóa ở 11A, không thay Phase 8 rubric formula |
| PDF crop/region/request JSON mạnh nhưng khó cho người dùng phổ thông | P2 | Guided mode trong 11F; polish sâu ở Phase 13 |
| Request JSON/system rules hiện diện trên lecturer surface | P1/P2 | Chuyển advanced/debug permission; Phase 11F/12C |
| Chưa có Excel import bài thi | P1 product gap | Thêm deterministic 11E |
| Revision hiện owner-only, restore mutate live graph, multi-test bị chặn | P1 governance | Phase 12B, không kéo vào 11A trừ metadata boundary |

### Kết quả triển khai Phase 11 ngày 2026-07-11

Status: `CLOSED_WITH_ACCEPTED_DEBT` at commit `324dad9`.

- 11A hoàn tất `practice-draft-v3`, stable client IDs, typed question/answer
  contract, persisted stimulus/provenance round-trip và explicit score fields
  `score_unit`, `earned_points`, `score_percentage` trong khi giữ `score` làm
  compatibility field.
- 11B thêm `assessment_exam_templates`, binding program-version/template trên
  draft, live set và immutable set version; authoring catalog được resolve từ
  database policy thay vì danh sách hard-code ở UI.
- 11C editor hiện có type-specific control cho `SINGLE_CHOICE`,
  `MULTIPLE_CHOICE`, `TRUE_FALSE_NOT_GIVEN`, `FILL_BLANK`, `MATCHING`, `ESSAY`
  và `SPEAKING`; answer spec, scoring policy và approved profile được build theo
  canonical contract.
- 11D backend validator phát machine code theo template/program policy; safe
  draft preview dùng learner delivery DTO và không trả answer spec,
  explanation, profile hoặc listening transcript.
- 11E có workbook `practice-excel-v2`, file mẫu sinh theo template và một
  parser chung cho policy-enabled L/R/W/S. Contract giữ đúng
  `Set > Test > Skill Section > Group > Question`; dòng lỗi vẫn hiện trong
  preview nhưng bị bỏ khi confirm, còn câu hợp lệ được đánh số lại liên tục
  trong lesson/section mà vẫn giữ số nguồn để truy vết.
- Preview Excel tách rõ Test/Section, instruction/passage/transcript/media cấp
  group, số câu nguồn/số câu sau import, media/prompt/note cấp câu, dạng/đáp án
  và Option A-H. Tệp ảnh/audio đi kèm được ghép theo basename an toàn, preview
  bằng `blob:` cục bộ và chỉ upload khi thực sự được dùng; không persist local
  path. `MATCHING` dùng left IDs L1-L8 có text/image, Option A-H ở phía right và
  pair map như `L1=A;L2=B`.
- 11F có guided `FULL_SELECTED_PAGES`, advanced crop mode, synthetic traceable
  page regions, canonical PDF assembler, confidence/review gate và payload
  không lặp raw page text. Raw prompt/request chỉ dành cho Head/Admin.
- 11G chỉ load hai module canonical mới cho authoring contract và safe preview;
  sáu file editor cũ không được load đã bị xóa để tránh hai implementation
  song song. Inline editor lớn vẫn là maintainability debt, không chặn correctness.
- 11H đã chứng minh migration sạch V1-V26 và Hibernate validation. Focused
  authoring gate đạt 31/31; final Excel/media/MATCHING gate đạt 47/47;
  compatibility integration đạt 5/5. Full suite với Hikari
  `maximum-pool-size=2`, `minimum-idle=0` đạt 1242 test, 0 failure, 0 error,
  0 skip, BUILD SUCCESS trên JDK 17 và MySQL V26.
- Closure focused rerun đạt 20/20 cho menu/UI contract và Excel controller
  404/403 JSON boundary; static JS/inline-script parse và diff check đều xanh.
- Runtime QA trên port 8082 xác nhận editor, linked PDF import, Excel action cho
  R1/L1/W1/S1, L1 điều hướng đúng
  `draftId=10&testNo=1&lessonCode=L1`, safe teacher preview có prompt/options
  nhưng không lộ key/explanation, và centered native Excel dialog đều hoạt động.
  Menu ba chấm, rename focus/select, delete confirmation/dismiss và ARIA state
  đều đúng; không có console warning/error. Draft 10 không bị xóa.
- Port 8080/8081/8082 đã được đóng sau QA.
- V26 gộp toàn bộ thay đổi chưa release, không ship V27-V29. Binding
  program-version/template vẫn nullable cho legacy rows; publisher mới bắt buộc
  và persist binding trên live set lẫn immutable version, tránh rewrite dữ liệu
  cũ chỉ để thỏa contract mới.
- Không có real AI provider call trong các gate đã chạy.

### Quyết định points và ảnh hưởng Phase 8

- `question.points` là số điểm tối đa, đồng thời là trọng số tương đối của câu
  trong tổng section/attempt.
- Objective type chấm deterministic trên `earnedPoints / possiblePoints`.
- Writing dùng raw rubric score rồi scale theo configured points; Speaking dùng
  provider percentage rồi scale theo configured points. Công thức nội bộ Phase
  8 vẫn hợp lệ.
- Thay points có thể thay trọng số câu hỏi, nhưng không thay rubric/provider
  score gốc. UI cần ghi rõ `Điểm tối đa / trọng số`.
- Quyết định đã triển khai: `attempt.score` tiếp tục là compatibility field;
  contract chuẩn đọc `earned_points` và `score_percentage`, còn `score_unit`
  nói rõ giá trị gốc của attempt. TOPIK dùng default/allowed value theo template;
  CUSTOM có thể cho sửa trọng số trong policy cho phép.

### Mô hình program và template

Không tạo table/parser riêng cho mỗi chứng chỉ. Dùng phân cấp:

- `programCode`: TOPIK, KIIP, KLAT, CUSTOM...;
- `programVersion`: version policy đang hiệu lực;
- `examTemplateCode`: TOPIK_I, TOPIK_II, KIIP_LEVEL_TEST...;
- template quyết định skill/section, timing, allowed question type, points và
  profile mặc định; policy backend vẫn là source of truth.

### 11A - Contract baseline and foundation fixes

Implementation: `COMPLETED`.

- version hóa canonical draft schema và adapter đọc draft legacy;
- giữ stable client/content IDs qua manual, Excel, PDF và publish;
- persist typed stimulus: instruction, reading passage, listening transcript,
  audio/image references và provenance; publisher/revision dùng cùng contract;
- khóa score-unit/weight contract và compatibility behavior;
- đưa program policy resolver vào draft validator, fail early theo machine code;
- end-to-end regression: manual draft -> publish -> learner -> explanation vẫn
  nhận đúng passage/transcript, answer spec và immutable source identity.

### 11B - Program-aware authoring

Implementation: `COMPLETED`.

- chọn program, program version, exam template, mode và skill từ dữ liệu server;
- UI chỉ hiển thị question type/field/timing/points policy đã enable;
- Speaking không hiển thị objective types nếu template không cho phép; Reading
  và Listening không hiển thị ESSAY/SPEAKING ngoài custom policy;
- lecturer không được tự tạo system rule;
- custom program dùng approved default profiles.

### 11C - Type-specific editors

Implementation: `COMPLETED`.

- SINGLE_CHOICE: radio correct option;
- MULTIPLE_CHOICE: checkbox correct options + scoring policy;
- TFNG: canonical three-value selector;
- FILL_BLANK: blank IDs, accepted values, aliases và normalization preview;
- MATCHING: left/right item editor + pair mapping;
- ESSAY/SPEAKING: approved rubric/prompt profile selector và task metadata.

### 11D - Validation and preview

Implementation: `COMPLETED`.

- shared backend validator là source of truth;
- frontend validation chỉ hỗ trợ UX;
- preview dùng cùng sanitized delivery DTO với learner player;
- publish fail closed khi thiếu answer/stimulus/policy/profile;
- warning và blocking code có machine-readable IDs.

### 11E - Deterministic Excel import

Implementation: `COMPLETED`.

- dùng một workbook contract versioned và một parser pipeline chung;
- sinh file mẫu theo `examTemplateCode`, không duy trì parser riêng cho từng
  chứng chỉ;
- workbook v2 biểu diễn Set/Test/Skill/Group/Question, local section numbering,
  group/question stimuli, answer/explanation trước Option A-H và optional
  text/image/audio refs;
- L/R/W/S đều có thể import khi policy enable; Excel là bulk-entry helper bên
  trong manual authoring, không phải một product area tách rời;
- flow: download template -> upload -> parse -> preview toàn bộ row -> confirm
  valid rows -> compact imported numbering per lesson/section -> lecturer review
  -> publish. Dòng lỗi luôn hiển thị để sửa nhưng tự bị bỏ khi confirm;
- preview có cột riêng cho passage/transcript/media cấp group, media/prompt/note
  cấp question, source/imported question number, answer và từng Option A-H;
- companion media ghép bằng basename an toàn, chỉ upload ref thực sự được dùng
  và normalize sang managed URL; local path không vào request/persistence;
- MATCHING dùng stable left/right IDs, text hoặc image ở cả hai phía và correct
  pair map; không ép matching vào các cột option text thuần;
- program-specific template chỉ giới hạn type/default/required column theo
  policy; Apache POI và pattern preview/validation hiện có được tái sử dụng;
- AI chỉ có thể hỗ trợ map cột lạ trong tương lai, không tự tạo answer key.

### 11F - PDF/AI normalization and stimulus/material binding

Implementation: `COMPLETED`.

- import output map vào canonical draft contract;
- AI không được tự invent answer khi evidence không đủ;
- lecturer phải xác nhận low-confidence answer/type/stimulus;
- TOPIK import giữ SINGLE_CHOICE mặc định;
- program khác dùng selected policy, không hard-code TOPIK prompt;
- import prompt/output schema versioned và privacy-safe.
- passage, instruction, image, audio và transcript là typed references;
- one stimulus có thể phục vụ nhiều questions;
- publish snapshot version-safe refs;
- không lưu private path/storage key trong version row.
- giữ page range và crop để giảm payload, nhưng thêm guided mode: đề xuất trang,
  auto-region, preset theo template và chỉ yêu cầu xác nhận vùng bất định;
- expert mode giữ region taxonomy/note/send-text/send-image; raw request JSON
  chỉ hiện cho authorized debug/Admin/Head, không là lecturer workflow mặc định.

### 11G - Editor maintainability and bounded usability

Implementation: `COMPLETED_WITH_ACCEPTED_DEBT`.

Editor template hiện khoảng 3.000 dòng và còn inline script lớn song song với
các file static. Trước khi thêm nhiều type, cần tách dần state/tree/actions/
validation thành module đang thực sự được load; xóa duplication chỉ sau contract
test. Đây vẫn là Thymeleaf/JS hiện tại, không phải React migration.

11G chỉ xử lý usability gắn trực tiếp với authoring/import correctness. Visual
polish, responsive/accessibility sweep và learner UI vẫn thuộc Phase 13.

### 11H - Phase gate

Implementation: `CLOSED_GREEN`.

- manual authoring cho tất cả MVP types;
- Excel import cho TOPIK và ít nhất một custom objective template;
- PDF import ít nhất cho TOPIK single-choice và một custom objective type;
- passage/transcript round-trip và explanation context không mất dữ liệu;
- score-unit/weight contract có regression test cho objective/Writing/Speaking;
- republish tạo version mới, old attempt không đổi;
- ownership/permission tests pass;
- no provider calls trong automated tests;
- focused Phase 11 gate 31/31 và final Excel/media/MATCHING gate 47/47 pass;
- closure focused rerun 20/20 và full suite pool-bounded pass 1242/1242, không
  failure/error/skip;
- browser runtime pass cho editor, menu/rename/delete-confirm, PDF, Excel
  R/L/W/S, exact L1 route, centered preview contract và sanitized teacher
  preview; console không warning/error;
- `PHASE_11_CLOSURE_STABILIZATION_GATE = CLOSED_GREEN`; top-level Phase 11 đã
  được user chấp thuận, commit/push tại `324dad9` và đóng với routed debt. Việc
  đóng Phase 11 không tự động mở Phase 12.

### 11I - Post-closure PDF/Excel teacher UX correction

Implementation: `IMPLEMENTED_AUTOMATED_GATE_GREEN_BROWSER_UAT_DEFERRED`.

Đây là corrective stabilization có phạm vi hẹp sau phản hồi thực tế, không thay
thế roadmap Phase 13 và không mở Phase 12:

- PDF import dùng `program/version/template` làm context chính; `category` chỉ
  còn compatibility metadata;
- chọn rõ Test + skill section trước import; linked import chỉ append vào lesson
  đã chọn, không ghi đè sibling section; số câu nguồn và số hiển thị tách riêng;
- đổi loại nội dung phải render field ngay, không F5; UI mô tả nội dung sẽ hiện
  thế nào trong bài học sinh;
- Lecturer chỉ thấy preview nghiệp vụ; raw system/model/request giữ cho Head/Admin;
- extracted text có bản dễ đọc và raw; AI output có learner-safe preview;
- Excel mặc định xem theo hierarchy, shared group content hiển thị một lần, lỗi
  có issue list/jump, còn bảng A-H/MATCHING/media giữ làm detail view;
- Ghi chú lịch sử: checkpoint này từng dùng V27 như forward migration sau V26;
  quyết định reduce-scope ngày 2026-07-13 đã supersede bằng squashed V25 trước
  commit nhánh này;
- compile/loaded-template JS, focused và broad Practice/PDF/Excel regression,
  clean Flyway V1-V27 + Hibernate validation đều xanh;
- final pool-bounded full suite: 1244 tests, 0 failure, 0 error, 0 skip,
  `BUILD SUCCESS` trên JDK 26.0.1/MySQL V27;
- browser QA của 11I được user yêu cầu bỏ qua. Không suy diễn runtime pass từ
  automated evidence; lượt browser QA được gom vào stabilization cuối Phase 12,
  còn full Manual UAT vẫn giữ ở Phase 15;
- tại checkpoint 11I, thay đổi chưa stage/commit/push và Phase 12 vẫn
  `NOT_STARTED`; user đã cấp implementation GO riêng sau checkpoint đó.

## 10. Kế hoạch Phase 12 - Materials, Permissions and Governance

Current status:
`SINGLE_SCOPE_REDUCTION_REQUIRED`.

Research boundary ngày 2026-07-11:

- 96 screenshots/14 evidence groups chỉ phản ánh learner account trên nền tảng
  tham khảo; không có teacher/admin evidence và không được dùng để suy diễn RBAC;
- program/certificate switcher từ research chỉ là evidence lịch sử. Product
  direction hiện tại dùng một KSH scope ngầm định và loại bỏ `topik_level` khỏi
  target identity/UI;
- Phase 12 chỉ được bắt đầu sau audit riêng về schema, permission seed,
  ownership, current revision service và material storage boundary của KSH;
  audit này đã hoàn tất và user đã cấp implementation GO riêng.

### Kết quả `PRE_PHASE_12_MATERIALS_PERMISSIONS_AND_GOVERNANCE_GATE`

Verdict: `AUDIT_COMPLETE_GO_WITH_REQUIRED_FOUNDATION_FIXES`.

Audit đã chạy trên committed HEAD `324dad9`, chỉ đọc source/schema/test
contract; không sửa production code/migration, không rerun test và không gọi
provider. Tại thời điểm audit, top-level Phase 12 vẫn `NOT_STARTED` cho tới khi
user duyệt implementation GO. GO đó đã được cấp sau đó; phần gap map dưới đây
được giữ như acceptance criteria lịch sử. Implementation được ghi vào
feature-branch checkpoint này theo chỉ thị stage/commit/push riêng của user.
Commit/push trên `feature/practice` không đồng nghĩa được merge vào `main` hoặc
product/live rollout; hai quyết định này vẫn NO-GO trước gate riêng.
Một audit Codex chạy song song được dùng như bằng chứng bổ trợ, không thay thế
audit này; các claim snapshot fail-open và DB/filesystem asset consistency đã
được đọc lại trong local source trước khi đưa vào gap map.

Foundation hiện có và phải giữ:

- management routes đã chặn Student và các draft/PDF/asset service chính có
  owner binding/cross-owner denial;
- immutable set versions và attempt-version lock từ Phase 9 đã hoạt động;
- V26 giữ program-version/template/profile identity trên authoring và publish;
- raw PDF request/system detail được server redaction cho Lecturer;
- CSRF được bật, temporary PDF session/asset có expiry cleanup.

Gap map bắt buộc:

| Mức | Gap hiện tại | Hệ quả | Slice sở hữu |
|---|---|---|---|
| P0 | V4 có roles/permissions/hierarchy/user overrides/effective view nhưng application không đọc effective permissions; practice controller chỉ dùng broad role check | Permission seed và override đang inert; Head/Admin không có emergency action rõ ràng | 12A |
| P0 | Draft/set chỉ có owner/creator, không có collaboration scope hoặc owner lock | Không thể xây `Của tôi`/`Được chia sẻ` an toàn; cross-owner luôn deny hoặc phải mở quá rộng | 12A/12B |
| P0 | Revision restore xóa/rebuild live graph; republish bị mutation guard chặn khi có bất kỳ attempt/submission | Không thể restore/republish thực tế sau khi học viên đã làm bài dù immutable version đã tồn tại | 12B |
| P0 | `captureSetSnapshot()` bắt exception rồi trả `{}`; publish/edit log có thể thành công với history hỏng. Restore lại reject `{}` và rollback log hiện thiếu complete before snapshot | Audit trail có thể không restore/undo được dù thao tác được báo thành công | 12B, fail-closed snapshot/history |
| P0 | `/uploads/**` public trong khi `lecturer_assets.visibility` chưa được map; owner-only asset content URL lại có thể được persist vào published graph | Draft/private asset có thể lộ, còn learner có thể không đọc được published asset | 12D |
| P0 | Asset usage chỉ track draft; version rows copy URL string; delete/cleanup không tính live + immutable refs | Xóa draft/asset có thể làm hỏng nội dung đã publish hoặc lịch sử version | 12D |
| P1 | Program/template/profile chỉ có runtime resolver, chưa có Admin/Head lifecycle; template config có thể mutate dưới stable code | Policy drift, rollback/audit không đáng tin; old content có thể resolve khác | 12C |
| P1 | DB prompt profile có `system_rules` nhưng entity/runtime không dùng; Writing/Speaking rules còn hard-coded | Admin config chưa thực sự điều khiển evaluation policy | 12C |
| P1 | `ARCHIVED` có trong schema nhưng không có archive/unarchive action | Lifecycle ghi trong docs chưa có operational path | 12B |
| P1 | Direct upload mới kiểm tra path/extension/size; MIME response và active-orphan retention chưa hoàn chỉnh | Content spoofing, sai MIME và orphan storage còn rủi ro | 12D/15E |
| P1/release | Asset promote copy/new-key/delete-old/save-DB và hard delete file-before-row không có durable transaction compensation | DB rollback/commit failure có thể làm DB/filesystem lệch; cleanup chỉ log không bảo đảm retry | 12D |
| P2 | History diff, shared-list scale và material UX chưa có | Không chặn foundation nhưng cần trước UAT rộng | 12B/12D, polish ở 13G |
| P2/release | Cloudflare R2 đã được chọn làm target về nguyên tắc nhưng chưa có API/bucket/domain/credential; virus scan và multi-node cleanup chưa chốt | Chặn live Speaking audio production, không chặn provider-neutral non-audio Phase 12 | 12D/12E/15E |

### Ma trận quyền mục tiêu

| Actor/context | Create | Edit/publish/restore | Archive | Lock/unlock | Emergency override | Config governance |
|---|---|---|---|---|---|---|
| Student | Deny | Deny | Deny | Deny | Deny | Deny |
| Lecturer owner | Own content | Own content khi state hợp lệ | Own content | Own content | Deny | Deny |
| Lecturer collaborator, unlocked | Theo grant | Chỉ shared content và action grant | Deny mặc định; cần grant riêng nếu product duyệt | Deny | Deny | Deny |
| Unrelated Lecturer hoặc collaborator khi owner lock bật | Theo create grant riêng | Deny | Deny | Deny | Deny | Deny |
| Head | Theo effective grant | Theo effective grant; cross-owner chỉ qua explicit override | Theo grant | Owner action hoặc explicit override | Có reason + audit | Theo approved governance grant |
| Admin | Theo effective grant | System-wide nhưng vẫn qua invariant/audit | Theo grant | Owner action hoặc explicit override | Có reason + audit | Full approved governance action |

Role chỉ cấp default grants. Mọi mutation phải đồng thời pass effective action
permission, ownership/collaboration scope, content state và owner lock ở service
boundary. Owner lock được check trước create-draft-from-published, autosave,
manual/PDF/Excel attachment, publish/republish, restore và material mutation.

### Boundary kiến trúc cho Phase 12

Không big-bang refactor, nhưng cũng không tiếp tục nhét các concern mới vào
inline editor, PDF workspace, Excel codec, `PracticePublisherService` hoặc
`PracticeService` vốn đã lớn. Phase 12 nên tách theo trách nhiệm:

- authorization decision boundary cho effective action + scope + lock;
- revision application boundary cho snapshot, restore, append-only publish và
  before/after audit;
- material access boundary cho private authoring versus published delivery;
- asset lifecycle boundary cho references, staged mutation, compensation,
  retry và cleanup;
- assessment governance boundary cho immutable config activation/rollback.

Các tên như `PracticeAuthorizationService`, `PracticeMaterialAccessService`,
`PracticeRevisionApplicationService`, `AssessmentGovernanceService` và
`PracticeAssetLifecycleService` chỉ là tên minh họa trách nhiệm, không phải
quyền tự động tạo class/schema trước khi đọc dependency hiện hữu.

### Compatibility và migration rules

- Ghi chú lịch sử: contract ban đầu yêu cầu forward-only sau V26. Quyết định
  reduce-scope trước commit nhánh này đã thay bằng một squashed V25 để giảm
  migration sprawl;
- không rewrite/delete attempt, submission, published version hoặc policy
  snapshot cũ;
- giữ mutation guard fail-closed cho legacy/unversioned attempt không chứng
  minh được source version; chỉ mở append-only update khi attempt đã lock an
  toàn vào immutable version;
- restore luôn tạo draft/revision/version mới từ version được chọn, không
  rebuild live history tại chỗ;
- owner là stable identity; actor/collaborator/override được audit riêng;
- learner scope `GLOBAL/CLASS` không được tái dùng làm collaboration scope;
- không invent table/column name trước implementation contract review; ưu tiên
  adapter tương thích với schema hiện hữu;
- normal shared edit/publish không cần notification theo quyết định product.

### 12A - Permission model wiring

- định nghĩa canonical practice actions cho create/read/edit/delete/publish,
  archive/unarchive, lock/unlock, restore, material mutation và emergency
  override; không thêm submit-for-review/approve vào normal flow;
- nối effective permission từ V4 vào application/service authorization;
  user override có reason, expiry và deny precedence rõ; deny-by-default khi
  permission key hoặc scope không resolve được;
- role chỉ là default grant. Ownership, authoring collaboration, program scope,
  state và owner lock vẫn là invariant riêng ở service;
- không chỉ bảo vệ controller: autosave, import, publisher, revision và asset
  service đều dùng cùng authorization component;
- regression matrix cho Lecturer/Head/Admin, owner/collaborator/unrelated,
  lock on/off, expired override và forged ID; Student luôn deny authoring;
- deliverable: action catalog, decision table, service guard API, seed/adapter
  plan và focused security test trước khi 12B dùng shared editing.

### 12B - Content lifecycle governance

- dùng lifecycle gọn `DRAFT -> PUBLISHED -> ARCHIVED`; không bắt normal lecturer
  flow đi qua `IN_REVIEW/APPROVED`;
- tạo explicit authoring collaboration policy/relation, tách khỏi learner
  scope; có hai query/view `Của tôi` và `Được chia sẻ`;
- tài liệu shared và unlocked có thể được lecturer đã được grant chỉnh sửa và
  publish trực tiếp; owner identity không đổi theo actor gần nhất;
- owner có thể bật `LOCKED_BY_OWNER`. Khi khóa, lecturer khác không được edit,
  restore, attach import/material hoặc publish version mới; owner có thể mở
  khóa. Head/Admin override phải là action riêng, có reason và audit;
- không triển khai notification edit/publish trong scope 12B;
- archive ngăn attempt mới/catalog publication nhưng không xóa attempt/history;
  unarchive phải tuân theo permission và current policy validation;
- mọi edit/publish/restore tạo immutable revision/version mới, ghi actor, owner,
  thời gian, source version, summary và diff metadata; không mutate old version;
- snapshot/history là publish invariant: serialize/validate thất bại phải fail
  closed và rollback mutation; không dùng `{}` như successful history;
- owner xem toàn bộ lịch sử và chọn bất kỳ version cũ nào để restore. Restore
  khởi tạo draft/revision mới từ snapshot đã chọn rồi publish append-only, không
  xóa/rebuild live history;
- restore audit event có complete before/after snapshot hoặc immutable version
  references đủ để tiếp tục restore/undo bằng cùng contract;
- republish với version-locked attempts tạo version mới và old attempts tiếp
  tục đọc old snapshot; legacy/unversioned path vẫn fail closed;
- test owner lock trên mọi entry point, concurrent optimistic lock, restore bất
  kỳ version, archive/unarchive và old-attempt invariants.

### 12C - Prompt/rubric/scoring profile administration

- Admin/Head API trước, bounded UI sau, cho immutable program/template/prompt/
  rubric/scoring profile versions;
- create version, validate, activate/deactivate atomically, audit actor/reason
  và rollback bằng version/activation mới; không mutate config cũ dưới stable
  code;
- map approved `system_rules`/profile identity vào runtime evaluator qua typed
  resolver; giữ hard-coded rule adapter chỉ làm legacy compatibility;
- fixture validation trước activation gồm skill/type limits, exact/min/max
  options, max tests/questions, timers, scoring scale, prompt/rubric references
  và disabled skill;
- lecturer chỉ chọn approved version;
- system rule, provider config và secret không lộ ra Lecturer/learner/client;
- cùng một resolved policy phải điều khiển manual editor, Excel template, PDF
  normalization và Phase 13 learner delivery; không tạo policy riêng theo UI;
- enabled skills/question types, option/count limits, timers và score-scale
  metadata đều versioned theo program/certificate;
- old draft/set/version/attempt giữ resolved identity, cache key gồm immutable
  config identity; test activation/rollback không làm đổi old attempts.

### 12D - Material security

- dùng một material identity/metadata contract cho manual, PDF và Excel; không
  persist private local path, raw storage key hoặc ungoverned public URL;
- tách private authoring delivery khỏi published learner delivery. Published
  material phải learner-readable theo authorization nhưng draft/private asset
  không được đi qua global `/uploads/**` public boundary;
- live graph và immutable versions giữ durable material references thay vì chỉ
  copy owner-only URL strings; material content response dùng đúng stored MIME;
- track reference từ draft, live graph và immutable version trước delete;
  xóa draft không được làm gãy published content/history;
- validate content-derived MIME/magic bytes, size, extension allowlist, random
  server name, traversal/symlink và archive/companion-bundle safety;
- cleanup phân biệt TEMPORARY/ACTIVE/PUBLISHED/orphan, có grace period,
  bounded batch, retry/audit và retention rule; không xóa object còn ref;
- promote/delete dùng staged file mutation và transaction synchronization,
  after-commit finalize hoặc durable compensation/outbox. Failure ở copy,
  save, commit, delete cũ hay retry đều phải idempotent và quan sát được;
- virus scanning hook/object storage adapter có thể defer theo deployment,
  nhưng authorization, durable refs và private/published boundary là P0;
- storage port phải provider-neutral. Có thể chuẩn bị disabled R2 adapter contract,
  nhưng không thêm SDK call, network call, secret, bucket giả hoặc credential mẫu
  khi API chưa được user cung cấp.

### 12E - Speaking media policy debt

- định nghĩa reviewer playback action qua 12A, không cho role broad access;
- Cloudflare R2 là production target đã chọn về nguyên tắc; ghi rõ local adapter
  cho dev/single-node và R2 adapter vẫn `NOT_STARTED_API_UNAVAILABLE`;
- chốt signed/authenticated playback, persisted consent, retention/legal hold và
  multi-node cleanup trước khi bật media production;
- không public learner media URL, không expose storage key;
- non-audio Phase 12 có thể tiếp tục với debt được ghi rõ; live production
  Speaking audio/AI vẫn NO-GO cho tới khi Phase 15 chấp thuận storage/UAT.

### 12F - Gate

- fresh V1-next migration và representative V26/legacy upgrade rehearsal pass;
- complete action/IDOR matrix pass cho owner/collaborator/locked/override;
- shared edit, restore bất kỳ version và republish với version-locked attempt
  tạo append-only version; old attempts/submissions không đổi;
- snapshot serialization/validation fault làm publish/restore fail closed;
  rollback event luôn có complete reversible evidence;
- archive/unarchive, actor/owner history, override reason và audit event pass;
- config fixture validation, activation/rollback và cache/identity tests pass;
- material draft privacy, published learner access, correct MIME, immutable ref
  retention, delete/cleanup, forged URL/ID và DB/filesystem fault-injection /
  compensation retry tests pass;
- focused slices, full suite và browser UAT hợp nhất cho `Của tôi`/`Được chia
  sẻ`, lock, history/restore, config và material delivery; no provider call
  trong automated gate;
- R2 API/bucket/domain/credential, object migration và multi-node rehearsal chưa
  có vẫn là Phase 15 blocker cho live Speaking audio, không được báo production GO.

#### 12F-Stabilization - Browser/route/dead-code gate trước Phase 13

- chạy một browser QA toàn cảnh `/practice`: library một scope,
  Set > Test > Skill, manual/Excel/PDF authoring, preview/publish,
  collaboration/lock/history/config/material, learner attempt/result và
  cross-role denial. Dùng deterministic test fixtures đủ cho route/state
  coverage; đây là lượt bù cho browser QA đã defer ở 11I;
- stabilization cuối Phase 12 phải inventory controller/route/template/script,
  phát hiện dead code, button chưa nối, redirect/parameter sai, valid-route
  4xx/5xx, content không load, console/network error, UI overlap/vỡ và
  mojibake. Fix theo slice nhỏ và rerun regression/full suite;
- không mở Phase 13 trước khi `PHASE_12_CLOSURE_STABILIZATION_GATE =
  CLOSED_GREEN`, ghi rõ evidence và dừng server tạm;
- gate này không phải full Manual UAT và không kéo quyền drop database/bộ seed
  chất lượng cao từ Phase 15 về Phase 12. Phase 15 vẫn sở hữu cross-browser /
  device, dữ liệu thực tế, scale và release GO/NO-GO.

### Kết quả implementation và stabilization ngày 2026-07-12

| Slice | Kết quả thực tế |
|---|---|
| 12A | Canonical action RBAC, effective permission, explicit collaboration, owner lock, scoped Head/Admin override và immutable audit event đã được nối ở service boundary |
| 12B | `DRAFT/PUBLISHED/ARCHIVED`, archive/unarchive và restore selected immutable version theo append-only draft/new-version flow; old history/attempt binding được giữ |
| 12C | Immutable program/template/scoring/prompt/rubric versions, validation, row-locked activation và rollback-by-activation cho Admin/Head |
| 12D | Governed `/practice/materials/{id}/content`, deny raw private upload paths, signature verification, durable material references và idempotent lifecycle task/retry/cleanup |
| 12E | Provider-neutral storage/readiness boundary; local adapter cho development, Cloudflare R2 vẫn `NOT_STARTED_API_UNAVAILABLE`, không SDK/network/secret/fake credential |
| 12F automated/static | Fresh MySQL V1-V27/Hibernate validation; focused 17/17 + 22/22 + 13/13 + 78/78; final full suite 1293/1293, zero failure/error/skip, `BUILD SUCCESS`, no provider call |

### 12G - Post-commit security, governance and UI closure

Implementation: `PLAN_READY_IMPLEMENTATION_NOT_STARTED`.

Post-commit audit không phủ nhận automated evidence của 12A-12F, nhưng đã mở
lại closure gate vì các gap sau chưa được test/khóa đúng contract:

- P0 historical material authorization: learner không có matching old attempt
  vẫn có thể đọc asset thuộc old published version nếu set hiện tại GLOBAL/CLASS;
- P0 program/template activation integrity: template root giữ
  `program_version_id`, template version không giữ exact compatible program
  version, và activation program version mới có thể làm authoring catalog reject
  các template roots còn trỏ version cũ;
- program/certificate, policy, scenario/template và profile settings đã được
  persist ở DB, nhưng current identity/UI chưa phản ánh contract learner-facing
  certificate -> skill -> task và one-active-scenario;
- emergency override chưa đi xuyên create-edit/autosave/publish/PDF/Excel/
  material path; material response chưa có HTTP Range thật; final suite chưa
  chạy trên project baseline JDK 17;
- manage dashboard/revision backend đã có một phần, nhưng collaborator grant
  management, material repository, per-set history link và assessment
  governance UI chưa hoàn chỉnh. Editor history vẫn là placeholder alert.

12G phải triển khai theo thứ tự security/invariant trước UI:

1. current-version material resolver và complete historical/current access tests;
2. forward-only program-template version relation và validated atomic activation;
3. root/read/version governance API cùng bounded Admin/Head program/scenario/
   profile UI theo one-active-scenario, field nghiệp vụ và không raw JSON;
4. end-to-end emergency override context/action matrix;
5. shared HTTP byte Range implementation cho governed material;
6. collaborator/material/per-set-history UI và xóa placeholder route;
7. focused/full tests dưới JDK 17, fresh/upgrade migration rehearsal và mandatory
   controlled browser closure QA.

Profile `ACTIVE` semantics phải được chốt trước UI. Khuyến nghị coi ACTIVE là
approved và để program/question policy tham chiếu exact profile ID; nếu cần một
current mặc định thì thêm con trỏ riêng, không mutate nghĩa của version cũ.
DB-managed Writing/Speaking rules chưa thay toàn bộ compatibility adapter phải
được hiển thị là runtime-support limitation và chặn rollout profile chưa hỗ trợ;
focused evaluator migration/calibration vẫn thuộc Phase 15.

### 12H - Generic certificate governance redesign

Status: `CANCELLED_SUPERSEDED_BY_12R`.

Không triển khai program/certificate CRUD, one-active-scenario, generic skill/
task routes hoặc DB-managed profile governance. Phần lịch sử ở continuation plan
không còn là target product.

### 12R - Single-scope reduction trước Phase 13

Status: `AUDIT_COMPLETE_IMPLEMENTATION_REQUIRED`.

Contract bắt buộc:

- không program/certificate/category/TOPIK-level selector; mọi set/test dùng một
  KSH content model ngầm định;
- Reading/Listening cho phép `SINGLE_CHOICE`, `FILL_BLANK`,
  `TRUE_FALSE_NOT_GIVEN` và không business-cap số câu;
- Writing có đúng Q51-Q54, mỗi task một lần;
- Speaking là skill mặc định, type `SPEAKING`, không business-cap số câu;
- không program/template/question-type/profile governance tables;
- giữ một `question_type` discriminator với đúng năm giá trị; drop
  `canonical_question_type`, `MULTIPLE_CHOICE` và `MATCHING` sau compatibility
  migration;
- Lecturer owner chia sẻ trực tiếp cho Lecturer collaborator; owner lock vẫn
  giữ; Head/Admin không approval hoặc emergency override content;
- giữ immutable history/restore, governed materials, deterministic scoring,
  R/L AI explanation, Writing evaluator và Speaking media/evaluator flow.

Migration inventory có 42 bảng trong practice/assessment boundary. Mandatory
target drop 14 bảng để còn tối đa 28; defer PDF-AI có thể drop thêm sáu bảng
nhưng cần quyết định riêng. V25-V29 đã được squash trước commit; không dùng
forward migration rời. Preflight vẫn nằm trong squashed V25 và drop/reseed
development DB đã được user cho phép.

Execution order:

1. freeze/deny generic governance, certificate selector và unsupported type;
2. thay DB policy runtime bằng code-owned `PracticeContentRules` không có UI
   selector;
3. migrate/drop table, column, FK và legacy duplicate;
4. xóa dead code/entities/repositories/routes/templates/tests;
5. giản lược owner/collaborator authorization;
6. regression ba R/L objective type + AI explanation, Writing Q51-Q54 và
   Speaking;
7. fresh/upgrade migration, JDK 17 full suite và browser/dead-route stabilization.

Canonical audit:
`docs/PRACTICE_SINGLE_SCOPE_REDUCTION_AUDIT.md`.

Chi tiết persistence matrix, finding severity, data invariant, UI acceptance,
test matrix và Definition of Done nằm tại
`docs/PRACTICE_PHASE_12_CONTINUATION_AND_CLOSURE_PLAN.md`.

Gate state sau documentation update:

- `PHASE_12_IMPLEMENTATION_BASELINE = COMMITTED`;
- `PHASE_12_POST_COMMIT_AUDIT = ACTION_REQUIRED`;
- `PHASE_12_CLOSURE_STABILIZATION_GATE = OPEN`;
- `PHASE_13_GO = NO_GO`;
- continuation checkpoint đã có code/test tự động, nhưng browser closure còn mở
  và 12R code/schema reduction chưa bắt đầu.

Static stabilization còn xác nhận private-upload deny ordering, route bindings
cho upload/material/restore/governance, worker scheduling, V1-V27-only migration
inventory, effective permission seed/query và UTF-8/mojibake guards. Không có
production TODO/FIXME/HACK mới trong boundary Phase 12.

Baseline gate verdict trước post-commit audit:

- `PHASE_12_AUTOMATED_STABILIZATION_GATE = CLOSED_GREEN`;
- `PHASE_12_CLOSURE_STABILIZATION_GATE = OPEN_BROWSER_QA_DEFERRED` vì browser
  QA không được chạy trong checkpoint này; chỉ thị ngắn lúc đó sau này được làm
  rõ là không hủy mandatory closure QA;
- không suy diễn browser/runtime pass từ automated evidence; không mở Phase 13,
  merge `main` hoặc product rollout trước quyết định riêng;
- Manual UAT, clean high-quality seed cho TOPIK và các certificate khác,
  cross-browser/device/load vẫn giữ nguyên ở Phase 15.

Accepted/deferred debt không được phép mất dấu:

- governance đã version/activate profile identity, nhưng migration toàn bộ
  hard-coded Writing/Speaking prompt rules sang database-managed `system_rules`
  chưa được tuyên bố hoàn tất; legacy adapters vẫn là compatibility path cho
  tới focused evaluator migration, calibration và provider-safe UAT;
- real Cloudflare R2 adapter, API/bucket/domain/credential policy, object
  migration/reconciliation, virus scan và multi-node rehearsal nằm ở 15E. Live
  Speaking audio/AI tiếp tục NO-GO;
- history diff/large shared-material UX và inline editor/PDF workspace lớn được
  route sang Phase 13/15;
- browser route/UI closure pass vẫn là điều kiện để đổi closure gate thành
  `CLOSED_GREEN` và bắt đầu Phase 13.

Post-commit audit supersedes only the current gate verdict, not the historical
test evidence:

- `PHASE_12_POST_COMMIT_AUDIT = ACTION_REQUIRED`;
- `PHASE_12_CLOSURE_STABILIZATION_GATE = OPEN` vì còn security, governance,
  bounded UI, JDK 17 và browser work trong 12G;
- execution contract nằm tại
  `docs/PRACTICE_PHASE_12_CONTINUATION_AND_CLOSURE_PLAN.md`.

## 11. Kế hoạch Phase 13 - Learner Delivery, Results, Progress and UI/UX

Tên phase phải được hiểu rộng hơn "polish". New-type interaction, attempt-state
semantics, result evidence và progress actionability đều là feature work hợp lệ.
Giữ route KSH `set -> test -> mode -> attempt -> result -> result/detail`; tài
liệu PREP chỉ là capability reference learner-side, không phải mẫu sao chép.

Scope lock mới: Phase 13 chỉ triển khai một KSH practice scope ngầm định sau khi
12R đóng xanh. Không có certificate/program/TOPIK-level selector. Các baseline
item về multi-certificate và governance được giữ làm history nhưng không còn là
acceptance criteria sản phẩm; Speaking vẫn là active scope.

Doc-only cleanup ngày 2026-07-13: Section 11 này là định hướng canonical cho
Phase 13 sau reduce-scope. Nếu các section cũ nói tới program/certificate,
scenario/template, DB-managed prompt/rubric/profile governance, canonical
policy table, Head/Admin approval hoặc multi-certificate routing thì coi là
historical audit trail, không phải acceptance criteria hiện hành. "Canonical"
trong Phase 13 nghĩa là contract runtime tối giản: một KSH practice scope ngầm
định, `Set > Test > Skill > Group > Question`, năm question type đã chốt,
immutable history/material traceability và lecturer collaboration.

Research input ngày 2026-07-11 gồm 96 learner screenshots/14 evidence groups và
learner-visible IELTS/TOEIC-like routes. Không có teacher/admin account evidence;
mọi teacher/admin assumption phải quay về KSH schema/RBAC. Speaking audio vẫn
`PENDING_AUDIO_UAT` vì research không kiểm chứng được âm thanh hệ thống.

PREP/IELTS/TOEIC research là học tập/tham khảo UI/UX learner-side: information
architecture, preflight, player shell, result/detail/progress states và error
recovery. Không copy brand, asset, wording, CSS, API, route structure, content
hoặc product claim. Không truy cập live PREP nếu user chưa cấp quyền và tài
khoản trong đúng task cần truy cập.

Research chỉ bổ sung, không thay baseline cũ. Baseline preservation audit khóa
ánh xạ sau; không mục nào được xem là hoàn tất chỉ vì đã được viết lại:

| Baseline trước research | Vị trí giữ trong kế hoạch hợp nhất |
|---|---|
| Typed learner player | 13C, gồm ba R/L objective type, Writing Q51-Q54 và Speaking controls, keyboard/screen reader, autosave/restore và answer-leak guard |
| Result and explanation rendering | 13D-13E, gồm correctness/pending state, objective evidence, teacher-vs-AI và limited Listening evidence |
| Full-test and skill-specific flow | 13B-13C, gồm aggregate attempt/timer, resume/submit xuyên section và history theo attempt session |
| Catalog scale | 13A và 13G, gồm server pagination/filter/search, bounded load, index/query review |
| Progress analytics | 13F, giữ skill/Writing-task dimensions và legacy semantics; không có TOPIK-level dimension mới |
| Retry and operational UX | 13F, giữ explanation retry, idempotency/rate limit và refresh-no-provider-call |
| Visual, encoding and accessibility | 13G, giữ UTF-8/mojibake, icon system, responsive/browser/a11y và overlap checks |
| Functional gate | 13H, giữ canonical-type E2E, full/skill paths, catalog performance, accessibility và answer-leak checks |

Các định hướng UI cũ về learning profile, practice library cards, set detail,
skill list, result/progress/retake/detail navigation và mobile/responsive vẫn mở.
Phase 9-11 đã hoàn tất contract/version/authoring prerequisites tương ứng nhưng
không thay thế Phase 13 UI implementation hay Phase 15 UAT.

### Baseline Phase 13 trước research - `PRESERVED_PENDING_AUDIT`

Không xóa hoặc coi các mục dưới đây đã hoàn tất trước Phase 13 audit:

- Typed learner player: R/L single choice/fill blank/true-false-not-given,
  Writing Q51-Q54 và Speaking component; keyboard và screen-reader semantics;
  typed-answer autosave; restore/reload không mất state; answer spec không xuất
  hiện trong HTML/JSON trước submit.
- Result and explanation rendering: correct/incorrect/not-answered/
  pending/unscorable; single-choice option detail; phân biệt teacher explanation và
  AI artifact; limited Listening evidence; không dùng official-equivalence wording.
- Full-test and skill-specific flow: mode dựa trên section thật có trong test;
  aggregate
  attempt session/timer; resume/submit/result xuyên section; section failure
  không corrupt session; history/progress group đúng theo attempt session.
- Catalog scale: server-side pagination/filter/search; skill, teacher/class
  filters; bounded initial load; realistic query/index
  review; không load hàng nghìn set/test card cùng lúc.
- Progress analytics: skill/Writing-task dimensions; Writing pending
  không thành 0; legacy compatibility rows có nhãn và handling rõ.
- Retry and operational UX: explanation
  unavailable/retry; idempotency/rate limit; refresh result không gọi provider.
- Visual/encoding/accessibility: sửa mojibake/UTF-8 xuyên template, seed/import,
  resource bundle và rendered page; bỏ emoji-style product icon, ưu tiên Lucide
  hoặc consistent SVG; responsive/browser matrix; no overlap; focus, labels,
  error summary và contrast; external product chỉ là inspiration.
- Gate: functional E2E ba R/L objective type, Writing Q51-Q54 và Speaking;
  full-test và skill paths; large
  catalog performance; mobile/desktop accessibility; không answer leakage.

### 13A - Library, hierarchy and state foundation

- server-backed library theo `Set > Test > Skill`, không flatten thành card độc lập;
- skill và teacher/class filter phản ánh vào URL; không có certificate/program/
  TOPIK-level filter; question-type filter chỉ thêm nếu thật sự hữu ích cho
  learner trong năm type cố định;
- thuật ngữ canonical trên UI/docs phải tránh gợi lại policy subsystem đã xóa;
  dùng ngôn ngữ sản phẩm như "bộ đề", "bài test", "kỹ năng", "nhóm câu", "câu";
- pagination/cursor, bounded initial load, loading/empty/error/retry rõ ràng;
- incremental extraction editor/player code nếu cần, không React rewrite;
- attempt state vocabulary dùng chung: `NOT_STARTED`, `IN_PROGRESS`,
  `SUBMITTED`, `SCORING`, `SCORED`, `PARTIAL`, `FAILED`, `STALE`.

### 13B - Mode selection and preflight

- chọn full test hoặc skill-specific dựa trên section thật có trong test;
- hiển thị section, timer, media/permission requirement và submit behavior trước
  khi bắt đầu;
- latest submitted, best score, in-progress attempt và attempt count là các field
  riêng; draft attempt không ghi đè latest submitted score;
- resume/start/retake action phải suy ra từ state, không từ badge trang trí.

### 13C - Shared shell and skill-native player

- shared header/timer/navigation/save/exit-warning/resume shell;
- player cần R/L single choice/fill blank/true-false-not-given, Writing Q51-Q54
  và Speaking controls, có keyboard/screen-reader semantics và autosave/restore
  không mất state;
- Reading dùng passage-question behavior; Listening dùng sticky audio/transcript
  policy; Writing giữ immutable original response/version; Speaking giữ
  prompt/media/evaluation/result boundary;
- section-level failure không corrupt full-test session;
- aggregate attempt session/timer, submit/resume navigation xuyên section và
  history/progress grouping đúng theo attempt session;
- answer spec/correct answer không xuất hiện trong HTML/JSON trước submit;
- không gọi provider chỉ vì refresh player/result.

### 13D - Result overview contract

- exam-native score scale, level/band, completion và recent-attempt semantics;
- giữ `score`, `scale`, `level`, `completion`, `timing` và
  `feedbackAvailability` là field riêng, không suy ra lẫn nhau;
- correct/partial/incorrect/not-answered/pending/unscorable summary có denominator;
- `not_answered`, `not_qualified`, `capture_failed`, `graded` render khác nhau;
- Writing unavailable hoặc pending score không biến thành 0.

### 13E - Evidence-based result detail

- learner answer, official answer, teacher explanation và AI artifact phân biệt rõ;
- single-choice option detail và bằng chứng passage/transcript;
- Reading passage evidence anchor phải bền theo content version, không dùng raw
  DOM offset; Listening transcript/audio evidence có timestamp/speaker khi có;
- Writing giữ original immutable và tách correction, upgraded answer,
  sample/rubric/criterion evidence;
- AI có confidence/limited-evidence state và không thay official answer key;
- không dùng wording ngụ ý official equivalence cho AI score/feedback;
- report entry point chuẩn bị context cho Phase 14.

### 13F - Progress/profile aggregation and operational recovery

- skill và Writing task dimensions;
- score trend/heatmap/attempt history luôn kèm sample size, recency và confidence;
- partial credit không bị tính như binary sai; legacy rows có handling rõ;
- insight deep-link tới `Practice more` theo skill/type thay vì dừng ở biểu đồ;
- filter/empty/error/retry state đầy đủ;
- Speaking retry vẫn là UX/operational decision riêng, không được tự gọi provider
  khi refresh player/result;
- explanation unavailable/retry state có idempotency và rate limit;
- refresh player/result không tự gọi lại provider.

### 13G - Responsive, accessibility and performance

- fix Vietnamese/Korean mojibake như `Cáº¥p`, `Äá»c` và các artifact tương
  tự trong template, database seed/import text, resource bundle và rendered
  page; toàn bộ đường dữ liệu phải nhất quán UTF-8 và có UI/UAT regression;
- tránh emoji-style product icon như `🚀`; ưu tiên Lucide hoặc consistent SVG
  icon component. Phase 9G chỉ sửa một nhóm encoding hẹp, chưa đóng sweep này;
- desktop/mobile/browser matrix, không overlap control/text;
- focus state, label, error summary, contrast và reduced-motion where applicable;
- large catalog/query/index review với realistic volume, không load hàng nghìn
  set/test card cùng lúc;
- PREP-like reference chỉ là inspiration, không copy brand/asset/content/CSS/API/URL.

### 13H - Visual and journey gate

- functional E2E cho R/L single choice/fill blank/true-false-not-given,
  Writing Q51-Q54 và Speaking;
- full-test + skill-specific + resume/retake paths;
- result overview/detail/progress deep-link journey;
- partial/failure/pending/capture-error/empty states;
- mobile/desktop accessibility và visual QA;
- large-catalog performance target, UTF-8/icon sweep và multi-test/multi-skill
  UAT phải đạt; research screenshots không thay thế gate này;
- không answer leakage, no provider call trong default automated gate;
- Speaking UI/flow có trong Phase 13; live Speaking AI/audio production rollout
  vẫn cần storage/provider/UAT gate riêng.

## 12. Kế hoạch Phase 14 - Report an Error and Content Review

Phase 14 phải là learner-visible review loop. Report state độc lập với content
lifecycle `DRAFT/PUBLISHED/ARCHIVED` của Phase 12.

Research chỉ mở rộng Phase 14. Baseline model/workflow/authorization/correction/
feedback/gate cũ được giữ theo ánh xạ: report model -> 14A-14B; workflow status
-> 14C; authorization/privacy -> 14B và 14D; correction semantics -> 14D;
notification/SLA/metrics -> 14E; end-to-end gate -> 14F.

### Baseline Phase 14 trước research - `PRESERVED_PENDING_AUDIT`

- Report model gắn immutable set/test/section/question version, attempt
  ID, target prompt/option/answer/explanation/translation/stimulus/audio/scoring/
  UI, category, description, reporter, timestamps và safe attachment refs.
- Workflow baseline giữ `OPEN`, `TRIAGED`, `IN_REVIEW`, `CHANGES_REQUESTED`,
  `RESOLVED`, `REJECTED`, `DUPLICATE` cho tới khi audit quyết định migration hay
  mapping sang candidate mới.
- Authorization/privacy: Student chỉ report content được truy cập; Lecturer chỉ
  xem content sở hữu/phụ trách; Head/Admin triage theo scope; learner answer/
  audio chỉ hiện khi cần và có quyền; attachment/log không lộ secret.
- Correction semantics: không mutate published version; content correction tạo
  draft/new version; explanation correction tạo artifact mới và supersede bản
  cũ; old attempt vẫn audit được; AI không tự publish.
- Feedback loop: notification reporter/content owner; SLA/age dashboard;
  duplicate grouping; metrics theo program/type/source/import; high-severity
  wrong-answer có thể tạm block new attempts theo policy nhưng không xóa lịch sử.
- Gate: report-to-correction-to-new-version E2E; permission/privacy tests;
  immutable history; audit log đầy đủ.

### 14A - Entry points and immutable context

- entry point từ player, question review và result detail;
- server tự gắn program, set/test/section/group/question, content version,
  attempt, reporter, timestamps, active view/tab và correlation ID;
- target có thể là prompt, option, answer, explanation, translation, passage,
  transcript, audio, scoring hoặc UI;
- learner không phải nhập lại internal IDs/context.

### 14B - Learner form and evidence snapshot

- structured category/reason, optional note và learner-safe confirmation;
- description và safe attachment references thuộc report model, không gắn raw
  local path/provider/storage secret;
- evidence snapshot lấy đúng immutable version/attempt/view đang report;
- screenshot/audio attachment là optional/category-dependent, có consent, MIME
  inspection, size limit, private storage và retention policy;
- không thu thập toàn màn hình hoặc dữ liệu ngoài scope mặc định.

### 14C - Deduplication and status history

- status baseline `OPEN`, `TRIAGED`, `IN_REVIEW`, `CHANGES_REQUESTED`,
  `RESOLVED`, `REJECTED`, `DUPLICATE` phải được giữ để audit; research bổ sung
  `NEW`, `NEEDS_INFO`. Phase 14 audit mới được chọn canonical vocabulary sau
  khi map semantics và compatibility, không thay âm thầm;
- dedupe theo immutable target/version/category và bounded time window;
- learner thấy lịch sử/status/response an toàn, không thấy internal moderation note;
- rate limit/abuse protection và idempotent submit.

### 14D - Reviewer queue and correction semantics

- queue filter theo skill, severity, status, owner/source/import và age;
- Student chỉ report nội dung mình truy cập được;
- Lecturer chỉ xem content mình sở hữu/phụ trách; Head/Admin theo action scope;
- learner answer/audio chỉ hiện khi cần và có quyền; secret/storage key không lộ;
- không mutate published question/explanation artifact;
- correction tạo draft/new published version; sửa explanation tạo artifact mới
  và đánh dấu artifact cũ superseded; old attempt/artifact vẫn audit được;
- AI không được tự publish correction; reviewer action/reason/version đều audit.

### 14E - Resolution feedback and operational loop

- notification/feedback cho reporter và content owner chỉ nói kết quả review
  thực tế; có SLA/age dashboard, duplicate grouping và metrics theo
  program/type/source/import;
- UI không ngụ ý report đã đổi điểm trước khi score decision/corrected version
  thật sự được review và publish;
- high-severity wrong-answer report có thể tạm block new attempts theo policy
  sau review, nhưng không xóa lịch sử;
- confirmed defect tạo regression fixture và metrics theo program/type/source;

### 14F - End-to-end gate

- E2E report -> review -> correction -> new version -> learner feedback;
- permission/privacy/dedupe/attachment/malformed-content UAT;
- immutable history và audit trail đầy đủ.

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

- dữ liệu development hiện tại chỉ là fixture thử nghiệm, không phải Manual UAT
  hoặc product evidence;
- khi Phase 15 Manual UAT bắt đầu, user cho phép drop/recreate **chỉ database
  local/UAT chuyên dụng** sau khi xác minh schema/environment; tuyệt đối không
  áp dụng cho production hoặc database chưa xác định. Chạy fresh migration và
  representative legacy-upgrade rehearsal trước khi load data;
- dùng deterministic/versioned UAT fixture pack hoặc UAT-only seed loader, tách
  khỏi production Flyway seed nếu chưa có duyệt riêng. Nội dung phải là tiếng
  Hàn thực tế, original/licensed, teacher-reviewed và không dùng placeholder;
- seed một KSH practice scope với nhiều set/test, đủ Reading, Listening,
  Writing, Speaking, shared group material, question material và valid/warning/
  invalid import fixtures;
- fixture full-form tối thiểu có 50 Reading, 50 Listening, Writing 51, 52, 53,
  54 và representative Speaking prompts, kèm local numbering, answer key/
  accepted values, teacher explanation, passage/transcript và media placement;
- Chrome, Firefox, Safari/Edge theo target support;
- desktop/mobile;
- recorder permission denied/revoked/retry;
- audio seek/range/reload;
- full-test resume/timer;
- all question types và result explanations;
- Korean/Vietnamese IME, Unicode và spacing.

Phase 15 Manual UAT là release gate riêng, không bị di chuyển sang Phase 12.
Stabilization cuối Phase 12 chỉ bảo đảm code/route/UI hiện hữu đủ sạch để bắt đầu
Phase 13.

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

- triển khai Cloudflare R2 sau khi user cấp API/bucket/domain policy; adapter
  vẫn disabled trước thời điểm đó;
- checksum-based local-to-R2 migration rehearsal, idempotency, rollback và
  orphan reconciliation;
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
| Multiple choice bị collapse về MCQ | Pre-10 baseline | RESOLVED_AUTHORING | 10A-10F + 11C hoàn tất | Phase 13 còn learner player UI |
| AI explanation version-locked lifecycle thiếu | 9/10 | RESOLVED_BACKEND | 10G hoàn tất | Phase 13 còn typed rendering đầy đủ |
| Manual editor `passageText` không được publisher/revision persist | Pre-11 audit | RESOLVED | 11A/11F hoàn tất | Đã đóng bằng live/version stimulus tests |
| Objective và Writing/Speaking dùng khác score unit trong `attempt.score` | 8/10/11 | RESOLVED_CONTRACT | 11A/11C hoàn tất | Phase 13 dùng field chuẩn để render/report |
| Listening thiếu explicit persisted transcript/evidence binding | 10/11 | RESOLVED_AUTHORING | 11F hoàn tất | Phase 13/15 còn learner UX và evidence UAT |
| Draft/category chưa có first-class exam template identity | 10/11 | RESOLVED_AUTHORING | 11A/11B hoàn tất | Attempt delivery-policy lock rộng hơn vẫn ở 13C |
| PDF AI import còn legacy answer key và TOPIK hard-code | Pre-11 audit | RESOLVED_AUTHORING | 11F hoàn tất | Provider/browser fixture UAT ở 15 |
| Excel assessment import chưa có | Product feedback | RESOLVED_AUTHORING | 11E hoàn tất | Browser UAT và asset extension về sau |
| Manual editor còn inline script lớn | 11G | P2 | 13A incremental | Không; hai canonical module đã tách và dead modules đã xóa |
| PDF workspace còn inline/alert-heavy và expert-first ở một số nhánh | 11F/closure audit | P2 | 13C/15B incremental | Không cho correctness; có cho UX/UAT production |
| Excel preview A-H/media rất rộng, phụ thuộc horizontal scroll | 11E/closure audit | P2 | 13G | Không; không được giảm evidence chỉ để thu gọn UI |
| Companion media orphan cleanup/retention chưa hoàn chỉnh | 11E/12D | P0/P1 | 12D/15E | Có cho production asset lifecycle |
| Upload mới dừng ở path/type/size; deep magic-byte/content inspection còn mở | 11/closure audit | P1 | 12D/15B/15E | Có cho public production upload |
| External CDN/CSP/supply-chain hardening chưa rehearsal | Closure audit | P1/P2 | 15A/15E | Có theo production threat model |
| Practice RBAC schema/effective overrides chưa được wire theo action | Pre-12 audit | P0 | 12A | Có |
| Collaboration scope và owner lock chưa có | Pre-12 audit | P0 | 12A/12B | Có cho shared editing rollout |
| Restore mutate live graph; republish bị chặn khi đã có attempt | Pre-12 audit | P0 | 12B | Có cho immutable history/shared editing |
| Snapshot capture fail-open thành `{}`; rollback log thiếu complete before evidence | Pre-12 audit bổ trợ | P0 | 12B | Có cho publish/history integrity |
| Private/public/published material delivery đang mâu thuẫn | Pre-12 audit | P0 | 12D | Có; có nguy cơ leak hoặc learner không đọc được asset |
| Published/version material refs chưa tham gia retention/delete guard | Pre-12 audit | P0 | 12D | Có cho lịch sử immutable |
| DB/filesystem asset promote/delete chưa có durable compensation/retry | Pre-12 audit bổ trợ | P1/release | 12D | Có trước product rollout |
| Program/template/profile activation có thể drift, chưa audit/rollback | Pre-12 audit | P1 | 12C | Có cho Admin/Head config rollout |
| Prompt profile system rules chưa được runtime map | Pre-12 audit | P1 | 12C | Có cho configurable Writing/Speaking policy |
| Archive/unarchive action chưa có dù schema có ARCHIVED | Pre-12 audit | P1 | 12B | Có cho lifecycle hoàn chỉnh |
| Public authoring upload validation/lifecycle | Pre-10/Pre-12 audit | P0/P1 | 12D; deep release rehearsal ở 15E | Có cho production |
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

Audit-only slice sau đã hoàn tất:

`PRE_PHASE_11_AUTHORING_AND_IMPORT_CONTRACT_GATE`

Kết quả sáu câu audit:

1. Editor đã dùng server authoring catalog và canonical typed answer contract.
2. PDF/AI import đã normalize selected template, stable IDs và typed contract
   trước publish.
3. Instruction, passage, transcript, media, image và provenance đã được persist
   vào live graph và immutable group version.
4. Draft validator, editor catalog và publisher cùng resolve program policy.
5. Guided PDF regions giữ source-region IDs đến canonical draft; low-confidence
   content bị chặn cho tới khi giáo viên xác nhận.
6. Ranh giới Phase 11 được giữ: learner interaction/polish vẫn thuộc Phase 13,
   governance/owner lock vẫn thuộc Phase 12.

Current next action: implement 12R from
`docs/PRACTICE_SINGLE_SCOPE_REDUCTION_AUDIT.md`. Không tiếp tục 12H generic
governance, không merge hoặc mở Phase 13 trước khi schema/code/UI đã về một KSH
scope ngầm định và JDK 17/browser stabilization gate xanh.

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

## Phụ lục C - Phase 11 current-state delta

| Pre-11 finding | Trạng thái sau implementation | Evidence chính |
|---|---|---|
| Passage/transcript mất khi publish/restore | Typed stimulus persist ở live group và immutable group version | V26, publisher/revision/flow tests |
| Category không đủ định danh variant | Program version và exam template là first-class authoring metadata | `assessment_exam_templates`, authoring catalog, V26 integration test |
| Editor hiển thị type tĩnh | Skill/type/scoring/profile lấy từ server policy | `manage-authoring-contract.js`, editor contract test |
| Draft validation lệch publisher | Backend validator dùng template/program policy và machine codes | `PracticeDraftValidator`, validator tests |
| Preview có nguy cơ lộ key/context nghe | Safe preview chỉ trả delivery DTO; transcript/answer/profile bị loại | `PracticeDraftPreviewServiceTest` |
| Không có deterministic spreadsheet import | Excel-v2 all-skill import, detailed valid/warning/error preview, compact numbering, A-H/media và typed MATCHING | Excel service/controller/template tests và browser smoke |
| PDF flow quá chuyên gia và output legacy | Guided full-page mode, canonical assembler, confidence/review gate, debug RBAC | PDF payload/assembler/controller tests |
| Score field mơ hồ | Giữ compatibility `score`, thêm explicit earned/percentage/unit | V26 và `PracticeAttemptScoreContractTest` |
| Editor có dead JS implementation song song | Dead modules đã xóa; hai canonical module đang được load | UI contract và JS syntax checks |
| Phase gate thiếu runtime/full-suite evidence | Focused closure 20/20, focused Excel/media/MATCHING 47/47, full suite 1242/1242 và editor/menu/PDF/Excel/preview runtime QA xanh | Maven JDK 17/MySQL V26 và controlled port 8082 QA; closure gate `CLOSED_GREEN` |
