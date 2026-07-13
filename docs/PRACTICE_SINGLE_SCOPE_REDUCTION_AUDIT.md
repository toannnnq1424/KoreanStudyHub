# Practice Single-Scope Reduction Audit

Ngày audit/refine: 2026-07-13

Nhánh: `feature/practice-reduce-scope`

Baseline: `59cbc78`

Trạng thái: `PRACTICE_CODE_GATE_GREEN_BROWSER_QA_SKIPPED`

Gate: `PHASE_12R_SINGLE_SCOPE_REDUCTION_GATE = PRACTICE_CODE_GATE_GREEN`

## 1. Quyết định sản phẩm

Scope đa chứng chỉ và assessment governance tổng quát dừng tại checkpoint hiện
tại. Product trước Phase 13 chỉ còn một mô hình luyện thi tiếng Hàn ngầm định của
KSH. Giáo viên không chọn certificate, program, TOPIK I/TOPIK II, scenario hay
policy khi tạo đề. Target được rút về:

- kỹ năng learner-facing gồm Reading, Listening, Writing và Speaking;
- Reading/Listening cho phép `SINGLE_CHOICE`, `FILL_BLANK` và
  `TRUE_FALSE_NOT_GIVEN`;
- Reading, Listening và Speaking không có giới hạn số câu theo product policy;
- mỗi Writing section có đúng bốn task Q51, Q52, Q53 và Q54, mỗi task xuất hiện
  đúng một lần;
- Speaking là kỹ năng mặc định của sản phẩm KSH. Hệ thống phải giữ authoring,
  media, playback, evaluation và result flow, không coi Speaking là add-on;
- toàn domain chỉ còn năm question type: `SINGLE_CHOICE`, `FILL_BLANK`,
  `TRUE_FALSE_NOT_GIVEN`, `ESSAY` và `SPEAKING`;
- không còn program/certificate CRUD, scenario/template governance tổng quát,
  question-type policy hay profile governance cho Admin/Head;
- nội dung do Lecturer sở hữu và có thể chia sẻ trực tiếp cho Lecturer khác;
  không có Head/Admin approval hoặc emergency override trong content workflow;
- immutable publish history, restore version bất kỳ, owner lock, private material
  delivery và attempt/result traceability vẫn được giữ;
- Reading/Listening vẫn chấm deterministic bằng answer key/accepted values của
  giáo viên. AI chỉ giải thích sau khi nộp bài và không được thay đổi đáp án
  chính thức;
- Writing Q51-Q54 và Speaking tiếp tục dùng evaluator/rubric/prompt rule hiện có
  trong code, không cần DB profile governance ở giai đoạn này.

Đây là thay đổi định hướng bắt buộc. Phase 13 không được bắt đầu trước khi code,
schema, routes, fixtures và UI đã thực sự thu về contract này.

## 2. Kết quả audit hiện trạng

### 2.1 Độ lớn schema

Migration inventory hiện tạo 96 bảng toàn hệ thống. Trong đó có 42 bảng thuộc
practice/assessment/material/import/writing/speaking boundary.

Nhóm generic assessment governance riêng đã chiếm 10 bảng:

1. `assessment_programs`
2. `assessment_program_versions`
3. `assessment_skills`
4. `assessment_program_skill_policies`
5. `assessment_question_type_policies`
6. `assessment_scoring_profiles`
7. `assessment_prompt_profiles`
8. `assessment_rubric_profiles`
9. `assessment_exam_templates`
10. `assessment_exam_template_versions`

Các lớp trùng lặp hoặc không còn cần thiết đã xác minh:

- `practice_governance_audit_events` chủ yếu phục vụ Head/Admin override và
  assessment governance; revision history đã có `practice_edit_logs`;
- `practice_ai_analysis_usage` có entity nhưng không có production service hoặc
  repository usage;
- `practice_submissions` là legacy flow; attempt flow mới dùng
  `practice_attempts` và integration test xác nhận không tạo submission mới;
- `practice_draft_asset_usages` là legacy material link trong khi V27 đã có
  `practice_material_references` cho draft và immutable published version.

### 2.2 Độ lớn code

Static reference inventory cho thấy:

- 19 file tham chiếu trực tiếp schema `assessment_*`;
- 31 file practice/test/template chứa generic objective types như
  `MULTIPLE_CHOICE`, `TRUE_FALSE_NOT_GIVEN`, `FILL_BLANK`, `MATCHING`;
- 158 file production/test/resource chứa Speaking behavior hoặc UI;
- 27 file tham gia governance override/audit path.

Package `features/practice/assessment` hiện có policy resolver, persistence,
repositories, generic answer contracts và scoring cho nhiều question types. Cụm
này cần được thu về rule cố định trong code cho năm type đã chốt. Package
`features/practice/ai/speaking` có evaluator, transcription, rubric, normalizer
và provider path riêng; đây là active scope cần giữ nhưng phải tách khỏi generic
program/profile governance.

### 2.3 Complexity đang bị trả nhưng không tạo giá trị trong scope mới

- Program version, template version và active pointer cần atomic activation dù
  product chỉ còn một mô hình đề ngầm định.
- Question-type policy và profile binding trong DB không tạo thêm giá trị vì
  skill/type matrix đã cố định trong code.
- Admin/Head governance UI buộc người dùng hiểu version, JSON, profile và
  scenario nhưng không còn actor nghiệp vụ tương ứng.
- Emergency override tạo reason/audit/context propagation xuyên manual, Excel,
  PDF, material và publish routes dù content workflow mới chỉ có owner và
  collaborator Lecturer.
- Speaking kéo theo media upload/playback, transcription, evaluation, cleanup,
  retention và R2 release concerns. Đây là complexity được chấp nhận vì Speaking
  là năng lực sản phẩm quan trọng, nhưng không được dùng để giữ lại toàn bộ
  generic program/profile governance.

## 3. Target domain tối thiểu

```text
Practice Set
  owner Lecturer
  optional collaborator Lecturer
  owner lock
  Test 1..N
    Listening section
      Group
        Question: SINGLE_CHOICE | FILL_BLANK | TRUE_FALSE_NOT_GIVEN
    Reading section
      Group
        Question: SINGLE_CHOICE | FILL_BLANK | TRUE_FALSE_NOT_GIVEN
    Writing section
      Question: Q51, Q52, Q53, Q54 exactly once
    Speaking section
      Question: SPEAKING, 1..N
```

Không có `Program`, `Certificate`, `TOPIK level`, `Scenario root`, `Delivery
mode`, `Question type policy` hoặc DB-managed profile trong target runtime.
`Set > Test > Skill > Group > Question` và nội dung thật của test là nguồn xác
định test có những kỹ năng nào. Full-test hoặc luyện riêng kỹ năng là lựa chọn
learner UX dựa trên section đang tồn tại, không phải policy giáo viên phải cấu
hình.

