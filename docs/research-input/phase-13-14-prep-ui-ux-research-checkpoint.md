# Phase 13-14 PREP UI/UX Research Checkpoint

> Trạng thái: `RESEARCH_CHECKPOINT_V2_REDUCED_SCOPE`
> Ngày ghi nhận: `2026-07-11`
> Phạm vi: tham khảo UI/UX cho KSH từ 96 ảnh chụp do người dùng cung cấp và phiên trải nghiệm trực tiếp trên tài khoản PREP đã đăng nhập.
> Tài liệu này là checkpoint trung gian. Phần TOEIC và một số trạng thái player sâu vẫn tiếp tục được khảo sát sau khi checkpoint được lưu.
> Cập nhật reduced-scope: `2026-07-13`. Ảnh/contact sheet và route đã quan sát là nguồn chuẩn cho **bằng chứng PREP tại thời điểm ghi nhận**, không phải nguồn chuẩn cho domain, schema, quyền hoặc route của KSH.
>
> **Roadmap supersession (`2026-07-22`):** các heading lịch sử ở cuối checkpoint
> từng gán `13E = Result summary` và `13F = Evidence-based result detail` đã bị
> supersede. Contract hiện hành là `13D = Result overview + immutable explanation
> lifecycle`, `13E = đúng ba skill-native Result Detail screens`, `13F =
> Progress/profile aggregation + operational recovery`; xem
> `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`,
> `CODEX_PRACTICE_WORKFLOW.md` và Phase 13E live log. Nội dung PREP dưới đây chỉ
> còn là bằng chứng/pattern IA, không phải lệnh thực thi hay taxonomy IELTS cho
> KSH.

## 1. Mục tiêu và ranh giới

Tài liệu này lưu lại toàn bộ context đã thu thập trước khi tiếp tục khảo sát website, nhằm:

- tránh mất các chi tiết UI/UX quan trọng sau một phiên Computer Use dài;
- phân biệt rõ dữ kiện đã quan sát với đề xuất thiết kế cho KSH;
- mở rộng định hướng Phase 13 và Phase 14 theo capability, không sao chép route của PREP;
- xác định phần nào đã khớp với hướng code hiện tại và có thể tái sử dụng;
- tạo backlog đủ cụ thể để triển khai, kiểm thử và UAT sau này.

### 1.1 Thứ tự nguồn chuẩn sau khi giảm scope

- Nguồn chuẩn sản phẩm/domain KSH là `docs/PRACTICE_SINGLE_SCOPE_REDUCTION_AUDIT.md`, `CODEX_PRACTICE_WORKFLOW.md` và Section 11 của `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`.
- Nguồn chuẩn bằng chứng PREP là các ảnh/contact sheet trong DOCX đi kèm và route ghi nhận trong tài liệu này. Chúng chỉ trả lời câu hỏi "PREP đã hiển thị và chuyển trạng thái như thế nào".
- Các nhãn, URL hoặc selector `program`, `certificate`, IELTS, TOEIC xuất hiện trong ảnh/route là dữ liệu quan sát bên ngoài. Chúng **không** tạo yêu cầu program/certificate selector hay multi-certificate routing cho KSH.
- Phase 13/14 không được khôi phục `program/certificate/scenario/template/profile governance`, Head/Admin approval hoặc generic policy tables đã bị loại khỏi reduced scope.
- Contract KSH hiện hành là một scope luyện tập KSH ngầm định với `Set -> Test -> Skill -> Group -> Question`, năm loại `SINGLE_CHOICE`, `FILL_BLANK`, `TRUE_FALSE_NOT_GIVEN`, `ESSAY`, `SPEAKING` và cộng tác trực tiếp giữa giảng viên.

### 1.2 Ranh giới sở hữu trí tuệ

PREP chỉ được dùng làm nguồn tham khảo hành vi và mô hình tương tác. KSH không được sao chép:

- logo, tên thương hiệu, mascot, hình minh họa hoặc icon độc quyền;
- nội dung đề thi, đáp án, transcript, bài mẫu hoặc feedback nguyên văn;
- CSS, mã nguồn, API nội bộ hoặc asset tải từ hệ thống PREP;
- route chỉ để trông giống PREP khi route hiện tại của KSH đã mô tả domain tốt hơn.

KSH có thể học và triển khai lại các pattern phổ quát như:

- phân cấp `set -> test -> skill/section -> group -> question -> attempt`;
- chọn chế độ luyện tập hoặc mô phỏng thi;
- player theo từng kỹ năng;
- result summary và evidence-based result detail;
- progress dashboard;
- report-an-error có context tự động.

Mascot và tên thương hiệu cần được xem là theme slot có thể thay thế. Không dùng mascot PREP làm placeholder trong production.

### 1.3 Ba mức độ chắc chắn

| Nhãn | Ý nghĩa |
|---|---|
| `OBSERVED` | Đã nhìn thấy trực tiếp trong ảnh hoặc phiên website đã đăng nhập. |
| `INFERRED` | Suy luận hợp lý từ nhiều màn hình, cần xác nhận bằng code hoặc dữ liệu. |
| `PENDING` | Chưa khảo sát đủ; không được dùng làm tiêu chí chốt implementation. |

## 2. Nguồn bằng chứng

### 2.1 Bộ ảnh người dùng cung cấp

Đã đọc đủ `96` file PNG trong `C:\Users\Admin\Desktop\prep`, chia thành `14` nhóm:

| STT | Thư mục ảnh | Số ảnh | Phạm vi |
|---:|---|---:|---|
| 1 | `0.trang home practice` | 4 | Home/library, bộ lọc, selector nhiều chứng chỉ của PREP, card và sidebar. |
| 2 | `1.trước khi làm bài` | 3 | Test detail, skill list, trạng thái attempt và mode selection. |
| 3 | `3.1.làm bài của readinglistening` | 10 | Listening/Reading player, timer, audio, navigator, note/highlight. |
| 4 | `3.2. làm bài của writing` | 6 | Writing mode, task selection, prompt/editor, word count, note. |
| 5 | `3.3. làm bài của speaking` | 15 | Speaking mode, mic preflight, think/record/process/complete. |
| 6 | `4.1.result của r-l` | 2 | Result summary của Reading/Listening. |
| 7 | `4.2. result của writing` | 8 | Writing score, tiêu chí và band descriptor. |
| 8 | `4.3 result của speaking` | 2 | Speaking score, tiêu chí và performance theo part. |
| 9 | `5.1.result detail của listening` | 7 | Audio, transcript, đáp án và giải thích bằng evidence. |
| 10 | `5.2.result detail của reading` | 5 | Passage, answer review, evidence/translation/reasoning. |
| 11 | `5.3 result detail của writing` | 11 | Overall, strengths, needs, upgraded answer, sample. |
| 12 | `5.4 result detail của speaking` | 12 | Transcript/audio, grammar, vocabulary, pronunciation, word stress. |
| 13 | `6. report an error ở góc` | 2 | Entry point và modal báo lỗi. |
| 14 | `7. progress` | 9 | Learning profile, score trend, history, heatmap và question-type performance. |
|  | **Tổng** | **96** |  |

