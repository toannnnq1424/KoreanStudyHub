# Practice Phase 12 Continuation and Closure Plan

Ngày lập: 2026-07-12

Nhánh checkpoint: `feature/practice-reduce-scope`

Baseline đã push: `85b65e1`

Trạng thái: `SUPERSEDED_BY_SINGLE_SCOPE_REDUCTION`

Gate cha: `PHASE_12_CLOSURE_STABILIZATION_GATE = OPEN`

> Scope decision 2026-07-12: kế hoạch generic program/certificate/scenario trong
> tài liệu này không còn là target triển khai. Nó được giữ làm audit history.
> Nguồn canonical mới là
> `docs/PRACTICE_SINGLE_SCOPE_REDUCTION_AUDIT.md` và gate
> `PHASE_12R_SINGLE_SCOPE_REDUCTION_GATE`. Các phần security, immutable
> history và material authorization vẫn phải được giữ khi giảm scope.

## 1. Mục đích và quyết định điều hành

Tài liệu này là kế hoạch tiếp tục Phase 12 sau post-commit audit. Nó bổ sung,
không thay thế, `CODEX_PRACTICE_WORKFLOW.md` và
`PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`.

Baseline Phase 12 đã có authorization, collaboration, append-only restore,
assessment governance API, governed material identity và durable lifecycle
task. Full suite 1293/1293 trên JDK 26/MySQL V27 vẫn là bằng chứng hợp lệ cho
baseline đó. Tuy nhiên, audit sau commit xác nhận thêm hai lỗi P0 chưa được bao
phủ và một nhóm UI/route contract chưa hoàn chỉnh. Vì vậy:

- Phase 12 chưa được đóng;
- Phase 13 vẫn `NO_GO`;
- không merge `main` và không product/live rollout;
- phải triển khai continuation slice này, chạy lại automated gate, JDK 17 gate
  và browser closure QA trước khi đổi trạng thái;
- Cloudflare R2 và full Manual UAT không bị kéo từ Phase 15 về Phase 12.

Tên gate tiếp tục:

`PHASE_12_POST_COMMIT_SECURITY_GOVERNANCE_AND_UI_CLOSURE`

Gate này hiện chỉ còn giá trị lịch sử. Không tiếp tục mở rộng governance UI hoặc
multi-certificate model từ tài liệu này.

Update 2026-07-13: single-scope reduction đã thay thế phần lớn nội dung
generic governance bên dưới. Practice-code gate mới được ghi ở
`docs/PRACTICE_SINGLE_SCOPE_REDUCTION_AUDIT.md`: JDK 17 practice-focused suite
`860/860`, fresh schema squashed V25 `82` base tables + `1` view, removed
governance tables count `0`. Final squash verification sau rename dùng
`V25__practice_single_scope_final.sql`, fresh schema
`ksh_reduce_scope_squashed_v25_final`, Flyway `25 - practice single scope final`
và `PracticeQuestionRepositoryTest` `6/6`. Browser QA không được chạy trong
checkpoint này theo yêu cầu trực tiếp của user, nên tài liệu này không được dùng
để claim browser/product closure xanh.

## 2. Trả lời contract lưu trữ program và kịch bản

### 2.1 Những gì đã được lưu trong database

Program/certificate và các setting không chỉ tồn tại trong Java hoặc JSON trả
về UI. V25-V27 đã lưu chúng ở database theo hai lớp: policy chuẩn hóa và config
versioned.