Skill/type matrix cố định:

| Kỹ năng | Question type | Số lượng |
|---|---|---|
| Reading | `SINGLE_CHOICE`, `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN` | tùy giáo viên |
| Listening | `SINGLE_CHOICE`, `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN` | tùy giáo viên |
| Writing | `ESSAY`, phân biệt bằng `writing_task_type` Q51-Q54 | đúng bốn task, mỗi loại một lần |
| Speaking | `SPEAKING` | tùy giáo viên |

Các invariant này được đặt trong một code-owned `PracticeContentRules` nhỏ,
không có selector trên UI và không truy vấn governance DB. Khi đổi rule phải đổi
source/test; không tạo thêm bảng version/policy.

## 4. AI contract được giữ

### Reading và Listening

Nguồn sự thật:

1. passage hoặc transcript/group material;
2. prompt;
3. options hoặc blank/TFNG contract;
4. teacher answer key hoặc accepted values;
5. learner answer;
6. deterministic correctness;
7. teacher explanation nếu có.

Backend chấm đúng/sai trước. `SINGLE_CHOICE` có đúng một key trong các option;
`TRUE_FALSE_NOT_GIVEN` có một key cố định; `FILL_BLANK` dùng accepted values và
aliases do giáo viên nhập. AI chỉ tạo giải thích tiếng Việt dựa trên context
trên, không được sửa key/accepted values, tự thêm option hoặc đổi deterministic
result.
`question_explanation_cache` tiếp tục được giữ nhưng bỏ program/profile metadata
không còn dùng.

### Writing

- giữ `WritingTaskType` Q51-Q54;
- giữ scoring/rubric/rule engine hiện tại trong code;
- immutable result phải giữ evaluator/rubric version string cần thiết để trace;
- không dùng assessment prompt/rubric/scoring profile tables;
- không mở UI cho Admin/Head sửa system rule ở scope mới.

### Speaking

- giữ Speaking authoring, text/audio answer, private media playback, evaluator,
  transcription and result rendering paths;
- giữ production NO-GO cho live Speaking audio/AI nếu storage/R2/provider/UAT
  gates chưa xanh;
- không dùng assessment prompt/rubric/scoring profile tables;
- prompt/rubric/system rules được quản lý bằng code-owned policy cho scope này,
  không mở generic Admin/Head governance UI.

## 5. Authorization và collaboration tối thiểu

Actor của content workflow:

| Actor | Quyền |
|---|---|
| Lecturer owner | Create, edit, publish, archive, restore, lock/unlock, material |
| Lecturer collaborator | Edit, publish, restore và material khi được share và owner chưa lock |
| Lecturer không liên quan | Deny |
| Student | Chỉ dùng published learner content đúng scope |
| Head/Admin | Không có content-governance override; role hệ thống khác không bị thay đổi |

Giữ một collaboration relation và owner lock. Không cần per-action checkbox cho
collaborator trong UI; grant có một capability cố định `CAN_EDIT_CONTENT` bao gồm
edit/publish/restore/material. Owner có thể revoke hoặc lock.

Global RBAC tables của toàn hệ thống không được drop vì các module khác còn dùng.
Chỉ xóa các permission seed/path riêng cho `practice.governance.manage`,
`practice.override` và reviewer behavior không còn trong single-scope workflow.

Audit lịch sử dùng `practice_edit_logs` cùng published version records. Không giữ
một governance audit table song song chỉ để ghi cùng actor/action/time/reason.

## 6. Database reduction matrix

### 6.1 Bắt buộc drop sau migration preflight

| Nhóm | Bảng | Lý do |
|---|---|---|
| Generic governance | 10 bảng `assessment_*` liệt kê ở mục 2.1 | Một rule set cố định trong code |
| Governance audit | `practice_governance_audit_events` | Gộp actor/action history vào edit log/version history |
| Unused | `practice_ai_analysis_usage` | Không có production usage |
| Legacy submission | `practice_submissions` | Attempt flow là canonical |
| Legacy asset link | `practice_draft_asset_usages` | `practice_material_references` thay thế |

Kết quả bắt buộc: giảm 14 bảng, từ 42 xuống tối đa 28 bảng trong practice/
assessment boundary.

### 6.2 Bắt buộc giữ

- live authoring graph: set, test, section, group, question;
- immutable published graph và published version;
- draft, attempt, edit log và Writing evaluation cache;
- `question_explanation_cache`;
- lecturer assets và `practice_material_references`;
- `practice_speaking_media` và `practice_speaking_media_cleanup_tasks`;
- asset lifecycle task hiện có vì private/published material vẫn cần cleanup an
  toàn và Cloudflare R2 vẫn là target tương lai;
- lecturer collaboration;
- PDF tables chỉ giữ nếu quyết định vẫn giữ PDF-AI authoring trong Phase 13.

### 6.3 Đề xuất giảm thêm: defer PDF-AI authoring

Manual authoring và Excel import đủ để mở Phase 13 UI. Nếu chấp nhận defer
PDF-AI workspace, có thể drop thêm:

1. `practice_pdf_import_sessions`
2. `practice_pdf_region_annotations`
3. `practice_pdf_page_extractions`
4. `practice_pdf_import_section_drafts`
5. `practice_pdf_import_group_drafts`
6. `practice_ai_request_audits`

Lean target khi defer PDF-AI: khoảng 20 bảng trong boundary này.

Đây là đề xuất, không phải quyết định bắt buộc trong audit này vì PDF-AI từng là
mục tiêu authoring quan trọng. Nếu vẫn giữ PDF-AI, freeze feature theo một scope
KSH và năm type đã chốt; không tiếp tục mở rộng strategy/region UX trước Phase
13.

### 6.4 Không gộp live graph với immutable version graph trong 12R

Không gộp các cặp bảng như `practice_tests` và `practice_test_versions` trong
scope-reduction này.

Lý do:

- `practice_tests` là live authoring graph, nơi giáo viên tiếp tục sửa bản hiện
  hành;
- `practice_test_versions` là immutable publish snapshot theo từng
  `practice_published_versions`;
- `practice_section_versions` đang tham chiếu `test_version_id`, và
  `practice_attempts` cũng khóa `test_version_id` để giữ đúng bài mà học viên đã
  làm;
- nếu đưa field version vào `practice_tests`, một bảng sẽ phải vừa chứa draft/live
  row vừa chứa historical row, cần thêm discriminator, parent/self reference,
  composite uniqueness và rule chống sửa nhầm snapshot;
