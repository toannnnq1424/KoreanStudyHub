# Korean Study Hub — Desktop Browser QA Incident Report

> Ngày QA: 2026-07-29
>
> Baseline: `origin/main` tại `7d768643df8832de0f375d9bb708e1bf7e9f1e1d` (PR #42)
>
> Runtime: Java 17.0.9, Spring Boot 3.5.16, MySQL schema V62
>
> Viewport ưu tiên: desktop 1440 × 900

## Cách dùng checklist

- `[ ]` Chưa xử lý hoặc chưa xác minh xong.
- `[~]` Đã có bản sửa cục bộ, còn chờ browser QA/regression.
- `[x]` Đã sửa và xác minh lại.
- Khi tick `[x]`, ghi commit/PR và bằng chứng QA vào mục tương ứng.

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

### QB-LEADER-CATALOG-001 — Trang LEADER báo rỗng dù giảng viên có danh mục

- [x] Chốt hướng UX: giải thích cơ chế mirror lười, không tạo dữ liệu bằng GET.
- [x] Xác minh lại bằng browser với `leader@ksh.edu.vn`.
- Mức độ: Medium.
- Luồng:
  - `/lecturer/question-bank/new` hiển thị tám danh mục môn học do ADMIN quản
    lý.
  - `/leader/question-bank` lại hiển thị “Chưa có danh mục nào” và mời LEADER
    tạo danh mục.
- Phân tích code:
  - `question_bank_categories` vẫn quan trọng vì là taxonomy theo bộ môn và là
    đích khóa ngoại của `question_bank_items.category_id`.
  - Danh mục ADMIN trên form giảng viên là reference âm tạm thời; service chỉ
    mirror sang `question_bank_categories` khi câu hỏi/import được lưu.
  - Vì vậy không nên drop bảng theo trạng thái hiện tại.
- Vấn đề còn lại là UX: LEADER không thấy các danh mục ADMIN đang được giảng
  viên nhìn thấy, nên màn hình tạo cảm giác hai hệ taxonomy không liên quan.
- Bản sửa:
  - Empty state nói rõ đây chỉ là danh mục riêng của bộ môn.
  - Giảng viên vẫn dùng được taxonomy ADMIN; KSH chỉ tạo liên kết bộ môn khi lưu
    câu hỏi đầu tiên.
  - Không drop `question_bank_categories`.
- Bằng chứng hồi quy:
  - `/leader/question-bank` hiển thị thông điệp mới, không còn ngụ ý giảng viên
    không có danh mục và không có lỗi trang.

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
- [x] Ngân hàng câu hỏi và form thêm câu hỏi tải đủ tám danh mục hiện hành.
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
- [x] Server-log sweep cuối: không có `ERROR`, exception, 403 hay lỗi template
  mới; chỉ còn hai warning platform đã ghi ở trên.
- [x] Giữ nguyên phạm vi loại trừ: mật khẩu demo `123456`, migration chain và
  mọi thay đổi logic/cấu hình riêng của `/practice`.