Đã tạo và kiểm tra 14 contact sheet tại:

`C:\Users\Admin\.codex\visualizations\2026\07\10\019f4d1d-37e3-73f0-bfba-2ef2977f5607\prep-contact-sheets`

Mỗi contact sheet tương ứng một nhóm ở bảng trên, theo thứ tự `01.png` đến `14.png`. Bộ contact sheet bao phủ đủ 96 ảnh nguồn và được dùng làm phụ lục cho DOCX đi kèm.

### 2.2 Route đã quan sát trực tiếp

Các entry route và route con đã được mở trong phiên Chrome đăng nhập:

- `https://app.prepedu.com/en/test-practice`
- `https://app.prepedu.com/en/learning-profile`
- `https://app.prepedu.com/en/test-practice/ielts/261`
- `https://app.prepedu.com/en/test-practice/ielts/261/test-detail/16864`
- Reading/Listening result và result detail sinh từ test `16864`.
- `https://app.prepedu.com/en/test-practice/ielts/345/test-detail/20336`
- `https://app.prepedu.com/en/test-practice/virtual-writing-room/ielts/result/6a439fafb007ffc67e0db681?test_set=345&test_id=20336`
- `https://app.prepedu.com/en/test-practice/virtual-writing-room/ielts/result/6a439fafb007ffc67e0db681?main_tab_name=detail&test_set=345&test_id=20336`
- `https://app.prepedu.com/en/test-practice/ielts/401/test-detail/23862`
- `https://app.prepedu.com/en/test-practice/virtual-speaking-room/ielts/result/6a426c47cca29b6792066f25?test_set=401&test_id=23862`
- `https://app.prepedu.com/en/test-practice/virtual-speaking-room/result/detail/6a426c47cca29b6792066f25?test_set=401&test_id=23862`
- Listening select-mode, preflight/player và exit confirmation của test `16864`.
- Reading select-mode, preflight/player và exit confirmation của test `16864`.

Các route động/instance route đã khôi phục từ lịch sử phiên và ảnh nguồn:

- Listening select mode: `https://app.prepedu.com/en/test-practice/ielts/listening/select-mode?test_set=261&test_id=16864&skill_id=1&questionOnly=1&submission_id=6a4271e359e4654de804e24a` (ảnh còn cho thấy query version bổ sung bị Chrome rút gọn).
- Listening player: `https://app.prepedu.com/en/test-practice/trt/6a439e91935aca895c03b5ec?mode=2&skill_id=1&test_set=261&test_id=16864&questionOnly=1&submission_id=6a4271e359e4654de804e24a` (query version phía sau bị rút gọn trong ảnh).
- Reading player: `https://app.prepedu.com/en/test-practice/trt/6a47e1a8488112205202c377/?mode=2&skill_id=2&test_set=261&test_id=16864&questionOnly=1&submission_id=6a4271e359e4654de804e24a` (query version phía sau bị rút gọn trong ảnh).
- Listening result: `https://app.prepedu.com/en/test-practice/ielts/result?test-set=261&test-id=16864&skill-id=1&submission-skill-id=6a439e91935aca895c03b5ec`.
- Reading result: `https://app.prepedu.com/en/test-practice/ielts/result?test-set=261&test-id=16864&skill-id=2&submission-skill-id=6a47e1a8488112205202c377`.
- Writing topic/preflight: `https://app.prepedu.com/en/test-practice/virtual-writing-room/ielts/topics/16866?test_set=261&submission_skill_id=`.
- Writing player: `https://app.prepedu.com/en/test-practice/trt/6a47e45bef58f21afb01680e?test_set=261&submission_id=6a47e45bef58f21afb01680d&skill_id=4&test_id=16866`.
- Earlier Writing result represented in the supplied images: `https://app.prepedu.com/en/test-practice/virtual-writing-room/ielts/result/6a438d1c8397cdcc7f01ee69?test_set=261&test_id=16865`.
- Speaking topic/preflight: `https://app.prepedu.com/en/test-practice/virtual-speaking-room/ielts/topics/16866?test_set=261&submission_skill_id=`.
- Speaking player: `https://app.prepedu.com/en/test-practice/trt/6a47e56ffacb0de0420dc9d7?test_set=261&submission_id=6a47e45bef58f21afb01680d&mode=2&skill_id=3&test_id=16866`.
- TOEIC Speaking result: `https://app.prepedu.com/en/test-practice/virtual-speaking-room/toeic/result/6a49c7fd61351d266b093b02?test_id=20234&test_set=343`.

Reading/Listening `View details` là state nội bộ trên cùng route `ielts/result`; thao tác này không đổi URL. Tương tự, các mục `Results`, `General statistics`, `Your work` và `Criteria-based feedback` trong TOEIC Speaking dùng cùng result route.

`/en/learning-profile` cũng là một route dùng chung của PREP nhưng nội dung thay đổi theo selector bên ngoài của họ. Quan sát trực tiếp khi PREP đang chọn TOEIC cho thấy dashboard chuyển sang các nhóm `Listening - Reading Score`, `Writing - Speaking score`, tổng thời lượng `42 mins` và `Total tests = 1`; ảnh IELTS trước đó hiển thị dữ liệu IELTS khác trên chính URL này. Đây là bằng chứng về stateful filtering của PREP, không phải yêu cầu program/certificate cho KSH.

Không nên sao chép các URL trên vào KSH. Chúng chỉ giúp xác định screen responsibility và trạng thái điều hướng.

### 2.3 Giới hạn của Computer Use

Computer Use cung cấp ảnh màn hình và accessibility tree, nhưng không truyền âm thanh hệ thống về cho agent. Vì vậy:

- đã kiểm tra được player, duration, seek/play control, transcript, `You spoke`, `Sample by Prep`, IPA và marker phát âm;
- chưa thể khẳng định bằng thính giác rằng transcript Speaking bắt đúng từng từ;
- chưa thể so sánh trực tiếp audio người học với audio mẫu;
- mọi kết luận về độ chính xác STT hoặc phát âm phải được gắn `PENDING_AUDIO_UAT`;
- Phase 15 cần human listening UAT với tai nghe, mic thật, nhiều accent và môi trường nhiễu.

## 3. Tóm tắt điều hành

### 3.1 Kết luận chính

1. PREP mạnh nhất không nằm ở màu xanh hoặc mascot, mà ở việc biểu diễn rõ trạng thái của một hành trình dài: chọn đề, chọn kỹ năng, chọn mode, làm bài, chấm, xem evidence và quay lại luyện điểm yếu.
2. Kiến trúc route hiện tại của KSH đã khớp khá tốt với domain này: `set -> test -> mode -> attempt -> result -> result/detail`. Không có lý do đổi route để giống PREP.
3. KSH đã có nền view model và màn hình riêng cho Reading/Listening và Writing/Speaking. Phase 13 nên làm giàu component shell thay vì viết lại luồng domain hoặc dựng lại governance đã loại bỏ.
4. Result detail nên là sản phẩm học tập, không chỉ là trang điểm. Mọi nhận xét AI cần có anchor vào bài làm, evidence, cách sửa và trạng thái confidence.
5. Learning profile nên nối trực tiếp từ insight sang hành động `Practice more`, không dừng ở biểu đồ.
6. Phase 14 nên auto-attach context kỹ thuật và học thuật khi báo lỗi; không bắt người học tự mô tả lại route, attempt, question hay content version.
7. Không sao chép các lỗi UX quan sát được ở PREP, đặc biệt là microcopy sai tab và việc một attempt chưa nộp làm mất điểm gần nhất trên score card.