- cách đó giảm số bảng nhưng làm mờ boundary live-vs-history, tăng rủi ro rewrite
  attempt cũ, restore sai version hoặc leak material historical.

Giữ immutable version graph là tradeoff có chủ đích. Phần giảm bảng phải tập
trung vào governance/question-type/legacy tables trước, vì các bảng đó không còn
tạo giá trị trong single-scope. Speaking không nằm trong nhóm cắt giảm. Chỉ
được xem xét gộp version graph sau Phase 13 nếu có một audit riêng chứng minh
schema mới vẫn giữ được các invariant: publish snapshot bất biến, restore bất kỳ
version tạo version mới, attempt cũ không đổi, material historical authorization
đúng version.

## 7. Column và contract reduction

### Xóa khỏi set/draft/version/import identity

- `assessment_program_code`;
- `assessment_program_version_id`;
- `exam_template_code`;
- `topik_level` và mọi category/certificate discriminator tương đương;
- các foreign key/index tương ứng.

### Thu gọn question/question-version

Do target còn năm dạng câu hỏi, cần giữ đúng một discriminator
`question_type`. Không được xóa type và suy diễn chỉ từ skill. Thu gọn như sau:

- giữ `question_type`, khóa DB/code về đúng năm giá trị `SINGLE_CHOICE`,
  `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN`, `ESSAY`, `SPEAKING`;
- xóa `canonical_question_type` trùng lặp sau compatibility migration;
- xóa `MULTIPLE_CHOICE`, `MATCHING` cùng scorer/codec/UI/import contract của hai
  type này;
- `scoring_policy_code`;
- scoring/prompt/rubric profile code/version columns.

`question_content_json` và `answer_spec_json` chỉ được drop sau khi xác minh không
còn giữ dữ liệu cần thiết. `FILL_BLANK` cần stable blank IDs, accepted values và
aliases; nếu đây là nơi canonical đang lưu contract đó thì phải giữ hoặc migrate
sang một cấu trúc hẹp tương đương trước.

Question behavior được xác định bằng section skill kết hợp `question_type`:

- section `READING` hoặc `LISTENING` -> chỉ ba objective type đã cho phép;
- section `WRITING` -> `ESSAY` và `writing_task_type` Q51-Q54;
- section `SPEAKING` -> `SPEAKING` cùng prompt/rubric/media rule trong code.

Vì live question hiện đi qua group để tới section, migration phải chuẩn hóa mọi
question còn `group_id = NULL` vào một group/section hợp lệ trước khi drop type.
Question version đã có `section_version_id` nên có thể resolve skill trực tiếp.
Service và DB integrity tests phải fail nếu question không resolve được đúng một
section skill/type pair hợp lệ; không fallback đoán theo options/prompt.

Giữ:

- `options_json`, `answer_key`, fill-blank accepted values và `explanation` cho
  R/L;
- `question_type` trong live và immutable question graph;
- `writing_task_type` Q51-Q54 cho Writing;
- points/display order/group relation;
- immutable copy của các field trên trong question version.

### Explanation cache

Bỏ `program_code` và DB profile reference. Giữ question version, question hash,
stimulus hash, answer-key hash, prompt version, model và generated explanation.

## 8. Code/UI cần xóa hoặc thay thế

### Xóa

- assessment governance page/controller/service/repositories/entities/JS/CSS;
- program/template/profile activation and CRUD;
- `AssessmentDeliveryMode` và user-facing delivery-mode controls;
- certificate/category/program/template selector và paths tương ứng;
- UI, validator, Excel sheet và scoring branch cho `MULTIPLE_CHOICE` và
  `MATCHING`;
- Head/Admin emergency override UI/context/audit path cho practice content.

### Thay thế

- `AssessmentProgramPolicyService` -> `PracticeContentRules` không truy vấn DB;
- `AssessmentAuthoringCatalogService` -> catalog một scope với skill/type matrix
  cố định;
- generic objective scoring -> ba deterministic scorer cho single choice,
  fill blank và true/false/not-given;
- generic Excel template -> một template KSH gồm Reading, Listening, Writing và
  Speaking, chỉ cho phép type hợp lệ theo skill;
- program/template/category selector -> xóa hoàn toàn khỏi authoring UI;
- content authorization -> owner/collaborator/lock service nhỏ, deny-by-default.

### Giữ

- set/test/section/group/question hierarchy;
- manual editor, Excel preview/import và media attachment ở section/group/
  question level;
- immutable publish/attempt version lock;
- full revision list và restore bất kỳ version;
- material authorization, Range và private/public version boundary;
- R/L explanation service hỗ trợ ba objective type đã chốt và luôn giữ teacher
  key/accepted values làm nguồn sự thật;
- Writing Q51-Q54 evaluator;
- Speaking player, media upload/playback, transcription/evaluation, result and
  cleanup paths.

## 9. Migration strategy

Update 2026-07-13: trước commit reduce-scope, user yêu cầu squash
`V25`-`V29` thành một migration gọn và khớp thực tế, không copy-paste tuần tự
các migration cũ. Vì vậy final tree chỉ ship
`V25__practice_single_scope_final.sql`; các file V26-V29 không còn tồn tại trong
branch này. V25 là final-state migration: không tạo các bảng
program/governance/profile đã bị loại rồi drop lại, mà chỉ giữ DDL/data còn cần
cho single-scope runtime.

Thứ tự bắt buộc:

1. Backup hoặc drop/reseed development database theo quyền user đã cấp.
2. Preflight tìm program/category/level metadata cũ, question type ngoài năm
   type được giữ, invalid skill/type pair, invalid Speaking media và legacy
   submission/asset usage.
3. Không âm thầm rewrite learner attempt/result. Nếu có dữ liệu không thể map,
   migration phải fail với report rõ hoặc dùng dev reset đã duyệt.
4. Migrate draft asset usage sang material references nếu còn row hợp lệ.
5. Ghi actor/action cần giữ từ governance audit sang edit log khi có set target.
6. Gỡ foreign keys/columns phụ thuộc assessment tables.
7. Drop table theo dependency order.
8. Thêm check constraints cho năm type, skill/type matrix và Writing Q51-Q54;
   seed một bộ dữ liệu KSH chất lượng tối thiểu.
9. Hibernate validation và fresh V1-squashed-V25 rehearsal.

Không tạo thêm migration V26-V29 trong branch reduce-scope này.

## 10. Execution slices trước Phase 13

### 12R.1 - Freeze scope

- ẩn/deny governance và custom certification routes;
- bỏ mọi certificate/program/category/level selector khỏi contract mới;
- chặn type ngoài `SINGLE_CHOICE`, `FILL_BLANK`,
  `TRUE_FALSE_NOT_GIVEN`, `ESSAY`, `SPEAKING` và skill/type pair sai ở
  server-side;
