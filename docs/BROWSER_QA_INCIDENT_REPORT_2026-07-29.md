# Korean Study Hub — Desktop Browser QA Incident Report

> Ngày QA: 2026-07-29
>
> Baseline ban đầu: `origin/main` tại
> `11aad00954a136ad667b8d936d354d5aa9e3f1dd` (PR #44)
>
> Runtime: Java 17.0.9, Spring Boot 3.5.16, MySQL schema V62
>
> Viewport ưu tiên: desktop 1440 × 900

## Cách dùng checklist

- `[ ]` Chưa xử lý hoặc chưa xác minh xong.
- `[~]` Đã có bản sửa cục bộ, còn chờ browser QA/regression.
- `[x]` Đã sửa và xác minh lại.
- Khi tick `[x]`, ghi commit/PR và bằng chứng QA vào mục tương ứng.

## Attribution backfill

> Bổ sung ngày 2026-08-11 từ lịch sử Git; không thay đổi nội dung hoặc trạng thái của các
> incident gốc bên dưới.

- **USER FIX**: commit của chủ repository qua các alias `toannqhe180972`, `toannq1424`
  hoặc `toannnnq1424`.
- **CONTRIBUTOR FIX**: commit của cộng tác viên khác.
- File test xuất hiện trong diff không tự chứng minh test đã chạy; execution evidence phải
  được ghi riêng. Báo cáo ngày này là dated browser record có sẵn.
- Chính sách schema hiện tại: **không tạo bảng mới; chỉ được thêm cột vào bảng có sẵn**.
  Các migration lịch sử, nếu có, không phải tiền lệ cho thay đổi schema mới.

| Nguồn sửa | Commit | Phạm vi incident đã ghi trong báo cáo |
| --- | --- | --- |
| **USER FIX** — toannqhe180972 | `952c00df` | Browser QA regressions: test navigation, Question Bank combobox/gutter/responsive và home LEADER |
| **USER FIX** — toannqhe180972 | `8648b458` | Assignment grading UI và messaging recipient roster |
| **USER FIX** — toannqhe180972 | `fe67907d` | Test management và exam-image/storage lifecycle |
| **USER FIX** — toannqhe180972 | `015c0fa7` | Question Bank department taxonomy, import sample và detail UI |
| **USER FIX** — toannqhe180972 | `00a3728f` | Student class-list layout |
| **USER FIX** — toannqhe180972 | `da166181` | Practice catalogue độc lập với classes |
| **USER FIX** — toannqhe180972 | `bd4a127e` | Commit tài liệu hóa audit/browser QA |

## Lỗi đã xử lý trong đợt này

### TEST-AUTH-UI-001 — Hành động test hiển thị nhưng bị từ chối

- [x] Sửa nguyên nhân gốc.
- [x] Xác minh lại bằng browser.
- Mức độ: High.
- Vai trò: `LECTURER`.
- Luồng:
  - `/lecturer/tests` hiển thị test `#1` cùng các hành động Sửa, Xem trước,
    Theo dõi và Bài nộp.
  - `/lecturer/tests/1/preview` tải thành công.
  - `/lecturer/tests/1/edit`, `/lecturer/tests/1/monitor` và
    `/lecturer/tests/1/submissions` dẫn tới trang lỗi.
- Bằng chứng server:
  - Backend trả 403 tại `/lecturer/tests/1/edit`.
  - `AccessDeniedException`: tài khoản không có quyền chỉnh sửa lớp này.
- Kỳ vọng:
  - Nếu giảng viên có quyền thấy/quản lý test thì các hành động hợp lệ phải mở
    được; nếu không có quyền thì test hoặc hành động không được xuất hiện trong
    danh sách quản lý.
- Nguyên nhân gốc:
  - Quyền quản lý test theo `TestAccessResolver` là hợp lệ vì giảng viên là người
    tạo test.
  - Controller sau đó dựng sidebar bằng `ClassesService#getViewable`, một quyền
    lớp chặt hơn; lỗi của phần chrome tùy chọn đã làm hỏng toàn bộ trang test.
- Bản sửa:
  - Giữ nguyên quyền lớp và quyền test.
  - Chỉ bỏ sidebar lớp khi người quản lý test không còn quyền xem lớp; trang test
    chuyển sang layout toàn chiều rộng.
  - Áp dụng cùng nguyên tắc cho trang review bài nộp.
- Bằng chứng hồi quy:
  - `LECTURER` mở thành công `/lecturer/tests/1/edit`.
  - `/monitor` redirect đúng sang `?tab=monitor` và hiển thị bảng theo dõi.
  - `/submissions` redirect đúng sang `?tab=submissions`.
  - Các tab Info/Monitor/Submissions/History đều dùng `lf-no-sidebar`, main rộng
    900px ở desktop thay vì mắc lại trong cột 280px.
  - Preview vẫn hoạt động; không có 403/exception mới trong server log.

### QB-CATEGORY-DROPDOWN-001 — Dropdown danh mục phóng gần toàn màn hình

- [x] Đã sửa.
- [x] Đã xác minh browser desktop, mobile, bàn phím và accessibility.
- Mức độ: Medium.
- Vai trò: `LECTURER`.
- Route: `/lecturer/question-bank/new`.
- Hiện tượng:
  - Native select mở menu rất rộng và chữ quá lớn, che phần lớn form/đáp án.
  - Dữ liệu danh mục vẫn tải đủ; lỗi nằm ở cách trình bày/điều khiển.
- Bản sửa cục bộ:
  - Progressive enhancement sang combobox KSH có danh sách giới hạn chiều cao.
  - Giữ native select làm control submit.
- Bằng chứng hồi quy:
  - Desktop 1440×900: ô và menu cùng rộng 567px; kết quả lọc một dòng cao 47px,
    không còn popup gần toàn màn hình.
  - Gõ `lap trinh` tìm đúng `Lập trình`; Enter đồng bộ `categoryId=-1`.
  - Chọn chuột `Tiếng Anh` đồng bộ `categoryId=-6`.
  - Click lại mở danh sách, Escape đóng danh sách nhưng giữ lựa chọn.
  - Validation chặn submit, đặt `aria-invalid=true` và focus đúng
    `#categoryId-combobox`, không kéo focus vào native select 1×1.
  - Mobile 390×844: ô và menu cùng rộng 283px, `scrollWidth=375`, không tràn ngang.
  - Hai combobox dùng chung tại `/admin/permissions/overrides` vẫn tìm/chọn được.

### QB-PAGE-GUTTER-001 — Form ngân hàng câu hỏi bám sát mép viewport

- [x] Đã sửa bằng CSS resource-only.
- [x] Đã chụp và kiểm tra lại ở viewport desktop và compact.
- Mức độ: Medium.
- Vai trò: `LECTURER`.
- Route: `/lecturer/question-bank/new`.
- Hiện tượng:
  - Link quay lại, tiêu đề, mô tả và khung form bắt đầu sát mép trái viewport.
  - Ở giao diện desktop rộng, khoảng trống bên ngoài card không đồng nhất với
    header và các màn hình giảng viên khác.
- Nguyên nhân gốc:
  - `.qb-page` giới hạn `max-width` và căn giữa nhưng đặt padding ngang bằng
    `0`, nên shell chạm mép khi viewport không lớn hơn đáng kể so với nội dung.
- Bản sửa:
  - Giữ `max-width: 1240px` và căn giữa, thêm gutter desktop co giãn
    `clamp(16px, 3vw, 32px)` cùng `box-sizing: border-box`.
  - Ở breakpoint compact, giữ gutter 14px và khoảng đệm trên 16px thay vì bỏ
    toàn bộ padding ngang.
- Bằng chứng hồi quy:
  - Ảnh chụp desktop sau refresh cho thấy link quay lại, tiêu đề và card thẳng
    hàng trong một shell có khoảng thở hai bên; form không còn bám góc trái.
  - Ảnh chụp viewport compact vẫn giữ gutter hai bên, card và trường nhập co
    trong chiều rộng khả dụng, không xuất hiện cuộn ngang.
  - Combobox danh mục và dữ liệu tám danh mục hiện hành vẫn hiển thị bình
    thường sau thay đổi layout.

### QB-RESPONSIVE-001 — Bảng và rich-content Question Bank bị cắt ở màn hình hẹp

- [x] Đã sửa markup/CSS responsive và compile bằng JDK 17.
- [x] Đã Browser QA route lecturer ở viewport 390×844.
- [x] Đã UAT route review bằng danh mục và câu hỏi tạm, sau đó xoá sạch dữ liệu
  QA; trang và modal không gây tràn ngang ở viewport 819 px.
- Mức độ: High.
- Phạm vi:
  - `/lecturer/question-bank`
  - `/leader/question-bank`
  - trang review/category detail khi có dữ liệu
- Nguyên nhân gốc:
  - Bảng `min-width: 720px` nằm trực tiếp trong panel `overflow: hidden`.
  - Review layout chưa có grid/gap và form inline không wrap ở mobile.
  - Preview category dùng `th:utext` trong `<span>`, nên HTML block có thể phá
    line-clamp; `<pre>` và chuỗi dài chưa có cơ chế chống tràn.
- Bản sửa:
  - Bọc bảng lecturer, import và review bằng `admin-list-table-scroll`.
  - Thêm grid review hai cột, tự hạ về một cột; form inline wrap/full-width ở
    breakpoint mobile.
  - Thêm `overflow-wrap: anywhere` và cuộn ngang cục bộ cho `<pre>`.
  - Render preview category bằng plain text `contentPreview`, không nhúng HTML
    block vào `<span>`.
- Bằng chứng Browser QA:
  - Viewport 390×844 cho `clientWidth=375`, `scrollWidth=375`; không có tràn
    ngang toàn trang.
  - `.qb-page` giữ gutter trái/phải 14px.
  - Import wrapper đã có đồng thời `qb-import-table-wrap` và
    `admin-list-table-scroll`.
  - Trang LEADER trạng thái rỗng render bình thường sau compile.

### QB-LEADER-CATALOG-001 — Trang LEADER báo rỗng dù giảng viên có danh mục

- [x] Xác định đây là taxonomy lai gây hiểu nhầm, không phải hai ngân hàng câu
  hỏi độc lập.
- [x] Chốt `question_bank_categories` là taxonomy canonical duy nhất cho ngân
  hàng câu hỏi bộ môn.
- Mức độ: Medium.
- Luồng:
  - `/lecturer/question-bank` từng ghép danh mục ADMIN với danh mục bộ môn nên
    hiển thị tám mục toàn cục cộng với `namdk`.
  - `/leader/question-bank` chỉ đọc danh mục bộ môn nên chỉ hiển thị `namdk`.
- Phân tích code:
  - `question_bank_categories` vẫn quan trọng vì là taxonomy theo bộ môn và là
    đích khóa ngoại của `question_bank_items.category_id`.
  - Cầu nối ID âm/mirror lười làm bộ lọc giảng viên chứa lựa chọn chưa có câu
    hỏi và nằm ngoài quyền mở/đóng trực tiếp của LEADER.
- Bản sửa:
  - Giảng viên, import, review và bộ chọn câu hỏi của bài test đều dùng cùng ID
    dương trong `question_bank_categories`.
  - LEADER thấy cả mục active/inactive để quản lý; giảng viên chỉ thấy mục active
    trong đúng bộ môn.
  - `categories` của ADMIN tiếp tục là taxonomy nội dung/khóa học, không còn được
    ghép vào ngân hàng câu hỏi.
  - Các bản mirror đã tồn tại vẫn là dòng bộ môn hợp lệ và không mất dữ liệu.
  - Không drop `question_bank_categories`.
- Bằng chứng hồi quy:
  - Compile Java 17 thành công.
  - `/lecturer/question-bank` chỉ còn hai danh mục bộ môn đang mở
    `Kinh tế học` và `namdk`; không còn ID âm/danh mục ADMIN trộn vào.
  - Câu hỏi `#81` dùng danh mục `Kinh tế học` và trang chi tiết mở bình thường.
- Commit: `015c0fa7`.

### QB-IMPORT-CATEGORY-002 — File mẫu dùng danh mục không tồn tại trong bộ môn

- [x] Đã xác định nguyên nhân dữ liệu.
- [x] Đã làm rõ thông báo validation và đồng bộ file mẫu theo danh mục ngân
  hàng câu hỏi đang mở của bộ môn.
- Mức độ: Medium.
- Route: `/lecturer/question-bank`, khối `Xem trước import Excel`.
- Hiện tượng:
  - Hai dòng dùng `Giải tích 1` đều bị chặn với thông báo danh mục không tồn tại
    hoặc đang bị ẩn.
  - Preview hợp lệ bằng `0`, xác nhận import bị vô hiệu hóa và chưa ghi DB.
- Nguyên nhân gốc:
  - Import đối chiếu chính xác tên trong `question_bank_categories` đang mở của
    đúng bộ môn.
  - Browser QA hiện chỉ thấy `Kinh tế học` và `namdk`; `Giải tích 1` không phải
    danh mục ngân hàng câu hỏi hợp lệ.
  - File Excel mẫu cũ lại hard-code `Giải tích 1`, nên chính file mẫu có thể tự
    tạo preview lỗi. Đây là lỗi hợp đồng mẫu, không phải corruption DB.
- Bản sửa:
  - Thông báo nêu tên danh mục lỗi, phân biệt danh mục ngân hàng câu hỏi bộ môn
    và gợi ý tối đa năm danh mục đang mở để sửa cột Excel.
  - Giữ nguyên exact-match và nguyên tắc preview không ghi DB.
- Commit: `015c0fa7`.

### QB-DETAIL-UI-001 — Chi tiết câu hỏi trải quá rộng và badge đáp án bị kéo dài

- [x] Đã sửa CSS/markup.
- [x] Đã Browser QA desktop 1280×720 và mobile 390×844.
- Mức độ: Medium.
- Route: `/lecturer/question-bank/81`.
- Bản sửa:
  - Giới hạn vùng đọc ở 960px và đặt nội dung trong card có padding.
  - Tách từng đáp án thành row/card; đáp án đúng có nền nhẹ nhưng badge
    `Đáp án đúng` giữ kích thước nội dung thay vì thành thanh xanh toàn hàng.
- Bằng chứng:
  - Desktop: page đúng 960px, card 888px; badge rộng 106px trong row 822px.
  - Mobile: card rộng 347px trong viewport 390px; không tràn ngang.
- Commit: `015c0fa7`.

### CLASSES-STUDENT-LIST-UI-001 — `/my/classes` dùng nhầm grid sáu cột

- [x] Đã sửa selector/layout và loại điều khiển không hoạt động.
- [x] Đã Browser QA desktop 1280×720 và mobile 390×844.
- Mức độ: High.
- Nguyên nhân gốc:
  - HTML đặt `class-page` và `my-classes-shell` trên cùng một `<main>`, nhưng CSS
    dùng selector descendant `.class-page .my-classes-shell`.
  - Quy tắc riêng không khớp, làm trang rơi về grid sáu cột của giảng viên dù
    mỗi dòng học viên chỉ có bốn ô.
- Bản sửa:
  - Dùng selector same-element `.class-page.my-classes-shell`.
  - Giữ gutter của shell; bổ sung semantic class cho giảng viên/ngày.
  - Search/sort chỉ thao tác trong `#active-class-list`, không trộn các yêu cầu
    tham gia đang chờ; bỏ `viewToggle` không có hành vi.
- Bằng chứng:
  - Desktop: shell có padding `22px 28px 28px`, header nằm từ x=29 đến x=1236,
    không tràn ngang.
  - Mobile: shell rộng 375px trong viewport 390px, không tràn ngang; empty state
    và các control vẫn render bình thường.
- Commit: `00a3728f`.

### PRACTICE-CATALOG-CLASS-001 — Catalog `/practice` lẫn bộ lọc lớp học

- [x] Đã bóc bộ lọc lớp khỏi catalog và resume công khai.
- [x] Đã Browser QA route có legacy `classId` và xác nhận UI/link không truyền
  tiếp tham số này.
- Mức độ: Medium.
- Quyết định phạm vi:
  - `/practice` là kho luyện tập độc lập với class, nên catalog chỉ đọc các bộ
    `GLOBAL` đã publish.
  - Loại `classId`, danh sách “Mọi lớp học” và chỉ số “Lớp đang tham gia” khỏi
    catalog.
  - Resume trên catalog chỉ đọc attempt của bộ GLOBAL.
  - Giữ nguyên schema `scope/class_id`, dữ liệu lịch sử, route direct legacy,
    AI config, storage config và các workflow Practice ngoài catalog để không
    phá tương thích.
- Bằng chứng:
  - `/practice?...&classId=1061` vẫn tải được nhưng không còn control
    `name=classId`, chữ “Mọi lớp học” hay link tiếp tục mang `classId`.
  - Trang chỉ hiển thị card GLOBAL và không tràn ngang.
- Commit: `da166181`.

### HOME-ROLE-LEADER-001 — Trang chủ để trống vai trò LEADER

- [x] Sửa hiển thị.
- [x] Xác minh lại bằng browser.
- Mức độ: Low.
- Tài khoản: `leader@ksh.edu.vn`.
- Route: `/`.
- Hiện tượng: dòng “Vai trò:” không có badge/text; các vai trò STUDENT và
  LECTURER hiển thị bình thường.
- Bằng chứng hồi quy: đăng nhập `leader@ksh.edu.vn`, route `/` hiển thị
  `Vai trò: LEADER`.

## Cải thiện vòng lặp phát triển giao diện

### DEV-UI-LIVE-REFRESH-001 — Sửa resource không cần chạy lại Maven

- [x] Bật `spring-boot-maven-plugin.addResources=true`.
- [x] Xác minh thay đổi template xuất hiện và biến mất chỉ bằng browser refresh.
- Phạm vi: HTML/CSS/JS trong `src/main/resources`.
- Java vẫn dùng DevTools restart; bản JAR production không bị thay đổi bởi tùy
  chọn dành cho goal `spring-boot:run`.
- Runtime xác minh: PID Java `15064` giữ nguyên trong phép thử refresh-only.

## E2E lớp học hoàn chỉnh và lỗi phát hiện thêm

### ASGN-LECTURER-FORM-UI-001 — Form tạo bài tập bị ép sát mép

- [x] Đã sửa bằng CSS resource-only.
- [x] Đã xác minh lại bằng browser desktop và kích thước hẹp.
- Mức độ: Medium.
- Route đã tái hiện:
  `/lecturer/classes/1061/assignments/new`.
- Hiện tượng:
  - Form dùng gần như toàn bộ chiều rộng panel, thiếu khoảng đệm.
  - Hàng điểm tối đa/hạn nộp và cụm nút bị ép sát cạnh.
- Bản sửa:
  - Thêm panel riêng có padding co giãn.
  - Giới hạn cột form ở 840px và căn giữa.
  - Cho hàng hai cột chuyển thành một cột dưới 720px.
- Bằng chứng hồi quy:
  - Desktop: form rộng 840px, nằm giữa panel và có khoảng trống hai bên.
  - Kích thước hẹp: panel giữ padding 18px, không tràn ngang.
  - Resource HTML/CSS xuất hiện ngay sau refresh, không restart Maven.

### ASGN-STUDENT-NAV-001 — Bài tập dùng nhầm sidebar giảng viên

- [x] Đã sửa.
- [x] Đã xác minh toàn bộ danh sách → chi tiết → phản hồi.
- Mức độ: High.
- Hiện tượng:
  - Mục Bài tập trong sidebar học viên bị vô hiệu hóa.
  - Ba template bài tập lại dùng fragment sidebar giảng viên, làm lộ link quản
    trị không phù hợp và phá tính nhất quán của luồng học viên.
- Bản sửa:
  - Kích hoạt mục Bài tập trong `student-class-sidebar`.
  - Ba trang học viên dùng chung sidebar học viên và active key
    `my-classes`.
- Bằng chứng hồi quy:
  - `/classes/1061/assignments` tải danh sách và đánh dấu đúng mục Bài tập.
  - `/classes/1061/assignments/47` tải đúng chi tiết và trạng thái Đã chấm.
  - `/classes/1061/assignments/47/feedback` tải đúng điểm và nhận xét.
  - Các link Bài giảng, Bài test và Tin nhắn vẫn là route học viên.

### ASGN-STUDENT-UI-001 — Chi tiết và phản hồi bài tập bị vỡ bố cục

- [x] Đã sửa bằng resource-only, không restart Maven.
- [x] Đã xác minh trực quan cả ba màn hình tại desktop 1440×900.
- Mức độ: High.
- Hiện tượng:
  - Tiêu đề, metadata và nội dung dính sát cạnh panel.
  - Sidebar quá hẹp, phần chính kéo edge-to-edge; trang phản hồi mất phân cấp
    thị giác và các cụm chữ chen sát nhau.
- Nguyên nhân gốc:
  - Các template bài tập thiếu `detail-page.css`, trong khi sidebar và layout
    lớp phụ thuộc các token/layout của stylesheet này.
  - Panel bài tập không có padding và không giới hạn chiều rộng đọc.
- Bản sửa:
  - Tải đủ `detail-page.css`, `class-detail.css`, `student-lessons.css` và
    `assignments.css` theo đúng thứ tự.
  - Tạo shell riêng có sidebar 260px, cột nội dung tối đa 1000px và panel có
    padding/radius.
  - Bảng danh sách cho phép cuộn ngang ở màn hình nhỏ; breakpoint 900px chuyển
    sidebar lên trên.
- Bằng chứng hồi quy:
  - Danh sách hiển thị đủ sáu cột, không tràn khỏi panel.
  - Chi tiết hiển thị rõ metadata, yêu cầu và trạng thái bài nộp.
  - Phản hồi hiển thị `95.00 / 100.00`, nhận xét và nội dung nộp trong các
    khối riêng, không còn đè/dính chữ.

### ASGN-GRADE-READONLY-001 — Mở trang chấm bài gây lỗi transaction

- [x] Đã sửa nguyên nhân gốc.
- [x] Đã compile Java 17 và xác minh lại bằng browser.
- Mức độ: High.
- Route đã tái hiện:
  `/lecturer/classes/1061/assignments/47/submissions/15/grade`.
- Bằng chứng server:
  - GET chạy trong transaction read-only nhưng repository phát
    `SELECT ... FOR UPDATE`.
  - MySQL từ chối câu lệnh ghi khóa trong transaction read-only và trả trang
    “Đã có lỗi xảy ra”.
- Bản sửa:
  - GET chi tiết dùng truy vấn không khóa.
  - Chuyển khóa bi quan sang transaction `grade()` và giữ thứ tự ổn định:
    assignment trước, submission sau.
- Bằng chứng hồi quy:
  - Trang chấm mở thành công.
  - Lưu được `95.00/100.00`.
  - Học viên đọc được nhận xét:
    “Hoàn thành tốt yêu cầu E2E, dùng đúng ba mẫu câu chào hỏi và giới thiệu.”

### E2E-CLASS-1061 — Bằng chứng luồng giảng viên và học viên

- [x] Giảng viên tạo lớp `#1061`, tên
  `KSH E2E Complete Flow 2026-07-29`.
- [x] Học viên vào lớp bằng invite `CA2A4R`; giảng viên phê duyệt thành viên.
- [x] Tạo chapter `#613` và publish bài giảng `#707`; học viên hoàn thành, trang
  tiến độ giảng viên ghi nhận 100% (1/1).
- [x] Tạo và publish bài tập `#47`; học viên nộp submission `#15`; giảng viên
  chấm 95/100; học viên xem được feedback.
- [x] Tạo và publish bài test `#96` gồm hai câu MCQ; attempt học viên `#16`
  đạt 2/2, 2.00/2.00 trong 45 giây; giảng viên xem được bài nộp.
- [x] Học viên hoàn thành bộ Practice ổn định `/practice/sets/1`, attempt
  `#66407`, đạt 100/100 và 2/2.
- Ghi chú:
  - Mã công khai của lớp là `39JC9`; invite đang hoạt động là `CA2A4R`. Đây là
    hai khái niệm hiện hữu khác nhau, không phải lỗi dữ liệu.
  - Không thay đổi AI config, storage config hay logic của `/practice`.

### EXAM-IMAGE-LIFECYCLE-001 — Ảnh soạn đề có thể tồn tại mồ côi hoặc bị cache công khai

- [x] Đã triển khai owner-bound staged upload và transactional claim.
- [x] Đã harden sanitize/canonical URL, lỗi xoá storage và cache header.
- [x] Đã Browser QA staged → durable trên bài test thật.
- [x] `ExamImageStorageServiceTest` đã chạy đạt 9/9 trong đợt audit 01/08/2026.
- Mức độ: High.
- Rủi ro đã đóng:
  - Rich HTML bị sanitizer loại bỏ không còn tạo durable object mồ côi.
  - Foreign-host, percent-encoded, non-image và staged reference còn sót đều bị
    từ chối.
  - Lỗi xoá ở local/R2 không còn bị nuốt; cleanup giữ retry trong bộ nhớ.
  - `/uploads/exams/staged-*` dùng `Cache-Control: private, no-store`.
- Bằng chứng browser:
  - Trước save, test `#97` dùng nguồn
    `/uploads/exams/staged-3-1785321814407-….png`.
  - Sau save và mở lại `/lecturer/tests/97/edit`, nguồn đã thành
    `/uploads/exams/d6c2e725-96f7-415b-85b5-82194816b250.png`.
  - Preview, edit, monitor và submissions của cụm test không phát sinh trang
    “Đã có lỗi xảy ra”.
- Giới hạn còn lại:
  - Retry xoá hiện chỉ bền trong vòng đời JVM. Muốn chống mất retry khi process
    crash cần outbox/queue persistent và migration, đang được hoãn theo phạm vi.

### PRACTICE-DEV-DATA-HYGIENE-001 — Dữ liệu fixture Practice quá nhiều

- [ ] Cần dọn/phân loại ở đợt Practice riêng.
- Mức độ: Low trong môi trường dev.
- Quan sát: trang Practice hiển thị khoảng 218 bộ, gồm nhiều tên fixture kiểm
  thử như `Speaking Media ...`.
- Không xử lý trong đợt này để giữ nguyên phạm vi loại trừ Practice.

### MSG-RECIPIENT-ROSTER-001 — Soạn tin không tải đủ người nhận

- [x] Đã sửa đồng bộ danh sách và quyền tạo hội thoại.
- [x] Đã xác minh bằng browser với STUDENT, LECTURER và ADMIN.
- Mức độ: High.
- Route: `/my/messages/new`.
- Hiện tượng:
  - STUDENT chỉ thấy giảng viên của lớp đang tham gia.
  - LECTURER/LEADER chỉ thấy học viên của lớp do mình dạy.
  - ADMIN nhận danh sách rỗng.
- Nguyên nhân gốc:
  - Controller và template đã render toàn bộ kết quả; JavaScript chỉ lọc nhanh
    các dòng hiện hữu.
  - `MessagingAccess` cố ý giới hạn cả roster lẫn POST theo quan hệ cùng lớp.
- Bản sửa:
  - STUDENT thấy mọi tài khoản STUDENT, LECTURER và LEADER hợp lệ.
  - LECTURER, LEADER và ADMIN thấy mọi role hợp lệ trong hệ thống.
  - Luôn loại chính người gửi, tài khoản inactive, locked và soft-deleted.
  - Dùng cùng một ma trận role cho danh sách và `POST /my/messages/new`, tránh
    hiện người nhận nhưng bấm vào lại 404.
- Bằng chứng browser:
  - STUDENT thấy tám học sinh khác, LECTURER và LEADER; không thấy ADMIN.
  - LECTURER thấy học sinh, LEADER và ADMIN; không thấy chính mình.
  - ADMIN thấy toàn bộ 11 tài khoản hợp lệ còn lại.
  - Tìm `sv08` chỉ còn đúng `Bùi Tuấn Khang — sv08@ksh.edu.vn`.
- Ghi chú production:
  - Yêu cầu này mở rộng Tin nhắn từ quan hệ lớp thành danh bạ hệ thống.
  - Với dữ liệu lớn nên chuyển sang tìm kiếm phân trang phía server; hiện tại
    danh sách đầy đủ vẫn được render vào HTML theo đúng yêu cầu sản phẩm.

## Quan sát cần theo dõi

### PRACTICE-DEADLINE-RETRY-001 — Hai lượt deadline được lên lịch retry

- [ ] Phân loại dữ liệu cũ hay lỗi runtime.
- Không sửa trong đợt này nếu vẫn thuộc phạm vi Practice đang được xử lý riêng.
- Server ghi nhận attempt `66374` và `66393` gặp
  `IllegalArgumentException`, disposition `RETRY_SCHEDULED`.
- Chưa có stack trace hoặc lỗi khởi động; trang tiến độ hiển thị hai lượt ở
  trạng thái “Cần bắt đầu lại”.

### PLATFORM-MYSQL-FLYWAY-001 — Cảnh báo compatibility

- [ ] Xử lý ở đợt migration/platform sau.
- Flyway hiện cảnh báo MySQL 9.2 mới hơn dải đã được phiên bản Flyway này kiểm
  thử (tối đa 8.1).
- Không làm ứng dụng khởi động thất bại; 62 migration vẫn validate thành công.

## Luồng đã QA đạt

- [x] Login/logout cho tài khoản demo học viên và giảng viên.
- [x] Học viên: Kho luyện tập → Tiến độ → Kết quả → Chi tiết bằng chứng.
- [x] Giảng viên Practice: Quản lý bộ đề, Lịch sử sửa đổi, Kho tài nguyên.
- [x] Test theo lớp: `/lecturer/classes/2/tests` → form tạo mới giữ
  `classId=2` → Quay lại đúng `/lecturer/classes/2/tests`.
- [x] Kho đề test toàn cục → form tạo mới → Quay lại đúng `/lecturer/tests`.
- [x] Ngân hàng câu hỏi và form thêm câu hỏi dùng đúng danh mục bộ môn đang mở;
  tại thời điểm QA là `Kinh tế học` và `namdk`.
- [x] 12 khu vực lớp `#2`: bảng tin, lịch, thành viên, vai trò, nhóm, bài tập,
  test, bảng điểm, tiến độ, bài giảng, tài liệu và cài đặt.
- [x] Kho học liệu, kho mẫu bài giảng và form/lịch sử sửa bài giảng.
- [x] Public/auth: login, logout, quên mật khẩu, reset token không hợp lệ và
  redirect route cần đăng nhập.
- [x] Học viên ngoài Practice: lớp, bài học, test, bài tập, flashcard, hồ sơ,
  thông báo và tin nhắn.
- [x] Toàn bộ route chi tiết giảng viên được liệt kê trong ma trận QA.
- [x] Luồng `LEADER`, gồm dashboard bộ môn, phân công, báo cáo, ngân hàng câu hỏi
  và negative RBAC.
- [x] Luồng `ADMIN`, gồm dashboard, tài khoản, bộ môn, danh mục, lớp, phân quyền
  và các trang cài đặt.

## Kết thúc đợt QA

- [x] Route matrix công khai, học viên, giảng viên, LEADER và ADMIN.
- [x] Browser regression cho các lỗi đã sửa.
- [x] 13/13 kiểm tra mục tiêu DB-free đạt; `node --check` và `git diff --check`
  đạt. Không chạy full suite theo yêu cầu.
- [x] Nhóm follow-up hiện tại compile cả main/test-source thành công bằng
  `.\mvnw.cmd -q -DskipTests test-compile`; không thực thi test;
  `git diff --check` đạt.
- [x] Các focused test non-Practice của nhóm follow-up đã chạy đạt 39/39 ngày
  01/08/2026. Full-suite và test dùng DB thật không chạy vì phạm vi hiện tại
  loại trừ Practice và migration/database harness.
- [x] Server-log sweep cuối: không có `ERROR`, exception, 403 hay lỗi template
  mới; chỉ còn hai warning platform đã ghi ở trên.
- [x] Giữ nguyên phạm vi loại trừ: mật khẩu demo `123456`, migration chain,
  AI/storage/schema và các workflow riêng của Practice. Ngoại lệ sản phẩm được
  yêu cầu trực tiếp trong đợt này chỉ là bóc liên kết lớp khỏi catalog
  `/practice`; direct legacy và dữ liệu lịch sử vẫn được giữ nguyên.