### 3.2 Nguyên tắc thiết kế nên giữ cho KSH

- **Evidence before decoration:** ưu tiên bằng chứng học tập trước mascot/illustration.
- **State is a first-class UI concern:** `NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED`, `SCORING`, `SCORED`, `FAILED`, `STALE` phải nhìn thấy và có action đúng.
- **Latest submitted is not latest attempt:** một attempt đang làm không được xóa hoặc thay thế điểm submitted gần nhất.
- **Overview leads to detail:** summary giúp định hướng; detail giải thích và cho hành động cụ thể.
- **Skill-native player:** dùng chung shell nhưng Reading, Listening, Writing và Speaking phải có interaction riêng.
- **AI is advisory:** điểm và feedback AI cần disclaimer, confidence và đường báo lỗi.
- **Capability-based routes:** URL phản ánh domain KSH, không phản ánh cách PREP đặt tên phòng ảo.
- **Responsive by design:** desktop split view, tablet stacked panes và mobile task-focused view phải được thiết kế như ba trạng thái có chủ đích.

## 4. Journey và state model quan sát được

```text
Practice library
  -> Set/collection
  -> Test
  -> Skill/section
  -> Attempt history / Start / Continue / Retake
  -> Mode selection
  -> Device or content preflight
  -> Player
  -> Submit / auto-submit / save draft / exit
  -> Scoring / AI processing
  -> Result summary
  -> Result detail
  -> Learning profile / Practice more
  -> Report issue when needed
```

### 4.1 Trạng thái attempt tối thiểu đề xuất cho KSH

| Trạng thái | Ý nghĩa UI | Action chính |
|---|---|---|
| `NOT_STARTED` | Chưa có attempt. | `Start` |
| `IN_PROGRESS` | Có draft hoặc phiên chưa hoàn thành. | `Continue` |
| `SUBMITTED` | Đã nộp, chưa chấm xong. | `View status` |
| `SCORING` | Hệ thống/AI đang xử lý. | `Refresh later` hoặc auto-poll có giới hạn |
| `SCORED` | Có kết quả dùng được. | `View result`, `View details`, `Retake` |
| `PARTIAL` | Có điểm một phần hoặc feedback không đầy đủ. | `View available result`, `Retry evaluation` nếu runtime cho phép |
| `FAILED` | Chấm thất bại hoặc dữ liệu không đủ. | Giải thích rõ và đưa action an toàn |
| `STALE` | Feedback không còn khớp content/media/version hiện tại. | Không hiển thị như kết quả mới; yêu cầu chấm lại có kiểm soát |

### 4.2 Tách điểm và attempt

Score card nên hiển thị đồng thời, không ghi đè lẫn nhau:

- `Latest submitted score`;
- `Best score` nếu product cần;
- `In-progress attempt` và thời điểm cập nhật;
- `Attempt count`;
- `Score pending` nếu đã nộp nhưng chưa chấm.

Trong phiên quan sát, sau khi mở một attempt mới rồi thoát, score card của PREP chuyển từ điểm `1.0` sang dấu `-` vì dùng attempt gần nhất dù attempt đó chưa nộp. KSH nên tránh hành vi này.

## 5. Phân tích từng surface

## 5.1 Practice library

### `OBSERVED`

- PREP có program switcher gồm IELTS, TOEIC, HSK, VSTEP, IELTS Junior, PrepTalk và HSK 3.0. Đây là quan sát bên ngoài, không phải backlog KSH.
- Có hai hướng vào nội dung: `Practice by type` và `Explore library`.
- Bộ lọc gồm skill, access `All/Pro/Free`, test type và search.
- Card có bookmark/collection, badge như `HOT` hoặc `Forecast`, trạng thái đang làm.
- Sidebar có profile, quota cho Writing/Speaking, guide, progress radar và collection.
- Phiên live IELTS hiển thị 28 test set và quota Writing/Speaking `10/10`.

### Bài học áp dụng

- KSH không thêm program/certificate selector. Header chỉ cần làm rõ bộ đề, bài test, kỹ năng và trạng thái người học đang xem.
- Search, filter và pagination phải là server-backed contract, không chỉ lọc DOM.
- Card cần status/action rõ hơn decoration.
- Với catalog lớn, initial payload phải bị chặn theo page size; không render hàng nghìn card.
- `Practice by type` nếu được giữ phải dùng đúng năm loại câu hỏi của reduced scope; không tạo taxonomy quản trị hoặc generic policy table mới.
- Sidebar desktop có thể chuyển thành summary drawer hoặc sticky action bar trên mobile.

### Không nên sao chép

- badge marketing không có ý nghĩa học tập;
- mascot chiếm diện tích lớn hơn action;
- quota hiển thị mà không giải thích reset/consumption;
- filter không phản ánh trong URL, khiến refresh/back khó đoán.

## 5.2 Set detail và test detail

### `OBSERVED`

- Một set chứa nhiều test theo timeline.
- Một test chứa nhiều skill, mỗi skill có trạng thái, attempt history và action riêng.
- Test detail có score summary bên phải và lịch sử theo skill bên trái.
- Có `View all` để mở thêm attempt cũ.
- Action thay đổi theo trạng thái: `Start`, `Continue`, `Retake`, `View details`.
- Score card ghi rằng kết quả được cập nhật theo câu trả lời gần nhất.

### Mức khớp với KSH hiện tại

KSH đã có các route learner hiện hành cần được giữ ổn định:

- `/practice/sets/{setId}`;
- `/practice/sets/{setId}/tests/{testId}`;
- `/practice/sets/{setId}/tests/{testId}/mode`;
- `/practice/attempts/{attemptId}`;
- `/practice/attempts/{attemptId}/result`;
- `/practice/attempts/{attemptId}/result/detail`.

Đây là mapping domain tốt và nên giữ nguyên. Phase 13 chỉ cần nâng chất lượng:

- skill status card;
- attempt timeline;
- score semantics;
- deep link và back-link;
- empty/loading/error state;
- responsive layout.

### Acceptance direction

- Không giả định `one set = one test`.
- Không giả định `one test = one skill`.
- `Continue` luôn trỏ đúng attempt đang làm.
- `View details` luôn trỏ đúng submitted attempt.
- `Retake` không làm mất điểm submitted hiện tại.
- Back-link không dựa vào default test hoặc ID sai cấp.

## 5.3 Mode selection

### `OBSERVED`

Reading/Listening có hai mode:

- Practice mode: không giới hạn thời gian; cho save/resume; Listening cho rewind/replay; không hỗ trợ highlight/note.
- Test mode: có timer và auto-submit; mô phỏng thi; không save draft; có highlight/note.
- Cả hai mode đều hứa hẹn grading và explanation chi tiết.

### Bài học áp dụng

- Mode card phải mô tả **khác biệt hành vi**, không chỉ tên.
- Hành vi mode thuộc test/attempt contract hiện hành: timer, autosubmit, save draft, playback và highlight/note.
- Không dựng lại program configuration hoặc generic policy table. Rule cố định của reduced scope phải nằm ở boundary domain phù hợp và được UI diễn đạt rõ.
- Bắt đầu attempt chỉ sau khi mode được chọn và preflight cần thiết đã qua.
- Nếu tạo attempt ngay khi bấm `Start`, UI phải nói rõ draft sẽ xuất hiện trong lịch sử.

## 5.4 Listening player

### `OBSERVED`

- Header có timer, title, `Save draft`, `Submit` và overflow menu.
- Audio control sticky gồm rewind 10s, play, forward 10s, seek, elapsed/duration, volume/settings.
- Nội dung theo `PART 1..4`, mỗi part có answered/total.
- Bottom navigator luôn hiển thị section progress và next/previous.
- Có `Pin material` và Teacher Bee entry.
- Start modal mô tả 4 section, 40 câu, 45 phút.
- Exit modal hỏi xác nhận khi bài chưa hoàn thành.

### Bài học áp dụng

- Audio state cần không phụ thuộc scroll.
- Duration, buffering, unavailable và retry phải là state hiển thị được.
- Navigator cần phân biệt unanswered, answered, flagged/current.
- `Submit` phải có pre-submit summary và xác nhận ở thời điểm action.
- Practice mode và exam mode phải tuân thủ rule playback đã định nghĩa cho từng mode.
- Không tự động phát âm thanh trước một user gesture rõ ràng.

## 5.5 Reading player

### `OBSERVED`

- Desktop dùng split view: passage bên trái, questions bên phải.
- Mỗi pane cuộn độc lập, có divider.
- Header có timer, save draft, submit và overflow.
- Bottom navigator chia `READING PASSAGE 1..3`, hiển thị answered/total.
- Start modal mô tả 3 section, 40 câu, 60 phút.
- Exit confirmation giống Listening.

### Bài học áp dụng

- Split ratio cần có giới hạn tối thiểu và có thể resize bằng keyboard.
- Mobile không nên ép hai cột hẹp; dùng tab/sheet `Passage | Questions` và giữ vị trí đọc.
- Anchor từ câu hỏi sang evidence trong passage nên dùng cùng contract với result detail.
- Highlight/note cần gắn version và range ổn định, không chỉ lưu raw DOM offset.

## 5.6 Writing player

### `OBSERVED`

- Có preview yêu cầu task và guideline trước khi vào phòng viết.
- Có chọn full test, Task 1 hoặc Task 2.
- Desktop split prompt/answer.
- Editor hiển thị word count và task navigation.
- Có suggestion/guideline và side note.
- Có save/continue và exit warning.

### Bài học áp dụng

- Word count là trợ giúp, không phải validation duy nhất.
- Draft autosave cần trạng thái `Saving/Saved/Failed/Offline`.
- Prompt version phải được snapshot cùng attempt.
- Nếu AI feedback yêu cầu nhiều task, result phải tách score và feedback theo task trước khi tổng hợp.
- Không để AI upgraded answer thay thế bài gốc; phải giữ song song và có nhãn rõ.

## 5.7 Speaking player

### `OBSERVED`

- Có chọn Practice/Test và part cụ thể.
- Có mic quality preflight và hướng dẫn.
- Flow gồm think timer, record timer/waveform, processing và complete.
- Có exit warning.
- Result detail có playback, transcript và per-word feedback.

### Bài học áp dụng

- Consent, permission, device selection, input level và test recording phải xảy ra trước attempt chính.
- Recording state machine cần explicit: `IDLE -> REQUESTING_PERMISSION -> READY -> THINKING -> RECORDING -> UPLOADING -> PROCESSING -> COMPLETE/FAILED`.
- Reload, tab close, mic revoke, network drop và retry phải có contract.
- Playback trong result/detail phải dùng media access đã bảo vệ của KSH.
- Không tuyên bố đánh giá phát âm chính xác khi audio/STT confidence thấp.

### `PENDING_AUDIO_UAT`

- độ đúng transcript theo từng accent;
- độ ổn định khi nói nhanh, ngập ngừng hoặc có tiếng ồn;
- alignment giữa timestamp, word marker và playback;
- chất lượng audio mẫu và so sánh phoneme.

## 5.8 Result summary của Reading/Listening

### `OBSERVED`

- Score lớn ở đầu trang.
- Correct/incorrect count.
- Bảng performance theo question type: total, correct, incorrect, accuracy.
- CTA `View details`.

### Bài học áp dụng

- Summary cần trả lời: điểm bao nhiêu, sai ở đâu, nên luyện gì tiếp.
- Accuracy nên tránh chia 0 và cần nêu sample size.
- Breakdown chỉ dùng năm loại câu hỏi của reduced scope; không mở rộng thành taxonomy/governance tổng quát trong Phase 13.
- CTA từ row yếu sang library filter `Practice more` tạo vòng lặp học tập.

## 5.9 Listening result detail

### `OBSERVED`

- Transcript có speaker và timestamp.
- Audio player đồng bộ với transcript.
- Câu trả lời hiển thị inline với wrong/correct state.
- Explanation mở rộng có evidence, từ khóa và bản dịch song ngữ.
- Bottom navigator chuyển part/question.
- Có Teacher Bee sidebar tùy chọn.

### Contract đề xuất

Mỗi answer review cần tối thiểu:

- `questionId`, `questionNo`, `questionType`;
- `learnerAnswer`, `answerKey`, `isCorrect`;
- `audioStartMs`, `audioEndMs` nếu có;
- `transcriptSegments[]`;
- `evidenceSpans[]`;
- `explanation`, `translation`, `confidence`;
- `contentVersionId`, `attemptId`.

Không dùng AI explanation để thay thế answer key chính thức.

## 5.10 Reading result detail

### `OBSERVED`

- Passage bên trái, answer review bên phải.
- Question explanation có bảng đối chiếu câu hỏi với evidence region.
- Có translation và reasoning.
- TRUE/FALSE/NOT GIVEN được giải thích bằng logic loại trừ.
- Có unpin/zoom và navigator.

### Bài học áp dụng

- Evidence anchor là dữ liệu, không phải HTML trang trí.
- Mỗi anchor phải survive reload và content versioning.
- Explanation nên tách `evidence`, `reasoning`, `answer` để UI có thể collapse độc lập.
- AI chỉ được bổ sung reasoning; answer key và source passage là nguồn chuẩn.

## 5.11 Writing result summary

### `OBSERVED`