- thêm contract tests cho single-scope boundary.

### 12R.2 - Replace generic policy runtime

- tạo code-owned `PracticeContentRules` không có user-selectable policy;
- đổi manual/Excel/PDF/publisher/player/scoring/explanation sang policy mới;
- xóa DB policy reads và profile resolution.

### 12R.3 - Schema reduction

- forward migration với preflight;
- drop 14 bảng bắt buộc và columns/FKs dư;
- quyết định riêng về 6 bảng PDF-AI;
- fresh và upgrade migration tests.

### 12R.4 - Remove code surface

- xóa governance/admin override packages/routes/UI/tests;
- xóa `MULTIPLE_CHOICE`, `MATCHING` và code/codec/fixture chỉ phục vụ hai type;
- xóa repository/entity không còn table;
- compile/dead-code scan.

### 12R.5 - Lecturer-only content collaboration

- owner share/revoke collaborator;
- collaborator edit/publish/restore/material;
- owner lock denies collaborator;
- Head/Admin không override;
- cross-owner and student denial tests.

### 12R.6 - KSH authoring and AI regression

- manual and Excel import không có program/certificate/level selector;
- R/L single choice, fill blank và true/false/not-given đúng deterministic
  contract;
- không áp business cap số câu Reading, Listening hoặc Speaking;
- Writing có đúng Q51-Q54, mỗi task một lần;
- group passage/transcript/audio/image preserved;
- AI explanation context and deterministic-key invariant;
- Writing Q51-Q54 evaluation and result trace.
- Speaking media/evaluation/result trace.

### 12R.7 - Mandatory stabilization

- full JDK 17 suite;
- fresh/upgrade migration;
- route/controller/template/script/dead-code audit;
- browser journeys Lecturer owner/collaborator and Student;
- no valid-route 4xx/5xx, broken controls, raw HTML error, console/network error,
  mojibake or stale program/certificate/level/governance UI;
- stop temporary servers;
- only then request Phase 13 GO.

## 10A. Implementation checkpoints

Mục này là nhật ký phục hồi context. Mỗi step triển khai phải cập nhật ngay khi
đóng, trước khi chuyển sang step kế tiếp.

### Checkpoint 1 - Baseline và dependency graph - COMPLETED

Ngày: `2026-07-13`

- xác nhận nhánh thực thi là `feature/practice-reduce-scope`;
- xác nhận migrations `V1` đến `V28` đã thuộc lịch sử đã push, vì vậy reduction
  phải dùng squashed `V25` migration, không sửa migration cũ;
- kiểm kê production dependency cho governance, authoring catalog, policy,
  question types, editor, Excel/PDF import, publisher, player, scoring,
  explanation, authorization và material/version history;
- xác nhận `practice_tests` là live graph còn `practice_test_versions` là
  immutable snapshot graph, không gộp hai bảng;
- xác nhận `openspec-temp/` là dữ liệu ngoài phạm vi và không được stage;
- baseline `clean compile` dưới JDK 17 xanh trước reduction;
- compile JDK 17 tiếp tục xanh sau khi thêm `PracticeContentRules`, chuyển
  authoring catalog/policy sang code-owned compatibility facade và gỡ package,
  route, template, CSS/JS, repository/entity/test của assessment governance;
- chưa tạo commit hoặc push trong checkpoint này.

Step kế tiếp: đóng runtime contract code-owned hoàn chỉnh, loại mọi DB policy
lookup khỏi authoring/publish/validation trước khi thay đổi schema.

### Checkpoint 2 - Code-owned runtime contract - COMPLETED

Ngày: `2026-07-13`

- thêm `PracticeContentRules` làm nguồn sự thật runtime duy nhất cho ma trận
  skill/question type, scoring mặc định, giới hạn option single-choice và bộ
  Writing Q51-Q54;
- Reading/Listening chỉ cho `SINGLE_CHOICE`, `FILL_BLANK`,
  `TRUE_FALSE_NOT_GIVEN`; Writing chỉ `ESSAY`; Speaking chỉ `SPEAKING`;
- `AssessmentAuthoringCatalogService` trở thành facade code-owned một cấu hình
  KSH ngầm định, không query DB và không áp business cap cho Reading,
  Listening hoặc Speaking;
- validator và publisher đã chuyển sang kiểm tra bằng `PracticeContentRules`;
- xóa `AssessmentProgramPolicyService`, `ResolvedAssessmentPolicy`,
  `AssessmentDeliveryMode` cùng test policy DB cũ;
- xóa production service/controller/page/entity/repository/static assets/template
  và test của assessment governance;
- thay test governance cũ bằng bất biến `PracticeContentRulesTest` và
  `AssessmentAuthoringCatalogServiceTest`;
- evidence JDK 17:
  `PracticeContentRulesTest,AssessmentAuthoringCatalogServiceTest` xanh;
- test compilation toàn project xanh trong lượt focused test;
- chưa tạo migration, chưa commit hoặc push.

Step kế tiếp: thu gọn contract authoring/import/scoring/explanation và UI về đúng
năm question type; loại selector program/certificate/template khỏi luồng giáo
viên nhưng giữ nguyên Speaking và AI explanation Reading/Listening.

### Checkpoint 3 - Five-type authoring/import/scoring contract - COMPLETED

Ngày: `2026-07-13`

- canonical contract chỉ còn năm type: `SINGLE_CHOICE`,
  `TRUE_FALSE_NOT_GIVEN`, `FILL_BLANK`, `ESSAY`, `SPEAKING`;
- xóa enum, codec, answer/content shape, scoring branch, editor control,
  Excel sheet/column và learner-render branch chỉ phục vụ
  `MULTIPLE_CHOICE`, `MATCHING`, `ORDERING`, `TEXT_COMPLETION` hoặc
  `SHORT_TEXT`;
- Reading/Listening vẫn deterministic và AI chỉ giải thích từ key của giáo viên;
  provider payload không nhận learner answer hoặc raw private media reference;
- Writing template/import bắt buộc đúng bốn identity Q51, Q52, Q53, Q54;
  Speaking vẫn là first-class skill và không có business question cap;
- manual editor, Excel wizard và PDF wizard không còn selector
  program/certificate/category/template; PDF chỉ nhận file, Test đích và skill
  đích;
- Excel template chỉ còn năm question sheet, vẫn preview option A-H, shared
  group/question media, hiển thị dòng lỗi và tự bỏ dòng lỗi khi import;
- PDF preview và request thật dùng chung `PracticePdfAiPromptRules`; payload đã
  bỏ category detection/conflict và chỉ được tạo type hợp lệ theo target skill;