| Khái niệm | Persistence hiện có | Ý nghĩa |
|---|---|---|
| Program/certificate ổn định | `assessment_programs` | Mã ổn định như `TOPIK`, `CUSTOM`; giữ con trỏ active version |
| Phiên bản program | `assessment_program_versions` | Tên hiển thị, version number, trạng thái, ngôn ngữ mặc định |
| Kỹ năng của program | `assessment_program_skill_policies` | Hiện lưu bật/tắt R/L/W/S và legacy delivery mode; target UI bỏ delivery selector |
| Dạng câu hỏi | `assessment_question_type_policies` | Bật/tắt type, scoring policy và exact scoring/prompt/rubric profile IDs |
| Kịch bản/mẫu đề ổn định | `assessment_exam_templates` | Code, display name, category compatibility, enabled và active version |
| Phiên bản kịch bản | `assessment_exam_template_versions` | Immutable `config_json`, version number, status và actor activation |
| Scoring profile | `assessment_scoring_profiles` | Immutable version và `config_json` |
| Prompt profile | `assessment_prompt_profiles` | Skill/task, compatibility adapter và `system_rules` |
| Rubric profile | `assessment_rubric_profiles` | Skill/task và immutable `config_json` |
| Nội dung đã chọn policy | draft/set/published-version columns | Khóa program code/version và template code để nội dung cũ truy vết được |

Template `config_json` hiện chứa các setting như `maxTests`, kỹ năng được bật,
`durationMinutes`, `defaultPoints`, `pointsEditable`, `maxQuestions`,
`excelImportEnabled`, question types, scoring policy choices và min/max option.
Các profile được policy tham chiếu bằng ID cụ thể, không chỉ bằng code dễ drift.

### 2.2 Một certificate chỉ có một kịch bản hoạt động tại một thời điểm

Quyết định sản phẩm ngày 2026-07-12 thay thế giả định cũ rằng một program có
nhiều template/scenario cùng `enabled`. Contract mục tiêu là:

```text
Certificate/program root: TOPIK_II
  Active program policy version: vN
  Active exam scenario: one scenario root
    Active scenario version: vM
    Reading route
    Listening route
    Writing route
      Q51 task route
      Q52 task route
      Q53 task route
      Q54 task route
```

Không hard-code số lượng version lịch sử. Tuy nhiên, tại một thời điểm mỗi
certificate chỉ có đúng một scenario được learner/authoring catalog coi là
hoạt động. Thay đổi cấu hình tạo một immutable scenario version mới; rollback là
chuyển active pointer về một version hợp lệ, không sửa hoặc xóa version cũ.
`Tạo kịch bản` chỉ có ý nghĩa khi certificate chưa có scenario root. Sau đó UI
phải dùng hành động `Tạo phiên bản mới`, tránh hai thao tác có semantics trùng
nhau.

Scenario root/version khác nếu còn trong DB chỉ được ở trạng thái draft,
inactive hoặc archived; chúng không được đồng thời xuất hiện như nhiều kịch bản
đang bật của cùng certificate. Practice set của giáo viên là nội dung, không
được dùng để mô phỏng nhiều active scenario governance roots.

Schema hiện có đủ bảng để giữ lịch sử, nhưng target contract chưa end-to-end:

- fresh local seed hiện chỉ có 2 program roots, 2 program versions, 3 template
  roots, 3 template versions, 8 skill policies và 16 question-type policies;
- current compatibility model còn đặt `TOPIK_I` và `TOPIK_II` dưới program root
  `TOPIK`; target phải coi certificate learner-facing như TOPIK I, TOPIK II,
  KLAT/KLATT, KLPT, OPIc và TOPIK Speaking là program roots rõ ràng khi chúng có
  policy/scenario độc lập;
- API hiện chỉ tạo version cho program/template root đã tồn tại; chưa có API/UI
  tạo mới program root hoặc template/scenario root;
- chưa có active-scenario pointer và atomic invariant bảo đảm một certificate
  chỉ có một scenario hoạt động;
- category vẫn còn là compatibility metadata ở một số UI/path, chưa được thay
  hoàn toàn bằng program + scenario/template context.

Migration tương lai phải forward-only từ V28, dự kiến squashed V25 tại
thời điểm triển khai. Không sửa V28 và không rewrite program/template identity
đã khóa trong old draft, published version, attempt hoặc result.

### 2.3 Certificate -> skill -> task route là contract đã hủy

Mỗi kỹ năng được bật trong policy tự động có route luyện riêng. Không hiển thị
dropdown kỹ thuật `Theo kỹ năng / Full test / Cả hai` và không yêu cầu Admin bật
thêm một cờ `Cho luyện riêng kỹ năng`:

```text
/practice/programs/{programCode}
/practice/programs/{programCode}/skills/{skillCode}
/practice/programs/{programCode}/skills/{skillCode}/tasks/{taskCode}
/practice/programs/{programCode}/full-test
```

Các route attempt/result hiện có vẫn giữ canonical set/test/attempt identity;
program/skill/task context phải được carry rõ, không tạo đường tắt làm mất
immutable version lock.

`Bao gồm trong đề thi đầy đủ` không phải skill policy. Nó là thành phần cấu trúc
của active scenario version: scenario xác định kỹ năng nào được ghép, thứ tự,
timer và transition khi learner chọn làm full test. Một certificate không có
full-test mode vẫn có các skill route của những kỹ năng được bật.

Mỗi skill có task routes động theo certificate:

- TOPIK II Writing có thể seed `Q51`, `Q52`, `Q53`, `Q54`;
- certificate khác được cấu hình task code/label riêng, không kế thừa hard-code
  TOPIK;
- mỗi task route giữ exact question type, max count và exact scoring, prompt,
  rubric profile version IDs;
- system rules/config của profile phải xem được bởi Admin/Head trong governance
  UI, có trạng thái runtime support rõ ràng; Lecturer chỉ thấy profile nghiệp vụ
  được phép dùng, không thấy secret/provider diagnostics;
- form quản trị là field có nhãn và validation. JSON chỉ là representation được
  backend tạo sau khi lưu, không phải input bắt người dùng nhập.

Editor giáo viên phải chọn certificate/program trước, sau đó chỉ thấy active
scenario, skills, task types và giới hạn hợp lệ của certificate đó. Learner
library và progress cũng phải có certificate context rõ ràng. API/UI phải trả
lỗi nghiệp vụ thân thiện; tuyệt đối không render raw HTML error page hoặc stack
detail trong banner.

Contract này đã bị supersede bởi single-scope reduction. Không tạo các route
generic program/certificate/task và không mở rộng learner UI đa chứng chỉ ở
Phase 13. Target không còn `topik_level`; route dùng set/test/skill identity thật
và năm question type cố định trong canonical audit mới.

## 3. P0 mới: program activation có thể làm hỏng authoring catalog

### 3.1 Hiện trạng đã xác minh

`assessment_exam_templates.program_version_id` hiện gắn template root trực
tiếp vào một program version. Ngược lại,
`assessment_exam_template_versions` chỉ giữ `template_code` và `config_json`,
không giữ program version mà config đã được validate cùng.

`AssessmentGovernanceService.activateProgramVersion()` làm program version cũ
thành `INACTIVE` và bật version mới. Nó không clone, retarget hoặc activate một
bộ template version tương thích. Sau đó
`AssessmentAuthoringCatalogService.toPolicy()` từ chối mọi template root đang
tham chiếu program version không còn `ACTIVE`.

Tình huống lỗi:

```text
TOPIK v1 ACTIVE
TOPIK_I/TOPIK_II template roots -> TOPIK v1
activate TOPIK v2
TOPIK v1 becomes INACTIVE
template roots still -> TOPIK v1
authoring catalog throws instead of returning usable templates
```

Đây là P0 vì activation hợp lệ của Admin/Head có thể làm manual/Excel/PDF
authoring catalog lỗi toàn cục.

### 3.2 Invariant bắt buộc

- Program root là identity ổn định của certificate.
- Program version là immutable policy snapshot.
- Template/scenario root là identity ổn định thuộc program root.
- Mỗi template version phải biết chính xác program version mà nó tương thích.
- Một authoring release chỉ được expose khi cả program version và toàn bộ
  enabled template versions trong release tương thích và active.
- Activation phải atomic hoặc fail closed; không có trạng thái giữa chừng làm
  catalog 500.
- Nội dung cũ giữ exact program/template/profile identity đã publish.

### 3.3 Hướng migration được ưu tiên

Implementation phải review live schema trước khi chốt tên cột, nhưng hướng dữ
liệu ưu tiên là:

1. Gắn stable template root với program root thay vì chỉ với một program version.
2. Gắn mỗi immutable template version với exact program version dùng để validate.
3. Historical plan before reduce-scope: backfill quan hệ từ dữ liệu V26/V27 bằng
   forward migration. Superseded 2026-07-13: final reduce-scope branch squashes
   V25-V29 into one V25 before commit.
4. Thay activation rời rạc bằng validated activation bundle, hoặc ít nhất chặn
   program activation nếu chưa có template version tương thích cho mọi template
   đang bật.
5. Catalog phải bỏ qua template bị disable có chủ đích, nhưng fail closed với
   active release sai invariant; không được để một template hỏng làm mất toàn bộ
   catalog mà không có diagnostics/audit.

Không chọn giải pháp chỉ update hàng loạt
`assessment_exam_templates.program_version_id` nếu việc đó làm mất bằng chứng
template version cũ đã được validate với program version nào.

## 4. Đánh giá các góp ý bổ sung

| Góp ý | Kết quả đối chiếu | Mức thực tế | Quyết định |
|---|---|---|---|
| Historical material có thể lộ | Chính xác. Fallback GLOBAL/CLASS chỉ kiểm tra set hiện tại, không kiểm tra reference thuộc learner-visible current version | P0 / HIGH security | Sửa ngay trong Phase 12 continuation |
| Program activation/template linkage | Không có trong góp ý ban đầu nhưng audit local xác nhận | P0 / HIGH availability + governance integrity | Sửa ngay cùng 12C continuation |
| Emergency override thiếu ở route chính | Chính xác. Lifecycle/restore có reason, nhưng create-edit, autosave, publish, PDF/Excel attach và material path chưa end-to-end | P1 / MEDIUM-HIGH functional | Hoàn thiện hoặc thu hẹp contract trước closure; ưu tiên hoàn thiện |
| Material controller quảng cáo Range nhưng luôn trả 200 | Chính xác | P1 / MEDIUM player compatibility | Reuse byte-range implementation đã có ở Speaking controller trước Phase 13 |
| Full suite chạy JDK 26 thay vì baseline JDK 17 | Chính xác về evidence gap, không tự chứng minh có runtime bug | P1 / MEDIUM closure evidence | Chạy full suite bằng JDK 17 thật; máy hiện chỉ có JDK 26 nên cần runtime JDK 17 |
| Profile activation có thể có nhiều ACTIVE version | Một phần đúng; có thể là policy hợp lệ nếu ACTIVE nghĩa là approved | P2 / decision required | Chốt invariant trước khi mở UI, không tự coi là bug |
| `max(version)+1` profile có race | Chính xác; unique constraint chặn corruption nhưng request đồng thời có thể fail | P2 / LOW-MEDIUM | Sửa cùng governance UI hoặc Phase 15 hardening, không để 500 mơ hồ |
| Mọi asset `ARCHIVED` đều phải deny | Quá rộng. Lifecycle hiện dùng `ARCHIVED` để giữ object còn durable reference; old attempt vẫn có thể cần đọc | P1 semantics | Deny unverified/DELETION_PENDING/DELETED; retained ARCHIVED tuân theo exact version authorization |
| DB-managed prompt chưa thay hết adapter | Chính xác và repo đã thừa nhận | P1 rollout debt, không chặn Phase 13 generic UI | Chặn activation/rollout profile chưa runtime-supported; evaluator migration + calibration ở Phase 15 |
| R2 chưa triển khai | Chính xác | Release blocker, không phải Phase 12 non-audio blocker | Giữ Phase 15E; không thêm SDK/secret giả |
| History diff/shared list UI còn thiếu | Chính xác một phần | P2 UX | Minimum usable UI ở Phase 12; diff/polish/scale ở Phase 13/15 |
| Browser closure QA là bắt buộc | Chính xác theo canonical workflow | Phase gate | Chạy cuối continuation; không thay thế Phase 15 Manual UAT |

## 5. 12G.1 - Current-version material authorization

### Policy bắt buộc

Với mỗi `PUBLISHED_VERSION` material reference:

1. Owner hoặc collaborator có action `READ` trên set/draft phù hợp được đọc.
2. Head/Admin reviewer có `MEDIA_REVIEW`, được đọc và phải có audit event.
3. Learner có attempt khóa đúng `publishedVersionId` được đọc historical
   material của attempt đó.
4. Learner không có matching attempt chỉ được đọc material thuộc current
   learner-visible published version.
5. Current GLOBAL version cho authenticated learner đọc.
6. Current CLASS version chỉ cho active class enrollee đọc.
7. Historical version không có matching attempt bị deny, dù set hiện tại vẫn
   PUBLISHED/GLOBAL/CLASS.
8. Unverified, deleted, deletion-scheduled hoặc unrelated private material bị
   deny trước khi mở storage stream.

### Implementation boundary

- Tạo một resolver dùng chung cho `current learner-visible published version`;
  không suy ra chỉ từ `PracticeSet.status`.
- Resolver phải dùng cùng semantics với player/catalog/attempt lock.
- Ưu tiên repository query latest `PUBLISHED` version hiện có, nhưng phải chốt
  archive/unpublish semantics và không duplicate logic ở controller.
- Authorization chạy trước `storageService.load()` và trước range metadata read.
- Reviewer access tiếp tục audit actor/asset/owner/reason/context.

### Test bắt buộc

- current GLOBAL material -> authenticated learner allowed;
- current CLASS material -> active enrollee allowed, unrelated learner denied;
- old material + matching attempt -> allowed;
- old material + no matching attempt -> denied;
- old material + attempt của user khác -> denied;
- owner/collaborator -> allowed theo grant;
- reviewer -> allowed và audit tồn tại;
- private draft/unrelated/forged ID -> denied;
- unverified/DELETION_PENDING/DELETED -> denied;
- retained ARCHIVED material có durable reference -> chỉ allowed theo đúng
  current/historical version policy, không tự động public hoặc tự động deny;
- denial xảy ra trước storage stream open.

## 6. 12G.2 - Program/scenario governance completion

### Backend cần bổ sung

- read/list/detail API cho program roots, versions, template/scenario roots,
  template versions, skill/question policies và profile references;
- create/archive program root theo permission, với immutable code và safe
  duplicate validation;
- create scenario root khi certificate chưa có scenario; sau đó chỉ tạo immutable
  scenario versions;
- one-active-scenario pointer và atomic uniqueness invariant theo certificate;
- clone program version và active scenario version để Admin/Head không phải nhập
  lại toàn bộ cấu hình;
- validate program version + selected active scenario version như một release;
- activate/rollback atomically, có actor, reason, before/after IDs và audit;
- optimistic/concurrent activation guard;
- catalog resolution không được dùng mixed active identities;
- old draft/set/published version giữ exact identity, không auto-upgrade.

Future task-policy persistence phải biểu diễn task routes động theo program
version và skill, gồm task code/label, question type, max count, display order và
exact scoring/prompt/rubric profile IDs. Không suy diễn Q51-Q54 từ tên câu hỏi
hoặc Java constant.

### UI Admin/Head tối thiểu trong Phase 12

Không cần visual redesign cấp Phase 13, nhưng phải có usable management surface:

1. Danh sách certificate/program: code, tên, active policy version, active
   scenario/version, trạng thái, last activation và validation status.
2. Program detail: overview, enabled skills, question types, limits, task routes,
   scoring/prompt/rubric links và version history. Mỗi skill bật tự có route
   luyện riêng; không còn delivery-mode dropdown.
3. Scenario/version history: chỉ một active scenario tại một thời điểm, các
   version cũ giữ immutable và có validation/audit rõ ràng.
4. Scenario editor: thành phần full test, thứ tự skill, max tests/questions,
   timers, points, option limits, Excel/PDF enablement và profile refs. Mỗi field
   có label/description nghiệp vụ; không dùng dãy input số vô danh.
5. Release preview: diff program policy + scenarios + profiles trước activation.
6. Activate/rollback confirmation: reason bắt buộc, atomic result, audit link.
7. Profile management: version list, task route, runtime-support state, exact
   references, system rules/config được trình bày bằng form/readable sections và
   approved/active semantics được giải thích rõ bằng trạng thái nghiệp vụ.