- PREP dùng score card mang phong cách chứng nhận với overall score và task score.
- Hiển thị criterion score và band descriptor.
- Có disclaimer về giới hạn tối ưu hóa điểm.
- Bài thực người dùng cung cấp có overall `5.5`; Task 1 hiển thị `1.0`; Task 2 hiển thị `7.5`.
- Task 2 nhìn thấy Task Achievement `7.0`, Coherence `8.0`, Lexical `8.0`; không ghi thêm criterion chưa nhìn rõ.

### Bài học áp dụng

- Overall và task score phải nói rõ cách tổng hợp điểm thực tế của KSH.
- Descriptor phải gắn với scoring rule/runtime version thực sự đã dùng khi chấm; không suy ra rằng DB-managed profile governance còn tồn tại.
- Confidence/disclaimer không được nằm ở vị trí dễ bỏ qua.
- Không dùng trang trí score card để che trạng thái `partial`, `fallback` hoặc `AI unavailable`.

## 5.12 Writing result detail

### `OBSERVED`

Layout hai cột:

- trái: question và bài gốc;
- phải: `Overall`, `Strengths`, `Needs improvement`, `Upgraded answer`, `Sample`.

Điểm mạnh của Task 2 được tổ chức thành:

- `53 Collocations`;
- `52 Topic specific words`;
- `11 Linking devices`;
- `13 Complex structures`.

Marker có số trên bài gốc và item tương ứng trong panel. `Needs improvement` có:

- `0 Lexical errors`;
- `1 Grammatical errors`;
- `12 Repetition words`;
- lỗi grammar quan sát được: `to care` -> `caring`;
- từ lặp hiển thị từ và số lần xuất hiện.

`Upgraded answer` tạo một phiên bản viết lại riêng, vẫn có marker và thống kê. `Sample` là bài tham khảo độc lập.

### Lỗi UX đã quan sát

Khi chọn `0 Lexical errors`, empty-state lại ghi `Congratulations, you had no grammar errors`. Đây là microcopy sai context. KSH cần test mapping giữa active tab, count, heading và empty-state.

### Bài học áp dụng

- Không mutate bài gốc.
- Marker phải hai chiều và keyboard accessible.
- Tách rõ `correction`, `upgrade`, `sample` để người học không hiểu nhầm.
- Mọi issue nên có original span, suggestion, reason, criterion và confidence.
- Count chỉ là summary; không được thay explanation có bằng chứng.
- Với bài rất yếu hoặc invalid, UI phải tránh phóng đại chất lượng của upgraded answer.

## 5.13 Speaking result summary

### `OBSERVED`

Bài thật người dùng cung cấp hiển thị:

- overall `3.5`;
- Fluency and Coherence `4.0`;
- Grammar `3.0`;
- Lexical `3.0`;
- Pronunciation `4.0`;
- radar, action plan và performance theo part.

Metric view có các signal như linking devices, relevance, speak-at-length, rate of speech, individual sounds, intonation và stress.

### Bài học áp dụng

- Criterion score cần đi kèm evidence và descriptor.
- Signal rule-based và AI-generated cần được phân biệt ở contract.
- Pronunciation là advisory trừ khi calibration chứng minh được độ tin cậy.
- Action plan nên giới hạn 2-4 việc ưu tiên, không tạo danh sách lỗi quá tải.

## 5.14 Speaking result detail

### `OBSERVED`

- Cột trái có question, audio và transcript.
- Cột phải có `Strengths`, `Needs improvement`, `Upgraded answer`, `Sample`.
- Có collocations, topic words, linking devices và complex structures.
- Needs improvement tách lexical, grammar, pronunciation và word stress.
- Bài thật quan sát có 1 lexical issue, 7 grammar issues, 61 pronunciation improvements và 9 word-stress improvements.
- Pronunciation card hiển thị IPA/phoneme, `You spoke` và `Sample by Prep`.
- Có drawer danh sách câu hỏi.

### Bài học áp dụng

- 61 pronunciation marker là tải nhận thức lớn; KSH nên ưu tiên top issues và cho mở rộng.
- Transcript cần timestamp/word range để playback focus được.
- Không dùng text-only transcript làm bằng chứng duy nhất cho pronunciation.
- Nếu media hoặc confidence thiếu, ẩn hoặc downgrade nhận xét phoneme thay vì suy đoán.
- `You spoke` và audio mẫu cần label, tốc độ phát và replay rõ.

## 5.15 Learning profile và progress

### `OBSERVED`

- Tab `Overview`, `Learning`, `Test Practice`.
- Header profile có predicted score, target và CTA study plan.
- Average score theo skill.
- Total tests theo skill.
- Study frequency heatmap.
- Total duration, trophies, tests và lessons.
- Test Practice có weekly average, score trend theo mode/skill, comments, question-type highlight, all-time report và history timeline.
- Có CTA `Practice more`.
- Phiên live quan sát: predicted IELTS `2.0`; average overall `2.5`; Listening `1.0`; Reading `1.5`; Speaking `3.5`; Writing `3.5`; tổng 8 test gồm L2/R3/S1/W2; total duration khoảng 6 giờ.

### Mức khớp với KSH hiện tại

`walkthrough.md` ghi rằng template profile cũ đã được thay bởi `progress.html` dạng tab dashboard. Vì vậy Phase 13 không nên hồi sinh trang profile cũ chỉ để giống PREP. Nên mở rộng `progress.html` và route hiện tại bằng các widget có evidence.

### Bài học áp dụng

- Predicted score phải công khai sample size, recency và confidence.
- Empty-state study plan phải có action khả thi.
- Heatmap phải accessible bằng text summary.
- Chart cần table/summary fallback.
- Trend phải phân biệt Practice/Exam và skill.
- `Practice more` nên deep-link vào filter có sẵn thay vì trang chung.

## 5.16 Report an Error

### `OBSERVED`

- Entry point nằm trong overflow góc trên.
- Modal có loại: Academic Inquiry, Technical Issue, Feedback or Contribution, Other.
- Có chọn phạm vi exercise/question group.
- Nội dung yêu cầu tối thiểu 10, tối đa 100 từ.
- Bắt buộc ảnh; tối đa 3 PNG/JPG, mỗi ảnh không quá 2 MB.

### Bài học áp dụng

Không bắt người dùng tự nhập context mà hệ thống đã biết. Report payload nên tự động gắn:

- `setId`, `testId`, `sectionId`, `questionGroupId`, `questionId`;
- `attemptId`, `submissionId`, `resultView`, `activeTab`;
- `contentVersionId`, `evaluationVersion` nếu runtime thực tế có giá trị này;
- browser/app version, viewport và correlation ID;
- media reference an toàn nếu liên quan Speaking;
- consent riêng nếu đính kèm screenshot có dữ liệu cá nhân.

Ảnh không nên luôn bắt buộc. Với academic error đã có content snapshot, screenshot là optional. Với visual/technical issue, screenshot có thể được khuyến nghị hoặc bắt buộc theo category.

## 6. Pattern có thể áp dụng và pattern cần tránh