- xóa hai Spring service AI PDF không có production caller cùng test riêng của
  chúng: `PracticeDocumentAnalyzer` và `PracticePdfQuestionGenerator`;
- dashboard/revision UI không còn nhãn Program/Kịch bản; hiển thị một phạm vi
  KSH Practice ngầm định;
- evidence JDK 17:
  - `clean compile` xanh;
  - toàn project `test-compile` xanh;
  - focused gate 51/51 test xanh cho resolver, content rules, catalog facade,
    codec, scoring, R/L explanation, Excel partial import, PDF target skill,
    preview sanitization và UI contract;
  - static scan production không còn positive branch cho các question type đã
    loại; tên type cũ chỉ xuất hiện trong negative rule chống AI tạo sai;
- chưa tạo migration, chưa commit hoặc push.

Step kế tiếp: hoàn thiện squashed `V25` migration, xóa persistence metadata và
table governance dư thừa, chuẩn hóa dữ liệu question type trước khi thu gọn
entity và repository tương ứng.

### Checkpoint 4 - Squashed V25 và persistence single-scope - COMPLETED

Ngày: `2026-07-13`

- gộp V25-V29 vào final-state `V25__practice_single_scope_final.sql`; final tree
  không ship V26, V27, V28 hoặc V29;
- squashed V25 fail closed với dữ liệu ngoài năm question type hoặc Writing không ánh xạ
  chính xác Q51-Q54; chỉ hai fixture V16 nhận diện bằng title và không có attempt
  được chuẩn hóa có chủ đích để fresh install vẫn chạy được;
- chuẩn hóa legacy `MCQ/MCQ_SINGLE -> SINGLE_CHOICE`, `GAP_FILL -> FILL_BLANK`;
- hợp nhất draft material usage vào `practice_material_references` bằng
  `reference_key` và `reference_metadata_json`, giữ placement ở cấp
  section/group/question/option;
- xóa 14 bảng ngoài scope: 10 bảng assessment governance cùng
  `practice_governance_audit_events`, `practice_draft_asset_usages`,
  `practice_submissions`, `practice_ai_analysis_usage`;
- xóa program/category/template/profile/canonical/scoring-policy persistence khỏi
  entity, repository, service và snapshot/cache metadata tương ứng;
- giữ riêng live graph và immutable version graph; không gộp
  `practice_tests` với `practice_test_versions`;
- material access không còn reviewer đặc quyền; historical material chỉ mở qua
  attempt đúng version hoặc owner/collaborator, còn learner thường chỉ đọc
  version PUBLISHED hiện hành;
- Writing được đồng bộ end-to-end thành question number 51, 52, 53, 54 theo
  `essayTaskType`; normalizer, validator, publisher, editor và DB constraint dùng
  cùng invariant;
- fresh database gate `ksh_reduce_scope_gate` đã áp thành công đủ V1-V25 squashed;
  Hibernate `ddl-auto=validate` khởi động xanh bằng JDK 17/MySQL 9.6;
- schema assertion sau migrate: `removed_tables_remaining = 0`, seed chỉ còn
  `SINGLE_CHOICE`, `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN`, `ESSAY`, `SPEAKING`,
  Writing seed là `question_no=53`, `writing_task_type=Q53`;
- toàn project `test-compile` xanh; focused gate cho content rules, draft
  contract/validator, publisher/restore, authorization/collaboration/lifecycle và
  material reference/access/library/asset ownership xanh;
- không tạo commit hoặc push; `openspec-temp/` tiếp tục nằm ngoài phạm vi.

Step kế tiếp: đóng authorization matrix lecturer-only: chỉ owner và lecturer được
share mới có quyền theo grant; owner lock phải chặn collaborator; Student,
Head/Admin và unrelated lecturer không có content override ngầm.

### Checkpoint 5 - Lecturer-only ownership/collaboration boundary - COMPLETED

Ngày: `2026-07-13`

- toàn bộ `/practice/manage/**` được chặn ở SecurityConfig bằng exact role
  `LECTURER`; bảy controller authoring cũng dùng cùng
  `@PreAuthorize(Roles.PREAUTH_LECTURER)` để giữ defense in depth;
- `PracticeAuthorizationService` xác minh actor là đúng `Role.LECTURER`, active
  và không bị khóa trước khi đọc permission view; Student, Head và Admin vẫn bị
  từ chối ngay cả khi có direct permission grant;
- owner có quyền với học liệu của mình; Lecturer khác chỉ có quyền khi tồn tại
  grant còn hiệu lực và đúng action; owner lock chặn mọi mutation của
  collaborator nhưng vẫn cho phép quyền đọc đã cấp;
- chỉ tài khoản Lecturer active/unlocked mới có thể trở thành collaborator;
  Student, Head, Admin, tài khoản inactive/locked và owner tự share cho chính
  mình đều bị từ chối;
- xóa toàn bộ emergency override end-to-end: session TTL context,
  `overrideReason` request/service parameter, route override-edit, dashboard
  review Head/Admin, restore override form và trường `overrideUsed` giả trong
  authorization decision;
- đổi REST boundary còn hữu ích từ `/practice/manage/governance` thành
  `/practice/manage/collaboration`; không còn class/route mang tên governance;
- PDF payload preview của Lecturer chỉ còn dữ liệu redacted hướng người dùng;
  xóa tab system prompt/request JSON và nhánh debug Head/Admin vốn không còn
  route hợp lệ;
- evidence JDK 17:
  - toàn project `clean test-compile` xanh sau khi xóa override API;
  - focused authorization gate xanh cho authorization, collaboration,
    lifecycle, material access, immutable restore, PDF route security và static
    authoring UI/route contract;
  - static scan production/test không còn `overrideReason`,
    `PracticeOverrideContextService`, `canEmergencyOverride`,
    `PracticeGovernanceController` hoặc `/practice/manage/governance`;
- chưa commit hoặc push; `openspec-temp/` tiếp tục nằm ngoài phạm vi.

Step kế tiếp: chạy migration/fresh-schema gate và full regression suite bằng
JDK 17, sửa mọi regression còn lại trước stabilization cuối.

### Checkpoint 6 - Fresh schema và full JDK 17 regression - COMPLETED

Ngày: `2026-07-13`

- fresh database `ksh_reduce_scope_full` áp thành công toàn bộ migration V1-V25 squashed;
  Flyway kết thúc ở `version=29`, `success=1` và Hibernate schema validation
  khởi động xanh trên Java `17.0.19`/MySQL `9.6`;
- focused regression lần đầu phát hiện MySQL coi CHECK expression có kết quả
  `UNKNOWN` là hợp lệ, khiến `ESSAY` có `writing_task_type=NULL` lọt constraint;
  squashed V25 đã được sửa fail closed bằng `IS NOT NULL`, bắt buộc Q51-Q54 khớp chính
  xác `question_no`, đồng thời bắt non-ESSAY phải có `writing_task_type=NULL` ở
  cả live graph và immutable version graph;