UI không được cho Lecturer xem system rules, provider config, secret hoặc raw
internal JSON. Admin/Head được xem và quản trị approved system rules nhưng cũng
không phải nhập JSON; raw diagnostics chỉ nằm trong một debug surface được phân
quyền riêng nếu thực sự cần.

### Invariant profile

Trước implementation phải chọn một trong hai contract và ghi test:

- `ACTIVE = approved`: nhiều version cùng code có thể active, policy tham chiếu
  exact ID; UI không gọi một version là “current duy nhất”; hoặc
- `ACTIVE = selected current`: chỉ một version mỗi code active; activation phải
  deactivate version trước atomically và root phải có active pointer.

Khuyến nghị hiện tại là `ACTIVE = approved` vì question policy đã giữ exact
profile ID. Một program/scenario release chọn exact version; không cần mutate
profile cũ. Nếu product muốn “current mặc định”, thêm con trỏ riêng thay vì đổi
nghĩa của trạng thái approved.

## 7. 12G.3 - Emergency override end-to-end

### Gap hiện tại

Backend có decision `overrideReason`, nhưng primary authoring route không truyền
đủ reason. Feature đang fail closed, nên đây không phải privilege escalation;
nhưng Head/Admin không thể hoàn thành emergency workflow đã được tài liệu tuyên
bố hỗ trợ.

### Action matrix cần audit

- create draft from another owner's published set;
- open editor and autosave;
- manual mutation;
- PDF/Excel import attachment;
- asset upload/promote/delete;
- publish/republish;
- restore;
- archive/unarchive;
- lock/unlock;
- collaborator management.

Mỗi action phải hoặc nhận/revalidate override context, hoặc ghi rõ server-side
không hỗ trợ override. Không được chỉ mở nút trên UI.

### UX/contract ưu tiên

- Head/Admin bắt đầu cross-owner mutation qua một confirmation modal có target,
  action scope và reason bắt buộc.
- Không hỏi reason lại ở từng autosave.
- Server tạo/bind một short-lived audited override context cho actor + target +
  allowed actions; mọi request tiếp theo vẫn revalidate role, permission, owner
  lock, target và expiry.
- Không truyền reason nhạy cảm trong query string.
- Publish/restore/archive vẫn ghi immutable action event riêng dù dùng cùng
  context.
- Closing editor, expiry hoặc target/version change làm context hết hiệu lực.

Nếu schema review cho thấy context server-side quá lớn cho closure, fallback
được phép là reason giữ trong page session và gửi qua request body mỗi mutation;
không localStorage, không URL và audit phải deduplicate autosave noise.

## 8. 12G.4 - HTTP byte Range cho material

`PracticeMaterialController` hiện gửi `Accept-Ranges: bytes` nhưng không xử lý
`Range`. Phase 12 continuation phải tái sử dụng/extract implementation đã được
test ở Speaking playback thay vì viết parser thứ hai.

Acceptance:

- GET không Range -> `200`, đúng MIME, content length và private cache policy;
- valid single Range -> `206`, đúng `Content-Range`, `Content-Length`;
- open-ended và suffix range được hỗ trợ;
- malformed/multiple/unsatisfiable range -> `416` với contract nhất quán;
- `HEAD` chỉ thêm nếu player/client thực sự cần;
- authorization/verification xảy ra trước storage stream;
- không đọc toàn file vào heap để trả một đoạn;
- image/document vẫn hoạt động với GET bình thường.

## 9. 12G.5 - UI còn thiếu và routing theo phase

### Đã có nhưng chưa hoàn chỉnh

- Manage dashboard có `Của tôi`, `Được chia sẻ`, edit, share email,
  lock/unlock và archive/unarchive.
- Revision page liệt kê immutable published versions và restore version được
  chọn.
- Assessment governance có REST mutation API.
- Material có authenticated content endpoint và lifecycle backend.

### Bắt buộc hoàn thiện trong Phase 12