| Surface | Có thể học | Cần tránh |
|---|---|---|
| Library | Filter/search, state card, bounded paging, skill entry. | Marketing badge lấn át tiến độ; tải toàn bộ catalog. |
| Test detail | Skill card, attempt timeline, action theo state. | Dùng attempt chưa nộp để ghi đè latest submitted score. |
| Mode | So sánh capability rõ ràng. | Gắn logic rải rác trong UI. |
| Listening | Sticky audio + part navigator. | Autoplay hoặc state audio mơ hồ. |
| Reading | Desktop split pane và evidence anchor. | Ép split view lên mobile. |
| Writing | Original/feedback/upgrade/sample tách biệt. | Cho upgraded answer trông như bài gốc đã được sửa. |
| Speaking | Mic preflight và recording state machine. | Tuyên bố pronunciation chắc chắn khi confidence thấp. |
| Result | Summary -> detail -> practice action. | Chỉ có điểm hoặc illustration lớn mà thiếu evidence. |
| Progress | Trend + question-type insight + deep link. | Predicted score không nêu sample/confidence. |
| Report | Category + scoped context. | Bắt user nhập lại ID/context hoặc luôn bắt screenshot. |

## 7. Định hướng Phase 13 đề xuất

Phase 13 vẫn là visual/richer UX trên reduced scope. Functional breakage cũ, immutable history/material, import/authoring và UAT tiếp tục theo ledger hiện hành; Phase 13 không mở lại program/certificate governance hoặc Head/Admin approval.

### 13A - Design foundation và state language

Mục tiêu:

- token hóa color, typography, spacing, elevation và focus ring;
- thống nhất icon SVG/Lucide, không dùng emoji product UI;
- xây status vocabulary và action matrix dùng chung;
- tạo brand/mascot slot có thể thay thế;
- sửa mojibake/UTF-8 ở các surface Phase 13 chạm tới.

Deliverable:

- CSS token layer;
- status chip/action component contract;
- loading/empty/error/partial/stale states;
- `BaekhoMascot` companion dùng asset KSH riêng từ `baekho_mascot_pack.zip`: ưu tiên atlas PNG 64/128 px và metadata JSON, không dùng GIF preview làm runtime chính;
- accessibility baseline WCAG 2.2 AA ở luồng chính.

#### Baekho companion contract

- Gói bàn giao hiện có đủ frame cho 15 trạng thái: `idle`, `loading`, `success`, `warning`, `error`, `reading`, `listening`, `writing`, `speaking`, `importing`, `validating`, `ai_explaining`, `empty_state`, `celebration`, `collapsed`.
- Trạng thái loop phải nhận từ state thật của màn hình. `success`, `warning`, `error` và `celebration` là one-shot rồi trở về loop trước đó; mascot không được tự suy đoán trạng thái nghiệp vụ.
- Có thể tạo cảm giác pet như Codex bằng chuyển động idle, phản ứng hover/click và roaming có biên trong library/result/import. Trong timed player, audio preflight hoặc speaking recording, mascot phải neo gọn hay chuyển sang `collapsed`, không đi qua câu hỏi hoặc che control.
- Người dùng có quyền thu gọn/tạm dừng. `prefers-reduced-motion` phải dùng frame tĩnh đại diện; animation không được là nguồn duy nhất truyền đạt loading, success hay error.
- Pixel art phải scale theo bội số nguyên với `image-rendering: pixelated`; light/dark mode và 64/128 px đã có fixture để visual regression.
- Trước khi đưa asset vào repo ở Phase 13, kiểm tra license/ownership của gói, checksum và tối ưu cache. Không lấy ong, logo hoặc asset nào từ PREP.

### 13B - Practice library và catalog scale

Mục tiêu:

- search/filter theo skill, trạng thái, bộ đề và bài test; không có program/certificate switcher;
- search/filter/sort phản ánh trong query string;
- server-side pagination hoặc cursor;
- card có status/action rõ;
- collection/bookmark nếu domain KSH cần;
- responsive filter drawer.

Acceptance:

- initial fetch bounded;
- back/forward giữ filter;
- keyboard dùng được toàn bộ;
- 6000 test không tạo 6000 DOM card;
- empty/error/retry rõ.

### 13C - Set/test/skill và attempt history

Mục tiêu:

- set timeline;
- test skill list;
- latest submitted/best/in-progress tách biệt;
- `Start`, `Continue`, `Retake`, `View result`, `View details` đúng state;
- attempt history collapse/expand;
- score summary responsive.

Acceptance:

- không default-test assumption;
- không wrong entity ID;
- in-progress attempt không xóa score đã nộp;
- route/back-link đúng test và set thực tế.

Roadmap hiện hành supersede các heading lịch sử tiếp theo như sau:

| Heading lịch sử trong checkpoint | Owner hiện hành |
| --- | --- |
| `13D - Skill-native player shell` | Phase 13C |
| `13E - Result summary` | Phase 13D, đã hoàn tất và qua focused/fresh-route gate |
| `13F - Evidence-based result detail` | Phase 13E; `13E-01` implemented pending phase validation with `ACCEPT_STATIC`, no validation; `13E-02` Objective R/L implementation in progress |
| `13G - Progress dashboard` | Phase 13F |

Current Phase 13E tách đúng ba Detail screen: Objective Reading/Listening,
Writing và Speaking. Nó sở hữu backend Vietnamese/Korean labels/chips, Writing
score-vs-diagnostic hierarchy, Speaking evidence-honest chip IA và type-native
R/L renderer. Các criterion/band/taxonomy IELTS quan sát trong PREP không trở
thành contract KSH. Khi đến `13E-03`, Writing chỉ dựng seam cho ba scoring
criteria ổn định và diagnostic evidence task-bounded; expanded Korean registry,
provider schema/prompt/normalizer/rule/cache identity thuộc pre-14 gate, còn
final SME/calibration thuộc post-14 release closure.

### 13D - Skill-native player shell

Mục tiêu:

- shell chung cho header, save state, submit, overflow và navigator;
- Listening sticky audio;
- Reading split pane;
- Writing prompt/editor/draft state;
- Speaking preflight/record/upload/process state;
- exit và submit confirmations.

Acceptance:

- autosave state quan sát được;
- timer và playback rule đúng mode;
- keyboard/focus không bị mất khi đổi part;
- mobile có interaction riêng, không chỉ thu nhỏ desktop;
- Speaking không ghi âm trước consent và user gesture.

### 13E - Result summary — `HISTORICAL_MAPPING_SUPERSEDED_2026_07_22`

Mục tiêu:

- score hero có trạng thái và disclaimer;
- breakdown theo criterion/question type/part;
- latest/best semantics;
- action plan ngắn;
- CTA tới result detail và practice filter.

Acceptance:

- partial/failure không được render như success;
- sample size và denominator rõ;
- cách tổng hợp điểm và scoring runtime version được diễn đạt đúng;
- R/L và W/S dùng shell thống nhất nhưng content theo skill.

### 13F - Evidence-based result detail — `HISTORICAL_MAPPING_SUPERSEDED_2026_07_22`

Mục tiêu:

- Reading/Listening: answer, key, evidence, reasoning, transcript/audio;
- Writing: original anchors, strengths, issues, upgraded answer, sample;
- Speaking: audio/transcript anchors, grammar/vocabulary/fluency/pronunciation advisory;
- two-way marker navigation;
- per-question navigator và deep link tab state.