- enum persistence `WritingTaskType` chỉ còn Q51, Q52, Q53, Q54; chuỗi
  `GENERAL` chỉ được giữ trong compatibility path của AI feedback cũ, không còn
  là metadata authoring/persistence hợp lệ;
- bỏ pre-parse Writing task trùng lặp trong publisher; `PracticeDraftValidator`
  là nguồn validation trước mutation và trả code ổn định
  `WRITING_TASK_UNSUPPORTED` cho task ngoài contract;
- cập nhật integration/repository/speaking fixtures theo contract mới; negative
  speaking test dùng một objective question hợp lệ ở DB để kiểm tra media chỉ
  nhận `SPEAKING`, thay vì tạo `ESSAY` vốn đã bị schema chặn;
- concurrency gate resume/republish vẫn xác minh publish bị block trong 250 ms;
  chỉ tăng thời gian chờ hoàn tất sau khi nhả lock từ 5 lên 15 giây để tránh
  false negative trên MySQL local chậm;
- schema assertions sau full gate:
  - `removed_tables_remaining=0` cho đủ 14 bảng ngoài scope;
  - live và immutable version graph chỉ còn `ESSAY`, `FILL_BLANK`,
    `SINGLE_CHOICE`, `SPEAKING`, `TRUE_FALSE_NOT_GIVEN`;
  - `override_permissions=0`;
- final full regression bằng JDK 17 xanh `1265/1265`, `0 failure`, `0 error`,
  `0 skipped`; focused publisher/validator và targeted concurrency rerun cũng
  xanh;
- `git diff --check` xanh; chưa commit hoặc push và `openspec-temp/` tiếp tục
  nằm ngoài phạm vi.

Step kế tiếp: stabilization cuối gồm static/dead-code audit, đối chiếu
controller-template-route, kiểm tra mojibake/emoji/raw storage exposure, browser
QA cho Lecturer và learner, rồi đồng bộ docs closure trước khi xin Phase 13 GO.

### Checkpoint 7A - Thu gọn collaboration và owner lock - COMPLETED

Ngày: `2026-07-13`

- collaboration chỉ còn một capability cố định ở cấp bộ đề: Lecturer được chia
  sẻ có thể đọc, sửa, xuất bản, khôi phục và quản lý material khi owner chưa
  khóa; không còn target polymorphic SET/DRAFT hoặc bốn cờ action riêng;
- squashed V25 xóa direct draft grants, owner/grantor metadata và action flags; bảng
  `practice_authoring_collaborations` chỉ còn identity set/collaborator cùng
  thời điểm grant/revoke, có FK và unique boundary ở cấp set;
- dashboard chia sẻ không còn form quyền chi tiết gây hiểu nhầm; UI diễn đạt rõ
  capability cố định và owner lock là công tắc chặn mọi mutation của Lecturer
  được chia sẻ;
- xóa controller collaboration REST trùng và các method/service/repository dead
  code không có route/UI sử dụng;
- bỏ khóa riêng trên `practice_drafts`: draft sửa một set kế thừa trực tiếp trạng
  thái khóa của set, còn draft mới chưa liên kết không có collaborator nên khóa
  riêng là ba cột dư thừa;
- squashed V25 xóa `owner_locked`, `locked_by`, `locked_at`, FK và index tương ứng khỏi
  `practice_drafts`; khóa cấp `practice_sets` vẫn được giữ nguyên;
- evidence JDK 17: collaboration/authorization/material/UI contract gate xanh
  25/25; lifecycle/authorization/draft service gate tiếp theo xanh; câu lệnh
  migration xóa draft lock áp dụng thành công trên schema V28 thử nghiệm;
- đối chiếu trực tiếp `information_schema.tables` làm rõ số bảng: Phase 11 có
  `91` base table + `1` view, Phase 12 V28 trước reduction có `96` base table +
  `1` view, còn hai schema đã áp squashed V25 (`ksh_reduce_scope_gate3` và
  `ksh_phase12_browser`) đều còn `82` base table + `1` view; số gần `100` trong
  IDE là schema V28/cũ hoặc metadata chưa refresh, không phải kết quả squashed V25;
- Flyway trên `ksh_phase12_browser` xác nhận migration `29 - practice single
  scope reduction` thành công; squashed V25 xóa ròng đủ `14` bảng governance/usage ngoài
  scope và các cột policy/override/draft-lock liên quan;
- `git diff --check` xanh; chưa commit hoặc push, `openspec-temp/` tiếp tục ngoài
  phạm vi.

Step kế tiếp: hoàn tất static route/template/UI scan, thay fallback nội dung cũ,
chạy fresh V1-V25 squashed và full regression cuối, sau đó browser QA Lecturer/learner.

### Checkpoint 7B - Fresh V1-V25 squashed và authoring route stabilization - COMPLETED

Ngày: `2026-07-13`

- tạo mới database disposable `ksh_reduce_scope_final` từ schema rỗng và chạy
  thành công toàn bộ Flyway V1-V25 squashed bằng JDK 17/MySQL 9.6; Flyway kết thúc ở
  `version=29` và Hibernate schema validation khởi động xanh;
- schema cuối còn `82` base table + `1` view, không còn bất kỳ bảng nào trong
  danh sách `14` bảng bị loại; đây là số liệu fresh migration, không phụ thuộc
  metadata cache của IDE;
- seed sau squashed V25 không còn chuỗi TOPIK trong set/test hoặc immutable version,
  không còn metadata `extended_types`; live graph và version graph cùng chỉ có
  đúng năm type `SINGLE_CHOICE`, `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN`, `ESSAY`,
  `SPEAKING`;
- fresh-schema focused gate bằng JDK 17 xanh `11/11`, gồm integration render
  test detail và toàn bộ static authoring UI contract;
- sửa breadcrumb test detail lấy title thật của test từ published view thay vì
  fallback tĩnh; controller và template dùng chung model attribute
  `testTitle`, có integration assertion chống regression;
- browser QA trên bản build trước đã xác minh Lecturer tạo được set/test/skill,
  editor Reading chỉ hiện đúng ba objective type, Excel/PDF nhận đúng
  `testNo/lessonCode`, revisions/materials mở đúng route; learner đi được
  library -> set -> test -> player -> submit -> result -> detail cho Reading và
  mở được player Listening, Writing Q53, Speaking;
- browser QA đồng thời phát hiện preview có thể hiện title cũ và publish có thể
  bỏ sót thay đổi cuối nếu autosave đang chờ; editor đã được sửa để đồng bộ
  title vào `DRAFT_DATA.document`, flush autosave trước preview và trước form
  publish, đồng thời dừng action và báo lỗi nếu save thất bại;