- Admin/Head program/scenario/profile management UI tối thiểu ở mục 6;
- collaborator list với grant hiện tại, edit grant và revoke, không chỉ form
  share email;
- Head/Admin emergency override modal/flow cho action được hỗ trợ;
- per-set history entry từ editor/dashboard, filter đúng set và hiển thị mọi
  immutable published version;
- sửa editor menu `Lịch sử chỉnh sửa` đang là placeholder alert;
- history phải nói rõ: publish 10 lần tạo v1-v10; restore v3 tạo v11. Autosave
  draft 10 lần không tự biến thành 10 published revisions;
- material views tối thiểu `Của tôi`/`Được chia sẻ`, owner, usage/reference,
  visibility/status và safe preview;
- authoring chooser hiển thị program + scenario/template đã resolve; category
  chỉ là compatibility metadata và không là primary concept;
- navigation và role denial rõ: Student/unrelated Lecturer không thấy hoặc
  không truy cập governance routes, nhưng server vẫn là nguồn authorization.

### Chuyển Phase 13

- visual redesign, responsive/accessibility và performance cho catalog lớn;
- rich revision diff theo section/group/question;
- advanced search/filter, bulk action và large-list virtualization;
- learner library/player/result/progress UX theo research checkpoint;
- polish toàn bộ information architecture sau khi backend contract đã khóa.

### Chuyển Phase 15

- full Manual UAT với database có thể drop/reseed theo kế hoạch đã duyệt;
- dữ liệu thật cho TOPIK và các certificate khác theo config riêng;
- cross-browser/device/load/migration rehearsal;
- Cloudflare R2 adapter, bucket/domain/credential, local-to-R2 migration,
  multi-node reconciliation và virus-scanning decision;
- provider-safe Writing/Speaking evaluator migration/calibration;
- production/live GO/NO-GO.

R2 không chặn Phase 13 UI development, nhưng tiếp tục chặn production Speaking
audio và multi-node media rollout.

## 10. 12G.6 - Test và closure gate

### Automated/focused

- material current/historical authorization matrix;
- program/template compatible activation bundle và rollback;
- one-active-scenario invariant theo certificate và immutable version history;
- root/version CRUD denial cho Lecturer/Student;
- concurrent program/template/profile version creation/activation;
- override action matrix và expiry/target mismatch;
- Range 200/206/416 + deny-before-stream;
- per-set history/restore vN -> vN+1;
- old attempt/result/material remains bound to old version;
- controller/template route contract và rendered-link tests;
- fresh V1-next migration + representative V28 upgrade; không rewrite V28;
- full suite dưới project baseline JDK 17;
- optional diagnostic run dưới JDK 26 không thay thế JDK 17 evidence.

### Browser closure QA bắt buộc trước Phase 13

1. Admin/Head tạo program/scenario version, validate và activate; hệ thống chỉ
   cho một active scenario mỗi certificate và lecturer thấy đúng catalog mới.
2. Lecturer owner tạo set, manual/Excel/PDF import, preview và publish.
3. Collaborator xem shared list, edit/publish khi unlocked; bị deny khi owner
   lock.
4. Head/Admin override bằng reason, action thành công và audit tồn tại.
5. Owner mở per-set history, restore version bất kỳ, nhận version mới; old
   learner result không đổi.
6. Learner GLOBAL/CLASS chạy library -> set -> test -> attempt -> submit ->
   result/detail; historical material chỉ mở cho matching attempt.
7. Audio seek/range hoạt động; private draft preview đúng quyền.
8. Student không vào manage/governance; unrelated Lecturer không đọc/mutate
   private content.
9. Không valid-route 4xx/5xx, broken button, wrong redirect, missing content,
   console/network error, mojibake, emoji product icon hoặc incoherent overlap.
10. Dừng toàn bộ server tạm sau gate.

Browser closure này dùng deterministic fixture để xác minh route/state. Nó
không thay thế Phase 15 Manual UAT, dữ liệu certificate chất lượng cao,
cross-browser/device hoặc release rehearsal.

## 11. Thứ tự triển khai đề xuất