Acceptance:

- mọi feedback AI có source anchor hoặc ghi rõ global;
- confidence thấp làm giảm mức khẳng định;
- original answer immutable;
- no answer-key leak trước submit;
- tab/count/empty-state mapping được test;
- 100+ marker vẫn usable nhờ priority/collapse.

### 13G - Progress dashboard

Mục tiêu:

- mở rộng `progress.html`, không hồi sinh profile cũ;
- overview, learning và test-practice tabs;
- score trend, heatmap, question-type insights và history;
- deep link `Practice more`;
- predicted/target score có confidence.

Acceptance:

- chart có text/table alternative;
- no-data và small-sample states rõ;
- timezone/date aggregation đúng;
- Practice/Exam tách được;
- filter skill/mode/time range phản ánh URL.

### 13H - Responsive, performance, accessibility và visual QA

Mục tiêu:

- desktop, tablet và mobile state matrix;
- performance budget;
- keyboard/screen-reader audit;
- reduced motion/high contrast;
- visual regression cho route chính;
- microcopy contract tests.

Acceptance:

- không có horizontal overflow ở viewport hỗ trợ;
- sticky controls không che nội dung;
- focus order đúng khi mở drawer/modal;
- modal khóa background và trả focus về trigger;
- test empty-state đúng active tab, tránh lỗi như `Lexical` hiển thị `grammar`;
- ảnh/illustration không làm tăng CLS đáng kể.
- mascot không che nội dung/control, không roaming trong timed player và đứng yên khi reduced motion bật;
- state transition của mascot phản ánh đúng app state, one-shot kết thúc phải quay về loop trước đó.

## 8. Phase 14 mapping — superseded by canonical 14A-14F

> Phân nhóm research cũ `14A contract / 14B modal / 14C queue / 14D lifecycle /
> 14E safety` đã bị supersede bởi workflow và blueprint hiện hành. Canonical
> Phase 14 vẫn là toàn bộ 14A-14F “Report an Error & Content Review”; không
> renumber và không rút thành modal. Các quan sát PREP dưới đây chỉ được map vào
> canonical scope, không tự định nghĩa lại phase.

### 14A - Entry points and immutable context

- entry point nhất quán ở player, question review và result detail;
- định nghĩa target/category/severity/scope/reporter intent;
- server auto-attach set/test/section/group/question, immutable content version,
  attempt, current view/tab, actor/timestamp và correlation context;
- idempotency key; learner không phải nhập lại internal ID.

### 14B - Learner form and safe evidence snapshot

- form ngắn, category-first; question/group scope lấy từ current context;
- preview đúng dữ liệu sẽ gửi; receipt ID sau submit;
- optional screenshot/audio có consent, MIME inspection, size limit, private
  storage, remove/retry và retention/deletion contract trước implementation;
- không mặc định thu toàn màn hình hoặc log raw answer/audio ngoài policy.

### 14C - Deduplication and learner-safe status history

- audit/mapping state baseline với candidate research, không âm thầm thay enum:

```text
NEW -> TRIAGED -> IN_REVIEW -> NEEDS_INFO -> RESOLVED/REJECTED/DUPLICATE
```

- dedupe theo immutable target/version/category và bounded window;
- mọi transition có actor, timestamp, reason; learner không thấy internal note;
- rate limit, anti-spam, idempotent submit và rule reopen có policy.

### 14D - Reviewer queue and immutable correction

- queue theo category/severity/set/test/skill/content version, assignment,
  ownership và age;
- evidence viewer mở đúng snapshot cũ, không phụ thuộc mutable content hiện tại;
- authorization theo report/content/attempt scope;
- correction đi qua authoring/version workflow, tạo version/artifact mới và
  supersede bản cũ; AI không tự publish.

### 14E - Resolution feedback and operational loop

- notification khi cần thêm thông tin hoặc đã giải quyết;
- resolution note learner-safe, SLA/age, duplicate grouping và metrics;
- PII redaction hỗ trợ reviewer; learner không thấy moderation nội bộ;
- confirmed defect tạo regression fixture.

### 14F - End-to-end acceptance gate

- report từ một question mở lại đúng immutable snapshot đã báo;
- content/explanation được sửa không làm mất bằng chứng cũ;
- learner không thấy dữ liệu nội bộ; reviewer không truy cập attempt ngoài quyền;
- duplicate reports không tạo nhiều correction mâu thuẫn;
- permission/privacy/dedupe/attachment/malformed-content và complete audit trail
  đều được kiểm chứng end-to-end.

## 9. Hướng mở rộng view model/DTO

Đây là định hướng, chưa phải quyết định migration.

### 9.1 Catalog

```text
PracticeCatalogPage
  items[]
  filters
  pageInfo/cursorInfo
  totalCount
  appliedQuery
```

Mỗi card nên có:

- set/test count;
- supported skills;
- access state;
- learner state;
- latest submitted score;
- in-progress attempt reference;
- primary action.

### 9.2 Test detail

```text
PracticeTestDetailView
  set
  test
  skills[]
  scoreSummary
  navigation
```

Mỗi skill row:

- `skillCode`;
- `availability`;
- `attemptState`;
- `inProgressAttemptId`;
- `latestSubmittedAttempt`;
- `bestAttempt`;
- `historyPreview[]`;
- `primaryAction`.

### 9.3 Result evidence

Nên mở rộng quanh các contract hiện có thay vì tạo flow thứ hai:

- `PracticeResultView`;
- `PracticeAnswerReviewRow`;
- `PracticeAnswerExplanationRow`;
- `ReadingListeningResultView`;
- Speaking feedback view hiện có.

Khối evidence dùng chung có thể gồm:

```text
EvidenceAnchor
  sourceType
  sourceId
  start/end range hoặc timestamp
  displayLabel
  confidence
  contentVersionId
```

AI issue:

```text
FeedbackIssue
  category
  criterionId
  severity/priority
  originalText
  suggestedText
  explanation
  anchors[]
  confidence
  generatorVersion
```

### 9.4 Progress

- aggregation window;
- attempt/sample count;
- skill/mode split;
- trend points;
- question-type metrics;
- prediction confidence;
- recommended practice query.

## 10. Quality gates và test matrix

### 10.1 Contract tests

- action matrix theo attempt state;
- route dùng đúng set/test/attempt ID;
- latest submitted không bị attempt draft ghi đè;
- score partial/failed/stale;
- feedback tab/count/empty-state;
- result detail không leak answer key trước submit;
- report context chứa version và scope đúng.

### 10.2 UI tests

- library filter/query/back-forward;
- mode comparison và start flow;
- save draft/reload/resume;
- submit/exit confirmation;
- split pane resize và mobile switch;
- marker click hai chiều;
- audio unavailable/buffering/retry;
- speaking permission denied/revoked;
- progress no-data/small-sample;
- report upload validation.

### 10.3 Manual UAT bắt buộc ở Phase 15