- focused UI contract sau fix xanh `10/10`; fresh integration + UI contract xanh
  `11/11`; chưa commit hoặc push và `openspec-temp/` tiếp tục ngoài phạm vi.

Step kế tiếp: dựng lại server từ code mới và browser-retest preview/publish ngay
sau khi sửa dữ liệu, sau đó chạy full suite JDK 17, static/dead-route scan và
đồng bộ tài liệu closure. Checkpoint 7B chưa phải Phase 12R closure.

### Checkpoint 7C - Authoring flush và learner test progress - COMPLETED

Ngày: `2026-07-13`

- browser retest trên server JDK 17/MySQL squashed V25 xác minh thay đổi title ngay trước
  preview xuất hiện đúng trong preview, thay đổi title ngay trước publish được
  autosave trước khi publish và published set/test dùng dữ liệu mới;
- learner đi được set mới qua `set -> test -> section player -> submit -> result`;
  câu Reading tiếng Hàn thật được chấm đúng `1/1` và result route tải thành công;
- phát hiện learner library đang hardcode tiến độ theo kỹ năng thành `0/40`,
  `0/20`, `0/10`, `0/8`; các số này không phải số test thật và tử số luôn bằng
  `0`, nên UI không phản ánh dữ liệu attempt;
- controller hiện truyền `setTestProgress` theo từng set; service tải theo lô
  test, section và attempt final từ DB, sau đó tính `completedTests/totalTests`;
- một test chỉ hoàn thành khi mọi section hiện có của test đều có ít nhất một
  attempt `SUBMITTED` hoặc `GRADED`; test rỗng không được tính hoàn thành và
  duplicate attempt không làm tăng số test;
- thẻ bộ đề QA có một test hiện đúng `0/1 bài luyện` thay vì `0/40`; JDK 17
  focused unit/UI/integration gate xanh, trong đó test hai section giữ `0/1`
  sau khi chỉ hoàn thành Reading và chuyển thành `1/1` sau khi Listening cũng
  hoàn tất;
- theo yêu cầu trực tiếp của user ngày `2026-07-13`, browser QA dừng sau khi
  submit/result thành công và không chạy thêm vòng xác nhận card sau submit;
  trạng thái `1/1` được khóa bằng integration test với MySQL squashed V25. Việc dừng này
  không được ghi sai thành một full browser closure gate;
- chưa commit hoặc push; `openspec-temp/` tiếp tục nằm ngoài phạm vi.

Step kế tiếp: không chạy thêm browser QA; chạy full regression JDK 17 và
static/dead-route/migration audit, sửa regression nếu có rồi cập nhật closure
docs. Phase 13 vẫn chưa được mở.

### Checkpoint 7D - Learner explanation option labels - COMPLETED

Ngày: `2026-07-13`

- đóng toàn bộ app listener trong dải `8080-8090`; giữ MySQL và cổng nội bộ IDE
  vì không phải KSH web server;
- phát hiện typed Reading/Listening explanation cố ý dùng stable option ID để
  kiểm chứng answer spec, nhưng result template lại hiển thị trực tiếp
  `optionKey`, khiến UUID nội bộ xuất hiện ở phần phân tích đáp án sai;
- giữ nguyên stable ID trong provider/cache contract và thêm presentation
  adapter tại `ReadingListeningExplanationService`: đáp án trả ra learner UI
  được đổi theo `optionLabelMode` thành `A/B/C...` hoặc `1/2/3...`;
- adapter đồng thời thêm `correctAnswer` hiển thị từ answer spec; option ID lạ
  không khớp question content bị bỏ, không rò sang UI;
- thêm regression test với option ID dạng UUID, khóa yêu cầu đáp án đúng hiển
  thị `A`, đáp án sai hiển thị `B` và JSON presentation không còn UUID;
- focused JDK 17 gate `ReadingListeningTypedExplanationTest` cùng
  `ReadingListeningExplanationServiceTest` xanh;
- full suite đang chạy trước thay đổi được dừng chủ động nên không được tính là
  failure của code; full suite cuối sẽ chạy lại từ đầu sau checkpoint này.

Step kế tiếp: chạy lại full regression JDK 17 trên fresh squashed V25, sau đó hoàn tất
static/dead-route/migration audit và closure docs; không mở lại browser QA.

### Checkpoint 7E - Static route và presentation stabilization - COMPLETED

Ngày: `2026-07-13`

- đối chiếu toàn bộ URL `/practice/manage/**` được gọi từ dashboard, editor,
  Excel, PDF workspace, material library và revision history với controller
  mapping tương ứng; không phát hiện primary action nào trỏ tới route đã xóa;
- nút tạo thủ công trong PDF import wizard từng đi qua compatibility redirect
  `/practice/manage/manual`; UI đã chuyển sang canonical route
  `/practice/manage/create`, còn redirect cũ chỉ được giữ cho bookmark/client
  cũ và có integration test riêng;
- thêm UI contract khóa import wizard không được gọi lại route legacy; focused
  JDK 17 gate cho authoring UI cùng typed/legacy Reading-Listening explanation
  xanh sau thay đổi;
- typed explanation fallback ở mọi nhánh, kể cả thiếu approved evidence, đều đi
  qua presentation adapter; stable option ID tiếp tục tồn tại trong cache và
  provider contract nhưng không còn đường hiển thị trực tiếp ra learner UI;
- `git diff --check` xanh; static scan production practice không còn route
  assessment governance, mojibake, emoji product icon hoặc UI tham chiếu
  `/practice/manage/manual`; `MULTIPLE_CHOICE`/`MATCHING` chỉ còn câu cấm trong
  AI prompt và một hằng trạng thái không liên quan của Speaking reuse policy;
- kiểm tra cuối dải `8080-8090` không còn listener; MySQL và cổng nội bộ IDE
  không bị can thiệp vì không phải KSH web server;
- chưa commit hoặc push; `openspec-temp/` tiếp tục nằm ngoài phạm vi.

Step kế tiếp: chạy full regression JDK 17 từ đầu trên fresh squashed V25, kiểm tra lại
schema evidence và đồng bộ closure docs. Không chạy thêm browser QA theo yêu
cầu trực tiếp của user.

### Checkpoint 7F - Full-suite concurrency timeout triage - COMPLETED

Ngày: `2026-07-13`

- full JDK 17 suite đầu tiên sau presentation/static fixes chạy đủ `1269` test:
  `0 failure`, `1 error`, `0 skipped`; error duy nhất là timeout tại
  `startWinsAgainstRepublishAndVersionLockedAttemptAllowsNewVersion` khi chờ
  republish hoàn tất;