1. `12G.1` sửa historical material authorization và test P0.
2. `12G.2` chốt/migrate program-template version invariant và atomic activation.
3. Đóng checkpoint security/history/material hiện tại; future governance redesign
   chỉ bắt đầu sau implementation GO riêng trên contract certificate -> skill ->
   task và one-active-scenario.
4. `12G.3` nối emergency override xuyên primary authoring routes.
5. `12G.4` thêm Range bằng shared implementation.
6. Hoàn thiện collaborator/material/per-set-history UI và thay placeholder.
7. Focused security/governance/UI contract tests.
8. Fresh/upgrade migration rehearsal và full suite JDK 17.
9. Controlled browser closure QA, sửa lỗi phát hiện, rerun affected tests và
   full suite cuối.
10. Cập nhật ba tài liệu canonical, ghi evidence, sau đó mới xin Phase 13 GO.

Không nên làm UI governance trước khi sửa program-template activation invariant;
nếu không UI sẽ cung cấp một nút hợp lệ có thể làm hỏng catalog.

## 12. Definition of Done

Gate chỉ được `CLOSED_GREEN` khi đồng thời:

- historical material IDOR đã có negative regression tests và pass;
- program version + scenario/template version activation nhất quán, atomic và
  có integration test bảo đảm tối đa một active scenario mỗi certificate;
- Admin/Head có usable governance UI, không chỉ raw REST endpoint;
- mỗi enabled skill có route riêng; full-test composition thuộc active scenario,
  task route/profile được resolve theo certificate và không hard-code TOPIK;
- governance UI không yêu cầu nhập JSON và không render raw HTML error response;
- collaborator, override, material và per-set history flows nối end-to-end;
- material Range contract pass;
- full suite pass dưới JDK 17;
- browser closure QA pass cho owner/collaborator/Head/Admin/Student;
- migration forward-only, old attempts/results/history không đổi;
- không gọi provider hoặc R2 trong automated gate;
- temporary server/process đã dừng;
- workflow/blueprint/evidence được cập nhật nhất quán;
- user duyệt Phase 12 closure và cấp Phase 13 GO riêng.

## 13. Evidence pointers

Các pointer này là điểm bắt đầu cho implementation audit; line number có thể
dịch chuyển nên phải đọc lại source tại HEAD thực tế:

- historical program/profile schema và seed từng nằm trong
  `src/main/resources/db/migration/V25__assessment_program_configuration.sql`,
  nhưng đã bị supersede trong branch reduce-scope;
- final single-scope reduction schema hiện nằm trong:
  `src/main/resources/db/migration/V25__practice_single_scope_final.sql`;
- historical program/template/profile create/activate logic từng nằm trong
  `src/main/java/com/ksh/features/practice/assessment/AssessmentGovernanceService.java`,
  nhưng service này đã bị xóa khỏi target runtime;
- catalog inactive-version rejection:
  `src/main/java/com/ksh/features/practice/assessment/AssessmentAuthoringCatalogService.java`;
- governance REST mutation surface:
  `src/main/java/com/ksh/features/practice/manage/controller/AssessmentGovernanceController.java`;
- historical/current material authorization:
  `src/main/java/com/ksh/features/practice/manage/service/PracticeMaterialAccessService.java`;
- material response without real Range handling:
  `src/main/java/com/ksh/features/practice/controller/PracticeMaterialController.java`;
- reusable Speaking Range behavior:
  `src/main/java/com/ksh/features/practice/controller/PracticeSpeakingMediaPlaybackController.java`;
- revision listing/restore routes:
  `src/main/java/com/ksh/features/practice/manage/controller/PracticeManageController.java`;
- revision UI:
  `src/main/resources/templates/practice/manage/revisions.html`;
- editor history placeholder:
  `src/main/resources/templates/practice/manage/editor.html`;
- current material authorization tests:
  `src/test/java/com/ksh/features/practice/manage/service/PracticeMaterialAccessServiceTest.java`;
- current governance tests:
  `src/test/java/com/ksh/features/practice/assessment/AssessmentGovernanceServiceTest.java`.