- real browser/device matrix;
- real audio playback/recording với tai nghe và mic;
- accent/noise/speed Speaking cases;
- long Writing answer và hàng trăm marker;
- 6000-test catalog;
- Vietnamese/Korean/English encoding;
- slow/offline network;
- keyboard-only và screen reader;
- responsive desktop/tablet/mobile;
- old attempts sau content/rubric version changes.

## 11. Rủi ro cần quản lý

| Rủi ro | Hậu quả | Giảm thiểu |
|---|---|---|
| Copy visual quá sát PREP | Mất bản sắc, rủi ro IP. | Dùng capability map, token KSH và asset riêng. |
| AI feedback không có evidence | Người học khó tin hoặc học sai. | Anchor + confidence + report path. |
| Attempt state lẫn score state | Điểm biến mất, action sai. | Tách latest submitted, best và in-progress. |
| Marker quá nhiều | Quá tải nhận thức. | Priority, grouping, progressive disclosure. |
| Split view trên mobile | Nội dung không dùng được. | Mobile task switcher, giữ scroll position. |
| Predicted score thiếu dữ liệu | Tạo kỳ vọng sai. | Sample/recency/confidence và no-prediction state. |
| Audio/STT được tin quá mức | Feedback Speaking sai. | Calibration, low-confidence guard, human UAT. |
| Screenshot report chứa PII | Rò rỉ dữ liệu. | Consent, redaction, retention và access control. |

## 12. Backlog khảo sát sau checkpoint

### `PENDING` ưu tiên cao

1. Chỉ khảo sát thêm PREP/TOEIC khi người dùng cấp quyền và tài khoản cho phiên đó; kết quả tiếp tục là bằng chứng UI/UX bên ngoài, không mở rộng scope KSH.
2. Mở Writing mode/preflight/player live, kiểm tra autosave, task navigation và exit behavior.
3. Mở Speaking mode/preflight/player đến trước bước ghi âm; không ghi/upload audio mới nếu chưa có xác nhận phù hợp.
4. Kiểm tra thêm Speaking playback control và transcript alignment bằng UI; human audio validation vẫn để Phase 15.
5. Kiểm tra responsive có chủ đích sau khi hoàn thành desktop route map.
6. Đối chiếu trực tiếp controller/service/DTO/template/CSS hiện tại của KSH.
7. Cập nhật section Phase 13/14 trong `CODEX_PRACTICE_WORKFLOW.md` sau khi TOEIC và code audit hoàn tất.

### Không được coi là đã hoàn thành

- đánh giá chất lượng nghe của Speaking;
- TOEIC AI scoring behavior;
- mobile UAT đầy đủ;
- performance đo bằng network trace/profiler;
- accessibility audit thực tế;
- implementation Phase 13 hoặc Phase 14.

## 13. Quyết định tạm thời cho KSH

1. Giữ route domain hiện tại; không đổi theo PREP.
2. Mở rộng `progress.html`, không hồi sinh profile cũ.
3. Tái sử dụng result shell/DTO hiện có và tăng evidence richness.
4. Tách attempt state khỏi score state.
5. Thiết kế player shell chung nhưng interaction theo skill.
6. Thiết kế report context trước UI modal.
7. Đặt mascot/brand là theme slot, dùng asset KSH riêng.
8. Không đưa chatbot vào Phase 13/14; Teacher Bee chỉ là quan sát, còn chatbot KSH thuộc roadmap riêng sau Phase 16 nếu được duyệt.

## 14. Tiêu chí hoàn tất nghiên cứu

Research này chỉ được chuyển từ checkpoint sang baseline khi:

- TOEIC đã có route map và observation riêng;
- Writing/Speaking player state còn thiếu đã được ghi nhận;
- code audit KSH xác định chính xác reusable component và gap;
- Phase 13/14 trong workflow chính được mở rộng;
- mọi `INFERRED` quan trọng được xác nhận hoặc chuyển thành `PENDING`;
- DOCX phụ lục ảnh đã render và kiểm tra toàn bộ trang;
- không có tuyên bố đã nghe/đánh giá audio khi công cụ không cung cấp audio.

---

Checkpoint này được tạo để bảo toàn context. Các phát hiện tiếp theo cần được append có ngày, evidence source và nhãn `OBSERVED/INFERRED/PENDING`, không sửa ngược dữ kiện cũ mà không ghi lý do.
## Live TOEIC Addendum

### Routes inspected

- `/en/test-practice/toeic/341/test-detail/19846`: `[Dec 2025] Real tests Speaking & Writing`, Speaking Test 1; unattempted state with `Start`.
- `/en/test-practice/toeic/343/test-detail/20235`: `[Jan 2026] Real tests Speaking & Writing`, Writing Test 1; unattempted state with `Start`.
- `/en/test-practice/toeic/310/test-detail/11127`: `Exam Collection 1 (Capricorn Grind)`, Listening and Reading; both available through `Start`.
- `/en/test-practice/toeic/343/test-detail/20234`: existing submitted Speaking attempt used for result-detail inspection.

### TOEIC library and test-detail patterns

- The TOEIC library uses a strong orange/red exam accent, `HOT` labeling, monthly real-test collections, exclusive collections, and themed collections. This is useful as a content taxonomy reference, not a branding direction.
- The set detail page is skill-specific for the monthly Speaking/Writing sets, while collection tests expose Listening and Reading together. The card makes availability explicit with skill pills and a single primary `Start`/`Retake` action.
- The result summary on the test-detail page states that the result is updated from the most recent answer and exposes `View details` without forcing the learner to navigate through a separate history page.

### TOEIC result and AI-feedback patterns

The submitted Speaking attempt route (`343/20234`) opens a dedicated result workspace with a left navigation:

- `Results` and `General statistics` for the overview.
- `Your work` and `Criteria-based feedback` for sentence/question-level inspection.
- Score presentation: `20 / 200`, `LEVEL 1/8`, an explanatory level description, and a link to the TOEIC score conversion descriptors.
- General statistics include each question, completion level (`Not qualified`/`Low`), and thinking time such as `269s / 45s` and `38s / 45s`.
- Criteria feedback is failure-aware. When an answer is unanswered, incorrect for the prompt, or the recording is too poor to capture, the UI explicitly says feedback cannot be provided and recommends retaking more carefully.

This suggests a KSH result contract with separate `score`, `scale`, `level`, `completion`, `timing`, and `feedbackAvailability` fields. Do not collapse a missing/invalid AI assessment into a zero score; distinguish `not_answered`, `not_qualified`, `capture_failed`, and `graded` states.

### Bài học từ bằng chứng TOEIC

- IELTS band và TOEIC `x/200` hoặc `x/495` chỉ là các scale đã quan sát ở PREP. KSH dùng score/level contract của chính mình và không thêm certificate-aware score governance từ bằng chứng này.
- Make per-question readiness visible before showing AI feedback. A clear “feedback unavailable and why” state is more trustworthy than an empty feedback panel.
- Surface timing evidence beside question-level feedback, but explain whether it is response time, preparation time, or elapsed time.
- Keep the result workspace navigable by skill/section and by question; the TOEIC left rail is a good information-architecture reference, while KSH should retain its own domain, route and visual language.