- test vẫn vượt qua assertion `250 ms` chứng minh transaction republish bị khóa
  trong lúc transaction tạo attempt chưa commit; không có dấu hiệu publish vượt
  khóa hoặc attempt bị pin sai published version;
- xác định hai test concurrency tương đương dùng timeout hoàn tất không nhất
  quán: resume/republish đã dùng `15s`, start/republish còn `5s`; trên full-suite
  MySQL local, publish hợp lệ cần hơn 5 giây nhưng vẫn hoàn tất;
- đồng bộ timeout hoàn tất start/republish thành `15s`, không nới assertion
  blocking `250 ms`; đây là test-budget stabilization, không thay đổi runtime
  locking hay transaction behavior;
- focused JDK 17 concurrency test xanh `3/3` lượt liên tiếp trên squashed V25; thời gian
  publish sau khi nhả khóa dao động khoảng `4-12s` và mọi lượt vẫn tạo version
  mới, giữ attempt ở version cũ đúng contract;
- chưa commit hoặc push; `openspec-temp/` tiếp tục nằm ngoài phạm vi.

Step kế tiếp: chạy lại toàn bộ full suite JDK 17 từ đầu. Chỉ kết quả lần chạy
sau timeout fix mới được dùng để đóng regression gate.

### Checkpoint 7G - Practice-only closure gate - COMPLETED

Ngày: `2026-07-13`

Phạm vi theo yêu cầu trực tiếp của user: chỉ kiểm tra code liên quan feature
`/practice`, không chạy browser QA trong lượt này.

- JDK 17 practice-focused suite trên fresh MySQL schema
  `ksh_reduce_scope_practice_gate` xanh: `860` test, `0 failure`, `0 error`,
  `0 skipped`;
- Flyway sạch từ V1 tới squashed V25; schema cuối là `82` base tables + `1` view;
- final squash verification sau khi đổi tên migration đã chạy trên fresh schema
  `ksh_reduce_scope_squashed_v25_final`: `PracticeQuestionRepositoryTest` xanh
  `6/6`, Flyway kết thúc ở `25 - practice single scope final`, bảng base/view
  vẫn là `82/1`;
- các bảng generic governance/program/profile đã loại bỏ không còn tồn tại:
  `REMOVED_TABLES_STILL_PRESENT = 0`;
- canonical `question_type` live/version chỉ còn:
  `ESSAY,FILL_BLANK,SINGLE_CHOICE,SPEAKING,TRUE_FALSE_NOT_GIVEN`;
- `git diff --check` xanh;
- static scan production practice không còn template/controller route
  `assessment-governance`; reference còn lại chỉ nằm trong test contract để
  khóa việc template/js cũ không được tái xuất hiện;
- không phát hiện mojibake/emoji product icon trong
  `src/main/resources/templates/practice` và
  `src/main/java/com/ksh/features/practice`;
- `MULTIPLE_CHOICE`/`MATCHING` không còn là runtime authoring type: chỉ còn
  trong squashed V25 để dọn dữ liệu legacy, trong prompt PDF để cấm AI tạo lại, và trong
  reuse-policy text của Speaking không liên quan question type;
- dải app ports `8080-8090` không còn listener;
- regression phần giải thích đáp án đã khóa lỗi rò UUID option ID: stable IDs
  vẫn dùng cho provider/cache, còn learner UI chỉ nhận nhãn A/B hoặc 1/2;
- browser QA và full product manual UAT không được tính là green trong checkpoint
  này vì user đã yêu cầu bỏ browser QA. Phase 13 vẫn cần GO riêng.

Kết luận checkpoint: `PHASE_12R_PRACTICE_CODE_GATE = CLOSED_GREEN`.
`PHASE_12R_BROWSER_QA = SKIPPED_BY_USER_FOR_THIS_CHECKPOINT`.

## 11. Phase 13 boundary sau reduction

Phase 13 chỉ thiết kế UI/UX cho:

- một library KSH, không có certificate/program/TOPIK-level filter;
- lựa chọn làm toàn test hoặc một skill dựa trên section thật có trong test;
- player R/L single choice, fill blank và true/false/not-given;
- Writing Q51-Q54 input;
- Speaking prompt/recorder/text fallback/player/result;
- result, explanation, transcript/passage evidence và progress theo skill;
- lecturer manual/Excel authoring and simple sharing/history/material views.

Research IELTS/TOEIC/PREP vẫn là interaction evidence, không phải product scope,
không tạo certificate selector, question type ngoài năm type đã chốt hay program
governance.

## 12. Definition of Done

`PHASE_12R_SINGLE_SCOPE_REDUCTION_GATE` chỉ đóng khi:

- product không còn certificate/program/category/TOPIK-level selector hoặc
  governance path;
- question tables chỉ còn một `question_type` discriminator với năm giá trị đã
  chốt; không còn `canonical_question_type`, `MULTIPLE_CHOICE` hoặc `MATCHING`;
- R/L chỉ cho ba objective type đúng skill và deterministic scoring;
- Reading, Listening, Speaking không bị giới hạn số câu bởi certificate policy;
- Writing chỉ Q51-Q54;
- Speaking giữ nguyên nhưng không phụ thuộc generic program/profile governance;
- 10 assessment governance tables cùng 4 bảng bắt buộc khác đã được drop khỏi
  schema cuối;
- program/template/profile/governance/override code và UI không còn dead route;
- lecturer collaboration hoạt động không cần Head/Admin;
- immutable history/material/attempt trace vẫn xanh;
- R/L AI explanation và Writing evaluator regression xanh;
- Speaking media/evaluator regression xanh;
- migration và automated practice stabilization xanh; browser QA chỉ được tính
  khi user yêu cầu chạy lại trong một gate riêng;
- docs canonical nhất quán;
- user duyệt closure và cấp Phase 13 GO riêng.

## 13. Verdict

- `SINGLE_IMPLICIT_SCOPE = APPROVED_DIRECTION`
- `GENERIC_PROGRAM_GOVERNANCE = REMOVE`
- `QUESTION_TYPES = SINGLE_CHOICE_FILL_BLANK_TFNG_ESSAY_SPEAKING_ONLY`
- `SPEAKING_PHASE_13_SCOPE = KEEP`
- `LECTURER_TO_LECTURER_COLLABORATION = KEEP_SIMPLIFIED`
- `READING_LISTENING_AI_EXPLANATION = KEEP`
- `TOPIK_WRITING_Q51_Q54 = KEEP`
- `PDF_AI_AUTHORING = DEFER_RECOMMENDED_SEPARATE_DECISION`
- `PHASE_13_GO = NO_GO_UNTIL_12R_CLOSED_GREEN`
